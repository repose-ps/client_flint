package rs2.gpu;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;

import rs2.media.Angle;
import rs2.media.Rasterizer3D;
import rs2.media.model.Model;
import rs2.media.model.Renderable;
import rs2.scene.Scene;
import rs2.scene.tile.ComplexTile;
import rs2.scene.tile.FloorDecoration;
import rs2.scene.tile.GenericTile;
import rs2.scene.tile.InteractiveObject;
import rs2.scene.tile.SceneTile;
import rs2.scene.tile.Wall;
import rs2.scene.tile.WallDecoration;

/** Converts loaded revision-377 terrain and static scene models into GPU-friendly triangles. */
public final class GpuSceneUploader {

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
        GpuVertexBuilder[] chunkVertices = new GpuVertexBuilder[chunkColumns * chunkRows * RENDER_PLANE_VARIANTS];
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

                    GpuVertexBuilder vertices = terrainChunk(chunkVertices, chunkRows, x, y, minRenderPlane);
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
                                UploadTally tally = appendRenderable(object.renderable, object.rotation, object.worldX,
                                        object.worldZ, object.worldY, objectPlane, chunks, uniqueModels);
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
                        UploadTally tally = appendRenderable(floorDecoration.renderable, 0, floorDecoration.x,
                                floorDecoration.z, floorDecoration.y, tilePlane, chunks, uniqueModels);
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
                        UploadTally tally = appendRenderable(object.renderable, object.rotation, object.worldX,
                                object.worldZ, object.worldY, objectPlane, chunks, uniqueModels);
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

    private static UploadTally appendWallDecoration(WallDecoration decoration, int minRenderPlane,
            StaticChunkSet chunks, Map<Model, Boolean> uniqueModels) {
        if ((decoration.configBits & 0x300) == 0) {
            return appendRenderable(decoration.renderable, decoration.face, decoration.x, decoration.z, decoration.y,
                    minRenderPlane, chunks, uniqueModels);
        }

        int face = decoration.face & 3;
        UploadTally total = UploadTally.EMPTY;
        if ((decoration.configBits & 0x100) != 0) {
            int orientation = face * Angle.QUARTER_TURN + Angle.EIGHTH_TURN & Angle.MASK;
            total = total.plus(appendRenderable(decoration.renderable, orientation,
                    decoration.x + Scene.WALL_DECORATION_INSET_X[face], decoration.z,
                    decoration.y + Scene.WALL_DECORATION_INSET_Y[face], minRenderPlane, chunks, uniqueModels));
        }
        if ((decoration.configBits & 0x200) != 0) {
            int orientation = face * Angle.QUARTER_TURN + Angle.FIVE_EIGHTHS_TURN & Angle.MASK;
            total = total.plus(appendRenderable(decoration.renderable, orientation,
                    decoration.x + Scene.WALL_DECORATION_OUTSET_X[face], decoration.z,
                    decoration.y + Scene.WALL_DECORATION_OUTSET_Y[face], minRenderPlane, chunks, uniqueModels));
        }
        return total;
    }

    private static UploadTally appendRenderable(Renderable renderable, int orientation, int worldX, int worldHeight,
            int worldY, int minRenderPlane, StaticChunkSet chunks, Map<Model, Boolean> uniqueModels) {
        if (renderable == null) {
            return UploadTally.EMPTY;
        }
        if (!(renderable instanceof Model model)) {
            return new UploadTally(0, 0, 1);
        }
        GpuVertexBuilder vertices = chunks.builderForWorld(worldX, worldY, minRenderPlane);
        GpuModelUploader.UploadResult result = GpuModelUploader.append(model, orientation, worldX, worldHeight, worldY,
                vertices);
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

    private static GpuVertexBuilder terrainChunk(GpuVertexBuilder[] chunks, int chunkRows, int tileX, int tileY,
            int minRenderPlane) {
        int chunkIndex = (tileX / CHUNK_SIZE) * chunkRows + tileY / CHUNK_SIZE;
        int index = chunkIndex * RENDER_PLANE_VARIANTS + minRenderPlane;
        GpuVertexBuilder vertices = chunks[index];
        if (vertices == null) {
            vertices = new GpuVertexBuilder(384);
            chunks[index] = vertices;
        }
        return vertices;
    }

    private static GpuTerrainMesh finishTerrain(GpuVertexBuilder[] chunkVertices, int surfaceCount,
            int triangleCount) {
        int totalWords = 0;
        int nonEmptyChunks = 0;
        for (GpuVertexBuilder chunk : chunkVertices) {
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
            GpuVertexBuilder chunk = chunkVertices[index];
            if (chunk == null || chunk.vertexCount() == 0) {
                continue;
            }
            int minRenderPlane = index % RENDER_PLANE_VARIANTS;
            ranges[rangeIndex++] = chunk.toChunk(wordOffset / GpuVertexBuilder.WORDS_PER_VERTEX, minRenderPlane);
            chunk.copyTo(vertices, wordOffset);
            wordOffset += chunk.wordCount();
        }
        return new GpuTerrainMesh(vertices, surfaceCount, triangleCount, ranges);
    }

    private static int appendSurface(Scene scene, SceneTile tile, int heightPlane, int tileX, int tileY,
            GpuVertexBuilder vertices) {
        if (tile.plainTile != null) {
            return appendPlainTile(scene, tile.plainTile, heightPlane, tileX, tileY, vertices);
        }
        if (tile.shapedTile != null) {
            return appendShapedTile(tile.shapedTile, vertices);
        }
        return 0;
    }

    private static int appendPlainTile(Scene scene, GenericTile tile, int plane, int tileX, int tileY,
            GpuVertexBuilder vertices) {
        if (plane < 0 || plane >= scene.tileHeights.length) {
            return 0;
        }

        int worldX = tileX * TILE_SIZE;
        int worldY = tileY * TILE_SIZE;
        int southWestHeight = scene.tileHeights[plane][tileX][tileY];
        int southEastHeight = scene.tileHeights[plane][tileX + 1][tileY];
        int northEastHeight = scene.tileHeights[plane][tileX + 1][tileY + 1];
        int northWestHeight = scene.tileHeights[plane][tileX][tileY + 1];

        int colourA = tile.colourA;
        int colourB = tile.colourB;
        int colourC = tile.colourC;
        int colourD = tile.colourD;
        if (tile.texture >= 0 && tile.texture < Scene.TEXTURE_COLORS.length) {
            int textureColour = Scene.TEXTURE_COLORS[tile.texture];
            colourA = mixTextureColour(colourA, textureColour);
            colourB = mixTextureColour(colourB, textureColour);
            colourC = mixTextureColour(colourC, textureColour);
            colourD = mixTextureColour(colourD, textureColour);
        }

        int triangles = 0;
        if (tile.texture >= 0 || tile.colourC != INVISIBLE_HSL) {
            appendVertex(vertices, worldX + TILE_SIZE, northEastHeight, worldY + TILE_SIZE, colourC);
            appendVertex(vertices, worldX, northWestHeight, worldY + TILE_SIZE, colourD);
            appendVertex(vertices, worldX + TILE_SIZE, southEastHeight, worldY, colourB);
            triangles++;
        }
        if (tile.texture >= 0 || tile.colourA != INVISIBLE_HSL) {
            appendVertex(vertices, worldX, southWestHeight, worldY, colourA);
            appendVertex(vertices, worldX + TILE_SIZE, southEastHeight, worldY, colourB);
            appendVertex(vertices, worldX, northWestHeight, worldY + TILE_SIZE, colourD);
            triangles++;
        }
        return triangles;
    }

    private static int appendShapedTile(ComplexTile tile, GpuVertexBuilder vertices) {
        int triangles = 0;
        for (int triangle = 0; triangle < tile.triangleVertexA.length; triangle++) {
            int colourA = tile.triangleHslA[triangle];
            int colourB = tile.triangleHslB[triangle];
            int colourC = tile.triangleHslC[triangle];
            int texture = tile.triangleTextures == null ? -1 : tile.triangleTextures[triangle];
            if (texture < 0 && colourA == INVISIBLE_HSL) {
                continue;
            }
            if (texture >= 0 && texture < Scene.TEXTURE_COLORS.length) {
                int textureColour = Scene.TEXTURE_COLORS[texture];
                colourA = mixTextureColour(colourA, textureColour);
                colourB = mixTextureColour(colourB, textureColour);
                colourC = mixTextureColour(colourC, textureColour);
            }

            appendIndexedVertex(vertices, tile, tile.triangleVertexA[triangle], colourA);
            appendIndexedVertex(vertices, tile, tile.triangleVertexB[triangle], colourB);
            appendIndexedVertex(vertices, tile, tile.triangleVertexC[triangle], colourC);
            triangles++;
        }
        return triangles;
    }

    private static void appendIndexedVertex(GpuVertexBuilder vertices, ComplexTile tile, int index, int hsl) {
        appendVertex(vertices, tile.vertexX[index], tile.vertexY[index], tile.vertexZ[index], hsl);
    }

    private static void appendVertex(GpuVertexBuilder vertices, int x, int height, int y, int hsl) {
        vertices.add(x, height, y, hslToRgb(hsl));
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

    private static int mixTextureColour(int lightness, int baseColour) {
        lightness = 127 - lightness;
        lightness = lightness * (baseColour & 0x7f) / 160;
        if (lightness < 2) {
            lightness = 2;
        } else if (lightness > 126) {
            lightness = 126;
        }
        return (baseColour & 0xff80) + lightness;
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

    /** Per-chunk/per-plane static-model accumulators concatenated into one VBO. */
    private static final class StaticChunkSet {
        private final int chunkColumns;
        private final int chunkRows;
        private final GpuVertexBuilder[] builders;

        StaticChunkSet(int sceneWidth, int sceneHeight) {
            chunkColumns = chunkCount(sceneWidth);
            chunkRows = chunkCount(sceneHeight);
            builders = new GpuVertexBuilder[chunkColumns * chunkRows * RENDER_PLANE_VARIANTS];
        }

        GpuVertexBuilder builderForWorld(int worldX, int worldY, int minRenderPlane) {
            int chunkX = Math.max(0, Math.min(chunkColumns - 1, Math.floorDiv(worldX, CHUNK_WORLD_SIZE)));
            int chunkY = Math.max(0, Math.min(chunkRows - 1, Math.floorDiv(worldY, CHUNK_WORLD_SIZE)));
            int chunkIndex = chunkX * chunkRows + chunkY;
            int index = chunkIndex * RENDER_PLANE_VARIANTS + minRenderPlane;
            GpuVertexBuilder builder = builders[index];
            if (builder == null) {
                builder = new GpuVertexBuilder(256);
                builders[index] = builder;
            }
            return builder;
        }

        GpuStaticSceneMesh finish(int instanceCount, int uniqueModelCount, int triangleCount, int skippedDynamicCount) {
            int totalWords = 0;
            int nonEmptyChunks = 0;
            for (GpuVertexBuilder chunk : builders) {
                if (chunk != null && chunk.vertexCount() > 0) {
                    totalWords += chunk.wordCount();
                    nonEmptyChunks++;
                }
            }

            int[] vertices = new int[totalWords];
            List<GpuSceneChunk> ranges = new ArrayList<>(nonEmptyChunks);
            int wordOffset = 0;
            for (int index = 0; index < builders.length; index++) {
                GpuVertexBuilder chunk = builders[index];
                if (chunk == null || chunk.vertexCount() == 0) {
                    continue;
                }
                int minRenderPlane = index % RENDER_PLANE_VARIANTS;
                ranges.add(chunk.toChunk(wordOffset / GpuVertexBuilder.WORDS_PER_VERTEX, minRenderPlane));
                chunk.copyTo(vertices, wordOffset);
                wordOffset += chunk.wordCount();
            }
            return new GpuStaticSceneMesh(vertices, instanceCount, uniqueModelCount, triangleCount, skippedDynamicCount,
                    ranges.toArray(GpuSceneChunk[]::new));
        }
    }
}
