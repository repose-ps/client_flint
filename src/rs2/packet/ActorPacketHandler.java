package rs2.packet;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import rs2.game.ActorSynchronizer;
import rs2.game.RegionManager;
import rs2.net.Buffer;
import rs2.net.IncomingPacketOpcode;

/**
 * Applies player/NPC synchronization and actor lifecycle packets.
 *
 * <p>This application-layer domain handler is invoked only after
 * {@link PacketDomainDispatcher} has explicitly routed a recognized
 * revision-377 opcode to it.</p>
 */
final class ActorPacketHandler {

	/** Actor synchronization owner. */
	private final ActorSynchronizer actors;
	/** Region owner notified after player synchronization. */
	private final RegionManager regions;
	/** Supplies the current game cycle. */
	private final IntSupplier gameCycle;
	/** Supplies the current scene plane. */
	private final IntSupplier currentPlane;
	/** Applies scene-plane changes produced by player synchronization. */
	private final IntConsumer setCurrentPlane;
	/** Supplies the login username used while decoding player state. */
	private final Supplier<String> loginUsername;
	/** Shared scratch buffer used by player chat-mask decoding. */
	private final Buffer chatBuffer;
	/** Receives public-chat side effects from player synchronization. */
	private final ActorSynchronizer.ChatHandler chatHandler;
	/** Applies account-membership status from the local-player-index packet. */
	private final IntConsumer setAccountMembershipStatus;
	/** Applies the local player's server index. */
	private final IntConsumer setLocalPlayerServerIndex;

	/**
	 * Creates the actor packet handler from its exact application capabilities.
	 *
	 * @param actors actor synchronization owner
	 * @param regions region owner
	 * @param gameCycle game-cycle supplier
	 * @param currentPlane current-plane supplier
	 * @param setCurrentPlane current-plane sink
	 * @param loginUsername login-username supplier
	 * @param chatBuffer shared chat scratch buffer
	 * @param chatHandler actor chat callback
	 * @param setAccountMembershipStatus membership-status sink
	 * @param setLocalPlayerServerIndex local-player server-index sink
	 */
	ActorPacketHandler(ActorSynchronizer actors, RegionManager regions, IntSupplier gameCycle, IntSupplier currentPlane,
			IntConsumer setCurrentPlane, Supplier<String> loginUsername, Buffer chatBuffer,
			ActorSynchronizer.ChatHandler chatHandler, IntConsumer setAccountMembershipStatus,
			IntConsumer setLocalPlayerServerIndex) {
		this.actors = actors;
		this.regions = regions;
		this.gameCycle = gameCycle;
		this.currentPlane = currentPlane;
		this.setCurrentPlane = setCurrentPlane;
		this.loginUsername = loginUsername;
		this.chatBuffer = chatBuffer;
		this.chatHandler = chatHandler;
		this.setAccountMembershipStatus = setAccountMembershipStatus;
		this.setLocalPlayerServerIndex = setLocalPlayerServerIndex;
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
			for (int playerIndex = 0; playerIndex < actors.players.length; playerIndex++)
				if (actors.players[playerIndex] != null)
					actors.players[playerIndex].sequence = -1;

			for (int npcIndex = 0; npcIndex < actors.npcs.length; npcIndex++)
				if (actors.npcs[npcIndex] != null)
					actors.npcs[npcIndex].sequence = -1;
			return true;
		}
		if (opcode == IncomingPacketOpcode.NPC_UPDATE) {
			actors.decodeNpcUpdate(buffer, packetSize, gameCycle.getAsInt(),
					loginUsername.get());
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_LOCAL_PLAYER_INDEX) {
			setAccountMembershipStatus.accept(buffer.readUnsignedByte());
			setLocalPlayerServerIndex.accept(buffer.readUnsignedShortLE());
			return true;
		}
		if (opcode == IncomingPacketOpcode.PLAYER_UPDATE) {
			setCurrentPlane.accept(actors.decodePlayerUpdate(buffer, packetSize,
					gameCycle.getAsInt(), currentPlane.getAsInt(), loginUsername.get(), chatBuffer, chatHandler));
			regions.playerUpdateReceived();
			return true;
		}
		throw new IllegalArgumentException("Opcode " + opcode + " is not a actor packet");
	}
}
