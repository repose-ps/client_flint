package rs2;

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

	private static final int TIMING_SAMPLE_COUNT = 10;
	private static final int KEY_BUFFER_SIZE = 128;
	private static final int FRAME_MOUSE_X_OFFSET = 4;
	private static final int FRAME_MOUSE_Y_OFFSET = 22;

	private static final int SHUTDOWN_REQUESTED = -1;
	private static final int STOPPED = -2;

	private int shutdownCountdown;
	protected int cycleDurationMillis = 20;
	protected int minimumSleepMillis = 1;
	private final long[] timingSamples = new long[TIMING_SAMPLE_COUNT];

	protected int fps;
	protected boolean debugTiming;

	protected int canvasWidth;
	protected int canvasHeight;
	protected Graphics graphics;
	protected GraphicsBuffer gameBuffer;
	protected GameFrame gameFrame;

	private boolean clearScreen = true;
	protected boolean hasFocus = true;
	protected int idleCycles;

	/** Current mouse button state: 0 none, 1 primary, 2 meta/secondary. */
	protected int mouseButton;
	protected int mouseX;
	protected int mouseY;

	private int pendingClickButton;
	private int pendingClickX;
	private int pendingClickY;
	private long pendingClickTime;

	/** Mouse click latched at the start of the current client tick. */
	protected int clickButton;
	protected int clickX;
	protected int clickY;
	protected long clickTime;

	/** Pressed state for the client's 0..127 internal key codes. */
	protected final int[] keyStatus = new int[KEY_BUFFER_SIZE];
	private final int[] keyQueue = new int[KEY_BUFFER_SIZE];
	private int keyQueueReadIndex;
	private int keyQueueWriteIndex;

	/**
	 * Creates the standalone game frame and starts this shell's game thread.
	 */
	public final void createFrame(int width, int height) {
		canvasWidth = width;
		canvasHeight = height;
		gameFrame = new GameFrame(this, width, height);
		initializeGraphics();
	}

	private void initializeGraphics() {
		Component component = getGameComponent();
		graphics = component.getGraphics();
		gameBuffer = new GraphicsBuffer(component, canvasWidth, canvasHeight);
		startThread(this, 1);
	}

	@Override
	public void run() {
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

		for (int i = 0; i < TIMING_SAMPLE_COUNT; i++) {
			timingSamples[i] = System.currentTimeMillis();
		}

		while (shutdownCountdown >= 0) {
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
				for (int i = 0; i < TIMING_SAMPLE_COUNT; i++) {
					if (timingSamples[i] != 0L) {
						timingSamples[i] += sleepMillis;
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
				clickButton = pendingClickButton;
				clickX = pendingClickX;
				clickY = pendingClickY;
				clickTime = pendingClickTime;
				pendingClickButton = 0;

				processGameLoop();
				keyQueueReadIndex = keyQueueWriteIndex;
			}

			accumulator &= 0xff;
			if (cycleDurationMillis > 0) {
				fps = (1000 * ratio) / (cycleDurationMillis * 256);
			}

			processDrawing();

			if (debugTiming) {
				System.out.println("ntime:" + currentTime);
				for (int i = 0; i < TIMING_SAMPLE_COUNT; i++) {
					int index = ((timingIndex - i - 1) + 20) % TIMING_SAMPLE_COUNT;
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

		if (shutdownCountdown == SHUTDOWN_REQUESTED) {
			exit();
		}
	}

	/**
	 * Performs the client cleanup hook and terminates a standalone frame.
	 */
	public void exit() {
		shutdownCountdown = STOPPED;
		cleanUpForQuit();
		if (gameFrame != null) {
			try {
				Thread.sleep(1000L);
			} catch (Exception ignored) {
				// Preserved client shutdown behavior.
			}
			try {
				System.exit(0);
			} catch (Throwable ignored) {
				// The original client tolerated a denied System.exit().
			}
		}
	}

	/** Sets the target game-loop frequency used by the original ratio timer. */
	public final void setTargetFps(int fps) {
		cycleDurationMillis = 1000 / fps;
	}

	/**
	 * Requests shutdown and retains the original ten-second fallback if the game
	 * thread fails to observe the request.
	 */
	public final void shutdown() {
		shutdownCountdown = SHUTDOWN_REQUESTED;
		try {
			Thread.sleep(10000L);
		} catch (Exception ignored) {
			// Preserved shutdown fallback behavior.
		}
		if (shutdownCountdown == SHUTDOWN_REQUESTED) {
			exit();
		}
	}

	@Override
	public final void update(Graphics graphics) {
		if (this.graphics == null) {
			this.graphics = graphics;
		}
		clearScreen = true;
	}

	@Override
	public final void paint(Graphics graphics) {
		if (this.graphics == null) {
			this.graphics = graphics;
		}
		clearScreen = true;
	}

	@Override
	public final void mousePressed(MouseEvent event) {
		int x = event.getX();
		int y = event.getY();
		if (gameFrame != null) {
			x -= FRAME_MOUSE_X_OFFSET;
			y -= FRAME_MOUSE_Y_OFFSET;
		}

		idleCycles = 0;
		pendingClickX = x;
		pendingClickY = y;
		pendingClickTime = System.currentTimeMillis();
		if (event.isMetaDown()) {
			pendingClickButton = 2;
			mouseButton = 2;
		} else {
			pendingClickButton = 1;
			mouseButton = 1;
		}
	}

	@Override
	public final void mouseReleased(MouseEvent event) {
		idleCycles = 0;
		mouseButton = 0;
	}

	@Override
	public final void mouseClicked(MouseEvent event) {
	}

	@Override
	public final void mouseEntered(MouseEvent event) {
	}

	@Override
	public final void mouseExited(MouseEvent event) {
		idleCycles = 0;
		mouseX = -1;
		mouseY = -1;
	}

	@Override
	public final void mouseDragged(MouseEvent event) {
		updateMousePosition(event);
	}

	@Override
	public final void mouseMoved(MouseEvent event) {
		updateMousePosition(event);
	}

	private void updateMousePosition(MouseEvent event) {
		int x = event.getX();
		int y = event.getY();
		if (gameFrame != null) {
			x -= FRAME_MOUSE_X_OFFSET;
			y -= FRAME_MOUSE_Y_OFFSET;
		}
		idleCycles = 0;
		mouseX = x;
		mouseY = y;
	}

	@Override
	public final void keyPressed(KeyEvent event) {
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

	@Override
	public final void keyReleased(KeyEvent event) {
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

	@Override
	public final void keyTyped(KeyEvent event) {
	}

	/** Returns the next queued client key code, or {@code -1} when empty. */
	public final int pollKey() {
		int key = -1;
		if (keyQueueWriteIndex != keyQueueReadIndex) {
			key = keyQueue[keyQueueReadIndex];
			keyQueueReadIndex = (keyQueueReadIndex + 1) & 0x7f;
		}
		return key;
	}

	@Override
	public final void focusGained(FocusEvent event) {
		hasFocus = true;
		clearScreen = true;
	}

	@Override
	public final void focusLost(FocusEvent event) {
		hasFocus = false;
		for (int i = 0; i < KEY_BUFFER_SIZE; i++) {
			keyStatus[i] = 0;
		}
	}

	@Override
	public final void windowActivated(WindowEvent event) {
	}

	@Override
	public final void windowClosed(WindowEvent event) {
	}

	@Override
	public final void windowClosing(WindowEvent event) {
		shutdown();
	}

	@Override
	public final void windowDeactivated(WindowEvent event) {
	}

	@Override
	public final void windowDeiconified(WindowEvent event) {
	}

	@Override
	public final void windowIconified(WindowEvent event) {
	}

	@Override
	public final void windowOpened(WindowEvent event) {
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

	/** Returns the top-level AWT component used for input and drawing. */
	public Component getGameComponent() {
		return gameFrame != null ? gameFrame : this;
	}

	/**
	 * Starts a client worker thread with the original start-then-prioritize
	 * ordering.
	 */
	public void startThread(Runnable runnable, int priority) {
		Thread thread = new Thread(runnable);
		thread.start();
		thread.setPriority(priority);
	}

	/** Draws the classic fixed-size loading bar directly through AWT. */
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

	/** Current mouse X used by the asynchronous mouse recorder. */
	public final int getMouseX() {
		return mouseX;
	}

	/** Current mouse Y used by the asynchronous mouse recorder. */
	public final int getMouseY() {
		return mouseY;
	}
}