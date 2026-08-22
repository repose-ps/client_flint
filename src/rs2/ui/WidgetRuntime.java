package rs2.ui;

import rs2.cache.cfg.Varbit;
import rs2.cache.def.ItemDefinition;
import rs2.cache.media.AnimationSequence;
import rs2.cache.ui.Widget;
import rs2.game.Skills;

/**
 * Runtime behavior shared by revision-377 interface widgets: CS1 expression
 * evaluation, CS1 comparisons, model animation updates, and animation reset.
 */
public final class WidgetRuntime {
	public interface ScriptContext {
		int currentSkillLevel(int skill);

		int baseSkillLevel(int skill);

		int skillExperience(int skill);

		int varp(int id);

		int experienceForLevel(int levelIndex);

		int bitMask(int width);

		int runEnergy();

		int weight();

		int combatLevel();

		int playerWorldX();

		int playerWorldY();

		boolean membersWorld();
	}

	private final ScriptContext context;

	public WidgetRuntime(ScriptContext context) {
		this.context = context;
	}

	/*
	 * Legacy client.method88(int i, int j): i -> deltaCycles j -> interfaceId
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

	/*
	 * Legacy client.method112(byte byte0, int i): byte0 -> removed required 36
	 * sentinel i -> interfaceId
	 *
	 * The legacy recursion checks type 1 rather than container type 0. That oddity
	 * is deliberately preserved.
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

	/* Legacy client.method95(Widget class13): class13 -> widget. */
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

	/*
	 * Legacy client.method129(int i, int j, Widget class13): i -> removed required
	 * value 3 sentinel j -> scriptIndex class13 -> widget
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