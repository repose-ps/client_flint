package rs2.game;

/**
 * Metadata for the skill slots used by the revision 377 client protocol and
 * interface scripts.
 *
 * <p>
 * The client reserves 25 slots even though only the first 21 are real skills in
 * this revision. The remaining names are historical placeholders; retaining
 * them is important because skill indices are part of the wire and
 * interface-script formats.
 * </p>
 */
public final class Skills {
	/** Number of skill slots allocated by the client. */
	public static final int COUNT = 25;

	/** Display/debug names indexed by protocol skill id. */
	public static String NAMES[] = { "attack", "defence", "strength", "hitpoints", "ranged", "prayer", "magic",
			"cooking", "woodcutting", "fletching", "fishing", "firemaking", "crafting", "smithing", "mining",
			"herblore", "agility", "thieving", "slayer", "farming", "runecraft", "yodelling", "hexediting", "-unused-",
			"-unused-" };

	/**
	 * Whether a slot contributes to the total-level interface expression. Only the
	 * 21 skills available in revision 377 are enabled.
	 */
	public static final boolean[] ENABLED = { true, true, true, true, true, true, true, true, true, true, true, true,
			true, true, true, true, true, true, true, true, true, false, false, false, false };

}
