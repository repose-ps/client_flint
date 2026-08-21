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
	public static int centerX;
	public static int centerY;

	public static int[] reciprocal15 = new int[512];
	public static int[] reciprocal16 = new int[2048];
	public static int[] SINE = new int[2048];
	public static int[] COSINE = new int[2048];
	public static int[] scanlineOffsets;

	private static int loadedTextureCount;
	public static IndexedImage[] textures = new IndexedImage[50];
	private static boolean[] textureHasTransparency = new boolean[50];
	private static int[] averageTextureColors = new int[50];
	private static int texturePoolAvailable;
	private static int[][] texturePool;
	private static int[][] texturePixels = new int[50][];
	public static int[] textureLastUsed = new int[50];
	public static int textureCycle;
	public static int[] HSL_TO_RGB = new int[0x10000];
	private static int[][] texturePalettes = new int[50][];

	static {
		for (int i = 1; i < 512; i++)
			reciprocal15[i] = 32768 / i;
		for (int i = 1; i < 2048; i++)
			reciprocal16[i] = 0x10000 / i;
		for (int angle = 0; angle < 2048; angle++) {
			SINE[angle] = (int) (65536D * Math.sin(angle * 0.0030679614999999999D));
			COSINE[angle] = (int) (65536D * Math.cos(angle * 0.0030679614999999999D));
		}
	}

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

	/** Builds scanline offsets from the current {@link Rasterizer} dimensions. */
	public static void setDefaultBounds() {
		scanlineOffsets = new int[Rasterizer.height];
		for (int y = 0; y < Rasterizer.height; y++)
			scanlineOffsets[y] = Rasterizer.width * y;
		centerX = Rasterizer.width / 2;
		centerY = Rasterizer.height / 2;
	}

	/** Builds projection scanline offsets for an explicit viewport. */
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

	/** Returns the cached gamma-adjusted average palette colour for a texture. */
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

	/** Returns an expanded texture buffer to the shared pool. */
	public static void releaseTexture(int textureId) {
		if (texturePixels[textureId] == null)
			return;
		texturePool[texturePoolAvailable++] = texturePixels[textureId];
		texturePixels[textureId] = null;
	}

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
					double q = lightness < 0.5D ? lightness * (1.0D + saturation)
							: (lightness + saturation) - lightness * saturation;
					double p = 2D * lightness - q;
					red = hueToRgb(p, q, hue + 0.33333333333333331D);
					green = hueToRgb(p, q, hue);
					blue = hueToRgb(p, q, hue - 0.33333333333333331D);
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
				for (int i = 0; i < source.length; i++) {
					int rgb = adjustBrightness(source[i], brightness);
					if ((rgb & 0xf8f8ff) == 0 && i != 0)
						rgb = 1;
					texturePalettes[textureId][i] = rgb;
				}
			}
		}
		for (int textureId = 0; textureId < 50; textureId++)
			releaseTexture(textureId);
	}

	private static double hueToRgb(double p, double q, double hue) {
		if (hue > 1.0D)
			hue--;
		if (hue < 0.0D)
			hue++;
		if (6D * hue < 1.0D)
			return p + (q - p) * 6D * hue;
		if (2D * hue < 1.0D)
			return q;
		if (3D * hue < 2D)
			return p + (q - p) * (0.66666666666666663D - hue) * 6D;
		return p;
	}

	public static int adjustBrightness(int rgb, double brightness) {
		double red = Math.pow((double) (rgb >> 16) / 256D, brightness);
		double green = Math.pow((double) (rgb >> 8 & 0xff) / 256D, brightness);
		double blue = Math.pow((double) (rgb & 0xff) / 256D, brightness);
		return ((int) (red * 256D) << 16) + ((int) (green * 256D) << 8) + (int) (blue * 256D);
	}

	public static void drawGouraudTriangle(int yA, int yB, int yC, int xA, int xB, int xC, int shadeA, int shadeB,
			int shadeC) {
		drawGouraudTriangleInternal(yA, yB, yC, xA, xB, xC, shadeA, shadeB, shadeC);
	}

	public static void drawFlatTriangle(int yA, int yB, int yC, int xA, int xB, int xC, int rgb) {
		drawFlatTriangleInternal(yA, yB, yC, xA, xB, xC, rgb);
	}

	public static void drawTexturedTriangle(int yA, int yB, int yC, int xA, int xB, int xC, int shadeA, int shadeB,
			int shadeC, int textureXA, int textureXB, int textureXC, int textureYA, int textureYB, int textureYC,
			int textureZA, int textureZB, int textureZC, int textureId) {
		drawTexturedTriangleInternal(yA, yB, yC, xA, xB, xC, shadeA, shadeB, shadeC, textureXA, textureXB, textureXC,
				textureYA, textureYB, textureYC, textureZA, textureZB, textureZC, textureId);
	}

	private static void drawGouraudTriangleInternal(int i, int j, int k, int l, int i1, int j1, int k1, int l1,
			int i2) {
		int j2 = 0;
		int k2 = 0;
		if (j != i) {
			j2 = (i1 - l << 16) / (j - i);
			k2 = (l1 - k1 << 15) / (j - i);
		}
		int l2 = 0;
		int i3 = 0;
		if (k != j) {
			l2 = (j1 - i1 << 16) / (k - j);
			i3 = (i2 - l1 << 15) / (k - j);
		}
		int j3 = 0;
		int k3 = 0;
		if (k != i) {
			j3 = (l - j1 << 16) / (i - k);
			k3 = (k1 - i2 << 15) / (i - k);
		}
		if (i <= j && i <= k) {
			if (i >= Rasterizer.bottomY)
				return;
			if (j > Rasterizer.bottomY)
				j = Rasterizer.bottomY;
			if (k > Rasterizer.bottomY)
				k = Rasterizer.bottomY;
			if (j < k) {
				j1 = l <<= 16;
				i2 = k1 <<= 15;
				if (i < 0) {
					j1 -= j3 * i;
					l -= j2 * i;
					i2 -= k3 * i;
					k1 -= k2 * i;
					i = 0;
				}
				i1 <<= 16;
				l1 <<= 15;
				if (j < 0) {
					i1 -= l2 * j;
					l1 -= i3 * j;
					j = 0;
				}
				if (i != j && j3 < j2 || i == j && j3 > l2) {
					k -= j;
					j -= i;
					for (i = scanlineOffsets[i]; --j >= 0; i += Rasterizer.width) {
						drawGouraudScanline(Rasterizer.pixels, i, 0, 0, j1 >> 16, l >> 16, i2 >> 7, k1 >> 7);
						j1 += j3;
						l += j2;
						i2 += k3;
						k1 += k2;
					}

					while (--k >= 0) {
						drawGouraudScanline(Rasterizer.pixels, i, 0, 0, j1 >> 16, i1 >> 16, i2 >> 7, l1 >> 7);
						j1 += j3;
						i1 += l2;
						i2 += k3;
						l1 += i3;
						i += Rasterizer.width;
					}
					return;
				}
				k -= j;
				j -= i;
				for (i = scanlineOffsets[i]; --j >= 0; i += Rasterizer.width) {
					drawGouraudScanline(Rasterizer.pixels, i, 0, 0, l >> 16, j1 >> 16, k1 >> 7, i2 >> 7);
					j1 += j3;
					l += j2;
					i2 += k3;
					k1 += k2;
				}

				while (--k >= 0) {
					drawGouraudScanline(Rasterizer.pixels, i, 0, 0, i1 >> 16, j1 >> 16, l1 >> 7, i2 >> 7);
					j1 += j3;
					i1 += l2;
					i2 += k3;
					l1 += i3;
					i += Rasterizer.width;
				}
				return;
			}
			i1 = l <<= 16;
			l1 = k1 <<= 15;
			if (i < 0) {
				i1 -= j3 * i;
				l -= j2 * i;
				l1 -= k3 * i;
				k1 -= k2 * i;
				i = 0;
			}
			j1 <<= 16;
			i2 <<= 15;
			if (k < 0) {
				j1 -= l2 * k;
				i2 -= i3 * k;
				k = 0;
			}
			if (i != k && j3 < j2 || i == k && l2 > j2) {
				j -= k;
				k -= i;
				for (i = scanlineOffsets[i]; --k >= 0; i += Rasterizer.width) {
					drawGouraudScanline(Rasterizer.pixels, i, 0, 0, i1 >> 16, l >> 16, l1 >> 7, k1 >> 7);
					i1 += j3;
					l += j2;
					l1 += k3;
					k1 += k2;
				}

				while (--j >= 0) {
					drawGouraudScanline(Rasterizer.pixels, i, 0, 0, j1 >> 16, l >> 16, i2 >> 7, k1 >> 7);
					j1 += l2;
					l += j2;
					i2 += i3;
					k1 += k2;
					i += Rasterizer.width;
				}
				return;
			}
			j -= k;
			k -= i;
			for (i = scanlineOffsets[i]; --k >= 0; i += Rasterizer.width) {
				drawGouraudScanline(Rasterizer.pixels, i, 0, 0, l >> 16, i1 >> 16, k1 >> 7, l1 >> 7);
				i1 += j3;
				l += j2;
				l1 += k3;
				k1 += k2;
			}

			while (--j >= 0) {
				drawGouraudScanline(Rasterizer.pixels, i, 0, 0, l >> 16, j1 >> 16, k1 >> 7, i2 >> 7);
				j1 += l2;
				l += j2;
				i2 += i3;
				k1 += k2;
				i += Rasterizer.width;
			}
			return;
		}
		if (j <= k) {
			if (j >= Rasterizer.bottomY)
				return;
			if (k > Rasterizer.bottomY)
				k = Rasterizer.bottomY;
			if (i > Rasterizer.bottomY)
				i = Rasterizer.bottomY;
			if (k < i) {
				l = i1 <<= 16;
				k1 = l1 <<= 15;
				if (j < 0) {
					l -= j2 * j;
					i1 -= l2 * j;
					k1 -= k2 * j;
					l1 -= i3 * j;
					j = 0;
				}
				j1 <<= 16;
				i2 <<= 15;
				if (k < 0) {
					j1 -= j3 * k;
					i2 -= k3 * k;
					k = 0;
				}
				if (j != k && j2 < l2 || j == k && j2 > j3) {
					i -= k;
					k -= j;
					for (j = scanlineOffsets[j]; --k >= 0; j += Rasterizer.width) {
						drawGouraudScanline(Rasterizer.pixels, j, 0, 0, l >> 16, i1 >> 16, k1 >> 7, l1 >> 7);
						l += j2;
						i1 += l2;
						k1 += k2;
						l1 += i3;
					}

					while (--i >= 0) {
						drawGouraudScanline(Rasterizer.pixels, j, 0, 0, l >> 16, j1 >> 16, k1 >> 7, i2 >> 7);
						l += j2;
						j1 += j3;
						k1 += k2;
						i2 += k3;
						j += Rasterizer.width;
					}
					return;
				}
				i -= k;
				k -= j;
				for (j = scanlineOffsets[j]; --k >= 0; j += Rasterizer.width) {
					drawGouraudScanline(Rasterizer.pixels, j, 0, 0, i1 >> 16, l >> 16, l1 >> 7, k1 >> 7);
					l += j2;
					i1 += l2;
					k1 += k2;
					l1 += i3;
				}

				while (--i >= 0) {
					drawGouraudScanline(Rasterizer.pixels, j, 0, 0, j1 >> 16, l >> 16, i2 >> 7, k1 >> 7);
					l += j2;
					j1 += j3;
					k1 += k2;
					i2 += k3;
					j += Rasterizer.width;
				}
				return;
			}
			j1 = i1 <<= 16;
			i2 = l1 <<= 15;
			if (j < 0) {
				j1 -= j2 * j;
				i1 -= l2 * j;
				i2 -= k2 * j;
				l1 -= i3 * j;
				j = 0;
			}
			l <<= 16;
			k1 <<= 15;
			if (i < 0) {
				l -= j3 * i;
				k1 -= k3 * i;
				i = 0;
			}
			if (j2 < l2) {
				k -= i;
				i -= j;
				for (j = scanlineOffsets[j]; --i >= 0; j += Rasterizer.width) {
					drawGouraudScanline(Rasterizer.pixels, j, 0, 0, j1 >> 16, i1 >> 16, i2 >> 7, l1 >> 7);
					j1 += j2;
					i1 += l2;
					i2 += k2;
					l1 += i3;
				}

				while (--k >= 0) {
					drawGouraudScanline(Rasterizer.pixels, j, 0, 0, l >> 16, i1 >> 16, k1 >> 7, l1 >> 7);
					l += j3;
					i1 += l2;
					k1 += k3;
					l1 += i3;
					j += Rasterizer.width;
				}
				return;
			}
			k -= i;
			i -= j;
			for (j = scanlineOffsets[j]; --i >= 0; j += Rasterizer.width) {
				drawGouraudScanline(Rasterizer.pixels, j, 0, 0, i1 >> 16, j1 >> 16, l1 >> 7, i2 >> 7);
				j1 += j2;
				i1 += l2;
				i2 += k2;
				l1 += i3;
			}

			while (--k >= 0) {
				drawGouraudScanline(Rasterizer.pixels, j, 0, 0, i1 >> 16, l >> 16, l1 >> 7, k1 >> 7);
				l += j3;
				i1 += l2;
				k1 += k3;
				l1 += i3;
				j += Rasterizer.width;
			}
			return;
		}
		if (k >= Rasterizer.bottomY)
			return;
		if (i > Rasterizer.bottomY)
			i = Rasterizer.bottomY;
		if (j > Rasterizer.bottomY)
			j = Rasterizer.bottomY;
		if (i < j) {
			i1 = j1 <<= 16;
			l1 = i2 <<= 15;
			if (k < 0) {
				i1 -= l2 * k;
				j1 -= j3 * k;
				l1 -= i3 * k;
				i2 -= k3 * k;
				k = 0;
			}
			l <<= 16;
			k1 <<= 15;
			if (i < 0) {
				l -= j2 * i;
				k1 -= k2 * i;
				i = 0;
			}
			if (l2 < j3) {
				j -= i;
				i -= k;
				for (k = scanlineOffsets[k]; --i >= 0; k += Rasterizer.width) {
					drawGouraudScanline(Rasterizer.pixels, k, 0, 0, i1 >> 16, j1 >> 16, l1 >> 7, i2 >> 7);
					i1 += l2;
					j1 += j3;
					l1 += i3;
					i2 += k3;
				}

				while (--j >= 0) {
					drawGouraudScanline(Rasterizer.pixels, k, 0, 0, i1 >> 16, l >> 16, l1 >> 7, k1 >> 7);
					i1 += l2;
					l += j2;
					l1 += i3;
					k1 += k2;
					k += Rasterizer.width;
				}
				return;
			}
			j -= i;
			i -= k;
			for (k = scanlineOffsets[k]; --i >= 0; k += Rasterizer.width) {
				drawGouraudScanline(Rasterizer.pixels, k, 0, 0, j1 >> 16, i1 >> 16, i2 >> 7, l1 >> 7);
				i1 += l2;
				j1 += j3;
				l1 += i3;
				i2 += k3;
			}

			while (--j >= 0) {
				drawGouraudScanline(Rasterizer.pixels, k, 0, 0, l >> 16, i1 >> 16, k1 >> 7, l1 >> 7);
				i1 += l2;
				l += j2;
				l1 += i3;
				k1 += k2;
				k += Rasterizer.width;
			}
			return;
		}
		l = j1 <<= 16;
		k1 = i2 <<= 15;
		if (k < 0) {
			l -= l2 * k;
			j1 -= j3 * k;
			k1 -= i3 * k;
			i2 -= k3 * k;
			k = 0;
		}
		i1 <<= 16;
		l1 <<= 15;
		if (j < 0) {
			i1 -= j2 * j;
			l1 -= k2 * j;
			j = 0;
		}
		if (l2 < j3) {
			i -= j;
			j -= k;
			for (k = scanlineOffsets[k]; --j >= 0; k += Rasterizer.width) {
				drawGouraudScanline(Rasterizer.pixels, k, 0, 0, l >> 16, j1 >> 16, k1 >> 7, i2 >> 7);
				l += l2;
				j1 += j3;
				k1 += i3;
				i2 += k3;
			}

			while (--i >= 0) {
				drawGouraudScanline(Rasterizer.pixels, k, 0, 0, i1 >> 16, j1 >> 16, l1 >> 7, i2 >> 7);
				i1 += j2;
				j1 += j3;
				l1 += k2;
				i2 += k3;
				k += Rasterizer.width;
			}
			return;
		}
		i -= j;
		j -= k;
		for (k = scanlineOffsets[k]; --j >= 0; k += Rasterizer.width) {
			drawGouraudScanline(Rasterizer.pixels, k, 0, 0, j1 >> 16, l >> 16, i2 >> 7, k1 >> 7);
			l += l2;
			j1 += j3;
			k1 += i3;
			i2 += k3;
		}

		while (--i >= 0) {
			drawGouraudScanline(Rasterizer.pixels, k, 0, 0, j1 >> 16, i1 >> 16, i2 >> 7, l1 >> 7);
			i1 += j2;
			j1 += j3;
			l1 += k2;
			i2 += k3;
			k += Rasterizer.width;
		}
	}

	private static void drawGouraudScanline(int ai[], int i, int j, int k, int l, int i1, int j1, int k1) {
		if (gouraudBlockShading) {
			int l1;
			if (restrictEdges) {
				if (i1 - l > 3)
					l1 = (k1 - j1) / (i1 - l);
				else
					l1 = 0;
				if (i1 > Rasterizer.viewportRx)
					i1 = Rasterizer.viewportRx;
				if (l < 0) {
					j1 -= l * l1;
					l = 0;
				}
				if (l >= i1)
					return;
				i += l;
				k = i1 - l >> 2;
				l1 <<= 2;
			} else {
				if (l >= i1)
					return;
				i += l;
				k = i1 - l >> 2;
				if (k > 0)
					l1 = (k1 - j1) * reciprocal15[k] >> 15;
				else
					l1 = 0;
			}
			if (alpha == 0) {
				while (--k >= 0) {
					j = HSL_TO_RGB[j1 >> 8];
					j1 += l1;
					ai[i++] = j;
					ai[i++] = j;
					ai[i++] = j;
					ai[i++] = j;
				}
				k = i1 - l & 3;
				if (k > 0) {
					j = HSL_TO_RGB[j1 >> 8];
					do
						ai[i++] = j;
					while (--k > 0);
					return;
				}
			} else {
				int j2 = alpha;
				int l2 = 256 - alpha;
				while (--k >= 0) {
					j = HSL_TO_RGB[j1 >> 8];
					j1 += l1;
					j = ((j & 0xff00ff) * l2 >> 8 & 0xff00ff) + ((j & 0xff00) * l2 >> 8 & 0xff00);
					ai[i++] = j + ((ai[i] & 0xff00ff) * j2 >> 8 & 0xff00ff) + ((ai[i] & 0xff00) * j2 >> 8 & 0xff00);
					ai[i++] = j + ((ai[i] & 0xff00ff) * j2 >> 8 & 0xff00ff) + ((ai[i] & 0xff00) * j2 >> 8 & 0xff00);
					ai[i++] = j + ((ai[i] & 0xff00ff) * j2 >> 8 & 0xff00ff) + ((ai[i] & 0xff00) * j2 >> 8 & 0xff00);
					ai[i++] = j + ((ai[i] & 0xff00ff) * j2 >> 8 & 0xff00ff) + ((ai[i] & 0xff00) * j2 >> 8 & 0xff00);
				}
				k = i1 - l & 3;
				if (k > 0) {
					j = HSL_TO_RGB[j1 >> 8];
					j = ((j & 0xff00ff) * l2 >> 8 & 0xff00ff) + ((j & 0xff00) * l2 >> 8 & 0xff00);
					do
						ai[i++] = j + ((ai[i] & 0xff00ff) * j2 >> 8 & 0xff00ff) + ((ai[i] & 0xff00) * j2 >> 8 & 0xff00);
					while (--k > 0);
				}
			}
			return;
		}
		if (l >= i1)
			return;
		int i2 = (k1 - j1) / (i1 - l);
		if (restrictEdges) {
			if (i1 > Rasterizer.viewportRx)
				i1 = Rasterizer.viewportRx;
			if (l < 0) {
				j1 -= l * i2;
				l = 0;
			}
			if (l >= i1)
				return;
		}
		i += l;
		k = i1 - l;
		if (alpha == 0) {
			do {
				ai[i++] = HSL_TO_RGB[j1 >> 8];
				j1 += i2;
			} while (--k > 0);
			return;
		}
		int k2 = alpha;
		int i3 = 256 - alpha;
		do {
			j = HSL_TO_RGB[j1 >> 8];
			j1 += i2;
			j = ((j & 0xff00ff) * i3 >> 8 & 0xff00ff) + ((j & 0xff00) * i3 >> 8 & 0xff00);
			ai[i++] = j + ((ai[i] & 0xff00ff) * k2 >> 8 & 0xff00ff) + ((ai[i] & 0xff00) * k2 >> 8 & 0xff00);
		} while (--k > 0);
	}

	private static void drawFlatTriangleInternal(int i, int j, int k, int l, int i1, int j1, int k1) {
		int l1 = 0;
		if (j != i)
			l1 = (i1 - l << 16) / (j - i);
		int i2 = 0;
		if (k != j)
			i2 = (j1 - i1 << 16) / (k - j);
		int j2 = 0;
		if (k != i)
			j2 = (l - j1 << 16) / (i - k);
		if (i <= j && i <= k) {
			if (i >= Rasterizer.bottomY)
				return;
			if (j > Rasterizer.bottomY)
				j = Rasterizer.bottomY;
			if (k > Rasterizer.bottomY)
				k = Rasterizer.bottomY;
			if (j < k) {
				j1 = l <<= 16;
				if (i < 0) {
					j1 -= j2 * i;
					l -= l1 * i;
					i = 0;
				}
				i1 <<= 16;
				if (j < 0) {
					i1 -= i2 * j;
					j = 0;
				}
				if (i != j && j2 < l1 || i == j && j2 > i2) {
					k -= j;
					j -= i;
					for (i = scanlineOffsets[i]; --j >= 0; i += Rasterizer.width) {
						drawFlatScanline(Rasterizer.pixels, i, k1, 0, j1 >> 16, l >> 16);
						j1 += j2;
						l += l1;
					}

					while (--k >= 0) {
						drawFlatScanline(Rasterizer.pixels, i, k1, 0, j1 >> 16, i1 >> 16);
						j1 += j2;
						i1 += i2;
						i += Rasterizer.width;
					}
					return;
				}
				k -= j;
				j -= i;
				for (i = scanlineOffsets[i]; --j >= 0; i += Rasterizer.width) {
					drawFlatScanline(Rasterizer.pixels, i, k1, 0, l >> 16, j1 >> 16);
					j1 += j2;
					l += l1;
				}

				while (--k >= 0) {
					drawFlatScanline(Rasterizer.pixels, i, k1, 0, i1 >> 16, j1 >> 16);
					j1 += j2;
					i1 += i2;
					i += Rasterizer.width;
				}
				return;
			}
			i1 = l <<= 16;
			if (i < 0) {
				i1 -= j2 * i;
				l -= l1 * i;
				i = 0;
			}
			j1 <<= 16;
			if (k < 0) {
				j1 -= i2 * k;
				k = 0;
			}
			if (i != k && j2 < l1 || i == k && i2 > l1) {
				j -= k;
				k -= i;
				for (i = scanlineOffsets[i]; --k >= 0; i += Rasterizer.width) {
					drawFlatScanline(Rasterizer.pixels, i, k1, 0, i1 >> 16, l >> 16);
					i1 += j2;
					l += l1;
				}

				while (--j >= 0) {
					drawFlatScanline(Rasterizer.pixels, i, k1, 0, j1 >> 16, l >> 16);
					j1 += i2;
					l += l1;
					i += Rasterizer.width;
				}
				return;
			}
			j -= k;
			k -= i;
			for (i = scanlineOffsets[i]; --k >= 0; i += Rasterizer.width) {
				drawFlatScanline(Rasterizer.pixels, i, k1, 0, l >> 16, i1 >> 16);
				i1 += j2;
				l += l1;
			}

			while (--j >= 0) {
				drawFlatScanline(Rasterizer.pixels, i, k1, 0, l >> 16, j1 >> 16);
				j1 += i2;
				l += l1;
				i += Rasterizer.width;
			}
			return;
		}
		if (j <= k) {
			if (j >= Rasterizer.bottomY)
				return;
			if (k > Rasterizer.bottomY)
				k = Rasterizer.bottomY;
			if (i > Rasterizer.bottomY)
				i = Rasterizer.bottomY;
			if (k < i) {
				l = i1 <<= 16;
				if (j < 0) {
					l -= l1 * j;
					i1 -= i2 * j;
					j = 0;
				}
				j1 <<= 16;
				if (k < 0) {
					j1 -= j2 * k;
					k = 0;
				}
				if (j != k && l1 < i2 || j == k && l1 > j2) {
					i -= k;
					k -= j;
					for (j = scanlineOffsets[j]; --k >= 0; j += Rasterizer.width) {
						drawFlatScanline(Rasterizer.pixels, j, k1, 0, l >> 16, i1 >> 16);
						l += l1;
						i1 += i2;
					}

					while (--i >= 0) {
						drawFlatScanline(Rasterizer.pixels, j, k1, 0, l >> 16, j1 >> 16);
						l += l1;
						j1 += j2;
						j += Rasterizer.width;
					}
					return;
				}
				i -= k;
				k -= j;
				for (j = scanlineOffsets[j]; --k >= 0; j += Rasterizer.width) {
					drawFlatScanline(Rasterizer.pixels, j, k1, 0, i1 >> 16, l >> 16);
					l += l1;
					i1 += i2;
				}

				while (--i >= 0) {
					drawFlatScanline(Rasterizer.pixels, j, k1, 0, j1 >> 16, l >> 16);
					l += l1;
					j1 += j2;
					j += Rasterizer.width;
				}
				return;
			}
			j1 = i1 <<= 16;
			if (j < 0) {
				j1 -= l1 * j;
				i1 -= i2 * j;
				j = 0;
			}
			l <<= 16;
			if (i < 0) {
				l -= j2 * i;
				i = 0;
			}
			if (l1 < i2) {
				k -= i;
				i -= j;
				for (j = scanlineOffsets[j]; --i >= 0; j += Rasterizer.width) {
					drawFlatScanline(Rasterizer.pixels, j, k1, 0, j1 >> 16, i1 >> 16);
					j1 += l1;
					i1 += i2;
				}

				while (--k >= 0) {
					drawFlatScanline(Rasterizer.pixels, j, k1, 0, l >> 16, i1 >> 16);
					l += j2;
					i1 += i2;
					j += Rasterizer.width;
				}
				return;
			}
			k -= i;
			i -= j;
			for (j = scanlineOffsets[j]; --i >= 0; j += Rasterizer.width) {
				drawFlatScanline(Rasterizer.pixels, j, k1, 0, i1 >> 16, j1 >> 16);
				j1 += l1;
				i1 += i2;
			}

			while (--k >= 0) {
				drawFlatScanline(Rasterizer.pixels, j, k1, 0, i1 >> 16, l >> 16);
				l += j2;
				i1 += i2;
				j += Rasterizer.width;
			}
			return;
		}
		if (k >= Rasterizer.bottomY)
			return;
		if (i > Rasterizer.bottomY)
			i = Rasterizer.bottomY;
		if (j > Rasterizer.bottomY)
			j = Rasterizer.bottomY;
		if (i < j) {
			i1 = j1 <<= 16;
			if (k < 0) {
				i1 -= i2 * k;
				j1 -= j2 * k;
				k = 0;
			}
			l <<= 16;
			if (i < 0) {
				l -= l1 * i;
				i = 0;
			}
			if (i2 < j2) {
				j -= i;
				i -= k;
				for (k = scanlineOffsets[k]; --i >= 0; k += Rasterizer.width) {
					drawFlatScanline(Rasterizer.pixels, k, k1, 0, i1 >> 16, j1 >> 16);
					i1 += i2;
					j1 += j2;
				}

				while (--j >= 0) {
					drawFlatScanline(Rasterizer.pixels, k, k1, 0, i1 >> 16, l >> 16);
					i1 += i2;
					l += l1;
					k += Rasterizer.width;
				}
				return;
			}
			j -= i;
			i -= k;
			for (k = scanlineOffsets[k]; --i >= 0; k += Rasterizer.width) {
				drawFlatScanline(Rasterizer.pixels, k, k1, 0, j1 >> 16, i1 >> 16);
				i1 += i2;
				j1 += j2;
			}

			while (--j >= 0) {
				drawFlatScanline(Rasterizer.pixels, k, k1, 0, l >> 16, i1 >> 16);
				i1 += i2;
				l += l1;
				k += Rasterizer.width;
			}
			return;
		}
		l = j1 <<= 16;
		if (k < 0) {
			l -= i2 * k;
			j1 -= j2 * k;
			k = 0;
		}
		i1 <<= 16;
		if (j < 0) {
			i1 -= l1 * j;
			j = 0;
		}
		if (i2 < j2) {
			i -= j;
			j -= k;
			for (k = scanlineOffsets[k]; --j >= 0; k += Rasterizer.width) {
				drawFlatScanline(Rasterizer.pixels, k, k1, 0, l >> 16, j1 >> 16);
				l += i2;
				j1 += j2;
			}

			while (--i >= 0) {
				drawFlatScanline(Rasterizer.pixels, k, k1, 0, i1 >> 16, j1 >> 16);
				i1 += l1;
				j1 += j2;
				k += Rasterizer.width;
			}
			return;
		}
		i -= j;
		j -= k;
		for (k = scanlineOffsets[k]; --j >= 0; k += Rasterizer.width) {
			drawFlatScanline(Rasterizer.pixels, k, k1, 0, j1 >> 16, l >> 16);
			l += i2;
			j1 += j2;
		}

		while (--i >= 0) {
			drawFlatScanline(Rasterizer.pixels, k, k1, 0, j1 >> 16, i1 >> 16);
			i1 += l1;
			j1 += j2;
			k += Rasterizer.width;
		}
	}

	private static void drawFlatScanline(int ai[], int i, int j, int k, int l, int i1) {
		if (restrictEdges) {
			if (i1 > Rasterizer.viewportRx)
				i1 = Rasterizer.viewportRx;
			if (l < 0)
				l = 0;
		}
		if (l >= i1)
			return;
		i += l;
		k = i1 - l >> 2;
		if (alpha == 0) {
			while (--k >= 0) {
				ai[i++] = j;
				ai[i++] = j;
				ai[i++] = j;
				ai[i++] = j;
			}
			for (k = i1 - l & 3; --k >= 0;)
				ai[i++] = j;

			return;
		}
		int j1 = alpha;
		int k1 = 256 - alpha;
		j = ((j & 0xff00ff) * k1 >> 8 & 0xff00ff) + ((j & 0xff00) * k1 >> 8 & 0xff00);
		while (--k >= 0) {
			ai[i++] = j + ((ai[i] & 0xff00ff) * j1 >> 8 & 0xff00ff) + ((ai[i] & 0xff00) * j1 >> 8 & 0xff00);
			ai[i++] = j + ((ai[i] & 0xff00ff) * j1 >> 8 & 0xff00ff) + ((ai[i] & 0xff00) * j1 >> 8 & 0xff00);
			ai[i++] = j + ((ai[i] & 0xff00ff) * j1 >> 8 & 0xff00ff) + ((ai[i] & 0xff00) * j1 >> 8 & 0xff00);
			ai[i++] = j + ((ai[i] & 0xff00ff) * j1 >> 8 & 0xff00ff) + ((ai[i] & 0xff00) * j1 >> 8 & 0xff00);
		}
		for (k = i1 - l & 3; --k >= 0;)
			ai[i++] = j + ((ai[i] & 0xff00ff) * j1 >> 8 & 0xff00ff) + ((ai[i] & 0xff00) * j1 >> 8 & 0xff00);

	}

	private static void drawTexturedTriangleInternal(int i, int j, int k, int l, int i1, int j1, int k1, int l1, int i2,
			int j2, int k2, int l2, int i3, int j3, int k3, int l3, int i4, int j4, int k4) {
		int ai[] = getTexturePixels(k4);
		opaqueTexture = !textureHasTransparency[k4];
		k2 = j2 - k2;
		j3 = i3 - j3;
		i4 = l3 - i4;
		l2 -= j2;
		k3 -= i3;
		j4 -= l3;
		int l4 = l2 * i3 - k3 * j2 << 14;
		int i5 = k3 * l3 - j4 * i3 << 8;
		int j5 = j4 * j2 - l2 * l3 << 5;
		int k5 = k2 * i3 - j3 * j2 << 14;
		int l5 = j3 * l3 - i4 * i3 << 8;
		int i6 = i4 * j2 - k2 * l3 << 5;
		int j6 = j3 * l2 - k2 * k3 << 14;
		int k6 = i4 * k3 - j3 * j4 << 8;
		int l6 = k2 * j4 - i4 * l2 << 5;
		int i7 = 0;
		int j7 = 0;
		if (j != i) {
			i7 = (i1 - l << 16) / (j - i);
			j7 = (l1 - k1 << 16) / (j - i);
		}
		int k7 = 0;
		int l7 = 0;
		if (k != j) {
			k7 = (j1 - i1 << 16) / (k - j);
			l7 = (i2 - l1 << 16) / (k - j);
		}
		int i8 = 0;
		int j8 = 0;
		if (k != i) {
			i8 = (l - j1 << 16) / (i - k);
			j8 = (k1 - i2 << 16) / (i - k);
		}
		if (i <= j && i <= k) {
			if (i >= Rasterizer.bottomY)
				return;
			if (j > Rasterizer.bottomY)
				j = Rasterizer.bottomY;
			if (k > Rasterizer.bottomY)
				k = Rasterizer.bottomY;
			if (j < k) {
				j1 = l <<= 16;
				i2 = k1 <<= 16;
				if (i < 0) {
					j1 -= i8 * i;
					l -= i7 * i;
					i2 -= j8 * i;
					k1 -= j7 * i;
					i = 0;
				}
				i1 <<= 16;
				l1 <<= 16;
				if (j < 0) {
					i1 -= k7 * j;
					l1 -= l7 * j;
					j = 0;
				}
				int k8 = i - centerY;
				l4 += j5 * k8;
				k5 += i6 * k8;
				j6 += l6 * k8;
				if (i != j && i8 < i7 || i == j && i8 > k7) {
					k -= j;
					j -= i;
					i = scanlineOffsets[i];
					while (--j >= 0) {
						drawTexturedScanline(Rasterizer.pixels, ai, 0, 0, i, j1 >> 16, l >> 16, i2 >> 8, k1 >> 8, l4,
								k5, j6, i5, l5, k6);
						j1 += i8;
						l += i7;
						i2 += j8;
						k1 += j7;
						i += Rasterizer.width;
						l4 += j5;
						k5 += i6;
						j6 += l6;
					}
					while (--k >= 0) {
						drawTexturedScanline(Rasterizer.pixels, ai, 0, 0, i, j1 >> 16, i1 >> 16, i2 >> 8, l1 >> 8, l4,
								k5, j6, i5, l5, k6);
						j1 += i8;
						i1 += k7;
						i2 += j8;
						l1 += l7;
						i += Rasterizer.width;
						l4 += j5;
						k5 += i6;
						j6 += l6;
					}
					return;
				}
				k -= j;
				j -= i;
				i = scanlineOffsets[i];
				while (--j >= 0) {
					drawTexturedScanline(Rasterizer.pixels, ai, 0, 0, i, l >> 16, j1 >> 16, k1 >> 8, i2 >> 8, l4, k5,
							j6, i5, l5, k6);
					j1 += i8;
					l += i7;
					i2 += j8;
					k1 += j7;
					i += Rasterizer.width;
					l4 += j5;
					k5 += i6;
					j6 += l6;
				}
				while (--k >= 0) {
					drawTexturedScanline(Rasterizer.pixels, ai, 0, 0, i, i1 >> 16, j1 >> 16, l1 >> 8, i2 >> 8, l4, k5,
							j6, i5, l5, k6);
					j1 += i8;
					i1 += k7;
					i2 += j8;
					l1 += l7;
					i += Rasterizer.width;
					l4 += j5;
					k5 += i6;
					j6 += l6;
				}
				return;
			}
			i1 = l <<= 16;
			l1 = k1 <<= 16;
			if (i < 0) {
				i1 -= i8 * i;
				l -= i7 * i;
				l1 -= j8 * i;
				k1 -= j7 * i;
				i = 0;
			}
			j1 <<= 16;
			i2 <<= 16;
			if (k < 0) {
				j1 -= k7 * k;
				i2 -= l7 * k;
				k = 0;
			}
			int l8 = i - centerY;
			l4 += j5 * l8;
			k5 += i6 * l8;
			j6 += l6 * l8;
			if (i != k && i8 < i7 || i == k && k7 > i7) {
				j -= k;
				k -= i;
				i = scanlineOffsets[i];
				while (--k >= 0) {
					drawTexturedScanline(Rasterizer.pixels, ai, 0, 0, i, i1 >> 16, l >> 16, l1 >> 8, k1 >> 8, l4, k5,
							j6, i5, l5, k6);
					i1 += i8;
					l += i7;
					l1 += j8;
					k1 += j7;
					i += Rasterizer.width;
					l4 += j5;
					k5 += i6;
					j6 += l6;
				}
				while (--j >= 0) {
					drawTexturedScanline(Rasterizer.pixels, ai, 0, 0, i, j1 >> 16, l >> 16, i2 >> 8, k1 >> 8, l4, k5,
							j6, i5, l5, k6);
					j1 += k7;
					l += i7;
					i2 += l7;
					k1 += j7;
					i += Rasterizer.width;
					l4 += j5;
					k5 += i6;
					j6 += l6;
				}
				return;
			}
			j -= k;
			k -= i;
			i = scanlineOffsets[i];
			while (--k >= 0) {
				drawTexturedScanline(Rasterizer.pixels, ai, 0, 0, i, l >> 16, i1 >> 16, k1 >> 8, l1 >> 8, l4, k5, j6,
						i5, l5, k6);
				i1 += i8;
				l += i7;
				l1 += j8;
				k1 += j7;
				i += Rasterizer.width;
				l4 += j5;
				k5 += i6;
				j6 += l6;
			}
			while (--j >= 0) {
				drawTexturedScanline(Rasterizer.pixels, ai, 0, 0, i, l >> 16, j1 >> 16, k1 >> 8, i2 >> 8, l4, k5, j6,
						i5, l5, k6);
				j1 += k7;
				l += i7;
				i2 += l7;
				k1 += j7;
				i += Rasterizer.width;
				l4 += j5;
				k5 += i6;
				j6 += l6;
			}
			return;
		}
		if (j <= k) {
			if (j >= Rasterizer.bottomY)
				return;
			if (k > Rasterizer.bottomY)
				k = Rasterizer.bottomY;
			if (i > Rasterizer.bottomY)
				i = Rasterizer.bottomY;
			if (k < i) {
				l = i1 <<= 16;
				k1 = l1 <<= 16;
				if (j < 0) {
					l -= i7 * j;
					i1 -= k7 * j;
					k1 -= j7 * j;
					l1 -= l7 * j;
					j = 0;
				}
				j1 <<= 16;
				i2 <<= 16;
				if (k < 0) {
					j1 -= i8 * k;
					i2 -= j8 * k;
					k = 0;
				}
				int i9 = j - centerY;
				l4 += j5 * i9;
				k5 += i6 * i9;
				j6 += l6 * i9;
				if (j != k && i7 < k7 || j == k && i7 > i8) {
					i -= k;
					k -= j;
					j = scanlineOffsets[j];
					while (--k >= 0) {
						drawTexturedScanline(Rasterizer.pixels, ai, 0, 0, j, l >> 16, i1 >> 16, k1 >> 8, l1 >> 8, l4,
								k5, j6, i5, l5, k6);
						l += i7;
						i1 += k7;
						k1 += j7;
						l1 += l7;
						j += Rasterizer.width;
						l4 += j5;
						k5 += i6;
						j6 += l6;
					}
					while (--i >= 0) {
						drawTexturedScanline(Rasterizer.pixels, ai, 0, 0, j, l >> 16, j1 >> 16, k1 >> 8, i2 >> 8, l4,
								k5, j6, i5, l5, k6);
						l += i7;
						j1 += i8;
						k1 += j7;
						i2 += j8;
						j += Rasterizer.width;
						l4 += j5;
						k5 += i6;
						j6 += l6;
					}
					return;
				}
				i -= k;
				k -= j;
				j = scanlineOffsets[j];
				while (--k >= 0) {
					drawTexturedScanline(Rasterizer.pixels, ai, 0, 0, j, i1 >> 16, l >> 16, l1 >> 8, k1 >> 8, l4, k5,
							j6, i5, l5, k6);
					l += i7;
					i1 += k7;
					k1 += j7;
					l1 += l7;
					j += Rasterizer.width;
					l4 += j5;
					k5 += i6;
					j6 += l6;
				}
				while (--i >= 0) {
					drawTexturedScanline(Rasterizer.pixels, ai, 0, 0, j, j1 >> 16, l >> 16, i2 >> 8, k1 >> 8, l4, k5,
							j6, i5, l5, k6);
					l += i7;
					j1 += i8;
					k1 += j7;
					i2 += j8;
					j += Rasterizer.width;
					l4 += j5;
					k5 += i6;
					j6 += l6;
				}
				return;
			}
			j1 = i1 <<= 16;
			i2 = l1 <<= 16;
			if (j < 0) {
				j1 -= i7 * j;
				i1 -= k7 * j;
				i2 -= j7 * j;
				l1 -= l7 * j;
				j = 0;
			}
			l <<= 16;
			k1 <<= 16;
			if (i < 0) {
				l -= i8 * i;
				k1 -= j8 * i;
				i = 0;
			}
			int j9 = j - centerY;
			l4 += j5 * j9;
			k5 += i6 * j9;
			j6 += l6 * j9;
			if (i7 < k7) {
				k -= i;
				i -= j;
				j = scanlineOffsets[j];
				while (--i >= 0) {
					drawTexturedScanline(Rasterizer.pixels, ai, 0, 0, j, j1 >> 16, i1 >> 16, i2 >> 8, l1 >> 8, l4, k5,
							j6, i5, l5, k6);
					j1 += i7;
					i1 += k7;
					i2 += j7;
					l1 += l7;
					j += Rasterizer.width;
					l4 += j5;
					k5 += i6;
					j6 += l6;
				}
				while (--k >= 0) {
					drawTexturedScanline(Rasterizer.pixels, ai, 0, 0, j, l >> 16, i1 >> 16, k1 >> 8, l1 >> 8, l4, k5,
							j6, i5, l5, k6);
					l += i8;
					i1 += k7;
					k1 += j8;
					l1 += l7;
					j += Rasterizer.width;
					l4 += j5;
					k5 += i6;
					j6 += l6;
				}
				return;
			}
			k -= i;
			i -= j;
			j = scanlineOffsets[j];
			while (--i >= 0) {
				drawTexturedScanline(Rasterizer.pixels, ai, 0, 0, j, i1 >> 16, j1 >> 16, l1 >> 8, i2 >> 8, l4, k5, j6,
						i5, l5, k6);
				j1 += i7;
				i1 += k7;
				i2 += j7;
				l1 += l7;
				j += Rasterizer.width;
				l4 += j5;
				k5 += i6;
				j6 += l6;
			}
			while (--k >= 0) {
				drawTexturedScanline(Rasterizer.pixels, ai, 0, 0, j, i1 >> 16, l >> 16, l1 >> 8, k1 >> 8, l4, k5, j6,
						i5, l5, k6);
				l += i8;
				i1 += k7;
				k1 += j8;
				l1 += l7;
				j += Rasterizer.width;
				l4 += j5;
				k5 += i6;
				j6 += l6;
			}
			return;
		}
		if (k >= Rasterizer.bottomY)
			return;
		if (i > Rasterizer.bottomY)
			i = Rasterizer.bottomY;
		if (j > Rasterizer.bottomY)
			j = Rasterizer.bottomY;
		if (i < j) {
			i1 = j1 <<= 16;
			l1 = i2 <<= 16;
			if (k < 0) {
				i1 -= k7 * k;
				j1 -= i8 * k;
				l1 -= l7 * k;
				i2 -= j8 * k;
				k = 0;
			}
			l <<= 16;
			k1 <<= 16;
			if (i < 0) {
				l -= i7 * i;
				k1 -= j7 * i;
				i = 0;
			}
			int k9 = k - centerY;
			l4 += j5 * k9;
			k5 += i6 * k9;
			j6 += l6 * k9;
			if (k7 < i8) {
				j -= i;
				i -= k;
				k = scanlineOffsets[k];
				while (--i >= 0) {
					drawTexturedScanline(Rasterizer.pixels, ai, 0, 0, k, i1 >> 16, j1 >> 16, l1 >> 8, i2 >> 8, l4, k5,
							j6, i5, l5, k6);
					i1 += k7;
					j1 += i8;
					l1 += l7;
					i2 += j8;
					k += Rasterizer.width;
					l4 += j5;
					k5 += i6;
					j6 += l6;
				}
				while (--j >= 0) {
					drawTexturedScanline(Rasterizer.pixels, ai, 0, 0, k, i1 >> 16, l >> 16, l1 >> 8, k1 >> 8, l4, k5,
							j6, i5, l5, k6);
					i1 += k7;
					l += i7;
					l1 += l7;
					k1 += j7;
					k += Rasterizer.width;
					l4 += j5;
					k5 += i6;
					j6 += l6;
				}
				return;
			}
			j -= i;
			i -= k;
			k = scanlineOffsets[k];
			while (--i >= 0) {
				drawTexturedScanline(Rasterizer.pixels, ai, 0, 0, k, j1 >> 16, i1 >> 16, i2 >> 8, l1 >> 8, l4, k5, j6,
						i5, l5, k6);
				i1 += k7;
				j1 += i8;
				l1 += l7;
				i2 += j8;
				k += Rasterizer.width;
				l4 += j5;
				k5 += i6;
				j6 += l6;
			}
			while (--j >= 0) {
				drawTexturedScanline(Rasterizer.pixels, ai, 0, 0, k, l >> 16, i1 >> 16, k1 >> 8, l1 >> 8, l4, k5, j6,
						i5, l5, k6);
				i1 += k7;
				l += i7;
				l1 += l7;
				k1 += j7;
				k += Rasterizer.width;
				l4 += j5;
				k5 += i6;
				j6 += l6;
			}
			return;
		}
		l = j1 <<= 16;
		k1 = i2 <<= 16;
		if (k < 0) {
			l -= k7 * k;
			j1 -= i8 * k;
			k1 -= l7 * k;
			i2 -= j8 * k;
			k = 0;
		}
		i1 <<= 16;
		l1 <<= 16;
		if (j < 0) {
			i1 -= i7 * j;
			l1 -= j7 * j;
			j = 0;
		}
		int l9 = k - centerY;
		l4 += j5 * l9;
		k5 += i6 * l9;
		j6 += l6 * l9;
		if (k7 < i8) {
			i -= j;
			j -= k;
			k = scanlineOffsets[k];
			while (--j >= 0) {
				drawTexturedScanline(Rasterizer.pixels, ai, 0, 0, k, l >> 16, j1 >> 16, k1 >> 8, i2 >> 8, l4, k5, j6,
						i5, l5, k6);
				l += k7;
				j1 += i8;
				k1 += l7;
				i2 += j8;
				k += Rasterizer.width;
				l4 += j5;
				k5 += i6;
				j6 += l6;
			}
			while (--i >= 0) {
				drawTexturedScanline(Rasterizer.pixels, ai, 0, 0, k, i1 >> 16, j1 >> 16, l1 >> 8, i2 >> 8, l4, k5, j6,
						i5, l5, k6);
				i1 += i7;
				j1 += i8;
				l1 += j7;
				i2 += j8;
				k += Rasterizer.width;
				l4 += j5;
				k5 += i6;
				j6 += l6;
			}
			return;
		}
		i -= j;
		j -= k;
		k = scanlineOffsets[k];
		while (--j >= 0) {
			drawTexturedScanline(Rasterizer.pixels, ai, 0, 0, k, j1 >> 16, l >> 16, i2 >> 8, k1 >> 8, l4, k5, j6, i5,
					l5, k6);
			l += k7;
			j1 += i8;
			k1 += l7;
			i2 += j8;
			k += Rasterizer.width;
			l4 += j5;
			k5 += i6;
			j6 += l6;
		}
		while (--i >= 0) {
			drawTexturedScanline(Rasterizer.pixels, ai, 0, 0, k, j1 >> 16, i1 >> 16, i2 >> 8, l1 >> 8, l4, k5, j6, i5,
					l5, k6);
			i1 += i7;
			j1 += i8;
			l1 += j7;
			i2 += j8;
			k += Rasterizer.width;
			l4 += j5;
			k5 += i6;
			j6 += l6;
		}
	}

	private static void drawTexturedScanline(int ai[], int ai1[], int i, int j, int k, int l, int i1, int j1, int k1,
			int l1, int i2, int j2, int k2, int l2, int i3) {
		if (l >= i1)
			return;
		int j3;
		int k3;
		if (restrictEdges) {
			j3 = (k1 - j1) / (i1 - l);
			if (i1 > Rasterizer.viewportRx)
				i1 = Rasterizer.viewportRx;
			if (l < 0) {
				j1 -= l * j3;
				l = 0;
			}
			if (l >= i1)
				return;
			k3 = i1 - l >> 3;
			j3 <<= 12;
			j1 <<= 9;
		} else {
			if (i1 - l > 7) {
				k3 = i1 - l >> 3;
				j3 = (k1 - j1) * reciprocal15[k3] >> 6;
			} else {
				k3 = 0;
				j3 = 0;
			}
			j1 <<= 9;
		}
		k += l;
		if (lowMemory) {
			int i4 = 0;
			int k4 = 0;
			int k6 = l - centerX;
			l1 += (k2 >> 3) * k6;
			i2 += (l2 >> 3) * k6;
			j2 += (i3 >> 3) * k6;
			int i5 = j2 >> 12;
			if (i5 != 0) {
				i = l1 / i5;
				j = i2 / i5;
				if (i < 0)
					i = 0;
				else if (i > 4032)
					i = 4032;
			}
			l1 += k2;
			i2 += l2;
			j2 += i3;
			i5 = j2 >> 12;
			if (i5 != 0) {
				i4 = l1 / i5;
				k4 = i2 / i5;
				if (i4 < 7)
					i4 = 7;
				else if (i4 > 4032)
					i4 = 4032;
			}
			int i7 = i4 - i >> 3;
			int k7 = k4 - j >> 3;
			i += (j1 & 0x600000) >> 3;
			int i8 = j1 >> 23;
			if (opaqueTexture) {
				while (k3-- > 0) {
					ai[k++] = ai1[(j & 0xfc0) + (i >> 6)] >>> i8;
					i += i7;
					j += k7;
					ai[k++] = ai1[(j & 0xfc0) + (i >> 6)] >>> i8;
					i += i7;
					j += k7;
					ai[k++] = ai1[(j & 0xfc0) + (i >> 6)] >>> i8;
					i += i7;
					j += k7;
					ai[k++] = ai1[(j & 0xfc0) + (i >> 6)] >>> i8;
					i += i7;
					j += k7;
					ai[k++] = ai1[(j & 0xfc0) + (i >> 6)] >>> i8;
					i += i7;
					j += k7;
					ai[k++] = ai1[(j & 0xfc0) + (i >> 6)] >>> i8;
					i += i7;
					j += k7;
					ai[k++] = ai1[(j & 0xfc0) + (i >> 6)] >>> i8;
					i += i7;
					j += k7;
					ai[k++] = ai1[(j & 0xfc0) + (i >> 6)] >>> i8;
					i = i4;
					j = k4;
					l1 += k2;
					i2 += l2;
					j2 += i3;
					int j5 = j2 >> 12;
					if (j5 != 0) {
						i4 = l1 / j5;
						k4 = i2 / j5;
						if (i4 < 7)
							i4 = 7;
						else if (i4 > 4032)
							i4 = 4032;
					}
					i7 = i4 - i >> 3;
					k7 = k4 - j >> 3;
					j1 += j3;
					i += (j1 & 0x600000) >> 3;
					i8 = j1 >> 23;
				}
				for (k3 = i1 - l & 7; k3-- > 0;) {
					ai[k++] = ai1[(j & 0xfc0) + (i >> 6)] >>> i8;
					i += i7;
					j += k7;
				}

				return;
			}
			while (k3-- > 0) {
				int k8;
				if ((k8 = ai1[(j & 0xfc0) + (i >> 6)] >>> i8) != 0)
					ai[k] = k8;
				k++;
				i += i7;
				j += k7;
				if ((k8 = ai1[(j & 0xfc0) + (i >> 6)] >>> i8) != 0)
					ai[k] = k8;
				k++;
				i += i7;
				j += k7;
				if ((k8 = ai1[(j & 0xfc0) + (i >> 6)] >>> i8) != 0)
					ai[k] = k8;
				k++;
				i += i7;
				j += k7;
				if ((k8 = ai1[(j & 0xfc0) + (i >> 6)] >>> i8) != 0)
					ai[k] = k8;
				k++;
				i += i7;
				j += k7;
				if ((k8 = ai1[(j & 0xfc0) + (i >> 6)] >>> i8) != 0)
					ai[k] = k8;
				k++;
				i += i7;
				j += k7;
				if ((k8 = ai1[(j & 0xfc0) + (i >> 6)] >>> i8) != 0)
					ai[k] = k8;
				k++;
				i += i7;
				j += k7;
				if ((k8 = ai1[(j & 0xfc0) + (i >> 6)] >>> i8) != 0)
					ai[k] = k8;
				k++;
				i += i7;
				j += k7;
				if ((k8 = ai1[(j & 0xfc0) + (i >> 6)] >>> i8) != 0)
					ai[k] = k8;
				k++;
				i = i4;
				j = k4;
				l1 += k2;
				i2 += l2;
				j2 += i3;
				int k5 = j2 >> 12;
				if (k5 != 0) {
					i4 = l1 / k5;
					k4 = i2 / k5;
					if (i4 < 7)
						i4 = 7;
					else if (i4 > 4032)
						i4 = 4032;
				}
				i7 = i4 - i >> 3;
				k7 = k4 - j >> 3;
				j1 += j3;
				i += (j1 & 0x600000) >> 3;
				i8 = j1 >> 23;
			}
			for (k3 = i1 - l & 7; k3-- > 0;) {
				int l8;
				if ((l8 = ai1[(j & 0xfc0) + (i >> 6)] >>> i8) != 0)
					ai[k] = l8;
				k++;
				i += i7;
				j += k7;
			}

			return;
		}
		int j4 = 0;
		int l4 = 0;
		int l6 = l - centerX;
		l1 += (k2 >> 3) * l6;
		i2 += (l2 >> 3) * l6;
		j2 += (i3 >> 3) * l6;
		int l5 = j2 >> 14;
		if (l5 != 0) {
			i = l1 / l5;
			j = i2 / l5;
			if (i < 0)
				i = 0;
			else if (i > 16256)
				i = 16256;
		}
		l1 += k2;
		i2 += l2;
		j2 += i3;
		l5 = j2 >> 14;
		if (l5 != 0) {
			j4 = l1 / l5;
			l4 = i2 / l5;
			if (j4 < 7)
				j4 = 7;
			else if (j4 > 16256)
				j4 = 16256;
		}
		int j7 = j4 - i >> 3;
		int l7 = l4 - j >> 3;
		i += j1 & 0x600000;
		int j8 = j1 >> 23;
		if (opaqueTexture) {
			while (k3-- > 0) {
				ai[k++] = ai1[(j & 0x3f80) + (i >> 7)] >>> j8;
				i += j7;
				j += l7;
				ai[k++] = ai1[(j & 0x3f80) + (i >> 7)] >>> j8;
				i += j7;
				j += l7;
				ai[k++] = ai1[(j & 0x3f80) + (i >> 7)] >>> j8;
				i += j7;
				j += l7;
				ai[k++] = ai1[(j & 0x3f80) + (i >> 7)] >>> j8;
				i += j7;
				j += l7;
				ai[k++] = ai1[(j & 0x3f80) + (i >> 7)] >>> j8;
				i += j7;
				j += l7;
				ai[k++] = ai1[(j & 0x3f80) + (i >> 7)] >>> j8;
				i += j7;
				j += l7;
				ai[k++] = ai1[(j & 0x3f80) + (i >> 7)] >>> j8;
				i += j7;
				j += l7;
				ai[k++] = ai1[(j & 0x3f80) + (i >> 7)] >>> j8;
				i = j4;
				j = l4;
				l1 += k2;
				i2 += l2;
				j2 += i3;
				int i6 = j2 >> 14;
				if (i6 != 0) {
					j4 = l1 / i6;
					l4 = i2 / i6;
					if (j4 < 7)
						j4 = 7;
					else if (j4 > 16256)
						j4 = 16256;
				}
				j7 = j4 - i >> 3;
				l7 = l4 - j >> 3;
				j1 += j3;
				i += j1 & 0x600000;
				j8 = j1 >> 23;
			}
			for (k3 = i1 - l & 7; k3-- > 0;) {
				ai[k++] = ai1[(j & 0x3f80) + (i >> 7)] >>> j8;
				i += j7;
				j += l7;
			}

			return;
		}
		while (k3-- > 0) {
			int i9;
			if ((i9 = ai1[(j & 0x3f80) + (i >> 7)] >>> j8) != 0)
				ai[k] = i9;
			k++;
			i += j7;
			j += l7;
			if ((i9 = ai1[(j & 0x3f80) + (i >> 7)] >>> j8) != 0)
				ai[k] = i9;
			k++;
			i += j7;
			j += l7;
			if ((i9 = ai1[(j & 0x3f80) + (i >> 7)] >>> j8) != 0)
				ai[k] = i9;
			k++;
			i += j7;
			j += l7;
			if ((i9 = ai1[(j & 0x3f80) + (i >> 7)] >>> j8) != 0)
				ai[k] = i9;
			k++;
			i += j7;
			j += l7;
			if ((i9 = ai1[(j & 0x3f80) + (i >> 7)] >>> j8) != 0)
				ai[k] = i9;
			k++;
			i += j7;
			j += l7;
			if ((i9 = ai1[(j & 0x3f80) + (i >> 7)] >>> j8) != 0)
				ai[k] = i9;
			k++;
			i += j7;
			j += l7;
			if ((i9 = ai1[(j & 0x3f80) + (i >> 7)] >>> j8) != 0)
				ai[k] = i9;
			k++;
			i += j7;
			j += l7;
			if ((i9 = ai1[(j & 0x3f80) + (i >> 7)] >>> j8) != 0)
				ai[k] = i9;
			k++;
			i = j4;
			j = l4;
			l1 += k2;
			i2 += l2;
			j2 += i3;
			int j6 = j2 >> 14;
			if (j6 != 0) {
				j4 = l1 / j6;
				l4 = i2 / j6;
				if (j4 < 7)
					j4 = 7;
				else if (j4 > 16256)
					j4 = 16256;
			}
			j7 = j4 - i >> 3;
			l7 = l4 - j >> 3;
			j1 += j3;
			i += j1 & 0x600000;
			j8 = j1 >> 23;
		}
		for (int l3 = i1 - l & 7; l3-- > 0;) {
			int j9;
			if ((j9 = ai1[(j & 0x3f80) + (i >> 7)] >>> j8) != 0)
				ai[k] = j9;
			k++;
			i += j7;
			j += l7;
		}

	}

}