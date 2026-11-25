package dev.sixik.aeupgrade.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.google.gson.Gson;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Definition;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Entry;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Kind;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Target;
import dev.sixik.aeupgrade.tree.UpgradePlan.Availability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UpgradeResearchPointsTest {
    @Test
    void grant_ceilingCountsSpentPointsAndResetRefundsPartOfSpending() {
        var catalog = catalog(1, 4);
        var start = UpgradePlan.empty(catalog);
        var starter = catalog.node(0);
        assertEquals(Availability.POINTS, start.availability(starter));
        assertSame(start, start.grant(-1));
        var acquired = start.grant(2).purchase(starter);
        assertEquals(3, acquired.total());
        assertEquals(1, acquired.remaining());
        var max = acquired.grant(Integer.MAX_VALUE);
        assertEquals(4, max.total());
        assertEquals(2, max.remaining());
        assertSame(max, max.grant(1));
        var refund = max.refund(UpgradeNode.ROOT);
        assertEquals(1, refund.returned());
        assertEquals(1, refund.lost());
        assertEquals(3, refund.plan().remaining());
        assertEquals(List.of(), refund.plan().ids());
        assertEquals(max.remaining(), UpgradePlan.restore(max.savedIds(), catalog, max.total()).remaining());
        assertEquals(max.ids(), UpgradePlan.restore(max.ids(), catalog, max.total()).ids());
    }

    @Test
    void update_startingPointsOnlyAffectNewProfilesAndLowerCapClampsSavedPoints() {
        var original = catalog(3, 7);
        var profile = UpgradePlan.empty(original).purchase(original.node(0)).grant(2);
        var revised = profile.update(catalog(0, 4));
        assertEquals(4, revised.total());
        assertEquals(2, revised.remaining());
        assertEquals(List.of(0), revised.ids());
        assertEquals(5, profile.update(catalog(6, 10)).total());
        assertEquals(6, UpgradePlan.empty(catalog(6, 10)).total());
    }

    @Test
    void migrate_legacyPurchasesKeepTheirInvestmentWithoutReceivingTheNewCeiling() {
        var catalog = catalog(0, 9);
        var legacy = UpgradePlan.migrate(UpgradePlan.restore(new int[] {0}, catalog));
        assertEquals(List.of(0), legacy.ids());
        assertEquals(2, legacy.total());
        assertEquals(0, legacy.remaining());
    }

    @Test
    void definition_missingStartKeepsLegacyBudgetAndInvalidRangesAreRejected() {
        var legacy = new Gson().fromJson("{\"budget\":36,\"nextId\":7,\"nodes\":[]}", Definition.class);
        assertEquals(36, UpgradePlan.empty(new UpgradeCatalog(legacy)).total());
        assertEquals(0, UpgradePlan.empty(catalog(0, 0)).total());
        assertThrows(IllegalArgumentException.class, () -> catalog(5, 4));
        assertThrows(IllegalArgumentException.class, () -> catalog(-1, 4));
        assertThrows(IllegalArgumentException.class, () -> catalog(0, UpgradeCatalog.MAX_POINTS + 1));
    }

    /** Verifies that new and older definitions permit owners unless the operator restriction is explicit. */
    @Test
    void definition_operatorRestrictionRequiresExplicitOptIn() {
        var gson = new Gson();
        assertFalse(UpgradeCatalog.DEFAULT.definition().operatorOnly());
        var legacy = gson.fromJson("{\"budget\":36,\"nextId\":7,\"nodes\":[]}", Definition.class);
        assertFalse(new UpgradeCatalog(legacy).definition().operatorOnly());
        var restricted = gson.fromJson("{\"budget\":36,\"nextId\":7,\"nodes\":[],\"operatorOnly\":true}", Definition.class);
        assertTrue(new UpgradeCatalog(restricted).definition().operatorOnly());
    }

    /**
     * Checks that an operator can change free points within the network cap while keeping the paid node.
     *
     * @param requested free balance requested by a command
     * @param remaining free balance after applying the cap
     * @param total paid cost plus the resulting free balance
     */
    @ParameterizedTest
    @CsvSource({"-2147483647, 0, 2", "0, 0, 2", "2, 2, 4", "2147483647, 5, 7"})
    void withRemaining_boundedBalanceKeepsPaidResearch(int requested, int remaining, int total) {
        var catalog = catalog(3, 7);
        var plan = UpgradePlan.empty(catalog).purchase(catalog.node(0)).grant(2);

        var updated = plan.withRemaining(requested);
        assertEquals(remaining, updated.remaining());
        assertEquals(total, updated.total());
        assertEquals(List.of(0), updated.ids());
        assertSame(plan.effects(), updated.effects());
        assertSame(updated, updated.withRemaining(remaining));
    }

    /** Checks whole-tree activation, its client snapshot, and a reset refund based only on paid research. */
    @Test
    void unlockAll_blockedOrUnaffordableNodesActivateWithoutConsumingPoints() {
        var starter = testEntry(7, 2, UpgradeNode.ROOT, 2).withBranches(List.of(8));
        var blocked = testEntry(8, 6, UpgradeNode.ROOT, 3);
        var disconnected = testEntry(9, 6, UpgradeNode.DISCONNECTED, 5);
        var catalog = new UpgradeCatalog(new Definition(4, 10, null, List.of(starter, blocked, disconnected)));
        var paid = UpgradePlan.empty(catalog).purchase(starter.node());

        assertEquals(Availability.EXCLUSIVE, paid.availability(blocked.node()));
        assertEquals(Availability.DISCONNECTED, paid.availability(disconnected.node()));
        var all = paid.unlockAll();
        assertTrue(all.allUnlocked());
        assertFalse(all.sameUnlocks(paid));
        assertSame(all, all.unlockAll());
        assertEquals(List.of(7), all.ids());
        assertEquals(2, all.remaining());
        assertEquals(4, all.total());
        assertEquals(0, all.recoveredCertificates(0));
        for (var node : catalog.ordered()) {
            assertTrue(all.unlocked(node));
            assertEquals(Availability.UNLOCKED, all.availability(node));
            assertFalse(all.canRefund(node.id()));
        }
        assertEquals(10.0, all.effects().amount(Kind.CHANNELS), 0.0);

        var client = UpgradePlan.restore(all.ids(), catalog, all.total(), all.allUnlocked());
        assertTrue(client.sameUnlocks(all));
        assertEquals(List.of(7, 8, 9), client.refund(UpgradeNode.ROOT).removedIds());
        var reset = all.withRemaining(0).grant(2).refund(UpgradeNode.ROOT);
        assertEquals(List.of(7, 8, 9), reset.removedIds());
        assertEquals(1, reset.returned());
        assertEquals(1, reset.lost());
        assertEquals(3, reset.plan().remaining());
        assertFalse(reset.plan().allUnlocked());
        assertTrue(reset.plan().isEmpty());
        assertSame(catalog.coreEffects(), reset.plan().effects());
    }

    /**
     * Creates a test node with a channel bonus.
     *
     * @param id stable node ID
     * @param cost research points required to unlock the node
     * @param previous preceding node ID or a connection sentinel
     * @param channels additional cable channels
     * @return entry suitable for a test catalog
     */
    private static Entry testEntry(int id, int cost, int previous, int channels) {
        var node = new UpgradeNode(id, "operator." + id, cost, 0, UpgradeNode.Sector.NETWORK, null,
                previous, UpgradeNode.ROOT, id * 20, 0, UpgradeNode.Kind.PASSIVE);
        return new Entry(node, "", "", "", List.of(new UpgradeEffect(Kind.CHANNELS, Target.ALL, channels)));
    }

    private static UpgradeCatalog catalog(int initial, int maximum) {
        var starter = UpgradeNode.CHANNELS.withCostAndPosition(2, 40, 0);
        var base = new Definition(maximum, 7, null, List.of(new Entry(starter, "", "", "")));
        return new UpgradeCatalog(new Definition(maximum, base.nextId(), null, base.nodes(), base.paths(),
                base.experimental(), base.nodeSettings(), base.core(), initial, true));
    }
}
