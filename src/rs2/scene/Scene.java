package rs2.scene;

import rs2.collection.NodeDeque;
import rs2.media.Angle;
import rs2.media.Rasterizer;
import rs2.media.Rasterizer3D;
import rs2.media.model.Model;
import rs2.media.model.Renderable;
import rs2.media.model.VertexNormal;
import rs2.scene.tile.ComplexTile;
import rs2.scene.tile.FloorDecoration;
import rs2.scene.tile.GenericTile;
import rs2.scene.tile.GroundItemTile;
import rs2.scene.tile.InteractiveObject;
import rs2.scene.tile.SceneTile;
import rs2.scene.tile.Wall;
import rs2.scene.tile.WallDecoration;

/** Provides scene state and behavior. */
public class Scene {

	/** Monotonic revision used by GPU backends to detect static scene rebuilds. */
	private long geometryRevision;

	/** Pending viewport-space walk-pick request consumed by the fixed logic picker. */
	private boolean tilePickPending;
	/** Pending walk-pick X coordinate relative to the world viewport. */
	private int tilePickRequestX;
	/** Pending walk-pick Y coordinate relative to the world viewport. */
	private int tilePickRequestY;

	/**
	 * Creates a new scene.
	 *
	 * @param heights    the heights
	 * @param planeCount the plane count
	 * @param width      the width in pixels
	 * @param height     the height in pixels
	 */
	public Scene(int heights[][][], int planeCount, int width, int height) {
		temporaryObjects = new InteractiveObject[5000];
		mergeStampA = new int[10000];
		mergeStampB = new int[10000];
		this.planeCount = planeCount;
		this.width = width;
		this.height = height;
		tiles = new SceneTile[planeCount][width][height];
		tileOcclusionCycles = new int[planeCount][width + 1][height + 1];
		tileHeights = heights;
		clear();
	}

	/**
	 * Clears static state.
	 */
	public static void clearStatic() {
		renderInteractiveObjects = null;
		occluderCounts = null;
		occluders = null;
		tileQueue = null;
		visibilityMaps = null;
		visibilityMap = null;
	}

	/**
	 * Clears value state.
	 */
	public void clear() {
		for (int loopIndex = 0; loopIndex < planeCount; loopIndex++) {
			for (int loopIndex2 = 0; loopIndex2 < width; loopIndex2++) {
				for (int loopIndex3 = 0; loopIndex3 < height; loopIndex3++)
					tiles[loopIndex][loopIndex2][loopIndex3] = null;

			}

		}

		for (int loopIndex4 = 0; loopIndex4 < OCCLUDER_PLANE_COUNT; loopIndex4++) {
			for (int loopIndex5 = 0; loopIndex5 < occluderCounts[loopIndex4]; loopIndex5++)
				occluders[loopIndex4][loopIndex5] = null;

			occluderCounts[loopIndex4] = 0;
		}

		for (int loopIndex6 = 0; loopIndex6 < temporaryObjectCount; loopIndex6++)
			temporaryObjects[loopIndex6] = null;

		temporaryObjectCount = 0;
		tilePickPending = false;
		pickedTileX = -1;
		pickedTileY = -1;
		for (int loopIndex7 = 0; loopIndex7 < renderInteractiveObjects.length; loopIndex7++)
			renderInteractiveObjects[loopIndex7] = null;

		geometryRevision++;
	}

	/** Returns the current static scene geometry revision. */
	public long geometryRevision() {
		return geometryRevision;
	}

	private void markGeometryChanged() {
		geometryRevision++;
	}

	/**
	 * Sets min plane.
	 *
	 * @param plane the plane
	 */
	public void setMinPlane(int plane) {
		minPlane = plane;
		for (int loopIndex = 0; loopIndex < width; loopIndex++) {
			for (int loopIndex2 = 0; loopIndex2 < height; loopIndex2++)
				if (tiles[plane][loopIndex][loopIndex2] == null)
					tiles[plane][loopIndex][loopIndex2] = new SceneTile(plane, loopIndex, loopIndex2);
		}
	}

	/**
	 * Sets bridge mode.
	 *
	 * @param x the x
	 * @param y the y
	 */
	public void setBridgeMode(int x, int y) {
		SceneTile sceneTile = tiles[0][x][y];
		for (int loopIndex = 0; loopIndex < 3; loopIndex++) {
			SceneTile sceneTile2 = tiles[loopIndex][x][y] = tiles[loopIndex + 1][x][y];
			if (sceneTile2 != null) {
				sceneTile2.plane--;
				for (int loopIndex2 = 0; loopIndex2 < sceneTile2.interactiveObjectCount; loopIndex2++) {
					InteractiveObject interactiveObject = sceneTile2.interactiveObjects[loopIndex2];
					if ((interactiveObject.uid >> SceneUid.ENTITY_TYPE_SHIFT
							& SceneUid.ENTITY_TYPE_MASK) == SceneUid.TYPE_OBJECT && interactiveObject.tileLeft == x
							&& interactiveObject.tileTop == y)
						interactiveObject.plane--;
				}

			}
		}

		if (tiles[0][x][y] == null)
			tiles[0][x][y] = new SceneTile(0, x, y);
		tiles[0][x][y].tileBelow = sceneTile;
		tiles[3][x][y] = null;
		markGeometryChanged();
	}

	/**
	 * Adds occluder.
	 *
	 * @param plane     the plane
	 * @param minWorldX the min world x
	 * @param minWorldZ the min world z
	 * @param maxWorldX the max world x
	 * @param maxWorldY the max world y
	 * @param maxWorldZ the max world z
	 * @param minWorldY the min world y
	 * @param type      the type
	 */
	public static void addOccluder(int plane, int minWorldX, int minWorldZ, int maxWorldX, int maxWorldY, int maxWorldZ,
			int minWorldY, int type) {
		SceneCluster sceneCluster = new SceneCluster();
		sceneCluster.minTileX = minWorldX / 128;
		sceneCluster.maxTileX = maxWorldX / 128;
		sceneCluster.minTileY = minWorldY / 128;
		sceneCluster.maxTileY = maxWorldY / 128;
		sceneCluster.type = type;
		sceneCluster.minWorldX = minWorldX;
		sceneCluster.maxWorldX = maxWorldX;
		sceneCluster.minWorldY = minWorldY;
		sceneCluster.maxWorldY = maxWorldY;
		sceneCluster.minWorldZ = minWorldZ;
		sceneCluster.maxWorldZ = maxWorldZ;
		occluders[plane][occluderCounts[plane]++] = sceneCluster;
	}

	/**
	 * Sets tile logic height.
	 *
	 * @param plane       the plane
	 * @param x           the x
	 * @param y           the y
	 * @param logicHeight the logic height
	 */
	public void setTileLogicHeight(int plane, int x, int y, int logicHeight) {
		SceneTile sceneTile = tiles[plane][x][y];
		if (sceneTile == null) {
			return;
		}
		if (sceneTile.logicHeight != logicHeight) {
			sceneTile.logicHeight = logicHeight;
			markGeometryChanged();
		}
	}

	/**
	 * Adds tile.
	 *
	 * @param plane             the plane
	 * @param x                 the x
	 * @param y                 the y
	 * @param shape             the shape
	 * @param rotation          the rotation
	 * @param textureId         the texture id
	 * @param southWestHeight   the south west height
	 * @param southEastHeight   the south east height
	 * @param northEastHeight   the north east height
	 * @param northWestHeight   the north west height
	 * @param underlaySouthWest the underlay south west
	 * @param underlaySouthEast the underlay south east
	 * @param underlayNorthEast the underlay north east
	 * @param inputValue        the input value
	 * @param inputValue2       the input value2
	 * @param inputValue3       the input value3
	 * @param inputValue4       the input value4
	 * @param inputValue5       the input value5
	 * @param inputValue6       the input value6
	 * @param inputValue7       the input value7
	 */
	public void addTile(int plane, int x, int y, int shape, int rotation, int textureId, int southWestHeight,
			int southEastHeight, int northEastHeight, int northWestHeight, int underlaySouthWest, int underlaySouthEast,
			int underlayNorthEast, int inputValue, int inputValue2, int inputValue3, int inputValue4, int inputValue5,
			int inputValue6, int inputValue7) {
		markGeometryChanged();
		if (shape == 0) {
			GenericTile genericTile = new GenericTile(underlaySouthWest, underlaySouthEast, underlayNorthEast,
					inputValue, -1, inputValue6, false);
			for (int loopIndex = plane; loopIndex >= 0; loopIndex--)
				if (tiles[loopIndex][x][y] == null)
					tiles[loopIndex][x][y] = new SceneTile(loopIndex, x, y);

			tiles[plane][x][y].plainTile = genericTile;
			return;
		}
		if (shape == 1) {
			GenericTile genericTile2 = new GenericTile(inputValue2, inputValue3, inputValue4, inputValue5, textureId,
					inputValue7, southWestHeight == southEastHeight && southWestHeight == northEastHeight
							&& southWestHeight == northWestHeight);
			for (int loopIndex2 = plane; loopIndex2 >= 0; loopIndex2--)
				if (tiles[loopIndex2][x][y] == null)
					tiles[loopIndex2][x][y] = new SceneTile(loopIndex2, x, y);

			tiles[plane][x][y].plainTile = genericTile2;
			return;
		}
		ComplexTile complexTile = new ComplexTile(x, southWestHeight, southEastHeight, northWestHeight, northEastHeight,
				y, rotation, textureId, shape, underlaySouthWest, inputValue2, underlaySouthEast, inputValue3,
				inputValue, inputValue5, underlayNorthEast, inputValue4, inputValue7, inputValue6);
		for (int loopIndex3 = plane; loopIndex3 >= 0; loopIndex3--)
			if (tiles[loopIndex3][x][y] == null)
				tiles[loopIndex3][x][y] = new SceneTile(loopIndex3, x, y);

		tiles[plane][x][y].shapedTile = complexTile;
	}

	/**
	 * Adds floor decoration.
	 *
	 * @param plane      the plane
	 * @param x          the x
	 * @param y          the y
	 * @param height     the height
	 * @param uid        the uid
	 * @param config     the config
	 * @param renderable the renderable
	 */
	public void addFloorDecoration(int plane, int x, int y, int height, int uid, byte config, Renderable renderable) {
		if (renderable == null)
			return;
		FloorDecoration floorDecoration = new FloorDecoration();
		floorDecoration.renderable = renderable;
		floorDecoration.x = x * 128 + 64;
		floorDecoration.y = y * 128 + 64;
		floorDecoration.z = height;
		floorDecoration.uid = uid;
		floorDecoration.config = config;
		if (tiles[plane][x][y] == null)
			tiles[plane][x][y] = new SceneTile(plane, x, y);
		tiles[plane][x][y].floorDecoration = floorDecoration;
		markGeometryChanged();
	}

	/**
	 * Adds ground item tile.
	 *
	 * @param plane  the plane
	 * @param x      the x
	 * @param y      the y
	 * @param height the height
	 * @param uid    the uid
	 * @param first  the first
	 * @param second the second
	 * @param third  the third
	 */
	public void addGroundItemTile(int plane, int x, int y, int height, int uid, Renderable first, Renderable second,
			Renderable third) {
		GroundItemTile groundItemTile = new GroundItemTile();
		groundItemTile.firstGroundItem = first;
		groundItemTile.x = x * 128 + 64;
		groundItemTile.y = y * 128 + 64;
		groundItemTile.z = height;
		groundItemTile.uid = uid;
		groundItemTile.secondGroundItem = second;
		groundItemTile.thirdGroundItem = third;
		int intermediateValue = 0;
		SceneTile sceneTile = tiles[plane][x][y];
		if (sceneTile != null) {
			for (int loopIndex = 0; loopIndex < sceneTile.interactiveObjectCount; loopIndex++)
				if (sceneTile.interactiveObjects[loopIndex].renderable instanceof Model) {
					int intermediateValue2 = ((Model) sceneTile.interactiveObjects[loopIndex].renderable).itemDropHeight;
					if (intermediateValue2 > intermediateValue)
						intermediateValue = intermediateValue2;
				}

		}
		groundItemTile.heightOffset = intermediateValue;
		if (tiles[plane][x][y] == null)
			tiles[plane][x][y] = new SceneTile(plane, x, y);
		tiles[plane][x][y].groundItemTile = groundItemTile;
	}

	/**
	 * Adds wall.
	 *
	 * @param plane                the plane
	 * @param x                    the x
	 * @param y                    the y
	 * @param drawHeight           the draw height
	 * @param uid                  the uid
	 * @param config               the config
	 * @param primary              the primary
	 * @param secondary            the secondary
	 * @param orientation          the orientation
	 * @param secondaryOrientation the secondary orientation
	 */
	public void addWall(int plane, int x, int y, int drawHeight, int uid, byte config, Renderable primary,
			Renderable secondary, int orientation, int secondaryOrientation) {
		if (primary == null && secondary == null)
			return;
		Wall wall = new Wall();
		wall.uid = uid;
		wall.config = config;
		wall.x = x * 128 + 64;
		wall.y = y * 128 + 64;
		wall.z = drawHeight;
		wall.primary = primary;
		wall.secondary = secondary;
		wall.orientation = orientation;
		wall.secondaryOrientation = secondaryOrientation;
		for (int tempplane = plane; tempplane >= 0; tempplane--)
			if (tiles[tempplane][x][y] == null)
				tiles[tempplane][x][y] = new SceneTile(tempplane, x, y);

		tiles[plane][x][y].wall = wall;
		markGeometryChanged();
	}

	/**
	 * Adds wall decoration.
	 *
	 * @param plane      the plane
	 * @param x          the x
	 * @param y          the y
	 * @param drawHeight the draw height
	 * @param offsetX    the offset x
	 * @param offsetY    the offset y
	 * @param face       the face
	 * @param uid        the uid
	 * @param config     the config
	 * @param configBits the config bits
	 * @param renderable the renderable
	 */
	public void addWallDecoration(int plane, int x, int y, int drawHeight, int offsetX, int offsetY, int face, int uid,
			byte config, int configBits, Renderable renderable) {
		if (renderable == null)
			return;
		WallDecoration wallDecoration = new WallDecoration();
		wallDecoration.uid = uid;
		wallDecoration.config = config;
		wallDecoration.x = x * 128 + 64 + offsetX;
		wallDecoration.y = y * 128 + 64 + offsetY;
		wallDecoration.z = drawHeight;
		wallDecoration.renderable = renderable;
		wallDecoration.configBits = configBits;
		wallDecoration.face = face;
		for (int loopIndex = plane; loopIndex >= 0; loopIndex--)
			if (tiles[loopIndex][x][y] == null)
				tiles[loopIndex][x][y] = new SceneTile(loopIndex, x, y);

		tiles[plane][x][y].wallDecoration = wallDecoration;
		markGeometryChanged();
	}

	/**
	 * Adds game object.
	 *
	 * @return {@code true} when add game object; otherwise {@code false}
	 * @param plane      the plane
	 * @param x          the x
	 * @param y          the y
	 * @param tileWidth  the tile width
	 * @param tileHeight the tile height
	 * @param drawHeight the draw height
	 * @param renderable the renderable
	 * @param rotation   the rotation
	 * @param uid        the uid
	 * @param config     the config
	 */
	public boolean addGameObject(int plane, int x, int y, int tileWidth, int tileHeight, int drawHeight,
			Renderable renderable, int rotation, int uid, byte config) {
		if (renderable == null) {
			return true;
		} else {
			int intermediateValue = x * 128 + 64 * tileWidth;
			int intermediateValue2 = y * 128 + 64 * tileHeight;
			return addInteractiveObject(plane, x, y, tileWidth, tileHeight, intermediateValue, intermediateValue2,
					drawHeight, renderable, rotation, false, uid, config);
		}
	}

	/**
	 * Adds entity.
	 *
	 * @return {@code true} when add entity; otherwise {@code false}
	 * @param plane         the plane
	 * @param worldX        the world x
	 * @param worldY        the world y
	 * @param worldZ        the world z
	 * @param renderable    the renderable
	 * @param uid           the uid
	 * @param radius        the radius
	 * @param accountForYaw the account for yaw
	 * @param yaw           the yaw
	 */
	public boolean addEntity(int plane, int worldX, int worldY, int worldZ, Renderable renderable, int uid, int radius,
			boolean accountForYaw, int yaw) {
		if (renderable == null)
			return true;
		int intermediateValue = worldX - radius;
		int intermediateValue2 = worldY - radius;
		int maxWorldX = worldX + radius;
		int maxWorldY = worldY + radius;
		if (accountForYaw) {
			if (yaw > 640 && yaw < 1408)
				maxWorldY += 128;
			if (yaw > 1152 && yaw < 1920)
				maxWorldX += 128;
			if (yaw > 1664 || yaw < 384)
				intermediateValue2 -= 128;
			if (yaw > 128 && yaw < 896)
				intermediateValue -= 128;
		}
		intermediateValue /= 128;
		intermediateValue2 /= 128;
		maxWorldX /= 128;
		maxWorldY /= 128;
		return addInteractiveObject(plane, intermediateValue, intermediateValue2, (maxWorldX - intermediateValue) + 1,
				(maxWorldY - intermediateValue2) + 1, worldX, worldY, worldZ, renderable, yaw, true, uid, (byte) 0);
	}

	/**
	 * Adds entity bounds.
	 *
	 * @return {@code true} when add entity bounds; otherwise {@code false}
	 * @param plane      the plane
	 * @param minX       the min x
	 * @param minY       the min y
	 * @param maxX       the max x
	 * @param maxY       the max y
	 * @param worldX     the world x
	 * @param worldY     the world y
	 * @param worldZ     the world z
	 * @param renderable the renderable
	 * @param rotation   the rotation
	 * @param uid        the uid
	 */
	public boolean addEntityBounds(int plane, int minX, int minY, int maxX, int maxY, int worldX, int worldY,
			int worldZ, Renderable renderable, int rotation, int uid) {
		if (renderable == null)
			return true;
		else
			return addInteractiveObject(plane, minX, minY, (maxX - minX) + 1, (maxY - minY) + 1, worldX, worldY, worldZ,
					renderable, rotation, true, uid, (byte) 0);
	}

	/**
	 * Adds interactive object.
	 *
	 * @return {@code true} when add interactive object; otherwise {@code false}
	 * @param plane      the plane
	 * @param minX       the min x
	 * @param minY       the min y
	 * @param tileWidth  the tile width
	 * @param tileHeight the tile height
	 * @param worldX     the world x
	 * @param worldY     the world y
	 * @param worldZ     the world z
	 * @param renderable the renderable
	 * @param rotation   the rotation
	 * @param temporary  the temporary
	 * @param uid        the uid
	 * @param config     the config
	 */
	public boolean addInteractiveObject(int plane, int minX, int minY, int tileWidth, int tileHeight, int worldX,
			int worldY, int worldZ, Renderable renderable, int rotation, boolean temporary, int uid, byte config) {
		for (int loopIndex = minX; loopIndex < minX + tileWidth; loopIndex++) {
			for (int loopIndex2 = minY; loopIndex2 < minY + tileHeight; loopIndex2++) {
				if (loopIndex < 0 || loopIndex2 < 0 || loopIndex >= width || loopIndex2 >= height)
					return false;
				SceneTile sceneTile = tiles[plane][loopIndex][loopIndex2];
				if (sceneTile != null && sceneTile.interactiveObjectCount >= 5)
					return false;
			}

		}

		InteractiveObject interactiveObject = new InteractiveObject();
		interactiveObject.uid = uid;
		interactiveObject.config = config;
		interactiveObject.plane = plane;
		interactiveObject.worldX = worldX;
		interactiveObject.worldY = worldY;
		interactiveObject.worldZ = worldZ;
		interactiveObject.renderable = renderable;
		interactiveObject.rotation = rotation;
		interactiveObject.tileLeft = minX;
		interactiveObject.tileTop = minY;
		interactiveObject.tileRight = (minX + tileWidth) - 1;
		interactiveObject.tileBottom = (minY + tileHeight) - 1;
		for (int loopIndex3 = minX; loopIndex3 < minX + tileWidth; loopIndex3++) {
			for (int loopIndex4 = minY; loopIndex4 < minY + tileHeight; loopIndex4++) {
				int intermediateValue = 0;
				if (loopIndex3 > minX)
					intermediateValue++;
				if (loopIndex3 < (minX + tileWidth) - 1)
					intermediateValue += 4;
				if (loopIndex4 > minY)
					intermediateValue += 8;
				if (loopIndex4 < (minY + tileHeight) - 1)
					intermediateValue += 2;
				for (int loopIndex5 = plane; loopIndex5 >= 0; loopIndex5--)
					if (tiles[loopIndex5][loopIndex3][loopIndex4] == null)
						tiles[loopIndex5][loopIndex3][loopIndex4] = new SceneTile(loopIndex5, loopIndex3, loopIndex4);

				SceneTile sceneTile2 = tiles[plane][loopIndex3][loopIndex4];
				sceneTile2.interactiveObjects[sceneTile2.interactiveObjectCount] = interactiveObject;
				sceneTile2.interactiveObjectEdgeMasks[sceneTile2.interactiveObjectCount] = intermediateValue;
				sceneTile2.combinedInteractiveObjectEdgeMask |= intermediateValue;
				sceneTile2.interactiveObjectCount++;
			}

		}

		if (temporary) {
			temporaryObjects[temporaryObjectCount++] = interactiveObject;
		} else {
			markGeometryChanged();
		}
		return true;
	}

	/**
	 * Clears temporary objects state.
	 */
	public void clearTemporaryObjects() {
		for (int loopIndex = 0; loopIndex < temporaryObjectCount; loopIndex++) {
			InteractiveObject interactiveObject = temporaryObjects[loopIndex];
			removeInteractiveObjectInternal(interactiveObject);
			temporaryObjects[loopIndex] = null;
		}

		temporaryObjectCount = 0;
	}

	/**
	 * Removes interactive object internal.
	 *
	 * @param interactiveObject the interactive object
	 */
	public void removeInteractiveObjectInternal(InteractiveObject interactiveObject) {
		for (int loopIndex = interactiveObject.tileLeft; loopIndex <= interactiveObject.tileRight; loopIndex++) {
			for (int loopIndex2 = interactiveObject.tileTop; loopIndex2 <= interactiveObject.tileBottom; loopIndex2++) {
				SceneTile sceneTile = tiles[interactiveObject.plane][loopIndex][loopIndex2];
				if (sceneTile != null) {
					for (int loopIndex3 = 0; loopIndex3 < sceneTile.interactiveObjectCount; loopIndex3++) {
						if (sceneTile.interactiveObjects[loopIndex3] != interactiveObject)
							continue;
						sceneTile.interactiveObjectCount--;
						for (int loopIndex4 = loopIndex3; loopIndex4 < sceneTile.interactiveObjectCount; loopIndex4++) {
							sceneTile.interactiveObjects[loopIndex4] = sceneTile.interactiveObjects[loopIndex4 + 1];
							sceneTile.interactiveObjectEdgeMasks[loopIndex4] = sceneTile.interactiveObjectEdgeMasks[loopIndex4
									+ 1];
						}

						sceneTile.interactiveObjects[sceneTile.interactiveObjectCount] = null;
						break;
					}

					sceneTile.combinedInteractiveObjectEdgeMask = 0;
					for (int loopIndex5 = 0; loopIndex5 < sceneTile.interactiveObjectCount; loopIndex5++)
						sceneTile.combinedInteractiveObjectEdgeMask |= sceneTile.interactiveObjectEdgeMasks[loopIndex5];

				}
			}

		}
	}

	/**
	 * Performs displace wall decoration.
	 *
	 * @param plane        the plane
	 * @param x            the x
	 * @param y            the y
	 * @param displacement the displacement
	 */
	public void displaceWallDecoration(int plane, int x, int y, int displacement) {
		SceneTile sceneTile = tiles[plane][x][y];
		if (sceneTile == null)
			return;
		WallDecoration wallDecoration = sceneTile.wallDecoration;
		if (wallDecoration == null)
			return;
		int intermediateValue = x * 128 + 64;
		int intermediateValue2 = y * 128 + 64;
		wallDecoration.x = intermediateValue + ((wallDecoration.x - intermediateValue) * displacement) / 16;
		wallDecoration.y = intermediateValue2 + ((wallDecoration.y - intermediateValue2) * displacement) / 16;
		markGeometryChanged();
	}

	/**
	 * Removes wall.
	 *
	 * @param plane the plane
	 * @param x     the x
	 * @param y     the y
	 */
	public void removeWall(int plane, int x, int y) {
		SceneTile sceneTile = tiles[plane][x][y];
		if (sceneTile == null || sceneTile.wall == null)
			return;
		sceneTile.wall = null;
		markGeometryChanged();
	}

	/**
	 * Removes wall decoration.
	 *
	 * @param plane the plane
	 * @param x     the x
	 * @param y     the y
	 */
	public void removeWallDecoration(int plane, int x, int y) {
		SceneTile sceneTile = tiles[plane][x][y];
		if (sceneTile == null || sceneTile.wallDecoration == null) {
			return;
		}
		sceneTile.wallDecoration = null;
		markGeometryChanged();
	}

	/**
	 * Removes interactive object.
	 *
	 * @param plane the plane
	 * @param x     the x
	 * @param y     the y
	 */
	public void removeInteractiveObject(int plane, int x, int y) {
		SceneTile sceneTile = tiles[plane][x][y];
		if (sceneTile == null)
			return;
		for (int loopIndex = 0; loopIndex < sceneTile.interactiveObjectCount; loopIndex++) {
			InteractiveObject interactiveObject = sceneTile.interactiveObjects[loopIndex];
			if ((interactiveObject.uid >> SceneUid.ENTITY_TYPE_SHIFT
					& SceneUid.ENTITY_TYPE_MASK) == SceneUid.TYPE_OBJECT && interactiveObject.tileLeft == x
					&& interactiveObject.tileTop == y) {
				removeInteractiveObjectInternal(interactiveObject);
				markGeometryChanged();
				return;
			}
		}

	}

	/**
	 * Removes floor decoration.
	 *
	 * @param plane the plane
	 * @param x     the x
	 * @param y     the y
	 */
	public void removeFloorDecoration(int plane, int x, int y) {
		SceneTile sceneTile = tiles[plane][x][y];
		if (sceneTile == null || sceneTile.floorDecoration == null)
			return;
		sceneTile.floorDecoration = null;
		markGeometryChanged();
	}

	/**
	 * Removes ground item tile.
	 *
	 * @param plane the plane
	 * @param x     the x
	 * @param y     the y
	 */
	public void removeGroundItemTile(int plane, int x, int y) {
		SceneTile sceneTile = tiles[plane][x][y];
		if (sceneTile == null) {
			return;
		} else {
			sceneTile.groundItemTile = null;
			return;
		}
	}

	/**
	 * Returns wall.
	 *
	 * @return the resulting wall
	 * @param plane the plane
	 * @param x     the x
	 * @param y     the y
	 */
	public Wall getWall(int plane, int x, int y) {
		SceneTile sceneTile = tiles[plane][x][y];
		if (sceneTile == null)
			return null;
		else
			return sceneTile.wall;
	}

	/**
	 * Returns wall decoration.
	 *
	 * @return the resulting wall decoration
	 * @param plane the plane
	 * @param x     the x
	 * @param y     the y
	 */
	public WallDecoration getWallDecoration(int plane, int x, int y) {
		SceneTile sceneTile = tiles[plane][x][y];
		if (sceneTile == null)
			return null;
		else
			return sceneTile.wallDecoration;
	}

	/**
	 * Returns interactive object.
	 *
	 * @return the resulting interactive object
	 * @param plane the plane
	 * @param x     the x
	 * @param y     the y
	 */
	public InteractiveObject getInteractiveObject(int plane, int x, int y) {
		SceneTile sceneTile = tiles[plane][x][y];
		if (sceneTile == null)
			return null;
		for (int loopIndex = 0; loopIndex < sceneTile.interactiveObjectCount; loopIndex++) {
			InteractiveObject interactiveObject = sceneTile.interactiveObjects[loopIndex];
			if ((interactiveObject.uid >> SceneUid.ENTITY_TYPE_SHIFT
					& SceneUid.ENTITY_TYPE_MASK) == SceneUid.TYPE_OBJECT && interactiveObject.tileLeft == x
					&& interactiveObject.tileTop == y)
				return interactiveObject;
		}

		return null;
	}

	/**
	 * Returns floor decoration.
	 *
	 * @return the resulting floor decoration
	 * @param plane the plane
	 * @param x     the x
	 * @param y     the y
	 */
	public FloorDecoration getFloorDecoration(int plane, int x, int y) {
		SceneTile sceneTile = tiles[plane][x][y];
		if (sceneTile == null || sceneTile.floorDecoration == null)
			return null;
		else
			return sceneTile.floorDecoration;
	}

	/**
	 * Returns wall uid.
	 *
	 * @return the resulting int
	 * @param plane the plane
	 * @param x     the x
	 * @param y     the y
	 */
	public int getWallUid(int plane, int x, int y) {
		SceneTile sceneTile = tiles[plane][x][y];
		if (sceneTile == null || sceneTile.wall == null)
			return 0;
		else
			return sceneTile.wall.uid;
	}

	/**
	 * Returns wall decoration uid.
	 *
	 * @return the resulting int
	 * @param plane the plane
	 * @param x     the x
	 * @param y     the y
	 */
	public int getWallDecorationUid(int plane, int x, int y) {
		SceneTile sceneTile = tiles[plane][x][y];
		if (sceneTile == null || sceneTile.wallDecoration == null)
			return 0;
		else
			return sceneTile.wallDecoration.uid;
	}

	/**
	 * Returns interactive object uid.
	 *
	 * @return the resulting int
	 * @param plane the plane
	 * @param x     the x
	 * @param y     the y
	 */
	public int getInteractiveObjectUid(int plane, int x, int y) {
		SceneTile sceneTile = tiles[plane][x][y];
		if (sceneTile == null)
			return 0;
		for (int loopIndex = 0; loopIndex < sceneTile.interactiveObjectCount; loopIndex++) {
			InteractiveObject interactiveObject = sceneTile.interactiveObjects[loopIndex];
			if ((interactiveObject.uid >> SceneUid.ENTITY_TYPE_SHIFT
					& SceneUid.ENTITY_TYPE_MASK) == SceneUid.TYPE_OBJECT && interactiveObject.tileLeft == x
					&& interactiveObject.tileTop == y)
				return interactiveObject.uid;
		}

		return 0;
	}

	/**
	 * Returns floor decoration uid.
	 *
	 * @return the resulting int
	 * @param plane the plane
	 * @param x     the x
	 * @param y     the y
	 */
	public int getFloorDecorationUid(int plane, int x, int y) {
		SceneTile sceneTile = tiles[plane][x][y];
		if (sceneTile == null || sceneTile.floorDecoration == null)
			return 0;
		else
			return sceneTile.floorDecoration.uid;
	}

	/**
	 * Returns config.
	 *
	 * @return the resulting int
	 * @param plane the plane
	 * @param x     the x
	 * @param y     the y
	 * @param uid   the uid
	 */
	public int getConfig(int plane, int x, int y, int uid) {
		SceneTile sceneTile = tiles[plane][x][y];
		if (sceneTile == null)
			return -1;
		if (sceneTile.wall != null && sceneTile.wall.uid == uid)
			return sceneTile.wall.config & 0xff;
		if (sceneTile.wallDecoration != null && sceneTile.wallDecoration.uid == uid)
			return sceneTile.wallDecoration.config & 0xff;
		if (sceneTile.floorDecoration != null && sceneTile.floorDecoration.uid == uid)
			return sceneTile.floorDecoration.config & 0xff;
		for (int loopIndex = 0; loopIndex < sceneTile.interactiveObjectCount; loopIndex++)
			if (sceneTile.interactiveObjects[loopIndex].uid == uid)
				return sceneTile.interactiveObjects[loopIndex].config & 0xff;

		return -1;
	}

	/**
	 * Performs shade models.
	 *
	 * @param lightX the light x
	 * @param lightY the light y
	 * @param lightZ the light z
	 */
	public void shadeModels(int lightX, int lightY, int lightZ) {
		for (int loopIndex = 0; loopIndex < planeCount; loopIndex++) {
			for (int loopIndex2 = 0; loopIndex2 < width; loopIndex2++) {
				for (int loopIndex3 = 0; loopIndex3 < height; loopIndex3++) {
					SceneTile sceneTile = tiles[loopIndex][loopIndex2][loopIndex3];
					if (sceneTile != null) {
						Wall wall = sceneTile.wall;
						if (wall != null && wall.primary != null && wall.primary.vertexNormals != null) {
							mergeAdjacentNormals((Model) wall.primary, loopIndex, loopIndex2, loopIndex3, 1, 1);
							if (wall.secondary != null && wall.secondary.vertexNormals != null) {
								mergeAdjacentNormals((Model) wall.secondary, loopIndex, loopIndex2, loopIndex3, 1, 1);
								mergeNormals((Model) wall.primary, (Model) wall.secondary, 0, 0, 0, false);
								((Model) wall.secondary).applyDeferredLighting(lightX, lightY, lightZ);
							}
							((Model) wall.primary).applyDeferredLighting(lightX, lightY, lightZ);
						}
						for (int loopIndex4 = 0; loopIndex4 < sceneTile.interactiveObjectCount; loopIndex4++) {
							InteractiveObject interactiveObject = sceneTile.interactiveObjects[loopIndex4];
							if (interactiveObject != null && interactiveObject.renderable != null
									&& interactiveObject.renderable.vertexNormals != null) {
								mergeAdjacentNormals((Model) interactiveObject.renderable, loopIndex, loopIndex2,
										loopIndex3, (interactiveObject.tileRight - interactiveObject.tileLeft) + 1,
										(interactiveObject.tileBottom - interactiveObject.tileTop) + 1);
								((Model) interactiveObject.renderable).applyDeferredLighting(lightX, lightY, lightZ);
							}
						}

						FloorDecoration floorDecoration = sceneTile.floorDecoration;
						if (floorDecoration != null && floorDecoration.renderable.vertexNormals != null) {
							mergeFloorDecorationNormals((Model) floorDecoration.renderable, loopIndex, loopIndex2,
									loopIndex3);
							((Model) floorDecoration.renderable).applyDeferredLighting(lightX, lightY, lightZ);
						}
					}
				}

			}

		}
	}

	/**
	 * Performs merge floor decoration normals.
	 *
	 * @param model the model
	 * @param plane the plane
	 * @param x     the x
	 * @param y     the y
	 */
	public void mergeFloorDecorationNormals(Model model, int plane, int x, int y) {
		if (x < width) {
			SceneTile sceneTile = tiles[plane][x + 1][y];
			if (sceneTile != null && sceneTile.floorDecoration != null
					&& sceneTile.floorDecoration.renderable.vertexNormals != null)
				mergeNormals(model, (Model) sceneTile.floorDecoration.renderable, 128, 0, 0, true);
		}
		if (y < width) {
			SceneTile sceneTile2 = tiles[plane][x][y + 1];
			if (sceneTile2 != null && sceneTile2.floorDecoration != null
					&& sceneTile2.floorDecoration.renderable.vertexNormals != null)
				mergeNormals(model, (Model) sceneTile2.floorDecoration.renderable, 0, 0, 128, true);
		}
		if (x < width && y < height) {
			SceneTile sceneTile3 = tiles[plane][x + 1][y + 1];
			if (sceneTile3 != null && sceneTile3.floorDecoration != null
					&& sceneTile3.floorDecoration.renderable.vertexNormals != null)
				mergeNormals(model, (Model) sceneTile3.floorDecoration.renderable, 128, 0, 128, true);
		}
		if (x < width && y > 0) {
			SceneTile sceneTile4 = tiles[plane][x + 1][y - 1];
			if (sceneTile4 != null && sceneTile4.floorDecoration != null
					&& sceneTile4.floorDecoration.renderable.vertexNormals != null)
				mergeNormals(model, (Model) sceneTile4.floorDecoration.renderable, 128, 0, -128, true);
		}
	}

	/**
	 * Performs merge adjacent normals.
	 *
	 * @param model the model
	 * @param plane the plane
	 * @param x     the x
	 * @param y     the y
	 * @param sizeX the size x
	 * @param sizeY the size y
	 */
	public void mergeAdjacentNormals(Model model, int plane, int x, int y, int sizeX, int sizeY) {
		boolean conditionFlag = true;
		int intermediateValue = x;
		int intermediateValue2 = x + sizeX;
		int intermediateValue3 = y - 1;
		int intermediateValue4 = y + sizeY;
		for (int loopIndex = plane; loopIndex <= plane + 1; loopIndex++)
			if (loopIndex != planeCount) {
				for (int loopIndex2 = intermediateValue; loopIndex2 <= intermediateValue2; loopIndex2++)
					if (loopIndex2 >= 0 && loopIndex2 < width) {
						for (int loopIndex3 = intermediateValue3; loopIndex3 <= intermediateValue4; loopIndex3++)
							if (loopIndex3 >= 0 && loopIndex3 < height
									&& (!conditionFlag || loopIndex2 >= intermediateValue2
											|| loopIndex3 >= intermediateValue4 || loopIndex3 < y && loopIndex2 != x)) {
								SceneTile sceneTile = tiles[loopIndex][loopIndex2][loopIndex3];
								if (sceneTile != null) {
									int intermediateValue5 = (tileHeights[loopIndex][loopIndex2][loopIndex3]
											+ tileHeights[loopIndex][loopIndex2 + 1][loopIndex3]
											+ tileHeights[loopIndex][loopIndex2][loopIndex3 + 1]
											+ tileHeights[loopIndex][loopIndex2 + 1][loopIndex3 + 1]) / 4
											- (tileHeights[plane][x][y] + tileHeights[plane][x + 1][y]
													+ tileHeights[plane][x][y + 1] + tileHeights[plane][x + 1][y + 1])
													/ 4;
									Wall wall = sceneTile.wall;
									if (wall != null && wall.primary != null && wall.primary.vertexNormals != null)
										mergeNormals(model, (Model) wall.primary,
												(loopIndex2 - x) * 128 + (1 - sizeX) * 64, intermediateValue5,
												(loopIndex3 - y) * 128 + (1 - sizeY) * 64, conditionFlag);
									if (wall != null && wall.secondary != null && wall.secondary.vertexNormals != null)
										mergeNormals(model, (Model) wall.secondary,
												(loopIndex2 - x) * 128 + (1 - sizeX) * 64, intermediateValue5,
												(loopIndex3 - y) * 128 + (1 - sizeY) * 64, conditionFlag);
									for (int loopIndex4 = 0; loopIndex4 < sceneTile.interactiveObjectCount; loopIndex4++) {
										InteractiveObject interactiveObject = sceneTile.interactiveObjects[loopIndex4];
										if (interactiveObject != null && interactiveObject.renderable != null
												&& interactiveObject.renderable.vertexNormals != null) {
											int intermediateValue6 = (interactiveObject.tileRight
													- interactiveObject.tileLeft) + 1;
											int intermediateValue7 = (interactiveObject.tileBottom
													- interactiveObject.tileTop) + 1;
											mergeNormals(model, (Model) interactiveObject.renderable,
													(interactiveObject.tileLeft - x) * 128
															+ (intermediateValue6 - sizeX) * 64,
													intermediateValue5, (interactiveObject.tileTop - y) * 128
															+ (intermediateValue7 - sizeY) * 64,
													conditionFlag);
										}
									}

								}
							}

					}

				intermediateValue--;
				conditionFlag = false;
			}
	}

	/**
	 * Performs merge normals.
	 *
	 * @param modelA    the model a
	 * @param modelB    the model b
	 * @param offsetX   the offset x
	 * @param offsetY   the offset y
	 * @param offsetZ   the offset z
	 * @param hideFaces the hide faces
	 */
	public void mergeNormals(Model modelA, Model modelB, int offsetX, int offsetY, int offsetZ, boolean hideFaces) {
		mergeCycle++;
		int intermediateValue = 0;
		int values[] = modelB.verticesX;
		int intermediateValue2 = modelB.vertexCount;
		int intermediateValue3 = modelB.packedXBounds >> 16;
		int intermediateValue4 = (modelB.packedXBounds << 16) >> 16;
		int intermediateValue5 = modelB.packedZBounds >> 16;
		int intermediateValue6 = (modelB.packedZBounds << 16) >> 16;
		for (int loopIndex = 0; loopIndex < modelA.vertexCount; loopIndex++) {
			VertexNormal vertexNormal = ((Renderable) (modelA)).vertexNormals[loopIndex];
			VertexNormal vertexNormal2 = modelA.vertexNormalOffsets[loopIndex];
			if (vertexNormal2.magnitude != 0) {
				int intermediateValue7 = modelA.verticesY[loopIndex] - offsetY;
				if (intermediateValue7 <= modelB.maxY) {
					int intermediateValue8 = modelA.verticesX[loopIndex] - offsetX;
					if (intermediateValue8 >= intermediateValue3 && intermediateValue8 <= intermediateValue4) {
						int intermediateValue9 = modelA.verticesZ[loopIndex] - offsetZ;
						if (intermediateValue9 >= intermediateValue6 && intermediateValue9 <= intermediateValue5) {
							for (int loopIndex2 = 0; loopIndex2 < intermediateValue2; loopIndex2++) {
								VertexNormal vertexNormal3 = ((Renderable) (modelB)).vertexNormals[loopIndex2];
								VertexNormal vertexNormal4 = modelB.vertexNormalOffsets[loopIndex2];
								if (intermediateValue8 == values[loopIndex2]
										&& intermediateValue9 == modelB.verticesZ[loopIndex2]
										&& intermediateValue7 == modelB.verticesY[loopIndex2]
										&& vertexNormal4.magnitude != 0) {
									vertexNormal.x += vertexNormal4.x;
									vertexNormal.y += vertexNormal4.y;
									vertexNormal.z += vertexNormal4.z;
									vertexNormal.magnitude += vertexNormal4.magnitude;
									vertexNormal3.x += vertexNormal2.x;
									vertexNormal3.y += vertexNormal2.y;
									vertexNormal3.z += vertexNormal2.z;
									vertexNormal3.magnitude += vertexNormal2.magnitude;
									intermediateValue++;
									mergeStampA[loopIndex] = mergeCycle;
									mergeStampB[loopIndex2] = mergeCycle;
								}
							}

						}
					}
				}
			}
		}

		if (intermediateValue < 3 || !hideFaces)
			return;
		for (int loopIndex3 = 0; loopIndex3 < modelA.triangleCount; loopIndex3++)
			if (mergeStampA[modelA.triangleVertexA[loopIndex3]] == mergeCycle
					&& mergeStampA[modelA.triangleVertexB[loopIndex3]] == mergeCycle
					&& mergeStampA[modelA.triangleVertexC[loopIndex3]] == mergeCycle)
				modelA.triangleDrawType[loopIndex3] = -1;

		for (int loopIndex4 = 0; loopIndex4 < modelB.triangleCount; loopIndex4++)
			if (mergeStampB[modelB.triangleVertexA[loopIndex4]] == mergeCycle
					&& mergeStampB[modelB.triangleVertexB[loopIndex4]] == mergeCycle
					&& mergeStampB[modelB.triangleVertexC[loopIndex4]] == mergeCycle)
				modelB.triangleDrawType[loopIndex4] = -1;

	}

	/**
	 * Draws minimap tile.
	 *
	 * @param pixels      the pixels
	 * @param pixelOffset the pixel offset
	 * @param rowStride   the row stride
	 * @param plane       the plane
	 * @param x           the x
	 * @param y           the y
	 */
	public void drawMinimapTile(int pixels[], int pixelOffset, int rowStride, int plane, int x, int y) {
		SceneTile sceneTile = tiles[plane][x][y];
		if (sceneTile == null)
			return;
		GenericTile genericTile = sceneTile.plainTile;
		if (genericTile != null) {
			int intermediateValue = genericTile.rgbColour;
			if (intermediateValue == 0)
				return;
			for (int loopIndex = 0; loopIndex < 4; loopIndex++) {
				pixels[pixelOffset] = intermediateValue;
				pixels[pixelOffset + 1] = intermediateValue;
				pixels[pixelOffset + 2] = intermediateValue;
				pixels[pixelOffset + 3] = intermediateValue;
				pixelOffset += rowStride;
			}

			return;
		}
		ComplexTile complexTile = sceneTile.shapedTile;
		if (complexTile == null)
			return;
		int intermediateValue2 = complexTile.shape;
		int intermediateValue3 = complexTile.rotation;
		int intermediateValue4 = complexTile.underlayRgb;
		int intermediateValue5 = complexTile.overlayRgb;
		int values[] = minimapTileShape[intermediateValue2];
		int values2[] = minimapTileRotation[intermediateValue3];
		int intermediateValue6 = 0;
		if (intermediateValue4 != 0) {
			for (int loopIndex2 = 0; loopIndex2 < 4; loopIndex2++) {
				pixels[pixelOffset] = values[values2[intermediateValue6++]] != 0 ? intermediateValue5
						: intermediateValue4;
				pixels[pixelOffset + 1] = values[values2[intermediateValue6++]] != 0 ? intermediateValue5
						: intermediateValue4;
				pixels[pixelOffset + 2] = values[values2[intermediateValue6++]] != 0 ? intermediateValue5
						: intermediateValue4;
				pixels[pixelOffset + 3] = values[values2[intermediateValue6++]] != 0 ? intermediateValue5
						: intermediateValue4;
				pixelOffset += rowStride;
			}

			return;
		}
		for (int loopIndex3 = 0; loopIndex3 < 4; loopIndex3++) {
			if (values[values2[intermediateValue6++]] != 0)
				pixels[pixelOffset] = intermediateValue5;
			if (values[values2[intermediateValue6++]] != 0)
				pixels[pixelOffset + 1] = intermediateValue5;
			if (values[values2[intermediateValue6++]] != 0)
				pixels[pixelOffset + 2] = intermediateValue5;
			if (values[values2[intermediateValue6++]] != 0)
				pixels[pixelOffset + 3] = intermediateValue5;
			pixelOffset += rowStride;
		}

	}

	/**
	 * Builds visibility maps.
	 *
	 * @param minZ           the min z
	 * @param maxZ           the max z
	 * @param viewportWidth  the viewport width
	 * @param viewportHeight the viewport height
	 * @param pitchHeights   the pitch heights
	 */
	public static void buildVisibilityMaps(int minZ, int maxZ, int viewportWidth, int viewportHeight,
			int pitchHeights[]) {
		viewportMinX = 0;
		viewportMinY = 0;
		viewportMaxX = viewportWidth;
		viewportMaxY = viewportHeight;
		viewportCenterX = viewportWidth / 2;
		viewportCenterY = viewportHeight / 2;
		boolean aflag[][][][] = new boolean[9][32][53][53];
		for (int loopIndex = 128; loopIndex <= 384; loopIndex += 32) {
			for (int loopIndex2 = 0; loopIndex2 < Angle.FULL_TURN; loopIndex2 += 64) {
				pitchSine = Model.SINE[loopIndex];
				pitchCosine = Model.COSINE[loopIndex];
				yawSine = Model.SINE[loopIndex2];
				yawCosine = Model.COSINE[loopIndex2];
				int intermediateValue = (loopIndex - 128) / 32;
				int intermediateValue2 = loopIndex2 / 64;
				for (int loopIndex3 = -26; loopIndex3 <= 26; loopIndex3++) {
					for (int loopIndex4 = -26; loopIndex4 <= 26; loopIndex4++) {
						int intermediateValue3 = loopIndex3 * 128;
						int intermediateValue4 = loopIndex4 * 128;
						boolean conditionFlag = false;
						for (int loopIndex5 = -minZ; loopIndex5 <= maxZ; loopIndex5 += 128) {
							if (!isProjectionVisible(intermediateValue4, intermediateValue3,
									pitchHeights[intermediateValue] + loopIndex5))
								continue;
							conditionFlag = true;
							break;
						}

						aflag[intermediateValue][intermediateValue2][loopIndex3 + 25 + 1][loopIndex4 + 25
								+ 1] = conditionFlag;
					}

				}

			}

		}

		for (int loopIndex6 = 0; loopIndex6 < 8; loopIndex6++) {
			for (int loopIndex7 = 0; loopIndex7 < 32; loopIndex7++) {
				for (int loopIndex8 = -25; loopIndex8 < 25; loopIndex8++) {
					for (int loopIndex9 = -25; loopIndex9 < 25; loopIndex9++) {
						boolean conditionFlag2 = false;
						label0: for (int loopIndex10 = -1; loopIndex10 <= 1; loopIndex10++) {
							for (int loopIndex11 = -1; loopIndex11 <= 1; loopIndex11++) {
								if (aflag[loopIndex6][loopIndex7][loopIndex8 + loopIndex10 + 25 + 1][loopIndex9
										+ loopIndex11 + 25 + 1])
									conditionFlag2 = true;
								else if (aflag[loopIndex6][(loopIndex7 + 1) % 31][loopIndex8 + loopIndex10 + 25
										+ 1][loopIndex9 + loopIndex11 + 25 + 1])
									conditionFlag2 = true;
								else if (aflag[loopIndex6 + 1][loopIndex7][loopIndex8 + loopIndex10 + 25 + 1][loopIndex9
										+ loopIndex11 + 25 + 1]) {
									conditionFlag2 = true;
								} else {
									if (!aflag[loopIndex6 + 1][(loopIndex7 + 1) % 31][loopIndex8 + loopIndex10 + 25
											+ 1][loopIndex9 + loopIndex11 + 25 + 1])
										continue;
									conditionFlag2 = true;
								}
								break label0;
							}

						}

						visibilityMaps[loopIndex6][loopIndex7][loopIndex8 + 25][loopIndex9 + 25] = conditionFlag2;
					}

				}

			}

		}
	}

	/**
	 * Returns whether projection visible.
	 *
	 * @return {@code true} when projection visible; otherwise {@code false}
	 * @param x the x
	 * @param y the y
	 * @param z the z
	 */
	public static boolean isProjectionVisible(int x, int y, int z) {
		int intermediateValue = x * yawSine + y * yawCosine >> 16;
		int intermediateValue2 = x * yawCosine - y * yawSine >> 16;
		int intermediateValue3 = z * pitchSine + intermediateValue2 * pitchCosine >> 16;
		int intermediateValue4 = z * pitchCosine - intermediateValue2 * pitchSine >> 16;
		if (intermediateValue3 < 50 || intermediateValue3 > 3500)
			return false;
		int intermediateValue5 = viewportCenterX + (intermediateValue << 9) / intermediateValue3;
		int intermediateValue6 = viewportCenterY + (intermediateValue4 << 9) / intermediateValue3;
		return intermediateValue5 >= viewportMinX && intermediateValue5 <= viewportMaxX
				&& intermediateValue6 >= viewportMinY && intermediateValue6 <= viewportMaxY;
	}

	/**
	 * Sets click.
	 *
	 * @param mouseX the mouse x
	 * @param mouseY the mouse y
	 */
	public void setClick(int mouseX, int mouseY) {
		tilePickPending = true;
		tilePickRequestX = mouseX;
		tilePickRequestY = mouseY;
		/*
		 * Scene.render historically consumed this request while rasterizing. Flint now
		 * resolves it during the fixed 50 Hz logic cycle so walking does not depend on
		 * presentation FPS or on whether the software/GPU backend is active.
		 */
		picking = false;
		Scene.mouseX = mouseX;
		Scene.mouseY = mouseY;
		pickedTileX = -1;
		pickedTileY = -1;
	}

	/** Returns whether a walk-to terrain pick is waiting to be resolved. */
	public boolean tilePickPending() {
		return tilePickPending;
	}

	/** Returns the pending walk-pick X coordinate relative to the viewport. */
	public int tilePickRequestX() {
		return tilePickRequestX;
	}

	/** Returns the pending walk-pick Y coordinate relative to the viewport. */
	public int tilePickRequestY() {
		return tilePickRequestY;
	}

	/** Completes the pending terrain pick with the supplied tile, or -1/-1 on miss. */
	public void completeTilePick(int tileX, int tileY) {
		tilePickPending = false;
		pickedTileX = tileX;
		pickedTileY = tileY;
	}

	/**
	 * Renders value.
	 *
	 * @param cameraWorldX the camera world x
	 * @param cameraWorldY the camera world y
	 * @param cameraWorldZ the camera world z
	 * @param plane        the plane
	 * @param yaw          the yaw
	 * @param pitch        the pitch
	 */
	public void render(int cameraWorldX, int cameraWorldY, int cameraWorldZ, int plane, int yaw, int pitch) {
		if (cameraWorldX < 0)
			cameraWorldX = 0;
		else if (cameraWorldX >= width * 128)
			cameraWorldX = width * 128 - 1;
		if (cameraWorldY < 0)
			cameraWorldY = 0;
		else if (cameraWorldY >= height * 128)
			cameraWorldY = height * 128 - 1;
		renderCycle++;
		pitchSine = Model.SINE[pitch];
		pitchCosine = Model.COSINE[pitch];
		yawSine = Model.SINE[yaw];
		yawCosine = Model.COSINE[yaw];
		visibilityMap = visibilityMaps[(pitch - 128) / 32][yaw / 64];
		cameraX = cameraWorldX;
		cameraZ = cameraWorldZ;
		cameraY = cameraWorldY;
		cameraTileX = cameraWorldX / 128;
		cameraTileY = cameraWorldY / 128;
		renderPlane = plane;
		minTileX = cameraTileX - 25;
		if (minTileX < 0)
			minTileX = 0;
		minTileY = cameraTileY - 25;
		if (minTileY < 0)
			minTileY = 0;
		maxTileX = cameraTileX + 25;
		if (maxTileX > width)
			maxTileX = width;
		maxTileY = cameraTileY + 25;
		if (maxTileY > height)
			maxTileY = height;
		processOccluders();
		remainingTileCount = 0;
		for (int loopIndex = minPlane; loopIndex < planeCount; loopIndex++) {
			SceneTile aclass50_sub3[][] = tiles[loopIndex];
			for (int loopIndex2 = minTileX; loopIndex2 < maxTileX; loopIndex2++) {
				for (int loopIndex3 = minTileY; loopIndex3 < maxTileY; loopIndex3++) {
					SceneTile sceneTile = aclass50_sub3[loopIndex2][loopIndex3];
					if (sceneTile != null)
						if (sceneTile.logicHeight > plane
								|| !visibilityMap[(loopIndex2 - cameraTileX) + 25][(loopIndex3 - cameraTileY) + 25]
										&& tileHeights[loopIndex][loopIndex2][loopIndex3] - cameraWorldZ < 2000) {
							sceneTile.draw = false;
							sceneTile.visible = false;
							sceneTile.wallCullDirection = 0;
						} else {
							sceneTile.draw = true;
							sceneTile.visible = true;
							if (sceneTile.interactiveObjectCount > 0)
								sceneTile.drawEntities = true;
							else
								sceneTile.drawEntities = false;
							remainingTileCount++;
						}
				}

			}

		}

		for (int loopIndex4 = minPlane; loopIndex4 < planeCount; loopIndex4++) {
			SceneTile aclass50_sub3_1[][] = tiles[loopIndex4];
			for (int loopIndex5 = -25; loopIndex5 <= 0; loopIndex5++) {
				int intermediateValue = cameraTileX + loopIndex5;
				int intermediateValue2 = cameraTileX - loopIndex5;
				if (intermediateValue >= minTileX || intermediateValue2 < maxTileX) {
					for (int loopIndex6 = -25; loopIndex6 <= 0; loopIndex6++) {
						int intermediateValue3 = cameraTileY + loopIndex6;
						int intermediateValue4 = cameraTileY - loopIndex6;
						if (intermediateValue >= minTileX) {
							if (intermediateValue3 >= minTileY) {
								SceneTile sceneTile2 = aclass50_sub3_1[intermediateValue][intermediateValue3];
								if (sceneTile2 != null && sceneTile2.draw)
									renderTile(sceneTile2, true);
							}
							if (intermediateValue4 < maxTileY) {
								SceneTile sceneTile3 = aclass50_sub3_1[intermediateValue][intermediateValue4];
								if (sceneTile3 != null && sceneTile3.draw)
									renderTile(sceneTile3, true);
							}
						}
						if (intermediateValue2 < maxTileX) {
							if (intermediateValue3 >= minTileY) {
								SceneTile sceneTile4 = aclass50_sub3_1[intermediateValue2][intermediateValue3];
								if (sceneTile4 != null && sceneTile4.draw)
									renderTile(sceneTile4, true);
							}
							if (intermediateValue4 < maxTileY) {
								SceneTile sceneTile5 = aclass50_sub3_1[intermediateValue2][intermediateValue4];
								if (sceneTile5 != null && sceneTile5.draw)
									renderTile(sceneTile5, true);
							}
						}
						if (remainingTileCount == 0) {
							picking = false;
							return;
						}
					}

				}
			}

		}

		for (int loopIndex7 = minPlane; loopIndex7 < planeCount; loopIndex7++) {
			SceneTile aclass50_sub3_2[][] = tiles[loopIndex7];
			for (int loopIndex8 = -25; loopIndex8 <= 0; loopIndex8++) {
				int intermediateValue5 = cameraTileX + loopIndex8;
				int intermediateValue6 = cameraTileX - loopIndex8;
				if (intermediateValue5 >= minTileX || intermediateValue6 < maxTileX) {
					for (int loopIndex9 = -25; loopIndex9 <= 0; loopIndex9++) {
						int intermediateValue7 = cameraTileY + loopIndex9;
						int intermediateValue8 = cameraTileY - loopIndex9;
						if (intermediateValue5 >= minTileX) {
							if (intermediateValue7 >= minTileY) {
								SceneTile sceneTile6 = aclass50_sub3_2[intermediateValue5][intermediateValue7];
								if (sceneTile6 != null && sceneTile6.draw)
									renderTile(sceneTile6, false);
							}
							if (intermediateValue8 < maxTileY) {
								SceneTile sceneTile7 = aclass50_sub3_2[intermediateValue5][intermediateValue8];
								if (sceneTile7 != null && sceneTile7.draw)
									renderTile(sceneTile7, false);
							}
						}
						if (intermediateValue6 < maxTileX) {
							if (intermediateValue7 >= minTileY) {
								SceneTile sceneTile8 = aclass50_sub3_2[intermediateValue6][intermediateValue7];
								if (sceneTile8 != null && sceneTile8.draw)
									renderTile(sceneTile8, false);
							}
							if (intermediateValue8 < maxTileY) {
								SceneTile sceneTile9 = aclass50_sub3_2[intermediateValue6][intermediateValue8];
								if (sceneTile9 != null && sceneTile9.draw)
									renderTile(sceneTile9, false);
							}
						}
						if (remainingTileCount == 0) {
							picking = false;
							return;
						}
					}

				}
			}

		}

		picking = false;
	}

	/**
	 * Renders tile.
	 *
	 * @param sceneTile     the scene tile
	 * @param conditionFlag the condition flag
	 */
	public void renderTile(SceneTile sceneTile, boolean conditionFlag) {
		tileQueue.addLast(sceneTile);
		do {
			SceneTile sceneTile2;
			do {
				sceneTile2 = (SceneTile) tileQueue.removeFirst();
				if (sceneTile2 == null)
					return;
			} while (!sceneTile2.visible);
			int intermediateValue = sceneTile2.x;
			int intermediateValue2 = sceneTile2.y;
			int intermediateValue3 = sceneTile2.plane;
			int intermediateValue4 = sceneTile2.renderLevel;
			SceneTile aclass50_sub3[][] = tiles[intermediateValue3];
			if (sceneTile2.draw) {
				if (conditionFlag) {
					if (intermediateValue3 > 0) {
						SceneTile sceneTile3 = tiles[intermediateValue3 - 1][intermediateValue][intermediateValue2];
						if (sceneTile3 != null && sceneTile3.visible)
							continue;
					}
					if (intermediateValue <= cameraTileX && intermediateValue > minTileX) {
						SceneTile sceneTile4 = aclass50_sub3[intermediateValue - 1][intermediateValue2];
						if (sceneTile4 != null && sceneTile4.visible
								&& (sceneTile4.draw || (sceneTile2.combinedInteractiveObjectEdgeMask & 1) == 0))
							continue;
					}
					if (intermediateValue >= cameraTileX && intermediateValue < maxTileX - 1) {
						SceneTile sceneTile5 = aclass50_sub3[intermediateValue + 1][intermediateValue2];
						if (sceneTile5 != null && sceneTile5.visible
								&& (sceneTile5.draw || (sceneTile2.combinedInteractiveObjectEdgeMask & 4) == 0))
							continue;
					}
					if (intermediateValue2 <= cameraTileY && intermediateValue2 > minTileY) {
						SceneTile sceneTile6 = aclass50_sub3[intermediateValue][intermediateValue2 - 1];
						if (sceneTile6 != null && sceneTile6.visible
								&& (sceneTile6.draw || (sceneTile2.combinedInteractiveObjectEdgeMask & 8) == 0))
							continue;
					}
					if (intermediateValue2 >= cameraTileY && intermediateValue2 < maxTileY - 1) {
						SceneTile sceneTile7 = aclass50_sub3[intermediateValue][intermediateValue2 + 1];
						if (sceneTile7 != null && sceneTile7.visible
								&& (sceneTile7.draw || (sceneTile2.combinedInteractiveObjectEdgeMask & 2) == 0))
							continue;
					}
				} else {
					conditionFlag = true;
				}
				sceneTile2.draw = false;
				if (sceneTile2.tileBelow != null) {
					SceneTile sceneTile8 = sceneTile2.tileBelow;
					if (sceneTile8.plainTile != null) {
						if (!isTileOccluded(0, intermediateValue, intermediateValue2))
							renderPlainTile(sceneTile8.plainTile, 0, intermediateValue, intermediateValue2, pitchSine,
									pitchCosine, yawSine, yawCosine);
					} else if (sceneTile8.shapedTile != null
							&& !isTileOccluded(0, intermediateValue, intermediateValue2))
						renderShapedTile(sceneTile8.shapedTile, intermediateValue, intermediateValue2, pitchSine,
								pitchCosine, yawSine, yawCosine);
					Wall wall = sceneTile8.wall;
					if (wall != null)
						wall.primary.draw(0, pitchSine, pitchCosine, yawSine, yawCosine, wall.x - cameraX,
								wall.z - cameraZ, wall.y - cameraY, wall.uid);
					for (int loopIndex = 0; loopIndex < sceneTile8.interactiveObjectCount; loopIndex++) {
						InteractiveObject interactiveObject = sceneTile8.interactiveObjects[loopIndex];
						if (interactiveObject != null)
							interactiveObject.renderable.draw(interactiveObject.rotation, pitchSine, pitchCosine,
									yawSine, yawCosine, interactiveObject.worldX - cameraX,
									interactiveObject.worldZ - cameraZ, interactiveObject.worldY - cameraY,
									interactiveObject.uid);
					}

				}
				boolean conditionFlag2 = false;
				if (sceneTile2.plainTile != null) {
					if (!isTileOccluded(intermediateValue4, intermediateValue, intermediateValue2)) {
						conditionFlag2 = true;
						renderPlainTile(sceneTile2.plainTile, intermediateValue4, intermediateValue, intermediateValue2,
								pitchSine, pitchCosine, yawSine, yawCosine);
					}
				} else if (sceneTile2.shapedTile != null
						&& !isTileOccluded(intermediateValue4, intermediateValue, intermediateValue2)) {
					conditionFlag2 = true;
					renderShapedTile(sceneTile2.shapedTile, intermediateValue, intermediateValue2, pitchSine,
							pitchCosine, yawSine, yawCosine);
				}
				int intermediateValue5 = 0;
				int intermediateValue6 = 0;
				Wall wall2 = sceneTile2.wall;
				WallDecoration wallDecoration = sceneTile2.wallDecoration;
				if (wall2 != null || wallDecoration != null) {
					if (cameraTileX == intermediateValue)
						intermediateValue5++;
					else if (cameraTileX < intermediateValue)
						intermediateValue5 += 2;
					if (cameraTileY == intermediateValue2)
						intermediateValue5 += 3;
					else if (cameraTileY > intermediateValue2)
						intermediateValue5 += 6;
					intermediateValue6 = WALL_DRAW_FLAGS[intermediateValue5];
					sceneTile2.wallDrawFlags = WALL_DRAW_FLAGS_2[intermediateValue5];
				}
				if (wall2 != null) {
					if ((wall2.orientation & WALL_CULL_FLAGS[intermediateValue5]) != 0) {
						if (wall2.orientation == 16) {
							sceneTile2.wallCullDirection = 3;
							sceneTile2.wallUncullDirection = WALL_UNCULL_FLAGS_0[intermediateValue5];
							sceneTile2.wallCullOppositeDirection = 3 - sceneTile2.wallUncullDirection;
						} else if (wall2.orientation == 32) {
							sceneTile2.wallCullDirection = 6;
							sceneTile2.wallUncullDirection = WALL_UNCULL_FLAGS_1[intermediateValue5];
							sceneTile2.wallCullOppositeDirection = 6 - sceneTile2.wallUncullDirection;
						} else if (wall2.orientation == 64) {
							sceneTile2.wallCullDirection = 12;
							sceneTile2.wallUncullDirection = WALL_UNCULL_FLAGS_2[intermediateValue5];
							sceneTile2.wallCullOppositeDirection = 12 - sceneTile2.wallUncullDirection;
						} else {
							sceneTile2.wallCullDirection = 9;
							sceneTile2.wallUncullDirection = WALL_UNCULL_FLAGS_3[intermediateValue5];
							sceneTile2.wallCullOppositeDirection = 9 - sceneTile2.wallUncullDirection;
						}
					} else {
						sceneTile2.wallCullDirection = 0;
					}
					if ((wall2.orientation & intermediateValue6) != 0 && !isWallOccluded(intermediateValue4,
							intermediateValue, intermediateValue2, wall2.orientation))
						wall2.primary.draw(0, pitchSine, pitchCosine, yawSine, yawCosine, wall2.x - cameraX,
								wall2.z - cameraZ, wall2.y - cameraY, wall2.uid);
					if ((wall2.secondaryOrientation & intermediateValue6) != 0 && !isWallOccluded(intermediateValue4,
							intermediateValue, intermediateValue2, wall2.secondaryOrientation))
						wall2.secondary.draw(0, pitchSine, pitchCosine, yawSine, yawCosine, wall2.x - cameraX,
								wall2.z - cameraZ, wall2.y - cameraY, wall2.uid);
				}
				if (wallDecoration != null && !isDecorationOccluded(intermediateValue4, intermediateValue,
						intermediateValue2, wallDecoration.renderable.modelHeight))
					if ((wallDecoration.configBits & intermediateValue6) != 0)
						wallDecoration.renderable.draw(wallDecoration.face, pitchSine, pitchCosine, yawSine, yawCosine,
								wallDecoration.x - cameraX, wallDecoration.z - cameraZ, wallDecoration.y - cameraY,
								wallDecoration.uid);
					else if ((wallDecoration.configBits & 0x300) != 0) {
						int intermediateValue7 = wallDecoration.x - cameraX;
						int intermediateValue8 = wallDecoration.z - cameraZ;
						int intermediateValue9 = wallDecoration.y - cameraY;
						int intermediateValue10 = wallDecoration.face;
						int intermediateValue11;
						if (intermediateValue10 == 1 || intermediateValue10 == 2)
							intermediateValue11 = -intermediateValue7;
						else
							intermediateValue11 = intermediateValue7;
						int intermediateValue12;
						if (intermediateValue10 == 2 || intermediateValue10 == 3)
							intermediateValue12 = -intermediateValue9;
						else
							intermediateValue12 = intermediateValue9;
						if ((wallDecoration.configBits & 0x100) != 0 && intermediateValue12 < intermediateValue11) {
							int intermediateValue13 = intermediateValue7 + WALL_DECORATION_INSET_X[intermediateValue10];
							int intermediateValue14 = intermediateValue9 + WALL_DECORATION_INSET_Y[intermediateValue10];
							wallDecoration.renderable.draw(intermediateValue10 * Angle.QUARTER_TURN + Angle.EIGHTH_TURN,
									pitchSine, pitchCosine, yawSine, yawCosine, intermediateValue13, intermediateValue8,
									intermediateValue14, wallDecoration.uid);
						}
						if ((wallDecoration.configBits & 0x200) != 0 && intermediateValue12 > intermediateValue11) {
							int intermediateValue15 = intermediateValue7
									+ WALL_DECORATION_OUTSET_X[intermediateValue10];
							int intermediateValue16 = intermediateValue9
									+ WALL_DECORATION_OUTSET_Y[intermediateValue10];
							wallDecoration.renderable.draw(
									intermediateValue10 * Angle.QUARTER_TURN + Angle.FIVE_EIGHTHS_TURN & Angle.MASK,
									pitchSine, pitchCosine, yawSine, yawCosine, intermediateValue15, intermediateValue8,
									intermediateValue16, wallDecoration.uid);
						}
					}
				if (conditionFlag2) {
					FloorDecoration floorDecoration = sceneTile2.floorDecoration;
					if (floorDecoration != null)
						floorDecoration.renderable.draw(0, pitchSine, pitchCosine, yawSine, yawCosine,
								floorDecoration.x - cameraX, floorDecoration.z - cameraZ, floorDecoration.y - cameraY,
								floorDecoration.uid);
					GroundItemTile groundItemTile = sceneTile2.groundItemTile;
					if (groundItemTile != null && groundItemTile.heightOffset == 0) {
						if (groundItemTile.secondGroundItem != null)
							groundItemTile.secondGroundItem.draw(0, pitchSine, pitchCosine, yawSine, yawCosine,
									groundItemTile.x - cameraX, groundItemTile.z - cameraZ, groundItemTile.y - cameraY,
									groundItemTile.uid);
						if (groundItemTile.thirdGroundItem != null)
							groundItemTile.thirdGroundItem.draw(0, pitchSine, pitchCosine, yawSine, yawCosine,
									groundItemTile.x - cameraX, groundItemTile.z - cameraZ, groundItemTile.y - cameraY,
									groundItemTile.uid);
						if (groundItemTile.firstGroundItem != null)
							groundItemTile.firstGroundItem.draw(0, pitchSine, pitchCosine, yawSine, yawCosine,
									groundItemTile.x - cameraX, groundItemTile.z - cameraZ, groundItemTile.y - cameraY,
									groundItemTile.uid);
					}
				}
				int intermediateValue17 = sceneTile2.combinedInteractiveObjectEdgeMask;
				if (intermediateValue17 != 0) {
					if (intermediateValue < cameraTileX && (intermediateValue17 & 4) != 0) {
						SceneTile sceneTile9 = aclass50_sub3[intermediateValue + 1][intermediateValue2];
						if (sceneTile9 != null && sceneTile9.visible)
							tileQueue.addLast(sceneTile9);
					}
					if (intermediateValue2 < cameraTileY && (intermediateValue17 & 2) != 0) {
						SceneTile sceneTile10 = aclass50_sub3[intermediateValue][intermediateValue2 + 1];
						if (sceneTile10 != null && sceneTile10.visible)
							tileQueue.addLast(sceneTile10);
					}
					if (intermediateValue > cameraTileX && (intermediateValue17 & 1) != 0) {
						SceneTile sceneTile11 = aclass50_sub3[intermediateValue - 1][intermediateValue2];
						if (sceneTile11 != null && sceneTile11.visible)
							tileQueue.addLast(sceneTile11);
					}
					if (intermediateValue2 > cameraTileY && (intermediateValue17 & 8) != 0) {
						SceneTile sceneTile12 = aclass50_sub3[intermediateValue][intermediateValue2 - 1];
						if (sceneTile12 != null && sceneTile12.visible)
							tileQueue.addLast(sceneTile12);
					}
				}
			}
			if (sceneTile2.wallCullDirection != 0) {
				boolean conditionFlag3 = true;
				for (int loopIndex2 = 0; loopIndex2 < sceneTile2.interactiveObjectCount; loopIndex2++) {
					if (sceneTile2.interactiveObjects[loopIndex2].lastDrawnCycle == renderCycle
							|| (sceneTile2.interactiveObjectEdgeMasks[loopIndex2]
									& sceneTile2.wallCullDirection) != sceneTile2.wallUncullDirection)
						continue;
					conditionFlag3 = false;
					break;
				}

				if (conditionFlag3) {
					Wall wall3 = sceneTile2.wall;
					if (!isWallOccluded(intermediateValue4, intermediateValue, intermediateValue2, wall3.orientation))
						wall3.primary.draw(0, pitchSine, pitchCosine, yawSine, yawCosine, wall3.x - cameraX,
								wall3.z - cameraZ, wall3.y - cameraY, wall3.uid);
					sceneTile2.wallCullDirection = 0;
				}
			}
			if (sceneTile2.drawEntities)
				try {
					int intermediateValue18 = sceneTile2.interactiveObjectCount;
					sceneTile2.drawEntities = false;
					int intermediateValue19 = 0;
					label0: for (int loopIndex3 = 0; loopIndex3 < intermediateValue18; loopIndex3++) {
						InteractiveObject interactiveObject2 = sceneTile2.interactiveObjects[loopIndex3];
						if (interactiveObject2.lastDrawnCycle == renderCycle)
							continue;
						for (int loopIndex4 = interactiveObject2.tileLeft; loopIndex4 <= interactiveObject2.tileRight; loopIndex4++) {
							for (int loopIndex5 = interactiveObject2.tileTop; loopIndex5 <= interactiveObject2.tileBottom; loopIndex5++) {
								SceneTile sceneTile13 = aclass50_sub3[loopIndex4][loopIndex5];
								if (sceneTile13.draw) {
									sceneTile2.drawEntities = true;
								} else {
									if (sceneTile13.wallCullDirection == 0)
										continue;
									int intermediateValue20 = 0;
									if (loopIndex4 > interactiveObject2.tileLeft)
										intermediateValue20++;
									if (loopIndex4 < interactiveObject2.tileRight)
										intermediateValue20 += 4;
									if (loopIndex5 > interactiveObject2.tileTop)
										intermediateValue20 += 8;
									if (loopIndex5 < interactiveObject2.tileBottom)
										intermediateValue20 += 2;
									if ((intermediateValue20
											& sceneTile13.wallCullDirection) != sceneTile2.wallCullOppositeDirection)
										continue;
									sceneTile2.drawEntities = true;
								}
								continue label0;
							}

						}

						renderInteractiveObjects[intermediateValue19++] = interactiveObject2;
						int intermediateValue21 = cameraTileX - interactiveObject2.tileLeft;
						int intermediateValue22 = interactiveObject2.tileRight - cameraTileX;
						if (intermediateValue22 > intermediateValue21)
							intermediateValue21 = intermediateValue22;
						int intermediateValue23 = cameraTileY - interactiveObject2.tileTop;
						int intermediateValue24 = interactiveObject2.tileBottom - cameraTileY;
						if (intermediateValue24 > intermediateValue23)
							interactiveObject2.drawPriority = intermediateValue21 + intermediateValue24;
						else
							interactiveObject2.drawPriority = intermediateValue21 + intermediateValue23;
					}

					while (intermediateValue19 > 0) {
						int intermediateValue25 = -50;
						int intermediateValue26 = -1;
						for (int loopIndex6 = 0; loopIndex6 < intermediateValue19; loopIndex6++) {
							InteractiveObject interactiveObject3 = renderInteractiveObjects[loopIndex6];
							if (interactiveObject3.lastDrawnCycle != renderCycle)
								if (interactiveObject3.drawPriority > intermediateValue25) {
									intermediateValue25 = interactiveObject3.drawPriority;
									intermediateValue26 = loopIndex6;
								} else if (interactiveObject3.drawPriority == intermediateValue25) {
									int intermediateValue27 = interactiveObject3.worldX - cameraX;
									int intermediateValue28 = interactiveObject3.worldY - cameraY;
									int intermediateValue29 = renderInteractiveObjects[intermediateValue26].worldX
											- cameraX;
									int intermediateValue30 = renderInteractiveObjects[intermediateValue26].worldY
											- cameraY;
									if (intermediateValue27 * intermediateValue27 + intermediateValue28
											* intermediateValue28 > intermediateValue29 * intermediateValue29
													+ intermediateValue30 * intermediateValue30)
										intermediateValue26 = loopIndex6;
								}
						}

						if (intermediateValue26 == -1)
							break;
						InteractiveObject interactiveObject4 = renderInteractiveObjects[intermediateValue26];
						interactiveObject4.lastDrawnCycle = renderCycle;
						if (!isAreaOccluded(intermediateValue4, interactiveObject4.tileLeft,
								interactiveObject4.tileRight, interactiveObject4.tileTop, interactiveObject4.tileBottom,
								interactiveObject4.renderable.modelHeight))
							interactiveObject4.renderable.draw(interactiveObject4.rotation, pitchSine, pitchCosine,
									yawSine, yawCosine, interactiveObject4.worldX - cameraX,
									interactiveObject4.worldZ - cameraZ, interactiveObject4.worldY - cameraY,
									interactiveObject4.uid);
						for (int loopIndex7 = interactiveObject4.tileLeft; loopIndex7 <= interactiveObject4.tileRight; loopIndex7++) {
							for (int loopIndex8 = interactiveObject4.tileTop; loopIndex8 <= interactiveObject4.tileBottom; loopIndex8++) {
								SceneTile sceneTile14 = aclass50_sub3[loopIndex7][loopIndex8];
								if (sceneTile14.wallCullDirection != 0)
									tileQueue.addLast(sceneTile14);
								else if ((loopIndex7 != intermediateValue || loopIndex8 != intermediateValue2)
										&& sceneTile14.visible)
									tileQueue.addLast(sceneTile14);
							}

						}

					}
					if (sceneTile2.drawEntities)
						continue;
				} catch (Exception _ex) {
					sceneTile2.drawEntities = false;
				}
			if (!sceneTile2.visible || sceneTile2.wallCullDirection != 0)
				continue;
			if (intermediateValue <= cameraTileX && intermediateValue > minTileX) {
				SceneTile sceneTile15 = aclass50_sub3[intermediateValue - 1][intermediateValue2];
				if (sceneTile15 != null && sceneTile15.visible)
					continue;
			}
			if (intermediateValue >= cameraTileX && intermediateValue < maxTileX - 1) {
				SceneTile sceneTile16 = aclass50_sub3[intermediateValue + 1][intermediateValue2];
				if (sceneTile16 != null && sceneTile16.visible)
					continue;
			}
			if (intermediateValue2 <= cameraTileY && intermediateValue2 > minTileY) {
				SceneTile sceneTile17 = aclass50_sub3[intermediateValue][intermediateValue2 - 1];
				if (sceneTile17 != null && sceneTile17.visible)
					continue;
			}
			if (intermediateValue2 >= cameraTileY && intermediateValue2 < maxTileY - 1) {
				SceneTile sceneTile18 = aclass50_sub3[intermediateValue][intermediateValue2 + 1];
				if (sceneTile18 != null && sceneTile18.visible)
					continue;
			}
			sceneTile2.visible = false;
			remainingTileCount--;
			GroundItemTile groundItemTile2 = sceneTile2.groundItemTile;
			if (groundItemTile2 != null && groundItemTile2.heightOffset != 0) {
				if (groundItemTile2.secondGroundItem != null)
					groundItemTile2.secondGroundItem.draw(0, pitchSine, pitchCosine, yawSine, yawCosine,
							groundItemTile2.x - cameraX, groundItemTile2.z - cameraZ - groundItemTile2.heightOffset,
							groundItemTile2.y - cameraY, groundItemTile2.uid);
				if (groundItemTile2.thirdGroundItem != null)
					groundItemTile2.thirdGroundItem.draw(0, pitchSine, pitchCosine, yawSine, yawCosine,
							groundItemTile2.x - cameraX, groundItemTile2.z - cameraZ - groundItemTile2.heightOffset,
							groundItemTile2.y - cameraY, groundItemTile2.uid);
				if (groundItemTile2.firstGroundItem != null)
					groundItemTile2.firstGroundItem.draw(0, pitchSine, pitchCosine, yawSine, yawCosine,
							groundItemTile2.x - cameraX, groundItemTile2.z - cameraZ - groundItemTile2.heightOffset,
							groundItemTile2.y - cameraY, groundItemTile2.uid);
			}
			if (sceneTile2.wallDrawFlags != 0) {
				WallDecoration wallDecoration2 = sceneTile2.wallDecoration;
				if (wallDecoration2 != null && !isDecorationOccluded(intermediateValue4, intermediateValue,
						intermediateValue2, wallDecoration2.renderable.modelHeight))
					if ((wallDecoration2.configBits & sceneTile2.wallDrawFlags) != 0)
						wallDecoration2.renderable.draw(wallDecoration2.face, pitchSine, pitchCosine, yawSine,
								yawCosine, wallDecoration2.x - cameraX, wallDecoration2.z - cameraZ,
								wallDecoration2.y - cameraY, wallDecoration2.uid);
					else if ((wallDecoration2.configBits & 0x300) != 0) {
						int intermediateValue31 = wallDecoration2.x - cameraX;
						int intermediateValue32 = wallDecoration2.z - cameraZ;
						int intermediateValue33 = wallDecoration2.y - cameraY;
						int intermediateValue34 = wallDecoration2.face;
						int intermediateValue35;
						if (intermediateValue34 == 1 || intermediateValue34 == 2)
							intermediateValue35 = -intermediateValue31;
						else
							intermediateValue35 = intermediateValue31;
						int intermediateValue36;
						if (intermediateValue34 == 2 || intermediateValue34 == 3)
							intermediateValue36 = -intermediateValue33;
						else
							intermediateValue36 = intermediateValue33;
						if ((wallDecoration2.configBits & 0x100) != 0 && intermediateValue36 >= intermediateValue35) {
							int intermediateValue37 = intermediateValue31
									+ WALL_DECORATION_INSET_X[intermediateValue34];
							int intermediateValue38 = intermediateValue33
									+ WALL_DECORATION_INSET_Y[intermediateValue34];
							wallDecoration2.renderable.draw(
									intermediateValue34 * Angle.QUARTER_TURN + Angle.EIGHTH_TURN, pitchSine,
									pitchCosine, yawSine, yawCosine, intermediateValue37, intermediateValue32,
									intermediateValue38, wallDecoration2.uid);
						}
						if ((wallDecoration2.configBits & 0x200) != 0 && intermediateValue36 <= intermediateValue35) {
							int intermediateValue39 = intermediateValue31
									+ WALL_DECORATION_OUTSET_X[intermediateValue34];
							int intermediateValue40 = intermediateValue33
									+ WALL_DECORATION_OUTSET_Y[intermediateValue34];
							wallDecoration2.renderable.draw(
									intermediateValue34 * Angle.QUARTER_TURN + Angle.FIVE_EIGHTHS_TURN & Angle.MASK,
									pitchSine, pitchCosine, yawSine, yawCosine, intermediateValue39,
									intermediateValue32, intermediateValue40, wallDecoration2.uid);
						}
					}
				Wall wall4 = sceneTile2.wall;
				if (wall4 != null) {
					if ((wall4.secondaryOrientation & sceneTile2.wallDrawFlags) != 0
							&& !isWallOccluded(intermediateValue4, intermediateValue, intermediateValue2,
									wall4.secondaryOrientation))
						wall4.secondary.draw(0, pitchSine, pitchCosine, yawSine, yawCosine, wall4.x - cameraX,
								wall4.z - cameraZ, wall4.y - cameraY, wall4.uid);
					if ((wall4.orientation & sceneTile2.wallDrawFlags) != 0 && !isWallOccluded(intermediateValue4,
							intermediateValue, intermediateValue2, wall4.orientation))
						wall4.primary.draw(0, pitchSine, pitchCosine, yawSine, yawCosine, wall4.x - cameraX,
								wall4.z - cameraZ, wall4.y - cameraY, wall4.uid);
				}
			}
			if (intermediateValue3 < planeCount - 1) {
				SceneTile sceneTile19 = tiles[intermediateValue3 + 1][intermediateValue][intermediateValue2];
				if (sceneTile19 != null && sceneTile19.visible)
					tileQueue.addLast(sceneTile19);
			}
			if (intermediateValue < cameraTileX) {
				SceneTile sceneTile20 = aclass50_sub3[intermediateValue + 1][intermediateValue2];
				if (sceneTile20 != null && sceneTile20.visible)
					tileQueue.addLast(sceneTile20);
			}
			if (intermediateValue2 < cameraTileY) {
				SceneTile sceneTile21 = aclass50_sub3[intermediateValue][intermediateValue2 + 1];
				if (sceneTile21 != null && sceneTile21.visible)
					tileQueue.addLast(sceneTile21);
			}
			if (intermediateValue > cameraTileX) {
				SceneTile sceneTile22 = aclass50_sub3[intermediateValue - 1][intermediateValue2];
				if (sceneTile22 != null && sceneTile22.visible)
					tileQueue.addLast(sceneTile22);
			}
			if (intermediateValue2 > cameraTileY) {
				SceneTile sceneTile23 = aclass50_sub3[intermediateValue][intermediateValue2 - 1];
				if (sceneTile23 != null && sceneTile23.visible)
					tileQueue.addLast(sceneTile23);
			}
		} while (true);
	}

	/**
	 * Renders plain tile.
	 *
	 * @param tile        the tile
	 * @param plane       the plane
	 * @param tileX       the tile x
	 * @param tileY       the tile y
	 * @param pitchSine   the pitch sine
	 * @param pitchCosine the pitch cosine
	 * @param yawSine     the yaw sine
	 * @param yawCosine   the yaw cosine
	 */
	public void renderPlainTile(GenericTile tile, int plane, int tileX, int tileY, int pitchSine, int pitchCosine,
			int yawSine, int yawCosine) {
		int northWestViewX;
		int southWestViewX = northWestViewX = (tileX << 7) - cameraX;
		int southEastDepth;
		int southWestDepth = southEastDepth = (tileY << 7) - cameraY;
		int northEastViewX;
		int southEastViewX = northEastViewX = southWestViewX + 128;
		int northWestDepth;
		int northEastDepth = northWestDepth = southWestDepth + 128;
		int southWestViewY = tileHeights[plane][tileX][tileY] - cameraZ;
		int southEastViewY = tileHeights[plane][tileX + 1][tileY] - cameraZ;
		int northEastViewY = tileHeights[plane][tileX + 1][tileY + 1] - cameraZ;
		int northWestViewY = tileHeights[plane][tileX][tileY + 1] - cameraZ;
		int rotated = southWestDepth * yawSine + southWestViewX * yawCosine >> 16;
		southWestDepth = southWestDepth * yawCosine - southWestViewX * yawSine >> 16;
		southWestViewX = rotated;
		rotated = southWestViewY * pitchCosine - southWestDepth * pitchSine >> 16;
		southWestDepth = southWestViewY * pitchSine + southWestDepth * pitchCosine >> 16;
		southWestViewY = rotated;
		if (southWestDepth < 50)
			return;
		rotated = southEastDepth * yawSine + southEastViewX * yawCosine >> 16;
		southEastDepth = southEastDepth * yawCosine - southEastViewX * yawSine >> 16;
		southEastViewX = rotated;
		rotated = southEastViewY * pitchCosine - southEastDepth * pitchSine >> 16;
		southEastDepth = southEastViewY * pitchSine + southEastDepth * pitchCosine >> 16;
		southEastViewY = rotated;
		if (southEastDepth < 50)
			return;
		rotated = northEastDepth * yawSine + northEastViewX * yawCosine >> 16;
		northEastDepth = northEastDepth * yawCosine - northEastViewX * yawSine >> 16;
		northEastViewX = rotated;
		rotated = northEastViewY * pitchCosine - northEastDepth * pitchSine >> 16;
		northEastDepth = northEastViewY * pitchSine + northEastDepth * pitchCosine >> 16;
		northEastViewY = rotated;
		if (northEastDepth < 50)
			return;
		rotated = northWestDepth * yawSine + northWestViewX * yawCosine >> 16;
		northWestDepth = northWestDepth * yawCosine - northWestViewX * yawSine >> 16;
		northWestViewX = rotated;
		rotated = northWestViewY * pitchCosine - northWestDepth * pitchSine >> 16;
		northWestDepth = northWestViewY * pitchSine + northWestDepth * pitchCosine >> 16;
		northWestViewY = rotated;
		if (northWestDepth < 50)
			return;
		int southWestScreenX = Rasterizer3D.centerX + (southWestViewX << 9) / southWestDepth;
		int southWestScreenY = Rasterizer3D.centerY + (southWestViewY << 9) / southWestDepth;
		int southEastScreenX = Rasterizer3D.centerX + (southEastViewX << 9) / southEastDepth;
		int southEastScreenY = Rasterizer3D.centerY + (southEastViewY << 9) / southEastDepth;
		int northEastScreenX = Rasterizer3D.centerX + (northEastViewX << 9) / northEastDepth;
		int northEastScreenY = Rasterizer3D.centerY + (northEastViewY << 9) / northEastDepth;
		int northWestScreenX = Rasterizer3D.centerX + (northWestViewX << 9) / northWestDepth;
		int northWestScreenY = Rasterizer3D.centerY + (northWestViewY << 9) / northWestDepth;
		Rasterizer3D.alpha = 0;
		if ((northEastScreenX - northWestScreenX) * (southEastScreenY - northWestScreenY)
				- (northEastScreenY - northWestScreenY) * (southEastScreenX - northWestScreenX) > 0) {
			Rasterizer3D.restrictEdges = false;
			if (northEastScreenX < 0 || northWestScreenX < 0 || southEastScreenX < 0
					|| northEastScreenX > Rasterizer.viewportRx || northWestScreenX > Rasterizer.viewportRx
					|| southEastScreenX > Rasterizer.viewportRx)
				Rasterizer3D.restrictEdges = true;
			if (picking && containsScreenPoint(mouseX, mouseY, northEastScreenY, northWestScreenY, southEastScreenY,
					northEastScreenX, northWestScreenX, southEastScreenX)) {
				pickedTileX = tileX;
				pickedTileY = tileY;
			}
			if (tile.texture == -1) {
				if (tile.colourC != 0xbc614e)
					Rasterizer3D.drawGouraudTriangle(northEastScreenY, northWestScreenY, southEastScreenY,
							northEastScreenX, northWestScreenX, southEastScreenX, tile.colourC, tile.colourD,
							tile.colourB);
			} else if (!lowMemory) {
				if (tile.flat)
					Rasterizer3D.drawTexturedTriangle(northEastScreenY, northWestScreenY, southEastScreenY,
							northEastScreenX, northWestScreenX, southEastScreenX, tile.colourC, tile.colourD,
							tile.colourB, southWestViewX, southEastViewX, northWestViewX, southWestViewY,
							southEastViewY, northWestViewY, southWestDepth, southEastDepth, northWestDepth,
							tile.texture);
				else
					Rasterizer3D.drawTexturedTriangle(northEastScreenY, northWestScreenY, southEastScreenY,
							northEastScreenX, northWestScreenX, southEastScreenX, tile.colourC, tile.colourD,
							tile.colourB, northEastViewX, northWestViewX, southEastViewX, northEastViewY,
							northWestViewY, southEastViewY, northEastDepth, northWestDepth, southEastDepth,
							tile.texture);
			} else {
				int textureColor = TEXTURE_COLORS[tile.texture];
				Rasterizer3D.drawGouraudTriangle(northEastScreenY, northWestScreenY, southEastScreenY, northEastScreenX,
						northWestScreenX, southEastScreenX, mixTextureColor(tile.colourC, textureColor),
						mixTextureColor(tile.colourD, textureColor), mixTextureColor(tile.colourB, textureColor));
			}
		}
		if ((southWestScreenX - southEastScreenX) * (northWestScreenY - southEastScreenY)
				- (southWestScreenY - southEastScreenY) * (northWestScreenX - southEastScreenX) > 0) {
			Rasterizer3D.restrictEdges = false;
			if (southWestScreenX < 0 || southEastScreenX < 0 || northWestScreenX < 0
					|| southWestScreenX > Rasterizer.viewportRx || southEastScreenX > Rasterizer.viewportRx
					|| northWestScreenX > Rasterizer.viewportRx)
				Rasterizer3D.restrictEdges = true;
			if (picking && containsScreenPoint(mouseX, mouseY, southWestScreenY, southEastScreenY, northWestScreenY,
					southWestScreenX, southEastScreenX, northWestScreenX)) {
				pickedTileX = tileX;
				pickedTileY = tileY;
			}
			if (tile.texture == -1) {
				if (tile.colourA != 0xbc614e) {
					Rasterizer3D.drawGouraudTriangle(southWestScreenY, southEastScreenY, northWestScreenY,
							southWestScreenX, southEastScreenX, northWestScreenX, tile.colourA, tile.colourB,
							tile.colourD);
					return;
				}
			} else {
				if (!lowMemory) {
					Rasterizer3D.drawTexturedTriangle(southWestScreenY, southEastScreenY, northWestScreenY,
							southWestScreenX, southEastScreenX, northWestScreenX, tile.colourA, tile.colourB,
							tile.colourD, southWestViewX, southEastViewX, northWestViewX, southWestViewY,
							southEastViewY, northWestViewY, southWestDepth, southEastDepth, northWestDepth,
							tile.texture);
					return;
				}
				int textureColor = TEXTURE_COLORS[tile.texture];
				Rasterizer3D.drawGouraudTriangle(southWestScreenY, southEastScreenY, northWestScreenY, southWestScreenX,
						southEastScreenX, northWestScreenX, mixTextureColor(tile.colourA, textureColor),
						mixTextureColor(tile.colourB, textureColor), mixTextureColor(tile.colourD, textureColor));
			}
		}
	}

	/**
	 * Renders shaped tile.
	 *
	 * @param tile        the tile
	 * @param tileX       the tile x
	 * @param tileY       the tile y
	 * @param pitchSine   the pitch sine
	 * @param pitchCosine the pitch cosine
	 * @param yawSine     the yaw sine
	 * @param yawCosine   the yaw cosine
	 */
	public void renderShapedTile(ComplexTile tile, int tileX, int tileY, int pitchSine, int pitchCosine, int yawSine,
			int yawCosine) {
		int elementCount = tile.vertexX.length;
		for (int vertex = 0; vertex < elementCount; vertex++) {
			int viewX = tile.vertexX[vertex] - cameraX;
			int viewY = tile.vertexY[vertex] - cameraZ;
			int viewDepth = tile.vertexZ[vertex] - cameraY;
			int rotated = viewDepth * yawSine + viewX * yawCosine >> 16;
			viewDepth = viewDepth * yawCosine - viewX * yawSine >> 16;
			viewX = rotated;
			rotated = viewY * pitchCosine - viewDepth * pitchSine >> 16;
			viewDepth = viewY * pitchSine + viewDepth * pitchCosine >> 16;
			viewY = rotated;
			if (viewDepth < 50)
				return;
			if (tile.triangleTextures != null) {
				ComplexTile.VIEW_X[vertex] = viewX;
				ComplexTile.VIEW_Y[vertex] = viewY;
				ComplexTile.VIEW_Z[vertex] = viewDepth;
			}
			ComplexTile.SCREEN_X[vertex] = Rasterizer3D.centerX + (viewX << 9) / viewDepth;
			ComplexTile.SCREEN_Y[vertex] = Rasterizer3D.centerY + (viewY << 9) / viewDepth;
		}

		Rasterizer3D.alpha = 0;
		elementCount = tile.triangleVertexA.length;
		for (int triangle = 0; triangle < elementCount; triangle++) {
			int vertexA = tile.triangleVertexA[triangle];
			int vertexB = tile.triangleVertexB[triangle];
			int vertexC = tile.triangleVertexC[triangle];
			int screenXA = ComplexTile.SCREEN_X[vertexA];
			int screenXB = ComplexTile.SCREEN_X[vertexB];
			int screenXC = ComplexTile.SCREEN_X[vertexC];
			int screenYA = ComplexTile.SCREEN_Y[vertexA];
			int screenYB = ComplexTile.SCREEN_Y[vertexB];
			int screenYC = ComplexTile.SCREEN_Y[vertexC];
			if ((screenXA - screenXB) * (screenYC - screenYB) - (screenYA - screenYB) * (screenXC - screenXB) > 0) {
				Rasterizer3D.restrictEdges = false;
				if (screenXA < 0 || screenXB < 0 || screenXC < 0 || screenXA > Rasterizer.viewportRx
						|| screenXB > Rasterizer.viewportRx || screenXC > Rasterizer.viewportRx)
					Rasterizer3D.restrictEdges = true;
				if (picking && containsScreenPoint(mouseX, mouseY, screenYA, screenYB, screenYC, screenXA, screenXB,
						screenXC)) {
					pickedTileX = tileX;
					pickedTileY = tileY;
				}
				if (tile.triangleTextures == null || tile.triangleTextures[triangle] == -1) {
					if (tile.triangleHslA[triangle] != 0xbc614e)
						Rasterizer3D.drawGouraudTriangle(screenYA, screenYB, screenYC, screenXA, screenXB, screenXC,
								tile.triangleHslA[triangle], tile.triangleHslB[triangle], tile.triangleHslC[triangle]);
				} else if (!lowMemory) {
					if (tile.flat)
						Rasterizer3D.drawTexturedTriangle(screenYA, screenYB, screenYC, screenXA, screenXB, screenXC,
								tile.triangleHslA[triangle], tile.triangleHslB[triangle], tile.triangleHslC[triangle],
								ComplexTile.VIEW_X[0], ComplexTile.VIEW_X[1], ComplexTile.VIEW_X[3],
								ComplexTile.VIEW_Y[0], ComplexTile.VIEW_Y[1], ComplexTile.VIEW_Y[3],
								ComplexTile.VIEW_Z[0], ComplexTile.VIEW_Z[1], ComplexTile.VIEW_Z[3],
								tile.triangleTextures[triangle]);
					else
						Rasterizer3D.drawTexturedTriangle(screenYA, screenYB, screenYC, screenXA, screenXB, screenXC,
								tile.triangleHslA[triangle], tile.triangleHslB[triangle], tile.triangleHslC[triangle],
								ComplexTile.VIEW_X[vertexA], ComplexTile.VIEW_X[vertexB], ComplexTile.VIEW_X[vertexC],
								ComplexTile.VIEW_Y[vertexA], ComplexTile.VIEW_Y[vertexB], ComplexTile.VIEW_Y[vertexC],
								ComplexTile.VIEW_Z[vertexA], ComplexTile.VIEW_Z[vertexB], ComplexTile.VIEW_Z[vertexC],
								tile.triangleTextures[triangle]);
				} else {
					int textureColor = TEXTURE_COLORS[tile.triangleTextures[triangle]];
					Rasterizer3D.drawGouraudTriangle(screenYA, screenYB, screenYC, screenXA, screenXB, screenXC,
							mixTextureColor(tile.triangleHslA[triangle], textureColor),
							mixTextureColor(tile.triangleHslB[triangle], textureColor),
							mixTextureColor(tile.triangleHslC[triangle], textureColor));
				}
			}
		}

	}

	/**
	 * Performs mix texture color.
	 *
	 * @return the resulting int
	 * @param lightness the lightness
	 * @param baseColor the base color
	 */
	public int mixTextureColor(int lightness, int baseColor) {
		lightness = 127 - lightness;
		lightness = (lightness * (baseColor & 0x7f)) / 160;
		if (lightness < 2)
			lightness = 2;
		else if (lightness > 126)
			lightness = 126;
		return (baseColor & 0xff80) + lightness;
	}

	/**
	 * Performs contains screen point.
	 *
	 * @return {@code true} when contains screen point; otherwise {@code false}
	 * @param pointX the point x
	 * @param pointY the point y
	 * @param yA     the y a
	 * @param yB     the y b
	 * @param yC     the y c
	 * @param xA     the x a
	 * @param xB     the x b
	 * @param xC     the x c
	 */
	public boolean containsScreenPoint(int pointX, int pointY, int yA, int yB, int yC, int xA, int xB, int xC) {
		if (pointY < yA && pointY < yB && pointY < yC)
			return false;
		if (pointY > yA && pointY > yB && pointY > yC)
			return false;
		if (pointX < xA && pointX < xB && pointX < xC)
			return false;
		if (pointX > xA && pointX > xB && pointX > xC)
			return false;
		int intermediateValue = (pointY - yA) * (xB - xA) - (pointX - xA) * (yB - yA);
		int intermediateValue2 = (pointY - yC) * (xA - xC) - (pointX - xC) * (yA - yC);
		int intermediateValue3 = (pointY - yB) * (xC - xB) - (pointX - xB) * (yC - yB);
		return intermediateValue * intermediateValue3 > 0 && intermediateValue3 * intermediateValue2 > 0;
	}

	/**
	 * Processes occluders.
	 */
	public void processOccluders() {
		int intermediateValue = occluderCounts[renderPlane];
		SceneCluster aclass39[] = occluders[renderPlane];
		activeOccluderCount = 0;
		for (int loopIndex = 0; loopIndex < intermediateValue; loopIndex++) {
			SceneCluster sceneCluster = aclass39[loopIndex];
			if (sceneCluster.type == SceneCluster.TYPE_X_PLANE) {
				int intermediateValue2 = (sceneCluster.minTileX - cameraTileX) + 25;
				if (intermediateValue2 < 0 || intermediateValue2 > 50)
					continue;
				int intermediateValue3 = (sceneCluster.minTileY - cameraTileY) + 25;
				if (intermediateValue3 < 0)
					intermediateValue3 = 0;
				int intermediateValue4 = (sceneCluster.maxTileY - cameraTileY) + 25;
				if (intermediateValue4 > 50)
					intermediateValue4 = 50;
				boolean conditionFlag = false;
				while (intermediateValue3 <= intermediateValue4)
					if (visibilityMap[intermediateValue2][intermediateValue3++]) {
						conditionFlag = true;
						break;
					}
				if (!conditionFlag)
					continue;
				int intermediateValue5 = cameraX - sceneCluster.minWorldX;
				if (intermediateValue5 > 32) {
					sceneCluster.projectionDirection = SceneCluster.PROJECT_POSITIVE_X;
				} else {
					if (intermediateValue5 >= -32)
						continue;
					sceneCluster.projectionDirection = SceneCluster.PROJECT_NEGATIVE_X;
					intermediateValue5 = -intermediateValue5;
				}
				sceneCluster.minYGradient = (sceneCluster.minWorldY - cameraY << 8) / intermediateValue5;
				sceneCluster.maxYGradient = (sceneCluster.maxWorldY - cameraY << 8) / intermediateValue5;
				sceneCluster.minZGradient = (sceneCluster.minWorldZ - cameraZ << 8) / intermediateValue5;
				sceneCluster.maxZGradient = (sceneCluster.maxWorldZ - cameraZ << 8) / intermediateValue5;
				activeOccluders[activeOccluderCount++] = sceneCluster;
				continue;
			}
			if (sceneCluster.type == SceneCluster.TYPE_Y_PLANE) {
				int intermediateValue6 = (sceneCluster.minTileY - cameraTileY) + 25;
				if (intermediateValue6 < 0 || intermediateValue6 > 50)
					continue;
				int intermediateValue7 = (sceneCluster.minTileX - cameraTileX) + 25;
				if (intermediateValue7 < 0)
					intermediateValue7 = 0;
				int intermediateValue8 = (sceneCluster.maxTileX - cameraTileX) + 25;
				if (intermediateValue8 > 50)
					intermediateValue8 = 50;
				boolean conditionFlag2 = false;
				while (intermediateValue7 <= intermediateValue8)
					if (visibilityMap[intermediateValue7++][intermediateValue6]) {
						conditionFlag2 = true;
						break;
					}
				if (!conditionFlag2)
					continue;
				int intermediateValue9 = cameraY - sceneCluster.minWorldY;
				if (intermediateValue9 > 32) {
					sceneCluster.projectionDirection = SceneCluster.PROJECT_POSITIVE_Y;
				} else {
					if (intermediateValue9 >= -32)
						continue;
					sceneCluster.projectionDirection = SceneCluster.PROJECT_NEGATIVE_Y;
					intermediateValue9 = -intermediateValue9;
				}
				sceneCluster.minXGradient = (sceneCluster.minWorldX - cameraX << 8) / intermediateValue9;
				sceneCluster.maxXGradient = (sceneCluster.maxWorldX - cameraX << 8) / intermediateValue9;
				sceneCluster.minZGradient = (sceneCluster.minWorldZ - cameraZ << 8) / intermediateValue9;
				sceneCluster.maxZGradient = (sceneCluster.maxWorldZ - cameraZ << 8) / intermediateValue9;
				activeOccluders[activeOccluderCount++] = sceneCluster;
			} else if (sceneCluster.type == SceneCluster.TYPE_HORIZONTAL_PLANE) {
				int intermediateValue10 = sceneCluster.minWorldZ - cameraZ;
				if (intermediateValue10 > 128) {
					int intermediateValue11 = (sceneCluster.minTileY - cameraTileY) + 25;
					if (intermediateValue11 < 0)
						intermediateValue11 = 0;
					int intermediateValue12 = (sceneCluster.maxTileY - cameraTileY) + 25;
					if (intermediateValue12 > 50)
						intermediateValue12 = 50;
					if (intermediateValue11 <= intermediateValue12) {
						int intermediateValue13 = (sceneCluster.minTileX - cameraTileX) + 25;
						if (intermediateValue13 < 0)
							intermediateValue13 = 0;
						int intermediateValue14 = (sceneCluster.maxTileX - cameraTileX) + 25;
						if (intermediateValue14 > 50)
							intermediateValue14 = 50;
						boolean conditionFlag3 = false;
						label0: for (int loopIndex2 = intermediateValue13; loopIndex2 <= intermediateValue14; loopIndex2++) {
							for (int loopIndex3 = intermediateValue11; loopIndex3 <= intermediateValue12; loopIndex3++) {
								if (!visibilityMap[loopIndex2][loopIndex3])
									continue;
								conditionFlag3 = true;
								break label0;
							}

						}

						if (conditionFlag3) {
							sceneCluster.projectionDirection = SceneCluster.PROJECT_ABOVE;
							sceneCluster.minXGradient = (sceneCluster.minWorldX - cameraX << 8) / intermediateValue10;
							sceneCluster.maxXGradient = (sceneCluster.maxWorldX - cameraX << 8) / intermediateValue10;
							sceneCluster.minYGradient = (sceneCluster.minWorldY - cameraY << 8) / intermediateValue10;
							sceneCluster.maxYGradient = (sceneCluster.maxWorldY - cameraY << 8) / intermediateValue10;
							activeOccluders[activeOccluderCount++] = sceneCluster;
						}
					}
				}
			}
		}

	}

	/**
	 * Returns whether tile occluded.
	 *
	 * @return {@code true} when tile occluded; otherwise {@code false}
	 * @param plane the plane
	 * @param x     the x
	 * @param y     the y
	 */
	public boolean isTileOccluded(int plane, int x, int y) {
		int intermediateValue = tileOcclusionCycles[plane][x][y];
		if (intermediateValue == -renderCycle)
			return false;
		if (intermediateValue == renderCycle)
			return true;
		int intermediateValue2 = x << 7;
		int intermediateValue3 = y << 7;
		if (isPointOccluded(intermediateValue2 + 1, tileHeights[plane][x][y], intermediateValue3 + 1)
				&& isPointOccluded((intermediateValue2 + 128) - 1, tileHeights[plane][x + 1][y], intermediateValue3 + 1)
				&& isPointOccluded((intermediateValue2 + 128) - 1, tileHeights[plane][x + 1][y + 1],
						(intermediateValue3 + 128) - 1)
				&& isPointOccluded(intermediateValue2 + 1, tileHeights[plane][x][y + 1],
						(intermediateValue3 + 128) - 1)) {
			tileOcclusionCycles[plane][x][y] = renderCycle;
			return true;
		} else {
			tileOcclusionCycles[plane][x][y] = -renderCycle;
			return false;
		}
	}

	/**
	 * Returns whether wall occluded.
	 *
	 * @return {@code true} when wall occluded; otherwise {@code false}
	 * @param plane       the plane
	 * @param x           the x
	 * @param y           the y
	 * @param orientation the orientation
	 */
	public boolean isWallOccluded(int plane, int x, int y, int orientation) {
		if (!isTileOccluded(plane, x, y))
			return false;
		int intermediateValue = x << 7;
		int intermediateValue2 = y << 7;
		int intermediateValue3 = tileHeights[plane][x][y] - 1;
		int intermediateValue4 = intermediateValue3 - 120;
		int intermediateValue5 = intermediateValue3 - 230;
		int intermediateValue6 = intermediateValue3 - 238;
		if (orientation < 16) {
			if (orientation == 1) {
				if (intermediateValue > cameraX) {
					if (!isPointOccluded(intermediateValue, intermediateValue3, intermediateValue2))
						return false;
					if (!isPointOccluded(intermediateValue, intermediateValue3, intermediateValue2 + 128))
						return false;
				}
				if (plane > 0) {
					if (!isPointOccluded(intermediateValue, intermediateValue4, intermediateValue2))
						return false;
					if (!isPointOccluded(intermediateValue, intermediateValue4, intermediateValue2 + 128))
						return false;
				}
				if (!isPointOccluded(intermediateValue, intermediateValue5, intermediateValue2))
					return false;
				return isPointOccluded(intermediateValue, intermediateValue5, intermediateValue2 + 128);
			}
			if (orientation == 2) {
				if (intermediateValue2 < cameraY) {
					if (!isPointOccluded(intermediateValue, intermediateValue3, intermediateValue2 + 128))
						return false;
					if (!isPointOccluded(intermediateValue + 128, intermediateValue3, intermediateValue2 + 128))
						return false;
				}
				if (plane > 0) {
					if (!isPointOccluded(intermediateValue, intermediateValue4, intermediateValue2 + 128))
						return false;
					if (!isPointOccluded(intermediateValue + 128, intermediateValue4, intermediateValue2 + 128))
						return false;
				}
				if (!isPointOccluded(intermediateValue, intermediateValue5, intermediateValue2 + 128))
					return false;
				return isPointOccluded(intermediateValue + 128, intermediateValue5, intermediateValue2 + 128);
			}
			if (orientation == 4) {
				if (intermediateValue < cameraX) {
					if (!isPointOccluded(intermediateValue + 128, intermediateValue3, intermediateValue2))
						return false;
					if (!isPointOccluded(intermediateValue + 128, intermediateValue3, intermediateValue2 + 128))
						return false;
				}
				if (plane > 0) {
					if (!isPointOccluded(intermediateValue + 128, intermediateValue4, intermediateValue2))
						return false;
					if (!isPointOccluded(intermediateValue + 128, intermediateValue4, intermediateValue2 + 128))
						return false;
				}
				if (!isPointOccluded(intermediateValue + 128, intermediateValue5, intermediateValue2))
					return false;
				return isPointOccluded(intermediateValue + 128, intermediateValue5, intermediateValue2 + 128);
			}
			if (orientation == 8) {
				if (intermediateValue2 > cameraY) {
					if (!isPointOccluded(intermediateValue, intermediateValue3, intermediateValue2))
						return false;
					if (!isPointOccluded(intermediateValue + 128, intermediateValue3, intermediateValue2))
						return false;
				}
				if (plane > 0) {
					if (!isPointOccluded(intermediateValue, intermediateValue4, intermediateValue2))
						return false;
					if (!isPointOccluded(intermediateValue + 128, intermediateValue4, intermediateValue2))
						return false;
				}
				if (!isPointOccluded(intermediateValue, intermediateValue5, intermediateValue2))
					return false;
				return isPointOccluded(intermediateValue + 128, intermediateValue5, intermediateValue2);
			}
		}
		if (!isPointOccluded(intermediateValue + 64, intermediateValue6, intermediateValue2 + 64))
			return false;
		if (orientation == 16)
			return isPointOccluded(intermediateValue, intermediateValue5, intermediateValue2 + 128);
		if (orientation == 32)
			return isPointOccluded(intermediateValue + 128, intermediateValue5, intermediateValue2 + 128);
		if (orientation == 64)
			return isPointOccluded(intermediateValue + 128, intermediateValue5, intermediateValue2);
		if (orientation == 128) {
			return isPointOccluded(intermediateValue, intermediateValue5, intermediateValue2);
		} else {
			System.out.println("Warning unsupported wall type");
			return true;
		}
	}

	/**
	 * Returns whether decoration occluded.
	 *
	 * @return {@code true} when decoration occluded; otherwise {@code false}
	 * @param plane       the plane
	 * @param x           the x
	 * @param y           the y
	 * @param modelHeight the model height
	 */
	public boolean isDecorationOccluded(int plane, int x, int y, int modelHeight) {
		if (!isTileOccluded(plane, x, y))
			return false;
		int intermediateValue = x << 7;
		int intermediateValue2 = y << 7;
		return isPointOccluded(intermediateValue + 1, tileHeights[plane][x][y] - modelHeight, intermediateValue2 + 1)
				&& isPointOccluded((intermediateValue + 128) - 1, tileHeights[plane][x + 1][y] - modelHeight,
						intermediateValue2 + 1)
				&& isPointOccluded((intermediateValue + 128) - 1, tileHeights[plane][x + 1][y + 1] - modelHeight,
						(intermediateValue2 + 128) - 1)
				&& isPointOccluded(intermediateValue + 1, tileHeights[plane][x][y + 1] - modelHeight,
						(intermediateValue2 + 128) - 1);
	}

	/**
	 * Returns whether area occluded.
	 *
	 * @return {@code true} when area occluded; otherwise {@code false}
	 * @param plane       the plane
	 * @param minX        the min x
	 * @param maxX        the max x
	 * @param minY        the min y
	 * @param maxY        the max y
	 * @param modelHeight the model height
	 */
	public boolean isAreaOccluded(int plane, int minX, int maxX, int minY, int maxY, int modelHeight) {
		if (minX == maxX && minY == maxY) {
			if (!isTileOccluded(plane, minX, minY))
				return false;
			int intermediateValue = minX << 7;
			int intermediateValue2 = minY << 7;
			return isPointOccluded(intermediateValue + 1, tileHeights[plane][minX][minY] - modelHeight,
					intermediateValue2 + 1)
					&& isPointOccluded((intermediateValue + 128) - 1, tileHeights[plane][minX + 1][minY] - modelHeight,
							intermediateValue2 + 1)
					&& isPointOccluded((intermediateValue + 128) - 1,
							tileHeights[plane][minX + 1][minY + 1] - modelHeight, (intermediateValue2 + 128) - 1)
					&& isPointOccluded(intermediateValue + 1, tileHeights[plane][minX][minY + 1] - modelHeight,
							(intermediateValue2 + 128) - 1);
		}
		for (int loopIndex = minX; loopIndex <= maxX; loopIndex++) {
			for (int loopIndex2 = minY; loopIndex2 <= maxY; loopIndex2++)
				if (tileOcclusionCycles[plane][loopIndex][loopIndex2] == -renderCycle)
					return false;

		}

		int intermediateValue3 = (minX << 7) + 1;
		int intermediateValue4 = (minY << 7) + 2;
		int intermediateValue5 = tileHeights[plane][minX][minY] - modelHeight;
		if (!isPointOccluded(intermediateValue3, intermediateValue5, intermediateValue4))
			return false;
		int intermediateValue6 = (maxX << 7) - 1;
		if (!isPointOccluded(intermediateValue6, intermediateValue5, intermediateValue4))
			return false;
		int intermediateValue7 = (maxY << 7) - 1;
		if (!isPointOccluded(intermediateValue3, intermediateValue5, intermediateValue7))
			return false;
		return isPointOccluded(intermediateValue6, intermediateValue5, intermediateValue7);
	}

	/**
	 * Returns whether point occluded.
	 *
	 * @return {@code true} when point occluded; otherwise {@code false}
	 * @param worldX the world x
	 * @param worldZ the world z
	 * @param worldY the world y
	 */
	public boolean isPointOccluded(int worldX, int worldZ, int worldY) {
		for (int loopIndex = 0; loopIndex < activeOccluderCount; loopIndex++) {
			SceneCluster sceneCluster = activeOccluders[loopIndex];
			if (sceneCluster.projectionDirection == 1) {
				int intermediateValue = sceneCluster.minWorldX - worldX;
				if (intermediateValue > 0) {
					int intermediateValue2 = sceneCluster.minWorldY
							+ (sceneCluster.minYGradient * intermediateValue >> 8);
					int intermediateValue3 = sceneCluster.maxWorldY
							+ (sceneCluster.maxYGradient * intermediateValue >> 8);
					int intermediateValue4 = sceneCluster.minWorldZ
							+ (sceneCluster.minZGradient * intermediateValue >> 8);
					int intermediateValue5 = sceneCluster.maxWorldZ
							+ (sceneCluster.maxZGradient * intermediateValue >> 8);
					if (worldY >= intermediateValue2 && worldY <= intermediateValue3 && worldZ >= intermediateValue4
							&& worldZ <= intermediateValue5)
						return true;
				}
			} else if (sceneCluster.projectionDirection == 2) {
				int intermediateValue6 = worldX - sceneCluster.minWorldX;
				if (intermediateValue6 > 0) {
					int intermediateValue7 = sceneCluster.minWorldY
							+ (sceneCluster.minYGradient * intermediateValue6 >> 8);
					int intermediateValue8 = sceneCluster.maxWorldY
							+ (sceneCluster.maxYGradient * intermediateValue6 >> 8);
					int intermediateValue9 = sceneCluster.minWorldZ
							+ (sceneCluster.minZGradient * intermediateValue6 >> 8);
					int intermediateValue10 = sceneCluster.maxWorldZ
							+ (sceneCluster.maxZGradient * intermediateValue6 >> 8);
					if (worldY >= intermediateValue7 && worldY <= intermediateValue8 && worldZ >= intermediateValue9
							&& worldZ <= intermediateValue10)
						return true;
				}
			} else if (sceneCluster.projectionDirection == 3) {
				int intermediateValue11 = sceneCluster.minWorldY - worldY;
				if (intermediateValue11 > 0) {
					int intermediateValue12 = sceneCluster.minWorldX
							+ (sceneCluster.minXGradient * intermediateValue11 >> 8);
					int intermediateValue13 = sceneCluster.maxWorldX
							+ (sceneCluster.maxXGradient * intermediateValue11 >> 8);
					int intermediateValue14 = sceneCluster.minWorldZ
							+ (sceneCluster.minZGradient * intermediateValue11 >> 8);
					int intermediateValue15 = sceneCluster.maxWorldZ
							+ (sceneCluster.maxZGradient * intermediateValue11 >> 8);
					if (worldX >= intermediateValue12 && worldX <= intermediateValue13 && worldZ >= intermediateValue14
							&& worldZ <= intermediateValue15)
						return true;
				}
			} else if (sceneCluster.projectionDirection == 4) {
				int intermediateValue16 = worldY - sceneCluster.minWorldY;
				if (intermediateValue16 > 0) {
					int intermediateValue17 = sceneCluster.minWorldX
							+ (sceneCluster.minXGradient * intermediateValue16 >> 8);
					int intermediateValue18 = sceneCluster.maxWorldX
							+ (sceneCluster.maxXGradient * intermediateValue16 >> 8);
					int intermediateValue19 = sceneCluster.minWorldZ
							+ (sceneCluster.minZGradient * intermediateValue16 >> 8);
					int intermediateValue20 = sceneCluster.maxWorldZ
							+ (sceneCluster.maxZGradient * intermediateValue16 >> 8);
					if (worldX >= intermediateValue17 && worldX <= intermediateValue18 && worldZ >= intermediateValue19
							&& worldZ <= intermediateValue20)
						return true;
				}
			} else if (sceneCluster.projectionDirection == 5) {
				int intermediateValue21 = worldZ - sceneCluster.minWorldZ;
				if (intermediateValue21 > 0) {
					int intermediateValue22 = sceneCluster.minWorldX
							+ (sceneCluster.minXGradient * intermediateValue21 >> 8);
					int intermediateValue23 = sceneCluster.maxWorldX
							+ (sceneCluster.maxXGradient * intermediateValue21 >> 8);
					int intermediateValue24 = sceneCluster.minWorldY
							+ (sceneCluster.minYGradient * intermediateValue21 >> 8);
					int intermediateValue25 = sceneCluster.maxWorldY
							+ (sceneCluster.maxYGradient * intermediateValue21 >> 8);
					if (worldX >= intermediateValue22 && worldX <= intermediateValue23 && worldY >= intermediateValue24
							&& worldY <= intermediateValue25)
						return true;
				}
			}
		}

		return false;
	}

	/**
	 * Whether low memory.
	 */
	public static boolean lowMemory = true;
	/**
	 * Number of plane entries.
	 */
	public int planeCount;

	/** Stores the current width. */
	public int width;

	/** Stores the current height. */
	public int height;

	/** Stores tile heights values. */
	public int tileHeights[][][];

	/** Stores tiles values. */
	public SceneTile tiles[][][];

	/** Stores the current min plane. */
	public int minPlane;
	/**
	 * Number of temporary object entries.
	 */
	public int temporaryObjectCount;

	/** Stores temporary objects values. */
	public InteractiveObject temporaryObjects[];

	/** Stores tile occlusion cycles values. */
	public int tileOcclusionCycles[][][];
	/**
	 * Number of remaining tile entries.
	 */
	public static int remainingTileCount;

	/** Stores the current render plane. */
	public static int renderPlane;

	/** Stores the current render cycle. */
	public static int renderCycle;

	/** Stores the current min tile X. */
	public static int minTileX;

	/** Stores the current max tile X. */
	public static int maxTileX;

	/** Stores the current min tile Y. */
	public static int minTileY;

	/** Stores the current max tile Y. */
	public static int maxTileY;

	/** Stores the current camera tile X. */
	public static int cameraTileX;

	/** Stores the current camera tile Y. */
	public static int cameraTileY;

	/** Stores the current camera X. */
	public static int cameraX;

	/** Stores the current camera Z. */
	public static int cameraZ;

	/** Stores the current camera Y. */
	public static int cameraY;

	/** Stores the current pitch sine. */
	public static int pitchSine;

	/** Stores the current pitch cosine. */
	public static int pitchCosine;

	/** Stores the current yaw sine. */
	public static int yawSine;

	/** Stores the current yaw cosine. */
	public static int yawCosine;

	/** Stores render interactive objects values. */
	public static InteractiveObject renderInteractiveObjects[] = new InteractiveObject[100];

	/** Constant value for wall decoration inset X. */
	public static final int WALL_DECORATION_INSET_X[] = { 53, -53, -53, 53 };

	/** Constant value for wall decoration inset Y. */
	public static final int WALL_DECORATION_INSET_Y[] = { -53, -53, 53, 53 };

	/** Constant value for wall decoration outset X. */
	public static final int WALL_DECORATION_OUTSET_X[] = { -45, 45, 45, -45 };

	/** Constant value for wall decoration outset Y. */
	public static final int WALL_DECORATION_OUTSET_Y[] = { 45, 45, -45, -45 };
	/**
	 * Whether picking.
	 */
	public static boolean picking;

	/** Stores the current mouse X. */
	public static int mouseX;

	/** Stores the current mouse Y. */
	public static int mouseY;

	/** Stores the current picked tile X. */
	public static int pickedTileX = -1;

	/** Stores the current picked tile Y. */
	public static int pickedTileY = -1;

	/** Constant value for occluder plane count. */
	public static int OCCLUDER_PLANE_COUNT;

	/** Stores occluder counts values. */
	public static int occluderCounts[];

	/** Stores occluders values. */
	public static SceneCluster occluders[][];
	/**
	 * Number of active occluder entries.
	 */
	public static int activeOccluderCount;

	/** Stores active occluders values. */
	public static SceneCluster activeOccluders[] = new SceneCluster[500];

	/**
	 * Tile queue.
	 *
	 */
	public static NodeDeque tileQueue = new NodeDeque();

	/** Constant value for wall draw flags. */
	public static final int WALL_DRAW_FLAGS[] = { 19, 55, 38, 155, 255, 110, 137, 205, 76 };

	/** Constant value for wall cull flags. */
	public static final int WALL_CULL_FLAGS[] = { 160, 192, 80, 96, 0, 144, 80, 48, 160 };

	/** Constant value for wall draw flags 2. */
	public static final int WALL_DRAW_FLAGS_2[] = { 76, 8, 137, 4, 0, 1, 38, 2, 19 };

	/** Constant value for wall uncull flags 0. */
	public static final int WALL_UNCULL_FLAGS_0[] = { 0, 0, 2, 0, 0, 2, 1, 1, 0 };

	/** Constant value for wall uncull flags 1. */
	public static final int WALL_UNCULL_FLAGS_1[] = { 2, 0, 0, 2, 0, 0, 0, 4, 4 };

	/** Constant value for wall uncull flags 2. */
	public static final int WALL_UNCULL_FLAGS_2[] = { 0, 4, 4, 8, 0, 0, 8, 0, 0 };

	/** Constant value for wall uncull flags 3. */
	public static final int WALL_UNCULL_FLAGS_3[] = { 1, 1, 0, 0, 0, 8, 0, 0, 8 };

	/** Constant value for texture colors. */
	public static final int TEXTURE_COLORS[] = { 41, 39248, 41, 4643, 41, 41, 41, 41, 41, 41, 41, 41, 41, 41, 41, 43086,
			41, 41, 41, 41, 41, 41, 41, 8602, 41, 28992, 41, 41, 41, 41, 41, 5056, 41, 41, 41, 7079, 41, 41, 41, 41, 41,
			41, 41, 41, 41, 41, 3131, 41, 41, 41 };

	/** Stores merge stamp a values. */
	public int mergeStampA[];

	/** Stores merge stamp b values. */
	public int mergeStampB[];

	/** Stores the current merge cycle. */
	public int mergeCycle;

	/** Stores minimap tile shape values. */
	public int minimapTileShape[][] = { new int[16], { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 },
			{ 1, 0, 0, 0, 1, 1, 0, 0, 1, 1, 1, 0, 1, 1, 1, 1 }, { 1, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0 },
			{ 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 1 }, { 0, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 },
			{ 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1 }, { 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0 },
			{ 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0 }, { 1, 1, 1, 1, 1, 1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 1 },
			{ 1, 1, 1, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0 }, { 0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1 },
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 1, 1 } };

	/** Stores minimap tile rotation values. */
	public int minimapTileRotation[][] = { { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 },
			{ 12, 8, 4, 0, 13, 9, 5, 1, 14, 10, 6, 2, 15, 11, 7, 3 },
			{ 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0 },
			{ 3, 7, 11, 15, 2, 6, 10, 14, 1, 5, 9, 13, 0, 4, 8, 12 } };

	/** Whether visibility maps is enabled or active. */
	public static boolean visibilityMaps[][][][] = new boolean[8][32][51][51];

	/** Whether visibility map is enabled or active. */
	public static boolean visibilityMap[][];

	/** Stores the current viewport center X. */
	public static int viewportCenterX;

	/** Stores the current viewport center Y. */
	public static int viewportCenterY;

	/** Stores the current viewport min X. */
	public static int viewportMinX;

	/** Stores the current viewport min Y. */
	public static int viewportMinY;

	/** Stores the current viewport max X. */
	public static int viewportMaxX;

	/** Stores the current viewport max Y. */
	public static int viewportMaxY;

	static {
		OCCLUDER_PLANE_COUNT = 4;
		occluderCounts = new int[OCCLUDER_PLANE_COUNT];
		occluders = new SceneCluster[OCCLUDER_PLANE_COUNT][500];
	}
}
