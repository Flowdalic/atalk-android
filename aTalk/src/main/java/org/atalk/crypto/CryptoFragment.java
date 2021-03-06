/*
 * aTalk, android VoIP and Instant Messaging client
 * Copyright 2014 Eng Chong Meng
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.atalk.crypto;

import android.content.*;
import android.view.*;

import net.java.otr4j.OtrPolicy;
import net.java.sip.communicator.plugin.otr.*;
import net.java.sip.communicator.plugin.otr.OtrContactManager.OtrContact;
import net.java.sip.communicator.service.contactlist.MetaContact;
import net.java.sip.communicator.service.gui.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.Logger;

import org.atalk.android.*;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.*;
import org.atalk.android.gui.settings.SettingsActivity;
import org.atalk.crypto.listener.CryptoModeChangeListener;
import org.atalk.crypto.omemo.OmemoAuthenticateDialog;
import org.atalk.service.osgi.OSGiFragment;
import org.jivesoftware.smack.*;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smackx.muc.*;
import org.jivesoftware.smackx.omemo.*;
import org.jivesoftware.smackx.omemo.exceptions.*;
import org.jivesoftware.smackx.omemo.internal.*;
import org.jxmpp.jid.*;

import java.net.URI;
import java.security.*;
import java.util.*;

import static net.java.sip.communicator.plugin.otr.OtrActivator.*;
import static org.atalk.android.gui.chat.ChatFragment.*;

/**
 * Fragment when added to <tt>Activity</tt> will display the padlock allowing user to select
 * various type of encryption options. Only currently active chat is handled by this fragment.
 *
 * @author Eng Chong Meng
 */
public class CryptoFragment extends OSGiFragment
		implements ChatSessionManager.CurrentChatListener
{
	/**
	 * The logger
	 */
	private final static Logger logger = Logger.getLogger(CryptoFragment.class);

	/**
	 * A map of the user selected encryption menu ItemId. The stored key is the chatId. The
	 * information is used to restore the last user selected encryption choice when a chat window
	 * is scrolled in view.
	 */
	private static final Map<String, Integer> encryptionChoice = new LinkedHashMap<>();

	/**
	 * A cache map of the Descriptor and its OmemoSupport capability. The information is to speed
	 * up decision making when a chat page is scrolled in view. Otherwise user page scroll may
	 * sometimes hang for 10 seconds for server to response. The Descriptor can be ChatRoom
	 * or Contact
     * #TODO: find a way to clear when session is closed in case it was invalid
	 */
	private static final Map<Object, Boolean> omemoCapable = new LinkedHashMap<>();

	/**
	 * A cache map of the Descriptor and its CryptoModeChangeListener. Need this as listener is
	 * being added only when the chatFragment is launch. Slide pages does not get updated.
	 * ChatType change event is sent to CryptoModeChangeListener to update chatFragment
	 * background colour:
	 */
	private static final Map<Object, CryptoModeChangeListener> cryptoModeChangeListeners = new LinkedHashMap<>();

	/**
	 * Menu instances used to select and control the crypto choices.
	 */
	private Menu menu;
	private MenuItem mCryptoChoice;
	private MenuItem mNone;
	private MenuItem mOmemo;
	private MenuItem mOtr;

	private XMPPTCPConnection mConnection;
	private Object mDescriptor;
	private OmemoManager mOmemoManager;
	private OmemoStore mOmemoStore;

	private int mChatType = MSGTYPE_NORMAL;

	/**
	 * Current active instance of chatSession & user.
	 */
	private ChatPanel activeChat = null;
	private String mEntity;

	/**
	 * Otr Contact for currently active chatSession.
	 */
	private OtrContact currentOtrContact = null;

	/**
	 * isOmemoMode flag prevents otr from changing status when transition from otr to omemo when
	 * <tt>true</tt>; otr listener is async event triggered.
	 */
	private boolean isOmemoMode = false;

	/**
	 * Creates new instance of <tt>OtrFragment</tt>.
	 */
	public CryptoFragment()
	{
		setHasOptionsMenu(true);
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onStop()
	{
		ChatSessionManager.removeCurrentChatListener(this);
		scOtrEngine.removeListener(scOtrEngineListener);
		scOtrKeyManager.removeListener(scOtrKeyManagerListener);
		super.onStop();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public void onCreateOptionsMenu(Menu menu, MenuInflater inflater)
	{
		super.onCreateOptionsMenu(menu, inflater);

		// (OtrActivator.scOtrEngine == null)
		// This happens when Activity is recreated by the system after OSGi service has been
		// killed (and the whole process)
		if (AndroidGUIActivator.bundleContext == null) {
			logger.error("OSGi service probably not initialized");
			return;
		}

		// Insert chat encryption choices if not found
		this.menu = menu;
		if (menu.findItem(R.id.encryption_none) == null)
			inflater.inflate(R.menu.crypto_choices, menu);

		mCryptoChoice = menu.findItem(R.id.crypto_choice);
		mNone = menu.findItem(R.id.encryption_none);
		mOmemo = menu.findItem(R.id.encryption_omemo);
		mOtr = menu.findItem(R.id.encryption_otr);

		// Initialize the padlock icon when new menu is created
		doInit();
	}

	/**
	 * {@inheritDoc}
	 */
	@Override
	public boolean onOptionsItemSelected(MenuItem item)
	{
		boolean hasChange = false;
		item.setChecked(true);

		switch (item.getItemId()) {
			case R.id.crypto_choice:
				// sync button check to current chatType
				checkCryptoButton(activeChat.getChatType());
				return true;
			case R.id.encryption_none:
				if ((mChatType != MSGTYPE_NORMAL) && (mChatType != MSGTYPE_MUC_NORMAL)) {
					if (mDescriptor instanceof Contact)
						mChatType = ChatFragment.MSGTYPE_NORMAL;
					else
						mChatType = ChatFragment.MSGTYPE_MUC_NORMAL;
					hasChange = true;
					doHandleOmemoPressed(false);
					doHandleOtrPressed(false);
				}
				break;
			case R.id.encryption_omemo:
				hasChange = true;
				doHandleOtrPressed(false);
				doHandleOmemoPressed(true);
				break;
			case R.id.encryption_otr:
				hasChange = true;
				doHandleOmemoPressed(false);
				doHandleOtrPressed(true);
				break;
			default:
				break;
		}

		if (hasChange) {
			String chatId = ChatSessionManager.getCurrentChatId();
			encryptionChoice.put(chatId, item.getItemId());
            setStatusOmemo(mChatType);
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * Synchronise the cryptoChoice button checkMark with the current chatType
	 *
	 * @param chatType
	 * 		Sync cryptoChoice button check to the given chatType
	 * @return the button menuItem corresponding to the given chatType
	 */
	private MenuItem checkCryptoButton(int chatType)
	{
		MenuItem mItem;

		switch (chatType) {
			case ChatFragment.MSGTYPE_NORMAL:
			case ChatFragment.MSGTYPE_MUC_NORMAL:
				mItem = mNone;
				break;

			case ChatFragment.MSGTYPE_OMEMO:
			case ChatFragment.MSGTYPE_OMEMO_UA:
			case ChatFragment.MSGTYPE_OMEMO_UT:
				mItem = mOmemo;
				break;

			case ChatFragment.MSGTYPE_OTR:
			case ChatFragment.MSGTYPE_OTR_UA:
				mItem = mOtr;
				break;

			default:
				mItem = mNone;
		}
		mItem.setChecked(true);
		return mItem;
	}

	/**
	 * Handle OMEMO state when the option is selected/unSelected.
	 */
	private void doHandleOmemoPressed(final boolean enable)
	{
		if (mDescriptor == null)
			return;

		// Linked map between OmemoDevice and its fingerprint.
		HashMap<OmemoDevice, OmemoFingerprint> fingerPrints;

		boolean allTrusted = true;
		isOmemoMode = enable;

		OmemoDevice omemoDevice;
		OmemoFingerprint fingerPrint;

		if (enable) {
			if (mDescriptor instanceof Contact) {
				BareJid bareJid = ((Contact) mDescriptor).getJid().asBareJid();
				mEntity = bareJid.toString();
				fingerPrints = mOmemoManager.getActiveFingerprints(bareJid);
				try {
					mOmemoManager.encrypt(bareJid, "Hi buddy!");
				}
				catch (UndecidedOmemoIdentityException e) {
					HashSet<OmemoDevice> omemoDevices = e.getUndecidedDevices();
					logger.warn("There are undecided Omemo devices: " + omemoDevices);
					startActivity(OmemoAuthenticateDialog.createIntent(mOmemoManager,
							omemoDevices, this));
					allTrusted = false;
				}
				catch (InterruptedException | CannotEstablishOmemoSessionException
						| SmackException.NoResponseException | CryptoFailedException
						| NoSuchAlgorithmException | SmackException.NotConnectedException e) {
					mChatType = ChatFragment.MSGTYPE_MUC_NORMAL;
					activeChat.addMessage(mEntity, new Date(), Chat.ERROR_MESSAGE,
							getString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED),
							ChatMessage.ENCODE_PLAIN);
					logger.info("OMEMO changes mChatType to: " + mChatType);
					return;
				}

				int numUntrusted = 0;
				for (Map.Entry<OmemoDevice, OmemoFingerprint> entry : fingerPrints.entrySet()) {
					omemoDevice = entry.getKey();
					fingerPrint = entry.getValue();
					if (!mOmemoManager.isTrustedOmemoIdentity(omemoDevice, fingerPrint)) {
						numUntrusted++;
					}
				}
				/*
				 * Found no trusted device for OMEMO session, so set to MSGTYPE_OMEMO_UT
				 * Encrypted message without the buddy <rid/> key
				*/
				if ((numUntrusted > 0) && (numUntrusted == fingerPrints.size())) {
					mChatType = ChatFragment.MSGTYPE_OMEMO_UT;
					activeChat.addMessage(mEntity, new Date(), Chat.SYSTEM_MESSAGE,
							getString(R.string.crypto_msg_OMEMO_SESSION_UNTRUSTED),
							ChatMessage.ENCODE_PLAIN);
				}
				else if (allTrusted) {
					mChatType = ChatFragment.MSGTYPE_OMEMO;
				}
				else {
					mChatType = ChatFragment.MSGTYPE_OMEMO_UA;
					activeChat.addMessage(mEntity, new Date(), Chat.SYSTEM_MESSAGE,
							getString(R.string.crypto_msg_OMEMO_SESSION_UNVERIFIED),
							ChatMessage.ENCODE_PLAIN);
				}
			}
			else if (mDescriptor instanceof ChatRoom) {
				EntityBareJid entityBareJid = ((ChatRoom) mDescriptor).getIdentifier();
				mEntity = entityBareJid.toString();
				MultiUserChatManager mucMgr = MultiUserChatManager.getInstanceFor(mConnection);
				MultiUserChat multiUserChat = mucMgr.getMultiUserChat(entityBareJid);

				try {
					mOmemoManager.encrypt(multiUserChat, "Hi everybody!");
				}
				catch (UndecidedOmemoIdentityException e) {
					HashSet<OmemoDevice> omemoDevices = e.getUndecidedDevices();
					logger.warn("There are undecided Omemo devices: " + omemoDevices);
					startActivity(OmemoAuthenticateDialog.createIntent(mOmemoManager,
							omemoDevices, this));
					allTrusted = false;
				}
				catch (NoOmemoSupportException | InterruptedException
						| CannotEstablishOmemoSessionException | SmackException.NoResponseException
						| XMPPException.XMPPErrorException | CryptoFailedException
						| NoSuchAlgorithmException | SmackException.NotConnectedException e) {
					mChatType = ChatFragment.MSGTYPE_MUC_NORMAL;
					activeChat.addMessage(mEntity, new Date(), Chat.ERROR_MESSAGE,
							getString(R.string.crypto_msg_OMEMO_SESSION_SETUP_FAILED),
							ChatMessage.ENCODE_PLAIN);
					return;
				}

				allTrusted = allTrusted && isAllTrusted(multiUserChat);
				if (allTrusted)
					mChatType = ChatFragment.MSGTYPE_OMEMO;
				else {
					mChatType = ChatFragment.MSGTYPE_OMEMO_UA;
					activeChat.addMessage(mEntity, new Date(), Chat.SYSTEM_MESSAGE,
							getString(R.string.crypto_msg_OMEMO_SESSION_UNVERIFIED_UNTRUSTED),
							ChatMessage.ENCODE_PLAIN);
				}
			}
            logger.info("OMEMO changes mChatType to: " + mChatType);
		}
		// Else Let calling method or OTR Listener to decide what to set
	}

	/**
	 * Check to see if all muc recipients are verified or trusted
	 *
	 * @param multiUserChat
	 * 		MultiUserChat
	 * @return return <tt>true</tt> if all muc recipients are verified or trusted. Otherwise
	 * <tt>false</tt>
	 */
	private boolean isAllTrusted(MultiUserChat multiUserChat)
	{
		boolean allTrusted = true;
		OmemoFingerprint fingerPrint;
		BareJid recipient;

		for (EntityFullJid e : multiUserChat.getOccupants()) {
			recipient = multiUserChat.getOccupant(e).getJid().asBareJid();
			CachedDeviceList theirDevices = mOmemoStore.loadCachedDeviceList(mOmemoManager, recipient);
			for (int id : theirDevices.getActiveDevices()) {
				OmemoDevice recipientDevice = new OmemoDevice(recipient, id);
				try {
					fingerPrint = mOmemoManager.getFingerprint(recipientDevice);
					allTrusted = mOmemoManager.isTrustedOmemoIdentity(recipientDevice, fingerPrint)
							&& allTrusted;
				}
				catch (CannotEstablishOmemoSessionException e1) {
					logger.warn("AllTrusted check exception: " + e1.getMessage());
				}
			}
		}
		return allTrusted;
	}

	/**
	 * Handle OTR state when the option is selected/unSelected.
	 * mChatType is set by ScOtrEngineListener
	 * <p>
	 * When (enable == true):
	 * - End session and go back to plain text for all non-encrypt states i.e.
	 * TIMED_OUT, LOADING and FINISHED (remote client end otr session)
	 * - Only start otr session if it is in PLAINTEXT mode
	 * - Return if it is already in ENCRYPTED state
	 * <p>
	 * Default action when (enable == false):
	 * - End session and go back to plain text for all states i.e.
	 * TIMED_OUT, LOADING, FINISHED (remote client end otr session) and ENCRYPTED
	 * - Do nothing if it is already in PLAINTEXT state
	 * <p>
	 * Run in new thread prevents network on main thread exception
	 */
	private void doHandleOtrPressed(final boolean enable)
	{
		if (currentOtrContact == null)
			return;

		new Thread()
		{
			@Override
			public void run()
			{
				Contact contact = currentOtrContact.contact;
				OtrPolicy policy = scOtrEngine.getContactPolicy(contact);
				ScSessionStatus status = scOtrEngine.getSessionStatus(currentOtrContact);

				if (enable) {
					switch (status) {
						// Do nothing if it is already in encryption mode
						case ENCRYPTED:
							break;
						/*
						 * Default action for timeout, loading (handset) and finished
						 * is end otr private session.
						 */
						case TIMED_OUT:
							policy.setSendWhitespaceTag(false);
							scOtrEngine.setContactPolicy(contact, policy);

						case LOADING:
						case FINISHED:
							scOtrEngine.endSession(currentOtrContact);
							break;

						// Start otr private session if in plainText mode
						case PLAINTEXT:
							OtrPolicy globalPolicy = scOtrEngine.getGlobalPolicy();
							policy.setSendWhitespaceTag(globalPolicy.getSendWhitespaceTag());
							scOtrEngine.setContactPolicy(contact, policy);
							scOtrEngine.startSession(currentOtrContact);
							break;
					}
				}
				else {
					// let calling method or omemo decides what to set when OTR is disabled
					switch (status) {
						case PLAINTEXT:
							// Do nothing if it is already in plain Text
							break;

						/*
						 * Default action for timeout, encrypted, loading (handset) and finished
						 * is end otr private session.
						 */
						case TIMED_OUT:
						case ENCRYPTED:
							policy.setSendWhitespaceTag(false);
							scOtrEngine.setContactPolicy(contact, policy);

						case LOADING:
						case FINISHED:
							scOtrEngine.endSession(currentOtrContact);
							break;
					}
				}
			}
		}.start();
	}

	/**
	 * Event trigger when user slide the chatFragment
	 *
	 * @param chatId
	 * 		id of current chat session or <tt>null</tt> if there is no chat currently
	 */
	@Override
	public void onCurrentChatChanged(String chatId)
	{
		setCurrentChatSession(chatId);
	}

	/**
	 * Register listeners, initializes the padlock icons and encryption options menu enable state.
	 * Must only be performed after the completion of onCreateOptionsMenu().
	 *
	 * @see #onCreateOptionsMenu(Menu, MenuInflater)
	 */
	private void doInit()
	{
		ChatSessionManager.addCurrentChatListener(this);
		scOtrEngine.addListener(scOtrEngineListener);
		scOtrKeyManager.addListener(scOtrKeyManagerListener);

		// Setup currentChatSession options for the Jid if login in
		String currentChatId = ChatSessionManager.getCurrentChatId();
		if (currentChatId != null)
			setCurrentChatSession(currentChatId);
	}

	/**
	 * Triggered from onCreateOption() or onCurrentChatChanged()
	 * <p>
	 * Sets current <tt>ChatPanel</tt> identified by given <tt>chatSessionKey</tt>.
	 * Init Crypto choice to last selected or encryption_none if new
	 * Set currentOtrContact as appropriate if OTR is supported
	 *
	 * @param chatSessionId
	 * 		chat session key managed by <tt>ChatSessionManager</tt>
	 */
	public void setCurrentChatSession(String chatSessionId)
	{
		// new Exception("current sessionID: " + chatSessionId).printStackTrace();
		MetaContact metaContact = null;
		activeChat = ChatSessionManager.getActiveChat(chatSessionId);
		if ((activeChat != null)
				&& activeChat.getChatSession() instanceof MetaContactChatSession) {
			metaContact = activeChat.getMetaContact();
		}

		Contact contact = (metaContact == null) ? null : metaContact.getDefaultContact();
		setCurrentContact(contact, chatSessionId);
	}

	/**
	 * Sets the current <tt>otrContact</tt> and updates status and OTR MenuItem.
	 * // cmeng: Assume support only single remote resource login - current implementation
	 *
	 * @param contact
	 * 		new <tt>contact</tt> to be used.
	 */

	private void setCurrentContact(Contact contact, String chatSessionId)
	{
        currentOtrContact = null;

		if (contact == null) {
            setOTRMenuItem(null);
			initOmemo(chatSessionId);
		}
		else {
			Collection<ContactResource> resources = contact.getResources();
			if (resources != null) {
				for (ContactResource resource : resources) {
					OtrContact otrContact = OtrContactManager.getOtrContact(contact, resource);
					if (otrContact.equals(currentOtrContact)) {
                        break;
                    }
                    currentOtrContact = otrContact;
				}
			}

			// OTR option is only available for online buddy
            if (contact.getPresenceStatus().isOnline())
                setOTRMenuItem(scOtrEngine.getContactPolicy(contact));
			else
                setOTRMenuItem(null);

			initOmemo(chatSessionId);
            if (!isOmemoMode && currentOtrContact != null) {
				setStatusOtr(scOtrEngine.getSessionStatus(currentOtrContact));
			}
		}
	}

	/**
	 * This method needs to be called to update crypto status after:
	 * - the crypto menu is first created (after launch of chatFragment).
	 * - when user slides chatSession pages i.e. onCurrentChatChanged().
	 *
	 * @param chatSessionId
	 * 		Current ChatSession Id to update crypto Status and button check
	 */
	private void initOmemo(String chatSessionId)
	{
		if (!encryptionChoice.containsKey(chatSessionId))
			encryptionChoice.put(chatSessionId, mNone.getItemId());

		int mSelectedChoice = encryptionChoice.get(chatSessionId);
		MenuItem mItem = menu.findItem(mSelectedChoice);
		mItem.setChecked(true);
		mOmemo.setEnabled(isOmemoSupport());

		// need to handle crypto state icon if it is not OTR - need to handle for all in Note 8???
		if (mItem != mOtr) {
			setStatusOmemo(activeChat.getChatType());
		}
		isOmemoMode = (mItem == mOmemo);

//		logger.warn("ChatSession ID: " + chatSessionId
//				+ "\nEncryption choice: " + encryptionChoice
//				+ "\nmItem: " + mItem
//				+ "\nChatType: " + activeChat.getChatType());
	}

	/**
	 * OTR engine listener.
	 */
	private final ScOtrEngineListener scOtrEngineListener = new ScOtrEngineListener()
	{
		public void sessionStatusChanged(OtrContact otrContact)
		{
			// OtrMetaContactButton.this.contact can be null - equals order is important.
			if (otrContact.equals(currentOtrContact)) {
				setStatusOtr(scOtrEngine.getSessionStatus(otrContact));
			}
		}

		public void contactPolicyChanged(Contact contact)
		{
			// this.otContact can be null - equals order is important.
			if (contact.equals(currentOtrContact.contact)) {
				setOTRMenuItem(scOtrEngine.getContactPolicy(contact));
			}
		}

		public void globalPolicyChanged()
		{
			if (currentOtrContact != null)
				setOTRMenuItem(scOtrEngine.getContactPolicy(currentOtrContact.contact));
		}

		@Override
		public void multipleInstancesDetected(OtrContact otrContact)
		{
			// TODO Auto-generated method stub
		}

		@Override
		public void outgoingSessionChanged(OtrContact otrContact)
		{
			// this.otrContact can be null - equals order is important.
			if (otrContact.equals(currentOtrContact)) {
				setStatusOtr(scOtrEngine.getSessionStatus(otrContact));
			}
		}
	};

	/**
	 * OTR key manager listener.
	 */
	private final ScOtrKeyManagerListener scOtrKeyManagerListener = new ScOtrKeyManagerListener()
	{
		public void contactVerificationStatusChanged(OtrContact otrContact)
		{
			// this.otrContact can be null - equals order is important..
			if (otrContact.equals(currentOtrContact)) {
				setStatusOtr(scOtrEngine.getSessionStatus(otrContact));
			}
		}
	};

	/**
	 * Sets the button enabled status according to the passed in {@link OtrPolicy}.
	 * Hides the padlock when OTR is not supported; Grey when supported but option disabled
	 *
	 * @param contactPolicy
	 * 		the {@link OtrPolicy}.
	 */
	private void setOTRMenuItem(final OtrPolicy contactPolicy)
	{
		runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				if (contactPolicy != null)
				{
					if (contactPolicy.getEnableManual()) {
                        mOtr.setEnabled(true);
						// padLock.getIcon().setAlpha(255);
					}
					else {
						// padLock.getIcon().setAlpha(120);
						mOtr.setEnabled(false);
					}
				}
				else {
					// padLock.setVisible(false);
					mOtr.setEnabled(false);
				}
			}
		});
	}

	/**
	 * Sets the padlock icon according to the passed in {@link ScSessionStatus}.
	 *
	 * @param status
	 * 		the {@link ScSessionStatus}.
	 * @see #isOmemoMode denition
	 */
	private void setStatusOtr(ScSessionStatus status)
	{
		// Only allow otr changing status triggered from events when not in omemo session
		if (!isOmemoMode) {
			final int iconId;
			final int tipKey;
			int chatType = MSGTYPE_NORMAL;

			switch (status) {
				case ENCRYPTED:
					PublicKey pubKey = scOtrEngine.getRemotePublicKey(currentOtrContact);
					String fingerprint = scOtrKeyManager.getFingerprintFromPublicKey(pubKey);
					boolean isVerified = scOtrKeyManager.isVerified(currentOtrContact.contact, fingerprint);
					iconId = isVerified ? R.drawable.encrypted_verified_dark : R.drawable.encrypted_unverified_dark;
					tipKey = isVerified ? R.string.plugin_otr_menu_OTR_AUTHETICATED : R.string
							.plugin_otr_menu_OTR_NON_AUTHETICATED;
					chatType = isVerified ? MSGTYPE_OTR : MSGTYPE_OTR_UA;
					break;
				case FINISHED:
					iconId = R.drawable.encrypted_finished_dark;
					tipKey = R.string.plugin_otr_menu_OTR_Finish;
					break;
				case PLAINTEXT:
					iconId = R.drawable.encrypted_unsecure_dark;
					tipKey = R.string.plugin_otr_menu_OTR_PLAINTTEXT;
					chatType = MSGTYPE_NORMAL;
					break;
				case LOADING:
					iconId = R.drawable.encrypted_loading_dark;
					tipKey = R.string.plugin_otr_menu_OTR_HANDSHAKE;
					break;
				case TIMED_OUT:
					iconId = R.drawable.encrypted_pdbroken_dark;
					tipKey = R.string.plugin_otr_menu_OTR_TIMEOUT;
					break;
				default:
					return;
			}

			runOnUiThread(new Runnable()
			{
				@Override
				public void run()
				{
					mCryptoChoice.setIcon(iconId);
					mCryptoChoice.setTitle(tipKey);
				}
			});

			// setStatusOmemo() will always get executed. So skip if same chatType
			if (chatType != mChatType) {
				mChatType = chatType;
				// logger.warn("OTR listener change mChatType to: " + mChatType);
				notifyCryptoModeChanged(mChatType);
			}
		}
	}

	/**
	 * Sets the padlock icon according to the passed in OMEMO mChatType.
	 *
	 * @param chatType
	 * 		OMEMO ChatType.
	 */
	private void setStatusOmemo(int chatType)
	{
		final int iconId;
		final int tipKey;
		mChatType = chatType;

		switch (chatType) {
			case ChatFragment.MSGTYPE_OMEMO:
				iconId = R.drawable.crypto_omemo_verified;
				tipKey = R.string.menu_omemo_authenticated;
				break;
			case ChatFragment.MSGTYPE_OMEMO_UA:
				iconId = R.drawable.crypto_omemo_unverified;
				tipKey = R.string.menu_omemo_unauthenticated;
				break;
			case ChatFragment.MSGTYPE_OMEMO_UT:
				iconId = R.drawable.crypto_omemo_untrusted;
				tipKey = R.string.menu_omemo_untrusted;
				break;
			case MSGTYPE_NORMAL:
			case MSGTYPE_MUC_NORMAL:
				iconId = R.drawable.encrypted_unsecure_dark;
				tipKey = R.string.menu_crypto_plain_text;
				break;
			default:
				iconId = R.drawable.encrypted_unsecure_dark;
				tipKey = R.string.menu_crypto_plain_text;
		}
		runOnUiThread(new Runnable()
		{
			@Override
			public void run()
			{
				mCryptoChoice.setIcon(iconId);
				mCryptoChoice.setTitle(tipKey);
			}
		});

		// logger.warn("Omemo CryptMode change to: " + chatType + " for " + mDescriptor);
		notifyCryptoModeChanged(mChatType);
	}

	/**
	 * Check if OMEMO is supported by current chatTransport
	 *
	 * @return <tt>true</tt> if OMEMO is supported on current chatTransport
	 */
	private boolean isOmemoSupport()
	{
		boolean serverCan = false;
		boolean entityCan = true; // default to support

		// Following 5 variables must be initialized in isOmemoSupport()
        // Do not proceed if account is not log in, otherwise system crash
		ChatTransport mChatTransport = activeChat.getChatSession().getCurrentChatTransport();
		mConnection = mChatTransport.getProtocolProvider().getConnection();
        if (mConnection == null)
            return false;

        mDescriptor = mChatTransport.getDescriptor();
		mOmemoManager = OmemoManager.getInstanceFor(mConnection);
		mOmemoStore = OmemoService.getInstance().getOmemoStoreBackend();

		// read from cache if available for speed
		if (omemoCapable.containsKey(mDescriptor))
			return omemoCapable.get(mDescriptor);

		try {
			DomainBareJid serverJid = mConnection.getServiceName();
			serverCan = OmemoManager.serverSupportsOmemo(mConnection, serverJid);

			if (mDescriptor instanceof ChatRoom) {
				EntityBareJid chatRoom = ((ChatRoom) mDescriptor).getIdentifier();
				entityCan = mOmemoManager.multiUserChatSupportsOmemo(chatRoom);
			}
			else {
				// online check may sometimes experience reply timeout
				Jid contactJId = ((Contact) mDescriptor).getJid();
				// not a good idea to include PEP_NODE_DEVICE_LIST_NOTIFY as some siblings may
				// support omemo encryption.
//				boolean support = ServiceDiscoveryManager.getInstanceFor(connection)
//						.discoverInfo(contactJId).containsFeature(PEP_NODE_DEVICE_LIST_NOTIFY);
				entityCan = mOmemoManager.contactSupportsOmemo(contactJId.asBareJid());

				// cmeng - check from backend database entities table instead
//				String usrID = ((Contact) mDescriptor).getAddress();
//				entityCan = ((SQLiteOmemoStore) mOmemoStore).getContactNumTrustedKeys(usrID) > 0;
			}
		}
		catch (XMPPException.XMPPErrorException | SmackException.NoResponseException
				| InterruptedException | SmackException.NotConnectedException e) {
            entityCan = false;
		}
		omemoCapable.put(mDescriptor, serverCan && entityCan);
		return serverCan && entityCan;
	}

    /**
	 * Listens for show history popup link
	 */
	public static class ShowHistoryLinkListener implements ChatLinkClickedListener
	{
		/**
		 * {@inheritDoc}
		 */
		@Override
		public void chatLinkClicked(URI uri)
		{
			if (uri.getPath().equals("/showHistoryPopupMenu")) {
				// Display settings
				Context ctx = aTalkApp.getGlobalContext();
				Intent settings = new Intent(ctx, SettingsActivity.class);
				settings.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				ctx.startActivity(settings);
			}
		}
	}

	/**
	 * @param chatType
	 * 		New chatType pending user verification action from omemo Authentication dialog
	 * @see OmemoAuthenticateDialog
	 */
	public void updateStatusOmemo(int chatType)
	{
		// Do not change the status if it was an unTrusted session
		if (mChatType == ChatFragment.MSGTYPE_OMEMO_UT)
			return;

		// Update system message if changes is from MSGTYPE_OMEMO_UA
		if (chatType == ChatFragment.MSGTYPE_OMEMO)
			activeChat.addMessage(mEntity, new Date(), Chat.SYSTEM_MESSAGE,
					getString(R.string.crypto_msg_OMEMO_SESSION_VERIFIED),
					ChatMessage.ENCODE_PLAIN);

		// Let resume to take care to update omemo statusIcon and notifyCryptoModeChanged
		activeChat.setChatType(chatType);
	}

	/**
	 * Chat Type change notification to all registered cryptoModeChangeListeners mainly to change
	 * chatFragment background color for the new chatType; which is triggered from user
	 * cryptoMode selection.
	 * When a listener for the mDescriptor key is not found, then the map is updated linking
	 * current mDescriptor with the recent added listener with null key. null key is added when a
	 * chatFragment is opened or became the primary selected.
	 *
	 * @param chatType
	 * 		The new chatType to broadcast to registered listener
	 */
	private void notifyCryptoModeChanged(int chatType)
	{
		CryptoModeChangeListener listener;
		if (!cryptoModeChangeListeners.containsKey(mDescriptor)) {
			listener = cryptoModeChangeListeners.get(null);
			addCryptoModeListener(mDescriptor, listener);
		}
		else {
			listener = cryptoModeChangeListeners.get(mDescriptor);
		}

		if (listener != null) {
			// logger.warn("CryptMode Listener changed: " + listener + "=>" + mDescriptor);
			listener.onCryptoModeChange(chatType);
		}
	}

	/**
	 * Note: mDescriptor is always null when first triggers by chatFragment. It gets updated in
	 * notifyCryptoModeChanged()
	 *
	 * @param listener
	 * 		CryptoModeChangeListener added by chatFragment.
	 *
	 * @see #notifyCryptoModeChanged(int)
	 */
	public void addCryptoModeListener(Object descriptor, CryptoModeChangeListener listener)
	{
		// logger.warn("CryptMode Listener added: " + listener + "<=" + mDescriptor);
		cryptoModeChangeListeners.put(descriptor, listener);
	}
}
