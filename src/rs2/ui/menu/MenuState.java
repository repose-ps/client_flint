package rs2.ui.menu;

/**
 * Fixed-size revision-377 context-menu state.
 *
 * Menu entries retain the original fixed-size representation and numeric action
 * semantics without exposing the classic five parallel arrays. Menu action IDs are
 * not renumbered: values >= 2000 are the original
 * low-priority variants and are normalized only when an action is dispatched.
 */
public final class MenuState {

	/** Creates a new menu state with its default client state. */
	public MenuState() {
	}
	/** Constant value for capacity. */
	public static final int CAPACITY = 500;
	/** Constant value for cancel action. */
	public static final int CANCEL_ACTION = 1016;

	/** Offset applied to menu actions that should be sorted behind normal actions. */
	public static final int LOW_PRIORITY_OFFSET = 2_000;

	/** Threshold used by the original context-menu priority partition. */
	private static final int PRIORITY_SORT_THRESHOLD = 1_000;

	/** Maximum number of entries built by entity menu builders before they stop. */
	public static final int BUILD_LIMIT = 400;

	/** Walk-to-tile action. */
	public static final int WALK_HERE = 14;
	/** First player option supplied by the server. */
	public static final int PLAYER_OPTION_1 = 200;
	/** Second player option supplied by the server. */
	public static final int PLAYER_OPTION_2 = 493;
	/** Third player option supplied by the server. */
	public static final int PLAYER_OPTION_3 = 408;
	/** Fourth player option supplied by the server. */
	public static final int PLAYER_OPTION_4 = 677;
	/** Fifth player option supplied by the server. */
	public static final int PLAYER_OPTION_5 = 876;
	/** Use the selected item on a player. */
	public static final int USE_ITEM_ON_PLAYER = 596;
	/** Cast the selected spell on a player. */
	public static final int CAST_SPELL_ON_PLAYER = 918;

	/** First NPC definition option. */
	public static final int NPC_OPTION_1 = 318;
	/** Second NPC definition option. */
	public static final int NPC_OPTION_2 = 921;
	/** Third NPC definition option. */
	public static final int NPC_OPTION_3 = 118;
	/** Fourth NPC definition option. */
	public static final int NPC_OPTION_4 = 553;
	/** Fifth NPC definition option. */
	public static final int NPC_OPTION_5 = 432;
	/** Use the selected item on an NPC. */
	public static final int USE_ITEM_ON_NPC = 347;
	/** Cast the selected spell on an NPC. */
	public static final int CAST_SPELL_ON_NPC = 67;
	/** Examine an NPC. */
	public static final int EXAMINE_NPC = 1668;

	/** First object-definition option. */
	public static final int OBJECT_OPTION_1 = 35;
	/** Second object-definition option. */
	public static final int OBJECT_OPTION_2 = 389;
	/** Third object-definition option. */
	public static final int OBJECT_OPTION_3 = 888;
	/** Fourth object-definition option. */
	public static final int OBJECT_OPTION_4 = 892;
	/** Fifth object-definition option. */
	public static final int OBJECT_OPTION_5 = 1280;
	/** Use the selected item on a scene object. */
	public static final int USE_ITEM_ON_OBJECT = 467;
	/** Cast the selected spell on a scene object. */
	public static final int CAST_SPELL_ON_OBJECT = 376;
	/** Examine a scene object. */
	public static final int EXAMINE_OBJECT = 1412;

	/** First ground-item definition option. */
	public static final int GROUND_ITEM_OPTION_1 = 68;
	/** Second ground-item definition option. */
	public static final int GROUND_ITEM_OPTION_2 = 26;
	/** Third ground-item definition option, normally Take. */
	public static final int GROUND_ITEM_OPTION_3 = 684;
	/** Fourth ground-item definition option. */
	public static final int GROUND_ITEM_OPTION_4 = 930;
	/** Fifth ground-item definition option. */
	public static final int GROUND_ITEM_OPTION_5 = 270;
	/** Use the selected item on a ground item. */
	public static final int USE_ITEM_ON_GROUND_ITEM = 100;
	/** Cast the selected spell on a ground item. */
	public static final int CAST_SPELL_ON_GROUND_ITEM = 199;
	/** Examine a ground item. */
	public static final int EXAMINE_GROUND_ITEM = 1564;

	/** First inventory action from the item definition. */
	public static final int INVENTORY_ITEM_OPTION_1 = 961;
	/** Second inventory action from the item definition. */
	public static final int INVENTORY_ITEM_OPTION_2 = 399;
	/** Third inventory action from the item definition. */
	public static final int INVENTORY_ITEM_OPTION_3 = 324;
	/** Fourth inventory action from the item definition. */
	public static final int INVENTORY_ITEM_OPTION_4 = 227;
	/** Fifth inventory action from the item definition, normally Drop. */
	public static final int INVENTORY_ITEM_OPTION_5 = 891;
	/** Select an inventory item for a subsequent use-with action. */
	public static final int SELECT_ITEM = 52;
	/** Use the selected item on another inventory item. */
	public static final int USE_ITEM_ON_INVENTORY_ITEM = 903;
	/** Cast the selected spell on an inventory item. */
	public static final int CAST_SPELL_ON_INVENTORY_ITEM = 361;
	/** Examine an inventory item. */
	public static final int EXAMINE_INVENTORY_ITEM = 1094;

	/** First action supplied by the containing inventory widget. */
	public static final int WIDGET_ITEM_OPTION_1 = 9;
	/** Second action supplied by the containing inventory widget. */
	public static final int WIDGET_ITEM_OPTION_2 = 225;
	/** Third action supplied by the containing inventory widget. */
	public static final int WIDGET_ITEM_OPTION_3 = 444;
	/** Fourth action supplied by the containing inventory widget. */
	public static final int WIDGET_ITEM_OPTION_4 = 564;
	/** Fifth action supplied by the containing inventory widget. */
	public static final int WIDGET_ITEM_OPTION_5 = 894;

	/** Standard widget button click. */
	public static final int WIDGET_BUTTON = 352;
	/** Select a spell widget. */
	public static final int SELECT_SPELL = 70;
	/** Close a chat/dialogue widget. */
	public static final int CLOSE_DIALOGUE = 55;
	/** Close a non-chat interface. */
	public static final int CLOSE_INTERFACE = 639;
	/** Toggle the varp controlled by a widget. */
	public static final int WIDGET_TOGGLE_VARP = 890;
	/** Set the varp controlled by a widget to its configured value. */
	public static final int WIDGET_SET_VARP = 518;
	/** Continue a dialogue or other one-shot interface action. */
	public static final int WIDGET_CONTINUE = 575;

	/** Add the selected name to the friend list. */
	public static final int ADD_FRIEND = 762;
	/** Add the selected name to the ignore list. */
	public static final int ADD_IGNORE = 574;
	/** Remove the selected name from the friend list. */
	public static final int REMOVE_FRIEND = 775;
	/** Remove the selected name from the ignore list. */
	public static final int REMOVE_IGNORE = 859;
	/** Open the private-message prompt for a friend. */
	public static final int MESSAGE_FRIEND = 984;
	/** Open Report Abuse for the selected name. */
	public static final int REPORT_ABUSE = 507;
	/** Accept a trade request from a named player. */
	public static final int ACCEPT_TRADE = 544;
	/** Accept a duel/challenge request from a named player. */
	public static final int ACCEPT_CHALLENGE = 695;

	/** Fixed-capacity menu entries. */
	private MenuEntry[] entries = new MenuEntry[CAPACITY];
	/** Stores the current count. */
	public int count;

	/** Whether open is enabled or active. */
	public boolean open;
	/** 0 = viewport, 1 = sidebar, 2 = chatbox. */
	public int screenArea;
	/** Stores the current offset X. */
	public int offsetX;
	/** Stores the current offset Y. */
	public int offsetY;
	/** Stores the current width. */
	public int width;
	/** Stores the current height. */
	public int height;

	/** Restores the one-entry default menu used before each rebuild. */
	public void reset() {
		entries[0] = new MenuEntry("Cancel", CANCEL_ACTION, 0, 0, 0);
		count = 1;
	}

	/**
	 * Returns the menu entry at an active index.
	 *
	 * @param index the menu index
	 * @return the menu entry
	 */
	public MenuEntry entry(int index) {
		return entries[index];
	}

	/**
	 * Appends a complete menu entry.
	 *
	 * @param entry the entry to append
	 */
	public void add(MenuEntry entry) {
		entries[count++] = entry;
	}

	/**
	 * Replaces one existing menu entry.
	 *
	 * @param index the menu index
	 * @param entry replacement entry
	 */
	void replace(int index, MenuEntry entry) {
		entries[index] = entry;
	}

	/**
	 * Preserve the original stable bubble partition: action IDs above 1000 move
	 * before action IDs below 1000. The comparison is intentionally strict.
	 */
	public void prioritizeActions() {
		for (boolean sorted = false; !sorted;) {
			sorted = true;
			for (int index = 0; index < count - 1; index++) {
				if (entries[index].action() < PRIORITY_SORT_THRESHOLD && entries[index + 1].action() > PRIORITY_SORT_THRESHOLD) {
					swap(index, index + 1);
					sorted = false;
				}
			}
		}
	}

	/**
	 * Returns whether add friend action.
	 *
	 * @param index the array or registry index
	 * @return whether add friend action
	 */
	public boolean isAddFriendAction(int index) {
		if (index < 0)
			return false;
		int actionId = normalizeActionId(entries[index].action());
		return actionId == ADD_FRIEND;
	}

	/**
	 * Returns the player-option action ID for a zero-based option index.
	 *
	 * @param index option index
	 * @return revision-377 action ID
	 */
	public static int playerOptionAction(int index) {
		return switch (index) {
		case 0 -> PLAYER_OPTION_1;
		case 1 -> PLAYER_OPTION_2;
		case 2 -> PLAYER_OPTION_3;
		case 3 -> PLAYER_OPTION_4;
		case 4 -> PLAYER_OPTION_5;
		default -> throw new IllegalArgumentException("player option index: " + index);
		};
	}

	/**
	 * Returns the item-definition inventory action ID for a zero-based option index.
	 *
	 * @param index option index
	 * @return revision-377 action ID
	 */
	public static int inventoryItemOptionAction(int index) {
		return switch (index) {
		case 0 -> INVENTORY_ITEM_OPTION_1;
		case 1 -> INVENTORY_ITEM_OPTION_2;
		case 2 -> INVENTORY_ITEM_OPTION_3;
		case 3 -> INVENTORY_ITEM_OPTION_4;
		case 4 -> INVENTORY_ITEM_OPTION_5;
		default -> throw new IllegalArgumentException("inventory option index: " + index);
		};
	}

	/**
	 * Returns the widget inventory action ID for a zero-based option index.
	 *
	 * @param index option index
	 * @return revision-377 action ID
	 */
	public static int widgetItemOptionAction(int index) {
		return switch (index) {
		case 0 -> WIDGET_ITEM_OPTION_1;
		case 1 -> WIDGET_ITEM_OPTION_2;
		case 2 -> WIDGET_ITEM_OPTION_3;
		case 3 -> WIDGET_ITEM_OPTION_4;
		case 4 -> WIDGET_ITEM_OPTION_5;
		default -> throw new IllegalArgumentException("widget item option index: " + index);
		};
	}

	/**
	 * Returns the NPC-option action ID for a zero-based option index.
	 *
	 * @param index option index
	 * @return revision-377 action ID
	 */
	public static int npcOptionAction(int index) {
		return switch (index) {
		case 0 -> NPC_OPTION_1;
		case 1 -> NPC_OPTION_2;
		case 2 -> NPC_OPTION_3;
		case 3 -> NPC_OPTION_4;
		case 4 -> NPC_OPTION_5;
		default -> throw new IllegalArgumentException("NPC option index: " + index);
		};
	}

	/**
	 * Returns the object-option action ID for a zero-based option index.
	 *
	 * @param index option index
	 * @return revision-377 action ID
	 */
	public static int objectOptionAction(int index) {
		return switch (index) {
		case 0 -> OBJECT_OPTION_1;
		case 1 -> OBJECT_OPTION_2;
		case 2 -> OBJECT_OPTION_3;
		case 3 -> OBJECT_OPTION_4;
		case 4 -> OBJECT_OPTION_5;
		default -> throw new IllegalArgumentException("object option index: " + index);
		};
	}

	/**
	 * Returns the ground-item option action ID for a zero-based option index.
	 *
	 * @param index option index
	 * @return revision-377 action ID
	 */
	public static int groundItemOptionAction(int index) {
		return switch (index) {
		case 0 -> GROUND_ITEM_OPTION_1;
		case 1 -> GROUND_ITEM_OPTION_2;
		case 2 -> GROUND_ITEM_OPTION_3;
		case 3 -> GROUND_ITEM_OPTION_4;
		case 4 -> GROUND_ITEM_OPTION_5;
		default -> throw new IllegalArgumentException("ground-item option index: " + index);
		};
	}

	/**
	 * Normalizes a menu action identifier by removing the priority offset.
	 *
	 * @param actionId the action ID
	 * @return the converted value
	 */
	public static int normalizeActionId(int actionId) {
		return actionId >= LOW_PRIORITY_OFFSET ? actionId - LOW_PRIORITY_OFFSET : actionId;
	}

	/**
	 * Returns the low-priority encoded form of a normal menu action identifier.
	 *
	 * @param actionId the normal action identifier
	 * @return the action identifier with the revision-377 priority offset applied
	 */
	public static int lowPriority(int actionId) {
		return actionId + LOW_PRIORITY_OFFSET;
	}

	/** Match the original quit-time release of menu entry storage. */
	public void clearReferencesForQuit() {
		entries = null;
	}

	/**
	 * Swaps two menu entries.
	 *
	 * @param first the first
	 * @param second the second
	 */
	private void swap(int first, int second) {
		MenuEntry entry = entries[first];
		entries[first] = entries[second];
		entries[second] = entry;
	}
}
