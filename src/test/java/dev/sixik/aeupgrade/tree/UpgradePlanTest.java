package dev.sixik.aeupgrade.tree;

import static dev.sixik.aeupgrade.tree.UpgradeNode.BATCH;
import static dev.sixik.aeupgrade.tree.UpgradeNode.CHANNELS;
import static dev.sixik.aeupgrade.tree.UpgradeNode.CRAFT_ENERGY;
import static dev.sixik.aeupgrade.tree.UpgradeNode.IDLE;
import static dev.sixik.aeupgrade.tree.UpgradeNode.OVERDRIVE;
import static dev.sixik.aeupgrade.tree.UpgradeNode.PATTERNS;
import static dev.sixik.aeupgrade.tree.UpgradeNode.SPEED;
import static dev.sixik.aeupgrade.tree.UpgradePlan.Availability.AVAILABLE;
import static dev.sixik.aeupgrade.tree.UpgradePlan.Availability.EXCLUSIVE;
import static dev.sixik.aeupgrade.tree.UpgradePlan.Availability.PREREQUISITE;
import static dev.sixik.aeupgrade.tree.UpgradePlan.Availability.UNLOCKED;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import dev.sixik.aeupgrade.tree.UpgradeNode.Route;
import org.junit.jupiter.api.Test;

/** Tests the catalog, route restrictions, legacy migration and point allocation. */
class UpgradePlanTest {
    /** All prerequisites are present before their dependents and each sector has two eight-node routes. */
    @Test
    void catalog_hasSixCompleteJunctions() {
        var nodes = UpgradeNode.all();
        assertEquals(102, nodes.size());
        assertEquals(102L, nodes.stream().map(UpgradeNode::key).distinct().count());
        assertEquals(7L, nodes.stream().filter(UpgradeNode::legacyEffectNode).count());
        assertEquals(6L, nodes.stream().filter(node -> node.previous() == UpgradeNode.ROOT).count());
        for (var node : nodes) {
            assertSame(node, UpgradeNode.byId(node.id()));
            assertTrue(node.previous() < node.id() && node.alternative() < node.id());
        }
        for (var route : Route.values()) {
            assertEquals(8, routeNodes(route).size());
            assertSame(route, route.opposite().opposite());
        }
    }

    /** The server requires a connected starter and charges only the first purchase. */
    @Test
    void purchase_missingPrerequisiteOrDuplicate_doesNotSpendPoints() {
        var empty = UpgradePlan.EMPTY;
        assertEquals(PREREQUISITE, empty.availability(PATTERNS));
        assertSame(empty, empty.purchase(PATTERNS));
        var plan = empty.purchase(CHANNELS).purchase(PATTERNS);
        assertEquals(UNLOCKED, plan.availability(PATTERNS));
        assertEquals(UpgradePlan.BUDGET - 3, plan.remaining());
        assertSame(plan, plan.purchase(PATTERNS));
    }

    /** Every node in either opposing route closes immediately when the other entry is purchased. */
    @Test
    void availability_choice_closesTheEntireOppositeRoute() {
        for (var route : Route.values()) {
            var entry = routeNodes(route).getFirst();
            var starter = UpgradeNode.byId(entry.previous());
            var plan = UpgradePlan.EMPTY.purchase(starter).purchase(entry);
            for (var blocked : routeNodes(route.opposite())) {
                assertEquals(EXCLUSIVE, plan.availability(blocked), blocked.key());
                assertSame(plan, plan.purchase(blocked));
            }
        }
    }

    /** Independent sectors still admit choices after a purchase and reset reopens both paths. */
    @Test
    void availability_separateJunctions_areIndependent() {
        var plan = UpgradePlan.EMPTY.purchase(CHANNELS).purchase(PATTERNS).purchase(SPEED).purchase(IDLE);
        assertEquals(AVAILABLE, plan.availability(BATCH));
        assertEquals(AVAILABLE, plan.availability(CRAFT_ENERGY));
        var production = plan.purchase(BATCH);
        assertEquals(EXCLUSIVE, production.availability(OVERDRIVE));
        var reset = UpgradePlan.EMPTY.purchase(SPEED);
        assertEquals(AVAILABLE, reset.availability(BATCH));
        assertEquals(AVAILABLE, reset.availability(OVERDRIVE));
    }

    /** Either side of each diamond is sufficient, including after replaying high-numbered saved IDs. */
    @Test
    void restore_eitherDiamondPath_keepsPurchasesBeyondOldMask() {
        var route = routeNodes(Route.DIAGNOSTICS);
        var entry = route.getFirst();
        var plan = UpgradePlan.EMPTY.purchase(UpgradeNode.byId(entry.previous())).purchase(entry).purchase(route.get(1))
                .purchase(route.get(3)).purchase(route.get(4)).purchase(route.get(6)).purchase(route.get(7));
        assertTrue(plan.unlocked(route.getLast()));
        assertTrue(route.getLast().id() >= 96);
        var restored = UpgradePlan.restore(plan.savedIds());
        assertArrayEquals(plan.savedIds(), restored.savedIds());
        assertEquals(plan.remaining(), restored.remaining());
        assertFalse(restored.unlocked(route.get(2)));
        assertEquals(EXCLUSIVE, restored.availability(routeNodes(Route.REDUNDANCY).getLast()));
        assertEquals(plan.ids(), UpgradePlan.restore(plan.ids()).ids());
    }

    /** Unknown, duplicate, orphaned and conflicting node IDs do not corrupt the allocation. */
    @Test
    void restore_invalidIds_dropsOnlyInvalidPurchases() {
        var plan = UpgradePlan.restore(new int[] {999, -1, SPEED.id(), OVERDRIVE.id(), BATCH.id(), BATCH.id(),
                routeNodes(Route.DIAGNOSTICS).getLast().id()});
        assertEquals(List.of(SPEED.id(), BATCH.id()), plan.ids());
        assertEquals(UpgradePlan.BUDGET - 3, plan.remaining());
    }

    /** Old valid allocations migrate with their original functional modifiers. */
    @Test
    void fromLegacyMask_preservesPurchasedFunctionalNodes() {
        var migrated = UpgradePlan.fromLegacyMask(1 | 4 | 16 | 64 | (1 << 31));
        assertEquals(List.of(CHANNELS.id(), SPEED.id(), IDLE.id(), OVERDRIVE.id()), migrated.ids());
        assertEquals(50, migrated.speedBonus());
        assertEquals(EXCLUSIVE, migrated.availability(BATCH));
        assertFalse(UpgradePlan.fromLegacyMask(1 << PATTERNS.id()).unlocked(PATTERNS));
    }

    /** A replay cannot exceed the point budget even when given every catalog node. */
    @Test
    void restore_fullCatalog_capsPurchasesAtBudget() {
        var plan = UpgradePlan.restore(UpgradeNode.all().stream().map(UpgradeNode::id).toList());
        assertEquals(0, plan.remaining());
        assertEquals(UpgradePlan.BUDGET,
                plan.ids().stream().mapToInt(id -> UpgradeNode.byId(id).cost()).sum());
        assertEquals(UpgradePlan.BUDGET, UpgradePlan.EMPTY.remaining());
    }

    /** Overdrive replaces the speed amount while preserving a configured stricter assembly limit. */
    @Test
    void overdrive_effects_applyAndClearWithPurchases() {
        var empty = UpgradePlan.EMPTY;
        var speed = empty.purchase(SPEED);
        var overdrive = speed.purchase(OVERDRIVE);
        assertEquals(0, empty.speedBonus());
        assertEquals(20, speed.speedBonus());
        assertEquals(50, overdrive.speedBonus());
        assertEquals(16, overdrive.assemblerLimit(-1));
        assertEquals(16, overdrive.assemblerLimit(32));
        assertEquals(8, overdrive.assemblerLimit(8));
        assertEquals(0, overdrive.assemblerLimit(0));
        assertEquals(-1, empty.assemblerLimit(-1));
        assertEquals(32, speed.assemblerLimit(32));
    }

    /** @param route route to find
     *  @return its nodes in catalog order */
    private static List<UpgradeNode> routeNodes(Route route) {
        return UpgradeNode.all().stream().filter(node -> node.route() == route).toList();
    }
}
