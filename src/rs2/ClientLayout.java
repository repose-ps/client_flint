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
    private static final int MINIMAP_WIDTH = 172;
    /** Constant value for minimap height. */
    private static final int MINIMAP_HEIGHT = 156;
    /** Constant value for sidebar width. */
    private static final int SIDEBAR_WIDTH = 190;
    /** Constant value for sidebar height. */
    private static final int SIDEBAR_HEIGHT = 261;
    /** Constant value for top tabs width. */
    private static final int TOP_TABS_WIDTH = 249;
    /** Constant value for top tabs height. */
    private static final int TOP_TABS_HEIGHT = 45;
    /** Constant value for bottom tabs width. */
    private static final int BOTTOM_TABS_WIDTH = 269;
    /** Constant value for bottom tabs height. */
    private static final int BOTTOM_TABS_HEIGHT = 37;
    /** Constant value for chatbox width. */
    private static final int CHATBOX_WIDTH = 479;
    /** Constant value for chatbox height. */
    private static final int CHATBOX_HEIGHT = 96;
    /** Constant value for chat modes width. */
    private static final int CHAT_MODES_WIDTH = 496;
    /** Constant value for chat modes height. */
    private static final int CHAT_MODES_HEIGHT = 50;

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
    public int viewportX() { return 4; }
    /**
     * Returns the viewport Y coordinate.
     *
     * @return the viewport Y
     */
    public int viewportY() { return 4; }

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
    public int minimapX() { return 550 + extraWidth(); }
    /**
     * Returns the minimap Y coordinate.
     *
     * @return the minimap Y
     */
    public int minimapY() { return 4; }

    /**
     * Returns the left edge of the top tab strip.
     *
     * @return the top-tab X coordinate
     */
    public int topTabsX() { return 516 + extraWidth(); }
    /**
     * Returns the top tabs Y coordinate.
     *
     * @return the converted value
     */
    public int topTabsY() { return 160; }

    /**
     * Returns the left edge of the sidebar content area.
     *
     * @return the sidebar X coordinate
     */
    public int sidebarX() { return 553 + extraWidth(); }
    /**
     * Returns the sidebar Y coordinate.
     *
     * @return the sidebar Y
     */
    public int sidebarY() { return 205; }

    /**
     * Returns the left edge of the bottom tab strip.
     *
     * @return the bottom-tab X coordinate
     */
    public int bottomTabsX() { return 496 + extraWidth(); }
    /**
     * Returns the bottom tabs Y coordinate.
     *
     * @return the bottom tabs Y
     */
    public int bottomTabsY() { return 466; }

    /* Bottom HUD: preserve the original horizontal geometry, anchor only Y. */
    /**
     * Returns the chatbox X coordinate.
     *
     * @return the chatbox X
     */
    public int chatboxX() { return 17; }
    /**
     * Returns the top edge of the bottom-anchored chatbox.
     *
     * @return the chatbox Y coordinate
     */
    public int chatboxY() { return 357 + extraHeight(); }

    /**
     * Returns the chat modes X coordinate.
     *
     * @return the chat modes X
     */
    public int chatModesX() { return 0; }
    /**
     * Returns the top edge of the bottom-anchored chat-mode strip.
     *
     * @return the chat-mode Y coordinate
     */
    public int chatModesY() { return 453 + extraHeight(); }

    /**
     * Returns the X coordinate of the classic middle frame border.
     *
     * @return the middle-border X coordinate
     */
    public int middleBorderX() { return 516 + extraWidth(); }
    /**
     * Returns the Y coordinate of the classic lower frame border.
     *
     * @return the lower-border Y coordinate
     */
    public int lowerBorderY() { return 338 + extraHeight(); }

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
