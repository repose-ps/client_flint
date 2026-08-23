package rs2.chat;

import rs2.cache.Archive;
import rs2.net.Buffer;

/**
 * Revision-377 client-side chat censor.
 *
 * <p>
 * The filter tables are loaded from the word-encoding archive and then used to
 * mask bad words, domains, top-level domains, and IPv4-like addresses while
 * retaining the original client's exception and capitalization rules.
 * </p>
 */
public final class Censor {

	/**
	 * Creates a new Censor instance.
	 */
	private Censor() {
	}

	/**
	 * Loads the four revision-377 word-filter tables from the supplied archive.
	 *
	 * @param archive the archive
	 */
	public static void load(Archive archive) {
		Buffer fragments = new Buffer(archive.read("fragmentsenc.txt"));
		Buffer badWords = new Buffer(archive.read("badenc.txt"));
		Buffer domains = new Buffer(archive.read("domainenc.txt"));
		Buffer topLevelDomains = new Buffer(archive.read("tldlist.txt"));
		loadTables(fragments, badWords, domains, topLevelDomains);
	}

	/**
	 * Loads tables.
	 *
	 * @param fragments             the fragments
	 * @param badWordsBuffer        the bad words buffer
	 * @param domains               the domains
	 * @param topLevelDomainsBuffer the top level domains buffer
	 */
	private static void loadTables(Buffer fragments, Buffer badWordsBuffer, Buffer domains,
			Buffer topLevelDomainsBuffer) {
		readBadWords(badWordsBuffer);
		readDomainWords(domains);
		readFragmentHashes(fragments);
		readTopLevelDomains(topLevelDomainsBuffer);
	}

	/**
	 * Reads top level domains.
	 *
	 * @param buffer the buffer
	 */
	private static void readTopLevelDomains(Buffer buffer) {
		int count = buffer.readInt();
		topLevelDomains = new char[count][];
		topLevelDomainTypes = new int[count];
		for (int index = 0; index < count; index++) {
			topLevelDomainTypes[index] = buffer.readUnsignedByte();
			char domain[] = new char[buffer.readUnsignedByte()];
			for (int charIndex = 0; charIndex < domain.length; charIndex++)
				domain[charIndex] = (char) buffer.readUnsignedByte();

			topLevelDomains[index] = domain;
		}

	}

	/**
	 * Reads bad words.
	 *
	 * @param buffer the buffer
	 */
	private static void readBadWords(Buffer buffer) {
		int count = buffer.readInt();
		badWords = new char[count][];
		badWordContextPairs = new byte[count][][];
		readBadWordEntries(buffer, badWords, badWordContextPairs);
	}

	/**
	 * Reads domain words.
	 *
	 * @param buffer the buffer
	 */
	private static void readDomainWords(Buffer buffer) {
		int count = buffer.readInt();
		domainWords = new char[count][];
		readWordList(buffer, domainWords);
	}

	/**
	 * Reads fragment hashes.
	 *
	 * @param buffer the buffer
	 */
	private static void readFragmentHashes(Buffer buffer) {
		fragmentHashes = new int[buffer.readInt()];
		for (int index = 0; index < fragmentHashes.length; index++)
			fragmentHashes[index] = buffer.readUnsignedShort();
	}

	/**
	 * Reads bad word entries.
	 *
	 * @param buffer   the buffer
	 * @param words    the words
	 * @param contexts the contexts
	 */
	private static void readBadWordEntries(Buffer buffer, char words[][], byte contexts[][][]) {
		for (int wordIndex = 0; wordIndex < words.length; wordIndex++) {
			char word[] = new char[buffer.readUnsignedByte()];
			for (int charIndex = 0; charIndex < word.length; charIndex++)
				word[charIndex] = (char) buffer.readUnsignedByte();

			words[wordIndex] = word;
			byte contextPairs[][] = new byte[buffer.readUnsignedByte()][2];
			for (int pairIndex = 0; pairIndex < contextPairs.length; pairIndex++) {
				contextPairs[pairIndex][0] = (byte) buffer.readUnsignedByte();
				contextPairs[pairIndex][1] = (byte) buffer.readUnsignedByte();
			}

			if (contextPairs.length > 0)
				contexts[wordIndex] = contextPairs;
		}

	}

	/**
	 * Reads word list.
	 *
	 * @param buffer the buffer
	 * @param words  the words
	 */
	private static void readWordList(Buffer buffer, char words[][]) {
		for (int wordIndex = 0; wordIndex < words.length; wordIndex++) {
			char word[] = new char[buffer.readUnsignedByte()];
			for (int charIndex = 0; charIndex < word.length; charIndex++)
				word[charIndex] = (char) buffer.readUnsignedByte();

			words[wordIndex] = word;
		}

	}

	/**
	 * Performs the sanitize operation.
	 *
	 * @param text the text
	 */
	private static void sanitize(char text[]) {
		int writeIndex = 0;
		for (int readIndex = 0; readIndex < text.length; readIndex++) {
			if (isValidCharacter(text[readIndex]))
				text[writeIndex] = text[readIndex];
			else
				text[writeIndex] = ' ';
			if (writeIndex == 0 || text[writeIndex] != ' ' || text[writeIndex - 1] != ' ')
				writeIndex++;
		}

		for (int index = writeIndex; index < text.length; index++)
			text[index] = ' ';

	}

	/**
	 * Returns whether valid character.
	 *
	 * @param character the character
	 * @return whether the requested condition is satisfied
	 */
	private static boolean isValidCharacter(char character) {
		return character >= ' ' && character <= '\177' || character == ' ' || character == '\n' || character == '\t'
				|| character == '\243' || character == '\u20AC';
	}

	/**
	 * Returns the revision-377 censored representation of {@code text}.
	 *
	 * @param text the text
	 */
	public static String censor(String text) {
		char characters[] = text.toCharArray();
		sanitize(characters);
		String cleaned = (new String(characters)).trim();
		characters = cleaned.toLowerCase().toCharArray();
		String lowercaseOriginal = cleaned.toLowerCase();
		filterTopLevelDomains(characters);
		filterBadWords(characters);
		filterDomains(characters);
		filterIpAddresses(characters);
		for (int exceptionIndex = 0; exceptionIndex < EXCEPTIONS.length; exceptionIndex++) {
			for (int matchIndex = -1; (matchIndex = lowercaseOriginal.indexOf(EXCEPTIONS[exceptionIndex],
					matchIndex + 1)) != -1;) {
				char exception[] = EXCEPTIONS[exceptionIndex].toCharArray();
				for (int charIndex = 0; charIndex < exception.length; charIndex++)
					characters[charIndex + matchIndex] = exception[charIndex];

			}

		}

		restoreUppercase(characters, cleaned.toCharArray());
		normalizeCapitalization(characters);
		return (new String(characters)).trim();
	}

	/**
	 * Performs the restore uppercase operation.
	 *
	 * @param filtered the filtered
	 * @param original the original
	 */
	private static void restoreUppercase(char filtered[], char original[]) {
		for (int index = 0; index < original.length; index++)
			if (filtered[index] != '*' && isUppercaseLetter(original[index]))
				filtered[index] = original[index];

	}

	/**
	 * Performs the normalize capitalization operation.
	 *
	 * @param text the text
	 */
	private static void normalizeCapitalization(char text[]) {
		boolean uppercaseAllowed = true;
		for (int index = 0; index < text.length; index++) {
			char character = text[index];
			if (isLetter(character)) {
				if (uppercaseAllowed) {
					if (isLowercaseLetter(character))
						uppercaseAllowed = false;
				} else if (isUppercaseLetter(character))
					text[index] = (char) ((character + 97) - 65);
			} else {
				uppercaseAllowed = true;
			}
		}

	}

	/**
	 * Filters bad words.
	 *
	 * @param text the text
	 */
	private static void filterBadWords(char text[]) {
		for (int pass = 0; pass < 2; pass++) {
			for (int wordIndex = badWords.length - 1; wordIndex >= 0; wordIndex--)
				filterWord(badWordContextPairs[wordIndex], badWords[wordIndex], text);

		}

	}

	/**
	 * Filters domains.
	 *
	 * @param text the text
	 */
	private static void filterDomains(char text[]) {
		char atFiltered[] = text.clone();
		char atPattern[] = { '(', 'a', ')' };
		filterWord(null, atPattern, atFiltered);
		char dotFiltered[] = text.clone();
		char dotPattern[] = { 'd', 'o', 't' };
		filterWord(null, dotPattern, dotFiltered);
		for (int domainIndex = domainWords.length - 1; domainIndex >= 0; domainIndex--)
			filterDomain(text, dotFiltered, atFiltered, domainWords[domainIndex]);

	}

	/**
	 * Filters domain.
	 *
	 * @param text        the text
	 * @param dotFiltered the dot filtered
	 * @param atFiltered  the at filtered
	 * @param domain      the domain
	 */
	private static void filterDomain(char text[], char dotFiltered[], char atFiltered[], char domain[]) {
		if (domain.length > text.length)
			return;
		int scanStep;
		for (int startIndex = 0; startIndex <= text.length - domain.length; startIndex += scanStep) {
			int textIndex = startIndex;
			int domainIndex = 0;
			scanStep = 1;
			while (textIndex < text.length) {
				int matchLength = 0;
				char currentCharacter = text[textIndex];
				char nextCharacter = '\0';
				if (textIndex + 1 < text.length)
					nextCharacter = text[textIndex + 1];
				if (domainIndex < domain.length && (matchLength = matchDomainCharacter(currentCharacter,
						domain[domainIndex], nextCharacter)) > 0) {
					textIndex += matchLength;
					domainIndex++;
					continue;
				}
				if (domainIndex == 0)
					break;
				if ((matchLength = matchDomainCharacter(currentCharacter, domain[domainIndex - 1],
						nextCharacter)) > 0) {
					textIndex += matchLength;
					if (domainIndex == 1)
						scanStep++;
					continue;
				}
				if (domainIndex >= domain.length || !isSeparator(currentCharacter))
					break;
				textIndex++;
			}
			if (domainIndex >= domain.length) {
				boolean shouldFilter = false;
				int leftContext = getDomainLeftContext(text, atFiltered, startIndex);
				int rightContext = getDomainRightContext(dotFiltered, textIndex - 1, text);
				if (leftContext > 2 || rightContext > 2)
					shouldFilter = true;
				if (shouldFilter) {
					for (int filterIndex = startIndex; filterIndex < textIndex; filterIndex++)
						text[filterIndex] = '*';

				}
			}
		}

	}

	/**
	 * Returns domain left context.
	 *
	 * @param text       the text
	 * @param atFiltered the at filtered
	 * @param start      the start
	 * @return the domain left context
	 */
	private static int getDomainLeftContext(char text[], char atFiltered[], int start) {
		if (start == 0)
			return 2;
		for (int index = start - 1; index >= 0; index--) {
			if (!isSeparator(text[index]))
				break;
			if (text[index] == '@')
				return 3;
		}

		int maskedCount = 0;
		for (int index = start - 1; index >= 0; index--) {
			if (!isSeparator(atFiltered[index]))
				break;
			if (atFiltered[index] == '*')
				maskedCount++;
		}

		if (maskedCount >= 3)
			return 4;
		return !isSeparator(text[start - 1]) ? 0 : 1;
	}

	/**
	 * Returns domain right context.
	 *
	 * @param dotFiltered the dot filtered
	 * @param end         the end
	 * @param text        the text
	 * @return the domain right context
	 */
	private static int getDomainRightContext(char dotFiltered[], int end, char text[]) {
		if (end + 1 == text.length)
			return 2;
		for (int index = end + 1; index < text.length; index++) {
			if (!isSeparator(text[index]))
				break;
			if (text[index] == '.' || text[index] == ',')
				return 3;
		}

		int maskedCount = 0;
		for (int index = end + 1; index < text.length; index++) {
			if (!isSeparator(dotFiltered[index]))
				break;
			if (dotFiltered[index] == '*')
				maskedCount++;
		}

		if (maskedCount >= 3)
			return 4;
		return !isSeparator(text[end + 1]) ? 0 : 1;
	}

	/**
	 * Filters top level domains.
	 *
	 * @param text the text
	 */
	private static void filterTopLevelDomains(char text[]) {
		char dotFiltered[] = text.clone();
		char dotPattern[] = { 'd', 'o', 't' };
		filterWord(null, dotPattern, dotFiltered);
		char slashFiltered[] = text.clone();
		char slashPattern[] = { 's', 'l', 'a', 's', 'h' };
		filterWord(null, slashPattern, slashFiltered);
		for (int tldIndex = 0; tldIndex < topLevelDomains.length; tldIndex++)
			filterTopLevelDomain(text, dotFiltered, topLevelDomainTypes[tldIndex], topLevelDomains[tldIndex],
					slashFiltered);

	}

	/**
	 * Filters top level domain.
	 *
	 * @param text          the text
	 * @param dotFiltered   the dot filtered
	 * @param type          the type
	 * @param tld           the tld
	 * @param slashFiltered the slash filtered
	 */
	private static void filterTopLevelDomain(char text[], char dotFiltered[], int type, char tld[],
			char slashFiltered[]) {
		if (tld.length > text.length)
			return;
		int scanStep;
		for (int startIndex = 0; startIndex <= text.length - tld.length; startIndex += scanStep) {
			int textIndex = startIndex;
			int tldIndex = 0;
			scanStep = 1;
			while (textIndex < text.length) {
				int matchLength = 0;
				char currentCharacter = text[textIndex];
				char nextCharacter = '\0';
				if (textIndex + 1 < text.length)
					nextCharacter = text[textIndex + 1];
				if (tldIndex < tld.length
						&& (matchLength = matchDomainCharacter(currentCharacter, tld[tldIndex], nextCharacter)) > 0) {
					textIndex += matchLength;
					tldIndex++;
					continue;
				}
				if (tldIndex == 0)
					break;
				if ((matchLength = matchDomainCharacter(currentCharacter, tld[tldIndex - 1], nextCharacter)) > 0) {
					textIndex += matchLength;
					if (tldIndex == 1)
						scanStep++;
					continue;
				}
				if (tldIndex >= tld.length || !isSeparator(currentCharacter))
					break;
				textIndex++;
			}
			if (tldIndex >= tld.length) {
				boolean shouldFilter = false;
				int leftContext = getTldLeftContext(dotFiltered, startIndex, text);
				int rightContext = getTldRightContext(slashFiltered, textIndex - 1, text);
				if (type == 1 && leftContext > 0 && rightContext > 0)
					shouldFilter = true;
				if (type == 2 && (leftContext > 2 && rightContext > 0 || leftContext > 0 && rightContext > 2))
					shouldFilter = true;
				if (type == 3 && leftContext > 0 && rightContext > 2)
					shouldFilter = true;
				if (shouldFilter) {
					int filterStart = startIndex;
					int filterEnd = textIndex - 1;
					if (leftContext > 2) {
						if (leftContext == 4) {
							boolean foundLeftMask = false;
							for (int index = filterStart - 1; index >= 0; index--)
								if (foundLeftMask) {
									if (dotFiltered[index] != '*')
										break;
									filterStart = index;
								} else if (dotFiltered[index] == '*') {
									filterStart = index;
									foundLeftMask = true;
								}

						}
						boolean foundLeftText = false;
						for (int index = filterStart - 1; index >= 0; index--)
							if (foundLeftText) {
								if (isSeparator(text[index]))
									break;
								filterStart = index;
							} else if (!isSeparator(text[index])) {
								foundLeftText = true;
								filterStart = index;
							}

					}
					if (rightContext > 2) {
						if (rightContext == 4) {
							boolean foundRightMask = false;
							for (int index = filterEnd + 1; index < text.length; index++)
								if (foundRightMask) {
									if (slashFiltered[index] != '*')
										break;
									filterEnd = index;
								} else if (slashFiltered[index] == '*') {
									filterEnd = index;
									foundRightMask = true;
								}

						}
						boolean foundRightText = false;
						for (int index = filterEnd + 1; index < text.length; index++)
							if (foundRightText) {
								if (isSeparator(text[index]))
									break;
								filterEnd = index;
							} else if (!isSeparator(text[index])) {
								foundRightText = true;
								filterEnd = index;
							}

					}
					for (int filterIndex = filterStart; filterIndex <= filterEnd; filterIndex++)
						text[filterIndex] = '*';

				}
			}
		}

	}

	/**
	 * Returns tld left context.
	 *
	 * @param dotFiltered the dot filtered
	 * @param start       the start
	 * @param text        the text
	 * @return the tld left context
	 */
	private static int getTldLeftContext(char dotFiltered[], int start, char text[]) {
		if (start == 0)
			return 2;
		for (int index = start - 1; index >= 0; index--) {
			if (!isSeparator(text[index]))
				break;
			if (text[index] == ',' || text[index] == '.')
				return 3;
		}

		int maskedCount = 0;
		for (int index = start - 1; index >= 0; index--) {
			if (!isSeparator(dotFiltered[index]))
				break;
			if (dotFiltered[index] == '*')
				maskedCount++;
		}

		if (maskedCount >= 3)
			return 4;
		return !isSeparator(text[start - 1]) ? 0 : 1;
	}

	/**
	 * Returns tld right context.
	 *
	 * @param slashFiltered the slash filtered
	 * @param end           the end
	 * @param text          the text
	 * @return the tld right context
	 */
	private static int getTldRightContext(char slashFiltered[], int end, char text[]) {
		if (end + 1 == text.length)
			return 2;
		for (int index = end + 1; index < text.length; index++) {
			if (!isSeparator(text[index]))
				break;
			if (text[index] == '\\' || text[index] == '/')
				return 3;
		}

		int maskedCount = 0;
		for (int index = end + 1; index < text.length; index++) {
			if (!isSeparator(slashFiltered[index]))
				break;
			if (slashFiltered[index] == '*')
				maskedCount++;
		}

		if (maskedCount >= 5)
			return 4;
		return !isSeparator(text[end + 1]) ? 0 : 1;
	}

	/**
	 * Filters word.
	 *
	 * @param contextPairs the context pairs
	 * @param word         the word
	 * @param text         the text
	 */
	private static void filterWord(byte contextPairs[][], char word[], char text[]) {
		if (word.length > text.length)
			return;
		int scanStep;
		for (int startIndex = 0; startIndex <= text.length - word.length; startIndex += scanStep) {
			int textIndex = startIndex;
			int wordIndex = 0;
			int skippedCharacterCount = 0;
			scanStep = 1;
			boolean containsSeparator = false;
			boolean matchedDigitSubstitution = false;
			boolean skippedDigit = false;
			while (textIndex < text.length && (!matchedDigitSubstitution || !skippedDigit)) {
				int matchLength = 0;
				char currentCharacter = text[textIndex];
				char nextCharacter = '\0';
				if (textIndex + 1 < text.length)
					nextCharacter = text[textIndex + 1];
				if (wordIndex < word.length && (matchLength = matchBadWordCharacter(word[wordIndex], currentCharacter,
						nextCharacter)) > 0) {
					if (matchLength == 1 && isDigit(currentCharacter))
						matchedDigitSubstitution = true;
					if (matchLength == 2 && (isDigit(currentCharacter) || isDigit(nextCharacter)))
						matchedDigitSubstitution = true;
					textIndex += matchLength;
					wordIndex++;
					continue;
				}
				if (wordIndex == 0)
					break;
				if ((matchLength = matchBadWordCharacter(word[wordIndex - 1], currentCharacter, nextCharacter)) > 0) {
					textIndex += matchLength;
					if (wordIndex == 1)
						scanStep++;
					continue;
				}
				if (wordIndex >= word.length || !isSkippableCharacter(currentCharacter))
					break;
				if (isSeparator(currentCharacter) && currentCharacter != '\'')
					containsSeparator = true;
				if (isDigit(currentCharacter))
					skippedDigit = true;
				textIndex++;
				if ((++skippedCharacterCount * 100) / (textIndex - startIndex) > 90)
					break;
			}
			if (wordIndex >= word.length && (!matchedDigitSubstitution || !skippedDigit)) {
				boolean shouldFilter = true;
				if (!containsSeparator) {
					char leftCharacter = ' ';
					if (startIndex - 1 >= 0)
						leftCharacter = text[startIndex - 1];
					char rightCharacter = ' ';
					if (textIndex < text.length)
						rightCharacter = text[textIndex];
					byte leftContextCode = encodeContextCharacter(leftCharacter);
					byte rightContextCode = encodeContextCharacter(rightCharacter);
					if (contextPairs != null && containsContextPair(rightContextCode, contextPairs, leftContextCode))
						shouldFilter = false;
				} else {
					boolean leftBoundary = false;
					boolean rightBoundary = false;
					if (startIndex - 1 < 0 || isSeparator(text[startIndex - 1]) && text[startIndex - 1] != '\'')
						leftBoundary = true;
					if (textIndex >= text.length || isSeparator(text[textIndex]) && text[textIndex] != '\'')
						rightBoundary = true;
					if (!leftBoundary || !rightBoundary) {
						boolean disallowedFragmentFound = false;
						int fragmentStart = startIndex - 2;
						if (leftBoundary)
							fragmentStart = startIndex;
						for (; !disallowedFragmentFound && fragmentStart < textIndex; fragmentStart++)
							if (fragmentStart >= 0
									&& (!isSeparator(text[fragmentStart]) || text[fragmentStart] == '\'')) {
								char fragment[] = new char[3];
								int fragmentLength;
								for (fragmentLength = 0; fragmentLength < 3; fragmentLength++) {
									if (fragmentStart + fragmentLength >= text.length
											|| isSeparator(text[fragmentStart + fragmentLength])
													&& text[fragmentStart + fragmentLength] != '\'')
										break;
									fragment[fragmentLength] = text[fragmentStart + fragmentLength];
								}

								boolean completeFragment = true;
								if (fragmentLength == 0)
									completeFragment = false;
								if (fragmentLength < 3 && fragmentStart - 1 >= 0
										&& (!isSeparator(text[fragmentStart - 1]) || text[fragmentStart - 1] == '\''))
									completeFragment = false;
								if (completeFragment && !isAllowedFragment(fragment))
									disallowedFragmentFound = true;
							}

						if (!disallowedFragmentFound)
							shouldFilter = false;
					}
				}
				if (shouldFilter) {
					int digitCount = 0;
					int letterCount = 0;
					int lastLetterIndex = -1;
					for (int scanIndex = startIndex; scanIndex < textIndex; scanIndex++)
						if (isDigit(text[scanIndex]))
							digitCount++;
						else if (isLetter(text[scanIndex])) {
							letterCount++;
							lastLetterIndex = scanIndex;
						}

					if (lastLetterIndex > -1)
						digitCount -= textIndex - 1 - lastLetterIndex;
					if (digitCount <= letterCount) {
						for (int filterIndex = startIndex; filterIndex < textIndex; filterIndex++)
							text[filterIndex] = '*';

					} else {
						scanStep = 1;
					}
				}
			}
		}

	}

	/**
	 * Performs the contains context pair operation.
	 *
	 * @param right the right
	 * @param pairs the pairs
	 * @param left  the left
	 * @return whether the operation succeeds
	 */
	private static boolean containsContextPair(byte right, byte pairs[][], byte left) {
		int lowerBound = 0;
		if (pairs[lowerBound][0] == left && pairs[lowerBound][1] == right)
			return true;
		int upperBound = pairs.length - 1;
		if (pairs[upperBound][0] == left && pairs[upperBound][1] == right)
			return true;
		do {
			int midpoint = (lowerBound + upperBound) / 2;
			if (pairs[midpoint][0] == left && pairs[midpoint][1] == right)
				return true;
			if (left < pairs[midpoint][0] || left == pairs[midpoint][0] && right < pairs[midpoint][1])
				upperBound = midpoint;
			else
				lowerBound = midpoint;
		} while (lowerBound != upperBound && lowerBound + 1 != upperBound);
		return false;
	}

	/**
	 * Performs the match domain character operation.
	 *
	 * @param current  the current
	 * @param expected the expected
	 * @param next     the next
	 * @return the resulting value
	 */
	private static int matchDomainCharacter(char current, char expected, char next) {
		if (expected == current)
			return 1;
		if (expected == 'o' && current == '0')
			return 1;
		if (expected == 'o' && current == '(' && next == ')')
			return 2;
		if (expected == 'c' && (current == '(' || current == '<' || current == '['))
			return 1;
		if (expected == 'e' && current == '\u20AC')
			return 1;
		if (expected == 's' && current == '$')
			return 1;
		return expected != 'l' || current != 'i' ? 0 : 1;
	}

	/**
	 * Performs the match bad word character operation.
	 *
	 * @param expected the expected
	 * @param current  the current
	 * @param next     the next
	 * @return the resulting value
	 */
	private static int matchBadWordCharacter(char expected, char current, char next) {
		if (expected == current)
			return 1;
		if (expected >= 'a' && expected <= 'm') {
			if (expected == 'a') {
				if (current == '4' || current == '@' || current == '^')
					return 1;
				return current != '/' || next != '\\' ? 0 : 2;
			}
			if (expected == 'b') {
				if (current == '6' || current == '8')
					return 1;
				return (current != '1' || next != '3') && (current != 'i' || next != '3') ? 0 : 2;
			}
			if (expected == 'c')
				return current != '(' && current != '<' && current != '{' && current != '[' ? 0 : 1;
			if (expected == 'd')
				return (current != '[' || next != ')') && (current != 'i' || next != ')') ? 0 : 2;
			if (expected == 'e')
				return current != '3' && current != '\u20AC' ? 0 : 1;
			if (expected == 'f') {
				if (current == 'p' && next == 'h')
					return 2;
				return current != '\243' ? 0 : 1;
			}
			if (expected == 'g')
				return current != '9' && current != '6' && current != 'q' ? 0 : 1;
			if (expected == 'h')
				return current != '#' ? 0 : 1;
			if (expected == 'i')
				return current != 'y' && current != 'l' && current != 'j' && current != '1' && current != '!'
						&& current != ':' && current != ';' && current != '|' ? 0 : 1;
			if (expected == 'j')
				return 0;
			if (expected == 'k')
				return 0;
			if (expected == 'l')
				return current != '1' && current != '|' && current != 'i' ? 0 : 1;
			if (expected == 'm')
				return 0;
		}
		if (expected >= 'n' && expected <= 'z') {
			if (expected == 'n')
				return 0;
			if (expected == 'o') {
				if (current == '0' || current == '*')
					return 1;
				return (current != '(' || next != ')') && (current != '[' || next != ']')
						&& (current != '{' || next != '}') && (current != '<' || next != '>') ? 0 : 2;
			}
			if (expected == 'p')
				return 0;
			if (expected == 'q')
				return 0;
			if (expected == 'r')
				return 0;
			if (expected == 's')
				return current != '5' && current != 'z' && current != '$' && current != '2' ? 0 : 1;
			if (expected == 't')
				return current != '7' && current != '+' ? 0 : 1;
			if (expected == 'u') {
				if (current == 'v')
					return 1;
				return (current != '\\' || next != '/') && (current != '\\' || next != '|')
						&& (current != '|' || next != '/') ? 0 : 2;
			}
			if (expected == 'v')
				return (current != '\\' || next != '/') && (current != '\\' || next != '|')
						&& (current != '|' || next != '/') ? 0 : 2;
			if (expected == 'w')
				return current != 'v' || next != 'v' ? 0 : 2;
			if (expected == 'x')
				return (current != ')' || next != '(') && (current != '}' || next != '{')
						&& (current != ']' || next != '[') && (current != '>' || next != '<') ? 0 : 2;
			if (expected == 'y')
				return 0;
			if (expected == 'z')
				return 0;
		}
		if (expected >= '0' && expected <= '9') {
			if (expected == '0') {
				if (current == 'o' || current == 'O')
					return 1;
				return (current != '(' || next != ')') && (current != '{' || next != '}')
						&& (current != '[' || next != ']') ? 0 : 2;
			}
			if (expected == '1')
				return current != 'l' ? 0 : 1;
			else
				return 0;
		}
		if (expected == ',')
			return current != '.' ? 0 : 1;
		if (expected == '.')
			return current != ',' ? 0 : 1;
		if (expected == '!')
			return current != 'i' ? 0 : 1;
		else
			return 0;
	}

	/**
	 * Encodes context character.
	 *
	 * @param character the character
	 * @return the resulting value
	 */
	private static byte encodeContextCharacter(char character) {
		if (character >= 'a' && character <= 'z')
			return (byte) ((character - 97) + 1);
		if (character == '\'')
			return 28;
		if (character >= '0' && character <= '9')
			return (byte) ((character - 48) + 29);
		else
			return 27;
	}

	/**
	 * Filters ip addresses.
	 *
	 * @param text the text
	 */
	private static void filterIpAddresses(char text[]) {
		int digitStart = 0;
		int scanPosition = 0;
		int octetCount = 0;
		int addressStart = 0;
		while ((digitStart = findFirstDigit(scanPosition, text)) != -1) {
			boolean hasInterveningText = false;
			for (int index = scanPosition; index >= 0 && index < digitStart && !hasInterveningText; index++)
				if (!isSeparator(text[index]) && !isSkippableCharacter(text[index]))
					hasInterveningText = true;

			if (hasInterveningText)
				octetCount = 0;
			if (octetCount == 0)
				addressStart = digitStart;
			scanPosition = findFirstNonDigit(digitStart, text);
			int octetValue = 0;
			for (int index = digitStart; index < scanPosition; index++)
				octetValue = (octetValue * 10 + text[index]) - 48;

			if (octetValue > 255 || scanPosition - digitStart > 8)
				octetCount = 0;
			else
				octetCount++;
			if (octetCount == 4) {
				for (int filterIndex = addressStart; filterIndex < scanPosition; filterIndex++)
					text[filterIndex] = '*';

				octetCount = 0;
			}
		}
	}

	/**
	 * Finds first digit.
	 *
	 * @param start the start
	 * @param text  the text
	 * @return the matching position or value
	 */
	private static int findFirstDigit(int start, char text[]) {
		for (int index = start; index < text.length && index >= 0; index++)
			if (text[index] >= '0' && text[index] <= '9')
				return index;

		return -1;
	}

	/**
	 * Finds first non digit.
	 *
	 * @param start the start
	 * @param text  the text
	 * @return the matching position or value
	 */
	private static int findFirstNonDigit(int start, char text[]) {
		for (int index = start; index < text.length && index >= 0; index++)
			if (text[index] < '0' || text[index] > '9')
				return index;

		return text.length;
	}

	/**
	 * Returns whether separator.
	 *
	 * @param character the character
	 * @return whether the requested condition is satisfied
	 */
	private static boolean isSeparator(char character) {
		return !isLetter(character) && !isDigit(character);
	}

	/**
	 * Returns whether skippable character.
	 *
	 * @param character the character
	 * @return whether the requested condition is satisfied
	 */
	private static boolean isSkippableCharacter(char character) {
		if (character < 'a' || character > 'z')
			return true;
		return character == 'v' || character == 'x' || character == 'j' || character == 'q' || character == 'z';
	}

	/**
	 * Returns whether letter.
	 *
	 * @param character the character
	 * @return whether the requested condition is satisfied
	 */
	private static boolean isLetter(char character) {
		return character >= 'a' && character <= 'z' || character >= 'A' && character <= 'Z';
	}

	/**
	 * Returns whether digit.
	 *
	 * @param character the character
	 * @return whether the requested condition is satisfied
	 */
	private static boolean isDigit(char character) {
		return character >= '0' && character <= '9';
	}

	/**
	 * Returns whether lowercase letter.
	 *
	 * @param character the character
	 * @return whether the requested condition is satisfied
	 */
	private static boolean isLowercaseLetter(char character) {
		return character >= 'a' && character <= 'z';
	}

	/**
	 * Returns whether uppercase letter.
	 *
	 * @param character the character
	 * @return whether the requested condition is satisfied
	 */
	private static boolean isUppercaseLetter(char character) {
		return character >= 'A' && character <= 'Z';
	}

	/**
	 * Returns whether allowed fragment.
	 *
	 * @param fragment the fragment
	 * @return whether the requested condition is satisfied
	 */
	private static boolean isAllowedFragment(char fragment[]) {
		boolean allDigits = true;
		for (int index = 0; index < fragment.length; index++)
			if (!isDigit(fragment[index]) && fragment[index] != 0)
				allDigits = false;

		if (allDigits)
			return true;
		int encodedFragment = encodeFragment(fragment);
		int lowerBound = 0;
		int upperBound = fragmentHashes.length - 1;
		if (encodedFragment == fragmentHashes[lowerBound] || encodedFragment == fragmentHashes[upperBound])
			return true;
		do {
			int midpoint = (lowerBound + upperBound) / 2;
			if (encodedFragment == fragmentHashes[midpoint])
				return true;
			if (encodedFragment < fragmentHashes[midpoint])
				upperBound = midpoint;
			else
				lowerBound = midpoint;
		} while (lowerBound != upperBound && lowerBound + 1 != upperBound);
		return false;
	}

	/**
	 * Encodes fragment.
	 *
	 * @param fragment the fragment
	 * @return the resulting value
	 */
	private static int encodeFragment(char fragment[]) {
		if (fragment.length > 6)
			return 0;
		int encodedValue = 0;
		for (int index = 0; index < fragment.length; index++) {
			char expected = fragment[fragment.length - index - 1];
			if (expected >= 'a' && expected <= 'z')
				encodedValue = encodedValue * 38 + ((expected - 97) + 1);
			else if (expected == '\'')
				encodedValue = encodedValue * 38 + 27;
			else if (expected >= '0' && expected <= '9')
				encodedValue = encodedValue * 38 + ((expected - 48) + 28);
			else if (expected != 0)
				return 0;
		}

		return encodedValue;
	}

	private static int[] fragmentHashes;

	private static char[][] badWords;

	private static byte[][][] badWordContextPairs;

	private static char[][] domainWords;

	private static char[][] topLevelDomains;

	private static int[] topLevelDomainTypes;
	/** Defines the exceptions constant. */
	private static final String[] EXCEPTIONS = { "cook", "cook's", "cooks", "seeks", "sheet", "woop", "woops", "faq",
			"noob", "noobs" };

}
