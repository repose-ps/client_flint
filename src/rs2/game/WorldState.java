package rs2.game;

import rs2.cache.def.GameObjectDefinition;
import rs2.cache.def.ItemDefinition;
import rs2.collection.NodeDeque;
import rs2.game.entity.Npc;
import rs2.game.entity.Player;
import rs2.net.Buffer;
import rs2.net.OutgoingPacketOpcode;
import rs2.scene.PendingSpawn;
import rs2.scene.Region;
import rs2.scene.Scene;
import rs2.scene.SceneConfig;
import rs2.scene.SceneConstants;
import rs2.scene.SceneUid;
import rs2.scene.TileFlags;
import rs2.scene.entity.DynamicObjectFactory;
import rs2.scene.entity.GraphicsObject;
import rs2.scene.entity.GroundItem;
import rs2.scene.entity.Projectile;
import rs2.scene.util.CollisionMap;

/**
 * Mutable state for the currently loaded 104x104 local world.
 *
 * <p>
 * The class owns scene/collision storage and the transient world entities that
 * used to live directly on {@code client}: ground-item piles, pending location
 * changes, projectiles, and stationary graphics effects.
 * </p>
 */
public final class WorldState {

	/** Constant value for plane count. */
	public static final int PLANE_COUNT = SceneConstants.PLANE_COUNT;

	/** Constant value for size. */
	public static final int SIZE = SceneConstants.SIZE;

	/** Stores tile flags values. */
	public final byte[][][] tileFlags = new byte[PLANE_COUNT][SIZE][SIZE];

	/** Stores tile heights values. */
	public final int[][][] tileHeights = new int[PLANE_COUNT][SIZE + 1][SIZE + 1];

	/** Scene graph backed by this world's shared tile-height array. */
	public final Scene scene = new Scene(tileHeights, PLANE_COUNT, SIZE, SIZE);

	/** Stores collision maps values. */
	public final CollisionMap[] collisionMaps = new CollisionMap[PLANE_COUNT];

	/** Stores ground items values. */
	public final NodeDeque[][][] groundItems = new NodeDeque[PLANE_COUNT][SIZE][SIZE];

	/**
	 * Pending spawns.
	 *
	 */
	public NodeDeque pendingSpawns = new NodeDeque();

	/**
	 * Projectiles.
	 *
	 */
	public final NodeDeque projectiles = new NodeDeque();

	/**
	 * Graphics objects.
	 *
	 */
	public final NodeDeque graphicsObjects = new NodeDeque();

	/** Stores the current projectile keepalive cycles. */
	private int projectileKeepaliveCycles;

	/** Creates animated/morphing scene locations for runtime object changes. */
	private final DynamicObjectFactory dynamicObjects;

	/**
	 * Creates a new world state.
	 *
	 * @param dynamicObjects dynamic-location factory
	 */
	public WorldState(DynamicObjectFactory dynamicObjects) {
		this.dynamicObjects = dynamicObjects;
		for (int plane = 0; plane < PLANE_COUNT; plane++) {
			collisionMaps[plane] = new CollisionMap(SIZE, SIZE);
		}
	}

	/**
	 * Interpolates the terrain height at one world-space coordinate.
	 * 
	 * @param worldX the world X
	 * @param worldY the world Y
	 * @param plane  the scene plane
	 * @return the tile height
	 */
	public int getTileHeight(int worldX, int worldY, int plane) {
		int tileX = worldX >> SceneConstants.TILE_BITS;
		int tileY = worldY >> SceneConstants.TILE_BITS;
		if (tileX < 0 || tileY < 0 || tileX > SceneConstants.MAX_TILE_INDEX || tileY > SceneConstants.MAX_TILE_INDEX) {
			return 0;
		}
		int heightPlane = plane;
		if (heightPlane < 3 && (tileFlags[1][tileX][tileY] & TileFlags.BRIDGE) != 0) {
			heightPlane++;
		}
		int localX = worldX & SceneConstants.TILE_OFFSET_MASK;
		int localY = worldY & SceneConstants.TILE_OFFSET_MASK;
		int south = tileHeights[heightPlane][tileX][tileY] * (SceneConstants.TILE_SIZE - localX)
				+ tileHeights[heightPlane][tileX + 1][tileY] * localX >> SceneConstants.TILE_BITS;
		int north = tileHeights[heightPlane][tileX][tileY + 1] * (SceneConstants.TILE_SIZE - localX)
				+ tileHeights[heightPlane][tileX + 1][tileY + 1] * localX >> SceneConstants.TILE_BITS;
		return south * (SceneConstants.TILE_SIZE - localY) + north * localY >> SceneConstants.TILE_BITS;
	}

	/**
	 * Rebuilds the visible ground-item pile for one scene tile.
	 * 
	 * @param plane the scene plane
	 * @param x     the X coordinate
	 * @param y     the Y coordinate
	 */
	public void updateGroundItemPile(int plane, int x, int y) {
		NodeDeque items = groundItems[plane][x][y];
		if (items == null) {
			scene.removeGroundItemTile(plane, x, y);
			return;
		}

		int highestValue = 0xfa0a1f01;
		GroundItem primary = null;
		for (GroundItem item = (GroundItem) items.first(); item != null; item = (GroundItem) items.next()) {
			ItemDefinition definition = ItemDefinition.lookup(item.id);
			int value = definition.price;
			if (definition.stackable) {
				value *= item.amount + 1;
			}
			if (value > highestValue) {
				highestValue = value;
				primary = item;
			}
		}

		items.addFirst(primary);
		GroundItem secondary = null;
		GroundItem tertiary = null;
		for (GroundItem item = (GroundItem) items.first(); item != null; item = (GroundItem) items.next()) {
			if (item.id != primary.id && secondary == null) {
				secondary = item;
			}
			if (item.id != primary.id && item.id != secondary.id && tertiary == null) {
				tertiary = item;
			}
		}

		int uid = x + (y << SceneUid.TILE_Y_SHIFT) + SceneUid.GROUND_ITEM_TYPE_BITS;
		scene.addGroundItemTile(plane, x, y,
				getTileHeight(x * SceneConstants.TILE_SIZE + SceneConstants.TILE_CENTER,
						y * SceneConstants.TILE_SIZE + SceneConstants.TILE_CENTER, plane),
				uid, primary, secondary, tertiary);
	}

	/**
	 * Clears all transient world entities on a successful login.
	 */
	public void resetTransientState() {
		projectiles.clear();
		graphicsObjects.clear();
		for (int plane = 0; plane < PLANE_COUNT; plane++) {
			for (int x = 0; x < SIZE; x++) {
				for (int y = 0; y < SIZE; y++) {
					groundItems[plane][x][y] = null;
				}
			}
		}
		pendingSpawns = new NodeDeque();
		projectileKeepaliveCycles = 0;
	}

	/**
	 * Legacy packet-40 world-zone clearing behavior.
	 *
	 * @param plane     the plane
	 * @param zoneBaseX the zone base x
	 * @param zoneBaseY the zone base y
	 */
	public void clearZone(int plane, int zoneBaseX, int zoneBaseY) {
		for (int x = zoneBaseX; x < zoneBaseX + SceneConstants.CHUNK_SIZE; x++) {
			for (int y = zoneBaseY; y < zoneBaseY + SceneConstants.CHUNK_SIZE; y++) {
				if (groundItems[plane][x][y] != null) {
					groundItems[plane][x][y] = null;
					updateGroundItemPile(plane, x, y);
				}
			}
		}

		for (PendingSpawn spawn = (PendingSpawn) pendingSpawns
				.first(); spawn != null; spawn = (PendingSpawn) pendingSpawns.next()) {
			if (spawn.x >= zoneBaseX && spawn.x < zoneBaseX + SceneConstants.CHUNK_SIZE && spawn.y >= zoneBaseY
					&& spawn.y < zoneBaseY + SceneConstants.CHUNK_SIZE && spawn.plane == plane) {
				spawn.restoreDelay = 0;
			}
		}
	}

	/**
	 * Applies a dynamic object replacement or removal to scene and collision state.
	 * 
	 * @param plane        the scene plane
	 * @param x            the X coordinate
	 * @param y            the Y coordinate
	 * @param sceneLayer   the scene layer
	 * @param objectId     the object ID
	 * @param type         the type
	 * @param orientation  the orientation
	 * @param lowMemory    whether low-memory mode is active
	 * @param currentPlane the current plane
	 */
	public void applyGameObjectChange(int plane, int x, int y, int sceneLayer, int objectId, int type, int orientation,
			boolean lowMemory, int currentPlane) {
		if (x < SceneConstants.INTERIOR_MIN_TILE || y < SceneConstants.INTERIOR_MIN_TILE
				|| x > SceneConstants.INTERIOR_MAX_TILE || y > SceneConstants.INTERIOR_MAX_TILE) {
			return;
		}
		if (lowMemory && plane != currentPlane) {
			return;
		}

		int uid = 0;
		if (sceneLayer == 0) {
			uid = scene.getWallUid(plane, x, y);
		}
		if (sceneLayer == 1) {
			uid = scene.getWallDecorationUid(plane, x, y);
		}
		if (sceneLayer == 2) {
			uid = scene.getInteractiveObjectUid(plane, x, y);
		}
		if (sceneLayer == 3) {
			uid = scene.getFloorDecorationUid(plane, x, y);
		}

		if (uid != 0) {
			int config = scene.getConfig(plane, x, y, uid);
			int previousId = uid >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK;
			int previousType = SceneConfig.type(config);
			int previousOrientation = SceneConfig.orientation(config);

			if (sceneLayer == 0) {
				scene.removeWall(plane, x, y);
				GameObjectDefinition definition = GameObjectDefinition.lookup(previousId);
				if (definition.blocksMovement) {
					collisionMaps[plane].unmarkWall(x, y, previousType, previousOrientation,
							definition.blocksProjectiles);
				}
			}
			if (sceneLayer == 1) {
				scene.removeWallDecoration(plane, x, y);
			}
			if (sceneLayer == 2) {
				scene.removeInteractiveObject(plane, x, y);
				GameObjectDefinition definition = GameObjectDefinition.lookup(previousId);
				if (x + definition.sizeX > SceneConstants.MAX_TILE_INDEX
						|| y + definition.sizeX > SceneConstants.MAX_TILE_INDEX
						|| x + definition.sizeY > SceneConstants.MAX_TILE_INDEX
						|| y + definition.sizeY > SceneConstants.MAX_TILE_INDEX) {
					return;
				}
				if (definition.blocksMovement) {
					collisionMaps[plane].unmarkSolidOccupant(x, y, definition.sizeX, definition.sizeY,
							previousOrientation, definition.blocksProjectiles);
				}
			}
			if (sceneLayer == 3) {
				scene.removeFloorDecoration(plane, x, y);
				GameObjectDefinition definition = GameObjectDefinition.lookup(previousId);
				if (definition.blocksMovement && definition.interactive) {
					collisionMaps[plane].unmarkBlocked(x, y);
				}
			}
		}

		if (objectId >= 0) {
			int heightPlane = plane;
			if (heightPlane < 3 && (tileFlags[1][x][y] & TileFlags.BRIDGE) != 0) {
				heightPlane++;
			}
			Region.addLocation(objectId, heightPlane, type, orientation, x, y, plane, collisionMaps[plane], scene,
					tileHeights, dynamicObjects);
		}
	}

	/**
	 * Captures the scene object currently occupying a pending-spawn location.
	 * 
	 * @param spawn the spawn
	 */
	public void capturePreviousState(PendingSpawn spawn) {
		int uid = 0;
		int id = -1;
		int type = 0;
		int orientation = 0;
		if (spawn.sceneLayer == 0) {
			uid = scene.getWallUid(spawn.plane, spawn.x, spawn.y);
		}
		if (spawn.sceneLayer == 1) {
			uid = scene.getWallDecorationUid(spawn.plane, spawn.x, spawn.y);
		}
		if (spawn.sceneLayer == 2) {
			uid = scene.getInteractiveObjectUid(spawn.plane, spawn.x, spawn.y);
		}
		if (spawn.sceneLayer == 3) {
			uid = scene.getFloorDecorationUid(spawn.plane, spawn.x, spawn.y);
		}
		if (uid != 0) {
			int config = scene.getConfig(spawn.plane, spawn.x, spawn.y, uid);
			id = uid >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK;
			type = SceneConfig.type(config);
			orientation = SceneConfig.orientation(config);
		}
		spawn.previousId = id;
		spawn.previousType = type;
		spawn.previousOrientation = orientation;
	}

	/**
	 * Creates or updates a pending scene-object spawn at one tile.
	 * 
	 * @param plane            the scene plane
	 * @param x                the X coordinate
	 * @param y                the Y coordinate
	 * @param sceneLayer       the scene layer
	 * @param spawnId          the spawn ID
	 * @param spawnType        the spawn type
	 * @param spawnOrientation the spawn orientation
	 * @param spawnDelay       the spawn delay
	 * @param restoreDelay     the restore delay
	 */
	public void schedulePendingSpawn(int plane, int x, int y, int sceneLayer, int spawnId, int spawnType,
			int spawnOrientation, int spawnDelay, int restoreDelay) {
		PendingSpawn spawn = null;
		for (PendingSpawn candidate = (PendingSpawn) pendingSpawns
				.first(); candidate != null; candidate = (PendingSpawn) pendingSpawns.next()) {
			if (candidate.plane == plane && candidate.x == x && candidate.y == y
					&& candidate.sceneLayer == sceneLayer) {
				spawn = candidate;
				break;
			}
		}

		if (spawn == null) {
			spawn = new PendingSpawn();
			spawn.plane = plane;
			spawn.sceneLayer = sceneLayer;
			spawn.x = x;
			spawn.y = y;
			capturePreviousState(spawn);
			pendingSpawns.addLast(spawn);
		}

		spawn.spawnId = spawnId;
		spawn.spawnType = spawnType;
		spawn.spawnOrientation = spawnOrientation;
		spawn.spawnDelay = spawnDelay;
		spawn.restoreDelay = restoreDelay;
	}

	/** Restores or discards pending spawns after a region rebuild. */
	public void resetPendingSpawnsAfterRegionBuild() {
		for (PendingSpawn spawn = (PendingSpawn) pendingSpawns
				.first(); spawn != null; spawn = (PendingSpawn) pendingSpawns.next()) {
			if (spawn.restoreDelay == -1) {
				spawn.spawnDelay = 0;
				capturePreviousState(spawn);
			} else {
				spawn.unlink();
			}
		}
	}

	/**
	 * Advances pending-spawn delays and applies due scene changes.
	 * 
	 * @param lowMemory    whether low-memory mode is active
	 * @param currentPlane the current plane
	 */
	public void updatePendingSpawns(boolean lowMemory, int currentPlane) {
		for (PendingSpawn spawn = (PendingSpawn) pendingSpawns
				.first(); spawn != null; spawn = (PendingSpawn) pendingSpawns.next()) {
			if (spawn.restoreDelay > 0) {
				spawn.restoreDelay--;
			}
			if (spawn.restoreDelay == 0) {
				if (spawn.previousId < 0 || Region.isGameObjectModelReady(spawn.previousId, spawn.previousType)) {
					applyGameObjectChange(spawn.plane, spawn.x, spawn.y, spawn.sceneLayer, spawn.previousId,
							spawn.previousType, spawn.previousOrientation, lowMemory, currentPlane);
					spawn.unlink();
				}
			} else {
				if (spawn.spawnDelay > 0) {
					spawn.spawnDelay--;
				}
				if (spawn.spawnDelay == 0 && spawn.x >= 1 && spawn.y >= 1 && spawn.x < SceneConstants.MAX_TILE_INDEX
						&& spawn.y < SceneConstants.MAX_TILE_INDEX
						&& (spawn.spawnId < 0 || Region.isGameObjectModelReady(spawn.spawnId, spawn.spawnType))) {
					applyGameObjectChange(spawn.plane, spawn.x, spawn.y, spawn.sceneLayer, spawn.spawnId,
							spawn.spawnType, spawn.spawnOrientation, lowMemory, currentPlane);
					spawn.spawnDelay = -1;
					if (spawn.spawnId == spawn.previousId && spawn.previousId == -1) {
						spawn.unlink();
					} else if (spawn.spawnId == spawn.previousId && spawn.spawnOrientation == spawn.previousOrientation
							&& spawn.spawnType == spawn.previousType) {
						spawn.unlink();
					}
				}
			}
		}
	}

	/**
	 * Shifts ground items and pending spawn coordinates after a region-base change.
	 *
	 * @param deltaX the delta x
	 * @param deltaY the delta y
	 */
	public void shiftLocalState(int deltaX, int deltaY) {
		int startX = 0;
		int endX = SIZE;
		int stepX = 1;
		if (deltaX < 0) {
			startX = SIZE - 1;
			endX = -1;
			stepX = -1;
		}
		int startY = 0;
		int endY = SIZE;
		int stepY = 1;
		if (deltaY < 0) {
			startY = SIZE - 1;
			endY = -1;
			stepY = -1;
		}

		for (int x = startX; x != endX; x += stepX) {
			for (int y = startY; y != endY; y += stepY) {
				int sourceX = x + deltaX;
				int sourceY = y + deltaY;
				for (int plane = 0; plane < PLANE_COUNT; plane++) {
					if (sourceX >= 0 && sourceY >= 0 && sourceX < SIZE && sourceY < SIZE) {
						groundItems[plane][x][y] = groundItems[plane][sourceX][sourceY];
					} else {
						groundItems[plane][x][y] = null;
					}
				}
			}
		}

		for (PendingSpawn spawn = (PendingSpawn) pendingSpawns
				.first(); spawn != null; spawn = (PendingSpawn) pendingSpawns.next()) {
			spawn.x -= deltaX;
			spawn.y -= deltaY;
			if (spawn.x < 0 || spawn.y < 0 || spawn.x >= SIZE || spawn.y >= SIZE) {
				spawn.unlink();
			}
		}
	}

	/**
	 * Advances active projectiles and submits visible ones to the scene.
	 * 
	 * @param currentPlane           the current plane
	 * @param currentCycle           the current client cycle
	 * @param cycleDelta             the cycle delta
	 * @param localPlayerServerIndex the local player server index
	 * @param localPlayer            the local player
	 * @param actors                 the actors
	 * @param outgoing               the outgoing
	 */
	public void updateProjectiles(int currentPlane, int currentCycle, int cycleDelta, int localPlayerServerIndex,
			Player localPlayer, ActorSynchronizer actors, Buffer outgoing) {

		boolean hasProjectiles = false;

		for (Projectile projectile = (Projectile) projectiles
				.first(); projectile != null; projectile = (Projectile) projectiles.next()) {

			if (projectile.plane != currentPlane || currentCycle > projectile.cycleEnd) {
				projectile.unlink();
				continue;
			}

			hasProjectiles = true;

			if (currentCycle >= projectile.cycleStart) {
				if (projectile.targetIndex > 0) {
					Npc npc = actors.npcs[projectile.targetIndex - 1];

					if (npc != null && npc.x >= 0 && npc.x < 13312 && npc.y >= 0 && npc.y < 13312) {
						projectile.setDestination(npc.x, npc.y,
								getTileHeight(npc.x, npc.y, projectile.plane) - projectile.endHeight, currentCycle);
					}
				}

				if (projectile.targetIndex < 0) {
					int playerIndex = -projectile.targetIndex - 1;
					Player player = playerIndex == localPlayerServerIndex ? localPlayer : actors.players[playerIndex];

					if (player != null && player.x >= 0 && player.x < 13312 && player.y >= 0 && player.y < 13312) {
						projectile.setDestination(player.x, player.y,
								getTileHeight(player.x, player.y, projectile.plane) - projectile.endHeight,
								currentCycle);
					}
				}

				projectile.advance(cycleDelta);

				scene.addEntity(currentPlane, (int) projectile.x, (int) projectile.y, (int) projectile.z, projectile,
						-1, 60, false, projectile.yaw);
			}
		}

		if (hasProjectiles) {
			projectileKeepaliveCycles++;

			if (projectileKeepaliveCycles > 51) {
				projectileKeepaliveCycles = 0;
				outgoing.writeOpcode(OutgoingPacketOpcode.PROJECTILE_KEEPALIVE);
			}

		} else {
			projectileKeepaliveCycles = 0;
		}
	}

	/**
	 * Advances temporary graphics objects and submits visible ones to the scene.
	 * 
	 * @param currentPlane the current plane
	 * @param currentCycle the current client cycle
	 * @param cycleDelta   the cycle delta
	 */
	public void updateGraphicsObjects(int currentPlane, int currentCycle, int cycleDelta) {
		for (GraphicsObject graphics = (GraphicsObject) graphicsObjects
				.first(); graphics != null; graphics = (GraphicsObject) graphicsObjects.next()) {
			if (graphics.plane != currentPlane || graphics.finished) {
				graphics.unlink();
			} else if (currentCycle >= graphics.cycleStart) {
				graphics.advance(cycleDelta);
				if (graphics.finished) {
					graphics.unlink();
				} else {
					scene.addEntity(graphics.plane, graphics.x, graphics.y, graphics.z, graphics, -1, 60, false, 0);
				}
			}
		}
	}
}
