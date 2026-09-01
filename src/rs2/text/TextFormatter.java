package rs2.text;

/**
 * Stateless presentation helpers for legacy client text.
 */
public class TextFormatter {

	/** Creates a new text formatter with its default client state. */
	public TextFormatter() {
	}

	/**
	 * Returns whether lowercase ascii.
	 *
	 * @param character the character
	 * @return whether lowercase ascii
	 */
	private static boolean isLowercaseAscii(char character) {
		return character >= 'a' && character <= 'z';
	}

	/**
	 * Converts a lowercase ASCII letter to uppercase.
	 *
	 * @param character the character
	 * @return the converted value
	 */
	private static char toUppercaseAscii(char character) {
		return (char) (character - 'a' + 'A');
	}

	/**
	 * Converts a protocol name such as {@code zeZima_1} to {@code ZeZima 1}.
	 *
	 * <p>
	 * The protocol is ASCII-oriented, so only lowercase ASCII letters are
	 * capitalized. This preserves the exact revision-377 transformation.
	 * </p>
	 * 
	 * @param value the value
	 * @return the display-formatted name
	 */
	public static String formatDisplayName(String value) {
		if (value.length() == 0) {
			return value;
		}

		char[] characters = value.toCharArray();

		for (int index = 0; index < characters.length; index++) {
			if (characters[index] != '_') {
				continue;
			}

			characters[index] = ' ';

			int followingIndex = index + 1;

			if (followingIndex < characters.length && isLowercaseAscii(characters[followingIndex])) {
				characters[followingIndex] = toUppercaseAscii(characters[followingIndex]);
			}
		}

		if (isLowercaseAscii(characters[0])) {
			characters[0] = toUppercaseAscii(characters[0]);
		}

		return new String(characters);
	}

	/**
	 * Returns a masked version of the supplied text.
	 *
	 * @param value the value
	 * @return the masked text
	 */
	public static String mask(String value) {
		StringBuilder masked = new StringBuilder(value.length());

		for (int index = 0; index < value.length(); index++) {
			masked.append('*');
		}

		return masked.toString();
	}
}
