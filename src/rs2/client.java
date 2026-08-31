package rs2;

import rs2.scene.SceneConfig;
import rs2.scene.SceneConstants;
import rs2.media.Angle;
import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.image.BufferedImage;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Calendar;
import java.util.Date;
import rs2.net.IncomingPacketDispatcher;
import rs2.net.OutgoingPacketOpcode;
import rs2.net.ProtocolConstants;
import rs2.ui.WidgetContentType;

import rs2.cache.Archive;
import rs2.cache.ResourceLoader;
import rs2.cache.cfg.Varbit;
import rs2.cache.cfg.Varp;
import rs2.cache.def.FloorDefinition;
import rs2.cache.def.GameObjectDefinition;
import rs2.cache.def.ItemDefinition;
import rs2.cache.def.NpcDefinition;
import rs2.cache.def.AnimationSequence;
import rs2.cache.def.IdentityKit;
import rs2.media.sprite.ImageRGB;
import rs2.media.sprite.IndexedImage;
import rs2.cache.def.SpotAnimation;
import rs2.cache.ondemand.OnDemandFetcher;
import rs2.cache.ondemand.OnDemandRequest;
import rs2.ui.Widget;
import rs2.chat.ChatCodec;
import rs2.chat.ChatController;
import rs2.chat.ChatMessageType;
import rs2.chat.ChatMode;
import rs2.chat.SocialManager;
import rs2.chat.Censor;
import rs2.collection.NodeDeque;
import rs2.game.Skills;
import rs2.game.ActorSynchronizer;
import rs2.game.CameraController;
import rs2.game.ActorUpdater;
import rs2.game.Pathfinder;
import rs2.game.RegionManager;
import rs2.game.SceneEntityRenderer;
import rs2.game.MinimapRenderer;
import rs2.game.WorldState;
import rs2.game.VarpState;
import rs2.game.ZoneUpdateHandler;
import rs2.input.MouseRecorder;
import rs2.media.AnimationFrame;
import rs2.media.GraphicsBuffer;
import rs2.media.sprite.ItemSpriteFactory;
import rs2.media.Rasterizer;
import rs2.media.Rasterizer3D;
import rs2.media.TypeFace;
import rs2.game.entity.Actor;
import rs2.scene.entity.DynamicObject;
import rs2.scene.entity.GroundItem;
import rs2.media.model.Model;
import rs2.game.entity.Npc;
import rs2.game.entity.Player;
import rs2.net.Buffer;
import rs2.net.ChatPacketEncoder;
import rs2.net.BufferedConnection;
import rs2.net.Ipv4Address;
import rs2.net.LoginSession;
import rs2.net.NetworkSession;
import rs2.net.MovementPacketEncoder;
import rs2.scene.Region;
import rs2.scene.Scene;
import rs2.scene.SceneUid;
import rs2.sign.Signlink;
import rs2.sound.MusicController;
import rs2.sound.SoundEffectQueue;
import rs2.sound.SoundTrack;
import rs2.text.Base37;
import rs2.text.TextFormatter;
import rs2.ui.AppearanceEditor;
import rs2.ui.ClientScriptContext;
import rs2.ui.InterfaceController;
import rs2.ui.WidgetRuntime;
import rs2.ui.WidgetRenderer;
import rs2.ui.menu.MenuState;
import rs2.ui.menu.MenuController;
import rs2.ui.login.LoginScreen;
import rs2.ui.login.TitleFlameAnimator;

/**
 * Standalone revision-377 game client coordinator.
 *
 * <p>
 * The class retains the original client protocol, rendering, timing, UI, and
 * gameplay behavior while delegating well-bounded subsystems to the semantic
 * owners extracted during earlier refactor steps.
 * </p>
 */
public class Client extends GameShell {

	/** Maximum X coordinate encoded by legacy revision-377 mouse telemetry. */
	private static final int LEGACY_MOUSE_MAX_X = ClientLayout.FIXED_WIDTH - 1;
	/** Maximum Y coordinate encoded by legacy revision-377 mouse telemetry. */
	private static final int LEGACY_MOUSE_MAX_Y = ClientLayout.FIXED_HEIGHT - 1;
	/** Packed mouse position used when the pointer is outside the client. */
	private static final int MOUSE_OUTSIDE_POSITION = 0x7ffff;
	/** Largest repeat count representable by the mouse telemetry formats. */
	private static final int MOUSE_REPEAT_MAX = 0x7ff;
	/** Repeat-count boundary for the compact mouse telemetry formats. */
	private static final int MOUSE_COMPACT_REPEAT_LIMIT = 8;
	/** Minimum delta represented by the compact two-byte mouse format. */
	private static final int MOUSE_COMPACT_DELTA_MIN = -32;
	/** Maximum delta represented by the compact two-byte mouse format. */
	private static final int MOUSE_COMPACT_DELTA_MAX = 31;
	/** Bias that converts compact signed mouse deltas to six-bit unsigned values. */
	private static final int MOUSE_COMPACT_DELTA_BIAS = 32;
	/** Shift of the repeat count in the compact two-byte mouse format. */
	private static final int MOUSE_SHORT_REPEAT_SHIFT = 12;
	/** Shift of the X delta in the compact two-byte mouse format. */
	private static final int MOUSE_SHORT_X_SHIFT = 6;
	/** Flag identifying the medium absolute-position mouse format. */
	private static final int MOUSE_MEDIUM_FLAG = 0x800000;
	/** Flag identifying the four-byte absolute-position mouse format. */
	private static final int MOUSE_INT_FLAG = 0xc0000000;
	/** Shift of the repeat count in absolute-position mouse formats. */
	private static final int MOUSE_ABSOLUTE_REPEAT_SHIFT = 19;
	/** Maximum encoded delay between click telemetry packets. */
	private static final int CLICK_DELAY_MAX = 0xfff;
	/** Shift of the click delay in the packed click telemetry word. */
	private static final int CLICK_DELAY_SHIFT = 20;
	/** Shift of the right-click flag in the packed click telemetry word. */
	private static final int CLICK_BUTTON_SHIFT = 19;
	/** Maximum payload bytes accumulated in one mouse-movement packet. */
	private static final int MOUSE_PACKET_PAYLOAD_LIMIT = 240;
	/** Recorded sample count that forces a mouse-movement packet. */
	private static final int MOUSE_PACKET_SAMPLE_THRESHOLD = 40;


	/**
	 * Layout.
	 *
	 */
	private final ClientLayout layout = new ClientLayout();
	/** Complete client frame composed off-screen before one AWT presentation blit. */
	private BufferedImage presentationBuffer;

	/**
	 * Searches loaded item definitions for names containing all supplied query
	 * terms.
	 *
	 * @param query the item-name search query
	 */
	public void searchItems(String query) {
		if (query == null || query.length() == 0) {
			itemSearchResultCount = 0;
			return;
		}
		String remainingQuery = query;
		String searchTerms[] = new String[100];
		int searchTermCount = 0;
		do {
			int spaceIndex = remainingQuery.indexOf(" ");
			if (spaceIndex == -1)
				break;
			String searchTerm = remainingQuery.substring(0, spaceIndex).trim();
			if (searchTerm.length() > 0)
				searchTerms[searchTermCount++] = searchTerm.toLowerCase();
			remainingQuery = remainingQuery.substring(spaceIndex + 1);
		} while (true);
		remainingQuery = remainingQuery.trim();
		if (remainingQuery.length() > 0)
			searchTerms[searchTermCount++] = remainingQuery.toLowerCase();
		itemSearchResultCount = 0;
		label0: for (int itemId = 0; itemId < ItemDefinition.count; itemId++) {
			ItemDefinition itemDefinition = ItemDefinition.lookup(itemId);
			if (itemDefinition.noteTemplateId != -1 || itemDefinition.name == null)
				continue;
			String lowercaseItemName = itemDefinition.name.toLowerCase();
			for (int searchTermIndex = 0; searchTermIndex < searchTermCount; searchTermIndex++)
				if (lowercaseItemName.indexOf(searchTerms[searchTermIndex]) == -1)
					continue label0;

			itemSearchResultNames[itemSearchResultCount] = lowercaseItemName;
			itemSearchResultIds[itemSearchResultCount] = itemId;
			itemSearchResultCount++;
			if (itemSearchResultCount >= itemSearchResultNames.length)
				return;
		}

	}

	/**
	 * Processes clicks on the fixed chat-mode strip. Public chat cycles through On,
	 * Friends, Off, and Hide; private chat and trade each cycle through On,
	 * Friends, and Off. The Report abuse button opens the cache-defined report
	 * interface. Changed chat modes are sent to the server using revision-377
	 * opcode 176.
	 */
	public void processChatModeClick() {
		if (super.clickButton != 1)
			return;

		int button = layout.chatModeButtonAt(super.clickX, super.clickY);
		boolean changed = false;

		if (button == ClientLayout.CHAT_MODE_PUBLIC) {
			chatController.setPublicMode((chatController.publicMode() + 1) % ChatMode.PUBLIC_MODE_COUNT);
			changed = true;
		} else if (button == ClientLayout.CHAT_MODE_PRIVATE) {
			chatController.setPrivateMode((chatController.privateMode() + 1) % ChatMode.STANDARD_MODE_COUNT);
			changed = true;
		} else if (button == ClientLayout.CHAT_MODE_TRADE) {
			chatController.setTradeMode((chatController.tradeMode() + 1) % ChatMode.STANDARD_MODE_COUNT);
			changed = true;
		} else if (button == ClientLayout.CHAT_MODE_REPORT_ABUSE) {
			if (interfaceController.state().openInterfaceId == -1) {
				closeInterfaces();
						interfaceController.setReportAbuseMutePlayer(false);
				interfaceController.state().reportAbuseInterfaceId = interfaceController.state().openInterfaceId = Widget.reportAbuseInterfaceId;
			} else {
				addChatMessage("", "Please close the interface you have open before using 'report abuse'", ChatMessageType.GAME);
			}
			return;
		}

		if (!changed)
			return;

		chatModesRedraw = true;
		chatboxRedraw = true;
		ChatPacketEncoder.writeChatModes(networkSession.outgoing, chatController.publicMode(), chatController.privateMode(), chatController.tradeMode());
	}

	/** Closes all open interface groups through {@link InterfaceController}. */
	public void closeInterfaces() {
		interfaceController.closeAll(networkSession.outgoing, interfaceRedrawSink);
	}

	/**
	 * Starts the standalone revision-377 client with command-line world, port, and
	 * memory settings.
	 *
	 * @param args the command-line arguments
	 */
	public static void main(String args[]) {
		try {
			System.out.println("RS2 user Client - release #" + 377);
			if (args.length < 5 || args.length > 7) {
				System.out.println(
						"Usage: node-id, port-offset, [lowmem/highmem], [free/members], storeid, [server-host], [cache-directory]");
				return;
			}
			currentWorldId = Integer.parseInt(args[0]);
			portOffset = Integer.parseInt(args[1]);
			if (args[2].equals("lowmem"))
				setLowMemory();
			else if (args[2].equals("highmem")) {
				setHighMemory();
			} else {
				System.out.println(
						"Usage: node-id, port-offset, [lowmem/highmem], [free/members], storeid, [server-host], [cache-directory]");
				return;
			}
			if (args[3].equals("free"))
				membersWorld = false;
			else if (args[3].equals("members")) {
				membersWorld = true;
			} else {
				System.out.println(
						"Usage: node-id, port-offset, [lowmem/highmem], [free/members], storeid, [server-host], [cache-directory]");
				return;
			}
			Signlink.storeId = Integer.parseInt(args[4]);
			setServerHost(args.length >= 6 ? args[5] : InetAddress.getLocalHost().getHostAddress());
			if (args.length >= 7)
				Signlink.setCacheDirectory(args[6]);
			Signlink.start(InetAddress.getByName(serverHost));
			Client client1 = new Client();
			client1.createFrame(ClientLayout.FIXED_WIDTH, ClientLayout.FIXED_HEIGHT);
			return;
		} catch (Exception exception) {
			return;
		}
	}



	/**
	 * Marks startup as failed and displays the supplied loading-error reason.
	 *
	 * @param message the message text
	 */
	public void haltOnLoadError(String message) {
		System.out.println(message);
		do
			try {
				Thread.sleep(1000L);
			} catch (Exception ignored) {
			}
		while (true);
	}

	/**
	 * Formats an item stack amount using the renderer's revision-377 notation.
	 *
	 * @param amount numeric amount
	 * @return formatted amount
	 */
	public static String formatItemStackAmount(int amount) {
		return WidgetRenderer.formatItemStackAmount(amount);
	}

	/**
	 * Releases client resources, subsystem state, caches, and platform connections
	 * during shutdown.
	 */
	public void cleanUpForQuit() {
		if (mouseRecorder != null) {
			mouseRecorder.stop();
			mouseRecorder = null;
		}
		if (onDemandFetcher != null) {
			onDemandFetcher.stop();
			onDemandFetcher = null;
		}
		disposeTitleScreen();
		if (networkSession != null) {
			networkSession.closeConnection();
			networkSession = null;
		}

		backLeft1Buffer = null;
		backLeft2Buffer = null;
		backRight1Buffer = null;
		backRight2Buffer = null;
		redstone1 = null;
		redstone2 = null;
		redstone3 = null;
		redstone1Horizontal = null;
		redstone2Horizontal = null;
		redstone1Vertical = null;
		redstone2Vertical = null;
		redstone3Vertical = null;
		redstone1Both = null;
		redstone2Both = null;
		socialManager.clearFriendReferencesForQuit();
		chatModesBuffer = null;
		bottomTabsBuffer = null;
		topTabsBuffer = null;
		regionManager.clear();
		titleLeftBottomBuffer = null;
		titleRightBottomBuffer = null;
		titleLeftCenterBuffer = null;
		titleRightCenterBuffer = null;
		groundItemMapDot = null;
		npcMapDot = null;
		playerMapDot = null;
		friendMapDot = null;
		teamMapDot = null;
		chatModesBackground = null;
		bottomTabBackground = null;
		topTabBackground = null;
		backTop1Buffer = null;
		backVerticalMiddle1Buffer = null;
		backVerticalMiddle2Buffer = null;
		backVerticalMiddle3Buffer = null;
		backHorizontalMiddle2Buffer = null;
		worldState = null;
		zoneUpdates = null;
		minimapRenderer.clear();
		titleLeftFlameBuffer = null;
		titleRightFlameBuffer = null;
		titleTopBuffer = null;
		titleBottomBuffer = null;
		loginBoxBuffer = null;
		compassSprite = null;
		hitmarkSprites = null;
		skullIconSprites = null;
		prayerIconSprites = null;
		hintIconSprites = null;
		crossSprites = null;
		musicController.stop();
		sidebarBuffer = null;
		minimapBuffer = null;
		viewportBuffer = null;
		chatboxBuffer = null;
		sidebarBackground = null;
		minimapBackground = null;
		chatboxBackground = null;
		textureScrollScratch = null;
		chatBuffer = null;
		mapSceneSprites = null;
		mapFunctionSprites = null;
		sidebarIcons = null;
		multiCombatOverlay = null;
		menuController.state().clearReferencesForQuit();
		GameObjectDefinition.clear();
		NpcDefinition.clear();
		ItemDefinition.clear();
		Widget.clear();
		FloorDefinition.definitions = null;
		IdentityKit.definitions = null;
		AnimationSequence.sequences = null;
		SpotAnimation.definitions = null;
		SpotAnimation.modelCache = null;
		Varp.definitions = null;
		super.gameBuffer = null;
		Player.modelCache = null;
		Rasterizer3D.clear();
		Scene.clearStatic();
		Model.clearModelLoader();
		AnimationFrame.clear();
		Signlink.shutdown();
		System.gc();
	}

	/**
	 * Processes clicks on the fixed sidebar-tab hit regions.
	 */
	public void processTabClick() {
		if (super.clickButton != 1)
			return;

		for (int tab = 0; tab < ClientLayout.TAB_COUNT; tab++) {
			if (!layout.isTabHit(tab, super.clickX, super.clickY) || interfaceController.state().tabInterfaceIds[tab] == -1)
				continue;
			sidebarRedraw = true;
			interfaceController.state().selectedTab = tab;
			tabAreaRedraw = true;
		}
	}

	/**
	 * Updates the normal follow-camera focal point, yaw, pitch, and distance.
	 */
	private void updateCameraFollow() {
		cameraController.updateFollow(localPlayer, keyStatus, worldState, currentPlane, regionManager.regionX,
				regionManager.regionY, regionManager.baseX, regionManager.baseY);

		if (!cameraController.cinematic && (super.cameraDragDeltaX != 0 || super.cameraDragDeltaY != 0)) {
			cameraController.rotateFollowByMouse(super.cameraDragDeltaX, super.cameraDragDeltaY);
			cameraOrientationChanged = true;
		}
	}

	/** Delegates social-list menu construction to {@link MenuController}.
	 * @param widget social-list widget
	 * @return whether the widget supplied a social menu
	 */
	public boolean buildSocialWidgetMenu(Widget widget) {
		return menuController.buildSocialWidgetMenu(widget);
	}

	/** Resets appearance-kit selections for the currently selected sex. */
	public void resetCharacterAppearance() {
		appearanceEditor.resetKits();
	}

	/**
	 * Enables the original high-memory configuration across rendering and region
	 * subsystems.
	 */
	public static void setHighMemory() {
		Scene.lowMemory = false;
		Rasterizer3D.lowMemory = false;
		lowMemory = false;
		Region.lowMemory = false;
		GameObjectDefinition.lowMemory = false;
	}

	/**
	 * Processes one logged-in client tick, including input telemetry, interface
	 * drag state, packets, actors, camera, audio, and keepalives.
	 */
	public void processLoggedInCycle() {
		if (systemUpdateTimer > 1)
			systemUpdateTimer--;
		if (logoutTimer > 0)
			logoutTimer--;
		for (int idleThreshold = 0; idleThreshold < 5; idleThreshold++)
			if (!processIncomingPacket())
				break;

		if (!loggedIn)
			return;
		synchronized (mouseRecorder.lock) {
			if (accountFlagged) {
				if (super.clickButton != 0 || mouseRecorder.sampleCount >= MOUSE_PACKET_SAMPLE_THRESHOLD) {
					networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.MOUSE_MOVEMENT);
					networkSession.outgoing.writeByte(0);
					int packetStart = networkSession.outgoing.position;
					int encodedSampleCount = 0;
					for (int sampleIndex = 0; sampleIndex < mouseRecorder.sampleCount; sampleIndex++) {
						if (packetStart - networkSession.outgoing.position >= MOUSE_PACKET_PAYLOAD_LIMIT)
							break;
						encodedSampleCount++;
						int mouseY = mouseRecorder.yCoordinates[sampleIndex];
						if (mouseY < 0)
							mouseY = 0;
						else if (mouseY > LEGACY_MOUSE_MAX_Y)
							mouseY = LEGACY_MOUSE_MAX_Y;
						int mouseX = mouseRecorder.xCoordinates[sampleIndex];
						if (mouseX < 0)
							mouseX = 0;
						else if (mouseX > LEGACY_MOUSE_MAX_X)
							mouseX = LEGACY_MOUSE_MAX_X;
						int packedPosition = mouseY * ClientLayout.FIXED_WIDTH + mouseX;
						if (mouseRecorder.yCoordinates[sampleIndex] == -1
								&& mouseRecorder.xCoordinates[sampleIndex] == -1) {
							mouseX = -1;
							mouseY = -1;
							packedPosition = MOUSE_OUTSIDE_POSITION;
						}
						if (mouseX == lastRecordedMouseX && mouseY == lastRecordedMouseY) {
							if (mouseTelemetryRepeatCount < MOUSE_REPEAT_MAX)
								mouseTelemetryRepeatCount++;
						} else {
							int deltaX = mouseX - lastRecordedMouseX;
							lastRecordedMouseX = mouseX;
							int deltaY = mouseY - lastRecordedMouseY;
							lastRecordedMouseY = mouseY;
							if (mouseTelemetryRepeatCount < MOUSE_COMPACT_REPEAT_LIMIT && deltaX >= MOUSE_COMPACT_DELTA_MIN && deltaX <= MOUSE_COMPACT_DELTA_MAX && deltaY >= MOUSE_COMPACT_DELTA_MIN
									&& deltaY <= MOUSE_COMPACT_DELTA_MAX) {
								deltaX += MOUSE_COMPACT_DELTA_BIAS;
								deltaY += MOUSE_COMPACT_DELTA_BIAS;
								networkSession.outgoing
										.writeShort((mouseTelemetryRepeatCount << MOUSE_SHORT_REPEAT_SHIFT) + (deltaX << MOUSE_SHORT_X_SHIFT) + deltaY);
								mouseTelemetryRepeatCount = 0;
							} else if (mouseTelemetryRepeatCount < MOUSE_COMPACT_REPEAT_LIMIT) {
								networkSession.outgoing
										.writeMedium(MOUSE_MEDIUM_FLAG + (mouseTelemetryRepeatCount << MOUSE_ABSOLUTE_REPEAT_SHIFT) + packedPosition);
								mouseTelemetryRepeatCount = 0;
							} else {
								networkSession.outgoing
										.writeInt(MOUSE_INT_FLAG + (mouseTelemetryRepeatCount << MOUSE_ABSOLUTE_REPEAT_SHIFT) + packedPosition);
								mouseTelemetryRepeatCount = 0;
							}
						}
					}

					networkSession.outgoing.writeLength(networkSession.outgoing.position - packetStart);
					if (encodedSampleCount >= mouseRecorder.sampleCount) {
						mouseRecorder.sampleCount = 0;
					} else {
						mouseRecorder.sampleCount -= encodedSampleCount;
						for (int duplicateSamples = 0; duplicateSamples < mouseRecorder.sampleCount; duplicateSamples++) {
							mouseRecorder.xCoordinates[duplicateSamples] = mouseRecorder.xCoordinates[duplicateSamples
									+ encodedSampleCount];
							mouseRecorder.yCoordinates[duplicateSamples] = mouseRecorder.yCoordinates[duplicateSamples
									+ encodedSampleCount];
						}

					}
				}
			} else {
				mouseRecorder.sampleCount = 0;
			}
		}
		if (super.clickButton != 0) {
			long clickDelayTicks = (super.clickTime - lastClickTime) / 50L;
			if (clickDelayTicks > CLICK_DELAY_MAX)
				clickDelayTicks = CLICK_DELAY_MAX;
			lastClickTime = super.clickTime;
			int clickY = super.clickY;
			if (clickY < 0)
				clickY = 0;
			else if (clickY > LEGACY_MOUSE_MAX_Y)
				clickY = LEGACY_MOUSE_MAX_Y;
			int clickX = super.clickX;
			if (clickX < 0)
				clickX = 0;
			else if (clickX > LEGACY_MOUSE_MAX_X)
				clickX = LEGACY_MOUSE_MAX_X;
			int packedClickPosition = clickY * ClientLayout.FIXED_WIDTH + clickX;
			int clickButton = 0;
			if (super.clickButton == 2)
				clickButton = 1;
			int encodedClickDelay = (int) clickDelayTicks;
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.MOUSE_CLICK);
			networkSession.outgoing.writeInt((encodedClickDelay << CLICK_DELAY_SHIFT) + (clickButton << CLICK_BUTTON_SHIFT) + packedClickPosition);
		}
		if (cameraPacketCooldown > 0)
			cameraPacketCooldown--;
		if (super.keyStatus[1] == 1 || super.keyStatus[2] == 1 || super.keyStatus[3] == 1 || super.keyStatus[4] == 1)
			cameraOrientationChanged = true;
		if (cameraOrientationChanged && cameraPacketCooldown <= 0) {
			cameraPacketCooldown = 20;
			cameraOrientationChanged = false;
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.CAMERA_ORIENTATION);
			networkSession.outgoing.writeShortLE(cameraController.followPitch);
			networkSession.outgoing.writeShortLE(cameraController.followYaw);
		}
		if (super.hasFocus && !windowFocusReported) {
			windowFocusReported = true;
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.WINDOW_FOCUS);
			networkSession.outgoing.writeByte(1);
		}
		if (!super.hasFocus && windowFocusReported) {
			windowFocusReported = false;
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.WINDOW_FOCUS);
			networkSession.outgoing.writeByte(0);
		}
		updateRegionLoading();
		if (regionManager.loadingStage == RegionManager.STAGE_LOADED)
			worldState.updatePendingSpawns(lowMemory, currentPlane);
		soundEffectQueue.update(networkSession.outgoing);
		musicController.updateResumeDelay(lowMemory, onDemandFetcher::request);
		networkSession.incomingIdleCycles++;
		if (networkSession.incomingIdleCycles > 750)
			reconnect();
		actorSynchronizer.updatePlayers(actorUpdater, gameCycle, localPlayerServerIndex, regionManager.baseX,
				regionManager.baseY);
		actorSynchronizer.updateNpcs(actorUpdater, gameCycle, localPlayerServerIndex, regionManager.baseX,
				regionManager.baseY);
		updateOverheadTextCycles();
		animationCycleDelta++;
		if (crossType != 0) {
			crossCycle += 20;
			if (crossCycle >= 400)
				crossType = 0;
		}
		if (interfaceController.state().pressedInventoryArea != 0) {
			inventoryClickCycle++;
			if (inventoryClickCycle >= 15) {
				if (interfaceController.state().pressedInventoryArea == 2)
					sidebarRedraw = true;
				if (interfaceController.state().pressedInventoryArea == 3)
					chatboxRedraw = true;
				interfaceController.state().pressedInventoryArea = 0;
			}
		}
		if (interfaceController.state().inventoryDragArea != 0) {
			interfaceController.state().inventoryDragDuration++;
			if (super.mouseX > interfaceController.state().inventoryDragStartX + 5
					|| super.mouseX < interfaceController.state().inventoryDragStartX - 5
					|| super.mouseY > interfaceController.state().inventoryDragStartY + 5
					|| super.mouseY < interfaceController.state().inventoryDragStartY - 5)
				inventoryDragMoved = true;
			if (super.mouseButton == 0) {
				if (interfaceController.state().inventoryDragArea == 2)
					sidebarRedraw = true;
				if (interfaceController.state().inventoryDragArea == 3)
					chatboxRedraw = true;
				interfaceController.state().inventoryDragArea = 0;
				if (inventoryDragMoved && interfaceController.state().inventoryDragDuration >= 5) {
					interfaceController.state().hoveredInventoryWidgetId = -1;
					buildContextMenu();
					if (interfaceController.state().hoveredInventoryWidgetId == interfaceController.state().draggedInventoryWidgetId
							&& interfaceController.state().hoveredInventorySlot != interfaceController.state().draggedInventorySlot) {
						Widget inventoryWidget = Widget.get(interfaceController.state().draggedInventoryWidgetId);
						// Legacy drag mode: 0 swaps/moves directly; 1 performs insertion-style
						// shifting.
						int insertionMode = 0;
						if (inventoryRearrangeMode == 1 && inventoryWidget.contentType == WidgetContentType.INSERTABLE_INVENTORY)
							insertionMode = 1;
						if (inventoryWidget.itemIds[interfaceController.state().hoveredInventorySlot] <= 0)
							insertionMode = 0;
						if (inventoryWidget.inventoryReplaceItems) {
							int sourceSlot = interfaceController.state().draggedInventorySlot;
							int destinationSlot = interfaceController.state().hoveredInventorySlot;
							inventoryWidget.itemIds[destinationSlot] = inventoryWidget.itemIds[sourceSlot];
							inventoryWidget.itemAmounts[destinationSlot] = inventoryWidget.itemAmounts[sourceSlot];
							inventoryWidget.itemIds[sourceSlot] = -1;
							inventoryWidget.itemAmounts[sourceSlot] = 0;
						} else if (insertionMode == 1) {
							int movingSlot = interfaceController.state().draggedInventorySlot;
							for (int targetSlot = interfaceController.state().hoveredInventorySlot; movingSlot != targetSlot;)
								if (movingSlot > targetSlot) {
									inventoryWidget.swapItems(movingSlot - 1, movingSlot);
									movingSlot--;
								} else if (movingSlot < targetSlot) {
									inventoryWidget.swapItems(movingSlot + 1, movingSlot);
									movingSlot++;
								}

						} else {
							inventoryWidget.swapItems(interfaceController.state().hoveredInventorySlot,
									interfaceController.state().draggedInventorySlot);
						}
						networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.REORDER_INVENTORY_ITEM);
						networkSession.outgoing.writeShortAddLE(interfaceController.state().hoveredInventorySlot);
						networkSession.outgoing.writeByteAdd(insertionMode);
						networkSession.outgoing.writeShortAdd(interfaceController.state().draggedInventoryWidgetId);
						networkSession.outgoing.writeShortLE(interfaceController.state().draggedInventorySlot);
					}
				} else if ((oneButtonMouseMode == 1 || isAddFriendMenuAction(menuController.state().count - 1))
						&& menuController.state().count > 2)
					openContextMenu();
				else if (menuController.state().count > 0)
					dispatchMenuAction(menuController.state().count - 1);
				inventoryClickCycle = 10;
				super.clickButton = 0;
			}
		}
		if (Scene.pickedTileX != -1) {
			int pickedTileX = Scene.pickedTileX;
			int pickedTileY = Scene.pickedTileY;
			boolean routeFound = walkTo(true, pickedTileX, pickedTileY, 0, 0, MovementPacketEncoder.SCREEN, 0, 0, 0);
			Scene.pickedTileX = -1;
			if (routeFound) {
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 1;
				crossCycle = 0;
			}
		}
		if (super.clickButton == 1 && chatController.clickToContinueMessage() != null) {
			chatController.setClickToContinueMessage(null);
			chatboxRedraw = true;
			super.clickButton = 0;
		}
		processMenuClick();
		if (interfaceController.state().fullscreenInterfaceId == -1) {
			processMinimapClick();
			processTabClick();
		}
		processChatModeClick();
		if (super.mouseButton == 1 || super.clickButton == 1)
			mouseButtonHoldTicks++;
		if (interfaceController.chatboxTooltipWidgetId() != 0 || interfaceController.sidebarTooltipWidgetId() != 0 || interfaceController.viewportTooltipWidgetId() != 0) {
			if (interfaceController.tooltipHoverTicks() < 100) {
				interfaceController.setTooltipHoverTicks(interfaceController.tooltipHoverTicks() + 1);
				if (interfaceController.tooltipHoverTicks() == 100) {
					if (interfaceController.chatboxTooltipWidgetId() != 0)
						chatboxRedraw = true;
					if (interfaceController.sidebarTooltipWidgetId() != 0)
						sidebarRedraw = true;
				}
			}
		} else if (interfaceController.tooltipHoverTicks() > 0)
			interfaceController.setTooltipHoverTicks(interfaceController.tooltipHoverTicks() - 1);
		if (regionManager.loadingStage == RegionManager.STAGE_LOADED)
			updateCameraFollow();
		if (regionManager.loadingStage == RegionManager.STAGE_LOADED && cameraController.cinematic)
			updateCinematicCamera();
		cameraController.advanceShakeCycles();

		processKeyboardInput();
		super.idleCycles++;
		if (super.idleCycles > 4500) {
			logoutTimer = 250;
			super.idleCycles -= 500;
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.IDLE);
		}
		cameraController.tickRandomOffsets();
		minimapRenderer.tickRandomOffsets();
		networkSession.outgoingIdleCycles++;
		if (networkSession.outgoingIdleCycles > 50)
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.NO_TIMEOUT);
		try {
			if (networkSession.outgoing.position > 0) {
				networkSession.flushOutgoing();
				return;
			}
		} catch (IOException ignored) {
			reconnect();
			return;
		} catch (Exception exception) {
			logout();
		}
	}

	/**
	 * Advances the scripted/cinematic camera toward its configured position and
	 * look target.
	 */
	private void updateCinematicCamera() {
		cameraController.updateCinematic(worldState, currentPlane);
	}

	/**
	 * Consumes queued keyboard input for prompts, chat, commands, item search, and
	 * dialogue input.
	 */
	public void processKeyboardInput() {
		do {
			int keyCode = pollKey();
			if (keyCode == -1)
				break;
			if (interfaceController.state().openInterfaceId != -1
					&& interfaceController.state().openInterfaceId == interfaceController.state().reportAbuseInterfaceId) {
				if (keyCode == 8 && interfaceController.reportAbuseName().length() > 0)
					interfaceController.setReportAbuseName(interfaceController.reportAbuseName().substring(0, interfaceController.reportAbuseName().length() - 1));
				if ((keyCode >= 97 && keyCode <= 122 || keyCode >= 65 && keyCode <= 90 || keyCode >= 48 && keyCode <= 57
						|| keyCode == 32) && interfaceController.reportAbuseName().length() < 12)
					interfaceController.setReportAbuseName(interfaceController.reportAbuseName() + (char) keyCode);
			} else if (chatController.isPromptRaised()) {
				if (keyCode >= 32 && keyCode <= 122 && chatController.promptInput().length() < 80) {
					chatController.setPromptInput(chatController.promptInput() + (char) keyCode);
					chatboxRedraw = true;
				}
				if (keyCode == 8 && chatController.promptInput().length() > 0) {
					chatController.setPromptInput(chatController.promptInput().substring(0, chatController.promptInput().length() - 1));
					chatboxRedraw = true;
				}
				if (keyCode == 13 || keyCode == 10) {
					chatController.closePrompt();
					chatboxRedraw = true;
					if (chatController.promptAction() == 1) {
						long encodedName = Base37.encode(chatController.promptInput());
						addFriend(encodedName);
					}
					if (chatController.promptAction() == 2 && socialManager.friendCount > 0) {
						long encodedName2 = Base37.encode(chatController.promptInput());
						removeFriend(encodedName2);
					}
					if (chatController.promptAction() == 3 && chatController.promptInput().length() > 0) {
						ChatPacketEncoder.writePrivateMessage(networkSession.outgoing, chatController.privateMessageTarget(),
								chatController.promptInput());
						chatController.setPromptInput(ChatCodec.normalize(chatController.promptInput()));
						chatController.setPromptInput(Censor.censor(chatController.promptInput()));
						addChatMessage(TextFormatter.formatDisplayName(Base37.decode(chatController.privateMessageTarget())),
								chatController.promptInput(), 6);
						if (chatController.privateMode() == ChatMode.OFF) {
							chatController.setPrivateMode(ChatMode.FRIENDS);
							chatModesRedraw = true;
							ChatPacketEncoder.writeChatModes(networkSession.outgoing, chatController.publicMode(), chatController.privateMode(),
									chatController.tradeMode());
						}
					}
					if (chatController.promptAction() == 4 && socialManager.ignoreCount < 100) {
						long encodedName3 = Base37.encode(chatController.promptInput());
						addIgnore(encodedName3);
					}
					if (chatController.promptAction() == 5 && socialManager.ignoreCount > 0) {
						long encodedName4 = Base37.encode(chatController.promptInput());
						removeIgnore(encodedName4);
					}
				}
			} else if (chatController.inputDialogState() == 1) {
				if (keyCode >= 48 && keyCode <= 57 && chatController.inputDialogText().length() < 10) {
					chatController.setInputDialogText(chatController.inputDialogText() + (char) keyCode);
					chatboxRedraw = true;
				}
				if (keyCode == 8 && chatController.inputDialogText().length() > 0) {
					chatController.setInputDialogText(chatController.inputDialogText().substring(0, chatController.inputDialogText().length() - 1));
					chatboxRedraw = true;
				}
				if (keyCode == 13 || keyCode == 10) {
					if (chatController.inputDialogText().length() > 0) {
						int amount = 0;
						try {
							amount = Integer.parseInt(chatController.inputDialogText());
						} catch (Exception ignored) {
						}
						networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.INPUT_AMOUNT);
						networkSession.outgoing.writeInt(amount);
					}
					chatController.setInputDialogState(0);
					chatboxRedraw = true;
				}
			} else if (chatController.inputDialogState() == 2) {
				if (keyCode >= 32 && keyCode <= 122 && chatController.inputDialogText().length() < 12) {
					chatController.setInputDialogText(chatController.inputDialogText() + (char) keyCode);
					chatboxRedraw = true;
				}
				if (keyCode == 8 && chatController.inputDialogText().length() > 0) {
					chatController.setInputDialogText(chatController.inputDialogText().substring(0, chatController.inputDialogText().length() - 1));
					chatboxRedraw = true;
				}
				if (keyCode == 13 || keyCode == 10) {
					if (chatController.inputDialogText().length() > 0) {
						networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.INPUT_NAME);
						networkSession.outgoing.writeLong(Base37.encode(chatController.inputDialogText()));
					}
					chatController.setInputDialogState(0);
					chatboxRedraw = true;
				}
			} else if (chatController.inputDialogState() == 3) {
				if (keyCode >= 32 && keyCode <= 122 && chatController.inputDialogText().length() < 40) {
					chatController.setInputDialogText(chatController.inputDialogText() + (char) keyCode);
					chatboxRedraw = true;
				}
				if (keyCode == 8 && chatController.inputDialogText().length() > 0) {
					chatController.setInputDialogText(chatController.inputDialogText().substring(0, chatController.inputDialogText().length() - 1));
					chatboxRedraw = true;
				}
			} else if (interfaceController.state().chatboxInterfaceId == -1 && interfaceController.state().fullscreenInterfaceId == -1) {
				if (keyCode >= 32 && keyCode <= 122 && chatController.input().length() < 80) {
					chatController.setInput(chatController.input() + (char) keyCode);
					chatboxRedraw = true;
				}
				if (keyCode == 8 && chatController.input().length() > 0) {
					chatController.setInput(chatController.input().substring(0, chatController.input().length() - 1));
					chatboxRedraw = true;
				}
				if ((keyCode == 13 || keyCode == 10) && chatController.input().length() > 0) {
					if (playerRights == 2) {
						if (chatController.input().equals("::clientdrop"))
							reconnect();
						if (chatController.input().equals("::lag"))
							printDebugInfo();
						if (chatController.input().equals("::prefetchmusic")) {
							for (int midiId = 0; midiId < onDemandFetcher.getFileCount(2); midiId++)
								onDemandFetcher.setExtraPriority(2, midiId, (byte) 1);

						}
						if (chatController.input().equals("::fpson"))
							showFps = true;
						if (chatController.input().equals("::fpsoff"))
							showFps = false;
						if (chatController.input().equals("::noclip")) {
							for (int plane = 0; plane < 4; plane++) {
								for (int tileX = 1; tileX < SceneConstants.INTERIOR_MAX_TILE; tileX++) {
									for (int tileY = 1; tileY < SceneConstants.INTERIOR_MAX_TILE; tileY++)
										worldState.collisionMaps[plane].flags[tileX][tileY] = 0;

								}

							}

						}
					}
					if (chatController.input().startsWith("::")) {
						ChatPacketEncoder.writeCommand(networkSession.outgoing, chatController.input());
					} else {
						String lowercaseInput = chatController.input().toLowerCase();
						int chatColor = 0;
						if (lowercaseInput.startsWith("yellow:")) {
							chatColor = 0;
							chatController.setInput(chatController.input().substring(7));
						} else if (lowercaseInput.startsWith("red:")) {
							chatColor = 1;
							chatController.setInput(chatController.input().substring(4));
						} else if (lowercaseInput.startsWith("green:")) {
							chatColor = 2;
							chatController.setInput(chatController.input().substring(6));
						} else if (lowercaseInput.startsWith("cyan:")) {
							chatColor = 3;
							chatController.setInput(chatController.input().substring(5));
						} else if (lowercaseInput.startsWith("purple:")) {
							chatColor = 4;
							chatController.setInput(chatController.input().substring(7));
						} else if (lowercaseInput.startsWith("white:")) {
							chatColor = 5;
							chatController.setInput(chatController.input().substring(6));
						} else if (lowercaseInput.startsWith("flash1:")) {
							chatColor = 6;
							chatController.setInput(chatController.input().substring(7));
						} else if (lowercaseInput.startsWith("flash2:")) {
							chatColor = 7;
							chatController.setInput(chatController.input().substring(7));
						} else if (lowercaseInput.startsWith("flash3:")) {
							chatColor = 8;
							chatController.setInput(chatController.input().substring(7));
						} else if (lowercaseInput.startsWith("glow1:")) {
							chatColor = 9;
							chatController.setInput(chatController.input().substring(6));
						} else if (lowercaseInput.startsWith("glow2:")) {
							chatColor = 10;
							chatController.setInput(chatController.input().substring(6));
						} else if (lowercaseInput.startsWith("glow3:")) {
							chatColor = 11;
							chatController.setInput(chatController.input().substring(6));
						}
						lowercaseInput = chatController.input().toLowerCase();
						int chatEffect = 0;
						if (lowercaseInput.startsWith("wave:")) {
							chatEffect = 1;
							chatController.setInput(chatController.input().substring(5));
						} else if (lowercaseInput.startsWith("wave2:")) {
							chatEffect = 2;
							chatController.setInput(chatController.input().substring(6));
						} else if (lowercaseInput.startsWith("shake:")) {
							chatEffect = 3;
							chatController.setInput(chatController.input().substring(6));
						} else if (lowercaseInput.startsWith("scroll:")) {
							chatEffect = 4;
							chatController.setInput(chatController.input().substring(7));
						} else if (lowercaseInput.startsWith("slide:")) {
							chatEffect = 5;
							chatController.setInput(chatController.input().substring(6));
						}
						ChatPacketEncoder.writePublicMessage(networkSession.outgoing, chatColor, chatEffect, chatController.input(),
								chatBuffer);
						chatController.setInput(ChatCodec.normalize(chatController.input()));
						chatController.setInput(Censor.censor(chatController.input()));
						localPlayer.overheadText = chatController.input();
						localPlayer.overheadTextColor = chatColor;
						localPlayer.overheadTextEffect = chatEffect;
						localPlayer.overheadTextCyclesRemaining = 150;
						if (playerRights == 2)
							addChatMessage("@cr2@" + localPlayer.name, ((Actor) (localPlayer)).overheadText, ChatMessageType.PUBLIC);
						else if (playerRights == 1)
							addChatMessage("@cr1@" + localPlayer.name, ((Actor) (localPlayer)).overheadText, ChatMessageType.PUBLIC);
						else
							addChatMessage(localPlayer.name, ((Actor) (localPlayer)).overheadText, ChatMessageType.PUBLIC);
						if (chatController.publicMode() == ChatMode.OFF) {
							chatController.setPublicMode(ChatMode.HIDE);
							chatModesRedraw = true;
							ChatPacketEncoder.writeChatModes(networkSession.outgoing, chatController.publicMode(), chatController.privateMode(),
									chatController.tradeMode());
						}
					}
					chatController.setInput("");
					chatboxRedraw = true;
				}
			}
		} while (true);
	}

	/**
	 * Opens a JAGGRAB request stream for the supplied resource path.
	 *
	 * @param request the request
	 * @return the resulting data input stream
	 * @throws IOException if an I/O operation fails
	 */
	public DataInputStream openJaggrabStream(String request) throws IOException {
		if (jaggrabSocket != null) {
			try {
				jaggrabSocket.close();
			} catch (Exception ignored) {
			}
			jaggrabSocket = null;
		}
		jaggrabSocket = openSocket(43595 + portOffset);
		jaggrabSocket.setSoTimeout(10000);
		java.io.InputStream inputstream = jaggrabSocket.getInputStream();
		OutputStream outputstream = jaggrabSocket.getOutputStream();
		outputstream.write(("JAGGRAB /" + request + "\n\n").getBytes());
		return new DataInputStream(inputstream);
	}

	/**
	 * Opens a client socket through the game shell.
	 *
	 * @param port the server port
	 * @return the resulting socket
	 * @throws IOException if an I/O operation fails
	 */
	public Socket openSocket(int port) throws IOException {
		return Signlink.openSocket(port);
	}

	/**
	 * Reads and dispatches at most one complete incoming packet frame.
	 *
	 * @return true when the requested condition/action succeeds; otherwise false
	 */
	public boolean processIncomingPacket() {
		try {
			return incomingPacketDispatcher.process();
		} catch (IOException ignored) {
			reconnect();
		} catch (Exception exception) {
			String errorDetails = "T2 - " + networkSession.incomingOpcode + "," + networkSession.secondLastOpcode + ","
					+ networkSession.thirdLastOpcode + " - " + networkSession.incomingLength + ","
					+ (regionManager.baseX + ((Actor) (localPlayer)).pathX[0]) + ","
					+ (regionManager.baseY + ((Actor) (localPlayer)).pathY[0]) + " - ";
			for (int payloadIndex = 0; payloadIndex < networkSession.incomingLength
					&& payloadIndex < 50; payloadIndex++)
				errorDetails = errorDetails + networkSession.incoming.payload[payloadIndex] + ",";

			Signlink.reportError(errorDetails);
			logout();
		}
		return true;
	}


	/**
	 * Draws the contextual action tooltip shown when the context menu is closed.
	 */
	public void drawMenuTooltip() {
		if (menuController.state().count < 2 && interfaceController.state().itemSelected == 0 && interfaceController.state().spellSelected == 0)
			return;
		String tooltip;
		if (interfaceController.state().itemSelected == 1 && menuController.state().count < 2)
			tooltip = "Use " + interfaceController.state().selectedItemName + " with...";
		else if (interfaceController.state().spellSelected == 1 && menuController.state().count < 2)
			tooltip = interfaceController.state().selectedSpellAction + "...";
		else
			tooltip = menuController.state().actionNames[menuController.state().count - 1];
		if (menuController.state().count > 2)
			tooltip = tooltip + "@whi@ / " + (menuController.state().count - 2) + " more options";
		boldFont.drawRandomizedTextWithTags(tooltip, 4, 15, 0xffffff, gameCycle / 1000, true);
	}

	/**
	 * Finds a route to a tile or interaction target and sends the appropriate
	 * movement request.
	 *
	 * @param allowAlternative whether the pathfinder may choose the original
	 *                         alternative-route fallback
	 * @param targetX          the target tile X coordinate
	 * @param targetY          the target tile Y coordinate
	 * @param targetWidth      the target width in tiles
	 * @param targetHeight     the target height in tiles
	 * @param movementType     the movement packet variant
	 * @param interactionType  the collision interaction type
	 * @param orientation      the target orientation
	 * @param accessMask       the rectangular-object access mask
	 * @return true when the requested condition/action succeeds; otherwise false
	 */
	private boolean walkTo(boolean allowAlternative, int targetX, int targetY, int targetWidth, int targetHeight,
			int movementType, int interactionType, int orientation, int accessMask) {
		Actor currentPlayer = localPlayer;
		Pathfinder.Route route = pathfinder.findRoute(worldState.collisionMaps[currentPlane], currentPlayer.pathX[0],
				currentPlayer.pathY[0], targetX, targetY, targetWidth, targetHeight, interactionType, orientation,
				accessMask, allowAlternative);
		alternativeRoute = 0;
		if (route == null) {
			return false;
		}

		alternativeRoute = route.isAlternative() ? 1 : 0;
		destinationX = route.getDestinationX();
		destinationY = route.getDestinationY();
		MovementPacketEncoder.write(networkSession.outgoing, route, movementType, regionManager.baseX,
				regionManager.baseY, keyStatus[5] == 1);
		return true;
	}

	/**
	 * Returns the explicit standalone game host used in place of the removed Applet
	 * code-base lookup.
	 *
	 * @return the resulting text
	 */
	public String getConfiguredHost() {
		return serverHost;
	}

	/**
	 * Configures the hostname used by all standalone game/update/archive sockets.
	 * @param host the host name
	 */
	public static void setServerHost(String host) {
		if (host == null || host.trim().isEmpty()) {
			throw new IllegalArgumentException("server host must not be blank");
		}
		serverHost = host.trim();
	}

	/** Delegates player menu construction to {@link MenuController}.
	 * @param playerIndex player index
	 * @param tileY local tile Y
	 * @param tileX local tile X
	 * @param player target player
	 */
	public void buildPlayerMenu(int playerIndex, int tileY, int tileX, Player player) {
		menuController.buildPlayerMenu(playerIndex, tileY, tileX, player, localPlayer, playerActions,
				playerActionLowPriority);
	}

	/**
	 * Delegates classic scrollbar input to the interface interaction owner.
	 *
	 * @param scrollHeight full content height
	 * @param y scrollbar Y coordinate
	 * @param widget scrollable widget
	 * @param mouseY mouse Y coordinate
	 * @param redrawArea fixed redraw area
	 * @param mouseX mouse X coordinate
	 * @param height visible height
	 * @param x scrollbar X coordinate
	 */
	public void handleScrollbarInput(int scrollHeight, int y, Widget widget, int mouseY, int redrawArea, int mouseX,
			int height, int x) {
		interfaceController.handleScrollbarInput(scrollHeight, y, widget, mouseY, redrawArea, mouseX, height, x,
				mouseButtonHoldTicks, interfaceRedrawSink);
	}

	/** Delegates scene-pick menu construction to {@link MenuController}. */
	public void buildViewportMenu() {
		menuController.buildViewportMenu(worldState, actorSynchronizer, currentPlane, localPlayer, playerActions,
				playerActionLowPriority, super.mouseX, super.mouseY);
	}

	/**
	 * Releases model resources held by one interface group.
	 *
	 * @param interfaceId interface group identifier
	 */
	public void unloadInterface(int interfaceId) {
		interfaceController.unload(interfaceId);
	}

	/**
	 * Adds a message to the fixed chat history and requests the appropriate redraw.
	 *
	 * @param sender  the message sender name
	 * @param message the message text
	 * @param type    the chat message type
	 */
	public void addChatMessage(String sender, String message, int type) {
		if (type == 0 && interfaceController.state().dialogueInterfaceId != -1) {
			chatController.setClickToContinueMessage(message);
			super.clickButton = 0;
		}
		if (interfaceController.state().chatboxInterfaceId == -1)
			chatboxRedraw = true;
		chatController.history().add(sender, message, type);
	}

	/**
	 * Clears definition, model, item-sprite, animation-frame, and scene caches.
	 */
	public void clearCaches() {
		GameObjectDefinition.clearModelCaches();
		NpcDefinition.modelCache.clear();
		ItemDefinition.modelCache.clear();
		ItemSpriteFactory.clearCache();
		Player.modelCache.clear();
		SpotAnimation.modelCache.clear();
	}

	/**
	 * Loads title-screen sprites and starts the independently owned flame animator.
	 */
	public void initializeTitleScreen() {
		titleBoxImage = new IndexedImage(titleArchive, "titlebox", 0);
		titleButtonImage = new IndexedImage(titleArchive, "titlebutton", 0);
		titleFlameAnimator.prepare(titleArchive, titleLeftFlameBuffer, titleRightFlameBuffer);
		drawLoadingText(10, "Connecting to fileserver");
		titleFlameAnimator.start(() -> gameCycle, () -> super.graphics);
	}

	/**
	 * Removes a friend through SocialManager and refreshes dependent interface
	 * state.
	 *
	 * @param encodedName the Base-37 encoded player name
	 */
	public void removeFriend(long encodedName) {
		if (socialManager.removeFriend(encodedName, networkSession.outgoing))
			sidebarRedraw = true;
	}

	/** Processes context-menu mouse input through {@link MenuController}. */
	public void processMenuClick() {
		menuController.processClick(super.clickButton, super.clickX, super.clickY, super.mouseX, super.mouseY,
				oneButtonMouseMode, boldFont, this::dispatchMenuAction, () -> inventoryDragMoved = false);
	}

	/**
	 * Draws a classic widget scrollbar through {@link WidgetRenderer}.
	 *
	 * @param scrollY current scroll offset
	 * @param x X coordinate
	 * @param height visible height
	 * @param scrollHeight full scrollable height
	 * @param y Y coordinate
	 */
	public void drawScrollbar(int scrollY, int x, int height, int scrollHeight, int y) {
		widgetRenderer.drawScrollbar(new WidgetRenderer.RenderContext(super.mouseX, super.mouseY, animationCycleDelta, smallFont, plainFont,
				scrollbarTop, scrollbarBottom, scrollbarTrackColor, scrollbarThumbColor, scrollbarHighlightColor,
				scrollbarShadowColor), scrollY, x, height, scrollHeight, y);
	}

	/**
	 * Adds NPCs matching the requested render-priority pass to the scene.
	 *
	 * @param priorityRender whether to render the priority NPC/player pass
	 */
	private void addNpcsToScene(boolean priorityRender) {
		sceneEntityRenderer.addNpcs(worldState, actorSynchronizer, currentPlane, priorityRender);
	}

	/**
	 * Attempts to re-establish a lost logged-in session.
	 */
	public void reconnect() {
		if (logoutTimer > 0) {
			logout();
			return;
		}
		drawGameLoadingMessage("Please wait - attempting to reestablish", "Connection lost");
		minimapRenderer.state = 0;
		destinationX = 0;
		BufferedConnection previousConnection = networkSession.getConnection();
		loggedIn = false;
		loginSession.resetFailures();
		login(loginScreen.username, loginScreen.password, true);
		if (!loggedIn)
			logout();
		try {
			if (previousConnection != null)
				previousConnection.close();
			return;
		} catch (Exception ignored) {
			return;
		}
	}

	/**
	 * Delegates content-type widget actions to {@link InterfaceController}.
	 * @param widget activated widget
	 * @return whether the widget click packet should be sent
	 */
	public boolean handleWidgetContentAction(Widget widget) {
		return interfaceController.handleContentAction(widget, socialManager, chatController, appearanceEditor,
				networkSession.outgoing, this::closeInterfaces, () -> chatboxRedraw = true, value -> logoutTimer = value);
	}

	/**
	 * Loads one startup archive through ResourceLoader while reporting progress.
	 *
	 * @param expectedCrc    the expected archive CRC
	 * @param archiveName    the archive request name
	 * @param loadingPercent the loading progress percentage
	 * @param cacheFileId    the cache file id
	 * @param displayName    the user-facing archive name
	 * @return the resulting archive
	 */
	private Archive loadArchive(int expectedCrc, String archiveName, int loadingPercent, int cacheFileId,
			String displayName) {
		return resourceLoader.loadArchive(expectedCrc, archiveName, loadingPercent, cacheFileId, displayName,
				this::openJaggrabStream, this::drawLoadingText);
	}

	/**
	 * Allocates and initializes the fixed title-screen graphics buffers.
	 */
	public void createTitleScreenBuffers() {
		if (titleTopBuffer != null)
			return;
		super.gameBuffer = null;
		chatboxBuffer = null;
		minimapBuffer = null;
		sidebarBuffer = null;
		viewportBuffer = null;
		chatModesBuffer = null;
		bottomTabsBuffer = null;
		topTabsBuffer = null;

		titleLeftFlameBuffer = new GraphicsBuffer(getGameComponent(), 128, 265);
		Rasterizer.resetPixels();
		titleRightFlameBuffer = new GraphicsBuffer(getGameComponent(), 128, 265);
		Rasterizer.resetPixels();
		titleTopBuffer = new GraphicsBuffer(getGameComponent(), 509, 171);
		Rasterizer.resetPixels();
		titleBottomBuffer = new GraphicsBuffer(getGameComponent(), 360, 132);
		Rasterizer.resetPixels();
		loginBoxBuffer = new GraphicsBuffer(getGameComponent(), 360, 200);
		Rasterizer.resetPixels();
		titleLeftBottomBuffer = new GraphicsBuffer(getGameComponent(), 202, 238);
		Rasterizer.resetPixels();
		titleRightBottomBuffer = new GraphicsBuffer(getGameComponent(), 203, 238);
		Rasterizer.resetPixels();
		titleLeftCenterBuffer = new GraphicsBuffer(getGameComponent(), 74, 94);
		Rasterizer.resetPixels();
		titleRightCenterBuffer = new GraphicsBuffer(getGameComponent(), 75, 94);
		Rasterizer.resetPixels();
		if (titleArchive != null) {
			drawTitleBackground();
			initializeTitleScreen();
		}
		gameScreenRedraw = true;
	}

	/**
	 * Loads startup archives and resources and initializes all major client
	 * subsystems.
	 */
	public void startUp() {
		drawLoadingText(20, "Starting up");
		if (startupStarted) {
			duplicateClientError = true;
			return;
		}
		startupStarted = true;
		if (Signlink.cacheData != null) {
			resourceLoader.initializeCacheIndices(Signlink.cacheData, Signlink.cacheIndexes);
		}
		try {
			/*
			 * The game server is authoritative for the packed cache. Always obtain its
			 * bootstrap CRC table first; loadArchive() will then keep matching local
			 * archives and JAGGRAB only the missing or outdated ones.
			 */
			loadArchiveCrcs();
			titleArchive = loadArchive(resourceLoader.getArchiveCrc(1), "title", 25, 1, "title screen");
			smallFont = new TypeFace(false, titleArchive, "p11_full");
			plainFont = new TypeFace(false, titleArchive, "p12_full");
			boldFont = new TypeFace(false, titleArchive, "b12_full");
			fancyFont = new TypeFace(true, titleArchive, "q8_full");
			drawTitleBackground();
			initializeTitleScreen();
			Archive configArchive = loadArchive(resourceLoader.getArchiveCrc(2), "config", 30, 2, "config");
			Archive interfaceArchive = loadArchive(resourceLoader.getArchiveCrc(3), "interface", 35, 3, "interface");
			Archive mediaArchive = loadArchive(resourceLoader.getArchiveCrc(4), "media", 40, 4, "2d graphics");
			Archive textureArchive = loadArchive(resourceLoader.getArchiveCrc(6), "textures", 45, 6, "textures");
			Archive wordEncodingArchive = loadArchive(resourceLoader.getArchiveCrc(7), "wordenc", 50, 7, "chat system");
			Archive soundArchive = loadArchive(resourceLoader.getArchiveCrc(8), "sounds", 55, 8, "sound effects");
			worldState = new WorldState();
			zoneUpdates = new ZoneUpdateHandler(worldState);

			minimapRenderer.initializeMapImage();
			Archive versionListArchive = loadArchive(resourceLoader.getArchiveCrc(5), "versionlist", 60, 5,
					"update list");
			drawLoadingText(60, "Initializing on-demand cache");
			onDemandFetcher = new OnDemandFetcher();
			onDemandFetcher.start(versionListArchive, this, resourceLoader);
			AnimationFrame.initialize(onDemandFetcher.getAnimationCount());
			Model.initializeModelHeaders(onDemandFetcher.getFileCount(0), onDemandFetcher);
			if (!lowMemory) {
				musicController.requestStartupTrack(onDemandFetcher::request, lowMemory);
				while (onDemandFetcher.getOutstandingRequestCount() > 0) {
					processOnDemandRequests();
					try {
						Thread.sleep(100L);
					} catch (Exception ignored) {
					}
					if (onDemandFetcher.requestFailures > 3) {
						haltOnLoadError("ondemand");
						return;
					}
				}
			}
			drawLoadingText(65, "Requesting animations");
			// Reused across preload phases as the total/outstanding resource count for that
			// phase.
			int requestCount = onDemandFetcher.getFileCount(1);
			for (int animationId = 0; animationId < requestCount; animationId++)
				onDemandFetcher.request(1, animationId);

			while (onDemandFetcher.getOutstandingRequestCount() > 0) {
				int loadedAnimationCount = requestCount - onDemandFetcher.getOutstandingRequestCount();
				if (loadedAnimationCount > 0)
					drawLoadingText(65, "Loading animations - " + (loadedAnimationCount * 100) / requestCount + "%");
				processOnDemandRequests();
				try {
					Thread.sleep(100L);
				} catch (Exception ignored2) {
				}
				if (onDemandFetcher.requestFailures > 3) {
					haltOnLoadError("ondemand");
					return;
				}
			}
			drawLoadingText(70, "Requesting models");
			requestCount = onDemandFetcher.getFileCount(0);
			for (int modelId = 0; modelId < requestCount; modelId++) {
				int modelFlags = onDemandFetcher.getModelIndex(modelId);
				if ((modelFlags & 1) != 0)
					onDemandFetcher.request(0, modelId);
			}

			requestCount = onDemandFetcher.getOutstandingRequestCount();
			while (onDemandFetcher.getOutstandingRequestCount() > 0) {
				int loadedModelCount = requestCount - onDemandFetcher.getOutstandingRequestCount();
				if (loadedModelCount > 0)
					drawLoadingText(70, "Loading models - " + (loadedModelCount * 100) / requestCount + "%");
				processOnDemandRequests();
				try {
					Thread.sleep(100L);
				} catch (Exception ignored3) {
				}
			}
			if (resourceLoader.hasCache()) {
				drawLoadingText(75, "Requesting maps");
				onDemandFetcher.request(OnDemandFetcher.MAP, onDemandFetcher.getMapFileId(47, 48, OnDemandFetcher.MAP_FILE_TERRAIN));
				onDemandFetcher.request(OnDemandFetcher.MAP, onDemandFetcher.getMapFileId(47, 48, OnDemandFetcher.MAP_FILE_LANDSCAPE));
				onDemandFetcher.request(OnDemandFetcher.MAP, onDemandFetcher.getMapFileId(48, 48, OnDemandFetcher.MAP_FILE_TERRAIN));
				onDemandFetcher.request(OnDemandFetcher.MAP, onDemandFetcher.getMapFileId(48, 48, OnDemandFetcher.MAP_FILE_LANDSCAPE));
				onDemandFetcher.request(OnDemandFetcher.MAP, onDemandFetcher.getMapFileId(49, 48, OnDemandFetcher.MAP_FILE_TERRAIN));
				onDemandFetcher.request(OnDemandFetcher.MAP, onDemandFetcher.getMapFileId(49, 48, OnDemandFetcher.MAP_FILE_LANDSCAPE));
				onDemandFetcher.request(OnDemandFetcher.MAP, onDemandFetcher.getMapFileId(47, 47, OnDemandFetcher.MAP_FILE_TERRAIN));
				onDemandFetcher.request(OnDemandFetcher.MAP, onDemandFetcher.getMapFileId(47, 47, OnDemandFetcher.MAP_FILE_LANDSCAPE));
				onDemandFetcher.request(OnDemandFetcher.MAP, onDemandFetcher.getMapFileId(48, 47, OnDemandFetcher.MAP_FILE_TERRAIN));
				onDemandFetcher.request(OnDemandFetcher.MAP, onDemandFetcher.getMapFileId(48, 47, OnDemandFetcher.MAP_FILE_LANDSCAPE));
				onDemandFetcher.request(OnDemandFetcher.MAP, onDemandFetcher.getMapFileId(48, 148, OnDemandFetcher.MAP_FILE_TERRAIN));
				onDemandFetcher.request(OnDemandFetcher.MAP, onDemandFetcher.getMapFileId(48, 148, OnDemandFetcher.MAP_FILE_LANDSCAPE));
				requestCount = onDemandFetcher.getOutstandingRequestCount();
				while (onDemandFetcher.getOutstandingRequestCount() > 0) {
					int loadedMapCount = requestCount - onDemandFetcher.getOutstandingRequestCount();
					if (loadedMapCount > 0)
						drawLoadingText(75, "Loading maps - " + (loadedMapCount * 100) / requestCount + "%");
					processOnDemandRequests();
					try {
						Thread.sleep(100L);
					} catch (Exception ignored4) {
					}
				}
			}
			requestCount = onDemandFetcher.getFileCount(0);
			for (int modelId2 = 0; modelId2 < requestCount; modelId2++) {
				int modelFlags2 = onDemandFetcher.getModelIndex(modelId2);
				byte extraPriority = 0;
				if ((modelFlags2 & 8) != 0)
					extraPriority = 10;
				else if ((modelFlags2 & 0x20) != 0)
					extraPriority = 9;
				else if ((modelFlags2 & 0x10) != 0)
					extraPriority = 8;
				else if ((modelFlags2 & 0x40) != 0)
					extraPriority = 7;
				else if ((modelFlags2 & 0x80) != 0)
					extraPriority = 6;
				else if ((modelFlags2 & 2) != 0)
					extraPriority = 5;
				else if ((modelFlags2 & 4) != 0)
					extraPriority = 4;
				if ((modelFlags2 & 1) != 0)
					extraPriority = 3;
				if (extraPriority != 0)
					onDemandFetcher.setExtraPriority(0, modelId2, extraPriority);
			}

			onDemandFetcher.preloadMaps(membersWorld);
			if (!lowMemory) {
				requestCount = onDemandFetcher.getFileCount(2);
				for (int midiId = 1; midiId < requestCount; midiId++)
					if (onDemandFetcher.isMidiPreload(midiId))
						onDemandFetcher.setExtraPriority(2, midiId, (byte) 1);

			}
			requestCount = onDemandFetcher.getFileCount(0);
			for (int modelId3 = 0; modelId3 < requestCount; modelId3++) {
				int modelFlags3 = onDemandFetcher.getModelIndex(modelId3);
				if (modelFlags3 == 0 && onDemandFetcher.totalFiles < 200)
					onDemandFetcher.setExtraPriority(0, modelId3, (byte) 1);
			}

			drawLoadingText(80, "Unpacking media");
			sidebarBackground = new IndexedImage(mediaArchive, "invback", 0);
			chatboxBackground = new IndexedImage(mediaArchive, "chatback", 0);
			minimapBackground = new IndexedImage(mediaArchive, "mapback", 0);
			chatModesBackground = new IndexedImage(mediaArchive, "backbase1", 0);
			bottomTabBackground = new IndexedImage(mediaArchive, "backbase2", 0);
			topTabBackground = new IndexedImage(mediaArchive, "backhmid1", 0);
			for (int sidebarIconIndex = 0; sidebarIconIndex < 13; sidebarIconIndex++)
				sidebarIcons[sidebarIconIndex] = new IndexedImage(mediaArchive, "sideicons", sidebarIconIndex);

			compassSprite = new ImageRGB(mediaArchive, "compass", 0);
			minimapEdgeArrow = new ImageRGB(mediaArchive, "mapedge", 0);
			minimapEdgeArrow.trim();
			for (int mapSceneIndex = 0; mapSceneIndex < 72; mapSceneIndex++)
				mapSceneSprites[mapSceneIndex] = new IndexedImage(mediaArchive, "mapscene", mapSceneIndex);

			for (int mapFunctionIndex = 0; mapFunctionIndex < 70; mapFunctionIndex++)
				mapFunctionSprites[mapFunctionIndex] = new ImageRGB(mediaArchive, "mapfunction", mapFunctionIndex);

			for (int hitmarkIndex = 0; hitmarkIndex < 5; hitmarkIndex++)
				hitmarkSprites[hitmarkIndex] = new ImageRGB(mediaArchive, "hitmarks", hitmarkIndex);

			for (int skullIconIndex = 0; skullIconIndex < 6; skullIconIndex++)
				skullIconSprites[skullIconIndex] = new ImageRGB(mediaArchive, "headicons_pk", skullIconIndex);

			for (int prayerIconIndex = 0; prayerIconIndex < 9; prayerIconIndex++)
				prayerIconSprites[prayerIconIndex] = new ImageRGB(mediaArchive, "headicons_prayer", prayerIconIndex);

			for (int hintIconIndex = 0; hintIconIndex < 6; hintIconIndex++)
				hintIconSprites[hintIconIndex] = new ImageRGB(mediaArchive, "headicons_hint", hintIconIndex);

			multiCombatOverlay = new ImageRGB(mediaArchive, "overlay_multiway", 0);
			destinationMapMarker = new ImageRGB(mediaArchive, "mapmarker", 0);
			hintMapMarker = new ImageRGB(mediaArchive, "mapmarker", 1);
			for (int crossIndex = 0; crossIndex < 8; crossIndex++)
				crossSprites[crossIndex] = new ImageRGB(mediaArchive, "cross", crossIndex);

			groundItemMapDot = new ImageRGB(mediaArchive, "mapdots", 0);
			npcMapDot = new ImageRGB(mediaArchive, "mapdots", 1);
			playerMapDot = new ImageRGB(mediaArchive, "mapdots", 2);
			friendMapDot = new ImageRGB(mediaArchive, "mapdots", 3);
			teamMapDot = new ImageRGB(mediaArchive, "mapdots", 4);
			scrollbarTop = new IndexedImage(mediaArchive, "scrollbar", 0);
			scrollbarBottom = new IndexedImage(mediaArchive, "scrollbar", 1);
			redstone1 = new IndexedImage(mediaArchive, "redstone1", 0);
			redstone2 = new IndexedImage(mediaArchive, "redstone2", 0);
			redstone3 = new IndexedImage(mediaArchive, "redstone3", 0);
			redstone1Horizontal = new IndexedImage(mediaArchive, "redstone1", 0);
			redstone1Horizontal.flipHorizontal();
			redstone2Horizontal = new IndexedImage(mediaArchive, "redstone2", 0);
			redstone2Horizontal.flipHorizontal();
			redstone1Vertical = new IndexedImage(mediaArchive, "redstone1", 0);
			redstone1Vertical.flipVertical();
			redstone2Vertical = new IndexedImage(mediaArchive, "redstone2", 0);
			redstone2Vertical.flipVertical();
			redstone3Vertical = new IndexedImage(mediaArchive, "redstone3", 0);
			redstone3Vertical.flipVertical();
			redstone1Both = new IndexedImage(mediaArchive, "redstone1", 0);
			redstone1Both.flipHorizontal();
			redstone1Both.flipVertical();
			redstone2Both = new IndexedImage(mediaArchive, "redstone2", 0);
			redstone2Both.flipHorizontal();
			redstone2Both.flipVertical();
			for (int moderatorIconIndex = 0; moderatorIconIndex < 2; moderatorIconIndex++)
				moderatorIcons[moderatorIconIndex] = new IndexedImage(mediaArchive, "mod_icons", moderatorIconIndex);

			ImageRGB frameSprite = new ImageRGB(mediaArchive, "backleft1", 0);
			backLeft1Buffer = new GraphicsBuffer(getGameComponent(), frameSprite.width, frameSprite.height);
			frameSprite.drawInverse(0, 0);
			frameSprite = new ImageRGB(mediaArchive, "backleft2", 0);
			backLeft2Buffer = new GraphicsBuffer(getGameComponent(), frameSprite.width, frameSprite.height);
			frameSprite.drawInverse(0, 0);
			frameSprite = new ImageRGB(mediaArchive, "backright1", 0);
			backRight1Buffer = new GraphicsBuffer(getGameComponent(), frameSprite.width, frameSprite.height);
			frameSprite.drawInverse(0, 0);
			frameSprite = new ImageRGB(mediaArchive, "backright2", 0);
			backRight2Buffer = new GraphicsBuffer(getGameComponent(), frameSprite.width, frameSprite.height);
			frameSprite.drawInverse(0, 0);
			frameSprite = new ImageRGB(mediaArchive, "backtop1", 0);
			backTop1Buffer = new GraphicsBuffer(getGameComponent(), frameSprite.width, frameSprite.height);
			frameSprite.drawInverse(0, 0);
			frameSprite = new ImageRGB(mediaArchive, "backvmid1", 0);
			backVerticalMiddle1Buffer = new GraphicsBuffer(getGameComponent(), frameSprite.width, frameSprite.height);
			frameSprite.drawInverse(0, 0);
			frameSprite = new ImageRGB(mediaArchive, "backvmid2", 0);
			backVerticalMiddle2Buffer = new GraphicsBuffer(getGameComponent(), frameSprite.width, frameSprite.height);
			frameSprite.drawInverse(0, 0);
			frameSprite = new ImageRGB(mediaArchive, "backvmid3", 0);
			backVerticalMiddle3Buffer = new GraphicsBuffer(getGameComponent(), frameSprite.width, frameSprite.height);
			frameSprite.drawInverse(0, 0);
			frameSprite = new ImageRGB(mediaArchive, "backhmid2", 0);
			backHorizontalMiddle2Buffer = new GraphicsBuffer(getGameComponent(), frameSprite.width, frameSprite.height);
			frameSprite.drawInverse(0, 0);
			int redOffset = (int) (Math.random() * 21D) - 10;
			int greenOffset = (int) (Math.random() * 21D) - 10;
			int blueOffset = (int) (Math.random() * 21D) - 10;
			int brightnessOffset = (int) (Math.random() * 41D) - 20;
			for (int spriteIndex = 0; spriteIndex < 100; spriteIndex++) {
				if (mapFunctionSprites[spriteIndex] != null)
					mapFunctionSprites[spriteIndex].adjustRgb(redOffset + brightnessOffset,
							greenOffset + brightnessOffset, blueOffset + brightnessOffset);
				if (mapSceneSprites[spriteIndex] != null)
					mapSceneSprites[spriteIndex].adjustPalette(redOffset + brightnessOffset,
							greenOffset + brightnessOffset, blueOffset + brightnessOffset);
			}

			drawLoadingText(83, "Unpacking textures");
			Rasterizer3D.loadTextures(textureArchive);
			Rasterizer3D.setBrightness(0.80000000000000004D);
			Rasterizer3D.initializeTexturePool(20);
			drawLoadingText(86, "Unpacking config");
			AnimationSequence.load(configArchive);
			GameObjectDefinition.load(configArchive);
			FloorDefinition.load(configArchive);
			ItemDefinition.load(configArchive);
			NpcDefinition.load(configArchive);
			IdentityKit.load(configArchive);
			SpotAnimation.load(configArchive);
			Varp.load(configArchive);
			Varbit.load(configArchive);
			ItemDefinition.membersWorld = membersWorld;
			if (!lowMemory) {
				drawLoadingText(90, "Unpacking sounds");
				byte soundData[] = soundArchive.read("sounds.dat");
				Buffer soundBuffer = new Buffer(soundData);
				SoundTrack.load(soundBuffer);
			}
			drawLoadingText(95, "Unpacking interfaces");
			TypeFace aclass50_sub1_sub1_sub2[] = { smallFont, plainFont, boldFont, fancyFont };
			Widget.load(interfaceArchive, mediaArchive, aclass50_sub1_sub1_sub2);
			drawLoadingText(100, "Preparing game engine");
			for (int compassRow = 0; compassRow < 33; compassRow++) {
				int maskStartX = 999;
				int maskEndX = 0;
				for (int maskX = 0; maskX < 34; maskX++) {
					if (minimapBackground.pixels[maskX + compassRow * minimapBackground.width] == 0) {
						if (maskStartX == 999)
							maskStartX = maskX;
						continue;
					}
					if (maskStartX == 999)
						continue;
					maskEndX = maskX;
					break;
				}

				compassMaskOffsets[compassRow] = maskStartX;
				compassMaskWidths[compassRow] = maskEndX - maskStartX;
			}

			for (int minimapRow = 5; minimapRow < 156; minimapRow++) {
				int maskStartX2 = 999;
				int maskEndX2 = 0;
				for (int maskX2 = 25; maskX2 < 172; maskX2++) {
					if (minimapBackground.pixels[maskX2 + minimapRow * minimapBackground.width] == 0
							&& (maskX2 > 34 || minimapRow > 34)) {
						if (maskStartX2 == 999)
							maskStartX2 = maskX2;
						continue;
					}
					if (maskStartX2 == 999)
						continue;
					maskEndX2 = maskX2;
					break;
				}

				minimapMaskOffsets[minimapRow - 5] = maskStartX2 - 25;
				minimapMaskWidths[minimapRow - 5] = maskEndX2 - maskStartX2;
			}

			Rasterizer3D.setBounds(ClientLayout.FIXED_WIDTH, ClientLayout.FIXED_HEIGHT);
			fullScreenScanlineOffsets = Rasterizer3D.scanlineOffsets;
			Rasterizer3D.setBounds(ClientLayout.CHATBOX_WIDTH, ClientLayout.CHATBOX_HEIGHT);
			chatboxScanlineOffsets = Rasterizer3D.scanlineOffsets;
			Rasterizer3D.setBounds(ClientLayout.SIDEBAR_WIDTH, ClientLayout.SIDEBAR_HEIGHT);
			sidebarScanlineOffsets = Rasterizer3D.scanlineOffsets;
			rebuildViewportProjection();
			Censor.load(wordEncodingArchive);
			mouseRecorder = new MouseRecorder(this);
			mouseRecorder.start(10);
			DynamicObject.clientInstance = this;
			GameObjectDefinition.clientInstance = this;
			NpcDefinition.clientInstance = this;
			return;
		} catch (Exception exception) {
			Signlink.reportError("loaderror " + loadingMessage + " " + loadingPercent);
		}
		loadingError = true;
	}

	/**
	 * Scrolls the legacy animated texture byte maps for the current texture cycle.
	 *
	 * @param textureCycle the accumulated texture animation cycle count
	 */
	public void animateTextures(int textureCycle) {
		if (!lowMemory) {
			for (int textureSlot = 0; textureSlot < animatedTextureIds.length; textureSlot++) {
				int textureId = animatedTextureIds[textureSlot];
				if (Rasterizer3D.textureLastUsed[textureId] >= textureCycle) {
					IndexedImage rune = Rasterizer3D.textures[textureId];
					int pixelMask = rune.width * rune.height - 1;
					int scrollOffset = rune.width * animationCycleDelta * 2;
					byte sourcePixels[] = rune.pixels;
					byte scrolledPixels[] = textureScrollScratch;
					for (int pixelIndex = 0; pixelIndex <= pixelMask; pixelIndex++)
						scrolledPixels[pixelIndex] = sourcePixels[pixelIndex - scrollOffset & pixelMask];

					rune.pixels = scrolledPixels;
					textureScrollScratch = sourcePixels;
					Rasterizer3D.releaseTexture(textureId);
				}
			}

		}
	}

	/** Delegates widget/inventory menu construction to {@link MenuController}.
	 * @param y root Y
	 * @param widget root widget
	 * @param screenArea fixed UI area
	 * @param scrollY scroll offset
	 * @param x root X
	 * @param mouseX mouse X
	 * @param mouseY mouse Y
	 */
	public void buildInterfaceMenu(int y, Widget widget, int screenArea, int scrollY, int x, int mouseX, int mouseY) {
		menuController.buildInterfaceMenu(y, widget, screenArea, scrollY, x, mouseX, mouseY);
	}

	/**
	 * Coordinates one complete logged-in frame across sidebar, chatbox, viewport,
	 * and fullscreen interfaces.
	 */
	public void drawGameScreen() {
		if (interfaceController.state().fullscreenInterfaceId != -1
				&& (regionManager.loadingStage == RegionManager.STAGE_LOADED || super.gameBuffer != null)) {
			if (regionManager.loadingStage == RegionManager.STAGE_LOADED) {
				widgetRuntime.updateAnimations(animationCycleDelta, interfaceController.state().fullscreenInterfaceId);
				if (interfaceController.state().fullscreenOverlayInterfaceId != -1)
					widgetRuntime.updateAnimations(animationCycleDelta, interfaceController.state().fullscreenOverlayInterfaceId);
				animationCycleDelta = 0;
				createGameBuffer();
				super.gameBuffer.bindRaster();
				Rasterizer3D.scanlineOffsets = fullScreenScanlineOffsets;
				Rasterizer.resetPixels();
				gameScreenRedraw = true;
				Widget fullscreenWidget = Widget.get(interfaceController.state().fullscreenInterfaceId);
				if (fullscreenWidget.width == ClientLayout.FIXED_VIEWPORT_WIDTH && fullscreenWidget.height == ClientLayout.FIXED_VIEWPORT_HEIGHT && fullscreenWidget.type == Widget.TYPE_CONTAINER) {
					fullscreenWidget.width = ClientLayout.FIXED_WIDTH;
					fullscreenWidget.height = ClientLayout.FIXED_HEIGHT;
				}
				drawInterface(0, 0, fullscreenWidget, 0);
				if (interfaceController.state().fullscreenOverlayInterfaceId != -1) {
					Widget fullscreenOverlayWidget = Widget.get(interfaceController.state().fullscreenOverlayInterfaceId);
					if (fullscreenOverlayWidget.width == ClientLayout.FIXED_VIEWPORT_WIDTH && fullscreenOverlayWidget.height == ClientLayout.FIXED_VIEWPORT_HEIGHT
							&& fullscreenOverlayWidget.type == Widget.TYPE_CONTAINER) {
						fullscreenOverlayWidget.width = ClientLayout.FIXED_WIDTH;
						fullscreenOverlayWidget.height = ClientLayout.FIXED_HEIGHT;
					}
					drawInterface(0, 0, fullscreenOverlayWidget, 0);
				}
				if (!menuController.state().open) {
					buildContextMenu();
					drawMenuTooltip();
				} else {
					drawContextMenu();
				}
			}
			super.gameBuffer.draw(super.graphics, 0, 0);
			return;
		}
		/*
		 * Always present the complete fixed-mode frame. The original client only
		 * blitted most of these buffers when their dirty flags were set. That saves
		 * work on period hardware, but it leaves exposed AWT regions white after the
		 * window has been obscured or moved off-screen.
		 */
		createGameScreenBuffers();
		// Re-raster and present every fixed UI panel on every draw cycle.
		sidebarRedraw = true;
		chatboxRedraw = true;
		tabAreaRedraw = true;
		chatModesRedraw = true;

		if (regionManager.loadingStage != RegionManager.STAGE_LOADED)
			viewportBuffer.draw(super.graphics, layout.viewportX(), layout.viewportY());

		/*
		 * Keep the legacy redraw-triggered packet cadence separate from the new
		 * unconditional presentation. Forcing gameScreenRedraw true every frame would
		 * otherwise emit opcode 168 far more often than the original client.
		 */
		if (gameScreenRedraw) {
			gameScreenRedraw = false;
			screenRedrawKeepaliveCounter++;
			if (screenRedrawKeepaliveCounter > 85) {
				screenRedrawKeepaliveCounter = 0;
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.SCREEN_REDRAW_KEEPALIVE);
			}
		}
		if (regionManager.loadingStage == RegionManager.STAGE_LOADED)
			renderGameScene();

		/*
		 * The game viewport is the background layer in resizable mode. Draw the
		 * classic frame pieces after it so their stone borders remain visible, then
		 * composite the fixed-size UI panels on top below.
		 */
		drawGameFrameDecorations();
		if (regionManager.loadingStage != RegionManager.STAGE_LOADED)
			minimapBuffer.draw(super.graphics, layout.minimapX(), layout.minimapY());

		if (menuController.state().open && menuController.state().screenArea == 1)
			sidebarRedraw = true;
		if (interfaceController.state().sidebarOverlayInterfaceId != -1) {
			boolean sidebarAnimationChanged = widgetRuntime.updateAnimations(animationCycleDelta,
					interfaceController.state().sidebarOverlayInterfaceId);
			if (sidebarAnimationChanged)
				sidebarRedraw = true;
		}
		if (interfaceController.state().pressedInventoryArea == 2)
			sidebarRedraw = true;
		if (interfaceController.state().inventoryDragArea == 2)
			sidebarRedraw = true;
		if (sidebarRedraw) {
			drawSidebar();
			sidebarRedraw = false;
		}
		if (interfaceController.state().chatboxInterfaceId == -1 && chatController.inputDialogState() == 0) {
			chatboxScrollWidget.scrollY = chatController.contentHeight() - chatController.scrollOffset() - 77;
			if (super.mouseX > 448 && super.mouseX < 560 && super.mouseY > layout.chatboxY() - 25)
				handleScrollbarInput(chatController.contentHeight(), 0, chatboxScrollWidget, super.mouseY - layout.chatboxY(), -1,
						super.mouseX - layout.chatboxX(), 77, 463);
			int chatScrollOffsetFromBottom = chatController.contentHeight() - 77 - chatboxScrollWidget.scrollY;
			if (chatScrollOffsetFromBottom < 0)
				chatScrollOffsetFromBottom = 0;
			if (chatScrollOffsetFromBottom > chatController.contentHeight() - 77)
				chatScrollOffsetFromBottom = chatController.contentHeight() - 77;
			if (chatController.scrollOffset() != chatScrollOffsetFromBottom) {
				chatController.setScrollOffset(chatScrollOffsetFromBottom);
				chatboxRedraw = true;
			}
		}
		if (interfaceController.state().chatboxInterfaceId == -1 && chatController.inputDialogState() == 3) {
			int searchContentHeight = itemSearchResultCount * 14 + 7;
			chatboxScrollWidget.scrollY = itemSearchScrollOffset;
			if (super.mouseX > 448 && super.mouseX < 560 && super.mouseY > layout.chatboxY() - 25)
				handleScrollbarInput(searchContentHeight, 0, chatboxScrollWidget, super.mouseY - layout.chatboxY(), -1,
						super.mouseX - layout.chatboxX(), 77, 463);
			int clampedSearchScroll = chatboxScrollWidget.scrollY;
			if (clampedSearchScroll < 0)
				clampedSearchScroll = 0;
			if (clampedSearchScroll > searchContentHeight - 77)
				clampedSearchScroll = searchContentHeight - 77;
			if (itemSearchScrollOffset != clampedSearchScroll) {
				itemSearchScrollOffset = clampedSearchScroll;
				chatboxRedraw = true;
			}
		}
		if (interfaceController.state().chatboxInterfaceId != -1) {
			boolean chatboxAnimationChanged = widgetRuntime.updateAnimations(animationCycleDelta,
					interfaceController.state().chatboxInterfaceId);
			if (chatboxAnimationChanged)
				chatboxRedraw = true;
		}
		if (interfaceController.state().pressedInventoryArea == 3)
			chatboxRedraw = true;
		if (interfaceController.state().inventoryDragArea == 3)
			chatboxRedraw = true;
		if (chatController.clickToContinueMessage() != null)
			chatboxRedraw = true;
		if (menuController.state().open && menuController.state().screenArea == 2)
			chatboxRedraw = true;
		if (chatboxRedraw) {
			drawChatbox();
			chatboxRedraw = false;
		}
		if (regionManager.loadingStage == RegionManager.STAGE_LOADED) {
			drawMinimap();
			minimapBuffer.draw(super.graphics, layout.minimapX(), layout.minimapY());
		}
		if (interfaceController.state().flashingTab != -1)
			tabAreaRedraw = true;
		if (tabAreaRedraw) {
			if (interfaceController.state().flashingTab != -1 && interfaceController.state().flashingTab == interfaceController.state().selectedTab) {
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.FLASHING_TAB_ACKNOWLEDGEMENT);
				networkSession.outgoing.writeByte(interfaceController.state().selectedTab);
			}
			tabAreaRedraw = false;
			topTabsBuffer.bindRaster();
			topTabBackground.draw(0, 0);
			if (interfaceController.state().sidebarOverlayInterfaceId == -1) {
				if (interfaceController.state().tabInterfaceIds[interfaceController.state().selectedTab] != -1) {
					if (interfaceController.state().selectedTab == 0)
						redstone1.draw(22, 10);
					if (interfaceController.state().selectedTab == 1)
						redstone2.draw(54, 8);
					if (interfaceController.state().selectedTab == 2)
						redstone2.draw(82, 8);
					if (interfaceController.state().selectedTab == 3)
						redstone3.draw(110, 8);
					if (interfaceController.state().selectedTab == 4)
						redstone2Horizontal.draw(153, 8);
					if (interfaceController.state().selectedTab == 5)
						redstone2Horizontal.draw(181, 8);
					if (interfaceController.state().selectedTab == 6)
						redstone1Horizontal.draw(209, 9);
				}
				if (interfaceController.state().tabInterfaceIds[0] != -1 && (interfaceController.state().flashingTab != 0 || gameCycle % 20 < 10))
					sidebarIcons[0].draw(29, 13);
				if (interfaceController.state().tabInterfaceIds[1] != -1 && (interfaceController.state().flashingTab != 1 || gameCycle % 20 < 10))
					sidebarIcons[1].draw(53, 11);
				if (interfaceController.state().tabInterfaceIds[2] != -1 && (interfaceController.state().flashingTab != 2 || gameCycle % 20 < 10))
					sidebarIcons[2].draw(82, 11);
				if (interfaceController.state().tabInterfaceIds[3] != -1 && (interfaceController.state().flashingTab != 3 || gameCycle % 20 < 10))
					sidebarIcons[3].draw(115, 12);
				if (interfaceController.state().tabInterfaceIds[4] != -1 && (interfaceController.state().flashingTab != 4 || gameCycle % 20 < 10))
					sidebarIcons[4].draw(153, 13);
				if (interfaceController.state().tabInterfaceIds[5] != -1 && (interfaceController.state().flashingTab != 5 || gameCycle % 20 < 10))
					sidebarIcons[5].draw(180, 11);
				if (interfaceController.state().tabInterfaceIds[6] != -1 && (interfaceController.state().flashingTab != 6 || gameCycle % 20 < 10))
					sidebarIcons[6].draw(208, 13);
			}
			topTabsBuffer.draw(super.graphics, layout.topTabsX(), layout.topTabsY());
			bottomTabsBuffer.bindRaster();
			bottomTabBackground.draw(0, 0);
			if (interfaceController.state().sidebarOverlayInterfaceId == -1) {
				if (interfaceController.state().tabInterfaceIds[interfaceController.state().selectedTab] != -1) {
					if (interfaceController.state().selectedTab == 7)
						redstone1Vertical.draw(42, 0);
					if (interfaceController.state().selectedTab == 8)
						redstone2Vertical.draw(74, 0);
					if (interfaceController.state().selectedTab == 9)
						redstone2Vertical.draw(102, 0);
					if (interfaceController.state().selectedTab == 10)
						redstone3Vertical.draw(130, 1);
					if (interfaceController.state().selectedTab == 11)
						redstone2Both.draw(173, 0);
					if (interfaceController.state().selectedTab == 12)
						redstone2Both.draw(201, 0);
					if (interfaceController.state().selectedTab == 13)
						redstone1Both.draw(229, 0);
				}
				if (interfaceController.state().tabInterfaceIds[8] != -1 && (interfaceController.state().flashingTab != 8 || gameCycle % 20 < 10))
					sidebarIcons[7].draw(74, 2);
				if (interfaceController.state().tabInterfaceIds[9] != -1 && (interfaceController.state().flashingTab != 9 || gameCycle % 20 < 10))
					sidebarIcons[8].draw(102, 3);
				if (interfaceController.state().tabInterfaceIds[10] != -1
						&& (interfaceController.state().flashingTab != 10 || gameCycle % 20 < 10))
					sidebarIcons[9].draw(137, 4);
				if (interfaceController.state().tabInterfaceIds[11] != -1
						&& (interfaceController.state().flashingTab != 11 || gameCycle % 20 < 10))
					sidebarIcons[10].draw(174, 2);
				if (interfaceController.state().tabInterfaceIds[12] != -1
						&& (interfaceController.state().flashingTab != 12 || gameCycle % 20 < 10))
					sidebarIcons[11].draw(201, 2);
				if (interfaceController.state().tabInterfaceIds[13] != -1
						&& (interfaceController.state().flashingTab != 13 || gameCycle % 20 < 10))
					sidebarIcons[12].draw(226, 2);
			}
			bottomTabsBuffer.draw(super.graphics, layout.bottomTabsX(), layout.bottomTabsY());
			viewportBuffer.bindRaster();
			Rasterizer3D.scanlineOffsets = viewportScanlineOffsets;
		}
		if (chatModesRedraw) {
			chatModesRedraw = false;
			chatModesBuffer.bindRaster();
			chatModesBackground.draw(0, 0);
			plainFont.drawCenteredTextWithTags("Public chat", 55, 28, 0xffffff, true);
			if (chatController.publicMode() == ChatMode.ON)
				plainFont.drawCenteredTextWithTags("On", 55, 41, 65280, true);
			if (chatController.publicMode() == ChatMode.FRIENDS)
				plainFont.drawCenteredTextWithTags("Friends", 55, 41, 0xffff00, true);
			if (chatController.publicMode() == ChatMode.OFF)
				plainFont.drawCenteredTextWithTags("Off", 55, 41, 0xff0000, true);
			if (chatController.publicMode() == ChatMode.HIDE)
				plainFont.drawCenteredTextWithTags("Hide", 55, 41, 65535, true);
			plainFont.drawCenteredTextWithTags("Private chat", 184, 28, 0xffffff, true);
			if (chatController.privateMode() == ChatMode.ON)
				plainFont.drawCenteredTextWithTags("On", 184, 41, 65280, true);
			if (chatController.privateMode() == ChatMode.FRIENDS)
				plainFont.drawCenteredTextWithTags("Friends", 184, 41, 0xffff00, true);
			if (chatController.privateMode() == ChatMode.OFF)
				plainFont.drawCenteredTextWithTags("Off", 184, 41, 0xff0000, true);
			plainFont.drawCenteredTextWithTags("Trade/compete", 324, 28, 0xffffff, true);
			if (chatController.tradeMode() == ChatMode.ON)
				plainFont.drawCenteredTextWithTags("On", 324, 41, 65280, true);
			if (chatController.tradeMode() == ChatMode.FRIENDS)
				plainFont.drawCenteredTextWithTags("Friends", 324, 41, 0xffff00, true);
			if (chatController.tradeMode() == ChatMode.OFF)
				plainFont.drawCenteredTextWithTags("Off", 324, 41, 0xff0000, true);
			plainFont.drawCenteredTextWithTags("Report abuse", 458, 33, 0xffffff, true);
			chatModesBuffer.draw(super.graphics, layout.chatModesX(), layout.chatModesY());
			viewportBuffer.bindRaster();
			Rasterizer3D.scanlineOffsets = viewportScanlineOffsets;
		}
		animationCycleDelta = 0;
	}

	/** Draws the classic fixed-size frame pieces over the resizable world underlay. */
	private void drawGameFrameDecorations() {
		// Viewport/top-left and bottom-chat framing.
		backLeft1Buffer.draw(super.graphics, 0, 4);
		backLeft2Buffer.draw(super.graphics, 0, layout.bottomAnchoredY(357));
		backTop1Buffer.draw(super.graphics, 0, 0);
		backHorizontalMiddle2Buffer.draw(super.graphics, 0, layout.lowerBorderY());

		// The complete classic right-hand frame now moves as one top-anchored dock.
		backRight1Buffer.draw(super.graphics, layout.rightAnchoredX(722), 4);
		backRight2Buffer.draw(super.graphics, layout.rightAnchoredX(743), 205);
		backVerticalMiddle1Buffer.draw(super.graphics, layout.middleBorderX(), 4);
		backVerticalMiddle2Buffer.draw(super.graphics, layout.middleBorderX(), 205);
		backVerticalMiddle3Buffer.draw(super.graphics, layout.rightAnchoredX(496), 357);
	}

	/**
	 * Draws the optional private-message overlay above the chatbox.
	 */
	public void drawSplitPrivateChat() {
		if (chatController.splitPrivateChat() == 0)
			return;
		TypeFace font = plainFont;
		int visibleLine = 0;
		if (systemUpdateTimer != 0)
			visibleLine = 1;
		for (int messageIndex = 0; messageIndex < 100; messageIndex++)
			if (chatController.history().messages[messageIndex] != null) {
				int messageType = chatController.history().types[messageIndex];
				String sender = chatController.history().senders[messageIndex];
				byte rightsIcon = 0;
				if (sender != null && sender.startsWith("@cr1@")) {
					sender = sender.substring(5);
					rightsIcon = 1;
				}
				if (sender != null && sender.startsWith("@cr2@")) {
					sender = sender.substring(5);
					rightsIcon = 2;
				}
				if ((messageType == ChatMessageType.PRIVATE_RECEIVED || messageType == ChatMessageType.PRIVATE_RECEIVED_PRIVILEGED) && (messageType == ChatMessageType.PRIVATE_RECEIVED_PRIVILEGED || chatController.privateMode() == ChatMode.ON
						|| chatController.privateMode() == ChatMode.FRIENDS && isFriendOrSelf(sender))) {
					int lineY = layout.unobscuredViewportHeight() - 5 - visibleLine * 13;
					int textX = 4;
					font.drawText("From", textX, lineY, 0);
					font.drawText("From", textX, lineY - 1, 65535);
					textX += font.getFormattedTextWidth("From ");
					if (rightsIcon == 1) {
						moderatorIcons[0].draw(textX, lineY - 12);
						textX += 14;
					}
					if (rightsIcon == 2) {
						moderatorIcons[1].draw(textX, lineY - 12);
						textX += 14;
					}
					font.drawText(sender + ": " + chatController.history().messages[messageIndex], textX, lineY, 0);
					font.drawText(sender + ": " + chatController.history().messages[messageIndex], textX, lineY - 1, 65535);
					if (++visibleLine >= 5)
						return;
				}
				if (messageType == ChatMessageType.PRIVATE_STATUS && chatController.privateMode() < ChatMode.OFF) {
					int lineY2 = layout.unobscuredViewportHeight() - 5 - visibleLine * 13;
					font.drawText(chatController.history().messages[messageIndex], 4, lineY2, 0);
					font.drawText(chatController.history().messages[messageIndex], 4, lineY2 - 1, 65535);
					if (++visibleLine >= 5)
						return;
				}
				if (messageType == ChatMessageType.PRIVATE_SENT && chatController.privateMode() < ChatMode.OFF) {
					int lineY3 = layout.unobscuredViewportHeight() - 5 - visibleLine * 13;
					font.drawText("To " + sender + ": " + chatController.history().messages[messageIndex], 4, lineY3, 0);
					font.drawText("To " + sender + ": " + chatController.history().messages[messageIndex], 4, lineY3 - 1, 65535);
					if (++visibleLine >= 5)
						return;
				}
			}

	}

	/**
	 * Consumes completed on-demand resource requests and installs models, maps, and
	 * music.
	 */
	public void processOnDemandRequests() {
		do {
			OnDemandRequest request;
			do {
				request = onDemandFetcher.poll();
				if (request == null)
					return;
				if (request.type == OnDemandFetcher.MODEL) {
					Model.loadModelHeader(request.buffer, request.id);
					if ((onDemandFetcher.getModelIndex(request.id) & 0x62) != 0) {
						sidebarRedraw = true;
						if (interfaceController.state().chatboxInterfaceId != -1 || interfaceController.state().dialogueInterfaceId != -1)
							chatboxRedraw = true;
					}
				}
				if (request.type == OnDemandFetcher.ANIMATION && request.buffer != null)
					AnimationFrame.load(request.buffer);
				musicController.acceptOnDemandRequest(request);
				if (request.type == OnDemandFetcher.MAP && regionManager.loadingStage == RegionManager.STAGE_LOADING)
					regionManager.acceptMapFile(request);
			} while (request.type != OnDemandFetcher.LOCATION_PREFETCH || !onDemandFetcher.isLandscapeFile(request.id));
			Region.requestGameObjectModels(new Buffer(request.buffer), onDemandFetcher);
		} while (true);
	}

	/**
	 * Performs the revision-377 login handshake and applies the full
	 * successful-login subsystem reset.
	 *
	 * @param loginUsername the login username
	 * @param loginPassword the login password
	 * @param reconnecting  the reconnecting
	 */
	public void login(String loginUsername, String loginPassword, boolean reconnecting) {
		loginSession.login(loginUsername, loginPassword, reconnecting, lowMemory);
	}

	/**
	 * Applies the runtime reset associated with a full successful login response.
	 *
	 * @param rights server-supplied player rights
	 * @param flagged whether the account is flagged
	 */
	private void handleFullLogin(int rights, boolean flagged) {
		playerRights = rights;
		accountFlagged = flagged;
		lastClickTime = 0L;
		mouseTelemetryRepeatCount = 0;
		mouseRecorder.sampleCount = 0;
		super.hasFocus = true;
		windowFocusReported = true;
		loggedIn = true;
		networkSession.resetPacketState();
		systemUpdateTimer = 0;
		logoutTimer = 0;
		hintIconType = 0;
		menuController.state().count = 0;
		super.idleCycles = 0;

		interfaceController.state().itemSelected = 0;
		interfaceController.state().spellSelected = 0;
		regionManager.loadingStage = RegionManager.STAGE_UNLOADED;
		soundEffectQueue.resetForLogin();
		cameraController.randomizeLoginOffsets();
		minimapRenderer.randomizeLoginOffsets();
		minimapRenderer.state = 0;
		lastMinimapPlane = -1;
		destinationX = 0;
		destinationY = 0;
		localPlayer = actorSynchronizer.reset();
		worldState.resetTransientState();
		socialManager.resetForLogin();
		unloadInterface(interfaceController.state().dialogueInterfaceId);
		unloadInterface(interfaceController.state().chatboxInterfaceId);
		unloadInterface(interfaceController.state().openInterfaceId);
		unloadInterface(interfaceController.state().fullscreenInterfaceId);
		unloadInterface(interfaceController.state().fullscreenOverlayInterfaceId);
		unloadInterface(interfaceController.state().sidebarOverlayInterfaceId);
		unloadInterface(interfaceController.state().walkableInterfaceId);
		interfaceController.setActionPending(false);
		chatController.resetForLogin();
		multiCombatZone = 0;
		appearanceEditor.resetForLogin();

		for (int actionIndex = 0; actionIndex < 5; actionIndex++) {
			playerActions[actionIndex] = null;
			playerActionLowPriority[actionIndex] = false;
		}

		groundItemAction26Counter = 0;
		inventoryAction227Counter = 0;
		npcAction118Counter = 0;
		groundItemAction684Counter = 0;
		inventoryAction961Counter = 0;
		createGameScreenBuffers();
	}

	/** Applies the partial reset associated with login response code 15. */
	private void handleReconnectAccepted() {
		loggedIn = true;
		networkSession.resetPacketState();
		systemUpdateTimer = 0;
		menuController.state().count = 0;
		regionManager.loadingStartTime = System.currentTimeMillis();
	}

	/**
	 * Resolves a scene object footprint and routes the local player into
	 * interaction range.
	 *
	 * @param tileY the local scene-tile Y coordinate
	 * @param tileX the local scene-tile X coordinate
	 * @param uid   the uid
	 * @return true when the requested condition/action succeeds; otherwise false
	 */
	private boolean walkToGameObject(int tileY, int tileX, int uid) {
		int objectId = uid >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK;
		int config = worldState.scene.getConfig(currentPlane, tileX, tileY, uid);
		if (config == -1)
			return false;
		int type = SceneConfig.type(config);
		int orientation = SceneConfig.orientation(config);
		if (type == 10 || type == 11 || type == 22) {
			GameObjectDefinition definition = GameObjectDefinition.lookup(objectId);
			int width;
			int height;
			if (orientation == 0 || orientation == 2) {
				width = definition.sizeX;
				height = definition.sizeY;
			} else {
				width = definition.sizeY;
				height = definition.sizeX;
			}
			int accessMask = definition.surroundings;
			if (orientation != 0)
				accessMask = (accessMask << orientation & 0xf) + (accessMask >> 4 - orientation);
			walkTo(true, tileX, tileY, width, height, MovementPacketEncoder.INTERACTION, 0, 0, accessMask);
		} else {
			walkTo(true, tileX, tileY, 0, 0, MovementPacketEncoder.INTERACTION, type + 1, orientation, 0);
		}
		crossX = clickX;
		crossY = clickY;
		crossType = 2;
		crossCycle = 0;
		return true;
	}



	/** Delegates NPC menu construction to {@link MenuController}.
	 * @param definition NPC definition
	 * @param tileY local tile Y
	 * @param tileX local tile X
	 * @param npcIndex NPC index
	 */
	public void buildNpcMenu(NpcDefinition definition, int tileY, int tileX, int npcIndex) {
		menuController.buildNpcMenu(definition, tileY, tileX, npcIndex, localPlayer);
	}



	/**
	 * Draws the chatbox, prompts, item-search results, dialogue interfaces, and
	 * message history.
	 */
	public void drawChatbox() {
		chatboxBuffer.bindRaster();
		Rasterizer3D.scanlineOffsets = chatboxScanlineOffsets;
		chatboxBackground.draw(0, 0);
		if (chatController.isPromptRaised()) {
			boldFont.drawCenteredText(chatController.promptMessage(), 239, 40, 0);
			boldFont.drawCenteredText(chatController.promptInput() + "*", 239, 60, 128);
		} else if (chatController.inputDialogState() == 1) {
			boldFont.drawCenteredText("Enter amount:", 239, 40, 0);
			boldFont.drawCenteredText(chatController.inputDialogText() + "*", 239, 60, 128);
		} else if (chatController.inputDialogState() == 2) {
			boldFont.drawCenteredText("Enter name:", 239, 40, 0);
			boldFont.drawCenteredText(chatController.inputDialogText() + "*", 239, 60, 128);
		} else if (chatController.inputDialogState() == 3) {
			if (chatController.inputDialogText() != itemSearchQuery) {
				searchItems(chatController.inputDialogText());
				itemSearchQuery = chatController.inputDialogText();
			}
			TypeFace searchFont = plainFont;
			Rasterizer.setCoordinates(0, 0, 463, 77);
			for (int resultIndex = 0; resultIndex < itemSearchResultCount; resultIndex++) {
				int resultY = (18 + resultIndex * 14) - itemSearchScrollOffset;
				if (resultY > 0 && resultY < 110)
					searchFont.drawCenteredText(itemSearchResultNames[resultIndex], 239, resultY, 0);
			}

			Rasterizer.resetCoordinates();
			if (itemSearchResultCount > 5)
				drawScrollbar(itemSearchScrollOffset, 463, 77, itemSearchResultCount * 14 + 7, 0);
			if (chatController.inputDialogText().length() == 0)
				boldFont.drawCenteredText("Enter object name", 239, 40, 255);
			else if (itemSearchResultCount == 0)
				boldFont.drawCenteredText("No matching objects found, please shorten search", 239, 40, 0);
			searchFont.drawCenteredText(chatController.inputDialogText() + "*", 239, 90, 0);
			Rasterizer.drawHorizontalLine(0, 77, 479, 0);
		} else if (chatController.clickToContinueMessage() != null) {
			boldFont.drawCenteredText(chatController.clickToContinueMessage(), 239, 40, 0);
			boldFont.drawCenteredText("Click to continue", 239, 60, 128);
		} else if (interfaceController.state().chatboxInterfaceId != -1)
			drawInterface(0, 0, Widget.get(interfaceController.state().chatboxInterfaceId), 0);
		else if (interfaceController.state().dialogueInterfaceId != -1) {
			drawInterface(0, 0, Widget.get(interfaceController.state().dialogueInterfaceId), 0);
		} else {
			TypeFace chatFont = plainFont;
			int visibleLine = 0;
			Rasterizer.setCoordinates(0, 0, 463, 77);
			for (int messageIndex = 0; messageIndex < 100; messageIndex++)
				if (chatController.history().messages[messageIndex] != null) {
					int messageType = chatController.history().types[messageIndex];
					int lineY = (70 - visibleLine * 14) + chatController.scrollOffset();
					String sender = chatController.history().senders[messageIndex];
					byte rightsIcon = 0;
					if (sender != null && sender.startsWith("@cr1@")) {
						sender = sender.substring(5);
						rightsIcon = 1;
					}
					if (sender != null && sender.startsWith("@cr2@")) {
						sender = sender.substring(5);
						rightsIcon = 2;
					}
					if (messageType == ChatMessageType.GAME) {
						if (lineY > 0 && lineY < 110)
							chatFont.drawText(chatController.history().messages[messageIndex], 4, lineY, 0);
						visibleLine++;
					}
					if ((messageType == ChatMessageType.PUBLIC_PRIVILEGED || messageType == ChatMessageType.PUBLIC) && (messageType == ChatMessageType.PUBLIC_PRIVILEGED || chatController.publicMode() == ChatMode.ON
							|| chatController.publicMode() == ChatMode.FRIENDS && isFriendOrSelf(sender))) {
						if (lineY > 0 && lineY < 110) {
							int textX = 4;
							if (rightsIcon == 1) {
								moderatorIcons[0].draw(textX, lineY - 12);
								textX += 14;
							}
							if (rightsIcon == 2) {
								moderatorIcons[1].draw(textX, lineY - 12);
								textX += 14;
							}
							chatFont.drawText(sender + ":", textX, lineY, 0);
							textX += chatFont.getFormattedTextWidth(sender) + 8;
							chatFont.drawText(chatController.history().messages[messageIndex], textX, lineY, 255);
						}
						visibleLine++;
					}
					if ((messageType == ChatMessageType.PRIVATE_RECEIVED || messageType == ChatMessageType.PRIVATE_RECEIVED_PRIVILEGED) && chatController.splitPrivateChat() == 0 && (messageType == ChatMessageType.PRIVATE_RECEIVED_PRIVILEGED
							|| chatController.privateMode() == ChatMode.ON || chatController.privateMode() == ChatMode.FRIENDS && isFriendOrSelf(sender))) {
						if (lineY > 0 && lineY < 110) {
							int textX2 = 4;
							chatFont.drawText("From", textX2, lineY, 0);
							textX2 += chatFont.getFormattedTextWidth("From ");
							if (rightsIcon == 1) {
								moderatorIcons[0].draw(textX2, lineY - 12);
								textX2 += 14;
							}
							if (rightsIcon == 2) {
								moderatorIcons[1].draw(textX2, lineY - 12);
								textX2 += 14;
							}
							chatFont.drawText(sender + ":", textX2, lineY, 0);
							textX2 += chatFont.getFormattedTextWidth(sender) + 8;
							chatFont.drawText(chatController.history().messages[messageIndex], textX2, lineY, 0x800000);
						}
						visibleLine++;
					}
					if (messageType == ChatMessageType.TRADE_REQUEST && (chatController.tradeMode() == ChatMode.ON || chatController.tradeMode() == ChatMode.FRIENDS && isFriendOrSelf(sender))) {
						if (lineY > 0 && lineY < 110)
							chatFont.drawText(sender + " " + chatController.history().messages[messageIndex], 4, lineY, 0x800080);
						visibleLine++;
					}
					if (messageType == ChatMessageType.PRIVATE_STATUS && chatController.splitPrivateChat() == 0 && chatController.privateMode() < ChatMode.OFF) {
						if (lineY > 0 && lineY < 110)
							chatFont.drawText(chatController.history().messages[messageIndex], 4, lineY, 0x800000);
						visibleLine++;
					}
					if (messageType == ChatMessageType.PRIVATE_SENT && chatController.splitPrivateChat() == 0 && chatController.privateMode() < ChatMode.OFF) {
						if (lineY > 0 && lineY < 110) {
							chatFont.drawText("To " + sender + ":", 4, lineY, 0);
							chatFont.drawText(chatController.history().messages[messageIndex],
									12 + chatFont.getFormattedTextWidth("To " + sender), lineY, 0x800000);
						}
						visibleLine++;
					}
					if (messageType == ChatMessageType.CHALLENGE_REQUEST && (chatController.tradeMode() == ChatMode.ON || chatController.tradeMode() == ChatMode.FRIENDS && isFriendOrSelf(sender))) {
						if (lineY > 0 && lineY < 110)
							chatFont.drawText(sender + " " + chatController.history().messages[messageIndex], 4, lineY, 0x7e3200);
						visibleLine++;
					}
				}

			Rasterizer.resetCoordinates();
			chatController.setContentHeight(visibleLine * 14 + 7);
			if (chatController.contentHeight() < ChatController.MIN_CONTENT_HEIGHT)
				chatController.setContentHeight(ChatController.MIN_CONTENT_HEIGHT);
			drawScrollbar(chatController.contentHeight() - chatController.scrollOffset() - 77, 463, 77,
					chatController.contentHeight(), 0);
			String localDisplayName;
			if (localPlayer != null && localPlayer.name != null)
				localDisplayName = localPlayer.name;
			else
				localDisplayName = TextFormatter.formatDisplayName(loginScreen.username);
			chatFont.drawText(localDisplayName + ":", 4, 90, 0);
			chatFont.drawText(chatController.input() + "*", 6 + chatFont.getFormattedTextWidth(localDisplayName + ": "), 90, 255);
			Rasterizer.drawHorizontalLine(0, 77, 479, 0);
		}
		if (menuController.state().open && menuController.state().screenArea == 2)
			drawContextMenu();
		chatboxBuffer.draw(super.graphics, layout.chatboxX(), layout.chatboxY());
		viewportBuffer.bindRaster();
		Rasterizer3D.scanlineOffsets = viewportScanlineOffsets;
	}

	/**
	 * Decrements player and NPC overhead-text lifetimes.
	 */
	public void updateOverheadTextCycles() {
		for (int playerListIndex = -1; playerListIndex < actorSynchronizer.playerCount; playerListIndex++) {
			int playerIndex;
			if (playerListIndex == -1)
				playerIndex = ActorSynchronizer.LOCAL_PLAYER_INDEX;
			else
				playerIndex = actorSynchronizer.playerIndices[playerListIndex];
			Player player = actorSynchronizer.players[playerIndex];
			if (player != null && ((Actor) (player)).overheadTextCyclesRemaining > 0) {
				player.overheadTextCyclesRemaining--;
				if (((Actor) (player)).overheadTextCyclesRemaining == 0)
					player.overheadText = null;
			}
		}

		for (int npcListIndex = 0; npcListIndex < actorSynchronizer.npcCount; npcListIndex++) {
			int npcIndex = actorSynchronizer.npcIndices[npcListIndex];
			Npc npc = actorSynchronizer.npcs[npcIndex];
			if (npc != null && ((Actor) (npc)).overheadTextCyclesRemaining > 0) {
				npc.overheadTextCyclesRemaining--;
				if (((Actor) (npc)).overheadTextCyclesRemaining == 0)
					npc.overheadText = null;
			}
		}

	}

	/**
	 * Retrieves and validates the nine-entry startup archive CRC table.
	 */
	private void loadArchiveCrcs() {
		resourceLoader.fetchArchiveCrcs(this::openJaggrabStream, this::drawLoadingText);
	}

	/**
	 * Draws the minimap, compass, map functions, ground items, actors, hints, and
	 * destination marker.
	 */
	private void drawMinimap() {
		MinimapRenderer.Assets assets = new MinimapRenderer.Assets();
		assets.minimapBuffer = minimapBuffer;
		assets.sceneBuffer = viewportBuffer;
		assets.minimapMask = minimapBackground;
		assets.compass = compassSprite;
		assets.compassMaskWidths = compassMaskWidths;
		assets.compassMaskOffsets = compassMaskOffsets;
		assets.minimapMaskWidths = minimapMaskWidths;
		assets.minimapMaskOffsets = minimapMaskOffsets;
		assets.sceneScanlineOffsets = viewportScanlineOffsets;
		assets.groundItemDot = groundItemMapDot;
		assets.npcDot = npcMapDot;
		assets.playerDot = playerMapDot;
		assets.friendDot = friendMapDot;
		assets.teamDot = teamMapDot;
		assets.hintMarker = hintMapMarker;
		assets.destinationMarker = destinationMapMarker;
		assets.edgeArrow = minimapEdgeArrow;
		minimapRenderer.draw(worldState, actorSynchronizer, localPlayer, currentPlane, cameraController.followYaw,
				destinationX, destinationY, hintIconType, hintNpcIndex, hintTileX, hintTileY, hintPlayerIndex,
				gameCycle, regionManager.baseX, regionManager.baseY, assets, name -> isFriendOrSelf(name));
	}

	/**
	 * Formats a widget-script value using the renderer's overflow placeholder.
	 *
	 * @param value script value
	 * @return formatted value
	 */
	public String formatWidgetScriptValue(int value) {
		return WidgetRenderer.formatWidgetScriptValue(value);
	}

	/**
	 * Adds an ignored name through SocialManager and refreshes dependent interface
	 * state.
	 *
	 * @param encodedName the Base-37 encoded player name
	 */
	public void addIgnore(long encodedName) {
		if (socialManager.addIgnore(encodedName, networkSession.outgoing, this::addChatMessage))
			sidebarRedraw = true;
	}

	/**
	 * Runs one client logic cycle in either logged-in or login/title mode.
	 */
	public void processGameLoop() {
		if (duplicateClientError || loadingError || invalidHostError)
			return;
		gameCycle++;
		if (!loggedIn)
			processLoginScreenInput();
		else
			processLoggedInCycle();
		processOnDemandRequests();
	}

	/** Rebuilds the context menu through {@link MenuController}. */
	public void buildContextMenu() {
		menuController.buildContextMenu(super.mouseX, super.mouseY, systemUpdateTimer, plainFont, playerRights,
				localPlayer, worldState, actorSynchronizer, currentPlane, playerActions, playerActionLowPriority);
	}

	/**
	 * Returns the classic combat-level difference color tag.
	 * @param playerLevel target combat level
	 * @param localLevel local combat level
	 * @return color tag
	 */
	public static String getCombatLevelColorTag(int playerLevel, int localLevel) {
		return MenuController.getCombatLevelColorTag(playerLevel, localLevel);
	}

	/**
	 * Positions the 3D camera from a focal point, distance, pitch, and yaw.
	 *
	 * @param targetHeight the target height in tiles
	 * @param targetX      the target tile X coordinate
	 * @param pitch        the pitch
	 * @param distance     the distance
	 * @param yaw          the yaw
	 * @param targetY      the target tile Y coordinate
	 */
	private void positionCamera(int targetHeight, int targetX, int pitch, int distance, int yaw, int targetY) {
		cameraController.positionFromTarget(targetHeight, targetX, pitch, distance, yaw, targetY);
	}

	/**
	 * Removes an ignored name through SocialManager and refreshes dependent
	 * interface state.
	 *
	 * @param encodedName the Base-37 encoded player name
	 */
	public void removeIgnore(long encodedName) {
		if (socialManager.removeIgnore(encodedName, networkSession.outgoing))
			sidebarRedraw = true;
	}



	/**
	 * Enables the original low-memory configuration across rendering and region
	 * subsystems.
	 */
	public static void setLowMemory() {
		Scene.lowMemory = true;
		Rasterizer3D.lowMemory = true;
		lowMemory = true;
		Region.lowMemory = true;
		GameObjectDefinition.lowMemory = true;
	}

	/**
	 * Adds a friend through SocialManager and refreshes dependent interface state.
	 *
	 * @param encodedName the Base-37 encoded player name
	 */
	public void addFriend(long encodedName) {
		boolean membersAccount = accountMembershipStatus == 1;
		if (socialManager.addFriend(encodedName, membersAccount, localPlayer.name, networkSession.outgoing,
				this::addChatMessage))
			sidebarRedraw = true;
	}

	/**
	 * Populates dynamic widget content such as social lists, appearance preview,
	 * and account status.
	 *
	 * @param widget the widget being processed
	 */
	public void updateWidgetContent(Widget widget) {
		int contentType = widget.contentType;
		if (contentType >= WidgetContentType.FRIEND_NAME_FIRST && contentType <= WidgetContentType.FRIEND_NAME_LAST || contentType >= WidgetContentType.FRIEND_NAME_ALTERNATE_FIRST && contentType <= WidgetContentType.FRIEND_NAME_ALTERNATE_LAST) {
			if (contentType == WidgetContentType.FRIEND_NAME_FIRST && socialManager.friendListStatus == SocialManager.FRIEND_LIST_LOADING) {
				widget.text = "Loading friend list";
				widget.buttonType = Widget.BUTTON_NONE;
				return;
			}
			if (contentType == WidgetContentType.FRIEND_NAME_FIRST && socialManager.friendListStatus == SocialManager.FRIEND_LIST_CONNECTING) {
				widget.text = "Connecting to friendserver";
				widget.buttonType = Widget.BUTTON_NONE;
				return;
			}
			if (contentType == WidgetContentType.FRIEND_NAME_SECOND && socialManager.friendListStatus != SocialManager.FRIEND_LIST_READY) {
				widget.text = "Please wait...";
				widget.buttonType = Widget.BUTTON_NONE;
				return;
			}
			int friendCount = socialManager.friendCount;
			if (socialManager.friendListStatus != SocialManager.FRIEND_LIST_READY)
				friendCount = 0;
			if (contentType >= WidgetContentType.FRIEND_NAME_ALTERNATE_FIRST)
				contentType -= WidgetContentType.FRIEND_NAME_ALTERNATE_INDEX_OFFSET;
			else
				contentType -= WidgetContentType.FRIEND_NAME_FIRST;
			if (contentType >= friendCount) {
				widget.text = "";
				widget.buttonType = Widget.BUTTON_NONE;
				return;
			} else {
				widget.text = socialManager.friendNames[contentType];
				widget.buttonType = Widget.BUTTON_ACTION;
				return;
			}
		}
		if (contentType >= WidgetContentType.FRIEND_WORLD_FIRST && contentType <= WidgetContentType.FRIEND_WORLD_LAST || contentType >= WidgetContentType.FRIEND_WORLD_ALTERNATE_FIRST && contentType <= WidgetContentType.FRIEND_WORLD_ALTERNATE_LAST) {
			int friendCount2 = socialManager.friendCount;
			if (socialManager.friendListStatus != SocialManager.FRIEND_LIST_READY)
				friendCount2 = 0;
			if (contentType > WidgetContentType.FRIEND_NAME_ALTERNATE_LAST)
				contentType -= WidgetContentType.FRIEND_WORLD_ALTERNATE_INDEX_OFFSET;
			else
				contentType -= WidgetContentType.FRIEND_WORLD_FIRST;
			if (contentType >= friendCount2) {
				widget.text = "";
				widget.buttonType = Widget.BUTTON_NONE;
				return;
			}
			if (socialManager.friendWorlds[contentType] == 0)
				widget.text = "@red@Offline";
			else if (socialManager.friendWorlds[contentType] < 200) {
				if (socialManager.friendWorlds[contentType] == currentWorldId)
					widget.text = "@gre@World" + (socialManager.friendWorlds[contentType] - 9);
				else
					widget.text = "@yel@World" + (socialManager.friendWorlds[contentType] - 9);
			} else if (socialManager.friendWorlds[contentType] == currentWorldId)
				widget.text = "@gre@Classic" + (socialManager.friendWorlds[contentType] - 219);
			else
				widget.text = "@yel@Classic" + (socialManager.friendWorlds[contentType] - 219);
			widget.buttonType = Widget.BUTTON_ACTION;
			return;
		}
		if (contentType == WidgetContentType.FRIEND_LIST_SCROLL) {
			int friendCount3 = socialManager.friendCount;
			if (socialManager.friendListStatus != SocialManager.FRIEND_LIST_READY)
				friendCount3 = 0;
			widget.scrollHeight = friendCount3 * 15 + 20;
			if (widget.scrollHeight <= widget.height)
				widget.scrollHeight = widget.height + 1;
			return;
		}
		if (contentType >= WidgetContentType.IGNORE_NAME_FIRST && contentType <= WidgetContentType.IGNORE_NAME_LAST) {
			if ((contentType -= WidgetContentType.IGNORE_NAME_FIRST) == 0 && socialManager.friendListStatus == SocialManager.FRIEND_LIST_LOADING) {
				widget.text = "Loading ignore list";
				widget.buttonType = Widget.BUTTON_NONE;
				return;
			}
			if (contentType == WidgetContentType.FRIEND_NAME_FIRST && socialManager.friendListStatus == SocialManager.FRIEND_LIST_LOADING) {
				widget.text = "Please wait...";
				widget.buttonType = Widget.BUTTON_NONE;
				return;
			}
			int ignoreCount = socialManager.ignoreCount;
			if (socialManager.friendListStatus == SocialManager.FRIEND_LIST_LOADING)
				ignoreCount = 0;
			if (contentType >= ignoreCount) {
				widget.text = "";
				widget.buttonType = Widget.BUTTON_NONE;
				return;
			} else {
				widget.text = TextFormatter
						.formatDisplayName(Base37.decode(socialManager.ignoreEncodedNames[contentType]));
				widget.buttonType = Widget.BUTTON_ACTION;
				return;
			}
		}
		if (contentType == WidgetContentType.IGNORE_LIST_SCROLL) {
			widget.scrollHeight = socialManager.ignoreCount * 15 + 20;
			if (widget.scrollHeight <= widget.height)
				widget.scrollHeight = widget.height + 1;
			return;
		}
		if (contentType == WidgetContentType.APPEARANCE_PREVIEW) {
			appearanceEditor.updatePreview(widget, gameCycle, localPlayer);
			return;
		}
		if (contentType == WidgetContentType.SELECT_MALE_APPEARANCE) {
			appearanceEditor.updateGenderButton(widget, true);
			return;
		}
		if (contentType == WidgetContentType.SELECT_FEMALE_APPEARANCE) {
			appearanceEditor.updateGenderButton(widget, false);
			return;
		}
		if (contentType == WidgetContentType.REPORT_ABUSE_NAME) {
			widget.text = interfaceController.reportAbuseName();
			if (gameCycle % 20 < 10) {
				widget.text += "|";
				return;
			} else {
				widget.text += " ";
				return;
			}
		}
		if (contentType == WidgetContentType.REPORT_ABUSE_MUTE)
			if (playerRights >= 1) {
				if (interfaceController.reportAbuseMutePlayer()) {
					widget.color = 0xff0000;
					widget.text = "Moderator option: Mute player for 48 hours: <ON>";
				} else {
					widget.color = 0xffffff;
					widget.text = "Moderator option: Mute player for 48 hours: <OFF>";
				}
			} else {
				widget.text = "";
			}
		if (contentType == WidgetContentType.ACCOUNT_LAST_LOGIN) {
			int daysSinceLogin = accountCurrentDay - lastLoginDay;
			String lastLoginText;
			if (daysSinceLogin <= 0)
				lastLoginText = "earlier today";
			else if (daysSinceLogin == 1)
				lastLoginText = "yesterday";
			else
				lastLoginText = daysSinceLogin + " days ago";
			widget.text = "You last logged in @red@" + lastLoginText + "@bla@ from: @red@" + Signlink.dns;
		}
		if (contentType == WidgetContentType.ACCOUNT_RECOVERY_QUESTIONS)
			if (recoveryQuestionsDate == 0)
				widget.text = "\\nYou have not yet set any recovery questions.\\nIt is @lre@strongly@yel@ recommended that you do so.\\n\\nIf you don't you will be @lre@unable to recover your\\n@lre@password@yel@ if you forget it, or it is stolen.";
			else if (recoveryQuestionsDate <= accountCurrentDay) {
				widget.text = "\\n\\nRecovery Questions Last Set:\\n@gre@" + formatAccountDate(recoveryQuestionsDate);
			} else {
				int daysUntilRecoveryChange = (accountCurrentDay + 14) - recoveryQuestionsDate;
				String recoveryChangeText;
				if (daysUntilRecoveryChange <= 0)
					recoveryChangeText = "Earlier today";
				else if (daysUntilRecoveryChange == 1)
					recoveryChangeText = "Yesterday";
				else
					recoveryChangeText = daysUntilRecoveryChange + " days ago";
				widget.text = recoveryChangeText
						+ " you requested@lre@ new recovery\\n@lre@questions.@yel@ The requested change will occur\\non: @lre@"
						+ formatAccountDate(recoveryQuestionsDate)
						+ "\\n\\nIf you do not remember making this request\\ncancel it immediately, and change your password.";
			}
		if (contentType == WidgetContentType.ACCOUNT_UNREAD_MESSAGES) {
			String messageSummary;
			if (unreadMessageCount == 0)
				messageSummary = "@yel@0 unread messages";
			else if (unreadMessageCount == 1)
				messageSummary = "@gre@1 unread message";
			else
				messageSummary = "@gre@" + unreadMessageCount + " unread messages";
			widget.text = "You have " + messageSummary + "\\nin your message centre.";
		}
		if (contentType == WidgetContentType.ACCOUNT_PASSWORD_CHANGE)
			if (lastPasswordChangeDate <= 0 || lastPasswordChangeDate > accountCurrentDay + 10)
				widget.text = "Last password change:\\n@gre@Never changed";
			else
				widget.text = "Last password change:\\n@gre@" + formatAccountDate(lastPasswordChangeDate);
		if (contentType == WidgetContentType.ACCOUNT_MEMBERSHIP_STATUS)
			if (membershipDays > 2 && !membersWorld)
				widget.text = "This is a non-members\\nworld. To enjoy your\\nmembers benefits we\\nrecommend you play on a\\nmembers world instead.";
			else if (membershipDays > 2)
				widget.text = "\\n\\nYou have @gre@" + membershipDays + "@yel@ days of\\nmember credit remaining.";
			else if (membershipDays > 0)
				widget.text = "You have @gre@" + membershipDays
						+ "@yel@ days of\\nmember credit remaining.\\n\\n@lre@Credit low! Renew now\\n@lre@to avoid losing members.";
			else
				widget.text = "You are not a member.\\n\\nChoose to subscribe and\\nyou'll get loads of extra\\nbenefits and features.";
		if (contentType == WidgetContentType.ACCOUNT_MEMBERSHIP_HELP)
			if (membershipDays > 2 && !membersWorld)
				widget.text = "To switch to a members-only world:\\n1) Logout and return to the world selection page.\\n2) Choose one of the members world with a gold star next to it's name.\\n\\nIf you prefer you can continue to use this world,\\nbut members only features will be unavailable here.";
			else if (membershipDays > 0)
				widget.text = "To extend or cancel a subscription:\\n1) Logout and return to the frontpage of this website.\\n2)Choose the relevant option from the 'membership' section.\\n\\nNote: If you are a credit card subscriber a top-up payment will\\nautomatically be taken when 3 days credit remain.\\n(unless you cancel your subscription, which can be done at any time.)";
			else
				widget.text = "To start a subscripton:\\n1) Logout and return to the frontpage of this website.\\n2) Choose 'Start a new subscription'";
		if (contentType == WidgetContentType.ACCOUNT_RECOVERY_HELP) {
			if (recoveryQuestionsDate > accountCurrentDay) {
				widget.text = "To cancel this request:\\n1) Logout and return to the frontpage of this website.\\n2) Choose 'Cancel recovery questions'.";
				return;
			}
			widget.text = "To change your recovery questions:\\n1) Logout and return to the frontpage of this website.\\n2) Choose 'Set new recovery questions'.";
		}
	}

	/**
	 * Formats an account-status day count using the original client calendar
	 * convention.
	 *
	 * @param dayValue the day value
	 * @return the resulting text
	 */
	public String formatAccountDate(int dayValue) {
		if (dayValue > accountCurrentDay + 10) {
			return "Unknown";
		} else {
			long timestampMillis = ((long) dayValue + 11745L) * 0x5265c00L;
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(new Date(timestampMillis));
			int dayOfMonth = calendar.get(5);
			int monthIndex = calendar.get(2);
			int year = calendar.get(1);
			String monthNames[] = { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov",
					"Dec" };
			return dayOfMonth + "-" + monthNames[monthIndex] + "-" + year;
		}
	}

	/**
	 * Returns social state to the application packet adapter.
	 * @return social manager
	 */
	SocialManager packetSocialManager() { return socialManager; }

	/**
	 * Returns chat state to the application packet adapter.
	 * @return chat controller
	 */
	ChatController packetChatController() { return chatController; }

	/**
	 * Returns interface state to the application packet adapter.
	 * @return interface controller
	 */
	InterfaceController packetInterfaceController() { return interfaceController; }

	/**
	 * Returns the current login username to the application packet adapter.
	 * @return login username
	 */
	String packetLoginUsername() { return loginScreen.username; }

	/**
	 * Returns sound-effect state to the application packet adapter.
	 * @return sound-effect queue
	 */
	SoundEffectQueue packetSoundEffectQueue() { return soundEffectQueue; }

	/**
	 * Returns music state to the application packet adapter.
	 * @return music controller
	 */
	MusicController packetMusicController() { return musicController; }

	/**
	 * Returns widget runtime state to the application packet adapter.
	 * @return widget runtime
	 */
	WidgetRuntime packetWidgetRuntime() { return widgetRuntime; }

	/**
	 * Returns actor synchronization state to the application packet adapter.
	 * @return actor synchronizer
	 */
	ActorSynchronizer packetActorSynchronizer() { return actorSynchronizer; }

	/**
	 * Returns camera state to the application packet adapter.
	 * @return camera controller
	 */
	CameraController packetCameraController() { return cameraController; }

	/**
	 * Returns minimap state to the application packet adapter.
	 * @return minimap renderer
	 */
	MinimapRenderer packetMinimapRenderer() { return minimapRenderer; }

	/**
	 * Returns region state to the application packet adapter.
	 * @return region manager
	 */
	RegionManager packetRegionManager() { return regionManager; }

	/**
	 * Returns varp state to the application packet adapter.
	 * @return varp state
	 */
	VarpState packetVarpState() { return varpState; }

	/**
	 * Returns world state to the application packet adapter.
	 * @return world state
	 */
	WorldState packetWorldState() { return worldState; }

	/**
	 * Returns zone-update state to the application packet adapter.
	 * @return zone-update handler
	 */
	ZoneUpdateHandler packetZoneUpdates() { return zoneUpdates; }

	/**
	 * Returns the actor chat callback to the application packet adapter.
	 * @return actor chat handler
	 */
	ActorSynchronizer.ChatHandler packetActorChatHandler() { return actorChatHandler; }

	/**
	 * Returns one current client varp value for definition morphing.
	 *
	 * @param varpId varp identifier
	 * @return current varp value
	 */
	public int getVarp(int varpId) {
		return varpState.get(varpId);
	}

	/**
	 * Applies a changed varp to client settings such as brightness, music, sound,
	 * and chat options.
	 *
	 * @param varpId the varp identifier
	 */
	public void applyVarp(int varpId) {
		int clientCode = Varp.definitions[varpId].clientCode;
		if (clientCode == 0)
			return;
		int varpValue = varpState.get(varpId);
		if (clientCode == 1) {
			if (varpValue == 1)
				Rasterizer3D.setBrightness(0.90000000000000002D);
			if (varpValue == 2)
				Rasterizer3D.setBrightness(0.80000000000000004D);
			if (varpValue == 3)
				Rasterizer3D.setBrightness(0.69999999999999996D);
			if (varpValue == 4)
				Rasterizer3D.setBrightness(0.59999999999999998D);
			ItemSpriteFactory.clearCache();
			gameScreenRedraw = true;
		}
		if (clientCode == 3)
			musicController.applySetting(varpValue, lowMemory, onDemandFetcher::request);
		if (clientCode == 4)
			soundEffectQueue.applySetting(varpValue);
		if (clientCode == 5)
			oneButtonMouseMode = varpValue;
		if (clientCode == 6)
			chatEffects = varpValue;
		if (clientCode == 8) {
			chatController.setSplitPrivateChat(varpValue);
			chatboxRedraw = true;
		}
		if (clientCode == 9)
			inventoryRearrangeMode = varpValue;
	}



	/**
	 * Updates the hard-coded tutorial-island/region suppression flag from the local
	 * player position.
	 */
	public void updateTutorialIslandFlag() {
		tutorialIslandFlag = 0;
		int worldX = (((Actor) (localPlayer)).x >> 7) + regionManager.baseX;
		int worldY = (((Actor) (localPlayer)).y >> 7) + regionManager.baseY;

		if (worldX >= 3053 && worldX <= 3156 && worldY >= 3056 && worldY <= 3136)
			tutorialIslandFlag = 1;
		if (worldX >= 3072 && worldX <= 3118 && worldY >= 9492 && worldY <= 9535)
			tutorialIslandFlag = 1;
		if (tutorialIslandFlag == 1 && worldX >= 3139 && worldX <= 3199 && worldY >= 3008 && worldY <= 3062)
			tutorialIslandFlag = 0;
	}

	/** Opens the context menu through {@link MenuController}. */
	public void openContextMenu() {
		menuController.openContextMenu(boldFont, super.clickX, super.clickY);
	}

	/**
	 * Draws viewport-level overlays such as FPS/memory diagnostics, update timer,
	 * and multi-combat icon.
	 */
	public void drawViewportOverlays() {
		drawSplitPrivateChat();
		if (crossType == 1)
			crossSprites[crossCycle / 100].drawImage(crossX - 8 - 4, crossY - 8 - 4);
		if (crossType == 2)
			crossSprites[4 + crossCycle / 100].drawImage(crossX - 8 - 4, crossY - 8 - 4);
		if (interfaceController.state().walkableInterfaceId != -1) {
			widgetRuntime.updateAnimations(animationCycleDelta, interfaceController.state().walkableInterfaceId);
			drawInterface(0, 0, Widget.get(interfaceController.state().walkableInterfaceId), 0);
		}
		if (interfaceController.state().openInterfaceId != -1) {
			widgetRuntime.updateAnimations(animationCycleDelta, interfaceController.state().openInterfaceId);
			Widget openInterface = Widget.get(interfaceController.state().openInterfaceId);
			int interfaceX = layout.centeredInterfaceX(openInterface.width);
			int interfaceY = layout.centeredInterfaceY(openInterface.height);
			drawInterface(interfaceY, interfaceX, openInterface, 0);
		}
		updateTutorialIslandFlag();
		if (!menuController.state().open) {
			buildContextMenu();
			drawMenuTooltip();
		} else if (menuController.state().screenArea == 0)
			drawContextMenu();
		if (multiCombatZone == 1)
			multiCombatOverlay.drawImage(layout.unobscuredViewportWidth() - 40, layout.unobscuredViewportHeight() - 38);
		if (showFps) {
			int rightAlignedX = layout.unobscuredViewportWidth() - 5;
			int lineY = 20;
			int textColor = 0xffff00;
			if (super.fps < 30 && lowMemory)
				textColor = 0xff0000;
			if (super.fps < 20 && !lowMemory)
				textColor = 0xff0000;
			plainFont.drawRightAlignedText("Fps:" + super.fps, rightAlignedX, lineY, textColor);
			lineY += 15;
			Runtime runtime = Runtime.getRuntime();
			int memoryKb = (int) ((runtime.totalMemory() - runtime.freeMemory()) / 1024L);
			textColor = 0xffff00;
			if (memoryKb > 0x2000000 && lowMemory)
				textColor = 0xff0000;
			if (memoryKb > 0x4000000 && !lowMemory)
				textColor = 0xff0000;
			plainFont.drawRightAlignedText("Mem:" + memoryKb + "k", rightAlignedX, lineY, 0xffff00);
			lineY += 15;
		}
		if (systemUpdateTimer != 0) {
			int secondsRemaining = systemUpdateTimer / 50;
			int minutesRemaining = secondsRemaining / 60;
			secondsRemaining %= 60;
			if (secondsRemaining < 10)
				plainFont.drawText("System update in: " + minutesRemaining + ":0" + secondsRemaining, 4, layout.unobscuredViewportHeight() - 5, 0xffff00);
			else
				plainFont.drawText("System update in: " + minutesRemaining + ":" + secondsRemaining, 4, layout.unobscuredViewportHeight() - 5, 0xffff00);
			systemUpdateKeepaliveCounter++;
			if (systemUpdateKeepaliveCounter > 112) {
				systemUpdateKeepaliveCounter = 0;
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.SYSTEM_UPDATE_KEEPALIVE);
				networkSession.outgoing.writeInt(0);
			}
		}
	}



	/** Delegates split-private-chat menu construction to {@link MenuController}. */
	public void buildSplitPrivateChatMenu() {
		menuController.buildSplitPrivateChatMenu(systemUpdateTimer, super.mouseX, super.mouseY, plainFont, playerRights,
				localPlayer.name);
	}

	/**
	 * Delegates chatbox message menu construction to {@link MenuController}.
	 * @param mouseY chatbox-local mouse Y coordinate
	 */
	public void buildChatboxMessageMenu(int mouseY) {
		menuController.buildChatboxMessageMenu(mouseY, playerRights, localPlayer.name);
	}

	/**
	 * Rebuilds the cached minimap scene image and map-function markers for the
	 * current plane.
	 *
	 * @param plane the scene plane
	 */
	private void rebuildMinimap(int plane) {
		minimapRenderer.rebuild(worldState, plane, mapSceneSprites, mapFunctionSprites, viewportBuffer,
				viewportScanlineOffsets, networkSession.outgoing);
	}

	/**
	 * Selects the normal render plane using camera pitch, roof flags, and tile-line
	 * traversal.
	 *
	 * @return the resulting numeric value
	 */
	private int selectNormalRenderPlane() {
		return cameraController.selectNormalRenderPlane(worldState, currentPlane, localPlayer, networkSession.outgoing);
	}

	/**
	 * Selects the render plane while a cinematic camera is active.
	 *
	 * @return the resulting numeric value
	 */
	private int selectCinematicRenderPlane() {
		return cameraController.selectCinematicRenderPlane(worldState, currentPlane);
	}

	/**
	 * Starts a client-owned worker thread with the requested priority.
	 *
	 * @param runnable the runnable
	 * @param priority the requested Java thread priority
	 */
	public void startThread(Runnable runnable, int priority) {
		if (priority > 10)
			priority = 10;
		Signlink.startThread(runnable, priority);
	}

	/**
	 * Adds players matching the requested render pass to the scene.
	 *
	 * @param localOnly the local only
	 */
	private void addPlayersToScene(boolean localOnly) {
		sceneEntityRenderer.addPlayers(worldState, actorSynchronizer, localPlayer, currentPlane, gameCycle, lowMemory,
				localOnly);
	}

	/**
	 * Normalizes and dispatches one menu action while preserving its numeric
	 * revision-377 action ID.
	 *
	 * @param menuIndex the menu index
	 */
	public void dispatchMenuAction(int menuIndex) {
		if (menuIndex < 0)
			return;
		int cmd2 = menuController.state().actionCmd2[menuIndex];
		int cmd3 = menuController.state().actionCmd3[menuIndex];
		int actionId = MenuState.normalizeActionId(menuController.state().actionIds[menuIndex]);
		int cmd1 = menuController.state().actionCmd1[menuIndex];
		if (chatController.inputDialogState() != 0 && actionId != MenuState.CANCEL_ACTION) {
			chatController.setInputDialogState(0);
			chatboxRedraw = true;
		}

		dispatchPlayerMenuAction(actionId, cmd1, cmd2, cmd3, menuIndex);
		dispatchNpcMenuAction(actionId, cmd1, cmd2, cmd3, menuIndex);
		dispatchObjectMenuAction(actionId, cmd1, cmd2, cmd3, menuIndex);
		dispatchGroundItemMenuAction(actionId, cmd1, cmd2, cmd3, menuIndex);
		if (dispatchInventoryMenuAction(actionId, cmd1, cmd2, cmd3, menuIndex))
			return;
		if (dispatchWidgetMenuAction(actionId, cmd1, cmd2, cmd3, menuIndex))
			return;
		dispatchSocialMenuAction(actionId, cmd1, cmd2, cmd3, menuIndex);
		dispatchMiscMenuAction(actionId, cmd1, cmd2, cmd3, menuIndex);

		interfaceController.state().itemSelected = 0;
		interfaceController.state().spellSelected = 0;
		sidebarRedraw = true;
	}

	// Player target actions: 200, 408, 493, 596, 677, 876, 918.
	/**
	 * Handles normalized menu actions targeting players.
	 *
	 * @param actionId  the normalized menu action identifier
	 * @param cmd1      the cmd1
	 * @param cmd2      the cmd2
	 * @param cmd3      the cmd3
	 * @param menuIndex the menu index
	 */
	private void dispatchPlayerMenuAction(int actionId, int cmd1, int cmd2, int cmd3, int menuIndex) {
		if (actionId == MenuState.PLAYER_OPTION_1) {
			Player player = actorSynchronizer.players[cmd1];
			if (player != null) {
				walkTo(false, ((Actor) (player)).pathX[0], ((Actor) (player)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.PLAYER_OPTION_1);
				networkSession.outgoing.writeShortAddLE(cmd1);
			}
		}
		if (actionId == MenuState.PLAYER_OPTION_5) {
			Player player2 = actorSynchronizer.players[cmd1];
			if (player2 != null) {
				walkTo(false, ((Actor) (player2)).pathX[0], ((Actor) (player2)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.PLAYER_OPTION_5);
				networkSession.outgoing.writeShortAdd(cmd1);
			}
		}
		if (actionId == MenuState.PLAYER_OPTION_4) {
			Player player3 = actorSynchronizer.players[cmd1];
			if (player3 != null) {
				walkTo(false, ((Actor) (player3)).pathX[0], ((Actor) (player3)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.PLAYER_OPTION_4);
				networkSession.outgoing.writeShortLE(cmd1);
			}
		}
		if (actionId == MenuState.PLAYER_OPTION_2) {
			Player player4 = actorSynchronizer.players[cmd1];
			if (player4 != null) {
				walkTo(false, ((Actor) (player4)).pathX[0], ((Actor) (player4)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.PLAYER_OPTION_2);
				networkSession.outgoing.writeShortAdd(cmd1);
			}
		}
		if (actionId == MenuState.CAST_SPELL_ON_PLAYER) {
			Player player5 = actorSynchronizer.players[cmd1];
			if (player5 != null) {
				walkTo(false, ((Actor) (player5)).pathX[0], ((Actor) (player5)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.CAST_SPELL_ON_PLAYER);
				networkSession.outgoing.writeShort(cmd1);
				networkSession.outgoing.writeShortLE(interfaceController.state().selectedSpellWidgetId);
			}
		}
		if (actionId == MenuState.USE_ITEM_ON_PLAYER) {
			Player player6 = actorSynchronizer.players[cmd1];
			if (player6 != null) {
				walkTo(false, ((Actor) (player6)).pathX[0], ((Actor) (player6)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.USE_ITEM_ON_PLAYER);
				networkSession.outgoing.writeShortLE(interfaceController.state().selectedItemId);
				networkSession.outgoing.writeShortAddLE(interfaceController.state().selectedItemSlot);
				networkSession.outgoing.writeShort(interfaceController.state().selectedItemWidgetId);
				networkSession.outgoing.writeShortAdd(cmd1);
			}
		}
		if (actionId == MenuState.PLAYER_OPTION_3) {
			Player player7 = actorSynchronizer.players[cmd1];
			if (player7 != null) {
				walkTo(false, ((Actor) (player7)).pathX[0], ((Actor) (player7)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.PLAYER_OPTION_3);
				networkSession.outgoing.writeShortLE(cmd1);
			}
		}
	}

	// NPC target actions: 67, 118, 318, 347, 432, 553, 921, 1668.
	/**
	 * Handles normalized menu actions targeting NPCs.
	 *
	 * @param actionId  the normalized menu action identifier
	 * @param cmd1      the cmd1
	 * @param cmd2      the cmd2
	 * @param cmd3      the cmd3
	 * @param menuIndex the menu index
	 */
	private void dispatchNpcMenuAction(int actionId, int cmd1, int cmd2, int cmd3, int menuIndex) {
		if (actionId == MenuState.NPC_OPTION_2) {
			Npc npc = actorSynchronizer.npcs[cmd1];
			if (npc != null) {
				walkTo(false, ((Actor) (npc)).pathX[0], ((Actor) (npc)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.NPC_OPTION_2);
				networkSession.outgoing.writeShortAdd(cmd1);
			}
		}
		if (actionId == MenuState.NPC_OPTION_4) {
			Npc npc2 = actorSynchronizer.npcs[cmd1];
			if (npc2 != null) {
				walkTo(false, ((Actor) (npc2)).pathX[0], ((Actor) (npc2)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.NPC_OPTION_4);
				networkSession.outgoing.writeShortLE(cmd1);
			}
		}
		if (actionId == MenuState.USE_ITEM_ON_NPC) {
			Npc npc3 = actorSynchronizer.npcs[cmd1];
			if (npc3 != null) {
				walkTo(false, ((Actor) (npc3)).pathX[0], ((Actor) (npc3)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.USE_ITEM_ON_NPC);
				networkSession.outgoing.writeShort(cmd1);
				networkSession.outgoing.writeShortLE(interfaceController.state().selectedItemId);
				networkSession.outgoing.writeShortAddLE(interfaceController.state().selectedItemWidgetId);
				networkSession.outgoing.writeShort(interfaceController.state().selectedItemSlot);
			}
		}
		if (actionId == MenuState.NPC_OPTION_3) {
			Npc npc4 = actorSynchronizer.npcs[cmd1];
			if (npc4 != null) {
				walkTo(false, ((Actor) (npc4)).pathX[0], ((Actor) (npc4)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				npcAction118Counter += cmd1;
				if (npcAction118Counter >= 143) {
					networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.ANTI_CHEAT_NPC_OPTION_3);
					networkSession.outgoing.writeInt(0);
					npcAction118Counter = 0;
				}
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.NPC_OPTION_3);
				networkSession.outgoing.writeShortAddLE(cmd1);
			}
		}
		if (actionId == MenuState.NPC_OPTION_5) {
			Npc npc5 = actorSynchronizer.npcs[cmd1];
			if (npc5 != null) {
				walkTo(false, ((Actor) (npc5)).pathX[0], ((Actor) (npc5)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.NPC_OPTION_5);
				networkSession.outgoing.writeShortLE(cmd1);
			}
		}
		if (actionId == MenuState.CAST_SPELL_ON_NPC) {
			Npc npc6 = actorSynchronizer.npcs[cmd1];
			if (npc6 != null) {
				walkTo(false, ((Actor) (npc6)).pathX[0], ((Actor) (npc6)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.CAST_SPELL_ON_NPC);
				networkSession.outgoing.writeShortAdd(interfaceController.state().selectedSpellWidgetId);
				networkSession.outgoing.writeShortLE(cmd1);
			}
		}
		if (actionId == MenuState.EXAMINE_NPC) {
			Npc npc7 = actorSynchronizer.npcs[cmd1];
			if (npc7 != null) {
				NpcDefinition npcDefinition = npc7.definition;
				if (npcDefinition.morphIds != null)
					npcDefinition = npcDefinition.transform();
				if (npcDefinition != null) {
					String description;
					if (npcDefinition.description != null)
						description = new String(npcDefinition.description);
					else
						description = "It's a " + npcDefinition.name + ".";
					addChatMessage("", description, ChatMessageType.GAME);
				}
			}
		}
		if (actionId == MenuState.NPC_OPTION_1) {
			Npc npc8 = actorSynchronizer.npcs[cmd1];
			if (npc8 != null) {
				walkTo(false, ((Actor) (npc8)).pathX[0], ((Actor) (npc8)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.NPC_OPTION_1);
				networkSession.outgoing.writeShortLE(cmd1);
			}
		}
	}

	// Game-object actions: 35, 376, 389, 467, 888, 892, 1280, 1412.
	/**
	 * Handles normalized menu actions targeting scene objects.
	 *
	 * @param actionId  the normalized menu action identifier
	 * @param cmd1      the cmd1
	 * @param cmd2      the cmd2
	 * @param cmd3      the cmd3
	 * @param menuIndex the menu index
	 */
	private void dispatchObjectMenuAction(int actionId, int cmd1, int cmd2, int cmd3, int menuIndex) {
		if (actionId == MenuState.USE_ITEM_ON_OBJECT && walkToGameObject(cmd3, cmd2, cmd1)) {
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.USE_ITEM_ON_OBJECT);
			networkSession.outgoing.writeShortLE(cmd1 >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK);
			networkSession.outgoing.writeShortLE(interfaceController.state().selectedItemWidgetId);
			networkSession.outgoing.writeShortLE(interfaceController.state().selectedItemId);
			networkSession.outgoing.writeShortLE(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShort(interfaceController.state().selectedItemSlot);
			networkSession.outgoing.writeShortAddLE(cmd2 + regionManager.baseX);
		}
		if (actionId == MenuState.CAST_SPELL_ON_OBJECT && walkToGameObject(cmd3, cmd2, cmd1)) {
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.CAST_SPELL_ON_OBJECT);
			networkSession.outgoing.writeShort(interfaceController.state().selectedSpellWidgetId);
			networkSession.outgoing.writeShortLE(cmd1 >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK);
			networkSession.outgoing.writeShortAdd(cmd2 + regionManager.baseX);
			networkSession.outgoing.writeShortLE(cmd3 + regionManager.baseY);
		}
		if (actionId == MenuState.OBJECT_OPTION_5) {
			walkToGameObject(cmd3, cmd2, cmd1);
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.OBJECT_OPTION_5);
			networkSession.outgoing.writeShortLE(cmd1 >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK);
			networkSession.outgoing.writeShortLE(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShort(cmd2 + regionManager.baseX);
		}
		if (actionId == MenuState.OBJECT_OPTION_1) {
			walkToGameObject(cmd3, cmd2, cmd1);
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.OBJECT_OPTION_1);
			networkSession.outgoing.writeShortAdd(cmd2 + regionManager.baseX);
			networkSession.outgoing.writeShortLE(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShortLE(cmd1 >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK);
		}
		if (actionId == MenuState.OBJECT_OPTION_3) {
			walkToGameObject(cmd3, cmd2, cmd1);
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.OBJECT_OPTION_3);
			networkSession.outgoing.writeShortAdd(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShortLE(cmd1 >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK);
			networkSession.outgoing.writeShortAddLE(cmd2 + regionManager.baseX);
		}
		if (actionId == MenuState.EXAMINE_OBJECT) {
			int objectId = cmd1 >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK;
			GameObjectDefinition objectDefinition = GameObjectDefinition.lookup(objectId);
			String description;
			if (objectDefinition.description != null)
				description = new String(objectDefinition.description);
			else
				description = "It's a " + objectDefinition.name + ".";
			addChatMessage("", description, ChatMessageType.GAME);
		}
		if (actionId == MenuState.OBJECT_OPTION_4) {
			walkToGameObject(cmd3, cmd2, cmd1);
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.OBJECT_OPTION_4);
			networkSession.outgoing.writeShort(cmd2 + regionManager.baseX);
			networkSession.outgoing.writeShortLE(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShort(cmd1 >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK);
		}
		if (actionId == MenuState.OBJECT_OPTION_2) {
			walkToGameObject(cmd3, cmd2, cmd1);
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.OBJECT_OPTION_2);
			networkSession.outgoing.writeShort(cmd1 >> SceneUid.ENTITY_ID_SHIFT & SceneUid.ENTITY_ID_MASK);
			networkSession.outgoing.writeShort(cmd2 + regionManager.baseX);
			networkSession.outgoing.writeShortAdd(cmd3 + regionManager.baseY);
		}
	}

	// Ground-item actions: 26, 68, 100, 199, 270, 684, 930, 1564.
	/**
	 * Handles normalized menu actions targeting ground items.
	 *
	 * @param actionId  the normalized menu action identifier
	 * @param cmd1      the cmd1
	 * @param cmd2      the cmd2
	 * @param cmd3      the cmd3
	 * @param menuIndex the menu index
	 */
	private void dispatchGroundItemMenuAction(int actionId, int cmd1, int cmd2, int cmd3, int menuIndex) {
		if (actionId == MenuState.GROUND_ITEM_OPTION_4) {
			boolean routeFound = walkTo(false, cmd2, cmd3, 0, 0, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			if (!routeFound)
				routeFound = walkTo(false, cmd2, cmd3, 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			crossX = super.clickX;
			crossY = super.clickY;
			crossType = 2;
			crossCycle = 0;
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.GROUND_ITEM_OPTION_4);
			networkSession.outgoing.writeShortAdd(cmd1);
			networkSession.outgoing.writeShortLE(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShort(cmd2 + regionManager.baseX);
		}
		if (actionId == MenuState.GROUND_ITEM_OPTION_1) {
			boolean routeFound2 = walkTo(false, cmd2, cmd3, 0, 0, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			if (!routeFound2)
				routeFound2 = walkTo(false, cmd2, cmd3, 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			crossX = super.clickX;
			crossY = super.clickY;
			crossType = 2;
			crossCycle = 0;
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.GROUND_ITEM_OPTION_1);
			networkSession.outgoing.writeShortAdd(cmd2 + regionManager.baseX);
			networkSession.outgoing.writeShort(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShortAddLE(cmd1);
		}
		if (actionId == MenuState.GROUND_ITEM_OPTION_3) {
			boolean routeFound3 = walkTo(false, cmd2, cmd3, 0, 0, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			if (!routeFound3)
				routeFound3 = walkTo(false, cmd2, cmd3, 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			crossX = super.clickX;
			crossY = super.clickY;
			crossType = 2;
			crossCycle = 0;
			if ((cmd1 & 3) == 0)
				groundItemAction684Counter++;
			if (groundItemAction684Counter >= 84) {
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.ANTI_CHEAT_GROUND_ITEM_OPTION_3);
				networkSession.outgoing.writeMedium(0xabc842);
				groundItemAction684Counter = 0;
			}
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.GROUND_ITEM_OPTION_3);
			networkSession.outgoing.writeShortAddLE(cmd1);
			networkSession.outgoing.writeShortAddLE(cmd2 + regionManager.baseX);
			networkSession.outgoing.writeShortAdd(cmd3 + regionManager.baseY);
		}
		if (actionId == MenuState.GROUND_ITEM_OPTION_5) {
			boolean routeFound4 = walkTo(false, cmd2, cmd3, 0, 0, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			if (!routeFound4)
				routeFound4 = walkTo(false, cmd2, cmd3, 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			crossX = super.clickX;
			crossY = super.clickY;
			crossType = 2;
			crossCycle = 0;
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.GROUND_ITEM_OPTION_5);
			networkSession.outgoing.writeShortLE(cmd1);
			networkSession.outgoing.writeShortAdd(cmd2 + regionManager.baseX);
			networkSession.outgoing.writeShort(cmd3 + regionManager.baseY);
		}
		if (actionId == MenuState.USE_ITEM_ON_GROUND_ITEM) {
			boolean routeFound5 = walkTo(false, cmd2, cmd3, 0, 0, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			if (!routeFound5)
				routeFound5 = walkTo(false, cmd2, cmd3, 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			crossX = super.clickX;
			crossY = super.clickY;
			crossType = 2;
			crossCycle = 0;
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.USE_ITEM_ON_GROUND_ITEM);
			networkSession.outgoing.writeShortAddLE(interfaceController.state().selectedItemSlot);
			networkSession.outgoing.writeShortAdd(interfaceController.state().selectedItemId);
			networkSession.outgoing.writeShortAddLE(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShortAddLE(cmd2 + regionManager.baseX);
			networkSession.outgoing.writeShortLE(interfaceController.state().selectedItemWidgetId);
			networkSession.outgoing.writeShortLE(cmd1);
		}
		if (actionId == MenuState.GROUND_ITEM_OPTION_2) {
			boolean routeFound6 = walkTo(false, cmd2, cmd3, 0, 0, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			if (!routeFound6)
				routeFound6 = walkTo(false, cmd2, cmd3, 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			crossX = super.clickX;
			crossY = super.clickY;
			crossType = 2;
			crossCycle = 0;
			groundItemAction26Counter++;
			if (groundItemAction26Counter >= 120) {
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.ANTI_CHEAT_GROUND_ITEM_OPTION_2);
				networkSession.outgoing.writeInt(0);
				groundItemAction26Counter = 0;
			}
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.GROUND_ITEM_OPTION_2);
			networkSession.outgoing.writeShort(cmd2 + regionManager.baseX);
			networkSession.outgoing.writeShortAdd(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShortAddLE(cmd1);
		}
		if (actionId == MenuState.CAST_SPELL_ON_GROUND_ITEM) {
			boolean routeFound7 = walkTo(false, cmd2, cmd3, 0, 0, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			if (!routeFound7)
				routeFound7 = walkTo(false, cmd2, cmd3, 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			crossX = super.clickX;
			crossY = super.clickY;
			crossType = 2;
			crossCycle = 0;
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.CAST_SPELL_ON_GROUND_ITEM);
			networkSession.outgoing.writeShortLE(cmd1);
			networkSession.outgoing.writeShort(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShortLE(interfaceController.state().selectedSpellWidgetId);
			networkSession.outgoing.writeShortAddLE(cmd2 + regionManager.baseX);
		}
		if (actionId == MenuState.EXAMINE_GROUND_ITEM) {
			ItemDefinition itemDefinition = ItemDefinition.lookup(cmd1);
			String description;
			if (itemDefinition.description != null)
				description = new String(itemDefinition.description);
			else
				description = "It's a " + itemDefinition.name + ".";
			addChatMessage("", description, ChatMessageType.GAME);
		}
	}

	/**
	 * Records the inventory slot/widget affected by an inventory menu action.
	 *
	 * @param widgetId the widget id
	 * @param slot     the slot
	 */
	private void markInventoryInteraction(int widgetId, int slot) {
		inventoryClickCycle = 0;
		interfaceController.state().pressedInventoryWidgetId = widgetId;
		interfaceController.state().pressedInventorySlot = slot;
		interfaceController.state().pressedInventoryArea = 2;
		if (Widget.get(widgetId).parentId == interfaceController.state().openInterfaceId)
			interfaceController.state().pressedInventoryArea = 1;
		if (Widget.get(widgetId).parentId == interfaceController.state().chatboxInterfaceId)
			interfaceController.state().pressedInventoryArea = 3;
	}

	// Inventory/item actions retain their original packet/action IDs.
	/**
	 * Handles normalized menu actions targeting inventory items and slots.
	 *
	 * @param actionId  the normalized menu action identifier
	 * @param cmd1      the cmd1
	 * @param cmd2      the cmd2
	 * @param cmd3      the cmd3
	 * @param menuIndex the menu index
	 * @return true when the requested condition/action succeeds; otherwise false
	 */
	private boolean dispatchInventoryMenuAction(int actionId, int cmd1, int cmd2, int cmd3, int menuIndex) {
		if (actionId == MenuState.INVENTORY_ITEM_OPTION_4) {
			inventoryAction227Counter++;
			if (inventoryAction227Counter >= 62) {
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.ANTI_CHEAT_INVENTORY_ITEM_OPTION_4);
				networkSession.outgoing.writeByte(206);
				inventoryAction227Counter = 0;
			}
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.INVENTORY_ITEM_OPTION_4);
			networkSession.outgoing.writeShortLE(cmd2);
			networkSession.outgoing.writeShortAdd(cmd1);
			networkSession.outgoing.writeShort(cmd3);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == MenuState.INVENTORY_ITEM_OPTION_1) {
			inventoryAction961Counter += cmd1;
			if (inventoryAction961Counter >= 115) {
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.ANTI_CHEAT_INVENTORY_ITEM_OPTION_1);
				networkSession.outgoing.writeByte(125);
				inventoryAction961Counter = 0;
			}
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.INVENTORY_ITEM_OPTION_1);
			networkSession.outgoing.writeShortAdd(cmd3);
			networkSession.outgoing.writeShortLE(cmd2);
			networkSession.outgoing.writeShortLE(cmd1);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == MenuState.WIDGET_ITEM_OPTION_1) {
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.WIDGET_ITEM_OPTION_1);
			networkSession.outgoing.writeShortAdd(cmd1);
			networkSession.outgoing.writeShort(cmd3);
			networkSession.outgoing.writeShort(cmd2);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == MenuState.INVENTORY_ITEM_OPTION_2) {
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.INVENTORY_ITEM_OPTION_2);
			networkSession.outgoing.writeShortLE(cmd3);
			networkSession.outgoing.writeShortLE(cmd1);
			networkSession.outgoing.writeShortAdd(cmd2);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == MenuState.USE_ITEM_ON_INVENTORY_ITEM) {
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.USE_ITEM_ON_INVENTORY_ITEM);
			networkSession.outgoing.writeShort(cmd1);
			networkSession.outgoing.writeShortLE(interfaceController.state().selectedItemSlot);
			networkSession.outgoing.writeShortLE(interfaceController.state().selectedItemId);
			networkSession.outgoing.writeShortAddLE(interfaceController.state().selectedItemWidgetId);
			networkSession.outgoing.writeShortAdd(cmd2);
			networkSession.outgoing.writeShortAdd(cmd3);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == MenuState.CAST_SPELL_ON_INVENTORY_ITEM) {
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.CAST_SPELL_ON_INVENTORY_ITEM);
			networkSession.outgoing.writeShort(interfaceController.state().selectedSpellWidgetId);
			networkSession.outgoing.writeShortAdd(cmd3);
			networkSession.outgoing.writeShortAdd(cmd2);
			networkSession.outgoing.writeShortAdd(cmd1);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == MenuState.WIDGET_ITEM_OPTION_2) {
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.WIDGET_ITEM_OPTION_2);
			networkSession.outgoing.writeShortAdd(cmd2);
			networkSession.outgoing.writeShortLE(cmd1);
			networkSession.outgoing.writeShortLE(cmd3);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == MenuState.INVENTORY_ITEM_OPTION_5) {
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.INVENTORY_ITEM_OPTION_5);
			networkSession.outgoing.writeShortLE(cmd2);
			networkSession.outgoing.writeShortAddLE(cmd1);
			networkSession.outgoing.writeShortAddLE(cmd3);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == MenuState.WIDGET_ITEM_OPTION_5) {
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.WIDGET_ITEM_OPTION_5);
			networkSession.outgoing.writeShortAddLE(cmd2);
			networkSession.outgoing.writeShortAddLE(cmd1);
			networkSession.outgoing.writeShortLE(cmd3);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == MenuState.INVENTORY_ITEM_OPTION_3) {
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.INVENTORY_ITEM_OPTION_3);
			networkSession.outgoing.writeShortAddLE(cmd2);
			networkSession.outgoing.writeShortAddLE(cmd1);
			networkSession.outgoing.writeShortLE(cmd3);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == MenuState.EXAMINE_INVENTORY_ITEM) {
			ItemDefinition itemDefinition = ItemDefinition.lookup(cmd1);
			Widget widget = Widget.get(cmd3);
			String description;
			if (widget != null && widget.itemAmounts[cmd2] >= 0x186a0)
				description = widget.itemAmounts[cmd2] + " x " + itemDefinition.name;
			else if (itemDefinition.description != null)
				description = new String(itemDefinition.description);
			else
				description = "It's a " + itemDefinition.name + ".";
			addChatMessage("", description, ChatMessageType.GAME);
		}
		if (actionId == MenuState.WIDGET_ITEM_OPTION_3) {
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.WIDGET_ITEM_OPTION_3);
			networkSession.outgoing.writeShortLE(cmd1);
			networkSession.outgoing.writeShortAddLE(cmd2);
			networkSession.outgoing.writeShort(cmd3);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == MenuState.WIDGET_ITEM_OPTION_4) {
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.WIDGET_ITEM_OPTION_4);
			networkSession.outgoing.writeShortAddLE(cmd3);
			networkSession.outgoing.writeShortLE(cmd2);
			networkSession.outgoing.writeShort(cmd1);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == MenuState.SELECT_ITEM) {
			interfaceController.state().itemSelected = 1;
			interfaceController.state().selectedItemSlot = cmd2;
			interfaceController.state().selectedItemWidgetId = cmd3;
			interfaceController.state().selectedItemId = cmd1;
			interfaceController.state().selectedItemName = String.valueOf(ItemDefinition.lookup(cmd1).name);
			interfaceController.state().spellSelected = 0;
			sidebarRedraw = true;
			return true;
		}
		return false;
	}

	// Widget/button actions, including spell selection and CS1 varp buttons.
	/**
	 * Handles normalized menu actions targeting widgets and widget-config state.
	 *
	 * @param actionId  the normalized menu action identifier
	 * @param cmd1      the cmd1
	 * @param cmd2      the cmd2
	 * @param cmd3      the cmd3
	 * @param menuIndex the menu index
	 * @return true when the requested condition/action succeeds; otherwise false
	 */
	private boolean dispatchWidgetMenuAction(int actionId, int cmd1, int cmd2, int cmd3, int menuIndex) {
		if (actionId == MenuState.WIDGET_TOGGLE_VARP) {
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.WIDGET_CLICK);
			networkSession.outgoing.writeShort(cmd3);
			Widget widget = Widget.get(cmd3);
			if (widget.cs1Instructions != null && widget.cs1Instructions[0][0] == 5) {
				int varpId = widget.cs1Instructions[0][1];
				varpState.toggleBinary(varpId);
				applyVarp(varpId);
				sidebarRedraw = true;
			}
		}
		if (actionId == MenuState.CLOSE_INTERFACE)
			closeInterfaces();
		if (actionId == MenuState.SELECT_SPELL) {
			Widget spellWidget = Widget.get(cmd3);
			interfaceController.state().spellSelected = 1;
			interfaceController.state().selectedSpellWidgetId = cmd3;
			interfaceController.state().selectedSpellTargetMask = spellWidget.spellUsableOn;
			interfaceController.state().itemSelected = 0;
			sidebarRedraw = true;
			String actionVerb = spellWidget.selectedActionName;
			if (actionVerb.indexOf(" ") != -1)
				actionVerb = actionVerb.substring(0, actionVerb.indexOf(" "));
			String actionTarget = spellWidget.selectedActionName;
			if (actionTarget.indexOf(" ") != -1)
				actionTarget = actionTarget.substring(actionTarget.indexOf(" ") + 1);
			interfaceController.state().selectedSpellAction = actionVerb + " " + spellWidget.spellName + " " + actionTarget;
			if (interfaceController.state().selectedSpellTargetMask == 16) {
				sidebarRedraw = true;
				tabAreaRedraw = true;
			}
			return true;
		}
		if (actionId == MenuState.WIDGET_BUTTON) {
			Widget actionWidget = Widget.get(cmd3);
			boolean sendWidgetClick = true;
			if (actionWidget.contentType > WidgetContentType.NONE)
				sendWidgetClick = handleWidgetContentAction(actionWidget);
			if (sendWidgetClick) {
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.WIDGET_CLICK);
				networkSession.outgoing.writeShort(cmd3);
			}
		}
		if (actionId == MenuState.WIDGET_CONTINUE && !interfaceController.actionPending()) {
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.WIDGET_CONTINUE);
			networkSession.outgoing.writeShort(cmd3);
			interfaceController.setActionPending(true);
		}
		if (actionId == MenuState.WIDGET_SET_VARP) {
			networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.WIDGET_CLICK);
			networkSession.outgoing.writeShort(cmd3);
			Widget configWidget = Widget.get(cmd3);
			if (configWidget.cs1Instructions != null && configWidget.cs1Instructions[0][0] == 5) {
				int varpId2 = configWidget.cs1Instructions[0][1];
				if (varpState.get(varpId2) != configWidget.cs1ComparisonValues[0]) {
					varpState.set(varpId2, configWidget.cs1ComparisonValues[0]);
					applyVarp(varpId2);
					sidebarRedraw = true;
				}
			}
		}
		if (actionId == MenuState.CLOSE_DIALOGUE) {
			unloadInterface(interfaceController.state().dialogueInterfaceId);
			chatboxRedraw = true;
		}
		return false;
	}

	// Friend/ignore/message/report actions plus name-based player targeting.
	/**
	 * Handles normalized menu actions for friends, ignores, private messages, and
	 * report abuse.
	 *
	 * @param actionId  the normalized menu action identifier
	 * @param cmd1      the cmd1
	 * @param cmd2      the cmd2
	 * @param cmd3      the cmd3
	 * @param menuIndex the menu index
	 */
	private void dispatchSocialMenuAction(int actionId, int cmd1, int cmd2, int cmd3, int menuIndex) {
		if (actionId == MenuState.ADD_FRIEND || actionId == MenuState.ADD_IGNORE || actionId == MenuState.REMOVE_FRIEND || actionId == MenuState.REMOVE_IGNORE) {
			String actionText = menuController.state().actionNames[menuIndex];
			int markerIndex = actionText.indexOf("@whi@");
			if (markerIndex != -1) {
				long encodedName = Base37.encode(actionText.substring(markerIndex + 5).trim());
				if (actionId == MenuState.ADD_FRIEND)
					addFriend(encodedName);
				if (actionId == MenuState.ADD_IGNORE)
					addIgnore(encodedName);
				if (actionId == MenuState.REMOVE_FRIEND)
					removeFriend(encodedName);
				if (actionId == MenuState.REMOVE_IGNORE)
					removeIgnore(encodedName);
			}
		}
		if (actionId == MenuState.ACCEPT_TRADE || actionId == MenuState.ACCEPT_CHALLENGE) {
			String actionText2 = menuController.state().actionNames[menuIndex];
			int markerIndex2 = actionText2.indexOf("@whi@");
			if (markerIndex2 != -1) {
				actionText2 = actionText2.substring(markerIndex2 + 5).trim();
				String encodedName2 = TextFormatter.formatDisplayName(Base37.decode(Base37.encode(actionText2)));
				boolean playerFound = false;
				for (int activePlayerIndex = 0; activePlayerIndex < actorSynchronizer.playerCount; activePlayerIndex++) {
					Player player = actorSynchronizer.players[actorSynchronizer.playerIndices[activePlayerIndex]];
					if (player == null || player.name == null || !player.name.equalsIgnoreCase(encodedName2))
						continue;
					walkTo(false, ((Actor) (player)).pathX[0], ((Actor) (player)).pathY[0], 1, 1,
							MovementPacketEncoder.INTERACTION, 0, 0, 0);
					if (actionId == MenuState.ACCEPT_TRADE) {
						networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.PLAYER_OPTION_4);
						networkSession.outgoing.writeShortLE(actorSynchronizer.playerIndices[activePlayerIndex]);
					}
					if (actionId == MenuState.ACCEPT_CHALLENGE) {
						networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.PLAYER_OPTION_1);
						networkSession.outgoing.writeShortAddLE(actorSynchronizer.playerIndices[activePlayerIndex]);
					}
					playerFound = true;
					break;
				}

				if (!playerFound)
					addChatMessage("", "Unable to find " + encodedName2, ChatMessageType.GAME);
			}
		}
		if (actionId == MenuState.REPORT_ABUSE) {
			String actionText3 = menuController.state().actionNames[menuIndex];
			int markerIndex3 = actionText3.indexOf("@whi@");
			if (markerIndex3 != -1)
				if (interfaceController.state().openInterfaceId == -1) {
					closeInterfaces();
					interfaceController.setReportAbuseName(actionText3.substring(markerIndex3 + 5).trim());
					interfaceController.setReportAbuseMutePlayer(false);
					interfaceController.state().reportAbuseInterfaceId = interfaceController.state().openInterfaceId = Widget.reportAbuseInterfaceId;
				} else {
					addChatMessage("", "Please close the interface you have open before using 'report abuse'", ChatMessageType.GAME);
				}
		}
		if (actionId == MenuState.MESSAGE_FRIEND) {
			String actionText4 = menuController.state().actionNames[menuIndex];
			int markerIndex4 = actionText4.indexOf("@whi@");
			if (markerIndex4 != -1) {
				long encodedName3 = Base37.encode(actionText4.substring(markerIndex4 + 5).trim());
				int friendIndex = socialManager.findFriendIndex(encodedName3);

				if (friendIndex != -1 && socialManager.friendWorlds[friendIndex] > 0) {
					chatboxRedraw = true;
					chatController.openPrivateMessagePrompt(socialManager.friendEncodedNames[friendIndex],
							socialManager.friendNames[friendIndex]);
				}
			}
		}
	}

	/**
	 * Handles the remaining menu action that does not belong to a target-specific
	 * group.
	 *
	 * @param actionId  the normalized menu action identifier
	 * @param cmd1      the cmd1
	 * @param cmd2      the cmd2
	 * @param cmd3      the cmd3
	 * @param menuIndex the menu index
	 */
	private void dispatchMiscMenuAction(int actionId, int cmd1, int cmd2, int cmd3, int menuIndex) {
		if (actionId == MenuState.WALK_HERE)
			if (!menuController.state().open)
				worldState.scene.setClick(super.clickX - layout.viewportX(), super.clickY - layout.viewportY());
			else
				worldState.scene.setClick(cmd2 - layout.viewportX(), cmd3 - layout.viewportY());
	}

	/**
	 * Projects and draws actor head icons, hints, overhead text, health bars, and
	 * hitmarks.
	 */
	public void drawActorOverlays() {
		overheadTextCount = 0;
		for (int actorListIndex = -1; actorListIndex < actorSynchronizer.playerCount
				+ actorSynchronizer.npcCount; actorListIndex++) {
			Object obj;
			if (actorListIndex == -1)
				obj = localPlayer;
			else if (actorListIndex < actorSynchronizer.playerCount)
				obj = actorSynchronizer.players[actorSynchronizer.playerIndices[actorListIndex]];
			else
				obj = actorSynchronizer.npcs[actorSynchronizer.npcIndices[actorListIndex
						- actorSynchronizer.playerCount]];
			if (obj == null || !((Actor) (obj)).isVisible())
				continue;
			if (obj instanceof Npc) {
				NpcDefinition npcDefinition = ((Npc) obj).definition;
				if (npcDefinition.morphIds != null)
					npcDefinition = npcDefinition.transform();
				if (npcDefinition == null)
					continue;
			}
			if (actorListIndex < actorSynchronizer.playerCount) {
				int iconY = 30;
				Player player = (Player) obj;
				if (player.skullIcon != -1 || player.prayerIcon != -1) {
					projectActorToScreen((Actor) obj, ((Actor) obj).height + 15);
					if (projectedX > -1) {
						if (player.skullIcon != -1) {
							skullIconSprites[player.skullIcon].drawImage(projectedX - 12, projectedY - iconY);
							iconY += 25;
						}
						if (player.prayerIcon != -1) {
							prayerIconSprites[player.prayerIcon].drawImage(projectedX - 12, projectedY - iconY);
							iconY += 25;
						}
					}
				}
				if (actorListIndex >= 0 && hintIconType == 10
						&& hintPlayerIndex == actorSynchronizer.playerIndices[actorListIndex]) {
					projectActorToScreen((Actor) obj, ((Actor) obj).height + 15);
					if (projectedX > -1)
						hintIconSprites[1].drawImage(projectedX - 12, projectedY - iconY);
				}
			} else {
				NpcDefinition npcDefinition2 = ((Npc) obj).definition;
				if (npcDefinition2.prayerIcon >= 0 && npcDefinition2.prayerIcon < prayerIconSprites.length) {
					projectActorToScreen((Actor) obj, ((Actor) obj).height + 15);
					if (projectedX > -1)
						prayerIconSprites[npcDefinition2.prayerIcon].drawImage(projectedX - 12, projectedY - 30);
				}
				if (hintIconType == 1
						&& hintNpcIndex == actorSynchronizer.npcIndices[actorListIndex - actorSynchronizer.playerCount]
						&& gameCycle % 20 < 10) {
					projectActorToScreen((Actor) obj, ((Actor) obj).height + 15);
					if (projectedX > -1)
						hintIconSprites[0].drawImage(projectedX - 12, projectedY - 28);
				}
			}
			if (((Actor) (obj)).overheadText != null
					&& (actorListIndex >= actorSynchronizer.playerCount || chatController.publicMode() == ChatMode.ON || chatController.publicMode() == ChatMode.HIDE
							|| chatController.publicMode() == ChatMode.FRIENDS && isFriendOrSelf(((Player) obj).name))) {
				projectActorToScreen((Actor) obj, ((Actor) obj).height);
				if (projectedX > -1 && overheadTextCount < overheadTextLimit) {
					overheadTextHalfWidths[overheadTextCount] = boldFont.getTextWidth(((Actor) (obj)).overheadText) / 2;
					overheadTextHeights[overheadTextCount] = boldFont.lineHeight;
					overheadTextXs[overheadTextCount] = projectedX;
					overheadTextYs[overheadTextCount] = projectedY;
					overheadTextColorCodes[overheadTextCount] = ((Actor) (obj)).overheadTextColor;
					overheadTextEffects[overheadTextCount] = ((Actor) (obj)).overheadTextEffect;
					overheadTextCycles[overheadTextCount] = ((Actor) (obj)).overheadTextCyclesRemaining;
					overheadTexts[overheadTextCount++] = ((Actor) (obj)).overheadText;
					if (chatEffects == 0 && ((Actor) (obj)).overheadTextEffect >= 1
							&& ((Actor) (obj)).overheadTextEffect <= 3) {
						overheadTextHeights[overheadTextCount] += 10;
						overheadTextYs[overheadTextCount] += 5;
					}
					if (chatEffects == 0 && ((Actor) (obj)).overheadTextEffect == 4)
						overheadTextHalfWidths[overheadTextCount] = 60;
					if (chatEffects == 0 && ((Actor) (obj)).overheadTextEffect == 5)
						overheadTextHeights[overheadTextCount] += 5;
				}
			}
			if (((Actor) (obj)).healthBarCycle > gameCycle) {
				projectActorToScreen((Actor) obj, ((Actor) obj).height + 15);
				if (projectedX > -1) {
					int healthBarWidth = (((Actor) (obj)).currentHealth * 30) / ((Actor) (obj)).maxHealth;
					if (healthBarWidth > 30)
						healthBarWidth = 30;
					Rasterizer.drawFilledRectangle(projectedX - 15, projectedY - 3, healthBarWidth, 5, 65280);
					Rasterizer.drawFilledRectangle((projectedX - 15) + healthBarWidth, projectedY - 3,
							30 - healthBarWidth, 5, 0xff0000);
				}
			}
			for (int hitIndex = 0; hitIndex < 4; hitIndex++)
				if (((Actor) (obj)).hitCycles[hitIndex] > gameCycle) {
					projectActorToScreen((Actor) obj, ((Actor) obj).height / 2);
					if (projectedX > -1) {
						if (hitIndex == 1)
							projectedY -= 20;
						if (hitIndex == 2) {
							projectedX -= 15;
							projectedY -= 10;
						}
						if (hitIndex == 3) {
							projectedX += 15;
							projectedY -= 10;
						}
						hitmarkSprites[((Actor) (obj)).hitTypes[hitIndex]].drawImage(projectedX - 12, projectedY - 12);
						smallFont.drawCenteredText(String.valueOf(((Actor) (obj)).hitDamages[hitIndex]), projectedX,
								projectedY + 4, 0);
						smallFont.drawCenteredText(String.valueOf(((Actor) (obj)).hitDamages[hitIndex]), projectedX - 1,
								projectedY + 3, 0xffffff);
					}
				}

		}

		for (int overheadIndex = 0; overheadIndex < overheadTextCount; overheadIndex++) {
			int textX = overheadTextXs[overheadIndex];
			int textY = overheadTextYs[overheadIndex];
			int textHalfWidth = overheadTextHalfWidths[overheadIndex];
			int textHeight = overheadTextHeights[overheadIndex];
			boolean adjustingOverlap = true;
			while (adjustingOverlap) {
				adjustingOverlap = false;
				for (int previousTextIndex = 0; previousTextIndex < overheadIndex; previousTextIndex++)
					if (textY + 2 > overheadTextYs[previousTextIndex] - overheadTextHeights[previousTextIndex]
							&& textY - textHeight < overheadTextYs[previousTextIndex] + 2
							&& textX - textHalfWidth < overheadTextXs[previousTextIndex]
									+ overheadTextHalfWidths[previousTextIndex]
							&& textX + textHalfWidth > overheadTextXs[previousTextIndex]
									- overheadTextHalfWidths[previousTextIndex]
							&& overheadTextYs[previousTextIndex] - overheadTextHeights[previousTextIndex] < textY) {
						textY = overheadTextYs[previousTextIndex] - overheadTextHeights[previousTextIndex];
						adjustingOverlap = true;
					}

			}
			projectedX = overheadTextXs[overheadIndex];
			projectedY = overheadTextYs[overheadIndex] = textY;
			String overheadText = overheadTexts[overheadIndex];
			if (chatEffects == 0) {
				int textColor = 0xffff00;
				if (overheadTextColorCodes[overheadIndex] < 6)
					textColor = overheadTextColors[overheadTextColorCodes[overheadIndex]];
				if (overheadTextColorCodes[overheadIndex] == 6)
					textColor = sceneEntityRenderer.getRenderCycle() % 20 >= 10 ? 0xffff00 : 0xff0000;
				if (overheadTextColorCodes[overheadIndex] == 7)
					textColor = sceneEntityRenderer.getRenderCycle() % 20 >= 10 ? 65535 : 255;
				if (overheadTextColorCodes[overheadIndex] == 8)
					textColor = sceneEntityRenderer.getRenderCycle() % 20 >= 10 ? 0x80ff80 : 45056;
				if (overheadTextColorCodes[overheadIndex] == 9) {
					int colorAge = 150 - overheadTextCycles[overheadIndex];
					if (colorAge < 50)
						textColor = 0xff0000 + 1280 * colorAge;
					else if (colorAge < 100)
						textColor = 0xffff00 - 0x50000 * (colorAge - 50);
					else if (colorAge < 150)
						textColor = 65280 + 5 * (colorAge - 100);
				}
				if (overheadTextColorCodes[overheadIndex] == 10) {
					int colorAge2 = 150 - overheadTextCycles[overheadIndex];
					if (colorAge2 < 50)
						textColor = 0xff0000 + 5 * colorAge2;
					else if (colorAge2 < 100)
						textColor = 0xff00ff - 0x50000 * (colorAge2 - 50);
					else if (colorAge2 < 150)
						textColor = (255 + 0x50000 * (colorAge2 - 100)) - 5 * (colorAge2 - 100);
				}
				if (overheadTextColorCodes[overheadIndex] == 11) {
					int colorAge3 = 150 - overheadTextCycles[overheadIndex];
					if (colorAge3 < 50)
						textColor = 0xffffff - 0x50005 * colorAge3;
					else if (colorAge3 < 100)
						textColor = 65280 + 0x50005 * (colorAge3 - 50);
					else if (colorAge3 < 150)
						textColor = 0xffffff - 0x50000 * (colorAge3 - 100);
				}
				if (overheadTextEffects[overheadIndex] == 0) {
					boldFont.drawCenteredText(overheadText, projectedX, projectedY + 1, 0);
					boldFont.drawCenteredText(overheadText, projectedX, projectedY, textColor);
				}
				if (overheadTextEffects[overheadIndex] == 1) {
					boldFont.drawWaveText(overheadText, projectedX, projectedY + 1, 0,
							sceneEntityRenderer.getRenderCycle());
					boldFont.drawWaveText(overheadText, projectedX, projectedY, textColor,
							sceneEntityRenderer.getRenderCycle());
				}
				if (overheadTextEffects[overheadIndex] == 2) {
					boldFont.drawWave2Text(overheadText, projectedX, projectedY + 1, 0,
							sceneEntityRenderer.getRenderCycle());
					boldFont.drawWave2Text(overheadText, projectedX, projectedY, textColor,
							sceneEntityRenderer.getRenderCycle());
				}
				if (overheadTextEffects[overheadIndex] == 3) {
					boldFont.drawWaveAmplitudeText(overheadText, projectedX, projectedY + 1, 0,
							150 - overheadTextCycles[overheadIndex], sceneEntityRenderer.getRenderCycle());
					boldFont.drawWaveAmplitudeText(overheadText, projectedX, projectedY, textColor,
							150 - overheadTextCycles[overheadIndex], sceneEntityRenderer.getRenderCycle());
				}
				if (overheadTextEffects[overheadIndex] == 4) {
					int textWidth = boldFont.getTextWidth(overheadText);
					int scrollOffset = ((150 - overheadTextCycles[overheadIndex]) * (textWidth + 100)) / 150;
					Rasterizer.setCoordinates(projectedX - 50, 0, projectedX + 50, layout.viewportHeight());
					boldFont.drawText(overheadText, (projectedX + 50) - scrollOffset, projectedY + 1, 0);
					boldFont.drawText(overheadText, (projectedX + 50) - scrollOffset, projectedY, textColor);
					Rasterizer.resetCoordinates();
				}
				if (overheadTextEffects[overheadIndex] == 5) {
					int effectAge = 150 - overheadTextCycles[overheadIndex];
					int verticalOffset = 0;
					if (effectAge < 25)
						verticalOffset = effectAge - 25;
					else if (effectAge > 125)
						verticalOffset = effectAge - 125;
					Rasterizer.setCoordinates(0, projectedY - boldFont.lineHeight - 1, layout.viewportWidth(), projectedY + 5);
					boldFont.drawCenteredText(overheadText, projectedX, projectedY + 1 + verticalOffset, 0);
					boldFont.drawCenteredText(overheadText, projectedX, projectedY + verticalOffset, textColor);
					Rasterizer.resetCoordinates();
				}
			} else {
				boldFont.drawCenteredText(overheadText, projectedX, projectedY + 1, 0);
				boldFont.drawCenteredText(overheadText, projectedX, projectedY, 0xffff00);
			}
		}

	}

	/** Rebuilds projection tables and scene visibility for the current viewport size. */
	private void rebuildViewportProjection() {
		Rasterizer3D.setBounds(layout.viewportWidth(), layout.viewportHeight());
		viewportScanlineOffsets = Rasterizer3D.scanlineOffsets;
		int visibilityPitchHeights[] = new int[9];
		for (int pitchIndex = 0; pitchIndex < 9; pitchIndex++) {
			int pitchAngle = 128 + pitchIndex * 32 + 15;
			int projectionDistance = 600 + pitchAngle * 3;
			int pitchSine = Rasterizer3D.SINE[pitchAngle];
			visibilityPitchHeights[pitchIndex] = projectionDistance * pitchSine >> 16;
		}
		Scene.buildVisibilityMaps(500, 800, layout.viewportWidth(), layout.viewportHeight(), visibilityPitchHeights);
	}

	/** Recreates only size-dependent renderer state when the window is resized. */
	@Override
	protected void onResize(int width, int height) {
		layout.resize(width, height);
		if (viewportBuffer != null)
			viewportBuffer = new GraphicsBuffer(getGameComponent(), layout.viewportWidth(), layout.viewportHeight());
		rebuildViewportProjection();
		if (viewportBuffer != null) {
			viewportBuffer.bindRaster();
			Rasterizer3D.scanlineOffsets = viewportScanlineOffsets;
		}
		gameScreenRedraw = true;
		sidebarRedraw = true;
		chatboxRedraw = true;
		tabAreaRedraw = true;
		chatModesRedraw = true;
	}

	/**
	 * Allocates the fixed sidebar, minimap, viewport, chatbox, and frame-decoration
	 * buffers.
	 */
	public void createGameScreenBuffers() {
		if (chatboxBuffer != null) {
			return;
		} else {
			disposeTitleScreen();
			super.gameBuffer = null;
			titleTopBuffer = null;
			titleBottomBuffer = null;
			loginBoxBuffer = null;
			titleLeftFlameBuffer = null;
			titleRightFlameBuffer = null;
			titleLeftBottomBuffer = null;
			titleRightBottomBuffer = null;
			titleLeftCenterBuffer = null;
			titleRightCenterBuffer = null;
			chatboxBuffer = new GraphicsBuffer(getGameComponent(), ClientLayout.CHATBOX_WIDTH, ClientLayout.CHATBOX_HEIGHT);
			minimapBuffer = new GraphicsBuffer(getGameComponent(), 172, 156);
			Rasterizer.resetPixels();
			minimapBackground.draw(0, 0);
			sidebarBuffer = new GraphicsBuffer(getGameComponent(), ClientLayout.SIDEBAR_WIDTH, ClientLayout.SIDEBAR_HEIGHT);
			viewportBuffer = new GraphicsBuffer(getGameComponent(), layout.viewportWidth(), layout.viewportHeight());
			Rasterizer.resetPixels();
			chatModesBuffer = new GraphicsBuffer(getGameComponent(), 496, 50);
			bottomTabsBuffer = new GraphicsBuffer(getGameComponent(), 269, 37);
			topTabsBuffer = new GraphicsBuffer(getGameComponent(), 249, 45);
			gameScreenRedraw = true;
			viewportBuffer.bindRaster();
			Rasterizer3D.scanlineOffsets = viewportScanlineOffsets;
			return;
		}
	}

	/**
	 * Draws the startup failure messages for loading, host, or duplicate-client
	 * errors.
	 */
	public void drawStartupErrorScreen() {
		Graphics g = getGameComponent().getGraphics();
		g.setColor(Color.black);
		g.fillRect(0, 0, ClientLayout.FIXED_WIDTH, ClientLayout.FIXED_HEIGHT);
		setTargetFps(1);
		if (loadingError) {
			titleFlameAnimator.requestStop();
			g.setFont(new Font("Helvetica", 1, 16));
			g.setColor(Color.yellow);
			int lineY = 35;
			g.drawString("Sorry, an error has occured whilst loading RuneScape", 30, lineY);
			lineY += 50;
			g.setColor(Color.white);
			g.drawString("To fix this try the following (in order):", 30, lineY);
			lineY += 50;
			g.setColor(Color.white);
			g.setFont(new Font("Helvetica", 1, 12));
			g.drawString("1: Try closing ALL open web-browser windows, and reloading", 30, lineY);
			lineY += 30;
			g.drawString("2: Try clearing your web-browsers cache from tools->internet options", 30, lineY);
			lineY += 30;
			g.drawString("3: Try using a different game-world", 30, lineY);
			lineY += 30;
			g.drawString("4: Try rebooting your computer", 30, lineY);
			lineY += 30;
			g.drawString("5: Try selecting a different version of Java from the play-game menu", 30, lineY);
		}
		if (invalidHostError) {
			titleFlameAnimator.requestStop();
			g.setFont(new Font("Helvetica", 1, 20));
			g.setColor(Color.white);
			g.drawString("Error - unable to load game!", 50, 50);
			g.drawString("To play RuneScape make sure you play from", 50, 100);
			g.drawString("http://www.runescape.com", 50, 150);
		}
		if (duplicateClientError) {
			titleFlameAnimator.requestStop();
			g.setColor(Color.yellow);
			int lineY2 = 35;
			g.drawString("Error a copy of RuneScape already appears to be loaded", 30, lineY2);
			lineY2 += 50;
			g.setColor(Color.white);
			g.drawString("To fix this try the following (in order):", 30, lineY2);
			lineY2 += 50;
			g.setColor(Color.white);
			g.setFont(new Font("Helvetica", 1, 12));
			g.drawString("1: Try closing ALL open web-browser windows, and reloading", 30, lineY2);
			lineY2 += 30;
			g.drawString("2: Try rebooting your computer, and reloading", 30, lineY2);
			lineY2 += 30;
		}
	}

	/**
	 * Closes the session and resets world, cache, login, and music state for the
	 * login screen.
	 */
	public void logout() {
		networkSession.closeConnection();
		loggedIn = false;
		loginScreen.resetForLogout();
		clearCaches();
		worldState.scene.clear();
		for (int plane = 0; plane < 4; plane++)
			worldState.collisionMaps[plane].reset();

		System.gc();
		musicController.resetOnLogout();
	}

	/**
	 * Draws an in-game loading message over the current viewport or full-screen
	 * buffer.
	 *
	 * @param secondaryMessage the secondary message
	 * @param primaryMessage   the primary message
	 */
	public void drawGameLoadingMessage(String secondaryMessage, String primaryMessage) {
		if (viewportBuffer != null) {
			viewportBuffer.bindRaster();
			Rasterizer3D.scanlineOffsets = viewportScanlineOffsets;
			int boxY = layout.unobscuredViewportHeight() / 2 - 16;
			if (secondaryMessage != null)
				boxY -= 7;
			plainFont.drawCenteredText(primaryMessage, layout.unobscuredViewportWidth() / 2 + 1, boxY, 0);
			plainFont.drawCenteredText(primaryMessage, layout.unobscuredViewportWidth() / 2, boxY - 1, 0xffffff);
			boxY += 15;
			if (secondaryMessage != null) {
				plainFont.drawCenteredText(secondaryMessage, layout.unobscuredViewportWidth() / 2 + 1, boxY, 0);
				plainFont.drawCenteredText(secondaryMessage, layout.unobscuredViewportWidth() / 2, boxY - 1, 0xffffff);
			}
			viewportBuffer.draw(super.graphics, layout.viewportX(), layout.viewportY());
			return;
		}
		if (super.gameBuffer != null) {
			super.gameBuffer.bindRaster();
			Rasterizer3D.scanlineOffsets = fullScreenScanlineOffsets;
			int screenX = 251;
			char boxWidth = '\u012C';
			byte screenY = 50;
			Rasterizer.drawFilledRectangle(383 - boxWidth / 2, screenX - 5 - screenY / 2, boxWidth, screenY, 0);
			Rasterizer.drawUnfilledRectangle(383 - boxWidth / 2, screenX - 5 - screenY / 2, boxWidth, screenY,
					0xffffff);
			if (secondaryMessage != null)
				screenX -= 7;
			plainFont.drawCenteredText(primaryMessage, 383, screenX, 0);
			plainFont.drawCenteredText(primaryMessage, 382, screenX - 1, 0xffffff);
			screenX += 15;
			if (secondaryMessage != null) {
				plainFont.drawCenteredText(secondaryMessage, 383, screenX, 0);
				plainFont.drawCenteredText(secondaryMessage, 382, screenX - 1, 0xffffff);
			}
			super.gameBuffer.draw(super.graphics, 0, 0);
		}
	}

	/**
	 * Returns whether a menu entry is the add-friend action.
	 * @param menuIndex menu index
	 * @return whether the entry adds a friend
	 */
	public boolean isAddFriendMenuAction(int menuIndex) {
		return menuController.state().isAddFriendAction(menuIndex);
	}

	/**
	 * Projects and draws the flashing world-coordinate hint icon.
	 */
	public void drawWorldHintIcon() {
		if (hintIconType != 2)
			return;
		projectWorldToScreen((hintTileX - regionManager.baseX << 7) + hintOffsetX, hintHeight * 2,
				(hintTileY - regionManager.baseY << 7) + hintOffsetY);
		if (projectedX > -1 && gameCycle % 20 < 10)
			hintIconSprites[0].drawImage(projectedX - 12, projectedY - 28);
	}

	/**
	 * Runs one render cycle in logged-in mode or title/login mode.
	 */
	public void processDrawing() {
		refreshGraphicsContextIfRequested();
		if (duplicateClientError || loadingError || invalidHostError) {
			drawStartupErrorScreen();
			return;
		}

		Graphics displayGraphics = super.graphics;
		if (displayGraphics == null)
			return;

		if (presentationBuffer == null
				|| presentationBuffer.getWidth() != super.canvasWidth
				|| presentationBuffer.getHeight() != super.canvasHeight) {
			presentationBuffer = new BufferedImage(super.canvasWidth, super.canvasHeight, BufferedImage.TYPE_INT_RGB);
		}

		Graphics frameGraphics = presentationBuffer.getGraphics();
		frameGraphics.setColor(Color.black);
		frameGraphics.fillRect(0, 0, super.canvasWidth, super.canvasHeight);

		/*
		 * All of the legacy GraphicsBuffer blits below now target one off-screen
		 * presentation image. This prevents the user from seeing the intermediate
		 * black clear and partially assembled UI that caused resize-mode flicker.
		 */
		super.graphics = frameGraphics;
		try {
			drawCycle++;
			if (!loggedIn)
				drawLoginScreen(false);
			else
				drawGameScreen();
		} finally {
			super.graphics = displayGraphics;
			frameGraphics.dispose();
		}

		displayGraphics.drawImage(presentationBuffer, 0, 0, null);
		mouseButtonHoldTicks = 0;
	}

	/**
	 * Draws the open context menu and highlights the entry under the mouse.
	 */
	public void drawContextMenu() {
		int menuX = menuController.state().offsetX;
		int menuY = menuController.state().offsetY;
		int menuWidth = menuController.state().width;
		int menuHeight = menuController.state().height;
		int headerColor = 0x5d5447;
		Rasterizer.drawFilledRectangle(menuX, menuY, menuWidth, menuHeight, headerColor);
		Rasterizer.drawFilledRectangle(menuX + 1, menuY + 1, menuWidth - 2, 16, 0);
		Rasterizer.drawUnfilledRectangle(menuX + 1, menuY + 18, menuWidth - 2, menuHeight - 19, 0);
		boldFont.drawText("Choose Option", menuX + 3, menuY + 14, headerColor);
		int mouseX = super.mouseX;
		int mouseY = super.mouseY;
		if (menuController.state().screenArea == 0) {
			mouseX -= layout.viewportX();
			mouseY -= layout.viewportY();
		}
		if (menuController.state().screenArea == 1) {
			mouseX -= layout.sidebarX();
			mouseY -= layout.sidebarY();
		}
		if (menuController.state().screenArea == 2) {
			mouseX -= layout.chatboxX();
			mouseY -= layout.chatboxY();
		}
		for (int entryIndex = 0; entryIndex < menuController.state().count; entryIndex++) {
			int entryY = menuY + 31 + (menuController.state().count - 1 - entryIndex) * 15;
			int entryColor = 0xffffff;
			if (mouseX > menuX && mouseX < menuX + menuWidth && mouseY > entryY - 13 && mouseY < entryY + 3)
				entryColor = 0xffff00;
			boldFont.drawTextWithTags(menuController.state().actionNames[entryIndex], menuX + 3, entryY, entryColor, true);
		}

	}

	/**
	 * Draws the current welcome, credentials, or create-account title-screen state.
	 *
	 * @param hideButtons whether to omit the login buttons
	 */
	public void drawLoginScreen(boolean hideButtons) {
		createTitleScreenBuffers();
		loginBoxBuffer.bindRaster();
		titleBoxImage.draw(0, 0);
		char boxWidth = '\u0168';
		char boxHeight = '\310';
		if (loginScreen.state == LoginScreen.WELCOME) {
			int textY = boxHeight / 2 + 80;
			smallFont.drawCenteredTextWithTags(onDemandFetcher.statusString, boxWidth / 2, textY, 0x75a9a9, true);
			textY = boxHeight / 2 - 20;
			boldFont.drawCenteredTextWithTags("Welcome to RuneScape", boxWidth / 2, textY, 0xffff00, true);
			textY += 30;
			int buttonX = boxWidth / 2 - 80;
			int buttonY = boxHeight / 2 + 20;
			titleButtonImage.draw(buttonX - 73, buttonY - 20);
			boldFont.drawCenteredTextWithTags("New User", buttonX, buttonY + 5, 0xffffff, true);
			buttonX = boxWidth / 2 + 80;
			titleButtonImage.draw(buttonX - 73, buttonY - 20);
			boldFont.drawCenteredTextWithTags("Existing User", buttonX, buttonY + 5, 0xffffff, true);
		}
		if (loginScreen.state == LoginScreen.CREDENTIALS) {
			int textY2 = boxHeight / 2 - 40;
			if (loginScreen.message1.length() > 0) {
				boldFont.drawCenteredTextWithTags(loginScreen.message1, boxWidth / 2, textY2 - 15, 0xffff00, true);
				boldFont.drawCenteredTextWithTags(loginScreen.message2, boxWidth / 2, textY2, 0xffff00, true);
				textY2 += 30;
			} else {
				boldFont.drawCenteredTextWithTags(loginScreen.message2, boxWidth / 2, textY2 - 7, 0xffff00, true);
				textY2 += 30;
			}
			boldFont.drawTextWithTags(
					"Username: " + loginScreen.username
							+ ((loginScreen.focusedField == 0) & (gameCycle % 40 < 20) ? "@yel@|" : ""),
					boxWidth / 2 - 90, textY2, 0xffffff, true);
			textY2 += 15;
			boldFont.drawTextWithTags(
					"Password: " + TextFormatter.mask(loginScreen.password)
							+ ((loginScreen.focusedField == 1) & (gameCycle % 40 < 20) ? "@yel@|" : ""),
					boxWidth / 2 - 88, textY2, 0xffffff, true);
			textY2 += 15;
			if (!hideButtons) {
				int buttonX2 = boxWidth / 2 - 80;
				int buttonY2 = boxHeight / 2 + 50;
				titleButtonImage.draw(buttonX2 - 73, buttonY2 - 20);
				boldFont.drawCenteredTextWithTags("Login", buttonX2, buttonY2 + 5, 0xffffff, true);
				buttonX2 = boxWidth / 2 + 80;
				titleButtonImage.draw(buttonX2 - 73, buttonY2 - 20);
				boldFont.drawCenteredTextWithTags("Cancel", buttonX2, buttonY2 + 5, 0xffffff, true);
			}
		}
		if (loginScreen.state == LoginScreen.CREATE_ACCOUNT) {
			boldFont.drawCenteredTextWithTags("Create a free account", boxWidth / 2, boxHeight / 2 - 60, 0xffff00,
					true);
			int textY3 = boxHeight / 2 - 35;
			boldFont.drawCenteredTextWithTags("To create a new account you need to", boxWidth / 2, textY3, 0xffffff,
					true);
			textY3 += 15;
			boldFont.drawCenteredTextWithTags("go back to the main RuneScape webpage", boxWidth / 2, textY3, 0xffffff,
					true);
			textY3 += 15;
			boldFont.drawCenteredTextWithTags("and choose the 'create account'", boxWidth / 2, textY3, 0xffffff, true);
			textY3 += 15;
			boldFont.drawCenteredTextWithTags("button near the top of that page.", boxWidth / 2, textY3, 0xffffff,
					true);
			textY3 += 15;
			int buttonX3 = boxWidth / 2;
			int buttonY3 = boxHeight / 2 + 50;
			titleButtonImage.draw(buttonX3 - 73, buttonY3 - 20);
			boldFont.drawCenteredTextWithTags("Cancel", buttonX3, buttonY3 + 5, 0xffffff, true);
		}
		loginBoxBuffer.draw(super.graphics, 202, 171);
		// Present the static title frame every cycle as well, so an AWT expose cannot
		// leave portions of the login screen white until another state change.
		gameScreenRedraw = false;
		titleTopBuffer.draw(super.graphics, 128, 0);
		titleBottomBuffer.draw(super.graphics, 202, 371);
		titleLeftBottomBuffer.draw(super.graphics, 0, 265);
		titleRightBottomBuffer.draw(super.graphics, 562, 265);
		titleLeftCenterBuffer.draw(super.graphics, 128, 171);
		titleRightCenterBuffer.draw(super.graphics, 562, 171);
	}

	/**
	 * Draws the sidebar background, selected tab, and active sidebar interface.
	 */
	public void drawSidebar() {
		sidebarBuffer.bindRaster();
		Rasterizer3D.scanlineOffsets = sidebarScanlineOffsets;
		sidebarBackground.draw(0, 0);
		if (interfaceController.state().sidebarOverlayInterfaceId != -1)
			drawInterface(0, 0, Widget.get(interfaceController.state().sidebarOverlayInterfaceId), 0);
		else if (interfaceController.state().tabInterfaceIds[interfaceController.state().selectedTab] != -1)
			drawInterface(0, 0, Widget.get(interfaceController.state().tabInterfaceIds[interfaceController.state().selectedTab]), 0);
		if (menuController.state().open && menuController.state().screenArea == 1)
			drawContextMenu();
		sidebarBuffer.draw(super.graphics, layout.sidebarX(), layout.sidebarY());
		viewportBuffer.bindRaster();
		Rasterizer3D.scanlineOffsets = viewportScanlineOffsets;
	}

	/**
	 * Formats an inventory amount using the renderer's legacy comma/K/million style.
	 *
	 * @param amount numeric amount
	 * @return formatted amount
	 */
	public static String formatAmountWithCommas(int amount) {
		return WidgetRenderer.formatAmountWithCommas(amount);
	}

	/**
	 * Projects an actor-relative point into viewport screen coordinates.
	 *
	 * @param actor        the actor
	 * @param heightOffset the height offset
	 */
	private void projectActorToScreen(Actor actor, int heightOffset) {
		projectWorldToScreen(actor.x, heightOffset, actor.y);
	}

	/**
	 * Projects a world-space point into viewport screen coordinates using the
	 * current camera.
	 *
	 * @param worldX       the local world-space X coordinate
	 * @param heightOffset the height offset
	 * @param worldY       the local world-space Y coordinate
	 */
	private void projectWorldToScreen(int worldX, int heightOffset, int worldY) {
		CameraController.ScreenPoint point = cameraController.project(worldState, currentPlane, worldX, heightOffset,
				worldY);
		projectedX = point.x;
		projectedY = point.y;
	}

	/**
	 * Prints client timing, memory, mouse, and network debug state to standard
	 * output.
	 */
	public void printDebugInfo() {
		System.out.println("============");
		System.out.println("flame-cycle:" + titleFlameAnimator.cycle());
		if (onDemandFetcher != null)
			System.out.println("Od-cycle:" + onDemandFetcher.onDemandCycle);
		System.out.println("loop-cycle:" + gameCycle);
		System.out.println("draw-cycle:" + drawCycle);
		System.out.println("ptype:" + networkSession.incomingOpcode);
		System.out.println("psize:" + networkSession.incomingLength);
		if (networkSession.isConnected())
			networkSession.printDebugInformation();
		super.debugTiming = true;
	}

	/**
	 * Returns the AWT component used by the standalone GameShell.
	 *
	 * @return the resulting component
	 */
	public Component getGameComponent() {
		if (super.gameFrame != null)
			return super.gameFrame;
		else
			return this;
	}

	/**
	 * Draws startup loading progress using the title-screen buffers when available.
	 *
	 * @param percent the percent
	 * @param message the message text
	 */
	public void drawLoadingText(int percent, String message) {
		loadingPercent = percent;
		loadingMessage = message;
		createTitleScreenBuffers();
		if (titleArchive == null) {
			super.drawLoadingText(percent, message);
			return;
		}
		loginBoxBuffer.bindRaster();
		char loginBoxWidth = '\u0168';
		char loginBoxHeight = '\310';
		byte barHeight = 20;
		boldFont.drawCenteredText("RuneScape is loading - please wait...", loginBoxWidth / 2,
				loginBoxHeight / 2 - 26 - barHeight, 0xffffff);
		int barY = loginBoxHeight / 2 - 18 - barHeight;
		Rasterizer.drawUnfilledRectangle(loginBoxWidth / 2 - 152, barY, 304, 34, 0x8c1111);
		Rasterizer.drawUnfilledRectangle(loginBoxWidth / 2 - 151, barY + 1, 302, 32, 0);
		Rasterizer.drawFilledRectangle(loginBoxWidth / 2 - 150, barY + 2, percent * 3, 30, 0x8c1111);
		Rasterizer.drawFilledRectangle((loginBoxWidth / 2 - 150) + percent * 3, barY + 2, 300 - percent * 3, 30, 0);
		boldFont.drawCenteredText(message, loginBoxWidth / 2, (loginBoxHeight / 2 + 5) - barHeight, 0xffffff);
		loginBoxBuffer.draw(super.graphics, 202, 171);
		if (gameScreenRedraw) {
			gameScreenRedraw = false;
			if (!titleFlameAnimator.isRunning()) {
				titleLeftFlameBuffer.draw(super.graphics, 0, 0);
				titleRightFlameBuffer.draw(super.graphics, 637, 0);
			}
			titleTopBuffer.draw(super.graphics, 128, 0);
			titleBottomBuffer.draw(super.graphics, 202, 371);
			titleLeftBottomBuffer.draw(super.graphics, 0, 265);
			titleRightBottomBuffer.draw(super.graphics, 562, 265);
			titleLeftCenterBuffer.draw(super.graphics, 128, 171);
			titleRightCenterBuffer.draw(super.graphics, 562, 171);
		}
	}

	/**
	 * Decodes and lays out the title background image across the fixed title
	 * buffers.
	 */
	public void drawTitleBackground() {
		byte titleData[] = titleArchive.read("title.dat");
		ImageRGB sprite = new ImageRGB(titleData, this);
		titleLeftFlameBuffer.bindRaster();
		sprite.drawInverse(0, 0);
		titleRightFlameBuffer.bindRaster();
		sprite.drawInverse(-637, 0);
		titleTopBuffer.bindRaster();
		sprite.drawInverse(-128, 0);
		titleBottomBuffer.bindRaster();
		sprite.drawInverse(-202, -371);
		loginBoxBuffer.bindRaster();
		sprite.drawInverse(-202, -171);
		titleLeftBottomBuffer.bindRaster();
		sprite.drawInverse(0, -265);
		titleRightBottomBuffer.bindRaster();
		sprite.drawInverse(-562, -265);
		titleLeftCenterBuffer.bindRaster();
		sprite.drawInverse(-128, -171);
		titleRightCenterBuffer.bindRaster();
		sprite.drawInverse(-562, -171);
		int scanlinePixels[] = new int[sprite.width];
		for (int row = 0; row < sprite.height; row++) {
			for (int column = 0; column < sprite.width; column++)
				scanlinePixels[column] = sprite.pixels[(sprite.width - column - 1) + sprite.width * row];

			for (int swapIndex = 0; swapIndex < sprite.width; swapIndex++)
				sprite.pixels[swapIndex + sprite.width * row] = scanlinePixels[swapIndex];

		}

		titleLeftFlameBuffer.bindRaster();
		sprite.drawInverse(382, 0);
		titleRightFlameBuffer.bindRaster();
		sprite.drawInverse(-255, 0);
		titleTopBuffer.bindRaster();
		sprite.drawInverse(254, 0);
		titleBottomBuffer.bindRaster();
		sprite.drawInverse(180, -371);
		loginBoxBuffer.bindRaster();
		sprite.drawInverse(180, -171);
		titleLeftBottomBuffer.bindRaster();
		sprite.drawInverse(382, -265);
		titleRightBottomBuffer.bindRaster();
		sprite.drawInverse(-180, -265);
		titleLeftCenterBuffer.bindRaster();
		sprite.drawInverse(254, -171);
		titleRightCenterBuffer.bindRaster();
		sprite.drawInverse(-180, -171);
		sprite = new ImageRGB(titleArchive, "logo", 0);
		titleTopBuffer.bindRaster();
		sprite.drawImage(382 - sprite.width / 2 - 128, 18);
		sprite = null;
		titleData = null;
		scanlinePixels = null;
		System.gc();
	}

	/** Stops and releases title-only animation resources. */
	public void disposeTitleScreen() {
		titleFlameAnimator.dispose();
		titleBoxImage = null;
		titleButtonImage = null;
	}

	/**
	 * Draws a cache-defined widget tree through {@link WidgetRenderer}.
	 *
	 * @param y root Y coordinate
	 * @param x root X coordinate
	 * @param widget root widget
	 * @param scrollY root scroll offset
	 */
	public void drawInterface(int y, int x, Widget widget, int scrollY) {
		widgetRenderer.drawInterface(new WidgetRenderer.RenderContext(super.mouseX, super.mouseY, animationCycleDelta, smallFont, plainFont,
				scrollbarTop, scrollbarBottom, scrollbarTrackColor, scrollbarThumbColor, scrollbarHighlightColor,
				scrollbarShadowColor), y, x, widget, scrollY);
	}

	/**
	 * Returns the interpolated world height at the supplied local world
	 * coordinates.
	 *
	 * @param worldY the local world-space Y coordinate
	 * @param worldX the local world-space X coordinate
	 * @param plane  the scene plane
	 * @return the resulting numeric value
	 */
	private int getTileHeight(int worldY, int worldX, int plane) {
		return worldState.getTileHeight(worldX, worldY, plane);
	}

	/**
	 * Queues a localized area sound if it falls within the original player-centered
	 * radius check.
	 *
	 * @param soundId the sound-effect identifier
	 * @param loops   the sound-effect loop count
	 * @param radius  the area-sound radius
	 * @param tileX   the local scene-tile X coordinate
	 * @param tileY   the local scene-tile Y coordinate
	 */
	void queueAreaSound(int soundId, int loops, int radius, int tileX, int tileY) {
		soundEffectQueue.queueAreaSound(soundId, loops, radius, tileX, tileY, localPlayer.pathX[0],
				localPlayer.pathY[0], lowMemory);
	}

	/**
	 * Advances region-loading state and rebuilds the region when all required
	 * resources are ready.
	 */
	private void updateRegionLoading() {
		if (lowMemory && regionManager.loadingStage == RegionManager.STAGE_LOADED
				&& Region.currentPlane != currentPlane) {
			drawGameLoadingMessage(null, "Loading - please wait.");
			regionManager.loadingStage = RegionManager.STAGE_LOADING;
			regionManager.loadingStartTime = System.currentTimeMillis();
		}
		if (regionManager.loadingStage == RegionManager.STAGE_LOADING) {
			int status = regionManager.getLoadingStatus();
			if (status == 0) {
				regionManager.loadingStage = RegionManager.STAGE_LOADED;
				Region.currentPlane = currentPlane;
				lastMinimapPlane = -1;
				regionManager.buildRegion(worldState, currentPlane, lowMemory, networkSession.outgoing, onDemandFetcher,
						super.gameFrame != null, () -> {
							if (viewportBuffer != null) {
								viewportBuffer.bindRaster();
								Rasterizer3D.scanlineOffsets = viewportScanlineOffsets;
							}
						});
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.REGION_LOADED);
			} else if (System.currentTimeMillis() - regionManager.loadingStartTime > 0x57e40L) {
				Signlink.reportError(
						loginScreen.username + " glcfb " + loginSession.getServerSessionKey() + "," + status + "," + lowMemory + ","
								+ resourceLoader.getCacheIndex(0) + "," + onDemandFetcher.getOutstandingRequestCount()
								+ "," + currentPlane + "," + regionManager.regionX + "," + regionManager.regionY);
				regionManager.loadingStartTime = System.currentTimeMillis();
			}
		}
		if (regionManager.loadingStage == RegionManager.STAGE_LOADED && currentPlane != lastMinimapPlane) {
			lastMinimapPlane = currentPlane;
			rebuildMinimap(currentPlane);
		}
	}

	/**
	 * Converts a minimap click through rotation/zoom into a world movement request.
	 */
	private void processMinimapClick() {
		if (minimapRenderer.state != 0 || clickButton != 1) {
			return;
		}
		MinimapRenderer.Click click = minimapRenderer.transformClick(clickX - layout.extraWidth(), clickY, localPlayer,
				cameraController.followYaw);
		if (click == null) {
			return;
		}
		if (walkTo(true, click.tileX, click.tileY, 0, 0, MovementPacketEncoder.MINIMAP, 0, 0, 0)) {
			networkSession.outgoing.writeByte(click.localX);
			networkSession.outgoing.writeByte(click.localY);
			networkSession.outgoing.writeShort(cameraController.followYaw);
			networkSession.outgoing.writeByte(57);
			networkSession.outgoing.writeByte(minimapRenderer.rotationOffset);
			networkSession.outgoing.writeByte(minimapRenderer.zoomOffset);
			networkSession.outgoing.writeByte(89);
			networkSession.outgoing.writeShort(localPlayer.x);
			networkSession.outgoing.writeShort(localPlayer.y);
			networkSession.outgoing.writeByte(alternativeRoute);
			networkSession.outgoing.writeByte(63);
		}
	}

	/**
	 * Allocates the full GameShell backing buffer if it does not already exist.
	 */
	public void createGameBuffer() {
		if (super.gameBuffer != null)
			return;
		disposeTitleScreen();
		titleTopBuffer = null;
		titleBottomBuffer = null;
		loginBoxBuffer = null;
		titleLeftFlameBuffer = null;
		titleRightFlameBuffer = null;
		titleLeftBottomBuffer = null;
		titleRightBottomBuffer = null;
		titleLeftCenterBuffer = null;
		titleRightCenterBuffer = null;
		chatboxBuffer = null;
		minimapBuffer = null;
		sidebarBuffer = null;
		viewportBuffer = null;
		chatModesBuffer = null;
		bottomTabsBuffer = null;
		topTabsBuffer = null;
		super.gameBuffer = new GraphicsBuffer(getGameComponent(), ClientLayout.FIXED_WIDTH, ClientLayout.FIXED_HEIGHT);
		gameScreenRedraw = true;
	}

	/**
	 * Tests whether a display name belongs to the local player or current friend
	 * list.
	 *
	 * @param name the name
	 * @return true when the requested condition/action succeeds; otherwise false
	 */
	public boolean isFriendOrSelf(String name) {
		return socialManager.isFriendOrSelf(name, localPlayer.name);
	}

	/**
	 * Processes mouse and keyboard input for the title/login screen state machine.
	 */
	public void processLoginScreenInput() {
		if (loginScreen.state == LoginScreen.WELCOME) {
			int buttonX = super.canvasWidth / 2 - 80;
			int buttonY = super.canvasHeight / 2 + 20;
			buttonY += 20;
			if (super.clickButton == 1 && super.clickX >= buttonX - 75 && super.clickX <= buttonX + 75
					&& super.clickY >= buttonY - 20 && super.clickY <= buttonY + 20) {
				loginScreen.showCreateAccount();
			}
			buttonX = super.canvasWidth / 2 + 80;
			if (super.clickButton == 1 && super.clickX >= buttonX - 75 && super.clickX <= buttonX + 75
					&& super.clickY >= buttonY - 20 && super.clickY <= buttonY + 20) {
				loginScreen.showCredentials();
				return;
			}
		} else {
			if (loginScreen.state == LoginScreen.CREDENTIALS) {
				int fieldY = super.canvasHeight / 2 - 40;
				fieldY += 30;
				fieldY += 25;
				if (super.clickButton == 1 && super.clickY >= fieldY - 15 && super.clickY < fieldY)
					loginScreen.focusedField = 0;
				fieldY += 15;
				if (super.clickButton == 1 && super.clickY >= fieldY - 15 && super.clickY < fieldY)
					loginScreen.focusedField = 1;
				fieldY += 15;
				int buttonX2 = super.canvasWidth / 2 - 80;
				int buttonY2 = super.canvasHeight / 2 + 50;
				buttonY2 += 20;
				if (super.clickButton == 1 && super.clickX >= buttonX2 - 75 && super.clickX <= buttonX2 + 75
						&& super.clickY >= buttonY2 - 20 && super.clickY <= buttonY2 + 20) {
					loginSession.resetFailures();
					login(loginScreen.username, loginScreen.password, false);
					if (loggedIn)
						return;
				}
				buttonX2 = super.canvasWidth / 2 + 80;
				if (super.clickButton == 1 && super.clickX >= buttonX2 - 75 && super.clickX <= buttonX2 + 75
						&& super.clickY >= buttonY2 - 20 && super.clickY <= buttonY2 + 20) {
					loginScreen.cancelCredentials();
				}
				do {
					int key = pollKey();
					if (key == -1)
						break;
					loginScreen.processKey(key);
				} while (true);
				return;
			}
			if (loginScreen.state == LoginScreen.CREATE_ACCOUNT) {
				int buttonX3 = super.canvasWidth / 2;
				int buttonY3 = super.canvasHeight / 2 + 50;
				buttonY3 += 20;
				if (super.clickButton == 1 && super.clickX >= buttonX3 - 75 && super.clickX <= buttonX3 + 75
						&& super.clickY >= buttonY3 - 20 && super.clickY <= buttonY3 + 20)
					loginScreen.cancelCreateAccount();
			}
		}
	}

	/**
	 * Builds scene entities, positions/shakes/restores the camera, renders the
	 * world, and presents the viewport.
	 */
	private void renderGameScene() {
		destinationX = sceneEntityRenderer.beginFrame(localPlayer, destinationX, destinationY);
		addPlayersToScene(true);
		addNpcsToScene(true);
		addPlayersToScene(false);
		addNpcsToScene(false);
		worldState.updateProjectiles(currentPlane, gameCycle, animationCycleDelta, localPlayerServerIndex, localPlayer,
				actorSynchronizer, networkSession.outgoing);
		worldState.updateGraphicsObjects(currentPlane, gameCycle, animationCycleDelta);

		if (!cameraController.cinematic) {
			int pitch = cameraController.getMinimumPitchForRender();
			int yaw = cameraController.followYaw + cameraController.yawOffset & Angle.MASK;
			positionCamera(worldState.getTileHeight(localPlayer.x, localPlayer.y, currentPlane) - 50,
					cameraController.followTargetX, pitch, 600 + pitch * 3, yaw, cameraController.followTargetY);
		}
		int renderPlane = cameraController.cinematic ? selectCinematicRenderPlane() : selectNormalRenderPlane();
		CameraController.Snapshot cameraSnapshot = cameraController.snapshot();
		cameraController.applyShake();

		int textureCycle = Rasterizer3D.textureCycle;
		Model.pickingEnabled = true;
		Model.pickedCount = 0;
		Model.mouseX = super.mouseX - layout.viewportX();
		Model.mouseY = super.mouseY - layout.viewportY();
		Rasterizer.resetPixels();
		worldState.scene.render(cameraController.x, cameraController.y, cameraController.height, renderPlane,
				cameraController.yaw, cameraController.pitch);
		worldState.scene.clearTemporaryObjects();
		drawActorOverlays();
		drawWorldHintIcon();
		animateTextures(textureCycle);
		drawViewportOverlays();
		viewportBuffer.draw(super.graphics, layout.viewportX(), layout.viewportY());
		cameraController.restore(cameraSnapshot);
	}

	/**
	 * Initializes a Client instance and wires the extracted subsystem owners and
	 * callbacks.
	 */
	public Client() {
		skillExperiences = new int[Skills.COUNT];
		itemSearchQuery = "";
		itemSearchResultNames = new String[100];
		itemSearchResultIds = new int[100];
		crossSprites = new ImageRGB[8];
		minimapMaskWidths = new int[151];
		networkSession = new NetworkSession();
		incomingPacketDispatcher = new IncomingPacketDispatcher(networkSession, new ClientIncomingPacketHandler(this));
		socialManager = new SocialManager();
		chatController = new ChatController();
		interfaceController = new InterfaceController();
		menuController = new MenuController(layout, interfaceController, socialManager, chatController, () -> mouseButtonHoldTicks, interfaceRedrawSink);
		loginScreen = new LoginScreen();
		titleFlameAnimator = new TitleFlameAnimator();
		appearanceEditor = new AppearanceEditor();
		resourceLoader = new ResourceLoader();
		loginSession = new LoginSession(networkSession, this::openSocket, resourceLoader::getArchiveCrc,
				new LoginSession.StatusSink() {
					@Override
					public void setMessage(String line1, String line2) {
						loginScreen.message1 = line1;
						loginScreen.message2 = line2;
					}

					@Override
					public void redraw() {
						drawLoginScreen(true);
					}
				}, new LoginSession.Listener() {
					@Override
					public void onFullLogin(int rights, boolean flagged) {
						handleFullLogin(rights, flagged);
					}

					@Override
					public void onReconnectAccepted() {
						handleReconnectAccepted();
					}
				}, RSA_EXPONENT, RSA_MODULUS, 43594 + portOffset);
		soundEffectQueue = new SoundEffectQueue();
		musicController = new MusicController();
		pathfinder = new Pathfinder();
		actorSynchronizer = new ActorSynchronizer();
		actorUpdater = new ActorUpdater();
		cameraController = new CameraController();
		sceneEntityRenderer = new SceneEntityRenderer();
		minimapRenderer = new MinimapRenderer();
		regionManager = new RegionManager();
		varpState = new VarpState();
		ClientScriptContext scriptContext = new ClientScriptContext(
				skill -> currentSkillLevels[skill],
				skill -> baseSkillLevels[skill],
				skill -> skillExperiences[skill],
				varpState::get,
				levelIndex -> experienceTable[levelIndex],
				width -> bitMasks[width],
				() -> runEnergy,
				() -> weight,
				() -> localPlayer.combatLevel,
				() -> (localPlayer.x >> 7) + regionManager.baseX,
				() -> (localPlayer.y >> 7) + regionManager.baseY,
				() -> membersWorld);
		widgetRuntime = new WidgetRuntime(scriptContext);
		widgetRenderer = new WidgetRenderer(interfaceController, widgetRuntime, this::updateWidgetContent);
		scrollbarTrackColor = 0x23201b;
		projectedX = -1;
		projectedY = -1;
		overheadTextLimit = 50;
		overheadTextXs = new int[overheadTextLimit];
		overheadTextYs = new int[overheadTextLimit];
		overheadTextHeights = new int[overheadTextLimit];
		overheadTextHalfWidths = new int[overheadTextLimit];
		overheadTextColorCodes = new int[overheadTextLimit];
		overheadTextEffects = new int[overheadTextLimit];
		overheadTextCycles = new int[overheadTextLimit];
		overheadTexts = new String[overheadTextLimit];
		tabAreaRedraw = false;
		hintIconSprites = new ImageRGB[32];
		localPlayerServerIndex = -1;
		sidebarIcons = new IndexedImage[13];
		duplicateClientError = false;
		minimapMaskOffsets = new int[151];
		currentSkillLevels = new int[Skills.COUNT];
		mapFunctionSprites = new ImageRGB[100];
		gameScreenRedraw = false;
		baseSkillLevels = new int[Skills.COUNT];
		regionManager.specialRegion = false;
		playerActions = new String[5];
		playerActionLowPriority = new boolean[5];
		prayerIconSprites = new ImageRGB[32];
		scrollbarThumbColor = 0x4d4233;
		invalidHostError = false;
		interfaceController.setReportAbuseMutePlayer(false);
		interfaceController.setScrollbarDragging(false);
		chatBuffer = new Buffer(new byte[5000]);
		scrollbarHighlightColor = 0x766654;
		loggedIn = false;
		moderatorIcons = new IndexedImage[2];
		mapSceneSprites = new IndexedImage[100];
		inventoryDragMoved = false;
		regionManager.instanced = false;
		compassMaskOffsets = new int[33];
		sidebarRedraw = false;
		hitmarkSprites = new ImageRGB[20];
		regionManager.awaitingPlayerUpdate = false;
		chatModesRedraw = false;
		interfaceController.setActionPending(false);
		chatboxRedraw = false;
		textureScrollScratch = new byte[16384];
		chatboxScrollWidget = new Widget();
		cameraOrientationChanged = false;
		windowFocusReported = true;
		lastMinimapPlane = -1;
		loadingError = false;
		compassMaskWidths = new int[33];
		scrollbarShadowColor = 0x332d25;
		skullIconSprites = new ImageRGB[32];
	}

	/** The client state for report abuse name. */
	/** The RSA modulus used by the revision-377 login handshake. */
	public static BigInteger RSA_MODULUS = new BigInteger(
			"7162900525229798032761816791230527296329313291232324290237849263501208207972894053929065636522363163621000728841182238772712427862772219676577293600221789");

	/** Stores overhead text colors values. */
	public int overheadTextColors[] = { 0xffff00, 0xff0000, 65280, 65535, 0xff00ff, 0xffffff };

	/** Stores skill experiences values. */
	public int skillExperiences[];
	/** The client state for hint tile x. */
	public int hintTileX;
	/** The client state for hint tile y. */
	public int hintTileY;
	/** The client state for hint height. */
	public int hintHeight;
	/** The client state for hint offset x. */
	public int hintOffsetX;
	/** The client state for hint offset y. */
	public int hintOffsetY;
	/** The client state for item search query. */
	public String itemSearchQuery;
	/** The current number of item search result entries. */
	public int itemSearchResultCount;

	/** Stores item search result names values. */
	public String itemSearchResultNames[];

	/** Stores item search result IDs values. */
	public int itemSearchResultIds[];
	/** The client state for item search scroll offset. */
	public int itemSearchScrollOffset;
	/** The client state for player rights. */
	public int playerRights;
	/** Whether show fps is currently active or requested. */
	public static boolean showFps;
	/** Tracks the current logout timer in client ticks/cycles where applicable. */
	public int logoutTimer;
	/** The client state for redstone1. */
	public IndexedImage redstone1;
	/** The client state for redstone2. */
	public IndexedImage redstone2;
	/** The client state for redstone3. */
	public IndexedImage redstone3;
	/** The client state for redstone1 horizontal. */
	public IndexedImage redstone1Horizontal;
	/** The client state for redstone2 horizontal. */
	public IndexedImage redstone2Horizontal;
	/** The client state for title archive. */
	public Archive titleArchive;
	/**
	 * Tracks the current tooltip hover ticks in client ticks/cycles where
	 * applicable.
	 */
	/**
	 * Counts system update keepalive events for the original client timing/protocol
	 * behavior.
	 */
	public static int systemUpdateKeepaliveCounter;

	/** Stores cross sprites values. */
	public ImageRGB crossSprites[];
	/** The client state for last click time. */
	public long lastClickTime;
	/** The graphics or protocol buffer used for back left1 buffer. */
	public GraphicsBuffer backLeft1Buffer;
	/** The graphics or protocol buffer used for back left2 buffer. */
	public GraphicsBuffer backLeft2Buffer;
	/** The graphics or protocol buffer used for back right1 buffer. */
	public GraphicsBuffer backRight1Buffer;
	/** The graphics or protocol buffer used for back right2 buffer. */
	public GraphicsBuffer backRight2Buffer;
	/** The graphics or protocol buffer used for back top1 buffer. */
	public GraphicsBuffer backTop1Buffer;
	/** The graphics or protocol buffer used for back vertical middle1 buffer. */
	public GraphicsBuffer backVerticalMiddle1Buffer;
	/** The graphics or protocol buffer used for back vertical middle2 buffer. */
	public GraphicsBuffer backVerticalMiddle2Buffer;
	/** The graphics or protocol buffer used for back vertical middle3 buffer. */
	public GraphicsBuffer backVerticalMiddle3Buffer;
	/** The graphics or protocol buffer used for back horizontal middle2 buffer. */
	public GraphicsBuffer backHorizontalMiddle2Buffer;
	/** The current current hovered widget id. */

	/** Stores minimap mask widths values. */
	public int minimapMaskWidths[];
	/** The current current world id. */
	public static int currentWorldId = 10;
	/** The client state for port offset. */
	public static int portOffset;

	/** Host used by every standalone socket opened through Signlink. */
	private static String serverHost = "127.0.0.1";
	/** Whether members world is currently active or requested. */
	public static boolean membersWorld = true;
	/** Whether low memory is currently active or requested. */
	public static boolean lowMemory;
	/** The client state for scrollbar track color. */
	public int scrollbarTrackColor;
	/** The client state for projected x. */
	public int projectedX;
	/** The client state for projected y. */
	public int projectedY;
	/** The current number of overhead text entries. */
	public int overheadTextCount;
	/** The client state for overhead text limit. */
	public int overheadTextLimit;

	/** Stores overhead text xs values. */
	public int overheadTextXs[];

	/** Stores overhead text ys values. */
	public int overheadTextYs[];

	/** Stores overhead text heights values. */
	public int overheadTextHeights[];

	/** Stores overhead text half widths values. */
	public int overheadTextHalfWidths[];

	/** Stores overhead text color codes values. */
	public int overheadTextColorCodes[];

	/** Stores overhead text effects values. */
	public int overheadTextEffects[];

	/** Stores overhead text cycles values. */
	public int overheadTextCycles[];

	/** Stores overhead texts values. */
	public String overheadTexts[];
	/** Whether tab area redraw is currently active or requested. */
	public boolean tabAreaRedraw;
	/** The client state for animation cycle delta. */
	public int animationCycleDelta;

	/** Stores experience table values. */
	public static int experienceTable[];

	/** Stores hint icon sprites values. */
	public ImageRGB hintIconSprites[];
	/** The client state for inventory rearrange mode. */
	public int inventoryRearrangeMode;
	/** Whether account flagged is currently active or requested. */
	public static boolean accountFlagged;
	/** The client state for network session. */
	public NetworkSession networkSession;
	/** Coordinates framed incoming packets with the application packet adapter. */
	private final IncomingPacketDispatcher incomingPacketDispatcher;
	/** The client state for social manager. */
	private final SocialManager socialManager;
	/** Owns chat history, modes, text input, prompts, and chat scrolling. */
	private final ChatController chatController;
	/** The client state for interface state. */
	private final InterfaceController interfaceController;
	/** Receives interface-controller redraw requests. */
	private final InterfaceController.RedrawSink interfaceRedrawSink = new InterfaceController.RedrawSink() {
		@Override
		public void redrawSidebar() {
			sidebarRedraw = true;
		}

		@Override
		public void redrawTabs() {
			tabAreaRedraw = true;
		}

		@Override
		public void redrawChatbox() {
			chatboxRedraw = true;
		}

		@Override
		public void redrawGameScreen() {
			gameScreenRedraw = true;
		}
	};
	/** The client state for menu state. */
	private final MenuController menuController;
	/** The client state for login screen. */
	private final LoginScreen loginScreen;
	/** Owns the revision-377 login handshake, retries, and login session key. */
	private final LoginSession loginSession;
	/** Owns title-flame simulation state and its animation worker. */
	private final TitleFlameAnimator titleFlameAnimator;
	/** Character-design interface state and preview owner. */
	private final AppearanceEditor appearanceEditor;
	/** The client state for resource loader. */
	private final ResourceLoader resourceLoader;
	/** The client state for sound effect queue. */
	private final SoundEffectQueue soundEffectQueue;
	/** The client state for music controller. */
	private final MusicController musicController;
	/** The client state for widget runtime. */
	private final WidgetRuntime widgetRuntime;
	/** Draws cache-defined widget trees and classic scrollbars. */
	private final WidgetRenderer widgetRenderer;
	/** The client state for pathfinder. */
	private final Pathfinder pathfinder;
	/** The client state for actor synchronizer. */
	private final ActorSynchronizer actorSynchronizer;
	/** The client state for actor updater. */
	private final ActorUpdater actorUpdater;
	/** The client state for camera controller. */
	private final CameraController cameraController;
	/** The client state for scene entity renderer. */
	private final SceneEntityRenderer sceneEntityRenderer;
	/** The client state for minimap renderer. */
	private final MinimapRenderer minimapRenderer;
	/** The client state for region manager. */
	private final RegionManager regionManager;
	/** Current and server-shadow client varp state. */
	private final VarpState varpState;
	/** The client state for world state. */
	private WorldState worldState;
	/** The client state for zone updates. */
	private ZoneUpdateHandler zoneUpdates;
	/** The client state for actor chat handler. */
	private final ActorSynchronizer.ChatHandler actorChatHandler = new ActorSynchronizer.ChatHandler() {
		/**
		 * Tests whether the supplied encoded name is ignored.
		 *
		 * @param encodedName the Base-37 encoded player name
		 * @return true when the requested condition/action succeeds; otherwise false
		 */
		@Override
		public boolean isIgnored(long encodedName) {
			return socialManager.isIgnored(encodedName);
		}

		/**
		 * Returns whether tutorial-area state currently suppresses public chat.
		 *
		 * @return true when the requested condition/action succeeds; otherwise false
		 */
		@Override
		public boolean isChatSuppressed() {
			return tutorialIslandFlag != 0;
		}

		/**
		 * Adds a message to the fixed chat history and requests the appropriate redraw.
		 *
		 * @param sender  the message sender name
		 * @param message the message text
		 * @param type    the chat message type
		 */
		@Override
		public void addChatMessage(String sender, String message, int type) {
			Client.this.addChatMessage(sender, message, type);
		}
	};
	/** The client state for local player server index. */
	public int localPlayerServerIndex;
	/** The client state for local player. */
	public static Player localPlayer;
	/** The client state for chat modes background. */
	public IndexedImage chatModesBackground;
	/** The client state for bottom tab background. */
	public IndexedImage bottomTabBackground;
	/** The client state for top tab background. */
	public IndexedImage topTabBackground;

	/** Stores sidebar icons values. */
	public IndexedImage sidebarIcons[];
	/** The client state for redstone1 vertical. */
	public IndexedImage redstone1Vertical;
	/** The client state for redstone2 vertical. */
	public IndexedImage redstone2Vertical;
	/** The client state for redstone3 vertical. */
	public IndexedImage redstone3Vertical;
	/** The client state for redstone1 both. */
	public IndexedImage redstone1Both;
	/** The client state for redstone2 both. */
	public IndexedImage redstone2Both;
	/** The client state for membership days. */
	public int membershipDays;
	/** The client state for chat effects. */
	public int chatEffects;
	/** Whether startup started is currently active or requested. */
	public static boolean startupStarted;

	/** Stores chatbox scanline offsets values. */
	public int chatboxScanlineOffsets[];

	/** Stores sidebar scanline offsets values. */
	public int sidebarScanlineOffsets[];

	/** Stores viewport scanline offsets values. */
	public int viewportScanlineOffsets[];

	/** Stores full screen scanline offsets values. */
	public int fullScreenScanlineOffsets[];


	/** The client state for last recorded mouse x. */
	public int lastRecordedMouseX;
	/** The client state for last recorded mouse y. */
	public int lastRecordedMouseY;
	/** Whether duplicate client error is currently active or requested. */
	public boolean duplicateClientError;

	/** Stores minimap mask offsets values. */
	public int minimapMaskOffsets[];
	/** The client state for cross x. */
	public int crossX;
	/** The client state for cross y. */
	public int crossY;
	/** Tracks the current cross cycle in client ticks/cycles where applicable. */
	public int crossCycle;
	/** The client state for cross type. */
	public int crossType;
	/** The client state for loading message. */
	public String loadingMessage;

	/** Stores current skill levels values. */
	public int currentSkillLevels[];
	/** The client state for weight. */
	public int weight;

	/** Stores map function sprites values. */
	public ImageRGB mapFunctionSprites[];
	/** The client state for recovery questions date. */
	public int recoveryQuestionsDate;
	/** The client state for destination map marker. */
	public ImageRGB destinationMapMarker;
	/** The client state for hint map marker. */
	public ImageRGB hintMapMarker;

	/** The current sidebar tooltip widget id. */
	/** Whether game screen redraw is currently active or requested. */
	public boolean gameScreenRedraw;
	/**
	 * Counts ground item action684 events for the original client timing/protocol
	 * behavior.
	 */
	public static int groundItemAction684Counter;

	/** Stores base skill levels values. */
	public int baseSkillLevels[];
	/**
	 * Tracks the current system update timer in client ticks/cycles where
	 * applicable.
	 */
	public int systemUpdateTimer;
	/** The font used for small font. */
	public TypeFace smallFont;
	/** The font used for plain font. */
	public TypeFace plainFont;
	/** The font used for bold font. */
	public TypeFace boldFont;
	/** The font used for fancy font. */
	public TypeFace fancyFont;
	/** The client state for account membership status. */
	public int accountMembershipStatus;

	/** Stores player actions values. */
	public String playerActions[];

	/** Whether player action low priority is enabled or active. */
	public boolean playerActionLowPriority[];

	/** Stores prayer icon sprites values. */
	public ImageRGB prayerIconSprites[];
	/** The client state for scrollbar thumb color. */
	public int scrollbarThumbColor;
	/** The client state for last password change date. */
	public int lastPasswordChangeDate;


	/** The client state for multi combat overlay. */
	public ImageRGB multiCombatOverlay;
	/** The client state for current plane. */
	public int currentPlane;
	/**
	 * Tracks the current mouse button hold ticks in client ticks/cycles where
	 * applicable.
	 */
	public int mouseButtonHoldTicks;
	/** The client state for scrollbar top. */
	public IndexedImage scrollbarTop;
	/** The client state for scrollbar bottom. */
	public IndexedImage scrollbarBottom;
	/** Whether invalid host error is currently active or requested. */
	public boolean invalidHostError;
	/** Whether report abuse mute player is currently active or requested. */

	/**
	 * Counts ground item action26 events for the original client timing/protocol
	 * behavior.
	 */
	public static int groundItemAction26Counter;
	/**
	 * Tracks the current title flame cycle in client ticks/cycles where applicable.
	 */
	public int titleFlameCycle;
	/** The current chatbox hovered widget id. */
	/** The graphics or protocol buffer used for chat modes buffer. */
	public GraphicsBuffer chatModesBuffer;
	/** The graphics or protocol buffer used for bottom tabs buffer. */
	public GraphicsBuffer bottomTabsBuffer;
	/** The graphics or protocol buffer used for top tabs buffer. */
	public GraphicsBuffer topTabsBuffer;
	/** The sprite resource used for compass sprite. */
	public ImageRGB compassSprite;

	/** The client state for destination x. */
	public int destinationX;
	/** The client state for destination y. */
	public int destinationY;
	/** The client state for alternative route. */
	public int alternativeRoute;
	/** Whether scrollbar dragging is currently active or requested. */
	/** The current viewport tooltip widget id. */
	/** The graphics or protocol buffer used for chat buffer. */
	public Buffer chatBuffer;
	/** The client state for scrollbar highlight color. */
	public int scrollbarHighlightColor;
	/** Whether logged in is currently active or requested. */
	public volatile boolean loggedIn;
	/**
	 * Counts inventory action961 events for the original client timing/protocol
	 * behavior.
	 */
	public static int inventoryAction961Counter;

	/** Stores moderator icons values. */
	public IndexedImage moderatorIcons[];
	/** The client state for hint player index. */
	public int hintPlayerIndex;

	/** Stores map scene sprites values. */
	public IndexedImage mapSceneSprites[];
	/** Whether inventory drag moved is currently active or requested. */
	public boolean inventoryDragMoved;
	/** The graphics or protocol buffer used for sidebar buffer. */
	public GraphicsBuffer sidebarBuffer;
	/** The graphics or protocol buffer used for minimap buffer. */
	public GraphicsBuffer minimapBuffer;
	/** The graphics or protocol buffer used for viewport buffer. */
	public GraphicsBuffer viewportBuffer;
	/** The graphics or protocol buffer used for chatbox buffer. */
	public GraphicsBuffer chatboxBuffer;
	/**
	 * Counts inventory action227 events for the original client timing/protocol
	 * behavior.
	 */
	public static int inventoryAction227Counter;

	/** The client state for account current day. */
	public int accountCurrentDay;



	/** Stores compass mask offsets values. */
	public int compassMaskOffsets[];
	/** Whether sidebar redraw is currently active or requested. */
	public boolean sidebarRedraw;

	/** Stores hitmark sprites values. */
	public ImageRGB hitmarkSprites[];
	/** The client state for sidebar background. */
	public IndexedImage sidebarBackground;
	/** The client state for minimap background. */
	public IndexedImage minimapBackground;
	/** The client state for chatbox background. */
	public IndexedImage chatboxBackground;
	/** The client state for ground item map dot. */
	public ImageRGB groundItemMapDot;
	/** The client state for npc map dot. */
	public ImageRGB npcMapDot;
	/** The client state for player map dot. */
	public ImageRGB playerMapDot;
	/** The client state for friend map dot. */
	public ImageRGB friendMapDot;
	/** The client state for team map dot. */
	public ImageRGB teamMapDot;
	/** The client state for hint icon type. */
	public int hintIconType;
	/** The graphics or protocol buffer used for title top buffer. */
	public GraphicsBuffer titleTopBuffer;
	/** The graphics or protocol buffer used for title bottom buffer. */
	public GraphicsBuffer titleBottomBuffer;
	/** The graphics or protocol buffer used for login box buffer. */
	public GraphicsBuffer loginBoxBuffer;
	/** The graphics or protocol buffer used for title left flame buffer. */
	public GraphicsBuffer titleLeftFlameBuffer;
	/** The graphics or protocol buffer used for title right flame buffer. */
	public GraphicsBuffer titleRightFlameBuffer;
	/** The graphics or protocol buffer used for title left bottom buffer. */
	public GraphicsBuffer titleLeftBottomBuffer;
	/** The graphics or protocol buffer used for title right bottom buffer. */
	public GraphicsBuffer titleRightBottomBuffer;
	/** The graphics or protocol buffer used for title left center buffer. */
	public GraphicsBuffer titleLeftCenterBuffer;
	/** The graphics or protocol buffer used for title right center buffer. */
	public GraphicsBuffer titleRightCenterBuffer;
	/** Whether chat modes redraw is currently active or requested. */
	public boolean chatModesRedraw;

	/** Stores bit masks values. */
	public static int bitMasks[];
	/** The client state for last login day. */
	public int lastLoginDay;
	/** The client state for jaggrab socket. */
	public Socket jaggrabSocket;
	/** The client state for hint npc index. */
	public int hintNpcIndex;
	/**
	 * Counts npc action118 events for the original client timing/protocol behavior.
	 */
	public static int npcAction118Counter;
	/**
	 * Counts screen redraw keepalive events for the original client timing/protocol
	 * behavior.
	 */
	public static int screenRedrawKeepaliveCounter;
	/** Whether interface action pending is currently active or requested. */
	/** Whether chatbox redraw is currently active or requested. */
	public boolean chatboxRedraw;
	/** The client state for last login ip. */
	public int lastLoginIp;

	/** Stores texture scroll scratch values. */
	public byte textureScrollScratch[];
	/** The client state for tutorial island flag. */
	public int tutorialIslandFlag;
	/** The client state for minimap edge arrow. */
	public ImageRGB minimapEdgeArrow;
	/** The client state for mouse recorder. */
	public MouseRecorder mouseRecorder;
	/** The client state for chatbox scroll widget. */
	public Widget chatboxScrollWidget;
	/** The client state for camera packet cooldown. */
	public int cameraPacketCooldown;
	/** Whether camera orientation changed is currently active or requested. */
	public boolean cameraOrientationChanged;

	/** The current number of unread message entries. */
	public int unreadMessageCount;
	/** Whether window focus reported is currently active or requested. */
	public boolean windowFocusReported;
	/** The client state for last minimap plane. */
	public int lastMinimapPlane;
	/** The current sidebar hovered widget id. */
	/** Whether loading error is currently active or requested. */
	public boolean loadingError;
	/** The current chatbox tooltip widget id. */

	/** Stores compass mask widths values. */
	public int compassMaskWidths[];
	/** The client state for scrollbar shadow color. */
	public int scrollbarShadowColor;

	/** Stores skull icon sprites values. */
	public ImageRGB skullIconSprites[];

	/** Stores animated texture IDs values. */
	public int animatedTextureIds[] = { 17, 24, 34, 40 };
	/** The client state for on demand fetcher. */
	public OnDemandFetcher onDemandFetcher;
	/** The client state for title box image. */
	public IndexedImage titleBoxImage;
	/** The client state for title button image. */
	public IndexedImage titleButtonImage;
	/** The current number of mouse telemetry repeat entries. */
	public int mouseTelemetryRepeatCount;
	/** The client state for one button mouse mode. */
	public int oneButtonMouseMode;
	/** The current viewport hovered widget id. */
	/** The client state for scrollbar drag padding. */
	/** Tracks the current draw cycle in client ticks/cycles where applicable. */
	public static int drawCycle;




	/** The current current tooltip widget id. */

	/** The RSA public exponent used by the revision-377 login handshake. */
	public static BigInteger RSA_EXPONENT = new BigInteger(
			"58778699976184461502525193738213253649000149147835990136706041084440742975821");

	/** The client state for multi combat zone. */
	public int multiCombatZone;
	/** The client state for loading percent. */
	public int loadingPercent;
	/** The client state for run energy. */
	public int runEnergy;
	/** Tracks the current game cycle in client ticks/cycles where applicable. */
	public static int gameCycle;

	/**
	 * Tracks the current inventory click cycle in client ticks/cycles where
	 * applicable.
	 */
	public int inventoryClickCycle;
	static {
		experienceTable = new int[99];
		int accumulatedExperience = 0;
		for (int levelIndex = 0; levelIndex < 99; levelIndex++) {
			int level = levelIndex + 1;
			int experienceDelta = (int) ((double) level + 300D * Math.pow(2D, (double) level / 7D));
			accumulatedExperience += experienceDelta;
			experienceTable[levelIndex] = accumulatedExperience / 4;
		}

		bitMasks = new int[32];
		accumulatedExperience = 2;
		for (int bitIndex = 0; bitIndex < 32; bitIndex++) {
			bitMasks[bitIndex] = accumulatedExperience - 1;
			accumulatedExperience += accumulatedExperience;
		}

	}
}
