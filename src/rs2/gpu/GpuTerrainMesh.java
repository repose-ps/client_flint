package rs2.gpu;

/** Immutable CPU-side terrain mesh ready for upload to an OpenGL vertex buffer. */
public final class GpuTerrainMesh {

    /** Number of 32-bit words stored for each unindexed terrain vertex. */
    public static final int WORDS_PER_VERTEX = GpuVertexBuilder.WORDS_PER_VERTEX;
    /** Number of bytes stored for each unindexed terrain vertex. */
    public static final int BYTES_PER_VERTEX = GpuVertexBuilder.BYTES_PER_VERTEX;

    private final int[] vertices;
    private final int surfaceCount;
    private final int triangleCount;
    private final GpuSceneChunk[] chunks;

    GpuTerrainMesh(int[] vertices, int surfaceCount, int triangleCount, GpuSceneChunk[] chunks) {
        this.vertices = vertices;
        this.surfaceCount = surfaceCount;
        this.triangleCount = triangleCount;
        this.chunks = chunks;
    }

    /** Returns interleaved {@code x,height,y,packedRGBA} terrain vertex words. */
    public int[] vertices() {
        return vertices;
    }

    public int surfaceCount() {
        return surfaceCount;
    }

    public int triangleCount() {
        return triangleCount;
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
