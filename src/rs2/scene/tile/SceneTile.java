package rs2.scene.tile;

import rs2.collection.Node;
import rs2.scene.GroundItemTile;
import rs2.scene.InteractiveObject;

/**
 * Stores the contents and transient rendering state of one scene tile.
 *
 * <p>
 * A tile may contain one floor surface, several independently rendered
 * fixtures, and up to five interactive objects. Interactive objects spanning
 * several tiles are shared by reference between every occupied tile.
 * </p>
 */
public class SceneTile extends Node {

	/** Scene plane on which this tile is currently stored. */
	public int plane;

	/** Tile-space X coordinate. */
	public int x;

	/** Tile-space Y coordinate. */
	public int y;

	/** Plane used for height lookup, occlusion tests, and rendering. */
	public int renderLevel;

	/** Unshaped quadrilateral floor surface, if present. */
	public GenericTile plainTile;

	/** Shaped floor mesh used instead of {@link #plainTile}, if present. */
	public ComplexTile shapedTile;

	/** Wall occupying this tile, if present. */
	public Wall wall;

	/** Decoration attached to a wall on this tile, if present. */
	public WallDecoration wallDecoration;

	/** Decoration placed directly on the floor, if present. */
	public FloorDecoration floorDecoration;

	/** Ground-item pile occupying this tile, if present. */
	public GroundItemTile groundItemTile;

	/** Number of populated entries in {@link #interactiveObjects}. */
	public int interactiveObjectCount;

	/**
	 * Interactive objects occupying this tile.
	 *
	 * <p>
	 * The scene format permits at most five references per tile. An object spanning
	 * multiple tiles appears in each covered tile's array.
	 * </p>
	 */
	public InteractiveObject[] interactiveObjects;

	/**
	 * Per-object footprint edge masks, indexed alongside
	 * {@link #interactiveObjects}.
	 */
	public int[] interactiveObjectEdgeMasks;

	/** Bitwise union of the populated {@link #interactiveObjectEdgeMasks}. */
	public int combinedInteractiveObjectEdgeMask;

	/** Lowest camera plane from which this tile may be considered visible. */
	public int logicHeight;

	/** Whether the tile's floor and fixtures are awaiting traversal. */
	public boolean draw;

	/** Whether this tile remains active in the current render traversal. */
	public boolean visible;

	/** Whether interactive objects on this tile still need to be processed. */
	public boolean drawEntities;

	/** Active directional mask delaying wall rendering. */
	public int wallCullDirection;

	/** Object-edge mask required before the delayed wall may be drawn. */
	public int wallUncullDirection;

	/** Complementary edge mask used while ordering multi-tile objects. */
	public int wallCullOppositeDirection;

	/** Wall-orientation flags eligible for the traversal's deferred pass. */
	public int wallDrawFlags;

	/**
	 * Tile displaced from the plane below by bridge processing, if present. Its
	 * contents are rendered beneath this tile.
	 */
	public SceneTile tileBelow;

	/**
	 * Creates an empty tile at the supplied scene-grid position.
	 *
	 * compatibility with the original client
	 * 
	 * @param plane the plane
	 * @param x     the x
	 * @param y     the y
	 */
	public SceneTile(int plane, int x, int y) {
		this.interactiveObjects = new InteractiveObject[5];
		this.interactiveObjectEdgeMasks = new int[5];
		this.plane = plane;
		this.renderLevel = plane;
		this.x = x;
		this.y = y;
	}
}
