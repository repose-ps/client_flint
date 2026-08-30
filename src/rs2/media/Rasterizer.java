package rs2.media;

import rs2.collection.DualNode;

/** Shared revision-377 software raster and clipping operations. */
public class Rasterizer extends DualNode {

	/** Creates a new rasterizer with its default client state. */
	public Rasterizer() {
	}

	/** Stores pixels values. */
	public static int[] pixels;
	/** Stores the current width. */
	public static int width;
	/** Stores the current height. */
	public static int height;
	/** Stores the current top Y. */
	public static int topY;
	/** Stores the current bottom Y. */
	public static int bottomY;
	/** Stores the current top X. */
	public static int topX;
	/** Stores the current bottom X. */
	public static int bottomX;
	/** Stores the current viewport rx. */
	public static int viewportRx;
	/** Stores the current center X. */
	public static int centerX;
	/** Stores the current center Y. */
	public static int centerY;

	/**
	 * Creates rasterizer.
	 *
	 * @param pixels the pixels
	 * @param width the width in pixels
	 * @param height the height in pixels
	 */
	public static void createRasterizer(int[] pixels, int width, int height) {
		Rasterizer.pixels = pixels;
		Rasterizer.width = width;
		Rasterizer.height = height;
		setCoordinates(0, 0, width, height);
	}

	/**
	 * Resets coordinates.
	 */
	public static void resetCoordinates() {
		topX = 0;
		topY = 0;
		bottomX = width;
		bottomY = height;
		viewportRx = bottomX - 1;
		centerX = bottomX / 2;
	}

	/**
	 * Resizes the operation.
	 *
	 * @param topX the top X
	 * @param topY the top Y
	 * @param bottomX the bottom X
	 * @param bottomY the bottom Y
	 */
	public static void resize(int topX, int topY, int bottomX, int bottomY) {
		if (Rasterizer.topX < topX) {
			Rasterizer.topX = topX;
		}
		if (Rasterizer.topY < topY) {
			Rasterizer.topY = topY;
		}
		if (Rasterizer.bottomX > bottomX) {
			Rasterizer.bottomX = bottomX;
		}
		if (Rasterizer.bottomY > bottomY) {
			Rasterizer.bottomY = bottomY;
		}
	}

	/**
	 * Sets coordinates.
	 *
	 * @param x the X coordinate
	 * @param y the Y coordinate
	 * @param width the width in pixels
	 * @param height the height in pixels
	 */
	public static void setCoordinates(int x, int y, int width, int height) {
		if (x < 0)
			x = 0;
		if (y < 0)
			y = 0;
		if (width > Rasterizer.width)
			width = Rasterizer.width;
		if (height > Rasterizer.height)
			height = Rasterizer.height;
		topX = x;
		topY = y;
		bottomX = width;
		bottomY = height;
		viewportRx = bottomX - 1;
		centerX = bottomX / 2;
		centerY = bottomY / 2;

	}

	/**
	 * Resets pixels.
	 */
	public static void resetPixels() {
		int pixelCount = width * height;
		for (int pixel = 0; pixel < pixelCount; pixel++)
			pixels[pixel] = 0;

	}

	/**
	 * Draws filled rectangle alpha.
	 *
	 * @param x the X coordinate
	 * @param y the Y coordinate
	 * @param width the width in pixels
	 * @param height the height in pixels
	 * @param colour the color value
	 * @param alpha the alpha
	 */
	public static void drawFilledRectangleAlpha(int x, int y, int width, int height, int colour, int alpha) {
		if (x < topX) {
			width -= topX - x;
			x = topX;
		}
		if (y < topY) {
			height -= topY - y;
			y = topY;
		}
		if (x + width > bottomX)
			width = bottomX - x;
		if (y + height > bottomY)
			height = bottomY - y;
		int a = 256 - alpha;
		int r = (colour >> 16 & 0xff) * alpha;
		int g = (colour >> 8 & 0xff) * alpha;
		int b = (colour & 0xff) * alpha;
		int widthOffset = Rasterizer.width - width;
		int pixel = x + y * Rasterizer.width;
		for (int heightCounter = 0; heightCounter < height; heightCounter++) {
			for (int widthCounter = -width; widthCounter < 0; widthCounter++) {
				int red = (pixels[pixel] >> 16 & 0xff) * a;
				int green = (pixels[pixel] >> 8 & 0xff) * a;
				int blue = (pixels[pixel] & 0xff) * a;
				int rgba = ((r + red >> 8) << 16) + ((g + green >> 8) << 8) + (b + blue >> 8);
				pixels[pixel++] = rgba;
			}

			pixel += widthOffset;
		}

	}

	/**
	 * Draws filled rectangle.
	 *
	 * @param x the X coordinate
	 * @param y the Y coordinate
	 * @param width the width in pixels
	 * @param height the height in pixels
	 * @param colour the color value
	 */
	public static void drawFilledRectangle(int x, int y, int width, int height, int colour) {
		if (x < topX) {
			width -= topX - x;
			x = topX;
		}
		if (y < topY) {
			height -= topY - y;
			y = topY;
		}
		if (x + width > bottomX)
			width = bottomX - x;
		if (y + height > bottomY)
			height = bottomY - y;
		int pixelOffset = Rasterizer.width - width;
		int pixel = x + y * Rasterizer.width;
		for (int heightCounter = -height; heightCounter < 0; heightCounter++) {
			for (int widthCounter = -width; widthCounter < 0; widthCounter++)
				pixels[pixel++] = colour;

			pixel += pixelOffset;
		}
	}

	/**
	 * Draws unfilled rectangle.
	 *
	 * @param x the X coordinate
	 * @param y the Y coordinate
	 * @param width the width in pixels
	 * @param height the height in pixels
	 * @param color the color value
	 */
	public static void drawUnfilledRectangle(int x, int y, int width, int height, int color) {
		drawHorizontalLine(x, y, width, color);
		drawHorizontalLine(x, (y + height) - 1, width, color);
		drawVerticalLine(x, y, height, color);
		drawVerticalLine((x + width) - 1, y, height, color);
	}

	/**
	 * Draws unfilled rectangle alpha.
	 *
	 * @param x the X coordinate
	 * @param y the Y coordinate
	 * @param width the width in pixels
	 * @param height the height in pixels
	 * @param colour the color value
	 * @param alpha the alpha
	 */
	public static void drawUnfilledRectangleAlpha(int x, int y, int width, int height, int colour, int alpha) {
		drawHorizontalLineAlpha(x, y, width, colour, alpha);
		drawHorizontalLineAlpha(x, (y + height) - 1, width, colour, alpha);
		if (height >= 3) {
			drawVerticalLineAlpha(x, y + 1, height - 2, colour, alpha);
			drawVerticalLineAlpha((x + width) - 1, y + 1, height - 2, colour, alpha);
		}
	}

	/**
	 * Draws horizontal line.
	 *
	 * @param x the X coordinate
	 * @param y the Y coordinate
	 * @param lenght the lenght
	 * @param colour the color value
	 */
	public static void drawHorizontalLine(int x, int y, int lenght, int colour) {
		if (y < topY || y >= bottomY)
			return;
		if (x < topX) {
			lenght -= topX - x;
			x = topX;
		}
		if (x + lenght > bottomX)
			lenght = bottomX - x;
		int pixelOffset = x + y * width;
		for (int pixel = 0; pixel < lenght; pixel++)
			pixels[pixelOffset + pixel] = colour;

	}

	/**
	 * Draws horizontal line alpha.
	 *
	 * @param x the X coordinate
	 * @param y the Y coordinate
	 * @param length the number of elements or bytes
	 * @param colour the color value
	 * @param alpha the alpha
	 */
	public static void drawHorizontalLineAlpha(int x, int y, int length, int colour, int alpha) {
		if (y < topY || y >= bottomY)
			return;
		if (x < topX) {
			length -= topX - x;
			x = topX;
		}
		if (x + length > bottomX)
			length = bottomX - x;
		int a = 256 - alpha;
		int r = (colour >> 16 & 0xff) * alpha;
		int g = (colour >> 8 & 0xff) * alpha;
		int b = (colour & 0xff) * alpha;
		int pixelOffset = x + y * width;
		for (int lengthCounter = 0; lengthCounter < length; lengthCounter++) {
			int red = (pixels[pixelOffset] >> 16 & 0xff) * a;
			int green = (pixels[pixelOffset] >> 8 & 0xff) * a;
			int blue = (pixels[pixelOffset] & 0xff) * a;
			int rgba = ((r + red >> 8) << 16) + ((g + green >> 8) << 8) + (b + blue >> 8);
			pixels[pixelOffset++] = rgba;
		}
	}

	/**
	 * Draws vertical line.
	 *
	 * @param x the X coordinate
	 * @param y the Y coordinate
	 * @param lenght the lenght
	 * @param colour the color value
	 */
	public static void drawVerticalLine(int x, int y, int lenght, int colour) {
		if (x < topX || x >= bottomX)
			return;
		if (y < topY) {
			lenght -= topY - y;
			y = topY;
		}
		if (y + lenght > bottomY)
			lenght = bottomY - y;
		int pixelOffset = x + y * width;
		for (int pixel = 0; pixel < lenght; pixel++)
			pixels[pixelOffset + pixel * width] = colour;

	}

	/**
	 * Draws vertical line alpha.
	 *
	 * @param x the X coordinate
	 * @param y the Y coordinate
	 * @param lenght the lenght
	 * @param colour the color value
	 * @param alpha the alpha
	 */
	public static void drawVerticalLineAlpha(int x, int y, int lenght, int colour, int alpha) {
		if (x < topX || x >= bottomX)
			return;
		if (y < topY) {
			lenght -= topY - y;
			y = topY;
		}
		if (y + lenght > bottomY)
			lenght = bottomY - y;
		int a = 256 - alpha;
		int r = (colour >> 16 & 0xff) * alpha;
		int g = (colour >> 8 & 0xff) * alpha;
		int b = (colour & 0xff) * alpha;
		int pixel = x + y * width;
		for (int lengthCounter = 0; lengthCounter < lenght; lengthCounter++) {
			int red = (pixels[pixel] >> 16 & 0xff) * a;
			int blue = (pixels[pixel] >> 8 & 0xff) * a;
			int green = (pixels[pixel] & 0xff) * a;
			int rgba = ((r + red >> 8) << 16) + ((g + blue >> 8) << 8) + (b + green >> 8);
			pixels[pixel] = rgba;
			pixel += width;
		}

	}
}
