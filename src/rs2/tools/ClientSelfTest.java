package rs2.tools;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import rs2.cache.cfg.BitMasks;
import rs2.chat.ChatCodec;
import rs2.game.VarpState;
import rs2.net.Buffer;
import rs2.scene.SceneConfig;
import rs2.scene.SceneConstants;
import rs2.scene.SceneUid;
import rs2.scene.util.TiledUtils;
import rs2.text.Base37;
import rs2.ui.menu.MenuEntry;
import rs2.ui.menu.MenuState;

/**
 * Permanent executable regression gate for core revision-377 client behavior.
 *
 * <p>
 * The suite runs architecture, action-dispatch, protocol, layout, renderer,
 * widget-content, naming/chat, varp, menu and scene-geometry checks without
 * requiring JUnit or a separate build system. Supplying a revision-377 cache
 * directory additionally enables the real-cache renderer texture check.
 * </p>
 */
public final class ClientSelfTest {

	/** Prevents instantiation. */
	private ClientSelfTest() {
	}

	/**
	 * Runs the complete permanent client self-test gate.
	 *
	 * @param args optional revision-377 cache directory
	 * @throws Exception if a test fixture or loopback protocol check fails
	 */
	public static void main(String[] args) throws Exception {
		if (args.length > 1) {
			throw new IllegalArgumentException("Usage: ClientSelfTest [revision-377-rscache-directory]");
		}
		Path cache = args.length == 1 ? Path.of(args[0]).toAbsolutePath().normalize() : null;

		int checks = runCoreClientChecks();
		checks += ArchitectureSelfTest.run();
		checks += ActionDispatchSelfTest.run();
		checks += ActionPacketSelfTest.run();
		checks += WidgetContentSelfTest.run();
		checks += ProtocolSelfTest.run();
		checks += LayoutSelfTest.run();
		checks += RendererGoldenTest.run(cache);
		checks += GpuTerrainMeshSelfTest.run();
		checks += GpuStaticSceneMeshSelfTest.run();
		checks += FrameTimingSelfTest.run();

		System.out.println("ClientSelfTest: PASS (" + checks + " checks"
				+ (cache == null ? ", cache-free" : ", real-cache renderer enabled") + ")");
	}

	/**
	 * Runs non-protocol core client checks.
	 * 
	 * @return completed assertion count
	 */
	private static int runCoreClientChecks() {
		SelfTestSupport test = new SelfTestSupport();
		testBase37(test);
		testChatCodec(test);
		testVarpState(test);
		testBitMasks(test);
		testMenuPriority(test);
		testScenePacking(test);
		testChunkGeometry(test);
		return test.checks();
	}

	/**
	 * Verifies Base37 known values.
	 * 
	 * @param test assertion sink
	 */
	private static void testBase37(SelfTestSupport test) {
		test.equal(Base37.encode("zezima"), 1_813_643_468L, "Base37 known name");
		test.equal(Base37.encode("Rune Scape"), 2_414_415_295_518_618L, "Base37 separator name");
		test.equal(Base37.encode("abc123"), 73_283_673L, "Base37 alphanumeric name");
		test.equal(Base37.encode("abcdefghijklmnop"), 187_939_216_216_112_118L, "Base37 twelve-character limit");
		test.equal(Base37.decode(1_813_643_468L), "zezima", "Base37 decode known name");
		test.equal(Base37.decode(2_414_415_295_518_618L), "rune_scape", "Base37 decode separator name");
		test.equal(Base37.decode(0), "invalid_name", "Base37 rejects zero");
		test.equal(Base37.decode(37), "invalid_name", "Base37 rejects trailing-zero digit");
	}

	/**
	 * Verifies revision-377 chat codec vectors.
	 * 
	 * @param test assertion sink
	 */
	private static void testChatCodec(SelfTestSupport test) {
		Buffer encoded = new Buffer(64);
		ChatCodec.encode("hello world!", encoded);
		test.bytes(Arrays.copyOf(encoded.payload, encoded.position),
				new byte[] { 0x61, (byte) 0xbb, 0x40, (byte) 0xd1, 0x49, (byte) 0xba, (byte) 0xe9 },
				"chat known bytes");
		encoded.position = 0;
		test.equal(ChatCodec.decode(encoded, 7), "Hello world!", "chat known decode");
		test.equal(ChatCodec.normalize("RuneScape 377"), "Runescape 377", "chat normalization");
		test.equal(ChatCodec.normalize("test? yes."), "Test? Yes. ", "chat sentence capitalization");
		test.equal(ChatCodec.normalize("\u2603"), "  ", "unsupported chat character becomes space");
	}

	/**
	 * Verifies varp current/shadow synchronization behavior.
	 * 
	 * @param test assertion sink
	 */
	private static void testVarpState(SelfTestSupport test) {
		VarpState state = new VarpState();
		test.equal(state.get(10), 0, "varp starts at zero");
		state.set(10, 1);
		test.equal(state.toggleBinary(10), 0, "varp binary toggle off");
		test.equal(state.toggleBinary(10), 1, "varp binary toggle on");
		test.check(!state.acceptServerValue(10, 1), "unchanged authoritative varp does not report change");
		state.set(10, 5);
		List<Integer> changed = new ArrayList<>();
		state.synchronizeToShadow(changed::add);
		test.equal(state.get(10), 1, "varp synchronizes to shadow");
		test.check(changed.equals(List.of(10)), "varp synchronization reports changed ID");
		test.check(state.acceptServerValue(11, 3), "new authoritative varp reports change");
		test.equal(state.get(11), 3, "authoritative varp updates current value");
	}

	/**
	 * Verifies the extracted revision-377 bit-mask table.
	 * 
	 * @param test assertion sink
	 */
	private static void testBitMasks(SelfTestSupport test) {
		test.equal(BitMasks.get(0), 0x1, "bit mask index 0");
		test.equal(BitMasks.get(1), 0x3, "bit mask index 1");
		test.equal(BitMasks.get(7), 0xff, "bit mask index 7");
		test.equal(BitMasks.get(15), 0xffff, "bit mask index 15");
		test.equal(BitMasks.get(30), 0x7fffffff, "bit mask index 30");
		test.equal(BitMasks.get(31), -1, "bit mask index 31");
	}

	/**
	 * Verifies the original stable menu-priority partition and entry cohesion.
	 * 
	 * @param test assertion sink
	 */
	private static void testMenuPriority(SelfTestSupport test) {
		MenuState menu = new MenuState();
		int[] ids = { 500, 1500, 1000, 2001, 999 };
		for (int index = 0; index < ids.length; index++)
			menu.add(new MenuEntry("entry-" + index, ids[index], 10 + index, 20 + index, 30 + index));
		menu.prioritizeActions();

		int[] expectedOrder = { 1, 0, 2, 3, 4 };
		int[] expectedIds = { 1500, 500, 1000, 2001, 999 };
		for (int index = 0; index < expectedOrder.length; index++) {
			int original = expectedOrder[index];
			test.equal(menu.entry(index).action(), expectedIds[index], "menu priority ID " + index);
			test.equal(menu.entry(index).text(), "entry-" + original, "menu priority name " + index);
			test.equal(menu.entry(index).argument0(), 10 + original, "menu priority argument 0 " + index);
			test.equal(menu.entry(index).argument1(), 20 + original, "menu priority argument 1 " + index);
			test.equal(menu.entry(index).argument2(), 30 + original, "menu priority argument 2 " + index);
		}
		test.equal(MenuState.lowPriority(MenuState.ADD_FRIEND), 2762, "menu low-priority encoding");
		test.equal(MenuState.normalizeActionId(2762), MenuState.ADD_FRIEND, "menu low-priority normalization");
		test.equal(MenuState.normalizeActionId(MenuState.ADD_FRIEND), MenuState.ADD_FRIEND,
				"menu normal action normalization");

		int[] playerOptions = { MenuState.PLAYER_OPTION_1, MenuState.PLAYER_OPTION_2, MenuState.PLAYER_OPTION_3,
				MenuState.PLAYER_OPTION_4, MenuState.PLAYER_OPTION_5 };
		int[] inventoryOptions = { MenuState.INVENTORY_ITEM_OPTION_1, MenuState.INVENTORY_ITEM_OPTION_2,
				MenuState.INVENTORY_ITEM_OPTION_3, MenuState.INVENTORY_ITEM_OPTION_4,
				MenuState.INVENTORY_ITEM_OPTION_5 };
		int[] widgetOptions = { MenuState.WIDGET_ITEM_OPTION_1, MenuState.WIDGET_ITEM_OPTION_2,
				MenuState.WIDGET_ITEM_OPTION_3, MenuState.WIDGET_ITEM_OPTION_4, MenuState.WIDGET_ITEM_OPTION_5 };
		int[] npcOptions = { MenuState.NPC_OPTION_1, MenuState.NPC_OPTION_2, MenuState.NPC_OPTION_3,
				MenuState.NPC_OPTION_4, MenuState.NPC_OPTION_5 };
		int[] objectOptions = { MenuState.OBJECT_OPTION_1, MenuState.OBJECT_OPTION_2, MenuState.OBJECT_OPTION_3,
				MenuState.OBJECT_OPTION_4, MenuState.OBJECT_OPTION_5 };
		int[] groundOptions = { MenuState.GROUND_ITEM_OPTION_1, MenuState.GROUND_ITEM_OPTION_2,
				MenuState.GROUND_ITEM_OPTION_3, MenuState.GROUND_ITEM_OPTION_4, MenuState.GROUND_ITEM_OPTION_5 };
		for (int index = 0; index < 5; index++) {
			test.equal(MenuState.playerOptionAction(index), playerOptions[index],
					"player menu action mapping " + index);
			test.equal(MenuState.inventoryItemOptionAction(index), inventoryOptions[index],
					"inventory menu action mapping " + index);
			test.equal(MenuState.widgetItemOptionAction(index), widgetOptions[index],
					"widget menu action mapping " + index);
			test.equal(MenuState.npcOptionAction(index), npcOptions[index], "NPC menu action mapping " + index);
			test.equal(MenuState.objectOptionAction(index), objectOptions[index],
					"object menu action mapping " + index);
			test.equal(MenuState.groundItemOptionAction(index), groundOptions[index],
					"ground-item menu action mapping " + index);
		}

		MenuState resetMenu = new MenuState();
		resetMenu.add(new MenuEntry("friend", MenuState.lowPriority(MenuState.ADD_FRIEND), 41, 42, 43));
		test.check(resetMenu.isAddFriendAction(0), "menu entry low-priority friend action");
		resetMenu.reset();
		test.equal(resetMenu.count, 1, "menu reset count");
		test.equal(resetMenu.entry(0).text(), "Cancel", "menu reset text");
		test.equal(resetMenu.entry(0).action(), MenuState.CANCEL_ACTION, "menu reset action");
		test.equal(resetMenu.entry(0).argument0(), 0, "menu reset argument 0");
		test.equal(resetMenu.entry(0).argument1(), 0, "menu reset argument 1");
		test.equal(resetMenu.entry(0).argument2(), 0, "menu reset argument 2");
	}

	/**
	 * Verifies packed scene configuration and UID fields.
	 * 
	 * @param test assertion sink
	 */
	private static void testScenePacking(SelfTestSupport test) {
		int config = SceneConfig.pack(17, 3) & 0xff;
		test.equal(config, 0xd1, "scene configuration bytes");
		test.equal(SceneConfig.type(config), 17, "scene configuration type");
		test.equal(SceneConfig.orientation(config), 3, "scene configuration orientation");

		int x = 12;
		int y = 34;
		int entityId = 12_345;
		int entityType = SceneUid.TYPE_OBJECT;
		int uid = x | y << SceneUid.TILE_Y_SHIFT | entityId << SceneUid.ENTITY_ID_SHIFT
				| entityType << SceneUid.ENTITY_TYPE_SHIFT | SceneUid.NON_INTERACTIVE_FLAG;
		test.equal(uid & SceneUid.TILE_COORDINATE_MASK, x, "scene UID tile X");
		test.equal(uid >> SceneUid.TILE_Y_SHIFT & SceneUid.TILE_COORDINATE_MASK, y, "scene UID tile Y");
		test.equal(uid >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK, entityId, "scene UID entity ID");
		test.equal(uid >> SceneUid.ENTITY_TYPE_SHIFT & SceneUid.ENTITY_TYPE_MASK, entityType, "scene UID entity type");
		test.check((uid & SceneUid.NON_INTERACTIVE_FLAG) != 0, "scene UID non-interactive flag");
		test.equal(SceneConstants.SIZE, 104, "scene tile size");
		test.equal(SceneConstants.TILE_SIZE, 128, "fine-coordinate tile size");
		test.equal(SceneConstants.CHUNK_SIZE, 8, "scene chunk size");
		test.equal(SceneConstants.REGION_SIZE, 64, "cache region size");
	}

	/**
	 * Verifies rotated chunk and landscape geometry.
	 * 
	 * @param test assertion sink
	 */
	private static void testChunkGeometry(SelfTestSupport test) {
		int[] expectedX = { 2, 5, 5, 2 };
		int[] expectedY = { 5, 5, 2, 2 };
		for (int rotation = 0; rotation < 4; rotation++) {
			test.equal(TiledUtils.getRotatedMapChunkX(2, 5, rotation), expectedX[rotation],
					"tile rotation X " + rotation);
			test.equal(TiledUtils.getRotatedMapChunkY(2, 5, rotation), expectedY[rotation],
					"tile rotation Y " + rotation);
		}
		test.equal(TiledUtils.getRotatedLandscapeChunkX(2, 5, 2, 3, 1, 1), 5, "landscape rotation 1 X");
		test.equal(TiledUtils.getRotatedLandscapeChunkY(2, 5, 2, 3, 1, 1), 3, "landscape rotation 1 Y");
		test.equal(TiledUtils.getRotatedLandscapeChunkX(2, 5, 2, 3, 1, 2), 3, "landscape rotation 2 X");
		test.equal(TiledUtils.getRotatedLandscapeChunkY(2, 5, 2, 3, 1, 2), 1, "landscape rotation 2 Y");
	}
}
