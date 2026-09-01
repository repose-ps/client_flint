package rs2.net;

/**
 * Revision-377 server-to-client packet opcodes handled by the client.
 *
 * <p>
 * These constants name protocol values only; packet lengths remain defined by
 * {@link IncomingPacketLengths}.
 * </p>
 */
public final class IncomingPacketOpcode {

	/** Prevents instantiation. */
	private IncomingPacketOpcode() {
	}

	/** Protocol/format constant for set widget animation. */
	public static final int SET_WIDGET_ANIMATION = 2;
	/** Protocol/format constant for set cinematic camera position. */
	public static final int SET_CINEMATIC_CAMERA_POSITION = 3;
	/** Protocol/format constant for logout. */
	public static final int LOGOUT = 5;
	/** Protocol/format constant for open name input dialog. */
	public static final int OPEN_NAME_INPUT_DIALOG = 6;
	/** Protocol/format constant for set tab interface. */
	public static final int SET_TAB_INTERFACE = 10;
	/** Protocol/format constant for reset entity animations. */
	public static final int RESET_ENTITY_ANIMATIONS = 13;
	/** Protocol/format constant for set widget model rotation speed. */
	public static final int SET_WIDGET_MODEL_ROTATION_SPEED = 18;
	/** Protocol/format constant for set widget item model. */
	public static final int SET_WIDGET_ITEM_MODEL = 21;
	/** Protocol/format constant for play sound effect. */
	public static final int PLAY_SOUND_EFFECT = 26;
	/** Protocol/format constant for close interfaces. */
	public static final int CLOSE_INTERFACES = 29;
	/** Protocol/format constant for clear zone. */
	public static final int CLEAR_ZONE = 40;
	/** Protocol/format constant for play area sound. */
	public static final int PLAY_AREA_SOUND = 41;
	/** Protocol/format constant for update skill. */
	public static final int UPDATE_SKILL = 49;
	/** Protocol/format constant for set walkable interface. */
	public static final int SET_WALKABLE_INTERFACE = 50;
	/** Protocol/format constant for rebuild instanced region. */
	public static final int REBUILD_INSTANCED_REGION = 53;
	/** Protocol/format constant for open amount input dialog. */
	public static final int OPEN_AMOUNT_INPUT_DIALOG = 58;
	/** Protocol/format constant for add graphics object. */
	public static final int ADD_GRAPHICS_OBJECT = 59;
	/** Protocol/format constant for clear destination. */
	public static final int CLEAR_DESTINATION = 61;
	/** Protocol/format constant for server message. */
	public static final int SERVER_MESSAGE = 63;
	/** Protocol/format constant for camera shake. */
	public static final int CAMERA_SHAKE = 67;
	/** Protocol/format constant for npc update. */
	public static final int NPC_UPDATE = 71;
	/** Protocol/format constant for set zone base. */
	public static final int SET_ZONE_BASE = 75;
	/** Protocol/format constant for account info. */
	public static final int ACCOUNT_INFO = 76;
	/** Protocol/format constant for friend status. */
	public static final int FRIEND_STATUS = 78;
	/** Protocol/format constant for set widget mouseover. */
	public static final int SET_WIDGET_MOUSEOVER = 82;
	/** Protocol/format constant for remove game object. */
	public static final int REMOVE_GAME_OBJECT = 88;
	/** Protocol/format constant for player update. */
	public static final int PLAYER_UPDATE = 90;
	/** Protocol/format constant for add ground item for other player. */
	public static final int ADD_GROUND_ITEM_FOR_OTHER_PLAYER = 106;
	/** Protocol/format constant for add ground item. */
	public static final int ADD_GROUND_ITEM = 107;
	/** Protocol/format constant for open chatbox interface. */
	public static final int OPEN_CHATBOX_INTERFACE = 109;
	/** Protocol/format constant for synchronize varps. */
	public static final int SYNCHRONIZE_VARPS = 113;
	/** Protocol/format constant for set varp large. */
	public static final int SET_VARP_LARGE = 115;
	/** Protocol/format constant for update ground item amount. */
	public static final int UPDATE_GROUND_ITEM_AMOUNT = 121;
	/** Protocol/format constant for update run energy. */
	public static final int UPDATE_RUN_ENERGY = 125;
	/** Protocol/format constant for set local player index. */
	public static final int SET_LOCAL_PLAYER_INDEX = 126;
	/** Protocol/format constant for open main and sidebar interfaces. */
	public static final int OPEN_MAIN_AND_SIDEBAR_INTERFACES = 128;
	/** Protocol/format constant for update widget items partial. */
	public static final int UPDATE_WIDGET_ITEMS_PARTIAL = 134;
	/** Protocol/format constant for private message. */
	public static final int PRIVATE_MESSAGE = 135;
	/** Protocol/format constant for animate game object. */
	public static final int ANIMATE_GAME_OBJECT = 142;
	/** Protocol/format constant for reset camera. */
	public static final int RESET_CAMERA = 148;
	/** Protocol/format constant for add game object. */
	public static final int ADD_GAME_OBJECT = 152;
	/** Protocol/format constant for set minimap state. */
	public static final int SET_MINIMAP_STATE = 156;
	/** Protocol/format constant for set player action. */
	public static final int SET_PLAYER_ACTION = 157;
	/** Protocol/format constant for set dialogue interface. */
	public static final int SET_DIALOGUE_INTERFACE = 158;
	/** Protocol/format constant for open main interface. */
	public static final int OPEN_MAIN_INTERFACE = 159;
	/** Protocol/format constant for set widget npc model. */
	public static final int SET_WIDGET_NPC_MODEL = 162;
	/** Protocol/format constant for set widget position. */
	public static final int SET_WIDGET_POSITION = 166;
	/** Protocol/format constant for set cinematic camera look at. */
	public static final int SET_CINEMATIC_CAMERA_LOOK_AT = 167;
	/** Protocol/format constant for update weight. */
	public static final int UPDATE_WEIGHT = 174;
	/** Protocol/format constant for add projectile. */
	public static final int ADD_PROJECTILE = 181;
	/** Protocol/format constant for set varp small. */
	public static final int SET_VARP_SMALL = 182;
	/** Protocol/format constant for batch zone updates. */
	public static final int BATCH_ZONE_UPDATES = 183;
	/** Protocol/format constant for set widget model transform. */
	public static final int SET_WIDGET_MODEL_TRANSFORM = 186;
	/** Protocol/format constant for set system update timer. */
	public static final int SET_SYSTEM_UPDATE_TIMER = 190;
	/** Protocol/format constant for set hint icon. */
	public static final int SET_HINT_ICON = 199;
	/** Protocol/format constant for set widget scroll position. */
	public static final int SET_WIDGET_SCROLL_POSITION = 200;
	/** Protocol/format constant for set chat modes. */
	public static final int SET_CHAT_MODES = 201;
	/** Protocol/format constant for attach object to player. */
	public static final int ATTACH_OBJECT_TO_PLAYER = 203;
	/** Protocol/format constant for update widget items. */
	public static final int UPDATE_WIDGET_ITEMS = 206;
	/** Protocol/format constant for remove ground item. */
	public static final int REMOVE_GROUND_ITEM = 208;
	/** Protocol/format constant for set widget model. */
	public static final int SET_WIDGET_MODEL = 216;
	/** Protocol/format constant for set widget color. */
	public static final int SET_WIDGET_COLOR = 218;
	/** Protocol/format constant for clear widget items. */
	public static final int CLEAR_WIDGET_ITEMS = 219;
	/** Protocol/format constant for play music. */
	public static final int PLAY_MUSIC = 220;
	/** Protocol/format constant for rebuild region. */
	public static final int REBUILD_REGION = 222;
	/** Protocol/format constant for update ignore list. */
	public static final int UPDATE_IGNORE_LIST = 226;
	/** Protocol/format constant for set widget text. */
	public static final int SET_WIDGET_TEXT = 232;
	/** Protocol/format constant for set multi combat. */
	public static final int SET_MULTI_COMBAT = 233;
	/** Protocol/format constant for flash tab. */
	public static final int FLASH_TAB = 238;
	/** Protocol/format constant for open sidebar interface. */
	public static final int OPEN_SIDEBAR_INTERFACE = 246;
	/** Protocol/format constant for play temporary music. */
	public static final int PLAY_TEMPORARY_MUSIC = 249;
	/** Protocol/format constant for set friend list status. */
	public static final int SET_FRIEND_LIST_STATUS = 251;
	/** Protocol/format constant for set selected tab. */
	public static final int SET_SELECTED_TAB = 252;
	/** Protocol/format constant for open fullscreen interfaces. */
	public static final int OPEN_FULLSCREEN_INTERFACES = 253;
	/** Protocol/format constant for set widget player model. */
	public static final int SET_WIDGET_PLAYER_MODEL = 255;
}
