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
public final /**
				 * Initializes this instance.
				 */
class PendingSpawn extends Node {

	/**
	 * Identifier for spawn.
	 */
	public int spawnId;
	/**
	 * Stores spawn orientation.
	 */
	public int spawnOrientation;
	/**
	 * Stores spawn type.
	 */
	public int spawnType;
	/**
	 * Identifier for previous.
	 */
	public int previousId;
	/**
	 * Stores previous orientation.
	 */
	public int previousOrientation;
	/**
	 * Stores previous type.
	 */
	public int previousType;
	/**
	 * Stores restore delay.
	 */
	public int restoreDelay = -1;
	/**
	 * Stores plane.
	 */
	public int plane;
	/**
	 * Stores scene layer.
	 */
	public int sceneLayer;
	/**
	 * Stores x.
	 */
	public int x;
	/**
	 * Stores y.
	 */
	public int y;
	/**
	 * Stores spawn delay.
	 */
	public int spawnDelay;
}