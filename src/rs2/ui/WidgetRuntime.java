package rs2.ui;

import rs2.cache.cfg.Varbit;
import rs2.cache.def.AnimationSequence;
import rs2.cache.def.ItemDefinition;
import rs2.game.Skills;
import rs2.media.Angle;

/**
 * Runtime behavior shared by revision-377 interface widgets: CS1 expression
 * evaluation, CS1 comparisons, model animation updates, and animation reset.
 */
public final class WidgetRuntime {

	/** CS1 comparison: the evaluated value must equal the target. */
	private static final int COMPARISON_EQUALS = 1;
	/** CS1 comparison: the evaluated value must be less than the target. */
	private static final int COMPARISON_LESS_THAN = 2;
	/** CS1 comparison: the evaluated value must be greater than the target. */
	private static final int COMPARISON_GREATER_THAN = 3;
	/** CS1 comparison: the evaluated value must not equal the target. */
	private static final int COMPARISON_NOT_EQUALS = 4;

	/** Terminates a CS1 instruction stream and returns the accumulator. */
	private static final int SCRIPT_END = 0;
	/** Reads a current skill level. */
	private static final int SCRIPT_CURRENT_SKILL_LEVEL = 1;
	/** Reads a base skill level. */
	private static final int SCRIPT_BASE_SKILL_LEVEL = 2;
	/** Reads skill experience. */
	private static final int SCRIPT_SKILL_EXPERIENCE = 3;
	/** Sums an item's amount in a widget inventory. */
	private static final int SCRIPT_INVENTORY_ITEM_AMOUNT = 4;
	/** Reads a varp value. */
	private static final int SCRIPT_VARP = 5;
	/** Reads the experience threshold for a base skill level. */
	private static final int SCRIPT_EXPERIENCE_FOR_LEVEL = 6;
	/** Converts a varp value to the legacy 0..100 percentage scale. */
	private static final int SCRIPT_VARP_PERCENT = 7;
	/** Reads the local player's combat level. */
	private static final int SCRIPT_COMBAT_LEVEL = 8;
	/** Sums all enabled base skill levels. */
	private static final int SCRIPT_TOTAL_LEVEL = 9;
	/** Tests whether a widget inventory contains an item. */
	private static final int SCRIPT_INVENTORY_CONTAINS_ITEM = 10;
	/** Reads run energy. */
	private static final int SCRIPT_RUN_ENERGY = 11;
	/** Reads carried weight. */
	private static final int SCRIPT_WEIGHT = 12;
	/** Tests one bit in a varp. */
	private static final int SCRIPT_VARP_BIT = 13;
	/** Reads a varbit value. */
	private static final int SCRIPT_VARBIT = 14;
	/** Selects subtraction for the next value. */
	private static final int SCRIPT_SET_SUBTRACT = 15;
	/** Selects division for the next value. */
	private static final int SCRIPT_SET_DIVIDE = 16;
	/** Selects multiplication for the next value. */
	private static final int SCRIPT_SET_MULTIPLY = 17;
	/** Reads the local player's absolute world X coordinate. */
	private static final int SCRIPT_PLAYER_WORLD_X = 18;
	/** Reads the local player's absolute world Y coordinate. */
	private static final int SCRIPT_PLAYER_WORLD_Y = 19;
	/** Reads an immediate literal from the instruction stream. */
	private static final int SCRIPT_LITERAL = 20;

	/** Applies addition to the accumulated CS1 value. */
	private static final int OPERATION_ADD = 0;
	/** Applies subtraction to the accumulated CS1 value. */
	private static final int OPERATION_SUBTRACT = 1;
	/** Applies division to the accumulated CS1 value. */
	private static final int OPERATION_DIVIDE = 2;
	/** Applies multiplication to the accumulated CS1 value. */
	private static final int OPERATION_MULTIPLY = 3;

	/** Result returned when a widget has no script at the requested index. */
	private static final int SCRIPT_UNAVAILABLE = -2;
	/** Result returned when a CS1 script throws while being evaluated. */
	private static final int SCRIPT_ERROR = -1;
	/** Legacy sentinel produced by the inventory-contains-item instruction. */
	private static final int ITEM_PRESENT_VALUE = 999_999_999;
	/** Percentage numerator used by the legacy varp-percent instruction. */
	private static final int PERCENT_SCALE = 100;
	/** Legacy denominator used by the varp-percent instruction. */
	private static final int VARP_PERCENT_DENOMINATOR = 46_875;

	/** Provides script context state and behavior. */
	public interface ScriptContext {
		/**
		 * Returns the current skill level.
		 *
		 * @param skill the skill
		 * @return the current skill level
		 */
		int currentSkillLevel(int skill);

		/**
		 * Returns the base skill level.
		 *
		 * @param skill the skill
		 * @return the base skill level
		 */
		int baseSkillLevel(int skill);

		/**
		 * Returns the skill experience.
		 *
		 * @param skill the skill
		 * @return the skill experience
		 */
		int skillExperience(int skill);

		/**
		 * Returns the varp.
		 *
		 * @param id the identifier
		 * @return the varp
		 */
		int varp(int id);

		/**
		 * Returns the experience for level.
		 *
		 * @param levelIndex the level index
		 * @return the experience for level
		 */
		int experienceForLevel(int levelIndex);

		/**
		 * Returns the bit mask.
		 *
		 * @param width the width in pixels
		 * @return the bit mask
		 */
		int bitMask(int width);

		/**
		 * Runs energy.
		 *
		 * @return the current run-energy percentage
		 */
		int runEnergy();

		/**
		 * Returns the weight.
		 *
		 * @return the weight
		 */
		int weight();

		/**
		 * Returns the combat level.
		 *
		 * @return the combat level
		 */
		int combatLevel();

		/**
		 * Returns the player world X coordinate.
		 *
		 * @return the player world X
		 */
		int playerWorldX();

		/**
		 * Returns the player world Y coordinate.
		 *
		 * @return the player world Y
		 */
		int playerWorldY();

		/**
		 * Returns whether members world is active.
		 *
		 * @return whether members world
		 */
		boolean membersWorld();
	}

	/** Stores the current context. */
	private final ScriptContext context;

	/**
	 * Creates a new widget runtime.
	 *
	 * @param context the context
	 */
	public WidgetRuntime(ScriptContext context) {
		this.context = context;
	}

	/**
	 * Advances model-widget animation and rotation state recursively.
	 * 
	 * @param deltaCycles the delta cycles
	 * @param interfaceId the interface ID
	 * @return whether update animations
	 */
	public boolean updateAnimations(int deltaCycles, int interfaceId) {
		boolean changed = false;
		Widget parent = Widget.get(interfaceId);
		for (int index = 0; index < parent.children.length; index++) {
			if (parent.children[index] == -1)
				break;
			Widget child = Widget.get(parent.children[index]);
			if (child.type == Widget.TYPE_CONTAINER)
				changed |= updateAnimations(deltaCycles, child.id);
			if (child.type == Widget.TYPE_MODEL && (child.animationId != -1 || child.activeAnimationId != -1)) {
				boolean active = isActive(child);
				int animationId = active ? child.activeAnimationId : child.animationId;
				if (animationId != -1) {
					AnimationSequence sequence = AnimationSequence.sequences[animationId];
					for (child.animationCycle += deltaCycles; child.animationCycle > sequence
							.getFrameLength(child.animationFrame);) {
						child.animationCycle -= sequence.getFrameLength(child.animationFrame);
						child.animationFrame++;
						if (child.animationFrame >= sequence.frameCount) {
							child.animationFrame -= sequence.frameStep;
							if (child.animationFrame < 0 || child.animationFrame >= sequence.frameCount)
								child.animationFrame = 0;
						}
						changed = true;
					}
				}
			}
			if (child.type == Widget.TYPE_MODEL && child.modelRotationSpeed != 0) {
				int pitchSpeed = child.modelRotationSpeed >> 16;
				int yawSpeed = (child.modelRotationSpeed << 16) >> 16;
				pitchSpeed *= deltaCycles;
				yawSpeed *= deltaCycles;
				child.modelPitch = child.modelPitch + pitchSpeed & Angle.MASK;
				child.modelYaw = child.modelYaw + yawSpeed & Angle.MASK;
				changed = true;
			}
		}
		return changed;
	}

	/**
	 * Resets animation frames for an interface tree.
	 * 
	 * @param interfaceId the interface ID
	 */
	public void resetAnimations(int interfaceId) {
		Widget parent = Widget.get(interfaceId);
		for (int index = 0; index < parent.children.length; index++) {
			if (parent.children[index] == -1)
				break;
			Widget child = Widget.get(parent.children[index]);
			if (child.type == Widget.TYPE_UNKNOWN)
				resetAnimations(child.id);
			child.animationFrame = 0;
			child.animationCycle = 0;
		}
	}

	/**
	 * Evaluates a widget's CS1 comparisons to determine its active state.
	 * 
	 * @param widget the widget
	 * @return whether active
	 */
	public boolean isActive(Widget widget) {
		if (widget.cs1Comparisons == null)
			return false;
		for (int index = 0; index < widget.cs1Comparisons.length; index++) {
			int value = evaluateScript(widget, index);
			int target = widget.cs1ComparisonValues[index];
			if (widget.cs1Comparisons[index] == COMPARISON_LESS_THAN) {
				if (value >= target)
					return false;
			} else if (widget.cs1Comparisons[index] == COMPARISON_GREATER_THAN) {
				if (value <= target)
					return false;
			} else if (widget.cs1Comparisons[index] == COMPARISON_NOT_EQUALS) {
				if (value == target)
					return false;
			} else if (widget.cs1Comparisons[index] == COMPARISON_EQUALS && value != target) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Evaluates one revision-377 CS1 integer script, returning -1 on evaluation
	 * failure.
	 * 
	 * @param widget      the widget
	 * @param scriptIndex the script index
	 * @return the script result, or a negative sentinel when evaluation cannot
	 *         complete
	 */
	public int evaluateScript(Widget widget, int scriptIndex) {
		if (widget.cs1Instructions == null || scriptIndex >= widget.cs1Instructions.length)
			return SCRIPT_UNAVAILABLE;
		try {
			int[] instructions = widget.cs1Instructions[scriptIndex];
			int accumulator = 0;
			int position = 0;
			int pendingOperation = OPERATION_ADD;
			do {
				int opcode = instructions[position++];
				int value = 0;
				byte nextOperation = OPERATION_ADD;
				if (opcode == SCRIPT_END)
					return accumulator;
				if (opcode == SCRIPT_CURRENT_SKILL_LEVEL)
					value = context.currentSkillLevel(instructions[position++]);
				if (opcode == SCRIPT_BASE_SKILL_LEVEL)
					value = context.baseSkillLevel(instructions[position++]);
				if (opcode == SCRIPT_SKILL_EXPERIENCE)
					value = context.skillExperience(instructions[position++]);
				if (opcode == SCRIPT_INVENTORY_ITEM_AMOUNT) {
					Widget inventory = Widget.get(instructions[position++]);
					int itemId = instructions[position++];
					if (itemId >= 0 && itemId < ItemDefinition.count
							&& (!ItemDefinition.lookup(itemId).membersOnly || context.membersWorld())) {
						for (int slot = 0; slot < inventory.itemIds.length; slot++)
							if (inventory.itemIds[slot] == itemId + 1)
								value += inventory.itemAmounts[slot];
					}
				}
				if (opcode == SCRIPT_VARP)
					value = context.varp(instructions[position++]);
				if (opcode == SCRIPT_EXPERIENCE_FOR_LEVEL)
					value = context.experienceForLevel(context.baseSkillLevel(instructions[position++]) - 1);
				if (opcode == SCRIPT_VARP_PERCENT)
					value = (context.varp(instructions[position++]) * PERCENT_SCALE) / VARP_PERCENT_DENOMINATOR;
				if (opcode == SCRIPT_COMBAT_LEVEL)
					value = context.combatLevel();
				if (opcode == SCRIPT_TOTAL_LEVEL) {
					for (int skill = 0; skill < Skills.COUNT; skill++)
						if (Skills.ENABLED[skill])
							value += context.baseSkillLevel(skill);
				}
				if (opcode == SCRIPT_INVENTORY_CONTAINS_ITEM) {
					Widget inventory = Widget.get(instructions[position++]);
					int encodedItemId = instructions[position++] + 1;
					if (encodedItemId >= 0 && encodedItemId < ItemDefinition.count
							&& (!ItemDefinition.lookup(encodedItemId).membersOnly || context.membersWorld())) {
						for (int slot = 0; slot < inventory.itemIds.length; slot++) {
							if (inventory.itemIds[slot] != encodedItemId)
								continue;
							value = ITEM_PRESENT_VALUE;
							break;
						}
					}
				}
				if (opcode == SCRIPT_RUN_ENERGY)
					value = context.runEnergy();
				if (opcode == SCRIPT_WEIGHT)
					value = context.weight();
				if (opcode == SCRIPT_VARP_BIT) {
					int varp = context.varp(instructions[position++]);
					int bit = instructions[position++];
					value = (varp & 1 << bit) == 0 ? 0 : 1;
				}
				if (opcode == SCRIPT_VARBIT) {
					Varbit varbit = Varbit.definitions[instructions[position++]];
					int width = varbit.mostSignificantBit - varbit.leastSignificantBit;
					value = context.varp(varbit.varpId) >> varbit.leastSignificantBit & context.bitMask(width);
				}
				if (opcode == SCRIPT_SET_SUBTRACT)
					nextOperation = OPERATION_SUBTRACT;
				if (opcode == SCRIPT_SET_DIVIDE)
					nextOperation = OPERATION_DIVIDE;
				if (opcode == SCRIPT_SET_MULTIPLY)
					nextOperation = OPERATION_MULTIPLY;
				if (opcode == SCRIPT_PLAYER_WORLD_X)
					value = context.playerWorldX();
				if (opcode == SCRIPT_PLAYER_WORLD_Y)
					value = context.playerWorldY();
				if (opcode == SCRIPT_LITERAL)
					value = instructions[position++];
				if (nextOperation == OPERATION_ADD) {
					if (pendingOperation == OPERATION_ADD)
						accumulator += value;
					if (pendingOperation == OPERATION_SUBTRACT)
						accumulator -= value;
					if (pendingOperation == OPERATION_DIVIDE && value != 0)
						accumulator /= value;
					if (pendingOperation == OPERATION_MULTIPLY)
						accumulator *= value;
					pendingOperation = OPERATION_ADD;
				} else {
					pendingOperation = nextOperation;
				}
			} while (true);
		} catch (Exception ignored) {
			return SCRIPT_ERROR;
		}
	}
}
