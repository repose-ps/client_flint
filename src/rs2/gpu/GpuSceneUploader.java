package rs2.gpu;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;

import rs2.media.Angle;
import rs2.media.Rasterizer3D;
import rs2.media.model.Model;
import rs2.media.model.Renderable;
import rs2.scene.Scene;
import rs2.scene.SceneUid;
import rs2.scene.tile.ComplexTile;
import rs2.scene.tile.FloorDecoration;
import rs2.scene.tile.GenericTile;
import rs2.scene.tile.InteractiveObject;
import rs2.scene.tile.SceneTile;
import rs2.scene.tile.Wall;
import rs2.scene.tile.WallDecoration;

/** Converts loaded revision-377 terrain and static scene models into GPU-friendly triangles. */
public final class GpuSceneUploader {

    private static final int PRIORITY_TAG_NONE = 0;
    private static final int PRIORITY_TAG_WALL_DECORATION = 0x40;
    private static final int PRIORITY_TAG_FLOOR_DECORATION = 0x80;
    private static final int PRIORITY_TAG_ROOF_OBJECT = 0xc0;
    private static final boolean WALL_PRIORITY_PAINTER_DEBUG = Boolean
            .getBoolean("flint.gpu.staticPriorityWallPainterDebug");
    private static final int WALL_PRIORITY_PAINTER_OBJECT_ID = Integer
            .getInteger("flint.gpu.staticPriorityWallPainterObjectId", -1);
    private static final boolean WALL_DEPTH_PAINTER_DEBUG = Boolean
            .getBoolean("flint.gpu.staticDepthWallPainterDebug");
    private static final int WALL_DEPTH_PAINTER_OBJECT_ID = Integer
            .getInteger("flint.gpu.staticDepthWallPainterObjectId", -1);
    private static final boolean INTERACTIVE_DEPTH_PAINTER_DEBUG = Boolean
            .getBoolean("flint.gpu.staticDepthInteractivePainterDebug");
    private static final int INTERACTIVE_DEPTH_PAINTER_OBJECT_ID = Integer
            .getInteger("flint.gpu.staticDepthInteractivePainterObjectId", -1);
    private static final boolean STATIC_LEGACY_GOURAUD_DEBUG = Boolean
            .getBoolean("flint.gpu.staticLegacyGouraudDebug");
    private static final int STATIC_LEGACY_GOURAUD_OBJECT_ID = Integer
            .getInteger("flint.gpu.staticLegacyGouraudObjectId", -1);
    private static final boolean STATIC_ALPHA_BLEND_DEBUG = Boolean
            .getBoolean("flint.gpu.staticAlphaBlendDebug");
    private static final int STATIC_ALPHA_BLEND_OBJECT_ID = Integer
            .getInteger("flint.gpu.staticAlphaBlendObjectId", 11669);

    /** Scene geometry is grouped into 8x8-tile ranges for cheap per-frame culling. */
    public static final int CHUNK_SIZE = 8;

    private static final int TILE_SIZE = 128;
    private static final int CHUNK_WORLD_SIZE = CHUNK_SIZE * TILE_SIZE;
    private static final int RENDER_PLANE_VARIANTS = 4;
    private static final int INVISIBLE_HSL = 0xbc614e;

    private GpuSceneUploader() {
    }

    /**
     * Builds one render-plane-independent terrain mesh for the complete loaded scene.
     *
     * <p>Each 8x8 chunk is further split by the minimum RuneScape render plane
     * required for that geometry. The renderer therefore keeps one terrain VBO
     * instead of four near-identical plane variants while still omitting roof-hidden
     * ranges from draw submission.</p>
     */
    public static GpuTerrainMesh buildTerrain(Scene scene) {
        int chunkColumns = chunkCount(scene.width);
        int chunkRows = chunkCount(scene.height);
        GpuTerrainVertexBuilder[] chunkVertices = new GpuTerrainVertexBuilder[chunkColumns * chunkRows * RENDER_PLANE_VARIANTS];
        int surfaceCount = 0;
        int triangleCount = 0;

        int firstPlane = Math.max(0, scene.minPlane);
        int lastPlane = Math.min(scene.planeCount, scene.tiles.length);
        for (int plane = firstPlane; plane < lastPlane; plane++) {
            SceneTile[][] planeTiles = scene.tiles[plane];
            for (int x = 0; x < scene.width; x++) {
                for (int y = 0; y < scene.height; y++) {
                    SceneTile tile = planeTiles[x][y];
                    int minRenderPlane = minRenderPlane(tile);
                    if (minRenderPlane < 0) {
                        continue;
                    }

                    GpuTerrainVertexBuilder vertices = terrainChunk(chunkVertices, chunkRows, x, y, minRenderPlane);
                    if (tile.tileBelow != null) {
                        int belowTriangles = appendSurface(scene, tile.tileBelow, 0, x, y, vertices);
                        if (belowTriangles > 0) {
                            surfaceCount++;
                            triangleCount += belowTriangles;
                        }
                    }

                    int tileTriangles = appendSurface(scene, tile, tile.renderLevel, x, y, vertices);
                    if (tileTriangles > 0) {
                        surfaceCount++;
                        triangleCount += tileTriangles;
                    }
                }
            }
        }

        return finishTerrain(chunkVertices, surfaceCount, triangleCount);
    }

    /** Builds one render-plane-independent pre-transformed mesh for static scene models. */
    public static GpuStaticSceneMesh buildStaticGeometry(Scene scene) {
        StaticChunkSet chunks = new StaticChunkSet(scene.width, scene.height);
        Map<InteractiveObject, Integer> objectPlanes = collectInteractiveObjectPlanes(scene);
        Map<InteractiveObject, Boolean> uploadedObjects = new IdentityHashMap<>();
        Map<Model, Boolean> uniqueModels = new IdentityHashMap<>();
        int instanceCount = 0;
        int triangleCount = 0;
        int skippedDynamicCount = 0;

        int firstPlane = Math.max(0, scene.minPlane);
        int lastPlane = Math.min(scene.planeCount, scene.tiles.length);
        for (int plane = firstPlane; plane < lastPlane; plane++) {
            SceneTile[][] planeTiles = scene.tiles[plane];
            for (int x = 0; x < scene.width; x++) {
                for (int y = 0; y < scene.height; y++) {
                    SceneTile tile = planeTiles[x][y];
                    int tilePlane = minRenderPlane(tile);
                    if (tilePlane < 0) {
                        continue;
                    }

                    if (tile.tileBelow != null) {
                        SceneTile below = tile.tileBelow;
                        if (below.wall != null) {
                            UploadTally tally = appendRenderable(below.wall.primary, 0, below.wall.x, below.wall.z,
                                    below.wall.y, tilePlane, chunks, uniqueModels);
                            instanceCount += tally.instances;
                            triangleCount += tally.triangles;
                            skippedDynamicCount += tally.skippedDynamic;
                        }
                        for (int objectIndex = 0; objectIndex < below.interactiveObjectCount; objectIndex++) {
                            InteractiveObject object = below.interactiveObjects[objectIndex];
                            if (object != null && uploadedObjects.put(object, Boolean.TRUE) == null) {
                                int objectPlane = objectPlanes.getOrDefault(object, tilePlane);
                                UploadTally tally = appendInteractiveObject(object, objectPlane, chunks, uniqueModels);
                                instanceCount += tally.instances;
                                triangleCount += tally.triangles;
                                skippedDynamicCount += tally.skippedDynamic;
                            }
                        }
                    }

                    Wall wall = tile.wall;
                    if (wall != null) {
                        UploadTally primary = appendRenderable(wall.primary, 0, wall.x, wall.z, wall.y, tilePlane,
                                chunks, uniqueModels);
                        instanceCount += primary.instances;
                        triangleCount += primary.triangles;
                        skippedDynamicCount += primary.skippedDynamic;
                        UploadTally secondary = appendRenderable(wall.secondary, 0, wall.x, wall.z, wall.y, tilePlane,
                                chunks, uniqueModels);
                        instanceCount += secondary.instances;
                        triangleCount += secondary.triangles;
                        skippedDynamicCount += secondary.skippedDynamic;
                    }

                    WallDecoration wallDecoration = tile.wallDecoration;
                    if (wallDecoration != null) {
                        UploadTally tally = appendWallDecoration(wallDecoration, tilePlane, chunks, uniqueModels);
                        instanceCount += tally.instances;
                        triangleCount += tally.triangles;
                        skippedDynamicCount += tally.skippedDynamic;
                    }

                    FloorDecoration floorDecoration = tile.floorDecoration;
                    if (floorDecoration != null) {
                        UploadTally tally = appendFloorDecoration(floorDecoration, tilePlane, chunks, uniqueModels);
                        instanceCount += tally.instances;
                        triangleCount += tally.triangles;
                        skippedDynamicCount += tally.skippedDynamic;
                    }

                    for (int objectIndex = 0; objectIndex < tile.interactiveObjectCount; objectIndex++) {
                        InteractiveObject object = tile.interactiveObjects[objectIndex];
                        if (object == null || uploadedObjects.put(object, Boolean.TRUE) != null) {
                            continue;
                        }
                        int objectPlane = objectPlanes.getOrDefault(object, tilePlane);
                        UploadTally tally = appendInteractiveObject(object, objectPlane, chunks, uniqueModels);
                        instanceCount += tally.instances;
                        triangleCount += tally.triangles;
                        skippedDynamicCount += tally.skippedDynamic;
                    }
                }
            }
        }

        return chunks.finish(instanceCount, uniqueModels.size(), triangleCount, skippedDynamicCount);
    }

    private static Map<InteractiveObject, Integer> collectInteractiveObjectPlanes(Scene scene) {
        Map<InteractiveObject, Integer> planes = new IdentityHashMap<>();
        int firstPlane = Math.max(0, scene.minPlane);
        int lastPlane = Math.min(scene.planeCount, scene.tiles.length);
        for (int plane = firstPlane; plane < lastPlane; plane++) {
            SceneTile[][] planeTiles = scene.tiles[plane];
            for (int x = 0; x < scene.width; x++) {
                for (int y = 0; y < scene.height; y++) {
                    SceneTile tile = planeTiles[x][y];
                    int tilePlane = minRenderPlane(tile);
                    if (tilePlane < 0) {
                        continue;
                    }
                    collectObjectPlanes(planes, tile, tilePlane);
                    if (tile.tileBelow != null) {
                        collectObjectPlanes(planes, tile.tileBelow, tilePlane);
                    }
                }
            }
        }
        return planes;
    }

    private static void collectObjectPlanes(Map<InteractiveObject, Integer> planes, SceneTile tile, int minPlane) {
        for (int objectIndex = 0; objectIndex < tile.interactiveObjectCount; objectIndex++) {
            InteractiveObject object = tile.interactiveObjects[objectIndex];
            if (object != null) {
                planes.merge(object, minPlane, Math::min);
            }
        }
    }


    private static UploadTally appendFloorDecoration(FloorDecoration decoration, int minRenderPlane,
            StaticChunkSet chunks, Map<Model, Boolean> uniqueModels) {
        int objectId = decoration.uid >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK;
        if (decoration.renderable instanceof Model model && model.trianglePriorities != null
                && !hasLegacySolidAlpha(model)) {
            // Floor decorations call Model.draw() just like interactive objects in the
            // software scene renderer. Priority-bearing models therefore need the same
            // camera-dependent Model.drawFaces() compatibility path; leaving them in the
            // ordinary depth-buffered static mesh causes their coplanar/detail faces to
            // flicker or disappear as the camera angle changes.
            chunks.addInteractivePriorityObject(new GpuStaticSceneMesh.InteractivePriorityObject(objectId, 0,
                    decoration.x, decoration.z, decoration.y, minRenderPlane, PRIORITY_TAG_FLOOR_DECORATION, model));
            uniqueModels.put(model, Boolean.TRUE);
            return new UploadTally(1, GpuModelUploader.drawableTriangleCount(model), 0);
        }
        return appendRenderable(decoration.renderable, 0, decoration.x, decoration.z, decoration.y, minRenderPlane,
                chunks, uniqueModels, PRIORITY_TAG_FLOOR_DECORATION);
    }

    private static UploadTally appendInteractiveObject(InteractiveObject object, int minRenderPlane,
            StaticChunkSet chunks, Map<Model, Boolean> uniqueModels) {
        int objectId = object.uid >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK;
        int objectType = rs2.scene.SceneConfig.type(object.config);
        boolean roofObject = objectType >= 12 && objectType <= 21;
        if (STATIC_LEGACY_GOURAUD_DEBUG
                && (STATIC_LEGACY_GOURAUD_OBJECT_ID < 0 || STATIC_LEGACY_GOURAUD_OBJECT_ID == objectId)
                && object.renderable instanceof Model model) {
            GpuVertexBuilder solidVertices = chunks.solidBuilderForWorld(object.worldX, object.worldY, minRenderPlane);
            GpuModelTextureBuilder texturedVertices = model.texturedTriangleCount > 0 && model.triangleDrawType != null
                    ? chunks.texturedBuilderForWorld(object.worldX, object.worldY, minRenderPlane)
                    : null;
            GpuModelUploader.UploadResult result = GpuModelUploader.appendLegacyGouraud(model, object.rotation,
                    object.worldX, object.worldZ, object.worldY, solidVertices, texturedVertices);
            if (result.instances > 0) {
                uniqueModels.put(model, Boolean.TRUE);
            }
            return new UploadTally(result.instances, result.triangles, 0);
        }

        if (STATIC_ALPHA_BLEND_DEBUG && objectId == STATIC_ALPHA_BLEND_OBJECT_ID
                && object.renderable instanceof Model model && model.triangleAlpha != null) {
            int triangles = GpuModelUploader.drawableLegacyAlphaTriangleCount(model);
            if (triangles > 0) {
                chunks.addAlphaPriorityObject(new GpuStaticSceneMesh.AlphaPriorityObject(objectId, object.rotation,
                        object.worldX, object.worldZ, object.worldY, minRenderPlane, model));
                uniqueModels.put(model, Boolean.TRUE);
                return new UploadTally(1, triangles, 0);
            }
            return UploadTally.EMPTY;
        }

        // Revision 377 routes every priority-bearing Model through Model.drawFaces().
        // That ordering is semantic, not an object-specific quirk. Defer every opaque
        // priority-bearing interactive to the compatibility painter. The painter now
        // commits depth after each individual object, so unrelated deferred objects
        // still occlude one another normally. Alpha-bearing models stay out of this
        // opaque path and are handled separately by the legacy-alpha compatibility path.
        boolean interactiveDepthPainter = INTERACTIVE_DEPTH_PAINTER_DEBUG
                && (INTERACTIVE_DEPTH_PAINTER_OBJECT_ID < 0 || INTERACTIVE_DEPTH_PAINTER_OBJECT_ID == objectId);
        if (object.renderable instanceof Model model
                && ((((model.trianglePriorities != null || roofObject) && !hasLegacySolidAlpha(model)))
                        || (interactiveDepthPainter && model.trianglePriorities == null))) {
            // Object shapes 12..21 are the one-tile roof/object shapes. Even when they
            // have no trianglePriorities array, revision 377 still submits the Model via
            // Model.drawFaces() and paints higher render-plane roof pieces after lower
            // ones. Defer those models too so the compatibility painter can preserve the
            // model-local face order and apply a narrowly-scoped cross-plane roof bias.
            int priorityMetadataTag = roofObject ? PRIORITY_TAG_ROOF_OBJECT : PRIORITY_TAG_NONE;
            chunks.addInteractivePriorityObject(new GpuStaticSceneMesh.InteractivePriorityObject(objectId,
                    object.rotation, object.worldX, object.worldZ, object.worldY, minRenderPlane, priorityMetadataTag,
                    model));
            uniqueModels.put(model, Boolean.TRUE);
            return new UploadTally(1, GpuModelUploader.drawableTriangleCount(model), 0);
        }

        return appendRenderable(object.renderable, object.rotation, object.worldX, object.worldZ, object.worldY,
                minRenderPlane, chunks, uniqueModels);
    }

    private static UploadTally appendWallDecoration(WallDecoration decoration, int minRenderPlane,
            StaticChunkSet chunks, Map<Model, Boolean> uniqueModels) {
        int objectId = decoration.uid >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK;
        boolean wallPriorityPainter = WALL_PRIORITY_PAINTER_DEBUG
                && (WALL_PRIORITY_PAINTER_OBJECT_ID < 0 || WALL_PRIORITY_PAINTER_OBJECT_ID == objectId);
        boolean wallDepthPainter = WALL_DEPTH_PAINTER_DEBUG
                && (WALL_DEPTH_PAINTER_OBJECT_ID < 0 || WALL_DEPTH_PAINTER_OBJECT_ID == objectId);

        // The compatibility painter below intentionally handles ordinary type-4/5
        // decorations only. The 0x100/0x200 variants are scene-painter constructs
        // whose early/late tile phases are separate from Model.drawFaces ordering.
        // Keep those on the existing static path rather than duplicating them here.
        if ((decoration.configBits & 0x300) == 0) {
            if (decoration.renderable instanceof Model model
                    && ((wallPriorityPainter && model.trianglePriorities != null)
                            || (wallDepthPainter && model.trianglePriorities == null))) {
                chunks.addWallPriorityDecoration(new GpuStaticSceneMesh.WallPriorityDecoration(objectId,
                        decoration.face, decoration.x, decoration.z, decoration.y, minRenderPlane, model));
                uniqueModels.put(model, Boolean.TRUE);
                return new UploadTally(1, GpuModelUploader.drawableTriangleCount(model), 0);
            }
            return appendRenderable(decoration.renderable, decoration.face, decoration.x, decoration.z,
                    decoration.y, minRenderPlane, chunks, uniqueModels, PRIORITY_TAG_WALL_DECORATION);
        }

        int face = decoration.face & 3;
        UploadTally total = UploadTally.EMPTY;
        if ((decoration.configBits & 0x100) != 0) {
            int orientation = face * Angle.QUARTER_TURN + Angle.EIGHTH_TURN & Angle.MASK;
            total = total.plus(appendRenderable(decoration.renderable, orientation,
                    decoration.x + Scene.WALL_DECORATION_INSET_X[face], decoration.z,
                    decoration.y + Scene.WALL_DECORATION_INSET_Y[face], minRenderPlane, chunks, uniqueModels,
                    PRIORITY_TAG_WALL_DECORATION));
        }
        if ((decoration.configBits & 0x200) != 0) {
            int orientation = face * Angle.QUARTER_TURN + Angle.FIVE_EIGHTHS_TURN & Angle.MASK;
            total = total.plus(appendRenderable(decoration.renderable, orientation,
                    decoration.x + Scene.WALL_DECORATION_OUTSET_X[face], decoration.z,
                    decoration.y + Scene.WALL_DECORATION_OUTSET_Y[face], minRenderPlane, chunks, uniqueModels,
                    PRIORITY_TAG_WALL_DECORATION));
        }
        return total;
    }

    private static UploadTally appendRenderable(Renderable renderable, int orientation, int worldX, int worldHeight,
            int worldY, int minRenderPlane, StaticChunkSet chunks, Map<Model, Boolean> uniqueModels) {
        return appendRenderable(renderable, orientation, worldX, worldHeight, worldY, minRenderPlane, chunks,
                uniqueModels, PRIORITY_TAG_NONE);
    }

    private static UploadTally appendRenderable(Renderable renderable, int orientation, int worldX, int worldHeight,
            int worldY, int minRenderPlane, StaticChunkSet chunks, Map<Model, Boolean> uniqueModels,
            int priorityMetadataTag) {
        if (renderable == null) {
            return UploadTally.EMPTY;
        }
        if (!(renderable instanceof Model model)) {
            return new UploadTally(0, 0, 1);
        }
        GpuVertexBuilder solidVertices = chunks.solidBuilderForWorld(worldX, worldY, minRenderPlane);
        GpuModelTextureBuilder texturedVertices = model.texturedTriangleCount > 0 && model.triangleDrawType != null
                ? chunks.texturedBuilderForWorld(worldX, worldY, minRenderPlane)
                : null;
        GpuModelUploader.UploadResult result = GpuModelUploader.append(model, orientation, worldX, worldHeight, worldY,
                solidVertices, texturedVertices, true, priorityMetadataTag);
        if (result.instances > 0) {
            uniqueModels.put(model, Boolean.TRUE);
        }
        return new UploadTally(result.instances, result.triangles, 0);
    }

    private record UploadTally(int instances, int triangles, int skippedDynamic) {
        private static final UploadTally EMPTY = new UploadTally(0, 0, 0);

        UploadTally plus(UploadTally other) {
            return new UploadTally(instances + other.instances, triangles + other.triangles,
                    skippedDynamic + other.skippedDynamic);
        }
    }

    private static GpuTerrainVertexBuilder terrainChunk(GpuTerrainVertexBuilder[] chunks, int chunkRows, int tileX,
            int tileY, int minRenderPlane) {
        int chunkX = tileX / CHUNK_SIZE;
        int chunkY = tileY / CHUNK_SIZE;
        int index = (chunkX * chunkRows + chunkY) * RENDER_PLANE_VARIANTS + minRenderPlane;
        GpuTerrainVertexBuilder vertices = chunks[index];
        if (vertices == null) {
            vertices = new GpuTerrainVertexBuilder(384);
            chunks[index] = vertices;
        }
        return vertices;
    }

    private static GpuTerrainMesh finishTerrain(GpuTerrainVertexBuilder[] chunkVertices, int surfaceCount,
            int triangleCount) {
        int totalWords = 0;
        int nonEmptyChunks = 0;
        for (GpuTerrainVertexBuilder chunk : chunkVertices) {
            if (chunk != null && chunk.vertexCount() > 0) {
                totalWords += chunk.wordCount();
                nonEmptyChunks++;
            }
        }

        int[] vertices = new int[totalWords];
        GpuSceneChunk[] ranges = new GpuSceneChunk[nonEmptyChunks];
        int wordOffset = 0;
        int rangeIndex = 0;
        for (int index = 0; index < chunkVertices.length; index++) {
            GpuTerrainVertexBuilder chunk = chunkVertices[index];
            if (chunk == null || chunk.vertexCount() == 0) {
                continue;
            }
            int minRenderPlane = index % RENDER_PLANE_VARIANTS;
            ranges[rangeIndex++] = chunk.toChunk(wordOffset / GpuTerrainVertexBuilder.WORDS_PER_VERTEX,
                    minRenderPlane);
            chunk.copyTo(vertices, wordOffset);
            wordOffset += chunk.wordCount();
        }
        return new GpuTerrainMesh(vertices, surfaceCount, triangleCount, ranges);
    }

    private static int appendSurface(Scene scene, SceneTile tile, int heightPlane, int tileX, int tileY,
            GpuTerrainVertexBuilder vertices) {
        if (tile.plainTile != null) {
            return appendPlainTile(scene, tile.plainTile, heightPlane, tileX, tileY, vertices);
        }
        if (tile.shapedTile != null) {
            return appendShapedTile(tile.shapedTile, tileX, tileY, vertices);
        }
        return 0;
    }

    private static int appendPlainTile(Scene scene, GenericTile tile, int plane, int tileX, int tileY,
            GpuTerrainVertexBuilder vertices) {
        if (plane < 0 || plane >= scene.tileHeights.length) {
            return 0;
        }

        int worldX = tileX * TILE_SIZE;
        int worldY = tileY * TILE_SIZE;
        int southWestHeight = scene.tileHeights[plane][tileX][tileY];
        int southEastHeight = scene.tileHeights[plane][tileX + 1][tileY];
        int northEastHeight = scene.tileHeights[plane][tileX + 1][tileY + 1];
        int northWestHeight = scene.tileHeights[plane][tileX][tileY + 1];
        boolean textured = validTexture(tile.texture);

        int triangles = 0;
        if (textured || tile.colourC != INVISIBLE_HSL) {
            appendTerrainVertex(vertices, worldX + TILE_SIZE, northEastHeight, worldY + TILE_SIZE, tile.colourC,
                    tile.texture, GpuTerrainVertexBuilder.UV_FIXED_ONE, GpuTerrainVertexBuilder.UV_FIXED_ONE);
            appendTerrainVertex(vertices, worldX, northWestHeight, worldY + TILE_SIZE, tile.colourD, tile.texture, 0,
                    GpuTerrainVertexBuilder.UV_FIXED_ONE);
            appendTerrainVertex(vertices, worldX + TILE_SIZE, southEastHeight, worldY, tile.colourB, tile.texture,
                    GpuTerrainVertexBuilder.UV_FIXED_ONE, 0);
            triangles++;
        }
        if (textured || tile.colourA != INVISIBLE_HSL) {
            appendTerrainVertex(vertices, worldX, southWestHeight, worldY, tile.colourA, tile.texture, 0, 0);
            appendTerrainVertex(vertices, worldX + TILE_SIZE, southEastHeight, worldY, tile.colourB, tile.texture,
                    GpuTerrainVertexBuilder.UV_FIXED_ONE, 0);
            appendTerrainVertex(vertices, worldX, northWestHeight, worldY + TILE_SIZE, tile.colourD, tile.texture, 0,
                    GpuTerrainVertexBuilder.UV_FIXED_ONE);
            triangles++;
        }
        return triangles;
    }

    private static int appendShapedTile(ComplexTile tile, int tileX, int tileY, GpuTerrainVertexBuilder vertices) {
        int triangles = 0;
        int worldX = tileX * TILE_SIZE;
        int worldY = tileY * TILE_SIZE;
        for (int triangle = 0; triangle < tile.triangleVertexA.length; triangle++) {
            int colourA = tile.triangleHslA[triangle];
            int colourB = tile.triangleHslB[triangle];
            int colourC = tile.triangleHslC[triangle];
            int texture = tile.triangleTextures == null ? -1 : tile.triangleTextures[triangle];
            boolean textured = validTexture(texture);
            if (!textured && colourA == INVISIBLE_HSL) {
                continue;
            }

            appendIndexedTerrainVertex(vertices, tile, tile.triangleVertexA[triangle], colourA, texture, worldX,
                    worldY);
            appendIndexedTerrainVertex(vertices, tile, tile.triangleVertexB[triangle], colourB, texture, worldX,
                    worldY);
            appendIndexedTerrainVertex(vertices, tile, tile.triangleVertexC[triangle], colourC, texture, worldX,
                    worldY);
            triangles++;
        }
        return triangles;
    }

    private static void appendIndexedTerrainVertex(GpuTerrainVertexBuilder vertices, ComplexTile tile, int index,
            int hslOrShade, int texture, int tileWorldX, int tileWorldY) {
        int uFixed = (tile.vertexX[index] - tileWorldX) * GpuTerrainVertexBuilder.UV_FIXED_ONE / TILE_SIZE;
        int vFixed = (tile.vertexZ[index] - tileWorldY) * GpuTerrainVertexBuilder.UV_FIXED_ONE / TILE_SIZE;
        appendTerrainVertex(vertices, tile.vertexX[index], tile.vertexY[index], tile.vertexZ[index], hslOrShade,
                texture, uFixed, vFixed);
    }

    private static void appendTerrainVertex(GpuTerrainVertexBuilder vertices, int x, int height, int y,
            int hslOrShade, int texture, int uFixed, int vFixed) {
        if (validTexture(texture)) {
            vertices.addTextured(x, height, y, hslOrShade, uFixed, vFixed, texture);
        } else {
            vertices.addUntextured(x, height, y, hslToRgb(hslOrShade));
        }
    }

    private static boolean validTexture(int texture) {
        return texture >= 0 && texture < 50;
    }

    private static int hslToRgb(int hsl) {
        if (hsl == INVISIBLE_HSL) {
            return 0;
        }
        int[] palette = Rasterizer3D.HSL_TO_RGB;
        if (palette == null || palette.length == 0) {
            return 0xffffff;
        }
        return palette[hsl & 0xffff];
    }

    private static boolean hasLegacySolidAlpha(Model model) {
        if (model == null || model.triangleAlpha == null) {
            return false;
        }
        int count = Math.min(model.triangleCount, model.triangleAlpha.length);
        for (int triangle = 0; triangle < count; triangle++) {
            if ((model.triangleAlpha[triangle] & 0xff) == 0) {
                continue;
            }
            int drawType = model.triangleDrawType == null ? 0 : model.triangleDrawType[triangle] & 3;
            if (drawType < 2) {
                return true;
            }
        }
        return false;
    }

    private static int minRenderPlane(SceneTile tile) {
        if (tile == null || tile.logicHeight >= RENDER_PLANE_VARIANTS) {
            return -1;
        }
        return Math.max(0, tile.logicHeight);
    }

    private static int chunkCount(int tiles) {
        return Math.max(1, (tiles + CHUNK_SIZE - 1) / CHUNK_SIZE);
    }

    /** Per-chunk/per-plane static-model accumulators concatenated into compact draw streams. */
    private static final class StaticChunkSet {
        private final int chunkColumns;
        private final int chunkRows;
        private final GpuVertexBuilder[] solidBuilders;
        private final GpuModelTextureBuilder[] texturedBuilders;
        private final ArrayList<GpuStaticSceneMesh.WallPriorityDecoration> wallPriorityDecorations = new ArrayList<>();
        private final ArrayList<GpuStaticSceneMesh.InteractivePriorityObject> interactivePriorityObjects = new ArrayList<>();
        private final ArrayList<GpuStaticSceneMesh.AlphaPriorityObject> alphaPriorityObjects = new ArrayList<>();

        StaticChunkSet(int sceneWidth, int sceneHeight) {
            chunkColumns = chunkCount(sceneWidth);
            chunkRows = chunkCount(sceneHeight);
            int entries = chunkColumns * chunkRows * RENDER_PLANE_VARIANTS;
            solidBuilders = new GpuVertexBuilder[entries];
            texturedBuilders = new GpuModelTextureBuilder[entries];
        }

        GpuVertexBuilder solidBuilderForWorld(int worldX, int worldY, int minRenderPlane) {
            int index = builderIndex(worldX, worldY, minRenderPlane);
            GpuVertexBuilder builder = solidBuilders[index];
            if (builder == null) {
                builder = new GpuVertexBuilder(256);
                solidBuilders[index] = builder;
            }
            return builder;
        }

        GpuModelTextureBuilder texturedBuilderForWorld(int worldX, int worldY, int minRenderPlane) {
            int index = builderIndex(worldX, worldY, minRenderPlane);
            GpuModelTextureBuilder builder = texturedBuilders[index];
            if (builder == null) {
                builder = new GpuModelTextureBuilder(64);
                texturedBuilders[index] = builder;
            }
            return builder;
        }


        void addWallPriorityDecoration(GpuStaticSceneMesh.WallPriorityDecoration decoration) {
            wallPriorityDecorations.add(decoration);
        }

        void addInteractivePriorityObject(GpuStaticSceneMesh.InteractivePriorityObject object) {
            interactivePriorityObjects.add(object);
        }

        void addAlphaPriorityObject(GpuStaticSceneMesh.AlphaPriorityObject object) {
            alphaPriorityObjects.add(object);
        }

        private int builderIndex(int worldX, int worldY, int minRenderPlane) {
            int chunkX = Math.max(0, Math.min(chunkColumns - 1, Math.floorDiv(worldX, CHUNK_WORLD_SIZE)));
            int chunkY = Math.max(0, Math.min(chunkRows - 1, Math.floorDiv(worldY, CHUNK_WORLD_SIZE)));
            int chunkIndex = chunkX * chunkRows + chunkY;
            return chunkIndex * RENDER_PLANE_VARIANTS + minRenderPlane;
        }

        GpuStaticSceneMesh finish(int instanceCount, int uniqueModelCount, int triangleCount, int skippedDynamicCount) {
            int solidWords = wordCount(solidBuilders);
            int solidChunkCount = solidChunkCount(solidBuilders);
            int texturedVertexWords = texturedVertexWordCount(texturedBuilders);
            int mappingWords = mappingWordCount(texturedBuilders);
            int texturedChunkCount = texturedChunkCount(texturedBuilders);
            int texturedTriangleCount = texturedTriangleCount(texturedBuilders);

            int[] solidVertices = new int[solidWords];
            GpuSceneChunk[] solidRanges = new GpuSceneChunk[solidChunkCount];
            copySolidBuilders(solidBuilders, solidVertices, solidRanges, 0);

            int[] texturedVertices = new int[texturedVertexWords];
            int[] textureMappings = new int[mappingWords];
            GpuSceneChunk[] texturedRanges = new GpuSceneChunk[texturedChunkCount];
            copyTexturedBuilders(texturedBuilders, texturedVertices, textureMappings, texturedRanges, 0, 0);

            GpuStaticSceneMesh.WallPriorityDecoration[] painterDecorations = wallPriorityDecorations
                    .toArray(GpuStaticSceneMesh.WallPriorityDecoration[]::new);
            GpuStaticSceneMesh.InteractivePriorityObject[] painterObjects = interactivePriorityObjects
                    .toArray(GpuStaticSceneMesh.InteractivePriorityObject[]::new);
            GpuStaticSceneMesh.AlphaPriorityObject[] alphaObjects = alphaPriorityObjects
                    .toArray(GpuStaticSceneMesh.AlphaPriorityObject[]::new);
            return new GpuStaticSceneMesh(solidVertices, solidRanges, texturedVertices, texturedRanges,
                    textureMappings, painterDecorations, painterObjects, alphaObjects, instanceCount,
                    uniqueModelCount, triangleCount, texturedTriangleCount, skippedDynamicCount);
        }

        private static int wordCount(GpuVertexBuilder[] builders) {
            int words = 0;
            for (GpuVertexBuilder builder : builders) {
                if (builder != null && builder.vertexCount() > 0) {
                    words += builder.wordCount();
                }
            }
            return words;
        }

        private static int solidChunkCount(GpuVertexBuilder[] builders) {
            int count = 0;
            for (GpuVertexBuilder builder : builders) {
                if (builder != null && builder.vertexCount() > 0) {
                    count++;
                }
            }
            return count;
        }

        private static int texturedVertexWordCount(GpuModelTextureBuilder[] builders) {
            int words = 0;
            for (GpuModelTextureBuilder builder : builders) {
                if (builder != null && builder.vertexCount() > 0) {
                    words += builder.vertexWordCount();
                }
            }
            return words;
        }

        private static int mappingWordCount(GpuModelTextureBuilder[] builders) {
            int words = 0;
            for (GpuModelTextureBuilder builder : builders) {
                if (builder != null && builder.vertexCount() > 0) {
                    words += builder.mappingWordCount();
                }
            }
            return words;
        }

        private static int texturedChunkCount(GpuModelTextureBuilder[] builders) {
            int count = 0;
            for (GpuModelTextureBuilder builder : builders) {
                if (builder != null && builder.vertexCount() > 0) {
                    count++;
                }
            }
            return count;
        }

        private static int texturedTriangleCount(GpuModelTextureBuilder[] builders) {
            int count = 0;
            for (GpuModelTextureBuilder builder : builders) {
                if (builder != null && builder.vertexCount() > 0) {
                    count += builder.triangleCount();
                }
            }
            return count;
        }

        private static int copySolidBuilders(GpuVertexBuilder[] builders, int[] vertices, GpuSceneChunk[] ranges,
                int wordOffset) {
            int rangeIndex = 0;
            for (int index = 0; index < builders.length; index++) {
                GpuVertexBuilder builder = builders[index];
                if (builder == null || builder.vertexCount() == 0) {
                    continue;
                }
                int minRenderPlane = index % RENDER_PLANE_VARIANTS;
                ranges[rangeIndex++] = builder.toChunk(wordOffset / GpuVertexBuilder.WORDS_PER_VERTEX,
                        minRenderPlane);
                builder.copyTo(vertices, wordOffset);
                wordOffset += builder.wordCount();
            }
            return wordOffset;
        }

        private static int[] copyTexturedBuilders(GpuModelTextureBuilder[] builders, int[] vertices, int[] mappings,
                GpuSceneChunk[] ranges, int vertexWordOffset, int mappingWordOffset) {
            int rangeIndex = 0;
            for (int index = 0; index < builders.length; index++) {
                GpuModelTextureBuilder builder = builders[index];
                if (builder == null || builder.vertexCount() == 0) {
                    continue;
                }
                int minRenderPlane = index % RENDER_PLANE_VARIANTS;
                ranges[rangeIndex++] = builder.toChunk(vertexWordOffset / GpuVertexBuilder.WORDS_PER_VERTEX,
                        minRenderPlane);
                builder.copyVerticesTo(vertices, vertexWordOffset);
                builder.copyMappingsTo(mappings, mappingWordOffset);
                vertexWordOffset += builder.vertexWordCount();
                mappingWordOffset += builder.mappingWordCount();
            }
            return new int[] { vertexWordOffset, mappingWordOffset };
        }
    }

}
