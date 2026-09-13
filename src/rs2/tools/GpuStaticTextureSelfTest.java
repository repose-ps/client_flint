package rs2.tools;

import rs2.gpu.GpuStaticTextureProbe;

/** Native OpenGL validation for Phase-4 projective static-model texture sampling. */
public final class GpuStaticTextureSelfTest {

    private GpuStaticTextureSelfTest() {
    }

    public static void main(String[] args) {
        GpuStaticTextureProbe.verify();
        System.out.println("GpuStaticTextureSelfTest: PASS (OpenGL 3.3 projective static-model texture sampling)");
    }
}
