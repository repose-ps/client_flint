package rs2.chat;

/**
 * Owns the revision-377 fixed-size chat history.
 *
 * <p>
 * Entries are newest-first. Adding a message shifts the existing 99 visible
 * entries down by one, exactly matching the original client behavior.
 * </p>
 */
public final class ChatHistory {

	/** Creates a new chat history with its default client state. */
	public ChatHistory() {
	}
	/** Constant value for capacity. */
	public static final int CAPACITY = 100;

	/** Stores types values. */
	public final int[] types = new int[CAPACITY];
	/** Stores senders values. */
	public final String[] senders = new String[CAPACITY];
	/** Stores messages values. */
	public final String[] messages = new String[CAPACITY];
	/** Stores recent private message IDs values. */
	public final int[] recentPrivateMessageIds = new int[CAPACITY];
	/** Stores the current recent private message index. */
	public int recentPrivateMessageIndex;

	/**
	 * Adds the operation.
	 *
	 * @param sender the sender
	 * @param message the message text
	 * @param type the type
	 */
	public void add(String sender, String message, int type) {
		for (int index = CAPACITY - 1; index > 0; index--) {
			types[index] = types[index - 1];
			senders[index] = senders[index - 1];
			messages[index] = messages[index - 1];
		}
		types[0] = type;
		senders[0] = sender;
		messages[0] = message;
	}

	/**
	 * Returns whether recent private message.
	 *
	 * @param messageId the message ID
	 * @return whether recent private message
	 */
	public boolean hasRecentPrivateMessage(int messageId) {
		for (int index = 0; index < CAPACITY; index++) {
			if (recentPrivateMessageIds[index] == messageId) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Records a private-message identifier in the duplicate-detection history.
	 *
	 * @param messageId the message ID
	 */
	public void rememberPrivateMessage(int messageId) {
		recentPrivateMessageIds[recentPrivateMessageIndex] = messageId;
		recentPrivateMessageIndex = (recentPrivateMessageIndex + 1) % CAPACITY;
	}

	/**
	 * Clears only the message slots, matching the login reset in the original
	 * client. Stale type/sender entries remain unreachable while message is null.
	 */
	public void clearMessages() {
		for (int index = 0; index < CAPACITY; index++) {
			messages[index] = null;
		}
	}
}
