package rs2.cache.ui;

import rs2.cache.Archive;
import rs2.cache.ResourceNameHash;
import rs2.cache.def.ItemDefinition;
import rs2.cache.def.NpcDefinition;
import rs2.cache.media.ImageRGB;
import rs2.collection.LruCache;
import rs2.media.AnimationFrame;
import rs2.media.TypeFace;
import rs2.media.renderable.Model;
import rs2.net.Buffer;
import rs2.Client;

/**
 * A revision-377 interface widget definition and its small amount of mutable UI
 * state.
 *
 * <p>
 * The interface archive is a segmented stream named {@code data}. A record
 * begins with a widget id. The marker {@code 65535} changes the current parent
 * id and is followed by the actual widget id. Each decoded record is also saved
 * with a two-byte parent-id prefix so widgets can be lazily reconstructed
 * without retaining the interface archive. The temporary objects decoded during
 * bulk loading are not inserted into {@link #widgets}; population is lazy.
 * </p>
 *
 * <p>
 * Widget type values used by revision 377 are: 0 container, 1 unknown, 2
 * inventory grid, 3 rectangle, 4 text, 5 sprite, 6 model, 7 inventory-text
 * grid, and 8 tooltip text.
 * </p>
 */
public class Widget {
	/**
	 * Creates a new widget.
	 */
	public Widget() {
	}

	/** Constant value for type container. */
	public static final int TYPE_CONTAINER = 0;
	/** Constant value for type unknown. */
	public static final int TYPE_UNKNOWN = 1;
	/** Constant value for type inventory. */
	public static final int TYPE_INVENTORY = 2;
	/** Constant value for type rectangle. */
	public static final int TYPE_RECTANGLE = 3;
	/** Constant value for type text. */
	public static final int TYPE_TEXT = 4;
	/** Constant value for type sprite. */
	public static final int TYPE_SPRITE = 5;
	/** Constant value for type model. */
	public static final int TYPE_MODEL = 6;
	/** Constant value for type inventory text. */
	public static final int TYPE_INVENTORY_TEXT = 7;
	/** Constant value for type tooltip. */
	public static final int TYPE_TOOLTIP = 8;

	/** Constant value for sprite cache capacity. */
	private static final int SPRITE_CACHE_CAPACITY = 50_000;
	/** Constant value for model cache capacity. */
	private static final int MODEL_CACHE_CAPACITY = 30;

	/** Stores the current spell name. */
	public String spellName;
	/** Stores the current sprite. */
	public ImageRGB sprite;
	/** Stores sprite Y offsets values. */
	public int[] spriteYOffsets;
	/** Stores the current sprite archive. */
	private static Archive spriteArchive;
	/** Stores the current ID. */
	public int id;
	/** Stores widgets values. */
	public static Widget[] widgets;
	/** Whether inventory replace items is enabled or active. */
	public boolean inventoryReplaceItems;
	/** Packed signed model pitch/yaw speeds: high 16 bits / low 16 bits. */
	public int modelRotationSpeed;
	/** Whether mouseover triggered is enabled or active. */
	public boolean mouseoverTriggered;
	/** Stores the current transparency. */
	public byte transparency;
	/** Stores sprite X offsets values. */
	public int[] spriteXOffsets;
	/** Stores the current spell usable on. */
	public int spellUsableOn;
	/** Stores fonts values. */
	private static TypeFace[] fonts;
	/** Stores item amounts values. */
	public int[] itemAmounts;
	/**
	 * Type-1 field consumed from the cache but not semantically used by this
	 * Client.
	 */
	public int type1UnknownValue;
	/** Stores the current active mouseover color. */
	public int activeMouseoverColor;
	/** Stores the current animation cycle. */
	public int animationCycle;
	/** Stores the current X offset. */
	public int xOffset;
	/** Whether inventory has options is enabled or active. */
	public boolean inventoryHasOptions;
	/** Stores the current text. */
	public String text;
	/** Stores the current scroll Y. */
	public int scrollY;
	/** Stores child X values. */
	public int[] childX;
	/**
	 * Type-1 field consumed from the cache but not semantically used by this
	 * Client.
	 */
	public boolean type1UnknownEnabled;
	/** Stores cs1 instructions values. */
	public int[][] cs1Instructions;
	/** Stores the current animation frame. */
	public int animationFrame;
	/** Stores the current type. */
	public int type;
	/** Stores the current font. */
	public TypeFace font;
	/** Stores the current height. */
	public int height;
	/** Whether filled is enabled or active. */
	public boolean filled;
	/** Stores the current color. */
	public int color;
	/** Stores the current width. */
	public int width;
	/** Stores the current content type. */
	public int contentType;
	/** Stores the current model contrast. */
	private static int modelContrast;
	/** Stores the current inventory sprite padding Y. */
	public int inventorySpritePaddingY;
	/** Stores the current active sprite. */
	public ImageRGB activeSprite;
	/** Parent interface id containing content type 600; used for Report Abuse. */
	public static int reportAbuseInterfaceId = -1;
	/** Whether text shadowed is enabled or active. */
	public boolean textShadowed;
	/** Stores the current parent ID. */
	public int parentId;
	/** Stores the current active text. */
	public String activeText;
	/** Stores the current sprite cache. */
	private static LruCache spriteCache;
	/** Stores the current model zoom. */
	public int modelZoom;
	/** Stores the current model pitch. */
	public int modelPitch;
	/** Stores the current model yaw. */
	public int modelYaw;
	/** Stores the current mouseover target ID. */
	public int mouseoverTargetId;
	/**
	 * Parent interface id containing content type 650. The supplied revision-377
	 * Client records this id while decoding but does not otherwise use it.
	 */
	public static int contentType650InterfaceId = -1;
	/** Stores cs1 comparison values values. */
	public int[] cs1ComparisonValues;
	/** Stores children values. */
	public int[] children;
	/** Stores the current Y offset. */
	public int yOffset;
	/** Stores the current active color. */
	public int activeColor;
	/** Stores the current mouseover color. */
	public int mouseoverColor;
	/** Stores actions values. */
	public String[] actions;
	/** Stores the current inventory sprite padding X. */
	public int inventorySpritePaddingX;
	/** Shared cache of widget models keyed by media type and identifier. */
	private static final LruCache modelCache = new LruCache(MODEL_CACHE_CAPACITY);
	/** Stores inventory sprites values. */
	public ImageRGB[] inventorySprites;
	/** Stores the current active media type. */
	public int activeMediaType;
	/** Stores the current active media ID. */
	public int activeMediaId;
	/** Stores the current tooltip. */
	public String tooltip;
	/** Stores item IDs values. */
	public int[] itemIds;
	/** Whether text centered is enabled or active. */
	public boolean textCentered;
	/** Stores cs1 comparisons values. */
	public int[] cs1Comparisons;
	/** Whether inventory allow swap is enabled or active. */
	public boolean inventoryAllowSwap;
	/** Stores child Y values. */
	public int[] childY;
	/**
	 * Parent interface id containing content type 655. The supplied revision-377
	 * Client records this id while decoding but does not otherwise use it.
	 */
	public static int contentType655InterfaceId = -1;
	/** Stores the current model ambient. */
	private static int modelAmbient;
	/** Stores the current selected action name. */
	public String selectedActionName;
	/** Stores encoded widgets values. */
	private static byte[][] encodedWidgets;
	/** Stores the current media type. */
	public int mediaType;
	/** Stores the current media ID. */
	public int mediaId;
	/** Stores the current scroll height. */
	public int scrollHeight;
	/** Stores the current animation ID. */
	public int animationId;
	/** Stores the current active animation ID. */
	public int activeAnimationId;
	/** Whether inventory usable items is enabled or active. */
	public boolean inventoryUsableItems;
	/** Stores the current button type. */
	public int buttonType;

	/**
	 * Returns a widget, lazily rebuilding it from the retained encoded record when
	 * an interface group has previously been unloaded.
	 * @param id the identifier
	 * @return the decoded widget for the supplied interface identifier
	 */
	public static Widget get(int id) {
		if (widgets[id] == null) {
			Buffer buffer = new Buffer(encodedWidgets[id]);
			int parentId = buffer.readUnsignedShort();
			widgets[id] = decode(parentId, buffer, id);
		}
		return widgets[id];
	}

	/**
	 * Swaps the item id and amount at two inventory slots.
	 * @param firstSlot the first slot
	 * @param secondSlot the second slot
	 */
	public void swapItems(int firstSlot, int secondSlot) {
		int itemId = itemIds[secondSlot];
		itemIds[secondSlot] = itemIds[firstSlot];
		itemIds[firstSlot] = itemId;

		int amount = itemAmounts[secondSlot];
		itemAmounts[secondSlot] = itemAmounts[firstSlot];
		itemAmounts[firstSlot] = amount;
	}

	/**
	 * Loads and decodes the complete revision-377 interface stream.
	 *
	 * @param interfaceArchive archive containing the {@code data} stream
	 * @param mediaArchive     archive used by sprite references embedded in widgets
	 * @param typeFaces        font table indexed by the one-byte font ids in
	 *                         widgets
	 */
	public static void load(Archive interfaceArchive, Archive mediaArchive, TypeFace[] typeFaces) {
		spriteCache = new LruCache(SPRITE_CACHE_CAPACITY);
		spriteArchive = mediaArchive;
		fonts = typeFaces;

		Buffer buffer = new Buffer(interfaceArchive.read("data"));
		int widgetCount = buffer.readUnsignedShort();
		widgets = new Widget[widgetCount];
		encodedWidgets = new byte[widgetCount][];

		int parentId = -1;
		while (buffer.position < buffer.payload.length) {
			int id = buffer.readUnsignedShort();
			if (id == 65535) {
				parentId = buffer.readUnsignedShort();
				id = buffer.readUnsignedShort();
			}

			int recordStart = buffer.position;
			Widget widget = decode(parentId, buffer, id);

			byte[] encoded = encodedWidgets[widget.id] = new byte[(buffer.position - recordStart) + 2];
			for (int source = recordStart; source < buffer.position; source++) {
				encoded[(source - recordStart) + 2] = buffer.payload[source];
			}
			encoded[0] = (byte) (parentId >> 8);
			encoded[1] = (byte) parentId;
		}

		// Sprite decoding is eager while loading. The archive reference is released
		// exactly as in the original Client; cached sprites remain available.
		spriteArchive = null;
	}

	/**
	 * Discards decoded widgets belonging to one parent group so they can be lazily
	 * reconstructed later. Type-2 inventory widgets are intentionally retained
	 * because their item arrays contain mutable runtime state.
	 * @param parentId the parent ID
	 */
	public static void unloadGroup(int parentId) {
		if (parentId == -1) {
			return;
		}

		for (int id = 0; id < widgets.length; id++) {
			Widget widget = widgets[id];
			if (widget != null && widget.parentId == parentId && widget.type != TYPE_INVENTORY) {
				widgets[id] = null;
			}
		}
	}

	/**
	 * Clears static interface resources. The model cache is deliberately retained,
	 * matching the original method.
	 */
	public static void clear() {
		widgets = null;
		spriteArchive = null;
		spriteCache = null;
		fonts = null;
		encodedWidgets = null;
	}

	/**
	 * Replaces the one-entry model source used for Client-built widget models. The
	 * cache is cleared before insertion, preserving revision-377 behavior.
	 * @param mediaType the media type
	 * @param mediaId the media ID
	 * @param model the model
	 */
	public static void cacheModel(int mediaType, int mediaId, Model model) {
		modelCache.clear();
		if (model != null && mediaType != 4) {
			modelCache.put((mediaType << 16) + mediaId, model);
		}
	}

	/**
	 * Builds the model displayed by a type-6 widget for its inactive or active
	 * state and optional animation frames.
	 * @param primaryFrameId the primary frame ID
	 * @param secondaryFrameId the secondary frame ID
	 * @param active whether the state is active
	 * @return the animated model
	 */
	public Model getAnimatedModel(int primaryFrameId, int secondaryFrameId, boolean active) {
		modelAmbient = 64;
		modelContrast = 768;

		Model baseModel = active ? getMediaModel(activeMediaType, activeMediaId) : getMediaModel(mediaType, mediaId);
		if (baseModel == null) {
			return null;
		}

		if (primaryFrameId == -1 && secondaryFrameId == -1 && baseModel.triangleColors == null) {
			return baseModel;
		}

		Model model = new Model(baseModel, false, true,
				AnimationFrame.isNull(primaryFrameId) & AnimationFrame.isNull(secondaryFrameId));
		if (primaryFrameId != -1 || secondaryFrameId != -1) {
			model.createBones();
		}
		if (primaryFrameId != -1) {
			model.applyTransformation(primaryFrameId);
		}
		if (secondaryFrameId != -1) {
			model.applyTransformation(secondaryFrameId);
		}
		model.light(modelAmbient, modelContrast, -50, -10, -50, true);
		return model;
	}

	/**
	 * Loads sprite.
	 *
	 * @param name the name
	 * @param index the array or registry index
	 * @return the cached or newly loaded sprite, or {@code null} if loading fails
	 */
	private static ImageRGB loadSprite(String name, int index) {
		long key = (ResourceNameHash.hash(name) << 8) + index;
		ImageRGB cached = (ImageRGB) spriteCache.get(key);
		if (cached != null) {
			return cached;
		}
		if (spriteArchive == null) {
			return null;
		}

		try {
			ImageRGB sprite = new ImageRGB(spriteArchive, name, index);
			spriteCache.put(key, sprite);
			return sprite;
		} catch (Exception ignored) {
			// Missing/malformed interface sprites were silently ignored by the
			// original Client and render as null.
			return null;
		}
	}

	/**
	 * Returns media model.
	 *
	 * @param mediaType the media type
	 * @param mediaId the media ID
	 * @return the media model
	 */
	private Model getMediaModel(int mediaType, int mediaId) {
		ItemDefinition itemDefinition = null;
		if (mediaType == 4) {
			itemDefinition = ItemDefinition.lookup(mediaId);
			modelAmbient += itemDefinition.ambient;
			modelContrast += itemDefinition.contrast;
		}

		long key = (mediaType << 16) + mediaId;
		Model model = (Model) modelCache.get(key);
		if (model != null) {
			return model;
		}

		if (mediaType == 1) {
			model = Model.getModel(mediaId);
		}
		if (mediaType == 2) {
			model = NpcDefinition.lookup(mediaId).getHeadModel();
		}
		if (mediaType == 3) {
			model = Client.localPlayer.getHeadModel();
		}
		if (mediaType == 4) {
			model = itemDefinition.getUnlitModel(50);
		}
		if (mediaType == 5) {
			model = null;
		}

		if (model != null) {
			modelCache.put(key, model);
		}
		return model;
	}

	/**
	 * Decodes the operation.
	 *
	 * @param parentId the parent ID
	 * @param buffer the source buffer
	 * @param id the identifier
	 * @return the decoded  value
	 */
	private static Widget decode(int parentId, Buffer buffer, int id) {
		Widget widget = new Widget();
		widget.id = id;
		widget.parentId = parentId;
		widget.type = buffer.readUnsignedByte();
		widget.buttonType = buffer.readUnsignedByte();
		widget.contentType = buffer.readUnsignedShort();
		widget.width = buffer.readUnsignedShort();
		widget.height = buffer.readUnsignedShort();
		widget.transparency = (byte) buffer.readUnsignedByte();

		int mouseoverHigh = buffer.readUnsignedByte();
		if (mouseoverHigh != 0) {
			widget.mouseoverTargetId = ((mouseoverHigh - 1) << 8) + buffer.readUnsignedByte();
		} else {
			widget.mouseoverTargetId = -1;
		}

		if (widget.contentType == 600) {
			reportAbuseInterfaceId = parentId;
		}
		if (widget.contentType == 650) {
			contentType650InterfaceId = parentId;
		}
		if (widget.contentType == 655) {
			contentType655InterfaceId = parentId;
		}

		int comparisonCount = buffer.readUnsignedByte();
		if (comparisonCount > 0) {
			widget.cs1Comparisons = new int[comparisonCount];
			widget.cs1ComparisonValues = new int[comparisonCount];
			for (int index = 0; index < comparisonCount; index++) {
				widget.cs1Comparisons[index] = buffer.readUnsignedByte();
				widget.cs1ComparisonValues[index] = buffer.readUnsignedShort();
			}
		}

		int instructionCount = buffer.readUnsignedByte();
		if (instructionCount > 0) {
			widget.cs1Instructions = new int[instructionCount][];
			for (int instruction = 0; instruction < instructionCount; instruction++) {
				int length = buffer.readUnsignedShort();
				widget.cs1Instructions[instruction] = new int[length];
				for (int operand = 0; operand < length; operand++) {
					widget.cs1Instructions[instruction][operand] = buffer.readUnsignedShort();
				}
			}
		}

		if (widget.type == TYPE_CONTAINER) {
			widget.scrollHeight = buffer.readUnsignedShort();
			widget.mouseoverTriggered = buffer.readUnsignedByte() == 1;
			int childCount = buffer.readUnsignedShort();
			widget.childX = new int[childCount];
			widget.childY = new int[childCount];
			int[] childIds = new int[childCount];
			// Keep the public declaration order compatible with the original while
			// assigning the children array after allocation.
			for (int child = 0; child < childCount; child++) {
				childIds[child] = buffer.readUnsignedShort();
				widget.childX[child] = buffer.readSignedShort();
				widget.childY[child] = buffer.readSignedShort();
			}
			widget.children = childIds;
		}

		if (widget.type == TYPE_UNKNOWN) {
			widget.type1UnknownValue = buffer.readUnsignedShort();
			widget.type1UnknownEnabled = buffer.readUnsignedByte() == 1;
		}

		if (widget.type == TYPE_INVENTORY) {
			int slotCount = widget.width * widget.height;
			widget.itemIds = new int[slotCount];
			widget.itemAmounts = new int[slotCount];
			widget.inventoryAllowSwap = buffer.readUnsignedByte() == 1;
			widget.inventoryHasOptions = buffer.readUnsignedByte() == 1;
			widget.inventoryUsableItems = buffer.readUnsignedByte() == 1;
			widget.inventoryReplaceItems = buffer.readUnsignedByte() == 1;
			widget.inventorySpritePaddingX = buffer.readUnsignedByte();
			widget.inventorySpritePaddingY = buffer.readUnsignedByte();
			widget.spriteXOffsets = new int[20];
			widget.spriteYOffsets = new int[20];
			widget.inventorySprites = new ImageRGB[20];

			for (int slot = 0; slot < 20; slot++) {
				int hasSprite = buffer.readUnsignedByte();
				if (hasSprite == 1) {
					widget.spriteXOffsets[slot] = buffer.readSignedShort();
					widget.spriteYOffsets[slot] = buffer.readSignedShort();
					String reference = buffer.readString();
					if (reference.length() > 0) {
						int separator = reference.lastIndexOf(',');
						widget.inventorySprites[slot] = loadSprite(reference.substring(0, separator),
								Integer.parseInt(reference.substring(separator + 1)));
					}
				}
			}

			widget.actions = new String[5];
			for (int action = 0; action < 5; action++) {
				widget.actions[action] = buffer.readString();
				if (widget.actions[action].length() == 0) {
					widget.actions[action] = null;
				}
			}
		}

		if (widget.type == TYPE_RECTANGLE) {
			widget.filled = buffer.readUnsignedByte() == 1;
		}

		if (widget.type == TYPE_TEXT || widget.type == TYPE_UNKNOWN) {
			widget.textCentered = buffer.readUnsignedByte() == 1;
			int fontId = buffer.readUnsignedByte();
			if (fonts != null) {
				widget.font = fonts[fontId];
			}
			widget.textShadowed = buffer.readUnsignedByte() == 1;
		}

		if (widget.type == TYPE_TEXT) {
			widget.text = buffer.readString();
			widget.activeText = buffer.readString();
		}

		if (widget.type == TYPE_UNKNOWN || widget.type == TYPE_RECTANGLE || widget.type == TYPE_TEXT) {
			widget.color = buffer.readInt();
		}

		if (widget.type == TYPE_RECTANGLE || widget.type == TYPE_TEXT) {
			widget.activeColor = buffer.readInt();
			widget.mouseoverColor = buffer.readInt();
			widget.activeMouseoverColor = buffer.readInt();
		}

		if (widget.type == TYPE_SPRITE) {
			String reference = buffer.readString();
			if (reference.length() > 0) {
				int separator = reference.lastIndexOf(',');
				widget.sprite = loadSprite(reference.substring(0, separator),
						Integer.parseInt(reference.substring(separator + 1)));
			}

			reference = buffer.readString();
			if (reference.length() > 0) {
				int separator = reference.lastIndexOf(',');
				widget.activeSprite = loadSprite(reference.substring(0, separator),
						Integer.parseInt(reference.substring(separator + 1)));
			}
		}

		if (widget.type == TYPE_MODEL) {
			int mediaHigh = buffer.readUnsignedByte();
			if (mediaHigh != 0) {
				widget.mediaType = 1;
				widget.mediaId = ((mediaHigh - 1) << 8) + buffer.readUnsignedByte();
			}

			mediaHigh = buffer.readUnsignedByte();
			if (mediaHigh != 0) {
				widget.activeMediaType = 1;
				widget.activeMediaId = ((mediaHigh - 1) << 8) + buffer.readUnsignedByte();
			}

			int animationHigh = buffer.readUnsignedByte();
			if (animationHigh != 0) {
				widget.animationId = ((animationHigh - 1) << 8) + buffer.readUnsignedByte();
			} else {
				widget.animationId = -1;
			}

			animationHigh = buffer.readUnsignedByte();
			if (animationHigh != 0) {
				widget.activeAnimationId = ((animationHigh - 1) << 8) + buffer.readUnsignedByte();
			} else {
				widget.activeAnimationId = -1;
			}

			widget.modelZoom = buffer.readUnsignedShort();
			widget.modelPitch = buffer.readUnsignedShort();
			widget.modelYaw = buffer.readUnsignedShort();
		}

		if (widget.type == TYPE_INVENTORY_TEXT) {
			int slotCount = widget.width * widget.height;
			widget.itemIds = new int[slotCount];
			widget.itemAmounts = new int[slotCount];
			widget.textCentered = buffer.readUnsignedByte() == 1;
			int fontId = buffer.readUnsignedByte();
			if (fonts != null) {
				widget.font = fonts[fontId];
			}
			widget.textShadowed = buffer.readUnsignedByte() == 1;
			widget.color = buffer.readInt();
			widget.inventorySpritePaddingX = buffer.readSignedShort();
			widget.inventorySpritePaddingY = buffer.readSignedShort();
			widget.inventoryHasOptions = buffer.readUnsignedByte() == 1;

			widget.actions = new String[5];
			for (int action = 0; action < 5; action++) {
				widget.actions[action] = buffer.readString();
				if (widget.actions[action].length() == 0) {
					widget.actions[action] = null;
				}
			}
		}

		if (widget.type == TYPE_TOOLTIP) {
			widget.text = buffer.readString();
		}

		if (widget.buttonType == 2 || widget.type == TYPE_INVENTORY) {
			widget.selectedActionName = buffer.readString();
			widget.spellName = buffer.readString();
			widget.spellUsableOn = buffer.readUnsignedShort();
		}

		if (widget.buttonType == 1 || widget.buttonType == 4 || widget.buttonType == 5 || widget.buttonType == 6) {
			widget.tooltip = buffer.readString();
			if (widget.tooltip.length() == 0) {
				if (widget.buttonType == 1)
					widget.tooltip = "Ok";
				if (widget.buttonType == 4)
					widget.tooltip = "Select";
				if (widget.buttonType == 5)
					widget.tooltip = "Select";
				if (widget.buttonType == 6)
					widget.tooltip = "Continue";
			}
		}

		return widget;
	}

}
