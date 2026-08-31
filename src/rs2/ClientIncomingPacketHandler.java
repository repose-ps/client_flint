package rs2;

import rs2.net.Buffer;
import rs2.net.IncomingPacketHandler;
import rs2.net.IncomingPacketOpcode;
import rs2.sign.Signlink;

/**
 * Routes decoded revision-377 incoming packets to cohesive application domains.
 *
 * <p>Packet framing remains in {@code rs2.net}. This application-layer adapter
 * keeps routing explicit while delegating packet effects to actor, interface,
 * social, region, camera, audio, and client-state handlers.</p>
 */
final class ClientIncomingPacketHandler implements IncomingPacketHandler {

	/** Client runtime used for logout and unknown-packet recovery. */
	private final Client client;
	/** Applies interface, widget, tab, and input-dialog packets to client UI state. */
	private final InterfacePacketHandler interfacePackets;
	/** Applies chat, social-list, private-message, and account-status packets. */
	private final SocialPacketHandler socialPackets;
	/** Applies region rebuild, zone update, and world-location packets. */
	private final RegionPacketHandler regionPackets;
	/** Applies cinematic camera, camera shake, reset, and world-hint packets. */
	private final CameraPacketHandler cameraPackets;
	/** Applies global sound-effect and music-selection packets. */
	private final AudioPacketHandler audioPackets;
	/** Applies player/NPC synchronization and actor lifecycle packets. */
	private final ActorPacketHandler actorPackets;
	/** Applies varp, skill, run-energy, minimap, weight, and timer state packets. */
	private final ClientStatePacketHandler clientStatePackets;

	/**
	 * Creates the incoming packet application router.
	 *
	 * @param client client runtime receiving packet effects
	 */
	ClientIncomingPacketHandler(Client client) {
		this.client = client;
		this.interfacePackets = new InterfacePacketHandler(client);
		this.socialPackets = new SocialPacketHandler(client);
		this.regionPackets = new RegionPacketHandler(client);
		this.cameraPackets = new CameraPacketHandler(client);
		this.audioPackets = new AudioPacketHandler(client);
		this.actorPackets = new ActorPacketHandler(client);
		this.clientStatePackets = new ClientStatePacketHandler(client);
	}

	@Override
	public boolean handle(int opcode, Buffer buffer, int packetSize) {
		return switch (opcode) {
			case IncomingPacketOpcode.SET_WIDGET_POSITION,
				IncomingPacketOpcode.SET_WIDGET_MODEL_TRANSFORM,
				IncomingPacketOpcode.SET_WIDGET_MODEL,
				IncomingPacketOpcode.SET_WIDGET_NPC_MODEL,
				IncomingPacketOpcode.OPEN_CHATBOX_INTERFACE,
				IncomingPacketOpcode.SET_DIALOGUE_INTERFACE,
				IncomingPacketOpcode.SET_WIDGET_COLOR,
				IncomingPacketOpcode.SET_PLAYER_ACTION,
				IncomingPacketOpcode.OPEN_NAME_INPUT_DIALOG,
				IncomingPacketOpcode.CLOSE_INTERFACES,
				IncomingPacketOpcode.SET_WALKABLE_INTERFACE,
				IncomingPacketOpcode.SET_WIDGET_MOUSEOVER,
				IncomingPacketOpcode.OPEN_MAIN_AND_SIDEBAR_INTERFACES,
				IncomingPacketOpcode.UPDATE_WIDGET_ITEMS_PARTIAL,
				IncomingPacketOpcode.OPEN_AMOUNT_INPUT_DIALOG,
				IncomingPacketOpcode.SET_SELECTED_TAB,
				IncomingPacketOpcode.SET_WIDGET_PLAYER_MODEL,
				IncomingPacketOpcode.OPEN_MAIN_INTERFACE,
				IncomingPacketOpcode.OPEN_SIDEBAR_INTERFACE,
				IncomingPacketOpcode.UPDATE_WIDGET_ITEMS,
				IncomingPacketOpcode.SET_WIDGET_ITEM_MODEL,
				IncomingPacketOpcode.SET_WIDGET_ANIMATION,
				IncomingPacketOpcode.SET_TAB_INTERFACE,
				IncomingPacketOpcode.CLEAR_WIDGET_ITEMS,
				IncomingPacketOpcode.FLASH_TAB,
				IncomingPacketOpcode.OPEN_FULLSCREEN_INTERFACES,
				IncomingPacketOpcode.SET_WIDGET_MODEL_ROTATION_SPEED,
				IncomingPacketOpcode.SET_WIDGET_TEXT,
				IncomingPacketOpcode.SET_WIDGET_SCROLL_POSITION -> interfacePackets.handle(opcode, buffer, packetSize);
			case IncomingPacketOpcode.SET_CHAT_MODES,
				IncomingPacketOpcode.ACCOUNT_INFO,
				IncomingPacketOpcode.SERVER_MESSAGE,
				IncomingPacketOpcode.FRIEND_STATUS,
				IncomingPacketOpcode.PRIVATE_MESSAGE,
				IncomingPacketOpcode.UPDATE_IGNORE_LIST,
				IncomingPacketOpcode.SET_FRIEND_LIST_STATUS -> socialPackets.handle(opcode, buffer, packetSize);
			case IncomingPacketOpcode.SET_MULTI_COMBAT,
				IncomingPacketOpcode.CLEAR_DESTINATION,
				IncomingPacketOpcode.CLEAR_ZONE,
				IncomingPacketOpcode.BATCH_ZONE_UPDATES,
				IncomingPacketOpcode.REBUILD_REGION,
				IncomingPacketOpcode.REBUILD_INSTANCED_REGION,
				IncomingPacketOpcode.PLAY_AREA_SOUND,
				IncomingPacketOpcode.UPDATE_GROUND_ITEM_AMOUNT,
				IncomingPacketOpcode.ATTACH_OBJECT_TO_PLAYER,
				IncomingPacketOpcode.ADD_GROUND_ITEM_FOR_OTHER_PLAYER,
				IncomingPacketOpcode.ADD_GRAPHICS_OBJECT,
				IncomingPacketOpcode.ADD_PROJECTILE,
				IncomingPacketOpcode.REMOVE_GROUND_ITEM,
				IncomingPacketOpcode.ADD_GROUND_ITEM,
				IncomingPacketOpcode.ANIMATE_GAME_OBJECT,
				IncomingPacketOpcode.REMOVE_GAME_OBJECT,
				IncomingPacketOpcode.ADD_GAME_OBJECT,
				IncomingPacketOpcode.SET_ZONE_BASE -> regionPackets.handle(opcode, buffer, packetSize);
			case IncomingPacketOpcode.SET_HINT_ICON,
				IncomingPacketOpcode.SET_CINEMATIC_CAMERA_LOOK_AT,
				IncomingPacketOpcode.CAMERA_SHAKE,
				IncomingPacketOpcode.SET_CINEMATIC_CAMERA_POSITION,
				IncomingPacketOpcode.RESET_CAMERA -> cameraPackets.handle(opcode, buffer, packetSize);
			case IncomingPacketOpcode.PLAY_SOUND_EFFECT,
				IncomingPacketOpcode.PLAY_MUSIC,
				IncomingPacketOpcode.PLAY_TEMPORARY_MUSIC -> audioPackets.handle(opcode, buffer, packetSize);
			case IncomingPacketOpcode.RESET_ENTITY_ANIMATIONS,
				IncomingPacketOpcode.NPC_UPDATE,
				IncomingPacketOpcode.SET_LOCAL_PLAYER_INDEX,
				IncomingPacketOpcode.PLAYER_UPDATE -> actorPackets.handle(opcode, buffer, packetSize);
			case IncomingPacketOpcode.SET_VARP_SMALL,
				IncomingPacketOpcode.SET_MINIMAP_STATE,
				IncomingPacketOpcode.SET_VARP_LARGE,
				IncomingPacketOpcode.UPDATE_WEIGHT,
				IncomingPacketOpcode.UPDATE_SKILL,
				IncomingPacketOpcode.SET_SYSTEM_UPDATE_TIMER,
				IncomingPacketOpcode.UPDATE_RUN_ENERGY,
				IncomingPacketOpcode.SYNCHRONIZE_VARPS -> clientStatePackets.handle(opcode, buffer, packetSize);
			case IncomingPacketOpcode.LOGOUT -> {
				client.logout();
				yield false;
			}
			default -> {
				Signlink.reportError("T1 - " + opcode + "," + packetSize + " - "
						+ client.networkSession.secondLastOpcode + "," + client.networkSession.thirdLastOpcode);
				client.logout();
				yield true;
			}
		};
	}
}
