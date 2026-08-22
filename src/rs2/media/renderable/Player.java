package rs2.media.renderable;

import rs2.Client;
import rs2.cache.def.ItemDefinition;
import rs2.cache.def.NpcDefinition;
import rs2.cache.media.AnimationSequence;
import rs2.cache.media.IdentityKit;
import rs2.cache.media.SpotAnimation;
import rs2.collection.LruCache;
import rs2.media.AnimationFrame;
import rs2.net.Buffer;
import rs2.text.Base37;
import rs2.text.TextFormatter;

/**
 * Runtime player actor and revision-377 appearance/model builder.
 *
 * <p>
 * Appearance slots use the classic encoding: {@code 0} is empty,
 * {@code 256..511} select identity kits, and values {@code >= 512} select item
 * definitions. Slot zero may instead contain {@code 65535}, followed by an NPC
 * id, to transform the player into an NPC.
 * </p>
 */
public /**
		 * Initializes this instance.
		 */
class Player extends Actor {

	/**
	 * Stores attached model x.
	 */
	public int attachedModelX;
	/**
	 * Stores attached model height.
	 */
	public int attachedModelHeight;
	/**
	 * Stores attached model y.
	 */
	public int attachedModelY;
	/**
	 * Stores attached model.
	 */
	public Model attachedModel;
	/**
	 * Stores prayer icon.
	 */
	public int prayerIcon = -1;
	/**
	 * Stores last model hash.
	 */
	private long lastModelHash = -1L;
	/**
	 * Stores tile height.
	 */
	public int tileHeight;
	/**
	 * Stores name.
	 */
	public String name;
	/**
	 * Stores equipment.
	 */
	public final int[] equipment = new int[12];
	/**
	 * Stores combat level.
	 */
	public int combatLevel;
	/**
	 * Stores appearance hash.
	 */
	private long appearanceHash;
	/**
	 * Stores gender.
	 */
	public int gender;
	/**
	 * Stores skull icon.
	 */
	public int skullIcon = -1;
	/**
	 * Stores npc definition.
	 */
	public NpcDefinition npcDefinition;
	/**
	 * Whether visible.
	 */
	public boolean visible;
	/**
	 * Stores skill level.
	 */
	public int skillLevel;
	/**
	 * Stores body colors.
	 */
	public final int[] bodyColors = new int[5];
	/**
	 * Stores model cache.
	 */
	public static LruCache modelCache = new LruCache(260);
	/**
	 * When true, return the cached lit base model without applying actor/spot
	 * animations.
	 */
	public boolean isUnanimated;
	/**
	 * Stores attached model start cycle.
	 */
	public int attachedModelStartCycle;
	/**
	 * Stores attached model end cycle.
	 */
	public int attachedModelEndCycle;
	/**
	 * Stores team.
	 */
	public int team;
	/**
	 * Stores attached model min x.
	 */
	public int attachedModelMinX;
	/**
	 * Stores attached model min y.
	 */
	public int attachedModelMinY;
	/**
	 * Stores attached model max x.
	 */
	public int attachedModelMaxX;
	/**
	 * Stores attached model max y.
	 */
	public int attachedModelMaxY;

	/**
	 * Builds the dialogue/head model for the current appearance.
	 */
	public Model getHeadModel() {
		if (!visible) {
			return null;
		}
		if (npcDefinition != null) {
			return npcDefinition.getHeadModel();
		}

		for (int slot = 0; slot < 12; slot++) {
			int appearance = equipment[slot];
			if (appearance >= 256 && appearance < 512
					&& !IdentityKit.definitions[appearance - 256].areHeadModelsReady()) {
				return null;
			}
			if (appearance >= 512 && !ItemDefinition.lookup(appearance - 512).areHeadModelsReady(gender)) {
				return null;
			}
		}

		Model[] parts = new Model[12];
		int partCount = 0;
		for (int slot = 0; slot < 12; slot++) {
			int appearance = equipment[slot];
			if (appearance >= 256 && appearance < 512) {
				Model part = IdentityKit.definitions[appearance - 256].buildHeadModel();
				if (part != null) {
					parts[partCount++] = part;
				}
			}
			if (appearance >= 512) {
				Model part = ItemDefinition.lookup(appearance - 512).getHeadModel(gender);
				if (part != null) {
					parts[partCount++] = part;
				}
			}
		}

		Model model = new Model(partCount, parts);
		recolorAppearance(model);
		return model;
	}

	/**
	 * Builds the cached body model and applies the currently selected
	 * movement/action frames.
	 */
	public Model getBaseModel() {
		if (npcDefinition != null) {
			int primaryFrameId = -1;
			if (sequence >= 0 && sequenceDelay == 0) {
				primaryFrameId = AnimationSequence.sequences[sequence].primaryFrameIds[sequenceFrame];
			} else if (movementSequence >= 0) {
				primaryFrameId = AnimationSequence.sequences[movementSequence].primaryFrameIds[movementFrame];
			}
			return npcDefinition.getAnimatedModel(primaryFrameId, -1, null);
		}

		long cacheKey = appearanceHash;
		int primaryFrameId = -1;
		int secondaryFrameId = -1;
		int shieldOverride = -1;
		int weaponOverride = -1;
		if (sequence >= 0 && sequenceDelay == 0) {
			AnimationSequence animation = AnimationSequence.sequences[sequence];
			primaryFrameId = animation.primaryFrameIds[sequenceFrame];
			if (movementSequence >= 0 && movementSequence != idleSequence) {
				secondaryFrameId = AnimationSequence.sequences[movementSequence].primaryFrameIds[movementFrame];
			}
			if (animation.shieldOverride >= 0) {
				shieldOverride = animation.shieldOverride;
				cacheKey += (long) (shieldOverride - equipment[5]) << 40;
			}
			if (animation.weaponOverride >= 0) {
				weaponOverride = animation.weaponOverride;
				cacheKey += (long) (weaponOverride - equipment[3]) << 48;
			}
		} else if (movementSequence >= 0) {
			primaryFrameId = AnimationSequence.sequences[movementSequence].primaryFrameIds[movementFrame];
		}

		Model baseModel = (Model) modelCache.get(cacheKey);
		if (baseModel == null) {
			boolean missingModel = false;
			for (int slot = 0; slot < 12; slot++) {
				int appearance = equipment[slot];
				if (weaponOverride >= 0 && slot == 3) {
					appearance = weaponOverride;
				}
				if (shieldOverride >= 0 && slot == 5) {
					appearance = shieldOverride;
				}
				if (appearance >= 256 && appearance < 512
						&& !IdentityKit.definitions[appearance - 256].areBodyModelsReady()) {
					missingModel = true;
				}
				if (appearance >= 512 && !ItemDefinition.lookup(appearance - 512).areWearableModelsReady(gender)) {
					missingModel = true;
				}
			}

			if (missingModel) {
				if (lastModelHash != -1L) {
					baseModel = (Model) modelCache.get(lastModelHash);
				}
				if (baseModel == null) {
					return null;
				}
			}
		}

		if (baseModel == null) {
			Model[] parts = new Model[12];
			int partCount = 0;
			for (int slot = 0; slot < 12; slot++) {
				int appearance = equipment[slot];
				if (weaponOverride >= 0 && slot == 3) {
					appearance = weaponOverride;
				}
				if (shieldOverride >= 0 && slot == 5) {
					appearance = shieldOverride;
				}
				if (appearance >= 256 && appearance < 512) {
					Model part = IdentityKit.definitions[appearance - 256].buildBodyModel();
					if (part != null) {
						parts[partCount++] = part;
					}
				}
				if (appearance >= 512) {
					Model part = ItemDefinition.lookup(appearance - 512).getWearableModel(gender);
					if (part != null) {
						parts[partCount++] = part;
					}
				}
			}

			baseModel = new Model(partCount, parts);
			recolorAppearance(baseModel);
			baseModel.createBones();
			baseModel.light(64, 850, -30, -50, -30, true);
			modelCache.put(cacheKey, baseModel);
			lastModelHash = cacheKey;
		}

		if (isUnanimated) {
			return baseModel;
		}

		Model model = Model.sharedModel;
		model.replaceWithModel(baseModel,
				AnimationFrame.isNull(primaryFrameId) & AnimationFrame.isNull(secondaryFrameId));
		if (primaryFrameId != -1 && secondaryFrameId != -1) {
			model.mixAnimationFrames(primaryFrameId, secondaryFrameId,
					AnimationSequence.sequences[sequence].interleaveOrder);
		} else if (primaryFrameId != -1) {
			model.applyTransformation(primaryFrameId);
		}
		model.calculateDiagonals();
		model.triangleGroups = null;
		model.vertexGroups = null;
		return model;
	}

	/**
	 * Returns model.
	 * 
	 * @return the resulting model
	 */
	@Override
	protected Model getModel() {
		if (!visible) {
			return null;
		}
		Model model = getBaseModel();
		if (model == null) {
			return null;
		}
		height = model.modelHeight;
		model.singleTile = true;
		if (isUnanimated) {
			return model;
		}

		if (spotAnimation != -1 && spotAnimationFrame != -1) {
			SpotAnimation graphic = SpotAnimation.definitions[spotAnimation];
			Model spotModel = graphic.getModel();
			if (spotModel != null) {
				int frameId = graphic.sequence.primaryFrameIds[spotAnimationFrame];
				Model animatedSpotModel = new Model(spotModel, false, true, AnimationFrame.isNull(spotAnimationFrame));
				animatedSpotModel.translate(0, -spotAnimationHeight, 0);
				animatedSpotModel.createBones();
				animatedSpotModel.applyTransformation(frameId);
				animatedSpotModel.triangleGroups = null;
				animatedSpotModel.vertexGroups = null;
				if (graphic.resizeXY != 128 || graphic.resizeZ != 128) {
					animatedSpotModel.scale(graphic.resizeXY, graphic.resizeZ, graphic.resizeXY);
				}
				animatedSpotModel.light(64 + graphic.ambient, 850 + graphic.contrast, -30, -50, -30, true);
				model = new Model(new Model[] { model, animatedSpotModel }, 2);
			}
		}

		if (attachedModel != null) {
			if (Client.gameCycle >= attachedModelEndCycle) {
				attachedModel = null;
			}
			if (Client.gameCycle >= attachedModelStartCycle && Client.gameCycle < attachedModelEndCycle) {
				Model temporaryModel = attachedModel;
				temporaryModel.translate(attachedModelX - x, attachedModelHeight - tileHeight, attachedModelY - y);
				if (orientation == 512) {
					temporaryModel.rotateY90Ccw();
					temporaryModel.rotateY90Ccw();
					temporaryModel.rotateY90Ccw();
				} else if (orientation == 1024) {
					temporaryModel.rotateY90Ccw();
					temporaryModel.rotateY90Ccw();
				} else if (orientation == 1536) {
					temporaryModel.rotateY90Ccw();
				}

				model = new Model(new Model[] { model, temporaryModel }, 2);

				if (orientation == 512) {
					temporaryModel.rotateY90Ccw();
				} else if (orientation == 1024) {
					temporaryModel.rotateY90Ccw();
					temporaryModel.rotateY90Ccw();
				} else if (orientation == 1536) {
					temporaryModel.rotateY90Ccw();
					temporaryModel.rotateY90Ccw();
					temporaryModel.rotateY90Ccw();
				}
				temporaryModel.translate(x - attachedModelX, tileHeight - attachedModelHeight, y - attachedModelY);
			}
		}

		model.singleTile = true;
		return model;
	}

	/**
	 * Returns whether visible.
	 * 
	 * @return the resulting boolean
	 */
	@Override
	public boolean isVisible() {
		return visible;
	}

	/**
	 * Decodes the revision-377 player appearance block and recalculates its
	 * model-cache hash.
	 * 
	 * @param buffer the buffer
	 */
	public void updateAppearance(Buffer buffer) {
		buffer.position = 0;
		gender = buffer.readUnsignedByte();
		skullIcon = buffer.readSignedByte();
		prayerIcon = buffer.readSignedByte();
		npcDefinition = null;
		team = 0;

		for (int slot = 0; slot < 12; slot++) {
			int high = buffer.readUnsignedByte();
			if (high == 0) {
				equipment[slot] = 0;
				continue;
			}
			int low = buffer.readUnsignedByte();
			equipment[slot] = (high << 8) + low;
			if (slot == 0 && equipment[0] == 65535) {
				npcDefinition = NpcDefinition.lookup(buffer.readUnsignedShort());
				break;
			}
			if (equipment[slot] >= 512 && equipment[slot] - 512 < ItemDefinition.count) {
				int itemTeam = ItemDefinition.lookup(equipment[slot] - 512).team;
				if (itemTeam != 0) {
					team = itemTeam;
				}
			}
		}

		for (int index = 0; index < 5; index++) {
			int color = buffer.readUnsignedByte();
			if (color < 0 || color >= Client.bodyColorPalettes[index].length) {
				color = 0;
			}
			bodyColors[index] = color;
		}

		idleSequence = readSequence(buffer);
		turnSequence = readSequence(buffer);
		walkSequence = readSequence(buffer);
		walkBackSequence = readSequence(buffer);
		walkRightSequence = readSequence(buffer);
		walkLeftSequence = readSequence(buffer);
		runSequence = readSequence(buffer);
		name = TextFormatter.formatDisplayName(Base37.decode(buffer.readLong()));
		combatLevel = buffer.readUnsignedByte();
		skillLevel = buffer.readUnsignedShort();
		visible = true;

		// Revision-377 swaps slots 5 and 9 only while calculating this hash. The
		// appearance
		// array itself is restored before returning.
		int slot5 = equipment[5];
		int slot9 = equipment[9];
		equipment[5] = slot9;
		equipment[9] = slot5;

		appearanceHash = 0L;
		for (int slot = 0; slot < 12; slot++) {
			appearanceHash <<= 4;
			if (equipment[slot] >= 256) {
				appearanceHash += equipment[slot] - 256;
			}
		}
		if (equipment[0] >= 256) {
			appearanceHash += equipment[0] - 256 >> 4;
		}
		if (equipment[1] >= 256) {
			appearanceHash += equipment[1] - 256 >> 8;
		}

		equipment[5] = slot5;
		equipment[9] = slot9;
		for (int index = 0; index < 5; index++) {
			appearanceHash <<= 3;
			appearanceHash += bodyColors[index];
		}
		appearanceHash <<= 1;
		appearanceHash += gender;
	}

	/**
	 * Reads sequence.
	 * 
	 * @return the resulting int
	 * @param buffer the buffer
	 */
	private static int readSequence(Buffer buffer) {
		int sequence = buffer.readUnsignedShort();
		return sequence == 65535 ? -1 : sequence;
	}

	/**
	 * Performs recolor appearance.
	 * 
	 * @param model the model
	 */
	private void recolorAppearance(Model model) {
		for (int index = 0; index < 5; index++) {
			if (bodyColors[index] != 0) {
				model.recolor(Client.bodyColorPalettes[index][0], Client.bodyColorPalettes[index][bodyColors[index]]);
				if (index == 1) {
					model.recolor(Client.skinColorPalette[0], Client.skinColorPalette[bodyColors[index]]);
				}
			}
		}
	}

}