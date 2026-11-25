package dev.sixik.aeupgrade.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import com.google.gson.GsonBuilder;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Definition;
import dev.sixik.aeupgrade.tree.UpgradeEffect;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Impact;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Kind;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Target;
import dev.sixik.aeupgrade.tree.UpgradeNode.Sector;
import dev.sixik.aeupgrade.tree.UpgradePlan.Availability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class BundledUpgradeTreeTest {
    @Test
    void definition_hasSixSectorsAndEffectsOnEveryUpgrade() throws IOException {
        var catalog = bundled();
        var definition = catalog.definition();
        assertTrue(definition.nodes().size() >= 400);
        assertEquals(Sector.values().length, catalog.ordered().stream().filter(UpgradeNode::connectsToCore).count());
        assertFalse(catalog.ordered().stream().anyMatch(UpgradeNode::disconnected));
        assertTrue(definition.nodes().stream().allMatch(entry -> !entry.effects().isEmpty()
                && entry.effects().stream().allMatch(effect -> effect.value() != 0)));
        var json = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(definition);
        assertTrue(json.getBytes(StandardCharsets.UTF_8).length + 128 * definition.nodes().size()
                < UpgradeTreeConfig.MAX_BYTES);
    }

    @Test
    void startingBonuses_applyWhenTheirNodesArePurchased() throws IOException {
        var catalog = bundled();
        var unspent = UpgradePlan.empty(catalog).grant(1);

        assertStarterBonus(catalog, unspent, Sector.NETWORK, Kind.CHANNELS, 1);
        assertStarterBonus(catalog, unspent, Sector.CRAFTING, Kind.SPEED_PERCENT, 5);
        assertStarterBonus(catalog, unspent, Sector.POWER, Kind.IDLE_PERCENT, -5);
        assertStarterBonus(catalog, unspent, Sector.STORAGE, Kind.INTERFACE_SLOTS, 1);
        assertStarterBonus(catalog, unspent, Sector.LOGISTICS, Kind.THROUGHPUT, 4);
        assertStarterBonus(catalog, unspent, Sector.RESILIENCE, Kind.CRAFT_PERCENT, -5);
    }

    private static void assertStarterBonus(UpgradeCatalog catalog, UpgradePlan unspent, Sector sector,
            Kind kind, int amount) {
        var starter = node(catalog, sector.key() + ".entry");
        assertEquals(Availability.AVAILABLE, unspent.availability(starter));
        assertEquals(0, unspent.effects().amount(kind), 0);
        assertEquals(amount, unspent.purchase(starter).effects().amount(kind), 0);
    }

    @Test
    void innerRing_connectsNeighboringSectorWithoutItsStarter() throws IOException {
        var catalog = bundled();
        var crossing = node(catalog, "network.inner_ring.0.hub");
        var plan = unlock(catalog, UpgradePlan.empty(catalog).grant(catalog.budget()), crossing);

        assertFalse(plan.unlocked(node(catalog, "crafting.entry")));
        assertEquals(Availability.AVAILABLE, plan.availability(node(catalog, "crafting.hub")));
    }

    @ParameterizedTest
    @CsvSource({
            "NETWORK, PATTERN_SLOTS, ALL, CHANNELS, ALL",
            "CRAFTING, BATCH, ALL, SPEED_PERCENT, ALL",
            "POWER, IDLE_PERCENT, ALL, CRAFT_PERCENT, ALL",
            "STORAGE, INTERFACE_SLOTS, ALL, CHANNEL_COST, INTERFACE",
            "LOGISTICS, PATTERN_SLOTS, ALL, THROUGHPUT, ALL",
            "RESILIENCE, CRAFT_PERCENT, ALL, CHANNEL_COST, PATTERN_PROVIDER"
    })
    void forks_specializeInDifferentStats(Sector sector, Kind leftKind, Target leftTarget, Kind rightKind,
            Target rightTarget) throws IOException {
        var catalog = bundled();
        var unspent = UpgradePlan.empty(catalog).grant(catalog.budget());
        var left = unlock(catalog, unspent, node(catalog, sector.key() + ".left")).effects();
        var right = unlock(catalog, unspent, node(catalog, sector.key() + ".right")).effects();

        double leftAdvantage = left.amount(leftKind, leftTarget) - right.amount(leftKind, leftTarget);
        double rightAdvantage = right.amount(rightKind, rightTarget) - left.amount(rightKind, rightTarget);
        assertEquals(Impact.BENEFIT, new UpgradeEffect(leftKind, leftTarget, leftAdvantage).impact());
        assertEquals(Impact.BENEFIT, new UpgradeEffect(rightKind, rightTarget, rightAdvantage).impact());
    }

    @ParameterizedTest
    @CsvSource({"storage, INTERFACE, 24, 30", "resilience, PATTERN_PROVIDER, 20, 26"})
    void compactBranches_reduceChannelCostAndRaiseTheirQuotaOnTheWayOut(String sector, Target target,
            int initialQuota, int finalQuota) throws IOException {
        var catalog = bundled();
        var plan = unlock(catalog, UpgradePlan.empty(catalog).grant(catalog.budget()), node(catalog, sector + ".right"));
        assertEquals(-1, plan.effects().amount(Kind.CHANNEL_COST, target), 0);
        assertEquals(initialQuota, plan.effects().deviceLimit(target, -1));

        plan = unlock(catalog, plan, node(catalog, sector + ".right.out"));
        assertEquals(finalQuota, plan.effects().deviceLimit(target, -1));
    }

    @Test
    void unlockAll_keepsPercentageEnergyDrawAboveZero() throws IOException {
        var effects = UpgradePlan.empty(bundled()).unlockAll().effects();
        assertTrue(effects.amount(Kind.CRAFT_PERCENT) > -100);
        for (var target : Target.values()) {
            assertTrue(effects.amount(Kind.IDLE_PERCENT, target) > -100, target.name());
        }
    }

    @Test
    void everySector_reachesItsSummitWithEitherForkAndLocksSpareInputs() throws IOException {
        var catalog = bundled();
        for (var sector : Sector.values()) {
            String key = sector.key();
            var left = node(catalog, key + ".left");
            var right = node(catalog, key + ".right");
            var quota = node(catalog, key + ".quota");
            assertEquals(4, quota.prerequisiteCount());
            assertTrue(quota.locksRemaining());
            for (var branch : List.of(left, right)) {
                var plan = unlock(catalog, UpgradePlan.empty(catalog).grant(catalog.budget()), branch);
                assertEquals(Availability.EXCLUSIVE, plan.availability(branch == left ? right : left));
                plan = unlock(catalog, plan, node(catalog, key + ".crown"));
                assertTrue(plan.unlocked(quota));
                assertEquals(quota.requiredCount(), plan.unlockedPrerequisites(quota));
                for (int id : quota.prerequisiteIds()) {
                    var input = catalog.node(id);
                    if (!plan.unlocked(input)) {
                        assertEquals(Availability.EXCLUSIVE, plan.availability(input));
                    }
                }
            }
        }
    }

    private static UpgradeCatalog bundled() throws IOException {
        try (var resource = BundledUpgradeTreeTest.class.getResourceAsStream("/data/aeupgrade/upgrade_tree.json")) {
            assertNotNull(resource);
            String json = new String(resource.readAllBytes(), StandardCharsets.UTF_8);
            return new UpgradeCatalog(new GsonBuilder().create().fromJson(json, Definition.class));
        }
    }

    private static UpgradeNode node(UpgradeCatalog catalog, String key) {
        return catalog.ordered().stream().filter(node -> node.key().equals("atlas." + key)).findFirst().orElseThrow();
    }

    private static UpgradePlan unlock(UpgradeCatalog catalog, UpgradePlan plan, UpgradeNode node) {
        for (int id : node.prerequisiteIds()) {
            if (plan.availability(node) != Availability.PREREQUISITE) {
                break;
            }
            var input = catalog.node(id);
            if (!plan.unlocked(input) && plan.availability(input) != Availability.EXCLUSIVE) {
                plan = unlock(catalog, plan, input);
            }
        }
        assertEquals(Availability.AVAILABLE, plan.availability(node), node.key());
        return plan.purchase(node);
    }
}
