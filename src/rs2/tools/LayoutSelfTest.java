package rs2.tools;

import rs2.ClientLayout;
import rs2.game.MinimapRenderer;
import rs2.game.entity.Player;

/** Table-driven regression coverage for fixed and resizable client geometry. */
public final class LayoutSelfTest {

    /** Prevents instantiation. */
    private LayoutSelfTest() {
    }

    /**
     * Runs the layout regression suite.
     *
     * @param args command-line arguments; none are accepted
     */
    public static void main(String[] args) {
        if (args.length != 0) {
            throw new IllegalArgumentException("Usage: LayoutSelfTest");
        }
        int checks = run();
        System.out.println("LayoutSelfTest: PASS (" + checks + " checks)");
    }

    /**
     * Runs all layout checks.
     *
     * @return completed assertion count
     */
    static int run() {
        SelfTestSupport test = new SelfTestSupport();
        assertLayout(test, new LayoutCase(765, 503, false,
                512, 334, 550, 553, 516, 496, 357, 453, 512, 334, 0, 0));
        assertLayout(test, new LayoutCase(900, 503, true,
                896, 499, 685, 688, 651, 631, 357, 453, 684, 353, 86, 9));
        assertLayout(test, new LayoutCase(765, 700, true,
                761, 696, 550, 553, 516, 496, 554, 650, 549, 550, 18, 108));
        assertLayout(test, new LayoutCase(1000, 700, true,
                996, 696, 785, 788, 751, 731, 554, 650, 784, 550, 136, 108));
        assertLayout(test, new LayoutCase(1600, 900, true,
                1596, 896, 1385, 1388, 1351, 1331, 754, 850, 1384, 750, 436, 208));
        testHitTesting(test);
        testTabGeometry(test);
        testChatModeGeometry(test);
        testPanelAndFrameGeometry(test);
        testMinimapClickGeometry(test);
        testContextMenuGeometry(test);
        testMinimumClamp(test);
        return test.checks();
    }

    /**
     * Verifies one complete layout case.
     *
     * @param test assertion sink
     * @param expected expected geometry
     */
    private static void assertLayout(SelfTestSupport test, LayoutCase expected) {
        ClientLayout layout = new ClientLayout();
        layout.resize(expected.width, expected.height);

        test.equal(layout.width(), expected.width, "layout width " + expected.width + "x" + expected.height);
        test.equal(layout.height(), expected.height, "layout height " + expected.width + "x" + expected.height);
        test.check(layout.isResizableMode() == expected.resizable,
                "resizable mode " + expected.width + "x" + expected.height);
        test.equal(layout.viewportX(), 4, "viewport X " + expected.width + "x" + expected.height);
        test.equal(layout.viewportY(), 4, "viewport Y " + expected.width + "x" + expected.height);
        test.equal(layout.viewportWidth(), expected.viewportWidth,
                "viewport width " + expected.width + "x" + expected.height);
        test.equal(layout.viewportHeight(), expected.viewportHeight,
                "viewport height " + expected.width + "x" + expected.height);
        test.equal(layout.minimapX(), expected.minimapX, "minimap X " + expected.width + "x" + expected.height);
        test.equal(layout.minimapY(), 4, "minimap Y " + expected.width + "x" + expected.height);
        test.equal(layout.sidebarX(), expected.sidebarX, "sidebar X " + expected.width + "x" + expected.height);
        test.equal(layout.sidebarY(), 205, "sidebar Y " + expected.width + "x" + expected.height);
        test.equal(layout.topTabsX(), expected.topTabsX, "top-tabs X " + expected.width + "x" + expected.height);
        test.equal(layout.topTabsY(), 160, "top-tabs Y " + expected.width + "x" + expected.height);
        test.equal(layout.bottomTabsX(), expected.bottomTabsX,
                "bottom-tabs X " + expected.width + "x" + expected.height);
        test.equal(layout.bottomTabsY(), 466, "bottom-tabs Y " + expected.width + "x" + expected.height);
        test.equal(layout.chatboxX(), 17, "chatbox X " + expected.width + "x" + expected.height);
        test.equal(layout.chatboxY(), expected.chatboxY, "chatbox Y " + expected.width + "x" + expected.height);
        test.equal(layout.chatModesX(), 0, "chat modes X " + expected.width + "x" + expected.height);
        test.equal(layout.chatModesY(), expected.chatModesY, "chat modes Y " + expected.width + "x" + expected.height);
        test.equal(layout.unobscuredViewportWidth(), expected.unobscuredWidth,
                "unobscured width " + expected.width + "x" + expected.height);
        test.equal(layout.unobscuredViewportHeight(), expected.unobscuredHeight,
                "unobscured height " + expected.width + "x" + expected.height);
        test.equal(layout.centeredInterfaceX(512), expected.centeredX,
                "modal center X " + expected.width + "x" + expected.height);
        test.equal(layout.centeredInterfaceY(334), expected.centeredY,
                "modal center Y " + expected.width + "x" + expected.height);
        test.equal(layout.middleBorderX(), ClientLayout.MIDDLE_BORDER_X + layout.extraWidth(),
                "middle border X " + expected.width + "x" + expected.height);
        test.equal(layout.lowerBorderY(), ClientLayout.LOWER_BORDER_Y + layout.extraHeight(),
                "lower border Y " + expected.width + "x" + expected.height);
        test.equal(layout.rightFrameTopX(), ClientLayout.RIGHT_FRAME_TOP_X + layout.extraWidth(),
                "right frame top X " + expected.width + "x" + expected.height);
        test.equal(layout.rightFrameMiddleX(), ClientLayout.RIGHT_FRAME_MIDDLE_X + layout.extraWidth(),
                "right frame middle X " + expected.width + "x" + expected.height);
        test.equal(layout.minimapLocalX(layout.minimapX() + 25), 25,
                "minimap local X " + expected.width + "x" + expected.height);
        test.equal(layout.minimapLocalY(layout.minimapY() + 5), 5,
                "minimap local Y " + expected.width + "x" + expected.height);
        test.equal(layout.chatboxLocalX(layout.chatboxX() + 463), 463,
                "chatbox local X " + expected.width + "x" + expected.height);
        test.equal(layout.chatboxLocalY(layout.chatboxY() + 77), 77,
                "chatbox local Y " + expected.width + "x" + expected.height);
    }

    /**
     * Verifies tab, chat-button and viewport hit testing.
     *
     * @param test assertion sink
     */
    private static void testHitTesting(SelfTestSupport test) {
        ClientLayout fixed = new ClientLayout();
        test.check(fixed.isTabHit(0, 539, 169), "fixed top-tab inclusive edge");
        test.check(!fixed.isTabHit(0, 574, 169), "fixed top-tab exclusive right edge");
        test.check(fixed.isTabHit(13, 758, 501), "fixed bottom-tab interior");
        test.check(!fixed.isTabHit(-1, 539, 169), "negative tab index rejected");
        test.check(!fixed.isTabHit(ClientLayout.TAB_COUNT, 539, 169), "past-last tab index rejected");

        test.equal(fixed.chatModeButtonAt(6, 467), ClientLayout.CHAT_MODE_PUBLIC,
                "fixed public chat button");
        test.equal(fixed.chatModeButtonAt(135, 467), ClientLayout.CHAT_MODE_PRIVATE,
                "fixed private chat button");
        test.equal(fixed.chatModeButtonAt(273, 467), ClientLayout.CHAT_MODE_TRADE,
                "fixed trade chat button");
        test.equal(fixed.chatModeButtonAt(412, 467), ClientLayout.CHAT_MODE_REPORT_ABUSE,
                "fixed report-abuse button");
        test.equal(fixed.chatModeButtonAt(513, 467), ClientLayout.NO_CHAT_MODE_BUTTON,
                "fixed chat button exclusive right edge");

        test.check(fixed.isViewportInteractionPoint(10, 10), "fixed viewport world point");
        test.check(!fixed.isViewportInteractionPoint(560, 20), "fixed minimap has input priority");
        test.check(!fixed.isViewportInteractionPoint(20, 370), "fixed chatbox has input priority");
        test.check(!fixed.isViewportInteractionPoint(0, 0), "outside viewport rejected");

        ClientLayout resized = new ClientLayout();
        resized.resize(1000, 700);
        int extraWidth = 1000 - ClientLayout.FIXED_WIDTH;
        int extraHeight = 700 - ClientLayout.FIXED_HEIGHT;
        test.check(resized.isTabHit(0, 539 + extraWidth, 169), "resized tab X anchor");
        test.equal(resized.chatModeButtonAt(6, 467 + extraHeight), ClientLayout.CHAT_MODE_PUBLIC,
                "resized chat-button Y anchor");
        test.check(resized.isViewportInteractionPoint(700, 300), "resized unobscured world point");
        test.check(!resized.isViewportInteractionPoint(resized.sidebarX() + 5, resized.sidebarY() + 5),
                "resized sidebar has input priority");
        test.check(!resized.isViewportInteractionPoint(resized.chatboxX() + 5, resized.chatboxY() + 5),
                "resized chatbox has input priority");
        test.equal(resized.rightAnchoredX(550), 550 + extraWidth, "generic right anchor");
        test.equal(resized.bottomAnchoredY(357), 357 + extraHeight, "generic bottom anchor");
    }

    /** Verifies all classic tab hitboxes and render coordinates.
     * @param test assertion sink
     */
    private static void testTabGeometry(SelfTestSupport test) {
        int[][] fixedBounds = {
                { 539, 574, 169, 205 }, { 569, 600, 168, 205 }, { 597, 628, 168, 205 },
                { 625, 670, 168, 203 }, { 666, 697, 168, 205 }, { 694, 725, 168, 205 },
                { 722, 757, 169, 205 }, { 540, 575, 466, 502 }, { 572, 603, 466, 503 },
                { 599, 630, 466, 503 }, { 627, 672, 467, 502 }, { 669, 700, 466, 503 },
                { 696, 727, 466, 503 }, { 724, 759, 466, 502 }
        };
        int[] highlightX = { 22, 54, 82, 110, 153, 181, 209, 42, 74, 102, 130, 173, 201, 229 };
        int[] highlightY = { 10, 8, 8, 8, 8, 8, 9, 0, 0, 0, 1, 0, 0, 0 };
        int[] iconX = { 29, 53, 82, 115, 153, 180, 208, -1, 74, 102, 137, 174, 201, 226 };
        int[] iconY = { 13, 11, 11, 12, 13, 11, 13, -1, 2, 3, 4, 2, 2, 2 };

        ClientLayout fixed = new ClientLayout();
        ClientLayout resized = new ClientLayout();
        resized.resize(1000, 700);
        for (int tab = 0; tab < ClientLayout.TAB_COUNT; tab++) {
            int[] bounds = fixedBounds[tab];
            test.check(fixed.isTabHit(tab, bounds[0], bounds[2]), "tab fixed inclusive corner " + tab);
            test.check(!fixed.isTabHit(tab, bounds[1], bounds[2]), "tab fixed exclusive right " + tab);
            test.check(!fixed.isTabHit(tab, bounds[0], bounds[3]), "tab fixed exclusive bottom " + tab);
            int shiftedX = bounds[0] + resized.extraWidth();
            test.check(resized.isTabHit(tab, shiftedX, bounds[2]), "tab resized anchor " + tab);
            test.equal(fixed.tabHighlightX(tab), highlightX[tab], "tab highlight X " + tab);
            test.equal(fixed.tabHighlightY(tab), highlightY[tab], "tab highlight Y " + tab);
            test.equal(fixed.tabIconX(tab), iconX[tab], "tab icon X " + tab);
            test.equal(fixed.tabIconY(tab), iconY[tab], "tab icon Y " + tab);
        }
    }

    /** Verifies chat-mode render and hit geometry from one layout source.
     * @param test assertion sink
     */
    private static void testChatModeGeometry(SelfTestSupport test) {
        int[][] fixedBounds = {
                { 6, 107, 467, 500 }, { 135, 236, 467, 500 },
                { 273, 374, 467, 500 }, { 412, 513, 467, 500 }
        };
        int[] centers = { 55, 184, 324, 458 };
        int[] labels = { 28, 28, 28, 33 };
        ClientLayout fixed = new ClientLayout();
        ClientLayout resized = new ClientLayout();
        resized.resize(1000, 700);
        for (int button = 0; button < fixedBounds.length; button++) {
            int[] bounds = fixedBounds[button];
            test.equal(fixed.chatModeButtonAt(bounds[0], bounds[2]), button,
                    "chat mode fixed inclusive corner " + button);
            test.equal(fixed.chatModeButtonAt(bounds[1], bounds[2]), ClientLayout.NO_CHAT_MODE_BUTTON,
                    "chat mode fixed exclusive right " + button);
            test.equal(fixed.chatModeButtonAt(bounds[0], bounds[3]), ClientLayout.NO_CHAT_MODE_BUTTON,
                    "chat mode fixed exclusive bottom " + button);
            test.equal(resized.chatModeButtonAt(bounds[0], bounds[2] + resized.extraHeight()), button,
                    "chat mode resized anchor " + button);
            test.equal(fixed.chatModeTextCenterX(button), centers[button], "chat mode center X " + button);
            test.equal(fixed.chatModeLabelY(button), labels[button], "chat mode label Y " + button);
        }
        test.equal(fixed.chatModeStatusY(), 41, "chat mode status baseline");
    }

    /** Verifies panel boundaries, chatbox subregions, and frame anchors.
     * @param test assertion sink
     */
    private static void testPanelAndFrameGeometry(SelfTestSupport test) {
        ClientLayout fixed = new ClientLayout();
        test.check(fixed.isTopTabsSpellBlockPoint(516, 160), "top-tabs spell block inclusive origin");
        test.check(fixed.isTopTabsSpellBlockPoint(765, 205), "top-tabs spell block legacy inclusive far edge");
        test.check(!fixed.isTopTabsSpellBlockPoint(766, 205), "top-tabs spell block rejects beyond right");

        test.check(!fixed.isSidebarInteractionPoint(553, 205), "sidebar strict origin excluded");
        test.check(fixed.isSidebarInteractionPoint(554, 206), "sidebar strict interior");
        test.check(!fixed.isSidebarInteractionPoint(743, 206), "sidebar strict right excluded");
        test.check(!fixed.isChatboxInteractionPoint(17, 357), "chatbox strict origin excluded");
        test.check(fixed.isChatboxInteractionPoint(18, 358), "chatbox strict interior");
        test.check(fixed.isChatboxMessageMenuPoint(425, 433), "chatbox message menu interior");
        test.check(!fixed.isChatboxMessageMenuPoint(426, 433), "chatbox message menu right edge");
        test.check(!fixed.isChatboxMessageMenuPoint(425, 434), "chatbox message menu bottom edge");

        test.check(fixed.isChatboxScrollbarInputCandidate(449, 333), "chat scrollbar gate interior");
        test.check(!fixed.isChatboxScrollbarInputCandidate(448, 333), "chat scrollbar gate left edge");
        test.check(!fixed.isChatboxScrollbarInputCandidate(449, 332), "chat scrollbar gate top edge");

        test.equal(fixed.rightFrameTopX(), 722, "fixed right-frame top X");
        test.equal(fixed.rightFrameMiddleX(), 743, "fixed right-frame middle X");
        test.equal(fixed.middleBorderX(), 516, "fixed middle-frame X");
        test.equal(fixed.lowerBorderY(), 338, "fixed lower-frame Y");
        test.equal(fixed.lowerVerticalMiddleY(), 357, "fixed lower vertical-middle Y");
        ClientLayout heightResized = new ClientLayout();
        heightResized.resize(765, 700);
        test.equal(heightResized.lowerVerticalMiddleY(), 357, "lower vertical-middle Y remains fixed on height resize");
        test.equal(ClientLayout.SCROLLBAR_WIDTH, 16, "scrollbar width");
        test.equal(ClientLayout.SCROLLBAR_ARROW_HEIGHT, 16, "scrollbar arrow height");
        test.equal(ClientLayout.SCROLLBAR_MIN_THUMB_HEIGHT, 8, "scrollbar minimum thumb height");
    }

    /** Verifies that screen-to-minimap geometry is shared with click transforms.
     * @param test assertion sink
     */
    private static void testMinimapClickGeometry(SelfTestSupport test) {
        MinimapRenderer minimap = new MinimapRenderer();
        Player player = new Player(() -> 0);
        player.x = 3200;
        player.y = 6400;

        ClientLayout fixed = new ClientLayout();
        MinimapRenderer.Click fixedCenter = minimap.transformClick(
                fixed.minimapLocalX(fixed.minimapX() + 98), fixed.minimapLocalY(fixed.minimapY() + 80),
                player, 0);
        test.check(fixedCenter != null, "fixed minimap center click accepted");
        test.equal(fixedCenter.localX, 0, "fixed minimap center local X");
        test.equal(fixedCenter.localY, 0, "fixed minimap center local Y");
        test.equal(fixedCenter.tileX, player.x >> 7, "fixed minimap center tile X");
        test.equal(fixedCenter.tileY, player.y >> 7, "fixed minimap center tile Y");

        ClientLayout resized = new ClientLayout();
        resized.resize(1000, 700);
        MinimapRenderer.Click resizedCenter = minimap.transformClick(
                resized.minimapLocalX(resized.minimapX() + 98), resized.minimapLocalY(resized.minimapY() + 80),
                player, 0);
        test.check(resizedCenter != null, "resized minimap center click accepted");
        test.equal(resizedCenter.localX, fixedCenter.localX, "resized minimap local X preserved");
        test.equal(resizedCenter.localY, fixedCenter.localY, "resized minimap local Y preserved");
        test.equal(resizedCenter.tileX, fixedCenter.tileX, "resized minimap tile X preserved");
        test.equal(resizedCenter.tileY, fixedCenter.tileY, "resized minimap tile Y preserved");

        MinimapRenderer.Click outside = minimap.transformClick(24, 80, player, 0);
        test.check(outside == null, "minimap click rejects left of aperture");
    }

    /** Verifies shared context-menu placement and row hit geometry.
     * @param test assertion sink
     */
    private static void testContextMenuGeometry(SelfTestSupport test) {
        ClientLayout layout = new ClientLayout();
        test.equal(layout.contextMenuClampHeight(4), 81, "context menu clamp height");
        test.equal(layout.contextMenuHeight(4), 82, "context menu drawn height");
        test.equal(layout.contextMenuEntryY(10, 4, 0), 86, "context menu first inserted entry baseline");
        test.equal(layout.contextMenuEntryY(10, 4, 3), 41, "context menu last inserted entry baseline");
        int entryY = layout.contextMenuEntryY(10, 4, 3);
        test.check(layout.isContextMenuEntryHit(21, entryY, 20, 100, entryY), "context menu entry interior");
        test.check(!layout.isContextMenuEntryHit(20, entryY, 20, 100, entryY), "context menu entry strict left");
        test.check(!layout.isContextMenuEntryHit(21, entryY - ClientLayout.CONTEXT_MENU_ENTRY_HIT_TOP,
                20, 100, entryY), "context menu entry strict top");
        test.check(!layout.isContextMenuEntryHit(21, entryY + ClientLayout.CONTEXT_MENU_ENTRY_HIT_BOTTOM,
                20, 100, entryY), "context menu entry strict bottom");
    }

    /**
     * Verifies that dimensions smaller than fixed mode are clamped.
     *
     * @param test assertion sink
     */
    private static void testMinimumClamp(SelfTestSupport test) {
        ClientLayout layout = new ClientLayout();
        layout.resize(400, 300);
        test.equal(layout.width(), ClientLayout.FIXED_WIDTH, "minimum width clamp");
        test.equal(layout.height(), ClientLayout.FIXED_HEIGHT, "minimum height clamp");
        test.check(!layout.isResizableMode(), "minimum clamp retains fixed mode");
    }

    /**
     * Expected geometry for one client size.
     *
     * @param width client width
     * @param height client height
     * @param resizable whether resizable mode should be active
     * @param viewportWidth expected viewport width
     * @param viewportHeight expected viewport height
     * @param minimapX expected minimap X coordinate
     * @param sidebarX expected sidebar X coordinate
     * @param topTabsX expected top-tab-strip X coordinate
     * @param bottomTabsX expected bottom-tab-strip X coordinate
     * @param chatboxY expected chatbox Y coordinate
     * @param chatModesY expected chat-mode-strip Y coordinate
     * @param unobscuredWidth expected unobscured viewport width
     * @param unobscuredHeight expected unobscured viewport height
     * @param centeredX expected centered-interface X coordinate
     * @param centeredY expected centered-interface Y coordinate
     */
    private record LayoutCase(
            int width,
            int height,
            boolean resizable,
            int viewportWidth,
            int viewportHeight,
            int minimapX,
            int sidebarX,
            int topTabsX,
            int bottomTabsX,
            int chatboxY,
            int chatModesY,
            int unobscuredWidth,
            int unobscuredHeight,
            int centeredX,
            int centeredY) {
    }
}
