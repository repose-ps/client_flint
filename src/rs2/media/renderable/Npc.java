package rs2.media.renderable;

import rs2.cache.def.NpcDefinition;
import rs2.cache.media.AnimationSequence;
import rs2.cache.media.SpotAnimation;
import rs2.media.AnimationFrame;

/** Runtime non-player actor backed by an {@link NpcDefinition}. */
public class Npc extends Actor {

    public NpcDefinition definition;

    /** Builds the NPC body with its current movement/action animation frames. */
    public Model getBaseModel() {
        if (sequence >= 0 && sequenceDelay == 0) {
            int primaryFrameId = AnimationSequence.sequences[sequence].primaryFrameIds[sequenceFrame];
            int secondaryFrameId = -1;
            if (movementSequence >= 0 && movementSequence != idleSequence) {
                secondaryFrameId = AnimationSequence.sequences[movementSequence].primaryFrameIds[movementFrame];
            }
            return definition.getAnimatedModel(
                    primaryFrameId,
                    secondaryFrameId,
                    AnimationSequence.sequences[sequence].interleaveOrder);
        }

        int movementFrameId = -1;
        if (movementSequence >= 0) {
            movementFrameId = AnimationSequence.sequences[movementSequence].primaryFrameIds[movementFrame];
        }
        return definition.getAnimatedModel(movementFrameId, -1, null);
    }

    @Override
    protected Model getModel() {
        if (definition == null) {
            return null;
        }
        Model model = getBaseModel();
        if (model == null) {
            return null;
        }
        height = model.modelHeight;

        if (spotAnimation != -1 && spotAnimationFrame != -1) {
            SpotAnimation graphic = SpotAnimation.definitions[spotAnimation];
            Model spotModel = graphic.getModel();
            if (spotModel != null) {
                int frameId = graphic.sequence.primaryFrameIds[spotAnimationFrame];
                Model animatedSpotModel = new Model(spotModel, false, true, AnimationFrame.isNull(frameId));
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

        if (definition.size == 1) {
            model.singleTile = true;
        }
        return model;
    }

    @Override
    public boolean isVisible() {
        return definition != null;
    }
}
