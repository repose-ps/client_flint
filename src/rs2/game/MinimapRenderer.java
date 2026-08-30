package rs2.game;

import rs2.cache.def.GameObjectDefinition;
import rs2.cache.def.NpcDefinition;
import rs2.cache.media.ImageRGB;
import rs2.cache.media.IndexedImage;
import rs2.collection.NodeDeque;
import rs2.media.GraphicsBuffer;
import rs2.media.Rasterizer;
import rs2.media.Rasterizer3D;
import rs2.media.renderable.Model;
import rs2.media.renderable.Npc;
import rs2.media.renderable.Player;
import rs2.net.Buffer;

/**
 * Owns the revision-377 minimap raster, map icons and minimap transform state.
 */
public final class MinimapRenderer {

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
		mapImage = new ImageRGB(512, 512);
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
		rotationOffset = (int) (Math.random() * 120D) - 60;
		zoomOffset = (int) (Math.random() * 30D) - 20;
	}

	/**
	 * Performs tick random offsets.
	 */
	public void tickRandomOffsets() {
		offsetCycle++;
		if (offsetCycle > 500) {
			offsetCycle = 0;
			int random = (int) (Math.random() * 8D);
			if ((random & 1) == 1) {
				rotationOffset += rotationStep;
			}
			if ((random & 2) == 2) {
				zoomOffset += zoomStep;
			}
		}
		if (rotationOffset < -60)
			rotationStep = 2;
		if (rotationOffset > 60)
			rotationStep = -2;
		if (zoomOffset < -20)
			zoomStep = 1;
		if (zoomOffset > 10)
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
		for (int y = 1; y < 103; y++) {
			int pixelOffset = 24628 + (103 - y) * 512 * 4;
			for (int x = 1; x < 103; x++) {
				if ((world.tileFlags[plane][x][y] & 0x18) == 0) {
					world.scene.drawMinimapTile(pixels, pixelOffset, 512, plane, x, y);
				}
				if (plane < 3 && (world.tileFlags[plane + 1][x][y] & 8) != 0) {
					world.scene.drawMinimapTile(pixels, pixelOffset, 512, plane + 1, x, y);
				}
				pixelOffset += 4;
			}
		}

		int wallColor = ((238 + (int) (Math.random() * 20D)) - 10 << 16)
				+ ((238 + (int) (Math.random() * 20D)) - 10 << 8) + ((238 + (int) (Math.random() * 20D)) - 10);
		int positiveWallColor = (238 + (int) (Math.random() * 20D)) - 10 << 16;
		mapImage.createRasterizer();
		for (int y = 1; y < 103; y++) {
			for (int x = 1; x < 103; x++) {
				if ((world.tileFlags[plane][x][y] & 0x18) == 0) {
					drawMapLocation(world, y, plane, x, positiveWallColor, wallColor, mapSceneSprites);
				}
				if (plane < 3 && (world.tileFlags[plane + 1][x][y] & 8) != 0) {
					drawMapLocation(world, y, plane + 1, x, positiveWallColor, wallColor, mapSceneSprites);
				}
			}
		}
		if (sceneBuffer != null) {
			sceneBuffer.bindRaster();
			Rasterizer3D.scanlineOffsets = sceneScanlineOffsets;
		}

		rebuildKeepaliveCycle++;
		if (rebuildKeepaliveCycle > 177) {
			rebuildKeepaliveCycle = 0;
			outgoing.writeOpcode(173);
			outgoing.writeMedium(0x288b80);
		}

		mapFunctionCount = 0;
		for (int x = 0; x < 104; x++) {
			for (int y = 0; y < 104; y++) {
				int uid = world.scene.getFloorDecorationUid(plane, x, y);
				if (uid == 0) {
					continue;
				}
				int objectId = uid >> 14 & 0x7fff;
				int functionId = GameObjectDefinition.lookup(objectId).mapFunctionId;
				if (functionId < 0) {
					continue;
				}
				int iconX = x;
				int iconY = y;
				if (functionId != 22 && functionId != 29 && functionId != 34 && functionId != 36 && functionId != 46
						&& functionId != 47 && functionId != 48) {
					int[][] collisionFlags = world.collisionMaps[plane].flags;
					for (int step = 0; step < 10; step++) {
						int direction = (int) (Math.random() * 4D);
						if (direction == 0 && iconX > 0 && iconX > x - 3
								&& (collisionFlags[iconX - 1][iconY] & 0x1280108) == 0) {
							iconX--;
						}
						if (direction == 1 && iconX < 103 && iconX < x + 3
								&& (collisionFlags[iconX + 1][iconY] & 0x1280180) == 0) {
							iconX++;
						}
						if (direction == 2 && iconY > 0 && iconY > y - 3
								&& (collisionFlags[iconX][iconY - 1] & 0x1280102) == 0) {
							iconY--;
						}
						if (direction == 3 && iconY < 103 && iconY < y + 3
								&& (collisionFlags[iconX][iconY + 1] & 0x1280120) == 0) {
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
			int orientation = config >> 6 & 3;
			int type = config & 0x1f;
			int color = uid > 0 ? positiveColor : normalColor;
			int[] pixels = mapImage.pixels;
			int offset = 24624 + tileX * 4 + (103 - tileY) * 512 * 4;
			GameObjectDefinition definition = GameObjectDefinition.lookup(uid >> 14 & 0x7fff);
			if (definition.mapSceneId != -1) {
				drawMapSceneSprite(definition, mapSceneSprites[definition.mapSceneId], tileX, tileY);
			} else {
				if (type == 0 || type == 2) {
					if (orientation == 0) {
						pixels[offset] = color;
						pixels[offset + 512] = color;
						pixels[offset + 1024] = color;
						pixels[offset + 1536] = color;
					} else if (orientation == 1) {
						pixels[offset] = color;
						pixels[offset + 1] = color;
						pixels[offset + 2] = color;
						pixels[offset + 3] = color;
					} else if (orientation == 2) {
						pixels[offset + 3] = color;
						pixels[offset + 515] = color;
						pixels[offset + 1027] = color;
						pixels[offset + 1539] = color;
					} else {
						pixels[offset + 1536] = color;
						pixels[offset + 1537] = color;
						pixels[offset + 1538] = color;
						pixels[offset + 1539] = color;
					}
				}
				if (type == 3) {
					if (orientation == 0)
						pixels[offset] = color;
					else if (orientation == 1)
						pixels[offset + 3] = color;
					else if (orientation == 2)
						pixels[offset + 1539] = color;
					else
						pixels[offset + 1536] = color;
				}
				if (type == 2) {
					if (orientation == 3) {
						pixels[offset] = color;
						pixels[offset + 512] = color;
						pixels[offset + 1024] = color;
						pixels[offset + 1536] = color;
					} else if (orientation == 0) {
						pixels[offset] = color;
						pixels[offset + 1] = color;
						pixels[offset + 2] = color;
						pixels[offset + 3] = color;
					} else if (orientation == 1) {
						pixels[offset + 3] = color;
						pixels[offset + 515] = color;
						pixels[offset + 1027] = color;
						pixels[offset + 1539] = color;
					} else {
						pixels[offset + 1536] = color;
						pixels[offset + 1537] = color;
						pixels[offset + 1538] = color;
						pixels[offset + 1539] = color;
					}
				}
			}
		}

		uid = world.scene.getInteractiveObjectUid(plane, tileX, tileY);
		if (uid != 0) {
			int config = world.scene.getConfig(plane, tileX, tileY, uid);
			int orientation = config >> 6 & 3;
			int type = config & 0x1f;
			GameObjectDefinition definition = GameObjectDefinition.lookup(uid >> 14 & 0x7fff);
			if (definition.mapSceneId != -1) {
				drawMapSceneSprite(definition, mapSceneSprites[definition.mapSceneId], tileX, tileY);
			} else if (type == 9) {
				int color = uid > 0 ? 0xee0000 : 0xeeeeee;
				int[] pixels = mapImage.pixels;
				int offset = 24624 + tileX * 4 + (103 - tileY) * 512 * 4;
				if (orientation == 0 || orientation == 2) {
					pixels[offset + 1536] = color;
					pixels[offset + 1025] = color;
					pixels[offset + 514] = color;
					pixels[offset + 3] = color;
				} else {
					pixels[offset] = color;
					pixels[offset + 513] = color;
					pixels[offset + 1026] = color;
					pixels[offset + 1539] = color;
				}
			}
		}

		uid = world.scene.getFloorDecorationUid(plane, tileX, tileY);
		if (uid != 0) {
			GameObjectDefinition definition = GameObjectDefinition.lookup(uid >> 14 & 0x7fff);
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
		int xOffset = (definition.sizeX * 4 - sprite.width) / 2;
		int yOffset = (definition.sizeY * 4 - sprite.height) / 2;
		sprite.draw(48 + tileX * 4 + xOffset, 48 + (104 - tileY - definition.sizeY) * 4 + yOffset);
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
		if (state == 2) {
			byte[] mask = assets.minimapMask.pixels;
			int[] raster = Rasterizer.pixels;
			for (int loopIndex = 0; loopIndex < mask.length; loopIndex++) {
				if (mask[loopIndex] == 0)
					raster[loopIndex] = 0;
			}
			assets.compass.shapeImageToPixels(0, 0, 33, 33, 256, 25, assets.compassMaskWidths, cameraYaw,
					assets.compassMaskOffsets, 25);
			assets.sceneBuffer.bindRaster();
			Rasterizer3D.scanlineOffsets = assets.sceneScanlineOffsets;
			return;
		}

		int rotation = cameraYaw + rotationOffset & 0x7ff;
		int mapX = 48 + localPlayer.x / 32;
		int mapY = 464 - localPlayer.y / 32;
		mapImage.shapeImageToPixels(25, 5, 146, 151, 256 + zoomOffset, mapX, assets.minimapMaskWidths, rotation,
				assets.minimapMaskOffsets, mapY);
		assets.compass.shapeImageToPixels(0, 0, 33, 33, 256, 25, assets.compassMaskWidths, cameraYaw,
				assets.compassMaskOffsets, 25);

		for (int loopIndex2 = 0; loopIndex2 < mapFunctionCount; loopIndex2++) {
			int dx = mapFunctionX[loopIndex2] * 4 + 2 - localPlayer.x / 32;
			int dy = mapFunctionY[loopIndex2] * 4 + 2 - localPlayer.y / 32;
			drawOnMinimap(dy, mapFunctionIcons[loopIndex2], dx, cameraYaw, assets);
		}

		for (int x = 0; x < 104; x++) {
			for (int y = 0; y < 104; y++) {
				NodeDeque items = world.groundItems[plane][x][y];
				if (items != null) {
					drawOnMinimap(y * 4 + 2 - localPlayer.y / 32, assets.groundItemDot, x * 4 + 2 - localPlayer.x / 32,
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
					drawOnMinimap(npc.y / 32 - localPlayer.y / 32, assets.npcDot, npc.x / 32 - localPlayer.x / 32,
							cameraYaw, assets);
				}
			}
		}

		for (int loopIndex4 = 0; loopIndex4 < actors.playerCount; loopIndex4++) {
			Player player = actors.players[actors.playerIndices[loopIndex4]];
			if (player == null || !player.isVisible())
				continue;
			int dx = player.x / 32 - localPlayer.x / 32;
			int dy = player.y / 32 - localPlayer.y / 32;
			boolean friend = friends.isFriend(player.name);
			boolean teammate = localPlayer.team != 0 && player.team != 0 && localPlayer.team == player.team;
			drawOnMinimap(dy, friend ? assets.friendDot : teammate ? assets.teamDot : assets.playerDot, dx, cameraYaw,
					assets);
		}

		if (hintType != 0 && gameCycle % 20 < 10) {
			if (hintType == 1 && hintNpcIndex >= 0 && hintNpcIndex < actors.npcs.length) {
				Npc npc = actors.npcs[hintNpcIndex];
				if (npc != null) {
					drawHint(npc.y / 32 - localPlayer.y / 32, assets.hintMarker, npc.x / 32 - localPlayer.x / 32,
							cameraYaw, assets);
				}
			} else if (hintType == 2) {
				drawHint((hintTileY - baseY) * 4 + 2 - localPlayer.y / 32, assets.hintMarker,
						(hintTileX - baseX) * 4 + 2 - localPlayer.x / 32, cameraYaw, assets);
			} else if (hintType == 10 && hintPlayerIndex >= 0 && hintPlayerIndex < actors.players.length) {
				Player player = actors.players[hintPlayerIndex];
				if (player != null) {
					drawHint(player.y / 32 - localPlayer.y / 32, assets.hintMarker, player.x / 32 - localPlayer.x / 32,
							cameraYaw, assets);
				}
			}
		}
		if (destinationX != 0) {
			drawOnMinimap(destinationY * 4 + 2 - localPlayer.y / 32, assets.destinationMarker,
					destinationX * 4 + 2 - localPlayer.x / 32, cameraYaw, assets);
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
		if (distance > 4225 && distance < 0x15f90) {
			int angle = cameraYaw + rotationOffset & 0x7ff;
			int sine = Model.SINE[angle] * 256 / (zoomOffset + 256);
			int cosine = Model.COSINE[angle] * 256 / (zoomOffset + 256);
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
		int angle = cameraYaw + rotationOffset & 0x7ff;
		int distance = dx * dx + dy * dy;
		if (distance > 6400)
			return;
		int sine = Model.SINE[angle] * 256 / (zoomOffset + 256);
		int cosine = Model.COSINE[angle] * 256 / (zoomOffset + 256);
		int rotatedX = dy * sine + dx * cosine >> 16;
		int rotatedY = dy * cosine - dx * sine >> 16;
		if (distance > 2500) {
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
	 * @param clickX      the click x
	 * @param clickY      the click y
	 * @param localPlayer the local player
	 * @param cameraYaw   the camera yaw
	 */
	public Click transformClick(int clickX, int clickY, Player localPlayer, int cameraYaw) {
		int x = clickX - 25 - 550;
		int y = clickY - 5 - 4;
		if (x < 0 || y < 0 || x >= 146 || y >= 151)
			return null;
		x -= 73;
		y -= 75;
		int angle = cameraYaw + rotationOffset & 0x7ff;
		int sine = Rasterizer3D.SINE[angle] * (zoomOffset + 256) >> 8;
		int cosine = Rasterizer3D.COSINE[angle] * (zoomOffset + 256) >> 8;
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
