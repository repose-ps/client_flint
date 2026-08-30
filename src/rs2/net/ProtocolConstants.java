package rs2.net;

/** Shared scalar values used by the revision-377 game protocol. */
public final class ProtocolConstants {

	/** Unsigned-short value used by packets to represent an absent id/index. */
	public static final int NULL_ID = 0xffff;
	/** Mask for the low unsigned-short half of a packed integer. */
	public static final int UNSIGNED_SHORT_MASK = 0xffff;
	/** Mask for an unsigned byte stored in an integer. */
	public static final int UNSIGNED_BYTE_MASK = 0xff;

	/** Prevents instantiation. */
	private ProtocolConstants() {
	}
}
