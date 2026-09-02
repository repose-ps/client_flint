package rs2.gpu;

import rs2.game.render.WorldRenderFrame;
import rs2.media.Rasterizer3D;

/** Conservative CPU-side frustum tests for uploaded GPU scene chunks. */
public final class GpuSceneVisibility {

    private static final int NEAR_CLIP = 50;
    private static final int FAR_CLIP = 25_000;
    private static final int PROJECTION_SCALE_TIMES_TWO = 1024;
    private static final int PIXEL_MARGIN = 32;

    private GpuSceneVisibility() {
    }

    /**
     * Returns whether any part of the chunk's world-space AABB can intersect the
     * current RuneScape camera frustum.
     *
     * <p>The test uses the same 16.16 yaw/pitch arithmetic and projection scale
     * as the GLSL camera path. A chunk is rejected only when all eight AABB
     * corners lie outside the same clip plane, which keeps this deliberately
     * conservative.</p>
     */
    public static boolean isVisible(GpuSceneChunk chunk, WorldRenderFrame frame, int viewportWidth,
            int viewportHeight) {
        int cameraX = Math.max(0, Math.min(frame.cameraX(), frame.scene().width * 128 - 1));
        int cameraY = Math.max(0, Math.min(frame.cameraY(), frame.scene().height * 128 - 1));
        int yaw = frame.yaw() & 0x7ff;
        int pitch = frame.pitch() & 0x7ff;
        int yawSin = Rasterizer3D.SINE[yaw];
        int yawCos = Rasterizer3D.COSINE[yaw];
        int pitchSin = Rasterizer3D.SINE[pitch];
        int pitchCos = Rasterizer3D.COSINE[pitch];

        return isVisible(chunk, cameraX, cameraY, frame.cameraHeight(), yawSin, yawCos, pitchSin, pitchCos,
                viewportWidth, viewportHeight);
    }

    static boolean isVisible(GpuSceneChunk chunk, int cameraX, int cameraY, int cameraHeight, int yawSin, int yawCos,
            int pitchSin, int pitchCos, int viewportWidth, int viewportHeight) {
        int expandedWidth = Math.max(1, viewportWidth) + PIXEL_MARGIN * 2;
        int expandedHeight = Math.max(1, viewportHeight) + PIXEL_MARGIN * 2;

        int outsideNear = 0;
        int outsideFar = 0;
        int outsideLeft = 0;
        int outsideRight = 0;
        int outsideTop = 0;
        int outsideBottom = 0;

        int minX = chunk.minX();
        int maxX = chunk.maxX();
        int minHeight = chunk.minHeight();
        int maxHeight = chunk.maxHeight();
        int minY = chunk.minY();
        int maxY = chunk.maxY();

        for (int xIndex = 0; xIndex < 2; xIndex++) {
            int worldX = xIndex == 0 ? minX : maxX;
            for (int heightIndex = 0; heightIndex < 2; heightIndex++) {
                int worldHeight = heightIndex == 0 ? minHeight : maxHeight;
                for (int yIndex = 0; yIndex < 2; yIndex++) {
                    int worldY = yIndex == 0 ? minY : maxY;

                    long relativeX = (long) worldX - cameraX;
                    long relativeHeight = (long) worldHeight - cameraHeight;
                    long relativeY = (long) worldY - cameraY;

                    long viewX = (relativeY * yawSin + relativeX * yawCos) >> 16;
                    long yawDepth = (relativeY * yawCos - relativeX * yawSin) >> 16;
                    long viewY = (relativeHeight * pitchCos - yawDepth * pitchSin) >> 16;
                    long depth = (relativeHeight * pitchSin + yawDepth * pitchCos) >> 16;

                    if (depth < NEAR_CLIP) {
                        outsideNear++;
                    }
                    if (depth > FAR_CLIP) {
                        outsideFar++;
                    }

                    long horizontalExtent = depth * expandedWidth;
                    long verticalExtent = depth * expandedHeight;
                    long projectedX = viewX * PROJECTION_SCALE_TIMES_TWO;
                    long projectedY = viewY * PROJECTION_SCALE_TIMES_TWO;
                    if (projectedX + horizontalExtent < 0) {
                        outsideLeft++;
                    }
                    if (-projectedX + horizontalExtent < 0) {
                        outsideRight++;
                    }
                    if (projectedY + verticalExtent < 0) {
                        outsideTop++;
                    }
                    if (-projectedY + verticalExtent < 0) {
                        outsideBottom++;
                    }
                }
            }
        }

        final int cornerCount = 8;
        return outsideNear != cornerCount && outsideFar != cornerCount && outsideLeft != cornerCount
                && outsideRight != cornerCount && outsideTop != cornerCount && outsideBottom != cornerCount;
    }
}
