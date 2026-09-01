package rs2.chat;

import java.util.Locale;

import rs2.net.Buffer;

/**
 * Encodes and decodes the nibble-packed public/private chat format used by
 * revision 377.
 *
 * <p>
 * Common characters occupy four bits. Less common characters occupy eight bits
 * by combining a marker nibble with a second nibble. This favors ordinary
 * English chat while retaining the complete protocol alphabet.
 * </p>
 */
public class ChatCodec {

	/** Creates a new chat codec with its default client state. */
	public ChatCodec() {
	}

	/** Maximum message length. */
	public static final int MAX_MESSAGE_LENGTH = 80;

	/** Constant value for alphabet. */
	public static final char ALPHABET[] = { ' ', 'e', 't', 'a', 'o', 'i', 'h', 'n', 's', 'r', 'd', 'l', 'u', 'm', 'w',
			'c', 'y', 'f', 'g', 'p', 'b', 'v', 'k', 'x', 'j', 'q', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8',
			'9', ' ', '!', '?', '.', ',', ':', ';', '(', ')', '-', '&', '*', '\\', '\'', '@', '#', '+', '=', '\243',
			'$', '%', '"', '[', ']' };
	/**
	 * Alphabet indexes below this limit fit into one nibble.
	 */
	private static final int COMMON_CHARACTER_LIMIT = 13;

	/**
	 * Offset applied to alphabet indexes encoded using two nibbles.
	 */
	private static final int EXTENDED_CHARACTER_OFFSET = 195;

	/**
	 * Decodes bytes from the buffer and advances its position.
	 *
	 * <p>
	 * Sentence capitalization is part of the original decoder: the first lowercase
	 * letter, and the first lowercase letter after {@code . ! ?}, is converted to
	 * uppercase.
	 * </p>
	 *
	 * @param buffer source buffer
	 * @param length number of encoded bytes to consume
	 * @return the decoded value
	 */
	public static String decode(Buffer buffer, int length) {
		if (length < 0 || length > Integer.MAX_VALUE / 2) {
			throw new IllegalArgumentException("Invalid chat length: " + length);
		}

		/*
		 * Every encoded byte contains two nibbles. In the largest possible decoded
		 * form, both nibbles produce one character.
		 */
		char[] decoded = new char[length * 2];

		int decodedLength = 0;
		int pendingNibble = -1;

		for (int index = 0; index < length; index++) {
			int encoded = buffer.readUnsignedByte();

			int highNibble = encoded >>> 4 & 0xf;

			if (pendingNibble == -1) {
				if (highNibble < COMMON_CHARACTER_LIMIT) {
					decoded[decodedLength++] = ALPHABET[highNibble];
				} else {
					pendingNibble = highNibble;
				}
			} else {
				decoded[decodedLength++] = decodeExtendedCharacter(pendingNibble, highNibble);

				pendingNibble = -1;
			}

			int lowNibble = encoded & 0xf;

			if (pendingNibble == -1) {
				if (lowNibble < COMMON_CHARACTER_LIMIT) {
					decoded[decodedLength++] = ALPHABET[lowNibble];
				} else {
					pendingNibble = lowNibble;
				}
			} else {
				decoded[decodedLength++] = decodeExtendedCharacter(pendingNibble, lowNibble);

				pendingNibble = -1;
			}
		}

		applySentenceCapitalization(decoded, decodedLength);

		return new String(decoded, 0, decodedLength);
	}

	/**
	 * Encodes a message into the supplied buffer.
	 *
	 * <p>
	 * Messages are truncated to 80 characters and converted to lowercase, matching
	 * the original protocol. Unsupported characters are encoded as spaces.
	 * </p>
	 * 
	 * @param message the message text
	 * @param buffer  the source buffer
	 */
	public static void encode(String message, Buffer buffer) {
		if (message.length() > MAX_MESSAGE_LENGTH) {
			message = message.substring(0, MAX_MESSAGE_LENGTH);
		}

		message = message.toLowerCase(Locale.ROOT);

		int pendingNibble = -1;

		for (int index = 0; index < message.length(); index++) {
			int encoded = findAlphabetIndex(message.charAt(index));

			if (encoded >= COMMON_CHARACTER_LIMIT) {
				encoded += EXTENDED_CHARACTER_OFFSET;
			}

			if (pendingNibble == -1) {
				if (encoded < COMMON_CHARACTER_LIMIT) {
					pendingNibble = encoded;
				} else {
					buffer.writeByte(encoded);
				}
			} else if (encoded < COMMON_CHARACTER_LIMIT) {
				buffer.writeByte((pendingNibble << 4) + encoded);

				pendingNibble = -1;
			} else {
				buffer.writeByte((pendingNibble << 4) + (encoded >>> 4));

				pendingNibble = encoded & 0xf;
			}
		}

		/*
		 * If one common character remains, place it in the high nibble and leave the
		 * low nibble as zero, which decodes to a space.
		 */
		if (pendingNibble != -1) {
			buffer.writeByte(pendingNibble << 4);
		}
	}

	/**
	 * Applies the exact encode/decode transformation used before displaying locally
	 * entered chat.
	 * 
	 * @param message the message text
	 * @return the converted value
	 */
	public static String normalize(String message) {
		Buffer temporary = new Buffer(MAX_MESSAGE_LENGTH);

		encode(message, temporary);

		int encodedLength = temporary.position;

		temporary.position = 0;

		return decode(temporary, encodedLength);
	}

	/**
	 * Decodes extended character.
	 *
	 * @param firstNibble  the first nibble
	 * @param secondNibble the second nibble
	 * @return the decoded extended character value
	 */
	private static char decodeExtendedCharacter(int firstNibble, int secondNibble) {
		int alphabetIndex = ((firstNibble << 4) + secondNibble) - EXTENDED_CHARACTER_OFFSET;

		return ALPHABET[alphabetIndex];
	}

	/**
	 * Finds a character's protocol alphabet index.
	 *
	 * Unsupported characters use index zero, which represents a space.
	 * 
	 * @param character the character
	 * @return the alphabet index result
	 */
	private static int findAlphabetIndex(char character) {
		for (int index = 0; index < ALPHABET.length; index++) {
			if (ALPHABET[index] == character) {
				return index;
			}
		}

		return 0;
	}

	/**
	 * Applies sentence capitalization.
	 *
	 * @param characters the characters
	 * @param length     the number of elements or bytes
	 */
	private static void applySentenceCapitalization(char[] characters, int length) {
		boolean capitalize = true;

		for (int index = 0; index < length; index++) {
			char character = characters[index];

			if (capitalize && character >= 'a' && character <= 'z') {
				characters[index] = (char) (character - 'a' + 'A');

				capitalize = false;
			}

			if (character == '.' || character == '!' || character == '?') {
				capitalize = true;
			}
		}
	}

}
