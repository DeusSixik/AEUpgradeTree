package dev.sixik.aeupgrade.client.tree.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import dev.sixik.aeupgrade.client.tree.editor.UpgradeEditorCanvas.LinkId;
import dev.sixik.aeupgrade.client.tree.editor.UpgradeEditorCanvas.LinkSelection;
import dev.sixik.aeupgrade.client.tree.editor.UpgradeEditorHistory.Draft;
import dev.sixik.aeupgrade.tree.UpgradeCatalog;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Definition;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Entry;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.ExperimentalSettings;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.NodeSettings;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute.Point;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute.Style;
import dev.sixik.aeupgrade.tree.UpgradeNode;
import dev.sixik.aeupgrade.tree.UpgradeTreeExamples;
import org.junit.jupiter.api.Test;

class UpgradeEditorHistoryTest {
    @Test
    void deleteSelection_linkedNodes_repairsReferencesAndRestoresThemOnUndo() {
        var deleted = List.of(0, 1);
        var original = new Draft("40", List.of(
                entry(0, -1, -1), entry(1, 0, -1), entry(2, -1, -1),
                entry(3, 0, 2).withBranches(List.of(1, 2)), entry(4, 1, 0), entry(5, 2, 1)), deleted, 1,
                new ExperimentalSettings(false, true));
        var after = original.deleteSelection();
        var catalog = new UpgradeCatalog(new Definition(40, 7, null, after.entries()));

        assertEquals(List.of(2, 3, 4, 5), after.entries().stream().map(entry -> entry.node().id()).toList());
        assertEquals(2, catalog.node(3).previous());
        assertEquals(UpgradeNode.ROOT, catalog.node(3).alternative());
        assertEquals(List.of(2), catalog.entry(3).branches());
        assertEquals(UpgradeNode.DISCONNECTED, catalog.node(4).previous());
        assertEquals(2, catalog.node(5).previous());
        assertEquals(UpgradeNode.ROOT, catalog.node(5).alternative());
        assertEquals(List.of(), after.selection());
        assertEquals(original.experimental(), after.experimental());

        var history = new UpgradeEditorHistory();
        history.record(original, after);
        assertEquals(original, history.undo());
        assertEquals(after, history.redo());
    }

    @Test
    void record_selectionOnly_preservesRedoUntilTheDraftChanges() {
        var history = new UpgradeEditorHistory();
        var effects = ExperimentalSettings.DEFAULT;
        var first = new Draft("40", List.of(entry(0, -1, -1)), List.of(0), 0, effects);
        var second = new Draft("50", first.entries(), first.selection(), 0, effects);
        var deselected = new Draft("40", first.entries(), List.of(), UpgradeNode.DISCONNECTED, effects);
        var third = new Draft("60", first.entries(), deselected.selection(), UpgradeNode.DISCONNECTED, effects);

        history.record(first, second);
        assertEquals(first, history.undo());
        history.record(first, deselected);
        assertEquals(second, history.redo());
        assertEquals(first, history.undo());
        history.record(deselected, third);
        assertNull(history.redo());
        assertEquals(deselected, history.undo());
        assertNull(history.undo());
    }

    @Test
    void deleteSelection_thresholdInputs_reduceTheThresholdAndPreserveItOnUndo() {
        var original = new Draft("36", UpgradeTreeExamples.at(102, 560, 0), List.of(111, 112), 111,
                ExperimentalSettings.DEFAULT);
        var deleted = original.deleteSelection();
        var junction = deleted.entries().getLast().node();
        assertEquals(List.of(113, 114), junction.prerequisiteIds());
        assertEquals(2, junction.requiredCount());

        var history = new UpgradeEditorHistory();
        history.record(original, deleted);
        var restored = history.undo().entries().getLast().node();
        assertEquals(3, restored.requiredCount());
        assertTrue(restored.locksRemaining());
    }

    @Test
    void record_experimentalToggle_restoresOptionsOnUndoAndRedo() {
        var first = new Draft("40", List.of(entry(0, -1, -1)), List.of(0), 0, ExperimentalSettings.DEFAULT);
        var second = new Draft("40", first.entries(), first.selection(), 0, new ExperimentalSettings(true, false));
        var history = new UpgradeEditorHistory();

        history.record(first, second);

        assertEquals(first, history.undo());
        assertEquals(second, history.redo());
    }

    @Test
    void record_requirementsToggle_preservesItThroughDeletionAndHistory() {
        var entries = List.of(entry(0, UpgradeNode.ROOT, UpgradeNode.ROOT));
        var before = new Draft("40", entries, List.of(0), 0, ExperimentalSettings.DEFAULT);
        var hidden = new NodeSettings(false);
        var after = new Draft("40", entries, List.of(0), 0, ExperimentalSettings.DEFAULT, hidden, null);
        var history = new UpgradeEditorHistory();

        history.record(before, after);

        assertEquals(before, history.undo());
        assertEquals(after, history.redo());
        assertEquals(hidden, after.deleteSelection().nodeSettings());
    }

    @Test
    void record_researchDropToggle_preservesItThroughDeletionAndHistory() {
        var entries = List.of(entry(0, UpgradeNode.ROOT, UpgradeNode.ROOT));
        var before = new Draft("40", entries, List.of(0), 0, ExperimentalSettings.DEFAULT);
        var after = new Draft(before.budget(), entries, before.selection(), before.inspectedId(),
                before.experimental(), before.nodeSettings(), null, before.startingPoints(), before.operatorOnly(),
                before.refundPercent(), false);
        var history = new UpgradeEditorHistory();

        history.record(before, after);

        assertTrue(history.undo().dropResearchOnBreak());
        assertFalse(history.redo().dropResearchOnBreak());
        assertFalse(after.deleteSelection().dropResearchOnBreak());
    }

    @Test
    void record_editingAndReorderingWaypoints_restoresRouteAndInspectedPoint() {
        var id = new LinkId(0, 1);
        var parent = entry(0, UpgradeNode.ROOT, UpgradeNode.ROOT);
        var child = entry(1, 0, UpgradeNode.ROOT);
        var original = new UpgradeLinkRoute(0, Style.POLYLINE, List.of(new Point(10, 5), new Point(30, 40)));
        var updated = new UpgradeLinkRoute(0, Style.ROUNDED, List.of(new Point(30, 40), new Point(10, -7)));
        var before = new Draft("36", List.of(parent, child.withNode(child.node().withLinkRoute(0, original))),
                List.of(), UpgradeNode.DISCONNECTED, ExperimentalSettings.DEFAULT, NodeSettings.DEFAULT, new LinkSelection(id, 0));
        var after = new Draft("36", List.of(parent, child.withNode(child.node().withLinkRoute(0, updated))),
                List.of(), UpgradeNode.DISCONNECTED, ExperimentalSettings.DEFAULT, NodeSettings.DEFAULT, new LinkSelection(id, 1));
        var history = new UpgradeEditorHistory();
        history.record(before, after);

        assertEquals(before, history.undo());
        assertEquals(after, history.redo());
    }

    private static Entry entry(int id, int previous, int alternative) {
        var node = new UpgradeNode(id, UpgradeNode.byId(id).key(), 1, 0, UpgradeNode.Sector.NETWORK, null,
                previous, alternative, 0, 0, UpgradeNode.Kind.PASSIVE);
        return new Entry(node, "", "", "", List.of(), null, null, List.of());
    }
}
