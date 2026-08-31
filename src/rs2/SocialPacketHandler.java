package rs2;

import rs2.chat.ChatCodec;
import rs2.chat.ChatMessageType;
import rs2.chat.Censor;
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

	/** Client runtime receiving decoded packet effects. */
	private final Client client;

	/**
	 * Creates the social packet handler.
	 *
	 * @param client client runtime receiving packet effects
	 */
	SocialPacketHandler(Client client) {
		this.client = client;
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
			client.packetChatController().setPublicMode(buffer.readUnsignedByte());
			client.packetChatController().setPrivateMode(buffer.readUnsignedByte());
			client.packetChatController().setTradeMode(buffer.readUnsignedByte());
			client.requestChatModesRedraw();
			client.requestChatboxRedraw();
			return true;
		}
		if (opcode == IncomingPacketOpcode.ACCOUNT_INFO) {
			client.lastPasswordChangeDate = buffer.readUnsignedShortLE();
			buffer.readUnsignedShortLEAdd();
			buffer.readUnsignedShort();
			buffer.readUnsignedShort();
			client.accountCurrentDay = buffer.readUnsignedShortLE();
			client.unreadMessageCount = buffer.readUnsignedShortAdd();
			client.lastLoginDay = buffer.readUnsignedShortAdd();
			client.membershipDays = buffer.readUnsignedShort();
			client.lastLoginIp = buffer.readIntLE();
			client.recoveryQuestionsDate = buffer.readUnsignedShortLEAdd();
			buffer.readUnsignedByteAdd();
			Signlink.lookupDns(Ipv4Address.format(client.lastLoginIp));
			return true;
		}
		if (opcode == IncomingPacketOpcode.SERVER_MESSAGE) {
			String serverMessage = buffer.readString();
			if (serverMessage.endsWith(":tradereq:")) {
				String tradeRequester = serverMessage.substring(0, serverMessage.indexOf(":"));
				long tradeRequesterEncoded = Base37.encode(tradeRequester);
				boolean ignored = client.packetSocialManager().isIgnored(tradeRequesterEncoded);
				if (!ignored && client.tutorialIslandFlag == 0)
					client.addChatMessage(tradeRequester, "wishes to trade with you.", ChatMessageType.TRADE_REQUEST);
			} else if (serverMessage.endsWith(":duelreq:")) {
				String duelRequester = serverMessage.substring(0, serverMessage.indexOf(":"));
				long duelRequesterEncoded = Base37.encode(duelRequester);
				boolean ignored = client.packetSocialManager().isIgnored(duelRequesterEncoded);
				if (!ignored && client.tutorialIslandFlag == 0)
					client.addChatMessage(duelRequester, "wishes to duel with you.", ChatMessageType.CHALLENGE_REQUEST);
			} else if (serverMessage.endsWith(":chalreq:")) {
				String challengeRequester = serverMessage.substring(0, serverMessage.indexOf(":"));
				long challengeRequesterEncoded = Base37.encode(challengeRequester);
				boolean ignored = client.packetSocialManager().isIgnored(challengeRequesterEncoded);
				if (!ignored && client.tutorialIslandFlag == 0) {
					String challengeText = serverMessage.substring(serverMessage.indexOf(":") + 1,
							serverMessage.length() - 9);
					client.addChatMessage(challengeRequester, challengeText, ChatMessageType.CHALLENGE_REQUEST);
				}
			} else {
				client.addChatMessage("", serverMessage, ChatMessageType.GAME);
			}
			return true;
		}
		if (opcode == IncomingPacketOpcode.FRIEND_STATUS) {
			long encodedName = buffer.readLong();
			int world = buffer.readUnsignedByte();
			if (client.packetSocialManager().updateFriend(encodedName, world, client.currentWorldId, client::addChatMessage))
				client.requestSidebarRedraw();
			return true;
		}
		if (opcode == IncomingPacketOpcode.PRIVATE_MESSAGE) {
			long senderEncodedName = buffer.readLong();
			int privateMessageId = buffer.readInt();
			int senderRights = buffer.readUnsignedByte();
			boolean duplicateOrIgnored = client.packetChatController().history().hasRecentPrivateMessage(privateMessageId);

			if (senderRights <= 1 && client.packetSocialManager().isIgnored(senderEncodedName))
				duplicateOrIgnored = true;
			if (!duplicateOrIgnored && client.tutorialIslandFlag == 0)
				try {
					client.packetChatController().history().rememberPrivateMessage(privateMessageId);
					String privateMessage = ChatCodec.decode(buffer,
							packetSize - 13);
					if (senderRights != 3)
						privateMessage = Censor.censor(privateMessage);
					if (senderRights == 2 || senderRights == 3)
						client.addChatMessage("@cr2@" + TextFormatter.formatDisplayName(Base37.decode(senderEncodedName)),
								privateMessage, 7);
					else if (senderRights == 1)
						client.addChatMessage("@cr1@" + TextFormatter.formatDisplayName(Base37.decode(senderEncodedName)),
								privateMessage, 7);
					else
						client.addChatMessage(TextFormatter.formatDisplayName(Base37.decode(senderEncodedName)),
								privateMessage, 3);
				} catch (Exception exception1) {
					Signlink.reportError("cde1");
				}
			return true;
		}
		if (opcode == IncomingPacketOpcode.UPDATE_IGNORE_LIST) {
			client.packetSocialManager().replaceIgnoreList(buffer, packetSize);
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_FRIEND_LIST_STATUS) {
			client.packetSocialManager().friendListStatus = buffer.readUnsignedByte();
			client.requestSidebarRedraw();
			return true;
		}
		throw new IllegalArgumentException("Opcode " + opcode + " is not a social packet");
	}
}
