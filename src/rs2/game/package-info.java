/**
 * Contains high-level client runtime systems that coordinate game-state updates
 * and presentation state.
 *
 * <p>
 * These classes process actor synchronization, movement, camera state, minimap
 * behavior, region transitions, zone updates, pathfinding, varp state, and the
 * currently loaded local world. They are client-side consumers of server and
 * cache state rather than authoritative game logic. Logged-in
 * software-rendering surfaces and frame orchestration live in
 * {@code rs2.game.render}, while synchronized players and NPCs live in
 * {@code rs2.game.entity}.
 * </p>
 */
package rs2.game;
