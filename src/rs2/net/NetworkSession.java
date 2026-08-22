package rs2.net;

import java.io.IOException;
import java.net.Socket;

/**
 * Owns the live game-server transport and revision-377 packet framing state.
 *
 * <p>
 * The client still owns packet <em>dispatch</em>: this class only turns the
 * byte stream into complete opcode/length/payload frames and provides the
 * shared outgoing buffer. Keeping dispatch out of this class prevents network
 * code from depending on world, UI, chat, or actor state.
 * </p>
 *
 * <p>
 * Several framing fields remain public during the incremental client refactor
 * because untouched client subsystems still contain decompiler dummy branches
 * that read or mutate them. Those accesses can be removed as the corresponding
 * systems are refactored.
 * </p>
 */
public final /**
				 * Initializes this instance.
				 */
class NetworkSession {

	/**
	 * Stores buffer capacity.
	 */
	public static final int BUFFER_CAPACITY = 5_000;

	/** Maximum payload that fits in the revision-377 incoming packet buffer. */
	private static final int MAX_INCOMING_PAYLOAD_LENGTH = BUFFER_CAPACITY;

	/** Shared buffer used by all outgoing game packets. */
	public final Buffer outgoing = new Buffer(BUFFER_CAPACITY);

	/** Shared payload buffer for the current incoming packet. */
	public final Buffer incoming = new Buffer(BUFFER_CAPACITY);

	/** Length of the current incoming packet payload. */
	public int incomingLength;

	/** Current decoded incoming opcode, or -1 while awaiting a new opcode. */
	public int incomingOpcode = -1;

	/** Number of game ticks since a complete incoming packet was received. */
	public int incomingIdleCycles;

	/** Number of game ticks since the outgoing buffer was last flushed. */
	public int outgoingIdleCycles;

	/** Most recently completed incoming opcode. */
	public int lastOpcode = -1;

	/** Opcode completed immediately before {@link #lastOpcode}. */
	public int secondLastOpcode = -1;

	/** Opcode completed immediately before {@link #secondLastOpcode}. */
	public int thirdLastOpcode = -1;

	/**
	 * Stores connection.
	 */
	private BufferedConnection connection;
	/**
	 * Stores incoming opcode cipher.
	 */
	private IsaacCipher incomingOpcodeCipher;

	/**
	 * Replaces the live game connection with a connection around {@code socket}.
	 * 
	 * @param socket the socket
	 */
	public void connect(Socket socket) throws IOException {
		connection = new BufferedConnection(socket);
	}

	/**
	 * Returns the current connection for the reconnect path's old-socket cleanup.
	 */
	public BufferedConnection getConnection() {
		return connection;
	}

	/**
	 * Returns whether connected.
	 * 
	 * @return the resulting boolean
	 */
	public boolean isConnected() {
		return connection != null;
	}

	/**
	 * Closes and forgets the current connection.
	 */
	public void closeConnection() {
		if (connection == null) {
			return;
		}

		try {
			connection.close();
		} catch (IOException ignored) {
		}
		connection = null;
	}

	/**
	 * Raw login-handshake read.
	 */
	public int read() throws IOException {
		if (connection == null) {
			return -1;
		}
		return connection.read();
	}

	/**
	 * Raw login-handshake block read.
	 * 
	 * @param destination the destination
	 * @param offset      the offset
	 * @param length      the length
	 */
	public void readFully(byte[] destination, int offset, int length) throws IOException {
		requireConnection().readFully(destination, offset, length);
	}

	/**
	 * Raw login-handshake write.
	 * 
	 * @param source the source
	 * @param offset the offset
	 * @param length the length
	 */
	public void write(byte[] source, int offset, int length) throws IOException {
		requireConnection().write(source, offset, length);
	}

	/**
	 * Initializes the outgoing and incoming ISAAC streams from the login seed.
	 *
	 * <p>
	 * The outgoing stream uses the supplied seed exactly. The incoming stream uses
	 * the revision-377 server convention of adding 50 to each of the four seed
	 * words.
	 * </p>
	 * 
	 * @param seed the seed
	 */
	public void initializeOpcodeCiphers(int[] seed) {
		outgoing.opcodeCipher = new IsaacCipher(seed);

		int[] incomingSeed = seed.clone();
		for (int index = 0; index < incomingSeed.length; index++) {
			incomingSeed[index] += 50;
		}
		incomingOpcodeCipher = new IsaacCipher(incomingSeed);
	}

	/**
	 * Transitional access for untouched decompiler-invalid branches elsewhere in
	 * client.java. Valid runtime packet framing uses this cipher internally.
	 */
	public int nextIncomingOpcodeCipherValue() {
		return incomingOpcodeCipher.nextInt();
	}

	/**
	 * Attempts to read one complete incoming packet.
	 *
	 * @return {@code true} only when {@link #incoming} contains the complete
	 *         payload for {@link #incomingOpcode}
	 */
	public boolean readIncomingPacket() throws IOException {
		if (connection == null) {
			return false;
		}

		int available = connection.available();
		if (available == 0) {
			return false;
		}

		if (incomingOpcode == -1) {
			connection.readFully(incoming.payload, 0, 1);
			incomingOpcode = incoming.payload[0] & 0xff;
			if (incomingOpcodeCipher != null) {
				incomingOpcode = incomingOpcode - incomingOpcodeCipher.nextInt() & 0xff;
			}
			incomingLength = IncomingPacketLengths.LENGTHS[incomingOpcode];
			if (incomingLength >= 0) {
				validateIncomingLength();
			}
			available--;
		}

		if (incomingLength == -1) {
			if (available <= 0) {
				return false;
			}
			connection.readFully(incoming.payload, 0, 1);
			incomingLength = incoming.payload[0] & 0xff;
			validateIncomingLength();
			available--;
		}

		if (incomingLength == -2) {
			if (available <= 1) {
				return false;
			}
			connection.readFully(incoming.payload, 0, 2);
			incoming.position = 0;
			incomingLength = incoming.readUnsignedShort();
			validateIncomingLength();
			available -= 2;
		}

		if (available < incomingLength) {
			return false;
		}

		incoming.position = 0;
		connection.readFully(incoming.payload, 0, incomingLength);
		incomingIdleCycles = 0;

		thirdLastOpcode = secondLastOpcode;
		secondLastOpcode = lastOpcode;
		lastOpcode = incomingOpcode;

		return true;
	}

	/**
	 * Rejects a framed payload that cannot fit in the fixed revision-377 receive
	 * buffer before any payload bytes are copied into it.
	 */
	private void validateIncomingLength() throws IOException {
		if (incomingLength < 0 || incomingLength > MAX_INCOMING_PAYLOAD_LENGTH) {
			throw new IOException("Incoming packet " + incomingOpcode + " length " + incomingLength
					+ " exceeds receive buffer capacity " + MAX_INCOMING_PAYLOAD_LENGTH);
		}
	}

	/**
	 * Marks the current packet consumed so the next call reads a new opcode.
	 */
	public void finishIncomingPacket() {
		incomingOpcode = -1;
	}

	/**
	 * Resets exactly the packet state cleared by the client's successful and
	 * partial-login response paths.
	 */
	public void resetPacketState() {
		outgoing.position = 0;
		incoming.position = 0;
		incomingOpcode = -1;
		lastOpcode = -1;
		secondLastOpcode = -1;
		thirdLastOpcode = -1;
		incomingLength = 0;
		incomingIdleCycles = 0;
	}

	/**
	 * Prints the underlying connection's legacy diagnostic counters.
	 */
	public void printDebugInformation() {
		if (connection != null) {
			connection.printDebugInformation();
		}
	}

	/**
	 * Flushes all queued client packet bytes to the connection, if any.
	 */
	public void flushOutgoing() throws IOException {
		if (connection != null && outgoing.position > 0) {
			connection.write(outgoing.payload, 0, outgoing.position);
			outgoing.position = 0;
			outgoingIdleCycles = 0;
		}
	}

	/**
	 * Performs require connection.
	 * 
	 * @return the resulting buffered connection
	 */
	private BufferedConnection requireConnection() throws IOException {
		if (connection == null) {
			throw new IOException("No active game connection");
		}
		return connection;
	}
}