package rs2.action;

import rs2.net.Buffer;
import rs2.net.OutgoingPacketOpcode;

/**
 * Encodes revision-377 outgoing packets produced by menu actions.
 *
 * <p>The action-domain handlers decide whether an action is valid and apply
 * movement/UI/application effects. This encoder owns only the exact opcode,
 * field ordering, endian choice, and Add/Neg/Sub transforms for the wire
 * protocol.</p>
 */
public final class ActionPacketEncoder {
    /** Shared outgoing game buffer. */
    private final Buffer outgoing;

    /**
     * Creates an action packet encoder.
     *
     * @param outgoing outgoing revision-377 game buffer
     */
    public ActionPacketEncoder(Buffer outgoing) {
        this.outgoing = outgoing;
    }

    /**
     * Writes player option 1.
     *
     * @param playerIndex target player index
     */
    public void playerOption1(int playerIndex) {
        outgoing.writeOpcode(OutgoingPacketOpcode.PLAYER_OPTION_1);
        outgoing.writeShortLEAdd(playerIndex);
    }

    /**
     * Writes player option 2.
     *
     * @param playerIndex target player index
     */
    public void playerOption2(int playerIndex) {
        outgoing.writeOpcode(OutgoingPacketOpcode.PLAYER_OPTION_2);
        outgoing.writeShortAdd(playerIndex);
    }

    /**
     * Writes player option 3.
     *
     * @param playerIndex target player index
     */
    public void playerOption3(int playerIndex) {
        outgoing.writeOpcode(OutgoingPacketOpcode.PLAYER_OPTION_3);
        outgoing.writeShortLE(playerIndex);
    }

    /**
     * Writes player option 4.
     *
     * @param playerIndex target player index
     */
    public void playerOption4(int playerIndex) {
        outgoing.writeOpcode(OutgoingPacketOpcode.PLAYER_OPTION_4);
        outgoing.writeShortLE(playerIndex);
    }

    /**
     * Writes player option 5.
     *
     * @param playerIndex target player index
     */
    public void playerOption5(int playerIndex) {
        outgoing.writeOpcode(OutgoingPacketOpcode.PLAYER_OPTION_5);
        outgoing.writeShortAdd(playerIndex);
    }

    /**
     * Writes spell-on-player.
     *
     * @param playerIndex target player index
     * @param spellWidgetId selected spell widget
     */
    public void castSpellOnPlayer(int playerIndex, int spellWidgetId) {
        outgoing.writeOpcode(OutgoingPacketOpcode.CAST_SPELL_ON_PLAYER);
        outgoing.writeShort(playerIndex);
        outgoing.writeShortLE(spellWidgetId);
    }

    /**
     * Writes item-on-player.
     * @param playerIndex target player index
     * @param itemId selected item ID
     * @param itemSlot selected item slot
     * @param itemWidgetId selected item widget
     */
    public void useItemOnPlayer(int playerIndex, int itemId, int itemSlot, int itemWidgetId) {
        outgoing.writeOpcode(OutgoingPacketOpcode.USE_ITEM_ON_PLAYER);
        outgoing.writeShortLE(itemId);
        outgoing.writeShortLEAdd(itemSlot);
        outgoing.writeShort(itemWidgetId);
        outgoing.writeShortAdd(playerIndex);
    }

    /**
     * Writes NPC option 1.
     *
     * @param npcIndex target NPC index
     */
    public void npcOption1(int npcIndex) {
        outgoing.writeOpcode(OutgoingPacketOpcode.NPC_OPTION_1);
        outgoing.writeShortLE(npcIndex);
    }

    /**
     * Writes NPC option 2.
     *
     * @param npcIndex target NPC index
     */
    public void npcOption2(int npcIndex) {
        outgoing.writeOpcode(OutgoingPacketOpcode.NPC_OPTION_2);
        outgoing.writeShortAdd(npcIndex);
    }

    /**
     * Writes NPC option 3.
     *
     * @param npcIndex target NPC index
     */
    public void npcOption3(int npcIndex) {
        outgoing.writeOpcode(OutgoingPacketOpcode.NPC_OPTION_3);
        outgoing.writeShortLEAdd(npcIndex);
    }

    /**
     * Writes NPC option 4.
     *
     * @param npcIndex target NPC index
     */
    public void npcOption4(int npcIndex) {
        outgoing.writeOpcode(OutgoingPacketOpcode.NPC_OPTION_4);
        outgoing.writeShortLE(npcIndex);
    }

    /**
     * Writes NPC option 5.
     *
     * @param npcIndex target NPC index
     */
    public void npcOption5(int npcIndex) {
        outgoing.writeOpcode(OutgoingPacketOpcode.NPC_OPTION_5);
        outgoing.writeShortLE(npcIndex);
    }

    /**
     * Writes item-on-NPC.
     * @param npcIndex target NPC index
     * @param itemId selected item ID
     * @param itemWidgetId selected item widget
     * @param itemSlot selected item slot
     */
    public void useItemOnNpc(int npcIndex, int itemId, int itemWidgetId, int itemSlot) {
        outgoing.writeOpcode(OutgoingPacketOpcode.USE_ITEM_ON_NPC);
        outgoing.writeShort(npcIndex);
        outgoing.writeShortLE(itemId);
        outgoing.writeShortLEAdd(itemWidgetId);
        outgoing.writeShort(itemSlot);
    }

    /**
     * Writes spell-on-NPC.
     *
     * @param npcIndex target NPC index
     * @param spellWidgetId selected spell widget
     */
    public void castSpellOnNpc(int npcIndex, int spellWidgetId) {
        outgoing.writeOpcode(OutgoingPacketOpcode.CAST_SPELL_ON_NPC);
        outgoing.writeShortAdd(spellWidgetId);
        outgoing.writeShortLE(npcIndex);
    }

    /** Writes the legacy NPC-option-3 anti-cheat packet. */
    public void npcOption3AntiCheat() {
        outgoing.writeOpcode(OutgoingPacketOpcode.ANTI_CHEAT_NPC_OPTION_3);
        outgoing.writeInt(0);
    }

    /**
     * Writes item-on-object.
     * @param objectId object definition ID
     * @param itemWidgetId selected item widget
     * @param itemId selected item ID
     * @param worldY absolute scene Y
     * @param itemSlot selected item slot
     * @param worldX absolute scene X
     */
    public void useItemOnObject(int objectId, int itemWidgetId, int itemId, int worldY, int itemSlot, int worldX) {
        outgoing.writeOpcode(OutgoingPacketOpcode.USE_ITEM_ON_OBJECT);
        outgoing.writeShortLE(objectId);
        outgoing.writeShortLE(itemWidgetId);
        outgoing.writeShortLE(itemId);
        outgoing.writeShortLE(worldY);
        outgoing.writeShort(itemSlot);
        outgoing.writeShortLEAdd(worldX);
    }

    /**
     * Writes spell-on-object.
     *
     * @param spellWidgetId selected spell widget
     * @param objectId object ID
     * @param worldX absolute X
     * @param worldY absolute Y
     */
    public void castSpellOnObject(int spellWidgetId, int objectId, int worldX, int worldY) {
        outgoing.writeOpcode(OutgoingPacketOpcode.CAST_SPELL_ON_OBJECT);
        outgoing.writeShort(spellWidgetId);
        outgoing.writeShortLE(objectId);
        outgoing.writeShortAdd(worldX);
        outgoing.writeShortLE(worldY);
    }

    /**
     * Writes object option 1.
     *
     * @param objectId object ID
     * @param worldX absolute X
     * @param worldY absolute Y
     */
    public void objectOption1(int objectId, int worldX, int worldY) {
        outgoing.writeOpcode(OutgoingPacketOpcode.OBJECT_OPTION_1);
        outgoing.writeShortAdd(worldX);
        outgoing.writeShortLE(worldY);
        outgoing.writeShortLE(objectId);
    }

    /**
     * Writes object option 2.
     *
     * @param objectId object ID
     * @param worldX absolute X
     * @param worldY absolute Y
     */
    public void objectOption2(int objectId, int worldX, int worldY) {
        outgoing.writeOpcode(OutgoingPacketOpcode.OBJECT_OPTION_2);
        outgoing.writeShort(objectId);
        outgoing.writeShort(worldX);
        outgoing.writeShortAdd(worldY);
    }

    /**
     * Writes object option 3.
     *
     * @param objectId object ID
     * @param worldX absolute X
     * @param worldY absolute Y
     */
    public void objectOption3(int objectId, int worldX, int worldY) {
        outgoing.writeOpcode(OutgoingPacketOpcode.OBJECT_OPTION_3);
        outgoing.writeShortAdd(worldY);
        outgoing.writeShortLE(objectId);
        outgoing.writeShortLEAdd(worldX);
    }

    /**
     * Writes object option 4.
     *
     * @param objectId object ID
     * @param worldX absolute X
     * @param worldY absolute Y
     */
    public void objectOption4(int objectId, int worldX, int worldY) {
        outgoing.writeOpcode(OutgoingPacketOpcode.OBJECT_OPTION_4);
        outgoing.writeShort(worldX);
        outgoing.writeShortLE(worldY);
        outgoing.writeShort(objectId);
    }

    /**
     * Writes object option 5.
     *
     * @param objectId object ID
     * @param worldX absolute X
     * @param worldY absolute Y
     */
    public void objectOption5(int objectId, int worldX, int worldY) {
        outgoing.writeOpcode(OutgoingPacketOpcode.OBJECT_OPTION_5);
        outgoing.writeShortLE(objectId);
        outgoing.writeShortLE(worldY);
        outgoing.writeShort(worldX);
    }

    /**
     * Writes ground-item option 1.
     *
     * @param itemId item ID
     * @param worldX absolute X
     * @param worldY absolute Y
     */
    public void groundItemOption1(int itemId, int worldX, int worldY) {
        outgoing.writeOpcode(OutgoingPacketOpcode.GROUND_ITEM_OPTION_1);
        outgoing.writeShortAdd(worldX);
        outgoing.writeShort(worldY);
        outgoing.writeShortLEAdd(itemId);
    }

    /**
     * Writes ground-item option 2.
     *
     * @param itemId item ID
     * @param worldX absolute X
     * @param worldY absolute Y
     */
    public void groundItemOption2(int itemId, int worldX, int worldY) {
        outgoing.writeOpcode(OutgoingPacketOpcode.GROUND_ITEM_OPTION_2);
        outgoing.writeShort(worldX);
        outgoing.writeShortAdd(worldY);
        outgoing.writeShortLEAdd(itemId);
    }

    /**
     * Writes ground-item option 3.
     *
     * @param itemId item ID
     * @param worldX absolute X
     * @param worldY absolute Y
     */
    public void groundItemOption3(int itemId, int worldX, int worldY) {
        outgoing.writeOpcode(OutgoingPacketOpcode.GROUND_ITEM_OPTION_3);
        outgoing.writeShortLEAdd(itemId);
        outgoing.writeShortLEAdd(worldX);
        outgoing.writeShortAdd(worldY);
    }

    /**
     * Writes ground-item option 4.
     *
     * @param itemId item ID
     * @param worldX absolute X
     * @param worldY absolute Y
     */
    public void groundItemOption4(int itemId, int worldX, int worldY) {
        outgoing.writeOpcode(OutgoingPacketOpcode.GROUND_ITEM_OPTION_4);
        outgoing.writeShortAdd(itemId);
        outgoing.writeShortLE(worldY);
        outgoing.writeShort(worldX);
    }

    /**
     * Writes ground-item option 5.
     *
     * @param itemId item ID
     * @param worldX absolute X
     * @param worldY absolute Y
     */
    public void groundItemOption5(int itemId, int worldX, int worldY) {
        outgoing.writeOpcode(OutgoingPacketOpcode.GROUND_ITEM_OPTION_5);
        outgoing.writeShortLE(itemId);
        outgoing.writeShortAdd(worldX);
        outgoing.writeShort(worldY);
    }

    /**
     * Writes item-on-ground-item.
     * @param itemId ground item ID
     * @param worldX absolute X
     * @param worldY absolute Y
     * @param selectedSlot selected item slot
     * @param selectedItemId selected item ID
     * @param selectedWidgetId selected item widget
     */
    public void useItemOnGroundItem(int itemId, int worldX, int worldY, int selectedSlot,
            int selectedItemId, int selectedWidgetId) {
        outgoing.writeOpcode(OutgoingPacketOpcode.USE_ITEM_ON_GROUND_ITEM);
        outgoing.writeShortLEAdd(selectedSlot);
        outgoing.writeShortAdd(selectedItemId);
        outgoing.writeShortLEAdd(worldY);
        outgoing.writeShortLEAdd(worldX);
        outgoing.writeShortLE(selectedWidgetId);
        outgoing.writeShortLE(itemId);
    }

    /**
     * Writes spell-on-ground-item.
     *
     * @param itemId item ID
     * @param worldX absolute X
     * @param worldY absolute Y
     * @param spellWidgetId selected spell widget
     */
    public void castSpellOnGroundItem(int itemId, int worldX, int worldY, int spellWidgetId) {
        outgoing.writeOpcode(OutgoingPacketOpcode.CAST_SPELL_ON_GROUND_ITEM);
        outgoing.writeShortLE(itemId);
        outgoing.writeShort(worldY);
        outgoing.writeShortLE(spellWidgetId);
        outgoing.writeShortLEAdd(worldX);
    }

    /** Writes the legacy ground-item-option-3 anti-cheat packet. */
    public void groundItemOption3AntiCheat() {
        outgoing.writeOpcode(OutgoingPacketOpcode.ANTI_CHEAT_GROUND_ITEM_OPTION_3);
        outgoing.writeMedium(0xabc842);
    }

    /** Writes the legacy ground-item-option-2 anti-cheat packet. */
    public void groundItemOption2AntiCheat() {
        outgoing.writeOpcode(OutgoingPacketOpcode.ANTI_CHEAT_GROUND_ITEM_OPTION_2);
        outgoing.writeInt(0);
    }

    /**
     * Writes inventory option 1.
     *
     * @param itemId item ID
     * @param slot slot
     * @param widgetId widget ID
     */
    public void inventoryItemOption1(int itemId, int slot, int widgetId) {
        outgoing.writeOpcode(OutgoingPacketOpcode.INVENTORY_ITEM_OPTION_1);
        outgoing.writeShortAdd(widgetId);
        outgoing.writeShortLE(slot);
        outgoing.writeShortLE(itemId);
    }

    /**
     * Writes inventory option 2.
     *
     * @param itemId item ID
     * @param slot slot
     * @param widgetId widget ID
     */
    public void inventoryItemOption2(int itemId, int slot, int widgetId) {
        outgoing.writeOpcode(OutgoingPacketOpcode.INVENTORY_ITEM_OPTION_2);
        outgoing.writeShortLE(widgetId);
        outgoing.writeShortLE(itemId);
        outgoing.writeShortAdd(slot);
    }

    /**
     * Writes inventory option 3.
     *
     * @param itemId item ID
     * @param slot slot
     * @param widgetId widget ID
     */
    public void inventoryItemOption3(int itemId, int slot, int widgetId) {
        outgoing.writeOpcode(OutgoingPacketOpcode.INVENTORY_ITEM_OPTION_3);
        outgoing.writeShortLEAdd(slot);
        outgoing.writeShortLEAdd(itemId);
        outgoing.writeShortLE(widgetId);
    }

    /**
     * Writes inventory option 4.
     *
     * @param itemId item ID
     * @param slot slot
     * @param widgetId widget ID
     */
    public void inventoryItemOption4(int itemId, int slot, int widgetId) {
        outgoing.writeOpcode(OutgoingPacketOpcode.INVENTORY_ITEM_OPTION_4);
        outgoing.writeShortLE(slot);
        outgoing.writeShortAdd(itemId);
        outgoing.writeShort(widgetId);
    }

    /**
     * Writes inventory option 5.
     *
     * @param itemId item ID
     * @param slot slot
     * @param widgetId widget ID
     */
    public void inventoryItemOption5(int itemId, int slot, int widgetId) {
        outgoing.writeOpcode(OutgoingPacketOpcode.INVENTORY_ITEM_OPTION_5);
        outgoing.writeShortLE(slot);
        outgoing.writeShortLEAdd(itemId);
        outgoing.writeShortLEAdd(widgetId);
    }

    /**
     * Writes widget-item option 1.
     *
     * @param itemId item ID
     * @param slot slot
     * @param widgetId widget ID
     */
    public void widgetItemOption1(int itemId, int slot, int widgetId) {
        outgoing.writeOpcode(OutgoingPacketOpcode.WIDGET_ITEM_OPTION_1);
        outgoing.writeShortAdd(itemId);
        outgoing.writeShort(widgetId);
        outgoing.writeShort(slot);
    }

    /**
     * Writes widget-item option 2.
     *
     * @param itemId item ID
     * @param slot slot
     * @param widgetId widget ID
     */
    public void widgetItemOption2(int itemId, int slot, int widgetId) {
        outgoing.writeOpcode(OutgoingPacketOpcode.WIDGET_ITEM_OPTION_2);
        outgoing.writeShortAdd(slot);
        outgoing.writeShortLE(itemId);
        outgoing.writeShortLE(widgetId);
    }

    /**
     * Writes widget-item option 3.
     *
     * @param itemId item ID
     * @param slot slot
     * @param widgetId widget ID
     */
    public void widgetItemOption3(int itemId, int slot, int widgetId) {
        outgoing.writeOpcode(OutgoingPacketOpcode.WIDGET_ITEM_OPTION_3);
        outgoing.writeShortLE(itemId);
        outgoing.writeShortLEAdd(slot);
        outgoing.writeShort(widgetId);
    }

    /**
     * Writes widget-item option 4.
     *
     * @param itemId item ID
     * @param slot slot
     * @param widgetId widget ID
     */
    public void widgetItemOption4(int itemId, int slot, int widgetId) {
        outgoing.writeOpcode(OutgoingPacketOpcode.WIDGET_ITEM_OPTION_4);
        outgoing.writeShortLEAdd(widgetId);
        outgoing.writeShortLE(slot);
        outgoing.writeShort(itemId);
    }

    /**
     * Writes widget-item option 5.
     *
     * @param itemId item ID
     * @param slot slot
     * @param widgetId widget ID
     */
    public void widgetItemOption5(int itemId, int slot, int widgetId) {
        outgoing.writeOpcode(OutgoingPacketOpcode.WIDGET_ITEM_OPTION_5);
        outgoing.writeShortLEAdd(slot);
        outgoing.writeShortLEAdd(itemId);
        outgoing.writeShortLE(widgetId);
    }

    /**
     * Writes item-on-inventory-item.
     * @param targetItemId target item ID
     * @param selectedSlot selected item slot
     * @param selectedItemId selected item ID
     * @param selectedWidgetId selected item widget
     * @param targetSlot target slot
     * @param targetWidgetId target widget
     */
    public void useItemOnInventoryItem(int targetItemId, int selectedSlot, int selectedItemId,
            int selectedWidgetId, int targetSlot, int targetWidgetId) {
        outgoing.writeOpcode(OutgoingPacketOpcode.USE_ITEM_ON_INVENTORY_ITEM);
        outgoing.writeShort(targetItemId);
        outgoing.writeShortLE(selectedSlot);
        outgoing.writeShortLE(selectedItemId);
        outgoing.writeShortLEAdd(selectedWidgetId);
        outgoing.writeShortAdd(targetSlot);
        outgoing.writeShortAdd(targetWidgetId);
    }

    /**
     * Writes spell-on-inventory-item.
     * @param spellWidgetId selected spell widget
     * @param targetWidgetId target widget
     * @param targetSlot target slot
     * @param targetItemId target item ID
     */
    public void castSpellOnInventoryItem(int spellWidgetId, int targetWidgetId, int targetSlot, int targetItemId) {
        outgoing.writeOpcode(OutgoingPacketOpcode.CAST_SPELL_ON_INVENTORY_ITEM);
        outgoing.writeShort(spellWidgetId);
        outgoing.writeShortAdd(targetWidgetId);
        outgoing.writeShortAdd(targetSlot);
        outgoing.writeShortAdd(targetItemId);
    }

    /** Writes the legacy inventory-option-4 anti-cheat packet. */
    public void inventoryItemOption4AntiCheat() {
        outgoing.writeOpcode(OutgoingPacketOpcode.ANTI_CHEAT_INVENTORY_ITEM_OPTION_4);
        outgoing.writeByte(206);
    }

    /** Writes the legacy inventory-option-1 anti-cheat packet. */
    public void inventoryItemOption1AntiCheat() {
        outgoing.writeOpcode(OutgoingPacketOpcode.ANTI_CHEAT_INVENTORY_ITEM_OPTION_1);
        outgoing.writeByte(125);
    }

    /**
     * Writes a generic widget click.
     *
     * @param widgetId widget ID
     */
    public void widgetClick(int widgetId) {
        outgoing.writeOpcode(OutgoingPacketOpcode.WIDGET_CLICK);
        outgoing.writeShort(widgetId);
    }

    /**
     * Writes the one-shot widget-continue acknowledgement.
     *
     * @param widgetId widget ID
     */
    public void widgetContinue(int widgetId) {
        outgoing.writeOpcode(OutgoingPacketOpcode.WIDGET_CONTINUE);
        outgoing.writeShort(widgetId);
    }

    /**
     * Writes a report-abuse submission.
     *
     * @param encodedName Base-37 encoded player name
     * @param rule zero-based rule index
     * @param mute whether the moderator mute flag is selected
     */
    public void reportAbuse(long encodedName, int rule, boolean mute) {
        outgoing.writeOpcode(OutgoingPacketOpcode.REPORT_ABUSE);
        outgoing.writeLong(encodedName);
        outgoing.writeByte(rule);
        outgoing.writeByte(mute ? 1 : 0);
    }

    /**
     * Writes the trade-accept player interaction packet.
     *
     * @param playerIndex target player index
     */
    public void acceptTrade(int playerIndex) {
        outgoing.writeOpcode(OutgoingPacketOpcode.PLAYER_OPTION_4);
        outgoing.writeShortLE(playerIndex);
    }

    /**
     * Writes the challenge-accept player interaction packet.
     *
     * @param playerIndex target player index
     */
    public void acceptChallenge(int playerIndex) {
        outgoing.writeOpcode(OutgoingPacketOpcode.PLAYER_OPTION_1);
        outgoing.writeShortLEAdd(playerIndex);
    }
}
