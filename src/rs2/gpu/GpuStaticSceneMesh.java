package rs2.gpu;

/** Immutable CPU-side mesh containing static RuneScape scene models. */
public final class GpuStaticSceneMesh {

    /** Number of 32-bit words stored for each unindexed static-model vertex. */
    public static final int WORDS_PER_VERTEX = GpuVertexBuilder.WORDS_PER_VERTEX;
    /** Number of bytes stored for each unindexed static-model vertex. */
    public static final int BYTES_PER_VERTEX = GpuVertexBuilder.BYTES_PER_VERTEX;

    private final int[] vertices;
    private final int instanceCount;
    private final int uniqueModelCount;
    private final int triangleCount;
    private final int skippedDynamicCount;
    private final GpuSceneChunk[] chunks;

    GpuStaticSceneMesh(int[] vertices, int instanceCount, int uniqueModelCount, int triangleCount,
            int skippedDynamicCount, GpuSceneChunk[] chunks) {
        this.vertices = vertices;
        this.instanceCount = instanceCount;
        this.uniqueModelCount = uniqueModelCount;
        this.triangleCount = triangleCount;
        this.skippedDynamicCount = skippedDynamicCount;
        this.chunks = chunks;
    }

    /** Returns interleaved {@code x,height,y,packedRGBA} world-space vertex words. */
    public int[] vertices() {
        return vertices;
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

    public int skippedDynamicCount() {
        return skippedDynamicCount;
    }

    /** Returns contiguous 8x8-tile/visibility-plane draw ranges with exact bounds. */
    public GpuSceneChunk[] chunks() {
        return chunks;
    }

    public int vertexCount() {
        return vertices.length / WORDS_PER_VERTEX;
    }

    public int byteSize() {
        return vertices.length * Integer.BYTES;
    }
}
