package rs2.gpu;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;

import rs2.game.render.WorldRenderFrame;
import rs2.media.Rasterizer3D;
import rs2.media.sprite.IndexedImage;
import rs2.scene.Scene;

/** Native OpenGL probe for real revision-377 terrain texture sampling. */
public final class GpuTerrainTextureProbe {

    private static final int TEST_SIZE = 16;
    private static final int TILE_SIZE = 128;
    private static final int TEST_TEXTURE = 0;

    private GpuTerrainTextureProbe() {
    }

    public static void verify() {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("GPU terrain texture validation requires a graphical display.");
        }

        IndexedImage previous = Rasterizer3D.textures[TEST_TEXTURE];
        try {
            installGreenTestTexture();
            Scene scene = createTestScene();
            WorldRenderFrame renderFrame = new WorldRenderFrame(scene, 8 * TILE_SIZE + 64, 4 * TILE_SIZE + 64, -500,
                    0, 0, 256, 0, 0, 0, 0, 320, 240);

            Frame frame = new Frame("Flint GPU Terrain Texture Self-Test");
            GpuSceneCanvas canvas = new GpuSceneCanvas();
            frame.setLayout(new BorderLayout());
            frame.add(canvas, BorderLayout.CENTER);
            frame.setSize(320, 240);
            frame.setVisible(true);
            try {
                canvas.setFrameData(renderFrame);
                canvas.requestGreenTextureValidation();
                canvas.render();
                if (!canvas.initialized()) {
                    throw new IllegalStateException("OpenGL context did not initialize.");
                }
                if (!canvas.validationPassed()) {
                    throw new IllegalStateException("GPU terrain texture draw did not sample the resident texture.");
                }
            } finally {
                frame.remove(canvas);
                frame.dispose();
            }
        } finally {
            Rasterizer3D.textures[TEST_TEXTURE] = previous;
            Rasterizer3D.markTextureChanged(TEST_TEXTURE);
        }
    }

    private static void installGreenTestTexture() {
        IndexedImage texture = new IndexedImage(64, 64, new int[] { 0, 0x00ff20 });
        for (int pixel = 0; pixel < texture.pixels.length; pixel++) {
            texture.pixels[pixel] = 1;
        }
        Rasterizer3D.textures[TEST_TEXTURE] = texture;
        Rasterizer3D.markTextureChanged(TEST_TEXTURE);
        Rasterizer3D.setBrightness(1.0D);
    }

    private static Scene createTestScene() {
        int[][][] heights = new int[1][TEST_SIZE + 1][TEST_SIZE + 1];
        Scene scene = new Scene(heights, 1, TEST_SIZE, TEST_SIZE);
        for (int x = 0; x < TEST_SIZE; x++) {
            for (int y = 0; y < TEST_SIZE; y++) {
                scene.addTile(0, x, y, 1, 0, TEST_TEXTURE, 0, 0, 0, 0,
                        0, 0, 0, 0, 8, 8, 8, 8, 0, 0);
            }
        }
        return scene;
    }
}
