package rs2.chat;

/**
 * Revision-377 chat visibility mode identifiers used by public, private, and
 * trade/challenge chat controls.
 */
public final class ChatMode {

	/** Chat is visible from everyone. */
	public static final int ON = 0;
	/** Chat is visible only from friends. */
	public static final int FRIENDS = 1;
	/** Chat is disabled. */
	public static final int OFF = 2;
	/** Public chat is hidden from the chatbox while still available overhead. */
	public static final int HIDE = 3;

	/** Number of selectable public-chat modes. */
	public static final int PUBLIC_MODE_COUNT = 4;
	/** Number of selectable private/trade modes. */
	public static final int STANDARD_MODE_COUNT = 3;

	/** Prevents instantiation. */
	private ChatMode() {
	}
}
