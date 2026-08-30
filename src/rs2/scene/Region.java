package rs2.scene;

import rs2.cache.def.FloorDefinition;
import rs2.cache.def.GameObjectDefinition;
import rs2.cache.ondemand.OnDemandFetcher;
import rs2.media.Rasterizer3D;
import rs2.media.renderable.DynamicObject;
import rs2.media.renderable.Model;
import rs2.media.renderable.Renderable;
import rs2.scene.util.TerrainNoise;
import rs2.net.Buffer;
import rs2.scene.util.CollisionMap;
import rs2.scene.util.TiledUtils;

/** Provides region state and behavior. */
public class Region {

	/** Stores tile flags values. */
	private final byte[][][] tileFlags;

	/** Randomized hue offset applied while building terrain colors. */
	private static int hueOffset = (int) (Math.random() * 17.0D) - 8;

	/** Stores overlay rotations values. */
	private final byte[][][] overlayRotations;

	/** Constant value for wall decoration Y offsets. */
	private static final int[] WALL_DECORATION_Y_OFFSETS = { 0, -1, 0, 1 };

	/** Stores hue sums values. */
	private final int[] hueSums;

	/** Stores saturation sums values. */
	private final int[] saturationSums;

	/** Stores lightness sums values. */
	private final int[] lightnessSums;

	/** Stores hue multiplier sums values. */
	private final int[] hueMultiplierSums;

	/** Stores underlay counts values. */
	private final int[] underlayCounts;

	/** Stores tile heights values. */
	private final int[][][] tileHeights;

	/** Stores the current minimum plane. */
	public static int minimumPlane = 99;

	/** Stores the current width. */
	private final int width;

	/** Stores the current height. */
	private final int height;

	/** Stores overlay shapes values. */
	private final byte[][][] overlayShapes;

	/** Stores overlay IDs values. */
	private final byte[][][] overlayIds;

	/** Constant value for wall orientation flags. */
	private static final int[] WALL_ORIENTATION_FLAGS = { 1, 2, 4, 8 };

	/** Stores underlay IDs values. */
	private final byte[][][] underlayIds;

	/** Constant value for wall decoration X offsets. */
	private static final int[] WALL_DECORATION_X_OFFSETS = { 1, 0, -1, 0 };

	/** Stores the current plane. */
	public static int currentPlane;

	/** Randomized lightness offset applied while building terrain colors. */
	private static int lightnessOffset = (int) (Math.random() * 33.0D) - 16;

	/** Stores shadow intensity values. */
	private final byte[][][] shadowIntensity;

	/** Stores tile lightness values. */
	private final int[][] tileLightness;

	/** Constant value for diagonal wall orientation flags. */
	private static final int[] DIAGONAL_WALL_ORIENTATION_FLAGS = { 16, 32, 64, 128 };

	/** Stores occlusion flags values. */
	private final int[][][] occlusionFlags;
	/**
	 * Whether low memory.
	 */
	public static boolean lowMemory = true;

	/**
	 * Returns the effective render plane after bridge/roof tile flags are applied.
	 *
	 * @param plane the plane
	 * @param x     the x
	 * @param y     the y
	 * @return the effective plane
	 */
	public int getEffectivePlane(int plane, int x, int y) {
		if ((tileFlags[plane][x][y] & 0x8) != 0) {
			return 0;
		}
		if (plane > 0 && (tileFlags[1][x][y] & 0x2) != 0) {
			return plane - 1;
		}
		return plane;
	}

	/**
	 * Performs create renderable.
	 *
	 * @return the resulting renderable
	 * @param definition      the definition
	 * @param objectId        the object id
	 * @param type            the type
	 * @param orientation     the orientation
	 * @param southWestHeight the south west height
	 * @param southEastHeight the south east height
	 * @param northEastHeight the north east height
	 * @param northWestHeight the north west height
	 */
	private static Renderable createRenderable(GameObjectDefinition definition, int objectId, int type, int orientation,
			int southWestHeight, int southEastHeight, int northEastHeight, int northWestHeight) {
		if (definition.animationId == -1 && definition.morphIds == null) {
			return definition.getModelAt(type, orientation, southWestHeight, southEastHeight, northEastHeight,
					northWestHeight, -1);
		}
		return new DynamicObject(objectId, type, orientation, southWestHeight, southEastHeight, northEastHeight,
				northWestHeight, definition.animationId, true);
	}

	/**
	 * Adds one runtime game object to an already-built scene.
	 *
	 * <p>
	 * This is intentionally separate from map-build placement: live updates do not
	 * modify the region shadow/occlusion work arrays or minimum-plane state.
	 * </p>
	 *
	 * @param objectId     the object id
	 * @param heightPlane  the height plane
	 * @param type         the type
	 * @param orientation  the orientation
	 * @param x            the x
	 * @param y            the y
	 * @param scenePlane   the scene plane
	 * @param collisionMap the collision map
	 * @param scene        the scene
	 * @param heights      the heights
	 */
	public static void addLocation(int objectId, int heightPlane, int type, int orientation, int x, int y,
			int scenePlane, CollisionMap collisionMap, Scene scene, int[][][] heights) {
		int southWestHeight = heights[heightPlane][x][y];
		int southEastHeight = heights[heightPlane][x + 1][y];
		int northEastHeight = heights[heightPlane][x + 1][y + 1];
		int northWestHeight = heights[heightPlane][x][y + 1];
		int averageHeight = southWestHeight + southEastHeight + northEastHeight + northWestHeight >> 2;

		GameObjectDefinition definition = GameObjectDefinition.lookup(objectId);
		int uid = x + (y << 7) + (objectId << 14) + 0x40000000;
		if (!definition.interactive) {
			uid += 0x80000000;
		}
		byte config = (byte) ((orientation << 6) + type);

		if (type == 22) {
			Renderable renderable = createRenderable(definition, objectId, 22, orientation, southWestHeight,
					southEastHeight, northEastHeight, northWestHeight);
			scene.addFloorDecoration(scenePlane, x, y, averageHeight, uid, config, renderable);
			if (definition.blocksMovement && definition.interactive) {
				collisionMap.markBlocked(x, y);
			}
			return;
		}

		if (type == 10 || type == 11) {
			Renderable renderable = createRenderable(definition, objectId, 10, orientation, southWestHeight,
					southEastHeight, northEastHeight, northWestHeight);
			if (renderable != null) {
				int extraFlags = type == 11 ? 256 : 0;
				int footprintX = (orientation == 1 || orientation == 3) ? definition.sizeY : definition.sizeX;
				int footprintY = (orientation == 1 || orientation == 3) ? definition.sizeX : definition.sizeY;
				scene.addGameObject(scenePlane, x, y, footprintX, footprintY, averageHeight, renderable, extraFlags,
						uid, config);
			}
			if (definition.blocksMovement) {
				collisionMap.markSolidOccupant(x, y, definition.sizeX, definition.sizeY, orientation,
						definition.blocksProjectiles);
			}
			return;
		}

		if (type >= 12) {
			Renderable renderable = createRenderable(definition, objectId, type, orientation, southWestHeight,
					southEastHeight, northEastHeight, northWestHeight);
			scene.addGameObject(scenePlane, x, y, 1, 1, averageHeight, renderable, 0, uid, config);
			if (definition.blocksMovement) {
				collisionMap.markSolidOccupant(x, y, definition.sizeX, definition.sizeY, orientation,
						definition.blocksProjectiles);
			}
			return;
		}

		if (type == 0) {
			Renderable renderable = createRenderable(definition, objectId, 0, orientation, southWestHeight,
					southEastHeight, northEastHeight, northWestHeight);
			scene.addWall(scenePlane, x, y, averageHeight, uid, config, renderable, null,
					WALL_ORIENTATION_FLAGS[orientation], 0);
			if (definition.blocksMovement) {
				collisionMap.markWall(x, y, type, orientation, definition.blocksProjectiles);
			}
			return;
		}

		if (type == 1) {
			Renderable renderable = createRenderable(definition, objectId, 1, orientation, southWestHeight,
					southEastHeight, northEastHeight, northWestHeight);
			scene.addWall(scenePlane, x, y, averageHeight, uid, config, renderable, null,
					DIAGONAL_WALL_ORIENTATION_FLAGS[orientation], 0);
			if (definition.blocksMovement) {
				collisionMap.markWall(x, y, type, orientation, definition.blocksProjectiles);
			}
			return;
		}

		if (type == 2) {
			int nextOrientation = orientation + 1 & 0x3;
			Renderable primary = createRenderable(definition, objectId, 2, orientation + 4, southWestHeight,
					southEastHeight, northEastHeight, northWestHeight);
			Renderable secondary = createRenderable(definition, objectId, 2, nextOrientation, southWestHeight,
					southEastHeight, northEastHeight, northWestHeight);
			scene.addWall(scenePlane, x, y, averageHeight, uid, config, primary, secondary,
					WALL_ORIENTATION_FLAGS[orientation], WALL_ORIENTATION_FLAGS[nextOrientation]);
			if (definition.blocksMovement) {
				collisionMap.markWall(x, y, type, orientation, definition.blocksProjectiles);
			}
			return;
		}

		if (type == 3) {
			Renderable renderable = createRenderable(definition, objectId, 3, orientation, southWestHeight,
					southEastHeight, northEastHeight, northWestHeight);
			scene.addWall(scenePlane, x, y, averageHeight, uid, config, renderable, null,
					DIAGONAL_WALL_ORIENTATION_FLAGS[orientation], 0);
			if (definition.blocksMovement) {
				collisionMap.markWall(x, y, type, orientation, definition.blocksProjectiles);
			}
			return;
		}

		if (type == 9) {
			Renderable renderable = createRenderable(definition, objectId, 9, orientation, southWestHeight,
					southEastHeight, northEastHeight, northWestHeight);
			scene.addGameObject(scenePlane, x, y, 1, 1, averageHeight, renderable, 0, uid, config);
			if (definition.blocksMovement) {
				collisionMap.markSolidOccupant(x, y, definition.sizeX, definition.sizeY, orientation,
						definition.blocksProjectiles);
			}
			return;
		}

		if (definition.contouredGround) {
			if (orientation == 1) {
				int temporary = northWestHeight;
				northWestHeight = northEastHeight;
				northEastHeight = southEastHeight;
				southEastHeight = southWestHeight;
				southWestHeight = temporary;
			} else if (orientation == 2) {
				int temporary = northWestHeight;
				northWestHeight = southEastHeight;
				southEastHeight = temporary;
				temporary = northEastHeight;
				northEastHeight = southWestHeight;
				southWestHeight = temporary;
			} else if (orientation == 3) {
				int temporary = northWestHeight;
				northWestHeight = southWestHeight;
				southWestHeight = southEastHeight;
				southEastHeight = northEastHeight;
				northEastHeight = temporary;
			}
		}

		Renderable decoration = createRenderable(definition, objectId, 4, 0, southWestHeight, southEastHeight,
				northEastHeight, northWestHeight);
		if (type == 4) {
			scene.addWallDecoration(scenePlane, x, y, averageHeight, 0, 0, orientation * 512, uid, config,
					WALL_ORIENTATION_FLAGS[orientation], decoration);
		} else if (type == 5) {
			int displacement = 16;
			int wallUid = scene.getWallUid(scenePlane, x, y);
			if (wallUid > 0) {
				displacement = GameObjectDefinition.lookup(wallUid >> 14 & 0x7fff).decorDisplacement;
			}
			scene.addWallDecoration(scenePlane, x, y, averageHeight,
					WALL_DECORATION_X_OFFSETS[orientation] * displacement,
					WALL_DECORATION_Y_OFFSETS[orientation] * displacement, orientation * 512, uid, config,
					WALL_ORIENTATION_FLAGS[orientation], decoration);
		} else if (type == 6) {
			scene.addWallDecoration(scenePlane, x, y, averageHeight, 0, 0, orientation, uid, config, 256, decoration);
		} else if (type == 7) {
			scene.addWallDecoration(scenePlane, x, y, averageHeight, 0, 0, orientation, uid, config, 512, decoration);
		} else if (type == 8) {
			scene.addWallDecoration(scenePlane, x, y, averageHeight, 0, 0, orientation, uid, config, 768, decoration);
		}
	}

	/**
	 * Clears one 8x8 instanced terrain chunk while preserving neighboring edge
	 * heights.
	 *
	 * @param plane the plane
	 * @param x     the x
	 * @param y     the y
	 */
	public void clearChunkHeights(int plane, int x, int y) {
		for (int dx = 0; dx < 8; dx++) {
			for (int dy = 0; dy < 8; dy++) {
				tileHeights[plane][x + dx][y + dy] = 0;
			}
		}
		if (x > 0) {
			for (int dy = 1; dy < 8; dy++) {
				tileHeights[plane][x][y + dy] = tileHeights[plane][x - 1][y + dy];
			}
		}
		if (y > 0) {
			for (int dx = 1; dx < 8; dx++) {
				tileHeights[plane][x + dx][y] = tileHeights[plane][x + dx][y - 1];
			}
		}
		if (x > 0 && tileHeights[plane][x - 1][y] != 0) {
			tileHeights[plane][x][y] = tileHeights[plane][x - 1][y];
		} else if (y > 0 && tileHeights[plane][x][y - 1] != 0) {
			tileHeights[plane][x][y] = tileHeights[plane][x][y - 1];
		} else if (x > 0 && y > 0 && tileHeights[plane][x - 1][y - 1] != 0) {
			tileHeights[plane][x][y] = tileHeights[plane][x - 1][y - 1];
		}
	}

	/**
	 * Finalizes terrain collision, lighting, floor tiles, bridges and occluders
	 * after map decoding.
	 *
	 * @param collisionMaps the collision maps
	 * @param scene         the scene
	 */
	public void buildScene(CollisionMap[] collisionMaps, Scene scene) {
		applyBlockedTileCollision(collisionMaps);
		randomizeFloorColorOffsets();

		for (int plane = 0; plane < 4; plane++) {
			calculateTileLightness(plane);
			buildFloorTiles(plane, scene);
			applyEffectivePlanes(plane, scene);
		}

		scene.shadeModels(-50, -10, -50);
		applyBridgeTiles(scene);
		buildOccluders();
	}

	/**
	 * Applies blocked tile collision.
	 *
	 * @param collisionMaps the collision maps
	 */
	private void applyBlockedTileCollision(CollisionMap[] collisionMaps) {
		for (int plane = 0; plane < 4; plane++) {
			for (int x = 0; x < 104; x++) {
				for (int y = 0; y < 104; y++) {
					if ((tileFlags[plane][x][y] & 0x1) == 0) {
						continue;
					}
					int collisionPlane = plane;
					if ((tileFlags[1][x][y] & 0x2) == 2) {
						collisionPlane--;
					}
					if (collisionPlane >= 0) {
						collisionMaps[collisionPlane].markBlocked(x, y);
					}
				}
			}
		}
	}

	/**
	 * Performs randomize floor color offsets.
	 */
	private static void randomizeFloorColorOffsets() {
		hueOffset += (int) (Math.random() * 5.0D) - 2;
		if (hueOffset < -8) {
			hueOffset = -8;
		} else if (hueOffset > 8) {
			hueOffset = 8;
		}

		lightnessOffset += (int) (Math.random() * 5.0D) - 2;
		if (lightnessOffset < -16) {
			lightnessOffset = -16;
		} else if (lightnessOffset > 16) {
			lightnessOffset = 16;
		}
	}

	/**
	 * Performs calculate tile lightness.
	 *
	 * @param plane the plane
	 */
	private void calculateTileLightness(int plane) {
		byte[][] planeShadows = shadowIntensity[plane];
		int baseLightness = 96;
		int contrast = 768;
		int lightX = -50;
		int lightY = -10;
		int lightZ = -50;
		int lightMagnitude = (int) Math.sqrt(lightX * lightX + lightY * lightY + lightZ * lightZ);
		int scaledMagnitude = contrast * lightMagnitude >> 8;

		for (int y = 1; y < height - 1; y++) {
			for (int x = 1; x < width - 1; x++) {
				int gradientX = tileHeights[plane][x + 1][y] - tileHeights[plane][x - 1][y];
				int gradientY = tileHeights[plane][x][y + 1] - tileHeights[plane][x][y - 1];
				int normalLength = (int) Math.sqrt(gradientX * gradientX + 65536 + gradientY * gradientY);
				int normalX = (gradientX << 8) / normalLength;
				int normalY = 65536 / normalLength;
				int normalZ = (gradientY << 8) / normalLength;
				int directionalLight = baseLightness
						+ (lightX * normalX + lightY * normalY + lightZ * normalZ) / scaledMagnitude;
				int shadow = (planeShadows[x - 1][y] >> 2) + (planeShadows[x + 1][y] >> 3)
						+ (planeShadows[x][y - 1] >> 2) + (planeShadows[x][y + 1] >> 3) + (planeShadows[x][y] >> 1);
				tileLightness[x][y] = directionalLight - shadow;
			}
		}
	}

	/**
	 * Builds floor tiles.
	 *
	 * @param plane the plane
	 * @param scene the scene
	 */
	private void buildFloorTiles(int plane, Scene scene) {
		for (int y = 0; y < height; y++) {
			hueSums[y] = 0;
			saturationSums[y] = 0;
			lightnessSums[y] = 0;
			hueMultiplierSums[y] = 0;
			underlayCounts[y] = 0;
		}

		for (int x = -5; x < width + 5; x++) {
			for (int y = 0; y < height; y++) {
				int enteringX = x + 5;
				if (enteringX >= 0 && enteringX < width) {
					int underlayId = underlayIds[plane][enteringX][y] & 0xff;
					if (underlayId > 0) {
						FloorDefinition floor = FloorDefinition.definitions[underlayId - 1];
						hueSums[y] += floor.weightedHue;
						saturationSums[y] += floor.saturation;
						lightnessSums[y] += floor.lightness;
						hueMultiplierSums[y] += floor.hueMultiplier;
						underlayCounts[y]++;
					}
				}

				int leavingX = x - 5;
				if (leavingX >= 0 && leavingX < width) {
					int underlayId = underlayIds[plane][leavingX][y] & 0xff;
					if (underlayId > 0) {
						FloorDefinition floor = FloorDefinition.definitions[underlayId - 1];
						hueSums[y] -= floor.weightedHue;
						saturationSums[y] -= floor.saturation;
						lightnessSums[y] -= floor.lightness;
						hueMultiplierSums[y] -= floor.hueMultiplier;
						underlayCounts[y]--;
					}
				}
			}

			if (x < 1 || x >= width - 1) {
				continue;
			}

			int hueSum = 0;
			int saturationSum = 0;
			int lightnessSum = 0;
			int hueMultiplierSum = 0;
			int underlayCount = 0;

			for (int y = -5; y < height + 5; y++) {
				int enteringY = y + 5;
				if (enteringY >= 0 && enteringY < height) {
					hueSum += hueSums[enteringY];
					saturationSum += saturationSums[enteringY];
					lightnessSum += lightnessSums[enteringY];
					hueMultiplierSum += hueMultiplierSums[enteringY];
					underlayCount += underlayCounts[enteringY];
				}

				int leavingY = y - 5;
				if (leavingY >= 0 && leavingY < height) {
					hueSum -= hueSums[leavingY];
					saturationSum -= saturationSums[leavingY];
					lightnessSum -= lightnessSums[leavingY];
					hueMultiplierSum -= hueMultiplierSums[leavingY];
					underlayCount -= underlayCounts[leavingY];
				}

				if (y < 1 || y >= height - 1 || !shouldBuildTile(plane, x, y)) {
					continue;
				}
				if (plane < minimumPlane) {
					minimumPlane = plane;
				}

				int underlayId = underlayIds[plane][x][y] & 0xff;
				int overlayId = overlayIds[plane][x][y] & 0xff;
				if (underlayId == 0 && overlayId == 0) {
					continue;
				}

				int southWestHeight = tileHeights[plane][x][y];
				int southEastHeight = tileHeights[plane][x + 1][y];
				int northEastHeight = tileHeights[plane][x + 1][y + 1];
				int northWestHeight = tileHeights[plane][x][y + 1];
				int southWestLight = tileLightness[x][y];
				int southEastLight = tileLightness[x + 1][y];
				int northEastLight = tileLightness[x + 1][y + 1];
				int northWestLight = tileLightness[x][y + 1];

				int underlayHsl = -1;
				int randomizedUnderlayHsl = -1;
				if (underlayId > 0) {
					int hue = hueSum * 256 / hueMultiplierSum;
					int saturation = saturationSum / underlayCount;
					int lightness = lightnessSum / underlayCount;
					underlayHsl = FloorDefinition.packHsl(hue, saturation, lightness);
					int randomizedHue = hue + hueOffset & 0xff;
					int randomizedLightness = lightness + lightnessOffset;
					if (randomizedLightness < 0) {
						randomizedLightness = 0;
					} else if (randomizedLightness > 255) {
						randomizedLightness = 255;
					}
					randomizedUnderlayHsl = FloorDefinition.packHsl(randomizedHue, saturation, randomizedLightness);
				}

				if (plane > 0) {
					boolean occludable = true;
					if (underlayId == 0 && overlayShapes[plane][x][y] != 0) {
						occludable = false;
					}
					if (overlayId > 0 && !FloorDefinition.definitions[overlayId - 1].occlude) {
						occludable = false;
					}
					if (occludable && southWestHeight == southEastHeight && southWestHeight == northEastHeight
							&& southWestHeight == northWestHeight) {
						occlusionFlags[plane][x][y] |= 0x924;
					}
				}

				int underlayRgb = 0;
				if (underlayHsl != -1) {
					underlayRgb = Rasterizer3D.HSL_TO_RGB[adjustUnderlayLightness(randomizedUnderlayHsl, 96)];
				}

				if (overlayId == 0) {
					scene.addTile(plane, x, y, 0, 0, -1, southWestHeight, southEastHeight, northEastHeight,
							northWestHeight, adjustUnderlayLightness(underlayHsl, southWestLight),
							adjustUnderlayLightness(underlayHsl, southEastLight),
							adjustUnderlayLightness(underlayHsl, northEastLight),
							adjustUnderlayLightness(underlayHsl, northWestLight), 0, 0, 0, 0, underlayRgb, 0);
					continue;
				}

				int shape = overlayShapes[plane][x][y] + 1;
				byte overlayRotation = overlayRotations[plane][x][y];
				FloorDefinition overlay = FloorDefinition.definitions[overlayId - 1];
				int textureId = overlay.textureId;
				int overlayHsl;
				int overlayRgb;
				if (textureId >= 0) {
					overlayRgb = Rasterizer3D.getAverageTextureColor(textureId);
					overlayHsl = -1;
				} else if (overlay.rgbColor == 0xff00ff) {
					overlayHsl = -2;
					textureId = -1;
					overlayRgb = Rasterizer3D.HSL_TO_RGB[adjustOverlayLightness(overlay.randomizedPackedHsl, 96)];
				} else {
					overlayHsl = FloorDefinition.packHsl(overlay.hue, overlay.saturation, overlay.lightness);
					overlayRgb = Rasterizer3D.HSL_TO_RGB[adjustOverlayLightness(overlay.randomizedPackedHsl, 96)];
				}

				scene.addTile(plane, x, y, shape, overlayRotation, textureId, southWestHeight, southEastHeight,
						northEastHeight, northWestHeight, adjustUnderlayLightness(underlayHsl, southWestLight),
						adjustUnderlayLightness(underlayHsl, southEastLight),
						adjustUnderlayLightness(underlayHsl, northEastLight),
						adjustUnderlayLightness(underlayHsl, northWestLight),
						adjustOverlayLightness(overlayHsl, southWestLight),
						adjustOverlayLightness(overlayHsl, southEastLight),
						adjustOverlayLightness(overlayHsl, northEastLight),
						adjustOverlayLightness(overlayHsl, northWestLight), underlayRgb, overlayRgb);
			}
		}
	}

	/**
	 * Performs should build tile.
	 *
	 * @return {@code true} when should build tile; otherwise {@code false}
	 * @param plane the plane
	 * @param x     the x
	 * @param y     the y
	 */
	private boolean shouldBuildTile(int plane, int x, int y) {
		return !lowMemory || (tileFlags[0][x][y] & 0x2) != 0
				|| ((tileFlags[plane][x][y] & 0x10) == 0 && getEffectivePlane(plane, x, y) == currentPlane);
	}

	/**
	 * Applies effective planes.
	 *
	 * @param plane the plane
	 * @param scene the scene
	 */
	private void applyEffectivePlanes(int plane, Scene scene) {
		for (int y = 1; y < height - 1; y++) {
			for (int x = 1; x < width - 1; x++) {
				scene.setTileLogicHeight(plane, x, y, getEffectivePlane(plane, x, y));
			}
		}
	}

	/**
	 * Applies bridge tiles.
	 *
	 * @param scene the scene
	 */
	private void applyBridgeTiles(Scene scene) {
		for (int x = 0; x < width; x++) {
			for (int y = 0; y < height; y++) {
				if ((tileFlags[1][x][y] & 0x2) == 2) {
					scene.setBridgeMode(x, y);
				}
			}
		}
	}

	/**
	 * Converts accumulated per-tile occlusion bits into the scene controller's
	 * merged clusters.
	 */
	private void buildOccluders() {
		int xWallMask = 1;
		int yWallMask = 2;
		int horizontalMask = 4;

		for (int targetPlane = 0; targetPlane < 4; targetPlane++) {
			if (targetPlane > 0) {
				xWallMask <<= 3;
				yWallMask <<= 3;
				horizontalMask <<= 3;
			}
			for (int sourcePlane = 0; sourcePlane <= targetPlane; sourcePlane++) {
				for (int y = 0; y <= height; y++) {
					for (int x = 0; x <= width; x++) {
						if ((occlusionFlags[sourcePlane][x][y] & xWallMask) != 0) {
							mergeXWallOccluder(sourcePlane, targetPlane, x, y, xWallMask);
						}
						if ((occlusionFlags[sourcePlane][x][y] & yWallMask) != 0) {
							mergeYWallOccluder(sourcePlane, targetPlane, x, y, yWallMask);
						}
						if ((occlusionFlags[sourcePlane][x][y] & horizontalMask) != 0) {
							mergeHorizontalOccluder(sourcePlane, targetPlane, x, y, horizontalMask);
						}
					}
				}
			}
		}
	}

	/**
	 * Performs merge xwall occluder.
	 *
	 * @param sourcePlane the source plane
	 * @param targetPlane the target plane
	 * @param x           the x
	 * @param y           the y
	 * @param mask        the mask
	 */
	private void mergeXWallOccluder(int sourcePlane, int targetPlane, int x, int y, int mask) {
		int minY = y;
		int maxY = y;
		int minPlane = sourcePlane;
		int maxPlane = sourcePlane;
		while (minY > 0 && (occlusionFlags[sourcePlane][x][minY - 1] & mask) != 0) {
			minY--;
		}
		while (maxY < height && (occlusionFlags[sourcePlane][x][maxY + 1] & mask) != 0) {
			maxY++;
		}
		planeSearch: while (minPlane > 0) {
			for (int tileY = minY; tileY <= maxY; tileY++) {
				if ((occlusionFlags[minPlane - 1][x][tileY] & mask) == 0) {
					break planeSearch;
				}
			}
			minPlane--;
		}
		planeSearch: while (maxPlane < targetPlane) {
			for (int tileY = minY; tileY <= maxY; tileY++) {
				if ((occlusionFlags[maxPlane + 1][x][tileY] & mask) == 0) {
					break planeSearch;
				}
			}
			maxPlane++;
		}
		int area = (maxPlane + 1 - minPlane) * (maxY - minY + 1);
		if (area < 8) {
			return;
		}
		int upperZ = tileHeights[maxPlane][x][minY] - 240;
		int lowerZ = tileHeights[minPlane][x][minY];
		Scene.addOccluder(targetPlane, x * 128, upperZ, x * 128, maxY * 128 + 128, lowerZ, minY * 128, 1);
		for (int plane = minPlane; plane <= maxPlane; plane++) {
			for (int tileY = minY; tileY <= maxY; tileY++) {
				occlusionFlags[plane][x][tileY] &= ~mask;
			}
		}
	}

	/**
	 * Performs merge ywall occluder.
	 *
	 * @param sourcePlane the source plane
	 * @param targetPlane the target plane
	 * @param x           the x
	 * @param y           the y
	 * @param mask        the mask
	 */
	private void mergeYWallOccluder(int sourcePlane, int targetPlane, int x, int y, int mask) {
		int minX = x;
		int maxX = x;
		int minPlane = sourcePlane;
		int maxPlane = sourcePlane;
		while (minX > 0 && (occlusionFlags[sourcePlane][minX - 1][y] & mask) != 0) {
			minX--;
		}
		while (maxX < width && (occlusionFlags[sourcePlane][maxX + 1][y] & mask) != 0) {
			maxX++;
		}
		planeSearch: while (minPlane > 0) {
			for (int tileX = minX; tileX <= maxX; tileX++) {
				if ((occlusionFlags[minPlane - 1][tileX][y] & mask) == 0) {
					break planeSearch;
				}
			}
			minPlane--;
		}
		planeSearch: while (maxPlane < targetPlane) {
			for (int tileX = minX; tileX <= maxX; tileX++) {
				if ((occlusionFlags[maxPlane + 1][tileX][y] & mask) == 0) {
					break planeSearch;
				}
			}
			maxPlane++;
		}
		int area = (maxPlane + 1 - minPlane) * (maxX - minX + 1);
		if (area < 8) {
			return;
		}
		int upperZ = tileHeights[maxPlane][minX][y] - 240;
		int lowerZ = tileHeights[minPlane][minX][y];
		Scene.addOccluder(targetPlane, minX * 128, upperZ, maxX * 128 + 128, y * 128, lowerZ, y * 128, 2);
		for (int plane = minPlane; plane <= maxPlane; plane++) {
			for (int tileX = minX; tileX <= maxX; tileX++) {
				occlusionFlags[plane][tileX][y] &= ~mask;
			}
		}
	}

	/**
	 * Performs merge horizontal occluder.
	 *
	 * @param plane       the plane
	 * @param targetPlane the target plane
	 * @param x           the x
	 * @param y           the y
	 * @param mask        the mask
	 */
	private void mergeHorizontalOccluder(int plane, int targetPlane, int x, int y, int mask) {
		int minX = x;
		int maxX = x;
		int minY = y;
		int maxY = y;
		while (minY > 0 && (occlusionFlags[plane][x][minY - 1] & mask) != 0) {
			minY--;
		}
		while (maxY < height && (occlusionFlags[plane][x][maxY + 1] & mask) != 0) {
			maxY++;
		}
		xSearch: while (minX > 0) {
			for (int tileY = minY; tileY <= maxY; tileY++) {
				if ((occlusionFlags[plane][minX - 1][tileY] & mask) == 0) {
					break xSearch;
				}
			}
			minX--;
		}
		xSearch: while (maxX < width) {
			for (int tileY = minY; tileY <= maxY; tileY++) {
				if ((occlusionFlags[plane][maxX + 1][tileY] & mask) == 0) {
					break xSearch;
				}
			}
			maxX++;
		}
		if ((maxX - minX + 1) * (maxY - minY + 1) < 4) {
			return;
		}
		int worldZ = tileHeights[plane][minX][minY];
		Scene.addOccluder(targetPlane, minX * 128, worldZ, maxX * 128 + 128, maxY * 128 + 128, worldZ, minY * 128, 4);
		for (int tileX = minX; tileX <= maxX; tileX++) {
			for (int tileY = minY; tileY <= maxY; tileY++) {
				occlusionFlags[plane][tileX][tileY] &= ~mask;
			}
		}
	}

	/**
	 * Decodes one rotated 8x8 terrain chunk from a 64x64 map square.
	 *
	 * @param data             the data
	 * @param sourcePlane      the source plane
	 * @param sourceX          the source x
	 * @param sourceY          the source y
	 * @param destinationPlane the destination plane
	 * @param destinationX     the destination x
	 * @param destinationY     the destination y
	 * @param rotation         the rotation
	 * @param collisionMaps    the collision maps
	 */
	public void loadTerrainChunk(byte[] data, int sourcePlane, int sourceX, int sourceY, int destinationPlane,
			int destinationX, int destinationY, int rotation, CollisionMap[] collisionMaps) {
		for (int x = 0; x < 8; x++) {
			for (int y = 0; y < 8; y++) {
				int targetX = destinationX + x;
				int targetY = destinationY + y;
				if (targetX > 0 && targetX < 103 && targetY > 0 && targetY < 103) {
					collisionMaps[destinationPlane].flags[targetX][targetY] &= ~0x1000000;
				}
			}
		}

		Buffer buffer = new Buffer(data);
		for (int plane = 0; plane < 4; plane++) {
			for (int x = 0; x < 64; x++) {
				for (int y = 0; y < 64; y++) {
					if (plane == sourcePlane && x >= sourceX && x < sourceX + 8 && y >= sourceY && y < sourceY + 8) {
						int targetX = destinationX + TiledUtils.getRotatedMapChunkX(x & 7, y & 7, rotation);
						int targetY = destinationY + TiledUtils.getRotatedMapChunkY(x & 7, y & 7, rotation);
						decodeTile(buffer, destinationPlane, targetX, targetY, 0, 0, rotation);
					} else {
						decodeTile(buffer, 0, -1, -1, 0, 0, 0);
					}
				}
			}
		}
	}

	/**
	 * Requests every source model referenced by a delta-encoded landscape stream.
	 *
	 * @param buffer  the buffer
	 * @param fetcher the fetcher
	 */
	public static void requestGameObjectModels(Buffer buffer, OnDemandFetcher fetcher) {
		int objectId = -1;
		for (;;) {
			int idDelta = buffer.readUnsignedSmart();
			if (idDelta == 0) {
				return;
			}
			objectId += idDelta;
			GameObjectDefinition.lookup(objectId).requestModels(fetcher);
			for (;;) {
				int positionDelta = buffer.readUnsignedSmart();
				if (positionDelta == 0) {
					break;
				}
				buffer.readUnsignedByte();
			}
		}
	}

	/**
	 * Returns whether the definition has the model required by a placement type.
	 *
	 * @param objectId the object id
	 * @param type     the type
	 * @return whether game object model ready
	 */
	public static boolean isGameObjectModelReady(int objectId, int type) {
		GameObjectDefinition definition = GameObjectDefinition.lookup(objectId);
		if (type == 11) {
			type = 10;
		}
		if (type >= 5 && type <= 8) {
			type = 4;
		}
		return definition.isModelReady(type);
	}

	/**
	 * Performs adjust underlay lightness.
	 *
	 * @return the resulting int
	 * @param packedHsl  the packed hsl
	 * @param brightness the brightness
	 */
	private static int adjustUnderlayLightness(int packedHsl, int brightness) {
		if (packedHsl == -1) {
			return 12345678;
		}
		brightness = brightness * (packedHsl & 0x7f) / 128;
		if (brightness < 2) {
			brightness = 2;
		} else if (brightness > 126) {
			brightness = 126;
		}
		return (packedHsl & 0xff80) + brightness;
	}

	/**
	 * Decodes one rotated 8x8 landscape/object chunk from a 64x64 map square.
	 *
	 * @param data             the data
	 * @param sourcePlane      the source plane
	 * @param sourceX          the source x
	 * @param sourceY          the source y
	 * @param destinationPlane the destination plane
	 * @param destinationX     the destination x
	 * @param destinationY     the destination y
	 * @param rotation         the rotation
	 * @param collisionMaps    the collision maps
	 * @param scene            the scene
	 */
	public void loadObjectChunk(byte[] data, int sourcePlane, int sourceX, int sourceY, int destinationPlane,
			int destinationX, int destinationY, int rotation, CollisionMap[] collisionMaps, Scene scene) {
		Buffer buffer = new Buffer(data);
		int objectId = -1;
		for (;;) {
			int idDelta = buffer.readUnsignedSmart();
			if (idDelta == 0) {
				return;
			}
			objectId += idDelta;
			int packedPosition = 0;
			for (;;) {
				int positionDelta = buffer.readUnsignedSmart();
				if (positionDelta == 0) {
					break;
				}
				packedPosition += positionDelta - 1;
				int localY = packedPosition & 0x3f;
				int localX = packedPosition >> 6 & 0x3f;
				int plane = packedPosition >> 12;
				int config = buffer.readUnsignedByte();
				int type = config >> 2;
				int orientation = config & 0x3;
				if (plane != sourcePlane || localX < sourceX || localX >= sourceX + 8 || localY < sourceY
						|| localY >= sourceY + 8) {
					continue;
				}

				GameObjectDefinition definition = GameObjectDefinition.lookup(objectId);
				int x = destinationX + TiledUtils.getRotatedLandscapeChunkX(localX & 7, localY & 7, definition.sizeX,
						definition.sizeY, orientation, rotation);
				int y = destinationY + TiledUtils.getRotatedLandscapeChunkY(localX & 7, localY & 7, definition.sizeX,
						definition.sizeY, orientation, rotation);
				if (x <= 0 || y <= 0 || x >= 103 || y >= 103) {
					continue;
				}

				int collisionPlane = destinationPlane;
				if ((tileFlags[1][x][y] & 0x2) == 2) {
					collisionPlane--;
				}
				CollisionMap collisionMap = collisionPlane >= 0 ? collisionMaps[collisionPlane] : null;
				placeLocation(objectId, type, orientation + rotation & 0x3, destinationPlane, x, y, collisionMap,
						scene);
			}
		}
	}

	/**
	 * Places one map-loaded object and updates region shadow/occlusion work state.
	 *
	 * @param objectId     the object id
	 * @param type         the type
	 * @param orientation  the orientation
	 * @param plane        the plane
	 * @param x            the x
	 * @param y            the y
	 * @param collisionMap the collision map
	 * @param scene        the scene
	 */
	private void placeLocation(int objectId, int type, int orientation, int plane, int x, int y,
			CollisionMap collisionMap, Scene scene) {
		if (lowMemory && (tileFlags[0][x][y] & 0x2) == 0) {
			if ((tileFlags[plane][x][y] & 0x10) != 0 || getEffectivePlane(plane, x, y) != currentPlane) {
				return;
			}
		}
		if (plane < minimumPlane) {
			minimumPlane = plane;
		}

		int southWestHeight = tileHeights[plane][x][y];
		int southEastHeight = tileHeights[plane][x + 1][y];
		int northEastHeight = tileHeights[plane][x + 1][y + 1];
		int northWestHeight = tileHeights[plane][x][y + 1];
		int averageHeight = southWestHeight + southEastHeight + northEastHeight + northWestHeight >> 2;
		GameObjectDefinition definition = GameObjectDefinition.lookup(objectId);
		int uid = x + (y << 7) + (objectId << 14) + 0x40000000;
		if (!definition.interactive) {
			uid += 0x80000000;
		}
		byte config = (byte) ((orientation << 6) + type);

		if (type == 22) {
			if (!lowMemory || definition.interactive || definition.obstructsGround) {
				Renderable renderable = createRenderable(definition, objectId, 22, orientation, southWestHeight,
						southEastHeight, northEastHeight, northWestHeight);
				scene.addFloorDecoration(plane, x, y, averageHeight, uid, config, renderable);
				if (definition.blocksMovement && definition.interactive && collisionMap != null) {
					collisionMap.markBlocked(x, y);
				}
			}
			return;
		}

		if (type == 10 || type == 11) {
			Renderable renderable = createRenderable(definition, objectId, 10, orientation, southWestHeight,
					southEastHeight, northEastHeight, northWestHeight);
			if (renderable != null) {
				int extraFlags = type == 11 ? 256 : 0;
				int footprintX = (orientation == 1 || orientation == 3) ? definition.sizeY : definition.sizeX;
				int footprintY = (orientation == 1 || orientation == 3) ? definition.sizeX : definition.sizeY;
				boolean inserted = scene.addGameObject(plane, x, y, footprintX, footprintY, averageHeight, renderable,
						extraFlags, uid, config);
				if (inserted && definition.castsShadow) {
					Model shadowModel = renderable instanceof Model ? (Model) renderable
							: definition.getModelAt(10, orientation, southWestHeight, southEastHeight, northEastHeight,
									northWestHeight, -1);
					if (shadowModel != null) {
						int shadow = shadowModel.horizontalRadius / 4;
						if (shadow > 30) {
							shadow = 30;
						}
						for (int dx = 0; dx <= footprintX; dx++) {
							for (int dy = 0; dy <= footprintY; dy++) {
								if (shadow > shadowIntensity[plane][x + dx][y + dy]) {
									shadowIntensity[plane][x + dx][y + dy] = (byte) shadow;
								}
							}
						}
					}
				}
			}
			if (definition.blocksMovement && collisionMap != null) {
				collisionMap.markSolidOccupant(x, y, definition.sizeX, definition.sizeY, orientation,
						definition.blocksProjectiles);
			}
			return;
		}

		if (type >= 12) {
			Renderable renderable = createRenderable(definition, objectId, type, orientation, southWestHeight,
					southEastHeight, northEastHeight, northWestHeight);
			scene.addGameObject(plane, x, y, 1, 1, averageHeight, renderable, 0, uid, config);
			if (type >= 12 && type <= 17 && type != 13 && plane > 0) {
				occlusionFlags[plane][x][y] |= 0x924;
			}
			if (definition.blocksMovement && collisionMap != null) {
				collisionMap.markSolidOccupant(x, y, definition.sizeX, definition.sizeY, orientation,
						definition.blocksProjectiles);
			}
			return;
		}

		if (type == 0) {
			Renderable renderable = createRenderable(definition, objectId, 0, orientation, southWestHeight,
					southEastHeight, northEastHeight, northWestHeight);
			scene.addWall(plane, x, y, averageHeight, uid, config, renderable, null,
					WALL_ORIENTATION_FLAGS[orientation], 0);
			if (orientation == 0) {
				if (definition.castsShadow) {
					shadowIntensity[plane][x][y] = 50;
					shadowIntensity[plane][x][y + 1] = 50;
				}
				if (definition.modelClipped) {
					occlusionFlags[plane][x][y] |= 0x249;
				}
			} else if (orientation == 1) {
				if (definition.castsShadow) {
					shadowIntensity[plane][x][y + 1] = 50;
					shadowIntensity[plane][x + 1][y + 1] = 50;
				}
				if (definition.modelClipped) {
					occlusionFlags[plane][x][y + 1] |= 0x492;
				}
			} else if (orientation == 2) {
				if (definition.castsShadow) {
					shadowIntensity[plane][x + 1][y] = 50;
					shadowIntensity[plane][x + 1][y + 1] = 50;
				}
				if (definition.modelClipped) {
					occlusionFlags[plane][x + 1][y] |= 0x249;
				}
			} else if (orientation == 3) {
				if (definition.castsShadow) {
					shadowIntensity[plane][x][y] = 50;
					shadowIntensity[plane][x + 1][y] = 50;
				}
				if (definition.modelClipped) {
					occlusionFlags[plane][x][y] |= 0x492;
				}
			}
			if (definition.blocksMovement && collisionMap != null) {
				collisionMap.markWall(x, y, type, orientation, definition.blocksProjectiles);
			}
			if (definition.decorDisplacement != 16) {
				scene.displaceWallDecoration(plane, x, y, definition.decorDisplacement);
			}
			return;
		}

		if (type == 1) {
			Renderable renderable = createRenderable(definition, objectId, 1, orientation, southWestHeight,
					southEastHeight, northEastHeight, northWestHeight);
			scene.addWall(plane, x, y, averageHeight, uid, config, renderable, null,
					DIAGONAL_WALL_ORIENTATION_FLAGS[orientation], 0);
			if (definition.castsShadow) {
				if (orientation == 0) {
					shadowIntensity[plane][x][y + 1] = 50;
				} else if (orientation == 1) {
					shadowIntensity[plane][x + 1][y + 1] = 50;
				} else if (orientation == 2) {
					shadowIntensity[plane][x + 1][y] = 50;
				} else if (orientation == 3) {
					shadowIntensity[plane][x][y] = 50;
				}
			}
			if (definition.blocksMovement && collisionMap != null) {
				collisionMap.markWall(x, y, type, orientation, definition.blocksProjectiles);
			}
			return;
		}

		if (type == 2) {
			int nextOrientation = orientation + 1 & 0x3;
			Renderable primary = createRenderable(definition, objectId, 2, orientation + 4, southWestHeight,
					southEastHeight, northEastHeight, northWestHeight);
			Renderable secondary = createRenderable(definition, objectId, 2, nextOrientation, southWestHeight,
					southEastHeight, northEastHeight, northWestHeight);
			scene.addWall(plane, x, y, averageHeight, uid, config, primary, secondary,
					WALL_ORIENTATION_FLAGS[orientation], WALL_ORIENTATION_FLAGS[nextOrientation]);
			if (definition.modelClipped) {
				if (orientation == 0) {
					occlusionFlags[plane][x][y] |= 0x249;
					occlusionFlags[plane][x][y + 1] |= 0x492;
				} else if (orientation == 1) {
					occlusionFlags[plane][x][y + 1] |= 0x492;
					occlusionFlags[plane][x + 1][y] |= 0x249;
				} else if (orientation == 2) {
					occlusionFlags[plane][x + 1][y] |= 0x249;
					occlusionFlags[plane][x][y] |= 0x492;
				} else if (orientation == 3) {
					occlusionFlags[plane][x][y] |= 0x492;
					occlusionFlags[plane][x][y] |= 0x249;
				}
			}
			if (definition.blocksMovement && collisionMap != null) {
				collisionMap.markWall(x, y, type, orientation, definition.blocksProjectiles);
			}
			if (definition.decorDisplacement != 16) {
				scene.displaceWallDecoration(plane, x, y, definition.decorDisplacement);
			}
			return;
		}

		if (type == 3) {
			Renderable renderable = createRenderable(definition, objectId, 3, orientation, southWestHeight,
					southEastHeight, northEastHeight, northWestHeight);
			scene.addWall(plane, x, y, averageHeight, uid, config, renderable, null,
					DIAGONAL_WALL_ORIENTATION_FLAGS[orientation], 0);
			if (definition.castsShadow) {
				if (orientation == 0) {
					shadowIntensity[plane][x][y + 1] = 50;
				} else if (orientation == 1) {
					shadowIntensity[plane][x + 1][y + 1] = 50;
				} else if (orientation == 2) {
					shadowIntensity[plane][x + 1][y] = 50;
				} else if (orientation == 3) {
					shadowIntensity[plane][x][y] = 50;
				}
			}
			if (definition.blocksMovement && collisionMap != null) {
				collisionMap.markWall(x, y, type, orientation, definition.blocksProjectiles);
			}
			return;
		}

		if (type == 9) {
			Renderable renderable = createRenderable(definition, objectId, 9, orientation, southWestHeight,
					southEastHeight, northEastHeight, northWestHeight);
			scene.addGameObject(plane, x, y, 1, 1, averageHeight, renderable, 0, uid, config);
			if (definition.blocksMovement && collisionMap != null) {
				collisionMap.markSolidOccupant(x, y, definition.sizeX, definition.sizeY, orientation,
						definition.blocksProjectiles);
			}
			return;
		}

		if (definition.contouredGround) {
			if (orientation == 1) {
				int temporary = northWestHeight;
				northWestHeight = northEastHeight;
				northEastHeight = southEastHeight;
				southEastHeight = southWestHeight;
				southWestHeight = temporary;
			} else if (orientation == 2) {
				int temporary = northWestHeight;
				northWestHeight = southEastHeight;
				southEastHeight = temporary;
				temporary = northEastHeight;
				northEastHeight = southWestHeight;
				southWestHeight = temporary;
			} else if (orientation == 3) {
				int temporary = northWestHeight;
				northWestHeight = southWestHeight;
				southWestHeight = southEastHeight;
				southEastHeight = northEastHeight;
				northEastHeight = temporary;
			}
		}

		Renderable decoration = createRenderable(definition, objectId, 4, 0, southWestHeight, southEastHeight,
				northEastHeight, northWestHeight);
		if (type == 4) {
			scene.addWallDecoration(plane, x, y, averageHeight, 0, 0, orientation * 512, uid, config,
					WALL_ORIENTATION_FLAGS[orientation], decoration);
		} else if (type == 5) {
			int displacement = 16;
			int wallUid = scene.getWallUid(plane, x, y);
			if (wallUid > 0) {
				displacement = GameObjectDefinition.lookup(wallUid >> 14 & 0x7fff).decorDisplacement;
			}
			scene.addWallDecoration(plane, x, y, averageHeight, WALL_DECORATION_X_OFFSETS[orientation] * displacement,
					WALL_DECORATION_Y_OFFSETS[orientation] * displacement, orientation * 512, uid, config,
					WALL_ORIENTATION_FLAGS[orientation], decoration);
		} else if (type == 6) {
			scene.addWallDecoration(plane, x, y, averageHeight, 0, 0, orientation, uid, config, 256, decoration);
		} else if (type == 7) {
			scene.addWallDecoration(plane, x, y, averageHeight, 0, 0, orientation, uid, config, 512, decoration);
		} else if (type == 8) {
			scene.addWallDecoration(plane, x, y, averageHeight, 0, 0, orientation, uid, config, 768, decoration);
		}
	}

	/**
	 * Decodes a complete 64x64 terrain map square into the local region.
	 *
	 * @param data          the data
	 * @param baseX         the base x
	 * @param baseY         the base y
	 * @param noiseX        the noise x
	 * @param noiseY        the noise y
	 * @param collisionMaps the collision maps
	 */
	public void loadTerrainRegion(byte[] data, int baseX, int baseY, int noiseX, int noiseY,
			CollisionMap[] collisionMaps) {
		for (int plane = 0; plane < 4; plane++) {
			for (int x = 0; x < 64; x++) {
				for (int y = 0; y < 64; y++) {
					int targetX = baseX + x;
					int targetY = baseY + y;
					if (targetX > 0 && targetX < 103 && targetY > 0 && targetY < 103) {
						collisionMaps[plane].flags[targetX][targetY] &= ~0x1000000;
					}
				}
			}
		}
		Buffer buffer = new Buffer(data);
		for (int plane = 0; plane < 4; plane++) {
			for (int x = 0; x < 64; x++) {
				for (int y = 0; y < 64; y++) {
					decodeTile(buffer, plane, baseX + x, baseY + y, noiseX, noiseY, 0);
				}
			}
		}
	}

	/**
	 * Creates a new region.
	 *
	 * @param tileHeights the tile heights
	 * @param tileFlags the tile flags
	 * @param width the width in pixels
	 * @param height the height in pixels
	 */
	public Region(int[][][] tileHeights, byte[][][] tileFlags, int width, int height) {
		minimumPlane = 99;
		this.width = width;
		this.height = height;
		this.tileHeights = tileHeights;
		this.tileFlags = tileFlags;
		underlayIds = new byte[4][width][height];
		overlayIds = new byte[4][width][height];
		overlayShapes = new byte[4][width][height];
		overlayRotations = new byte[4][width][height];
		occlusionFlags = new int[4][width + 1][height + 1];
		shadowIntensity = new byte[4][width + 1][height + 1];
		tileLightness = new int[width + 1][height + 1];
		hueSums = new int[height];
		saturationSums = new int[height];
		lightnessSums = new int[height];
		hueMultiplierSums = new int[height];
		underlayCounts = new int[height];
	}

	/**
	 * Decodes a complete delta-encoded landscape/object map square.
	 *
	 * @param data          the data
	 * @param baseX         the base x
	 * @param baseY         the base y
	 * @param collisionMaps the collision maps
	 * @param scene         the scene
	 */
	public void loadObjectRegion(byte[] data, int baseX, int baseY, CollisionMap[] collisionMaps, Scene scene) {
		Buffer buffer = new Buffer(data);
		int objectId = -1;
		for (;;) {
			int idDelta = buffer.readUnsignedSmart();
			if (idDelta == 0) {
				return;
			}
			objectId += idDelta;
			int packedPosition = 0;
			for (;;) {
				int positionDelta = buffer.readUnsignedSmart();
				if (positionDelta == 0) {
					break;
				}
				packedPosition += positionDelta - 1;
				int localY = packedPosition & 0x3f;
				int localX = packedPosition >> 6 & 0x3f;
				int plane = packedPosition >> 12;
				int config = buffer.readUnsignedByte();
				int type = config >> 2;
				int orientation = config & 0x3;
				int x = baseX + localX;
				int y = baseY + localY;
				if (x <= 0 || y <= 0 || x >= 103 || y >= 103) {
					continue;
				}
				int collisionPlane = plane;
				if ((tileFlags[1][x][y] & 0x2) == 2) {
					collisionPlane--;
				}
				CollisionMap collisionMap = collisionPlane >= 0 ? collisionMaps[collisionPlane] : null;
				placeLocation(objectId, type, orientation, plane, x, y, collisionMap, scene);
			}
		}
	}

	/**
	 * Fills an unavailable terrain rectangle with shadow 127 and copied edge
	 * heights.
	 *
	 * @param x          the x
	 * @param y          the y
	 * @param areaWidth  the area width
	 * @param areaHeight the area height
	 */
	public void fillMissingTerrain(int x, int y, int areaWidth, int areaHeight) {
		for (int tileY = y; tileY <= y + areaHeight; tileY++) {
			for (int tileX = x; tileX <= x + areaWidth; tileX++) {
				if (tileX < 0 || tileX >= width || tileY < 0 || tileY >= height) {
					continue;
				}
				shadowIntensity[0][tileX][tileY] = 127;
				if (tileX == x && tileX > 0) {
					tileHeights[0][tileX][tileY] = tileHeights[0][tileX - 1][tileY];
				}
				if (tileX == x + areaWidth && tileX < width - 1) {
					tileHeights[0][tileX][tileY] = tileHeights[0][tileX + 1][tileY];
				}
				if (tileY == y && tileY > 0) {
					tileHeights[0][tileX][tileY] = tileHeights[0][tileX][tileY - 1];
				}
				if (tileY == y + areaHeight && tileY < height - 1) {
					tileHeights[0][tileX][tileY] = tileHeights[0][tileX][tileY + 1];
				}
			}
		}
	}

	/**
	 * Scans a landscape stream and verifies the first relevant placement of each
	 * object has its models loaded.
	 *
	 * @param data  the data
	 * @param baseX the base x
	 * @param baseY the base y
	 * @return whether are object models ready
	 */
	public static boolean areObjectModelsReady(byte[] data, int baseX, int baseY) {
		boolean ready = true;
		Buffer buffer = new Buffer(data);
		int objectId = -1;
		for (;;) {
			int idDelta = buffer.readUnsignedSmart();
			if (idDelta == 0) {
				return ready;
			}
			objectId += idDelta;
			int packedPosition = 0;
			boolean checkedDefinition = false;
			for (;;) {
				if (checkedDefinition) {
					int positionDelta = buffer.readUnsignedSmart();
					if (positionDelta == 0) {
						break;
					}
					buffer.readUnsignedByte();
					continue;
				}

				int positionDelta = buffer.readUnsignedSmart();
				if (positionDelta == 0) {
					break;
				}
				packedPosition += positionDelta - 1;
				int localY = packedPosition & 0x3f;
				int localX = packedPosition >> 6 & 0x3f;
				int type = buffer.readUnsignedByte() >> 2;
				int x = baseX + localX;
				int y = baseY + localY;
				if (x <= 0 || y <= 0 || x >= 103 || y >= 103) {
					continue;
				}
				GameObjectDefinition definition = GameObjectDefinition.lookup(objectId);
				if (type != 22 || !lowMemory || definition.interactive || definition.obstructsGround) {
					ready &= definition.areAllModelsReady();
					checkedDefinition = true;
				}
			}
		}
	}

	/**
	 * Performs adjust overlay lightness.
	 *
	 * @return the resulting int
	 * @param packedHsl  the packed hsl
	 * @param brightness the brightness
	 */
	private static int adjustOverlayLightness(int packedHsl, int brightness) {
		if (packedHsl == -2) {
			return 12345678;
		}
		if (packedHsl == -1) {
			if (brightness < 0) {
				brightness = 0;
			} else if (brightness > 127) {
				brightness = 127;
			}
			return 127 - brightness;
		}
		brightness = brightness * (packedHsl & 0x7f) / 128;
		if (brightness < 2) {
			brightness = 2;
		} else if (brightness > 126) {
			brightness = 126;
		}
		return (packedHsl & 0xff80) + brightness;
	}

	/**
	 * Decodes one terrain-tile record. Out-of-bounds targets still consume the
	 * complete record.
	 *
	 * @param buffer   the buffer
	 * @param plane    the plane
	 * @param x        the x
	 * @param y        the y
	 * @param noiseX   the noise x
	 * @param noiseY   the noise y
	 * @param rotation the rotation
	 */
	private void decodeTile(Buffer buffer, int plane, int x, int y, int noiseX, int noiseY, int rotation) {
		if (x >= 0 && x < 104 && y >= 0 && y < 104) {
			tileFlags[plane][x][y] = 0;
			for (;;) {
				int opcode = buffer.readUnsignedByte();
				if (opcode == 0) {
					if (plane == 0) {
						tileHeights[0][x][y] = -TerrainNoise.calculateHeight(932731 + x + noiseX, 556238 + y + noiseY)
								* 8;
					} else {
						tileHeights[plane][x][y] = tileHeights[plane - 1][x][y] - 240;
					}
					return;
				}
				if (opcode == 1) {
					int heightOffset = buffer.readUnsignedByte();
					if (heightOffset == 1) {
						heightOffset = 0;
					}
					if (plane == 0) {
						tileHeights[0][x][y] = -heightOffset * 8;
					} else {
						tileHeights[plane][x][y] = tileHeights[plane - 1][x][y] - heightOffset * 8;
					}
					return;
				}
				if (opcode <= 49) {
					overlayIds[plane][x][y] = buffer.readSignedByte();
					overlayShapes[plane][x][y] = (byte) ((opcode - 2) / 4);
					overlayRotations[plane][x][y] = (byte) (opcode - 2 + rotation & 0x3);
				} else if (opcode <= 81) {
					tileFlags[plane][x][y] = (byte) (opcode - 49);
				} else {
					underlayIds[plane][x][y] = (byte) (opcode - 81);
				}
			}
		}

		for (;;) {
			int opcode = buffer.readUnsignedByte();
			if (opcode == 0) {
				return;
			}
			if (opcode == 1) {
				buffer.readUnsignedByte();
				return;
			}
			if (opcode <= 49) {
				buffer.readUnsignedByte();
			}
		}
	}

}
