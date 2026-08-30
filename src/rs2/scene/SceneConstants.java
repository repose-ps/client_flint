package rs2.scene;

/** Shared geometry constants for the revision-377 local scene. */
public final class SceneConstants {

	/** Prevents instantiation. */
	private SceneConstants() {
	}

	/** Number of local scene tiles along each horizontal axis. */
	public static final int SIZE = 104;
	/** Largest valid local scene-tile index. */
	public static final int MAX_TILE_INDEX = SIZE - 1;
	/** First tile used for object changes that require a one-tile border. */
	public static final int INTERIOR_MIN_TILE = 1;
	/** Last tile used for object changes that require a one-tile border. */
	public static final int INTERIOR_MAX_TILE = MAX_TILE_INDEX - 1;
	/** Height-map axis length, which includes the extra corner sample. */
	public static final int HEIGHT_MAP_SIZE = SIZE + 1;
	/** Number of height/scene planes maintained by the client. */
	public static final int PLANE_COUNT = 4;
	/** Number of bits used by one fine-coordinate tile component. */
	public static final int TILE_BITS = 7;
	/** Fine-coordinate units represented by one scene tile. */
	public static final int TILE_SIZE = 1 << TILE_BITS;
	/** Mask for the fine-coordinate position within one tile. */
	public static final int TILE_OFFSET_MASK = TILE_SIZE - 1;
	/** Fine-coordinate offset from a tile edge to its center. */
	public static final int TILE_CENTER = TILE_SIZE / 2;
	/** Number of tiles along one map chunk axis. */
	public static final int CHUNK_SIZE = 8;
	/** Mask for a coordinate local to an eight-tile chunk. */
	public static final int CHUNK_COORDINATE_MASK = CHUNK_SIZE - 1;
	/** Number of tiles along one cache region axis. */
	public static final int REGION_SIZE = 64;
	/** Number of chunks along one cache region axis. */
	public static final int CHUNKS_PER_REGION = REGION_SIZE / CHUNK_SIZE;
	/** Number of chunks along one axis of an instanced scene template grid. */
	public static final int INSTANCE_CHUNK_COUNT = 13;
}
