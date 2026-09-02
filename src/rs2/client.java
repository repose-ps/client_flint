package rs2;

import java.awt.Color;
import java.awt.Component;
import java.awt.Font;
import java.awt.Graphics;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.Socket;

import rs2.action.ActionPacketEncoder;
import rs2.action.ClientActionDispatcher;
import rs2.action.GroundItemActionHandler;
import rs2.action.InventoryActionHandler;
import rs2.action.NpcActionHandler;
import rs2.action.ObjectActionHandler;
import rs2.action.PlayerActionHandler;
import rs2.action.SocialActionHandler;
import rs2.action.WalkActionHandler;
import rs2.action.WidgetActionHandler;
import rs2.cache.Archive;
import rs2.cache.cfg.BitMasks;
import rs2.cache.cfg.Varp;
import rs2.cache.def.GameObjectDefinition;
import rs2.cache.def.ItemDefinition;
import rs2.cache.def.NpcDefinition;
import rs2.cache.ondemand.OnDemandFetcher;
import rs2.cache.ondemand.OnDemandRequest;
import rs2.chat.Censor;
import rs2.chat.ChatCodec;
import rs2.chat.ChatController;
import rs2.chat.ChatMessageType;
import rs2.chat.ChatMode;
import rs2.chat.SocialManager;
import rs2.game.ActorSynchronizer;
import rs2.game.ActorUpdater;
import rs2.game.CameraController;
import rs2.game.MinimapRenderer;
import rs2.game.Pathfinder;
import rs2.game.RegionManager;
import rs2.game.SceneEntityRenderer;
import rs2.game.Skills;
import rs2.game.VarpState;
import rs2.game.WorldState;
import rs2.game.ZoneUpdateHandler;
import rs2.game.entity.Actor;
import rs2.game.entity.Npc;
import rs2.game.entity.Player;
import rs2.game.render.ActorOverlayRenderer;
import rs2.game.render.GameRenderer;
import rs2.input.MouseRecorder;
import rs2.media.GraphicsBuffer;
import rs2.media.Rasterizer;
import rs2.media.Rasterizer3D;
import rs2.media.TypeFace;
import rs2.media.animation.AnimationFrame;
import rs2.media.model.Model;
import rs2.media.sprite.ImageRGB;
import rs2.media.sprite.IndexedImage;
import rs2.media.sprite.ItemSpriteFactory;
import rs2.net.Buffer;
import rs2.net.BufferedConnection;
import rs2.net.ChatPacketEncoder;
import rs2.net.IncomingPacketDispatcher;
import rs2.net.LoginSession;
import rs2.net.MovementPacketEncoder;
import rs2.net.NetworkSession;
import rs2.net.OutgoingPacketOpcode;
import rs2.scene.Region;
import rs2.scene.Scene;
import rs2.scene.SceneConstants;
import rs2.scene.entity.DynamicObjectFactory;
import rs2.shell.GameShell;
import rs2.sign.Signlink;
import rs2.sound.MusicController;
import rs2.sound.SoundEffectQueue;
import rs2.text.Base37;
import rs2.text.TextFormatter;
import rs2.ui.AppearanceEditor;
import rs2.ui.ClientLayout;
import rs2.ui.ClientScriptContext;
import rs2.ui.InterfaceController;
import rs2.ui.Widget;
import rs2.ui.WidgetContentController;
import rs2.ui.WidgetContentType;
import rs2.ui.WidgetRenderer;
import rs2.ui.WidgetRuntime;
import rs2.ui.login.LoginScreen;
import rs2.ui.login.TitleFlameAnimator;
import rs2.ui.menu.MenuController;

/**
 * Standalone revision-377 game client coordinator.
 *
 * <p>
 * The class retains the original client protocol, rendering, timing, UI, and
 * gameplay behavior while delegating well-bounded subsystems to the semantic
 * owners extracted during earlier refactor steps.
 * </p>
 */
public class client extends GameShell {

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
	/**
	 * Bias that converts compact signed mouse deltas to six-bit unsigned values.
	 */
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
				interfaceController.state().reportAbuseInterfaceId = interfaceController
						.state().openInterfaceId = Widget.reportAbuseInterfaceId;
			} else {
				addChatMessage("", "Please close the interface you have open before using 'report abuse'",
						ChatMessageType.GAME);
			}
			return;
		}

		if (!changed)
			return;

		gameRenderer.requestChatModesRedraw();
		gameRenderer.requestChatboxRedraw();
		ChatPacketEncoder.writeChatModes(networkSession.outgoing, chatController.publicMode(),
				chatController.privateMode(), chatController.tradeMode());
	}

	/** Closes all open interface groups through {@link InterfaceController}. */
	private void closeInterfaces() {
		networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.CLOSE_INTERFACES);
		interfaceController.closeAll(interfaceRedrawSink);
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
			client client1 = new client();
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
		lifecycle.shutdown();
	}

	/**
	 * Processes clicks on the current fixed or resizable sidebar-tab hit regions.
	 */
	public void processTabClick() {
		if (super.clickButton != 1)
			return;

		for (int tab = 0; tab < ClientLayout.TAB_COUNT; tab++) {
			if (!layout.isTabHit(tab, super.clickX, super.clickY)
					|| interfaceController.state().tabInterfaceIds[tab] == -1)
				continue;
			gameRenderer.requestSidebarRedraw();
			interfaceController.state().selectedTab = tab;
			gameRenderer.requestTabAreaRedraw();
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

	/**
	 * Delegates social-list menu construction to {@link MenuController}.
	 * 
	 * @param widget social-list widget
	 * @return whether the widget supplied a social menu
	 */
	public boolean buildSocialWidgetMenu(Widget widget) {
		return menuController.buildSocialWidgetMenu(widget);
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
							if (mouseTelemetryRepeatCount < MOUSE_COMPACT_REPEAT_LIMIT
									&& deltaX >= MOUSE_COMPACT_DELTA_MIN && deltaX <= MOUSE_COMPACT_DELTA_MAX
									&& deltaY >= MOUSE_COMPACT_DELTA_MIN && deltaY <= MOUSE_COMPACT_DELTA_MAX) {
								deltaX += MOUSE_COMPACT_DELTA_BIAS;
								deltaY += MOUSE_COMPACT_DELTA_BIAS;
								networkSession.outgoing
										.writeShort((mouseTelemetryRepeatCount << MOUSE_SHORT_REPEAT_SHIFT)
												+ (deltaX << MOUSE_SHORT_X_SHIFT) + deltaY);
								mouseTelemetryRepeatCount = 0;
							} else if (mouseTelemetryRepeatCount < MOUSE_COMPACT_REPEAT_LIMIT) {
								networkSession.outgoing.writeMedium(MOUSE_MEDIUM_FLAG
										+ (mouseTelemetryRepeatCount << MOUSE_ABSOLUTE_REPEAT_SHIFT) + packedPosition);
								mouseTelemetryRepeatCount = 0;
							} else {
								networkSession.outgoing.writeInt(MOUSE_INT_FLAG
										+ (mouseTelemetryRepeatCount << MOUSE_ABSOLUTE_REPEAT_SHIFT) + packedPosition);
								mouseTelemetryRepeatCount = 0;
							}
						}
					}

					networkSession.outgoing.writeLengthByte(networkSession.outgoing.position - packetStart);
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
			networkSession.outgoing.writeInt((encodedClickDelay << CLICK_DELAY_SHIFT)
					+ (clickButton << CLICK_BUTTON_SHIFT) + packedClickPosition);
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
		musicController.updateResumeDelay(lowMemory, lifecycle.onDemandFetcher()::request);
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
					gameRenderer.requestSidebarRedraw();
				if (interfaceController.state().pressedInventoryArea == 3)
					gameRenderer.requestChatboxRedraw();
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
					gameRenderer.requestSidebarRedraw();
				if (interfaceController.state().inventoryDragArea == 3)
					gameRenderer.requestChatboxRedraw();
				interfaceController.state().inventoryDragArea = 0;
				if (inventoryDragMoved && interfaceController.state().inventoryDragDuration >= 5) {
					interfaceController.state().hoveredInventoryWidgetId = -1;
					buildContextMenu();
					if (interfaceController.state().hoveredInventoryWidgetId == interfaceController
							.state().draggedInventoryWidgetId
							&& interfaceController.state().hoveredInventorySlot != interfaceController
									.state().draggedInventorySlot) {
						Widget inventoryWidget = Widget.get(interfaceController.state().draggedInventoryWidgetId);
						// Legacy drag mode: 0 swaps/moves directly; 1 performs insertion-style
						// shifting.
						int insertionMode = 0;
						if (inventoryRearrangeMode == 1
								&& inventoryWidget.contentType == WidgetContentType.INSERTABLE_INVENTORY)
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
							for (int targetSlot = interfaceController
									.state().hoveredInventorySlot; movingSlot != targetSlot;)
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
						networkSession.outgoing.writeShortLEAdd(interfaceController.state().hoveredInventorySlot);
						networkSession.outgoing.writeByteAdd(insertionMode);
						networkSession.outgoing.writeShortAdd(interfaceController.state().draggedInventoryWidgetId);
						networkSession.outgoing.writeShortLE(interfaceController.state().draggedInventorySlot);
					}
				} else if ((oneButtonMouseMode == 1
						|| menuController.state().isAddFriendAction(menuController.state().count - 1))
						&& menuController.state().count > 2)
					openContextMenu();
				else if (menuController.state().count > 0)
					actionDispatcher.dispatch(menuController.state().count - 1);
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
			gameRenderer.requestChatboxRedraw();
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
		if (interfaceController.chatboxTooltipWidgetId() != 0 || interfaceController.sidebarTooltipWidgetId() != 0
				|| interfaceController.viewportTooltipWidgetId() != 0) {
			if (interfaceController.tooltipHoverTicks() < 100) {
				interfaceController.setTooltipHoverTicks(interfaceController.tooltipHoverTicks() + 1);
				if (interfaceController.tooltipHoverTicks() == 100) {
					if (interfaceController.chatboxTooltipWidgetId() != 0)
						gameRenderer.requestChatboxRedraw();
					if (interfaceController.sidebarTooltipWidgetId() != 0)
						gameRenderer.requestSidebarRedraw();
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
			if (interfaceController.state().openInterfaceId != -1 && interfaceController
					.state().openInterfaceId == interfaceController.state().reportAbuseInterfaceId) {
				if (keyCode == 8 && interfaceController.reportAbuseName().length() > 0)
					interfaceController.setReportAbuseName(interfaceController.reportAbuseName().substring(0,
							interfaceController.reportAbuseName().length() - 1));
				if ((keyCode >= 97 && keyCode <= 122 || keyCode >= 65 && keyCode <= 90 || keyCode >= 48 && keyCode <= 57
						|| keyCode == 32) && interfaceController.reportAbuseName().length() < 12)
					interfaceController.setReportAbuseName(interfaceController.reportAbuseName() + (char) keyCode);
			} else if (chatController.isPromptRaised()) {
				if (keyCode >= 32 && keyCode <= 122 && chatController.promptInput().length() < 80) {
					chatController.setPromptInput(chatController.promptInput() + (char) keyCode);
					gameRenderer.requestChatboxRedraw();
				}
				if (keyCode == 8 && chatController.promptInput().length() > 0) {
					chatController.setPromptInput(
							chatController.promptInput().substring(0, chatController.promptInput().length() - 1));
					gameRenderer.requestChatboxRedraw();
				}
				if (keyCode == 13 || keyCode == 10) {
					chatController.closePrompt();
					gameRenderer.requestChatboxRedraw();
					if (chatController.promptAction() == 1) {
						long encodedName = Base37.encode(chatController.promptInput());
						addFriend(encodedName);
					}
					if (chatController.promptAction() == 2 && socialManager.friendCount > 0) {
						long encodedName2 = Base37.encode(chatController.promptInput());
						removeFriend(encodedName2);
					}
					if (chatController.promptAction() == 3 && chatController.promptInput().length() > 0) {
						ChatPacketEncoder.writePrivateMessage(networkSession.outgoing,
								chatController.privateMessageTarget(), chatController.promptInput());
						chatController.setPromptInput(ChatCodec.normalize(chatController.promptInput()));
						chatController.setPromptInput(Censor.censor(chatController.promptInput()));
						addChatMessage(
								TextFormatter.formatDisplayName(Base37.decode(chatController.privateMessageTarget())),
								chatController.promptInput(), 6);
						if (chatController.privateMode() == ChatMode.OFF) {
							chatController.setPrivateMode(ChatMode.FRIENDS);
							gameRenderer.requestChatModesRedraw();
							ChatPacketEncoder.writeChatModes(networkSession.outgoing, chatController.publicMode(),
									chatController.privateMode(), chatController.tradeMode());
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
					gameRenderer.requestChatboxRedraw();
				}
				if (keyCode == 8 && chatController.inputDialogText().length() > 0) {
					chatController.setInputDialogText(chatController.inputDialogText().substring(0,
							chatController.inputDialogText().length() - 1));
					gameRenderer.requestChatboxRedraw();
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
					gameRenderer.requestChatboxRedraw();
				}
			} else if (chatController.inputDialogState() == 2) {
				if (keyCode >= 32 && keyCode <= 122 && chatController.inputDialogText().length() < 12) {
					chatController.setInputDialogText(chatController.inputDialogText() + (char) keyCode);
					gameRenderer.requestChatboxRedraw();
				}
				if (keyCode == 8 && chatController.inputDialogText().length() > 0) {
					chatController.setInputDialogText(chatController.inputDialogText().substring(0,
							chatController.inputDialogText().length() - 1));
					gameRenderer.requestChatboxRedraw();
				}
				if (keyCode == 13 || keyCode == 10) {
					if (chatController.inputDialogText().length() > 0) {
						networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.INPUT_NAME);
						networkSession.outgoing.writeLong(Base37.encode(chatController.inputDialogText()));
					}
					chatController.setInputDialogState(0);
					gameRenderer.requestChatboxRedraw();
				}
			} else if (chatController.inputDialogState() == 3) {
				if (keyCode >= 32 && keyCode <= 122 && chatController.inputDialogText().length() < 40) {
					chatController.setInputDialogText(chatController.inputDialogText() + (char) keyCode);
					gameRenderer.requestChatboxRedraw();
				}
				if (keyCode == 8 && chatController.inputDialogText().length() > 0) {
					chatController.setInputDialogText(chatController.inputDialogText().substring(0,
							chatController.inputDialogText().length() - 1));
					gameRenderer.requestChatboxRedraw();
				}
			} else if (interfaceController.state().chatboxInterfaceId == -1
					&& interfaceController.state().fullscreenInterfaceId == -1) {
				if (keyCode >= 32 && keyCode <= 122 && chatController.input().length() < 80) {
					chatController.setInput(chatController.input() + (char) keyCode);
					gameRenderer.requestChatboxRedraw();
				}
				if (keyCode == 8 && chatController.input().length() > 0) {
					chatController.setInput(chatController.input().substring(0, chatController.input().length() - 1));
					gameRenderer.requestChatboxRedraw();
				}
				if ((keyCode == 13 || keyCode == 10) && chatController.input().length() > 0) {
					if (playerRights == 2) {
						if (chatController.input().equals("::clientdrop"))
							reconnect();
						if (chatController.input().equals("::lag"))
							printDebugInfo();
						if (chatController.input().equals("::prefetchmusic")) {
							for (int midiId = 0; midiId < lifecycle.onDemandFetcher().getFileCount(2); midiId++)
								lifecycle.onDemandFetcher().setExtraPriority(2, midiId, (byte) 1);

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
						ChatPacketEncoder.writePublicMessage(networkSession.outgoing, chatColor, chatEffect,
								chatController.input(), chatBuffer);
						chatController.setInput(ChatCodec.normalize(chatController.input()));
						chatController.setInput(Censor.censor(chatController.input()));
						localPlayer.overheadText = chatController.input();
						localPlayer.overheadTextColor = chatColor;
						localPlayer.overheadTextEffect = chatEffect;
						localPlayer.overheadTextCyclesRemaining = 150;
						if (playerRights == 2)
							addChatMessage("@cr2@" + localPlayer.name, ((Actor) (localPlayer)).overheadText,
									ChatMessageType.PUBLIC);
						else if (playerRights == 1)
							addChatMessage("@cr1@" + localPlayer.name, ((Actor) (localPlayer)).overheadText,
									ChatMessageType.PUBLIC);
						else
							addChatMessage(localPlayer.name, ((Actor) (localPlayer)).overheadText,
									ChatMessageType.PUBLIC);
						if (chatController.publicMode() == ChatMode.OFF) {
							chatController.setPublicMode(ChatMode.HIDE);
							gameRenderer.requestChatModesRedraw();
							ChatPacketEncoder.writeChatModes(networkSession.outgoing, chatController.publicMode(),
									chatController.privateMode(), chatController.tradeMode());
						}
					}
					chatController.setInput("");
					gameRenderer.requestChatboxRedraw();
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
		if (menuController.state().count < 2 && interfaceController.state().itemSelected == 0
				&& interfaceController.state().spellSelected == 0)
			return;
		String tooltip;
		if (interfaceController.state().itemSelected == 1 && menuController.state().count < 2)
			tooltip = "Use " + interfaceController.state().selectedItemName + " with...";
		else if (interfaceController.state().spellSelected == 1 && menuController.state().count < 2)
			tooltip = interfaceController.state().selectedSpellAction + "...";
		else
			tooltip = menuController.state().entry(menuController.state().count - 1).text();
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
	 * Configures the hostname used by all standalone game/update/archive sockets.
	 * 
	 * @param host the host name
	 */
	public static void setServerHost(String host) {
		if (host == null || host.trim().isEmpty()) {
			throw new IllegalArgumentException("server host must not be blank");
		}
		serverHost = host.trim();
	}

	/**
	 * Delegates player menu construction to {@link MenuController}.
	 * 
	 * @param playerIndex player index
	 * @param tileY       local tile Y
	 * @param tileX       local tile X
	 * @param player      target player
	 */
	public void buildPlayerMenu(int playerIndex, int tileY, int tileX, Player player) {
		menuController.buildPlayerMenu(playerIndex, tileY, tileX, player, localPlayer, playerActions,
				playerActionLowPriority);
	}

	/**
	 * Delegates classic scrollbar input to the interface interaction owner.
	 *
	 * @param scrollHeight full content height
	 * @param y            scrollbar Y coordinate
	 * @param widget       scrollable widget
	 * @param mouseY       mouse Y coordinate
	 * @param redrawArea   fixed redraw area
	 * @param mouseX       mouse X coordinate
	 * @param height       visible height
	 * @param x            scrollbar X coordinate
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
	void unloadInterface(int interfaceId) {
		interfaceController.unload(interfaceId);
	}

	/**
	 * Adds a message to the fixed chat history and requests the appropriate redraw.
	 *
	 * @param sender  the message sender name
	 * @param message the message text
	 * @param type    the chat message type
	 */
	void addChatMessage(String sender, String message, int type) {
		if (type == 0 && interfaceController.state().dialogueInterfaceId != -1) {
			chatController.setClickToContinueMessage(message);
			super.clickButton = 0;
		}
		if (interfaceController.state().chatboxInterfaceId == -1)
			gameRenderer.requestChatboxRedraw();
		chatController.history().add(sender, message, type);
	}

	/**
	 * Clears definition, model, item-sprite, animation-frame, and scene caches.
	 */
	public void clearCaches() {
		lifecycle.clearRuntimeCaches();
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
	private void removeFriend(long encodedName) {
		if (socialManager.removeFriend(encodedName, networkSession.outgoing))
			gameRenderer.requestSidebarRedraw();
	}

	/** Processes context-menu mouse input through {@link MenuController}. */
	public void processMenuClick() {
		menuController.processClick(super.clickButton, super.clickX, super.clickY, super.mouseX, super.mouseY,
				oneButtonMouseMode, boldFont, actionDispatcher::dispatch, () -> inventoryDragMoved = false);
	}

	/**
	 * Draws a classic widget scrollbar through {@link WidgetRenderer}.
	 *
	 * @param scrollY      current scroll offset
	 * @param x            X coordinate
	 * @param height       visible height
	 * @param scrollHeight full scrollable height
	 * @param y            Y coordinate
	 */
	public void drawScrollbar(int scrollY, int x, int height, int scrollHeight, int y) {
		widgetRenderer.drawScrollbar(new WidgetRenderer.RenderContext(super.mouseX, super.mouseY, animationCycleDelta,
				smallFont, plainFont, scrollbarTop, scrollbarBottom, scrollbarTrackColor, scrollbarThumbColor,
				scrollbarHighlightColor, scrollbarShadowColor), scrollY, x, height, scrollHeight, y);
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
	 * Allocates and initializes the fixed title-screen graphics buffers.
	 */
	public void createTitleScreenBuffers() {
		if (titleTopBuffer != null)
			return;
		super.gameBuffer = null;
		gameRenderer.clearGameScreenBuffers();

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
		gameRenderer.requestGameScreenRedraw();
	}

	/**
	 * Loads startup archives and resources and initializes all major client
	 * subsystems.
	 */
	public void startUp() {
		lifecycle.startUp();
	}

	/**
	 * Animates any scrolling textures used during the current scene frame.
	 * 
	 * @param textureCycle rasterizer texture-usage cycle threshold
	 */
	public void animateTextures(int textureCycle) {
		gameRenderer.animateTextures(textureCycle, animationCycleDelta, lowMemory);
	}

	/**
	 * Delegates widget/inventory menu construction to {@link MenuController}.
	 * 
	 * @param y          root Y
	 * @param widget     root widget
	 * @param screenArea fixed UI area
	 * @param scrollY    scroll offset
	 * @param x          root X
	 * @param mouseX     mouse X
	 * @param mouseY     mouse Y
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
					widgetRuntime.updateAnimations(animationCycleDelta,
							interfaceController.state().fullscreenOverlayInterfaceId);
				animationCycleDelta = 0;
				createGameBuffer();
				super.gameBuffer.bindRaster();
				gameRenderer.bindFullScreenScanlines();
				Rasterizer.resetPixels();
				gameRenderer.requestGameScreenRedraw();
				Widget fullscreenWidget = Widget.get(interfaceController.state().fullscreenInterfaceId);
				if (fullscreenWidget.width == ClientLayout.FIXED_VIEWPORT_WIDTH
						&& fullscreenWidget.height == ClientLayout.FIXED_VIEWPORT_HEIGHT
						&& fullscreenWidget.type == Widget.TYPE_CONTAINER) {
					fullscreenWidget.width = ClientLayout.FIXED_WIDTH;
					fullscreenWidget.height = ClientLayout.FIXED_HEIGHT;
				}
				drawInterface(0, 0, fullscreenWidget, 0);
				if (interfaceController.state().fullscreenOverlayInterfaceId != -1) {
					Widget fullscreenOverlayWidget = Widget
							.get(interfaceController.state().fullscreenOverlayInterfaceId);
					if (fullscreenOverlayWidget.width == ClientLayout.FIXED_VIEWPORT_WIDTH
							&& fullscreenOverlayWidget.height == ClientLayout.FIXED_VIEWPORT_HEIGHT
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
		gameRenderer.requestSidebarRedraw();
		gameRenderer.requestChatboxRedraw();
		gameRenderer.requestTabAreaRedraw();
		gameRenderer.requestChatModesRedraw();

		if (regionManager.loadingStage != RegionManager.STAGE_LOADED)
			gameRenderer.viewportBuffer().draw(super.graphics, layout.viewportX(), layout.viewportY());

		/*
		 * Keep the legacy redraw-triggered packet cadence separate from the new
		 * unconditional presentation. Forcing gameScreenRedraw true every frame would
		 * otherwise emit opcode 168 far more often than the original client.
		 */
		if (gameRenderer.consumeGameScreenRedraw()) {
			screenRedrawKeepaliveCounter++;
			if (screenRedrawKeepaliveCounter > 85) {
				screenRedrawKeepaliveCounter = 0;
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.SCREEN_REDRAW_KEEPALIVE);
			}
		}
		if (regionManager.loadingStage == RegionManager.STAGE_LOADED)
			renderGameScene();

		/*
		 * The game viewport is the background layer in resizable mode. Draw the classic
		 * frame pieces after it so their stone borders remain visible, then composite
		 * the fixed-size UI panels on top below.
		 */
		gameRenderer.drawFrameDecorations(super.graphics, layout);
		if (regionManager.loadingStage != RegionManager.STAGE_LOADED)
			gameRenderer.minimapBuffer().draw(super.graphics, layout.minimapX(), layout.minimapY());

		if (menuController.state().open && menuController.state().screenArea == 1)
			gameRenderer.requestSidebarRedraw();
		if (interfaceController.state().sidebarOverlayInterfaceId != -1) {
			boolean sidebarAnimationChanged = widgetRuntime.updateAnimations(animationCycleDelta,
					interfaceController.state().sidebarOverlayInterfaceId);
			if (sidebarAnimationChanged)
				gameRenderer.requestSidebarRedraw();
		}
		if (interfaceController.state().pressedInventoryArea == 2)
			gameRenderer.requestSidebarRedraw();
		if (interfaceController.state().inventoryDragArea == 2)
			gameRenderer.requestSidebarRedraw();
		if (gameRenderer.sidebarRedrawPending()) {
			drawSidebar();
			gameRenderer.clearSidebarRedraw();
		}
		if (interfaceController.state().chatboxInterfaceId == -1 && chatController.inputDialogState() == 0) {
			chatboxScrollWidget.scrollY = chatController.contentHeight() - chatController.scrollOffset()
					- ClientLayout.CHATBOX_MESSAGE_HEIGHT;
			if (layout.isChatboxScrollbarInputCandidate(super.mouseX, super.mouseY))
				handleScrollbarInput(chatController.contentHeight(), 0, chatboxScrollWidget,
						layout.chatboxLocalY(super.mouseY), -1, layout.chatboxLocalX(super.mouseX),
						ClientLayout.CHATBOX_MESSAGE_HEIGHT, ClientLayout.CHATBOX_SCROLLBAR_X);
			int chatScrollOffsetFromBottom = chatController.contentHeight() - ClientLayout.CHATBOX_MESSAGE_HEIGHT
					- chatboxScrollWidget.scrollY;
			if (chatScrollOffsetFromBottom < 0)
				chatScrollOffsetFromBottom = 0;
			if (chatScrollOffsetFromBottom > chatController.contentHeight() - ClientLayout.CHATBOX_MESSAGE_HEIGHT)
				chatScrollOffsetFromBottom = chatController.contentHeight() - ClientLayout.CHATBOX_MESSAGE_HEIGHT;
			if (chatController.scrollOffset() != chatScrollOffsetFromBottom) {
				chatController.setScrollOffset(chatScrollOffsetFromBottom);
				gameRenderer.requestChatboxRedraw();
			}
		}
		if (interfaceController.state().chatboxInterfaceId == -1 && chatController.inputDialogState() == 3) {
			int searchContentHeight = itemSearchResultCount * ClientLayout.CHATBOX_MESSAGE_LINE_HEIGHT
					+ ClientLayout.CHATBOX_CONTENT_HEIGHT_PADDING;
			chatboxScrollWidget.scrollY = itemSearchScrollOffset;
			if (layout.isChatboxScrollbarInputCandidate(super.mouseX, super.mouseY))
				handleScrollbarInput(searchContentHeight, 0, chatboxScrollWidget, layout.chatboxLocalY(super.mouseY),
						-1, layout.chatboxLocalX(super.mouseX), ClientLayout.CHATBOX_MESSAGE_HEIGHT,
						ClientLayout.CHATBOX_SCROLLBAR_X);
			int clampedSearchScroll = chatboxScrollWidget.scrollY;
			if (clampedSearchScroll < 0)
				clampedSearchScroll = 0;
			if (clampedSearchScroll > searchContentHeight - ClientLayout.CHATBOX_MESSAGE_HEIGHT)
				clampedSearchScroll = searchContentHeight - ClientLayout.CHATBOX_MESSAGE_HEIGHT;
			if (itemSearchScrollOffset != clampedSearchScroll) {
				itemSearchScrollOffset = clampedSearchScroll;
				gameRenderer.requestChatboxRedraw();
			}
		}
		if (interfaceController.state().chatboxInterfaceId != -1) {
			boolean chatboxAnimationChanged = widgetRuntime.updateAnimations(animationCycleDelta,
					interfaceController.state().chatboxInterfaceId);
			if (chatboxAnimationChanged)
				gameRenderer.requestChatboxRedraw();
		}
		if (interfaceController.state().pressedInventoryArea == 3)
			gameRenderer.requestChatboxRedraw();
		if (interfaceController.state().inventoryDragArea == 3)
			gameRenderer.requestChatboxRedraw();
		if (chatController.clickToContinueMessage() != null)
			gameRenderer.requestChatboxRedraw();
		if (menuController.state().open && menuController.state().screenArea == 2)
			gameRenderer.requestChatboxRedraw();
		if (gameRenderer.chatboxRedrawPending()) {
			drawChatbox();
			gameRenderer.clearChatboxRedraw();
		}
		if (regionManager.loadingStage == RegionManager.STAGE_LOADED) {
			drawMinimap();
			gameRenderer.minimapBuffer().draw(super.graphics, layout.minimapX(), layout.minimapY());
		}
		if (interfaceController.state().flashingTab != -1)
			gameRenderer.requestTabAreaRedraw();
		if (gameRenderer.tabAreaRedrawPending()) {
			if (interfaceController.state().flashingTab != -1
					&& interfaceController.state().flashingTab == interfaceController.state().selectedTab) {
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.FLASHING_TAB_ACKNOWLEDGEMENT);
				networkSession.outgoing.writeByte(interfaceController.state().selectedTab);
			}
			gameRenderer.clearTabAreaRedraw();
			if (!layout.isResizableMode()) {
				gameRenderer.topTabsBuffer().bindRaster();
				topTabBackground.draw(0, 0);
			} else {
				gameRenderer.beginResizableTabsFrame();
			}
			if (interfaceController.state().sidebarOverlayInterfaceId == -1) {
				if (interfaceController.state().tabInterfaceIds[interfaceController.state().selectedTab] != -1) {
					if (interfaceController.state().selectedTab == 0)
						drawTabHighlight(redstone1, 0);
					if (interfaceController.state().selectedTab == 1)
						drawTabHighlight(redstone2, 1);
					if (interfaceController.state().selectedTab == 2)
						drawTabHighlight(redstone2, 2);
					if (interfaceController.state().selectedTab == 3)
						drawTabHighlight(redstone3, 3);
					if (interfaceController.state().selectedTab == 4)
						drawTabHighlight(redstone2Horizontal, 4);
					if (interfaceController.state().selectedTab == 5)
						drawTabHighlight(redstone2Horizontal, 5);
					if (interfaceController.state().selectedTab == 6)
						drawTabHighlight(redstone1Horizontal, 6);
				}
				if (interfaceController.state().tabInterfaceIds[0] != -1
						&& (interfaceController.state().flashingTab != 0 || gameCycle % 20 < 10))
					drawTabIcon(sidebarIcons[0], 0);
				if (interfaceController.state().tabInterfaceIds[1] != -1
						&& (interfaceController.state().flashingTab != 1 || gameCycle % 20 < 10))
					drawTabIcon(sidebarIcons[1], 1);
				if (interfaceController.state().tabInterfaceIds[2] != -1
						&& (interfaceController.state().flashingTab != 2 || gameCycle % 20 < 10))
					drawTabIcon(sidebarIcons[2], 2);
				if (interfaceController.state().tabInterfaceIds[3] != -1
						&& (interfaceController.state().flashingTab != 3 || gameCycle % 20 < 10))
					drawTabIcon(sidebarIcons[3], 3);
				if (interfaceController.state().tabInterfaceIds[4] != -1
						&& (interfaceController.state().flashingTab != 4 || gameCycle % 20 < 10))
					drawTabIcon(sidebarIcons[4], 4);
				if (interfaceController.state().tabInterfaceIds[5] != -1
						&& (interfaceController.state().flashingTab != 5 || gameCycle % 20 < 10))
					drawTabIcon(sidebarIcons[5], 5);
				if (interfaceController.state().tabInterfaceIds[6] != -1
						&& (interfaceController.state().flashingTab != 6 || gameCycle % 20 < 10))
					drawTabIcon(sidebarIcons[6], 6);
			}
			if (!layout.isResizableMode()) {
				gameRenderer.topTabsBuffer().draw(super.graphics, layout.topTabsX(), layout.topTabsY());
				gameRenderer.bottomTabsBuffer().bindRaster();
				bottomTabBackground.draw(0, 0);
			}
			if (interfaceController.state().sidebarOverlayInterfaceId == -1) {
				if (interfaceController.state().tabInterfaceIds[interfaceController.state().selectedTab] != -1) {
					if (interfaceController.state().selectedTab == 7)
						drawTabHighlight(redstone1Vertical, 7);
					if (interfaceController.state().selectedTab == 8)
						drawTabHighlight(redstone2Vertical, 8);
					if (interfaceController.state().selectedTab == 9)
						drawTabHighlight(redstone2Vertical, 9);
					if (interfaceController.state().selectedTab == 10)
						drawTabHighlight(redstone3Vertical, 10);
					if (interfaceController.state().selectedTab == 11)
						drawTabHighlight(redstone2Both, 11);
					if (interfaceController.state().selectedTab == 12)
						drawTabHighlight(redstone2Both, 12);
					if (interfaceController.state().selectedTab == 13)
						drawTabHighlight(redstone1Both, 13);
				}
				if (interfaceController.state().tabInterfaceIds[8] != -1
						&& (interfaceController.state().flashingTab != 8 || gameCycle % 20 < 10))
					drawTabIcon(sidebarIcons[7], 8);
				if (interfaceController.state().tabInterfaceIds[9] != -1
						&& (interfaceController.state().flashingTab != 9 || gameCycle % 20 < 10))
					drawTabIcon(sidebarIcons[8], 9);
				if (interfaceController.state().tabInterfaceIds[10] != -1
						&& (interfaceController.state().flashingTab != 10 || gameCycle % 20 < 10))
					drawTabIcon(sidebarIcons[9], 10);
				if (interfaceController.state().tabInterfaceIds[11] != -1
						&& (interfaceController.state().flashingTab != 11 || gameCycle % 20 < 10))
					drawTabIcon(sidebarIcons[10], 11);
				if (interfaceController.state().tabInterfaceIds[12] != -1
						&& (interfaceController.state().flashingTab != 12 || gameCycle % 20 < 10))
					drawTabIcon(sidebarIcons[11], 12);
				if (interfaceController.state().tabInterfaceIds[13] != -1
						&& (interfaceController.state().flashingTab != 13 || gameCycle % 20 < 10))
					drawTabIcon(sidebarIcons[12], 13);
			}
			if (!layout.isResizableMode()) {
				gameRenderer.bottomTabsBuffer().draw(super.graphics, layout.bottomTabsX(), layout.bottomTabsY());
			} else {
				gameRenderer.drawResizableTabsFrame(super.graphics, layout);
			}
			gameRenderer.viewportBuffer().bindRaster();
			gameRenderer.bindViewport();
		}
		if (gameRenderer.chatModesRedrawPending()) {
			gameRenderer.clearChatModesRedraw();
			if (!layout.isResizableMode()) {
				gameRenderer.chatModesBuffer().bindRaster();
				chatModesBackground.draw(0, 0);
			} else {
				gameRenderer.bindResizableChatModes();
			}
			plainFont.drawCenteredTextWithTags("Public chat", chatModeRenderTextCenterX(ClientLayout.CHAT_MODE_PUBLIC),
					layout.chatModeLabelY(ClientLayout.CHAT_MODE_PUBLIC), 0xffffff, true);
			if (chatController.publicMode() == ChatMode.ON)
				plainFont.drawCenteredTextWithTags("On", chatModeRenderTextCenterX(ClientLayout.CHAT_MODE_PUBLIC),
						layout.chatModeStatusY(), 65280, true);
			if (chatController.publicMode() == ChatMode.FRIENDS)
				plainFont.drawCenteredTextWithTags("Friends", chatModeRenderTextCenterX(ClientLayout.CHAT_MODE_PUBLIC),
						layout.chatModeStatusY(), 0xffff00, true);
			if (chatController.publicMode() == ChatMode.OFF)
				plainFont.drawCenteredTextWithTags("Off", chatModeRenderTextCenterX(ClientLayout.CHAT_MODE_PUBLIC),
						layout.chatModeStatusY(), 0xff0000, true);
			if (chatController.publicMode() == ChatMode.HIDE)
				plainFont.drawCenteredTextWithTags("Hide", chatModeRenderTextCenterX(ClientLayout.CHAT_MODE_PUBLIC),
						layout.chatModeStatusY(), 65535, true);
			plainFont.drawCenteredTextWithTags("Private chat",
					chatModeRenderTextCenterX(ClientLayout.CHAT_MODE_PRIVATE),
					layout.chatModeLabelY(ClientLayout.CHAT_MODE_PRIVATE), 0xffffff, true);
			if (chatController.privateMode() == ChatMode.ON)
				plainFont.drawCenteredTextWithTags("On", chatModeRenderTextCenterX(ClientLayout.CHAT_MODE_PRIVATE),
						layout.chatModeStatusY(), 65280, true);
			if (chatController.privateMode() == ChatMode.FRIENDS)
				plainFont.drawCenteredTextWithTags("Friends",
						chatModeRenderTextCenterX(ClientLayout.CHAT_MODE_PRIVATE), layout.chatModeStatusY(), 0xffff00,
						true);
			if (chatController.privateMode() == ChatMode.OFF)
				plainFont.drawCenteredTextWithTags("Off", chatModeRenderTextCenterX(ClientLayout.CHAT_MODE_PRIVATE),
						layout.chatModeStatusY(), 0xff0000, true);
			plainFont.drawCenteredTextWithTags("Trade/compete",
					chatModeRenderTextCenterX(ClientLayout.CHAT_MODE_TRADE),
					layout.chatModeLabelY(ClientLayout.CHAT_MODE_TRADE), 0xffffff, true);
			if (chatController.tradeMode() == ChatMode.ON)
				plainFont.drawCenteredTextWithTags("On", chatModeRenderTextCenterX(ClientLayout.CHAT_MODE_TRADE),
						layout.chatModeStatusY(), 65280, true);
			if (chatController.tradeMode() == ChatMode.FRIENDS)
				plainFont.drawCenteredTextWithTags("Friends", chatModeRenderTextCenterX(ClientLayout.CHAT_MODE_TRADE),
						layout.chatModeStatusY(), 0xffff00, true);
			if (chatController.tradeMode() == ChatMode.OFF)
				plainFont.drawCenteredTextWithTags("Off", chatModeRenderTextCenterX(ClientLayout.CHAT_MODE_TRADE),
						layout.chatModeStatusY(), 0xff0000, true);
			plainFont.drawCenteredTextWithTags("Report abuse",
					chatModeRenderTextCenterX(ClientLayout.CHAT_MODE_REPORT_ABUSE),
					layout.chatModeLabelY(ClientLayout.CHAT_MODE_REPORT_ABUSE), 0xffffff, true);
			if (!layout.isResizableMode()) {
				gameRenderer.chatModesBuffer().draw(super.graphics, layout.chatModesX(), layout.chatModesY());
			} else {
				gameRenderer.drawResizableChatModes(super.graphics, layout);
			}
			gameRenderer.viewportBuffer().bindRaster();
			gameRenderer.bindViewport();
		}
		animationCycleDelta = 0;
	}

	/** Draws a selected-tab highlight into the active fixed or resizable strip. */
	private void drawTabHighlight(IndexedImage highlight, int tab) {
		int x = layout.tabHighlightX(tab);
		int y = layout.tabHighlightY(tab);
		if (!layout.isResizableMode()) {
			highlight.draw(x, y);
			return;
		}
		gameRenderer.drawResizableTabSprite(highlight, tab, x, y);
	}

	/** Draws one sidebar icon into the active fixed or resizable tab strip. */
	private void drawTabIcon(IndexedImage icon, int tab) {
		int x = layout.tabIconX(tab);
		int y = layout.tabIconY(tab);
		if (!layout.isResizableMode()) {
			icon.draw(x, y);
			return;
		}
		gameRenderer.drawResizableTabSprite(icon, tab, x, y);
	}

	/** Returns the chat-mode text center in whichever raster is currently bound. */
	private int chatModeRenderTextCenterX(int button) {
		return layout.isResizableMode() ? layout.resizableChatModeTextCenterX(button)
				: layout.chatModeTextCenterX(button);
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
				if ((messageType == ChatMessageType.PRIVATE_RECEIVED
						|| messageType == ChatMessageType.PRIVATE_RECEIVED_PRIVILEGED)
						&& (messageType == ChatMessageType.PRIVATE_RECEIVED_PRIVILEGED
								|| chatController.privateMode() == ChatMode.ON
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
					font.drawText(sender + ": " + chatController.history().messages[messageIndex], textX, lineY - 1,
							65535);
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
					font.drawText("To " + sender + ": " + chatController.history().messages[messageIndex], 4, lineY3,
							0);
					font.drawText("To " + sender + ": " + chatController.history().messages[messageIndex], 4,
							lineY3 - 1, 65535);
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
				request = lifecycle.onDemandFetcher().poll();
				if (request == null)
					return;
				if (request.type == OnDemandFetcher.MODEL) {
					Model.loadModelHeader(request.buffer, request.id);
					if ((lifecycle.onDemandFetcher().getModelIndex(request.id) & 0x62) != 0) {
						gameRenderer.requestSidebarRedraw();
						if (interfaceController.state().chatboxInterfaceId != -1
								|| interfaceController.state().dialogueInterfaceId != -1)
							gameRenderer.requestChatboxRedraw();
					}
				}
				if (request.type == OnDemandFetcher.ANIMATION && request.buffer != null)
					AnimationFrame.load(request.buffer);
				musicController.acceptOnDemandRequest(request);
				if (request.type == OnDemandFetcher.MAP && regionManager.loadingStage == RegionManager.STAGE_LOADING)
					regionManager.acceptMapFile(request);
			} while (request.type != OnDemandFetcher.LOCATION_PREFETCH
					|| !lifecycle.onDemandFetcher().isLandscapeFile(request.id));
			Region.requestGameObjectModels(new Buffer(request.buffer), lifecycle.onDemandFetcher());
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
	 * @param rights  server-supplied player rights
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

		actionDispatcher.resetForLogin();
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
	 * Delegates NPC menu construction to {@link MenuController}.
	 * 
	 * @param definition NPC definition
	 * @param tileY      local tile Y
	 * @param tileX      local tile X
	 * @param npcIndex   NPC index
	 */
	public void buildNpcMenu(NpcDefinition definition, int tileY, int tileX, int npcIndex) {
		menuController.buildNpcMenu(definition, tileY, tileX, npcIndex, localPlayer);
	}

	/**
	 * Draws the chatbox, prompts, item-search results, dialogue interfaces, and
	 * message history.
	 */
	public void drawChatbox() {
		gameRenderer.bindChatbox();
		chatboxBackground.draw(0, 0);
		if (chatController.isPromptRaised()) {
			boldFont.drawCenteredText(chatController.promptMessage(), ClientLayout.CHATBOX_TEXT_CENTER_X, 40, 0);
			boldFont.drawCenteredText(chatController.promptInput() + "*", ClientLayout.CHATBOX_TEXT_CENTER_X, 60, 128);
		} else if (chatController.inputDialogState() == 1) {
			boldFont.drawCenteredText("Enter amount:", ClientLayout.CHATBOX_TEXT_CENTER_X, 40, 0);
			boldFont.drawCenteredText(chatController.inputDialogText() + "*", ClientLayout.CHATBOX_TEXT_CENTER_X, 60,
					128);
		} else if (chatController.inputDialogState() == 2) {
			boldFont.drawCenteredText("Enter name:", ClientLayout.CHATBOX_TEXT_CENTER_X, 40, 0);
			boldFont.drawCenteredText(chatController.inputDialogText() + "*", ClientLayout.CHATBOX_TEXT_CENTER_X, 60,
					128);
		} else if (chatController.inputDialogState() == 3) {
			if (chatController.inputDialogText() != itemSearchQuery) {
				searchItems(chatController.inputDialogText());
				itemSearchQuery = chatController.inputDialogText();
			}
			TypeFace searchFont = plainFont;
			Rasterizer.setCoordinates(0, 0, ClientLayout.CHATBOX_MESSAGE_CLIP_WIDTH,
					ClientLayout.CHATBOX_MESSAGE_HEIGHT);
			for (int resultIndex = 0; resultIndex < itemSearchResultCount; resultIndex++) {
				int resultY = (18 + resultIndex * 14) - itemSearchScrollOffset;
				if (resultY > 0 && resultY < 110)
					searchFont.drawCenteredText(itemSearchResultNames[resultIndex], ClientLayout.CHATBOX_TEXT_CENTER_X,
							resultY, 0);
			}

			Rasterizer.resetCoordinates();
			if (itemSearchResultCount > 5)
				drawScrollbar(itemSearchScrollOffset, ClientLayout.CHATBOX_SCROLLBAR_X,
						ClientLayout.CHATBOX_MESSAGE_HEIGHT,
						itemSearchResultCount * ClientLayout.CHATBOX_MESSAGE_LINE_HEIGHT
								+ ClientLayout.CHATBOX_CONTENT_HEIGHT_PADDING,
						0);
			if (chatController.inputDialogText().length() == 0)
				boldFont.drawCenteredText("Enter object name", ClientLayout.CHATBOX_TEXT_CENTER_X, 40, 255);
			else if (itemSearchResultCount == 0)
				boldFont.drawCenteredText("No matching objects found, please shorten search",
						ClientLayout.CHATBOX_TEXT_CENTER_X, 40, 0);
			searchFont.drawCenteredText(chatController.inputDialogText() + "*", ClientLayout.CHATBOX_TEXT_CENTER_X,
					ClientLayout.CHATBOX_INPUT_BASELINE_Y, 0);
			Rasterizer.drawHorizontalLine(0, ClientLayout.CHATBOX_MESSAGE_HEIGHT, ClientLayout.CHATBOX_DIVIDER_WIDTH,
					0);
		} else if (chatController.clickToContinueMessage() != null) {
			boldFont.drawCenteredText(chatController.clickToContinueMessage(), ClientLayout.CHATBOX_TEXT_CENTER_X, 40,
					0);
			boldFont.drawCenteredText("Click to continue", ClientLayout.CHATBOX_TEXT_CENTER_X, 60, 128);
		} else if (interfaceController.state().chatboxInterfaceId != -1)
			drawInterface(0, 0, Widget.get(interfaceController.state().chatboxInterfaceId), 0);
		else if (interfaceController.state().dialogueInterfaceId != -1) {
			drawInterface(0, 0, Widget.get(interfaceController.state().dialogueInterfaceId), 0);
		} else {
			TypeFace chatFont = plainFont;
			int visibleLine = 0;
			Rasterizer.setCoordinates(0, 0, ClientLayout.CHATBOX_MESSAGE_CLIP_WIDTH,
					ClientLayout.CHATBOX_MESSAGE_HEIGHT);
			for (int messageIndex = 0; messageIndex < 100; messageIndex++)
				if (chatController.history().messages[messageIndex] != null) {
					int messageType = chatController.history().types[messageIndex];
					int lineY = (ClientLayout.CHATBOX_MESSAGE_BASELINE_Y
							- visibleLine * ClientLayout.CHATBOX_MESSAGE_LINE_HEIGHT) + chatController.scrollOffset();
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
					if ((messageType == ChatMessageType.PUBLIC_PRIVILEGED || messageType == ChatMessageType.PUBLIC)
							&& (messageType == ChatMessageType.PUBLIC_PRIVILEGED
									|| chatController.publicMode() == ChatMode.ON
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
					if ((messageType == ChatMessageType.PRIVATE_RECEIVED
							|| messageType == ChatMessageType.PRIVATE_RECEIVED_PRIVILEGED)
							&& chatController.splitPrivateChat() == 0
							&& (messageType == ChatMessageType.PRIVATE_RECEIVED_PRIVILEGED
									|| chatController.privateMode() == ChatMode.ON
									|| chatController.privateMode() == ChatMode.FRIENDS && isFriendOrSelf(sender))) {
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
					if (messageType == ChatMessageType.TRADE_REQUEST && (chatController.tradeMode() == ChatMode.ON
							|| chatController.tradeMode() == ChatMode.FRIENDS && isFriendOrSelf(sender))) {
						if (lineY > 0 && lineY < 110)
							chatFont.drawText(sender + " " + chatController.history().messages[messageIndex], 4, lineY,
									0x800080);
						visibleLine++;
					}
					if (messageType == ChatMessageType.PRIVATE_STATUS && chatController.splitPrivateChat() == 0
							&& chatController.privateMode() < ChatMode.OFF) {
						if (lineY > 0 && lineY < 110)
							chatFont.drawText(chatController.history().messages[messageIndex], 4, lineY, 0x800000);
						visibleLine++;
					}
					if (messageType == ChatMessageType.PRIVATE_SENT && chatController.splitPrivateChat() == 0
							&& chatController.privateMode() < ChatMode.OFF) {
						if (lineY > 0 && lineY < 110) {
							chatFont.drawText("To " + sender + ":", 4, lineY, 0);
							chatFont.drawText(chatController.history().messages[messageIndex],
									12 + chatFont.getFormattedTextWidth("To " + sender), lineY, 0x800000);
						}
						visibleLine++;
					}
					if (messageType == ChatMessageType.CHALLENGE_REQUEST && (chatController.tradeMode() == ChatMode.ON
							|| chatController.tradeMode() == ChatMode.FRIENDS && isFriendOrSelf(sender))) {
						if (lineY > 0 && lineY < 110)
							chatFont.drawText(sender + " " + chatController.history().messages[messageIndex], 4, lineY,
									0x7e3200);
						visibleLine++;
					}
				}

			Rasterizer.resetCoordinates();
			chatController.setContentHeight(visibleLine * ClientLayout.CHATBOX_MESSAGE_LINE_HEIGHT
					+ ClientLayout.CHATBOX_CONTENT_HEIGHT_PADDING);
			if (chatController.contentHeight() < ChatController.MIN_CONTENT_HEIGHT)
				chatController.setContentHeight(ChatController.MIN_CONTENT_HEIGHT);
			drawScrollbar(
					chatController.contentHeight() - chatController.scrollOffset()
							- ClientLayout.CHATBOX_MESSAGE_HEIGHT,
					ClientLayout.CHATBOX_SCROLLBAR_X, ClientLayout.CHATBOX_MESSAGE_HEIGHT,
					chatController.contentHeight(), 0);
			String localDisplayName;
			if (localPlayer != null && localPlayer.name != null)
				localDisplayName = localPlayer.name;
			else
				localDisplayName = TextFormatter.formatDisplayName(loginScreen.username);
			chatFont.drawText(localDisplayName + ":", 4, ClientLayout.CHATBOX_INPUT_BASELINE_Y, 0);
			chatFont.drawText(chatController.input() + "*", 6 + chatFont.getFormattedTextWidth(localDisplayName + ": "),
					ClientLayout.CHATBOX_INPUT_BASELINE_Y, 255);
			Rasterizer.drawHorizontalLine(0, ClientLayout.CHATBOX_MESSAGE_HEIGHT, ClientLayout.CHATBOX_DIVIDER_WIDTH,
					0);
		}
		if (menuController.state().open && menuController.state().screenArea == 2)
			drawContextMenu();
		gameRenderer.chatboxBuffer().draw(super.graphics, layout.chatboxX(), layout.chatboxY());
		gameRenderer.viewportBuffer().bindRaster();
		gameRenderer.bindViewport();
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

	/**
	 * Draws the minimap, compass, map functions, ground items, actors, hints, and
	 * destination marker.
	 */
	private void drawMinimap() {
		MinimapRenderer.Assets assets = new MinimapRenderer.Assets();
		assets.minimapBuffer = gameRenderer.minimapBuffer();
		assets.sceneBuffer = gameRenderer.viewportBuffer();
		assets.minimapMask = minimapBackground;
		assets.compass = compassSprite;
		assets.compassMaskWidths = compassMaskWidths;
		assets.compassMaskOffsets = compassMaskOffsets;
		assets.minimapMaskWidths = minimapMaskWidths;
		assets.minimapMaskOffsets = minimapMaskOffsets;
		assets.sceneScanlineOffsets = gameRenderer.viewportScanlineOffsets();
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
	private void addIgnore(long encodedName) {
		if (socialManager.addIgnore(encodedName, networkSession.outgoing, this::addChatMessage))
			gameRenderer.requestSidebarRedraw();
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
	 * 
	 * @param playerLevel target combat level
	 * @param localLevel  local combat level
	 * @return color tag
	 */
	public static String getCombatLevelColorTag(int playerLevel, int localLevel) {
		return MenuController.getCombatLevelColorTag(playerLevel, localLevel);
	}

	/**
	 * Removes an ignored name through SocialManager and refreshes dependent
	 * interface state.
	 *
	 * @param encodedName the Base-37 encoded player name
	 */
	private void removeIgnore(long encodedName) {
		if (socialManager.removeIgnore(encodedName, networkSession.outgoing))
			gameRenderer.requestSidebarRedraw();
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
	private void addFriend(long encodedName) {
		boolean membersAccount = accountMembershipStatus == 1;
		if (socialManager.addFriend(encodedName, membersAccount, localPlayer.name, networkSession.outgoing,
				this::addChatMessage))
			gameRenderer.requestSidebarRedraw();
	}

	/** Initializes world/zone state during one-time client bootstrap. */
	void initializeWorldForStartup() {
		worldState = new WorldState(dynamicObjects);
		zoneUpdates = new ZoneUpdateHandler(worldState, dynamicObjects);
		minimapRenderer.initializeMapImage();
	}

	/** Clears the inherited shell back buffer during final lifecycle cleanup. */
	void lifecycleClearGameBuffer() {
		gameBuffer = null;
	}

	/**
	 * Returns current varp state to the lifecycle coordinator.
	 * 
	 * @return varp state
	 */
	VarpState lifecycleVarpState() {
		return varpState;
	}

	/**
	 * Returns rendering ownership to the lifecycle coordinator.
	 * 
	 * @return game renderer
	 */
	GameRenderer lifecycleGameRenderer() {
		return gameRenderer;
	}

	/**
	 * Returns music ownership to the lifecycle coordinator.
	 * 
	 * @return music controller
	 */
	MusicController lifecycleMusicController() {
		return musicController;
	}

	/**
	 * Returns social ownership to the lifecycle coordinator.
	 * 
	 * @return social manager
	 */
	SocialManager lifecycleSocialManager() {
		return socialManager;
	}

	/**
	 * Returns region ownership to the lifecycle coordinator.
	 * 
	 * @return region manager
	 */
	RegionManager lifecycleRegionManager() {
		return regionManager;
	}

	/**
	 * Returns minimap ownership to the lifecycle coordinator.
	 * 
	 * @return minimap renderer
	 */
	MinimapRenderer lifecycleMinimapRenderer() {
		return minimapRenderer;
	}

	/**
	 * Returns menu ownership to the lifecycle coordinator.
	 * 
	 * @return menu controller
	 */
	MenuController lifecycleMenuController() {
		return menuController;
	}

	/**
	 * Returns the client layout to the lifecycle coordinator.
	 * 
	 * @return current layout owner
	 */
	ClientLayout clientLayout() {
		return layout;
	}

	/** Clears world/zone owners after their final shutdown cleanup. */
	void clearRuntimeWorldForShutdown() {
		worldState = null;
		zoneUpdates = null;
	}

	/**
	 * Applies a changed varp to client settings such as brightness, music, sound,
	 * and chat options.
	 *
	 * @param varpId the varp identifier
	 */
	void applyVarp(int varpId) {
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
			gameRenderer.requestGameScreenRedraw();
		}
		if (clientCode == 3)
			musicController.applySetting(varpValue, lowMemory, lifecycle.onDemandFetcher()::request);
		if (clientCode == 4)
			soundEffectQueue.applySetting(varpValue);
		if (clientCode == 5)
			oneButtonMouseMode = varpValue;
		if (clientCode == 6)
			chatEffects = varpValue;
		if (clientCode == 8) {
			chatController.setSplitPrivateChat(varpValue);
			gameRenderer.requestChatboxRedraw();
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
				plainFont.drawText("System update in: " + minutesRemaining + ":0" + secondsRemaining, 4,
						layout.unobscuredViewportHeight() - 5, 0xffff00);
			else
				plainFont.drawText("System update in: " + minutesRemaining + ":" + secondsRemaining, 4,
						layout.unobscuredViewportHeight() - 5, 0xffff00);
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
	 * 
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
		minimapRenderer.rebuild(worldState, plane, mapSceneSprites, mapFunctionSprites, gameRenderer.viewportBuffer(),
				gameRenderer.viewportScanlineOffsets(), networkSession.outgoing);
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

	/** Marks the current shell click as the classic interaction crosshair. */
	private void markInteractionCrosshair() {
		crossX = super.clickX;
		crossY = super.clickY;
		crossType = 2;
		crossCycle = 0;
	}

	/**
	 * Builds the current actor-overlay rendering context.
	 * 
	 * @return current actor-overlay frame context
	 */
	private ActorOverlayRenderer.Context createActorOverlayContext() {
		return new ActorOverlayRenderer.Context(layout, actorSynchronizer, localPlayer, cameraController, worldState,
				currentPlane, gameCycle, sceneEntityRenderer.getRenderCycle(), chatController.publicMode(), chatEffects,
				this::isFriendOrSelf, smallFont, boldFont, skullIconSprites, prayerIconSprites, hintIconSprites,
				hitmarkSprites, overheadTextColors, hintIconType, hintPlayerIndex, hintNpcIndex, hintTileX, hintTileY,
				hintHeight, hintOffsetX, hintOffsetY, regionManager.baseX, regionManager.baseY);
	}

	/**
	 * Rebuilds projection tables and scene visibility for the current viewport
	 * size.
	 */
	private void rebuildViewportProjection() {
		gameRenderer.rebuildViewportProjection(layout);
	}

	/** Recreates only size-dependent renderer state when the window is resized. */
	@Override
	protected void onResize(int width, int height) {
		layout.resize(width, height);
		gameRenderer.resizeViewport(getGameComponent(), layout);
	}

	/** Allocates fixed HUD surfaces and the current-size viewport lazily. */
	public void createGameScreenBuffers() {
		if (gameRenderer.chatboxBuffer() != null) {
			return;
		}
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
		gameRenderer.createGameScreenBuffers(getGameComponent(), layout, minimapBackground);
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
	void logout() {
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
	void drawGameLoadingMessage(String secondaryMessage, String primaryMessage) {
		if (gameRenderer.viewportBuffer() != null) {
			gameRenderer.viewportBuffer().bindRaster();
			gameRenderer.bindViewport();
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
			gameRenderer.viewportBuffer().draw(super.graphics, layout.viewportX(), layout.viewportY());
			return;
		}
		if (super.gameBuffer != null) {
			super.gameBuffer.bindRaster();
			gameRenderer.bindFullScreenScanlines();
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

	/** Runs one render cycle in logged-in mode or title/login mode. */
	public void processDrawing() {
		refreshGraphicsContextIfRequested();
		if (duplicateClientError || loadingError || invalidHostError) {
			drawStartupErrorScreen();
			return;
		}

		Graphics displayGraphics = super.graphics;
		if (displayGraphics == null) {
			return;
		}

		Graphics frameGraphics = gameRenderer.presentationGraphics(super.canvasWidth, super.canvasHeight);
		super.graphics = frameGraphics;
		try {
			drawCycle++;
			if (!loggedIn) {
				drawLoginScreen(false);
			} else {
				drawGameScreen();
			}
		} finally {
			super.graphics = displayGraphics;
			frameGraphics.dispose();
		}

		gameRenderer.blitPresentation(displayGraphics);
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
			int entryY = layout.contextMenuEntryY(menuY, menuController.state().count, entryIndex);
			int entryColor = 0xffffff;
			if (layout.isContextMenuEntryHit(mouseX, mouseY, menuX, menuWidth, entryY))
				entryColor = 0xffff00;
			boldFont.drawTextWithTags(menuController.state().entry(entryIndex).text(), menuX + 3, entryY, entryColor,
					true);
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
			smallFont.drawCenteredTextWithTags(lifecycle.onDemandFetcher().statusString, boxWidth / 2, textY, 0x75a9a9,
					true);
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
		gameRenderer.consumeGameScreenRedraw();
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
		gameRenderer.bindSidebar();
		sidebarBackground.draw(0, 0);
		if (interfaceController.state().sidebarOverlayInterfaceId != -1)
			drawInterface(0, 0, Widget.get(interfaceController.state().sidebarOverlayInterfaceId), 0);
		else if (interfaceController.state().tabInterfaceIds[interfaceController.state().selectedTab] != -1)
			drawInterface(0, 0,
					Widget.get(interfaceController.state().tabInterfaceIds[interfaceController.state().selectedTab]),
					0);
		if (menuController.state().open && menuController.state().screenArea == 1)
			drawContextMenu();
		gameRenderer.sidebarBuffer().draw(super.graphics, layout.sidebarX(), layout.sidebarY());
		gameRenderer.viewportBuffer().bindRaster();
		gameRenderer.bindViewport();
	}

	/**
	 * Formats an inventory amount using the renderer's legacy comma/K/million
	 * style.
	 *
	 * @param amount numeric amount
	 * @return formatted amount
	 */
	public static String formatAmountWithCommas(int amount) {
		return WidgetRenderer.formatAmountWithCommas(amount);
	}

	/**
	 * Prints client timing, memory, mouse, and network debug state to standard
	 * output.
	 */
	public void printDebugInfo() {
		System.out.println("============");
		System.out.println("flame-cycle:" + titleFlameAnimator.cycle());
		if (lifecycle.onDemandFetcher() != null)
			System.out.println("Od-cycle:" + lifecycle.onDemandFetcher().onDemandCycle);
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
		if (gameRenderer.consumeGameScreenRedraw()) {
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
	 * @param y       root Y coordinate
	 * @param x       root X coordinate
	 * @param widget  root widget
	 * @param scrollY root scroll offset
	 */
	public void drawInterface(int y, int x, Widget widget, int scrollY) {
		widgetRenderer.drawInterface(new WidgetRenderer.RenderContext(super.mouseX, super.mouseY, animationCycleDelta,
				smallFont, plainFont, scrollbarTop, scrollbarBottom, scrollbarTrackColor, scrollbarThumbColor,
				scrollbarHighlightColor, scrollbarShadowColor), y, x, widget, scrollY);
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
				regionManager.buildRegion(worldState, currentPlane, lowMemory, networkSession.outgoing,
						lifecycle.onDemandFetcher(), super.gameFrame != null, () -> {
							if (gameRenderer.viewportBuffer() != null) {
								gameRenderer.viewportBuffer().bindRaster();
								gameRenderer.bindViewport();
							}
						});
				networkSession.outgoing.writeOpcode(OutgoingPacketOpcode.REGION_LOADED);
			} else if (System.currentTimeMillis() - regionManager.loadingStartTime > 0x57e40L) {
				Signlink.reportError(loginScreen.username + " glcfb " + loginSession.getServerSessionKey() + ","
						+ status + "," + lowMemory + "," + lifecycle.getCacheIndex(0) + ","
						+ lifecycle.onDemandFetcher().getOutstandingRequestCount() + "," + currentPlane + ","
						+ regionManager.regionX + "," + regionManager.regionY);
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
		MinimapRenderer.Click click = minimapRenderer.transformClick(layout.minimapLocalX(clickX),
				layout.minimapLocalY(clickY), localPlayer, cameraController.followYaw);
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
		gameRenderer.clearGameScreenBuffers();
		super.gameBuffer = new GraphicsBuffer(getGameComponent(), ClientLayout.FIXED_WIDTH, ClientLayout.FIXED_HEIGHT);
		gameRenderer.requestGameScreenRedraw();
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

	/** Builds and presents one 3D scene frame through {@link GameRenderer}. */
	private void renderGameScene() {
		destinationX = gameRenderer.renderScene(
				new GameRenderer.SceneFrame(layout, sceneEntityRenderer, worldState, actorSynchronizer, localPlayer,
						cameraController, actorOverlayRenderer, createActorOverlayContext(), networkSession.outgoing,
						super.graphics, this::drawViewportOverlays, destinationX, destinationY, currentPlane, gameCycle,
						animationCycleDelta, localPlayerServerIndex, super.mouseX, super.mouseY, lowMemory));
	}

	/**
	 * Initializes a Client instance and wires the extracted subsystem owners and
	 * callbacks.
	 */
	public client() {
		skillExperiences = new int[Skills.COUNT];
		itemSearchQuery = "";
		itemSearchResultNames = new String[100];
		itemSearchResultIds = new int[100];
		crossSprites = new ImageRGB[8];
		minimapMaskWidths = new int[151];
		networkSession = new NetworkSession();
		socialManager = new SocialManager();
		chatController = new ChatController();
		interfaceController = new InterfaceController();
		menuController = new MenuController(layout, interfaceController, socialManager, chatController,
				() -> mouseButtonHoldTicks, interfaceRedrawSink);
		loginScreen = new LoginScreen();
		titleFlameAnimator = new TitleFlameAnimator();
		appearanceEditor = new AppearanceEditor();
		lifecycle = new ClientLifecycle(this);
		loginSession = new LoginSession(networkSession, this::openSocket, lifecycle::getArchiveCrc,
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
		varpState = new VarpState();
		dynamicObjects = new DynamicObjectFactory(varpState::get, () -> gameCycle);
		actorSynchronizer = new ActorSynchronizer(() -> gameCycle);
		actorUpdater = new ActorUpdater();
		cameraController = new CameraController();
		sceneEntityRenderer = new SceneEntityRenderer();
		actorOverlayRenderer = new ActorOverlayRenderer();
		gameRenderer = new GameRenderer();
		regionManager = new RegionManager(dynamicObjects);
		ClientActionDispatcher.Movement actionMovement = this::walkTo;
		Runnable interactionCrosshair = this::markInteractionCrosshair;
		ClientActionDispatcher.SocialListActions socialListActions = new ClientActionDispatcher.SocialListActions() {
			@Override
			public void addFriend(long encodedName) {
				client.this.addFriend(encodedName);
			}

			@Override
			public void addIgnore(long encodedName) {
				client.this.addIgnore(encodedName);
			}

			@Override
			public void removeFriend(long encodedName) {
				client.this.removeFriend(encodedName);
			}

			@Override
			public void removeIgnore(long encodedName) {
				client.this.removeIgnore(encodedName);
			}
		};
		ActionPacketEncoder actionPackets = new ActionPacketEncoder(networkSession.outgoing);
		PlayerActionHandler playerActionsHandler = new PlayerActionHandler(actorSynchronizer, actionPackets,
				interfaceController, actionMovement, interactionCrosshair);
		NpcActionHandler npcActionsHandler = new NpcActionHandler(actorSynchronizer, actionPackets, interfaceController,
				actionMovement, interactionCrosshair, this::addChatMessage);
		ObjectActionHandler objectActionsHandler = new ObjectActionHandler(actionPackets, interfaceController,
				regionManager, () -> worldState, () -> currentPlane, actionMovement, interactionCrosshair,
				this::addChatMessage);
		GroundItemActionHandler groundItemActionsHandler = new GroundItemActionHandler(actionPackets,
				interfaceController, regionManager, actionMovement, interactionCrosshair, this::addChatMessage);
		InventoryActionHandler inventoryActionsHandler = new InventoryActionHandler(actionPackets, interfaceController,
				gameRenderer, () -> inventoryClickCycle = 0, this::addChatMessage);
		WidgetActionHandler widgetActionsHandler = new WidgetActionHandler(actionPackets, interfaceController,
				varpState, gameRenderer, this::applyVarp, socialManager, chatController, appearanceEditor,
				() -> appearanceEditor.writeUpdate(networkSession.outgoing), this::closeInterfaces,
				value -> logoutTimer = value);
		SocialActionHandler socialActionsHandler = new SocialActionHandler(socialManager, actorSynchronizer,
				actionPackets, interfaceController, gameRenderer, chatController, actionMovement, this::addChatMessage,
				socialListActions, this::closeInterfaces);
		WalkActionHandler walkActionsHandler = new WalkActionHandler(() -> menuController.state().open,
				() -> worldState, layout, () -> super.clickX, () -> super.clickY);
		actionDispatcher = new ClientActionDispatcher(menuController, chatController, interfaceController, gameRenderer,
				playerActionsHandler, npcActionsHandler, objectActionsHandler, groundItemActionsHandler,
				inventoryActionsHandler, widgetActionsHandler, socialActionsHandler, walkActionsHandler);
		minimapRenderer = new MinimapRenderer();
		ClientScriptContext scriptContext = new ClientScriptContext(skill -> currentSkillLevels[skill],
				skill -> baseSkillLevels[skill], skill -> skillExperiences[skill], varpState::get,
				levelIndex -> experienceTable[levelIndex], BitMasks::get, () -> runEnergy, () -> weight,
				() -> localPlayer.combatLevel, () -> (localPlayer.x >> 7) + regionManager.baseX,
				() -> (localPlayer.y >> 7) + regionManager.baseY, () -> membersWorld);
		widgetRuntime = new WidgetRuntime(scriptContext);
		WidgetContentController widgetContentController = new WidgetContentController(socialManager, appearanceEditor,
				interfaceController, () -> gameCycle, () -> localPlayer, () -> currentWorldId, () -> playerRights,
				() -> new WidgetContentController.AccountStatus(accountCurrentDay, lastLoginDay, recoveryQuestionsDate,
						unreadMessageCount, lastPasswordChangeDate, membershipDays, membersWorld, Signlink.dns));
		widgetRenderer = new WidgetRenderer(interfaceController, widgetRuntime, widgetContentController);
		scrollbarTrackColor = 0x23201b;
		gameRenderer.clearTabAreaRedraw();
		hintIconSprites = new ImageRGB[32];
		localPlayerServerIndex = -1;
		sidebarIcons = new IndexedImage[13];
		duplicateClientError = false;
		minimapMaskOffsets = new int[151];
		currentSkillLevels = new int[Skills.COUNT];
		mapFunctionSprites = new ImageRGB[100];
		gameRenderer.consumeGameScreenRedraw();
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
		gameRenderer.clearSidebarRedraw();
		hitmarkSprites = new ImageRGB[20];
		regionManager.awaitingPlayerUpdate = false;
		gameRenderer.clearChatModesRedraw();
		interfaceController.setActionPending(false);
		gameRenderer.clearChatboxRedraw();
		chatboxScrollWidget = new Widget();
		cameraOrientationChanged = false;
		windowFocusReported = true;
		lastMinimapPlane = -1;
		loadingError = false;
		compassMaskWidths = new int[33];
		scrollbarShadowColor = 0x332d25;
		skullIconSprites = new ImageRGB[32];
		incomingPacketDispatcher = new IncomingPacketDispatcher(networkSession, new ClientPacketDispatcher(this));
	}

	/** The client state for report abuse name. */
	/** The RSA modulus used by the revision-377 login handshake. */
	private static BigInteger RSA_MODULUS = new BigInteger(
			"7162900525229798032761816791230527296329313291232324290237849263501208207972894053929065636522363163621000728841182238772712427862772219676577293600221789");

	/** Stores overhead text colors values. */
	private int overheadTextColors[] = { 0xffff00, 0xff0000, 65280, 65535, 0xff00ff, 0xffffff };

	/** Stores skill experiences values. */
	int skillExperiences[];
	/** The client state for hint tile x. */
	int hintTileX;
	/** The client state for hint tile y. */
	int hintTileY;
	/** The client state for hint height. */
	int hintHeight;
	/** The client state for hint offset x. */
	int hintOffsetX;
	/** The client state for hint offset y. */
	int hintOffsetY;
	/** The client state for item search query. */
	private String itemSearchQuery;
	/** The current number of item search result entries. */
	private int itemSearchResultCount;

	/** Stores item search result names values. */
	private String itemSearchResultNames[];

	/** Stores item search result IDs values. */
	private int itemSearchResultIds[];
	/** The client state for item search scroll offset. */
	private int itemSearchScrollOffset;
	/** The client state for player rights. */
	private int playerRights;
	/** Whether show fps is currently active or requested. */
	private static boolean showFps;
	/** Tracks the current logout timer in client ticks/cycles where applicable. */
	private int logoutTimer;
	/** The client state for redstone1. */
	IndexedImage redstone1;
	/** The client state for redstone2. */
	IndexedImage redstone2;
	/** The client state for redstone3. */
	IndexedImage redstone3;
	/** The client state for redstone1 horizontal. */
	IndexedImage redstone1Horizontal;
	/** The client state for redstone2 horizontal. */
	IndexedImage redstone2Horizontal;
	/** The client state for title archive. */
	Archive titleArchive;
	/**
	 * Tracks the current tooltip hover ticks in client ticks/cycles where
	 * applicable.
	 */
	/**
	 * Counts system update keepalive events for the original client timing/protocol
	 * behavior.
	 */
	private static int systemUpdateKeepaliveCounter;

	/** Stores cross sprites values. */
	ImageRGB crossSprites[];
	/** The client state for last click time. */
	private long lastClickTime;
	/** The current current hovered widget id. */

	/** Stores minimap mask widths values. */
	int minimapMaskWidths[];
	/** The current current world id. */
	static int currentWorldId = 10;
	/** The client state for port offset. */
	static int portOffset;

	/** Host used by every standalone socket opened through Signlink. */
	private static String serverHost = "127.0.0.1";
	/** Whether members world is currently active or requested. */
	static boolean membersWorld = true;
	/** Whether low memory is currently active or requested. */
	static boolean lowMemory;
	/** The client state for scrollbar track color. */
	private int scrollbarTrackColor;

	/** The client state for animation cycle delta. */
	private int animationCycleDelta;

	/** Stores experience table values. */
	static int experienceTable[];

	/** Stores hint icon sprites values. */
	ImageRGB hintIconSprites[];
	/** The client state for inventory rearrange mode. */
	private int inventoryRearrangeMode;
	/** Whether account flagged is currently active or requested. */
	private static boolean accountFlagged;
	/** The client state for network session. */
	final NetworkSession networkSession;
	/** Coordinates framed incoming packets with the application packet adapter. */
	private final IncomingPacketDispatcher incomingPacketDispatcher;
	/** The client state for social manager. */
	final SocialManager socialManager;
	/** Owns chat history, modes, text input, prompts, and chat scrolling. */
	final ChatController chatController;
	/** The client state for interface state. */
	final InterfaceController interfaceController;
	/** Receives interface-controller redraw requests. */
	final InterfaceController.RedrawSink interfaceRedrawSink = new InterfaceController.RedrawSink() {
		@Override
		public void redrawSidebar() {
			gameRenderer.requestSidebarRedraw();
		}

		@Override
		public void redrawTabs() {
			gameRenderer.requestTabAreaRedraw();
		}

		@Override
		public void redrawChatbox() {
			gameRenderer.requestChatboxRedraw();
		}

		@Override
		public void redrawGameScreen() {
			gameRenderer.requestGameScreenRedraw();
		}
	};
	/** The client state for menu state. */
	private final MenuController menuController;
	/** Owns normalized menu-action routing and shared selection cleanup. */
	private final ClientActionDispatcher actionDispatcher;
	/** The client state for login screen. */
	final LoginScreen loginScreen;
	/** Owns the revision-377 login handshake, retries, and login session key. */
	private final LoginSession loginSession;
	/** Owns title-flame simulation state and its animation worker. */
	private final TitleFlameAnimator titleFlameAnimator;
	/** Character-design interface state and preview owner. */
	private final AppearanceEditor appearanceEditor;
	/** Owns startup, resource services, and final shutdown. */
	final ClientLifecycle lifecycle;
	/** The client state for sound effect queue. */
	final SoundEffectQueue soundEffectQueue;
	/** The client state for music controller. */
	final MusicController musicController;
	/** The client state for widget runtime. */
	final WidgetRuntime widgetRuntime;
	/** Draws cache-defined widget trees and classic scrollbars. */
	private final WidgetRenderer widgetRenderer;
	/** The client state for pathfinder. */
	private final Pathfinder pathfinder;
	/** The client state for actor synchronizer. */
	final ActorSynchronizer actorSynchronizer;
	/** The client state for actor updater. */
	private final ActorUpdater actorUpdater;
	/** The client state for camera controller. */
	final CameraController cameraController;
	/** The client state for scene entity renderer. */
	private final SceneEntityRenderer sceneEntityRenderer;
	/**
	 * Draws actor-associated viewport overlays and owns their transient layout
	 * state.
	 */
	private final ActorOverlayRenderer actorOverlayRenderer;
	/**
	 * Owns logged-in rendering surfaces, invalidation state, projection tables, and
	 * presentation.
	 */
	final GameRenderer gameRenderer;
	/** The client state for minimap renderer. */
	final MinimapRenderer minimapRenderer;
	/** The client state for region manager. */
	final RegionManager regionManager;
	/** Current and server-shadow client varp state. */
	final VarpState varpState;
	/** Creates dynamic locations with the narrow runtime state they require. */
	private final DynamicObjectFactory dynamicObjects;
	/** The client state for world state. */
	WorldState worldState;
	/** The client state for zone updates. */
	ZoneUpdateHandler zoneUpdates;
	/** The client state for actor chat handler. */
	final ActorSynchronizer.ChatHandler actorChatHandler = new ActorSynchronizer.ChatHandler() {
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
			client.this.addChatMessage(sender, message, type);
		}
	};
	/** The client state for local player server index. */
	int localPlayerServerIndex;
	/** The client state for local player. */
	static Player localPlayer;
	/** The client state for chat modes background. */
	IndexedImage chatModesBackground;
	/** The client state for bottom tab background. */
	IndexedImage bottomTabBackground;
	/** The client state for top tab background. */
	IndexedImage topTabBackground;

	/** Stores sidebar icons values. */
	IndexedImage sidebarIcons[];
	/** The client state for redstone1 vertical. */
	IndexedImage redstone1Vertical;
	/** The client state for redstone2 vertical. */
	IndexedImage redstone2Vertical;
	/** The client state for redstone3 vertical. */
	IndexedImage redstone3Vertical;
	/** The client state for redstone1 both. */
	IndexedImage redstone1Both;
	/** The client state for redstone2 both. */
	IndexedImage redstone2Both;
	/** The client state for membership days. */
	int membershipDays;
	/** The client state for chat effects. */
	private int chatEffects;

	/** Stores chatbox scanline offsets values. */

	/** Stores sidebar scanline offsets values. */

	/** Stores full screen scanline offsets values. */

	/** The client state for last recorded mouse x. */
	private int lastRecordedMouseX;
	/** The client state for last recorded mouse y. */
	private int lastRecordedMouseY;
	/** Whether duplicate client error is currently active or requested. */
	boolean duplicateClientError;

	/** Stores minimap mask offsets values. */
	int minimapMaskOffsets[];
	/** The client state for cross x. */
	private int crossX;
	/** The client state for cross y. */
	private int crossY;
	/** Tracks the current cross cycle in client ticks/cycles where applicable. */
	private int crossCycle;
	/** The client state for cross type. */
	private int crossType;
	/** The client state for loading message. */
	String loadingMessage;

	/** Stores current skill levels values. */
	int currentSkillLevels[];
	/** The client state for weight. */
	int weight;

	/** Stores map function sprites values. */
	ImageRGB mapFunctionSprites[];
	/** The client state for recovery questions date. */
	int recoveryQuestionsDate;
	/** The client state for destination map marker. */
	ImageRGB destinationMapMarker;
	/** The client state for hint map marker. */
	ImageRGB hintMapMarker;

	/** The current sidebar tooltip widget id. */

	/** Stores base skill levels values. */
	int baseSkillLevels[];
	/**
	 * Tracks the current system update timer in client ticks/cycles where
	 * applicable.
	 */
	int systemUpdateTimer;
	/** The font used for small font. */
	TypeFace smallFont;
	/** The font used for plain font. */
	TypeFace plainFont;
	/** The font used for bold font. */
	TypeFace boldFont;
	/** The font used for fancy font. */
	TypeFace fancyFont;
	/** The client state for account membership status. */
	int accountMembershipStatus;

	/** Stores player actions values. */
	String playerActions[];

	/** Whether player action low priority is enabled or active. */
	boolean playerActionLowPriority[];

	/** Stores prayer icon sprites values. */
	ImageRGB prayerIconSprites[];
	/** The client state for scrollbar thumb color. */
	private int scrollbarThumbColor;
	/** The client state for last password change date. */
	int lastPasswordChangeDate;

	/** The client state for multi combat overlay. */
	ImageRGB multiCombatOverlay;
	/** The client state for current plane. */
	int currentPlane;
	/**
	 * Tracks the current mouse button hold ticks in client ticks/cycles where
	 * applicable.
	 */
	private int mouseButtonHoldTicks;
	/** The client state for scrollbar top. */
	IndexedImage scrollbarTop;
	/** The client state for scrollbar bottom. */
	IndexedImage scrollbarBottom;
	/** Whether invalid host error is currently active or requested. */
	private boolean invalidHostError;
	/** Whether report abuse mute player is currently active or requested. */

	/**
	 * Tracks the current title flame cycle in client ticks/cycles where applicable.
	 */
	private int titleFlameCycle;
	/** The current chatbox hovered widget id. */
	/** The sprite resource used for compass sprite. */
	ImageRGB compassSprite;

	/** The client state for destination x. */
	int destinationX;
	/** The client state for destination y. */
	int destinationY;
	/** The client state for alternative route. */
	private int alternativeRoute;
	/** Whether scrollbar dragging is currently active or requested. */
	/** The current viewport tooltip widget id. */
	/** The graphics or protocol buffer used for chat buffer. */
	Buffer chatBuffer;
	/** The client state for scrollbar highlight color. */
	private int scrollbarHighlightColor;
	/** Whether logged in is currently active or requested. */
	volatile boolean loggedIn;

	/** Stores moderator icons values. */
	IndexedImage moderatorIcons[];
	/** The client state for hint player index. */
	int hintPlayerIndex;

	/** Stores map scene sprites values. */
	IndexedImage mapSceneSprites[];
	/** Whether inventory drag moved is currently active or requested. */
	private boolean inventoryDragMoved;

	/** The client state for account current day. */
	int accountCurrentDay;

	/** Stores compass mask offsets values. */
	int compassMaskOffsets[];

	/** Stores hitmark sprites values. */
	ImageRGB hitmarkSprites[];
	/** The client state for sidebar background. */
	IndexedImage sidebarBackground;
	/** The client state for minimap background. */
	IndexedImage minimapBackground;
	/** The client state for chatbox background. */
	IndexedImage chatboxBackground;
	/** The client state for ground item map dot. */
	ImageRGB groundItemMapDot;
	/** The client state for npc map dot. */
	ImageRGB npcMapDot;
	/** The client state for player map dot. */
	ImageRGB playerMapDot;
	/** The client state for friend map dot. */
	ImageRGB friendMapDot;
	/** The client state for team map dot. */
	ImageRGB teamMapDot;
	/** The client state for hint icon type. */
	int hintIconType;
	/** The graphics or protocol buffer used for title top buffer. */
	GraphicsBuffer titleTopBuffer;
	/** The graphics or protocol buffer used for title bottom buffer. */
	GraphicsBuffer titleBottomBuffer;
	/** The graphics or protocol buffer used for login box buffer. */
	GraphicsBuffer loginBoxBuffer;
	/** The graphics or protocol buffer used for title left flame buffer. */
	GraphicsBuffer titleLeftFlameBuffer;
	/** The graphics or protocol buffer used for title right flame buffer. */
	GraphicsBuffer titleRightFlameBuffer;
	/** The graphics or protocol buffer used for title left bottom buffer. */
	GraphicsBuffer titleLeftBottomBuffer;
	/** The graphics or protocol buffer used for title right bottom buffer. */
	GraphicsBuffer titleRightBottomBuffer;
	/** The graphics or protocol buffer used for title left center buffer. */
	GraphicsBuffer titleLeftCenterBuffer;
	/** The graphics or protocol buffer used for title right center buffer. */
	GraphicsBuffer titleRightCenterBuffer;

	/** The client state for last login day. */
	int lastLoginDay;
	/** The client state for jaggrab socket. */
	private Socket jaggrabSocket;
	/** The client state for hint npc index. */
	int hintNpcIndex;
	/**
	 * Counts screen redraw keepalive events for the original client timing/protocol
	 * behavior.
	 */
	private static int screenRedrawKeepaliveCounter;
	/** Whether interface action pending is currently active or requested. */
	/** The client state for last login ip. */
	int lastLoginIp;

	/** The client state for tutorial island flag. */
	int tutorialIslandFlag;
	/** The client state for minimap edge arrow. */
	ImageRGB minimapEdgeArrow;
	/** The client state for mouse recorder. */
	MouseRecorder mouseRecorder;
	/** The client state for chatbox scroll widget. */
	private Widget chatboxScrollWidget;
	/** The client state for camera packet cooldown. */
	private int cameraPacketCooldown;
	/** Whether camera orientation changed is currently active or requested. */
	private boolean cameraOrientationChanged;

	/** The current number of unread message entries. */
	int unreadMessageCount;
	/** Whether window focus reported is currently active or requested. */
	private boolean windowFocusReported;
	/** The client state for last minimap plane. */
	private int lastMinimapPlane;
	/** The current sidebar hovered widget id. */
	/** Whether loading error is currently active or requested. */
	boolean loadingError;
	/** The current chatbox tooltip widget id. */

	/** Stores compass mask widths values. */
	int compassMaskWidths[];
	/** The client state for scrollbar shadow color. */
	private int scrollbarShadowColor;

	/** Stores skull icon sprites values. */
	ImageRGB skullIconSprites[];

	/** The client state for title box image. */
	private IndexedImage titleBoxImage;
	/** The client state for title button image. */
	private IndexedImage titleButtonImage;
	/** The current number of mouse telemetry repeat entries. */
	private int mouseTelemetryRepeatCount;
	/** The client state for one button mouse mode. */
	private int oneButtonMouseMode;
	/** The current viewport hovered widget id. */
	/** The client state for scrollbar drag padding. */
	/** Tracks the current draw cycle in client ticks/cycles where applicable. */
	private static int drawCycle;

	/** The current current tooltip widget id. */

	/** The RSA public exponent used by the revision-377 login handshake. */
	private static BigInteger RSA_EXPONENT = new BigInteger(
			"58778699976184461502525193738213253649000149147835990136706041084440742975821");

	/** The client state for multi combat zone. */
	int multiCombatZone;
	/** The client state for loading percent. */
	int loadingPercent;
	/** The client state for run energy. */
	int runEnergy;
	/** Tracks the current game cycle in client ticks/cycles where applicable. */
	static int gameCycle;

	/**
	 * Tracks the current inventory click cycle in client ticks/cycles where
	 * applicable.
	 */
	private int inventoryClickCycle;
	static {
		experienceTable = new int[99];
		int accumulatedExperience = 0;
		for (int levelIndex = 0; levelIndex < 99; levelIndex++) {
			int level = levelIndex + 1;
			int experienceDelta = (int) ((double) level + 300D * Math.pow(2D, (double) level / 7D));
			accumulatedExperience += experienceDelta;
			experienceTable[levelIndex] = accumulatedExperience / 4;
		}

	}
}
