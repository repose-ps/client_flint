package rs2.tools;

import rs2.gpu.GpuStaticSceneProbe;

/** Native OpenGL smoke test for GPU renderer Phase 3 static scene geometry. */
public final class GpuStaticSceneSelfTest {

	private GpuStaticSceneSelfTest() {
	}

	public static void main(String[] args) {
		GpuStaticSceneProbe.verify();
		System.out.println("GpuStaticSceneSelfTest: PASS (OpenGL 3.3 static model upload + depth-rendered draw)");
	}
}
