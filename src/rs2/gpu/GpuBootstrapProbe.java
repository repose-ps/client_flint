package rs2.gpu;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;

/** Executable probe for validating the Phase-1 native OpenGL bootstrap. */
public final class GpuBootstrapProbe {

    private GpuBootstrapProbe() {
    }

    /** Creates a real AWT OpenGL 3.3 surface and verifies the diagnostic triangle. */
    public static void verify() {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("GPU bootstrap validation requires a graphical display.");
        }

        Frame frame = new Frame("Flint GPU Bootstrap Self-Test");
        GpuTestCanvas canvas = new GpuTestCanvas();
        frame.setLayout(new BorderLayout());
        frame.add(canvas, BorderLayout.CENTER);
        frame.setSize(320, 240);
        frame.setVisible(true);
        try {
            canvas.requestValidation();
            canvas.render();
            if (!canvas.initialized()) {
                throw new IllegalStateException("OpenGL context did not initialize.");
            }
            if (!canvas.validationPassed()) {
                throw new IllegalStateException("OpenGL test triangle did not produce the expected framebuffer output.");
            }
        } finally {
            canvas.closeGl();
            frame.remove(canvas);
            frame.dispose();
        }
    }
}
