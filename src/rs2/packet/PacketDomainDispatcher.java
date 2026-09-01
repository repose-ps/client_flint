package rs2.packet;

import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;
import java.util.function.Supplier;

import rs2.cache.ondemand.OnDemandFetcher;
import rs2.chat.ChatController;
import rs2.chat.SocialManager;
import rs2.game.ActorSynchronizer;
import rs2.game.CameraController;
import rs2.game.MinimapRenderer;
import rs2.game.RegionManager;
import rs2.game.VarpState;
import rs2.game.WorldState;
import rs2.game.ZoneUpdateHandler;
import rs2.game.entity.Player;
import rs2.net.Buffer;
import rs2.net.IncomingPacketOpcode;
import rs2.sound.MusicController;
import rs2.sound.SoundEffectQueue;
import rs2.ui.InterfaceController;
import rs2.ui.WidgetRuntime;

/**
 * Routes recognized revision-377 application packets to package-private domain
 * handlers.
 *
 * <p>
 * The public facade is intentionally the only packet-domain type visible
 * outside this package. The concrete domain handlers remain package-private
 * implementation details.
 * </p>
 */
public final class PacketDomainDispatcher {
	/** Interface packet domain. */
	private final InterfacePacketHandler interfacePackets;
	/** Social packet domain. */
	private final SocialPacketHandler socialPackets;
	/** Region packet domain. */
	private final RegionPacketHandler regionPackets;
	/** Camera packet domain. */
	private final CameraPacketHandler cameraPackets;
	/** Audio packet domain. */
	private final AudioPacketHandler audioPackets;
	/** Actor packet domain. */
	private final ActorPacketHandler actorPackets;
	/** Client-state packet domain. */
	private final ClientStatePacketHandler clientStatePackets;

	/**
	 * Creates the domain dispatcher from narrow per-domain bindings.
	 *
	 * @param interfaces  interface-domain bindings
	 * @param social      social-domain bindings
	 * @param region      region-domain bindings
	 * @param camera      camera-domain bindings
	 * @param audio       audio-domain bindings
	 * @param actor       actor-domain bindings
	 * @param clientState client-state-domain bindings
	 */
	public PacketDomainDispatcher(InterfaceBindings interfaces, SocialBindings social, RegionBindings region,
			CameraBindings camera, AudioBindings audio, ActorBindings actor, ClientStateBindings clientState) {
		interfacePackets = new InterfacePacketHandler(interfaces.interfaces(), interfaces.widgets(), interfaces.chat(),
				interfaces.localPlayer(), interfaces.playerActions(), interfaces.playerActionLowPriority(),
				interfaces.unloadInterface(), interfaces.redraw());
		socialPackets = new SocialPacketHandler(social.social(), social.chat(), social.tutorialIslandFlag(),
				social.currentWorldId(), social.messages(), social.accountInfo(), social.redrawChatModes(),
				social.redrawChatbox(), social.redrawSidebar());
		regionPackets = new RegionPacketHandler(region.regions(), region.resources(), region.actors(), region.world(),
				region.zoneUpdates(), region.camera(), region.state(), region.areaSounds(),
				region.showLoadingMessage());
		cameraPackets = new CameraPacketHandler(camera.camera(), camera.world(), camera.currentPlane(), camera.hints());
		audioPackets = new AudioPacketHandler(audio.sounds(), audio.music(), audio.resources(), audio.lowMemory());
		actorPackets = new ActorPacketHandler(actor.actors(), actor.regions(), actor.gameCycle(), actor.currentPlane(),
				actor.setCurrentPlane(), actor.loginUsername(), actor.chatBuffer(), actor.chatHandler(),
				actor.setAccountMembershipStatus(), actor.setLocalPlayerServerIndex());
		clientStatePackets = new ClientStatePacketHandler(clientState.varps(), clientState.interfaces(),
				clientState.minimap(), clientState.applyVarp(), clientState.redrawSidebar(),
				clientState.redrawChatbox(), clientState.setWeight(), clientState.skillExperiences(),
				clientState.currentSkillLevels(), clientState.baseSkillLevels(), clientState.experienceTable(),
				clientState.setSystemUpdateTimer(), clientState.setRunEnergy());
	}

	/**
	 * Applies a recognized domain packet.
	 *
	 * @param opcode     decoded revision-377 opcode
	 * @param buffer     payload buffer positioned at zero
	 * @param packetSize payload length in bytes
	 * @return {@code true} when the opcode belongs to a packet domain;
	 *         {@code false} otherwise
	 */
	public boolean handle(int opcode, Buffer buffer, int packetSize) {
		return switch (opcode) {
		case IncomingPacketOpcode.SET_WIDGET_POSITION, IncomingPacketOpcode.SET_WIDGET_MODEL_TRANSFORM,
				IncomingPacketOpcode.SET_WIDGET_MODEL, IncomingPacketOpcode.SET_WIDGET_NPC_MODEL,
				IncomingPacketOpcode.OPEN_CHATBOX_INTERFACE, IncomingPacketOpcode.SET_DIALOGUE_INTERFACE,
				IncomingPacketOpcode.SET_WIDGET_COLOR, IncomingPacketOpcode.SET_PLAYER_ACTION,
				IncomingPacketOpcode.OPEN_NAME_INPUT_DIALOG, IncomingPacketOpcode.CLOSE_INTERFACES,
				IncomingPacketOpcode.SET_WALKABLE_INTERFACE, IncomingPacketOpcode.SET_WIDGET_MOUSEOVER,
				IncomingPacketOpcode.OPEN_MAIN_AND_SIDEBAR_INTERFACES, IncomingPacketOpcode.UPDATE_WIDGET_ITEMS_PARTIAL,
				IncomingPacketOpcode.OPEN_AMOUNT_INPUT_DIALOG, IncomingPacketOpcode.SET_SELECTED_TAB,
				IncomingPacketOpcode.SET_WIDGET_PLAYER_MODEL, IncomingPacketOpcode.OPEN_MAIN_INTERFACE,
				IncomingPacketOpcode.OPEN_SIDEBAR_INTERFACE, IncomingPacketOpcode.UPDATE_WIDGET_ITEMS,
				IncomingPacketOpcode.SET_WIDGET_ITEM_MODEL, IncomingPacketOpcode.SET_WIDGET_ANIMATION,
				IncomingPacketOpcode.SET_TAB_INTERFACE, IncomingPacketOpcode.CLEAR_WIDGET_ITEMS,
				IncomingPacketOpcode.FLASH_TAB, IncomingPacketOpcode.OPEN_FULLSCREEN_INTERFACES,
				IncomingPacketOpcode.SET_WIDGET_MODEL_ROTATION_SPEED, IncomingPacketOpcode.SET_WIDGET_TEXT,
				IncomingPacketOpcode.SET_WIDGET_SCROLL_POSITION ->
			interfacePackets.handle(opcode, buffer, packetSize);
		case IncomingPacketOpcode.SET_CHAT_MODES, IncomingPacketOpcode.ACCOUNT_INFO,
				IncomingPacketOpcode.SERVER_MESSAGE, IncomingPacketOpcode.FRIEND_STATUS,
				IncomingPacketOpcode.PRIVATE_MESSAGE, IncomingPacketOpcode.UPDATE_IGNORE_LIST,
				IncomingPacketOpcode.SET_FRIEND_LIST_STATUS ->
			socialPackets.handle(opcode, buffer, packetSize);
		case IncomingPacketOpcode.SET_MULTI_COMBAT, IncomingPacketOpcode.CLEAR_DESTINATION,
				IncomingPacketOpcode.CLEAR_ZONE, IncomingPacketOpcode.BATCH_ZONE_UPDATES,
				IncomingPacketOpcode.REBUILD_REGION, IncomingPacketOpcode.REBUILD_INSTANCED_REGION,
				IncomingPacketOpcode.PLAY_AREA_SOUND, IncomingPacketOpcode.UPDATE_GROUND_ITEM_AMOUNT,
				IncomingPacketOpcode.ATTACH_OBJECT_TO_PLAYER, IncomingPacketOpcode.ADD_GROUND_ITEM_FOR_OTHER_PLAYER,
				IncomingPacketOpcode.ADD_GRAPHICS_OBJECT, IncomingPacketOpcode.ADD_PROJECTILE,
				IncomingPacketOpcode.REMOVE_GROUND_ITEM, IncomingPacketOpcode.ADD_GROUND_ITEM,
				IncomingPacketOpcode.ANIMATE_GAME_OBJECT, IncomingPacketOpcode.REMOVE_GAME_OBJECT,
				IncomingPacketOpcode.ADD_GAME_OBJECT, IncomingPacketOpcode.SET_ZONE_BASE ->
			regionPackets.handle(opcode, buffer, packetSize);
		case IncomingPacketOpcode.SET_HINT_ICON, IncomingPacketOpcode.SET_CINEMATIC_CAMERA_LOOK_AT,
				IncomingPacketOpcode.CAMERA_SHAKE, IncomingPacketOpcode.SET_CINEMATIC_CAMERA_POSITION,
				IncomingPacketOpcode.RESET_CAMERA ->
			cameraPackets.handle(opcode, buffer, packetSize);
		case IncomingPacketOpcode.PLAY_SOUND_EFFECT, IncomingPacketOpcode.PLAY_MUSIC,
				IncomingPacketOpcode.PLAY_TEMPORARY_MUSIC ->
			audioPackets.handle(opcode, buffer, packetSize);
		case IncomingPacketOpcode.RESET_ENTITY_ANIMATIONS, IncomingPacketOpcode.NPC_UPDATE,
				IncomingPacketOpcode.SET_LOCAL_PLAYER_INDEX, IncomingPacketOpcode.PLAYER_UPDATE ->
			actorPackets.handle(opcode, buffer, packetSize);
		case IncomingPacketOpcode.SET_VARP_SMALL, IncomingPacketOpcode.SET_MINIMAP_STATE,
				IncomingPacketOpcode.SET_VARP_LARGE, IncomingPacketOpcode.UPDATE_WEIGHT,
				IncomingPacketOpcode.UPDATE_SKILL, IncomingPacketOpcode.SET_SYSTEM_UPDATE_TIMER,
				IncomingPacketOpcode.UPDATE_RUN_ENERGY, IncomingPacketOpcode.SYNCHRONIZE_VARPS ->
			clientStatePackets.handle(opcode, buffer, packetSize);
		default -> false;
		};
	}

	/** Receives decoded account-status values. */
	@FunctionalInterface
	public interface AccountInfoSink {
		/**
		 * Applies one decoded account-status snapshot.
		 *
		 * @param lastPasswordChangeDate last password-change date
		 * @param accountCurrentDay      current account day
		 * @param unreadMessageCount     unread message count
		 * @param lastLoginDay           last login day
		 * @param membershipDays         remaining membership days
		 * @param lastLoginIp            last login IPv4 address
		 * @param recoveryQuestionsDate  recovery-question date
		 */
		void update(int lastPasswordChangeDate, int accountCurrentDay, int unreadMessageCount, int lastLoginDay,
				int membershipDays, int lastLoginIp, int recoveryQuestionsDate);
	}

	/** Mutable application state used only by region packets. */
	public interface RegionState {
		/**
		 * Returns the current scene plane.
		 * 
		 * @return current scene plane
		 */
		int currentPlane();

		/**
		 * Returns the current game cycle.
		 * 
		 * @return current game cycle
		 */
		int gameCycle();

		/**
		 * Returns the local player server index.
		 * 
		 * @return local player server index
		 */
		int localPlayerServerIndex();

		/**
		 * Returns the local player.
		 * 
		 * @return local player
		 */
		Player localPlayer();

		/**
		 * Returns the destination X coordinate.
		 * 
		 * @return destination X
		 */
		int destinationX();

		/**
		 * Returns the destination Y coordinate.
		 * 
		 * @return destination Y
		 */
		int destinationY();

		/**
		 * Updates the destination marker.
		 * 
		 * @param x destination X
		 * @param y destination Y
		 */
		void setDestination(int x, int y);

		/**
		 * Updates multi-combat state.
		 * 
		 * @param value multi-combat value
		 */
		void setMultiCombatZone(int value);
	}

	/** Receives camera hint-target effects. */
	public interface HintSink {
		/**
		 * Sets the raw hint type.
		 * 
		 * @param type hint type
		 */
		void setType(int type);

		/**
		 * Sets the hinted NPC.
		 * 
		 * @param index NPC index
		 */
		void setNpcIndex(int index);

		/**
		 * Sets a world-tile hint.
		 * 
		 * @param tileX   tile X
		 * @param tileY   tile Y
		 * @param height  hint height
		 * @param offsetX fine X offset
		 * @param offsetY fine Y offset
		 */
		void setTileHint(int tileX, int tileY, int height, int offsetX, int offsetY);

		/**
		 * Sets the hinted player.
		 * 
		 * @param index player index
		 */
		void setPlayerIndex(int index);
	}

	/**
	 * Interface-packet dependencies.
	 *
	 * @param interfaces              interfaces binding
	 * @param widgets                 widgets binding
	 * @param chat                    chat binding
	 * @param localPlayer             localPlayer binding
	 * @param playerActions           playerActions binding
	 * @param playerActionLowPriority playerActionLowPriority binding
	 * @param unloadInterface         unloadInterface binding
	 * @param redraw                  redraw binding
	 */
	public record InterfaceBindings(InterfaceController interfaces, WidgetRuntime widgets, ChatController chat,
			Supplier<Player> localPlayer, String[] playerActions, boolean[] playerActionLowPriority,
			IntConsumer unloadInterface, InterfaceController.RedrawSink redraw) {
	}

	/**
	 * Social-packet dependencies.
	 *
	 * @param social             social binding
	 * @param chat               chat binding
	 * @param tutorialIslandFlag tutorialIslandFlag binding
	 * @param currentWorldId     currentWorldId binding
	 * @param messages           messages binding
	 * @param accountInfo        accountInfo binding
	 * @param redrawChatModes    redrawChatModes binding
	 * @param redrawChatbox      redrawChatbox binding
	 * @param redrawSidebar      redrawSidebar binding
	 */
	public record SocialBindings(SocialManager social, ChatController chat, IntSupplier tutorialIslandFlag,
			IntSupplier currentWorldId, SocialManager.MessageSink messages, AccountInfoSink accountInfo,
			Runnable redrawChatModes, Runnable redrawChatbox, Runnable redrawSidebar) {
	}

	/**
	 * Region-packet dependencies.
	 *
	 * @param regions            regions binding
	 * @param resources          resources binding
	 * @param actors             actors binding
	 * @param world              world binding
	 * @param zoneUpdates        zoneUpdates binding
	 * @param camera             camera binding
	 * @param state              state binding
	 * @param areaSounds         areaSounds binding
	 * @param showLoadingMessage showLoadingMessage binding
	 */
	public record RegionBindings(RegionManager regions, Supplier<OnDemandFetcher> resources, ActorSynchronizer actors,
			Supplier<WorldState> world, Supplier<ZoneUpdateHandler> zoneUpdates, CameraController camera,
			RegionState state, ZoneUpdateHandler.AreaSoundHandler areaSounds, Runnable showLoadingMessage) {
	}

	/**
	 * Camera-packet dependencies.
	 *
	 * @param camera       camera binding
	 * @param world        world binding
	 * @param currentPlane currentPlane binding
	 * @param hints        hints binding
	 */
	public record CameraBindings(CameraController camera, Supplier<WorldState> world, IntSupplier currentPlane,
			HintSink hints) {
	}

	/**
	 * Audio-packet dependencies.
	 *
	 * @param sounds    sounds binding
	 * @param music     music binding
	 * @param resources resources binding
	 * @param lowMemory lowMemory binding
	 */
	public record AudioBindings(SoundEffectQueue sounds, MusicController music, Supplier<OnDemandFetcher> resources,
			BooleanSupplier lowMemory) {
	}

	/**
	 * Actor-packet dependencies.
	 *
	 * @param actors                     actors binding
	 * @param regions                    regions binding
	 * @param gameCycle                  gameCycle binding
	 * @param currentPlane               currentPlane binding
	 * @param setCurrentPlane            setCurrentPlane binding
	 * @param loginUsername              loginUsername binding
	 * @param chatBuffer                 chatBuffer binding
	 * @param chatHandler                chatHandler binding
	 * @param setAccountMembershipStatus setAccountMembershipStatus binding
	 * @param setLocalPlayerServerIndex  setLocalPlayerServerIndex binding
	 */
	public record ActorBindings(ActorSynchronizer actors, RegionManager regions, IntSupplier gameCycle,
			IntSupplier currentPlane, IntConsumer setCurrentPlane, Supplier<String> loginUsername, Buffer chatBuffer,
			ActorSynchronizer.ChatHandler chatHandler, IntConsumer setAccountMembershipStatus,
			IntConsumer setLocalPlayerServerIndex) {
	}

	/**
	 * Client-state packet dependencies.
	 *
	 * @param varps                varps binding
	 * @param interfaces           interfaces binding
	 * @param minimap              minimap binding
	 * @param applyVarp            applyVarp binding
	 * @param redrawSidebar        redrawSidebar binding
	 * @param redrawChatbox        redrawChatbox binding
	 * @param setWeight            setWeight binding
	 * @param skillExperiences     skillExperiences binding
	 * @param currentSkillLevels   currentSkillLevels binding
	 * @param baseSkillLevels      baseSkillLevels binding
	 * @param experienceTable      experienceTable binding
	 * @param setSystemUpdateTimer setSystemUpdateTimer binding
	 * @param setRunEnergy         setRunEnergy binding
	 */
	public record ClientStateBindings(VarpState varps, InterfaceController interfaces, MinimapRenderer minimap,
			IntConsumer applyVarp, Runnable redrawSidebar, Runnable redrawChatbox, IntConsumer setWeight,
			int[] skillExperiences, int[] currentSkillLevels, int[] baseSkillLevels, int[] experienceTable,
			IntConsumer setSystemUpdateTimer, IntConsumer setRunEnergy) {
	}
}
