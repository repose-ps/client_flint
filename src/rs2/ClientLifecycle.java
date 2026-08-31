package rs2;

import rs2.cache.Archive;
import rs2.cache.ClientResourceManager;
import rs2.cache.CacheIndex;
import rs2.cache.def.AnimationSequence;
import rs2.cache.def.FloorDefinition;
import rs2.cache.def.GameObjectDefinition;
import rs2.cache.def.IdentityKit;
import rs2.cache.def.ItemDefinition;
import rs2.cache.def.NpcDefinition;
import rs2.cache.def.SpotAnimation;
import rs2.cache.cfg.Varbit;
import rs2.cache.cfg.Varp;
import rs2.cache.ondemand.OnDemandFetcher;
import rs2.chat.Censor;
import rs2.game.entity.Player;
import rs2.media.AnimationFrame;
import rs2.media.Rasterizer3D;
import rs2.media.TypeFace;
import rs2.media.model.Model;
import rs2.media.sprite.ImageRGB;
import rs2.media.sprite.ItemSpriteFactory;
import rs2.media.sprite.IndexedImage;
import rs2.net.Buffer;
import rs2.scene.Scene;
import rs2.sign.Signlink;
import rs2.sound.SoundTrack;
import rs2.ui.Widget;

/**
 * Owns the desktop client's startup and final-shutdown lifecycle.
 *
 * <p>
 * The lifecycle owns bootstrap cache state and the asynchronous on-demand
 * service. {@link Client} remains the application coordinator during normal
 * gameplay, while this class contains the one-time ordering required to load
 * archives, install resources, start background services, and release static
 * client/cache state at process shutdown.
 * </p>
 */
public final class ClientLifecycle {

	/** Prevents more than one client startup in the same JVM, matching legacy behavior. */
	private static boolean startupStarted;

	/** Client whose lifecycle is coordinated. */
	private final Client client;

	/** Bootstrap cache and asynchronous resource owner. */
	private final ClientResourceManager resources = new ClientResourceManager();

	/** Startup archive/on-demand preload coordinator. */
	private final ClientBootstrap bootstrap = new ClientBootstrap(resources);

	/**
	 * Creates lifecycle ownership for one client instance.
	 *
	 * @param client application client whose one-time lifecycle is coordinated
	 */
	public ClientLifecycle(Client client) {
		this.client = client;
	}

	/**
	 * Returns one authoritative bootstrap archive CRC for login framing.
	 *
	 * @param index bootstrap archive index
	 * @return CRC-32 value
	 */
	public int getArchiveCrc(int index) {
		return resources.getArchiveCrc(index);
	}

	/**
	 * Returns the active asynchronous resource fetcher.
	 *
	 * @return fetcher, or {@code null} before startup/after shutdown
	 */
	public OnDemandFetcher onDemandFetcher() {
		return resources.onDemandFetcher();
	}

	/**
	 * Returns one local cache index for diagnostics.
	 *
	 * @param index zero-based index number
	 * @return cache index, or {@code null}
	 */
	public CacheIndex getCacheIndex(int index) {
		return resources.getCacheIndex(index);
	}

	/**
	 * Performs the complete one-time client bootstrap sequence.
	 */
	public void startUp() {
		client.drawLoadingText(20, "Starting up");
		if (startupStarted) {
			client.duplicateClientError = true;
			return;
		}
		startupStarted = true;
		if (Signlink.cacheData != null) {
			resources.initializeCacheIndices(Signlink.cacheData, Signlink.cacheIndexes);
		}
		try {
			ClientBootstrap.Archives archives = bootstrap.load(Client.lowMemory, Client.membersWorld,
					43594 + Client.portOffset, client::openJaggrabStream, client::drawLoadingText, client::openSocket,
					() -> client.loggedIn, this::initializeTitleArchive, client::initializeWorldForStartup,
					this::initializeOnDemandConsumers, client::processOnDemandRequests, client::haltOnLoadError);
			if (archives == null) {
				return;
			}
			unpackMedia(archives.media());
			unpackTextures(archives.textures());
			unpackConfig(archives.config());
			unpackSounds(archives.sounds());
			unpackInterfaces(archives.interfaces(), archives.media());
			prepareGameEngine(archives.wordEncoding());
		} catch (Exception exception) {
			Signlink.reportError("loaderror " + client.loadingMessage + " " + client.loadingPercent);
			client.loadingError = true;
		}
	}

	/**
	 * Loads title fonts/backgrounds as soon as the title archive becomes available.
	 *
	 * @param titleArchive loaded title archive
	 */
	private void initializeTitleArchive(Archive titleArchive) {
		client.titleArchive = titleArchive;
		client.smallFont = new TypeFace(false, titleArchive, "p11_full");
		client.plainFont = new TypeFace(false, titleArchive, "p12_full");
		client.boldFont = new TypeFace(false, titleArchive, "b12_full");
		client.fancyFont = new TypeFace(true, titleArchive, "q8_full");
		client.drawTitleBackground();
		client.initializeTitleScreen();
	}

	/**
	 * Initializes model/animation consumers and queues the startup MIDI request.
	 *
	 * @param fetcher started on-demand resource service
	 */
	private void initializeOnDemandConsumers(OnDemandFetcher fetcher) {
		AnimationFrame.initialize(fetcher.getAnimationCount());
		Model.initializeModelHeaders(fetcher.getFileCount(OnDemandFetcher.MODEL), fetcher);
		if (!Client.lowMemory) {
			client.lifecycleMusicController().requestStartupTrack(fetcher::request, Client.lowMemory);
		}
	}

	/**
	 * Decodes classic frame/UI/media sprites from the media archive.
	 *
	 * @param mediaArchive loaded 2D media archive
	 */
	private void unpackMedia(Archive mediaArchive) {
		client.drawLoadingText(80, "Unpacking media");
		client.sidebarBackground = new IndexedImage(mediaArchive, "invback", 0);
		client.chatboxBackground = new IndexedImage(mediaArchive, "chatback", 0);
		client.minimapBackground = new IndexedImage(mediaArchive, "mapback", 0);
		client.chatModesBackground = new IndexedImage(mediaArchive, "backbase1", 0);
		client.bottomTabBackground = new IndexedImage(mediaArchive, "backbase2", 0);
		client.topTabBackground = new IndexedImage(mediaArchive, "backhmid1", 0);
		for (int index = 0; index < 13; index++) {
			client.sidebarIcons[index] = new IndexedImage(mediaArchive, "sideicons", index);
		}

		client.compassSprite = new ImageRGB(mediaArchive, "compass", 0);
		client.minimapEdgeArrow = new ImageRGB(mediaArchive, "mapedge", 0);
		client.minimapEdgeArrow.trim();
		for (int index = 0; index < 72; index++) {
			client.mapSceneSprites[index] = new IndexedImage(mediaArchive, "mapscene", index);
		}
		for (int index = 0; index < 70; index++) {
			client.mapFunctionSprites[index] = new ImageRGB(mediaArchive, "mapfunction", index);
		}
		for (int index = 0; index < 5; index++) {
			client.hitmarkSprites[index] = new ImageRGB(mediaArchive, "hitmarks", index);
		}
		for (int index = 0; index < 6; index++) {
			client.skullIconSprites[index] = new ImageRGB(mediaArchive, "headicons_pk", index);
		}
		for (int index = 0; index < 9; index++) {
			client.prayerIconSprites[index] = new ImageRGB(mediaArchive, "headicons_prayer", index);
		}
		for (int index = 0; index < 6; index++) {
			client.hintIconSprites[index] = new ImageRGB(mediaArchive, "headicons_hint", index);
		}
		client.multiCombatOverlay = new ImageRGB(mediaArchive, "overlay_multiway", 0);
		client.destinationMapMarker = new ImageRGB(mediaArchive, "mapmarker", 0);
		client.hintMapMarker = new ImageRGB(mediaArchive, "mapmarker", 1);
		for (int index = 0; index < 8; index++) {
			client.crossSprites[index] = new ImageRGB(mediaArchive, "cross", index);
		}
		client.groundItemMapDot = new ImageRGB(mediaArchive, "mapdots", 0);
		client.npcMapDot = new ImageRGB(mediaArchive, "mapdots", 1);
		client.playerMapDot = new ImageRGB(mediaArchive, "mapdots", 2);
		client.friendMapDot = new ImageRGB(mediaArchive, "mapdots", 3);
		client.teamMapDot = new ImageRGB(mediaArchive, "mapdots", 4);
		client.scrollbarTop = new IndexedImage(mediaArchive, "scrollbar", 0);
		client.scrollbarBottom = new IndexedImage(mediaArchive, "scrollbar", 1);
		client.redstone1 = new IndexedImage(mediaArchive, "redstone1", 0);
		client.redstone2 = new IndexedImage(mediaArchive, "redstone2", 0);
		client.redstone3 = new IndexedImage(mediaArchive, "redstone3", 0);
		client.redstone1Horizontal = flippedHorizontal(mediaArchive, "redstone1");
		client.redstone2Horizontal = flippedHorizontal(mediaArchive, "redstone2");
		client.redstone1Vertical = flippedVertical(mediaArchive, "redstone1");
		client.redstone2Vertical = flippedVertical(mediaArchive, "redstone2");
		client.redstone3Vertical = flippedVertical(mediaArchive, "redstone3");
		client.redstone1Both = flippedBoth(mediaArchive, "redstone1");
		client.redstone2Both = flippedBoth(mediaArchive, "redstone2");
		for (int index = 0; index < 2; index++) {
			client.moderatorIcons[index] = new IndexedImage(mediaArchive, "mod_icons", index);
		}

		client.lifecycleGameRenderer().initializeFrameDecorations(client.getGameComponent(), mediaArchive);
		randomizeMapSprites();
	}

	/**
	 * Loads software textures and establishes the classic texture pool.
	 *
	 * @param textureArchive loaded software-texture archive
	 */
	private void unpackTextures(Archive textureArchive) {
		client.drawLoadingText(83, "Unpacking textures");
		Rasterizer3D.loadTextures(textureArchive);
		Rasterizer3D.setBrightness(0.80000000000000004D);
		Rasterizer3D.initializeTexturePool(20);
	}

	/**
	 * Decodes cache configuration definitions.
	 *
	 * @param configArchive loaded configuration archive
	 */
	private void unpackConfig(Archive configArchive) {
		client.drawLoadingText(86, "Unpacking config");
		AnimationSequence.load(configArchive);
		GameObjectDefinition.load(configArchive, client.lifecycleVarpState()::get);
		FloorDefinition.load(configArchive);
		ItemDefinition.load(configArchive);
		NpcDefinition.load(configArchive, client.lifecycleVarpState()::get);
		IdentityKit.load(configArchive);
		SpotAnimation.load(configArchive);
		Varp.load(configArchive);
		Varbit.load(configArchive);
		ItemDefinition.membersWorld = Client.membersWorld;
	}

	/**
	 * Decodes startup sound effects when high-memory audio is enabled.
	 *
	 * @param soundArchive loaded sound-effect archive
	 */
	private void unpackSounds(Archive soundArchive) {
		if (Client.lowMemory) {
			return;
		}
		client.drawLoadingText(90, "Unpacking sounds");
		SoundTrack.load(new Buffer(soundArchive.read("sounds.dat")));
	}

	/**
	 * Loads interface definitions using the four title fonts.
	 *
	 * @param interfaceArchive loaded interface archive
	 * @param mediaArchive loaded 2D media archive used by widget sprites
	 */
	private void unpackInterfaces(Archive interfaceArchive, Archive mediaArchive) {
		client.drawLoadingText(95, "Unpacking interfaces");
		Widget.load(interfaceArchive, mediaArchive,
				new TypeFace[] { client.smallFont, client.plainFont, client.boldFont, client.fancyFont },
				() -> client.localPlayer.getHeadModel());
	}

	/**
	 * Prepares minimap masks, projection state, chat filtering, and runtime workers.
	 *
	 * @param wordEncodingArchive loaded chat word-encoding archive
	 */
	private void prepareGameEngine(Archive wordEncodingArchive) {
		client.drawLoadingText(100, "Preparing game engine");
		buildMinimapMasks();
		client.lifecycleGameRenderer().initializeProjectionTables(client.clientLayout());
		Censor.load(wordEncodingArchive);
		client.mouseRecorder = new rs2.input.MouseRecorder(client);
		client.mouseRecorder.start(10);
	}

	/** Applies the original small random palette shift to map-function/scene sprites. */
	private void randomizeMapSprites() {
		int redOffset = (int) (Math.random() * 21D) - 10;
		int greenOffset = (int) (Math.random() * 21D) - 10;
		int blueOffset = (int) (Math.random() * 21D) - 10;
		int brightnessOffset = (int) (Math.random() * 41D) - 20;
		for (int index = 0; index < 100; index++) {
			if (client.mapFunctionSprites[index] != null) {
				client.mapFunctionSprites[index].adjustRgb(redOffset + brightnessOffset, greenOffset + brightnessOffset,
						blueOffset + brightnessOffset);
			}
			if (client.mapSceneSprites[index] != null) {
				client.mapSceneSprites[index].adjustPalette(redOffset + brightnessOffset, greenOffset + brightnessOffset,
						blueOffset + brightnessOffset);
			}
		}
	}

	/** Builds the compass and circular minimap clipping masks from the map background. */
	private void buildMinimapMasks() {
		for (int row = 0; row < 33; row++) {
			int startX = 999;
			int endX = 0;
			for (int x = 0; x < 34; x++) {
				if (client.minimapBackground.pixels[x + row * client.minimapBackground.width] == 0) {
					if (startX == 999) {
						startX = x;
					}
					continue;
				}
				if (startX == 999) {
					continue;
				}
				endX = x;
				break;
			}
			client.compassMaskOffsets[row] = startX;
			client.compassMaskWidths[row] = endX - startX;
		}

		for (int row = 5; row < ClientLayout.MINIMAP_HEIGHT; row++) {
			int startX = 999;
			int endX = 0;
			for (int x = 25; x < ClientLayout.MINIMAP_WIDTH; x++) {
				if (client.minimapBackground.pixels[x + row * client.minimapBackground.width] == 0
						&& (x > 34 || row > 34)) {
					if (startX == 999) {
						startX = x;
					}
					continue;
				}
				if (startX == 999) {
					continue;
				}
				endX = x;
				break;
			}
			client.minimapMaskOffsets[row - 5] = startX - 25;
			client.minimapMaskWidths[row - 5] = endX - startX;
		}
	}

	/**
	 * Returns a horizontally flipped redstone sprite.
	 *
	 * @param mediaArchive loaded media archive
	 * @param name sprite group name
	 * @return flipped sprite
	 */
	private static IndexedImage flippedHorizontal(Archive mediaArchive, String name) {
		IndexedImage image = new IndexedImage(mediaArchive, name, 0);
		image.flipHorizontal();
		return image;
	}

	/**
	 * Returns a vertically flipped redstone sprite.
	 *
	 * @param mediaArchive loaded media archive
	 * @param name sprite group name
	 * @return flipped sprite
	 */
	private static IndexedImage flippedVertical(Archive mediaArchive, String name) {
		IndexedImage image = new IndexedImage(mediaArchive, name, 0);
		image.flipVertical();
		return image;
	}

	/**
	 * Returns a horizontally and vertically flipped redstone sprite.
	 *
	 * @param mediaArchive loaded media archive
	 * @param name sprite group name
	 * @return flipped sprite
	 */
	private static IndexedImage flippedBoth(Archive mediaArchive, String name) {
		IndexedImage image = new IndexedImage(mediaArchive, name, 0);
		image.flipHorizontal();
		image.flipVertical();
		return image;
	}

	/** Clears model and item-sprite caches when leaving a game session. */
	public void clearRuntimeCaches() {
		GameObjectDefinition.clearModelCaches();
		NpcDefinition.modelCache.clear();
		ItemDefinition.modelCache.clear();
		ItemSpriteFactory.clearCache();
		Player.modelCache.clear();
		SpotAnimation.modelCache.clear();
	}

	/**
	 * Stops asynchronous services and releases final client/static resources.
	 */
	public void shutdown() {
		if (client.mouseRecorder != null) {
			client.mouseRecorder.stop();
			client.mouseRecorder = null;
		}
		resources.stop();
		client.disposeTitleScreen();
		client.networkSession.closeConnection();

		client.redstone1 = null;
		client.redstone2 = null;
		client.redstone3 = null;
		client.redstone1Horizontal = null;
		client.redstone2Horizontal = null;
		client.redstone1Vertical = null;
		client.redstone2Vertical = null;
		client.redstone3Vertical = null;
		client.redstone1Both = null;
		client.redstone2Both = null;
		client.titleLeftBottomBuffer = null;
		client.titleRightBottomBuffer = null;
		client.titleLeftCenterBuffer = null;
		client.titleRightCenterBuffer = null;
		client.groundItemMapDot = null;
		client.npcMapDot = null;
		client.playerMapDot = null;
		client.friendMapDot = null;
		client.teamMapDot = null;
		client.chatModesBackground = null;
		client.bottomTabBackground = null;
		client.topTabBackground = null;
		client.titleLeftFlameBuffer = null;
		client.titleRightFlameBuffer = null;
		client.titleTopBuffer = null;
		client.titleBottomBuffer = null;
		client.loginBoxBuffer = null;
		client.compassSprite = null;
		client.hitmarkSprites = null;
		client.skullIconSprites = null;
		client.prayerIconSprites = null;
		client.hintIconSprites = null;
		client.crossSprites = null;
		client.sidebarBackground = null;
		client.minimapBackground = null;
		client.chatboxBackground = null;
		client.chatBuffer = null;
		client.mapSceneSprites = null;
		client.mapFunctionSprites = null;
		client.sidebarIcons = null;
		client.multiCombatOverlay = null;

		client.lifecycleSocialManager().clearFriendReferencesForQuit();
		client.lifecycleGameRenderer().clearGameScreenBuffers();
		client.lifecycleGameRenderer().clearFrameDecorations();
		client.lifecycleRegionManager().clear();
		client.lifecycleMinimapRenderer().clear();
		client.lifecycleMusicController().stop();
		client.lifecycleMenuController().state().clearReferencesForQuit();
		client.clearRuntimeWorldForShutdown();

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
		client.gameBuffer = null;
		Player.modelCache = null;
		Rasterizer3D.clear();
		Scene.clearStatic();
		Model.clearModelLoader();
		AnimationFrame.clear();
		Signlink.shutdown();
		System.gc();
	}
}
