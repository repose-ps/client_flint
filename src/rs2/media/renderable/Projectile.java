package rs2.media.renderable;

import rs2.cache.media.SpotAnimation;
import rs2.media.AnimationFrame;

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

	public SpotAnimation spotAnimation;

	public int plane;

	public double x;

	public double y;

	public double z;

	public int slope;

	public int startHeight;
	/**
	 * Index used for target.
	 */
	public int targetIndex;

	public int yaw;

	public int pitch;

	public int cycleStart;

	public int cycleEnd;

	public int frame;

	public int frameCycle;

	public double speedX;

	public double speedY;

	public double speed;

	public double speedZ;

	public double accelerationZ;
	/**
	 * Whether is moving.
	 */
	public boolean isMoving;

	public int sourceX;

	public int sourceY;

	public int sourceZ;

	public int endHeight;

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
		yaw = (int) (Math.atan2(speedX, speedY) * 325.94900000000001D) + 1024 & 0x7ff;
		pitch = (int) (Math.atan2(speedZ, speed) * 325.94900000000001D) & 0x7ff;

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
