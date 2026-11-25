package dev.sixik.aeupgrade.tuning;

/**
 * Exposes Pattern Provider capacity to the crafting CPU.
 */
public interface ProviderDispatch {
    /**
     * Default recipe operations per server tick on a controller network.
     */
    int DEFAULT_THROUGHPUT = 32;

    /**
     * Upper bound on recipe operations a provider can dispatch per tick.
     */
    int MAX_THROUGHPUT = 256;

    /**
     * Reports how many more recipe operations this provider can accept in the current tick.
     *
     * @return remaining capacity, or zero if enhanced dispatch is unavailable
     */
    int aeupgrade$remainingOperations();
}
