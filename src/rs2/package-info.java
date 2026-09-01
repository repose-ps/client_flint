/**
 * Contains the revision-377 desktop client entry point, lifecycle/bootstrap
 * coordination, and application-layer protocol adapter. The AWT host lives in
 * {@code rs2.shell}, while classic layout geometry lives in {@code rs2.ui}.
 *
 * <p>
 * Most protocol transport, cache, scene, rendering, UI, and gameplay concerns
 * live in dedicated subpackages. {@code ClientPacketDispatcher} is the
 * package-private application boundary for incoming packets, while normalized
 * menu-action routing and shared post-action selection cleanup live in
 * {@code rs2.action}; cohesive packet-domain handlers live behind the
 * {@code rs2.packet} facade and do not retain the client coordinator.
 * {@code rs2.net} therefore remains independent of application ownership, while
 * {@code ClientLifecycle} and {@code ClientBootstrap} own one-time startup and
 * final shutdown ordering.
 * </p>
 */
package rs2;
