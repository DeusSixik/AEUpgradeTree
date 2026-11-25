package dev.sixik.aeupgrade.pattern;

/**
 * Values consumed by the shared row scrollbar.
 */
public interface SlotScrollState {
    /**
     * @return number of unlocked slots
     */
    int aeupgrade$capacity();

    /**
     * @return range length including locked slots with saved contents
     */
    int aeupgrade$browsable();

    /**
     * @return zero-based index of the first displayed slot
     */
    int aeupgrade$offset();

    /**
     * @return requested first slot, which may differ from the displayed slot on a remote viewport
     */
    default int aeupgrade$scrollTarget() {
        return aeupgrade$offset();
    }
}
