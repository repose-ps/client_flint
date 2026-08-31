package rs2;

import java.util.function.IntSupplier;

import rs2.chat.ChatCodec;
import rs2.chat.ChatController;
import rs2.chat.ChatMessageType;
import rs2.chat.Censor;
import rs2.chat.SocialManager;
import rs2.net.Buffer;
import rs2.net.IncomingPacketOpcode;
import rs2.net.Ipv4Address;
import rs2.sign.Signlink;
import rs2.text.Base37;
import rs2.text.TextFormatter;

/**
 * Applies chat, social-list, private-message, and account-status packets.
 *
 * <p>This application-layer domain handler is invoked only after
 * {@link ClientIncomingPacketHandler} has explicitly routed a recognized
 * revision-377 opcode to it.</p>
 */
final class SocialPacketHandler {

	/** Social-list owner mutated by social packets. */
	private final SocialManager social;
	/** Chat modes/history owner mutated by social packets. */
	private final ChatController chat;
	/** Supplies tutorial-area suppression state. */
	private final IntSupplier tutorialIslandFlag;
	/** Supplies the current world identifier used by friend status ordering. */
	private final IntSupplier currentWorldId;
	/** Receives chat-history messages produced by social packets. */
	private final SocialManager.MessageSink messages;
	/** Receives the decoded account-status snapshot. */
	private final AccountInfoSink accountInfo;
	/** Requests chat-mode-strip redraws. */
	private final Runnable redrawChatModes;
	/** Requests chatbox redraws. */
	private final Runnable redrawChatbox;
	/** Requests sidebar redraws. */
	private final Runnable redrawSidebar;

	/**
	 * Receives account-status values decoded from one server packet.
	 */
	@FunctionalInterface
	interface AccountInfoSink {
		/**
		 * Applies the decoded account-status snapshot.
		 *
		 * @param lastPasswordChangeDate last password-change day
		 * @param accountCurrentDay account current day
		 * @param unreadMessageCount unread message count
		 * @param lastLoginDay last-login day
		 * @param membershipDays remaining membership days
		 * @param lastLoginIp last-login IPv4 address
		 * @param recoveryQuestionsDate recovery-question date
		 */
		void update(int lastPasswordChangeDate, int accountCurrentDay, int unreadMessageCount, int lastLoginDay,
				int membershipDays, int lastLoginIp, int recoveryQuestionsDate);
	}

	/**
	 * Creates the social packet handler from its exact application capabilities.
	 *
	 * @param social social-list owner
	 * @param chat chat modes/history owner
	 * @param tutorialIslandFlag tutorial-area state supplier
	 * @param currentWorldId current-world supplier
	 * @param messages chat-message sink
	 * @param accountInfo account-status sink
	 * @param redrawChatModes chat-mode redraw callback
	 * @param redrawChatbox chatbox redraw callback
	 * @param redrawSidebar sidebar redraw callback
	 */
	SocialPacketHandler(SocialManager social, ChatController chat,
			IntSupplier tutorialIslandFlag, IntSupplier currentWorldId, SocialManager.MessageSink messages,
			AccountInfoSink accountInfo, Runnable redrawChatModes, Runnable redrawChatbox, Runnable redrawSidebar) {
		this.social = social;
		this.chat = chat;
		this.tutorialIslandFlag = tutorialIslandFlag;
		this.currentWorldId = currentWorldId;
		this.messages = messages;
		this.accountInfo = accountInfo;
		this.redrawChatModes = redrawChatModes;
		this.redrawChatbox = redrawChatbox;
		this.redrawSidebar = redrawSidebar;
	}

	/**
	 * Applies one packet already routed to this domain.
	 *
	 * @param opcode decoded revision-377 opcode
	 * @param buffer payload buffer positioned at zero
	 * @param packetSize payload length in bytes
	 * @return always {@code true}; routed domain packets continue processing
	 * @throws IllegalArgumentException if the opcode was routed to the wrong domain
	 */
	boolean handle(int opcode, Buffer buffer, int packetSize) {
		if (opcode == IncomingPacketOpcode.SET_CHAT_MODES) {
			chat.setPublicMode(buffer.readUnsignedByte());
			chat.setPrivateMode(buffer.readUnsignedByte());
			chat.setTradeMode(buffer.readUnsignedByte());
			redrawChatModes.run();
			redrawChatbox.run();
			return true;
		}
		if (opcode == IncomingPacketOpcode.ACCOUNT_INFO) {
			int lastPasswordChangeDate = buffer.readUnsignedShortLE();
			buffer.readUnsignedShortLEAdd();
			buffer.readUnsignedShort();
			buffer.readUnsignedShort();
			int accountCurrentDay = buffer.readUnsignedShortLE();
			int unreadMessageCount = buffer.readUnsignedShortAdd();
			int lastLoginDay = buffer.readUnsignedShortAdd();
			int membershipDays = buffer.readUnsignedShort();
			int lastLoginIp = buffer.readIntLE();
			int recoveryQuestionsDate = buffer.readUnsignedShortLEAdd();
			buffer.readUnsignedByteAdd();
			accountInfo.update(lastPasswordChangeDate, accountCurrentDay, unreadMessageCount, lastLoginDay,
					membershipDays, lastLoginIp, recoveryQuestionsDate);
			Signlink.lookupDns(Ipv4Address.format(lastLoginIp));
			return true;
		}
		if (opcode == IncomingPacketOpcode.SERVER_MESSAGE) {
			String serverMessage = buffer.readString();
			if (serverMessage.endsWith(":tradereq:")) {
				String tradeRequester = serverMessage.substring(0, serverMessage.indexOf(":"));
				long tradeRequesterEncoded = Base37.encode(tradeRequester);
				boolean ignored = social.isIgnored(tradeRequesterEncoded);
				if (!ignored && tutorialIslandFlag.getAsInt() == 0)
					messages.addChatMessage(tradeRequester, "wishes to trade with you.", ChatMessageType.TRADE_REQUEST);
			} else if (serverMessage.endsWith(":duelreq:")) {
				String duelRequester = serverMessage.substring(0, serverMessage.indexOf(":"));
				long duelRequesterEncoded = Base37.encode(duelRequester);
				boolean ignored = social.isIgnored(duelRequesterEncoded);
				if (!ignored && tutorialIslandFlag.getAsInt() == 0)
					messages.addChatMessage(duelRequester, "wishes to duel with you.", ChatMessageType.CHALLENGE_REQUEST);
			} else if (serverMessage.endsWith(":chalreq:")) {
				String challengeRequester = serverMessage.substring(0, serverMessage.indexOf(":"));
				long challengeRequesterEncoded = Base37.encode(challengeRequester);
				boolean ignored = social.isIgnored(challengeRequesterEncoded);
				if (!ignored && tutorialIslandFlag.getAsInt() == 0) {
					String challengeText = serverMessage.substring(serverMessage.indexOf(":") + 1,
							serverMessage.length() - 9);
					messages.addChatMessage(challengeRequester, challengeText, ChatMessageType.CHALLENGE_REQUEST);
				}
			} else {
				messages.addChatMessage("", serverMessage, ChatMessageType.GAME);
			}
			return true;
		}
		if (opcode == IncomingPacketOpcode.FRIEND_STATUS) {
			long encodedName = buffer.readLong();
			int world = buffer.readUnsignedByte();
			if (social.updateFriend(encodedName, world, currentWorldId.getAsInt(), messages))
				redrawSidebar.run();
			return true;
		}
		if (opcode == IncomingPacketOpcode.PRIVATE_MESSAGE) {
			long senderEncodedName = buffer.readLong();
			int privateMessageId = buffer.readInt();
			int senderRights = buffer.readUnsignedByte();
			boolean duplicateOrIgnored = chat.history().hasRecentPrivateMessage(privateMessageId);

			if (senderRights <= 1 && social.isIgnored(senderEncodedName))
				duplicateOrIgnored = true;
			if (!duplicateOrIgnored && tutorialIslandFlag.getAsInt() == 0)
				try {
					chat.history().rememberPrivateMessage(privateMessageId);
					String privateMessage = ChatCodec.decode(buffer,
							packetSize - 13);
					if (senderRights != 3)
						privateMessage = Censor.censor(privateMessage);
					if (senderRights == 2 || senderRights == 3)
						messages.addChatMessage("@cr2@" + TextFormatter.formatDisplayName(Base37.decode(senderEncodedName)),
								privateMessage, 7);
					else if (senderRights == 1)
						messages.addChatMessage("@cr1@" + TextFormatter.formatDisplayName(Base37.decode(senderEncodedName)),
								privateMessage, 7);
					else
						messages.addChatMessage(TextFormatter.formatDisplayName(Base37.decode(senderEncodedName)),
								privateMessage, 3);
				} catch (Exception exception1) {
					Signlink.reportError("cde1");
				}
			return true;
		}
		if (opcode == IncomingPacketOpcode.UPDATE_IGNORE_LIST) {
			social.replaceIgnoreList(buffer, packetSize);
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_FRIEND_LIST_STATUS) {
			social.friendListStatus = buffer.readUnsignedByte();
			redrawSidebar.run();
			return true;
		}
		throw new IllegalArgumentException("Opcode " + opcode + " is not a social packet");
	}
}
