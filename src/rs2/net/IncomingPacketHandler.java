package rs2.net;

/**
 * Applies one complete decoded incoming packet payload to client runtime state.
 */
@FunctionalInterface
public interface IncomingPacketHandler {

	/**
	 * Handles one framed incoming packet.
	 *
	 * @param opcode  decoded revision-377 opcode
	 * @param payload payload buffer positioned at zero
	 * @param length  payload length in bytes
	 * @return {@code false} when packet handling should stop the current packet
	 *         loop
	 */
	boolean handle(int opcode, Buffer payload, int length);
}
