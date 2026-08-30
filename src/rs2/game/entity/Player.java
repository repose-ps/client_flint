package rs2.game.entity;

import rs2.media.Angle;
import rs2.Client;
import rs2.cache.def.AnimationSequence;
import rs2.cache.def.IdentityKit;
import rs2.cache.def.ItemDefinition;
import rs2.cache.def.NpcDefinition;
import rs2.cache.def.SpotAnimation;
import rs2.collection.LruCache;
import rs2.media.AnimationFrame;
import rs2.media.model.Model;
import rs2.net.Buffer;
import rs2.net.ProtocolConstants;
import rs2.text.Base37;
import rs2.text.TextFormatter;


/**
 * Runtime player actor and revision-377 appearance/model builder.
 *
 * <p>
 * Appearance slots use the classic encoding: {@code 0} is empty,
 * {@code 256..511} select identity kits, and values {@code >= 512} select item
 * definitions. Slot zero may instead contain {@link ProtocolConstants#NULL_ID}, followed by an NPC
 * id, to transform the player into an NPC.
 * </p>
 */
public class Player extends Actor {

	/** Number of encoded player equipment/appearance slots. */
	private static final int EQUIPMENT_SLOT_COUNT = 12;
	/** Number of player body-colour selections. */
	private static final int BODY_COLOR_COUNT = 5;
	/** First appearance value that references an identity-kit definition. */
	private static final int IDENTITY_KIT_OFFSET = 256;
	/** First appearance value that references an item definition. */
	private static final int ITEM_OFFSET = 512;
	/** Equipment slot overridden by an animation weapon override. */
	private static final int WEAPON_SLOT = 3;
	/** Equipment slot overridden by an animation shield override. */
	private static final int SHIELD_SLOT = 5;
	/** Equipment slot swapped with the shield slot while hashing appearance. */
	private static final int APPEARANCE_HASH_SWAP_SLOT = 9;
	/** Bit position of the shield override contribution to the model-cache key. */
	private static final int SHIELD_OVERRIDE_HASH_SHIFT = 40;
	/** Bit position of the weapon override contribution to the model-cache key. */
	private static final int WEAPON_OVERRIDE_HASH_SHIFT = 48;
	/** Bits contributed by each encoded equipment value to the appearance hash. */
	private static final int EQUIPMENT_HASH_BITS = 4;
	/** Bits contributed by each body-colour selection to the appearance hash. */
	private static final int BODY_COLOR_HASH_BITS = 3;
	/** Bits contributed by gender to the appearance hash. */
	private static final int GENDER_HASH_BITS = 1;

	/** Creates a new player with its default client state. */
	public Player() {
	}

	/** Stores the current attached model X. */
	public int attachedModelX;

	/** Stores the current attached model height. */
	public int attachedModelHeight;

	/** Stores the current attached model Y. */
	public int attachedModelY;

	/** Stores the current attached model. */
	public Model attachedModel;

	/** Stores the current prayer icon. */
	public int prayerIcon = -1;

	/** Stores the current last model hash. */
	private long lastModelHash = -1L;

	/** Stores the current tile height. */
	public int tileHeight;

	/** Stores the current name. */
	public String name;

	/** Stores equipment values. */
	public final int[] equipment = new int[EQUIPMENT_SLOT_COUNT];

	/** Stores the current combat level. */
	public int combatLevel;

	/** Stores the current appearance hash. */
	private long appearanceHash;

	/** Stores the current gender. */
	public int gender;

	/** Stores the current skull icon. */
	public int skullIcon = -1;

	/** Stores the current NPC definition. */
	public NpcDefinition npcDefinition;
	/**
	 * Whether visible.
	 */
	public boolean visible;

	/** Stores the current skill level. */
	public int skillLevel;

	/** Stores body colors values. */
	public final int[] bodyColors = new int[BODY_COLOR_COUNT];

	/**
	 * Model cache.
	 *
	 */
	public static LruCache modelCache = new LruCache(260);
	/**
	 * When true, return the cached lit base model without applying actor/spot
	 * animations.
	 */
	public boolean isUnanimated;

	/** Stores the current attached model start cycle. */
	public int attachedModelStartCycle;

	/** Stores the current attached model end cycle. */
	public int attachedModelEndCycle;

	/** Stores the current team. */
	public int team;

	/** Stores the current attached model min X. */
	public int attachedModelMinX;

	/** Stores the current attached model min Y. */
	public int attachedModelMinY;

	/** Stores the current attached model max X. */
	public int attachedModelMaxX;

	/** Stores the current attached model max Y. */
	public int attachedModelMaxY;

	/**
	 * Builds the dialogue/head model for the current appearance.
	 * @return the head model
	 */
	public Model getHeadModel() {
		if (!visible) {
			return null;
		}
		if (npcDefinition != null) {
			return npcDefinition.getHeadModel();
		}

		for (int slot = 0; slot < EQUIPMENT_SLOT_COUNT; slot++) {
			int appearance = equipment[slot];
			if (appearance >= IDENTITY_KIT_OFFSET && appearance < ITEM_OFFSET
					&& !IdentityKit.definitions[appearance - IDENTITY_KIT_OFFSET].areHeadModelsReady()) {
				return null;
			}
			if (appearance >= ITEM_OFFSET && !ItemDefinition.lookup(appearance - ITEM_OFFSET).areHeadModelsReady(gender)) {
				return null;
			}
		}

		Model[] parts = new Model[EQUIPMENT_SLOT_COUNT];
		int partCount = 0;
		for (int slot = 0; slot < EQUIPMENT_SLOT_COUNT; slot++) {
			int appearance = equipment[slot];
			if (appearance >= IDENTITY_KIT_OFFSET && appearance < ITEM_OFFSET) {
				Model part = IdentityKit.definitions[appearance - IDENTITY_KIT_OFFSET].buildHeadModel();
				if (part != null) {
					parts[partCount++] = part;
				}
			}
			if (appearance >= ITEM_OFFSET) {
				Model part = ItemDefinition.lookup(appearance - ITEM_OFFSET).getHeadModel(gender);
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
	 * @return the base model
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
				cacheKey += (long) (shieldOverride - equipment[SHIELD_SLOT]) << SHIELD_OVERRIDE_HASH_SHIFT;
			}
			if (animation.weaponOverride >= 0) {
				weaponOverride = animation.weaponOverride;
				cacheKey += (long) (weaponOverride - equipment[WEAPON_SLOT]) << WEAPON_OVERRIDE_HASH_SHIFT;
			}
		} else if (movementSequence >= 0) {
			primaryFrameId = AnimationSequence.sequences[movementSequence].primaryFrameIds[movementFrame];
		}

		Model baseModel = (Model) modelCache.get(cacheKey);
		if (baseModel == null) {
			boolean missingModel = false;
			for (int slot = 0; slot < EQUIPMENT_SLOT_COUNT; slot++) {
				int appearance = equipment[slot];
				if (weaponOverride >= 0 && slot == WEAPON_SLOT) {
					appearance = weaponOverride;
				}
				if (shieldOverride >= 0 && slot == SHIELD_SLOT) {
					appearance = shieldOverride;
				}
				if (appearance >= IDENTITY_KIT_OFFSET && appearance < ITEM_OFFSET
						&& !IdentityKit.definitions[appearance - IDENTITY_KIT_OFFSET].areBodyModelsReady()) {
					missingModel = true;
				}
				if (appearance >= ITEM_OFFSET && !ItemDefinition.lookup(appearance - ITEM_OFFSET).areWearableModelsReady(gender)) {
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
			Model[] parts = new Model[EQUIPMENT_SLOT_COUNT];
			int partCount = 0;
			for (int slot = 0; slot < EQUIPMENT_SLOT_COUNT; slot++) {
				int appearance = equipment[slot];
				if (weaponOverride >= 0 && slot == WEAPON_SLOT) {
					appearance = weaponOverride;
				}
				if (shieldOverride >= 0 && slot == SHIELD_SLOT) {
					appearance = shieldOverride;
				}
				if (appearance >= IDENTITY_KIT_OFFSET && appearance < ITEM_OFFSET) {
					Model part = IdentityKit.definitions[appearance - IDENTITY_KIT_OFFSET].buildBodyModel();
					if (part != null) {
						parts[partCount++] = part;
					}
				}
				if (appearance >= ITEM_OFFSET) {
					Model part = ItemDefinition.lookup(appearance - ITEM_OFFSET).getWearableModel(gender);
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
				if (orientation == Angle.QUARTER_TURN) {
					temporaryModel.rotateY90Ccw();
					temporaryModel.rotateY90Ccw();
					temporaryModel.rotateY90Ccw();
				} else if (orientation == Angle.HALF_TURN) {
					temporaryModel.rotateY90Ccw();
					temporaryModel.rotateY90Ccw();
				} else if (orientation == Angle.THREE_QUARTER_TURN) {
					temporaryModel.rotateY90Ccw();
				}

				model = new Model(new Model[] { model, temporaryModel }, 2);

				if (orientation == Angle.QUARTER_TURN) {
					temporaryModel.rotateY90Ccw();
				} else if (orientation == Angle.HALF_TURN) {
					temporaryModel.rotateY90Ccw();
					temporaryModel.rotateY90Ccw();
				} else if (orientation == Angle.THREE_QUARTER_TURN) {
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
	 * @return {@code true} when visible; otherwise {@code false}
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

		for (int slot = 0; slot < EQUIPMENT_SLOT_COUNT; slot++) {
			int high = buffer.readUnsignedByte();
			if (high == 0) {
				equipment[slot] = 0;
				continue;
			}
			int low = buffer.readUnsignedByte();
			equipment[slot] = (high << 8) + low;
			if (slot == 0 && equipment[0] == ProtocolConstants.NULL_ID) {
				npcDefinition = NpcDefinition.lookup(buffer.readUnsignedShort());
				break;
			}
			if (equipment[slot] >= ITEM_OFFSET && equipment[slot] - ITEM_OFFSET < ItemDefinition.count) {
				int itemTeam = ItemDefinition.lookup(equipment[slot] - ITEM_OFFSET).team;
				if (itemTeam != 0) {
					team = itemTeam;
				}
			}
		}

		for (int index = 0; index < BODY_COLOR_COUNT; index++) {
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
		int shieldSlotAppearance = equipment[SHIELD_SLOT];
		int swapSlotAppearance = equipment[APPEARANCE_HASH_SWAP_SLOT];
		equipment[SHIELD_SLOT] = swapSlotAppearance;
		equipment[APPEARANCE_HASH_SWAP_SLOT] = shieldSlotAppearance;

		appearanceHash = 0L;
		for (int slot = 0; slot < EQUIPMENT_SLOT_COUNT; slot++) {
			appearanceHash <<= EQUIPMENT_HASH_BITS;
			if (equipment[slot] >= IDENTITY_KIT_OFFSET) {
				appearanceHash += equipment[slot] - IDENTITY_KIT_OFFSET;
			}
		}
		if (equipment[0] >= IDENTITY_KIT_OFFSET) {
			appearanceHash += equipment[0] - IDENTITY_KIT_OFFSET >> 4;
		}
		if (equipment[1] >= IDENTITY_KIT_OFFSET) {
			appearanceHash += equipment[1] - IDENTITY_KIT_OFFSET >> 8;
		}

		equipment[SHIELD_SLOT] = shieldSlotAppearance;
		equipment[APPEARANCE_HASH_SWAP_SLOT] = swapSlotAppearance;
		for (int index = 0; index < BODY_COLOR_COUNT; index++) {
			appearanceHash <<= BODY_COLOR_HASH_BITS;
			appearanceHash += bodyColors[index];
		}
		appearanceHash <<= GENDER_HASH_BITS;
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
		return sequence == ProtocolConstants.NULL_ID ? -1 : sequence;
	}

	/**
	 * Performs recolor appearance.
	 *
	 * @param model the model
	 */
	private void recolorAppearance(Model model) {
		for (int index = 0; index < BODY_COLOR_COUNT; index++) {
			if (bodyColors[index] != 0) {
				model.recolor(Client.bodyColorPalettes[index][0], Client.bodyColorPalettes[index][bodyColors[index]]);
				if (index == 1) {
					model.recolor(Client.skinColorPalette[0], Client.skinColorPalette[bodyColors[index]]);
				}
			}
		}
	}

}
