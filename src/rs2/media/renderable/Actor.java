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

	/** Text currently displayed above the actor, or {@code null} when absent. */
	public String overheadText;

	/** Number of client cycles before the current overhead text expires. */
	public int overheadTextCyclesRemaining = 100;

	/** Protocol color/effect palette index used for overhead text. */
	public int overheadTextColor;

	/** Desired facing angle in the client's 0..2047 angular coordinate system. */
	public int orientation;

	/** Client cycle in which this actor was most recently present in synchronization. */
	public int lastUpdateCycle;

	/** Queued path tile X coordinates, with index zero holding the newest destination. */
	public final int[] pathX = new int[10];

	/** Queued path tile Y coordinates, with index zero holding the newest destination. */
	public final int[] pathY = new int[10];

	/** Sequence identifier currently used for movement animation, or {@code -1}. */
	public int movementSequence = -1;

	/** Current frame index within {@link #movementSequence}. */
	public int movementFrame;

	/** Number of cycles accumulated on the current movement frame. */
	public int movementFrameCycle;

	/** Whether each queued path step should use running movement. */
	public final boolean[] pathRunning = new boolean[10];

	/** Whether the current animation permits model stretching. */
	public boolean animationStretches;

	/** Protocol effect identifier used when rendering overhead text. */
	public int overheadTextEffect;

	/** Vertical model height used to position overhead elements. */
	public int height = 200;

	/** Client cycle until which the actor's health bar remains visible. */
	public int healthBarCycle = -1000;

	/** Current health value supplied by the most recent hit update. */
	public int currentHealth;

	/** Maximum health value supplied by the most recent hit update. */
	public int maxHealth;

	/** Protocol world-coordinate X target used for face-location updates. */
	public int faceX;

	/** Protocol world-coordinate Y target used for face-location updates. */
	public int faceY;

	/** Maximum angular change applied per client cycle while turning. */
	public int turnSpeed = 32;

	/** Actor footprint size in tiles. */
	public int size = 1;

	/** Forced-movement starting tile X coordinate. */
	public int forceMoveStartX;

	/** Forced-movement ending tile X coordinate. */
	public int forceMoveEndX;

	/** Forced-movement starting tile Y coordinate. */
	public int forceMoveStartY;

	/** Forced-movement ending tile Y coordinate. */
	public int forceMoveEndY;

	/** Client cycle on which forced movement reaches its starting tile. */
	public int forceMoveStartCycle;

	/** Client cycle on which forced movement reaches its ending tile. */
	public int forceMoveEndCycle;

	/** Protocol direction code that determines facing during forced movement. */
	public int forceMoveDirection;

	/** Index of the actor or entity this actor is targeting, or {@code -1}. */
	public int targetIndex = -1;

	/** Fine-grained world X coordinate in 128-units-per-tile space. */
	public int x;

	/** Fine-grained world Y coordinate in 128-units-per-tile space. */
	public int y;

	/** Current rendered facing angle in the client's 0..2047 angular coordinate system. */
	public int rotation;

	/**
	 * Number of queued path steps that remained when the current action sequence
	 * began.
	 */
	public int sequencePathLength;

	/** Active spot-animation identifier, or {@code -1} when none is active. */
	public int spotAnimation = -1;

	/** Current frame index within the active spot animation. */
	public int spotAnimationFrame;

	/** Number of cycles accumulated on the current spot-animation frame. */
	public int spotAnimationFrameCycle;

	/** Client cycle on which the active spot animation begins. */
	public int spotAnimationStartCycle;

	/** Vertical offset applied to the active spot-animation model. */
	public int spotAnimationHeight;

	/** Forward-walk animation sequence identifier, or {@code -1}. */
	public int walkSequence = -1;

	/** Backward-walk animation sequence identifier, or {@code -1}. */
	public int walkBackSequence = -1;

	/** Right-strafe/turn walking sequence identifier, or {@code -1}. */
	public int walkRightSequence = -1;

	/** Left-strafe/turn walking sequence identifier, or {@code -1}. */
	public int walkLeftSequence = -1;

	/**
	 * Movement delay accumulated when animation precedence prevents consuming the
	 * path.
	 */
	public int movementDelay;

	/** Active action-animation sequence identifier, or {@code -1}. */
	public int sequence = -1;

	/** Current frame index within {@link #sequence}. */
	public int sequenceFrame;

	/** Number of cycles accumulated on the current action-sequence frame. */
	public int sequenceFrameCycle;

	/** Remaining start delay for the current action sequence. */
	public int sequenceDelay;

	/** Number of completed loop entries for the current action sequence. */
	public int sequenceLoopCount;

	/** Running animation sequence identifier, or {@code -1}. */
	public int runSequence = -1;

	/** Damage values for the actor's four timed hit-splat slots. */
	public final int[] hitDamages = new int[4];

	/** Hit-type identifiers for the actor's four timed hit-splat slots. */
	public final int[] hitTypes = new int[4];

	/** Expiry cycle for each of the actor's four timed hit-splat slots. */
	public final int[] hitCycles = new int[4];

	/** Number of queued movement steps currently stored in the path arrays. */
	public int pathLength;

	/** Idle animation sequence identifier, or {@code -1}. */
	public int idleSequence = -1;

	/** In-place turning animation sequence identifier, or {@code -1}. */
	public int turnSequence = -1;

	/** Creates an actor with the revision-377 default movement and animation state. */
	public Actor() {
	}

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
	 *
	 * @return {@code true} when the actor is renderable; the base implementation
	 *         always returns {@code false}
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
