package rs2.cache;

import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Reads and writes one index in the revision-377 disk cache.
 *
 * <p>
 * Each six-byte index entry stores a three-byte file length and a three-byte
 * first-sector number. File data is split across 520-byte sectors. Every sector
 * contains an eight-byte header followed by at most 512 bytes of payload.
 * </p>
 *
 * <p>
 * This object does not own either {@link RandomAccessFile}; their lifecycle
 * remains with the cache bootstrap code that opened them.
 * </p>
 */
public final class CacheIndex {

	private static final int INDEX_ENTRY_SIZE = 6;

	private static final int SECTOR_SIZE = 520;
	private static final int SECTOR_HEADER_SIZE = 8;

	private static final int SECTOR_PAYLOAD_SIZE = SECTOR_SIZE - SECTOR_HEADER_SIZE;

	/*
	 * Entry and chunk identifiers occupy two bytes in a sector header.
	 */
	private static final int MAX_ENTRY_ID = 0xffff;

	/*
	 * The cache index identifier occupies one byte in a sector header.
	 */
	private static final int MAX_INDEX_ID = 0xff;

	/*
	 * File lengths and sector numbers are stored as unsigned 24-bit values.
	 */
	private static final int MAX_MEDIUM_VALUE = 0xffffff;

	private final int indexId;
	private final int maxEntrySize;

	private final RandomAccessFile dataFile;
	private final RandomAccessFile indexFile;

	/**
	 * All indexes share the same data RandomAccessFile and therefore the same
	 * mutable file pointer.
	 *
	 * <p>
	 * Locking that shared object coordinates operations across different CacheIndex
	 * instances, not merely different threads using one instance.
	 * </p>
	 */
	private final Object ioLock;

	/**
	 * Scratch space used for index entries, sector headers, and sector data.
	 *
	 * Access is protected by {@link #ioLock}.
	 */
	private final byte[] scratch = new byte[SECTOR_SIZE];

	public CacheIndex(int indexId, int maxEntrySize, RandomAccessFile dataFile, RandomAccessFile indexFile) {
		if (indexId < 0 || indexId > MAX_INDEX_ID) {
			throw new IllegalArgumentException("indexId must be between 0 and 255: " + indexId);
		}

		if (maxEntrySize < 0 || maxEntrySize > MAX_MEDIUM_VALUE) {
			throw new IllegalArgumentException("Invalid maximum entry size: " + maxEntrySize);
		}

		if (dataFile == null) {
			throw new NullPointerException("dataFile");
		}

		if (indexFile == null) {
			throw new NullPointerException("indexFile");
		}

		this.indexId = indexId;
		this.maxEntrySize = maxEntrySize;
		this.dataFile = dataFile;
		this.indexFile = indexFile;

		/*
		 * Multiple indexes created with the same RandomAccessFile will share this
		 * monitor.
		 */
		ioLock = dataFile;
	}

	/**
	 * Reads an entry and validates every sector in its chain.
	 *
	 * @param entryId entry identifier from the index file
	 *
	 * @return the complete entry, or {@code null} when it is missing, corrupt,
	 *         truncated, oversized, or could not be read
	 */
	public byte[] read(int entryId) {
		checkEntryId(entryId);

		synchronized (ioLock) {
			try {
				return readEntry(entryId);
			} catch (IOException exception) {
				/*
				 * The original client treats unreadable cache entries as misses and attempts to
				 * download them again.
				 */
				return null;
			}
		}
	}

	/**
	 * Writes or replaces an entry.
	 *
	 * <p>
	 * The implementation first attempts to reuse the entry's existing sector chain.
	 * If that chain does not exist or cannot be reused, a new chain is appended to
	 * the data file.
	 * </p>
	 *
	 * @param entryId entry identifier
	 * @param data    complete entry contents
	 *
	 * @return {@code true} when all index and sector data was written
	 */
	public boolean write(int entryId, byte[] data) {
		checkEntryId(entryId);

		if (data == null) {
			throw new NullPointerException("data");
		}

		if (data.length > maxEntrySize) {
			throw new IllegalArgumentException("Entry length " + data.length + " exceeds maximum " + maxEntrySize);
		}

		synchronized (ioLock) {
			try {
				/*
				 * Empty entries require no sector. The original implementation produced an
				 * unreadable empty entry because it still required a positive first-sector
				 * number.
				 */
				if (data.length == 0) {
					writeIndexEntry(entryId, 0, 0);

					return true;
				}

				if (writeEntry(entryId, data, true)) {
					return true;
				}

				return writeEntry(entryId, data, false);
			} catch (IOException exception) {
				return false;
			}
		}
	}

	private byte[] readEntry(int entryId) throws IOException {
		long indexPosition = (long) entryId * INDEX_ENTRY_SIZE;

		indexFile.seek(indexPosition);

		if (!readFully(indexFile, scratch, 0, INDEX_ENTRY_SIZE)) {
			return null;
		}

		int length = readMedium(scratch, 0);
		int sector = readMedium(scratch, 3);

		if (length < 0 || length > maxEntrySize) {
			return null;
		}

		if (length == 0) {
			return new byte[0];
		}

		if (!isExistingSector(sector)) {
			return null;
		}

		byte[] data = new byte[length];

		int destinationOffset = 0;
		int chunk = 0;

		while (destinationOffset < length) {
			/*
			 * A zero sector is the end-of-chain marker. Encountering it before all
			 * requested bytes have been read means the chain is truncated.
			 */
			if (sector == 0) {
				return null;
			}

			int payloadLength = Math.min(SECTOR_PAYLOAD_SIZE, length - destinationOffset);

			dataFile.seek((long) sector * SECTOR_SIZE);

			if (!readFully(dataFile, scratch, 0, SECTOR_HEADER_SIZE + payloadLength)) {
				return null;
			}

			int storedEntryId = readUnsignedShort(scratch, 0);

			int storedChunk = readUnsignedShort(scratch, 2);

			int nextSector = readMedium(scratch, 4);

			int storedIndexId = scratch[7] & 0xff;

			/*
			 * These values prevent an invalid chain from crossing into another file, chunk,
			 * or cache index.
			 */
			if (storedEntryId != entryId || storedChunk != chunk || storedIndexId != indexId) {
				return null;
			}

			if (nextSector != 0 && !isExistingSector(nextSector)) {
				return null;
			}

			System.arraycopy(scratch, SECTOR_HEADER_SIZE, data, destinationOffset, payloadLength);

			destinationOffset += payloadLength;
			sector = nextSector;
			chunk++;
		}

		return data;
	}

	/**
	 * Attempts either an overwrite using an existing chain or a new append.
	 */
	private boolean writeEntry(int entryId, byte[] data, boolean overwrite) throws IOException {
		int sector;

		if (overwrite) {
			long indexPosition = (long) entryId * INDEX_ENTRY_SIZE;

			indexFile.seek(indexPosition);

			if (!readFully(indexFile, scratch, 0, INDEX_ENTRY_SIZE)) {
				return false;
			}

			sector = readMedium(scratch, 3);

			if (!isExistingSector(sector)) {
				return false;
			}
		} else {
			sector = nextFreeSector();

			if (!isWritableSector(sector)) {
				return false;
			}
		}

		writeIndexEntry(entryId, data.length, sector);

		int sourceOffset = 0;
		int chunk = 0;

		while (sourceOffset < data.length) {
			int nextSector = 0;

			/*
			 * When overwriting, inspect the existing sector header to locate and validate
			 * the next sector in the chain.
			 */
			if (overwrite) {
				dataFile.seek((long) sector * SECTOR_SIZE);

				if (readFully(dataFile, scratch, 0, SECTOR_HEADER_SIZE)) {
					int storedEntryId = readUnsignedShort(scratch, 0);

					int storedChunk = readUnsignedShort(scratch, 2);

					nextSector = readMedium(scratch, 4);

					int storedIndexId = scratch[7] & 0xff;

					if (storedEntryId != entryId || storedChunk != chunk || storedIndexId != indexId) {
						return false;
					}

					if (nextSector != 0 && !isExistingSector(nextSector)) {
						return false;
					}

					/*
					 * A self-referencing sector would cause the writer to overwrite the same sector
					 * repeatedly.
					 */
					if (nextSector == sector) {
						return false;
					}
				}
			}

			/*
			 * The existing chain ended or this is a new entry. Allocate the next sector at
			 * the end of the data file.
			 */
			if (nextSector == 0) {
				overwrite = false;
				nextSector = nextFreeSector();

				/*
				 * Before the current sector has been written, the calculated end-of-file sector
				 * may equal it. Sector chains cannot point to themselves, so advance to the
				 * following sector.
				 */
				if (nextSector == sector) {
					nextSector++;
				}

				if (!isWritableSector(nextSector)) {
					return false;
				}
			}

			int payloadLength = Math.min(SECTOR_PAYLOAD_SIZE, data.length - sourceOffset);

			/*
			 * The final sector terminates the chain with a zero pointer.
			 */
			if (sourceOffset + payloadLength >= data.length) {
				nextSector = 0;
			}

			writeUnsignedShort(scratch, 0, entryId);

			writeUnsignedShort(scratch, 2, chunk);

			writeMedium(scratch, 4, nextSector);

			scratch[7] = (byte) indexId;

			dataFile.seek((long) sector * SECTOR_SIZE);

			dataFile.write(scratch, 0, SECTOR_HEADER_SIZE);

			dataFile.write(data, sourceOffset, payloadLength);

			sourceOffset += payloadLength;
			sector = nextSector;
			chunk++;
		}

		return true;
	}

	/**
	 * Writes one six-byte index entry.
	 */
	private void writeIndexEntry(int entryId, int length, int firstSector) throws IOException {
		writeMedium(scratch, 0, length);

		writeMedium(scratch, 3, firstSector);

		indexFile.seek((long) entryId * INDEX_ENTRY_SIZE);

		indexFile.write(scratch, 0, INDEX_ENTRY_SIZE);
	}

	/**
	 * Calculates the next sector at or beyond the current end of the file.
	 *
	 * Sector zero is reserved as the end-of-chain marker.
	 */
	private int nextFreeSector() throws IOException {
		long sector = (dataFile.length() + SECTOR_SIZE - 1L) / SECTOR_SIZE;

		if (sector == 0L) {
			sector = 1L;
		}

		if (sector > MAX_MEDIUM_VALUE) {
			return -1;
		}

		return (int) sector;
	}

	/**
	 * Determines whether a sector currently exists in the data file.
	 */
	private boolean isExistingSector(int sector) throws IOException {
		return sector > 0 && sector <= MAX_MEDIUM_VALUE && (long) sector <= dataFile.length() / SECTOR_SIZE;
	}

	/**
	 * Determines whether a sector can be represented in a three-byte pointer.
	 */
	private static boolean isWritableSector(int sector) {
		return sector > 0 && sector <= MAX_MEDIUM_VALUE;
	}

	/**
	 * Reads exactly the requested number of bytes.
	 *
	 * @return false when end-of-file is reached first
	 */
	private static boolean readFully(RandomAccessFile file, byte[] destination, int offset, int length)
			throws IOException {
		int total = 0;

		while (total < length) {
			int count = file.read(destination, offset + total, length - total);

			if (count < 0) {
				return false;
			}

			if (count == 0) {
				continue;
			}

			total += count;
		}

		return true;
	}

	private static int readUnsignedShort(byte[] source, int offset) {
		return ((source[offset] & 0xff) << 8) | (source[offset + 1] & 0xff);
	}

	private static int readMedium(byte[] source, int offset) {
		return ((source[offset] & 0xff) << 16) | ((source[offset + 1] & 0xff) << 8) | (source[offset + 2] & 0xff);
	}

	private static void writeUnsignedShort(byte[] destination, int offset, int value) {
		destination[offset] = (byte) (value >>> 8);

		destination[offset + 1] = (byte) value;
	}

	private static void writeMedium(byte[] destination, int offset, int value) {
		destination[offset] = (byte) (value >>> 16);

		destination[offset + 1] = (byte) (value >>> 8);

		destination[offset + 2] = (byte) value;
	}

	private static void checkEntryId(int entryId) {
		if (entryId < 0 || entryId > MAX_ENTRY_ID) {
			throw new IllegalArgumentException("entryId must be between 0 and 65535: " + entryId);
		}
	}
}