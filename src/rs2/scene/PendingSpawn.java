package rs2.scene;

import rs2.collection.Node;

/**
 * A delayed scene-location replacement together with the state it may restore.
 *
 * <p>
 * The client uses one node for permanent spawns/removals and for temporary
 * replacements such as the location hidden while a player performs an attached
 * object animation. A spawn id of {@code -1} represents an empty replacement.
 * {@link #restoreDelay} defaults to {@code -1}, meaning there is no scheduled
 * restoration of the previous scene state.
 * </p>
 */
public final class PendingSpawn extends Node {

	/**
	 * Identifier for spawn.
	 */
	public int spawnId;

	public int spawnOrientation;

	public int spawnType;
	/**
	 * Identifier for previous.
	 */
	public int previousId;

	public int previousOrientation;

	public int previousType;

	public int restoreDelay = -1;

	public int plane;

	public int sceneLayer;

	public int x;

	public int y;

	public int spawnDelay;
}
