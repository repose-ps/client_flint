package rs2.net;

/**
 * Revision 377 inbound packet framing metadata.
 *
 * <p>
 * A non-negative entry in {@link #LENGTHS} is a fixed payload length.
 * {@code -1} means the payload is prefixed by one unsigned length byte and
 * {@code -2} means it is prefixed by one unsigned length short. These are
 * framing lengths only; the opcode byte itself is not included.
 * </p>
 */
public class IncomingPacketLengths {

	/** Creates a new incoming packet lengths with its default client state. */
	public IncomingPacketLengths() {
	}

	/** Packet length is supplied by one unsigned byte after the opcode. */
	public static final int VARIABLE_BYTE = -1;
	/** Packet length is supplied by one unsigned short after the opcode. */
	public static final int VARIABLE_SHORT = -2;

	/**
	 * Payload lengths indexed by the ISAAC-deciphered inbound opcode.
	 */
	public static final int LENGTHS[] = { 0, 0, 4, 6, 0, 0, 0, 0, 0, 0, 3, 0, 0, 0, 0, 0, 0, 0, 6, 0, 0, 6, 0, 0, 0, 0,
			5, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 2, 4, 0, 0, 0, 0, 0, 0, 0, 6, 2, 0, 0, VARIABLE_SHORT, 0, 0, 0, 0,
			0, 6, 0, 0, 0, VARIABLE_BYTE, 0, 0, 0, 4, 0, 0, 0, VARIABLE_SHORT, 0, 0, 0, 2, 23, 0, 9, 0, 0, 0, 3, 0, 0,
			0, 0, 0, 2, 0, VARIABLE_SHORT, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 7, 5, 0, 2, 0, 0, 0, 0, 0, 6, 0,
			0, 0, 0, 0, 7, 0, 0, 0, 1, 3, 0, 4, 0, 0, 0, 0, 0, VARIABLE_SHORT, VARIABLE_BYTE, 0, 0, 0, 0, 0, 0, 4, 0, 0,
			0, 0, 0, 0, 0, 0, 0, 4, 0, 0, 0, 1, VARIABLE_BYTE, 2, 2, 0, 0, 4, 0, 0, 0, 6, 6, 0, 0, 0, 0, 0, 0, 2, 0, 0,
			0, 0, 0, 0, 15, 3, VARIABLE_SHORT, 0, 0, 8, 6, 0, 0, 2, 0, 0, 0, 0, 0, 0, 0, 0, 6, 4, 3, 0, 14, 0, 0,
			VARIABLE_SHORT, 0, 3, 0, 0, 0, 0, 0, 0, 0, 4, 0, 4, 2, 2, 0, 4, 0, 0, 0, VARIABLE_SHORT, 0, 0, 0, 0, 0,
			VARIABLE_SHORT, 1, 0, 0, 0, 0, 1, 0, 0, 0, 0, 0, 0, 0, 2, 0, 0, 5, 0, 1, 1, 4, 0, 2, 0 };

}
