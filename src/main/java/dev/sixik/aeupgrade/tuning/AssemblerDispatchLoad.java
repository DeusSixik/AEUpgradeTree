package dev.sixik.aeupgrade.tuning;

/**
 * Exposes a molecular assembler's queue when its provider chooses a crafting destination.
 */
public interface AssemblerDispatchLoad {
    /**
     * Reports the number of jobs already collected for the next batch.
     *
     * @return queued recipe operations, or {@link Integer#MAX_VALUE} when the machine is busy
     */
    int aeupgrade$queuedRecipes();
}
