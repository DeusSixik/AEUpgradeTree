package dev.sixik.aeupgrade.client.tree.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import dev.sixik.aeupgrade.tree.UpgradeCatalog.Entry;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute.Point;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute.Style;
import dev.sixik.aeupgrade.tree.UpgradeNode;
import dev.sixik.aeupgrade.tree.UpgradeNode.Kind;
import dev.sixik.aeupgrade.tree.UpgradeNode.Sector;
import dev.sixik.aeupgrade.tree.UpgradeTreeExamples;
import org.junit.jupiter.api.Test;

class UpgradeTreeClipboardTest {
    @Test
    void paste_group_remapsInternalEdgesAndForksAndPreservesOffsets() {
        var first = entry(3, 1, -1, 20).withBranches(List.of(6, 75));
        var second = entry(6, 2, 3, 60);
        var copies = UpgradeTreeClipboard.paste(List.of(first, second), 102, 3995, 86);
        assertEquals(List.of(103), copies.getFirst().branches());
        assertEquals(UpgradeNode.DISCONNECTED, copies.getFirst().node().previous());
        assertEquals(102, copies.getLast().node().previous());
        assertEquals(UpgradeNode.ROOT, copies.getLast().node().alternative());
        assertEquals(3960f, copies.getFirst().node().x(), Math.ulp(3960f));
        assertEquals(4000f, copies.getLast().node().x(), Math.ulp(4000f));
        assertEquals(86f, copies.getLast().node().y(), Math.ulp(86f));
        assertEquals("@test.title", copies.getLast().title());
    }

    @Test
    void paste_threshold_remapsIncludedInputsAndKeepsTheValidCount() {
        var copied = UpgradeTreeExamples.at(102, 560, 0);

        var full = UpgradeTreeClipboard.paste(copied, 116, 0, 0).getLast().node();
        assertEquals(List.of(125, 126, 127, 128), full.prerequisiteIds());
        assertEquals(3, full.requiredCount());
        assertTrue(full.locksRemaining());

        var partial = UpgradeTreeClipboard.paste(copied.subList(11, 14), 116, 0, 0).getLast().node();
        assertEquals(List.of(116, 117), partial.prerequisiteIds());
        assertEquals(2, partial.requiredCount());
    }

    @Test
    void paste_routedConnection_remapsSourceAndKeepsTheWaypointInsideTheMap() {
        var parent = entry(8, UpgradeNode.ROOT, UpgradeNode.ROOT, 20);
        var child = entry(9, 8, UpgradeNode.ROOT, 60);
        child = child.withNode(child.node().withLinkRoute(8,
                new UpgradeLinkRoute(8, Style.ROUNDED, List.of(new Point(80, 120)))));
        var copy = UpgradeTreeClipboard.paste(List.of(parent, child), 102, 3995, 86).getLast().node();

        assertEquals(new UpgradeLinkRoute(102, Style.ROUNDED, List.of(new Point(4000, 106))), copy.linkRoute(102));
        assertEquals(3980f, copy.x(), 0f);
        assertTrue(UpgradeTreeClipboard.paste(List.of(child), 102, 0, 0).getFirst().node().routes().isEmpty());
    }

    private static Entry entry(int id, int first, int alternate, float x) {
        var node = new UpgradeNode(id, "custom." + id, 1, 0, Sector.NETWORK, null, first, alternate, x, 100, Kind.PASSIVE);
        return new Entry(node, "@test.title", "", "", List.of(), null, null, List.of());
    }
}
