package dev.sixik.aeupgrade.tuning;

/**
 * Computes configured draw and virtual acceleration while preserving AE2's progress calculations.
 */
public final class DeviceTuningMath {
    /**
     * Maximum effective speed-card count supported by an AE2 molecular assembler.
     */
    public static final int MAX_SPEED_CARDS = 5;

    /**
     * Crafting progress required for one assembly cycle.
     */
    public static final int PROGRESS_PER_CYCLE = 100;

    /**
     * Maximum recipe operations gathered in a batch, bounding the inventory work done in one tick.
     */
    public static final int MAX_BATCH_SIZE = 64;

    /**
     * Idle collection window after the last accepted job. A base AE2 CPU pushes once every three ticks.
     */
    static final int COLLECTION_TICKS = 4;

    /**
     * Keeps this class non-instantiable.
     */
    private DeviceTuningMath() {
    }

    /**
     * Computes {@code max(0, base * (1 + percent / 100) + flat)}.
     *
     * @param base    AE2 draw per tick in AE
     * @param percent signed percentage change relative to the base draw
     * @param flat    signed addition per tick in AE, applied after the percentage
     * @return adjusted non-negative draw per tick in AE
     */
    public static double adjustedDraw(double base, double percent, double flat) {
        return Math.max(0, base * (1.0 + percent / 100.0) + flat);
    }

    /**
     * Converts paid energy to the equivalent base energy for AE2's work calculation.
     * The result is {@code base * (paid / requested)}, capped at the full base amount.
     *
     * @param base      unmodified energy for this work step in AE
     * @param requested positive adjusted energy cost for the same step in AE
     * @param paid      energy delivered to the assembler in AE
     * @return equivalent unmodified energy in AE
     */
    public static double equivalentEnergy(double base, double requested, double paid) {
        return paid >= requested ? base : base * (paid / requested);
    }

    /**
     * Applies virtual card changes within AE2's native range.
     *
     * @param installed number of installed speed cards
     * @param virtual   signed adjustment to the installed cards
     * @return the effective card count
     */
    public static int effectiveSpeedCards(int installed, int virtual) {
        return (int) Math.clamp((long) installed + virtual, 0, MAX_SPEED_CARDS);
    }

    /**
     * Computes {@code min(100, base * (1 + percent / 100))}. One cycle can complete per tick.
     *
     * @param base    AE2 progress per tick at the selected card level
     * @param percent signed speed modifier, at least -100
     * @return adjusted progress per tick
     */
    public static double speed(double base, double percent) {
        return Math.min(PROGRESS_PER_CYCLE, base * (1 + percent / 100));
    }

    /**
     * Starts a full batch immediately and a partial batch after its collection window expires.
     *
     * @param queued          number of accepted recipes
     * @param limit           configured maximum recipes per cycle
     * @param ticksSinceInput game ticks since the last accepted recipe
     * @return true if the cycle should start work
     */
    public static boolean batchReady(int queued, int limit, long ticksSinceInput) {
        return queued > 0 && (queued >= limit || ticksSinceInput >= COLLECTION_TICKS);
    }
}
