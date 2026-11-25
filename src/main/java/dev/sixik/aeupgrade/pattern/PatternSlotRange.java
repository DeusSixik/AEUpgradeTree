package dev.sixik.aeupgrade.pattern;

/**
 * Bounds of the Pattern Provider's slot upgrades and visible window.
 */
public final class PatternSlotRange {
    /**
     * Number of native pattern slots and width of the visible slot row.
     */
    public static final int ROW_SIZE = 9;

    /**
     * Number of pattern slots displayed in the provider's two-row menu.
     */
    public static final int WINDOW_SIZE = 2 * ROW_SIZE;

    /**
     * Largest bonus that fits alongside the native row in an integer slot count.
     */
    public static final int MAX_BONUS = Integer.MAX_VALUE - ROW_SIZE;

    /**
     * Smallest adjustment, which closes all native slots.
     */
    public static final int MIN_BONUS = -ROW_SIZE;

    /**
     * Keeps the range calculations static.
     */
    private PatternSlotRange() {
    }

    /**
     * Adjusts the installed bonus by the given number of slots.
     *
     * @param bonus current adjustment to the native slot count
     * @param delta signed number of slots to add or remove
     * @return signed adjustment, clamped to keep total capacity between zero and Integer.MAX_VALUE
     */
    public static int adjust(int bonus, int delta) {
        return Math.clamp((long) bonus + delta, MIN_BONUS, MAX_BONUS);
    }

    /**
     * Checks whether the visible provider menu needs its second pattern row.
     *
     * @param browsable total unlocked slots or stored slots that must remain reachable
     * @return {@code true} when the range exceeds the native nine-slot row
     */
    public static boolean needsSecondRow(int browsable) {
        return browsable > ROW_SIZE;
    }

    /**
     * Restricts the first visible slot to the start of a row within the viewport range.
     *
     * @param requested zero-based logical index, possibly outside the available range
     * @param browsable total unlocked slots or slot count including stored overflow, whichever is higher
     * @return index of the first slot in the selected row
     */
    public static int clampOffset(long requested, int browsable) {
        int offset = Math.clamp(requested, 0, maxOffset(browsable));
        return offset - offset % ROW_SIZE;
    }

    /**
     * Finds the last row-aligned position for the two-row viewport. Each complete
     * or partial row beyond the initial window adds one scroll step.
     *
     * @param browsable total unlocked or occupied slots
     * @return greatest allowed offset, in slots, or zero when the initial window covers the range
     */
    public static int maxOffset(int browsable) {
        long overflow = Math.max(0, (long) browsable - WINDOW_SIZE);
        return (int) (Math.ceilDiv(overflow, ROW_SIZE) * ROW_SIZE);
    }
}
