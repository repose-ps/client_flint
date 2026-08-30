package rs2.ui;

import rs2.cache.cfg.Varbit;
import rs2.cache.def.ItemDefinition;
import rs2.cache.def.AnimationSequence;
import rs2.ui.Widget;
import rs2.game.Skills;

/**
 * Runtime behavior shared by revision-377 interface widgets: CS1 expression
 * evaluation, CS1 comparisons, model animation updates, and animation reset.
 */
public final class WidgetRuntime {
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
				child.modelPitch = child.modelPitch + pitchSpeed & 0x7ff;
				child.modelYaw = child.modelYaw + yawSpeed & 0x7ff;
				changed = true;
			}
		}
		return changed;
	}

	/**
	 * Resets animation frames for an interface tree.
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
	 * @param widget the widget
	 * @return whether active
	 */
	public boolean isActive(Widget widget) {
		if (widget.cs1Comparisons == null)
			return false;
		for (int index = 0; index < widget.cs1Comparisons.length; index++) {
			int value = evaluateScript(widget, index);
			int target = widget.cs1ComparisonValues[index];
			if (widget.cs1Comparisons[index] == 2) {
				if (value >= target)
					return false;
			} else if (widget.cs1Comparisons[index] == 3) {
				if (value <= target)
					return false;
			} else if (widget.cs1Comparisons[index] == 4) {
				if (value == target)
					return false;
			} else if (value != target) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Evaluates one revision-377 CS1 integer script, returning -1 on evaluation failure.
	 * @param widget the widget
	 * @param scriptIndex the script index
	 * @return the script result, or a negative sentinel when evaluation cannot complete
	 */
	public int evaluateScript(Widget widget, int scriptIndex) {
		if (widget.cs1Instructions == null || scriptIndex >= widget.cs1Instructions.length)
			return -2;
		try {
			int[] instructions = widget.cs1Instructions[scriptIndex];
			int accumulator = 0;
			int position = 0;
			int pendingOperation = 0;
			do {
				int opcode = instructions[position++];
				int value = 0;
				byte nextOperation = 0;
				if (opcode == 0)
					return accumulator;
				if (opcode == 1)
					value = context.currentSkillLevel(instructions[position++]);
				if (opcode == 2)
					value = context.baseSkillLevel(instructions[position++]);
				if (opcode == 3)
					value = context.skillExperience(instructions[position++]);
				if (opcode == 4) {
					Widget inventory = Widget.get(instructions[position++]);
					int itemId = instructions[position++];
					if (itemId >= 0 && itemId < ItemDefinition.count
							&& (!ItemDefinition.lookup(itemId).membersOnly || context.membersWorld())) {
						for (int slot = 0; slot < inventory.itemIds.length; slot++)
							if (inventory.itemIds[slot] == itemId + 1)
								value += inventory.itemAmounts[slot];
					}
				}
				if (opcode == 5)
					value = context.varp(instructions[position++]);
				if (opcode == 6)
					value = context.experienceForLevel(context.baseSkillLevel(instructions[position++]) - 1);
				if (opcode == 7)
					value = (context.varp(instructions[position++]) * 100) / 46875;
				if (opcode == 8)
					value = context.combatLevel();
				if (opcode == 9) {
					for (int skill = 0; skill < Skills.COUNT; skill++)
						if (Skills.ENABLED[skill])
							value += context.baseSkillLevel(skill);
				}
				if (opcode == 10) {
					Widget inventory = Widget.get(instructions[position++]);
					int encodedItemId = instructions[position++] + 1;
					if (encodedItemId >= 0 && encodedItemId < ItemDefinition.count
							&& (!ItemDefinition.lookup(encodedItemId).membersOnly || context.membersWorld())) {
						for (int slot = 0; slot < inventory.itemIds.length; slot++) {
							if (inventory.itemIds[slot] != encodedItemId)
								continue;
							value = 0x3b9ac9ff;
							break;
						}
					}
				}
				if (opcode == 11)
					value = context.runEnergy();
				if (opcode == 12)
					value = context.weight();
				if (opcode == 13) {
					int varp = context.varp(instructions[position++]);
					int bit = instructions[position++];
					value = (varp & 1 << bit) == 0 ? 0 : 1;
				}
				if (opcode == 14) {
					Varbit varbit = Varbit.definitions[instructions[position++]];
					int width = varbit.mostSignificantBit - varbit.leastSignificantBit;
					value = context.varp(varbit.varpId) >> varbit.leastSignificantBit & context.bitMask(width);
				}
				if (opcode == 15)
					nextOperation = 1;
				if (opcode == 16)
					nextOperation = 2;
				if (opcode == 17)
					nextOperation = 3;
				if (opcode == 18)
					value = context.playerWorldX();
				if (opcode == 19)
					value = context.playerWorldY();
				if (opcode == 20)
					value = instructions[position++];
				if (nextOperation == 0) {
					if (pendingOperation == 0)
						accumulator += value;
					if (pendingOperation == 1)
						accumulator -= value;
					if (pendingOperation == 2 && value != 0)
						accumulator /= value;
					if (pendingOperation == 3)
						accumulator *= value;
					pendingOperation = 0;
				} else {
					pendingOperation = nextOperation;
				}
			} while (true);
		} catch (Exception ignored) {
			return -1;
		}
	}
}
