package rs2.shell;

import java.util.Locale;

/** Startup-only frame-timing configuration used until in-game settings exist. */
public final class FrameTimingConfig {

    /** JVM property controlling presentation rate independently from the 50 Hz logic loop. */
    public static final String RENDER_FPS_PROPERTY = "flint.renderFps";
    /** GPU default chosen to improve high-refresh presentation without running uncapped. */
    public static final int DEFAULT_GPU_RENDER_FPS = 120;
    /** Software rendering keeps the legacy presentation rate unless explicitly overridden. */
    public static final int DEFAULT_SOFTWARE_RENDER_FPS = 50;

    private FrameTimingConfig() {
    }

    /**
     * Reads {@value #RENDER_FPS_PROPERTY}. Accepted values are a positive integer,
     * {@code legacy} (50), or {@code unlimited}/{@code uncapped} (0).
     */
    public static int configuredRenderFps(int defaultFps) {
        if (defaultFps <= 0) {
            throw new IllegalArgumentException("Default render FPS must be positive: " + defaultFps);
        }
        String raw = System.getProperty(RENDER_FPS_PROPERTY);
        if (raw == null || raw.isBlank()) {
            return defaultFps;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (value.equals("unlimited") || value.equals("uncapped")) {
            return 0;
        }
        if (value.equals("legacy")) {
            return 50;
        }
        try {
            int fps = Integer.parseInt(value);
            if (fps <= 0) {
                throw new IllegalArgumentException("-D" + RENDER_FPS_PROPERTY
                        + " must be a positive integer, legacy, unlimited, or uncapped: " + raw);
            }
            return fps;
        } catch (NumberFormatException exception) {
            throw new IllegalArgumentException("Invalid -D" + RENDER_FPS_PROPERTY + "='" + raw
                    + "'. Expected a positive integer, legacy, unlimited, or uncapped.", exception);
        }
    }

    /** Human-readable rendering rate for startup diagnostics. */
    public static String describe(int fps) {
        return fps == 0 ? "unlimited" : fps + " FPS";
    }
}
