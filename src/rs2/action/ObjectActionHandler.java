package rs2.action;

import java.util.function.IntSupplier;
import java.util.function.Supplier;

import rs2.cache.def.GameObjectDefinition;
import rs2.chat.ChatMessageType;
import rs2.chat.SocialManager;
import rs2.game.WorldState;
import rs2.net.MovementPacketEncoder;
import rs2.scene.SceneConfig;
import rs2.scene.SceneUid;
import rs2.ui.InterfaceController;
import rs2.ui.menu.MenuEntry;
import rs2.ui.menu.MenuState;
import rs2.game.RegionManager;

/** Applies revision-377 menu actions targeting scene objects. */
public final class ObjectActionHandler implements ClientActionDispatcher.ActionHandler {
    /** Revision-377 action packet encoder. */
    private final ActionPacketEncoder packets;
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
     * @param packets revision-377 action packet encoder
     * @param interfaces interface state owner
     * @param regionManager region/base-coordinate owner
     * @param worldState active world supplier
     * @param currentPlane current-plane supplier
     * @param movement interaction movement capability
     * @param markInteractionCrosshair interaction-crosshair callback
     * @param messages chat-message sink
     */
    public ObjectActionHandler(ActionPacketEncoder packets, InterfaceController interfaces, RegionManager regionManager,
            Supplier<WorldState> worldState, IntSupplier currentPlane, ClientActionDispatcher.Movement movement,
            Runnable markInteractionCrosshair, SocialManager.MessageSink messages) {
        this.packets = packets;
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
    public boolean dispatch(int actionId, MenuEntry entry) {
        int argument0 = entry.argument0();
        int argument1 = entry.argument1();
        int argument2 = entry.argument2();
        if (actionId == MenuState.USE_ITEM_ON_OBJECT && walkToGameObject(argument2, argument1, argument0)) {
            packets.useItemOnObject(objectId(argument0), interfaces.state().selectedItemWidgetId,
                    interfaces.state().selectedItemId, argument2 + regionManager.baseY,
                    interfaces.state().selectedItemSlot, argument1 + regionManager.baseX);
        }
        if (actionId == MenuState.CAST_SPELL_ON_OBJECT && walkToGameObject(argument2, argument1, argument0)) {
            packets.castSpellOnObject(interfaces.state().selectedSpellWidgetId, objectId(argument0),
                    argument1 + regionManager.baseX, argument2 + regionManager.baseY);
        }
        if (actionId == MenuState.OBJECT_OPTION_5) {
            walkToGameObject(argument2, argument1, argument0);
            packets.objectOption5(objectId(argument0), argument1 + regionManager.baseX, argument2 + regionManager.baseY);
        }
        if (actionId == MenuState.OBJECT_OPTION_1) {
            walkToGameObject(argument2, argument1, argument0);
            packets.objectOption1(objectId(argument0), argument1 + regionManager.baseX, argument2 + regionManager.baseY);
        }
        if (actionId == MenuState.OBJECT_OPTION_3) {
            walkToGameObject(argument2, argument1, argument0);
            packets.objectOption3(objectId(argument0), argument1 + regionManager.baseX, argument2 + regionManager.baseY);
        }
        if (actionId == MenuState.EXAMINE_OBJECT) {
            int objectId = argument0 >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK;
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
            walkToGameObject(argument2, argument1, argument0);
            packets.objectOption4(objectId(argument0), argument1 + regionManager.baseX, argument2 + regionManager.baseY);
        }
        if (actionId == MenuState.OBJECT_OPTION_2) {
            walkToGameObject(argument2, argument1, argument0);
            packets.objectOption2(objectId(argument0), argument1 + regionManager.baseX, argument2 + regionManager.baseY);
        }
        return false;
    }

    /** Returns the object definition ID encoded in a scene UID.
     * @param uid packed scene UID
     * @return object definition ID
     */
    private static int objectId(int uid) {
        return uid >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK;
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
