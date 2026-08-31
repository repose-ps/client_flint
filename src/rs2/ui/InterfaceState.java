package rs2.ui;

/**
 * Mutable revision-377 interface and inventory-widget interaction state.
 *
 * <p>{@link InterfaceController} owns lifecycle and interaction behavior, while
 * packet and menu domains coordinate through this state without moving those
 * responsibilities back into the top-level client coordinator.</p>
 */
public final class InterfaceState {

	/** Creates a new interface state with its default client state. */
	public InterfaceState() {
	}
	/** Stores the current open interface ID. */
	public int openInterfaceId = -1;
	/** Stores the current walkable interface ID. */
	public int walkableInterfaceId = -1;
	/** Stores the current chatbox interface ID. */
	public int chatboxInterfaceId = -1;
	/** Stores the current dialogue interface ID. */
	public int dialogueInterfaceId = -1;
	/** Stores the current sidebar overlay interface ID. */
	public int sidebarOverlayInterfaceId = -1;
	/** Stores the current fullscreen interface ID. */
	public int fullscreenInterfaceId = -1;
	/** Stores the current fullscreen overlay interface ID. */
	public int fullscreenOverlayInterfaceId = -1;
	/** Stores the current report abuse interface ID. */
	public int reportAbuseInterfaceId = -1;

	/** Stores tab interface IDs values. */
	public final int[] tabInterfaceIds = { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 };
	/** Stores the current selected tab. */
	public int selectedTab = 3;
	/** Stores the current flashing tab. */
	public int flashingTab = -1;

	/** Stores the current item selected. */
	public int itemSelected;
	/** Stores the current selected item slot. */
	public int selectedItemSlot;
	/** Stores the current selected item widget ID. */
	public int selectedItemWidgetId;
	/** Stores the current selected item ID. */
	public int selectedItemId;
	/** Stores the current selected item name. */
	public String selectedItemName;

	/** Stores the current spell selected. */
	public int spellSelected;
	/** Stores the current selected spell widget ID. */
	public int selectedSpellWidgetId;
	/** Stores the current selected spell target mask. */
	public int selectedSpellTargetMask;
	/** Stores the current selected spell action. */
	public String selectedSpellAction;

	/** Inventory slot currently under the mouse while building interface input. */
	public int hoveredInventorySlot;
	/** Stores the current hovered inventory widget ID. */
	public int hoveredInventoryWidgetId = -1;

	/** Inventory drag state started from a menu/click action. */
	public int draggedInventorySlot;
	/** Stores the current dragged inventory widget ID. */
	public int draggedInventoryWidgetId;
	/** Stores the current inventory drag area. */
	public int inventoryDragArea;
	/** Stores the current inventory drag start X. */
	public int inventoryDragStartX;
	/** Stores the current inventory drag start Y. */
	public int inventoryDragStartY;
	/** Stores the current inventory drag duration. */
	public int inventoryDragDuration;

	/** Short-lived pressed-item highlight used by interface rendering. */
	public int pressedInventoryWidgetId;
	/** Stores the current pressed inventory slot. */
	public int pressedInventorySlot;
	/** Stores the current pressed inventory area. */
	public int pressedInventoryArea;
}
