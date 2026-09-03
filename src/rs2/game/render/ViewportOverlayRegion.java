package rs2.game.render;

/**
 * Viewport-local rectangle containing legacy software UI that must be
 * composited above a native world surface.
 *
 * <p>Most overlays are opaque. A keyed overlay uses one impossible legacy
 * raster pixel value as transparent so text-only overlays can retain the
 * native world underneath them.</p>
 */
public record ViewportOverlayRegion(int x, int y, int width, int height, int transparentPixelKey) {

    /** Sentinel meaning every source pixel is opaque. */
    public static final int OPAQUE = Integer.MIN_VALUE;

    public ViewportOverlayRegion(int x, int y, int width, int height) {
        this(x, y, width, height, OPAQUE);
    }

    public ViewportOverlayRegion {
        if (x < 0 || y < 0 || width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid viewport overlay region.");
        }
    }

    /** Returns whether the overlay contains keyed transparent pixels. */
    public boolean hasTransparencyKey() {
        return transparentPixelKey != OPAQUE;
    }
}
