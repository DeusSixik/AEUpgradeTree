package dev.sixik.aeupgrade.tree;

import java.util.ArrayList;
import java.util.List;

import dev.sixik.aeupgrade.tree.UpgradeCatalog.Entry;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Kind;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Target;
import dev.sixik.aeupgrade.tree.UpgradeNode.Sector;

/**
 * Two playable graph examples sharing a starter connected to the ME core.
 */
public final class UpgradeTreeExamples {
    public static final int NODE_COUNT = 14;
    private static final int BRANCH_COUNT = 4;
    private static final int WIDTH = 172;
    private static final int HALF_HEIGHT = 172;
    private static final String PREFIX = "@aeupgrade.tree.example.";

    private UpgradeTreeExamples() {
    }

    /**
     * @param firstId first unused stable ID
     * @param x       horizontal position of the shared starter
     * @param y       vertical position of the shared starter
     * @return a one-of-four fork and a three-of-four fork with a shared continuation
     */
    public static List<Entry> at(int firstId, float x, float y) {
        int bound = UpgradeCatalog.MAX_COORDINATE;
        x = Math.clamp(x, -bound, bound - WIDTH);
        y = Math.clamp(y, -bound + HALF_HEIGHT, bound - HALF_HEIGHT);
        var entries = new ArrayList<Entry>(NODE_COUNT);
        entries.add(entry(firstId, "starter", "ae2:controller", UpgradeNode.ROOT, x, y, UpgradeNode.Kind.STARTER,
                List.of(), PREFIX + "starter_hint"));

        var choices = List.of(
                new Example("channels", "ae2:engineering_processor", Kind.CHANNELS, 2),
                new Example("patterns", "ae2:pattern_provider", Kind.PATTERN_SLOTS, 2),
                new Example("speed", "ae2:speed_card", Kind.SPEED_BASE, 10),
                new Example("power", "ae2:energy_cell", Kind.IDLE_PERCENT, -5));
        var choiceIds = new ArrayList<Integer>(BRANCH_COUNT);
        for (int i = 0; i < BRANCH_COUNT; i++) {
            choiceIds.add(firstId + 1 + i * 2);
        }
        for (int i = 0; i < BRANCH_COUNT; i++) {
            int id = choiceIds.get(i);
            var choice = choices.get(i);
            float row = y - HALF_HEIGHT + i * 40;
            var effects = List.of(new UpgradeEffect(choice.effect(), Target.ALL, choice.amount()));
            entries.add(entry(id, choice.name(), choice.icon(), firstId, x + 84, row, UpgradeNode.Kind.CHOICE,
                    effects, "").withBranches(choiceIds.stream().filter(other -> other != id).toList()));
            entries.add(entry(id + 1, choice.name() + "_mastery", choice.icon(), id, x + WIDTH, row,
                    UpgradeNode.Kind.PASSIVE, effects, ""));
        }

        var inputs = List.of(
                new Example("input_a", "ae2:logic_processor", Kind.CHANNELS, 1),
                new Example("input_b", "ae2:calculation_processor", Kind.PATTERN_SLOTS, 1),
                new Example("input_c", "ae2:formation_core", Kind.INTERFACE_SLOTS, 1),
                new Example("input_d", "ae2:annihilation_core", Kind.SPEED_BASE, 5));
        var inputIds = new ArrayList<Integer>(BRANCH_COUNT);
        for (int i = 0; i < BRANCH_COUNT; i++) {
            int id = firstId + entries.size();
            inputIds.add(id);
            var input = inputs.get(i);
            entries.add(entry(id, input.name(), input.icon(), firstId, x + 84, y + 52 + i * 40, UpgradeNode.Kind.PASSIVE,
                    List.of(new UpgradeEffect(input.effect(), Target.ALL, input.amount())), ""));
        }
        var junction = entry(firstId + entries.size(), "junction", "ae2:molecular_assembler", UpgradeNode.DISCONNECTED,
                x + WIDTH, y + 112, UpgradeNode.Kind.CAPSTONE, List.of(new UpgradeEffect(Kind.BATCH, Target.ALL, 1)), "");
        entries.add(junction.withNode(junction.node().withPrerequisites(inputIds, 3, true)));
        return List.copyOf(entries);
    }

    private static Entry entry(int id, String name, String icon, int previous, float x, float y, UpgradeNode.Kind kind,
                               List<UpgradeEffect> effects, String description) {
        var node = new UpgradeNode(id, "example." + name + "." + id, 1, 0, Sector.NETWORK, null,
                previous, UpgradeNode.ROOT, x, y, kind);
        return new Entry(node, PREFIX + name, description, icon, effects, null, null, List.of());
    }

    private record Example(String name, String icon, Kind effect, int amount) {
    }
}
