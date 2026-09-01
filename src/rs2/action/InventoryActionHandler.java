package rs2.action;

import rs2.cache.def.ItemDefinition;
import rs2.chat.ChatMessageType;
import rs2.chat.SocialManager;
import rs2.game.render.GameRenderer;
import rs2.ui.InterfaceController;
import rs2.ui.Widget;
import rs2.ui.menu.MenuEntry;
import rs2.ui.menu.MenuState;

/** Applies revision-377 menu actions targeting inventory items and slots. */
public final class InventoryActionHandler implements ClientActionDispatcher.ActionHandler {
    /** Legacy anti-cheat accumulator for inventory item option 4. */
    private static int inventoryOption4Counter;
    /** Legacy anti-cheat accumulator for inventory item option 1. */
    private static int inventoryOption1Counter;

    /** Revision-377 action packet encoder. */
    private final ActionPacketEncoder packets;
    /** Current interface/inventory selection state. */
    private final InterfaceController interfaces;
    /** Renderer invalidation owner. */
    private final GameRenderer gameRenderer;
    /** Resets the client-owned pressed-inventory click timer. */
    private final Runnable resetInventoryClickCycle;
    /** Chat-message sink used by examine actions. */
    private final SocialManager.MessageSink messages;

    /**
     * Creates the inventory action handler.
     *
     * @param packets revision-377 action packet encoder
     * @param interfaces interface state owner
     * @param gameRenderer renderer invalidation owner
     * @param resetInventoryClickCycle inventory-click timer reset callback
     * @param messages chat-message sink
     */
    public InventoryActionHandler(ActionPacketEncoder packets, InterfaceController interfaces,
            GameRenderer gameRenderer, Runnable resetInventoryClickCycle, SocialManager.MessageSink messages) {
        this.packets = packets;
        this.interfaces = interfaces;
        this.gameRenderer = gameRenderer;
        this.resetInventoryClickCycle = resetInventoryClickCycle;
        this.messages = messages;
    }

    /** {@inheritDoc} */
    @Override
    public boolean dispatch(int actionId, MenuEntry entry) {
        int argument0 = entry.argument0();
        int argument1 = entry.argument1();
        int argument2 = entry.argument2();
        if (actionId == MenuState.INVENTORY_ITEM_OPTION_4) {
            inventoryOption4Counter++;
            if (inventoryOption4Counter >= 62) {
                packets.inventoryItemOption4AntiCheat();
                inventoryOption4Counter = 0;
            }
            packets.inventoryItemOption4(argument0, argument1, argument2);
            markInventoryInteraction(argument2, argument1);
        }
        if (actionId == MenuState.INVENTORY_ITEM_OPTION_1) {
            inventoryOption1Counter += argument0;
            if (inventoryOption1Counter >= 115) {
                packets.inventoryItemOption1AntiCheat();
                inventoryOption1Counter = 0;
            }
            packets.inventoryItemOption1(argument0, argument1, argument2);
            markInventoryInteraction(argument2, argument1);
        }
        if (actionId == MenuState.WIDGET_ITEM_OPTION_1) {
            packets.widgetItemOption1(argument0, argument1, argument2);
            markInventoryInteraction(argument2, argument1);
        }
        if (actionId == MenuState.INVENTORY_ITEM_OPTION_2) {
            packets.inventoryItemOption2(argument0, argument1, argument2);
            markInventoryInteraction(argument2, argument1);
        }
        if (actionId == MenuState.USE_ITEM_ON_INVENTORY_ITEM) {
            packets.useItemOnInventoryItem(argument0, interfaces.state().selectedItemSlot,
                    interfaces.state().selectedItemId, interfaces.state().selectedItemWidgetId, argument1, argument2);
            markInventoryInteraction(argument2, argument1);
        }
        if (actionId == MenuState.CAST_SPELL_ON_INVENTORY_ITEM) {
            packets.castSpellOnInventoryItem(interfaces.state().selectedSpellWidgetId, argument2, argument1, argument0);
            markInventoryInteraction(argument2, argument1);
        }
        if (actionId == MenuState.WIDGET_ITEM_OPTION_2) {
            packets.widgetItemOption2(argument0, argument1, argument2);
            markInventoryInteraction(argument2, argument1);
        }
        if (actionId == MenuState.INVENTORY_ITEM_OPTION_5) {
            packets.inventoryItemOption5(argument0, argument1, argument2);
            markInventoryInteraction(argument2, argument1);
        }
        if (actionId == MenuState.WIDGET_ITEM_OPTION_5) {
            packets.widgetItemOption5(argument0, argument1, argument2);
            markInventoryInteraction(argument2, argument1);
        }
        if (actionId == MenuState.INVENTORY_ITEM_OPTION_3) {
            packets.inventoryItemOption3(argument0, argument1, argument2);
            markInventoryInteraction(argument2, argument1);
        }
        if (actionId == MenuState.EXAMINE_INVENTORY_ITEM) {
            ItemDefinition itemDefinition = ItemDefinition.lookup(argument0);
            Widget widget = Widget.get(argument2);
            String description;
            if (widget != null && widget.itemAmounts[argument1] >= 0x186a0) {
                description = widget.itemAmounts[argument1] + " x " + itemDefinition.name;
            } else if (itemDefinition.description != null) {
                description = new String(itemDefinition.description);
            } else {
                description = "It's a " + itemDefinition.name + ".";
            }
            messages.addChatMessage("", description, ChatMessageType.GAME);
        }
        if (actionId == MenuState.WIDGET_ITEM_OPTION_3) {
            packets.widgetItemOption3(argument0, argument1, argument2);
            markInventoryInteraction(argument2, argument1);
        }
        if (actionId == MenuState.WIDGET_ITEM_OPTION_4) {
            packets.widgetItemOption4(argument0, argument1, argument2);
            markInventoryInteraction(argument2, argument1);
        }
        if (actionId == MenuState.SELECT_ITEM) {
            interfaces.state().itemSelected = 1;
            interfaces.state().selectedItemSlot = argument1;
            interfaces.state().selectedItemWidgetId = argument2;
            interfaces.state().selectedItemId = argument0;
            interfaces.state().selectedItemName = String.valueOf(ItemDefinition.lookup(argument0).name);
            interfaces.state().spellSelected = 0;
            gameRenderer.requestSidebarRedraw();
            return true;
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public void resetForLogin() {
        inventoryOption4Counter = 0;
        inventoryOption1Counter = 0;
    }

    /**
     * Records the slot/widget affected by an inventory action.
     *
     * @param widgetId inventory widget ID
     * @param slot inventory slot
     */
    private void markInventoryInteraction(int widgetId, int slot) {
        resetInventoryClickCycle.run();
        interfaces.state().pressedInventoryWidgetId = widgetId;
        interfaces.state().pressedInventorySlot = slot;
        interfaces.state().pressedInventoryArea = 2;
        if (Widget.get(widgetId).parentId == interfaces.state().openInterfaceId) {
            interfaces.state().pressedInventoryArea = 1;
        }
        if (Widget.get(widgetId).parentId == interfaces.state().chatboxInterfaceId) {
            interfaces.state().pressedInventoryArea = 3;
        }
    }
}
