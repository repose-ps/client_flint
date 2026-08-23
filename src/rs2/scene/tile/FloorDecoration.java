package rs2.scene.tile;

import rs2.media.renderable.Renderable;

/**
 * A single renderable placed on the floor of a scene tile.
 *
 * <p>
 * Unlike an interactive object, a floor decoration occupies one tile and does
 * not maintain a multi-tile footprint.
 * </p>
 */
public class FloorDecoration {

	/** World-space elevation of the decoration. */
	public int z;

	/** World-space x-coordinate, normally the centre of the tile. */
	public int x;

	/** World-space y-coordinate, normally the centre of the tile. */
	public int y;

	/** Model rendered for the decoration. */
	public Renderable renderable;

	/** Packed identifier used by scene queries and menu actions. */
	public int uid;

	/** Packed scene configuration associated with {@link #uid}. */
	public byte config;
}
