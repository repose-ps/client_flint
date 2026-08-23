package rs2.chat;

import rs2.net.Buffer;
import rs2.text.Base37;
import rs2.text.TextFormatter;

/**
 * Owns revision-377 friend/ignore state and the exact list mutation protocol.
 *
 * <p>
 * This intentionally keeps the original fixed capacities and friend sorting
 * rules. It does not own widgets, chatbox rendering, menus, or login UI.
 * </p>
 */
public final class SocialManager {
	public static final int MAX_FRIENDS = 200;
	public static final int MAX_FREE_FRIENDS = 100;
	public static final int MAX_IGNORES = 100;

	public int friendCount;
	public String[] friendNames = new String[MAX_FRIENDS];
	public long[] friendEncodedNames = new long[MAX_FRIENDS];
	public int[] friendWorlds = new int[MAX_FRIENDS];
	public int friendListStatus;

	public int ignoreCount;
	public final long[] ignoreEncodedNames = new long[MAX_IGNORES];

	public interface MessageSink {
		void addChatMessage(String sender, String message, int type);
	}

	/** Mirrors the original quit-time nulling of the three friend-list arrays. */
	public void clearFriendReferencesForQuit() {
		friendNames = null;
		friendEncodedNames = null;
		friendWorlds = null;
	}

	/** Login resets only the friend-server state/count in the supplied client. */
	public void resetForLogin() {
		friendListStatus = 0;
		friendCount = 0;
	}

	public boolean isIgnored(long encodedName) {
		for (int index = 0; index < ignoreCount; index++) {
			if (ignoreEncodedNames[index] == encodedName) {
				return true;
			}
		}
		return false;
	}

	public boolean isFriend(String name) {
		if (name == null) {
			return false;
		}
		for (int index = 0; index < friendCount; index++) {
			if (name.equalsIgnoreCase(friendNames[index])) {
				return true;
			}
		}
		return false;
	}

	public boolean isFriendOrSelf(String name, String localPlayerName) {
		if (isFriend(name)) {
			return true;
		}
		return name != null && localPlayerName != null && name.equalsIgnoreCase(localPlayerName);
	}

	public int findFriendIndex(long encodedName) {
		for (int index = 0; index < friendCount; index++) {
			if (friendEncodedNames[index] == encodedName) {
				return index;
			}
		}
		return -1;
	}

	public boolean addFriend(long encodedName, boolean membersAccount, String localPlayerName, Buffer outgoing,
			MessageSink messages) {
		if (encodedName == 0L) {
			return false;
		}
		if (friendCount >= MAX_FREE_FRIENDS && !membersAccount || friendCount >= MAX_FRIENDS) {
			messages.addChatMessage("", "Your friendlist is full. Max of 100 for free users, and 200 for members", 0);
			return false;
		}
		String displayName = TextFormatter.formatDisplayName(Base37.decode(encodedName));
		for (int index = 0; index < friendCount; index++) {
			if (friendEncodedNames[index] == encodedName) {
				messages.addChatMessage("", displayName + " is already on your friend list", 0);
				return false;
			}
		}
		for (int index = 0; index < ignoreCount; index++) {
			if (ignoreEncodedNames[index] == encodedName) {
				messages.addChatMessage("", "Please remove " + displayName + " from your ignore list first", 0);
				return false;
			}
		}
		if (displayName.equals(localPlayerName)) {
			return false;
		}

		friendNames[friendCount] = displayName;
		friendEncodedNames[friendCount] = encodedName;
		friendWorlds[friendCount] = 0;
		friendCount++;
		outgoing.writeOpcode(120);
		outgoing.writeLong(encodedName);
		return true;
	}

	public boolean removeFriend(long encodedName, Buffer outgoing) {
		if (encodedName == 0L) {
			return false;
		}
		for (int index = 0; index < friendCount; index++) {
			if (friendEncodedNames[index] != encodedName) {
				continue;
			}
			friendCount--;
			for (int shift = index; shift < friendCount; shift++) {
				friendNames[shift] = friendNames[shift + 1];
				friendWorlds[shift] = friendWorlds[shift + 1];
				friendEncodedNames[shift] = friendEncodedNames[shift + 1];
			}
			outgoing.writeOpcode(141);
			outgoing.writeLong(encodedName);
			return true;
		}
		return false;
	}

	public boolean addIgnore(long encodedName, Buffer outgoing, MessageSink messages) {
		if (encodedName == 0L) {
			return false;
		}
		if (ignoreCount >= MAX_IGNORES) {
			messages.addChatMessage("", "Your ignore list is full. Max of 100 hit", 0);
			return false;
		}
		String displayName = TextFormatter.formatDisplayName(Base37.decode(encodedName));
		for (int index = 0; index < ignoreCount; index++) {
			if (ignoreEncodedNames[index] == encodedName) {
				messages.addChatMessage("", displayName + " is already on your ignore list", 0);
				return false;
			}
		}
		for (int index = 0; index < friendCount; index++) {
			if (friendEncodedNames[index] == encodedName) {
				messages.addChatMessage("", "Please remove " + displayName + " from your friend list first", 0);
				return false;
			}
		}

		ignoreEncodedNames[ignoreCount++] = encodedName;
		outgoing.writeOpcode(217);
		outgoing.writeLong(encodedName);
		return true;
	}

	public boolean removeIgnore(long encodedName, Buffer outgoing) {
		if (encodedName == 0L) {
			return false;
		}
		for (int index = 0; index < ignoreCount; index++) {
			if (ignoreEncodedNames[index] != encodedName) {
				continue;
			}
			ignoreCount--;
			for (int shift = index; shift < ignoreCount; shift++) {
				ignoreEncodedNames[shift] = ignoreEncodedNames[shift + 1];
			}
			outgoing.writeOpcode(160);
			outgoing.writeLong(encodedName);
			return true;
		}
		return false;
	}

	public void replaceIgnoreList(Buffer incoming, int packetLength) {
		ignoreCount = packetLength / 8;
		for (int index = 0; index < ignoreCount; index++) {
			ignoreEncodedNames[index] = incoming.readLong();
		}
	}

	/**
	 * Applies opcode-78 friend presence updates and preserves the original
	 * current-world/online bubble-sort ordering.
	 *
	 * @return true when visible friend-list state/order changed
	 */
	public boolean updateFriend(long encodedName, int world, int currentWorld, MessageSink messages) {
		String displayName = TextFormatter.formatDisplayName(Base37.decode(encodedName));
		boolean changed = false;
		for (int index = 0; index < friendCount; index++) {
			if (encodedName != friendEncodedNames[index]) {
				continue;
			}
			if (friendWorlds[index] != world) {
				friendWorlds[index] = world;
				changed = true;
				if (world > 0) {
					messages.addChatMessage("", displayName + " has logged in.", 5);
				}
				if (world == 0) {
					messages.addChatMessage("", displayName + " has logged out.", 5);
				}
			}
			displayName = null;
			break;
		}

		if (displayName != null && friendCount < MAX_FRIENDS) {
			friendEncodedNames[friendCount] = encodedName;
			friendNames[friendCount] = displayName;
			friendWorlds[friendCount] = world;
			friendCount++;
			changed = true;
		}

		for (boolean sorted = false; !sorted;) {
			sorted = true;
			for (int index = 0; index < friendCount - 1; index++) {
				if (friendWorlds[index] != currentWorld && friendWorlds[index + 1] == currentWorld
						|| friendWorlds[index] == 0 && friendWorlds[index + 1] != 0) {
					int worldSwap = friendWorlds[index];
					friendWorlds[index] = friendWorlds[index + 1];
					friendWorlds[index + 1] = worldSwap;

					String nameSwap = friendNames[index];
					friendNames[index] = friendNames[index + 1];
					friendNames[index + 1] = nameSwap;

					long encodedSwap = friendEncodedNames[index];
					friendEncodedNames[index] = friendEncodedNames[index + 1];
					friendEncodedNames[index + 1] = encodedSwap;

					changed = true;
					sorted = false;
				}
			}
		}
		return changed;
	}
}
