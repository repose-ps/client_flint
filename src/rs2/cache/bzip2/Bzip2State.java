package rs2.cache.bzip2;

/**
 * Mutable workspace used while decoding one cache BZip2 stream.
 *
 * <p>The revision 377 cache removes the normal {@code BZh1} stream header,
 * but the remaining block representation is BZip2. Keeping this state in a
 * separate object makes the decoder re-entrant: every decompression call owns
 * its bit reader, Huffman tables, move-to-front table, and inverse-BWT table.</p>
 *
 * <p>Most field names follow the terminology used by the BZip2 format. This is
 * intentionally package-private implementation state, not part of the cache
 * API.</p>
 */
final class Bzip2State {

    static final int ALPHABET_SIZE = 256;
    static final int SYMBOL_GROUP_COUNT = 16;
    static final int MAX_HUFFMAN_GROUPS = 6;
    static final int MAX_ALPHA_SIZE = 258;
    static final int MAX_SELECTORS = 18_002;
    static final int HUFFMAN_DECODE_TABLE_SIZE = 258;
    static final int MOVE_TO_FRONT_SIZE = 4_096;
    static final int BLOCK_SIZE = 100_000;

    byte[] input;
    int inputPosition;
    int inputRemaining;

    byte[] output;
    int outputPosition;
    int outputRemaining;

    byte outputRunByte;
    int outputRunLength;

    int bitBuffer;
    int liveBits;

    int originalPointer;
    int transformationPosition;
    int currentByte;
    int usedBlockBytes;
    int blockLength;

    final int[] frequencyTable = new int[ALPHABET_SIZE];
    final int[] cumulativeFrequency = new int[ALPHABET_SIZE + 1];
    final int[] transformationTable = new int[BLOCK_SIZE];

    int usedSymbolCount;
    final boolean[] symbolInUse = new boolean[ALPHABET_SIZE];
    final boolean[] symbolGroupInUse = new boolean[SYMBOL_GROUP_COUNT];
    final byte[] symbolMap = new byte[ALPHABET_SIZE];

    final byte[] moveToFront = new byte[MOVE_TO_FRONT_SIZE];
    final int[] moveToFrontBase = new int[SYMBOL_GROUP_COUNT];

    final byte[] selector = new byte[MAX_SELECTORS];
    final byte[] selectorMoveToFront = new byte[MAX_SELECTORS];

    final byte[][] codeLengths =
        new byte[MAX_HUFFMAN_GROUPS][MAX_ALPHA_SIZE];

    final int[][] codeLimits =
        new int[MAX_HUFFMAN_GROUPS][HUFFMAN_DECODE_TABLE_SIZE];

    final int[][] codeBases =
        new int[MAX_HUFFMAN_GROUPS][HUFFMAN_DECODE_TABLE_SIZE];

    final int[][] codePermutations =
        new int[MAX_HUFFMAN_GROUPS][HUFFMAN_DECODE_TABLE_SIZE];

    final int[] minimumCodeLengths = new int[MAX_HUFFMAN_GROUPS];
}