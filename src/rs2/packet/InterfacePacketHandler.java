package rs2.packet;

import java.util.function.IntConsumer;
import java.util.function.Supplier;

import rs2.cache.def.ItemDefinition;
import rs2.chat.ChatController;
import rs2.game.entity.Player;
import rs2.net.Buffer;
import rs2.net.IncomingPacketOpcode;
import rs2.net.ProtocolConstants;
import rs2.ui.InterfaceController;
import rs2.ui.Widget;
import rs2.ui.WidgetRuntime;

/**
 * Applies interface, widget, tab, and input-dialog packets to client UI state.
 *
 * <p>
 * This application-layer domain handler is invoked only after
 * {@link PacketDomainDispatcher} has explicitly routed a recognized
 * revision-377 opcode to it.
 * </p>
 */
final class InterfacePacketHandler {

	/** Interface state mutated by interface packets. */
	private final InterfaceController interfaces;
	/** Widget runtime used to reset cache-defined interface animations. */
	private final WidgetRuntime widgets;
	/** Chat/input state mutated by interface packets. */
	private final ChatController chat;
	/** Supplies the current local player for player-model widget packets. */
	private final Supplier<Player> localPlayer;
	/** Player interaction labels updated by server packets. */
	private final String[] playerActions;
	/** Low-priority flags paired with {@link #playerActions}. */
	private final boolean[] playerActionLowPriority;
	/** Unloads cache-defined interface groups no longer displayed. */
	private final IntConsumer unloadInterface;
	/** Receives redraw requests caused by interface packet effects. */
	private final InterfaceController.RedrawSink redraw;

	/**
	 * Creates the interface packet handler from its exact application capabilities.
	 *
	 * @param interfaces              interface state owner
	 * @param widgets                 widget runtime
	 * @param chat                    chat/input state owner
	 * @param localPlayer             current local-player supplier
	 * @param playerActions           player interaction labels
	 * @param playerActionLowPriority player interaction priority flags
	 * @param unloadInterface         interface-unload callback
	 * @param redraw                  redraw callback
	 */
	InterfacePacketHandler(InterfaceController interfaces, WidgetRuntime widgets, ChatController chat,
			Supplier<Player> localPlayer, String[] playerActions, boolean[] playerActionLowPriority,
			IntConsumer unloadInterface, InterfaceController.RedrawSink redraw) {
		this.interfaces = interfaces;
		this.widgets = widgets;
		this.chat = chat;
		this.localPlayer = localPlayer;
		this.playerActions = playerActions;
		this.playerActionLowPriority = playerActionLowPriority;
		this.unloadInterface = unloadInterface;
		this.redraw = redraw;
	}

	/**
	 * Applies one packet already routed to this domain.
	 *
	 * @param opcode     decoded revision-377 opcode
	 * @param buffer     payload buffer positioned at zero
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
			widgets.resetAnimations(chatboxInterfaceId);
			if (interfaces.state().sidebarOverlayInterfaceId != -1) {
				unloadInterface.accept(interfaces.state().sidebarOverlayInterfaceId);
				redraw.redrawSidebar();
				redraw.redrawTabs();
			}
			if (interfaces.state().fullscreenInterfaceId != -1) {
				unloadInterface.accept(interfaces.state().fullscreenInterfaceId);
				redraw.redrawGameScreen();
			}
			if (interfaces.state().fullscreenOverlayInterfaceId != -1) {
				unloadInterface.accept(interfaces.state().fullscreenOverlayInterfaceId);
			}
			if (interfaces.state().openInterfaceId != -1) {
				unloadInterface.accept(interfaces.state().openInterfaceId);
			}
			if (interfaces.state().chatboxInterfaceId != chatboxInterfaceId) {
				unloadInterface.accept(interfaces.state().chatboxInterfaceId);
				interfaces.state().chatboxInterfaceId = chatboxInterfaceId;
			}
			interfaces.setActionPending(false);
			redraw.redrawChatbox();
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_DIALOGUE_INTERFACE) {
			int dialogueInterfaceId = buffer.readSignedShortLE();
			if (dialogueInterfaceId != interfaces.state().dialogueInterfaceId) {
				unloadInterface.accept(interfaces.state().dialogueInterfaceId);
				interfaces.state().dialogueInterfaceId = dialogueInterfaceId;
			}
			redraw.redrawChatbox();
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
				playerActions[actionSlot - 1] = actionText;
				playerActionLowPriority[actionSlot - 1] = priorityFlag == 0;
			}
			return true;
		}
		if (opcode == IncomingPacketOpcode.OPEN_NAME_INPUT_DIALOG) {
			chat.openInputDialog(2);
			redraw.redrawChatbox();
			return true;
		}
		if (opcode == IncomingPacketOpcode.CLOSE_INTERFACES) {
			interfaces.closeAll(redraw);
			if (chat.inputDialogState() != 0) {
				chat.setInputDialogState(0);
				redraw.redrawChatbox();
			}
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_WALKABLE_INTERFACE) {
			int walkableInterfaceId = buffer.readSignedShort();
			if (walkableInterfaceId >= 0)
				widgets.resetAnimations(walkableInterfaceId);
			if (walkableInterfaceId != interfaces.state().walkableInterfaceId) {
				unloadInterface.accept(interfaces.state().walkableInterfaceId);
				interfaces.state().walkableInterfaceId = walkableInterfaceId;
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
			if (interfaces.state().chatboxInterfaceId != -1) {
				unloadInterface.accept(interfaces.state().chatboxInterfaceId);
				redraw.redrawChatbox();
			}
			if (interfaces.state().fullscreenInterfaceId != -1) {
				unloadInterface.accept(interfaces.state().fullscreenInterfaceId);
				redraw.redrawGameScreen();
			}
			if (interfaces.state().fullscreenOverlayInterfaceId != -1) {
				unloadInterface.accept(interfaces.state().fullscreenOverlayInterfaceId);
			}
			if (interfaces.state().openInterfaceId != openInterfaceId) {
				unloadInterface.accept(interfaces.state().openInterfaceId);
				interfaces.state().openInterfaceId = openInterfaceId;
			}
			if (interfaces.state().sidebarOverlayInterfaceId != sidebarOverlayInterfaceId) {
				unloadInterface.accept(interfaces.state().sidebarOverlayInterfaceId);
				interfaces.state().sidebarOverlayInterfaceId = sidebarOverlayInterfaceId;
			}
			if (chat.inputDialogState() != 0) {
				chat.setInputDialogState(0);
				redraw.redrawChatbox();
			}
			redraw.redrawSidebar();
			redraw.redrawTabs();
			interfaces.setActionPending(false);
			return true;
		}
		if (opcode == IncomingPacketOpcode.UPDATE_WIDGET_ITEMS_PARTIAL) {
			redraw.redrawSidebar();
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
			chat.openInputDialog(1);
			redraw.redrawChatbox();
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_SELECTED_TAB) {
			interfaces.state().selectedTab = buffer.readUnsignedByteNeg();
			redraw.redrawSidebar();
			redraw.redrawTabs();
			return true;
		}
		if (opcode == IncomingPacketOpcode.SET_WIDGET_PLAYER_MODEL) {
			int widgetId8 = buffer.readUnsignedShortLEAdd();
			Widget.get(widgetId8).mediaType = Widget.MEDIA_PLAYER;
			if (localPlayer.get().npcDefinition == null)
				Widget.get(widgetId8).mediaId = (localPlayer.get().bodyColors[0] << 25)
						+ (localPlayer.get().bodyColors[4] << 20) + (localPlayer.get().equipment[0] << 15)
						+ (localPlayer.get().equipment[8] << 10) + (localPlayer.get().equipment[11] << 5)
						+ localPlayer.get().equipment[1];
			else
				Widget.get(widgetId8).mediaId = (int) (0x12345678L + localPlayer.get().npcDefinition.id);
			return true;
		}
		if (opcode == IncomingPacketOpcode.OPEN_MAIN_INTERFACE) {
			int openInterfaceId2 = buffer.readUnsignedShortLEAdd();
			widgets.resetAnimations(openInterfaceId2);
			if (interfaces.state().sidebarOverlayInterfaceId != -1) {
				unloadInterface.accept(interfaces.state().sidebarOverlayInterfaceId);
				redraw.redrawSidebar();
				redraw.redrawTabs();
			}
			if (interfaces.state().chatboxInterfaceId != -1) {
				unloadInterface.accept(interfaces.state().chatboxInterfaceId);
				redraw.redrawChatbox();
			}
			if (interfaces.state().fullscreenInterfaceId != -1) {
				unloadInterface.accept(interfaces.state().fullscreenInterfaceId);
				redraw.redrawGameScreen();
			}
			if (interfaces.state().fullscreenOverlayInterfaceId != -1) {
				unloadInterface.accept(interfaces.state().fullscreenOverlayInterfaceId);
			}
			if (interfaces.state().openInterfaceId != openInterfaceId2) {
				unloadInterface.accept(interfaces.state().openInterfaceId);
				interfaces.state().openInterfaceId = openInterfaceId2;
			}
			if (chat.inputDialogState() != 0) {
				chat.setInputDialogState(0);
				redraw.redrawChatbox();
			}
			interfaces.setActionPending(false);
			return true;
		}
		if (opcode == IncomingPacketOpcode.OPEN_SIDEBAR_INTERFACE) {
			int sidebarOverlayInterfaceId2 = buffer.readUnsignedShortLEAdd();
			widgets.resetAnimations(sidebarOverlayInterfaceId2);
			if (interfaces.state().chatboxInterfaceId != -1) {
				unloadInterface.accept(interfaces.state().chatboxInterfaceId);
				redraw.redrawChatbox();
			}
			if (interfaces.state().fullscreenInterfaceId != -1) {
				unloadInterface.accept(interfaces.state().fullscreenInterfaceId);
				redraw.redrawGameScreen();
			}
			if (interfaces.state().fullscreenOverlayInterfaceId != -1) {
				unloadInterface.accept(interfaces.state().fullscreenOverlayInterfaceId);
			}
			if (interfaces.state().openInterfaceId != -1) {
				unloadInterface.accept(interfaces.state().openInterfaceId);
			}
			if (interfaces.state().sidebarOverlayInterfaceId != sidebarOverlayInterfaceId2) {
				unloadInterface.accept(interfaces.state().sidebarOverlayInterfaceId);
				interfaces.state().sidebarOverlayInterfaceId = sidebarOverlayInterfaceId2;
			}
			if (chat.inputDialogState() != 0) {
				chat.setInputDialogState(0);
				redraw.redrawChatbox();
			}
			redraw.redrawSidebar();
			redraw.redrawTabs();
			interfaces.setActionPending(false);
			return true;
		}
		if (opcode == IncomingPacketOpcode.UPDATE_WIDGET_ITEMS) {
			redraw.redrawSidebar();
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
			if (interfaces.state().tabInterfaceIds[tabIndex] != interfaceId) {
				unloadInterface.accept(interfaces.state().tabInterfaceIds[tabIndex]);
				interfaces.state().tabInterfaceIds[tabIndex] = interfaceId;
			}
			redraw.redrawSidebar();
			redraw.redrawTabs();
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
			interfaces.state().flashingTab = buffer.readUnsignedByte();
			if (interfaces.state().flashingTab == interfaces.state().selectedTab) {
				if (interfaces.state().flashingTab == 3)
					interfaces.state().selectedTab = 1;
				else
					redraw.redrawSidebar();
			}
			return true;
		}
		if (opcode == IncomingPacketOpcode.OPEN_FULLSCREEN_INTERFACES) {
			int fullscreenOverlayInterfaceId = buffer.readUnsignedShortLE();
			int fullscreenInterfaceId = buffer.readUnsignedShortAdd();
			widgets.resetAnimations(fullscreenInterfaceId);
			if (fullscreenOverlayInterfaceId != -1)
				widgets.resetAnimations(fullscreenOverlayInterfaceId);
			if (interfaces.state().openInterfaceId != -1) {
				unloadInterface.accept(interfaces.state().openInterfaceId);
			}
			if (interfaces.state().sidebarOverlayInterfaceId != -1) {
				unloadInterface.accept(interfaces.state().sidebarOverlayInterfaceId);
			}
			if (interfaces.state().chatboxInterfaceId != -1) {
				unloadInterface.accept(interfaces.state().chatboxInterfaceId);
			}
			if (interfaces.state().fullscreenInterfaceId != fullscreenInterfaceId) {
				unloadInterface.accept(interfaces.state().fullscreenInterfaceId);
				interfaces.state().fullscreenInterfaceId = fullscreenInterfaceId;
			}
			if (interfaces.state().fullscreenOverlayInterfaceId != fullscreenInterfaceId) {
				unloadInterface.accept(interfaces.state().fullscreenOverlayInterfaceId);
				interfaces.state().fullscreenOverlayInterfaceId = fullscreenOverlayInterfaceId;
			}
			chat.setInputDialogState(0);
			interfaces.setActionPending(false);
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
			if (Widget.get(widgetId14).parentId == interfaces.state().tabInterfaceIds[interfaces.state().selectedTab])
				redraw.redrawSidebar();
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
