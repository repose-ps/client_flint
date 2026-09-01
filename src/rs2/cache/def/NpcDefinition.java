package rs2.cache.def;

import rs2.cache.Archive;
import rs2.cache.cfg.BitMasks;
import rs2.cache.cfg.Varbit;
import rs2.cache.cfg.VarpProvider;
import rs2.collection.LruCache;
import rs2.media.animation.AnimationFrame;
import rs2.media.model.Model;
import rs2.net.Buffer;

/**
 * Revision-377 NPC definition decoded from {@code npc.dat}/{@code npc.idx}.
 *
 * <p>
 * Definitions are retained in the original 20-entry rotating decode cache,
 * while lit base body models use a separate 30-entry LRU cache. Morph selection
 * is controlled by either a varbit or a varp, with the varbit taking precedence
 * exactly as in the Client.
 * </p>
 */
public class NpcDefinition {
	/* Cache-format opcode values. */
	/** Opcode for end. */
	private static final int OPCODE_END = 0;
	/** Opcode for models. */
	private static final int OPCODE_MODELS = 1;
	/** Opcode for name. */
	private static final int OPCODE_NAME = 2;
	/** Opcode for description. */
	private static final int OPCODE_DESCRIPTION = 3;
	/** Opcode for size. */
	private static final int OPCODE_SIZE = 12;
	/** Opcode for idle sequence. */
	private static final int OPCODE_IDLE_SEQUENCE = 13;
	/** Opcode for walk sequence. */
	private static final int OPCODE_WALK_SEQUENCE = 14;
	/** Opcode for movement sequences. */
	private static final int OPCODE_MOVEMENT_SEQUENCES = 17;
	/** Opcode for recolors. */
	private static final int OPCODE_RECOLORS = 40;
	/** Opcode for head models. */
	private static final int OPCODE_HEAD_MODELS = 60;
	/** Opcode for unknown 90. */
	private static final int OPCODE_UNKNOWN_90 = 90;
	/** Opcode for unknown 91. */
	private static final int OPCODE_UNKNOWN_91 = 91;
	/** Opcode for unknown 92. */
	private static final int OPCODE_UNKNOWN_92 = 92;
	/** Opcode for hide minimap. */
	private static final int OPCODE_HIDE_MINIMAP = 93;
	/** Opcode for combat level. */
	private static final int OPCODE_COMBAT_LEVEL = 95;
	/** Opcode for scale xz. */
	private static final int OPCODE_SCALE_XZ = 97;
	/** Opcode for scale y. */
	private static final int OPCODE_SCALE_Y = 98;
	/** Opcode for priority render. */
	private static final int OPCODE_PRIORITY_RENDER = 99;
	/** Opcode for ambient. */
	private static final int OPCODE_AMBIENT = 100;
	/** Opcode for contrast. */
	private static final int OPCODE_CONTRAST = 101;
	/** Opcode for prayer icon. */
	private static final int OPCODE_PRAYER_ICON = 102;
	/** Opcode for turn speed. */
	private static final int OPCODE_TURN_SPEED = 103;
	/** Opcode for morphs. */
	private static final int OPCODE_MORPHS = 106;
	/** Opcode for not clickable. */
	private static final int OPCODE_NOT_CLICKABLE = 107;
	/** First opcode in the action range. */
	private static final int ACTION_OPCODE_FIRST = 30;
	/** Exclusive upper bound of the action opcode range. */
	private static final int ACTION_OPCODE_LIMIT = 40;
	/** Action count. */
	private static final int ACTION_COUNT = 5;

	/** Creates a new NPC definition with its default client state. */
	public NpcDefinition() {
	}

	/** Stores the current idle sequence. */
	public int idleSequence = -1;
	/** Stores morph IDs values. */
	public int[] morphIds;
	/** Stores head model IDs values. */
	public int[] headModelIds;
	/** Stores model IDs values. */
	public int[] modelIds;
	/**
	 * Value consumed by opcode 91. Its role is not established by the supplied
	 * Client.
	 */
	public int opcode91Value = -1;
	/** Stores the current ID. */
	public long id = -1L;
	/** Current varp values used to select morph definitions. */
	private static VarpProvider varpProvider;
	/** Stores the current scale Y. */
	public int scaleY = 128;
	/** Whether clickable is enabled or active. */
	public boolean clickable = true;
	/** Stores the current scale xz. */
	public int scaleXZ = 128;
	/** Stores the current turn90 ccw sequence. */
	public int turn90CcwSequence = -1;
	/** Stores recolor from values. */
	public int[] recolorFrom;
	/**
	 * Model cache.
	 *
	 */
	public static LruCache modelCache = new LruCache(30);
	/** Whether visible on minimap is enabled or active. */
	public boolean visibleOnMinimap = true;
	/**
	 * Value consumed by opcode 92. Its role is not established by the supplied
	 * Client.
	 */
	public int opcode92Value = -1;
	/** Stores the current prayer icon. */
	public int prayerIcon = -1;
	/** Stores the current combat level. */
	public int combatLevel = -1;
	/** Stores the current turn90 cw sequence. */
	public int turn90CwSequence = -1;
	/** Stores the current size. */
	public byte size = 1;
	/** Stores the current walk back sequence. */
	public int walkBackSequence = -1;
	/** Whether priority render is enabled or active. */
	public boolean priorityRender;
	/** Stores the current walk sequence. */
	public int walkSequence = -1;
	/** Stores actions values. */
	public String[] actions;
	/**
	 * Value consumed by opcode 90. Its role is not established by the supplied
	 * Client.
	 */
	public int opcode90Value = -1;
	/** Stores the current count. */
	public static int count;
	/** Stores offsets values. */
	public static int[] offsets;
	/** Stores the current turn speed. */
	public int turnSpeed = 32;
	/** Stores the current name. */
	public String name = "null";
	/** Stores the current varbit ID. */
	public int varbitId = -1;
	/** Stores cache values. */
	private static NpcDefinition[] cache;
	/** Stores recolor to values. */
	public int[] recolorTo;
	/** Stores the current data buffer. */
	private static Buffer dataBuffer;
	/**
	 * Contrast adjustment. Opcode 101 stores the signed byte multiplied by five.
	 */
	public int contrast;
	/** Stores the current varp ID. */
	public int varpId = -1;
	/** Stores description values. */
	public byte[] description;
	/** Stores the current cache index. */
	private static int cacheIndex;
	/** Stores the current ambient. */
	public int ambient;

	/**
	 * Decodes one opcode-delimited definition.
	 * 
	 * @param buffer the source buffer
	 */
	public void decode(Buffer buffer) {
		while (true) {
			int opcode = buffer.readUnsignedByte();
			if (opcode == OPCODE_END) {
				return;
			}
			switch (opcode) {
			case OPCODE_MODELS:
				int modelCount = buffer.readUnsignedByte();
				modelIds = new int[modelCount];
				for (int index = 0; index < modelCount; index++) {
					modelIds[index] = buffer.readUnsignedShort();
				}
				break;
			case OPCODE_NAME:
				name = buffer.readString();
				break;
			case OPCODE_DESCRIPTION:
				description = buffer.readStringBytes();
				break;
			case OPCODE_SIZE:
				size = buffer.readSignedByte();
				break;
			case OPCODE_IDLE_SEQUENCE:
				idleSequence = buffer.readUnsignedShort();
				break;
			case OPCODE_WALK_SEQUENCE:
				walkSequence = buffer.readUnsignedShort();
				break;
			case OPCODE_MOVEMENT_SEQUENCES:
				walkSequence = buffer.readUnsignedShort();
				walkBackSequence = buffer.readUnsignedShort();
				turn90CwSequence = buffer.readUnsignedShort();
				turn90CcwSequence = buffer.readUnsignedShort();
				break;
			case OPCODE_RECOLORS:
				int recolorCount = buffer.readUnsignedByte();
				recolorFrom = new int[recolorCount];
				recolorTo = new int[recolorCount];
				for (int index = 0; index < recolorCount; index++) {
					recolorFrom[index] = buffer.readUnsignedShort();
					recolorTo[index] = buffer.readUnsignedShort();
				}
				break;
			case OPCODE_HEAD_MODELS:
				int headModelCount = buffer.readUnsignedByte();
				headModelIds = new int[headModelCount];
				for (int index = 0; index < headModelCount; index++) {
					headModelIds[index] = buffer.readUnsignedShort();
				}
				break;
			case OPCODE_UNKNOWN_90:
				opcode90Value = buffer.readUnsignedShort();
				break;
			case OPCODE_UNKNOWN_91:
				opcode91Value = buffer.readUnsignedShort();
				break;
			case OPCODE_UNKNOWN_92:
				opcode92Value = buffer.readUnsignedShort();
				break;
			case OPCODE_HIDE_MINIMAP:
				visibleOnMinimap = false;
				break;
			case OPCODE_COMBAT_LEVEL:
				combatLevel = buffer.readUnsignedShort();
				break;
			case OPCODE_SCALE_XZ:
				scaleXZ = buffer.readUnsignedShort();
				break;
			case OPCODE_SCALE_Y:
				scaleY = buffer.readUnsignedShort();
				break;
			case OPCODE_PRIORITY_RENDER:
				priorityRender = true;
				break;
			case OPCODE_AMBIENT:
				ambient = buffer.readSignedByte();
				break;
			case OPCODE_CONTRAST:
				contrast = buffer.readSignedByte() * 5;
				break;
			case OPCODE_PRAYER_ICON:
				prayerIcon = buffer.readUnsignedShort();
				break;
			case OPCODE_TURN_SPEED:
				turnSpeed = buffer.readUnsignedShort();
				break;
			case OPCODE_MORPHS:
				varbitId = buffer.readUnsignedShort();
				if (varbitId == DefinitionConstants.NULL_REFERENCE_ID) {
					varbitId = -1;
				}
				varpId = buffer.readUnsignedShort();
				if (varpId == DefinitionConstants.NULL_REFERENCE_ID) {
					varpId = -1;
				}
				int lastMorphIndex = buffer.readUnsignedByte();
				morphIds = new int[lastMorphIndex + 1];
				for (int index = 0; index <= lastMorphIndex; index++) {
					morphIds[index] = buffer.readUnsignedShort();
					if (morphIds[index] == DefinitionConstants.NULL_REFERENCE_ID) {
						morphIds[index] = -1;
					}
				}
				break;
			case OPCODE_NOT_CLICKABLE:
				clickable = false;
				break;
			default:
				if (opcode >= ACTION_OPCODE_FIRST && opcode < ACTION_OPCODE_LIMIT) {
					if (actions == null) {
						actions = new String[ACTION_COUNT];
					}
					// The supplied 377 decoder accepts {@value #ACTION_OPCODE_FIRST}..39 despite
					// allocating only five
					// slots. Keeping that range intentionally preserves its malformed-input
					// behavior for opcodes 35..39.
					actions[opcode - ACTION_OPCODE_FIRST] = buffer.readString();
					if (actions[opcode - ACTION_OPCODE_FIRST].equalsIgnoreCase("hidden")) {
						actions[opcode - ACTION_OPCODE_FIRST] = null;
					}
				}
				break;
			}
		}
	}

	/** Releases the definition and base-model caches. */
	public static void clear() {
		varpProvider = null;
		modelCache = null;
		offsets = null;
		cache = null;
		dataBuffer = null;
	}

	/**
	 * Builds the head/dialogue model, or {@code null} when required model files are
	 * unavailable.
	 * 
	 * @return the head model
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
	 * 
	 * @return whether morph visible
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
	 * 
	 * @param archive      the source archive
	 * @param currentVarps current varp values used by morph definitions
	 */
	public static void load(Archive archive, VarpProvider currentVarps) {
		varpProvider = currentVarps;
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
	 * 
	 * @param primaryFrameId   the primary frame ID
	 * @param secondaryFrameId the secondary frame ID
	 * @param interleaveOrder  the interleave order
	 * @return the animated model
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
	 * 
	 * @return the active morph definition, or {@code null} when no valid morph is
	 *         selected
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
	 * 
	 * @param id the identifier
	 * @return the result
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

	/**
	 * Returns morph index.
	 *
	 * @return the morph index
	 */
	private int getMorphIndex() {
		if (varbitId != -1) {
			Varbit varbit = Varbit.definitions[varbitId];
			int varp = varbit.varpId;
			int leastBit = varbit.leastSignificantBit;
			int mostBit = varbit.mostSignificantBit;
			int mask = BitMasks.get(mostBit - leastBit);
			return varpProvider.getVarp(varp) >> leastBit & mask;
		}
		if (varpId != -1) {
			return varpProvider.getVarp(varpId);
		}
		return -1;
	}

	/**
	 * Applies this definition's recoloring table to a model.
	 *
	 * @param model the model
	 */
	private void recolor(Model model) {
		if (recolorFrom == null) {
			return;
		}
		for (int index = 0; index < recolorFrom.length; index++) {
			model.recolor(recolorFrom[index], recolorTo[index]);
		}
	}
}
