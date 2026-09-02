package rs2.gpu;

import java.util.Arrays;

import rs2.media.Rasterizer3D;
import rs2.scene.Scene;
import rs2.scene.tile.ComplexTile;
import rs2.scene.tile.GenericTile;
import rs2.scene.tile.SceneTile;

/** Converts the loaded revision-377 scene terrain into GPU-friendly triangles. */
public final class GpuSceneUploader {

	private static final int TILE_SIZE = 128;
	private static final int INVISIBLE_HSL = 0xbc614e;

	private GpuSceneUploader() {
	}

	/**
	 * Builds the complete terrain mesh for the loaded local scene.
	 *
	 * <p>
	 * Unlike {@link Scene#render(int, int, int, int, int, int)}, this intentionally
	 * does not apply the legacy +/-25 tile visibility window. Plane/roof logic is
	 * retained through each tile's logic height and the scene minimum plane.
	 * </p>
	 *
	 * @param scene       loaded local scene
	 * @param renderPlane camera-selected render plane
	 * @return unindexed terrain mesh
	 */
	public static GpuTerrainMesh buildTerrain(Scene scene, int renderPlane) {
		FloatBuilder vertices = new FloatBuilder(256 * 1024);
		int surfaceCount = 0;
		int triangleCount = 0;

		int firstPlane = Math.max(0, scene.minPlane);
		int lastPlane = Math.min(scene.planeCount, scene.tiles.length);
		for (int plane = firstPlane; plane < lastPlane; plane++) {
			SceneTile[][] planeTiles = scene.tiles[plane];
			for (int x = 0; x < scene.width; x++) {
				for (int y = 0; y < scene.height; y++) {
					SceneTile tile = planeTiles[x][y];
					if (tile == null || tile.logicHeight > renderPlane) {
						continue;
					}

					if (tile.tileBelow != null) {
						int belowTriangles = appendSurface(scene, tile.tileBelow, 0, x, y, vertices);
						if (belowTriangles > 0) {
							surfaceCount++;
							triangleCount += belowTriangles;
						}
					}

					int tileTriangles = appendSurface(scene, tile, tile.renderLevel, x, y, vertices);
					if (tileTriangles > 0) {
						surfaceCount++;
						triangleCount += tileTriangles;
					}
				}
			}
		}

		return new GpuTerrainMesh(vertices.toArray(), surfaceCount, triangleCount);
	}

	private static int appendSurface(Scene scene, SceneTile tile, int heightPlane, int tileX, int tileY,
			FloatBuilder vertices) {
		if (tile.plainTile != null) {
			return appendPlainTile(scene, tile.plainTile, heightPlane, tileX, tileY, vertices);
		}
		if (tile.shapedTile != null) {
			return appendShapedTile(tile.shapedTile, vertices);
		}
		return 0;
	}

	private static int appendPlainTile(Scene scene, GenericTile tile, int plane, int tileX, int tileY,
			FloatBuilder vertices) {
		if (plane < 0 || plane >= scene.tileHeights.length) {
			return 0;
		}

		int worldX = tileX * TILE_SIZE;
		int worldY = tileY * TILE_SIZE;
		int southWestHeight = scene.tileHeights[plane][tileX][tileY];
		int southEastHeight = scene.tileHeights[plane][tileX + 1][tileY];
		int northEastHeight = scene.tileHeights[plane][tileX + 1][tileY + 1];
		int northWestHeight = scene.tileHeights[plane][tileX][tileY + 1];

		int colourA = tile.colourA;
		int colourB = tile.colourB;
		int colourC = tile.colourC;
		int colourD = tile.colourD;
		if (tile.texture >= 0 && tile.texture < Scene.TEXTURE_COLORS.length) {
			int textureColour = Scene.TEXTURE_COLORS[tile.texture];
			colourA = mixTextureColour(colourA, textureColour);
			colourB = mixTextureColour(colourB, textureColour);
			colourC = mixTextureColour(colourC, textureColour);
			colourD = mixTextureColour(colourD, textureColour);
		}

		int triangles = 0;
		// Match the software renderer's first triangle: NE, NW, SE -> C, D, B.
		if (tile.texture >= 0 || tile.colourC != INVISIBLE_HSL) {
			appendVertex(vertices, worldX + TILE_SIZE, northEastHeight, worldY + TILE_SIZE, colourC);
			appendVertex(vertices, worldX, northWestHeight, worldY + TILE_SIZE, colourD);
			appendVertex(vertices, worldX + TILE_SIZE, southEastHeight, worldY, colourB);
			triangles++;
		}

		// Match the software renderer's second triangle: SW, SE, NW -> A, B, D.
		if (tile.texture >= 0 || tile.colourA != INVISIBLE_HSL) {
			appendVertex(vertices, worldX, southWestHeight, worldY, colourA);
			appendVertex(vertices, worldX + TILE_SIZE, southEastHeight, worldY, colourB);
			appendVertex(vertices, worldX, northWestHeight, worldY + TILE_SIZE, colourD);
			triangles++;
		}
		return triangles;
	}

	private static int appendShapedTile(ComplexTile tile, FloatBuilder vertices) {
		int triangles = 0;
		for (int triangle = 0; triangle < tile.triangleVertexA.length; triangle++) {
			int colourA = tile.triangleHslA[triangle];
			int colourB = tile.triangleHslB[triangle];
			int colourC = tile.triangleHslC[triangle];
			int texture = tile.triangleTextures == null ? -1 : tile.triangleTextures[triangle];
			if (texture < 0 && colourA == INVISIBLE_HSL) {
				continue;
			}
			if (texture >= 0 && texture < Scene.TEXTURE_COLORS.length) {
				int textureColour = Scene.TEXTURE_COLORS[texture];
				colourA = mixTextureColour(colourA, textureColour);
				colourB = mixTextureColour(colourB, textureColour);
				colourC = mixTextureColour(colourC, textureColour);
			}

			appendIndexedVertex(vertices, tile, tile.triangleVertexA[triangle], colourA);
			appendIndexedVertex(vertices, tile, tile.triangleVertexB[triangle], colourB);
			appendIndexedVertex(vertices, tile, tile.triangleVertexC[triangle], colourC);
			triangles++;
		}
		return triangles;
	}

	private static void appendIndexedVertex(FloatBuilder vertices, ComplexTile tile, int index, int hsl) {
		appendVertex(vertices, tile.vertexX[index], tile.vertexY[index], tile.vertexZ[index], hsl);
	}

	private static void appendVertex(FloatBuilder vertices, int x, int height, int y, int hsl) {
		int rgb = hslToRgb(hsl);
		vertices.add(x);
		vertices.add(height);
		vertices.add(y);
		vertices.add(((rgb >> 16) & 0xff) / 255.0f);
		vertices.add(((rgb >> 8) & 0xff) / 255.0f);
		vertices.add((rgb & 0xff) / 255.0f);
	}

	private static int hslToRgb(int hsl) {
		if (hsl == INVISIBLE_HSL) {
			return 0;
		}
		int[] palette = Rasterizer3D.HSL_TO_RGB;
		if (palette == null || palette.length == 0) {
			return 0xffffff;
		}
		return palette[hsl & 0xffff];
	}

	private static int mixTextureColour(int lightness, int baseColour) {
		lightness = 127 - lightness;
		lightness = lightness * (baseColour & 0x7f) / 160;
		if (lightness < 2) {
			lightness = 2;
		} else if (lightness > 126) {
			lightness = 126;
		}
		return (baseColour & 0xff80) + lightness;
	}

	/** Small primitive float accumulator to avoid boxing hundreds of thousands of vertices. */
	private static final class FloatBuilder {
		private float[] values;
		private int size;

		FloatBuilder(int initialCapacity) {
			values = new float[Math.max(1, initialCapacity)];
		}

		void add(float value) {
			if (size == values.length) {
				values = Arrays.copyOf(values, values.length + (values.length >> 1) + 1);
			}
			values[size++] = value;
		}

		float[] toArray() {
			return Arrays.copyOf(values, size);
		}
	}
}
