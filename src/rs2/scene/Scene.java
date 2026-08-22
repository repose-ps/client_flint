package rs2.scene;

import rs2.collection.NodeDeque;
import rs2.media.Rasterizer;
import rs2.media.Rasterizer3D;
import rs2.media.VertexNormal;
import rs2.media.renderable.Model;
import rs2.media.renderable.Renderable;
import rs2.scene.tile.ComplexTile;
import rs2.scene.tile.FloorDecoration;
import rs2.scene.tile.GenericTile;
import rs2.scene.tile.SceneTile;
import rs2.scene.tile.Wall;
import rs2.scene.tile.WallDecoration;

public class Scene {

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

	public static void clearStatic() {
		renderInteractiveObjects = null;
		occluderCounts = null;
		occluders = null;
		tileQueue = null;
		visibilityMaps = null;
		visibilityMap = null;
	}

	public void clear() {
		for (int i = 0; i < planeCount; i++) {
			for (int j = 0; j < width; j++) {
				for (int i1 = 0; i1 < height; i1++)
					tiles[i][j][i1] = null;

			}

		}

		for (int l = 0; l < OCCLUDER_PLANE_COUNT; l++) {
			for (int j1 = 0; j1 < occluderCounts[l]; j1++)
				occluders[l][j1] = null;

			occluderCounts[l] = 0;
		}

		for (int k1 = 0; k1 < temporaryObjectCount; k1++)
			temporaryObjects[k1] = null;

		temporaryObjectCount = 0;
		for (int l1 = 0; l1 < renderInteractiveObjects.length; l1++)
			renderInteractiveObjects[l1] = null;

	}

	public void setMinPlane(int i) {
		minPlane = i;
		for (int j = 0; j < width; j++) {
			for (int k = 0; k < height; k++)
				if (tiles[i][j][k] == null)
					tiles[i][j][k] = new SceneTile(i, j, k);
		}
	}

	public void setBridgeMode(int x, int y) {
		SceneTile class50_sub3 = tiles[0][x][y];
		for (int k = 0; k < 3; k++) {
			SceneTile class50_sub3_1 = tiles[k][x][y] = tiles[k + 1][x][y];
			if (class50_sub3_1 != null) {
				class50_sub3_1.plane--;
				for (int i1 = 0; i1 < class50_sub3_1.interactiveObjectCount; i1++) {
					InteractiveObject class5 = class50_sub3_1.interactiveObjects[i1];
					if ((class5.uid >> 29 & 3) == 2 && class5.tileLeft == x && class5.tileTop == y)
						class5.plane--;
				}

			}
		}

		if (tiles[0][x][y] == null)
			tiles[0][x][y] = new SceneTile(0, x, y);
		tiles[0][x][y].tileBelow = class50_sub3;
		tiles[3][x][y] = null;
	}

	public static void addOccluder(int plane, int minWorldX, int minWorldZ, int maxWorldX, int maxWorldY, int maxWorldZ,
			int minWorldY, int type) {
		SceneCluster class39 = new SceneCluster();
		class39.minTileX = minWorldX / 128;
		class39.maxTileX = maxWorldX / 128;
		class39.minTileY = minWorldY / 128;
		class39.maxTileY = maxWorldY / 128;
		class39.type = type;
		class39.minWorldX = minWorldX;
		class39.maxWorldX = maxWorldX;
		class39.minWorldY = minWorldY;
		class39.maxWorldY = maxWorldY;
		class39.minWorldZ = minWorldZ;
		class39.maxWorldZ = maxWorldZ;
		occluders[plane][occluderCounts[plane]++] = class39;
	}

	public void setTileLogicHeight(int plane, int x, int y, int logicHeight) {
		SceneTile class50_sub3 = tiles[plane][x][y];
		if (class50_sub3 == null) {
			return;
		} else {
			tiles[plane][x][y].logicHeight = logicHeight;
			return;
		}
	}

	public void addTile(int plane, int x, int y, int shape, int rotation, int textureId, int southWestHeight,
			int southEastHeight, int northEastHeight, int northWestHeight, int underlaySouthWest, int underlaySouthEast,
			int underlayNorthEast, int j3, int k3, int l3, int i4, int j4, int k4, int l4) {
		if (shape == 0) {
			GenericTile class3 = new GenericTile(underlaySouthWest, underlaySouthEast, underlayNorthEast, j3, -1, k4,
					false);
			for (int i5 = plane; i5 >= 0; i5--)
				if (tiles[i5][x][y] == null)
					tiles[i5][x][y] = new SceneTile(i5, x, y);

			tiles[plane][x][y].plainTile = class3;
			return;
		}
		if (shape == 1) {
			GenericTile class3_1 = new GenericTile(k3, l3, i4, j4, textureId, l4, southWestHeight == southEastHeight
					&& southWestHeight == northEastHeight && southWestHeight == northWestHeight);
			for (int j5 = plane; j5 >= 0; j5--)
				if (tiles[j5][x][y] == null)
					tiles[j5][x][y] = new SceneTile(j5, x, y);

			tiles[plane][x][y].plainTile = class3_1;
			return;
		}
		ComplexTile class20 = new ComplexTile(x, southWestHeight, southEastHeight, northWestHeight, northEastHeight, y,
				rotation, textureId, shape, underlaySouthWest, k3, underlaySouthEast, l3, j3, j4, underlayNorthEast, i4,
				l4, k4);
		for (int k5 = plane; k5 >= 0; k5--)
			if (tiles[k5][x][y] == null)
				tiles[k5][x][y] = new SceneTile(k5, x, y);

		tiles[plane][x][y].shapedTile = class20;
	}

	public void addFloorDecoration(int pln, int x, int y, int dwht, int uid, byte cfg, Renderable redn) {
		if (redn == null)
			return;
		FloorDecoration class28 = new FloorDecoration();
		class28.renderable = redn;
		class28.x = x * 128 + 64;
		class28.y = y * 128 + 64;
		class28.z = dwht;
		class28.uid = uid;
		class28.config = cfg;
		if (tiles[pln][x][y] == null)
			tiles[pln][x][y] = new SceneTile(pln, x, y);
		tiles[pln][x][y].floorDecoration = class28;
	}

	public void addGroundItemTile(int pn, int x, int y, int dh, int uid, Renderable first, Renderable second,
			Renderable thrid) {
		GroundItemTile class10 = new GroundItemTile();
		class10.firstGroundItem = first;
		class10.x = x * 128 + 64;
		class10.y = y * 128 + 64;
		class10.z = dh;
		class10.uid = uid;
		class10.secondGroundItem = second;
		class10.thirdGroundItem = thrid;
		int k1 = 0;
		SceneTile class50_sub3 = tiles[pn][x][y];
		if (class50_sub3 != null) {
			for (int l1 = 0; l1 < class50_sub3.interactiveObjectCount; l1++)
				if (class50_sub3.interactiveObjects[l1].renderable instanceof Model) {
					int i2 = ((Model) class50_sub3.interactiveObjects[l1].renderable).itemDropHeight;
					if (i2 > k1)
						k1 = i2;
				}

		}
		class10.heightOffset = k1;
		if (tiles[pn][x][y] == null)
			tiles[pn][x][y] = new SceneTile(pn, x, y);
		tiles[pn][x][y].groundItemTile = class10;
	}

	public void addWall(int plane, int x, int y, int drawHeight, int uid, byte config, Renderable primary,
			Renderable secondary, int orientation, int secondaryOrientation) {
		if (primary == null && secondary == null)
			return;
		Wall class44 = new Wall();
		class44.uid = uid;
		class44.config = config;
		class44.x = x * 128 + 64;
		class44.y = y * 128 + 64;
		class44.z = drawHeight;
		class44.primary = primary;
		class44.secondary = secondary;
		class44.orientation = orientation;
		class44.secondaryOrientation = secondaryOrientation;
		for (int tempplane = plane; tempplane >= 0; tempplane--)
			if (tiles[tempplane][x][y] == null)
				tiles[tempplane][x][y] = new SceneTile(tempplane, x, y);

		tiles[plane][x][y].wall = class44;
	}

	public void addWallDecoration(int plane, int x, int y, int drawHeight, int offsetX, int offsetY, int face, int uid,
			byte config, int configBits, Renderable renderable) {
		if (renderable == null)
			return;
		WallDecoration class35 = new WallDecoration();
		class35.uid = uid;
		class35.config = config;
		class35.x = x * 128 + 64 + offsetX;
		class35.y = y * 128 + 64 + offsetY;
		class35.z = drawHeight;
		class35.renderable = renderable;
		class35.configBits = configBits;
		class35.face = face;
		for (int k2 = plane; k2 >= 0; k2--)
			if (tiles[k2][x][y] == null)
				tiles[k2][x][y] = new SceneTile(k2, x, y);

		tiles[plane][x][y].wallDecoration = class35;
	}

	public boolean addGameObject(int plane, int x, int y, int tileWidth, int tileHeight, int drawHeight,
			Renderable renderable, int rotation, int uid, byte config) {
		if (renderable == null) {
			return true;
		} else {
			int j2 = x * 128 + 64 * tileWidth;
			int k2 = y * 128 + 64 * tileHeight;
			return addInteractiveObject(plane, x, y, tileWidth, tileHeight, j2, k2, drawHeight, renderable, rotation, false, uid,
					config);
		}
	}

	public boolean addEntity(int plane, int worldX, int worldY, int worldZ, Renderable renderable, int uid, int radius,
			boolean accountForYaw, int yaw) {
		if (renderable == null)
			return true;
		int i2 = worldX - radius;
		int j2 = worldY - radius;
		int maxWorldX = worldX + radius;
		int maxWorldY = worldY + radius;
		if (accountForYaw) {
			if (yaw > 640 && yaw < 1408)
				maxWorldY += 128;
			if (yaw > 1152 && yaw < 1920)
				maxWorldX += 128;
			if (yaw > 1664 || yaw < 384)
				j2 -= 128;
			if (yaw > 128 && yaw < 896)
				i2 -= 128;
		}
		i2 /= 128;
		j2 /= 128;
		maxWorldX /= 128;
		maxWorldY /= 128;
		return addInteractiveObject(plane, i2, j2, (maxWorldX - i2) + 1, (maxWorldY - j2) + 1, worldX, worldY, worldZ, renderable,
				yaw, true, uid, (byte) 0);
	}

	public boolean addEntityBounds(int plane, int minX, int minY, int maxX, int maxY, int worldX, int worldY,
			int worldZ, Renderable renderable, int rotation, int uid) {
		if (renderable == null)
			return true;
		else
			return addInteractiveObject(plane, minX, minY, (maxX - minX) + 1, (maxY - minY) + 1, worldX, worldY, worldZ,
					renderable, rotation, true, uid, (byte) 0);
	}

	public boolean addInteractiveObject(int plane, int minX, int minY, int tileWidth, int tileHeight, int worldX, int worldY,
			int worldZ, Renderable renderable, int rotation, boolean temporary, int uid, byte config) {
		for (int k2 = minX; k2 < minX + tileWidth; k2++) {
			for (int l2 = minY; l2 < minY + tileHeight; l2++) {
				if (k2 < 0 || l2 < 0 || k2 >= width || l2 >= height)
					return false;
				SceneTile class50_sub3 = tiles[plane][k2][l2];
				if (class50_sub3 != null && class50_sub3.interactiveObjectCount >= 5)
					return false;
			}

		}

		InteractiveObject class5 = new InteractiveObject();
		class5.uid = uid;
		class5.config = config;
		class5.plane = plane;
		class5.worldX = worldX;
		class5.worldY = worldY;
		class5.worldZ = worldZ;
		class5.renderable = renderable;
		class5.rotation = rotation;
		class5.tileLeft = minX;
		class5.tileTop = minY;
		class5.tileRight = (minX + tileWidth) - 1;
		class5.tileBottom = (minY + tileHeight) - 1;
		for (int i3 = minX; i3 < minX + tileWidth; i3++) {
			for (int j3 = minY; j3 < minY + tileHeight; j3++) {
				int k3 = 0;
				if (i3 > minX)
					k3++;
				if (i3 < (minX + tileWidth) - 1)
					k3 += 4;
				if (j3 > minY)
					k3 += 8;
				if (j3 < (minY + tileHeight) - 1)
					k3 += 2;
				for (int l3 = plane; l3 >= 0; l3--)
					if (tiles[l3][i3][j3] == null)
						tiles[l3][i3][j3] = new SceneTile(l3, i3, j3);

				SceneTile class50_sub3_1 = tiles[plane][i3][j3];
				class50_sub3_1.interactiveObjects[class50_sub3_1.interactiveObjectCount] = class5;
				class50_sub3_1.interactiveObjectEdgeMasks[class50_sub3_1.interactiveObjectCount] = k3;
				class50_sub3_1.combinedInteractiveObjectEdgeMask |= k3;
				class50_sub3_1.interactiveObjectCount++;
			}

		}

		if (temporary)
			temporaryObjects[temporaryObjectCount++] = class5;
		return true;
	}

	public void clearTemporaryObjects() {
		for (int j = 0; j < temporaryObjectCount; j++) {
			InteractiveObject class5 = temporaryObjects[j];
			removeInteractiveObjectInternal(class5);
			temporaryObjects[j] = null;
		}

		temporaryObjectCount = 0;
	}

	public void removeInteractiveObjectInternal(InteractiveObject class5) {
		for (int j = class5.tileLeft; j <= class5.tileRight; j++) {
			for (int k = class5.tileTop; k <= class5.tileBottom; k++) {
				SceneTile class50_sub3 = tiles[class5.plane][j][k];
				if (class50_sub3 != null) {
					for (int l = 0; l < class50_sub3.interactiveObjectCount; l++) {
						if (class50_sub3.interactiveObjects[l] != class5)
							continue;
						class50_sub3.interactiveObjectCount--;
						for (int i1 = l; i1 < class50_sub3.interactiveObjectCount; i1++) {
							class50_sub3.interactiveObjects[i1] = class50_sub3.interactiveObjects[i1 + 1];
							class50_sub3.interactiveObjectEdgeMasks[i1] = class50_sub3.interactiveObjectEdgeMasks[i1
									+ 1];
						}

						class50_sub3.interactiveObjects[class50_sub3.interactiveObjectCount] = null;
						break;
					}

					class50_sub3.combinedInteractiveObjectEdgeMask = 0;
					for (int j1 = 0; j1 < class50_sub3.interactiveObjectCount; j1++)
						class50_sub3.combinedInteractiveObjectEdgeMask |= class50_sub3.interactiveObjectEdgeMasks[j1];

				}
			}

		}
	}

	public void displaceWallDecoration(int plane, int x, int y, int displacement) {
		SceneTile class50_sub3 = tiles[plane][x][y];
		if (class50_sub3 == null)
			return;
		WallDecoration class35 = class50_sub3.wallDecoration;
		if (class35 == null)
			return;
		int j1 = x * 128 + 64;
		int k1 = y * 128 + 64;
		class35.x = j1 + ((class35.x - j1) * displacement) / 16;
		class35.y = k1 + ((class35.y - k1) * displacement) / 16;
	}

	public void removeWall(int plane, int x, int y) {
		SceneTile class50_sub3 = tiles[plane][x][y];
		if (class50_sub3 == null)
			return;
		class50_sub3.wall = null;
	}

	public void removeWallDecoration(int plane, int x, int y) {
		SceneTile class50_sub3 = tiles[plane][x][y];
		if (class50_sub3 == null) {
			return;
		} else {
			class50_sub3.wallDecoration = null;
			return;
		}
	}

	public void removeInteractiveObject(int plane, int x, int y) {
		SceneTile class50_sub3 = tiles[plane][x][y];
		if (class50_sub3 == null)
			return;
		for (int i1 = 0; i1 < class50_sub3.interactiveObjectCount; i1++) {
			InteractiveObject class5 = class50_sub3.interactiveObjects[i1];
			if ((class5.uid >> 29 & 3) == 2 && class5.tileLeft == x && class5.tileTop == y) {
				removeInteractiveObjectInternal(class5);
				return;
			}
		}

	}

	public void removeFloorDecoration(int plane, int x, int y) {
		SceneTile class50_sub3 = tiles[plane][x][y];
		if (class50_sub3 == null)
			return;
		class50_sub3.floorDecoration = null;
	}

	public void removeGroundItemTile(int plane, int x, int y) {
		SceneTile class50_sub3 = tiles[plane][x][y];
		if (class50_sub3 == null) {
			return;
		} else {
			class50_sub3.groundItemTile = null;
			return;
		}
	}

	public Wall getWall(int p, int x, int y) {
		SceneTile class50_sub3 = tiles[p][x][y];
		if (class50_sub3 == null)
			return null;
		else
			return class50_sub3.wall;
	}

	public WallDecoration getWallDecoration(int p, int x, int y) {
		SceneTile class50_sub3 = tiles[p][x][y];
		if (class50_sub3 == null)
			return null;
		else
			return class50_sub3.wallDecoration;
	}

	public InteractiveObject getInteractiveObject(int p, int x, int y) {
		SceneTile class50_sub3 = tiles[p][x][y];
		if (class50_sub3 == null)
			return null;
		for (int i1 = 0; i1 < class50_sub3.interactiveObjectCount; i1++) {
			InteractiveObject class5 = class50_sub3.interactiveObjects[i1];
			if ((class5.uid >> 29 & 3) == 2 && class5.tileLeft == x && class5.tileTop == y)
				return class5;
		}

		return null;
	}

	public FloorDecoration getFloorDecoration(int p, int x, int y) {
		SceneTile class50_sub3 = tiles[p][x][y];
		if (class50_sub3 == null || class50_sub3.floorDecoration == null)
			return null;
		else
			return class50_sub3.floorDecoration;
	}

	public int getWallUid(int p, int x, int y) {
		SceneTile class50_sub3 = tiles[p][x][y];
		if (class50_sub3 == null || class50_sub3.wall == null)
			return 0;
		else
			return class50_sub3.wall.uid;
	}

	public int getWallDecorationUid(int p, int x, int y) {
		SceneTile class50_sub3 = tiles[p][x][y];
		if (class50_sub3 == null || class50_sub3.wallDecoration == null)
			return 0;
		else
			return class50_sub3.wallDecoration.uid;
	}

	public int getInteractiveObjectUid(int p, int x, int y) {
		SceneTile class50_sub3 = tiles[p][x][y];
		if (class50_sub3 == null)
			return 0;
		for (int l = 0; l < class50_sub3.interactiveObjectCount; l++) {
			InteractiveObject class5 = class50_sub3.interactiveObjects[l];
			if ((class5.uid >> 29 & 3) == 2 && class5.tileLeft == x && class5.tileTop == y)
				return class5.uid;
		}

		return 0;
	}

	public int getFloorDecorationUid(int p, int x, int y) {
		SceneTile class50_sub3 = tiles[p][x][y];
		if (class50_sub3 == null || class50_sub3.floorDecoration == null)
			return 0;
		else
			return class50_sub3.floorDecoration.uid;
	}

	public int getConfig(int plane, int x, int y, int uid) {
		SceneTile class50_sub3 = tiles[plane][x][y];
		if (class50_sub3 == null)
			return -1;
		if (class50_sub3.wall != null && class50_sub3.wall.uid == uid)
			return class50_sub3.wall.config & 0xff;
		if (class50_sub3.wallDecoration != null && class50_sub3.wallDecoration.uid == uid)
			return class50_sub3.wallDecoration.config & 0xff;
		if (class50_sub3.floorDecoration != null && class50_sub3.floorDecoration.uid == uid)
			return class50_sub3.floorDecoration.config & 0xff;
		for (int i1 = 0; i1 < class50_sub3.interactiveObjectCount; i1++)
			if (class50_sub3.interactiveObjects[i1].uid == uid)
				return class50_sub3.interactiveObjects[i1].config & 0xff;

		return -1;
	}

	public void shadeModels(int lightX, int lightY, int lightZ) {
		for (int l = 0; l < planeCount; l++) {
			for (int i1 = 0; i1 < width; i1++) {
				for (int j1 = 0; j1 < height; j1++) {
					SceneTile class50_sub3 = tiles[l][i1][j1];
					if (class50_sub3 != null) {
						Wall class44 = class50_sub3.wall;
						if (class44 != null && class44.primary != null && class44.primary.vertexNormals != null) {
							mergeAdjacentNormals((Model) class44.primary, l, i1, j1, 1, 1);
							if (class44.secondary != null && class44.secondary.vertexNormals != null) {
								mergeAdjacentNormals((Model) class44.secondary, l, i1, j1, 1, 1);
								mergeNormals((Model) class44.primary, (Model) class44.secondary, 0, 0, 0, false);
								((Model) class44.secondary).applyDeferredLighting(lightX, lightY, lightZ);
							}
							((Model) class44.primary).applyDeferredLighting(lightX, lightY, lightZ);
						}
						for (int k1 = 0; k1 < class50_sub3.interactiveObjectCount; k1++) {
							InteractiveObject class5 = class50_sub3.interactiveObjects[k1];
							if (class5 != null && class5.renderable != null
									&& class5.renderable.vertexNormals != null) {
								mergeAdjacentNormals((Model) class5.renderable, l, i1, j1,
										(class5.tileRight - class5.tileLeft) + 1,
										(class5.tileBottom - class5.tileTop) + 1);
								((Model) class5.renderable).applyDeferredLighting(lightX, lightY, lightZ);
							}
						}

						FloorDecoration class28 = class50_sub3.floorDecoration;
						if (class28 != null && class28.renderable.vertexNormals != null) {
							mergeFloorDecorationNormals((Model) class28.renderable, l, i1, j1);
							((Model) class28.renderable).applyDeferredLighting(lightX, lightY, lightZ);
						}
					}
				}

			}

		}
	}

	public void mergeFloorDecorationNormals(Model model, int plane, int x, int y) {
		if (x < width) {
			SceneTile class50_sub3 = tiles[plane][x + 1][y];
			if (class50_sub3 != null && class50_sub3.floorDecoration != null
					&& class50_sub3.floorDecoration.renderable.vertexNormals != null)
				mergeNormals(model, (Model) class50_sub3.floorDecoration.renderable, 128, 0, 0, true);
		}
		if (y < width) {
			SceneTile class50_sub3_1 = tiles[plane][x][y + 1];
			if (class50_sub3_1 != null && class50_sub3_1.floorDecoration != null
					&& class50_sub3_1.floorDecoration.renderable.vertexNormals != null)
				mergeNormals(model, (Model) class50_sub3_1.floorDecoration.renderable, 0, 0, 128, true);
		}
		if (x < width && y < height) {
			SceneTile class50_sub3_2 = tiles[plane][x + 1][y + 1];
			if (class50_sub3_2 != null && class50_sub3_2.floorDecoration != null
					&& class50_sub3_2.floorDecoration.renderable.vertexNormals != null)
				mergeNormals(model, (Model) class50_sub3_2.floorDecoration.renderable, 128, 0, 128, true);
		}
		if (x < width && y > 0) {
			SceneTile class50_sub3_3 = tiles[plane][x + 1][y - 1];
			if (class50_sub3_3 != null && class50_sub3_3.floorDecoration != null
					&& class50_sub3_3.floorDecoration.renderable.vertexNormals != null)
				mergeNormals(model, (Model) class50_sub3_3.floorDecoration.renderable, 128, 0, -128, true);
		}
	}

	public void mergeAdjacentNormals(Model model, int plane, int x, int y, int sizeX, int sizeY) {
		boolean flag = true;
		int k1 = x;
		int l1 = x + sizeX;
		int i2 = y - 1;
		int j2 = y + sizeY;
		for (int k2 = plane; k2 <= plane + 1; k2++)
			if (k2 != planeCount) {
				for (int l2 = k1; l2 <= l1; l2++)
					if (l2 >= 0 && l2 < width) {
						for (int i3 = i2; i3 <= j2; i3++)
							if (i3 >= 0 && i3 < height && (!flag || l2 >= l1 || i3 >= j2 || i3 < y && l2 != x)) {
								SceneTile class50_sub3 = tiles[k2][l2][i3];
								if (class50_sub3 != null) {
									int j3 = (tileHeights[k2][l2][i3] + tileHeights[k2][l2 + 1][i3]
											+ tileHeights[k2][l2][i3 + 1] + tileHeights[k2][l2 + 1][i3 + 1]) / 4
											- (tileHeights[plane][x][y] + tileHeights[plane][x + 1][y]
													+ tileHeights[plane][x][y + 1] + tileHeights[plane][x + 1][y + 1])
													/ 4;
									Wall class44 = class50_sub3.wall;
									if (class44 != null && class44.primary != null
											&& class44.primary.vertexNormals != null)
										mergeNormals(model, (Model) class44.primary, (l2 - x) * 128 + (1 - sizeX) * 64,
												j3, (i3 - y) * 128 + (1 - sizeY) * 64, flag);
									if (class44 != null && class44.secondary != null
											&& class44.secondary.vertexNormals != null)
										mergeNormals(model, (Model) class44.secondary,
												(l2 - x) * 128 + (1 - sizeX) * 64, j3,
												(i3 - y) * 128 + (1 - sizeY) * 64, flag);
									for (int k3 = 0; k3 < class50_sub3.interactiveObjectCount; k3++) {
										InteractiveObject class5 = class50_sub3.interactiveObjects[k3];
										if (class5 != null && class5.renderable != null
												&& class5.renderable.vertexNormals != null) {
											int l3 = (class5.tileRight - class5.tileLeft) + 1;
											int i4 = (class5.tileBottom - class5.tileTop) + 1;
											mergeNormals(model, (Model) class5.renderable,
													(class5.tileLeft - x) * 128 + (l3 - sizeX) * 64, j3,
													(class5.tileTop - y) * 128 + (i4 - sizeY) * 64, flag);
										}
									}

								}
							}

					}

				k1--;
				flag = false;
			}
	}

	public void mergeNormals(Model modelA, Model modelB, int offsetX, int offsetY, int offsetZ, boolean hideFaces) {
		mergeCycle++;
		int l = 0;
		int ai[] = modelB.verticesX;
		int i1 = modelB.vertexCount;
		int j1 = modelB.packedXBounds >> 16;
		int k1 = (modelB.packedXBounds << 16) >> 16;
		int l1 = modelB.packedZBounds >> 16;
		int i2 = (modelB.packedZBounds << 16) >> 16;
		for (int j2 = 0; j2 < modelA.vertexCount; j2++) {
			VertexNormal class40 = ((Renderable) (modelA)).vertexNormals[j2];
			VertexNormal class40_1 = modelA.vertexNormalOffsets[j2];
			if (class40_1.magnitude != 0) {
				int i3 = modelA.verticesY[j2] - offsetY;
				if (i3 <= modelB.maxY) {
					int j3 = modelA.verticesX[j2] - offsetX;
					if (j3 >= j1 && j3 <= k1) {
						int k3 = modelA.verticesZ[j2] - offsetZ;
						if (k3 >= i2 && k3 <= l1) {
							for (int l3 = 0; l3 < i1; l3++) {
								VertexNormal class40_2 = ((Renderable) (modelB)).vertexNormals[l3];
								VertexNormal class40_3 = modelB.vertexNormalOffsets[l3];
								if (j3 == ai[l3] && k3 == modelB.verticesZ[l3] && i3 == modelB.verticesY[l3]
										&& class40_3.magnitude != 0) {
									class40.x += class40_3.x;
									class40.y += class40_3.y;
									class40.z += class40_3.z;
									class40.magnitude += class40_3.magnitude;
									class40_2.x += class40_1.x;
									class40_2.y += class40_1.y;
									class40_2.z += class40_1.z;
									class40_2.magnitude += class40_1.magnitude;
									l++;
									mergeStampA[j2] = mergeCycle;
									mergeStampB[l3] = mergeCycle;
								}
							}

						}
					}
				}
			}
		}

		if (l < 3 || !hideFaces)
			return;
		for (int k2 = 0; k2 < modelA.triangleCount; k2++)
			if (mergeStampA[modelA.triangleVertexA[k2]] == mergeCycle
					&& mergeStampA[modelA.triangleVertexB[k2]] == mergeCycle
					&& mergeStampA[modelA.triangleVertexC[k2]] == mergeCycle)
				modelA.triangleDrawType[k2] = -1;

		for (int l2 = 0; l2 < modelB.triangleCount; l2++)
			if (mergeStampB[modelB.triangleVertexA[l2]] == mergeCycle
					&& mergeStampB[modelB.triangleVertexB[l2]] == mergeCycle
					&& mergeStampB[modelB.triangleVertexC[l2]] == mergeCycle)
				modelB.triangleDrawType[l2] = -1;

	}

	public void drawMinimapTile(int pixels[], int pixelOffset, int rowStride, int plane, int x, int y) {
		SceneTile class50_sub3 = tiles[plane][x][y];
		if (class50_sub3 == null)
			return;
		GenericTile class3 = class50_sub3.plainTile;
		if (class3 != null) {
			int j1 = class3.rgbColour;
			if (j1 == 0)
				return;
			for (int k1 = 0; k1 < 4; k1++) {
				pixels[pixelOffset] = j1;
				pixels[pixelOffset + 1] = j1;
				pixels[pixelOffset + 2] = j1;
				pixels[pixelOffset + 3] = j1;
				pixelOffset += rowStride;
			}

			return;
		}
		ComplexTile class20 = class50_sub3.shapedTile;
		if (class20 == null)
			return;
		int l1 = class20.shape;
		int i2 = class20.rotation;
		int j2 = class20.underlayRgb;
		int k2 = class20.overlayRgb;
		int ai1[] = minimapTileShape[l1];
		int ai2[] = minimapTileRotation[i2];
		int l2 = 0;
		if (j2 != 0) {
			for (int i3 = 0; i3 < 4; i3++) {
				pixels[pixelOffset] = ai1[ai2[l2++]] != 0 ? k2 : j2;
				pixels[pixelOffset + 1] = ai1[ai2[l2++]] != 0 ? k2 : j2;
				pixels[pixelOffset + 2] = ai1[ai2[l2++]] != 0 ? k2 : j2;
				pixels[pixelOffset + 3] = ai1[ai2[l2++]] != 0 ? k2 : j2;
				pixelOffset += rowStride;
			}

			return;
		}
		for (int j3 = 0; j3 < 4; j3++) {
			if (ai1[ai2[l2++]] != 0)
				pixels[pixelOffset] = k2;
			if (ai1[ai2[l2++]] != 0)
				pixels[pixelOffset + 1] = k2;
			if (ai1[ai2[l2++]] != 0)
				pixels[pixelOffset + 2] = k2;
			if (ai1[ai2[l2++]] != 0)
				pixels[pixelOffset + 3] = k2;
			pixelOffset += rowStride;
		}

	}

	public static void buildVisibilityMaps(int minZ, int maxZ, int viewportWidth, int viewportHeight,
			int pitchHeights[]) {
		viewportMinX = 0;
		viewportMinY = 0;
		viewportMaxX = viewportWidth;
		viewportMaxY = viewportHeight;
		viewportCenterX = viewportWidth / 2;
		viewportCenterY = viewportHeight / 2;
		boolean aflag[][][][] = new boolean[9][32][53][53];
		for (int j1 = 128; j1 <= 384; j1 += 32) {
			for (int k1 = 0; k1 < 2048; k1 += 64) {
				pitchSine = Model.SINE[j1];
				pitchCosine = Model.COSINE[j1];
				yawSine = Model.SINE[k1];
				yawCosine = Model.COSINE[k1];
				int i2 = (j1 - 128) / 32;
				int k2 = k1 / 64;
				for (int i3 = -26; i3 <= 26; i3++) {
					for (int k3 = -26; k3 <= 26; k3++) {
						int l3 = i3 * 128;
						int j4 = k3 * 128;
						boolean flag1 = false;
						for (int l4 = -minZ; l4 <= maxZ; l4 += 128) {
							if (!isProjectionVisible(j4, l3, pitchHeights[i2] + l4))
								continue;
							flag1 = true;
							break;
						}

						aflag[i2][k2][i3 + 25 + 1][k3 + 25 + 1] = flag1;
					}

				}

			}

		}

		for (int l1 = 0; l1 < 8; l1++) {
			for (int j2 = 0; j2 < 32; j2++) {
				for (int l2 = -25; l2 < 25; l2++) {
					for (int j3 = -25; j3 < 25; j3++) {
						boolean flag = false;
						label0: for (int i4 = -1; i4 <= 1; i4++) {
							for (int k4 = -1; k4 <= 1; k4++) {
								if (aflag[l1][j2][l2 + i4 + 25 + 1][j3 + k4 + 25 + 1])
									flag = true;
								else if (aflag[l1][(j2 + 1) % 31][l2 + i4 + 25 + 1][j3 + k4 + 25 + 1])
									flag = true;
								else if (aflag[l1 + 1][j2][l2 + i4 + 25 + 1][j3 + k4 + 25 + 1]) {
									flag = true;
								} else {
									if (!aflag[l1 + 1][(j2 + 1) % 31][l2 + i4 + 25 + 1][j3 + k4 + 25 + 1])
										continue;
									flag = true;
								}
								break label0;
							}

						}

						visibilityMaps[l1][j2][l2 + 25][j3 + 25] = flag;
					}

				}

			}

		}
	}

	public static boolean isProjectionVisible(int x, int y, int z) {
		int i1 = x * yawSine + y * yawCosine >> 16;
		int j1 = x * yawCosine - y * yawSine >> 16;
		int k1 = z * pitchSine + j1 * pitchCosine >> 16;
		int l1 = z * pitchCosine - j1 * pitchSine >> 16;
		if (k1 < 50 || k1 > 3500)
			return false;
		int i2 = viewportCenterX + (i1 << 9) / k1;
		int j2 = viewportCenterY + (l1 << 9) / k1;
		return i2 >= viewportMinX && i2 <= viewportMaxX && j2 >= viewportMinY && j2 <= viewportMaxY;
	}

	public void setClick(int mx, int my) {
		picking = true;
		mouseX = mx;
		mouseY = my;
		pickedTileX = -1;
		pickedTileY = -1;
	}

	public void render(int cwx, int cwy, int cwz, int plane, int yaw, int pitch) {
		if (cwx < 0)
			cwx = 0;
		else if (cwx >= width * 128)
			cwx = width * 128 - 1;
		if (cwy < 0)
			cwy = 0;
		else if (cwy >= height * 128)
			cwy = height * 128 - 1;
		renderCycle++;
		pitchSine = Model.SINE[pitch];
		pitchCosine = Model.COSINE[pitch];
		yawSine = Model.SINE[yaw];
		yawCosine = Model.COSINE[yaw];
		visibilityMap = visibilityMaps[(pitch - 128) / 32][yaw / 64];
		cameraX = cwx;
		cameraZ = cwz;
		cameraY = cwy;
		cameraTileX = cwx / 128;
		cameraTileY = cwy / 128;
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
		for (int l1 = minPlane; l1 < planeCount; l1++) {
			SceneTile aclass50_sub3[][] = tiles[l1];
			for (int j2 = minTileX; j2 < maxTileX; j2++) {
				for (int l2 = minTileY; l2 < maxTileY; l2++) {
					SceneTile class50_sub3 = aclass50_sub3[j2][l2];
					if (class50_sub3 != null)
						if (class50_sub3.logicHeight > plane
								|| !visibilityMap[(j2 - cameraTileX) + 25][(l2 - cameraTileY) + 25]
										&& tileHeights[l1][j2][l2] - cwz < 2000) {
							class50_sub3.draw = false;
							class50_sub3.visible = false;
							class50_sub3.wallCullDirection = 0;
						} else {
							class50_sub3.draw = true;
							class50_sub3.visible = true;
							if (class50_sub3.interactiveObjectCount > 0)
								class50_sub3.drawEntities = true;
							else
								class50_sub3.drawEntities = false;
							remainingTileCount++;
						}
				}

			}

		}

		for (int i2 = minPlane; i2 < planeCount; i2++) {
			SceneTile aclass50_sub3_1[][] = tiles[i2];
			for (int i3 = -25; i3 <= 0; i3++) {
				int j3 = cameraTileX + i3;
				int l3 = cameraTileX - i3;
				if (j3 >= minTileX || l3 < maxTileX) {
					for (int j4 = -25; j4 <= 0; j4++) {
						int l4 = cameraTileY + j4;
						int j5 = cameraTileY - j4;
						if (j3 >= minTileX) {
							if (l4 >= minTileY) {
								SceneTile class50_sub3_1 = aclass50_sub3_1[j3][l4];
								if (class50_sub3_1 != null && class50_sub3_1.draw)
									renderTile(class50_sub3_1, true);
							}
							if (j5 < maxTileY) {
								SceneTile class50_sub3_2 = aclass50_sub3_1[j3][j5];
								if (class50_sub3_2 != null && class50_sub3_2.draw)
									renderTile(class50_sub3_2, true);
							}
						}
						if (l3 < maxTileX) {
							if (l4 >= minTileY) {
								SceneTile class50_sub3_3 = aclass50_sub3_1[l3][l4];
								if (class50_sub3_3 != null && class50_sub3_3.draw)
									renderTile(class50_sub3_3, true);
							}
							if (j5 < maxTileY) {
								SceneTile class50_sub3_4 = aclass50_sub3_1[l3][j5];
								if (class50_sub3_4 != null && class50_sub3_4.draw)
									renderTile(class50_sub3_4, true);
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

		for (int k2 = minPlane; k2 < planeCount; k2++) {
			SceneTile aclass50_sub3_2[][] = tiles[k2];
			for (int k3 = -25; k3 <= 0; k3++) {
				int i4 = cameraTileX + k3;
				int k4 = cameraTileX - k3;
				if (i4 >= minTileX || k4 < maxTileX) {
					for (int i5 = -25; i5 <= 0; i5++) {
						int k5 = cameraTileY + i5;
						int l5 = cameraTileY - i5;
						if (i4 >= minTileX) {
							if (k5 >= minTileY) {
								SceneTile class50_sub3_5 = aclass50_sub3_2[i4][k5];
								if (class50_sub3_5 != null && class50_sub3_5.draw)
									renderTile(class50_sub3_5, false);
							}
							if (l5 < maxTileY) {
								SceneTile class50_sub3_6 = aclass50_sub3_2[i4][l5];
								if (class50_sub3_6 != null && class50_sub3_6.draw)
									renderTile(class50_sub3_6, false);
							}
						}
						if (k4 < maxTileX) {
							if (k5 >= minTileY) {
								SceneTile class50_sub3_7 = aclass50_sub3_2[k4][k5];
								if (class50_sub3_7 != null && class50_sub3_7.draw)
									renderTile(class50_sub3_7, false);
							}
							if (l5 < maxTileY) {
								SceneTile class50_sub3_8 = aclass50_sub3_2[k4][l5];
								if (class50_sub3_8 != null && class50_sub3_8.draw)
									renderTile(class50_sub3_8, false);
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

	public void renderTile(SceneTile class50_sub3, boolean flag) {
		tileQueue.addLast(class50_sub3);
		do {
			SceneTile class50_sub3_1;
			do {
				class50_sub3_1 = (SceneTile) tileQueue.removeFirst();
				if (class50_sub3_1 == null)
					return;
			} while (!class50_sub3_1.visible);
			int i = class50_sub3_1.x;
			int j = class50_sub3_1.y;
			int k = class50_sub3_1.plane;
			int l = class50_sub3_1.renderLevel;
			SceneTile aclass50_sub3[][] = tiles[k];
			if (class50_sub3_1.draw) {
				if (flag) {
					if (k > 0) {
						SceneTile class50_sub3_2 = tiles[k - 1][i][j];
						if (class50_sub3_2 != null && class50_sub3_2.visible)
							continue;
					}
					if (i <= cameraTileX && i > minTileX) {
						SceneTile class50_sub3_3 = aclass50_sub3[i - 1][j];
						if (class50_sub3_3 != null && class50_sub3_3.visible
								&& (class50_sub3_3.draw || (class50_sub3_1.combinedInteractiveObjectEdgeMask & 1) == 0))
							continue;
					}
					if (i >= cameraTileX && i < maxTileX - 1) {
						SceneTile class50_sub3_4 = aclass50_sub3[i + 1][j];
						if (class50_sub3_4 != null && class50_sub3_4.visible
								&& (class50_sub3_4.draw || (class50_sub3_1.combinedInteractiveObjectEdgeMask & 4) == 0))
							continue;
					}
					if (j <= cameraTileY && j > minTileY) {
						SceneTile class50_sub3_5 = aclass50_sub3[i][j - 1];
						if (class50_sub3_5 != null && class50_sub3_5.visible
								&& (class50_sub3_5.draw || (class50_sub3_1.combinedInteractiveObjectEdgeMask & 8) == 0))
							continue;
					}
					if (j >= cameraTileY && j < maxTileY - 1) {
						SceneTile class50_sub3_6 = aclass50_sub3[i][j + 1];
						if (class50_sub3_6 != null && class50_sub3_6.visible
								&& (class50_sub3_6.draw || (class50_sub3_1.combinedInteractiveObjectEdgeMask & 2) == 0))
							continue;
					}
				} else {
					flag = true;
				}
				class50_sub3_1.draw = false;
				if (class50_sub3_1.tileBelow != null) {
					SceneTile class50_sub3_7 = class50_sub3_1.tileBelow;
					if (class50_sub3_7.plainTile != null) {
						if (!isTileOccluded(0, i, j))
							renderPlainTile(class50_sub3_7.plainTile, 0, i, j, pitchSine, pitchCosine, yawSine,
									yawCosine);
					} else if (class50_sub3_7.shapedTile != null && !isTileOccluded(0, i, j))
						renderShapedTile(class50_sub3_7.shapedTile, i, j, pitchSine, pitchCosine, yawSine, yawCosine);
					Wall class44 = class50_sub3_7.wall;
					if (class44 != null)
						class44.primary.draw(0, pitchSine, pitchCosine, yawSine, yawCosine, class44.x - cameraX,
								class44.z - cameraZ, class44.y - cameraY, class44.uid);
					for (int i2 = 0; i2 < class50_sub3_7.interactiveObjectCount; i2++) {
						InteractiveObject class5 = class50_sub3_7.interactiveObjects[i2];
						if (class5 != null)
							class5.renderable.draw(class5.rotation, pitchSine, pitchCosine, yawSine, yawCosine,
									class5.worldX - cameraX, class5.worldZ - cameraZ, class5.worldY - cameraY,
									class5.uid);
					}

				}
				boolean flag1 = false;
				if (class50_sub3_1.plainTile != null) {
					if (!isTileOccluded(l, i, j)) {
						flag1 = true;
						renderPlainTile(class50_sub3_1.plainTile, l, i, j, pitchSine, pitchCosine, yawSine, yawCosine);
					}
				} else if (class50_sub3_1.shapedTile != null && !isTileOccluded(l, i, j)) {
					flag1 = true;
					renderShapedTile(class50_sub3_1.shapedTile, i, j, pitchSine, pitchCosine, yawSine, yawCosine);
				}
				int j1 = 0;
				int j2 = 0;
				Wall class44_3 = class50_sub3_1.wall;
				WallDecoration class35_1 = class50_sub3_1.wallDecoration;
				if (class44_3 != null || class35_1 != null) {
					if (cameraTileX == i)
						j1++;
					else if (cameraTileX < i)
						j1 += 2;
					if (cameraTileY == j)
						j1 += 3;
					else if (cameraTileY > j)
						j1 += 6;
					j2 = WALL_DRAW_FLAGS[j1];
					class50_sub3_1.wallDrawFlags = WALL_DRAW_FLAGS_2[j1];
				}
				if (class44_3 != null) {
					if ((class44_3.orientation & WALL_CULL_FLAGS[j1]) != 0) {
						if (class44_3.orientation == 16) {
							class50_sub3_1.wallCullDirection = 3;
							class50_sub3_1.wallUncullDirection = WALL_UNCULL_FLAGS_0[j1];
							class50_sub3_1.wallCullOppositeDirection = 3 - class50_sub3_1.wallUncullDirection;
						} else if (class44_3.orientation == 32) {
							class50_sub3_1.wallCullDirection = 6;
							class50_sub3_1.wallUncullDirection = WALL_UNCULL_FLAGS_1[j1];
							class50_sub3_1.wallCullOppositeDirection = 6 - class50_sub3_1.wallUncullDirection;
						} else if (class44_3.orientation == 64) {
							class50_sub3_1.wallCullDirection = 12;
							class50_sub3_1.wallUncullDirection = WALL_UNCULL_FLAGS_2[j1];
							class50_sub3_1.wallCullOppositeDirection = 12 - class50_sub3_1.wallUncullDirection;
						} else {
							class50_sub3_1.wallCullDirection = 9;
							class50_sub3_1.wallUncullDirection = WALL_UNCULL_FLAGS_3[j1];
							class50_sub3_1.wallCullOppositeDirection = 9 - class50_sub3_1.wallUncullDirection;
						}
					} else {
						class50_sub3_1.wallCullDirection = 0;
					}
					if ((class44_3.orientation & j2) != 0 && !isWallOccluded(l, i, j, class44_3.orientation))
						class44_3.primary.draw(0, pitchSine, pitchCosine, yawSine, yawCosine, class44_3.x - cameraX,
								class44_3.z - cameraZ, class44_3.y - cameraY, class44_3.uid);
					if ((class44_3.secondaryOrientation & j2) != 0
							&& !isWallOccluded(l, i, j, class44_3.secondaryOrientation))
						class44_3.secondary.draw(0, pitchSine, pitchCosine, yawSine, yawCosine, class44_3.x - cameraX,
								class44_3.z - cameraZ, class44_3.y - cameraY, class44_3.uid);
				}
				if (class35_1 != null && !isDecorationOccluded(l, i, j, class35_1.renderable.modelHeight))
					if ((class35_1.configBits & j2) != 0)
						class35_1.renderable.draw(class35_1.face, pitchSine, pitchCosine, yawSine, yawCosine,
								class35_1.x - cameraX, class35_1.z - cameraZ, class35_1.y - cameraY, class35_1.uid);
					else if ((class35_1.configBits & 0x300) != 0) {
						int j4 = class35_1.x - cameraX;
						int l5 = class35_1.z - cameraZ;
						int k6 = class35_1.y - cameraY;
						int i8 = class35_1.face;
						int k9;
						if (i8 == 1 || i8 == 2)
							k9 = -j4;
						else
							k9 = j4;
						int k10;
						if (i8 == 2 || i8 == 3)
							k10 = -k6;
						else
							k10 = k6;
						if ((class35_1.configBits & 0x100) != 0 && k10 < k9) {
							int i11 = j4 + WALL_DECORATION_INSET_X[i8];
							int k11 = k6 + WALL_DECORATION_INSET_Y[i8];
							class35_1.renderable.draw(i8 * 512 + 256, pitchSine, pitchCosine, yawSine, yawCosine, i11,
									l5, k11, class35_1.uid);
						}
						if ((class35_1.configBits & 0x200) != 0 && k10 > k9) {
							int j11 = j4 + WALL_DECORATION_OUTSET_X[i8];
							int l11 = k6 + WALL_DECORATION_OUTSET_Y[i8];
							class35_1.renderable.draw(i8 * 512 + 1280 & 0x7ff, pitchSine, pitchCosine, yawSine,
									yawCosine, j11, l5, l11, class35_1.uid);
						}
					}
				if (flag1) {
					FloorDecoration class28 = class50_sub3_1.floorDecoration;
					if (class28 != null)
						class28.renderable.draw(0, pitchSine, pitchCosine, yawSine, yawCosine, class28.x - cameraX,
								class28.z - cameraZ, class28.y - cameraY, class28.uid);
					GroundItemTile class10_1 = class50_sub3_1.groundItemTile;
					if (class10_1 != null && class10_1.heightOffset == 0) {
						if (class10_1.secondGroundItem != null)
							class10_1.secondGroundItem.draw(0, pitchSine, pitchCosine, yawSine, yawCosine,
									class10_1.x - cameraX, class10_1.z - cameraZ, class10_1.y - cameraY, class10_1.uid);
						if (class10_1.thirdGroundItem != null)
							class10_1.thirdGroundItem.draw(0, pitchSine, pitchCosine, yawSine, yawCosine,
									class10_1.x - cameraX, class10_1.z - cameraZ, class10_1.y - cameraY, class10_1.uid);
						if (class10_1.firstGroundItem != null)
							class10_1.firstGroundItem.draw(0, pitchSine, pitchCosine, yawSine, yawCosine,
									class10_1.x - cameraX, class10_1.z - cameraZ, class10_1.y - cameraY, class10_1.uid);
					}
				}
				int k4 = class50_sub3_1.combinedInteractiveObjectEdgeMask;
				if (k4 != 0) {
					if (i < cameraTileX && (k4 & 4) != 0) {
						SceneTile class50_sub3_17 = aclass50_sub3[i + 1][j];
						if (class50_sub3_17 != null && class50_sub3_17.visible)
							tileQueue.addLast(class50_sub3_17);
					}
					if (j < cameraTileY && (k4 & 2) != 0) {
						SceneTile class50_sub3_18 = aclass50_sub3[i][j + 1];
						if (class50_sub3_18 != null && class50_sub3_18.visible)
							tileQueue.addLast(class50_sub3_18);
					}
					if (i > cameraTileX && (k4 & 1) != 0) {
						SceneTile class50_sub3_19 = aclass50_sub3[i - 1][j];
						if (class50_sub3_19 != null && class50_sub3_19.visible)
							tileQueue.addLast(class50_sub3_19);
					}
					if (j > cameraTileY && (k4 & 8) != 0) {
						SceneTile class50_sub3_20 = aclass50_sub3[i][j - 1];
						if (class50_sub3_20 != null && class50_sub3_20.visible)
							tileQueue.addLast(class50_sub3_20);
					}
				}
			}
			if (class50_sub3_1.wallCullDirection != 0) {
				boolean flag2 = true;
				for (int k1 = 0; k1 < class50_sub3_1.interactiveObjectCount; k1++) {
					if (class50_sub3_1.interactiveObjects[k1].lastDrawnCycle == renderCycle
							|| (class50_sub3_1.interactiveObjectEdgeMasks[k1]
									& class50_sub3_1.wallCullDirection) != class50_sub3_1.wallUncullDirection)
						continue;
					flag2 = false;
					break;
				}

				if (flag2) {
					Wall class44_1 = class50_sub3_1.wall;
					if (!isWallOccluded(l, i, j, class44_1.orientation))
						class44_1.primary.draw(0, pitchSine, pitchCosine, yawSine, yawCosine, class44_1.x - cameraX,
								class44_1.z - cameraZ, class44_1.y - cameraY, class44_1.uid);
					class50_sub3_1.wallCullDirection = 0;
				}
			}
			if (class50_sub3_1.drawEntities)
				try {
					int i1 = class50_sub3_1.interactiveObjectCount;
					class50_sub3_1.drawEntities = false;
					int l1 = 0;
					label0: for (int k2 = 0; k2 < i1; k2++) {
						InteractiveObject class5_1 = class50_sub3_1.interactiveObjects[k2];
						if (class5_1.lastDrawnCycle == renderCycle)
							continue;
						for (int k3 = class5_1.tileLeft; k3 <= class5_1.tileRight; k3++) {
							for (int l4 = class5_1.tileTop; l4 <= class5_1.tileBottom; l4++) {
								SceneTile class50_sub3_21 = aclass50_sub3[k3][l4];
								if (class50_sub3_21.draw) {
									class50_sub3_1.drawEntities = true;
								} else {
									if (class50_sub3_21.wallCullDirection == 0)
										continue;
									int l6 = 0;
									if (k3 > class5_1.tileLeft)
										l6++;
									if (k3 < class5_1.tileRight)
										l6 += 4;
									if (l4 > class5_1.tileTop)
										l6 += 8;
									if (l4 < class5_1.tileBottom)
										l6 += 2;
									if ((l6 & class50_sub3_21.wallCullDirection) != class50_sub3_1.wallCullOppositeDirection)
										continue;
									class50_sub3_1.drawEntities = true;
								}
								continue label0;
							}

						}

						renderInteractiveObjects[l1++] = class5_1;
						int i5 = cameraTileX - class5_1.tileLeft;
						int i6 = class5_1.tileRight - cameraTileX;
						if (i6 > i5)
							i5 = i6;
						int i7 = cameraTileY - class5_1.tileTop;
						int j8 = class5_1.tileBottom - cameraTileY;
						if (j8 > i7)
							class5_1.drawPriority = i5 + j8;
						else
							class5_1.drawPriority = i5 + i7;
					}

					while (l1 > 0) {
						int i3 = -50;
						int l3 = -1;
						for (int j5 = 0; j5 < l1; j5++) {
							InteractiveObject class5_2 = renderInteractiveObjects[j5];
							if (class5_2.lastDrawnCycle != renderCycle)
								if (class5_2.drawPriority > i3) {
									i3 = class5_2.drawPriority;
									l3 = j5;
								} else if (class5_2.drawPriority == i3) {
									int j7 = class5_2.worldX - cameraX;
									int k8 = class5_2.worldY - cameraY;
									int l9 = renderInteractiveObjects[l3].worldX - cameraX;
									int l10 = renderInteractiveObjects[l3].worldY - cameraY;
									if (j7 * j7 + k8 * k8 > l9 * l9 + l10 * l10)
										l3 = j5;
								}
						}

						if (l3 == -1)
							break;
						InteractiveObject class5_3 = renderInteractiveObjects[l3];
						class5_3.lastDrawnCycle = renderCycle;
						if (!isAreaOccluded(l, class5_3.tileLeft, class5_3.tileRight, class5_3.tileTop,
								class5_3.tileBottom, class5_3.renderable.modelHeight))
							class5_3.renderable.draw(class5_3.rotation, pitchSine, pitchCosine, yawSine, yawCosine,
									class5_3.worldX - cameraX, class5_3.worldZ - cameraZ, class5_3.worldY - cameraY,
									class5_3.uid);
						for (int k7 = class5_3.tileLeft; k7 <= class5_3.tileRight; k7++) {
							for (int l8 = class5_3.tileTop; l8 <= class5_3.tileBottom; l8++) {
								SceneTile class50_sub3_22 = aclass50_sub3[k7][l8];
								if (class50_sub3_22.wallCullDirection != 0)
									tileQueue.addLast(class50_sub3_22);
								else if ((k7 != i || l8 != j) && class50_sub3_22.visible)
									tileQueue.addLast(class50_sub3_22);
							}

						}

					}
					if (class50_sub3_1.drawEntities)
						continue;
				} catch (Exception _ex) {
					class50_sub3_1.drawEntities = false;
				}
			if (!class50_sub3_1.visible || class50_sub3_1.wallCullDirection != 0)
				continue;
			if (i <= cameraTileX && i > minTileX) {
				SceneTile class50_sub3_8 = aclass50_sub3[i - 1][j];
				if (class50_sub3_8 != null && class50_sub3_8.visible)
					continue;
			}
			if (i >= cameraTileX && i < maxTileX - 1) {
				SceneTile class50_sub3_9 = aclass50_sub3[i + 1][j];
				if (class50_sub3_9 != null && class50_sub3_9.visible)
					continue;
			}
			if (j <= cameraTileY && j > minTileY) {
				SceneTile class50_sub3_10 = aclass50_sub3[i][j - 1];
				if (class50_sub3_10 != null && class50_sub3_10.visible)
					continue;
			}
			if (j >= cameraTileY && j < maxTileY - 1) {
				SceneTile class50_sub3_11 = aclass50_sub3[i][j + 1];
				if (class50_sub3_11 != null && class50_sub3_11.visible)
					continue;
			}
			class50_sub3_1.visible = false;
			remainingTileCount--;
			GroundItemTile class10 = class50_sub3_1.groundItemTile;
			if (class10 != null && class10.heightOffset != 0) {
				if (class10.secondGroundItem != null)
					class10.secondGroundItem.draw(0, pitchSine, pitchCosine, yawSine, yawCosine, class10.x - cameraX,
							class10.z - cameraZ - class10.heightOffset, class10.y - cameraY, class10.uid);
				if (class10.thirdGroundItem != null)
					class10.thirdGroundItem.draw(0, pitchSine, pitchCosine, yawSine, yawCosine, class10.x - cameraX,
							class10.z - cameraZ - class10.heightOffset, class10.y - cameraY, class10.uid);
				if (class10.firstGroundItem != null)
					class10.firstGroundItem.draw(0, pitchSine, pitchCosine, yawSine, yawCosine, class10.x - cameraX,
							class10.z - cameraZ - class10.heightOffset, class10.y - cameraY, class10.uid);
			}
			if (class50_sub3_1.wallDrawFlags != 0) {
				WallDecoration class35 = class50_sub3_1.wallDecoration;
				if (class35 != null && !isDecorationOccluded(l, i, j, class35.renderable.modelHeight))
					if ((class35.configBits & class50_sub3_1.wallDrawFlags) != 0)
						class35.renderable.draw(class35.face, pitchSine, pitchCosine, yawSine, yawCosine,
								class35.x - cameraX, class35.z - cameraZ, class35.y - cameraY, class35.uid);
					else if ((class35.configBits & 0x300) != 0) {
						int l2 = class35.x - cameraX;
						int j3 = class35.z - cameraZ;
						int i4 = class35.y - cameraY;
						int k5 = class35.face;
						int j6;
						if (k5 == 1 || k5 == 2)
							j6 = -l2;
						else
							j6 = l2;
						int l7;
						if (k5 == 2 || k5 == 3)
							l7 = -i4;
						else
							l7 = i4;
						if ((class35.configBits & 0x100) != 0 && l7 >= j6) {
							int i9 = l2 + WALL_DECORATION_INSET_X[k5];
							int i10 = i4 + WALL_DECORATION_INSET_Y[k5];
							class35.renderable.draw(k5 * 512 + 256, pitchSine, pitchCosine, yawSine, yawCosine, i9, j3,
									i10, class35.uid);
						}
						if ((class35.configBits & 0x200) != 0 && l7 <= j6) {
							int j9 = l2 + WALL_DECORATION_OUTSET_X[k5];
							int j10 = i4 + WALL_DECORATION_OUTSET_Y[k5];
							class35.renderable.draw(k5 * 512 + 1280 & 0x7ff, pitchSine, pitchCosine, yawSine, yawCosine,
									j9, j3, j10, class35.uid);
						}
					}
				Wall class44_2 = class50_sub3_1.wall;
				if (class44_2 != null) {
					if ((class44_2.secondaryOrientation & class50_sub3_1.wallDrawFlags) != 0
							&& !isWallOccluded(l, i, j, class44_2.secondaryOrientation))
						class44_2.secondary.draw(0, pitchSine, pitchCosine, yawSine, yawCosine, class44_2.x - cameraX,
								class44_2.z - cameraZ, class44_2.y - cameraY, class44_2.uid);
					if ((class44_2.orientation & class50_sub3_1.wallDrawFlags) != 0
							&& !isWallOccluded(l, i, j, class44_2.orientation))
						class44_2.primary.draw(0, pitchSine, pitchCosine, yawSine, yawCosine, class44_2.x - cameraX,
								class44_2.z - cameraZ, class44_2.y - cameraY, class44_2.uid);
				}
			}
			if (k < planeCount - 1) {
				SceneTile class50_sub3_12 = tiles[k + 1][i][j];
				if (class50_sub3_12 != null && class50_sub3_12.visible)
					tileQueue.addLast(class50_sub3_12);
			}
			if (i < cameraTileX) {
				SceneTile class50_sub3_13 = aclass50_sub3[i + 1][j];
				if (class50_sub3_13 != null && class50_sub3_13.visible)
					tileQueue.addLast(class50_sub3_13);
			}
			if (j < cameraTileY) {
				SceneTile class50_sub3_14 = aclass50_sub3[i][j + 1];
				if (class50_sub3_14 != null && class50_sub3_14.visible)
					tileQueue.addLast(class50_sub3_14);
			}
			if (i > cameraTileX) {
				SceneTile class50_sub3_15 = aclass50_sub3[i - 1][j];
				if (class50_sub3_15 != null && class50_sub3_15.visible)
					tileQueue.addLast(class50_sub3_15);
			}
			if (j > cameraTileY) {
				SceneTile class50_sub3_16 = aclass50_sub3[i][j - 1];
				if (class50_sub3_16 != null && class50_sub3_16.visible)
					tileQueue.addLast(class50_sub3_16);
			}
		} while (true);
	}

	public void renderPlainTile(GenericTile tile, int plane, int tileX, int tiley, int pitchSine, int pitchCosine,
			int yawSine, int yawCosine) {
		int l1;
		int i2 = l1 = (tileX << 7) - cameraX;
		int j2;
		int k2 = j2 = (tiley << 7) - cameraY;
		int l2;
		int i3 = l2 = i2 + 128;
		int j3;
		int k3 = j3 = k2 + 128;
		int l3 = tileHeights[plane][tileX][tiley] - cameraZ;
		int i4 = tileHeights[plane][tileX + 1][tiley] - cameraZ;
		int j4 = tileHeights[plane][tileX + 1][tiley + 1] - cameraZ;
		int k4 = tileHeights[plane][tileX][tiley + 1] - cameraZ;
		int l4 = k2 * yawSine + i2 * yawCosine >> 16;
		k2 = k2 * yawCosine - i2 * yawSine >> 16;
		i2 = l4;
		l4 = l3 * pitchCosine - k2 * pitchSine >> 16;
		k2 = l3 * pitchSine + k2 * pitchCosine >> 16;
		l3 = l4;
		if (k2 < 50)
			return;
		l4 = j2 * yawSine + i3 * yawCosine >> 16;
		j2 = j2 * yawCosine - i3 * yawSine >> 16;
		i3 = l4;
		l4 = i4 * pitchCosine - j2 * pitchSine >> 16;
		j2 = i4 * pitchSine + j2 * pitchCosine >> 16;
		i4 = l4;
		if (j2 < 50)
			return;
		l4 = k3 * yawSine + l2 * yawCosine >> 16;
		k3 = k3 * yawCosine - l2 * yawSine >> 16;
		l2 = l4;
		l4 = j4 * pitchCosine - k3 * pitchSine >> 16;
		k3 = j4 * pitchSine + k3 * pitchCosine >> 16;
		j4 = l4;
		if (k3 < 50)
			return;
		l4 = j3 * yawSine + l1 * yawCosine >> 16;
		j3 = j3 * yawCosine - l1 * yawSine >> 16;
		l1 = l4;
		l4 = k4 * pitchCosine - j3 * pitchSine >> 16;
		j3 = k4 * pitchSine + j3 * pitchCosine >> 16;
		k4 = l4;
		if (j3 < 50)
			return;
		int i5 = Rasterizer3D.centerX + (i2 << 9) / k2;
		int j5 = Rasterizer3D.centerY + (l3 << 9) / k2;
		int k5 = Rasterizer3D.centerX + (i3 << 9) / j2;
		int l5 = Rasterizer3D.centerY + (i4 << 9) / j2;
		int i6 = Rasterizer3D.centerX + (l2 << 9) / k3;
		int j6 = Rasterizer3D.centerY + (j4 << 9) / k3;
		int k6 = Rasterizer3D.centerX + (l1 << 9) / j3;
		int l6 = Rasterizer3D.centerY + (k4 << 9) / j3;
		Rasterizer3D.alpha = 0;
		if ((i6 - k6) * (l5 - l6) - (j6 - l6) * (k5 - k6) > 0) {
			Rasterizer3D.restrictEdges = false;
			if (i6 < 0 || k6 < 0 || k5 < 0 || i6 > Rasterizer.viewportRx || k6 > Rasterizer.viewportRx
					|| k5 > Rasterizer.viewportRx)
				Rasterizer3D.restrictEdges = true;
			if (picking && containsScreenPoint(mouseX, mouseY, j6, l6, l5, i6, k6, k5)) {
				pickedTileX = tileX;
				pickedTileY = tiley;
			}
			if (tile.texture == -1) {
				if (tile.colourC != 0xbc614e)
					Rasterizer3D.drawGouraudTriangle(j6, l6, l5, i6, k6, k5, tile.colourC, tile.colourD, tile.colourB);
			} else if (!lowMemory) {
				if (tile.flat)
					Rasterizer3D.drawTexturedTriangle(j6, l6, l5, i6, k6, k5, tile.colourC, tile.colourD, tile.colourB,
							i2, i3, l1, l3, i4, k4, k2, j2, j3, tile.texture);
				else
					Rasterizer3D.drawTexturedTriangle(j6, l6, l5, i6, k6, k5, tile.colourC, tile.colourD, tile.colourB,
							l2, l1, i3, j4, k4, i4, k3, j3, j2, tile.texture);
			} else {
				int i7 = TEXTURE_COLORS[tile.texture];
				Rasterizer3D.drawGouraudTriangle(j6, l6, l5, i6, k6, k5, mixTextureColor(tile.colourC, i7),
						mixTextureColor(tile.colourD, i7), mixTextureColor(tile.colourB, i7));
			}
		}
		if ((i5 - k5) * (l6 - l5) - (j5 - l5) * (k6 - k5) > 0) {
			Rasterizer3D.restrictEdges = false;
			if (i5 < 0 || k5 < 0 || k6 < 0 || i5 > Rasterizer.viewportRx || k5 > Rasterizer.viewportRx
					|| k6 > Rasterizer.viewportRx)
				Rasterizer3D.restrictEdges = true;
			if (picking && containsScreenPoint(mouseX, mouseY, j5, l5, l6, i5, k5, k6)) {
				pickedTileX = tileX;
				pickedTileY = tiley;
			}
			if (tile.texture == -1) {
				if (tile.colourA != 0xbc614e) {
					Rasterizer3D.drawGouraudTriangle(j5, l5, l6, i5, k5, k6, tile.colourA, tile.colourB, tile.colourD);
					return;
				}
			} else {
				if (!lowMemory) {
					Rasterizer3D.drawTexturedTriangle(j5, l5, l6, i5, k5, k6, tile.colourA, tile.colourB, tile.colourD,
							i2, i3, l1, l3, i4, k4, k2, j2, j3, tile.texture);
					return;
				}
				int j7 = TEXTURE_COLORS[tile.texture];
				Rasterizer3D.drawGouraudTriangle(j5, l5, l6, i5, k5, k6, mixTextureColor(tile.colourA, j7),
						mixTextureColor(tile.colourB, j7), mixTextureColor(tile.colourD, j7));
			}
		}
	}

	public void renderShapedTile(ComplexTile tile, int tileX, int tileY, int pitchSine, int pitchCosine, int yawSine,
			int yawCosine) {
		int k1 = tile.vertexX.length;
		for (int l1 = 0; l1 < k1; l1++) {
			int i2 = tile.vertexX[l1] - cameraX;
			int k2 = tile.vertexY[l1] - cameraZ;
			int i3 = tile.vertexZ[l1] - cameraY;
			int k3 = i3 * yawSine + i2 * yawCosine >> 16;
			i3 = i3 * yawCosine - i2 * yawSine >> 16;
			i2 = k3;
			k3 = k2 * pitchCosine - i3 * pitchSine >> 16;
			i3 = k2 * pitchSine + i3 * pitchCosine >> 16;
			k2 = k3;
			if (i3 < 50)
				return;
			if (tile.triangleTextures != null) {
				ComplexTile.VIEW_X[l1] = i2;
				ComplexTile.VIEW_Y[l1] = k2;
				ComplexTile.VIEW_Z[l1] = i3;
			}
			ComplexTile.SCREEN_X[l1] = Rasterizer3D.centerX + (i2 << 9) / i3;
			ComplexTile.SCREEN_Y[l1] = Rasterizer3D.centerY + (k2 << 9) / i3;
		}

		Rasterizer3D.alpha = 0;
		k1 = tile.triangleVertexA.length;
		for (int j2 = 0; j2 < k1; j2++) {
			int l2 = tile.triangleVertexA[j2];
			int j3 = tile.triangleVertexB[j2];
			int l3 = tile.triangleVertexC[j2];
			int i4 = ComplexTile.SCREEN_X[l2];
			int j4 = ComplexTile.SCREEN_X[j3];
			int k4 = ComplexTile.SCREEN_X[l3];
			int l4 = ComplexTile.SCREEN_Y[l2];
			int i5 = ComplexTile.SCREEN_Y[j3];
			int j5 = ComplexTile.SCREEN_Y[l3];
			if ((i4 - j4) * (j5 - i5) - (l4 - i5) * (k4 - j4) > 0) {
				Rasterizer3D.restrictEdges = false;
				if (i4 < 0 || j4 < 0 || k4 < 0 || i4 > Rasterizer.viewportRx || j4 > Rasterizer.viewportRx
						|| k4 > Rasterizer.viewportRx)
					Rasterizer3D.restrictEdges = true;
				if (picking && containsScreenPoint(mouseX, mouseY, l4, i5, j5, i4, j4, k4)) {
					pickedTileX = tileX;
					pickedTileY = tileY;
				}
				if (tile.triangleTextures == null || tile.triangleTextures[j2] == -1) {
					if (tile.triangleHslA[j2] != 0xbc614e)
						Rasterizer3D.drawGouraudTriangle(l4, i5, j5, i4, j4, k4, tile.triangleHslA[j2],
								tile.triangleHslB[j2], tile.triangleHslC[j2]);
				} else if (!lowMemory) {
					if (tile.flat)
						Rasterizer3D.drawTexturedTriangle(l4, i5, j5, i4, j4, k4, tile.triangleHslA[j2],
								tile.triangleHslB[j2], tile.triangleHslC[j2], ComplexTile.VIEW_X[0],
								ComplexTile.VIEW_X[1], ComplexTile.VIEW_X[3], ComplexTile.VIEW_Y[0],
								ComplexTile.VIEW_Y[1], ComplexTile.VIEW_Y[3], ComplexTile.VIEW_Z[0],
								ComplexTile.VIEW_Z[1], ComplexTile.VIEW_Z[3], tile.triangleTextures[j2]);
					else
						Rasterizer3D.drawTexturedTriangle(l4, i5, j5, i4, j4, k4, tile.triangleHslA[j2],
								tile.triangleHslB[j2], tile.triangleHslC[j2], ComplexTile.VIEW_X[l2],
								ComplexTile.VIEW_X[j3], ComplexTile.VIEW_X[l3], ComplexTile.VIEW_Y[l2],
								ComplexTile.VIEW_Y[j3], ComplexTile.VIEW_Y[l3], ComplexTile.VIEW_Z[l2],
								ComplexTile.VIEW_Z[j3], ComplexTile.VIEW_Z[l3], tile.triangleTextures[j2]);
				} else {
					int k5 = TEXTURE_COLORS[tile.triangleTextures[j2]];
					Rasterizer3D.drawGouraudTriangle(l4, i5, j5, i4, j4, k4, mixTextureColor(tile.triangleHslA[j2], k5),
							mixTextureColor(tile.triangleHslB[j2], k5), mixTextureColor(tile.triangleHslC[j2], k5));
				}
			}
		}

	}

	public int mixTextureColor(int lightness, int baseColor) {
		lightness = 127 - lightness;
		lightness = (lightness * (baseColor & 0x7f)) / 160;
		if (lightness < 2)
			lightness = 2;
		else if (lightness > 126)
			lightness = 126;
		return (baseColor & 0xff80) + lightness;
	}

	public boolean containsScreenPoint(int px, int py, int ya, int yb, int yc, int xa, int xb, int xc) {
		if (py < ya && py < yb && py < yc)
			return false;
		if (py > ya && py > yb && py > yc)
			return false;
		if (px < xa && px < xb && px < xc)
			return false;
		if (px > xa && px > xb && px > xc)
			return false;
		int i2 = (py - ya) * (xb - xa) - (px - xa) * (yb - ya);
		int j2 = (py - yc) * (xa - xc) - (px - xc) * (ya - yc);
		int k2 = (py - yb) * (xc - xb) - (px - xb) * (yc - yb);
		return i2 * k2 > 0 && k2 * j2 > 0;
	}

	public void processOccluders() {
		int j = occluderCounts[renderPlane];
		SceneCluster aclass39[] = occluders[renderPlane];
		activeOccluderCount = 0;
		for (int k = 0; k < j; k++) {
			SceneCluster class39 = aclass39[k];
			if (class39.type == 1) {
				int l = (class39.minTileX - cameraTileX) + 25;
				if (l < 0 || l > 50)
					continue;
				int k1 = (class39.minTileY - cameraTileY) + 25;
				if (k1 < 0)
					k1 = 0;
				int j2 = (class39.maxTileY - cameraTileY) + 25;
				if (j2 > 50)
					j2 = 50;
				boolean flag = false;
				while (k1 <= j2)
					if (visibilityMap[l][k1++]) {
						flag = true;
						break;
					}
				if (!flag)
					continue;
				int j3 = cameraX - class39.minWorldX;
				if (j3 > 32) {
					class39.projectionDirection = 1;
				} else {
					if (j3 >= -32)
						continue;
					class39.projectionDirection = 2;
					j3 = -j3;
				}
				class39.minYGradient = (class39.minWorldY - cameraY << 8) / j3;
				class39.maxYGradient = (class39.maxWorldY - cameraY << 8) / j3;
				class39.minZGradient = (class39.minWorldZ - cameraZ << 8) / j3;
				class39.maxZGradient = (class39.maxWorldZ - cameraZ << 8) / j3;
				activeOccluders[activeOccluderCount++] = class39;
				continue;
			}
			if (class39.type == 2) {
				int i1 = (class39.minTileY - cameraTileY) + 25;
				if (i1 < 0 || i1 > 50)
					continue;
				int l1 = (class39.minTileX - cameraTileX) + 25;
				if (l1 < 0)
					l1 = 0;
				int k2 = (class39.maxTileX - cameraTileX) + 25;
				if (k2 > 50)
					k2 = 50;
				boolean flag1 = false;
				while (l1 <= k2)
					if (visibilityMap[l1++][i1]) {
						flag1 = true;
						break;
					}
				if (!flag1)
					continue;
				int k3 = cameraY - class39.minWorldY;
				if (k3 > 32) {
					class39.projectionDirection = 3;
				} else {
					if (k3 >= -32)
						continue;
					class39.projectionDirection = 4;
					k3 = -k3;
				}
				class39.minXGradient = (class39.minWorldX - cameraX << 8) / k3;
				class39.maxXGradient = (class39.maxWorldX - cameraX << 8) / k3;
				class39.minZGradient = (class39.minWorldZ - cameraZ << 8) / k3;
				class39.maxZGradient = (class39.maxWorldZ - cameraZ << 8) / k3;
				activeOccluders[activeOccluderCount++] = class39;
			} else if (class39.type == 4) {
				int j1 = class39.minWorldZ - cameraZ;
				if (j1 > 128) {
					int i2 = (class39.minTileY - cameraTileY) + 25;
					if (i2 < 0)
						i2 = 0;
					int l2 = (class39.maxTileY - cameraTileY) + 25;
					if (l2 > 50)
						l2 = 50;
					if (i2 <= l2) {
						int i3 = (class39.minTileX - cameraTileX) + 25;
						if (i3 < 0)
							i3 = 0;
						int l3 = (class39.maxTileX - cameraTileX) + 25;
						if (l3 > 50)
							l3 = 50;
						boolean flag2 = false;
						label0: for (int i4 = i3; i4 <= l3; i4++) {
							for (int j4 = i2; j4 <= l2; j4++) {
								if (!visibilityMap[i4][j4])
									continue;
								flag2 = true;
								break label0;
							}

						}

						if (flag2) {
							class39.projectionDirection = 5;
							class39.minXGradient = (class39.minWorldX - cameraX << 8) / j1;
							class39.maxXGradient = (class39.maxWorldX - cameraX << 8) / j1;
							class39.minYGradient = (class39.minWorldY - cameraY << 8) / j1;
							class39.maxYGradient = (class39.maxWorldY - cameraY << 8) / j1;
							activeOccluders[activeOccluderCount++] = class39;
						}
					}
				}
			}
		}

	}

	public boolean isTileOccluded(int p, int x, int y) {
		int l = tileOcclusionCycles[p][x][y];
		if (l == -renderCycle)
			return false;
		if (l == renderCycle)
			return true;
		int i1 = x << 7;
		int j1 = y << 7;
		if (isPointOccluded(i1 + 1, tileHeights[p][x][y], j1 + 1)
				&& isPointOccluded((i1 + 128) - 1, tileHeights[p][x + 1][y], j1 + 1)
				&& isPointOccluded((i1 + 128) - 1, tileHeights[p][x + 1][y + 1], (j1 + 128) - 1)
				&& isPointOccluded(i1 + 1, tileHeights[p][x][y + 1], (j1 + 128) - 1)) {
			tileOcclusionCycles[p][x][y] = renderCycle;
			return true;
		} else {
			tileOcclusionCycles[p][x][y] = -renderCycle;
			return false;
		}
	}

	public boolean isWallOccluded(int p, int x, int y, int orientation) {
		if (!isTileOccluded(p, x, y))
			return false;
		int i1 = x << 7;
		int j1 = y << 7;
		int k1 = tileHeights[p][x][y] - 1;
		int l1 = k1 - 120;
		int i2 = k1 - 230;
		int j2 = k1 - 238;
		if (orientation < 16) {
			if (orientation == 1) {
				if (i1 > cameraX) {
					if (!isPointOccluded(i1, k1, j1))
						return false;
					if (!isPointOccluded(i1, k1, j1 + 128))
						return false;
				}
				if (p > 0) {
					if (!isPointOccluded(i1, l1, j1))
						return false;
					if (!isPointOccluded(i1, l1, j1 + 128))
						return false;
				}
				if (!isPointOccluded(i1, i2, j1))
					return false;
				return isPointOccluded(i1, i2, j1 + 128);
			}
			if (orientation == 2) {
				if (j1 < cameraY) {
					if (!isPointOccluded(i1, k1, j1 + 128))
						return false;
					if (!isPointOccluded(i1 + 128, k1, j1 + 128))
						return false;
				}
				if (p > 0) {
					if (!isPointOccluded(i1, l1, j1 + 128))
						return false;
					if (!isPointOccluded(i1 + 128, l1, j1 + 128))
						return false;
				}
				if (!isPointOccluded(i1, i2, j1 + 128))
					return false;
				return isPointOccluded(i1 + 128, i2, j1 + 128);
			}
			if (orientation == 4) {
				if (i1 < cameraX) {
					if (!isPointOccluded(i1 + 128, k1, j1))
						return false;
					if (!isPointOccluded(i1 + 128, k1, j1 + 128))
						return false;
				}
				if (p > 0) {
					if (!isPointOccluded(i1 + 128, l1, j1))
						return false;
					if (!isPointOccluded(i1 + 128, l1, j1 + 128))
						return false;
				}
				if (!isPointOccluded(i1 + 128, i2, j1))
					return false;
				return isPointOccluded(i1 + 128, i2, j1 + 128);
			}
			if (orientation == 8) {
				if (j1 > cameraY) {
					if (!isPointOccluded(i1, k1, j1))
						return false;
					if (!isPointOccluded(i1 + 128, k1, j1))
						return false;
				}
				if (p > 0) {
					if (!isPointOccluded(i1, l1, j1))
						return false;
					if (!isPointOccluded(i1 + 128, l1, j1))
						return false;
				}
				if (!isPointOccluded(i1, i2, j1))
					return false;
				return isPointOccluded(i1 + 128, i2, j1);
			}
		}
		if (!isPointOccluded(i1 + 64, j2, j1 + 64))
			return false;
		if (orientation == 16)
			return isPointOccluded(i1, i2, j1 + 128);
		if (orientation == 32)
			return isPointOccluded(i1 + 128, i2, j1 + 128);
		if (orientation == 64)
			return isPointOccluded(i1 + 128, i2, j1);
		if (orientation == 128) {
			return isPointOccluded(i1, i2, j1);
		} else {
			System.out.println("Warning unsupported wall type");
			return true;
		}
	}

	public boolean isDecorationOccluded(int plane, int x, int y, int modelHeight) {
		if (!isTileOccluded(plane, x, y))
			return false;
		int i1 = x << 7;
		int j1 = y << 7;
		return isPointOccluded(i1 + 1, tileHeights[plane][x][y] - modelHeight, j1 + 1)
				&& isPointOccluded((i1 + 128) - 1, tileHeights[plane][x + 1][y] - modelHeight, j1 + 1)
				&& isPointOccluded((i1 + 128) - 1, tileHeights[plane][x + 1][y + 1] - modelHeight, (j1 + 128) - 1)
				&& isPointOccluded(i1 + 1, tileHeights[plane][x][y + 1] - modelHeight, (j1 + 128) - 1);
	}

	public boolean isAreaOccluded(int plane, int minX, int maxX, int minY, int maxY, int modelHeight) {
		if (minX == maxX && minY == maxY) {
			if (!isTileOccluded(plane, minX, minY))
				return false;
			int k1 = minX << 7;
			int i2 = minY << 7;
			return isPointOccluded(k1 + 1, tileHeights[plane][minX][minY] - modelHeight, i2 + 1)
					&& isPointOccluded((k1 + 128) - 1, tileHeights[plane][minX + 1][minY] - modelHeight, i2 + 1)
					&& isPointOccluded((k1 + 128) - 1, tileHeights[plane][minX + 1][minY + 1] - modelHeight,
							(i2 + 128) - 1)
					&& isPointOccluded(k1 + 1, tileHeights[plane][minX][minY + 1] - modelHeight, (i2 + 128) - 1);
		}
		for (int l1 = minX; l1 <= maxX; l1++) {
			for (int j2 = minY; j2 <= maxY; j2++)
				if (tileOcclusionCycles[plane][l1][j2] == -renderCycle)
					return false;

		}

		int k2 = (minX << 7) + 1;
		int l2 = (minY << 7) + 2;
		int i3 = tileHeights[plane][minX][minY] - modelHeight;
		if (!isPointOccluded(k2, i3, l2))
			return false;
		int j3 = (maxX << 7) - 1;
		if (!isPointOccluded(j3, i3, l2))
			return false;
		int k3 = (maxY << 7) - 1;
		if (!isPointOccluded(k2, i3, k3))
			return false;
		return isPointOccluded(j3, i3, k3);
	}

	public boolean isPointOccluded(int worldX, int worldZ, int worldY) {
		for (int l = 0; l < activeOccluderCount; l++) {
			SceneCluster class39 = activeOccluders[l];
			if (class39.projectionDirection == 1) {
				int i1 = class39.minWorldX - worldX;
				if (i1 > 0) {
					int j2 = class39.minWorldY + (class39.minYGradient * i1 >> 8);
					int k3 = class39.maxWorldY + (class39.maxYGradient * i1 >> 8);
					int l4 = class39.minWorldZ + (class39.minZGradient * i1 >> 8);
					int i6 = class39.maxWorldZ + (class39.maxZGradient * i1 >> 8);
					if (worldY >= j2 && worldY <= k3 && worldZ >= l4 && worldZ <= i6)
						return true;
				}
			} else if (class39.projectionDirection == 2) {
				int j1 = worldX - class39.minWorldX;
				if (j1 > 0) {
					int k2 = class39.minWorldY + (class39.minYGradient * j1 >> 8);
					int l3 = class39.maxWorldY + (class39.maxYGradient * j1 >> 8);
					int i5 = class39.minWorldZ + (class39.minZGradient * j1 >> 8);
					int j6 = class39.maxWorldZ + (class39.maxZGradient * j1 >> 8);
					if (worldY >= k2 && worldY <= l3 && worldZ >= i5 && worldZ <= j6)
						return true;
				}
			} else if (class39.projectionDirection == 3) {
				int k1 = class39.minWorldY - worldY;
				if (k1 > 0) {
					int l2 = class39.minWorldX + (class39.minXGradient * k1 >> 8);
					int i4 = class39.maxWorldX + (class39.maxXGradient * k1 >> 8);
					int j5 = class39.minWorldZ + (class39.minZGradient * k1 >> 8);
					int k6 = class39.maxWorldZ + (class39.maxZGradient * k1 >> 8);
					if (worldX >= l2 && worldX <= i4 && worldZ >= j5 && worldZ <= k6)
						return true;
				}
			} else if (class39.projectionDirection == 4) {
				int l1 = worldY - class39.minWorldY;
				if (l1 > 0) {
					int i3 = class39.minWorldX + (class39.minXGradient * l1 >> 8);
					int j4 = class39.maxWorldX + (class39.maxXGradient * l1 >> 8);
					int k5 = class39.minWorldZ + (class39.minZGradient * l1 >> 8);
					int l6 = class39.maxWorldZ + (class39.maxZGradient * l1 >> 8);
					if (worldX >= i3 && worldX <= j4 && worldZ >= k5 && worldZ <= l6)
						return true;
				}
			} else if (class39.projectionDirection == 5) {
				int i2 = worldZ - class39.minWorldZ;
				if (i2 > 0) {
					int j3 = class39.minWorldX + (class39.minXGradient * i2 >> 8);
					int k4 = class39.maxWorldX + (class39.maxXGradient * i2 >> 8);
					int l5 = class39.minWorldY + (class39.minYGradient * i2 >> 8);
					int i7 = class39.maxWorldY + (class39.maxYGradient * i2 >> 8);
					if (worldX >= j3 && worldX <= k4 && worldY >= l5 && worldY <= i7)
						return true;
				}
			}
		}

		return false;
	}

	public static boolean lowMemory = true;
	public int planeCount;
	public int width;
	public int height;
	public int tileHeights[][][];
	public SceneTile tiles[][][];
	public int minPlane;
	public int temporaryObjectCount;
	public InteractiveObject temporaryObjects[];
	public int tileOcclusionCycles[][][];
	public static int remainingTileCount;
	public static int renderPlane;
	public static int renderCycle;
	public static int minTileX;
	public static int maxTileX;
	public static int minTileY;
	public static int maxTileY;
	public static int cameraTileX;
	public static int cameraTileY;
	public static int cameraX;
	public static int cameraZ;
	public static int cameraY;
	public static int pitchSine;
	public static int pitchCosine;
	public static int yawSine;
	public static int yawCosine;
	public static InteractiveObject renderInteractiveObjects[] = new InteractiveObject[100];
	public static final int WALL_DECORATION_INSET_X[] = { 53, -53, -53, 53 };
	public static final int WALL_DECORATION_INSET_Y[] = { -53, -53, 53, 53 };
	public static final int WALL_DECORATION_OUTSET_X[] = { -45, 45, 45, -45 };
	public static final int WALL_DECORATION_OUTSET_Y[] = { 45, 45, -45, -45 };
	public static boolean picking;
	public static int mouseX;
	public static int mouseY;
	public static int pickedTileX = -1;
	public static int pickedTileY = -1;
	public static int OCCLUDER_PLANE_COUNT;
	public static int occluderCounts[];
	public static SceneCluster occluders[][];
	public static int activeOccluderCount;
	public static SceneCluster activeOccluders[] = new SceneCluster[500];
	public static NodeDeque tileQueue = new NodeDeque();
	public static final int WALL_DRAW_FLAGS[] = { 19, 55, 38, 155, 255, 110, 137, 205, 76 };
	public static final int WALL_CULL_FLAGS[] = { 160, 192, 80, 96, 0, 144, 80, 48, 160 };
	public static final int WALL_DRAW_FLAGS_2[] = { 76, 8, 137, 4, 0, 1, 38, 2, 19 };
	public static final int WALL_UNCULL_FLAGS_0[] = { 0, 0, 2, 0, 0, 2, 1, 1, 0 };
	public static final int WALL_UNCULL_FLAGS_1[] = { 2, 0, 0, 2, 0, 0, 0, 4, 4 };
	public static final int WALL_UNCULL_FLAGS_2[] = { 0, 4, 4, 8, 0, 0, 8, 0, 0 };
	public static final int WALL_UNCULL_FLAGS_3[] = { 1, 1, 0, 0, 0, 8, 0, 0, 8 };
	public static final int TEXTURE_COLORS[] = { 41, 39248, 41, 4643, 41, 41, 41, 41, 41, 41, 41, 41, 41, 41, 41, 43086,
			41, 41, 41, 41, 41, 41, 41, 8602, 41, 28992, 41, 41, 41, 41, 41, 5056, 41, 41, 41, 7079, 41, 41, 41, 41, 41,
			41, 41, 41, 41, 41, 3131, 41, 41, 41 };
	public int mergeStampA[];
	public int mergeStampB[];
	public int mergeCycle;
	public int minimapTileShape[][] = { new int[16], { 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 },
			{ 1, 0, 0, 0, 1, 1, 0, 0, 1, 1, 1, 0, 1, 1, 1, 1 }, { 1, 1, 0, 0, 1, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0 },
			{ 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 0, 1, 0, 0, 0, 1 }, { 0, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1 },
			{ 1, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1, 1, 1, 1, 1, 1 }, { 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0, 1, 1, 0, 0 },
			{ 0, 0, 0, 0, 0, 0, 0, 0, 1, 0, 0, 0, 1, 1, 0, 0 }, { 1, 1, 1, 1, 1, 1, 1, 1, 0, 1, 1, 1, 0, 0, 1, 1 },
			{ 1, 1, 1, 1, 1, 1, 0, 0, 1, 0, 0, 0, 1, 0, 0, 0 }, { 0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 1, 0, 1, 1, 1 },
			{ 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 1, 0, 1, 1, 1, 1 } };
	public int minimapTileRotation[][] = { { 0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15 },
			{ 12, 8, 4, 0, 13, 9, 5, 1, 14, 10, 6, 2, 15, 11, 7, 3 },
			{ 15, 14, 13, 12, 11, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1, 0 },
			{ 3, 7, 11, 15, 2, 6, 10, 14, 1, 5, 9, 13, 0, 4, 8, 12 } };
	public static boolean visibilityMaps[][][][] = new boolean[8][32][51][51];
	public static boolean visibilityMap[][];
	public static int viewportCenterX;
	public static int viewportCenterY;
	public static int viewportMinX;
	public static int viewportMinY;
	public static int viewportMaxX;
	public static int viewportMaxY;

	static {
		OCCLUDER_PLANE_COUNT = 4;
		occluderCounts = new int[OCCLUDER_PLANE_COUNT];
		occluders = new SceneCluster[OCCLUDER_PLANE_COUNT][500];
	}
}