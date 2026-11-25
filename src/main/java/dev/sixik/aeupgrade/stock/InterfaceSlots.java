package dev.sixik.aeupgrade.stock;

import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.parts.misc.InterfacePart;

/**
 * Live slot capacity on the underlying stocking logic.
 */
public interface InterfaceSlots {
    /**
     * Identifies the AE2 block and panel with the native nine-slot screen.
     *
     * @param host interface logic owner
     * @return true for AE2's stock interface block and panel
     */
    static boolean supports(Object host) {
        return host.getClass() == InterfaceBlockEntity.class || host.getClass() == InterfacePart.class;
    }

    /**
     * @return number of unlocked configuration and storage pairs
     */
    int aeupgrade$slotCapacity();

    /**
     * Updates storage insertion checks on the client mirror of the open menu.
     *
     * @param capacity number of unlocked configuration and buffer pairs reported by the server
     */
    void aeupgrade$syncClientSlotCapacity(int capacity);

    /**
     * Recalculates stocking work after the interface or its controller joins a grid.
     */
    void aeupgrade$refreshSlotCapacity();
}
