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
	public static final int CAPACITY = 100;

	public final int[] types = new int[CAPACITY];
	public final String[] senders = new String[CAPACITY];
	public final String[] messages = new String[CAPACITY];
	public final int[] recentPrivateMessageIds = new int[CAPACITY];
	public int recentPrivateMessageIndex;

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

	public boolean hasRecentPrivateMessage(int messageId) {
		for (int index = 0; index < CAPACITY; index++) {
			if (recentPrivateMessageIds[index] == messageId) {
				return true;
			}
		}
		return false;
	}

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
