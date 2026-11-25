package dev.sixik.aeupgrade.client.tree.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import dev.sixik.aeupgrade.client.tree.map.TreeLinkEffects.Effect;
import dev.sixik.aeupgrade.tree.UpgradeCatalog;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Core;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute.Point;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute.Style;
import dev.sixik.aeupgrade.tree.UpgradeNode;
import dev.sixik.aeupgrade.tree.UpgradeNodeStyle.Shape;
import dev.sixik.aeupgrade.tree.UpgradePlan;
import dev.sixik.aeupgrade.tree.UpgradePlan.Availability;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TreeLinkEffectsTest {
    @ParameterizedTest
    @CsvSource({
            "UNLOCKED, UNLOCKED, CURRENT",
            "UNLOCKED, AVAILABLE, NONE",
            "AVAILABLE, UNLOCKED, NONE",
            "UNLOCKED, EXCLUSIVE, BREAKER",
            "PREREQUISITE, EXCLUSIVE, BREAKER",
            "EXCLUSIVE, EXCLUSIVE, NONE",
            "EXCLUSIVE, AVAILABLE, NONE"
    })
    void effectFor_linkStatuses_selectCurrentOrBranchEntrance(Availability from, Availability to, Effect expected) {
        assertEquals(expected, TreeLinkEffects.effectFor(from, to));
    }

    @Test
    void breakerDistance_wideGap_leavesRoomForThePulseToReachTheWall() {
        assertEquals(42.2f, TreeLinkEffects.breakerDistance(line(100, 0), Shape.CIRCLE, 10, Shape.CIRCLE, 20),
                Math.ulp(42.2f));
        assertEquals(54f, TreeLinkEffects.breakerDistance(line(1000, 0), Shape.CIRCLE, 10, Shape.CIRCLE, 20), 0f);
    }

    @Test
    void breakerDistance_diagonalNarrowGap_placesWallBetweenTheActualOutlines() {
        float expected = (float) (9.92 * Math.sqrt(2));
        assertEquals(expected,
                TreeLinkEffects.breakerDistance(line(14, 14), Shape.SQUARE, 9, Shape.DIAMOND, 6), 4 * Math.ulp(expected));
    }

    @Test
    void breakerDistance_overlappingOrCoincidentNodes_hasNoExposedGap() {
        assertEquals(0, TreeLinkEffects.breakerDistance(line(10, 0), Shape.CIRCLE, 6, Shape.CIRCLE, 6), 0f);
        assertEquals(0, TreeLinkEffects.breakerDistance(line(0, 0), Shape.CIRCLE, 6, Shape.CIRCLE, 6), 0f);
    }

    @Test
    void flowDistances_mergingPaths_shareArrivalAndUpdateAfterReset() {
        var graph = new UpgradeCatalog(new UpgradeCatalog.Definition(10, 109, null, List.of(
                entry(108, 106, UpgradeNode.ROOT, 18, 0),
                entry(106, 104, 105, 12, 0),
                entry(105, 103, UpgradeNode.ROOT, 9, 8),
                entry(104, 103, UpgradeNode.ROOT, 9, 0),
                entry(103, UpgradeNode.ROOT, UpgradeNode.ROOT, 3, 0)), List.of()));

        var afterFork = UpgradePlan.restore(List.of(103, 104, 106, 108), graph);
        assertEquals(Map.of(-1, 0f, 103, 3f, 104, 9f, 106, 12f, 108, 18f),
                TreeLinkEffects.flowDistances(graph, afterFork));

        var afterMerge = UpgradePlan.restore(List.of(103, 104, 105, 106, 108), graph);
        var distances = TreeLinkEffects.flowDistances(graph, afterMerge);
        assertEquals(13f, distances.get(105), 0f);
        assertEquals(13f + (float) Math.hypot(3, 8), distances.get(106), 0f);
        assertEquals(distances.get(106) + 6f, distances.get(108), 0f);
        assertEquals(Map.of(-1, 0f), TreeLinkEffects.flowDistances(graph, UpgradePlan.restore(List.of(), graph)));
    }

    @Test
    void flowDistances_routedLink_arrivesUsingItsFullLength() {
        var origin = entry(103, UpgradeNode.ROOT, UpgradeNode.ROOT, 10, 0);
        var target = entry(104, 103, UpgradeNode.ROOT, 20, 20);
        target = target.withNode(target.node().withLinkRoute(103, new UpgradeLinkRoute(103, Style.ORTHOGONAL,
                List.of(new Point(40, 0)))));
        var catalog = new UpgradeCatalog(new UpgradeCatalog.Definition(10, 105, null, List.of(origin, target)));
        var purchased = UpgradePlan.restore(List.of(103, 104), catalog);
        assertEquals(80f, TreeLinkEffects.flowDistances(catalog, purchased).get(104), 0f);
    }

    @Test
    void flowDistances_movedCore_usesTheNewLinkOrigin() {
        var starter = entry(103, UpgradeNode.ROOT, UpgradeNode.ROOT, 40, 50);
        var base = new UpgradeCatalog.Definition(10, 105, null, List.of(starter));
        var core = new Core("", "", "", List.of(), 10, 10, null);
        var catalog = new UpgradeCatalog(new UpgradeCatalog.Definition(base.budget(), base.nextId(), null, base.nodes(),
                base.paths(), base.experimental(), base.nodeSettings(), core));
        var purchased = UpgradePlan.restore(List.of(103), catalog);
        assertEquals(50f, TreeLinkEffects.flowDistances(catalog, purchased).get(103), 0f);
    }

    private static TreeLinkPath line(float x, float y) {
        return TreeLinkPath.build(new Point(0, 0), new Point(x, y), null);
    }

    private static UpgradeCatalog.Entry entry(int id, int previous, int alternative, float x, float y) {
        var node = new UpgradeNode(id, "test." + id, 1, 0, UpgradeNode.Sector.NETWORK, null,
                previous, alternative, x, y, UpgradeNode.Kind.PASSIVE);
        return new UpgradeCatalog.Entry(node, "", "", "");
    }
}
