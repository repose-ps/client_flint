package rs2.game;

import rs2.media.renderable.Actor;
import rs2.media.renderable.Model;
import rs2.net.Buffer;
import rs2.sign.Signlink;

/**
 * Owns revision-377 camera position, follow controls, cinematic camera motion,
 * roof-plane selection and camera shake state.
 */
public final /**
				 * Initializes this instance.
				 */
class CameraController {
	/**
	 * Stores angle mask.
	 */
	public static final int ANGLE_MASK = 0x7ff;

	/**
	 * Stores x.
	 */
	public int x;
	/**
	 * Stores height.
	 */
	public int height;
	/**
	 * Stores y.
	 */
	public int y;
	/**
	 * Stores pitch.
	 */
	public int pitch = 128;
	/**
	 * Stores yaw.
	 */
	public int yaw;

	/**
	 * Stores follow pitch.
	 */
	public int followPitch = 128;
	/**
	 * Stores follow yaw.
	 */
	public int followYaw;
	/**
	 * Stores yaw velocity.
	 */
	private int yawVelocity;
	/**
	 * Stores pitch velocity.
	 */
	private int pitchVelocity;
	/**
	 * Stores follow target x.
	 */
	public int followTargetX;
	/**
	 * Stores follow target y.
	 */
	public int followTargetY;
	/**
	 * Stores terrain pitch scale.
	 */
	public int terrainPitchScale;

	/**
	 * Stores follow offset x.
	 */
	public int followOffsetX;
	/**
	 * Stores follow offset y.
	 */
	public int followOffsetY;
	/**
	 * Stores yaw offset.
	 */
	public int yawOffset;
	/**
	 * Stores follow offset xstep.
	 */
	private int followOffsetXStep = 2;
	/**
	 * Stores follow offset ystep.
	 */
	private int followOffsetYStep = 2;
	/**
	 * Stores yaw offset step.
	 */
	private int yawOffsetStep = 1;
	/**
	 * Stores follow offset cycle.
	 */
	private int followOffsetCycle;

	/**
	 * Whether cinematic.
	 */
	public boolean cinematic;
	/**
	 * Stores position tile x.
	 */
	private int positionTileX;
	/**
	 * Stores position tile y.
	 */
	private int positionTileY;
	/**
	 * Stores position height offset.
	 */
	private int positionHeightOffset;
	/**
	 * Stores position base speed.
	 */
	private int positionBaseSpeed;
	/**
	 * Stores position scale.
	 */
	private int positionScale;
	/**
	 * Stores look tile x.
	 */
	private int lookTileX;
	/**
	 * Stores look tile y.
	 */
	private int lookTileY;
	/**
	 * Stores look height offset.
	 */
	private int lookHeightOffset;
	/**
	 * Stores look base speed.
	 */
	private int lookBaseSpeed;
	/**
	 * Stores look scale.
	 */
	private int lookScale;

	/**
	 * Stores shake enabled.
	 */
	private final boolean[] shakeEnabled = new boolean[5];
	/**
	 * Stores shake random amplitude.
	 */
	private final int[] shakeRandomAmplitude = new int[5];
	/**
	 * Stores shake sine amplitude.
	 */
	private final int[] shakeSineAmplitude = new int[5];
	/**
	 * Stores shake frequency.
	 */
	private final int[] shakeFrequency = new int[5];
	/**
	 * Stores shake cycles.
	 */
	private final int[] shakeCycles = new int[5];

	/**
	 * Stores roof probe counter.
	 */
	private int roofProbeCounter;

	/**
	 * Updates follow.
	 * 
	 * @param localPlayer the local player
	 * @param keyStatus   the key status
	 * @param world       the world
	 * @param plane       the plane
	 * @param regionX     the region x
	 * @param regionY     the region y
	 * @param baseX       the base x
	 * @param baseY       the base y
	 */
	public void updateFollow(Actor localPlayer, int[] keyStatus, WorldState world, int plane, int regionX, int regionY,
			int baseX, int baseY) {
		try {
			int targetX = localPlayer.x + followOffsetX;
			int targetY = localPlayer.y + followOffsetY;
			if (followTargetX - targetX < -500 || followTargetX - targetX > 500 || followTargetY - targetY < -500
					|| followTargetY - targetY > 500) {
				followTargetX = targetX;
				followTargetY = targetY;
			}
			if (followTargetX != targetX) {
				followTargetX += (targetX - followTargetX) / 16;
			}
			if (followTargetY != targetY) {
				followTargetY += (targetY - followTargetY) / 16;
			}

			if (keyStatus[1] == 1) {
				yawVelocity += (-24 - yawVelocity) / 2;
			} else if (keyStatus[2] == 1) {
				yawVelocity += (24 - yawVelocity) / 2;
			} else {
				yawVelocity /= 2;
			}
			if (keyStatus[3] == 1) {
				pitchVelocity += (12 - pitchVelocity) / 2;
			} else if (keyStatus[4] == 1) {
				pitchVelocity += (-12 - pitchVelocity) / 2;
			} else {
				pitchVelocity /= 2;
			}
			followYaw = followYaw + yawVelocity / 2 & ANGLE_MASK;
			followPitch += pitchVelocity / 2;
			if (followPitch < 128) {
				followPitch = 128;
			}
			if (followPitch > 383) {
				followPitch = 383;
			}

			int tileX = followTargetX >> 7;
			int tileY = followTargetY >> 7;
			int targetHeight = world.getTileHeight(followTargetX, followTargetY, plane);
			int maximumDrop = 0;
			if (tileX > 3 && tileY > 3 && tileX < 100 && tileY < 100) {
				for (int x = tileX - 4; x <= tileX + 4; x++) {
					for (int y = tileY - 4; y <= tileY + 4; y++) {
						int effectivePlane = plane;
						if (effectivePlane < 3 && (world.tileFlags[1][x][y] & 2) == 2) {
							effectivePlane++;
						}
						int drop = targetHeight - world.tileHeights[effectivePlane][x][y];
						if (drop > maximumDrop) {
							maximumDrop = drop;
						}
					}
				}
			}
			int desiredScale = maximumDrop * 192;
			if (desiredScale > 0x17f00) {
				desiredScale = 0x17f00;
			}
			if (desiredScale < 32768) {
				desiredScale = 32768;
			}
			if (desiredScale > terrainPitchScale) {
				terrainPitchScale += (desiredScale - terrainPitchScale) / 24;
			} else if (desiredScale < terrainPitchScale) {
				terrainPitchScale += (desiredScale - terrainPitchScale) / 80;
			}
		} catch (Exception ex) {
			Signlink.reportError("glfc_ex " + localPlayer.x + "," + localPlayer.y + "," + followTargetX + ","
					+ followTargetY + "," + regionX + "," + regionY + "," + baseX + "," + baseY);
			throw new RuntimeException("eek");
		}
	}

	/**
	 * Updates cinematic.
	 * 
	 * @param world the world
	 * @param plane the plane
	 */
	public void updateCinematic(WorldState world, int plane) {
		int targetWorldX = positionTileX * 128 + 64;
		int targetWorldY = positionTileY * 128 + 64;
		int targetHeight = world.getTileHeight(targetWorldX, targetWorldY, plane) - positionHeightOffset;
		x = approach(x, targetWorldX, positionBaseSpeed, positionScale);
		height = approach(height, targetHeight, positionBaseSpeed, positionScale);
		y = approach(y, targetWorldY, positionBaseSpeed, positionScale);

		int lookWorldX = lookTileX * 128 + 64;
		int lookWorldY = lookTileY * 128 + 64;
		int lookHeight = world.getTileHeight(lookWorldX, lookWorldY, plane) - lookHeightOffset;
		int deltaX = lookWorldX - x;
		int deltaHeight = lookHeight - height;
		int deltaY = lookWorldY - y;
		int horizontalDistance = (int) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
		int targetPitch = (int) (Math.atan2(deltaHeight, horizontalDistance) * 325.94900000000001D) & ANGLE_MASK;
		int targetYaw = (int) (Math.atan2(deltaX, deltaY) * -325.94900000000001D) & ANGLE_MASK;
		if (targetPitch < 128) {
			targetPitch = 128;
		}
		if (targetPitch > 383) {
			targetPitch = 383;
		}
		pitch = approach(pitch, targetPitch, lookBaseSpeed, lookScale);

		int yawDelta = targetYaw - yaw;
		if (yawDelta > 1024) {
			yawDelta -= 2048;
		}
		if (yawDelta < -1024) {
			yawDelta += 2048;
		}
		if (yawDelta > 0) {
			yaw += lookBaseSpeed + yawDelta * lookScale / 1000;
			yaw &= ANGLE_MASK;
		} else if (yawDelta < 0) {
			yaw -= lookBaseSpeed + (-yawDelta) * lookScale / 1000;
			yaw &= ANGLE_MASK;
		}
		int remaining = targetYaw - yaw;
		if (remaining > 1024) {
			remaining -= 2048;
		}
		if (remaining < -1024) {
			remaining += 2048;
		}
		if (remaining < 0 && yawDelta > 0 || remaining > 0 && yawDelta < 0) {
			yaw = targetYaw;
		}
	}

	/**
	 * Performs approach.
	 * 
	 * @return the resulting int
	 * @param current   the current
	 * @param target    the target
	 * @param baseSpeed the base speed
	 * @param scale     the scale
	 */
	private static int approach(int current, int target, int baseSpeed, int scale) {
		if (current < target) {
			current += baseSpeed + (target - current) * scale / 1000;
			if (current > target) {
				current = target;
			}
		} else if (current > target) {
			current -= baseSpeed + (current - target) * scale / 1000;
			if (current < target) {
				current = target;
			}
		}
		return current;
	}

	/**
	 * Sets cinematic position.
	 * 
	 * @param tileX        the tile x
	 * @param tileY        the tile y
	 * @param heightOffset the height offset
	 * @param baseSpeed    the base speed
	 * @param scale        the scale
	 * @param world        the world
	 * @param plane        the plane
	 */
	public void setCinematicPosition(int tileX, int tileY, int heightOffset, int baseSpeed, int scale, WorldState world,
			int plane) {
		cinematic = true;
		positionTileX = tileX;
		positionTileY = tileY;
		positionHeightOffset = heightOffset;
		positionBaseSpeed = baseSpeed;
		positionScale = scale;
		if (scale >= 100) {
			x = tileX * 128 + 64;
			y = tileY * 128 + 64;
			height = world.getTileHeight(x, y, plane) - heightOffset;
		}
	}

	/**
	 * Sets cinematic look at.
	 * 
	 * @param tileX        the tile x
	 * @param tileY        the tile y
	 * @param heightOffset the height offset
	 * @param baseSpeed    the base speed
	 * @param scale        the scale
	 * @param world        the world
	 * @param plane        the plane
	 */
	public void setCinematicLookAt(int tileX, int tileY, int heightOffset, int baseSpeed, int scale, WorldState world,
			int plane) {
		cinematic = true;
		lookTileX = tileX;
		lookTileY = tileY;
		lookHeightOffset = heightOffset;
		lookBaseSpeed = baseSpeed;
		lookScale = scale;
		if (scale >= 100) {
			int lookX = tileX * 128 + 64;
			int lookY = tileY * 128 + 64;
			int lookZ = world.getTileHeight(lookX, lookY, plane) - heightOffset;
			int dx = lookX - x;
			int dz = lookZ - height;
			int dy = lookY - y;
			int horizontal = (int) Math.sqrt(dx * dx + dy * dy);
			pitch = (int) (Math.atan2(dz, horizontal) * 325.94900000000001D) & ANGLE_MASK;
			yaw = (int) (Math.atan2(dx, dy) * -325.94900000000001D) & ANGLE_MASK;
			if (pitch < 128) {
				pitch = 128;
			}
			if (pitch > 383) {
				pitch = 383;
			}
		}
	}

	/**
	 * Performs stop cinematic.
	 */
	public void stopCinematic() {
		cinematic = false;
		clearShakes();
	}

	/**
	 * Performs configure shake.
	 * 
	 * @param index           the index
	 * @param randomAmplitude the random amplitude
	 * @param sineAmplitude   the sine amplitude
	 * @param frequency       the frequency
	 */
	public void configureShake(int index, int randomAmplitude, int sineAmplitude, int frequency) {
		shakeEnabled[index] = true;
		shakeRandomAmplitude[index] = randomAmplitude;
		shakeSineAmplitude[index] = sineAmplitude;
		shakeFrequency[index] = frequency;
		shakeCycles[index] = 0;
	}

	/**
	 * Clears shakes state.
	 */
	public void clearShakes() {
		for (int loopIndex = 0; loopIndex < 5; loopIndex++) {
			shakeEnabled[loopIndex] = false;
		}
	}

	/**
	 * Performs advance shake cycles.
	 */
	public void advanceShakeCycles() {
		for (int loopIndex = 0; loopIndex < 5; loopIndex++) {
			shakeCycles[loopIndex]++;
		}
	}

	/**
	 * Returns minimum pitch for render.
	 * 
	 * @return the resulting int
	 */
	public int getMinimumPitchForRender() {
		int minimum = followPitch;
		if (terrainPitchScale / 256 > minimum) {
			minimum = terrainPitchScale / 256;
		}
		if (shakeEnabled[4] && shakeSineAmplitude[4] + 128 > minimum) {
			minimum = shakeSineAmplitude[4] + 128;
		}
		return minimum;
	}

	/**
	 * Performs snapshot.
	 * 
	 * @return the resulting snapshot
	 */
	public Snapshot snapshot() {
		return new Snapshot(x, height, y, pitch, yaw);
	}

	/**
	 * Performs restore.
	 * 
	 * @param snapshot the snapshot
	 */
	public void restore(Snapshot snapshot) {
		x = snapshot.x;
		height = snapshot.height;
		y = snapshot.y;
		pitch = snapshot.pitch;
		yaw = snapshot.yaw;
	}

	/**
	 * Applies shake.
	 */
	public void applyShake() {
		for (int index = 0; index < 5; index++) {
			if (!shakeEnabled[index]) {
				continue;
			}
			int offset = (int) ((Math.random() * (shakeRandomAmplitude[index] * 2.0 + 1.0)
					- shakeRandomAmplitude[index])
					+ Math.sin(shakeCycles[index] * (shakeFrequency[index] / 100D)) * shakeSineAmplitude[index]);
			if (index == 0) {
				x += offset;
			} else if (index == 1) {
				height += offset;
			} else if (index == 2) {
				y += offset;
			} else if (index == 3) {
				yaw = yaw + offset & ANGLE_MASK;
			} else {
				pitch += offset;
				if (pitch < 128) {
					pitch = 128;
				}
				if (pitch > 383) {
					pitch = 383;
				}
			}
		}
	}

	/**
	 * Performs position from target.
	 * 
	 * @param targetHeight the target height
	 * @param targetX      the target x
	 * @param pitch        the pitch
	 * @param distance     the distance
	 * @param yaw          the yaw
	 * @param targetY      the target y
	 */
	public void positionFromTarget(int targetHeight, int targetX, int pitch, int distance, int yaw, int targetY) {
		int inversePitch = 2048 - pitch & ANGLE_MASK;
		int inverseYaw = 2048 - yaw & ANGLE_MASK;
		int offsetX = 0;
		int offsetHeight = 0;
		int offsetY = distance;
		if (inversePitch != 0) {
			int sine = Model.SINE[inversePitch];
			int cosine = Model.COSINE[inversePitch];
			int rotatedHeight = offsetHeight * cosine - offsetY * sine >> 16;
			offsetY = offsetHeight * sine + offsetY * cosine >> 16;
			offsetHeight = rotatedHeight;
		}
		if (inverseYaw != 0) {
			int sine = Model.SINE[inverseYaw];
			int cosine = Model.COSINE[inverseYaw];
			int rotatedX = offsetY * sine + offsetX * cosine >> 16;
			offsetY = offsetY * cosine - offsetX * sine >> 16;
			offsetX = rotatedX;
		}
		x = targetX - offsetX;
		height = targetHeight - offsetHeight;
		y = targetY - offsetY;
		this.pitch = pitch;
		this.yaw = yaw;
	}

	/**
	 * Performs select normal render plane.
	 * 
	 * @return the resulting int
	 * @param world        the world
	 * @param currentPlane the current plane
	 * @param localPlayer  the local player
	 * @param outgoing     the outgoing
	 */
	public int selectNormalRenderPlane(WorldState world, int currentPlane, Actor localPlayer, Buffer outgoing) {
		int plane = 3;
		if (pitch < 310) {
			roofProbeCounter++;
			if (roofProbeCounter > 1457) {
				roofProbeCounter = 0;
				outgoing.writeOpcode(244);
				outgoing.writeByte(0);
				int start = outgoing.position;
				outgoing.writeByte(219);
				outgoing.writeShort(37745);
				outgoing.writeByte(61);
				outgoing.writeShort(43756);
				outgoing.writeShort((int) (Math.random() * 65536D));
				outgoing.writeByte((int) (Math.random() * 256D));
				outgoing.writeShort(51171);
				if ((int) (Math.random() * 2D) == 0) {
					outgoing.writeShort(15808);
				}
				outgoing.writeByte(97);
				outgoing.writeByte((int) (Math.random() * 256D));
				outgoing.writeLength(outgoing.position - start);
			}
			int cameraTileX = x >> 7;
			int cameraTileY = y >> 7;
			int playerTileX = localPlayer.x >> 7;
			int playerTileY = localPlayer.y >> 7;
			if ((world.tileFlags[currentPlane][cameraTileX][cameraTileY] & 4) != 0) {
				plane = currentPlane;
			}
			int deltaX = Math.abs(playerTileX - cameraTileX);
			int deltaY = Math.abs(playerTileY - cameraTileY);
			if (deltaX > deltaY) {
				int step = deltaY * 0x10000 / deltaX;
				int accumulator = 32768;
				while (cameraTileX != playerTileX) {
					cameraTileX += cameraTileX < playerTileX ? 1 : -1;
					if ((world.tileFlags[currentPlane][cameraTileX][cameraTileY] & 4) != 0) {
						plane = currentPlane;
					}
					accumulator += step;
					if (accumulator >= 0x10000) {
						accumulator -= 0x10000;
						cameraTileY += cameraTileY < playerTileY ? 1 : cameraTileY > playerTileY ? -1 : 0;
						if ((world.tileFlags[currentPlane][cameraTileX][cameraTileY] & 4) != 0) {
							plane = currentPlane;
						}
					}
				}
			} else {
				int step = deltaX * 0x10000 / deltaY;
				int accumulator = 32768;
				while (cameraTileY != playerTileY) {
					cameraTileY += cameraTileY < playerTileY ? 1 : -1;
					if ((world.tileFlags[currentPlane][cameraTileX][cameraTileY] & 4) != 0) {
						plane = currentPlane;
					}
					accumulator += step;
					if (accumulator >= 0x10000) {
						accumulator -= 0x10000;
						cameraTileX += cameraTileX < playerTileX ? 1 : cameraTileX > playerTileX ? -1 : 0;
						if ((world.tileFlags[currentPlane][cameraTileX][cameraTileY] & 4) != 0) {
							plane = currentPlane;
						}
					}
				}
			}
		}
		if ((world.tileFlags[currentPlane][localPlayer.x >> 7][localPlayer.y >> 7] & 4) != 0) {
			plane = currentPlane;
		}
		return plane;
	}

	/**
	 * Performs select cinematic render plane.
	 * 
	 * @return the resulting int
	 * @param world        the world
	 * @param currentPlane the current plane
	 */
	public int selectCinematicRenderPlane(WorldState world, int currentPlane) {
		int tileHeight = world.getTileHeight(x, y, currentPlane);
		if (tileHeight - height < 800 && (world.tileFlags[currentPlane][x >> 7][y >> 7] & 4) != 0) {
			return currentPlane;
		}
		return 3;
	}

	/**
	 * Performs project.
	 * 
	 * @return the resulting screen point
	 * @param world        the world
	 * @param plane        the plane
	 * @param worldX       the world x
	 * @param heightOffset the height offset
	 * @param worldY       the world y
	 */
	public ScreenPoint project(WorldState world, int plane, int worldX, int heightOffset, int worldY) {
		if (worldX < 128 || worldY < 128 || worldX > 13056 || worldY > 13056) {
			return ScreenPoint.INVISIBLE;
		}
		int projectedHeight = world.getTileHeight(worldX, worldY, plane) - heightOffset;
		int dx = worldX - x;
		projectedHeight -= height;
		int dy = worldY - y;
		int pitchSine = Model.SINE[pitch];
		int pitchCosine = Model.COSINE[pitch];
		int yawSine = Model.SINE[yaw];
		int yawCosine = Model.COSINE[yaw];
		int rotatedX = dy * yawSine + dx * yawCosine >> 16;
		dy = dy * yawCosine - dx * yawSine >> 16;
		dx = rotatedX;
		int screenHeight = projectedHeight * pitchCosine - dy * pitchSine >> 16;
		int depth = projectedHeight * pitchSine + dy * pitchCosine >> 16;
		if (depth < 50) {
			return ScreenPoint.INVISIBLE;
		}
		return new ScreenPoint(rs2.media.Rasterizer3D.centerX + (dx << 9) / depth,
				rs2.media.Rasterizer3D.centerY + (screenHeight << 9) / depth);
	}

	/**
	 * Performs randomize login offsets.
	 */
	public void randomizeLoginOffsets() {
		followOffsetX = (int) (Math.random() * 100D) - 50;
		followOffsetY = (int) (Math.random() * 110D) - 55;
		yawOffset = (int) (Math.random() * 80D) - 40;
		followYaw = (int) (Math.random() * 20D) - 10 & ANGLE_MASK;
	}

	/**
	 * Performs tick random offsets.
	 */
	public void tickRandomOffsets() {
		followOffsetCycle++;
		if (followOffsetCycle > 500) {
			followOffsetCycle = 0;
			int random = (int) (Math.random() * 8D);
			if ((random & 1) == 1) {
				followOffsetX += followOffsetXStep;
			}
			if ((random & 2) == 2) {
				followOffsetY += followOffsetYStep;
			}
			if ((random & 4) == 4) {
				yawOffset += yawOffsetStep;
			}
		}
		if (followOffsetX < -50)
			followOffsetXStep = 2;
		if (followOffsetX > 50)
			followOffsetXStep = -2;
		if (followOffsetY < -55)
			followOffsetYStep = 2;
		if (followOffsetY > 55)
			followOffsetYStep = -2;
		if (yawOffset < -40)
			yawOffsetStep = 1;
		if (yawOffset > 40)
			yawOffsetStep = -1;
	}

	public static final class Snapshot {
		/**
		 * Stores x.
		 */
		private final int x;
		/**
		 * Stores height.
		 */
		private final int height;
		/**
		 * Stores y.
		 */
		private final int y;
		/**
		 * Stores pitch.
		 */
		private final int pitch;
		/**
		 * Stores yaw.
		 */
		private final int yaw;

		/**
		 * Initializes this instance.
		 * 
		 * @param x      the x
		 * @param height the height
		 * @param y      the y
		 * @param pitch  the pitch
		 * @param yaw    the yaw
		 */
		private Snapshot(int x, int height, int y, int pitch, int yaw) {
			this.x = x;
			this.height = height;
			this.y = y;
			this.pitch = pitch;
			this.yaw = yaw;
		}
	}

	public static final class ScreenPoint {
		/**
		 * Stores invisible.
		 */
		public static final ScreenPoint INVISIBLE = new ScreenPoint(-1, -1);
		/**
		 * Stores x.
		 */
		public final int x;
		/**
		 * Stores y.
		 */
		public final int y;

		/**
		 * Initializes this instance.
		 * 
		 * @param x the x
		 * @param y the y
		 */
		public ScreenPoint(int x, int y) {
			this.x = x;
			this.y = y;
		}

		/**
		 * Returns whether visible.
		 * 
		 * @return the resulting boolean
		 */
		public boolean isVisible() {
			return x >= 0;
		}
	}
}