package rs2.tools;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Consumer;

import rs2.Client;
import rs2.action.ClientActionDispatcher;
import rs2.net.Buffer;
import rs2.net.NetworkSession;
import rs2.ui.WidgetRenderer;
import rs2.ui.menu.MenuEntry;
import rs2.ui.menu.MenuState;

/**
 * Permanent architecture checks for the completed client decomposition.
 *
 * <p>The checks intentionally use reflection so they validate compiled
 * dependency surfaces without requiring a source-tree location at runtime.</p>
 */
public final class ArchitectureSelfTest {

    /** Packet application classes that must not retain the application coordinator. */
    private static final String[] PACKET_HANDLER_CLASSES = {
            "rs2.packet.PacketDomainDispatcher",
            "rs2.packet.InterfacePacketHandler",
            "rs2.packet.SocialPacketHandler",
            "rs2.packet.RegionPacketHandler",
            "rs2.packet.CameraPacketHandler",
            "rs2.packet.AudioPacketHandler",
            "rs2.packet.ActorPacketHandler",
            "rs2.packet.ClientStatePacketHandler"
    };

    /** Cohesive menu-action effect handlers extracted during Phase 7.3. */
    private static final String[] ACTION_HANDLER_CLASSES = {
            "rs2.action.PlayerActionHandler",
            "rs2.action.NpcActionHandler",
            "rs2.action.ObjectActionHandler",
            "rs2.action.GroundItemActionHandler",
            "rs2.action.InventoryActionHandler",
            "rs2.action.WidgetActionHandler",
            "rs2.action.SocialActionHandler",
            "rs2.action.WalkActionHandler"
    };

    /** Classes moved during the post-Phase-6 package cohesion sweep. */
    private static final String[] PACKAGED_CLASSES = {
            "rs2.action.ClientActionDispatcher",
            "rs2.shell.GameShell",
            "rs2.shell.GameFrame",
            "rs2.ui.ClientLayout",
            "rs2.media.animation.AnimationFrame",
            "rs2.media.animation.Skeleton",
            "rs2.scene.tile.GroundItemTile",
            "rs2.scene.tile.InteractiveObject"
    };

    /** Obsolete pre-sweep class names that must not reappear. */
    private static final String[] OBSOLETE_CLASS_NAMES = {
            "rs2.ClientActionDispatcher",
            "rs2.ClientIncomingPacketHandler",
            "rs2.InterfacePacketHandler",
            "rs2.SocialPacketHandler",
            "rs2.RegionPacketHandler",
            "rs2.CameraPacketHandler",
            "rs2.AudioPacketHandler",
            "rs2.ActorPacketHandler",
            "rs2.ClientStatePacketHandler",
            "rs2.GameShell",
            "rs2.GameFrame",
            "rs2.ClientLayout",
            "rs2.media.AnimationFrame",
            "rs2.media.Skeleton",
            "rs2.scene.GroundItemTile",
            "rs2.scene.InteractiveObject"
    };

    /** Transitional packet bridge methods removed from {@link Client}. */
    private static final Set<String> REMOVED_CLIENT_BRIDGES = Set.of(
            "packetSocialManager",
            "packetChatController",
            "packetInterfaceController",
            "packetLoginUsername",
            "packetSoundEffectQueue",
            "packetMusicController",
            "packetWidgetRuntime",
            "packetOnDemandFetcher",
            "packetActorSynchronizer",
            "packetCameraController",
            "packetMinimapRenderer",
            "packetRegionManager",
            "packetVarpState",
            "packetWorldState",
            "packetZoneUpdates",
            "packetActorChatHandler",
            "requestSidebarRedraw",
            "requestChatboxRedraw",
            "requestTabAreaRedraw",
            "requestChatModesRedraw",
            "requestGameScreenRedraw",
            "queueAreaSound",
            "resetCharacterAppearance",
            "getConfiguredHost",
            "drawActorOverlays",
            "drawWorldHintIcon",
            "dispatchPlayerMenuAction",
            "dispatchNpcMenuAction",
            "dispatchObjectMenuAction",
            "dispatchGroundItemMenuAction",
            "dispatchInventoryMenuAction",
            "dispatchWidgetMenuAction",
            "dispatchSocialMenuAction",
            "dispatchMiscMenuAction",
            "walkToGameObject",
            "markInventoryInteraction",
            "handleWidgetContentAction"
    );

    /** Ambiguous pre-6.3 Buffer method names that must not reappear. */
    private static final Set<String> LEGACY_BUFFER_METHODS = Set.of(
            "writeLength",
            "startBitAccess",
            "readByteAdd",
            "readByteNeg",
            "readByteSub",
            "writeShortAddLE",
            "readUnsignedShortAddLE",
            "readShortLE",
            "readShortAdd"
    );

    /** Prevents instantiation. */
    private ArchitectureSelfTest() {
    }

    /**
     * Runs the architecture regression checks as a standalone tool.
     *
     * @param args no arguments are accepted
     * @throws ClassNotFoundException if a required packet-domain class is missing
     */
    public static void main(String[] args) throws ClassNotFoundException {
        if (args.length != 0) {
            throw new IllegalArgumentException("Usage: ArchitectureSelfTest");
        }
        System.out.println("ArchitectureSelfTest: PASS (" + run() + " checks)");
    }

    /**
     * Runs the architecture regression checks.
     *
     * @return completed assertion count
     * @throws ClassNotFoundException if a required packet-domain class is missing
     */
    static int run() throws ClassNotFoundException {
        SelfTestSupport test = new SelfTestSupport();
        testClientFieldVisibility(test);
        testPacketHandlerBoundaries(test);
        testPacketAdapterBoundary(test);
        testActionDispatcherBoundary(test);
        testPackageCohesion(test);
        testRemovedClientBridges(test);
        testWidgetRendererBoundary(test);
        testMenuEntryState(test);
        testBufferNaming(test);
        return test.checks();
    }

    /**
     * Verifies that decomposition-era client state is no longer exposed as public fields.
     *
     * @param test assertion sink
     */
    private static void testClientFieldVisibility(SelfTestSupport test) {
        for (Field field : Client.class.getDeclaredFields()) {
            test.check(!Modifier.isPublic(field.getModifiers()), "Client field is not public: " + field.getName());
        }
    }

    /**
     * Verifies that packet-domain classes depend on narrow capabilities rather than {@link Client}.
     *
     * @param test assertion sink
     * @throws ClassNotFoundException if a packet-domain class is missing
     */
    private static void testPacketHandlerBoundaries(SelfTestSupport test) throws ClassNotFoundException {
        for (String className : PACKET_HANDLER_CLASSES) {
            Class<?> handler = Class.forName(className);
            for (Field field : handler.getDeclaredFields()) {
                test.check(field.getType() != Client.class, className + " field does not retain Client: " + field.getName());
            }
            for (Constructor<?> constructor : handler.getDeclaredConstructors()) {
                test.check(Arrays.stream(constructor.getParameterTypes()).noneMatch(type -> type == Client.class),
                        className + " constructor does not accept Client");
            }
        }
    }


    /**
     * Verifies that the sole root packet adapter is package-private and intentionally owns the Client boundary.
     *
     * @param test assertion sink
     * @throws ClassNotFoundException if the adapter class is missing
     */
    private static void testPacketAdapterBoundary(SelfTestSupport test) throws ClassNotFoundException {
        Class<?> adapter = Class.forName("rs2.ClientPacketDispatcher");
        test.check(!Modifier.isPublic(adapter.getModifiers()), "ClientPacketDispatcher stays package-private");
        long clientFields = Arrays.stream(adapter.getDeclaredFields()).filter(field -> field.getType() == Client.class).count();
        test.check(clientFields == 1, "ClientPacketDispatcher is the single intentional packet Client boundary");
    }

    /**
     * Verifies that menu-action routing has a dedicated boundary that does not
     * retain the application coordinator.
     *
     * @param test assertion sink
     */
    private static void testActionDispatcherBoundary(SelfTestSupport test) {
        Class<?> dispatcher = ClientActionDispatcher.class;
        test.check(Modifier.isPublic(dispatcher.getModifiers()),
                "ClientActionDispatcher is the public rs2.action routing facade");
        for (Field field : dispatcher.getDeclaredFields()) {
            test.check(field.getType() != Client.class,
                    "ClientActionDispatcher field does not retain Client: " + field.getName());
        }
        for (Constructor<?> constructor : dispatcher.getDeclaredConstructors()) {
            test.check(Arrays.stream(constructor.getParameterTypes()).noneMatch(type -> type == Client.class),
                    "ClientActionDispatcher constructor does not accept Client");
        }
        for (String className : ACTION_HANDLER_CLASSES) {
            try {
                Class<?> handler = Class.forName(className);
                test.check(ClientActionDispatcher.ActionHandler.class.isAssignableFrom(handler),
                        className + " implements the action-handler contract");
                for (Field field : handler.getDeclaredFields()) {
                    test.check(field.getType() != Client.class,
                            className + " field does not retain Client: " + field.getName());
                    test.check(field.getType() != NetworkSession.class,
                            className + " field does not retain NetworkSession: " + field.getName());
                }
                for (Constructor<?> constructor : handler.getDeclaredConstructors()) {
                    test.check(Arrays.stream(constructor.getParameterTypes()).noneMatch(type -> type == Client.class),
                            className + " constructor does not accept Client");
                    test.check(Arrays.stream(constructor.getParameterTypes()).noneMatch(type -> type == NetworkSession.class),
                            className + " constructor does not accept NetworkSession");
                }
            } catch (ClassNotFoundException exception) {
                test.check(false, "action handler is present: " + className);
            }
        }
        Set<String> clientFieldNames = Arrays.stream(Client.class.getDeclaredFields())
                .map(Field::getName)
                .collect(java.util.stream.Collectors.toSet());
        for (String counter : Set.of("npcAction118Counter", "groundItemAction684Counter", "groundItemAction26Counter",
                "inventoryAction227Counter", "inventoryAction961Counter")) {
            test.check(!clientFieldNames.contains(counter), "action-local counter moved out of Client: " + counter);
        }
        long clientFields = Arrays.stream(Client.class.getDeclaredFields())
                .filter(field -> field.getType() == ClientActionDispatcher.class)
                .count();
        test.check(clientFields == 1, "Client owns exactly one ClientActionDispatcher boundary");
    }

    /**
     * Verifies the package-only moves performed by the cohesion sweep.
     *
     * @param test assertion sink
     * @throws ClassNotFoundException if a moved class is missing
     */
    private static void testPackageCohesion(SelfTestSupport test) throws ClassNotFoundException {
        for (String className : PACKAGED_CLASSES) {
            test.check(Class.forName(className) != null, "packaged class is present: " + className);
        }
        for (String className : OBSOLETE_CLASS_NAMES) {
            try {
                Class.forName(className);
                test.check(false, "obsolete class name stays absent: " + className);
            } catch (ClassNotFoundException expected) {
                test.check(true, "obsolete class name stays absent: " + className);
            }
        }
    }

    /**
     * Verifies that transitional packet/redraw bridge methods stay removed from {@link Client}.
     *
     * @param test assertion sink
     */
    private static void testRemovedClientBridges(SelfTestSupport test) {
        Set<String> declaredMethods = Arrays.stream(Client.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(java.util.stream.Collectors.toSet());
        for (String bridge : REMOVED_CLIENT_BRIDGES) {
            test.check(!declaredMethods.contains(bridge), "removed Client bridge stays absent: " + bridge);
        }
    }

    /**
     * Verifies that widget rendering no longer owns a generic application mutation callback.
     *
     * @param test assertion sink
     */
    private static void testWidgetRendererBoundary(SelfTestSupport test) {
        for (Field field : WidgetRenderer.class.getDeclaredFields()) {
            test.check(field.getType() != Consumer.class,
                    "WidgetRenderer field is not a generic Consumer: " + field.getName());
        }
        for (Constructor<?> constructor : WidgetRenderer.class.getDeclaredConstructors()) {
            test.check(Arrays.stream(constructor.getParameterTypes()).noneMatch(type -> type == Consumer.class),
                    "WidgetRenderer constructor does not accept a generic Consumer");
        }
    }

    /**
     * Verifies that menu state retains cohesive entries instead of the old parallel arrays.
     *
     * @param test assertion sink
     */
    private static void testMenuEntryState(SelfTestSupport test) {
        Set<String> oldFields = Set.of("actionNames", "actionIds", "actionCmd1", "actionCmd2", "actionCmd3");
        boolean hasEntryArray = false;
        for (Field field : MenuState.class.getDeclaredFields()) {
            test.check(!oldFields.contains(field.getName()), "legacy menu array stays absent: " + field.getName());
            if (field.getType() == MenuEntry[].class) {
                hasEntryArray = true;
            }
        }
        test.check(hasEntryArray, "MenuState owns a MenuEntry array");
    }

    /**
     * Verifies that ambiguous Buffer operation names removed in Phase 6.3 stay absent.
     *
     * @param test assertion sink
     */
    private static void testBufferNaming(SelfTestSupport test) {
        Set<String> methodNames = Arrays.stream(Buffer.class.getDeclaredMethods())
                .map(Method::getName)
                .collect(java.util.stream.Collectors.toSet());
        for (String legacyName : LEGACY_BUFFER_METHODS) {
            test.check(!methodNames.contains(legacyName), "legacy Buffer method stays absent: " + legacyName);
        }
    }
}
