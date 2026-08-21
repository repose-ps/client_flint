package rs2.media.renderable;

import rs2.cache.media.SpotAnimation;
import rs2.media.AnimationFrame;

/** A stationary spot animation placed at a fixed scene position. */
public class GraphicsObject extends Renderable {

	public int plane;
	public int x;
	public int y;
	public int z;
	public boolean finished;
	public int frame;
	public int frameCycle;
	public SpotAnimation spotAnimation;
	public int cycleStart;

	public GraphicsObject(int spotAnimationId, int plane, int x, int y, int z, int delay, int currentCycle) {
		this.spotAnimation = SpotAnimation.definitions[spotAnimationId];
		this.plane = plane;
		this.x = x;
		this.y = y;
		this.z = z;
		this.cycleStart = currentCycle + delay;
		this.finished = false;
	}

	/**
	 * Advances the animation; completion wraps to frame zero and marks the object
	 * finished.
	 */
	public void advance(int cycles) {
		frameCycle += cycles;
		while (frameCycle > spotAnimation.sequence.getFrameLength(frame)) {
			frameCycle -= spotAnimation.sequence.getFrameLength(frame);
			frame++;
			if (frame >= spotAnimation.sequence.frameCount
					&& (frame < 0 || frame >= spotAnimation.sequence.frameCount)) {
				frame = 0;
				finished = true;
			}
		}
	}

	@Override
	protected Model getModel() {
		Model baseModel = spotAnimation.getModel();
		if (baseModel == null) {
			return null;
		}

		int frameId = spotAnimation.sequence.primaryFrameIds[frame];
		Model model = new Model(baseModel, false, true, AnimationFrame.isNull(frameId));
		if (!finished) {
			model.createBones();
			model.applyTransformation(frameId);
			model.triangleGroups = null;
			model.vertexGroups = null;
		}
		if (spotAnimation.resizeXY != 128 || spotAnimation.resizeZ != 128) {
			model.scale(spotAnimation.resizeXY, spotAnimation.resizeZ, spotAnimation.resizeXY);
		}
		if (spotAnimation.rotation != 0) {
			if (spotAnimation.rotation == 90) {
				model.rotateY90Ccw();
			}
			if (spotAnimation.rotation == 180) {
				model.rotateY90Ccw();
				model.rotateY90Ccw();
			}
			if (spotAnimation.rotation == 270) {
				model.rotateY90Ccw();
				model.rotateY90Ccw();
				model.rotateY90Ccw();
			}
		}
		model.light(64 + spotAnimation.ambient, 850 + spotAnimation.contrast, -30, -50, -30, true);
		return model;
	}
}