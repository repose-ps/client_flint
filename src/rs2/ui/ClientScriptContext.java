package rs2.ui;

import java.util.function.BooleanSupplier;
import java.util.function.IntSupplier;
import java.util.function.IntUnaryOperator;

/**
 * Read-only adapter exposing selected client state to revision-377 widget CS1
 * scripts without making {@link WidgetRuntime} depend on the top-level client.
 */
public final class ClientScriptContext implements WidgetRuntime.ScriptContext {

	/** Reads the current boosted/drained level for a skill. */
	private final IntUnaryOperator currentSkillLevel;
	/** Reads the base level for a skill. */
	private final IntUnaryOperator baseSkillLevel;
	/** Reads accumulated experience for a skill. */
	private final IntUnaryOperator skillExperience;
	/** Reads one current client varp value. */
	private final IntUnaryOperator varp;
	/** Reads the experience-table threshold for a level index. */
	private final IntUnaryOperator experienceForLevel;
	/** Reads a precomputed low-bit mask by width. */
	private final IntUnaryOperator bitMask;
	/** Reads current run energy. */
	private final IntSupplier runEnergy;
	/** Reads current carried weight. */
	private final IntSupplier weight;
	/** Reads the local player combat level. */
	private final IntSupplier combatLevel;
	/** Reads the local player's absolute world X tile. */
	private final IntSupplier playerWorldX;
	/** Reads the local player's absolute world Y tile. */
	private final IntSupplier playerWorldY;
	/** Reports whether the client is operating in members-world mode. */
	private final BooleanSupplier membersWorld;

	/**
	 * Creates a script context from narrow state readers.
	 *
	 * @param currentSkillLevel  current skill-level reader
	 * @param baseSkillLevel     base skill-level reader
	 * @param skillExperience    skill-experience reader
	 * @param varp               varp reader
	 * @param experienceForLevel experience-table reader
	 * @param bitMask            bit-mask reader
	 * @param runEnergy          run-energy reader
	 * @param weight             carried-weight reader
	 * @param combatLevel        combat-level reader
	 * @param playerWorldX       absolute player X-tile reader
	 * @param playerWorldY       absolute player Y-tile reader
	 * @param membersWorld       members-world reader
	 */
	public ClientScriptContext(IntUnaryOperator currentSkillLevel, IntUnaryOperator baseSkillLevel,
			IntUnaryOperator skillExperience, IntUnaryOperator varp, IntUnaryOperator experienceForLevel,
			IntUnaryOperator bitMask, IntSupplier runEnergy, IntSupplier weight, IntSupplier combatLevel,
			IntSupplier playerWorldX, IntSupplier playerWorldY, BooleanSupplier membersWorld) {
		this.currentSkillLevel = currentSkillLevel;
		this.baseSkillLevel = baseSkillLevel;
		this.skillExperience = skillExperience;
		this.varp = varp;
		this.experienceForLevel = experienceForLevel;
		this.bitMask = bitMask;
		this.runEnergy = runEnergy;
		this.weight = weight;
		this.combatLevel = combatLevel;
		this.playerWorldX = playerWorldX;
		this.playerWorldY = playerWorldY;
		this.membersWorld = membersWorld;
	}

	@Override
	public int currentSkillLevel(int skill) {
		return currentSkillLevel.applyAsInt(skill);
	}

	@Override
	public int baseSkillLevel(int skill) {
		return baseSkillLevel.applyAsInt(skill);
	}

	@Override
	public int skillExperience(int skill) {
		return skillExperience.applyAsInt(skill);
	}

	@Override
	public int varp(int id) {
		return varp.applyAsInt(id);
	}

	@Override
	public int experienceForLevel(int levelIndex) {
		return experienceForLevel.applyAsInt(levelIndex);
	}

	@Override
	public int bitMask(int width) {
		return bitMask.applyAsInt(width);
	}

	@Override
	public int runEnergy() {
		return runEnergy.getAsInt();
	}

	@Override
	public int weight() {
		return weight.getAsInt();
	}

	@Override
	public int combatLevel() {
		return combatLevel.getAsInt();
	}

	@Override
	public int playerWorldX() {
		return playerWorldX.getAsInt();
	}

	@Override
	public int playerWorldY() {
		return playerWorldY.getAsInt();
	}

	@Override
	public boolean membersWorld() {
		return membersWorld.getAsBoolean();
	}
}
