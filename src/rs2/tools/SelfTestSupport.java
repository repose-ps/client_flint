package rs2.tools;

import java.util.Arrays;

/** Lightweight assertion counter shared by the executable client self-tests. */
final class SelfTestSupport {

    /** Number of checks completed by this suite. */
    private int checks;

    /** Creates an empty assertion counter. */
    SelfTestSupport() {
    }

    /**
     * Records one successful or failed boolean check.
     *
     * @param condition whether the assertion succeeded
     * @param message assertion description
     */
    void check(boolean condition, String message) {
        checks++;
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    /**
     * Checks an integer value.
     *
     * @param actual actual value
     * @param expected expected value
     * @param message assertion description
     */
    void equal(int actual, int expected, String message) {
        check(actual == expected, message + ": expected " + expected + ", got " + actual);
    }

    /**
     * Checks a long value.
     *
     * @param actual actual value
     * @param expected expected value
     * @param message assertion description
     */
    void equal(long actual, long expected, String message) {
        check(actual == expected, message + ": expected " + expected + ", got " + actual);
    }

    /**
     * Checks a string value.
     *
     * @param actual actual value
     * @param expected expected value
     * @param message assertion description
     */
    void equal(String actual, String expected, String message) {
        check(expected.equals(actual), message + ": expected \"" + expected + "\", got \"" + actual + "\"");
    }

    /**
     * Checks a complete byte sequence.
     *
     * @param actual actual bytes
     * @param expected expected bytes
     * @param message assertion description
     */
    void bytes(byte[] actual, byte[] expected, String message) {
        check(Arrays.equals(actual, expected), message + ": expected " + hex(expected) + ", got " + hex(actual));
    }

    /**
     * Returns the number of checks completed by this suite.
     *
     * @return completed check count
     */
    int checks() {
        return checks;
    }

    /**
     * Formats bytes for assertion diagnostics.
     *
     * @param bytes bytes to format
     * @return lowercase hexadecimal bytes separated by spaces
     */
    static String hex(byte[] bytes) {
        StringBuilder text = new StringBuilder(bytes.length * 3);
        for (int index = 0; index < bytes.length; index++) {
            if (index != 0) {
                text.append(' ');
            }
            text.append(String.format("%02x", bytes[index] & 0xff));
        }
        return text.toString();
    }
}
