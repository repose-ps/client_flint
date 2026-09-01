package rs2;

import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

import rs2.cache.Archive;
import rs2.cache.ClientResourceManager;
import rs2.cache.ResourceLoader;
import rs2.cache.ondemand.OnDemandFetcher;

/**
 * Coordinates revision-377 startup archive loading and on-demand preloading.
 *
 * <p>
 * This application-layer bootstrapper owns the ordering of authoritative CRC
 * retrieval, bootstrap archive validation, update-service startup, and the
 * original animation/model/map preload phases. Decoding those archives into UI,
 * rendering, and definition state remains with the subsystem-specific startup
 * code that consumes the returned {@link Archives}.
 * </p>
 */
public final class ClientBootstrap {

	/**
	 * Bootstrap archives required after the on-demand preload phases finish.
	 *
	 * @param config       configuration-definition archive
	 * @param interfaces   interface/widget archive
	 * @param media        2D media archive
	 * @param textures     software-texture archive
	 * @param wordEncoding chat-censor/word-encoding archive
	 * @param sounds       sound-effect archive
	 */
	public record Archives(Archive config, Archive interfaces, Archive media, Archive textures, Archive wordEncoding,
			Archive sounds) {
	}

	/** Receives fatal bootstrap failures that require the legacy loading halt. */
	@FunctionalInterface
	public interface FatalLoadHandler {
		/**
		 * Handles a fatal startup resource failure.
		 *
		 * @param reason short legacy loading-error reason
		 */
		void halt(String reason);
	}

	/** Owns bootstrap cache and on-demand resource lifecycle. */
	private final ClientResourceManager resources;

	/**
	 * Creates a bootstrap coordinator using the supplied resource owner.
	 *
	 * @param resources bootstrap/on-demand resource owner
	 */
	public ClientBootstrap(ClientResourceManager resources) {
		this.resources = resources;
	}

	/**
	 * Loads bootstrap archives, starts the on-demand service, and performs the
	 * original high-priority startup preload phases.
	 *
	 * @param lowMemory                 whether low-memory mode is active
	 * @param membersWorld              whether members map preloading is active
	 * @param updateServerPort          update-server port
	 * @param jaggrabOpener             JAGGRAB bootstrap stream opener
	 * @param progress                  loading progress sink
	 * @param socketOpener              update-server socket opener
	 * @param loggedInSupplier          current login-state supplier
	 * @param titleInitializer          receives the title archive immediately after
	 *                                  it loads
	 * @param worldInitializer          initializes world state before on-demand
	 *                                  startup
	 * @param onDemandInitializer       initializes model/animation consumers and
	 *                                  startup music
	 * @param completedRequestProcessor installs completed on-demand requests during
	 *                                  preload waits
	 * @param fatalLoadHandler          handles repeated on-demand failures
	 * @return archives needed for media/config/interface preparation, or
	 *         {@code null} when a fatal handler returned instead of halting
	 */
	public Archives load(boolean lowMemory, boolean membersWorld, int updateServerPort,
			ResourceLoader.JaggrabOpener jaggrabOpener, ResourceLoader.ProgressListener progress,
			OnDemandFetcher.SocketOpener socketOpener, BooleanSupplier loggedInSupplier,
			Consumer<Archive> titleInitializer, Runnable worldInitializer,
			Consumer<OnDemandFetcher> onDemandInitializer, Runnable completedRequestProcessor,
			FatalLoadHandler fatalLoadHandler) {
		/*
		 * The game server is authoritative for the packed cache. Obtain its bootstrap
		 * CRC table before accepting local bootstrap archives.
		 */
		resources.fetchArchiveCrcs(jaggrabOpener, progress);

		Archive titleArchive = resources.loadArchive("title", 25, 1, "title screen", jaggrabOpener, progress);
		titleInitializer.accept(titleArchive);
		Archive configArchive = resources.loadArchive("config", 30, 2, "config", jaggrabOpener, progress);
		Archive interfaceArchive = resources.loadArchive("interface", 35, 3, "interface", jaggrabOpener, progress);
		Archive mediaArchive = resources.loadArchive("media", 40, 4, "2d graphics", jaggrabOpener, progress);
		Archive textureArchive = resources.loadArchive("textures", 45, 6, "textures", jaggrabOpener, progress);
		Archive wordEncodingArchive = resources.loadArchive("wordenc", 50, 7, "chat system", jaggrabOpener, progress);
		Archive soundArchive = resources.loadArchive("sounds", 55, 8, "sound effects", jaggrabOpener, progress);

		worldInitializer.run();

		Archive versionListArchive = resources.loadArchive("versionlist", 60, 5, "update list", jaggrabOpener,
				progress);
		progress.update(60, "Initializing on-demand cache");
		OnDemandFetcher fetcher = resources.startOnDemand(versionListArchive, socketOpener, loggedInSupplier,
				updateServerPort);
		onDemandInitializer.accept(fetcher);

		if (!lowMemory && !waitForOutstanding(fetcher, completedRequestProcessor, fatalLoadHandler)) {
			return null;
		}

		progress.update(65, "Requesting animations");
		int requestCount = fetcher.getFileCount(OnDemandFetcher.ANIMATION);
		for (int animationId = 0; animationId < requestCount; animationId++) {
			fetcher.request(OnDemandFetcher.ANIMATION, animationId);
		}
		if (!waitForOutstanding(fetcher, completedRequestProcessor, fatalLoadHandler, 65, "Loading animations",
				requestCount, progress, true)) {
			return null;
		}

		progress.update(70, "Requesting models");
		requestCount = fetcher.getFileCount(OnDemandFetcher.MODEL);
		for (int modelId = 0; modelId < requestCount; modelId++) {
			if ((fetcher.getModelIndex(modelId) & 1) != 0) {
				fetcher.request(OnDemandFetcher.MODEL, modelId);
			}
		}
		requestCount = fetcher.getOutstandingRequestCount();
		if (!waitForOutstanding(fetcher, completedRequestProcessor, fatalLoadHandler, 70, "Loading models",
				requestCount, progress, false)) {
			return null;
		}

		if (resources.hasCache()) {
			progress.update(75, "Requesting maps");
			requestStartupMaps(fetcher);
			requestCount = fetcher.getOutstandingRequestCount();
			if (!waitForOutstanding(fetcher, completedRequestProcessor, fatalLoadHandler, 75, "Loading maps",
					requestCount, progress, false)) {
				return null;
			}
		}

		configureExtraPriorities(fetcher, lowMemory, membersWorld);
		return new Archives(configArchive, interfaceArchive, mediaArchive, textureArchive, wordEncodingArchive,
				soundArchive);
	}

	/**
	 * Waits for the startup-track request phase, which reports no percentage.
	 *
	 * @param fetcher      active on-demand fetcher
	 * @param processor    completed-request installer
	 * @param fatalHandler repeated-failure handler
	 * @return {@code true} after all requests finish; {@code false} if the fatal
	 *         handler returned
	 */
	private static boolean waitForOutstanding(OnDemandFetcher fetcher, Runnable processor,
			FatalLoadHandler fatalHandler) {
		while (fetcher.getOutstandingRequestCount() > 0) {
			processor.run();
			sleepForOnDemand();
			if (fetcher.requestFailures > 3) {
				fatalHandler.halt("ondemand");
				return false;
			}
		}
		return true;
	}

	/**
	 * Waits for a progress-reporting animation/model/map preload phase.
	 *
	 * @param fetcher       active on-demand fetcher
	 * @param processor     completed-request installer
	 * @param fatalHandler  repeated-failure handler
	 * @param percent       loading-screen phase percentage
	 * @param label         loading-screen phase label
	 * @param requestCount  number of requests in the phase
	 * @param progress      loading-screen progress sink
	 * @param checkFailures whether to apply the legacy request-failure halt check
	 * @return {@code true} after all requests finish; {@code false} if the fatal
	 *         handler returned
	 */
	private static boolean waitForOutstanding(OnDemandFetcher fetcher, Runnable processor,
			FatalLoadHandler fatalHandler, int percent, String label, int requestCount,
			ResourceLoader.ProgressListener progress, boolean checkFailures) {
		while (fetcher.getOutstandingRequestCount() > 0) {
			int loadedCount = requestCount - fetcher.getOutstandingRequestCount();
			if (loadedCount > 0 && requestCount > 0) {
				progress.update(percent, label + " - " + (loadedCount * 100) / requestCount + "%");
			}
			processor.run();
			sleepForOnDemand();
			if (checkFailures && fetcher.requestFailures > 3) {
				fatalHandler.halt("ondemand");
				return false;
			}
		}
		return true;
	}

	/** Sleeps between legacy startup on-demand polling iterations. */
	private static void sleepForOnDemand() {
		try {
			Thread.sleep(100L);
		} catch (InterruptedException ignored) {
			/* Preserve the original startup polling behavior. */
		}
	}

	/**
	 * Queues the fixed classic startup-map preload set.
	 *
	 * @param fetcher active on-demand fetcher
	 */
	private static void requestStartupMaps(OnDemandFetcher fetcher) {
		int[][] regions = { { 47, 48 }, { 48, 48 }, { 49, 48 }, { 47, 47 }, { 48, 47 }, { 48, 148 } };
		for (int[] region : regions) {
			fetcher.request(OnDemandFetcher.MAP,
					fetcher.getMapFileId(region[0], region[1], OnDemandFetcher.MAP_FILE_TERRAIN));
			fetcher.request(OnDemandFetcher.MAP,
					fetcher.getMapFileId(region[0], region[1], OnDemandFetcher.MAP_FILE_LANDSCAPE));
		}
	}

	/**
	 * Applies the original low-priority model/map/MIDI background preload rules.
	 *
	 * @param fetcher      active on-demand fetcher
	 * @param lowMemory    whether low-memory mode is active
	 * @param membersWorld whether members map preloading is active
	 */
	private static void configureExtraPriorities(OnDemandFetcher fetcher, boolean lowMemory, boolean membersWorld) {
		int modelCount = fetcher.getFileCount(OnDemandFetcher.MODEL);
		for (int modelId = 0; modelId < modelCount; modelId++) {
			int modelFlags = fetcher.getModelIndex(modelId);
			byte extraPriority = 0;
			if ((modelFlags & 8) != 0) {
				extraPriority = 10;
			} else if ((modelFlags & 0x20) != 0) {
				extraPriority = 9;
			} else if ((modelFlags & 0x10) != 0) {
				extraPriority = 8;
			} else if ((modelFlags & 0x40) != 0) {
				extraPriority = 7;
			} else if ((modelFlags & 0x80) != 0) {
				extraPriority = 6;
			} else if ((modelFlags & 2) != 0) {
				extraPriority = 5;
			} else if ((modelFlags & 4) != 0) {
				extraPriority = 4;
			}
			if ((modelFlags & 1) != 0) {
				extraPriority = 3;
			}
			if (extraPriority != 0) {
				fetcher.setExtraPriority(OnDemandFetcher.MODEL, modelId, extraPriority);
			}
		}

		fetcher.preloadMaps(membersWorld);
		if (!lowMemory) {
			int midiCount = fetcher.getFileCount(OnDemandFetcher.MIDI);
			for (int midiId = 1; midiId < midiCount; midiId++) {
				if (fetcher.isMidiPreload(midiId)) {
					fetcher.setExtraPriority(OnDemandFetcher.MIDI, midiId, (byte) 1);
				}
			}
		}

		for (int modelId = 0; modelId < modelCount; modelId++) {
			if (fetcher.getModelIndex(modelId) == 0 && fetcher.totalFiles < 200) {
				fetcher.setExtraPriority(OnDemandFetcher.MODEL, modelId, (byte) 1);
			}
		}
	}
}
