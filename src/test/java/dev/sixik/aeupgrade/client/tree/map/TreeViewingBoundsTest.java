package dev.sixik.aeupgrade.client.tree.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import dev.sixik.aeupgrade.client.tree.UpgradeTreeVisibility;
import dev.sixik.aeupgrade.tree.UpgradeCatalog;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Core;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Definition;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Entry;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Reveal;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute.Point;
import dev.sixik.aeupgrade.tree.UpgradeNode;
import dev.sixik.aeupgrade.tree.UpgradeNodeStyle;
import dev.sixik.aeupgrade.tree.UpgradeNodeStyle.Shape;
import dev.sixik.aeupgrade.tree.UpgradePlan;
import dev.sixik.unigui.api.viewport.ViewportBounds;
import dev.sixik.unigui.api.viewport.Viewport2D;
import org.junit.jupiter.api.Test;

class TreeViewingBoundsTest {
    // Bounds are stored as origin and span, so recovering the end can involve float rounding.
    private static final float EPSILON = 0.0001f;

    @Test
    void viewingBounds_emptyTree_includesCoreAndMargin() {
        var bounds = bounds(List.of());
        float edge = 39 / (2 * TreeCanvas.REFERENCE_ZOOM) + 44;
        assertEquals(-edge, bounds.x(), EPSILON);
        assertEquals(edge, bounds.right(), EPSILON);
        assertEquals(-edge, bounds.y(), EPSILON);
        assertEquals(edge, bounds.bottom(), EPSILON);
    }

    @Test
    void viewingBounds_movedAndResizedCore_usesItsSavedPositionAndSize() {
        var core = new Core("", "", "", List.of(), 500, -270, new UpgradeNodeStyle(70, Shape.SQUARE, null, List.of()));
        var base = new Definition(36, 7, null, List.of());
        var catalog = new UpgradeCatalog(new Definition(base.budget(), base.nextId(), null, base.nodes(), base.paths(),
                base.experimental(), base.nodeSettings(), core));
        var visibility = new UpgradeTreeVisibility(catalog, UpgradePlan.restore(List.of(), catalog));
        var bounds = TreeCanvas.viewingBounds(catalog.definition(), visibility);
        float edge = 70 / (2 * TreeCanvas.REFERENCE_ZOOM) + 44;
        assertEquals(500 - edge, bounds.x(), EPSILON);
        assertEquals(-270 + edge, bounds.bottom(), EPSILON);
    }

    @Test
    void viewingBounds_asymmetricTree_usesOuterNodeEdgesAndPadding() {
        var node = new UpgradeNode(0, "channels", 1, 0, UpgradeNode.Sector.NETWORK, null,
                UpgradeNode.ROOT, UpgradeNode.ROOT, 240, -120, UpgradeNode.Kind.STARTER);
        var entry = new Entry(node, "", "", "");
        var bounds = bounds(List.of(entry));
        float margin = entry.appearance().size() / (2 * TreeCanvas.REFERENCE_ZOOM) + 44;
        var core = bounds(List.of());
        assertEquals(core.x(), bounds.x(), EPSILON);
        assertEquals(-120 - margin, bounds.y(), EPSILON);
        assertEquals(240 + margin, bounds.right(), EPSILON);
        assertEquals(core.bottom(), bounds.bottom(), EPSILON);

        var camera = new Viewport2D().worldBounds(bounds.x(), bounds.y(), bounds.width(), bounds.height())
                .clampToWorldBounds(true).zoom(2).position(-1000, 1000);
        camera.clamp(180, 160);
        assertEquals(bounds.right(), camera.screenToWorldX(180), EPSILON);
        assertEquals(bounds.y(), camera.screenToWorldY(0), EPSILON);
    }

    @Test
    void viewingBounds_hiddenNodesAndIncomingRoutes_doNotExpandMap() {
        var root = UpgradeNode.CHANNELS.withCostAndPosition(1, 0, 0);
        var gate = new Entry(root, "", "", "", List.of(), null, null, List.of(), new Reveal(true, false));
        var alternate = new Entry(UpgradeNode.SPEED.withCostAndPosition(1, 0, 20), "", "", "");
        var join = UpgradeNode.PATTERNS.withCostAndPosition(1, 30, 0)
                .withPrerequisites(List.of(root.id(), alternate.node().id()), 1)
                .withLinkRoute(root.id(), new UpgradeLinkRoute(root.id(), UpgradeLinkRoute.Style.POLYLINE,
                        List.of(new Point(1700, 0))));
        var hidden = UpgradeNode.BATCH.withCostAndPosition(1, 1300, 0)
                .withPrerequisites(List.of(root.id()), 1);
        var entries = List.of(gate, alternate, new Entry(join, "", "", ""), new Entry(hidden, "", "", ""));
        var catalog = new UpgradeCatalog(new Definition(36, 7, null, entries));
        var concealed = new UpgradeTreeVisibility(catalog, UpgradePlan.restore(List.of(), catalog));
        var revealed = new UpgradeTreeVisibility(catalog, UpgradePlan.restore(List.of(root.id()), catalog));

        assertEquals(bounds(entries.subList(0, 2)).x(), TreeCanvas.viewingBounds(catalog.definition(), concealed).x(), EPSILON);
        float joinRadius = entries.get(2).appearance().size() / (2 * TreeCanvas.REFERENCE_ZOOM);
        assertEquals(30 + joinRadius + 44,
                TreeCanvas.viewingBounds(catalog.definition(), concealed).right(), EPSILON);
        assertEquals(1744, TreeCanvas.viewingBounds(catalog.definition(), revealed).right(), EPSILON);
    }

    private static ViewportBounds bounds(List<Entry> entries) {
        var catalog = new UpgradeCatalog(new Definition(36, 7, null, entries));
        return TreeCanvas.viewingBounds(catalog.definition(),
                new UpgradeTreeVisibility(catalog, UpgradePlan.restore(List.of(), catalog)));
    }
}
