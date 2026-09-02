package rs2.game;

import rs2.game.entity.Actor;
import rs2.media.Angle;
import rs2.media.model.Model;
import rs2.net.Buffer;
import rs2.net.OutgoingPacketOpcode;
import rs2.scene.SceneConstants;
import rs2.scene.TileFlags;
import rs2.sign.Signlink;

/**
 * Owns revision-377 camera position, follow controls, cinematic camera motion,
 * roof-plane selection and camera shake state.
 */
public final class CameraController {

	/** Creates a new camera controller with its default client state. */
	public CameraController() {
	}

	/** Camera-angle units applied for each pixel of middle-mouse movement. */
	private static final int MOUSE_DRAG_SENSITIVITY = 3;

	/** Lowest legal camera pitch in the client angle system. */
	private static final int MIN_PITCH = 128;

	/** Highest legal camera pitch in the client angle system. */
	private static final int MAX_PITCH = 383;

	/** Maximum fine world coordinate accepted by roof/terrain sampling. */
	private static final int MAX_TERRAIN_SAMPLE_COORDINATE = SceneConstants.INTERIOR_MAX_TILE
			* SceneConstants.TILE_SIZE;

	/** Stores the current X. */
	public int x;

	/** Stores the current height. */
	public int height;

	/** Stores the current Y. */
	public int y;

	/** Stores the current pitch. */
	public int pitch = MIN_PITCH;

	/** Stores the current yaw. */
	public int yaw;

	/** Previous fixed-tick render camera X used for interpolation. */
	private int previousRenderX;
	/** Previous fixed-tick render camera height used for interpolation. */
	private int previousRenderHeight;
	/** Previous fixed-tick render camera Y used for interpolation. */
	private int previousRenderY;
	/** Previous fixed-tick render pitch used for interpolation. */
	private int previousRenderPitch = MIN_PITCH;
	/** Previous fixed-tick render yaw used for interpolation. */
	private int previousRenderYaw;
	/** Whether a complete logical camera state is available for interpolation. */
	private boolean renderInterpolationInitialized;

	/** Stores the current follow pitch. */
	public int followPitch = MIN_PITCH;

	/** Stores the current follow yaw. */
	public int followYaw;

	/** Stores the current yaw velocity. */
	private int yawVelocity;

	/** Stores the current pitch velocity. */
	private int pitchVelocity;

	/** Stores the current follow target X. */
	public int followTargetX;

	/** Stores the current follow target Y. */
	public int followTargetY;

	/** Stores the current terrain pitch scale. */
	public int terrainPitchScale;

	/** Stores the current follow offset X. */
	public int followOffsetX;

	/** Stores the current follow offset Y. */
	public int followOffsetY;

	/** Stores the current yaw offset. */
	public int yawOffset;

	/** Stores the current follow offset X step. */
	private int followOffsetXStep = 2;

	/** Stores the current follow offset Y step. */
	private int followOffsetYStep = 2;

	/** Stores the current yaw offset step. */
	private int yawOffsetStep = 1;

	/** Stores the current follow offset cycle. */
	private int followOffsetCycle;

	/**
	 * Whether cinematic.
	 */
	public boolean cinematic;

	/** Stores the current position tile X. */
	private int positionTileX;

	/** Stores the current position tile Y. */
	private int positionTileY;

	/** Stores the current position height offset. */
	private int positionHeightOffset;

	/** Stores the current position base speed. */
	private int positionBaseSpeed;

	/** Stores the current position scale. */
	private int positionScale;

	/** Stores the current look tile X. */
	private int lookTileX;

	/** Stores the current look tile Y. */
	private int lookTileY;

	/** Stores the current look height offset. */
	private int lookHeightOffset;

	/** Stores the current look base speed. */
	private int lookBaseSpeed;

	/** Stores the current look scale. */
	private int lookScale;

	/** Whether shake enabled is enabled or active. */
	private final boolean[] shakeEnabled = new boolean[5];

	/** Stores shake random amplitude values. */
	private final int[] shakeRandomAmplitude = new int[5];

	/** Stores shake sine amplitude values. */
	private final int[] shakeSineAmplitude = new int[5];

	/** Stores shake frequency values. */
	private final int[] shakeFrequency = new int[5];

	/** Stores shake cycles values. */
	private final int[] shakeCycles = new int[5];

	/** Stores the current roof probe counter. */
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
			followYaw = followYaw + yawVelocity / 2 & Angle.MASK;
			followPitch += pitchVelocity / 2;
			if (followPitch < MIN_PITCH) {
				followPitch = MIN_PITCH;
			}
			if (followPitch > MAX_PITCH) {
				followPitch = MAX_PITCH;
			}

			int tileX = followTargetX >> 7;
			int tileY = followTargetY >> 7;
			int targetHeight = world.getTileHeight(followTargetX, followTargetY, plane);
			int maximumDrop = 0;
			if (tileX > 3 && tileY > 3 && tileX < 100 && tileY < 100) {
				for (int x = tileX - 4; x <= tileX + 4; x++) {
					for (int y = tileY - 4; y <= tileY + 4; y++) {
						int effectivePlane = plane;
						if (effectivePlane < 3 && (world.tileFlags[1][x][y] & TileFlags.BRIDGE) != 0) {
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
	 * Rotates the normal follow camera using middle-mouse drag movement.
	 *
	 * @param deltaX horizontal mouse movement
	 * @param deltaY vertical mouse movement
	 */
	public void rotateFollowByMouse(int deltaX, int deltaY) {
		yawVelocity = 0;
		pitchVelocity = 0;

		followYaw = followYaw - deltaX * MOUSE_DRAG_SENSITIVITY & Angle.MASK;
		followPitch += deltaY * MOUSE_DRAG_SENSITIVITY;

		if (followPitch < MIN_PITCH) {
			followPitch = MIN_PITCH;
		}
		if (followPitch > MAX_PITCH) {
			followPitch = MAX_PITCH;
		}
	}

	/**
	 * Updates cinematic.
	 *
	 * @param world the world
	 * @param plane the plane
	 */
	public void updateCinematic(WorldState world, int plane) {
		int targetWorldX = positionTileX * SceneConstants.TILE_SIZE + SceneConstants.TILE_CENTER;
		int targetWorldY = positionTileY * SceneConstants.TILE_SIZE + SceneConstants.TILE_CENTER;
		int targetHeight = world.getTileHeight(targetWorldX, targetWorldY, plane) - positionHeightOffset;
		x = approach(x, targetWorldX, positionBaseSpeed, positionScale);
		height = approach(height, targetHeight, positionBaseSpeed, positionScale);
		y = approach(y, targetWorldY, positionBaseSpeed, positionScale);

		int lookWorldX = lookTileX * SceneConstants.TILE_SIZE + SceneConstants.TILE_CENTER;
		int lookWorldY = lookTileY * SceneConstants.TILE_SIZE + SceneConstants.TILE_CENTER;
		int lookHeight = world.getTileHeight(lookWorldX, lookWorldY, plane) - lookHeightOffset;
		int deltaX = lookWorldX - x;
		int deltaHeight = lookHeight - height;
		int deltaY = lookWorldY - y;
		int horizontalDistance = (int) Math.sqrt(deltaX * deltaX + deltaY * deltaY);
		int targetPitch = (int) (Math.atan2(deltaHeight, horizontalDistance) * Angle.UNITS_PER_RADIAN) & Angle.MASK;
		int targetYaw = (int) (Math.atan2(deltaX, deltaY) * -Angle.UNITS_PER_RADIAN) & Angle.MASK;
		if (targetPitch < MIN_PITCH) {
			targetPitch = MIN_PITCH;
		}
		if (targetPitch > MAX_PITCH) {
			targetPitch = MAX_PITCH;
		}
		pitch = approach(pitch, targetPitch, lookBaseSpeed, lookScale);

		int yawDelta = targetYaw - yaw;
		if (yawDelta > Angle.HALF_TURN) {
			yawDelta -= Angle.FULL_TURN;
		}
		if (yawDelta < -Angle.HALF_TURN) {
			yawDelta += Angle.FULL_TURN;
		}
		if (yawDelta > 0) {
			yaw += lookBaseSpeed + yawDelta * lookScale / 1000;
			yaw &= Angle.MASK;
		} else if (yawDelta < 0) {
			yaw -= lookBaseSpeed + (-yawDelta) * lookScale / 1000;
			yaw &= Angle.MASK;
		}
		int remaining = targetYaw - yaw;
		if (remaining > Angle.HALF_TURN) {
			remaining -= Angle.FULL_TURN;
		}
		if (remaining < -Angle.HALF_TURN) {
			remaining += Angle.FULL_TURN;
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
			x = tileX * SceneConstants.TILE_SIZE + SceneConstants.TILE_CENTER;
			y = tileY * SceneConstants.TILE_SIZE + SceneConstants.TILE_CENTER;
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
			int lookX = tileX * SceneConstants.TILE_SIZE + SceneConstants.TILE_CENTER;
			int lookY = tileY * SceneConstants.TILE_SIZE + SceneConstants.TILE_CENTER;
			int lookZ = world.getTileHeight(lookX, lookY, plane) - heightOffset;
			int dx = lookX - x;
			int dz = lookZ - height;
			int dy = lookY - y;
			int horizontal = (int) Math.sqrt(dx * dx + dy * dy);
			pitch = (int) (Math.atan2(dz, horizontal) * Angle.UNITS_PER_RADIAN) & Angle.MASK;
			yaw = (int) (Math.atan2(dx, dy) * -Angle.UNITS_PER_RADIAN) & Angle.MASK;
			if (pitch < MIN_PITCH) {
				pitch = MIN_PITCH;
			}
			if (pitch > MAX_PITCH) {
				pitch = MAX_PITCH;
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

	/** Captures the current fixed-tick camera before game logic advances it. */
	public void beginLogicCycle() {
		if (!renderInterpolationInitialized) {
			return;
		}
		previousRenderX = x;
		previousRenderHeight = height;
		previousRenderY = y;
		previousRenderPitch = pitch;
		previousRenderYaw = yaw;
	}

	/**
	 * Resolves the normal follow-camera position once per 50 Hz logic update.
	 * Cinematic camera motion already writes the render camera during its logic tick.
	 */
	public void resolveLogicalRenderPosition(WorldState world, Actor localPlayer, int plane) {
		if (cinematic || localPlayer == null) {
			return;
		}
		int renderPitch = getMinimumPitchForRender();
		int renderYaw = followYaw + yawOffset & Angle.MASK;
		positionFromTarget(world.getTileHeight(localPlayer.x, localPlayer.y, plane) - 50, followTargetX, renderPitch,
				600 + renderPitch * 3, renderYaw, followTargetY);
	}

	/** Marks the current logical camera as a complete interpolation endpoint. */
	public void finishLogicCycle() {
		if (renderInterpolationInitialized) {
			return;
		}
		previousRenderX = x;
		previousRenderHeight = height;
		previousRenderY = y;
		previousRenderPitch = pitch;
		previousRenderYaw = yaw;
		renderInterpolationInitialized = true;
	}

	/** Returns a smooth render-only camera between the previous and current 50 Hz states. */
	public Snapshot interpolatedSnapshot(float alpha) {
		if (!renderInterpolationInitialized) {
			return snapshot();
		}
		float clamped = Math.max(0.0f, Math.min(1.0f, alpha));
		return new Snapshot(interpolateLinear(previousRenderX, x, clamped),
				interpolateLinear(previousRenderHeight, height, clamped),
				interpolateLinear(previousRenderY, y, clamped),
				interpolateLinear(previousRenderPitch, pitch, clamped),
				interpolateAngle(previousRenderYaw, yaw, clamped));
	}

	private static int interpolateLinear(int previous, int current, float alpha) {
		return Math.round(previous + (current - previous) * alpha);
	}

	private static int interpolateAngle(int previous, int current, float alpha) {
		int delta = current - previous;
		if (delta > Angle.HALF_TURN) {
			delta -= Angle.FULL_TURN;
		} else if (delta < -Angle.HALF_TURN) {
			delta += Angle.FULL_TURN;
		}
		return previous + Math.round(delta * alpha) & Angle.MASK;
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
		if (shakeEnabled[4] && shakeSineAmplitude[4] + MIN_PITCH > minimum) {
			minimum = shakeSineAmplitude[4] + MIN_PITCH;
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
				yaw = yaw + offset & Angle.MASK;
			} else {
				pitch += offset;
				if (pitch < MIN_PITCH) {
					pitch = MIN_PITCH;
				}
				if (pitch > MAX_PITCH) {
					pitch = MAX_PITCH;
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
		int inversePitch = Angle.FULL_TURN - pitch & Angle.MASK;
		int inverseYaw = Angle.FULL_TURN - yaw & Angle.MASK;
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
				outgoing.writeOpcode(OutgoingPacketOpcode.CAMERA_PROBE);
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
				outgoing.writeLengthByte(outgoing.position - start);
			}
			int cameraTileX = x >> 7;
			int cameraTileY = y >> 7;
			int playerTileX = localPlayer.x >> 7;
			int playerTileY = localPlayer.y >> 7;
			if ((world.tileFlags[currentPlane][cameraTileX][cameraTileY] & TileFlags.ROOF) != 0) {
				plane = currentPlane;
			}
			int deltaX = Math.abs(playerTileX - cameraTileX);
			int deltaY = Math.abs(playerTileY - cameraTileY);
			if (deltaX > deltaY) {
				int step = deltaY * 0x10000 / deltaX;
				int accumulator = 32768;
				while (cameraTileX != playerTileX) {
					cameraTileX += cameraTileX < playerTileX ? 1 : -1;
					if ((world.tileFlags[currentPlane][cameraTileX][cameraTileY] & TileFlags.ROOF) != 0) {
						plane = currentPlane;
					}
					accumulator += step;
					if (accumulator >= 0x10000) {
						accumulator -= 0x10000;
						cameraTileY += cameraTileY < playerTileY ? 1 : cameraTileY > playerTileY ? -1 : 0;
						if ((world.tileFlags[currentPlane][cameraTileX][cameraTileY] & TileFlags.ROOF) != 0) {
							plane = currentPlane;
						}
					}
				}
			} else {
				int step = deltaX * 0x10000 / deltaY;
				int accumulator = 32768;
				while (cameraTileY != playerTileY) {
					cameraTileY += cameraTileY < playerTileY ? 1 : -1;
					if ((world.tileFlags[currentPlane][cameraTileX][cameraTileY] & TileFlags.ROOF) != 0) {
						plane = currentPlane;
					}
					accumulator += step;
					if (accumulator >= 0x10000) {
						accumulator -= 0x10000;
						cameraTileX += cameraTileX < playerTileX ? 1 : cameraTileX > playerTileX ? -1 : 0;
						if ((world.tileFlags[currentPlane][cameraTileX][cameraTileY] & TileFlags.ROOF) != 0) {
							plane = currentPlane;
						}
					}
				}
			}
		}
		if ((world.tileFlags[currentPlane][localPlayer.x >> 7][localPlayer.y >> 7] & TileFlags.ROOF) != 0) {
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
		if (tileHeight - height < 800 && (world.tileFlags[currentPlane][x >> 7][y >> 7] & TileFlags.ROOF) != 0) {
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
		if (worldX < SceneConstants.TILE_SIZE || worldY < SceneConstants.TILE_SIZE
				|| worldX > MAX_TERRAIN_SAMPLE_COORDINATE || worldY > MAX_TERRAIN_SAMPLE_COORDINATE) {
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
		followYaw = (int) (Math.random() * 20D) - 10 & Angle.MASK;
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

	/** Provides snapshot state and behavior. */
	public static final class Snapshot {

		/** Stores the current X. */
		private final int x;

		/** Stores the current height. */
		private final int height;

		/** Stores the current Y. */
		private final int y;

		/** Stores the current pitch. */
		private final int pitch;

		/** Stores the current yaw. */
		private final int yaw;

		/**
		 * Creates a new snapshot.
		 *
		 * @param x      the X coordinate
		 * @param height the height in pixels
		 * @param y      the Y coordinate
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

	/** Provides screen point state and behavior. */
	public static final class ScreenPoint {

		/**
		 * Invisible.
		 *
		 */
		public static final ScreenPoint INVISIBLE = new ScreenPoint(-1, -1);

		/** Stores the current X. */
		public final int x;

		/** Stores the current Y. */
		public final int y;

		/**
		 * Creates a new screen point.
		 *
		 * @param x the X coordinate
		 * @param y the Y coordinate
		 */
		public ScreenPoint(int x, int y) {
			this.x = x;
			this.y = y;
		}

		/**
		 * Returns whether visible.
		 *
		 * @return {@code true} when visible; otherwise {@code false}
		 */
		public boolean isVisible() {
			return x >= 0;
		}
	}
}
