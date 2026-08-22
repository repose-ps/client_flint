package rs2.media;

import rs2.cache.def.ItemDefinition;
import rs2.cache.media.ImageRGB;
import rs2.collection.LruCache;
import rs2.media.renderable.Model;

/**
 * Renders revision-377 32x32 item icons using the global software rasterizers.
 *
 * <p>
 * This code is intentionally stateful because the original client temporarily
 * replaces the global 2D raster and 3D projection bounds while drawing each
 * icon. The exact outline, shadow, note-overlay, cache-key, and sprite metadata
 * quirks are preserved.
 * </p>
 */
public final class ItemSpriteFactory {

	private static LruCache spriteCache = new LruCache(100);

	private ItemSpriteFactory() {
	}

	/**
	 * Returns a 32x32 item sprite.
	 *
	 * @param itemId       item definition id
	 * @param quantity     stack amount used for quantity variants
	 * @param outlineColor {@code 0} for the normal diagonal shadow, {@code >0} for
	 *                     a colored outline, and {@code -1} for the enlarged
	 *                     note-overlay path
	 */
	public static ImageRGB getSprite(int itemId, int quantity, int outlineColor) {
		if (outlineColor == 0) {
			ImageRGB cached = (ImageRGB) spriteCache.get(itemId);
			if (cached != null && cached.maxHeight != quantity && cached.maxHeight != -1) {
				cached.unlink();
				cached = null;
			}
			if (cached != null) {
				return cached;
			}
		}

		ItemDefinition definition = ItemDefinition.lookup(itemId);
		if (definition.stackVariantIds == null) {
			quantity = -1;
		}
		if (quantity > 1) {
			int variantId = -1;
			for (int index = 0; index < 10; index++) {
				if (quantity >= definition.stackVariantAmounts[index] && definition.stackVariantAmounts[index] != 0) {
					variantId = definition.stackVariantIds[index];
				}
			}
			if (variantId != -1) {
				definition = ItemDefinition.lookup(variantId);
			}
		}

		Model model = definition.getModel(1);
		if (model == null) {
			return null;
		}

		ImageRGB noteOverlay = null;
		if (definition.noteTemplateId != -1) {
			noteOverlay = getSprite(definition.noteId, 10, -1);
			if (noteOverlay == null) {
				return null;
			}
		}

		ImageRGB sprite = new ImageRGB(32, 32);
		int oldCenterX = Rasterizer3D.centerX;
		int oldCenterY = Rasterizer3D.centerY;
		int[] oldScanlineOffsets = Rasterizer3D.scanlineOffsets;
		int[] oldPixels = Rasterizer.pixels;
		int oldWidth = Rasterizer.width;
		int oldHeight = Rasterizer.height;
		int oldTopX = Rasterizer.topX;
		int oldBottomX = Rasterizer.bottomX;
		int oldTopY = Rasterizer.topY;
		int oldBottomY = Rasterizer.bottomY;

		Rasterizer3D.gouraudBlockShading = false;
		Rasterizer.createRasterizer(sprite.pixels, 32, 32);
		Rasterizer.drawFilledRectangle(0, 0, 32, 32, 0);
		Rasterizer3D.setDefaultBounds();

		int zoom = definition.zoom2d;
		if (outlineColor == -1) {
			zoom = (int) (zoom * 1.5D);
		}
		if (outlineColor > 0) {
			zoom = (int) (zoom * 1.04D);
		}

		int sineOffset = Rasterizer3D.SINE[definition.xan2d] * zoom >> 16;
		int cosineOffset = Rasterizer3D.COSINE[definition.xan2d] * zoom >> 16;
		model.renderSimple(0, definition.yan2d, definition.zan2d, definition.xan2d, definition.offsetX2d,
				sineOffset + model.modelHeight / 2 + definition.offsetY2d, cosineOffset + definition.offsetY2d);

		addInnerOutline(sprite.pixels);
		if (outlineColor > 0) {
			addColoredOutline(sprite.pixels, outlineColor);
		} else if (outlineColor == 0) {
			addDiagonalShadow(sprite.pixels);
		}

		if (noteOverlay != null) {
			int oldMaxWidth = noteOverlay.maxWidth;
			int oldMaxHeight = noteOverlay.maxHeight;
			noteOverlay.maxWidth = 32;
			noteOverlay.maxHeight = 32;
			noteOverlay.drawImage(0, 0);
			noteOverlay.maxWidth = oldMaxWidth;
			noteOverlay.maxHeight = oldMaxHeight;
		}

		if (outlineColor == 0) {
			spriteCache.put(itemId, sprite);
		}

		Rasterizer.createRasterizer(oldPixels, oldWidth, oldHeight);
		Rasterizer.setCoordinates(oldTopX, oldTopY, oldBottomX, oldBottomY);
		Rasterizer3D.centerX = oldCenterX;
		Rasterizer3D.centerY = oldCenterY;
		Rasterizer3D.scanlineOffsets = oldScanlineOffsets;
		Rasterizer3D.gouraudBlockShading = true;

		sprite.maxWidth = definition.stackable ? 33 : 32;
		sprite.maxHeight = quantity;
		return sprite;
	}

	/** Clears only the rendered item-sprite LRU. */
	public static void clearCache() {
		spriteCache.clear();
	}

	/**
	 * Used by the full item-definition teardown to preserve the original nulling
	 * behavior.
	 */
	public static void clear() {
		spriteCache = null;
	}

	private static void addInnerOutline(int[] pixels) {
		for (int x = 31; x >= 0; x--) {
			for (int y = 31; y >= 0; y--) {
				int index = x + y * 32;
				if (pixels[index] != 0) {
					continue;
				}
				if (x > 0 && pixels[index - 1] > 1) {
					pixels[index] = 1;
				} else if (y > 0 && pixels[index - 32] > 1) {
					pixels[index] = 1;
				} else if (x < 31 && pixels[index + 1] > 1) {
					pixels[index] = 1;
				} else if (y < 31 && pixels[index + 32] > 1) {
					pixels[index] = 1;
				}
			}
		}
	}

	private static void addColoredOutline(int[] pixels, int color) {
		for (int x = 31; x >= 0; x--) {
			for (int y = 31; y >= 0; y--) {
				int index = x + y * 32;
				if (pixels[index] != 0) {
					continue;
				}
				if (x > 0 && pixels[index - 1] == 1) {
					pixels[index] = color;
				} else if (y > 0 && pixels[index - 32] == 1) {
					pixels[index] = color;
				} else if (x < 31 && pixels[index + 1] == 1) {
					pixels[index] = color;
				} else if (y < 31 && pixels[index + 32] == 1) {
					pixels[index] = color;
				}
			}
		}
	}

	private static void addDiagonalShadow(int[] pixels) {
		for (int x = 31; x >= 0; x--) {
			for (int y = 31; y >= 0; y--) {
				int index = x + y * 32;
				if (pixels[index] == 0 && x > 0 && y > 0 && pixels[index - 33] > 0) {
					pixels[index] = 0x302020;
				}
			}
		}
	}

}
