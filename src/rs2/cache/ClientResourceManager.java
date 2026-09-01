package rs2.cache;

import java.io.RandomAccessFile;
import java.util.function.BooleanSupplier;

import rs2.cache.ondemand.OnDemandFetcher;

/**
 * Owns the bootstrap cache loader and the running revision-377 on-demand
 * resource service for one client instance.
 *
 * <p>
 * Bootstrap archives are validated against the authoritative CRC table before
 * use. Once the version-list archive is available, this owner starts and later
 * stops the asynchronous on-demand fetcher. Application code can therefore
 * depend on one resource lifecycle instead of separately managing the bootstrap
 * loader and update-server worker.
 * </p>
 */
public final class ClientResourceManager {

	/** Owns bootstrap archive CRCs and local cache indices. */
	private final ResourceLoader resourceLoader = new ResourceLoader();

	/** Running asynchronous resource fetcher, or {@code null} before startup. */
	private OnDemandFetcher onDemandFetcher;

	/** Creates an empty resource manager ready for cache bootstrap. */
	public ClientResourceManager() {
	}

	/**
	 * Initializes local cache indices when Signlink opened the packed cache files.
	 *
	 * @param dataFile   packed cache data file, or {@code null} when unavailable
	 * @param indexFiles packed cache index files
	 */
	public void initializeCacheIndices(RandomAccessFile dataFile, RandomAccessFile[] indexFiles) {
		resourceLoader.initializeCacheIndices(dataFile, indexFiles);
	}

	/**
	 * Fetches the authoritative bootstrap archive CRC table.
	 *
	 * @param opener   JAGGRAB stream opener
	 * @param progress loading-screen progress sink
	 */
	public void fetchArchiveCrcs(ResourceLoader.JaggrabOpener opener, ResourceLoader.ProgressListener progress) {
		resourceLoader.fetchArchiveCrcs(opener, progress);
	}

	/**
	 * Returns the authoritative CRC for one bootstrap archive.
	 *
	 * @param index bootstrap archive index
	 * @return expected CRC-32 value
	 */
	public int getArchiveCrc(int index) {
		return resourceLoader.getArchiveCrc(index);
	}

	/**
	 * Loads and validates one bootstrap archive.
	 *
	 * @param archiveName    archive request name
	 * @param loadingPercent loading-screen progress percentage
	 * @param cacheFileId    local cache file id
	 * @param displayName    user-facing archive name
	 * @param opener         JAGGRAB stream opener
	 * @param progress       loading-screen progress sink
	 * @return validated archive
	 */
	public Archive loadArchive(String archiveName, int loadingPercent, int cacheFileId, String displayName,
			ResourceLoader.JaggrabOpener opener, ResourceLoader.ProgressListener progress) {
		return resourceLoader.loadArchive(getArchiveCrc(cacheFileId), archiveName, loadingPercent, cacheFileId,
				displayName, opener, progress);
	}

	/**
	 * Starts the asynchronous version-list/on-demand resource service.
	 *
	 * @param versionListArchive revision-377 version-list archive
	 * @param socketOpener       update-server socket opener
	 * @param loggedInSupplier   current game-session login-state supplier
	 * @param updateServerPort   update-server port
	 * @return started fetcher
	 */
	public OnDemandFetcher startOnDemand(Archive versionListArchive, OnDemandFetcher.SocketOpener socketOpener,
			BooleanSupplier loggedInSupplier, int updateServerPort) {
		if (onDemandFetcher != null) {
			onDemandFetcher.stop();
		}
		OnDemandFetcher fetcher = new OnDemandFetcher();
		fetcher.start(versionListArchive, resourceLoader, socketOpener, loggedInSupplier, updateServerPort);
		onDemandFetcher = fetcher;
		return fetcher;
	}

	/**
	 * Returns the running on-demand fetcher.
	 *
	 * @return fetcher, or {@code null} before on-demand startup/after shutdown
	 */
	public OnDemandFetcher onDemandFetcher() {
		return onDemandFetcher;
	}

	/**
	 * Returns whether local packed-cache indices are available.
	 *
	 * @return {@code true} when the local cache is usable
	 */
	public boolean hasCache() {
		return resourceLoader.hasCache();
	}

	/**
	 * Returns one local cache index.
	 *
	 * @param index zero-based cache-index number
	 * @return cache index, or {@code null} when unavailable
	 */
	public CacheIndex getCacheIndex(int index) {
		return resourceLoader.getCacheIndex(index);
	}

	/**
	 * Stops the asynchronous resource service and releases its update connection.
	 */
	public void stop() {
		if (onDemandFetcher != null) {
			onDemandFetcher.stop();
			onDemandFetcher = null;
		}
	}
}
