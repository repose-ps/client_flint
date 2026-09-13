package rs2.gpu;

import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Compact terrain vertex accumulator with texture coordinates and material metadata.
 *
 * <p>Terrain vertices remain integer world-space positions. Untextured vertices carry
 * packed RGB while textured vertices carry the original 0..127 software texture
 * shade in RGB, signed Q8.8 texture coordinates, and a texture id encoded as
 * {@code id + 1}. Zero therefore remains the untextured material sentinel.</p>
 */
final class GpuTerrainVertexBuilder {

    static final int WORDS_PER_VERTEX = 6;
    static final int BYTES_PER_VERTEX = WORDS_PER_VERTEX * Integer.BYTES;
    static final int COLOR_BYTE_OFFSET = 3 * Integer.BYTES;
    static final int UV_BYTE_OFFSET = 4 * Integer.BYTES;
    static final int MATERIAL_BYTE_OFFSET = 5 * Integer.BYTES;
    static final int UV_FIXED_ONE = 256;

    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    private int[] words;
    private int wordCount;
    private int minX = Integer.MAX_VALUE;
    private int minHeight = Integer.MAX_VALUE;
    private int minY = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE;
    private int maxHeight = Integer.MIN_VALUE;
    private int maxY = Integer.MIN_VALUE;

    GpuTerrainVertexBuilder(int initialVertexCapacity) {
        words = new int[Math.max(WORDS_PER_VERTEX, initialVertexCapacity * WORDS_PER_VERTEX)];
    }

    void addUntextured(int x, int height, int y, int rgb) {
        add(x, height, y, GpuVertexBuilder.packRgba(rgb), 0, 0);
    }

    void addTextured(int x, int height, int y, int shade, int uFixed, int vFixed, int textureId) {
        int clampedShade = Math.max(0, Math.min(127, shade));
        int shadeRgb = clampedShade << 16 | clampedShade << 8 | clampedShade;
        int packedUv = packUv(uFixed, vFixed);
        int material = textureId < 0 ? 0 : (textureId + 1) & 0xff;
        add(x, height, y, GpuVertexBuilder.packRgba(shadeRgb), packedUv, material);
    }

    private void add(int x, int height, int y, int color, int packedUv, int material) {
        ensureWords(WORDS_PER_VERTEX);
        words[wordCount++] = x;
        words[wordCount++] = height;
        words[wordCount++] = y;
        words[wordCount++] = color;
        words[wordCount++] = packedUv;
        words[wordCount++] = material;
        minX = Math.min(minX, x);
        minHeight = Math.min(minHeight, height);
        minY = Math.min(minY, y);
        maxX = Math.max(maxX, x);
        maxHeight = Math.max(maxHeight, height);
        maxY = Math.max(maxY, y);
    }

    int vertexCount() {
        return wordCount / WORDS_PER_VERTEX;
    }

    int wordCount() {
        return wordCount;
    }

    void copyTo(int[] destination, int wordOffset) {
        System.arraycopy(words, 0, destination, wordOffset, wordCount);
    }

    GpuSceneChunk toChunk(int firstVertex, int minRenderPlane) {
        if (wordCount == 0) {
            throw new IllegalStateException("Cannot create a draw range for an empty GPU terrain builder.");
        }
        return new GpuSceneChunk(firstVertex, vertexCount(), minX, minHeight, minY, maxX, maxHeight, maxY,
                minRenderPlane);
    }

    static int packUv(int uFixed, int vFixed) {
        int u = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, uFixed));
        int v = Math.max(Short.MIN_VALUE, Math.min(Short.MAX_VALUE, vFixed));
        if (LITTLE_ENDIAN) {
            return (v & 0xffff) << 16 | u & 0xffff;
        }
        return (u & 0xffff) << 16 | v & 0xffff;
    }

    static int materialTextureId(int materialWord) {
        int encoded = materialWord & 0xff;
        return encoded == 0 ? -1 : encoded - 1;
    }

    static int unpackUFixed(int packedUv) {
        return LITTLE_ENDIAN ? (short) (packedUv & 0xffff) : (short) (packedUv >>> 16);
    }

    static int unpackVFixed(int packedUv) {
        return LITTLE_ENDIAN ? (short) (packedUv >>> 16) : (short) (packedUv & 0xffff);
    }

    private void ensureWords(int additionalWords) {
        int required = wordCount + additionalWords;
        if (required <= words.length) {
            return;
        }
        int grown = words.length + (words.length >> 1) + WORDS_PER_VERTEX;
        words = Arrays.copyOf(words, Math.max(required, grown));
    }
}
