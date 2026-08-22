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

	public boolean running = true;
	public final int[] yCoordinates = new int[500];
	public final Object lock = new Object();
	private final GameShell source;
	public int sampleCount;
	public final int[] xCoordinates = new int[500];

	public MouseRecorder(GameShell source) {
		this.source = source;
	}

	@Override
	public void run() {
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
			} catch (Exception ignored) {
				// The original recorder ignores interruption and all other sleep exceptions.
			}
		}
	}
}