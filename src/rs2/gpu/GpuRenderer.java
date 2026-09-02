package rs2.gpu;

import rs2.game.render.RendererBackend;
import rs2.game.render.WorldRenderFrame;
import rs2.game.render.WorldRenderer;
import rs2.shell.GameFrame;

/** Native OpenGL world renderer backed by the complete loaded terrain mesh. */
public final class GpuRenderer implements WorldRenderer, AutoCloseable {

    private GpuSceneCanvas canvas;
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
        this.canvas = new GpuSceneCanvas();
        frame.installRenderOverlay(canvas);
    }

    @Override
    public void render(WorldRenderFrame frameData) {
        if (canvas == null || frame == null) {
            throw new IllegalStateException("GPU renderer has no AWT rendering surface.");
        }

        updateBounds(frameData);
        canvas.setFrameData(frameData);
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
        /*
         * lwjgl3-awt 0.2.4 caches the JAWT drawing surface on the thread which
         * renders the first frame, but AWT removes child components on the EDT.
         * Freeing that cached surface from the EDT after Flint's game/render
         * thread has exited can crash the JVM (LWJGLX/lwjgl3-awt #121).
         *
         * This renderer is only closed during standalone process shutdown, so do
         * not detach the native child canvas here. System.exit follows immediately
         * after client cleanup and the operating system will reclaim the context.
         * A future lwjgl3-awt release containing #124 can restore explicit detach.
         */
        canvas.setVisible(false);
        canvas = null;
        frame = null;
    }
}
