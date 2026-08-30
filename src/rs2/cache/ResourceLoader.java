package rs2.cache;

import java.io.DataInputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.util.zip.CRC32;

import rs2.net.Buffer;
import rs2.sign.Signlink;

/**
 * Owns the revision-377 bootstrap archive checksums and disk-cache indices.
 *
 * <p>
 * The retry timing and JAGGRAB framing deliberately remain revision-accurate.
 * Local bootstrap archives are CRC-validated before use; corrupt entries are
 * treated as cache misses and recovered through the same JAGGRAB path as
 * missing entries.
 * </p>
 */
public final class ResourceLoader {

	public interface JaggrabOpener {
		DataInputStream open(String request) throws IOException;
	}

	public interface ProgressListener {
		void update(int percent, String message);
	}

	private static final int ARCHIVE_COUNT = 9;
	private static final int CACHE_INDEX_COUNT = 5;
	private static final int REVISION = 377;
	private static final int MAX_CACHE_ENTRY_SIZE = 0xffffff;

	/**
	 * Fallback CRC-32 values for the authentic revision-377 bootstrap cache.
	 * Startup immediately replaces these with the authoritative table fetched from
	 * the game server before any bootstrap archive is accepted.
	 */
	private static final int[] REVISION_377_BOOTSTRAP_CRCS = { 0, 0x9509ece5, 0x88dcbfa7, 0x5574bc2e,
			0xa10e55ac, 0x3b8ed781, 0x982e83fb, 0x84fff872, 0x42fd7584 };

	private final int[] archiveCrcs = REVISION_377_BOOTSTRAP_CRCS.clone();
	private final CacheIndex[] cacheIndices = new CacheIndex[CACHE_INDEX_COUNT];
	private final CRC32 crc32 = new CRC32();

	public void initializeCacheIndices(RandomAccessFile dataFile, RandomAccessFile[] indexFiles) {
		if (dataFile == null) {
			return;
		}
		for (int index = 0; index < CACHE_INDEX_COUNT; index++) {
			cacheIndices[index] = new CacheIndex(index + 1, MAX_CACHE_ENTRY_SIZE, dataFile, indexFiles[index]);
		}
	}



	public int getArchiveCrc(int index) {
		return archiveCrcs[index];
	}

	public CacheIndex getCacheIndex(int index) {
		return cacheIndices[index];
	}

	public boolean hasCache() {
		return cacheIndices[0] != null;
	}


	/** Loads and CRC-validates one bootstrap archive, recovering it over JAGGRAB when necessary. */
	public Archive loadArchive(int expectedCrc, String archiveName, int loadingPercent, int cacheFileId,
			String displayName, JaggrabOpener opener, ProgressListener progress) {
		byte[] data = null;
		int retryDelay = 5;
		try {
			if (cacheIndices[0] != null) {
				data = cacheIndices[0].read(cacheFileId);
				if (data != null && checksum(data) == expectedCrc) {
					return new Archive(data);
				}
				data = null;
			}
		} catch (RuntimeException ignored) {
			/* Treat malformed or otherwise unusable cached bytes as a cache miss. */
			data = null;
		}

		int checksumFailures = 0;
		while (data == null) {
			String error = "Unknown error";
			progress.update(loadingPercent, "Requesting " + displayName);
			try (DataInputStream input = opener.open(archiveName + expectedCrc)) {
				int lastPercent = 0;
				byte[] header = new byte[6];
				input.readFully(header, 0, header.length);
				Buffer headerBuffer = new Buffer(header);
				headerBuffer.position = 3;
				int totalLength = headerBuffer.readMedium() + header.length;
				int position = header.length;
				data = new byte[totalLength];
				System.arraycopy(header, 0, data, 0, header.length);

				while (position < totalLength) {
					int blockLength = Math.min(1000, totalLength - position);
					int bytesRead = input.read(data, position, blockLength);
					if (bytesRead < 0) {
						error = "Length error: " + position + "/" + totalLength;
						throw new EOFException(error);
					}
					if (bytesRead == 0) {
						continue;
					}
					position += bytesRead;
					int percent = (position * 100) / totalLength;
					if (percent != lastPercent) {
						progress.update(loadingPercent, "Loading " + displayName + " - " + percent + "%");
					}
					lastPercent = percent;
				}

				int actualCrc = checksum(data);
				if (actualCrc != expectedCrc) {
					data = null;
					checksumFailures++;
					error = "Checksum error: " + actualCrc;
				} else if (cacheIndices[0] != null) {
					/* Cache only bytes that passed the authoritative CRC check. */
					cacheIndices[0].write(cacheFileId, data);
				}
			} catch (IOException exception) {
				if (error.equals("Unknown error")) {
					error = "Connection error";
				}
				data = null;
			} catch (NullPointerException exception) {
				error = "Null error";
				data = null;
				if (!Signlink.reportErrors) {
					return null;
				}
			} catch (ArrayIndexOutOfBoundsException exception) {
				error = "Bounds error";
				data = null;
				if (!Signlink.reportErrors) {
					return null;
				}
			} catch (RuntimeException exception) {
				error = "Unexpected error";
				data = null;
				if (!Signlink.reportErrors) {
					return null;
				}
			}

			if (data == null) {
				for (int seconds = retryDelay; seconds > 0; seconds--) {
					if (checksumFailures >= 3) {
						progress.update(loadingPercent, "Game updated - please reload page");
						seconds = 10;
					} else {
						progress.update(loadingPercent, error + " - Retrying in " + seconds);
					}
					try {
						Thread.sleep(1000L);
					} catch (InterruptedException ignored) {
					}
				}
				retryDelay *= 2;
				if (retryDelay > 60) {
					retryDelay = 60;
				}
			}
		}
		return new Archive(data);
	}

	/** Fetches and validates the revision-377 bootstrap CRC table used for cache recovery. */
	public void fetchArchiveCrcs(JaggrabOpener opener, ProgressListener progress) {
		int retryDelay = 5;
		int failures = 0;
		boolean loaded = false;
		while (!loaded) {
			String error = "Unknown problem";
			progress.update(20, "Checking server cache");
			try (DataInputStream input = opener.open("crc" + (int) (Math.random() * 99999999D) + "-" + REVISION)) {
				Buffer buffer = new Buffer(new byte[40]);
				input.readFully(buffer.payload, 0, buffer.payload.length);

				int[] fetchedCrcs = new int[ARCHIVE_COUNT];
				for (int index = 0; index < ARCHIVE_COUNT; index++) {
					fetchedCrcs[index] = buffer.readInt();
				}
				int expectedChecksum = buffer.readInt();
				int checksum = 1234;
				for (int index = 0; index < ARCHIVE_COUNT; index++) {
					checksum = (checksum << 1) + fetchedCrcs[index];
				}
				if (expectedChecksum != checksum || fetchedCrcs[ARCHIVE_COUNT - 1] == 0) {
					error = "checksum problem";
				} else {
					System.arraycopy(fetchedCrcs, 0, archiveCrcs, 0, ARCHIVE_COUNT);
					loaded = true;
				}
			} catch (EOFException exception) {
				error = "EOF problem";
			} catch (IOException exception) {
				error = "connection problem";
			} catch (RuntimeException exception) {
				error = "logic problem";
				if (!Signlink.reportErrors) {
					return;
				}
			}

			if (!loaded) {
				failures++;
				for (int seconds = retryDelay; seconds > 0; seconds--) {
					if (failures >= 10) {
						progress.update(10, "Game updated - please reload page");
						seconds = 10;
					} else {
						progress.update(10, error + " - Will retry in " + seconds + " secs.");
					}
					try {
						Thread.sleep(1000L);
					} catch (InterruptedException ignored) {
					}
				}
				retryDelay *= 2;
				if (retryDelay > 60) {
					retryDelay = 60;
				}
			}
		}
	}

	private int checksum(byte[] data) {
		crc32.reset();
		crc32.update(data);
		return (int) crc32.getValue();
	}
}
