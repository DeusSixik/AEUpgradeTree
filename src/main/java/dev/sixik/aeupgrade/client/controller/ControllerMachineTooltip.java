package dev.sixik.aeupgrade.client.controller;

import java.text.DecimalFormat;
import java.util.List;

import appeng.menu.me.networktool.MachineGroup;
import appeng.util.Platform;
import dev.sixik.aeupgrade.network.ControllerOverview;
import dev.sixik.aeupgrade.network.ControllerOverview.EnergyChange;
import dev.sixik.aeupgrade.network.ControllerOverview.Stat;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * Adds live AEUpgradeTree settings to the native ME Controller device tooltip.
 */
public final class ControllerMachineTooltip {
    /**
     * Translation prefix for the controller labels.
     */
    private static final String TEXT = "aeupgrade.controller.";

    /**
     * Keeps tooltip formatting stateless.
     */
    private ControllerMachineTooltip() {
    }

    /**
     * Appends the effective settings and energy changes for the native machine group under the cursor.
     *
     * @param overview current menu snapshot
     * @param machine  native AE2 status group
     * @param tooltip  lines already produced by AE2
     */
    public static void append(ControllerOverview overview, MachineGroup machine, List<Component> tooltip) {
        for (var entry : overview.machines()) {
            if (entry.missingChannel() != machine.isMissingChannel() || !entry.item().equals(machine.getDisplay())) {
                continue;
            }
            if (entry.stats().isEmpty() && !entry.change().changed()) {
                return;
            }
            tooltip.add(Component.literal("AEUpgradeTree").withStyle(ChatFormatting.LIGHT_PURPLE));
            for (var setting : entry.stats()) {
                var stat = setting.stat();
                String value = statValue(stat, setting.min());
                if (setting.min() != setting.max()) {
                    value += " … " + statValue(stat, setting.max());
                }
                tooltip.add(line("stat." + stat.key(), value, ChatFormatting.AQUA));
            }
            appendEnergy(tooltip, entry.change());
            return;
        }
    }

    /**
     * Formats an endpoint of a setting's range for display.
     *
     * @param stat  setting specifying the displayed unit
     * @param value effective value for the setting
     * @return count, bonus in cards, or signed speed percent
     */
    private static String statValue(Stat stat, double value) {
        if (stat == Stat.SPEED) {
            return signed(value, true);
        }
        return (stat == Stat.EXTRA_SPEED_CARDS && value > 0 ? "+" : "") + (int) value;
    }

    /**
     * Preserves the energy adjustments shown for this machine group.
     *
     * @param tooltip native machine tooltip lines
     * @param change  idle and crafting energy changes across the group
     */
    private static void appendEnergy(List<Component> tooltip, EnergyChange change) {
        if (change.added() > 0) {
            tooltip.add(line("idle", signed(change.added(), false), ChatFormatting.RED));
        }
        if (change.saved() > 0) {
            tooltip.add(line("idle", signed(-change.saved(), false), ChatFormatting.GREEN));
        }
        change.crafting().ifPresent(range -> {
            addCraftEnergy(tooltip, range.minPercent(), range.maxPercent(), true);
            addCraftEnergy(tooltip, range.minFlat(), range.maxFlat(), false);
        });
    }

    /**
     * Adds a signed crafting energy amount or range.
     *
     * @param tooltip native machine tooltip lines
     * @param min     smallest modifier in the group
     * @param max     largest modifier in the group
     * @param percent true for a percent of base draw, false for flat AE/t
     */
    private static void addCraftEnergy(List<Component> tooltip, double min, double max, boolean percent) {
        if (min != 0 || max != 0) {
            String value = min == max ? signed(min, percent) : signed(min, percent) + " … " + signed(max, percent);
            var color = min >= 0 ? ChatFormatting.RED : max <= 0 ? ChatFormatting.GREEN : ChatFormatting.AQUA;
            tooltip.add(line("craft", value, color));
        }
    }

    /**
     * Formats a signed percentage or flat power change.
     *
     * @param value   signed modifier
     * @param percent true for percent, false for AE/t in the current AE2 display unit
     * @return sign, localized magnitude and unit
     */
    private static String signed(double value, boolean percent) {
        String magnitude = percent ? new DecimalFormat("0.##").format(Math.abs(value)) + "%"
                : Platform.formatPower(Math.abs(value), true);
        return (value > 0 ? "+" : value < 0 ? "−" : "") + magnitude;
    }

    /**
     * Builds a localized label and value in the requested tooltip color.
     *
     * @param label suffix of the label's translation key
     * @param value preformatted adjustment
     * @param color tooltip line color
     * @return formatted tooltip line
     */
    private static MutableComponent line(String label, String value, ChatFormatting color) {
        return Component.translatable(TEXT + "tooltip", Component.translatable(TEXT + label), value).withStyle(color);
    }
}
