package rs2.ui;

/**
 * Computes screen-space positions for the classic revision-377 interface in a
 * resizable host window.
 *
 * <p>
 * The original 765x503 layout remains the minimum size. In resizable mode the
 * software-rendered game viewport becomes a full-window underlay. The
 * resizable HUD is composed from three independent fixed-size boxes over that
 * underlay: the minimap anchors to the top-right, the tab/sidebar enclosure to
 * the bottom-right, and the chat enclosure to the bottom-left. The existing
 * minimap, sidebar and chat software surfaces keep their classic dimensions.
 * </p>
 */
public final class ClientLayout {

	/** Creates a new client layout with its default client state. */
	public ClientLayout() {
	}

	/** Constant value for fixed width. */
	public static final int FIXED_WIDTH = 765;
	/** Constant value for fixed height. */
	public static final int FIXED_HEIGHT = 503;
	/** Constant value for fixed viewport width. */
	public static final int FIXED_VIEWPORT_WIDTH = 512;
	/** Constant value for fixed viewport height. */
	public static final int FIXED_VIEWPORT_HEIGHT = 334;

	/** Constant value for minimap width. */
	public static final int MINIMAP_WIDTH = 172;
	/** Constant value for minimap height. */
	public static final int MINIMAP_HEIGHT = 156;
	/** Constant value for sidebar width. */
	public static final int SIDEBAR_WIDTH = 190;
	/** Constant value for sidebar height. */
	public static final int SIDEBAR_HEIGHT = 261;
	/** Constant value for top tabs width. */
	public static final int TOP_TABS_WIDTH = 249;
	/** Constant value for top tabs height. */
	public static final int TOP_TABS_HEIGHT = 45;
	/** Constant value for bottom tabs width. */
	public static final int BOTTOM_TABS_WIDTH = 269;
	/** Constant value for bottom tabs height. */
	public static final int BOTTOM_TABS_HEIGHT = 37;
	/** Constant value for chatbox width. */
	public static final int CHATBOX_WIDTH = 479;
	/** Constant value for chatbox height. */
	public static final int CHATBOX_HEIGHT = 96;
	/** Constant value for chat modes width. */
	public static final int CHAT_MODES_WIDTH = 496;
	/** Constant value for chat modes height. */
	public static final int CHAT_MODES_HEIGHT = 50;

	/** Width of the complete resizable minimap frame. */
	public static final int RESIZABLE_MINIMAP_FRAME_WIDTH = 249;
	/** Height of the complete resizable minimap frame. */
	public static final int RESIZABLE_MINIMAP_FRAME_HEIGHT = 169;
	/** Minimap content inset from the left edge of its resizable frame. */
	public static final int RESIZABLE_MINIMAP_CONTENT_X = 34;
	/** Minimap content inset from the top edge of its resizable frame. */
	public static final int RESIZABLE_MINIMAP_CONTENT_Y = 4;

	/** Width of the complete resizable tab/sidebar frame. */
	public static final int RESIZABLE_TABS_FRAME_WIDTH = 249;
	/** Height of the complete resizable tab/sidebar frame. */
	public static final int RESIZABLE_TABS_FRAME_HEIGHT = 337;
	/** Sidebar content inset from the left edge of its resizable frame. */
	public static final int RESIZABLE_TABS_CONTENT_X = 37;
	/** Sidebar content inset from the top edge of its resizable frame. */
	public static final int RESIZABLE_TABS_CONTENT_Y = 39;
	/** Width of each resizable top/bottom tab strip. */
	public static final int RESIZABLE_TAB_STRIP_WIDTH = 191;
	/** Height of the resizable top tab strip. */
	public static final int RESIZABLE_TOP_TAB_STRIP_HEIGHT = 39;
	/** Height of the resizable bottom tab strip. */
	public static final int RESIZABLE_BOTTOM_TAB_STRIP_HEIGHT = 37;

	/**
	 * Source X of {@code resizable_tabs_top} inside the original 249x45
	 * {@code tabstopborder} image.
	 */
	public static final int RESIZABLE_TOP_TABS_SOURCE_X = 37;
	/**
	 * Source Y of {@code resizable_tabs_top} inside the original 249x45
	 * {@code tabstopborder} image.
	 */
	public static final int RESIZABLE_TOP_TABS_SOURCE_Y = 6;
	/**
	 * Original bottom-tab X represented by the left edge of the resizable frame.
	 */
	public static final int RESIZABLE_BOTTOM_TABS_FRAME_SOURCE_X = 19;
	/**
	 * Source X of {@code resizable_tabs_bottom} inside the original 269x37
	 * {@code tabsbottomborder} image.
	 */
	public static final int RESIZABLE_BOTTOM_TABS_SOURCE_X = 56;
	/**
	 * Source X represented by the resizable right frame piece at the bottom.
	 * The custom pieces intentionally skip one source column at their seam.
	 */
	public static final int RESIZABLE_BOTTOM_TABS_RIGHT_SOURCE_X = 247;

	/** Width of the complete resizable chat frame. */
	public static final int RESIZABLE_CHAT_FRAME_WIDTH = 516;
	/** Height of the complete resizable chat frame. */
	public static final int RESIZABLE_CHAT_FRAME_HEIGHT = 165;
	/** Chatbox content inset from the left edge of its resizable frame. */
	public static final int RESIZABLE_CHAT_CONTENT_X = 17;
	/** Chatbox content inset from the top edge of its resizable frame. */
	public static final int RESIZABLE_CHAT_CONTENT_Y = 19;

	/** Width of the clipped chat-message text area inside the chatbox. */
	public static final int CHATBOX_MESSAGE_CLIP_WIDTH = 463;
	/** Height of the scrollable chat-message area inside the chatbox. */
	public static final int CHATBOX_MESSAGE_HEIGHT = 77;
	/** Width of the chatbox divider drawn above the input line. */
	public static final int CHATBOX_DIVIDER_WIDTH = 479;
	/** X coordinate of the chatbox scrollbar inside the chatbox buffer. */
	public static final int CHATBOX_SCROLLBAR_X = 463;
	/** Fixed client-space right edge used by chat-message menu hit testing. */
	public static final int CHATBOX_MESSAGE_MENU_RIGHT_X = 426;
	/** Fixed client-space left edge of the legacy chat scrollbar input gate. */
	public static final int CHATBOX_SCROLL_INPUT_LEFT_X = 448;
	/** Fixed client-space right edge of the legacy chat scrollbar input gate. */
	public static final int CHATBOX_SCROLL_INPUT_RIGHT_X = 560;
	/** Pixels above the chatbox accepted by the legacy scrollbar input gate. */
	public static final int CHATBOX_SCROLL_INPUT_TOP_MARGIN = 25;
	/** Horizontal center used by chatbox prompt and dialog text. */
	public static final int CHATBOX_TEXT_CENTER_X = 239;
	/** Baseline of the normal chat input line. */
	public static final int CHATBOX_INPUT_BASELINE_Y = 90;
	/** Baseline of the newest visible normal chat message. */
	public static final int CHATBOX_MESSAGE_BASELINE_Y = 70;
	/** Vertical spacing between normal chat-message lines. */
	public static final int CHATBOX_MESSAGE_LINE_HEIGHT = 14;
	/** Additional baseline offset used by chat-message menu hit testing. */
	public static final int CHATBOX_MESSAGE_MENU_BASELINE_OFFSET = 4;
	/**
	 * Padding added after visible chat lines when computing scroll content height.
	 */
	public static final int CHATBOX_CONTENT_HEIGHT_PADDING = 7;

	/** Width of the classic widget scrollbar. */
	public static final int SCROLLBAR_WIDTH = 16;
	/** Height of each classic scrollbar arrow cap. */
	public static final int SCROLLBAR_ARROW_HEIGHT = 16;
	/** Minimum height of the draggable classic scrollbar thumb. */
	public static final int SCROLLBAR_MIN_THUMB_HEIGHT = 8;
	/** Extra horizontal hit padding retained while dragging a scrollbar. */
	public static final int SCROLLBAR_DRAG_PADDING = 32;

	/** Vertical spacing between context-menu entries. */
	public static final int CONTEXT_MENU_ROW_HEIGHT = 15;
	/** Baseline offset of the first context-menu entry. */
	public static final int CONTEXT_MENU_FIRST_ENTRY_BASELINE = 31;
	/** Pixels above an entry baseline included in its hit area. */
	public static final int CONTEXT_MENU_ENTRY_HIT_TOP = 13;
	/** Pixels below an entry baseline included in its hit area. */
	public static final int CONTEXT_MENU_ENTRY_HIT_BOTTOM = 3;
	/** Horizontal padding added to the widest context-menu label. */
	public static final int CONTEXT_MENU_WIDTH_PADDING = 8;
	/** Extra height used while clamping an opening context menu. */
	public static final int CONTEXT_MENU_CLAMP_HEIGHT_PADDING = 21;
	/** Extra height stored on an opened context menu. */
	public static final int CONTEXT_MENU_HEIGHT_PADDING = 22;
	/** Mouse distance outside an open menu that closes it. */
	public static final int CONTEXT_MENU_CLOSE_PADDING = 10;

	/** Fixed-layout X origin of the world viewport. */
	public static final int VIEWPORT_X = 4;
	/** Fixed-layout Y origin of the world viewport. */
	public static final int VIEWPORT_Y = 4;
	/** Fixed-layout X origin of the minimap dock. */
	public static final int MINIMAP_X = 550;
	/** Fixed-layout Y origin of the minimap dock. */
	public static final int MINIMAP_Y = 4;
	/** Fixed-layout X origin of the top tab strip. */
	public static final int TOP_TABS_X = 516;
	/** Fixed-layout Y origin of the top tab strip. */
	public static final int TOP_TABS_Y = 160;
	/** Fixed-layout X origin of the sidebar content area. */
	public static final int SIDEBAR_X = 553;
	/** Fixed-layout Y origin of the sidebar content area. */
	public static final int SIDEBAR_Y = 205;
	/** Fixed-layout X origin of the bottom tab strip. */
	public static final int BOTTOM_TABS_X = 496;
	/** Fixed-layout Y origin of the bottom tab strip. */
	public static final int BOTTOM_TABS_Y = 466;
	/** Fixed-layout X origin of the chatbox. */
	public static final int CHATBOX_X = 17;
	/** Fixed-layout Y origin of the chatbox. */
	public static final int CHATBOX_Y = 357;
	/** Fixed-layout X origin of the chat-mode strip. */
	public static final int CHAT_MODES_X = 0;
	/** Fixed-layout Y origin of the chat-mode strip. */
	public static final int CHAT_MODES_Y = 453;
	/** Fixed-layout X coordinate of the middle frame border. */
	public static final int MIDDLE_BORDER_X = 516;
	/** Fixed-layout Y coordinate of the lower frame border. */
	public static final int LOWER_BORDER_Y = 338;
	/** Fixed Y coordinate of the lower vertical middle frame decoration. */
	public static final int LOWER_VERTICAL_MIDDLE_Y = 357;
	/** Fixed-layout X coordinate of the upper-right frame decoration. */
	public static final int RIGHT_FRAME_TOP_X = 722;
	/** Fixed-layout X coordinate of the middle-right frame decoration. */
	public static final int RIGHT_FRAME_MIDDLE_X = 743;

	/** Number of classic sidebar tabs. */
	public static final int TAB_COUNT = 14;

	/** Chat-mode button identifier for public chat. */
	public static final int CHAT_MODE_PUBLIC = 0;
	/** Chat-mode button identifier for private chat. */
	public static final int CHAT_MODE_PRIVATE = 1;
	/** Chat-mode button identifier for trade/compete requests. */
	public static final int CHAT_MODE_TRADE = 2;
	/** Chat-mode button identifier for Report Abuse. */
	public static final int CHAT_MODE_REPORT_ABUSE = 3;
	/** Value returned when no fixed chat-mode button contains the point. */
	public static final int NO_CHAT_MODE_BUTTON = -1;

	/**
	 * Classic sidebar-tab hit rectangles as panel-local {left, rightExclusive, top,
	 * bottomExclusive} bounds. Tabs 0..6 are local to the top strip; tabs 7..13 are
	 * local to the bottom strip.
	 */
	private static final int[][] TAB_HITBOXES = { { 23, 58, 9, 45 }, { 53, 84, 8, 45 }, { 81, 112, 8, 45 },
			{ 109, 154, 8, 43 }, { 150, 181, 8, 45 }, { 178, 209, 8, 45 }, { 206, 241, 9, 45 }, { 44, 79, 0, 36 },
			{ 76, 107, 0, 37 }, { 103, 134, 0, 37 }, { 131, 176, 1, 36 }, { 173, 204, 0, 37 }, { 200, 231, 0, 37 },
			{ 228, 263, 0, 36 } };

	/** Selected-tab highlight X coordinates inside the tab-strip buffers. */
	private static final int[] TAB_HIGHLIGHT_X = { 22, 54, 82, 110, 153, 181, 209, 42, 74, 102, 130, 173, 201, 229 };

	/** Selected-tab highlight Y coordinates inside the tab-strip buffers. */
	private static final int[] TAB_HIGHLIGHT_Y = { 10, 8, 8, 8, 8, 8, 9, 0, 0, 0, 1, 0, 0, 0 };

	/**
	 * Sidebar icon X coordinates indexed by tab; tab 7 intentionally has no icon.
	 */
	private static final int[] TAB_ICON_X = { 29, 53, 82, 115, 153, 180, 208, -1, 74, 102, 137, 174, 201, 226 };

	/**
	 * Sidebar icon Y coordinates indexed by tab; tab 7 intentionally has no icon.
	 */
	private static final int[] TAB_ICON_Y = { 13, 11, 11, 12, 13, 11, 13, -1, 2, 3, 4, 2, 2, 2 };

	/**
	 * Classic chat-mode hit rectangles as chat-mode-buffer-local {left,
	 * rightExclusive, top, bottomExclusive} bounds.
	 */
	private static final int[][] CHAT_MODE_HITBOXES = { { 6, 107, 14, 47 }, { 135, 236, 14, 47 }, { 273, 374, 14, 47 },
			{ 412, 513, 14, 47 } };

	/** Text center X coordinates for the four chat-mode buttons. */
	private static final int[] CHAT_MODE_TEXT_CENTER_X = { 55, 184, 324, 458 };
	/** Label baselines for the four chat-mode buttons. */
	private static final int[] CHAT_MODE_LABEL_Y = { 28, 28, 28, 33 };
	/** Status baselines for public/private/trade chat-mode buttons. */
	private static final int CHAT_MODE_STATUS_Y = 41;

	/** Stores the current width. */
	private int width = FIXED_WIDTH;
	/** Stores the current height. */
	private int height = FIXED_HEIGHT;

	/**
	 * Resizes the operation.
	 *
	 * @param width  the width in pixels
	 * @param height the height in pixels
	 */
	public void resize(int width, int height) {
		this.width = Math.max(FIXED_WIDTH, width);
		this.height = Math.max(FIXED_HEIGHT, height);
	}

	/**
	 * Returns the width.
	 *
	 * @return the width
	 */
	public int width() {
		return width;
	}

	/**
	 * Returns the height.
	 *
	 * @return the height
	 */
	public int height() {
		return height;
	}

	/**
	 * Returns the extra width.
	 *
	 * @return the extra width
	 */
	public int extraWidth() {
		return width - FIXED_WIDTH;
	}

	/**
	 * Returns the extra height.
	 *
	 * @return the extra height
	 */
	public int extraHeight() {
		return height - FIXED_HEIGHT;
	}

	/**
	 * True as soon as either client dimension differs from the fixed layout.
	 * 
	 * @return whether resizable mode
	 */
	public boolean isResizableMode() {
		return width != FIXED_WIDTH || height != FIXED_HEIGHT;
	}

	/**
	 * Returns the viewport X coordinate.
	 *
	 * @return the viewport X
	 */
	public int viewportX() {
		return VIEWPORT_X;
	}

	/**
	 * Returns the viewport Y coordinate.
	 *
	 * @return the viewport Y
	 */
	public int viewportY() {
		return VIEWPORT_Y;
	}

	/**
	 * The fixed client keeps its original 512x334 viewport exactly. Once either
	 * dimension is resized, both viewport dimensions become full-window underlay
	 * dimensions. This deliberately avoids reintroducing the old right/bottom
	 * gutters when only one window dimension is stretched.
	 * 
	 * @return the current world-viewport width in pixels
	 */
	public int viewportWidth() {
		return isResizableMode() ? width - viewportX() : FIXED_VIEWPORT_WIDTH;
	}

	/**
	 * Returns the viewport height.
	 *
	 * @return the viewport height
	 */
	public int viewportHeight() {
		return isResizableMode() ? height - viewportY() : FIXED_VIEWPORT_HEIGHT;
	}

	/**
	 * Width available to HUD text that should not be obscured by the classic
	 * right-hand dock.
	 * 
	 * @return the viewport width not covered by the right-hand HUD dock
	 */
	public int unobscuredViewportWidth() {
		return isResizableMode() ? tabsFrameX() - viewportX() : FIXED_VIEWPORT_WIDTH;
	}

	/**
	 * Height available to HUD text that should remain above the bottom chat HUD.
	 * 
	 * @return the viewport height above the bottom chat HUD
	 */
	public int unobscuredViewportHeight() {
		return isResizableMode() ? chatFrameY() - viewportY() : FIXED_VIEWPORT_HEIGHT;
	}

	/* Resizable minimap stays top-right; tabs/sidebar are independently bottom-right. */
	/**
	 * Returns the left edge of the minimap dock.
	 *
	 * @return the minimap X coordinate
	 */
	public int minimapX() {
		return isResizableMode() ? minimapFrameX() + RESIZABLE_MINIMAP_CONTENT_X : MINIMAP_X;
	}

	/**
	 * Returns the minimap Y coordinate.
	 *
	 * @return the minimap Y
	 */
	public int minimapY() {
		return isResizableMode() ? RESIZABLE_MINIMAP_CONTENT_Y : MINIMAP_Y;
	}

	/**
	 * Returns the left edge of the top tab strip.
	 *
	 * @return the top-tab X coordinate
	 */
	public int topTabsX() {
		return isResizableMode() ? sidebarX() : TOP_TABS_X;
	}

	/**
	 * Returns the top tabs Y coordinate.
	 *
	 * @return the converted value
	 */
	public int topTabsY() {
		return isResizableMode() ? tabsFrameY() : TOP_TABS_Y;
	}

	/**
	 * Returns the left edge of the sidebar content area.
	 *
	 * @return the sidebar X coordinate
	 */
	public int sidebarX() {
		return isResizableMode() ? tabsFrameX() + RESIZABLE_TABS_CONTENT_X : SIDEBAR_X;
	}

	/**
	 * Returns the sidebar Y coordinate.
	 *
	 * @return the sidebar Y
	 */
	public int sidebarY() {
		return isResizableMode() ? tabsFrameY() + RESIZABLE_TABS_CONTENT_Y : SIDEBAR_Y;
	}

	/**
	 * Returns the left edge of the bottom tab strip.
	 *
	 * @return the bottom-tab X coordinate
	 */
	public int bottomTabsX() {
		return isResizableMode() ? sidebarX() : BOTTOM_TABS_X;
	}

	/**
	 * Returns the bottom tabs Y coordinate.
	 *
	 * @return the bottom tabs Y
	 */
	public int bottomTabsY() {
		return isResizableMode() ? height - RESIZABLE_BOTTOM_TAB_STRIP_HEIGHT : BOTTOM_TABS_Y;
	}

	/* Chat content preserves its classic local geometry inside a bottom-left frame. */
	/**
	 * Returns the chatbox X coordinate.
	 *
	 * @return the chatbox X
	 */
	public int chatboxX() {
		return isResizableMode() ? chatFrameX() + RESIZABLE_CHAT_CONTENT_X : CHATBOX_X;
	}

	/**
	 * Returns the top edge of the bottom-anchored chatbox.
	 *
	 * @return the chatbox Y coordinate
	 */
	public int chatboxY() {
		return isResizableMode() ? chatFrameY() + RESIZABLE_CHAT_CONTENT_Y : CHATBOX_Y;
	}

	/**
	 * Returns the chat modes X coordinate.
	 *
	 * @return the chat modes X
	 */
	public int chatModesX() {
		return CHAT_MODES_X;
	}

	/**
	 * Returns the top edge of the bottom-anchored chat-mode strip.
	 *
	 * @return the chat-mode Y coordinate
	 */
	public int chatModesY() {
		return isResizableMode() ? height - CHAT_MODES_HEIGHT : CHAT_MODES_Y;
	}

	/** Returns the left edge of the resizable minimap frame. */
	public int minimapFrameX() {
		return width - RESIZABLE_MINIMAP_FRAME_WIDTH;
	}

	/** Returns the top edge of the resizable minimap frame. */
	public int minimapFrameY() {
		return 0;
	}

	/** Returns the left edge of the bottom-right resizable tab/sidebar frame. */
	public int tabsFrameX() {
		return width - RESIZABLE_TABS_FRAME_WIDTH;
	}

	/** Returns the top edge of the bottom-right resizable tab/sidebar frame. */
	public int tabsFrameY() {
		return height - RESIZABLE_TABS_FRAME_HEIGHT;
	}

	/** Returns the left edge of the bottom-left resizable chat frame. */
	public int chatFrameX() {
		return 0;
	}

	/** Returns the top edge of the bottom-left resizable chat frame. */
	public int chatFrameY() {
		return height - RESIZABLE_CHAT_FRAME_HEIGHT;
	}

	/** Screen X at which the resizable chat-mode background is drawn. */
	public int resizableChatModesX() {
		return chatboxX();
	}

	/**
	 * Converts a classic chat-mode text X coordinate into the custom bottom-strip
	 * raster, whose left edge starts at the chatbox inset.
	 */
	public int resizableChatModeTextCenterX(int button) {
		return chatModeTextCenterX(button) - RESIZABLE_CHAT_CONTENT_X;
	}

	/** Returns the local left edge of one of seven equally-spaced resizable tabs. */
	public int resizableTabCellLeft(int tab) {
		int localTab = requireTab(tab) % 7;
		return localTab * RESIZABLE_TAB_STRIP_WIDTH / 7;
	}

	/** Returns the local width of one resizable tab cell. */
	public int resizableTabCellWidth(int tab) {
		int localTab = requireTab(tab) % 7;
		int left = localTab * RESIZABLE_TAB_STRIP_WIDTH / 7;
		int right = (localTab + 1) * RESIZABLE_TAB_STRIP_WIDTH / 7;
		return right - left;
	}

	/** Returns the height of the top or bottom resizable strip containing a tab. */
	public int resizableTabStripHeight(int tab) {
		return requireTab(tab) < 7 ? RESIZABLE_TOP_TAB_STRIP_HEIGHT : RESIZABLE_BOTTOM_TAB_STRIP_HEIGHT;
	}

	/**
	 * Returns the X coordinate of the classic middle frame border.
	 *
	 * @return the middle-border X coordinate
	 */
	public int middleBorderX() {
		return MIDDLE_BORDER_X + extraWidth();
	}

	/**
	 * Returns the Y coordinate of the classic lower frame border.
	 *
	 * @return the lower-border Y coordinate
	 */
	public int lowerBorderY() {
		return LOWER_BORDER_Y + extraHeight();
	}

	/**
	 * Returns the fixed lower vertical-middle decoration Y coordinate.
	 * 
	 * @return the lower vertical-middle decoration Y coordinate
	 */
	public int lowerVerticalMiddleY() {
		return LOWER_VERTICAL_MIDDLE_Y;
	}

	/**
	 * Returns the upper-right frame-decoration X coordinate.
	 * 
	 * @return the upper-right frame-decoration X coordinate
	 */
	public int rightFrameTopX() {
		return rightAnchoredX(RIGHT_FRAME_TOP_X);
	}

	/**
	 * Returns the middle-right frame-decoration X coordinate.
	 * 
	 * @return the middle-right frame-decoration X coordinate
	 */
	public int rightFrameMiddleX() {
		return rightAnchoredX(RIGHT_FRAME_MIDDLE_X);
	}

	/**
	 * Converts a client-space X coordinate to minimap-buffer space.
	 * 
	 * @param x client-space X coordinate
	 * @return minimap-local X coordinate
	 */
	public int minimapLocalX(int x) {
		return x - minimapX();
	}

	/**
	 * Converts a client-space Y coordinate to minimap-buffer space.
	 * 
	 * @param y client-space Y coordinate
	 * @return minimap-local Y coordinate
	 */
	public int minimapLocalY(int y) {
		return y - minimapY();
	}

	/**
	 * Converts a client-space X coordinate to chatbox-buffer space.
	 * 
	 * @param x client-space X coordinate
	 * @return chatbox-local X coordinate
	 */
	public int chatboxLocalX(int x) {
		return x - chatboxX();
	}

	/**
	 * Converts a client-space Y coordinate to chatbox-buffer space.
	 * 
	 * @param y client-space Y coordinate
	 * @return chatbox-local Y coordinate
	 */
	public int chatboxLocalY(int y) {
		return y - chatboxY();
	}

	/**
	 * Returns the viewport-local X origin for a modal/root interface. Fixed mode
	 * preserves the original (0,0) placement; resizable mode centers the root in
	 * the game area left of the right-hand HUD dock.
	 * 
	 * @param interfaceWidth the interface width
	 * @return the viewport-local X coordinate for the centered interface
	 */
	public int centeredInterfaceX(int interfaceWidth) {
		if (!isResizableMode())
			return 0;
		return Math.max(0, (unobscuredViewportWidth() - interfaceWidth) / 2);
	}

	/**
	 * Returns the viewport-local Y origin for a modal/root interface. Resizable
	 * mode keeps the root above the bottom chat HUD instead of centering it behind
	 * that panel.
	 * 
	 * @param interfaceHeight the interface height
	 * @return the viewport-local Y coordinate for the centered interface
	 */
	public int centeredInterfaceY(int interfaceHeight) {
		if (!isResizableMode())
			return 0;
		return Math.max(0, (unobscuredViewportHeight() - interfaceHeight) / 2);
	}

	/**
	 * Tests the legacy inclusive top-tab strip bounds used while a spell is
	 * selected. The inclusive right/bottom edges intentionally preserve the
	 * original fixed-client behavior.
	 * 
	 * @param x client-space X coordinate
	 * @param y client-space Y coordinate
	 * @return whether the point lies in the spell-selection tab region
	 */
	public boolean isTopTabsSpellBlockPoint(int x, int y) {
		if (isResizableMode()) {
			return x >= tabsFrameX() && y >= tabsFrameY()
					&& x <= tabsFrameX() + RESIZABLE_TABS_FRAME_WIDTH
					&& y <= tabsFrameY() + RESIZABLE_TOP_TAB_STRIP_HEIGHT;
		}
		return x >= topTabsX() && y >= topTabsY() && x <= topTabsX() + TOP_TABS_WIDTH
				&& y <= topTabsY() + TOP_TABS_HEIGHT;
	}

	/**
	 * Tests the strict interior bounds used for sidebar menu interaction.
	 * 
	 * @param x client-space X coordinate
	 * @param y client-space Y coordinate
	 * @return whether the point lies in the sidebar interaction region
	 */
	public boolean isSidebarInteractionPoint(int x, int y) {
		return containsStrict(x, y, sidebarX(), sidebarY(), SIDEBAR_WIDTH, SIDEBAR_HEIGHT);
	}

	/**
	 * Tests the strict interior bounds used for chatbox menu interaction.
	 * 
	 * @param x client-space X coordinate
	 * @param y client-space Y coordinate
	 * @return whether the point lies in the chatbox interaction region
	 */
	public boolean isChatboxInteractionPoint(int x, int y) {
		return containsStrict(x, y, chatboxX(), chatboxY(), CHATBOX_WIDTH, CHATBOX_HEIGHT);
	}

	/**
	 * Tests the normal chat-message subregion used to build player-name menus.
	 * 
	 * @param x client-space X coordinate
	 * @param y client-space Y coordinate
	 * @return whether the point lies in the chat-message menu region
	 */
	public boolean isChatboxMessageMenuPoint(int x, int y) {
		return isChatboxInteractionPoint(x, y) && y < chatboxY() + CHATBOX_MESSAGE_HEIGHT
				&& x < chatboxX() + (CHATBOX_MESSAGE_MENU_RIGHT_X - CHATBOX_X);
	}

	/**
	 * Tests the original broad gate around chatbox scrollbar interaction.
	 * 
	 * @param x client-space X coordinate
	 * @param y client-space Y coordinate
	 * @return whether scrollbar processing should inspect the point
	 */
	public boolean isChatboxScrollbarInputCandidate(int x, int y) {
		int xOffset = chatboxX() - CHATBOX_X;
		return x > CHATBOX_SCROLL_INPUT_LEFT_X + xOffset && x < CHATBOX_SCROLL_INPUT_RIGHT_X + xOffset
				&& y > chatboxY() - CHATBOX_SCROLL_INPUT_TOP_MARGIN;
	}

	/**
	 * True when a point is in the rectangular world render target and is not
	 * covered by an interactive HUD region. The world still renders beneath all of
	 * these areas; the exclusion only gives UI input priority.
	 * 
	 * @param x the X coordinate
	 * @param y the Y coordinate
	 * @return whether viewport interaction point
	 */
	public boolean isViewportInteractionPoint(int x, int y) {
		if (!contains(x, y, viewportX(), viewportY(), viewportWidth(), viewportHeight()))
			return false;
		if (isResizableMode()) {
			return !contains(x, y, minimapFrameX(), minimapFrameY(), RESIZABLE_MINIMAP_FRAME_WIDTH,
					RESIZABLE_MINIMAP_FRAME_HEIGHT)
					&& !contains(x, y, tabsFrameX(), tabsFrameY(), RESIZABLE_TABS_FRAME_WIDTH,
							RESIZABLE_TABS_FRAME_HEIGHT)
					&& !contains(x, y, chatFrameX(), chatFrameY(), RESIZABLE_CHAT_FRAME_WIDTH,
							RESIZABLE_CHAT_FRAME_HEIGHT);
		}
		return !contains(x, y, minimapX(), minimapY(), MINIMAP_WIDTH, MINIMAP_HEIGHT)
				&& !contains(x, y, sidebarX(), sidebarY(), SIDEBAR_WIDTH, SIDEBAR_HEIGHT)
				&& !contains(x, y, topTabsX(), topTabsY(), TOP_TABS_WIDTH, TOP_TABS_HEIGHT)
				&& !contains(x, y, bottomTabsX(), bottomTabsY(), BOTTOM_TABS_WIDTH, BOTTOM_TABS_HEIGHT)
				&& !contains(x, y, chatboxX(), chatboxY(), CHATBOX_WIDTH, CHATBOX_HEIGHT)
				&& !contains(x, y, chatModesX(), chatModesY(), CHAT_MODES_WIDTH, CHAT_MODES_HEIGHT);
	}

	/**
	 * Returns whether the operation.
	 *
	 * @param x            the X coordinate
	 * @param y            the Y coordinate
	 * @param left         the left
	 * @param top          the top
	 * @param regionWidth  the region width
	 * @param regionHeight the region height
	 * @return whether contains
	 */
	private boolean contains(int x, int y, int left, int top, int regionWidth, int regionHeight) {
		return regionWidth > 0 && regionHeight > 0 && x >= left && y >= top && x < left + regionWidth
				&& y < top + regionHeight;
	}

	/**
	 * Tests strict interior rectangle bounds used by legacy menu regions.
	 * 
	 * @param x            point X coordinate
	 * @param y            point Y coordinate
	 * @param left         rectangle left edge
	 * @param top          rectangle top edge
	 * @param regionWidth  rectangle width
	 * @param regionHeight rectangle height
	 * @return whether the point lies strictly inside the rectangle
	 */
	private boolean containsStrict(int x, int y, int left, int top, int regionWidth, int regionHeight) {
		return regionWidth > 0 && regionHeight > 0 && x > left && y > top && x < left + regionWidth
				&& y < top + regionHeight;
	}

	/**
	 * Tests whether the supplied screen coordinate falls within one sidebar-tab
	 * hitbox. Fixed mode uses the classic rectangles; resizable mode divides each
	 * custom strip into seven cells.
	 *
	 * @param tab tab index 0..13
	 * @param x   screen X coordinate
	 * @param y   screen Y coordinate
	 * @return whether the point lies in that tab's current-layout hitbox
	 */
	public boolean isTabHit(int tab, int x, int y) {
		if (tab < 0 || tab >= TAB_HITBOXES.length)
			return false;

		int localX;
		int localY;
		if (!isResizableMode()) {
			localX = x - (tab < 7 ? topTabsX() : bottomTabsX());
			localY = y - (tab < 7 ? topTabsY() : bottomTabsY());
		} else if (tab < 7) {
			/*
			 * The complete top edge of the custom frame is the original 249px tab
			 * background with its first six rows removed. Map the mouse back into that
			 * original raster so the legacy hit rectangles still match the artwork.
			 */
			localX = x - tabsFrameX();
			localY = y - tabsFrameY() + RESIZABLE_TOP_TABS_SOURCE_Y;
		} else {
			/*
			 * The bottom frame corresponds to original bottom-tab columns 19..268.
			 * Its right piece skips one source column at the overlap seam.
			 */
			int frameX = x - tabsFrameX();
			localX = frameX + RESIZABLE_BOTTOM_TABS_FRAME_SOURCE_X;
			if (frameX >= RESIZABLE_TABS_CONTENT_X + RESIZABLE_TAB_STRIP_WIDTH)
				localX++;
			localY = y - bottomTabsY();
		}

		int[] bounds = TAB_HITBOXES[tab];
		return containsExclusive(localX, localY, bounds[0], bounds[2], bounds[1], bounds[3]);
	}

	/**
	 * Returns the selected-tab highlight X coordinate inside its strip buffer.
	 * 
	 * @param tab tab index 0..13
	 * @return highlight X coordinate
	 */
	public int tabHighlightX(int tab) {
		return TAB_HIGHLIGHT_X[requireTab(tab)];
	}

	/**
	 * Returns the selected-tab highlight Y coordinate inside its strip buffer.
	 * 
	 * @param tab tab index 0..13
	 * @return highlight Y coordinate
	 */
	public int tabHighlightY(int tab) {
		return TAB_HIGHLIGHT_Y[requireTab(tab)];
	}

	/**
	 * Returns the sidebar icon X coordinate for a tab, or -1 when none exists.
	 * 
	 * @param tab tab index 0..13
	 * @return icon X coordinate, or -1
	 */
	public int tabIconX(int tab) {
		return TAB_ICON_X[requireTab(tab)];
	}

	/**
	 * Returns the sidebar icon Y coordinate for a tab, or -1 when none exists.
	 * 
	 * @param tab tab index 0..13
	 * @return icon Y coordinate, or -1
	 */
	public int tabIconY(int tab) {
		return TAB_ICON_Y[requireTab(tab)];
	}

	/**
	 * Returns the fixed chat-mode button at the supplied screen coordinate.
	 *
	 * @param x screen X coordinate
	 * @param y screen Y coordinate
	 * @return one of the {@code CHAT_MODE_*} constants, or
	 *         {@link #NO_CHAT_MODE_BUTTON}
	 */
	public int chatModeButtonAt(int x, int y) {
		int localX = x - chatModesX();
		int localY = y - chatModesY();
		for (int button = 0; button < CHAT_MODE_HITBOXES.length; button++) {
			int[] bounds = CHAT_MODE_HITBOXES[button];
			if (containsExclusive(localX, localY, bounds[0], bounds[2], bounds[1], bounds[3]))
				return button;
		}
		return NO_CHAT_MODE_BUTTON;
	}

	/**
	 * Returns the local text center for one chat-mode button.
	 * 
	 * @param button chat-mode button identifier
	 * @return local text center X coordinate
	 */
	public int chatModeTextCenterX(int button) {
		return CHAT_MODE_TEXT_CENTER_X[requireChatModeButton(button)];
	}

	/**
	 * Returns the local label baseline for one chat-mode button.
	 * 
	 * @param button chat-mode button identifier
	 * @return local label baseline Y coordinate
	 */
	public int chatModeLabelY(int button) {
		return CHAT_MODE_LABEL_Y[requireChatModeButton(button)];
	}

	/**
	 * Returns the local status baseline shared by public/private/trade modes.
	 * 
	 * @return local status baseline Y coordinate
	 */
	public int chatModeStatusY() {
		return CHAT_MODE_STATUS_Y;
	}

	/**
	 * Tests a rectangle expressed using exclusive right/bottom edges.
	 *
	 * @param x      point X coordinate
	 * @param y      point Y coordinate
	 * @param left   inclusive left edge
	 * @param top    inclusive top edge
	 * @param right  exclusive right edge
	 * @param bottom exclusive bottom edge
	 * @return whether the point lies inside the rectangle
	 */
	private boolean containsExclusive(int x, int y, int left, int top, int right, int bottom) {
		return x >= left && y >= top && x < right && y < bottom;
	}

	/**
	 * Returns the baseline for one context-menu entry.
	 * 
	 * @param menuY      menu-local top coordinate
	 * @param entryCount number of entries
	 * @param entryIndex entry index in insertion order
	 * @return entry text baseline Y coordinate
	 */
	public int contextMenuEntryY(int menuY, int entryCount, int entryIndex) {
		return menuY + CONTEXT_MENU_FIRST_ENTRY_BASELINE + (entryCount - 1 - entryIndex) * CONTEXT_MENU_ROW_HEIGHT;
	}

	/**
	 * Tests whether a menu-local point hits one context-menu entry.
	 * 
	 * @param x         point X coordinate
	 * @param y         point Y coordinate
	 * @param menuX     menu X coordinate
	 * @param menuWidth menu width
	 * @param entryY    entry baseline Y coordinate
	 * @return whether the point hits the entry
	 */
	public boolean isContextMenuEntryHit(int x, int y, int menuX, int menuWidth, int entryY) {
		return x > menuX && x < menuX + menuWidth && y > entryY - CONTEXT_MENU_ENTRY_HIT_TOP
				&& y < entryY + CONTEXT_MENU_ENTRY_HIT_BOTTOM;
	}

	/**
	 * Returns the height used when clamping a context menu to a panel.
	 * 
	 * @param entryCount number of entries
	 * @return clamp height
	 */
	public int contextMenuClampHeight(int entryCount) {
		return CONTEXT_MENU_ROW_HEIGHT * entryCount + CONTEXT_MENU_CLAMP_HEIGHT_PADDING;
	}

	/**
	 * Returns the stored/drawn height of a context menu.
	 * 
	 * @param entryCount number of entries
	 * @return menu height
	 */
	public int contextMenuHeight(int entryCount) {
		return CONTEXT_MENU_ROW_HEIGHT * entryCount + CONTEXT_MENU_HEIGHT_PADDING;
	}

	/**
	 * Validates one tab index.
	 * 
	 * @param tab tab index
	 * @return the validated tab index
	 */
	private int requireTab(int tab) {
		if (tab < 0 || tab >= TAB_COUNT)
			throw new IllegalArgumentException("tab out of range: " + tab);
		return tab;
	}

	/**
	 * Validates one chat-mode button identifier.
	 * 
	 * @param button chat-mode button identifier
	 * @return the validated button identifier
	 */
	private int requireChatModeButton(int button) {
		if (button < 0 || button >= CHAT_MODE_HITBOXES.length)
			throw new IllegalArgumentException("chat-mode button out of range: " + button);
		return button;
	}

	/**
	 * Maps a right-anchored fixed-layout X coordinate into resizable space.
	 * 
	 * @param fixedX the fixed X
	 * @return the resizable X coordinate anchored to the right edge
	 */
	public int rightAnchoredX(int fixedX) {
		return fixedX + extraWidth();
	}

	/**
	 * Maps a bottom-anchored fixed-layout Y coordinate into resizable space.
	 * 
	 * @param fixedY the fixed Y
	 * @return the resizable Y coordinate anchored to the bottom edge
	 */
	public int bottomAnchoredY(int fixedY) {
		return fixedY + extraHeight();
	}
}
