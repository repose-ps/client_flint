package rs2.game.render;

/**
 * Lightweight counters for comparing software and GPU world-rendering backends.
 *
 * <p>
 * Measurements cover only the {@link WorldRenderer#render(WorldRenderFrame)}
 * call. Actor insertion, overlays, UI composition and AWT presentation are
 * intentionally excluded so the two 3D backends can be compared directly.
 * </p>
 */
public final class RendererMetrics {

	/** Number of measured world frames. */
	private long worldFrameCount;
	/** Duration of the most recently measured world frame. */
	private long lastWorldRenderNanos;
	/** Accumulated duration of all measured world frames. */
	private long totalWorldRenderNanos;

	/** Package-private constructor; metrics are owned by {@link GameRenderer}. */
	RendererMetrics() {
	}

	/** Records one backend render duration. */
	void recordWorldRender(long durationNanos) {
		worldFrameCount++;
		lastWorldRenderNanos = Math.max(0L, durationNanos);
		totalWorldRenderNanos += lastWorldRenderNanos;
	}

	/**
	 * Returns the number of measured world frames.
	 *
	 * @return measured frame count
	 */
	public long worldFrameCount() {
		return worldFrameCount;
	}

	/**
	 * Returns the duration of the most recent backend render.
	 *
	 * @return last render duration in nanoseconds
	 */
	public long lastWorldRenderNanos() {
		return lastWorldRenderNanos;
	}

	/**
	 * Returns the arithmetic mean backend render duration.
	 *
	 * @return average duration in nanoseconds, or zero before the first frame
	 */
	public long averageWorldRenderNanos() {
		return worldFrameCount == 0 ? 0L : totalWorldRenderNanos / worldFrameCount;
	}

	/** Clears all collected timing data. */
	public void reset() {
		worldFrameCount = 0L;
		lastWorldRenderNanos = 0L;
		totalWorldRenderNanos = 0L;
	}
}
