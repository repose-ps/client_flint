package rs2.cache.cfg;

/**
 * Revision-377 bit-mask lookup values used by varbits and CS1 expressions.
 *
 * <p>The table preserves the original client sequence exactly: index zero is
 * {@code 0b1}, index one is {@code 0b11}, through index 31 being all bits set.</p>
 */
public final class BitMasks {

	/** Original 32-entry mask table. */
	private static final int[] VALUES = new int[Integer.SIZE];

	static {
		int value = 2;
		for (int index = 0; index < VALUES.length; index++) {
			VALUES[index] = value - 1;
			value += value;
		}
	}

	/** Prevents instantiation. */
	private BitMasks() {
	}

	/**
	 * Returns the original mask-table value at one bit-difference index.
	 *
	 * @param index mask-table index from 0 through 31
	 * @return mask value
	 */
	public static int get(int index) {
		return VALUES[index];
	}
}
