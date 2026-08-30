package rs2.game;

import rs2.cache.def.GameObjectDefinition;
import rs2.collection.NodeDeque;
import rs2.scene.entity.DynamicObject;
import rs2.scene.entity.GraphicsObject;
import rs2.scene.entity.GroundItem;
import rs2.media.model.Model;
import rs2.game.entity.Player;
import rs2.scene.entity.Projectile;
import rs2.net.Buffer;
import rs2.net.IncomingPacketOpcode;
import rs2.net.ProtocolConstants;
import rs2.scene.tile.FloorDecoration;
import rs2.scene.InteractiveObject;
import rs2.scene.SceneConfig;
import rs2.scene.SceneConstants;
import rs2.scene.SceneUid;
import rs2.scene.tile.Wall;
import rs2.scene.tile.WallDecoration;

/** Decodes the compact 8x8-zone update packet formats used by revision 377. */
public final class ZoneUpdateHandler {

	/** Constant value for attach object to player. */
	public static final int ATTACH_OBJECT_TO_PLAYER = IncomingPacketOpcode.ATTACH_OBJECT_TO_PLAYER;

	/** Constant value for add ground item for other player. */
	public static final int ADD_GROUND_ITEM_FOR_OTHER_PLAYER = IncomingPacketOpcode.ADD_GROUND_ITEM_FOR_OTHER_PLAYER;

	/** Constant value for animate game object. */
	public static final int ANIMATE_GAME_OBJECT = IncomingPacketOpcode.ANIMATE_GAME_OBJECT;

	/** Constant value for add ground item. */
	public static final int ADD_GROUND_ITEM = IncomingPacketOpcode.ADD_GROUND_ITEM;

	/** Constant value for update ground item amount. */
	public static final int UPDATE_GROUND_ITEM_AMOUNT = IncomingPacketOpcode.UPDATE_GROUND_ITEM_AMOUNT;

	/** Constant value for add projectile. */
	public static final int ADD_PROJECTILE = IncomingPacketOpcode.ADD_PROJECTILE;

	/** Constant value for play area sound. */
	public static final int PLAY_AREA_SOUND = IncomingPacketOpcode.PLAY_AREA_SOUND;

	/** Constant value for add graphics object. */
	public static final int ADD_GRAPHICS_OBJECT = IncomingPacketOpcode.ADD_GRAPHICS_OBJECT;

	/** Constant value for add game object. */
	public static final int ADD_GAME_OBJECT = IncomingPacketOpcode.ADD_GAME_OBJECT;

	/** Constant value for remove ground item. */
	public static final int REMOVE_GROUND_ITEM = IncomingPacketOpcode.REMOVE_GROUND_ITEM;

	/** Constant value for remove game object. */
	public static final int REMOVE_GAME_OBJECT = IncomingPacketOpcode.REMOVE_GAME_OBJECT;

	/** Wall scene layer. */
	private static final int SCENE_LAYER_WALL = 0;

	/** Wall-decoration scene layer. */
	private static final int SCENE_LAYER_WALL_DECORATION = 1;

	/** Interactive-object scene layer. */
	private static final int SCENE_LAYER_INTERACTIVE_OBJECT = 2;

	/** Floor-decoration scene layer. */
	private static final int SCENE_LAYER_FLOOR_DECORATION = 3;

	/** Number of low bits occupied by orientation in packed location types. */
	private static final int LOCATION_TYPE_SHIFT = 2;

	/** Corner-wall object type requiring two animated wall renderables. */
	private static final int CORNER_WALL_TYPE = 2;

	/** Wall-decoration model type used by animated decorations. */
	private static final int WALL_DECORATION_MODEL_TYPE = 4;

	/** Standard interactive-object model type. */
	private static final int INTERACTIVE_OBJECT_TYPE = 10;

	/** Alternate interactive-object protocol type normalized to type 10. */
	private static final int ALTERNATE_INTERACTIVE_OBJECT_TYPE = 11;

	/** Floor-decoration model type. */
	private static final int FLOOR_DECORATION_TYPE = 22;

	/** Orientation offset used for the second face of a corner wall. */
	private static final int CORNER_WALL_SECONDARY_ORIENTATION_OFFSET = 4;

	/** Low 15 bits carrying the ground-item definition id. */
	private static final int GROUND_ITEM_ID_MASK = 0x7fff;

	/** Protocol unit multiplier for projectile source/destination heights. */
	private static final int PROJECTILE_HEIGHT_SCALE = 4;

	/** High-nibble shift for the packed area-sound radius. */
	private static final int AREA_SOUND_RADIUS_SHIFT = 4;

	/** Four-bit mask for the packed area-sound radius. */
	private static final int AREA_SOUND_RADIUS_MASK = 0xf;

	/** Three-bit mask for the packed area-sound loop count. */
	private static final int AREA_SOUND_LOOPS_MASK = 0x7;

	/** Maps location model types to the scene layer that owns them. */
	private static final int[] SCENE_LAYERS_BY_TYPE = {
			SCENE_LAYER_WALL, SCENE_LAYER_WALL, SCENE_LAYER_WALL, SCENE_LAYER_WALL,
			SCENE_LAYER_WALL_DECORATION, SCENE_LAYER_WALL_DECORATION, SCENE_LAYER_WALL_DECORATION,
			SCENE_LAYER_WALL_DECORATION, SCENE_LAYER_WALL_DECORATION,
			SCENE_LAYER_INTERACTIVE_OBJECT, SCENE_LAYER_INTERACTIVE_OBJECT, SCENE_LAYER_INTERACTIVE_OBJECT,
			SCENE_LAYER_INTERACTIVE_OBJECT, SCENE_LAYER_INTERACTIVE_OBJECT, SCENE_LAYER_INTERACTIVE_OBJECT,
			SCENE_LAYER_INTERACTIVE_OBJECT, SCENE_LAYER_INTERACTIVE_OBJECT, SCENE_LAYER_INTERACTIVE_OBJECT,
			SCENE_LAYER_INTERACTIVE_OBJECT, SCENE_LAYER_INTERACTIVE_OBJECT, SCENE_LAYER_INTERACTIVE_OBJECT,
			SCENE_LAYER_INTERACTIVE_OBJECT, SCENE_LAYER_FLOOR_DECORATION };

	/** Stores the current world. */
	private final WorldState world;

	/** Stores the current zone base X. */
	private int zoneBaseX;

	/** Stores the current zone base Y. */
	private int zoneBaseY;

	/**
	 * Creates a new zone update handler.
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

	/** Handles area sound operations. */
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
	 * Applies one zone-update packet to ground items, objects, projectiles, graphics, or area sound.
	 * @param buffer the source buffer
	 * @param updateType the update type
	 * @param currentPlane the current plane
	 * @param currentCycle the current client cycle
	 * @param localPlayerServerIndex the local player server index
	 * @param localPlayer the local player
	 * @param actors the actors
	 * @param areaSoundHandler the area sound handler
	 */
	public void decode(Buffer buffer, int updateType, int currentPlane, int currentCycle, int localPlayerServerIndex,
			Player localPlayer, ActorSynchronizer actors, AreaSoundHandler areaSoundHandler) {
		if (updateType == ATTACH_OBJECT_TO_PLAYER) {
			int objectId = buffer.readUnsignedShort();
			int typeAndOrientation = buffer.readUnsignedByte();
			int type = typeAndOrientation >> LOCATION_TYPE_SHIFT;
			int orientation = typeAndOrientation & SceneConfig.ORIENTATION_MASK;
			int sceneLayer = SCENE_LAYERS_BY_TYPE[type];
			byte maxXOffset = buffer.readByteNeg();
			int packedTile = buffer.readUnsignedByteAdd();
			int tileX = zoneBaseX + (packedTile >> 4 & SceneConstants.CHUNK_COORDINATE_MASK);
			int tileY = zoneBaseY + (packedTile & SceneConstants.CHUNK_COORDINATE_MASK);
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
					player.attachedModelX = tileX * SceneConstants.TILE_SIZE + sizeX * SceneConstants.TILE_CENTER;
					player.attachedModelY = tileY * SceneConstants.TILE_SIZE + sizeY * SceneConstants.TILE_CENTER;
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
			int tileX = zoneBaseX + (packedTile >> 4 & SceneConstants.CHUNK_COORDINATE_MASK);
			int tileY = zoneBaseY + (packedTile & SceneConstants.CHUNK_COORDINATE_MASK);
			int amount = buffer.readUnsignedShortAddLE();
			int itemId = buffer.readUnsignedShortAdd();
			int ownerIndex = buffer.readUnsignedShortAdd();
			if (tileX >= 0 && tileY >= 0 && tileX < SceneConstants.SIZE && tileY < SceneConstants.SIZE && ownerIndex != localPlayerServerIndex) {
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
			int type = typeAndOrientation >> LOCATION_TYPE_SHIFT;
			int orientation = typeAndOrientation & SceneConfig.ORIENTATION_MASK;
			int sceneLayer = SCENE_LAYERS_BY_TYPE[type];
			int packedTile = buffer.readUnsignedByte();
			int tileX = zoneBaseX + (packedTile >> 4 & SceneConstants.CHUNK_COORDINATE_MASK);
			int tileY = zoneBaseY + (packedTile & SceneConstants.CHUNK_COORDINATE_MASK);
			if (tileX >= 0 && tileY >= 0 && tileX < SceneConstants.MAX_TILE_INDEX && tileY < SceneConstants.MAX_TILE_INDEX) {
				int southWestHeight = world.tileHeights[currentPlane][tileX][tileY];
				int southEastHeight = world.tileHeights[currentPlane][tileX + 1][tileY];
				int northEastHeight = world.tileHeights[currentPlane][tileX + 1][tileY + 1];
				int northWestHeight = world.tileHeights[currentPlane][tileX][tileY + 1];
				if (sceneLayer == SCENE_LAYER_WALL) {
					Wall wall = world.scene.getWall(currentPlane, tileX, tileY);
					if (wall != null) {
						int objectId = wall.uid >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK;
						if (type == CORNER_WALL_TYPE) {
							wall.primary = new DynamicObject(objectId, CORNER_WALL_TYPE, CORNER_WALL_SECONDARY_ORIENTATION_OFFSET + orientation, southWestHeight,
									southEastHeight, northEastHeight, northWestHeight, animationId, false);
							wall.secondary = new DynamicObject(objectId, CORNER_WALL_TYPE, orientation + 1 & SceneConfig.ORIENTATION_MASK, southWestHeight,
									southEastHeight, northEastHeight, northWestHeight, animationId, false);
						} else {
							wall.primary = new DynamicObject(objectId, type, orientation, southWestHeight,
									southEastHeight, northEastHeight, northWestHeight, animationId, false);
						}
					}
				}
				if (sceneLayer == SCENE_LAYER_WALL_DECORATION) {
					WallDecoration decoration = world.scene.getWallDecoration(currentPlane, tileX, tileY);
					if (decoration != null) {
						decoration.renderable = new DynamicObject(decoration.uid >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK, WALL_DECORATION_MODEL_TYPE, 0, southWestHeight,
								southEastHeight, northEastHeight, northWestHeight, animationId, false);
					}
				}
				if (sceneLayer == SCENE_LAYER_INTERACTIVE_OBJECT) {
					InteractiveObject object = world.scene.getInteractiveObject(currentPlane, tileX, tileY);
					if (type == ALTERNATE_INTERACTIVE_OBJECT_TYPE) {
						type = INTERACTIVE_OBJECT_TYPE;
					}
					if (object != null) {
						object.renderable = new DynamicObject(object.uid >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK, type, orientation,
								southWestHeight, southEastHeight, northEastHeight, northWestHeight, animationId, false);
					}
				}
				if (sceneLayer == SCENE_LAYER_FLOOR_DECORATION) {
					FloorDecoration decoration = world.scene.getFloorDecoration(currentPlane, tileX, tileY);
					if (decoration != null) {
						decoration.renderable = new DynamicObject(decoration.uid >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK, FLOOR_DECORATION_TYPE, orientation,
								southWestHeight, southEastHeight, northEastHeight, northWestHeight, animationId, false);
					}
				}
			}
			return;
		}

		if (updateType == ADD_GROUND_ITEM) {
			int itemId = buffer.readUnsignedShort();
			int packedTile = buffer.readUnsignedByteNeg();
			int tileX = zoneBaseX + (packedTile >> 4 & SceneConstants.CHUNK_COORDINATE_MASK);
			int tileY = zoneBaseY + (packedTile & SceneConstants.CHUNK_COORDINATE_MASK);
			int amount = buffer.readUnsignedShortAdd();
			if (tileX >= 0 && tileY >= 0 && tileX < SceneConstants.SIZE && tileY < SceneConstants.SIZE) {
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
			int tileX = zoneBaseX + (packedTile >> 4 & SceneConstants.CHUNK_COORDINATE_MASK);
			int tileY = zoneBaseY + (packedTile & SceneConstants.CHUNK_COORDINATE_MASK);
			int itemId = buffer.readUnsignedShort();
			int oldAmount = buffer.readUnsignedShort();
			int newAmount = buffer.readUnsignedShort();
			if (tileX >= 0 && tileY >= 0 && tileX < SceneConstants.SIZE && tileY < SceneConstants.SIZE) {
				NodeDeque items = world.groundItems[currentPlane][tileX][tileY];
				if (items != null) {
					for (GroundItem item = (GroundItem) items.first(); item != null; item = (GroundItem) items.next()) {
						if (item.id == (itemId & GROUND_ITEM_ID_MASK) && item.amount == oldAmount) {
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
			int sourceTileX = zoneBaseX + (packedTile >> 4 & SceneConstants.CHUNK_COORDINATE_MASK);
			int sourceTileY = zoneBaseY + (packedTile & SceneConstants.CHUNK_COORDINATE_MASK);
			int destinationTileX = sourceTileX + buffer.readSignedByte();
			int destinationTileY = sourceTileY + buffer.readSignedByte();
			int targetIndex = buffer.readSignedShort();
			int spotAnimationId = buffer.readUnsignedShort();
			int sourceHeight = buffer.readUnsignedByte() * PROJECTILE_HEIGHT_SCALE;
			int endHeight = buffer.readUnsignedByte() * PROJECTILE_HEIGHT_SCALE;
			int startDelay = buffer.readUnsignedShort();
			int endDelay = buffer.readUnsignedShort();
			int slope = buffer.readUnsignedByte();
			int startHeight = buffer.readUnsignedByte();
			if (sourceTileX >= 0 && sourceTileY >= 0 && sourceTileX < SceneConstants.SIZE && sourceTileY < SceneConstants.SIZE && destinationTileX >= 0
					&& destinationTileY >= 0 && destinationTileX < SceneConstants.SIZE && destinationTileY < SceneConstants.SIZE
					&& spotAnimationId != ProtocolConstants.NULL_ID) {
				int sourceX = sourceTileX * SceneConstants.TILE_SIZE + SceneConstants.TILE_CENTER;
				int sourceY = sourceTileY * SceneConstants.TILE_SIZE + SceneConstants.TILE_CENTER;
				int destinationX = destinationTileX * SceneConstants.TILE_SIZE + SceneConstants.TILE_CENTER;
				int destinationY = destinationTileY * SceneConstants.TILE_SIZE + SceneConstants.TILE_CENTER;
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
			int tileX = zoneBaseX + (packedTile >> 4 & SceneConstants.CHUNK_COORDINATE_MASK);
			int tileY = zoneBaseY + (packedTile & SceneConstants.CHUNK_COORDINATE_MASK);
			int soundId = buffer.readUnsignedShort();
			int packedRadiusAndLoops = buffer.readUnsignedByte();
			int radius = packedRadiusAndLoops >> AREA_SOUND_RADIUS_SHIFT & AREA_SOUND_RADIUS_MASK;
			int loops = packedRadiusAndLoops & AREA_SOUND_LOOPS_MASK;
			areaSoundHandler.queueAreaSound(soundId, loops, radius, tileX, tileY);
		}

		if (updateType == ADD_GRAPHICS_OBJECT) {
			int packedTile = buffer.readUnsignedByte();
			int tileX = zoneBaseX + (packedTile >> 4 & SceneConstants.CHUNK_COORDINATE_MASK);
			int tileY = zoneBaseY + (packedTile & SceneConstants.CHUNK_COORDINATE_MASK);
			int spotAnimationId = buffer.readUnsignedShort();
			int height = buffer.readUnsignedByte();
			int delay = buffer.readUnsignedShort();
			if (tileX >= 0 && tileY >= 0 && tileX < SceneConstants.SIZE && tileY < SceneConstants.SIZE) {
				int worldX = tileX * SceneConstants.TILE_SIZE + SceneConstants.TILE_CENTER;
				int worldY = tileY * SceneConstants.TILE_SIZE + SceneConstants.TILE_CENTER;
				GraphicsObject graphics = new GraphicsObject(spotAnimationId, currentPlane, worldX, worldY,
						world.getTileHeight(worldX, worldY, currentPlane) - height, delay, currentCycle);
				world.graphicsObjects.addLast(graphics);
			}
			return;
		}

		if (updateType == ADD_GAME_OBJECT) {
			int typeAndOrientation = buffer.readUnsignedByteNeg();
			int type = typeAndOrientation >> LOCATION_TYPE_SHIFT;
			int orientation = typeAndOrientation & SceneConfig.ORIENTATION_MASK;
			int sceneLayer = SCENE_LAYERS_BY_TYPE[type];
			int objectId = buffer.readUnsignedShortAddLE();
			int packedTile = buffer.readUnsignedByteAdd();
			int tileX = zoneBaseX + (packedTile >> 4 & SceneConstants.CHUNK_COORDINATE_MASK);
			int tileY = zoneBaseY + (packedTile & SceneConstants.CHUNK_COORDINATE_MASK);
			if (tileX >= 0 && tileY >= 0 && tileX < SceneConstants.SIZE && tileY < SceneConstants.SIZE) {
				world.schedulePendingSpawn(currentPlane, tileX, tileY, sceneLayer, objectId, type, orientation, 0, -1);
			}
			return;
		}

		if (updateType == REMOVE_GROUND_ITEM) {
			int itemId = buffer.readUnsignedShortAdd();
			int packedTile = buffer.readUnsignedByteAdd();
			int tileX = zoneBaseX + (packedTile >> 4 & SceneConstants.CHUNK_COORDINATE_MASK);
			int tileY = zoneBaseY + (packedTile & SceneConstants.CHUNK_COORDINATE_MASK);
			if (tileX >= 0 && tileY >= 0 && tileX < SceneConstants.SIZE && tileY < SceneConstants.SIZE) {
				NodeDeque items = world.groundItems[currentPlane][tileX][tileY];
				if (items != null) {
					for (GroundItem item = (GroundItem) items.first(); item != null; item = (GroundItem) items.next()) {
						if (item.id == (itemId & GROUND_ITEM_ID_MASK)) {
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
			int tileX = zoneBaseX + (packedTile >> 4 & SceneConstants.CHUNK_COORDINATE_MASK);
			int tileY = zoneBaseY + (packedTile & SceneConstants.CHUNK_COORDINATE_MASK);
			int typeAndOrientation = buffer.readUnsignedByteSub();
			int type = typeAndOrientation >> LOCATION_TYPE_SHIFT;
			int orientation = typeAndOrientation & SceneConfig.ORIENTATION_MASK;
			int sceneLayer = SCENE_LAYERS_BY_TYPE[type];
			if (tileX >= 0 && tileY >= 0 && tileX < SceneConstants.SIZE && tileY < SceneConstants.SIZE) {
				world.schedulePendingSpawn(currentPlane, tileX, tileY, sceneLayer, -1, type, orientation, 0, -1);
			}
		}
	}
}
