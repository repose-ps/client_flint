
package rs2.media.renderable;

import rs2.Client;
import rs2.cache.cfg.Varbit;
import rs2.cache.def.GameObjectDefinition;
import rs2.cache.media.AnimationSequence;

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

	/** Stores the current client instance. */
	public static Client clientInstance;

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
	 */
	public DynamicObject(int objectId, int type, int orientation, int southWestHeight, int southEastHeight,
			int northEastHeight, int northWestHeight, int animationId, boolean randomizeAnimation) {
		this.objectId = objectId;
		this.type = type;
		this.orientation = orientation;
		this.southWestHeight = southWestHeight;
		this.southEastHeight = southEastHeight;
		this.northEastHeight = northEastHeight;
		this.northWestHeight = northWestHeight;

		if (animationId != -1) {
			sequence = AnimationSequence.sequences[animationId];
			frame = 0;
			animationCycleStart = Client.gameCycle - 1;
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
			int mask = Client.bitMasks[varbit.mostSignificantBit - varbit.leastSignificantBit];
			morphIndex = clientInstance.varpValues[varbit.varpId] >> varbit.leastSignificantBit & mask;
		} else if (varpId != -1) {
			morphIndex = clientInstance.varpValues[varpId];
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
			int elapsed = Client.gameCycle - animationCycleStart;
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
			animationCycleStart = Client.gameCycle - elapsed;
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
