package rs2.scene.entity;

import rs2.media.animation.AnimationFrame;

import rs2.media.Angle;
import rs2.cache.def.SpotAnimation;
import rs2.media.animation.AnimationFrame;
import rs2.media.model.Model;
import rs2.media.model.Renderable;


/**
 * A moving spot-animation model following the client's parabolic projectile
 * path.
 *
 * <p>
 * Coordinates and cycles use the client's world/tick units. The historical
 * {@code startHeight} name is retained from later deobfuscated clients even
 * though the value is used as a horizontal offset from the source along the
 * initial trajectory.
 * </p>
 */
public class Projectile extends Renderable {

	/** Stores the current spot animation. */
	public SpotAnimation spotAnimation;

	/** Stores the current plane. */
	public int plane;

	/** Stores the current X. */
	public double x;

	/** Stores the current Y. */
	public double y;

	/** Stores the current Z. */
	public double z;

	/** Stores the current slope. */
	public int slope;

	/** Stores the current start height. */
	public int startHeight;
	/**
	 * Index used for target.
	 */
	public int targetIndex;

	/** Stores the current yaw. */
	public int yaw;

	/** Stores the current pitch. */
	public int pitch;

	/** Stores the current cycle start. */
	public int cycleStart;

	/** Stores the current cycle end. */
	public int cycleEnd;

	/** Stores the current frame. */
	public int frame;

	/** Stores the current frame cycle. */
	public int frameCycle;

	/** Stores the current speed X. */
	public double speedX;

	/** Stores the current speed Y. */
	public double speedY;

	/** Stores the current speed. */
	public double speed;

	/** Stores the current speed Z. */
	public double speedZ;

	/** Stores the current acceleration Z. */
	public double accelerationZ;
	/**
	 * Whether is moving.
	 */
	public boolean isMoving;

	/** Stores the current source X. */
	public int sourceX;

	/** Stores the current source Y. */
	public int sourceY;

	/** Stores the current source Z. */
	public int sourceZ;

	/** Stores the current end height. */
	public int endHeight;

	/**
	 * Creates a new projectile.
	 *
	 * @param spotAnimationId the spot animation ID
	 * @param plane the scene plane
	 * @param sourceX the source X
	 * @param sourceY the source Y
	 * @param sourceZ the source Z
	 * @param cycleStart the cycle start
	 * @param cycleEnd the cycle end
	 * @param slope the slope
	 * @param startHeight the start height
	 * @param targetIndex the target index
	 * @param endHeight the end height
	 */
	public Projectile(int spotAnimationId, int plane, int sourceX, int sourceY, int sourceZ, int cycleStart,
			int cycleEnd, int slope, int startHeight, int targetIndex, int endHeight) {
		this.spotAnimation = SpotAnimation.definitions[spotAnimationId];
		this.plane = plane;
		this.sourceX = sourceX;
		this.sourceY = sourceY;
		this.sourceZ = sourceZ;
		this.cycleStart = cycleStart;
		this.cycleEnd = cycleEnd;
		this.slope = slope;
		this.startHeight = startHeight;
		this.targetIndex = targetIndex;
		this.endHeight = endHeight;
		this.isMoving = false;
	}

	/**
	 * Recomputes the trajectory so it reaches the supplied destination at
	 * {@link #cycleEnd}.
	 *
	 * @param destinationX the destination x
	 * @param destinationY the destination y
	 * @param destinationZ the destination z
	 * @param cycle        the cycle
	 */
	public void setDestination(int destinationX, int destinationY, int destinationZ, int cycle) {
		if (!isMoving) {
			double deltaX = destinationX - sourceX;
			double deltaY = destinationY - sourceY;
			double distance = Math.sqrt(deltaX * deltaX + deltaY * deltaY);
			x = sourceX + deltaX * startHeight / distance;
			y = sourceY + deltaY * startHeight / distance;
			z = sourceZ;
		}

		double cyclesRemaining = cycleEnd + 1 - cycle;
		speedX = (destinationX - x) / cyclesRemaining;
		speedY = (destinationY - y) / cyclesRemaining;
		speed = Math.sqrt(speedX * speedX + speedY * speedY);
		if (!isMoving) {
			speedZ = -speed * Math.tan(slope * 0.02454369D);
		}
		accelerationZ = 2D * (destinationZ - z - speedZ * cyclesRemaining) / (cyclesRemaining * cyclesRemaining);
	}

	/**
	 * Advances position, orientation, and spot-animation frame state by client
	 * cycles.
	 *
	 * @param cycles the cycles
	 */
	public void advance(int cycles) {
		isMoving = true;
		x += speedX * cycles;
		y += speedY * cycles;
		z += speedZ * cycles + 0.5D * accelerationZ * cycles * cycles;
		speedZ += accelerationZ * cycles;
		yaw = (int) (Math.atan2(speedX, speedY) * Angle.UNITS_PER_RADIAN) + Angle.HALF_TURN & Angle.MASK;
		pitch = (int) (Math.atan2(speedZ, speed) * Angle.UNITS_PER_RADIAN) & Angle.MASK;

		if (spotAnimation.sequence != null) {
			for (frameCycle += cycles; frameCycle > spotAnimation.sequence.getFrameLength(frame);) {
				frameCycle -= spotAnimation.sequence.getFrameLength(frame);
				frame++;
				if (frame >= spotAnimation.sequence.frameCount) {
					frame = 0;
				}
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

		int frameId = -1;
		if (spotAnimation.sequence != null) {
			frameId = spotAnimation.sequence.primaryFrameIds[frame];
		}

		Model model = new Model(baseModel, false, true, AnimationFrame.isNull(frameId));
		if (frameId != -1) {
			model.createBones();
			model.applyTransformation(frameId);
			model.triangleGroups = null;
			model.vertexGroups = null;
		}
		if (spotAnimation.resizeXY != 128 || spotAnimation.resizeZ != 128) {
			model.scale(spotAnimation.resizeXY, spotAnimation.resizeZ, spotAnimation.resizeXY);
		}
		model.rotateX(pitch);
		model.light(64 + spotAnimation.ambient, 850 + spotAnimation.contrast, -30, -50, -30, true);
		return model;
	}
}
