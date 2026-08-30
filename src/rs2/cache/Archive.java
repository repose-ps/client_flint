package rs2.cache;

import java.util.Locale;
import java.util.Objects;

import rs2.cache.bzip2.Bzip2Decompressor;
import rs2.net.Buffer;

/** Provides archive state and behavior. */
public class Archive {

	/** Constant value for header size. */
	private static final int HEADER_SIZE = 6;
	/** Constant value for entry size. */
	private static final int ENTRY_SIZE = 10;

	/** Stores data values. */
	private final byte[] data;
	/** Stores name hashes values. */
	private final int[] nameHashes;
	/** Stores uncompressed sizes values. */
	private final int[] uncompressedSizes;
	/** Stores compressed sizes values. */
	private final int[] compressedSizes;
	/** Stores offsets values. */
	private final int[] offsets;
	/** Whether whole archive compressed is enabled or active. */
	private final boolean wholeArchiveCompressed;

	/**
	 * Parses an archive and decompresses its outer payload when necessary.
	 *
	 * @param source complete archive bytes, including the six-byte header
	 * @throws IllegalArgumentException if the archive is truncated or malformed
	 */
	public Archive(byte[] source) {
		Objects.requireNonNull(source, "source");

		require(source.length >= HEADER_SIZE, "Archive header is truncated");

		Buffer buffer = new Buffer(source);

		int uncompressedLength = buffer.readMedium();
		int compressedLength = buffer.readMedium();

		if (compressedLength != uncompressedLength) {
			require(compressedLength <= source.length - HEADER_SIZE, "Compressed archive payload is truncated");

			data = new byte[uncompressedLength];

			int bytesWritten = Bzip2Decompressor.decompress(data, uncompressedLength, source, compressedLength,
					HEADER_SIZE);

			require(bytesWritten == uncompressedLength, "Archive decompressed to an unexpected length");

			buffer = new Buffer(data);
			wholeArchiveCompressed = true;
		} else {
			require(uncompressedLength <= source.length - HEADER_SIZE, "Archive payload is truncated");

			data = source;
			wholeArchiveCompressed = false;
		}

		require(buffer.position <= data.length - 2, "Archive file count is truncated");

		int fileCount = buffer.readUnsignedShort();

		require(fileCount <= (data.length - buffer.position) / ENTRY_SIZE, "Archive file table is truncated");

		nameHashes = new int[fileCount];
		uncompressedSizes = new int[fileCount];
		compressedSizes = new int[fileCount];
		offsets = new int[fileCount];

		int nextOffset = buffer.position + fileCount * ENTRY_SIZE;

		for (int index = 0; index < fileCount; index++) {
			nameHashes[index] = buffer.readInt();
			uncompressedSizes[index] = buffer.readMedium();
			compressedSizes[index] = buffer.readMedium();
			offsets[index] = nextOffset;

			require(compressedSizes[index] <= data.length - nextOffset, "Archive file data is truncated");

			nextOffset += compressedSizes[index];
		}
	}

	/**
	 * Returns a newly allocated file, or {@code null} when the name is absent.
	 *
	 * <p>
	 * Returning a fresh array preserves the original Client's behavior and prevents
	 * callers from mutating the archive's shared backing data.
	 * </p>
	 * @param fileName the file name
	 * @return the decoded  value
	 */
	public byte[] read(String fileName) {
		int requestedHash = hashName(fileName);

		for (int index = 0; index < nameHashes.length; index++) {
			if (nameHashes[index] != requestedHash) {
				continue;
			}

			byte[] file = new byte[uncompressedSizes[index]];

			if (wholeArchiveCompressed) {
				require(uncompressedSizes[index] == compressedSizes[index],
						"Whole-archive entry has inconsistent lengths");

				System.arraycopy(data, offsets[index], file, 0, file.length);
			} else {
				int bytesWritten = Bzip2Decompressor.decompress(file, file.length, data, compressedSizes[index],
						offsets[index]);

				require(bytesWritten == file.length, "Archive file decompressed to an unexpected length");
			}

			return file;
		}

		return null;
	}

	/**
	 * Computes the case-insensitive hash stored in revision 377 archives.
	 * @param fileName the file name
	 * @return whether h name
	 */
	public static int hashName(String fileName) {
		Objects.requireNonNull(fileName, "fileName");

		int hash = 0;
		String normalized = fileName.toUpperCase(Locale.ROOT);

		for (int index = 0; index < normalized.length(); index++) {
			hash = hash * 61 + normalized.charAt(index) - 32;
		}

		return hash;
	}

	/**
	 * Validates an archive decoding invariant.
	 *
	 * @param condition the condition
	 * @param message the message text
	 */
	private static void require(boolean condition, String message) {
		if (!condition) {
			throw new IllegalArgumentException(message);
		}
	}

}
