package rs2.media;

import java.awt.Component;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.ColorModel;
import java.awt.image.DirectColorModel;
import java.awt.image.ImageConsumer;
import java.awt.image.ImageObserver;
import java.awt.image.ImageProducer;

/**
 * AWT image producer backed directly by the client's software-raster pixel
 * array.
 *
 * <p>
 * The revision-377 client uses these buffers for both the main game raster and
 * fixed-layout UI panels. Calling {@link #bindRaster()} makes this buffer the
 * global {@link Rasterizer} drawing target; {@link #draw(Graphics, int, int)}
 * publishes the current pixels to AWT before drawing the produced image.
 * </p>
 *
 * <p>
 * The producer deliberately retains the original single-consumer design and
 * reports each pixel push as {@link ImageConsumer#SINGLEFRAMEDONE}. The
 * constructor also performs the original three prepare/publish cycles used to
 * prime AWT's producer/consumer connection.
 * </p>
 */
public final class GraphicsBuffer implements ImageProducer, ImageObserver {

	/** Directly writable 32-bit RGB software-raster pixels. */
	public final int[] pixels;

	private final int width;
	private final int height;
	private final ColorModel colorModel;
	private ImageConsumer consumer;
	private final Image image;

	public GraphicsBuffer(Component component, int width, int height) {
		this.width = width;
		this.height = height;
		pixels = new int[width * height];
		colorModel = new DirectColorModel(32, 0xff0000, 0xff00, 0xff);
		image = component.createImage(this);

		pushPixels();
		component.prepareImage(image, this);
		pushPixels();
		component.prepareImage(image, this);
		pushPixels();
		component.prepareImage(image, this);

		bindRaster();
	}

	/** Makes this buffer the global software-raster drawing target. */
	public void bindRaster() {
		Rasterizer.createRasterizer(pixels, width, height);
	}

	/**
	 * Publishes the current pixels and draws the produced image at {@code (x, y)}.
	 */
	public void draw(Graphics graphics, int x, int y) {
		pushPixels();
		graphics.drawImage(image, x, y, this);
	}

	@Override
	public synchronized void addConsumer(ImageConsumer consumer) {
		this.consumer = consumer;
		consumer.setDimensions(width, height);
		consumer.setProperties(null);
		consumer.setColorModel(colorModel);
		consumer.setHints(ImageConsumer.TOPDOWNLEFTRIGHT | ImageConsumer.COMPLETESCANLINES | ImageConsumer.SINGLEPASS);
	}

	@Override
	public synchronized boolean isConsumer(ImageConsumer consumer) {
		return this.consumer == consumer;
	}

	@Override
	public synchronized void removeConsumer(ImageConsumer consumer) {
		if (this.consumer == consumer) {
			this.consumer = null;
		}
	}

	@Override
	public void startProduction(ImageConsumer consumer) {
		addConsumer(consumer);
	}

	@Override
	public void requestTopDownLeftRightResend(ImageConsumer consumer) {
		// ImageProducer's resend mechanism was never implemented by the original
		// client.
		System.out.println("TDLR");
	}

	/** Pushes the complete current raster to the registered AWT image consumer. */
	private synchronized void pushPixels() {
		if (consumer == null) {
			return;
		}
		consumer.setPixels(0, 0, width, height, colorModel, pixels, 0, width);
		consumer.imageComplete(ImageConsumer.SINGLEFRAMEDONE);
	}

	@Override
	public boolean imageUpdate(Image image, int flags, int x, int y, int width, int height) {
		return true;
	}
}
