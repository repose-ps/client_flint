package rs2.game.render;

import java.util.IdentityHashMap;
import java.util.Map;

import rs2.game.ActorSynchronizer;
import rs2.game.CameraController;
import rs2.game.WorldState;
import rs2.game.entity.Npc;
import rs2.game.entity.Player;
import rs2.media.Angle;
import rs2.media.Rasterizer3D;
import rs2.media.model.Model;
import rs2.media.model.Renderable;
import rs2.scene.Scene;
import rs2.scene.SceneUid;
import rs2.scene.tile.ComplexTile;
import rs2.scene.tile.FloorDecoration;
import rs2.scene.tile.GenericTile;
import rs2.scene.tile.GroundItemTile;
import rs2.scene.tile.InteractiveObject;
import rs2.scene.tile.SceneTile;
import rs2.scene.tile.Wall;
import rs2.scene.tile.WallDecoration;

/**
 * Fixed-rate CPU scene picking used by both rendering backends.
 *
 * <p>The original 377 client populated interaction state as a side effect of
 * software rasterization. Flint renders independently from the 50 Hz simulation,
 * so that coupling would make walking and context menus depend on presentation
 * FPS. This picker performs the same job once per logic tick without drawing any
 * pixels.</p>
 */
public final class ScenePicker {

    private static final int TILE_SIZE = 128;
    private static final int RENDER_PLANE_VARIANTS = 4;
    private static final int INVISIBLE_HSL = 0xbc614e;
    private static final double NO_HIT = Double.POSITIVE_INFINITY;

    /** Reused identity set for multi-tile scene objects. */
    private final Map<InteractiveObject, Boolean> visitedObjects = new IdentityHashMap<>();

    /** Scratch result from the most recent terrain ray cast. */
    private int terrainTileX = -1;
    private int terrainTileY = -1;

    /** Whether the active backend currently renders only persistent {@link Model}s. */
    private boolean staticModelsOnly;

    /**
     * Refreshes hover picks and resolves any pending walk-to terrain request.
     *
     * @param world          current local world
     * @param actors         synchronized actor registry
     * @param localPlayer    local player
     * @param currentPlane   current gameplay plane
     * @param renderPlane    roof-filtered render plane
     * @param camera         current fixed-tick camera state
     * @param mouseX         client-space mouse X
     * @param mouseY         client-space mouse Y
     * @param viewportX      viewport origin X in client space
     * @param viewportY      viewport origin Y in client space
     * @param viewportWidth  viewport width
     * @param viewportHeight   viewport height
     * @param staticModelsOnly whether deferred/dynamic renderables must be excluded
     *                         because the active backend does not draw them yet
     */
    public void update(WorldState world, ActorSynchronizer actors, Player localPlayer, int currentPlane,
            int renderPlane, CameraController camera, int mouseX, int mouseY, int viewportX, int viewportY,
            int viewportWidth, int viewportHeight, boolean staticModelsOnly) {
        Model.pickingEnabled = false;
        Model.pickedCount = 0;
        if (world == null || world.scene == null || camera == null || viewportWidth <= 0 || viewportHeight <= 0) {
            return;
        }

        this.staticModelsOnly = staticModelsOnly;
        Scene scene = world.scene;
        int relativeMouseX = mouseX - viewportX;
        int relativeMouseY = mouseY - viewportY;
        boolean mouseInViewport = relativeMouseX >= 0 && relativeMouseY >= 0 && relativeMouseX < viewportWidth
                && relativeMouseY < viewportHeight;

        int normalizedRenderPlane = Math.max(0, Math.min(RENDER_PLANE_VARIANTS - 1, renderPlane));
        int yaw = camera.yaw & Angle.MASK;
        int pitch = camera.pitch & Angle.MASK;
        int yawSin = Rasterizer3D.SINE[yaw];
        int yawCos = Rasterizer3D.COSINE[yaw];
        int pitchSin = Rasterizer3D.SINE[pitch];
        int pitchCos = Rasterizer3D.COSINE[pitch];

        if (mouseInViewport) {
            pickSceneEntities(scene, normalizedRenderPlane, camera.x, camera.y, camera.height, yawSin, yawCos,
                    pitchSin, pitchCos, relativeMouseX, relativeMouseY, viewportWidth, viewportHeight);
            pickActors(world, actors, localPlayer, currentPlane, camera.x, camera.y, camera.height, yawSin, yawCos,
                    pitchSin, pitchCos, relativeMouseX, relativeMouseY, viewportWidth, viewportHeight);
        }

        if (scene.tilePickPending()) {
            int pickX = scene.tilePickRequestX();
            int pickY = scene.tilePickRequestY();
            if (pickX >= 0 && pickY >= 0 && pickX < viewportWidth && pickY < viewportHeight
                    && pickTerrain(scene, normalizedRenderPlane, camera.x, camera.y, camera.height, yawSin, yawCos,
                            pitchSin, pitchCos, pickX, pickY, viewportWidth, viewportHeight)) {
                scene.completeTilePick(terrainTileX, terrainTileY);
            } else {
                scene.completeTilePick(-1, -1);
            }
        }
    }

    private void pickSceneEntities(Scene scene, int renderPlane, int cameraX, int cameraY, int cameraHeight,
            int yawSin, int yawCos, int pitchSin, int pitchCos, int mouseX, int mouseY, int viewportWidth,
            int viewportHeight) {
        visitedObjects.clear();
        int firstPlane = Math.max(0, scene.minPlane);
        int lastPlane = Math.min(scene.planeCount, scene.tiles.length);
        for (int plane = firstPlane; plane < lastPlane; plane++) {
            SceneTile[][] planeTiles = scene.tiles[plane];
            for (int tileX = 0; tileX < scene.width; tileX++) {
                for (int tileY = 0; tileY < scene.height; tileY++) {
                    SceneTile tile = planeTiles[tileX][tileY];
                    int minRenderPlane = minRenderPlane(tile);
                    if (minRenderPlane < 0 || minRenderPlane > renderPlane) {
                        continue;
                    }

                    SceneTile below = tile.tileBelow;
                    if (below != null) {
                        if (below.wall != null) {
                            pickRenderable(below.wall.primary, 0, below.wall.x, below.wall.z, below.wall.y,
                                    below.wall.uid, cameraX, cameraY, cameraHeight, yawSin, yawCos, pitchSin, pitchCos,
                                    mouseX, mouseY, viewportWidth, viewportHeight);
                        }
                        pickInteractiveObjects(below, cameraX, cameraY, cameraHeight, yawSin, yawCos, pitchSin,
                                pitchCos, mouseX, mouseY, viewportWidth, viewportHeight);
                    }

                    Wall wall = tile.wall;
                    if (wall != null) {
                        pickRenderable(wall.primary, 0, wall.x, wall.z, wall.y, wall.uid, cameraX, cameraY,
                                cameraHeight, yawSin, yawCos, pitchSin, pitchCos, mouseX, mouseY, viewportWidth,
                                viewportHeight);
                        pickRenderable(wall.secondary, 0, wall.x, wall.z, wall.y, wall.uid, cameraX, cameraY,
                                cameraHeight, yawSin, yawCos, pitchSin, pitchCos, mouseX, mouseY, viewportWidth,
                                viewportHeight);
                    }

                    WallDecoration decoration = tile.wallDecoration;
                    if (decoration != null) {
                        pickWallDecoration(decoration, cameraX, cameraY, cameraHeight, yawSin, yawCos, pitchSin,
                                pitchCos, mouseX, mouseY, viewportWidth, viewportHeight);
                    }

                    FloorDecoration floor = tile.floorDecoration;
                    if (floor != null) {
                        pickRenderable(floor.renderable, 0, floor.x, floor.z, floor.y, floor.uid, cameraX, cameraY,
                                cameraHeight, yawSin, yawCos, pitchSin, pitchCos, mouseX, mouseY, viewportWidth,
                                viewportHeight);
                    }

                    GroundItemTile items = tile.groundItemTile;
                    if (items != null) {
                        int itemHeight = items.z - items.heightOffset;
                        if (hitRenderable(items.firstGroundItem, 0, items.x, itemHeight, items.y, cameraX, cameraY,
                                cameraHeight, yawSin, yawCos, pitchSin, pitchCos, mouseX, mouseY, viewportWidth,
                                viewportHeight)
                                || hitRenderable(items.secondGroundItem, 0, items.x, itemHeight, items.y, cameraX,
                                        cameraY, cameraHeight, yawSin, yawCos, pitchSin, pitchCos, mouseX, mouseY,
                                        viewportWidth, viewportHeight)
                                || hitRenderable(items.thirdGroundItem, 0, items.x, itemHeight, items.y, cameraX,
                                        cameraY, cameraHeight, yawSin, yawCos, pitchSin, pitchCos, mouseX, mouseY,
                                        viewportWidth, viewportHeight)) {
                            addPickedUid(items.uid);
                        }
                    }

                    pickInteractiveObjects(tile, cameraX, cameraY, cameraHeight, yawSin, yawCos, pitchSin, pitchCos,
                            mouseX, mouseY, viewportWidth, viewportHeight);
                }
            }
        }
    }

    private void pickInteractiveObjects(SceneTile tile, int cameraX, int cameraY, int cameraHeight, int yawSin,
            int yawCos, int pitchSin, int pitchCos, int mouseX, int mouseY, int viewportWidth, int viewportHeight) {
        for (int index = 0; index < tile.interactiveObjectCount; index++) {
            InteractiveObject object = tile.interactiveObjects[index];
            if (object == null || visitedObjects.put(object, Boolean.TRUE) != null) {
                continue;
            }
            pickRenderable(object.renderable, object.rotation, object.worldX, object.worldZ, object.worldY, object.uid,
                    cameraX, cameraY, cameraHeight, yawSin, yawCos, pitchSin, pitchCos, mouseX, mouseY, viewportWidth,
                    viewportHeight);
        }
    }

    private void pickWallDecoration(WallDecoration decoration, int cameraX, int cameraY, int cameraHeight, int yawSin,
            int yawCos, int pitchSin, int pitchCos, int mouseX, int mouseY, int viewportWidth, int viewportHeight) {
        if ((decoration.configBits & 0x300) == 0) {
            pickRenderable(decoration.renderable, decoration.face, decoration.x, decoration.z, decoration.y,
                    decoration.uid, cameraX, cameraY, cameraHeight, yawSin, yawCos, pitchSin, pitchCos, mouseX, mouseY,
                    viewportWidth, viewportHeight);
            return;
        }
        int face = decoration.face & 3;
        if ((decoration.configBits & 0x100) != 0) {
            int orientation = face * Angle.QUARTER_TURN + Angle.EIGHTH_TURN & Angle.MASK;
            pickRenderable(decoration.renderable, orientation, decoration.x + Scene.WALL_DECORATION_INSET_X[face],
                    decoration.z, decoration.y + Scene.WALL_DECORATION_INSET_Y[face], decoration.uid, cameraX,
                    cameraY, cameraHeight, yawSin, yawCos, pitchSin, pitchCos, mouseX, mouseY, viewportWidth,
                    viewportHeight);
        }
        if ((decoration.configBits & 0x200) != 0) {
            int orientation = face * Angle.QUARTER_TURN + Angle.FIVE_EIGHTHS_TURN & Angle.MASK;
            pickRenderable(decoration.renderable, orientation, decoration.x + Scene.WALL_DECORATION_OUTSET_X[face],
                    decoration.z, decoration.y + Scene.WALL_DECORATION_OUTSET_Y[face], decoration.uid, cameraX,
                    cameraY, cameraHeight, yawSin, yawCos, pitchSin, pitchCos, mouseX, mouseY, viewportWidth,
                    viewportHeight);
        }
    }

    private void pickActors(WorldState world, ActorSynchronizer actors, Player localPlayer, int plane, int cameraX,
            int cameraY, int cameraHeight, int yawSin, int yawCos, int pitchSin, int pitchCos, int mouseX, int mouseY,
            int viewportWidth, int viewportHeight) {
        if (staticModelsOnly || actors == null) {
            return;
        }
        if (localPlayer != null && localPlayer.isVisible()) {
            int tileX = localPlayer.x >> 7;
            int tileY = localPlayer.y >> 7;
            int uid = packEntityUid(SceneUid.TYPE_PLAYER, ActorSynchronizer.LOCAL_PLAYER_INDEX, tileX, tileY);
            int height = world.getTileHeight(localPlayer.x, localPlayer.y, plane);
            pickRenderable(localPlayer, localPlayer.rotation, localPlayer.x, height, localPlayer.y, uid, cameraX,
                    cameraY, cameraHeight, yawSin, yawCos, pitchSin, pitchCos, mouseX, mouseY, viewportWidth,
                    viewportHeight);
        }

        for (int index = 0; index < actors.playerCount; index++) {
            int playerIndex = actors.playerIndices[index];
            Player player = actors.players[playerIndex];
            if (player == null || player == localPlayer || !player.isVisible()) {
                continue;
            }
            int tileX = player.x >> 7;
            int tileY = player.y >> 7;
            int uid = packEntityUid(SceneUid.TYPE_PLAYER, playerIndex, tileX, tileY);
            int height = world.getTileHeight(player.x, player.y, plane);
            pickRenderable(player, player.rotation, player.x, height, player.y, uid, cameraX, cameraY, cameraHeight,
                    yawSin, yawCos, pitchSin, pitchCos, mouseX, mouseY, viewportWidth, viewportHeight);
        }

        for (int index = 0; index < actors.npcCount; index++) {
            int npcIndex = actors.npcIndices[index];
            Npc npc = actors.npcs[npcIndex];
            if (npc == null || !npc.isVisible() || npc.definition == null || !npc.definition.isMorphVisible()) {
                continue;
            }
            int tileX = npc.x >> 7;
            int tileY = npc.y >> 7;
            int uid = packEntityUid(SceneUid.TYPE_NPC, npcIndex, tileX, tileY);
            if (!npc.definition.clickable) {
                uid |= SceneUid.NON_INTERACTIVE_FLAG;
            }
            int height = world.getTileHeight(npc.x, npc.y, plane);
            pickRenderable(npc, npc.rotation, npc.x, height, npc.y, uid, cameraX, cameraY, cameraHeight, yawSin,
                    yawCos, pitchSin, pitchCos, mouseX, mouseY, viewportWidth, viewportHeight);
        }
    }

    private static int packEntityUid(int type, int id, int tileX, int tileY) {
        return (type << SceneUid.ENTITY_TYPE_SHIFT) | (id << SceneUid.ENTITY_ID_SHIFT)
                | ((tileY & SceneUid.TILE_COORDINATE_MASK) << SceneUid.TILE_Y_SHIFT)
                | (tileX & SceneUid.TILE_COORDINATE_MASK);
    }

    private void pickRenderable(Renderable renderable, int orientation, int worldX, int worldHeight, int worldY,
            int uid, int cameraX, int cameraY, int cameraHeight, int yawSin, int yawCos, int pitchSin, int pitchCos,
            int mouseX, int mouseY, int viewportWidth, int viewportHeight) {
        if (uid <= 0) {
            return;
        }
        if (hitRenderable(renderable, orientation, worldX, worldHeight, worldY, cameraX, cameraY, cameraHeight, yawSin,
                yawCos, pitchSin, pitchCos, mouseX, mouseY, viewportWidth, viewportHeight)) {
            addPickedUid(uid);
        }
    }

    private boolean hitRenderable(Renderable renderable, int orientation, int worldX, int worldHeight,
            int worldY, int cameraX, int cameraY, int cameraHeight, int yawSin, int yawCos, int pitchSin, int pitchCos,
            int mouseX, int mouseY, int viewportWidth, int viewportHeight) {
        if (renderable == null || staticModelsOnly && !(renderable instanceof Model)) {
            return false;
        }
        return renderable.hitTest(orientation, pitchSin, pitchCos, yawSin, yawCos,
                worldX - cameraX, worldHeight - cameraHeight, worldY - cameraY, mouseX, mouseY, viewportWidth,
                viewportHeight);
    }

    private static void addPickedUid(int uid) {
        if (uid <= 0) {
            return;
        }
        int count = Model.pickedCount;
        for (int index = 0; index < count; index++) {
            if (Model.pickedUids[index] == uid) {
                return;
            }
        }
        if (count < Model.pickedUids.length) {
            Model.pickedUids[count] = uid;
            Model.pickedCount = count + 1;
        }
    }

    private boolean pickTerrain(Scene scene, int renderPlane, int cameraX, int cameraY, int cameraHeight, int yawSin,
            int yawCos, int pitchSin, int pitchCos, int mouseX, int mouseY, int viewportWidth, int viewportHeight) {
        double viewX = mouseX - viewportWidth * 0.5;
        double viewY = mouseY - viewportHeight * 0.5;
        double viewDepth = 512.0;
        double sinYaw = yawSin / 65536.0;
        double cosYaw = yawCos / 65536.0;
        double sinPitch = pitchSin / 65536.0;
        double cosPitch = pitchCos / 65536.0;

        double relativeHeight = viewY * cosPitch + viewDepth * sinPitch;
        double yawDepth = -viewY * sinPitch + viewDepth * cosPitch;
        double rayX = viewX * cosYaw - yawDepth * sinYaw;
        double rayY = relativeHeight;
        double rayZ = viewX * sinYaw + yawDepth * cosYaw;

        double best = NO_HIT;
        int bestTileX = -1;
        int bestTileY = -1;
        int firstPlane = Math.max(0, scene.minPlane);
        int lastPlane = Math.min(scene.planeCount, scene.tiles.length);
        for (int plane = firstPlane; plane < lastPlane; plane++) {
            SceneTile[][] planeTiles = scene.tiles[plane];
            for (int tileX = 0; tileX < scene.width; tileX++) {
                for (int tileY = 0; tileY < scene.height; tileY++) {
                    SceneTile tile = planeTiles[tileX][tileY];
                    int minRenderPlane = minRenderPlane(tile);
                    if (minRenderPlane < 0 || minRenderPlane > renderPlane) {
                        continue;
                    }
                    if (tile.tileBelow != null) {
                        double distance = hitSurface(scene, tile.tileBelow, 0, tileX, tileY, cameraX, cameraHeight,
                                cameraY, rayX, rayY, rayZ);
                        if (distance < best) {
                            best = distance;
                            bestTileX = tileX;
                            bestTileY = tileY;
                        }
                    }
                    double distance = hitSurface(scene, tile, tile.renderLevel, tileX, tileY, cameraX, cameraHeight,
                            cameraY, rayX, rayY, rayZ);
                    if (distance < best) {
                        best = distance;
                        bestTileX = tileX;
                        bestTileY = tileY;
                    }
                }
            }
        }
        terrainTileX = bestTileX;
        terrainTileY = bestTileY;
        return bestTileX >= 0;
    }

    private static double hitSurface(Scene scene, SceneTile tile, int heightPlane, int tileX, int tileY,
            double originX, double originY, double originZ, double rayX, double rayY, double rayZ) {
        if (tile == null) {
            return NO_HIT;
        }
        GenericTile plain = tile.plainTile;
        if (plain != null) {
            if (heightPlane < 0 || heightPlane >= scene.tileHeights.length || tileX < 0 || tileY < 0
                    || tileX + 1 >= scene.tileHeights[heightPlane].length
                    || tileY + 1 >= scene.tileHeights[heightPlane][tileX].length) {
                return NO_HIT;
            }
            int worldX = tileX * TILE_SIZE;
            int worldY = tileY * TILE_SIZE;
            int southWestHeight = scene.tileHeights[heightPlane][tileX][tileY];
            int southEastHeight = scene.tileHeights[heightPlane][tileX + 1][tileY];
            int northEastHeight = scene.tileHeights[heightPlane][tileX + 1][tileY + 1];
            int northWestHeight = scene.tileHeights[heightPlane][tileX][tileY + 1];
            double best = NO_HIT;
            if (plain.texture >= 0 || plain.colourC != INVISIBLE_HSL) {
                best = rayTriangle(originX, originY, originZ, rayX, rayY, rayZ, worldX + TILE_SIZE,
                        northEastHeight, worldY + TILE_SIZE, worldX, northWestHeight, worldY + TILE_SIZE,
                        worldX + TILE_SIZE, southEastHeight, worldY);
            }
            if (plain.texture >= 0 || plain.colourA != INVISIBLE_HSL) {
                double second = rayTriangle(originX, originY, originZ, rayX, rayY, rayZ, worldX, southWestHeight,
                        worldY, worldX + TILE_SIZE, southEastHeight, worldY, worldX, northWestHeight,
                        worldY + TILE_SIZE);
                if (second < best) {
                    best = second;
                }
            }
            return best;
        }

        ComplexTile shaped = tile.shapedTile;
        if (shaped == null) {
            return NO_HIT;
        }
        double best = NO_HIT;
        int count = Math.min(shaped.triangleVertexA.length,
                Math.min(shaped.triangleVertexB.length, shaped.triangleVertexC.length));
        for (int triangle = 0; triangle < count; triangle++) {
            int texture = shaped.triangleTextures == null || triangle >= shaped.triangleTextures.length ? -1
                    : shaped.triangleTextures[triangle];
            if (texture < 0 && shaped.triangleHslA[triangle] == INVISIBLE_HSL) {
                continue;
            }
            int a = shaped.triangleVertexA[triangle];
            int b = shaped.triangleVertexB[triangle];
            int c = shaped.triangleVertexC[triangle];
            double distance = rayTriangle(originX, originY, originZ, rayX, rayY, rayZ, shaped.vertexX[a],
                    shaped.vertexY[a], shaped.vertexZ[a], shaped.vertexX[b], shaped.vertexY[b], shaped.vertexZ[b],
                    shaped.vertexX[c], shaped.vertexY[c], shaped.vertexZ[c]);
            if (distance < best) {
                best = distance;
            }
        }
        return best;
    }

    /** Moller-Trumbore ray/triangle intersection; positive ray parameter on hit. */
    private static double rayTriangle(double ox, double oy, double oz, double dx, double dy, double dz, double ax,
            double ay, double az, double bx, double by, double bz, double cx, double cy, double cz) {
        double edge1X = bx - ax;
        double edge1Y = by - ay;
        double edge1Z = bz - az;
        double edge2X = cx - ax;
        double edge2Y = cy - ay;
        double edge2Z = cz - az;

        double pX = dy * edge2Z - dz * edge2Y;
        double pY = dz * edge2X - dx * edge2Z;
        double pZ = dx * edge2Y - dy * edge2X;
        double determinant = edge1X * pX + edge1Y * pY + edge1Z * pZ;
        if (Math.abs(determinant) < 1.0e-9) {
            return NO_HIT;
        }
        double inverse = 1.0 / determinant;
        double tX = ox - ax;
        double tY = oy - ay;
        double tZ = oz - az;
        double u = (tX * pX + tY * pY + tZ * pZ) * inverse;
        if (u < 0.0 || u > 1.0) {
            return NO_HIT;
        }

        double qX = tY * edge1Z - tZ * edge1Y;
        double qY = tZ * edge1X - tX * edge1Z;
        double qZ = tX * edge1Y - tY * edge1X;
        double v = (dx * qX + dy * qY + dz * qZ) * inverse;
        if (v < 0.0 || u + v > 1.0) {
            return NO_HIT;
        }
        double distance = (edge2X * qX + edge2Y * qY + edge2Z * qZ) * inverse;
        return distance > 0.0 ? distance : NO_HIT;
    }

    private static int minRenderPlane(SceneTile tile) {
        if (tile == null || tile.logicHeight >= RENDER_PLANE_VARIANTS) {
            return -1;
        }
        return Math.max(0, tile.logicHeight);
    }
}
