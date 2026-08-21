package rs2.cache.media;

import rs2.cache.Archive;
import rs2.media.Rasterizer;
import rs2.net.Buffer;

/** Palette-indexed software sprite loaded from the media archive. */
public class IndexedImage extends Rasterizer {

    public byte[] pixels;
    public int[] palette;
    public int width;
    public int height;
    public int offsetX;
    public int offsetY;
    public int maxWidth;
    public int maxHeight;

    /** Creates an empty indexed sprite for client-side media generation. */
    public IndexedImage(int width, int height, int[] palette) {
        if (width < 0 || height < 0) {
            throw new IllegalArgumentException("Sprite dimensions must be non-negative");
        }
        this.width = maxWidth = width;
        this.height = maxHeight = height;
        this.palette = palette.clone();
        pixels = new byte[width * height];
    }

    public IndexedImage(Archive archive, String archiveName, int offset) {
        Buffer dataBuffer = new Buffer(archive.read(archiveName + ".dat"));
        Buffer indexBuffer = new Buffer(archive.read("index.dat"));
        indexBuffer.position = dataBuffer.readUnsignedShort();
        maxWidth = indexBuffer.readUnsignedShort();
        maxHeight = indexBuffer.readUnsignedShort();
        int palleteLength = indexBuffer.readUnsignedByte();
        palette = new int[palleteLength];
        for (int index = 0; index < palleteLength - 1; index++)
            palette[index + 1] = indexBuffer.readMedium();

        for (int counter = 0; counter < offset; counter++) {
            indexBuffer.position += 2;
            dataBuffer.position += indexBuffer.readUnsignedShort() * indexBuffer.readUnsignedShort();
            indexBuffer.position++;
        }

        offsetX = indexBuffer.readUnsignedByte();
        offsetY = indexBuffer.readUnsignedByte();
        width = indexBuffer.readUnsignedShort();
        height = indexBuffer.readUnsignedShort();
        int type = indexBuffer.readUnsignedByte();
        int pixelLength = width * height;
        pixels = new byte[pixelLength];
        if (type == 0) {
            for (int pixel = 0; pixel < pixelLength; pixel++)
                pixels[pixel] = dataBuffer.readSignedByte();

            return;
        }
        if (type == 1) {
            for (int x = 0; x < width; x++) {
                for (int y = 0; y < height; y++)
                    pixels[x + y * width] = dataBuffer.readSignedByte();

            }

        }
    }

    public void resizeToHalf() {
        maxWidth /= 2;
        maxHeight /= 2;
        byte[] resizedPixels = new byte[maxWidth * maxHeight];
        int pixelCount = 0;
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++)
                resizedPixels[(x + offsetX >> 1) + (y + offsetY >> 1) * maxWidth] = pixels[pixelCount++];

        }

        pixels = resizedPixels;
        width = maxWidth;
        height = maxHeight;
        offsetX = 0;
        offsetY = 0;
    }

    public void resizeToCanvas() {
        if (width != maxWidth || height != maxHeight) {
            byte[] resizedPixels = new byte[maxWidth * maxHeight];
            int pixelCount = 0;
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++)
                    resizedPixels[x + offsetX + (y + offsetY) * maxWidth] = pixels[pixelCount++];

            }

            pixels = resizedPixels;
            width = maxWidth;
            height = maxHeight;
            offsetX = 0;
            offsetY = 0;
        }

    }

    public void flipHorizontal() {
        byte[] flipedPixels = new byte[width * height];
        int pixelCount = 0;
        for (int y = 0; y < height; y++) {
            for (int x = width - 1; x >= 0; x--)
                flipedPixels[pixelCount++] = pixels[x + y * width];

        }

        pixels = flipedPixels;
        offsetX = maxWidth - width - offsetX;

    }

    public void flipVertical() {
        byte[] flipedPixels = new byte[width * height];
        int pixelCount = 0;
        for (int y = height - 1; y >= 0; y--) {
            for (int x = 0; x < width; x++)
                flipedPixels[pixelCount++] = pixels[x + y * width];

        }
        pixels = flipedPixels;
        offsetY = maxHeight - height - offsetY;
    }

    public void adjustPalette(int red, int green, int blue) {
        for (int index = 0; index < palette.length; index++) {
            int r = palette[index] >> 16 & 0xff;
            r += red;
            if (r < 0)
                r = 0;
            else if (r > 255)
                r = 255;
            int g = palette[index] >> 8 & 0xff;
            g += green;
            if (g < 0)
                g = 0;
            else if (g > 255)
                g = 255;
            int b = palette[index] & 0xff;
            b += blue;
            if (b < 0)
                b = 0;
            else if (b > 255)
                b = 255;
            palette[index] = (r << 16) + (g << 8) + b;
        }
    }

    public void draw(int x, int y) {
        x += offsetX;
        y += offsetY;
        int offset = x + y * Rasterizer.width;
        int originalOffset = 0;
        int imageHeight = height;
        int imageWidth = width;
        int deviation = Rasterizer.width - imageWidth;
        int originalDeviation = 0;
        if (y < Rasterizer.topY) {
            int yOffset = Rasterizer.topY - y;
            imageHeight -= yOffset;
            y = Rasterizer.topY;
            originalOffset += yOffset * imageWidth;
            offset += yOffset * Rasterizer.width;
        }
        if (y + imageHeight > Rasterizer.bottomY)
            imageHeight -= (y + imageHeight) - Rasterizer.bottomY;
        if (x < Rasterizer.topX) {
            int xOffset = Rasterizer.topX - x;
            imageWidth -= xOffset;
            x = Rasterizer.topX;
            originalOffset += xOffset;
            offset += xOffset;
            originalDeviation += xOffset;
            deviation += xOffset;
        }
        if (x + imageWidth > Rasterizer.bottomX) {
            int xOffset = (x + imageWidth) - Rasterizer.bottomX;
            imageWidth -= xOffset;
            originalDeviation += xOffset;
            deviation += xOffset;
        }
        if (imageWidth > 0 && imageHeight > 0) {
            copyPixels(pixels, Rasterizer.pixels, imageWidth, imageHeight, offset, originalOffset, deviation, originalDeviation, palette);
        }
    }

    public void copyPixels(byte[] pixels, int[] rasterizerPixels, int width, int height, int offset, int originalOffset, int deviation, int originalDeviation, int[] pallete) {
        int shiftedWidth = -(width >> 2);
        width = -(width & 3);
        for (int heightCounter = -height; heightCounter < 0; heightCounter++) {
            for (int shiftedWidthCounter = shiftedWidth; shiftedWidthCounter < 0; shiftedWidthCounter++) {
                byte pixel = pixels[originalOffset++];
                if (pixel != 0)
                    rasterizerPixels[offset++] = pallete[pixel & 0xff];
                else
                    offset++;
                pixel = pixels[originalOffset++];
                if (pixel != 0)
                    rasterizerPixels[offset++] = pallete[pixel & 0xff];
                else
                    offset++;
                pixel = pixels[originalOffset++];
                if (pixel != 0)
                    rasterizerPixels[offset++] = pallete[pixel & 0xff];
                else
                    offset++;
                pixel = pixels[originalOffset++];
                if (pixel != 0)
                    rasterizerPixels[offset++] = pallete[pixel & 0xff];
                else
                    offset++;
            }

            for (int widthCounter = width; widthCounter < 0; widthCounter++) {
                byte pixel = pixels[originalOffset++];
                if (pixel != 0)
                    rasterizerPixels[offset++] = pallete[pixel & 0xff];
                else
                    offset++;
            }

            offset += deviation;
            originalOffset += originalDeviation;
        }

    }

}