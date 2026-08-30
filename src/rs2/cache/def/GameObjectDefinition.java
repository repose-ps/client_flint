package rs2.cache.def;

import rs2.Client;
import rs2.cache.Archive;
import rs2.cache.cfg.Varbit;
import rs2.cache.ondemand.OnDemandFetcher;
import rs2.collection.LruCache;
import rs2.media.AnimationFrame;
import rs2.media.model.Model;
import rs2.net.Buffer;

/**
 * Revision-377 world-location definition loaded from
 * {@code loc.dat}/{@code loc.idx}.
 *
 * <p>
 * RuneScape's cache terminology distinguishes world locations ({@code loc})
 * from inventory objects/items ({@code obj}). Definitions are decoded through
 * an opcode stream, retained in a 20-entry rotating definition cache, and use
 * separate LRU caches for raw source models and fully transformed/lit location
 * models.
 * </p>
 *
 * <p>
 * The class deliberately preserves revision-377 details such as the opcode-1/5
 * low-memory decode choice, the opcode-19 derived interactability fallback,
 * opcode-74 collision clearing, opcode-75 support-item defaulting,
 * orientation-aware model mirroring, cache-key packing, and terrain contour
 * interpolation.
 * </p>
 */
public class GameObjectDefinition {
	/* Cache-format opcode values. */
	/** Opcode for end. */
	private static final int OPCODE_END = 0;
	/** Opcode for typed models. */
	private static final int OPCODE_TYPED_MODELS = 1;
	/** Opcode for name. */
	private static final int OPCODE_NAME = 2;
	/** Opcode for description. */
	private static final int OPCODE_DESCRIPTION = 3;
	/** Opcode for models. */
	private static final int OPCODE_MODELS = 5;
	/** Opcode for size x. */
	private static final int OPCODE_SIZE_X = 14;
	/** Opcode for size y. */
	private static final int OPCODE_SIZE_Y = 15;
	/** Opcode for allow walking. */
	private static final int OPCODE_ALLOW_WALKING = 17;
	/** Opcode for allow projectiles. */
	private static final int OPCODE_ALLOW_PROJECTILES = 18;
	/** Opcode for interactive. */
	private static final int OPCODE_INTERACTIVE = 19;
	/** Opcode for contoured ground. */
	private static final int OPCODE_CONTOURED_GROUND = 21;
	/** Opcode for non flat shading. */
	private static final int OPCODE_NON_FLAT_SHADING = 22;
	/** Opcode for model clipped. */
	private static final int OPCODE_MODEL_CLIPPED = 23;
	/** Opcode for animation. */
	private static final int OPCODE_ANIMATION = 24;
	/** Opcode for decor displacement. */
	private static final int OPCODE_DECOR_DISPLACEMENT = 28;
	/** Opcode for ambient. */
	private static final int OPCODE_AMBIENT = 29;
	/** Opcode for contrast. */
	private static final int OPCODE_CONTRAST = 39;
	/** Opcode for recolors. */
	private static final int OPCODE_RECOLORS = 40;
	/** Opcode for map function. */
	private static final int OPCODE_MAP_FUNCTION = 60;
	/** Opcode for rotated. */
	private static final int OPCODE_ROTATED = 62;
	/** Opcode for disable shadow. */
	private static final int OPCODE_DISABLE_SHADOW = 64;
	/** Opcode for scale x. */
	private static final int OPCODE_SCALE_X = 65;
	/** Opcode for scale y. */
	private static final int OPCODE_SCALE_Y = 66;
	/** Opcode for scale z. */
	private static final int OPCODE_SCALE_Z = 67;
	/** Opcode for map scene. */
	private static final int OPCODE_MAP_SCENE = 68;
	/** Opcode for surroundings. */
	private static final int OPCODE_SURROUNDINGS = 69;
	/** Opcode for translate x. */
	private static final int OPCODE_TRANSLATE_X = 70;
	/** Opcode for translate y. */
	private static final int OPCODE_TRANSLATE_Y = 71;
	/** Opcode for translate z. */
	private static final int OPCODE_TRANSLATE_Z = 72;
	/** Opcode for obstructs ground. */
	private static final int OPCODE_OBSTRUCTS_GROUND = 73;
	/** Opcode for hollow. */
	private static final int OPCODE_HOLLOW = 74;
	/** Opcode for support items. */
	private static final int OPCODE_SUPPORT_ITEMS = 75;
	/** Opcode for morphs. */
	private static final int OPCODE_MORPHS = 77;
	/** First opcode in the action range. */
	private static final int ACTION_OPCODE_FIRST = 30;
	/** Exclusive upper bound of the action opcode range. */
	private static final int ACTION_OPCODE_LIMIT = 39;
	/** Action count. */
	private static final int ACTION_COUNT = 5;


	/** Creates a new game object definition with its default client state. */
	public GameObjectDefinition() {
	}

	/** Stores offsets values. */
	private static int[] offsets;

	/** Tracks whether interactive. */
	public boolean interactive;

	/** Stores the current scale Y. */
	public int scaleY;

	/** Stores the current translate X. */
	public int translateX;

	/**
	 * Model cache.
	 *
	 */
	private static LruCache modelCache = new LruCache(40);

	/** Stores model IDs values. */
	public int[] modelIds;

	/** Stores the current surroundings. */
	public int surroundings;
	/** Tracks whether obstructs ground. */
	public boolean obstructsGround;

	/** Stores the current translate Z. */
	public int translateZ;

	/** Stores the current data buffer. */
	private static Buffer dataBuffer;
	/** Tracks whether contoured ground. */
	public boolean contouredGround;

	/** Stores the current client instance. */
	public static Client clientInstance;

	/** Stores model parts values. */
	private static final Model[] modelParts = new Model[4];
	/** Tracks whether low memory. */
	public static boolean lowMemory;

	/** Stores the current ID. */
	public int id = -1;

	/** Stores the current size Y. */
	public int sizeY;

	/** Stores the current name. */
	public String name = "null";

	/** Stores the current cache index. */
	private static int cacheIndex;

	/** Stores the current varbit ID. */
	public int varbitId;

	/**
	 * Raw model cache.
	 *
	 */
	private static LruCache rawModelCache = new LruCache(500);

	/** Stores the current scale X. */
	public int scaleX;

	/** Stores the current varp ID. */
	public int varpId;

	/** Stores cache values. */
	private static GameObjectDefinition[] cache;

	/** Stores description values. */
	public byte[] description;

	/** Stores the current ambient. */
	public byte ambient;

	/** Stores the current translate Y. */
	public int translateY;

	/** Stores the current contrast. */
	public byte contrast;

	/** Stores model types values. */
	public int[] modelTypes;

	/** Stores actions values. */
	public String[] actions;
	/** Tracks whether hollow. */
	public boolean hollow;

	/** Stores recolor to values. */
	public int[] recolorTo;

	/** Stores the current support items. */
	public int supportItems;

	/** Stores the current map scene ID. */
	public int mapSceneId;

	/** Stores the current scale Z. */
	public int scaleZ;
	/** Tracks whether model clipped. */
	public boolean modelClipped;
	/** Tracks whether rotated. */
	public boolean rotated;

	/** Stores recolor from values. */
	public int[] recolorFrom;

	/** Stores the current size X. */
	public int sizeX;

	/** Stores the current decor displacement. */
	public int decorDisplacement;

	/** Stores the current animation ID. */
	public int animationId;
	/** Tracks whether non flat shading. */
	public boolean nonFlatShading;

	/** Stores morph IDs values. */
	public int[] morphIds;

	/** Stores the current map function ID. */
	public int mapFunctionId;
	/** Tracks whether casts shadow. */
	public boolean castsShadow;

	/** Stores the current count. */
	public static int count;
	/** Tracks whether blocks projectiles. */
	public boolean blocksProjectiles;
	/** Tracks whether blocks movement. */
	public boolean blocksMovement;

	/**
	 * Loads the indexed location-definition archive.
	 *
	 * @param archive the archive
	 */
	public static void load(Archive archive) {
		dataBuffer = new Buffer(archive.read("loc.dat"));
		Buffer index = new Buffer(archive.read("loc.idx"));
		count = index.readUnsignedShort();
		offsets = new int[count];
		int offset = 2;
		for (int id = 0; id < count; id++) {
			offsets[id] = offset;
			offset += index.readUnsignedShort();
		}
		cache = new GameObjectDefinition[20];
		for (int cacheSlot = 0; cacheSlot < cache.length; cacheSlot++) {
			cache[cacheSlot] = new GameObjectDefinition();
		}
	}

	/**
	 * Looks up a definition through the original 20-entry rotating cache. The
	 * replacement index is incremented before use, so the first miss uses slot 1.
	 *
	 * @param id the id
	 * @return the  result
	 */
	public static GameObjectDefinition lookup(int id) {
		for (GameObjectDefinition definition : cache) {
			if (definition.id == id) {
				return definition;
			}
		}
		cacheIndex = (cacheIndex + 1) % cache.length;
		GameObjectDefinition definition = cache[cacheIndex];
		dataBuffer.position = offsets[id];
		definition.id = id;
		definition.reset();
		definition.decode(dataBuffer);
		return definition;
	}

	/** Releases definition/model caches and indexed archive state. */
	public static void clear() {
		rawModelCache = null;
		modelCache = null;
		offsets = null;
		cache = null;
		dataBuffer = null;
	}

	/**
	 * Clears both model LRUs while keeping the loaded definition index available.
	 */
	public static void clearModelCaches() {
		if (rawModelCache != null) {
			rawModelCache.clear();
		}
		if (modelCache != null) {
			modelCache.clear();
		}
	}

	/**
	 * Requests every source model referenced by this definition as a model
	 * resource.
	 *
	 * @param fetcher the fetcher
	 */
	public void requestModels(OnDemandFetcher fetcher) {
		if (modelIds == null) {
			return;
		}
		for (int modelId : modelIds) {
			fetcher.queueExtraRequest(OnDemandFetcher.MODEL, modelId & 0xffff);
		}
	}

	/**
	 * Returns whether all source models referenced by the definition are loaded.
	 * @return whether are all models ready
	 */
	public boolean areAllModelsReady() {
		if (modelIds == null) {
			return true;
		}
		boolean ready = true;
		for (int modelId : modelIds) {
			ready &= Model.isLoaded(modelId & 0xffff);
		}
		return ready;
	}

	/**
	 * Returns whether the model needed for a specific location type is loaded.
	 *
	 * @param type the type
	 * @return whether model ready
	 */
	public boolean isModelReady(int type) {
		if (modelTypes == null) {
			if (modelIds == null) {
				return true;
			}
			if (type != 10) {
				return true;
			}
			boolean ready = true;
			for (int modelId : modelIds) {
				ready &= Model.isLoaded(modelId & 0xffff);
			}
			return ready;
		}
		for (int modelTypeIndex = 0; modelTypeIndex < modelTypes.length; modelTypeIndex++) {
			if (modelTypes[modelTypeIndex] == type) {
				return Model.isLoaded(modelIds[modelTypeIndex] & 0xffff);
			}
		}
		return true;
	}

	/**
	 * Resolves the active morph using this definition's varbit first, otherwise its
	 * varp.
	 * @return the active morph definition, or {@code null} when no valid morph is selected
	 */
	public GameObjectDefinition transform() {
		int morphIndex = -1;
		if (varbitId != -1) {
			Varbit varbit = Varbit.definitions[varbitId];
			int mask = Client.bitMasks[varbit.mostSignificantBit - varbit.leastSignificantBit];
			morphIndex = clientInstance.getVarp(varbit.varpId) >> varbit.leastSignificantBit & mask;
		} else if (varpId != -1) {
			morphIndex = clientInstance.getVarp(varpId);
		}
		if (morphIndex < 0 || morphIndex >= morphIds.length || morphIds[morphIndex] == -1) {
			return null;
		}
		return lookup(morphIds[morphIndex]);
	}

	/**
	 * Builds a model for a placed location and optionally contours it to the four
	 * tile heights. Height order is south-west, south-east, north-east, north-west.
	 *
	 * @param type            the type
	 * @param orientation     the orientation
	 * @param southWestHeight the south west height
	 * @param southEastHeight the south east height
	 * @param northEastHeight the north east height
	 * @param northWestHeight the north west height
	 * @param frameId         the frame id
	 * @return the model at
	 */
	public Model getModelAt(int type, int orientation, int southWestHeight, int southEastHeight, int northEastHeight,
			int northWestHeight, int frameId) {
		Model model = getModel(type, orientation, frameId);
		if (model == null) {
			return null;
		}
		if (contouredGround || nonFlatShading) {
			model = new Model(model, contouredGround, nonFlatShading);
		}
		if (contouredGround) {
			int averageHeight = (southWestHeight + southEastHeight + northEastHeight + northWestHeight) / 4;
			for (int vertex = 0; vertex < model.vertexCount; vertex++) {
				int x = model.verticesX[vertex];
				int z = model.verticesZ[vertex];
				int southHeight = southWestHeight + ((southEastHeight - southWestHeight) * (x + 64)) / 128;
				int northHeight = northWestHeight + ((northEastHeight - northWestHeight) * (x + 64)) / 128;
				int interpolatedHeight = southHeight + ((northHeight - southHeight) * (z + 64)) / 128;
				model.verticesY[vertex] += interpolatedHeight - averageHeight;
			}
			model.normalise();
		}
		return model;
	}

	/**
	 * Returns model.
	 *
	 * @param type        the type
	 * @param orientation the orientation
	 * @param frameId     the frame id
	 * @return the model
	 */
	private Model getModel(int type, int orientation, int frameId) {
		Model baseModel = null;
		long cacheKey;
		if (modelTypes == null) {
			if (type != 10) {
				return null;
			}
			cacheKey = (long) ((id << 6) + orientation) + ((long) (frameId + 1) << 32);
			Model cached = (Model) modelCache.get(cacheKey);
			if (cached != null) {
				return cached;
			}
			if (modelIds == null) {
				return null;
			}
			boolean mirror = rotated ^ (orientation > 3);
			int modelCount = modelIds.length;
			for (int modelPartIndex = 0; modelPartIndex < modelCount; modelPartIndex++) {
				int modelId = modelIds[modelPartIndex];
				if (mirror) {
					modelId += 0x10000;
				}
				baseModel = (Model) rawModelCache.get(modelId);
				if (baseModel == null) {
					baseModel = Model.getModel(modelId & 0xffff);
					if (baseModel == null) {
						return null;
					}
					if (mirror) {
						baseModel.mirror();
					}
					rawModelCache.put(modelId, baseModel);
				}
				if (modelCount > 1) {
					modelParts[modelPartIndex] = baseModel;
				}
			}
			if (modelCount > 1) {
				baseModel = new Model(modelCount, modelParts);
			}
		} else {
			int modelIndex = -1;
			for (int modelTypeIndex = 0; modelTypeIndex < modelTypes.length; modelTypeIndex++) {
				if (modelTypes[modelTypeIndex] == type) {
					modelIndex = modelTypeIndex;
					break;
				}
			}
			if (modelIndex == -1) {
				return null;
			}
			cacheKey = (long) ((id << 6) + (modelIndex << 3) + orientation) + ((long) (frameId + 1) << 32);
			Model cached = (Model) modelCache.get(cacheKey);
			if (cached != null) {
				return cached;
			}
			int modelId = modelIds[modelIndex];
			boolean mirror = rotated ^ (orientation > 3);
			if (mirror) {
				modelId += 0x10000;
			}
			baseModel = (Model) rawModelCache.get(modelId);
			if (baseModel == null) {
				baseModel = Model.getModel(modelId & 0xffff);
				if (baseModel == null) {
					return null;
				}
				if (mirror) {
					baseModel.mirror();
				}
				rawModelCache.put(modelId, baseModel);
			}
		}

		boolean needsScale = scaleX != 128 || scaleY != 128 || scaleZ != 128;
		boolean needsTranslation = translateX != 0 || translateY != 0 || translateZ != 0;
		Model model = new Model(baseModel, orientation == 0 && frameId == -1 && !needsScale && !needsTranslation,
				recolorFrom == null, AnimationFrame.isNull(frameId));

		if (frameId != -1) {
			model.createBones();
			model.applyTransformation(frameId);
			model.triangleGroups = null;
			model.vertexGroups = null;
		}
		while (orientation-- > 0) {
			model.rotateY90Ccw();
		}
		if (recolorFrom != null) {
			for (int recolorIndex = 0; recolorIndex < recolorFrom.length; recolorIndex++) {
				model.recolor(recolorFrom[recolorIndex], recolorTo[recolorIndex]);
			}
		}
		if (needsScale) {
			model.scale(scaleX, scaleY, scaleZ);
		}
		if (needsTranslation) {
			model.translate(translateX, translateY, translateZ);
		}
		model.light(64 + ambient, 768 + contrast * 5, -50, -10, -50, !nonFlatShading);
		if (supportItems == 1) {
			model.itemDropHeight = model.modelHeight;
		}
		modelCache.put(cacheKey, model);
		return model;
	}

	/**
	 * Resets this object's mutable state.
	 */
	private void reset() {
		modelIds = null;
		modelTypes = null;
		name = "null";
		description = null;
		recolorFrom = null;
		recolorTo = null;
		sizeX = 1;
		sizeY = 1;
		blocksMovement = true;
		blocksProjectiles = true;
		interactive = false;
		contouredGround = false;
		nonFlatShading = false;
		modelClipped = false;
		animationId = -1;
		decorDisplacement = 16;
		ambient = 0;
		contrast = 0;
		actions = null;
		mapFunctionId = -1;
		mapSceneId = -1;
		rotated = false;
		castsShadow = true;
		scaleX = 128;
		scaleY = 128;
		scaleZ = 128;
		surroundings = 0;
		translateX = 0;
		translateY = 0;
		translateZ = 0;
		obstructsGround = false;
		hollow = false;
		supportItems = -1;
		varbitId = -1;
		varpId = -1;
		morphIds = null;
	}

	/**
	 * Decodes this object from the supplied data.
	 *
	 * @param buffer the buffer
	 */
	private void decode(Buffer buffer) {
		int explicitInteractive = -1;
		while (true) {
			int opcode = buffer.readUnsignedByte();
			if (opcode == OPCODE_END) {
				break;
			}
			if (opcode == OPCODE_TYPED_MODELS) {
				int length = buffer.readUnsignedByte();
				if (length > 0) {
					if (modelIds == null || lowMemory) {
						modelTypes = new int[length];
						modelIds = new int[length];
						for (int modelIndex = 0; modelIndex < length; modelIndex++) {
							modelIds[modelIndex] = buffer.readUnsignedShort();
							modelTypes[modelIndex] = buffer.readUnsignedByte();
						}
					} else {
						buffer.position += length * 3;
					}
				}
			} else if (opcode == OPCODE_NAME) {
				name = buffer.readString();
			} else if (opcode == OPCODE_DESCRIPTION) {
				description = buffer.readStringBytes();
			} else if (opcode == OPCODE_MODELS) {
				int length = buffer.readUnsignedByte();
				if (length > 0) {
					if (modelIds == null || lowMemory) {
						modelTypes = null;
						modelIds = new int[length];
						for (int modelIndex = 0; modelIndex < length; modelIndex++) {
							modelIds[modelIndex] = buffer.readUnsignedShort();
						}
					} else {
						buffer.position += length * 2;
					}
				}
			} else if (opcode == OPCODE_SIZE_X) {
				sizeX = buffer.readUnsignedByte();
			} else if (opcode == OPCODE_SIZE_Y) {
				sizeY = buffer.readUnsignedByte();
			} else if (opcode == OPCODE_ALLOW_WALKING) {
				blocksMovement = false;
			} else if (opcode == OPCODE_ALLOW_PROJECTILES) {
				blocksProjectiles = false;
			} else if (opcode == OPCODE_INTERACTIVE) {
				explicitInteractive = buffer.readUnsignedByte();
				if (explicitInteractive == 1) {
					interactive = true;
				}
			} else if (opcode == OPCODE_CONTOURED_GROUND) {
				contouredGround = true;
			} else if (opcode == OPCODE_NON_FLAT_SHADING) {
				nonFlatShading = true;
			} else if (opcode == OPCODE_MODEL_CLIPPED) {
				modelClipped = true;
			} else if (opcode == OPCODE_ANIMATION) {
				animationId = buffer.readUnsignedShort();
				if (animationId == DefinitionConstants.NULL_REFERENCE_ID) {
					animationId = -1;
				}
			} else if (opcode == OPCODE_DECOR_DISPLACEMENT) {
				decorDisplacement = buffer.readUnsignedByte();
			} else if (opcode == OPCODE_AMBIENT) {
				ambient = buffer.readSignedByte();
			} else if (opcode == OPCODE_CONTRAST) {
				contrast = buffer.readSignedByte();
			} else if (opcode >= ACTION_OPCODE_FIRST && opcode < ACTION_OPCODE_LIMIT) {
				if (actions == null) {
					actions = new String[ACTION_COUNT];
				}
				actions[opcode - ACTION_OPCODE_FIRST] = buffer.readString();
				if (actions[opcode - ACTION_OPCODE_FIRST].equalsIgnoreCase("hidden")) {
					actions[opcode - ACTION_OPCODE_FIRST] = null;
				}
			} else if (opcode == OPCODE_RECOLORS) {
				int length = buffer.readUnsignedByte();
				recolorFrom = new int[length];
				recolorTo = new int[length];
				for (int recolorIndex = 0; recolorIndex < length; recolorIndex++) {
					recolorFrom[recolorIndex] = buffer.readUnsignedShort();
					recolorTo[recolorIndex] = buffer.readUnsignedShort();
				}
			} else if (opcode == OPCODE_MAP_FUNCTION) {
				mapFunctionId = buffer.readUnsignedShort();
			} else if (opcode == OPCODE_ROTATED) {
				rotated = true;
			} else if (opcode == OPCODE_DISABLE_SHADOW) {
				castsShadow = false;
			} else if (opcode == OPCODE_SCALE_X) {
				scaleX = buffer.readUnsignedShort();
			} else if (opcode == OPCODE_SCALE_Y) {
				scaleY = buffer.readUnsignedShort();
			} else if (opcode == OPCODE_SCALE_Z) {
				scaleZ = buffer.readUnsignedShort();
			} else if (opcode == OPCODE_MAP_SCENE) {
				mapSceneId = buffer.readUnsignedShort();
			} else if (opcode == OPCODE_SURROUNDINGS) {
				surroundings = buffer.readUnsignedByte();
			} else if (opcode == OPCODE_TRANSLATE_X) {
				translateX = buffer.readSignedShort();
			} else if (opcode == OPCODE_TRANSLATE_Y) {
				translateY = buffer.readSignedShort();
			} else if (opcode == OPCODE_TRANSLATE_Z) {
				translateZ = buffer.readSignedShort();
			} else if (opcode == OPCODE_OBSTRUCTS_GROUND) {
				obstructsGround = true;
			} else if (opcode == OPCODE_HOLLOW) {
				hollow = true;
			} else if (opcode == OPCODE_SUPPORT_ITEMS) {
				supportItems = buffer.readUnsignedByte();
			} else if (opcode == OPCODE_MORPHS) {
				varbitId = buffer.readUnsignedShort();
				if (varbitId == DefinitionConstants.NULL_REFERENCE_ID) {
					varbitId = -1;
				}
				varpId = buffer.readUnsignedShort();
				if (varpId == DefinitionConstants.NULL_REFERENCE_ID) {
					varpId = -1;
				}
				int lastIndex = buffer.readUnsignedByte();
				morphIds = new int[lastIndex + 1];
				for (int morphIndex = 0; morphIndex <= lastIndex; morphIndex++) {
					morphIds[morphIndex] = buffer.readUnsignedShort();
					if (morphIds[morphIndex] == DefinitionConstants.NULL_REFERENCE_ID) {
						morphIds[morphIndex] = -1;
					}
				}
			}
		}

		if (explicitInteractive == -1) {
			interactive = false;
			if (modelIds != null && (modelTypes == null || modelTypes[0] == 10)) {
				interactive = true;
			}
			if (actions != null) {
				interactive = true;
			}
		}
		if (hollow) {
			blocksMovement = false;
			blocksProjectiles = false;
		}
		if (supportItems == -1) {
			supportItems = blocksMovement ? 1 : 0;
		}
	}
}
