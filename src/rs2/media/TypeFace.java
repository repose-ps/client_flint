package rs2.media;

import java.util.Random;

import rs2.cache.Archive;
import rs2.net.Buffer;

/**
 * Revision-377 bitmap typeface renderer.
 *
 * <p>
 * Glyph masks are read from {@code <name>.dat}; their dimensions and storage
 * order are described by the shared {@code index.dat}. A non-zero glyph byte is
 * opaque. This class deliberately preserves the original client's one-pixel
 * right/bottom clipping quirk in the low-level glyph blitters.
 * </p>
 *
 * <p>
 * Formatted text uses the classic five-character tags such as {@code @red@},
 * {@code @str@}, and {@code @end@}. Tags are recognized only by methods whose
 * names explicitly mention formatting.
 * </p>
 */
public class TypeFace extends Rasterizer {

	private static final int GLYPH_COUNT = 256;
	private static final int SHADOW_COLOR = 0;
	private static final int STRIKETHROUGH_COLOR = 0x800000;

	private final byte[][] glyphMasks = new byte[GLYPH_COUNT][];
	private final int[] glyphWidths = new int[GLYPH_COUNT];
	private final int[] glyphHeights = new int[GLYPH_COUNT];
	private final int[] glyphXOffsets = new int[GLYPH_COUNT];
	private final int[] glyphYOffsets = new int[GLYPH_COUNT];
	private final int[] glyphAdvances = new int[GLYPH_COUNT];
	private final Random random = new Random();

	/** Maximum glyph height among character codes 0..127. */
	public int lineHeight;

	private boolean strikethrough;

	/**
	 * Loads one bitmap font from a media archive.
	 *
	 * @param useUppercaseISpaceWidth when true, the space advance is copied from
	 *                                {@code 'I'}; otherwise it is copied from
	 *                                {@code 'i'}. This odd rule is present in the
	 *                                original revision-377 client.
	 * @param archive                 media archive containing the font data and
	 *                                {@code index.dat}
	 * @param name                    font resource name without the {@code .dat}
	 *                                suffix
	 */
	public TypeFace(boolean useUppercaseISpaceWidth, Archive archive, String name) {
		Buffer glyphData = new Buffer(archive.read(name + ".dat"));
		Buffer index = new Buffer(archive.read("index.dat"));

		index.position = glyphData.readUnsignedShort() + 4;
		int indexVariantCount = index.readUnsignedByte();
		if (indexVariantCount > 0) {
			index.position += 3 * (indexVariantCount - 1);
		}

		for (int character = 0; character < GLYPH_COUNT; character++) {
			glyphXOffsets[character] = index.readUnsignedByte();
			glyphYOffsets[character] = index.readUnsignedByte();
			int width = glyphWidths[character] = index.readUnsignedShort();
			int height = glyphHeights[character] = index.readUnsignedShort();
			int storageOrder = index.readUnsignedByte();
			int pixelCount = width * height;

			byte[] mask = glyphMasks[character] = new byte[pixelCount];
			if (storageOrder == 0) {
				for (int pixel = 0; pixel < pixelCount; pixel++) {
					mask[pixel] = glyphData.readSignedByte();
				}
			} else if (storageOrder == 1) {
				for (int x = 0; x < width; x++) {
					for (int y = 0; y < height; y++) {
						mask[x + y * width] = glyphData.readSignedByte();
					}
				}
			}

			if (height > lineHeight && character < 128) {
				lineHeight = height;
			}

			// The file offsets are intentionally discarded. The original client
			// normalizes x to one pixel, then trims empty lower side columns.
			glyphXOffsets[character] = 1;
			glyphAdvances[character] = width + 2;

			int leftInk = 0;
			for (int y = height / 7; y < height; y++) {
				leftInk += mask[y * width];
			}
			if (leftInk <= height / 7) {
				glyphAdvances[character]--;
				glyphXOffsets[character] = 0;
			}

			int rightInk = 0;
			for (int y = height / 7; y < height; y++) {
				rightInk += mask[(width - 1) + y * width];
			}
			if (rightInk <= height / 7) {
				glyphAdvances[character]--;
			}
		}

		glyphAdvances[' '] = glyphAdvances[useUppercaseISpaceWidth ? 'I' : 'i'];
	}

	/** Draws unformatted text whose right edge is {@code rightX}. */
	public void drawRightAlignedText(String text, int rightX, int y, int color) {
		drawText(text, rightX - getTextWidth(text), y, color);
	}

	/** Draws unformatted text centered on {@code centerX}. */
	public void drawCenteredText(String text, int centerX, int y, int color) {
		drawText(text, centerX - getTextWidth(text) / 2, y, color);
	}

	/**
	 * Draws formatted text centered on {@code centerX}.
	 *
	 * @param shadow when true, each glyph also receives the original one-pixel
	 *               black drop shadow
	 */
	public void drawCenteredTextWithTags(String text, int centerX, int y, int color, boolean shadow) {
		drawTextWithTags(text, centerX - getFormattedTextWidth(text) / 2, y, color, shadow);
	}

	/**
	 * Returns the advance width while ignoring every five-character {@code @xxx@}
	 * span.
	 */
	public int getFormattedTextWidth(String text) {
		if (text == null) {
			return 0;
		}

		int width = 0;
		for (int index = 0; index < text.length(); index++) {
			if (text.charAt(index) == '@' && index + 4 < text.length() && text.charAt(index + 4) == '@') {
				index += 4;
			} else {
				width += glyphAdvances[text.charAt(index)];
			}
		}
		return width;
	}

	/** Returns the advance width of every character, including tag characters. */
	public int getTextWidth(String text) {
		if (text == null) {
			return 0;
		}

		int width = 0;
		for (int index = 0; index < text.length(); index++) {
			width += glyphAdvances[text.charAt(index)];
		}
		return width;
	}

	/** Draws unformatted opaque text. The supplied y-coordinate is the baseline. */
	public void drawText(String text, int x, int y, int color) {
		if (text == null) {
			return;
		}

		int drawY = y - lineHeight;
		for (int index = 0; index < text.length(); index++) {
			char character = text.charAt(index);
			if (character != ' ') {
				drawGlyph(glyphMasks[character], x + glyphXOffsets[character], drawY + glyphYOffsets[character],
						glyphWidths[character], glyphHeights[character], color);
			}
			x += glyphAdvances[character];
		}
	}

	/** Draws horizontally centered text with a vertical sine-wave displacement. */
	public void drawWaveText(String text, int centerX, int y, int color, int phase) {
		if (text == null) {
			return;
		}

		int x = centerX - getTextWidth(text) / 2;
		int drawY = y - lineHeight;
		for (int index = 0; index < text.length(); index++) {
			char character = text.charAt(index);
			if (character != ' ') {
				int waveY = (int) (Math.sin(index / 2.0D + phase / 5.0D) * 5.0D);
				drawGlyph(glyphMasks[character], x + glyphXOffsets[character], drawY + glyphYOffsets[character] + waveY,
						glyphWidths[character], glyphHeights[character], color);
			}
			x += glyphAdvances[character];
		}
	}

	/** Draws horizontally centered text with independent x/y sine waves. */
	public void drawWave2Text(String text, int centerX, int y, int color, int phase) {
		if (text == null) {
			return;
		}

		int x = centerX - getTextWidth(text) / 2;
		int drawY = y - lineHeight;
		for (int index = 0; index < text.length(); index++) {
			char character = text.charAt(index);
			if (character != ' ') {
				int waveX = (int) (Math.sin(index / 5.0D + phase / 5.0D) * 5.0D);
				int waveY = (int) (Math.sin(index / 3.0D + phase / 5.0D) * 5.0D);
				drawGlyph(glyphMasks[character], x + glyphXOffsets[character] + waveX,
						drawY + glyphYOffsets[character] + waveY, glyphWidths[character], glyphHeights[character],
						color);
			}
			x += glyphAdvances[character];
		}
	}

	/**
	 * Draws centered text with the revision-377 decaying wave effect. The amplitude
	 * is {@code max(0, 7 - age / 8)}.
	 */
	public void drawWaveAmplitudeText(String text, int centerX, int y, int color, int age, int phase) {
		if (text == null) {
			return;
		}

		double amplitude = 7.0D - age / 8.0D;
		if (amplitude < 0.0D) {
			amplitude = 0.0D;
		}

		int x = centerX - getTextWidth(text) / 2;
		int drawY = y - lineHeight;
		for (int index = 0; index < text.length(); index++) {
			char character = text.charAt(index);
			if (character != ' ') {
				int waveY = (int) (Math.sin(index / 1.5D + phase) * amplitude);
				drawGlyph(glyphMasks[character], x + glyphXOffsets[character], drawY + glyphYOffsets[character] + waveY,
						glyphWidths[character], glyphHeights[character], color);
			}
			x += glyphAdvances[character];
		}
	}

	/**
	 * Draws text with classic {@code @xxx@} color/strikethrough tags. Unknown tags
	 * are consumed exactly like recognized tags but do not alter state.
	 */
	public void drawTextWithTags(String text, int x, int y, int color, boolean shadow) {
		strikethrough = false;
		if (text == null) {
			return;
		}

		int startX = x;
		int drawY = y - lineHeight;
		for (int index = 0; index < text.length(); index++) {
			if (text.charAt(index) == '@' && index + 4 < text.length() && text.charAt(index + 4) == '@') {
				int parsedColor = parseTag(text.substring(index + 1, index + 4));
				if (parsedColor != -1) {
					color = parsedColor;
				}
				index += 4;
				continue;
			}

			char character = text.charAt(index);
			if (character != ' ') {
				if (shadow) {
					drawGlyph(glyphMasks[character], x + glyphXOffsets[character] + 1,
							drawY + glyphYOffsets[character] + 1, glyphWidths[character], glyphHeights[character],
							SHADOW_COLOR);
				}
				drawGlyph(glyphMasks[character], x + glyphXOffsets[character], drawY + glyphYOffsets[character],
						glyphWidths[character], glyphHeights[character], color);
			}
			x += glyphAdvances[character];
		}

		if (strikethrough) {
			Rasterizer.drawHorizontalLine(startX, drawY + (int) (lineHeight * 0.7D), x - startX, STRIKETHROUGH_COLOR);
		}
	}

	/**
	 * Draws the revision-377 random-alpha/jitter text effect.
	 *
	 * <p>
	 * The random source is deterministically reseeded for each call. Alpha is
	 * always 192..223 inclusive, and one extra horizontal pixel is inserted with
	 * probability 1/4 after each visible/text-space character.
	 * </p>
	 */
	public void drawRandomizedTextWithTags(String text, int x, int y, int color, int seed, boolean shadow) {
		if (text == null) {
			return;
		}

		random.setSeed(seed);
		int alpha = 192 + (random.nextInt() & 0x1f);
		int drawY = y - lineHeight;

		for (int index = 0; index < text.length(); index++) {
			if (text.charAt(index) == '@' && index + 4 < text.length() && text.charAt(index + 4) == '@') {
				int parsedColor = parseTag(text.substring(index + 1, index + 4));
				if (parsedColor != -1) {
					color = parsedColor;
				}
				index += 4;
				continue;
			}

			char character = text.charAt(index);
			if (character != ' ') {
				if (shadow) {
					drawGlyphAlpha(glyphMasks[character], x + glyphXOffsets[character] + 1,
							drawY + glyphYOffsets[character] + 1, glyphWidths[character], glyphHeights[character],
							SHADOW_COLOR, 192);
				}
				drawGlyphAlpha(glyphMasks[character], x + glyphXOffsets[character], drawY + glyphYOffsets[character],
						glyphWidths[character], glyphHeights[character], color, alpha);
			}

			x += glyphAdvances[character];
			if ((random.nextInt() & 3) == 0) {
				x++;
			}
		}
	}

	private int parseTag(String tag) {
		if (tag.equals("red"))
			return 0xff0000;
		if (tag.equals("gre"))
			return 0x00ff00;
		if (tag.equals("blu"))
			return 0x0000ff;
		if (tag.equals("yel"))
			return 0xffff00;
		if (tag.equals("cya"))
			return 0x00ffff;
		if (tag.equals("mag"))
			return 0xff00ff;
		if (tag.equals("whi"))
			return 0xffffff;
		if (tag.equals("bla"))
			return 0x000000;
		if (tag.equals("lre"))
			return 0xff9040;
		if (tag.equals("dre"))
			return 0x800000;
		if (tag.equals("dbl"))
			return 0x000080;
		if (tag.equals("or1"))
			return 0xffb000;
		if (tag.equals("or2"))
			return 0xff7000;
		if (tag.equals("or3"))
			return 0xff3000;
		if (tag.equals("gr1"))
			return 0xc0ff00;
		if (tag.equals("gr2"))
			return 0x80ff00;
		if (tag.equals("gr3"))
			return 0x40ff00;
		if (tag.equals("str"))
			strikethrough = true;
		if (tag.equals("end"))
			strikethrough = false;
		return -1;
	}

	private void drawGlyph(byte[] mask, int x, int y, int width, int height, int color) {
		int destination = x + y * Rasterizer.width;
		int destinationRowSkip = Rasterizer.width - width;
		int source = 0;
		int sourceRowSkip = 0;

		if (y < Rasterizer.topY) {
			int clipped = Rasterizer.topY - y;
			height -= clipped;
			y = Rasterizer.topY;
			source += clipped * width;
			destination += clipped * Rasterizer.width;
		}
		// Preserve the original font blitter's inclusive-looking +1 reduction,
		// even though the shared Rasterizer otherwise exposes half-open bounds.
		if (y + height >= Rasterizer.bottomY) {
			height -= (y + height - Rasterizer.bottomY) + 1;
		}
		if (x < Rasterizer.topX) {
			int clipped = Rasterizer.topX - x;
			width -= clipped;
			x = Rasterizer.topX;
			source += clipped;
			destination += clipped;
			sourceRowSkip += clipped;
			destinationRowSkip += clipped;
		}
		if (x + width >= Rasterizer.bottomX) {
			int clipped = (x + width - Rasterizer.bottomX) + 1;
			width -= clipped;
			sourceRowSkip += clipped;
			destinationRowSkip += clipped;
		}

		if (width > 0 && height > 0) {
			drawGlyphOpaque(Rasterizer.pixels, mask, color, source, destination, width, height, destinationRowSkip,
					sourceRowSkip);
		}
	}

	private static void drawGlyphOpaque(int[] destinationPixels, byte[] sourceMask, int color, int source,
			int destination, int width, int height, int destinationRowSkip, int sourceRowSkip) {
		int groupsOfFour = -(width >> 2);
		int remainder = -(width & 3);

		for (int row = -height; row < 0; row++) {
			for (int group = groupsOfFour; group < 0; group++) {
				if (sourceMask[source++] != 0)
					destinationPixels[destination++] = color;
				else
					destination++;
				if (sourceMask[source++] != 0)
					destinationPixels[destination++] = color;
				else
					destination++;
				if (sourceMask[source++] != 0)
					destinationPixels[destination++] = color;
				else
					destination++;
				if (sourceMask[source++] != 0)
					destinationPixels[destination++] = color;
				else
					destination++;
			}
			for (int pixel = remainder; pixel < 0; pixel++) {
				if (sourceMask[source++] != 0)
					destinationPixels[destination++] = color;
				else
					destination++;
			}
			destination += destinationRowSkip;
			source += sourceRowSkip;
		}
	}

	private void drawGlyphAlpha(byte[] mask, int x, int y, int width, int height, int color, int alpha) {
		int destination = x + y * Rasterizer.width;
		int destinationRowSkip = Rasterizer.width - width;
		int source = 0;
		int sourceRowSkip = 0;

		if (y < Rasterizer.topY) {
			int clipped = Rasterizer.topY - y;
			height -= clipped;
			y = Rasterizer.topY;
			source += clipped * width;
			destination += clipped * Rasterizer.width;
		}
		if (y + height >= Rasterizer.bottomY) {
			height -= (y + height - Rasterizer.bottomY) + 1;
		}
		if (x < Rasterizer.topX) {
			int clipped = Rasterizer.topX - x;
			width -= clipped;
			x = Rasterizer.topX;
			source += clipped;
			destination += clipped;
			sourceRowSkip += clipped;
			destinationRowSkip += clipped;
		}
		if (x + width >= Rasterizer.bottomX) {
			int clipped = (x + width - Rasterizer.bottomX) + 1;
			width -= clipped;
			sourceRowSkip += clipped;
			destinationRowSkip += clipped;
		}

		if (width > 0 && height > 0) {
			drawGlyphAlphaPixels(source, destinationRowSkip, sourceRowSkip, destination, alpha, Rasterizer.pixels,
					color, height, width, mask);
		}
	}

	private static void drawGlyphAlphaPixels(int source, int destinationRowSkip, int sourceRowSkip, int destination,
			int alpha, int[] destinationPixels, int color, int height, int width, byte[] sourceMask) {
		int sourceColor = (((color & 0xff00ff) * alpha & 0xff00ff00) + ((color & 0xff00) * alpha & 0xff0000)) >> 8;
		int inverseAlpha = 256 - alpha;

		for (int row = -height; row < 0; row++) {
			for (int pixel = -width; pixel < 0; pixel++) {
				if (sourceMask[source++] != 0) {
					int destinationColor = destinationPixels[destination];
					destinationPixels[destination++] = ((((destinationColor & 0xff00ff) * inverseAlpha & 0xff00ff00)
							+ ((destinationColor & 0xff00) * inverseAlpha & 0xff0000)) >> 8) + sourceColor;
				} else {
					destination++;
				}
			}
			destination += destinationRowSkip;
			source += sourceRowSkip;
		}
	}
}