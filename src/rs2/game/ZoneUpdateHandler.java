package rs2.game;

import rs2.cache.def.GameObjectDefinition;
import rs2.collection.NodeDeque;
import rs2.media.renderable.DynamicObject;
import rs2.media.renderable.GraphicsObject;
import rs2.media.renderable.GroundItem;
import rs2.media.renderable.Model;
import rs2.media.renderable.Player;
import rs2.media.renderable.Projectile;
import rs2.net.Buffer;
import rs2.scene.tile.FloorDecoration;
import rs2.scene.InteractiveObject;
import rs2.scene.tile.Wall;
import rs2.scene.tile.WallDecoration;

/** Decodes the compact 8x8-zone update packet formats used by revision 377. */
public final class ZoneUpdateHandler {

	/**
	 * Stores attach object to player.
	 */
	public static final int ATTACH_OBJECT_TO_PLAYER = 203;
	/**
	 * Stores add ground item for other player.
	 */
	public static final int ADD_GROUND_ITEM_FOR_OTHER_PLAYER = 106;
	/**
	 * Stores animate game object.
	 */
	public static final int ANIMATE_GAME_OBJECT = 142;
	/**
	 * Stores add ground item.
	 */
	public static final int ADD_GROUND_ITEM = 107;
	/**
	 * Stores update ground item amount.
	 */
	public static final int UPDATE_GROUND_ITEM_AMOUNT = 121;
	/**
	 * Stores add projectile.
	 */
	public static final int ADD_PROJECTILE = 181;
	/**
	 * Stores play area sound.
	 */
	public static final int PLAY_AREA_SOUND = 41;
	/**
	 * Stores add graphics object.
	 */
	public static final int ADD_GRAPHICS_OBJECT = 59;
	/**
	 * Stores add game object.
	 */
	public static final int ADD_GAME_OBJECT = 152;
	/**
	 * Stores remove ground item.
	 */
	public static final int REMOVE_GROUND_ITEM = 208;
	/**
	 * Stores remove game object.
	 */
	public static final int REMOVE_GAME_OBJECT = 88;

	/**
	 * Stores scene layers by type.
	 */
	private static final int[] SCENE_LAYERS_BY_TYPE = { 0, 0, 0, 0, 1, 1, 1, 1, 1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2, 2,
			2, 3 };

	/**
	 * Stores world.
	 */
	private final WorldState world;
	/**
	 * Stores zone base x.
	 */
	private int zoneBaseX;
	/**
	 * Stores zone base y.
	 */
	private int zoneBaseY;

	/**
	 * Initializes this instance.
	 * 
	 * @param world the world
	 */
	public ZoneUpdateHandler(WorldState world) {
		this.world = world;
	}

	/**
	 * Returns zone base x.
	 * 
	 * @return the resulting int
	 */
	public int getZoneBaseX() {
		return zoneBaseX;
	}

	/**
	 * Returns zone base y.
	 * 
	 * @return the resulting int
	 */
	public int getZoneBaseY() {
		return zoneBaseY;
	}

	/**
	 * Sets zone base.
	 * 
	 * @param x the x
	 * @param y the y
	 */
	public void setZoneBase(int x, int y) {
		zoneBaseX = x;
		zoneBaseY = y;
	}

	@FunctionalInterface
	public interface AreaSoundHandler {
		/**
		 * Performs queue area sound.
		 * 
		 * @param soundId the sound id
		 * @param loops   the loops
		 * @param radius  the radius
		 * @param tileX   the tile x
		 * @param tileY   the tile y
		 */
		void queueAreaSound(int soundId, int loops, int radius, int tileX, int tileY);
	}

	/**
	 * Legacy client.method133(Buffer class50_sub1_sub2, int i, int j)
	 *
	 * <pre>
	 * class50_sub1_sub2 -> buffer
	 * i                  -> removed sentinel (required 0)
	 * j                  -> updateType
	 * </pre>
	 * 
	 * @param buffer                 the buffer
	 * @param updateType             the update type
	 * @param currentPlane           the current plane
	 * @param currentCycle           the current cycle
	 * @param localPlayerServerIndex the local player server index
	 * @param localPlayer            the local player
	 * @param actors                 the actors
	 * @param areaSoundHandler       the area sound handler
	 */
	public void decode(Buffer buffer, int updateType, int currentPlane, int currentCycle, int localPlayerServerIndex,
			Player localPlayer, ActorSynchronizer actors, AreaSoundHandler areaSoundHandler) {
		if (updateType == ATTACH_OBJECT_TO_PLAYER) {
			int objectId = buffer.readUnsignedShort();
			int typeAndOrientation = buffer.readUnsignedByte();
			int type = typeAndOrientation >> 2;
			int orientation = typeAndOrientation & 3;
			int sceneLayer = SCENE_LAYERS_BY_TYPE[type];
			byte maxXOffset = buffer.readByteNeg();
			int packedTile = buffer.readUnsignedByteAdd();
			int tileX = zoneBaseX + (packedTile >> 4 & 7);
			int tileY = zoneBaseY + (packedTile & 7);
			byte minXOffset = buffer.readByteAdd();
			int endDelay = buffer.readUnsignedShortAdd();
			int playerIndex = buffer.readUnsignedShortLE();
			byte maxYOffset = buffer.readSignedByte();
			byte minYOffset = buffer.readByteAdd();
			int startDelay = buffer.readUnsignedShort();
			Player player = playerIndex == localPlayerServerIndex ? localPlayer : actors.players[playerIndex];
			if (player != null) {
				GameObjectDefinition definition = GameObjectDefinition.lookup(objectId);
				int southWestHeight = world.tileHeights[currentPlane][tileX][tileY];
				int southEastHeight = world.tileHeights[currentPlane][tileX + 1][tileY];
				int northEastHeight = world.tileHeights[currentPlane][tileX + 1][tileY + 1];
				int northWestHeight = world.tileHeights[currentPlane][tileX][tileY + 1];
				Model model = definition.getModelAt(type, orientation, southWestHeight, southEastHeight,
						northEastHeight, northWestHeight, -1);
				if (model != null) {
					world.schedulePendingSpawn(currentPlane, tileX, tileY, sceneLayer, -1, 0, 0, startDelay + 1,
							endDelay + 1);
					player.attachedModelStartCycle = startDelay + currentCycle;
					player.attachedModelEndCycle = endDelay + currentCycle;
					player.attachedModel = model;
					int sizeX = definition.sizeX;
					int sizeY = definition.sizeY;
					if (orientation == 1 || orientation == 3) {
						sizeX = definition.sizeY;
						sizeY = definition.sizeX;
					}
					player.attachedModelX = tileX * 128 + sizeX * 64;
					player.attachedModelY = tileY * 128 + sizeY * 64;
					player.attachedModelHeight = world.getTileHeight(player.attachedModelX, player.attachedModelY,
							currentPlane);
					if (minXOffset > maxXOffset) {
						byte temporary = minXOffset;
						minXOffset = maxXOffset;
						maxXOffset = temporary;
					}
					if (minYOffset > maxYOffset) {
						byte temporary = minYOffset;
						minYOffset = maxYOffset;
						maxYOffset = temporary;
					}
					player.attachedModelMinX = tileX + minXOffset;
					player.attachedModelMaxX = tileX + maxXOffset;
					player.attachedModelMinY = tileY + minYOffset;
					player.attachedModelMaxY = tileY + maxYOffset;
				}
			}
		}

		if (updateType == ADD_GROUND_ITEM_FOR_OTHER_PLAYER) {
			int packedTile = buffer.readUnsignedByteAdd();
			int tileX = zoneBaseX + (packedTile >> 4 & 7);
			int tileY = zoneBaseY + (packedTile & 7);
			int amount = buffer.readUnsignedShortAddLE();
			int itemId = buffer.readUnsignedShortAdd();
			int ownerIndex = buffer.readUnsignedShortAdd();
			if (tileX >= 0 && tileY >= 0 && tileX < 104 && tileY < 104 && ownerIndex != localPlayerServerIndex) {
				GroundItem item = new GroundItem();
				item.id = itemId;
				item.amount = amount;
				if (world.groundItems[currentPlane][tileX][tileY] == null) {
					world.groundItems[currentPlane][tileX][tileY] = new NodeDeque();
				}
				world.groundItems[currentPlane][tileX][tileY].addLast(item);
				world.updateGroundItemPile(currentPlane, tileX, tileY);
			}
			return;
		}

		if (updateType == ANIMATE_GAME_OBJECT) {
			int animationId = buffer.readUnsignedShort();
			int typeAndOrientation = buffer.readUnsignedByteAdd();
			int type = typeAndOrientation >> 2;
			int orientation = typeAndOrientation & 3;
			int sceneLayer = SCENE_LAYERS_BY_TYPE[type];
			int packedTile = buffer.readUnsignedByte();
			int tileX = zoneBaseX + (packedTile >> 4 & 7);
			int tileY = zoneBaseY + (packedTile & 7);
			if (tileX >= 0 && tileY >= 0 && tileX < 103 && tileY < 103) {
				int southWestHeight = world.tileHeights[currentPlane][tileX][tileY];
				int southEastHeight = world.tileHeights[currentPlane][tileX + 1][tileY];
				int northEastHeight = world.tileHeights[currentPlane][tileX + 1][tileY + 1];
				int northWestHeight = world.tileHeights[currentPlane][tileX][tileY + 1];
				if (sceneLayer == 0) {
					Wall wall = world.scene.getWall(currentPlane, tileX, tileY);
					if (wall != null) {
						int objectId = wall.uid >> 14 & 0x7fff;
						if (type == 2) {
							wall.primary = new DynamicObject(objectId, 2, 4 + orientation, southWestHeight,
									southEastHeight, northEastHeight, northWestHeight, animationId, false);
							wall.secondary = new DynamicObject(objectId, 2, orientation + 1 & 3, southWestHeight,
									southEastHeight, northEastHeight, northWestHeight, animationId, false);
						} else {
							wall.primary = new DynamicObject(objectId, type, orientation, southWestHeight,
									southEastHeight, northEastHeight, northWestHeight, animationId, false);
						}
					}
				}
				if (sceneLayer == 1) {
					WallDecoration decoration = world.scene.getWallDecoration(currentPlane, tileX, tileY);
					if (decoration != null) {
						decoration.renderable = new DynamicObject(decoration.uid >> 14 & 0x7fff, 4, 0, southWestHeight,
								southEastHeight, northEastHeight, northWestHeight, animationId, false);
					}
				}
				if (sceneLayer == 2) {
					InteractiveObject object = world.scene.getInteractiveObject(currentPlane, tileX, tileY);
					if (type == 11) {
						type = 10;
					}
					if (object != null) {
						object.renderable = new DynamicObject(object.uid >> 14 & 0x7fff, type, orientation,
								southWestHeight, southEastHeight, northEastHeight, northWestHeight, animationId, false);
					}
				}
				if (sceneLayer == 3) {
					FloorDecoration decoration = world.scene.getFloorDecoration(currentPlane, tileX, tileY);
					if (decoration != null) {
						decoration.renderable = new DynamicObject(decoration.uid >> 14 & 0x7fff, 22, orientation,
								southWestHeight, southEastHeight, northEastHeight, northWestHeight, animationId, false);
					}
				}
			}
			return;
		}

		if (updateType == ADD_GROUND_ITEM) {
			int itemId = buffer.readUnsignedShort();
			int packedTile = buffer.readUnsignedByteNeg();
			int tileX = zoneBaseX + (packedTile >> 4 & 7);
			int tileY = zoneBaseY + (packedTile & 7);
			int amount = buffer.readUnsignedShortAdd();
			if (tileX >= 0 && tileY >= 0 && tileX < 104 && tileY < 104) {
				GroundItem item = new GroundItem();
				item.id = itemId;
				item.amount = amount;
				if (world.groundItems[currentPlane][tileX][tileY] == null) {
					world.groundItems[currentPlane][tileX][tileY] = new NodeDeque();
				}
				world.groundItems[currentPlane][tileX][tileY].addLast(item);
				world.updateGroundItemPile(currentPlane, tileX, tileY);
			}
			return;
		}

		if (updateType == UPDATE_GROUND_ITEM_AMOUNT) {
			int packedTile = buffer.readUnsignedByte();
			int tileX = zoneBaseX + (packedTile >> 4 & 7);
			int tileY = zoneBaseY + (packedTile & 7);
			int itemId = buffer.readUnsignedShort();
			int oldAmount = buffer.readUnsignedShort();
			int newAmount = buffer.readUnsignedShort();
			if (tileX >= 0 && tileY >= 0 && tileX < 104 && tileY < 104) {
				NodeDeque items = world.groundItems[currentPlane][tileX][tileY];
				if (items != null) {
					for (GroundItem item = (GroundItem) items.first(); item != null; item = (GroundItem) items.next()) {
						if (item.id == (itemId & 0x7fff) && item.amount == oldAmount) {
							item.amount = newAmount;
							break;
						}
					}
					world.updateGroundItemPile(currentPlane, tileX, tileY);
				}
			}
			return;
		}

		if (updateType == ADD_PROJECTILE) {
			int packedTile = buffer.readUnsignedByte();
			int sourceTileX = zoneBaseX + (packedTile >> 4 & 7);
			int sourceTileY = zoneBaseY + (packedTile & 7);
			int destinationTileX = sourceTileX + buffer.readSignedByte();
			int destinationTileY = sourceTileY + buffer.readSignedByte();
			int targetIndex = buffer.readSignedShort();
			int spotAnimationId = buffer.readUnsignedShort();
			int sourceHeight = buffer.readUnsignedByte() * 4;
			int endHeight = buffer.readUnsignedByte() * 4;
			int startDelay = buffer.readUnsignedShort();
			int endDelay = buffer.readUnsignedShort();
			int slope = buffer.readUnsignedByte();
			int startHeight = buffer.readUnsignedByte();
			if (sourceTileX >= 0 && sourceTileY >= 0 && sourceTileX < 104 && sourceTileY < 104 && destinationTileX >= 0
					&& destinationTileY >= 0 && destinationTileX < 104 && destinationTileY < 104
					&& spotAnimationId != 65535) {
				int sourceX = sourceTileX * 128 + 64;
				int sourceY = sourceTileY * 128 + 64;
				int destinationX = destinationTileX * 128 + 64;
				int destinationY = destinationTileY * 128 + 64;
				Projectile projectile = new Projectile(spotAnimationId, currentPlane, sourceX, sourceY,
						world.getTileHeight(sourceX, sourceY, currentPlane) - sourceHeight, startDelay + currentCycle,
						endDelay + currentCycle, slope, startHeight, targetIndex, endHeight);
				projectile.setDestination(destinationX, destinationY,
						world.getTileHeight(destinationX, destinationY, currentPlane) - endHeight,
						startDelay + currentCycle);
				world.projectiles.addLast(projectile);
			}
			return;
		}

		if (updateType == PLAY_AREA_SOUND) {
			int packedTile = buffer.readUnsignedByte();
			int tileX = zoneBaseX + (packedTile >> 4 & 7);
			int tileY = zoneBaseY + (packedTile & 7);
			int soundId = buffer.readUnsignedShort();
			int packedRadiusAndLoops = buffer.readUnsignedByte();
			int radius = packedRadiusAndLoops >> 4 & 0xf;
			int loops = packedRadiusAndLoops & 7;
			areaSoundHandler.queueAreaSound(soundId, loops, radius, tileX, tileY);
		}

		if (updateType == ADD_GRAPHICS_OBJECT) {
			int packedTile = buffer.readUnsignedByte();
			int tileX = zoneBaseX + (packedTile >> 4 & 7);
			int tileY = zoneBaseY + (packedTile & 7);
			int spotAnimationId = buffer.readUnsignedShort();
			int height = buffer.readUnsignedByte();
			int delay = buffer.readUnsignedShort();
			if (tileX >= 0 && tileY >= 0 && tileX < 104 && tileY < 104) {
				int worldX = tileX * 128 + 64;
				int worldY = tileY * 128 + 64;
				GraphicsObject graphics = new GraphicsObject(spotAnimationId, currentPlane, worldX, worldY,
						world.getTileHeight(worldX, worldY, currentPlane) - height, delay, currentCycle);
				world.graphicsObjects.addLast(graphics);
			}
			return;
		}

		if (updateType == ADD_GAME_OBJECT) {
			int typeAndOrientation = buffer.readUnsignedByteNeg();
			int type = typeAndOrientation >> 2;
			int orientation = typeAndOrientation & 3;
			int sceneLayer = SCENE_LAYERS_BY_TYPE[type];
			int objectId = buffer.readUnsignedShortAddLE();
			int packedTile = buffer.readUnsignedByteAdd();
			int tileX = zoneBaseX + (packedTile >> 4 & 7);
			int tileY = zoneBaseY + (packedTile & 7);
			if (tileX >= 0 && tileY >= 0 && tileX < 104 && tileY < 104) {
				world.schedulePendingSpawn(currentPlane, tileX, tileY, sceneLayer, objectId, type, orientation, 0, -1);
			}
			return;
		}

		if (updateType == REMOVE_GROUND_ITEM) {
			int itemId = buffer.readUnsignedShortAdd();
			int packedTile = buffer.readUnsignedByteAdd();
			int tileX = zoneBaseX + (packedTile >> 4 & 7);
			int tileY = zoneBaseY + (packedTile & 7);
			if (tileX >= 0 && tileY >= 0 && tileX < 104 && tileY < 104) {
				NodeDeque items = world.groundItems[currentPlane][tileX][tileY];
				if (items != null) {
					for (GroundItem item = (GroundItem) items.first(); item != null; item = (GroundItem) items.next()) {
						if (item.id == (itemId & 0x7fff)) {
							item.unlink();
							break;
						}
					}
					if (items.first() == null) {
						world.groundItems[currentPlane][tileX][tileY] = null;
					}
					world.updateGroundItemPile(currentPlane, tileX, tileY);
				}
			}
			return;
		}

		if (updateType == REMOVE_GAME_OBJECT) {
			int packedTile = buffer.readUnsignedByteSub();
			int tileX = zoneBaseX + (packedTile >> 4 & 7);
			int tileY = zoneBaseY + (packedTile & 7);
			int typeAndOrientation = buffer.readUnsignedByteSub();
			int type = typeAndOrientation >> 2;
			int orientation = typeAndOrientation & 3;
			int sceneLayer = SCENE_LAYERS_BY_TYPE[type];
			if (tileX >= 0 && tileY >= 0 && tileX < 104 && tileY < 104) {
				world.schedulePendingSpawn(currentPlane, tileX, tileY, sceneLayer, -1, type, orientation, 0, -1);
			}
		}
	}
}