package rs2.tools;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import rs2.action.ClientActionDispatcher;
import rs2.action.PlayerActionHandler;
import rs2.chat.ChatController;
import rs2.chat.SocialManager;
import rs2.game.ActorSynchronizer;
import rs2.game.entity.Player;
import rs2.game.render.GameRenderer;
import rs2.net.MovementPacketEncoder;
import rs2.net.NetworkSession;
import rs2.ui.ClientLayout;
import rs2.ui.InterfaceController;
import rs2.ui.menu.MenuActionDomain;
import rs2.ui.menu.MenuController;
import rs2.ui.menu.MenuEntry;
import rs2.ui.menu.MenuState;

/**
 * Permanent census of revision-377 menu-action routing.
 *
 * <p>The suite freezes the Phase 7 action-domain boundary and verifies every
 * public action ID, including low-priority encodings, belongs to exactly one
 * intended application domain. It also exercises dispatcher cleanup/reset
 * semantics and representative behavior of extracted concrete handlers.</p>
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
        testDispatcherContract(test);
        testDispatcherResetContract(test);
        testConcretePlayerHandler(test);
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
    /**
     * Verifies the extracted dispatcher owns normalization, single-domain routing,
     * input-dialog cancellation, early-return preservation, and shared cleanup.
     *
     * @param test assertion sink
     */
    private static void testDispatcherContract(SelfTestSupport test) {
        InterfaceController interfaces = new InterfaceController();
        ChatController chat = new ChatController();
        GameRenderer renderer = new GameRenderer();
        MenuController menus = new MenuController(new ClientLayout(), interfaces, new SocialManager(), chat, () -> 0,
                new InterfaceController.RedrawSink() {
                    @Override
                    public void redrawSidebar() {
                    }

                    @Override
                    public void redrawTabs() {
                    }

                    @Override
                    public void redrawChatbox() {
                    }

                    @Override
                    public void redrawGameScreen() {
                    }
                });
        int[] calls = new int[8];
        int[] last = new int[5];
        ClientActionDispatcher.ActionHandler player = recordingHandler(calls, 0, last, interfaces, false);
        ClientActionDispatcher.ActionHandler npc = recordingHandler(calls, 1, last, interfaces, false);
        ClientActionDispatcher.ActionHandler object = recordingHandler(calls, 2, last, interfaces, false);
        ClientActionDispatcher.ActionHandler ground = recordingHandler(calls, 3, last, interfaces, false);
        ClientActionDispatcher.ActionHandler inventory = (actionId, argument0, argument1, argument2, menuIndex) -> {
            record(calls, 4, last, actionId, argument0, argument1, argument2, menuIndex);
            if (actionId == MenuState.SELECT_ITEM) {
                interfaces.state().itemSelected = 1;
                interfaces.state().spellSelected = 0;
                return true;
            }
            return false;
        };
        ClientActionDispatcher.ActionHandler widget = (actionId, argument0, argument1, argument2, menuIndex) -> {
            record(calls, 5, last, actionId, argument0, argument1, argument2, menuIndex);
            if (actionId == MenuState.SELECT_SPELL) {
                interfaces.state().itemSelected = 0;
                interfaces.state().spellSelected = 1;
                return true;
            }
            return false;
        };
        ClientActionDispatcher.ActionHandler social = recordingHandler(calls, 6, last, interfaces, false);
        ClientActionDispatcher.ActionHandler walk = recordingHandler(calls, 7, last, interfaces, false);
        ClientActionDispatcher dispatcher = new ClientActionDispatcher(menus, chat, interfaces, renderer, player, npc,
                object, ground, inventory, widget, social, walk);

        dispatcher.dispatch(-1);
        test.equal(total(calls), 0, "negative menu index performs no dispatch");
        test.check(!renderer.sidebarRedrawPending(), "negative menu index performs no cleanup redraw");

        int playerIndex = add(menus, MenuState.lowPriority(MenuState.PLAYER_OPTION_2), 11, 22, 33);
        chat.setInputDialogState(1);
        interfaces.state().itemSelected = 1;
        interfaces.state().spellSelected = 1;
        dispatcher.dispatch(playerIndex);
        test.equal(calls[0], 1, "player action reaches player callback once");
        test.equal(total(calls), 1, "player action reaches exactly one domain callback");
        test.equal(last[0], MenuState.PLAYER_OPTION_2, "dispatcher normalizes low-priority action ID");
        test.equal(last[1], 11, "dispatcher preserves argument 0");
        test.equal(last[2], 22, "dispatcher preserves argument 1");
        test.equal(last[3], 33, "dispatcher preserves argument 2");
        test.equal(last[4], playerIndex, "dispatcher preserves menu index");
        test.equal(chat.inputDialogState(), 0, "non-cancel action closes input dialog");
        test.check(renderer.chatboxRedrawPending(), "input-dialog cancellation requests chatbox redraw");
        test.equal(interfaces.state().itemSelected, 0, "normal action clears item selection");
        test.equal(interfaces.state().spellSelected, 0, "normal action clears spell selection");
        test.check(renderer.sidebarRedrawPending(), "normal action requests sidebar redraw");

        renderer.clearChatboxRedraw();
        renderer.clearSidebarRedraw();
        chat.setInputDialogState(2);
        interfaces.state().itemSelected = 1;
        interfaces.state().spellSelected = 1;
        int cancelIndex = add(menus, MenuState.CANCEL_ACTION, 0, 0, 0);
        dispatcher.dispatch(cancelIndex);
        test.equal(chat.inputDialogState(), 2, "cancel preserves open input dialog");
        test.equal(total(calls), 1, "cancel does not invoke a domain callback");
        test.equal(interfaces.state().itemSelected, 0, "cancel clears item selection");
        test.equal(interfaces.state().spellSelected, 0, "cancel clears spell selection");
        test.check(renderer.sidebarRedrawPending(), "cancel still requests shared sidebar redraw");
        test.check(!renderer.chatboxRedrawPending(), "cancel does not request dialog-cancellation redraw");

        renderer.clearSidebarRedraw();
        int selectItemIndex = add(menus, MenuState.SELECT_ITEM, 44, 55, 66);
        dispatcher.dispatch(selectItemIndex);
        test.equal(calls[4], 1, "select-item reaches inventory callback");
        test.equal(interfaces.state().itemSelected, 1, "select-item preserves item selection");
        test.equal(interfaces.state().spellSelected, 0, "select-item preserves inventory handler spell state");
        test.check(!renderer.sidebarRedrawPending(), "dispatcher skips shared redraw after select-item early return");

        int selectSpellIndex = add(menus, MenuState.SELECT_SPELL, 77, 88, 99);
        dispatcher.dispatch(selectSpellIndex);
        test.equal(calls[5], 1, "select-spell reaches widget callback");
        test.equal(interfaces.state().itemSelected, 0, "select-spell preserves widget handler item state");
        test.equal(interfaces.state().spellSelected, 1, "select-spell preserves spell selection");
        test.check(!renderer.sidebarRedrawPending(), "dispatcher skips shared redraw after select-spell early return");

        renderer.clearSidebarRedraw();
        chat.setInputDialogState(3);
        interfaces.state().itemSelected = 1;
        interfaces.state().spellSelected = 1;
        int unknownIndex = add(menus, 123_456, 1, 2, 3);
        dispatcher.dispatch(unknownIndex);
        test.equal(total(calls), 3, "unknown action invokes no domain callback");
        test.equal(chat.inputDialogState(), 0, "unknown non-cancel action closes input dialog");
        test.equal(interfaces.state().itemSelected, 0, "unknown action performs shared item cleanup");
        test.equal(interfaces.state().spellSelected, 0, "unknown action performs shared spell cleanup");
        test.check(renderer.sidebarRedrawPending(), "unknown action requests shared sidebar redraw");
    }

    /**
     * Verifies the dispatcher fans full-login reset through every action domain.
     *
     * @param test assertion sink
     */
    private static void testDispatcherResetContract(SelfTestSupport test) {
        InterfaceController interfaces = new InterfaceController();
        ChatController chat = new ChatController();
        GameRenderer renderer = new GameRenderer();
        MenuController menus = new MenuController(new ClientLayout(), interfaces, new SocialManager(), chat, () -> 0,
                emptyRedrawSink());
        int[] resets = new int[8];
        ClientActionDispatcher.ActionHandler[] handlers = new ClientActionDispatcher.ActionHandler[8];
        for (int index = 0; index < handlers.length; index++) {
            final int domain = index;
            handlers[index] = new ClientActionDispatcher.ActionHandler() {
                @Override
                public boolean dispatch(int actionId, int argument0, int argument1, int argument2, int menuIndex) {
                    return false;
                }

                @Override
                public void resetForLogin() {
                    resets[domain]++;
                }
            };
        }
        ClientActionDispatcher dispatcher = new ClientActionDispatcher(menus, chat, interfaces, renderer, handlers[0],
                handlers[1], handlers[2], handlers[3], handlers[4], handlers[5], handlers[6], handlers[7]);
        dispatcher.resetForLogin();
        for (int index = 0; index < resets.length; index++) {
            test.equal(resets[index], 1, "login reset reaches action domain " + index);
        }
    }

    /**
     * Exercises one concrete extracted target handler, including movement, crosshair,
     * and transformed packet-field behavior.
     *
     * @param test assertion sink
     */
    private static void testConcretePlayerHandler(SelfTestSupport test) {
        ActorSynchronizer actors = new ActorSynchronizer(() -> 0);
        Player target = new Player(() -> 0);
        target.pathX[0] = 50;
        target.pathY[0] = 60;
        actors.players[7] = target;
        NetworkSession network = new NetworkSession();
        network.initializeOpcodeCiphers(new int[] { 0, 0, 0, 0 });
        InterfaceController interfaces = new InterfaceController();
        int[] movement = new int[9];
        int[] crosshair = new int[1];
        PlayerActionHandler handler = new PlayerActionHandler(actors, network.outgoing, interfaces,
                (allowAlternative, targetX, targetY, targetWidth, targetHeight, movementType, interactionType,
                        orientation, accessMask) -> {
                    movement[0]++;
                    movement[1] = allowAlternative ? 1 : 0;
                    movement[2] = targetX;
                    movement[3] = targetY;
                    movement[4] = targetWidth;
                    movement[5] = targetHeight;
                    movement[6] = movementType;
                    movement[7] = interactionType;
                    movement[8] = orientation | accessMask;
                    return true;
                },
                () -> crosshair[0]++);
        handler.dispatch(MenuState.PLAYER_OPTION_1, 7, 0, 0, 0);
        test.equal(movement[0], 1, "player handler routes exactly once");
        test.equal(movement[1], 0, "player handler disables alternative route");
        test.equal(movement[2], 50, "player handler routes to target X");
        test.equal(movement[3], 60, "player handler routes to target Y");
        test.equal(movement[4], 1, "player handler uses one-tile target width");
        test.equal(movement[5], 1, "player handler uses one-tile target height");
        test.equal(movement[6], MovementPacketEncoder.INTERACTION, "player handler uses interaction movement packet");
        test.equal(crosshair[0], 1, "player handler marks interaction crosshair");
        test.equal(network.outgoing.position, 3, "player option 1 packet length");
        test.equal(network.outgoing.payload[1] & 0xff, 135, "player option 1 transformed index low byte");
        test.equal(network.outgoing.payload[2] & 0xff, 0, "player option 1 transformed index high byte");

        int previousPosition = network.outgoing.position;
        handler.dispatch(MenuState.PLAYER_OPTION_1, 8, 0, 0, 0);
        test.equal(movement[0], 1, "missing player does not route");
        test.equal(crosshair[0], 1, "missing player does not mark crosshair");
        test.equal(network.outgoing.position, previousPosition, "missing player writes no packet");
    }

    /** Creates an inert interface redraw sink for action tests.
     * @return redraw sink
     */
    private static InterfaceController.RedrawSink emptyRedrawSink() {
        return new InterfaceController.RedrawSink() {
            @Override
            public void redrawSidebar() {
            }

            @Override
            public void redrawTabs() {
            }

            @Override
            public void redrawChatbox() {
            }

            @Override
            public void redrawGameScreen() {
            }
        };
    }

    /**
     * Creates a callback that records one domain invocation.
     *
     * @param calls per-domain counters
     * @param domain domain index
     * @param last last dispatched action/arguments
     * @param interfaces interface state owner
     * @param preserve whether the callback preserves selection
     * @return recording action callback
     */
    private static ClientActionDispatcher.ActionHandler recordingHandler(int[] calls, int domain, int[] last,
            InterfaceController interfaces, boolean preserve) {
        return (actionId, argument0, argument1, argument2, menuIndex) -> {
            record(calls, domain, last, actionId, argument0, argument1, argument2, menuIndex);
            if (preserve) {
                interfaces.state().itemSelected = 1;
            }
            return preserve;
        };
    }

    /** Records one callback invocation.
     * @param calls per-domain counters
     * @param domain domain index
     * @param last last dispatched action/arguments
     * @param actionId normalized action ID
     * @param argument0 first argument
     * @param argument1 second argument
     * @param argument2 third argument
     * @param menuIndex menu index
     */
    private static void record(int[] calls, int domain, int[] last, int actionId, int argument0, int argument1,
            int argument2, int menuIndex) {
        calls[domain]++;
        last[0] = actionId;
        last[1] = argument0;
        last[2] = argument1;
        last[3] = argument2;
        last[4] = menuIndex;
    }

    /** Appends one test menu entry.
     * @param menus menu owner
     * @param action action ID
     * @param argument0 first argument
     * @param argument1 second argument
     * @param argument2 third argument
     * @return appended menu index
     */
    private static int add(MenuController menus, int action, int argument0, int argument1, int argument2) {
        int index = menus.state().count;
        menus.state().add(new MenuEntry("test", action, argument0, argument1, argument2));
        return index;
    }

    /** Returns the total callback count.
     * @param calls per-domain counters
     * @return total callback count
     */
    private static int total(int[] calls) {
        int total = 0;
        for (int count : calls) {
            total += count;
        }
        return total;
    }

}
