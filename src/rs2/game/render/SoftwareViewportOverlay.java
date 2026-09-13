package rs2.game.render;

/**
 * One rectangle from the legacy software viewport raster that must be
 * composited above a native world renderer.
 *
 * <p>This is intentionally a narrow bridge for legacy viewport UI while the
 * world is native OpenGL. It avoids copying the complete software viewport and
 * keeps the GPU backend independent from menu/widget classes.</p>
 */
public record SoftwareViewportOverlay(int[] pixels, int sourceStride, int sourceX, int sourceY, int x, int y,
        int width, int height, int transparentPixelKey) {

    /** Sentinel meaning every source pixel is opaque. */
    public static final int OPAQUE = Integer.MIN_VALUE;

    public SoftwareViewportOverlay(int[] pixels, int sourceStride, int x, int y, int width, int height) {
        this(pixels, sourceStride, x, y, x, y, width, height, OPAQUE);
    }

    public SoftwareViewportOverlay(int[] pixels, int sourceStride, int x, int y, int width, int height,
            int transparentPixelKey) {
        this(pixels, sourceStride, x, y, x, y, width, height, transparentPixelKey);
    }

    /**
     * Creates an overlay whose source rectangle and native destination are different.
     * This is used by resizable mode, where HUD pixels live in client coordinates
     * while the native OpenGL child starts at the viewport origin.
     */
    public SoftwareViewportOverlay(int[] pixels, int sourceStride, int sourceX, int sourceY, int x, int y,
            int width, int height) {
        this(pixels, sourceStride, sourceX, sourceY, x, y, width, height, OPAQUE);
    }

    public SoftwareViewportOverlay {
        if (pixels == null) {
            throw new NullPointerException("pixels");
        }
        if (sourceStride <= 0 || sourceX < 0 || sourceY < 0 || x < 0 || y < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid viewport overlay rectangle.");
        }
        long finalIndex = (long) (sourceY + height - 1) * sourceStride + sourceX + width;
        if (sourceX + width > sourceStride || finalIndex > pixels.length) {
            throw new IllegalArgumentException("Viewport overlay rectangle exceeds source raster.");
        }
    }

    /** Returns whether matching source pixels should be uploaded with zero alpha. */
    public boolean hasTransparencyKey() {
        return transparentPixelKey != OPAQUE;
    }
}
