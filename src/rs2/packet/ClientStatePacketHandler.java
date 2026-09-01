package rs2.packet;

import java.util.function.IntConsumer;

import rs2.game.MinimapRenderer;
import rs2.game.VarpState;
import rs2.net.Buffer;
import rs2.net.IncomingPacketOpcode;
import rs2.ui.InterfaceController;

/**
 * Applies varp, skill, run-energy, minimap, weight, and timer state packets.
 *
 * <p>
 * This application-layer domain handler is invoked only after
 * {@link PacketDomainDispatcher} has explicitly routed a recognized
 * revision-377 opcode to it.
 * </p>
 */
final class ClientStatePacketHandler {

	/** Current/server-shadow varp owner. */
	private final VarpState varps;
	/** Interface state consulted for selected-tab redraws. */
	private final InterfaceController interfaces;
	/** Minimap owner receiving minimap-state packets. */
	private final MinimapRenderer minimap;
	/** Applies a changed varp to client subsystems. */
	private final IntConsumer applyVarp;
	/** Requests sidebar redraws after visible state changes. */
	private final Runnable redrawSidebar;
	/** Requests chatbox redraws when dialogue-visible varps change. */
	private final Runnable redrawChatbox;
	/** Applies the decoded weight value. */
	private final IntConsumer setWeight;
	/** Stores skill experience values. */
	private final int[] skillExperiences;
	/** Stores current skill-level values. */
	private final int[] currentSkillLevels;
	/** Stores calculated base skill levels. */
	private final int[] baseSkillLevels;
	/** Revision-377 experience thresholds used to calculate base levels. */
	private final int[] experienceTable;
	/** Applies the decoded system-update timer. */
	private final IntConsumer setSystemUpdateTimer;
	/** Applies the decoded run-energy value. */
	private final IntConsumer setRunEnergy;

	/**
	 * Creates the client-state packet handler from its exact application
	 * capabilities.
	 *
	 * @param varps                varp owner
	 * @param interfaces           interface state owner
	 * @param minimap              minimap owner
	 * @param applyVarp            varp-effect callback
	 * @param redrawSidebar        sidebar redraw callback
	 * @param redrawChatbox        chatbox redraw callback
	 * @param setWeight            weight sink
	 * @param skillExperiences     skill experience array
	 * @param currentSkillLevels   current skill-level array
	 * @param baseSkillLevels      base skill-level array
	 * @param experienceTable      experience threshold table
	 * @param setSystemUpdateTimer system-update timer sink
	 * @param setRunEnergy         run-energy sink
	 */
	ClientStatePacketHandler(VarpState varps, InterfaceController interfaces, MinimapRenderer minimap,
			IntConsumer applyVarp, Runnable redrawSidebar, Runnable redrawChatbox, IntConsumer setWeight,
			int[] skillExperiences, int[] currentSkillLevels, int[] baseSkillLevels, int[] experienceTable,
			IntConsumer setSystemUpdateTimer, IntConsumer setRunEnergy) {
		this.varps = varps;
		this.interfaces = interfaces;
		this.minimap = minimap;
		this.applyVarp = applyVarp;
		this.redrawSidebar = redrawSidebar;
		this.redrawChatbox = redrawChatbox;
		this.setWeight = setWeight;
		this.skillExperiences = skillExperiences;
		this.currentSkillLevels = currentSkillLevels;
		this.baseSkillLevels = baseSkillLevels;
		this.experienceTable = experienceTable;
		this.setSystemUpdateTimer = setSystemUpdateTimer;
		this.setRunEnergy = setRunEnergy;
	}

	/**
	 * Applies one packet already routed to this domain.
	 *
	 * @param opcode     decoded revision-377 opcode
	 * @param buffer     payload buffer positioned at zero
	 * @param packetSize payload length in bytes
	 * @return always {@code true}; routed domain packets continue processing
	 * @throws IllegalArgumentException if the opcode was routed to the wrong domain
	 */
	boolean handle(int opcode, Buffer buffer, int packetSize) {
		if (opcode == IncomingPacketOpcode.SET_VARP_SMALL) {
			int varpId = buffer.readUnsignedShortAdd();
			byte varpValue = buffer.readSignedByteSub();
			if (varps.acceptServerValue(varpId, varpValue)) {
				applyVarp.accept(varpId);
				redrawSidebar.run();
				if (interfaces.state().dialogueInterfaceId != -1)
					redrawChatbox.run();
			}
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_MINIMAP_STATE) {
			minimap.state = buffer.readUnsignedByte();
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_VARP_LARGE) {
			int varpValue2 = buffer.readIntIME();
			int varpId2 = buffer.readUnsignedShortLE();
			if (varps.acceptServerValue(varpId2, varpValue2)) {
				applyVarp.accept(varpId2);
				redrawSidebar.run();
				if (interfaces.state().dialogueInterfaceId != -1)
					redrawChatbox.run();
			}
			return true;
		}
		if (opcode == IncomingPacketOpcode.UPDATE_WEIGHT) {
			if (interfaces.state().selectedTab == 12)
				redrawSidebar.run();
			setWeight.accept(buffer.readSignedShort());
			return true;
		}
		if (opcode == IncomingPacketOpcode.UPDATE_SKILL) {
			redrawSidebar.run();
			int skillId = buffer.readUnsignedByteNeg();
			int currentLevel = buffer.readUnsignedByte();
			int experience = buffer.readInt();
			skillExperiences[skillId] = experience;
			currentSkillLevels[skillId] = currentLevel;
			baseSkillLevels[skillId] = 1;
			for (int levelIndex = 0; levelIndex < 98; levelIndex++)
				if (experience >= experienceTable[levelIndex])
					baseSkillLevels[skillId] = levelIndex + 2;
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_SYSTEM_UPDATE_TIMER) {
			setSystemUpdateTimer.accept(buffer.readUnsignedShortLE() * 30);
			return true;
		}
		if (opcode == IncomingPacketOpcode.UPDATE_RUN_ENERGY) {
			if (interfaces.state().selectedTab == 12)
				redrawSidebar.run();
			setRunEnergy.accept(buffer.readUnsignedByte());
			return true;
		}
		if (opcode == IncomingPacketOpcode.SYNCHRONIZE_VARPS) {
			varps.synchronizeToShadow(varpId3 -> {
				applyVarp.accept(varpId3);
				redrawSidebar.run();
			});
			return true;
		}
		throw new IllegalArgumentException("Opcode " + opcode + " is not a client-state packet");
	}
}
