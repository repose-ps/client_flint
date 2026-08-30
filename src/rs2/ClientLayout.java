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

    public static final int FIXED_WIDTH = 765;
    public static final int FIXED_HEIGHT = 503;
    public static final int FIXED_VIEWPORT_WIDTH = 512;
    public static final int FIXED_VIEWPORT_HEIGHT = 334;

    private static final int MINIMAP_WIDTH = 172;
    private static final int MINIMAP_HEIGHT = 156;
    private static final int SIDEBAR_WIDTH = 190;
    private static final int SIDEBAR_HEIGHT = 261;
    private static final int TOP_TABS_WIDTH = 249;
    private static final int TOP_TABS_HEIGHT = 45;
    private static final int BOTTOM_TABS_WIDTH = 269;
    private static final int BOTTOM_TABS_HEIGHT = 37;
    private static final int CHATBOX_WIDTH = 479;
    private static final int CHATBOX_HEIGHT = 96;
    private static final int CHAT_MODES_WIDTH = 496;
    private static final int CHAT_MODES_HEIGHT = 50;

    private int width = FIXED_WIDTH;
    private int height = FIXED_HEIGHT;

    public void resize(int width, int height) {
        this.width = Math.max(FIXED_WIDTH, width);
        this.height = Math.max(FIXED_HEIGHT, height);
    }

    public int width() { return width; }
    public int height() { return height; }
    public int extraWidth() { return width - FIXED_WIDTH; }
    public int extraHeight() { return height - FIXED_HEIGHT; }

    /** True as soon as either client dimension differs from the fixed layout. */
    public boolean isResizableMode() {
        return width != FIXED_WIDTH || height != FIXED_HEIGHT;
    }

    public int viewportX() { return 4; }
    public int viewportY() { return 4; }

    /**
     * The fixed client keeps its original 512x334 viewport exactly. Once either
     * dimension is resized, both viewport dimensions become full-window underlay
     * dimensions. This deliberately avoids reintroducing the old right/bottom
     * gutters when only one window dimension is stretched.
     */
    public int viewportWidth() {
        return isResizableMode() ? width - viewportX() : FIXED_VIEWPORT_WIDTH;
    }

    public int viewportHeight() {
        return isResizableMode() ? height - viewportY() : FIXED_VIEWPORT_HEIGHT;
    }

    /**
     * Width available to HUD text that should not be obscured by the classic
     * right-hand dock.
     */
    public int unobscuredViewportWidth() {
        return isResizableMode() ? sidebarX() - viewportX() : FIXED_VIEWPORT_WIDTH;
    }

    /**
     * Height available to HUD text that should remain above the bottom chat HUD.
     */
    public int unobscuredViewportHeight() {
        return isResizableMode() ? chatboxY() - viewportY() : FIXED_VIEWPORT_HEIGHT;
    }

    /* Right-hand dock: preserve the original vertical geometry, anchor only X. */
    public int minimapX() { return 550 + extraWidth(); }
    public int minimapY() { return 4; }

    public int topTabsX() { return 516 + extraWidth(); }
    public int topTabsY() { return 160; }

    public int sidebarX() { return 553 + extraWidth(); }
    public int sidebarY() { return 205; }

    public int bottomTabsX() { return 496 + extraWidth(); }
    public int bottomTabsY() { return 466; }

    /* Bottom HUD: preserve the original horizontal geometry, anchor only Y. */
    public int chatboxX() { return 17; }
    public int chatboxY() { return 357 + extraHeight(); }

    public int chatModesX() { return 0; }
    public int chatModesY() { return 453 + extraHeight(); }

    public int middleBorderX() { return 516 + extraWidth(); }
    public int lowerBorderY() { return 338 + extraHeight(); }

    /**
     * Returns the viewport-local X origin for a modal/root interface. Fixed mode
     * preserves the original (0,0) placement; resizable mode centers the root in
     * the game area left of the right-hand HUD dock.
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

    private boolean contains(int x, int y, int left, int top, int regionWidth, int regionHeight) {
        return regionWidth > 0 && regionHeight > 0
                && x >= left && y >= top && x < left + regionWidth && y < top + regionHeight;
    }

    /** Maps a right-anchored fixed-layout X coordinate into resizable space. */
    public int rightAnchoredX(int fixedX) { return fixedX + extraWidth(); }

    /** Maps a bottom-anchored fixed-layout Y coordinate into resizable space. */
    public int bottomAnchoredY(int fixedY) { return fixedY + extraHeight(); }
}
