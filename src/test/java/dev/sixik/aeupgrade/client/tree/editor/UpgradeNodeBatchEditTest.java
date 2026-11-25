package dev.sixik.aeupgrade.client.tree.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;
import java.util.Set;

import dev.sixik.aeupgrade.client.tree.editor.UpgradeEditorHistory.Draft;
import dev.sixik.aeupgrade.client.tree.editor.UpgradeNodeBatchEdit.Field;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Entry;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.ExperimentalSettings;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Reveal;
import dev.sixik.aeupgrade.tree.UpgradeEffect;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute;
import dev.sixik.aeupgrade.tree.UpgradeNode;
import dev.sixik.aeupgrade.tree.UpgradeNodeStyle;
import dev.sixik.aeupgrade.tree.UpgradeNodeStyle.Shape;
import org.junit.jupiter.api.Test;

class UpgradeNodeBatchEditTest {
    private static final String BACKGROUND = "pack:textures/tree/background.png";
    private static final String ICON = "pack:textures/tree/icon.png";

    @Test
    void apply_sharedBorderColor_preservesEachNodesOtherAppearanceAndData() {
        var first = withColor(entry(7, 31, Shape.CIRCLE, null, List.of(), List.of(effect(2))), "#35C9E8");
        var second = entry(8, 48, Shape.DIAMOND, ICON, List.of(BACKGROUND), List.of(effect(9)));
        var edit = new UpgradeNodeBatchEdit(Set.of(Field.BORDER_COLOR), first, null, false);

        var changedFirst = edit.apply(first);
        var changedSecond = edit.apply(second);

        assertEquals(first, changedFirst);
        assertEquals("#35C9E8", changedSecond.appearance().borderColor());
        assertEquals(second.appearance().size(), changedSecond.appearance().size());
        assertEquals(second.appearance().shape(), changedSecond.appearance().shape());
        assertEquals(second.appearance().iconTexture(), changedSecond.appearance().iconTexture());
        assertEquals(second.appearance().backgrounds(), changedSecond.appearance().backgrounds());
        assertEquals(second.effects(), changedSecond.effects());
        assertEquals(second.node(), changedSecond.node());
    }

    @Test
    void apply_sharedNodeSize_keepsDifferentShapeAndBorderSettings() {
        var first = entry(7, 31, Shape.CIRCLE, null, List.of(), List.of());
        var second = entry(8, 48, Shape.DIAMOND, ICON, List.of(BACKGROUND), List.of());
        var values = withStyle(first, new UpgradeNodeStyle(64, Shape.CIRCLE, null, List.of(),
                "#123456", 4f, 6f));
        var edit = new UpgradeNodeBatchEdit(Set.of(Field.SIZE), values, null, false);

        var changedFirst = edit.apply(first);
        var changedSecond = edit.apply(second);

        assertEquals(64f, changedFirst.appearance().size());
        assertEquals(64f, changedSecond.appearance().size());
        assertEquals(Shape.DIAMOND, changedSecond.appearance().shape());
        assertEquals(ICON, changedSecond.appearance().iconTexture());
        assertEquals(List.of(BACKGROUND), changedSecond.appearance().backgrounds());
        assertEquals("#123456", changedSecond.appearance().borderColor());
    }

    @Test
    void apply_selectedAppearanceField_preservesIndividualIconsAndLinks() {
        var first = entry(7, 31, Shape.CIRCLE, null, List.of(), List.of());
        var second = entry(8, 48, Shape.DIAMOND, ICON, List.of(BACKGROUND), List.of());
        var replacement = withStyle(first, new UpgradeNodeStyle(31, Shape.SQUARE, null, List.of()));
        var edit = new UpgradeNodeBatchEdit(Set.of(Field.SHAPE), replacement, null, false);

        var changed = edit.apply(second);

        assertEquals(Shape.SQUARE, changed.appearance().shape());
        assertEquals(ICON, changed.appearance().iconTexture());
        assertEquals(List.of(BACKGROUND), changed.appearance().backgrounds());
        assertEquals(second.node(), changed.node());
    }

    @Test
    void history_groupEdit_isUndoneAndRedoneAsOneChange() {
        var first = entry(7, 31, Shape.CIRCLE, null, List.of(), List.of(effect(2)));
        var second = entry(8, 48, Shape.DIAMOND, ICON, List.of(BACKGROUND), List.of(effect(9)));
        var third = entry(9, 24, Shape.CIRCLE, null, List.of(), List.of());
        var before = new Draft("40", List.of(first, second, third), List.of(7, 8), 7, ExperimentalSettings.DEFAULT);
        var color = withColor(first, "#35C9E8");
        var edit = new UpgradeNodeBatchEdit(Set.of(Field.BORDER_COLOR), color, null, false);
        var afterEntries = edit.applyToSelection(before.entries(), Set.copyOf(before.selection()));
        var after = new Draft("40", afterEntries, before.selection(), 7, ExperimentalSettings.DEFAULT);
        var history = new UpgradeEditorHistory();

        history.record(before, after);

        assertEquals(third, afterEntries.getLast());
        assertEquals(before, history.undo());
        assertNull(history.undo());
        assertEquals(after, history.redo());
    }

    @Test
    void apply_backgroundAndEffectEdits_replaceOnlyTheSpecifiedLists() {
        var first = entry(7, 31, Shape.CIRCLE, null, List.of(BACKGROUND, ICON), List.of(effect(2), effect(3)));
        var second = entry(8, 48, Shape.DIAMOND, ICON, List.of(), List.of(effect(9)));
        var backgrounds = new UpgradeNodeBatchEdit(Set.of(Field.BACKGROUNDS), first, null, false).apply(second);
        var effects = new UpgradeNodeBatchEdit(Set.of(Field.EFFECTS), first, null, false).apply(second);

        assertEquals(first.appearance().backgrounds(), backgrounds.appearance().backgrounds());
        assertEquals(second.effects(), backgrounds.effects());
        assertEquals(first.effects(), effects.effects());
        assertEquals(second.appearance(), effects.appearance());
    }

    @Test
    void history_pasteEffects_replacesOnlySelectedListsAndReplaysAsOneEdit() {
        var source = entry(7, 31, Shape.CIRCLE, null, List.of(), List.of(
                new UpgradeEffect(UpgradeEffect.Kind.IDLE_PERCENT, UpgradeEffect.Target.PATTERN_PROVIDER, -5, "#33BB66"),
                new UpgradeEffect(null, UpgradeEffect.Target.ALL, 4, null, "example:dispatch")));
        var first = entry(8, 48, Shape.DIAMOND, ICON, List.of(BACKGROUND), List.of(effect(9)));
        var second = entry(9, 24, Shape.CIRCLE, null, List.of(), List.of());
        var other = entry(10, 28, Shape.SQUARE, null, List.of(), List.of(effect(2)));
        var before = new Draft("40", List.of(source, first, second, other), List.of(8, 9), 8, ExperimentalSettings.DEFAULT);
        var edit = new UpgradeNodeBatchEdit(Set.of(Field.EFFECTS), source, null, false);
        var updated = edit.applyToSelection(before.entries(), Set.copyOf(before.selection()));
        var after = new Draft("40", updated, before.selection(), 8, ExperimentalSettings.DEFAULT);
        var history = new UpgradeEditorHistory();

        history.record(before, after);

        assertEquals(source, updated.getFirst());
        assertEquals(source.effects(), updated.get(1).effects());
        assertEquals(source.effects(), updated.get(2).effects());
        assertEquals(first.node(), updated.get(1).node());
        assertEquals(first.appearance(), updated.get(1).appearance());
        assertEquals(other, updated.getLast());
        assertEquals(before, history.undo());
        assertNull(history.undo());
        assertEquals(after, history.redo());
    }

    @Test
    void apply_sharedHorizontalCoordinate_keepsVerticalCoordinateAndRoutes() {
        var first = entry(7, 31, Shape.CIRCLE, null, List.of(), List.of());
        var second = entry(8, 48, Shape.DIAMOND, ICON, List.of(), List.of());
        var positioned = second.withNode(second.node().withCostAndPosition(3, 80, -32));
        var edit = new UpgradeNodeBatchEdit(Set.of(Field.X), first, null, false);
        var changed = edit.apply(positioned);

        assertEquals(first.node().x(), changed.node().x());
        assertEquals(-32f, changed.node().y());
        assertEquals(3, changed.node().cost());
        assertNotEquals(positioned.node(), changed.node());
        assertEquals(positioned.node().routes(), changed.node().routes());
    }

    @Test
    void apply_visibilityToggle_preservesTheOtherGateAndCopiesBoth() {
        var first = withReveal(entry(7, 31, Shape.CIRCLE, null, List.of(), List.of()), new Reveal(true, false));
        var second = withReveal(entry(8, 48, Shape.DIAMOND, ICON, List.of(), List.of()), new Reveal(false, true));
        var edit = new UpgradeNodeBatchEdit(Set.of(Field.HIDE_DESCENDANTS), first, null, false);

        var updated = edit.apply(second);

        assertEquals(new Reveal(true, true), updated.revealRules());
        assertEquals(second.appearance(), updated.appearance());
        assertEquals(second.node(), updated.node());
        var copied = UpgradeTreeClipboard.paste(List.of(first, updated), 10, 0, 0);
        assertEquals(first.revealRules(), copied.getFirst().revealRules());
        assertEquals(updated.revealRules(), copied.getLast().revealRules());
    }

    private static Entry entry(int id, float size, Shape shape, String icon, List<String> backgrounds,
            List<UpgradeEffect> effects) {
        var prerequisite = id == 7 ? UpgradeNode.ROOT : 7;
        var node = new UpgradeNode(id, "test_" + id, 1, 0, UpgradeNode.Sector.NETWORK, null,
                prerequisite, UpgradeNode.ROOT, id * 20, 0, UpgradeNode.Kind.PASSIVE);
        var style = new UpgradeNodeStyle(size, shape, icon, backgrounds, "#123456", 2f, 3f);
        if (id == 8) {
            node = node.withLinkRoute(7, new UpgradeLinkRoute(7, UpgradeLinkRoute.Style.ROUNDED, List.of()));
        }
        return new Entry(node, "Node " + id, "", "", effects, null, style, List.of());
    }

    private static UpgradeEffect effect(double value) {
        return new UpgradeEffect(UpgradeEffect.Kind.SPEED_PERCENT, UpgradeEffect.Target.ALL, value);
    }

    private static Entry withColor(Entry entry, String color) {
        var appearance = entry.appearance();
        return withStyle(entry, new UpgradeNodeStyle(appearance.size(), appearance.shape(), appearance.iconTexture(),
                appearance.backgrounds(), color, appearance.borderWidth(), appearance.hoverBorderWidth()));
    }

    private static Entry withStyle(Entry entry, UpgradeNodeStyle style) {
        return new Entry(entry.node(), entry.title(), entry.description(), entry.icon(),
                entry.effects(), entry.path(), style, entry.branches(), entry.reveal());
    }

    private static Entry withReveal(Entry entry, Reveal reveal) {
        return new Entry(entry.node(), entry.title(), entry.description(), entry.icon(),
                entry.effects(), entry.path(), entry.style(), entry.branches(), reveal);
    }
}
