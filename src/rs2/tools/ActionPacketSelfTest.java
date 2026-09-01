package rs2.tools;

import java.util.Arrays;
import java.util.function.Consumer;

import rs2.action.ActionPacketEncoder;
import rs2.net.NetworkSession;

/**
 * Known-byte regression vectors for revision-377 menu-action packet encoding.
 *
 * <p>Each vector starts from the same zero-seed ISAAC state so the encrypted
 * opcode byte as well as every transformed payload field is frozen.</p>
 */
public final class ActionPacketSelfTest {
    /** Zero seed used by the permanent action-packet vectors. */
    private static final int[] ZERO_SEED = { 0, 0, 0, 0 };

    /** Prevents instantiation. */
    private ActionPacketSelfTest() {
    }

    /**
     * Runs the known-byte action packet checks as a standalone tool.
     *
     * @param args no arguments are accepted
     */
    public static void main(String[] args) {
        if (args.length != 0) {
            throw new IllegalArgumentException("Usage: ActionPacketSelfTest");
        }
        System.out.println("ActionPacketSelfTest: PASS (" + run() + " checks)");
    }

    /**
     * Runs the action packet vectors.
     *
     * @return completed assertion count
     */
    static int run() {
        SelfTestSupport test = new SelfTestSupport();
        vector(test, "player option1", packets -> packets.playerOption1(0x1234), "e8b412");
        vector(test, "player option2", packets -> packets.playerOption2(0x1234), "dc12b4");
        vector(test, "player option3", packets -> packets.playerOption3(0x1234), "b53412");
        vector(test, "player option4", packets -> packets.playerOption4(0x1234), "673412");
        vector(test, "player option5", packets -> packets.playerOption5(0x1234), "2012b4");
        vector(test, "cast spell on player", packets -> packets.castSpellOnPlayer(0x1234, 0x2345), "1212344523");
        vector(test, "use item on player", packets -> packets.useItemOnPlayer(0x1234, 0x2345, 0x3456, 0x4567), "824523d634456712b4");
        vector(test, "npc option1", packets -> packets.npcOption1(0x1234), "633412");
        vector(test, "npc option2", packets -> packets.npcOption2(0x1234), "3612b4");
        vector(test, "npc option3", packets -> packets.npcOption3(0x1234), "00b412");
        vector(test, "npc option4", packets -> packets.npcOption4(0x1234), "1d3412");
        vector(test, "npc option5", packets -> packets.npcOption5(0x1234), "fb3412");
        vector(test, "use item on npc", packets -> packets.useItemOnNpc(0x1234, 0x2345, 0x3456, 0x4567), "2c12344523d6344567");
        vector(test, "cast spell on npc", packets -> packets.castSpellOnNpc(0x1234, 0x2345), "5b23c53412");
        vector(test, "npc option3 anti cheat", packets -> packets.npcOption3AntiCheat(), "9000000000");
        vector(test, "use item on object", packets -> packets.useItemOnObject(0x1234, 0x2345, 0x3456, 0x4567, 0x5678, 0x6789), "8b341245235634674556780967");
        vector(test, "cast spell on object", packets -> packets.castSpellOnObject(0x1234, 0x2345, 0x3456, 0x4567), "c51234452334d66745");
        vector(test, "object option1", packets -> packets.objectOption1(0x1234, 0x2345, 0x3456), "a823c556343412");
        vector(test, "object option2", packets -> packets.objectOption2(0x1234, 0x2345, 0x3456), "e41234234534d6");
        vector(test, "object option3", packets -> packets.objectOption3(0x1234, 0x2345, 0x3456), "2534d63412c523");
        vector(test, "object option4", packets -> packets.objectOption4(0x1234, 0x2345, 0x3456), "7b234556341234");
        vector(test, "object option5", packets -> packets.objectOption5(0x1234, 0x2345, 0x3456), "2a341256342345");
        vector(test, "ground item option1", packets -> packets.groundItemOption1(0x1234, 0x2345, 0x3456), "4023c53456b412");
        vector(test, "ground item option2", packets -> packets.groundItemOption2(0x1234, 0x2345, 0x3456), "57234534d6b412");
        vector(test, "ground item option3", packets -> packets.groundItemOption3(0x1234, 0x2345, 0x3456), "3ab412c52334d6");
        vector(test, "ground item option4", packets -> packets.groundItemOption4(0x1234, 0x2345, 0x3456), "2912b456342345");
        vector(test, "ground item option5", packets -> packets.groundItemOption5(0x1234, 0x2345, 0x3456), "d9341223c53456");
        vector(test, "use item on ground item", packets -> packets.useItemOnGroundItem(0x1234, 0x2345, 0x3456, 0x4567, 0x5678, 0x6789), "c6e74556f8d634c52389673412");
        vector(test, "cast spell on ground item", packets -> packets.castSpellOnGroundItem(0x1234, 0x2345, 0x3456, 0x4567), "46341234566745c523");
        vector(test, "ground item option3 anti cheat", packets -> packets.groundItemOption3AntiCheat(), "d1abc842");
        vector(test, "ground item option2 anti cheat", packets -> packets.groundItemOption2AntiCheat(), "5200000000");
        vector(test, "inventory item option1", packets -> packets.inventoryItemOption1(0x1234, 0x2345, 0x3456), "be34d645233412");
        vector(test, "inventory item option2", packets -> packets.inventoryItemOption2(0x1234, 0x2345, 0x3456), "0b5634341223c5");
        vector(test, "inventory item option3", packets -> packets.inventoryItemOption3(0x1234, 0x2345, 0x3456), "94c523b4125634");
        vector(test, "inventory item option4", packets -> packets.inventoryItemOption4(0x1234, 0x2345, 0x3456), "d7452312b43456");
        vector(test, "inventory item option5", packets -> packets.inventoryItemOption5(0x1234, 0x2345, 0x3456), "f74523b412d634");
        vector(test, "widget item option1", packets -> packets.widgetItemOption1(0x1234, 0x2345, 0x3456), "f612b434562345");
        vector(test, "widget item option2", packets -> packets.widgetItemOption2(0x1234, 0x2345, 0x3456), "a423c534125634");
        vector(test, "widget item option3", packets -> packets.widgetItemOption3(0x1234, 0x2345, 0x3456), "4e3412c5233456");
        vector(test, "widget item option4", packets -> packets.widgetItemOption4(0x1234, 0x2345, 0x3456), "dad63445231234");
        vector(test, "widget item option5", packets -> packets.widgetItemOption5(0x1234, 0x2345, 0x3456), "91c523b4125634");
        vector(test, "use item on inventory item", packets -> packets.useItemOnInventoryItem(0x1234, 0x2345, 0x3456, 0x4567, 0x5678, 0x6789), "f4123445235634e74556f86709");
        vector(test, "cast spell on inventory item", packets -> packets.castSpellOnInventoryItem(0x1234, 0x2345, 0x3456, 0x4567), "17123423c534d645e7");
        vector(test, "inventory item option4 anti cheat", packets -> packets.inventoryItemOption4AntiCheat(), "98ce");
        vector(test, "inventory item option1 anti cheat", packets -> packets.inventoryItemOption1AntiCheat(), "717d");
        vector(test, "widget click", packets -> packets.widgetClick(0x1234), "421234");
        vector(test, "widget continue", packets -> packets.widgetContinue(0x1234), "d51234");
        vector(test, "report abuse", packets -> packets.reportAbuse(0x0102030405060708L, 0x2345, true), "ab01020304050607084501");
        vector(test, "accept trade", packets -> packets.acceptTrade(0x1234), "673412");
        vector(test, "accept challenge", packets -> packets.acceptChallenge(0x1234), "e8b412");
        return test.checks();
    }

    /**
     * Applies one encoder call from a fresh ISAAC state and compares exact bytes.
     *
     * @param test assertion sink
     * @param name vector name
     * @param call encoder call
     * @param expectedHex expected encrypted opcode and payload bytes
     */
    private static void vector(SelfTestSupport test, String name, Consumer<ActionPacketEncoder> call,
            String expectedHex) {
        NetworkSession network = new NetworkSession();
        network.initializeOpcodeCiphers(ZERO_SEED.clone());
        call.accept(new ActionPacketEncoder(network.outgoing));
        byte[] actual = Arrays.copyOf(network.outgoing.payload, network.outgoing.position);
        test.bytes(actual, decodeHex(expectedHex), name + " known bytes");
    }

    /**
     * Decodes an even-length lowercase/uppercase hexadecimal string.
     *
     * @param hex hexadecimal bytes
     * @return decoded bytes
     */
    private static byte[] decodeHex(String hex) {
        if ((hex.length() & 1) != 0) {
            throw new IllegalArgumentException("Odd-length hexadecimal vector");
        }
        byte[] bytes = new byte[hex.length() / 2];
        for (int index = 0; index < bytes.length; index++) {
            int high = Character.digit(hex.charAt(index * 2), 16);
            int low = Character.digit(hex.charAt(index * 2 + 1), 16);
            if (high < 0 || low < 0) {
                throw new IllegalArgumentException("Invalid hexadecimal vector: " + hex);
            }
            bytes[index] = (byte) ((high << 4) | low);
        }
        return bytes;
    }
}
