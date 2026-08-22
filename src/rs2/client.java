package rs2;

import java.applet.AppletContext;
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
import java.net.URL;
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
import rs2.collection.Node;
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
import rs2.media.renderable.GraphicsObject;
import rs2.media.renderable.GroundItem;
import rs2.media.renderable.Model;
import rs2.media.renderable.Npc;
import rs2.media.renderable.Player;
import rs2.media.renderable.Projectile;
import rs2.media.renderable.Renderable;
import rs2.net.Buffer;
import rs2.net.ChatPacketEncoder;
import rs2.net.BufferedConnection;
import rs2.net.Ipv4Address;
import rs2.net.NetworkSession;
import rs2.net.MovementPacketEncoder;
import rs2.scene.InteractiveObject;
import rs2.scene.PendingSpawn;
import rs2.scene.Region;
import rs2.scene.Scene;
import rs2.scene.tile.FloorDecoration;
import rs2.scene.tile.Wall;
import rs2.scene.tile.WallDecoration;
import rs2.scene.util.CollisionMap;
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

public class client extends GameShell {

	public void method14(String s, int i) {
		if (s == null || s.length() == 0) {
			anInt862 = 0;
			return;
		}
		String s1 = s;
		String as[] = new String[100];
		int j = 0;
		do {
			int k = s1.indexOf(" ");
			if (k == -1)
				break;
			String s2 = s1.substring(0, k).trim();
			if (s2.length() > 0)
				as[j++] = s2.toLowerCase();
			s1 = s1.substring(k + 1);
		} while (true);
		s1 = s1.trim();
		if (s1.length() > 0)
			as[j++] = s1.toLowerCase();
		anInt862 = 0;
		if (i != 2)
			aBoolean959 = !aBoolean959;
		label0: for (int l = 0; l < ItemDefinition.count; l++) {
			ItemDefinition class16 = ItemDefinition.lookup(l);
			if (class16.noteTemplateId != -1 || class16.name == null)
				continue;
			String s3 = class16.name.toLowerCase();
			for (int i1 = 0; i1 < j; i1++)
				if (s3.indexOf(as[i1]) == -1)
					continue label0;

			aStringArray863[anInt862] = s3;
			anIntArray864[anInt862] = l;
			anInt862++;
			if (anInt862 >= aStringArray863.length)
				return;
		}

	}

	public void method15(boolean flag) {
		networkSession.outgoing.writeOpcode(110);
		if (interfaceState.sidebarOverlayInterfaceId != -1) {
			unloadInterface(interfaceState.sidebarOverlayInterfaceId);
			aBoolean1181 = true;
			aBoolean1239 = false;
			aBoolean950 = true;
		}
		if (interfaceState.chatboxInterfaceId != -1) {
			unloadInterface(interfaceState.chatboxInterfaceId);
			aBoolean1240 = true;
			aBoolean1239 = false;
		}
		if (interfaceState.fullscreenInterfaceId != -1) {
			unloadInterface(interfaceState.fullscreenInterfaceId);
			aBoolean1046 = true;
		}
		if (interfaceState.fullscreenOverlayInterfaceId != -1) {
			unloadInterface(interfaceState.fullscreenOverlayInterfaceId);
		}
		if (interfaceState.openInterfaceId != -1) {
			unloadInterface(interfaceState.openInterfaceId);
		}
	}


	public static void main(String args[]) {
		try {
			System.out.println("RS2 user client - release #" + 377);
			if (args.length != 5) {
				System.out.println("Usage: node-id, port-offset, [lowmem/highmem], [free/members], storeid");
				return;
			}
			currentWorldId = Integer.parseInt(args[0]);
			portOffset = Integer.parseInt(args[1]);
			if (args[2].equals("lowmem"))
				setLowMemory();
			else if (args[2].equals("highmem")) {
				setHighMemory();
			} else {
				System.out.println("Usage: node-id, port-offset, [lowmem/highmem], [free/members], storeid");
				return;
			}
			if (args[3].equals("free"))
				membersWorld = false;
			else if (args[3].equals("members")) {
				membersWorld = true;
			} else {
				System.out.println("Usage: node-id, port-offset, [lowmem/highmem], [free/members], storeid");
				return;
			}
			Signlink.storeId = Integer.parseInt(args[4]);
			Signlink.start(InetAddress.getLocalHost());
			client client1 = new client();
			client1.createFrame(765, 503);
			return;
		} catch (Exception exception) {
			return;
		}
	}

	/* Legacy client.method17(byte byte0): byte0 -> removed fixed 4 sentinel. */
	public void runTitleFlameLoop() {
		titleFlameThreadActive = true;
		try {
			long l = System.currentTimeMillis();
			int i = 0;
			int j = 20;
			while (titleFlamesRunning) {
				titleFlameCycle++;
				updateTitleFlames();
				updateTitleFlames();
				drawTitleFlames();
				if (++i > 10) {
					long l1 = System.currentTimeMillis();
					int k = (int) (l1 - l) / 10 - j;
					j = 40 - k;
					if (j < 5)
						j = 5;
					i = 0;
					l = l1;
				}
				try {
					Thread.sleep(j);
				} catch (Exception _ex) {
				}
			}
		} catch (Exception _ex) {
		}
		titleFlameThreadActive = false;
	}

	/* Legacy client.method19(String s): fatal startup/on-demand load halt. */
	public void haltOnLoadError(String s) {
		System.out.println(s);
		do
			try {
				Thread.sleep(1000L);
			} catch (Exception _ex) {
			}
		while (true);
	}

	public static String method20(int i, int j) {
		if (j >= 0)
			throw new NullPointerException();
		if (i < 0x186a0)
			return String.valueOf(i);
		if (i < 0x989680)
			return i / 1000 + "K";
		else
			return i / 0xf4240 + "M";
	}

	public void cleanUpForQuit() {
		aClass18_906 = null;
		aClass18_907 = null;
		aClass18_908 = null;
		aClass18_909 = null;
		aClass50_Sub1_Sub1_Sub3_880 = null;
		aClass50_Sub1_Sub1_Sub3_881 = null;
		aClass50_Sub1_Sub1_Sub3_882 = null;
		aClass50_Sub1_Sub1_Sub3_883 = null;
		aClass50_Sub1_Sub1_Sub3_884 = null;
		aClass50_Sub1_Sub1_Sub3_983 = null;
		aClass50_Sub1_Sub1_Sub3_984 = null;
		aClass50_Sub1_Sub1_Sub3_985 = null;
		aClass50_Sub1_Sub1_Sub3_986 = null;
		aClass50_Sub1_Sub1_Sub3_987 = null;
		socialManager.clearFriendReferencesForQuit();
		aClass18_1108 = null;
		aClass18_1109 = null;
		aClass18_1110 = null;
		varpValues = null;
		regionManager.clear();
		titleLeftBottomBuffer = null;
		titleRightBottomBuffer = null;
		titleLeftCenterBuffer = null;
		titleRightCenterBuffer = null;
		aClass50_Sub1_Sub1_Sub1_1192 = null;
		aClass50_Sub1_Sub1_Sub1_1193 = null;
		aClass50_Sub1_Sub1_Sub1_1194 = null;
		aClass50_Sub1_Sub1_Sub1_1195 = null;
		aClass50_Sub1_Sub1_Sub1_1196 = null;
		if (aClass7_1248 != null)
			aClass7_1248.running = false;
		aClass7_1248 = null;
		aClass50_Sub1_Sub1_Sub3_965 = null;
		aClass50_Sub1_Sub1_Sub3_966 = null;
		aClass50_Sub1_Sub1_Sub3_967 = null;
		aClass18_910 = null;
		aClass18_911 = null;
		aClass18_912 = null;
		aClass18_913 = null;
		aClass18_914 = null;
		worldState = null;
		zoneUpdates = null;
		minimapRenderer.clear();
		titleLeftFlameBuffer = null;
		titleRightFlameBuffer = null;
		titleTopBuffer = null;
		titleBottomBuffer = null;
		loginBoxBuffer = null;
		aClass50_Sub1_Sub1_Sub1_1116 = null;
		aClass50_Sub1_Sub1_Sub1Array1182 = null;
		aClass50_Sub1_Sub1_Sub1Array1288 = null;
		aClass50_Sub1_Sub1_Sub1Array1079 = null;
		aClass50_Sub1_Sub1_Sub1Array954 = null;
		aClass50_Sub1_Sub1_Sub1Array896 = null;
		musicController.stop();
		loginBuffer = null;
		sidebarBuffer = null;
		minimapBuffer = null;
		viewportBuffer = null;
		chatboxBuffer = null;
		aClass50_Sub1_Sub1_Sub3_1185 = null;
		aClass50_Sub1_Sub1_Sub3_1186 = null;
		aClass50_Sub1_Sub1_Sub3_1187 = null;
		if (networkSession != null) {
			networkSession.closeConnection();
			networkSession = null;
		}
		aByteArray1245 = null;
		chatBuffer = null;
		aClass50_Sub1_Sub1_Sub3Array1153 = null;
		aClass50_Sub1_Sub1_Sub1Array1031 = null;
		aClass50_Sub1_Sub1_Sub3Array976 = null;
		aClass50_Sub1_Sub1_Sub1_1086 = null;
		if (onDemandFetcher != null)
			onDemandFetcher.stop();
		onDemandFetcher = null;
		menuState.clearReferencesForQuit();
		disposeTitleScreen();
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
		System.gc();
	}

	public void method21(boolean flag) {
		if (flag)
			return;
		if (super.clickButton == 1) {
			if (super.clickX >= 539 && super.clickX <= 573 && super.clickY >= 169 && super.clickY < 205
					&& interfaceState.tabInterfaceIds[0] != -1) {
				aBoolean1181 = true;
				interfaceState.selectedTab = 0;
				aBoolean950 = true;
			}
			if (super.clickX >= 569 && super.clickX <= 599 && super.clickY >= 168 && super.clickY < 205
					&& interfaceState.tabInterfaceIds[1] != -1) {
				aBoolean1181 = true;
				interfaceState.selectedTab = 1;
				aBoolean950 = true;
			}
			if (super.clickX >= 597 && super.clickX <= 627 && super.clickY >= 168 && super.clickY < 205
					&& interfaceState.tabInterfaceIds[2] != -1) {
				aBoolean1181 = true;
				interfaceState.selectedTab = 2;
				aBoolean950 = true;
			}
			if (super.clickX >= 625 && super.clickX <= 669 && super.clickY >= 168 && super.clickY < 203
					&& interfaceState.tabInterfaceIds[3] != -1) {
				aBoolean1181 = true;
				aBoolean950 = true;
			}
			if (super.clickX >= 666 && super.clickX <= 696 && super.clickY >= 168 && super.clickY < 205
					&& interfaceState.tabInterfaceIds[4] != -1) {
				aBoolean1181 = true;
				interfaceState.selectedTab = 4;
				aBoolean950 = true;
			}
			if (super.clickX >= 694 && super.clickX <= 724 && super.clickY >= 168 && super.clickY < 205
					&& interfaceState.tabInterfaceIds[5] != -1) {
				aBoolean1181 = true;
				interfaceState.selectedTab = 5;
				aBoolean950 = true;
			}
			if (super.clickX >= 722 && super.clickX <= 756 && super.clickY >= 169 && super.clickY < 205
					&& interfaceState.tabInterfaceIds[6] != -1) {
				aBoolean1181 = true;
				interfaceState.selectedTab = 6;
				aBoolean950 = true;
			}
			if (super.clickX >= 540 && super.clickX <= 574 && super.clickY >= 466 && super.clickY < 502
					&& interfaceState.tabInterfaceIds[7] != -1) {
				aBoolean1181 = true;
				interfaceState.selectedTab = 7;
				aBoolean950 = true;
			}
			if (super.clickX >= 572 && super.clickX <= 602 && super.clickY >= 466 && super.clickY < 503
					&& interfaceState.tabInterfaceIds[8] != -1) {
				aBoolean1181 = true;
				interfaceState.selectedTab = 8;
				aBoolean950 = true;
			}
			if (super.clickX >= 599 && super.clickX <= 629 && super.clickY >= 466 && super.clickY < 503
					&& interfaceState.tabInterfaceIds[9] != -1) {
				aBoolean1181 = true;
				interfaceState.selectedTab = 9;
				aBoolean950 = true;
			}
			if (super.clickX >= 627 && super.clickX <= 671 && super.clickY >= 467 && super.clickY < 502
					&& interfaceState.tabInterfaceIds[10] != -1) {
				aBoolean1181 = true;
				interfaceState.selectedTab = 10;
				aBoolean950 = true;
			}
			if (super.clickX >= 669 && super.clickX <= 699 && super.clickY >= 466 && super.clickY < 503
					&& interfaceState.tabInterfaceIds[11] != -1) {
				aBoolean1181 = true;
				interfaceState.selectedTab = 11;
				aBoolean950 = true;
			}
			if (super.clickX >= 696 && super.clickX <= 726 && super.clickY >= 466 && super.clickY < 503
					&& interfaceState.tabInterfaceIds[12] != -1) {
				aBoolean1181 = true;
				interfaceState.selectedTab = 12;
				aBoolean950 = true;
			}
			if (super.clickX >= 724 && super.clickX <= 758 && super.clickY >= 466 && super.clickY < 502
					&& interfaceState.tabInterfaceIds[13] != -1) {
				aBoolean1181 = true;
				interfaceState.selectedTab = 13;
				aBoolean950 = true;
			}
		}
	}

	/* Legacy client.method22(int i): i -> removed nonzero division sentinel. */
	private void updateCameraFollow() {
		cameraController.updateFollow(localPlayer, keyStatus, worldState, currentPlane,
				regionManager.regionX, regionManager.regionY, regionManager.baseX, regionManager.baseY);
	}


	/*
	 * Legacy client.method23(Widget class13, int i):
	 *   class13 -> widget
	 *   i       -> removed division sentinel; the only caller supplied 8
	 */
	public boolean buildSocialWidgetMenu(Widget class13) {
		int j = class13.contentType;
		if (j >= 1 && j <= 200 || j >= 701 && j <= 900) {
			if (j >= 801)
				j -= 701;
			else if (j >= 701)
				j -= 601;
			else if (j >= 101)
				j -= 101;
			else
				j--;
			menuState.actionNames[menuState.count] = "Remove @whi@" + socialManager.friendNames[j];
			menuState.actionIds[menuState.count] = 775;
			menuState.count++;
			menuState.actionNames[menuState.count] = "Message @whi@" + socialManager.friendNames[j];
			menuState.actionIds[menuState.count] = 984;
			menuState.count++;
			return true;
		}
		if (j >= 401 && j <= 500) {
			menuState.actionNames[menuState.count] = "Remove @whi@" + class13.text;
			menuState.actionIds[menuState.count] = 859;
			menuState.count++;
			return true;
		} else {
			return false;
		}
	}

	public void method25(int i) {
		if (i != 0)
			networkSession.outgoing.writeByte(186);
		aBoolean1277 = true;
		for (int j = 0; j < 7; j++) {
			anIntArray1326[j] = -1;
			for (int k = 0; k < IdentityKit.count; k++) {
				if (IdentityKit.definitions[k].nonSelectable
						|| IdentityKit.definitions[k].bodyPartId != j + (aBoolean1144 ? 0 : 7))
					continue;
				anIntArray1326[j] = k;
				break;
			}

		}

	}

	public static void setHighMemory() {
		Scene.lowMemory = false;
		Rasterizer3D.lowMemory = false;
		lowMemory = false;
		Region.lowMemory = false;
		GameObjectDefinition.lowMemory = false;
	}

	public void method28(byte byte0) {
		if (anInt1057 > 1)
			anInt1057--;
		if (logoutTimer > 0)
			logoutTimer--;
		for (int i = 0; i < 5; i++)
			if (!processIncomingPacket())
				break;

		if (!loggedIn)
			return;
		synchronized (aClass7_1248.lock) {
			if (accountFlagged) {
				if (super.clickButton != 0 || aClass7_1248.sampleCount >= 40) {
					networkSession.outgoing.writeOpcode(171);
					networkSession.outgoing.writeByte(0);
					int i2 = networkSession.outgoing.position;
					int i3 = 0;
					for (int i4 = 0; i4 < aClass7_1248.sampleCount; i4++) {
						if (i2 - networkSession.outgoing.position >= 240)
							break;
						i3++;
						int k4 = aClass7_1248.yCoordinates[i4];
						if (k4 < 0)
							k4 = 0;
						else if (k4 > 502)
							k4 = 502;
						int j5 = aClass7_1248.xCoordinates[i4];
						if (j5 < 0)
							j5 = 0;
						else if (j5 > 764)
							j5 = 764;
						int l5 = k4 * 765 + j5;
						if (aClass7_1248.yCoordinates[i4] == -1 && aClass7_1248.xCoordinates[i4] == -1) {
							j5 = -1;
							k4 = -1;
							l5 = 0x7ffff;
						}
						if (j5 == anInt1011 && k4 == anInt1012) {
							if (anInt1299 < 2047)
								anInt1299++;
						} else {
							int i6 = j5 - anInt1011;
							anInt1011 = j5;
							int j6 = k4 - anInt1012;
							anInt1012 = k4;
							if (anInt1299 < 8 && i6 >= -32 && i6 <= 31 && j6 >= -32 && j6 <= 31) {
								i6 += 32;
								j6 += 32;
								networkSession.outgoing.writeShort((anInt1299 << 12) + (i6 << 6) + j6);
								anInt1299 = 0;
							} else if (anInt1299 < 8) {
								networkSession.outgoing.writeMedium(0x800000 + (anInt1299 << 19) + l5);
								anInt1299 = 0;
							} else {
								networkSession.outgoing.writeInt(0xc0000000 + (anInt1299 << 19) + l5);
								anInt1299 = 0;
							}
						}
					}

					networkSession.outgoing.writeLength(networkSession.outgoing.position - i2);
					if (i3 >= aClass7_1248.sampleCount) {
						aClass7_1248.sampleCount = 0;
					} else {
						aClass7_1248.sampleCount -= i3;
						for (int l4 = 0; l4 < aClass7_1248.sampleCount; l4++) {
							aClass7_1248.xCoordinates[l4] = aClass7_1248.xCoordinates[l4 + i3];
							aClass7_1248.yCoordinates[l4] = aClass7_1248.yCoordinates[l4 + i3];
						}

					}
				}
			} else {
				aClass7_1248.sampleCount = 0;
			}
		}
		if (super.clickButton != 0) {
			long l = (super.clickTime - aLong902) / 50L;
			if (l > 4095L)
				l = 4095L;
			aLong902 = super.clickTime;
			int j2 = super.clickY;
			if (j2 < 0)
				j2 = 0;
			else if (j2 > 502)
				j2 = 502;
			int j3 = super.clickX;
			if (j3 < 0)
				j3 = 0;
			else if (j3 > 764)
				j3 = 764;
			int j4 = j2 * 765 + j3;
			int i5 = 0;
			if (super.clickButton == 2)
				i5 = 1;
			int k5 = (int) l;
			networkSession.outgoing.writeOpcode(19);
			networkSession.outgoing.writeInt((k5 << 20) + (i5 << 19) + j4);
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
		if (super.hasFocus && !aBoolean1275) {
			aBoolean1275 = true;
			networkSession.outgoing.writeOpcode(187);
			networkSession.outgoing.writeByte(1);
		}
		if (!super.hasFocus && aBoolean1275) {
			aBoolean1275 = false;
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
		actorSynchronizer.updatePlayers(actorUpdater, anInt1325, localPlayerServerIndex, regionManager.baseX, regionManager.baseY);
		actorSynchronizer.updateNpcs(actorUpdater, anInt1325, localPlayerServerIndex, regionManager.baseX, regionManager.baseY);
		method85(0);
		anInt951++;
		if (anInt1023 != 0) {
			anInt1022 += 20;
			if (anInt1022 >= 400)
				anInt1023 = 0;
		}
		if (interfaceState.pressedInventoryArea != 0) {
			anInt1329++;
			if (anInt1329 >= 15) {
				if (interfaceState.pressedInventoryArea == 2)
					aBoolean1181 = true;
				if (interfaceState.pressedInventoryArea == 3)
					aBoolean1240 = true;
				interfaceState.pressedInventoryArea = 0;
			}
		}
		if (interfaceState.inventoryDragArea != 0) {
			interfaceState.inventoryDragDuration++;
			if (super.mouseX > interfaceState.inventoryDragStartX + 5 || super.mouseX < interfaceState.inventoryDragStartX - 5 || super.mouseY > interfaceState.inventoryDragStartY + 5
					|| super.mouseY < interfaceState.inventoryDragStartY - 5)
				aBoolean1155 = true;
			if (super.mouseButton == 0) {
				if (interfaceState.inventoryDragArea == 2)
					aBoolean1181 = true;
				if (interfaceState.inventoryDragArea == 3)
					aBoolean1240 = true;
				interfaceState.inventoryDragArea = 0;
				if (aBoolean1155 && interfaceState.inventoryDragDuration >= 5) {
					interfaceState.hoveredInventoryWidgetId = -1;
					buildContextMenu();
					if (interfaceState.hoveredInventoryWidgetId == interfaceState.draggedInventoryWidgetId && interfaceState.hoveredInventorySlot != interfaceState.draggedInventorySlot) {
						Widget class13 = Widget.get(interfaceState.draggedInventoryWidgetId);
						int i1 = 0;
						if (anInt955 == 1 && class13.contentType == 206)
							i1 = 1;
						if (class13.itemIds[interfaceState.hoveredInventorySlot] <= 0)
							i1 = 0;
						if (class13.inventoryReplaceItems) {
							int k2 = interfaceState.draggedInventorySlot;
							int k3 = interfaceState.hoveredInventorySlot;
							class13.itemIds[k3] = class13.itemIds[k2];
							class13.itemAmounts[k3] = class13.itemAmounts[k2];
							class13.itemIds[k2] = -1;
							class13.itemAmounts[k2] = 0;
						} else if (i1 == 1) {
							int l2 = interfaceState.draggedInventorySlot;
							for (int l3 = interfaceState.hoveredInventorySlot; l2 != l3;)
								if (l2 > l3) {
									class13.swapItems(l2 - 1, l2);
									l2--;
								} else if (l2 < l3) {
									class13.swapItems(l2 + 1, l2);
									l2++;
								}

						} else {
							class13.swapItems(interfaceState.hoveredInventorySlot, interfaceState.draggedInventorySlot);
						}
						networkSession.outgoing.writeOpcode(123);
						networkSession.outgoing.writeShortAddLE(interfaceState.hoveredInventorySlot);
						networkSession.outgoing.writeByteAdd(i1);
						networkSession.outgoing.writeShortAdd(interfaceState.draggedInventoryWidgetId);
						networkSession.outgoing.writeShortLE(interfaceState.draggedInventorySlot);
					}
				} else if ((oneButtonMouseMode == 1 || isAddFriendMenuAction(menuState.count - 1)) && menuState.count > 2)
					openContextMenu();
				else if (menuState.count > 0)
					dispatchMenuAction(menuState.count - 1);
				anInt1329 = 10;
				super.clickButton = 0;
			}
		}
		if (Scene.pickedTileX != -1) {
			int j = Scene.pickedTileX;
			int j1 = Scene.pickedTileY;
			boolean flag = walkTo(true, j, j1, 0, 0, MovementPacketEncoder.SCREEN, 0, 0, 0);
			Scene.pickedTileX = -1;
			if (flag) {
				anInt1020 = super.clickX;
				anInt1021 = super.clickY;
				anInt1023 = 1;
				anInt1022 = 0;
			}
		}
		if (super.clickButton == 1 && aString1058 != null) {
			aString1058 = null;
			aBoolean1240 = true;
			super.clickButton = 0;
		}
		processMenuClick();
		if (interfaceState.fullscreenInterfaceId == -1) {
			processMinimapClick();
			method21(false);
		}
		if (super.mouseButton == 1 || super.clickButton == 1)
			anInt1094++;
		if (anInt1284 != 0 || anInt1044 != 0 || anInt1129 != 0) {
			if (anInt893 < 100) {
				anInt893++;
				if (anInt893 == 100) {
					if (anInt1284 != 0)
						aBoolean1240 = true;
					if (anInt1044 != 0)
						aBoolean1181 = true;
				}
			}
		} else if (anInt893 > 0)
			anInt893--;
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
		if (byte0 != 4)
			networkSession.incomingOpcode = networkSession.incoming.readUnsignedByte();
		if (networkSession.outgoingIdleCycles > 50)
			networkSession.outgoing.writeOpcode(40);
		try {
			if (networkSession.outgoing.position > 0) {
				networkSession.flushOutgoing();
				return;
			}
		} catch (IOException _ex) {
			reconnect();
			return;
		} catch (Exception exception) {
			logout();
		}
	}

	/* Legacy client.method29(boolean flag): flag -> removed false sentinel. */
	private void updateCinematicCamera() {
		cameraController.updateCinematic(worldState, currentPlane);
	}


	/* Legacy client.method30(byte byte0): byte0 -> removed required value 2 sentinel. */
	public void processKeyboardInput() {
		do {
			int i = pollKey();
			if (i == -1)
				break;
			if (interfaceState.openInterfaceId != -1 && interfaceState.openInterfaceId == interfaceState.reportAbuseInterfaceId) {
				if (i == 8 && reportAbuseName.length() > 0)
					reportAbuseName = reportAbuseName.substring(0, reportAbuseName.length() - 1);
				if ((i >= 97 && i <= 122 || i >= 65 && i <= 90 || i >= 48 && i <= 57 || i == 32)
						&& reportAbuseName.length() < 12)
					reportAbuseName += (char) i;
			} else if (messagePromptRaised) {
				if (i >= 32 && i <= 122 && promptInput.length() < 80) {
					promptInput += (char) i;
					aBoolean1240 = true;
				}
				if (i == 8 && promptInput.length() > 0) {
					promptInput = promptInput.substring(0, promptInput.length() - 1);
					aBoolean1240 = true;
				}
				if (i == 13 || i == 10) {
					messagePromptRaised = false;
					aBoolean1240 = true;
					if (promptAction == 1) {
						long l = Base37.encode(promptInput);
						addFriend(l);
					}
					if (promptAction == 2 && socialManager.friendCount > 0) {
						long l1 = Base37.encode(promptInput);
						removeFriend(l1);
					}
					if (promptAction == 3 && promptInput.length() > 0) {
						ChatPacketEncoder.writePrivateMessage(networkSession.outgoing, privateMessageTarget, promptInput);
						promptInput = ChatCodec.normalize(promptInput);
						promptInput = Censor.censor(promptInput);
						addChatMessage(TextFormatter.formatDisplayName(Base37.decode(privateMessageTarget)), promptInput, 6);
						if (privateChatMode == 2) {
							privateChatMode = 1;
							chatModesRedraw = true;
							ChatPacketEncoder.writeChatModes(networkSession.outgoing, publicChatMode, privateChatMode, tradeMode);
						}
					}
					if (promptAction == 4 && socialManager.ignoreCount < 100) {
						long l2 = Base37.encode(promptInput);
						addIgnore(l2);
					}
					if (promptAction == 5 && socialManager.ignoreCount > 0) {
						long l3 = Base37.encode(promptInput);
						removeIgnore(l3);
					}
				}
			} else if (anInt1244 == 1) {
				if (i >= 48 && i <= 57 && aString949.length() < 10) {
					aString949 += (char) i;
					aBoolean1240 = true;
				}
				if (i == 8 && aString949.length() > 0) {
					aString949 = aString949.substring(0, aString949.length() - 1);
					aBoolean1240 = true;
				}
				if (i == 13 || i == 10) {
					if (aString949.length() > 0) {
						int k = 0;
						try {
							k = Integer.parseInt(aString949);
						} catch (Exception _ex) {
						}
						networkSession.outgoing.writeOpcode(75);
						networkSession.outgoing.writeInt(k);
					}
					anInt1244 = 0;
					aBoolean1240 = true;
				}
			} else if (anInt1244 == 2) {
				if (i >= 32 && i <= 122 && aString949.length() < 12) {
					aString949 += (char) i;
					aBoolean1240 = true;
				}
				if (i == 8 && aString949.length() > 0) {
					aString949 = aString949.substring(0, aString949.length() - 1);
					aBoolean1240 = true;
				}
				if (i == 13 || i == 10) {
					if (aString949.length() > 0) {
						networkSession.outgoing.writeOpcode(206);
						networkSession.outgoing.writeLong(Base37.encode(aString949));
					}
					anInt1244 = 0;
					aBoolean1240 = true;
				}
			} else if (anInt1244 == 3) {
				if (i >= 32 && i <= 122 && aString949.length() < 40) {
					aString949 += (char) i;
					aBoolean1240 = true;
				}
				if (i == 8 && aString949.length() > 0) {
					aString949 = aString949.substring(0, aString949.length() - 1);
					aBoolean1240 = true;
				}
			} else if (interfaceState.chatboxInterfaceId == -1 && interfaceState.fullscreenInterfaceId == -1) {
				if (i >= 32 && i <= 122 && chatInput.length() < 80) {
					chatInput += (char) i;
					aBoolean1240 = true;
				}
				if (i == 8 && chatInput.length() > 0) {
					chatInput = chatInput.substring(0, chatInput.length() - 1);
					aBoolean1240 = true;
				}
				if ((i == 13 || i == 10) && chatInput.length() > 0) {
					if (playerRights == 2) {
						if (chatInput.equals("::clientdrop"))
							reconnect();
						if (chatInput.equals("::lag"))
							method138(false);
						if (chatInput.equals("::prefetchmusic")) {
							for (int i1 = 0; i1 < onDemandFetcher.getFileCount(2); i1++)
								onDemandFetcher.setExtraPriority(2, i1, (byte) 1);

						}
						if (chatInput.equals("::fpson"))
							aBoolean868 = true;
						if (chatInput.equals("::fpsoff"))
							aBoolean868 = false;
						if (chatInput.equals("::noclip")) {
							for (int j1 = 0; j1 < 4; j1++) {
								for (int k1 = 1; k1 < 103; k1++) {
									for (int j2 = 1; j2 < 103; j2++)
										worldState.collisionMaps[j1].flags[k1][j2] = 0;

								}

							}

						}
					}
					if (chatInput.startsWith("::")) {
						ChatPacketEncoder.writeCommand(networkSession.outgoing, chatInput);
					} else {
						String s = chatInput.toLowerCase();
						int i2 = 0;
						if (s.startsWith("yellow:")) {
							i2 = 0;
							chatInput = chatInput.substring(7);
						} else if (s.startsWith("red:")) {
							i2 = 1;
							chatInput = chatInput.substring(4);
						} else if (s.startsWith("green:")) {
							i2 = 2;
							chatInput = chatInput.substring(6);
						} else if (s.startsWith("cyan:")) {
							i2 = 3;
							chatInput = chatInput.substring(5);
						} else if (s.startsWith("purple:")) {
							i2 = 4;
							chatInput = chatInput.substring(7);
						} else if (s.startsWith("white:")) {
							i2 = 5;
							chatInput = chatInput.substring(6);
						} else if (s.startsWith("flash1:")) {
							i2 = 6;
							chatInput = chatInput.substring(7);
						} else if (s.startsWith("flash2:")) {
							i2 = 7;
							chatInput = chatInput.substring(7);
						} else if (s.startsWith("flash3:")) {
							i2 = 8;
							chatInput = chatInput.substring(7);
						} else if (s.startsWith("glow1:")) {
							i2 = 9;
							chatInput = chatInput.substring(6);
						} else if (s.startsWith("glow2:")) {
							i2 = 10;
							chatInput = chatInput.substring(6);
						} else if (s.startsWith("glow3:")) {
							i2 = 11;
							chatInput = chatInput.substring(6);
						}
						s = chatInput.toLowerCase();
						int k2 = 0;
						if (s.startsWith("wave:")) {
							k2 = 1;
							chatInput = chatInput.substring(5);
						} else if (s.startsWith("wave2:")) {
							k2 = 2;
							chatInput = chatInput.substring(6);
						} else if (s.startsWith("shake:")) {
							k2 = 3;
							chatInput = chatInput.substring(6);
						} else if (s.startsWith("scroll:")) {
							k2 = 4;
							chatInput = chatInput.substring(7);
						} else if (s.startsWith("slide:")) {
							k2 = 5;
							chatInput = chatInput.substring(6);
						}
						ChatPacketEncoder.writePublicMessage(networkSession.outgoing, i2, k2, chatInput, chatBuffer);
						chatInput = ChatCodec.normalize(chatInput);
						chatInput = Censor.censor(chatInput);
						localPlayer.overheadText = chatInput;
						localPlayer.overheadTextColor = i2;
						localPlayer.overheadTextEffect = k2;
						localPlayer.overheadTextCyclesRemaining = 150;
						if (playerRights == 2)
							addChatMessage("@cr2@" + localPlayer.name,
									((Actor) (localPlayer)).overheadText, 2);
						else if (playerRights == 1)
							addChatMessage("@cr1@" + localPlayer.name,
									((Actor) (localPlayer)).overheadText, 2);
						else
							addChatMessage(localPlayer.name,
									((Actor) (localPlayer)).overheadText, 2);
						if (publicChatMode == 2) {
							publicChatMode = 3;
							chatModesRedraw = true;
							ChatPacketEncoder.writeChatModes(networkSession.outgoing, publicChatMode, privateChatMode, tradeMode);
						}
					}
					chatInput = "";
					aBoolean1240 = true;
				}
			}
		} while (true);
	}

	public DataInputStream openJaggrabStream(String request) throws IOException {
		if (jaggrabSocket != null) {
			try {
				jaggrabSocket.close();
			} catch (Exception _ex) {
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

	public Socket openSocket(int port) throws IOException {
		return Signlink.openSocket(port);
	}

	public boolean processIncomingPacket() {
		try {
			if (!networkSession.readIncomingPacket())
				return false;
			return dispatchIncomingPacket();
		} catch (IOException _ex) {
			reconnect();
		} catch (Exception exception) {
			String s1 = "T2 - " + networkSession.incomingOpcode + "," + networkSession.secondLastOpcode + "," + networkSession.thirdLastOpcode + " - " + networkSession.incomingLength + ","
					+ (regionManager.baseX + ((Actor) (localPlayer)).pathX[0]) + ","
					+ (regionManager.baseY + ((Actor) (localPlayer)).pathY[0]) + " - ";
			for (int j16 = 0; j16 < networkSession.incomingLength && j16 < 50; j16++)
				s1 = s1 + networkSession.incoming.payload[j16] + ",";

			Signlink.reportError(s1);
			logout();
		}
		return true;
	}

	private boolean dispatchIncomingPacket() {
		if (networkSession.incomingOpcode == 166) {
			int l = networkSession.incoming.readShortLE();
			int l10 = networkSession.incoming.readShortLE();
			int k16 = networkSession.incoming.readUnsignedShort();
			Widget class13_5 = Widget.get(k16);
			class13_5.xOffset = l10;
			class13_5.yOffset = l;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 186) {
			int i1 = networkSession.incoming.readUnsignedShortAdd();
			int i11 = networkSession.incoming.readUnsignedShortAddLE();
			int l16 = networkSession.incoming.readUnsignedShortAdd();
			int i22 = networkSession.incoming.readUnsignedShortLE();
			Widget.get(i11).modelPitch = i1;
			Widget.get(i11).modelYaw = i22;
			Widget.get(i11).modelZoom = l16;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 216) {
			int j1 = networkSession.incoming.readUnsignedShortAddLE();
			int j11 = networkSession.incoming.readUnsignedShortAddLE();
			Widget.get(j11).mediaType = 1;
			Widget.get(j11).mediaId = j1;
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
			int l1 = networkSession.incoming.readUnsignedShortAdd();
			byte byte0 = networkSession.incoming.readByteSub();
			anIntArray1005[l1] = byte0;
			if (varpValues[l1] != byte0) {
				varpValues[l1] = byte0;
				method105(0, l1);
				aBoolean1181 = true;
				if (interfaceState.dialogueInterfaceId != -1)
					aBoolean1240 = true;
			}
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 13) {
			for (int i2 = 0; i2 < actorSynchronizer.players.length; i2++)
				if (actorSynchronizer.players[i2] != null)
					actorSynchronizer.players[i2].sequence = -1;

			for (int l11 = 0; l11 < actorSynchronizer.npcs.length; l11++)
				if (actorSynchronizer.npcs[l11] != null)
					actorSynchronizer.npcs[l11].sequence = -1;

			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 156) {
			minimapRenderer.state = networkSession.incoming.readUnsignedByte();
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 162) {
			int j2 = networkSession.incoming.readUnsignedShortAdd();
			int i12 = networkSession.incoming.readUnsignedShortLE();
			Widget.get(i12).mediaType = 2;
			Widget.get(i12).mediaId = j2;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 109) {
			int k2 = networkSession.incoming.readUnsignedShort();
			widgetRuntime.resetAnimations(k2);
			if (interfaceState.sidebarOverlayInterfaceId != -1) {
				unloadInterface(interfaceState.sidebarOverlayInterfaceId);
				aBoolean1181 = true;
				aBoolean950 = true;
			}
			if (interfaceState.fullscreenInterfaceId != -1) {
				unloadInterface(interfaceState.fullscreenInterfaceId);
				aBoolean1046 = true;
			}
			if (interfaceState.fullscreenOverlayInterfaceId != -1) {
				unloadInterface(interfaceState.fullscreenOverlayInterfaceId);
			}
			if (interfaceState.openInterfaceId != -1) {
				unloadInterface(interfaceState.openInterfaceId);
			}
			if (interfaceState.chatboxInterfaceId != k2) {
				unloadInterface(interfaceState.chatboxInterfaceId);
				interfaceState.chatboxInterfaceId = k2;
			}
			aBoolean1239 = false;
			aBoolean1240 = true;
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
			int j3 = networkSession.incoming.readShortLE();
			if (j3 != interfaceState.dialogueInterfaceId) {
				unloadInterface(interfaceState.dialogueInterfaceId);
				interfaceState.dialogueInterfaceId = j3;
			}
			aBoolean1240 = true;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 218) {
			int k3 = networkSession.incoming.readUnsignedShort();
			int k12 = networkSession.incoming.readUnsignedShortAdd();
			int j17 = k12 >> 10 & 0x1f;
			int j22 = k12 >> 5 & 0x1f;
			int l24 = k12 & 0x1f;
			Widget.get(k3).color = (j17 << 19) + (j22 << 11) + (l24 << 3);
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 157) {
			int l3 = networkSession.incoming.readUnsignedByteNeg();
			String s2 = networkSession.incoming.readString();
			int k17 = networkSession.incoming.readUnsignedByte();
			if (l3 >= 1 && l3 <= 5) {
				if (s2.equalsIgnoreCase("null"))
					s2 = null;
				aStringArray1069[l3 - 1] = s2;
				aBooleanArray1070[l3 - 1] = k17 == 0;
			}
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 6) {
			messagePromptRaised = false;
			anInt1244 = 2;
			aString949 = "";
			aBoolean1240 = true;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 201) {
			publicChatMode = networkSession.incoming.readUnsignedByte();
			privateChatMode = networkSession.incoming.readUnsignedByte();
			tradeMode = networkSession.incoming.readUnsignedByte();
			chatModesRedraw = true;
			aBoolean1240 = true;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 199) {
			anInt1197 = networkSession.incoming.readUnsignedByte();
			if (anInt1197 == 1)
				anInt1226 = networkSession.incoming.readUnsignedShort();
			if (anInt1197 >= 2 && anInt1197 <= 6) {
				if (anInt1197 == 2) {
					anInt847 = 64;
					anInt848 = 64;
				}
				if (anInt1197 == 3) {
					anInt847 = 0;
					anInt848 = 64;
				}
				if (anInt1197 == 4) {
					anInt847 = 128;
					anInt848 = 64;
				}
				if (anInt1197 == 5) {
					anInt847 = 64;
					anInt848 = 0;
				}
				if (anInt1197 == 6) {
					anInt847 = 64;
					anInt848 = 128;
				}
				anInt1197 = 2;
				anInt844 = networkSession.incoming.readUnsignedShort();
				anInt845 = networkSession.incoming.readUnsignedShort();
				anInt846 = networkSession.incoming.readUnsignedByte();
			}
			if (anInt1197 == 10)
				anInt1151 = networkSession.incoming.readUnsignedShort();
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
			int j4 = networkSession.incoming.readIntIME();
			int i13 = networkSession.incoming.readUnsignedShortLE();
			anIntArray1005[i13] = j4;
			if (varpValues[i13] != j4) {
				varpValues[i13] = j4;
				method105(0, i13);
				aBoolean1181 = true;
				if (interfaceState.dialogueInterfaceId != -1)
					aBoolean1240 = true;
			}
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 29) {
			if (interfaceState.sidebarOverlayInterfaceId != -1) {
				unloadInterface(interfaceState.sidebarOverlayInterfaceId);
				aBoolean1181 = true;
				aBoolean950 = true;
			}
			if (interfaceState.chatboxInterfaceId != -1) {
				unloadInterface(interfaceState.chatboxInterfaceId);
				aBoolean1240 = true;
			}
			if (interfaceState.fullscreenInterfaceId != -1) {
				unloadInterface(interfaceState.fullscreenInterfaceId);
				aBoolean1046 = true;
			}
			if (interfaceState.fullscreenOverlayInterfaceId != -1) {
				unloadInterface(interfaceState.fullscreenOverlayInterfaceId);
			}
			if (interfaceState.openInterfaceId != -1) {
				unloadInterface(interfaceState.openInterfaceId);
			}
			if (anInt1244 != 0) {
				anInt1244 = 0;
				aBoolean1240 = true;
			}
			aBoolean1239 = false;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 76) {
			anInt1083 = networkSession.incoming.readUnsignedShortLE();
			anInt1075 = networkSession.incoming.readUnsignedShortAddLE();
			networkSession.incoming.readUnsignedShort();
			anInt1208 = networkSession.incoming.readUnsignedShort();
			anInt1170 = networkSession.incoming.readUnsignedShortLE();
			unreadMessageCount = networkSession.incoming.readUnsignedShortAdd();
			anInt1215 = networkSession.incoming.readUnsignedShortAdd();
			anInt992 = networkSession.incoming.readUnsignedShort();
			anInt1241 = networkSession.incoming.readIntLE();
			anInt1034 = networkSession.incoming.readUnsignedShortAddLE();
			networkSession.incoming.readUnsignedByteAdd();
			Signlink.lookupDns(Ipv4Address.format(anInt1241));
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 63) {
			String s = networkSession.incoming.readString();
			if (s.endsWith(":tradereq:")) {
				String s3 = s.substring(0, s.indexOf(":"));
				long l18 = Base37.encode(s3);
				boolean ignored = socialManager.isIgnored(l18);
				if (!ignored && tutorialIslandFlag == 0)
					addChatMessage(s3, "wishes to trade with you.", 4);
			} else if (s.endsWith(":duelreq:")) {
				String s4 = s.substring(0, s.indexOf(":"));
				long l19 = Base37.encode(s4);
				boolean ignored = socialManager.isIgnored(l19);
				if (!ignored && tutorialIslandFlag == 0)
					addChatMessage(s4, "wishes to duel with you.", 8);
			} else if (s.endsWith(":chalreq:")) {
				String s5 = s.substring(0, s.indexOf(":"));
				long l20 = Base37.encode(s5);
				boolean ignored = socialManager.isIgnored(l20);
				if (!ignored && tutorialIslandFlag == 0) {
					String s8 = s.substring(s.indexOf(":") + 1, s.length() - 9);
					addChatMessage(s5, s8, 8);
				}
			} else {
				addChatMessage("", s, 0);
			}
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 50) {
			int k4 = networkSession.incoming.readSignedShort();
			if (k4 >= 0)
				widgetRuntime.resetAnimations(k4);
			if (k4 != interfaceState.walkableInterfaceId) {
				unloadInterface(interfaceState.walkableInterfaceId);
				interfaceState.walkableInterfaceId = k4;
			}
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 82) {
			boolean flag = networkSession.incoming.readUnsignedByte() == 1;
			int j13 = networkSession.incoming.readUnsignedShort();
			Widget.get(j13).mouseoverTriggered = flag;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 174) {
			if (interfaceState.selectedTab == 12)
				aBoolean1181 = true;
			weight = networkSession.incoming.readSignedShort();
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 233) {
			anInt1319 = networkSession.incoming.readUnsignedByte();
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 61) {
			destinationX = 0;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 128) {
			int l4 = networkSession.incoming.readUnsignedShortAdd();
			int k13 = networkSession.incoming.readUnsignedShortAddLE();
			if (interfaceState.chatboxInterfaceId != -1) {
				unloadInterface(interfaceState.chatboxInterfaceId);
				aBoolean1240 = true;
			}
			if (interfaceState.fullscreenInterfaceId != -1) {
				unloadInterface(interfaceState.fullscreenInterfaceId);
				aBoolean1046 = true;
			}
			if (interfaceState.fullscreenOverlayInterfaceId != -1) {
				unloadInterface(interfaceState.fullscreenOverlayInterfaceId);
			}
			if (interfaceState.openInterfaceId != l4) {
				unloadInterface(interfaceState.openInterfaceId);
				interfaceState.openInterfaceId = l4;
			}
			if (interfaceState.sidebarOverlayInterfaceId != k13) {
				unloadInterface(interfaceState.sidebarOverlayInterfaceId);
				interfaceState.sidebarOverlayInterfaceId = k13;
			}
			if (anInt1244 != 0) {
				anInt1244 = 0;
				aBoolean1240 = true;
			}
			aBoolean1181 = true;
			aBoolean950 = true;
			aBoolean1239 = false;
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
			aBoolean1181 = true;
			int j5 = networkSession.incoming.readUnsignedShort();
			Widget class13 = Widget.get(j5);
			while (networkSession.incoming.position < networkSession.incomingLength) {
				int j18 = networkSession.incoming.readUnsignedSmart();
				int i23 = networkSession.incoming.readUnsignedShort();
				int j25 = networkSession.incoming.readUnsignedByte();
				if (j25 == 255)
					j25 = networkSession.incoming.readInt();
				if (j18 >= 0 && j18 < class13.itemIds.length) {
					class13.itemIds[j18] = i23;
					class13.itemAmounts[j18] = j25;
				}
			}
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 78) {
			long encodedName = networkSession.incoming.readLong();
			int world = networkSession.incoming.readUnsignedByte();
			if (socialManager.updateFriend(encodedName, world, currentWorldId, this::addChatMessage))
				aBoolean1181 = true;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 58) {
			messagePromptRaised = false;
			anInt1244 = 1;
			aString949 = "";
			aBoolean1240 = true;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 252) {
			interfaceState.selectedTab = networkSession.incoming.readUnsignedByteNeg();
			aBoolean1181 = true;
			aBoolean950 = true;
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
			int i6 = networkSession.incoming.readUnsignedShortAddLE();
			Widget.get(i6).mediaType = 3;
			if (localPlayer.npcDefinition == null)
				Widget.get(i6).mediaId = (localPlayer.bodyColors[0] << 25)
						+ (localPlayer.bodyColors[4] << 20)
						+ (localPlayer.equipment[0] << 15)
						+ (localPlayer.equipment[8] << 10)
						+ (localPlayer.equipment[11] << 5)
						+ localPlayer.equipment[1];
			else
				Widget.get(i6).mediaId = (int) (0x12345678L + localPlayer.npcDefinition.id);
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 135) {
			long l6 = networkSession.incoming.readLong();
			int i19 = networkSession.incoming.readInt();
			int j23 = networkSession.incoming.readUnsignedByte();
			boolean flag4 = chatHistory.hasRecentPrivateMessage(i19);

			if (j23 <= 1 && socialManager.isIgnored(l6))
				flag4 = true;
			if (!flag4 && tutorialIslandFlag == 0)
				try {
					chatHistory.rememberPrivateMessage(i19);
					String s9 = ChatCodec.decode(networkSession.incoming, networkSession.incomingLength - 13);
					if (j23 != 3)
						s9 = Censor.censor(s9);
					if (j23 == 2 || j23 == 3)
						addChatMessage("@cr2@" + TextFormatter.formatDisplayName(Base37.decode(l6)), s9, 7);
					else if (j23 == 1)
						addChatMessage("@cr1@" + TextFormatter.formatDisplayName(Base37.decode(l6)), s9, 7);
					else
						addChatMessage(TextFormatter.formatDisplayName(Base37.decode(l6)), s9, 3);
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
				zoneUpdates.decode(networkSession.incoming, updateType, currentPlane, anInt1325,
						localPlayerServerIndex, localPlayer, actorSynchronizer, this::queueAreaSound);
			}
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 159) {
			int k6 = networkSession.incoming.readUnsignedShortAddLE();
			widgetRuntime.resetAnimations(k6);
			if (interfaceState.sidebarOverlayInterfaceId != -1) {
				unloadInterface(interfaceState.sidebarOverlayInterfaceId);
				aBoolean1181 = true;
				aBoolean950 = true;
			}
			if (interfaceState.chatboxInterfaceId != -1) {
				unloadInterface(interfaceState.chatboxInterfaceId);
				aBoolean1240 = true;
			}
			if (interfaceState.fullscreenInterfaceId != -1) {
				unloadInterface(interfaceState.fullscreenInterfaceId);
				aBoolean1046 = true;
			}
			if (interfaceState.fullscreenOverlayInterfaceId != -1) {
				unloadInterface(interfaceState.fullscreenOverlayInterfaceId);
			}
			if (interfaceState.openInterfaceId != k6) {
				unloadInterface(interfaceState.openInterfaceId);
				interfaceState.openInterfaceId = k6;
			}
			if (anInt1244 != 0) {
				anInt1244 = 0;
				aBoolean1240 = true;
			}
			aBoolean1239 = false;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 246) {
			int i7 = networkSession.incoming.readUnsignedShortAddLE();
			widgetRuntime.resetAnimations(i7);
			if (interfaceState.chatboxInterfaceId != -1) {
				unloadInterface(interfaceState.chatboxInterfaceId);
				aBoolean1240 = true;
			}
			if (interfaceState.fullscreenInterfaceId != -1) {
				unloadInterface(interfaceState.fullscreenInterfaceId);
				aBoolean1046 = true;
			}
			if (interfaceState.fullscreenOverlayInterfaceId != -1) {
				unloadInterface(interfaceState.fullscreenOverlayInterfaceId);
			}
			if (interfaceState.openInterfaceId != -1) {
				unloadInterface(interfaceState.openInterfaceId);
			}
			if (interfaceState.sidebarOverlayInterfaceId != i7) {
				unloadInterface(interfaceState.sidebarOverlayInterfaceId);
				interfaceState.sidebarOverlayInterfaceId = i7;
			}
			if (anInt1244 != 0) {
				anInt1244 = 0;
				aBoolean1240 = true;
			}
			aBoolean1181 = true;
			aBoolean950 = true;
			aBoolean1239 = false;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 49) {
			aBoolean1181 = true;
			int j7 = networkSession.incoming.readUnsignedByteNeg();
			int j14 = networkSession.incoming.readUnsignedByte();
			int j19 = networkSession.incoming.readInt();
			skillExperiences[j7] = j19;
			currentSkillLevels[j7] = j14;
			baseSkillLevels[j7] = 1;
			for (int k23 = 0; k23 < 98; k23++)
				if (j19 >= experienceTable[k23])
					baseSkillLevels[j7] = k23 + 2;

			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 206) {
			aBoolean1181 = true;
			int k7 = networkSession.incoming.readUnsignedShort();
			Widget class13_1 = Widget.get(k7);
			int k19 = networkSession.incoming.readUnsignedShort();
			for (int l23 = 0; l23 < k19; l23++) {
				class13_1.itemIds[l23] = networkSession.incoming.readUnsignedShortAddLE();
				int l25 = networkSession.incoming.readUnsignedByteNeg();
				if (l25 == 255)
					l25 = networkSession.incoming.readIntLE();
				class13_1.itemAmounts[l23] = l25;
			}

			for (int i26 = k19; i26 < class13_1.itemIds.length; i26++) {
				class13_1.itemIds[i26] = 0;
				class13_1.itemAmounts[i26] = 0;
			}

			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 222 || networkSession.incomingOpcode == 53) {
			RegionManager.RegionShift shift = regionManager.decodeRebuild(networkSession.incoming,
					networkSession.incomingOpcode, onDemandFetcher, actorSynchronizer, worldState,
					destinationX, destinationY);
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
			anInt1057 = networkSession.incoming.readUnsignedShortLE() * 30;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 41 || networkSession.incomingOpcode == 121
				|| networkSession.incomingOpcode == 203 || networkSession.incomingOpcode == 106
				|| networkSession.incomingOpcode == 59 || networkSession.incomingOpcode == 181
				|| networkSession.incomingOpcode == 208 || networkSession.incomingOpcode == 107
				|| networkSession.incomingOpcode == 142 || networkSession.incomingOpcode == 88
				|| networkSession.incomingOpcode == 152) {
			zoneUpdates.decode(networkSession.incoming, networkSession.incomingOpcode, currentPlane, anInt1325,
					localPlayerServerIndex, localPlayer, actorSynchronizer, this::queueAreaSound);
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 125) {
			if (interfaceState.selectedTab == 12)
				aBoolean1181 = true;
			runEnergy = networkSession.incoming.readUnsignedByte();
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 21) {
			int i8 = networkSession.incoming.readUnsignedShort();
			int l14 = networkSession.incoming.readUnsignedShortLE();
			int j21 = networkSession.incoming.readUnsignedShortAddLE();
			if (l14 == 65535) {
				Widget.get(j21).mediaType = 0;
				networkSession.incomingOpcode = -1;
				return true;
			} else {
				ItemDefinition class16 = ItemDefinition.lookup(l14);
				Widget.get(j21).mediaType = 4;
				Widget.get(j21).mediaId = l14;
				Widget.get(j21).modelPitch = class16.xan2d;
				Widget.get(j21).modelYaw = class16.yan2d;
				Widget.get(j21).modelZoom = (class16.zoom2d * 100) / i8;
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
			cameraController.setCinematicPosition(tileX, tileY, heightOffset, baseSpeed, scale, worldState, currentPlane);
			networkSession.incomingOpcode = -1;
			return true;
		}

		if (networkSession.incomingOpcode == 2) {
			int j8 = networkSession.incoming.readUnsignedShortAddLE();
			int i15 = networkSession.incoming.readShortAdd();
			Widget class13_3 = Widget.get(j8);
			if (class13_3.animationId != i15 || i15 == -1) {
				class13_3.animationId = i15;
				class13_3.animationFrame = 0;
				class13_3.animationCycle = 0;
			}
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 71) {
			actorSynchronizer.decodeNpcUpdate(networkSession.incoming, networkSession.incomingLength, anInt1325, loginScreen.username);
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 226) {
			socialManager.replaceIgnoreList(networkSession.incoming, networkSession.incomingLength);
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 10) {
			int l8 = networkSession.incoming.readUnsignedByteSub();
			int j15 = networkSession.incoming.readUnsignedShortAdd();
			if (j15 == 65535)
				j15 = -1;
			if (interfaceState.tabInterfaceIds[l8] != j15) {
				unloadInterface(interfaceState.tabInterfaceIds[l8]);
				interfaceState.tabInterfaceIds[l8] = j15;
			}
			aBoolean1181 = true;
			aBoolean950 = true;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 219) {
			int i9 = networkSession.incoming.readUnsignedShortLE();
			Widget class13_2 = Widget.get(i9);
			for (int k21 = 0; k21 < class13_2.itemIds.length; k21++) {
				class13_2.itemIds[k21] = -1;
				class13_2.itemIds[k21] = 0;
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
				aBoolean1181 = true;
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
			anInt1068 = networkSession.incoming.readUnsignedByte();
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
			int k9 = networkSession.incoming.readUnsignedShortLE();
			int k15 = networkSession.incoming.readUnsignedShortAdd();
			widgetRuntime.resetAnimations(k15);
			if (k9 != -1)
				widgetRuntime.resetAnimations(k9);
			if (interfaceState.openInterfaceId != -1) {
				unloadInterface(interfaceState.openInterfaceId);
			}
			if (interfaceState.sidebarOverlayInterfaceId != -1) {
				unloadInterface(interfaceState.sidebarOverlayInterfaceId);
			}
			if (interfaceState.chatboxInterfaceId != -1) {
				unloadInterface(interfaceState.chatboxInterfaceId);
			}
			if (interfaceState.fullscreenInterfaceId != k15) {
				unloadInterface(interfaceState.fullscreenInterfaceId);
				interfaceState.fullscreenInterfaceId = k15;
			}
			if (interfaceState.fullscreenOverlayInterfaceId != k15) {
				unloadInterface(interfaceState.fullscreenOverlayInterfaceId);
				interfaceState.fullscreenOverlayInterfaceId = k9;
			}
			anInt1244 = 0;
			aBoolean1239 = false;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 251) {
			socialManager.friendListStatus = networkSession.incoming.readUnsignedByte();
			aBoolean1181 = true;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 18) {
			int l9 = networkSession.incoming.readUnsignedShort();
			int l15 = networkSession.incoming.readUnsignedShortAdd();
			int l21 = networkSession.incoming.readUnsignedShortLE();
			Widget.get(l15).modelRotationSpeed = (l9 << 16) + l21;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 90) {
			currentPlane = actorSynchronizer.decodePlayerUpdate(networkSession.incoming, networkSession.incomingLength, anInt1325,
					currentPlane, loginScreen.username, chatBuffer, actorChatHandler);
			regionManager.playerUpdateReceived();
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 113) {
			for (int i10 = 0; i10 < varpValues.length; i10++)
				if (varpValues[i10] != anIntArray1005[i10]) {
					varpValues[i10] = anIntArray1005[i10];
					method105(0, i10);
					aBoolean1181 = true;
				}

			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 232) {
			int j10 = networkSession.incoming.readUnsignedShortAddLE();
			String s6 = networkSession.incoming.readString();
			Widget.get(j10).text = s6;
			if (Widget.get(j10).parentId == interfaceState.tabInterfaceIds[interfaceState.selectedTab])
				aBoolean1181 = true;
			networkSession.incomingOpcode = -1;
			return true;
		}
		if (networkSession.incomingOpcode == 200) {
			int k10 = networkSession.incoming.readUnsignedShort();
			int i16 = networkSession.incoming.readUnsignedShortAddLE();
			Widget class13_4 = Widget.get(k10);
			if (class13_4 != null && class13_4.type == 0) {
				if (i16 < 0)
					i16 = 0;
				if (i16 > class13_4.scrollHeight - class13_4.height)
					i16 = class13_4.scrollHeight - class13_4.height;
				class13_4.scrollY = i16;
			}
			networkSession.incomingOpcode = -1;
			return true;
		}
		Signlink.reportError("T1 - " + networkSession.incomingOpcode + "," + networkSession.incomingLength + " - " + networkSession.secondLastOpcode + "," + networkSession.thirdLastOpcode);
		logout();
		return true;
	}

	/* Legacy client.method34(byte byte0): byte0 -> removed fixed -79 sentinel. */
	public void drawMenuTooltip() {
		if (menuState.count < 2 && interfaceState.itemSelected == 0 && interfaceState.spellSelected == 0)
			return;
		String s;
		if (interfaceState.itemSelected == 1 && menuState.count < 2)
			s = "Use " + interfaceState.selectedItemName + " with...";
		else if (interfaceState.spellSelected == 1 && menuState.count < 2)
			s = interfaceState.selectedSpellAction + "...";
		else
			s = menuState.actionNames[menuState.count - 1];
		if (menuState.count > 2)
			s = s + "@whi@ / " + (menuState.count - 2) + " more options";
		boldFont.drawRandomizedTextWithTags(s, 4, 15, 0xffffff, anInt1325 / 1000, true);
	}

	/*
	 * Legacy client.method35(boolean flag, boolean flag1, int i, int j, int k, int l,
	 *         int i1, int j1, int k1, int l1, int i2, int j2)
	 *
	 * Parameter mapping:
	 *   flag  -> allowAlternative
	 *   flag1 -> removed dummy branch (all supplied callers pass false)
	 *   i     -> destinationY
	 *   j     -> removed startY (always local player's pathY[0])
	 *   k     -> targetWidth
	 *   l     -> targetHeight
	 *   i1    -> movementType
	 *   j1    -> interactionType
	 *   k1    -> destinationX
	 *   l1    -> accessMask
	 *   i2    -> orientation
	 *   j2    -> removed startX (always local player's pathX[0])
	 */
	private boolean walkTo(boolean allowAlternative, int targetX, int targetY, int targetWidth, int targetHeight,
			int movementType, int interactionType, int orientation, int accessMask) {
		Actor currentPlayer = localPlayer;
		Pathfinder.Route route = pathfinder.findRoute(worldState.collisionMaps[currentPlane], currentPlayer.pathX[0],
				currentPlayer.pathY[0], targetX, targetY, targetWidth, targetHeight, interactionType, orientation, accessMask,
				allowAlternative);
		alternativeRoute = 0;
		if (route == null) {
			return false;
		}

		alternativeRoute = route.isAlternative() ? 1 : 0;
		destinationX = route.getDestinationX();
		destinationY = route.getDestinationY();
		MovementPacketEncoder.write(networkSession.outgoing, route, movementType, regionManager.baseX, regionManager.baseY,
				keyStatus[5] == 1);
		return true;
	}

	public String getConfiguredHost() {
		return "runescape.com";
	}

	/*
	 * Legacy client.method38(int i, int j, int k, Player class50_sub1_sub4_sub3_sub2):
	 *   i -> playerIndex, j -> tileY, k -> tileX, class50... -> player
	 */
	public void buildPlayerMenu(int i, int j, int k, Player class50_sub1_sub4_sub3_sub2) {
		if (class50_sub1_sub4_sub3_sub2 == localPlayer)
			return;
		if (menuState.count >= 400)
			return;
		String s;
		if (class50_sub1_sub4_sub3_sub2.skillLevel == 0)
			s = class50_sub1_sub4_sub3_sub2.name + method92(class50_sub1_sub4_sub3_sub2.combatLevel,
					localPlayer.combatLevel, 736) + " (level-"
					+ class50_sub1_sub4_sub3_sub2.combatLevel + ")";
		else
			s = class50_sub1_sub4_sub3_sub2.name + " (skill-" + class50_sub1_sub4_sub3_sub2.skillLevel + ")";
		if (interfaceState.itemSelected == 1) {
			menuState.actionNames[menuState.count] = "Use " + interfaceState.selectedItemName + " with @whi@" + s;
			menuState.actionIds[menuState.count] = 596;
			menuState.actionCmd1[menuState.count] = i;
			menuState.actionCmd2[menuState.count] = k;
			menuState.actionCmd3[menuState.count] = j;
			menuState.count++;
		} else if (interfaceState.spellSelected == 1) {
			if ((interfaceState.selectedSpellTargetMask & 8) == 8) {
				menuState.actionNames[menuState.count] = interfaceState.selectedSpellAction + " @whi@" + s;
				menuState.actionIds[menuState.count] = 918;
				menuState.actionCmd1[menuState.count] = i;
				menuState.actionCmd2[menuState.count] = k;
				menuState.actionCmd3[menuState.count] = j;
				menuState.count++;
			}
		} else {
			for (int i1 = 4; i1 >= 0; i1--)
				if (aStringArray1069[i1] != null) {
					menuState.actionNames[menuState.count] = aStringArray1069[i1] + " @whi@" + s;
					char c = '\0';
					if (aStringArray1069[i1].equalsIgnoreCase("attack")) {
						if (class50_sub1_sub4_sub3_sub2.combatLevel > localPlayer.combatLevel)
							c = '\u07D0';
						if (localPlayer.team != 0 && class50_sub1_sub4_sub3_sub2.team != 0)
							if (localPlayer.team == class50_sub1_sub4_sub3_sub2.team)
								c = '\u07D0';
							else
								c = '\0';
					} else if (aBooleanArray1070[i1])
						c = '\u07D0';
					if (i1 == 0)
						menuState.actionIds[menuState.count] = 200 + c;
					if (i1 == 1)
						menuState.actionIds[menuState.count] = 493 + c;
					if (i1 == 2)
						menuState.actionIds[menuState.count] = 408 + c;
					if (i1 == 3)
						menuState.actionIds[menuState.count] = 677 + c;
					if (i1 == 4)
						menuState.actionIds[menuState.count] = 876 + c;
					menuState.actionCmd1[menuState.count] = i;
					menuState.actionCmd2[menuState.count] = k;
					menuState.actionCmd3[menuState.count] = j;
					menuState.count++;
				}

		}
		for (int j1 = 0; j1 < menuState.count; j1++)
			if (menuState.actionIds[j1] == 14) {
				menuState.actionNames[j1] = "Walk here @whi@" + s;
				return;
			}

	}

	/*
	 * Legacy client.method39(boolean flag) was removed. Its only caller passed true,
	 * while all contained behavior was guarded by !flag, so it was behaviorally inert.
	 */




	/*
	 * Legacy client.method42(int i, int j, Widget class13, byte byte0, int k, int l, int i1, int j1, int k1):
	 *   i -> scrollHeight, j -> y, class13 -> widget, byte0 -> removed 102 sentinel,
	 *   k -> mouseY, l -> redrawArea, i1 -> mouseX, j1 -> height, k1 -> x.
	 */
	public void handleScrollbarInput(int scrollHeight, int y, Widget widget, int mouseY, int redrawArea,
            int mouseX, int height, int x) {
        if (aBoolean1127)
            anInt1303 = 32;
        else
            anInt1303 = 0;
        aBoolean1127 = false;
        if (mouseX >= x && mouseX < x + 16 && mouseY >= y && mouseY < y + 16) {
            widget.scrollY -= anInt1094 * 4;
            if (redrawArea == 1)
                aBoolean1181 = true;
            if (redrawArea == 2 || redrawArea == 3)
                aBoolean1240 = true;
            return;
        }
        if (mouseX >= x && mouseX < x + 16 && mouseY >= (y + height) - 16 && mouseY < y + height) {
            widget.scrollY += anInt1094 * 4;
            if (redrawArea == 1)
                aBoolean1181 = true;
            if (redrawArea == 2 || redrawArea == 3)
                aBoolean1240 = true;
            return;
        }
        if (mouseX >= x - anInt1303 && mouseX < x + 16 + anInt1303 && mouseY >= y + 16
                && mouseY < (y + height) - 16 && anInt1094 > 0) {
            int thumbHeight = ((height - 32) * height) / scrollHeight;
            if (thumbHeight < 8)
                thumbHeight = 8;
            int dragOffset = mouseY - y - 16 - thumbHeight / 2;
            int dragRange = height - 32 - thumbHeight;
            widget.scrollY = ((scrollHeight - height) * dragOffset) / dragRange;
            if (redrawArea == 1)
                aBoolean1181 = true;
            if (redrawArea == 2 || redrawArea == 3)
                aBoolean1240 = true;
            aBoolean1127 = true;
        }
    }

	/* Legacy client.method43(byte byte0): byte0 -> removed fixed 7 sentinel. */
	public void buildViewportMenu() {
		if (interfaceState.itemSelected == 0 && interfaceState.spellSelected == 0) {
			menuState.actionNames[menuState.count] = "Walk here";
			menuState.actionIds[menuState.count] = 14;
			menuState.actionCmd2[menuState.count] = super.mouseX;
			menuState.actionCmd3[menuState.count] = super.mouseY;
			menuState.count++;
		}
		int i = -1;
		for (int j = 0; j < Model.pickedCount; j++) {
			int k = Model.pickedUids[j];
			int l = k & 0x7f;
			int i1 = k >> 7 & 0x7f;
			int j1 = k >> 29 & 3;
			int k1 = k >> 14 & 0x7fff;
			if (k == i)
				continue;
			i = k;
			if (j1 == 2 && worldState.scene.getConfig(currentPlane, l, i1, k) >= 0) {
				GameObjectDefinition class47 = GameObjectDefinition.lookup(k1);
				if (class47.morphIds != null)
					class47 = class47.transform();
				if (class47 == null)
					continue;
				if (interfaceState.itemSelected == 1) {
					menuState.actionNames[menuState.count] = "Use " + interfaceState.selectedItemName + " with @cya@" + class47.name;
					menuState.actionIds[menuState.count] = 467;
					menuState.actionCmd1[menuState.count] = k;
					menuState.actionCmd2[menuState.count] = l;
					menuState.actionCmd3[menuState.count] = i1;
					menuState.count++;
				} else if (interfaceState.spellSelected == 1) {
					if ((interfaceState.selectedSpellTargetMask & 4) == 4) {
						menuState.actionNames[menuState.count] = interfaceState.selectedSpellAction + " @cya@" + class47.name;
						menuState.actionIds[menuState.count] = 376;
						menuState.actionCmd1[menuState.count] = k;
						menuState.actionCmd2[menuState.count] = l;
						menuState.actionCmd3[menuState.count] = i1;
						menuState.count++;
					}
				} else {
					if (class47.actions != null) {
						for (int l1 = 4; l1 >= 0; l1--)
							if (class47.actions[l1] != null) {
								menuState.actionNames[menuState.count] = class47.actions[l1] + " @cya@" + class47.name;
								if (l1 == 0)
									menuState.actionIds[menuState.count] = 35;
								if (l1 == 1)
									menuState.actionIds[menuState.count] = 389;
								if (l1 == 2)
									menuState.actionIds[menuState.count] = 888;
								if (l1 == 3)
									menuState.actionIds[menuState.count] = 892;
								if (l1 == 4)
									menuState.actionIds[menuState.count] = 1280;
								menuState.actionCmd1[menuState.count] = k;
								menuState.actionCmd2[menuState.count] = l;
								menuState.actionCmd3[menuState.count] = i1;
								menuState.count++;
							}

					}
					menuState.actionNames[menuState.count] = "Examine @cya@" + class47.name;
					menuState.actionIds[menuState.count] = 1412;
					menuState.actionCmd1[menuState.count] = class47.id << 14;
					menuState.actionCmd2[menuState.count] = l;
					menuState.actionCmd3[menuState.count] = i1;
					menuState.count++;
				}
			}
			if (j1 == 1) {
				Npc class50_sub1_sub4_sub3_sub1 = actorSynchronizer.npcs[k1];
				if (class50_sub1_sub4_sub3_sub1.definition.size == 1
						&& (((Actor) (class50_sub1_sub4_sub3_sub1)).x & 0x7f) == 64
						&& (((Actor) (class50_sub1_sub4_sub3_sub1)).y & 0x7f) == 64) {
					for (int i2 = 0; i2 < actorSynchronizer.npcCount; i2++) {
						Npc class50_sub1_sub4_sub3_sub1_1 = actorSynchronizer.npcs[actorSynchronizer.npcIndices[i2]];
						if (class50_sub1_sub4_sub3_sub1_1 != null
								&& class50_sub1_sub4_sub3_sub1_1 != class50_sub1_sub4_sub3_sub1
								&& class50_sub1_sub4_sub3_sub1_1.definition.size == 1
								&& ((Actor) (class50_sub1_sub4_sub3_sub1_1)).x == ((Actor) (class50_sub1_sub4_sub3_sub1)).x
								&& ((Actor) (class50_sub1_sub4_sub3_sub1_1)).y == ((Actor) (class50_sub1_sub4_sub3_sub1)).y)
							buildNpcMenu(class50_sub1_sub4_sub3_sub1_1.definition, i1, l, actorSynchronizer.npcIndices[i2]);
					}

					for (int k2 = 0; k2 < actorSynchronizer.playerCount; k2++) {
						Player class50_sub1_sub4_sub3_sub2_1 = actorSynchronizer.players[actorSynchronizer.playerIndices[k2]];
						if (class50_sub1_sub4_sub3_sub2_1 != null
								&& ((Actor) (class50_sub1_sub4_sub3_sub2_1)).x == ((Actor) (class50_sub1_sub4_sub3_sub1)).x
								&& ((Actor) (class50_sub1_sub4_sub3_sub2_1)).y == ((Actor) (class50_sub1_sub4_sub3_sub1)).y)
							buildPlayerMenu(actorSynchronizer.playerIndices[k2], i1, l, class50_sub1_sub4_sub3_sub2_1);
					}

				}
				buildNpcMenu(class50_sub1_sub4_sub3_sub1.definition, i1, l, k1);
			}
			if (j1 == 0) {
				Player class50_sub1_sub4_sub3_sub2 = actorSynchronizer.players[k1];
				if ((((Actor) (class50_sub1_sub4_sub3_sub2)).x & 0x7f) == 64
						&& (((Actor) (class50_sub1_sub4_sub3_sub2)).y & 0x7f) == 64) {
					for (int j2 = 0; j2 < actorSynchronizer.npcCount; j2++) {
						Npc class50_sub1_sub4_sub3_sub1_2 = actorSynchronizer.npcs[actorSynchronizer.npcIndices[j2]];
						if (class50_sub1_sub4_sub3_sub1_2 != null && class50_sub1_sub4_sub3_sub1_2.definition.size == 1
								&& ((Actor) (class50_sub1_sub4_sub3_sub1_2)).x == ((Actor) (class50_sub1_sub4_sub3_sub2)).x
								&& ((Actor) (class50_sub1_sub4_sub3_sub1_2)).y == ((Actor) (class50_sub1_sub4_sub3_sub2)).y)
							buildNpcMenu(class50_sub1_sub4_sub3_sub1_2.definition, i1, l, actorSynchronizer.npcIndices[j2]);
					}

					for (int l2 = 0; l2 < actorSynchronizer.playerCount; l2++) {
						Player class50_sub1_sub4_sub3_sub2_2 = actorSynchronizer.players[actorSynchronizer.playerIndices[l2]];
						if (class50_sub1_sub4_sub3_sub2_2 != null
								&& class50_sub1_sub4_sub3_sub2_2 != class50_sub1_sub4_sub3_sub2
								&& ((Actor) (class50_sub1_sub4_sub3_sub2_2)).x == ((Actor) (class50_sub1_sub4_sub3_sub2)).x
								&& ((Actor) (class50_sub1_sub4_sub3_sub2_2)).y == ((Actor) (class50_sub1_sub4_sub3_sub2)).y)
							buildPlayerMenu(actorSynchronizer.playerIndices[l2], i1, l, class50_sub1_sub4_sub3_sub2_2);
					}

				}
				buildPlayerMenu(k1, i1, l, class50_sub1_sub4_sub3_sub2);
			}
			if (j1 == 3) {
				NodeDeque class6 = worldState.groundItems[currentPlane][l][i1];
				if (class6 != null) {
					for (GroundItem class50_sub1_sub4_sub1 = (GroundItem) class6
							.last(); class50_sub1_sub4_sub1 != null; class50_sub1_sub4_sub1 = (GroundItem) class6
									.previous()) {
						ItemDefinition class16 = ItemDefinition.lookup(class50_sub1_sub4_sub1.id);
						if (interfaceState.itemSelected == 1) {
							menuState.actionNames[menuState.count] = "Use " + interfaceState.selectedItemName + " with @lre@" + class16.name;
							menuState.actionIds[menuState.count] = 100;
							menuState.actionCmd1[menuState.count] = class50_sub1_sub4_sub1.id;
							menuState.actionCmd2[menuState.count] = l;
							menuState.actionCmd3[menuState.count] = i1;
							menuState.count++;
						} else if (interfaceState.spellSelected == 1) {
							if ((interfaceState.selectedSpellTargetMask & 1) == 1) {
								menuState.actionNames[menuState.count] = interfaceState.selectedSpellAction + " @lre@" + class16.name;
								menuState.actionIds[menuState.count] = 199;
								menuState.actionCmd1[menuState.count] = class50_sub1_sub4_sub1.id;
								menuState.actionCmd2[menuState.count] = l;
								menuState.actionCmd3[menuState.count] = i1;
								menuState.count++;
							}
						} else {
							for (int i3 = 4; i3 >= 0; i3--)
								if (class16.groundActions != null && class16.groundActions[i3] != null) {
									menuState.actionNames[menuState.count] = class16.groundActions[i3] + " @lre@" + class16.name;
									if (i3 == 0)
										menuState.actionIds[menuState.count] = 68;
									if (i3 == 1)
										menuState.actionIds[menuState.count] = 26;
									if (i3 == 2)
										menuState.actionIds[menuState.count] = 684;
									if (i3 == 3)
										menuState.actionIds[menuState.count] = 930;
									if (i3 == 4)
										menuState.actionIds[menuState.count] = 270;
									menuState.actionCmd1[menuState.count] = class50_sub1_sub4_sub1.id;
									menuState.actionCmd2[menuState.count] = l;
									menuState.actionCmd3[menuState.count] = i1;
									menuState.count++;
								} else if (i3 == 2) {
									menuState.actionNames[menuState.count] = "Take @lre@" + class16.name;
									menuState.actionIds[menuState.count] = 684;
									menuState.actionCmd1[menuState.count] = class50_sub1_sub4_sub1.id;
									menuState.actionCmd2[menuState.count] = l;
									menuState.actionCmd3[menuState.count] = i1;
									menuState.count++;
								}

							menuState.actionNames[menuState.count] = "Examine @lre@" + class16.name;
							menuState.actionIds[menuState.count] = 1564;
							menuState.actionCmd1[menuState.count] = class50_sub1_sub4_sub1.id;
							menuState.actionCmd2[menuState.count] = l;
							menuState.actionCmd3[menuState.count] = i1;
							menuState.count++;
						}
					}

				}
			}
		}

	}

	/* Legacy client.method44(int i): i -> interfaceId. */
	public void unloadInterface(int i) {
		Widget.unloadGroup(i);
	}

	/*
	 * Legacy client.method47(String s, String s1, int i):
	 *   s  -> sender
	 *   s1 -> message
	 *   i  -> type
	 */
	public void addChatMessage(String sender, String message, int type) {
		if (type == 0 && interfaceState.dialogueInterfaceId != -1) {
			aString1058 = message;
			super.clickButton = 0;
		}
		if (interfaceState.chatboxInterfaceId == -1)
			aBoolean1240 = true;
		chatHistory.add(sender, message, type);
	}



	public void method49(int i) {
		GameObjectDefinition.clearModelCaches();
		if (i <= 0) {
			for (int j = 1; j > 0; j++)
				;
		}
		NpcDefinition.modelCache.clear();
		ItemDefinition.modelCache.clear();
		ItemSpriteFactory.clearCache();
		Player.modelCache.clear();
		SpotAnimation.modelCache.clear();
	}

	/*
	 * Legacy client.method52(boolean flag): flag -> removed false sentinel.
	 * Initializes title sprites, flame palettes/noise and the flame thread.
	 */
	public void initializeTitleScreen() {
		titleBoxImage = new IndexedImage(titleArchive, "titlebox", 0);
		titleButtonImage = new IndexedImage(titleArchive, "titlebutton", 0);
		titleRunes = new IndexedImage[12];
		for (int i = 0; i < 12; i++)
			titleRunes[i] = new IndexedImage(titleArchive, "runes", i);

		titleLeftFlameBackground = new ImageRGB(128, 265);
		titleRightFlameBackground = new ImageRGB(128, 265);
		for (int j = 0; j < 33920; j++)
			titleLeftFlameBackground.pixels[j] = titleLeftFlameBuffer.pixels[j];

		for (int k = 0; k < 33920; k++)
			titleRightFlameBackground.pixels[k] = titleRightFlameBuffer.pixels[k];

		titleFlameRedPalette = new int[256];
		for (int l = 0; l < 64; l++)
			titleFlameRedPalette[l] = l * 0x40000;

		for (int i1 = 0; i1 < 64; i1++)
			titleFlameRedPalette[i1 + 64] = 0xff0000 + 1024 * i1;

		for (int j1 = 0; j1 < 64; j1++)
			titleFlameRedPalette[j1 + 128] = 0xffff00 + 4 * j1;

		for (int k1 = 0; k1 < 64; k1++)
			titleFlameRedPalette[k1 + 192] = 0xffffff;

		titleFlameGreenPalette = new int[256];
		for (int l1 = 0; l1 < 64; l1++)
			titleFlameGreenPalette[l1] = l1 * 1024;

		for (int i2 = 0; i2 < 64; i2++)
			titleFlameGreenPalette[i2 + 64] = 65280 + 4 * i2;

		for (int j2 = 0; j2 < 64; j2++)
			titleFlameGreenPalette[j2 + 128] = 65535 + 0x40000 * j2;

		for (int k2 = 0; k2 < 64; k2++)
			titleFlameGreenPalette[k2 + 192] = 0xffffff;

		titleFlameBluePalette = new int[256];
		for (int l2 = 0; l2 < 64; l2++)
			titleFlameBluePalette[l2] = l2 * 4;

		for (int i3 = 0; i3 < 64; i3++)
			titleFlameBluePalette[i3 + 64] = 255 + 0x40000 * i3;

		for (int j3 = 0; j3 < 64; j3++)
			titleFlameBluePalette[j3 + 128] = 0xff00ff + 1024 * j3;

		for (int k3 = 0; k3 < 64; k3++)
			titleFlameBluePalette[k3 + 192] = 0xffffff;

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
			startThread(this, 2);
		}
	}

	/*
	 * Legacy client.method53(long l, int i):
	 *   l -> encodedName
	 *   i -> removed zero sentinel; the original only added it to incomingLength.
	 */
	public void removeFriend(long encodedName) {
		if (socialManager.removeFriend(encodedName, networkSession.outgoing))
			aBoolean1181 = true;
	}


	/* Legacy client.method54(int i): i -> removed fixed 0 sentinel. */
	public void processMenuClick() {
		if (interfaceState.inventoryDragArea != 0)
			return;
		int j = super.clickButton;
		if (interfaceState.spellSelected == 1 && super.clickX >= 516 && super.clickY >= 160 && super.clickX <= 765 && super.clickY <= 205)
			j = 0;
		if (menuState.open) {
			if (j != 1) {
				int k = super.mouseX;
				int j1 = super.mouseY;
				if (menuState.screenArea == 0) {
					k -= 4;
					j1 -= 4;
				}
				if (menuState.screenArea == 1) {
					k -= 553;
					j1 -= 205;
				}
				if (menuState.screenArea == 2) {
					k -= 17;
					j1 -= 357;
				}
				if (k < menuState.offsetX - 10 || k > menuState.offsetX + menuState.width + 10 || j1 < menuState.offsetY - 10
						|| j1 > menuState.offsetY + menuState.height + 10) {
								if (menuState.screenArea == 1)
						aBoolean1181 = true;
					if (menuState.screenArea == 2)
						aBoolean1240 = true;
				}
			}
			if (j == 1) {
				int l = menuState.offsetX;
				int k1 = menuState.offsetY;
				int i2 = menuState.width;
				int k2 = super.clickX;
				int l2 = super.clickY;
				if (menuState.screenArea == 0) {
					k2 -= 4;
					l2 -= 4;
				}
				if (menuState.screenArea == 1) {
					k2 -= 553;
					l2 -= 205;
				}
				if (menuState.screenArea == 2) {
					k2 -= 17;
					l2 -= 357;
				}
				int i3 = -1;
				for (int j3 = 0; j3 < menuState.count; j3++) {
					int k3 = k1 + 31 + (menuState.count - 1 - j3) * 15;
					if (k2 > l && k2 < l + i2 && l2 > k3 - 13 && l2 < k3 + 3)
						i3 = j3;
				}

				if (i3 != -1)
					dispatchMenuAction(i3);
						if (menuState.screenArea == 1)
					aBoolean1181 = true;
				if (menuState.screenArea == 2) {
					aBoolean1240 = true;
					return;
				}
			}
		} else {
			if (j == 1 && menuState.count > 0) {
				int i1 = menuState.actionIds[menuState.count - 1];
				if (i1 == 9 || i1 == 225 || i1 == 444 || i1 == 564 || i1 == 894 || i1 == 961 || i1 == 399 || i1 == 324
						|| i1 == 227 || i1 == 891 || i1 == 52 || i1 == 1094) {
					int l1 = menuState.actionCmd2[menuState.count - 1];
					int j2 = menuState.actionCmd3[menuState.count - 1];
					Widget class13 = Widget.get(j2);
					if (class13.inventoryAllowSwap || class13.inventoryReplaceItems) {
						aBoolean1155 = false;
						interfaceState.inventoryDragDuration = 0;
						interfaceState.draggedInventoryWidgetId = j2;
						interfaceState.draggedInventorySlot = l1;
						interfaceState.inventoryDragArea = 2;
						interfaceState.inventoryDragStartX = super.clickX;
						interfaceState.inventoryDragStartY = super.clickY;
						if (Widget.get(j2).parentId == interfaceState.openInterfaceId)
							interfaceState.inventoryDragArea = 1;
						if (Widget.get(j2).parentId == interfaceState.chatboxInterfaceId)
							interfaceState.inventoryDragArea = 3;
						return;
					}
				}
			}
			if (j == 1 && (oneButtonMouseMode == 1 || isAddFriendMenuAction(menuState.count - 1)) && menuState.count > 2)
				j = 2;
			if (j == 1 && menuState.count > 0)
				dispatchMenuAction(menuState.count - 1);
			if (j == 2 && menuState.count > 0)
				openContextMenu();
		}
	}

	// Legacy method55 moved into MinimapRenderer.drawHint.


	/*
	 * Legacy client.method56(boolean flag, int i, int j, int k, int l, int i1):
	 *   flag -> removed always-true sentinel, i -> scrollY, j -> x, k -> height,
	 *   l -> scrollHeight, i1 -> y.
	 */
	public void drawScrollbar(int scrollY, int x, int height, int scrollHeight, int y) {
        aClass50_Sub1_Sub1_Sub3_1095.draw(x, y);
        aClass50_Sub1_Sub1_Sub3_1096.draw(x, (y + height) - 16);
        Rasterizer.drawFilledRectangle(x, y + 16, 16, height - 32, anInt931);
        int thumbHeight = ((height - 32) * height) / scrollHeight;
        if (thumbHeight < 8)
            thumbHeight = 8;
        int thumbY = ((height - 32 - thumbHeight) * scrollY) / (scrollHeight - height);
        Rasterizer.drawFilledRectangle(x, y + 16 + thumbY, 16, thumbHeight, anInt1080);
        Rasterizer.drawVerticalLine(x, y + 16 + thumbY, thumbHeight, anInt1135);
        Rasterizer.drawVerticalLine(x + 1, y + 16 + thumbY, thumbHeight, anInt1135);
        Rasterizer.drawHorizontalLine(x, y + 16 + thumbY, 16, anInt1135);
        Rasterizer.drawHorizontalLine(x, y + 17 + thumbY, 16, anInt1135);
        Rasterizer.drawVerticalLine(x + 15, y + 16 + thumbY, thumbHeight, anInt1287);
        Rasterizer.drawVerticalLine(x + 14, y + 17 + thumbY, thumbHeight - 1, anInt1287);
        Rasterizer.drawHorizontalLine(x, y + 15 + thumbY + thumbHeight, 16, anInt1287);
        Rasterizer.drawHorizontalLine(x + 1, y + 14 + thumbY + thumbHeight, 15, anInt1287);
    }

	/* Legacy client.method57(int i, boolean flag): i -> removed 751 sentinel; flag -> priorityRender. */
	private void addNpcsToScene(boolean priorityRender) {
		sceneEntityRenderer.addNpcs(worldState, actorSynchronizer, currentPlane, priorityRender);
	}


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
		} catch (Exception _ex) {
			return;
		}
	}

	/* Legacy client.method60(int i, Widget class13): i -> removed positive sentinel; class13 -> widget. */
	public boolean handleWidgetContentAction(Widget widget) {
		int j = widget.contentType;
		if (socialManager.friendListStatus == 2) {
			if (j == 201) {
				aBoolean1240 = true;
				anInt1244 = 0;
				messagePromptRaised = true;
				promptInput = "";
				promptAction = 1;
				promptMessage = "Enter name of friend to add to list";
			}
			if (j == 202) {
				aBoolean1240 = true;
				anInt1244 = 0;
				messagePromptRaised = true;
				promptInput = "";
				promptAction = 2;
				promptMessage = "Enter name of friend to delete from list";
			}
		}
		if (j == 205) {
			logoutTimer = 250;
			return true;
		}
		if (j == 501) {
			aBoolean1240 = true;
			anInt1244 = 0;
			messagePromptRaised = true;
			promptInput = "";
			promptAction = 4;
			promptMessage = "Enter name of player to add to list";
		}
		if (j == 502) {
			aBoolean1240 = true;
			anInt1244 = 0;
			messagePromptRaised = true;
			promptInput = "";
			promptAction = 5;
			promptMessage = "Enter name of player to delete from list";
		}
		if (j >= 300 && j <= 313) {
			int k = (j - 300) / 2;
			int j1 = j & 1;
			int i2 = anIntArray1326[k];
			if (i2 != -1) {
				do {
					if (j1 == 0 && --i2 < 0)
						i2 = IdentityKit.count - 1;
					if (j1 == 1 && ++i2 >= IdentityKit.count)
						i2 = 0;
				} while (IdentityKit.definitions[i2].nonSelectable
						|| IdentityKit.definitions[i2].bodyPartId != k + (aBoolean1144 ? 0 : 7));
				anIntArray1326[k] = i2;
				aBoolean1277 = true;
			}
		}
		if (j >= 314 && j <= 323) {
			int l = (j - 314) / 2;
			int k1 = j & 1;
			int j2 = anIntArray1099[l];
			if (k1 == 0 && --j2 < 0)
				j2 = anIntArrayArray1008[l].length - 1;
			if (k1 == 1 && ++j2 >= anIntArrayArray1008[l].length)
				j2 = 0;
			anIntArray1099[l] = j2;
			aBoolean1277 = true;
		}
		if (j == 324 && !aBoolean1144) {
			aBoolean1144 = true;
			method25(anInt1015);
		}
		if (j == 325 && aBoolean1144) {
			aBoolean1144 = false;
			method25(anInt1015);
		}
		if (j == 326) {
			networkSession.outgoing.writeOpcode(163);
			networkSession.outgoing.writeByte(aBoolean1144 ? 0 : 1);
			for (int i1 = 0; i1 < 7; i1++)
				networkSession.outgoing.writeByte(anIntArray1326[i1]);

			for (int l1 = 0; l1 < 5; l1++)
				networkSession.outgoing.writeByte(anIntArray1099[l1]);

			return true;
		}
		if (j == 620)
			reportAbuseMutePlayer = !reportAbuseMutePlayer;
		if (j >= 601 && j <= 613) {
			method15(false);
			if (reportAbuseName.length() > 0) {
				networkSession.outgoing.writeOpcode(184);
				networkSession.outgoing.writeLong(Base37.encode(reportAbuseName));
				networkSession.outgoing.writeByte(j - 601);
				networkSession.outgoing.writeByte(reportAbuseMutePlayer ? 1 : 0);
			}
		}
		return false;
	}

	/*
	 * Legacy client.method61(int i, int j, String s, int k, int l, String s1):
	 *   i -> removed fixed 14076 sentinel, j -> expectedCrc, s -> archiveName,
	 *   k -> loadingPercent, l -> cacheFileId, s1 -> displayName.
	 */
	private Archive loadArchive(int expectedCrc, String archiveName, int loadingPercent, int cacheFileId, String displayName) {
		return resourceLoader.loadArchive(expectedCrc, archiveName, loadingPercent, cacheFileId, displayName,
				this::openJaggrabStream, this::drawLoadingText);
	}

	public void method10(byte byte0) {
		aBoolean1046 = true;
		if (byte0 == -99)
			;
	}



	/* Legacy client.method64(int i): i -> removed negative sentinel. */
	public void createTitleScreenBuffers() {
		if (titleTopBuffer != null)
			return;
		super.gameBuffer = null;
		chatboxBuffer = null;
		minimapBuffer = null;
		sidebarBuffer = null;
		viewportBuffer = null;
		aClass18_1108 = null;
		aClass18_1109 = null;
		aClass18_1110 = null;

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
		aBoolean1046 = true;
	}

	public void startUp() {
		drawLoadingText(20, "Starting up");
		if (startupStarted) {
			duplicateClientError = true;
			return;
		}
		startupStarted = true;
		boolean flag = false;
		String s = getConfiguredHost();
		if (s.endsWith("jagex.com"))
			flag = true;
		if (s.endsWith("runescape.com"))
			flag = true;
		if (s.endsWith("192.168.1.2"))
			flag = true;
		if (s.endsWith("192.168.1.231"))
			flag = true;
		if (s.endsWith("192.168.1.229"))
			flag = true;
		if (s.endsWith("192.168.1.228"))
			flag = true;
		if (s.endsWith("192.168.1.227"))
			flag = true;
		if (s.endsWith("192.168.1.226"))
			flag = true;
		if (s.endsWith("192.168.1.224"))
			flag = true;
		if (s.endsWith("192.168.1.223"))
			flag = true;
		if (s.endsWith("192.168.1.221"))
			flag = true;
		if (s.endsWith("127.0.0.1"))
			flag = true;
		if (!flag) {
			invalidHostError = true;
			return;
		}
		if (Signlink.cacheData != null) {
			resourceLoader.initializeCacheIndices(Signlink.cacheData, Signlink.cacheIndexes);

		}
		try {
//			loadArchiveCrcs(); TODO debug - intentionally disabled in supplied source
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
			Archive versionListArchive = loadArchive(resourceLoader.getArchiveCrc(5), "versionlist", 60, 5, "update list");
			drawLoadingText(60, "Connecting to update server");
			onDemandFetcher = new OnDemandFetcher();
			onDemandFetcher.start(versionListArchive, this, resourceLoader);
			AnimationFrame.initialize(onDemandFetcher.getAnimationCount());
			Model.initializeModelHeaders(onDemandFetcher.getFileCount(0), onDemandFetcher);
			if (!lowMemory) {
				musicController.requestStartupTrack(onDemandFetcher::request, lowMemory);
				while (onDemandFetcher.getOutstandingRequestCount() > 0) {
					method77(false);
					try {
						Thread.sleep(100L);
					} catch (Exception _ex) {
					}
					if (onDemandFetcher.requestFailures > 3) {
						haltOnLoadError("ondemand");
						return;
					}
				}
			}
			drawLoadingText(65, "Requesting animations");
			int k = onDemandFetcher.getFileCount(1);
			for (int l = 0; l < k; l++)
				onDemandFetcher.request(1, l);

			while (onDemandFetcher.getOutstandingRequestCount() > 0) {
				int i1 = k - onDemandFetcher.getOutstandingRequestCount();
				if (i1 > 0)
					drawLoadingText(65, "Loading animations - " + (i1 * 100) / k + "%");
				method77(false);
				try {
					Thread.sleep(100L);
				} catch (Exception _ex) {
				}
				if (onDemandFetcher.requestFailures > 3) {
					haltOnLoadError("ondemand");
					return;
				}
			}
			drawLoadingText(70, "Requesting models");
			k = onDemandFetcher.getFileCount(0);
			for (int j1 = 0; j1 < k; j1++) {
				int k1 = onDemandFetcher.getModelIndex(j1);
				if ((k1 & 1) != 0)
					onDemandFetcher.request(0, j1);
			}

			k = onDemandFetcher.getOutstandingRequestCount();
			while (onDemandFetcher.getOutstandingRequestCount() > 0) {
				int l1 = k - onDemandFetcher.getOutstandingRequestCount();
				if (l1 > 0)
					drawLoadingText(70, "Loading models - " + (l1 * 100) / k + "%");
				method77(false);
				try {
					Thread.sleep(100L);
				} catch (Exception _ex) {
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
				k = onDemandFetcher.getOutstandingRequestCount();
				while (onDemandFetcher.getOutstandingRequestCount() > 0) {
					int i2 = k - onDemandFetcher.getOutstandingRequestCount();
					if (i2 > 0)
						drawLoadingText(75, "Loading maps - " + (i2 * 100) / k + "%");
					method77(false);
					try {
						Thread.sleep(100L);
					} catch (Exception _ex) {
					}
				}
			}
			k = onDemandFetcher.getFileCount(0);
			for (int j2 = 0; j2 < k; j2++) {
				int k2 = onDemandFetcher.getModelIndex(j2);
				byte byte0 = 0;
				if ((k2 & 8) != 0)
					byte0 = 10;
				else if ((k2 & 0x20) != 0)
					byte0 = 9;
				else if ((k2 & 0x10) != 0)
					byte0 = 8;
				else if ((k2 & 0x40) != 0)
					byte0 = 7;
				else if ((k2 & 0x80) != 0)
					byte0 = 6;
				else if ((k2 & 2) != 0)
					byte0 = 5;
				else if ((k2 & 4) != 0)
					byte0 = 4;
				if ((k2 & 1) != 0)
					byte0 = 3;
				if (byte0 != 0)
					onDemandFetcher.setExtraPriority(0, j2, byte0);
			}

			onDemandFetcher.preloadMaps(membersWorld);
			if (!lowMemory) {
				k = onDemandFetcher.getFileCount(2);
				for (int l2 = 1; l2 < k; l2++)
					if (onDemandFetcher.isMidiPreload(l2))
						onDemandFetcher.setExtraPriority(2, l2, (byte) 1);

			}
			k = onDemandFetcher.getFileCount(0);
			for (int i3 = 0; i3 < k; i3++) {
				int j3 = onDemandFetcher.getModelIndex(i3);
				if (j3 == 0 && onDemandFetcher.totalFiles < 200)
					onDemandFetcher.setExtraPriority(0, i3, (byte) 1);
			}

			drawLoadingText(80, "Unpacking media");
			aClass50_Sub1_Sub1_Sub3_1185 = new IndexedImage(mediaArchive, "invback", 0);
			aClass50_Sub1_Sub1_Sub3_1187 = new IndexedImage(mediaArchive, "chatback", 0);
			aClass50_Sub1_Sub1_Sub3_1186 = new IndexedImage(mediaArchive, "mapback", 0);
			aClass50_Sub1_Sub1_Sub3_965 = new IndexedImage(mediaArchive, "backbase1", 0);
			aClass50_Sub1_Sub1_Sub3_966 = new IndexedImage(mediaArchive, "backbase2", 0);
			aClass50_Sub1_Sub1_Sub3_967 = new IndexedImage(mediaArchive, "backhmid1", 0);
			for (int k3 = 0; k3 < 13; k3++)
				aClass50_Sub1_Sub1_Sub3Array976[k3] = new IndexedImage(mediaArchive, "sideicons", k3);

			aClass50_Sub1_Sub1_Sub1_1116 = new ImageRGB(mediaArchive, "compass", 0);
			aClass50_Sub1_Sub1_Sub1_1247 = new ImageRGB(mediaArchive, "mapedge", 0);
			aClass50_Sub1_Sub1_Sub1_1247.trim();
			for (int l3 = 0; l3 < 72; l3++)
				aClass50_Sub1_Sub1_Sub3Array1153[l3] = new IndexedImage(mediaArchive, "mapscene", l3);

			for (int i4 = 0; i4 < 70; i4++)
				aClass50_Sub1_Sub1_Sub1Array1031[i4] = new ImageRGB(mediaArchive, "mapfunction", i4);

			for (int j4 = 0; j4 < 5; j4++)
				aClass50_Sub1_Sub1_Sub1Array1182[j4] = new ImageRGB(mediaArchive, "hitmarks", j4);

			for (int k4 = 0; k4 < 6; k4++)
				aClass50_Sub1_Sub1_Sub1Array1288[k4] = new ImageRGB(mediaArchive, "headicons_pk", k4);

			for (int l4 = 0; l4 < 9; l4++)
				aClass50_Sub1_Sub1_Sub1Array1079[l4] = new ImageRGB(mediaArchive, "headicons_prayer", l4);

			for (int i5 = 0; i5 < 6; i5++)
				aClass50_Sub1_Sub1_Sub1Array954[i5] = new ImageRGB(mediaArchive, "headicons_hint", i5);

			aClass50_Sub1_Sub1_Sub1_1086 = new ImageRGB(mediaArchive, "overlay_multiway", 0);
			aClass50_Sub1_Sub1_Sub1_1036 = new ImageRGB(mediaArchive, "mapmarker", 0);
			aClass50_Sub1_Sub1_Sub1_1037 = new ImageRGB(mediaArchive, "mapmarker", 1);
			for (int j5 = 0; j5 < 8; j5++)
				aClass50_Sub1_Sub1_Sub1Array896[j5] = new ImageRGB(mediaArchive, "cross", j5);

			aClass50_Sub1_Sub1_Sub1_1192 = new ImageRGB(mediaArchive, "mapdots", 0);
			aClass50_Sub1_Sub1_Sub1_1193 = new ImageRGB(mediaArchive, "mapdots", 1);
			aClass50_Sub1_Sub1_Sub1_1194 = new ImageRGB(mediaArchive, "mapdots", 2);
			aClass50_Sub1_Sub1_Sub1_1195 = new ImageRGB(mediaArchive, "mapdots", 3);
			aClass50_Sub1_Sub1_Sub1_1196 = new ImageRGB(mediaArchive, "mapdots", 4);
			aClass50_Sub1_Sub1_Sub3_1095 = new IndexedImage(mediaArchive, "scrollbar", 0);
			aClass50_Sub1_Sub1_Sub3_1096 = new IndexedImage(mediaArchive, "scrollbar", 1);
			aClass50_Sub1_Sub1_Sub3_880 = new IndexedImage(mediaArchive, "redstone1", 0);
			aClass50_Sub1_Sub1_Sub3_881 = new IndexedImage(mediaArchive, "redstone2", 0);
			aClass50_Sub1_Sub1_Sub3_882 = new IndexedImage(mediaArchive, "redstone3", 0);
			aClass50_Sub1_Sub1_Sub3_883 = new IndexedImage(mediaArchive, "redstone1", 0);
			aClass50_Sub1_Sub1_Sub3_883.flipHorizontal();
			aClass50_Sub1_Sub1_Sub3_884 = new IndexedImage(mediaArchive, "redstone2", 0);
			aClass50_Sub1_Sub1_Sub3_884.flipHorizontal();
			aClass50_Sub1_Sub1_Sub3_983 = new IndexedImage(mediaArchive, "redstone1", 0);
			aClass50_Sub1_Sub1_Sub3_983.flipVertical();
			aClass50_Sub1_Sub1_Sub3_984 = new IndexedImage(mediaArchive, "redstone2", 0);
			aClass50_Sub1_Sub1_Sub3_984.flipVertical();
			aClass50_Sub1_Sub1_Sub3_985 = new IndexedImage(mediaArchive, "redstone3", 0);
			aClass50_Sub1_Sub1_Sub3_985.flipVertical();
			aClass50_Sub1_Sub1_Sub3_986 = new IndexedImage(mediaArchive, "redstone1", 0);
			aClass50_Sub1_Sub1_Sub3_986.flipHorizontal();
			aClass50_Sub1_Sub1_Sub3_986.flipVertical();
			aClass50_Sub1_Sub1_Sub3_987 = new IndexedImage(mediaArchive, "redstone2", 0);
			aClass50_Sub1_Sub1_Sub3_987.flipHorizontal();
			aClass50_Sub1_Sub1_Sub3_987.flipVertical();
			for (int k5 = 0; k5 < 2; k5++)
				aClass50_Sub1_Sub1_Sub3Array1142[k5] = new IndexedImage(mediaArchive, "mod_icons", k5);

			ImageRGB class50_sub1_sub1_sub1 = new ImageRGB(mediaArchive, "backleft1", 0);
			aClass18_906 = new GraphicsBuffer(getGameComponent(), class50_sub1_sub1_sub1.width,
					class50_sub1_sub1_sub1.height);
			class50_sub1_sub1_sub1.drawInverse(0, 0);
			class50_sub1_sub1_sub1 = new ImageRGB(mediaArchive, "backleft2", 0);
			aClass18_907 = new GraphicsBuffer(getGameComponent(), class50_sub1_sub1_sub1.width,
					class50_sub1_sub1_sub1.height);
			class50_sub1_sub1_sub1.drawInverse(0, 0);
			class50_sub1_sub1_sub1 = new ImageRGB(mediaArchive, "backright1", 0);
			aClass18_908 = new GraphicsBuffer(getGameComponent(), class50_sub1_sub1_sub1.width,
					class50_sub1_sub1_sub1.height);
			class50_sub1_sub1_sub1.drawInverse(0, 0);
			class50_sub1_sub1_sub1 = new ImageRGB(mediaArchive, "backright2", 0);
			aClass18_909 = new GraphicsBuffer(getGameComponent(), class50_sub1_sub1_sub1.width,
					class50_sub1_sub1_sub1.height);
			class50_sub1_sub1_sub1.drawInverse(0, 0);
			class50_sub1_sub1_sub1 = new ImageRGB(mediaArchive, "backtop1", 0);
			aClass18_910 = new GraphicsBuffer(getGameComponent(), class50_sub1_sub1_sub1.width,
					class50_sub1_sub1_sub1.height);
			class50_sub1_sub1_sub1.drawInverse(0, 0);
			class50_sub1_sub1_sub1 = new ImageRGB(mediaArchive, "backvmid1", 0);
			aClass18_911 = new GraphicsBuffer(getGameComponent(), class50_sub1_sub1_sub1.width,
					class50_sub1_sub1_sub1.height);
			class50_sub1_sub1_sub1.drawInverse(0, 0);
			class50_sub1_sub1_sub1 = new ImageRGB(mediaArchive, "backvmid2", 0);
			aClass18_912 = new GraphicsBuffer(getGameComponent(), class50_sub1_sub1_sub1.width,
					class50_sub1_sub1_sub1.height);
			class50_sub1_sub1_sub1.drawInverse(0, 0);
			class50_sub1_sub1_sub1 = new ImageRGB(mediaArchive, "backvmid3", 0);
			aClass18_913 = new GraphicsBuffer(getGameComponent(), class50_sub1_sub1_sub1.width,
					class50_sub1_sub1_sub1.height);
			class50_sub1_sub1_sub1.drawInverse(0, 0);
			class50_sub1_sub1_sub1 = new ImageRGB(mediaArchive, "backhmid2", 0);
			aClass18_914 = new GraphicsBuffer(getGameComponent(), class50_sub1_sub1_sub1.width,
					class50_sub1_sub1_sub1.height);
			class50_sub1_sub1_sub1.drawInverse(0, 0);
			int l5 = (int) (Math.random() * 21D) - 10;
			int i6 = (int) (Math.random() * 21D) - 10;
			int j6 = (int) (Math.random() * 21D) - 10;
			int k6 = (int) (Math.random() * 41D) - 20;
			for (int l6 = 0; l6 < 100; l6++) {
				if (aClass50_Sub1_Sub1_Sub1Array1031[l6] != null)
					aClass50_Sub1_Sub1_Sub1Array1031[l6].adjustRgb(l5 + k6, i6 + k6, j6 + k6);
				if (aClass50_Sub1_Sub1_Sub3Array1153[l6] != null)
					aClass50_Sub1_Sub1_Sub3Array1153[l6].adjustPalette(l5 + k6, i6 + k6, j6 + k6);
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
				byte abyte0[] = soundArchive.read("sounds.dat");
				Buffer class50_sub1_sub2 = new Buffer(abyte0);
				SoundTrack.load(class50_sub1_sub2);
			}
			drawLoadingText(95, "Unpacking interfaces");
			TypeFace aclass50_sub1_sub1_sub2[] = { smallFont, plainFont,
					boldFont, fancyFont };
			Widget.load(interfaceArchive, mediaArchive, aclass50_sub1_sub1_sub2);
			drawLoadingText(100, "Preparing game engine");
			for (int i7 = 0; i7 < 33; i7++) {
				int j7 = 999;
				int l7 = 0;
				for (int j8 = 0; j8 < 34; j8++) {
					if (aClass50_Sub1_Sub1_Sub3_1186.pixels[j8 + i7 * aClass50_Sub1_Sub1_Sub3_1186.width] == 0) {
						if (j7 == 999)
							j7 = j8;
						continue;
					}
					if (j7 == 999)
						continue;
					l7 = j8;
					break;
				}

				anIntArray1180[i7] = j7;
				anIntArray1286[i7] = l7 - j7;
			}

			for (int k7 = 5; k7 < 156; k7++) {
				int i8 = 999;
				int k8 = 0;
				for (int i9 = 25; i9 < 172; i9++) {
					if (aClass50_Sub1_Sub1_Sub3_1186.pixels[i9 + k7 * aClass50_Sub1_Sub1_Sub3_1186.width] == 0
							&& (i9 > 34 || k7 > 34)) {
						if (i8 == 999)
							i8 = i9;
						continue;
					}
					if (i8 == 999)
						continue;
					k8 = i9;
					break;
				}

				anIntArray1019[k7 - 5] = i8 - 25;
				anIntArray920[k7 - 5] = k8 - i8;
			}

			Rasterizer3D.setBounds(765, 503);
			fullScreenScanlineOffsets = Rasterizer3D.scanlineOffsets;
			Rasterizer3D.setBounds(479, 96);
			chatboxScanlineOffsets = Rasterizer3D.scanlineOffsets;
			Rasterizer3D.setBounds(190, 261);
			sidebarScanlineOffsets = Rasterizer3D.scanlineOffsets;
			Rasterizer3D.setBounds(512, 334);
			viewportScanlineOffsets = Rasterizer3D.scanlineOffsets;
			int ai[] = new int[9];
			for (int l8 = 0; l8 < 9; l8++) {
				int j9 = 128 + l8 * 32 + 15;
				int k9 = 600 + j9 * 3;
				int l9 = Rasterizer3D.SINE[j9];
				ai[l8] = k9 * l9 >> 16;
			}

			Scene.buildVisibilityMaps(500, 800, 512, 334, ai);
			Censor.load(wordEncodingArchive);
			aClass7_1248 = new MouseRecorder(this);
			startThread(aClass7_1248, 10);
			DynamicObject.clientInstance = this;
			GameObjectDefinition.clientInstance = this;
			NpcDefinition.clientInstance = this;
			return;
		} catch (Exception exception) {
			Signlink.reportError("loaderror " + loadingMessage + " " + loadingPercent);
		}
		loadingError = true;
	}

	public void method65(int i, int j) {
		while (j >= 0)
			return;
		if (!lowMemory) {
			for (int k = 0; k < anIntArray1290.length; k++) {
				int l = anIntArray1290[k];
				if (Rasterizer3D.textureLastUsed[l] >= i) {
					IndexedImage rune = Rasterizer3D.textures[l];
					int i1 = rune.width * rune.height - 1;
					int j1 = rune.width * anInt951 * 2;
					byte abyte0[] = rune.pixels;
					byte abyte1[] = aByteArray1245;
					for (int k1 = 0; k1 <= i1; k1++)
						abyte1[k1] = abyte0[k1 - j1 & i1];

					rune.pixels = abyte1;
					aByteArray1245 = abyte0;
					Rasterizer3D.releaseTexture(l);
				}
			}

		}
	}

	/*
	 * Legacy client.method66(int i, Widget class13, int j, int k, int l, int i1, int j1, int k1):
	 *   i -> y, class13 -> widget, j -> screenArea, k -> scrollY, l -> x,
	 *   i1 -> mouseX, j1 -> removed fixed 23658 sentinel, k1 -> mouseY
	 */
	public void buildInterfaceMenu(int i, Widget class13, int j, int k, int l, int i1, int k1) {
		if (class13.type != 0 || class13.children == null || class13.mouseoverTriggered)
			return;
		if (i1 < l || k1 < i || i1 > l + class13.width || k1 > i + class13.height)
			return;
		int l1 = class13.children.length;
		for (int i2 = 0; i2 < l1; i2++) {
			int j2 = class13.childX[i2] + l;
			int k2 = (class13.childY[i2] + i) - k;
			Widget class13_1 = Widget.get(class13.children[i2]);
			j2 += class13_1.xOffset;
			k2 += class13_1.yOffset;
			if ((class13_1.mouseoverTargetId >= 0 || class13_1.mouseoverColor != 0) && i1 >= j2 && k1 >= k2
					&& i1 < j2 + class13_1.width && k1 < k2 + class13_1.height)
				if (class13_1.mouseoverTargetId >= 0)
					anInt915 = class13_1.mouseoverTargetId;
				else
					anInt915 = class13_1.id;
			if (class13_1.type == 8 && i1 >= j2 && k1 >= k2 && i1 < j2 + class13_1.width && k1 < k2 + class13_1.height)
				anInt1315 = class13_1.id;
			if (class13_1.type == 0) {
				buildInterfaceMenu(k2, class13_1, j, class13_1.scrollY, j2, i1, k1);
				if (class13_1.scrollHeight > class13_1.height)
					handleScrollbarInput(class13_1.scrollHeight, k2, class13_1, k1, j, i1, class13_1.height,
							j2 + class13_1.width);
			} else {
				if (class13_1.buttonType == 1 && i1 >= j2 && k1 >= k2 && i1 < j2 + class13_1.width
						&& k1 < k2 + class13_1.height) {
					boolean flag = false;
					if (class13_1.contentType != 0)
						flag = buildSocialWidgetMenu(class13_1);
					if (!flag) {
						menuState.actionNames[menuState.count] = class13_1.tooltip;
						menuState.actionIds[menuState.count] = 352;
						menuState.actionCmd3[menuState.count] = class13_1.id;
						menuState.count++;
					}
				}
				if (class13_1.buttonType == 2 && interfaceState.spellSelected == 0 && i1 >= j2 && k1 >= k2 && i1 < j2 + class13_1.width
						&& k1 < k2 + class13_1.height) {
					String s = class13_1.selectedActionName;
					if (s.indexOf(" ") != -1)
						s = s.substring(0, s.indexOf(" "));
					menuState.actionNames[menuState.count] = s + " @gre@" + class13_1.spellName;
					menuState.actionIds[menuState.count] = 70;
					menuState.actionCmd3[menuState.count] = class13_1.id;
					menuState.count++;
				}
				if (class13_1.buttonType == 3 && i1 >= j2 && k1 >= k2 && i1 < j2 + class13_1.width
						&& k1 < k2 + class13_1.height) {
					menuState.actionNames[menuState.count] = "Close";
					if (j == 3)
						menuState.actionIds[menuState.count] = 55;
					else
						menuState.actionIds[menuState.count] = 639;
					menuState.actionCmd3[menuState.count] = class13_1.id;
					menuState.count++;
				}
				if (class13_1.buttonType == 4 && i1 >= j2 && k1 >= k2 && i1 < j2 + class13_1.width
						&& k1 < k2 + class13_1.height) {
					menuState.actionNames[menuState.count] = class13_1.tooltip;
					menuState.actionIds[menuState.count] = 890;
					menuState.actionCmd3[menuState.count] = class13_1.id;
					menuState.count++;
				}
				if (class13_1.buttonType == 5 && i1 >= j2 && k1 >= k2 && i1 < j2 + class13_1.width
						&& k1 < k2 + class13_1.height) {
					menuState.actionNames[menuState.count] = class13_1.tooltip;
					menuState.actionIds[menuState.count] = 518;
					menuState.actionCmd3[menuState.count] = class13_1.id;
					menuState.count++;
				}
				if (class13_1.buttonType == 6 && !aBoolean1239 && i1 >= j2 && k1 >= k2 && i1 < j2 + class13_1.width
						&& k1 < k2 + class13_1.height) {
					menuState.actionNames[menuState.count] = class13_1.tooltip;
					menuState.actionIds[menuState.count] = 575;
					menuState.actionCmd3[menuState.count] = class13_1.id;
					menuState.count++;
				}
				if (class13_1.type == 2) {
					int l2 = 0;
					for (int i3 = 0; i3 < class13_1.height; i3++) {
						for (int j3 = 0; j3 < class13_1.width; j3++) {
							int k3 = j2 + j3 * (32 + class13_1.inventorySpritePaddingX);
							int l3 = k2 + i3 * (32 + class13_1.inventorySpritePaddingY);
							if (l2 < 20) {
								k3 += class13_1.spriteXOffsets[l2];
								l3 += class13_1.spriteYOffsets[l2];
							}
							if (i1 >= k3 && k1 >= l3 && i1 < k3 + 32 && k1 < l3 + 32) {
								interfaceState.hoveredInventorySlot = l2;
								interfaceState.hoveredInventoryWidgetId = class13_1.id;
								if (class13_1.itemIds[l2] > 0) {
									ItemDefinition class16 = ItemDefinition.lookup(class13_1.itemIds[l2] - 1);
									if (interfaceState.itemSelected == 1 && class13_1.inventoryHasOptions) {
										if (class13_1.id != interfaceState.selectedItemWidgetId || l2 != interfaceState.selectedItemSlot) {
											menuState.actionNames[menuState.count] = "Use " + interfaceState.selectedItemName + " with @lre@"
													+ class16.name;
											menuState.actionIds[menuState.count] = 903;
											menuState.actionCmd1[menuState.count] = class16.id;
											menuState.actionCmd2[menuState.count] = l2;
											menuState.actionCmd3[menuState.count] = class13_1.id;
											menuState.count++;
										}
									} else if (interfaceState.spellSelected == 1 && class13_1.inventoryHasOptions) {
										if ((interfaceState.selectedSpellTargetMask & 0x10) == 16) {
											menuState.actionNames[menuState.count] = interfaceState.selectedSpellAction + " @lre@" + class16.name;
											menuState.actionIds[menuState.count] = 361;
											menuState.actionCmd1[menuState.count] = class16.id;
											menuState.actionCmd2[menuState.count] = l2;
											menuState.actionCmd3[menuState.count] = class13_1.id;
											menuState.count++;
										}
									} else {
										if (class13_1.inventoryHasOptions) {
											for (int i4 = 4; i4 >= 3; i4--)
												if (class16.inventoryActions != null
														&& class16.inventoryActions[i4] != null) {
													menuState.actionNames[menuState.count] = class16.inventoryActions[i4]
															+ " @lre@" + class16.name;
													if (i4 == 3)
														menuState.actionIds[menuState.count] = 227;
													if (i4 == 4)
														menuState.actionIds[menuState.count] = 891;
													menuState.actionCmd1[menuState.count] = class16.id;
													menuState.actionCmd2[menuState.count] = l2;
													menuState.actionCmd3[menuState.count] = class13_1.id;
													menuState.count++;
												} else if (i4 == 4) {
													menuState.actionNames[menuState.count] = "Drop @lre@" + class16.name;
													menuState.actionIds[menuState.count] = 891;
													menuState.actionCmd1[menuState.count] = class16.id;
													menuState.actionCmd2[menuState.count] = l2;
													menuState.actionCmd3[menuState.count] = class13_1.id;
													menuState.count++;
												}

										}
										if (class13_1.inventoryUsableItems) {
											menuState.actionNames[menuState.count] = "Use @lre@" + class16.name;
											menuState.actionIds[menuState.count] = 52;
											menuState.actionCmd1[menuState.count] = class16.id;
											menuState.actionCmd2[menuState.count] = l2;
											menuState.actionCmd3[menuState.count] = class13_1.id;
											menuState.count++;
										}
										if (class13_1.inventoryHasOptions && class16.inventoryActions != null) {
											for (int j4 = 2; j4 >= 0; j4--)
												if (class16.inventoryActions[j4] != null) {
													menuState.actionNames[menuState.count] = class16.inventoryActions[j4]
															+ " @lre@" + class16.name;
													if (j4 == 0)
														menuState.actionIds[menuState.count] = 961;
													if (j4 == 1)
														menuState.actionIds[menuState.count] = 399;
													if (j4 == 2)
														menuState.actionIds[menuState.count] = 324;
													menuState.actionCmd1[menuState.count] = class16.id;
													menuState.actionCmd2[menuState.count] = l2;
													menuState.actionCmd3[menuState.count] = class13_1.id;
													menuState.count++;
												}

										}
										if (class13_1.actions != null) {
											for (int k4 = 4; k4 >= 0; k4--)
												if (class13_1.actions[k4] != null) {
													menuState.actionNames[menuState.count] = class13_1.actions[k4] + " @lre@"
															+ class16.name;
													if (k4 == 0)
														menuState.actionIds[menuState.count] = 9;
													if (k4 == 1)
														menuState.actionIds[menuState.count] = 225;
													if (k4 == 2)
														menuState.actionIds[menuState.count] = 444;
													if (k4 == 3)
														menuState.actionIds[menuState.count] = 564;
													if (k4 == 4)
														menuState.actionIds[menuState.count] = 894;
													menuState.actionCmd1[menuState.count] = class16.id;
													menuState.actionCmd2[menuState.count] = l2;
													menuState.actionCmd3[menuState.count] = class13_1.id;
													menuState.count++;
												}

										}
										menuState.actionNames[menuState.count] = "Examine @lre@" + class16.name;
										menuState.actionIds[menuState.count] = 1094;
										menuState.actionCmd1[menuState.count] = class16.id;
										menuState.actionCmd2[menuState.count] = l2;
										menuState.actionCmd3[menuState.count] = class13_1.id;
										menuState.count++;
									}
								}
							}
							l2++;
						}

					}

				}
			}
		}

	}








	public void method74(int i) {
		if (interfaceState.fullscreenInterfaceId != -1 && (regionManager.loadingStage == RegionManager.STAGE_LOADED || super.gameBuffer != null)) {
			if (regionManager.loadingStage == RegionManager.STAGE_LOADED) {
				widgetRuntime.updateAnimations(anInt951, interfaceState.fullscreenInterfaceId);
				if (interfaceState.fullscreenOverlayInterfaceId != -1)
					widgetRuntime.updateAnimations(anInt951, interfaceState.fullscreenOverlayInterfaceId);
				anInt951 = 0;
				createGameBuffer();
				super.gameBuffer.bindRaster();
				Rasterizer3D.scanlineOffsets = fullScreenScanlineOffsets;
				Rasterizer.resetPixels();
				aBoolean1046 = true;
				Widget class13 = Widget.get(interfaceState.fullscreenInterfaceId);
				if (class13.width == 512 && class13.height == 334 && class13.type == 0) {
					class13.width = 765;
					class13.height = 503;
				}
				drawInterface(0, 0, class13, 0);
				if (interfaceState.fullscreenOverlayInterfaceId != -1) {
					Widget class13_1 = Widget.get(interfaceState.fullscreenOverlayInterfaceId);
					if (class13_1.width == 512 && class13_1.height == 334 && class13_1.type == 0) {
						class13_1.width = 765;
						class13_1.height = 503;
					}
					drawInterface(0, 0, class13_1, 0);
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
		if (aBoolean1046) {
			createGameScreenBuffers();
			aBoolean1046 = false;
			aClass18_906.draw(super.graphics, 0, 4);
			aClass18_907.draw(super.graphics, 0, 357);
			aClass18_908.draw(super.graphics, 722, 4);
			aClass18_909.draw(super.graphics, 743, 205);
			aClass18_910.draw(super.graphics, 0, 0);
			aClass18_911.draw(super.graphics, 516, 4);
			aClass18_912.draw(super.graphics, 516, 205);
			aClass18_913.draw(super.graphics, 496, 357);
			aClass18_914.draw(super.graphics, 0, 338);
			aBoolean1181 = true;
			aBoolean1240 = true;
			aBoolean950 = true;
			chatModesRedraw = true;
			if (regionManager.loadingStage != RegionManager.STAGE_LOADED) {
				viewportBuffer.draw(super.graphics, 4, 4);
				minimapBuffer.draw(super.graphics, 550, 4);
			}
			anInt1237++;
			if (anInt1237 > 85) {
				anInt1237 = 0;
				networkSession.outgoing.writeOpcode(168);
			}
		}
		if (regionManager.loadingStage == RegionManager.STAGE_LOADED)
			renderGameScene();
		if (menuState.open && menuState.screenArea == 1)
			aBoolean1181 = true;
		if (interfaceState.sidebarOverlayInterfaceId != -1) {
			boolean flag = widgetRuntime.updateAnimations(anInt951, interfaceState.sidebarOverlayInterfaceId);
			if (flag)
				aBoolean1181 = true;
		}
		if (interfaceState.pressedInventoryArea == 2)
			aBoolean1181 = true;
		if (interfaceState.inventoryDragArea == 2)
			aBoolean1181 = true;
		if (aBoolean1181) {
			method134((byte) 7);
			aBoolean1181 = false;
		}
		if (interfaceState.chatboxInterfaceId == -1 && anInt1244 == 0) {
			aClass13_1249.scrollY = chatContentHeight - chatScrollOffset - 77;
			if (super.mouseX > 448 && super.mouseX < 560 && super.mouseY > 332)
				handleScrollbarInput(chatContentHeight, 0, aClass13_1249, super.mouseY - 357, -1, super.mouseX - 17, 77, 463);
			int j = chatContentHeight - 77 - aClass13_1249.scrollY;
			if (j < 0)
				j = 0;
			if (j > chatContentHeight - 77)
				j = chatContentHeight - 77;
			if (chatScrollOffset != j) {
				chatScrollOffset = j;
				aBoolean1240 = true;
			}
		}
		if (interfaceState.chatboxInterfaceId == -1 && anInt1244 == 3) {
			int k = anInt862 * 14 + 7;
			aClass13_1249.scrollY = anInt865;
			if (super.mouseX > 448 && super.mouseX < 560 && super.mouseY > 332)
				handleScrollbarInput(k, 0, aClass13_1249, super.mouseY - 357, -1, super.mouseX - 17, 77, 463);
			int i1 = aClass13_1249.scrollY;
			if (i1 < 0)
				i1 = 0;
			if (i1 > k - 77)
				i1 = k - 77;
			if (anInt865 != i1) {
				anInt865 = i1;
				aBoolean1240 = true;
			}
		}
		if (interfaceState.chatboxInterfaceId != -1) {
			boolean flag1 = widgetRuntime.updateAnimations(anInt951, interfaceState.chatboxInterfaceId);
			if (flag1)
				aBoolean1240 = true;
		}
		if (interfaceState.pressedInventoryArea == 3)
			aBoolean1240 = true;
		if (interfaceState.inventoryDragArea == 3)
			aBoolean1240 = true;
		if (aString1058 != null)
			aBoolean1240 = true;
		if (menuState.open && menuState.screenArea == 2)
			aBoolean1240 = true;
		if (aBoolean1240) {
			method84(0);
			aBoolean1240 = false;
		}
		if (regionManager.loadingStage == RegionManager.STAGE_LOADED) {
			drawMinimap();
			minimapBuffer.draw(super.graphics, 550, 4);
		}
		if (interfaceState.flashingTab != -1)
			aBoolean950 = true;
		if (aBoolean950) {
			if (interfaceState.flashingTab != -1 && interfaceState.flashingTab == interfaceState.selectedTab) {
				networkSession.outgoing.writeOpcode(119);
				networkSession.outgoing.writeByte(interfaceState.selectedTab);
			}
			aBoolean950 = false;
			aClass18_1110.bindRaster();
			aClass50_Sub1_Sub1_Sub3_967.draw(0, 0);
			if (interfaceState.sidebarOverlayInterfaceId == -1) {
				if (interfaceState.tabInterfaceIds[interfaceState.selectedTab] != -1) {
					if (interfaceState.selectedTab == 0)
						aClass50_Sub1_Sub1_Sub3_880.draw(22, 10);
					if (interfaceState.selectedTab == 1)
						aClass50_Sub1_Sub1_Sub3_881.draw(54, 8);
					if (interfaceState.selectedTab == 2)
						aClass50_Sub1_Sub1_Sub3_881.draw(82, 8);
					if (interfaceState.selectedTab == 3)
						aClass50_Sub1_Sub1_Sub3_882.draw(110, 8);
					if (interfaceState.selectedTab == 4)
						aClass50_Sub1_Sub1_Sub3_884.draw(153, 8);
					if (interfaceState.selectedTab == 5)
						aClass50_Sub1_Sub1_Sub3_884.draw(181, 8);
					if (interfaceState.selectedTab == 6)
						aClass50_Sub1_Sub1_Sub3_883.draw(209, 9);
				}
				if (interfaceState.tabInterfaceIds[0] != -1 && (interfaceState.flashingTab != 0 || anInt1325 % 20 < 10))
					aClass50_Sub1_Sub1_Sub3Array976[0].draw(29, 13);
				if (interfaceState.tabInterfaceIds[1] != -1 && (interfaceState.flashingTab != 1 || anInt1325 % 20 < 10))
					aClass50_Sub1_Sub1_Sub3Array976[1].draw(53, 11);
				if (interfaceState.tabInterfaceIds[2] != -1 && (interfaceState.flashingTab != 2 || anInt1325 % 20 < 10))
					aClass50_Sub1_Sub1_Sub3Array976[2].draw(82, 11);
				if (interfaceState.tabInterfaceIds[3] != -1 && (interfaceState.flashingTab != 3 || anInt1325 % 20 < 10))
					aClass50_Sub1_Sub1_Sub3Array976[3].draw(115, 12);
				if (interfaceState.tabInterfaceIds[4] != -1 && (interfaceState.flashingTab != 4 || anInt1325 % 20 < 10))
					aClass50_Sub1_Sub1_Sub3Array976[4].draw(153, 13);
				if (interfaceState.tabInterfaceIds[5] != -1 && (interfaceState.flashingTab != 5 || anInt1325 % 20 < 10))
					aClass50_Sub1_Sub1_Sub3Array976[5].draw(180, 11);
				if (interfaceState.tabInterfaceIds[6] != -1 && (interfaceState.flashingTab != 6 || anInt1325 % 20 < 10))
					aClass50_Sub1_Sub1_Sub3Array976[6].draw(208, 13);
			}
			aClass18_1110.draw(super.graphics, 516, 160);
			aClass18_1109.bindRaster();
			aClass50_Sub1_Sub1_Sub3_966.draw(0, 0);
			if (interfaceState.sidebarOverlayInterfaceId == -1) {
				if (interfaceState.tabInterfaceIds[interfaceState.selectedTab] != -1) {
					if (interfaceState.selectedTab == 7)
						aClass50_Sub1_Sub1_Sub3_983.draw(42, 0);
					if (interfaceState.selectedTab == 8)
						aClass50_Sub1_Sub1_Sub3_984.draw(74, 0);
					if (interfaceState.selectedTab == 9)
						aClass50_Sub1_Sub1_Sub3_984.draw(102, 0);
					if (interfaceState.selectedTab == 10)
						aClass50_Sub1_Sub1_Sub3_985.draw(130, 1);
					if (interfaceState.selectedTab == 11)
						aClass50_Sub1_Sub1_Sub3_987.draw(173, 0);
					if (interfaceState.selectedTab == 12)
						aClass50_Sub1_Sub1_Sub3_987.draw(201, 0);
					if (interfaceState.selectedTab == 13)
						aClass50_Sub1_Sub1_Sub3_986.draw(229, 0);
				}
				if (interfaceState.tabInterfaceIds[8] != -1 && (interfaceState.flashingTab != 8 || anInt1325 % 20 < 10))
					aClass50_Sub1_Sub1_Sub3Array976[7].draw(74, 2);
				if (interfaceState.tabInterfaceIds[9] != -1 && (interfaceState.flashingTab != 9 || anInt1325 % 20 < 10))
					aClass50_Sub1_Sub1_Sub3Array976[8].draw(102, 3);
				if (interfaceState.tabInterfaceIds[10] != -1 && (interfaceState.flashingTab != 10 || anInt1325 % 20 < 10))
					aClass50_Sub1_Sub1_Sub3Array976[9].draw(137, 4);
				if (interfaceState.tabInterfaceIds[11] != -1 && (interfaceState.flashingTab != 11 || anInt1325 % 20 < 10))
					aClass50_Sub1_Sub1_Sub3Array976[10].draw(174, 2);
				if (interfaceState.tabInterfaceIds[12] != -1 && (interfaceState.flashingTab != 12 || anInt1325 % 20 < 10))
					aClass50_Sub1_Sub1_Sub3Array976[11].draw(201, 2);
				if (interfaceState.tabInterfaceIds[13] != -1 && (interfaceState.flashingTab != 13 || anInt1325 % 20 < 10))
					aClass50_Sub1_Sub1_Sub3Array976[12].draw(226, 2);
			}
			aClass18_1109.draw(super.graphics, 496, 466);
			viewportBuffer.bindRaster();
			Rasterizer3D.scanlineOffsets = viewportScanlineOffsets;
		}
		if (chatModesRedraw) {
			chatModesRedraw = false;
			aClass18_1108.bindRaster();
			aClass50_Sub1_Sub1_Sub3_965.draw(0, 0);
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
			aClass18_1108.draw(super.graphics, 0, 453);
			viewportBuffer.bindRaster();
			Rasterizer3D.scanlineOffsets = viewportScanlineOffsets;
		}
		anInt951 = 0;
		if (i != 7) {
			for (int l = 1; l > 0; l++)
				;
		}
	}

	/* Legacy client.method75(int i): i -> removed zero sentinel. */
	public void drawSplitPrivateChat() {
		if (splitPrivateChat == 0)
			return;
		TypeFace class50_sub1_sub1_sub2 = plainFont;
		int j = 0;
		if (anInt1057 != 0)
			j = 1;
		for (int k = 0; k < 100; k++)
			if (chatHistory.messages[k] != null) {
				int l = chatHistory.types[k];
				String s = chatHistory.senders[k];
				byte byte0 = 0;
				if (s != null && s.startsWith("@cr1@")) {
					s = s.substring(5);
					byte0 = 1;
				}
				if (s != null && s.startsWith("@cr2@")) {
					s = s.substring(5);
					byte0 = 2;
				}
				if ((l == 3 || l == 7) && (l == 7 || privateChatMode == 0 || privateChatMode == 1 && isFriendOrSelf(s))) {
					int i1 = 329 - j * 13;
					int l1 = 4;
					class50_sub1_sub1_sub2.drawText("From", l1, i1, 0);
					class50_sub1_sub1_sub2.drawText("From", l1, i1 - 1, 65535);
					l1 += class50_sub1_sub1_sub2.getFormattedTextWidth("From ");
					if (byte0 == 1) {
						aClass50_Sub1_Sub1_Sub3Array1142[0].draw(l1, i1 - 12);
						l1 += 14;
					}
					if (byte0 == 2) {
						aClass50_Sub1_Sub1_Sub3Array1142[1].draw(l1, i1 - 12);
						l1 += 14;
					}
					class50_sub1_sub1_sub2.drawText(s + ": " + chatHistory.messages[k], l1, i1, 0);
					class50_sub1_sub1_sub2.drawText(s + ": " + chatHistory.messages[k], l1, i1 - 1, 65535);
					if (++j >= 5)
						return;
				}
				if (l == 5 && privateChatMode < 2) {
					int j1 = 329 - j * 13;
					class50_sub1_sub1_sub2.drawText(chatHistory.messages[k], 4, j1, 0);
					class50_sub1_sub1_sub2.drawText(chatHistory.messages[k], 4, j1 - 1, 65535);
					if (++j >= 5)
						return;
				}
				if (l == 6 && privateChatMode < 2) {
					int k1 = 329 - j * 13;
					class50_sub1_sub1_sub2.drawText("To " + s + ": " + chatHistory.messages[k], 4, k1, 0);
					class50_sub1_sub1_sub2.drawText("To " + s + ": " + chatHistory.messages[k], 4, k1 - 1, 65535);
					if (++j >= 5)
						return;
				}
			}

	}

	public void method77(boolean flag) {
		if (flag)
			networkSession.incomingOpcode = -1;
		do {
			OnDemandRequest class50_sub1_sub3;
			do {
				class50_sub1_sub3 = onDemandFetcher.poll();
				if (class50_sub1_sub3 == null)
					return;
				if (class50_sub1_sub3.type == 0) {
					Model.loadModelHeader(class50_sub1_sub3.buffer, class50_sub1_sub3.id);
					if ((onDemandFetcher.getModelIndex(class50_sub1_sub3.id) & 0x62) != 0) {
						aBoolean1181 = true;
						if (interfaceState.chatboxInterfaceId != -1 || interfaceState.dialogueInterfaceId != -1)
							aBoolean1240 = true;
					}
				}
				if (class50_sub1_sub3.type == 1 && class50_sub1_sub3.buffer != null)
					AnimationFrame.load(class50_sub1_sub3.buffer);
				musicController.acceptOnDemandRequest(class50_sub1_sub3);
				if (class50_sub1_sub3.type == 3 && regionManager.loadingStage == RegionManager.STAGE_LOADING)
					regionManager.acceptMapFile(class50_sub1_sub3);
			} while (class50_sub1_sub3.type != 93 || !onDemandFetcher.isLandscapeFile(class50_sub1_sub3.id));
			Region.requestGameObjectModels(new Buffer(class50_sub1_sub3.buffer), onDemandFetcher);
		} while (true);
	}

	public void login(String loginUsername, String loginPassword, boolean reconnecting) {
		try {
			if (!reconnecting) {
			loginScreen.message1 = "";
			loginScreen.message2 = "Connecting to server...";
				drawLoginScreen(true);
			}
			networkSession.connect(openSocket(43594 + portOffset));
			long l = Base37.encode(loginUsername);
			int i = (int) (l >> 16 & 31L);
			networkSession.outgoing.position = 0;
			networkSession.outgoing.writeByte(14);
			networkSession.outgoing.writeByte(i);
			networkSession.write(networkSession.outgoing.payload, 0, 2);
			for (int j = 0; j < 8; j++)
				networkSession.read();

			int k = networkSession.read();
			int i1 = k;
			if (k == 0) {
				networkSession.readFully(networkSession.incoming.payload, 0, 8);
				networkSession.incoming.position = 0;
				serverSessionKey = networkSession.incoming.readLong();
				int ai[] = new int[4];
				ai[0] = (int) (Math.random() * 99999999D);
				ai[1] = (int) (Math.random() * 99999999D);
				ai[2] = (int) (serverSessionKey >> 32);
				ai[3] = (int) serverSessionKey;
				networkSession.outgoing.position = 0;
				networkSession.outgoing.writeByte(10);
				networkSession.outgoing.writeInt(ai[0]);
				networkSession.outgoing.writeInt(ai[1]);
				networkSession.outgoing.writeInt(ai[2]);
				networkSession.outgoing.writeInt(ai[3]);
				networkSession.outgoing.writeInt(Signlink.uid);
				networkSession.outgoing.writeString(loginUsername);
				networkSession.outgoing.writeString(loginPassword);
				networkSession.outgoing.encryptRsa(aBigInteger1316, aBigInteger840);
				loginBuffer.position = 0;
				if (reconnecting)
					loginBuffer.writeByte(18);
				else
					loginBuffer.writeByte(16);
				loginBuffer.writeByte(networkSession.outgoing.position + 36 + 1 + 1 + 2);
				loginBuffer.writeByte(255);
				loginBuffer.writeShort(377);
				loginBuffer.writeByte(lowMemory ? 1 : 0);
				for (int l1 = 0; l1 < 9; l1++)
					loginBuffer.writeInt(resourceLoader.getArchiveCrc(l1));

				loginBuffer.writeBytes(networkSession.outgoing.payload, 0, networkSession.outgoing.position);
				networkSession.initializeOpcodeCiphers(ai);
				networkSession.write(loginBuffer.payload, 0, loginBuffer.position);
				k = networkSession.read();
			}
			if (k == 1) {
				try {
					Thread.sleep(2000L);
				} catch (Exception _ex) {
				}
				login(loginUsername, loginPassword, reconnecting);
				return;
			}
			if (k == 2) {
				playerRights = networkSession.read();
				accountFlagged = networkSession.read() == 1;
				aLong902 = 0L;
				anInt1299 = 0;
				aClass7_1248.sampleCount = 0;
				super.hasFocus = true;
				aBoolean1275 = true;
				loggedIn = true;
				networkSession.resetPacketState();
				anInt1057 = 0;
				logoutTimer = 0;
				anInt1197 = 0;
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
				aBoolean1239 = false;
				anInt1244 = 0;
						messagePromptRaised = false;
				aString1058 = null;
				anInt1319 = 0;
				aBoolean1144 = true;
				method25(anInt1015);
				for (int j3 = 0; j3 < 5; j3++)
					anIntArray1099[j3] = 0;

				for (int l3 = 0; l3 < 5; l3++) {
					aStringArray1069[l3] = null;
					aBooleanArray1070[l3] = false;
				}

				anInt1100 = 0;
				anInt1165 = 0;
				anInt1235 = 0;
				anInt1052 = 0;
				anInt1139 = 0;
				anInt841 = 0;
				anInt1230 = 0;
				anInt1013 = 0;
				anInt1049 = 0;
				anInt1162 = 0;
				createGameScreenBuffers();
				return;
			}
			if (k == 3) {
				loginScreen.message1 = "";
				loginScreen.message2 = "Invalid username or password.";
				return;
			}
			if (k == 4) {
				loginScreen.message1 = "Your account has been disabled.";
				loginScreen.message2 = "Please check your message-centre for details.";
				return;
			}
			if (k == 5) {
				loginScreen.message1 = "Your account is already logged in.";
				loginScreen.message2 = "Try again in 60 secs...";
				return;
			}
			if (k == 6) {
				loginScreen.message1 = "RuneScape has been updated!";
				loginScreen.message2 = "Please reload this page.";
				return;
			}
			if (k == 7) {
				loginScreen.message1 = "This world is full.";
				loginScreen.message2 = "Please use a different world.";
				return;
			}
			if (k == 8) {
				loginScreen.message1 = "Unable to connect.";
				loginScreen.message2 = "Login server offline.";
				return;
			}
			if (k == 9) {
				loginScreen.message1 = "Login limit exceeded.";
				loginScreen.message2 = "Too many connections from your address.";
				return;
			}
			if (k == 10) {
				loginScreen.message1 = "Unable to connect.";
				loginScreen.message2 = "Bad session id.";
				return;
			}
			if (k == 12) {
				loginScreen.message1 = "You need a members account to login to this world.";
				loginScreen.message2 = "Please subscribe, or use a different world.";
				return;
			}
			if (k == 13) {
				loginScreen.message1 = "Could not complete login.";
				loginScreen.message2 = "Please try using a different world.";
				return;
			}
			if (k == 14) {
				loginScreen.message1 = "The server is being updated.";
				loginScreen.message2 = "Please wait 1 minute and try again.";
				return;
			}
			if (k == 15) {
				loggedIn = true;
				networkSession.resetPacketState();
				anInt1057 = 0;
				menuState.count = 0;
						regionManager.loadingStartTime = System.currentTimeMillis();
				return;
			}
			if (k == 16) {
				loginScreen.message1 = "Login attempts exceeded.";
				loginScreen.message2 = "Please wait 1 minute and try again.";
				return;
			}
			if (k == 17) {
				loginScreen.message1 = "You are standing in a members-only area.";
				loginScreen.message2 = "To play on this world move to a free area first";
				return;
			}
			if (k == 18) {
				loginScreen.message1 = "Account locked as we suspect it has been stolen.";
				loginScreen.message2 = "Press 'recover a locked account' on front page.";
				return;
			}
			if (k == 20) {
				loginScreen.message1 = "Invalid loginserver requested";
				loginScreen.message2 = "Please try using a different world.";
				return;
			}
			if (k == 21) {
				int k1 = networkSession.read();
				for (k1 += 3; k1 >= 0; k1--) {
					loginScreen.message1 = "You have only just left another world";
					loginScreen.message2 = "Your profile will be transferred in: " + k1;
					drawLoginScreen(true);
					try {
						Thread.sleep(1200L);
					} catch (Exception _ex) {
					}
				}

				login(loginUsername, loginPassword, reconnecting);
				return;
			}
			if (k == 22) {
				loginScreen.message1 = "Malformed login packet.";
				loginScreen.message2 = "Please try again.";
				return;
			}
			if (k == 23) {
				loginScreen.message1 = "No reply from loginserver.";
				loginScreen.message2 = "Please try again.";
				return;
			}
			if (k == 24) {
				loginScreen.message1 = "Error loading your profile.";
				loginScreen.message2 = "Please contact customer support.";
				return;
			}
			if (k == 25) {
				loginScreen.message1 = "Unexpected loginserver response.";
				loginScreen.message2 = "Please try using a different world.";
				return;
			}
			if (k == 26) {
				loginScreen.message1 = "This computers address has been blocked";
				loginScreen.message2 = "as it was used to break our rules";
				return;
			}
			if (k == -1) {
				if (i1 == 0) {
					if (loginFailures < 2) {
						try {
							Thread.sleep(2000L);
						} catch (Exception _ex) {
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
				System.out.println("response:" + k);
				loginScreen.message1 = "Unexpected server response";
				loginScreen.message2 = "Please try using a different world.";
				return;
			}
		} catch (IOException _ex) {
			loginScreen.message1 = "";
		}
		loginScreen.message2 = "Error connecting to server.";
	}

	/*
	 * Legacy client.method80(int i, int j, int k, int l)
	 *   i -> tileY
	 *   j -> removed dummy value (all supplied callers pass 0; only added to incoming packet length)
	 *   k -> tileX
	 *   l -> uid
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
		anInt1020 = clickX;
		anInt1021 = clickY;
		anInt1023 = 2;
		anInt1022 = 0;
		return true;
	}

	/* Legacy client.method81(byte byte0): byte0 -> removed fixed 1 sentinel. */
	public void updateTitleFlames() {
		char c = '\u0100';
		for (int i = 10; i < 117; i++) {
			int j = (int) (Math.random() * 100D);
			if (j < 50)
				titleFlameIntensity[i + (c - 2 << 7)] = 255;
		}

		for (int k = 0; k < 100; k++) {
			int l = (int) (Math.random() * 124D) + 2;
			int j1 = (int) (Math.random() * 128D) + 128;
			int j2 = l + (j1 << 7);
			titleFlameIntensity[j2] = 192;
		}

		for (int i1 = 1; i1 < c - 1; i1++) {
			for (int k1 = 1; k1 < 127; k1++) {
				int k2 = k1 + (i1 << 7);
				titleFlameIntensityScratch[k2] = (titleFlameIntensity[k2 - 1] + titleFlameIntensity[k2 + 1] + titleFlameIntensity[k2 - 128]
						+ titleFlameIntensity[k2 + 128]) / 4;
			}

		}

		titleFlameNoiseOffset += 128;
		if (titleFlameNoiseOffset > titleFlameNoise.length) {
			titleFlameNoiseOffset -= titleFlameNoise.length;
			int l1 = (int) (Math.random() * 12D);
			initializeTitleFlameNoise(titleRunes[l1]);
		}
		for (int i2 = 1; i2 < c - 1; i2++) {
			for (int l2 = 1; l2 < 127; l2++) {
				int k3 = l2 + (i2 << 7);
				int i4 = titleFlameIntensityScratch[k3 + 128] - titleFlameNoise[k3 + titleFlameNoiseOffset & titleFlameNoise.length - 1] / 5;
				if (i4 < 0)
					i4 = 0;
				titleFlameIntensity[k3] = i4;
			}

		}

		for (int j3 = 0; j3 < c - 1; j3++)
			titleFlameLineOffsets[j3] = titleFlameLineOffsets[j3 + 1];

		titleFlameLineOffsets[c - 1] = (int) (Math.sin((double) anInt1325 / 14D) * 16D
				+ Math.sin((double) anInt1325 / 15D) * 14D + Math.sin((double) anInt1325 / 16D) * 12D);
		if (greenFlameTransition > 0)
			greenFlameTransition -= 4;
		if (blueFlameTransition > 0)
			blueFlameTransition -= 4;
		if (greenFlameTransition == 0 && blueFlameTransition == 0) {
			int l3 = (int) (Math.random() * 2000D);
			if (l3 == 0)
				greenFlameTransition = 1024;
			if (l3 == 1)
				blueFlameTransition = 1024;
		}
	}

	/*
	 * Legacy client.method82(NpcDefinition class37, int i, int j, int k, byte byte0):
	 *   class37 -> definition, i -> tileY, j -> tileX, k -> npcIndex,
	 *   byte0 -> removed fixed -76 sentinel
	 */
	public void buildNpcMenu(NpcDefinition class37, int i, int j, int k) {
		if (menuState.count >= 400)
			return;
		if (class37.morphIds != null)
			class37 = class37.transform();
		if (class37 == null)
			return;
		if (!class37.clickable)
			return;
		String s = class37.name;
		if (class37.combatLevel != 0)
			s = s + method92(class37.combatLevel, localPlayer.combatLevel, 736) + " (level-"
					+ class37.combatLevel + ")";
		if (interfaceState.itemSelected == 1) {
			menuState.actionNames[menuState.count] = "Use " + interfaceState.selectedItemName + " with @yel@" + s;
			menuState.actionIds[menuState.count] = 347;
			menuState.actionCmd1[menuState.count] = k;
			menuState.actionCmd2[menuState.count] = j;
			menuState.actionCmd3[menuState.count] = i;
			menuState.count++;
			return;
		}
		if (interfaceState.spellSelected == 1) {
			if ((interfaceState.selectedSpellTargetMask & 2) == 2) {
				menuState.actionNames[menuState.count] = interfaceState.selectedSpellAction + " @yel@" + s;
				menuState.actionIds[menuState.count] = 67;
				menuState.actionCmd1[menuState.count] = k;
				menuState.actionCmd2[menuState.count] = j;
				menuState.actionCmd3[menuState.count] = i;
				menuState.count++;
				return;
			}
		} else {
			if (class37.actions != null) {
				for (int l = 4; l >= 0; l--)
					if (class37.actions[l] != null && !class37.actions[l].equalsIgnoreCase("attack")) {
						menuState.actionNames[menuState.count] = class37.actions[l] + " @yel@" + s;
						if (l == 0)
							menuState.actionIds[menuState.count] = 318;
						if (l == 1)
							menuState.actionIds[menuState.count] = 921;
						if (l == 2)
							menuState.actionIds[menuState.count] = 118;
						if (l == 3)
							menuState.actionIds[menuState.count] = 553;
						if (l == 4)
							menuState.actionIds[menuState.count] = 432;
						menuState.actionCmd1[menuState.count] = k;
						menuState.actionCmd2[menuState.count] = j;
						menuState.actionCmd3[menuState.count] = i;
						menuState.count++;
					}

			}
			if (class37.actions != null) {
				for (int i1 = 4; i1 >= 0; i1--)
					if (class37.actions[i1] != null && class37.actions[i1].equalsIgnoreCase("attack")) {
						char c = '\0';
						if (class37.combatLevel > localPlayer.combatLevel)
							c = '\u07D0';
						menuState.actionNames[menuState.count] = class37.actions[i1] + " @yel@" + s;
						if (i1 == 0)
							menuState.actionIds[menuState.count] = 318 + c;
						if (i1 == 1)
							menuState.actionIds[menuState.count] = 921 + c;
						if (i1 == 2)
							menuState.actionIds[menuState.count] = 118 + c;
						if (i1 == 3)
							menuState.actionIds[menuState.count] = 553 + c;
						if (i1 == 4)
							menuState.actionIds[menuState.count] = 432 + c;
						menuState.actionCmd1[menuState.count] = k;
						menuState.actionCmd2[menuState.count] = j;
						menuState.actionCmd3[menuState.count] = i;
						menuState.count++;
					}

			}
			menuState.actionNames[menuState.count] = "Examine @yel@" + s;
			menuState.actionIds[menuState.count] = 1668;
			menuState.actionCmd1[menuState.count] = k;
			menuState.actionCmd2[menuState.count] = j;
			menuState.actionCmd3[menuState.count] = i;
			menuState.count++;
		}
	}

	/*
	 * Legacy client.method83(IndexedImage class50_sub1_sub1_sub3, int i):
	 *   class50_sub1_sub1_sub3 -> rune, i -> removed zero packet-length mutation sentinel.
	 */
	public void initializeTitleFlameNoise(IndexedImage rune) {
		int j = 256;
		for (int k = 0; k < titleFlameNoise.length; k++)
			titleFlameNoise[k] = 0;

		for (int l = 0; l < 5000; l++) {
			int i1 = (int) (Math.random() * 128D * (double) j);
			titleFlameNoise[i1] = (int) (Math.random() * 256D);
		}

		for (int j1 = 0; j1 < 20; j1++) {
			for (int k1 = 1; k1 < j - 1; k1++) {
				for (int i2 = 1; i2 < 127; i2++) {
					int k2 = i2 + (k1 << 7);
					titleFlameNoiseScratch[k2] = (titleFlameNoise[k2 - 1] + titleFlameNoise[k2 + 1] + titleFlameNoise[k2 - 128]
							+ titleFlameNoise[k2 + 128]) / 4;
				}

			}

			int ai[] = titleFlameNoise;
			titleFlameNoise = titleFlameNoiseScratch;
			titleFlameNoiseScratch = ai;
		}

		if (rune != null) {
			int l1 = 0;
			for (int j2 = 0; j2 < rune.height; j2++) {
				for (int l2 = 0; l2 < rune.width; l2++)
					if (rune.pixels[l1++] != 0) {
						int i3 = l2 + 16 + rune.offsetX;
						int j3 = j2 + 16 + rune.offsetY;
						int k3 = i3 + (j3 << 7);
						titleFlameNoise[k3] = 0;
					}

			}

		}
	}

	public void method84(int i) {
		chatboxBuffer.bindRaster();
		Rasterizer3D.scanlineOffsets = chatboxScanlineOffsets;
		aClass50_Sub1_Sub1_Sub3_1187.draw(0, 0);
		if (messagePromptRaised) {
			boldFont.drawCenteredText(promptMessage, 239, 40, 0);
			boldFont.drawCenteredText(promptInput + "*", 239, 60, 128);
		} else if (anInt1244 == 1) {
			boldFont.drawCenteredText("Enter amount:", 239, 40, 0);
			boldFont.drawCenteredText(aString949 + "*", 239, 60, 128);
		} else if (anInt1244 == 2) {
			boldFont.drawCenteredText("Enter name:", 239, 40, 0);
			boldFont.drawCenteredText(aString949 + "*", 239, 60, 128);
		} else if (anInt1244 == 3) {
			if (aString949 != aString861) {
				method14(aString949, 2);
				aString861 = aString949;
			}
			TypeFace class50_sub1_sub1_sub2 = plainFont;
			Rasterizer.setCoordinates(0, 0, 463, 77);
			for (int j = 0; j < anInt862; j++) {
				int l = (18 + j * 14) - anInt865;
				if (l > 0 && l < 110)
					class50_sub1_sub1_sub2.drawCenteredText(aStringArray863[j], 239, l, 0);
			}

			Rasterizer.resetCoordinates();
			if (anInt862 > 5)
				drawScrollbar(anInt865, 463, 77, anInt862 * 14 + 7, 0);
			if (aString949.length() == 0)
				boldFont.drawCenteredText("Enter object name", 239, 40, 255);
			else if (anInt862 == 0)
				boldFont.drawCenteredText("No matching objects found, please shorten search", 239,
						40, 0);
			class50_sub1_sub1_sub2.drawCenteredText(aString949 + "*", 239, 90, 0);
			Rasterizer.drawHorizontalLine(0, 77, 479, 0);
		} else if (aString1058 != null) {
			boldFont.drawCenteredText(aString1058, 239, 40, 0);
			boldFont.drawCenteredText("Click to continue", 239, 60, 128);
		} else if (interfaceState.chatboxInterfaceId != -1)
			drawInterface(0, 0, Widget.get(interfaceState.chatboxInterfaceId), 0);
		else if (interfaceState.dialogueInterfaceId != -1) {
			drawInterface(0, 0, Widget.get(interfaceState.dialogueInterfaceId), 0);
		} else {
			TypeFace class50_sub1_sub1_sub2_1 = plainFont;
			int k = 0;
			Rasterizer.setCoordinates(0, 0, 463, 77);
			for (int i1 = 0; i1 < 100; i1++)
				if (chatHistory.messages[i1] != null) {
					int j1 = chatHistory.types[i1];
					int k1 = (70 - k * 14) + chatScrollOffset;
					String s1 = chatHistory.senders[i1];
					byte byte0 = 0;
					if (s1 != null && s1.startsWith("@cr1@")) {
						s1 = s1.substring(5);
						byte0 = 1;
					}
					if (s1 != null && s1.startsWith("@cr2@")) {
						s1 = s1.substring(5);
						byte0 = 2;
					}
					if (j1 == 0) {
						if (k1 > 0 && k1 < 110)
							class50_sub1_sub1_sub2_1.drawText(chatHistory.messages[i1], 4, k1, 0);
						k++;
					}
					if ((j1 == 1 || j1 == 2) && (j1 == 1 || publicChatMode == 0 || publicChatMode == 1 && isFriendOrSelf(s1))) {
						if (k1 > 0 && k1 < 110) {
							int l1 = 4;
							if (byte0 == 1) {
								aClass50_Sub1_Sub1_Sub3Array1142[0].draw(l1, k1 - 12);
								l1 += 14;
							}
							if (byte0 == 2) {
								aClass50_Sub1_Sub1_Sub3Array1142[1].draw(l1, k1 - 12);
								l1 += 14;
							}
							class50_sub1_sub1_sub2_1.drawText(s1 + ":", l1, k1, 0);
							l1 += class50_sub1_sub1_sub2_1.getFormattedTextWidth(s1) + 8;
							class50_sub1_sub1_sub2_1.drawText(chatHistory.messages[i1], l1, k1, 255);
						}
						k++;
					}
					if ((j1 == 3 || j1 == 7) && splitPrivateChat == 0
							&& (j1 == 7 || privateChatMode == 0 || privateChatMode == 1 && isFriendOrSelf(s1))) {
						if (k1 > 0 && k1 < 110) {
							int i2 = 4;
							class50_sub1_sub1_sub2_1.drawText("From", i2, k1, 0);
							i2 += class50_sub1_sub1_sub2_1.getFormattedTextWidth("From ");
							if (byte0 == 1) {
								aClass50_Sub1_Sub1_Sub3Array1142[0].draw(i2, k1 - 12);
								i2 += 14;
							}
							if (byte0 == 2) {
								aClass50_Sub1_Sub1_Sub3Array1142[1].draw(i2, k1 - 12);
								i2 += 14;
							}
							class50_sub1_sub1_sub2_1.drawText(s1 + ":", i2, k1, 0);
							i2 += class50_sub1_sub1_sub2_1.getFormattedTextWidth(s1) + 8;
							class50_sub1_sub1_sub2_1.drawText(chatHistory.messages[i1], i2, k1, 0x800000);
						}
						k++;
					}
					if (j1 == 4 && (tradeMode == 0 || tradeMode == 1 && isFriendOrSelf(s1))) {
						if (k1 > 0 && k1 < 110)
							class50_sub1_sub1_sub2_1.drawText(s1 + " " + chatHistory.messages[i1], 4, k1, 0x800080);
						k++;
					}
					if (j1 == 5 && splitPrivateChat == 0 && privateChatMode < 2) {
						if (k1 > 0 && k1 < 110)
							class50_sub1_sub1_sub2_1.drawText(chatHistory.messages[i1], 4, k1, 0x800000);
						k++;
					}
					if (j1 == 6 && splitPrivateChat == 0 && privateChatMode < 2) {
						if (k1 > 0 && k1 < 110) {
							class50_sub1_sub1_sub2_1.drawText("To " + s1 + ":", 4, k1, 0);
							class50_sub1_sub1_sub2_1.drawText(chatHistory.messages[i1],
									12 + class50_sub1_sub1_sub2_1.getFormattedTextWidth("To " + s1), k1, 0x800000);
						}
						k++;
					}
					if (j1 == 8 && (tradeMode == 0 || tradeMode == 1 && isFriendOrSelf(s1))) {
						if (k1 > 0 && k1 < 110)
							class50_sub1_sub1_sub2_1.drawText(s1 + " " + chatHistory.messages[i1], 4, k1, 0x7e3200);
						k++;
					}
				}

			Rasterizer.resetCoordinates();
			chatContentHeight = k * 14 + 7;
			if (chatContentHeight < 78)
				chatContentHeight = 78;
			drawScrollbar(chatContentHeight - chatScrollOffset - 77, 463, 77, chatContentHeight, 0);
			String s;
			if (localPlayer != null && localPlayer.name != null)
				s = localPlayer.name;
			else
				s = TextFormatter.formatDisplayName(loginScreen.username);
			class50_sub1_sub1_sub2_1.drawText(s + ":", 4, 90, 0);
			class50_sub1_sub1_sub2_1.drawText(chatInput + "*",
					6 + class50_sub1_sub1_sub2_1.getFormattedTextWidth(s + ": "), 90, 255);
			Rasterizer.drawHorizontalLine(0, 77, 479, 0);
		}
		if (menuState.open && menuState.screenArea == 2)
			drawContextMenu();
		chatboxBuffer.draw(super.graphics, 17, 357);
		viewportBuffer.bindRaster();
		Rasterizer3D.scanlineOffsets = viewportScanlineOffsets;
		}

	public void method85(int i) {
		for (int j = -1; j < actorSynchronizer.playerCount; j++) {
			int k;
			if (j == -1)
				k = ActorSynchronizer.LOCAL_PLAYER_INDEX;
			else
				k = actorSynchronizer.playerIndices[j];
			Player class50_sub1_sub4_sub3_sub2 = actorSynchronizer.players[k];
			if (class50_sub1_sub4_sub3_sub2 != null
					&& ((Actor) (class50_sub1_sub4_sub3_sub2)).overheadTextCyclesRemaining > 0) {
				class50_sub1_sub4_sub3_sub2.overheadTextCyclesRemaining--;
				if (((Actor) (class50_sub1_sub4_sub3_sub2)).overheadTextCyclesRemaining == 0)
					class50_sub1_sub4_sub3_sub2.overheadText = null;
			}
		}

		for (int l = 0; l < actorSynchronizer.npcCount; l++) {
			int i1 = actorSynchronizer.npcIndices[l];
			Npc class50_sub1_sub4_sub3_sub1 = actorSynchronizer.npcs[i1];
			if (class50_sub1_sub4_sub3_sub1 != null
					&& ((Actor) (class50_sub1_sub4_sub3_sub1)).overheadTextCyclesRemaining > 0) {
				class50_sub1_sub4_sub3_sub1.overheadTextCyclesRemaining--;
				if (((Actor) (class50_sub1_sub4_sub3_sub1)).overheadTextCyclesRemaining == 0)
					class50_sub1_sub4_sub3_sub1.overheadText = null;
			}
		}

	}

	/*
	 * Legacy client.method86(boolean flag): flag -> removed false sentinel.
	 * The supplied startup path intentionally leaves this CRC refresh disabled.
	 */
	private void loadArchiveCrcs() {
		resourceLoader.fetchArchiveCrcs(this::openJaggrabStream, this::drawLoadingText);
	}

	/* Legacy client.method87(int i): i -> removed nonzero division sentinel. */
	private void drawMinimap() {
		MinimapRenderer.Assets assets = new MinimapRenderer.Assets();
		assets.minimapBuffer = minimapBuffer;
		assets.sceneBuffer = viewportBuffer;
		assets.minimapMask = aClass50_Sub1_Sub1_Sub3_1186;
		assets.compass = aClass50_Sub1_Sub1_Sub1_1116;
		assets.compassXOffsets = anIntArray1286;
		assets.compassWidths = anIntArray1180;
		assets.minimapXOffsets = anIntArray920;
		assets.minimapWidths = anIntArray1019;
		assets.sceneScanlineOffsets = viewportScanlineOffsets;
		assets.groundItemDot = aClass50_Sub1_Sub1_Sub1_1192;
		assets.npcDot = aClass50_Sub1_Sub1_Sub1_1193;
		assets.playerDot = aClass50_Sub1_Sub1_Sub1_1194;
		assets.friendDot = aClass50_Sub1_Sub1_Sub1_1195;
		assets.teamDot = aClass50_Sub1_Sub1_Sub1_1196;
		assets.hintMarker = aClass50_Sub1_Sub1_Sub1_1037;
		assets.destinationMarker = aClass50_Sub1_Sub1_Sub1_1036;
		assets.edgeArrow = aClass50_Sub1_Sub1_Sub1_1247;
		minimapRenderer.draw(worldState, actorSynchronizer, localPlayer, currentPlane,
				cameraController.followYaw, destinationX, destinationY, anInt1197, anInt1226,
				anInt844, anInt845, anInt1151, anInt1325, regionManager.baseX, regionManager.baseY,
				assets, name -> isFriendOrSelf(name));
	}


	public String method89(int i, int j) {
		if (j < 8 || j > 8)
			throw new NullPointerException();
		if (i < 0x3b9ac9ff)
			return String.valueOf(i);
		else
			return "*";
	}

	/*
	 * Legacy client.method90(int i, long l):
	 *   i -> removed -916 sentinel
	 *   l -> encodedName
	 */
	public void addIgnore(long encodedName) {
		if (socialManager.addIgnore(encodedName, networkSession.outgoing, this::addChatMessage))
			aBoolean1181 = true;
	}


	public void processGameLoop() {
		if (duplicateClientError || loadingError || invalidHostError)
			return;
		anInt1325++;
		if (!loggedIn)
			processLoginScreenInput();
		else
			method28((byte) 4);
		method77(false);
	}

	/* Legacy client.method91(): rebuild the complete context-menu option list. */
	public void buildContextMenu() {
		if (interfaceState.inventoryDragArea != 0)
			return;
		menuState.reset();
		if (interfaceState.fullscreenInterfaceId != -1) {
			anInt915 = 0;
			anInt1315 = 0;
			buildInterfaceMenu(0, Widget.get(interfaceState.fullscreenInterfaceId), 0, 0, 0, super.mouseX, super.mouseY);
			if (anInt915 != anInt1302)
				anInt1302 = anInt915;
			if (anInt1315 != anInt1129)
				anInt1129 = anInt1315;
			return;
		}
		buildSplitPrivateChatMenu();
		anInt915 = 0;
		anInt1315 = 0;
		if (super.mouseX > 4 && super.mouseY > 4 && super.mouseX < 516 && super.mouseY < 338)
			if (interfaceState.openInterfaceId != -1)
				buildInterfaceMenu(4, Widget.get(interfaceState.openInterfaceId), 0, 0, 4, super.mouseX, super.mouseY);
			else
				buildViewportMenu();
		if (anInt915 != anInt1302)
			anInt1302 = anInt915;
		if (anInt1315 != anInt1129)
			anInt1129 = anInt1315;
		anInt915 = 0;
		anInt1315 = 0;
		if (super.mouseX > 553 && super.mouseY > 205 && super.mouseX < 743 && super.mouseY < 466)
			if (interfaceState.sidebarOverlayInterfaceId != -1)
				buildInterfaceMenu(205, Widget.get(interfaceState.sidebarOverlayInterfaceId), 1, 0, 553, super.mouseX, super.mouseY);
			else if (interfaceState.tabInterfaceIds[interfaceState.selectedTab] != -1)
				buildInterfaceMenu(205, Widget.get(interfaceState.tabInterfaceIds[interfaceState.selectedTab]), 1, 0, 553, super.mouseX, super.mouseY);
		if (anInt915 != anInt1280) {
			aBoolean1181 = true;
			anInt1280 = anInt915;
		}
		if (anInt1315 != anInt1044) {
			aBoolean1181 = true;
			anInt1044 = anInt1315;
		}
		anInt915 = 0;
		anInt1315 = 0;
		if (super.mouseX > 17 && super.mouseY > 357 && super.mouseX < 496 && super.mouseY < 453)
			if (interfaceState.chatboxInterfaceId != -1)
				buildInterfaceMenu(357, Widget.get(interfaceState.chatboxInterfaceId), 2, 0, 17, super.mouseX, super.mouseY);
			else if (interfaceState.dialogueInterfaceId != -1)
				buildInterfaceMenu(357, Widget.get(interfaceState.dialogueInterfaceId), 3, 0, 17, super.mouseX, super.mouseY);
			else if (super.mouseY < 434 && super.mouseX < 426 && anInt1244 == 0)
				buildChatboxMessageMenu(super.mouseY - 357);
		if ((interfaceState.chatboxInterfaceId != -1 || interfaceState.dialogueInterfaceId != -1) && anInt915 != anInt1106) {
			aBoolean1240 = true;
			anInt1106 = anInt915;
		}
		if ((interfaceState.chatboxInterfaceId != -1 || interfaceState.dialogueInterfaceId != -1) && anInt1315 != anInt1284) {
			aBoolean1240 = true;
			anInt1284 = anInt1315;
		}
		menuState.prioritizeActions();

	}

	public static String method92(int i, int j, int k) {
		if (k <= 0)
			throw new NullPointerException();
		int l = j - i;
		if (l < -9)
			return "@red@";
		if (l < -6)
			return "@or3@";
		if (l < -3)
			return "@or2@";
		if (l < 0)
			return "@or1@";
		if (l > 9)
			return "@gre@";
		if (l > 6)
			return "@gr3@";
		if (l > 3)
			return "@gr2@";
		if (l > 0)
			return "@gr1@";
		else
			return "@yel@";
	}

	/*
	 * Legacy client.method94(int i, int j, int k, int l, int i1, int j1, byte byte0)
	 * i -> targetHeight, j -> targetX, k -> pitch, l -> distance,
	 * i1 -> yaw, j1 -> targetY, byte0 -> removed -103 sentinel.
	 */
	private void positionCamera(int targetHeight, int targetX, int pitch, int distance, int yaw, int targetY) {
		cameraController.positionFromTarget(targetHeight, targetX, pitch, distance, yaw, targetY);
	}


	/*
	 * Legacy client.method97(int i, long l):
	 *   i -> removed 325 division sentinel
	 *   l -> encodedName
	 */
	public void removeIgnore(long encodedName) {
		if (socialManager.removeIgnore(encodedName, networkSession.outgoing))
			aBoolean1181 = true;
	}


	/* Legacy client.method98(int i): i -> removed fixed 47 division sentinel. */
	public void drawTitleFlames() {
		char c = '\u0100';
		if (greenFlameTransition > 0) {
			for (int j = 0; j < 256; j++)
				if (greenFlameTransition > 768)
					titleFlamePalette[j] = blendTitleFlameColors(titleFlameRedPalette[j], titleFlameGreenPalette[j], 1024 - greenFlameTransition);
				else if (greenFlameTransition > 256)
					titleFlamePalette[j] = titleFlameGreenPalette[j];
				else
					titleFlamePalette[j] = blendTitleFlameColors(titleFlameGreenPalette[j], titleFlameRedPalette[j], 256 - greenFlameTransition);

		} else if (blueFlameTransition > 0) {
			for (int k = 0; k < 256; k++)
				if (blueFlameTransition > 768)
					titleFlamePalette[k] = blendTitleFlameColors(titleFlameRedPalette[k], titleFlameBluePalette[k], 1024 - blueFlameTransition);
				else if (blueFlameTransition > 256)
					titleFlamePalette[k] = titleFlameBluePalette[k];
				else
					titleFlamePalette[k] = blendTitleFlameColors(titleFlameBluePalette[k], titleFlameRedPalette[k], 256 - blueFlameTransition);

		} else {
			for (int l = 0; l < 256; l++)
				titleFlamePalette[l] = titleFlameRedPalette[l];

		}
		for (int i1 = 0; i1 < 33920; i1++)
			titleLeftFlameBuffer.pixels[i1] = titleLeftFlameBackground.pixels[i1];

		int j1 = 0;
		int k1 = 1152;
		for (int l1 = 1; l1 < c - 1; l1++) {
			int i2 = (titleFlameLineOffsets[l1] * (c - l1)) / c;
			int k2 = 22 + i2;
			if (k2 < 0)
				k2 = 0;
			j1 += k2;
			for (int i3 = k2; i3 < 128; i3++) {
				int k3 = titleFlameIntensity[j1++];
				if (k3 != 0) {
					int i4 = k3;
					int k4 = 256 - k3;
					k3 = titleFlamePalette[k3];
					int i5 = titleLeftFlameBuffer.pixels[k1];
					titleLeftFlameBuffer.pixels[k1++] = ((k3 & 0xff00ff) * i4 + (i5 & 0xff00ff) * k4 & 0xff00ff00)
							+ ((k3 & 0xff00) * i4 + (i5 & 0xff00) * k4 & 0xff0000) >> 8;
				} else {
					k1++;
				}
			}

			k1 += k2;
		}

		titleLeftFlameBuffer.draw(super.graphics, 0, 0);
		for (int j2 = 0; j2 < 33920; j2++)
			titleRightFlameBuffer.pixels[j2] = titleRightFlameBackground.pixels[j2];

		j1 = 0;
		k1 = 1176;
		for (int l2 = 1; l2 < c - 1; l2++) {
			int j3 = (titleFlameLineOffsets[l2] * (c - l2)) / c;
			int l3 = 103 - j3;
			k1 += j3;
			for (int j4 = 0; j4 < l3; j4++) {
				int l4 = titleFlameIntensity[j1++];
				if (l4 != 0) {
					int j5 = l4;
					int k5 = 256 - l4;
					l4 = titleFlamePalette[l4];
					int l5 = titleRightFlameBuffer.pixels[k1];
					titleRightFlameBuffer.pixels[k1++] = ((l4 & 0xff00ff) * j5 + (l5 & 0xff00ff) * k5 & 0xff00ff00)
							+ ((l4 & 0xff00) * j5 + (l5 & 0xff00) * k5 & 0xff0000) >> 8;
				} else {
					k1++;
				}
			}

			j1 += 128 - l3;
			k1 += 128 - l3 - j3;
		}

		titleRightFlameBuffer.draw(super.graphics, 637, 0);
	}


	public static void setLowMemory() {
		Scene.lowMemory = true;
		Rasterizer3D.lowMemory = true;
		lowMemory = true;
		Region.lowMemory = true;
		GameObjectDefinition.lowMemory = true;
	}

	/*
	 * Legacy client.method102(long l, int i):
	 *   l -> encodedName
	 *   i -> removed -45229 sentinel
	 */
	public void addFriend(long encodedName) {
		boolean membersAccount = anInt1068 == 1;
		if (socialManager.addFriend(encodedName, membersAccount, localPlayer.name, networkSession.outgoing, this::addChatMessage))
			aBoolean1181 = true;
	}


	/* Legacy client.method103(Widget class13): class13 -> widget. */
	public void updateWidgetContent(Widget class13) {
		int i = class13.contentType;
		if (i >= 1 && i <= 100 || i >= 701 && i <= 800) {
			if (i == 1 && socialManager.friendListStatus == 0) {
				class13.text = "Loading friend list";
				class13.buttonType = 0;
				return;
			}
			if (i == 1 && socialManager.friendListStatus == 1) {
				class13.text = "Connecting to friendserver";
				class13.buttonType = 0;
				return;
			}
			if (i == 2 && socialManager.friendListStatus != 2) {
				class13.text = "Please wait...";
				class13.buttonType = 0;
				return;
			}
			int j = socialManager.friendCount;
			if (socialManager.friendListStatus != 2)
				j = 0;
			if (i > 700)
				i -= 601;
			else
				i--;
			if (i >= j) {
				class13.text = "";
				class13.buttonType = 0;
				return;
			} else {
				class13.text = socialManager.friendNames[i];
				class13.buttonType = 1;
				return;
			}
		}
		if (i >= 101 && i <= 200 || i >= 801 && i <= 900) {
			int k = socialManager.friendCount;
			if (socialManager.friendListStatus != 2)
				k = 0;
			if (i > 800)
				i -= 701;
			else
				i -= 101;
			if (i >= k) {
				class13.text = "";
				class13.buttonType = 0;
				return;
			}
			if (socialManager.friendWorlds[i] == 0)
				class13.text = "@red@Offline";
			else if (socialManager.friendWorlds[i] < 200) {
				if (socialManager.friendWorlds[i] == currentWorldId)
					class13.text = "@gre@World" + (socialManager.friendWorlds[i] - 9);
				else
					class13.text = "@yel@World" + (socialManager.friendWorlds[i] - 9);
			} else if (socialManager.friendWorlds[i] == currentWorldId)
				class13.text = "@gre@Classic" + (socialManager.friendWorlds[i] - 219);
			else
				class13.text = "@yel@Classic" + (socialManager.friendWorlds[i] - 219);
			class13.buttonType = 1;
			return;
		}
		if (i == 203) {
			int l = socialManager.friendCount;
			if (socialManager.friendListStatus != 2)
				l = 0;
			class13.scrollHeight = l * 15 + 20;
			if (class13.scrollHeight <= class13.height)
				class13.scrollHeight = class13.height + 1;
			return;
		}
		if (i >= 401 && i <= 500) {
			if ((i -= 401) == 0 && socialManager.friendListStatus == 0) {
				class13.text = "Loading ignore list";
				class13.buttonType = 0;
				return;
			}
			if (i == 1 && socialManager.friendListStatus == 0) {
				class13.text = "Please wait...";
				class13.buttonType = 0;
				return;
			}
			int i1 = socialManager.ignoreCount;
			if (socialManager.friendListStatus == 0)
				i1 = 0;
			if (i >= i1) {
				class13.text = "";
				class13.buttonType = 0;
				return;
			} else {
				class13.text = TextFormatter.formatDisplayName(Base37.decode(socialManager.ignoreEncodedNames[i]));
				class13.buttonType = 1;
				return;
			}
		}
		if (i == 503) {
			class13.scrollHeight = socialManager.ignoreCount * 15 + 20;
			if (class13.scrollHeight <= class13.height)
				class13.scrollHeight = class13.height + 1;
			return;
		}
		if (i == 327) {
			class13.modelPitch = 150;
			class13.modelYaw = (int) (Math.sin((double) anInt1325 / 40D) * 256D) & 0x7ff;
			if (aBoolean1277) {
				for (int j1 = 0; j1 < 7; j1++) {
					int i2 = anIntArray1326[j1];
					if (i2 >= 0 && !IdentityKit.definitions[i2].areBodyModelsReady())
						return;
				}

				aBoolean1277 = false;
				Model aclass50_sub1_sub4_sub4[] = new Model[7];
				int j2 = 0;
				for (int k2 = 0; k2 < 7; k2++) {
					int l2 = anIntArray1326[k2];
					if (l2 >= 0)
						aclass50_sub1_sub4_sub4[j2++] = IdentityKit.definitions[l2].buildBodyModel();
				}

				Model class50_sub1_sub4_sub4 = new Model(j2, aclass50_sub1_sub4_sub4);
				for (int i3 = 0; i3 < 5; i3++)
					if (anIntArray1099[i3] != 0) {
						class50_sub1_sub4_sub4.recolor(anIntArrayArray1008[i3][0],
								anIntArrayArray1008[i3][anIntArray1099[i3]]);
						if (i3 == 1)
							class50_sub1_sub4_sub4.recolor(anIntArray1268[0], anIntArray1268[anIntArray1099[i3]]);
					}

				class50_sub1_sub4_sub4.createBones();
				class50_sub1_sub4_sub4.applyTransformation(
						AnimationSequence.sequences[((Actor) (localPlayer)).idleSequence].primaryFrameIds[0]);
				class50_sub1_sub4_sub4.light(64, 850, -30, -50, -30, true);
				class13.mediaType = 5;
				class13.mediaId = 0;
				Widget.cacheModel(5, 0, class50_sub1_sub4_sub4);
			}
			return;
		}
		if (i == 324) {
			if (aClass50_Sub1_Sub1_Sub1_1102 == null) {
				aClass50_Sub1_Sub1_Sub1_1102 = class13.sprite;
				aClass50_Sub1_Sub1_Sub1_1103 = class13.activeSprite;
			}
			if (aBoolean1144) {
				class13.sprite = aClass50_Sub1_Sub1_Sub1_1103;
				return;
			} else {
				class13.sprite = aClass50_Sub1_Sub1_Sub1_1102;
				return;
			}
		}
		if (i == 325) {
			if (aClass50_Sub1_Sub1_Sub1_1102 == null) {
				aClass50_Sub1_Sub1_Sub1_1102 = class13.sprite;
				aClass50_Sub1_Sub1_Sub1_1103 = class13.activeSprite;
			}
			if (aBoolean1144) {
				class13.sprite = aClass50_Sub1_Sub1_Sub1_1102;
				return;
			} else {
				class13.sprite = aClass50_Sub1_Sub1_Sub1_1103;
				return;
			}
		}
		if (i == 600) {
			class13.text = reportAbuseName;
			if (anInt1325 % 20 < 10) {
				class13.text += "|";
				return;
			} else {
				class13.text += " ";
				return;
			}
		}
		if (i == 620)
			if (playerRights >= 1) {
				if (reportAbuseMutePlayer) {
					class13.color = 0xff0000;
					class13.text = "Moderator option: Mute player for 48 hours: <ON>";
				} else {
					class13.color = 0xffffff;
					class13.text = "Moderator option: Mute player for 48 hours: <OFF>";
				}
			} else {
				class13.text = "";
			}
		if (i == 660) {
			int k1 = anInt1170 - anInt1215;
			String s1;
			if (k1 <= 0)
				s1 = "earlier today";
			else if (k1 == 1)
				s1 = "yesterday";
			else
				s1 = k1 + " days ago";
			class13.text = "You last logged in @red@" + s1 + "@bla@ from: @red@" + Signlink.dns;
		}
		if (i == 661)
			if (anInt1034 == 0)
				class13.text = "\\nYou have not yet set any recovery questions.\\nIt is @lre@strongly@yel@ recommended that you do so.\\n\\nIf you don't you will be @lre@unable to recover your\\n@lre@password@yel@ if you forget it, or it is stolen.";
			else if (anInt1034 <= anInt1170) {
				class13.text = "\\n\\nRecovery Questions Last Set:\\n@gre@" + method104(anInt1034, (byte) 83);
			} else {
				int l1 = (anInt1170 + 14) - anInt1034;
				String s2;
				if (l1 <= 0)
					s2 = "Earlier today";
				else if (l1 == 1)
					s2 = "Yesterday";
				else
					s2 = l1 + " days ago";
				class13.text = s2
						+ " you requested@lre@ new recovery\\n@lre@questions.@yel@ The requested change will occur\\non: @lre@"
						+ method104(anInt1034, (byte) 83)
						+ "\\n\\nIf you do not remember making this request\\ncancel it immediately, and change your password.";
			}
		if (i == 662) {
			String s;
			if (unreadMessageCount == 0)
				s = "@yel@0 unread messages";
			else if (unreadMessageCount == 1)
				s = "@gre@1 unread message";
			else
				s = "@gre@" + unreadMessageCount + " unread messages";
			class13.text = "You have " + s + "\\nin your message centre.";
		}
		if (i == 663)
			if (anInt1083 <= 0 || anInt1083 > anInt1170 + 10)
				class13.text = "Last password change:\\n@gre@Never changed";
			else
				class13.text = "Last password change:\\n@gre@" + method104(anInt1083, (byte) 83);
		if (i == 665)
			if (anInt992 > 2 && !membersWorld)
				class13.text = "This is a non-members\\nworld. To enjoy your\\nmembers benefits we\\nrecommend you play on a\\nmembers world instead.";
			else if (anInt992 > 2)
				class13.text = "\\n\\nYou have @gre@" + anInt992 + "@yel@ days of\\nmember credit remaining.";
			else if (anInt992 > 0)
				class13.text = "You have @gre@" + anInt992
						+ "@yel@ days of\\nmember credit remaining.\\n\\n@lre@Credit low! Renew now\\n@lre@to avoid losing members.";
			else
				class13.text = "You are not a member.\\n\\nChoose to subscribe and\\nyou'll get loads of extra\\nbenefits and features.";
		if (i == 667)
			if (anInt992 > 2 && !membersWorld)
				class13.text = "To switch to a members-only world:\\n1) Logout and return to the world selection page.\\n2) Choose one of the members world with a gold star next to it's name.\\n\\nIf you prefer you can continue to use this world,\\nbut members only features will be unavailable here.";
			else if (anInt992 > 0)
				class13.text = "To extend or cancel a subscription:\\n1) Logout and return to the frontpage of this website.\\n2)Choose the relevant option from the 'membership' section.\\n\\nNote: If you are a credit card subscriber a top-up payment will\\nautomatically be taken when 3 days credit remain.\\n(unless you cancel your subscription, which can be done at any time.)";
			else
				class13.text = "To start a subscripton:\\n1) Logout and return to the frontpage of this website.\\n2) Choose 'Start a new subscription'";
		if (i == 668) {
			if (anInt1034 > anInt1170) {
				class13.text = "To cancel this request:\\n1) Logout and return to the frontpage of this website.\\n2) Choose 'Cancel recovery questions'.";
				return;
			}
			class13.text = "To change your recovery questions:\\n1) Logout and return to the frontpage of this website.\\n2) Choose 'Set new recovery questions'.";
		}
	}

	public String method104(int i, byte byte0) {
		if (byte0 != 83)
			networkSession.incomingOpcode = networkSession.incoming.readUnsignedByte();
		if (i > anInt1170 + 10) {
			return "Unknown";
		} else {
			long l = ((long) i + 11745L) * 0x5265c00L;
			Calendar calendar = Calendar.getInstance();
			calendar.setTime(new Date(l));
			int j = calendar.get(5);
			int k = calendar.get(2);
			int i1 = calendar.get(1);
			String as[] = { "Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec" };
			return j + "-" + as[k] + "-" + i1;
		}
	}

	public void method105(int i, int j) {
		int k = Varp.definitions[j].clientCode;
		if (k == 0)
			return;
		int l = varpValues[j];
		if (k == 1) {
			if (l == 1)
				Rasterizer3D.setBrightness(0.90000000000000002D);
			if (l == 2)
				Rasterizer3D.setBrightness(0.80000000000000004D);
			if (l == 3)
				Rasterizer3D.setBrightness(0.69999999999999996D);
			if (l == 4)
				Rasterizer3D.setBrightness(0.59999999999999998D);
			ItemSpriteFactory.clearCache();
			aBoolean1046 = true;
		}
		if (k == 3)
			musicController.applySetting(l, lowMemory, onDemandFetcher::request);
		if (k == 4)
			soundEffectQueue.applySetting(l);
		if (k == 5)
			oneButtonMouseMode = l;
		if (k == 6)
			anInt998 = l;
		if (k == 8) {
			splitPrivateChat = l;
			aBoolean1240 = true;
		}
		if (k == 9)
			anInt955 = l;
	}

	public int blendTitleFlameColors(int i, int j, int k) {
		int i1 = 256 - k;
		return ((i & 0xff00ff) * i1 + (j & 0xff00ff) * k & 0xff00ff00)
				+ ((i & 0xff00) * i1 + (j & 0xff00) * k & 0xff0000) >> 8;
	}

	/* Legacy client.method107(int i): i -> removed negative sentinel. */
	public void updateTutorialIslandFlag() {
		tutorialIslandFlag = 0;
		int j = (((Actor) (localPlayer)).x >> 7) + regionManager.baseX;
		int k = (((Actor) (localPlayer)).y >> 7) + regionManager.baseY;

		if (j >= 3053 && j <= 3156 && k >= 3056 && k <= 3136)
			tutorialIslandFlag = 1;
		if (j >= 3072 && j <= 3118 && k >= 9492 && k <= 9535)
			tutorialIslandFlag = 1;
		if (tutorialIslandFlag == 1 && j >= 3139 && j <= 3199 && k >= 3008 && k <= 3062)
			tutorialIslandFlag = 0;
	}

	/* Legacy client.method108(): compute and open the context-menu rectangle. */
	public void openContextMenu() {
		int j = boldFont.getFormattedTextWidth("Choose Option");
		for (int k = 0; k < menuState.count; k++) {
			int l = boldFont.getFormattedTextWidth(menuState.actionNames[k]);
			if (l > j)
				j = l;
		}

		j += 8;
		int i1 = 15 * menuState.count + 21;
		if (super.clickX > 4 && super.clickY > 4 && super.clickX < 516 && super.clickY < 338) {
			int j1 = super.clickX - 4 - j / 2;
			if (j1 + j > 512)
				j1 = 512 - j;
			if (j1 < 0)
				j1 = 0;
			int i2 = super.clickY - 4;
			if (i2 + i1 > 334)
				i2 = 334 - i1;
			if (i2 < 0)
				i2 = 0;
			menuState.open = true;
			menuState.screenArea = 0;
			menuState.offsetX = j1;
			menuState.offsetY = i2;
			menuState.width = j;
			menuState.height = 15 * menuState.count + 22;
		}
		if (super.clickX > 553 && super.clickY > 205 && super.clickX < 743 && super.clickY < 466) {
			int k1 = super.clickX - 553 - j / 2;
			if (k1 < 0)
				k1 = 0;
			else if (k1 + j > 190)
				k1 = 190 - j;
			int j2 = super.clickY - 205;
			if (j2 < 0)
				j2 = 0;
			else if (j2 + i1 > 261)
				j2 = 261 - i1;
			menuState.open = true;
			menuState.screenArea = 1;
			menuState.offsetX = k1;
			menuState.offsetY = j2;
			menuState.width = j;
			menuState.height = 15 * menuState.count + 22;
		}
		if (super.clickX > 17 && super.clickY > 357 && super.clickX < 496 && super.clickY < 453) {
			int l1 = super.clickX - 17 - j / 2;
			if (l1 < 0)
				l1 = 0;
			else if (l1 + j > 479)
				l1 = 479 - j;
			int k2 = super.clickY - 357;
			if (k2 < 0)
				k2 = 0;
			else if (k2 + i1 > 96)
				k2 = 96 - i1;
			menuState.open = true;
			menuState.screenArea = 2;
			menuState.offsetX = l1;
			menuState.offsetY = k2;
			menuState.width = j;
			menuState.height = 15 * menuState.count + 22;
		}
	}

	public void method109() {
		drawSplitPrivateChat();
		if (anInt1023 == 1)
			aClass50_Sub1_Sub1_Sub1Array896[anInt1022 / 100].drawImage(anInt1020 - 8 - 4, anInt1021 - 8 - 4);
		if (anInt1023 == 2)
			aClass50_Sub1_Sub1_Sub1Array896[4 + anInt1022 / 100].drawImage(anInt1020 - 8 - 4, anInt1021 - 8 - 4);
		if (interfaceState.walkableInterfaceId != -1) {
			widgetRuntime.updateAnimations(anInt951, interfaceState.walkableInterfaceId);
			drawInterface(0, 0, Widget.get(interfaceState.walkableInterfaceId), 0);
		}
		if (interfaceState.openInterfaceId != -1) {
			widgetRuntime.updateAnimations(anInt951, interfaceState.openInterfaceId);
			drawInterface(0, 0, Widget.get(interfaceState.openInterfaceId), 0);
		}
		updateTutorialIslandFlag();
		if (!menuState.open) {
			buildContextMenu();
			drawMenuTooltip();
		} else if (menuState.screenArea == 0)
			drawContextMenu();
		if (anInt1319 == 1)
			aClass50_Sub1_Sub1_Sub1_1086.drawImage(472, 296);
		if (aBoolean868) {
			char c = '\u01FB';
			int k = 20;
			int i1 = 0xffff00;
			if (super.fps < 30 && lowMemory)
				i1 = 0xff0000;
			if (super.fps < 20 && !lowMemory)
				i1 = 0xff0000;
			plainFont.drawRightAlignedText("Fps:" + super.fps, c, k, i1);
			k += 15;
			Runtime runtime = Runtime.getRuntime();
			int j1 = (int) ((runtime.totalMemory() - runtime.freeMemory()) / 1024L);
			i1 = 0xffff00;
			if (j1 > 0x2000000 && lowMemory)
				i1 = 0xff0000;
			if (j1 > 0x4000000 && !lowMemory)
				i1 = 0xff0000;
			plainFont.drawRightAlignedText("Mem:" + j1 + "k", c, k, 0xffff00);
			k += 15;
		}
		if (anInt1057 != 0) {
			int j = anInt1057 / 50;
			int l = j / 60;
			j %= 60;
			if (j < 10)
				plainFont.drawText("System update in: " + l + ":0" + j, 4, 329, 0xffff00);
			else
				plainFont.drawText("System update in: " + l + ":" + j, 4, 329, 0xffff00);
			anInt895++;
			if (anInt895 > 112) {
				anInt895 = 0;
				networkSession.outgoing.writeOpcode(197);
				networkSession.outgoing.writeInt(0);
			}
		}
	}

	public void run() {
		if (titleFlameThreadMode) {
			runTitleFlameLoop();
			return;
		} else {
			super.run();
			return;
		}
	}

	/* Legacy client.method111(int i): i -> removed nonzero division sentinel. */
	public void buildSplitPrivateChatMenu() {
		if (splitPrivateChat == 0)
			return;
		int j = 0;
		if (anInt1057 != 0)
			j = 1;
		for (int k = 0; k < 100; k++)
			if (chatHistory.messages[k] != null) {
				int l = chatHistory.types[k];
				String s = chatHistory.senders[k];
				boolean flag = false;
				if (s != null && s.startsWith("@cr1@")) {
					s = s.substring(5);
					boolean flag1 = true;
				}
				if (s != null && s.startsWith("@cr2@")) {
					s = s.substring(5);
					byte byte0 = 2;
				}
				if ((l == 3 || l == 7) && (l == 7 || privateChatMode == 0 || privateChatMode == 1 && isFriendOrSelf(s))) {
					int i1 = 329 - j * 13;
					if (super.mouseX > 4 && super.mouseY - 4 > i1 - 10 && super.mouseY - 4 <= i1 + 3) {
						int j1 = plainFont.getFormattedTextWidth("From:  " + s + chatHistory.messages[k])
								+ 25;
						if (j1 > 450)
							j1 = 450;
						if (super.mouseX < 4 + j1) {
							if (playerRights >= 1) {
								menuState.actionNames[menuState.count] = "Report abuse @whi@" + s;
								menuState.actionIds[menuState.count] = 2507;
								menuState.count++;
							}
							menuState.actionNames[menuState.count] = "Add ignore @whi@" + s;
							menuState.actionIds[menuState.count] = 2574;
							menuState.count++;
							menuState.actionNames[menuState.count] = "Add friend @whi@" + s;
							menuState.actionIds[menuState.count] = 2762;
							menuState.count++;
						}
					}
					if (++j >= 5)
						return;
				}
				if ((l == 5 || l == 6) && privateChatMode < 2 && ++j >= 5)
					return;
			}

	}

	/*
	 * Legacy client.method113(int i, int j, int k):
	 *   i -> removed nonzero division sentinel
	 *   j -> removed unused mouseX within chatbox
	 *   k -> mouseY within chatbox
	 */
	public void buildChatboxMessageMenu(int mouseY) {
		int l = 0;
		for (int i1 = 0; i1 < 100; i1++) {
			if (chatHistory.messages[i1] == null)
				continue;
			int j1 = chatHistory.types[i1];
			int k1 = (70 - l * 14) + chatScrollOffset + 4;
			if (k1 < -20)
				break;
			String s = chatHistory.senders[i1];
			boolean flag = false;
			if (s != null && s.startsWith("@cr1@")) {
				s = s.substring(5);
				boolean flag1 = true;
			}
			if (s != null && s.startsWith("@cr2@")) {
				s = s.substring(5);
				byte byte0 = 2;
			}
			if (j1 == 0)
				l++;
			if ((j1 == 1 || j1 == 2) && (j1 == 1 || publicChatMode == 0 || publicChatMode == 1 && isFriendOrSelf(s))) {
				if (mouseY > k1 - 14 && mouseY <= k1 && !s.equals(localPlayer.name)) {
					if (playerRights >= 1) {
						menuState.actionNames[menuState.count] = "Report abuse @whi@" + s;
						menuState.actionIds[menuState.count] = 507;
						menuState.count++;
					}
					menuState.actionNames[menuState.count] = "Add ignore @whi@" + s;
					menuState.actionIds[menuState.count] = 574;
					menuState.count++;
					menuState.actionNames[menuState.count] = "Add friend @whi@" + s;
					menuState.actionIds[menuState.count] = 762;
					menuState.count++;
				}
				l++;
			}
			if ((j1 == 3 || j1 == 7) && splitPrivateChat == 0
					&& (j1 == 7 || privateChatMode == 0 || privateChatMode == 1 && isFriendOrSelf(s))) {
				if (mouseY > k1 - 14 && mouseY <= k1) {
					if (playerRights >= 1) {
						menuState.actionNames[menuState.count] = "Report abuse @whi@" + s;
						menuState.actionIds[menuState.count] = 507;
						menuState.count++;
					}
					menuState.actionNames[menuState.count] = "Add ignore @whi@" + s;
					menuState.actionIds[menuState.count] = 574;
					menuState.count++;
					menuState.actionNames[menuState.count] = "Add friend @whi@" + s;
					menuState.actionIds[menuState.count] = 762;
					menuState.count++;
				}
				l++;
			}
			if (j1 == 4 && (tradeMode == 0 || tradeMode == 1 && isFriendOrSelf(s))) {
				if (mouseY > k1 - 14 && mouseY <= k1) {
					menuState.actionNames[menuState.count] = "Accept trade @whi@" + s;
					menuState.actionIds[menuState.count] = 544;
					menuState.count++;
				}
				l++;
			}
			if ((j1 == 5 || j1 == 6) && splitPrivateChat == 0 && privateChatMode < 2)
				l++;
			if (j1 == 8 && (tradeMode == 0 || tradeMode == 1 && isFriendOrSelf(s))) {
				if (mouseY > k1 - 14 && mouseY <= k1) {
					menuState.actionNames[menuState.count] = "Accept challenge @whi@" + s;
					menuState.actionIds[menuState.count] = 695;
					menuState.count++;
				}
				l++;
			}
		}

	}


	/* Legacy client.method115(int i, int j): i -> plane; j -> removed zero sentinel. */
	private void rebuildMinimap(int plane) {
		minimapRenderer.rebuild(worldState, plane, aClass50_Sub1_Sub1_Sub3Array1153,
				aClass50_Sub1_Sub1_Sub1Array1031, viewportBuffer, viewportScanlineOffsets, networkSession.outgoing);
	}


	/* Legacy client.method117(byte byte0): byte0 -> removed fixed aByte956 sentinel. */
	private int selectNormalRenderPlane() {
		return cameraController.selectNormalRenderPlane(worldState, currentPlane, localPlayer, networkSession.outgoing);
	}


	/* Legacy client.method118(int i): i -> removed negative sentinel. */
	private int selectCinematicRenderPlane() {
		return cameraController.selectCinematicRenderPlane(worldState, currentPlane);
	}


	public void startThread(Runnable runnable, int i) {
		if (i > 10)
			i = 10;
		Signlink.startThread(runnable, i);
	}

	/* Legacy client.method119(int i, boolean flag): i -> removed zero sentinel; flag -> localOnly. */
	private void addPlayersToScene(boolean localOnly) {
		sceneEntityRenderer.addPlayers(worldState, actorSynchronizer, localPlayer, currentPlane,
				anInt1325, lowMemory, localOnly);
	}


	/*
	 * Legacy client.method120(int i, int j):
	 *   i -> menuIndex
	 *   j -> removed fixed value 8 sentinel
	 *
	 * Legacy menu array mapping:
	 *   anIntArray982 -> actionCmd1
	 *   anIntArray979 -> actionCmd2
	 *   anIntArray980 -> actionCmd3
	 *   anIntArray981 -> actionId
	 *   aStringArray1184 -> actionName
	 */
	public void dispatchMenuAction(int menuIndex) {
		if (menuIndex < 0)
			return;
		int cmd2 = menuState.actionCmd2[menuIndex];
		int cmd3 = menuState.actionCmd3[menuIndex];
		int actionId = MenuState.normalizeActionId(menuState.actionIds[menuIndex]);
		int cmd1 = menuState.actionCmd1[menuIndex];
		if (anInt1244 != 0 && actionId != MenuState.CANCEL_ACTION) {
			anInt1244 = 0;
			aBoolean1240 = true;
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
		aBoolean1181 = true;
	}

	// Player target actions: 200, 408, 493, 596, 677, 876, 918.
	private void dispatchPlayerMenuAction(int actionId, int cmd1, int cmd2, int cmd3, int menuIndex) {
		if (actionId == 200) {
			Player class50_sub1_sub4_sub3_sub2 = actorSynchronizer.players[cmd1];
			if (class50_sub1_sub4_sub3_sub2 != null) {
				walkTo(false, ((Actor) (class50_sub1_sub4_sub3_sub2)).pathX[0], ((Actor) (class50_sub1_sub4_sub3_sub2)).pathY[0], 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
				anInt1020 = super.clickX;
				anInt1021 = super.clickY;
				anInt1023 = 2;
				anInt1022 = 0;
				networkSession.outgoing.writeOpcode(245);
				networkSession.outgoing.writeShortAddLE(cmd1);
			}
		}
		if (actionId == 876) {
			Player class50_sub1_sub4_sub3_sub2_1 = actorSynchronizer.players[cmd1];
			if (class50_sub1_sub4_sub3_sub2_1 != null) {
				walkTo(false, ((Actor) (class50_sub1_sub4_sub3_sub2_1)).pathX[0], ((Actor) (class50_sub1_sub4_sub3_sub2_1)).pathY[0], 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
				anInt1020 = super.clickX;
				anInt1021 = super.clickY;
				anInt1023 = 2;
				anInt1022 = 0;
				networkSession.outgoing.writeOpcode(45);
				networkSession.outgoing.writeShortAdd(cmd1);
			}
		}
		if (actionId == 677) {
			Player class50_sub1_sub4_sub3_sub2_2 = actorSynchronizer.players[cmd1];
			if (class50_sub1_sub4_sub3_sub2_2 != null) {
				walkTo(false, ((Actor) (class50_sub1_sub4_sub3_sub2_2)).pathX[0], ((Actor) (class50_sub1_sub4_sub3_sub2_2)).pathY[0], 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
				anInt1020 = super.clickX;
				anInt1021 = super.clickY;
				anInt1023 = 2;
				anInt1022 = 0;
				networkSession.outgoing.writeOpcode(116);
				networkSession.outgoing.writeShortLE(cmd1);
			}
		}
		if (actionId == 493) {
			Player class50_sub1_sub4_sub3_sub2_3 = actorSynchronizer.players[cmd1];
			if (class50_sub1_sub4_sub3_sub2_3 != null) {
				walkTo(false, ((Actor) (class50_sub1_sub4_sub3_sub2_3)).pathX[0], ((Actor) (class50_sub1_sub4_sub3_sub2_3)).pathY[0], 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
				anInt1020 = super.clickX;
				anInt1021 = super.clickY;
				anInt1023 = 2;
				anInt1022 = 0;
				networkSession.outgoing.writeOpcode(233);
				networkSession.outgoing.writeShortAdd(cmd1);
			}
		}
		if (actionId == 918) {
			Player class50_sub1_sub4_sub3_sub2_4 = actorSynchronizer.players[cmd1];
			if (class50_sub1_sub4_sub3_sub2_4 != null) {
				walkTo(false, ((Actor) (class50_sub1_sub4_sub3_sub2_4)).pathX[0], ((Actor) (class50_sub1_sub4_sub3_sub2_4)).pathY[0], 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
				anInt1020 = super.clickX;
				anInt1021 = super.clickY;
				anInt1023 = 2;
				anInt1022 = 0;
				networkSession.outgoing.writeOpcode(31);
				networkSession.outgoing.writeShort(cmd1);
				networkSession.outgoing.writeShortLE(anInt1172);
			}
		}
		if (actionId == 596) {
			Player class50_sub1_sub4_sub3_sub2_5 = actorSynchronizer.players[cmd1];
			if (class50_sub1_sub4_sub3_sub2_5 != null) {
				walkTo(false, ((Actor) (class50_sub1_sub4_sub3_sub2_5)).pathX[0], ((Actor) (class50_sub1_sub4_sub3_sub2_5)).pathY[0], 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
				anInt1020 = super.clickX;
				anInt1021 = super.clickY;
				anInt1023 = 2;
				anInt1022 = 0;
				networkSession.outgoing.writeOpcode(143);
				networkSession.outgoing.writeShortLE(anInt1149);
				networkSession.outgoing.writeShortAddLE(interfaceState.selectedItemSlot);
				networkSession.outgoing.writeShort(interfaceState.selectedItemWidgetId);
				networkSession.outgoing.writeShortAdd(cmd1);
			}
		}
		if (actionId == 408) {
			Player class50_sub1_sub4_sub3_sub2_6 = actorSynchronizer.players[cmd1];
			if (class50_sub1_sub4_sub3_sub2_6 != null) {
				walkTo(false, ((Actor) (class50_sub1_sub4_sub3_sub2_6)).pathX[0], ((Actor) (class50_sub1_sub4_sub3_sub2_6)).pathY[0], 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
				anInt1020 = super.clickX;
				anInt1021 = super.clickY;
				anInt1023 = 2;
				anInt1022 = 0;
				networkSession.outgoing.writeOpcode(194);
				networkSession.outgoing.writeShortLE(cmd1);
			}
		}
	}

	// NPC target actions: 67, 118, 318, 347, 432, 553, 921, 1668.
	private void dispatchNpcMenuAction(int actionId, int cmd1, int cmd2, int cmd3, int menuIndex) {
		if (actionId == 921) {
			Npc class50_sub1_sub4_sub3_sub1 = actorSynchronizer.npcs[cmd1];
			if (class50_sub1_sub4_sub3_sub1 != null) {
				walkTo(false, ((Actor) (class50_sub1_sub4_sub3_sub1)).pathX[0], ((Actor) (class50_sub1_sub4_sub3_sub1)).pathY[0], 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
				anInt1020 = super.clickX;
				anInt1021 = super.clickY;
				anInt1023 = 2;
				anInt1022 = 0;
				networkSession.outgoing.writeOpcode(67);
				networkSession.outgoing.writeShortAdd(cmd1);
			}
		}
		if (actionId == 553) {
			Npc class50_sub1_sub4_sub3_sub1_1 = actorSynchronizer.npcs[cmd1];
			if (class50_sub1_sub4_sub3_sub1_1 != null) {
				walkTo(false, ((Actor) (class50_sub1_sub4_sub3_sub1_1)).pathX[0], ((Actor) (class50_sub1_sub4_sub3_sub1_1)).pathY[0], 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
				anInt1020 = super.clickX;
				anInt1021 = super.clickY;
				anInt1023 = 2;
				anInt1022 = 0;
				networkSession.outgoing.writeOpcode(42);
				networkSession.outgoing.writeShortLE(cmd1);
			}
		}
		if (actionId == 347) {
			Npc class50_sub1_sub4_sub3_sub1_2 = actorSynchronizer.npcs[cmd1];
			if (class50_sub1_sub4_sub3_sub1_2 != null) {
				walkTo(false, ((Actor) (class50_sub1_sub4_sub3_sub1_2)).pathX[0], ((Actor) (class50_sub1_sub4_sub3_sub1_2)).pathY[0], 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
				anInt1020 = super.clickX;
				anInt1021 = super.clickY;
				anInt1023 = 2;
				anInt1022 = 0;
				networkSession.outgoing.writeOpcode(57);
				networkSession.outgoing.writeShort(cmd1);
				networkSession.outgoing.writeShortLE(anInt1149);
				networkSession.outgoing.writeShortAddLE(interfaceState.selectedItemWidgetId);
				networkSession.outgoing.writeShort(interfaceState.selectedItemSlot);
			}
		}
		if (actionId == 118) {
			Npc class50_sub1_sub4_sub3_sub1_3 = actorSynchronizer.npcs[cmd1];
			if (class50_sub1_sub4_sub3_sub1_3 != null) {
				walkTo(false, ((Actor) (class50_sub1_sub4_sub3_sub1_3)).pathX[0], ((Actor) (class50_sub1_sub4_sub3_sub1_3)).pathY[0], 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
				anInt1020 = super.clickX;
				anInt1021 = super.clickY;
				anInt1023 = 2;
				anInt1022 = 0;
				anInt1235 += cmd1;
				if (anInt1235 >= 143) {
					networkSession.outgoing.writeOpcode(157);
					networkSession.outgoing.writeInt(0);
					anInt1235 = 0;
				}
				networkSession.outgoing.writeOpcode(13);
				networkSession.outgoing.writeShortAddLE(cmd1);
			}
		}
		if (actionId == 432) {
			Npc class50_sub1_sub4_sub3_sub1_4 = actorSynchronizer.npcs[cmd1];
			if (class50_sub1_sub4_sub3_sub1_4 != null) {
				walkTo(false, ((Actor) (class50_sub1_sub4_sub3_sub1_4)).pathX[0], ((Actor) (class50_sub1_sub4_sub3_sub1_4)).pathY[0], 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
				anInt1020 = super.clickX;
				anInt1021 = super.clickY;
				anInt1023 = 2;
				anInt1022 = 0;
				networkSession.outgoing.writeOpcode(8);
				networkSession.outgoing.writeShortLE(cmd1);
			}
		}
		if (actionId == 67) {
			Npc class50_sub1_sub4_sub3_sub1_5 = actorSynchronizer.npcs[cmd1];
			if (class50_sub1_sub4_sub3_sub1_5 != null) {
				walkTo(false, ((Actor) (class50_sub1_sub4_sub3_sub1_5)).pathX[0], ((Actor) (class50_sub1_sub4_sub3_sub1_5)).pathY[0], 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
				anInt1020 = super.clickX;
				anInt1021 = super.clickY;
				anInt1023 = 2;
				anInt1022 = 0;
				networkSession.outgoing.writeOpcode(104);
				networkSession.outgoing.writeShortAdd(anInt1172);
				networkSession.outgoing.writeShortLE(cmd1);
			}
		}
		if (actionId == 1668) {
			Npc class50_sub1_sub4_sub3_sub1_6 = actorSynchronizer.npcs[cmd1];
			if (class50_sub1_sub4_sub3_sub1_6 != null) {
				NpcDefinition class37 = class50_sub1_sub4_sub3_sub1_6.definition;
				if (class37.morphIds != null)
					class37 = class37.transform();
				if (class37 != null) {
					String s10;
					if (class37.description != null)
						s10 = new String(class37.description);
					else
						s10 = "It's a " + class37.name + ".";
					addChatMessage("", s10, 0);
				}
			}
		}
		if (actionId == 318) {
			Npc class50_sub1_sub4_sub3_sub1_7 = actorSynchronizer.npcs[cmd1];
			if (class50_sub1_sub4_sub3_sub1_7 != null) {
				walkTo(false, ((Actor) (class50_sub1_sub4_sub3_sub1_7)).pathX[0], ((Actor) (class50_sub1_sub4_sub3_sub1_7)).pathY[0], 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
				anInt1020 = super.clickX;
				anInt1021 = super.clickY;
				anInt1023 = 2;
				anInt1022 = 0;
				networkSession.outgoing.writeOpcode(112);
				networkSession.outgoing.writeShortLE(cmd1);
			}
		}
	}

	// Game-object actions: 35, 376, 389, 467, 888, 892, 1280, 1412.
	private void dispatchObjectMenuAction(int actionId, int cmd1, int cmd2, int cmd3, int menuIndex) {
		if (actionId == 467 && walkToGameObject(cmd3, cmd2, cmd1)) {
			networkSession.outgoing.writeOpcode(152);
			networkSession.outgoing.writeShortLE(cmd1 >> 14 & 0x7fff);
			networkSession.outgoing.writeShortLE(interfaceState.selectedItemWidgetId);
			networkSession.outgoing.writeShortLE(anInt1149);
			networkSession.outgoing.writeShortLE(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShort(interfaceState.selectedItemSlot);
			networkSession.outgoing.writeShortAddLE(cmd2 + regionManager.baseX);
		}
		if (actionId == 376 && walkToGameObject(cmd3, cmd2, cmd1)) {
			networkSession.outgoing.writeOpcode(210);
			networkSession.outgoing.writeShort(anInt1172);
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
			int k1 = cmd1 >> 14 & 0x7fff;
			GameObjectDefinition class47 = GameObjectDefinition.lookup(k1);
			String s9;
			if (class47.description != null)
				s9 = new String(class47.description);
			else
				s9 = "It's a " + class47.name + ".";
			addChatMessage("", s9, 0);
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
	private void dispatchGroundItemMenuAction(int actionId, int cmd1, int cmd2, int cmd3, int menuIndex) {
		if (actionId == 930) {
			boolean flag = walkTo(false, cmd2, cmd3, 0, 0, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			if (!flag)
				flag = walkTo(false, cmd2, cmd3, 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			anInt1020 = super.clickX;
			anInt1021 = super.clickY;
			anInt1023 = 2;
			anInt1022 = 0;
			networkSession.outgoing.writeOpcode(54);
			networkSession.outgoing.writeShortAdd(cmd1);
			networkSession.outgoing.writeShortLE(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShort(cmd2 + regionManager.baseX);
		}
		if (actionId == 68) {
			boolean flag1 = walkTo(false, cmd2, cmd3, 0, 0, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			if (!flag1)
				flag1 = walkTo(false, cmd2, cmd3, 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			anInt1020 = super.clickX;
			anInt1021 = super.clickY;
			anInt1023 = 2;
			anInt1022 = 0;
			networkSession.outgoing.writeOpcode(77);
			networkSession.outgoing.writeShortAdd(cmd2 + regionManager.baseX);
			networkSession.outgoing.writeShort(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShortAddLE(cmd1);
		}
		if (actionId == 684) {
			boolean flag2 = walkTo(false, cmd2, cmd3, 0, 0, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			if (!flag2)
				flag2 = walkTo(false, cmd2, cmd3, 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			anInt1020 = super.clickX;
			anInt1021 = super.clickY;
			anInt1023 = 2;
			anInt1022 = 0;
			if ((cmd1 & 3) == 0)
				anInt1052++;
			if (anInt1052 >= 84) {
				networkSession.outgoing.writeOpcode(222);
				networkSession.outgoing.writeMedium(0xabc842);
				anInt1052 = 0;
			}
			networkSession.outgoing.writeOpcode(71);
			networkSession.outgoing.writeShortAddLE(cmd1);
			networkSession.outgoing.writeShortAddLE(cmd2 + regionManager.baseX);
			networkSession.outgoing.writeShortAdd(cmd3 + regionManager.baseY);
		}
		if (actionId == 270) {
			boolean flag3 = walkTo(false, cmd2, cmd3, 0, 0, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			if (!flag3)
				flag3 = walkTo(false, cmd2, cmd3, 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			anInt1020 = super.clickX;
			anInt1021 = super.clickY;
			anInt1023 = 2;
			anInt1022 = 0;
			networkSession.outgoing.writeOpcode(230);
			networkSession.outgoing.writeShortLE(cmd1);
			networkSession.outgoing.writeShortAdd(cmd2 + regionManager.baseX);
			networkSession.outgoing.writeShort(cmd3 + regionManager.baseY);
		}
		if (actionId == 100) {
			boolean flag4 = walkTo(false, cmd2, cmd3, 0, 0, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			if (!flag4)
				flag4 = walkTo(false, cmd2, cmd3, 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			anInt1020 = super.clickX;
			anInt1021 = super.clickY;
			anInt1023 = 2;
			anInt1022 = 0;
			networkSession.outgoing.writeOpcode(211);
			networkSession.outgoing.writeShortAddLE(interfaceState.selectedItemSlot);
			networkSession.outgoing.writeShortAdd(anInt1149);
			networkSession.outgoing.writeShortAddLE(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShortAddLE(cmd2 + regionManager.baseX);
			networkSession.outgoing.writeShortLE(interfaceState.selectedItemWidgetId);
			networkSession.outgoing.writeShortLE(cmd1);
		}
		if (actionId == 26) {
			boolean flag5 = walkTo(false, cmd2, cmd3, 0, 0, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			if (!flag5)
				flag5 = walkTo(false, cmd2, cmd3, 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			anInt1020 = super.clickX;
			anInt1021 = super.clickY;
			anInt1023 = 2;
			anInt1022 = 0;
			anInt1100++;
			if (anInt1100 >= 120) {
				networkSession.outgoing.writeOpcode(95);
				networkSession.outgoing.writeInt(0);
				anInt1100 = 0;
			}
			networkSession.outgoing.writeOpcode(100);
			networkSession.outgoing.writeShort(cmd2 + regionManager.baseX);
			networkSession.outgoing.writeShortAdd(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShortAddLE(cmd1);
		}
		if (actionId == 199) {
			boolean flag6 = walkTo(false, cmd2, cmd3, 0, 0, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			if (!flag6)
				flag6 = walkTo(false, cmd2, cmd3, 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
			anInt1020 = super.clickX;
			anInt1021 = super.clickY;
			anInt1023 = 2;
			anInt1022 = 0;
			networkSession.outgoing.writeOpcode(83);
			networkSession.outgoing.writeShortLE(cmd1);
			networkSession.outgoing.writeShort(cmd3 + regionManager.baseY);
			networkSession.outgoing.writeShortLE(anInt1172);
			networkSession.outgoing.writeShortAddLE(cmd2 + regionManager.baseX);
		}
		if (actionId == 1564) {
			ItemDefinition class16_1 = ItemDefinition.lookup(cmd1);
			String s6;
			if (class16_1.description != null)
				s6 = new String(class16_1.description);
			else
				s6 = "It's a " + class16_1.name + ".";
			addChatMessage("", s6, 0);
		}
	}


	private void markInventoryInteraction(int widgetId, int slot) {
		anInt1329 = 0;
		interfaceState.pressedInventoryWidgetId = widgetId;
		interfaceState.pressedInventorySlot = slot;
		interfaceState.pressedInventoryArea = 2;
		if (Widget.get(widgetId).parentId == interfaceState.openInterfaceId)
			interfaceState.pressedInventoryArea = 1;
		if (Widget.get(widgetId).parentId == interfaceState.chatboxInterfaceId)
			interfaceState.pressedInventoryArea = 3;
	}

	// Inventory/item actions retain their original packet/action IDs.
	private boolean dispatchInventoryMenuAction(int actionId, int cmd1, int cmd2, int cmd3, int menuIndex) {
		if (actionId == 227) {
			anInt1165++;
			if (anInt1165 >= 62) {
				networkSession.outgoing.writeOpcode(165);
				networkSession.outgoing.writeByte(206);
				anInt1165 = 0;
			}
			networkSession.outgoing.writeOpcode(228);
			networkSession.outgoing.writeShortLE(cmd2);
			networkSession.outgoing.writeShortAdd(cmd1);
			networkSession.outgoing.writeShort(cmd3);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == 961) {
			anInt1139 += cmd1;
			if (anInt1139 >= 115) {
				networkSession.outgoing.writeOpcode(126);
				networkSession.outgoing.writeByte(125);
				anInt1139 = 0;
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
			networkSession.outgoing.writeShortLE(anInt1149);
			networkSession.outgoing.writeShortAddLE(interfaceState.selectedItemWidgetId);
			networkSession.outgoing.writeShortAdd(cmd2);
			networkSession.outgoing.writeShortAdd(cmd3);
			markInventoryInteraction(cmd3, cmd2);
		}
		if (actionId == 361) {
			networkSession.outgoing.writeOpcode(36);
			networkSession.outgoing.writeShort(anInt1172);
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
			ItemDefinition class16 = ItemDefinition.lookup(cmd1);
			Widget class13_4 = Widget.get(cmd3);
			String s5;
			if (class13_4 != null && class13_4.itemAmounts[cmd2] >= 0x186a0)
				s5 = class13_4.itemAmounts[cmd2] + " x " + class16.name;
			else if (class16.description != null)
				s5 = new String(class16.description);
			else
				s5 = "It's a " + class16.name + ".";
			addChatMessage("", s5, 0);
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
			anInt1149 = cmd1;
			interfaceState.selectedItemName = String.valueOf(ItemDefinition.lookup(cmd1).name);
			interfaceState.spellSelected = 0;
			aBoolean1181 = true;
			return true;
		}
		return false;
	}

	// Widget/button actions, including spell selection and CS1 varp buttons.
	private boolean dispatchWidgetMenuAction(int actionId, int cmd1, int cmd2, int cmd3, int menuIndex) {
		if (actionId == 890) {
			networkSession.outgoing.writeOpcode(79);
			networkSession.outgoing.writeShort(cmd3);
			Widget class13 = Widget.get(cmd3);
			if (class13.cs1Instructions != null && class13.cs1Instructions[0][0] == 5) {
				int i2 = class13.cs1Instructions[0][1];
				varpValues[i2] = 1 - varpValues[i2];
				method105(0, i2);
				aBoolean1181 = true;
			}
		}
		if (actionId == 639)
			method15(false);
		if (actionId == 70) {
			Widget class13_1 = Widget.get(cmd3);
			interfaceState.spellSelected = 1;
			anInt1172 = cmd3;
			interfaceState.selectedSpellTargetMask = class13_1.spellUsableOn;
			interfaceState.itemSelected = 0;
			aBoolean1181 = true;
			String s4 = class13_1.selectedActionName;
			if (s4.indexOf(" ") != -1)
				s4 = s4.substring(0, s4.indexOf(" "));
			String s8 = class13_1.selectedActionName;
			if (s8.indexOf(" ") != -1)
				s8 = s8.substring(s8.indexOf(" ") + 1);
			interfaceState.selectedSpellAction = s4 + " " + class13_1.spellName + " " + s8;
			if (interfaceState.selectedSpellTargetMask == 16) {
				aBoolean1181 = true;
				aBoolean950 = true;
			}
			return true;
		}
		if (actionId == 352) {
			Widget class13_2 = Widget.get(cmd3);
			boolean flag7 = true;
			if (class13_2.contentType > 0)
				flag7 = handleWidgetContentAction(class13_2);
			if (flag7) {
				networkSession.outgoing.writeOpcode(79);
				networkSession.outgoing.writeShort(cmd3);
			}
		}
		if (actionId == 575 && !aBoolean1239) {
			networkSession.outgoing.writeOpcode(226);
			networkSession.outgoing.writeShort(cmd3);
			aBoolean1239 = true;
		}
		if (actionId == 518) {
			networkSession.outgoing.writeOpcode(79);
			networkSession.outgoing.writeShort(cmd3);
			Widget class13_3 = Widget.get(cmd3);
			if (class13_3.cs1Instructions != null && class13_3.cs1Instructions[0][0] == 5) {
				int i3 = class13_3.cs1Instructions[0][1];
				if (varpValues[i3] != class13_3.cs1ComparisonValues[0]) {
					varpValues[i3] = class13_3.cs1ComparisonValues[0];
					method105(0, i3);
					aBoolean1181 = true;
				}
			}
		}
		if (actionId == 55) {
			unloadInterface(interfaceState.dialogueInterfaceId);
			aBoolean1240 = true;
		}
		return false;
	}

	// Friend/ignore/message/report actions plus name-based player targeting.
	private void dispatchSocialMenuAction(int actionId, int cmd1, int cmd2, int cmd3, int menuIndex) {
		if (actionId == 762 || actionId == 574 || actionId == 775 || actionId == 859) {
			String s = menuState.actionNames[menuIndex];
			int l1 = s.indexOf("@whi@");
			if (l1 != -1) {
				long l3 = Base37.encode(s.substring(l1 + 5).trim());
				if (actionId == 762)
					addFriend(l3);
				if (actionId == 574)
					addIgnore(l3);
				if (actionId == 775)
					removeFriend(l3);
				if (actionId == 859)
					removeIgnore(l3);
			}
		}
		if (actionId == 544 || actionId == 695) {
			String s1 = menuState.actionNames[menuIndex];
			int j2 = s1.indexOf("@whi@");
			if (j2 != -1) {
				s1 = s1.substring(j2 + 5).trim();
				String s7 = TextFormatter.formatDisplayName(Base37.decode(Base37.encode(s1)));
				boolean flag8 = false;
				for (int j3 = 0; j3 < actorSynchronizer.playerCount; j3++) {
					Player class50_sub1_sub4_sub3_sub2_7 = actorSynchronizer.players[actorSynchronizer.playerIndices[j3]];
					if (class50_sub1_sub4_sub3_sub2_7 == null || class50_sub1_sub4_sub3_sub2_7.name == null
							|| !class50_sub1_sub4_sub3_sub2_7.name.equalsIgnoreCase(s7))
						continue;
					walkTo(false, ((Actor) (class50_sub1_sub4_sub3_sub2_7)).pathX[0], ((Actor) (class50_sub1_sub4_sub3_sub2_7)).pathY[0], 1, 1, MovementPacketEncoder.INTERACTION, 0, 0, 0);
					if (actionId == 544) {
						networkSession.outgoing.writeOpcode(116);
						networkSession.outgoing.writeShortLE(actorSynchronizer.playerIndices[j3]);
					}
					if (actionId == 695) {
						networkSession.outgoing.writeOpcode(245);
						networkSession.outgoing.writeShortAddLE(actorSynchronizer.playerIndices[j3]);
					}
					flag8 = true;
					break;
				}

				if (!flag8)
					addChatMessage("", "Unable to find " + s7, 0);
			}
		}
		if (actionId == 507) {
			String s2 = menuState.actionNames[menuIndex];
			int k2 = s2.indexOf("@whi@");
			if (k2 != -1)
				if (interfaceState.openInterfaceId == -1) {
					method15(false);
					reportAbuseName = s2.substring(k2 + 5).trim();
					reportAbuseMutePlayer = false;
					interfaceState.reportAbuseInterfaceId = interfaceState.openInterfaceId = Widget.reportAbuseInterfaceId;
				} else {
					addChatMessage("", "Please close the interface you have open before using 'report abuse'", 0);
				}
		}
		if (actionId == 984) {
			String s3 = menuState.actionNames[menuIndex];
			int l2 = s3.indexOf("@whi@");
			if (l2 != -1) {
				long l4 = Base37.encode(s3.substring(l2 + 5).trim());
				int k3 = socialManager.findFriendIndex(l4);

				if (k3 != -1 && socialManager.friendWorlds[k3] > 0) {
					aBoolean1240 = true;
					anInt1244 = 0;
					messagePromptRaised = true;
					promptInput = "";
					promptAction = 3;
					privateMessageTarget = socialManager.friendEncodedNames[k3];
					promptMessage = "Enter message to send to " + socialManager.friendNames[k3];
				}
			}
		}
	}

	private void dispatchMiscMenuAction(int actionId, int cmd1, int cmd2, int cmd3, int menuIndex) {
		if (actionId == 14)
			if (!menuState.open)
				worldState.scene.setClick(super.clickX - 4, super.clickY - 4);
			else
				worldState.scene.setClick(cmd2 - 4, cmd3 - 4);
	}



	public void method121(boolean flag) {
		anInt939 = 0;
		for (int i = -1; i < actorSynchronizer.playerCount + actorSynchronizer.npcCount; i++) {
			Object obj;
			if (i == -1)
				obj = localPlayer;
			else if (i < actorSynchronizer.playerCount)
				obj = actorSynchronizer.players[actorSynchronizer.playerIndices[i]];
			else
				obj = actorSynchronizer.npcs[actorSynchronizer.npcIndices[i - actorSynchronizer.playerCount]];
			if (obj == null || !((Actor) (obj)).isVisible())
				continue;
			if (obj instanceof Npc) {
				NpcDefinition class37 = ((Npc) obj).definition;
				if (class37.morphIds != null)
					class37 = class37.transform();
				if (class37 == null)
					continue;
			}
			if (i < actorSynchronizer.playerCount) {
				int k = 30;
				Player class50_sub1_sub4_sub3_sub2 = (Player) obj;
				if (class50_sub1_sub4_sub3_sub2.skullIcon != -1 || class50_sub1_sub4_sub3_sub2.prayerIcon != -1) {
					projectActorToScreen((Actor) obj, ((Actor) obj).height + 15);
					if (projectedX > -1) {
						if (class50_sub1_sub4_sub3_sub2.skullIcon != -1) {
							aClass50_Sub1_Sub1_Sub1Array1288[class50_sub1_sub4_sub3_sub2.skullIcon]
									.drawImage(projectedX - 12, projectedY - k);
							k += 25;
						}
						if (class50_sub1_sub4_sub3_sub2.prayerIcon != -1) {
							aClass50_Sub1_Sub1_Sub1Array1079[class50_sub1_sub4_sub3_sub2.prayerIcon]
									.drawImage(projectedX - 12, projectedY - k);
							k += 25;
						}
					}
				}
				if (i >= 0 && anInt1197 == 10 && anInt1151 == actorSynchronizer.playerIndices[i]) {
					projectActorToScreen((Actor) obj, ((Actor) obj).height + 15);
					if (projectedX > -1)
						aClass50_Sub1_Sub1_Sub1Array954[1].drawImage(projectedX - 12, projectedY - k);
				}
			} else {
				NpcDefinition class37_1 = ((Npc) obj).definition;
				if (class37_1.prayerIcon >= 0 && class37_1.prayerIcon < aClass50_Sub1_Sub1_Sub1Array1079.length) {
					projectActorToScreen((Actor) obj, ((Actor) obj).height + 15);
					if (projectedX > -1)
						aClass50_Sub1_Sub1_Sub1Array1079[class37_1.prayerIcon].drawImage(projectedX - 12, projectedY - 30);
				}
				if (anInt1197 == 1 && anInt1226 == actorSynchronizer.npcIndices[i - actorSynchronizer.playerCount] && anInt1325 % 20 < 10) {
					projectActorToScreen((Actor) obj, ((Actor) obj).height + 15);
					if (projectedX > -1)
						aClass50_Sub1_Sub1_Sub1Array954[0].drawImage(projectedX - 12, projectedY - 28);
				}
			}
			if (((Actor) (obj)).overheadText != null && (i >= actorSynchronizer.playerCount || publicChatMode == 0 || publicChatMode == 3
					|| publicChatMode == 1 && isFriendOrSelf(((Player) obj).name))) {
				projectActorToScreen((Actor) obj, ((Actor) obj).height);
				if (projectedX > -1 && anInt939 < anInt940) {
					anIntArray944[anInt939] = boldFont.getTextWidth(((Actor) (obj)).overheadText)
							/ 2;
					anIntArray943[anInt939] = boldFont.lineHeight;
					anIntArray941[anInt939] = projectedX;
					anIntArray942[anInt939] = projectedY;
					anIntArray945[anInt939] = ((Actor) (obj)).overheadTextColor;
					anIntArray946[anInt939] = ((Actor) (obj)).overheadTextEffect;
					anIntArray947[anInt939] = ((Actor) (obj)).overheadTextCyclesRemaining;
					aStringArray948[anInt939++] = ((Actor) (obj)).overheadText;
					if (anInt998 == 0 && ((Actor) (obj)).overheadTextEffect >= 1
							&& ((Actor) (obj)).overheadTextEffect <= 3) {
						anIntArray943[anInt939] += 10;
						anIntArray942[anInt939] += 5;
					}
					if (anInt998 == 0 && ((Actor) (obj)).overheadTextEffect == 4)
						anIntArray944[anInt939] = 60;
					if (anInt998 == 0 && ((Actor) (obj)).overheadTextEffect == 5)
						anIntArray943[anInt939] += 5;
				}
			}
			if (((Actor) (obj)).healthBarCycle > anInt1325) {
				projectActorToScreen((Actor) obj, ((Actor) obj).height + 15);
				if (projectedX > -1) {
					int l = (((Actor) (obj)).currentHealth * 30) / ((Actor) (obj)).maxHealth;
					if (l > 30)
						l = 30;
					Rasterizer.drawFilledRectangle(projectedX - 15, projectedY - 3, l, 5, 65280);
					Rasterizer.drawFilledRectangle((projectedX - 15) + l, projectedY - 3, 30 - l, 5, 0xff0000);
				}
			}
			for (int i1 = 0; i1 < 4; i1++)
				if (((Actor) (obj)).hitCycles[i1] > anInt1325) {
					projectActorToScreen((Actor) obj, ((Actor) obj).height / 2);
					if (projectedX > -1) {
						if (i1 == 1)
							projectedY -= 20;
						if (i1 == 2) {
							projectedX -= 15;
							projectedY -= 10;
						}
						if (i1 == 3) {
							projectedX += 15;
							projectedY -= 10;
						}
						aClass50_Sub1_Sub1_Sub1Array1182[((Actor) (obj)).hitTypes[i1]].drawImage(projectedX - 12,
								projectedY - 12);
						smallFont.drawCenteredText(String.valueOf(((Actor) (obj)).hitDamages[i1]),
								projectedX, projectedY + 4, 0);
						smallFont.drawCenteredText(String.valueOf(((Actor) (obj)).hitDamages[i1]),
								projectedX - 1, projectedY + 3, 0xffffff);
					}
				}

		}

		for (int j = 0; j < anInt939; j++) {
			int j1 = anIntArray941[j];
			int k1 = anIntArray942[j];
			int l1 = anIntArray944[j];
			int i2 = anIntArray943[j];
			boolean flag1 = true;
			while (flag1) {
				flag1 = false;
				for (int j2 = 0; j2 < j; j2++)
					if (k1 + 2 > anIntArray942[j2] - anIntArray943[j2] && k1 - i2 < anIntArray942[j2] + 2
							&& j1 - l1 < anIntArray941[j2] + anIntArray944[j2]
							&& j1 + l1 > anIntArray941[j2] - anIntArray944[j2]
							&& anIntArray942[j2] - anIntArray943[j2] < k1) {
						k1 = anIntArray942[j2] - anIntArray943[j2];
						flag1 = true;
					}

			}
			projectedX = anIntArray941[j];
			projectedY = anIntArray942[j] = k1;
			String s = aStringArray948[j];
			if (anInt998 == 0) {
				int k2 = 0xffff00;
				if (anIntArray945[j] < 6)
					k2 = anIntArray842[anIntArray945[j]];
				if (anIntArray945[j] == 6)
					k2 = sceneEntityRenderer.getRenderCycle() % 20 >= 10 ? 0xffff00 : 0xff0000;
				if (anIntArray945[j] == 7)
					k2 = sceneEntityRenderer.getRenderCycle() % 20 >= 10 ? 65535 : 255;
				if (anIntArray945[j] == 8)
					k2 = sceneEntityRenderer.getRenderCycle() % 20 >= 10 ? 0x80ff80 : 45056;
				if (anIntArray945[j] == 9) {
					int l2 = 150 - anIntArray947[j];
					if (l2 < 50)
						k2 = 0xff0000 + 1280 * l2;
					else if (l2 < 100)
						k2 = 0xffff00 - 0x50000 * (l2 - 50);
					else if (l2 < 150)
						k2 = 65280 + 5 * (l2 - 100);
				}
				if (anIntArray945[j] == 10) {
					int i3 = 150 - anIntArray947[j];
					if (i3 < 50)
						k2 = 0xff0000 + 5 * i3;
					else if (i3 < 100)
						k2 = 0xff00ff - 0x50000 * (i3 - 50);
					else if (i3 < 150)
						k2 = (255 + 0x50000 * (i3 - 100)) - 5 * (i3 - 100);
				}
				if (anIntArray945[j] == 11) {
					int j3 = 150 - anIntArray947[j];
					if (j3 < 50)
						k2 = 0xffffff - 0x50005 * j3;
					else if (j3 < 100)
						k2 = 65280 + 0x50005 * (j3 - 50);
					else if (j3 < 150)
						k2 = 0xffffff - 0x50000 * (j3 - 100);
				}
				if (anIntArray946[j] == 0) {
					boldFont.drawCenteredText(s, projectedX, projectedY + 1, 0);
					boldFont.drawCenteredText(s, projectedX, projectedY, k2);
				}
				if (anIntArray946[j] == 1) {
					boldFont.drawWaveText(s, projectedX, projectedY + 1, 0, sceneEntityRenderer.getRenderCycle());
					boldFont.drawWaveText(s, projectedX, projectedY, k2, sceneEntityRenderer.getRenderCycle());
				}
				if (anIntArray946[j] == 2) {
					boldFont.drawWave2Text(s, projectedX, projectedY + 1, 0, sceneEntityRenderer.getRenderCycle());
					boldFont.drawWave2Text(s, projectedX, projectedY, k2, sceneEntityRenderer.getRenderCycle());
				}
				if (anIntArray946[j] == 3) {
					boldFont.drawWaveAmplitudeText(s, projectedX, projectedY + 1, 0,
							150 - anIntArray947[j], sceneEntityRenderer.getRenderCycle());
					boldFont.drawWaveAmplitudeText(s, projectedX, projectedY, k2,
							150 - anIntArray947[j], sceneEntityRenderer.getRenderCycle());
				}
				if (anIntArray946[j] == 4) {
					int k3 = boldFont.getTextWidth(s);
					int i4 = ((150 - anIntArray947[j]) * (k3 + 100)) / 150;
					Rasterizer.setCoordinates(projectedX - 50, 0, projectedX + 50, 334);
					boldFont.drawText(s, (projectedX + 50) - i4, projectedY + 1, 0);
					boldFont.drawText(s, (projectedX + 50) - i4, projectedY, k2);
					Rasterizer.resetCoordinates();
				}
				if (anIntArray946[j] == 5) {
					int l3 = 150 - anIntArray947[j];
					int j4 = 0;
					if (l3 < 25)
						j4 = l3 - 25;
					else if (l3 > 125)
						j4 = l3 - 125;
					Rasterizer.setCoordinates(0, projectedY - boldFont.lineHeight - 1, 512,
							projectedY + 5);
					boldFont.drawCenteredText(s, projectedX, projectedY + 1 + j4, 0);
					boldFont.drawCenteredText(s, projectedX, projectedY + j4, k2);
					Rasterizer.resetCoordinates();
				}
			} else {
				boldFont.drawCenteredText(s, projectedX, projectedY + 1, 0);
				boldFont.drawCenteredText(s, projectedX, projectedY, 0xffff00);
			}
		}

		if (flag)
			networkSession.incomingOpcode = -1;
	}

	/* Legacy client.method122(int i): i -> removed negative sentinel. */
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
			aClass50_Sub1_Sub1_Sub3_1186.draw(0, 0);
			sidebarBuffer = new GraphicsBuffer(getGameComponent(), 190, 261);
			viewportBuffer = new GraphicsBuffer(getGameComponent(), 512, 334);
			Rasterizer.resetPixels();
			aClass18_1108 = new GraphicsBuffer(getGameComponent(), 496, 50);
			aClass18_1109 = new GraphicsBuffer(getGameComponent(), 269, 37);
			aClass18_1110 = new GraphicsBuffer(getGameComponent(), 249, 45);
			aBoolean1046 = true;
			viewportBuffer.bindRaster();
			Rasterizer3D.scanlineOffsets = viewportScanlineOffsets;
			return;
		}
	}

	/* Legacy client.method123(int i): i -> removed fixed 281 division sentinel. */
	public void drawStartupErrorScreen() {
		Graphics g = getGameComponent().getGraphics();
		g.setColor(Color.black);
		g.fillRect(0, 0, 765, 503);
		setTargetFps(1);
		if (loadingError) {
			titleFlamesRunning = false;
			g.setFont(new Font("Helvetica", 1, 16));
			g.setColor(Color.yellow);
			int j = 35;
			g.drawString("Sorry, an error has occured whilst loading RuneScape", 30, j);
			j += 50;
			g.setColor(Color.white);
			g.drawString("To fix this try the following (in order):", 30, j);
			j += 50;
			g.setColor(Color.white);
			g.setFont(new Font("Helvetica", 1, 12));
			g.drawString("1: Try closing ALL open web-browser windows, and reloading", 30, j);
			j += 30;
			g.drawString("2: Try clearing your web-browsers cache from tools->internet options", 30, j);
			j += 30;
			g.drawString("3: Try using a different game-world", 30, j);
			j += 30;
			g.drawString("4: Try rebooting your computer", 30, j);
			j += 30;
			g.drawString("5: Try selecting a different version of Java from the play-game menu", 30, j);
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
			int k = 35;
			g.drawString("Error a copy of RuneScape already appears to be loaded", 30, k);
			k += 50;
			g.setColor(Color.white);
			g.drawString("To fix this try the following (in order):", 30, k);
			k += 50;
			g.setColor(Color.white);
			g.setFont(new Font("Helvetica", 1, 12));
			g.drawString("1: Try closing ALL open web-browser windows, and reloading", 30, k);
			k += 30;
			g.drawString("2: Try rebooting your computer, and reloading", 30, k);
			k += 30;
		}
	}

	public void logout() {
		networkSession.closeConnection();
		loggedIn = false;
		loginScreen.resetForLogout();
		method49(383);
		worldState.scene.clear();
		for (int i = 0; i < 4; i++)
			worldState.collisionMaps[i].reset();

		System.gc();
		musicController.resetOnLogout();
	}

	/*
	 * Legacy client.method125(int i, String s, String s1):
	 *   i -> removed negative sentinel, s -> secondaryMessage, s1 -> primaryMessage.
	 */
	public void drawGameLoadingMessage(String s, String s1) {
		if (viewportBuffer != null) {
			viewportBuffer.bindRaster();
			Rasterizer3D.scanlineOffsets = viewportScanlineOffsets;
			int j = 151;
			if (s != null)
				j -= 7;
			plainFont.drawCenteredText(s1, 257, j, 0);
			plainFont.drawCenteredText(s1, 256, j - 1, 0xffffff);
			j += 15;
			if (s != null) {
				plainFont.drawCenteredText(s, 257, j, 0);
				plainFont.drawCenteredText(s, 256, j - 1, 0xffffff);
			}
			viewportBuffer.draw(super.graphics, 4, 4);
			return;
		}
		if (super.gameBuffer != null) {
			super.gameBuffer.bindRaster();
			Rasterizer3D.scanlineOffsets = fullScreenScanlineOffsets;
			int k = 251;
			char c = '\u012C';
			byte byte0 = 50;
			Rasterizer.drawFilledRectangle(383 - c / 2, k - 5 - byte0 / 2, c, byte0, 0);
			Rasterizer.drawUnfilledRectangle(383 - c / 2, k - 5 - byte0 / 2, c, byte0, 0xffffff);
			if (s != null)
				k -= 7;
			plainFont.drawCenteredText(s1, 383, k, 0);
			plainFont.drawCenteredText(s1, 382, k - 1, 0xffffff);
			k += 15;
			if (s != null) {
				plainFont.drawCenteredText(s, 383, k, 0);
				plainFont.drawCenteredText(s, 382, k - 1, 0xffffff);
			}
			super.gameBuffer.draw(super.graphics, 0, 0);
		}
	}

	/* Legacy client.method126(int i, byte byte0): byte0 -> removed fixed 97 sentinel. */
	public boolean isAddFriendMenuAction(int index) {
		return menuState.isAddFriendAction(index);
	}

	public void method127() {
		if (anInt1197 != 2)
			return;
		projectWorldToScreen((anInt844 - regionManager.baseX << 7) + anInt847, anInt846 * 2, (anInt845 - regionManager.baseY << 7) + anInt848);
		if (projectedX > -1 && anInt1325 % 20 < 10)
			aClass50_Sub1_Sub1_Sub1Array954[0].drawImage(projectedX - 12, projectedY - 28);
	}

	public void processDrawing() {
		if (duplicateClientError || loadingError || invalidHostError) {
			drawStartupErrorScreen();
			return;
		}
		anInt1309++;
		if (!loggedIn)
			drawLoginScreen(false);
		else
			method74(7);
		anInt1094 = 0;
	}

	/* Legacy client.method128(boolean flag): flag -> removed always-false sentinel. */
	public void drawContextMenu() {
		int i = menuState.offsetX;
		int j = menuState.offsetY;
		int k = menuState.width;
		int l = menuState.height;
		int i1 = 0x5d5447;
		Rasterizer.drawFilledRectangle(i, j, k, l, i1);
		Rasterizer.drawFilledRectangle(i + 1, j + 1, k - 2, 16, 0);
		Rasterizer.drawUnfilledRectangle(i + 1, j + 18, k - 2, l - 19, 0);
		boldFont.drawText("Choose Option", i + 3, j + 14, i1);
		int j1 = super.mouseX;
		int k1 = super.mouseY;
		if (menuState.screenArea == 0) {
			j1 -= 4;
			k1 -= 4;
		}
		if (menuState.screenArea == 1) {
			j1 -= 553;
			k1 -= 205;
		}
		if (menuState.screenArea == 2) {
			j1 -= 17;
			k1 -= 357;
		}
		for (int l1 = 0; l1 < menuState.count; l1++) {
			int i2 = j + 31 + (menuState.count - 1 - l1) * 15;
			int j2 = 0xffffff;
			if (j1 > i && j1 < i + k && k1 > i2 - 13 && k1 < i2 + 3)
				j2 = 0xffff00;
			boldFont.drawTextWithTags(menuState.actionNames[l1], i + 3, i2, j2, true);
		}

	}

	// Legacy method130 moved into MinimapRenderer.drawOnMinimap.


	/*
	 * Legacy client.method131(byte byte0, boolean flag):
	 *   byte0 -> removed fixed -50 sentinel, flag -> hideButtons.
	 */
	public void drawLoginScreen(boolean hideButtons) {
		createTitleScreenBuffers();
		loginBoxBuffer.bindRaster();
		titleBoxImage.draw(0, 0);
		char c = '\u0168';
		char c1 = '\310';
		if (loginScreen.state == LoginScreen.WELCOME) {
			int j = c1 / 2 + 80;
			smallFont.drawCenteredTextWithTags(onDemandFetcher.statusString, c / 2, j, 0x75a9a9,
					true);
			j = c1 / 2 - 20;
			boldFont.drawCenteredTextWithTags("Welcome to RuneScape", c / 2, j, 0xffff00, true);
			j += 30;
			int i1 = c / 2 - 80;
			int l1 = c1 / 2 + 20;
			titleButtonImage.draw(i1 - 73, l1 - 20);
			boldFont.drawCenteredTextWithTags("New User", i1, l1 + 5, 0xffffff, true);
			i1 = c / 2 + 80;
			titleButtonImage.draw(i1 - 73, l1 - 20);
			boldFont.drawCenteredTextWithTags("Existing User", i1, l1 + 5, 0xffffff, true);
		}
		if (loginScreen.state == LoginScreen.CREDENTIALS) {
			int k = c1 / 2 - 40;
			if (loginScreen.message1.length() > 0) {
				boldFont.drawCenteredTextWithTags(loginScreen.message1, c / 2, k - 15, 0xffff00, true);
				boldFont.drawCenteredTextWithTags(loginScreen.message2, c / 2, k, 0xffff00, true);
				k += 30;
			} else {
				boldFont.drawCenteredTextWithTags(loginScreen.message2, c / 2, k - 7, 0xffff00, true);
				k += 30;
			}
			boldFont.drawTextWithTags(
					"Username: " + loginScreen.username + ((loginScreen.focusedField == 0) & (anInt1325 % 40 < 20) ? "@yel@|" : ""), c / 2 - 90,
					k, 0xffffff, true);
			k += 15;
			boldFont.drawTextWithTags("Password: " + TextFormatter.mask(loginScreen.password)
					+ ((loginScreen.focusedField == 1) & (anInt1325 % 40 < 20) ? "@yel@|" : ""), c / 2 - 88, k, 0xffffff, true);
			k += 15;
			if (!hideButtons) {
				int j1 = c / 2 - 80;
				int i2 = c1 / 2 + 50;
				titleButtonImage.draw(j1 - 73, i2 - 20);
				boldFont.drawCenteredTextWithTags("Login", j1, i2 + 5, 0xffffff, true);
				j1 = c / 2 + 80;
				titleButtonImage.draw(j1 - 73, i2 - 20);
				boldFont.drawCenteredTextWithTags("Cancel", j1, i2 + 5, 0xffffff, true);
			}
		}
		if (loginScreen.state == LoginScreen.CREATE_ACCOUNT) {
			boldFont.drawCenteredTextWithTags("Create a free account", c / 2, c1 / 2 - 60, 0xffff00,
					true);
			int l = c1 / 2 - 35;
			boldFont.drawCenteredTextWithTags("To create a new account you need to", c / 2, l,
					0xffffff, true);
			l += 15;
			boldFont.drawCenteredTextWithTags("go back to the main RuneScape webpage", c / 2, l,
					0xffffff, true);
			l += 15;
			boldFont.drawCenteredTextWithTags("and choose the 'create account'", c / 2, l, 0xffffff,
					true);
			l += 15;
			boldFont.drawCenteredTextWithTags("button near the top of that page.", c / 2, l,
					0xffffff, true);
			l += 15;
			int k1 = c / 2;
			int j2 = c1 / 2 + 50;
			titleButtonImage.draw(k1 - 73, j2 - 20);
			boldFont.drawCenteredTextWithTags("Cancel", k1, j2 + 5, 0xffffff, true);
		}
		loginBoxBuffer.draw(super.graphics, 202, 171);
		if (aBoolean1046) {
			aBoolean1046 = false;
			titleTopBuffer.draw(super.graphics, 128, 0);
			titleBottomBuffer.draw(super.graphics, 202, 371);
			titleLeftBottomBuffer.draw(super.graphics, 0, 265);
			titleRightBottomBuffer.draw(super.graphics, 562, 265);
			titleLeftCenterBuffer.draw(super.graphics, 128, 171);
			titleRightCenterBuffer.draw(super.graphics, 562, 171);
		}
	}


	public void method134(byte byte0) {
		sidebarBuffer.bindRaster();
		Rasterizer3D.scanlineOffsets = sidebarScanlineOffsets;
		aClass50_Sub1_Sub1_Sub3_1185.draw(0, 0);
		if (interfaceState.sidebarOverlayInterfaceId != -1)
			drawInterface(0, 0, Widget.get(interfaceState.sidebarOverlayInterfaceId), 0);
		else if (interfaceState.tabInterfaceIds[interfaceState.selectedTab] != -1)
			drawInterface(0, 0, Widget.get(interfaceState.tabInterfaceIds[interfaceState.selectedTab]), 0);
		if (menuState.open && menuState.screenArea == 1)
			drawContextMenu();
		sidebarBuffer.draw(super.graphics, 553, 205);
		viewportBuffer.bindRaster();
		Rasterizer3D.scanlineOffsets = viewportScanlineOffsets;
		if (byte0 == 7)
			;
	}

	public static String method135(int i, int j) {
		String s = String.valueOf(j);
		if (i != 0)
			throw new NullPointerException();
		for (int k = s.length() - 3; k > 0; k -= 3)
			s = s.substring(0, k) + "," + s.substring(k);

		if (s.length() > 8)
			s = "@gre@" + s.substring(0, s.length() - 8) + " million @whi@(" + s + ")";
		else if (s.length() > 4)
			s = "@cya@" + s.substring(0, s.length() - 4) + "K @whi@(" + s + ")";
		return " " + s;
	}

	/* Legacy client.method136(Actor actor, boolean flag, int i): flag -> unused; i -> heightOffset. */
	private void projectActorToScreen(Actor actor, int heightOffset) {
		projectWorldToScreen(actor.x, heightOffset, actor.y);
	}


	/*
	 * Legacy client.method137(int i, int j, int k, int l)
	 * i -> worldX, j -> heightOffset, k -> worldY, l -> removed negative sentinel.
	 */
	private void projectWorldToScreen(int worldX, int heightOffset, int worldY) {
		CameraController.ScreenPoint point = cameraController.project(worldState, currentPlane, worldX, heightOffset, worldY);
		projectedX = point.x;
		projectedY = point.y;
	}


	public void method138(boolean flag) {
		System.out.println("============");
		System.out.println("flame-cycle:" + titleFlameCycle);
		if (onDemandFetcher != null)
			System.out.println("Od-cycle:" + onDemandFetcher.onDemandCycle);
		System.out.println("loop-cycle:" + anInt1325);
		System.out.println("draw-cycle:" + anInt1309);
		System.out.println("ptype:" + networkSession.incomingOpcode);
		System.out.println("psize:" + networkSession.incomingLength);
		if (flag)
			aBoolean1028 = !aBoolean1028;
		if (networkSession.isConnected())
			networkSession.printDebugInformation();
		super.debugTiming = true;
	}

	public Component getGameComponent() {
		if (super.gameFrame != null)
			return super.gameFrame;
		else
			return this;
	}

	public void drawLoadingText(int i, String s) {
		loadingPercent = i;
		loadingMessage = s;
		createTitleScreenBuffers();
		if (titleArchive == null) {
			super.drawLoadingText(i, s);
			return;
		}
		loginBoxBuffer.bindRaster();
		char c = '\u0168';
		char c1 = '\310';
		byte byte0 = 20;
		boldFont.drawCenteredText("RuneScape is loading - please wait...", c / 2,
				c1 / 2 - 26 - byte0, 0xffffff);
		int j = c1 / 2 - 18 - byte0;
		Rasterizer.drawUnfilledRectangle(c / 2 - 152, j, 304, 34, 0x8c1111);
		Rasterizer.drawUnfilledRectangle(c / 2 - 151, j + 1, 302, 32, 0);
		Rasterizer.drawFilledRectangle(c / 2 - 150, j + 2, i * 3, 30, 0x8c1111);
		Rasterizer.drawFilledRectangle((c / 2 - 150) + i * 3, j + 2, 300 - i * 3, 30, 0);
		boldFont.drawCenteredText(s, c / 2, (c1 / 2 + 5) - byte0, 0xffffff);
		loginBoxBuffer.draw(super.graphics, 202, 171);
		if (aBoolean1046) {
			aBoolean1046 = false;
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

	/* Legacy client.method139(boolean flag): flag -> removed false infinite-loop sentinel. */
	public void drawTitleBackground() {
		byte abyte0[] = titleArchive.read("title.dat");
		ImageRGB class50_sub1_sub1_sub1 = new ImageRGB(abyte0, this);
		titleLeftFlameBuffer.bindRaster();
		class50_sub1_sub1_sub1.drawInverse(0, 0);
		titleRightFlameBuffer.bindRaster();
		class50_sub1_sub1_sub1.drawInverse(-637, 0);
		titleTopBuffer.bindRaster();
		class50_sub1_sub1_sub1.drawInverse(-128, 0);
		titleBottomBuffer.bindRaster();
		class50_sub1_sub1_sub1.drawInverse(-202, -371);
		loginBoxBuffer.bindRaster();
		class50_sub1_sub1_sub1.drawInverse(-202, -171);
		titleLeftBottomBuffer.bindRaster();
		class50_sub1_sub1_sub1.drawInverse(0, -265);
		titleRightBottomBuffer.bindRaster();
		class50_sub1_sub1_sub1.drawInverse(-562, -265);
		titleLeftCenterBuffer.bindRaster();
		class50_sub1_sub1_sub1.drawInverse(-128, -171);
		titleRightCenterBuffer.bindRaster();
		class50_sub1_sub1_sub1.drawInverse(-562, -171);
		int ai[] = new int[class50_sub1_sub1_sub1.width];
		for (int i = 0; i < class50_sub1_sub1_sub1.height; i++) {
			for (int j = 0; j < class50_sub1_sub1_sub1.width; j++)
				ai[j] = class50_sub1_sub1_sub1.pixels[(class50_sub1_sub1_sub1.width - j - 1)
						+ class50_sub1_sub1_sub1.width * i];

			for (int l = 0; l < class50_sub1_sub1_sub1.width; l++)
				class50_sub1_sub1_sub1.pixels[l + class50_sub1_sub1_sub1.width * i] = ai[l];

		}

		titleLeftFlameBuffer.bindRaster();
		class50_sub1_sub1_sub1.drawInverse(382, 0);
		titleRightFlameBuffer.bindRaster();
		class50_sub1_sub1_sub1.drawInverse(-255, 0);
		titleTopBuffer.bindRaster();
		class50_sub1_sub1_sub1.drawInverse(254, 0);
		titleBottomBuffer.bindRaster();
		class50_sub1_sub1_sub1.drawInverse(180, -371);
		loginBoxBuffer.bindRaster();
		class50_sub1_sub1_sub1.drawInverse(180, -171);
		titleLeftBottomBuffer.bindRaster();
		class50_sub1_sub1_sub1.drawInverse(382, -265);
		titleRightBottomBuffer.bindRaster();
		class50_sub1_sub1_sub1.drawInverse(-180, -265);
		titleLeftCenterBuffer.bindRaster();
		class50_sub1_sub1_sub1.drawInverse(254, -171);
		titleRightCenterBuffer.bindRaster();
		class50_sub1_sub1_sub1.drawInverse(-180, -171);
		class50_sub1_sub1_sub1 = new ImageRGB(titleArchive, "logo", 0);
		titleTopBuffer.bindRaster();
		class50_sub1_sub1_sub1.drawImage(382 - class50_sub1_sub1_sub1.width / 2 - 128, 18);
		class50_sub1_sub1_sub1 = null;
		abyte0 = null;
		ai = null;
		System.gc();
	}

	/* Legacy client.method141(int i): i -> removed fixed 28614 sentinel. */
	public void disposeTitleScreen() {
		titleFlamesRunning = false;
		while (titleFlameThreadActive) {
			titleFlamesRunning = false;
			try {
				Thread.sleep(50L);
			} catch (Exception _ex) {
			}
		}
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

	/*
	 * Legacy client.method142(int i, int j, Widget class13, int k, int l):
	 *   i -> y, j -> x, class13 -> widget, k -> scrollY, l -> removed fixed 8 sentinel.
	 */
	public void drawInterface(int y, int x, Widget widget, int scrollY) {
		if (widget.type != 0 || widget.children == null)
			return;
		if (widget.mouseoverTriggered && anInt1302 != widget.id && anInt1280 != widget.id && anInt1106 != widget.id)
			return;
		int i1 = Rasterizer.topX;
		int j1 = Rasterizer.topY;
		int k1 = Rasterizer.bottomX;
		int l1 = Rasterizer.bottomY;
		Rasterizer.setCoordinates(x, y, x + widget.width, y + widget.height);
		int i2 = widget.children.length;
		for (int j2 = 0; j2 < i2; j2++) {
			int k2 = widget.childX[j2] + x;
			int l2 = (widget.childY[j2] + y) - scrollY;
			Widget class13_1 = Widget.get(widget.children[j2]);
			k2 += class13_1.xOffset;
			l2 += class13_1.yOffset;
			if (class13_1.contentType > 0)
				updateWidgetContent(class13_1);
			if (class13_1.type == 0) {
				if (class13_1.scrollY > class13_1.scrollHeight - class13_1.height)
					class13_1.scrollY = class13_1.scrollHeight - class13_1.height;
				if (class13_1.scrollY < 0)
					class13_1.scrollY = 0;
				drawInterface(l2, k2, class13_1, class13_1.scrollY);
				if (class13_1.scrollHeight > class13_1.height)
					drawScrollbar(class13_1.scrollY, k2 + class13_1.width, class13_1.height, class13_1.scrollHeight,
							l2);
			} else if (class13_1.type != 1)
				if (class13_1.type == 2) {
					int i3 = 0;
					for (int i4 = 0; i4 < class13_1.height; i4++) {
						for (int j5 = 0; j5 < class13_1.width; j5++) {
							int i6 = k2 + j5 * (32 + class13_1.inventorySpritePaddingX);
							int l6 = l2 + i4 * (32 + class13_1.inventorySpritePaddingY);
							if (i3 < 20) {
								i6 += class13_1.spriteXOffsets[i3];
								l6 += class13_1.spriteYOffsets[i3];
							}
							if (class13_1.itemIds[i3] > 0) {
								int i7 = 0;
								int j8 = 0;
								int l10 = class13_1.itemIds[i3] - 1;
								if (i6 > Rasterizer.topX - 32 && i6 < Rasterizer.bottomX && l6 > Rasterizer.topY - 32
										&& l6 < Rasterizer.bottomY || interfaceState.inventoryDragArea != 0 && interfaceState.draggedInventorySlot == i3) {
									int k11 = 0;
									if (interfaceState.itemSelected == 1 && interfaceState.selectedItemSlot == i3 && interfaceState.selectedItemWidgetId == class13_1.id)
										k11 = 0xffffff;
									ImageRGB class50_sub1_sub1_sub1_2 = ItemSpriteFactory.getSprite(l10,
											class13_1.itemAmounts[i3], k11);
									if (class50_sub1_sub1_sub1_2 != null) {
										if (interfaceState.inventoryDragArea != 0 && interfaceState.draggedInventorySlot == i3 && interfaceState.draggedInventoryWidgetId == class13_1.id) {
											i7 = super.mouseX - interfaceState.inventoryDragStartX;
											j8 = super.mouseY - interfaceState.inventoryDragStartY;
											if (i7 < 5 && i7 > -5)
												i7 = 0;
											if (j8 < 5 && j8 > -5)
												j8 = 0;
											if (interfaceState.inventoryDragDuration < 5) {
												i7 = 0;
												j8 = 0;
											}
											class50_sub1_sub1_sub1_2.drawImageAlpha(i6 + i7, l6 + j8, 128);
											if (l6 + j8 < Rasterizer.topY && widget.scrollY > 0) {
												int i12 = (anInt951 * (Rasterizer.topY - l6 - j8)) / 3;
												if (i12 > anInt951 * 10)
													i12 = anInt951 * 10;
												if (i12 > widget.scrollY)
													i12 = widget.scrollY;
												widget.scrollY -= i12;
												interfaceState.inventoryDragStartY += i12;
											}
											if (l6 + j8 + 32 > Rasterizer.bottomY
													&& widget.scrollY < widget.scrollHeight - widget.height) {
												int j12 = (anInt951 * ((l6 + j8 + 32) - Rasterizer.bottomY)) / 3;
												if (j12 > anInt951 * 10)
													j12 = anInt951 * 10;
												if (j12 > widget.scrollHeight - widget.height - widget.scrollY)
													j12 = widget.scrollHeight - widget.height - widget.scrollY;
												widget.scrollY += j12;
												interfaceState.inventoryDragStartY -= j12;
											}
										} else if (interfaceState.pressedInventoryArea != 0 && interfaceState.pressedInventorySlot == i3 && interfaceState.pressedInventoryWidgetId == class13_1.id)
											class50_sub1_sub1_sub1_2.drawImageAlpha(i6, l6, 128);
										else
											class50_sub1_sub1_sub1_2.drawImage(i6, l6);
										if (class50_sub1_sub1_sub1_2.maxWidth == 33 || class13_1.itemAmounts[i3] != 1) {
											int k12 = class13_1.itemAmounts[i3];
											smallFont.drawText(method20(k12, -243), i6 + 1 + i7,
													l6 + 10 + j8, 0);
											smallFont.drawText(method20(k12, -243), i6 + i7,
													l6 + 9 + j8, 0xffff00);
										}
									}
								}
							} else if (class13_1.inventorySprites != null && i3 < 20) {
								ImageRGB class50_sub1_sub1_sub1_1 = class13_1.inventorySprites[i3];
								if (class50_sub1_sub1_sub1_1 != null)
									class50_sub1_sub1_sub1_1.drawImage(i6, l6);
							}
							i3++;
						}

					}

				} else if (class13_1.type == 3) {
					boolean flag = false;
					if (anInt1106 == class13_1.id || anInt1280 == class13_1.id || anInt1302 == class13_1.id)
						flag = true;
					int j3;
					if (widgetRuntime.isActive(class13_1)) {
						j3 = class13_1.activeColor;
						if (flag && class13_1.activeMouseoverColor != 0)
							j3 = class13_1.activeMouseoverColor;
					} else {
						j3 = class13_1.color;
						if (flag && class13_1.mouseoverColor != 0)
							j3 = class13_1.mouseoverColor;
					}
					if (class13_1.transparency == 0) {
						if (class13_1.filled)
							Rasterizer.drawFilledRectangle(k2, l2, class13_1.width, class13_1.height, j3);
						else
							Rasterizer.drawUnfilledRectangle(k2, l2, class13_1.width, class13_1.height, j3);
					} else if (class13_1.filled)
						Rasterizer.drawFilledRectangleAlpha(k2, l2, class13_1.width, class13_1.height, j3,
								256 - (class13_1.transparency & 0xff));
					else
						Rasterizer.drawUnfilledRectangleAlpha(k2, l2, class13_1.width, class13_1.height, j3,
								256 - (class13_1.transparency & 0xff));
				} else if (class13_1.type == 4) {
					TypeFace class50_sub1_sub1_sub2 = class13_1.font;
					String s = class13_1.text;
					boolean flag1 = false;
					if (anInt1106 == class13_1.id || anInt1280 == class13_1.id || anInt1302 == class13_1.id)
						flag1 = true;
					int j4;
					if (widgetRuntime.isActive(class13_1)) {
						j4 = class13_1.activeColor;
						if (flag1 && class13_1.activeMouseoverColor != 0)
							j4 = class13_1.activeMouseoverColor;
						if (class13_1.activeText.length() > 0)
							s = class13_1.activeText;
					} else {
						j4 = class13_1.color;
						if (flag1 && class13_1.mouseoverColor != 0)
							j4 = class13_1.mouseoverColor;
					}
					if (class13_1.buttonType == 6 && aBoolean1239) {
						s = "Please wait...";
						j4 = class13_1.color;
					}
					if (Rasterizer.width == 479) {
						if (j4 == 0xffff00)
							j4 = 255;
						if (j4 == 49152)
							j4 = 0xffffff;
					}
					for (int j7 = l2 + class50_sub1_sub1_sub2.lineHeight; s
							.length() > 0; j7 += class50_sub1_sub1_sub2.lineHeight) {
						if (s.indexOf("%") != -1) {
							do {
								int k8 = s.indexOf("%1");
								if (k8 == -1)
									break;
								s = s.substring(0, k8) + method89(widgetRuntime.evaluateScript(class13_1, 0), 8) + s.substring(k8 + 2);
							} while (true);
							do {
								int l8 = s.indexOf("%2");
								if (l8 == -1)
									break;
								s = s.substring(0, l8) + method89(widgetRuntime.evaluateScript(class13_1, 1), 8) + s.substring(l8 + 2);
							} while (true);
							do {
								int i9 = s.indexOf("%3");
								if (i9 == -1)
									break;
								s = s.substring(0, i9) + method89(widgetRuntime.evaluateScript(class13_1, 2), 8) + s.substring(i9 + 2);
							} while (true);
							do {
								int j9 = s.indexOf("%4");
								if (j9 == -1)
									break;
								s = s.substring(0, j9) + method89(widgetRuntime.evaluateScript(class13_1, 3), 8) + s.substring(j9 + 2);
							} while (true);
							do {
								int k9 = s.indexOf("%5");
								if (k9 == -1)
									break;
								s = s.substring(0, k9) + method89(widgetRuntime.evaluateScript(class13_1, 4), 8) + s.substring(k9 + 2);
							} while (true);
						}
						int l9 = s.indexOf("\\n");
						String s3;
						if (l9 != -1) {
							s3 = s.substring(0, l9);
							s = s.substring(l9 + 2);
						} else {
							s3 = s;
							s = "";
						}
						if (class13_1.textCentered)
							class50_sub1_sub1_sub2.drawCenteredTextWithTags(s3, k2 + class13_1.width / 2, j7, j4,
									class13_1.textShadowed);
						else
							class50_sub1_sub1_sub2.drawTextWithTags(s3, k2, j7, j4, class13_1.textShadowed);
					}

				} else if (class13_1.type == 5) {
					ImageRGB class50_sub1_sub1_sub1;
					if (widgetRuntime.isActive(class13_1))
						class50_sub1_sub1_sub1 = class13_1.activeSprite;
					else
						class50_sub1_sub1_sub1 = class13_1.sprite;
					if (class50_sub1_sub1_sub1 != null)
						class50_sub1_sub1_sub1.drawImage(k2, l2);
				} else if (class13_1.type == 6) {
					int k3 = Rasterizer3D.centerX;
					int k4 = Rasterizer3D.centerY;
					Rasterizer3D.centerX = k2 + class13_1.width / 2;
					Rasterizer3D.centerY = l2 + class13_1.height / 2;
					int k5 = Rasterizer3D.SINE[class13_1.modelPitch] * class13_1.modelZoom >> 16;
					int j6 = Rasterizer3D.COSINE[class13_1.modelPitch] * class13_1.modelZoom >> 16;
					boolean flag2 = widgetRuntime.isActive(class13_1);
					int k7;
					if (flag2)
						k7 = class13_1.activeAnimationId;
					else
						k7 = class13_1.animationId;
					Model class50_sub1_sub4_sub4;
					if (k7 == -1) {
						class50_sub1_sub4_sub4 = class13_1.getAnimatedModel(-1, -1, flag2);
					} else {
						AnimationSequence class14 = AnimationSequence.sequences[k7];
						class50_sub1_sub4_sub4 = class13_1.getAnimatedModel(
								class14.primaryFrameIds[class13_1.animationFrame],
								class14.secondaryFrameIds[class13_1.animationFrame], flag2);
					}
					if (class50_sub1_sub4_sub4 != null)
						class50_sub1_sub4_sub4.renderSimple(0, class13_1.modelYaw, 0, class13_1.modelPitch, 0, k5, j6);
					Rasterizer3D.centerX = k3;
					Rasterizer3D.centerY = k4;
				} else {
					if (class13_1.type == 7) {
						TypeFace class50_sub1_sub1_sub2_1 = class13_1.font;
						int l4 = 0;
						for (int l5 = 0; l5 < class13_1.height; l5++) {
							for (int k6 = 0; k6 < class13_1.width; k6++) {
								if (class13_1.itemIds[l4] > 0) {
									ItemDefinition class16 = ItemDefinition.lookup(class13_1.itemIds[l4] - 1);
									String s6 = String.valueOf(class16.name);
									if (class16.stackable || class13_1.itemAmounts[l4] != 1)
										s6 = s6 + " x" + method135(0, class13_1.itemAmounts[l4]);
									int i10 = k2 + k6 * (115 + class13_1.inventorySpritePaddingX);
									int i11 = l2 + l5 * (12 + class13_1.inventorySpritePaddingY);
									if (class13_1.textCentered)
										class50_sub1_sub1_sub2_1.drawCenteredTextWithTags(s6, i10 + class13_1.width / 2,
												i11, class13_1.color, class13_1.textShadowed);
									else
										class50_sub1_sub1_sub2_1.drawTextWithTags(s6, i10, i11, class13_1.color,
												class13_1.textShadowed);
								}
								l4++;
							}

						}

					}
					if (class13_1.type == 8
							&& (anInt1284 == class13_1.id || anInt1044 == class13_1.id || anInt1129 == class13_1.id)
							&& anInt893 == 100) {
						int l3 = 0;
						int i5 = 0;
						TypeFace class50_sub1_sub1_sub2_2 = plainFont;
						for (String s1 = class13_1.text; s1.length() > 0;) {
							int l7 = s1.indexOf("\\n");
							String s4;
							if (l7 != -1) {
								s4 = s1.substring(0, l7);
								s1 = s1.substring(l7 + 2);
							} else {
								s4 = s1;
								s1 = "";
							}
							int j10 = class50_sub1_sub1_sub2_2.getFormattedTextWidth(s4);
							if (j10 > l3)
								l3 = j10;
							i5 += class50_sub1_sub1_sub2_2.lineHeight + 1;
						}

						l3 += 6;
						i5 += 7;
						int i8 = (k2 + class13_1.width) - 5 - l3;
						int k10 = l2 + class13_1.height + 5;
						if (i8 < k2 + 5)
							i8 = k2 + 5;
						if (i8 + l3 > x + widget.width)
							i8 = (x + widget.width) - l3;
						if (k10 + i5 > y + widget.height)
							k10 = (y + widget.height) - i5;
						Rasterizer.drawFilledRectangle(i8, k10, l3, i5, 0xffffa0);
						Rasterizer.drawUnfilledRectangle(i8, k10, l3, i5, 0);
						String s2 = class13_1.text;
						for (int j11 = k10 + class50_sub1_sub1_sub2_2.lineHeight + 2; s2
								.length() > 0; j11 += class50_sub1_sub1_sub2_2.lineHeight + 1) {
							int l11 = s2.indexOf("\\n");
							String s5;
							if (l11 != -1) {
								s5 = s2.substring(0, l11);
								s2 = s2.substring(l11 + 2);
							} else {
								s5 = s2;
								s2 = "";
							}
							class50_sub1_sub1_sub2_2.drawTextWithTags(s5, i8 + 3, j11, 0, false);
						}

					}
				}
		}

		Rasterizer.setCoordinates(i1, j1, k1, l1);
	}

	private int getTileHeight(int worldY, int worldX, int plane) {
		return worldState.getTileHeight(worldX, worldY, plane);
	}

	private void queueAreaSound(int soundId, int loops, int radius, int tileX, int tileY) {
		soundEffectQueue.queueAreaSound(soundId, loops, radius, tileX, tileY,
				localPlayer.pathX[0], localPlayer.pathY[0], lowMemory);
	}

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
						onDemandFetcher, super.gameFrame != null, () -> {
							if (viewportBuffer != null) {
								viewportBuffer.bindRaster();
								Rasterizer3D.scanlineOffsets = viewportScanlineOffsets;
							}
						});
				networkSession.outgoing.writeOpcode(6);
			} else if (System.currentTimeMillis() - regionManager.loadingStartTime > 0x57e40L) {
				Signlink.reportError(loginScreen.username + " glcfb " + serverSessionKey + "," + status + "," + lowMemory + ","
						+ resourceLoader.getCacheIndex(0) + "," + onDemandFetcher.getOutstandingRequestCount() + ","
						+ currentPlane + "," + regionManager.regionX + "," + regionManager.regionY);
				regionManager.loadingStartTime = System.currentTimeMillis();
			}
		}
		if (regionManager.loadingStage == RegionManager.STAGE_LOADED && currentPlane != lastMinimapPlane) {
			lastMinimapPlane = currentPlane;
			rebuildMinimap(currentPlane);
		}
	}

	/* Legacy client.method146(byte byte0): byte0 -> removed fixed 4 sentinel. */
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
		aClass18_1108 = null;
		aClass18_1109 = null;
		aClass18_1110 = null;
		super.gameBuffer = new GraphicsBuffer(getGameComponent(), 765, 503);
		aBoolean1046 = true;
	}

	/*
	 * Legacy client.method148(int i, String s):
	 *   i -> removed 13292 sentinel
	 *   s -> name
	 */
	public boolean isFriendOrSelf(String name) {
		return socialManager.isFriendOrSelf(name, localPlayer.name);
	}


	/* Legacy client.method149(int i): i -> removed negative packet-read sentinel. */
	public void processLoginScreenInput() {
		if (loginScreen.state == LoginScreen.WELCOME) {
			int j = super.canvasWidth / 2 - 80;
			int i1 = super.canvasHeight / 2 + 20;
			i1 += 20;
			if (super.clickButton == 1 && super.clickX >= j - 75 && super.clickX <= j + 75 && super.clickY >= i1 - 20
					&& super.clickY <= i1 + 20) {
				loginScreen.showCreateAccount();
			}
			j = super.canvasWidth / 2 + 80;
			if (super.clickButton == 1 && super.clickX >= j - 75 && super.clickX <= j + 75 && super.clickY >= i1 - 20
					&& super.clickY <= i1 + 20) {
				loginScreen.showCredentials();
				return;
			}
		} else {
			if (loginScreen.state == LoginScreen.CREDENTIALS) {
				int k = super.canvasHeight / 2 - 40;
				k += 30;
				k += 25;
				if (super.clickButton == 1 && super.clickY >= k - 15 && super.clickY < k)
					loginScreen.focusedField = 0;
				k += 15;
				if (super.clickButton == 1 && super.clickY >= k - 15 && super.clickY < k)
					loginScreen.focusedField = 1;
				k += 15;
				int j1 = super.canvasWidth / 2 - 80;
				int l1 = super.canvasHeight / 2 + 50;
				l1 += 20;
				if (super.clickButton == 1 && super.clickX >= j1 - 75 && super.clickX <= j1 + 75
						&& super.clickY >= l1 - 20 && super.clickY <= l1 + 20) {
					loginFailures = 0;
					login(loginScreen.username, loginScreen.password, false);
					if (loggedIn)
						return;
				}
				j1 = super.canvasWidth / 2 + 80;
				if (super.clickButton == 1 && super.clickX >= j1 - 75 && super.clickX <= j1 + 75
						&& super.clickY >= l1 - 20 && super.clickY <= l1 + 20) {
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
				int l = super.canvasWidth / 2;
				int k1 = super.canvasHeight / 2 + 50;
				k1 += 20;
				if (super.clickButton == 1 && super.clickX >= l - 75 && super.clickX <= l + 75
						&& super.clickY >= k1 - 20 && super.clickY <= k1 + 20)
					loginScreen.cancelCreateAccount();
			}
		}
	}

	// Legacy method150 moved into MinimapRenderer.drawMapLocation.


	/* Legacy client.method151(): render the complete 3D game view. */
	private void renderGameScene() {
		destinationX = sceneEntityRenderer.beginFrame(localPlayer, destinationX, destinationY);
		addPlayersToScene(true);
		addNpcsToScene(true);
		addPlayersToScene(false);
		addNpcsToScene(false);
		worldState.updateProjectiles(currentPlane, anInt1325, anInt951, localPlayerServerIndex, localPlayer,
				actorSynchronizer, networkSession.outgoing);
		worldState.updateGraphicsObjects(currentPlane, anInt1325, anInt951);

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
		worldState.scene.render(cameraController.x, cameraController.y, cameraController.height,
				renderPlane, cameraController.yaw, cameraController.pitch);
		worldState.scene.clearTemporaryObjects();
		method121(false);
		method127();
		method65(textureCycle, -927);
		method109();
		viewportBuffer.draw(super.graphics, 4, 4);
		cameraController.restore(cameraSnapshot);
	}


	public client() {
		reportAbuseName = "";
		skillExperiences = new int[Skills.COUNT];
		aString861 = "";
		aStringArray863 = new String[100];
		anIntArray864 = new int[100];
		messagePromptRaised = false;
		aBoolean892 = false;
		anInt894 = -992;
		aClass50_Sub1_Sub1_Sub1Array896 = new ImageRGB[8];
		aBoolean918 = true;
		aBoolean919 = true;
		anIntArray920 = new int[151];
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
			@Override public int currentSkillLevel(int skill) { return currentSkillLevels[skill]; }
			@Override public int baseSkillLevel(int skill) { return baseSkillLevels[skill]; }
			@Override public int skillExperience(int skill) { return skillExperiences[skill]; }
			@Override public int varp(int id) { return varpValues[id]; }
			@Override public int experienceForLevel(int levelIndex) { return experienceTable[levelIndex]; }
			@Override public int bitMask(int width) { return bitMasks[width]; }
			@Override public int runEnergy() { return runEnergy; }
			@Override public int weight() { return weight; }
			@Override public int combatLevel() { return localPlayer.combatLevel; }
			@Override public int playerWorldX() { return (localPlayer.x >> 7) + regionManager.baseX; }
			@Override public int playerWorldY() { return (localPlayer.y >> 7) + regionManager.baseY; }
			@Override public boolean membersWorld() { return membersWorld; }
		});
		loginBuffer = new Buffer(new byte[5000]);
		anInt931 = 0x23201b;
		projectedX = -1;
		projectedY = -1;
		promptMessage = "";
		anInt938 = -214;
		anInt940 = 50;
		anIntArray941 = new int[anInt940];
		anIntArray942 = new int[anInt940];
		anIntArray943 = new int[anInt940];
		anIntArray944 = new int[anInt940];
		anIntArray945 = new int[anInt940];
		anIntArray946 = new int[anInt940];
		anIntArray947 = new int[anInt940];
		aStringArray948 = new String[anInt940];
		aString949 = "";
		aBoolean950 = false;
		aBoolean953 = false;
		aClass50_Sub1_Sub1_Sub1Array954 = new ImageRGB[32];
		aBoolean959 = true;
		localPlayerServerIndex = -1;
		aClass50_Sub1_Sub1_Sub3Array976 = new IndexedImage[13];
		anIntArray1005 = new int[2000];
		duplicateClientError = false;
		anIntArray1019 = new int[151];
		promptInput = "";
		aBoolean1028 = false;
		currentSkillLevels = new int[Skills.COUNT];
		aClass50_Sub1_Sub1_Sub1Array1031 = new ImageRGB[100];
		aBoolean1033 = false;
		varpValues = new int[2000];
		aBoolean1046 = false;
		anInt1051 = 69;
		baseSkillLevels = new int[Skills.COUNT];
		regionManager.specialRegion = false;
		aStringArray1069 = new String[5];
		aBooleanArray1070 = new boolean[5];
		anInt1072 = 20411;
		aClass50_Sub1_Sub1_Sub1Array1079 = new ImageRGB[32];
		anInt1080 = 0x4d4233;
		invalidHostError = false;
		reportAbuseMutePlayer = false;
		anIntArray1099 = new int[5];
		chatInput = "";
		chatContentHeight = 78;
		aBoolean1127 = false;
		chatBuffer = new Buffer(new byte[5000]);
		anInt1135 = 0x766654;
		aBoolean1136 = false;
		loggedIn = false;
		aClass50_Sub1_Sub1_Sub3Array1142 = new IndexedImage[2];
		aByte1143 = -80;
		aBoolean1144 = true;
		aClass50_Sub1_Sub1_Sub3Array1153 = new IndexedImage[100];
		aBoolean1155 = false;
		regionManager.instanced = false;
		titleFlameLineOffsets = new int[256];
		anIntArray1180 = new int[33];
		aBoolean1181 = false;
		aClass50_Sub1_Sub1_Sub1Array1182 = new ImageRGB[20];
		regionManager.awaitingPlayerUpdate = false;
		chatModesRedraw = false;
		aBoolean1239 = false;
		aBoolean1240 = false;
		titleFlamesRunning = false;
		aByteArray1245 = new byte[16384];
		aClass13_1249 = new Widget();
		cameraOrientationChanged = false;
		aBoolean1275 = true;
		lastMinimapPlane = -1;
		aBoolean1277 = false;
		loadingError = false;
		anIntArray1286 = new int[33];
		anInt1287 = 0x332d25;
		aClass50_Sub1_Sub1_Sub1Array1288 = new ImageRGB[32];
		titleFlameThreadMode = false;
		anInt1318 = 416;
		titleFlameThreadActive = false;
		anIntArray1326 = new int[7];
		anInt1328 = 409;
	}

	public String reportAbuseName;
	public static BigInteger aBigInteger840 = new BigInteger(
			"7162900525229798032761816791230527296329313291232324290237849263501208207972894053929065636522363163621000728841182238772712427862772219676577293600221789");
	public static int anInt841;
	public int anIntArray842[] = { 0xffff00, 0xff0000, 65280, 65535, 0xff00ff, 0xffffff };
	public int skillExperiences[];
	public int anInt844;
	public int anInt845;
	public int anInt846;
	public int anInt847;
	public int anInt848;
	public int loginFailures;
	public int chatScrollOffset;
	public String aString861;
	public int anInt862;
	public String aStringArray863[];
	public int anIntArray864[];
	public int anInt865;
	public boolean messagePromptRaised;
	public int playerRights;
	public static boolean aBoolean868;
	public int logoutTimer;
	public IndexedImage aClass50_Sub1_Sub1_Sub3_880;
	public IndexedImage aClass50_Sub1_Sub1_Sub3_881;
	public IndexedImage aClass50_Sub1_Sub1_Sub3_882;
	public IndexedImage aClass50_Sub1_Sub1_Sub3_883;
	public IndexedImage aClass50_Sub1_Sub1_Sub3_884;
	public int privateChatMode;
	public Archive titleArchive;
	public boolean aBoolean892;
	public int anInt893;
	public int anInt894;
	public static int anInt895;
	public ImageRGB aClass50_Sub1_Sub1_Sub1Array896[];
	public long aLong902;
	public GraphicsBuffer aClass18_906;
	public GraphicsBuffer aClass18_907;
	public GraphicsBuffer aClass18_908;
	public GraphicsBuffer aClass18_909;
	public GraphicsBuffer aClass18_910;
	public GraphicsBuffer aClass18_911;
	public GraphicsBuffer aClass18_912;
	public GraphicsBuffer aClass18_913;
	public GraphicsBuffer aClass18_914;
	public int anInt915;
	public boolean aBoolean918;
	public boolean aBoolean919;
	public int anIntArray920[];
	public static int currentWorldId = 10;
	public static int portOffset;
	public static boolean membersWorld = true;
	public static boolean lowMemory;
	public Buffer loginBuffer;
	public long serverSessionKey;
	public int anInt931;
	public int projectedX;
	public int projectedY;
	public String promptMessage;
	public int anInt938;
	public int anInt939;
	public int anInt940;
	public int anIntArray941[];
	public int anIntArray942[];
	public int anIntArray943[];
	public int anIntArray944[];
	public int anIntArray945[];
	public int anIntArray946[];
	public int anIntArray947[];
	public String aStringArray948[];
	public String aString949;
	public boolean aBoolean950;
	public int anInt951;
	public static int experienceTable[];
	public boolean aBoolean953;
	public ImageRGB aClass50_Sub1_Sub1_Sub1Array954[];
	public int anInt955;
	public boolean aBoolean959;
	public static boolean accountFlagged;
	public NetworkSession networkSession;
	private final SocialManager socialManager;
	private final ChatHistory chatHistory;
	private final InterfaceState interfaceState;
	private final MenuState menuState;
	private final LoginScreen loginScreen;
	private final ResourceLoader resourceLoader;
	private final SoundEffectQueue soundEffectQueue;
	private final MusicController musicController;
	private final WidgetRuntime widgetRuntime;
	private final Pathfinder pathfinder;
	private final ActorSynchronizer actorSynchronizer;
	private final ActorUpdater actorUpdater;
	private final CameraController cameraController;
	private final SceneEntityRenderer sceneEntityRenderer;
	private final MinimapRenderer minimapRenderer;
	private final RegionManager regionManager;
	private WorldState worldState;
	private ZoneUpdateHandler zoneUpdates;
	private final ActorSynchronizer.ChatHandler actorChatHandler = new ActorSynchronizer.ChatHandler() {
		@Override
		public boolean isIgnored(long encodedName) {
			return socialManager.isIgnored(encodedName);
		}

		@Override
		public boolean isChatSuppressed() {
			return tutorialIslandFlag != 0;
		}

		@Override
		public void addChatMessage(String sender, String message, int type) {
			client.this.addChatMessage(sender, message, type);
		}
	};
	public int localPlayerServerIndex;
	public static Player localPlayer;
	public IndexedImage aClass50_Sub1_Sub1_Sub3_965;
	public IndexedImage aClass50_Sub1_Sub1_Sub3_966;
	public IndexedImage aClass50_Sub1_Sub1_Sub3_967;
	public IndexedImage aClass50_Sub1_Sub1_Sub3Array976[];
	public IndexedImage aClass50_Sub1_Sub1_Sub3_983;
	public IndexedImage aClass50_Sub1_Sub1_Sub3_984;
	public IndexedImage aClass50_Sub1_Sub1_Sub3_985;
	public IndexedImage aClass50_Sub1_Sub1_Sub3_986;
	public IndexedImage aClass50_Sub1_Sub1_Sub3_987;
	public int anInt992;
	public int anInt998;
	public static boolean startupStarted;
	public int chatboxScanlineOffsets[];
	public int sidebarScanlineOffsets[];
	public int viewportScanlineOffsets[];
	public int fullScreenScanlineOffsets[];
	public int anIntArray1005[];
	public int publicChatMode;
	public static final int anIntArrayArray1008[][] = {
			{ 6798, 107, 10283, 16, 4797, 7744, 5799, 4634, 33697, 22433, 2983, 54193 },
			{ 8741, 12, 64030, 43162, 7735, 8404, 1701, 38430, 24094, 10153, 56621, 4783, 1341, 16578, 35003, 25239 },
			{ 25238, 8742, 12, 64030, 43162, 7735, 8404, 1701, 38430, 24094, 10153, 56621, 4783, 1341, 16578, 35003 },
			{ 4626, 11146, 6439, 12, 4758, 10270 }, { 4550, 4537, 5681, 5673, 5790, 6806, 8076, 4574 } };
	public int anInt1011;
	public int anInt1012;
	public static int anInt1013;
	public int anInt1015;
	public boolean duplicateClientError;
	public ImageRGB titleLeftFlameBackground;
	public ImageRGB titleRightFlameBackground;
	public int anIntArray1019[];
	public int anInt1020;
	public int anInt1021;
	public int anInt1022;
	public int anInt1023;
	public String promptInput;
	public String loadingMessage;
	public boolean aBoolean1028;
	public int currentSkillLevels[];
	public int weight;
	public ImageRGB aClass50_Sub1_Sub1_Sub1Array1031[];
	public boolean aBoolean1033;
	public int anInt1034;
	public ImageRGB aClass50_Sub1_Sub1_Sub1_1036;
	public ImageRGB aClass50_Sub1_Sub1_Sub1_1037;
	public int varpValues[];
	public int anInt1044;
	public boolean aBoolean1046;
	public int greenFlameTransition;
	public int blueFlameTransition;
	public static int anInt1049;
	public int anInt1051;
	public static int anInt1052;
	public int baseSkillLevels[];
	public int anInt1057;
	public String aString1058;
	public TypeFace smallFont;
	public TypeFace plainFont;
	public TypeFace boldFont;
	public TypeFace fancyFont;
	public int anInt1068;
	public String aStringArray1069[];
	public boolean aBooleanArray1070[];
	public int anInt1072;
	public int anInt1075;
	public ImageRGB aClass50_Sub1_Sub1_Sub1Array1079[];
	public int anInt1080;
	public int anInt1083;
	public int titleFlameIntensity[];
	public int titleFlameIntensityScratch[];
	public ImageRGB aClass50_Sub1_Sub1_Sub1_1086;
	public int currentPlane;
	public int anInt1094;
	public IndexedImage aClass50_Sub1_Sub1_Sub3_1095;
	public IndexedImage aClass50_Sub1_Sub1_Sub3_1096;
	public boolean invalidHostError;
	public boolean reportAbuseMutePlayer;
	public int anIntArray1099[];
	public static int anInt1100;
	public int titleFlameCycle;
	public ImageRGB aClass50_Sub1_Sub1_Sub1_1102;
	public ImageRGB aClass50_Sub1_Sub1_Sub1_1103;
	public String chatInput;
	public int anInt1106;
	public int chatContentHeight;
	public GraphicsBuffer aClass18_1108;
	public GraphicsBuffer aClass18_1109;
	public GraphicsBuffer aClass18_1110;
	public ImageRGB aClass50_Sub1_Sub1_Sub1_1116;
	public IndexedImage titleRunes[];
	public int destinationX;
	public int destinationY;
	public int alternativeRoute;
	public boolean aBoolean1127;
	public int anInt1129;
	public Buffer chatBuffer;
	public int anInt1135;
	public boolean aBoolean1136;
	public boolean loggedIn;
	public static int anInt1139;
	public long privateMessageTarget;
	public IndexedImage aClass50_Sub1_Sub1_Sub3Array1142[];
	public byte aByte1143;
	public boolean aBoolean1144;
	public int anInt1149;
	public int anInt1151;
	public IndexedImage aClass50_Sub1_Sub1_Sub3Array1153[];
	public boolean aBoolean1155;
	public GraphicsBuffer sidebarBuffer;
	public GraphicsBuffer minimapBuffer;
	public GraphicsBuffer viewportBuffer;
	public GraphicsBuffer chatboxBuffer;
	public static int anInt1162;
	public static int anInt1165;
	public int titleFlameLineOffsets[];
	public int anInt1170;
	public int anInt1172;
	public int titleFlameNoise[];
	public int titleFlameNoiseScratch[];
	public int anIntArray1180[];
	public boolean aBoolean1181;
	public ImageRGB aClass50_Sub1_Sub1_Sub1Array1182[];
	public IndexedImage aClass50_Sub1_Sub1_Sub3_1185;
	public IndexedImage aClass50_Sub1_Sub1_Sub3_1186;
	public IndexedImage aClass50_Sub1_Sub1_Sub3_1187;
	public ImageRGB aClass50_Sub1_Sub1_Sub1_1192;
	public ImageRGB aClass50_Sub1_Sub1_Sub1_1193;
	public ImageRGB aClass50_Sub1_Sub1_Sub1_1194;
	public ImageRGB aClass50_Sub1_Sub1_Sub1_1195;
	public ImageRGB aClass50_Sub1_Sub1_Sub1_1196;
	public int anInt1197;
	public GraphicsBuffer titleTopBuffer;
	public GraphicsBuffer titleBottomBuffer;
	public GraphicsBuffer loginBoxBuffer;
	public GraphicsBuffer titleLeftFlameBuffer;
	public GraphicsBuffer titleRightFlameBuffer;
	public GraphicsBuffer titleLeftBottomBuffer;
	public GraphicsBuffer titleRightBottomBuffer;
	public GraphicsBuffer titleLeftCenterBuffer;
	public GraphicsBuffer titleRightCenterBuffer;
	public int anInt1208;
	public boolean chatModesRedraw;
	public static int bitMasks[];
	public int anInt1215;
	public int promptAction;
	public int anInt1222;
	public int splitPrivateChat;
	public Socket jaggrabSocket;
	public int anInt1226;
	public int tradeMode;
	public static int anInt1230;
	public static int anInt1235;
	public static int anInt1237;
	public int titleFlameNoiseOffset;
	public boolean aBoolean1239;
	public boolean aBoolean1240;
	public int anInt1241;
	public static boolean aBoolean1242 = true;
	public volatile boolean titleFlamesRunning;
	public int anInt1244;
	public byte aByteArray1245[];
	public int tutorialIslandFlag;
	public ImageRGB aClass50_Sub1_Sub1_Sub1_1247;
	public MouseRecorder aClass7_1248;
	public Widget aClass13_1249;
	public final int anInt1257 = 100;
	public int cameraPacketCooldown;
	public boolean cameraOrientationChanged;
	public static final int anIntArray1268[] = { 9104, 10275, 7595, 3610, 7975, 8526, 918, 38802, 24466, 10145, 58654,
			5027, 1457, 16565, 34991, 25486 };
	public int unreadMessageCount;
	public boolean aBoolean1275;
	public int lastMinimapPlane;
	public boolean aBoolean1277;
	public int anInt1280;
	public boolean loadingError;
	public int anInt1284;
	public int anIntArray1286[];
	public int anInt1287;
	public ImageRGB aClass50_Sub1_Sub1_Sub1Array1288[];
	public int anIntArray1290[] = { 17, 24, 34, 40 };
	public OnDemandFetcher onDemandFetcher;
	public IndexedImage titleBoxImage;
	public IndexedImage titleButtonImage;
	public int anInt1299;
	public int oneButtonMouseMode;
	public int anInt1302;
	public int anInt1303;
	public static int anInt1309;
	public int titleFlamePalette[];
	public int titleFlameRedPalette[];
	public int titleFlameGreenPalette[];
	public int titleFlameBluePalette[];
	public volatile boolean titleFlameThreadMode;
	public int anInt1315;
	public static BigInteger aBigInteger1316 = new BigInteger(
			"58778699976184461502525193738213253649000149147835990136706041084440742975821");
	public int anInt1318;
	public int anInt1319;
	public volatile boolean titleFlameThreadActive;
	public int loadingPercent;
	public int runEnergy;
	public static int anInt1325;
	public int anIntArray1326[];
	public int anInt1328;
	public int anInt1329;
	public static int anInt1333;

	static {
		experienceTable = new int[99];
		int i = 0;
		for (int j = 0; j < 99; j++) {
			int l = j + 1;
			int i1 = (int) ((double) l + 300D * Math.pow(2D, (double) l / 7D));
			i += i1;
			experienceTable[j] = i / 4;
		}

		bitMasks = new int[32];
		i = 2;
		for (int k = 0; k < 32; k++) {
			bitMasks[k] = i - 1;
			i += i;
		}

	}
}
