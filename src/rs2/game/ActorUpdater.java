package rs2.game;

import rs2.cache.media.AnimationSequence;
import rs2.cache.media.SpotAnimation;
import rs2.media.renderable.Actor;
import rs2.media.renderable.Npc;
import rs2.media.renderable.Player;

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
		if (actor.x < 128 || actor.y < 128 || actor.x >= 13184 || actor.y >= 13184) {
			resetToPathStart(actor);
		}
		if (actor == localPlayer && (actor.x < 1536 || actor.y < 1536 || actor.x >= 11776 || actor.y >= 11776)) {
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
		actor.x = actor.pathX[0] * 128 + actor.size * 64;
		actor.y = actor.pathY[0] * 128 + actor.size * 64;
		actor.resetPath();
	}

	/**
	 * Interpolates an actor toward the start of a scheduled forced movement.
	 * @param actor the actor
	 * @param cycle the current client cycle
	 */
	private static void updatePreForcedMovement(Actor actor, int cycle) {
		int remaining = actor.forceMoveStartCycle - cycle;
		int targetX = actor.forceMoveStartX * 128 + actor.size * 64;
		int targetY = actor.forceMoveStartY * 128 + actor.size * 64;
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
			int startX = actor.forceMoveStartX * 128 + actor.size * 64;
			int startY = actor.forceMoveStartY * 128 + actor.size * 64;
			int endX = actor.forceMoveEndX * 128 + actor.size * 64;
			int endY = actor.forceMoveEndY * 128 + actor.size * 64;
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
			actor.orientation = 1024;
		}
		if (actor.forceMoveDirection == 1) {
			actor.orientation = 1536;
		}
		if (actor.forceMoveDirection == 2) {
			actor.orientation = 0;
		}
		if (actor.forceMoveDirection == 3) {
			actor.orientation = 512;
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
		int targetX = actor.pathX[actor.pathLength - 1] * 128 + actor.size * 64;
		int targetY = actor.pathY[actor.pathLength - 1] * 128 + actor.size * 64;
		if (targetX - currentX > 256 || targetX - currentX < -256 || targetY - currentY > 256
				|| targetY - currentY < -256) {
			actor.x = targetX;
			actor.y = targetY;
			return;
		}

		if (currentX < targetX) {
			if (currentY < targetY) {
				actor.orientation = 1280;
			} else if (currentY > targetY) {
				actor.orientation = 1792;
			} else {
				actor.orientation = 1536;
			}
		} else if (currentX > targetX) {
			if (currentY < targetY) {
				actor.orientation = 768;
			} else if (currentY > targetY) {
				actor.orientation = 256;
			} else {
				actor.orientation = 512;
			}
		} else if (currentY < targetY) {
			actor.orientation = 1024;
		} else {
			actor.orientation = 0;
		}

		int deltaRotation = actor.orientation - actor.rotation & 0x7ff;
		if (deltaRotation > 1024) {
			deltaRotation -= 2048;
		}
		int movementSequence = actor.walkBackSequence;
		if (deltaRotation >= -256 && deltaRotation <= 256) {
			movementSequence = actor.walkSequence;
		} else if (deltaRotation >= 256 && deltaRotation < 768) {
			movementSequence = actor.walkLeftSequence;
		} else if (deltaRotation >= -768 && deltaRotation <= -256) {
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
					actor.orientation = (int) (Math.atan2(deltaX, deltaY) * 325.94900000000001D) & 0x7ff;
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
					actor.orientation = (int) (Math.atan2(deltaX, deltaY) * 325.94900000000001D) & 0x7ff;
				}
			}
		}
		if ((actor.faceX != 0 || actor.faceY != 0) && (actor.pathLength == 0 || actor.movementDelay > 0)) {
			int deltaX = actor.x - (actor.faceX - regionBaseX - regionBaseX) * 64;
			int deltaY = actor.y - (actor.faceY - regionBaseY - regionBaseY) * 64;
			if (deltaX != 0 || deltaY != 0) {
				actor.orientation = (int) (Math.atan2(deltaX, deltaY) * 325.94900000000001D) & 0x7ff;
			}
			actor.faceX = 0;
			actor.faceY = 0;
		}

		int deltaRotation = actor.orientation - actor.rotation & 0x7ff;
		if (deltaRotation != 0) {
			if (deltaRotation < actor.turnSpeed || deltaRotation > 2048 - actor.turnSpeed) {
				actor.rotation = actor.orientation;
			} else if (deltaRotation > 1024) {
				actor.rotation -= actor.turnSpeed;
			} else {
				actor.rotation += actor.turnSpeed;
			}
			actor.rotation &= 0x7ff;
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
