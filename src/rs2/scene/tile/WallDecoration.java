package rs2.scene.tile;

import rs2.Class50_Sub1_Sub4;

/**
 * A decoration rendered against a wall, such as a painting or mounted object.
 *
 * <p>
 * The position may be offset from the centre of its tile. This is how the scene
 * places decorations against a particular wall face.
 * </p>
 */
public class WallDecoration {

	/** World-space elevation of the decoration. */
	public int z;

	/** World-space x-coordinate after applying the decoration offset. */
	public int x;

	/** World-space y-coordinate after applying the decoration offset. */
	public int y;

	/** Bit mask controlling which camera-relative faces may draw the model. */
	public int configBits;

	/** Direction the decoration faces. */
	public int face;

	/** Model rendered for the decoration. */
	public Class50_Sub1_Sub4 renderable;

	/** Packed identifier used by scene queries and menu actions. */
	public int uid;

	/** Packed scene configuration associated with {@link #uid}. */
	public byte config;
}