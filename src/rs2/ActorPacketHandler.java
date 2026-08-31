package rs2;

import rs2.net.Buffer;
import rs2.net.IncomingPacketOpcode;

/**
 * Applies player/NPC synchronization and actor lifecycle packets.
 *
 * <p>This application-layer domain handler is invoked only after
 * {@link ClientIncomingPacketHandler} has explicitly routed a recognized
 * revision-377 opcode to it.</p>
 */
final class ActorPacketHandler {

	/** Client runtime receiving decoded packet effects. */
	private final Client client;

	/**
	 * Creates the actor packet handler.
	 *
	 * @param client client runtime receiving packet effects
	 */
	ActorPacketHandler(Client client) {
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
		if (opcode == IncomingPacketOpcode.RESET_ENTITY_ANIMATIONS) {
			for (int playerIndex = 0; playerIndex < client.packetActorSynchronizer().players.length; playerIndex++)
				if (client.packetActorSynchronizer().players[playerIndex] != null)
					client.packetActorSynchronizer().players[playerIndex].sequence = -1;

			for (int npcIndex = 0; npcIndex < client.packetActorSynchronizer().npcs.length; npcIndex++)
				if (client.packetActorSynchronizer().npcs[npcIndex] != null)
					client.packetActorSynchronizer().npcs[npcIndex].sequence = -1;
			return true;
		}
		if (opcode == IncomingPacketOpcode.NPC_UPDATE) {
			client.packetActorSynchronizer().decodeNpcUpdate(buffer, packetSize, client.gameCycle,
					client.packetLoginUsername());
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_LOCAL_PLAYER_INDEX) {
			client.accountMembershipStatus = buffer.readUnsignedByte();
			client.localPlayerServerIndex = buffer.readUnsignedShortLE();
			return true;
		}
		if (opcode == IncomingPacketOpcode.PLAYER_UPDATE) {
			client.currentPlane = client.packetActorSynchronizer().decodePlayerUpdate(buffer, packetSize,
					client.gameCycle, client.currentPlane, client.packetLoginUsername(), client.chatBuffer, client.packetActorChatHandler());
			client.packetRegionManager().playerUpdateReceived();
			return true;
		}
		throw new IllegalArgumentException("Opcode " + opcode + " is not a actor packet");
	}
}
