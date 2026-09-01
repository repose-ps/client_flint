package rs2.action;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

import rs2.cache.def.GameObjectDefinition;
import rs2.chat.ChatMessageType;
import rs2.chat.SocialManager;
import rs2.game.WorldState;
import rs2.net.MovementPacketEncoder;
import rs2.net.Buffer;
import rs2.net.OutgoingPacketOpcode;
import rs2.scene.SceneConfig;
import rs2.scene.SceneUid;
import rs2.ui.InterfaceController;
import rs2.ui.menu.MenuState;
import rs2.game.RegionManager;

/** Applies revision-377 menu actions targeting scene objects. */
public final class ObjectActionHandler implements ClientActionDispatcher.ActionHandler {
    /** Outgoing network session. */
    private final Buffer outgoing;
    /** Current item/spell selection state. */
    private final InterfaceController interfaces;
    /** Region base coordinates used by object packets. */
    private final RegionManager regionManager;
    /** Supplies the active world once startup has initialized it. */
    private final Supplier<WorldState> worldState;
    /** Supplies the current scene plane. */
    private final IntSupplier currentPlane;
    /** Movement capability supplied by the application coordinator. */
    private final ClientActionDispatcher.Movement movement;
    /** Marks the current click as an interaction crosshair. */
    private final Runnable markInteractionCrosshair;
    /** Chat-message sink used by examine actions. */
    private final SocialManager.MessageSink messages;

    /**
     * Creates the object action handler.
     *
     * @param outgoing outgoing revision-377 packet buffer
     * @param interfaces interface state owner
     * @param regionManager region/base-coordinate owner
     * @param worldState active world supplier
     * @param currentPlane current-plane supplier
     * @param movement interaction movement capability
     * @param markInteractionCrosshair interaction-crosshair callback
     * @param messages chat-message sink
     */
    public ObjectActionHandler(Buffer outgoing, InterfaceController interfaces, RegionManager regionManager,
            Supplier<WorldState> worldState, IntSupplier currentPlane, ClientActionDispatcher.Movement movement,
            Runnable markInteractionCrosshair, SocialManager.MessageSink messages) {
        this.outgoing = outgoing;
        this.interfaces = interfaces;
        this.regionManager = regionManager;
        this.worldState = worldState;
        this.currentPlane = currentPlane;
        this.movement = movement;
        this.markInteractionCrosshair = markInteractionCrosshair;
        this.messages = messages;
    }

    /** {@inheritDoc} */
    @Override
    public boolean dispatch(int actionId, int cmd1, int cmd2, int cmd3, int menuIndex) {
        if (actionId == MenuState.USE_ITEM_ON_OBJECT && walkToGameObject(cmd3, cmd2, cmd1)) {
            outgoing.writeOpcode(OutgoingPacketOpcode.USE_ITEM_ON_OBJECT);
            outgoing.writeShortLE(cmd1 >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK);
            outgoing.writeShortLE(interfaces.state().selectedItemWidgetId);
            outgoing.writeShortLE(interfaces.state().selectedItemId);
            outgoing.writeShortLE(cmd3 + regionManager.baseY);
            outgoing.writeShort(interfaces.state().selectedItemSlot);
            outgoing.writeShortLEAdd(cmd2 + regionManager.baseX);
        }
        if (actionId == MenuState.CAST_SPELL_ON_OBJECT && walkToGameObject(cmd3, cmd2, cmd1)) {
            outgoing.writeOpcode(OutgoingPacketOpcode.CAST_SPELL_ON_OBJECT);
            outgoing.writeShort(interfaces.state().selectedSpellWidgetId);
            outgoing.writeShortLE(cmd1 >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK);
            outgoing.writeShortAdd(cmd2 + regionManager.baseX);
            outgoing.writeShortLE(cmd3 + regionManager.baseY);
        }
        if (actionId == MenuState.OBJECT_OPTION_5) {
            walkToGameObject(cmd3, cmd2, cmd1);
            outgoing.writeOpcode(OutgoingPacketOpcode.OBJECT_OPTION_5);
            outgoing.writeShortLE(cmd1 >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK);
            outgoing.writeShortLE(cmd3 + regionManager.baseY);
            outgoing.writeShort(cmd2 + regionManager.baseX);
        }
        if (actionId == MenuState.OBJECT_OPTION_1) {
            walkToGameObject(cmd3, cmd2, cmd1);
            outgoing.writeOpcode(OutgoingPacketOpcode.OBJECT_OPTION_1);
            outgoing.writeShortAdd(cmd2 + regionManager.baseX);
            outgoing.writeShortLE(cmd3 + regionManager.baseY);
            outgoing.writeShortLE(cmd1 >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK);
        }
        if (actionId == MenuState.OBJECT_OPTION_3) {
            walkToGameObject(cmd3, cmd2, cmd1);
            outgoing.writeOpcode(OutgoingPacketOpcode.OBJECT_OPTION_3);
            outgoing.writeShortAdd(cmd3 + regionManager.baseY);
            outgoing.writeShortLE(cmd1 >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK);
            outgoing.writeShortLEAdd(cmd2 + regionManager.baseX);
        }
        if (actionId == MenuState.EXAMINE_OBJECT) {
            int objectId = cmd1 >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK;
            GameObjectDefinition objectDefinition = GameObjectDefinition.lookup(objectId);
            String description;
            if (objectDefinition.description != null) {
                description = new String(objectDefinition.description);
            } else {
                description = "It's a " + objectDefinition.name + ".";
            }
            messages.addChatMessage("", description, ChatMessageType.GAME);
        }
        if (actionId == MenuState.OBJECT_OPTION_4) {
            walkToGameObject(cmd3, cmd2, cmd1);
            outgoing.writeOpcode(OutgoingPacketOpcode.OBJECT_OPTION_4);
            outgoing.writeShort(cmd2 + regionManager.baseX);
            outgoing.writeShortLE(cmd3 + regionManager.baseY);
            outgoing.writeShort(cmd1 >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK);
        }
        if (actionId == MenuState.OBJECT_OPTION_2) {
            walkToGameObject(cmd3, cmd2, cmd1);
            outgoing.writeOpcode(OutgoingPacketOpcode.OBJECT_OPTION_2);
            outgoing.writeShort(cmd1 >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK);
            outgoing.writeShort(cmd2 + regionManager.baseX);
            outgoing.writeShortAdd(cmd3 + regionManager.baseY);
        }
        return false;
    }

    /**
     * Resolves object footprint/configuration and routes into interaction range.
     *
     * @param tileY target local tile Y
     * @param tileX target local tile X
     * @param uid packed scene object UID
     * @return whether the scene object still has a valid configuration
     */
    private boolean walkToGameObject(int tileY, int tileX, int uid) {
        int objectId = uid >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK;
        int config = worldState.get().scene.getConfig(currentPlane.getAsInt(), tileX, tileY, uid);
        if (config == -1) {
            return false;
        }
        int type = SceneConfig.type(config);
        int orientation = SceneConfig.orientation(config);
        if (type == 10 || type == 11 || type == 22) {
            GameObjectDefinition definition = GameObjectDefinition.lookup(objectId);
            int width;
            int height;
            if (orientation == 0 || orientation == 2) {
                width = definition.sizeX;
                height = definition.sizeY;
            } else {
                width = definition.sizeY;
                height = definition.sizeX;
            }
            int accessMask = definition.surroundings;
            if (orientation != 0) {
                accessMask = (accessMask << orientation & 0xf) + (accessMask >> 4 - orientation);
            }
            movement.walkTo(true, tileX, tileY, width, height, MovementPacketEncoder.INTERACTION, 0, 0, accessMask);
        } else {
            movement.walkTo(true, tileX, tileY, 0, 0, MovementPacketEncoder.INTERACTION, type + 1, orientation, 0);
        }
        markInteractionCrosshair.run();
        return true;
    }
}
