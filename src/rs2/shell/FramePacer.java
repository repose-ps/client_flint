package rs2.shell;

import java.util.concurrent.locks.LockSupport;

/** Hybrid sleep/spin frame pacer for high-refresh presentation. */
final class FramePacer {

    /** Final busy-wait window used to avoid the common Windows park overshoot. */
    private static final long SPIN_WINDOW_NANOS = 250_000L;

    private FramePacer() {
    }

    /** Waits until the requested monotonic deadline without clearing interrupts. */
    static void waitUntil(long deadlineNanos) {
        while (true) {
            long remaining = deadlineNanos - System.nanoTime();
            if (remaining <= 0L || Thread.currentThread().isInterrupted()) {
                return;
            }
            if (remaining > SPIN_WINDOW_NANOS) {
                LockSupport.parkNanos(remaining - SPIN_WINDOW_NANOS);
            } else {
                Thread.onSpinWait();
            }
        }
    }

    /** Advances a periodic deadline while preventing long stalls from causing bursts. */
    static long advanceDeadline(long deadlineNanos, long nowNanos, long stepNanos) {
        if (stepNanos <= 0L) {
            return nowNanos;
        }
        if (nowNanos - deadlineNanos > stepNanos * 4L) {
            return nowNanos + stepNanos;
        }
        do {
            deadlineNanos += stepNanos;
        } while (deadlineNanos <= nowNanos);
        return deadlineNanos;
    }
}
