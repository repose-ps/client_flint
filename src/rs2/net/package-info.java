/**
 * Contains revision-377 networking primitives, packet buffers, ISAAC opcode
 * ciphering, connection/session framing, login-handshake mechanics, incoming
 * dispatch coordination, and outbound packet encoders.
 *
 * <p>Transport classes deliberately remain independent of the top-level client:
 * application packet effects are supplied through {@link rs2.net.IncomingPacketHandler}
 * rather than by making the network layer depend on world or UI state.</p>
 */
package rs2.net;
