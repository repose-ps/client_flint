package rs2.net;

import rs2.chat.ChatCodec;

/**
 * Encodes revision-377 outgoing chat/social packets without changing wire
 * order.
 */
public final class ChatPacketEncoder {

	/**
	 * Creates a new chat packet encoder.
	 */
	private ChatPacketEncoder() {
	}

	/**
	 * Opcode 176: public/private/trade chat mode settings.
	 *
	 * @param outgoing    the outgoing
	 * @param publicMode  the public mode
	 * @param privateMode the private mode
	 * @param tradeMode   the trade mode
	 */
	public static void writeChatModes(Buffer outgoing, int publicMode, int privateMode, int tradeMode) {
		outgoing.writeOpcode(176);
		outgoing.writeByte(publicMode);
		outgoing.writeByte(privateMode);
		outgoing.writeByte(tradeMode);
	}

	/**
	 * Opcode 227: private message with one-byte payload length backfill.
	 *
	 * @param outgoing  the outgoing
	 * @param recipient the recipient
	 * @param message   the message
	 */
	public static void writePrivateMessage(Buffer outgoing, long recipient, String message) {
		outgoing.writeOpcode(227);
		outgoing.writeByte(0);
		int payloadStart = outgoing.position;
		outgoing.writeLong(recipient);
		ChatCodec.encode(message, outgoing);
		outgoing.writeLength(outgoing.position - payloadStart);
	}

	/**
	 * Opcode 49: public chat with transformed color/effect bytes.
	 *
	 * @param outgoing the outgoing
	 * @param color    the color
	 * @param effect   the effect
	 * @param message  the message
	 * @param scratch  the scratch
	 */
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

	/**
	 * Opcode 56: command text after the leading "::" marker.
	 *
	 * @param outgoing     the outgoing
	 * @param commandInput the command input
	 */
	public static void writeCommand(Buffer outgoing, String commandInput) {
		outgoing.writeOpcode(56);
		outgoing.writeByte(commandInput.length() - 1);
		outgoing.writeString(commandInput.substring(2));
	}
}
