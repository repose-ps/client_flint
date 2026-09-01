package rs2.action;

import rs2.cache.def.ItemDefinition;
import rs2.chat.ChatMessageType;
import rs2.chat.SocialManager;
import rs2.game.render.GameRenderer;
import rs2.net.Buffer;
import rs2.net.OutgoingPacketOpcode;
import rs2.ui.InterfaceController;
import rs2.ui.Widget;
import rs2.ui.menu.MenuState;

/** Applies revision-377 menu actions targeting inventory items and slots. */
public final class InventoryActionHandler implements ClientActionDispatcher.ActionHandler {
    /** Legacy anti-cheat accumulator for inventory item option 4. */
    private static int inventoryOption4Counter;
    /** Legacy anti-cheat accumulator for inventory item option 1. */
    private static int inventoryOption1Counter;

    /** Outgoing network session. */
    private final Buffer outgoing;
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
     * @param outgoing outgoing revision-377 packet buffer
     * @param interfaces interface state owner
     * @param gameRenderer renderer invalidation owner
     * @param resetInventoryClickCycle inventory-click timer reset callback
     * @param messages chat-message sink
     */
    public InventoryActionHandler(Buffer outgoing, InterfaceController interfaces,
            GameRenderer gameRenderer, Runnable resetInventoryClickCycle, SocialManager.MessageSink messages) {
        this.outgoing = outgoing;
        this.interfaces = interfaces;
        this.gameRenderer = gameRenderer;
        this.resetInventoryClickCycle = resetInventoryClickCycle;
        this.messages = messages;
    }

    /** {@inheritDoc} */
    @Override
    public boolean dispatch(int actionId, int cmd1, int cmd2, int cmd3, int menuIndex) {
        if (actionId == MenuState.INVENTORY_ITEM_OPTION_4) {
            inventoryOption4Counter++;
            if (inventoryOption4Counter >= 62) {
                outgoing.writeOpcode(OutgoingPacketOpcode.ANTI_CHEAT_INVENTORY_ITEM_OPTION_4);
                outgoing.writeByte(206);
                inventoryOption4Counter = 0;
            }
            outgoing.writeOpcode(OutgoingPacketOpcode.INVENTORY_ITEM_OPTION_4);
            outgoing.writeShortLE(cmd2);
            outgoing.writeShortAdd(cmd1);
            outgoing.writeShort(cmd3);
            markInventoryInteraction(cmd3, cmd2);
        }
        if (actionId == MenuState.INVENTORY_ITEM_OPTION_1) {
            inventoryOption1Counter += cmd1;
            if (inventoryOption1Counter >= 115) {
                outgoing.writeOpcode(OutgoingPacketOpcode.ANTI_CHEAT_INVENTORY_ITEM_OPTION_1);
                outgoing.writeByte(125);
                inventoryOption1Counter = 0;
            }
            outgoing.writeOpcode(OutgoingPacketOpcode.INVENTORY_ITEM_OPTION_1);
            outgoing.writeShortAdd(cmd3);
            outgoing.writeShortLE(cmd2);
            outgoing.writeShortLE(cmd1);
            markInventoryInteraction(cmd3, cmd2);
        }
        if (actionId == MenuState.WIDGET_ITEM_OPTION_1) {
            outgoing.writeOpcode(OutgoingPacketOpcode.WIDGET_ITEM_OPTION_1);
            outgoing.writeShortAdd(cmd1);
            outgoing.writeShort(cmd3);
            outgoing.writeShort(cmd2);
            markInventoryInteraction(cmd3, cmd2);
        }
        if (actionId == MenuState.INVENTORY_ITEM_OPTION_2) {
            outgoing.writeOpcode(OutgoingPacketOpcode.INVENTORY_ITEM_OPTION_2);
            outgoing.writeShortLE(cmd3);
            outgoing.writeShortLE(cmd1);
            outgoing.writeShortAdd(cmd2);
            markInventoryInteraction(cmd3, cmd2);
        }
        if (actionId == MenuState.USE_ITEM_ON_INVENTORY_ITEM) {
            outgoing.writeOpcode(OutgoingPacketOpcode.USE_ITEM_ON_INVENTORY_ITEM);
            outgoing.writeShort(cmd1);
            outgoing.writeShortLE(interfaces.state().selectedItemSlot);
            outgoing.writeShortLE(interfaces.state().selectedItemId);
            outgoing.writeShortLEAdd(interfaces.state().selectedItemWidgetId);
            outgoing.writeShortAdd(cmd2);
            outgoing.writeShortAdd(cmd3);
            markInventoryInteraction(cmd3, cmd2);
        }
        if (actionId == MenuState.CAST_SPELL_ON_INVENTORY_ITEM) {
            outgoing.writeOpcode(OutgoingPacketOpcode.CAST_SPELL_ON_INVENTORY_ITEM);
            outgoing.writeShort(interfaces.state().selectedSpellWidgetId);
            outgoing.writeShortAdd(cmd3);
            outgoing.writeShortAdd(cmd2);
            outgoing.writeShortAdd(cmd1);
            markInventoryInteraction(cmd3, cmd2);
        }
        if (actionId == MenuState.WIDGET_ITEM_OPTION_2) {
            outgoing.writeOpcode(OutgoingPacketOpcode.WIDGET_ITEM_OPTION_2);
            outgoing.writeShortAdd(cmd2);
            outgoing.writeShortLE(cmd1);
            outgoing.writeShortLE(cmd3);
            markInventoryInteraction(cmd3, cmd2);
        }
        if (actionId == MenuState.INVENTORY_ITEM_OPTION_5) {
            outgoing.writeOpcode(OutgoingPacketOpcode.INVENTORY_ITEM_OPTION_5);
            outgoing.writeShortLE(cmd2);
            outgoing.writeShortLEAdd(cmd1);
            outgoing.writeShortLEAdd(cmd3);
            markInventoryInteraction(cmd3, cmd2);
        }
        if (actionId == MenuState.WIDGET_ITEM_OPTION_5) {
            outgoing.writeOpcode(OutgoingPacketOpcode.WIDGET_ITEM_OPTION_5);
            outgoing.writeShortLEAdd(cmd2);
            outgoing.writeShortLEAdd(cmd1);
            outgoing.writeShortLE(cmd3);
            markInventoryInteraction(cmd3, cmd2);
        }
        if (actionId == MenuState.INVENTORY_ITEM_OPTION_3) {
            outgoing.writeOpcode(OutgoingPacketOpcode.INVENTORY_ITEM_OPTION_3);
            outgoing.writeShortLEAdd(cmd2);
            outgoing.writeShortLEAdd(cmd1);
            outgoing.writeShortLE(cmd3);
            markInventoryInteraction(cmd3, cmd2);
        }
        if (actionId == MenuState.EXAMINE_INVENTORY_ITEM) {
            ItemDefinition itemDefinition = ItemDefinition.lookup(cmd1);
            Widget widget = Widget.get(cmd3);
            String description;
            if (widget != null && widget.itemAmounts[cmd2] >= 0x186a0) {
                description = widget.itemAmounts[cmd2] + " x " + itemDefinition.name;
            } else if (itemDefinition.description != null) {
                description = new String(itemDefinition.description);
            } else {
                description = "It's a " + itemDefinition.name + ".";
            }
            messages.addChatMessage("", description, ChatMessageType.GAME);
        }
        if (actionId == MenuState.WIDGET_ITEM_OPTION_3) {
            outgoing.writeOpcode(OutgoingPacketOpcode.WIDGET_ITEM_OPTION_3);
            outgoing.writeShortLE(cmd1);
            outgoing.writeShortLEAdd(cmd2);
            outgoing.writeShort(cmd3);
            markInventoryInteraction(cmd3, cmd2);
        }
        if (actionId == MenuState.WIDGET_ITEM_OPTION_4) {
            outgoing.writeOpcode(OutgoingPacketOpcode.WIDGET_ITEM_OPTION_4);
            outgoing.writeShortLEAdd(cmd3);
            outgoing.writeShortLE(cmd2);
            outgoing.writeShort(cmd1);
            markInventoryInteraction(cmd3, cmd2);
        }
        if (actionId == MenuState.SELECT_ITEM) {
            interfaces.state().itemSelected = 1;
            interfaces.state().selectedItemSlot = cmd2;
            interfaces.state().selectedItemWidgetId = cmd3;
            interfaces.state().selectedItemId = cmd1;
            interfaces.state().selectedItemName = String.valueOf(ItemDefinition.lookup(cmd1).name);
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
