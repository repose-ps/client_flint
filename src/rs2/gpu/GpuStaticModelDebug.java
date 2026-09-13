package rs2.gpu;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import rs2.media.Angle;
import rs2.media.model.Model;
import rs2.media.model.Renderable;
import rs2.scene.Scene;
import rs2.scene.SceneConfig;
import rs2.scene.SceneUid;
import rs2.scene.tile.ComplexTile;
import rs2.scene.tile.FloorDecoration;
import rs2.scene.tile.GenericTile;
import rs2.scene.tile.InteractiveObject;
import rs2.scene.tile.SceneTile;
import rs2.scene.tile.Wall;
import rs2.scene.tile.WallDecoration;

/**
 * Opt-in face-level diagnostics for a static model under the mouse cursor.
 *
 * <p>This deliberately mirrors the legacy model projection far enough to answer
 * whether a suspicious pixel is covered by a face that the software renderer
 * considers front-facing, whether the GPU uploader retains that face, and which
 * draw/alpha/priority metadata belongs to it. It never changes scene geometry.</p>
 */
final class GpuStaticModelDebug {

    private static final int SEARCH_RADIUS_TILES = 16;
    private static final int MAX_FACE_LINES_PER_MODEL = 16;

    private GpuStaticModelDebug() {
    }

    static void dumpHovered(Scene scene, int renderPlane, int cameraX, int cameraHeight, int cameraY, int yawSin,
            int yawCos, int pitchSin, int pitchCos, int mouseX, int mouseY, int viewportWidth, int viewportHeight) {
        int cameraTileX = cameraX >> 7;
        int cameraTileY = cameraY >> 7;
        int minX = Math.max(0, cameraTileX - SEARCH_RADIUS_TILES);
        int maxX = Math.min(scene.width - 1, cameraTileX + SEARCH_RADIUS_TILES);
        int minY = Math.max(0, cameraTileY - SEARCH_RADIUS_TILES);
        int maxY = Math.min(scene.height - 1, cameraTileY + SEARCH_RADIUS_TILES);

        List<ModelHit> hits = new ArrayList<>();
        Map<InteractiveObject, Boolean> seenObjects = new IdentityHashMap<>();

        int firstPlane = Math.max(0, scene.minPlane);
        int lastPlane = Math.min(scene.planeCount, scene.tiles.length);
        for (int plane = firstPlane; plane < lastPlane; plane++) {
            for (int tileX = minX; tileX <= maxX; tileX++) {
                for (int tileY = minY; tileY <= maxY; tileY++) {
                    SceneTile tile = scene.tiles[plane][tileX][tileY];
                    if (tile == null) {
                        continue;
                    }
                    collectTile(hits, seenObjects, tile, plane, tileX, tileY, renderPlane, cameraX, cameraHeight,
                            cameraY, yawSin, yawCos, pitchSin, pitchCos, mouseX, mouseY, viewportWidth,
                            viewportHeight);
                    if (tile.tileBelow != null) {
                        collectTile(hits, seenObjects, tile.tileBelow, plane, tileX, tileY, renderPlane, cameraX,
                                cameraHeight, cameraY, yawSin, yawCos, pitchSin, pitchCos, mouseX, mouseY,
                                viewportWidth, viewportHeight);
                    }
                }
            }
        }

        List<TerrainHit> terrainHits = terrainUnderCursor(scene, renderPlane, cameraX, cameraHeight, cameraY, yawSin,
                yawCos, pitchSin, pitchCos, mouseX, mouseY, viewportWidth, viewportHeight, minX, maxX, minY, maxY);

        hits.sort(Comparator.comparingDouble(ModelHit::nearestDepth));
        terrainHits.sort(Comparator.comparingDouble(TerrainHit::averageDepth));
        System.out.printf(
                "GPU static-model hover debug (software-front faces only): mouse=(%d,%d) camera=(%d,%d,%d) renderPlane=%d hits=%d terrainHits=%d%n",
                mouseX, mouseY, cameraX, cameraHeight, cameraY, renderPlane, hits.size(), terrainHits.size());
        if (hits.isEmpty()) {
            System.out.println("  no static Model face covers the cursor (a terrain surface or deferred/dynamic renderable may be visible there)");
        } else {
            for (ModelHit hit : hits) {
                dump(hit);
            }
        }
        if (terrainHits.isEmpty()) {
            System.out.println("  no cached terrain face covers the cursor");
        } else {
            System.out.println("  terrain faces under cursor (nearest first):");
            for (TerrainHit hit : terrainHits) {
                System.out.printf(
                        "    source=%s sceneP=%d tile=(%d,%d) renderLevel=%d logicHeight=%d heightP=%d kind=%s tri=%d tex=%d depth=(%d,%d,%d) avg=%.1f area=%d softwareFront=%s gpuUploaded=%s gpuEligible=%s%n",
                        hit.source(), hit.scenePlane(), hit.tileX(), hit.tileY(), hit.renderLevel(), hit.logicHeight(),
                        hit.heightPlane(), hit.kind(), hit.triangle(), hit.texture(), hit.depthA(), hit.depthB(),
                        hit.depthC(), hit.averageDepth(), hit.area(), hit.softwareFront(), hit.gpuUploaded(),
                        hit.gpuEligible());
            }
        }
    }


    private static List<TerrainHit> terrainUnderCursor(Scene scene, int renderPlane, int cameraX, int cameraHeight,
            int cameraY, int yawSin, int yawCos, int pitchSin, int pitchCos, int mouseX, int mouseY, int viewportWidth,
            int viewportHeight, int minX, int maxX, int minY, int maxY) {
        List<TerrainHit> hits = new ArrayList<>();
        int firstPlane = Math.max(0, scene.minPlane);
        int lastPlane = Math.min(scene.planeCount, scene.tiles.length);
        for (int plane = firstPlane; plane < lastPlane; plane++) {
            for (int tileX = minX; tileX <= maxX; tileX++) {
                for (int tileY = minY; tileY <= maxY; tileY++) {
                    SceneTile tile = scene.tiles[plane][tileX][tileY];
                    if (tile == null) {
                        continue;
                    }
                    if (tile.tileBelow != null) {
                        collectTerrainSurface(hits, scene, tile.tileBelow, "tile-below", plane, 0, tileX, tileY,
                                renderPlane, cameraX, cameraHeight, cameraY, yawSin, yawCos, pitchSin, pitchCos, mouseX,
                                mouseY, viewportWidth, viewportHeight);
                    }
                    collectTerrainSurface(hits, scene, tile, "tile", plane, tile.renderLevel, tileX, tileY, renderPlane,
                            cameraX, cameraHeight, cameraY, yawSin, yawCos, pitchSin, pitchCos, mouseX, mouseY,
                            viewportWidth, viewportHeight);
                }
            }
        }
        return hits;
    }

    private static void collectTerrainSurface(List<TerrainHit> hits, Scene scene, SceneTile tile, String source,
            int scenePlane, int heightPlane, int tileX, int tileY, int renderPlane, int cameraX, int cameraHeight,
            int cameraY, int yawSin, int yawCos, int pitchSin, int pitchCos, int mouseX, int mouseY, int viewportWidth,
            int viewportHeight) {
        if (tile == null || tile.logicHeight >= 4) {
            return;
        }
        boolean gpuEligible = tile.logicHeight <= renderPlane;
        if (tile.plainTile != null) {
            collectPlainTerrain(hits, scene, tile, tile.plainTile, source, scenePlane, heightPlane, tileX, tileY,
                    gpuEligible, cameraX, cameraHeight, cameraY, yawSin, yawCos, pitchSin, pitchCos, mouseX, mouseY,
                    viewportWidth, viewportHeight);
        } else if (tile.shapedTile != null) {
            collectShapedTerrain(hits, tile, tile.shapedTile, source, scenePlane, heightPlane, tileX, tileY, gpuEligible,
                    cameraX, cameraHeight, cameraY, yawSin, yawCos, pitchSin, pitchCos, mouseX, mouseY, viewportWidth,
                    viewportHeight);
        }
    }

    private static void collectPlainTerrain(List<TerrainHit> hits, Scene scene, SceneTile sceneTile, GenericTile tile,
            String source, int scenePlane, int heightPlane, int tileX, int tileY, boolean gpuEligible, int cameraX,
            int cameraHeight, int cameraY, int yawSin, int yawCos, int pitchSin, int pitchCos, int mouseX, int mouseY,
            int viewportWidth, int viewportHeight) {
        if (heightPlane < 0 || heightPlane >= scene.tileHeights.length || tileX + 1 >= scene.tileHeights[heightPlane].length
                || tileY + 1 >= scene.tileHeights[heightPlane][tileX].length) {
            return;
        }
        int worldX = tileX << 7;
        int worldY = tileY << 7;
        ProjectedVertex sw = project(worldX, scene.tileHeights[heightPlane][tileX][tileY], worldY, cameraX,
                cameraHeight, cameraY, yawSin, yawCos, pitchSin, pitchCos, viewportWidth, viewportHeight);
        ProjectedVertex se = project(worldX + 128, scene.tileHeights[heightPlane][tileX + 1][tileY], worldY, cameraX,
                cameraHeight, cameraY, yawSin, yawCos, pitchSin, pitchCos, viewportWidth, viewportHeight);
        ProjectedVertex ne = project(worldX + 128, scene.tileHeights[heightPlane][tileX + 1][tileY + 1], worldY + 128,
                cameraX, cameraHeight, cameraY, yawSin, yawCos, pitchSin, pitchCos, viewportWidth, viewportHeight);
        ProjectedVertex nw = project(worldX, scene.tileHeights[heightPlane][tileX][tileY + 1], worldY + 128, cameraX,
                cameraHeight, cameraY, yawSin, yawCos, pitchSin, pitchCos, viewportWidth, viewportHeight);
        if (!sw.inFront() || !se.inFront() || !ne.inFront() || !nw.inFront()) {
            return;
        }
        boolean textured = tile.texture >= 0 && tile.texture < 50;
        addTerrainHit(hits, source, scenePlane, tileX, tileY, sceneTile, heightPlane, "plain", 0, tile.texture, ne, nw,
                se, mouseX, mouseY, textured || tile.colourC != 0xbc614e, gpuEligible);
        addTerrainHit(hits, source, scenePlane, tileX, tileY, sceneTile, heightPlane, "plain", 1, tile.texture, sw, se,
                nw, mouseX, mouseY, textured || tile.colourA != 0xbc614e, gpuEligible);
    }

    private static void collectShapedTerrain(List<TerrainHit> hits, SceneTile sceneTile, ComplexTile tile, String source,
            int scenePlane, int heightPlane, int tileX, int tileY, boolean gpuEligible, int cameraX, int cameraHeight,
            int cameraY, int yawSin, int yawCos, int pitchSin, int pitchCos, int mouseX, int mouseY, int viewportWidth,
            int viewportHeight) {
        ProjectedVertex[] projected = new ProjectedVertex[tile.vertexX.length];
        for (int vertex = 0; vertex < tile.vertexX.length; vertex++) {
            projected[vertex] = project(tile.vertexX[vertex], tile.vertexY[vertex], tile.vertexZ[vertex], cameraX,
                    cameraHeight, cameraY, yawSin, yawCos, pitchSin, pitchCos, viewportWidth, viewportHeight);
            if (!projected[vertex].inFront()) {
                return;
            }
        }
        for (int triangle = 0; triangle < tile.triangleVertexA.length; triangle++) {
            int a = tile.triangleVertexA[triangle];
            int b = tile.triangleVertexB[triangle];
            int c = tile.triangleVertexC[triangle];
            int texture = tile.triangleTextures == null ? -1 : tile.triangleTextures[triangle];
            boolean uploaded = texture >= 0 && texture < 50 || tile.triangleHslA[triangle] != 0xbc614e;
            addTerrainHit(hits, source, scenePlane, tileX, tileY, sceneTile, heightPlane,
                    "shaped(shape=" + tile.shape + ",rot=" + tile.rotation + ")", triangle, texture, projected[a],
                    projected[b], projected[c], mouseX, mouseY, uploaded, gpuEligible);
        }
    }

    private static void addTerrainHit(List<TerrainHit> hits, String source, int scenePlane, int tileX, int tileY,
            SceneTile tile, int heightPlane, String kind, int triangle, int texture, ProjectedVertex a,
            ProjectedVertex b, ProjectedVertex c, int mouseX, int mouseY, boolean gpuUploaded, boolean gpuEligible) {
        if (!contains(mouseX, mouseY, a.screenX(), a.screenY(), b.screenX(), b.screenY(), c.screenX(), c.screenY())) {
            return;
        }
        long area = (long) (a.screenX() - b.screenX()) * (c.screenY() - b.screenY())
                - (long) (a.screenY() - b.screenY()) * (c.screenX() - b.screenX());
        hits.add(new TerrainHit(source, scenePlane, tileX, tileY, tile.renderLevel, tile.logicHeight, heightPlane, kind,
                triangle, texture, a.depth(), b.depth(), c.depth(), area, area > 0, gpuUploaded, gpuEligible));
    }

    private static ProjectedVertex project(int worldX, int worldHeight, int worldY, int cameraX, int cameraHeight,
            int cameraY, int yawSin, int yawCos, int pitchSin, int pitchCos, int viewportWidth, int viewportHeight) {
        int relX = worldX - cameraX;
        int relY = worldHeight - cameraHeight;
        int relZ = worldY - cameraY;
        int viewX = relZ * yawSin + relX * yawCos >> 16;
        int yawDepth = relZ * yawCos - relX * yawSin >> 16;
        int viewY = relY * pitchCos - yawDepth * pitchSin >> 16;
        int depth = relY * pitchSin + yawDepth * pitchCos >> 16;
        if (depth < 50) {
            return new ProjectedVertex(0, 0, depth, false);
        }
        int screenX = viewportWidth / 2 + (viewX << 9) / depth;
        int screenY = viewportHeight / 2 + (viewY << 9) / depth;
        return new ProjectedVertex(screenX, screenY, depth, true);
    }

    private static void collectTile(List<ModelHit> hits, Map<InteractiveObject, Boolean> seenObjects, SceneTile tile,
            int plane, int tileX, int tileY, int renderPlane, int cameraX, int cameraHeight, int cameraY, int yawSin,
            int yawCos, int pitchSin, int pitchCos, int mouseX, int mouseY, int viewportWidth, int viewportHeight) {
        if (tile == null || tile.renderLevel > renderPlane) {
            return;
        }

        Wall wall = tile.wall;
        if (wall != null) {
            collectRenderable(hits, "wall-primary", plane, tileX, tileY, wall.uid, wall.config, wall.primary, 0, wall.x,
                    wall.z, wall.y, cameraX, cameraHeight, cameraY, yawSin, yawCos, pitchSin, pitchCos, mouseX, mouseY,
                    viewportWidth, viewportHeight);
            collectRenderable(hits, "wall-secondary", plane, tileX, tileY, wall.uid, wall.config, wall.secondary, 0,
                    wall.x, wall.z, wall.y, cameraX, cameraHeight, cameraY, yawSin, yawCos, pitchSin, pitchCos, mouseX,
                    mouseY, viewportWidth, viewportHeight);
        }

        WallDecoration decoration = tile.wallDecoration;
        if (decoration != null) {
            if ((decoration.configBits & 0x300) == 0) {
                collectRenderable(hits, "wall-decoration", plane, tileX, tileY, decoration.uid, decoration.config,
                        decoration.renderable, decoration.face, decoration.x, decoration.z, decoration.y, cameraX,
                        cameraHeight, cameraY, yawSin, yawCos, pitchSin, pitchCos, mouseX, mouseY, viewportWidth,
                        viewportHeight);
            } else {
                int face = decoration.face & 3;
                if ((decoration.configBits & 0x100) != 0) {
                    int orientation = face * Angle.QUARTER_TURN + Angle.EIGHTH_TURN & Angle.MASK;
                    collectRenderable(hits, "wall-decoration-0x100", plane, tileX, tileY, decoration.uid,
                            decoration.config, decoration.renderable, orientation,
                            decoration.x + Scene.WALL_DECORATION_INSET_X[face], decoration.z,
                            decoration.y + Scene.WALL_DECORATION_INSET_Y[face], cameraX, cameraHeight, cameraY, yawSin,
                            yawCos, pitchSin, pitchCos, mouseX, mouseY, viewportWidth, viewportHeight);
                }
                if ((decoration.configBits & 0x200) != 0) {
                    int orientation = face * Angle.QUARTER_TURN + Angle.FIVE_EIGHTHS_TURN & Angle.MASK;
                    collectRenderable(hits, "wall-decoration-0x200", plane, tileX, tileY, decoration.uid,
                            decoration.config, decoration.renderable, orientation,
                            decoration.x + Scene.WALL_DECORATION_OUTSET_X[face], decoration.z,
                            decoration.y + Scene.WALL_DECORATION_OUTSET_Y[face], cameraX, cameraHeight, cameraY, yawSin,
                            yawCos, pitchSin, pitchCos, mouseX, mouseY, viewportWidth, viewportHeight);
                }
            }
        }

        FloorDecoration floor = tile.floorDecoration;
        if (floor != null) {
            collectRenderable(hits, "floor-decoration", plane, tileX, tileY, floor.uid, floor.config, floor.renderable,
                    0, floor.x, floor.z, floor.y, cameraX, cameraHeight, cameraY, yawSin, yawCos, pitchSin, pitchCos,
                    mouseX, mouseY, viewportWidth, viewportHeight);
        }

        for (int index = 0; index < tile.interactiveObjectCount; index++) {
            InteractiveObject object = tile.interactiveObjects[index];
            if (object == null || seenObjects.put(object, Boolean.TRUE) != null) {
                continue;
            }
            collectRenderable(hits, "interactive", plane, tileX, tileY, object.uid, object.config, object.renderable,
                    object.rotation, object.worldX, object.worldZ, object.worldY, cameraX, cameraHeight, cameraY,
                    yawSin, yawCos, pitchSin, pitchCos, mouseX, mouseY, viewportWidth, viewportHeight);
        }
    }

    private static void collectRenderable(List<ModelHit> hits, String kind, int plane, int tileX, int tileY, int uid,
            byte config, Renderable renderable, int orientation, int worldX, int worldHeight, int worldY, int cameraX,
            int cameraHeight, int cameraY, int yawSin, int yawCos, int pitchSin, int pitchCos, int mouseX, int mouseY,
            int viewportWidth, int viewportHeight) {
        if (!(renderable instanceof Model model) || model.vertexCount <= 0 || model.triangleCount <= 0) {
            return;
        }
        List<FaceHit> faceHits = facesUnderCursor(model, orientation, worldX, worldHeight, worldY, cameraX,
                cameraHeight, cameraY, yawSin, yawCos, pitchSin, pitchCos, mouseX, mouseY, viewportWidth,
                viewportHeight);
        if (faceHits.isEmpty()) {
            return;
        }
        double nearest = faceHits.stream().mapToDouble(FaceHit::averageDepth).min().orElse(Double.POSITIVE_INFINITY);
        int objectId = uid >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK;
        int type = SceneConfig.type(config);
        int configOrientation = SceneConfig.orientation(config);
        hits.add(new ModelHit(kind, plane, tileX, tileY, uid, objectId, type, configOrientation, orientation & Angle.MASK,
                worldX, worldHeight, worldY, model, faceHits, nearest));
    }

    private static List<FaceHit> facesUnderCursor(Model model, int orientation, int worldX, int worldHeight, int worldY,
            int cameraX, int cameraHeight, int cameraY, int yawSin, int yawCos, int pitchSin, int pitchCos, int mouseX,
            int mouseY, int viewportWidth, int viewportHeight) {
        int[] screenX = new int[model.vertexCount];
        int[] screenY = new int[model.vertexCount];
        int[] depth = new int[model.vertexCount];
        boolean[] inFront = new boolean[model.vertexCount];

        int sine = orientation == 0 ? 0 : rs2.media.Rasterizer3D.SINE[orientation & Angle.MASK];
        int cosine = orientation == 0 ? 65536 : rs2.media.Rasterizer3D.COSINE[orientation & Angle.MASK];
        int centerX = viewportWidth / 2;
        int centerY = viewportHeight / 2;
        for (int vertex = 0; vertex < model.vertexCount; vertex++) {
            int localX = model.verticesX[vertex];
            int localZ = model.verticesZ[vertex];
            if (orientation != 0) {
                int rotatedX = localZ * sine + localX * cosine >> 16;
                localZ = localZ * cosine - localX * sine >> 16;
                localX = rotatedX;
            }
            int relX = worldX + localX - cameraX;
            int relY = worldHeight + model.verticesY[vertex] - cameraHeight;
            int relZ = worldY + localZ - cameraY;
            int viewX = relZ * yawSin + relX * yawCos >> 16;
            int yawDepth = relZ * yawCos - relX * yawSin >> 16;
            int viewY = relY * pitchCos - yawDepth * pitchSin >> 16;
            int viewDepth = relY * pitchSin + yawDepth * pitchCos >> 16;
            depth[vertex] = viewDepth;
            if (viewDepth >= 50) {
                screenX[vertex] = centerX + (viewX << 9) / viewDepth;
                screenY[vertex] = centerY + (viewY << 9) / viewDepth;
                inFront[vertex] = true;
            }
        }

        List<FaceHit> hits = new ArrayList<>();
        for (int face = 0; face < model.triangleCount; face++) {
            if (!validFace(model, face)) {
                continue;
            }
            int a = model.triangleVertexA[face];
            int b = model.triangleVertexB[face];
            int c = model.triangleVertexC[face];
            if (!validVertex(model, a) || !validVertex(model, b) || !validVertex(model, c)) {
                continue;
            }
            boolean nearClipped = !inFront[a] || !inFront[b] || !inFront[c];
            if (nearClipped) {
                continue;
            }

            // Model.drawFaces() rejects non-positive projected area before drawing.
            // Filter those faces before the cursor containment test as well. Degenerate
            // and back-facing triangles otherwise account for most of the noisy hover
            // matches and can make an unrelated model look like the object under the
            // mouse.
            long area = (long) (screenX[a] - screenX[b]) * (screenY[c] - screenY[b])
                    - (long) (screenY[a] - screenY[b]) * (screenX[c] - screenX[b]);
            if (area <= 0) {
                continue;
            }
            if (!contains(mouseX, mouseY, screenX[a], screenY[a], screenX[b], screenY[b], screenX[c], screenY[c])) {
                continue;
            }

            int rawDrawType = value(model.triangleDrawType, face, 0);
            int drawType = rawDrawType == -1 ? -1 : rawDrawType & 3;
            int alpha = value(model.triangleAlpha, face, 0);
            boolean gpuUploaded = rawDrawType != -1 && !(drawType >= 0 && drawType < 2 && alpha >= 254);
            if (!gpuUploaded) {
                continue;
            }
            int fallbackPriority = model.defaultTrianglePriority >= 0 ? model.defaultTrianglePriority : 0;
            int priority = value(model.trianglePriorities, face, fallbackPriority);
            int shadeA = value(model.triangleShadeA, face, Integer.MIN_VALUE);
            int shadeB = value(model.triangleShadeB, face, Integer.MIN_VALUE);
            int shadeC = value(model.triangleShadeC, face, Integer.MIN_VALUE);
            int color = value(model.triangleColors, face, Integer.MIN_VALUE);
            String stream = drawType == 2 || drawType == 3 ? "textured" : "solid";
            hits.add(new FaceHit(face, a, b, c, rawDrawType, drawType, alpha, priority, color, shadeA, shadeB, shadeC,
                    depth[a], depth[b], depth[c], area, true, true, stream));
        }
        hits.sort(Comparator.comparingDouble(FaceHit::averageDepth));
        return hits;
    }

    private static void dump(ModelHit hit) {
        Model model = hit.model();
        int draw0 = 0, draw1 = 0, draw2 = 0, draw3 = 0, hidden = 0, alpha254 = 0;
        for (int face = 0; face < model.triangleCount; face++) {
            int raw = value(model.triangleDrawType, face, 0);
            if (raw == -1) {
                hidden++;
                continue;
            }
            switch (raw & 3) {
                case 0 -> draw0++;
                case 1 -> draw1++;
                case 2 -> draw2++;
                case 3 -> draw3++;
                default -> { }
            }
            if ((raw & 3) < 2 && value(model.triangleAlpha, face, 0) >= 254) {
                alpha254++;
            }
        }

        System.out.printf(
                "  hit kind=%s p=%d tile=(%d,%d) id=%d uid=0x%08x type=%d cfgOri=%d gpuOri=%d world=(%d,%d,%d)%n",
                hit.kind(), hit.plane(), hit.tileX(), hit.tileY(), hit.objectId(), hit.uid(), hit.type(),
                hit.configOrientation(), hit.orientation(), hit.worldX(), hit.worldHeight(), hit.worldY());
        System.out.printf(
                "    model vertices=%d triangles=%d texRefs=%d singleTile=%s defaultPriority=%d arrays(draw=%s alpha=%s priority=%s colors=%s) drawCounts=[%d,%d,%d,%d] hidden=%d alpha254Solid=%d%n",
                model.vertexCount, model.triangleCount, model.texturedTriangleCount, model.singleTile,
                model.defaultTrianglePriority, model.triangleDrawType != null, model.triangleAlpha != null,
                model.trianglePriorities != null, model.triangleColors != null, draw0, draw1, draw2, draw3, hidden,
                alpha254);
        System.out.printf("    faces under cursor=%d (nearest first):%n", hit.faces().size());
        int lines = Math.min(MAX_FACE_LINES_PER_MODEL, hit.faces().size());
        for (int index = 0; index < lines; index++) {
            FaceHit face = hit.faces().get(index);
            System.out.printf(
                    "      f=%d v=(%d,%d,%d) stream=%s raw=%d draw=%d alpha=%d prio=%d color=%s shade=(%s,%s,%s) depth=(%d,%d,%d) avg=%.1f area=%d softwareFront=%s gpuUploaded=%s%n",
                    face.face(), face.a(), face.b(), face.c(), face.stream(), face.rawDrawType(), face.drawType(),
                    face.alpha(), face.priority(), formatValue(face.color()), formatValue(face.shadeA()),
                    formatValue(face.shadeB()), formatValue(face.shadeC()), face.depthA(), face.depthB(), face.depthC(),
                    face.averageDepth(), face.area(), face.softwareFront(), face.gpuUploaded());
        }
        if (hit.faces().size() > lines) {
            System.out.printf("      ... %d more covering faces omitted%n", hit.faces().size() - lines);
        }
    }

    private static String formatValue(int value) {
        return value == Integer.MIN_VALUE ? "n/a" : Integer.toString(value);
    }

    private static boolean validFace(Model model, int face) {
        return face >= 0 && model.triangleVertexA != null && face < model.triangleVertexA.length
                && model.triangleVertexB != null && face < model.triangleVertexB.length && model.triangleVertexC != null
                && face < model.triangleVertexC.length;
    }

    private static boolean validVertex(Model model, int vertex) {
        return vertex >= 0 && vertex < model.vertexCount && model.verticesX != null && vertex < model.verticesX.length
                && model.verticesY != null && vertex < model.verticesY.length && model.verticesZ != null
                && vertex < model.verticesZ.length;
    }

    private static int value(int[] values, int index, int fallback) {
        return values != null && index >= 0 && index < values.length ? values[index] : fallback;
    }

    private static boolean contains(int px, int py, int ax, int ay, int bx, int by, int cx, int cy) {
        long ab = edge(ax, ay, bx, by, px, py);
        long bc = edge(bx, by, cx, cy, px, py);
        long ca = edge(cx, cy, ax, ay, px, py);
        boolean negative = ab < 0 || bc < 0 || ca < 0;
        boolean positive = ab > 0 || bc > 0 || ca > 0;
        return !(negative && positive);
    }

    private static long edge(int ax, int ay, int bx, int by, int px, int py) {
        return (long) (px - ax) * (by - ay) - (long) (py - ay) * (bx - ax);
    }

    private record ProjectedVertex(int screenX, int screenY, int depth, boolean inFront) {
    }

    private record TerrainHit(String source, int scenePlane, int tileX, int tileY, int renderLevel, int logicHeight,
            int heightPlane, String kind, int triangle, int texture, int depthA, int depthB, int depthC, long area,
            boolean softwareFront, boolean gpuUploaded, boolean gpuEligible) {
        double averageDepth() {
            return (depthA + depthB + depthC) / 3.0;
        }
    }

    private record ModelHit(String kind, int plane, int tileX, int tileY, int uid, int objectId, int type,
            int configOrientation, int orientation, int worldX, int worldHeight, int worldY, Model model,
            List<FaceHit> faces, double nearestDepth) {
    }

    private record FaceHit(int face, int a, int b, int c, int rawDrawType, int drawType, int alpha, int priority,
            int color, int shadeA, int shadeB, int shadeC, int depthA, int depthB, int depthC, long area,
            boolean softwareFront, boolean gpuUploaded, String stream) {
        double averageDepth() {
            return (depthA + depthB + depthC) / 3.0;
        }
    }
}
