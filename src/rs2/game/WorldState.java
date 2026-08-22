package rs2.game;

import rs2.cache.def.GameObjectDefinition;
import rs2.cache.def.ItemDefinition;
import rs2.collection.NodeDeque;
import rs2.media.renderable.GraphicsObject;
import rs2.media.renderable.GroundItem;
import rs2.media.renderable.Npc;
import rs2.media.renderable.Player;
import rs2.media.renderable.Projectile;
import rs2.net.Buffer;
import rs2.scene.PendingSpawn;
import rs2.scene.Region;
import rs2.scene.Scene;
import rs2.scene.util.CollisionMap;

/**
 * Mutable state for the currently loaded 104x104 local world.
 *
 * <p>The class owns scene/collision storage and the transient world entities that
 * used to live directly on {@code client}: ground-item piles, pending location
 * changes, projectiles, and stationary graphics effects.</p>
 */
public final class WorldState {

    public static final int PLANE_COUNT = 4;
    public static final int SIZE = 104;

    public final byte[][][] tileFlags = new byte[PLANE_COUNT][SIZE][SIZE];
    public final int[][][] tileHeights = new int[PLANE_COUNT][SIZE + 1][SIZE + 1];
    public final Scene scene = new Scene(tileHeights, PLANE_COUNT, SIZE, SIZE);
    public final CollisionMap[] collisionMaps = new CollisionMap[PLANE_COUNT];
    public final NodeDeque[][][] groundItems = new NodeDeque[PLANE_COUNT][SIZE][SIZE];
    public NodeDeque pendingSpawns = new NodeDeque();
    public final NodeDeque projectiles = new NodeDeque();
    public final NodeDeque graphicsObjects = new NodeDeque();

    private int projectileKeepaliveCycles;

    public WorldState() {
        for (int plane = 0; plane < PLANE_COUNT; plane++) {
            collisionMaps[plane] = new CollisionMap(SIZE, SIZE);
        }
    }

    /**
     * Legacy client.method110(int i, int j, byte byte0, int k)
     *
     * <pre>
     * i     -> worldY
     * j     -> worldX
     * byte0 -> removed sentinel (required 9)
     * k     -> plane
     * </pre>
     */
    public int getTileHeight(int worldX, int worldY, int plane) {
        int tileX = worldX >> 7;
        int tileY = worldY >> 7;
        if (tileX < 0 || tileY < 0 || tileX > 103 || tileY > 103) {
            return 0;
        }
        int heightPlane = plane;
        if (heightPlane < 3 && (tileFlags[1][tileX][tileY] & 2) == 2) {
            heightPlane++;
        }
        int localX = worldX & 0x7f;
        int localY = worldY & 0x7f;
        int south = tileHeights[heightPlane][tileX][tileY] * (128 - localX)
                + tileHeights[heightPlane][tileX + 1][tileY] * localX >> 7;
        int north = tileHeights[heightPlane][tileX][tileY + 1] * (128 - localX)
                + tileHeights[heightPlane][tileX + 1][tileY + 1] * localX >> 7;
        return south * (128 - localY) + north * localY >> 7;
    }

    /**
     * Legacy client.method26(int i, int j)
     *
     * <pre>
     * i -> x
     * j -> y
     * old client.anInt1091 -> plane
     * </pre>
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

        int uid = x + (y << 7) + 0x60000000;
        scene.addGroundItemTile(plane, x, y, getTileHeight(x * 128 + 64, y * 128 + 64, plane), uid,
                primary, secondary, tertiary);
    }

    /** Clears all transient world entities on a successful login. */
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
     */
    public void clearZone(int plane, int zoneBaseX, int zoneBaseY) {
        for (int x = zoneBaseX; x < zoneBaseX + 8; x++) {
            for (int y = zoneBaseY; y < zoneBaseY + 8; y++) {
                if (groundItems[plane][x][y] != null) {
                    groundItems[plane][x][y] = null;
                    updateGroundItemPile(plane, x, y);
                }
            }
        }

        for (PendingSpawn spawn = (PendingSpawn) pendingSpawns.first(); spawn != null;
                spawn = (PendingSpawn) pendingSpawns.next()) {
            if (spawn.x >= zoneBaseX && spawn.x < zoneBaseX + 8
                    && spawn.y >= zoneBaseY && spawn.y < zoneBaseY + 8 && spawn.plane == plane) {
                spawn.restoreDelay = 0;
            }
        }
    }

    /**
     * Legacy client.method45(int i, int j, int k, int l, int i1, int j1, int k1)
     *
     * <pre>
     * i  -> orientation
     * j  -> x
     * k  -> objectId
     * l  -> y
     * i1 -> plane
     * j1 -> type
     * k1 -> sceneLayer
     * </pre>
     */
    public void applyGameObjectChange(int plane, int x, int y, int sceneLayer, int objectId, int type,
            int orientation, boolean lowMemory, int currentPlane) {
        if (x < 1 || y < 1 || x > 102 || y > 102) {
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
            int previousId = uid >> 14 & 0x7fff;
            int previousType = config & 0x1f;
            int previousOrientation = config >> 6;

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
                if (x + definition.sizeX > 103 || y + definition.sizeX > 103 || x + definition.sizeY > 103
                        || y + definition.sizeY > 103) {
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
            if (heightPlane < 3 && (tileFlags[1][x][y] & 2) == 2) {
                heightPlane++;
            }
            Region.addLocation(objectId, heightPlane, type, orientation, x, y, plane, collisionMaps[plane], scene,
                    tileHeights);
        }
    }

    /**
     * Legacy client.method140(byte byte0, PendingSpawn class50_sub2)
     *
     * <pre>
     * byte0       -> removed sentinel (required -61)
     * class50_sub2 -> spawn
     * </pre>
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
            id = uid >> 14 & 0x7fff;
            type = config & 0x1f;
            orientation = config >> 6;
        }
        spawn.previousId = id;
        spawn.previousType = type;
        spawn.previousOrientation = orientation;
    }

    /**
     * Legacy client.method145(boolean flag, int i, int j, int k, int l, int i1,
     * int j1, int k1, int l1, int i2)
     *
     * <pre>
     * flag -> removed unused argument
     * i    -> plane
     * j    -> x
     * k    -> spawnOrientation
     * l    -> restoreDelay
     * i1   -> spawnType
     * j1   -> spawnId
     * k1   -> spawnDelay
     * l1   -> sceneLayer
     * i2   -> y
     * </pre>
     */
    public void schedulePendingSpawn(int plane, int x, int y, int sceneLayer, int spawnId, int spawnType,
            int spawnOrientation, int spawnDelay, int restoreDelay) {
        PendingSpawn spawn = null;
        for (PendingSpawn candidate = (PendingSpawn) pendingSpawns.first(); candidate != null;
                candidate = (PendingSpawn) pendingSpawns.next()) {
            if (candidate.plane == plane && candidate.x == x && candidate.y == y && candidate.sceneLayer == sceneLayer) {
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

    /** Legacy client.method18(byte 3). */
    public void resetPendingSpawnsAfterRegionBuild() {
        for (PendingSpawn spawn = (PendingSpawn) pendingSpawns.first(); spawn != null;
                spawn = (PendingSpawn) pendingSpawns.next()) {
            if (spawn.restoreDelay == -1) {
                spawn.spawnDelay = 0;
                capturePreviousState(spawn);
            } else {
                spawn.unlink();
            }
        }
    }

    /** Legacy client.method36(int 16220). */
    public void updatePendingSpawns(boolean lowMemory, int currentPlane) {
        for (PendingSpawn spawn = (PendingSpawn) pendingSpawns.first(); spawn != null;
                spawn = (PendingSpawn) pendingSpawns.next()) {
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
                if (spawn.spawnDelay == 0 && spawn.x >= 1 && spawn.y >= 1 && spawn.x <= 102 && spawn.y <= 102
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

    /** Shifts ground items and pending spawn coordinates after a region-base change. */
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

        for (PendingSpawn spawn = (PendingSpawn) pendingSpawns.first(); spawn != null;
                spawn = (PendingSpawn) pendingSpawns.next()) {
            spawn.x -= deltaX;
            spawn.y -= deltaY;
            if (spawn.x < 0 || spawn.y < 0 || spawn.x >= SIZE || spawn.y >= SIZE) {
                spawn.unlink();
            }
        }
    }

    /** Legacy client.method51(false). */
    public void updateProjectiles(int currentPlane, int currentCycle, int cycleDelta, int localPlayerServerIndex,
            Player localPlayer, ActorSynchronizer actors, Buffer outgoing) {
        for (Projectile projectile = (Projectile) projectiles.first(); projectile != null;
                projectile = (Projectile) projectiles.next()) {
            if (projectile.plane != currentPlane || currentCycle > projectile.cycleEnd) {
                projectile.unlink();
            } else if (currentCycle >= projectile.cycleStart) {
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
                                getTileHeight(player.x, player.y, projectile.plane) - projectile.endHeight, currentCycle);
                    }
                }
                projectile.advance(cycleDelta);
                scene.addEntity(currentPlane, (int) projectile.x, (int) projectile.y, (int) projectile.z, projectile,
                        -1, 60, false, projectile.yaw);
            }
        }

        projectileKeepaliveCycles++;
        if (projectileKeepaliveCycles > 51) {
            projectileKeepaliveCycles = 0;
            outgoing.writeOpcode(248);
        }
    }

    /** Legacy client.method76(-992). */
    public void updateGraphicsObjects(int currentPlane, int currentCycle, int cycleDelta) {
        for (GraphicsObject graphics = (GraphicsObject) graphicsObjects.first(); graphics != null;
                graphics = (GraphicsObject) graphicsObjects.next()) {
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