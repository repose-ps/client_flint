package rs2.game;

import rs2.media.animation.AnimationFrame;

import rs2.media.Angle;
import rs2.cache.def.AnimationSequence;
import rs2.cache.def.SpotAnimation;
import rs2.game.entity.Actor;
import rs2.game.entity.Npc;
import rs2.game.entity.Player;
import rs2.scene.SceneConstants;

/**
 * Advances revision-377 actor movement, facing and animation state for one
 * client cycle.
 *
 * <p>
 * This is the non-network half of actor synchronization. Packet decoders
 * populate paths, forced-movement state, targets and animations; this class
 * consumes that state once per game cycle using the original integer movement
 * and angle arithmetic.
 * </p>
 */
public final class ActorUpdater {

	/** Minimum safe local-player tile used before forcing a path reset. */
	private static final int LOCAL_PLAYER_SAFE_MIN_TILE = 12;
	/** Exclusive maximum safe local-player tile used before forcing a path reset. */
	private static final int LOCAL_PLAYER_SAFE_MAX_TILE = 92;
	/** Largest fine-coordinate delta treated as normal queued path movement. */
	private static final int MAX_PATH_FINE_DELTA = SceneConstants.TILE_SIZE * 2;

	/** Creates a new actor updater with its default client state. */
	public ActorUpdater() {
	}

	/**
	 * Advances movement, facing, and animations for one actor during the current client cycle.
	 * @param actor the actor
	 * @param cycle the current client cycle
	 * @param localPlayer the local player
	 * @param players the player registry
	 * @param npcs the NPC registry
	 * @param localPlayerServerIndex the local player server index
	 * @param localPlayerArrayIndex the local player array index
	 * @param regionBaseX the region base X coordinate
	 * @param regionBaseY the region base Y coordinate
	 */
	public void update(Actor actor, int cycle, Player localPlayer, Player[] players, Npc[] npcs,
			int localPlayerServerIndex, int localPlayerArrayIndex, int regionBaseX, int regionBaseY) {
		if (actor.x < SceneConstants.TILE_SIZE || actor.y < SceneConstants.TILE_SIZE
				|| actor.x >= SceneConstants.MAX_TILE_INDEX * SceneConstants.TILE_SIZE
				|| actor.y >= SceneConstants.MAX_TILE_INDEX * SceneConstants.TILE_SIZE) {
			resetToPathStart(actor);
		}
		if (actor == localPlayer && (actor.x < LOCAL_PLAYER_SAFE_MIN_TILE * SceneConstants.TILE_SIZE
				|| actor.y < LOCAL_PLAYER_SAFE_MIN_TILE * SceneConstants.TILE_SIZE
				|| actor.x >= LOCAL_PLAYER_SAFE_MAX_TILE * SceneConstants.TILE_SIZE
				|| actor.y >= LOCAL_PLAYER_SAFE_MAX_TILE * SceneConstants.TILE_SIZE)) {
			resetToPathStart(actor);
		}

		if (actor.forceMoveStartCycle > cycle) {
			updatePreForcedMovement(actor, cycle);
		} else if (actor.forceMoveEndCycle >= cycle) {
			updateForcedMovement(actor, cycle);
		} else {
			updatePathMovement(actor);
		}

		updateFacing(actor, players, npcs, localPlayerServerIndex, localPlayerArrayIndex, regionBaseX, regionBaseY);
		updateAnimations(actor, cycle);
	}

	/**
	 * Resets to path start.
	 *
	 * @param actor the actor
	 */
	private static void resetToPathStart(Actor actor) {
		actor.sequence = -1;
		actor.spotAnimation = -1;
		actor.forceMoveStartCycle = 0;
		actor.forceMoveEndCycle = 0;
		actor.x = actor.pathX[0] * SceneConstants.TILE_SIZE + actor.size * SceneConstants.TILE_CENTER;
		actor.y = actor.pathY[0] * SceneConstants.TILE_SIZE + actor.size * SceneConstants.TILE_CENTER;
		actor.resetPath();
	}

	/**
	 * Interpolates an actor toward the start of a scheduled forced movement.
	 * @param actor the actor
	 * @param cycle the current client cycle
	 */
	private static void updatePreForcedMovement(Actor actor, int cycle) {
		int remaining = actor.forceMoveStartCycle - cycle;
		int targetX = actor.forceMoveStartX * SceneConstants.TILE_SIZE + actor.size * SceneConstants.TILE_CENTER;
		int targetY = actor.forceMoveStartY * SceneConstants.TILE_SIZE + actor.size * SceneConstants.TILE_CENTER;
		actor.x += (targetX - actor.x) / remaining;
		actor.y += (targetY - actor.y) / remaining;
		actor.movementDelay = 0;
		setForcedMovementOrientation(actor);
	}

	/**
	 * Interpolates an actor across an active forced-movement interval.
	 * @param actor the actor
	 * @param cycle the current client cycle
	 */
	private static void updateForcedMovement(Actor actor, int cycle) {
		if (actor.forceMoveEndCycle == cycle || actor.sequence == -1 || actor.sequenceDelay != 0
				|| actor.sequenceFrameCycle + 1 > AnimationSequence.sequences[actor.sequence]
						.getFrameLength(actor.sequenceFrame)) {
			int duration = actor.forceMoveEndCycle - actor.forceMoveStartCycle;
			int elapsed = cycle - actor.forceMoveStartCycle;
			int startX = actor.forceMoveStartX * SceneConstants.TILE_SIZE + actor.size * SceneConstants.TILE_CENTER;
			int startY = actor.forceMoveStartY * SceneConstants.TILE_SIZE + actor.size * SceneConstants.TILE_CENTER;
			int endX = actor.forceMoveEndX * SceneConstants.TILE_SIZE + actor.size * SceneConstants.TILE_CENTER;
			int endY = actor.forceMoveEndY * SceneConstants.TILE_SIZE + actor.size * SceneConstants.TILE_CENTER;
			actor.x = (startX * (duration - elapsed) + endX * elapsed) / duration;
			actor.y = (startY * (duration - elapsed) + endY * elapsed) / duration;
		}
		actor.movementDelay = 0;
		setForcedMovementOrientation(actor);
		actor.rotation = actor.orientation;
	}

	/**
	 * Sets forced movement orientation.
	 *
	 * @param actor the actor
	 */
	private static void setForcedMovementOrientation(Actor actor) {
		if (actor.forceMoveDirection == 0) {
			actor.orientation = Angle.HALF_TURN;
		}
		if (actor.forceMoveDirection == 1) {
			actor.orientation = Angle.THREE_QUARTER_TURN;
		}
		if (actor.forceMoveDirection == 2) {
			actor.orientation = 0;
		}
		if (actor.forceMoveDirection == 3) {
			actor.orientation = Angle.QUARTER_TURN;
		}
	}

	/**
	 * Advances normal path movement and chooses the movement animation and speed.
	 * @param actor the actor
	 */
	private static void updatePathMovement(Actor actor) {
		actor.movementSequence = actor.idleSequence;
		if (actor.pathLength == 0) {
			actor.movementDelay = 0;
			return;
		}
		if (actor.sequence != -1 && actor.sequenceDelay == 0) {
			AnimationSequence sequence = AnimationSequence.sequences[actor.sequence];
			if (actor.sequencePathLength > 0 && sequence.precedenceAnimating == 0) {
				actor.movementDelay++;
				return;
			}
			if (actor.sequencePathLength <= 0 && sequence.priority == 0) {
				actor.movementDelay++;
				return;
			}
		}

		int currentX = actor.x;
		int currentY = actor.y;
		int targetX = actor.pathX[actor.pathLength - 1] * SceneConstants.TILE_SIZE + actor.size * SceneConstants.TILE_CENTER;
		int targetY = actor.pathY[actor.pathLength - 1] * SceneConstants.TILE_SIZE + actor.size * SceneConstants.TILE_CENTER;
		if (targetX - currentX > MAX_PATH_FINE_DELTA || targetX - currentX < -MAX_PATH_FINE_DELTA
				|| targetY - currentY > MAX_PATH_FINE_DELTA || targetY - currentY < -MAX_PATH_FINE_DELTA) {
			actor.x = targetX;
			actor.y = targetY;
			return;
		}

		if (currentX < targetX) {
			if (currentY < targetY) {
				actor.orientation = Angle.FIVE_EIGHTHS_TURN;
			} else if (currentY > targetY) {
				actor.orientation = Angle.SEVEN_EIGHTHS_TURN;
			} else {
				actor.orientation = Angle.THREE_QUARTER_TURN;
			}
		} else if (currentX > targetX) {
			if (currentY < targetY) {
				actor.orientation = Angle.THREE_EIGHTHS_TURN;
			} else if (currentY > targetY) {
				actor.orientation = Angle.EIGHTH_TURN;
			} else {
				actor.orientation = Angle.QUARTER_TURN;
			}
		} else if (currentY < targetY) {
			actor.orientation = Angle.HALF_TURN;
		} else {
			actor.orientation = 0;
		}

		int deltaRotation = actor.orientation - actor.rotation & Angle.MASK;
		if (deltaRotation > Angle.HALF_TURN) {
			deltaRotation -= Angle.FULL_TURN;
		}
		int movementSequence = actor.walkBackSequence;
		if (deltaRotation >= -Angle.EIGHTH_TURN && deltaRotation <= Angle.EIGHTH_TURN) {
			movementSequence = actor.walkSequence;
		} else if (deltaRotation >= Angle.EIGHTH_TURN && deltaRotation < Angle.THREE_EIGHTHS_TURN) {
			movementSequence = actor.walkLeftSequence;
		} else if (deltaRotation >= -Angle.THREE_EIGHTHS_TURN && deltaRotation <= -Angle.EIGHTH_TURN) {
			movementSequence = actor.walkRightSequence;
		}
		if (movementSequence == -1) {
			movementSequence = actor.walkSequence;
		}
		actor.movementSequence = movementSequence;

		int speed = 4;
		if (actor.rotation != actor.orientation && actor.targetIndex == -1 && actor.turnSpeed != 0) {
			speed = 2;
		}
		if (actor.pathLength > 2) {
			speed = 6;
		}
		if (actor.pathLength > 3) {
			speed = 8;
		}
		if (actor.movementDelay > 0 && actor.pathLength > 1) {
			speed = 8;
			actor.movementDelay--;
		}
		if (actor.pathRunning[actor.pathLength - 1]) {
			speed <<= 1;
		}
		if (speed >= 8 && actor.movementSequence == actor.walkSequence && actor.runSequence != -1) {
			actor.movementSequence = actor.runSequence;
		}

		if (currentX < targetX) {
			actor.x += speed;
			if (actor.x > targetX) {
				actor.x = targetX;
			}
		} else if (currentX > targetX) {
			actor.x -= speed;
			if (actor.x < targetX) {
				actor.x = targetX;
			}
		}
		if (currentY < targetY) {
			actor.y += speed;
			if (actor.y > targetY) {
				actor.y = targetY;
			}
		} else if (currentY > targetY) {
			actor.y -= speed;
			if (actor.y < targetY) {
				actor.y = targetY;
			}
		}
		if (actor.x == targetX && actor.y == targetY) {
			actor.pathLength--;
			if (actor.sequencePathLength > 0) {
				actor.sequencePathLength--;
			}
		}
	}

	/**
	 * Rotates an actor toward its target entity or queued face coordinates.
	 * @param actor the actor
	 * @param players the player registry
	 * @param npcs the NPC registry
	 * @param localPlayerServerIndex the local player server index
	 * @param localPlayerArrayIndex the local player array index
	 * @param regionBaseX the region base X coordinate
	 * @param regionBaseY the region base Y coordinate
	 */
	private static void updateFacing(Actor actor, Player[] players, Npc[] npcs, int localPlayerServerIndex,
			int localPlayerArrayIndex, int regionBaseX, int regionBaseY) {
		if (actor.turnSpeed == 0) {
			return;
		}

		if (actor.targetIndex != -1 && actor.targetIndex < 32768) {
			Npc target = npcs[actor.targetIndex];
			if (target != null) {
				int deltaX = actor.x - target.x;
				int deltaY = actor.y - target.y;
				if (deltaX != 0 || deltaY != 0) {
					actor.orientation = (int) (Math.atan2(deltaX, deltaY) * Angle.UNITS_PER_RADIAN) & Angle.MASK;
				}
			}
		}
		if (actor.targetIndex >= 32768) {
			int playerIndex = actor.targetIndex - 32768;
			if (playerIndex == localPlayerServerIndex) {
				playerIndex = localPlayerArrayIndex;
			}
			Player target = players[playerIndex];
			if (target != null) {
				int deltaX = actor.x - target.x;
				int deltaY = actor.y - target.y;
				if (deltaX != 0 || deltaY != 0) {
					actor.orientation = (int) (Math.atan2(deltaX, deltaY) * Angle.UNITS_PER_RADIAN) & Angle.MASK;
				}
			}
		}
		if ((actor.faceX != 0 || actor.faceY != 0) && (actor.pathLength == 0 || actor.movementDelay > 0)) {
			int deltaX = actor.x - (actor.faceX - regionBaseX - regionBaseX) * 64;
			int deltaY = actor.y - (actor.faceY - regionBaseY - regionBaseY) * 64;
			if (deltaX != 0 || deltaY != 0) {
				actor.orientation = (int) (Math.atan2(deltaX, deltaY) * Angle.UNITS_PER_RADIAN) & Angle.MASK;
			}
			actor.faceX = 0;
			actor.faceY = 0;
		}

		int deltaRotation = actor.orientation - actor.rotation & Angle.MASK;
		if (deltaRotation != 0) {
			if (deltaRotation < actor.turnSpeed || deltaRotation > Angle.FULL_TURN - actor.turnSpeed) {
				actor.rotation = actor.orientation;
			} else if (deltaRotation > Angle.HALF_TURN) {
				actor.rotation -= actor.turnSpeed;
			} else {
				actor.rotation += actor.turnSpeed;
			}
			actor.rotation &= Angle.MASK;
			if (actor.movementSequence == actor.idleSequence && actor.rotation != actor.orientation) {
				if (actor.turnSequence != -1) {
					actor.movementSequence = actor.turnSequence;
					return;
				}
				actor.movementSequence = actor.walkSequence;
			}
		}
	}

	/**
	 * Advances movement, spot-animation, and primary-sequence frames.
	 * @param actor the actor
	 * @param cycle the current client cycle
	 */
	private static void updateAnimations(Actor actor, int cycle) {
		actor.animationStretches = false;
		if (actor.movementSequence != -1) {
			AnimationSequence movement = AnimationSequence.sequences[actor.movementSequence];
			actor.movementFrameCycle++;
			if (actor.movementFrame < movement.frameCount
					&& actor.movementFrameCycle > movement.getFrameLength(actor.movementFrame)) {
				actor.movementFrameCycle = 1;
				actor.movementFrame++;
			}
			if (actor.movementFrame >= movement.frameCount) {
				actor.movementFrameCycle = 1;
				actor.movementFrame = 0;
			}
		}

		if (actor.spotAnimation != -1 && cycle >= actor.spotAnimationStartCycle) {
			if (actor.spotAnimationFrame < 0) {
				actor.spotAnimationFrame = 0;
			}
			AnimationSequence spot = SpotAnimation.definitions[actor.spotAnimation].sequence;
			actor.spotAnimationFrameCycle++;
			if (actor.spotAnimationFrame < spot.frameCount
					&& actor.spotAnimationFrameCycle > spot.getFrameLength(actor.spotAnimationFrame)) {
				actor.spotAnimationFrameCycle = 1;
				actor.spotAnimationFrame++;
			}
			if (actor.spotAnimationFrame >= spot.frameCount
					&& (actor.spotAnimationFrame < 0 || actor.spotAnimationFrame >= spot.frameCount)) {
				actor.spotAnimation = -1;
			}
		}

		if (actor.sequence != -1 && actor.sequenceDelay <= 1) {
			AnimationSequence sequence = AnimationSequence.sequences[actor.sequence];
			if (sequence.precedenceAnimating == 1 && actor.sequencePathLength > 0 && actor.forceMoveStartCycle <= cycle
					&& actor.forceMoveEndCycle < cycle) {
				actor.sequenceDelay = 1;
				return;
			}
		}
		if (actor.sequence != -1 && actor.sequenceDelay == 0) {
			AnimationSequence sequence = AnimationSequence.sequences[actor.sequence];
			actor.sequenceFrameCycle++;
			if (actor.sequenceFrame < sequence.frameCount
					&& actor.sequenceFrameCycle > sequence.getFrameLength(actor.sequenceFrame)) {
				actor.sequenceFrameCycle = 1;
				actor.sequenceFrame++;
			}
			if (actor.sequenceFrame >= sequence.frameCount) {
				actor.sequenceFrame -= sequence.frameStep;
				actor.sequenceLoopCount++;
				if (actor.sequenceLoopCount >= sequence.maximumLoops) {
					actor.sequence = -1;
				}
				if (actor.sequenceFrame < 0 || actor.sequenceFrame >= sequence.frameCount) {
					actor.sequence = -1;
				}
			}
			actor.animationStretches = sequence.stretches;
		}
		if (actor.sequenceDelay > 0) {
			actor.sequenceDelay--;
		}
	}
}
