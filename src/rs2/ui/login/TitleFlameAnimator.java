package rs2.ui.login;

import java.awt.Graphics;
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
	/** Supplies the current AWT graphics context for presentation. */
	private Supplier<Graphics> graphicsSupplier;
	/** Rolling offset into {@link #noise}. */
	private int noiseOffset;
	/** Number of completed title-flame frames. */
	private int cycle;
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
	}

	/**
	 * Starts the dedicated flame worker if it is not already running.
	 * 
	 * @param gameCycleSupplier supplies the main client cycle
	 * @param graphicsSupplier  supplies the AWT graphics context used for drawing
	 */
	public void start(IntSupplier gameCycleSupplier, Supplier<Graphics> graphicsSupplier) {
		this.gameCycleSupplier = gameCycleSupplier;
		this.graphicsSupplier = graphicsSupplier;
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

	/** Requests asynchronous worker termination without waiting for the thread. */
	public void requestStop() {
		running = false;
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
		graphicsSupplier = null;
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

	/** Runs the original adaptive-sleep title-flame timing loop. */
	private void runLoop() {
		Thread current = Thread.currentThread();
		try {
			long timingWindowStart = System.currentTimeMillis();
			int timingSampleCount = 0;
			int sleepMillis = 20;
			while (running) {
				cycle++;
				update();
				update();
				draw();
				if (++timingSampleCount > 10) {
					long now = System.currentTimeMillis();
					int timingError = (int) (now - timingWindowStart) / 10 - sleepMillis;
					sleepMillis = 40 - timingError;
					if (sleepMillis < 5) {
						sleepMillis = 5;
					}
					timingSampleCount = 0;
					timingWindowStart = now;
				}
				try {
					Thread.sleep(sleepMillis);
				} catch (Exception ignored) {
					// The running flag decides whether the loop continues.
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

	/** Composites and presents the current title-flame frame. */
	private void draw() {
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
		System.arraycopy(leftBackground.pixels, 0, leftBuffer.pixels, 0, BACKGROUND_PIXEL_COUNT);
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
					int backgroundColor = leftBuffer.pixels[framebufferOffset];
					leftBuffer.pixels[framebufferOffset++] = ((flameColor & 0xff00ff) * alpha
							+ (backgroundColor & 0xff00ff) * inverseAlpha & 0xff00ff00)
							+ ((flameColor & 0xff00) * alpha + (backgroundColor & 0xff00) * inverseAlpha
									& 0xff0000) >> 8;
				} else
					framebufferOffset++;
			}
			framebufferOffset += leftInset;
		}
		Graphics graphics = graphicsSupplier.get();
		leftBuffer.draw(graphics, 0, 0);
		System.arraycopy(rightBackground.pixels, 0, rightBuffer.pixels, 0, BACKGROUND_PIXEL_COUNT);
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
					int backgroundColor = rightBuffer.pixels[framebufferOffset];
					rightBuffer.pixels[framebufferOffset++] = ((flameColor & 0xff00ff) * alpha
							+ (backgroundColor & 0xff00ff) * inverseAlpha & 0xff00ff00)
							+ ((flameColor & 0xff00) * alpha + (backgroundColor & 0xff00) * inverseAlpha
									& 0xff0000) >> 8;
				} else
					framebufferOffset++;
			}
			intensityOffset += FLAME_WIDTH - visibleWidth;
			framebufferOffset += FLAME_WIDTH - visibleWidth - lineOffset;
		}
		rightBuffer.draw(graphics, 637, 0);
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
