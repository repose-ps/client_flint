package rs2.action;

import rs2.chat.ChatController;
import rs2.game.render.GameRenderer;
import rs2.ui.InterfaceController;
import rs2.ui.menu.MenuController;
import rs2.ui.menu.MenuEntry;
import rs2.ui.menu.MenuState;

/**
 * Application-level menu-action dispatcher.
 *
 * <p>
 * The dispatcher owns action normalization, dialog cancellation, explicit
 * domain routing, and shared post-action item/spell selection cleanup. Cohesive
 * action-effect handlers are injected by the application composition root.
 * </p>
 */
public final class ClientActionDispatcher {

	/**
	 * Handles one normalized action and reports whether shared cleanup must be
	 * skipped.
	 */
	@FunctionalInterface
	public interface ActionHandler {
		/**
		 * Applies one normalized action.
		 *
		 * @param actionId normalized revision-377 action ID
		 * @param entry    cohesive menu entry and action arguments
		 * @return {@code true} when the action intentionally preserves selection state
		 */
		boolean dispatch(int actionId, MenuEntry entry);

		/** Resets action-local transient counters for a full-login state reset. */
		default void resetForLogin() {
		}
	}

	/** Finds and writes a revision-377 movement route for an action target. */
	@FunctionalInterface
	public interface Movement {
		/**
		 * Routes the local player to an interaction target.
		 *
		 * @param allowAlternative whether the original alternative-route fallback is
		 *                         allowed
		 * @param targetX          target local tile X
		 * @param targetY          target local tile Y
		 * @param targetWidth      target width in tiles
		 * @param targetHeight     target height in tiles
		 * @param movementType     movement packet variant
		 * @param interactionType  collision interaction type
		 * @param orientation      target orientation
		 * @param accessMask       rectangular-object access mask
		 * @return whether a route was found
		 */
		boolean walkTo(boolean allowAlternative, int targetX, int targetY, int targetWidth, int targetHeight,
				int movementType, int interactionType, int orientation, int accessMask);
	}

	/** Narrow social-list mutation capability used by social menu actions. */
	public interface SocialListActions {
		/**
		 * Adds a friend name.
		 *
		 * @param encodedName Base-37 encoded name
		 */
		void addFriend(long encodedName);

		/**
		 * Adds an ignored name.
		 *
		 * @param encodedName Base-37 encoded name
		 */
		void addIgnore(long encodedName);

		/**
		 * Removes a friend name.
		 *
		 * @param encodedName Base-37 encoded name
		 */
		void removeFriend(long encodedName);

		/**
		 * Removes an ignored name.
		 *
		 * @param encodedName Base-37 encoded name
		 */
		void removeIgnore(long encodedName);
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
	 * @param menuController      menu-state owner
	 * @param chatController      chat/input owner
	 * @param interfaceController interface/selection owner
	 * @param gameRenderer        renderer invalidation owner
	 * @param playerHandler       player action effects
	 * @param npcHandler          NPC action effects
	 * @param objectHandler       object action effects
	 * @param groundItemHandler   ground-item action effects
	 * @param inventoryHandler    inventory/item action effects
	 * @param widgetHandler       widget action effects
	 * @param socialHandler       social action effects
	 * @param walkHandler         walk action effects
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
		case PLAYER -> dispatchWithoutPreservingSelection(playerHandler, actionId, entry);
		case NPC -> dispatchWithoutPreservingSelection(npcHandler, actionId, entry);
		case OBJECT -> dispatchWithoutPreservingSelection(objectHandler, actionId, entry);
		case GROUND_ITEM -> dispatchWithoutPreservingSelection(groundItemHandler, actionId, entry);
		case INVENTORY -> inventoryHandler.dispatch(actionId, entry);
		case WIDGET -> widgetHandler.dispatch(actionId, entry);
		case SOCIAL -> dispatchWithoutPreservingSelection(socialHandler, actionId, entry);
		case WALK -> dispatchWithoutPreservingSelection(walkHandler, actionId, entry);
		case CANCEL, UNKNOWN -> false;
		};

		if (preserveSelection) {
			return;
		}
		interfaceController.state().itemSelected = 0;
		interfaceController.state().spellSelected = 0;
		gameRenderer.requestSidebarRedraw();
	}

	/** Resets the transient anti-cheat counters owned by action domains. */
	public void resetForLogin() {
		playerHandler.resetForLogin();
		npcHandler.resetForLogin();
		objectHandler.resetForLogin();
		groundItemHandler.resetForLogin();
		inventoryHandler.resetForLogin();
		widgetHandler.resetForLogin();
		socialHandler.resetForLogin();
		walkHandler.resetForLogin();
	}

	/**
	 * Invokes a domain whose effect handler never preserves selection.
	 *
	 * @param handler  domain callback
	 * @param actionId normalized action ID
	 * @param entry    menu entry
	 * @return always {@code false}
	 */
	private static boolean dispatchWithoutPreservingSelection(ActionHandler handler, int actionId, MenuEntry entry) {
		handler.dispatch(actionId, entry);
		return false;
	}
}
