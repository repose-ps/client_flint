package rs2.text;

/**
 * Encodes RuneScape player names as compact base-37 numbers.
 *
 * <p>
 * Digit zero represents a separator, digits 1-26 represent {@code a-z}, and
 * digits 27-36 represent {@code 0-9}. Only the first twelve characters
 * participate because every valid encoded name must fit in a signed long.
 * </p>
 */
public class Base37 {

	/** Creates a new base37 with its default client state. */
	public Base37() {
	}

	/** Maximum name length. */
	public static final int MAX_NAME_LENGTH = 12;

	/** Constant value for encoded name limit. */
	private static final long ENCODED_NAME_LIMIT = 0x5b5b57f8a98a5dd1L;

	/** Constant value for invalid name. */
	private static final String INVALID_NAME = "invalid_name";

	/** Constant value for alphabet. */
	private static final char[] ALPHABET = { '_', 'a', 'b', 'c', 'd', 'e', 'f', 'g', 'h', 'i', 'j', 'k', 'l', 'm', 'n',
			'o', 'p', 'q', 'r', 's', 't', 'u', 'v', 'w', 'x', 'y', 'z', '0', '1', '2', '3', '4', '5', '6', '7', '8',
			'9' };

	/**
	 * Encodes a name using the revision-377 base-37 representation.
	 *
	 * <p>
	 * Unsupported characters occupy a zero digit, just as spaces and underscores
	 * do. Trailing zero digits are removed so equivalent names have one canonical
	 * numeric representation.
	 * </p>
	 * 
	 * @param name the name
	 * @return the base-37 encoded name
	 */
	public static long encode(String name) {
		long encoded = 0L;

		int length = Math.min(name.length(), MAX_NAME_LENGTH);

		for (int index = 0; index < length; index++) {
			char character = name.charAt(index);
			encoded *= 37L;

			if (character >= 'A' && character <= 'Z') {
				encoded += character - 'A' + 1;
			} else if (character >= 'a' && character <= 'z') {
				encoded += character - 'a' + 1;
			} else if (character >= '0' && character <= '9') {
				encoded += character - '0' + 27;
			}
		}

		while (encoded != 0L && encoded % 37L == 0L) {
			encoded /= 37L;
		}

		return encoded;
	}

	/**
	 * Decodes a base-37 name into lowercase letters, digits, and underscores.
	 *
	 * <p>
	 * Malformed values return {@code "invalid_name"}, preserving the behavior
	 * expected by the original client.
	 * </p>
	 * 
	 * @param encoded the encoded
	 * @return the decoded value
	 */
	public static String decode(long encoded) {
		if (encoded <= 0L || encoded >= ENCODED_NAME_LIMIT || encoded % 37L == 0L) {
			return INVALID_NAME;
		}

		char[] characters = new char[MAX_NAME_LENGTH];

		int position = characters.length;

		while (encoded != 0L) {
			long quotient = encoded / 37L;
			int digit = (int) (encoded - quotient * 37L);

			characters[--position] = ALPHABET[digit];

			encoded = quotient;
		}

		return new String(characters, position, characters.length - position);
	}
}
