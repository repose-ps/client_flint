package rs2.gpu;

import java.util.Arrays;

/**
 * Per-chunk accumulator for static textured model faces.
 *
 * <p>The face vertices remain in Flint's compact 16-byte position/shade stream.
 * One separate 48-byte mapping record is stored per textured triangle so the
 * shader can reproduce the revision-377 projective texture mapping without
 * duplicating the mapping triangle on all three rendered vertices.</p>
 */
final class GpuModelTextureBuilder {

    static final int MAPPING_WORDS_PER_TRIANGLE = 12;
    static final int MAPPING_BYTES_PER_TRIANGLE = MAPPING_WORDS_PER_TRIANGLE * Integer.BYTES;

    private final GpuVertexBuilder vertices;
    private int[] mappingWords;
    private int mappingWordCount;

    GpuModelTextureBuilder(int initialTriangleCapacity) {
        vertices = new GpuVertexBuilder(Math.max(3, initialTriangleCapacity * 3));
        mappingWords = new int[Math.max(MAPPING_WORDS_PER_TRIANGLE,
                initialTriangleCapacity * MAPPING_WORDS_PER_TRIANGLE)];
    }

    void addTriangle(int ax, int ay, int az, int shadeA,
            int bx, int by, int bz, int shadeB,
            int cx, int cy, int cz, int shadeC,
            int mapAx, int mapAy, int mapAz,
            int mapBx, int mapBy, int mapBz,
            int mapCx, int mapCy, int mapCz,
            int textureId, int priorityByte) {
        vertices.add(ax, ay, az, shadeRgb(shadeA), priorityByte);
        vertices.add(bx, by, bz, shadeRgb(shadeB), priorityByte);
        vertices.add(cx, cy, cz, shadeRgb(shadeC), priorityByte);

        ensureMappingWords(MAPPING_WORDS_PER_TRIANGLE);
        mappingWords[mappingWordCount++] = mapAx;
        mappingWords[mappingWordCount++] = mapAy;
        mappingWords[mappingWordCount++] = mapAz;
        mappingWords[mappingWordCount++] = textureId;
        mappingWords[mappingWordCount++] = mapBx;
        mappingWords[mappingWordCount++] = mapBy;
        mappingWords[mappingWordCount++] = mapBz;
        mappingWords[mappingWordCount++] = 0;
        mappingWords[mappingWordCount++] = mapCx;
        mappingWords[mappingWordCount++] = mapCy;
        mappingWords[mappingWordCount++] = mapCz;
        mappingWords[mappingWordCount++] = 0;
    }

    int vertexCount() {
        return vertices.vertexCount();
    }

    int vertexWordCount() {
        return vertices.wordCount();
    }

    int triangleCount() {
        return mappingWordCount / MAPPING_WORDS_PER_TRIANGLE;
    }

    int mappingWordCount() {
        return mappingWordCount;
    }

    void clear() {
        vertices.clear();
        mappingWordCount = 0;
    }

    void copyVerticesTo(int[] destination, int wordOffset) {
        vertices.copyTo(destination, wordOffset);
    }

    void copyVertexRangeTo(int firstVertex, int vertexCount, int[] destination, int wordOffset) {
        vertices.copyRangeTo(firstVertex, vertexCount, destination, wordOffset);
    }

    void copyMappingsTo(int[] destination, int wordOffset) {
        System.arraycopy(mappingWords, 0, destination, wordOffset, mappingWordCount);
    }

    void copyMappingRangeTo(int firstTriangle, int triangleCount, int[] destination, int wordOffset) {
        if (triangleCount <= 0) {
            return;
        }
        System.arraycopy(mappingWords, firstTriangle * MAPPING_WORDS_PER_TRIANGLE, destination, wordOffset,
                triangleCount * MAPPING_WORDS_PER_TRIANGLE);
    }

    /** Appends one solid triangle to a unified painter stream using a no-texture sentinel mapping. */
    void appendSolidTriangleFrom(GpuVertexBuilder source, int firstVertex) {
        vertices.appendPackedRangeFrom(source, firstVertex, 3);
        ensureMappingWords(MAPPING_WORDS_PER_TRIANGLE);
        Arrays.fill(mappingWords, mappingWordCount, mappingWordCount + MAPPING_WORDS_PER_TRIANGLE, 0);
        mappingWords[mappingWordCount + 3] = -1;
        mappingWordCount += MAPPING_WORDS_PER_TRIANGLE;
    }

    /** Appends one textured triangle and its mapping to a unified painter stream. */
    void appendTexturedTriangleFrom(GpuModelTextureBuilder source, int firstVertex) {
        if (source == null || firstVertex < 0 || firstVertex + 3 > source.vertexCount()) {
            throw new IndexOutOfBoundsException("Textured triangle outside source builder");
        }
        vertices.appendPackedRangeFrom(source.vertices, firstVertex, 3);
        int firstTriangle = firstVertex / 3;
        ensureMappingWords(MAPPING_WORDS_PER_TRIANGLE);
        System.arraycopy(source.mappingWords, firstTriangle * MAPPING_WORDS_PER_TRIANGLE, mappingWords,
                mappingWordCount, MAPPING_WORDS_PER_TRIANGLE);
        mappingWordCount += MAPPING_WORDS_PER_TRIANGLE;
    }

    GpuSceneChunk toChunk(int firstVertex, int minRenderPlane) {
        return vertices.toChunk(firstVertex, minRenderPlane);
    }

    private static int shadeRgb(int shade) {
        int clamped = Math.max(0, Math.min(127, shade));
        return clamped << 16 | clamped << 8 | clamped;
    }

    private void ensureMappingWords(int additionalWords) {
        int required = mappingWordCount + additionalWords;
        if (required <= mappingWords.length) {
            return;
        }
        int grown = mappingWords.length + (mappingWords.length >> 1) + MAPPING_WORDS_PER_TRIANGLE;
        mappingWords = Arrays.copyOf(mappingWords, Math.max(required, grown));
    }
}
