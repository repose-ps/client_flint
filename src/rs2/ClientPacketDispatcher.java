package rs2;

import rs2.net.Buffer;
import rs2.net.IncomingPacketHandler;
import rs2.net.IncomingPacketOpcode;
import rs2.packet.PacketDomainDispatcher;
import rs2.sign.Signlink;

/**
 * Package-level incoming-packet adapter for the application coordinator.
 *
 * <p>
 * This is the only packet application type allowed to see package-level
 * {@link client} state. It translates that state into narrow domain bindings
 * and delegates recognized packet effects to {@link PacketDomainDispatcher}.
 * </p>
 */
final class ClientPacketDispatcher implements IncomingPacketHandler {
	/** Application coordinator exposed only at this package boundary. */
	private final client client;
	/** Packet-domain facade built from narrow bindings. */
	private final PacketDomainDispatcher domains;

	/**
	 * Creates and wires the packet-domain facade for one client instance.
	 *
	 * @param client application coordinator
	 */
	ClientPacketDispatcher(client client) {
		this.client = client;
		domains = new PacketDomainDispatcher(
				new PacketDomainDispatcher.InterfaceBindings(client.interfaceController, client.widgetRuntime,
						client.chatController, () -> client.localPlayer, client.playerActions,
						client.playerActionLowPriority, client::unloadInterface, client.interfaceRedrawSink),
				new PacketDomainDispatcher.SocialBindings(client.socialManager, client.chatController,
						() -> client.tutorialIslandFlag, () -> client.currentWorldId, client::addChatMessage,
						(lastPasswordChange, currentDay, unreadMessages, loginDay, memberDays, loginIp,
								recoveryDate) -> {
							client.lastPasswordChangeDate = lastPasswordChange;
							client.accountCurrentDay = currentDay;
							client.unreadMessageCount = unreadMessages;
							client.lastLoginDay = loginDay;
							client.membershipDays = memberDays;
							client.lastLoginIp = loginIp;
							client.recoveryQuestionsDate = recoveryDate;
						}, client.gameRenderer::requestChatModesRedraw, client.gameRenderer::requestChatboxRedraw,
						client.gameRenderer::requestSidebarRedraw),
				new PacketDomainDispatcher.RegionBindings(client.regionManager, client.lifecycle::onDemandFetcher,
						client.actorSynchronizer, () -> client.worldState, () -> client.zoneUpdates,
						client.cameraController, new PacketDomainDispatcher.RegionState() {
							@Override
							public int currentPlane() {
								return client.currentPlane;
							}

							@Override
							public int gameCycle() {
								return client.gameCycle;
							}

							@Override
							public int localPlayerServerIndex() {
								return client.localPlayerServerIndex;
							}

							@Override
							public rs2.game.entity.Player localPlayer() {
								return client.localPlayer;
							}

							@Override
							public int destinationX() {
								return client.destinationX;
							}

							@Override
							public int destinationY() {
								return client.destinationY;
							}

							@Override
							public void setDestination(int x, int y) {
								client.destinationX = x;
								client.destinationY = y;
							}

							@Override
							public void setMultiCombatZone(int value) {
								client.multiCombatZone = value;
							}
						},
						(soundId, loops, radius, tileX, tileY) -> client.soundEffectQueue.queueAreaSound(soundId, loops,
								radius, tileX, tileY, client.localPlayer.pathX[0], client.localPlayer.pathY[0],
								client.lowMemory),
						() -> client.drawGameLoadingMessage(null, "Loading - please wait.")),
				new PacketDomainDispatcher.CameraBindings(client.cameraController, () -> client.worldState,
						() -> client.currentPlane, new PacketDomainDispatcher.HintSink() {
							@Override
							public void setType(int type) {
								client.hintIconType = type;
							}

							@Override
							public void setNpcIndex(int index) {
								client.hintNpcIndex = index;
							}

							@Override
							public void setTileHint(int tileX, int tileY, int height, int offsetX, int offsetY) {
								client.hintIconType = 2;
								client.hintTileX = tileX;
								client.hintTileY = tileY;
								client.hintHeight = height;
								client.hintOffsetX = offsetX;
								client.hintOffsetY = offsetY;
							}

							@Override
							public void setPlayerIndex(int index) {
								client.hintPlayerIndex = index;
							}
						}),
				new PacketDomainDispatcher.AudioBindings(client.soundEffectQueue, client.musicController,
						client.lifecycle::onDemandFetcher, () -> client.lowMemory),
				new PacketDomainDispatcher.ActorBindings(client.actorSynchronizer, client.regionManager,
						() -> client.gameCycle, () -> client.currentPlane, value -> client.currentPlane = value,
						() -> client.loginScreen.username, client.chatBuffer, client.actorChatHandler,
						value -> client.accountMembershipStatus = value,
						value -> client.localPlayerServerIndex = value),
				new PacketDomainDispatcher.ClientStateBindings(client.varpState, client.interfaceController,
						client.minimapRenderer, client::applyVarp, client.gameRenderer::requestSidebarRedraw,
						client.gameRenderer::requestChatboxRedraw, value -> client.weight = value,
						client.skillExperiences, client.currentSkillLevels, client.baseSkillLevels,
						client.experienceTable, value -> client.systemUpdateTimer = value,
						value -> client.runEnergy = value));
	}

	@Override
	public boolean handle(int opcode, Buffer buffer, int packetSize) {
		if (opcode == IncomingPacketOpcode.LOGOUT) {
			client.logout();
			return false;
		}
		if (domains.handle(opcode, buffer, packetSize)) {
			return true;
		}
		Signlink.reportError("T1 - " + opcode + "," + packetSize + " - " + client.networkSession.secondLastOpcode + ","
				+ client.networkSession.thirdLastOpcode);
		client.logout();
		return true;
	}
}
