package dev.sixik.aeupgrade.client.tree;

import java.util.BitSet;

import dev.sixik.aeupgrade.tree.UpgradeCatalog;
import dev.sixik.aeupgrade.tree.UpgradeNode;
import dev.sixik.aeupgrade.tree.UpgradePlan;

/**
 * Visible nodes and outgoing paths for one confirmed allocation in the player map.
 */
public final class UpgradeTreeVisibility {
    private final BitSet hidden = new BitSet();
    private final BitSet hiddenOutputs = new BitSet();

    /**
     * @param catalog   tree whose dependencies are evaluated in topological order
     * @param purchases confirmed allocation for the current network
     */
    public UpgradeTreeVisibility(UpgradeCatalog catalog, UpgradePlan purchases) {
        for (var node : catalog.ordered()) {
            if (purchases.unlocked(node)) {
                continue;
            }
            var rules = catalog.entry(node.id()).revealRules();
            boolean waitingForPrerequisites = rules.hideUntilPrerequisites()
                    && (node.disconnected() || purchases.unlockedPrerequisites(node) < node.requiredCount());
            if (waitingForPrerequisites || !hasVisibleInputs(node)) {
                hidden.set(node.id());
                hiddenOutputs.set(node.id());
            } else if (rules.hideDescendantsUntilUnlocked()) {
                hiddenOutputs.set(node.id());
            }
        }
    }

    private boolean hasVisibleInputs(UpgradeNode node) {
        // A gate hides its own path. A junction can still be seen if its other paths meet the threshold.
        int remaining = node.prerequisiteCount();
        for (int i = 0; i < node.prerequisiteCount(); i++) {
            if (hiddenOutputs.get(node.prerequisiteId(i)) && --remaining < node.requiredCount()) {
                return false;
            }
        }
        return true;
    }

    /**
     * @return whether an upgrade can be seen and inspected on the player map
     */
    public boolean visible(int nodeId) {
        return !hidden.get(nodeId);
    }

    /**
     * @return whether the connection may be drawn between its source and target
     */
    public boolean linkVisible(int source, int target) {
        return visible(target) && (source == UpgradeNode.ROOT || !hiddenOutputs.get(source));
    }
}
