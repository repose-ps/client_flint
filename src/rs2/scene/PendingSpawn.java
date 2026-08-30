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

	/** Creates a new pending spawn with its default client state. */
	public PendingSpawn() {
	}

	/**
	 * Identifier for spawn.
	 */
	public int spawnId;

	/** Stores the current spawn orientation. */
	public int spawnOrientation;

	/** Stores the current spawn type. */
	public int spawnType;
	/**
	 * Identifier for previous.
	 */
	public int previousId;

	/** Stores the current previous orientation. */
	public int previousOrientation;

	/** Stores the current previous type. */
	public int previousType;

	/** Stores the current restore delay. */
	public int restoreDelay = -1;

	/** Stores the current plane. */
	public int plane;

	/** Stores the current scene layer. */
	public int sceneLayer;

	/** Stores the current X. */
	public int x;

	/** Stores the current Y. */
	public int y;

	/** Stores the current spawn delay. */
	public int spawnDelay;
}
