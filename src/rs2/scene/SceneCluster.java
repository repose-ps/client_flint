package rs2.scene;

/**
 * Axis-aligned occlusion volume used by the software scene renderer.
 *
 * <p>The map loader supplies the tile-space and world-space bounds. Before
 * drawing each frame, the scene renderer selects visible clusters and derives
 * camera-relative projection gradients. Those gradients allow a point to be
 * tested against the occluded volume using integer arithmetic.</p>
 */
public class SceneCluster {

    /** Minimum tile-space X coordinate covered by this cluster. */
    public int minTileX;

    /** Maximum tile-space X coordinate covered by this cluster. */
    public int maxTileX;

    /** Minimum tile-space Y coordinate covered by this cluster. */
    public int minTileY;

    /** Maximum tile-space Y coordinate covered by this cluster. */
    public int maxTileY;

    /**
     * Occluder orientation: {@code 1} for an X plane, {@code 2} for a Y
     * plane, or {@code 4} for a horizontal plane.
     */
    public int type;

    /** Minimum world-space X coordinate covered by this cluster. */
    public int minWorldX;

    /** Maximum world-space X coordinate covered by this cluster. */
    public int maxWorldX;

    /** Minimum world-space Y coordinate covered by this cluster. */
    public int minWorldY;

    /** Maximum world-space Y coordinate covered by this cluster. */
    public int maxWorldY;

    /**
     * Minimum numeric world-space Z coordinate.
     *
     * <p>This is visually the upper bound because RuneScape's scene Z axis
     * decreases as elevation increases.</p>
     */
    public int minWorldZ;

    /** Maximum numeric world-space Z coordinate; visually the lower bound. */
    public int maxWorldZ;

    /**
     * Camera-relative projection direction selected for the current frame.
     * Values {@code 1-4} represent the two sides of the X and Y planes;
     * {@code 5} represents a horizontal plane viewed from above.
     */
    public int projectionDirection;

    /** 8.8 fixed-point gradient from the camera to {@link #minWorldX}. */
    public int minXGradient;

    /** 8.8 fixed-point gradient from the camera to {@link #maxWorldX}. */
    public int maxXGradient;

    /** 8.8 fixed-point gradient from the camera to {@link #minWorldY}. */
    public int minYGradient;

    /** 8.8 fixed-point gradient from the camera to {@link #maxWorldY}. */
    public int maxYGradient;

    /** 8.8 fixed-point gradient from the camera to {@link #minWorldZ}. */
    public int minZGradient;

    /** 8.8 fixed-point gradient from the camera to {@link #maxWorldZ}. */
    public int maxZGradient;
}