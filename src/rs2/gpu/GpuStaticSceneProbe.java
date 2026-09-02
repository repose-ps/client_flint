package rs2.gpu;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;

import rs2.game.render.WorldRenderFrame;
import rs2.media.Rasterizer3D;
import rs2.media.model.Model;
import rs2.scene.Scene;

/** Executable native probe for validating the Phase-3 static model render path. */
public final class GpuStaticSceneProbe {

	private static final int TEST_SIZE = 16;
	private static final int TILE_SIZE = 128;

	private GpuStaticSceneProbe() {
	}

	/** Renders a static Model with no terrain so framebuffer output proves the model path. */
	public static void verify() {
		if (GraphicsEnvironment.isHeadless()) {
			throw new IllegalStateException("GPU static-scene validation requires a graphical display.");
		}

		Rasterizer3D.setBrightness(0.8D);
		Scene scene = createTestScene();
		WorldRenderFrame renderFrame = new WorldRenderFrame(scene, 8 * TILE_SIZE + 64, 4 * TILE_SIZE + 64, -500, 0,
				0, 256, 0, 0, 0, 0, 320, 240);

		Frame frame = new Frame("Flint GPU Static Scene Self-Test");
		GpuSceneCanvas canvas = new GpuSceneCanvas();
		frame.setLayout(new BorderLayout());
		frame.add(canvas, BorderLayout.CENTER);
		frame.setSize(320, 240);
		frame.setVisible(true);
		try {
			canvas.setFrameData(renderFrame);
			canvas.requestValidation();
			canvas.render();
			if (!canvas.initialized()) {
				throw new IllegalStateException("OpenGL context did not initialize.");
			}
			if (!canvas.validationPassed()) {
				throw new IllegalStateException("GPU static model draw did not produce framebuffer output.");
			}
		} finally {
			frame.remove(canvas);
			frame.dispose();
		}
	}

	private static Scene createTestScene() {
		Scene scene = new Scene(new int[1][TEST_SIZE + 1][TEST_SIZE + 1], 1, TEST_SIZE, TEST_SIZE);
		Model model = new Model(0, new Model[0]);
		model.vertexCount = 3;
		model.verticesX = new int[] { -160, 160, 0 };
		model.verticesY = new int[] { 0, 0, -260 };
		model.verticesZ = new int[] { 0, 0, 0 };
		model.triangleCount = 1;
		model.triangleVertexA = new int[] { 0 };
		model.triangleVertexB = new int[] { 1 };
		model.triangleVertexC = new int[] { 2 };
		model.triangleShadeA = new int[] { 0x3200 };
		model.triangleShadeB = new int[] { 0x3a20 };
		model.triangleShadeC = new int[] { 0x4220 };
		scene.addFloorDecoration(0, 8, 8, 0, 1, (byte) 0, model);
		return scene;
	}
}
