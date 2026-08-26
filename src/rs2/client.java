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
import java.util.Calendar;
import java.util.Date;

import rs2.cache.Archive;
import rs2.cache.ResourceLoader;
import rs2.cache.cfg.Varbit;
import rs2.cache.cfg.Varp;
import rs2.cache.def.FloorDefinition;
import rs2.cache.def.GameObjectDefinition;
import rs2.cache.def.ItemDefinition;
import rs2.cache.def.NpcDefinition;
import rs2.cache.media.AnimationSequence;
import rs2.cache.media.IdentityKit;
import rs2.cache.media.ImageRGB;
import rs2.cache.media.IndexedImage;
import rs2.cache.media.SpotAnimation;
import rs2.cache.ondemand.OnDemandFetcher;
import rs2.cache.ondemand.OnDemandRequest;
import rs2.cache.ui.Widget;
import rs2.chat.ChatCodec;
import rs2.chat.ChatHistory;
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
import rs2.game.ZoneUpdateHandler;
import rs2.input.MouseRecorder;
import rs2.media.AnimationFrame;
import rs2.media.GraphicsBuffer;
import rs2.media.ItemSpriteFactory;
import rs2.media.Rasterizer;
import rs2.media.Rasterizer3D;
import rs2.media.TypeFace;
import rs2.media.renderable.Actor;
import rs2.media.renderable.DynamicObject;
import rs2.media.renderable.GroundItem;
import rs2.media.renderable.Model;
import rs2.media.renderable.Npc;
import rs2.media.renderable.Player;
import rs2.net.Buffer;
import rs2.net.ChatPacketEncoder;
import rs2.net.BufferedConnection;
import rs2.net.Ipv4Address;
import rs2.net.NetworkSession;
import rs2.net.MovementPacketEncoder;
import rs2.scene.Region;
import rs2.scene.Scene;
import rs2.sign.Signlink;
import rs2.sound.MusicController;
import rs2.sound.SoundEffectQueue;
import rs2.sound.SoundTrack;
import rs2.text.Base37;
import rs2.text.TextFormatter;
import rs2.ui.InterfaceState;
import rs2.ui.WidgetRuntime;
import rs2.ui.menu.MenuState;
import rs2.ui.login.LoginScreen;

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
	 * Closes the currently open client interfaces and emits the matching
	 * close-interface packet.
	 */
	public void closeInterfaces() {
		networkSession.outgoing.writeOpcode(110);
		if (interfaceState.sidebarOverlayInterfaceId != -1) {
			unloadInterface(interfaceState.sidebarOverlayInterfaceId);
			sidebarRedraw = true;
			interfaceActionPending = false;
			tabAreaRedraw = true;
		}
		if (interfaceState.chatboxInterfaceId != -1) {
			unloadInterface(interfaceState.chatboxInterfaceId);
			chatboxRedraw = true;
			interfaceActionPending = false;
		}
		if (interfaceState.fullscreenInterfaceId != -1) {
			unloadInterface(interfaceState.fullscreenInterfaceId);
			gameScreenRedraw = true;
		}
		if (interfaceState.fullscreenOverlayInterfaceId != -1) {
			unloadInterface(interfaceState.fullscreenOverlayInterfaceId);
		}
		if (interfaceState.openInterfaceId != -1) {
			unloadInterface(interfaceState.openInterfaceId);
		}
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
			if (args.length != 5 && args.length != 6) {
				System.out.println(
						"Usage: node-id, port-offset, [lowmem/highmem], [free/members], storeid, [server-host]");
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
						"Usage: node-id, port-offset, [lowmem/highmem], [free/members], storeid, [server-host]");
				return;
			}
			if (args[3].equals("free"))
				membersWorld = false;
			else if (args[3].equals("members")) {
				membersWorld = true;
			} else {
				System.out.println(
						"Usage: node-id, port-offset, [lowmem/highmem], [free/members], storeid, [server-host]");
				return;
			}
			Signlink.storeId = Integer.parseInt(args[4]);
			setServerHost(args.length == 6 ? args[5] : InetAddress.getLocalHost().getHostAddress());
			Signlink.start(InetAddress.getByName(serverHost));
			Client client1 = new Client();
			client1.createFrame(765, 503);
			return;
		} catch (Exception exception) {
			return;
		}
	}

	/**
	 * Runs the title-screen flame animation timing loop.
	 */
	public void runTitleFlameLoop() {
		Thread current = Thread.currentThread();
		titleFlameThread = current;
		titleFlameThreadActive = true;
		try {
			long timingWindowStart = System.currentTimeMillis();
			int timingSampleCount = 0;
			int sleepMillis = 20;
			while (titleFlamesRunning) {
				titleFlameCycle++;
				updateTitleFlames();
				updateTitleFlames();
				drawTitleFlames();
				if (++timingSampleCount > 10) {
					long now = System.currentTimeMillis();
					int timingError = (int) (now - timingWindowStart) / 10 - sleepMillis;
					sleepMillis = 40 - timingError;
					if (sleepMillis < 5)
						sleepMillis = 5;
					timingSampleCount = 0;
					timingWindowStart = now;
				}
				try {
					Thread.sleep(sleepMillis);
				} catch (Exception ignored) {
				}
			}
		} catch (Exception ignored2) {
		}
		titleFlameThreadActive = false;
		if (titleFlameThread == current) {
			titleFlameThread = null;
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
	 * Formats an item stack amount using the original K/M abbreviations.
	 *
	 * @param amount the numeric/item-stack amount
	 * @return the resulting text
	 */
	public static String formatItemStackAmount(int amount) {
		if (amount < 0x186a0)
			return String.valueOf(amount);
		if (amount < 0x989680)
			return amount / 1000 + "K";
		else
			return amount / 0xf4240 + "M";
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
		varpValues = null;
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
		loginBuffer = null;
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
		menuState.clearReferencesForQuit();
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
		if (super.clickButton == 1) {
			if (super.clickX >= 539 && super.clickX <= 573 && super.clickY >= 169 && super.clickY < 205
					&& interfaceState.tabInterfaceIds[0] != -1) {
				sidebarRedraw = true;
				interfaceState.selectedTab = 0;
				tabAreaRedraw = true;
			}
			if (super.clickX >= 569 && super.clickX <= 599 && super.clickY >= 168 && super.clickY < 205
					&& interfaceState.tabInterfaceIds[1] != -1) {
				sidebarRedraw = true;
				interfaceState.selectedTab = 1;
				tabAreaRedraw = true;
			}
			if (super.clickX >= 597 && super.clickX <= 627 && super.clickY >= 168 && super.clickY < 205
					&& interfaceState.tabInterfaceIds[2] != -1) {
				sidebarRedraw = true;
				interfaceState.selectedTab = 2;
				tabAreaRedraw = true;
			}
			if (super.clickX >= 625 && super.clickX <= 669 && super.clickY >= 168 && super.clickY < 203
					&& interfaceState.tabInterfaceIds[3] != -1) {
				sidebarRedraw = true;
				tabAreaRedraw = true;
			}
			if (super.clickX >= 666 && super.clickX <= 696 && super.clickY >= 168 && super.clickY < 205
					&& interfaceState.tabInterfaceIds[4] != -1) {
				sidebarRedraw = true;
				interfaceState.selectedTab = 4;
				tabAreaRedraw = true;
			}
			if (super.clickX >= 694 && super.clickX <= 724 && super.clickY >= 168 && super.clickY < 205
					&& interfaceState.tabInterfaceIds[5] != -1) {
				sidebarRedraw = true;
				interfaceState.selectedTab = 5;
				tabAreaRedraw = true;
			}
			if (super.clickX >= 722 && super.clickX <= 756 && super.clickY >= 169 && super.clickY < 205
					&& interfaceState.tabInterfaceIds[6] != -1) {
				sidebarRedraw = true;
				interfaceState.selectedTab = 6;
				tabAreaRedraw = true;
			}
			if (super.clickX >= 540 && super.clickX <= 574 && super.clickY >= 466 && super.clickY < 502
					&& interfaceState.tabInterfaceIds[7] != -1) {
				sidebarRedraw = true;
				interfaceState.selectedTab = 7;
				tabAreaRedraw = true;
			}
			if (super.clickX >= 572 && super.clickX <= 602 && super.clickY >= 466 && super.clickY < 503
					&& interfaceState.tabInterfaceIds[8] != -1) {
				sidebarRedraw = true;
				interfaceState.selectedTab = 8;
				tabAreaRedraw = true;
			}
			if (super.clickX >= 599 && super.clickX <= 629 && super.clickY >= 466 && super.clickY < 503
					&& interfaceState.tabInterfaceIds[9] != -1) {
				sidebarRedraw = true;
				interfaceState.selectedTab = 9;
				tabAreaRedraw = true;
			}
			if (super.clickX >= 627 && super.clickX <= 671 && super.clickY >= 467 && super.clickY < 502
					&& interfaceState.tabInterfaceIds[10] != -1) {
				sidebarRedraw = true;
				interfaceState.selectedTab = 10;
				tabAreaRedraw = true;
			}
			if (super.clickX >= 669 && super.clickX <= 699 && super.clickY >= 466 && super.clickY < 503
					&& interfaceState.tabInterfaceIds[11] != -1) {
				sidebarRedraw = true;
				interfaceState.selectedTab = 11;
				tabAreaRedraw = true;
			}
			if (super.clickX >= 696 && super.clickX <= 726 && super.clickY >= 466 && super.clickY < 503
					&& interfaceState.tabInterfaceIds[12] != -1) {
				sidebarRedraw = true;
				interfaceState.selectedTab = 12;
				tabAreaRedraw = true;
			}
			if (super.clickX >= 724 && super.clickX <= 758 && super.clickY >= 466 && super.clickY < 502
					&& interfaceState.tabInterfaceIds[13] != -1) {
				sidebarRedraw = true;
				interfaceState.selectedTab = 13;
				tabAreaRedraw = true;
			}
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
	 * Builds friend/ignore context-menu entries for a social-list widget.
	 *
	 * @param widget the widget being processed
	 * @return true when the requested condition/action succeeds; otherwise false
	 */
	public boolean buildSocialWidgetMenu(Widget widget) {
		int contentType = widget.contentType;
		if (contentType >= 1 && contentType <= 200 || contentType >= 701 && contentType <= 900) {
			if (contentType >= 801)
				contentType -= 701;
			else if (contentType >= 701)
				contentType -= 601;
			else if (contentType >= 101)
				contentType -= 101;
			else
				contentType--;
			menuState.actionNames[menuState.count] = "Remove @whi@" + socialManager.friendNames[contentType];
			menuState.actionIds[menuState.count] = 775;
			menuState.count++;
			menuState.actionNames[menuState.count] = "Message @whi@" + socialManager.friendNames[contentType];
			menuState.actionIds[menuState.count] = 984;
			menuState.count++;
			return true;
		}
		if (contentType >= 401 && contentType <= 500) {
			menuState.actionNames[menuState.count] = "Remove @whi@" + widget.text;
			menuState.actionIds[menuState.count] = 859;
			menuState.count++;
			return true;
		} else {
			return false;
		}
	}

	/**
	 * Resets the appearance-kit selections to the first valid kits for the current
	 * sex.
	 */
	public void resetCharacterAppearance() {
		appearanceModelDirty = true;
		for (int bodyPart = 0; bodyPart < 7; bodyPart++) {
			appearanceKitIds[bodyPart] = -1;
			for (int kitId = 0; kitId < IdentityKit.count; kitId++) {
				if (IdentityKit.definitions[kitId].nonSelectable
						|| IdentityKit.definitions[kitId].bodyPartId != bodyPart + (maleAppearance ? 0 : 7))
					continue;
				appearanceKitIds[bodyPart] = kitId;
				break;
			}

		}

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
				if (super.clickButton != 0 || mouseRecorder.sampleCount >= 40) {
					networkSession.outgoing.writeOpcode(171);
					networkSession.outgoing.writeByte(0);
					int packetStart = networkSession.outgoing.position;
					int encodedSampleCount = 0;
					for (int sampleIndex = 0; sampleIndex < mouseRecorder.sampleCount; sampleIndex++) {
						if (packetStart - networkSession.outgoing.position >= 240)
							break;
						encodedSampleCount++;
						int mouseY = mouseRecorder.yCoordinates[sampleIndex];
						if (mouseY < 0)
							mouseY = 0;
						else if (mouseY > 502)
							mouseY = 502;
						int mouseX = mouseRecorder.xCoordinates[sampleIndex];
						if (mouseX < 0)
							mouseX = 0;
						else if (mouseX > 764)
							mouseX = 764;
						int packedPosition = mouseY * 765 + mouseX;
						if (mouseRecorder.yCoordinates[sampleIndex] == -1
								&& mouseRecorder.xCoordinates[sampleIndex] == -1) {
							mouseX = -1;
							mouseY = -1;
							packedPosition = 0x7ffff;
						}
						if (mouseX == lastRecordedMouseX && mouseY == lastRecordedMouseY) {
							if (mouseTelemetryRepeatCount < 2047)
								mouseTelemetryRepeatCount++;
						} else {
							int deltaX = mouseX - lastRecordedMouseX;
							lastRecordedMouseX = mouseX;
							int deltaY = mouseY - lastRecordedMouseY;
							lastRecordedMouseY = mouseY;
							if (mouseTelemetryRepeatCount < 8 && deltaX >= -32 && deltaX <= 31 && deltaY >= -32
									&& deltaY <= 31) {
								deltaX += 32;
								deltaY += 32;
								networkSession.outgoing
										.writeShort((mouseTelemetryRepeatCount << 12) + (deltaX << 6) + deltaY);
								mouseTelemetryRepeatCount = 0;
							} else if (mouseTelemetryRepeatCount < 8) {
								networkSession.outgoing
										.writeMedium(0x800000 + (mouseTelemetryRepeatCount << 19) + packedPosition);
								mouseTelemetryRepeatCount = 0;
							} else {
								networkSession.outgoing
										.writeInt(0xc0000000 + (mouseTelemetryRepeatCount << 19) + packedPosition);
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
			if (clickDelayTicks > 4095L)
				clickDelayTicks = 4095L;
			lastClickTime = super.clickTime;
			int clickY = super.clickY;
			if (clickY < 0)
				clickY = 0;
			else if (clickY > 502)
				clickY = 502;
			int clickX = super.clickX;
			if (clickX < 0)
				clickX = 0;
			else if (clickX > 764)
				clickX = 764;
			int packedClickPosition = clickY * 765 + clickX;
			int clickButton = 0;
			if (super.clickButton == 2)
				clickButton = 1;
			int encodedClickDelay = (int) clickDelayTicks;
			networkSession.outgoing.writeOpcode(19);
			networkSession.outgoing.writeInt((encodedClickDelay << 20) + (clickButton << 19) + packedClickPosition);
		}
		if (cameraPacketCooldown > 0)
			cameraPacketCooldown--;
		if (super.keyStatus[1] == 1 || super.keyStatus[2] == 1 || super.keyStatus[3] == 1 || super.keyStatus[4] == 1)
			cameraOrientationChanged = true;
		if (cameraOrientationChanged && cameraPacketCooldown <= 0) {
			cameraPacketCooldown = 20;
			cameraOrientationChanged = false;
			networkSession.outgoing.writeOpcode(140);
			networkSession.outgoing.writeShortLE(cameraController.followPitch);
			networkSession.outgoing.writeShortLE(cameraController.followYaw);
		}
		if (super.hasFocus && !windowFocusReported) {
			windowFocusReported = true;
			networkSession.outgoing.writeOpcode(187);
			networkSession.outgoing.writeByte(1);
		}
		if (!super.hasFocus && windowFocusReported) {
			windowFocusReported = false;
			networkSession.outgoing.writeOpcode(187);
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
		if (interfaceState.pressedInventoryArea != 0) {
			inventoryClickCycle++;
			if (inventoryClickCycle >= 15) {
				if (interfaceState.pressedInventoryArea == 2)
					sidebarRedraw = true;
				if (interfaceState.pressedInventoryArea == 3)
					chatboxRedraw = true;
				interfaceState.pressedInventoryArea = 0;
			}
		}
		if (interfaceState.inventoryDragArea != 0) {
			interfaceState.inventoryDragDuration++;
			if (super.mouseX > interfaceState.inventoryDragStartX + 5
					|| super.mouseX < interfaceState.inventoryDragStartX - 5
					|| super.mouseY > interfaceState.inventoryDragStartY + 5
					|| super.mouseY < interfaceState.inventoryDragStartY - 5)
				inventoryDragMoved = true;
			if (super.mouseButton == 0) {
				if (interfaceState.inventoryDragArea == 2)
					sidebarRedraw = true;
				if (interfaceState.inventoryDragArea == 3)
					chatboxRedraw = true;
				interfaceState.inventoryDragArea = 0;
				if (inventoryDragMoved && interfaceState.inventoryDragDuration >= 5) {
					interfaceState.hoveredInventoryWidgetId = -1;
					buildContextMenu();
					if (interfaceState.hoveredInventoryWidgetId == interfaceState.draggedInventoryWidgetId
							&& interfaceState.hoveredInventorySlot != interfaceState.draggedInventorySlot) {
						Widget inventoryWidget = Widget.get(interfaceState.draggedInventoryWidgetId);
						// Legacy drag mode: 0 swaps/moves directly; 1 performs insertion-style
						// shifting.
						int insertionMode = 0;
						if (inventoryRearrangeMode == 1 && inventoryWidget.contentType == 206)
							insertionMode = 1;
						if (inventoryWidget.itemIds[interfaceState.hoveredInventorySlot] <= 0)
							insertionMode = 0;
						if (inventoryWidget.inventoryReplaceItems) {
							int sourceSlot = interfaceState.draggedInventorySlot;
							int destinationSlot = interfaceState.hoveredInventorySlot;
							inventoryWidget.itemIds[destinationSlot] = inventoryWidget.itemIds[sourceSlot];
							inventoryWidget.itemAmounts[destinationSlot] = inventoryWidget.itemAmounts[sourceSlot];
							inventoryWidget.itemIds[sourceSlot] = -1;
							inventoryWidget.itemAmounts[sourceSlot] = 0;
						} else if (insertionMode == 1) {
							int movingSlot = interfaceState.draggedInventorySlot;
							for (int targetSlot = interfaceState.hoveredInventorySlot; movingSlot != targetSlot;)
								if (movingSlot > targetSlot) {
									inventoryWidget.swapItems(movingSlot - 1, movingSlot);
									movingSlot--;
								} else if (movingSlot < targetSlot) {
									inventoryWidget.swapItems(movingSlot + 1, movingSlot);
									movingSlot++;
								}

						} else {
							inventoryWidget.swapItems(interfaceState.hoveredInventorySlot,
									interfaceState.draggedInventorySlot);
						}
						networkSession.outgoing.writeOpcode(123);
						networkSession.outgoing.writeShortAddLE(interfaceState.hoveredInventorySlot);
						networkSession.outgoing.writeByteAdd(insertionMode);
						networkSession.outgoing.writeShortAdd(interfaceState.draggedInventoryWidgetId);
						networkSession.outgoing.writeShortLE(interfaceState.draggedInventorySlot);
					}
				} else if ((oneButtonMouseMode == 1 || isAddFriendMenuAction(menuState.count - 1))
						&& menuState.count > 2)
					openContextMenu();
				else if (menuState.count > 0)
					dispatchMenuAction(menuState.count - 1);
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
		if (super.clickButton == 1 && clickToContinueMessage != null) {
			clickToContinueMessage = null;
			chatboxRedraw = true;
			super.clickButton = 0;
		}
		processMenuClick();
		if (interfaceState.fullscreenInterfaceId == -1) {
			processMinimapClick();
			processTabClick();
		}
		if (super.mouseButton == 1 || super.clickButton == 1)
			mouseButtonHoldTicks++;
		if (chatboxTooltipWidgetId != 0 || sidebarTooltipWidgetId != 0 || viewportTooltipWidgetId != 0) {
			if (tooltipHoverTicks < 100) {
				tooltipHoverTicks++;
				if (tooltipHoverTicks == 100) {
					if (chatboxTooltipWidgetId != 0)
						chatboxRedraw = true;
					if (sidebarTooltipWidgetId != 0)
						sidebarRedraw = true;
				}
			}
		} else if (tooltipHoverTicks > 0)
			tooltipHoverTicks--;
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
			networkSession.outgoing.writeOpcode(202);
		}
		cameraController.tickRandomOffsets();
		minimapRenderer.tickRandomOffsets();
		networkSession.outgoingIdleCycles++;
		if (networkSession.outgoingIdleCycles > 50)
			networkSession.outgoing.writeOpcode(40);
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
			if (interfaceState.openInterfaceId != -1
					&& interfaceState.openInterfaceId == interfaceState.reportAbuseInterfaceId) {
				if (keyCode == 8 && reportAbuseName.length() > 0)
					reportAbuseName = reportAbuseName.substring(0, reportAbuseName.length() - 1);
				if ((keyCode >= 97 && keyCode <= 122 || keyCode >= 65 && keyCode <= 90 || keyCode >= 48 && keyCode <= 57
						|| keyCode == 32) && reportAbuseName.length() < 12)
					reportAbuseName += (char) keyCode;
			} else if (messagePromptRaised) {
				if (keyCode >= 32 && keyCode <= 122 && promptInput.length() < 80) {
					promptInput += (char) keyCode;
					chatboxRedraw = true;
				}
				if (keyCode == 8 && promptInput.length() > 0) {
					promptInput = promptInput.substring(0, promptInput.length() - 1);
					chatboxRedraw = true;
				}
				if (keyCode == 13 || keyCode == 10) {
					messagePromptRaised = false;
					chatboxRedraw = true;
					if (promptAction == 1) {
						long encodedName = Base37.encode(promptInput);
						addFriend(encodedName);
					}
					if (promptAction == 2 && socialManager.friendCount > 0) {
						long encodedName2 = Base37.encode(promptInput);
						removeFriend(encodedName2);
					}
					if (promptAction == 3 && promptInput.length() > 0) {
						ChatPacketEncoder.writePrivateMessage(networkSession.outgoing, privateMessageTarget,
								promptInput);
						promptInput = ChatCodec.normalize(promptInput);
						promptInput = Censor.censor(promptInput);
						addChatMessage(TextFormatter.formatDisplayName(Base37.decode(privateMessageTarget)),
								promptInput, 6);
						if (privateChatMode == 2) {
							privateChatMode = 1;
							chatModesRedraw = true;
							ChatPacketEncoder.writeChatModes(networkSession.outgoing, publicChatMode, privateChatMode,
									tradeMode);
						}
					}
					if (promptAction == 4 && socialManager.ignoreCount < 100) {
						long encodedName3 = Base37.encode(promptInput);
						addIgnore(encodedName3);
					}
					if (promptAction == 5 && socialManager.ignoreCount > 0) {
						long encodedName4 = Base37.encode(promptInput);
						removeIgnore(encodedName4);
					}
				}
			} else if (inputDialogState == 1) {
				if (keyCode >= 48 && keyCode <= 57 && inputDialogText.length() < 10) {
					inputDialogText += (char) keyCode;
					chatboxRedraw = true;
				}
				if (keyCode == 8 && inputDialogText.length() > 0) {
					inputDialogText = inputDialogText.substring(0, inputDialogText.length() - 1);
					chatboxRedraw = true;
				}
				if (keyCode == 13 || keyCode == 10) {
					if (inputDialogText.length() > 0) {
						int amount = 0;
						try {
							amount = Integer.parseInt(inputDialogText);
						} catch (Exception ignored) {
						}
						networkSession.outgoing.writeOpcode(75);
						networkSession.outgoing.writeInt(amount);
					}
					inputDialogState = 0;
					chatboxRedraw = true;
				}
			} else if (inputDialogState == 2) {
				if (keyCode >= 32 && keyCode <= 122 && inputDialogText.length() < 12) {
					inputDialogText += (char) keyCode;
					chatboxRedraw = true;
				}
				if (keyCode == 8 && inputDialogText.length() > 0) {
					inputDialogText = inputDialogText.substring(0, inputDialogText.length() - 1);
					chatboxRedraw = true;
				}
				if (keyCode == 13 || keyCode == 10) {
					if (inputDialogText.length() > 0) {
						networkSession.outgoing.writeOpcode(206);
						networkSession.outgoing.writeLong(Base37.encode(inputDialogText));
					}
					inputDialogState = 0;
					chatboxRedraw = true;
				}
			} else if (inputDialogState == 3) {
				if (keyCode >= 32 && keyCode <= 122 && inputDialogText.length() < 40) {
					inputDialogText += (char) keyCode;
					chatboxRedraw = true;
				}
				if (keyCode == 8 && inputDialogText.length() > 0) {
					inputDialogText = inputDialogText.substring(0, inputDialogText.length() - 1);
					chatboxRedraw = true;
				}
			} else if (interfaceState.chatboxInterfaceId == -1 && interfaceState.fullscreenInterfaceId == -1) {
				if (keyCode >= 32 && keyCode <= 122 && chatInput.length() < 80) {
					chatInput += (char) keyCode;
					chatboxRedraw = true;
				}
				if (keyCode == 8 && chatInput.length() > 0) {
					chatInput = chatInput.substring(0, chatInput.length() - 1);
					chatboxRedraw = true;
				}
				if ((keyCode == 13 || keyCode == 10) && chatInput.length() > 0) {
					if (playerRights == 2) {
						if (chatInput.equals("::clientdrop"))
							reconnect();
						if (chatInput.equals("::lag"))
							printDebugInfo();
						if (chatInput.equals("::prefetchmusic")) {
							for (int midiId = 0; midiId < onDemandFetcher.getFileCount(2); midiId++)
								onDemandFetcher.setExtraPriority(2, midiId, (byte) 1);

						}
						if (chatInput.equals("::fpson"))
							showFps = true;
						if (chatInput.equals("::fpsoff"))
							showFps = false;
						if (chatInput.equals("::noclip")) {
							for (int plane = 0; plane < 4; plane++) {
								for (int tileX = 1; tileX < 103; tileX++) {
									for (int tileY = 1; tileY < 103; tileY++)
										worldState.collisionMaps[plane].flags[tileX][tileY] = 0;

								}

							}

						}
					}
					if (chatInput.startsWith("::")) {
						ChatPacketEncoder.writeCommand(networkSession.outgoing, chatInput);
					} else {
						String lowercaseInput = chatInput.toLowerCase();
						int chatColor = 0;
						if (lowercaseInput.startsWith("yellow:")) {
							chatColor = 0;
							chatInput = chatInput.substring(7);
						} else if (lowercaseInput.startsWith("red:")) {
							chatColor = 1;
							chatInput = chatInput.substring(4);
						} else if (lowercaseInput.startsWith("green:")) {
							chatColor = 2;
							chatInput = chatInput.substring(6);
						} else if (lowercaseInput.startsWith("cyan:")) {
							chatColor = 3;
							chatInput = chatInput.substring(5);
						} else if (lowercaseInput.startsWith("purple:")) {
							chatColor = 4;
							chatInput = chatInput.substring(7);
						} else if (lowercaseInput.startsWith("white:")) {
							chatColor = 5;
							chatInput = chatInput.substring(6);
						} else if (lowercaseInput.startsWith("flash1:")) {
							chatColor = 6;
							chatInput = chatInput.substring(7);
						} else if (lowercaseInput.startsWith("flash2:")) {
							chatColor = 7;
							chatInput = chatInput.substring(7);
						} else if (lowercaseInput.startsWith("flash3:")) {
							chatColor = 8;
							chatInput = chatInput.substring(7);
						} else if (lowercaseInput.startsWith("glow1:")) {
							chatColor = 9;
							chatInput = chatInput.substring(6);
						} else if (lowercaseInput.startsWith("glow2:")) {
							chatColor = 10;
							chatInput = chatInput.substring(6);
						} else if (lowercaseInput.startsWith("glow3:")) {
							chatColor = 11;
							chatInput = chatInput.substring(6);
						}
						lowercaseInput = chatInput.toLowerCase();
						int chatEffect = 0;
						if (lowercaseInput.startsWith("wave:")) {
							chatEffect = 1;
							chatInput = chatInput.substring(5);
						} else if (lowercaseInput.startsWith("wave2:")) {
							chatEffect = 2;
							chatInput = chatInput.substring(6);
						} else if (lowercaseInput.startsWith("shake:")) {
							chatEffect = 3;
							chatInput = chatInput.substring(6);
						} else if (lowercaseInput.startsWith("scroll:")) {
							chatEffect = 4;
							chatInput = chatInput.substring(7);
						} else if (lowercaseInput.startsWith("slide:")) {
							chatEffect = 5;
							chatInput = chatInput.substring(6);
						}
						ChatPacketEncoder.writePublicMessage(networkSession.outgoing, chatColor, chatEffect, chatInput,
								chatBuffer);
						chatInput = ChatCodec.normalize(chatInput);
						chatInput = Censor.censor(chatInput);
						localPlayer.overheadText = chatInput;
						localPlayer.overheadTextColor = chatColor;
						localPlayer.overheadTextEffect = chatEffect;
						localPlayer.overheadTextCyclesRemaining = 150;
						if (playerRights == 2)
							addChatMessage("@cr2@" + localPlayer.name, ((Actor) (localPlayer)).overheadText, 2);
						else if (playerRights == 1)
							addChatMessage("@cr1@" + localPlayer.name, ((Actor) (localPlayer)).overheadText, 2);
						else
							addChatMessage(localPlayer.name, ((Actor) (localPlayer)).overheadText, 2);
						if (publicChatMode == 2) {
							publicChatMode = 3;
							chatModesRedraw = true;
							ChatPacketEncoder.writeChatModes(networkSession.outgoing, publicChatMode, privateChatMode,
									tradeMode);
						}
					}
					chatInput = "";
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
	 */
	public DataInputStream openJaggrabStream(String request) throws IOException {
		if (jaggrabSocket != null) {
			try {
				jaggrabSocket.close();
			} catch (Exception ignored) {
			}
			jaggrabSocket = null;
		}
		jaggrabSocket = openSocket(43595);
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
			if (!networkSession.readIncomingPacket())
				return false;
			return dispatchIncomingPacket();
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
	 * Handles the currently framed incoming packet while preserving revision-377
	 * opcode-specific reads and side effects.
	 *
	 * @return true when the requested condition/action succeeds; otherwise false
	 */
	private boolean dispatchIncomingPacket() {
		if (networkSession.incomingOpcode == 166) {
			int widgetYOffset = networkSession.incoming.readShortLE();
			int widgetXOffset = networkSession.incoming.readShortLE();
			int widgetId = networkSession.incoming.readUnsignedShort();
			Widget widget = Widget.get(widgetId);
			widget.xOffset = widgetXOffset;
			widget.yOffset = widgetYOffset;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 186) {
			int modelPitch = networkSession.incoming.readUnsignedShortAdd();
			int widgetId2 = networkSession.incoming.readUnsignedShortAddLE();
			int modelZoom = networkSession.incoming.readUnsignedShortAdd();
			int modelYaw = networkSession.incoming.readUnsignedShortLE();
			Widget.get(widgetId2).modelPitch = modelPitch;
			Widget.get(widgetId2).modelYaw = modelYaw;
			Widget.get(widgetId2).modelZoom = modelZoom;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 216) {
			int mediaId = networkSession.incoming.readUnsignedShortAddLE();
			int widgetId3 = networkSession.incoming.readUnsignedShortAddLE();
			Widget.get(widgetId3).mediaType = 1;
			Widget.get(widgetId3).mediaId = mediaId;
			networkSession.incomingOpcode = -1;
			return true;
		}
		/* Opcode 26: queued sound effect (soundId, loopCount, delay). */
		if (networkSession.incomingOpcode == 26) {
			int soundId = networkSession.incoming.readUnsignedShort();
			int loopCount = networkSession.incoming.readUnsignedByte();
			int delay = networkSession.incoming.readUnsignedShort();
			soundEffectQueue.queuePacketSound(soundId, loopCount, delay, lowMemory);
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 182) {
			int varpId = networkSession.incoming.readUnsignedShortAdd();
			byte varpValue = networkSession.incoming.readByteSub();
			varpShadowValues[varpId] = varpValue;
			if (varpValues[varpId] != varpValue) {
				varpValues[varpId] = varpValue;
				applyVarp(varpId);
				sidebarRedraw = true;
				if (interfaceState.dialogueInterfaceId != -1)
					chatboxRedraw = true;
			}
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 13) {
			for (int playerIndex = 0; playerIndex < actorSynchronizer.players.length; playerIndex++)
				if (actorSynchronizer.players[playerIndex] != null)
					actorSynchronizer.players[playerIndex].sequence = -1;

			for (int npcIndex = 0; npcIndex < actorSynchronizer.npcs.length; npcIndex++)
				if (actorSynchronizer.npcs[npcIndex] != null)
					actorSynchronizer.npcs[npcIndex].sequence = -1;

			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 156) {
			minimapRenderer.state = networkSession.incoming.readUnsignedByte();
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 162) {
			int npcId = networkSession.incoming.readUnsignedShortAdd();
			int widgetId4 = networkSession.incoming.readUnsignedShortLE();
			Widget.get(widgetId4).mediaType = 2;
			Widget.get(widgetId4).mediaId = npcId;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 109) {
			int chatboxInterfaceId = networkSession.incoming.readUnsignedShort();
			widgetRuntime.resetAnimations(chatboxInterfaceId);
			if (interfaceState.sidebarOverlayInterfaceId != -1) {
				unloadInterface(interfaceState.sidebarOverlayInterfaceId);
				sidebarRedraw = true;
				tabAreaRedraw = true;
			}
			if (interfaceState.fullscreenInterfaceId != -1) {
				unloadInterface(interfaceState.fullscreenInterfaceId);
				gameScreenRedraw = true;
			}
			if (interfaceState.fullscreenOverlayInterfaceId != -1) {
				unloadInterface(interfaceState.fullscreenOverlayInterfaceId);
			}
			if (interfaceState.openInterfaceId != -1) {
				unloadInterface(interfaceState.openInterfaceId);
			}
			if (interfaceState.chatboxInterfaceId != chatboxInterfaceId) {
				unloadInterface(interfaceState.chatboxInterfaceId);
				interfaceState.chatboxInterfaceId = chatboxInterfaceId;
			}
			interfaceActionPending = false;
			chatboxRedraw = true;
			networkSession.incomingOpcode = -1;
			return true;
		}
		/* Opcode 220: select background MIDI track. */
		if (networkSession.incomingOpcode == 220) {
			int trackId = networkSession.incoming.readUnsignedShortAddLE();
			musicController.selectTrack(trackId, lowMemory, onDemandFetcher::request);
			networkSession.incomingOpcode = -1;
			return true;
		}
		/* Opcode 249: temporary MIDI track followed by delayed resume. */
		if (networkSession.incomingOpcode == 249) {
			int trackId = networkSession.incoming.readUnsignedShortLE();
			int resumeDelay = networkSession.incoming.readMediumME();
			musicController.playTemporaryTrack(trackId, resumeDelay, lowMemory, onDemandFetcher::request);
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 158) {
			int dialogueInterfaceId = networkSession.incoming.readShortLE();
			if (dialogueInterfaceId != interfaceState.dialogueInterfaceId) {
				unloadInterface(interfaceState.dialogueInterfaceId);
				interfaceState.dialogueInterfaceId = dialogueInterfaceId;
			}
			chatboxRedraw = true;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 218) {
			int widgetId5 = networkSession.incoming.readUnsignedShort();
			int packedColor = networkSession.incoming.readUnsignedShortAdd();
			int red5 = packedColor >> 10 & 0x1f;
			int green5 = packedColor >> 5 & 0x1f;
			int blue5 = packedColor & 0x1f;
			Widget.get(widgetId5).color = (red5 << 19) + (green5 << 11) + (blue5 << 3);
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 157) {
			int actionSlot = networkSession.incoming.readUnsignedByteNeg();
			String actionText = networkSession.incoming.readString();
			// Protocol flag name is unknown; zero marks the player action as low-priority
			// in this revision.
			int priorityFlag = networkSession.incoming.readUnsignedByte();
			if (actionSlot >= 1 && actionSlot <= 5) {
				if (actionText.equalsIgnoreCase("null"))
					actionText = null;
				playerActions[actionSlot - 1] = actionText;
				playerActionLowPriority[actionSlot - 1] = priorityFlag == 0;
			}
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 6) {
			messagePromptRaised = false;
			inputDialogState = 2;
			inputDialogText = "";
			chatboxRedraw = true;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 201) {
			publicChatMode = networkSession.incoming.readUnsignedByte();
			privateChatMode = networkSession.incoming.readUnsignedByte();
			tradeMode = networkSession.incoming.readUnsignedByte();
			chatModesRedraw = true;
			chatboxRedraw = true;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 199) {
			hintIconType = networkSession.incoming.readUnsignedByte();
			if (hintIconType == 1)
				hintNpcIndex = networkSession.incoming.readUnsignedShort();
			if (hintIconType >= 2 && hintIconType <= 6) {
				if (hintIconType == 2) {
					hintOffsetX = 64;
					hintOffsetY = 64;
				}
				if (hintIconType == 3) {
					hintOffsetX = 0;
					hintOffsetY = 64;
				}
				if (hintIconType == 4) {
					hintOffsetX = 128;
					hintOffsetY = 64;
				}
				if (hintIconType == 5) {
					hintOffsetX = 64;
					hintOffsetY = 0;
				}
				if (hintIconType == 6) {
					hintOffsetX = 64;
					hintOffsetY = 128;
				}
				hintIconType = 2;
				hintTileX = networkSession.incoming.readUnsignedShort();
				hintTileY = networkSession.incoming.readUnsignedShort();
				hintHeight = networkSession.incoming.readUnsignedByte();
			}
			if (hintIconType == 10)
				hintPlayerIndex = networkSession.incoming.readUnsignedShort();
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 167) {
			int tileX = networkSession.incoming.readUnsignedByte();
			int tileY = networkSession.incoming.readUnsignedByte();
			int heightOffset = networkSession.incoming.readUnsignedShort();
			int baseSpeed = networkSession.incoming.readUnsignedByte();
			int scale = networkSession.incoming.readUnsignedByte();
			cameraController.setCinematicLookAt(tileX, tileY, heightOffset, baseSpeed, scale, worldState, currentPlane);
			networkSession.incomingOpcode = -1;
			return true;
		}

		if (networkSession.incomingOpcode == 5) {
			logout();
			networkSession.incomingOpcode = -1;
			return false;
		}
		if (networkSession.incomingOpcode == 115) {
			int varpValue2 = networkSession.incoming.readIntIME();
			int varpId2 = networkSession.incoming.readUnsignedShortLE();
			varpShadowValues[varpId2] = varpValue2;
			if (varpValues[varpId2] != varpValue2) {
				varpValues[varpId2] = varpValue2;
				applyVarp(varpId2);
				sidebarRedraw = true;
				if (interfaceState.dialogueInterfaceId != -1)
					chatboxRedraw = true;
			}
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 29) {
			if (interfaceState.sidebarOverlayInterfaceId != -1) {
				unloadInterface(interfaceState.sidebarOverlayInterfaceId);
				sidebarRedraw = true;
				tabAreaRedraw = true;
			}
			if (interfaceState.chatboxInterfaceId != -1) {
				unloadInterface(interfaceState.chatboxInterfaceId);
				chatboxRedraw = true;
			}
			if (interfaceState.fullscreenInterfaceId != -1) {
				unloadInterface(interfaceState.fullscreenInterfaceId);
				gameScreenRedraw = true;
			}
			if (interfaceState.fullscreenOverlayInterfaceId != -1) {
				unloadInterface(interfaceState.fullscreenOverlayInterfaceId);
			}
			if (interfaceState.openInterfaceId != -1) {
				unloadInterface(interfaceState.openInterfaceId);
			}
			if (inputDialogState != 0) {
				inputDialogState = 0;
				chatboxRedraw = true;
			}
			interfaceActionPending = false;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 76) {
			lastPasswordChangeDate = networkSession.incoming.readUnsignedShortLE();
			networkSession.incoming.readUnsignedShortAddLE();
			networkSession.incoming.readUnsignedShort();
			networkSession.incoming.readUnsignedShort();
			accountCurrentDay = networkSession.incoming.readUnsignedShortLE();
			unreadMessageCount = networkSession.incoming.readUnsignedShortAdd();
			lastLoginDay = networkSession.incoming.readUnsignedShortAdd();
			membershipDays = networkSession.incoming.readUnsignedShort();
			lastLoginIp = networkSession.incoming.readIntLE();
			recoveryQuestionsDate = networkSession.incoming.readUnsignedShortAddLE();
			networkSession.incoming.readUnsignedByteAdd();
			Signlink.lookupDns(Ipv4Address.format(lastLoginIp));
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 63) {
			String serverMessage = networkSession.incoming.readString();
			if (serverMessage.endsWith(":tradereq:")) {
				String tradeRequester = serverMessage.substring(0, serverMessage.indexOf(":"));
				long tradeRequesterEncoded = Base37.encode(tradeRequester);
				boolean ignored = socialManager.isIgnored(tradeRequesterEncoded);
				if (!ignored && tutorialIslandFlag == 0)
					addChatMessage(tradeRequester, "wishes to trade with you.", 4);
			} else if (serverMessage.endsWith(":duelreq:")) {
				String duelRequester = serverMessage.substring(0, serverMessage.indexOf(":"));
				long duelRequesterEncoded = Base37.encode(duelRequester);
				boolean ignored = socialManager.isIgnored(duelRequesterEncoded);
				if (!ignored && tutorialIslandFlag == 0)
					addChatMessage(duelRequester, "wishes to duel with you.", 8);
			} else if (serverMessage.endsWith(":chalreq:")) {
				String challengeRequester = serverMessage.substring(0, serverMessage.indexOf(":"));
				long challengeRequesterEncoded = Base37.encode(challengeRequester);
				boolean ignored = socialManager.isIgnored(challengeRequesterEncoded);
				if (!ignored && tutorialIslandFlag == 0) {
					String challengeText = serverMessage.substring(serverMessage.indexOf(":") + 1,
							serverMessage.length() - 9);
					addChatMessage(challengeRequester, challengeText, 8);
				}
			} else {
				addChatMessage("", serverMessage, 0);
			}
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 50) {
			int walkableInterfaceId = networkSession.incoming.readSignedShort();
			if (walkableInterfaceId >= 0)
				widgetRuntime.resetAnimations(walkableInterfaceId);
			if (walkableInterfaceId != interfaceState.walkableInterfaceId) {
				unloadInterface(interfaceState.walkableInterfaceId);
				interfaceState.walkableInterfaceId = walkableInterfaceId;
			}
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 82) {
			boolean mouseoverTriggered = networkSession.incoming.readUnsignedByte() == 1;
			int widgetId6 = networkSession.incoming.readUnsignedShort();
			Widget.get(widgetId6).mouseoverTriggered = mouseoverTriggered;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 174) {
			if (interfaceState.selectedTab == 12)
				sidebarRedraw = true;
			weight = networkSession.incoming.readSignedShort();
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 233) {
			multiCombatZone = networkSession.incoming.readUnsignedByte();
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 61) {
			destinationX = 0;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 128) {
			int openInterfaceId = networkSession.incoming.readUnsignedShortAdd();
			int sidebarOverlayInterfaceId = networkSession.incoming.readUnsignedShortAddLE();
			if (interfaceState.chatboxInterfaceId != -1) {
				unloadInterface(interfaceState.chatboxInterfaceId);
				chatboxRedraw = true;
			}
			if (interfaceState.fullscreenInterfaceId != -1) {
				unloadInterface(interfaceState.fullscreenInterfaceId);
				gameScreenRedraw = true;
			}
			if (interfaceState.fullscreenOverlayInterfaceId != -1) {
				unloadInterface(interfaceState.fullscreenOverlayInterfaceId);
			}
			if (interfaceState.openInterfaceId != openInterfaceId) {
				unloadInterface(interfaceState.openInterfaceId);
				interfaceState.openInterfaceId = openInterfaceId;
			}
			if (interfaceState.sidebarOverlayInterfaceId != sidebarOverlayInterfaceId) {
				unloadInterface(interfaceState.sidebarOverlayInterfaceId);
				interfaceState.sidebarOverlayInterfaceId = sidebarOverlayInterfaceId;
			}
			if (inputDialogState != 0) {
				inputDialogState = 0;
				chatboxRedraw = true;
			}
			sidebarRedraw = true;
			tabAreaRedraw = true;
			interfaceActionPending = false;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 67) {
			int shakeIndex = networkSession.incoming.readUnsignedByte();
			int randomAmplitude = networkSession.incoming.readUnsignedByte();
			int sineAmplitude = networkSession.incoming.readUnsignedByte();
			int frequency = networkSession.incoming.readUnsignedByte();
			cameraController.configureShake(shakeIndex, randomAmplitude, sineAmplitude, frequency);
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 134) {
			sidebarRedraw = true;
			int widgetId7 = networkSession.incoming.readUnsignedShort();
			Widget inventoryWidget = Widget.get(widgetId7);
			while (networkSession.incoming.position < networkSession.incomingLength) {
				int slot = networkSession.incoming.readUnsignedSmart();
				int itemId = networkSession.incoming.readUnsignedShort();
				int amount = networkSession.incoming.readUnsignedByte();
				if (amount == 255)
					amount = networkSession.incoming.readInt();
				if (slot >= 0 && slot < inventoryWidget.itemIds.length) {
					inventoryWidget.itemIds[slot] = itemId;
					inventoryWidget.itemAmounts[slot] = amount;
				}
			}
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 78) {
			long encodedName = networkSession.incoming.readLong();
			int world = networkSession.incoming.readUnsignedByte();
			if (socialManager.updateFriend(encodedName, world, currentWorldId, this::addChatMessage))
				sidebarRedraw = true;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 58) {
			messagePromptRaised = false;
			inputDialogState = 1;
			inputDialogText = "";
			chatboxRedraw = true;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 252) {
			interfaceState.selectedTab = networkSession.incoming.readUnsignedByteNeg();
			sidebarRedraw = true;
			tabAreaRedraw = true;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 40) {
			int zoneBaseY = networkSession.incoming.readUnsignedByteSub();
			int zoneBaseX = networkSession.incoming.readUnsignedByteNeg();
			zoneUpdates.setZoneBase(zoneBaseX, zoneBaseY);
			worldState.clearZone(currentPlane, zoneBaseX, zoneBaseY);
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 255) {
			int widgetId8 = networkSession.incoming.readUnsignedShortAddLE();
			Widget.get(widgetId8).mediaType = 3;
			if (localPlayer.npcDefinition == null)
				Widget.get(widgetId8).mediaId = (localPlayer.bodyColors[0] << 25) + (localPlayer.bodyColors[4] << 20)
						+ (localPlayer.equipment[0] << 15) + (localPlayer.equipment[8] << 10)
						+ (localPlayer.equipment[11] << 5) + localPlayer.equipment[1];
			else
				Widget.get(widgetId8).mediaId = (int) (0x12345678L + localPlayer.npcDefinition.id);
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 135) {
			long senderEncodedName = networkSession.incoming.readLong();
			int privateMessageId = networkSession.incoming.readInt();
			int senderRights = networkSession.incoming.readUnsignedByte();
			boolean duplicateOrIgnored = chatHistory.hasRecentPrivateMessage(privateMessageId);

			if (senderRights <= 1 && socialManager.isIgnored(senderEncodedName))
				duplicateOrIgnored = true;
			if (!duplicateOrIgnored && tutorialIslandFlag == 0)
				try {
					chatHistory.rememberPrivateMessage(privateMessageId);
					String privateMessage = ChatCodec.decode(networkSession.incoming,
							networkSession.incomingLength - 13);
					if (senderRights != 3)
						privateMessage = Censor.censor(privateMessage);
					if (senderRights == 2 || senderRights == 3)
						addChatMessage("@cr2@" + TextFormatter.formatDisplayName(Base37.decode(senderEncodedName)),
								privateMessage, 7);
					else if (senderRights == 1)
						addChatMessage("@cr1@" + TextFormatter.formatDisplayName(Base37.decode(senderEncodedName)),
								privateMessage, 7);
					else
						addChatMessage(TextFormatter.formatDisplayName(Base37.decode(senderEncodedName)),
								privateMessage, 3);
				} catch (Exception exception1) {
					Signlink.reportError("cde1");
				}
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 183) {
			zoneUpdates.setZoneBase(networkSession.incoming.readUnsignedByte(),
					networkSession.incoming.readUnsignedByteAdd());
			while (networkSession.incoming.position < networkSession.incomingLength) {
				int updateType = networkSession.incoming.readUnsignedByte();
				zoneUpdates.decode(networkSession.incoming, updateType, currentPlane, gameCycle, localPlayerServerIndex,
						localPlayer, actorSynchronizer, this::queueAreaSound);
			}
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 159) {
			int openInterfaceId2 = networkSession.incoming.readUnsignedShortAddLE();
			widgetRuntime.resetAnimations(openInterfaceId2);
			if (interfaceState.sidebarOverlayInterfaceId != -1) {
				unloadInterface(interfaceState.sidebarOverlayInterfaceId);
				sidebarRedraw = true;
				tabAreaRedraw = true;
			}
			if (interfaceState.chatboxInterfaceId != -1) {
				unloadInterface(interfaceState.chatboxInterfaceId);
				chatboxRedraw = true;
			}
			if (interfaceState.fullscreenInterfaceId != -1) {
				unloadInterface(interfaceState.fullscreenInterfaceId);
				gameScreenRedraw = true;
			}
			if (interfaceState.fullscreenOverlayInterfaceId != -1) {
				unloadInterface(interfaceState.fullscreenOverlayInterfaceId);
			}
			if (interfaceState.openInterfaceId != openInterfaceId2) {
				unloadInterface(interfaceState.openInterfaceId);
				interfaceState.openInterfaceId = openInterfaceId2;
			}
			if (inputDialogState != 0) {
				inputDialogState = 0;
				chatboxRedraw = true;
			}
			interfaceActionPending = false;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 246) {
			int sidebarOverlayInterfaceId2 = networkSession.incoming.readUnsignedShortAddLE();
			widgetRuntime.resetAnimations(sidebarOverlayInterfaceId2);
			if (interfaceState.chatboxInterfaceId != -1) {
				unloadInterface(interfaceState.chatboxInterfaceId);
				chatboxRedraw = true;
			}
			if (interfaceState.fullscreenInterfaceId != -1) {
				unloadInterface(interfaceState.fullscreenInterfaceId);
				gameScreenRedraw = true;
			}
			if (interfaceState.fullscreenOverlayInterfaceId != -1) {
				unloadInterface(interfaceState.fullscreenOverlayInterfaceId);
			}
			if (interfaceState.openInterfaceId != -1) {
				unloadInterface(interfaceState.openInterfaceId);
			}
			if (interfaceState.sidebarOverlayInterfaceId != sidebarOverlayInterfaceId2) {
				unloadInterface(interfaceState.sidebarOverlayInterfaceId);
				interfaceState.sidebarOverlayInterfaceId = sidebarOverlayInterfaceId2;
			}
			if (inputDialogState != 0) {
				inputDialogState = 0;
				chatboxRedraw = true;
			}
			sidebarRedraw = true;
			tabAreaRedraw = true;
			interfaceActionPending = false;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 49) {
			sidebarRedraw = true;
			int skillId = networkSession.incoming.readUnsignedByteNeg();
			int currentLevel = networkSession.incoming.readUnsignedByte();
			int experience = networkSession.incoming.readInt();
			skillExperiences[skillId] = experience;
			currentSkillLevels[skillId] = currentLevel;
			baseSkillLevels[skillId] = 1;
			for (int levelIndex = 0; levelIndex < 98; levelIndex++)
				if (experience >= experienceTable[levelIndex])
					baseSkillLevels[skillId] = levelIndex + 2;

			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 206) {
			sidebarRedraw = true;
			int widgetId9 = networkSession.incoming.readUnsignedShort();
			Widget inventoryWidget2 = Widget.get(widgetId9);
			int itemCount = networkSession.incoming.readUnsignedShort();
			for (int slot2 = 0; slot2 < itemCount; slot2++) {
				inventoryWidget2.itemIds[slot2] = networkSession.incoming.readUnsignedShortAddLE();
				int amount2 = networkSession.incoming.readUnsignedByteNeg();
				if (amount2 == 255)
					amount2 = networkSession.incoming.readIntLE();
				inventoryWidget2.itemAmounts[slot2] = amount2;
			}

			for (int slot3 = itemCount; slot3 < inventoryWidget2.itemIds.length; slot3++) {
				inventoryWidget2.itemIds[slot3] = 0;
				inventoryWidget2.itemAmounts[slot3] = 0;
			}

			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 222 || networkSession.incomingOpcode == 53) {
			RegionManager.RegionShift shift = regionManager.decodeRebuild(networkSession.incoming,
					networkSession.incomingOpcode, onDemandFetcher, actorSynchronizer, worldState, destinationX,
					destinationY);
			if (shift.changed) {
				destinationX = shift.destinationX;
				destinationY = shift.destinationY;
				cameraController.cinematic = false;
				drawGameLoadingMessage(null, "Loading - please wait.");
			}
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 190) {
			systemUpdateTimer = networkSession.incoming.readUnsignedShortLE() * 30;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 41 || networkSession.incomingOpcode == 121
				|| networkSession.incomingOpcode == 203 || networkSession.incomingOpcode == 106
				|| networkSession.incomingOpcode == 59 || networkSession.incomingOpcode == 181
				|| networkSession.incomingOpcode == 208 || networkSession.incomingOpcode == 107
				|| networkSession.incomingOpcode == 142 || networkSession.incomingOpcode == 88
				|| networkSession.incomingOpcode == 152) {
			zoneUpdates.decode(networkSession.incoming, networkSession.incomingOpcode, currentPlane, gameCycle,
					localPlayerServerIndex, localPlayer, actorSynchronizer, this::queueAreaSound);
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 125) {
			if (interfaceState.selectedTab == 12)
				sidebarRedraw = true;
			runEnergy = networkSession.incoming.readUnsignedByte();
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 21) {
			int zoomDivisor = networkSession.incoming.readUnsignedShort();
			int itemId2 = networkSession.incoming.readUnsignedShortLE();
			int widgetId10 = networkSession.incoming.readUnsignedShortAddLE();
			if (itemId2 == 65535) {
				Widget.get(widgetId10).mediaType = 0;
				networkSession.incomingOpcode = -1;
				return true;
			} else {
				ItemDefinition itemDefinition = ItemDefinition.lookup(itemId2);
				Widget.get(widgetId10).mediaType = 4;
				Widget.get(widgetId10).mediaId = itemId2;
				Widget.get(widgetId10).modelPitch = itemDefinition.xan2d;
				Widget.get(widgetId10).modelYaw = itemDefinition.yan2d;
				Widget.get(widgetId10).modelZoom = (itemDefinition.zoom2d * 100) / zoomDivisor;
				networkSession.incomingOpcode = -1;
				return true;
			}
		}
		if (networkSession.incomingOpcode == 3) {
			int tileX = networkSession.incoming.readUnsignedByte();
			int tileY = networkSession.incoming.readUnsignedByte();
			int heightOffset = networkSession.incoming.readUnsignedShort();
			int baseSpeed = networkSession.incoming.readUnsignedByte();
			int scale = networkSession.incoming.readUnsignedByte();
			cameraController.setCinematicPosition(tileX, tileY, heightOffset, baseSpeed, scale, worldState,
					currentPlane);
			networkSession.incomingOpcode = -1;
			return true;
		}

		if (networkSession.incomingOpcode == 2) {
			int widgetId11 = networkSession.incoming.readUnsignedShortAddLE();
			int animationId = networkSession.incoming.readShortAdd();
			Widget animationWidget = Widget.get(widgetId11);
			if (animationWidget.animationId != animationId || animationId == -1) {
				animationWidget.animationId = animationId;
				animationWidget.animationFrame = 0;
				animationWidget.animationCycle = 0;
			}
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 71) {
			actorSynchronizer.decodeNpcUpdate(networkSession.incoming, networkSession.incomingLength, gameCycle,
					loginScreen.username);
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 226) {
			socialManager.replaceIgnoreList(networkSession.incoming, networkSession.incomingLength);
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 10) {
			int tabIndex = networkSession.incoming.readUnsignedByteSub();
			int interfaceId = networkSession.incoming.readUnsignedShortAdd();
			if (interfaceId == 65535)
				interfaceId = -1;
			if (interfaceState.tabInterfaceIds[tabIndex] != interfaceId) {
				unloadInterface(interfaceState.tabInterfaceIds[tabIndex]);
				interfaceState.tabInterfaceIds[tabIndex] = interfaceId;
			}
			sidebarRedraw = true;
			tabAreaRedraw = true;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 219) {
			int widgetId12 = networkSession.incoming.readUnsignedShortLE();
			Widget inventoryWidget3 = Widget.get(widgetId12);
			for (int slot4 = 0; slot4 < inventoryWidget3.itemIds.length; slot4++) {
				inventoryWidget3.itemIds[slot4] = -1;
				inventoryWidget3.itemIds[slot4] = 0;
			}

			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 238) {
			interfaceState.flashingTab = networkSession.incoming.readUnsignedByte();
			if (interfaceState.flashingTab == interfaceState.selectedTab) {
				if (interfaceState.flashingTab == 3)
					interfaceState.selectedTab = 1;
				else
					sidebarRedraw = true;
			}
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 148) {
			cameraController.stopCinematic();
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 126) {
			accountMembershipStatus = networkSession.incoming.readUnsignedByte();
			localPlayerServerIndex = networkSession.incoming.readUnsignedShortLE();
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 75) {
			zoneUpdates.setZoneBase(networkSession.incoming.readUnsignedByteNeg(),
					networkSession.incoming.readUnsignedByteAdd());
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 253) {
			int fullscreenOverlayInterfaceId = networkSession.incoming.readUnsignedShortLE();
			int fullscreenInterfaceId = networkSession.incoming.readUnsignedShortAdd();
			widgetRuntime.resetAnimations(fullscreenInterfaceId);
			if (fullscreenOverlayInterfaceId != -1)
				widgetRuntime.resetAnimations(fullscreenOverlayInterfaceId);
			if (interfaceState.openInterfaceId != -1) {
				unloadInterface(interfaceState.openInterfaceId);
			}
			if (interfaceState.sidebarOverlayInterfaceId != -1) {
				unloadInterface(interfaceState.sidebarOverlayInterfaceId);
			}
			if (interfaceState.chatboxInterfaceId != -1) {
				unloadInterface(interfaceState.chatboxInterfaceId);
			}
			if (interfaceState.fullscreenInterfaceId != fullscreenInterfaceId) {
				unloadInterface(interfaceState.fullscreenInterfaceId);
				interfaceState.fullscreenInterfaceId = fullscreenInterfaceId;
			}
			if (interfaceState.fullscreenOverlayInterfaceId != fullscreenInterfaceId) {
				unloadInterface(interfaceState.fullscreenOverlayInterfaceId);
				interfaceState.fullscreenOverlayInterfaceId = fullscreenOverlayInterfaceId;
			}
			inputDialogState = 0;
			interfaceActionPending = false;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 251) {
			socialManager.friendListStatus = networkSession.incoming.readUnsignedByte();
			sidebarRedraw = true;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 18) {
			int pitchRotationSpeed = networkSession.incoming.readUnsignedShort();
			int widgetId13 = networkSession.incoming.readUnsignedShortAdd();
			int yawRotationSpeed = networkSession.incoming.readUnsignedShortLE();
			Widget.get(widgetId13).modelRotationSpeed = (pitchRotationSpeed << 16) + yawRotationSpeed;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 90) {
			currentPlane = actorSynchronizer.decodePlayerUpdate(networkSession.incoming, networkSession.incomingLength,
					gameCycle, currentPlane, loginScreen.username, chatBuffer, actorChatHandler);
			regionManager.playerUpdateReceived();
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 113) {
			for (int varpId3 = 0; varpId3 < varpValues.length; varpId3++)
				if (varpValues[varpId3] != varpShadowValues[varpId3]) {
					varpValues[varpId3] = varpShadowValues[varpId3];
					applyVarp(varpId3);
					sidebarRedraw = true;
				}

			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 232) {
			int widgetId14 = networkSession.incoming.readUnsignedShortAddLE();
			String widgetText = networkSession.incoming.readString();
			Widget.get(widgetId14).text = widgetText;
			if (Widget.get(widgetId14).parentId == interfaceState.tabInterfaceIds[interfaceState.selectedTab])
				sidebarRedraw = true;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 200) {
			int widgetId15 = networkSession.incoming.readUnsignedShort();
			int scrollY = networkSession.incoming.readUnsignedShortAddLE();
			Widget scrollWidget = Widget.get(widgetId15);
			if (scrollWidget != null && scrollWidget.type == 0) {
				if (scrollY < 0)
					scrollY = 0;
				if (scrollY > scrollWidget.scrollHeight - scrollWidget.height)
					scrollY = scrollWidget.scrollHeight - scrollWidget.height;
				scrollWidget.scrollY = scrollY;
			}
			networkSession.incomingOpcode = -1;
			return true;
		}
		Signlink.reportError("T1 - " + networkSession.incomingOpcode + "," + networkSession.incomingLength + " - "
				+ networkSession.secondLastOpcode + "," + networkSession.thirdLastOpcode);
		logout();
		return true;
	}

	/**
	 * Draws the contextual action tooltip shown when the context menu is closed.
	 */
	public void drawMenuTooltip() {
		if (menuState.count < 2 && interfaceState.itemSelected == 0 && interfaceState.spellSelected == 0)
			return;
		String tooltip;
		if (interfaceState.itemSelected == 1 && menuState.count < 2)
			tooltip = "Use " + interfaceState.selectedItemName + " with...";
		else if (interfaceState.spellSelected == 1 && menuState.count < 2)
			tooltip = interfaceState.selectedSpellAction + "...";
		else
			tooltip = menuState.actionNames[menuState.count - 1];
		if (menuState.count > 2)
			tooltip = tooltip + "@whi@ / " + (menuState.count - 2) + " more options";
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
	 */
	public static void setServerHost(String host) {
		if (host == null || host.trim().isEmpty()) {
			throw new IllegalArgumentException("server host must not be blank");
		}
		serverHost = host.trim();
	}

	/**
	 * Adds context-menu actions for a player at the supplied scene tile.
	 *
	 * @param playerIndex the player index
	 * @param tileY       the local scene-tile Y coordinate
	 * @param tileX       the local scene-tile X coordinate
	 * @param player      the target player
	 */
	public void buildPlayerMenu(int playerIndex, int tileY, int tileX, Player player) {
		if (player == localPlayer)
			return;
		if (menuState.count >= 400)
			return;
		String displayName;
		if (player.skillLevel == 0)
			displayName = player.name + getCombatLevelColorTag(player.combatLevel, localPlayer.combatLevel) + " (level-"
					+ player.combatLevel + ")";
		else
			displayName = player.name + " (skill-" + player.skillLevel + ")";
		if (interfaceState.itemSelected == 1) {
			menuState.actionNames[menuState.count] = "Use " + interfaceState.selectedItemName + " with @whi@"
					+ displayName;
			menuState.actionIds[menuState.count] = 596;
			menuState.actionCmd1[menuState.count] = playerIndex;
			menuState.actionCmd2[menuState.count] = tileX;
			menuState.actionCmd3[menuState.count] = tileY;
			menuState.count++;
		} else if (interfaceState.spellSelected == 1) {
			if ((interfaceState.selectedSpellTargetMask & 8) == 8) {
				menuState.actionNames[menuState.count] = interfaceState.selectedSpellAction + " @whi@" + displayName;
				menuState.actionIds[menuState.count] = 918;
				menuState.actionCmd1[menuState.count] = playerIndex;
				menuState.actionCmd2[menuState.count] = tileX;
				menuState.actionCmd3[menuState.count] = tileY;
				menuState.count++;
			}
		} else {
			for (int actionIndex = 4; actionIndex >= 0; actionIndex--)
				if (playerActions[actionIndex] != null) {
					menuState.actionNames[menuState.count] = playerActions[actionIndex] + " @whi@" + displayName;
					char priorityOffset = '\0';
					if (playerActions[actionIndex].equalsIgnoreCase("attack")) {
						if (player.combatLevel > localPlayer.combatLevel)
							priorityOffset = '\u07D0';
						if (localPlayer.team != 0 && player.team != 0)
							if (localPlayer.team == player.team)
								priorityOffset = '\u07D0';
							else
								priorityOffset = '\0';
					} else if (playerActionLowPriority[actionIndex])
						priorityOffset = '\u07D0';
					if (actionIndex == 0)
						menuState.actionIds[menuState.count] = 200 + priorityOffset;
					if (actionIndex == 1)
						menuState.actionIds[menuState.count] = 493 + priorityOffset;
					if (actionIndex == 2)
						menuState.actionIds[menuState.count] = 408 + priorityOffset;
					if (actionIndex == 3)
						menuState.actionIds[menuState.count] = 677 + priorityOffset;
					if (actionIndex == 4)
						menuState.actionIds[menuState.count] = 876 + priorityOffset;
					menuState.actionCmd1[menuState.count] = playerIndex;
					menuState.actionCmd2[menuState.count] = tileX;
					menuState.actionCmd3[menuState.count] = tileY;
					menuState.count++;
				}

		}
		for (int menuIndex = 0; menuIndex < menuState.count; menuIndex++)
			if (menuState.actionIds[menuIndex] == 14) {
				menuState.actionNames[menuIndex] = "Walk here @whi@" + displayName;
				return;
			}

	}

	/**
	 * Processes scrollbar arrow, track, and drag input for a widget.
	 *
	 * @param scrollHeight the full scrollable content height
	 * @param y            the Y coordinate
	 * @param widget       the widget being processed
	 * @param mouseY       the mouse Y coordinate
	 * @param redrawArea   the redraw area
	 * @param mouseX       the mouse X coordinate
	 * @param height       the visible height
	 * @param x            the X coordinate
	 */
	public void handleScrollbarInput(int scrollHeight, int y, Widget widget, int mouseY, int redrawArea, int mouseX,
			int height, int x) {
		if (scrollbarDragging)
			scrollbarDragPadding = 32;
		else
			scrollbarDragPadding = 0;
		scrollbarDragging = false;
		if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
			widget.scrollY -= mouseButtonHoldTicks * 4;
			if (redrawArea == 1)
				sidebarRedraw = true;
			if (redrawArea == 2 || redrawArea == 3)
				chatboxRedraw = true;
			return;
		}
		if (mouseX >= x && mouseX < x + 16 && mouseY >= (y + height) - 16 && mouseY < y + height) {
			widget.scrollY += mouseButtonHoldTicks * 4;
			if (redrawArea == 1)
				sidebarRedraw = true;
			if (redrawArea == 2 || redrawArea == 3)
				chatboxRedraw = true;
			return;
		}
		if (mouseX >= x - scrollbarDragPadding && mouseX < x + 16 + scrollbarDragPadding && mouseY >= y + 16
				&& mouseY < (y + height) - 16 && mouseButtonHoldTicks > 0) {
			int thumbHeight = ((height - 32) * height) / scrollHeight;
			if (thumbHeight < 8)
				thumbHeight = 8;
			int dragOffset = mouseY - y - 16 - thumbHeight / 2;
			int dragRange = height - 32 - thumbHeight;
			widget.scrollY = ((scrollHeight - height) * dragOffset) / dragRange;
			if (redrawArea == 1)
				sidebarRedraw = true;
			if (redrawArea == 2 || redrawArea == 3)
				chatboxRedraw = true;
			scrollbarDragging = true;
		}
	}

	/**
	 * Builds world-view menu entries from the scene picking results.
	 */
	public void buildViewportMenu() {
		if (interfaceState.itemSelected == 0 && interfaceState.spellSelected == 0) {
			menuState.actionNames[menuState.count] = "Walk here";
			menuState.actionIds[menuState.count] = 14;
			menuState.actionCmd2[menuState.count] = super.mouseX;
			menuState.actionCmd3[menuState.count] = super.mouseY;
			menuState.count++;
		}
		int previousUid = -1;
		for (int pickedIndex = 0; pickedIndex < Model.pickedCount; pickedIndex++) {
			int packedUid = Model.pickedUids[pickedIndex];
			int tileX = packedUid & 0x7f;
			int tileY = packedUid >> 7 & 0x7f;
			int entityType = packedUid >> 29 & 3;
			int entityId = packedUid >> 14 & 0x7fff;
			if (packedUid == previousUid)
				continue;
			previousUid = packedUid;
			if (entityType == 2 && worldState.scene.getConfig(currentPlane, tileX, tileY, packedUid) >= 0) {
				GameObjectDefinition objectDefinition = GameObjectDefinition.lookup(entityId);
				if (objectDefinition.morphIds != null)
					objectDefinition = objectDefinition.transform();
				if (objectDefinition == null)
					continue;
				if (interfaceState.itemSelected == 1) {
					menuState.actionNames[menuState.count] = "Use " + interfaceState.selectedItemName + " with @cya@"
							+ objectDefinition.name;
					menuState.actionIds[menuState.count] = 467;
					menuState.actionCmd1[menuState.count] = packedUid;
					menuState.actionCmd2[menuState.count] = tileX;
					menuState.actionCmd3[menuState.count] = tileY;
					menuState.count++;
				} else if (interfaceState.spellSelected == 1) {
					if ((interfaceState.selectedSpellTargetMask & 4) == 4) {
						menuState.actionNames[menuState.count] = interfaceState.selectedSpellAction + " @cya@"
								+ objectDefinition.name;
						menuState.actionIds[menuState.count] = 376;
						menuState.actionCmd1[menuState.count] = packedUid;
						menuState.actionCmd2[menuState.count] = tileX;
						menuState.actionCmd3[menuState.count] = tileY;
						menuState.count++;
					}
				} else {
					if (objectDefinition.actions != null) {
						for (int objectActionIndex = 4; objectActionIndex >= 0; objectActionIndex--)
							if (objectDefinition.actions[objectActionIndex] != null) {
								menuState.actionNames[menuState.count] = objectDefinition.actions[objectActionIndex]
										+ " @cya@" + objectDefinition.name;
								if (objectActionIndex == 0)
									menuState.actionIds[menuState.count] = 35;
								if (objectActionIndex == 1)
									menuState.actionIds[menuState.count] = 389;
								if (objectActionIndex == 2)
									menuState.actionIds[menuState.count] = 888;
								if (objectActionIndex == 3)
									menuState.actionIds[menuState.count] = 892;
								if (objectActionIndex == 4)
									menuState.actionIds[menuState.count] = 1280;
								menuState.actionCmd1[menuState.count] = packedUid;
								menuState.actionCmd2[menuState.count] = tileX;
								menuState.actionCmd3[menuState.count] = tileY;
								menuState.count++;
							}

					}
					menuState.actionNames[menuState.count] = "Examine @cya@" + objectDefinition.name;
					menuState.actionIds[menuState.count] = 1412;
					menuState.actionCmd1[menuState.count] = objectDefinition.id << 14;
					menuState.actionCmd2[menuState.count] = tileX;
					menuState.actionCmd3[menuState.count] = tileY;
					menuState.count++;
				}
			}
			if (entityType == 1) {
				Npc npc = actorSynchronizer.npcs[entityId];
				if (npc.definition.size == 1 && (((Actor) (npc)).x & 0x7f) == 64 && (((Actor) (npc)).y & 0x7f) == 64) {
					for (int activeNpcIndex = 0; activeNpcIndex < actorSynchronizer.npcCount; activeNpcIndex++) {
						Npc stackedNpc = actorSynchronizer.npcs[actorSynchronizer.npcIndices[activeNpcIndex]];
						if (stackedNpc != null && stackedNpc != npc && stackedNpc.definition.size == 1
								&& ((Actor) (stackedNpc)).x == ((Actor) (npc)).x
								&& ((Actor) (stackedNpc)).y == ((Actor) (npc)).y)
							buildNpcMenu(stackedNpc.definition, tileY, tileX,
									actorSynchronizer.npcIndices[activeNpcIndex]);
					}

					for (int activePlayerIndex = 0; activePlayerIndex < actorSynchronizer.playerCount; activePlayerIndex++) {
						Player stackedPlayer = actorSynchronizer.players[actorSynchronizer.playerIndices[activePlayerIndex]];
						if (stackedPlayer != null && ((Actor) (stackedPlayer)).x == ((Actor) (npc)).x
								&& ((Actor) (stackedPlayer)).y == ((Actor) (npc)).y)
							buildPlayerMenu(actorSynchronizer.playerIndices[activePlayerIndex], tileY, tileX,
									stackedPlayer);
					}

				}
				buildNpcMenu(npc.definition, tileY, tileX, entityId);
			}
			if (entityType == 0) {
				Player player = actorSynchronizer.players[entityId];
				if ((((Actor) (player)).x & 0x7f) == 64 && (((Actor) (player)).y & 0x7f) == 64) {
					for (int activeNpcIndex2 = 0; activeNpcIndex2 < actorSynchronizer.npcCount; activeNpcIndex2++) {
						Npc stackedNpc2 = actorSynchronizer.npcs[actorSynchronizer.npcIndices[activeNpcIndex2]];
						if (stackedNpc2 != null && stackedNpc2.definition.size == 1
								&& ((Actor) (stackedNpc2)).x == ((Actor) (player)).x
								&& ((Actor) (stackedNpc2)).y == ((Actor) (player)).y)
							buildNpcMenu(stackedNpc2.definition, tileY, tileX,
									actorSynchronizer.npcIndices[activeNpcIndex2]);
					}

					for (int activePlayerIndex2 = 0; activePlayerIndex2 < actorSynchronizer.playerCount; activePlayerIndex2++) {
						Player stackedPlayer2 = actorSynchronizer.players[actorSynchronizer.playerIndices[activePlayerIndex2]];
						if (stackedPlayer2 != null && stackedPlayer2 != player
								&& ((Actor) (stackedPlayer2)).x == ((Actor) (player)).x
								&& ((Actor) (stackedPlayer2)).y == ((Actor) (player)).y)
							buildPlayerMenu(actorSynchronizer.playerIndices[activePlayerIndex2], tileY, tileX,
									stackedPlayer2);
					}

				}
				buildPlayerMenu(entityId, tileY, tileX, player);
			}
			if (entityType == 3) {
				NodeDeque groundItems = worldState.groundItems[currentPlane][tileX][tileY];
				if (groundItems != null) {
					for (GroundItem groundItem = (GroundItem) groundItems
							.last(); groundItem != null; groundItem = (GroundItem) groundItems.previous()) {
						ItemDefinition itemDefinition = ItemDefinition.lookup(groundItem.id);
						if (interfaceState.itemSelected == 1) {
							menuState.actionNames[menuState.count] = "Use " + interfaceState.selectedItemName
									+ " with @lre@" + itemDefinition.name;
							menuState.actionIds[menuState.count] = 100;
							menuState.actionCmd1[menuState.count] = groundItem.id;
							menuState.actionCmd2[menuState.count] = tileX;
							menuState.actionCmd3[menuState.count] = tileY;
							menuState.count++;
						} else if (interfaceState.spellSelected == 1) {
							if ((interfaceState.selectedSpellTargetMask & 1) == 1) {
								menuState.actionNames[menuState.count] = interfaceState.selectedSpellAction + " @lre@"
										+ itemDefinition.name;
								menuState.actionIds[menuState.count] = 199;
								menuState.actionCmd1[menuState.count] = groundItem.id;
								menuState.actionCmd2[menuState.count] = tileX;
								menuState.actionCmd3[menuState.count] = tileY;
								menuState.count++;
							}
						} else {
							for (int groundActionIndex = 4; groundActionIndex >= 0; groundActionIndex--)
								if (itemDefinition.groundActions != null
										&& itemDefinition.groundActions[groundActionIndex] != null) {
									menuState.actionNames[menuState.count] = itemDefinition.groundActions[groundActionIndex]
											+ " @lre@" + itemDefinition.name;
									if (groundActionIndex == 0)
										menuState.actionIds[menuState.count] = 68;
									if (groundActionIndex == 1)
										menuState.actionIds[menuState.count] = 26;
									if (groundActionIndex == 2)
										menuState.actionIds[menuState.count] = 684;
									if (groundActionIndex == 3)
										menuState.actionIds[menuState.count] = 930;
									if (groundActionIndex == 4)
										menuState.actionIds[menuState.count] = 270;
									menuState.actionCmd1[menuState.count] = groundItem.id;
									menuState.actionCmd2[menuState.count] = tileX;
									menuState.actionCmd3[menuState.count] = tileY;
									menuState.count++;
								} else if (groundActionIndex == 2) {
									menuState.actionNames[menuState.count] = "Take @lre@" + itemDefinition.name;
									menuState.actionIds[menuState.count] = 684;
									menuState.actionCmd1[menuState.count] = groundItem.id;
									menuState.actionCmd2[menuState.count] = tileX;
									menuState.actionCmd3[menuState.count] = tileY;
									menuState.count++;
								}

							menuState.actionNames[menuState.count] = "Examine @lre@" + itemDefinition.name;
							menuState.actionIds[menuState.count] = 1564;
							menuState.actionCmd1[menuState.count] = groundItem.id;
							menuState.actionCmd2[menuState.count] = tileX;
							menuState.actionCmd3[menuState.count] = tileY;
							menuState.count++;
						}
					}

				}
			}
		}

	}

	/**
	 * Releases model resources held by the widgets in an interface group.
	 *
	 * @param interfaceId the interface group identifier
	 */
	public void unloadInterface(int interfaceId) {
		Widget.unloadGroup(interfaceId);
	}

	/**
	 * Adds a message to the fixed chat history and requests the appropriate redraw.
	 *
	 * @param sender  the message sender name
	 * @param message the message text
	 * @param type    the chat message type
	 */
	public void addChatMessage(String sender, String message, int type) {
		if (type == 0 && interfaceState.dialogueInterfaceId != -1) {
			clickToContinueMessage = message;
			super.clickButton = 0;
		}
		if (interfaceState.chatboxInterfaceId == -1)
			chatboxRedraw = true;
		chatHistory.add(sender, message, type);
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
	 * Loads title-screen sprites, initializes flame palettes/noise, and starts the
	 * flame thread.
	 */
	public void initializeTitleScreen() {
		titleBoxImage = new IndexedImage(titleArchive, "titlebox", 0);
		titleButtonImage = new IndexedImage(titleArchive, "titlebutton", 0);
		titleRunes = new IndexedImage[12];
		for (int assetIndex = 0; assetIndex < 12; assetIndex++)
			titleRunes[assetIndex] = new IndexedImage(titleArchive, "runes", assetIndex);

		titleLeftFlameBackground = new ImageRGB(128, 265);
		titleRightFlameBackground = new ImageRGB(128, 265);
		for (int pixelIndex = 0; pixelIndex < 33920; pixelIndex++)
			titleLeftFlameBackground.pixels[pixelIndex] = titleLeftFlameBuffer.pixels[pixelIndex];

		for (int pixelIndex2 = 0; pixelIndex2 < 33920; pixelIndex2++)
			titleRightFlameBackground.pixels[pixelIndex2] = titleRightFlameBuffer.pixels[pixelIndex2];

		titleFlameRedPalette = new int[256];
		for (int paletteStep = 0; paletteStep < 64; paletteStep++)
			titleFlameRedPalette[paletteStep] = paletteStep * 0x40000;

		for (int paletteStep = 0; paletteStep < 64; paletteStep++)
			titleFlameRedPalette[paletteStep + 64] = 0xff0000 + 1024 * paletteStep;

		for (int paletteStep = 0; paletteStep < 64; paletteStep++)
			titleFlameRedPalette[paletteStep + 128] = 0xffff00 + 4 * paletteStep;

		for (int paletteStep = 0; paletteStep < 64; paletteStep++)
			titleFlameRedPalette[paletteStep + 192] = 0xffffff;

		titleFlameGreenPalette = new int[256];
		for (int paletteStep = 0; paletteStep < 64; paletteStep++)
			titleFlameGreenPalette[paletteStep] = paletteStep * 1024;

		for (int paletteStep = 0; paletteStep < 64; paletteStep++)
			titleFlameGreenPalette[paletteStep + 64] = 65280 + 4 * paletteStep;

		for (int paletteStep = 0; paletteStep < 64; paletteStep++)
			titleFlameGreenPalette[paletteStep + 128] = 65535 + 0x40000 * paletteStep;

		for (int paletteStep = 0; paletteStep < 64; paletteStep++)
			titleFlameGreenPalette[paletteStep + 192] = 0xffffff;

		titleFlameBluePalette = new int[256];
		for (int paletteStep = 0; paletteStep < 64; paletteStep++)
			titleFlameBluePalette[paletteStep] = paletteStep * 4;

		for (int paletteStep = 0; paletteStep < 64; paletteStep++)
			titleFlameBluePalette[paletteStep + 64] = 255 + 0x40000 * paletteStep;

		for (int paletteStep = 0; paletteStep < 64; paletteStep++)
			titleFlameBluePalette[paletteStep + 128] = 0xff00ff + 1024 * paletteStep;

		for (int paletteStep = 0; paletteStep < 64; paletteStep++)
			titleFlameBluePalette[paletteStep + 192] = 0xffffff;

		titleFlamePalette = new int[256];
		titleFlameNoise = new int[32768];
		titleFlameNoiseScratch = new int[32768];
		initializeTitleFlameNoise(null);
		titleFlameIntensity = new int[32768];
		titleFlameIntensityScratch = new int[32768];
		drawLoadingText(10, "Connecting to fileserver");
		if (!titleFlamesRunning) {
			titleFlameThreadMode = true;
			titleFlamesRunning = true;
			Thread thread = new Thread(this, "rs2-title-flame");
			thread.setDaemon(true);
			titleFlameThread = thread;
			thread.start();
			thread.setPriority(2);
		}
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

	/**
	 * Processes mouse interaction with either the open context menu or the default
	 * menu action.
	 */
	public void processMenuClick() {
		if (interfaceState.inventoryDragArea != 0)
			return;
		int clickButton = super.clickButton;
		if (interfaceState.spellSelected == 1 && super.clickX >= 516 && super.clickY >= 160 && super.clickX <= 765
				&& super.clickY <= 205)
			clickButton = 0;
		if (menuState.open) {
			if (clickButton != 1) {
				int menuMouseX = super.mouseX;
				int menuMouseY = super.mouseY;
				if (menuState.screenArea == 0) {
					menuMouseX -= 4;
					menuMouseY -= 4;
				}
				if (menuState.screenArea == 1) {
					menuMouseX -= 553;
					menuMouseY -= 205;
				}
				if (menuState.screenArea == 2) {
					menuMouseX -= 17;
					menuMouseY -= 357;
				}
				if (menuMouseX < menuState.offsetX - 10 || menuMouseX > menuState.offsetX + menuState.width + 10
						|| menuMouseY < menuState.offsetY - 10
						|| menuMouseY > menuState.offsetY + menuState.height + 10) {
					menuState.open = false;
					if (menuState.screenArea == 1)
						sidebarRedraw = true;
					if (menuState.screenArea == 2)
						chatboxRedraw = true;
				}
			}
			if (clickButton == 1) {
				int menuX = menuState.offsetX;
				int menuY = menuState.offsetY;
				int menuWidth = menuState.width;
				int clickX = super.clickX;
				int clickY = super.clickY;
				if (menuState.screenArea == 0) {
					clickX -= 4;
					clickY -= 4;
				}
				if (menuState.screenArea == 1) {
					clickX -= 553;
					clickY -= 205;
				}
				if (menuState.screenArea == 2) {
					clickX -= 17;
					clickY -= 357;
				}
				int selectedEntry = -1;
				for (int entryIndex = 0; entryIndex < menuState.count; entryIndex++) {
					int entryY = menuY + 31 + (menuState.count - 1 - entryIndex) * 15;
					if (clickX > menuX && clickX < menuX + menuWidth && clickY > entryY - 13 && clickY < entryY + 3)
						selectedEntry = entryIndex;
				}

				if (selectedEntry != -1)
					dispatchMenuAction(selectedEntry);
				menuState.open = false;
				if (menuState.screenArea == 1)
					sidebarRedraw = true;
				if (menuState.screenArea == 2) {
					chatboxRedraw = true;
					return;
				}
			}
		} else {
			if (clickButton == 1 && menuState.count > 0) {
				int actionId = menuState.actionIds[menuState.count - 1];
				if (actionId == 9 || actionId == 225 || actionId == 444 || actionId == 564 || actionId == 894
						|| actionId == 961 || actionId == 399 || actionId == 324 || actionId == 227 || actionId == 891
						|| actionId == 52 || actionId == 1094) {
					int slot = menuState.actionCmd2[menuState.count - 1];
					int widgetId = menuState.actionCmd3[menuState.count - 1];
					Widget inventoryWidget = Widget.get(widgetId);
					if (inventoryWidget.inventoryAllowSwap || inventoryWidget.inventoryReplaceItems) {
						inventoryDragMoved = false;
						interfaceState.inventoryDragDuration = 0;
						interfaceState.draggedInventoryWidgetId = widgetId;
						interfaceState.draggedInventorySlot = slot;
						interfaceState.inventoryDragArea = 2;
						interfaceState.inventoryDragStartX = super.clickX;
						interfaceState.inventoryDragStartY = super.clickY;
						if (Widget.get(widgetId).parentId == interfaceState.openInterfaceId)
							interfaceState.inventoryDragArea = 1;
						if (Widget.get(widgetId).parentId == interfaceState.chatboxInterfaceId)
							interfaceState.inventoryDragArea = 3;
						return;
					}
				}
			}
			if (clickButton == 1 && (oneButtonMouseMode == 1 || isAddFriendMenuAction(menuState.count - 1))
					&& menuState.count > 2)
				clickButton = 2;
			if (clickButton == 1 && menuState.count > 0)
				dispatchMenuAction(menuState.count - 1);
			if (clickButton == 2 && menuState.count > 0)
				openContextMenu();
		}
	}

	/**
	 * Draws the original fixed-width scrollbar for a scrollable widget.
	 *
	 * @param scrollY      the current scroll offset
	 * @param x            the X coordinate
	 * @param height       the visible height
	 * @param scrollHeight the full scrollable content height
	 * @param y            the Y coordinate
	 */
	public void drawScrollbar(int scrollY, int x, int height, int scrollHeight, int y) {
		scrollbarTop.draw(x, y);
		scrollbarBottom.draw(x, (y + height) - 16);
		Rasterizer.drawFilledRectangle(x, y + 16, 16, height - 32, scrollbarTrackColor);
		int thumbHeight = ((height - 32) * height) / scrollHeight;
		if (thumbHeight < 8)
			thumbHeight = 8;
		int thumbY = ((height - 32 - thumbHeight) * scrollY) / (scrollHeight - height);
		Rasterizer.drawFilledRectangle(x, y + 16 + thumbY, 16, thumbHeight, scrollbarThumbColor);
		Rasterizer.drawVerticalLine(x, y + 16 + thumbY, thumbHeight, scrollbarHighlightColor);
		Rasterizer.drawVerticalLine(x + 1, y + 16 + thumbY, thumbHeight, scrollbarHighlightColor);
		Rasterizer.drawHorizontalLine(x, y + 16 + thumbY, 16, scrollbarHighlightColor);
		Rasterizer.drawHorizontalLine(x, y + 17 + thumbY, 16, scrollbarHighlightColor);
		Rasterizer.drawVerticalLine(x + 15, y + 16 + thumbY, thumbHeight, scrollbarShadowColor);
		Rasterizer.drawVerticalLine(x + 14, y + 17 + thumbY, thumbHeight - 1, scrollbarShadowColor);
		Rasterizer.drawHorizontalLine(x, y + 15 + thumbY + thumbHeight, 16, scrollbarShadowColor);
		Rasterizer.drawHorizontalLine(x + 1, y + 14 + thumbY + thumbHeight, 15, scrollbarShadowColor);
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
		loginFailures = 0;
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
	 * Handles content-type-specific widget buttons such as appearance, logout, and
	 * report-abuse controls.
	 *
	 * @param widget the widget being processed
	 * @return true when the requested condition/action succeeds; otherwise false
	 */
	public boolean handleWidgetContentAction(Widget widget) {
		int contentType = widget.contentType;
		if (socialManager.friendListStatus == 2) {
			if (contentType == 201) {
				chatboxRedraw = true;
				inputDialogState = 0;
				messagePromptRaised = true;
				promptInput = "";
				promptAction = 1;
				promptMessage = "Enter name of friend to add to list";
			}
			if (contentType == 202) {
				chatboxRedraw = true;
				inputDialogState = 0;
				messagePromptRaised = true;
				promptInput = "";
				promptAction = 2;
				promptMessage = "Enter name of friend to delete from list";
			}
		}
		if (contentType == 205) {
			logoutTimer = 250;
			return true;
		}
		if (contentType == 501) {
			chatboxRedraw = true;
			inputDialogState = 0;
			messagePromptRaised = true;
			promptInput = "";
			promptAction = 4;
			promptMessage = "Enter name of player to add to list";
		}
		if (contentType == 502) {
			chatboxRedraw = true;
			inputDialogState = 0;
			messagePromptRaised = true;
			promptInput = "";
			promptAction = 5;
			promptMessage = "Enter name of player to delete from list";
		}
		if (contentType >= 300 && contentType <= 313) {
			int bodyPart = (contentType - 300) / 2;
			int direction = contentType & 1;
			int kitId = appearanceKitIds[bodyPart];
			if (kitId != -1) {
				do {
					if (direction == 0 && --kitId < 0)
						kitId = IdentityKit.count - 1;
					if (direction == 1 && ++kitId >= IdentityKit.count)
						kitId = 0;
				} while (IdentityKit.definitions[kitId].nonSelectable
						|| IdentityKit.definitions[kitId].bodyPartId != bodyPart + (maleAppearance ? 0 : 7));
				appearanceKitIds[bodyPart] = kitId;
				appearanceModelDirty = true;
			}
		}
		if (contentType >= 314 && contentType <= 323) {
			int colorSlot = (contentType - 314) / 2;
			int direction2 = contentType & 1;
			int colorIndex = appearanceColors[colorSlot];
			if (direction2 == 0 && --colorIndex < 0)
				colorIndex = bodyColorPalettes[colorSlot].length - 1;
			if (direction2 == 1 && ++colorIndex >= bodyColorPalettes[colorSlot].length)
				colorIndex = 0;
			appearanceColors[colorSlot] = colorIndex;
			appearanceModelDirty = true;
		}
		if (contentType == 324 && !maleAppearance) {
			maleAppearance = true;
			resetCharacterAppearance();
		}
		if (contentType == 325 && maleAppearance) {
			maleAppearance = false;
			resetCharacterAppearance();
		}
		if (contentType == 326) {
			networkSession.outgoing.writeOpcode(163);
			networkSession.outgoing.writeByte(maleAppearance ? 0 : 1);
			for (int bodyPart2 = 0; bodyPart2 < 7; bodyPart2++)
				networkSession.outgoing.writeByte(appearanceKitIds[bodyPart2]);

			for (int colorSlot2 = 0; colorSlot2 < 5; colorSlot2++)
				networkSession.outgoing.writeByte(appearanceColors[colorSlot2]);

			return true;
		}
		if (contentType == 620)
			reportAbuseMutePlayer = !reportAbuseMutePlayer;
		if (contentType >= 601 && contentType <= 613) {
			closeInterfaces();
			if (reportAbuseName.length() > 0) {
				networkSession.outgoing.writeOpcode(184);
				networkSession.outgoing.writeLong(Base37.encode(reportAbuseName));
				networkSession.outgoing.writeByte(contentType - 601);
				networkSession.outgoing.writeByte(reportAbuseMutePlayer ? 1 : 0);
			}
		}
		return false;
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
			if (!resourceLoader.hasAllBootstrapArchives())
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
			drawLoadingText(60, "Connecting to update server");
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
				onDemandFetcher.request(3, onDemandFetcher.getMapFileId(47, 48, 0));
				onDemandFetcher.request(3, onDemandFetcher.getMapFileId(47, 48, 1));
				onDemandFetcher.request(3, onDemandFetcher.getMapFileId(48, 48, 0));
				onDemandFetcher.request(3, onDemandFetcher.getMapFileId(48, 48, 1));
				onDemandFetcher.request(3, onDemandFetcher.getMapFileId(49, 48, 0));
				onDemandFetcher.request(3, onDemandFetcher.getMapFileId(49, 48, 1));
				onDemandFetcher.request(3, onDemandFetcher.getMapFileId(47, 47, 0));
				onDemandFetcher.request(3, onDemandFetcher.getMapFileId(47, 47, 1));
				onDemandFetcher.request(3, onDemandFetcher.getMapFileId(48, 47, 0));
				onDemandFetcher.request(3, onDemandFetcher.getMapFileId(48, 47, 1));
				onDemandFetcher.request(3, onDemandFetcher.getMapFileId(48, 148, 0));
				onDemandFetcher.request(3, onDemandFetcher.getMapFileId(48, 148, 1));
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

			Rasterizer3D.setBounds(765, 503);
			fullScreenScanlineOffsets = Rasterizer3D.scanlineOffsets;
			Rasterizer3D.setBounds(479, 96);
			chatboxScanlineOffsets = Rasterizer3D.scanlineOffsets;
			Rasterizer3D.setBounds(190, 261);
			sidebarScanlineOffsets = Rasterizer3D.scanlineOffsets;
			Rasterizer3D.setBounds(512, 334);
			viewportScanlineOffsets = Rasterizer3D.scanlineOffsets;
			int visibilityPitchHeights[] = new int[9];
			for (int pitchIndex = 0; pitchIndex < 9; pitchIndex++) {
				int pitchAngle = 128 + pitchIndex * 32 + 15;
				int projectionDistance = 600 + pitchAngle * 3;
				int pitchSine = Rasterizer3D.SINE[pitchAngle];
				visibilityPitchHeights[pitchIndex] = projectionDistance * pitchSine >> 16;
			}

			Scene.buildVisibilityMaps(500, 800, 512, 334, visibilityPitchHeights);
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

	/**
	 * Recursively builds context-menu entries for widgets and inventory slots under
	 * the mouse.
	 *
	 * @param y          the Y coordinate
	 * @param widget     the widget being processed
	 * @param screenArea the fixed client screen area identifier
	 * @param scrollY    the current scroll offset
	 * @param x          the X coordinate
	 * @param mouseX     the mouse X coordinate
	 * @param mouseY     the mouse Y coordinate
	 */
	public void buildInterfaceMenu(int y, Widget widget, int screenArea, int scrollY, int x, int mouseX, int mouseY) {
		if (widget.type != 0 || widget.children == null || widget.mouseoverTriggered)
			return;
		if (mouseX < x || mouseY < y || mouseX > x + widget.width || mouseY > y + widget.height)
			return;
		int childCount = widget.children.length;
		for (int childIndex = 0; childIndex < childCount; childIndex++) {
			int childX = widget.childX[childIndex] + x;
			int childY = (widget.childY[childIndex] + y) - scrollY;
			Widget childWidget = Widget.get(widget.children[childIndex]);
			childX += childWidget.xOffset;
			childY += childWidget.yOffset;
			if ((childWidget.mouseoverTargetId >= 0 || childWidget.mouseoverColor != 0) && mouseX >= childX
					&& mouseY >= childY && mouseX < childX + childWidget.width && mouseY < childY + childWidget.height)
				if (childWidget.mouseoverTargetId >= 0)
					currentHoveredWidgetId = childWidget.mouseoverTargetId;
				else
					currentHoveredWidgetId = childWidget.id;
			if (childWidget.type == 8 && mouseX >= childX && mouseY >= childY && mouseX < childX + childWidget.width
					&& mouseY < childY + childWidget.height)
				currentTooltipWidgetId = childWidget.id;
			if (childWidget.type == 0) {
				buildInterfaceMenu(childY, childWidget, screenArea, childWidget.scrollY, childX, mouseX, mouseY);
				if (childWidget.scrollHeight > childWidget.height)
					handleScrollbarInput(childWidget.scrollHeight, childY, childWidget, mouseY, screenArea, mouseX,
							childWidget.height, childX + childWidget.width);
			} else {
				if (childWidget.buttonType == 1 && mouseX >= childX && mouseY >= childY
						&& mouseX < childX + childWidget.width && mouseY < childY + childWidget.height) {
					boolean contentHandled = false;
					if (childWidget.contentType != 0)
						contentHandled = buildSocialWidgetMenu(childWidget);
					if (!contentHandled) {
						menuState.actionNames[menuState.count] = childWidget.tooltip;
						menuState.actionIds[menuState.count] = 352;
						menuState.actionCmd3[menuState.count] = childWidget.id;
						menuState.count++;
					}
				}
				if (childWidget.buttonType == 2 && interfaceState.spellSelected == 0 && mouseX >= childX
						&& mouseY >= childY && mouseX < childX + childWidget.width
						&& mouseY < childY + childWidget.height) {
					String spellAction = childWidget.selectedActionName;
					if (spellAction.indexOf(" ") != -1)
						spellAction = spellAction.substring(0, spellAction.indexOf(" "));
					menuState.actionNames[menuState.count] = spellAction + " @gre@" + childWidget.spellName;
					menuState.actionIds[menuState.count] = 70;
					menuState.actionCmd3[menuState.count] = childWidget.id;
					menuState.count++;
				}
				if (childWidget.buttonType == 3 && mouseX >= childX && mouseY >= childY
						&& mouseX < childX + childWidget.width && mouseY < childY + childWidget.height) {
					menuState.actionNames[menuState.count] = "Close";
					if (screenArea == 3)
						menuState.actionIds[menuState.count] = 55;
					else
						menuState.actionIds[menuState.count] = 639;
					menuState.actionCmd3[menuState.count] = childWidget.id;
					menuState.count++;
				}
				if (childWidget.buttonType == 4 && mouseX >= childX && mouseY >= childY
						&& mouseX < childX + childWidget.width && mouseY < childY + childWidget.height) {
					menuState.actionNames[menuState.count] = childWidget.tooltip;
					menuState.actionIds[menuState.count] = 890;
					menuState.actionCmd3[menuState.count] = childWidget.id;
					menuState.count++;
				}
				if (childWidget.buttonType == 5 && mouseX >= childX && mouseY >= childY
						&& mouseX < childX + childWidget.width && mouseY < childY + childWidget.height) {
					menuState.actionNames[menuState.count] = childWidget.tooltip;
					menuState.actionIds[menuState.count] = 518;
					menuState.actionCmd3[menuState.count] = childWidget.id;
					menuState.count++;
				}
				if (childWidget.buttonType == 6 && !interfaceActionPending && mouseX >= childX && mouseY >= childY
						&& mouseX < childX + childWidget.width && mouseY < childY + childWidget.height) {
					menuState.actionNames[menuState.count] = childWidget.tooltip;
					menuState.actionIds[menuState.count] = 575;
					menuState.actionCmd3[menuState.count] = childWidget.id;
					menuState.count++;
				}
				if (childWidget.type == 2) {
					int slot = 0;
					for (int row = 0; row < childWidget.height; row++) {
						for (int column = 0; column < childWidget.width; column++) {
							int slotX = childX + column * (32 + childWidget.inventorySpritePaddingX);
							int slotY = childY + row * (32 + childWidget.inventorySpritePaddingY);
							if (slot < 20) {
								slotX += childWidget.spriteXOffsets[slot];
								slotY += childWidget.spriteYOffsets[slot];
							}
							if (mouseX >= slotX && mouseY >= slotY && mouseX < slotX + 32 && mouseY < slotY + 32) {
								interfaceState.hoveredInventorySlot = slot;
								interfaceState.hoveredInventoryWidgetId = childWidget.id;
								if (childWidget.itemIds[slot] > 0) {
									ItemDefinition itemDefinition = ItemDefinition
											.lookup(childWidget.itemIds[slot] - 1);
									if (interfaceState.itemSelected == 1 && childWidget.inventoryHasOptions) {
										if (childWidget.id != interfaceState.selectedItemWidgetId
												|| slot != interfaceState.selectedItemSlot) {
											menuState.actionNames[menuState.count] = "Use "
													+ interfaceState.selectedItemName + " with @lre@"
													+ itemDefinition.name;
											menuState.actionIds[menuState.count] = 903;
											menuState.actionCmd1[menuState.count] = itemDefinition.id;
											menuState.actionCmd2[menuState.count] = slot;
											menuState.actionCmd3[menuState.count] = childWidget.id;
											menuState.count++;
										}
									} else if (interfaceState.spellSelected == 1 && childWidget.inventoryHasOptions) {
										if ((interfaceState.selectedSpellTargetMask & 0x10) == 16) {
											menuState.actionNames[menuState.count] = interfaceState.selectedSpellAction
													+ " @lre@" + itemDefinition.name;
											menuState.actionIds[menuState.count] = 361;
											menuState.actionCmd1[menuState.count] = itemDefinition.id;
											menuState.actionCmd2[menuState.count] = slot;
											menuState.actionCmd3[menuState.count] = childWidget.id;
											menuState.count++;
										}
									} else {
										if (childWidget.inventoryHasOptions) {
											for (int inventoryActionIndex = 4; inventoryActionIndex >= 3; inventoryActionIndex--)
												if (itemDefinition.inventoryActions != null
														&& itemDefinition.inventoryActions[inventoryActionIndex] != null) {
													menuState.actionNames[menuState.count] = itemDefinition.inventoryActions[inventoryActionIndex]
															+ " @lre@" + itemDefinition.name;
													if (inventoryActionIndex == 3)
														menuState.actionIds[menuState.count] = 227;
													if (inventoryActionIndex == 4)
														menuState.actionIds[menuState.count] = 891;
													menuState.actionCmd1[menuState.count] = itemDefinition.id;
													menuState.actionCmd2[menuState.count] = slot;
													menuState.actionCmd3[menuState.count] = childWidget.id;
													menuState.count++;
												} else if (inventoryActionIndex == 4) {
													menuState.actionNames[menuState.count] = "Drop @lre@"
															+ itemDefinition.name;
													menuState.actionIds[menuState.count] = 891;
													menuState.actionCmd1[menuState.count] = itemDefinition.id;
													menuState.actionCmd2[menuState.count] = slot;
													menuState.actionCmd3[menuState.count] = childWidget.id;
													menuState.count++;
												}

										}
										if (childWidget.inventoryUsableItems) {
											menuState.actionNames[menuState.count] = "Use @lre@" + itemDefinition.name;
											menuState.actionIds[menuState.count] = 52;
											menuState.actionCmd1[menuState.count] = itemDefinition.id;
											menuState.actionCmd2[menuState.count] = slot;
											menuState.actionCmd3[menuState.count] = childWidget.id;
											menuState.count++;
										}
										if (childWidget.inventoryHasOptions
												&& itemDefinition.inventoryActions != null) {
											for (int inventoryActionIndex2 = 2; inventoryActionIndex2 >= 0; inventoryActionIndex2--)
												if (itemDefinition.inventoryActions[inventoryActionIndex2] != null) {
													menuState.actionNames[menuState.count] = itemDefinition.inventoryActions[inventoryActionIndex2]
															+ " @lre@" + itemDefinition.name;
													if (inventoryActionIndex2 == 0)
														menuState.actionIds[menuState.count] = 961;
													if (inventoryActionIndex2 == 1)
														menuState.actionIds[menuState.count] = 399;
													if (inventoryActionIndex2 == 2)
														menuState.actionIds[menuState.count] = 324;
													menuState.actionCmd1[menuState.count] = itemDefinition.id;
													menuState.actionCmd2[menuState.count] = slot;
													menuState.actionCmd3[menuState.count] = childWidget.id;
													menuState.count++;
												}

										}
										if (childWidget.actions != null) {
											for (int widgetActionIndex = 4; widgetActionIndex >= 0; widgetActionIndex--)
												if (childWidget.actions[widgetActionIndex] != null) {
													menuState.actionNames[menuState.count] = childWidget.actions[widgetActionIndex]
															+ " @lre@" + itemDefinition.name;
													if (widgetActionIndex == 0)
														menuState.actionIds[menuState.count] = 9;
													if (widgetActionIndex == 1)
														menuState.actionIds[menuState.count] = 225;
													if (widgetActionIndex == 2)
														menuState.actionIds[menuState.count] = 444;
													if (widgetActionIndex == 3)
														menuState.actionIds[menuState.count] = 564;
													if (widgetActionIndex == 4)
														menuState.actionIds[menuState.count] = 894;
													menuState.actionCmd1[menuState.count] = itemDefinition.id;
													menuState.actionCmd2[menuState.count] = slot;
													menuState.actionCmd3[menuState.count] = childWidget.id;
													menuState.count++;
												}

										}
										menuState.actionNames[menuState.count] = "Examine @lre@" + itemDefinition.name;
										menuState.actionIds[menuState.count] = 1094;
										menuState.actionCmd1[menuState.count] = itemDefinition.id;
										menuState.actionCmd2[menuState.count] = slot;
										menuState.actionCmd3[menuState.count] = childWidget.id;
										menuState.count++;
									}
								}
							}
							slot++;
						}

					}

				}
			}
		}

	}

	/**
	 * Coordinates one complete logged-in frame across sidebar, chatbox, viewport,
	 * and fullscreen interfaces.
	 */
	public void drawGameScreen() {
		if (interfaceState.fullscreenInterfaceId != -1
				&& (regionManager.loadingStage == RegionManager.STAGE_LOADED || super.gameBuffer != null)) {
			if (regionManager.loadingStage == RegionManager.STAGE_LOADED) {
				widgetRuntime.updateAnimations(animationCycleDelta, interfaceState.fullscreenInterfaceId);
				if (interfaceState.fullscreenOverlayInterfaceId != -1)
					widgetRuntime.updateAnimations(animationCycleDelta, interfaceState.fullscreenOverlayInterfaceId);
				animationCycleDelta = 0;
				createGameBuffer();
				super.gameBuffer.bindRaster();
				Rasterizer3D.scanlineOffsets = fullScreenScanlineOffsets;
				Rasterizer.resetPixels();
				gameScreenRedraw = true;
				Widget fullscreenWidget = Widget.get(interfaceState.fullscreenInterfaceId);
				if (fullscreenWidget.width == 512 && fullscreenWidget.height == 334 && fullscreenWidget.type == 0) {
					fullscreenWidget.width = 765;
					fullscreenWidget.height = 503;
				}
				drawInterface(0, 0, fullscreenWidget, 0);
				if (interfaceState.fullscreenOverlayInterfaceId != -1) {
					Widget fullscreenOverlayWidget = Widget.get(interfaceState.fullscreenOverlayInterfaceId);
					if (fullscreenOverlayWidget.width == 512 && fullscreenOverlayWidget.height == 334
							&& fullscreenOverlayWidget.type == 0) {
						fullscreenOverlayWidget.width = 765;
						fullscreenOverlayWidget.height = 503;
					}
					drawInterface(0, 0, fullscreenOverlayWidget, 0);
				}
				if (!menuState.open) {
					buildContextMenu();
					drawMenuTooltip();
				} else {
					drawContextMenu();
				}
			}
			super.gameBuffer.draw(super.graphics, 0, 0);
			return;
		}
		if (gameScreenRedraw) {
			createGameScreenBuffers();
			gameScreenRedraw = false;
			backLeft1Buffer.draw(super.graphics, 0, 4);
			backLeft2Buffer.draw(super.graphics, 0, 357);
			backRight1Buffer.draw(super.graphics, 722, 4);
			backRight2Buffer.draw(super.graphics, 743, 205);
			backTop1Buffer.draw(super.graphics, 0, 0);
			backVerticalMiddle1Buffer.draw(super.graphics, 516, 4);
			backVerticalMiddle2Buffer.draw(super.graphics, 516, 205);
			backVerticalMiddle3Buffer.draw(super.graphics, 496, 357);
			backHorizontalMiddle2Buffer.draw(super.graphics, 0, 338);
			sidebarRedraw = true;
			chatboxRedraw = true;
			tabAreaRedraw = true;
			chatModesRedraw = true;
			if (regionManager.loadingStage != RegionManager.STAGE_LOADED) {
				viewportBuffer.draw(super.graphics, 4, 4);
				minimapBuffer.draw(super.graphics, 550, 4);
			}
			screenRedrawKeepaliveCounter++;
			if (screenRedrawKeepaliveCounter > 85) {
				screenRedrawKeepaliveCounter = 0;
				networkSession.outgoing.writeOpcode(168);
			}
		}
		if (regionManager.loadingStage == RegionManager.STAGE_LOADED)
			renderGameScene();
		if (menuState.open && menuState.screenArea == 1)
			sidebarRedraw = true;
		if (interfaceState.sidebarOverlayInterfaceId != -1) {
			boolean sidebarAnimationChanged = widgetRuntime.updateAnimations(animationCycleDelta,
					interfaceState.sidebarOverlayInterfaceId);
			if (sidebarAnimationChanged)
				sidebarRedraw = true;
		}
		if (interfaceState.pressedInventoryArea == 2)
			sidebarRedraw = true;
		if (interfaceState.inventoryDragArea == 2)
			sidebarRedraw = true;
		if (sidebarRedraw) {
			drawSidebar();
			sidebarRedraw = false;
		}
		if (interfaceState.chatboxInterfaceId == -1 && inputDialogState == 0) {
			chatboxScrollWidget.scrollY = chatContentHeight - chatScrollOffset - 77;
			if (super.mouseX > 448 && super.mouseX < 560 && super.mouseY > 332)
				handleScrollbarInput(chatContentHeight, 0, chatboxScrollWidget, super.mouseY - 357, -1,
						super.mouseX - 17, 77, 463);
			int chatScrollOffsetFromBottom = chatContentHeight - 77 - chatboxScrollWidget.scrollY;
			if (chatScrollOffsetFromBottom < 0)
				chatScrollOffsetFromBottom = 0;
			if (chatScrollOffsetFromBottom > chatContentHeight - 77)
				chatScrollOffsetFromBottom = chatContentHeight - 77;
			if (chatScrollOffset != chatScrollOffsetFromBottom) {
				chatScrollOffset = chatScrollOffsetFromBottom;
				chatboxRedraw = true;
			}
		}
		if (interfaceState.chatboxInterfaceId == -1 && inputDialogState == 3) {
			int searchContentHeight = itemSearchResultCount * 14 + 7;
			chatboxScrollWidget.scrollY = itemSearchScrollOffset;
			if (super.mouseX > 448 && super.mouseX < 560 && super.mouseY > 332)
				handleScrollbarInput(searchContentHeight, 0, chatboxScrollWidget, super.mouseY - 357, -1,
						super.mouseX - 17, 77, 463);
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
		if (interfaceState.chatboxInterfaceId != -1) {
			boolean chatboxAnimationChanged = widgetRuntime.updateAnimations(animationCycleDelta,
					interfaceState.chatboxInterfaceId);
			if (chatboxAnimationChanged)
				chatboxRedraw = true;
		}
		if (interfaceState.pressedInventoryArea == 3)
			chatboxRedraw = true;
		if (interfaceState.inventoryDragArea == 3)
			chatboxRedraw = true;
		if (clickToContinueMessage != null)
			chatboxRedraw = true;
		if (menuState.open && menuState.screenArea == 2)
			chatboxRedraw = true;
		if (chatboxRedraw) {
			drawChatbox();
			chatboxRedraw = false;
		}
		if (regionManager.loadingStage == RegionManager.STAGE_LOADED) {
			drawMinimap();
			minimapBuffer.draw(super.graphics, 550, 4);
		}
		if (interfaceState.flashingTab != -1)
			tabAreaRedraw = true;
		if (tabAreaRedraw) {
			if (interfaceState.flashingTab != -1 && interfaceState.flashingTab == interfaceState.selectedTab) {
				networkSession.outgoing.writeOpcode(119);
				networkSession.outgoing.writeByte(interfaceState.selectedTab);
			}
			tabAreaRedraw = false;
			topTabsBuffer.bindRaster();
			topTabBackground.draw(0, 0);
			if (interfaceState.sidebarOverlayInterfaceId == -1) {
				if (interfaceState.tabInterfaceIds[interfaceState.selectedTab] != -1) {
					if (interfaceState.selectedTab == 0)
						redstone1.draw(22, 10);
					if (interfaceState.selectedTab == 1)
						redstone2.draw(54, 8);
					if (interfaceState.selectedTab == 2)
						redstone2.draw(82, 8);
					if (interfaceState.selectedTab == 3)
						redstone3.draw(110, 8);
					if (interfaceState.selectedTab == 4)
						redstone2Horizontal.draw(153, 8);
					if (interfaceState.selectedTab == 5)
						redstone2Horizontal.draw(181, 8);
					if (interfaceState.selectedTab == 6)
						redstone1Horizontal.draw(209, 9);
				}
				if (interfaceState.tabInterfaceIds[0] != -1 && (interfaceState.flashingTab != 0 || gameCycle % 20 < 10))
					sidebarIcons[0].draw(29, 13);
				if (interfaceState.tabInterfaceIds[1] != -1 && (interfaceState.flashingTab != 1 || gameCycle % 20 < 10))
					sidebarIcons[1].draw(53, 11);
				if (interfaceState.tabInterfaceIds[2] != -1 && (interfaceState.flashingTab != 2 || gameCycle % 20 < 10))
					sidebarIcons[2].draw(82, 11);
				if (interfaceState.tabInterfaceIds[3] != -1 && (interfaceState.flashingTab != 3 || gameCycle % 20 < 10))
					sidebarIcons[3].draw(115, 12);
				if (interfaceState.tabInterfaceIds[4] != -1 && (interfaceState.flashingTab != 4 || gameCycle % 20 < 10))
					sidebarIcons[4].draw(153, 13);
				if (interfaceState.tabInterfaceIds[5] != -1 && (interfaceState.flashingTab != 5 || gameCycle % 20 < 10))
					sidebarIcons[5].draw(180, 11);
				if (interfaceState.tabInterfaceIds[6] != -1 && (interfaceState.flashingTab != 6 || gameCycle % 20 < 10))
					sidebarIcons[6].draw(208, 13);
			}
			topTabsBuffer.draw(super.graphics, 516, 160);
			bottomTabsBuffer.bindRaster();
			bottomTabBackground.draw(0, 0);
			if (interfaceState.sidebarOverlayInterfaceId == -1) {
				if (interfaceState.tabInterfaceIds[interfaceState.selectedTab] != -1) {
					if (interfaceState.selectedTab == 7)
						redstone1Vertical.draw(42, 0);
					if (interfaceState.selectedTab == 8)
						redstone2Vertical.draw(74, 0);
					if (interfaceState.selectedTab == 9)
						redstone2Vertical.draw(102, 0);
					if (interfaceState.selectedTab == 10)
						redstone3Vertical.draw(130, 1);
					if (interfaceState.selectedTab == 11)
						redstone2Both.draw(173, 0);
					if (interfaceState.selectedTab == 12)
						redstone2Both.draw(201, 0);
					if (interfaceState.selectedTab == 13)
						redstone1Both.draw(229, 0);
				}
				if (interfaceState.tabInterfaceIds[8] != -1 && (interfaceState.flashingTab != 8 || gameCycle % 20 < 10))
					sidebarIcons[7].draw(74, 2);
				if (interfaceState.tabInterfaceIds[9] != -1 && (interfaceState.flashingTab != 9 || gameCycle % 20 < 10))
					sidebarIcons[8].draw(102, 3);
				if (interfaceState.tabInterfaceIds[10] != -1
						&& (interfaceState.flashingTab != 10 || gameCycle % 20 < 10))
					sidebarIcons[9].draw(137, 4);
				if (interfaceState.tabInterfaceIds[11] != -1
						&& (interfaceState.flashingTab != 11 || gameCycle % 20 < 10))
					sidebarIcons[10].draw(174, 2);
				if (interfaceState.tabInterfaceIds[12] != -1
						&& (interfaceState.flashingTab != 12 || gameCycle % 20 < 10))
					sidebarIcons[11].draw(201, 2);
				if (interfaceState.tabInterfaceIds[13] != -1
						&& (interfaceState.flashingTab != 13 || gameCycle % 20 < 10))
					sidebarIcons[12].draw(226, 2);
			}
			bottomTabsBuffer.draw(super.graphics, 496, 466);
			viewportBuffer.bindRaster();
			Rasterizer3D.scanlineOffsets = viewportScanlineOffsets;
		}
		if (chatModesRedraw) {
			chatModesRedraw = false;
			chatModesBuffer.bindRaster();
			chatModesBackground.draw(0, 0);
			plainFont.drawCenteredTextWithTags("Public chat", 55, 28, 0xffffff, true);
			if (publicChatMode == 0)
				plainFont.drawCenteredTextWithTags("On", 55, 41, 65280, true);
			if (publicChatMode == 1)
				plainFont.drawCenteredTextWithTags("Friends", 55, 41, 0xffff00, true);
			if (publicChatMode == 2)
				plainFont.drawCenteredTextWithTags("Off", 55, 41, 0xff0000, true);
			if (publicChatMode == 3)
				plainFont.drawCenteredTextWithTags("Hide", 55, 41, 65535, true);
			plainFont.drawCenteredTextWithTags("Private chat", 184, 28, 0xffffff, true);
			if (privateChatMode == 0)
				plainFont.drawCenteredTextWithTags("On", 184, 41, 65280, true);
			if (privateChatMode == 1)
				plainFont.drawCenteredTextWithTags("Friends", 184, 41, 0xffff00, true);
			if (privateChatMode == 2)
				plainFont.drawCenteredTextWithTags("Off", 184, 41, 0xff0000, true);
			plainFont.drawCenteredTextWithTags("Trade/compete", 324, 28, 0xffffff, true);
			if (tradeMode == 0)
				plainFont.drawCenteredTextWithTags("On", 324, 41, 65280, true);
			if (tradeMode == 1)
				plainFont.drawCenteredTextWithTags("Friends", 324, 41, 0xffff00, true);
			if (tradeMode == 2)
				plainFont.drawCenteredTextWithTags("Off", 324, 41, 0xff0000, true);
			plainFont.drawCenteredTextWithTags("Report abuse", 458, 33, 0xffffff, true);
			chatModesBuffer.draw(super.graphics, 0, 453);
			viewportBuffer.bindRaster();
			Rasterizer3D.scanlineOffsets = viewportScanlineOffsets;
		}
		animationCycleDelta = 0;
	}

	/**
	 * Draws the optional private-message overlay above the chatbox.
	 */
	public void drawSplitPrivateChat() {
		if (splitPrivateChat == 0)
			return;
		TypeFace font = plainFont;
		int visibleLine = 0;
		if (systemUpdateTimer != 0)
			visibleLine = 1;
		for (int messageIndex = 0; messageIndex < 100; messageIndex++)
			if (chatHistory.messages[messageIndex] != null) {
				int messageType = chatHistory.types[messageIndex];
				String sender = chatHistory.senders[messageIndex];
				byte rightsIcon = 0;
				if (sender != null && sender.startsWith("@cr1@")) {
					sender = sender.substring(5);
					rightsIcon = 1;
				}
				if (sender != null && sender.startsWith("@cr2@")) {
					sender = sender.substring(5);
					rightsIcon = 2;
				}
				if ((messageType == 3 || messageType == 7) && (messageType == 7 || privateChatMode == 0
						|| privateChatMode == 1 && isFriendOrSelf(sender))) {
					int lineY = 329 - visibleLine * 13;
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
					font.drawText(sender + ": " + chatHistory.messages[messageIndex], textX, lineY, 0);
					font.drawText(sender + ": " + chatHistory.messages[messageIndex], textX, lineY - 1, 65535);
					if (++visibleLine >= 5)
						return;
				}
				if (messageType == 5 && privateChatMode < 2) {
					int lineY2 = 329 - visibleLine * 13;
					font.drawText(chatHistory.messages[messageIndex], 4, lineY2, 0);
					font.drawText(chatHistory.messages[messageIndex], 4, lineY2 - 1, 65535);
					if (++visibleLine >= 5)
						return;
				}
				if (messageType == 6 && privateChatMode < 2) {
					int lineY3 = 329 - visibleLine * 13;
					font.drawText("To " + sender + ": " + chatHistory.messages[messageIndex], 4, lineY3, 0);
					font.drawText("To " + sender + ": " + chatHistory.messages[messageIndex], 4, lineY3 - 1, 65535);
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
				if (request.type == 0) {
					Model.loadModelHeader(request.buffer, request.id);
					if ((onDemandFetcher.getModelIndex(request.id) & 0x62) != 0) {
						sidebarRedraw = true;
						if (interfaceState.chatboxInterfaceId != -1 || interfaceState.dialogueInterfaceId != -1)
							chatboxRedraw = true;
					}
				}
				if (request.type == 1 && request.buffer != null)
					AnimationFrame.load(request.buffer);
				musicController.acceptOnDemandRequest(request);
				if (request.type == 3 && regionManager.loadingStage == RegionManager.STAGE_LOADING)
					regionManager.acceptMapFile(request);
			} while (request.type != 93 || !onDemandFetcher.isLandscapeFile(request.id));
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
		try {
			if (!reconnecting) {
				loginScreen.message1 = "";
				loginScreen.message2 = "Connecting to server...";
				drawLoginScreen(true);
			}
			networkSession.connect(openSocket(43594 + portOffset));
			long encodedUsername = Base37.encode(loginUsername);
			// Five-bit username hash partition used by the legacy login handshake.
			int usernameHashPart = (int) (encodedUsername >> 16 & 31L);
			networkSession.outgoing.position = 0;
			networkSession.outgoing.writeByte(14);
			networkSession.outgoing.writeByte(usernameHashPart);
			networkSession.write(networkSession.outgoing.payload, 0, 2);
			for (int handshakeByteIndex = 0; handshakeByteIndex < 8; handshakeByteIndex++)
				networkSession.read();

			int responseCode = networkSession.read();
			int initialResponseCode = responseCode;
			if (responseCode == 0) {
				networkSession.readFully(networkSession.incoming.payload, 0, 8);
				networkSession.incoming.position = 0;
				serverSessionKey = networkSession.incoming.readLong();
				int isaacSeed[] = new int[4];
				isaacSeed[0] = (int) (Math.random() * 99999999D);
				isaacSeed[1] = (int) (Math.random() * 99999999D);
				isaacSeed[2] = (int) (serverSessionKey >> 32);
				isaacSeed[3] = (int) serverSessionKey;
				networkSession.outgoing.position = 0;
				networkSession.outgoing.writeByte(10);
				networkSession.outgoing.writeInt(isaacSeed[0]);
				networkSession.outgoing.writeInt(isaacSeed[1]);
				networkSession.outgoing.writeInt(isaacSeed[2]);
				networkSession.outgoing.writeInt(isaacSeed[3]);
				networkSession.outgoing.writeInt(Signlink.uid);
				networkSession.outgoing.writeString(loginUsername);
				networkSession.outgoing.writeString(loginPassword);
				networkSession.outgoing.encryptRsa(RSA_EXPONENT, RSA_MODULUS);
				loginBuffer.position = 0;
				if (reconnecting)
					loginBuffer.writeByte(18);
				else
					loginBuffer.writeByte(16);
				loginBuffer.writeByte(networkSession.outgoing.position + 36 + 1 + 1 + 2);
				loginBuffer.writeByte(255);
				loginBuffer.writeShort(377);
				loginBuffer.writeByte(lowMemory ? 1 : 0);
				for (int crcIndex = 0; crcIndex < 9; crcIndex++)
					loginBuffer.writeInt(resourceLoader.getArchiveCrc(crcIndex));

				loginBuffer.writeBytes(networkSession.outgoing.payload, 0, networkSession.outgoing.position);
				networkSession.initializeOpcodeCiphers(isaacSeed);
				networkSession.write(loginBuffer.payload, 0, loginBuffer.position);
				responseCode = networkSession.read();
			}
			if (responseCode == 1) {
				try {
					Thread.sleep(2000L);
				} catch (Exception ignored) {
				}
				login(loginUsername, loginPassword, reconnecting);
				return;
			}
			if (responseCode == 2) {
				playerRights = networkSession.read();
				accountFlagged = networkSession.read() == 1;
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
				menuState.count = 0;
				super.idleCycles = 0;
				chatHistory.clearMessages();

				interfaceState.itemSelected = 0;
				interfaceState.spellSelected = 0;
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
				unloadInterface(interfaceState.dialogueInterfaceId);
				unloadInterface(interfaceState.chatboxInterfaceId);
				unloadInterface(interfaceState.openInterfaceId);
				unloadInterface(interfaceState.fullscreenInterfaceId);
				unloadInterface(interfaceState.fullscreenOverlayInterfaceId);
				unloadInterface(interfaceState.sidebarOverlayInterfaceId);
				unloadInterface(interfaceState.walkableInterfaceId);
				interfaceActionPending = false;
				inputDialogState = 0;
				messagePromptRaised = false;
				clickToContinueMessage = null;
				multiCombatZone = 0;
				maleAppearance = true;
				resetCharacterAppearance();
				for (int colorSlot = 0; colorSlot < 5; colorSlot++)
					appearanceColors[colorSlot] = 0;

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
				return;
			}
			if (responseCode == 3) {
				loginScreen.message1 = "";
				loginScreen.message2 = "Invalid username or password.";
				return;
			}
			if (responseCode == 4) {
				loginScreen.message1 = "Your account has been disabled.";
				loginScreen.message2 = "Please check your message-centre for details.";
				return;
			}
			if (responseCode == 5) {
				loginScreen.message1 = "Your account is already logged in.";
				loginScreen.message2 = "Try again in 60 secs...";
				return;
			}
			if (responseCode == 6) {
				loginScreen.message1 = "RuneScape has been updated!";
				loginScreen.message2 = "Please reload this page.";
				return;
			}
			if (responseCode == 7) {
				loginScreen.message1 = "This world is full.";
				loginScreen.message2 = "Please use a different world.";
				return;
			}
			if (responseCode == 8) {
				loginScreen.message1 = "Unable to connect.";
				loginScreen.message2 = "Login server offline.";
				return;
			}
			if (responseCode == 9) {
				loginScreen.message1 = "Login limit exceeded.";
				loginScreen.message2 = "Too many connections from your address.";
				return;
			}
			if (responseCode == 10) {
				loginScreen.message1 = "Unable to connect.";
				loginScreen.message2 = "Bad session id.";
				return;
			}
			if (responseCode == 12) {
				loginScreen.message1 = "You need a members account to login to this world.";
				loginScreen.message2 = "Please subscribe, or use a different world.";
				return;
			}
			if (responseCode == 13) {
				loginScreen.message1 = "Could not complete login.";
				loginScreen.message2 = "Please try using a different world.";
				return;
			}
			if (responseCode == 14) {
				loginScreen.message1 = "The server is being updated.";
				loginScreen.message2 = "Please wait 1 minute and try again.";
				return;
			}
			if (responseCode == 15) {
				loggedIn = true;
				networkSession.resetPacketState();
				systemUpdateTimer = 0;
				menuState.count = 0;
				regionManager.loadingStartTime = System.currentTimeMillis();
				return;
			}
			if (responseCode == 16) {
				loginScreen.message1 = "Login attempts exceeded.";
				loginScreen.message2 = "Please wait 1 minute and try again.";
				return;
			}
			if (responseCode == 17) {
				loginScreen.message1 = "You are standing in a members-only area.";
				loginScreen.message2 = "To play on this world move to a free area first";
				return;
			}
			if (responseCode == 18) {
				loginScreen.message1 = "Account locked as we suspect it has been stolen.";
				loginScreen.message2 = "Press 'recover a locked account' on front page.";
				return;
			}
			if (responseCode == 20) {
				loginScreen.message1 = "Invalid loginserver requested";
				loginScreen.message2 = "Please try using a different world.";
				return;
			}
			if (responseCode == 21) {
				int transferSeconds = networkSession.read();
				for (transferSeconds += 3; transferSeconds >= 0; transferSeconds--) {
					loginScreen.message1 = "You have only just left another world";
					loginScreen.message2 = "Your profile will be transferred in: " + transferSeconds;
					drawLoginScreen(true);
					try {
						Thread.sleep(1200L);
					} catch (Exception ignored2) {
					}
				}

				login(loginUsername, loginPassword, reconnecting);
				return;
			}
			if (responseCode == 22) {
				loginScreen.message1 = "Malformed login packet.";
				loginScreen.message2 = "Please try again.";
				return;
			}
			if (responseCode == 23) {
				loginScreen.message1 = "No reply from loginserver.";
				loginScreen.message2 = "Please try again.";
				return;
			}
			if (responseCode == 24) {
				loginScreen.message1 = "Error loading your profile.";
				loginScreen.message2 = "Please contact customer support.";
				return;
			}
			if (responseCode == 25) {
				loginScreen.message1 = "Unexpected loginserver response.";
				loginScreen.message2 = "Please try using a different world.";
				return;
			}
			if (responseCode == 26) {
				loginScreen.message1 = "This computers address has been blocked";
				loginScreen.message2 = "as it was used to break our rules";
				return;
			}
			if (responseCode == -1) {
				if (initialResponseCode == 0) {
					if (loginFailures < 2) {
						try {
							Thread.sleep(2000L);
						} catch (Exception ignored3) {
						}
						loginFailures++;
						login(loginUsername, loginPassword, reconnecting);
						return;
					} else {
						loginScreen.message1 = "No response from loginserver";
						loginScreen.message2 = "Please wait 1 minute and try again.";
						return;
					}
				} else {
					loginScreen.message1 = "No response from server";
					loginScreen.message2 = "Please try using a different world.";
					return;
				}
			} else {
				System.out.println("response:" + responseCode);
				loginScreen.message1 = "Unexpected server response";
				loginScreen.message2 = "Please try using a different world.";
				return;
			}
		} catch (IOException ignored4) {
			loginScreen.message1 = "";
		}
		loginScreen.message2 = "Error connecting to server.";
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
		int objectId = uid >> 14 & 0x7fff;
		int config = worldState.scene.getConfig(currentPlane, tileX, tileY, uid);
		if (config == -1)
			return false;
		int type = config & 0x1f;
		int orientation = config >> 6 & 3;
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

	/**
	 * Advances the title flame simulation by two original update steps.
	 */
	public void updateTitleFlames() {
		char flameHeight = '\u0100';
		for (int sparkX = 10; sparkX < 117; sparkX++) {
			// Random control value for the original title-flame spark distribution.
			int sparkRoll = (int) (Math.random() * 100D);
			if (sparkRoll < 50)
				titleFlameIntensity[sparkX + (flameHeight - 2 << 7)] = 255;
		}

		for (int sparkIndex = 0; sparkIndex < 100; sparkIndex++) {
			int sparkX2 = (int) (Math.random() * 124D) + 2;
			int sparkY = (int) (Math.random() * 128D) + 128;
			int sparkOffset = sparkX2 + (sparkY << 7);
			titleFlameIntensity[sparkOffset] = 192;
		}

		for (int blurY = 1; blurY < flameHeight - 1; blurY++) {
			for (int blurX = 1; blurX < 127; blurX++) {
				int blurOffset = blurX + (blurY << 7);
				titleFlameIntensityScratch[blurOffset] = (titleFlameIntensity[blurOffset - 1]
						+ titleFlameIntensity[blurOffset + 1] + titleFlameIntensity[blurOffset - 128]
						+ titleFlameIntensity[blurOffset + 128]) / 4;
			}

		}

		titleFlameNoiseOffset += 128;
		if (titleFlameNoiseOffset > titleFlameNoise.length) {
			titleFlameNoiseOffset -= titleFlameNoise.length;
			int runeIndex = (int) (Math.random() * 12D);
			initializeTitleFlameNoise(titleRunes[runeIndex]);
		}
		for (int noiseY = 1; noiseY < flameHeight - 1; noiseY++) {
			for (int noiseX = 1; noiseX < 127; noiseX++) {
				int noiseOffset = noiseX + (noiseY << 7);
				int intensity = titleFlameIntensityScratch[noiseOffset + 128]
						- titleFlameNoise[noiseOffset + titleFlameNoiseOffset & titleFlameNoise.length - 1] / 5;
				if (intensity < 0)
					intensity = 0;
				titleFlameIntensity[noiseOffset] = intensity;
			}

		}

		for (int lineIndex = 0; lineIndex < flameHeight - 1; lineIndex++)
			titleFlameLineOffsets[lineIndex] = titleFlameLineOffsets[lineIndex + 1];

		titleFlameLineOffsets[flameHeight - 1] = (int) (Math.sin((double) gameCycle / 14D) * 16D
				+ Math.sin((double) gameCycle / 15D) * 14D + Math.sin((double) gameCycle / 16D) * 12D);
		if (greenFlameTransition > 0)
			greenFlameTransition -= 4;
		if (blueFlameTransition > 0)
			blueFlameTransition -= 4;
		if (greenFlameTransition == 0 && blueFlameTransition == 0) {
			// Random trigger for the green/blue palette transitions; historical name is
			// unknown.
			int transitionRoll = (int) (Math.random() * 2000D);
			if (transitionRoll == 0)
				greenFlameTransition = 1024;
			if (transitionRoll == 1)
				blueFlameTransition = 1024;
		}
	}

	/**
	 * Adds context-menu actions for an NPC at the supplied scene tile.
	 *
	 * @param definition the definition
	 * @param tileY      the local scene-tile Y coordinate
	 * @param tileX      the local scene-tile X coordinate
	 * @param npcIndex   the npc index
	 */
	public void buildNpcMenu(NpcDefinition definition, int tileY, int tileX, int npcIndex) {
		if (menuState.count >= 400)
			return;
		if (definition.morphIds != null)
			definition = definition.transform();
		if (definition == null)
			return;
		if (!definition.clickable)
			return;
		String displayName = definition.name;
		if (definition.combatLevel != 0)
			displayName = displayName + getCombatLevelColorTag(definition.combatLevel, localPlayer.combatLevel)
					+ " (level-" + definition.combatLevel + ")";
		if (interfaceState.itemSelected == 1) {
			menuState.actionNames[menuState.count] = "Use " + interfaceState.selectedItemName + " with @yel@"
					+ displayName;
			menuState.actionIds[menuState.count] = 347;
			menuState.actionCmd1[menuState.count] = npcIndex;
			menuState.actionCmd2[menuState.count] = tileX;
			menuState.actionCmd3[menuState.count] = tileY;
			menuState.count++;
			return;
		}
		if (interfaceState.spellSelected == 1) {
			if ((interfaceState.selectedSpellTargetMask & 2) == 2) {
				menuState.actionNames[menuState.count] = interfaceState.selectedSpellAction + " @yel@" + displayName;
				menuState.actionIds[menuState.count] = 67;
				menuState.actionCmd1[menuState.count] = npcIndex;
				menuState.actionCmd2[menuState.count] = tileX;
				menuState.actionCmd3[menuState.count] = tileY;
				menuState.count++;
				return;
			}
		} else {
			if (definition.actions != null) {
				for (int actionIndex = 4; actionIndex >= 0; actionIndex--)
					if (definition.actions[actionIndex] != null
							&& !definition.actions[actionIndex].equalsIgnoreCase("attack")) {
						menuState.actionNames[menuState.count] = definition.actions[actionIndex] + " @yel@"
								+ displayName;
						if (actionIndex == 0)
							menuState.actionIds[menuState.count] = 318;
						if (actionIndex == 1)
							menuState.actionIds[menuState.count] = 921;
						if (actionIndex == 2)
							menuState.actionIds[menuState.count] = 118;
						if (actionIndex == 3)
							menuState.actionIds[menuState.count] = 553;
						if (actionIndex == 4)
							menuState.actionIds[menuState.count] = 432;
						menuState.actionCmd1[menuState.count] = npcIndex;
						menuState.actionCmd2[menuState.count] = tileX;
						menuState.actionCmd3[menuState.count] = tileY;
						menuState.count++;
					}

			}
			if (definition.actions != null) {
				for (int actionIndex2 = 4; actionIndex2 >= 0; actionIndex2--)
					if (definition.actions[actionIndex2] != null
							&& definition.actions[actionIndex2].equalsIgnoreCase("attack")) {
						char priorityOffset = '\0';
						if (definition.combatLevel > localPlayer.combatLevel)
							priorityOffset = '\u07D0';
						menuState.actionNames[menuState.count] = definition.actions[actionIndex2] + " @yel@"
								+ displayName;
						if (actionIndex2 == 0)
							menuState.actionIds[menuState.count] = 318 + priorityOffset;
						if (actionIndex2 == 1)
							menuState.actionIds[menuState.count] = 921 + priorityOffset;
						if (actionIndex2 == 2)
							menuState.actionIds[menuState.count] = 118 + priorityOffset;
						if (actionIndex2 == 3)
							menuState.actionIds[menuState.count] = 553 + priorityOffset;
						if (actionIndex2 == 4)
							menuState.actionIds[menuState.count] = 432 + priorityOffset;
						menuState.actionCmd1[menuState.count] = npcIndex;
						menuState.actionCmd2[menuState.count] = tileX;
						menuState.actionCmd3[menuState.count] = tileY;
						menuState.count++;
					}

			}
			menuState.actionNames[menuState.count] = "Examine @yel@" + displayName;
			menuState.actionIds[menuState.count] = 1668;
			menuState.actionCmd1[menuState.count] = npcIndex;
			menuState.actionCmd2[menuState.count] = tileX;
			menuState.actionCmd3[menuState.count] = tileY;
			menuState.count++;
		}
	}

	/**
	 * Randomizes and smooths the title flame noise buffer, optionally masking it
	 * with a rune sprite.
	 *
	 * @param rune the rune
	 */
	public void initializeTitleFlameNoise(IndexedImage rune) {
		int flameHeight = 256;
		for (int clearIndex = 0; clearIndex < titleFlameNoise.length; clearIndex++)
			titleFlameNoise[clearIndex] = 0;

		for (int randomPoint = 0; randomPoint < 5000; randomPoint++) {
			int randomOffset = (int) (Math.random() * 128D * (double) flameHeight);
			titleFlameNoise[randomOffset] = (int) (Math.random() * 256D);
		}

		for (int blurPass = 0; blurPass < 20; blurPass++) {
			for (int blurY = 1; blurY < flameHeight - 1; blurY++) {
				for (int blurX = 1; blurX < 127; blurX++) {
					int blurOffset = blurX + (blurY << 7);
					titleFlameNoiseScratch[blurOffset] = (titleFlameNoise[blurOffset - 1]
							+ titleFlameNoise[blurOffset + 1] + titleFlameNoise[blurOffset - 128]
							+ titleFlameNoise[blurOffset + 128]) / 4;
				}

			}

			int noiseSwap[] = titleFlameNoise;
			titleFlameNoise = titleFlameNoiseScratch;
			titleFlameNoiseScratch = noiseSwap;
		}

		if (rune != null) {
			int runePixelIndex = 0;
			for (int runeY = 0; runeY < rune.height; runeY++) {
				for (int runeX = 0; runeX < rune.width; runeX++)
					if (rune.pixels[runePixelIndex++] != 0) {
						int maskX = runeX + 16 + rune.offsetX;
						int maskY = runeY + 16 + rune.offsetY;
						int maskOffset = maskX + (maskY << 7);
						titleFlameNoise[maskOffset] = 0;
					}

			}

		}
	}

	/**
	 * Draws the chatbox, prompts, item-search results, dialogue interfaces, and
	 * message history.
	 */
	public void drawChatbox() {
		chatboxBuffer.bindRaster();
		Rasterizer3D.scanlineOffsets = chatboxScanlineOffsets;
		chatboxBackground.draw(0, 0);
		if (messagePromptRaised) {
			boldFont.drawCenteredText(promptMessage, 239, 40, 0);
			boldFont.drawCenteredText(promptInput + "*", 239, 60, 128);
		} else if (inputDialogState == 1) {
			boldFont.drawCenteredText("Enter amount:", 239, 40, 0);
			boldFont.drawCenteredText(inputDialogText + "*", 239, 60, 128);
		} else if (inputDialogState == 2) {
			boldFont.drawCenteredText("Enter name:", 239, 40, 0);
			boldFont.drawCenteredText(inputDialogText + "*", 239, 60, 128);
		} else if (inputDialogState == 3) {
			if (inputDialogText != itemSearchQuery) {
				searchItems(inputDialogText);
				itemSearchQuery = inputDialogText;
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
			if (inputDialogText.length() == 0)
				boldFont.drawCenteredText("Enter object name", 239, 40, 255);
			else if (itemSearchResultCount == 0)
				boldFont.drawCenteredText("No matching objects found, please shorten search", 239, 40, 0);
			searchFont.drawCenteredText(inputDialogText + "*", 239, 90, 0);
			Rasterizer.drawHorizontalLine(0, 77, 479, 0);
		} else if (clickToContinueMessage != null) {
			boldFont.drawCenteredText(clickToContinueMessage, 239, 40, 0);
			boldFont.drawCenteredText("Click to continue", 239, 60, 128);
		} else if (interfaceState.chatboxInterfaceId != -1)
			drawInterface(0, 0, Widget.get(interfaceState.chatboxInterfaceId), 0);
		else if (interfaceState.dialogueInterfaceId != -1) {
			drawInterface(0, 0, Widget.get(interfaceState.dialogueInterfaceId), 0);
		} else {
			TypeFace chatFont = plainFont;
			int visibleLine = 0;
			Rasterizer.setCoordinates(0, 0, 463, 77);
			for (int messageIndex = 0; messageIndex < 100; messageIndex++)
				if (chatHistory.messages[messageIndex] != null) {
					int messageType = chatHistory.types[messageIndex];
					int lineY = (70 - visibleLine * 14) + chatScrollOffset;
					String sender = chatHistory.senders[messageIndex];
					byte rightsIcon = 0;
					if (sender != null && sender.startsWith("@cr1@")) {
						sender = sender.substring(5);
						rightsIcon = 1;
					}
					if (sender != null && sender.startsWith("@cr2@")) {
						sender = sender.substring(5);
						rightsIcon = 2;
					}
					if (messageType == 0) {
						if (lineY > 0 && lineY < 110)
							chatFont.drawText(chatHistory.messages[messageIndex], 4, lineY, 0);
						visibleLine++;
					}
					if ((messageType == 1 || messageType == 2) && (messageType == 1 || publicChatMode == 0
							|| publicChatMode == 1 && isFriendOrSelf(sender))) {
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
							chatFont.drawText(chatHistory.messages[messageIndex], textX, lineY, 255);
						}
						visibleLine++;
					}
					if ((messageType == 3 || messageType == 7) && splitPrivateChat == 0 && (messageType == 7
							|| privateChatMode == 0 || privateChatMode == 1 && isFriendOrSelf(sender))) {
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
							chatFont.drawText(chatHistory.messages[messageIndex], textX2, lineY, 0x800000);
						}
						visibleLine++;
					}
					if (messageType == 4 && (tradeMode == 0 || tradeMode == 1 && isFriendOrSelf(sender))) {
						if (lineY > 0 && lineY < 110)
							chatFont.drawText(sender + " " + chatHistory.messages[messageIndex], 4, lineY, 0x800080);
						visibleLine++;
					}
					if (messageType == 5 && splitPrivateChat == 0 && privateChatMode < 2) {
						if (lineY > 0 && lineY < 110)
							chatFont.drawText(chatHistory.messages[messageIndex], 4, lineY, 0x800000);
						visibleLine++;
					}
					if (messageType == 6 && splitPrivateChat == 0 && privateChatMode < 2) {
						if (lineY > 0 && lineY < 110) {
							chatFont.drawText("To " + sender + ":", 4, lineY, 0);
							chatFont.drawText(chatHistory.messages[messageIndex],
									12 + chatFont.getFormattedTextWidth("To " + sender), lineY, 0x800000);
						}
						visibleLine++;
					}
					if (messageType == 8 && (tradeMode == 0 || tradeMode == 1 && isFriendOrSelf(sender))) {
						if (lineY > 0 && lineY < 110)
							chatFont.drawText(sender + " " + chatHistory.messages[messageIndex], 4, lineY, 0x7e3200);
						visibleLine++;
					}
				}

			Rasterizer.resetCoordinates();
			chatContentHeight = visibleLine * 14 + 7;
			if (chatContentHeight < 78)
				chatContentHeight = 78;
			drawScrollbar(chatContentHeight - chatScrollOffset - 77, 463, 77, chatContentHeight, 0);
			String localDisplayName;
			if (localPlayer != null && localPlayer.name != null)
				localDisplayName = localPlayer.name;
			else
				localDisplayName = TextFormatter.formatDisplayName(loginScreen.username);
			chatFont.drawText(localDisplayName + ":", 4, 90, 0);
			chatFont.drawText(chatInput + "*", 6 + chatFont.getFormattedTextWidth(localDisplayName + ": "), 90, 255);
			Rasterizer.drawHorizontalLine(0, 77, 479, 0);
		}
		if (menuState.open && menuState.screenArea == 2)
			drawContextMenu();
		chatboxBuffer.draw(super.graphics, 17, 357);
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
	 * Formats a CS1/widget-script value using the original overflow placeholder.
	 *
	 * @param value the value
	 * @return the resulting text
	 */
	public String formatWidgetScriptValue(int value) {
		if (value < 0x3b9ac9ff)
			return String.valueOf(value);
		else
			return "*";
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

	/**
	 * Rebuilds and priority-partitions the context menu for the current mouse
	 * location.
	 */
	public void buildContextMenu() {
		if (interfaceState.inventoryDragArea != 0)
			return;
		menuState.reset();
		if (interfaceState.fullscreenInterfaceId != -1) {
			currentHoveredWidgetId = 0;
			currentTooltipWidgetId = 0;
			buildInterfaceMenu(0, Widget.get(interfaceState.fullscreenInterfaceId), 0, 0, 0, super.mouseX,
					super.mouseY);
			if (currentHoveredWidgetId != viewportHoveredWidgetId)
				viewportHoveredWidgetId = currentHoveredWidgetId;
			if (currentTooltipWidgetId != viewportTooltipWidgetId)
				viewportTooltipWidgetId = currentTooltipWidgetId;
			return;
		}
		buildSplitPrivateChatMenu();
		currentHoveredWidgetId = 0;
		currentTooltipWidgetId = 0;
		if (super.mouseX > 4 && super.mouseY > 4 && super.mouseX < 516 && super.mouseY < 338)
			if (interfaceState.openInterfaceId != -1)
				buildInterfaceMenu(4, Widget.get(interfaceState.openInterfaceId), 0, 0, 4, super.mouseX, super.mouseY);
			else
				buildViewportMenu();
		if (currentHoveredWidgetId != viewportHoveredWidgetId)
			viewportHoveredWidgetId = currentHoveredWidgetId;
		if (currentTooltipWidgetId != viewportTooltipWidgetId)
			viewportTooltipWidgetId = currentTooltipWidgetId;
		currentHoveredWidgetId = 0;
		currentTooltipWidgetId = 0;
		if (super.mouseX > 553 && super.mouseY > 205 && super.mouseX < 743 && super.mouseY < 466)
			if (interfaceState.sidebarOverlayInterfaceId != -1)
				buildInterfaceMenu(205, Widget.get(interfaceState.sidebarOverlayInterfaceId), 1, 0, 553, super.mouseX,
						super.mouseY);
			else if (interfaceState.tabInterfaceIds[interfaceState.selectedTab] != -1)
				buildInterfaceMenu(205, Widget.get(interfaceState.tabInterfaceIds[interfaceState.selectedTab]), 1, 0,
						553, super.mouseX, super.mouseY);
		if (currentHoveredWidgetId != sidebarHoveredWidgetId) {
			sidebarRedraw = true;
			sidebarHoveredWidgetId = currentHoveredWidgetId;
		}
		if (currentTooltipWidgetId != sidebarTooltipWidgetId) {
			sidebarRedraw = true;
			sidebarTooltipWidgetId = currentTooltipWidgetId;
		}
		currentHoveredWidgetId = 0;
		currentTooltipWidgetId = 0;
		if (super.mouseX > 17 && super.mouseY > 357 && super.mouseX < 496 && super.mouseY < 453)
			if (interfaceState.chatboxInterfaceId != -1)
				buildInterfaceMenu(357, Widget.get(interfaceState.chatboxInterfaceId), 2, 0, 17, super.mouseX,
						super.mouseY);
			else if (interfaceState.dialogueInterfaceId != -1)
				buildInterfaceMenu(357, Widget.get(interfaceState.dialogueInterfaceId), 3, 0, 17, super.mouseX,
						super.mouseY);
			else if (super.mouseY < 434 && super.mouseX < 426 && inputDialogState == 0)
				buildChatboxMessageMenu(super.mouseY - 357);
		if ((interfaceState.chatboxInterfaceId != -1 || interfaceState.dialogueInterfaceId != -1)
				&& currentHoveredWidgetId != chatboxHoveredWidgetId) {
			chatboxRedraw = true;
			chatboxHoveredWidgetId = currentHoveredWidgetId;
		}
		if ((interfaceState.chatboxInterfaceId != -1 || interfaceState.dialogueInterfaceId != -1)
				&& currentTooltipWidgetId != chatboxTooltipWidgetId) {
			chatboxRedraw = true;
			chatboxTooltipWidgetId = currentTooltipWidgetId;
		}
		menuState.prioritizeActions();

	}

	/**
	 * Returns the legacy color tag for the difference between two combat levels.
	 *
	 * @param playerLevel the player level
	 * @param localLevel  the local level
	 * @return the resulting text
	 */
	public static String getCombatLevelColorTag(int playerLevel, int localLevel) {
		int levelDifference = localLevel - playerLevel;
		if (levelDifference < -9)
			return "@red@";
		if (levelDifference < -6)
			return "@or3@";
		if (levelDifference < -3)
			return "@or2@";
		if (levelDifference < 0)
			return "@or1@";
		if (levelDifference > 9)
			return "@gre@";
		if (levelDifference > 6)
			return "@gr3@";
		if (levelDifference > 3)
			return "@gr2@";
		if (levelDifference > 0)
			return "@gr1@";
		else
			return "@yel@";
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
	 * Composites the current flame intensities onto the left and right title
	 * backgrounds.
	 */
	public void drawTitleFlames() {
		char flameHeight = '\u0100';
		if (greenFlameTransition > 0) {
			for (int paletteIndex = 0; paletteIndex < 256; paletteIndex++)
				if (greenFlameTransition > 768)
					titleFlamePalette[paletteIndex] = blendTitleFlameColors(titleFlameRedPalette[paletteIndex],
							titleFlameGreenPalette[paletteIndex], 1024 - greenFlameTransition);
				else if (greenFlameTransition > 256)
					titleFlamePalette[paletteIndex] = titleFlameGreenPalette[paletteIndex];
				else
					titleFlamePalette[paletteIndex] = blendTitleFlameColors(titleFlameGreenPalette[paletteIndex],
							titleFlameRedPalette[paletteIndex], 256 - greenFlameTransition);

		} else if (blueFlameTransition > 0) {
			for (int paletteIndex2 = 0; paletteIndex2 < 256; paletteIndex2++)
				if (blueFlameTransition > 768)
					titleFlamePalette[paletteIndex2] = blendTitleFlameColors(titleFlameRedPalette[paletteIndex2],
							titleFlameBluePalette[paletteIndex2], 1024 - blueFlameTransition);
				else if (blueFlameTransition > 256)
					titleFlamePalette[paletteIndex2] = titleFlameBluePalette[paletteIndex2];
				else
					titleFlamePalette[paletteIndex2] = blendTitleFlameColors(titleFlameBluePalette[paletteIndex2],
							titleFlameRedPalette[paletteIndex2], 256 - blueFlameTransition);

		} else {
			for (int paletteIndex3 = 0; paletteIndex3 < 256; paletteIndex3++)
				titleFlamePalette[paletteIndex3] = titleFlameRedPalette[paletteIndex3];

		}
		for (int pixelIndex = 0; pixelIndex < 33920; pixelIndex++)
			titleLeftFlameBuffer.pixels[pixelIndex] = titleLeftFlameBackground.pixels[pixelIndex];

		int intensityOffset = 0;
		int framebufferOffset = 1152;
		for (int row = 1; row < flameHeight - 1; row++) {
			int lineOffset = (titleFlameLineOffsets[row] * (flameHeight - row)) / flameHeight;
			int leftInset = 22 + lineOffset;
			if (leftInset < 0)
				leftInset = 0;
			intensityOffset += leftInset;
			for (int column = leftInset; column < 128; column++) {
				int intensity = titleFlameIntensity[intensityOffset++];
				if (intensity != 0) {
					int alpha = intensity;
					int inverseAlpha = 256 - intensity;
					intensity = titleFlamePalette[intensity];
					int backgroundColor = titleLeftFlameBuffer.pixels[framebufferOffset];
					titleLeftFlameBuffer.pixels[framebufferOffset++] = ((intensity & 0xff00ff) * alpha
							+ (backgroundColor & 0xff00ff) * inverseAlpha & 0xff00ff00)
							+ ((intensity & 0xff00) * alpha + (backgroundColor & 0xff00) * inverseAlpha
									& 0xff0000) >> 8;
				} else {
					framebufferOffset++;
				}
			}

			framebufferOffset += leftInset;
		}

		titleLeftFlameBuffer.draw(super.graphics, 0, 0);
		for (int pixelIndex2 = 0; pixelIndex2 < 33920; pixelIndex2++)
			titleRightFlameBuffer.pixels[pixelIndex2] = titleRightFlameBackground.pixels[pixelIndex2];

		intensityOffset = 0;
		framebufferOffset = 1176;
		for (int row2 = 1; row2 < flameHeight - 1; row2++) {
			int lineOffset2 = (titleFlameLineOffsets[row2] * (flameHeight - row2)) / flameHeight;
			int visibleWidth = 103 - lineOffset2;
			framebufferOffset += lineOffset2;
			for (int column2 = 0; column2 < visibleWidth; column2++) {
				int intensity2 = titleFlameIntensity[intensityOffset++];
				if (intensity2 != 0) {
					int alpha2 = intensity2;
					int inverseAlpha2 = 256 - intensity2;
					intensity2 = titleFlamePalette[intensity2];
					int backgroundColor2 = titleRightFlameBuffer.pixels[framebufferOffset];
					titleRightFlameBuffer.pixels[framebufferOffset++] = ((intensity2 & 0xff00ff) * alpha2
							+ (backgroundColor2 & 0xff00ff) * inverseAlpha2 & 0xff00ff00)
							+ ((intensity2 & 0xff00) * alpha2 + (backgroundColor2 & 0xff00) * inverseAlpha2
									& 0xff0000) >> 8;
				} else {
					framebufferOffset++;
				}
			}

			intensityOffset += 128 - visibleWidth;
			framebufferOffset += 128 - visibleWidth - lineOffset2;
		}

		titleRightFlameBuffer.draw(super.graphics, 637, 0);
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
		if (contentType >= 1 && contentType <= 100 || contentType >= 701 && contentType <= 800) {
			if (contentType == 1 && socialManager.friendListStatus == 0) {
				widget.text = "Loading friend list";
				widget.buttonType = 0;
				return;
			}
			if (contentType == 1 && socialManager.friendListStatus == 1) {
				widget.text = "Connecting to friendserver";
				widget.buttonType = 0;
				return;
			}
			if (contentType == 2 && socialManager.friendListStatus != 2) {
				widget.text = "Please wait...";
				widget.buttonType = 0;
				return;
			}
			int friendCount = socialManager.friendCount;
			if (socialManager.friendListStatus != 2)
				friendCount = 0;
			if (contentType > 700)
				contentType -= 601;
			else
				contentType--;
			if (contentType >= friendCount) {
				widget.text = "";
				widget.buttonType = 0;
				return;
			} else {
				widget.text = socialManager.friendNames[contentType];
				widget.buttonType = 1;
				return;
			}
		}
		if (contentType >= 101 && contentType <= 200 || contentType >= 801 && contentType <= 900) {
			int friendCount2 = socialManager.friendCount;
			if (socialManager.friendListStatus != 2)
				friendCount2 = 0;
			if (contentType > 800)
				contentType -= 701;
			else
				contentType -= 101;
			if (contentType >= friendCount2) {
				widget.text = "";
				widget.buttonType = 0;
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
			widget.buttonType = 1;
			return;
		}
		if (contentType == 203) {
			int friendCount3 = socialManager.friendCount;
			if (socialManager.friendListStatus != 2)
				friendCount3 = 0;
			widget.scrollHeight = friendCount3 * 15 + 20;
			if (widget.scrollHeight <= widget.height)
				widget.scrollHeight = widget.height + 1;
			return;
		}
		if (contentType >= 401 && contentType <= 500) {
			if ((contentType -= 401) == 0 && socialManager.friendListStatus == 0) {
				widget.text = "Loading ignore list";
				widget.buttonType = 0;
				return;
			}
			if (contentType == 1 && socialManager.friendListStatus == 0) {
				widget.text = "Please wait...";
				widget.buttonType = 0;
				return;
			}
			int ignoreCount = socialManager.ignoreCount;
			if (socialManager.friendListStatus == 0)
				ignoreCount = 0;
			if (contentType >= ignoreCount) {
				widget.text = "";
				widget.buttonType = 0;
				return;
			} else {
				widget.text = TextFormatter
						.formatDisplayName(Base37.decode(socialManager.ignoreEncodedNames[contentType]));
				widget.buttonType = 1;
				return;
			}
		}
		if (contentType == 503) {
			widget.scrollHeight = socialManager.ignoreCount * 15 + 20;
			if (widget.scrollHeight <= widget.height)
				widget.scrollHeight = widget.height + 1;
			return;
		}
		if (contentType == 327) {
			widget.modelPitch = 150;
			widget.modelYaw = (int) (Math.sin((double) gameCycle / 40D) * 256D) & 0x7ff;
			if (appearanceModelDirty) {
				for (int bodyPart = 0; bodyPart < 7; bodyPart++) {
					int kitId = appearanceKitIds[bodyPart];
					if (kitId >= 0 && !IdentityKit.definitions[kitId].areBodyModelsReady())
						return;
				}

				appearanceModelDirty = false;
				Model aclass50_sub1_sub4_sub4[] = new Model[7];
				int modelCount = 0;
				for (int appearanceSlot = 0; appearanceSlot < 7; appearanceSlot++) {
					int kitId2 = appearanceKitIds[appearanceSlot];
					if (kitId2 >= 0)
						aclass50_sub1_sub4_sub4[modelCount++] = IdentityKit.definitions[kitId2].buildBodyModel();
				}

				Model model = new Model(modelCount, aclass50_sub1_sub4_sub4);
				for (int colorSlot = 0; colorSlot < 5; colorSlot++)
					if (appearanceColors[colorSlot] != 0) {
						model.recolor(bodyColorPalettes[colorSlot][0],
								bodyColorPalettes[colorSlot][appearanceColors[colorSlot]]);
						if (colorSlot == 1)
							model.recolor(skinColorPalette[0], skinColorPalette[appearanceColors[colorSlot]]);
					}

				model.createBones();
				model.applyTransformation(
						AnimationSequence.sequences[((Actor) (localPlayer)).idleSequence].primaryFrameIds[0]);
				model.light(64, 850, -30, -50, -30, true);
				widget.mediaType = 5;
				widget.mediaId = 0;
				Widget.cacheModel(5, 0, model);
			}
			return;
		}
		if (contentType == 324) {
			if (maleAppearanceButtonSprite == null) {
				maleAppearanceButtonSprite = widget.sprite;
				femaleAppearanceButtonSprite = widget.activeSprite;
			}
			if (maleAppearance) {
				widget.sprite = femaleAppearanceButtonSprite;
				return;
			} else {
				widget.sprite = maleAppearanceButtonSprite;
				return;
			}
		}
		if (contentType == 325) {
			if (maleAppearanceButtonSprite == null) {
				maleAppearanceButtonSprite = widget.sprite;
				femaleAppearanceButtonSprite = widget.activeSprite;
			}
			if (maleAppearance) {
				widget.sprite = maleAppearanceButtonSprite;
				return;
			} else {
				widget.sprite = femaleAppearanceButtonSprite;
				return;
			}
		}
		if (contentType == 600) {
			widget.text = reportAbuseName;
			if (gameCycle % 20 < 10) {
				widget.text += "|";
				return;
			} else {
				widget.text += " ";
				return;
			}
		}
		if (contentType == 620)
			if (playerRights >= 1) {
				if (reportAbuseMutePlayer) {
					widget.color = 0xff0000;
					widget.text = "Moderator option: Mute player for 48 hours: <ON>";
				} else {
					widget.color = 0xffffff;
					widget.text = "Moderator option: Mute player for 48 hours: <OFF>";
				}
			} else {
				widget.text = "";
			}
		if (contentType == 660) {
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
		if (contentType == 661)
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
		if (contentType == 662) {
			String messageSummary;
			if (unreadMessageCount == 0)
				messageSummary = "@yel@0 unread messages";
			else if (unreadMessageCount == 1)
				messageSummary = "@gre@1 unread message";
			else
				messageSummary = "@gre@" + unreadMessageCount + " unread messages";
			widget.text = "You have " + messageSummary + "\\nin your message centre.";
		}
		if (contentType == 663)
			if (lastPasswordChangeDate <= 0 || lastPasswordChangeDate > accountCurrentDay + 10)
				widget.text = "Last password change:\\n@gre@Never changed";
			else
				widget.text = "Last password change:\\n@gre@" + formatAccountDate(lastPasswordChangeDate);
		if (contentType == 665)
			if (membershipDays > 2 && !membersWorld)
				widget.text = "This is a non-members\\nworld. To enjoy your\\nmembers benefits we\\nrecommend you play on a\\nmembers world instead.";
			else if (membershipDays > 2)
				widget.text = "\\n\\nYou have @gre@" + membershipDays + "@yel@ days of\\nmember credit remaining.";
			else if (membershipDays > 0)
				widget.text = "You have @gre@" + membershipDays
						+ "@yel@ days of\\nmember credit remaining.\\n\\n@lre@Credit low! Renew now\\n@lre@to avoid losing members.";
			else
				widget.text = "You are not a member.\\n\\nChoose to subscribe and\\nyou'll get loads of extra\\nbenefits and features.";
		if (contentType == 667)
			if (membershipDays > 2 && !membersWorld)
				widget.text = "To switch to a members-only world:\\n1) Logout and return to the world selection page.\\n2) Choose one of the members world with a gold star next to it's name.\\n\\nIf you prefer you can continue to use this world,\\nbut members only features will be unavailable here.";
			else if (membershipDays > 0)
				widget.text = "To extend or cancel a subscription:\\n1) Logout and return to the frontpage of this website.\\n2)Choose the relevant option from the 'membership' section.\\n\\nNote: If you are a credit card subscriber a top-up payment will\\nautomatically be taken when 3 days credit remain.\\n(unless you cancel your subscription, which can be done at any time.)";
			else
				widget.text = "To start a subscripton:\\n1) Logout and return to the frontpage of this website.\\n2) Choose 'Start a new subscription'";
		if (contentType == 668) {
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
	 * Applies a changed varp to client settings such as brightness, music, sound,
	 * and chat options.
	 *
	 * @param varpId the varp identifier
	 */
	public void applyVarp(int varpId) {
		int clientCode = Varp.definitions[varpId].clientCode;
		if (clientCode == 0)
			return;
		int varpValue = varpValues[varpId];
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
			splitPrivateChat = varpValue;
			chatboxRedraw = true;
		}
		if (clientCode == 9)
			inventoryRearrangeMode = varpValue;
	}

	/**
	 * Blends two title-flame palette colors using the original 8-bit fixed-point
	 * weight.
	 *
	 * @param fromColor the from color
	 * @param toColor   the to color
	 * @param blend     the blend
	 * @return the resulting numeric value
	 */
	public int blendTitleFlameColors(int fromColor, int toColor, int blend) {
		int inverseBlend = 256 - blend;
		return ((fromColor & 0xff00ff) * inverseBlend + (toColor & 0xff00ff) * blend & 0xff00ff00)
				+ ((fromColor & 0xff00) * inverseBlend + (toColor & 0xff00) * blend & 0xff0000) >> 8;
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

	/**
	 * Computes context-menu dimensions and opens it in the appropriate fixed screen
	 * area.
	 */
	public void openContextMenu() {
		int textWidth = boldFont.getFormattedTextWidth("Choose Option");
		for (int menuIndex = 0; menuIndex < menuState.count; menuIndex++) {
			int textWidth2 = boldFont.getFormattedTextWidth(menuState.actionNames[menuIndex]);
			if (textWidth2 > textWidth)
				textWidth = textWidth2;
		}

		textWidth += 8;
		int menuHeight = 15 * menuState.count + 21;
		if (super.clickX > 4 && super.clickY > 4 && super.clickX < 516 && super.clickY < 338) {
			int clickX = super.clickX - 4 - textWidth / 2;
			if (clickX + textWidth > 512)
				clickX = 512 - textWidth;
			if (clickX < 0)
				clickX = 0;
			int clickY = super.clickY - 4;
			if (clickY + menuHeight > 334)
				clickY = 334 - menuHeight;
			if (clickY < 0)
				clickY = 0;
			menuState.open = true;
			menuState.screenArea = 0;
			menuState.offsetX = clickX;
			menuState.offsetY = clickY;
			menuState.width = textWidth;
			menuState.height = 15 * menuState.count + 22;
		}
		if (super.clickX > 553 && super.clickY > 205 && super.clickX < 743 && super.clickY < 466) {
			int clickX2 = super.clickX - 553 - textWidth / 2;
			if (clickX2 < 0)
				clickX2 = 0;
			else if (clickX2 + textWidth > 190)
				clickX2 = 190 - textWidth;
			int clickY2 = super.clickY - 205;
			if (clickY2 < 0)
				clickY2 = 0;
			else if (clickY2 + menuHeight > 261)
				clickY2 = 261 - menuHeight;
			menuState.open = true;
			menuState.screenArea = 1;
			menuState.offsetX = clickX2;
			menuState.offsetY = clickY2;
			menuState.width = textWidth;
			menuState.height = 15 * menuState.count + 22;
		}
		if (super.clickX > 17 && super.clickY > 357 && super.clickX < 496 && super.clickY < 453) {
			int clickX3 = super.clickX - 17 - textWidth / 2;
			if (clickX3 < 0)
				clickX3 = 0;
			else if (clickX3 + textWidth > 479)
				clickX3 = 479 - textWidth;
			int clickY3 = super.clickY - 357;
			if (clickY3 < 0)
				clickY3 = 0;
			else if (clickY3 + menuHeight > 96)
				clickY3 = 96 - menuHeight;
			menuState.open = true;
			menuState.screenArea = 2;
			menuState.offsetX = clickX3;
			menuState.offsetY = clickY3;
			menuState.width = textWidth;
			menuState.height = 15 * menuState.count + 22;
		}
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
		if (interfaceState.walkableInterfaceId != -1) {
			widgetRuntime.updateAnimations(animationCycleDelta, interfaceState.walkableInterfaceId);
			drawInterface(0, 0, Widget.get(interfaceState.walkableInterfaceId), 0);
		}
		if (interfaceState.openInterfaceId != -1) {
			widgetRuntime.updateAnimations(animationCycleDelta, interfaceState.openInterfaceId);
			drawInterface(0, 0, Widget.get(interfaceState.openInterfaceId), 0);
		}
		updateTutorialIslandFlag();
		if (!menuState.open) {
			buildContextMenu();
			drawMenuTooltip();
		} else if (menuState.screenArea == 0)
			drawContextMenu();
		if (multiCombatZone == 1)
			multiCombatOverlay.drawImage(472, 296);
		if (showFps) {
			char rightAlignedX = '\u01FB';
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
				plainFont.drawText("System update in: " + minutesRemaining + ":0" + secondsRemaining, 4, 329, 0xffff00);
			else
				plainFont.drawText("System update in: " + minutesRemaining + ":" + secondsRemaining, 4, 329, 0xffff00);
			systemUpdateKeepaliveCounter++;
			if (systemUpdateKeepaliveCounter > 112) {
				systemUpdateKeepaliveCounter = 0;
				networkSession.outgoing.writeOpcode(197);
				networkSession.outgoing.writeInt(0);
			}
		}
	}

	/**
	 * Runs the GameShell loop or delegates the dedicated title-flame thread.
	 */
	public void run() {
		if (titleFlameThreadMode) {
			runTitleFlameLoop();
			return;
		} else {
			super.run();
			return;
		}
	}

	/**
	 * Builds context-menu actions for names visible in the split-private-chat
	 * overlay.
	 */
	public void buildSplitPrivateChatMenu() {
		if (splitPrivateChat == 0)
			return;
		int visibleLine = 0;
		if (systemUpdateTimer != 0)
			visibleLine = 1;
		for (int messageIndex = 0; messageIndex < 100; messageIndex++)
			if (chatHistory.messages[messageIndex] != null) {
				int messageType = chatHistory.types[messageIndex];
				String sender = chatHistory.senders[messageIndex];
				if (sender != null && sender.startsWith("@cr1@")) {
					sender = sender.substring(5);
				}
				if (sender != null && sender.startsWith("@cr2@")) {
					sender = sender.substring(5);
				}
				if ((messageType == 3 || messageType == 7) && (messageType == 7 || privateChatMode == 0
						|| privateChatMode == 1 && isFriendOrSelf(sender))) {
					int lineY = 329 - visibleLine * 13;
					if (super.mouseX > 4 && super.mouseY - 4 > lineY - 10 && super.mouseY - 4 <= lineY + 3) {
						int messageWidth = plainFont
								.getFormattedTextWidth("From:  " + sender + chatHistory.messages[messageIndex]) + 25;
						if (messageWidth > 450)
							messageWidth = 450;
						if (super.mouseX < 4 + messageWidth) {
							if (playerRights >= 1) {
								menuState.actionNames[menuState.count] = "Report abuse @whi@" + sender;
								menuState.actionIds[menuState.count] = 2507;
								menuState.count++;
							}
							menuState.actionNames[menuState.count] = "Add ignore @whi@" + sender;
							menuState.actionIds[menuState.count] = 2574;
							menuState.count++;
							menuState.actionNames[menuState.count] = "Add friend @whi@" + sender;
							menuState.actionIds[menuState.count] = 2762;
							menuState.count++;
						}
					}
					if (++visibleLine >= 5)
						return;
				}
				if ((messageType == 5 || messageType == 6) && privateChatMode < 2 && ++visibleLine >= 5)
					return;
			}

	}

	/**
	 * Builds context-menu actions for player names under the chatbox mouse
	 * position.
	 *
	 * @param mouseY the mouse Y coordinate
	 */
	public void buildChatboxMessageMenu(int mouseY) {
		int visibleLine = 0;
		for (int messageIndex = 0; messageIndex < 100; messageIndex++) {
			if (chatHistory.messages[messageIndex] == null)
				continue;
			int messageType = chatHistory.types[messageIndex];
			int lineY = (70 - visibleLine * 14) + chatScrollOffset + 4;
			if (lineY < -20)
				break;
			String sender = chatHistory.senders[messageIndex];
			if (sender != null && sender.startsWith("@cr1@")) {
				sender = sender.substring(5);
			}
			if (sender != null && sender.startsWith("@cr2@")) {
				sender = sender.substring(5);
			}
			if (messageType == 0)
				visibleLine++;
			if ((messageType == 1 || messageType == 2)
					&& (messageType == 1 || publicChatMode == 0 || publicChatMode == 1 && isFriendOrSelf(sender))) {
				if (mouseY > lineY - 14 && mouseY <= lineY && !sender.equals(localPlayer.name)) {
					if (playerRights >= 1) {
						menuState.actionNames[menuState.count] = "Report abuse @whi@" + sender;
						menuState.actionIds[menuState.count] = 507;
						menuState.count++;
					}
					menuState.actionNames[menuState.count] = "Add ignore @whi@" + sender;
					menuState.actionIds[menuState.count] = 574;
					menuState.count++;
					menuState.actionNames[menuState.count] = "Add friend @whi@" + sender;
					menuState.actionIds[menuState.count] = 762;
					menuState.count++;
				}
				visibleLine++;
			}
			if ((messageType == 3 || messageType == 7) && splitPrivateChat == 0
					&& (messageType == 7 || privateChatMode == 0 || privateChatMode == 1 && isFriendOrSelf(sender))) {
				if (mouseY > lineY - 14 && mouseY <= lineY) {
					if (playerRights >= 1) {
						menuState.actionNames[menuState.count] = "Report abuse @whi@" + sender;
						menuState.actionIds[menuState.count] = 507;
						menuState.count++;
					}
					menuState.actionNames[menuState.count] = "Add ignore @whi@" + sender;
					menuState.actionIds[menuState.count] = 574;
					menuState.count++;
					menuState.actionNames[menuState.count] = "Add friend @whi@" + sender;
					menuState.actionIds[menuState.count] = 762;
					menuState.count++;
				}
				visibleLine++;
			}
			if (messageType == 4 && (tradeMode == 0 || tradeMode == 1 && isFriendOrSelf(sender))) {
				if (mouseY > lineY - 14 && mouseY <= lineY) {
					menuState.actionNames[menuState.count] = "Accept trade @whi@" + sender;
					menuState.actionIds[menuState.count] = 544;
					menuState.count++;
				}
				visibleLine++;
			}
			if ((messageType == 5 || messageType == 6) && splitPrivateChat == 0 && privateChatMode < 2)
				visibleLine++;
			if (messageType == 8 && (tradeMode == 0 || tradeMode == 1 && isFriendOrSelf(sender))) {
				if (mouseY > lineY - 14 && mouseY <= lineY) {
					menuState.actionNames[menuState.count] = "Accept challenge @whi@" + sender;
					menuState.actionIds[menuState.count] = 695;
					menuState.count++;
				}
				visibleLine++;
			}
		}

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
		int cmd2 = menuState.actionCmd2[menuIndex];
		int cmd3 = menuState.actionCmd3[menuIndex];
		int actionId = MenuState.normalizeActionId(menuState.actionIds[menuIndex]);
		int cmd1 = menuState.actionCmd1[menuIndex];
		if (inputDialogState != 0 && actionId != MenuState.CANCEL_ACTION) {
			inputDialogState = 0;
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

		interfaceState.itemSelected = 0;
		interfaceState.spellSelected = 0;
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
		if (actionId == 200) {
			Player player = actorSynchronizer.players[cmd1];
			if (player != null) {
				walkTo(false, ((Actor) (player)).pathX[0], ((Actor) (player)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(245);
				networkSession.outgoing.writeShortAddLE(cmd1);
			}
		}
		if (actionId == 876) {
			Player player2 = actorSynchronizer.players[cmd1];
			if (player2 != null) {
				walkTo(false, ((Actor) (player2)).pathX[0], ((Actor) (player2)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(45);
				networkSession.outgoing.writeShortAdd(cmd1);
			}
		}
		if (actionId == 677) {
			Player player3 = actorSynchronizer.players[cmd1];
			if (player3 != null) {
				walkTo(false, ((Actor) (player3)).pathX[0], ((Actor) (player3)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(116);
				networkSession.outgoing.writeShortLE(cmd1);
			}
		}
		if (actionId == 493) {
			Player player4 = actorSynchronizer.players[cmd1];
			if (player4 != null) {
				walkTo(false, ((Actor) (player4)).pathX[0], ((Actor) (player4)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(233);
				networkSession.outgoing.writeShortAdd(cmd1);
			}
		}
		if (actionId == 918) {
			Player player5 = actorSynchronizer.players[cmd1];
			if (player5 != null) {
				walkTo(false, ((Actor) (player5)).pathX[0], ((Actor) (player5)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(31);
				networkSession.outgoing.writeShort(cmd1);
				networkSession.outgoing.writeShortLE(interfaceState.selectedSpellWidgetId);
			}
		}
		if (actionId == 596) {
			Player player6 = actorSynchronizer.players[cmd1];
			if (player6 != null) {
				walkTo(false, ((Actor) (player6)).pathX[0], ((Actor) (player6)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(143);
				networkSession.outgoing.writeShortLE(interfaceState.selectedItemId);
				networkSession.outgoing.writeShortAddLE(interfaceState.selectedItemSlot);
				networkSession.outgoing.writeShort(interfaceState.selectedItemWidgetId);
				networkSession.outgoing.writeShortAdd(cmd1);
			}
		}
		if (actionId == 408) {
			Player player7 = actorSynchronizer.players[cmd1];
			if (player7 != null) {
				walkTo(false, ((Actor) (player7)).pathX[0], ((Actor) (player7)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(194);
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
		if (actionId == 921) {
			Npc npc = actorSynchronizer.npcs[cmd1];
			if (npc != null) {
				walkTo(false, ((Actor) (npc)).pathX[0], ((Actor) (npc)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(67);
				networkSession.outgoing.writeShortAdd(cmd1);
			}
		}
		if (actionId == 553) {
			Npc npc2 = actorSynchronizer.npcs[cmd1];
			if (npc2 != null) {
				walkTo(false, ((Actor) (npc2)).pathX[0], ((Actor) (npc2)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(42);
				networkSession.outgoing.writeShortLE(cmd1);
			}
		}
		if (actionId == 347) {
			Npc npc3 = actorSynchronizer.npcs[cmd1];
			if (npc3 != null) {
				walkTo(false, ((Actor) (npc3)).pathX[0], ((Actor) (npc3)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(57);
				networkSession.outgoing.writeShort(cmd1);
				networkSession.outgoing.writeShortLE(interfaceState.selectedItemId);
				networkSession.outgoing.writeShortAddLE(interfaceState.selectedItemWidgetId);
				networkSession.outgoing.writeShort(interfaceState.selectedItemSlot);
			}
		}
		if (actionId == 118) {
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
					networkSession.outgoing.writeOpcode(157);
					networkSession.outgoing.writeInt(0);
					npcAction118Counter = 0;
				}
				networkSession.outgoing.writeOpcode(13);
				networkSession.outgoing.writeShortAddLE(cmd1);
			}
		}
		if (actionId == 432) {
			Npc npc5 = actorSynchronizer.npcs[cmd1];
			if (npc5 != null) {
				walkTo(false, ((Actor) (npc5)).pathX[0], ((Actor) (npc5)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(8);
				networkSession.outgoing.writeShortLE(cmd1);
			}
		}
		if (actionId == 67) {
			Npc npc6 = actorSynchronizer.npcs[cmd1];
			if (npc6 != null) {
				walkTo(false, ((Actor) (npc6)).pathX[0], ((Actor) (npc6)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(104);
				networkSession.outgoing.writeShortAdd(interfaceState.selectedSpellWidgetId);
				networkSession.outgoing.writeShortLE(cmd1);
			}
		}
		if (actionId == 1668) {
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
					addChatMessage("", description, 0);
				}
			}
		}
		if (actionId == 318) {
			Npc npc8 = actorSynchronizer.npcs[cmd1];
			if (npc8 != null) {
				walkTo(false, ((Actor) (npc8)).pathX[0], ((Actor) (npc8)).pathY[0], 1, 1,
						MovementPacketEncoder.INTERACTION, 0, 0, 0);
				crossX = super.clickX;
				crossY = super.clickY;
				crossType = 2;
				crossCycle = 0;
				networkSession.outgoing.writeOpcode(112);
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
		if (actionId == 467 && walkToGameObject(cmd3, cmd2, cmd1)) {
			networkSession.outgoing.writeOpcode(152);
			networkSession.outgoing.writeShortLE(cmd1 >> 14 & 0x7fff);
			networkSession.outgoing.writeShortLE(interfaceState.selectedItemWidgetId);
			networkSession.outgoing.writeShortLE(interfaceState.selectedItemId);
			networkSession.outgoing.writeShortLE(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShort(interfaceState.selectedItemSlot);
			networkSession.outgoing.writeShortAddLE(cmd2 + regionManager.baseX);
		}
		if (actionId == 376 && walkToGameObject(cmd3, cmd2, cmd1)) {
			networkSession.outgoing.writeOpcode(210);
			networkSession.outgoing.writeShort(interfaceState.selectedSpellWidgetId);
			networkSession.outgoing.writeShortLE(cmd1 >> 14 & 0x7fff);
			networkSession.outgoing.writeShortAdd(cmd2 + regionManager.baseX);
			networkSession.outgoing.writeShortLE(cmd3 + regionManager.baseY);
		}
		if (actionId == 1280) {
			walkToGameObject(cmd3, cmd2, cmd1);
			networkSession.outgoing.writeOpcode(55);
			networkSession.outgoing.writeShortLE(cmd1 >> 14 & 0x7fff);
			networkSession.outgoing.writeShortLE(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShort(cmd2 + regionManager.baseX);
		}
		if (actionId == 35) {
			walkToGameObject(cmd3, cmd2, cmd1);
			networkSession.outgoing.writeOpcode(181);
			networkSession.outgoing.writeShortAdd(cmd2 + regionManager.baseX);
			networkSession.outgoing.writeShortLE(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShortLE(cmd1 >> 14 & 0x7fff);
		}
		if (actionId == 888) {
			walkToGameObject(cmd3, cmd2, cmd1);
			networkSession.outgoing.writeOpcode(50);
			networkSession.outgoing.writeShortAdd(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShortLE(cmd1 >> 14 & 0x7fff);
			networkSession.outgoing.writeShortAddLE(cmd2 + regionManager.baseX);
		}
		if (actionId == 1412) {
			int objectId = cmd1 >> 14 & 0x7fff;
			GameObjectDefinition objectDefinition = GameObjectDefinition.lookup(objectId);
			String description;
			if (objectDefinition.description != null)
				description = new String(objectDefinition.description);
			else
				description = "It's a " + objectDefinition.name + ".";
			addChatMessage("", description, 0);
		}
		if (actionId == 892) {
			walkToGameObject(cmd3, cmd2, cmd1);
			networkSession.outgoing.writeOpcode(136);
			networkSession.outgoing.writeShort(cmd2 + regionManager.baseX);
			networkSession.outgoing.writeShortLE(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShort(cmd1 >> 14 & 0x7fff);
		}
		if (actionId == 389) {
			walkToGameObject(cmd3, cmd2, cmd1);
			networkSession.outgoing.writeOpcode(241);
			networkSession.outgoing.writeShort(cmd1 >> 14 & 0x7fff);
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
		if (actionId == 930) {
			boolean routeFound = walkTo(false, cmd2, cmd3, 0, 0, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			if (!routeFound)
				routeFound = walkTo(false, cmd2, cmd3, 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			crossX = super.clickX;
			crossY = super.clickY;
			crossType = 2;
			crossCycle = 0;
			networkSession.outgoing.writeOpcode(54);
			networkSession.outgoing.writeShortAdd(cmd1);
			networkSession.outgoing.writeShortLE(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShort(cmd2 + regionManager.baseX);
		}
		if (actionId == 68) {
			boolean routeFound2 = walkTo(false, cmd2, cmd3, 0, 0, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			if (!routeFound2)
				routeFound2 = walkTo(false, cmd2, cmd3, 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			crossX = super.clickX;
			crossY = super.clickY;
			crossType = 2;
			crossCycle = 0;
			networkSession.outgoing.writeOpcode(77);
			networkSession.outgoing.writeShortAdd(cmd2 + regionManager.baseX);
			networkSession.outgoing.writeShort(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShortAddLE(cmd1);
		}
		if (actionId == 684) {
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
				networkSession.outgoing.writeOpcode(222);
				networkSession.outgoing.writeMedium(0xabc842);
				groundItemAction684Counter = 0;
			}
			networkSession.outgoing.writeOpcode(71);
			networkSession.outgoing.writeShortAddLE(cmd1);
			networkSession.outgoing.writeShortAddLE(cmd2 + regionManager.baseX);
			networkSession.outgoing.writeShortAdd(cmd3 + regionManager.baseY);
		}
		if (actionId == 270) {
			boolean routeFound4 = walkTo(false, cmd2, cmd3, 0, 0, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			if (!routeFound4)
				routeFound4 = walkTo(false, cmd2, cmd3, 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			crossX = super.clickX;
			crossY = super.clickY;
			crossType = 2;
			crossCycle = 0;
			networkSession.outgoing.writeOpcode(230);
			networkSession.outgoing.writeShortLE(cmd1);
			networkSession.outgoing.writeShortAdd(cmd2 + regionManager.baseX);
			networkSession.outgoing.writeShort(cmd3 + regionManager.baseY);
		}
		if (actionId == 100) {
			boolean routeFound5 = walkTo(false, cmd2, cmd3, 0, 0, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			if (!routeFound5)
				routeFound5 = walkTo(false, cmd2, cmd3, 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			crossX = super.clickX;
			crossY = super.clickY;
			crossType = 2;
			crossCycle = 0;
			networkSession.outgoing.writeOpcode(211);
			networkSession.outgoing.writeShortAddLE(interfaceState.selectedItemSlot);
			networkSession.outgoing.writeShortAdd(interfaceState.selectedItemId);
			networkSession.outgoing.writeShortAddLE(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShortAddLE(cmd2 + regionManager.baseX);
			networkSession.outgoing.writeShortLE(interfaceState.selectedItemWidgetId);
			networkSession.outgoing.writeShortLE(cmd1);
		}
		if (actionId == 26) {
			boolean routeFound6 = walkTo(false, cmd2, cmd3, 0, 0, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			if (!routeFound6)
				routeFound6 = walkTo(false, cmd2, cmd3, 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			crossX = super.clickX;
			crossY = super.clickY;
			crossType = 2;
			crossCycle = 0;
			groundItemAction26Counter++;
			if (groundItemAction26Counter >= 120) {
				networkSession.outgoing.writeOpcode(95);
				networkSession.outgoing.writeInt(0);
				groundItemAction26Counter = 0;
			}
			networkSession.outgoing.writeOpcode(100);
			networkSession.outgoing.writeShort(cmd2 + regionManager.baseX);
			networkSession.outgoing.writeShortAdd(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShortAddLE(cmd1);
		}
		if (actionId == 199) {
			boolean routeFound7 = walkTo(false, cmd2, cmd3, 0, 0, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			if (!routeFound7)
				routeFound7 = walkTo(false, cmd2, cmd3, 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			crossX = super.clickX;
			crossY = super.clickY;
			crossType = 2;
			crossCycle = 0;
			networkSession.outgoing.writeOpcode(83);
			networkSession.outgoing.writeShortLE(cmd1);
			networkSession.outgoing.writeShort(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShortLE(interfaceState.selectedSpellWidgetId);
			networkSession.outgoing.writeShortAddLE(cmd2 + regionManager.baseX);
		}
		if (actionId == 1564) {
			ItemDefinition itemDefinition = ItemDefinition.lookup(cmd1);
			String description;
			if (itemDefinition.description != null)
				description = new String(itemDefinition.description);
			else
				description = "It's a " + itemDefinition.name + ".";
			addChatMessage("", description, 0);
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
		interfaceState.pressedInventoryWidgetId = widgetId;
		interfaceState.pressedInventorySlot = slot;
		interfaceState.pressedInventoryArea = 2;
		if (Widget.get(widgetId).parentId == interfaceState.openInterfaceId)
			interfaceState.pressedInventoryArea = 1;
		if (Widget.get(widgetId).parentId == interfaceState.chatboxInterfaceId)
			interfaceState.pressedInventoryArea = 3;
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
		if (actionId == 227) {
			inventoryAction227Counter++;
			if (inventoryAction227Counter >= 62) {
				networkSession.outgoing.writeOpcode(165);
				networkSession.outgoing.writeByte(206);
				inventoryAction227Counter = 0;
			}
			networkSession.outgoing.writeOpcode(228);
			networkSession.outgoing.writeShortLE(cmd2);
			networkSession.outgoing.writeShortAdd(cmd1);
			networkSession.outgoing.writeShort(cmd3);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == 961) {
			inventoryAction961Counter += cmd1;
			if (inventoryAction961Counter >= 115) {
				networkSession.outgoing.writeOpcode(126);
				networkSession.outgoing.writeByte(125);
				inventoryAction961Counter = 0;
			}
			networkSession.outgoing.writeOpcode(203);
			networkSession.outgoing.writeShortAdd(cmd3);
			networkSession.outgoing.writeShortLE(cmd2);
			networkSession.outgoing.writeShortLE(cmd1);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == 9) {
			networkSession.outgoing.writeOpcode(3);
			networkSession.outgoing.writeShortAdd(cmd1);
			networkSession.outgoing.writeShort(cmd3);
			networkSession.outgoing.writeShort(cmd2);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == 399) {
			networkSession.outgoing.writeOpcode(24);
			networkSession.outgoing.writeShortLE(cmd3);
			networkSession.outgoing.writeShortLE(cmd1);
			networkSession.outgoing.writeShortAdd(cmd2);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == 903) {
			networkSession.outgoing.writeOpcode(1);
			networkSession.outgoing.writeShort(cmd1);
			networkSession.outgoing.writeShortLE(interfaceState.selectedItemSlot);
			networkSession.outgoing.writeShortLE(interfaceState.selectedItemId);
			networkSession.outgoing.writeShortAddLE(interfaceState.selectedItemWidgetId);
			networkSession.outgoing.writeShortAdd(cmd2);
			networkSession.outgoing.writeShortAdd(cmd3);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == 361) {
			networkSession.outgoing.writeOpcode(36);
			networkSession.outgoing.writeShort(interfaceState.selectedSpellWidgetId);
			networkSession.outgoing.writeShortAdd(cmd3);
			networkSession.outgoing.writeShortAdd(cmd2);
			networkSession.outgoing.writeShortAdd(cmd1);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == 225) {
			networkSession.outgoing.writeOpcode(177);
			networkSession.outgoing.writeShortAdd(cmd2);
			networkSession.outgoing.writeShortLE(cmd1);
			networkSession.outgoing.writeShortLE(cmd3);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == 891) {
			networkSession.outgoing.writeOpcode(4);
			networkSession.outgoing.writeShortLE(cmd2);
			networkSession.outgoing.writeShortAddLE(cmd1);
			networkSession.outgoing.writeShortAddLE(cmd3);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == 894) {
			networkSession.outgoing.writeOpcode(158);
			networkSession.outgoing.writeShortAddLE(cmd2);
			networkSession.outgoing.writeShortAddLE(cmd1);
			networkSession.outgoing.writeShortLE(cmd3);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == 324) {
			networkSession.outgoing.writeOpcode(161);
			networkSession.outgoing.writeShortAddLE(cmd2);
			networkSession.outgoing.writeShortAddLE(cmd1);
			networkSession.outgoing.writeShortLE(cmd3);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == 1094) {
			ItemDefinition itemDefinition = ItemDefinition.lookup(cmd1);
			Widget widget = Widget.get(cmd3);
			String description;
			if (widget != null && widget.itemAmounts[cmd2] >= 0x186a0)
				description = widget.itemAmounts[cmd2] + " x " + itemDefinition.name;
			else if (itemDefinition.description != null)
				description = new String(itemDefinition.description);
			else
				description = "It's a " + itemDefinition.name + ".";
			addChatMessage("", description, 0);
		}
		if (actionId == 444) {
			networkSession.outgoing.writeOpcode(91);
			networkSession.outgoing.writeShortLE(cmd1);
			networkSession.outgoing.writeShortAddLE(cmd2);
			networkSession.outgoing.writeShort(cmd3);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == 564) {
			networkSession.outgoing.writeOpcode(231);
			networkSession.outgoing.writeShortAddLE(cmd3);
			networkSession.outgoing.writeShortLE(cmd2);
			networkSession.outgoing.writeShort(cmd1);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == 52) {
			interfaceState.itemSelected = 1;
			interfaceState.selectedItemSlot = cmd2;
			interfaceState.selectedItemWidgetId = cmd3;
			interfaceState.selectedItemId = cmd1;
			interfaceState.selectedItemName = String.valueOf(ItemDefinition.lookup(cmd1).name);
			interfaceState.spellSelected = 0;
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
		if (actionId == 890) {
			networkSession.outgoing.writeOpcode(79);
			networkSession.outgoing.writeShort(cmd3);
			Widget widget = Widget.get(cmd3);
			if (widget.cs1Instructions != null && widget.cs1Instructions[0][0] == 5) {
				int varpId = widget.cs1Instructions[0][1];
				varpValues[varpId] = 1 - varpValues[varpId];
				applyVarp(varpId);
				sidebarRedraw = true;
			}
		}
		if (actionId == 639)
			closeInterfaces();
		if (actionId == 70) {
			Widget spellWidget = Widget.get(cmd3);
			interfaceState.spellSelected = 1;
			interfaceState.selectedSpellWidgetId = cmd3;
			interfaceState.selectedSpellTargetMask = spellWidget.spellUsableOn;
			interfaceState.itemSelected = 0;
			sidebarRedraw = true;
			String actionVerb = spellWidget.selectedActionName;
			if (actionVerb.indexOf(" ") != -1)
				actionVerb = actionVerb.substring(0, actionVerb.indexOf(" "));
			String actionTarget = spellWidget.selectedActionName;
			if (actionTarget.indexOf(" ") != -1)
				actionTarget = actionTarget.substring(actionTarget.indexOf(" ") + 1);
			interfaceState.selectedSpellAction = actionVerb + " " + spellWidget.spellName + " " + actionTarget;
			if (interfaceState.selectedSpellTargetMask == 16) {
				sidebarRedraw = true;
				tabAreaRedraw = true;
			}
			return true;
		}
		if (actionId == 352) {
			Widget actionWidget = Widget.get(cmd3);
			boolean sendWidgetClick = true;
			if (actionWidget.contentType > 0)
				sendWidgetClick = handleWidgetContentAction(actionWidget);
			if (sendWidgetClick) {
				networkSession.outgoing.writeOpcode(79);
				networkSession.outgoing.writeShort(cmd3);
			}
		}
		if (actionId == 575 && !interfaceActionPending) {
			networkSession.outgoing.writeOpcode(226);
			networkSession.outgoing.writeShort(cmd3);
			interfaceActionPending = true;
		}
		if (actionId == 518) {
			networkSession.outgoing.writeOpcode(79);
			networkSession.outgoing.writeShort(cmd3);
			Widget configWidget = Widget.get(cmd3);
			if (configWidget.cs1Instructions != null && configWidget.cs1Instructions[0][0] == 5) {
				int varpId2 = configWidget.cs1Instructions[0][1];
				if (varpValues[varpId2] != configWidget.cs1ComparisonValues[0]) {
					varpValues[varpId2] = configWidget.cs1ComparisonValues[0];
					applyVarp(varpId2);
					sidebarRedraw = true;
				}
			}
		}
		if (actionId == 55) {
			unloadInterface(interfaceState.dialogueInterfaceId);
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
		if (actionId == 762 || actionId == 574 || actionId == 775 || actionId == 859) {
			String actionText = menuState.actionNames[menuIndex];
			int markerIndex = actionText.indexOf("@whi@");
			if (markerIndex != -1) {
				long encodedName = Base37.encode(actionText.substring(markerIndex + 5).trim());
				if (actionId == 762)
					addFriend(encodedName);
				if (actionId == 574)
					addIgnore(encodedName);
				if (actionId == 775)
					removeFriend(encodedName);
				if (actionId == 859)
					removeIgnore(encodedName);
			}
		}
		if (actionId == 544 || actionId == 695) {
			String actionText2 = menuState.actionNames[menuIndex];
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
					if (actionId == 544) {
						networkSession.outgoing.writeOpcode(116);
						networkSession.outgoing.writeShortLE(actorSynchronizer.playerIndices[activePlayerIndex]);
					}
					if (actionId == 695) {
						networkSession.outgoing.writeOpcode(245);
						networkSession.outgoing.writeShortAddLE(actorSynchronizer.playerIndices[activePlayerIndex]);
					}
					playerFound = true;
					break;
				}

				if (!playerFound)
					addChatMessage("", "Unable to find " + encodedName2, 0);
			}
		}
		if (actionId == 507) {
			String actionText3 = menuState.actionNames[menuIndex];
			int markerIndex3 = actionText3.indexOf("@whi@");
			if (markerIndex3 != -1)
				if (interfaceState.openInterfaceId == -1) {
					closeInterfaces();
					reportAbuseName = actionText3.substring(markerIndex3 + 5).trim();
					reportAbuseMutePlayer = false;
					interfaceState.reportAbuseInterfaceId = interfaceState.openInterfaceId = Widget.reportAbuseInterfaceId;
				} else {
					addChatMessage("", "Please close the interface you have open before using 'report abuse'", 0);
				}
		}
		if (actionId == 984) {
			String actionText4 = menuState.actionNames[menuIndex];
			int markerIndex4 = actionText4.indexOf("@whi@");
			if (markerIndex4 != -1) {
				long encodedName3 = Base37.encode(actionText4.substring(markerIndex4 + 5).trim());
				int friendIndex = socialManager.findFriendIndex(encodedName3);

				if (friendIndex != -1 && socialManager.friendWorlds[friendIndex] > 0) {
					chatboxRedraw = true;
					inputDialogState = 0;
					messagePromptRaised = true;
					promptInput = "";
					promptAction = 3;
					privateMessageTarget = socialManager.friendEncodedNames[friendIndex];
					promptMessage = "Enter message to send to " + socialManager.friendNames[friendIndex];
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
		if (actionId == 14)
			if (!menuState.open)
				worldState.scene.setClick(super.clickX - 4, super.clickY - 4);
			else
				worldState.scene.setClick(cmd2 - 4, cmd3 - 4);
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
					&& (actorListIndex >= actorSynchronizer.playerCount || publicChatMode == 0 || publicChatMode == 3
							|| publicChatMode == 1 && isFriendOrSelf(((Player) obj).name))) {
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
					Rasterizer.setCoordinates(projectedX - 50, 0, projectedX + 50, 334);
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
					Rasterizer.setCoordinates(0, projectedY - boldFont.lineHeight - 1, 512, projectedY + 5);
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
			chatboxBuffer = new GraphicsBuffer(getGameComponent(), 479, 96);
			minimapBuffer = new GraphicsBuffer(getGameComponent(), 172, 156);
			Rasterizer.resetPixels();
			minimapBackground.draw(0, 0);
			sidebarBuffer = new GraphicsBuffer(getGameComponent(), 190, 261);
			viewportBuffer = new GraphicsBuffer(getGameComponent(), 512, 334);
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
		g.fillRect(0, 0, 765, 503);
		setTargetFps(1);
		if (loadingError) {
			titleFlamesRunning = false;
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
			titleFlamesRunning = false;
			g.setFont(new Font("Helvetica", 1, 20));
			g.setColor(Color.white);
			g.drawString("Error - unable to load game!", 50, 50);
			g.drawString("To play RuneScape make sure you play from", 50, 100);
			g.drawString("http://www.runescape.com", 50, 150);
		}
		if (duplicateClientError) {
			titleFlamesRunning = false;
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
			int boxY = 151;
			if (secondaryMessage != null)
				boxY -= 7;
			plainFont.drawCenteredText(primaryMessage, 257, boxY, 0);
			plainFont.drawCenteredText(primaryMessage, 256, boxY - 1, 0xffffff);
			boxY += 15;
			if (secondaryMessage != null) {
				plainFont.drawCenteredText(secondaryMessage, 257, boxY, 0);
				plainFont.drawCenteredText(secondaryMessage, 256, boxY - 1, 0xffffff);
			}
			viewportBuffer.draw(super.graphics, 4, 4);
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
	 * Tests whether a menu entry represents an Add friend action after priority
	 * normalization.
	 *
	 * @param menuIndex the context-menu entry index
	 * @return true when the requested condition/action succeeds; otherwise false
	 */
	public boolean isAddFriendMenuAction(int menuIndex) {
		return menuState.isAddFriendAction(menuIndex);
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
		if (duplicateClientError || loadingError || invalidHostError) {
			drawStartupErrorScreen();
			return;
		}
		drawCycle++;
		if (!loggedIn)
			drawLoginScreen(false);
		else
			drawGameScreen();
		mouseButtonHoldTicks = 0;
	}

	/**
	 * Draws the open context menu and highlights the entry under the mouse.
	 */
	public void drawContextMenu() {
		int menuX = menuState.offsetX;
		int menuY = menuState.offsetY;
		int menuWidth = menuState.width;
		int menuHeight = menuState.height;
		int headerColor = 0x5d5447;
		Rasterizer.drawFilledRectangle(menuX, menuY, menuWidth, menuHeight, headerColor);
		Rasterizer.drawFilledRectangle(menuX + 1, menuY + 1, menuWidth - 2, 16, 0);
		Rasterizer.drawUnfilledRectangle(menuX + 1, menuY + 18, menuWidth - 2, menuHeight - 19, 0);
		boldFont.drawText("Choose Option", menuX + 3, menuY + 14, headerColor);
		int mouseX = super.mouseX;
		int mouseY = super.mouseY;
		if (menuState.screenArea == 0) {
			mouseX -= 4;
			mouseY -= 4;
		}
		if (menuState.screenArea == 1) {
			mouseX -= 553;
			mouseY -= 205;
		}
		if (menuState.screenArea == 2) {
			mouseX -= 17;
			mouseY -= 357;
		}
		for (int entryIndex = 0; entryIndex < menuState.count; entryIndex++) {
			int entryY = menuY + 31 + (menuState.count - 1 - entryIndex) * 15;
			int entryColor = 0xffffff;
			if (mouseX > menuX && mouseX < menuX + menuWidth && mouseY > entryY - 13 && mouseY < entryY + 3)
				entryColor = 0xffff00;
			boldFont.drawTextWithTags(menuState.actionNames[entryIndex], menuX + 3, entryY, entryColor, true);
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
		if (gameScreenRedraw) {
			gameScreenRedraw = false;
			titleTopBuffer.draw(super.graphics, 128, 0);
			titleBottomBuffer.draw(super.graphics, 202, 371);
			titleLeftBottomBuffer.draw(super.graphics, 0, 265);
			titleRightBottomBuffer.draw(super.graphics, 562, 265);
			titleLeftCenterBuffer.draw(super.graphics, 128, 171);
			titleRightCenterBuffer.draw(super.graphics, 562, 171);
		}
	}

	/**
	 * Draws the sidebar background, selected tab, and active sidebar interface.
	 */
	public void drawSidebar() {
		sidebarBuffer.bindRaster();
		Rasterizer3D.scanlineOffsets = sidebarScanlineOffsets;
		sidebarBackground.draw(0, 0);
		if (interfaceState.sidebarOverlayInterfaceId != -1)
			drawInterface(0, 0, Widget.get(interfaceState.sidebarOverlayInterfaceId), 0);
		else if (interfaceState.tabInterfaceIds[interfaceState.selectedTab] != -1)
			drawInterface(0, 0, Widget.get(interfaceState.tabInterfaceIds[interfaceState.selectedTab]), 0);
		if (menuState.open && menuState.screenArea == 1)
			drawContextMenu();
		sidebarBuffer.draw(super.graphics, 553, 205);
		viewportBuffer.bindRaster();
		Rasterizer3D.scanlineOffsets = viewportScanlineOffsets;
	}

	/**
	 * Formats a numeric amount with comma grouping and legacy K/million color
	 * annotations.
	 *
	 * @param amount the numeric/item-stack amount
	 * @return the resulting text
	 */
	public static String formatAmountWithCommas(int amount) {
		String amountText = String.valueOf(amount);
		for (int separatorIndex = amountText.length() - 3; separatorIndex > 0; separatorIndex -= 3)
			amountText = amountText.substring(0, separatorIndex) + "," + amountText.substring(separatorIndex);

		if (amountText.length() > 8)
			amountText = "@gre@" + amountText.substring(0, amountText.length() - 8) + " million @whi@(" + amountText
					+ ")";
		else if (amountText.length() > 4)
			amountText = "@cya@" + amountText.substring(0, amountText.length() - 4) + "K @whi@(" + amountText + ")";
		return " " + amountText;
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
		System.out.println("flame-cycle:" + titleFlameCycle);
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
			if (!titleFlamesRunning) {
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

	/**
	 * Stops the title flame thread and releases title-only graphics resources.
	 */
	public void disposeTitleScreen() {
		titleFlamesRunning = false;
		Thread thread = titleFlameThread;
		if (thread != null && thread != Thread.currentThread()) {
			thread.interrupt();
			boolean interrupted = false;
			for (;;) {
				try {
					thread.join();
					break;
				} catch (InterruptedException exception) {
					interrupted = true;
				}
			}
			if (interrupted) {
				Thread.currentThread().interrupt();
			}
		}
		if (titleFlameThread == thread) {
			titleFlameThread = null;
		}
		titleFlameThreadActive = false;
		titleBoxImage = null;
		titleButtonImage = null;
		titleRunes = null;
		titleFlamePalette = null;
		titleFlameRedPalette = null;
		titleFlameGreenPalette = null;
		titleFlameBluePalette = null;
		titleFlameNoise = null;
		titleFlameNoiseScratch = null;
		titleFlameIntensity = null;
		titleFlameIntensityScratch = null;
		titleLeftFlameBackground = null;
		titleRightFlameBackground = null;
	}

	/**
	 * Recursively renders a widget tree, including containers, inventories, text,
	 * sprites, models, and tooltips.
	 *
	 * @param y       the Y coordinate
	 * @param x       the X coordinate
	 * @param widget  the widget being processed
	 * @param scrollY the current scroll offset
	 */
	public void drawInterface(int y, int x, Widget widget, int scrollY) {
		if (widget.type != 0 || widget.children == null)
			return;
		if (widget.mouseoverTriggered && viewportHoveredWidgetId != widget.id && sidebarHoveredWidgetId != widget.id
				&& chatboxHoveredWidgetId != widget.id)
			return;
		int previousClipLeft = Rasterizer.topX;
		int previousClipTop = Rasterizer.topY;
		int previousClipRight = Rasterizer.bottomX;
		int previousClipBottom = Rasterizer.bottomY;
		Rasterizer.setCoordinates(x, y, x + widget.width, y + widget.height);
		int childCount = widget.children.length;
		for (int childIndex = 0; childIndex < childCount; childIndex++) {
			int childX = widget.childX[childIndex] + x;
			int childY = (widget.childY[childIndex] + y) - scrollY;
			Widget childWidget = Widget.get(widget.children[childIndex]);
			childX += childWidget.xOffset;
			childY += childWidget.yOffset;
			if (childWidget.contentType > 0)
				updateWidgetContent(childWidget);
			if (childWidget.type == 0) {
				if (childWidget.scrollY > childWidget.scrollHeight - childWidget.height)
					childWidget.scrollY = childWidget.scrollHeight - childWidget.height;
				if (childWidget.scrollY < 0)
					childWidget.scrollY = 0;
				drawInterface(childY, childX, childWidget, childWidget.scrollY);
				if (childWidget.scrollHeight > childWidget.height)
					drawScrollbar(childWidget.scrollY, childX + childWidget.width, childWidget.height,
							childWidget.scrollHeight, childY);
			} else if (childWidget.type != 1)
				if (childWidget.type == 2) {
					int slot = 0;
					for (int row = 0; row < childWidget.height; row++) {
						for (int column = 0; column < childWidget.width; column++) {
							int slotX = childX + column * (32 + childWidget.inventorySpritePaddingX);
							int slotY = childY + row * (32 + childWidget.inventorySpritePaddingY);
							if (slot < 20) {
								slotX += childWidget.spriteXOffsets[slot];
								slotY += childWidget.spriteYOffsets[slot];
							}
							if (childWidget.itemIds[slot] > 0) {
								int dragOffsetX = 0;
								int dragOffsetY = 0;
								int itemId = childWidget.itemIds[slot] - 1;
								if (slotX > Rasterizer.topX - 32 && slotX < Rasterizer.bottomX
										&& slotY > Rasterizer.topY - 32 && slotY < Rasterizer.bottomY
										|| interfaceState.inventoryDragArea != 0
												&& interfaceState.draggedInventorySlot == slot) {
									// Selected inventory items request the white outline variant from
									// ItemSpriteFactory.
									int spriteOutlineColor = 0;
									if (interfaceState.itemSelected == 1 && interfaceState.selectedItemSlot == slot
											&& interfaceState.selectedItemWidgetId == childWidget.id)
										spriteOutlineColor = 0xffffff;
									ImageRGB itemSprite = ItemSpriteFactory.getSprite(itemId,
											childWidget.itemAmounts[slot], spriteOutlineColor);
									if (itemSprite != null) {
										if (interfaceState.inventoryDragArea != 0
												&& interfaceState.draggedInventorySlot == slot
												&& interfaceState.draggedInventoryWidgetId == childWidget.id) {
											dragOffsetX = super.mouseX - interfaceState.inventoryDragStartX;
											dragOffsetY = super.mouseY - interfaceState.inventoryDragStartY;
											if (dragOffsetX < 5 && dragOffsetX > -5)
												dragOffsetX = 0;
											if (dragOffsetY < 5 && dragOffsetY > -5)
												dragOffsetY = 0;
											if (interfaceState.inventoryDragDuration < 5) {
												dragOffsetX = 0;
												dragOffsetY = 0;
											}
											itemSprite.drawImageAlpha(slotX + dragOffsetX, slotY + dragOffsetY, 128);
											if (slotY + dragOffsetY < Rasterizer.topY && widget.scrollY > 0) {
												int scrollAmount = (animationCycleDelta
														* (Rasterizer.topY - slotY - dragOffsetY)) / 3;
												if (scrollAmount > animationCycleDelta * 10)
													scrollAmount = animationCycleDelta * 10;
												if (scrollAmount > widget.scrollY)
													scrollAmount = widget.scrollY;
												widget.scrollY -= scrollAmount;
												interfaceState.inventoryDragStartY += scrollAmount;
											}
											if (slotY + dragOffsetY + 32 > Rasterizer.bottomY
													&& widget.scrollY < widget.scrollHeight - widget.height) {
												int scrollAmount2 = (animationCycleDelta
														* ((slotY + dragOffsetY + 32) - Rasterizer.bottomY)) / 3;
												if (scrollAmount2 > animationCycleDelta * 10)
													scrollAmount2 = animationCycleDelta * 10;
												if (scrollAmount2 > widget.scrollHeight - widget.height
														- widget.scrollY)
													scrollAmount2 = widget.scrollHeight - widget.height
															- widget.scrollY;
												widget.scrollY += scrollAmount2;
												interfaceState.inventoryDragStartY -= scrollAmount2;
											}
										} else if (interfaceState.pressedInventoryArea != 0
												&& interfaceState.pressedInventorySlot == slot
												&& interfaceState.pressedInventoryWidgetId == childWidget.id)
											itemSprite.drawImageAlpha(slotX, slotY, 128);
										else
											itemSprite.drawImage(slotX, slotY);
										if (itemSprite.maxWidth == 33 || childWidget.itemAmounts[slot] != 1) {
											int itemAmount = childWidget.itemAmounts[slot];
											smallFont.drawText(formatItemStackAmount(itemAmount),
													slotX + 1 + dragOffsetX, slotY + 10 + dragOffsetY, 0);
											smallFont.drawText(formatItemStackAmount(itemAmount), slotX + dragOffsetX,
													slotY + 9 + dragOffsetY, 0xffff00);
										}
									}
								}
							} else if (childWidget.inventorySprites != null && slot < 20) {
								ImageRGB inventorySprite = childWidget.inventorySprites[slot];
								if (inventorySprite != null)
									inventorySprite.drawImage(slotX, slotY);
							}
							slot++;
						}

					}

				} else if (childWidget.type == 3) {
					boolean hovered = false;
					if (chatboxHoveredWidgetId == childWidget.id || sidebarHoveredWidgetId == childWidget.id
							|| viewportHoveredWidgetId == childWidget.id)
						hovered = true;
					int color;
					if (widgetRuntime.isActive(childWidget)) {
						color = childWidget.activeColor;
						if (hovered && childWidget.activeMouseoverColor != 0)
							color = childWidget.activeMouseoverColor;
					} else {
						color = childWidget.color;
						if (hovered && childWidget.mouseoverColor != 0)
							color = childWidget.mouseoverColor;
					}
					if (childWidget.transparency == 0) {
						if (childWidget.filled)
							Rasterizer.drawFilledRectangle(childX, childY, childWidget.width, childWidget.height,
									color);
						else
							Rasterizer.drawUnfilledRectangle(childX, childY, childWidget.width, childWidget.height,
									color);
					} else if (childWidget.filled)
						Rasterizer.drawFilledRectangleAlpha(childX, childY, childWidget.width, childWidget.height,
								color, 256 - (childWidget.transparency & 0xff));
					else
						Rasterizer.drawUnfilledRectangleAlpha(childX, childY, childWidget.width, childWidget.height,
								color, 256 - (childWidget.transparency & 0xff));
				} else if (childWidget.type == 4) {
					TypeFace font = childWidget.font;
					String widgetText = childWidget.text;
					boolean hovered2 = false;
					if (chatboxHoveredWidgetId == childWidget.id || sidebarHoveredWidgetId == childWidget.id
							|| viewportHoveredWidgetId == childWidget.id)
						hovered2 = true;
					int textColor;
					if (widgetRuntime.isActive(childWidget)) {
						textColor = childWidget.activeColor;
						if (hovered2 && childWidget.activeMouseoverColor != 0)
							textColor = childWidget.activeMouseoverColor;
						if (childWidget.activeText.length() > 0)
							widgetText = childWidget.activeText;
					} else {
						textColor = childWidget.color;
						if (hovered2 && childWidget.mouseoverColor != 0)
							textColor = childWidget.mouseoverColor;
					}
					if (childWidget.buttonType == 6 && interfaceActionPending) {
						widgetText = "Please wait...";
						textColor = childWidget.color;
					}
					if (Rasterizer.width == 479) {
						if (textColor == 0xffff00)
							textColor = 255;
						if (textColor == 49152)
							textColor = 0xffffff;
					}
					for (int lineY = childY + font.lineHeight; widgetText.length() > 0; lineY += font.lineHeight) {
						if (widgetText.indexOf("%") != -1) {
							do {
								int placeholderIndex = widgetText.indexOf("%1");
								if (placeholderIndex == -1)
									break;
								widgetText = widgetText.substring(0, placeholderIndex)
										+ formatWidgetScriptValue(widgetRuntime.evaluateScript(childWidget, 0))
										+ widgetText.substring(placeholderIndex + 2);
							} while (true);
							do {
								int placeholderIndex2 = widgetText.indexOf("%2");
								if (placeholderIndex2 == -1)
									break;
								widgetText = widgetText.substring(0, placeholderIndex2)
										+ formatWidgetScriptValue(widgetRuntime.evaluateScript(childWidget, 1))
										+ widgetText.substring(placeholderIndex2 + 2);
							} while (true);
							do {
								int placeholderIndex3 = widgetText.indexOf("%3");
								if (placeholderIndex3 == -1)
									break;
								widgetText = widgetText.substring(0, placeholderIndex3)
										+ formatWidgetScriptValue(widgetRuntime.evaluateScript(childWidget, 2))
										+ widgetText.substring(placeholderIndex3 + 2);
							} while (true);
							do {
								int placeholderIndex4 = widgetText.indexOf("%4");
								if (placeholderIndex4 == -1)
									break;
								widgetText = widgetText.substring(0, placeholderIndex4)
										+ formatWidgetScriptValue(widgetRuntime.evaluateScript(childWidget, 3))
										+ widgetText.substring(placeholderIndex4 + 2);
							} while (true);
							do {
								int placeholderIndex5 = widgetText.indexOf("%5");
								if (placeholderIndex5 == -1)
									break;
								widgetText = widgetText.substring(0, placeholderIndex5)
										+ formatWidgetScriptValue(widgetRuntime.evaluateScript(childWidget, 4))
										+ widgetText.substring(placeholderIndex5 + 2);
							} while (true);
						}
						int lineBreakIndex = widgetText.indexOf("\\n");
						String lineText;
						if (lineBreakIndex != -1) {
							lineText = widgetText.substring(0, lineBreakIndex);
							widgetText = widgetText.substring(lineBreakIndex + 2);
						} else {
							lineText = widgetText;
							widgetText = "";
						}
						if (childWidget.textCentered)
							font.drawCenteredTextWithTags(lineText, childX + childWidget.width / 2, lineY, textColor,
									childWidget.textShadowed);
						else
							font.drawTextWithTags(lineText, childX, lineY, textColor, childWidget.textShadowed);
					}

				} else if (childWidget.type == 5) {
					ImageRGB sprite;
					if (widgetRuntime.isActive(childWidget))
						sprite = childWidget.activeSprite;
					else
						sprite = childWidget.sprite;
					if (sprite != null)
						sprite.drawImage(childX, childY);
				} else if (childWidget.type == 6) {
					int previousCenterX = Rasterizer3D.centerX;
					int previousCenterY = Rasterizer3D.centerY;
					Rasterizer3D.centerX = childX + childWidget.width / 2;
					Rasterizer3D.centerY = childY + childWidget.height / 2;
					int pitchSineOffset = Rasterizer3D.SINE[childWidget.modelPitch] * childWidget.modelZoom >> 16;
					int pitchCosineOffset = Rasterizer3D.COSINE[childWidget.modelPitch] * childWidget.modelZoom >> 16;
					boolean active = widgetRuntime.isActive(childWidget);
					int animationId;
					if (active)
						animationId = childWidget.activeAnimationId;
					else
						animationId = childWidget.animationId;
					Model model;
					if (animationId == -1) {
						model = childWidget.getAnimatedModel(-1, -1, active);
					} else {
						AnimationSequence sequence = AnimationSequence.sequences[animationId];
						model = childWidget.getAnimatedModel(sequence.primaryFrameIds[childWidget.animationFrame],
								sequence.secondaryFrameIds[childWidget.animationFrame], active);
					}
					if (model != null)
						model.renderSimple(0, childWidget.modelYaw, 0, childWidget.modelPitch, 0, pitchSineOffset,
								pitchCosineOffset);
					Rasterizer3D.centerX = previousCenterX;
					Rasterizer3D.centerY = previousCenterY;
				} else {
					if (childWidget.type == 7) {
						TypeFace font2 = childWidget.font;
						int slot2 = 0;
						for (int row2 = 0; row2 < childWidget.height; row2++) {
							for (int column2 = 0; column2 < childWidget.width; column2++) {
								if (childWidget.itemIds[slot2] > 0) {
									ItemDefinition itemDefinition = ItemDefinition
											.lookup(childWidget.itemIds[slot2] - 1);
									String itemText = String.valueOf(itemDefinition.name);
									if (itemDefinition.stackable || childWidget.itemAmounts[slot2] != 1)
										itemText = itemText + " x"
												+ formatAmountWithCommas(childWidget.itemAmounts[slot2]);
									int itemX = childX + column2 * (115 + childWidget.inventorySpritePaddingX);
									int itemY = childY + row2 * (12 + childWidget.inventorySpritePaddingY);
									if (childWidget.textCentered)
										font2.drawCenteredTextWithTags(itemText, itemX + childWidget.width / 2, itemY,
												childWidget.color, childWidget.textShadowed);
									else
										font2.drawTextWithTags(itemText, itemX, itemY, childWidget.color,
												childWidget.textShadowed);
								}
								slot2++;
							}

						}

					}
					if (childWidget.type == 8 && (chatboxTooltipWidgetId == childWidget.id
							|| sidebarTooltipWidgetId == childWidget.id || viewportTooltipWidgetId == childWidget.id)
							&& tooltipHoverTicks == 100) {
						int tooltipWidth = 0;
						int tooltipHeight = 0;
						TypeFace font3 = plainFont;
						for (String remainingText = childWidget.text; remainingText.length() > 0;) {
							int lineBreakIndex2 = remainingText.indexOf("\\n");
							String lineText2;
							if (lineBreakIndex2 != -1) {
								lineText2 = remainingText.substring(0, lineBreakIndex2);
								remainingText = remainingText.substring(lineBreakIndex2 + 2);
							} else {
								lineText2 = remainingText;
								remainingText = "";
							}
							int lineWidth = font3.getFormattedTextWidth(lineText2);
							if (lineWidth > tooltipWidth)
								tooltipWidth = lineWidth;
							tooltipHeight += font3.lineHeight + 1;
						}

						tooltipWidth += 6;
						tooltipHeight += 7;
						int tooltipX = (childX + childWidget.width) - 5 - tooltipWidth;
						int tooltipY = childY + childWidget.height + 5;
						if (tooltipX < childX + 5)
							tooltipX = childX + 5;
						if (tooltipX + tooltipWidth > x + widget.width)
							tooltipX = (x + widget.width) - tooltipWidth;
						if (tooltipY + tooltipHeight > y + widget.height)
							tooltipY = (y + widget.height) - tooltipHeight;
						Rasterizer.drawFilledRectangle(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 0xffffa0);
						Rasterizer.drawUnfilledRectangle(tooltipX, tooltipY, tooltipWidth, tooltipHeight, 0);
						String remainingText2 = childWidget.text;
						for (int textY = tooltipY + font3.lineHeight + 2; remainingText2
								.length() > 0; textY += font3.lineHeight + 1) {
							int lineBreakIndex3 = remainingText2.indexOf("\\n");
							String lineText3;
							if (lineBreakIndex3 != -1) {
								lineText3 = remainingText2.substring(0, lineBreakIndex3);
								remainingText2 = remainingText2.substring(lineBreakIndex3 + 2);
							} else {
								lineText3 = remainingText2;
								remainingText2 = "";
							}
							font3.drawTextWithTags(lineText3, tooltipX + 3, textY, 0, false);
						}

					}
				}
		}

		Rasterizer.setCoordinates(previousClipLeft, previousClipTop, previousClipRight, previousClipBottom);
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
	private void queueAreaSound(int soundId, int loops, int radius, int tileX, int tileY) {
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
				networkSession.outgoing.writeOpcode(6);
			} else if (System.currentTimeMillis() - regionManager.loadingStartTime > 0x57e40L) {
				Signlink.reportError(
						loginScreen.username + " glcfb " + serverSessionKey + "," + status + "," + lowMemory + ","
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
		MinimapRenderer.Click click = minimapRenderer.transformClick(clickX, clickY, localPlayer,
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
		super.gameBuffer = new GraphicsBuffer(getGameComponent(), 765, 503);
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
					loginFailures = 0;
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
			int yaw = cameraController.followYaw + cameraController.yawOffset & 0x7ff;
			positionCamera(worldState.getTileHeight(localPlayer.x, localPlayer.y, currentPlane) - 50,
					cameraController.followTargetX, pitch, 600 + pitch * 3, yaw, cameraController.followTargetY);
		}
		int renderPlane = cameraController.cinematic ? selectCinematicRenderPlane() : selectNormalRenderPlane();
		CameraController.Snapshot cameraSnapshot = cameraController.snapshot();
		cameraController.applyShake();

		int textureCycle = Rasterizer3D.textureCycle;
		Model.pickingEnabled = true;
		Model.pickedCount = 0;
		Model.mouseX = super.mouseX - 4;
		Model.mouseY = super.mouseY - 4;
		Rasterizer.resetPixels();
		worldState.scene.render(cameraController.x, cameraController.y, cameraController.height, renderPlane,
				cameraController.yaw, cameraController.pitch);
		worldState.scene.clearTemporaryObjects();
		drawActorOverlays();
		drawWorldHintIcon();
		animateTextures(textureCycle);
		drawViewportOverlays();
		viewportBuffer.draw(super.graphics, 4, 4);
		cameraController.restore(cameraSnapshot);
	}

	/**
	 * Initializes a Client instance and wires the extracted subsystem owners and
	 * callbacks.
	 */
	public Client() {
		reportAbuseName = "";
		skillExperiences = new int[Skills.COUNT];
		itemSearchQuery = "";
		itemSearchResultNames = new String[100];
		itemSearchResultIds = new int[100];
		messagePromptRaised = false;
		crossSprites = new ImageRGB[8];
		minimapMaskWidths = new int[151];
		networkSession = new NetworkSession();
		socialManager = new SocialManager();
		chatHistory = new ChatHistory();
		interfaceState = new InterfaceState();
		menuState = new MenuState();
		loginScreen = new LoginScreen();
		resourceLoader = new ResourceLoader();
		soundEffectQueue = new SoundEffectQueue();
		musicController = new MusicController();
		pathfinder = new Pathfinder();
		actorSynchronizer = new ActorSynchronizer();
		actorUpdater = new ActorUpdater();
		cameraController = new CameraController();
		sceneEntityRenderer = new SceneEntityRenderer();
		minimapRenderer = new MinimapRenderer();
		regionManager = new RegionManager();
		widgetRuntime = new WidgetRuntime(new WidgetRuntime.ScriptContext() {
			/**
			 * Returns the current boosted/drained level for a skill.
			 *
			 * @param skill the skill
			 * @return the resulting numeric value
			 */
			@Override
			public int currentSkillLevel(int skill) {
				return currentSkillLevels[skill];
			}

			/**
			 * Returns the base level for a skill.
			 *
			 * @param skill the skill
			 * @return the resulting numeric value
			 */
			@Override
			public int baseSkillLevel(int skill) {
				return baseSkillLevels[skill];
			}

			/**
			 * Returns the accumulated experience for a skill.
			 *
			 * @param skill the skill
			 * @return the resulting numeric value
			 */
			@Override
			public int skillExperience(int skill) {
				return skillExperiences[skill];
			}

			/**
			 * Returns the current value of a varp.
			 *
			 * @param id the id
			 * @return the resulting numeric value
			 */
			@Override
			public int varp(int id) {
				return varpValues[id];
			}

			/**
			 * Returns the experience-table entry for a zero-based level index.
			 *
			 * @param levelIndex the level index
			 * @return the resulting numeric value
			 */
			@Override
			public int experienceForLevel(int levelIndex) {
				return experienceTable[levelIndex];
			}

			/**
			 * Returns the precomputed mask for a bit width.
			 *
			 * @param width the width
			 * @return the resulting numeric value
			 */
			@Override
			public int bitMask(int width) {
				return bitMasks[width];
			}

			/**
			 * Returns the current run-energy percentage.
			 *
			 * @return the resulting numeric value
			 */
			@Override
			public int runEnergy() {
				return runEnergy;
			}

			/**
			 * Returns the current carried-weight value.
			 *
			 * @return the resulting numeric value
			 */
			@Override
			public int weight() {
				return weight;
			}

			/**
			 * Returns the local player combat level.
			 *
			 * @return the resulting numeric value
			 */
			@Override
			public int combatLevel() {
				return localPlayer.combatLevel;
			}

			/**
			 * Returns the local player absolute world X tile.
			 *
			 * @return the resulting numeric value
			 */
			@Override
			public int playerWorldX() {
				return (localPlayer.x >> 7) + regionManager.baseX;
			}

			/**
			 * Returns the local player absolute world Y tile.
			 *
			 * @return the resulting numeric value
			 */
			@Override
			public int playerWorldY() {
				return (localPlayer.y >> 7) + regionManager.baseY;
			}

			/**
			 * Returns whether the client is currently in members-world mode.
			 *
			 * @return true when the requested condition/action succeeds; otherwise false
			 */
			@Override
			public boolean membersWorld() {
				return membersWorld;
			}
		});
		loginBuffer = new Buffer(new byte[5000]);
		scrollbarTrackColor = 0x23201b;
		projectedX = -1;
		projectedY = -1;
		promptMessage = "";
		overheadTextLimit = 50;
		overheadTextXs = new int[overheadTextLimit];
		overheadTextYs = new int[overheadTextLimit];
		overheadTextHeights = new int[overheadTextLimit];
		overheadTextHalfWidths = new int[overheadTextLimit];
		overheadTextColorCodes = new int[overheadTextLimit];
		overheadTextEffects = new int[overheadTextLimit];
		overheadTextCycles = new int[overheadTextLimit];
		overheadTexts = new String[overheadTextLimit];
		inputDialogText = "";
		tabAreaRedraw = false;
		hintIconSprites = new ImageRGB[32];
		localPlayerServerIndex = -1;
		sidebarIcons = new IndexedImage[13];
		varpShadowValues = new int[2000];
		duplicateClientError = false;
		minimapMaskOffsets = new int[151];
		promptInput = "";
		currentSkillLevels = new int[Skills.COUNT];
		mapFunctionSprites = new ImageRGB[100];
		varpValues = new int[2000];
		gameScreenRedraw = false;
		baseSkillLevels = new int[Skills.COUNT];
		regionManager.specialRegion = false;
		playerActions = new String[5];
		playerActionLowPriority = new boolean[5];
		prayerIconSprites = new ImageRGB[32];
		scrollbarThumbColor = 0x4d4233;
		invalidHostError = false;
		reportAbuseMutePlayer = false;
		appearanceColors = new int[5];
		chatInput = "";
		chatContentHeight = 78;
		scrollbarDragging = false;
		chatBuffer = new Buffer(new byte[5000]);
		scrollbarHighlightColor = 0x766654;
		loggedIn = false;
		moderatorIcons = new IndexedImage[2];
		maleAppearance = true;
		mapSceneSprites = new IndexedImage[100];
		inventoryDragMoved = false;
		regionManager.instanced = false;
		titleFlameLineOffsets = new int[256];
		compassMaskOffsets = new int[33];
		sidebarRedraw = false;
		hitmarkSprites = new ImageRGB[20];
		regionManager.awaitingPlayerUpdate = false;
		chatModesRedraw = false;
		interfaceActionPending = false;
		chatboxRedraw = false;
		titleFlamesRunning = false;
		textureScrollScratch = new byte[16384];
		chatboxScrollWidget = new Widget();
		cameraOrientationChanged = false;
		windowFocusReported = true;
		lastMinimapPlane = -1;
		appearanceModelDirty = false;
		loadingError = false;
		compassMaskWidths = new int[33];
		scrollbarShadowColor = 0x332d25;
		skullIconSprites = new ImageRGB[32];
		titleFlameThreadMode = false;
		titleFlameThreadActive = false;
		appearanceKitIds = new int[7];
	}

	/** The client state for report abuse name. */
	public String reportAbuseName;
	/** The RSA modulus used by the revision-377 login handshake. */
	public static BigInteger RSA_MODULUS = new BigInteger(
			"7162900525229798032761816791230527296329313291232324290237849263501208207972894053929065636522363163621000728841182238772712427862772219676577293600221789");

	public int overheadTextColors[] = { 0xffff00, 0xff0000, 65280, 65535, 0xff00ff, 0xffffff };

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
	/** The client state for login failures. */
	public int loginFailures;
	/** The client state for chat scroll offset. */
	public int chatScrollOffset;
	/** The client state for item search query. */
	public String itemSearchQuery;
	/** The current number of item search result entries. */
	public int itemSearchResultCount;

	public String itemSearchResultNames[];

	public int itemSearchResultIds[];
	/** The client state for item search scroll offset. */
	public int itemSearchScrollOffset;
	/** Whether message prompt raised is currently active or requested. */
	public boolean messagePromptRaised;
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
	/** The client state for private chat mode. */
	public int privateChatMode;
	/** The client state for title archive. */
	public Archive titleArchive;
	/**
	 * Tracks the current tooltip hover ticks in client ticks/cycles where
	 * applicable.
	 */
	public int tooltipHoverTicks;
	/**
	 * Counts system update keepalive events for the original client timing/protocol
	 * behavior.
	 */
	public static int systemUpdateKeepaliveCounter;

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
	public int currentHoveredWidgetId;

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
	/** The graphics or protocol buffer used for login buffer. */
	public Buffer loginBuffer;
	/** The client state for server session key. */
	public long serverSessionKey;
	/** The client state for scrollbar track color. */
	public int scrollbarTrackColor;
	/** The client state for projected x. */
	public int projectedX;
	/** The client state for projected y. */
	public int projectedY;
	/** The client state for prompt message. */
	public String promptMessage;
	/** The current number of overhead text entries. */
	public int overheadTextCount;
	/** The client state for overhead text limit. */
	public int overheadTextLimit;

	public int overheadTextXs[];

	public int overheadTextYs[];

	public int overheadTextHeights[];

	public int overheadTextHalfWidths[];

	public int overheadTextColorCodes[];

	public int overheadTextEffects[];

	public int overheadTextCycles[];

	public String overheadTexts[];
	/** The client state for input dialog text. */
	public String inputDialogText;
	/** Whether tab area redraw is currently active or requested. */
	public boolean tabAreaRedraw;
	/** The client state for animation cycle delta. */
	public int animationCycleDelta;

	public static int experienceTable[];

	public ImageRGB hintIconSprites[];
	/** The client state for inventory rearrange mode. */
	public int inventoryRearrangeMode;
	/** Whether account flagged is currently active or requested. */
	public static boolean accountFlagged;
	/** The client state for network session. */
	public NetworkSession networkSession;
	/** The client state for social manager. */
	private final SocialManager socialManager;
	/** The client state for chat history. */
	private final ChatHistory chatHistory;
	/** The client state for interface state. */
	private final InterfaceState interfaceState;
	/** The client state for menu state. */
	private final MenuState menuState;
	/** The client state for login screen. */
	private final LoginScreen loginScreen;
	/** The client state for resource loader. */
	private final ResourceLoader resourceLoader;
	/** The client state for sound effect queue. */
	private final SoundEffectQueue soundEffectQueue;
	/** The client state for music controller. */
	private final MusicController musicController;
	/** The client state for widget runtime. */
	private final WidgetRuntime widgetRuntime;
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

	public int chatboxScanlineOffsets[];

	public int sidebarScanlineOffsets[];

	public int viewportScanlineOffsets[];

	public int fullScreenScanlineOffsets[];

	public int varpShadowValues[];
	/** The client state for public chat mode. */
	public int publicChatMode;

	public static final int bodyColorPalettes[][] = {
			{ 6798, 107, 10283, 16, 4797, 7744, 5799, 4634, 33697, 22433, 2983, 54193 },
			{ 8741, 12, 64030, 43162, 7735, 8404, 1701, 38430, 24094, 10153, 56621, 4783, 1341, 16578, 35003, 25239 },
			{ 25238, 8742, 12, 64030, 43162, 7735, 8404, 1701, 38430, 24094, 10153, 56621, 4783, 1341, 16578, 35003 },
			{ 4626, 11146, 6439, 12, 4758, 10270 }, { 4550, 4537, 5681, 5673, 5790, 6806, 8076, 4574 } };
	/** The client state for last recorded mouse x. */
	public int lastRecordedMouseX;
	/** The client state for last recorded mouse y. */
	public int lastRecordedMouseY;
	/** Whether duplicate client error is currently active or requested. */
	public boolean duplicateClientError;
	/** The client state for title left flame background. */
	public ImageRGB titleLeftFlameBackground;
	/** The client state for title right flame background. */
	public ImageRGB titleRightFlameBackground;

	public int minimapMaskOffsets[];
	/** The client state for cross x. */
	public int crossX;
	/** The client state for cross y. */
	public int crossY;
	/** Tracks the current cross cycle in client ticks/cycles where applicable. */
	public int crossCycle;
	/** The client state for cross type. */
	public int crossType;
	/** The client state for prompt input. */
	public String promptInput;
	/** The client state for loading message. */
	public String loadingMessage;

	public int currentSkillLevels[];
	/** The client state for weight. */
	public int weight;

	public ImageRGB mapFunctionSprites[];
	/** The client state for recovery questions date. */
	public int recoveryQuestionsDate;
	/** The client state for destination map marker. */
	public ImageRGB destinationMapMarker;
	/** The client state for hint map marker. */
	public ImageRGB hintMapMarker;

	public int varpValues[];
	/** The current sidebar tooltip widget id. */
	public int sidebarTooltipWidgetId;
	/** Whether game screen redraw is currently active or requested. */
	public boolean gameScreenRedraw;
	/** The client state for green flame transition. */
	public int greenFlameTransition;
	/** The client state for blue flame transition. */
	public int blueFlameTransition;
	/**
	 * Counts ground item action684 events for the original client timing/protocol
	 * behavior.
	 */
	public static int groundItemAction684Counter;

	public int baseSkillLevels[];
	/**
	 * Tracks the current system update timer in client ticks/cycles where
	 * applicable.
	 */
	public int systemUpdateTimer;
	/** The client state for click to continue message. */
	public String clickToContinueMessage;
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

	public String playerActions[];

	public boolean playerActionLowPriority[];

	public ImageRGB prayerIconSprites[];
	/** The client state for scrollbar thumb color. */
	public int scrollbarThumbColor;
	/** The client state for last password change date. */
	public int lastPasswordChangeDate;

	public int titleFlameIntensity[];

	public int titleFlameIntensityScratch[];
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
	public boolean reportAbuseMutePlayer;

	public int appearanceColors[];
	/**
	 * Counts ground item action26 events for the original client timing/protocol
	 * behavior.
	 */
	public static int groundItemAction26Counter;
	/**
	 * Tracks the current title flame cycle in client ticks/cycles where applicable.
	 */
	public int titleFlameCycle;
	/** The sprite resource used for male appearance button sprite. */
	public ImageRGB maleAppearanceButtonSprite;
	/** The sprite resource used for female appearance button sprite. */
	public ImageRGB femaleAppearanceButtonSprite;
	/** The client state for chat input. */
	public String chatInput;
	/** The current chatbox hovered widget id. */
	public int chatboxHoveredWidgetId;
	/** The client state for chat content height. */
	public int chatContentHeight;
	/** The graphics or protocol buffer used for chat modes buffer. */
	public GraphicsBuffer chatModesBuffer;
	/** The graphics or protocol buffer used for bottom tabs buffer. */
	public GraphicsBuffer bottomTabsBuffer;
	/** The graphics or protocol buffer used for top tabs buffer. */
	public GraphicsBuffer topTabsBuffer;
	/** The sprite resource used for compass sprite. */
	public ImageRGB compassSprite;

	public IndexedImage titleRunes[];
	/** The client state for destination x. */
	public int destinationX;
	/** The client state for destination y. */
	public int destinationY;
	/** The client state for alternative route. */
	public int alternativeRoute;
	/** Whether scrollbar dragging is currently active or requested. */
	public boolean scrollbarDragging;
	/** The current viewport tooltip widget id. */
	public int viewportTooltipWidgetId;
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
	/** The client state for private message target. */
	public long privateMessageTarget;

	public IndexedImage moderatorIcons[];
	/** Whether male appearance is currently active or requested. */
	public boolean maleAppearance;
	/** The client state for hint player index. */
	public int hintPlayerIndex;

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

	public int titleFlameLineOffsets[];
	/** The client state for account current day. */
	public int accountCurrentDay;

	public int titleFlameNoise[];

	public int titleFlameNoiseScratch[];

	public int compassMaskOffsets[];
	/** Whether sidebar redraw is currently active or requested. */
	public boolean sidebarRedraw;

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

	public static int bitMasks[];
	/** The client state for last login day. */
	public int lastLoginDay;
	/** The client state for prompt action. */
	public int promptAction;
	/** The client state for split private chat. */
	public int splitPrivateChat;
	/** The client state for jaggrab socket. */
	public Socket jaggrabSocket;
	/** The client state for hint npc index. */
	public int hintNpcIndex;
	/** The client state for trade mode. */
	public int tradeMode;
	/**
	 * Counts npc action118 events for the original client timing/protocol behavior.
	 */
	public static int npcAction118Counter;
	/**
	 * Counts screen redraw keepalive events for the original client timing/protocol
	 * behavior.
	 */
	public static int screenRedrawKeepaliveCounter;
	/** The client state for title flame noise offset. */
	public int titleFlameNoiseOffset;
	/** Whether interface action pending is currently active or requested. */
	public boolean interfaceActionPending;
	/** Whether chatbox redraw is currently active or requested. */
	public boolean chatboxRedraw;
	/** The client state for last login ip. */
	public int lastLoginIp;
	/** Whether title flames running is currently active or requested. */
	public volatile boolean titleFlamesRunning;
	/** Dedicated title-flame worker, owned by this client. */
	private volatile Thread titleFlameThread;
	/** The client state for input dialog state. */
	public int inputDialogState;

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

	public static final int skinColorPalette[] = { 9104, 10275, 7595, 3610, 7975, 8526, 918, 38802, 24466, 10145, 58654,
			5027, 1457, 16565, 34991, 25486 };
	/** The current number of unread message entries. */
	public int unreadMessageCount;
	/** Whether window focus reported is currently active or requested. */
	public boolean windowFocusReported;
	/** The client state for last minimap plane. */
	public int lastMinimapPlane;
	/** Whether appearance model dirty is currently active or requested. */
	public boolean appearanceModelDirty;
	/** The current sidebar hovered widget id. */
	public int sidebarHoveredWidgetId;
	/** Whether loading error is currently active or requested. */
	public boolean loadingError;
	/** The current chatbox tooltip widget id. */
	public int chatboxTooltipWidgetId;

	public int compassMaskWidths[];
	/** The client state for scrollbar shadow color. */
	public int scrollbarShadowColor;

	public ImageRGB skullIconSprites[];

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
	public int viewportHoveredWidgetId;
	/** The client state for scrollbar drag padding. */
	public int scrollbarDragPadding;
	/** Tracks the current draw cycle in client ticks/cycles where applicable. */
	public static int drawCycle;

	public int titleFlamePalette[];

	public int titleFlameRedPalette[];

	public int titleFlameGreenPalette[];

	public int titleFlameBluePalette[];
	/** Whether title flame thread mode is currently active or requested. */
	public volatile boolean titleFlameThreadMode;
	/** The current current tooltip widget id. */
	public int currentTooltipWidgetId;

	/** The RSA public exponent used by the revision-377 login handshake. */
	public static BigInteger RSA_EXPONENT = new BigInteger(
			"58778699976184461502525193738213253649000149147835990136706041084440742975821");

	/** The client state for multi combat zone. */
	public int multiCombatZone;
	/** Whether title flame thread active is currently active or requested. */
	public volatile boolean titleFlameThreadActive;
	/** The client state for loading percent. */
	public int loadingPercent;
	/** The client state for run energy. */
	public int runEnergy;
	/** Tracks the current game cycle in client ticks/cycles where applicable. */
	public static int gameCycle;

	public int appearanceKitIds[];
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
