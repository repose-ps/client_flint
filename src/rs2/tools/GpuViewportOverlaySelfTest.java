package rs2.tools;

import rs2.gpu.GpuViewportOverlayProbe;

/** Native OpenGL smoke test for legacy viewport UI compositing over the GPU world. */
public final class GpuViewportOverlaySelfTest {

    private GpuViewportOverlaySelfTest() {
    }

    public static void main(String[] args) {
        GpuViewportOverlayProbe.verify();
        System.out.println("GpuViewportOverlaySelfTest: PASS (opaque + keyed-transparent software viewport overlays)");
    }
}
