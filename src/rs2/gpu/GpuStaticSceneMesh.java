package rs2.gpu;

import rs2.media.model.Model;

/** Immutable CPU-side mesh containing static RuneScape scene models. */
public final class GpuStaticSceneMesh {

    /** One ordinary wall decoration deferred to the legacy per-face painter. */
    public record WallPriorityDecoration(
            int objectId,
            int orientation,
            int worldX,
            int worldHeight,
            int worldY,
            int minRenderPlane,
            GpuSceneChunk bounds,
            Model model) {
    }

    /** One priority-bearing interactive object deferred to the legacy per-face painter. */
    public record InteractivePriorityObject(
            int objectId,
            int orientation,
            int worldX,
            int worldHeight,
            int worldY,
            int minRenderPlane,
            int priorityMetadataTag,
            GpuSceneChunk bounds,
            Model model) {
    }

    /** One alpha-bearing interactive object deferred to the exact legacy face painter. */
    public record AlphaPriorityObject(
            int objectId,
            int orientation,
            int worldX,
            int worldHeight,
            int worldY,
            int minRenderPlane,
            GpuSceneChunk bounds,
            Model model) {
    }

    /** Number of 32-bit words stored for each unindexed static-model vertex. */
    public static final int WORDS_PER_VERTEX = GpuVertexBuilder.WORDS_PER_VERTEX;
    /** Number of bytes stored for each unindexed static-model vertex. */
    public static final int BYTES_PER_VERTEX = GpuVertexBuilder.BYTES_PER_VERTEX;

    private final int[] vertices;
    private final GpuSceneChunk[] chunks;
    private final int[] texturedVertices;
    private final GpuSceneChunk[] texturedChunks;
    private final int[] textureMappings;
    private final WallPriorityDecoration[] wallPriorityDecorations;
    private final InteractivePriorityObject[] interactivePriorityObjects;
    private final AlphaPriorityObject[] alphaPriorityObjects;
    private final int instanceCount;
    private final int uniqueModelCount;
    private final int triangleCount;
    private final int texturedTriangleCount;
    private final int skippedDynamicCount;

    GpuStaticSceneMesh(int[] vertices, GpuSceneChunk[] chunks, int[] texturedVertices, GpuSceneChunk[] texturedChunks,
            int[] textureMappings, WallPriorityDecoration[] wallPriorityDecorations,
            InteractivePriorityObject[] interactivePriorityObjects, AlphaPriorityObject[] alphaPriorityObjects,
            int instanceCount, int uniqueModelCount, int triangleCount,
            int texturedTriangleCount, int skippedDynamicCount) {
        this.vertices = vertices;
        this.chunks = chunks;
        this.texturedVertices = texturedVertices;
        this.texturedChunks = texturedChunks;
        this.textureMappings = textureMappings;
        this.wallPriorityDecorations = wallPriorityDecorations;
        this.interactivePriorityObjects = interactivePriorityObjects;
        this.alphaPriorityObjects = alphaPriorityObjects;
        this.instanceCount = instanceCount;
        this.uniqueModelCount = uniqueModelCount;
        this.triangleCount = triangleCount;
        this.texturedTriangleCount = texturedTriangleCount;
        this.skippedDynamicCount = skippedDynamicCount;
    }

    /** Returns interleaved {@code x,height,y,packedRGBA} untextured vertex words. */
    public int[] vertices() {
        return vertices;
    }

    /** Returns interleaved {@code x,height,y,packedShade} textured-model vertex words. */
    public int[] texturedVertices() {
        return texturedVertices;
    }

    /**
     * Returns three {@code ivec4} mapping records per textured triangle. The first
     * record stores texture A + texture id, followed by texture B and texture C.
     */
    public int[] textureMappings() {
        return textureMappings;
    }

    public int instanceCount() {
        return instanceCount;
    }

    /** Number of distinct Model identities contributing to the static scene. */
    public int uniqueModelCount() {
        return uniqueModelCount;
    }

    public int triangleCount() {
        return triangleCount;
    }

    public int texturedTriangleCount() {
        return texturedTriangleCount;
    }

    public int untexturedTriangleCount() {
        return triangleCount - texturedTriangleCount;
    }

    public int skippedDynamicCount() {
        return skippedDynamicCount;
    }

    /** Returns contiguous untextured 8x8-tile/visibility-plane draw ranges. */
    public GpuSceneChunk[] chunks() {
        return chunks;
    }

    /** Returns contiguous textured 8x8-tile/visibility-plane draw ranges. */
    public GpuSceneChunk[] texturedChunks() {
        return texturedChunks;
    }

    /** Returns ordinary wall decorations rendered with legacy Model.drawFaces ordering. */
    public WallPriorityDecoration[] wallPriorityDecorations() {
        return wallPriorityDecorations;
    }

    /** Returns priority-bearing interactive objects rendered with legacy Model.drawFaces ordering. */
    public InteractivePriorityObject[] interactivePriorityObjects() {
        return interactivePriorityObjects;
    }

    /** Returns alpha-bearing objects rendered with exact legacy Model.drawFaces ordering. */
    public AlphaPriorityObject[] alphaPriorityObjects() {
        return alphaPriorityObjects;
    }

    public int vertexCount() {
        return vertices.length / WORDS_PER_VERTEX + texturedVertices.length / WORDS_PER_VERTEX;
    }

    public int untexturedVertexCount() {
        return vertices.length / WORDS_PER_VERTEX;
    }

    public int texturedVertexCount() {
        return texturedVertices.length / WORDS_PER_VERTEX;
    }

    public int byteSize() {
        return (vertices.length + texturedVertices.length + textureMappings.length) * Integer.BYTES;
    }

    public int vertexByteSize() {
        return (vertices.length + texturedVertices.length) * Integer.BYTES;
    }

    public int mappingByteSize() {
        return textureMappings.length * Integer.BYTES;
    }
}
