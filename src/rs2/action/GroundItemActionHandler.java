package rs2.action;

import rs2.cache.def.ItemDefinition;
import rs2.chat.ChatMessageType;
import rs2.chat.SocialManager;
import rs2.game.RegionManager;
import rs2.net.MovementPacketEncoder;
import rs2.ui.InterfaceController;
import rs2.ui.menu.MenuEntry;
import rs2.ui.menu.MenuState;

/** Applies revision-377 menu actions targeting ground items. */
public final class GroundItemActionHandler implements ClientActionDispatcher.ActionHandler {
    /** Legacy anti-cheat accumulator for ground-item option 3. */
    private static int groundItemOption3Counter;
    /** Legacy anti-cheat accumulator for ground-item option 2. */
    private static int groundItemOption2Counter;

    /** Revision-377 action packet encoder. */
    private final ActionPacketEncoder packets;
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
     * @param packets revision-377 action packet encoder
     * @param interfaces interface state owner
     * @param regionManager region/base-coordinate owner
     * @param movement interaction movement capability
     * @param markInteractionCrosshair interaction-crosshair callback
     * @param messages chat-message sink
     */
    public GroundItemActionHandler(ActionPacketEncoder packets, InterfaceController interfaces,
            RegionManager regionManager, ClientActionDispatcher.Movement movement, Runnable markInteractionCrosshair,
            SocialManager.MessageSink messages) {
        this.packets = packets;
        this.interfaces = interfaces;
        this.regionManager = regionManager;
        this.movement = movement;
        this.markInteractionCrosshair = markInteractionCrosshair;
        this.messages = messages;
    }

    /** {@inheritDoc} */
    @Override
    public boolean dispatch(int actionId, MenuEntry entry) {
        int argument0 = entry.argument0();
        int argument1 = entry.argument1();
        int argument2 = entry.argument2();
        if (actionId == MenuState.GROUND_ITEM_OPTION_4) {
            walkToGroundItem(argument1, argument2);
            packets.groundItemOption4(argument0, argument1 + regionManager.baseX, argument2 + regionManager.baseY);
        }
        if (actionId == MenuState.GROUND_ITEM_OPTION_1) {
            walkToGroundItem(argument1, argument2);
            packets.groundItemOption1(argument0, argument1 + regionManager.baseX, argument2 + regionManager.baseY);
        }
        if (actionId == MenuState.GROUND_ITEM_OPTION_3) {
            walkToGroundItem(argument1, argument2);
            if ((argument0 & 3) == 0) {
                groundItemOption3Counter++;
            }
            if (groundItemOption3Counter >= 84) {
                packets.groundItemOption3AntiCheat();
                groundItemOption3Counter = 0;
            }
            packets.groundItemOption3(argument0, argument1 + regionManager.baseX, argument2 + regionManager.baseY);
        }
        if (actionId == MenuState.GROUND_ITEM_OPTION_5) {
            walkToGroundItem(argument1, argument2);
            packets.groundItemOption5(argument0, argument1 + regionManager.baseX, argument2 + regionManager.baseY);
        }
        if (actionId == MenuState.USE_ITEM_ON_GROUND_ITEM) {
            walkToGroundItem(argument1, argument2);
            packets.useItemOnGroundItem(argument0, argument1 + regionManager.baseX, argument2 + regionManager.baseY,
                    interfaces.state().selectedItemSlot, interfaces.state().selectedItemId,
                    interfaces.state().selectedItemWidgetId);
        }
        if (actionId == MenuState.GROUND_ITEM_OPTION_2) {
            walkToGroundItem(argument1, argument2);
            groundItemOption2Counter++;
            if (groundItemOption2Counter >= 120) {
                packets.groundItemOption2AntiCheat();
                groundItemOption2Counter = 0;
            }
            packets.groundItemOption2(argument0, argument1 + regionManager.baseX, argument2 + regionManager.baseY);
        }
        if (actionId == MenuState.CAST_SPELL_ON_GROUND_ITEM) {
            walkToGroundItem(argument1, argument2);
            packets.castSpellOnGroundItem(argument0, argument1 + regionManager.baseX, argument2 + regionManager.baseY,
                    interfaces.state().selectedSpellWidgetId);
        }
        if (actionId == MenuState.EXAMINE_GROUND_ITEM) {
            ItemDefinition itemDefinition = ItemDefinition.lookup(argument0);
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
