package rs2.gpu;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;

import rs2.game.render.WorldRenderFrame;
import rs2.media.Rasterizer3D;
import rs2.scene.Scene;

/** Executable native probe for validating the Phase-2 terrain render path. */
public final class GpuTerrainProbe {

	private static final int TEST_SIZE = 16;
	private static final int TILE_SIZE = 128;

	private GpuTerrainProbe() {
	}

	/** Creates a synthetic terrain scene and verifies that OpenGL rasterizes it. */
	public static void verify() {
		if (GraphicsEnvironment.isHeadless()) {
			throw new IllegalStateException("GPU terrain validation requires a graphical display.");
		}

		Rasterizer3D.setBrightness(0.8D);
		Scene scene = createTestScene();
		WorldRenderFrame renderFrame = new WorldRenderFrame(scene, 8 * TILE_SIZE + 64, 4 * TILE_SIZE + 64, -500, 0,
				0, 256, 0, 0, 0, 0, 320, 240);

		Frame frame = new Frame("Flint GPU Terrain Self-Test");
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
				throw new IllegalStateException("GPU terrain draw did not produce framebuffer output.");
			}
		} finally {
			frame.remove(canvas);
			frame.dispose();
		}
	}

	private static Scene createTestScene() {
		int[][][] heights = new int[1][TEST_SIZE + 1][TEST_SIZE + 1];
		for (int x = 0; x <= TEST_SIZE; x++) {
			for (int y = 0; y <= TEST_SIZE; y++) {
				heights[0][x][y] = -((x - 8) * (x - 8) + (y - 8) * (y - 8));
			}
		}
		Scene scene = new Scene(heights, 1, TEST_SIZE, TEST_SIZE);
		for (int x = 0; x < TEST_SIZE; x++) {
			for (int y = 0; y < TEST_SIZE; y++) {
				int colour = 0x2800 + ((x * 5 + y * 3) & 0x7f);
				scene.addTile(0, x, y, 0, 0, -1, heights[0][x][y], heights[0][x + 1][y],
						heights[0][x + 1][y + 1], heights[0][x][y + 1], colour, colour + 4, colour + 8,
						colour + 12, 0, 0, 0, 0, 0, 0);
			}
		}
		return scene;
	}
}
