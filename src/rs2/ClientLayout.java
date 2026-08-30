package rs2;

/**
 * Computes screen-space positions for the classic revision-377 interface in a
 * resizable host window.
 *
 * <p>The original 765x503 layout remains the minimum size. In resizable mode the
 * software-rendered game viewport becomes a full-window underlay. The right-hand
 * minimap/tab/sidebar cluster keeps its original fixed geometry and is anchored
 * to the right edge, while the chatbox and chat-mode strip stay anchored to the
 * bottom edge.</p>
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
     * Classic sidebar-tab hit rectangles as {left, rightExclusive, top, bottomExclusive}.
     * The slightly overlapping bounds are retained exactly from the fixed client.
     */
    private static final int[][] TAB_HITBOXES = {
            { 539, 574, 169, 205 }, { 569, 600, 168, 205 }, { 597, 628, 168, 205 },
            { 625, 670, 168, 203 }, { 666, 697, 168, 205 }, { 694, 725, 168, 205 },
            { 722, 757, 169, 205 }, { 540, 575, 466, 502 }, { 572, 603, 466, 503 },
            { 599, 630, 466, 503 }, { 627, 672, 467, 502 }, { 669, 700, 466, 503 },
            { 696, 727, 466, 503 }, { 724, 759, 466, 502 }
    };

    /**
     * Classic chat-mode hit rectangles as {left, rightExclusive, top, bottomExclusive}.
     */
    private static final int[][] CHAT_MODE_HITBOXES = {
            { 6, 107, 467, 500 }, { 135, 236, 467, 500 },
            { 273, 374, 467, 500 }, { 412, 513, 467, 500 }
    };

    /** Stores the current width. */
    private int width = FIXED_WIDTH;
    /** Stores the current height. */
    private int height = FIXED_HEIGHT;

    /**
     * Resizes the operation.
     *
     * @param width the width in pixels
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
    public int width() { return width; }
    /**
     * Returns the height.
     *
     * @return the height
     */
    public int height() { return height; }
    /**
     * Returns the extra width.
     *
     * @return the extra width
     */
    public int extraWidth() { return width - FIXED_WIDTH; }
    /**
     * Returns the extra height.
     *
     * @return the extra height
     */
    public int extraHeight() { return height - FIXED_HEIGHT; }

    /**
     * True as soon as either client dimension differs from the fixed layout.
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
    public int viewportX() { return VIEWPORT_X; }
    /**
     * Returns the viewport Y coordinate.
     *
     * @return the viewport Y
     */
    public int viewportY() { return VIEWPORT_Y; }

    /**
     * The fixed client keeps its original 512x334 viewport exactly. Once either
     * dimension is resized, both viewport dimensions become full-window underlay
     * dimensions. This deliberately avoids reintroducing the old right/bottom
     * gutters when only one window dimension is stretched.
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
     * @return the viewport width not covered by the right-hand HUD dock
     */
    public int unobscuredViewportWidth() {
        return isResizableMode() ? sidebarX() - viewportX() : FIXED_VIEWPORT_WIDTH;
    }

    /**
     * Height available to HUD text that should remain above the bottom chat HUD.
     * @return the viewport height above the bottom chat HUD
     */
    public int unobscuredViewportHeight() {
        return isResizableMode() ? chatboxY() - viewportY() : FIXED_VIEWPORT_HEIGHT;
    }

    /* Right-hand dock: preserve the original vertical geometry, anchor only X. */
    /**
     * Returns the left edge of the minimap dock.
     *
     * @return the minimap X coordinate
     */
    public int minimapX() { return MINIMAP_X + extraWidth(); }
    /**
     * Returns the minimap Y coordinate.
     *
     * @return the minimap Y
     */
    public int minimapY() { return MINIMAP_Y; }

    /**
     * Returns the left edge of the top tab strip.
     *
     * @return the top-tab X coordinate
     */
    public int topTabsX() { return TOP_TABS_X + extraWidth(); }
    /**
     * Returns the top tabs Y coordinate.
     *
     * @return the converted value
     */
    public int topTabsY() { return TOP_TABS_Y; }

    /**
     * Returns the left edge of the sidebar content area.
     *
     * @return the sidebar X coordinate
     */
    public int sidebarX() { return SIDEBAR_X + extraWidth(); }
    /**
     * Returns the sidebar Y coordinate.
     *
     * @return the sidebar Y
     */
    public int sidebarY() { return SIDEBAR_Y; }

    /**
     * Returns the left edge of the bottom tab strip.
     *
     * @return the bottom-tab X coordinate
     */
    public int bottomTabsX() { return BOTTOM_TABS_X + extraWidth(); }
    /**
     * Returns the bottom tabs Y coordinate.
     *
     * @return the bottom tabs Y
     */
    public int bottomTabsY() { return BOTTOM_TABS_Y; }

    /* Bottom HUD: preserve the original horizontal geometry, anchor only Y. */
    /**
     * Returns the chatbox X coordinate.
     *
     * @return the chatbox X
     */
    public int chatboxX() { return CHATBOX_X; }
    /**
     * Returns the top edge of the bottom-anchored chatbox.
     *
     * @return the chatbox Y coordinate
     */
    public int chatboxY() { return CHATBOX_Y + extraHeight(); }

    /**
     * Returns the chat modes X coordinate.
     *
     * @return the chat modes X
     */
    public int chatModesX() { return CHAT_MODES_X; }
    /**
     * Returns the top edge of the bottom-anchored chat-mode strip.
     *
     * @return the chat-mode Y coordinate
     */
    public int chatModesY() { return CHAT_MODES_Y + extraHeight(); }

    /**
     * Returns the X coordinate of the classic middle frame border.
     *
     * @return the middle-border X coordinate
     */
    public int middleBorderX() { return MIDDLE_BORDER_X + extraWidth(); }
    /**
     * Returns the Y coordinate of the classic lower frame border.
     *
     * @return the lower-border Y coordinate
     */
    public int lowerBorderY() { return LOWER_BORDER_Y + extraHeight(); }

    /**
     * Returns the viewport-local X origin for a modal/root interface. Fixed mode
     * preserves the original (0,0) placement; resizable mode centers the root in
     * the game area left of the right-hand HUD dock.
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
     * @param interfaceHeight the interface height
     * @return the viewport-local Y coordinate for the centered interface
     */
    public int centeredInterfaceY(int interfaceHeight) {
        if (!isResizableMode())
            return 0;
        return Math.max(0, (unobscuredViewportHeight() - interfaceHeight) / 2);
    }

    /**
     * True when a point is in the rectangular world render target and is not
     * covered by an interactive HUD region. The world still renders beneath all
     * of these areas; the exclusion only gives UI input priority.
     * @param x the X coordinate
     * @param y the Y coordinate
     * @return whether viewport interaction point
     */
    public boolean isViewportInteractionPoint(int x, int y) {
        if (!contains(x, y, viewportX(), viewportY(), viewportWidth(), viewportHeight()))
            return false;
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
     * @param x the X coordinate
     * @param y the Y coordinate
     * @param left the left
     * @param top the top
     * @param regionWidth the region width
     * @param regionHeight the region height
     * @return whether contains
     */
    private boolean contains(int x, int y, int left, int top, int regionWidth, int regionHeight) {
        return regionWidth > 0 && regionHeight > 0
                && x >= left && y >= top && x < left + regionWidth && y < top + regionHeight;
    }

    /**
     * Tests whether the supplied screen coordinate falls within one classic
     * sidebar-tab hitbox.
     *
     * @param tab tab index 0..13
     * @param x screen X coordinate
     * @param y screen Y coordinate
     * @return whether the point lies in that tab's original fixed-layout hitbox
     */
    public boolean isTabHit(int tab, int x, int y) {
        if (tab < 0 || tab >= TAB_HITBOXES.length)
            return false;
        int[] bounds = TAB_HITBOXES[tab];
        int fixedX = x - extraWidth();
        return containsExclusive(fixedX, y, bounds[0], bounds[2], bounds[1], bounds[3]);
    }

    /**
     * Returns the fixed chat-mode button at the supplied screen coordinate.
     *
     * @param x screen X coordinate
     * @param y screen Y coordinate
     * @return one of the {@code CHAT_MODE_*} constants, or {@link #NO_CHAT_MODE_BUTTON}
     */
    public int chatModeButtonAt(int x, int y) {
        int fixedY = y - extraHeight();
        for (int button = 0; button < CHAT_MODE_HITBOXES.length; button++) {
            int[] bounds = CHAT_MODE_HITBOXES[button];
            if (containsExclusive(x, fixedY, bounds[0], bounds[2], bounds[1], bounds[3]))
                return button;
        }
        return NO_CHAT_MODE_BUTTON;
    }

    /**
     * Tests a rectangle expressed using exclusive right/bottom edges.
     *
     * @param x point X coordinate
     * @param y point Y coordinate
     * @param left inclusive left edge
     * @param top inclusive top edge
     * @param right exclusive right edge
     * @param bottom exclusive bottom edge
     * @return whether the point lies inside the rectangle
     */
    private boolean containsExclusive(int x, int y, int left, int top, int right, int bottom) {
        return x >= left && y >= top && x < right && y < bottom;
    }

    /**
     * Maps a right-anchored fixed-layout X coordinate into resizable space.
     * @param fixedX the fixed X
     * @return the resizable X coordinate anchored to the right edge
     */
    public int rightAnchoredX(int fixedX) { return fixedX + extraWidth(); }

    /**
     * Maps a bottom-anchored fixed-layout Y coordinate into resizable space.
     * @param fixedY the fixed Y
     * @return the resizable Y coordinate anchored to the bottom edge
     */
    public int bottomAnchoredY(int fixedY) { return fixedY + extraHeight(); }
}
