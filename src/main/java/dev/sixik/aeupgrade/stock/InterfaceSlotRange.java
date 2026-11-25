package dev.sixik.aeupgrade.stock;

import dev.sixik.aeupgrade.pattern.PatternSlotRange;

/**
 * Bounds for the paired stocking slots of a stock ME Interface.
 */
public final class InterfaceSlotRange {
    /**
     * Number of configuration and storage pairs in a standard interface.
     */
    public static final int DEFAULT_CAPACITY = PatternSlotRange.ROW_SIZE;

    /**
     * Maximum number of pairs backed by AE2's native inventory and crafting arrays.
     */
    public static final int MAX_CAPACITY = 54;

    /**
     * Keeps slot range calculations static.
     */
    private InterfaceSlotRange() {
    }

    /**
     * Applies a signed slot adjustment.
     *
     * @param capacity current number of unlocked pairs
     * @param delta    signed number of pairs to add or remove
     * @return new capacity between zero and the maximum
     */
    public static int adjust(int capacity, int delta) {
        return Math.clamp((long) capacity + delta, 0, MAX_CAPACITY);
    }
}
