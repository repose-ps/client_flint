package rs2.gpu;

import java.awt.BorderLayout;
import java.awt.Frame;
import java.awt.GraphicsEnvironment;

import rs2.game.render.WorldRenderFrame;
import rs2.media.Rasterizer3D;
import rs2.media.model.Model;
import rs2.media.sprite.IndexedImage;
import rs2.scene.Scene;

/** Native OpenGL probe for revision-377 projective texture mapping on static Models. */
public final class GpuStaticTextureProbe {

    private static final int TEST_SIZE = 16;
    private static final int TILE_SIZE = 128;
    private static final int TEST_TEXTURE = 0;

    private GpuStaticTextureProbe() {
    }

    public static void verify() {
        if (GraphicsEnvironment.isHeadless()) {
            throw new IllegalStateException("GPU static texture validation requires a graphical display.");
        }

        IndexedImage previous = Rasterizer3D.textures[TEST_TEXTURE];
        try {
            installPatternTestTexture();
            Scene scene = createTestScene();
            // Keep the complete distinct face/reference-triangle fixture inside the
            // native framebuffer. The earlier camera clipped the high-V corner above
            // the viewport, making the blue half of the validation texture impossible
            // to sample even when mapping was correct.
            WorldRenderFrame renderFrame = new WorldRenderFrame(scene, 8 * TILE_SIZE + 64, 5 * TILE_SIZE, -650,
                    0, 0, 256, 0, 0, 0, 0, 320, 240);

            Frame frame = new Frame("Flint GPU Static Texture Self-Test");
            GpuSceneCanvas canvas = new GpuSceneCanvas();
            frame.setLayout(new BorderLayout());
            frame.add(canvas, BorderLayout.CENTER);
            frame.setSize(320, 240);
            frame.setVisible(true);
            try {
                canvas.setFrameData(renderFrame);
                canvas.requestStaticTexturePatternValidation();
                canvas.render();
                if (!canvas.initialized()) {
                    throw new IllegalStateException("OpenGL context did not initialize.");
                }
                if (!canvas.validationPassed()) {
                    throw new IllegalStateException(
                            "GPU static textured model draw did not vary sampling across the projective mapping.");
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

    private static void installPatternTestTexture() {
        IndexedImage texture = new IndexedImage(64, 64, new int[] { 0, 0xff1000, 0x00ff20, 0x1020ff });
        for (int y = 0; y < texture.height; y++) {
            for (int x = 0; x < texture.width; x++) {
                int paletteIndex;
                if (y < texture.height / 2) {
                    paletteIndex = x < texture.width / 2 ? 1 : 2;
                } else {
                    paletteIndex = 3;
                }
                texture.pixels[x + y * texture.width] = (byte) paletteIndex;
            }
        }
        Rasterizer3D.textures[TEST_TEXTURE] = texture;
        Rasterizer3D.markTextureChanged(TEST_TEXTURE);
        Rasterizer3D.setBrightness(1.0D);
    }

    private static Scene createTestScene() {
        Scene scene = new Scene(new int[1][TEST_SIZE + 1][TEST_SIZE + 1], 1, TEST_SIZE, TEST_SIZE);
        Model model = new Model(0, new Model[0]);
        // Vertices 0..2 are the visible face. Vertices 3..5 are a larger,
        // distinct revision-native texture-reference triangle. The face resolves
        // to roughly (.15,.15), (.85,.15), (.15,.85), forcing the native probe to validate camera-space projective
        // reference-triangle mapping rather than the trivial face == texture-triangle case.
        model.vertexCount = 6;
        model.verticesX = new int[] { -140, 140, -140, -200, 200, -200 };
        model.verticesY = new int[] { -60, -60, -340, 0, 0, -400 };
        model.verticesZ = new int[] { 0, 0, 0, 0, 0, 0 };
        model.triangleCount = 1;
        model.triangleVertexA = new int[] { 0 };
        model.triangleVertexB = new int[] { 1 };
        model.triangleVertexC = new int[] { 2 };
        model.triangleShadeA = new int[] { 48 };
        model.triangleShadeB = new int[] { 64 };
        model.triangleShadeC = new int[] { 80 };
        model.triangleDrawType = new int[] { 2 };
        model.triangleColors = new int[] { TEST_TEXTURE };
        model.texturedTriangleCount = 1;
        model.texturedTriangleA = new int[] { 3 };
        model.texturedTriangleB = new int[] { 4 };
        model.texturedTriangleC = new int[] { 5 };
        scene.addFloorDecoration(0, 8, 8, 0, 1, (byte) 0, model);
        return scene;
    }
}
