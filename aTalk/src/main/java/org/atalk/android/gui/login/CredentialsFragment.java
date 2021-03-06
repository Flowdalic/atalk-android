/*
 * Jitsi, the OpenSource Java VoIP and Instant Messaging client.
 * 
 * Distributable under LGPL license. See terms of license at gnu.org.
 */
package org.atalk.android.gui.login;

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.text.*;
import android.view.*;
import android.widget.*;

import org.atalk.android.R;
import org.atalk.android.gui.util.ViewUtil;

/**
 * The credentials fragment can be used to retrieve username, password, the "store password" option
 * status, login server overridden option and the server ip:port. Use the arguments to fill the
 * fragment with default values.
 * Supported arguments are:<br/>
 * - {@link #ARG_LOGIN} login default text value<br/>
 * - {@link #ARG_LOGIN_EDITABLE} <tt>boolean</tt> flag indicating if the login field is
 * editable<br/>
 * - {@link #ARG_PASSWORD} password default text value<br/>
 * - {@link #ARG_STORE_PASSWORD} "store password" default <tt>boolean</tt> value
 * - {@link #ARG_IS_SERVER_OVERRIDDEN} "Server Overridden" default <tt>boolean</tt> value
 * - {@link #ARG_SERVER_ADDRESS} Server address default text value<br/>
 * - {@link #ARG_SERVER_PORT} Server port default text value<br/>
 *
 * @author Pawel Domas
 * @author Eng Chong Meng
 */
public class CredentialsFragment extends Fragment
{

	/**
	 * Pre-entered login argument.
	 */
	public static final String ARG_LOGIN = "login";

	/**
	 * Pre-entered password argument.
	 */
	public static final String ARG_PASSWORD = "password";

	/**
	 * Argument indicating whether the login can be edited.
	 */
	public static final String ARG_LOGIN_EDITABLE = "login_editable";

	/**
	 * Pre-entered "store password" <tt>boolean</tt> value.
	 */
	public static final String ARG_STORE_PASSWORD = "store_pass";

	/**
	 * Pre-entered "store password" <tt>boolean</tt> value.
	 */
	public static final String ARG_IB_REGISTRATION = "ib_registration";

	/**
	 * Show server option for user entry if true " <tt>boolean</tt> value.
	 */
	public static final String ARG_IS_SHOWN_SERVER_OPTION = "is_shown_server_option";

	/**
	 * Pre-entered "is server overridden" <tt>boolean</tt> value.
	 */
	public static final String ARG_IS_SERVER_OVERRIDDEN = "is_server_overridden";

	/**
	 * Pre-entered "store server address".
	 */
	public static final String ARG_SERVER_ADDRESS = "server_address";

	/**
	 * Pre-entered "store server port".
	 */
	public static final String ARG_SERVER_PORT = "server_port";

	/**
	 * Reason for the login / reLogin.
	 */
	public static final String ARG_LOGIN_REASON = "login_reason";

	private CheckBox mServerOverrideCheckBox;
	private EditText mServerIpField;
	private EditText mServerPortField;

	private EditText mPasswordField;
	private CheckBox mShowPasswordCheckBox;
	private ImageView mShowPasswordImage;

	/**
	 * {@inheritDoc}
	 */
	@Override
	public View onCreateView(LayoutInflater inflater, ViewGroup container,
			Bundle savedInstanceState)
	{
		Bundle args = getArguments();
		View content = inflater.inflate(R.layout.credentials, container, false);

		ViewUtil.setTextViewValue(content, R.id.username, args.getString(ARG_LOGIN));

		boolean loginEditable = args.getBoolean(ARG_LOGIN_EDITABLE, true);
		content.findViewById(R.id.username).setEnabled(loginEditable);

		mShowPasswordCheckBox = (CheckBox) content.findViewById(R.id.show_password);
		mShowPasswordImage = (ImageView) content.findViewById(R.id.pwdviewImage);

		mPasswordField = (EditText) content.findViewById(R.id.password);
		mPasswordField.setText(args.getString(ARG_PASSWORD));
		// ViewUtil.setTextViewValue(content, R.id.password, args.getString(ARG_PASSWORD));

		ViewUtil.setCompoundChecked(content, R.id.store_password,
				args.getBoolean(ARG_STORE_PASSWORD, true));

		ViewUtil.setCompoundChecked(content, R.id.ib_registration,
				args.getBoolean(ARG_IB_REGISTRATION, false));

		Boolean isShownServerOption = args.getBoolean(ARG_IS_SHOWN_SERVER_OPTION, false);
		if (isShownServerOption) {
			Boolean isServerOverridden = args.getBoolean(ARG_IS_SERVER_OVERRIDDEN, false);
			mServerOverrideCheckBox = (CheckBox) content.findViewById(R.id.serverOverridden);
			ViewUtil.setCompoundChecked(content, R.id.serverOverridden, isServerOverridden);

			mServerIpField = (EditText) content.findViewById(R.id.serverIpField);
			mServerIpField.setText(args.getString(ARG_SERVER_ADDRESS));

			mServerPortField = (EditText) content.findViewById(R.id.serverPortField);
			mServerPortField.setText(args.getString(ARG_SERVER_PORT));
			updateViewVisibility(isServerOverridden);
		}
		else {
			content.findViewById(R.id.serverOverridden).setVisibility(View.GONE);
			content.findViewById(R.id.serverField).setVisibility(View.GONE);
		}

		String loginReason = args.getString(ARG_LOGIN_REASON);
		TextView reasonField = (TextView) content.findViewById(R.id.reason_field);
		reasonField.setText(loginReason);

		initializeViewListeners();
		return content;
	}

	private void initializeViewListeners() {
		mShowPasswordCheckBox.setOnCheckedChangeListener(
				new CompoundButton.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
						showPassword(isChecked);
					}
				});

		mServerOverrideCheckBox.setOnCheckedChangeListener(
				new CompoundButton.OnCheckedChangeListener() {
					@Override
					public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
						updateViewVisibility(isChecked);
					}
				});
	}

	private void showPassword(boolean show) {
		int cursorPosition = mPasswordField.getSelectionStart();
		if (show) {
			mShowPasswordImage.setAlpha(1.0f);
			mPasswordField.setInputType(InputType.TYPE_TEXT_VARIATION_PASSWORD);
		} else {
			mShowPasswordImage.setAlpha(0.3f);
			mPasswordField.setInputType(InputType.TYPE_CLASS_TEXT
					| InputType.TYPE_TEXT_VARIATION_PASSWORD);
		}
		mPasswordField.setSelection(cursorPosition);
	}

	private void updateViewVisibility(boolean IsServerOverridden) {
		if (IsServerOverridden) {
			mServerIpField.setVisibility(View.VISIBLE);
			mServerPortField.setVisibility(View.VISIBLE);
		} else {
			mServerIpField.setVisibility(View.GONE);
			mServerPortField.setVisibility(View.GONE);
		}
	}
}
