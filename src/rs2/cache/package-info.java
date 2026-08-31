/**
 * Provides revision-377 cache archives, cache indexes, bootstrap validation,
 * and resource-lifecycle infrastructure.
 *
 * <p>
 * {@code ClientResourceManager} owns the bootstrap {@code ResourceLoader} and
 * the asynchronous on-demand service as one lifecycle. Format-specific
 * definitions and assets are decoded by the cache subpackages and higher-level
 * application bootstrap code.
 * </p>
 */
package rs2.cache;
