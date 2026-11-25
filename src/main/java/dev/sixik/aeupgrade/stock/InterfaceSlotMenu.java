package dev.sixik.aeupgrade.stock;

/**
 * Stock slot counts synchronized from the open ME Interface to its screen.
 */
public interface InterfaceSlotMenu {
    /**
     * @return true when this is an expandable stock interface
     */
    boolean aeupgrade$hasStockSlots();

    /**
     * @return count of enabled configuration and storage pairs
     */
    int aeupgrade$stockCapacity();

    /**
     * @return length of the browsable range including locked settings and stored resources
     */
    int aeupgrade$stockRange();
}
