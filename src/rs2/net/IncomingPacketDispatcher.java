package rs2.net;

import java.io.IOException;

/**
 * Coordinates revision-377 packet framing with application-level packet
 * handling.
 *
 * <p>
 * The dispatcher owns no game, UI, or world state. {@link NetworkSession}
 * produces complete frames and {@link IncomingPacketHandler} applies their
 * effects.
 * </p>
 */
public final class IncomingPacketDispatcher {

	/** Framed transport session supplying complete incoming packets. */
	private final NetworkSession network;
	/** Application-layer consumer of each decoded packet. */
	private final IncomingPacketHandler handler;

	/**
	 * Creates an incoming packet dispatcher.
	 *
	 * @param network framed network session
	 * @param handler application packet handler
	 */
	public IncomingPacketDispatcher(NetworkSession network, IncomingPacketHandler handler) {
		this.network = network;
		this.handler = handler;
	}

	/**
	 * Reads and applies at most one complete packet.
	 *
	 * @return {@code true} when a packet was handled and processing may continue;
	 *         {@code false} when no complete packet was available or handling
	 *         requested the caller to stop
	 * @throws IOException if the transport fails while framing a packet
	 */
	public boolean process() throws IOException {
		if (!network.readIncomingPacket()) {
			return false;
		}
		int opcode = network.incomingOpcode;
		int length = network.incomingLength;
		boolean continueProcessing = handler.handle(opcode, network.incoming, length);
		network.finishIncomingPacket();
		return continueProcessing;
	}
}
