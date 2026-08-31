package rs2.cache.cfg;

/**
 * Narrow read-only access to current client varp values.
 *
 * <p>Lower-level cache, scene, and model code uses this boundary instead of
 * depending on the application {@code Client} coordinator.</p>
 */
@FunctionalInterface
public interface VarpProvider {

	/**
	 * Returns one current varp value.
	 *
	 * @param varpId varp identifier
	 * @return current value
	 */
	int getVarp(int varpId);
}
