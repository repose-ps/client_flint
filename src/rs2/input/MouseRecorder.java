package rs2.input;

import rs2.shell.GameShell;

/**
 * Samples the game shell's mouse position every 50 ms for movement telemetry.
 *
 * <p>
 * The client drains these samples while constructing its compressed mouse
 * movement packet. The fixed 500-sample capacity and silent dropping of new
 * samples while full are revision-377 behavior.
 * </p>
 */
public final class MouseRecorder implements Runnable {

	/** Whether running is enabled or active. */
	public volatile boolean running = true;
	/** Stores the current worker thread. */
	private volatile Thread workerThread;
	/** Stores Y coordinates values. */
	public final int[] yCoordinates = new int[500];
	/**
	 * Lock.
	 *
	 */
	public final Object lock = new Object();
	/** Stores the current source. */
	private final GameShell source;
	/** Stores the current sample count. */
	public int sampleCount;
	/** Stores X coordinates values. */
	public final int[] xCoordinates = new int[500];

	/**
	 * Creates a new mouse recorder.
	 *
	 * @param source the source
	 */
	public MouseRecorder(GameShell source) {
		this.source = source;
	}

	/**
	 * Starts the recorder on its owned daemon thread.
	 * 
	 * @param priority the request priority
	 */
	public synchronized void start(int priority) {
		if (workerThread != null && workerThread.isAlive()) {
			return;
		}
		running = true;
		Thread thread = new Thread(this, "rs2-mouse-recorder");
		thread.setDaemon(true);
		workerThread = thread;
		thread.start();
		thread.setPriority(priority);
	}

	@Override
	public void run() {
		Thread current = Thread.currentThread();
		workerThread = current;
		try {
			while (running) {
				synchronized (lock) {
					if (sampleCount < 500) {
						xCoordinates[sampleCount] = source.getMouseX();
						yCoordinates[sampleCount] = source.getMouseY();
						sampleCount++;
					}
				}
				try {
					Thread.sleep(50L);
				} catch (InterruptedException exception) {
					if (!running) {
						break;
					}
				}
			}
		} finally {
			if (workerThread == current) {
				workerThread = null;
			}
		}
	}

	/** Stops the recorder and waits until its worker has terminated. */
	public void stop() {
		running = false;
		Thread thread = workerThread;
		if (thread != null && thread != Thread.currentThread()) {
			thread.interrupt();
			boolean interrupted = false;
			for (;;) {
				try {
					thread.join();
					break;
				} catch (InterruptedException exception) {
					interrupted = true;
				}
			}
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
	}
}
