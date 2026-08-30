package rs2.cache.bzip2;

/**
 * Mutable workspace used while decoding one cache BZip2 stream.
 *
 * <p>
 * The revision 377 cache removes the normal {@code BZh1} stream header, but the
 * remaining block representation is BZip2. Keeping this state in a separate
 * object makes the decoder re-entrant: every decompression call owns its bit
 * reader, Huffman tables, move-to-front table, and inverse-BWT table.
 * </p>
 *
 * <p>
 * Most field names follow the terminology used by the BZip2 format. This is
 * intentionally package-private implementation state, not part of the cache
 * API.
 * </p>
 */
final class Bzip2State {

	/** Creates a new BZIP2 state with its default client state. */
	Bzip2State() {
	}

	/** Constant value for alphabet size. */
	static final int ALPHABET_SIZE = 256;
	/** Constant value for symbol group count. */
	static final int SYMBOL_GROUP_COUNT = 16;
	/** Maximum huffman groups. */
	static final int MAX_HUFFMAN_GROUPS = 6;
	/** Maximum alpha size. */
	static final int MAX_ALPHA_SIZE = 258;
	/** Maximum selectors. */
	static final int MAX_SELECTORS = 18_002;
	/** Constant value for huffman decode table size. */
	static final int HUFFMAN_DECODE_TABLE_SIZE = 258;
	/** Constant value for move to front size. */
	static final int MOVE_TO_FRONT_SIZE = 4_096;
	/** Constant value for block size. */
	static final int BLOCK_SIZE = 100_000;

	/** Stores input values. */
	byte[] input;
	/** Stores the current input position. */
	int inputPosition;
	/** Stores the current input remaining. */
	int inputRemaining;

	/** Stores output values. */
	byte[] output;
	/** Stores the current output position. */
	int outputPosition;
	/** Stores the current output remaining. */
	int outputRemaining;

	/** Stores the current output run byte. */
	byte outputRunByte;
	/** Stores the current output run length. */
	int outputRunLength;

	/** Stores the current bit buffer. */
	int bitBuffer;
	/** Stores the current live bits. */
	int liveBits;

	/** Stores the current original pointer. */
	int originalPointer;
	/** Stores the current transformation position. */
	int transformationPosition;
	/** Stores the current byte. */
	int currentByte;
	/** Stores the current used block bytes. */
	int usedBlockBytes;
	/** Stores the current block length. */
	int blockLength;

	/** Stores frequency table values. */
	final int[] frequencyTable = new int[ALPHABET_SIZE];
	/** Stores cumulative frequency values. */
	final int[] cumulativeFrequency = new int[ALPHABET_SIZE + 1];
	/** Stores transformation table values. */
	final int[] transformationTable = new int[BLOCK_SIZE];

	/** Stores the current used symbol count. */
	int usedSymbolCount;
	/** Whether symbol in use is enabled or active. */
	final boolean[] symbolInUse = new boolean[ALPHABET_SIZE];
	/** Whether symbol group in use is enabled or active. */
	final boolean[] symbolGroupInUse = new boolean[SYMBOL_GROUP_COUNT];
	/** Stores symbol map values. */
	final byte[] symbolMap = new byte[ALPHABET_SIZE];

	/** Stores move to front values. */
	final byte[] moveToFront = new byte[MOVE_TO_FRONT_SIZE];
	/** Stores move to front base values. */
	final int[] moveToFrontBase = new int[SYMBOL_GROUP_COUNT];

	/** Stores selector values. */
	final byte[] selector = new byte[MAX_SELECTORS];
	/** Stores selector move to front values. */
	final byte[] selectorMoveToFront = new byte[MAX_SELECTORS];

	/** Stores code lengths values. */
	final byte[][] codeLengths = new byte[MAX_HUFFMAN_GROUPS][MAX_ALPHA_SIZE];

	/** Stores code limits values. */
	final int[][] codeLimits = new int[MAX_HUFFMAN_GROUPS][HUFFMAN_DECODE_TABLE_SIZE];

	/** Stores code bases values. */
	final int[][] codeBases = new int[MAX_HUFFMAN_GROUPS][HUFFMAN_DECODE_TABLE_SIZE];

	/** Stores code permutations values. */
	final int[][] codePermutations = new int[MAX_HUFFMAN_GROUPS][HUFFMAN_DECODE_TABLE_SIZE];

	/** Stores minimum code lengths values. */
	final int[] minimumCodeLengths = new int[MAX_HUFFMAN_GROUPS];
}
