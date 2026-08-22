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

	private Censor() {
	}

	/** Loads the four revision-377 word-filter tables from the supplied archive. */
	public static void load(Archive archive) {
		Buffer fragments = new Buffer(archive.read("fragmentsenc.txt"));
		Buffer badWords = new Buffer(archive.read("badenc.txt"));
		Buffer domains = new Buffer(archive.read("domainenc.txt"));
		Buffer topLevelDomains = new Buffer(archive.read("tldlist.txt"));
		loadTables(fragments, badWords, domains, topLevelDomains);
	}

	private static void loadTables(Buffer fragments, Buffer badWordsBuffer, Buffer domains,
			Buffer topLevelDomainsBuffer) {
		readBadWords(badWordsBuffer);
		readDomainWords(domains);
		readFragmentHashes(fragments);
		readTopLevelDomains(topLevelDomainsBuffer);
	}

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

	private static void readBadWords(Buffer buffer) {
		int count = buffer.readInt();
		badWords = new char[count][];
		badWordContextPairs = new byte[count][][];
		readBadWordEntries(buffer, badWords, badWordContextPairs);
	}

	private static void readDomainWords(Buffer buffer) {
		int count = buffer.readInt();
		domainWords = new char[count][];
		readWordList(buffer, domainWords);
	}

	private static void readFragmentHashes(Buffer buffer) {
		fragmentHashes = new int[buffer.readInt()];
		for (int index = 0; index < fragmentHashes.length; index++)
			fragmentHashes[index] = buffer.readUnsignedShort();
	}

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

	private static void readWordList(Buffer buffer, char words[][]) {
		for (int wordIndex = 0; wordIndex < words.length; wordIndex++) {
			char word[] = new char[buffer.readUnsignedByte()];
			for (int charIndex = 0; charIndex < word.length; charIndex++)
				word[charIndex] = (char) buffer.readUnsignedByte();

			words[wordIndex] = word;
		}

	}

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

	private static boolean isValidCharacter(char character) {
		return character >= ' ' && character <= '\177' || character == ' ' || character == '\n' || character == '\t'
				|| character == '\243' || character == '\u20AC';
	}

	/** Returns the revision-377 censored representation of {@code text}. */
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

	private static void restoreUppercase(char filtered[], char original[]) {
		for (int index = 0; index < original.length; index++)
			if (filtered[index] != '*' && isUppercaseLetter(original[index]))
				filtered[index] = original[index];

	}

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

	private static void filterBadWords(char text[]) {
		for (int pass = 0; pass < 2; pass++) {
			for (int wordIndex = badWords.length - 1; wordIndex >= 0; wordIndex--)
				filterWord(badWordContextPairs[wordIndex], badWords[wordIndex], text);

		}

	}

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

	private static void filterDomain(char text[], char dotFiltered[], char atFiltered[], char domain[]) {
		if (domain.length > text.length)
			return;
		int j;
		for (int k = 0; k <= text.length - domain.length; k += j) {
			int l = k;
			int i1 = 0;
			j = 1;
			while (l < text.length) {
				int j1 = 0;
				char c = text[l];
				char c1 = '\0';
				if (l + 1 < text.length)
					c1 = text[l + 1];
				if (i1 < domain.length && (j1 = matchDomainCharacter(c, domain[i1], c1)) > 0) {
					l += j1;
					i1++;
					continue;
				}
				if (i1 == 0)
					break;
				if ((j1 = matchDomainCharacter(c, domain[i1 - 1], c1)) > 0) {
					l += j1;
					if (i1 == 1)
						j++;
					continue;
				}
				if (i1 >= domain.length || !isSeparator(c))
					break;
				l++;
			}
			if (i1 >= domain.length) {
				boolean flag1 = false;
				int k1 = getDomainLeftContext(text, atFiltered, k);
				int l1 = getDomainRightContext(dotFiltered, l - 1, text);
				if (k1 > 2 || l1 > 2)
					flag1 = true;
				if (flag1) {
					for (int i2 = k; i2 < l; i2++)
						text[i2] = '*';

				}
			}
		}

	}

	private static int getDomainLeftContext(char text[], char atFiltered[], int start) {
		if (start == 0)
			return 2;
		for (int j = start - 1; j >= 0; j--) {
			if (!isSeparator(text[j]))
				break;
			if (text[j] == '@')
				return 3;
		}

		int k = 0;
		for (int l = start - 1; l >= 0; l--) {
			if (!isSeparator(atFiltered[l]))
				break;
			if (atFiltered[l] == '*')
				k++;
		}

		if (k >= 3)
			return 4;
		return !isSeparator(text[start - 1]) ? 0 : 1;
	}

	private static int getDomainRightContext(char dotFiltered[], int end, char text[]) {
		if (end + 1 == text.length)
			return 2;
		for (int k = end + 1; k < text.length; k++) {
			if (!isSeparator(text[k]))
				break;
			if (text[k] == '.' || text[k] == ',')
				return 3;
		}

		int l = 0;
		for (int i1 = end + 1; i1 < text.length; i1++) {
			if (!isSeparator(dotFiltered[i1]))
				break;
			if (dotFiltered[i1] == '*')
				l++;
		}

		if (l >= 3)
			return 4;
		return !isSeparator(text[end + 1]) ? 0 : 1;
	}

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

	private static void filterTopLevelDomain(char text[], char dotFiltered[], int type, char tld[],
			char slashFiltered[]) {
		if (tld.length > text.length)
			return;
		int j;
		for (int k = 0; k <= text.length - tld.length; k += j) {
			int l = k;
			int i1 = 0;
			j = 1;
			while (l < text.length) {
				int j1 = 0;
				char c = text[l];
				char c1 = '\0';
				if (l + 1 < text.length)
					c1 = text[l + 1];
				if (i1 < tld.length && (j1 = matchDomainCharacter(c, tld[i1], c1)) > 0) {
					l += j1;
					i1++;
					continue;
				}
				if (i1 == 0)
					break;
				if ((j1 = matchDomainCharacter(c, tld[i1 - 1], c1)) > 0) {
					l += j1;
					if (i1 == 1)
						j++;
					continue;
				}
				if (i1 >= tld.length || !isSeparator(c))
					break;
				l++;
			}
			if (i1 >= tld.length) {
				boolean flag1 = false;
				int k1 = getTldLeftContext(dotFiltered, k, text);
				int l1 = getTldRightContext(slashFiltered, l - 1, text);
				if (type == 1 && k1 > 0 && l1 > 0)
					flag1 = true;
				if (type == 2 && (k1 > 2 && l1 > 0 || k1 > 0 && l1 > 2))
					flag1 = true;
				if (type == 3 && k1 > 0 && l1 > 2)
					flag1 = true;
				if (flag1) {
					int i2 = k;
					int j2 = l - 1;
					if (k1 > 2) {
						if (k1 == 4) {
							boolean flag2 = false;
							for (int l2 = i2 - 1; l2 >= 0; l2--)
								if (flag2) {
									if (dotFiltered[l2] != '*')
										break;
									i2 = l2;
								} else if (dotFiltered[l2] == '*') {
									i2 = l2;
									flag2 = true;
								}

						}
						boolean flag3 = false;
						for (int i3 = i2 - 1; i3 >= 0; i3--)
							if (flag3) {
								if (isSeparator(text[i3]))
									break;
								i2 = i3;
							} else if (!isSeparator(text[i3])) {
								flag3 = true;
								i2 = i3;
							}

					}
					if (l1 > 2) {
						if (l1 == 4) {
							boolean flag4 = false;
							for (int j3 = j2 + 1; j3 < text.length; j3++)
								if (flag4) {
									if (slashFiltered[j3] != '*')
										break;
									j2 = j3;
								} else if (slashFiltered[j3] == '*') {
									j2 = j3;
									flag4 = true;
								}

						}
						boolean flag5 = false;
						for (int k3 = j2 + 1; k3 < text.length; k3++)
							if (flag5) {
								if (isSeparator(text[k3]))
									break;
								j2 = k3;
							} else if (!isSeparator(text[k3])) {
								flag5 = true;
								j2 = k3;
							}

					}
					for (int k2 = i2; k2 <= j2; k2++)
						text[k2] = '*';

				}
			}
		}

	}

	private static int getTldLeftContext(char dotFiltered[], int start, char text[]) {
		if (start == 0)
			return 2;
		for (int k = start - 1; k >= 0; k--) {
			if (!isSeparator(text[k]))
				break;
			if (text[k] == ',' || text[k] == '.')
				return 3;
		}

		int l = 0;
		for (int i1 = start - 1; i1 >= 0; i1--) {
			if (!isSeparator(dotFiltered[i1]))
				break;
			if (dotFiltered[i1] == '*')
				l++;
		}

		if (l >= 3)
			return 4;
		return !isSeparator(text[start - 1]) ? 0 : 1;
	}

	private static int getTldRightContext(char slashFiltered[], int end, char text[]) {
		if (end + 1 == text.length)
			return 2;
		for (int l = end + 1; l < text.length; l++) {
			if (!isSeparator(text[l]))
				break;
			if (text[l] == '\\' || text[l] == '/')
				return 3;
		}

		int i1 = 0;
		for (int j1 = end + 1; j1 < text.length; j1++) {
			if (!isSeparator(slashFiltered[j1]))
				break;
			if (slashFiltered[j1] == '*')
				i1++;
		}

		if (i1 >= 5)
			return 4;
		return !isSeparator(text[end + 1]) ? 0 : 1;
	}

	private static void filterWord(byte contextPairs[][], char word[], char text[]) {
		if (word.length > text.length)
			return;
		int j;
		for (int k = 0; k <= text.length - word.length; k += j) {
			int l = k;
			int i1 = 0;
			int j1 = 0;
			j = 1;
			boolean flag1 = false;
			boolean flag2 = false;
			boolean flag3 = false;
			while (l < text.length && (!flag2 || !flag3)) {
				int k1 = 0;
				char c = text[l];
				char c2 = '\0';
				if (l + 1 < text.length)
					c2 = text[l + 1];
				if (i1 < word.length && (k1 = matchBadWordCharacter(word[i1], c, c2)) > 0) {
					if (k1 == 1 && isDigit(c))
						flag2 = true;
					if (k1 == 2 && (isDigit(c) || isDigit(c2)))
						flag2 = true;
					l += k1;
					i1++;
					continue;
				}
				if (i1 == 0)
					break;
				if ((k1 = matchBadWordCharacter(word[i1 - 1], c, c2)) > 0) {
					l += k1;
					if (i1 == 1)
						j++;
					continue;
				}
				if (i1 >= word.length || !isSkippableCharacter(c))
					break;
				if (isSeparator(c) && c != '\'')
					flag1 = true;
				if (isDigit(c))
					flag3 = true;
				l++;
				if ((++j1 * 100) / (l - k) > 90)
					break;
			}
			if (i1 >= word.length && (!flag2 || !flag3)) {
				boolean flag4 = true;
				if (!flag1) {
					char c1 = ' ';
					if (k - 1 >= 0)
						c1 = text[k - 1];
					char c3 = ' ';
					if (l < text.length)
						c3 = text[l];
					byte byte0 = encodeContextCharacter(c1);
					byte byte1 = encodeContextCharacter(c3);
					if (contextPairs != null && containsContextPair(byte1, contextPairs, byte0))
						flag4 = false;
				} else {
					boolean flag5 = false;
					boolean flag6 = false;
					if (k - 1 < 0 || isSeparator(text[k - 1]) && text[k - 1] != '\'')
						flag5 = true;
					if (l >= text.length || isSeparator(text[l]) && text[l] != '\'')
						flag6 = true;
					if (!flag5 || !flag6) {
						boolean flag7 = false;
						int k2 = k - 2;
						if (flag5)
							k2 = k;
						for (; !flag7 && k2 < l; k2++)
							if (k2 >= 0 && (!isSeparator(text[k2]) || text[k2] == '\'')) {
								char ac2[] = new char[3];
								int j3;
								for (j3 = 0; j3 < 3; j3++) {
									if (k2 + j3 >= text.length || isSeparator(text[k2 + j3]) && text[k2 + j3] != '\'')
										break;
									ac2[j3] = text[k2 + j3];
								}

								boolean flag8 = true;
								if (j3 == 0)
									flag8 = false;
								if (j3 < 3 && k2 - 1 >= 0 && (!isSeparator(text[k2 - 1]) || text[k2 - 1] == '\''))
									flag8 = false;
								if (flag8 && !isAllowedFragment(ac2))
									flag7 = true;
							}

						if (!flag7)
							flag4 = false;
					}
				}
				if (flag4) {
					int l1 = 0;
					int i2 = 0;
					int j2 = -1;
					for (int l2 = k; l2 < l; l2++)
						if (isDigit(text[l2]))
							l1++;
						else if (isLetter(text[l2])) {
							i2++;
							j2 = l2;
						}

					if (j2 > -1)
						l1 -= l - 1 - j2;
					if (l1 <= i2) {
						for (int i3 = k; i3 < l; i3++)
							text[i3] = '*';

					} else {
						j = 1;
					}
				}
			}
		}

	}

	private static boolean containsContextPair(byte right, byte pairs[][], byte left) {
		int j = 0;
		if (pairs[j][0] == left && pairs[j][1] == right)
			return true;
		int k = pairs.length - 1;
		if (pairs[k][0] == left && pairs[k][1] == right)
			return true;
		do {
			int l = (j + k) / 2;
			if (pairs[l][0] == left && pairs[l][1] == right)
				return true;
			if (left < pairs[l][0] || left == pairs[l][0] && right < pairs[l][1])
				k = l;
			else
				j = l;
		} while (j != k && j + 1 != k);
		return false;
	}

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

	private static void filterIpAddresses(char text[]) {
		int j = 0;
		int k = 0;
		int l = 0;
		int i1 = 0;
		while ((j = findFirstDigit(k, text)) != -1) {
			boolean flag = false;
			for (int j1 = k; j1 >= 0 && j1 < j && !flag; j1++)
				if (!isSeparator(text[j1]) && !isSkippableCharacter(text[j1]))
					flag = true;

			if (flag)
				l = 0;
			if (l == 0)
				i1 = j;
			k = findFirstNonDigit(j, text);
			int k1 = 0;
			for (int l1 = j; l1 < k; l1++)
				k1 = (k1 * 10 + text[l1]) - 48;

			if (k1 > 255 || k - j > 8)
				l = 0;
			else
				l++;
			if (l == 4) {
				for (int i2 = i1; i2 < k; i2++)
					text[i2] = '*';

				l = 0;
			}
		}
	}

	private static int findFirstDigit(int start, char text[]) {
		for (int k = start; k < text.length && k >= 0; k++)
			if (text[k] >= '0' && text[k] <= '9')
				return k;

		return -1;
	}

	private static int findFirstNonDigit(int start, char text[]) {
		for (int l = start; l < text.length && l >= 0; l++)
			if (text[l] < '0' || text[l] > '9')
				return l;

		return text.length;
	}

	private static boolean isSeparator(char character) {
		return !isLetter(character) && !isDigit(character);
	}

	private static boolean isSkippableCharacter(char character) {
		if (character < 'a' || character > 'z')
			return true;
		return character == 'v' || character == 'x' || character == 'j' || character == 'q' || character == 'z';
	}

	private static boolean isLetter(char character) {
		return character >= 'a' && character <= 'z' || character >= 'A' && character <= 'Z';
	}

	private static boolean isDigit(char character) {
		return character >= '0' && character <= '9';
	}

	private static boolean isLowercaseLetter(char character) {
		return character >= 'a' && character <= 'z';
	}

	private static boolean isUppercaseLetter(char character) {
		return character >= 'A' && character <= 'Z';
	}

	private static boolean isAllowedFragment(char fragment[]) {
		boolean flag = true;
		for (int j = 0; j < fragment.length; j++)
			if (!isDigit(fragment[j]) && fragment[j] != 0)
				flag = false;

		if (flag)
			return true;
		int k = encodeFragment(fragment);
		int l = 0;
		int i1 = fragmentHashes.length - 1;
		if (k == fragmentHashes[l] || k == fragmentHashes[i1])
			return true;
		do {
			int j1 = (l + i1) / 2;
			if (k == fragmentHashes[j1])
				return true;
			if (k < fragmentHashes[j1])
				i1 = j1;
			else
				l = j1;
		} while (l != i1 && l + 1 != i1);
		return false;
	}

	private static int encodeFragment(char fragment[]) {
		if (fragment.length > 6)
			return 0;
		int i = 0;
		for (int j = 0; j < fragment.length; j++) {
			char expected = fragment[fragment.length - j - 1];
			if (expected >= 'a' && expected <= 'z')
				i = i * 38 + ((expected - 97) + 1);
			else if (expected == '\'')
				i = i * 38 + 27;
			else if (expected >= '0' && expected <= '9')
				i = i * 38 + ((expected - 48) + 28);
			else if (expected != 0)
				return 0;
		}

		return i;
	}

	private static int[] fragmentHashes;
	private static char[][] badWords;
	private static byte[][][] badWordContextPairs;
	private static char[][] domainWords;
	private static char[][] topLevelDomains;
	private static int[] topLevelDomainTypes;
	private static final String[] EXCEPTIONS = { "cook", "cook's", "cooks", "seeks", "sheet", "woop", "woops", "faq",
			"noob", "noobs" };

}