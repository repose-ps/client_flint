package rs2.scene.util;

/**
 * Coordinate transforms for the client's rotated 8-by-8 map chunks.
 *
 * <p>
 * Instanced terrain and landscape data are stored in local chunk coordinates.
 * These helpers rotate those coordinates into their destination chunk without
 * allocating temporary coordinate objects.
 * </p>
 */
public final class TiledUtils {

	private static final int CHUNK_MAX_COORDINATE = 7;

	private TiledUtils() {
		// Utility class.
	}

	/**
	 * Rotates a terrain tile's local X coordinate within an 8-by-8 chunk.
	 *
	 * @return rotated local X coordinate
	 * @param x        the x
	 * @param y        the y
	 * @param rotation the rotation
	 */
	public static int getRotatedMapChunkX(int x, int y, int rotation) {
		switch (rotation & 3) {
		case 0:
			return x;
		case 1:
			return y;
		case 2:
			return CHUNK_MAX_COORDINATE - x;
		default:
			return CHUNK_MAX_COORDINATE - y;
		}
	}

	/**
	 * Rotates a terrain tile's local Y coordinate within an 8-by-8 chunk.
	 *
	 * @return rotated local Y coordinate
	 * @param x        the x
	 * @param y        the y
	 * @param rotation the rotation
	 */
	public static int getRotatedMapChunkY(int x, int y, int rotation) {
		switch (rotation & 3) {
		case 0:
			return y;
		case 1:
			return CHUNK_MAX_COORDINATE - x;
		case 2:
			return CHUNK_MAX_COORDINATE - y;
		default:
			return x;
		}
	}

	/**
	 * Rotates the local X coordinate of a multi-tile landscape object.
	 *
	 * <p>
	 * Odd object orientations exchange the object's width and height before the
	 * chunk rotation is applied. This preserves the occupied south-west corner
	 * rather than merely rotating a single point.
	 * </p>
	 *
	 * @param x                 the x
	 * @param y                 the y
	 * @param sizeX             the size x
	 * @param sizeY             the size y
	 * @param objectOrientation the object orientation
	 * @param chunkRotation     the chunk rotation
	 */
	public static int getRotatedLandscapeChunkX(int x, int y, int sizeX, int sizeY, int objectOrientation,
			int chunkRotation) {
		if ((objectOrientation & 1) == 1) {
			int originalSizeX = sizeX;
			sizeX = sizeY;
			sizeY = originalSizeX;
		}

		switch (chunkRotation & 3) {
		case 0:
			return x;
		case 1:
			return y;
		case 2:
			return CHUNK_MAX_COORDINATE - x - (sizeX - 1);
		default:
			return CHUNK_MAX_COORDINATE - y - (sizeY - 1);
		}
	}

	/**
	 * Rotates the local Y coordinate of a multi-tile landscape object.
	 *
	 * @see #getRotatedLandscapeChunkX(int, int, int, int, int, int)
	 * @param x                 the x
	 * @param y                 the y
	 * @param sizeX             the size x
	 * @param sizeY             the size y
	 * @param objectOrientation the object orientation
	 * @param chunkRotation     the chunk rotation
	 */
	public static int getRotatedLandscapeChunkY(int x, int y, int sizeX, int sizeY, int objectOrientation,
			int chunkRotation) {
		if ((objectOrientation & 1) == 1) {
			int originalSizeX = sizeX;
			sizeX = sizeY;
			sizeY = originalSizeX;
		}

		switch (chunkRotation & 3) {
		case 0:
			return y;
		case 1:
			return CHUNK_MAX_COORDINATE - x - (sizeX - 1);
		case 2:
			return CHUNK_MAX_COORDINATE - y - (sizeY - 1);
		default:
			return x;
		}
	}
}
