package dev.sixik.aeupgrade.client.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.google.gson.Gson;
import dev.sixik.aeupgrade.tree.UpgradeCatalog;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Definition;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Entry;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Reveal;
import dev.sixik.aeupgrade.tree.UpgradeNode;
import dev.sixik.aeupgrade.tree.UpgradePlan;
import org.jetbrains.annotations.Nullable;
import org.junit.jupiter.api.Test;

class UpgradeTreeVisibilityTest {
    private static final Reveal HIDE_DESCENDANTS = new Reveal(true, false);
    private static final Reveal WAIT_FOR_INPUTS = new Reveal(false, true);

    @Test
    void visibility_purchaseGate_hidesItsBranchAndRevealsItAfterPurchase() {
        var catalog = catalog(root(7, HIDE_DESCENDANTS), entry(8, null, 1, 7), entry(9, null, 1, 8));

        var initial = visibility(catalog);
        assertTrue(initial.visible(7));
        assertTrue(initial.linkVisible(UpgradeNode.ROOT, 7));
        assertFalse(initial.visible(8));
        assertFalse(initial.visible(9));
        assertFalse(initial.linkVisible(7, 8));

        var purchased = visibility(catalog, 7);
        assertTrue(purchased.visible(8));
        assertTrue(purchased.visible(9));
        assertTrue(purchased.linkVisible(7, 8));
        assertTrue(purchased.linkVisible(8, 9));
        assertFalse(visibility(catalog).visible(9));
    }

    @Test
    void visibility_prerequisiteGate_checksThresholdAndIgnoresPointCost() {
        var catalog = new UpgradeCatalog(new Definition(2, 12, null, List.of(
                root(7, null), root(8, null), root(9, null),
                entry(10, WAIT_FOR_INPUTS, 2, 7, 8, 9), entry(11, null, 1, 10))));

        var oneInput = visibility(catalog, 7);
        assertFalse(oneInput.visible(10));
        assertFalse(oneInput.visible(11));
        var enoughInputs = visibility(catalog, 7, 9);
        assertTrue(enoughInputs.visible(10));
        assertTrue(enoughInputs.visible(11));
        assertTrue(enoughInputs.linkVisible(10, 11));
        assertEquals(UpgradePlan.Availability.POINTS, UpgradePlan.restore(List.of(7, 9), catalog).availability(catalog.node(10)));
    }

    @Test
    void visibility_alternateRoutes_keepReachableNodesButHideLinksFromGates() {
        var catalog = catalog(root(7, HIDE_DESCENDANTS), root(8, null),
                entry(9, null, 1, 7, 8), entry(10, null, 2, 7, 8));
        var initial = visibility(catalog);
        assertTrue(initial.visible(9));
        assertTrue(initial.linkVisible(8, 9));
        assertFalse(initial.linkVisible(7, 9));
        assertFalse(initial.visible(10));
        assertFalse(initial.linkVisible(8, 10));

        var alternatePurchase = visibility(catalog, 8, 9);
        assertTrue(alternatePurchase.visible(9));
        assertFalse(alternatePurchase.linkVisible(7, 9));
        var revealed = visibility(catalog, 7);
        assertTrue(revealed.visible(10));
        assertTrue(revealed.linkVisible(7, 10));
    }

    @Test
    void visibility_bothGates_revealTheNodeBeforeItsDescendants() {
        var catalog = catalog(root(7, null), entry(8, new Reveal(true, true), 1, 7), entry(9, null, 1, 8));
        assertFalse(visibility(catalog).visible(8));
        assertTrue(visibility(catalog, 7).visible(8));
        assertFalse(visibility(catalog, 7).visible(9));
        assertTrue(visibility(catalog, 7, 8).visible(9));
    }

    @Test
    void catalog_roundTrip_preservesVisibilityAndDefaultsForLegacyEntries() {
        var original = catalog(root(7, null), entry(8, WAIT_FOR_INPUTS, 1, 7));
        var gson = new Gson();
        var reloaded = new UpgradeCatalog(gson.fromJson(gson.toJson(original.definition()), Definition.class));

        assertNull(reloaded.entry(7).reveal());
        assertEquals(new Reveal(false, false), reloaded.entry(7).revealRules());
        assertEquals(WAIT_FOR_INPUTS, reloaded.entry(8).reveal());
        assertFalse(visibility(reloaded).visible(8));
    }

    private static UpgradeTreeVisibility visibility(UpgradeCatalog catalog, Integer... unlocked) {
        return new UpgradeTreeVisibility(catalog, UpgradePlan.restore(List.of(unlocked), catalog));
    }

    private static UpgradeCatalog catalog(Entry... entries) {
        return new UpgradeCatalog(new Definition(36, 7 + entries.length, null, List.of(entries)));
    }

    private static Entry root(int id, @Nullable Reveal reveal) {
        return entry(id, reveal, 0, UpgradeNode.ROOT);
    }

    private static Entry entry(int id, @Nullable Reveal reveal, int required, Integer... inputs) {
        var node = new UpgradeNode(id, "test." + id, 1, 0, UpgradeNode.Sector.NETWORK, null,
                UpgradeNode.ROOT, UpgradeNode.ROOT, id * 20, 0, UpgradeNode.Kind.PASSIVE)
                .withPrerequisites(List.of(inputs), required);
        return new Entry(node, "", "", "", List.of(), null, null, List.of(), reveal);
    }
}
