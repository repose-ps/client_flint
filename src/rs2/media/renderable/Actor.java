package rs2.media.renderable;

import rs2.cache.media.AnimationSequence;

/**
 * Shared movement, orientation, animation, overhead-text and hit state for
 * players and NPCs.
 *
 * <p>
 * Path entries are tile coordinates. {@link #pathLength} is capped at nine
 * queued steps, leaving element zero as the newest destination. Fine world
 * coordinates use the classic 128-units-per-tile scale and are centered using
 * {@link #size}.
 * </p>
 */
public abstract /**
				 * Initializes this instance.
				 */
class Actor extends Renderable {

	/**
	 * Stores overhead text.
	 */
	public String overheadText;
	/**
	 * Stores overhead text cycles remaining.
	 */
	public int overheadTextCyclesRemaining = 100;
	/**
	 * Stores overhead text color.
	 */
	public int overheadTextColor;
	/**
	 * Stores orientation.
	 */
	public int orientation;
	/**
	 * Stores last update cycle.
	 */
	public int lastUpdateCycle;
	/**
	 * Stores path x.
	 */
	public final int[] pathX = new int[10];
	/**
	 * Stores path y.
	 */
	public final int[] pathY = new int[10];
	/**
	 * Stores movement sequence.
	 */
	public int movementSequence = -1;
	/**
	 * Stores movement frame.
	 */
	public int movementFrame;
	/**
	 * Stores movement frame cycle.
	 */
	public int movementFrameCycle;
	/**
	 * Stores path running.
	 */
	public final boolean[] pathRunning = new boolean[10];
	/**
	 * Whether animation stretches.
	 */
	public boolean animationStretches;
	/**
	 * Stores overhead text effect.
	 */
	public int overheadTextEffect;
	/**
	 * Stores height.
	 */
	public int height = 200;
	/**
	 * Stores health bar cycle.
	 */
	public int healthBarCycle = -1000;
	/**
	 * Stores current health.
	 */
	public int currentHealth;
	/**
	 * Stores max health.
	 */
	public int maxHealth;
	/**
	 * Stores face x.
	 */
	public int faceX;
	/**
	 * Stores face y.
	 */
	public int faceY;
	/**
	 * Stores turn speed.
	 */
	public int turnSpeed = 32;
	/**
	 * Stores size.
	 */
	public int size = 1;
	/**
	 * Stores force move start x.
	 */
	public int forceMoveStartX;
	/**
	 * Stores force move end x.
	 */
	public int forceMoveEndX;
	/**
	 * Stores force move start y.
	 */
	public int forceMoveStartY;
	/**
	 * Stores force move end y.
	 */
	public int forceMoveEndY;
	/**
	 * Stores force move start cycle.
	 */
	public int forceMoveStartCycle;
	/**
	 * Stores force move end cycle.
	 */
	public int forceMoveEndCycle;
	/**
	 * Stores force move direction.
	 */
	public int forceMoveDirection;
	/**
	 * Index used for target.
	 */
	public int targetIndex = -1;
	/**
	 * Stores x.
	 */
	public int x;
	/**
	 * Stores y.
	 */
	public int y;
	/**
	 * Stores rotation.
	 */
	public int rotation;
	/**
	 * Number of queued path steps that remained when the current action sequence
	 * began.
	 */
	public int sequencePathLength;
	/**
	 * Stores spot animation.
	 */
	public int spotAnimation = -1;
	/**
	 * Stores spot animation frame.
	 */
	public int spotAnimationFrame;
	/**
	 * Stores spot animation frame cycle.
	 */
	public int spotAnimationFrameCycle;
	/**
	 * Stores spot animation start cycle.
	 */
	public int spotAnimationStartCycle;
	/**
	 * Stores spot animation height.
	 */
	public int spotAnimationHeight;
	/**
	 * Stores walk sequence.
	 */
	public int walkSequence = -1;
	/**
	 * Stores walk back sequence.
	 */
	public int walkBackSequence = -1;
	/**
	 * Stores walk right sequence.
	 */
	public int walkRightSequence = -1;
	/**
	 * Stores walk left sequence.
	 */
	public int walkLeftSequence = -1;
	/**
	 * Movement delay accumulated when animation precedence prevents consuming the
	 * path.
	 */
	public int movementDelay;
	/**
	 * Stores sequence.
	 */
	public int sequence = -1;
	/**
	 * Stores sequence frame.
	 */
	public int sequenceFrame;
	/**
	 * Stores sequence frame cycle.
	 */
	public int sequenceFrameCycle;
	/**
	 * Stores sequence delay.
	 */
	public int sequenceDelay;
	/**
	 * Number of sequence loop entries.
	 */
	public int sequenceLoopCount;
	/**
	 * Stores run sequence.
	 */
	public int runSequence = -1;
	/**
	 * Stores hit damages.
	 */
	public final int[] hitDamages = new int[4];
	/**
	 * Stores hit types.
	 */
	public final int[] hitTypes = new int[4];
	/**
	 * Stores hit cycles.
	 */
	public final int[] hitCycles = new int[4];
	/**
	 * Stores path length.
	 */
	public int pathLength;
	/**
	 * Stores idle sequence.
	 */
	public int idleSequence = -1;
	/**
	 * Stores turn sequence.
	 */
	public int turnSequence = -1;

	/**
	 * Clears queued movement without changing the actor's current tile or fine
	 * coordinates.
	 */
	public void resetPath() {
		pathLength = 0;
		sequencePathLength = 0;
	}

	/**
	 * Base visibility hook. Concrete actor types report whether enough
	 * definition/appearance state is available to render them.
	 */
	public boolean isVisible() {
		return false;
	}

	/**
	 * Queues one of the eight protocol walking directions.
	 *
	 * @param running   the running
	 * @param direction the direction
	 */
	public void moveInDirection(boolean running, int direction) {
		int nextX = pathX[0];
		int nextY = pathY[0];
		if (direction == 0) {
			nextX--;
			nextY++;
		}
		if (direction == 1) {
			nextY++;
		}
		if (direction == 2) {
			nextX++;
			nextY++;
		}
		if (direction == 3) {
			nextX--;
		}
		if (direction == 4) {
			nextX++;
		}
		if (direction == 5) {
			nextX--;
			nextY--;
		}
		if (direction == 6) {
			nextY--;
		}
		if (direction == 7) {
			nextX++;
			nextY--;
		}

		cancelMovementBlockingSequence();
		if (pathLength < 9) {
			pathLength++;
		}
		for (int index = pathLength; index > 0; index--) {
			pathX[index] = pathX[index - 1];
			pathY[index] = pathY[index - 1];
			pathRunning[index] = pathRunning[index - 1];
		}
		pathX[0] = nextX;
		pathY[0] = nextY;
		pathRunning[0] = running;
	}

	/**
	 * Stores one of the four timed hit-splat slots, using the first slot whose
	 * expiry has passed.
	 * 
	 * @param cycle  the cycle
	 * @param damage the damage
	 * @param type   the type
	 */
	public void addHit(int cycle, int damage, int type) {
		for (int slot = 0; slot < 4; slot++) {
			if (hitCycles[slot] <= cycle) {
				hitDamages[slot] = damage;
				hitTypes[slot] = type;
				hitCycles[slot] = cycle + 70;
				return;
			}
		}
	}

	/**
	 * Moves or teleports the actor to a tile.
	 *
	 * <p>
	 * Non-teleport moves within eight tiles are queued. Larger moves, or explicit
	 * teleports, discard the path and immediately recompute fine world coordinates.
	 * </p>
	 * 
	 * @param tileX    the tile x
	 * @param tileY    the tile y
	 * @param teleport the teleport
	 */
	public void setPosition(int tileX, int tileY, boolean teleport) {
		cancelMovementBlockingSequence();
		if (!teleport) {
			int deltaX = tileX - pathX[0];
			int deltaY = tileY - pathY[0];
			if (deltaX >= -8 && deltaX <= 8 && deltaY >= -8 && deltaY <= 8) {
				if (pathLength < 9) {
					pathLength++;
				}
				for (int index = pathLength; index > 0; index--) {
					pathX[index] = pathX[index - 1];
					pathY[index] = pathY[index - 1];
					pathRunning[index] = pathRunning[index - 1];
				}
				pathX[0] = tileX;
				pathY[0] = tileY;
				pathRunning[0] = false;
				return;
			}
		}

		pathLength = 0;
		sequencePathLength = 0;
		movementDelay = 0;
		pathX[0] = tileX;
		pathY[0] = tileY;
		x = tileX * 128 + size * 64;
		y = tileY * 128 + size * 64;
	}

	/**
	 * Performs cancel movement blocking sequence.
	 */
	private void cancelMovementBlockingSequence() {
		if (sequence != -1 && AnimationSequence.sequences[sequence].priority == 1) {
			sequence = -1;
		}
	}
}