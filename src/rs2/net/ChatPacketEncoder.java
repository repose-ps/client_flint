package rs2.net;

import rs2.chat.ChatCodec;

/**
 * Encodes revision-377 outgoing chat/social packets without changing wire
 * order.
 */
public final class ChatPacketEncoder {
	private ChatPacketEncoder() {
	}

	/** Opcode 176: public/private/trade chat mode settings. */
	public static void writeChatModes(Buffer outgoing, int publicMode, int privateMode, int tradeMode) {
		outgoing.writeOpcode(176);
		outgoing.writeByte(publicMode);
		outgoing.writeByte(privateMode);
		outgoing.writeByte(tradeMode);
	}

	/** Opcode 227: private message with one-byte payload length backfill. */
	public static void writePrivateMessage(Buffer outgoing, long recipient, String message) {
		outgoing.writeOpcode(227);
		outgoing.writeByte(0);
		int payloadStart = outgoing.position;
		outgoing.writeLong(recipient);
		ChatCodec.encode(message, outgoing);
		outgoing.writeLength(outgoing.position - payloadStart);
	}

	/** Opcode 49: public chat with transformed color/effect bytes. */
	public static void writePublicMessage(Buffer outgoing, int color, int effect, String message, Buffer scratch) {
		outgoing.writeOpcode(49);
		outgoing.writeByte(0);
		int payloadStart = outgoing.position;
		outgoing.writeByteNeg(color);
		outgoing.writeByteAdd(effect);
		scratch.position = 0;
		ChatCodec.encode(message, scratch);
		outgoing.writeBytes(scratch.payload, 0, scratch.position);
		outgoing.writeLength(outgoing.position - payloadStart);
	}

	/** Opcode 56: command text after the leading "::" marker. */
	public static void writeCommand(Buffer outgoing, String commandInput) {
		outgoing.writeOpcode(56);
		outgoing.writeByte(commandInput.length() - 1);
		outgoing.writeString(commandInput.substring(2));
	}
}