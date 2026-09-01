package rs2.action;

import rs2.game.ActorSynchronizer;
import rs2.game.entity.Actor;
import rs2.game.entity.Player;
import rs2.net.MovementPacketEncoder;
import rs2.ui.InterfaceController;
import rs2.ui.menu.MenuState;

/** Applies revision-377 menu actions targeting another player. */
public final class PlayerActionHandler implements ClientActionDispatcher.ActionHandler {
    /** Actor state used to resolve player indices. */
    private final ActorSynchronizer actors;
    /** Revision-377 action packet encoder. */
    private final ActionPacketEncoder packets;
    /** Current item/spell selection state. */
    private final InterfaceController interfaces;
    /** Movement capability supplied by the application coordinator. */
    private final ClientActionDispatcher.Movement movement;
    /** Marks the current click as an interaction crosshair. */
    private final Runnable markInteractionCrosshair;

    /**
     * Creates the player action handler.
     *
     * @param actors actor state
     * @param packets revision-377 action packet encoder
     * @param interfaces interface state owner
     * @param movement interaction movement capability
     * @param markInteractionCrosshair interaction-crosshair callback
     */
    public PlayerActionHandler(ActorSynchronizer actors, ActionPacketEncoder packets, InterfaceController interfaces,
            ClientActionDispatcher.Movement movement, Runnable markInteractionCrosshair) {
        this.actors = actors;
        this.packets = packets;
        this.interfaces = interfaces;
        this.movement = movement;
        this.markInteractionCrosshair = markInteractionCrosshair;
    }

    /** {@inheritDoc} */
    @Override
    public boolean dispatch(int actionId, int cmd1, int cmd2, int cmd3, int menuIndex) {
        if (actionId == MenuState.PLAYER_OPTION_1) {
            Player player = actors.players[cmd1];
            if (player != null) {
                walkTo(player);
                packets.playerOption1(cmd1);
            }
        }
        if (actionId == MenuState.PLAYER_OPTION_5) {
            Player player2 = actors.players[cmd1];
            if (player2 != null) {
                walkTo(player2);
                packets.playerOption5(cmd1);
            }
        }
        if (actionId == MenuState.PLAYER_OPTION_4) {
            Player player3 = actors.players[cmd1];
            if (player3 != null) {
                walkTo(player3);
                packets.playerOption4(cmd1);
            }
        }
        if (actionId == MenuState.PLAYER_OPTION_2) {
            Player player4 = actors.players[cmd1];
            if (player4 != null) {
                walkTo(player4);
                packets.playerOption2(cmd1);
            }
        }
        if (actionId == MenuState.CAST_SPELL_ON_PLAYER) {
            Player player5 = actors.players[cmd1];
            if (player5 != null) {
                walkTo(player5);
                packets.castSpellOnPlayer(cmd1, interfaces.state().selectedSpellWidgetId);
            }
        }
        if (actionId == MenuState.USE_ITEM_ON_PLAYER) {
            Player player6 = actors.players[cmd1];
            if (player6 != null) {
                walkTo(player6);
                packets.useItemOnPlayer(cmd1, interfaces.state().selectedItemId,
                        interfaces.state().selectedItemSlot, interfaces.state().selectedItemWidgetId);
            }
        }
        if (actionId == MenuState.PLAYER_OPTION_3) {
            Player player7 = actors.players[cmd1];
            if (player7 != null) {
                walkTo(player7);
                packets.playerOption3(cmd1);
            }
        }
        return false;
    }

    /**
     * Routes to a player and applies the interaction crosshair.
     *
     * @param player target player
     */
    private void walkTo(Player player) {
        movement.walkTo(false, ((Actor) player).pathX[0], ((Actor) player).pathY[0], 1, 1,
                MovementPacketEncoder.INTERACTION, 0, 0, 0);
        markInteractionCrosshair.run();
    }
}
