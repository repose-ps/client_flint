package rs2.ui.login;

/**
 * Owns the small revision-377 title/login state machine and credential input.
 * Network authentication and whole-client reset behavior remain with client.
 */
public final class LoginScreen {

	/** Creates a new login screen with its default client state. */
	public LoginScreen() {
	}

	/** Constant value for welcome. */
	public static final int WELCOME = 0;
	/** Constant value for credentials. */
	public static final int CREDENTIALS = 2;
	/** Constant value for create account. */
	public static final int CREATE_ACCOUNT = 3;

	/** Performs this client operation. */
	private static final String VALID_CHARACTERS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!\"\243$%^&*()-_=+[{]};:'@#~,<.>/?\\| ";

	/** Stores the current state. */
	public int state = WELCOME;
	/** Stores the current focused field. */
	public int focusedField;
	/** Stores the current username. */
	public String username = "";
	/** Stores the current password. */
	public String password = "";
	/** Stores the current message1. */
	public String message1 = "";
	/** Stores the current message2. */
	public String message2 = "";

	/**
	 * Switches the login UI to the account-creation screen.
	 */
	public void showCreateAccount() {
		state = CREATE_ACCOUNT;
		focusedField = 0;
	}

	/**
	 * Switches the login UI to the credential-entry screen.
	 */
	public void showCredentials() {
		message1 = "";
		message2 = "Enter your username & password.";
		state = CREDENTIALS;
		focusedField = 0;
	}

	/**
	 * Returns whether cel credentials.
	 */
	public void cancelCredentials() {
		state = WELCOME;
		username = "";
		password = "";
	}

	/**
	 * Returns whether cel create account.
	 */
	public void cancelCreateAccount() {
		state = WELCOME;
	}

	/**
	 * Resets for logout.
	 */
	public void resetForLogout() {
		state = WELCOME;
		username = "";
		password = "";
	}

	/**
	 * Preserves the original per-key order, field switching and length caps.
	 * 
	 * @param key the lookup key
	 */
	public void processKey(int key) {
		boolean valid = false;
		for (int index = 0; index < VALID_CHARACTERS.length(); index++) {
			if (key == VALID_CHARACTERS.charAt(index)) {
				valid = true;
				break;
			}
		}

		if (focusedField == 0) {
			if (key == 8 && username.length() > 0) {
				username = username.substring(0, username.length() - 1);
			}
			if (key == 9 || key == 10 || key == 13) {
				focusedField = 1;
			}
			if (valid) {
				username += (char) key;
			}
			if (username.length() > 12) {
				username = username.substring(0, 12);
			}
		} else if (focusedField == 1) {
			if (key == 8 && password.length() > 0) {
				password = password.substring(0, password.length() - 1);
			}
			if (key == 9 || key == 10 || key == 13) {
				focusedField = 0;
			}
			if (valid) {
				password += (char) key;
			}
			if (password.length() > 20) {
				password = password.substring(0, 20);
			}
		}
	}
}
