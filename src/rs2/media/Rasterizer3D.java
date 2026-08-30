package rs2.media;

import rs2.cache.Archive;
import rs2.cache.media.IndexedImage;

/**
 * Revision-377 software 3D triangle rasterizer.
 *
 * <p>
 * The implementation intentionally retains the client's fixed-point scan
 * conversion, palette construction, texture LRU pool, transparency rules, and
 * low-memory 64x64 texture path. The private scan converters remain close to
 * the original arithmetic so rounding, edge ownership, and overflow behavior
 * are not changed by refactoring.
 * </p>
 */
public class Rasterizer3D extends Rasterizer {

	/**
	 * Whether low memory.
	 */
	public static boolean lowMemory = true;
	/**
	 * Clips horizontal spans to the current 2D raster bounds when set by model
	 * projection.
	 */
	public static boolean restrictEdges;
	/**
	 * True for the texture currently being rasterized when texel zero is not
	 * transparent.
	 */
	private static boolean opaqueTexture;
	/** Enables the original four-pixel Gouraud interpolation fast path. */
	public static boolean gouraudBlockShading = true;
	/** 0..255 source alpha used by flat and Gouraud scanlines. */
	public static int alpha;

	/** Stores the current center X. */
	public static int centerX;

	/** Stores the current center Y. */
	public static int centerY;

	/** Stores reciprocal15 values. */
	public static int[] reciprocal15 = new int[8192];

	/** Stores reciprocal16 values. */
	public static int[] reciprocal16 = new int[2048];

	/** Constant value for sine. */
	public static int[] SINE = new int[2048];

	/** Constant value for cosine. */
	public static int[] COSINE = new int[2048];

	/** Stores scanline offsets values. */
	public static int[] scanlineOffsets;

	/**
	 * Number of loaded texture entries.
	 */
	private static int loadedTextureCount;

	/** Stores textures values. */
	public static IndexedImage[] textures = new IndexedImage[50];

	/** Whether texture has transparency is enabled or active. */
	private static boolean[] textureHasTransparency = new boolean[50];

	/** Stores average texture colors values. */
	private static int[] averageTextureColors = new int[50];

	/** Stores the current texture pool available. */
	private static int texturePoolAvailable;

	/** Stores texture pool values. */
	private static int[][] texturePool;

	/** Stores texture pixels values. */
	private static int[][] texturePixels = new int[50][];

	/** Stores texture last used values. */
	public static int[] textureLastUsed = new int[50];

	/** Stores the current texture cycle. */
	public static int textureCycle;

	/** Constant value for hsl to RGB. */
	public static int[] HSL_TO_RGB = new int[0x10000];

	/** Stores texture palettes values. */
	private static int[][] texturePalettes = new int[50][];

	static {
		for (int loopIndex = 1; loopIndex < reciprocal15.length; loopIndex++)
			reciprocal15[loopIndex] = 32768 / loopIndex;
		for (int loopIndex2 = 1; loopIndex2 < 2048; loopIndex2++)
			reciprocal16[loopIndex2] = 0x10000 / loopIndex2;
		for (int angle = 0; angle < 2048; angle++) {
			SINE[angle] = (int) (65536D * Math.sin(angle * 0.0030679614999999999D));
			COSINE[angle] = (int) (65536D * Math.cos(angle * 0.0030679614999999999D));
		}
	}

	/**
	 * Creates a new rasterizer3 d.
	 */
	private Rasterizer3D() {
	}

	/**
	 * Releases static rendering and texture tables, matching the original explicit
	 * teardown.
	 */
	public static void clear() {
		reciprocal15 = null;
		// The original decompilation assigned the first reciprocal table twice and
		// never
		// nulled reciprocal16 here. Preserve that exact teardown quirk.
		SINE = null;
		COSINE = null;
		scanlineOffsets = null;
		textures = null;
		textureHasTransparency = null;
		averageTextureColors = null;
		texturePool = null;
		texturePixels = null;
		textureLastUsed = null;
		HSL_TO_RGB = null;
		texturePalettes = null;
	}

	/**
	 * Builds scanline offsets from the current {@link Rasterizer} dimensions.
	 */
	public static void setDefaultBounds() {
		scanlineOffsets = new int[Rasterizer.height];
		for (int y = 0; y < Rasterizer.height; y++)
			scanlineOffsets[y] = Rasterizer.width * y;
		centerX = Rasterizer.width / 2;
		centerY = Rasterizer.height / 2;
	}

	/**
	 * Builds projection scanline offsets for an explicit viewport.
	 *
	 * @param width  the width
	 * @param height the height
	 */
	public static void setBounds(int width, int height) {
		scanlineOffsets = new int[height];
		for (int y = 0; y < height; y++)
			scanlineOffsets[y] = width * y;
		centerX = width / 2;
		centerY = height / 2;
	}

	/**
	 * Drops pooled and resident expanded texture texels without unloading indexed
	 * images.
	 */
	public static void clearTextureCache() {
		texturePool = null;
		for (int textureId = 0; textureId < 50; textureId++)
			texturePixels[textureId] = null;
	}

	/**
	 * Allocates the reusable expanded-texel pool if it has not already been
	 * allocated.
	 *
	 * @param capacity the capacity
	 */
	public static void initializeTexturePool(int capacity) {
		if (texturePool != null)
			return;
		texturePoolAvailable = capacity;
		texturePool = new int[capacity][lowMemory ? 16384 : 0x10000];
		for (int textureId = 0; textureId < 50; textureId++)
			texturePixels[textureId] = null;
	}

	/**
	 * Loads numbered indexed textures 0..49; absent entries are silently skipped as
	 * in 377.
	 *
	 * @param archive the archive
	 */
	public static void loadTextures(Archive archive) {
		loadedTextureCount = 0;
		for (int textureId = 0; textureId < 50; textureId++) {
			try {
				textures[textureId] = new IndexedImage(archive, String.valueOf(textureId), 0);
				if (lowMemory && textures[textureId].maxWidth == 128)
					textures[textureId].resizeToHalf();
				else
					textures[textureId].resizeToCanvas();
				loadedTextureCount++;
			} catch (Exception ignored) {
				// Missing texture entries are valid in the original numbered archive.
			}
		}
	}

	/**
	 * Returns the cached gamma-adjusted average palette colour for a texture.
	 *
	 * @param textureId the texture id
	 * @return the average texture color
	 */
	public static int getAverageTextureColor(int textureId) {
		if (averageTextureColors[textureId] != 0)
			return averageTextureColors[textureId];
		int red = 0, green = 0, blue = 0;
		int[] palette = texturePalettes[textureId];
		for (int rgb : palette) {
			red += rgb >> 16 & 0xff;
			green += rgb >> 8 & 0xff;
			blue += rgb & 0xff;
		}
		int rgb = (red / palette.length << 16) + (green / palette.length << 8) + blue / palette.length;
		rgb = adjustBrightness(rgb, 1.3999999999999999D);
		if (rgb == 0)
			rgb = 1;
		return averageTextureColors[textureId] = rgb;
	}

	/**
	 * Returns an expanded texture buffer to the shared pool.
	 *
	 * @param textureId the texture id
	 */
	public static void releaseTexture(int textureId) {
		if (texturePixels[textureId] == null)
			return;
		texturePool[texturePoolAvailable++] = texturePixels[textureId];
		texturePixels[textureId] = null;
	}

	/**
	 * Returns texture pixels.
	 *
	 * @return the resulting int array
	 * @param textureId the texture id
	 */
	private static int[] getTexturePixels(int textureId) {
		textureLastUsed[textureId] = textureCycle++;
		if (texturePixels[textureId] != null)
			return texturePixels[textureId];
		int[] texels;
		if (texturePoolAvailable > 0) {
			texels = texturePool[--texturePoolAvailable];
			texturePool[texturePoolAvailable] = null;
		} else {
			int oldestCycle = 0;
			int oldestTexture = -1;
			for (int candidate = 0; candidate < loadedTextureCount; candidate++) {
				if (texturePixels[candidate] != null
						&& (textureLastUsed[candidate] < oldestCycle || oldestTexture == -1)) {
					oldestCycle = textureLastUsed[candidate];
					oldestTexture = candidate;
				}
			}
			texels = texturePixels[oldestTexture];
			texturePixels[oldestTexture] = null;
		}
		texturePixels[textureId] = texels;
		IndexedImage texture = textures[textureId];
		int[] palette = texturePalettes[textureId];
		if (lowMemory) {
			textureHasTransparency[textureId] = false;
			for (int pixel = 0; pixel < 4096; pixel++) {
				int rgb = texels[pixel] = palette[texture.pixels[pixel]] & 0xf8f8ff;
				if (rgb == 0)
					textureHasTransparency[textureId] = true;
				texels[4096 + pixel] = rgb - (rgb >>> 3) & 0xf8f8ff;
				texels[8192 + pixel] = rgb - (rgb >>> 2) & 0xf8f8ff;
				texels[12288 + pixel] = rgb - (rgb >>> 2) - (rgb >>> 3) & 0xf8f8ff;
			}
		} else {
			if (texture.width == 64) {
				for (int y = 0; y < 128; y++)
					for (int x = 0; x < 128; x++)
						texels[x + (y << 7)] = palette[texture.pixels[(x >> 1) + ((y >> 1) << 6)]];
			} else {
				for (int pixel = 0; pixel < 16384; pixel++)
					texels[pixel] = palette[texture.pixels[pixel]];
			}
			textureHasTransparency[textureId] = false;
			for (int pixel = 0; pixel < 16384; pixel++) {
				texels[pixel] &= 0xf8f8ff;
				int rgb = texels[pixel];
				if (rgb == 0)
					textureHasTransparency[textureId] = true;
				texels[16384 + pixel] = rgb - (rgb >>> 3) & 0xf8f8ff;
				texels[32768 + pixel] = rgb - (rgb >>> 2) & 0xf8f8ff;
				texels[49152 + pixel] = rgb - (rgb >>> 2) - (rgb >>> 3) & 0xf8f8ff;
			}
		}
		return texels;
	}

	/**
	 * Rebuilds the HSL and texture palettes using the client's randomized
	 * brightness jitter.
	 *
	 * @param brightness the brightness
	 */
	public static void setBrightness(double brightness) {
		brightness += Math.random() * 0.029999999999999999D - 0.014999999999999999D;
		int paletteIndex = 0;
		for (int hueSat = 0; hueSat < 512; hueSat++) {
			double hue = (double) (hueSat / 8) / 64D + 0.0078125D;
			double saturation = (double) (hueSat & 7) / 8D + 0.0625D;
			for (int lightnessIndex = 0; lightnessIndex < 128; lightnessIndex++) {
				double lightness = (double) lightnessIndex / 128D;
				double red = lightness, green = lightness, blue = lightness;
				if (saturation != 0.0D) {
					double upperComponent = lightness < 0.5D ? lightness * (1.0D + saturation)
							: (lightness + saturation) - lightness * saturation;
					double lowerComponent = 2D * lightness - upperComponent;
					red = hueToRgb(lowerComponent, upperComponent, hue + 0.33333333333333331D);
					green = hueToRgb(lowerComponent, upperComponent, hue);
					blue = hueToRgb(lowerComponent, upperComponent, hue - 0.33333333333333331D);
				}
				int rgb = ((int) (red * 256D) << 16) + ((int) (green * 256D) << 8) + (int) (blue * 256D);
				rgb = adjustBrightness(rgb, brightness);
				if (rgb == 0)
					rgb = 1;
				HSL_TO_RGB[paletteIndex++] = rgb;
			}
		}
		for (int textureId = 0; textureId < 50; textureId++) {
			if (textures[textureId] != null) {
				int[] source = textures[textureId].palette;
				texturePalettes[textureId] = new int[source.length];
				for (int loopIndex = 0; loopIndex < source.length; loopIndex++) {
					int rgb = adjustBrightness(source[loopIndex], brightness);
					if ((rgb & 0xf8f8ff) == 0 && loopIndex != 0)
						rgb = 1;
					texturePalettes[textureId][loopIndex] = rgb;
				}
			}
		}
		for (int textureId = 0; textureId < 50; textureId++)
			releaseTexture(textureId);
	}

	/**
	 * Performs hue to rgb.
	 *
	 * @return the resulting double
	 * @param lowerComponent the lower component
	 * @param upperComponent the upper component
	 * @param hue            the hue
	 */
	private static double hueToRgb(double lowerComponent, double upperComponent, double hue) {
		if (hue > 1.0D)
			hue--;
		if (hue < 0.0D)
			hue++;
		if (6D * hue < 1.0D)
			return lowerComponent + (upperComponent - lowerComponent) * 6D * hue;
		if (2D * hue < 1.0D)
			return upperComponent;
		if (3D * hue < 2D)
			return lowerComponent + (upperComponent - lowerComponent) * (0.66666666666666663D - hue) * 6D;
		return lowerComponent;
	}

	/**
	 * Performs adjust brightness.
	 *
	 * @return the resulting int
	 * @param rgb        the rgb
	 * @param brightness the brightness
	 */
	public static int adjustBrightness(int rgb, double brightness) {
		double red = Math.pow((double) (rgb >> 16) / 256D, brightness);
		double green = Math.pow((double) (rgb >> 8 & 0xff) / 256D, brightness);
		double blue = Math.pow((double) (rgb & 0xff) / 256D, brightness);
		return ((int) (red * 256D) << 16) + ((int) (green * 256D) << 8) + (int) (blue * 256D);
	}

	/**
	 * Draws gouraud triangle.
	 *
	 * @param yA     the y a
	 * @param yB     the y b
	 * @param yC     the y c
	 * @param xA     the x a
	 * @param xB     the x b
	 * @param xC     the x c
	 * @param shadeA the shade a
	 * @param shadeB the shade b
	 * @param shadeC the shade c
	 */
	public static void drawGouraudTriangle(int yA, int yB, int yC, int xA, int xB, int xC, int shadeA, int shadeB,
			int shadeC) {
		drawGouraudTriangleInternal(yA, yB, yC, xA, xB, xC, shadeA, shadeB, shadeC);
	}

	/**
	 * Draws flat triangle.
	 *
	 * @param yA  the y a
	 * @param yB  the y b
	 * @param yC  the y c
	 * @param xA  the x a
	 * @param xB  the x b
	 * @param xC  the x c
	 * @param rgb the rgb
	 */
	public static void drawFlatTriangle(int yA, int yB, int yC, int xA, int xB, int xC, int rgb) {
		drawFlatTriangleInternal(yA, yB, yC, xA, xB, xC, rgb);
	}

	/**
	 * Draws textured triangle.
	 *
	 * @param yA        the y a
	 * @param yB        the y b
	 * @param yC        the y c
	 * @param xA        the x a
	 * @param xB        the x b
	 * @param xC        the x c
	 * @param shadeA    the shade a
	 * @param shadeB    the shade b
	 * @param shadeC    the shade c
	 * @param textureXA the texture xa
	 * @param textureXB the texture xb
	 * @param textureXC the texture xc
	 * @param textureYA the texture ya
	 * @param textureYB the texture yb
	 * @param textureYC the texture yc
	 * @param textureZA the texture za
	 * @param textureZB the texture zb
	 * @param textureZC the texture zc
	 * @param textureId the texture id
	 */
	public static void drawTexturedTriangle(int yA, int yB, int yC, int xA, int xB, int xC, int shadeA, int shadeB,
			int shadeC, int textureXA, int textureXB, int textureXC, int textureYA, int textureYB, int textureYC,
			int textureZA, int textureZB, int textureZC, int textureId) {
		drawTexturedTriangleInternal(yA, yB, yC, xA, xB, xC, shadeA, shadeB, shadeC, textureXA, textureXB, textureXC,
				textureYA, textureYB, textureYC, textureZA, textureZB, textureZC, textureId);
	}

	/**
	 * Draws gouraud triangle internal.
	 *
	 * @param inputValue  the input value
	 * @param inputValue2 the input value2
	 * @param inputValue3 the input value3
	 * @param inputValue4 the input value4
	 * @param inputValue5 the input value5
	 * @param inputValue6 the input value6
	 * @param inputValue7 the input value7
	 * @param inputValue8 the input value8
	 * @param inputValue9 the input value9
	 */
	private static void drawGouraudTriangleInternal(int inputValue, int inputValue2, int inputValue3, int inputValue4,
			int inputValue5, int inputValue6, int inputValue7, int inputValue8, int inputValue9) {
		int intermediateValue = 0;
		int intermediateValue2 = 0;
		if (inputValue2 != inputValue) {
			intermediateValue = (inputValue5 - inputValue4 << 16) / (inputValue2 - inputValue);
			intermediateValue2 = (inputValue8 - inputValue7 << 15) / (inputValue2 - inputValue);
		}
		int intermediateValue3 = 0;
		int intermediateValue4 = 0;
		if (inputValue3 != inputValue2) {
			intermediateValue3 = (inputValue6 - inputValue5 << 16) / (inputValue3 - inputValue2);
			intermediateValue4 = (inputValue9 - inputValue8 << 15) / (inputValue3 - inputValue2);
		}
		int intermediateValue5 = 0;
		int intermediateValue6 = 0;
		if (inputValue3 != inputValue) {
			intermediateValue5 = (inputValue4 - inputValue6 << 16) / (inputValue - inputValue3);
			intermediateValue6 = (inputValue7 - inputValue9 << 15) / (inputValue - inputValue3);
		}
		if (inputValue <= inputValue2 && inputValue <= inputValue3) {
			if (inputValue >= Rasterizer.bottomY)
				return;
			if (inputValue2 > Rasterizer.bottomY)
				inputValue2 = Rasterizer.bottomY;
			if (inputValue3 > Rasterizer.bottomY)
				inputValue3 = Rasterizer.bottomY;
			if (inputValue2 < inputValue3) {
				inputValue6 = inputValue4 <<= 16;
				inputValue9 = inputValue7 <<= 15;
				if (inputValue < 0) {
					inputValue6 -= intermediateValue5 * inputValue;
					inputValue4 -= intermediateValue * inputValue;
					inputValue9 -= intermediateValue6 * inputValue;
					inputValue7 -= intermediateValue2 * inputValue;
					inputValue = 0;
				}
				inputValue5 <<= 16;
				inputValue8 <<= 15;
				if (inputValue2 < 0) {
					inputValue5 -= intermediateValue3 * inputValue2;
					inputValue8 -= intermediateValue4 * inputValue2;
					inputValue2 = 0;
				}
				if (inputValue != inputValue2 && intermediateValue5 < intermediateValue
						|| inputValue == inputValue2 && intermediateValue5 > intermediateValue3) {
					inputValue3 -= inputValue2;
					inputValue2 -= inputValue;
					for (inputValue = scanlineOffsets[inputValue]; --inputValue2 >= 0; inputValue += Rasterizer.width) {
						drawGouraudScanline(Rasterizer.pixels, inputValue, 0, 0, inputValue6 >> 16, inputValue4 >> 16,
								inputValue9 >> 7, inputValue7 >> 7);
						inputValue6 += intermediateValue5;
						inputValue4 += intermediateValue;
						inputValue9 += intermediateValue6;
						inputValue7 += intermediateValue2;
					}

					while (--inputValue3 >= 0) {
						drawGouraudScanline(Rasterizer.pixels, inputValue, 0, 0, inputValue6 >> 16, inputValue5 >> 16,
								inputValue9 >> 7, inputValue8 >> 7);
						inputValue6 += intermediateValue5;
						inputValue5 += intermediateValue3;
						inputValue9 += intermediateValue6;
						inputValue8 += intermediateValue4;
						inputValue += Rasterizer.width;
					}
					return;
				}
				inputValue3 -= inputValue2;
				inputValue2 -= inputValue;
				for (inputValue = scanlineOffsets[inputValue]; --inputValue2 >= 0; inputValue += Rasterizer.width) {
					drawGouraudScanline(Rasterizer.pixels, inputValue, 0, 0, inputValue4 >> 16, inputValue6 >> 16,
							inputValue7 >> 7, inputValue9 >> 7);
					inputValue6 += intermediateValue5;
					inputValue4 += intermediateValue;
					inputValue9 += intermediateValue6;
					inputValue7 += intermediateValue2;
				}

				while (--inputValue3 >= 0) {
					drawGouraudScanline(Rasterizer.pixels, inputValue, 0, 0, inputValue5 >> 16, inputValue6 >> 16,
							inputValue8 >> 7, inputValue9 >> 7);
					inputValue6 += intermediateValue5;
					inputValue5 += intermediateValue3;
					inputValue9 += intermediateValue6;
					inputValue8 += intermediateValue4;
					inputValue += Rasterizer.width;
				}
				return;
			}
			inputValue5 = inputValue4 <<= 16;
			inputValue8 = inputValue7 <<= 15;
			if (inputValue < 0) {
				inputValue5 -= intermediateValue5 * inputValue;
				inputValue4 -= intermediateValue * inputValue;
				inputValue8 -= intermediateValue6 * inputValue;
				inputValue7 -= intermediateValue2 * inputValue;
				inputValue = 0;
			}
			inputValue6 <<= 16;
			inputValue9 <<= 15;
			if (inputValue3 < 0) {
				inputValue6 -= intermediateValue3 * inputValue3;
				inputValue9 -= intermediateValue4 * inputValue3;
				inputValue3 = 0;
			}
			if (inputValue != inputValue3 && intermediateValue5 < intermediateValue
					|| inputValue == inputValue3 && intermediateValue3 > intermediateValue) {
				inputValue2 -= inputValue3;
				inputValue3 -= inputValue;
				for (inputValue = scanlineOffsets[inputValue]; --inputValue3 >= 0; inputValue += Rasterizer.width) {
					drawGouraudScanline(Rasterizer.pixels, inputValue, 0, 0, inputValue5 >> 16, inputValue4 >> 16,
							inputValue8 >> 7, inputValue7 >> 7);
					inputValue5 += intermediateValue5;
					inputValue4 += intermediateValue;
					inputValue8 += intermediateValue6;
					inputValue7 += intermediateValue2;
				}

				while (--inputValue2 >= 0) {
					drawGouraudScanline(Rasterizer.pixels, inputValue, 0, 0, inputValue6 >> 16, inputValue4 >> 16,
							inputValue9 >> 7, inputValue7 >> 7);
					inputValue6 += intermediateValue3;
					inputValue4 += intermediateValue;
					inputValue9 += intermediateValue4;
					inputValue7 += intermediateValue2;
					inputValue += Rasterizer.width;
				}
				return;
			}
			inputValue2 -= inputValue3;
			inputValue3 -= inputValue;
			for (inputValue = scanlineOffsets[inputValue]; --inputValue3 >= 0; inputValue += Rasterizer.width) {
				drawGouraudScanline(Rasterizer.pixels, inputValue, 0, 0, inputValue4 >> 16, inputValue5 >> 16,
						inputValue7 >> 7, inputValue8 >> 7);
				inputValue5 += intermediateValue5;
				inputValue4 += intermediateValue;
				inputValue8 += intermediateValue6;
				inputValue7 += intermediateValue2;
			}

			while (--inputValue2 >= 0) {
				drawGouraudScanline(Rasterizer.pixels, inputValue, 0, 0, inputValue4 >> 16, inputValue6 >> 16,
						inputValue7 >> 7, inputValue9 >> 7);
				inputValue6 += intermediateValue3;
				inputValue4 += intermediateValue;
				inputValue9 += intermediateValue4;
				inputValue7 += intermediateValue2;
				inputValue += Rasterizer.width;
			}
			return;
		}
		if (inputValue2 <= inputValue3) {
			if (inputValue2 >= Rasterizer.bottomY)
				return;
			if (inputValue3 > Rasterizer.bottomY)
				inputValue3 = Rasterizer.bottomY;
			if (inputValue > Rasterizer.bottomY)
				inputValue = Rasterizer.bottomY;
			if (inputValue3 < inputValue) {
				inputValue4 = inputValue5 <<= 16;
				inputValue7 = inputValue8 <<= 15;
				if (inputValue2 < 0) {
					inputValue4 -= intermediateValue * inputValue2;
					inputValue5 -= intermediateValue3 * inputValue2;
					inputValue7 -= intermediateValue2 * inputValue2;
					inputValue8 -= intermediateValue4 * inputValue2;
					inputValue2 = 0;
				}
				inputValue6 <<= 16;
				inputValue9 <<= 15;
				if (inputValue3 < 0) {
					inputValue6 -= intermediateValue5 * inputValue3;
					inputValue9 -= intermediateValue6 * inputValue3;
					inputValue3 = 0;
				}
				if (inputValue2 != inputValue3 && intermediateValue < intermediateValue3
						|| inputValue2 == inputValue3 && intermediateValue > intermediateValue5) {
					inputValue -= inputValue3;
					inputValue3 -= inputValue2;
					for (inputValue2 = scanlineOffsets[inputValue2]; --inputValue3 >= 0; inputValue2 += Rasterizer.width) {
						drawGouraudScanline(Rasterizer.pixels, inputValue2, 0, 0, inputValue4 >> 16, inputValue5 >> 16,
								inputValue7 >> 7, inputValue8 >> 7);
						inputValue4 += intermediateValue;
						inputValue5 += intermediateValue3;
						inputValue7 += intermediateValue2;
						inputValue8 += intermediateValue4;
					}

					while (--inputValue >= 0) {
						drawGouraudScanline(Rasterizer.pixels, inputValue2, 0, 0, inputValue4 >> 16, inputValue6 >> 16,
								inputValue7 >> 7, inputValue9 >> 7);
						inputValue4 += intermediateValue;
						inputValue6 += intermediateValue5;
						inputValue7 += intermediateValue2;
						inputValue9 += intermediateValue6;
						inputValue2 += Rasterizer.width;
					}
					return;
				}
				inputValue -= inputValue3;
				inputValue3 -= inputValue2;
				for (inputValue2 = scanlineOffsets[inputValue2]; --inputValue3 >= 0; inputValue2 += Rasterizer.width) {
					drawGouraudScanline(Rasterizer.pixels, inputValue2, 0, 0, inputValue5 >> 16, inputValue4 >> 16,
							inputValue8 >> 7, inputValue7 >> 7);
					inputValue4 += intermediateValue;
					inputValue5 += intermediateValue3;
					inputValue7 += intermediateValue2;
					inputValue8 += intermediateValue4;
				}

				while (--inputValue >= 0) {
					drawGouraudScanline(Rasterizer.pixels, inputValue2, 0, 0, inputValue6 >> 16, inputValue4 >> 16,
							inputValue9 >> 7, inputValue7 >> 7);
					inputValue4 += intermediateValue;
					inputValue6 += intermediateValue5;
					inputValue7 += intermediateValue2;
					inputValue9 += intermediateValue6;
					inputValue2 += Rasterizer.width;
				}
				return;
			}
			inputValue6 = inputValue5 <<= 16;
			inputValue9 = inputValue8 <<= 15;
			if (inputValue2 < 0) {
				inputValue6 -= intermediateValue * inputValue2;
				inputValue5 -= intermediateValue3 * inputValue2;
				inputValue9 -= intermediateValue2 * inputValue2;
				inputValue8 -= intermediateValue4 * inputValue2;
				inputValue2 = 0;
			}
			inputValue4 <<= 16;
			inputValue7 <<= 15;
			if (inputValue < 0) {
				inputValue4 -= intermediateValue5 * inputValue;
				inputValue7 -= intermediateValue6 * inputValue;
				inputValue = 0;
			}
			if (intermediateValue < intermediateValue3) {
				inputValue3 -= inputValue;
				inputValue -= inputValue2;
				for (inputValue2 = scanlineOffsets[inputValue2]; --inputValue >= 0; inputValue2 += Rasterizer.width) {
					drawGouraudScanline(Rasterizer.pixels, inputValue2, 0, 0, inputValue6 >> 16, inputValue5 >> 16,
							inputValue9 >> 7, inputValue8 >> 7);
					inputValue6 += intermediateValue;
					inputValue5 += intermediateValue3;
					inputValue9 += intermediateValue2;
					inputValue8 += intermediateValue4;
				}

				while (--inputValue3 >= 0) {
					drawGouraudScanline(Rasterizer.pixels, inputValue2, 0, 0, inputValue4 >> 16, inputValue5 >> 16,
							inputValue7 >> 7, inputValue8 >> 7);
					inputValue4 += intermediateValue5;
					inputValue5 += intermediateValue3;
					inputValue7 += intermediateValue6;
					inputValue8 += intermediateValue4;
					inputValue2 += Rasterizer.width;
				}
				return;
			}
			inputValue3 -= inputValue;
			inputValue -= inputValue2;
			for (inputValue2 = scanlineOffsets[inputValue2]; --inputValue >= 0; inputValue2 += Rasterizer.width) {
				drawGouraudScanline(Rasterizer.pixels, inputValue2, 0, 0, inputValue5 >> 16, inputValue6 >> 16,
						inputValue8 >> 7, inputValue9 >> 7);
				inputValue6 += intermediateValue;
				inputValue5 += intermediateValue3;
				inputValue9 += intermediateValue2;
				inputValue8 += intermediateValue4;
			}

			while (--inputValue3 >= 0) {
				drawGouraudScanline(Rasterizer.pixels, inputValue2, 0, 0, inputValue5 >> 16, inputValue4 >> 16,
						inputValue8 >> 7, inputValue7 >> 7);
				inputValue4 += intermediateValue5;
				inputValue5 += intermediateValue3;
				inputValue7 += intermediateValue6;
				inputValue8 += intermediateValue4;
				inputValue2 += Rasterizer.width;
			}
			return;
		}
		if (inputValue3 >= Rasterizer.bottomY)
			return;
		if (inputValue > Rasterizer.bottomY)
			inputValue = Rasterizer.bottomY;
		if (inputValue2 > Rasterizer.bottomY)
			inputValue2 = Rasterizer.bottomY;
		if (inputValue < inputValue2) {
			inputValue5 = inputValue6 <<= 16;
			inputValue8 = inputValue9 <<= 15;
			if (inputValue3 < 0) {
				inputValue5 -= intermediateValue3 * inputValue3;
				inputValue6 -= intermediateValue5 * inputValue3;
				inputValue8 -= intermediateValue4 * inputValue3;
				inputValue9 -= intermediateValue6 * inputValue3;
				inputValue3 = 0;
			}
			inputValue4 <<= 16;
			inputValue7 <<= 15;
			if (inputValue < 0) {
				inputValue4 -= intermediateValue * inputValue;
				inputValue7 -= intermediateValue2 * inputValue;
				inputValue = 0;
			}
			if (intermediateValue3 < intermediateValue5) {
				inputValue2 -= inputValue;
				inputValue -= inputValue3;
				for (inputValue3 = scanlineOffsets[inputValue3]; --inputValue >= 0; inputValue3 += Rasterizer.width) {
					drawGouraudScanline(Rasterizer.pixels, inputValue3, 0, 0, inputValue5 >> 16, inputValue6 >> 16,
							inputValue8 >> 7, inputValue9 >> 7);
					inputValue5 += intermediateValue3;
					inputValue6 += intermediateValue5;
					inputValue8 += intermediateValue4;
					inputValue9 += intermediateValue6;
				}

				while (--inputValue2 >= 0) {
					drawGouraudScanline(Rasterizer.pixels, inputValue3, 0, 0, inputValue5 >> 16, inputValue4 >> 16,
							inputValue8 >> 7, inputValue7 >> 7);
					inputValue5 += intermediateValue3;
					inputValue4 += intermediateValue;
					inputValue8 += intermediateValue4;
					inputValue7 += intermediateValue2;
					inputValue3 += Rasterizer.width;
				}
				return;
			}
			inputValue2 -= inputValue;
			inputValue -= inputValue3;
			for (inputValue3 = scanlineOffsets[inputValue3]; --inputValue >= 0; inputValue3 += Rasterizer.width) {
				drawGouraudScanline(Rasterizer.pixels, inputValue3, 0, 0, inputValue6 >> 16, inputValue5 >> 16,
						inputValue9 >> 7, inputValue8 >> 7);
				inputValue5 += intermediateValue3;
				inputValue6 += intermediateValue5;
				inputValue8 += intermediateValue4;
				inputValue9 += intermediateValue6;
			}

			while (--inputValue2 >= 0) {
				drawGouraudScanline(Rasterizer.pixels, inputValue3, 0, 0, inputValue4 >> 16, inputValue5 >> 16,
						inputValue7 >> 7, inputValue8 >> 7);
				inputValue5 += intermediateValue3;
				inputValue4 += intermediateValue;
				inputValue8 += intermediateValue4;
				inputValue7 += intermediateValue2;
				inputValue3 += Rasterizer.width;
			}
			return;
		}
		inputValue4 = inputValue6 <<= 16;
		inputValue7 = inputValue9 <<= 15;
		if (inputValue3 < 0) {
			inputValue4 -= intermediateValue3 * inputValue3;
			inputValue6 -= intermediateValue5 * inputValue3;
			inputValue7 -= intermediateValue4 * inputValue3;
			inputValue9 -= intermediateValue6 * inputValue3;
			inputValue3 = 0;
		}
		inputValue5 <<= 16;
		inputValue8 <<= 15;
		if (inputValue2 < 0) {
			inputValue5 -= intermediateValue * inputValue2;
			inputValue8 -= intermediateValue2 * inputValue2;
			inputValue2 = 0;
		}
		if (intermediateValue3 < intermediateValue5) {
			inputValue -= inputValue2;
			inputValue2 -= inputValue3;
			for (inputValue3 = scanlineOffsets[inputValue3]; --inputValue2 >= 0; inputValue3 += Rasterizer.width) {
				drawGouraudScanline(Rasterizer.pixels, inputValue3, 0, 0, inputValue4 >> 16, inputValue6 >> 16,
						inputValue7 >> 7, inputValue9 >> 7);
				inputValue4 += intermediateValue3;
				inputValue6 += intermediateValue5;
				inputValue7 += intermediateValue4;
				inputValue9 += intermediateValue6;
			}

			while (--inputValue >= 0) {
				drawGouraudScanline(Rasterizer.pixels, inputValue3, 0, 0, inputValue5 >> 16, inputValue6 >> 16,
						inputValue8 >> 7, inputValue9 >> 7);
				inputValue5 += intermediateValue;
				inputValue6 += intermediateValue5;
				inputValue8 += intermediateValue2;
				inputValue9 += intermediateValue6;
				inputValue3 += Rasterizer.width;
			}
			return;
		}
		inputValue -= inputValue2;
		inputValue2 -= inputValue3;
		for (inputValue3 = scanlineOffsets[inputValue3]; --inputValue2 >= 0; inputValue3 += Rasterizer.width) {
			drawGouraudScanline(Rasterizer.pixels, inputValue3, 0, 0, inputValue6 >> 16, inputValue4 >> 16,
					inputValue9 >> 7, inputValue7 >> 7);
			inputValue4 += intermediateValue3;
			inputValue6 += intermediateValue5;
			inputValue7 += intermediateValue4;
			inputValue9 += intermediateValue6;
		}

		while (--inputValue >= 0) {
			drawGouraudScanline(Rasterizer.pixels, inputValue3, 0, 0, inputValue6 >> 16, inputValue5 >> 16,
					inputValue9 >> 7, inputValue8 >> 7);
			inputValue5 += intermediateValue;
			inputValue6 += intermediateValue5;
			inputValue8 += intermediateValue2;
			inputValue9 += intermediateValue6;
			inputValue3 += Rasterizer.width;
		}
	}

	/**
	 * Draws gouraud scanline.
	 *
	 * @param pixels          destination pixel buffer
	 * @param offset          scanline base offset
	 * @param rgb             scratch RGB value retained by the original scanline loop
	 * @param pixelGroupCount scratch group/pixel counter retained by the original loop
	 * @param xStart          inclusive span start
	 * @param xEnd            exclusive span end
	 * @param shadeStart      starting fixed-point shade
	 * @param shadeEnd        ending fixed-point shade
	 */
	private static void drawGouraudScanline(int pixels[], int offset, int rgb, int pixelGroupCount,
			int xStart, int xEnd, int shadeStart, int shadeEnd) {
		if (gouraudBlockShading) {
			int shadeStep;
			if (restrictEdges) {
				if (xEnd - xStart > 3)
					shadeStep = (shadeEnd - shadeStart) / (xEnd - xStart);
				else
					shadeStep = 0;
				if (xEnd > Rasterizer.viewportRx)
					xEnd = Rasterizer.viewportRx;
				if (xStart < 0) {
					shadeStart -= xStart * shadeStep;
					xStart = 0;
				}
				if (xStart >= xEnd)
					return;
				offset += xStart;
				pixelGroupCount = xEnd - xStart >> 2;
				shadeStep <<= 2;
			} else {
				if (xStart >= xEnd)
					return;
				offset += xStart;
				pixelGroupCount = xEnd - xStart >> 2;
				if (pixelGroupCount > 0)
					shadeStep = (shadeEnd - shadeStart) * reciprocal15[pixelGroupCount] >> 15;
				else
					shadeStep = 0;
			}
			if (alpha == 0) {
				while (--pixelGroupCount >= 0) {
					rgb = HSL_TO_RGB[shadeStart >> 8];
					shadeStart += shadeStep;
					pixels[offset++] = rgb;
					pixels[offset++] = rgb;
					pixels[offset++] = rgb;
					pixels[offset++] = rgb;
				}
				pixelGroupCount = xEnd - xStart & 3;
				if (pixelGroupCount > 0) {
					rgb = HSL_TO_RGB[shadeStart >> 8];
					do
						pixels[offset++] = rgb;
					while (--pixelGroupCount > 0);
					return;
				}
			} else {
				int destinationAlpha = alpha;
				int sourceAlpha = 256 - alpha;
				while (--pixelGroupCount >= 0) {
					rgb = HSL_TO_RGB[shadeStart >> 8];
					shadeStart += shadeStep;
					rgb = ((rgb & 0xff00ff) * sourceAlpha >> 8 & 0xff00ff)
							+ ((rgb & 0xff00) * sourceAlpha >> 8 & 0xff00);
					pixels[offset++] = rgb
							+ ((pixels[offset] & 0xff00ff) * destinationAlpha >> 8 & 0xff00ff)
							+ ((pixels[offset] & 0xff00) * destinationAlpha >> 8 & 0xff00);
					pixels[offset++] = rgb
							+ ((pixels[offset] & 0xff00ff) * destinationAlpha >> 8 & 0xff00ff)
							+ ((pixels[offset] & 0xff00) * destinationAlpha >> 8 & 0xff00);
					pixels[offset++] = rgb
							+ ((pixels[offset] & 0xff00ff) * destinationAlpha >> 8 & 0xff00ff)
							+ ((pixels[offset] & 0xff00) * destinationAlpha >> 8 & 0xff00);
					pixels[offset++] = rgb
							+ ((pixels[offset] & 0xff00ff) * destinationAlpha >> 8 & 0xff00ff)
							+ ((pixels[offset] & 0xff00) * destinationAlpha >> 8 & 0xff00);
				}
				pixelGroupCount = xEnd - xStart & 3;
				if (pixelGroupCount > 0) {
					rgb = HSL_TO_RGB[shadeStart >> 8];
					rgb = ((rgb & 0xff00ff) * sourceAlpha >> 8 & 0xff00ff)
							+ ((rgb & 0xff00) * sourceAlpha >> 8 & 0xff00);
					do
						pixels[offset++] = rgb
								+ ((pixels[offset] & 0xff00ff) * destinationAlpha >> 8 & 0xff00ff)
								+ ((pixels[offset] & 0xff00) * destinationAlpha >> 8 & 0xff00);
					while (--pixelGroupCount > 0);
				}
			}
			return;
		}
		if (xStart >= xEnd)
			return;
		int shadeStepScalar = (shadeEnd - shadeStart) / (xEnd - xStart);
		if (restrictEdges) {
			if (xEnd > Rasterizer.viewportRx)
				xEnd = Rasterizer.viewportRx;
			if (xStart < 0) {
				shadeStart -= xStart * shadeStepScalar;
				xStart = 0;
			}
			if (xStart >= xEnd)
				return;
		}
		offset += xStart;
		pixelGroupCount = xEnd - xStart;
		if (alpha == 0) {
			do {
				pixels[offset++] = HSL_TO_RGB[shadeStart >> 8];
				shadeStart += shadeStepScalar;
			} while (--pixelGroupCount > 0);
			return;
		}
		int destinationAlphaScalar = alpha;
		int sourceAlphaScalar = 256 - alpha;
		do {
			rgb = HSL_TO_RGB[shadeStart >> 8];
			shadeStart += shadeStepScalar;
			rgb = ((rgb & 0xff00ff) * sourceAlphaScalar >> 8 & 0xff00ff)
					+ ((rgb & 0xff00) * sourceAlphaScalar >> 8 & 0xff00);
			pixels[offset++] = rgb + ((pixels[offset] & 0xff00ff) * destinationAlphaScalar >> 8 & 0xff00ff)
					+ ((pixels[offset] & 0xff00) * destinationAlphaScalar >> 8 & 0xff00);
		} while (--pixelGroupCount > 0);
	}

	/**
	 * Draws flat triangle internal.
	 *
	 * @param inputValue  the input value
	 * @param inputValue2 the input value2
	 * @param inputValue3 the input value3
	 * @param inputValue4 the input value4
	 * @param inputValue5 the input value5
	 * @param inputValue6 the input value6
	 * @param inputValue7 the input value7
	 */
	private static void drawFlatTriangleInternal(int inputValue, int inputValue2, int inputValue3, int inputValue4,
			int inputValue5, int inputValue6, int inputValue7) {
		int intermediateValue = 0;
		if (inputValue2 != inputValue)
			intermediateValue = (inputValue5 - inputValue4 << 16) / (inputValue2 - inputValue);
		int intermediateValue2 = 0;
		if (inputValue3 != inputValue2)
			intermediateValue2 = (inputValue6 - inputValue5 << 16) / (inputValue3 - inputValue2);
		int intermediateValue3 = 0;
		if (inputValue3 != inputValue)
			intermediateValue3 = (inputValue4 - inputValue6 << 16) / (inputValue - inputValue3);
		if (inputValue <= inputValue2 && inputValue <= inputValue3) {
			if (inputValue >= Rasterizer.bottomY)
				return;
			if (inputValue2 > Rasterizer.bottomY)
				inputValue2 = Rasterizer.bottomY;
			if (inputValue3 > Rasterizer.bottomY)
				inputValue3 = Rasterizer.bottomY;
			if (inputValue2 < inputValue3) {
				inputValue6 = inputValue4 <<= 16;
				if (inputValue < 0) {
					inputValue6 -= intermediateValue3 * inputValue;
					inputValue4 -= intermediateValue * inputValue;
					inputValue = 0;
				}
				inputValue5 <<= 16;
				if (inputValue2 < 0) {
					inputValue5 -= intermediateValue2 * inputValue2;
					inputValue2 = 0;
				}
				if (inputValue != inputValue2 && intermediateValue3 < intermediateValue
						|| inputValue == inputValue2 && intermediateValue3 > intermediateValue2) {
					inputValue3 -= inputValue2;
					inputValue2 -= inputValue;
					for (inputValue = scanlineOffsets[inputValue]; --inputValue2 >= 0; inputValue += Rasterizer.width) {
						drawFlatScanline(Rasterizer.pixels, inputValue, inputValue7, 0, inputValue6 >> 16,
								inputValue4 >> 16);
						inputValue6 += intermediateValue3;
						inputValue4 += intermediateValue;
					}

					while (--inputValue3 >= 0) {
						drawFlatScanline(Rasterizer.pixels, inputValue, inputValue7, 0, inputValue6 >> 16,
								inputValue5 >> 16);
						inputValue6 += intermediateValue3;
						inputValue5 += intermediateValue2;
						inputValue += Rasterizer.width;
					}
					return;
				}
				inputValue3 -= inputValue2;
				inputValue2 -= inputValue;
				for (inputValue = scanlineOffsets[inputValue]; --inputValue2 >= 0; inputValue += Rasterizer.width) {
					drawFlatScanline(Rasterizer.pixels, inputValue, inputValue7, 0, inputValue4 >> 16,
							inputValue6 >> 16);
					inputValue6 += intermediateValue3;
					inputValue4 += intermediateValue;
				}

				while (--inputValue3 >= 0) {
					drawFlatScanline(Rasterizer.pixels, inputValue, inputValue7, 0, inputValue5 >> 16,
							inputValue6 >> 16);
					inputValue6 += intermediateValue3;
					inputValue5 += intermediateValue2;
					inputValue += Rasterizer.width;
				}
				return;
			}
			inputValue5 = inputValue4 <<= 16;
			if (inputValue < 0) {
				inputValue5 -= intermediateValue3 * inputValue;
				inputValue4 -= intermediateValue * inputValue;
				inputValue = 0;
			}
			inputValue6 <<= 16;
			if (inputValue3 < 0) {
				inputValue6 -= intermediateValue2 * inputValue3;
				inputValue3 = 0;
			}
			if (inputValue != inputValue3 && intermediateValue3 < intermediateValue
					|| inputValue == inputValue3 && intermediateValue2 > intermediateValue) {
				inputValue2 -= inputValue3;
				inputValue3 -= inputValue;
				for (inputValue = scanlineOffsets[inputValue]; --inputValue3 >= 0; inputValue += Rasterizer.width) {
					drawFlatScanline(Rasterizer.pixels, inputValue, inputValue7, 0, inputValue5 >> 16,
							inputValue4 >> 16);
					inputValue5 += intermediateValue3;
					inputValue4 += intermediateValue;
				}

				while (--inputValue2 >= 0) {
					drawFlatScanline(Rasterizer.pixels, inputValue, inputValue7, 0, inputValue6 >> 16,
							inputValue4 >> 16);
					inputValue6 += intermediateValue2;
					inputValue4 += intermediateValue;
					inputValue += Rasterizer.width;
				}
				return;
			}
			inputValue2 -= inputValue3;
			inputValue3 -= inputValue;
			for (inputValue = scanlineOffsets[inputValue]; --inputValue3 >= 0; inputValue += Rasterizer.width) {
				drawFlatScanline(Rasterizer.pixels, inputValue, inputValue7, 0, inputValue4 >> 16, inputValue5 >> 16);
				inputValue5 += intermediateValue3;
				inputValue4 += intermediateValue;
			}

			while (--inputValue2 >= 0) {
				drawFlatScanline(Rasterizer.pixels, inputValue, inputValue7, 0, inputValue4 >> 16, inputValue6 >> 16);
				inputValue6 += intermediateValue2;
				inputValue4 += intermediateValue;
				inputValue += Rasterizer.width;
			}
			return;
		}
		if (inputValue2 <= inputValue3) {
			if (inputValue2 >= Rasterizer.bottomY)
				return;
			if (inputValue3 > Rasterizer.bottomY)
				inputValue3 = Rasterizer.bottomY;
			if (inputValue > Rasterizer.bottomY)
				inputValue = Rasterizer.bottomY;
			if (inputValue3 < inputValue) {
				inputValue4 = inputValue5 <<= 16;
				if (inputValue2 < 0) {
					inputValue4 -= intermediateValue * inputValue2;
					inputValue5 -= intermediateValue2 * inputValue2;
					inputValue2 = 0;
				}
				inputValue6 <<= 16;
				if (inputValue3 < 0) {
					inputValue6 -= intermediateValue3 * inputValue3;
					inputValue3 = 0;
				}
				if (inputValue2 != inputValue3 && intermediateValue < intermediateValue2
						|| inputValue2 == inputValue3 && intermediateValue > intermediateValue3) {
					inputValue -= inputValue3;
					inputValue3 -= inputValue2;
					for (inputValue2 = scanlineOffsets[inputValue2]; --inputValue3 >= 0; inputValue2 += Rasterizer.width) {
						drawFlatScanline(Rasterizer.pixels, inputValue2, inputValue7, 0, inputValue4 >> 16,
								inputValue5 >> 16);
						inputValue4 += intermediateValue;
						inputValue5 += intermediateValue2;
					}

					while (--inputValue >= 0) {
						drawFlatScanline(Rasterizer.pixels, inputValue2, inputValue7, 0, inputValue4 >> 16,
								inputValue6 >> 16);
						inputValue4 += intermediateValue;
						inputValue6 += intermediateValue3;
						inputValue2 += Rasterizer.width;
					}
					return;
				}
				inputValue -= inputValue3;
				inputValue3 -= inputValue2;
				for (inputValue2 = scanlineOffsets[inputValue2]; --inputValue3 >= 0; inputValue2 += Rasterizer.width) {
					drawFlatScanline(Rasterizer.pixels, inputValue2, inputValue7, 0, inputValue5 >> 16,
							inputValue4 >> 16);
					inputValue4 += intermediateValue;
					inputValue5 += intermediateValue2;
				}

				while (--inputValue >= 0) {
					drawFlatScanline(Rasterizer.pixels, inputValue2, inputValue7, 0, inputValue6 >> 16,
							inputValue4 >> 16);
					inputValue4 += intermediateValue;
					inputValue6 += intermediateValue3;
					inputValue2 += Rasterizer.width;
				}
				return;
			}
			inputValue6 = inputValue5 <<= 16;
			if (inputValue2 < 0) {
				inputValue6 -= intermediateValue * inputValue2;
				inputValue5 -= intermediateValue2 * inputValue2;
				inputValue2 = 0;
			}
			inputValue4 <<= 16;
			if (inputValue < 0) {
				inputValue4 -= intermediateValue3 * inputValue;
				inputValue = 0;
			}
			if (intermediateValue < intermediateValue2) {
				inputValue3 -= inputValue;
				inputValue -= inputValue2;
				for (inputValue2 = scanlineOffsets[inputValue2]; --inputValue >= 0; inputValue2 += Rasterizer.width) {
					drawFlatScanline(Rasterizer.pixels, inputValue2, inputValue7, 0, inputValue6 >> 16,
							inputValue5 >> 16);
					inputValue6 += intermediateValue;
					inputValue5 += intermediateValue2;
				}

				while (--inputValue3 >= 0) {
					drawFlatScanline(Rasterizer.pixels, inputValue2, inputValue7, 0, inputValue4 >> 16,
							inputValue5 >> 16);
					inputValue4 += intermediateValue3;
					inputValue5 += intermediateValue2;
					inputValue2 += Rasterizer.width;
				}
				return;
			}
			inputValue3 -= inputValue;
			inputValue -= inputValue2;
			for (inputValue2 = scanlineOffsets[inputValue2]; --inputValue >= 0; inputValue2 += Rasterizer.width) {
				drawFlatScanline(Rasterizer.pixels, inputValue2, inputValue7, 0, inputValue5 >> 16, inputValue6 >> 16);
				inputValue6 += intermediateValue;
				inputValue5 += intermediateValue2;
			}

			while (--inputValue3 >= 0) {
				drawFlatScanline(Rasterizer.pixels, inputValue2, inputValue7, 0, inputValue5 >> 16, inputValue4 >> 16);
				inputValue4 += intermediateValue3;
				inputValue5 += intermediateValue2;
				inputValue2 += Rasterizer.width;
			}
			return;
		}
		if (inputValue3 >= Rasterizer.bottomY)
			return;
		if (inputValue > Rasterizer.bottomY)
			inputValue = Rasterizer.bottomY;
		if (inputValue2 > Rasterizer.bottomY)
			inputValue2 = Rasterizer.bottomY;
		if (inputValue < inputValue2) {
			inputValue5 = inputValue6 <<= 16;
			if (inputValue3 < 0) {
				inputValue5 -= intermediateValue2 * inputValue3;
				inputValue6 -= intermediateValue3 * inputValue3;
				inputValue3 = 0;
			}
			inputValue4 <<= 16;
			if (inputValue < 0) {
				inputValue4 -= intermediateValue * inputValue;
				inputValue = 0;
			}
			if (intermediateValue2 < intermediateValue3) {
				inputValue2 -= inputValue;
				inputValue -= inputValue3;
				for (inputValue3 = scanlineOffsets[inputValue3]; --inputValue >= 0; inputValue3 += Rasterizer.width) {
					drawFlatScanline(Rasterizer.pixels, inputValue3, inputValue7, 0, inputValue5 >> 16,
							inputValue6 >> 16);
					inputValue5 += intermediateValue2;
					inputValue6 += intermediateValue3;
				}

				while (--inputValue2 >= 0) {
					drawFlatScanline(Rasterizer.pixels, inputValue3, inputValue7, 0, inputValue5 >> 16,
							inputValue4 >> 16);
					inputValue5 += intermediateValue2;
					inputValue4 += intermediateValue;
					inputValue3 += Rasterizer.width;
				}
				return;
			}
			inputValue2 -= inputValue;
			inputValue -= inputValue3;
			for (inputValue3 = scanlineOffsets[inputValue3]; --inputValue >= 0; inputValue3 += Rasterizer.width) {
				drawFlatScanline(Rasterizer.pixels, inputValue3, inputValue7, 0, inputValue6 >> 16, inputValue5 >> 16);
				inputValue5 += intermediateValue2;
				inputValue6 += intermediateValue3;
			}

			while (--inputValue2 >= 0) {
				drawFlatScanline(Rasterizer.pixels, inputValue3, inputValue7, 0, inputValue4 >> 16, inputValue5 >> 16);
				inputValue5 += intermediateValue2;
				inputValue4 += intermediateValue;
				inputValue3 += Rasterizer.width;
			}
			return;
		}
		inputValue4 = inputValue6 <<= 16;
		if (inputValue3 < 0) {
			inputValue4 -= intermediateValue2 * inputValue3;
			inputValue6 -= intermediateValue3 * inputValue3;
			inputValue3 = 0;
		}
		inputValue5 <<= 16;
		if (inputValue2 < 0) {
			inputValue5 -= intermediateValue * inputValue2;
			inputValue2 = 0;
		}
		if (intermediateValue2 < intermediateValue3) {
			inputValue -= inputValue2;
			inputValue2 -= inputValue3;
			for (inputValue3 = scanlineOffsets[inputValue3]; --inputValue2 >= 0; inputValue3 += Rasterizer.width) {
				drawFlatScanline(Rasterizer.pixels, inputValue3, inputValue7, 0, inputValue4 >> 16, inputValue6 >> 16);
				inputValue4 += intermediateValue2;
				inputValue6 += intermediateValue3;
			}

			while (--inputValue >= 0) {
				drawFlatScanline(Rasterizer.pixels, inputValue3, inputValue7, 0, inputValue5 >> 16, inputValue6 >> 16);
				inputValue5 += intermediateValue;
				inputValue6 += intermediateValue3;
				inputValue3 += Rasterizer.width;
			}
			return;
		}
		inputValue -= inputValue2;
		inputValue2 -= inputValue3;
		for (inputValue3 = scanlineOffsets[inputValue3]; --inputValue2 >= 0; inputValue3 += Rasterizer.width) {
			drawFlatScanline(Rasterizer.pixels, inputValue3, inputValue7, 0, inputValue6 >> 16, inputValue4 >> 16);
			inputValue4 += intermediateValue2;
			inputValue6 += intermediateValue3;
		}

		while (--inputValue >= 0) {
			drawFlatScanline(Rasterizer.pixels, inputValue3, inputValue7, 0, inputValue6 >> 16, inputValue5 >> 16);
			inputValue5 += intermediateValue;
			inputValue6 += intermediateValue3;
			inputValue3 += Rasterizer.width;
		}
	}

	/**
	 * Draws flat scanline.
	 *
	 * @param pixels          destination pixel buffer
	 * @param offset          scanline base offset
	 * @param rgb             flat RGB value
	 * @param pixelGroupCount scratch group counter retained by the original loop
	 * @param xStart          inclusive span start
	 * @param xEnd            exclusive span end
	 */
	private static void drawFlatScanline(int pixels[], int offset, int rgb, int pixelGroupCount,
			int xStart, int xEnd) {
		if (restrictEdges) {
			if (xEnd > Rasterizer.viewportRx)
				xEnd = Rasterizer.viewportRx;
			if (xStart < 0)
				xStart = 0;
		}
		if (xStart >= xEnd)
			return;
		offset += xStart;
		pixelGroupCount = xEnd - xStart >> 2;
		if (alpha == 0) {
			while (--pixelGroupCount >= 0) {
				pixels[offset++] = rgb;
				pixels[offset++] = rgb;
				pixels[offset++] = rgb;
				pixels[offset++] = rgb;
			}
			for (pixelGroupCount = xEnd - xStart & 3; --pixelGroupCount >= 0;)
				pixels[offset++] = rgb;

			return;
		}
		int destinationAlpha = alpha;
		int sourceAlpha = 256 - alpha;
		rgb = ((rgb & 0xff00ff) * sourceAlpha >> 8 & 0xff00ff)
				+ ((rgb & 0xff00) * sourceAlpha >> 8 & 0xff00);
		while (--pixelGroupCount >= 0) {
			pixels[offset++] = rgb + ((pixels[offset] & 0xff00ff) * destinationAlpha >> 8 & 0xff00ff)
					+ ((pixels[offset] & 0xff00) * destinationAlpha >> 8 & 0xff00);
			pixels[offset++] = rgb + ((pixels[offset] & 0xff00ff) * destinationAlpha >> 8 & 0xff00ff)
					+ ((pixels[offset] & 0xff00) * destinationAlpha >> 8 & 0xff00);
			pixels[offset++] = rgb + ((pixels[offset] & 0xff00ff) * destinationAlpha >> 8 & 0xff00ff)
					+ ((pixels[offset] & 0xff00) * destinationAlpha >> 8 & 0xff00);
			pixels[offset++] = rgb + ((pixels[offset] & 0xff00ff) * destinationAlpha >> 8 & 0xff00ff)
					+ ((pixels[offset] & 0xff00) * destinationAlpha >> 8 & 0xff00);
		}
		for (pixelGroupCount = xEnd - xStart & 3; --pixelGroupCount >= 0;)
			pixels[offset++] = rgb + ((pixels[offset] & 0xff00ff) * destinationAlpha >> 8 & 0xff00ff)
					+ ((pixels[offset] & 0xff00) * destinationAlpha >> 8 & 0xff00);

	}

	/**
	 * Draws textured triangle internal.
	 *
	 * @param inputValue   the input value
	 * @param inputValue2  the input value2
	 * @param inputValue3  the input value3
	 * @param inputValue4  the input value4
	 * @param inputValue5  the input value5
	 * @param inputValue6  the input value6
	 * @param inputValue7  the input value7
	 * @param inputValue8  the input value8
	 * @param inputValue9  the input value9
	 * @param inputValue10 the input value10
	 * @param inputValue11 the input value11
	 * @param inputValue12 the input value12
	 * @param inputValue13 the input value13
	 * @param inputValue14 the input value14
	 * @param inputValue15 the input value15
	 * @param inputValue16 the input value16
	 * @param inputValue17 the input value17
	 * @param inputValue18 the input value18
	 * @param inputValue19 the input value19
	 */
	private static void drawTexturedTriangleInternal(int inputValue, int inputValue2, int inputValue3, int inputValue4,
			int inputValue5, int inputValue6, int inputValue7, int inputValue8, int inputValue9, int inputValue10,
			int inputValue11, int inputValue12, int inputValue13, int inputValue14, int inputValue15, int inputValue16,
			int inputValue17, int inputValue18, int inputValue19) {
		int values[] = getTexturePixels(inputValue19);
		opaqueTexture = !textureHasTransparency[inputValue19];
		inputValue11 = inputValue10 - inputValue11;
		inputValue14 = inputValue13 - inputValue14;
		inputValue17 = inputValue16 - inputValue17;
		inputValue12 -= inputValue10;
		inputValue15 -= inputValue13;
		inputValue18 -= inputValue16;
		int intermediateValue = inputValue12 * inputValue13 - inputValue15 * inputValue10 << 14;
		int intermediateValue2 = inputValue15 * inputValue16 - inputValue18 * inputValue13 << 8;
		int intermediateValue3 = inputValue18 * inputValue10 - inputValue12 * inputValue16 << 5;
		int intermediateValue4 = inputValue11 * inputValue13 - inputValue14 * inputValue10 << 14;
		int intermediateValue5 = inputValue14 * inputValue16 - inputValue17 * inputValue13 << 8;
		int intermediateValue6 = inputValue17 * inputValue10 - inputValue11 * inputValue16 << 5;
		int intermediateValue7 = inputValue14 * inputValue12 - inputValue11 * inputValue15 << 14;
		int intermediateValue8 = inputValue17 * inputValue15 - inputValue14 * inputValue18 << 8;
		int intermediateValue9 = inputValue11 * inputValue18 - inputValue17 * inputValue12 << 5;
		int intermediateValue10 = 0;
		int intermediateValue11 = 0;
		if (inputValue2 != inputValue) {
			intermediateValue10 = (inputValue5 - inputValue4 << 16) / (inputValue2 - inputValue);
			intermediateValue11 = (inputValue8 - inputValue7 << 16) / (inputValue2 - inputValue);
		}
		int intermediateValue12 = 0;
		int intermediateValue13 = 0;
		if (inputValue3 != inputValue2) {
			intermediateValue12 = (inputValue6 - inputValue5 << 16) / (inputValue3 - inputValue2);
			intermediateValue13 = (inputValue9 - inputValue8 << 16) / (inputValue3 - inputValue2);
		}
		int intermediateValue14 = 0;
		int intermediateValue15 = 0;
		if (inputValue3 != inputValue) {
			intermediateValue14 = (inputValue4 - inputValue6 << 16) / (inputValue - inputValue3);
			intermediateValue15 = (inputValue7 - inputValue9 << 16) / (inputValue - inputValue3);
		}
		if (inputValue <= inputValue2 && inputValue <= inputValue3) {
			if (inputValue >= Rasterizer.bottomY)
				return;
			if (inputValue2 > Rasterizer.bottomY)
				inputValue2 = Rasterizer.bottomY;
			if (inputValue3 > Rasterizer.bottomY)
				inputValue3 = Rasterizer.bottomY;
			if (inputValue2 < inputValue3) {
				inputValue6 = inputValue4 <<= 16;
				inputValue9 = inputValue7 <<= 16;
				if (inputValue < 0) {
					inputValue6 -= intermediateValue14 * inputValue;
					inputValue4 -= intermediateValue10 * inputValue;
					inputValue9 -= intermediateValue15 * inputValue;
					inputValue7 -= intermediateValue11 * inputValue;
					inputValue = 0;
				}
				inputValue5 <<= 16;
				inputValue8 <<= 16;
				if (inputValue2 < 0) {
					inputValue5 -= intermediateValue12 * inputValue2;
					inputValue8 -= intermediateValue13 * inputValue2;
					inputValue2 = 0;
				}
				int intermediateValue16 = inputValue - centerY;
				intermediateValue += intermediateValue3 * intermediateValue16;
				intermediateValue4 += intermediateValue6 * intermediateValue16;
				intermediateValue7 += intermediateValue9 * intermediateValue16;
				if (inputValue != inputValue2 && intermediateValue14 < intermediateValue10
						|| inputValue == inputValue2 && intermediateValue14 > intermediateValue12) {
					inputValue3 -= inputValue2;
					inputValue2 -= inputValue;
					inputValue = scanlineOffsets[inputValue];
					while (--inputValue2 >= 0) {
						drawTexturedScanline(Rasterizer.pixels, values, 0, 0, inputValue, inputValue6 >> 16,
								inputValue4 >> 16, inputValue9 >> 8, inputValue7 >> 8, intermediateValue,
								intermediateValue4, intermediateValue7, intermediateValue2, intermediateValue5,
								intermediateValue8);
						inputValue6 += intermediateValue14;
						inputValue4 += intermediateValue10;
						inputValue9 += intermediateValue15;
						inputValue7 += intermediateValue11;
						inputValue += Rasterizer.width;
						intermediateValue += intermediateValue3;
						intermediateValue4 += intermediateValue6;
						intermediateValue7 += intermediateValue9;
					}
					while (--inputValue3 >= 0) {
						drawTexturedScanline(Rasterizer.pixels, values, 0, 0, inputValue, inputValue6 >> 16,
								inputValue5 >> 16, inputValue9 >> 8, inputValue8 >> 8, intermediateValue,
								intermediateValue4, intermediateValue7, intermediateValue2, intermediateValue5,
								intermediateValue8);
						inputValue6 += intermediateValue14;
						inputValue5 += intermediateValue12;
						inputValue9 += intermediateValue15;
						inputValue8 += intermediateValue13;
						inputValue += Rasterizer.width;
						intermediateValue += intermediateValue3;
						intermediateValue4 += intermediateValue6;
						intermediateValue7 += intermediateValue9;
					}
					return;
				}
				inputValue3 -= inputValue2;
				inputValue2 -= inputValue;
				inputValue = scanlineOffsets[inputValue];
				while (--inputValue2 >= 0) {
					drawTexturedScanline(Rasterizer.pixels, values, 0, 0, inputValue, inputValue4 >> 16,
							inputValue6 >> 16, inputValue7 >> 8, inputValue9 >> 8, intermediateValue,
							intermediateValue4, intermediateValue7, intermediateValue2, intermediateValue5,
							intermediateValue8);
					inputValue6 += intermediateValue14;
					inputValue4 += intermediateValue10;
					inputValue9 += intermediateValue15;
					inputValue7 += intermediateValue11;
					inputValue += Rasterizer.width;
					intermediateValue += intermediateValue3;
					intermediateValue4 += intermediateValue6;
					intermediateValue7 += intermediateValue9;
				}
				while (--inputValue3 >= 0) {
					drawTexturedScanline(Rasterizer.pixels, values, 0, 0, inputValue, inputValue5 >> 16,
							inputValue6 >> 16, inputValue8 >> 8, inputValue9 >> 8, intermediateValue,
							intermediateValue4, intermediateValue7, intermediateValue2, intermediateValue5,
							intermediateValue8);
					inputValue6 += intermediateValue14;
					inputValue5 += intermediateValue12;
					inputValue9 += intermediateValue15;
					inputValue8 += intermediateValue13;
					inputValue += Rasterizer.width;
					intermediateValue += intermediateValue3;
					intermediateValue4 += intermediateValue6;
					intermediateValue7 += intermediateValue9;
				}
				return;
			}
			inputValue5 = inputValue4 <<= 16;
			inputValue8 = inputValue7 <<= 16;
			if (inputValue < 0) {
				inputValue5 -= intermediateValue14 * inputValue;
				inputValue4 -= intermediateValue10 * inputValue;
				inputValue8 -= intermediateValue15 * inputValue;
				inputValue7 -= intermediateValue11 * inputValue;
				inputValue = 0;
			}
			inputValue6 <<= 16;
			inputValue9 <<= 16;
			if (inputValue3 < 0) {
				inputValue6 -= intermediateValue12 * inputValue3;
				inputValue9 -= intermediateValue13 * inputValue3;
				inputValue3 = 0;
			}
			int intermediateValue17 = inputValue - centerY;
			intermediateValue += intermediateValue3 * intermediateValue17;
			intermediateValue4 += intermediateValue6 * intermediateValue17;
			intermediateValue7 += intermediateValue9 * intermediateValue17;
			if (inputValue != inputValue3 && intermediateValue14 < intermediateValue10
					|| inputValue == inputValue3 && intermediateValue12 > intermediateValue10) {
				inputValue2 -= inputValue3;
				inputValue3 -= inputValue;
				inputValue = scanlineOffsets[inputValue];
				while (--inputValue3 >= 0) {
					drawTexturedScanline(Rasterizer.pixels, values, 0, 0, inputValue, inputValue5 >> 16,
							inputValue4 >> 16, inputValue8 >> 8, inputValue7 >> 8, intermediateValue,
							intermediateValue4, intermediateValue7, intermediateValue2, intermediateValue5,
							intermediateValue8);
					inputValue5 += intermediateValue14;
					inputValue4 += intermediateValue10;
					inputValue8 += intermediateValue15;
					inputValue7 += intermediateValue11;
					inputValue += Rasterizer.width;
					intermediateValue += intermediateValue3;
					intermediateValue4 += intermediateValue6;
					intermediateValue7 += intermediateValue9;
				}
				while (--inputValue2 >= 0) {
					drawTexturedScanline(Rasterizer.pixels, values, 0, 0, inputValue, inputValue6 >> 16,
							inputValue4 >> 16, inputValue9 >> 8, inputValue7 >> 8, intermediateValue,
							intermediateValue4, intermediateValue7, intermediateValue2, intermediateValue5,
							intermediateValue8);
					inputValue6 += intermediateValue12;
					inputValue4 += intermediateValue10;
					inputValue9 += intermediateValue13;
					inputValue7 += intermediateValue11;
					inputValue += Rasterizer.width;
					intermediateValue += intermediateValue3;
					intermediateValue4 += intermediateValue6;
					intermediateValue7 += intermediateValue9;
				}
				return;
			}
			inputValue2 -= inputValue3;
			inputValue3 -= inputValue;
			inputValue = scanlineOffsets[inputValue];
			while (--inputValue3 >= 0) {
				drawTexturedScanline(Rasterizer.pixels, values, 0, 0, inputValue, inputValue4 >> 16, inputValue5 >> 16,
						inputValue7 >> 8, inputValue8 >> 8, intermediateValue, intermediateValue4, intermediateValue7,
						intermediateValue2, intermediateValue5, intermediateValue8);
				inputValue5 += intermediateValue14;
				inputValue4 += intermediateValue10;
				inputValue8 += intermediateValue15;
				inputValue7 += intermediateValue11;
				inputValue += Rasterizer.width;
				intermediateValue += intermediateValue3;
				intermediateValue4 += intermediateValue6;
				intermediateValue7 += intermediateValue9;
			}
			while (--inputValue2 >= 0) {
				drawTexturedScanline(Rasterizer.pixels, values, 0, 0, inputValue, inputValue4 >> 16, inputValue6 >> 16,
						inputValue7 >> 8, inputValue9 >> 8, intermediateValue, intermediateValue4, intermediateValue7,
						intermediateValue2, intermediateValue5, intermediateValue8);
				inputValue6 += intermediateValue12;
				inputValue4 += intermediateValue10;
				inputValue9 += intermediateValue13;
				inputValue7 += intermediateValue11;
				inputValue += Rasterizer.width;
				intermediateValue += intermediateValue3;
				intermediateValue4 += intermediateValue6;
				intermediateValue7 += intermediateValue9;
			}
			return;
		}
		if (inputValue2 <= inputValue3) {
			if (inputValue2 >= Rasterizer.bottomY)
				return;
			if (inputValue3 > Rasterizer.bottomY)
				inputValue3 = Rasterizer.bottomY;
			if (inputValue > Rasterizer.bottomY)
				inputValue = Rasterizer.bottomY;
			if (inputValue3 < inputValue) {
				inputValue4 = inputValue5 <<= 16;
				inputValue7 = inputValue8 <<= 16;
				if (inputValue2 < 0) {
					inputValue4 -= intermediateValue10 * inputValue2;
					inputValue5 -= intermediateValue12 * inputValue2;
					inputValue7 -= intermediateValue11 * inputValue2;
					inputValue8 -= intermediateValue13 * inputValue2;
					inputValue2 = 0;
				}
				inputValue6 <<= 16;
				inputValue9 <<= 16;
				if (inputValue3 < 0) {
					inputValue6 -= intermediateValue14 * inputValue3;
					inputValue9 -= intermediateValue15 * inputValue3;
					inputValue3 = 0;
				}
				int intermediateValue18 = inputValue2 - centerY;
				intermediateValue += intermediateValue3 * intermediateValue18;
				intermediateValue4 += intermediateValue6 * intermediateValue18;
				intermediateValue7 += intermediateValue9 * intermediateValue18;
				if (inputValue2 != inputValue3 && intermediateValue10 < intermediateValue12
						|| inputValue2 == inputValue3 && intermediateValue10 > intermediateValue14) {
					inputValue -= inputValue3;
					inputValue3 -= inputValue2;
					inputValue2 = scanlineOffsets[inputValue2];
					while (--inputValue3 >= 0) {
						drawTexturedScanline(Rasterizer.pixels, values, 0, 0, inputValue2, inputValue4 >> 16,
								inputValue5 >> 16, inputValue7 >> 8, inputValue8 >> 8, intermediateValue,
								intermediateValue4, intermediateValue7, intermediateValue2, intermediateValue5,
								intermediateValue8);
						inputValue4 += intermediateValue10;
						inputValue5 += intermediateValue12;
						inputValue7 += intermediateValue11;
						inputValue8 += intermediateValue13;
						inputValue2 += Rasterizer.width;
						intermediateValue += intermediateValue3;
						intermediateValue4 += intermediateValue6;
						intermediateValue7 += intermediateValue9;
					}
					while (--inputValue >= 0) {
						drawTexturedScanline(Rasterizer.pixels, values, 0, 0, inputValue2, inputValue4 >> 16,
								inputValue6 >> 16, inputValue7 >> 8, inputValue9 >> 8, intermediateValue,
								intermediateValue4, intermediateValue7, intermediateValue2, intermediateValue5,
								intermediateValue8);
						inputValue4 += intermediateValue10;
						inputValue6 += intermediateValue14;
						inputValue7 += intermediateValue11;
						inputValue9 += intermediateValue15;
						inputValue2 += Rasterizer.width;
						intermediateValue += intermediateValue3;
						intermediateValue4 += intermediateValue6;
						intermediateValue7 += intermediateValue9;
					}
					return;
				}
				inputValue -= inputValue3;
				inputValue3 -= inputValue2;
				inputValue2 = scanlineOffsets[inputValue2];
				while (--inputValue3 >= 0) {
					drawTexturedScanline(Rasterizer.pixels, values, 0, 0, inputValue2, inputValue5 >> 16,
							inputValue4 >> 16, inputValue8 >> 8, inputValue7 >> 8, intermediateValue,
							intermediateValue4, intermediateValue7, intermediateValue2, intermediateValue5,
							intermediateValue8);
					inputValue4 += intermediateValue10;
					inputValue5 += intermediateValue12;
					inputValue7 += intermediateValue11;
					inputValue8 += intermediateValue13;
					inputValue2 += Rasterizer.width;
					intermediateValue += intermediateValue3;
					intermediateValue4 += intermediateValue6;
					intermediateValue7 += intermediateValue9;
				}
				while (--inputValue >= 0) {
					drawTexturedScanline(Rasterizer.pixels, values, 0, 0, inputValue2, inputValue6 >> 16,
							inputValue4 >> 16, inputValue9 >> 8, inputValue7 >> 8, intermediateValue,
							intermediateValue4, intermediateValue7, intermediateValue2, intermediateValue5,
							intermediateValue8);
					inputValue4 += intermediateValue10;
					inputValue6 += intermediateValue14;
					inputValue7 += intermediateValue11;
					inputValue9 += intermediateValue15;
					inputValue2 += Rasterizer.width;
					intermediateValue += intermediateValue3;
					intermediateValue4 += intermediateValue6;
					intermediateValue7 += intermediateValue9;
				}
				return;
			}
			inputValue6 = inputValue5 <<= 16;
			inputValue9 = inputValue8 <<= 16;
			if (inputValue2 < 0) {
				inputValue6 -= intermediateValue10 * inputValue2;
				inputValue5 -= intermediateValue12 * inputValue2;
				inputValue9 -= intermediateValue11 * inputValue2;
				inputValue8 -= intermediateValue13 * inputValue2;
				inputValue2 = 0;
			}
			inputValue4 <<= 16;
			inputValue7 <<= 16;
			if (inputValue < 0) {
				inputValue4 -= intermediateValue14 * inputValue;
				inputValue7 -= intermediateValue15 * inputValue;
				inputValue = 0;
			}
			int intermediateValue19 = inputValue2 - centerY;
			intermediateValue += intermediateValue3 * intermediateValue19;
			intermediateValue4 += intermediateValue6 * intermediateValue19;
			intermediateValue7 += intermediateValue9 * intermediateValue19;
			if (intermediateValue10 < intermediateValue12) {
				inputValue3 -= inputValue;
				inputValue -= inputValue2;
				inputValue2 = scanlineOffsets[inputValue2];
				while (--inputValue >= 0) {
					drawTexturedScanline(Rasterizer.pixels, values, 0, 0, inputValue2, inputValue6 >> 16,
							inputValue5 >> 16, inputValue9 >> 8, inputValue8 >> 8, intermediateValue,
							intermediateValue4, intermediateValue7, intermediateValue2, intermediateValue5,
							intermediateValue8);
					inputValue6 += intermediateValue10;
					inputValue5 += intermediateValue12;
					inputValue9 += intermediateValue11;
					inputValue8 += intermediateValue13;
					inputValue2 += Rasterizer.width;
					intermediateValue += intermediateValue3;
					intermediateValue4 += intermediateValue6;
					intermediateValue7 += intermediateValue9;
				}
				while (--inputValue3 >= 0) {
					drawTexturedScanline(Rasterizer.pixels, values, 0, 0, inputValue2, inputValue4 >> 16,
							inputValue5 >> 16, inputValue7 >> 8, inputValue8 >> 8, intermediateValue,
							intermediateValue4, intermediateValue7, intermediateValue2, intermediateValue5,
							intermediateValue8);
					inputValue4 += intermediateValue14;
					inputValue5 += intermediateValue12;
					inputValue7 += intermediateValue15;
					inputValue8 += intermediateValue13;
					inputValue2 += Rasterizer.width;
					intermediateValue += intermediateValue3;
					intermediateValue4 += intermediateValue6;
					intermediateValue7 += intermediateValue9;
				}
				return;
			}
			inputValue3 -= inputValue;
			inputValue -= inputValue2;
			inputValue2 = scanlineOffsets[inputValue2];
			while (--inputValue >= 0) {
				drawTexturedScanline(Rasterizer.pixels, values, 0, 0, inputValue2, inputValue5 >> 16, inputValue6 >> 16,
						inputValue8 >> 8, inputValue9 >> 8, intermediateValue, intermediateValue4, intermediateValue7,
						intermediateValue2, intermediateValue5, intermediateValue8);
				inputValue6 += intermediateValue10;
				inputValue5 += intermediateValue12;
				inputValue9 += intermediateValue11;
				inputValue8 += intermediateValue13;
				inputValue2 += Rasterizer.width;
				intermediateValue += intermediateValue3;
				intermediateValue4 += intermediateValue6;
				intermediateValue7 += intermediateValue9;
			}
			while (--inputValue3 >= 0) {
				drawTexturedScanline(Rasterizer.pixels, values, 0, 0, inputValue2, inputValue5 >> 16, inputValue4 >> 16,
						inputValue8 >> 8, inputValue7 >> 8, intermediateValue, intermediateValue4, intermediateValue7,
						intermediateValue2, intermediateValue5, intermediateValue8);
				inputValue4 += intermediateValue14;
				inputValue5 += intermediateValue12;
				inputValue7 += intermediateValue15;
				inputValue8 += intermediateValue13;
				inputValue2 += Rasterizer.width;
				intermediateValue += intermediateValue3;
				intermediateValue4 += intermediateValue6;
				intermediateValue7 += intermediateValue9;
			}
			return;
		}
		if (inputValue3 >= Rasterizer.bottomY)
			return;
		if (inputValue > Rasterizer.bottomY)
			inputValue = Rasterizer.bottomY;
		if (inputValue2 > Rasterizer.bottomY)
			inputValue2 = Rasterizer.bottomY;
		if (inputValue < inputValue2) {
			inputValue5 = inputValue6 <<= 16;
			inputValue8 = inputValue9 <<= 16;
			if (inputValue3 < 0) {
				inputValue5 -= intermediateValue12 * inputValue3;
				inputValue6 -= intermediateValue14 * inputValue3;
				inputValue8 -= intermediateValue13 * inputValue3;
				inputValue9 -= intermediateValue15 * inputValue3;
				inputValue3 = 0;
			}
			inputValue4 <<= 16;
			inputValue7 <<= 16;
			if (inputValue < 0) {
				inputValue4 -= intermediateValue10 * inputValue;
				inputValue7 -= intermediateValue11 * inputValue;
				inputValue = 0;
			}
			int intermediateValue20 = inputValue3 - centerY;
			intermediateValue += intermediateValue3 * intermediateValue20;
			intermediateValue4 += intermediateValue6 * intermediateValue20;
			intermediateValue7 += intermediateValue9 * intermediateValue20;
			if (intermediateValue12 < intermediateValue14) {
				inputValue2 -= inputValue;
				inputValue -= inputValue3;
				inputValue3 = scanlineOffsets[inputValue3];
				while (--inputValue >= 0) {
					drawTexturedScanline(Rasterizer.pixels, values, 0, 0, inputValue3, inputValue5 >> 16,
							inputValue6 >> 16, inputValue8 >> 8, inputValue9 >> 8, intermediateValue,
							intermediateValue4, intermediateValue7, intermediateValue2, intermediateValue5,
							intermediateValue8);
					inputValue5 += intermediateValue12;
					inputValue6 += intermediateValue14;
					inputValue8 += intermediateValue13;
					inputValue9 += intermediateValue15;
					inputValue3 += Rasterizer.width;
					intermediateValue += intermediateValue3;
					intermediateValue4 += intermediateValue6;
					intermediateValue7 += intermediateValue9;
				}
				while (--inputValue2 >= 0) {
					drawTexturedScanline(Rasterizer.pixels, values, 0, 0, inputValue3, inputValue5 >> 16,
							inputValue4 >> 16, inputValue8 >> 8, inputValue7 >> 8, intermediateValue,
							intermediateValue4, intermediateValue7, intermediateValue2, intermediateValue5,
							intermediateValue8);
					inputValue5 += intermediateValue12;
					inputValue4 += intermediateValue10;
					inputValue8 += intermediateValue13;
					inputValue7 += intermediateValue11;
					inputValue3 += Rasterizer.width;
					intermediateValue += intermediateValue3;
					intermediateValue4 += intermediateValue6;
					intermediateValue7 += intermediateValue9;
				}
				return;
			}
			inputValue2 -= inputValue;
			inputValue -= inputValue3;
			inputValue3 = scanlineOffsets[inputValue3];
			while (--inputValue >= 0) {
				drawTexturedScanline(Rasterizer.pixels, values, 0, 0, inputValue3, inputValue6 >> 16, inputValue5 >> 16,
						inputValue9 >> 8, inputValue8 >> 8, intermediateValue, intermediateValue4, intermediateValue7,
						intermediateValue2, intermediateValue5, intermediateValue8);
				inputValue5 += intermediateValue12;
				inputValue6 += intermediateValue14;
				inputValue8 += intermediateValue13;
				inputValue9 += intermediateValue15;
				inputValue3 += Rasterizer.width;
				intermediateValue += intermediateValue3;
				intermediateValue4 += intermediateValue6;
				intermediateValue7 += intermediateValue9;
			}
			while (--inputValue2 >= 0) {
				drawTexturedScanline(Rasterizer.pixels, values, 0, 0, inputValue3, inputValue4 >> 16, inputValue5 >> 16,
						inputValue7 >> 8, inputValue8 >> 8, intermediateValue, intermediateValue4, intermediateValue7,
						intermediateValue2, intermediateValue5, intermediateValue8);
				inputValue5 += intermediateValue12;
				inputValue4 += intermediateValue10;
				inputValue8 += intermediateValue13;
				inputValue7 += intermediateValue11;
				inputValue3 += Rasterizer.width;
				intermediateValue += intermediateValue3;
				intermediateValue4 += intermediateValue6;
				intermediateValue7 += intermediateValue9;
			}
			return;
		}
		inputValue4 = inputValue6 <<= 16;
		inputValue7 = inputValue9 <<= 16;
		if (inputValue3 < 0) {
			inputValue4 -= intermediateValue12 * inputValue3;
			inputValue6 -= intermediateValue14 * inputValue3;
			inputValue7 -= intermediateValue13 * inputValue3;
			inputValue9 -= intermediateValue15 * inputValue3;
			inputValue3 = 0;
		}
		inputValue5 <<= 16;
		inputValue8 <<= 16;
		if (inputValue2 < 0) {
			inputValue5 -= intermediateValue10 * inputValue2;
			inputValue8 -= intermediateValue11 * inputValue2;
			inputValue2 = 0;
		}
		int intermediateValue21 = inputValue3 - centerY;
		intermediateValue += intermediateValue3 * intermediateValue21;
		intermediateValue4 += intermediateValue6 * intermediateValue21;
		intermediateValue7 += intermediateValue9 * intermediateValue21;
		if (intermediateValue12 < intermediateValue14) {
			inputValue -= inputValue2;
			inputValue2 -= inputValue3;
			inputValue3 = scanlineOffsets[inputValue3];
			while (--inputValue2 >= 0) {
				drawTexturedScanline(Rasterizer.pixels, values, 0, 0, inputValue3, inputValue4 >> 16, inputValue6 >> 16,
						inputValue7 >> 8, inputValue9 >> 8, intermediateValue, intermediateValue4, intermediateValue7,
						intermediateValue2, intermediateValue5, intermediateValue8);
				inputValue4 += intermediateValue12;
				inputValue6 += intermediateValue14;
				inputValue7 += intermediateValue13;
				inputValue9 += intermediateValue15;
				inputValue3 += Rasterizer.width;
				intermediateValue += intermediateValue3;
				intermediateValue4 += intermediateValue6;
				intermediateValue7 += intermediateValue9;
			}
			while (--inputValue >= 0) {
				drawTexturedScanline(Rasterizer.pixels, values, 0, 0, inputValue3, inputValue5 >> 16, inputValue6 >> 16,
						inputValue8 >> 8, inputValue9 >> 8, intermediateValue, intermediateValue4, intermediateValue7,
						intermediateValue2, intermediateValue5, intermediateValue8);
				inputValue5 += intermediateValue10;
				inputValue6 += intermediateValue14;
				inputValue8 += intermediateValue11;
				inputValue9 += intermediateValue15;
				inputValue3 += Rasterizer.width;
				intermediateValue += intermediateValue3;
				intermediateValue4 += intermediateValue6;
				intermediateValue7 += intermediateValue9;
			}
			return;
		}
		inputValue -= inputValue2;
		inputValue2 -= inputValue3;
		inputValue3 = scanlineOffsets[inputValue3];
		while (--inputValue2 >= 0) {
			drawTexturedScanline(Rasterizer.pixels, values, 0, 0, inputValue3, inputValue6 >> 16, inputValue4 >> 16,
					inputValue9 >> 8, inputValue7 >> 8, intermediateValue, intermediateValue4, intermediateValue7,
					intermediateValue2, intermediateValue5, intermediateValue8);
			inputValue4 += intermediateValue12;
			inputValue6 += intermediateValue14;
			inputValue7 += intermediateValue13;
			inputValue9 += intermediateValue15;
			inputValue3 += Rasterizer.width;
			intermediateValue += intermediateValue3;
			intermediateValue4 += intermediateValue6;
			intermediateValue7 += intermediateValue9;
		}
		while (--inputValue >= 0) {
			drawTexturedScanline(Rasterizer.pixels, values, 0, 0, inputValue3, inputValue6 >> 16, inputValue5 >> 16,
					inputValue9 >> 8, inputValue8 >> 8, intermediateValue, intermediateValue4, intermediateValue7,
					intermediateValue2, intermediateValue5, intermediateValue8);
			inputValue5 += intermediateValue10;
			inputValue6 += intermediateValue14;
			inputValue8 += intermediateValue11;
			inputValue9 += intermediateValue15;
			inputValue3 += Rasterizer.width;
			intermediateValue += intermediateValue3;
			intermediateValue4 += intermediateValue6;
			intermediateValue7 += intermediateValue9;
		}
	}

	/**
	 * Draws textured scanline.
	 *
	 * @param values       the values
	 * @param values2      the values2
	 * @param inputValue   the input value
	 * @param inputValue2  the input value2
	 * @param inputValue3  the input value3
	 * @param inputValue4  the input value4
	 * @param inputValue5  the input value5
	 * @param inputValue6  the input value6
	 * @param inputValue7  the input value7
	 * @param inputValue8  the input value8
	 * @param inputValue9  the input value9
	 * @param inputValue10 the input value10
	 * @param inputValue11 the input value11
	 * @param inputValue12 the input value12
	 * @param inputValue13 the input value13
	 */
	private static void drawTexturedScanline(int values[], int values2[], int inputValue, int inputValue2,
			int inputValue3, int inputValue4, int inputValue5, int inputValue6, int inputValue7, int inputValue8,
			int inputValue9, int inputValue10, int inputValue11, int inputValue12, int inputValue13) {
		if (inputValue4 >= inputValue5)
			return;
		int intermediateValue;
		int intermediateValue2;
		if (restrictEdges) {
			intermediateValue = (inputValue7 - inputValue6) / (inputValue5 - inputValue4);
			if (inputValue5 > Rasterizer.viewportRx)
				inputValue5 = Rasterizer.viewportRx;
			if (inputValue4 < 0) {
				inputValue6 -= inputValue4 * intermediateValue;
				inputValue4 = 0;
			}
			if (inputValue4 >= inputValue5)
				return;
			intermediateValue2 = inputValue5 - inputValue4 >> 3;
			intermediateValue <<= 12;
			inputValue6 <<= 9;
		} else {
			if (inputValue5 - inputValue4 > 7) {
				intermediateValue2 = inputValue5 - inputValue4 >> 3;
				intermediateValue = (inputValue7 - inputValue6) * reciprocal15[intermediateValue2] >> 6;
			} else {
				intermediateValue2 = 0;
				intermediateValue = 0;
			}
			inputValue6 <<= 9;
		}
		inputValue3 += inputValue4;
		if (lowMemory) {
			int intermediateValue3 = 0;
			int intermediateValue4 = 0;
			int intermediateValue5 = inputValue4 - centerX;
			inputValue8 += (inputValue11 >> 3) * intermediateValue5;
			inputValue9 += (inputValue12 >> 3) * intermediateValue5;
			inputValue10 += (inputValue13 >> 3) * intermediateValue5;
			int intermediateValue6 = inputValue10 >> 12;
			if (intermediateValue6 != 0) {
				inputValue = inputValue8 / intermediateValue6;
				inputValue2 = inputValue9 / intermediateValue6;
				if (inputValue < 0)
					inputValue = 0;
				else if (inputValue > 4032)
					inputValue = 4032;
			}
			inputValue8 += inputValue11;
			inputValue9 += inputValue12;
			inputValue10 += inputValue13;
			intermediateValue6 = inputValue10 >> 12;
			if (intermediateValue6 != 0) {
				intermediateValue3 = inputValue8 / intermediateValue6;
				intermediateValue4 = inputValue9 / intermediateValue6;
				if (intermediateValue3 < 7)
					intermediateValue3 = 7;
				else if (intermediateValue3 > 4032)
					intermediateValue3 = 4032;
			}
			int intermediateValue7 = intermediateValue3 - inputValue >> 3;
			int intermediateValue8 = intermediateValue4 - inputValue2 >> 3;
			inputValue += (inputValue6 & 0x600000) >> 3;
			int intermediateValue9 = inputValue6 >> 23;
			if (opaqueTexture) {
				while (intermediateValue2-- > 0) {
					values[inputValue3++] = values2[(inputValue2 & 0xfc0) + (inputValue >> 6)] >>> intermediateValue9;
					inputValue += intermediateValue7;
					inputValue2 += intermediateValue8;
					values[inputValue3++] = values2[(inputValue2 & 0xfc0) + (inputValue >> 6)] >>> intermediateValue9;
					inputValue += intermediateValue7;
					inputValue2 += intermediateValue8;
					values[inputValue3++] = values2[(inputValue2 & 0xfc0) + (inputValue >> 6)] >>> intermediateValue9;
					inputValue += intermediateValue7;
					inputValue2 += intermediateValue8;
					values[inputValue3++] = values2[(inputValue2 & 0xfc0) + (inputValue >> 6)] >>> intermediateValue9;
					inputValue += intermediateValue7;
					inputValue2 += intermediateValue8;
					values[inputValue3++] = values2[(inputValue2 & 0xfc0) + (inputValue >> 6)] >>> intermediateValue9;
					inputValue += intermediateValue7;
					inputValue2 += intermediateValue8;
					values[inputValue3++] = values2[(inputValue2 & 0xfc0) + (inputValue >> 6)] >>> intermediateValue9;
					inputValue += intermediateValue7;
					inputValue2 += intermediateValue8;
					values[inputValue3++] = values2[(inputValue2 & 0xfc0) + (inputValue >> 6)] >>> intermediateValue9;
					inputValue += intermediateValue7;
					inputValue2 += intermediateValue8;
					values[inputValue3++] = values2[(inputValue2 & 0xfc0) + (inputValue >> 6)] >>> intermediateValue9;
					inputValue = intermediateValue3;
					inputValue2 = intermediateValue4;
					inputValue8 += inputValue11;
					inputValue9 += inputValue12;
					inputValue10 += inputValue13;
					int intermediateValue10 = inputValue10 >> 12;
					if (intermediateValue10 != 0) {
						intermediateValue3 = inputValue8 / intermediateValue10;
						intermediateValue4 = inputValue9 / intermediateValue10;
						if (intermediateValue3 < 7)
							intermediateValue3 = 7;
						else if (intermediateValue3 > 4032)
							intermediateValue3 = 4032;
					}
					intermediateValue7 = intermediateValue3 - inputValue >> 3;
					intermediateValue8 = intermediateValue4 - inputValue2 >> 3;
					inputValue6 += intermediateValue;
					inputValue += (inputValue6 & 0x600000) >> 3;
					intermediateValue9 = inputValue6 >> 23;
				}
				for (intermediateValue2 = inputValue5 - inputValue4 & 7; intermediateValue2-- > 0;) {
					values[inputValue3++] = values2[(inputValue2 & 0xfc0) + (inputValue >> 6)] >>> intermediateValue9;
					inputValue += intermediateValue7;
					inputValue2 += intermediateValue8;
				}

				return;
			}
			while (intermediateValue2-- > 0) {
				int intermediateValue11;
				if ((intermediateValue11 = values2[(inputValue2 & 0xfc0)
						+ (inputValue >> 6)] >>> intermediateValue9) != 0)
					values[inputValue3] = intermediateValue11;
				inputValue3++;
				inputValue += intermediateValue7;
				inputValue2 += intermediateValue8;
				if ((intermediateValue11 = values2[(inputValue2 & 0xfc0)
						+ (inputValue >> 6)] >>> intermediateValue9) != 0)
					values[inputValue3] = intermediateValue11;
				inputValue3++;
				inputValue += intermediateValue7;
				inputValue2 += intermediateValue8;
				if ((intermediateValue11 = values2[(inputValue2 & 0xfc0)
						+ (inputValue >> 6)] >>> intermediateValue9) != 0)
					values[inputValue3] = intermediateValue11;
				inputValue3++;
				inputValue += intermediateValue7;
				inputValue2 += intermediateValue8;
				if ((intermediateValue11 = values2[(inputValue2 & 0xfc0)
						+ (inputValue >> 6)] >>> intermediateValue9) != 0)
					values[inputValue3] = intermediateValue11;
				inputValue3++;
				inputValue += intermediateValue7;
				inputValue2 += intermediateValue8;
				if ((intermediateValue11 = values2[(inputValue2 & 0xfc0)
						+ (inputValue >> 6)] >>> intermediateValue9) != 0)
					values[inputValue3] = intermediateValue11;
				inputValue3++;
				inputValue += intermediateValue7;
				inputValue2 += intermediateValue8;
				if ((intermediateValue11 = values2[(inputValue2 & 0xfc0)
						+ (inputValue >> 6)] >>> intermediateValue9) != 0)
					values[inputValue3] = intermediateValue11;
				inputValue3++;
				inputValue += intermediateValue7;
				inputValue2 += intermediateValue8;
				if ((intermediateValue11 = values2[(inputValue2 & 0xfc0)
						+ (inputValue >> 6)] >>> intermediateValue9) != 0)
					values[inputValue3] = intermediateValue11;
				inputValue3++;
				inputValue += intermediateValue7;
				inputValue2 += intermediateValue8;
				if ((intermediateValue11 = values2[(inputValue2 & 0xfc0)
						+ (inputValue >> 6)] >>> intermediateValue9) != 0)
					values[inputValue3] = intermediateValue11;
				inputValue3++;
				inputValue = intermediateValue3;
				inputValue2 = intermediateValue4;
				inputValue8 += inputValue11;
				inputValue9 += inputValue12;
				inputValue10 += inputValue13;
				int intermediateValue12 = inputValue10 >> 12;
				if (intermediateValue12 != 0) {
					intermediateValue3 = inputValue8 / intermediateValue12;
					intermediateValue4 = inputValue9 / intermediateValue12;
					if (intermediateValue3 < 7)
						intermediateValue3 = 7;
					else if (intermediateValue3 > 4032)
						intermediateValue3 = 4032;
				}
				intermediateValue7 = intermediateValue3 - inputValue >> 3;
				intermediateValue8 = intermediateValue4 - inputValue2 >> 3;
				inputValue6 += intermediateValue;
				inputValue += (inputValue6 & 0x600000) >> 3;
				intermediateValue9 = inputValue6 >> 23;
			}
			for (intermediateValue2 = inputValue5 - inputValue4 & 7; intermediateValue2-- > 0;) {
				int intermediateValue13;
				if ((intermediateValue13 = values2[(inputValue2 & 0xfc0)
						+ (inputValue >> 6)] >>> intermediateValue9) != 0)
					values[inputValue3] = intermediateValue13;
				inputValue3++;
				inputValue += intermediateValue7;
				inputValue2 += intermediateValue8;
			}

			return;
		}
		int intermediateValue14 = 0;
		int intermediateValue15 = 0;
		int intermediateValue16 = inputValue4 - centerX;
		inputValue8 += (inputValue11 >> 3) * intermediateValue16;
		inputValue9 += (inputValue12 >> 3) * intermediateValue16;
		inputValue10 += (inputValue13 >> 3) * intermediateValue16;
		int intermediateValue17 = inputValue10 >> 14;
		if (intermediateValue17 != 0) {
			inputValue = inputValue8 / intermediateValue17;
			inputValue2 = inputValue9 / intermediateValue17;
			if (inputValue < 0)
				inputValue = 0;
			else if (inputValue > 16256)
				inputValue = 16256;
		}
		inputValue8 += inputValue11;
		inputValue9 += inputValue12;
		inputValue10 += inputValue13;
		intermediateValue17 = inputValue10 >> 14;
		if (intermediateValue17 != 0) {
			intermediateValue14 = inputValue8 / intermediateValue17;
			intermediateValue15 = inputValue9 / intermediateValue17;
			if (intermediateValue14 < 7)
				intermediateValue14 = 7;
			else if (intermediateValue14 > 16256)
				intermediateValue14 = 16256;
		}
		int intermediateValue18 = intermediateValue14 - inputValue >> 3;
		int intermediateValue19 = intermediateValue15 - inputValue2 >> 3;
		inputValue += inputValue6 & 0x600000;
		int intermediateValue20 = inputValue6 >> 23;
		if (opaqueTexture) {
			while (intermediateValue2-- > 0) {
				values[inputValue3++] = values2[(inputValue2 & 0x3f80) + (inputValue >> 7)] >>> intermediateValue20;
				inputValue += intermediateValue18;
				inputValue2 += intermediateValue19;
				values[inputValue3++] = values2[(inputValue2 & 0x3f80) + (inputValue >> 7)] >>> intermediateValue20;
				inputValue += intermediateValue18;
				inputValue2 += intermediateValue19;
				values[inputValue3++] = values2[(inputValue2 & 0x3f80) + (inputValue >> 7)] >>> intermediateValue20;
				inputValue += intermediateValue18;
				inputValue2 += intermediateValue19;
				values[inputValue3++] = values2[(inputValue2 & 0x3f80) + (inputValue >> 7)] >>> intermediateValue20;
				inputValue += intermediateValue18;
				inputValue2 += intermediateValue19;
				values[inputValue3++] = values2[(inputValue2 & 0x3f80) + (inputValue >> 7)] >>> intermediateValue20;
				inputValue += intermediateValue18;
				inputValue2 += intermediateValue19;
				values[inputValue3++] = values2[(inputValue2 & 0x3f80) + (inputValue >> 7)] >>> intermediateValue20;
				inputValue += intermediateValue18;
				inputValue2 += intermediateValue19;
				values[inputValue3++] = values2[(inputValue2 & 0x3f80) + (inputValue >> 7)] >>> intermediateValue20;
				inputValue += intermediateValue18;
				inputValue2 += intermediateValue19;
				values[inputValue3++] = values2[(inputValue2 & 0x3f80) + (inputValue >> 7)] >>> intermediateValue20;
				inputValue = intermediateValue14;
				inputValue2 = intermediateValue15;
				inputValue8 += inputValue11;
				inputValue9 += inputValue12;
				inputValue10 += inputValue13;
				int intermediateValue21 = inputValue10 >> 14;
				if (intermediateValue21 != 0) {
					intermediateValue14 = inputValue8 / intermediateValue21;
					intermediateValue15 = inputValue9 / intermediateValue21;
					if (intermediateValue14 < 7)
						intermediateValue14 = 7;
					else if (intermediateValue14 > 16256)
						intermediateValue14 = 16256;
				}
				intermediateValue18 = intermediateValue14 - inputValue >> 3;
				intermediateValue19 = intermediateValue15 - inputValue2 >> 3;
				inputValue6 += intermediateValue;
				inputValue += inputValue6 & 0x600000;
				intermediateValue20 = inputValue6 >> 23;
			}
			for (intermediateValue2 = inputValue5 - inputValue4 & 7; intermediateValue2-- > 0;) {
				values[inputValue3++] = values2[(inputValue2 & 0x3f80) + (inputValue >> 7)] >>> intermediateValue20;
				inputValue += intermediateValue18;
				inputValue2 += intermediateValue19;
			}

			return;
		}
		while (intermediateValue2-- > 0) {
			int intermediateValue22;
			if ((intermediateValue22 = values2[(inputValue2 & 0x3f80)
					+ (inputValue >> 7)] >>> intermediateValue20) != 0)
				values[inputValue3] = intermediateValue22;
			inputValue3++;
			inputValue += intermediateValue18;
			inputValue2 += intermediateValue19;
			if ((intermediateValue22 = values2[(inputValue2 & 0x3f80)
					+ (inputValue >> 7)] >>> intermediateValue20) != 0)
				values[inputValue3] = intermediateValue22;
			inputValue3++;
			inputValue += intermediateValue18;
			inputValue2 += intermediateValue19;
			if ((intermediateValue22 = values2[(inputValue2 & 0x3f80)
					+ (inputValue >> 7)] >>> intermediateValue20) != 0)
				values[inputValue3] = intermediateValue22;
			inputValue3++;
			inputValue += intermediateValue18;
			inputValue2 += intermediateValue19;
			if ((intermediateValue22 = values2[(inputValue2 & 0x3f80)
					+ (inputValue >> 7)] >>> intermediateValue20) != 0)
				values[inputValue3] = intermediateValue22;
			inputValue3++;
			inputValue += intermediateValue18;
			inputValue2 += intermediateValue19;
			if ((intermediateValue22 = values2[(inputValue2 & 0x3f80)
					+ (inputValue >> 7)] >>> intermediateValue20) != 0)
				values[inputValue3] = intermediateValue22;
			inputValue3++;
			inputValue += intermediateValue18;
			inputValue2 += intermediateValue19;
			if ((intermediateValue22 = values2[(inputValue2 & 0x3f80)
					+ (inputValue >> 7)] >>> intermediateValue20) != 0)
				values[inputValue3] = intermediateValue22;
			inputValue3++;
			inputValue += intermediateValue18;
			inputValue2 += intermediateValue19;
			if ((intermediateValue22 = values2[(inputValue2 & 0x3f80)
					+ (inputValue >> 7)] >>> intermediateValue20) != 0)
				values[inputValue3] = intermediateValue22;
			inputValue3++;
			inputValue += intermediateValue18;
			inputValue2 += intermediateValue19;
			if ((intermediateValue22 = values2[(inputValue2 & 0x3f80)
					+ (inputValue >> 7)] >>> intermediateValue20) != 0)
				values[inputValue3] = intermediateValue22;
			inputValue3++;
			inputValue = intermediateValue14;
			inputValue2 = intermediateValue15;
			inputValue8 += inputValue11;
			inputValue9 += inputValue12;
			inputValue10 += inputValue13;
			int intermediateValue23 = inputValue10 >> 14;
			if (intermediateValue23 != 0) {
				intermediateValue14 = inputValue8 / intermediateValue23;
				intermediateValue15 = inputValue9 / intermediateValue23;
				if (intermediateValue14 < 7)
					intermediateValue14 = 7;
				else if (intermediateValue14 > 16256)
					intermediateValue14 = 16256;
			}
			intermediateValue18 = intermediateValue14 - inputValue >> 3;
			intermediateValue19 = intermediateValue15 - inputValue2 >> 3;
			inputValue6 += intermediateValue;
			inputValue += inputValue6 & 0x600000;
			intermediateValue20 = inputValue6 >> 23;
		}
		for (int loopIndex = inputValue5 - inputValue4 & 7; loopIndex-- > 0;) {
			int intermediateValue24;
			if ((intermediateValue24 = values2[(inputValue2 & 0x3f80)
					+ (inputValue >> 7)] >>> intermediateValue20) != 0)
				values[inputValue3] = intermediateValue24;
			inputValue3++;
			inputValue += intermediateValue18;
			inputValue2 += intermediateValue19;
		}

	}

}
