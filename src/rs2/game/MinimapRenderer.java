package rs2.game;

import rs2.scene.TileFlags;
import rs2.scene.SceneConfig;
import rs2.media.Angle;
import rs2.cache.def.GameObjectDefinition;
import rs2.cache.def.NpcDefinition;
import rs2.collection.NodeDeque;
import rs2.game.entity.Npc;
import rs2.game.entity.Player;
import rs2.media.GraphicsBuffer;
import rs2.media.Rasterizer3D;
import rs2.media.Rasterizer;
import rs2.media.model.Model;
import rs2.media.sprite.ImageRGB;
import rs2.media.sprite.IndexedImage;
import rs2.net.Buffer;
import rs2.net.OutgoingPacketOpcode;
import rs2.scene.SceneConstants;
import rs2.scene.SceneUid;
import rs2.scene.util.CollisionMap;

/**
 * Owns the revision-377 minimap raster, map icons and minimap transform state.
 */
public final class MinimapRenderer {

	/** Width and height of the generated world-map raster. */
	private static final int MAP_IMAGE_SIZE = 512;
	/** Pixels occupied by one scene tile in the generated map raster. */
	private static final int MAP_TILE_PIXELS = 4;
	/** Pixel border around the 104x104 tile map inside the 512x512 raster. */
	private static final int MAP_BORDER_PIXELS = 48;
	/** Pixel offset from the corner of a four-pixel map tile to its center. */
	private static final int MAP_TILE_CENTER_PIXELS = MAP_TILE_PIXELS / 2;
	/** Fine world-coordinate units represented by one minimap pixel. */
	private static final int FINE_UNITS_PER_MAP_PIXEL = SceneConstants.TILE_SIZE / MAP_TILE_PIXELS;

	/** Minimap state that suppresses the map and displays only the compass mask. */
	private static final int STATE_DISABLED = 2;
	/** Base fixed-point scale used by minimap sprite transforms. */
	private static final int TRANSFORM_SCALE = 256;
	/** X coordinate of the visible minimap aperture inside its graphics buffer. */
	private static final int VIEW_X = 25;
	/** Y coordinate of the visible minimap aperture inside its graphics buffer. */
	private static final int VIEW_Y = 5;
	/** Width of the visible minimap aperture. */
	private static final int VIEW_WIDTH = 146;
	/** Height of the visible minimap aperture. */
	private static final int VIEW_HEIGHT = 151;
	/** Horizontal center of the minimap aperture used for click transforms. */
	private static final int VIEW_CENTER_X = 73;
	/** Vertical center of the minimap aperture used for click transforms. */
	private static final int VIEW_CENTER_Y = 75;
	/** Compass sprite dimensions within the minimap buffer. */
	private static final int COMPASS_SIZE = 33;
	/** Center supplied to the masked compass rotation routine. */
	private static final int COMPASS_ROTATION_CENTER = 25;

	/** Number of cycles between attempts to perturb randomized minimap offsets. */
	private static final int RANDOM_OFFSET_INTERVAL = 500;
	/** Maximum absolute randomized minimap rotation offset. */
	private static final int ROTATION_OFFSET_LIMIT = 60;
	/** Lowest randomized minimap zoom offset. */
	private static final int ZOOM_OFFSET_MIN = -20;
	/** Highest randomized minimap zoom offset reached by drift. */
	private static final int ZOOM_OFFSET_MAX = 10;
	/** Number of direction choices used by the offset random walk. */
	private static final int RANDOM_DIRECTION_COUNT = 8;

	/** Cycles allowed before the legacy minimap rebuild keepalive is emitted. */
	private static final int REBUILD_KEEPALIVE_THRESHOLD = 177;
	/** Fixed medium payload paired with the minimap rebuild keepalive packet. */
	private static final int REBUILD_KEEPALIVE_PAYLOAD = 0x288b80;
	/** Number of cardinal directions used when randomizing map-function icons. */
	private static final int MAP_FUNCTION_DIRECTION_COUNT = 4;
	/** Maximum random-walk attempts used to displace a map-function icon. */
	private static final int MAP_FUNCTION_RANDOM_WALK_STEPS = 10;
	/** Maximum tile displacement allowed for randomized map-function icons. */
	private static final int MAP_FUNCTION_RANDOM_RADIUS = 3;

	/** Squared distance beyond which ordinary minimap dots are not drawn. */
	private static final int DOT_MAX_DISTANCE_SQUARED = 80 * 80;
	/** Squared distance beyond which dots are clipped through the minimap mask. */
	private static final int DOT_MASK_DISTANCE_SQUARED = 50 * 50;
	/** Squared distance at which a hint switches to the edge-arrow renderer. */
	private static final int HINT_EDGE_MIN_DISTANCE_SQUARED = 65 * 65;
	/** Maximum squared distance at which a hint edge arrow is rendered. */
	private static final int HINT_EDGE_MAX_DISTANCE_SQUARED = 300 * 300;
	/** Hint-icon blink period in client cycles. */
	private static final int HINT_BLINK_PERIOD = 20;
	/** Number of cycles in each blink period for which the hint is visible. */
	private static final int HINT_BLINK_VISIBLE_CYCLES = HINT_BLINK_PERIOD / 2;
	/** Hint type that targets an NPC. */
	private static final int HINT_NPC = 1;
	/** Hint type that targets an absolute world tile. */
	private static final int HINT_TILE = 2;
	/** Hint type that targets another player. */
	private static final int HINT_PLAYER = 10;

	/** Creates a new minimap renderer with its default client state. */
	public MinimapRenderer() {
	}

	/** Maximum map functions. */
	private static final int MAX_MAP_FUNCTIONS = 1000;

	/** Stores the current map image. */
	public ImageRGB mapImage;

	/** Stores the current state. */
	public int state;

	/** Stores the current rotation offset. */
	public int rotationOffset;

	/** Stores the current zoom offset. */
	public int zoomOffset;

	/** Stores the current rotation step. */
	private int rotationStep = 2;

	/** Stores the current zoom step. */
	private int zoomStep = 1;

	/** Stores the current offset cycle. */
	private int offsetCycle;

	/** Stores the current rebuild keepalive cycle. */
	private int rebuildKeepaliveCycle;

	/**
	 * Number of map function entries.
	 */
	private int mapFunctionCount;

	/** Stores map function icons values. */
	private final ImageRGB[] mapFunctionIcons = new ImageRGB[MAX_MAP_FUNCTIONS];

	/** Stores map function X values. */
	private final int[] mapFunctionX = new int[MAX_MAP_FUNCTIONS];

	/** Stores map function Y values. */
	private final int[] mapFunctionY = new int[MAX_MAP_FUNCTIONS];

	/**
	 * Performs initialize map image.
	 */
	public void initializeMapImage() {
		mapImage = new ImageRGB(MAP_IMAGE_SIZE, MAP_IMAGE_SIZE);
	}

	/**
	 * Clears value state.
	 */
	public void clear() {
		mapImage = null;
		for (int loopIndex = 0; loopIndex < mapFunctionIcons.length; loopIndex++) {
			mapFunctionIcons[loopIndex] = null;
		}
		mapFunctionCount = 0;
	}

	/**
	 * Performs randomize login offsets.
	 */
	public void randomizeLoginOffsets() {
		rotationOffset = (int) (Math.random() * (ROTATION_OFFSET_LIMIT * 2D)) - ROTATION_OFFSET_LIMIT;
		zoomOffset = (int) (Math.random() * 30D) + ZOOM_OFFSET_MIN;
	}

	/**
	 * Performs tick random offsets.
	 */
	public void tickRandomOffsets() {
		offsetCycle++;
		if (offsetCycle > RANDOM_OFFSET_INTERVAL) {
			offsetCycle = 0;
			int random = (int) (Math.random() * RANDOM_DIRECTION_COUNT);
			if ((random & 1) == 1) {
				rotationOffset += rotationStep;
			}
			if ((random & 2) == 2) {
				zoomOffset += zoomStep;
			}
		}
		if (rotationOffset < -ROTATION_OFFSET_LIMIT)
			rotationStep = 2;
		if (rotationOffset > ROTATION_OFFSET_LIMIT)
			rotationStep = -2;
		if (zoomOffset < ZOOM_OFFSET_MIN)
			zoomStep = 1;
		if (zoomOffset > ZOOM_OFFSET_MAX)
			zoomStep = -1;
	}

	/**
	 * Performs rebuild.
	 *
	 * @param world                the world
	 * @param plane                the plane
	 * @param mapSceneSprites      the map scene sprites
	 * @param mapFunctionSprites   the map function sprites
	 * @param sceneBuffer          the scene buffer
	 * @param sceneScanlineOffsets the scene scanline offsets
	 * @param outgoing             the outgoing
	 */
	public void rebuild(WorldState world, int plane, IndexedImage[] mapSceneSprites, ImageRGB[] mapFunctionSprites,
			GraphicsBuffer sceneBuffer, int[] sceneScanlineOffsets, Buffer outgoing) {
		int[] pixels = mapImage.pixels;
		for (int loopIndex = 0; loopIndex < pixels.length; loopIndex++) {
			pixels[loopIndex] = 0;
		}
		for (int y = 1; y < SceneConstants.MAX_TILE_INDEX; y++) {
			int pixelOffset = mapTilePixelOffset(1, y);
			for (int x = 1; x < SceneConstants.MAX_TILE_INDEX; x++) {
				if ((world.tileFlags[plane][x][y] & TileFlags.MINIMAP_EXCLUDED) == 0) {
					world.scene.drawMinimapTile(pixels, pixelOffset, MAP_IMAGE_SIZE, plane, x, y);
				}
				if (plane < SceneConstants.PLANE_COUNT - 1 && (world.tileFlags[plane + 1][x][y] & TileFlags.FORCE_LOWEST_PLANE) != 0) {
					world.scene.drawMinimapTile(pixels, pixelOffset, MAP_IMAGE_SIZE, plane + 1, x, y);
				}
				pixelOffset += MAP_TILE_PIXELS;
			}
		}

		int wallColor = ((238 + (int) (Math.random() * 20D)) - 10 << 16)
				+ ((238 + (int) (Math.random() * 20D)) - 10 << 8) + ((238 + (int) (Math.random() * 20D)) - 10);
		int positiveWallColor = (238 + (int) (Math.random() * 20D)) - 10 << 16;
		mapImage.createRasterizer();
		for (int y = 1; y < SceneConstants.MAX_TILE_INDEX; y++) {
			for (int x = 1; x < SceneConstants.MAX_TILE_INDEX; x++) {
				if ((world.tileFlags[plane][x][y] & TileFlags.MINIMAP_EXCLUDED) == 0) {
					drawMapLocation(world, y, plane, x, positiveWallColor, wallColor, mapSceneSprites);
				}
				if (plane < SceneConstants.PLANE_COUNT - 1 && (world.tileFlags[plane + 1][x][y] & TileFlags.FORCE_LOWEST_PLANE) != 0) {
					drawMapLocation(world, y, plane + 1, x, positiveWallColor, wallColor, mapSceneSprites);
				}
			}
		}
		if (sceneBuffer != null) {
			sceneBuffer.bindRaster();
			Rasterizer3D.scanlineOffsets = sceneScanlineOffsets;
		}

		rebuildKeepaliveCycle++;
		if (rebuildKeepaliveCycle > REBUILD_KEEPALIVE_THRESHOLD) {
			rebuildKeepaliveCycle = 0;
			outgoing.writeOpcode(OutgoingPacketOpcode.MINIMAP_REBUILD_KEEPALIVE);
			outgoing.writeMedium(REBUILD_KEEPALIVE_PAYLOAD);
		}

		mapFunctionCount = 0;
		for (int x = 0; x < SceneConstants.SIZE; x++) {
			for (int y = 0; y < SceneConstants.SIZE; y++) {
				int uid = world.scene.getFloorDecorationUid(plane, x, y);
				if (uid == 0) {
					continue;
				}
				int objectId = uid >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK;
				int functionId = GameObjectDefinition.lookup(objectId).mapFunctionId;
				if (functionId < 0) {
					continue;
				}
				int iconX = x;
				int iconY = y;
				if (functionId != 22 && functionId != 29 && functionId != 34 && functionId != 36 && functionId != 46
						&& functionId != 47 && functionId != 48) {
					int[][] collisionFlags = world.collisionMaps[plane].flags;
					for (int step = 0; step < MAP_FUNCTION_RANDOM_WALK_STEPS; step++) {
						int direction = (int) (Math.random() * MAP_FUNCTION_DIRECTION_COUNT);
						if (direction == 0 && iconX > 0 && iconX > x - MAP_FUNCTION_RANDOM_RADIUS
								&& (collisionFlags[iconX - 1][iconY] & CollisionMap.ACCESS_FROM_WEST_BLOCKED) == 0) {
							iconX--;
						}
						if (direction == 1 && iconX < SceneConstants.MAX_TILE_INDEX && iconX < x + MAP_FUNCTION_RANDOM_RADIUS
								&& (collisionFlags[iconX + 1][iconY] & CollisionMap.ACCESS_FROM_EAST_BLOCKED) == 0) {
							iconX++;
						}
						if (direction == 2 && iconY > 0 && iconY > y - MAP_FUNCTION_RANDOM_RADIUS
								&& (collisionFlags[iconX][iconY - 1] & CollisionMap.ACCESS_FROM_SOUTH_BLOCKED) == 0) {
							iconY--;
						}
						if (direction == 3 && iconY < SceneConstants.MAX_TILE_INDEX && iconY < y + MAP_FUNCTION_RANDOM_RADIUS
								&& (collisionFlags[iconX][iconY + 1] & CollisionMap.ACCESS_FROM_NORTH_BLOCKED) == 0) {
							iconY++;
						}
					}
				}
				mapFunctionIcons[mapFunctionCount] = mapFunctionSprites[functionId];
				mapFunctionX[mapFunctionCount] = iconX;
				mapFunctionY[mapFunctionCount] = iconY;
				mapFunctionCount++;
			}
		}
	}

	/**
	 * Returns the raster offset of the north-west pixel for one scene tile.
	 *
	 * @param tileX the local scene X coordinate
	 * @param tileY the local scene Y coordinate
	 * @return the first pixel offset for the tile
	 */
	private static int mapTilePixelOffset(int tileX, int tileY) {
		int pixelX = MAP_BORDER_PIXELS + tileX * MAP_TILE_PIXELS;
		int pixelY = MAP_BORDER_PIXELS + (SceneConstants.MAX_TILE_INDEX - tileY) * MAP_TILE_PIXELS;
		return pixelX + pixelY * MAP_IMAGE_SIZE;
	}

	/**
	 * Draws map location.
	 *
	 * @param world           the world
	 * @param tileY           the tile y
	 * @param plane           the plane
	 * @param tileX           the tile x
	 * @param positiveColor   the positive color
	 * @param normalColor     the normal color
	 * @param mapSceneSprites the map scene sprites
	 */
	private void drawMapLocation(WorldState world, int tileY, int plane, int tileX, int positiveColor, int normalColor,
			IndexedImage[] mapSceneSprites) {
		int uid = world.scene.getWallUid(plane, tileX, tileY);
		if (uid != 0) {
			int config = world.scene.getConfig(plane, tileX, tileY, uid);
			int orientation = SceneConfig.orientation(config);
			int type = SceneConfig.type(config);
			int color = uid > 0 ? positiveColor : normalColor;
			int[] pixels = mapImage.pixels;
			int offset = mapTilePixelOffset(tileX, tileY);
			GameObjectDefinition definition = GameObjectDefinition.lookup(uid >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK);
			if (definition.mapSceneId != -1) {
				drawMapSceneSprite(definition, mapSceneSprites[definition.mapSceneId], tileX, tileY);
			} else {
				if (type == 0 || type == 2) {
					if (orientation == 0) {
						pixels[offset] = color;
						pixels[offset + MAP_IMAGE_SIZE] = color;
						pixels[offset + MAP_IMAGE_SIZE * 2] = color;
						pixels[offset + MAP_IMAGE_SIZE * 3] = color;
					} else if (orientation == 1) {
						pixels[offset] = color;
						pixels[offset + 1] = color;
						pixels[offset + 2] = color;
						pixels[offset + 3] = color;
					} else if (orientation == 2) {
						pixels[offset + 3] = color;
						pixels[offset + MAP_IMAGE_SIZE + 3] = color;
						pixels[offset + MAP_IMAGE_SIZE * 2 + 3] = color;
						pixels[offset + MAP_IMAGE_SIZE * 3 + 3] = color;
					} else {
						pixels[offset + MAP_IMAGE_SIZE * 3] = color;
						pixels[offset + MAP_IMAGE_SIZE * 3 + 1] = color;
						pixels[offset + MAP_IMAGE_SIZE * 3 + 2] = color;
						pixels[offset + MAP_IMAGE_SIZE * 3 + 3] = color;
					}
				}
				if (type == 3) {
					if (orientation == 0)
						pixels[offset] = color;
					else if (orientation == 1)
						pixels[offset + 3] = color;
					else if (orientation == 2)
						pixels[offset + MAP_IMAGE_SIZE * 3 + 3] = color;
					else
						pixels[offset + MAP_IMAGE_SIZE * 3] = color;
				}
				if (type == 2) {
					if (orientation == 3) {
						pixels[offset] = color;
						pixels[offset + MAP_IMAGE_SIZE] = color;
						pixels[offset + MAP_IMAGE_SIZE * 2] = color;
						pixels[offset + MAP_IMAGE_SIZE * 3] = color;
					} else if (orientation == 0) {
						pixels[offset] = color;
						pixels[offset + 1] = color;
						pixels[offset + 2] = color;
						pixels[offset + 3] = color;
					} else if (orientation == 1) {
						pixels[offset + 3] = color;
						pixels[offset + MAP_IMAGE_SIZE + 3] = color;
						pixels[offset + MAP_IMAGE_SIZE * 2 + 3] = color;
						pixels[offset + MAP_IMAGE_SIZE * 3 + 3] = color;
					} else {
						pixels[offset + MAP_IMAGE_SIZE * 3] = color;
						pixels[offset + MAP_IMAGE_SIZE * 3 + 1] = color;
						pixels[offset + MAP_IMAGE_SIZE * 3 + 2] = color;
						pixels[offset + MAP_IMAGE_SIZE * 3 + 3] = color;
					}
				}
			}
		}

		uid = world.scene.getInteractiveObjectUid(plane, tileX, tileY);
		if (uid != 0) {
			int config = world.scene.getConfig(plane, tileX, tileY, uid);
			int orientation = SceneConfig.orientation(config);
			int type = SceneConfig.type(config);
			GameObjectDefinition definition = GameObjectDefinition.lookup(uid >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK);
			if (definition.mapSceneId != -1) {
				drawMapSceneSprite(definition, mapSceneSprites[definition.mapSceneId], tileX, tileY);
			} else if (type == 9) {
				int color = uid > 0 ? 0xee0000 : 0xeeeeee;
				int[] pixels = mapImage.pixels;
				int offset = mapTilePixelOffset(tileX, tileY);
				if (orientation == 0 || orientation == 2) {
					pixels[offset + MAP_IMAGE_SIZE * 3] = color;
					pixels[offset + MAP_IMAGE_SIZE * 2 + 1] = color;
					pixels[offset + MAP_IMAGE_SIZE + 2] = color;
					pixels[offset + 3] = color;
				} else {
					pixels[offset] = color;
					pixels[offset + MAP_IMAGE_SIZE + 1] = color;
					pixels[offset + MAP_IMAGE_SIZE * 2 + 2] = color;
					pixels[offset + MAP_IMAGE_SIZE * 3 + 3] = color;
				}
			}
		}

		uid = world.scene.getFloorDecorationUid(plane, tileX, tileY);
		if (uid != 0) {
			GameObjectDefinition definition = GameObjectDefinition.lookup(uid >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK);
			if (definition.mapSceneId != -1) {
				drawMapSceneSprite(definition, mapSceneSprites[definition.mapSceneId], tileX, tileY);
			}
		}
	}

	/**
	 * Draws map scene sprite.
	 *
	 * @param definition the definition
	 * @param sprite     the sprite
	 * @param tileX      the tile x
	 * @param tileY      the tile y
	 */
	private static void drawMapSceneSprite(GameObjectDefinition definition, IndexedImage sprite, int tileX, int tileY) {
		if (sprite == null) {
			return;
		}
		int xOffset = (definition.sizeX * MAP_TILE_PIXELS - sprite.width) / 2;
		int yOffset = (definition.sizeY * MAP_TILE_PIXELS - sprite.height) / 2;
		sprite.draw(MAP_BORDER_PIXELS + tileX * MAP_TILE_PIXELS + xOffset, MAP_BORDER_PIXELS + (SceneConstants.SIZE - tileY - definition.sizeY) * MAP_TILE_PIXELS + yOffset);
	}

	/**
	 * Draws value.
	 *
	 * @param world           the world
	 * @param actors          the actors
	 * @param localPlayer     the local player
	 * @param plane           the plane
	 * @param cameraYaw       the camera yaw
	 * @param destinationX    the destination x
	 * @param destinationY    the destination y
	 * @param hintType        the hint type
	 * @param hintNpcIndex    the hint npc index
	 * @param hintTileX       the hint tile x
	 * @param hintTileY       the hint tile y
	 * @param hintPlayerIndex the hint player index
	 * @param gameCycle       the game cycle
	 * @param baseX           the base x
	 * @param baseY           the base y
	 * @param assets          the assets
	 * @param friends         the friends
	 */
	public void draw(WorldState world, ActorSynchronizer actors, Player localPlayer, int plane, int cameraYaw,
			int destinationX, int destinationY, int hintType, int hintNpcIndex, int hintTileX, int hintTileY,
			int hintPlayerIndex, int gameCycle, int baseX, int baseY, Assets assets, FriendLookup friends) {
		assets.minimapBuffer.bindRaster();
		if (state == STATE_DISABLED) {
			byte[] mask = assets.minimapMask.pixels;
			int[] raster = Rasterizer.pixels;
			for (int loopIndex = 0; loopIndex < mask.length; loopIndex++) {
				if (mask[loopIndex] == 0)
					raster[loopIndex] = 0;
			}
			assets.compass.shapeImageToPixels(0, 0, COMPASS_SIZE, COMPASS_SIZE, TRANSFORM_SCALE, COMPASS_ROTATION_CENTER, assets.compassMaskWidths, cameraYaw,
					assets.compassMaskOffsets, COMPASS_ROTATION_CENTER);
			assets.sceneBuffer.bindRaster();
			Rasterizer3D.scanlineOffsets = assets.sceneScanlineOffsets;
			return;
		}

		int rotation = cameraYaw + rotationOffset & Angle.MASK;
		int mapX = MAP_BORDER_PIXELS + localPlayer.x / FINE_UNITS_PER_MAP_PIXEL;
		int mapY = MAP_IMAGE_SIZE - MAP_BORDER_PIXELS - localPlayer.y / FINE_UNITS_PER_MAP_PIXEL;
		mapImage.shapeImageToPixels(VIEW_X, VIEW_Y, VIEW_WIDTH, VIEW_HEIGHT, TRANSFORM_SCALE + zoomOffset, mapX, assets.minimapMaskWidths, rotation,
				assets.minimapMaskOffsets, mapY);
		assets.compass.shapeImageToPixels(0, 0, COMPASS_SIZE, COMPASS_SIZE, TRANSFORM_SCALE, COMPASS_ROTATION_CENTER, assets.compassMaskWidths, cameraYaw,
				assets.compassMaskOffsets, COMPASS_ROTATION_CENTER);

		for (int loopIndex2 = 0; loopIndex2 < mapFunctionCount; loopIndex2++) {
			int dx = mapFunctionX[loopIndex2] * MAP_TILE_PIXELS + MAP_TILE_CENTER_PIXELS - localPlayer.x / FINE_UNITS_PER_MAP_PIXEL;
			int dy = mapFunctionY[loopIndex2] * MAP_TILE_PIXELS + MAP_TILE_CENTER_PIXELS - localPlayer.y / FINE_UNITS_PER_MAP_PIXEL;
			drawOnMinimap(dy, mapFunctionIcons[loopIndex2], dx, cameraYaw, assets);
		}

		for (int x = 0; x < SceneConstants.SIZE; x++) {
			for (int y = 0; y < SceneConstants.SIZE; y++) {
				NodeDeque items = world.groundItems[plane][x][y];
				if (items != null) {
					drawOnMinimap(y * MAP_TILE_PIXELS + MAP_TILE_CENTER_PIXELS - localPlayer.y / FINE_UNITS_PER_MAP_PIXEL, assets.groundItemDot, x * MAP_TILE_PIXELS + MAP_TILE_CENTER_PIXELS - localPlayer.x / FINE_UNITS_PER_MAP_PIXEL,
							cameraYaw, assets);
				}
			}
		}

		for (int loopIndex3 = 0; loopIndex3 < actors.npcCount; loopIndex3++) {
			Npc npc = actors.npcs[actors.npcIndices[loopIndex3]];
			if (npc != null && npc.isVisible()) {
				NpcDefinition definition = npc.definition;
				if (definition.morphIds != null)
					definition = definition.transform();
				if (definition != null && definition.visibleOnMinimap && definition.clickable) {
					drawOnMinimap(npc.y / FINE_UNITS_PER_MAP_PIXEL - localPlayer.y / FINE_UNITS_PER_MAP_PIXEL, assets.npcDot, npc.x / FINE_UNITS_PER_MAP_PIXEL - localPlayer.x / FINE_UNITS_PER_MAP_PIXEL,
							cameraYaw, assets);
				}
			}
		}

		for (int loopIndex4 = 0; loopIndex4 < actors.playerCount; loopIndex4++) {
			Player player = actors.players[actors.playerIndices[loopIndex4]];
			if (player == null || !player.isVisible())
				continue;
			int dx = player.x / FINE_UNITS_PER_MAP_PIXEL - localPlayer.x / FINE_UNITS_PER_MAP_PIXEL;
			int dy = player.y / FINE_UNITS_PER_MAP_PIXEL - localPlayer.y / FINE_UNITS_PER_MAP_PIXEL;
			boolean friend = friends.isFriend(player.name);
			boolean teammate = localPlayer.team != 0 && player.team != 0 && localPlayer.team == player.team;
			drawOnMinimap(dy, friend ? assets.friendDot : teammate ? assets.teamDot : assets.playerDot, dx, cameraYaw,
					assets);
		}

		if (hintType != 0 && gameCycle % HINT_BLINK_PERIOD < HINT_BLINK_VISIBLE_CYCLES) {
			if (hintType == HINT_NPC && hintNpcIndex >= 0 && hintNpcIndex < actors.npcs.length) {
				Npc npc = actors.npcs[hintNpcIndex];
				if (npc != null) {
					drawHint(npc.y / FINE_UNITS_PER_MAP_PIXEL - localPlayer.y / FINE_UNITS_PER_MAP_PIXEL, assets.hintMarker, npc.x / FINE_UNITS_PER_MAP_PIXEL - localPlayer.x / FINE_UNITS_PER_MAP_PIXEL,
							cameraYaw, assets);
				}
			} else if (hintType == HINT_TILE) {
				drawHint((hintTileY - baseY) * MAP_TILE_PIXELS + MAP_TILE_CENTER_PIXELS - localPlayer.y / FINE_UNITS_PER_MAP_PIXEL, assets.hintMarker,
						(hintTileX - baseX) * MAP_TILE_PIXELS + MAP_TILE_CENTER_PIXELS - localPlayer.x / FINE_UNITS_PER_MAP_PIXEL, cameraYaw, assets);
			} else if (hintType == HINT_PLAYER && hintPlayerIndex >= 0 && hintPlayerIndex < actors.players.length) {
				Player player = actors.players[hintPlayerIndex];
				if (player != null) {
					drawHint(player.y / FINE_UNITS_PER_MAP_PIXEL - localPlayer.y / FINE_UNITS_PER_MAP_PIXEL, assets.hintMarker, player.x / FINE_UNITS_PER_MAP_PIXEL - localPlayer.x / FINE_UNITS_PER_MAP_PIXEL,
							cameraYaw, assets);
				}
			}
		}
		if (destinationX != 0) {
			drawOnMinimap(destinationY * MAP_TILE_PIXELS + MAP_TILE_CENTER_PIXELS - localPlayer.y / FINE_UNITS_PER_MAP_PIXEL, assets.destinationMarker,
					destinationX * MAP_TILE_PIXELS + MAP_TILE_CENTER_PIXELS - localPlayer.x / FINE_UNITS_PER_MAP_PIXEL, cameraYaw, assets);
		}
		Rasterizer.drawFilledRectangle(97, 78, 3, 3, 0xffffff);
		assets.sceneBuffer.bindRaster();
		Rasterizer3D.scanlineOffsets = assets.sceneScanlineOffsets;
	}

	/**
	 * Draws hint.
	 *
	 * @param dy        the dy
	 * @param sprite    the sprite
	 * @param dx        the dx
	 * @param cameraYaw the camera yaw
	 * @param assets    the assets
	 */
	private void drawHint(int dy, ImageRGB sprite, int dx, int cameraYaw, Assets assets) {
		int distance = dx * dx + dy * dy;
		if (distance > HINT_EDGE_MIN_DISTANCE_SQUARED && distance < HINT_EDGE_MAX_DISTANCE_SQUARED) {
			int angle = cameraYaw + rotationOffset & Angle.MASK;
			int sine = Model.SINE[angle] * TRANSFORM_SCALE / (zoomOffset + TRANSFORM_SCALE);
			int cosine = Model.COSINE[angle] * TRANSFORM_SCALE / (zoomOffset + TRANSFORM_SCALE);
			int rotatedX = dy * sine + dx * cosine >> 16;
			int rotatedY = dy * cosine - dx * sine >> 16;
			double radians = Math.atan2(rotatedX, rotatedY);
			int x = (int) (Math.sin(radians) * 63D);
			int y = (int) (Math.cos(radians) * 57D);
			assets.edgeArrow.drawRotated((94 + x + 4) - 10, 83 - y - 20, 15, 15, 20, 20, 256, radians);
		} else {
			drawOnMinimap(dy, sprite, dx, cameraYaw, assets);
		}
	}

	/**
	 * Draws on minimap.
	 *
	 * @param dy        the dy
	 * @param sprite    the sprite
	 * @param dx        the dx
	 * @param cameraYaw the camera yaw
	 * @param assets    the assets
	 */
	private void drawOnMinimap(int dy, ImageRGB sprite, int dx, int cameraYaw, Assets assets) {
		if (sprite == null)
			return;
		int angle = cameraYaw + rotationOffset & Angle.MASK;
		int distance = dx * dx + dy * dy;
		if (distance > DOT_MAX_DISTANCE_SQUARED)
			return;
		int sine = Model.SINE[angle] * TRANSFORM_SCALE / (zoomOffset + TRANSFORM_SCALE);
		int cosine = Model.COSINE[angle] * TRANSFORM_SCALE / (zoomOffset + TRANSFORM_SCALE);
		int rotatedX = dy * sine + dx * cosine >> 16;
		int rotatedY = dy * cosine - dx * sine >> 16;
		if (distance > DOT_MASK_DISTANCE_SQUARED) {
			sprite.drawTo(assets.minimapMask, ((94 + rotatedX) - sprite.maxWidth / 2) + 4,
					83 - rotatedY - sprite.maxHeight / 2 - 4);
		} else {
			sprite.drawImage(((94 + rotatedX) - sprite.maxWidth / 2) + 4, 83 - rotatedY - sprite.maxHeight / 2 - 4);
		}
	}

	/**
	 * Performs transform click.
	 *
	 * @return the resulting click
	 * @param clickX      minimap-local click X coordinate
	 * @param clickY      minimap-local click Y coordinate
	 * @param localPlayer the local player
	 * @param cameraYaw   the camera yaw
	 */
	public Click transformClick(int clickX, int clickY, Player localPlayer, int cameraYaw) {
		int x = clickX - VIEW_X;
		int y = clickY - VIEW_Y;
		if (x < 0 || y < 0 || x >= VIEW_WIDTH || y >= VIEW_HEIGHT)
			return null;
		x -= VIEW_CENTER_X;
		y -= VIEW_CENTER_Y;
		int angle = cameraYaw + rotationOffset & Angle.MASK;
		int sine = Rasterizer3D.SINE[angle] * (zoomOffset + TRANSFORM_SCALE) >> 8;
		int cosine = Rasterizer3D.COSINE[angle] * (zoomOffset + TRANSFORM_SCALE) >> 8;
		int worldOffsetX = y * sine + x * cosine >> 11;
		int worldOffsetY = y * cosine - x * sine >> 11;
		int tileX = localPlayer.x + worldOffsetX >> 7;
		int tileY = localPlayer.y - worldOffsetY >> 7;
		return new Click(x, y, tileX, tileY);
	}

	/** Provides click state and behavior. */
	public static final class Click {

		/** Stores the current local X. */
		public final int localX;

		/** Stores the current local Y. */
		public final int localY;

		/** Stores the current tile X. */
		public final int tileX;

		/** Stores the current tile Y. */
		public final int tileY;

		/**
		 * Creates a new click.
		 *
		 * @param localX the local X
		 * @param localY the local Y
		 * @param tileX the tile X
		 * @param tileY the tile Y
		 */
		private Click(int localX, int localY, int tileX, int tileY) {
			this.localX = localX;
			this.localY = localY;
			this.tileX = tileX;
			this.tileY = tileY;
		}
	}

	/** Provides friend lookup state and behavior. */
	public interface FriendLookup {
		/**
		 * Returns whether friend.
		 *
		 * @return {@code true} when friend; otherwise {@code false}
		 * @param name the name
		 */
		boolean isFriend(String name);
	}

	/** Mutable sprite and buffer assets consumed by the minimap renderer. */
	public static final
	class Assets {

		/** Creates a new assets with its default client state. */
		public Assets() {
		}

		/** Stores the current minimap buffer. */
		public GraphicsBuffer minimapBuffer;

		/** Stores the current scene buffer. */
		public GraphicsBuffer sceneBuffer;

		/** Stores the current minimap mask. */
		public IndexedImage minimapMask;

		/** Stores the current compass. */
		public ImageRGB compass;

		/** Stores compass mask widths values. */
		public int[] compassMaskWidths;

		/** Stores compass mask offsets values. */
		public int[] compassMaskOffsets;

		/** Stores minimap mask widths values. */
		public int[] minimapMaskWidths;

		/** Stores minimap mask offsets values. */
		public int[] minimapMaskOffsets;

		/** Stores scene scanline offsets values. */
		public int[] sceneScanlineOffsets;

		/** Stores the current ground item dot. */
		public ImageRGB groundItemDot;

		/** Stores the current NPC dot. */
		public ImageRGB npcDot;

		/** Stores the current player dot. */
		public ImageRGB playerDot;

		/** Stores the current friend dot. */
		public ImageRGB friendDot;

		/** Stores the current team dot. */
		public ImageRGB teamDot;

		/** Stores the current hint marker. */
		public ImageRGB hintMarker;

		/** Stores the current destination marker. */
		public ImageRGB destinationMarker;

		/** Stores the current edge arrow. */
		public ImageRGB edgeArrow;
	}
}
