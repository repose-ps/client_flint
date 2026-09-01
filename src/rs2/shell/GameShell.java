package rs2.shell;

import java.awt.Canvas;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.event.FocusEvent;
import java.awt.event.FocusListener;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;

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

	/** Defines the timing sample count constant. */
	private static final int TIMING_SAMPLE_COUNT = 10;
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

	/** Stores timing samples values. */
	private final long[] timingSamples = new long[TIMING_SAMPLE_COUNT];

	/** Stores the current fps. */
	protected int fps;
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

			int timingIndex = 0;
			int ratio = 256;
			int sleepMillis = 1;
			int accumulator = 0;
			int interruptedSleeps = 0;

			for (int sampleIndex = 0; sampleIndex < TIMING_SAMPLE_COUNT; sampleIndex++) {
				timingSamples[sampleIndex] = System.currentTimeMillis();
			}

			while (shutdownCountdown >= 0) {
				synchronizeCanvasSize();
				if (shutdownCountdown > 0) {
					shutdownCountdown--;
					if (shutdownCountdown == 0) {
						exit();
						return;
					}
				}

				int previousRatio = ratio;
				int previousSleepMillis = sleepMillis;
				ratio = 300;
				sleepMillis = 1;

				long currentTime = System.currentTimeMillis();
				if (timingSamples[timingIndex] == 0L) {
					ratio = previousRatio;
					sleepMillis = previousSleepMillis;
				} else if (currentTime > timingSamples[timingIndex]) {
					ratio = (int) ((long) (2560 * cycleDurationMillis) / (currentTime - timingSamples[timingIndex]));
				}

				if (ratio < 25) {
					ratio = 25;
				}
				if (ratio > 256) {
					ratio = 256;
					sleepMillis = (int) ((long) cycleDurationMillis - (currentTime - timingSamples[timingIndex]) / 10L);
				}
				if (sleepMillis > cycleDurationMillis) {
					sleepMillis = cycleDurationMillis;
				}

				timingSamples[timingIndex] = currentTime;
				timingIndex = (timingIndex + 1) % TIMING_SAMPLE_COUNT;

				if (sleepMillis > 1) {
					for (int sampleIndex = 0; sampleIndex < TIMING_SAMPLE_COUNT; sampleIndex++) {
						if (timingSamples[sampleIndex] != 0L) {
							timingSamples[sampleIndex] += sleepMillis;
						}
					}
				}

				if (sleepMillis < minimumSleepMillis) {
					sleepMillis = minimumSleepMillis;
				}

				try {
					Thread.sleep(sleepMillis);
				} catch (InterruptedException ignored) {
					interruptedSleeps++;
				}

				for (; accumulator < 256; accumulator += ratio) {
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

				accumulator &= 0xff;
				if (cycleDurationMillis > 0) {
					fps = (1000 * ratio) / (cycleDurationMillis * 256);
				}

				processDrawing();

				if (debugTiming) {
					System.out.println("ntime:" + currentTime);
					for (int sampleIndex = 0; sampleIndex < TIMING_SAMPLE_COUNT; sampleIndex++) {
						int index = ((timingIndex - sampleIndex - 1) + 20) % TIMING_SAMPLE_COUNT;
						System.out.println("otim" + index + ":" + timingSamples[index]);
					}
					System.out.println("fps:" + fps + " ratio:" + ratio + " count:" + accumulator);
					System.out.println(
							"del:" + sleepMillis + " deltime:" + cycleDurationMillis + " mindel:" + minimumSleepMillis);
					System.out.println("intex:" + interruptedSleeps + " opos:" + timingIndex);
					debugTiming = false;
					interruptedSleeps = 0;
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
	 * Sets the target game-loop frequency used by the original ratio timer.
	 *
	 * @param fps the fps
	 */
	public final void setTargetFps(int fps) {
		cycleDurationMillis = 1000 / fps;
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
		int x = toClientX(event.getX());
		int y = toClientY(event.getY());

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
		int x = toClientX(event.getX());
		int y = toClientY(event.getY());

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
		int x = toClientX(event.getX());
		int y = toClientY(event.getY());
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
	private int toClientX(int frameX) {
		return gameFrame == null ? frameX : gameFrame.toClientX(frameX);
	}

	/**
	 * Converts a frame-relative Y coordinate to client-area coordinates.
	 *
	 * @param frameY the frame Y
	 * @return the converted value
	 */
	private int toClientY(int frameY) {
		return gameFrame == null ? frameY : gameFrame.toClientY(frameY);
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
