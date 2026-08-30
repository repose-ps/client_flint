package rs2.chat;

/**
 * Client chat-history message categories used by revision 377.
 *
 * <p>The values are local presentation identifiers rather than game packet
 * opcodes. They determine chatbox/split-chat formatting and menu behavior.</p>
 */
public final class ChatMessageType {

	/** Plain game/system message without a sender. */
	public static final int GAME = 0;
	/** Privileged public chat that bypasses the normal public-chat visibility filter. */
	public static final int PUBLIC_PRIVILEGED = 1;
	/** Normal public player chat. */
	public static final int PUBLIC = 2;
	/** Normal received private message. */
	public static final int PRIVATE_RECEIVED = 3;
	/** Trade request. */
	public static final int TRADE_REQUEST = 4;
	/** Private-channel status/system message, such as friend login state. */
	public static final int PRIVATE_STATUS = 5;
	/** Private message sent by the local player. */
	public static final int PRIVATE_SENT = 6;
	/** Privileged received private message that bypasses private-chat filtering. */
	public static final int PRIVATE_RECEIVED_PRIVILEGED = 7;
	/** Duel or challenge request. */
	public static final int CHALLENGE_REQUEST = 8;

	/** Prevents instantiation. */
	private ChatMessageType() {
	}
}
