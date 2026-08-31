/**
 * Implements the revision-377 on-demand resource request and delivery system
 * for models, animations, maps, MIDI tracks, and other cache groups.
 *
 * <p>
 * The update worker depends only on narrow socket and login-state callbacks;
 * it does not depend on the application {@code Client}. This keeps update-server
 * transport and cache validation inside the resource layer.
 * </p>
 */
package rs2.cache.ondemand;
