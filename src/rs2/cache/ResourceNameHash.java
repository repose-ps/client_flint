package rs2.cache;

import java.util.Locale;

/**
 * Produces the legacy 56-bit hash used in resource-related cache keys.
 */
public final class ResourceNameHash {

	/** Constant value for radix. */
	private static final int RADIX = 61;
	/** Constant value for character bias. */
	private static final int CHARACTER_BIAS = 32;
	/** Constant value for folded bits. */
	private static final int FOLDED_BITS = 56;

	/** Constant value for hash mask. */
	private static final long HASH_MASK = 0x00ffffffffffffffL;

	/**
	 * Creates a new resource name hash.
	 */
	private ResourceNameHash() {
		throw new AssertionError("No instances");
	}

	/**
	 * Hashes a resource name using the case-insensitive revision-377 formula.
	 *
	 * <p>
	 * {@link Locale#ROOT} makes case normalization independent of the machine's
	 * display language. The original default-locale conversion could otherwise
	 * produce different cache keys in locales such as Turkish.
	 * </p>
	 * 
	 * @param resourceName the resource name
	 * @return whether h
	 */
	public static long hash(String resourceName) {
		String normalized = resourceName.toUpperCase(Locale.ROOT);

		long hash = 0L;

		for (int index = 0; index < normalized.length(); index++) {
			hash = hash * RADIX + normalized.charAt(index) - CHARACTER_BIAS;

			/*
			 * Fold overflow back into the lower 56 bits before masking. This operation is
			 * part of the original hash definition.
			 */
			hash = (hash + (hash >> FOLDED_BITS)) & HASH_MASK;
		}

		return hash;
	}
}
