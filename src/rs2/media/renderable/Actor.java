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
public abstract class Actor extends Renderable {

	public String overheadText;

	public int overheadTextCyclesRemaining = 100;

	public int overheadTextColor;

	public int orientation;

	public int lastUpdateCycle;

	public final int[] pathX = new int[10];

	public final int[] pathY = new int[10];

	public int movementSequence = -1;

	public int movementFrame;

	public int movementFrameCycle;

	public final boolean[] pathRunning = new boolean[10];
	/**
	 * Whether animation stretches.
	 */
	public boolean animationStretches;

	public int overheadTextEffect;

	public int height = 200;

	public int healthBarCycle = -1000;

	public int currentHealth;

	public int maxHealth;

	public int faceX;

	public int faceY;

	public int turnSpeed = 32;

	public int size = 1;

	public int forceMoveStartX;

	public int forceMoveEndX;

	public int forceMoveStartY;

	public int forceMoveEndY;

	public int forceMoveStartCycle;

	public int forceMoveEndCycle;

	public int forceMoveDirection;
	/**
	 * Index used for target.
	 */
	public int targetIndex = -1;

	public int x;

	public int y;

	public int rotation;
	/**
	 * Number of queued path steps that remained when the current action sequence
	 * began.
	 */
	public int sequencePathLength;

	public int spotAnimation = -1;

	public int spotAnimationFrame;

	public int spotAnimationFrameCycle;

	public int spotAnimationStartCycle;

	public int spotAnimationHeight;

	public int walkSequence = -1;

	public int walkBackSequence = -1;

	public int walkRightSequence = -1;

	public int walkLeftSequence = -1;
	/**
	 * Movement delay accumulated when animation precedence prevents consuming the
	 * path.
	 */
	public int movementDelay;

	public int sequence = -1;

	public int sequenceFrame;

	public int sequenceFrameCycle;

	public int sequenceDelay;
	/**
	 * Number of sequence loop entries.
	 */
	public int sequenceLoopCount;

	public int runSequence = -1;

	public final int[] hitDamages = new int[4];

	public final int[] hitTypes = new int[4];

	public final int[] hitCycles = new int[4];

	public int pathLength;

	public int idleSequence = -1;

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
