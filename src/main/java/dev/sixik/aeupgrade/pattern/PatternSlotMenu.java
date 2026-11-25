package dev.sixik.aeupgrade.pattern;

/**
 * Scroll state shared by the provider's menu and its client screen.
 */
public interface PatternSlotMenu extends SlotScrollState {
    /**
     * @return true for the standard block and panel provider menus
     */
    boolean aeupgrade$hasPatternWindow();

    /**
     * @return true while awaiting the slot update for a client scroll request
     */
    boolean aeupgrade$waitingForScroll();

    /**
     * Requests a synchronized change of the first visible row.
     *
     * @param offset requested zero-based index of the first visible slot
     */
    void aeupgrade$requestScroll(long offset);
}
