package rs2.net;

/**
 * Revision-377 client-to-server packet opcodes.
 *
 * <p>The names describe the packet's semantic role in this client. Numeric
 * values are protocol constants and must not be changed without changing the
 * revision-377 wire protocol.</p>
 */
public final class OutgoingPacketOpcode {

	/** Prevents instantiation. */
	private OutgoingPacketOpcode() {
	}

	/** Protocol/format constant for use item on inventory item. */
	public static final int USE_ITEM_ON_INVENTORY_ITEM = 1;
	/** Protocol/format constant for widget item option 1. */
	public static final int WIDGET_ITEM_OPTION_1 = 3;
	/** Protocol/format constant for inventory item option 5. */
	public static final int INVENTORY_ITEM_OPTION_5 = 4;
	/** Protocol/format constant for region loaded. */
	public static final int REGION_LOADED = 6;
	/** Protocol/format constant for npc option 5. */
	public static final int NPC_OPTION_5 = 8;
	/** Protocol/format constant for npc option 3. */
	public static final int NPC_OPTION_3 = 13;
	/** Protocol/format constant for mouse click. */
	public static final int MOUSE_CLICK = 19;
	/** Protocol/format constant for inventory item option 2. */
	public static final int INVENTORY_ITEM_OPTION_2 = 24;
	/** Protocol/format constant for walk screen. */
	public static final int WALK_SCREEN = 28;
	/** Protocol/format constant for cast spell on player. */
	public static final int CAST_SPELL_ON_PLAYER = 31;
	/** Protocol/format constant for cast spell on inventory item. */
	public static final int CAST_SPELL_ON_INVENTORY_ITEM = 36;
	/** Protocol/format constant for no timeout. */
	public static final int NO_TIMEOUT = 40;
	/** Protocol/format constant for npc option 4. */
	public static final int NPC_OPTION_4 = 42;
	/** Protocol/format constant for player option 5. */
	public static final int PLAYER_OPTION_5 = 45;
	/** Protocol/format constant for public chat. */
	public static final int PUBLIC_CHAT = 49;
	/** Protocol/format constant for object option 3. */
	public static final int OBJECT_OPTION_3 = 50;
	/** Protocol/format constant for ground item option 4. */
	public static final int GROUND_ITEM_OPTION_4 = 54;
	/** Protocol/format constant for object option 5. */
	public static final int OBJECT_OPTION_5 = 55;
	/** Protocol/format constant for command. */
	public static final int COMMAND = 56;
	/** Protocol/format constant for use item on npc. */
	public static final int USE_ITEM_ON_NPC = 57;
	/** Protocol/format constant for npc option 2. */
	public static final int NPC_OPTION_2 = 67;
	/** Protocol/format constant for ground item option 3. */
	public static final int GROUND_ITEM_OPTION_3 = 71;
	/** Protocol/format constant for input amount. */
	public static final int INPUT_AMOUNT = 75;
	/** Protocol/format constant for ground item option 1. */
	public static final int GROUND_ITEM_OPTION_1 = 77;
	/** Protocol/format constant for region load check. */
	public static final int REGION_LOAD_CHECK = 78;
	/** Protocol/format constant for widget click. */
	public static final int WIDGET_CLICK = 79;
	/** Protocol/format constant for sound effect error. */
	public static final int SOUND_EFFECT_ERROR = 80;
	/** Protocol/format constant for cast spell on ground item. */
	public static final int CAST_SPELL_ON_GROUND_ITEM = 83;
	/** Protocol/format constant for widget item option 3. */
	public static final int WIDGET_ITEM_OPTION_3 = 91;
	/** Protocol/format constant for anti cheat ground item option 2. */
	public static final int ANTI_CHEAT_GROUND_ITEM_OPTION_2 = 95;
	/** Protocol/format constant for ground item option 2. */
	public static final int GROUND_ITEM_OPTION_2 = 100;
	/** Protocol/format constant for cast spell on npc. */
	public static final int CAST_SPELL_ON_NPC = 104;
	/** Protocol/format constant for close interfaces. */
	public static final int CLOSE_INTERFACES = 110;
	/** Protocol/format constant for npc option 1. */
	public static final int NPC_OPTION_1 = 112;
	/** Protocol/format constant for player option 4. */
	public static final int PLAYER_OPTION_4 = 116;
	/** Protocol/format constant for flashing tab acknowledgement. */
	public static final int FLASHING_TAB_ACKNOWLEDGEMENT = 119;
	/** Protocol/format constant for add friend. */
	public static final int ADD_FRIEND = 120;
	/** Protocol/format constant for reorder inventory item. */
	public static final int REORDER_INVENTORY_ITEM = 123;
	/** Protocol/format constant for anti cheat inventory item option 1. */
	public static final int ANTI_CHEAT_INVENTORY_ITEM_OPTION_1 = 126;
	/** Protocol/format constant for object option 4. */
	public static final int OBJECT_OPTION_4 = 136;
	/** Protocol/format constant for camera orientation. */
	public static final int CAMERA_ORIENTATION = 140;
	/** Protocol/format constant for remove friend. */
	public static final int REMOVE_FRIEND = 141;
	/** Protocol/format constant for use item on player. */
	public static final int USE_ITEM_ON_PLAYER = 143;
	/** Protocol/format constant for use item on object. */
	public static final int USE_ITEM_ON_OBJECT = 152;
	/** Protocol/format constant for anti cheat npc option 3. */
	public static final int ANTI_CHEAT_NPC_OPTION_3 = 157;
	/** Protocol/format constant for widget item option 5. */
	public static final int WIDGET_ITEM_OPTION_5 = 158;
	/** Protocol/format constant for remove ignore. */
	public static final int REMOVE_IGNORE = 160;
	/** Protocol/format constant for inventory item option 3. */
	public static final int INVENTORY_ITEM_OPTION_3 = 161;
	/** Protocol/format constant for appearance update. */
	public static final int APPEARANCE_UPDATE = 163;
	/** Protocol/format constant for anti cheat inventory item option 4. */
	public static final int ANTI_CHEAT_INVENTORY_ITEM_OPTION_4 = 165;
	/** Protocol/format constant for screen redraw keepalive. */
	public static final int SCREEN_REDRAW_KEEPALIVE = 168;
	/** Protocol/format constant for mouse movement. */
	public static final int MOUSE_MOVEMENT = 171;
	/** Protocol/format constant for minimap rebuild keepalive. */
	public static final int MINIMAP_REBUILD_KEEPALIVE = 173;
	/** Protocol/format constant for chat modes. */
	public static final int CHAT_MODES = 176;
	/** Protocol/format constant for widget item option 2. */
	public static final int WIDGET_ITEM_OPTION_2 = 177;
	/** Protocol/format constant for object option 1. */
	public static final int OBJECT_OPTION_1 = 181;
	/** Protocol/format constant for report abuse. */
	public static final int REPORT_ABUSE = 184;
	/** Protocol/format constant for window focus. */
	public static final int WINDOW_FOCUS = 187;
	/** Protocol/format constant for player option 3. */
	public static final int PLAYER_OPTION_3 = 194;
	/** Protocol/format constant for system update keepalive. */
	public static final int SYSTEM_UPDATE_KEEPALIVE = 197;
	/** Protocol/format constant for idle. */
	public static final int IDLE = 202;
	/** Protocol/format constant for inventory item option 1. */
	public static final int INVENTORY_ITEM_OPTION_1 = 203;
	/** Protocol/format constant for input name. */
	public static final int INPUT_NAME = 206;
	/** Protocol/format constant for cast spell on object. */
	public static final int CAST_SPELL_ON_OBJECT = 210;
	/** Protocol/format constant for use item on ground item. */
	public static final int USE_ITEM_ON_GROUND_ITEM = 211;
	/** Protocol/format constant for walk minimap. */
	public static final int WALK_MINIMAP = 213;
	/** Protocol/format constant for add ignore. */
	public static final int ADD_IGNORE = 217;
	/** Protocol/format constant for anti cheat ground item option 3. */
	public static final int ANTI_CHEAT_GROUND_ITEM_OPTION_3 = 222;
	/** Protocol/format constant for widget continue. */
	public static final int WIDGET_CONTINUE = 226;
	/** Protocol/format constant for private chat. */
	public static final int PRIVATE_CHAT = 227;
	/** Protocol/format constant for inventory item option 4. */
	public static final int INVENTORY_ITEM_OPTION_4 = 228;
	/** Protocol/format constant for ground item option 5. */
	public static final int GROUND_ITEM_OPTION_5 = 230;
	/** Protocol/format constant for widget item option 4. */
	public static final int WIDGET_ITEM_OPTION_4 = 231;
	/** Protocol/format constant for player option 2. */
	public static final int PLAYER_OPTION_2 = 233;
	/** Protocol/format constant for object option 2. */
	public static final int OBJECT_OPTION_2 = 241;
	/** Protocol/format constant for camera probe. */
	public static final int CAMERA_PROBE = 244;
	/** Protocol/format constant for player option 1. */
	public static final int PLAYER_OPTION_1 = 245;
	/** Protocol/format constant for walk interaction. */
	public static final int WALK_INTERACTION = 247;
	/** Protocol/format constant for projectile keepalive. */
	public static final int PROJECTILE_KEEPALIVE = 248;
}
