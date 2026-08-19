package rs2.net;

/**
 * Formatting utilities for IPv4 addresses stored as packed 32-bit integers.
 */
public final class Ipv4Address {

    private Ipv4Address() {
        throw new AssertionError("No instances");
    }

    /**
     * Formats a network-order integer as dotted-decimal IPv4 text.
     *
     * <p>For example, {@code 0x7f000001} becomes
     * {@code 127.0.0.1}.</p>
     */
    public static String format(int address) {
        return new StringBuilder(15)
            .append(address >>> 24 & 0xff)
            .append('.')
            .append(address >>> 16 & 0xff)
            .append('.')
            .append(address >>> 8 & 0xff)
            .append('.')
            .append(address & 0xff)
            .toString();
    }
}