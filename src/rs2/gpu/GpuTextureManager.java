package rs2.gpu;

import static org.lwjgl.opengl.GL33C.GL_CLAMP_TO_EDGE;
import static org.lwjgl.opengl.GL33C.GL_NEAREST;
import static org.lwjgl.opengl.GL33C.GL_REPEAT;
import static org.lwjgl.opengl.GL33C.GL_RGBA;
import static org.lwjgl.opengl.GL33C.GL_RGBA8;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_MAG_FILTER;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_MIN_FILTER;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_WRAP_S;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_WRAP_T;
import static org.lwjgl.opengl.GL33C.GL_UNSIGNED_BYTE;
import static org.lwjgl.opengl.GL33C.glBindTexture;
import static org.lwjgl.opengl.GL33C.glGenTextures;
import static org.lwjgl.opengl.GL33C.glPixelStorei;
import static org.lwjgl.opengl.GL33C.glTexImage3D;
import static org.lwjgl.opengl.GL33C.glTexParameteri;
import static org.lwjgl.opengl.GL33C.glTexSubImage3D;
import static org.lwjgl.opengl.GL33C.GL_TEXTURE_2D_ARRAY;
import static org.lwjgl.opengl.GL33C.GL_UNPACK_ALIGNMENT;

import java.nio.ByteBuffer;
import java.util.Arrays;

import org.lwjgl.BufferUtils;

import rs2.media.Rasterizer3D;

/** Resident OpenGL texture-array cache backed by the revision-377 texture archive. */
final class GpuTextureManager {

    static final int TEXTURE_SIZE = 128;
    static final int TEXTURE_LAYERS = 50;
    private static final int BYTES_PER_LAYER = TEXTURE_SIZE * TEXTURE_SIZE * 4;

    private final int[] uploadedTextureRevisions = new int[TEXTURE_LAYERS];
    private final int[] rgbaScratch = new int[TEXTURE_SIZE * TEXTURE_SIZE];
    private final ByteBuffer uploadScratch = BufferUtils.createByteBuffer(BYTES_PER_LAYER);
    private int textureId;
    private int uploadedPaletteRevision = Integer.MIN_VALUE;
    private int residentLayers;
    private long uploadCount;

    GpuTextureManager() {
        Arrays.fill(uploadedTextureRevisions, Integer.MIN_VALUE);
    }

    void initialize() {
        textureId = glGenTextures();
        glBindTexture(GL_TEXTURE_2D_ARRAY, textureId);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MIN_FILTER, GL_NEAREST);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
        // Revision-377 clamps the horizontal projective coordinate but wraps rows.
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
        glTexParameteri(GL_TEXTURE_2D_ARRAY, GL_TEXTURE_WRAP_T, GL_REPEAT);
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1);
        glTexImage3D(GL_TEXTURE_2D_ARRAY, 0, GL_RGBA8, TEXTURE_SIZE, TEXTURE_SIZE, TEXTURE_LAYERS, 0, GL_RGBA,
                GL_UNSIGNED_BYTE, (ByteBuffer) null);
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
        updateResidentTextures();
    }

    void updateResidentTextures() {
        if (textureId == 0 || Rasterizer3D.textures == null) {
            return;
        }
        int paletteRevision = Rasterizer3D.paletteRevision();
        boolean paletteChanged = paletteRevision != uploadedPaletteRevision;
        if (paletteChanged) {
            Arrays.fill(uploadedTextureRevisions, Integer.MIN_VALUE);
            uploadedPaletteRevision = paletteRevision;
        }

        glBindTexture(GL_TEXTURE_2D_ARRAY, textureId);
        int available = 0;
        for (int layer = 0; layer < TEXTURE_LAYERS; layer++) {
            int revision = Rasterizer3D.textureRevision(layer);
            if (Rasterizer3D.hasTexture(layer)) {
                available++;
            }
            if (uploadedTextureRevisions[layer] == revision) {
                continue;
            }
            uploadLayer(layer);
            uploadedTextureRevisions[layer] = revision;
        }
        residentLayers = available;
        glBindTexture(GL_TEXTURE_2D_ARRAY, 0);
    }

    private void uploadLayer(int layer) {
        boolean present = Rasterizer3D.copyTextureRgba(layer, TEXTURE_SIZE, rgbaScratch);
        uploadScratch.clear();
        if (present) {
            for (int pixel : rgbaScratch) {
                uploadScratch.put((byte) (pixel >> 24));
                uploadScratch.put((byte) (pixel >> 16));
                uploadScratch.put((byte) (pixel >> 8));
                uploadScratch.put((byte) pixel);
            }
        } else {
            // Missing archive entries are valid. Keep them visible and deterministic if a
            // malformed scene references one rather than sampling uninitialized storage.
            for (int pixel = 0; pixel < rgbaScratch.length; pixel++) {
                uploadScratch.put((byte) 0xff);
                uploadScratch.put((byte) 0x00);
                uploadScratch.put((byte) 0xff);
                uploadScratch.put((byte) 0xff);
            }
        }
        uploadScratch.flip();
        glTexSubImage3D(GL_TEXTURE_2D_ARRAY, 0, 0, 0, layer, TEXTURE_SIZE, TEXTURE_SIZE, 1, GL_RGBA,
                GL_UNSIGNED_BYTE, uploadScratch);
        uploadCount++;
    }

    int textureId() {
        return textureId;
    }

    int residentLayers() {
        return residentLayers;
    }

    long uploadCount() {
        return uploadCount;
    }

    double memoryMiB() {
        return TEXTURE_SIZE * (double) TEXTURE_SIZE * TEXTURE_LAYERS * 4.0 / (1024.0 * 1024.0);
    }
}
