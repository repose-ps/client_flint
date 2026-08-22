package rs2.scene.util;

import rs2.media.Rasterizer3D;

/**
 * Deterministic terrain height noise used by the revision-377 map decoder.
 *
 * <p>
 * The arithmetic, constants, cosine interpolation, octave weights and final
 * clamp are preserved exactly. These helpers intentionally use integer
 * truncation and the client renderer's fixed-point cosine table.
 * </p>
 */
public final class TerrainNoise {

	/**
	 * Initializes this instance.
	 */
	private TerrainNoise() {
	}

	/**
	 * Returns the client's deterministic 0..255 pseudo-random value for a lattice
	 * point.
	 * 
	 * @param x the x
	 * @param y the y
	 */
	public static int randomNoise(int x, int y) {
		int seed = x + y * 57;
		seed = seed << 13 ^ seed;
		int value = seed * (seed * seed * 15731 + 789221) + 1376312589 & 0x7fffffff;
		return value >> 19 & 0xff;
	}

	/**
	 * Applies the original corner/edge/center weighting around a lattice point.
	 * 
	 * @param x the x
	 * @param y the y
	 */
	public static int smoothNoise(int x, int y) {
		int corners = randomNoise(x - 1, y - 1) + randomNoise(x + 1, y - 1) + randomNoise(x - 1, y + 1)
				+ randomNoise(x + 1, y + 1);
		int sides = randomNoise(x - 1, y) + randomNoise(x + 1, y) + randomNoise(x, y - 1) + randomNoise(x, y + 1);
		int center = randomNoise(x, y);
		return corners / 16 + sides / 8 + center / 4;
	}

	/**
	 * Cosine-interpolates two samples using the renderer's 16-bit fixed-point
	 * cosine table.
	 * 
	 * @param from     the from
	 * @param to       the to
	 * @param position the position
	 * @param scale    the scale
	 */
	public static int cosineInterpolate(int from, int to, int position, int scale) {
		int weight = 65536 - Rasterizer3D.COSINE[position * 1024 / scale] >> 1;
		return (from * (65536 - weight) >> 16) + (to * weight >> 16);
	}

	/**
	 * Bilinearly samples the smoothed lattice at the requested power-of-two scale.
	 * 
	 * @param x     the x
	 * @param y     the y
	 * @param scale the scale
	 */
	public static int interpolatedNoise(int x, int y, int scale) {
		int cellX = x / scale;
		int localX = x & scale - 1;
		int cellY = y / scale;
		int localY = y & scale - 1;
		int southWest = smoothNoise(cellX, cellY);
		int southEast = smoothNoise(cellX + 1, cellY);
		int northWest = smoothNoise(cellX, cellY + 1);
		int northEast = smoothNoise(cellX + 1, cellY + 1);
		int south = cosineInterpolate(southWest, southEast, localX, scale);
		int north = cosineInterpolate(northWest, northEast, localX, scale);
		return cosineInterpolate(south, north, localY, scale);
	}

	/**
	 * Produces the default plane-0 tile height before the map stream's
	 * factor-of-eight conversion.
	 * 
	 * @param x the x
	 * @param y the y
	 */
	public static int calculateHeight(int x, int y) {
		int value = interpolatedNoise(x + 45365, y + 91923, 4) - 128
				+ (interpolatedNoise(x + 10294, y + 37821, 2) - 128 >> 1) + (interpolatedNoise(x, y, 1) - 128 >> 2);
		value = (int) (value * 0.3D) + 35;
		if (value < 10) {
			value = 10;
		} else if (value > 60) {
			value = 60;
		}
		return value;
	}
}
