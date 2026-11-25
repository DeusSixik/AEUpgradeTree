package dev.sixik.aeupgrade.client.tree.editor;

import static dev.sixik.aeupgrade.tree.UpgradeCatalog.DEFAULT_REFUND_PERCENT;

import java.util.ArrayDeque;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import dev.sixik.aeupgrade.client.tree.editor.UpgradeEditorCanvas.LinkSelection;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Entry;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.ExperimentalSettings;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.NodeSettings;
import dev.sixik.aeupgrade.tree.UpgradeNode;
import org.jetbrains.annotations.Nullable;

/**
 * Bounded undo and redo history for edits to the current tree draft.
 */
final class UpgradeEditorHistory {
    private static final int LIMIT = 100;
    private final ArrayDeque<Change> undo = new ArrayDeque<>();
    private final ArrayDeque<Change> redo = new ArrayDeque<>();

    void record(Draft before, Draft after) {
        if (before.budget().equals(after.budget()) && before.entries().equals(after.entries())
                && before.experimental().equals(after.experimental()) && before.nodeSettings().equals(after.nodeSettings())
                && before.startingPoints().equals(after.startingPoints()) && before.operatorOnly() == after.operatorOnly()
                && before.refundPercent().equals(after.refundPercent())
                && before.dropResearchOnBreak() == after.dropResearchOnBreak()) {
            return;
        }
        undo.addLast(new Change(before, after));
        if (undo.size() > LIMIT) {
            undo.removeFirst();
        }
        redo.clear();
    }

    @Nullable Draft undo() {
        var change = undo.pollLast();
        if (change == null) {
            return null;
        }
        redo.addLast(change);
        return change.before();
    }

    @Nullable Draft redo() {
        var change = redo.pollLast();
        if (change == null) {
            return null;
        }
        undo.addLast(change);
        return change.after();
    }

    private record Change(Draft before, Draft after) {
    }

    /**
     * Document contents and node or route inspector selection associated with an edit.
     */
    record Draft(String budget, List<Entry> entries, List<Integer> selection, int inspectedId,
                 ExperimentalSettings experimental, NodeSettings nodeSettings, @Nullable LinkSelection inspectedLink,
                 String startingPoints, boolean operatorOnly, String refundPercent, boolean dropResearchOnBreak) {
        Draft {
            entries = List.copyOf(entries);
            selection = List.copyOf(selection);
        }

        Draft(String budget, List<Entry> entries, List<Integer> selection, int inspectedId,
              ExperimentalSettings experimental) {
            this(budget, entries, selection, inspectedId, experimental, NodeSettings.DEFAULT, null);
        }

        Draft(String budget, List<Entry> entries, List<Integer> selection, int inspectedId,
              ExperimentalSettings experimental, NodeSettings nodeSettings, @Nullable LinkSelection inspectedLink) {
            this(budget, entries, selection, inspectedId, experimental, nodeSettings, inspectedLink, budget, true,
                    Integer.toString(DEFAULT_REFUND_PERCENT), true);
        }

        /**
         * Removes selected nodes and clears their references in surviving entries.
         */
        Draft deleteSelection() {
            var deleted = selection.stream().filter(id -> id >= 0).collect(Collectors.toUnmodifiableSet());
            if (deleted.isEmpty()) {
                return this;
            }
            var remaining = entries.stream().filter(entry -> !deleted.contains(entry.node().id()))
                    .map(entry -> unlink(entry, deleted)).toList();
            return new Draft(budget, remaining, List.of(), UpgradeNode.DISCONNECTED, experimental, nodeSettings, null,
                    startingPoints, operatorOnly, refundPercent, dropResearchOnBreak);
        }

        private static Entry unlink(Entry entry, Set<Integer> deleted) {
            var node = entry.node();
            var parents = node.prerequisiteIds();
            if (parents.stream().anyMatch(deleted::contains)) {
                var remaining = parents.stream().filter(id -> !deleted.contains(id)).toList();
                entry = entry.withNode(node.withPrerequisites(remaining, Math.min(node.requiredCount(), remaining.size())));
            }
            if (entry.branches().stream().anyMatch(deleted::contains)) {
                entry = entry.withBranches(entry.branches().stream().filter(id -> !deleted.contains(id)).toList());
            }
            return entry;
        }
    }
}
