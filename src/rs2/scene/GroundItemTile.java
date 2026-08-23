package rs2.scene;

import rs2.media.renderable.Renderable;

/**
 * Describes the renderable item pile attached to a single scene tile.
 *
 * <p>
 * The scene selects at most three item renderables for a pile. Their ordering
 * is significant to the renderer, so the three references remain distinct
 * rather than being exposed as an unordered collection.
 * </p>
 */
public class GroundItemTile {

	/** World-space height at which the pile is based. */
	public int z;

	/** World-space X coordinate of the tile centre. */
	public int x;

	/** World-space Y coordinate of the tile centre. */
	public int y;

	/** Primary item renderable selected for the pile. */
	public Renderable firstGroundItem;

	/** Secondary item renderable selected for the pile, if present. */
	public Renderable secondGroundItem;

	/** Tertiary item renderable selected for the pile, if present. */
	public Renderable thirdGroundItem;

	/** Packed scene identifier supplied when the pile is inserted. */
	public int uid;

	/**
	 * Maximum model height of the interactive objects occupying this tile.
	 *
	 * <p>
	 * The renderer subtracts this value from the pile's camera-relative Z
	 * coordinate, lifting the items above supporting scene geometry under the
	 * client's inverted vertical-axis convention.
	 * </p>
	 */
	public int heightOffset;
}
