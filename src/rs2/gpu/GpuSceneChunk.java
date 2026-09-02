package rs2.gpu;

/**
 * One contiguous draw range and its world-space bounds inside a GPU scene VBO.
 *
 * <p>Geometry is grouped by 8x8 scene chunk and minimum render plane. This lets
 * the renderer keep one render-plane-independent scene buffer while rejecting
 * both roof-hidden and off-screen ranges before issuing the multi-draw call.</p>
 */
public record GpuSceneChunk(int firstVertex, int vertexCount, int minX, int minHeight, int minY, int maxX,
        int maxHeight, int maxY, int minRenderPlane) {

    /** Compatibility constructor for tests/ranges visible on every render plane. */
    public GpuSceneChunk(int firstVertex, int vertexCount, int minX, int minHeight, int minY, int maxX,
            int maxHeight, int maxY) {
        this(firstVertex, vertexCount, minX, minHeight, minY, maxX, maxHeight, maxY, 0);
    }

    /** Returns the number of unindexed triangles in this draw range. */
    public int triangleCount() {
        return vertexCount / 3;
    }

    /** Returns whether this range participates in the supplied RuneScape render plane. */
    public boolean visibleOnPlane(int renderPlane) {
        return minRenderPlane <= renderPlane;
    }
}
