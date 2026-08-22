package rs2.ui;

/**
 * Mutable Client-side state for revision-377 interfaces and inventory-widget
 * interaction. Protocol/menu code remains in the Client until its own refactor
 * steps; this class only owns the state those systems coordinate through.
 */
public final class InterfaceState {
	public int openInterfaceId = -1;
	public int walkableInterfaceId = -1;
	public int chatboxInterfaceId = -1;
	public int dialogueInterfaceId = -1;
	public int sidebarOverlayInterfaceId = -1;
	public int fullscreenInterfaceId = -1;
	public int fullscreenOverlayInterfaceId = -1;
	public int reportAbuseInterfaceId = -1;

	public final int[] tabInterfaceIds = { -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1 };
	public int selectedTab = 3;
	public int flashingTab = -1;

	public int itemSelected;
	public int selectedItemSlot;
	public int selectedItemWidgetId;
	public int selectedItemId;
	public String selectedItemName;

	public int spellSelected;
	public int selectedSpellWidgetId;
	public int selectedSpellTargetMask;
	public String selectedSpellAction;

	/** Inventory slot currently under the mouse while building interface input. */
	public int hoveredInventorySlot;
	public int hoveredInventoryWidgetId = -1;

	/** Inventory drag state started from a menu/click action. */
	public int draggedInventorySlot;
	public int draggedInventoryWidgetId;
	public int inventoryDragArea;
	public int inventoryDragStartX;
	public int inventoryDragStartY;
	public int inventoryDragDuration;

	/** Short-lived pressed-item highlight used by interface rendering. */
	public int pressedInventoryWidgetId;
	public int pressedInventorySlot;
	public int pressedInventoryArea;
}