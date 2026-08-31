package rs2.tools;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import rs2.ui.menu.MenuActionDomain;
import rs2.ui.menu.MenuState;

/**
 * Permanent census of revision-377 menu-action routing.
 *
 * <p>The suite freezes the 7.1 action-domain boundary before action effects are
 * extracted from {@code Client}. It verifies every public action ID, including
 * low-priority encodings, belongs to exactly one intended application domain.</p>
 */
public final class ActionDispatchSelfTest {

    /** Non-action public integers on {@link MenuState}. */
    private static final Set<String> STRUCTURAL_CONSTANTS = Set.of(
            "CAPACITY", "LOW_PRIORITY_OFFSET", "BUILD_LIMIT");

    /** Expected action IDs grouped by their current application owner. */
    private static final Map<MenuActionDomain, int[]> ACTIONS = Map.ofEntries(
            Map.entry(MenuActionDomain.PLAYER, new int[] {
                    MenuState.PLAYER_OPTION_1, MenuState.PLAYER_OPTION_2, MenuState.PLAYER_OPTION_3,
                    MenuState.PLAYER_OPTION_4, MenuState.PLAYER_OPTION_5, MenuState.USE_ITEM_ON_PLAYER,
                    MenuState.CAST_SPELL_ON_PLAYER
            }),
            Map.entry(MenuActionDomain.NPC, new int[] {
                    MenuState.NPC_OPTION_1, MenuState.NPC_OPTION_2, MenuState.NPC_OPTION_3,
                    MenuState.NPC_OPTION_4, MenuState.NPC_OPTION_5, MenuState.USE_ITEM_ON_NPC,
                    MenuState.CAST_SPELL_ON_NPC, MenuState.EXAMINE_NPC
            }),
            Map.entry(MenuActionDomain.OBJECT, new int[] {
                    MenuState.OBJECT_OPTION_1, MenuState.OBJECT_OPTION_2, MenuState.OBJECT_OPTION_3,
                    MenuState.OBJECT_OPTION_4, MenuState.OBJECT_OPTION_5, MenuState.USE_ITEM_ON_OBJECT,
                    MenuState.CAST_SPELL_ON_OBJECT, MenuState.EXAMINE_OBJECT
            }),
            Map.entry(MenuActionDomain.GROUND_ITEM, new int[] {
                    MenuState.GROUND_ITEM_OPTION_1, MenuState.GROUND_ITEM_OPTION_2,
                    MenuState.GROUND_ITEM_OPTION_3, MenuState.GROUND_ITEM_OPTION_4,
                    MenuState.GROUND_ITEM_OPTION_5, MenuState.USE_ITEM_ON_GROUND_ITEM,
                    MenuState.CAST_SPELL_ON_GROUND_ITEM, MenuState.EXAMINE_GROUND_ITEM
            }),
            Map.entry(MenuActionDomain.INVENTORY, new int[] {
                    MenuState.INVENTORY_ITEM_OPTION_1, MenuState.INVENTORY_ITEM_OPTION_2,
                    MenuState.INVENTORY_ITEM_OPTION_3, MenuState.INVENTORY_ITEM_OPTION_4,
                    MenuState.INVENTORY_ITEM_OPTION_5, MenuState.SELECT_ITEM,
                    MenuState.USE_ITEM_ON_INVENTORY_ITEM, MenuState.CAST_SPELL_ON_INVENTORY_ITEM,
                    MenuState.EXAMINE_INVENTORY_ITEM, MenuState.WIDGET_ITEM_OPTION_1,
                    MenuState.WIDGET_ITEM_OPTION_2, MenuState.WIDGET_ITEM_OPTION_3,
                    MenuState.WIDGET_ITEM_OPTION_4, MenuState.WIDGET_ITEM_OPTION_5
            }),
            Map.entry(MenuActionDomain.WIDGET, new int[] {
                    MenuState.WIDGET_BUTTON, MenuState.SELECT_SPELL, MenuState.CLOSE_DIALOGUE,
                    MenuState.CLOSE_INTERFACE, MenuState.WIDGET_TOGGLE_VARP,
                    MenuState.WIDGET_SET_VARP, MenuState.WIDGET_CONTINUE
            }),
            Map.entry(MenuActionDomain.SOCIAL, new int[] {
                    MenuState.ADD_FRIEND, MenuState.ADD_IGNORE, MenuState.REMOVE_FRIEND,
                    MenuState.REMOVE_IGNORE, MenuState.MESSAGE_FRIEND, MenuState.REPORT_ABUSE,
                    MenuState.ACCEPT_TRADE, MenuState.ACCEPT_CHALLENGE
            }),
            Map.entry(MenuActionDomain.WALK, new int[] { MenuState.WALK_HERE }),
            Map.entry(MenuActionDomain.CANCEL, new int[] { MenuState.CANCEL_ACTION }));

    /** Prevents instantiation. */
    private ActionDispatchSelfTest() {
    }

    /**
     * Runs the action-routing regression checks as a standalone tool.
     *
     * @param args no arguments are accepted
     * @throws IllegalAccessException if a menu constant cannot be read
     */
    public static void main(String[] args) throws IllegalAccessException {
        if (args.length != 0) {
            throw new IllegalArgumentException("Usage: ActionDispatchSelfTest");
        }
        System.out.println("ActionDispatchSelfTest: PASS (" + run() + " checks)");
    }

    /**
     * Runs the action-routing regression checks.
     *
     * @return completed assertion count
     * @throws IllegalAccessException if a menu constant cannot be read
     */
    static int run() throws IllegalAccessException {
        SelfTestSupport test = new SelfTestSupport();
        Set<Integer> expectedIds = testDomainRoutes(test);
        testMenuStateActionSurface(test, expectedIds);
        testUnknownActions(test);
        return test.checks();
    }

    /**
     * Verifies the explicit action-domain census and low-priority normalization.
     *
     * @param test assertion sink
     * @return complete set of expected normal action IDs
     */
    private static Set<Integer> testDomainRoutes(SelfTestSupport test) {
        Set<Integer> ids = new HashSet<>();
        int actionCount = 0;
        for (Map.Entry<MenuActionDomain, int[]> route : ACTIONS.entrySet()) {
            MenuActionDomain expectedDomain = route.getKey();
            for (int actionId : route.getValue()) {
                actionCount++;
                test.check(ids.add(actionId), "menu action ID is unique: " + actionId);
                test.check(MenuState.actionDomain(actionId) == expectedDomain,
                        "normal action domain " + actionId + " -> " + expectedDomain);
                test.check(MenuState.actionDomain(MenuState.lowPriority(actionId)) == expectedDomain,
                        "low-priority action domain " + actionId + " -> " + expectedDomain);
            }
        }
        test.equal(actionCount, 62, "revision-377 menu action census size");
        test.equal(ACTIONS.get(MenuActionDomain.PLAYER).length, 7, "player action count");
        test.equal(ACTIONS.get(MenuActionDomain.NPC).length, 8, "NPC action count");
        test.equal(ACTIONS.get(MenuActionDomain.OBJECT).length, 8, "object action count");
        test.equal(ACTIONS.get(MenuActionDomain.GROUND_ITEM).length, 8, "ground-item action count");
        test.equal(ACTIONS.get(MenuActionDomain.INVENTORY).length, 14, "inventory action count");
        test.equal(ACTIONS.get(MenuActionDomain.WIDGET).length, 7, "widget action count");
        test.equal(ACTIONS.get(MenuActionDomain.SOCIAL).length, 8, "social action count");
        test.equal(ACTIONS.get(MenuActionDomain.WALK).length, 1, "walk action count");
        test.equal(ACTIONS.get(MenuActionDomain.CANCEL).length, 1, "cancel action count");
        return ids;
    }

    /**
     * Verifies that every public action constant on {@link MenuState} is represented
     * by the explicit census and no structural constant is mistaken for an action.
     *
     * @param test assertion sink
     * @param expectedIds expected action IDs
     * @throws IllegalAccessException if a menu constant cannot be read
     */
    private static void testMenuStateActionSurface(SelfTestSupport test, Set<Integer> expectedIds)
            throws IllegalAccessException {
        Set<Integer> reflectedIds = new HashSet<>();
        int reflectedActions = 0;
        for (Field field : MenuState.class.getDeclaredFields()) {
            int modifiers = field.getModifiers();
            if (field.getType() != int.class || !Modifier.isPublic(modifiers) || !Modifier.isStatic(modifiers)
                    || !Modifier.isFinal(modifiers) || STRUCTURAL_CONSTANTS.contains(field.getName())) {
                continue;
            }
            reflectedActions++;
            int actionId = field.getInt(null);
            test.check(reflectedIds.add(actionId), "reflected menu action ID is unique: " + field.getName());
            test.check(expectedIds.contains(actionId), "reflected menu action is in census: " + field.getName());
            test.check(MenuState.actionDomain(actionId) != MenuActionDomain.UNKNOWN,
                    "reflected menu action has a route: " + field.getName());
        }
        test.equal(reflectedActions, 62, "reflected revision-377 menu action count");
        test.check(reflectedIds.equals(expectedIds), "reflected menu actions exactly match route census");
    }

    /** Verifies unsupported and structural values cannot accidentally enter a domain.
     * @param test assertion sink
     */
    private static void testUnknownActions(SelfTestSupport test) {
        test.check(MenuState.actionDomain(0) == MenuActionDomain.UNKNOWN, "zero action is unknown");
        test.check(MenuState.actionDomain(MenuState.CAPACITY) == MenuActionDomain.UNKNOWN,
                "menu capacity is not an action");
        test.check(MenuState.actionDomain(MenuState.BUILD_LIMIT) == MenuActionDomain.UNKNOWN,
                "menu build limit is not an action");
        test.check(MenuState.actionDomain(MenuState.LOW_PRIORITY_OFFSET) == MenuActionDomain.UNKNOWN,
                "priority offset alone is not an action");
    }
}
