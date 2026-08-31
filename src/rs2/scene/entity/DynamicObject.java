
package rs2.scene.entity;

import java.util.function.IntSupplier;

import rs2.cache.cfg.BitMasks;
import rs2.cache.cfg.Varbit;
import rs2.cache.cfg.VarpProvider;
import rs2.cache.def.AnimationSequence;
import rs2.cache.def.GameObjectDefinition;
import rs2.media.model.Model;
import rs2.media.model.Renderable;


/**
 * Scene location whose model may animate and/or morph according to a varbit or
 * varp.
 *
 * <p>
 * The four height values are passed through to the location definition in the
 * original south-west, south-east, north-east, north-west corner order.
 * </p>
 */
public class DynamicObject extends Renderable {

	/** Stores the current south west height. */
	public int southWestHeight;

	/** Stores the current south east height. */
	public int southEastHeight;

	/** Stores the current north east height. */
	public int northEastHeight;

	/** Stores the current north west height. */
	public int northWestHeight;
	/**
	 * Identifier for object.
	 */
	public int objectId;

	/** Stores the current type. */
	public int type;

	/** Stores the current orientation. */
	public int orientation;

	/** Current varp values used by location morphing. */
	private final VarpProvider varpProvider;

	/** Current client cycle used by animation timing. */
	private final IntSupplier gameCycleProvider;

	/** Stores the current sequence. */
	public AnimationSequence sequence;
	/**
	 * Identifier for varbit.
	 */
	public int varbitId;
	/**
	 * Identifier for varp.
	 */
	public int varpId;

	/** Stores morph IDs values. */
	public int[] morphIds;

	/** Stores the current animation cycle start. */
	public int animationCycleStart;

	/** Stores the current frame. */
	public int frame;

	/**
	 * Creates a new dynamic object.
	 *
	 * @param objectId the object ID
	 * @param type the type
	 * @param orientation the orientation
	 * @param southWestHeight the south west height
	 * @param southEastHeight the south east height
	 * @param northEastHeight the north east height
	 * @param northWestHeight the north west height
	 * @param animationId the animation ID
	 * @param randomizeAnimation the randomize animation
	 * @param varpProvider current varp source used by morphing locations
	 * @param gameCycleProvider current client-cycle source used by animations
	 */
	DynamicObject(int objectId, int type, int orientation, int southWestHeight, int southEastHeight,
			int northEastHeight, int northWestHeight, int animationId, boolean randomizeAnimation,
			VarpProvider varpProvider, IntSupplier gameCycleProvider) {
		this.objectId = objectId;
		this.type = type;
		this.orientation = orientation;
		this.southWestHeight = southWestHeight;
		this.southEastHeight = southEastHeight;
		this.northEastHeight = northEastHeight;
		this.northWestHeight = northWestHeight;
		this.varpProvider = varpProvider;
		this.gameCycleProvider = gameCycleProvider;

		if (animationId != -1) {
			sequence = AnimationSequence.sequences[animationId];
			frame = 0;
			animationCycleStart = gameCycleProvider.getAsInt() - 1;
			if (randomizeAnimation && sequence.frameStep != -1) {
				frame = (int) (Math.random() * sequence.frameCount);
				animationCycleStart -= (int) (Math.random() * sequence.getFrameLength(frame));
			}
		}

		GameObjectDefinition definition = GameObjectDefinition.lookup(objectId);
		varbitId = definition.varbitId;
		varpId = definition.varpId;
		morphIds = definition.morphIds;
	}

	/**
	 * Performs resolve definition.
	 *
	 * @return the resulting game object definition
	 */
	private GameObjectDefinition resolveDefinition() {
		int morphIndex = -1;
		if (varbitId != -1) {
			Varbit varbit = Varbit.definitions[varbitId];
			int mask = BitMasks.get(varbit.mostSignificantBit - varbit.leastSignificantBit);
			morphIndex = varpProvider.getVarp(varbit.varpId) >> varbit.leastSignificantBit & mask;
		} else if (varpId != -1) {
			morphIndex = varpProvider.getVarp(varpId);
		}

		if (morphIndex < 0 || morphIndex >= morphIds.length || morphIds[morphIndex] == -1) {
			return null;
		}
		return GameObjectDefinition.lookup(morphIds[morphIndex]);
	}

	/**
	 * Returns model.
	 *
	 * @return the resulting model
	 */
	@Override
	protected Model getModel() {
		int frameId = -1;
		if (sequence != null) {
			int gameCycle = gameCycleProvider.getAsInt();
			int elapsed = gameCycle - animationCycleStart;
			if (elapsed > 100 && sequence.frameStep > 0) {
				elapsed = 100;
			}
			while (elapsed > sequence.getFrameLength(frame)) {
				elapsed -= sequence.getFrameLength(frame);
				frame++;
				if (frame < sequence.frameCount) {
					continue;
				}
				frame -= sequence.frameStep;
				if (frame >= 0 && frame < sequence.frameCount) {
					continue;
				}
				sequence = null;
				break;
			}
			animationCycleStart = gameCycle - elapsed;
			if (sequence != null) {
				frameId = sequence.primaryFrameIds[frame];
			}
		}

		GameObjectDefinition definition = morphIds != null ? resolveDefinition()
				: GameObjectDefinition.lookup(objectId);
		if (definition == null) {
			return null;
		}
		return definition.getModelAt(type, orientation, southWestHeight, southEastHeight, northEastHeight,
				northWestHeight, frameId);
	}
}
