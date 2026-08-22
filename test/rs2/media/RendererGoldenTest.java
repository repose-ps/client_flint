package rs2.media;

import java.io.RandomAccessFile;
import java.lang.reflect.Field;
import java.nio.file.Path;
import java.util.Arrays;

import rs2.cache.Archive;
import rs2.cache.CacheIndex;
import rs2.cache.media.IndexedImage;
import rs2.media.renderable.Model;
import rs2.scene.Scene;
import rs2.scene.tile.ComplexTile;
import rs2.scene.tile.GenericTile;

/** Pixel-golden coverage for the revision-377 software renderer. */
public final class RendererGoldenTest {

    private static final long FNV_OFFSET = 0xcbf29ce484222325L;
    private static final long FNV_PRIME = 0x100000001b3L;
    private static int checks;
    private static int[] pixels;

    private RendererGoldenTest() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 1) {
            throw new IllegalArgumentException("Usage: RendererGoldenTest <revision-377-rscache-directory>");
        }

        testFlatTriangleRasterization();
        testGouraudTriangleRasterization();
        testModelProjectionAndRasterization();
        testPlainSceneTileRasterization();
        testShapedSceneTileRasterization();
        testRealCacheTextureRasterization(Path.of(args[0]));

        System.out.println("RendererGoldenTest: PASS (" + checks + " checks)");
    }

    private static void testFlatTriangleRasterization() {
        int background = 0x102030;
        initializeRaster(96, 72, background);

        Rasterizer3D.drawFlatTriangle(5, 57, 19, 8, 82, 45, 0x3366cc);
        Rasterizer3D.restrictEdges = true;
        Rasterizer3D.drawFlatTriangle(-8, 35, 67, -15, 35, 105, 0xcc4411);
        Rasterizer3D.alpha = 96;
        Rasterizer3D.drawFlatTriangle(12, 62, 42, 25, 70, 3, 0x22cc55);

        check(pixelHash() == 0x07692e39a4aa5098L, "flat triangle pixel hash");
        check(changedPixels(background) == 1946, "flat triangle changed-pixel count");
        check(pixels[20 * 96 + 30] == 0x28a581, "flat triangle alpha sample");
        check(pixels[40 * 96 + 40] == 0x1b8b47, "flat triangle overlap sample");
        check(pixels[60 * 96 + 20] == background, "flat triangle untouched sample");
    }

    private static void testGouraudTriangleRasterization() {
        int background = 0x07111b;
        initializeRaster(96, 72, background);

        Rasterizer3D.drawGouraudTriangle(3, 65, 20, 12, 88, 40, 0x1200, 0x5a00, 0x3200);
        Rasterizer3D.restrictEdges = true;
        Rasterizer3D.gouraudBlockShading = false;
        Rasterizer3D.drawGouraudTriangle(-12, 48, 70, -20, 55, 110, 0x0800, 0x6200, 0x4000);
        Rasterizer3D.alpha = 80;
        Rasterizer3D.drawGouraudTriangle(10, 60, 38, 18, 74, 2, 0x2200, 0x5200, 0x7000);

        check(pixelHash() == 0x5fac19f5518c0045L, "Gouraud triangle pixel hash");
        check(changedPixels(background) == 1807, "Gouraud triangle changed-pixel count");
        check(pixels[20 * 96 + 30] == background, "Gouraud triangle clipping sample");
        check(pixels[40 * 96 + 40] == 0x4f26ad, "Gouraud triangle interpolation sample");
        check(pixels[60 * 96 + 20] == background, "Gouraud triangle untouched sample");
    }

    private static void testModelProjectionAndRasterization() {
        int background = 0x010203;
        initializeRaster(128, 96, background);
        Model model = createQuadModel();

        model.renderSimple(0, 0, 0, 0, 0, 0, 900);

        check(pixelHash() == 0x0e08e6762878f776L, "model pixel hash");
        check(changedPixels(background) == 3808, "model changed-pixel count");
        check(model.horizontalRadius == 60, "model horizontal radius");
        check(model.radius == 79, "model radius");
        check(model.depthSpan == 158, "model depth span");
        check(pixels[48 * 128 + 64] == 0xdbb793, "model center sample");
    }

    private static void testPlainSceneTileRasterization() {
        int background = 0x0a0b0c;
        initializeRaster(160, 120, background);
        Scene scene = createScene();
        GenericTile tile = new GenericTile(0x1100, 0x2900, 0x4100, 0x5900, -1, 0, true);
        setSceneCamera();

        int pitch = 128;
        scene.renderPlainTile(tile, 0, 0, 0, Rasterizer3D.SINE[pitch], Rasterizer3D.COSINE[pitch],
                Rasterizer3D.SINE[0], Rasterizer3D.COSINE[0]);

        check(pixelHash() == 0xfcef3c131b65d421L, "plain scene tile pixel hash");
        check(changedPixels(background) == 2266, "plain scene tile changed-pixel count");
        check(pixels[60 * 160 + 80] == background, "plain scene tile background sample");
    }

    private static void testShapedSceneTileRasterization() {
        int background = 0x0d0e0f;
        initializeRaster(160, 120, background);
        Scene scene = createScene();
        ComplexTile tile = new ComplexTile(0, 0, 12, 24, 40, 0, 1, -1, 7,
                0x1100, 0x1500, 0x2500, 0x2900, 0x3900, 0x3d00, 0x4d00, 0x5100, 0, 0);
        setSceneCamera();

        int pitch = 128;
        scene.renderShapedTile(tile, 0, 0, Rasterizer3D.SINE[pitch], Rasterizer3D.COSINE[pitch],
                Rasterizer3D.SINE[0], Rasterizer3D.COSINE[0]);

        check(pixelHash() == 0xd4146d89d0035f5eL, "shaped scene tile pixel hash");
        check(changedPixels(background) == 2269, "shaped scene tile changed-pixel count");
        check(tile.vertexX.length == 6, "shaped scene tile vertex count");
        check(tile.triangleVertexA.length == 4, "shaped scene tile triangle count");
    }

    private static void testRealCacheTextureRasterization(Path cache) throws Exception {
        byte[] textureArchiveData;
        try (RandomAccessFile data = new RandomAccessFile(cache.resolve("main_file_cache.dat").toFile(), "r");
                RandomAccessFile index = new RandomAccessFile(cache.resolve("main_file_cache.idx0").toFile(), "r")) {
            textureArchiveData = new CacheIndex(1, 0x927c0, data, index).read(6);
        }
        check(textureArchiveData != null, "real texture archive is readable");

        Rasterizer3D.lowMemory = true;
        Rasterizer3D.loadTextures(new Archive(textureArchiveData));
        Field palettesField = Rasterizer3D.class.getDeclaredField("texturePalettes");
        palettesField.setAccessible(true);
        int[][] palettes = (int[][]) palettesField.get(null);
        int loadedTextures = 0;
        for (int textureId = 0; textureId < Rasterizer3D.textures.length; textureId++) {
            IndexedImage texture = Rasterizer3D.textures[textureId];
            if (texture != null) {
                palettes[textureId] = texture.palette.clone();
                loadedTextures++;
            }
        }
        check(loadedTextures == 50, "all 50 revision-377 textures load");
        Rasterizer3D.clearTextureCache();
        Rasterizer3D.initializeTexturePool(20);

        int background = 0x112233;
        initializeRaster(96, 72, background);
        Rasterizer3D.restrictEdges = true;
        Rasterizer3D.drawTexturedTriangle(5, 66, 22, 8, 88, 44, 64, 96, 80,
                -64, 64, 0, -48, -48, 64, 256, 256, 320, 0);

        check(pixelHash() == 0x581ef1b2694f3596L, "real-cache textured triangle pixel hash");
        check(changedPixels(background) == 440, "real-cache textured triangle changed-pixel count");
        check(pixels[35 * 96 + 48] == 0x281c0e, "real-cache textured triangle sample");
    }

    private static Model createQuadModel() {
        Model model = new Model(0, new Model[0]);
        model.vertexCount = 4;
        model.verticesX = new int[] { -60, 60, 60, -60 };
        model.verticesY = new int[] { -50, -50, 50, 50 };
        model.verticesZ = new int[] { 0, 0, 0, 0 };
        model.triangleCount = 2;
        model.triangleVertexA = new int[] { 0, 0 };
        model.triangleVertexB = new int[] { 2, 3 };
        model.triangleVertexC = new int[] { 1, 2 };
        model.triangleShadeA = new int[] { 0x1200, 0x3200 };
        model.triangleShadeB = new int[] { 0x4200, 0x6200 };
        model.triangleShadeC = new int[] { 0x7200, 0x2200 };
        model.triangleColors = new int[] { 0x1200, 0x3200 };
        model.defaultTrianglePriority = 0;
        model.calculateDiagonals();
        return model;
    }

    private static Scene createScene() {
        return new Scene(new int[][][] { { { 0, 24 }, { 12, 40 } } }, 1, 1, 1);
    }

    private static void setSceneCamera() {
        Scene.cameraX = 64;
        Scene.cameraY = -300;
        Scene.cameraZ = -90;
    }

    private static void initializeRaster(int width, int height, int background) {
        pixels = new int[width * height];
        Arrays.fill(pixels, background);
        Rasterizer.createRasterizer(pixels, width, height);
        Rasterizer3D.setDefaultBounds();
        Rasterizer3D.alpha = 0;
        Rasterizer3D.restrictEdges = false;
        Rasterizer3D.gouraudBlockShading = true;
        for (int index = 0; index < Rasterizer3D.HSL_TO_RGB.length; index++) {
            Rasterizer3D.HSL_TO_RGB[index] = ((index * 37) & 0xff) << 16
                    | ((index * 73) & 0xff) << 8
                    | (index * 109) & 0xff;
        }
    }

    private static long pixelHash() {
        long hash = FNV_OFFSET;
        for (int pixel : pixels) {
            hash ^= pixel & 0xffffffffL;
            hash *= FNV_PRIME;
        }
        return hash;
    }

    private static int changedPixels(int background) {
        int changed = 0;
        for (int pixel : pixels) {
            if (pixel != background) {
                changed++;
            }
        }
        return changed;
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }
}
