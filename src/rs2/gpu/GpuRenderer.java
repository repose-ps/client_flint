package rs2.gpu;

import rs2.game.render.RendererBackend;
import rs2.game.render.WorldRenderFrame;
import rs2.game.render.WorldRenderer;
import rs2.shell.GameFrame;

/** Native OpenGL renderer bootstrap used by Phase 1. */
public final class GpuRenderer implements WorldRenderer, AutoCloseable {

    private GpuTestCanvas canvas;
    private GameFrame frame;
    private int lastX = Integer.MIN_VALUE;
    private int lastY = Integer.MIN_VALUE;
    private int lastWidth = -1;
    private int lastHeight = -1;

    @Override
    public RendererBackend backend() {
        return RendererBackend.GPU;
    }

    /** Attaches the native surface to the already-created AWT host frame. */
    public void attach(GameFrame frame) {
        if (this.frame != null) {
            throw new IllegalStateException("GPU renderer is already attached to a frame.");
        }
        this.frame = frame;
        this.canvas = new GpuTestCanvas();
        frame.installRenderOverlay(canvas);
    }

    @Override
    public void render(WorldRenderFrame frameData) {
        if (canvas == null || frame == null) {
            throw new IllegalStateException("GPU renderer has no AWT rendering surface.");
        }

        updateBounds(frameData);
        canvas.render();
    }

    /** Hides the native surface outside logged-in world rendering. */
    public void setSurfaceActive(boolean active) {
        if (canvas != null && !active) {
            canvas.setVisible(false);
        }
    }

    private void updateBounds(WorldRenderFrame frameData) {
        int x = frameData.viewportX();
        int y = frameData.viewportY();
        int width = frameData.viewportWidth();
        int height = frameData.viewportHeight();
        if (x == lastX && y == lastY && width == lastWidth && height == lastHeight) {
            return;
        }
        lastX = x;
        lastY = y;
        lastWidth = width;
        lastHeight = height;
        frame.positionRenderOverlay(canvas, x, y, width, height);
    }

    @Override
    public void close() {
        if (canvas == null) {
            return;
        }
        canvas.closeGl();
        if (frame != null) {
            frame.removeRenderOverlay(canvas);
        }
        canvas = null;
        frame = null;
    }
}
