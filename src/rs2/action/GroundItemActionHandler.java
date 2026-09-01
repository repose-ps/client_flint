package rs2.action;

import rs2.cache.def.ItemDefinition;
import rs2.chat.ChatMessageType;
import rs2.chat.SocialManager;
import rs2.game.RegionManager;
import rs2.net.MovementPacketEncoder;
import rs2.net.Buffer;
import rs2.net.OutgoingPacketOpcode;
import rs2.ui.InterfaceController;
import rs2.ui.menu.MenuState;

/** Applies revision-377 menu actions targeting ground items. */
public final class GroundItemActionHandler implements ClientActionDispatcher.ActionHandler {
    /** Legacy anti-cheat accumulator for ground-item option 3. */
    private static int groundItemOption3Counter;
    /** Legacy anti-cheat accumulator for ground-item option 2. */
    private static int groundItemOption2Counter;

    /** Outgoing network session. */
    private final Buffer outgoing;
    /** Current item/spell selection state. */
    private final InterfaceController interfaces;
    /** Region base coordinates used by ground-item packets. */
    private final RegionManager regionManager;
    /** Movement capability supplied by the application coordinator. */
    private final ClientActionDispatcher.Movement movement;
    /** Marks the current click as an interaction crosshair. */
    private final Runnable markInteractionCrosshair;
    /** Chat-message sink used by examine actions. */
    private final SocialManager.MessageSink messages;

    /**
     * Creates the ground-item action handler.
     *
     * @param outgoing outgoing revision-377 packet buffer
     * @param interfaces interface state owner
     * @param regionManager region/base-coordinate owner
     * @param movement interaction movement capability
     * @param markInteractionCrosshair interaction-crosshair callback
     * @param messages chat-message sink
     */
    public GroundItemActionHandler(Buffer outgoing, InterfaceController interfaces,
            RegionManager regionManager, ClientActionDispatcher.Movement movement, Runnable markInteractionCrosshair,
            SocialManager.MessageSink messages) {
        this.outgoing = outgoing;
        this.interfaces = interfaces;
        this.regionManager = regionManager;
        this.movement = movement;
        this.markInteractionCrosshair = markInteractionCrosshair;
        this.messages = messages;
    }

    /** {@inheritDoc} */
    @Override
    public boolean dispatch(int actionId, int cmd1, int cmd2, int cmd3, int menuIndex) {
        if (actionId == MenuState.GROUND_ITEM_OPTION_4) {
            walkToGroundItem(cmd2, cmd3);
            outgoing.writeOpcode(OutgoingPacketOpcode.GROUND_ITEM_OPTION_4);
            outgoing.writeShortAdd(cmd1);
            outgoing.writeShortLE(cmd3 + regionManager.baseY);
            outgoing.writeShort(cmd2 + regionManager.baseX);
        }
        if (actionId == MenuState.GROUND_ITEM_OPTION_1) {
            walkToGroundItem(cmd2, cmd3);
            outgoing.writeOpcode(OutgoingPacketOpcode.GROUND_ITEM_OPTION_1);
            outgoing.writeShortAdd(cmd2 + regionManager.baseX);
            outgoing.writeShort(cmd3 + regionManager.baseY);
            outgoing.writeShortLEAdd(cmd1);
        }
        if (actionId == MenuState.GROUND_ITEM_OPTION_3) {
            walkToGroundItem(cmd2, cmd3);
            if ((cmd1 & 3) == 0) {
                groundItemOption3Counter++;
            }
            if (groundItemOption3Counter >= 84) {
                outgoing.writeOpcode(OutgoingPacketOpcode.ANTI_CHEAT_GROUND_ITEM_OPTION_3);
                outgoing.writeMedium(0xabc842);
                groundItemOption3Counter = 0;
            }
            outgoing.writeOpcode(OutgoingPacketOpcode.GROUND_ITEM_OPTION_3);
            outgoing.writeShortLEAdd(cmd1);
            outgoing.writeShortLEAdd(cmd2 + regionManager.baseX);
            outgoing.writeShortAdd(cmd3 + regionManager.baseY);
        }
        if (actionId == MenuState.GROUND_ITEM_OPTION_5) {
            walkToGroundItem(cmd2, cmd3);
            outgoing.writeOpcode(OutgoingPacketOpcode.GROUND_ITEM_OPTION_5);
            outgoing.writeShortLE(cmd1);
            outgoing.writeShortAdd(cmd2 + regionManager.baseX);
            outgoing.writeShort(cmd3 + regionManager.baseY);
        }
        if (actionId == MenuState.USE_ITEM_ON_GROUND_ITEM) {
            walkToGroundItem(cmd2, cmd3);
            outgoing.writeOpcode(OutgoingPacketOpcode.USE_ITEM_ON_GROUND_ITEM);
            outgoing.writeShortLEAdd(interfaces.state().selectedItemSlot);
            outgoing.writeShortAdd(interfaces.state().selectedItemId);
            outgoing.writeShortLEAdd(cmd3 + regionManager.baseY);
            outgoing.writeShortLEAdd(cmd2 + regionManager.baseX);
            outgoing.writeShortLE(interfaces.state().selectedItemWidgetId);
            outgoing.writeShortLE(cmd1);
        }
        if (actionId == MenuState.GROUND_ITEM_OPTION_2) {
            walkToGroundItem(cmd2, cmd3);
            groundItemOption2Counter++;
            if (groundItemOption2Counter >= 120) {
                outgoing.writeOpcode(OutgoingPacketOpcode.ANTI_CHEAT_GROUND_ITEM_OPTION_2);
                outgoing.writeInt(0);
                groundItemOption2Counter = 0;
            }
            outgoing.writeOpcode(OutgoingPacketOpcode.GROUND_ITEM_OPTION_2);
            outgoing.writeShort(cmd2 + regionManager.baseX);
            outgoing.writeShortAdd(cmd3 + regionManager.baseY);
            outgoing.writeShortLEAdd(cmd1);
        }
        if (actionId == MenuState.CAST_SPELL_ON_GROUND_ITEM) {
            walkToGroundItem(cmd2, cmd3);
            outgoing.writeOpcode(OutgoingPacketOpcode.CAST_SPELL_ON_GROUND_ITEM);
            outgoing.writeShortLE(cmd1);
            outgoing.writeShort(cmd3 + regionManager.baseY);
            outgoing.writeShortLE(interfaces.state().selectedSpellWidgetId);
            outgoing.writeShortLEAdd(cmd2 + regionManager.baseX);
        }
        if (actionId == MenuState.EXAMINE_GROUND_ITEM) {
            ItemDefinition itemDefinition = ItemDefinition.lookup(cmd1);
            String description;
            if (itemDefinition.description != null) {
                description = new String(itemDefinition.description);
            } else {
                description = "It's a " + itemDefinition.name + ".";
            }
            messages.addChatMessage("", description, ChatMessageType.GAME);
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public void resetForLogin() {
        groundItemOption2Counter = 0;
        groundItemOption3Counter = 0;
    }

    /**
     * Applies the original zero-size then one-tile route fallback and crosshair.
     *
     * @param tileX target local tile X
     * @param tileY target local tile Y
     */
    private void walkToGroundItem(int tileX, int tileY) {
        boolean routeFound = movement.walkTo(false, tileX, tileY, 0, 0, MovementPacketEncoder.INTERACTION, 0, 0, 0);
        if (!routeFound) {
            movement.walkTo(false, tileX, tileY, 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
        }
        markInteractionCrosshair.run();
    }
}
