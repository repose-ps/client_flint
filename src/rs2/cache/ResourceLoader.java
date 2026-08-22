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
 * The retry timing and JAGGRAB framing deliberately remain source-accurate. In
 * particular, the supplied client returns a disk-cached archive immediately
 * without validating its CRC. That behavior is preserved here rather than
 * silently repaired.
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

	private final int[] archiveCrcs = new int[ARCHIVE_COUNT];
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
				return new Archive(data);
			}
		} catch (Exception ignored) {
		}

		// Retained for source correspondence even though the immediate cache return
		// above means a successfully read cache entry never reaches this check.
		if (data != null && checksum(data) != expectedCrc) {
			data = null;
		}
		if (data != null) {
			return new Archive(data);
		}

		int checksumFailures = 0;
		while (data == null) {
			String error = "Unknown error";
			progress.update(loadingPercent, "Requesting " + displayName);
			try {
				int lastPercent = 0;
				DataInputStream input = opener.open(archiveName + expectedCrc);
				byte[] header = new byte[6];
				input.readFully(header, 0, 6);
				Buffer headerBuffer = new Buffer(header);
				headerBuffer.position = 3;
				int totalLength = headerBuffer.readMedium() + 6;
				int position = 6;
				data = new byte[totalLength];
				System.arraycopy(header, 0, data, 0, 6);

				while (position < totalLength) {
					int blockLength = totalLength - position;
					if (blockLength > 1000) {
						blockLength = 1000;
					}
					int bytesRead = input.read(data, position, blockLength);
					if (bytesRead < 0) {
						error = "Length error: " + position + "/" + totalLength;
						throw new IOException("EOF");
					}
					position += bytesRead;
					int percent = (position * 100) / totalLength;
					if (percent != lastPercent) {
						progress.update(loadingPercent, "Loading " + displayName + " - " + percent + "%");
					}
					lastPercent = percent;
				}
				input.close();

				try {
					if (cacheIndices[0] != null) {
						cacheIndices[0].write(cacheFileId, data);
					}
				} catch (Exception ignored) {
					cacheIndices[0] = null;
				}

				if (data != null) {
					int actualCrc = checksum(data);
					if (actualCrc != expectedCrc) {
						data = null;
						checksumFailures++;
						error = "Checksum error: " + actualCrc;
					}
				}
			} catch (IOException exception) {
				exception.printStackTrace();
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
			} catch (Exception exception) {
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
					} catch (Exception ignored) {
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
	 * This method remains available for source correspondence, but the supplied
	 * post-refactor startup path intentionally does not call it.
	 * </p>
	 */
	public void fetchArchiveCrcs(JaggrabOpener opener, ProgressListener progress) {
		int retryDelay = 5;
		archiveCrcs[8] = 0;
		int failures = 0;
		while (archiveCrcs[8] == 0) {
			String error = "Unknown problem";
			progress.update(20, "Connecting to web server");
			try {
				DataInputStream input = opener.open("crc" + (int) (Math.random() * 99999999D) + "-" + REVISION);
				Buffer buffer = new Buffer(new byte[40]);
				input.readFully(buffer.payload, 0, 40);
				input.close();
				for (int index = 0; index < ARCHIVE_COUNT; index++) {
					archiveCrcs[index] = buffer.readInt();
				}
				int expectedChecksum = buffer.readInt();
				int checksum = 1234;
				for (int index = 0; index < ARCHIVE_COUNT; index++) {
					checksum = (checksum << 1) + archiveCrcs[index];
				}
				if (expectedChecksum != checksum) {
					error = "checksum problem";
					archiveCrcs[8] = 0;
				}
			} catch (EOFException exception) {
				error = "EOF problem";
				archiveCrcs[8] = 0;
			} catch (IOException exception) {
				error = "connection problem";
				archiveCrcs[8] = 0;
			} catch (Exception exception) {
				error = "logic problem";
				archiveCrcs[8] = 0;
				if (!Signlink.reportErrors) {
					return;
				}
			}

			if (archiveCrcs[8] == 0) {
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
					} catch (Exception ignored) {
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