package rs2.scene;

import rs2.media.renderable.Renderable;

/**
 * A renderable scene object that may occupy one or more adjacent tiles.
 *
 * <p>
 * Every tile covered by the object's bounds refers to the same instance. The
 * render-cycle marker prevents that shared object from being drawn more than
 * once during a scene traversal.
 * </p>
 */
public class InteractiveObject {

	/** Scene plane containing the object. */
	public int plane;

	/** World-space height used when rendering the object. */
	public int worldZ;

	/** World-space X coordinate of the object's render origin. */
	public int worldX;

	/** World-space Y coordinate of the object's render origin. */
	public int worldY;

	/** Object geometry submitted to the scene renderer. */
	public Renderable renderable;

	/** Model rotation in the client's 0-2047 angular coordinate system. */
	public int rotation;

	/** Minimum occupied tile X coordinate, inclusive. */
	public int tileLeft;

	/** Maximum occupied tile X coordinate, inclusive. */
	public int tileRight;

	/** Minimum occupied tile Y coordinate, inclusive. */
	public int tileTop;

	/** Maximum occupied tile Y coordinate, inclusive. */
	public int tileBottom;

	/**
	 * Camera-relative tile-distance priority calculated during traversal. Higher
	 * values are drawn first; world distance breaks equal priorities.
	 */
	public int drawPriority;

	/** Render cycle in which this shared object was most recently drawn. */
	public int lastDrawnCycle;

	/** Packed scene identifier supplied when the object is inserted. */
	public int uid;

	/** Packed object configuration supplied by the region loader. */
	public byte config;
}
