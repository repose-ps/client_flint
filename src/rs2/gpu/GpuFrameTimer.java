package rs2.gpu;

import static org.lwjgl.opengl.GL33C.GL_QUERY_RESULT;
import static org.lwjgl.opengl.GL33C.GL_QUERY_RESULT_AVAILABLE;
import static org.lwjgl.opengl.GL33C.GL_TIMESTAMP;
import static org.lwjgl.opengl.GL33C.glGenQueries;
import static org.lwjgl.opengl.GL33C.glGetQueryObjecti;
import static org.lwjgl.opengl.GL33C.glGetQueryObjecti64;
import static org.lwjgl.opengl.GL33C.glQueryCounter;

/**
 * Non-blocking timestamp-query ring used only by optional GPU diagnostics.
 * Query results are consumed several frames later so profiling never forces a
 * CPU/GPU synchronization point in the render path.
 */
final class GpuFrameTimer {

    private static final int SLOT_COUNT = 8;
    private static final int START = 0;
    private static final int CLEAR_DONE = 1;
    private static final int TERRAIN_DONE = 2;
    private static final int STATIC_DONE = 3;
    private static final int MARKER_COUNT = 4;

    private final int[][] queries = new int[SLOT_COUNT][MARKER_COUNT];
    private final boolean[] pending = new boolean[SLOT_COUNT];
    private int writeSlot;
    private int activeSlot = -1;
    private long completedFrames;
    private long clearNanos;
    private long terrainNanos;
    private long staticNanos;
    private long totalNanos;
    private long droppedFrames;

    void initialize() {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            for (int marker = 0; marker < MARKER_COUNT; marker++) {
                queries[slot][marker] = glGenQueries();
            }
        }
    }

    void beginFrame() {
        collectReady();
        if (pending[writeSlot]) {
            activeSlot = -1;
            droppedFrames++;
            return;
        }
        activeSlot = writeSlot;
        glQueryCounter(queries[activeSlot][START], GL_TIMESTAMP);
    }

    void markClearDone() {
        mark(CLEAR_DONE);
    }

    void markTerrainDone() {
        mark(TERRAIN_DONE);
    }

    void markStaticDone() {
        mark(STATIC_DONE);
    }

    private void mark(int marker) {
        if (activeSlot >= 0) {
            glQueryCounter(queries[activeSlot][marker], GL_TIMESTAMP);
        }
    }

    void endFrame() {
        if (activeSlot < 0) {
            return;
        }
        pending[activeSlot] = true;
        writeSlot = (activeSlot + 1) % SLOT_COUNT;
        activeSlot = -1;
        collectReady();
    }

    private void collectReady() {
        for (int slot = 0; slot < SLOT_COUNT; slot++) {
            if (!pending[slot]
                    || glGetQueryObjecti(queries[slot][STATIC_DONE], GL_QUERY_RESULT_AVAILABLE) == 0) {
                continue;
            }
            long started = glGetQueryObjecti64(queries[slot][START], GL_QUERY_RESULT);
            long cleared = glGetQueryObjecti64(queries[slot][CLEAR_DONE], GL_QUERY_RESULT);
            long terrainDone = glGetQueryObjecti64(queries[slot][TERRAIN_DONE], GL_QUERY_RESULT);
            long staticDone = glGetQueryObjecti64(queries[slot][STATIC_DONE], GL_QUERY_RESULT);
            if (cleared >= started && terrainDone >= cleared && staticDone >= terrainDone) {
                completedFrames++;
                clearNanos += cleared - started;
                terrainNanos += terrainDone - cleared;
                staticNanos += staticDone - terrainDone;
                totalNanos += staticDone - started;
            }
            pending[slot] = false;
        }
    }

    Snapshot snapshotAndReset() {
        collectReady();
        Snapshot snapshot = new Snapshot(completedFrames, clearNanos, terrainNanos, staticNanos, totalNanos,
                droppedFrames);
        completedFrames = 0L;
        clearNanos = 0L;
        terrainNanos = 0L;
        staticNanos = 0L;
        totalNanos = 0L;
        droppedFrames = 0L;
        return snapshot;
    }

    record Snapshot(long frames, long clearNanos, long terrainNanos, long staticNanos, long totalNanos,
            long droppedFrames) {
        double averageClearMs() {
            return average(clearNanos);
        }

        double averageTerrainMs() {
            return average(terrainNanos);
        }

        double averageStaticMs() {
            return average(staticNanos);
        }

        double averageTotalMs() {
            return average(totalNanos);
        }

        private double average(long nanos) {
            return frames == 0L ? 0.0 : nanos / 1_000_000.0 / frames;
        }
    }
}
