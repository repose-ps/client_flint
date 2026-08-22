
package rs2.media.renderable;

import rs2.client;
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

	public int southWestHeight;
	public int southEastHeight;
	public int northEastHeight;
	public int northWestHeight;
	public int objectId;
	public int type;
	public int orientation;
	public static client clientInstance;
	public AnimationSequence sequence;
	public int varbitId;
	public int varpId;
	public int[] morphIds;
	public int animationCycleStart;
	public int frame;

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
			animationCycleStart = client.anInt1325 - 1;
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

	private GameObjectDefinition resolveDefinition() {
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
		return GameObjectDefinition.lookup(morphIds[morphIndex]);
	}

	@Override
	protected Model getModel() {
		int frameId = -1;
		if (sequence != null) {
			int elapsed = client.anInt1325 - animationCycleStart;
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
			animationCycleStart = client.anInt1325 - elapsed;
			if (sequence != null) {
				frameId = sequence.primaryFrameIds[frame];
			}
		}

		GameObjectDefinition definition = morphIds != null ? resolveDefinition() : GameObjectDefinition.lookup(objectId);
		if (definition == null) {
			return null;
		}
		return definition.getModelAt(type, orientation, southWestHeight, southEastHeight, northEastHeight,
				northWestHeight, frameId);
	}
}