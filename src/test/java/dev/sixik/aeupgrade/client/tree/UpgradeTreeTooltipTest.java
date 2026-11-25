package dev.sixik.aeupgrade.client.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import dev.sixik.aeupgrade.tree.UpgradeCatalog;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Definition;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Entry;
import dev.sixik.aeupgrade.tree.UpgradeNode;
import dev.sixik.aeupgrade.tree.UpgradePlan;
import org.junit.jupiter.api.Test;

class UpgradeTreeTooltipTest {
    @Test
    void branchChoices_repeatedBranchRules_warnOnlyAboutNewClosuresAtTheChoice() {
        var catalog = new UpgradeCatalog(new Definition(36, 12, null, List.of(
                entry(7, UpgradeNode.ROOT, 9),
                entry(8, UpgradeNode.ROOT, 9, 10),
                entry(9, UpgradeNode.ROOT),
                entry(10, UpgradeNode.ROOT),
                entry(11, 8, 9, 10))));

        assertEquals(List.of(9, 10), warnings(catalog, 8));
        assertEquals(List.of(), warnings(catalog, 11));
        assertEquals(List.of(10), warnings(catalog, 8, 7));
        assertEquals(List.of(), warnings(catalog, 8, 8));
        assertEquals(List.of(), warnings(catalog, 11, 8));
        assertEquals(List.of(9, 10), warnings(catalog, 8));
    }

    @Test
    void branchChoices_childWithNewConflicts_stillWarnsAtItsOwnChoice() {
        var catalog = new UpgradeCatalog(new Definition(36, 11, null, List.of(
                entry(7, UpgradeNode.ROOT, 9),
                entry(8, 7, 9, 10),
                entry(9, UpgradeNode.ROOT),
                entry(10, UpgradeNode.ROOT))));

        assertEquals(List.of(10), warnings(catalog, 8, 7));
    }

    private static List<Integer> warnings(UpgradeCatalog catalog, int node, Integer... bought) {
        var plan = UpgradePlan.restore(List.of(bought), catalog);
        var entry = catalog.entry(node);
        return UpgradeTreeText.branchChoices(entry, plan.availability(entry.node()), catalog, plan);
    }

    private static Entry entry(int id, int parent, Integer... branches) {
        var node = new UpgradeNode(id, "test." + id, 1, 0, UpgradeNode.Sector.NETWORK, null,
                parent, UpgradeNode.ROOT, id * 20, 0, UpgradeNode.Kind.PASSIVE);
        return new Entry(node, "", "", "", List.of(), null, null, List.of(branches));
    }
}
