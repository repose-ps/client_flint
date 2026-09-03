package rs2.ui.login;

import java.awt.Graphics;
import java.util.concurrent.locks.LockSupport;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import rs2.cache.Archive;
import rs2.media.GraphicsBuffer;
import rs2.media.sprite.ImageRGB;
import rs2.media.sprite.IndexedImage;

/**
 * Owns the classic title-screen flame simulation, palette state, and dedicated
 * animation thread.
 *
 * <p>
 * The animator preserves the revision-377 128-by-256 intensity simulation and
 * composites it onto the two fixed 128-by-265 title buffers. The game cycle is
 * supplied by the client so the original sine-wave line offsets remain
 * synchronized with the main client loop without coupling this class back to
 * {@code Client}.
 * </p>
 */
public final class TitleFlameAnimator {

	/** Width of the flame simulation in pixels. */
	private static final int FLAME_WIDTH = 128;
	/** Height of the flame simulation in pixels. */
	private static final int FLAME_HEIGHT = 256;
	/** Height of each title-side backing buffer in pixels. */
	private static final int BACKGROUND_HEIGHT = 265;
	/** Number of pixels in each title-side backing buffer. */
	private static final int BACKGROUND_PIXEL_COUNT = FLAME_WIDTH * BACKGROUND_HEIGHT;
	/** Number of entries in each flame palette. */
	private static final int PALETTE_SIZE = 256;
	/** Number of title rune masks in the media archive. */
	private static final int RUNE_COUNT = 12;
	/** Total number of cells in the 128-by-256 flame simulation. */
	private static final int SIMULATION_SIZE = FLAME_WIDTH * FLAME_HEIGHT;
	/** Flame simulation cadence. The original worker advanced two steps about every 40 ms. */
	private static final long UPDATE_STEP_NANOS = 20_000_000L;
	/** Prevents a stalled title thread from performing an unbounded catch-up burst. */
	private static final int MAX_CATCH_UP_STEPS = 4;

	/** Per-line horizontal distortion offsets. */
	private final int[] lineOffsets = new int[FLAME_HEIGHT];
	/** Current random noise field. */
	private int[] noise;
	/** Scratch random noise field. */
	private int[] noiseScratch;
	/** Current flame intensity field. */
	private int[] intensity;
	/** Scratch flame intensity field. */
	private int[] intensityScratch;
	/** Palette selected for the current frame. */
	private int[] palette;
	/** Base red/yellow/white flame palette. */
	private int[] redPalette;
	/** Alternate green flame palette. */
	private int[] greenPalette;
	/** Alternate blue flame palette. */
	private int[] bluePalette;
	/** Rune masks used to periodically reseed the noise field. */
	private IndexedImage[] runes;
	/** Saved left-side title background beneath the flame. */
	private ImageRGB leftBackground;
	/** Saved right-side title background beneath the flame. */
	private ImageRGB rightBackground;
	/** Left AWT-backed title buffer onto which the flame is composited. */
	private GraphicsBuffer leftBuffer;
	/** Right AWT-backed title buffer onto which the flame is composited. */
	private GraphicsBuffer rightBuffer;
	/** Supplies the main client cycle used by the original line-wave formula. */
	private IntSupplier gameCycleSupplier;
	/** Protects publication of complete left/right flame frame pairs. */
	private final Object frameLock = new Object();
	/** Serializes AWT presentation so startup and main-loop draws cannot overlap. */
	private final Object presentationLock = new Object();
	/** Supplies the stable startup AWT graphics context while bootstrap is synchronous. */
	private Supplier<Graphics> startupGraphicsSupplier;
	/** Whether the worker may present directly before the normal render loop starts. */
	private volatile boolean startupPresentationEnabled;
	/** Previous completed left flame frame used for render interpolation. */
	private int[] previousLeftFrame;
	/** Previous completed right flame frame used for render interpolation. */
	private int[] previousRightFrame;
	/** Most recently completed left flame frame. */
	private int[] frontLeftFrame;
	/** Most recently completed right flame frame. */
	private int[] frontRightFrame;
	/** Worker-owned left composition scratch frame. */
	private int[] backLeftFrame;
	/** Worker-owned right composition scratch frame. */
	private int[] backRightFrame;
	/** Monotonic publication time of the current completed flame frame. */
	private long frontFramePublishedNanos;
	/** Rolling offset into {@link #noise}. */
	private int noiseOffset;
	/** Number of completed title-flame frames. */
	private volatile int cycle;
	/** Remaining green-palette transition amount. */
	private int greenTransition;
	/** Remaining blue-palette transition amount. */
	private int blueTransition;
	/** Whether the animation worker should continue running. */
	private volatile boolean running;
	/** Dedicated title-flame worker thread. */
	private volatile Thread thread;

	/** Creates an inactive title-flame animator. */
	public TitleFlameAnimator() {
	}

	/**
	 * Loads rune masks and allocates the title-flame simulation state.
	 *
	 * @param titleArchive title-screen media archive
	 * @param leftBuffer   left title-side graphics buffer
	 * @param rightBuffer  right title-side graphics buffer
	 */
	public void prepare(Archive titleArchive, GraphicsBuffer leftBuffer, GraphicsBuffer rightBuffer) {
		this.leftBuffer = leftBuffer;
		this.rightBuffer = rightBuffer;
		runes = new IndexedImage[RUNE_COUNT];
		for (int assetIndex = 0; assetIndex < RUNE_COUNT; assetIndex++) {
			runes[assetIndex] = new IndexedImage(titleArchive, "runes", assetIndex);
		}
		leftBackground = new ImageRGB(FLAME_WIDTH, BACKGROUND_HEIGHT);
		rightBackground = new ImageRGB(FLAME_WIDTH, BACKGROUND_HEIGHT);
		for (int pixelIndex = 0; pixelIndex < BACKGROUND_PIXEL_COUNT; pixelIndex++) {
			leftBackground.pixels[pixelIndex] = leftBuffer.pixels[pixelIndex];
			rightBackground.pixels[pixelIndex] = rightBuffer.pixels[pixelIndex];
		}
		initializePalettes();
		palette = new int[PALETTE_SIZE];
		noise = new int[SIMULATION_SIZE];
		noiseScratch = new int[SIMULATION_SIZE];
		initializeNoise(null);
		intensity = new int[SIMULATION_SIZE];
		intensityScratch = new int[SIMULATION_SIZE];
		previousLeftFrame = leftBackground.pixels.clone();
		previousRightFrame = rightBackground.pixels.clone();
		frontLeftFrame = leftBackground.pixels.clone();
		frontRightFrame = rightBackground.pixels.clone();
		backLeftFrame = new int[BACKGROUND_PIXEL_COUNT];
		backRightFrame = new int[BACKGROUND_PIXEL_COUNT];
		frontFramePublishedNanos = System.nanoTime();
	}

	/**
	 * Starts the dedicated flame worker if it is not already running.
	 * 
	 * @param gameCycleSupplier supplies the main client cycle
	 * @param startupGraphicsSupplier optional startup-only AWT target; {@code null}
	 *                                once the normal render loop is available
	 */
	public void start(IntSupplier gameCycleSupplier, Supplier<Graphics> startupGraphicsSupplier) {
		this.gameCycleSupplier = gameCycleSupplier;
		synchronized (presentationLock) {
			this.startupGraphicsSupplier = startupGraphicsSupplier;
			startupPresentationEnabled = startupGraphicsSupplier != null;
		}
		if (running) {
			return;
		}
		running = true;
		Thread worker = new Thread(this::runLoop, "rs2-title-flame");
		worker.setDaemon(true);
		thread = worker;
		worker.start();
		worker.setPriority(2);
	}

	/**
	 * Ends direct worker presentation before the independently paced main render
	 * loop starts. Waiting on the presentation lock guarantees that any in-flight
	 * startup draw has completed before this method returns.
	 */
	public void finishStartupPresentation() {
		synchronized (presentationLock) {
			startupPresentationEnabled = false;
			startupGraphicsSupplier = null;
		}
	}

	/** Requests asynchronous worker termination without waiting for the thread. */
	public void requestStop() {
		running = false;
		Thread worker = thread;
		if (worker != null) {
			LockSupport.unpark(worker);
		}
	}

	/**
	 * Stops the worker, waits for it to terminate, and releases title-only
	 * resources.
	 */
	public void dispose() {
		running = false;
		Thread worker = thread;
		if (worker != null && worker != Thread.currentThread()) {
			worker.interrupt();
			boolean interrupted = false;
			for (;;) {
				try {
					worker.join();
					break;
				} catch (InterruptedException exception) {
					interrupted = true;
				}
			}
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
		if (thread == worker) {
			thread = null;
		}
		finishStartupPresentation();
		runes = null;
		palette = null;
		redPalette = null;
		greenPalette = null;
		bluePalette = null;
		noise = null;
		noiseScratch = null;
		intensity = null;
		intensityScratch = null;
		leftBackground = null;
		rightBackground = null;
		leftBuffer = null;
		rightBuffer = null;
		gameCycleSupplier = null;
		synchronized (frameLock) {
			previousLeftFrame = null;
			previousRightFrame = null;
			frontLeftFrame = null;
			frontRightFrame = null;
			backLeftFrame = null;
			backRightFrame = null;
			frontFramePublishedNanos = 0L;
		}
	}

	/**
	 * Returns whether the worker is requested to run.
	 *
	 * @return running state
	 */
	public boolean isRunning() {
		return running;
	}

	/**
	 * Returns the number of completed flame frames.
	 *
	 * @return completed frame count
	 */
	public int cycle() {
		return cycle;
	}

	/**
	 * Runs the flame simulation at its effective original 50 Hz update rate. The
	 * historical worker advanced two simulation steps before each roughly-40 ms
	 * presentation. Keeping one step every 20 ms preserves simulation speed while
	 * publishing a coherent frame at 50 Hz for the independently paced renderer.
	 */
	private void runLoop() {
		Thread current = Thread.currentThread();
		try {
			long nextUpdate = System.nanoTime();
			while (running) {
				long now = System.nanoTime();
				int updates = 0;
				while (running && now >= nextUpdate && updates < MAX_CATCH_UP_STEPS) {
					update();
					composeFrame();
					cycle++;
					presentStartupFrame();
					nextUpdate += UPDATE_STEP_NANOS;
					updates++;
				}
				if (updates == MAX_CATCH_UP_STEPS && now >= nextUpdate) {
					nextUpdate = now + UPDATE_STEP_NANOS;
				}
				long waitNanos = nextUpdate - System.nanoTime();
				if (running && waitNanos > 0L) {
					LockSupport.parkNanos(waitNanos);
				}
			}
		} catch (Exception ignored) {
			// Preserve the original title worker's fail-silent behavior.
		}
		if (thread == current) {
			thread = null;
		}
	}

	/** Initializes the three historical title-flame palettes. */
	private void initializePalettes() {
		redPalette = new int[PALETTE_SIZE];
		for (int i = 0; i < 64; i++)
			redPalette[i] = i * 0x40000;
		for (int i = 0; i < 64; i++)
			redPalette[i + 64] = 0xff0000 + 1024 * i;
		for (int i = 0; i < 64; i++)
			redPalette[i + 128] = 0xffff00 + 4 * i;
		for (int i = 0; i < 64; i++)
			redPalette[i + 192] = 0xffffff;
		greenPalette = new int[PALETTE_SIZE];
		for (int i = 0; i < 64; i++)
			greenPalette[i] = i * 1024;
		for (int i = 0; i < 64; i++)
			greenPalette[i + 64] = 65280 + 4 * i;
		for (int i = 0; i < 64; i++)
			greenPalette[i + 128] = 65535 + 0x40000 * i;
		for (int i = 0; i < 64; i++)
			greenPalette[i + 192] = 0xffffff;
		bluePalette = new int[PALETTE_SIZE];
		for (int i = 0; i < 64; i++)
			bluePalette[i] = i * 4;
		for (int i = 0; i < 64; i++)
			bluePalette[i + 64] = 255 + 0x40000 * i;
		for (int i = 0; i < 64; i++)
			bluePalette[i + 128] = 0xff00ff + 1024 * i;
		for (int i = 0; i < 64; i++)
			bluePalette[i + 192] = 0xffffff;
	}

	/** Advances one original title-flame simulation step. */
	private void update() {
		for (int sparkX = 10; sparkX < 117; sparkX++) {
			if ((int) (Math.random() * 100D) < 50) {
				intensity[sparkX + (FLAME_HEIGHT - 2 << 7)] = 255;
			}
		}
		for (int sparkIndex = 0; sparkIndex < 100; sparkIndex++) {
			int sparkX = (int) (Math.random() * 124D) + 2;
			int sparkY = (int) (Math.random() * 128D) + 128;
			intensity[sparkX + (sparkY << 7)] = 192;
		}
		for (int blurY = 1; blurY < FLAME_HEIGHT - 1; blurY++) {
			for (int blurX = 1; blurX < 127; blurX++) {
				int offset = blurX + (blurY << 7);
				intensityScratch[offset] = (intensity[offset - 1] + intensity[offset + 1] + intensity[offset - 128]
						+ intensity[offset + 128]) / 4;
			}
		}
		noiseOffset += 128;
		if (noiseOffset > noise.length) {
			noiseOffset -= noise.length;
			initializeNoise(runes[(int) (Math.random() * RUNE_COUNT)]);
		}
		for (int y = 1; y < FLAME_HEIGHT - 1; y++) {
			for (int x = 1; x < 127; x++) {
				int offset = x + (y << 7);
				int value = intensityScratch[offset + 128] - noise[offset + noiseOffset & noise.length - 1] / 5;
				if (value < 0)
					value = 0;
				intensity[offset] = value;
			}
		}
		for (int i = 0; i < FLAME_HEIGHT - 1; i++)
			lineOffsets[i] = lineOffsets[i + 1];
		int gameCycle = gameCycleSupplier.getAsInt();
		lineOffsets[FLAME_HEIGHT - 1] = (int) (Math.sin((double) gameCycle / 14D) * 16D
				+ Math.sin((double) gameCycle / 15D) * 14D + Math.sin((double) gameCycle / 16D) * 12D);
		if (greenTransition > 0)
			greenTransition -= 4;
		if (blueTransition > 0)
			blueTransition -= 4;
		if (greenTransition == 0 && blueTransition == 0) {
			int roll = (int) (Math.random() * 2000D);
			if (roll == 0)
				greenTransition = 1024;
			if (roll == 1)
				blueTransition = 1024;
		}
	}

	/**
	 * Randomizes and smooths the flame noise field and applies an optional rune
	 * mask.
	 * 
	 * @param rune optional rune mask, or {@code null} for an unmasked field
	 */
	private void initializeNoise(IndexedImage rune) {
		for (int i = 0; i < noise.length; i++)
			noise[i] = 0;
		for (int i = 0; i < 5000; i++)
			noise[(int) (Math.random() * SIMULATION_SIZE)] = (int) (Math.random() * 256D);
		for (int pass = 0; pass < 20; pass++) {
			for (int y = 1; y < FLAME_HEIGHT - 1; y++) {
				for (int x = 1; x < 127; x++) {
					int offset = x + (y << 7);
					noiseScratch[offset] = (noise[offset - 1] + noise[offset + 1] + noise[offset - 128]
							+ noise[offset + 128]) / 4;
				}
			}
			int[] swap = noise;
			noise = noiseScratch;
			noiseScratch = swap;
		}
		if (rune != null) {
			int pixel = 0;
			for (int y = 0; y < rune.height; y++) {
				for (int x = 0; x < rune.width; x++) {
					if (rune.pixels[pixel++] != 0) {
						int maskX = x + 16 + rune.offsetX;
						int maskY = y + 16 + rune.offsetY;
						noise[maskX + (maskY << 7)] = 0;
					}
				}
			}
		}
	}

	/** Composites one complete title-flame frame into worker-owned scratch arrays. */
	private void composeFrame() {
		if (greenTransition > 0) {
			for (int i = 0; i < PALETTE_SIZE; i++) {
				if (greenTransition > 768)
					palette[i] = blendColors(redPalette[i], greenPalette[i], 1024 - greenTransition);
				else if (greenTransition > 256)
					palette[i] = greenPalette[i];
				else
					palette[i] = blendColors(greenPalette[i], redPalette[i], 256 - greenTransition);
			}
		} else if (blueTransition > 0) {
			for (int i = 0; i < PALETTE_SIZE; i++) {
				if (blueTransition > 768)
					palette[i] = blendColors(redPalette[i], bluePalette[i], 1024 - blueTransition);
				else if (blueTransition > 256)
					palette[i] = bluePalette[i];
				else
					palette[i] = blendColors(bluePalette[i], redPalette[i], 256 - blueTransition);
			}
		} else {
			System.arraycopy(redPalette, 0, palette, 0, PALETTE_SIZE);
		}
		System.arraycopy(leftBackground.pixels, 0, backLeftFrame, 0, BACKGROUND_PIXEL_COUNT);
		int intensityOffset = 0;
		int framebufferOffset = 1152;
		for (int row = 1; row < FLAME_HEIGHT - 1; row++) {
			int lineOffset = (lineOffsets[row] * (FLAME_HEIGHT - row)) / FLAME_HEIGHT;
			int leftInset = 22 + lineOffset;
			if (leftInset < 0)
				leftInset = 0;
			intensityOffset += leftInset;
			for (int column = leftInset; column < FLAME_WIDTH; column++) {
				int value = intensity[intensityOffset++];
				if (value != 0) {
					int alpha = value;
					int inverseAlpha = 256 - value;
					int flameColor = palette[value];
					int backgroundColor = backLeftFrame[framebufferOffset];
					backLeftFrame[framebufferOffset++] = ((flameColor & 0xff00ff) * alpha
							+ (backgroundColor & 0xff00ff) * inverseAlpha & 0xff00ff00)
							+ ((flameColor & 0xff00) * alpha + (backgroundColor & 0xff00) * inverseAlpha
									& 0xff0000) >> 8;
				} else
					framebufferOffset++;
			}
			framebufferOffset += leftInset;
		}
		System.arraycopy(rightBackground.pixels, 0, backRightFrame, 0, BACKGROUND_PIXEL_COUNT);
		intensityOffset = 0;
		framebufferOffset = 1176;
		for (int row = 1; row < FLAME_HEIGHT - 1; row++) {
			int lineOffset = (lineOffsets[row] * (FLAME_HEIGHT - row)) / FLAME_HEIGHT;
			int visibleWidth = 103 - lineOffset;
			framebufferOffset += lineOffset;
			for (int column = 0; column < visibleWidth; column++) {
				int value = intensity[intensityOffset++];
				if (value != 0) {
					int alpha = value;
					int inverseAlpha = 256 - value;
					int flameColor = palette[value];
					int backgroundColor = backRightFrame[framebufferOffset];
					backRightFrame[framebufferOffset++] = ((flameColor & 0xff00ff) * alpha
							+ (backgroundColor & 0xff00ff) * inverseAlpha & 0xff00ff00)
							+ ((flameColor & 0xff00) * alpha + (backgroundColor & 0xff00) * inverseAlpha
									& 0xff0000) >> 8;
				} else
					framebufferOffset++;
			}
			intensityOffset += FLAME_WIDTH - visibleWidth;
			framebufferOffset += FLAME_WIDTH - visibleWidth - lineOffset;
		}
		synchronized (frameLock) {
			int[] reusableLeft = previousLeftFrame;
			previousLeftFrame = frontLeftFrame;
			frontLeftFrame = backLeftFrame;
			backLeftFrame = reusableLeft;

			int[] reusableRight = previousRightFrame;
			previousRightFrame = frontRightFrame;
			frontRightFrame = backRightFrame;
			backRightFrame = reusableRight;
			frontFramePublishedNanos = System.nanoTime();
		}
	}

	/**
	 * Copies the latest complete worker frame into the legacy title buffers and
	 * draws them through the caller-owned presentation graphics. This keeps all
	 * AWT presentation on the main render thread instead of racing the flame worker
	 * against the high-refresh off-screen frame compositor.
	 *
	 * @param graphics current frame presentation graphics
	 * @return {@code true} when a prepared flame frame was presented
	 */
	public boolean present(Graphics graphics) {
		synchronized (presentationLock) {
			if (graphics == null || leftBuffer == null || rightBuffer == null) {
				return false;
			}
			synchronized (frameLock) {
				if (previousLeftFrame == null || previousRightFrame == null || frontLeftFrame == null
						|| frontRightFrame == null) {
					return false;
				}
				long elapsed = Math.max(0L, System.nanoTime() - frontFramePublishedNanos);
				int blend = (int) Math.min(256L, elapsed * 256L / UPDATE_STEP_NANOS);
				interpolateFrame(previousLeftFrame, frontLeftFrame, leftBuffer.pixels, blend);
				interpolateFrame(previousRightFrame, frontRightFrame, rightBuffer.pixels, blend);
			}
			drawTitleSides(graphics);
			return true;
		}
	}

	/**
	 * Presents the newest complete flame frame without interpolation. Startup uses
	 * this path because the main high-refresh render loop does not begin until the
	 * synchronous archive/bootstrap sequence has completed.
	 */
	public boolean presentLatest(Graphics graphics) {
		synchronized (presentationLock) {
			return presentLatestLocked(graphics);
		}
	}

	/** Presents one worker-owned frame while startup still has no main render loop. */
	private void presentStartupFrame() {
		if (!startupPresentationEnabled) {
			return;
		}
		synchronized (presentationLock) {
			if (!startupPresentationEnabled || startupGraphicsSupplier == null) {
				return;
			}
			presentLatestLocked(startupGraphicsSupplier.get());
		}
	}

	/** Copies and draws the newest frame while {@link #presentationLock} is held. */
	private boolean presentLatestLocked(Graphics graphics) {
		if (graphics == null || leftBuffer == null || rightBuffer == null) {
			return false;
		}
		synchronized (frameLock) {
			if (frontLeftFrame == null || frontRightFrame == null) {
				return false;
			}
			System.arraycopy(frontLeftFrame, 0, leftBuffer.pixels, 0, BACKGROUND_PIXEL_COUNT);
			System.arraycopy(frontRightFrame, 0, rightBuffer.pixels, 0, BACKGROUND_PIXEL_COUNT);
		}
		drawTitleSides(graphics);
		return true;
	}

	/** Draws the two title-side buffers to their fixed classic positions. */
	private void drawTitleSides(Graphics graphics) {
		leftBuffer.draw(graphics, 0, 0);
		rightBuffer.draw(graphics, 637, 0);
	}

	/**
	 * Interpolates two complete RGB flame frames using an 8-bit fixed-point
	 * fraction. Rendering deliberately trails simulation by one 20 ms flame step
	 * so high-refresh presentation can move smoothly between two known states
	 * without changing the original 50 Hz simulation.
	 */
	private static void interpolateFrame(int[] previous, int[] current, int[] destination, int blend) {
		if (blend <= 0) {
			System.arraycopy(previous, 0, destination, 0, BACKGROUND_PIXEL_COUNT);
			return;
		}
		if (blend >= 256) {
			System.arraycopy(current, 0, destination, 0, BACKGROUND_PIXEL_COUNT);
			return;
		}
		int inverseBlend = 256 - blend;
		for (int pixelIndex = 0; pixelIndex < BACKGROUND_PIXEL_COUNT; pixelIndex++) {
			int from = previous[pixelIndex];
			int to = current[pixelIndex];
			destination[pixelIndex] = ((from & 0xff00ff) * inverseBlend + (to & 0xff00ff) * blend
					& 0xff00ff00)
					+ ((from & 0xff00) * inverseBlend + (to & 0xff00) * blend & 0xff0000) >> 8;
		}
	}

	/**
	 * Blends two palette colors using the original 8-bit fixed-point weight.
	 * 
	 * @param fromColor source color
	 * @param toColor   destination color
	 * @param blend     destination weight
	 * @return blended RGB value
	 */
	private static int blendColors(int fromColor, int toColor, int blend) {
		int inverseBlend = 256 - blend;
		return ((fromColor & 0xff00ff) * inverseBlend + (toColor & 0xff00ff) * blend & 0xff00ff00)
				+ ((fromColor & 0xff00) * inverseBlend + (toColor & 0xff00) * blend & 0xff0000) >> 8;
	}
}
