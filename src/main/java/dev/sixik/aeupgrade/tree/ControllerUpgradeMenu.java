package dev.sixik.aeupgrade.tree;

import dev.sixik.aeupgrade.network.ControllerOverview;

/**
 * Upgrade action and diagnostic snapshot for a native ME Controller menu.
 */
public interface ControllerUpgradeMenu {
    /**
     * Requests the tree for the controller hosting this menu.
     */
    void aeupgrade$openTree();

    /**
     * @return latest synchronized diagnostics for this menu
     */
    ControllerOverview aeupgrade$overview();

    /**
     * Stores diagnostics for a matching open controller menu.
     *
     * @param overview server snapshot for this container
     */
    void aeupgrade$acceptOverview(ControllerOverview overview);
}
