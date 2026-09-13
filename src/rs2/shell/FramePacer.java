package rs2.shell;

import java.util.concurrent.locks.LockSupport;

/** Hybrid sleep/spin frame pacer for high-refresh presentation. */
final class FramePacer {

    /** Small final margin after the predicted park overshoot. */
    private static final long PARK_MARGIN_NANOS = 100_000L;
    /** Do not burn more than this much CPU waiting for one deadline. */
    private static final long MAX_PARK_GUARD_NANOS = 2_000_000L;
    /** Initial guard before enough park samples have been observed. */
    private static long parkGuardNanos = 500_000L;

    private FramePacer() {
    }

    /** Waits until the requested monotonic deadline without clearing interrupts. */
    static void waitUntil(long deadlineNanos) {
        while (true) {
            long now = System.nanoTime();
            long remaining = deadlineNanos - now;
            if (remaining <= 0L || Thread.currentThread().isInterrupted()) {
                return;
            }

            long guard = parkGuardNanos;
            if (remaining > guard) {
                long requestedPark = remaining - guard;
                long beforePark = now;
                LockSupport.parkNanos(requestedPark);
                long afterPark = System.nanoTime();
                long overshoot = Math.max(0L, afterPark - beforePark - requestedPark);
                long targetGuard = Math.min(MAX_PARK_GUARD_NANOS, overshoot + PARK_MARGIN_NANOS);
                // Smooth scheduler jitter instead of letting one slow wake-up make every
                // following frame spend milliseconds in the busy-wait tail.
                parkGuardNanos += (targetGuard - parkGuardNanos) >> 3;
            } else {
                Thread.onSpinWait();
            }
        }
    }

    /**
     * Advances a periodic deadline while allowing one missed slot to catch up.
     *
     * <p>A frame that finishes only slightly beyond the following presentation
     * deadline should not permanently lower the measured frame rate by skipping
     * that slot. Keep the original phase in that case so the next loop can render
     * immediately. Large stalls still rebase the schedule to avoid a render burst.</p>
     */
    static long advanceDeadline(long deadlineNanos, long nowNanos, long stepNanos) {
        if (stepNanos <= 0L) {
            return nowNanos;
        }
        long nextDeadline = deadlineNanos + stepNanos;
        if (nowNanos - nextDeadline >= stepNanos) {
            return nowNanos + stepNanos;
        }
        return nextDeadline;
    }
}
