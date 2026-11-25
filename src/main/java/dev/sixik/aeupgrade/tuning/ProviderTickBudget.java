package dev.sixik.aeupgrade.tuning;

/**
 * Counts recipe dispatches shared by all crafting CPUs that use one provider.
 */
public final class ProviderTickBudget {
    /**
     * Server tick of the last dispatch reservation.
     */
    private long tick;

    /**
     * Operations reserved or dispatched during that tick.
     */
    private int used;

    /**
     * Reads available slots at the current live limit.
     *
     * @param now   current server tick
     * @param limit maximum recipe operations per tick
     * @return non-negative remaining recipe operations
     */
    public int remaining(long now, int limit) {
        return now == tick ? Math.max(0, limit - used) : limit;
    }

    /**
     * Reserves one operation before offering the inputs to the destination.
     *
     * @param now   current server tick
     * @param limit maximum recipe operations per tick
     * @return true when one slot was reserved
     */
    public boolean reserve(long now, int limit) {
        if (tick != now) {
            tick = now;
            used = 0;
        }
        if (used >= limit) {
            return false;
        }
        used++;
        return true;
    }

    /**
     * Releases a reservation whose destination did not accept the inputs.
     */
    public void release() {
        used--;
    }
}
