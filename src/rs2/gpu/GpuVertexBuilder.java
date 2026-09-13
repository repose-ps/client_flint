package rs2.gpu;

import java.nio.ByteOrder;
import java.util.Arrays;

/**
 * Primitive accumulator for Flint's compact static GPU vertex format.
 *
 * <p>Each vertex is four native-order 32-bit words: x, height, y and packed
 * RGBA8. Keeping scene positions as integers exactly matches the revision-377
 * fixed-point camera math while cutting the core vertex stream from six floats
 * (24 bytes) to 16 bytes per vertex.</p>
 */
final class GpuVertexBuilder {

    static final int WORDS_PER_VERTEX = 4;
    static final int BYTES_PER_VERTEX = WORDS_PER_VERTEX * Integer.BYTES;
    static final int COLOR_BYTE_OFFSET = 3 * Integer.BYTES;

    private static final boolean LITTLE_ENDIAN = ByteOrder.nativeOrder() == ByteOrder.LITTLE_ENDIAN;

    private int[] words;
    private int wordCount;
    private int minX = Integer.MAX_VALUE;
    private int minHeight = Integer.MAX_VALUE;
    private int minY = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE;
    private int maxHeight = Integer.MIN_VALUE;
    private int maxY = Integer.MIN_VALUE;

    GpuVertexBuilder(int initialVertexCapacity) {
        words = new int[Math.max(WORDS_PER_VERTEX, initialVertexCapacity * WORDS_PER_VERTEX)];
    }

    void add(int x, int height, int y, int rgb) {
        add(x, height, y, rgb, 0xff);
    }

    /** Adds a vertex while storing one byte of opaque static-model metadata in alpha. */
    void add(int x, int height, int y, int rgb, int alphaByte) {
        ensureWords(WORDS_PER_VERTEX);
        words[wordCount++] = x;
        words[wordCount++] = height;
        words[wordCount++] = y;
        words[wordCount++] = packRgba(rgb, alphaByte);
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

    void clear() {
        wordCount = 0;
        minX = Integer.MAX_VALUE;
        minHeight = Integer.MAX_VALUE;
        minY = Integer.MAX_VALUE;
        maxX = Integer.MIN_VALUE;
        maxHeight = Integer.MIN_VALUE;
        maxY = Integer.MIN_VALUE;
    }

    void copyTo(int[] destination, int wordOffset) {
        System.arraycopy(words, 0, destination, wordOffset, wordCount);
    }

    GpuSceneChunk toChunk(int firstVertex, int minRenderPlane) {
        if (wordCount == 0) {
            throw new IllegalStateException("Cannot create a draw range for an empty GPU vertex builder.");
        }
        return new GpuSceneChunk(firstVertex, vertexCount(), minX, minHeight, minY, maxX, maxHeight, maxY,
                minRenderPlane);
    }

    private void ensureWords(int additionalWords) {
        int required = wordCount + additionalWords;
        if (required <= words.length) {
            return;
        }
        int grown = words.length + (words.length >> 1) + WORDS_PER_VERTEX;
        words = Arrays.copyOf(words, Math.max(required, grown));
    }

    /** Packs RGBA bytes so their in-memory order is R,G,B,A on every platform. */
    static int packRgba(int rgb) {
        return packRgba(rgb, 0xff);
    }

    static int packRgba(int rgb, int alphaByte) {
        int red = rgb >> 16 & 0xff;
        int green = rgb >> 8 & 0xff;
        int blue = rgb & 0xff;
        int alpha = Math.max(0, Math.min(255, alphaByte));
        if (LITTLE_ENDIAN) {
            return alpha << 24 | blue << 16 | green << 8 | red;
        }
        return red << 24 | green << 16 | blue << 8 | alpha;
    }
}
