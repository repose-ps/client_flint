package rs2;

import rs2.net.IncomingPacketHandler;
import rs2.net.IncomingPacketOpcode;
import rs2.net.ProtocolConstants;

import rs2.cache.def.ItemDefinition;
import rs2.ui.Widget;
import rs2.chat.ChatCodec;
import rs2.chat.ChatMessageType;
import rs2.chat.Censor;
import rs2.game.RegionManager;
import rs2.net.Buffer;
import rs2.net.Ipv4Address;
import rs2.sign.Signlink;
import rs2.text.Base37;
import rs2.text.TextFormatter;

/**
 * Applies decoded revision-377 incoming packets to the client runtime.
 *
 * <p>This application-layer adapter deliberately sits outside {@code rs2.net}:
 * packet framing remains transport-only, while this class bridges decoded wire
 * values into the existing world, UI, chat, audio, and actor subsystems. The
 * concrete client dependency is localized here so lower-level networking code
 * does not depend on application state.</p>
 */
final class ClientIncomingPacketHandler implements IncomingPacketHandler {

	/** Client runtime receiving decoded packet effects. */
	private final Client client;

	/**
	 * Creates the incoming packet application adapter.
	 *
	 * @param client client runtime receiving packet effects
	 */
	ClientIncomingPacketHandler(Client client) {
		this.client = client;
	}

	@Override
	public boolean handle(int opcode, Buffer buffer, int packetSize) {
		if (opcode == IncomingPacketOpcode.SET_WIDGET_POSITION) {
			int widgetYOffset = buffer.readShortLE();
			int widgetXOffset = buffer.readShortLE();
			int widgetId = buffer.readUnsignedShort();
			Widget widget = Widget.get(widgetId);
			widget.xOffset = widgetXOffset;
			widget.yOffset = widgetYOffset;
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_WIDGET_MODEL_TRANSFORM) {
			int modelPitch = buffer.readUnsignedShortAdd();
			int widgetId2 = buffer.readUnsignedShortAddLE();
			int modelZoom = buffer.readUnsignedShortAdd();
			int modelYaw = buffer.readUnsignedShortLE();
			Widget.get(widgetId2).modelPitch = modelPitch;
			Widget.get(widgetId2).modelYaw = modelYaw;
			Widget.get(widgetId2).modelZoom = modelZoom;
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_WIDGET_MODEL) {
			int mediaId = buffer.readUnsignedShortAddLE();
			int widgetId3 = buffer.readUnsignedShortAddLE();
			Widget.get(widgetId3).mediaType = Widget.MEDIA_MODEL;
			Widget.get(widgetId3).mediaId = mediaId;
			return true;
		}
		/* Opcode 26: queued sound effect (soundId, loopCount, delay). */
		if (opcode == IncomingPacketOpcode.PLAY_SOUND_EFFECT) {
			int soundId = buffer.readUnsignedShort();
			int loopCount = buffer.readUnsignedByte();
			int delay = buffer.readUnsignedShort();
			client.packetSoundEffectQueue().queuePacketSound(soundId, loopCount, delay, client.lowMemory);
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_VARP_SMALL) {
			int varpId = buffer.readUnsignedShortAdd();
			byte varpValue = buffer.readByteSub();
			if (client.packetVarpState().acceptServerValue(varpId, varpValue)) {
				client.applyVarp(varpId);
				client.sidebarRedraw = true;
				if (client.packetInterfaceController().state().dialogueInterfaceId != -1)
					client.chatboxRedraw = true;
			}
			return true;
		}
		if (opcode == IncomingPacketOpcode.RESET_ENTITY_ANIMATIONS) {
			for (int playerIndex = 0; playerIndex < client.packetActorSynchronizer().players.length; playerIndex++)
				if (client.packetActorSynchronizer().players[playerIndex] != null)
					client.packetActorSynchronizer().players[playerIndex].sequence = -1;

			for (int npcIndex = 0; npcIndex < client.packetActorSynchronizer().npcs.length; npcIndex++)
				if (client.packetActorSynchronizer().npcs[npcIndex] != null)
					client.packetActorSynchronizer().npcs[npcIndex].sequence = -1;
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_MINIMAP_STATE) {
			client.packetMinimapRenderer().state = buffer.readUnsignedByte();
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_WIDGET_NPC_MODEL) {
			int npcId = buffer.readUnsignedShortAdd();
			int widgetId4 = buffer.readUnsignedShortLE();
			Widget.get(widgetId4).mediaType = Widget.MEDIA_NPC;
			Widget.get(widgetId4).mediaId = npcId;
			return true;
		}
		if (opcode == IncomingPacketOpcode.OPEN_CHATBOX_INTERFACE) {
			int chatboxInterfaceId = buffer.readUnsignedShort();
			client.packetWidgetRuntime().resetAnimations(chatboxInterfaceId);
			if (client.packetInterfaceController().state().sidebarOverlayInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().sidebarOverlayInterfaceId);
				client.sidebarRedraw = true;
				client.tabAreaRedraw = true;
			}
			if (client.packetInterfaceController().state().fullscreenInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().fullscreenInterfaceId);
				client.gameScreenRedraw = true;
			}
			if (client.packetInterfaceController().state().fullscreenOverlayInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().fullscreenOverlayInterfaceId);
			}
			if (client.packetInterfaceController().state().openInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().openInterfaceId);
			}
			if (client.packetInterfaceController().state().chatboxInterfaceId != chatboxInterfaceId) {
				client.unloadInterface(client.packetInterfaceController().state().chatboxInterfaceId);
				client.packetInterfaceController().state().chatboxInterfaceId = chatboxInterfaceId;
			}
			client.packetInterfaceController().setActionPending(false);
			client.chatboxRedraw = true;
			return true;
		}
		/* Opcode 220: select background MIDI track. */
		if (opcode == IncomingPacketOpcode.PLAY_MUSIC) {
			int trackId = buffer.readUnsignedShortAddLE();
			client.packetMusicController().selectTrack(trackId, client.lowMemory, client.onDemandFetcher::request);
			return true;
		}
		/* Opcode 249: temporary MIDI track followed by delayed resume. */
		if (opcode == IncomingPacketOpcode.PLAY_TEMPORARY_MUSIC) {
			int trackId = buffer.readUnsignedShortLE();
			int resumeDelay = buffer.readMediumME();
			client.packetMusicController().playTemporaryTrack(trackId, resumeDelay, client.lowMemory, client.onDemandFetcher::request);
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_DIALOGUE_INTERFACE) {
			int dialogueInterfaceId = buffer.readShortLE();
			if (dialogueInterfaceId != client.packetInterfaceController().state().dialogueInterfaceId) {
				client.unloadInterface(client.packetInterfaceController().state().dialogueInterfaceId);
				client.packetInterfaceController().state().dialogueInterfaceId = dialogueInterfaceId;
			}
			client.chatboxRedraw = true;
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_WIDGET_COLOR) {
			int widgetId5 = buffer.readUnsignedShort();
			int packedColor = buffer.readUnsignedShortAdd();
			int red5 = packedColor >> 10 & 0x1f;
			int green5 = packedColor >> 5 & 0x1f;
			int blue5 = packedColor & 0x1f;
			Widget.get(widgetId5).color = (red5 << 19) + (green5 << 11) + (blue5 << 3);
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_PLAYER_ACTION) {
			int actionSlot = buffer.readUnsignedByteNeg();
			String actionText = buffer.readString();
			// Protocol flag name is unknown; zero marks the player action as low-priority
			// in this revision.
			int priorityFlag = buffer.readUnsignedByte();
			if (actionSlot >= 1 && actionSlot <= 5) {
				if (actionText.equalsIgnoreCase("null"))
					actionText = null;
				client.playerActions[actionSlot - 1] = actionText;
				client.playerActionLowPriority[actionSlot - 1] = priorityFlag == 0;
			}
			return true;
		}
		if (opcode == IncomingPacketOpcode.OPEN_NAME_INPUT_DIALOG) {
			client.packetChatController().openInputDialog(2);
			client.chatboxRedraw = true;
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_CHAT_MODES) {
			client.packetChatController().setPublicMode(buffer.readUnsignedByte());
			client.packetChatController().setPrivateMode(buffer.readUnsignedByte());
			client.packetChatController().setTradeMode(buffer.readUnsignedByte());
			client.chatModesRedraw = true;
			client.chatboxRedraw = true;
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_HINT_ICON) {
			client.hintIconType = buffer.readUnsignedByte();
			if (client.hintIconType == 1)
				client.hintNpcIndex = buffer.readUnsignedShort();
			if (client.hintIconType >= 2 && client.hintIconType <= 6) {
				if (client.hintIconType == 2) {
					client.hintOffsetX = 64;
					client.hintOffsetY = 64;
				}
				if (client.hintIconType == 3) {
					client.hintOffsetX = 0;
					client.hintOffsetY = 64;
				}
				if (client.hintIconType == 4) {
					client.hintOffsetX = 128;
					client.hintOffsetY = 64;
				}
				if (client.hintIconType == 5) {
					client.hintOffsetX = 64;
					client.hintOffsetY = 0;
				}
				if (client.hintIconType == 6) {
					client.hintOffsetX = 64;
					client.hintOffsetY = 128;
				}
				client.hintIconType = 2;
				client.hintTileX = buffer.readUnsignedShort();
				client.hintTileY = buffer.readUnsignedShort();
				client.hintHeight = buffer.readUnsignedByte();
			}
			if (client.hintIconType == 10)
				client.hintPlayerIndex = buffer.readUnsignedShort();
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_CINEMATIC_CAMERA_LOOK_AT) {
			int tileX = buffer.readUnsignedByte();
			int tileY = buffer.readUnsignedByte();
			int heightOffset = buffer.readUnsignedShort();
			int baseSpeed = buffer.readUnsignedByte();
			int scale = buffer.readUnsignedByte();
			client.packetCameraController().setCinematicLookAt(tileX, tileY, heightOffset, baseSpeed, scale, client.packetWorldState(), client.currentPlane);
			return true;
		}

		if (opcode == IncomingPacketOpcode.LOGOUT) {
			client.logout();
			return false;
		}
		if (opcode == IncomingPacketOpcode.SET_VARP_LARGE) {
			int varpValue2 = buffer.readIntIME();
			int varpId2 = buffer.readUnsignedShortLE();
			if (client.packetVarpState().acceptServerValue(varpId2, varpValue2)) {
				client.applyVarp(varpId2);
				client.sidebarRedraw = true;
				if (client.packetInterfaceController().state().dialogueInterfaceId != -1)
					client.chatboxRedraw = true;
			}
			return true;
		}
		if (opcode == IncomingPacketOpcode.CLOSE_INTERFACES) {
			if (client.packetInterfaceController().state().sidebarOverlayInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().sidebarOverlayInterfaceId);
				client.sidebarRedraw = true;
				client.tabAreaRedraw = true;
			}
			if (client.packetInterfaceController().state().chatboxInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().chatboxInterfaceId);
				client.chatboxRedraw = true;
			}
			if (client.packetInterfaceController().state().fullscreenInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().fullscreenInterfaceId);
				client.gameScreenRedraw = true;
			}
			if (client.packetInterfaceController().state().fullscreenOverlayInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().fullscreenOverlayInterfaceId);
			}
			if (client.packetInterfaceController().state().openInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().openInterfaceId);
			}
			if (client.packetChatController().inputDialogState() != 0) {
				client.packetChatController().setInputDialogState(0);
				client.chatboxRedraw = true;
			}
			client.packetInterfaceController().setActionPending(false);
			return true;
		}
		if (opcode == IncomingPacketOpcode.ACCOUNT_INFO) {
			client.lastPasswordChangeDate = buffer.readUnsignedShortLE();
			buffer.readUnsignedShortAddLE();
			buffer.readUnsignedShort();
			buffer.readUnsignedShort();
			client.accountCurrentDay = buffer.readUnsignedShortLE();
			client.unreadMessageCount = buffer.readUnsignedShortAdd();
			client.lastLoginDay = buffer.readUnsignedShortAdd();
			client.membershipDays = buffer.readUnsignedShort();
			client.lastLoginIp = buffer.readIntLE();
			client.recoveryQuestionsDate = buffer.readUnsignedShortAddLE();
			buffer.readUnsignedByteAdd();
			Signlink.lookupDns(Ipv4Address.format(client.lastLoginIp));
			return true;
		}
		if (opcode == IncomingPacketOpcode.SERVER_MESSAGE) {
			String serverMessage = buffer.readString();
			if (serverMessage.endsWith(":tradereq:")) {
				String tradeRequester = serverMessage.substring(0, serverMessage.indexOf(":"));
				long tradeRequesterEncoded = Base37.encode(tradeRequester);
				boolean ignored = client.packetSocialManager().isIgnored(tradeRequesterEncoded);
				if (!ignored && client.tutorialIslandFlag == 0)
					client.addChatMessage(tradeRequester, "wishes to trade with you.", ChatMessageType.TRADE_REQUEST);
			} else if (serverMessage.endsWith(":duelreq:")) {
				String duelRequester = serverMessage.substring(0, serverMessage.indexOf(":"));
				long duelRequesterEncoded = Base37.encode(duelRequester);
				boolean ignored = client.packetSocialManager().isIgnored(duelRequesterEncoded);
				if (!ignored && client.tutorialIslandFlag == 0)
					client.addChatMessage(duelRequester, "wishes to duel with you.", ChatMessageType.CHALLENGE_REQUEST);
			} else if (serverMessage.endsWith(":chalreq:")) {
				String challengeRequester = serverMessage.substring(0, serverMessage.indexOf(":"));
				long challengeRequesterEncoded = Base37.encode(challengeRequester);
				boolean ignored = client.packetSocialManager().isIgnored(challengeRequesterEncoded);
				if (!ignored && client.tutorialIslandFlag == 0) {
					String challengeText = serverMessage.substring(serverMessage.indexOf(":") + 1,
							serverMessage.length() - 9);
					client.addChatMessage(challengeRequester, challengeText, ChatMessageType.CHALLENGE_REQUEST);
				}
			} else {
				client.addChatMessage("", serverMessage, ChatMessageType.GAME);
			}
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_WALKABLE_INTERFACE) {
			int walkableInterfaceId = buffer.readSignedShort();
			if (walkableInterfaceId >= 0)
				client.packetWidgetRuntime().resetAnimations(walkableInterfaceId);
			if (walkableInterfaceId != client.packetInterfaceController().state().walkableInterfaceId) {
				client.unloadInterface(client.packetInterfaceController().state().walkableInterfaceId);
				client.packetInterfaceController().state().walkableInterfaceId = walkableInterfaceId;
			}
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_WIDGET_MOUSEOVER) {
			boolean mouseoverTriggered = buffer.readUnsignedByte() == 1;
			int widgetId6 = buffer.readUnsignedShort();
			Widget.get(widgetId6).mouseoverTriggered = mouseoverTriggered;
			return true;
		}
		if (opcode == IncomingPacketOpcode.UPDATE_WEIGHT) {
			if (client.packetInterfaceController().state().selectedTab == 12)
				client.sidebarRedraw = true;
			client.weight = buffer.readSignedShort();
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_MULTI_COMBAT) {
			client.multiCombatZone = buffer.readUnsignedByte();
			return true;
		}
		if (opcode == IncomingPacketOpcode.CLEAR_DESTINATION) {
			client.destinationX = 0;
			return true;
		}
		if (opcode == IncomingPacketOpcode.OPEN_MAIN_AND_SIDEBAR_INTERFACES) {
			int openInterfaceId = buffer.readUnsignedShortAdd();
			int sidebarOverlayInterfaceId = buffer.readUnsignedShortAddLE();
			if (client.packetInterfaceController().state().chatboxInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().chatboxInterfaceId);
				client.chatboxRedraw = true;
			}
			if (client.packetInterfaceController().state().fullscreenInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().fullscreenInterfaceId);
				client.gameScreenRedraw = true;
			}
			if (client.packetInterfaceController().state().fullscreenOverlayInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().fullscreenOverlayInterfaceId);
			}
			if (client.packetInterfaceController().state().openInterfaceId != openInterfaceId) {
				client.unloadInterface(client.packetInterfaceController().state().openInterfaceId);
				client.packetInterfaceController().state().openInterfaceId = openInterfaceId;
			}
			if (client.packetInterfaceController().state().sidebarOverlayInterfaceId != sidebarOverlayInterfaceId) {
				client.unloadInterface(client.packetInterfaceController().state().sidebarOverlayInterfaceId);
				client.packetInterfaceController().state().sidebarOverlayInterfaceId = sidebarOverlayInterfaceId;
			}
			if (client.packetChatController().inputDialogState() != 0) {
				client.packetChatController().setInputDialogState(0);
				client.chatboxRedraw = true;
			}
			client.sidebarRedraw = true;
			client.tabAreaRedraw = true;
			client.packetInterfaceController().setActionPending(false);
			return true;
		}
		if (opcode == IncomingPacketOpcode.CAMERA_SHAKE) {
			int shakeIndex = buffer.readUnsignedByte();
			int randomAmplitude = buffer.readUnsignedByte();
			int sineAmplitude = buffer.readUnsignedByte();
			int frequency = buffer.readUnsignedByte();
			client.packetCameraController().configureShake(shakeIndex, randomAmplitude, sineAmplitude, frequency);
			return true;
		}
		if (opcode == IncomingPacketOpcode.UPDATE_WIDGET_ITEMS_PARTIAL) {
			client.sidebarRedraw = true;
			int widgetId7 = buffer.readUnsignedShort();
			Widget inventoryWidget = Widget.get(widgetId7);
			while (buffer.position < packetSize) {
				int slot = buffer.readUnsignedSmart();
				int itemId = buffer.readUnsignedShort();
				int amount = buffer.readUnsignedByte();
				if (amount == 255)
					amount = buffer.readInt();
				if (slot >= 0 && slot < inventoryWidget.itemIds.length) {
					inventoryWidget.itemIds[slot] = itemId;
					inventoryWidget.itemAmounts[slot] = amount;
				}
			}
			return true;
		}
		if (opcode == IncomingPacketOpcode.FRIEND_STATUS) {
			long encodedName = buffer.readLong();
			int world = buffer.readUnsignedByte();
			if (client.packetSocialManager().updateFriend(encodedName, world, client.currentWorldId, client::addChatMessage))
				client.sidebarRedraw = true;
			return true;
		}
		if (opcode == IncomingPacketOpcode.OPEN_AMOUNT_INPUT_DIALOG) {
			client.packetChatController().openInputDialog(1);
			client.chatboxRedraw = true;
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_SELECTED_TAB) {
			client.packetInterfaceController().state().selectedTab = buffer.readUnsignedByteNeg();
			client.sidebarRedraw = true;
			client.tabAreaRedraw = true;
			return true;
		}
		if (opcode == IncomingPacketOpcode.CLEAR_ZONE) {
			int zoneBaseY = buffer.readUnsignedByteSub();
			int zoneBaseX = buffer.readUnsignedByteNeg();
			client.packetZoneUpdates().setZoneBase(zoneBaseX, zoneBaseY);
			client.packetWorldState().clearZone(client.currentPlane, zoneBaseX, zoneBaseY);
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_WIDGET_PLAYER_MODEL) {
			int widgetId8 = buffer.readUnsignedShortAddLE();
			Widget.get(widgetId8).mediaType = Widget.MEDIA_PLAYER;
			if (client.localPlayer.npcDefinition == null)
				Widget.get(widgetId8).mediaId = (client.localPlayer.bodyColors[0] << 25) + (client.localPlayer.bodyColors[4] << 20)
						+ (client.localPlayer.equipment[0] << 15) + (client.localPlayer.equipment[8] << 10)
						+ (client.localPlayer.equipment[11] << 5) + client.localPlayer.equipment[1];
			else
				Widget.get(widgetId8).mediaId = (int) (0x12345678L + client.localPlayer.npcDefinition.id);
			return true;
		}
		if (opcode == IncomingPacketOpcode.PRIVATE_MESSAGE) {
			long senderEncodedName = buffer.readLong();
			int privateMessageId = buffer.readInt();
			int senderRights = buffer.readUnsignedByte();
			boolean duplicateOrIgnored = client.packetChatController().history().hasRecentPrivateMessage(privateMessageId);

			if (senderRights <= 1 && client.packetSocialManager().isIgnored(senderEncodedName))
				duplicateOrIgnored = true;
			if (!duplicateOrIgnored && client.tutorialIslandFlag == 0)
				try {
					client.packetChatController().history().rememberPrivateMessage(privateMessageId);
					String privateMessage = ChatCodec.decode(buffer,
							packetSize - 13);
					if (senderRights != 3)
						privateMessage = Censor.censor(privateMessage);
					if (senderRights == 2 || senderRights == 3)
						client.addChatMessage("@cr2@" + TextFormatter.formatDisplayName(Base37.decode(senderEncodedName)),
								privateMessage, 7);
					else if (senderRights == 1)
						client.addChatMessage("@cr1@" + TextFormatter.formatDisplayName(Base37.decode(senderEncodedName)),
								privateMessage, 7);
					else
						client.addChatMessage(TextFormatter.formatDisplayName(Base37.decode(senderEncodedName)),
								privateMessage, 3);
				} catch (Exception exception1) {
					Signlink.reportError("cde1");
				}
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
		if (opcode == IncomingPacketOpcode.OPEN_MAIN_INTERFACE) {
			int openInterfaceId2 = buffer.readUnsignedShortAddLE();
			client.packetWidgetRuntime().resetAnimations(openInterfaceId2);
			if (client.packetInterfaceController().state().sidebarOverlayInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().sidebarOverlayInterfaceId);
				client.sidebarRedraw = true;
				client.tabAreaRedraw = true;
			}
			if (client.packetInterfaceController().state().chatboxInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().chatboxInterfaceId);
				client.chatboxRedraw = true;
			}
			if (client.packetInterfaceController().state().fullscreenInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().fullscreenInterfaceId);
				client.gameScreenRedraw = true;
			}
			if (client.packetInterfaceController().state().fullscreenOverlayInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().fullscreenOverlayInterfaceId);
			}
			if (client.packetInterfaceController().state().openInterfaceId != openInterfaceId2) {
				client.unloadInterface(client.packetInterfaceController().state().openInterfaceId);
				client.packetInterfaceController().state().openInterfaceId = openInterfaceId2;
			}
			if (client.packetChatController().inputDialogState() != 0) {
				client.packetChatController().setInputDialogState(0);
				client.chatboxRedraw = true;
			}
			client.packetInterfaceController().setActionPending(false);
			return true;
		}
		if (opcode == IncomingPacketOpcode.OPEN_SIDEBAR_INTERFACE) {
			int sidebarOverlayInterfaceId2 = buffer.readUnsignedShortAddLE();
			client.packetWidgetRuntime().resetAnimations(sidebarOverlayInterfaceId2);
			if (client.packetInterfaceController().state().chatboxInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().chatboxInterfaceId);
				client.chatboxRedraw = true;
			}
			if (client.packetInterfaceController().state().fullscreenInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().fullscreenInterfaceId);
				client.gameScreenRedraw = true;
			}
			if (client.packetInterfaceController().state().fullscreenOverlayInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().fullscreenOverlayInterfaceId);
			}
			if (client.packetInterfaceController().state().openInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().openInterfaceId);
			}
			if (client.packetInterfaceController().state().sidebarOverlayInterfaceId != sidebarOverlayInterfaceId2) {
				client.unloadInterface(client.packetInterfaceController().state().sidebarOverlayInterfaceId);
				client.packetInterfaceController().state().sidebarOverlayInterfaceId = sidebarOverlayInterfaceId2;
			}
			if (client.packetChatController().inputDialogState() != 0) {
				client.packetChatController().setInputDialogState(0);
				client.chatboxRedraw = true;
			}
			client.sidebarRedraw = true;
			client.tabAreaRedraw = true;
			client.packetInterfaceController().setActionPending(false);
			return true;
		}
		if (opcode == IncomingPacketOpcode.UPDATE_SKILL) {
			client.sidebarRedraw = true;
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
		if (opcode == IncomingPacketOpcode.UPDATE_WIDGET_ITEMS) {
			client.sidebarRedraw = true;
			int widgetId9 = buffer.readUnsignedShort();
			Widget inventoryWidget2 = Widget.get(widgetId9);
			int itemCount = buffer.readUnsignedShort();
			for (int slot2 = 0; slot2 < itemCount; slot2++) {
				inventoryWidget2.itemIds[slot2] = buffer.readUnsignedShortAddLE();
				int amount2 = buffer.readUnsignedByteNeg();
				if (amount2 == 255)
					amount2 = buffer.readIntLE();
				inventoryWidget2.itemAmounts[slot2] = amount2;
			}

			for (int slot3 = itemCount; slot3 < inventoryWidget2.itemIds.length; slot3++) {
				inventoryWidget2.itemIds[slot3] = 0;
				inventoryWidget2.itemAmounts[slot3] = 0;
			}
			return true;
		}
		if (opcode == IncomingPacketOpcode.REBUILD_REGION || opcode == IncomingPacketOpcode.REBUILD_INSTANCED_REGION) {
			RegionManager.RegionShift shift = client.packetRegionManager().decodeRebuild(buffer,
					opcode, client.onDemandFetcher, client.packetActorSynchronizer(), client.packetWorldState(), client.destinationX,
					client.destinationY);
			if (shift.changed) {
				client.destinationX = shift.destinationX;
				client.destinationY = shift.destinationY;
				client.packetCameraController().cinematic = false;
				client.drawGameLoadingMessage(null, "Loading - please wait.");
			}
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_SYSTEM_UPDATE_TIMER) {
			client.systemUpdateTimer = buffer.readUnsignedShortLE() * 30;
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
		if (opcode == IncomingPacketOpcode.UPDATE_RUN_ENERGY) {
			if (client.packetInterfaceController().state().selectedTab == 12)
				client.sidebarRedraw = true;
			client.runEnergy = buffer.readUnsignedByte();
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_WIDGET_ITEM_MODEL) {
			int zoomDivisor = buffer.readUnsignedShort();
			int itemId2 = buffer.readUnsignedShortLE();
			int widgetId10 = buffer.readUnsignedShortAddLE();
			if (itemId2 == ProtocolConstants.NULL_ID) {
				Widget.get(widgetId10).mediaType = Widget.MEDIA_NONE;
				return true;
			} else {
				ItemDefinition itemDefinition = ItemDefinition.lookup(itemId2);
				Widget.get(widgetId10).mediaType = Widget.MEDIA_ITEM;
				Widget.get(widgetId10).mediaId = itemId2;
				Widget.get(widgetId10).modelPitch = itemDefinition.xan2d;
				Widget.get(widgetId10).modelYaw = itemDefinition.yan2d;
				Widget.get(widgetId10).modelZoom = (itemDefinition.zoom2d * 100) / zoomDivisor;
				return true;
			}
		}
		if (opcode == IncomingPacketOpcode.SET_CINEMATIC_CAMERA_POSITION) {
			int tileX = buffer.readUnsignedByte();
			int tileY = buffer.readUnsignedByte();
			int heightOffset = buffer.readUnsignedShort();
			int baseSpeed = buffer.readUnsignedByte();
			int scale = buffer.readUnsignedByte();
			client.packetCameraController().setCinematicPosition(tileX, tileY, heightOffset, baseSpeed, scale, client.packetWorldState(),
					client.currentPlane);
			return true;
		}

		if (opcode == IncomingPacketOpcode.SET_WIDGET_ANIMATION) {
			int widgetId11 = buffer.readUnsignedShortAddLE();
			int animationId = buffer.readShortAdd();
			Widget animationWidget = Widget.get(widgetId11);
			if (animationWidget.animationId != animationId || animationId == -1) {
				animationWidget.animationId = animationId;
				animationWidget.animationFrame = 0;
				animationWidget.animationCycle = 0;
			}
			return true;
		}
		if (opcode == IncomingPacketOpcode.NPC_UPDATE) {
			client.packetActorSynchronizer().decodeNpcUpdate(buffer, packetSize, client.gameCycle,
					client.packetLoginUsername());
			return true;
		}
		if (opcode == IncomingPacketOpcode.UPDATE_IGNORE_LIST) {
			client.packetSocialManager().replaceIgnoreList(buffer, packetSize);
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_TAB_INTERFACE) {
			int tabIndex = buffer.readUnsignedByteSub();
			int interfaceId = buffer.readUnsignedShortAdd();
			if (interfaceId == ProtocolConstants.NULL_ID)
				interfaceId = -1;
			if (client.packetInterfaceController().state().tabInterfaceIds[tabIndex] != interfaceId) {
				client.unloadInterface(client.packetInterfaceController().state().tabInterfaceIds[tabIndex]);
				client.packetInterfaceController().state().tabInterfaceIds[tabIndex] = interfaceId;
			}
			client.sidebarRedraw = true;
			client.tabAreaRedraw = true;
			return true;
		}
		if (opcode == IncomingPacketOpcode.CLEAR_WIDGET_ITEMS) {
			int widgetId12 = buffer.readUnsignedShortLE();
			Widget inventoryWidget3 = Widget.get(widgetId12);
			for (int slot4 = 0; slot4 < inventoryWidget3.itemIds.length; slot4++) {
				inventoryWidget3.itemIds[slot4] = -1;
				inventoryWidget3.itemIds[slot4] = 0;
			}
			return true;
		}
		if (opcode == IncomingPacketOpcode.FLASH_TAB) {
			client.packetInterfaceController().state().flashingTab = buffer.readUnsignedByte();
			if (client.packetInterfaceController().state().flashingTab == client.packetInterfaceController().state().selectedTab) {
				if (client.packetInterfaceController().state().flashingTab == 3)
					client.packetInterfaceController().state().selectedTab = 1;
				else
					client.sidebarRedraw = true;
			}
			return true;
		}
		if (opcode == IncomingPacketOpcode.RESET_CAMERA) {
			client.packetCameraController().stopCinematic();
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_LOCAL_PLAYER_INDEX) {
			client.accountMembershipStatus = buffer.readUnsignedByte();
			client.localPlayerServerIndex = buffer.readUnsignedShortLE();
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_ZONE_BASE) {
			client.packetZoneUpdates().setZoneBase(buffer.readUnsignedByteNeg(),
					buffer.readUnsignedByteAdd());
			return true;
		}
		if (opcode == IncomingPacketOpcode.OPEN_FULLSCREEN_INTERFACES) {
			int fullscreenOverlayInterfaceId = buffer.readUnsignedShortLE();
			int fullscreenInterfaceId = buffer.readUnsignedShortAdd();
			client.packetWidgetRuntime().resetAnimations(fullscreenInterfaceId);
			if (fullscreenOverlayInterfaceId != -1)
				client.packetWidgetRuntime().resetAnimations(fullscreenOverlayInterfaceId);
			if (client.packetInterfaceController().state().openInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().openInterfaceId);
			}
			if (client.packetInterfaceController().state().sidebarOverlayInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().sidebarOverlayInterfaceId);
			}
			if (client.packetInterfaceController().state().chatboxInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().chatboxInterfaceId);
			}
			if (client.packetInterfaceController().state().fullscreenInterfaceId != fullscreenInterfaceId) {
				client.unloadInterface(client.packetInterfaceController().state().fullscreenInterfaceId);
				client.packetInterfaceController().state().fullscreenInterfaceId = fullscreenInterfaceId;
			}
			if (client.packetInterfaceController().state().fullscreenOverlayInterfaceId != fullscreenInterfaceId) {
				client.unloadInterface(client.packetInterfaceController().state().fullscreenOverlayInterfaceId);
				client.packetInterfaceController().state().fullscreenOverlayInterfaceId = fullscreenOverlayInterfaceId;
			}
			client.packetChatController().setInputDialogState(0);
			client.packetInterfaceController().setActionPending(false);
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_FRIEND_LIST_STATUS) {
			client.packetSocialManager().friendListStatus = buffer.readUnsignedByte();
			client.sidebarRedraw = true;
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_WIDGET_MODEL_ROTATION_SPEED) {
			int pitchRotationSpeed = buffer.readUnsignedShort();
			int widgetId13 = buffer.readUnsignedShortAdd();
			int yawRotationSpeed = buffer.readUnsignedShortLE();
			Widget.get(widgetId13).modelRotationSpeed = (pitchRotationSpeed << 16) + yawRotationSpeed;
			return true;
		}
		if (opcode == IncomingPacketOpcode.PLAYER_UPDATE) {
			client.currentPlane = client.packetActorSynchronizer().decodePlayerUpdate(buffer, packetSize,
					client.gameCycle, client.currentPlane, client.packetLoginUsername(), client.chatBuffer, client.packetActorChatHandler());
			client.packetRegionManager().playerUpdateReceived();
			return true;
		}
		if (opcode == IncomingPacketOpcode.SYNCHRONIZE_VARPS) {
			client.packetVarpState().synchronizeToShadow(varpId3 -> {
				client.applyVarp(varpId3);
				client.sidebarRedraw = true;
			});
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_WIDGET_TEXT) {
			int widgetId14 = buffer.readUnsignedShortAddLE();
			String widgetText = buffer.readString();
			Widget.get(widgetId14).text = widgetText;
			if (Widget.get(widgetId14).parentId == client.packetInterfaceController().state().tabInterfaceIds[client.packetInterfaceController().state().selectedTab])
				client.sidebarRedraw = true;
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_WIDGET_SCROLL_POSITION) {
			int widgetId15 = buffer.readUnsignedShort();
			int scrollY = buffer.readUnsignedShortAddLE();
			Widget scrollWidget = Widget.get(widgetId15);
			if (scrollWidget != null && scrollWidget.type == Widget.TYPE_CONTAINER) {
				if (scrollY < 0)
					scrollY = 0;
				if (scrollY > scrollWidget.scrollHeight - scrollWidget.height)
					scrollY = scrollWidget.scrollHeight - scrollWidget.height;
				scrollWidget.scrollY = scrollY;
			}
			return true;
		}
		Signlink.reportError("T1 - " + opcode + "," + packetSize + " - "
				+ client.networkSession.secondLastOpcode + "," + client.networkSession.thirdLastOpcode);
		client.logout();
		return true;
	}
}
