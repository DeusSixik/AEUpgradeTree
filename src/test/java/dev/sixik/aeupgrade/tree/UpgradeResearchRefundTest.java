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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UpgradeResearchRefundTest {
    @ParameterizedTest
    @CsvSource({"0, 0, 0", "33, 1, 2", "50, 2, 3", "100, 5, 7"})
    void refund_sharedPercentageRoundsDownForResearchAndCertificates(int percent, int refund, int certificates) {
        var catalog = catalog(4, percent);
        var plan = UpgradePlan.empty(catalog).grant(7).purchase(catalog.node(7)).purchase(catalog.node(8));
        assertEquals(certificates, plan.recoveredCertificates(0));
        var result = plan.refund(UpgradeNode.ROOT);
        assertEquals(List.of(7, 8), result.removedIds());
        assertEquals(refund, result.returned());
        assertEquals(5 - refund, result.lost());
        assertEquals(6 + refund, result.plan().remaining());
        assertEquals(result.plan().remaining(), result.plan().total());
        assertSame(catalog.coreEffects(), result.plan().effects());
    }

    @Test
    void refund_singleNodeDropsOnlyPurchasedDependentsWithoutAnotherSufficientInput() {
        var catalog = catalog(20, 50);
        var plan = UpgradePlan.restore(List.of(7, 8, 9, 10), catalog);
        var result = plan.refund(7);

        assertEquals(List.of(7, 8), result.removedIds());
        assertEquals(List.of(9, 10), result.plan().ids());
        assertEquals(2, result.returned());
        assertEquals(3, result.lost());
        assertEquals(13, result.plan().remaining());
        assertSame(result.plan(), result.plan().refund(7).plan());
        assertSame(result.plan(), result.plan().refund(-2).plan());
    }

    @Test
    void recovery_freePointsNeverDropAndRetunedStartingPointsDoNotRewriteTheirOrigin() {
        var catalog = catalog(4, 100);
        var initial = UpgradePlan.empty(catalog);
        var earned = initial.grant(7).purchase(catalog.node(7));

        assertEquals(0, initial.recoveredCertificates(0));
        assertEquals(7, earned.recoveredCertificates(0));
        var adjusted = earned.update(catalog(0, 100));
        assertEquals(7, adjusted.recoveredCertificates(0));
        var restored = UpgradePlan.restore(adjusted.savedIds(), catalog(20, 100), adjusted.total(), adjusted.startingAllowance());
        assertEquals(7, restored.recoveredCertificates(0));
        assertEquals(0, UpgradePlan.migrate(UpgradePlan.restore(new int[] {7}, catalog)).recoveredCertificates(0));
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "33, 3", "50, 5", "100, 10"})
    void recovery_carriedPointsUseCurrentRefundRate(int percent, int certificates) {
        var catalog = catalog(4, percent);
        var plan = UpgradePlan.empty(catalog).grant(7).purchase(catalog.node(7));

        assertEquals(certificates, plan.recoveredCertificates(3));
    }

    @Test
    void config_missingPercentageDefaultsToHalfAndOutOfRangeValuesFailValidation() {
        var legacy = new Gson().fromJson("{\"budget\":36,\"nextId\":7,\"nodes\":[]}", Definition.class);
        assertEquals(50, new UpgradeCatalog(legacy).definition().refundPercent());
        assertThrows(IllegalArgumentException.class, () -> catalog(4, -1));
        assertThrows(IllegalArgumentException.class, () -> catalog(4, 101));
    }

    @Test
    void recovery_disabledInConfigSuppressesCertificatesAndKeepsRespecRefund() {
        var gson = new Gson();
        var legacy = gson.fromJson("{\"budget\":36,\"nextId\":7,\"nodes\":[]}", Definition.class);
        assertTrue(new UpgradeCatalog(legacy).definition().dropResearchOnBreak());
        var base = catalog(4, 50).definition();
        var draft = new Definition(base.budget(), base.nextId(), null, base.nodes(), base.paths(),
                base.experimental(), base.nodeSettings(), base.core(), base.startingPoints(), base.operatorOnly(),
                base.refundPercent(), false);
        var serialized = gson.toJson(new UpgradeCatalog(draft).definition());
        var disabled = new UpgradeCatalog(gson.fromJson(serialized, Definition.class));
        assertFalse(disabled.definition().dropResearchOnBreak());

        var plan = UpgradePlan.empty(disabled).grant(7).purchase(disabled.node(7));
        assertEquals(0, plan.recoveredCertificates(3));
        assertEquals(1, plan.refund(UpgradeNode.ROOT).returned());
    }

    private static UpgradeCatalog catalog(int initial, int percent) {
        var child = node(8, 7, 3);
        var joined = node(10, 7, 3).withPrerequisites(List.of(7, 9), 1);
        var base = new Definition(30, 11, null, List.of(
                entry(node(7, UpgradeNode.ROOT, 2)), entry(child), entry(node(9, UpgradeNode.ROOT, 1)), entry(joined)));
        return new UpgradeCatalog(new Definition(base.budget(), base.nextId(), null, base.nodes(), base.paths(),
                base.experimental(), base.nodeSettings(), base.core(), initial, true, percent));
    }

    private static Entry entry(UpgradeNode node) {
        return new Entry(node, "", "", "", List.of());
    }

    private static UpgradeNode node(int id, int previous, int cost) {
        return new UpgradeNode(id, "refund." + id, cost, 0, UpgradeNode.Sector.NETWORK, null, previous,
                UpgradeNode.ROOT, id * 20, 0, UpgradeNode.Kind.PASSIVE);
    }
}
