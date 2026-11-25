package dev.sixik.aeupgrade.channel;

/**
 * Computes cable limits from the normal cable capacity in AE2's default channel mode.
 */
public final class CableChannelCapacity {
    /**
     * AE2's unmodified normal cable limit in the default mode, in channels.
     */
    public static final int DEFAULT_NORMAL_LIMIT = 8;

    /**
     * AE2's unmodified dense cable limit in the default mode, in channels.
     */
    public static final int DEFAULT_DENSE_LIMIT = 32;

    /**
     * Largest normal limit that leaves room for the dense cable multiplier (4),
     * the X4 channel mode (4), and the smart dense display's scaling factor (32).
     */
    static final int MAX_NORMAL_LIMIT = Integer.MAX_VALUE / (4 * 4 * 32);

    /**
     * Keeps this utility class non-instantiable.
     */
    private CableChannelCapacity() {
    }

    /**
     * Applies {@code clamp(normalLimit + delta, 0, MAX_NORMAL_LIMIT)} to the configured limit.
     *
     * @param normalLimit current default-mode limit for a normal cable, in channels
     * @param delta       signed change to the normal cable limit, in channels
     * @return the adjusted limit between zero and {@link #MAX_NORMAL_LIMIT}, inclusive
     */
    static int adjust(int normalLimit, int delta) {
        return Math.clamp((long) normalLimit + delta, 0, MAX_NORMAL_LIMIT);
    }

    /**
     * Computes {@code ae2Limit / DEFAULT_NORMAL_LIMIT * normalLimit}, keeping AE2's cable and mode multipliers.
     *
     * @param ae2Limit    unmodified finite limit for this cable and channel mode, in channels
     * @param normalLimit configured normal cable limit in default mode, in channels
     * @return the adjusted finite capacity, in channels
     */
    public static int scale(int ae2Limit, int normalLimit) {
        return ae2Limit / DEFAULT_NORMAL_LIMIT * normalLimit;
    }
}
