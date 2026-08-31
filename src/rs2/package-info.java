/**
 * Contains the revision-377 desktop client entry point, AWT shell, top-level
 * layout coordination, and application-layer protocol adapter.
 *
 * <p>Most protocol transport, cache, scene, rendering, UI, and gameplay concerns
 * live in dedicated subpackages. {@code ClientIncomingPacketHandler} is kept in
 * this application layer because it bridges decoded packets into those runtime
 * subsystems without making {@code rs2.net} depend on the client coordinator.</p>
 */
package rs2;
