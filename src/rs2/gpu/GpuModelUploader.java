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
            GpuVertexBuilder solidVertices, GpuModelTextureBuilder texturedVertices) {
        return append(model, orientation, worldX, worldHeight, worldY, solidVertices, texturedVertices, false);
    }

    static UploadResult append(Model model, int orientation, int worldX, int worldHeight, int worldY,
            GpuVertexBuilder solidVertices, GpuModelTextureBuilder texturedVertices, boolean encodeFacePriority) {
        return append(model, orientation, worldX, worldHeight, worldY, solidVertices, texturedVertices,
                encodeFacePriority, 0);
    }

    static UploadResult append(Model model, int orientation, int worldX, int worldHeight, int worldY,
            GpuVertexBuilder solidVertices, GpuModelTextureBuilder texturedVertices, boolean encodeFacePriority,
            int priorityMetadataTag) {
        return append(model, orientation, worldX, worldHeight, worldY, solidVertices, texturedVertices,
                encodeFacePriority, priorityMetadataTag, -1);
    }

    static UploadResult appendPriority(Model model, int orientation, int worldX, int worldHeight, int worldY,
            GpuVertexBuilder solidVertices, GpuModelTextureBuilder texturedVertices, int requiredPriority,
            int priorityMetadataTag) {
        return append(model, orientation, worldX, worldHeight, worldY, solidVertices, texturedVertices, true,
                priorityMetadataTag, Math.max(0, Math.min(11, requiredPriority)));
    }

    static final int FACE_SKIPPED = 0;
    static final int FACE_SOLID = 1;
    static final int FACE_TEXTURED = 2;

    /** Returns the number of model faces which can contribute to the static GPU path. */
    static int drawableTriangleCount(Model model) {
        if (model == null) {
            return 0;
        }
        int count = 0;
        for (int triangle = 0; triangle < model.triangleCount; triangle++) {
            if (!validTriangle(model, triangle)) {
                continue;
            }
            int rawDrawType = value(model.triangleDrawType, triangle, 0);
            if (rawDrawType == -1) {
                continue;
            }
            int drawType = rawDrawType & 3;
            if (drawType < 2 && value(model.triangleAlpha, triangle, 0) >= 254) {
                continue;
            }
            int a = model.triangleVertexA[triangle];
            int b = model.triangleVertexB[triangle];
            int c = model.triangleVertexC[triangle];
            if (validVertex(model, a) && validVertex(model, b) && validVertex(model, c)) {
                count++;
            }
        }
        return count;
    }

    /** Counts faces for the exact legacy-alpha path without treating alpha 254/255 as hidden. */
    static int drawableLegacyAlphaTriangleCount(Model model) {
        if (model == null) {
            return 0;
        }
        int count = 0;
        for (int triangle = 0; triangle < model.triangleCount; triangle++) {
            if (!validTriangle(model, triangle)) {
                continue;
            }
            int rawDrawType = value(model.triangleDrawType, triangle, 0);
            if (rawDrawType == -1) {
                continue;
            }
            int a = model.triangleVertexA[triangle];
            int b = model.triangleVertexB[triangle];
            int c = model.triangleVertexC[triangle];
            if (validVertex(model, a) && validVertex(model, b) && validVertex(model, c)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Appends exactly one model face. The caller controls face order, allowing the
     * GPU compatibility painter to reproduce revision-377 Model.drawFaces().
     */
    static int appendFace(Model model, int triangle, int orientation, int worldX, int worldHeight, int worldY,
            GpuVertexBuilder solidVertices, GpuModelTextureBuilder texturedVertices, int priorityMetadataTag) {
        if (model == null || !validTriangle(model, triangle)) {
            return FACE_SKIPPED;
        }
        int rawDrawType = value(model.triangleDrawType, triangle, 0);
        if (rawDrawType == -1) {
            return FACE_SKIPPED;
        }
        int drawType = rawDrawType & 3;
        if (drawType < 2 && value(model.triangleAlpha, triangle, 0) >= 254) {
            return FACE_SKIPPED;
        }

        int vertexA = model.triangleVertexA[triangle];
        int vertexB = model.triangleVertexB[triangle];
        int vertexC = model.triangleVertexC[triangle];
        if (!validVertex(model, vertexA) || !validVertex(model, vertexB) || !validVertex(model, vertexC)) {
            return FACE_SKIPPED;
        }

        int sine = orientation == 0 ? 0 : Rasterizer3D.SINE[orientation & 0x7ff];
        int cosine = orientation == 0 ? 65536 : Rasterizer3D.COSINE[orientation & 0x7ff];
        int facePriority = model.trianglePriorities == null ? 0xff
                : priorityMetadataTag | Math.max(0, Math.min(11, value(model.trianglePriorities, triangle, 0)));

        if ((drawType == 2 || drawType == 3)
                && appendTexturedFace(model, triangle, rawDrawType, drawType, vertexA, vertexB, vertexC, sine,
                        cosine, worldX, worldHeight, worldY, texturedVertices, facePriority)) {
            return FACE_TEXTURED;
        }

        if (drawType == 2 || drawType == 3) {
            appendTexturedFallback(model, triangle, drawType, vertexA, vertexB, vertexC, sine, cosine, worldX,
                    worldHeight, worldY, solidVertices, facePriority);
        } else {
            int shadeA = shade(model.triangleShadeA, triangle, value(model.triangleColors, triangle, 0));
            int shadeB = drawType == 1 ? shadeA : shade(model.triangleShadeB, triangle, shadeA);
            int shadeC = drawType == 1 ? shadeA : shade(model.triangleShadeC, triangle, shadeA);
            appendVertex(solidVertices, model, vertexA, sine, cosine, worldX, worldHeight, worldY,
                    hslToRgb(shadeA), facePriority);
            appendVertex(solidVertices, model, vertexB, sine, cosine, worldX, worldHeight, worldY,
                    hslToRgb(shadeB), facePriority);
            appendVertex(solidVertices, model, vertexC, sine, cosine, worldX, worldHeight, worldY,
                    hslToRgb(shadeC), facePriority);
        }
        return FACE_SOLID;
    }

    /**
     * Appends exactly one face for the legacy-alpha compatibility painter. Solid
     * draw types keep the raw triangleAlpha byte; textured draw types remain opaque
     * because revision 377's textured rasterizer does not consult Rasterizer3D.alpha.
     */
    static int appendLegacyAlphaFace(Model model, int triangle, int orientation, int worldX, int worldHeight,
            int worldY, GpuVertexBuilder solidVertices, GpuModelTextureBuilder texturedVertices) {
        if (model == null || !validTriangle(model, triangle)) {
            return FACE_SKIPPED;
        }
        int rawDrawType = value(model.triangleDrawType, triangle, 0);
        if (rawDrawType == -1) {
            return FACE_SKIPPED;
        }
        int drawType = rawDrawType & 3;

        int vertexA = model.triangleVertexA[triangle];
        int vertexB = model.triangleVertexB[triangle];
        int vertexC = model.triangleVertexC[triangle];
        if (!validVertex(model, vertexA) || !validVertex(model, vertexB) || !validVertex(model, vertexC)) {
            return FACE_SKIPPED;
        }

        int sine = orientation == 0 ? 0 : Rasterizer3D.SINE[orientation & 0x7ff];
        int cosine = orientation == 0 ? 65536 : Rasterizer3D.COSINE[orientation & 0x7ff];
        int rawAlpha = value(model.triangleAlpha, triangle, 0) & 0xff;

        if ((drawType == 2 || drawType == 3)
                && appendTexturedFace(model, triangle, rawDrawType, drawType, vertexA, vertexB, vertexC, sine,
                        cosine, worldX, worldHeight, worldY, texturedVertices, 0)) {
            return FACE_TEXTURED;
        }

        if (drawType == 2 || drawType == 3) {
            // A texture fallback is still opaque in the software renderer because
            // drawTexturedTriangle() ignores Rasterizer3D.alpha.
            appendTexturedFallback(model, triangle, drawType, vertexA, vertexB, vertexC, sine, cosine, worldX,
                    worldHeight, worldY, solidVertices, 0);
        } else {
            int shadeA = shade(model.triangleShadeA, triangle, value(model.triangleColors, triangle, 0));
            int shadeB = drawType == 1 ? shadeA : shade(model.triangleShadeB, triangle, shadeA);
            int shadeC = drawType == 1 ? shadeA : shade(model.triangleShadeC, triangle, shadeA);
            appendVertex(solidVertices, model, vertexA, sine, cosine, worldX, worldHeight, worldY,
                    hslToRgb(shadeA), rawAlpha);
            appendVertex(solidVertices, model, vertexB, sine, cosine, worldX, worldHeight, worldY,
                    hslToRgb(shadeB), rawAlpha);
            appendVertex(solidVertices, model, vertexC, sine, cosine, worldX, worldHeight, worldY,
                    hslToRgb(shadeC), rawAlpha);
        }
        return FACE_SOLID;
    }

    /**
     * Diagnostic upload path for revision-377 Gouraud shading. Solid draw types keep
     * their raw 16-bit HSL shade at each vertex so the fragment shader can interpolate
     * the shade first and perform the palette lookup afterwards, matching the software
     * rasterizer's ordering. Textured faces retain the existing texture path.
     */
    static UploadResult appendLegacyGouraud(Model model, int orientation, int worldX, int worldHeight, int worldY,
            GpuVertexBuilder solidVertices, GpuModelTextureBuilder texturedVertices) {
        return append(model, orientation, worldX, worldHeight, worldY, solidVertices, texturedVertices, true, 0, -1,
                true);
    }

    private static UploadResult append(Model model, int orientation, int worldX, int worldHeight, int worldY,
            GpuVertexBuilder solidVertices, GpuModelTextureBuilder texturedVertices, boolean encodeFacePriority,
            int priorityMetadataTag, int requiredPriority) {
        return append(model, orientation, worldX, worldHeight, worldY, solidVertices, texturedVertices,
                encodeFacePriority, priorityMetadataTag, requiredPriority, false);
    }

    private static UploadResult append(Model model, int orientation, int worldX, int worldHeight, int worldY,
            GpuVertexBuilder solidVertices, GpuModelTextureBuilder texturedVertices, boolean encodeFacePriority,
            int priorityMetadataTag, int requiredPriority, boolean encodeLegacyGouraud) {
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
            if (requiredPriority >= 0) {
                int actualPriority = Math.max(0, Math.min(11, value(model.trianglePriorities, triangle, 0)));
                if (actualPriority != requiredPriority) {
                    continue;
                }
            }

            int rawDrawType = value(model.triangleDrawType, triangle, 0);
            if (rawDrawType == -1) {
                continue;
            }
            int drawType = rawDrawType & 3;
            int facePriority = 0xff;
            if (encodeFacePriority && (model.trianglePriorities != null || priorityMetadataTag != 0)) {
                int legacyPriority = model.trianglePriorities == null ? 0
                        : Math.max(0, Math.min(11, value(model.trianglePriorities, triangle, 0)));
                // The alpha byte is otherwise unused by opaque static faces. Keep the
                // low nibble as the legacy 0..11 priority and reserve the upper bits for
                // object-class tags (floor=0x80, wall-decoration=0x40). Class tags are
                // emitted even when a model has no explicit priority array so the shader
                // can reproduce scene-level painter relationships such as floor
                // decorations being drawn after their supporting terrain.
                facePriority = priorityMetadataTag | legacyPriority;
            }
            // The legacy textured scanline path does not consult Rasterizer3D.alpha.
            // Alpha 254/255 is therefore not a hidden-face marker for draw types 2/3;
            // dropping those faces creates literal holes in textured models. Keep the
            // existing near-invisible-face shortcut only for Gouraud/flat faces.
            if (drawType < 2 && value(model.triangleAlpha, triangle, 0) >= 254) {
                continue;
            }

            int vertexA = model.triangleVertexA[triangle];
            int vertexB = model.triangleVertexB[triangle];
            int vertexC = model.triangleVertexC[triangle];
            if (!validVertex(model, vertexA) || !validVertex(model, vertexB) || !validVertex(model, vertexC)) {
                continue;
            }

            if ((drawType == 2 || drawType == 3)
                    && appendTexturedFace(model, triangle, rawDrawType, drawType, vertexA, vertexB, vertexC, sine,
                            cosine, worldX, worldHeight, worldY, texturedVertices, facePriority)) {
                emittedTriangles++;
                continue;
            }

            if (drawType == 2 || drawType == 3) {
                appendTexturedFallback(model, triangle, drawType, vertexA, vertexB, vertexC, sine, cosine, worldX,
                        worldHeight, worldY, solidVertices, facePriority);
            } else {
                int shadeA = shade(model.triangleShadeA, triangle, value(model.triangleColors, triangle, 0));
                int shadeB = drawType == 1 ? shadeA : shade(model.triangleShadeB, triangle, shadeA);
                int shadeC = drawType == 1 ? shadeA : shade(model.triangleShadeC, triangle, shadeA);
                if (encodeLegacyGouraud) {
                    int priority = model.trianglePriorities == null ? 0
                            : Math.max(0, Math.min(11, value(model.trianglePriorities, triangle, 0)));
                    int legacyGouraudMetadata = 0x20 | priority;
                    appendLegacyGouraudVertex(solidVertices, model, vertexA, sine, cosine, worldX, worldHeight,
                            worldY, shadeA, legacyGouraudMetadata);
                    appendLegacyGouraudVertex(solidVertices, model, vertexB, sine, cosine, worldX, worldHeight,
                            worldY, shadeB, legacyGouraudMetadata);
                    appendLegacyGouraudVertex(solidVertices, model, vertexC, sine, cosine, worldX, worldHeight,
                            worldY, shadeC, legacyGouraudMetadata);
                } else {
                    appendVertex(solidVertices, model, vertexA, sine, cosine, worldX, worldHeight, worldY,
                            hslToRgb(shadeA), facePriority);
                    appendVertex(solidVertices, model, vertexB, sine, cosine, worldX, worldHeight, worldY,
                            hslToRgb(shadeB), facePriority);
                    appendVertex(solidVertices, model, vertexC, sine, cosine, worldX, worldHeight, worldY,
                            hslToRgb(shadeC), facePriority);
                }
            }
            emittedTriangles++;
        }

        return emittedTriangles == 0 ? UploadResult.EMPTY : new UploadResult(1, emittedTriangles);
    }

    private static boolean appendTexturedFace(Model model, int triangle, int rawDrawType, int drawType, int vertexA,
            int vertexB, int vertexC, int sine, int cosine, int worldX, int worldHeight, int worldY,
            GpuModelTextureBuilder output, int facePriority) {
        int textureId = value(model.triangleColors, triangle, -1);
        if (textureId < 0 || textureId >= Scene.TEXTURE_COLORS.length || output == null) {
            return false;
        }

        int textureTriangle = rawDrawType >> 2;
        if (!validTextureTriangle(model, textureTriangle)) {
            return false;
        }
        int textureVertexA = model.texturedTriangleA[textureTriangle];
        int textureVertexB = model.texturedTriangleB[textureTriangle];
        int textureVertexC = model.texturedTriangleC[textureTriangle];
        if (!validVertex(model, textureVertexA) || !validVertex(model, textureVertexB)
                || !validVertex(model, textureVertexC)
                || !nonDegenerateTextureTriangle(model, textureVertexA, textureVertexB, textureVertexC)) {
            return false;
        }

        int shadeA = shade(model.triangleShadeA, triangle, 64);
        int shadeB = drawType == 3 ? shadeA : shade(model.triangleShadeB, triangle, shadeA);
        int shadeC = drawType == 3 ? shadeA : shade(model.triangleShadeC, triangle, shadeA);

        int faceAx = worldVertexX(model, vertexA, sine, cosine, worldX);
        int faceAy = worldHeight + model.verticesY[vertexA];
        int faceAz = worldVertexY(model, vertexA, sine, cosine, worldY);
        int faceBx = worldVertexX(model, vertexB, sine, cosine, worldX);
        int faceBy = worldHeight + model.verticesY[vertexB];
        int faceBz = worldVertexY(model, vertexB, sine, cosine, worldY);
        int faceCx = worldVertexX(model, vertexC, sine, cosine, worldX);
        int faceCy = worldHeight + model.verticesY[vertexC];
        int faceCz = worldVertexY(model, vertexC, sine, cosine, worldY);

        int mapAx = worldVertexX(model, textureVertexA, sine, cosine, worldX);
        int mapAy = worldHeight + model.verticesY[textureVertexA];
        int mapAz = worldVertexY(model, textureVertexA, sine, cosine, worldY);
        int mapBx = worldVertexX(model, textureVertexB, sine, cosine, worldX);
        int mapBy = worldHeight + model.verticesY[textureVertexB];
        int mapBz = worldVertexY(model, textureVertexB, sine, cosine, worldY);
        int mapCx = worldVertexX(model, textureVertexC, sine, cosine, worldX);
        int mapCy = worldHeight + model.verticesY[textureVertexC];
        int mapCz = worldVertexY(model, textureVertexC, sine, cosine, worldY);

        output.addTriangle(faceAx, faceAy, faceAz, shadeA, faceBx, faceBy, faceBz, shadeB, faceCx, faceCy, faceCz,
                shadeC, mapAx, mapAy, mapAz, mapBx, mapBy, mapBz, mapCx, mapCy, mapCz, textureId, facePriority);
        return true;
    }

    private static void appendTexturedFallback(Model model, int triangle, int drawType, int vertexA, int vertexB,
            int vertexC, int sine, int cosine, int worldX, int worldHeight, int worldY, GpuVertexBuilder vertices,
            int facePriority) {
        int textureId = value(model.triangleColors, triangle, -1);
        int average = averageTextureRgb(textureId);
        int shadeA = shade(model.triangleShadeA, triangle, 64);
        int shadeB = drawType == 3 ? shadeA : shade(model.triangleShadeB, triangle, shadeA);
        int shadeC = drawType == 3 ? shadeA : shade(model.triangleShadeC, triangle, shadeA);
        appendVertex(vertices, model, vertexA, sine, cosine, worldX, worldHeight, worldY, shadeTexture(average, shadeA),
                facePriority);
        appendVertex(vertices, model, vertexB, sine, cosine, worldX, worldHeight, worldY, shadeTexture(average, shadeB),
                facePriority);
        appendVertex(vertices, model, vertexC, sine, cosine, worldX, worldHeight, worldY, shadeTexture(average, shadeC),
                facePriority);
    }

    private static boolean validTriangle(Model model, int triangle) {
        return triangle >= 0 && triangle < model.triangleCount && model.triangleVertexA != null
                && triangle < model.triangleVertexA.length && model.triangleVertexB != null
                && triangle < model.triangleVertexB.length && model.triangleVertexC != null
                && triangle < model.triangleVertexC.length;
    }

    private static boolean validTextureTriangle(Model model, int triangle) {
        return triangle >= 0 && triangle < model.texturedTriangleCount && model.texturedTriangleA != null
                && triangle < model.texturedTriangleA.length && model.texturedTriangleB != null
                && triangle < model.texturedTriangleB.length && model.texturedTriangleC != null
                && triangle < model.texturedTriangleC.length;
    }

    private static boolean nonDegenerateTextureTriangle(Model model, int a, int b, int c) {
        long abX = model.verticesX[b] - (long) model.verticesX[a];
        long abY = model.verticesY[b] - (long) model.verticesY[a];
        long abZ = model.verticesZ[b] - (long) model.verticesZ[a];
        long acX = model.verticesX[c] - (long) model.verticesX[a];
        long acY = model.verticesY[c] - (long) model.verticesY[a];
        long acZ = model.verticesZ[c] - (long) model.verticesZ[a];
        long normalX = abY * acZ - abZ * acY;
        long normalY = abZ * acX - abX * acZ;
        long normalZ = abX * acY - abY * acX;
        return normalX != 0L || normalY != 0L || normalZ != 0L;
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

    private static void appendLegacyGouraudVertex(GpuVertexBuilder vertices, Model model, int vertex, int sine,
            int cosine, int worldX, int worldHeight, int worldY, int shade, int metadataByte) {
        int rawShade = shade & 0xffff;
        // Store the complete 16-bit palette index in the R/G bytes. The vertex
        // shader reconstructs it before interpolation, avoiding byte-boundary
        // artifacts that would occur if the two bytes were interpolated separately.
        int encodedRgb = (rawShade >> 8 & 0xff) << 16 | (rawShade & 0xff) << 8;
        appendVertex(vertices, model, vertex, sine, cosine, worldX, worldHeight, worldY, encodedRgb, metadataByte);
    }

    private static void appendVertex(GpuVertexBuilder vertices, Model model, int vertex, int sine, int cosine,
            int worldX, int worldHeight, int worldY, int rgb) {
        appendVertex(vertices, model, vertex, sine, cosine, worldX, worldHeight, worldY, rgb, 0xff);
    }

    private static void appendVertex(GpuVertexBuilder vertices, Model model, int vertex, int sine, int cosine,
            int worldX, int worldHeight, int worldY, int rgb, int alphaByte) {
        vertices.add(worldVertexX(model, vertex, sine, cosine, worldX), worldHeight + model.verticesY[vertex],
                worldVertexY(model, vertex, sine, cosine, worldY), rgb, alphaByte);
    }

    private static int worldVertexX(Model model, int vertex, int sine, int cosine, int worldX) {
        int localX = model.verticesX[vertex];
        int localZ = model.verticesZ[vertex];
        localX = localZ * sine + localX * cosine >> 16;
        return worldX + localX;
    }

    private static int worldVertexY(Model model, int vertex, int sine, int cosine, int worldY) {
        int localX = model.verticesX[vertex];
        int localZ = model.verticesZ[vertex];
        localZ = localZ * cosine - localX * sine >> 16;
        return worldY + localZ;
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
