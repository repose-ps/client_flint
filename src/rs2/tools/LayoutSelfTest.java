package rs2.tools;

import rs2.ClientLayout;

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
