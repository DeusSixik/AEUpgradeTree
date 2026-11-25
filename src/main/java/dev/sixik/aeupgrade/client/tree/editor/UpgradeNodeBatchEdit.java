package dev.sixik.aeupgrade.client.tree.editor;

import java.util.List;
import java.util.Set;

import dev.sixik.aeupgrade.tree.UpgradeCatalog.Entry;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Reveal;
import dev.sixik.aeupgrade.tree.UpgradeNode;
import dev.sixik.aeupgrade.tree.UpgradeNodeStyle;
import org.jetbrains.annotations.Nullable;

/**
 * Applies explicitly edited inspector fields to a node without copying its identity or unrelated values.
 */
record UpgradeNodeBatchEdit(Set<Field> fields, Entry values, @Nullable Integer threshold, boolean lockRemaining) {
    enum Field {
        TITLE, DESCRIPTION, COST, X, Y, INPUTS, THRESHOLD, LOCK_REMAINING, BRANCHES, EFFECTS,
        HIDE_DESCENDANTS, HIDE_UNTIL_PREREQUISITES,
        ICON, SIZE, SHAPE, BORDER_COLOR, BORDER_WIDTH, HOVER_BORDER_WIDTH, BACKGROUNDS
    }

    static final Set<Field> APPEARANCE = Set.of(Field.ICON, Field.SIZE, Field.SHAPE, Field.BORDER_COLOR,
            Field.BORDER_WIDTH, Field.HOVER_BORDER_WIDTH, Field.BACKGROUNDS);

    UpgradeNodeBatchEdit {
        fields = Set.copyOf(fields);
    }

    List<Entry> applyToSelection(List<Entry> entries, Set<Integer> selection) {
        return entries.stream().map(entry -> selection.contains(entry.node().id()) ? apply(entry) : entry).toList();
    }

    Entry apply(Entry original) {
        var oldNode = original.node();
        var newNode = values.node();
        var node = oldNode.withCostAndPosition(has(Field.COST) ? newNode.cost() : oldNode.cost(),
                has(Field.X) ? newNode.x() : oldNode.x(), has(Field.Y) ? newNode.y() : oldNode.y());
        if (has(Field.INPUTS) || has(Field.THRESHOLD) || has(Field.LOCK_REMAINING)) {
            var inputs = inputs(has(Field.INPUTS) ? newNode : oldNode);
            int count = oldNode.requiredCount();
            if (has(Field.THRESHOLD)) {
                count = threshold == null ? defaultThreshold(inputs) : threshold;
            } else if (has(Field.INPUTS) && count <= 1) {
                count = defaultThreshold(inputs);
            }
            node = node.withPrerequisites(inputs, count, has(Field.LOCK_REMAINING) ? lockRemaining : oldNode.locksRemaining());
        }

        var appearance = original.appearance();
        var edited = values.appearance();
        var style = new UpgradeNodeStyle(has(Field.SIZE) ? edited.size() : appearance.size(),
                has(Field.SHAPE) ? edited.shape() : appearance.shape(),
                has(Field.ICON) ? edited.iconTexture() : appearance.iconTexture(),
                has(Field.BACKGROUNDS) ? edited.backgrounds() : appearance.backgrounds(),
                has(Field.BORDER_COLOR) ? edited.borderColor() : appearance.borderColor(),
                has(Field.BORDER_WIDTH) ? edited.borderWidth() : appearance.borderWidth(),
                has(Field.HOVER_BORDER_WIDTH) ? edited.hoverBorderWidth() : appearance.hoverBorderWidth());
        String icon = has(Field.ICON) && edited.iconTexture() == null ? values.icon() : original.icon();
        var rules = original.revealRules();
        var editedRules = values.revealRules();
        var reveal = new Reveal(has(Field.HIDE_DESCENDANTS)
                ? editedRules.hideDescendantsUntilUnlocked() : rules.hideDescendantsUntilUnlocked(),
                has(Field.HIDE_UNTIL_PREREQUISITES) ? editedRules.hideUntilPrerequisites() : rules.hideUntilPrerequisites());
        return new Entry(node, has(Field.TITLE) ? values.title() : original.title(),
                has(Field.DESCRIPTION) ? values.description() : original.description(), icon,
                has(Field.EFFECTS) ? values.effects() : original.effects(), original.path(),
                style.equals(appearance) ? original.style() : style,
                has(Field.BRANCHES) ? values.branches() : original.branches(), reveal);
    }

    private boolean has(Field field) {
        return fields.contains(field);
    }

    static List<Integer> inputs(UpgradeNode node) {
        return node.connectsToCore() ? List.of(UpgradeNode.ROOT) : node.prerequisiteIds();
    }

    static int defaultThreshold(List<Integer> inputs) {
        return inputs.isEmpty() || inputs.equals(List.of(UpgradeNode.ROOT)) ? 0 : 1;
    }
}
