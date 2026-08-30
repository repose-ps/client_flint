package rs2.media;

/**
 * Revision-377 angular coordinate constants.
 *
 * <p>The software renderer represents one full turn with 2048 integer units,
 * allowing angles to wrap efficiently with {@link #MASK}.</p>
 */
public final class Angle {

	/** One eighth of a full turn. */
	public static final int EIGHTH_TURN = 256;
	/** One quarter of a full turn. */
	public static final int QUARTER_TURN = 512;
	/** Three eighths of a full turn. */
	public static final int THREE_EIGHTHS_TURN = 768;
	/** One half of a full turn. */
	public static final int HALF_TURN = 1024;
	/** Five eighths of a full turn. */
	public static final int FIVE_EIGHTHS_TURN = 1280;
	/** Three quarters of a full turn. */
	public static final int THREE_QUARTER_TURN = 1536;
	/** Seven eighths of a full turn. */
	public static final int SEVEN_EIGHTHS_TURN = 1792;
	/** Number of integer angle units in one full turn. */
	public static final int FULL_TURN = 2048;
	/** Mask that wraps an angle into the valid {@code 0..2047} range. */
	public static final int MASK = FULL_TURN - 1;
	/** Conversion factor from radians to revision-377 integer angle units. */
	public static final double UNITS_PER_RADIAN = 325.94900000000001D;

	/** Prevents instantiation. */
	private Angle() {
	}
}
