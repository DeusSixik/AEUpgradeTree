package dev.sixik.aeupgrade.client.tree;

import java.util.List;
import java.util.function.IntFunction;

import dev.sixik.aeupgrade.tree.UpgradeCatalog;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Entry;
import dev.sixik.aeupgrade.tree.UpgradeNode;
import dev.sixik.aeupgrade.tree.UpgradePlan;
import dev.sixik.aeupgrade.tree.UpgradePlan.Availability;
import dev.sixik.unigui.api.math.ColorView;
import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.text.RichText;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftFonts;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;

/**
 * Localized labels and hover text shared by both tree screens.
 */
public final class UpgradeTreeText {
    private UpgradeTreeText() {
    }

    /**
     * Translation prefix for prototype screen controls, node descriptions and action results.
     */
    private static final String TEXT = "aeupgrade.tree.";
    /**
     * Color of readable labels on AE2's dark disabled sprite.
     */
    private static final ColorView DISABLED_TEXT = MutableColor.fromHex("#F0F1F8");
    /**
     * AE2 selection color for the current node and purchased connections.
     */
    private static final ColorView SELECTION = MutableColor.fromHex("#ACE9FF");
    /**
     * Accent for energy nodes, traces and lock reasons.
     */
    private static final ColorView MAP_ENERGY = MutableColor.fromHex("#F3C77C");
    /**
     * Contrasting color for a trade-off in a node tooltip.
     */
    private static final ColorView MAP_PENALTY = MutableColor.fromHex("#FF9F9F");

    /**
     * @param message wire feedback key with optional diagnostic text
     * @return localized, readable feedback
     */
    public static String feedback(String message) {
        if (message.isEmpty()) {
            return "";
        }
        var parts = message.split("\n", 2);
        var label = Component.translatable(parts[0]).getString();
        return parts.length == 1 ? label : label + " " + parts[1];
    }

    /**
     * @param node       upgrade with incoming prerequisites
     * @param name       lookup for prerequisite titles
     * @param choiceOpen whether the branch quota can still be filled
     * @return localized description of the prerequisite rule
     */
    public static String prerequisiteText(UpgradeNode node, IntFunction<String> name, boolean choiceOpen) {
        if (choiceOpen && node.locksRemaining() && node.requiredCount() < node.prerequisiteCount()) {
            return tr("choice_requires", node.requiredCount(), node.prerequisiteCount());
        }
        if (node.requirements() != null) {
            return tr("requires_count", node.requiredCount(), node.prerequisiteCount());
        }
        String first = name.apply(node.prerequisiteId(0));
        return node.prerequisiteCount() == 1 ? tr("requires", first)
                : tr("requires_either", first, name.apply(node.prerequisiteId(1)));
    }

    /**
     * @return branch roots that buying this upgrade could still close
     */
    static List<Integer> branchChoices(Entry entry, Availability status, UpgradeCatalog catalog, UpgradePlan purchases) {
        if ((status != Availability.AVAILABLE && status != Availability.POINTS) || entry.branches().isEmpty()) {
            return List.of();
        }
        return entry.branches().stream()
                .filter(id -> purchases.availability(catalog.node(id)) != Availability.EXCLUSIVE)
                .toList();
    }

    /**
     * Builds a hover description with separate lines for bonuses, devices and availability.
     *
     * @param entry     upgrade under the pointer
     * @param status    current availability for the controller
     * @param condition localized availability or editing information
     * @param branches  branch roots to list in the warning
     * @param nodeName  lookup for branch root names
     * @return colored tooltip text at a fixed screen scale
     */
    public static RichText nodeTooltip(Entry entry, Availability status, String condition, List<Integer> branches,
                                       IntFunction<String> nodeName) {
        var font = MinecraftFonts.defaultFace();
        var text = RichText.builder().append(RichText.of(entryName(entry) + "\n", font, 11, DISABLED_TEXT));
        var effects = entry.effects();
        if (effects.isEmpty() && entry.node().id() != UpgradeNode.ROOT) {
            text.append(RichText.of(tr("prototype") + "\n", font, 9, MAP_ENERGY));
        }
        int shown = Math.min(effects.size(), 6);
        for (int i = 0; i < shown; i++) {
            var effect = effects.get(i);
            text.append(RichText.of(UpgradeEffectText.describe(effect) + "\n", font, 10, UpgradeEffectText.color(effect)));
        }
        if (effects.size() > shown) {
            text.append(RichText.of(tr("effect_more", effects.size() - shown) + "\n", font, 9, DISABLED_TEXT));
        }
        var description = localizedText(entry.description());
        if (!description.isBlank()) {
            text.append(RichText.of(description + "\n", font, 9, DISABLED_TEXT));
        }
        if (!branches.isEmpty()) {
            String warning = branches.size() == 1 ? tr("blocks", nodeName.apply(branches.getFirst()))
                    : tr("blocks_multiple", branches.size());
            text.append(RichText.of(warning + "\n", font, 9, MAP_PENALTY));
        }
        return text
                .append(RichText.of(condition, font, 9, status == Availability.AVAILABLE
                        || status == Availability.UNLOCKED ? SELECTION
                        : status == Availability.EXCLUSIVE ? MAP_PENALTY : MAP_ENERGY))
                .build();
    }

    /**
     * @param entry node and its configured presentation
     * @return custom label or localized default
     */
    public static String entryName(UpgradeCatalog.Entry entry) {
        return entry.title().isBlank() ? defaultNodeName(entry.node()) : localizedText(entry.title());
    }

    public static String defaultNodeName(UpgradeNode node) {
        return node.id() == UpgradeNode.ROOT ? tr("core") : tr("node." + node.key());
    }

    /**
     * Resolves explicit @keys and known bare translation keys, leaving literal text as entered.
     */
    public static String localizedText(String value) {
        if (value.startsWith("@") && value.length() > 1) {
            return Component.translatable(value.substring(1)).getString();
        }
        return I18n.exists(value) ? Component.translatable(value).getString() : value;
    }

    /**
     * @param path built-in or custom path
     * @return localized built-in name or the configured custom title
     */
    public static String pathName(UpgradeCatalog.Path path) {
        return path.title().isEmpty() ? tr("route." + path.id()) : path.title();
    }

    /**
     * Resolves a prototype translation using the client's selected language.
     *
     * @param key  translation suffix
     * @param args translation substitutions
     * @return localized text
     */
    public static String tr(String key, Object... args) {
        return Component.translatable(TEXT + key, args).getString();
    }
}
