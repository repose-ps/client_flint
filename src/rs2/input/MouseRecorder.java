package rs2.input;

import rs2.GameShell;

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

	public volatile boolean running = true;
	private volatile Thread workerThread;
	public final int[] yCoordinates = new int[500];
	public final Object lock = new Object();
	private final GameShell source;
	public int sampleCount;
	public final int[] xCoordinates = new int[500];

	public MouseRecorder(GameShell source) {
		this.source = source;
	}

	/** Starts the recorder on its owned daemon thread. */
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