package rs2.tools;

import rs2.gpu.GpuBootstrapProbe;

/** Native OpenGL smoke test for GPU renderer Phase 1. */
public final class GpuBootstrapSelfTest {

    private GpuBootstrapSelfTest() {
    }

    public static void main(String[] args) {
        GpuBootstrapProbe.verify();
        System.out.println("GpuBootstrapSelfTest: PASS (OpenGL 3.3 context + test triangle)");
    }
}
