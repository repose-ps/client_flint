package rs2.action;

import rs2.cache.def.NpcDefinition;
import rs2.chat.ChatMessageType;
import rs2.chat.SocialManager;
import rs2.game.ActorSynchronizer;
import rs2.game.entity.Actor;
import rs2.game.entity.Npc;
import rs2.net.MovementPacketEncoder;
import rs2.net.Buffer;
import rs2.net.OutgoingPacketOpcode;
import rs2.ui.InterfaceController;
import rs2.ui.menu.MenuState;

/** Applies revision-377 menu actions targeting NPCs. */
public final class NpcActionHandler implements ClientActionDispatcher.ActionHandler {
    /** Legacy anti-cheat accumulator for NPC option 3. */
    private static int npcOption3Counter;

    /** Actor state used to resolve NPC indices. */
    private final ActorSynchronizer actors;
    /** Outgoing network session. */
    private final Buffer outgoing;
    /** Current item/spell selection state. */
    private final InterfaceController interfaces;
    /** Movement capability supplied by the application coordinator. */
    private final ClientActionDispatcher.Movement movement;
    /** Marks the current click as an interaction crosshair. */
    private final Runnable markInteractionCrosshair;
    /** Chat-message sink used by examine actions. */
    private final SocialManager.MessageSink messages;

    /**
     * Creates the NPC action handler.
     *
     * @param actors actor state
     * @param outgoing outgoing revision-377 packet buffer
     * @param interfaces interface state owner
     * @param movement interaction movement capability
     * @param markInteractionCrosshair interaction-crosshair callback
     * @param messages chat-message sink
     */
    public NpcActionHandler(ActorSynchronizer actors, Buffer outgoing, InterfaceController interfaces,
            ClientActionDispatcher.Movement movement, Runnable markInteractionCrosshair,
            SocialManager.MessageSink messages) {
        this.actors = actors;
        this.outgoing = outgoing;
        this.interfaces = interfaces;
        this.movement = movement;
        this.markInteractionCrosshair = markInteractionCrosshair;
        this.messages = messages;
    }

    /** {@inheritDoc} */
    @Override
    public boolean dispatch(int actionId, int cmd1, int cmd2, int cmd3, int menuIndex) {
        if (actionId == MenuState.NPC_OPTION_2) {
            Npc npc = actors.npcs[cmd1];
            if (npc != null) {
                walkTo(npc);
                outgoing.writeOpcode(OutgoingPacketOpcode.NPC_OPTION_2);
                outgoing.writeShortAdd(cmd1);
            }
        }
        if (actionId == MenuState.NPC_OPTION_4) {
            Npc npc2 = actors.npcs[cmd1];
            if (npc2 != null) {
                walkTo(npc2);
                outgoing.writeOpcode(OutgoingPacketOpcode.NPC_OPTION_4);
                outgoing.writeShortLE(cmd1);
            }
        }
        if (actionId == MenuState.USE_ITEM_ON_NPC) {
            Npc npc3 = actors.npcs[cmd1];
            if (npc3 != null) {
                walkTo(npc3);
                outgoing.writeOpcode(OutgoingPacketOpcode.USE_ITEM_ON_NPC);
                outgoing.writeShort(cmd1);
                outgoing.writeShortLE(interfaces.state().selectedItemId);
                outgoing.writeShortLEAdd(interfaces.state().selectedItemWidgetId);
                outgoing.writeShort(interfaces.state().selectedItemSlot);
            }
        }
        if (actionId == MenuState.NPC_OPTION_3) {
            Npc npc4 = actors.npcs[cmd1];
            if (npc4 != null) {
                walkTo(npc4);
                npcOption3Counter += cmd1;
                if (npcOption3Counter >= 143) {
                    outgoing.writeOpcode(OutgoingPacketOpcode.ANTI_CHEAT_NPC_OPTION_3);
                    outgoing.writeInt(0);
                    npcOption3Counter = 0;
                }
                outgoing.writeOpcode(OutgoingPacketOpcode.NPC_OPTION_3);
                outgoing.writeShortLEAdd(cmd1);
            }
        }
        if (actionId == MenuState.NPC_OPTION_5) {
            Npc npc5 = actors.npcs[cmd1];
            if (npc5 != null) {
                walkTo(npc5);
                outgoing.writeOpcode(OutgoingPacketOpcode.NPC_OPTION_5);
                outgoing.writeShortLE(cmd1);
            }
        }
        if (actionId == MenuState.CAST_SPELL_ON_NPC) {
            Npc npc6 = actors.npcs[cmd1];
            if (npc6 != null) {
                walkTo(npc6);
                outgoing.writeOpcode(OutgoingPacketOpcode.CAST_SPELL_ON_NPC);
                outgoing.writeShortAdd(interfaces.state().selectedSpellWidgetId);
                outgoing.writeShortLE(cmd1);
            }
        }
        if (actionId == MenuState.EXAMINE_NPC) {
            Npc npc7 = actors.npcs[cmd1];
            if (npc7 != null) {
                NpcDefinition npcDefinition = npc7.definition;
                if (npcDefinition.morphIds != null) {
                    npcDefinition = npcDefinition.transform();
                }
                if (npcDefinition != null) {
                    String description;
                    if (npcDefinition.description != null) {
                        description = new String(npcDefinition.description);
                    } else {
                        description = "It's a " + npcDefinition.name + ".";
                    }
                    messages.addChatMessage("", description, ChatMessageType.GAME);
                }
            }
        }
        if (actionId == MenuState.NPC_OPTION_1) {
            Npc npc8 = actors.npcs[cmd1];
            if (npc8 != null) {
                walkTo(npc8);
                outgoing.writeOpcode(OutgoingPacketOpcode.NPC_OPTION_1);
                outgoing.writeShortLE(cmd1);
            }
        }
        return false;
    }

    /** {@inheritDoc} */
    @Override
    public void resetForLogin() {
        npcOption3Counter = 0;
    }

    /**
     * Routes to an NPC and applies the interaction crosshair.
     *
     * @param npc target NPC
     */
    private void walkTo(Npc npc) {
        movement.walkTo(false, ((Actor) npc).pathX[0], ((Actor) npc).pathY[0], 1, 1,
                MovementPacketEncoder.INTERACTION, 0, 0, 0);
        markInteractionCrosshair.run();
    }
}
