package rs2;

import rs2.net.Buffer;
import rs2.net.IncomingPacketOpcode;

/**
 * Applies varp, skill, run-energy, minimap, weight, and timer state packets.
 *
 * <p>This application-layer domain handler is invoked only after
 * {@link ClientIncomingPacketHandler} has explicitly routed a recognized
 * revision-377 opcode to it.</p>
 */
final class ClientStatePacketHandler {

	/** Client runtime receiving decoded packet effects. */
	private final Client client;

	/**
	 * Creates the client-state packet handler.
	 *
	 * @param client client runtime receiving packet effects
	 */
	ClientStatePacketHandler(Client client) {
		this.client = client;
	}

	/**
	 * Applies one packet already routed to this domain.
	 *
	 * @param opcode decoded revision-377 opcode
	 * @param buffer payload buffer positioned at zero
	 * @param packetSize payload length in bytes
	 * @return always {@code true}; routed domain packets continue processing
	 * @throws IllegalArgumentException if the opcode was routed to the wrong domain
	 */
	boolean handle(int opcode, Buffer buffer, int packetSize) {
		if (opcode == IncomingPacketOpcode.SET_VARP_SMALL) {
			int varpId = buffer.readUnsignedShortAdd();
			byte varpValue = buffer.readSignedByteSub();
			if (client.packetVarpState().acceptServerValue(varpId, varpValue)) {
				client.applyVarp(varpId);
				client.requestSidebarRedraw();
				if (client.packetInterfaceController().state().dialogueInterfaceId != -1)
					client.requestChatboxRedraw();
			}
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_MINIMAP_STATE) {
			client.packetMinimapRenderer().state = buffer.readUnsignedByte();
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_VARP_LARGE) {
			int varpValue2 = buffer.readIntIME();
			int varpId2 = buffer.readUnsignedShortLE();
			if (client.packetVarpState().acceptServerValue(varpId2, varpValue2)) {
				client.applyVarp(varpId2);
				client.requestSidebarRedraw();
				if (client.packetInterfaceController().state().dialogueInterfaceId != -1)
					client.requestChatboxRedraw();
			}
			return true;
		}
		if (opcode == IncomingPacketOpcode.UPDATE_WEIGHT) {
			if (client.packetInterfaceController().state().selectedTab == 12)
				client.requestSidebarRedraw();
			client.weight = buffer.readSignedShort();
			return true;
		}
		if (opcode == IncomingPacketOpcode.UPDATE_SKILL) {
			client.requestSidebarRedraw();
			int skillId = buffer.readUnsignedByteNeg();
			int currentLevel = buffer.readUnsignedByte();
			int experience = buffer.readInt();
			client.skillExperiences[skillId] = experience;
			client.currentSkillLevels[skillId] = currentLevel;
			client.baseSkillLevels[skillId] = 1;
			for (int levelIndex = 0; levelIndex < 98; levelIndex++)
				if (experience >= client.experienceTable[levelIndex])
					client.baseSkillLevels[skillId] = levelIndex + 2;
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_SYSTEM_UPDATE_TIMER) {
			client.systemUpdateTimer = buffer.readUnsignedShortLE() * 30;
			return true;
		}
		if (opcode == IncomingPacketOpcode.UPDATE_RUN_ENERGY) {
			if (client.packetInterfaceController().state().selectedTab == 12)
				client.requestSidebarRedraw();
			client.runEnergy = buffer.readUnsignedByte();
			return true;
		}
		if (opcode == IncomingPacketOpcode.SYNCHRONIZE_VARPS) {
			client.packetVarpState().synchronizeToShadow(varpId3 -> {
				client.applyVarp(varpId3);
				client.requestSidebarRedraw();
			});
			return true;
		}
		throw new IllegalArgumentException("Opcode " + opcode + " is not a client-state packet");
	}
}
