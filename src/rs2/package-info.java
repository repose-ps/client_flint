/**
 * Contains the revision-377 desktop client entry point, AWT shell, top-level
 * layout coordination, lifecycle/bootstrap coordination, and application-layer
 * protocol adapter.
 *
 * <p>
 * Most protocol transport, cache, scene, rendering, UI, and gameplay concerns
 * live in dedicated subpackages. {@code ClientIncomingPacketHandler} routes
 * decoded packets into cohesive application-domain handlers wired from narrow
 * owners, suppliers, and sinks rather than retaining the client coordinator.
 * {@code rs2.net} therefore remains independent of application ownership, while
 * {@code ClientLifecycle} and {@code ClientBootstrap} own one-time startup and
 * final shutdown ordering.
 * </p>
 */
package rs2;
