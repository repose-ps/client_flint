package rs2.tools;

import rs2.gpu.GpuTerrainProbe;

/** Native OpenGL smoke test for GPU renderer Phase 2 terrain rendering. */
public final class GpuTerrainSelfTest {

	private GpuTerrainSelfTest() {
	}

	public static void main(String[] args) {
		GpuTerrainProbe.verify();
		System.out.println("GpuTerrainSelfTest: PASS (OpenGL 3.3 full terrain upload + depth-rendered draw)");
	}
}
