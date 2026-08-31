package rs2.packet;

import java.util.function.Supplier;

import rs2.cache.ondemand.OnDemandFetcher;
import rs2.game.ActorSynchronizer;
import rs2.game.CameraController;
import rs2.game.RegionManager;
import rs2.game.WorldState;
import rs2.game.ZoneUpdateHandler;
import rs2.game.entity.Player;
import rs2.net.Buffer;
import rs2.net.IncomingPacketOpcode;

/**
 * Applies region rebuild, zone update, and world-location packets.
 *
 * <p>This application-layer domain handler is invoked only after
 * {@link PacketDomainDispatcher} has explicitly routed a recognized
 * revision-377 opcode to it.</p>
 */
final class RegionPacketHandler {

	/** Region-rebuild owner. */
	private final RegionManager regions;
	/** Supplies the asynchronous on-demand resource service. */
	private final Supplier<OnDemandFetcher> resources;
	/** Actor synchronization owner used by rebuild and zone packets. */
	private final ActorSynchronizer actors;
	/** Supplies world state created during client startup. */
	private final Supplier<WorldState> world;
	/** Supplies zone-update state created during client startup. */
	private final Supplier<ZoneUpdateHandler> zoneUpdates;
	/** Camera owner reset after a region shift. */
	private final CameraController camera;
	/** Narrow mutable client state required by region packets. */
	private final PacketDomainDispatcher.RegionState state;
	/** Localized-area-sound callback. */
	private final ZoneUpdateHandler.AreaSoundHandler areaSounds;
	/** Displays the region-loading message after a rebuild shift. */
	private final Runnable showLoadingMessage;


	/**
	 * Creates the region packet handler from its exact application capabilities.
	 *
	 * @param regions region-rebuild owner
	 * @param resources on-demand resource supplier
	 * @param actors actor synchronization owner
	 * @param world world-state supplier
	 * @param zoneUpdates zone-update-state supplier
	 * @param camera camera owner
	 * @param state narrow region packet state
	 * @param areaSounds localized-area-sound callback
	 * @param showLoadingMessage loading-message callback
	 */
	RegionPacketHandler(RegionManager regions, Supplier<OnDemandFetcher> resources, ActorSynchronizer actors,
			Supplier<WorldState> world, Supplier<ZoneUpdateHandler> zoneUpdates, CameraController camera, PacketDomainDispatcher.RegionState state,
			ZoneUpdateHandler.AreaSoundHandler areaSounds, Runnable showLoadingMessage) {
		this.regions = regions;
		this.resources = resources;
		this.actors = actors;
		this.world = world;
		this.zoneUpdates = zoneUpdates;
		this.camera = camera;
		this.state = state;
		this.areaSounds = areaSounds;
		this.showLoadingMessage = showLoadingMessage;
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
			state.setMultiCombatZone(buffer.readUnsignedByte());
			return true;
		}
		if (opcode == IncomingPacketOpcode.CLEAR_DESTINATION) {
			state.setDestination(0, state.destinationY());
			return true;
		}
		if (opcode == IncomingPacketOpcode.CLEAR_ZONE) {
			int zoneBaseY = buffer.readUnsignedByteSub();
			int zoneBaseX = buffer.readUnsignedByteNeg();
			zoneUpdates.get().setZoneBase(zoneBaseX, zoneBaseY);
			world.get().clearZone(state.currentPlane(), zoneBaseX, zoneBaseY);
			return true;
		}
		if (opcode == IncomingPacketOpcode.BATCH_ZONE_UPDATES) {
			zoneUpdates.get().setZoneBase(buffer.readUnsignedByte(),
					buffer.readUnsignedByteAdd());
			while (buffer.position < packetSize) {
				int updateType = buffer.readUnsignedByte();
				zoneUpdates.get().decode(buffer, updateType, state.currentPlane(), state.gameCycle(), state.localPlayerServerIndex(),
						state.localPlayer(), actors, areaSounds);
			}
			return true;
		}
		if (opcode == IncomingPacketOpcode.REBUILD_REGION || opcode == IncomingPacketOpcode.REBUILD_INSTANCED_REGION) {
			RegionManager.RegionShift shift = regions.decodeRebuild(buffer,
					opcode, resources.get(), actors, world.get(), state.destinationX(),
					state.destinationY());
			if (shift.changed) {
				state.setDestination(shift.destinationX, shift.destinationY);
				camera.cinematic = false;
				showLoadingMessage.run();
			}
			return true;
		}
		if (opcode == IncomingPacketOpcode.PLAY_AREA_SOUND || opcode == IncomingPacketOpcode.UPDATE_GROUND_ITEM_AMOUNT
				|| opcode == IncomingPacketOpcode.ATTACH_OBJECT_TO_PLAYER || opcode == IncomingPacketOpcode.ADD_GROUND_ITEM_FOR_OTHER_PLAYER
				|| opcode == IncomingPacketOpcode.ADD_GRAPHICS_OBJECT || opcode == IncomingPacketOpcode.ADD_PROJECTILE
				|| opcode == IncomingPacketOpcode.REMOVE_GROUND_ITEM || opcode == IncomingPacketOpcode.ADD_GROUND_ITEM
				|| opcode == IncomingPacketOpcode.ANIMATE_GAME_OBJECT || opcode == IncomingPacketOpcode.REMOVE_GAME_OBJECT
				|| opcode == IncomingPacketOpcode.ADD_GAME_OBJECT) {
			zoneUpdates.get().decode(buffer, opcode, state.currentPlane(), state.gameCycle(),
					state.localPlayerServerIndex(), state.localPlayer(), actors, areaSounds);
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_ZONE_BASE) {
			zoneUpdates.get().setZoneBase(buffer.readUnsignedByteNeg(),
					buffer.readUnsignedByteAdd());
			return true;
		}
		throw new IllegalArgumentException("Opcode " + opcode + " is not a region packet");
	}
}
