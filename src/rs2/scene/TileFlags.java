package rs2.scene;

/**
 * Bit flags stored for each local terrain tile by the revision-377 map format.
 */
public final class TileFlags {

	/** Tile blocks ordinary movement and contributes floor collision. */
	public static final int BLOCKED = 0x1;
	/** Tile is a bridge tile whose effective collision/render plane is lowered. */
	public static final int BRIDGE = 0x2;
	/** Tile is roof-covered for camera plane selection. */
	public static final int ROOF = 0x4;
	/** Tile forces effective rendering onto the lowest scene plane. */
	public static final int FORCE_LOWEST_PLANE = 0x8;
	/** Tile is omitted by the low-memory scene-building path. */
	public static final int LOW_MEMORY_HIDDEN = 0x10;
	/** Flags that suppress drawing the base-plane tile on the minimap. */
	public static final int MINIMAP_EXCLUDED = FORCE_LOWEST_PLANE | LOW_MEMORY_HIDDEN;

	/** Prevents instantiation. */
	private TileFlags() {
	}
}
