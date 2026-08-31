package rs2.action;

import rs2.chat.ChatController;
import rs2.game.render.GameRenderer;
import rs2.ui.InterfaceController;
import rs2.ui.menu.MenuController;
import rs2.ui.menu.MenuEntry;
import rs2.ui.menu.MenuState;

/**
 * Package-level menu-action dispatcher for the application coordinator.
 *
 * <p>The dispatcher owns action normalization, dialog cancellation, explicit
 * domain routing, and the shared post-action item/spell selection cleanup. The
 * effect handlers remain callbacks during Phase 7.2 so subsequent milestones
 * can move those bodies by domain without changing this routing contract.</p>
 */
public final class ClientActionDispatcher {

    /** Handles one normalized action and reports whether shared cleanup must be skipped. */
    @FunctionalInterface
    public interface ActionHandler {
        /**
         * Applies one normalized action.
         *
         * @param actionId normalized revision-377 action ID
         * @param argument0 first menu argument
         * @param argument1 second menu argument
         * @param argument2 third menu argument
         * @param menuIndex source menu index
         * @return {@code true} when the action intentionally preserves selection state
         */
        boolean dispatch(int actionId, int argument0, int argument1, int argument2, int menuIndex);
    }

    /** Menu state supplying the selected entry. */
    private final MenuController menuController;
    /** Chat/input owner whose open input dialog may be cancelled by an action. */
    private final ChatController chatController;
    /** Interface owner containing item and spell selection state. */
    private final InterfaceController interfaceController;
    /** Renderer invalidation owner. */
    private final GameRenderer gameRenderer;
    /** Player-domain action effects. */
    private final ActionHandler playerHandler;
    /** NPC-domain action effects. */
    private final ActionHandler npcHandler;
    /** Object-domain action effects. */
    private final ActionHandler objectHandler;
    /** Ground-item-domain action effects. */
    private final ActionHandler groundItemHandler;
    /** Inventory-domain action effects. */
    private final ActionHandler inventoryHandler;
    /** Widget-domain action effects. */
    private final ActionHandler widgetHandler;
    /** Social-domain action effects. */
    private final ActionHandler socialHandler;
    /** Walk-domain action effects. */
    private final ActionHandler walkHandler;

    /**
     * Creates the application action dispatcher.
     *
     * @param menuController menu-state owner
     * @param chatController chat/input owner
     * @param interfaceController interface/selection owner
     * @param gameRenderer renderer invalidation owner
     * @param playerHandler player action effects
     * @param npcHandler NPC action effects
     * @param objectHandler object action effects
     * @param groundItemHandler ground-item action effects
     * @param inventoryHandler inventory/item action effects
     * @param widgetHandler widget action effects
     * @param socialHandler social action effects
     * @param walkHandler walk action effects
     */
    public ClientActionDispatcher(MenuController menuController, ChatController chatController,
            InterfaceController interfaceController, GameRenderer gameRenderer, ActionHandler playerHandler,
            ActionHandler npcHandler, ActionHandler objectHandler, ActionHandler groundItemHandler,
            ActionHandler inventoryHandler, ActionHandler widgetHandler, ActionHandler socialHandler,
            ActionHandler walkHandler) {
        this.menuController = menuController;
        this.chatController = chatController;
        this.interfaceController = interfaceController;
        this.gameRenderer = gameRenderer;
        this.playerHandler = playerHandler;
        this.npcHandler = npcHandler;
        this.objectHandler = objectHandler;
        this.groundItemHandler = groundItemHandler;
        this.inventoryHandler = inventoryHandler;
        this.widgetHandler = widgetHandler;
        this.socialHandler = socialHandler;
        this.walkHandler = walkHandler;
    }

    /**
     * Normalizes and dispatches one menu action while preserving revision-377
     * selection-cleanup semantics.
     *
     * @param menuIndex source menu index
     */
    public void dispatch(int menuIndex) {
        if (menuIndex < 0) {
            return;
        }

        MenuEntry entry = menuController.state().entry(menuIndex);
        int actionId = MenuState.normalizeActionId(entry.action());
        if (chatController.inputDialogState() != 0 && actionId != MenuState.CANCEL_ACTION) {
            chatController.setInputDialogState(0);
            gameRenderer.requestChatboxRedraw();
        }

        boolean preserveSelection = switch (MenuState.actionDomain(actionId)) {
        case PLAYER -> dispatchWithoutPreservingSelection(playerHandler, actionId, entry, menuIndex);
        case NPC -> dispatchWithoutPreservingSelection(npcHandler, actionId, entry, menuIndex);
        case OBJECT -> dispatchWithoutPreservingSelection(objectHandler, actionId, entry, menuIndex);
        case GROUND_ITEM -> dispatchWithoutPreservingSelection(groundItemHandler, actionId, entry, menuIndex);
        case INVENTORY -> inventoryHandler.dispatch(actionId, entry.argument0(), entry.argument1(), entry.argument2(),
                menuIndex);
        case WIDGET -> widgetHandler.dispatch(actionId, entry.argument0(), entry.argument1(), entry.argument2(),
                menuIndex);
        case SOCIAL -> dispatchWithoutPreservingSelection(socialHandler, actionId, entry, menuIndex);
        case WALK -> dispatchWithoutPreservingSelection(walkHandler, actionId, entry, menuIndex);
        case CANCEL, UNKNOWN -> false;
        };

        if (preserveSelection) {
            return;
        }
        interfaceController.state().itemSelected = 0;
        interfaceController.state().spellSelected = 0;
        gameRenderer.requestSidebarRedraw();
    }

    /**
     * Invokes a domain whose Phase 7.2 effect handler never preserves selection.
     *
     * @param handler domain callback
     * @param actionId normalized action ID
     * @param entry menu entry
     * @param menuIndex source menu index
     * @return always {@code false}
     */
    private static boolean dispatchWithoutPreservingSelection(ActionHandler handler, int actionId, MenuEntry entry, int menuIndex) {
        handler.dispatch(actionId, entry.argument0(), entry.argument1(), entry.argument2(), menuIndex);
        return false;
    }
}
