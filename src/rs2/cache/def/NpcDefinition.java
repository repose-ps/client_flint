package rs2.cache.def;

import rs2.client;
import rs2.cache.Archive;
import rs2.cache.cfg.Varbit;
import rs2.collection.LruCache;
import rs2.media.AnimationFrame;
import rs2.media.renderable.Model;
import rs2.net.Buffer;

/**
 * Revision-377 NPC definition decoded from {@code npc.dat}/{@code npc.idx}.
 *
 * <p>
 * Definitions are retained in the original 20-entry rotating decode cache,
 * while lit base body models use a separate 30-entry LRU cache. Morph selection
 * is controlled by either a varbit or a varp, with the varbit taking precedence
 * exactly as in the client.
 * </p>
 */
public class NpcDefinition {

	public int idleSequence = -1;
	public int[] morphIds;
	public int[] headModelIds;
	public int[] modelIds;
	/**
	 * Value consumed by opcode 91. Its role is not established by the supplied
	 * client.
	 */
	public int opcode91Value = -1;
	public long id = -1L;
	public static client clientInstance;
	public int scaleY = 128;
	public boolean clickable = true;
	public int scaleXZ = 128;
	public int turn90CcwSequence = -1;
	public int[] recolorFrom;
	public static LruCache modelCache = new LruCache(30);
	public boolean visibleOnMinimap = true;
	/**
	 * Value consumed by opcode 92. Its role is not established by the supplied
	 * client.
	 */
	public int opcode92Value = -1;
	public int prayerIcon = -1;
	public int combatLevel = -1;
	public int turn90CwSequence = -1;
	public byte size = 1;
	public int walkBackSequence = -1;
	public boolean priorityRender;
	public int walkSequence = -1;
	public String[] actions;
	/**
	 * Value consumed by opcode 90. Its role is not established by the supplied
	 * client.
	 */
	public int opcode90Value = -1;
	public static int count;
	public static int[] offsets;
	public int turnSpeed = 32;
	public String name = "null";
	public int varbitId = -1;
	private static NpcDefinition[] cache;
	public int[] recolorTo;
	private static Buffer dataBuffer;
	/**
	 * Contrast adjustment. Opcode 101 stores the signed byte multiplied by five.
	 */
	public int contrast;
	public int varpId = -1;
	public byte[] description;
	private static int cacheIndex;
	public int ambient;

	/** Decodes one opcode-delimited definition. */
	public void decode(Buffer buffer) {
		while (true) {
			int opcode = buffer.readUnsignedByte();
			if (opcode == 0) {
				return;
			}
			switch (opcode) {
			case 1:
				int modelCount = buffer.readUnsignedByte();
				modelIds = new int[modelCount];
				for (int index = 0; index < modelCount; index++) {
					modelIds[index] = buffer.readUnsignedShort();
				}
				break;
			case 2:
				name = buffer.readString();
				break;
			case 3:
				description = buffer.readStringBytes();
				break;
			case 12:
				size = buffer.readSignedByte();
				break;
			case 13:
				idleSequence = buffer.readUnsignedShort();
				break;
			case 14:
				walkSequence = buffer.readUnsignedShort();
				break;
			case 17:
				walkSequence = buffer.readUnsignedShort();
				walkBackSequence = buffer.readUnsignedShort();
				turn90CwSequence = buffer.readUnsignedShort();
				turn90CcwSequence = buffer.readUnsignedShort();
				break;
			case 40:
				int recolorCount = buffer.readUnsignedByte();
				recolorFrom = new int[recolorCount];
				recolorTo = new int[recolorCount];
				for (int index = 0; index < recolorCount; index++) {
					recolorFrom[index] = buffer.readUnsignedShort();
					recolorTo[index] = buffer.readUnsignedShort();
				}
				break;
			case 60:
				int headModelCount = buffer.readUnsignedByte();
				headModelIds = new int[headModelCount];
				for (int index = 0; index < headModelCount; index++) {
					headModelIds[index] = buffer.readUnsignedShort();
				}
				break;
			case 90:
				opcode90Value = buffer.readUnsignedShort();
				break;
			case 91:
				opcode91Value = buffer.readUnsignedShort();
				break;
			case 92:
				opcode92Value = buffer.readUnsignedShort();
				break;
			case 93:
				visibleOnMinimap = false;
				break;
			case 95:
				combatLevel = buffer.readUnsignedShort();
				break;
			case 97:
				scaleXZ = buffer.readUnsignedShort();
				break;
			case 98:
				scaleY = buffer.readUnsignedShort();
				break;
			case 99:
				priorityRender = true;
				break;
			case 100:
				ambient = buffer.readSignedByte();
				break;
			case 101:
				contrast = buffer.readSignedByte() * 5;
				break;
			case 102:
				prayerIcon = buffer.readUnsignedShort();
				break;
			case 103:
				turnSpeed = buffer.readUnsignedShort();
				break;
			case 106:
				varbitId = buffer.readUnsignedShort();
				if (varbitId == 65535) {
					varbitId = -1;
				}
				varpId = buffer.readUnsignedShort();
				if (varpId == 65535) {
					varpId = -1;
				}
				int lastMorphIndex = buffer.readUnsignedByte();
				morphIds = new int[lastMorphIndex + 1];
				for (int index = 0; index <= lastMorphIndex; index++) {
					morphIds[index] = buffer.readUnsignedShort();
					if (morphIds[index] == 65535) {
						morphIds[index] = -1;
					}
				}
				break;
			case 107:
				clickable = false;
				break;
			default:
				if (opcode >= 30 && opcode < 40) {
					if (actions == null) {
						actions = new String[5];
					}
					// The supplied 377 decoder accepts 30..39 despite allocating only five
					// slots. Keeping that range intentionally preserves its malformed-input
					// behavior for opcodes 35..39.
					actions[opcode - 30] = buffer.readString();
					if (actions[opcode - 30].equalsIgnoreCase("hidden")) {
						actions[opcode - 30] = null;
					}
				}
				break;
			}
		}
	}

	/** Releases the definition and base-model caches. */
	public static void clear() {
		modelCache = null;
		offsets = null;
		cache = null;
		dataBuffer = null;
	}

	/**
	 * Builds the head/dialogue model, or {@code null} when required model files are
	 * unavailable.
	 */
	public Model getHeadModel() {
		if (morphIds != null) {
			NpcDefinition transformed = transform();
			return transformed == null ? null : transformed.getHeadModel();
		}
		if (headModelIds == null) {
			return null;
		}

		for (int modelId : headModelIds) {
			if (!Model.isLoaded(modelId)) {
				return null;
			}
		}

		Model[] parts = new Model[headModelIds.length];
		for (int index = 0; index < headModelIds.length; index++) {
			parts[index] = Model.getModel(headModelIds[index]);
		}
		Model model = parts.length == 1 ? parts[0] : new Model(parts.length, parts);
		recolor(model);
		return model;
	}

	/**
	 * Returns whether the currently selected morph points at a valid NPC
	 * definition.
	 */
	public boolean isMorphVisible() {
		if (morphIds == null) {
			return true;
		}
		int morphIndex = getMorphIndex();
		return morphIndex >= 0 && morphIndex < morphIds.length && morphIds[morphIndex] != -1;
	}

	/**
	 * Loads the indexed NPC definition table from {@code npc.dat}/{@code npc.idx}.
	 */
	public static void load(Archive archive) {
		dataBuffer = new Buffer(archive.read("npc.dat"));
		Buffer indexBuffer = new Buffer(archive.read("npc.idx"));
		count = indexBuffer.readUnsignedShort();
		offsets = new int[count];
		int offset = 2;
		for (int id = 0; id < count; id++) {
			offsets[id] = offset;
			offset += indexBuffer.readUnsignedShort();
		}
		cache = new NpcDefinition[20];
		for (int index = 0; index < cache.length; index++) {
			cache[index] = new NpcDefinition();
		}
	}

	/**
	 * Returns a lit animated body model. The shared scratch model behavior is
	 * retained exactly; callers must not retain the returned transformed scratch
	 * model as an immutable instance.
	 */
	public Model getAnimatedModel(int primaryFrameId, int secondaryFrameId, int[] interleaveOrder) {
		if (morphIds != null) {
			NpcDefinition transformed = transform();
			return transformed == null ? null
					: transformed.getAnimatedModel(primaryFrameId, secondaryFrameId, interleaveOrder);
		}

		Model baseModel = (Model) modelCache.get(id);
		if (baseModel == null) {
			for (int modelId : modelIds) {
				if (!Model.isLoaded(modelId)) {
					return null;
				}
			}

			Model[] parts = new Model[modelIds.length];
			for (int index = 0; index < modelIds.length; index++) {
				parts[index] = Model.getModel(modelIds[index]);
			}
			baseModel = parts.length == 1 ? parts[0] : new Model(parts.length, parts);
			recolor(baseModel);
			baseModel.createBones();
			baseModel.light(64 + ambient, 850 + contrast, -30, -50, -30, true);
			modelCache.put(id, baseModel);
		}

		Model animatedModel = Model.sharedModel;
		animatedModel.replaceWithModel(baseModel,
				AnimationFrame.isNull(primaryFrameId) & AnimationFrame.isNull(secondaryFrameId));
		if (primaryFrameId != -1 && secondaryFrameId != -1) {
			animatedModel.mixAnimationFrames(primaryFrameId, secondaryFrameId, interleaveOrder);
		} else if (primaryFrameId != -1) {
			animatedModel.applyTransformation(primaryFrameId);
		}
		if (scaleXZ != 128 || scaleY != 128) {
			animatedModel.scale(scaleXZ, scaleY, scaleXZ);
		}
		animatedModel.calculateDiagonals();
		animatedModel.triangleGroups = null;
		animatedModel.vertexGroups = null;
		if (size == 1) {
			animatedModel.singleTile = true;
		}
		return animatedModel;
	}

	/**
	 * Resolves this definition's active varbit/varp morph, or returns {@code null}.
	 */
	public NpcDefinition transform() {
		int morphIndex = getMorphIndex();
		if (morphIndex < 0 || morphIndex >= morphIds.length || morphIds[morphIndex] == -1) {
			return null;
		}
		return lookup(morphIds[morphIndex]);
	}

	/**
	 * Retrieves a definition through the original 20-entry rotating decode cache.
	 */
	public static NpcDefinition lookup(int id) {
		for (NpcDefinition definition : cache) {
			if (definition.id == id) {
				return definition;
			}
		}

		cacheIndex = (cacheIndex + 1) % 20;
		NpcDefinition definition = cache[cacheIndex] = new NpcDefinition();
		dataBuffer.position = offsets[id];
		definition.id = id;
		definition.decode(dataBuffer);
		return definition;
	}

	private int getMorphIndex() {
		if (varbitId != -1) {
			Varbit varbit = Varbit.definitions[varbitId];
			int varp = varbit.varpId;
			int leastBit = varbit.leastSignificantBit;
			int mostBit = varbit.mostSignificantBit;
			int mask = client.anIntArray1214[mostBit - leastBit];
			return clientInstance.anIntArray1039[varp] >> leastBit & mask;
		}
		if (varpId != -1) {
			return clientInstance.anIntArray1039[varpId];
		}
		return -1;
	}

	private void recolor(Model model) {
		if (recolorFrom == null) {
			return;
		}
		for (int index = 0; index < recolorFrom.length; index++) {
			model.recolor(recolorFrom[index], recolorTo[index]);
		}
	}
}
