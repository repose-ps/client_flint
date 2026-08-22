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

	/**
	 * CRC-32 values for bootstrap cache entries 1..8 in the supplied authentic
	 * revision-377 cache fixture. Entry zero is not present in cache index 0 and
	 * therefore remains unknown until a server CRC table is fetched.
	 *
	 * <p>These defaults preserve standalone startup without a web server while
	 * still allowing local corruption to be detected. A successfully fetched CRC
	 * table replaces all nine values.</p>
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
			cacheIndices[index] = new CacheIndex(index + 1, 0x927c0, dataFile, indexFiles[index]);
		}
	}

	public int getArchiveCrc(int index) {
		return archiveCrcs[index];
	}

	public int[] copyArchiveCrcs() {
		return archiveCrcs.clone();
	}

	public CacheIndex getCacheIndex(int index) {
		return cacheIndices[index];
	}

	public boolean hasCache() {
		return cacheIndices[0] != null;
	}

	/**
	 * Returns whether all eight bootstrap archives needed before the on-demand
	 * system are readable and match the known revision-377 CRCs.
	 *
	 * <p>If any archive is absent, structurally unreadable, or has the wrong CRC,
	 * startup fetches the server CRC table before attempting JAGGRAB recovery.</p>
	 */
	public boolean hasAllBootstrapArchives() {
		if (cacheIndices[0] == null) {
			return false;
		}
		try {
			for (int archiveId = 1; archiveId < ARCHIVE_COUNT; archiveId++) {
				byte[] data = cacheIndices[0].read(archiveId);
				if (data == null || checksum(data) != archiveCrcs[archiveId]) {
					return false;
				}
			}
			return true;
		} catch (RuntimeException exception) {
			return false;
		}
	}

	/**
	 * Legacy client.method61(int i, int j, String s, int k, int l, String s1)
	 *
	 * Parameter mapping: i -> removed fixed 14076 sentinel j -> expectedCrc s ->
	 * archiveName k -> loadingPercent l -> cacheFileId s1 -> displayName
	 */
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

	/**
	 * Legacy client.method86(boolean flag): flag -> removed false sentinel.
	 *
	 * <p>
	 * Startup calls this only when the local bootstrap set is missing or fails
	 * revision-377 CRC validation, preserving offline startup for a complete valid
	 * cache while retaining authoritative server recovery when needed.
	 * </p>
	 */
	public void fetchArchiveCrcs(JaggrabOpener opener, ProgressListener progress) {
		int retryDelay = 5;
		int failures = 0;
		boolean loaded = false;
		while (!loaded) {
			String error = "Unknown problem";
			progress.update(20, "Connecting to web server");
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