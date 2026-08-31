package rs2;

import rs2.cache.def.ItemDefinition;
import rs2.net.Buffer;
import rs2.net.IncomingPacketOpcode;
import rs2.net.ProtocolConstants;
import rs2.ui.Widget;

/**
 * Applies interface, widget, tab, and input-dialog packets to client UI state.
 *
 * <p>This application-layer domain handler is invoked only after
 * {@link ClientIncomingPacketHandler} has explicitly routed a recognized
 * revision-377 opcode to it.</p>
 */
final class InterfacePacketHandler {

	/** Client runtime receiving decoded packet effects. */
	private final Client client;

	/**
	 * Creates the interface packet handler.
	 *
	 * @param client client runtime receiving packet effects
	 */
	InterfacePacketHandler(Client client) {
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
		if (opcode == IncomingPacketOpcode.SET_WIDGET_POSITION) {
			int widgetYOffset = buffer.readSignedShortLE();
			int widgetXOffset = buffer.readSignedShortLE();
			int widgetId = buffer.readUnsignedShort();
			Widget widget = Widget.get(widgetId);
			widget.xOffset = widgetXOffset;
			widget.yOffset = widgetYOffset;
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_WIDGET_MODEL_TRANSFORM) {
			int modelPitch = buffer.readUnsignedShortAdd();
			int widgetId2 = buffer.readUnsignedShortLEAdd();
			int modelZoom = buffer.readUnsignedShortAdd();
			int modelYaw = buffer.readUnsignedShortLE();
			Widget.get(widgetId2).modelPitch = modelPitch;
			Widget.get(widgetId2).modelYaw = modelYaw;
			Widget.get(widgetId2).modelZoom = modelZoom;
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_WIDGET_MODEL) {
			int mediaId = buffer.readUnsignedShortLEAdd();
			int widgetId3 = buffer.readUnsignedShortLEAdd();
			Widget.get(widgetId3).mediaType = Widget.MEDIA_MODEL;
			Widget.get(widgetId3).mediaId = mediaId;
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
				client.requestSidebarRedraw();
				client.requestTabAreaRedraw();
			}
			if (client.packetInterfaceController().state().fullscreenInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().fullscreenInterfaceId);
				client.requestGameScreenRedraw();
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
			client.requestChatboxRedraw();
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_DIALOGUE_INTERFACE) {
			int dialogueInterfaceId = buffer.readSignedShortLE();
			if (dialogueInterfaceId != client.packetInterfaceController().state().dialogueInterfaceId) {
				client.unloadInterface(client.packetInterfaceController().state().dialogueInterfaceId);
				client.packetInterfaceController().state().dialogueInterfaceId = dialogueInterfaceId;
			}
			client.requestChatboxRedraw();
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
			client.requestChatboxRedraw();
			return true;
		}
		if (opcode == IncomingPacketOpcode.CLOSE_INTERFACES) {
			if (client.packetInterfaceController().state().sidebarOverlayInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().sidebarOverlayInterfaceId);
				client.requestSidebarRedraw();
				client.requestTabAreaRedraw();
			}
			if (client.packetInterfaceController().state().chatboxInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().chatboxInterfaceId);
				client.requestChatboxRedraw();
			}
			if (client.packetInterfaceController().state().fullscreenInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().fullscreenInterfaceId);
				client.requestGameScreenRedraw();
			}
			if (client.packetInterfaceController().state().fullscreenOverlayInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().fullscreenOverlayInterfaceId);
			}
			if (client.packetInterfaceController().state().openInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().openInterfaceId);
			}
			if (client.packetChatController().inputDialogState() != 0) {
				client.packetChatController().setInputDialogState(0);
				client.requestChatboxRedraw();
			}
			client.packetInterfaceController().setActionPending(false);
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
		if (opcode == IncomingPacketOpcode.OPEN_MAIN_AND_SIDEBAR_INTERFACES) {
			int openInterfaceId = buffer.readUnsignedShortAdd();
			int sidebarOverlayInterfaceId = buffer.readUnsignedShortLEAdd();
			if (client.packetInterfaceController().state().chatboxInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().chatboxInterfaceId);
				client.requestChatboxRedraw();
			}
			if (client.packetInterfaceController().state().fullscreenInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().fullscreenInterfaceId);
				client.requestGameScreenRedraw();
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
				client.requestChatboxRedraw();
			}
			client.requestSidebarRedraw();
			client.requestTabAreaRedraw();
			client.packetInterfaceController().setActionPending(false);
			return true;
		}
		if (opcode == IncomingPacketOpcode.UPDATE_WIDGET_ITEMS_PARTIAL) {
			client.requestSidebarRedraw();
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
		if (opcode == IncomingPacketOpcode.OPEN_AMOUNT_INPUT_DIALOG) {
			client.packetChatController().openInputDialog(1);
			client.requestChatboxRedraw();
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_SELECTED_TAB) {
			client.packetInterfaceController().state().selectedTab = buffer.readUnsignedByteNeg();
			client.requestSidebarRedraw();
			client.requestTabAreaRedraw();
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_WIDGET_PLAYER_MODEL) {
			int widgetId8 = buffer.readUnsignedShortLEAdd();
			Widget.get(widgetId8).mediaType = Widget.MEDIA_PLAYER;
			if (client.localPlayer.npcDefinition == null)
				Widget.get(widgetId8).mediaId = (client.localPlayer.bodyColors[0] << 25) + (client.localPlayer.bodyColors[4] << 20)
						+ (client.localPlayer.equipment[0] << 15) + (client.localPlayer.equipment[8] << 10)
						+ (client.localPlayer.equipment[11] << 5) + client.localPlayer.equipment[1];
			else
				Widget.get(widgetId8).mediaId = (int) (0x12345678L + client.localPlayer.npcDefinition.id);
			return true;
		}
		if (opcode == IncomingPacketOpcode.OPEN_MAIN_INTERFACE) {
			int openInterfaceId2 = buffer.readUnsignedShortLEAdd();
			client.packetWidgetRuntime().resetAnimations(openInterfaceId2);
			if (client.packetInterfaceController().state().sidebarOverlayInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().sidebarOverlayInterfaceId);
				client.requestSidebarRedraw();
				client.requestTabAreaRedraw();
			}
			if (client.packetInterfaceController().state().chatboxInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().chatboxInterfaceId);
				client.requestChatboxRedraw();
			}
			if (client.packetInterfaceController().state().fullscreenInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().fullscreenInterfaceId);
				client.requestGameScreenRedraw();
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
				client.requestChatboxRedraw();
			}
			client.packetInterfaceController().setActionPending(false);
			return true;
		}
		if (opcode == IncomingPacketOpcode.OPEN_SIDEBAR_INTERFACE) {
			int sidebarOverlayInterfaceId2 = buffer.readUnsignedShortLEAdd();
			client.packetWidgetRuntime().resetAnimations(sidebarOverlayInterfaceId2);
			if (client.packetInterfaceController().state().chatboxInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().chatboxInterfaceId);
				client.requestChatboxRedraw();
			}
			if (client.packetInterfaceController().state().fullscreenInterfaceId != -1) {
				client.unloadInterface(client.packetInterfaceController().state().fullscreenInterfaceId);
				client.requestGameScreenRedraw();
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
				client.requestChatboxRedraw();
			}
			client.requestSidebarRedraw();
			client.requestTabAreaRedraw();
			client.packetInterfaceController().setActionPending(false);
			return true;
		}
		if (opcode == IncomingPacketOpcode.UPDATE_WIDGET_ITEMS) {
			client.requestSidebarRedraw();
			int widgetId9 = buffer.readUnsignedShort();
			Widget inventoryWidget2 = Widget.get(widgetId9);
			int itemCount = buffer.readUnsignedShort();
			for (int slot2 = 0; slot2 < itemCount; slot2++) {
				inventoryWidget2.itemIds[slot2] = buffer.readUnsignedShortLEAdd();
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
		if (opcode == IncomingPacketOpcode.SET_WIDGET_ITEM_MODEL) {
			int zoomDivisor = buffer.readUnsignedShort();
			int itemId2 = buffer.readUnsignedShortLE();
			int widgetId10 = buffer.readUnsignedShortLEAdd();
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
		if (opcode == IncomingPacketOpcode.SET_WIDGET_ANIMATION) {
			int widgetId11 = buffer.readUnsignedShortLEAdd();
			int animationId = buffer.readSignedShortAdd();
			Widget animationWidget = Widget.get(widgetId11);
			if (animationWidget.animationId != animationId || animationId == -1) {
				animationWidget.animationId = animationId;
				animationWidget.animationFrame = 0;
				animationWidget.animationCycle = 0;
			}
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
			client.requestSidebarRedraw();
			client.requestTabAreaRedraw();
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
					client.requestSidebarRedraw();
			}
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
		if (opcode == IncomingPacketOpcode.SET_WIDGET_MODEL_ROTATION_SPEED) {
			int pitchRotationSpeed = buffer.readUnsignedShort();
			int widgetId13 = buffer.readUnsignedShortAdd();
			int yawRotationSpeed = buffer.readUnsignedShortLE();
			Widget.get(widgetId13).modelRotationSpeed = (pitchRotationSpeed << 16) + yawRotationSpeed;
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_WIDGET_TEXT) {
			int widgetId14 = buffer.readUnsignedShortLEAdd();
			String widgetText = buffer.readString();
			Widget.get(widgetId14).text = widgetText;
			if (Widget.get(widgetId14).parentId == client.packetInterfaceController().state().tabInterfaceIds[client.packetInterfaceController().state().selectedTab])
				client.requestSidebarRedraw();
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_WIDGET_SCROLL_POSITION) {
			int widgetId15 = buffer.readUnsignedShort();
			int scrollY = buffer.readUnsignedShortLEAdd();
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
		throw new IllegalArgumentException("Opcode " + opcode + " is not a interface packet");
	}
}
