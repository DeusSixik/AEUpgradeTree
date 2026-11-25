package dev.sixik.aeupgrade.channel;

/**
 * Resolves configured costs and adapts AE2's one-channel availability check.
 */
public final class ChannelCostRules {
    /**
     * Sentinel for a missing override. Zero is a configured cost.
     */
    public static final int INHERIT = -1;

    /**
     * Keeps the rules class non-instantiable.
     */
    private ChannelCostRules() {
    }

    /**
     * Resolves the most specific configured channel cost.
     *
     * @param local   per-device cost in channels, or {@link #INHERIT}
     * @param network per-family cost on this grid in channels, or {@link #INHERIT}
     * @param global  per-family cost in channels, or {@link #INHERIT}
     * @return resolved cost, or {@link #INHERIT} when all settings defer to AE2
     */
    public static int resolve(int local, int network, int global) {
        if (local != INHERIT) {
            return local;
        }
        return network == INHERIT ? global : network;
    }

    /**
     * Computes the rejection threshold for an allocation of {@code cost} channels.
     * AE2 rejects when {@code used >= threshold}. The threshold is
     * {@code capacity - cost + 1}, equivalent to rejecting when
     * {@code used + cost > capacity} without overflowing that sum.
     *
     * @param capacity maximum channels the route node can carry, at least zero
     * @param cost     positive number of channels to allocate together
     * @return first used-channel count that leaves too little room for the full cost
     */
    public static int rejectionThreshold(int capacity, int cost) {
        return capacity - cost + 1;
    }
}
