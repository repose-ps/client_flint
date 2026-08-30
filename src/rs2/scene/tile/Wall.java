package rs2.scene.tile;

import rs2.media.renderable.Renderable;

/**
 * A wall attached to a scene tile.
 *
 * <p>
 * A wall can contain two renderables because a corner may expose models in two
 * directions. The orientation fields are scene visibility masks rather than
 * angles expressed in degrees.
 * </p>
 */
public class Wall {

	/** Creates a new wall with its default client state. */
	public Wall() {
	}

	/** World-space elevation of the wall. */
	public int z;

	/** World-space x-coordinate, where one tile spans 128 units. */
	public int x;

	/** World-space y-coordinate, where one tile spans 128 units. */
	public int y;

	/** Visibility mask for the primary wall face. */
	public int orientation;

	/** Visibility mask for the secondary wall face. */
	public int secondaryOrientation;

	/** Model drawn for the primary wall face. */
	public Renderable primary;

	/** Optional model drawn for the adjoining wall face. */
	public Renderable secondary;

	/** Packed identifier used by scene queries and menu actions. */
	public int uid;

	/** Packed scene configuration associated with {@link #uid}. */
	public byte config;
}
