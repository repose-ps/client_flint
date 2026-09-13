package rs2.tools;

import rs2.media.Rasterizer3D;
import rs2.media.sprite.IndexedImage;

/** Cache-free checks for the revision-377 texture source exposed to OpenGL. */
public final class GpuTextureSourceSelfTest {

    private GpuTextureSourceSelfTest() {
    }

    public static int run() {
        SelfTestSupport test = new SelfTestSupport();
        int textureId = 49;
        IndexedImage previous = Rasterizer3D.textures[textureId];
        try {
            IndexedImage texture = new IndexedImage(2, 2, new int[] { 0, 0xff0000, 0x00ff00, 0x0000ff });
            texture.pixels[0] = 0;
            texture.pixels[1] = 1;
            texture.pixels[2] = 2;
            texture.pixels[3] = 3;
            Rasterizer3D.textures[textureId] = texture;
            Rasterizer3D.markTextureChanged(textureId);
            int revision = Rasterizer3D.textureRevision(textureId);
            Rasterizer3D.setBrightness(1.0D);

            int[] rgba = new int[16];
            test.check(Rasterizer3D.copyTextureRgba(textureId, 4, rgba),
                    "GPU texture source expands a loaded indexed texture");
            test.equal(rgba[0] & 0xff, 0, "palette index zero remains transparent");
            test.equal(rgba[3] & 0xff, 0xff, "nonzero indexed texel remains opaque");
            test.equal(rgba[0], rgba[1], "nearest expansion duplicates the first source texel horizontally");
            test.equal(rgba[0], rgba[4], "nearest expansion duplicates the first source texel vertically");
            test.check((rgba[2] >>> 24 & 0xff) > (rgba[2] >>> 16 & 0xff),
                    "palette conversion preserves red-dominant texel colour");
            Rasterizer3D.markTextureChanged(textureId);
            test.equal(Rasterizer3D.textureRevision(textureId), revision + 1,
                    "texture content revision advances independently of scene geometry");
        } finally {
            Rasterizer3D.textures[textureId] = previous;
            Rasterizer3D.markTextureChanged(textureId);
        }
        return test.checks();
    }

    public static void main(String[] args) {
        int checks = run();
        System.out.println("GpuTextureSourceSelfTest: PASS (" + checks + " checks, cache-free)");
    }
}
