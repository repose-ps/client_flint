package rs2.gpu;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;
import java.util.Arrays;

import rs2.game.render.SoftwareViewportOverlay;
import rs2.game.render.WorldRenderFrame;
import rs2.scene.Scene;

/** Native OpenGL probe for the legacy-software-to-GPU viewport overlay bridge. */
public final class GpuViewportOverlayProbe {

    private GpuViewportOverlayProbe() {
    }

    /** Draws an opaque software rectangle over an otherwise empty GPU frame. */
    public static void verify() {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("GPU viewport-overlay validation requires a graphical display.");
        }

        Scene scene = new Scene(new int[1][2][2], 1, 1, 1);
        WorldRenderFrame renderFrame = new WorldRenderFrame(scene, 64, 64, -100, 0, 0, 256, 0, 0, 0, 0, 320, 240);
        int overlayWidth = 96;
        int overlayHeight = 48;
        int[] pixels = new int[overlayWidth * overlayHeight];
        Arrays.fill(pixels, 0x5d5447);
        SoftwareViewportOverlay overlay = new SoftwareViewportOverlay(pixels, overlayWidth, 0, 0, overlayWidth,
                overlayHeight);

        Frame frame = new Frame("Flint GPU Viewport Overlay Self-Test");
        GpuSceneCanvas canvas = new GpuSceneCanvas();
        frame.setLayout(new BorderLayout());
        frame.add(canvas, BorderLayout.CENTER);
        frame.setSize(320, 240);
        frame.setVisible(true);
        try {
            canvas.setFrameData(renderFrame);
            canvas.setViewportOverlay(overlay);
            canvas.requestValidation();
            canvas.render();
            if (!canvas.initialized()) {
                throw new IllegalStateException("OpenGL context did not initialize.");
            }
            if (!canvas.validationPassed()) {
                throw new IllegalStateException("Opaque GPU viewport overlay did not produce framebuffer output.");
            }

            int transparentKey = 0x01000000;
            Arrays.fill(pixels, transparentKey);
            for (int y = 12; y < 36; y++) {
                Arrays.fill(pixels, y * overlayWidth + 24, y * overlayWidth + 72, 0xffffff);
            }
            canvas.setViewportOverlay(new SoftwareViewportOverlay(pixels, overlayWidth, 0, 0, overlayWidth,
                    overlayHeight, transparentKey));
            canvas.requestValidation();
            canvas.render();
            if (!canvas.validationPassed()) {
                throw new IllegalStateException("Transparent GPU viewport overlay did not produce framebuffer output.");
            }
        } finally {
            frame.remove(canvas);
            frame.dispose();
        }
    }
}
