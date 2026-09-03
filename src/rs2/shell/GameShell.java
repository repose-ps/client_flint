package rs2.shell;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

import javax.swing.SwingUtilities;


import rs2.media.GraphicsBuffer;

/**
 * Fixed-size RuneScape client shell.
 *
 * <p>
 * The revision-377 client historically inherited from {@code Applet}. This
 * standalone client does not use applet embedding, so the shell is a
 * {@link Canvas} hosted by {@link GameFrame}. The game-loop timing, input
 * encoding and frame-coordinate adjustments remain compatible with the original
 * client.
 * </p>
 */
public class GameShell extends Canvas
		implements Runnable, MouseListener, MouseMotionListener, KeyListener, FocusListener, WindowListener {

	/** Creates a new game shell with its default client state. */
	public GameShell() {
	}

	/** Defines the key buffer size constant. */
	private static final int KEY_BUFFER_SIZE = 128;
	/** Defines the shutdown requested constant. */
	private static final int SHUTDOWN_REQUESTED = -1;
	/** Defines the stopped constant. */
	private static final int STOPPED = -2;

	/** Stores the current shutdown countdown. */
	private volatile int shutdownCountdown;
	/** Serializes AWT input mutation with one game tick's consumption. */
	private final Object inputLock = new Object();
	/** Protects game-thread ownership and one-time cleanup state. */
	private final Object lifecycleLock = new Object();
	/** Stores the current game thread. */
	private volatile Thread gameThread;
	/** Whether cleanup started is enabled or active. */
	private boolean cleanupStarted;
	/** Whether cleanup complete is enabled or active. */
	private boolean cleanupComplete;
	/** True while a non-game thread owns the normal join-before-cleanup path. */
	private volatile boolean shutdownJoinPending;

	/** Stores the current cycle duration millis. */
	protected int cycleDurationMillis = 20;

	/** Stores the current minimum sleep millis. */
	protected int minimumSleepMillis = 1;

	/** Maximum number of fixed logic updates processed after a long stall. */
	private static final int MAX_CATCH_UP_TICKS = 10;
	/** Long pauses are clamped so resuming the client cannot trigger a huge tick burst. */
	private static final long MAX_ELAPSED_NANOS = 250_000_000L;
	/** Nanoseconds in one millisecond. */
	private static final long NANOS_PER_MILLI = 1_000_000L;
	/** Nanoseconds in one second. */
	private static final long NANOS_PER_SECOND = 1_000_000_000L;
	/** Generic frame diagnostics interval. */
	private static final long FRAME_STATS_INTERVAL_NANOS = 5_000_000_000L;
	/** Enables whole-client frame diagnostics; GPU stats imply this for correlated profiling. */
	private static final boolean FRAME_STATS = Boolean.getBoolean(FrameTimingConfig.FRAME_STATS_PROPERTY)
			|| Boolean.getBoolean("flint.gpu.perfStats");

	/** Stores the current measured render fps. */
	protected int fps;
	/** Configurable presentation cap. Zero means uncapped. */
	private volatile int renderFpsLimit = 50;
	/** Fraction between the previous and current fixed logic state used for rendering. */
	private volatile float renderInterpolationAlpha = 1.0f;
	/** Tracks whether debug timing. */
	protected boolean debugTiming;

	/** Stores the current canvas width. */
	protected int canvasWidth;

	/** Stores the current canvas height. */
	protected int canvasHeight;

	/** Stores the current graphics. */
	protected volatile Graphics graphics;

	/** Requests a fresh component graphics context after an AWT expose/repaint. */
	private volatile boolean graphicsRefreshRequested;

	/** Stores the current game buffer. */
	protected GraphicsBuffer gameBuffer;

	/** Stores the current game frame. */
	protected GameFrame gameFrame;

	/** Tracks whether clear screen. */
	private volatile boolean clearScreen = true;
	/** Tracks whether has focus. */
	protected volatile boolean hasFocus = true;

	/** Stores the current idle cycles. */
	protected volatile int idleCycles;

	/** Current gameplay mouse button state: 0 none, 1 primary, 2 secondary. */
	protected volatile int mouseButton;

	/** Stores the current mouse X. */
	protected volatile int mouseX;

	/** Stores the current mouse Y. */
	protected volatile int mouseY;

	/** Stores the current pending click button. */
	private int pendingClickButton;

	/** Stores the current pending click X. */
	private int pendingClickX;

	/** Stores the current pending click Y. */
	private int pendingClickY;

	/** Stores the current pending click time. */
	private long pendingClickTime;

	/** Horizontal middle-mouse camera movement waiting for the next client tick. */
	private int pendingCameraDragDeltaX;

	/** Vertical middle-mouse camera movement waiting for the next client tick. */
	private int pendingCameraDragDeltaY;

	/** Whether the middle mouse button is currently being held. */
	private boolean middleMouseDown;

	/**
	 * Previous horizontal mouse position used to measure middle-button camera
	 * dragging.
	 */
	private int middleDragX;

	/**
	 * Previous vertical mouse position used to measure middle-button camera
	 * dragging.
	 */
	private int middleDragY;

	/** Mouse click latched at the start of the current client tick. */
	protected int clickButton;

	/** Stores the current click X. */
	protected int clickX;

	/** Stores the current click Y. */
	protected int clickY;

	/** Stores the current click time. */
	protected long clickTime;

	/**
	 * Horizontal middle-mouse camera movement accumulated for the current client
	 * tick.
	 */
	protected int cameraDragDeltaX;

	/**
	 * Vertical middle-mouse camera movement accumulated for the current client
	 * tick.
	 */
	protected int cameraDragDeltaY;

	/** Pressed state for the client's 0..127 internal key codes. */
	protected final int[] keyStatus = new int[KEY_BUFFER_SIZE];

	/** Stores key queue values. */
	private final int[] keyQueue = new int[KEY_BUFFER_SIZE];

	/** Stores the current key queue read index. */
	private int keyQueueReadIndex;

	/** Stores the current key queue write index. */
	private int keyQueueWriteIndex;

	/**
	 * Creates the standalone game frame and starts this shell's game thread.
	 *
	 * @param width  the width
	 * @param height the height
	 */
	public final void createFrame(int width, int height) {
		canvasWidth = width;
		canvasHeight = height;
		gameFrame = new GameFrame(this, width, height);
		onFrameCreated(gameFrame);
		initializeGraphics();
	}

	/**
	 * Initializes the AWT graphics context and software framebuffer.
	 */
	private void initializeGraphics() {
		Component component = getGameComponent();
		graphics = component.getGraphics();
		gameBuffer = new GraphicsBuffer(component, canvasWidth, canvasHeight);
		startThread(this, 1);
	}

	/**
	 * Runs this component's main processing loop.
	 */
	@Override
	public void run() {
		Thread currentThread = Thread.currentThread();
		synchronized (lifecycleLock) {
			gameThread = currentThread;
		}
		try {
			Component component = getGameComponent();
			component.addMouseListener(this);
			component.addMouseMotionListener(this);
			component.addKeyListener(this);
			component.addFocusListener(this);
			if (gameFrame != null) {
				gameFrame.addWindowListener(this);
			}

			drawLoadingText(0, "Loading...");
			startUp();

			long previousTime = System.nanoTime();
			long logicAccumulator = 0L;
			long nextRenderDeadline = previousTime;
			long fpsWindowStarted = previousTime;
			long frameStatsStarted = previousTime;
			long frameStatsRenderNanos = 0L;
			long frameStatsMaxRenderNanos = 0L;
			long frameStatsLogicNanos = 0L;
			long frameStatsMaxLogicNanos = 0L;
			long frameStatsLateNanos = 0L;
			long frameStatsMaxLateNanos = 0L;
			int frameStatsFrames = 0;
			int frameStatsLogicTicks = 0;
			int renderedFrames = 0;

			while (shutdownCountdown >= 0) {
				synchronizeCanvasSize();
				long now = System.nanoTime();
				long elapsed = now - previousTime;
				previousTime = now;
				if (elapsed < 0L) {
					elapsed = 0L;
				} else if (elapsed > MAX_ELAPSED_NANOS) {
					elapsed = MAX_ELAPSED_NANOS;
				}
				logicAccumulator += elapsed;

				long logicStepNanos = Math.max(1, cycleDurationMillis) * NANOS_PER_MILLI;
				int processedTicks = 0;
				while (logicAccumulator >= logicStepNanos && processedTicks < MAX_CATCH_UP_TICKS) {
					if (shutdownCountdown > 0) {
						shutdownCountdown--;
						if (shutdownCountdown == 0) {
							exit();
							return;
						}
					}
					long logicStarted = FRAME_STATS ? System.nanoTime() : 0L;
					consumeInputAndProcessGameLoop();
					if (FRAME_STATS) {
						long logicNanos = System.nanoTime() - logicStarted;
						frameStatsLogicNanos += logicNanos;
						frameStatsMaxLogicNanos = Math.max(frameStatsMaxLogicNanos, logicNanos);
					}
					logicAccumulator -= logicStepNanos;
					processedTicks++;
				}
				if (processedTicks == MAX_CATCH_UP_TICKS && logicAccumulator >= logicStepNanos) {
					logicAccumulator %= logicStepNanos;
				}
				frameStatsLogicTicks += processedTicks;

				int renderLimit = renderFpsLimit;
				long renderStepNanos = renderLimit > 0 ? Math.max(1L, NANOS_PER_SECOND / renderLimit) : 0L;
				long renderCheckTime = System.nanoTime();
				long interpolationAccumulator = logicAccumulator + Math.max(0L, renderCheckTime - now);
				renderInterpolationAlpha = Math.min(1.0f, interpolationAccumulator / (float) logicStepNanos);
				boolean renderDue = renderLimit <= 0 || renderCheckTime >= nextRenderDeadline;
				if (renderDue) {
					long renderStarted = System.nanoTime();
					long schedulerLate = renderLimit > 0 ? Math.max(0L, renderStarted - nextRenderDeadline) : 0L;
					processDrawing();
					long afterRender = System.nanoTime();
					long renderNanos = afterRender - renderStarted;
					renderedFrames++;
					frameStatsFrames++;
					frameStatsRenderNanos += renderNanos;
					frameStatsMaxRenderNanos = Math.max(frameStatsMaxRenderNanos, renderNanos);
					frameStatsLateNanos += schedulerLate;
					frameStatsMaxLateNanos = Math.max(frameStatsMaxLateNanos, schedulerLate);
					if (renderLimit > 0) {
						nextRenderDeadline = FramePacer.advanceDeadline(nextRenderDeadline, afterRender, renderStepNanos);
					}
				}

				long afterWork = System.nanoTime();
				long fpsElapsed = afterWork - fpsWindowStarted;
				if (fpsElapsed >= NANOS_PER_SECOND) {
					fps = (int) Math.round(renderedFrames * (double) NANOS_PER_SECOND / fpsElapsed);
					renderedFrames = 0;
					fpsWindowStarted = afterWork;
				}

				long frameStatsElapsed = afterWork - frameStatsStarted;
				if (FRAME_STATS && frameStatsElapsed >= FRAME_STATS_INTERVAL_NANOS) {
					double seconds = frameStatsElapsed / 1_000_000_000.0;
					double measuredFps = frameStatsFrames / seconds;
					double logicHz = frameStatsLogicTicks / seconds;
					double renderMs = frameStatsFrames == 0 ? 0.0
							: frameStatsRenderNanos / 1_000_000.0 / frameStatsFrames;
					double logicMs = frameStatsLogicTicks == 0 ? 0.0
							: frameStatsLogicNanos / 1_000_000.0 / frameStatsLogicTicks;
					double lateMs = frameStatsFrames == 0 ? 0.0
							: frameStatsLateNanos / 1_000_000.0 / frameStatsFrames;
					System.out.printf("Frame perf: %.1f frames/s, logic %.1f Hz / %.3f ms avg / %.3f ms max, "
							+ "client render %.3f ms avg / %.3f ms max, scheduler late %.3f ms avg / %.3f ms max%n",
							measuredFps, logicHz, logicMs, frameStatsMaxLogicNanos / 1_000_000.0, renderMs,
							frameStatsMaxRenderNanos / 1_000_000.0, lateMs, frameStatsMaxLateNanos / 1_000_000.0);
					frameStatsStarted = afterWork;
					frameStatsRenderNanos = 0L;
					frameStatsMaxRenderNanos = 0L;
					frameStatsLogicNanos = 0L;
					frameStatsMaxLogicNanos = 0L;
					frameStatsLateNanos = 0L;
					frameStatsMaxLateNanos = 0L;
					frameStatsFrames = 0;
					frameStatsLogicTicks = 0;
				}

				if (debugTiming) {
					System.out.println("fps:" + fps + " logicHz:" + Math.max(1, 1000 / Math.max(1, cycleDurationMillis))
							+ " renderCap:" + (renderLimit == 0 ? "unlimited" : renderLimit)
							+ " alpha:" + renderInterpolationAlpha + " catchup:" + processedTicks);
					debugTiming = false;
				}

				if (renderLimit > 0) {
					long unaccountedWork = Math.max(0L, afterWork - now);
					long effectiveAccumulator = logicAccumulator + unaccountedWork;
					long untilLogic = Math.max(0L, logicStepNanos - effectiveAccumulator);
					long logicDeadline = afterWork + untilLogic;
					long waitDeadline = Math.min(logicDeadline, nextRenderDeadline);
					FramePacer.waitUntil(waitDeadline);
				}
			}

			if (shutdownCountdown == SHUTDOWN_REQUESTED && !shutdownJoinPending) {
				exit();
			}
		} finally {
			synchronized (lifecycleLock) {
				if (gameThread == currentThread) {
					gameThread = null;
				}
				lifecycleLock.notifyAll();
			}
		}
	}

	/** Consumes one input snapshot and advances exactly one fixed logic cycle. */
	private void consumeInputAndProcessGameLoop() {
		synchronized (inputLock) {
			clickButton = pendingClickButton;
			clickX = pendingClickX;
			clickY = pendingClickY;
			clickTime = pendingClickTime;
			pendingClickButton = 0;

			cameraDragDeltaX = pendingCameraDragDeltaX;
			cameraDragDeltaY = pendingCameraDragDeltaY;
			pendingCameraDragDeltaX = 0;
			pendingCameraDragDeltaY = 0;

			processGameLoop();
			keyQueueReadIndex = keyQueueWriteIndex;
		}
	}

	/**
	 * Performs the client cleanup hook and terminates a standalone frame.
	 */
	public void exit() {
		shutdownCountdown = STOPPED;
		boolean performCleanup = false;
		synchronized (lifecycleLock) {
			if (!cleanupStarted) {
				cleanupStarted = true;
				performCleanup = true;
			} else {
				boolean interrupted = false;
				while (!cleanupComplete) {
					try {
						lifecycleLock.wait();
					} catch (InterruptedException exception) {
						interrupted = true;
					}
				}
				if (interrupted) {
					Thread.currentThread().interrupt();
				}
			}
		}

		if (!performCleanup) {
			return;
		}
		try {
			cleanUpForQuit();
		} finally {
			synchronized (lifecycleLock) {
				cleanupComplete = true;
				lifecycleLock.notifyAll();
			}
		}
		if (gameFrame != null) {
			try {
				Thread.sleep(1000L);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
			try {
				System.exit(0);
			} catch (Throwable ignored) {
				// The original client tolerated a denied System.exit().
			}
		}
	}

	/**
	 * Sets the fixed logic-loop frequency. Normal gameplay remains at 50 Hz; this
	 * compatibility hook is retained for startup/error states.
	 *
	 * @param fps logic updates per second
	 */
	public final void setTargetFps(int fps) {
		if (fps <= 0) {
			throw new IllegalArgumentException("Logic FPS must be positive: " + fps);
		}
		cycleDurationMillis = Math.max(1, 1000 / fps);
	}

	/** Sets the independent presentation rate. Zero means uncapped. */
	public final void setRenderFps(int fps) {
		if (fps < 0) {
			throw new IllegalArgumentException("Render FPS must be zero or positive: " + fps);
		}
		renderFpsLimit = fps;
	}

	/** Returns the configured independent presentation cap. Zero means uncapped. */
	public final int renderFpsLimit() {
		return renderFpsLimit;
	}

	/** Returns interpolation progress between the previous and current 50 Hz states. */
	protected final float renderInterpolationAlpha() {
		return renderInterpolationAlpha;
	}

	/**
	 * Requests shutdown and retains the original ten-second fallback if the game
	 * thread fails to observe the request.
	 */
	public final void shutdown() {
		Thread thread;
		synchronized (lifecycleLock) {
			thread = gameThread;
			shutdownJoinPending = thread != null && thread != Thread.currentThread();
			shutdownCountdown = SHUTDOWN_REQUESTED;
		}

		if (thread == null) {
			shutdownJoinPending = false;
			exit();
			return;
		}
		if (thread == Thread.currentThread()) {
			return;
		}

		thread.interrupt();
		long deadline = System.currentTimeMillis() + 10000L;
		boolean interrupted = false;
		while (thread.isAlive()) {
			long remaining = deadline - System.currentTimeMillis();
			if (remaining <= 0L) {
				break;
			}
			try {
				thread.join(remaining);
			} catch (InterruptedException exception) {
				interrupted = true;
			}
		}
		if (interrupted) {
			Thread.currentThread().interrupt();
		}

		shutdownJoinPending = false;
		/*
		 * Normal shutdown reaches here only after the owned game thread has ended. If
		 * it is still alive, this is the preserved hard-stop fallback path.
		 */
		exit();
	}

	/**
	 * Updates the component using the supplied graphics context.
	 *
	 * @param graphics the graphics
	 */
	@Override
	public final void update(Graphics graphics) {
		if (this.graphics == null) {
			this.graphics = graphics;
		}
		graphicsRefreshRequested = true;
		clearScreen = true;
	}

	/**
	 * Paints the component using the supplied graphics context.
	 *
	 * @param graphics the graphics
	 */
	@Override
	public final void paint(Graphics graphics) {
		if (this.graphics == null) {
			this.graphics = graphics;
		}
		graphicsRefreshRequested = true;
		clearScreen = true;
	}

	/**
	 * Handles the mouse pressed event.
	 *
	 * @param event the event
	 */
	@Override
	public final void mousePressed(MouseEvent event) {
		int x = toClientX(event);
		int y = toClientY(event);

		synchronized (inputLock) {
			idleCycles = 0;

			if (event.getButton() == MouseEvent.BUTTON2) {
				middleMouseDown = true;
				middleDragX = x;
				middleDragY = y;
				return;
			}

			if (event.getButton() != MouseEvent.BUTTON1 && event.getButton() != MouseEvent.BUTTON3) {
				return;
			}

			pendingClickX = x;
			pendingClickY = y;
			pendingClickTime = System.currentTimeMillis();

			if (event.getButton() == MouseEvent.BUTTON3) {
				pendingClickButton = 2;
				mouseButton = 2;
			} else {
				pendingClickButton = 1;
				mouseButton = 1;
			}
		}
	}

	/**
	 * Handles the mouse released event.
	 *
	 * @param event the event
	 */
	@Override
	public final void mouseReleased(MouseEvent event) {
		synchronized (inputLock) {
			idleCycles = 0;

			if (event.getButton() == MouseEvent.BUTTON2) {
				middleMouseDown = false;
				return;
			}

			if ((event.getButton() == MouseEvent.BUTTON1 && mouseButton == 1)
					|| (event.getButton() == MouseEvent.BUTTON3 && mouseButton == 2)) {
				mouseButton = 0;
			}
		}
	}

	/**
	 * Handles the mouse clicked event.
	 *
	 * @param event the event
	 */
	@Override
	public final void mouseClicked(MouseEvent event) {
	}

	/**
	 * Handles the mouse entered event.
	 *
	 * @param event the event
	 */
	@Override
	public final void mouseEntered(MouseEvent event) {
	}

	/**
	 * Handles the mouse exited event.
	 *
	 * @param event the event
	 */
	@Override
	public final void mouseExited(MouseEvent event) {
		synchronized (inputLock) {
			idleCycles = 0;
			mouseX = -1;
			mouseY = -1;
		}
	}

	/**
	 * Handles the mouse dragged event.
	 *
	 * @param event the event
	 */
	@Override
	public final void mouseDragged(MouseEvent event) {
		int x = toClientX(event);
		int y = toClientY(event);

		synchronized (inputLock) {
			idleCycles = 0;

			if (middleMouseDown) {
				pendingCameraDragDeltaX += x - middleDragX;
				pendingCameraDragDeltaY += y - middleDragY;
				middleDragX = x;
				middleDragY = y;
			}

			mouseX = x;
			mouseY = y;
		}
	}

	/**
	 * Handles the mouse moved event.
	 *
	 * @param event the event
	 */
	@Override
	public final void mouseMoved(MouseEvent event) {
		updateMousePosition(event);
	}

	/**
	 * Updates mouse position.
	 *
	 * @param event the event
	 */
	private void updateMousePosition(MouseEvent event) {
		int x = toClientX(event);
		int y = toClientY(event);
		synchronized (inputLock) {
			idleCycles = 0;
			mouseX = x;
			mouseY = y;
		}
	}

	/**
	 * Handles the key pressed event.
	 *
	 * @param event the event
	 */
	@Override
	public final void keyPressed(KeyEvent event) {
		synchronized (inputLock) {
			idleCycles = 0;
			int keyCode = event.getKeyCode();
			int key = event.getKeyChar();
			if (key < 30) {
				key = 0;
			}
			if (keyCode == KeyEvent.VK_LEFT) {
				key = 1;
			}
			if (keyCode == KeyEvent.VK_RIGHT) {
				key = 2;
			}
			if (keyCode == KeyEvent.VK_UP) {
				key = 3;
			}
			if (keyCode == KeyEvent.VK_DOWN) {
				key = 4;
			}
			if (keyCode == KeyEvent.VK_CONTROL) {
				key = 5;
			}
			if (keyCode == KeyEvent.VK_BACK_SPACE || keyCode == KeyEvent.VK_DELETE) {
				key = 8;
			}
			if (keyCode == KeyEvent.VK_TAB) {
				key = 9;
			}
			if (keyCode == KeyEvent.VK_ENTER) {
				key = 10;
			}
			if (keyCode >= KeyEvent.VK_F1 && keyCode <= KeyEvent.VK_F12) {
				key = 1008 + keyCode - KeyEvent.VK_F1;
			}
			if (keyCode == KeyEvent.VK_HOME) {
				key = 1000;
			}
			if (keyCode == KeyEvent.VK_END) {
				key = 1001;
			}
			if (keyCode == KeyEvent.VK_PAGE_UP) {
				key = 1002;
			}
			if (keyCode == KeyEvent.VK_PAGE_DOWN) {
				key = 1003;
			}

			if (key > 0 && key < 128) {
				keyStatus[key] = 1;
			}
			if (key > 4) {
				keyQueue[keyQueueWriteIndex] = key;
				keyQueueWriteIndex = (keyQueueWriteIndex + 1) & 0x7f;
			}
		}
	}

	/**
	 * Handles the key released event.
	 *
	 * @param event the event
	 */
	@Override
	public final void keyReleased(KeyEvent event) {
		synchronized (inputLock) {
			idleCycles = 0;
			int keyCode = event.getKeyCode();
			int key = event.getKeyChar();
			if (key < 30) {
				key = 0;
			}
			if (keyCode == KeyEvent.VK_LEFT) {
				key = 1;
			}
			if (keyCode == KeyEvent.VK_RIGHT) {
				key = 2;
			}
			if (keyCode == KeyEvent.VK_UP) {
				key = 3;
			}
			if (keyCode == KeyEvent.VK_DOWN) {
				key = 4;
			}
			if (keyCode == KeyEvent.VK_CONTROL) {
				key = 5;
			}
			if (keyCode == KeyEvent.VK_BACK_SPACE || keyCode == KeyEvent.VK_DELETE) {
				key = 8;
			}
			if (keyCode == KeyEvent.VK_TAB) {
				key = 9;
			}
			if (keyCode == KeyEvent.VK_ENTER) {
				key = 10;
			}

			if (key > 0 && key < 128) {
				keyStatus[key] = 0;
			}
		}
	}

	/**
	 * Handles the key typed event.
	 *
	 * @param event the event
	 */
	@Override
	public final void keyTyped(KeyEvent event) {
	}

	/**
	 * Returns the next queued client key code, or {@code -1} when empty.
	 * 
	 * @return the next completed request, or {@code null} when none is available
	 */
	public final int pollKey() {
		synchronized (inputLock) {
			int key = -1;
			if (keyQueueWriteIndex != keyQueueReadIndex) {
				key = keyQueue[keyQueueReadIndex];
				keyQueueReadIndex = (keyQueueReadIndex + 1) & 0x7f;
			}
			return key;
		}
	}

	/**
	 * Handles the focus gained event.
	 *
	 * @param event the event
	 */
	@Override
	public final void focusGained(FocusEvent event) {
		synchronized (inputLock) {
			hasFocus = true;
			clearScreen = true;
		}
	}

	/**
	 * Handles the focus lost event.
	 *
	 * @param event the event
	 */
	@Override
	public final void focusLost(FocusEvent event) {
		synchronized (inputLock) {
			hasFocus = false;
			mouseButton = 0;
			middleMouseDown = false;
			pendingCameraDragDeltaX = 0;
			pendingCameraDragDeltaY = 0;

			for (int keyCode = 0; keyCode < KEY_BUFFER_SIZE; keyCode++) {
				keyStatus[keyCode] = 0;
			}
		}
	}

	/**
	 * Handles the window activated event.
	 *
	 * @param event the event
	 */
	@Override
	public final void windowActivated(WindowEvent event) {
	}

	/**
	 * Handles the window closed event.
	 *
	 * @param event the event
	 */
	@Override
	public final void windowClosed(WindowEvent event) {
	}

	/**
	 * Handles the window closing event.
	 *
	 * @param event the event
	 */
	@Override
	public final void windowClosing(WindowEvent event) {
		shutdown();
	}

	/**
	 * Handles the window deactivated event.
	 *
	 * @param event the event
	 */
	@Override
	public final void windowDeactivated(WindowEvent event) {
	}

	/**
	 * Handles the window deiconified event.
	 *
	 * @param event the event
	 */
	@Override
	public final void windowDeiconified(WindowEvent event) {
	}

	/**
	 * Handles the window iconified event.
	 *
	 * @param event the event
	 */
	@Override
	public final void windowIconified(WindowEvent event) {
	}

	/**
	 * Handles the window opened event.
	 *
	 * @param event the event
	 */
	@Override
	public final void windowOpened(WindowEvent event) {
	}

	/**
	 * Synchronizes the logical client-area dimensions with a user-resized frame.
	 * The callback is invoked on the game thread so renderer buffers are never
	 * recreated from the AWT event thread.
	 */
	private void synchronizeCanvasSize() {
		if (gameFrame == null)
			return;

		int width = gameFrame.getClientWidth();
		int height = gameFrame.getClientHeight();
		if (width == canvasWidth && height == canvasHeight)
			return;

		canvasWidth = width;
		canvasHeight = height;
		graphicsRefreshRequested = true;
		clearScreen = true;
		onResize(width, height);
	}

	/**
	 * Converts a frame-relative X coordinate to client-area coordinates.
	 *
	 * @param frameX the frame X
	 * @return the converted value
	 */
	private int toClientX(MouseEvent event) {
		if (gameFrame == null) {
			return event.getX();
		}
		Point point = event.getComponent() == gameFrame ? event.getPoint()
				: SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), gameFrame);
		return gameFrame.toClientX(point.x);
	}

	/** Converts mouse-event Y coordinates from either the frame or a child surface. */
	private int toClientY(MouseEvent event) {
		if (gameFrame == null) {
			return event.getY();
		}
		Point point = event.getComponent() == gameFrame ? event.getPoint()
				: SwingUtilities.convertPoint(event.getComponent(), event.getPoint(), gameFrame);
		return gameFrame.toClientY(point.y);
	}

	/** Called after the standalone AWT host frame becomes available. */
	protected void onFrameCreated(GameFrame frame) {
	}

	/**
	 * Called on the game thread whenever the drawable client area changes size.
	 *
	 * @param width  the width in pixels
	 * @param height the height in pixels
	 */
	protected void onResize(int width, int height) {
	}

	/** Client initialization hook. */
	protected void startUp() {
	}

	/** One logical client tick. */
	protected void processGameLoop() {
	}

	/** Client resource-cleanup hook invoked before standalone exit. */
	protected void cleanUpForQuit() {
	}

	/** One client draw pass. */
	protected void processDrawing() {
	}

	/**
	 * Reacquires the component graphics after AWT reports an expose/repaint.
	 *
	 * <p>
	 * The standalone frame applies its client-area inset translation in
	 * {@link GameFrame#getGraphics()}, so reacquiring here is safer than retaining
	 * the Graphics instance supplied to {@link #paint(Graphics)}.
	 * </p>
	 */
	protected final void refreshGraphicsContextIfRequested() {
		if (!graphicsRefreshRequested)
			return;

		Graphics refreshedGraphics = getGameComponent().getGraphics();
		if (refreshedGraphics == null)
			return;

		graphics = refreshedGraphics;
		graphicsRefreshRequested = false;
	}

	/**
	 * Returns the top-level AWT component used for input and drawing.
	 * 
	 * @return the game component
	 */
	public Component getGameComponent() {
		return gameFrame != null ? gameFrame : this;
	}

	/**
	 * Starts a client worker thread with the original start-then-prioritize
	 * ordering.
	 *
	 * @param runnable the runnable
	 * @param priority the priority
	 */
	public void startThread(Runnable runnable, int priority) {
		Thread thread = new Thread(runnable);
		thread.start();
		thread.setPriority(priority);
	}

	/**
	 * Draws the classic fixed-size loading bar directly through AWT.
	 *
	 * @param progress the progress
	 * @param text     the text
	 */
	public void drawLoadingText(int progress, String text) {
		while (graphics == null) {
			Component component = getGameComponent();
			graphics = component.getGraphics();
			try {
				component.repaint();
			} catch (Exception ignored) {
			}
			try {
				Thread.sleep(1000L);
			} catch (Exception ignored) {
			}
		}

		Font boldFont = new Font("Helvetica", Font.BOLD, 13);
		FontMetrics metrics = getGameComponent().getFontMetrics(boldFont);
		Font plainFont = new Font("Helvetica", Font.PLAIN, 13);
		getGameComponent().getFontMetrics(plainFont);

		if (clearScreen) {
			graphics.setColor(Color.black);
			graphics.fillRect(0, 0, canvasWidth, canvasHeight);
			clearScreen = false;
		}

		Color loadingColor = new Color(140, 17, 17);
		int y = canvasHeight / 2 - 18;
		graphics.setColor(loadingColor);
		graphics.drawRect(canvasWidth / 2 - 152, y, 304, 34);
		graphics.fillRect(canvasWidth / 2 - 150, y + 2, progress * 3, 30);
		graphics.setColor(Color.black);
		graphics.fillRect((canvasWidth / 2 - 150) + progress * 3, y + 2, 300 - progress * 3, 30);
		graphics.setFont(boldFont);
		graphics.setColor(Color.white);
		graphics.drawString(text, (canvasWidth - metrics.stringWidth(text)) / 2, y + 22);
	}

	/**
	 * Current mouse X used by the asynchronous mouse recorder.
	 * 
	 * @return the mouse X
	 */
	public final int getMouseX() {
		return mouseX;
	}

	/**
	 * Current mouse Y used by the asynchronous mouse recorder.
	 * 
	 * @return the mouse Y
	 */
	public final int getMouseY() {
		return mouseY;
	}
}
