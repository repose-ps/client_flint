package rs2.game.entity;

/**
 * Revision-377 player-appearance recolour palettes shared by player model
 * decoding and the character-design interface.
 */
public final class PlayerAppearancePalettes {

	/** Body recolour palettes indexed by appearance colour slot. */
	private static final int[][] BODY_COLORS = {
			{ 6798, 107, 10283, 16, 4797, 7744, 5799, 4634, 33697, 22433, 2983, 54193 },
			{ 8741, 12, 64030, 43162, 7735, 8404, 1701, 38430, 24094, 10153, 56621, 4783, 1341, 16578, 35003, 25239 },
			{ 25238, 8742, 12, 64030, 43162, 7735, 8404, 1701, 38430, 24094, 10153, 56621, 4783, 1341, 16578, 35003 },
			{ 4626, 11146, 6439, 12, 4758, 10270 }, { 4550, 4537, 5681, 5673, 5790, 6806, 8076, 4574 } };

	/** Skin recolour palette paired with appearance colour slot one. */
	private static final int[] SKIN_COLORS = { 9104, 10275, 7595, 3610, 7975, 8526, 918, 38802, 24466, 10145, 58654,
			5027, 1457, 16565, 34991, 25486 };

	/** Utility class. */
	private PlayerAppearancePalettes() {
	}

	/**
	 * Returns the number of selectable colours in one body-colour slot.
	 *
	 * @param slot appearance colour slot
	 * @return selectable colour count
	 */
	public static int bodyColorCount(int slot) {
		return BODY_COLORS[slot].length;
	}

	/**
	 * Returns one body recolour value.
	 *
	 * @param slot  appearance colour slot
	 * @param index palette index within the slot
	 * @return packed model colour
	 */
	public static int bodyColor(int slot, int index) {
		return BODY_COLORS[slot][index];
	}

	/**
	 * Returns one skin recolour value.
	 *
	 * @param index skin-palette index
	 * @return packed model colour
	 */
	public static int skinColor(int index) {
		return SKIN_COLORS[index];
	}
}
