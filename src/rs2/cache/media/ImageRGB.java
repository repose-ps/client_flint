package rs2.cache.media;

import java.awt.Component;
import java.awt.Image;
import java.awt.MediaTracker;
import java.awt.Toolkit;
import java.awt.image.PixelGrabber;

import rs2.cache.Archive;
import rs2.media.Rasterizer;
import rs2.net.Buffer;

/** Full-colour software sprite backed by 24-bit RGB pixels. */
public class ImageRGB extends Rasterizer {

	/** Stores the pixels values. */
	public int[] pixels;
	/** Stores the width. */
	public int width;
	/** Stores the height. */
	public int height;
	/** Stores the offset x. */
	public int offsetX;
	/** Stores the offset y. */
	public int offsetY;
	/** Stores the max width. */
	public int maxWidth;
	/** Stores the max height. */
	public int maxHeight;

	/**
	 * Creates a new ImageRGB instance.
	 *
	 * @param width  the width
	 * @param height the height
	 */
	public ImageRGB(int width, int height) {
		pixels = new int[width * height];
		this.width = maxWidth = width;
		this.height = maxHeight = height;
		offsetX = offsetY = 0;
	}

	/**
	 * Creates a new ImageRGB instance.
	 *
	 * @param imagedata the imagedata
	 * @param component the component
	 */
	public ImageRGB(byte[] imagedata, Component component) {
		try {
			Image image = Toolkit.getDefaultToolkit().createImage(imagedata);
			MediaTracker mediatracker = new MediaTracker(component);
			mediatracker.addImage(image, 0);
			mediatracker.waitForAll();
			width = image.getWidth(component);
			height = image.getHeight(component);
			maxWidth = width;
			maxHeight = height;
			offsetX = 0;
			offsetY = 0;
			pixels = new int[width * height];
			PixelGrabber pixelgrabber = new PixelGrabber(image, 0, 0, width, height, pixels, 0, width);
			pixelgrabber.grabPixels();
			return;
		} catch (Exception _ex) {
			System.out.println("Error converting jpg");
		}
	}

	/**
	 * Creates a new ImageRGB instance.
	 *
	 * @param archive      the archive
	 * @param archiveName  the archive name
	 * @param archiveIndex the archive index
	 */
	public ImageRGB(Archive archive, String archiveName, int archiveIndex) {
		Buffer dataBuffer = new Buffer(archive.read(archiveName + ".dat"));
		Buffer indexBuffer = new Buffer(archive.read("index.dat"));
		indexBuffer.position = dataBuffer.readUnsignedShort();
		maxWidth = indexBuffer.readUnsignedShort();
		maxHeight = indexBuffer.readUnsignedShort();
		int length = indexBuffer.readUnsignedByte();
		int[] pixels = new int[length];
		for (int pixel = 0; pixel < length - 1; pixel++) {
			pixels[pixel + 1] = indexBuffer.readMedium();
			if (pixels[pixel + 1] == 0)
				pixels[pixel + 1] = 1;
		}

		for (int index = 0; index < archiveIndex; index++) {
			indexBuffer.position += 2;
			dataBuffer.position += indexBuffer.readUnsignedShort() * indexBuffer.readUnsignedShort();
			indexBuffer.position++;
		}

		offsetX = indexBuffer.readUnsignedByte();
		offsetY = indexBuffer.readUnsignedByte();
		width = indexBuffer.readUnsignedShort();
		height = indexBuffer.readUnsignedShort();
		int type = indexBuffer.readUnsignedByte();
		int pixelCount = width * height;
		this.pixels = new int[pixelCount];
		if (type == 0) {
			for (int pixel = 0; pixel < pixelCount; pixel++)
				this.pixels[pixel] = pixels[dataBuffer.readUnsignedByte()];

			return;
		}
		if (type == 1) {
			for (int x = 0; x < width; x++) {
				for (int y = 0; y < height; y++)
					this.pixels[x + y * width] = pixels[dataBuffer.readUnsignedByte()];

			}

		}
	}

	/**
	 * Creates rasterizer.
	 */
	public void createRasterizer() {
		Rasterizer.createRasterizer(pixels, width, height);
	}

	/**
	 * Performs the adjust rgb operation.
	 *
	 * @param redOffset   the red offset
	 * @param greenOffset the green offset
	 * @param blueOffset  the blue offset
	 */
	public void adjustRgb(int redOffset, int greenOffset, int blueOffset) {
		for (int pixel = 0; pixel < pixels.length; pixel++) {
			int originalColor = pixels[pixel];
			if (originalColor != 0) {
				int red = originalColor >> 16 & 0xff;
				red += redOffset;
				if (red < 1)
					red = 1;
				else if (red > 255)
					red = 255;
				int green = originalColor >> 8 & 0xff;
				green += greenOffset;
				if (green < 1)
					green = 1;
				else if (green > 255)
					green = 255;
				int blue = originalColor & 0xff;
				blue += blueOffset;
				if (blue < 1)
					blue = 1;
				else if (blue > 255)
					blue = 255;
				pixels[pixel] = (red << 16) + (green << 8) + blue;
			}
		}

	}

	/**
	 * Performs the trim operation.
	 */
	public void trim() {
		int[] newPixels = new int[maxWidth * maxHeight];
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++)
				newPixels[(y + offsetY) * maxWidth + (x + offsetX)] = pixels[y * width + x];

		}

		pixels = newPixels;
		width = maxWidth;
		height = maxHeight;
		offsetX = 0;
		offsetY = 0;
	}

	/**
	 * Draws inverse.
	 *
	 * @param x the x
	 * @param y the y
	 */
	public void drawInverse(int x, int y) {
		x += offsetX;
		y += offsetY;
		int rasterizerPixel = x + y * Rasterizer.width;
		int pixel = 0;
		int newHeight = height;
		int newWidth = width;
		int rasterizerPixelOffset = Rasterizer.width - newWidth;
		int pixelOffset = 0;
		if (y < Rasterizer.topY) {
			int yOffset = Rasterizer.topY - y;
			newHeight -= yOffset;
			y = Rasterizer.topY;
			pixel += yOffset * newWidth;
			rasterizerPixel += yOffset * Rasterizer.width;
		}
		if (y + newHeight > Rasterizer.bottomY)
			newHeight -= (y + newHeight) - Rasterizer.bottomY;
		if (x < Rasterizer.topX) {
			int xOffset = Rasterizer.topX - x;
			newWidth -= xOffset;
			x = Rasterizer.topX;
			pixel += xOffset;
			rasterizerPixel += xOffset;
			pixelOffset += xOffset;
			rasterizerPixelOffset += xOffset;
		}
		if (x + newWidth > Rasterizer.bottomX) {
			int widthOffset = (x + newWidth) - Rasterizer.bottomX;
			newWidth -= widthOffset;
			pixelOffset += widthOffset;
			rasterizerPixelOffset += widthOffset;
		}
		if (newWidth <= 0 || newHeight <= 0)
			return;
		copyPixels(pixels, Rasterizer.pixels, pixel, rasterizerPixel, pixelOffset, rasterizerPixelOffset, newWidth,
				newHeight);
	}

	/**
	 * Copies pixels.
	 *
	 * @param pixels                the pixels
	 * @param rasterizerPixels      the rasterizer pixels
	 * @param pixel                 the pixel
	 * @param rasterizerPixel       the rasterizer pixel
	 * @param pixelOffset           the pixel offset
	 * @param rasterizerPixelOffset the rasterizer pixel offset
	 * @param width                 the width
	 * @param height                the height
	 */
	public void copyPixels(int[] pixels, int[] rasterizerPixels, int pixel, int rasterizerPixel, int pixelOffset,
			int rasterizerPixelOffset, int width, int height) {
		int shiftedWidth = -(width >> 2);
		width = -(width & 3);
		for (int heightCounter = -height; heightCounter < 0; heightCounter++) {
			for (int widthCounter = shiftedWidth; widthCounter < 0; widthCounter++) {
				rasterizerPixels[rasterizerPixel++] = pixels[pixel++];
				rasterizerPixels[rasterizerPixel++] = pixels[pixel++];
				rasterizerPixels[rasterizerPixel++] = pixels[pixel++];
				rasterizerPixels[rasterizerPixel++] = pixels[pixel++];
			}

			for (int widthCounter = width; widthCounter < 0; widthCounter++)
				rasterizerPixels[rasterizerPixel++] = pixels[pixel++];

			rasterizerPixel += rasterizerPixelOffset;
			pixel += pixelOffset;
		}

	}

	/**
	 * Draws image.
	 *
	 * @param x the x
	 * @param y the y
	 */
	public void drawImage(int x, int y) {
		x += offsetX;
		y += offsetY;
		int rasterizerOffset = x + y * Rasterizer.width;
		int pixelOffset = 0;
		int imageHeight = height;
		int imageWidth = width;
		int deviation = Rasterizer.width - imageWidth;
		int originalDeviation = 0;
		if (y < Rasterizer.topY) {
			int yOffset = Rasterizer.topY - y;
			imageHeight -= yOffset;
			y = Rasterizer.topY;
			pixelOffset += yOffset * imageWidth;
			rasterizerOffset += yOffset * Rasterizer.width;
		}
		if (y + imageHeight > Rasterizer.bottomY)
			imageHeight -= (y + imageHeight) - Rasterizer.bottomY;
		if (x < Rasterizer.topX) {
			int xOffset = Rasterizer.topX - x;
			imageWidth -= xOffset;
			x = Rasterizer.topX;
			pixelOffset += xOffset;
			rasterizerOffset += xOffset;
			originalDeviation += xOffset;
			deviation += xOffset;
		}
		if (x + imageWidth > Rasterizer.bottomX) {
			int xOffset = (x + imageWidth) - Rasterizer.bottomX;
			imageWidth -= xOffset;
			originalDeviation += xOffset;
			deviation += xOffset;
		}
		if (imageWidth <= 0 || imageHeight <= 0) {
			return;
		} else {
			shapeImageToPixels(pixels, Rasterizer.pixels, pixelOffset, rasterizerOffset, imageWidth, imageHeight,
					originalDeviation, deviation, 0);
			return;
		}
	}

	/**
	 * Performs the shape image to pixels operation.
	 *
	 * @param pixels                the pixels
	 * @param rasterizerPixels      the rasterizer pixels
	 * @param pixel                 the pixel
	 * @param rasterizerPixel       the rasterizer pixel
	 * @param width                 the width
	 * @param height                the height
	 * @param pixelOffset           the pixel offset
	 * @param rasterizerPixelOffset the rasterizer pixel offset
	 * @param pixelColor            the pixel color
	 */
	public void shapeImageToPixels(int[] pixels, int[] rasterizerPixels, int pixel, int rasterizerPixel, int width,
			int height, int pixelOffset, int rasterizerPixelOffset, int pixelColor) {
		int shiftedWidth = -(width >> 2);
		width = -(width & 3);
		for (int heightCounter = -height; heightCounter < 0; heightCounter++) {
			for (int widthCounter = shiftedWidth; widthCounter < 0; widthCounter++) {
				pixelColor = pixels[pixel++];
				if (pixelColor != 0)
					rasterizerPixels[rasterizerPixel++] = pixelColor;
				else
					rasterizerPixel++;
				pixelColor = pixels[pixel++];
				if (pixelColor != 0)
					rasterizerPixels[rasterizerPixel++] = pixelColor;
				else
					rasterizerPixel++;
				pixelColor = pixels[pixel++];
				if (pixelColor != 0)
					rasterizerPixels[rasterizerPixel++] = pixelColor;
				else
					rasterizerPixel++;
				pixelColor = pixels[pixel++];
				if (pixelColor != 0)
					rasterizerPixels[rasterizerPixel++] = pixelColor;
				else
					rasterizerPixel++;
			}

			for (int widthCounter = width; widthCounter < 0; widthCounter++) {
				pixelColor = pixels[pixel++];
				if (pixelColor != 0)
					rasterizerPixels[rasterizerPixel++] = pixelColor;
				else
					rasterizerPixel++;
			}

			rasterizerPixel += rasterizerPixelOffset;
			pixel += pixelOffset;
		}

	}

	/**
	 * Draws image alpha.
	 *
	 * @param x     the x
	 * @param y     the y
	 * @param alpha the alpha
	 */
	public void drawImageAlpha(int x, int y, int alpha) {
		x += offsetX;
		y += offsetY;
		int rasterizerPixel = x + y * Rasterizer.width;
		int pixel = 0;
		int newHeight = height;
		int newWidth = width;
		int rasterizerPixelOffset = Rasterizer.width - newWidth;
		int pixelOffset = 0;
		if (y < Rasterizer.topY) {
			int yOffset = Rasterizer.topY - y;
			newHeight -= yOffset;
			y = Rasterizer.topY;
			pixel += yOffset * newWidth;
			rasterizerPixel += yOffset * Rasterizer.width;
		}
		if (y + newHeight > Rasterizer.bottomY)
			newHeight -= (y + newHeight) - Rasterizer.bottomY;
		if (x < Rasterizer.topX) {
			int xOffset = Rasterizer.topX - x;
			newWidth -= xOffset;
			x = Rasterizer.topX;
			pixel += xOffset;
			rasterizerPixel += xOffset;
			pixelOffset += xOffset;
			rasterizerPixelOffset += xOffset;
		}
		if (x + newWidth > Rasterizer.bottomX) {
			int xOffset = (x + newWidth) - Rasterizer.bottomX;
			newWidth -= xOffset;
			pixelOffset += xOffset;
			rasterizerPixelOffset += xOffset;
		}
		if (newWidth > 0 && newHeight > 0) {
			copyPixelsAlpha(pixels, Rasterizer.pixels, pixel, rasterizerPixel, pixelOffset, rasterizerPixelOffset,
					newWidth, newHeight, 0, alpha);
		}
	}

	/**
	 * Copies pixels alpha.
	 *
	 * @param pixels                the pixels
	 * @param rasterizerPixels      the rasterizer pixels
	 * @param pixel                 the pixel
	 * @param rasterizerPixel       the rasterizer pixel
	 * @param pixelOffset           the pixel offset
	 * @param rasterizerPixelOffset the rasterizer pixel offset
	 * @param width                 the width
	 * @param height                the height
	 * @param color                 the color
	 * @param alpha                 the alpha
	 */
	public void copyPixelsAlpha(int[] pixels, int[] rasterizerPixels, int pixel, int rasterizerPixel, int pixelOffset,
			int rasterizerPixelOffset, int width, int height, int color, int alpha) {
		int alphaValue = 256 - alpha;
		for (int heightCounter = -height; heightCounter < 0; heightCounter++) {
			for (int widthCounter = -width; widthCounter < 0; widthCounter++) {
				color = pixels[pixel++];
				if (color != 0) {
					int rasterizerPixelColor = rasterizerPixels[rasterizerPixel];
					rasterizerPixels[rasterizerPixel++] = ((color & 0xff00ff) * alpha
							+ (rasterizerPixelColor & 0xff00ff) * alphaValue & 0xff00ff00)
							+ ((color & 0xff00) * alpha + (rasterizerPixelColor & 0xff00) * alphaValue & 0xff0000) >> 8;
				} else {
					rasterizerPixel++;
				}
			}

			rasterizerPixel += rasterizerPixelOffset;
			pixel += pixelOffset;
		}

	}

	/**
	 * Performs the shape image to pixels operation.
	 *
	 * @param x          the x
	 * @param y          the y
	 * @param width      the width
	 * @param height     the height
	 * @param zoom       the zoom
	 * @param pivotX     the pivot x
	 * @param rowWidths  the row widths
	 * @param angle      the angle
	 * @param rowOffsets the row offsets
	 * @param pivotY     the pivot y
	 */
	public void shapeImageToPixels(int x, int y, int width, int height, int zoom, int pivotX, int[] rowWidths,
			int angle, int[] rowOffsets, int pivotY) {
		try {
			int centerX = -width / 2;
			int centerY = -height / 2;
			int sine = (int) (Math.sin(angle / 326.11000000000001D) * 65536D);
			int cosine = (int) (Math.cos(angle / 326.11000000000001D) * 65536D);
			sine = sine * zoom >> 8;
			cosine = cosine * zoom >> 8;
			int sourceOffsetX = (pivotX << 16) + (centerY * sine + centerX * cosine);
			int sourceOffsetY = (pivotY << 16) + (centerY * cosine - centerX * sine);
			int destinationOffset = x + y * Rasterizer.width;
			for (y = 0; y < height; y++) {
				int rowOffset = rowOffsets[y];
				int destinationPixel = destinationOffset + rowOffset;
				int sourceX = sourceOffsetX + cosine * rowOffset;
				int sourceY = sourceOffsetY - sine * rowOffset;
				for (x = -rowWidths[y]; x < 0; x++) {
					Rasterizer.pixels[destinationPixel++] = pixels[(sourceX >> 16) + (sourceY >> 16) * this.width];
					sourceX += cosine;
					sourceY -= sine;
				}

				sourceOffsetX += sine;
				sourceOffsetY += cosine;
				destinationOffset += Rasterizer.width;
			}

		} catch (Exception _ex) {
		}
	}

	/**
	 * Draws rotated.
	 *
	 * @param x      the x
	 * @param y      the y
	 * @param pivotX the pivot x
	 * @param pivotY the pivot y
	 * @param width  the width
	 * @param height the height
	 * @param zoom   the zoom
	 * @param angle  the angle
	 */
	public void drawRotated(int x, int y, int pivotX, int pivotY, int width, int height, int zoom, double angle) {
		try {
			int centerX = -width / 2;
			int centerY = -height / 2;
			int sine = (int) (Math.sin(angle) * 65536D);
			int cosine = (int) (Math.cos(angle) * 65536D);
			sine = sine * zoom >> 8;
			cosine = cosine * zoom >> 8;
			int sourceOffsetX = (pivotX << 16) + (centerY * sine + centerX * cosine);
			int sourceOffsetY = (pivotY << 16) + (centerY * cosine - centerX * sine);
			int destinationOffset = x + y * Rasterizer.width;
			for (y = 0; y < height; y++) {
				int destinationPixel = destinationOffset;
				int offsetX = sourceOffsetX;
				int offsetY = sourceOffsetY;
				for (x = -width; x < 0; x++) {
					int colour = pixels[(offsetX >> 16) + (offsetY >> 16) * this.width];
					if (colour != 0)
						Rasterizer.pixels[destinationPixel++] = colour;
					else
						destinationPixel++;
					offsetX += cosine;
					offsetY -= sine;
				}

				sourceOffsetX += sine;
				sourceOffsetY += cosine;
				destinationOffset += Rasterizer.width;
			}

		} catch (Exception _ex) {
		}
	}

	/**
	 * Draws to.
	 *
	 * @param indexedImage the indexed image
	 * @param x            the x
	 * @param y            the y
	 */
	public void drawTo(IndexedImage indexedImage, int x, int y) {
		x += offsetX;
		y += offsetY;
		int destinationPixel = x + y * Rasterizer.width;
		int sourcePixel = 0;
		int drawHeight = height;
		int drawWidth = width;
		int destinationRowSkip = Rasterizer.width - drawWidth;
		int sourceRowSkip = 0;
		if (y < Rasterizer.topY) {
			int clippedTop = Rasterizer.topY - y;
			drawHeight -= clippedTop;
			y = Rasterizer.topY;
			sourcePixel += clippedTop * drawWidth;
			destinationPixel += clippedTop * Rasterizer.width;
		}
		if (y + drawHeight > Rasterizer.bottomY)
			drawHeight -= (y + drawHeight) - Rasterizer.bottomY;
		if (x < Rasterizer.topX) {
			int clippedLeft = Rasterizer.topX - x;
			drawWidth -= clippedLeft;
			x = Rasterizer.topX;
			sourcePixel += clippedLeft;
			destinationPixel += clippedLeft;
			sourceRowSkip += clippedLeft;
			destinationRowSkip += clippedLeft;
		}
		if (x + drawWidth > Rasterizer.bottomX) {
			int clippedRight = (x + drawWidth) - Rasterizer.bottomX;
			drawWidth -= clippedRight;
			sourceRowSkip += clippedRight;
			destinationRowSkip += clippedRight;
		}
		if (drawWidth <= 0 || drawHeight <= 0) {
			return;
		} else {
			copyPixelsMasked(destinationPixel, destinationRowSkip, pixels, drawWidth, Rasterizer.pixels,
					indexedImage.pixels, drawHeight, sourcePixel, sourceRowSkip);
			return;
		}
	}

	/**
	 * Copies pixels masked.
	 *
	 * @param destinationPixel   the destination pixel
	 * @param destinationRowSkip the destination row skip
	 * @param sourcePixels       the source pixels
	 * @param drawWidth          the draw width
	 * @param destinationPixels  the destination pixels
	 * @param maskPixels         the mask pixels
	 * @param drawHeight         the draw height
	 * @param sourcePixel        the source pixel
	 * @param sourceRowSkip      the source row skip
	 */
	private static void copyPixelsMasked(int destinationPixel, int destinationRowSkip, int sourcePixels[],
			int drawWidth, int destinationPixels[], byte maskPixels[], int drawHeight, int sourcePixel,
			int sourceRowSkip) {
		int color;
		int fourPixelGroups = -(drawWidth >> 2);
		drawWidth = -(drawWidth & 3);
		for (int rowCounter = -drawHeight; rowCounter < 0; rowCounter++) {
			for (int groupCounter = fourPixelGroups; groupCounter < 0; groupCounter++) {
				color = sourcePixels[sourcePixel++];
				if (color != 0 && maskPixels[destinationPixel] == 0)
					destinationPixels[destinationPixel++] = color;
				else
					destinationPixel++;
				color = sourcePixels[sourcePixel++];
				if (color != 0 && maskPixels[destinationPixel] == 0)
					destinationPixels[destinationPixel++] = color;
				else
					destinationPixel++;
				color = sourcePixels[sourcePixel++];
				if (color != 0 && maskPixels[destinationPixel] == 0)
					destinationPixels[destinationPixel++] = color;
				else
					destinationPixel++;
				color = sourcePixels[sourcePixel++];
				if (color != 0 && maskPixels[destinationPixel] == 0)
					destinationPixels[destinationPixel++] = color;
				else
					destinationPixel++;
			}

			for (int remainderCounter = drawWidth; remainderCounter < 0; remainderCounter++) {
				color = sourcePixels[sourcePixel++];
				if (color != 0 && maskPixels[destinationPixel] == 0)
					destinationPixels[destinationPixel++] = color;
				else
					destinationPixel++;
			}

			destinationPixel += destinationRowSkip;
			sourcePixel += sourceRowSkip;
		}

	}

}