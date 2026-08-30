package rs2.media.renderable;

import rs2.cache.media.SpotAnimation;
import rs2.media.AnimationFrame;

/** A stationary spot animation placed at a fixed scene position. */
public class GraphicsObject extends Renderable {

	/** Stores the current plane. */
	public int plane;

	/** Stores the current X. */
	public int x;

	/** Stores the current Y. */
	public int y;

	/** Stores the current Z. */
	public int z;
	/**
	 * Whether finished.
	 */
	public boolean finished;

	/** Stores the current frame. */
	public int frame;

	/** Stores the current frame cycle. */
	public int frameCycle;

	/** Stores the current spot animation. */
	public SpotAnimation spotAnimation;

	/** Stores the current cycle start. */
	public int cycleStart;

	/**
	 * Creates a new graphics object.
	 *
	 * @param spotAnimationId the spot animation ID
	 * @param plane the scene plane
	 * @param x the X coordinate
	 * @param y the Y coordinate
	 * @param z the Z coordinate
	 * @param delay the delay
	 * @param currentCycle the current client cycle
	 */
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
	 *
	 * @param cycles the cycles
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

	/**
	 * Returns model.
	 *
	 * @return the resulting model
	 */
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
