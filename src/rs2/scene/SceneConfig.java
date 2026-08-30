package rs2.scene;

/**
 * Packing layout for the one-byte scene configuration attached to placed
 * objects, walls, and decorations.
 */
public final class SceneConfig {

	/** Mask for the five-bit location/shape type. */
	public static final int TYPE_MASK = 0x1f;
	/** Bit shift of the two-bit orientation field. */
	public static final int ORIENTATION_SHIFT = 6;
	/** Mask for the two-bit orientation field. */
	public static final int ORIENTATION_MASK = 0x3;

	/** Prevents instantiation. */
	private SceneConfig() {
	}

	/**
	 * Packs a location type and orientation into the revision-377 scene byte.
	 *
	 * @param type the five-bit location type
	 * @param orientation the two-bit orientation
	 * @return the packed scene configuration
	 */
	public static byte pack(int type, int orientation) {
		return (byte) ((orientation << ORIENTATION_SHIFT) + type);
	}

	/**
	 * Extracts the five-bit location type.
	 *
	 * @param config the packed scene configuration
	 * @return the location type
	 */
	public static int type(int config) {
		return config & TYPE_MASK;
	}

	/**
	 * Extracts the two-bit orientation.
	 *
	 * @param config the packed scene configuration
	 * @return the orientation
	 */
	public static int orientation(int config) {
		return config >> ORIENTATION_SHIFT & ORIENTATION_MASK;
	}
}
