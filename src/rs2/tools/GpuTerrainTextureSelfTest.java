package rs2.tools;

import rs2.gpu.GpuTerrainTextureProbe;

/** Native OpenGL validation for Phase-4 terrain texture residency and sampling. */
public final class GpuTerrainTextureSelfTest {

    private GpuTerrainTextureSelfTest() {
    }

    public static void main(String[] args) {
        GpuTerrainTextureProbe.verify();
        System.out.println("GpuTerrainTextureSelfTest: PASS (OpenGL 3.3 texture-array terrain sampling)");
    }
}
