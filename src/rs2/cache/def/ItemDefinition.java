package rs2.cache.def;

import rs2.cache.Archive;
import rs2.collection.LruCache;
import rs2.media.ItemSpriteFactory;
import rs2.media.renderable.Model;
import rs2.net.Buffer;

/** Provides item definition state and behavior. */
public class ItemDefinition {

	/**
	 * Returns whether head models ready.
	 *
	 * @param gender the gender
	 * @return whether the requested condition is satisfied
	 */
	public boolean areHeadModelsReady(int gender) {
		int primaryHeadModelId = maleHeadModel0;
		int secondaryHeadModelId = maleHeadModel1;
		if (gender == 1) {
			primaryHeadModelId = femaleHeadModel0;
			secondaryHeadModelId = femaleHeadModel1;
		}
		if (primaryHeadModelId == -1)
			return true;
		boolean ready = true;
		if (!Model.isLoaded(primaryHeadModelId))
			ready = false;
		if (secondaryHeadModelId != -1 && !Model.isLoaded(secondaryHeadModelId))
			ready = false;
		return ready;
	}

	/**
	 * Looks up an item definition by identifier.
	 *
	 * @param id the id
	 * @return the matching value
	 */
	public static ItemDefinition lookup(int id) {
		for (int cacheSlot = 0; cacheSlot < 10; cacheSlot++)
			if (cache[cacheSlot].id == id)
				return cache[cacheSlot];

		cacheIndex = (cacheIndex + 1) % 10;
		ItemDefinition definition = cache[cacheIndex];
		dataBuffer.position = offsets[id];
		definition.id = id;
		definition.resetDefaults();
		definition.decode(dataBuffer);
		if (definition.noteTemplateId != -1)
			definition.toNote();
		if (!membersWorld && definition.membersOnly) {
			definition.name = "Members Object";
			definition.description = "Login to a members' server to use this object.".getBytes();
			definition.groundActions = null;
			definition.inventoryActions = null;
			definition.team = 0;
		}
		return definition;
	}

	/**
	 * Returns wearable model.
	 *
	 * @param gender the gender
	 * @return the wearable model
	 */
	public Model getWearableModel(int gender) {
		int primaryModelId = maleModel0;
		int secondaryModelId = maleModel1;
		int tertiaryModelId = maleModel2;
		if (gender == 1) {
			primaryModelId = femaleModel0;
			secondaryModelId = femaleModel1;
			tertiaryModelId = femaleModel2;
		}
		if (primaryModelId == -1)
			return null;
		Model model = Model.getModel(primaryModelId);
		if (secondaryModelId != -1)
			if (tertiaryModelId != -1) {
				Model secondaryModel = Model.getModel(secondaryModelId);
				Model tertiaryModel = Model.getModel(tertiaryModelId);
				Model modelParts[] = { model, secondaryModel, tertiaryModel };
				model = new Model(3, modelParts);
			} else {
				Model secondaryModel = Model.getModel(secondaryModelId);
				Model modelParts[] = { model, secondaryModel };
				model = new Model(2, modelParts);
			}
		if (gender == 0 && maleOffset != 0)
			model.translate(0, maleOffset, 0);
		if (gender == 1 && femaleOffset != 0)
			model.translate(0, femaleOffset, 0);
		if (recolorFrom != null) {
			for (int recolorIndex = 0; recolorIndex < recolorFrom.length; recolorIndex++)
				model.recolor(recolorFrom[recolorIndex], recolorTo[recolorIndex]);

		}
		return model;
	}

	/**
	 * Loads this class's data from the supplied source.
	 *
	 * @param archive the archive
	 */
	public static void load(Archive archive) {
		dataBuffer = new Buffer(archive.read("obj.dat"));
		Buffer indexBuffer = new Buffer(archive.read("obj.idx"));
		count = indexBuffer.readUnsignedShort();
		offsets = new int[count];
		int offset = 2;
		for (int id = 0; id < count; id++) {
			offsets[id] = offset;
			offset += indexBuffer.readUnsignedShort();
		}

		cache = new ItemDefinition[10];
		for (int cacheSlot = 0; cacheSlot < 10; cacheSlot++)
			cache[cacheSlot] = new ItemDefinition();

	}

	/**
	 * Converts this definition to its noted-item variant.
	 */
	public void toNote() {
		ItemDefinition templateDefinition = lookup(noteTemplateId);
		modelId = templateDefinition.modelId;
		zoom2d = templateDefinition.zoom2d;
		xan2d = templateDefinition.xan2d;
		yan2d = templateDefinition.yan2d;
		zan2d = templateDefinition.zan2d;
		offsetX2d = templateDefinition.offsetX2d;
		offsetY2d = templateDefinition.offsetY2d;
		recolorFrom = templateDefinition.recolorFrom;
		recolorTo = templateDefinition.recolorTo;
		ItemDefinition noteDefinition = lookup(noteId);
		name = noteDefinition.name;
		membersOnly = noteDefinition.membersOnly;
		price = noteDefinition.price;
		String article = "a";
		char firstCharacter = noteDefinition.name.charAt(0);
		if (firstCharacter == 'A' || firstCharacter == 'E' || firstCharacter == 'I' || firstCharacter == 'O'
				|| firstCharacter == 'U')
			article = "an";
		description = ("Swap this note at any bank for " + article + " " + noteDefinition.name + ".").getBytes();
		stackable = true;
	}

	/**
	 * Returns whether wearable models ready.
	 *
	 * @param gender the gender
	 * @return whether the requested condition is satisfied
	 */
	public boolean areWearableModelsReady(int gender) {
		int primaryModelId = maleModel0;
		int secondaryModelId = maleModel1;
		int tertiaryModelId = maleModel2;
		if (gender == 1) {
			primaryModelId = femaleModel0;
			secondaryModelId = femaleModel1;
			tertiaryModelId = femaleModel2;
		}
		if (primaryModelId == -1)
			return true;
		boolean ready = true;
		if (!Model.isLoaded(primaryModelId))
			ready = false;
		if (secondaryModelId != -1 && !Model.isLoaded(secondaryModelId))
			ready = false;
		if (tertiaryModelId != -1 && !Model.isLoaded(tertiaryModelId))
			ready = false;
		return ready;
	}

	/**
	 * Returns unlit model.
	 *
	 * @param amount the amount
	 * @return the unlit model
	 */
	public Model getUnlitModel(int amount) {
		if (stackVariantIds != null && amount > 1) {
			int variantId = -1;
			for (int variantIndex = 0; variantIndex < 10; variantIndex++)
				if (amount >= stackVariantAmounts[variantIndex] && stackVariantAmounts[variantIndex] != 0)
					variantId = stackVariantIds[variantIndex];

			if (variantId != -1)
				return lookup(variantId).getUnlitModel(1);
		}
		Model model = Model.getModel(modelId);
		if (model == null)
			return null;
		if (recolorFrom != null) {
			for (int recolorIndex = 0; recolorIndex < recolorFrom.length; recolorIndex++)
				model.recolor(recolorFrom[recolorIndex], recolorTo[recolorIndex]);

		}
		return model;
	}

	/**
	 * Decodes this object from the supplied data.
	 *
	 * @param buffer the buffer
	 */
	public void decode(Buffer buffer) {
		do {
			int opcode = buffer.readUnsignedByte();
			if (opcode == 0)
				return;
			if (opcode == 1)
				modelId = buffer.readUnsignedShort();
			else if (opcode == 2)
				name = buffer.readString();
			else if (opcode == 3)
				description = buffer.readStringBytes();
			else if (opcode == 4)
				zoom2d = buffer.readUnsignedShort();
			else if (opcode == 5)
				xan2d = buffer.readUnsignedShort();
			else if (opcode == 6)
				yan2d = buffer.readUnsignedShort();
			else if (opcode == 7) {
				offsetX2d = buffer.readUnsignedShort();
				if (offsetX2d > 32767)
					offsetX2d -= 0x10000;
			} else if (opcode == 8) {
				offsetY2d = buffer.readUnsignedShort();
				if (offsetY2d > 32767)
					offsetY2d -= 0x10000;
			} else if (opcode == 10)
				opcode10Value = buffer.readUnsignedShort();
			else if (opcode == 11)
				stackable = true;
			else if (opcode == 12)
				price = buffer.readInt();
			else if (opcode == 16)
				membersOnly = true;
			else if (opcode == 23) {
				maleModel0 = buffer.readUnsignedShort();
				maleOffset = buffer.readSignedByte();
			} else if (opcode == 24)
				maleModel1 = buffer.readUnsignedShort();
			else if (opcode == 25) {
				femaleModel0 = buffer.readUnsignedShort();
				femaleOffset = buffer.readSignedByte();
			} else if (opcode == 26)
				femaleModel1 = buffer.readUnsignedShort();
			else if (opcode >= 30 && opcode < 35) {
				if (groundActions == null)
					groundActions = new String[5];
				groundActions[opcode - 30] = buffer.readString();
				if (groundActions[opcode - 30].equalsIgnoreCase("hidden"))
					groundActions[opcode - 30] = null;
			} else if (opcode >= 35 && opcode < 40) {
				if (inventoryActions == null)
					inventoryActions = new String[5];
				inventoryActions[opcode - 35] = buffer.readString();
			} else if (opcode == 40) {
				int recolorCount = buffer.readUnsignedByte();
				recolorFrom = new int[recolorCount];
				recolorTo = new int[recolorCount];
				for (int recolorIndex = 0; recolorIndex < recolorCount; recolorIndex++) {
					recolorFrom[recolorIndex] = buffer.readUnsignedShort();
					recolorTo[recolorIndex] = buffer.readUnsignedShort();
				}

			} else if (opcode == 78)
				maleModel2 = buffer.readUnsignedShort();
			else if (opcode == 79)
				femaleModel2 = buffer.readUnsignedShort();
			else if (opcode == 90)
				maleHeadModel0 = buffer.readUnsignedShort();
			else if (opcode == 91)
				femaleHeadModel0 = buffer.readUnsignedShort();
			else if (opcode == 92)
				maleHeadModel1 = buffer.readUnsignedShort();
			else if (opcode == 93)
				femaleHeadModel1 = buffer.readUnsignedShort();
			else if (opcode == 95)
				zan2d = buffer.readUnsignedShort();
			else if (opcode == 97)
				noteId = buffer.readUnsignedShort();
			else if (opcode == 98)
				noteTemplateId = buffer.readUnsignedShort();
			else if (opcode >= 100 && opcode < 110) {
				if (stackVariantIds == null) {
					stackVariantIds = new int[10];
					stackVariantAmounts = new int[10];
				}
				stackVariantIds[opcode - 100] = buffer.readUnsignedShort();
				stackVariantAmounts[opcode - 100] = buffer.readUnsignedShort();
			} else if (opcode == 110)
				resizeX = buffer.readUnsignedShort();
			else if (opcode == 111)
				resizeY = buffer.readUnsignedShort();
			else if (opcode == 112)
				resizeZ = buffer.readUnsignedShort();
			else if (opcode == 113)
				ambient = buffer.readSignedByte();
			else if (opcode == 114)
				contrast = buffer.readSignedByte() * 5;
			else if (opcode == 115)
				team = buffer.readUnsignedByte();
		} while (true);
	}

	/**
	 * Returns head model.
	 *
	 * @param gender the gender
	 * @return the head model
	 */
	public Model getHeadModel(int gender) {
		int primaryHeadModelId = maleHeadModel0;
		int secondaryHeadModelId = maleHeadModel1;
		if (gender == 1) {
			primaryHeadModelId = femaleHeadModel0;
			secondaryHeadModelId = femaleHeadModel1;
		}
		if (primaryHeadModelId == -1)
			return null;
		Model model = Model.getModel(primaryHeadModelId);
		if (secondaryHeadModelId != -1) {
			Model secondaryModel = Model.getModel(secondaryHeadModelId);
			Model modelParts[] = { model, secondaryModel };
			model = new Model(2, modelParts);
		}
		if (recolorFrom != null) {
			for (int recolorIndex = 0; recolorIndex < recolorFrom.length; recolorIndex++)
				model.recolor(recolorFrom[recolorIndex], recolorTo[recolorIndex]);

		}
		return model;
	}

	/**
	 * Returns model.
	 *
	 * @param amount the amount
	 * @return the model
	 */
	public Model getModel(int amount) {
		if (stackVariantIds != null && amount > 1) {
			int variantId = -1;
			for (int variantIndex = 0; variantIndex < 10; variantIndex++)
				if (amount >= stackVariantAmounts[variantIndex] && stackVariantAmounts[variantIndex] != 0)
					variantId = stackVariantIds[variantIndex];

			if (variantId != -1)
				return lookup(variantId).getModel(1);
		}
		Model model = (Model) modelCache.get(id);
		if (model != null)
			return model;
		model = Model.getModel(modelId);
		if (model == null)
			return null;
		if (resizeX != 128 || resizeY != 128 || resizeZ != 128)
			model.scale(resizeX, resizeY, resizeZ);
		if (recolorFrom != null) {
			for (int recolorIndex = 0; recolorIndex < recolorFrom.length; recolorIndex++)
				model.recolor(recolorFrom[recolorIndex], recolorTo[recolorIndex]);

		}
		model.light(64 + ambient, 768 + contrast, -50, -10, -50, true);
		model.singleTile = true;
		modelCache.put(id, model);
		return model;
	}

	/**
	 * Clears the retained class state.
	 */
	public static void clear() {
		modelCache = null;
		ItemSpriteFactory.clear();
		offsets = null;
		cache = null;
		dataBuffer = null;
	}

	/**
	 * Resets defaults.
	 */
	public void resetDefaults() {
		modelId = 0;
		name = null;
		description = null;
		recolorFrom = null;
		recolorTo = null;
		zoom2d = 2000;
		xan2d = 0;
		yan2d = 0;
		zan2d = 0;
		offsetX2d = 0;
		offsetY2d = 0;
		opcode10Value = -1;
		stackable = false;
		price = 1;
		membersOnly = false;
		groundActions = null;
		inventoryActions = null;
		maleModel0 = -1;
		maleModel1 = -1;
		maleOffset = 0;
		femaleModel0 = -1;
		femaleModel1 = -1;
		femaleOffset = 0;
		maleModel2 = -1;
		femaleModel2 = -1;
		maleHeadModel0 = -1;
		maleHeadModel1 = -1;
		femaleHeadModel0 = -1;
		femaleHeadModel1 = -1;
		stackVariantIds = null;
		stackVariantAmounts = null;
		noteId = -1;
		noteTemplateId = -1;
		resizeX = 128;
		resizeY = 128;
		resizeZ = 128;
		ambient = 0;
		contrast = 0;
		team = 0;
	}

	/**
	 * Creates a new ItemDefinition instance.
	 */
	public ItemDefinition() {
		id = -1;
	}

	/** Stores the current female model0. */
	public int femaleModel0;

	/** Stores the current offset x2d. */
	public int offsetX2d;

	/** Stores description values. */
	public byte description[];

	/** Stores the current name. */
	public String name;

	/** Stores the current female offset. */
	public byte femaleOffset;

	/** Stores the current male model1. */
	public int maleModel1;

	/** Stores the current team. */
	public int team;

	/** Stores the current note ID. */
	public int noteId;

	/** Stores the current male head model0. */
	public int maleHeadModel0;

	/** Stores the current count. */
	public static int count;

	/** Stores cache values. */
	public static ItemDefinition cache[];

	/**
	 * Model cache.
	 *
	 */
	public static LruCache modelCache = new LruCache(50);

	/** Stores ground actions values. */
	public String groundActions[];

	/** Stores the current zan2d. */
	public int zan2d;

	/** Stores the current offset y2d. */
	public int offsetY2d;

	/** Stores recolor to values. */
	public int recolorTo[];

	/** Stores offsets values. */
	public static int offsets[];

	/** Stores the current note template ID. */
	public int noteTemplateId;
	/** Tracks whether members world. */
	public static boolean membersWorld = true;

	/** Stores the current price. */
	public int price;

	/** Stores inventory actions values. */
	public String inventoryActions[];

	/** Stores the current cache index. */
	public static int cacheIndex;

	/** Stores the current male model0. */
	public int maleModel0;

	/** Stores the current ambient. */
	public int ambient;

	/** Stores the current female model1. */
	public int femaleModel1;

	/** Stores the current yan2d. */
	public int yan2d;

	/** Stores the current resize Y. */
	public int resizeY;

	/** Stores the current contrast. */
	public int contrast;

	/** Stores the current xan2d. */
	public int xan2d;

	/** Stores the current model ID. */
	public int modelId;

	/** Stores the current male head model1. */
	public int maleHeadModel1;

	/** Stores the current female head model1. */
	public int femaleHeadModel1;

	/** Stores the current ID. */
	public int id;

	/** Stores recolor from values. */
	public int recolorFrom[];

	/** Stores stack variant IDs values. */
	public int stackVariantIds[];

	/** Stores the current resize X. */
	public int resizeX;

	/** Stores the current female model2. */
	public int femaleModel2;

	/** Stores the current resize Z. */
	public int resizeZ;

	/** Stores the current zoom2d. */
	public int zoom2d;

	/** Stores the current male model2. */
	public int maleModel2;
	/** Tracks whether stackable. */
	public boolean stackable;

	/** Stores the current opcode10 value. */
	public int opcode10Value;

	/** Stores the current data buffer. */
	public static Buffer dataBuffer;

	/** Stores the current female head model0. */
	public int femaleHeadModel0;

	/** Stores stack variant amounts values. */
	public int stackVariantAmounts[];
	/** Tracks whether members only. */
	public boolean membersOnly;

	/** Stores the current male offset. */
	public byte maleOffset;

}
