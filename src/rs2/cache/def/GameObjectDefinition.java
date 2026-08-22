package rs2.cache.def;

import rs2.client;
import rs2.cache.Archive;
import rs2.cache.cfg.Varbit;
import rs2.cache.ondemand.OnDemandFetcher;
import rs2.collection.LruCache;
import rs2.media.AnimationFrame;
import rs2.media.renderable.Model;
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

	private static int[] offsets;

	public boolean interactive;
	public int scaleY;
	public int translateX;
	private static LruCache modelCache = new LruCache(40);
	public int[] modelIds;
	public int surroundings;
	public boolean obstructsGround;
	public int translateZ;
	private static Buffer dataBuffer;
	public boolean contouredGround;
	public static client clientInstance;
	private static final Model[] modelParts = new Model[4];
	public static boolean lowMemory;
	public int id = -1;
	public int sizeY;
	public String name = "null";
	private static int cacheIndex;
	public int varbitId;
	private static LruCache rawModelCache = new LruCache(500);
	public int scaleX;
	public int varpId;
	private static GameObjectDefinition[] cache;
	public byte[] description;
	public byte ambient;
	public int translateY;
	public byte contrast;
	public int[] modelTypes;
	public String[] actions;
	public boolean hollow;
	public int[] recolorTo;
	public int supportItems;
	public int mapSceneId;
	public int scaleZ;
	public boolean modelClipped;
	public boolean rotated;
	public int[] recolorFrom;
	public int sizeX;
	public int decorDisplacement;
	public int animationId;
	public boolean nonFlatShading;
	public int[] morphIds;
	public int mapFunctionId;
	public boolean castsShadow;
	public static int count;
	public boolean blocksProjectiles;
	public boolean blocksMovement;

	/** Loads the indexed location-definition archive. */
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
		for (int i = 0; i < cache.length; i++) {
			cache[i] = new GameObjectDefinition();
		}
	}

	/**
	 * Looks up a definition through the original 20-entry rotating cache. The
	 * replacement index is incremented before use, so the first miss uses slot 1.
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

	/** Returns whether the model needed for a specific location type is loaded. */
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
		for (int i = 0; i < modelTypes.length; i++) {
			if (modelTypes[i] == type) {
				return Model.isLoaded(modelIds[i] & 0xffff);
			}
		}
		return true;
	}

	/**
	 * Resolves the active morph using this definition's varbit first, otherwise its
	 * varp.
	 */
	public GameObjectDefinition transform() {
		int morphIndex = -1;
		if (varbitId != -1) {
			Varbit varbit = Varbit.definitions[varbitId];
			int mask = client.anIntArray1214[varbit.mostSignificantBit - varbit.leastSignificantBit];
			morphIndex = clientInstance.anIntArray1039[varbit.varpId] >> varbit.leastSignificantBit & mask;
		} else if (varpId != -1) {
			morphIndex = clientInstance.anIntArray1039[varpId];
		}
		if (morphIndex < 0 || morphIndex >= morphIds.length || morphIds[morphIndex] == -1) {
			return null;
		}
		return lookup(morphIds[morphIndex]);
	}

	/**
	 * Builds a model for a placed location and optionally contours it to the four
	 * tile heights. Height order is south-west, south-east, north-east, north-west.
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
			for (int i = 0; i < modelCount; i++) {
				int modelId = modelIds[i];
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
					modelParts[i] = baseModel;
				}
			}
			if (modelCount > 1) {
				baseModel = new Model(modelCount, modelParts);
			}
		} else {
			int modelIndex = -1;
			for (int i = 0; i < modelTypes.length; i++) {
				if (modelTypes[i] == type) {
					modelIndex = i;
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
			for (int i = 0; i < recolorFrom.length; i++) {
				model.recolor(recolorFrom[i], recolorTo[i]);
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

	private void decode(Buffer buffer) {
		int explicitInteractive = -1;
		while (true) {
			int opcode = buffer.readUnsignedByte();
			if (opcode == 0) {
				break;
			}
			if (opcode == 1) {
				int length = buffer.readUnsignedByte();
				if (length > 0) {
					if (modelIds == null || lowMemory) {
						modelTypes = new int[length];
						modelIds = new int[length];
						for (int i = 0; i < length; i++) {
							modelIds[i] = buffer.readUnsignedShort();
							modelTypes[i] = buffer.readUnsignedByte();
						}
					} else {
						buffer.position += length * 3;
					}
				}
			} else if (opcode == 2) {
				name = buffer.readString();
			} else if (opcode == 3) {
				description = buffer.readStringBytes();
			} else if (opcode == 5) {
				int length = buffer.readUnsignedByte();
				if (length > 0) {
					if (modelIds == null || lowMemory) {
						modelTypes = null;
						modelIds = new int[length];
						for (int i = 0; i < length; i++) {
							modelIds[i] = buffer.readUnsignedShort();
						}
					} else {
						buffer.position += length * 2;
					}
				}
			} else if (opcode == 14) {
				sizeX = buffer.readUnsignedByte();
			} else if (opcode == 15) {
				sizeY = buffer.readUnsignedByte();
			} else if (opcode == 17) {
				blocksMovement = false;
			} else if (opcode == 18) {
				blocksProjectiles = false;
			} else if (opcode == 19) {
				explicitInteractive = buffer.readUnsignedByte();
				if (explicitInteractive == 1) {
					interactive = true;
				}
			} else if (opcode == 21) {
				contouredGround = true;
			} else if (opcode == 22) {
				nonFlatShading = true;
			} else if (opcode == 23) {
				modelClipped = true;
			} else if (opcode == 24) {
				animationId = buffer.readUnsignedShort();
				if (animationId == 65535) {
					animationId = -1;
				}
			} else if (opcode == 28) {
				decorDisplacement = buffer.readUnsignedByte();
			} else if (opcode == 29) {
				ambient = buffer.readSignedByte();
			} else if (opcode == 39) {
				contrast = buffer.readSignedByte();
			} else if (opcode >= 30 && opcode < 39) {
				if (actions == null) {
					actions = new String[5];
				}
				actions[opcode - 30] = buffer.readString();
				if (actions[opcode - 30].equalsIgnoreCase("hidden")) {
					actions[opcode - 30] = null;
				}
			} else if (opcode == 40) {
				int length = buffer.readUnsignedByte();
				recolorFrom = new int[length];
				recolorTo = new int[length];
				for (int i = 0; i < length; i++) {
					recolorFrom[i] = buffer.readUnsignedShort();
					recolorTo[i] = buffer.readUnsignedShort();
				}
			} else if (opcode == 60) {
				mapFunctionId = buffer.readUnsignedShort();
			} else if (opcode == 62) {
				rotated = true;
			} else if (opcode == 64) {
				castsShadow = false;
			} else if (opcode == 65) {
				scaleX = buffer.readUnsignedShort();
			} else if (opcode == 66) {
				scaleY = buffer.readUnsignedShort();
			} else if (opcode == 67) {
				scaleZ = buffer.readUnsignedShort();
			} else if (opcode == 68) {
				mapSceneId = buffer.readUnsignedShort();
			} else if (opcode == 69) {
				surroundings = buffer.readUnsignedByte();
			} else if (opcode == 70) {
				translateX = buffer.readSignedShort();
			} else if (opcode == 71) {
				translateY = buffer.readSignedShort();
			} else if (opcode == 72) {
				translateZ = buffer.readSignedShort();
			} else if (opcode == 73) {
				obstructsGround = true;
			} else if (opcode == 74) {
				hollow = true;
			} else if (opcode == 75) {
				supportItems = buffer.readUnsignedByte();
			} else if (opcode == 77) {
				varbitId = buffer.readUnsignedShort();
				if (varbitId == 65535) {
					varbitId = -1;
				}
				varpId = buffer.readUnsignedShort();
				if (varpId == 65535) {
					varpId = -1;
				}
				int lastIndex = buffer.readUnsignedByte();
				morphIds = new int[lastIndex + 1];
				for (int i = 0; i <= lastIndex; i++) {
					morphIds[i] = buffer.readUnsignedShort();
					if (morphIds[i] == 65535) {
						morphIds[i] = -1;
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
