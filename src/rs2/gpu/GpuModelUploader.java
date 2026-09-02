package rs2.gpu;

import java.util.Arrays;

import rs2.media.Rasterizer3D;
import rs2.media.model.Model;
import rs2.scene.Scene;

/** Converts already-lit revision-377 Models into pre-transformed world-space triangles. */
final class GpuModelUploader {

    private static final int[] AVERAGE_TEXTURE_RGB = new int[Scene.TEXTURE_COLORS.length];
    private static final boolean[] AVERAGE_TEXTURE_VALID = new boolean[Scene.TEXTURE_COLORS.length];
    private static int averageTexturePaletteRevision = Integer.MIN_VALUE;

    private GpuModelUploader() {
    }

    static UploadResult append(Model model, int orientation, int worldX, int worldHeight, int worldY,
            GpuVertexBuilder vertices) {
        if (model == null || model.vertexCount == 0 || model.triangleCount == 0) {
            return UploadResult.EMPTY;
        }

        int sine = orientation == 0 ? 0 : Rasterizer3D.SINE[orientation & 0x7ff];
        int cosine = orientation == 0 ? 65536 : Rasterizer3D.COSINE[orientation & 0x7ff];
        int emittedTriangles = 0;

        for (int triangle = 0; triangle < model.triangleCount; triangle++) {
            if (!validTriangle(model, triangle)) {
                continue;
            }

            int rawDrawType = value(model.triangleDrawType, triangle, 0);
            if (rawDrawType == -1) {
                continue;
            }
            if (value(model.triangleAlpha, triangle, 0) >= 254) {
                continue;
            }

            int vertexA = model.triangleVertexA[triangle];
            int vertexB = model.triangleVertexB[triangle];
            int vertexC = model.triangleVertexC[triangle];
            if (!validVertex(model, vertexA) || !validVertex(model, vertexB) || !validVertex(model, vertexC)) {
                continue;
            }

            int drawType = rawDrawType & 3;
            int rgbA;
            int rgbB;
            int rgbC;
            if (drawType == 2 || drawType == 3) {
                int textureId = value(model.triangleColors, triangle, -1);
                int average = averageTextureRgb(textureId);
                int shadeA = shade(model.triangleShadeA, triangle, 64);
                int shadeB = drawType == 3 ? shadeA : shade(model.triangleShadeB, triangle, shadeA);
                int shadeC = drawType == 3 ? shadeA : shade(model.triangleShadeC, triangle, shadeA);
                rgbA = shadeTexture(average, shadeA);
                rgbB = shadeTexture(average, shadeB);
                rgbC = shadeTexture(average, shadeC);
            } else {
                int shadeA = shade(model.triangleShadeA, triangle, value(model.triangleColors, triangle, 0));
                int shadeB = drawType == 1 ? shadeA : shade(model.triangleShadeB, triangle, shadeA);
                int shadeC = drawType == 1 ? shadeA : shade(model.triangleShadeC, triangle, shadeA);
                rgbA = hslToRgb(shadeA);
                rgbB = hslToRgb(shadeB);
                rgbC = hslToRgb(shadeC);
            }

            appendVertex(vertices, model, vertexA, sine, cosine, worldX, worldHeight, worldY, rgbA);
            appendVertex(vertices, model, vertexB, sine, cosine, worldX, worldHeight, worldY, rgbB);
            appendVertex(vertices, model, vertexC, sine, cosine, worldX, worldHeight, worldY, rgbC);
            emittedTriangles++;
        }

        return emittedTriangles == 0 ? UploadResult.EMPTY : new UploadResult(1, emittedTriangles);
    }

    private static boolean validTriangle(Model model, int triangle) {
        return triangle >= 0 && triangle < model.triangleCount && model.triangleVertexA != null
                && triangle < model.triangleVertexA.length && model.triangleVertexB != null
                && triangle < model.triangleVertexB.length && model.triangleVertexC != null
                && triangle < model.triangleVertexC.length;
    }

    private static boolean validVertex(Model model, int vertex) {
        return vertex >= 0 && vertex < model.vertexCount && model.verticesX != null && vertex < model.verticesX.length
                && model.verticesY != null && vertex < model.verticesY.length && model.verticesZ != null
                && vertex < model.verticesZ.length;
    }

    private static int shade(int[] shades, int triangle, int fallback) {
        return value(shades, triangle, fallback);
    }

    private static int value(int[] values, int index, int fallback) {
        return values != null && index >= 0 && index < values.length ? values[index] : fallback;
    }

    private static int averageTextureRgb(int textureId) {
        if (textureId < 0 || textureId >= Scene.TEXTURE_COLORS.length) {
            return 0xffffff;
        }
        int paletteRevision = Rasterizer3D.paletteRevision();
        if (paletteRevision != averageTexturePaletteRevision) {
            Arrays.fill(AVERAGE_TEXTURE_VALID, false);
            averageTexturePaletteRevision = paletteRevision;
        }
        if (!AVERAGE_TEXTURE_VALID[textureId]) {
            int average;
            try {
                average = Rasterizer3D.getAverageTextureColor(textureId);
            } catch (RuntimeException ignored) {
                average = hslToRgb(Scene.TEXTURE_COLORS[textureId]);
            }
            AVERAGE_TEXTURE_RGB[textureId] = average;
            AVERAGE_TEXTURE_VALID[textureId] = true;
        }
        return AVERAGE_TEXTURE_RGB[textureId];
    }

    private static int shadeTexture(int rgb, int shade) {
        // Preserve the Phase-3 textured-face approximation exactly; only cache the
        // average texture lookup around it.
        int brightness = 127 - Math.max(0, Math.min(127, shade));
        float scale = 0.35f + brightness / 127.0f * 0.65f;
        int red = Math.min(255, Math.round(((rgb >> 16) & 0xff) * scale));
        int green = Math.min(255, Math.round(((rgb >> 8) & 0xff) * scale));
        int blue = Math.min(255, Math.round((rgb & 0xff) * scale));
        return red << 16 | green << 8 | blue;
    }

    private static int hslToRgb(int hsl) {
        int[] palette = Rasterizer3D.HSL_TO_RGB;
        if (palette == null || palette.length == 0) {
            return 0xffffff;
        }
        return palette[hsl & 0xffff];
    }

    private static void appendVertex(GpuVertexBuilder vertices, Model model, int vertex, int sine, int cosine,
            int worldX, int worldHeight, int worldY, int rgb) {
        int localX = model.verticesX[vertex];
        int localY = model.verticesY[vertex];
        int localZ = model.verticesZ[vertex];
        if (sine != 0) {
            int rotatedX = localZ * sine + localX * cosine >> 16;
            localZ = localZ * cosine - localX * sine >> 16;
            localX = rotatedX;
        }

        vertices.add(worldX + localX, worldHeight + localY, worldY + localZ, rgb);
    }

    static final class UploadResult {
        static final UploadResult EMPTY = new UploadResult(0, 0);
        final int instances;
        final int triangles;

        UploadResult(int instances, int triangles) {
            this.instances = instances;
            this.triangles = triangles;
        }
    }
}
