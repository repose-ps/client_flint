package rs2.ui;

import java.util.function.IntConsumer;

import rs2.ClientLayout;
import rs2.chat.ChatController;
import rs2.chat.SocialManager;
import rs2.net.Buffer;
import rs2.net.OutgoingPacketOpcode;
import rs2.text.Base37;

/**
 * Owns revision-377 interface lifecycle and transient UI interaction state.
 *
 * <p>The controller deliberately does not render widgets or interpret menu
 * actions. It centralizes which interface groups are open, hover/tooltip
 * tracking, scrollbar interaction, report-abuse form state, and the short-lived
 * "please wait" interface action flag.</p>
 */
public final class InterfaceController {

    /** Receives redraw requests caused by opening/closing interface groups. */
    public interface RedrawSink {
        /** Requests the sidebar to be redrawn. */
        void redrawSidebar();

        /** Requests the tab strip to be redrawn. */
        void redrawTabs();

        /** Requests the chatbox to be redrawn. */
        void redrawChatbox();

        /** Requests the game screen to be redrawn. */
        void redrawGameScreen();
    }

    /** Mutable interface selection, tab, item/spell and inventory interaction state. */
    private final InterfaceState state = new InterfaceState();

    /** Whether a one-shot interface action is awaiting a server response. */
    private boolean actionPending;
    /** Widget hovered during the current menu-build traversal. */
    private int currentHoveredWidgetId;
    /** Widget currently hovered in the chatbox region. */
    private int chatboxHoveredWidgetId;
    /** Widget currently hovered in the sidebar region. */
    private int sidebarHoveredWidgetId;
    /** Widget currently hovered in the viewport region. */
    private int viewportHoveredWidgetId;
    /** Tooltip candidate found during the current menu-build traversal. */
    private int currentTooltipWidgetId;
    /** Tooltip currently active in the chatbox region. */
    private int chatboxTooltipWidgetId;
    /** Tooltip currently active in the sidebar region. */
    private int sidebarTooltipWidgetId;
    /** Tooltip currently active in the viewport region. */
    private int viewportTooltipWidgetId;
    /** Number of client cycles spent hovering the current tooltip candidate. */
    private int tooltipHoverTicks;
    /** Whether the mouse is currently dragging a scrollbar thumb. */
    private boolean scrollbarDragging;
    /** Temporary horizontal hit padding retained while dragging a scrollbar. */
    private int scrollbarDragPadding;
    /** Name currently entered in the report-abuse form. */
    private String reportAbuseName = "";
    /** Whether the moderator-only 48-hour mute option is selected. */
    private boolean reportAbuseMutePlayer;

    /** Creates an interface controller with no interface groups open. */
    public InterfaceController() {
    }

    /**
     * Returns the mutable revision-377 interface selection/inventory state.
     *
     * @return interface state owned by this controller
     */
    public InterfaceState state() {
        return state;
    }

    /**
     * Releases model resources held by one interface group.
     *
     * @param interfaceId interface group identifier
     */
    public void unload(int interfaceId) {
        Widget.unloadGroup(interfaceId);
    }

    /**
     * Closes all currently open interface groups and writes the matching client
     * packet.
     *
     * @param outgoing outgoing revision-377 packet buffer
     * @param redraw redraw callbacks for affected fixed UI regions
     */
    public void closeAll(Buffer outgoing, RedrawSink redraw) {
        outgoing.writeOpcode(OutgoingPacketOpcode.CLOSE_INTERFACES);
        if (state.sidebarOverlayInterfaceId != -1) {
            unload(state.sidebarOverlayInterfaceId);
            redraw.redrawSidebar();
            actionPending = false;
            redraw.redrawTabs();
        }
        if (state.chatboxInterfaceId != -1) {
            unload(state.chatboxInterfaceId);
            redraw.redrawChatbox();
            actionPending = false;
        }
        if (state.fullscreenInterfaceId != -1) {
            unload(state.fullscreenInterfaceId);
            redraw.redrawGameScreen();
        }
        if (state.fullscreenOverlayInterfaceId != -1) {
            unload(state.fullscreenOverlayInterfaceId);
        }
        if (state.openInterfaceId != -1) {
            unload(state.openInterfaceId);
        }
    }

    /**
     * Returns whether a one-shot widget action is awaiting server response.
     *
     * @return whether an interface action is pending
     */
    public boolean actionPending() {
        return actionPending;
    }

    /**
     * Changes the one-shot widget action pending state.
     *
     * @param pending new pending state
     */
    public void setActionPending(boolean pending) {
        actionPending = pending;
    }

    /** Returns the widget hovered during the current menu-build pass.
     * @return hovered widget id
     */
    public int currentHoveredWidgetId() {
        return currentHoveredWidgetId;
    }

    /** Sets the widget hovered during the current menu-build pass.
     * @param widgetId hovered widget id
     */
    public void setCurrentHoveredWidgetId(int widgetId) {
        currentHoveredWidgetId = widgetId;
    }

    /** Returns the currently hovered chatbox widget id.
     * @return chatbox hovered widget id
     */
    public int chatboxHoveredWidgetId() {
        return chatboxHoveredWidgetId;
    }

    /** Sets the currently hovered chatbox widget id.
     * @param widgetId widget id
     */
    public void setChatboxHoveredWidgetId(int widgetId) {
        chatboxHoveredWidgetId = widgetId;
    }

    /** Returns the currently hovered sidebar widget id.
     * @return sidebar hovered widget id
     */
    public int sidebarHoveredWidgetId() {
        return sidebarHoveredWidgetId;
    }

    /** Sets the currently hovered sidebar widget id.
     * @param widgetId widget id
     */
    public void setSidebarHoveredWidgetId(int widgetId) {
        sidebarHoveredWidgetId = widgetId;
    }

    /** Returns the currently hovered viewport widget id.
     * @return viewport hovered widget id
     */
    public int viewportHoveredWidgetId() {
        return viewportHoveredWidgetId;
    }

    /** Sets the currently hovered viewport widget id.
     * @param widgetId widget id
     */
    public void setViewportHoveredWidgetId(int widgetId) {
        viewportHoveredWidgetId = widgetId;
    }

    /** Returns the tooltip widget found during the current menu-build pass.
     * @return tooltip widget id
     */
    public int currentTooltipWidgetId() {
        return currentTooltipWidgetId;
    }

    /** Sets the tooltip widget found during the current menu-build pass.
     * @param widgetId tooltip widget id
     */
    public void setCurrentTooltipWidgetId(int widgetId) {
        currentTooltipWidgetId = widgetId;
    }

    /** Returns the active chatbox tooltip widget id.
     * @return chatbox tooltip widget id
     */
    public int chatboxTooltipWidgetId() {
        return chatboxTooltipWidgetId;
    }

    /** Sets the active chatbox tooltip widget id.
     * @param widgetId tooltip widget id
     */
    public void setChatboxTooltipWidgetId(int widgetId) {
        chatboxTooltipWidgetId = widgetId;
    }

    /** Returns the active sidebar tooltip widget id.
     * @return sidebar tooltip widget id
     */
    public int sidebarTooltipWidgetId() {
        return sidebarTooltipWidgetId;
    }

    /** Sets the active sidebar tooltip widget id.
     * @param widgetId tooltip widget id
     */
    public void setSidebarTooltipWidgetId(int widgetId) {
        sidebarTooltipWidgetId = widgetId;
    }

    /** Returns the active viewport tooltip widget id.
     * @return viewport tooltip widget id
     */
    public int viewportTooltipWidgetId() {
        return viewportTooltipWidgetId;
    }

    /** Sets the active viewport tooltip widget id.
     * @param widgetId tooltip widget id
     */
    public void setViewportTooltipWidgetId(int widgetId) {
        viewportTooltipWidgetId = widgetId;
    }

    /** Returns the number of cycles spent hovering the current tooltip.
     * @return hover duration in client cycles
     */
    public int tooltipHoverTicks() {
        return tooltipHoverTicks;
    }

    /** Sets the current tooltip hover duration.
     * @param ticks hover duration in client cycles
     */
    public void setTooltipHoverTicks(int ticks) {
        tooltipHoverTicks = ticks;
    }

    /** Returns whether any fixed UI region currently has a tooltip candidate.
     * @return whether any tooltip candidate is active
     */
    public boolean hasTooltipCandidate() {
        return chatboxTooltipWidgetId != 0 || sidebarTooltipWidgetId != 0 || viewportTooltipWidgetId != 0;
    }

    /** Returns whether the supplied widget is hovered in any fixed UI region.
     * @param widgetId widget id
     * @return whether the widget is hovered
     */
    public boolean isHovered(int widgetId) {
        return chatboxHoveredWidgetId == widgetId || sidebarHoveredWidgetId == widgetId
                || viewportHoveredWidgetId == widgetId;
    }

    /** Returns whether the supplied tooltip is active in any fixed UI region.
     * @param widgetId tooltip widget id
     * @return whether the tooltip is active
     */
    public boolean isTooltipActive(int widgetId) {
        return chatboxTooltipWidgetId == widgetId || sidebarTooltipWidgetId == widgetId
                || viewportTooltipWidgetId == widgetId;
    }

    /** Returns whether a scrollbar thumb is currently being dragged.
     * @return scrollbar dragging state
     */
    public boolean scrollbarDragging() {
        return scrollbarDragging;
    }

    /** Changes scrollbar dragging state.
     * @param dragging new dragging state
     */
    public void setScrollbarDragging(boolean dragging) {
        scrollbarDragging = dragging;
    }

    /** Returns the temporary horizontal hit padding used during scrollbar drag.
     * @return horizontal hit padding in pixels
     */
    public int scrollbarDragPadding() {
        return scrollbarDragPadding;
    }

    /** Sets the temporary horizontal hit padding used during scrollbar drag.
     * @param padding horizontal hit padding in pixels
     */
    public void setScrollbarDragPadding(int padding) {
        scrollbarDragPadding = padding;
    }

    /** Returns the name currently entered in the report-abuse form.
     * @return report-abuse target name
     */
    public String reportAbuseName() {
        return reportAbuseName;
    }

    /** Sets the report-abuse target name.
     * @param name report-abuse target name
     */
    public void setReportAbuseName(String name) {
        reportAbuseName = name;
    }

    /** Returns whether the moderator report-abuse mute option is selected.
     * @return mute-option state
     */
    public boolean reportAbuseMutePlayer() {
        return reportAbuseMutePlayer;
    }

    /** Sets the moderator report-abuse mute option.
     * @param mutePlayer new mute-option state
     */
    public void setReportAbuseMutePlayer(boolean mutePlayer) {
        reportAbuseMutePlayer = mutePlayer;
    }

    /** Toggles the moderator report-abuse mute option. */
    public void toggleReportAbuseMutePlayer() {
        reportAbuseMutePlayer = !reportAbuseMutePlayer;
    }

    /** Resets transient interaction state used when entering a logged-in session. */
    public void resetInteractionState() {
        actionPending = false;
        scrollbarDragging = false;
        scrollbarDragPadding = 0;
        currentHoveredWidgetId = 0;
        chatboxHoveredWidgetId = 0;
        sidebarHoveredWidgetId = 0;
        viewportHoveredWidgetId = 0;
        currentTooltipWidgetId = 0;
        chatboxTooltipWidgetId = 0;
        sidebarTooltipWidgetId = 0;
        viewportTooltipWidgetId = 0;
        tooltipHoverTicks = 0;
    }
    /**
     * Handles content-type-specific widget buttons such as social prompts,
     * appearance editing, logout and report-abuse controls.
     *
     * @param widget activated widget
     * @param socialManager social-list owner
     * @param chatController chat/prompt owner
     * @param appearanceEditor character-design owner
     * @param outgoing outgoing packet buffer
     * @param closeInterfaces callback that closes open interfaces
     * @param redrawChatbox callback that marks the chatbox dirty
     * @param setLogoutTimer callback that updates the logout countdown
     * @return whether the generic widget-click packet should be sent
     */
    public boolean handleContentAction(Widget widget, SocialManager socialManager, ChatController chatController,
            AppearanceEditor appearanceEditor, Buffer outgoing, Runnable closeInterfaces, Runnable redrawChatbox,
                IntConsumer setLogoutTimer) {
            int contentType = widget.contentType;
            if (socialManager.friendListStatus == SocialManager.FRIEND_LIST_READY) {
                if (contentType == WidgetContentType.ADD_FRIEND) {
                    redrawChatbox.run();
                    chatController.openPrompt(1, "Enter name of friend to add to list");
                }
                if (contentType == WidgetContentType.REMOVE_FRIEND) {
                    redrawChatbox.run();
                    chatController.openPrompt(2, "Enter name of friend to delete from list");
                }
            }
            if (contentType == WidgetContentType.LOGOUT) {
                setLogoutTimer.accept(250);
                return true;
            }
            if (contentType == WidgetContentType.ADD_IGNORE) {
                redrawChatbox.run();
                chatController.openPrompt(4, "Enter name of player to add to list");
            }
            if (contentType == WidgetContentType.REMOVE_IGNORE) {
                redrawChatbox.run();
                chatController.openPrompt(5, "Enter name of player to delete from list");
            }
            if (contentType >= WidgetContentType.APPEARANCE_KIT_FIRST && contentType <= WidgetContentType.APPEARANCE_KIT_LAST) {
                int bodyPart = (contentType - WidgetContentType.APPEARANCE_KIT_FIRST) / 2;
                appearanceEditor.cycleKit(bodyPart, contentType & 1);
            }
            if (contentType >= WidgetContentType.APPEARANCE_COLOR_FIRST && contentType <= WidgetContentType.APPEARANCE_COLOR_LAST) {
                int colorSlot = (contentType - WidgetContentType.APPEARANCE_COLOR_FIRST) / 2;
                appearanceEditor.cycleColor(colorSlot, contentType & 1);
            }
            if (contentType == WidgetContentType.SELECT_MALE_APPEARANCE)
                appearanceEditor.selectMale();
            if (contentType == WidgetContentType.SELECT_FEMALE_APPEARANCE)
                appearanceEditor.selectFemale();
            if (contentType == WidgetContentType.ACCEPT_APPEARANCE) {
                appearanceEditor.writeUpdate(outgoing);
                return true;
            }
            if (contentType == WidgetContentType.REPORT_ABUSE_MUTE)
                toggleReportAbuseMutePlayer();
            if (contentType >= WidgetContentType.REPORT_ABUSE_RULE_FIRST && contentType <= WidgetContentType.REPORT_ABUSE_RULE_LAST) {
                closeInterfaces.run();
                if (reportAbuseName().length() > 0) {
                    outgoing.writeOpcode(OutgoingPacketOpcode.REPORT_ABUSE);
                    outgoing.writeLong(Base37.encode(reportAbuseName()));
                    outgoing.writeByte(contentType - WidgetContentType.REPORT_ABUSE_RULE_FIRST);
                    outgoing.writeByte(reportAbuseMutePlayer() ? 1 : 0);
                }
            }
            return false;
        }

    /**
     * Processes the classic scrollbar arrows, track and thumb drag interaction.
     *
     * @param scrollHeight full scrollable content height
     * @param y scrollbar Y coordinate
     * @param widget scrollable widget
     * @param mouseY current mouse Y coordinate
     * @param redrawArea fixed UI redraw area identifier
     * @param mouseX current mouse X coordinate
     * @param height visible widget height
     * @param x scrollbar X coordinate
     * @param mouseButtonHoldTicks number of cycles the mouse button has been held
     * @param redraw redraw sink for affected UI regions
     */
    public void handleScrollbarInput(int scrollHeight, int y, Widget widget, int mouseY, int redrawArea, int mouseX,
            int height, int x, int mouseButtonHoldTicks, RedrawSink redraw) {
        scrollbarDragPadding = scrollbarDragging ? ClientLayout.SCROLLBAR_DRAG_PADDING : 0;
        scrollbarDragging = false;
        if (mouseX >= x && mouseX < x + ClientLayout.SCROLLBAR_WIDTH
                && mouseY >= y && mouseY < y + ClientLayout.SCROLLBAR_ARROW_HEIGHT) {
            widget.scrollY -= mouseButtonHoldTicks * 4;
            requestScrollRedraw(redrawArea, redraw);
            return;
        }
        if (mouseX >= x && mouseX < x + ClientLayout.SCROLLBAR_WIDTH
                && mouseY >= (y + height) - ClientLayout.SCROLLBAR_ARROW_HEIGHT && mouseY < y + height) {
            widget.scrollY += mouseButtonHoldTicks * 4;
            requestScrollRedraw(redrawArea, redraw);
            return;
        }
        if (mouseX >= x - scrollbarDragPadding
                && mouseX < x + ClientLayout.SCROLLBAR_WIDTH + scrollbarDragPadding
                && mouseY >= y + ClientLayout.SCROLLBAR_ARROW_HEIGHT
                && mouseY < (y + height) - ClientLayout.SCROLLBAR_ARROW_HEIGHT && mouseButtonHoldTicks > 0) {
            int trackHeight = height - ClientLayout.SCROLLBAR_ARROW_HEIGHT * 2;
            int thumbHeight = (trackHeight * height) / scrollHeight;
            if (thumbHeight < ClientLayout.SCROLLBAR_MIN_THUMB_HEIGHT)
                thumbHeight = ClientLayout.SCROLLBAR_MIN_THUMB_HEIGHT;
            int dragOffset = mouseY - y - ClientLayout.SCROLLBAR_ARROW_HEIGHT - thumbHeight / 2;
            int dragRange = trackHeight - thumbHeight;
            widget.scrollY = ((scrollHeight - height) * dragOffset) / dragRange;
            requestScrollRedraw(redrawArea, redraw);
            scrollbarDragging = true;
        }
    }

    /**
     * Requests the fixed UI redraw associated with a scrollbar area.
     *
     * @param redrawArea fixed UI area identifier
     * @param redraw redraw sink
     */
    private static void requestScrollRedraw(int redrawArea, RedrawSink redraw) {
        if (redrawArea == 1)
            redraw.redrawSidebar();
        if (redrawArea == 2 || redrawArea == 3)
            redraw.redrawChatbox();
    }

}
