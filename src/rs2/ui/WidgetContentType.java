package rs2.ui;

/**
 * Revision-377 widget content-type identifiers interpreted directly by the
 * client.
 *
 * <p>
 * Unlike {@link Widget#type}, content types attach client behavior or dynamic
 * text to otherwise cache-defined widgets.
 * </p>
 */
public final class WidgetContentType {

	/** No client-interpreted content behavior. */
	public static final int NONE = 0;

	/** Prevents instantiation. */
	private WidgetContentType() {
	}

	/** Content type for friend name first. */
	public static final int FRIEND_NAME_FIRST = 1;
	/** Content type for the second primary friend-name row. */
	public static final int FRIEND_NAME_SECOND = 2;
	/** Content type for friend name last. */
	public static final int FRIEND_NAME_LAST = 100;
	/** Content type for friend world first. */
	public static final int FRIEND_WORLD_FIRST = 101;
	/** Content type for friend world last. */
	public static final int FRIEND_WORLD_LAST = 200;
	/** Content type for add friend. */
	public static final int ADD_FRIEND = 201;
	/** Content type for remove friend. */
	public static final int REMOVE_FRIEND = 202;
	/** Content type for friend list scroll. */
	public static final int FRIEND_LIST_SCROLL = 203;
	/** Content type for logout. */
	public static final int LOGOUT = 205;
	/** Content type for insertable inventory. */
	public static final int INSERTABLE_INVENTORY = 206;

	/** Content type for appearance kit first. */
	public static final int APPEARANCE_KIT_FIRST = 300;
	/** Content type for appearance kit last. */
	public static final int APPEARANCE_KIT_LAST = 313;
	/** Content type for appearance color first. */
	public static final int APPEARANCE_COLOR_FIRST = 314;
	/** Content type for appearance color last. */
	public static final int APPEARANCE_COLOR_LAST = 323;
	/** Content type for select male appearance. */
	public static final int SELECT_MALE_APPEARANCE = 324;
	/** Content type for select female appearance. */
	public static final int SELECT_FEMALE_APPEARANCE = 325;
	/** Content type for accept appearance. */
	public static final int ACCEPT_APPEARANCE = 326;
	/** Content type for appearance preview. */
	public static final int APPEARANCE_PREVIEW = 327;

	/** Content type for ignore name first. */
	public static final int IGNORE_NAME_FIRST = 401;
	/** Content type for ignore name last. */
	public static final int IGNORE_NAME_LAST = 500;
	/** Content type for add ignore. */
	public static final int ADD_IGNORE = 501;
	/** Content type for remove ignore. */
	public static final int REMOVE_IGNORE = 502;
	/** Content type for ignore list scroll. */
	public static final int IGNORE_LIST_SCROLL = 503;

	/** Content type for report abuse name. */
	public static final int REPORT_ABUSE_NAME = 600;
	/** Content type for report abuse rule first. */
	public static final int REPORT_ABUSE_RULE_FIRST = 601;
	/** Content type for report abuse rule last. */
	public static final int REPORT_ABUSE_RULE_LAST = 613;
	/** Content type for report abuse mute. */
	public static final int REPORT_ABUSE_MUTE = 620;

	/**
	 * Cache marker whose parent interface is retained for legacy account UI use.
	 */
	public static final int LEGACY_INTERFACE_MARKER_650 = 650;
	/**
	 * Cache marker whose parent interface is retained for legacy account UI use.
	 */
	public static final int LEGACY_INTERFACE_MARKER_655 = 655;

	/** Content type for account last login. */
	public static final int ACCOUNT_LAST_LOGIN = 660;
	/** Content type for account recovery questions. */
	public static final int ACCOUNT_RECOVERY_QUESTIONS = 661;
	/** Content type for account unread messages. */
	public static final int ACCOUNT_UNREAD_MESSAGES = 662;
	/** Content type for account password change. */
	public static final int ACCOUNT_PASSWORD_CHANGE = 663;
	/** Content type for account membership status. */
	public static final int ACCOUNT_MEMBERSHIP_STATUS = 665;
	/** Content type for account membership help. */
	public static final int ACCOUNT_MEMBERSHIP_HELP = 667;
	/** Content type for account recovery help. */
	public static final int ACCOUNT_RECOVERY_HELP = 668;

	/** Offset that maps alternate friend-name content types onto friend indices. */
	public static final int FRIEND_NAME_ALTERNATE_INDEX_OFFSET = 601;
	/** Content type for friend name alternate first. */
	public static final int FRIEND_NAME_ALTERNATE_FIRST = 701;
	/** Content type for friend name alternate last. */
	public static final int FRIEND_NAME_ALTERNATE_LAST = 800;
	/**
	 * Offset that maps alternate friend-world content types onto friend indices.
	 */
	public static final int FRIEND_WORLD_ALTERNATE_INDEX_OFFSET = 701;
	/** Content type for friend world alternate first. */
	public static final int FRIEND_WORLD_ALTERNATE_FIRST = 801;
	/** Content type for friend world alternate last. */
	public static final int FRIEND_WORLD_ALTERNATE_LAST = 900;
}
