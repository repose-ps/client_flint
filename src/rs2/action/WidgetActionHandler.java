package rs2.action;

import java.util.function.IntConsumer;

import rs2.chat.ChatController;
import rs2.chat.SocialManager;
import rs2.game.VarpState;
import rs2.game.render.GameRenderer;
import rs2.ui.AppearanceEditor;
import rs2.ui.InterfaceController;
import rs2.ui.Widget;
import rs2.ui.WidgetContentType;
import rs2.ui.menu.MenuEntry;
import rs2.ui.menu.MenuState;

/**
 * Applies revision-377 menu actions targeting widgets and widget config state.
 */
public final class WidgetActionHandler implements ClientActionDispatcher.ActionHandler {
	/** Revision-377 action packet encoder. */
	private final ActionPacketEncoder packets;
	/** Interface state/action owner. */
	private final InterfaceController interfaces;
	/** Client varp state. */
	private final VarpState varpState;
	/** Renderer invalidation owner. */
	private final GameRenderer gameRenderer;
	/** Applies varp side effects owned by the application coordinator. */
	private final IntConsumer applyVarp;
	/** Social-list owner used by content-type widget actions. */
	private final SocialManager socialManager;
	/** Chat/prompt owner used by content-type widget actions. */
	private final ChatController chatController;
	/** Appearance-editing owner used by content-type widget actions. */
	private final AppearanceEditor appearanceEditor;
	/** Writes the appearance-update packet owned by the appearance editor. */
	private final Runnable submitAppearance;
	/** Shared interface-close operation. */
	private final Runnable closeInterfaces;
	/** Updates the client logout countdown. */
	private final IntConsumer setLogoutTimer;

	/**
	 * Creates the widget action handler.
	 *
	 * @param packets          revision-377 action packet encoder
	 * @param interfaces       interface state/action owner
	 * @param varpState        varp state
	 * @param gameRenderer     renderer invalidation owner
	 * @param applyVarp        application varp-effects callback
	 * @param socialManager    social-list owner
	 * @param chatController   chat/prompt owner
	 * @param appearanceEditor appearance-editing owner
	 * @param submitAppearance appearance-update submission
	 * @param closeInterfaces  shared interface-close operation
	 * @param setLogoutTimer   logout-countdown setter
	 */
	public WidgetActionHandler(ActionPacketEncoder packets, InterfaceController interfaces, VarpState varpState,
			GameRenderer gameRenderer, IntConsumer applyVarp, SocialManager socialManager,
			ChatController chatController, AppearanceEditor appearanceEditor, Runnable submitAppearance,
			Runnable closeInterfaces, IntConsumer setLogoutTimer) {
		this.packets = packets;
		this.interfaces = interfaces;
		this.varpState = varpState;
		this.gameRenderer = gameRenderer;
		this.applyVarp = applyVarp;
		this.socialManager = socialManager;
		this.chatController = chatController;
		this.appearanceEditor = appearanceEditor;
		this.submitAppearance = submitAppearance;
		this.closeInterfaces = closeInterfaces;
		this.setLogoutTimer = setLogoutTimer;
	}

	/** {@inheritDoc} */
	@Override
	public boolean dispatch(int actionId, MenuEntry entry) {
		int argument0 = entry.argument0();
		int argument1 = entry.argument1();
		int argument2 = entry.argument2();
		if (actionId == MenuState.WIDGET_TOGGLE_VARP) {
			packets.widgetClick(argument2);
			Widget widget = Widget.get(argument2);
			if (widget.cs1Instructions != null && widget.cs1Instructions[0][0] == 5) {
				int varpId = widget.cs1Instructions[0][1];
				varpState.toggleBinary(varpId);
				applyVarp.accept(varpId);
				gameRenderer.requestSidebarRedraw();
			}
		}
		if (actionId == MenuState.CLOSE_INTERFACE) {
			closeInterfaces.run();
		}
		if (actionId == MenuState.SELECT_SPELL) {
			Widget spellWidget = Widget.get(argument2);
			interfaces.state().spellSelected = 1;
			interfaces.state().selectedSpellWidgetId = argument2;
			interfaces.state().selectedSpellTargetMask = spellWidget.spellUsableOn;
			interfaces.state().itemSelected = 0;
			gameRenderer.requestSidebarRedraw();
			String actionVerb = spellWidget.selectedActionName;
			if (actionVerb.indexOf(" ") != -1) {
				actionVerb = actionVerb.substring(0, actionVerb.indexOf(" "));
			}
			String actionTarget = spellWidget.selectedActionName;
			if (actionTarget.indexOf(" ") != -1) {
				actionTarget = actionTarget.substring(actionTarget.indexOf(" ") + 1);
			}
			interfaces.state().selectedSpellAction = actionVerb + " " + spellWidget.spellName + " " + actionTarget;
			if (interfaces.state().selectedSpellTargetMask == 16) {
				gameRenderer.requestSidebarRedraw();
				gameRenderer.requestTabAreaRedraw();
			}
			return true;
		}
		if (actionId == MenuState.WIDGET_BUTTON) {
			Widget actionWidget = Widget.get(argument2);
			boolean sendWidgetClick = true;
			if (actionWidget.contentType > WidgetContentType.NONE) {
				sendWidgetClick = handleWidgetContentAction(actionWidget);
			}
			if (sendWidgetClick) {
				packets.widgetClick(argument2);
			}
		}
		if (actionId == MenuState.WIDGET_CONTINUE && !interfaces.actionPending()) {
			packets.widgetContinue(argument2);
			interfaces.setActionPending(true);
		}
		if (actionId == MenuState.WIDGET_SET_VARP) {
			packets.widgetClick(argument2);
			Widget configWidget = Widget.get(argument2);
			if (configWidget.cs1Instructions != null && configWidget.cs1Instructions[0][0] == 5) {
				int varpId2 = configWidget.cs1Instructions[0][1];
				if (varpState.get(varpId2) != configWidget.cs1ComparisonValues[0]) {
					varpState.set(varpId2, configWidget.cs1ComparisonValues[0]);
					applyVarp.accept(varpId2);
					gameRenderer.requestSidebarRedraw();
				}
			}
		}
		if (actionId == MenuState.CLOSE_DIALOGUE) {
			interfaces.unload(interfaces.state().dialogueInterfaceId);
			gameRenderer.requestChatboxRedraw();
		}
		return false;
	}

	/**
	 * Applies a content-type widget action and reports whether generic click should
	 * be sent.
	 *
	 * @param widget activated widget
	 * @return whether the generic widget-click packet should be sent
	 */
	private boolean handleWidgetContentAction(Widget widget) {
		return interfaces.handleContentAction(widget, socialManager, chatController, appearanceEditor, submitAppearance,
				packets::reportAbuse, closeInterfaces, gameRenderer::requestChatboxRedraw, setLogoutTimer);
	}
}
