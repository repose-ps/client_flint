package rs2.game;

import java.util.function.IntConsumer;

/**
 * Owns the current and server-shadow revision-377 varp arrays.
 *
 * <p>The shadow array tracks the latest authoritative values received from the
 * server. The current array is the value exposed to client scripts and UI code;
 * individual changes may trigger client-side setting effects before the arrays
 * are synchronized again.</p>
 */
public final class VarpState {

    /** Number of varp slots allocated by the revision-377 client. */
    public static final int CAPACITY = 2000;

    /** Current client-visible varp values. */
    private final int[] values = new int[CAPACITY];
    /** Latest server-shadow varp values. */
    private final int[] shadowValues = new int[CAPACITY];

    /** Creates zero-initialized varp state. */
    public VarpState() {
    }

    /**
     * Returns one current varp value.
     *
     * @param id varp identifier
     * @return current value
     */
    public int get(int id) {
        return values[id];
    }

    /**
     * Sets one current varp value.
     *
     * @param id varp identifier
     * @param value new value
     */
    public void set(int id, int value) {
        values[id] = value;
    }

    /**
     * Toggles a binary current varp between zero and one.
     *
     * @param id varp identifier
     * @return toggled value
     */
    public int toggleBinary(int id) {
        values[id] = 1 - values[id];
        return values[id];
    }

    /**
     * Stores a server-shadow value and copies it into current state when changed.
     *
     * @param id varp identifier
     * @param value authoritative value
     * @return {@code true} when the current value changed
     */
    public boolean acceptServerValue(int id, int value) {
        shadowValues[id] = value;
        if (values[id] == value) {
            return false;
        }
        values[id] = value;
        return true;
    }

    /**
     * Synchronizes every current varp to its server-shadow value.
     *
     * @param changedIdConsumer callback invoked for each changed varp identifier
     */
    public void synchronizeToShadow(IntConsumer changedIdConsumer) {
        for (int id = 0; id < values.length; id++) {
            if (values[id] == shadowValues[id]) {
                continue;
            }
            values[id] = shadowValues[id];
            changedIdConsumer.accept(id);
        }
    }
}
