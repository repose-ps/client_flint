package rs2.cache.bzip2;

import java.util.Objects;

/**
 * Decodes the headerless BZip2 blocks stored by the revision 377 cache.
 *
 * <p>A normal BZip2 stream begins with {@code BZh} and a block-size digit.
 * Cache archives omit those four bytes, so a general-purpose BZip2 stream
 * reader cannot consume them directly. This decoder starts at the first block
 * marker and uses the 100 KiB block size employed by the client.</p>
 *
 * <p>Each invocation owns a separate {@link Bzip2State}; decompression calls
 * can therefore run concurrently without sharing mutable decoder tables.</p>
 */
public final class Bzip2Decompressor {

    private static final int BLOCK_MAGIC_1 = 0x31;
    private static final int BLOCK_MAGIC_2 = 0x41;
    private static final int BLOCK_MAGIC_3 = 0x59;
    private static final int BLOCK_MAGIC_4 = 0x26;
    private static final int BLOCK_MAGIC_5 = 0x53;
    private static final int BLOCK_MAGIC_6 = 0x59;

    private static final int END_OF_STREAM_MAGIC_1 = 0x17;

    private Bzip2Decompressor() {
        // Utility class.
    }

    /**
     * Decompresses one headerless cache BZip2 stream.
     *
     * @param output destination array; decoded bytes begin at index zero
     * @param outputLength maximum number of bytes to write
     * @param input array containing the compressed stream
     * @param inputLength number of compressed bytes available
     * @param inputOffset index of the first BZip2 block marker
     * @return the number of bytes written to {@code output}
     * @throws IllegalArgumentException if a range or compressed stream is invalid
     */
    public static int decompress(
        byte[] output,
        int outputLength,
        byte[] input,
        int inputLength,
        int inputOffset
    ) {
        Objects.requireNonNull(output, "output");
        Objects.requireNonNull(input, "input");

        requireRange(
            outputLength >= 0 && outputLength <= output.length,
            "outputLength is outside the output array"
        );

        requireRange(
            inputOffset >= 0
                && inputLength >= 0
                && inputOffset <= input.length - inputLength,
            "inputOffset and inputLength are outside the input array"
        );

        if (outputLength == 0) {
            return 0;
        }

        Bzip2State state = new Bzip2State();
        state.input = input;
        state.inputPosition = inputOffset;
        state.inputRemaining = inputLength;
        state.output = output;
        state.outputRemaining = outputLength;

        decode(state);

        return outputLength - state.outputRemaining;
    }

    /**
     * Expands the final BZip2 run-length stage into the caller's output.
     */
    private static void writeDecodedBlock(Bzip2State state) {
        byte outputRunByte = state.outputRunByte;
        int outputRunLength = state.outputRunLength;
        int usedBlockBytes = state.usedBlockBytes;
        int currentByte = state.currentByte;

        int[] transformationTable = state.transformationTable;
        int transformationPosition = state.transformationPosition;

        byte[] output = state.output;
        int outputPosition = state.outputPosition;
        int outputRemaining = state.outputRemaining;

        int endOfBlock = state.blockLength + 1;

        outer:
        do {
            if (outputRunLength > 0) {
                do {
                    if (outputRemaining == 0) {
                        break outer;
                    }

                    if (outputRunLength == 1) {
                        break;
                    }

                    output[outputPosition++] = outputRunByte;
                    outputRunLength--;
                    outputRemaining--;
                } while (true);

                if (outputRemaining == 0) {
                    outputRunLength = 1;
                    break;
                }

                output[outputPosition++] = outputRunByte;
                outputRemaining--;
            }

            boolean continueOutput = true;

            while (continueOutput) {
                continueOutput = false;

                if (usedBlockBytes == endOfBlock) {
                    outputRunLength = 0;
                    break outer;
                }

                outputRunByte = (byte) currentByte;

                transformationPosition =
                    transformationTable[transformationPosition];

                byte nextByte = (byte) transformationPosition;
                transformationPosition >>>= 8;
                usedBlockBytes++;

                if (nextByte != currentByte) {
                    currentByte = nextByte;

                    if (outputRemaining == 0) {
                        outputRunLength = 1;
                    } else {
                        output[outputPosition++] = outputRunByte;
                        outputRemaining--;
                        continueOutput = true;
                        continue;
                    }

                    break outer;
                }

                if (usedBlockBytes != endOfBlock) {
                    continue;
                }

                if (outputRemaining == 0) {
                    outputRunLength = 1;
                    break outer;
                }

                output[outputPosition++] = outputRunByte;
                outputRemaining--;
                continueOutput = true;
            }

            outputRunLength = 2;

            transformationPosition =
                transformationTable[transformationPosition];

            byte nextByte = (byte) transformationPosition;
            transformationPosition >>>= 8;

            if (++usedBlockBytes != endOfBlock) {
                if (nextByte != currentByte) {
                    currentByte = nextByte;
                } else {
                    outputRunLength = 3;

                    transformationPosition =
                        transformationTable[transformationPosition];

                    byte thirdByte = (byte) transformationPosition;
                    transformationPosition >>>= 8;

                    if (++usedBlockBytes != endOfBlock) {
                        if (thirdByte != currentByte) {
                            currentByte = thirdByte;
                        } else {
                            transformationPosition =
                                transformationTable[transformationPosition];

                            int additionalRunLength =
                                transformationPosition & 0xff;

                            transformationPosition >>>= 8;
                            usedBlockBytes++;

                            outputRunLength = additionalRunLength + 4;

                            transformationPosition =
                                transformationTable[transformationPosition];

                            currentByte = (byte) transformationPosition;
                            transformationPosition >>>= 8;
                            usedBlockBytes++;
                        }
                    }
                }
            }
        } while (true);

        state.outputRunByte = outputRunByte;
        state.outputRunLength = outputRunLength;
        state.usedBlockBytes = usedBlockBytes;
        state.currentByte = currentByte;
        state.transformationPosition = transformationPosition;
        state.outputPosition = outputPosition;
        state.outputRemaining = outputRemaining;
    }

    private static void decode(Bzip2State state) {
        /*
         * BZip2 switches between Huffman tables after every 50 decoded
         * symbols. These variables refer to the currently selected table.
         */
        int minimumLength = 0;
        int[] activeLimits = null;
        int[] activeBases = null;
        int[] activePermutations = null;

        boolean blockComplete = true;

        while (blockComplete) {
            /*
             * Cache streams begin here. The standard four-byte BZh1 header has
             * already been removed by the archive packer.
             */
            int firstMagicByte = readUnsignedByte(state);

            if (firstMagicByte == END_OF_STREAM_MAGIC_1) {
                return;
            }

            requireRange(
                firstMagicByte == BLOCK_MAGIC_1
                    && readUnsignedByte(state) == BLOCK_MAGIC_2
                    && readUnsignedByte(state) == BLOCK_MAGIC_3
                    && readUnsignedByte(state) == BLOCK_MAGIC_4
                    && readUnsignedByte(state) == BLOCK_MAGIC_5
                    && readUnsignedByte(state) == BLOCK_MAGIC_6,
                "Invalid BZip2 block marker"
            );

            /*
             * The four-byte block CRC is retained for format compatibility.
             * The original cache decoder did not validate it, so it is skipped.
             */
            readUnsignedByte(state);
            readUnsignedByte(state);
            readUnsignedByte(state);
            readUnsignedByte(state);

            requireRange(
                readBit(state) == 0,
                "Randomized BZip2 blocks are not supported"
            );

            state.originalPointer = 0;
            state.originalPointer =
                state.originalPointer << 8 | readUnsignedByte(state);
            state.originalPointer =
                state.originalPointer << 8 | readUnsignedByte(state);
            state.originalPointer =
                state.originalPointer << 8 | readUnsignedByte(state);

            /*
             * Decode the compact 16-by-16 bitmap that identifies which byte
             * values occur in this block.
             */
            for (int index = 0; index < 16; index++) {
                state.symbolGroupInUse[index] = readBit(state) == 1;
            }

            for (int index = 0; index < 256; index++) {
                state.symbolInUse[index] = false;
            }

            for (int group = 0; group < 16; group++) {
                if (!state.symbolGroupInUse[group]) {
                    continue;
                }

                for (int offset = 0; offset < 16; offset++) {
                    if (readBit(state) == 1) {
                        state.symbolInUse[group * 16 + offset] = true;
                    }
                }
            }

            buildSymbolMap(state);

            int alphabetSize = state.usedSymbolCount + 2;
            int groupCount = readBits(3, state);
            int selectorCount = readBits(15, state);

            requireRange(
                groupCount >= 2
                    && groupCount <= Bzip2State.MAX_HUFFMAN_GROUPS,
                "Invalid BZip2 Huffman group count"
            );

            requireRange(
                selectorCount >= 1
                    && selectorCount <= Bzip2State.MAX_SELECTORS,
                "Invalid BZip2 selector count"
            );

            for (int index = 0; index < selectorCount; index++) {
                int count = 0;

                while (readBit(state) != 0) {
                    count++;
                }

                requireRange(
                    count < groupCount,
                    "Invalid BZip2 selector move-to-front index"
                );

                state.selectorMoveToFront[index] = (byte) count;
            }

            /*
             * Undo move-to-front coding of the Huffman-table selectors.
             */
            byte[] selectorOrder =
                new byte[Bzip2State.MAX_HUFFMAN_GROUPS];

            for (byte group = 0; group < groupCount; group++) {
                selectorOrder[group] = group;
            }

            for (int index = 0; index < selectorCount; index++) {
                byte selectorIndex = state.selectorMoveToFront[index];
                byte selectedGroup = selectorOrder[selectorIndex];

                while (selectorIndex > 0) {
                    selectorOrder[selectorIndex] =
                        selectorOrder[selectorIndex - 1];

                    selectorIndex--;
                }

                selectorOrder[0] = selectedGroup;
                state.selector[index] = selectedGroup;
            }

            /*
             * Read the delta-coded canonical Huffman code lengths.
             */
            for (int group = 0; group < groupCount; group++) {
                int currentLength = readBits(5, state);

                for (int symbol = 0; symbol < alphabetSize; symbol++) {
                    while (readBit(state) != 0) {
                        if (readBit(state) == 0) {
                            currentLength++;
                        } else {
                            currentLength--;
                        }
                    }

                    requireRange(
                        currentLength >= 1 && currentLength <= 20,
                        "Invalid BZip2 Huffman code length"
                    );

                    state.codeLengths[group][symbol] =
                        (byte) currentLength;
                }
            }

            for (int group = 0; group < groupCount; group++) {
                byte minimumLengthInGroup = 32;
                int maximumLengthInGroup = 0;

                for (int symbol = 0; symbol < alphabetSize; symbol++) {
                    int length = state.codeLengths[group][symbol];

                    if (length > maximumLengthInGroup) {
                        maximumLengthInGroup = length;
                    }

                    if (length < minimumLengthInGroup) {
                        minimumLengthInGroup = (byte) length;
                    }
                }

                buildHuffmanDecodeTables(
                    state.codeLimits[group],
                    state.codeBases[group],
                    state.codePermutations[group],
                    state.codeLengths[group],
                    minimumLengthInGroup,
                    maximumLengthInGroup,
                    alphabetSize
                );

                state.minimumCodeLengths[group] =
                    minimumLengthInGroup;
            }

            int endOfBlockSymbol = state.usedSymbolCount + 1;
            int groupIndex = -1;
            int groupRemaining = 0;

            for (int index = 0; index < 256; index++) {
                state.frequencyTable[index] = 0;
            }

            /*
             * Initialize the segmented move-to-front list with all possible
             * byte symbols.
             */
            int moveToFrontIndex =
                Bzip2State.MOVE_TO_FRONT_SIZE - 1;

            for (
                int group = Bzip2State.SYMBOL_GROUP_COUNT - 1;
                group >= 0;
                group--
            ) {
                for (int offset = 15; offset >= 0; offset--) {
                    state.moveToFront[moveToFrontIndex--] =
                        (byte) (group * 16 + offset);
                }

                state.moveToFrontBase[group] =
                    moveToFrontIndex + 1;
            }

            int decodedBlockLength = 0;

            if (groupRemaining == 0) {
                groupIndex++;

                requireRange(
                    groupIndex < selectorCount,
                    "BZip2 selector table ended early"
                );

                groupRemaining = 50;

                byte tableIndex = state.selector[groupIndex];

                minimumLength =
                    state.minimumCodeLengths[tableIndex];
                activeLimits =
                    state.codeLimits[tableIndex];
                activePermutations =
                    state.codePermutations[tableIndex];
                activeBases =
                    state.codeBases[tableIndex];
            }

            groupRemaining--;

            int codeLength = minimumLength;
            int code = readBits(codeLength, state);

            while (code > activeLimits[codeLength]) {
                codeLength++;
                code = code << 1 | readBit(state);
            }

            int nextSymbol =
                activePermutations[code - activeBases[codeLength]];

            while (nextSymbol != endOfBlockSymbol) {
                if (nextSymbol == 0 || nextSymbol == 1) {
                    /*
                     * Symbols zero and one encode a run of the current
                     * move-to-front head.
                     */
                    int runLength = -1;
                    int runPower = 1;

                    do {
                        if (nextSymbol == 0) {
                            runLength += runPower;
                        } else {
                            runLength += 2 * runPower;
                        }

                        runPower *= 2;

                        if (groupRemaining == 0) {
                            groupIndex++;

                            requireRange(
                                groupIndex < selectorCount,
                                "BZip2 selector table ended early"
                            );

                            groupRemaining = 50;

                            byte tableIndex =
                                state.selector[groupIndex];

                            minimumLength =
                                state.minimumCodeLengths[tableIndex];
                            activeLimits =
                                state.codeLimits[tableIndex];
                            activePermutations =
                                state.codePermutations[tableIndex];
                            activeBases =
                                state.codeBases[tableIndex];
                        }

                        groupRemaining--;

                        int nextCodeLength = minimumLength;
                        int nextCode =
                            readBits(nextCodeLength, state);

                        while (
                            nextCode
                                > activeLimits[nextCodeLength]
                        ) {
                            nextCodeLength++;
                            nextCode =
                                nextCode << 1 | readBit(state);
                        }

                        nextSymbol = activePermutations[
                            nextCode - activeBases[nextCodeLength]
                        ];
                    } while (
                        nextSymbol == 0 || nextSymbol == 1
                    );

                    runLength++;

                    byte repeatedByte = state.symbolMap[
                        state.moveToFront[
                            state.moveToFrontBase[0]
                        ] & 0xff
                    ];

                    state.frequencyTable[
                        repeatedByte & 0xff
                    ] += runLength;

                    while (runLength-- > 0) {
                        requireRange(
                            decodedBlockLength
                                < state.transformationTable.length,
                            "BZip2 block exceeds 100 KiB"
                        );

                        state.transformationTable[
                            decodedBlockLength++
                        ] = repeatedByte & 0xff;
                    }
                } else {
                    /*
                     * All other symbols select and promote an entry in the
                     * move-to-front list.
                     */
                    int symbolIndex = nextSymbol - 1;
                    byte movedSymbol;

                    if (symbolIndex < 16) {
                        int base = state.moveToFrontBase[0];

                        movedSymbol =
                            state.moveToFront[base + symbolIndex];

                        while (symbolIndex > 3) {
                            int position = base + symbolIndex;

                            state.moveToFront[position] =
                                state.moveToFront[position - 1];
                            state.moveToFront[position - 1] =
                                state.moveToFront[position - 2];
                            state.moveToFront[position - 2] =
                                state.moveToFront[position - 3];
                            state.moveToFront[position - 3] =
                                state.moveToFront[position - 4];

                            symbolIndex -= 4;
                        }

                        while (symbolIndex > 0) {
                            state.moveToFront[
                                base + symbolIndex
                            ] = state.moveToFront[
                                base + symbolIndex - 1
                            ];

                            symbolIndex--;
                        }

                        state.moveToFront[base] = movedSymbol;
                    } else {
                        int listIndex = symbolIndex / 16;
                        int listOffset = symbolIndex % 16;

                        int absoluteIndex =
                            state.moveToFrontBase[listIndex]
                                + listOffset;

                        movedSymbol =
                            state.moveToFront[absoluteIndex];

                        while (
                            absoluteIndex
                                > state.moveToFrontBase[listIndex]
                        ) {
                            state.moveToFront[absoluteIndex] =
                                state.moveToFront[absoluteIndex - 1];

                            absoluteIndex--;
                        }

                        state.moveToFrontBase[listIndex]++;

                        while (listIndex > 0) {
                            state.moveToFrontBase[listIndex]--;

                            state.moveToFront[
                                state.moveToFrontBase[listIndex]
                            ] = state.moveToFront[
                                state.moveToFrontBase[
                                    listIndex - 1
                                ] + 15
                            ];

                            listIndex--;
                        }

                        state.moveToFrontBase[0]--;

                        state.moveToFront[
                            state.moveToFrontBase[0]
                        ] = movedSymbol;

                        if (state.moveToFrontBase[0] == 0) {
                            int rebuildIndex =
                                Bzip2State.MOVE_TO_FRONT_SIZE - 1;

                            for (
                                int group =
                                    Bzip2State.SYMBOL_GROUP_COUNT - 1;
                                group >= 0;
                                group--
                            ) {
                                for (
                                    int offset = 15;
                                    offset >= 0;
                                    offset--
                                ) {
                                    state.moveToFront[
                                        rebuildIndex--
                                    ] = state.moveToFront[
                                        state.moveToFrontBase[group]
                                            + offset
                                    ];
                                }

                                state.moveToFrontBase[group] =
                                    rebuildIndex + 1;
                            }
                        }
                    }

                    int decodedByte =
                        state.symbolMap[movedSymbol & 0xff] & 0xff;

                    state.frequencyTable[decodedByte]++;

                    requireRange(
                        decodedBlockLength
                            < state.transformationTable.length,
                        "BZip2 block exceeds 100 KiB"
                    );

                    state.transformationTable[
                        decodedBlockLength++
                    ] = decodedByte;

                    if (groupRemaining == 0) {
                        groupIndex++;

                        requireRange(
                            groupIndex < selectorCount,
                            "BZip2 selector table ended early"
                        );

                        groupRemaining = 50;

                        byte tableIndex =
                            state.selector[groupIndex];

                        minimumLength =
                            state.minimumCodeLengths[tableIndex];
                        activeLimits =
                            state.codeLimits[tableIndex];
                        activePermutations =
                            state.codePermutations[tableIndex];
                        activeBases =
                            state.codeBases[tableIndex];
                    }

                    groupRemaining--;

                    int selectedCodeLength = minimumLength;
                    int selectedCode =
                        readBits(selectedCodeLength, state);

                    while (
                        selectedCode
                            > activeLimits[selectedCodeLength]
                    ) {
                        selectedCodeLength++;
                        selectedCode =
                            selectedCode << 1 | readBit(state);
                    }

                    nextSymbol = activePermutations[
                        selectedCode
                            - activeBases[selectedCodeLength]
                    ];
                }
            }

            /*
             * Build the LF-mapping needed to reverse the Burrows-Wheeler
             * transform, then expand BZip2's final run-length encoding.
             */
            state.outputRunLength = 0;
            state.outputRunByte = 0;
            state.cumulativeFrequency[0] = 0;

            System.arraycopy(
                state.frequencyTable,
                0,
                state.cumulativeFrequency,
                1,
                256
            );

            for (int index = 1; index <= 256; index++) {
                state.cumulativeFrequency[index] +=
                    state.cumulativeFrequency[index - 1];
            }

            for (
                int index = 0;
                index < decodedBlockLength;
                index++
            ) {
                int value =
                    state.transformationTable[index] & 0xff;

                state.transformationTable[
                    state.cumulativeFrequency[value]
                ] |= index << 8;

                state.cumulativeFrequency[value]++;
            }

            requireRange(
                state.originalPointer >= 0
                    && state.originalPointer
                        < decodedBlockLength,
                "Invalid BZip2 original pointer"
            );

            state.transformationPosition =
                state.transformationTable[
                    state.originalPointer
                ] >>> 8;

            state.usedBlockBytes = 0;

            state.transformationPosition =
                state.transformationTable[
                    state.transformationPosition
                ];

            state.currentByte =
                (byte) state.transformationPosition;

            state.transformationPosition >>>= 8;
            state.usedBlockBytes++;
            state.blockLength = decodedBlockLength;

            writeDecodedBlock(state);

            blockComplete =
                state.usedBlockBytes
                    == state.blockLength + 1
                    && state.outputRunLength == 0;
        }
    }

    private static int readUnsignedByte(Bzip2State state) {
        return readBits(8, state);
    }

    private static byte readBit(Bzip2State state) {
        return (byte) readBits(1, state);
    }

    private static int readBits(
        int bitCount,
        Bzip2State state
    ) {
        while (state.liveBits < bitCount) {
            requireRange(
                state.inputRemaining > 0,
                "Truncated BZip2 stream"
            );

            state.bitBuffer =
                state.bitBuffer << 8
                    | state.input[state.inputPosition] & 0xff;

            state.liveBits += 8;
            state.inputPosition++;
            state.inputRemaining--;
        }

        int bits =
            state.bitBuffer
                >> state.liveBits - bitCount
                & (1 << bitCount) - 1;

        state.liveBits -= bitCount;
        return bits;
    }

    private static void buildSymbolMap(
        Bzip2State state
    ) {
        state.usedSymbolCount = 0;

        for (int symbol = 0; symbol < 256; symbol++) {
            if (state.symbolInUse[symbol]) {
                state.symbolMap[state.usedSymbolCount++] =
                    (byte) symbol;
            }
        }
    }

    /**
     * Builds canonical Huffman decoder tables from a code-length table.
     */
    private static void buildHuffmanDecodeTables(
        int[] codeLimits,
        int[] codeBases,
        int[] codePermutations,
        byte[] codeLengths,
        int minimumLength,
        int maximumLength,
        int alphabetSize
    ) {
        int permutationIndex = 0;

        for (
            int length = minimumLength;
            length <= maximumLength;
            length++
        ) {
            for (int symbol = 0; symbol < alphabetSize; symbol++) {
                if (codeLengths[symbol] == length) {
                    codePermutations[permutationIndex++] = symbol;
                }
            }
        }

        for (int index = 0; index < 23; index++) {
            codeBases[index] = 0;
        }

        for (int symbol = 0; symbol < alphabetSize; symbol++) {
            codeBases[codeLengths[symbol] + 1]++;
        }

        for (int index = 1; index < 23; index++) {
            codeBases[index] += codeBases[index - 1];
        }

        for (int index = 0; index < 23; index++) {
            codeLimits[index] = 0;
        }

        int code = 0;

        for (
            int length = minimumLength;
            length <= maximumLength;
            length++
        ) {
            code +=
                codeBases[length + 1]
                    - codeBases[length];

            codeLimits[length] = code - 1;
            code <<= 1;
        }

        for (
            int length = minimumLength + 1;
            length <= maximumLength;
            length++
        ) {
            codeBases[length] =
                ((codeLimits[length - 1] + 1) << 1)
                    - codeBases[length];
        }
    }

    private static void requireRange(
        boolean condition,
        String message
    ) {
        if (!condition) {
            throw new IllegalArgumentException(message);
        }
    }
}
