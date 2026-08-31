/**
 * Contains the revision-377 desktop client entry point, AWT shell, top-level
 * layout coordination, lifecycle/bootstrap coordination, and application-layer
 * protocol adapter.
 *
 * <p>
 * Most protocol transport, cache, scene, rendering, UI, and gameplay concerns
 * live in dedicated subpackages. {@code ClientIncomingPacketHandler} routes
 * decoded packets into cohesive application-domain handlers without making
 * {@code rs2.net} depend on the client coordinator, while {@code ClientLifecycle} and
 * {@code ClientBootstrap} own one-time startup and final shutdown ordering.
 * </p>
 */
package rs2;
