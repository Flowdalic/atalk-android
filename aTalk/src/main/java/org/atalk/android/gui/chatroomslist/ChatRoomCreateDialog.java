/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file
 * except in compliance with the License. You may
 * obtain a copy of the License at
 * 
 * http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software distributed under the
 * License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the License for
 * the specific language governing permissions
 * and limitations under the License.
 */
package org.atalk.android.gui.chatroomslist;

import android.app.Dialog;
import android.content.*;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.TypedValue;
import android.view.View;
import android.widget.*;
import android.widget.AdapterView.OnItemSelectedListener;

import net.java.sip.communicator.impl.muc.*;
import net.java.sip.communicator.service.muc.*;
import net.java.sip.communicator.service.protocol.*;
import net.java.sip.communicator.util.ConfigurationUtils;

import org.atalk.android.R;
import org.atalk.android.gui.AndroidGUIActivator;
import org.atalk.android.gui.chat.ChatSessionManager;
import org.atalk.android.gui.menu.MainMenuActivity;
import org.atalk.android.util.ComboBox;
import org.jxmpp.util.XmppStringUtils;

import java.beans.*;
import java.util.*;

/**
 * The invite dialog is the one shown when the user clicks on the conference button in the
 * Contact List toolbar.
 *
 * @author Eng Chong Meng
 */
public class ChatRoomCreateDialog extends Dialog implements OnItemSelectedListener
{
	/**
	 * The logger
	 */
	// private final static Logger logger = Logger.getLogger(ChatRoomCreateDialog.class);
	public static final String REMOVE_ROOM_ON_FIRST_JOIN_FAILED
			= "gui.chatroomslist.REMOVE_ROOM_ON_FIRST_JOIN_FAILED";

	private final MainMenuActivity mParent;
	private final MUCServiceImpl mucService;

	/**
	 * The account list view.
	 */
	private Spinner accountsSpinner;
	private ComboBox chatRoomComboBox;

	private EditText subjectField;
	private EditText nicknameField;
	private String chatRoomField;
	private Button mJoinButton;

	/**
	 * A map of <JID, ChatRoomProviderWrapper>
	 */
	private Map<String, ChatRoomProviderWrapper> mucRoomWrapperList = new LinkedHashMap<>();

	/**
	 * Constructs the <tt>ChatInviteDialog</tt>.
	 *
	 * @param mContext
	 * 		the <tt>ChatPanel</tt> corresponding to the <tt>ChatRoom</tt>, where the contact is
	 * 		invited.
	 */
	public ChatRoomCreateDialog(Context mContext)
	{
		super(mContext);
		mParent = (MainMenuActivity) mContext;
		mucService = MUCActivator.getMUCService();
	}

	@Override
	public void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
		setTitle(R.string.service_gui_CHAT_ROOM_JOIN);
		this.setContentView(R.layout.muc_room_create_dialog);

		accountsSpinner = (Spinner) this.findViewById(R.id.jid_Accounts_Spinner);
		initAccountSpinner();

		nicknameField = (EditText) this.findViewById(R.id.NickName_Edit);
		subjectField = (EditText) this.findViewById(R.id.chatRoom_Subject_Edit);
		subjectField.setText(mParent.getString(R.string.service_gui_GROUP_CHAT));

		chatRoomComboBox = (ComboBox) this.findViewById(R.id.chatRoom_Combo);
		initComboBox();

		mJoinButton = (Button) this.findViewById(R.id.button_Join);
		mJoinButton.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				createOrJoinChatRoom();
				closeDialog();
			}
		});

		Button mCancelButton = (Button) this.findViewById(R.id.button_Cancel);
		mCancelButton.setOnClickListener(new View.OnClickListener()
		{
			public void onClick(View v)
			{
				closeDialog();
			}
		});
	}

	// add items into accountsSpinner dynamically
	private void initAccountSpinner()
	{
		String mAccount = null;
		List<String> list = new ArrayList<>();

		Iterator<ChatRoomProviderWrapper> providers = mucService.getChatRoomProviders();
		while (providers.hasNext()) {
			ChatRoomProviderWrapper provider = providers.next();
			mAccount = provider.getProtocolProvider().getAccountID().getDisplayName();
			mucRoomWrapperList.put(mAccount, provider);
			list.add(mAccount);
		}

		// Create an ArrayAdapter using the string array and a default spinner layout
		ArrayAdapter<String> mAdapter
				= new ArrayAdapter<String>(mParent, android.R.layout.simple_spinner_item, list);
		// Specify the layout to use when the list of choices appears
		mAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		// Apply the adapter to the spinner
		accountsSpinner.setAdapter(mAdapter);
		accountsSpinner.setOnItemSelectedListener(this);
	}

	/**
	 * Creates the providers comboBox and filling its content with the current available chatRooms
	 */
	private void initComboBox()
	{
        List<String> chatRoomList = new ArrayList<>();
        ChatRoomProviderWrapper crpWrapper = getSelectedProvider();
        if (crpWrapper != null) {
            ProtocolProviderService pps = crpWrapper.getProtocolProvider();
            chatRoomList = mucService.getExistingChatRooms(pps);
        }
		if (chatRoomList.size() == 0)
			chatRoomList.add(mParent.getString(R.string.service_gui_ROOM_NAME));

		chatRoomComboBox.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
		chatRoomComboBox.setText(chatRoomList.get(0));
		chatRoomComboBox.setSuggestionSource(chatRoomList);
	}

	private void closeDialog()
	{
		this.cancel();
	}

	/**
	 * Updates the enable/disable state of the OK button.
	 */
	private void updateJoinButtonEnableState()
	{
		String nickName = nicknameField.getText().toString().trim();
		chatRoomField = chatRoomComboBox.getText().trim();

		boolean mEnable = (!TextUtils.isEmpty(chatRoomField) && !TextUtils.isEmpty(nickName));
		if (mEnable) {
			mJoinButton.setEnabled(true);
			mJoinButton.setAlpha(1.0f);
		}
		else {
			mJoinButton.setEnabled(true);
			mJoinButton.setAlpha(1.0f);
		}
	}

	@Override
	public void onItemSelected(AdapterView<?> adapter, View view, int pos, long id)
	{
		String userId = (String) adapter.getItemAtPosition(pos);
		ChatRoomProviderWrapper protocol = mucRoomWrapperList.get(userId);
		setDefaultNickname(protocol.getProtocolProvider());
		initComboBox();
	}

	@Override
	public void onNothingSelected(AdapterView<?> parent)
	{
		// Another interface callback
	}

	/**
	 * Sets the default value in the nickname field based on pps.
	 *
	 * @param pps
	 * 		the ProtocolProviderService
	 */
	private void setDefaultNickname(ProtocolProviderService pps)
	{
		if (pps != null) {
			String nickName
					= AndroidGUIActivator.getGlobalDisplayDetailsService().getDisplayName(pps);
			if ((nickName == null) || nickName.contains("@"))
				nickName = XmppStringUtils.parseLocalpart(pps.getAccountID().getAccountJid());
			nicknameField.setText(nickName);
			updateJoinButtonEnableState();
		}
	}

	/**
	 * Gets the (chat room) subject displayed in this <tt>ChatRoomSubjectPanel</tt>.
	 *
	 * @return the (chat room) subject displayed in this <tt>ChatRoomSubjectPanel</tt>
	 */
	public String getSubject()
	{
		return subjectField.getText().toString();
	}

	/**
	 * Sets the (chat room) subject to be displayed in this <tt>ChatRoomSubjectPanel</tt>.
	 *
	 * @param subject
	 * 		the (chat room) subject to be displayed in this <tt>ChatRoomSubjectPanel</tt>
	 */
	public void setSubject(String subject)
	{
		subjectField.setText(subject);
	}

	/**
	 * Returns the selected provider in the providers combo box.
	 *
	 * @return the selected provider
	 */
	private ChatRoomProviderWrapper getSelectedProvider()
	{
		String key = (String) accountsSpinner.getSelectedItem();
		return mucRoomWrapperList.get(key);
	}

	/**
	 * Sets the value of chat room name field.
	 *
	 * @param chatRoom
	 * 		the chat room name.
	 */
	public void setChatRoomField(String chatRoom)
	{
		this.chatRoomComboBox.setText(chatRoom);
		updateJoinButtonEnableState();
	}

	/**
	 * Invites the contacts to the chat conference.
	 */
	private void createOrJoinChatRoom()
	{
		// allow nickName to contain spaces
		String nickName = nicknameField.getText().toString().trim();
		String subject = subjectField.getText().toString().trim();
		String chatRoomID = chatRoomComboBox.getText().replaceAll("\\s", "");
		Collection<String> contacts = new ArrayList<>();
		String reason = "Let's chat";
		Boolean createNew = false;

		if (!TextUtils.isEmpty(chatRoomID) && !TextUtils.isEmpty(nickName)) {
			ProtocolProviderService pps = getSelectedProvider().getProtocolProvider();

			// create if new chatRoom
			ChatRoomWrapper chatRoomWrapper
					= mucService.findChatRoomWrapperFromChatRoomID(chatRoomID, pps);
			if (chatRoomWrapper == null) {
				createNew = true;
				chatRoomWrapper = mucService.createChatRoom(chatRoomID, pps, contacts,
						reason, true, false, false);
				// In case the protocol failed to create a chat room (null), then return without
				// open the chat room.
				if (chatRoomWrapper == null) {
					return;
				}
				chatRoomID = chatRoomWrapper.getChatRoomID();
				ConfigurationUtils.saveChatRoom(pps, chatRoomID, chatRoomID);
			}
			ConfigurationUtils.updateChatRoomProperty(pps, chatRoomID,
					ChatRoom.USER_NICK_NAME, nickName);

			// Allow to remove new chatRoom if join failed
			if (createNew && AndroidGUIActivator.getConfigurationService()
					.getBoolean(REMOVE_ROOM_ON_FIRST_JOIN_FAILED, false)) {
				final ChatRoomWrapper crWrapper = chatRoomWrapper;

				chatRoomWrapper.addPropertyChangeListener(new PropertyChangeListener()
				{
					@Override
					public void propertyChange(PropertyChangeEvent evt)
					{
						if (evt.getPropertyName().equals(ChatRoomWrapper.JOIN_SUCCESS_PROP))
							return;

						// if we failed for some , then close and remove the room
						AndroidGUIActivator.getUIService().closeChatRoomWindow(crWrapper);
						AndroidGUIActivator.getMUCService().removeChatRoom(crWrapper);
					}
				});
			}

			// Set chatRoom openAutomatically on_activity
			MUCService.setChatRoomAutoOpenOption(pps, chatRoomID, MUCService.OPEN_ON_ACTIVITY);
			mucService.joinChatRoom(chatRoomWrapper, nickName, null, subject);
			Intent chatIntent = ChatSessionManager.getChatIntent(chatRoomWrapper);
			mParent.startActivity(chatIntent);
		}
	}
	
//	/**
//	 * Invites the contacts to the chat conference.
//	 */
//	private void createChatRoom()
//	{
//		// allow nickName to contain spaces
//		String nickName = nicknameField.getText().toString().trim();
//		String subject = subjectField.getText().toString().trim();
//		chatRoomField = chatRoomComboBox.getText().replaceAll("\\s", "");
//		Collection<String> contacts = new ArrayList<>();
//		String reason = "Let's chat";
//
//		if (!TextUtils.isEmpty(chatRoomField) && !TextUtils.isEmpty(nickName)) {
//			final ChatRoomWrapper chatRoomWrapper = AndroidGUIActivator.getMUCService()
//					.createChatRoom(chatRoomField, getSelectedProvider().getProtocolProvider(),
//							contacts, reason, true, false, false);
//
//			// In case the protocol failed to create a chat room (null), then return without
//			// open the chat room.
//			if (chatRoomWrapper == null) {
//				return;
//			}
//
//			ProtocolProviderService pps
//					= chatRoomWrapper.getParentProvider().getProtocolProvider();
//			String chatRoomID = chatRoomWrapper.getChatRoomID();
//
//			if (!chatRoomWrapper.isPersistent()) {
//				chatRoomWrapper.setPersistent(true);
//				ConfigurationUtils.saveChatRoom(pps, chatRoomID, chatRoomID);
//			}
//			ConfigurationUtils.updateChatRoomProperty(pps, chatRoomID, ChatRoom.USER_NICK_NAME,
//					nickName);
//
//			if (AndroidGUIActivator.getConfigurationService()
//					.getBoolean(REMOVE_ROOM_ON_FIRST_JOIN_FAILED, false)) {
//				chatRoomWrapper.addPropertyChangeListener(new PropertyChangeListener()
//				{
//					@Override
//					public void propertyChange(PropertyChangeEvent evt)
//					{
//						if (evt.getPropertyName().equals(ChatRoomWrapper.JOIN_SUCCESS_PROP))
//							return;
//
//						// if we failed for some reason we want to remove the room close the room
//						AndroidGUIActivator.getUIService().closeChatRoomWindow(chatRoomWrapper);
//
//						// remove it
//						AndroidGUIActivator.getMUCService().removeChatRoom(chatRoomWrapper);
//					}
//				});
//			}
//
//			// Set chatRoom openAutomatically on_activity
//			MUCService.setChatRoomAutoOpenOption(pps, chatRoomID, MUCService.OPEN_ON_ACTIVITY);
//			AndroidGUIActivator.getMUCService()
//					.joinChatRoom(chatRoomWrapper, nickName, null, subject);
//			Intent chatIntent = ChatSessionManager.getChatIntent(chatRoomWrapper);
//			mParent.startActivity(chatIntent);
//		}
//	}
}
