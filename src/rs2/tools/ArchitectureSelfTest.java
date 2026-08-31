package rs2.tools;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Set;
import java.util.function.Consumer;

import rs2.Client;
import rs2.net.Buffer;
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
            "rs2.ClientIncomingPacketHandler",
            "rs2.InterfacePacketHandler",
            "rs2.SocialPacketHandler",
            "rs2.RegionPacketHandler",
            "rs2.CameraPacketHandler",
            "rs2.AudioPacketHandler",
            "rs2.ActorPacketHandler",
            "rs2.ClientStatePacketHandler"
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
            "drawWorldHintIcon"
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
