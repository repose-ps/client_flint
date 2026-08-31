package rs2;

import rs2.game.RegionManager;
import rs2.net.Buffer;
import rs2.net.IncomingPacketOpcode;

/**
 * Applies region rebuild, zone update, and world-location packets.
 *
 * <p>This application-layer domain handler is invoked only after
 * {@link ClientIncomingPacketHandler} has explicitly routed a recognized
 * revision-377 opcode to it.</p>
 */
final class RegionPacketHandler {

	/** Client runtime receiving decoded packet effects. */
	private final Client client;

	/**
	 * Creates the region packet handler.
	 *
	 * @param client client runtime receiving packet effects
	 */
	RegionPacketHandler(Client client) {
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
		if (opcode == IncomingPacketOpcode.SET_MULTI_COMBAT) {
			client.multiCombatZone = buffer.readUnsignedByte();
			return true;
		}
		if (opcode == IncomingPacketOpcode.CLEAR_DESTINATION) {
			client.destinationX = 0;
			return true;
		}
		if (opcode == IncomingPacketOpcode.CLEAR_ZONE) {
			int zoneBaseY = buffer.readUnsignedByteSub();
			int zoneBaseX = buffer.readUnsignedByteNeg();
			client.packetZoneUpdates().setZoneBase(zoneBaseX, zoneBaseY);
			client.packetWorldState().clearZone(client.currentPlane, zoneBaseX, zoneBaseY);
			return true;
		}
		if (opcode == IncomingPacketOpcode.BATCH_ZONE_UPDATES) {
			client.packetZoneUpdates().setZoneBase(buffer.readUnsignedByte(),
					buffer.readUnsignedByteAdd());
			while (buffer.position < packetSize) {
				int updateType = buffer.readUnsignedByte();
				client.packetZoneUpdates().decode(buffer, updateType, client.currentPlane, client.gameCycle, client.localPlayerServerIndex,
						client.localPlayer, client.packetActorSynchronizer(), client::queueAreaSound);
			}
			return true;
		}
		if (opcode == IncomingPacketOpcode.REBUILD_REGION || opcode == IncomingPacketOpcode.REBUILD_INSTANCED_REGION) {
			RegionManager.RegionShift shift = client.packetRegionManager().decodeRebuild(buffer,
					opcode, client.packetOnDemandFetcher(), client.packetActorSynchronizer(), client.packetWorldState(), client.destinationX,
					client.destinationY);
			if (shift.changed) {
				client.destinationX = shift.destinationX;
				client.destinationY = shift.destinationY;
				client.packetCameraController().cinematic = false;
				client.drawGameLoadingMessage(null, "Loading - please wait.");
			}
			return true;
		}
		if (opcode == IncomingPacketOpcode.PLAY_AREA_SOUND || opcode == IncomingPacketOpcode.UPDATE_GROUND_ITEM_AMOUNT
				|| opcode == IncomingPacketOpcode.ATTACH_OBJECT_TO_PLAYER || opcode == IncomingPacketOpcode.ADD_GROUND_ITEM_FOR_OTHER_PLAYER
				|| opcode == IncomingPacketOpcode.ADD_GRAPHICS_OBJECT || opcode == IncomingPacketOpcode.ADD_PROJECTILE
				|| opcode == IncomingPacketOpcode.REMOVE_GROUND_ITEM || opcode == IncomingPacketOpcode.ADD_GROUND_ITEM
				|| opcode == IncomingPacketOpcode.ANIMATE_GAME_OBJECT || opcode == IncomingPacketOpcode.REMOVE_GAME_OBJECT
				|| opcode == IncomingPacketOpcode.ADD_GAME_OBJECT) {
			client.packetZoneUpdates().decode(buffer, opcode, client.currentPlane, client.gameCycle,
					client.localPlayerServerIndex, client.localPlayer, client.packetActorSynchronizer(), client::queueAreaSound);
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_ZONE_BASE) {
			client.packetZoneUpdates().setZoneBase(buffer.readUnsignedByteNeg(),
					buffer.readUnsignedByteAdd());
			return true;
		}
		throw new IllegalArgumentException("Opcode " + opcode + " is not a region packet");
	}
}
