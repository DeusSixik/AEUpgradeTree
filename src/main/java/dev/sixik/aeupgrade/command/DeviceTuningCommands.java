package dev.sixik.aeupgrade.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.sixik.aeupgrade.channel.DeviceChannelType;
import dev.sixik.aeupgrade.network.NetworkDeviceSettingsService;
import dev.sixik.aeupgrade.tuning.DeviceTuningMath;
import dev.sixik.aeupgrade.tuning.DeviceTuningProfile;
import dev.sixik.aeupgrade.tuning.DeviceTuningProfile.Setting;
import dev.sixik.aeupgrade.tuning.DeviceTuningService;
import dev.sixik.aeupgrade.tuning.GlobalDeviceTuning;
import dev.sixik.aeupgrade.tuning.ProviderDispatch;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * Writes energy, speed and throughput overrides for operator-selected devices and networks.
 */
final class DeviceTuningCommands {
    /**
     * Error shown when an assembler-only modifier was aimed at another device.
     */
    private static final SimpleCommandExceptionType ASSEMBLER_REQUIRED = new SimpleCommandExceptionType(
            Component.literal("Look at a Molecular Assembler for this setting."));

    /**
     * Error shown when throughput was aimed at a device other than a Pattern Provider.
     */
    private static final SimpleCommandExceptionType PROVIDER_REQUIRED = new SimpleCommandExceptionType(
            Component.literal("Look at an ME Pattern Provider for this setting."));

    /**
     * Applies a validated command value to the scope and family bound to a command branch.
     */
    @FunctionalInterface
    private interface TuningTarget {
        /**
         * Updates one property or clears it to inherit from a broader scope.
         *
         * @param source  operator issuing the command
         * @param setting property to update
         * @param value   new value, or NaN to reset
         * @return one after processing the command
         * @throws CommandSyntaxException if the operator's target is invalid
         */
        int apply(CommandSourceStack source, Setting setting, double value) throws CommandSyntaxException;
    }

    /**
     * Keeps this command class non-instantiable.
     */
    private DeviceTuningCommands() {
    }

    /**
     * Defines the energy subcommand under AEUpgradeTree's operator-only root.
     *
     * @return the energy command and its scope options
     */
    static LiteralArgumentBuilder<CommandSourceStack> energy() {
        var network = literal("network");
        for (var type : DeviceChannelType.values()) {
            network.then(energyScope(type.id(), networkTarget(type), type == DeviceChannelType.MOLECULAR_ASSEMBLER));
        }
        return literal("energy")
                .then(energyScope("global", deviceTarget(true), true))
                .then(energyScope("local", deviceTarget(false), true))
                .then(network);
    }

    /**
     * Defines the virtual speed-card subcommand under AEUpgradeTree's operator-only root.
     *
     * @return the speed command and its scope options
     */
    static LiteralArgumentBuilder<CommandSourceStack> speed() {
        return literal("speed")
                .then(speedScope("global", deviceTarget(true)))
                .then(speedScope("local", deviceTarget(false)))
                .then(speedScope("network", networkTarget(DeviceChannelType.MOLECULAR_ASSEMBLER)));
    }

    /**
     * Defines the maximum recipes collected into one assembly cycle.
     *
     * @return the batch size command and its scope options
     */
    static LiteralArgumentBuilder<CommandSourceStack> batch() {
        return literal("batch")
                .then(integerScope("global", deviceTarget(true), Setting.BATCH_SIZE, 1, DeviceTuningMath.MAX_BATCH_SIZE))
                .then(integerScope("local", deviceTarget(false), Setting.BATCH_SIZE, 1, DeviceTuningMath.MAX_BATCH_SIZE))
                .then(integerScope("network", networkTarget(DeviceChannelType.MOLECULAR_ASSEMBLER),
                        Setting.BATCH_SIZE, 1, DeviceTuningMath.MAX_BATCH_SIZE));
    }

    /**
     * Defines the per-tick recipe throughput command for Pattern Providers.
     *
     * @return the throughput command with local and global scope options
     */
    static LiteralArgumentBuilder<CommandSourceStack> throughput() {
        return literal("throughput")
                .then(integerScope("global", deviceTarget(true), Setting.PROVIDER_THROUGHPUT, 1, ProviderDispatch.MAX_THROUGHPUT))
                .then(integerScope("local", deviceTarget(false), Setting.PROVIDER_THROUGHPUT, 1, ProviderDispatch.MAX_THROUGHPUT))
                .then(integerScope("network", networkTarget(DeviceChannelType.PATTERN_PROVIDER),
                        Setting.PROVIDER_THROUGHPUT, 1, ProviderDispatch.MAX_THROUGHPUT));
    }

    /**
     * Creates the idle and crafting energy options for a scope.
     *
     * @param name     command literal for the scope
     * @param target   handler bound to the selected scope
     * @param crafting true when crafting energy can be configured in this branch
     * @return the scope branch
     */
    private static LiteralArgumentBuilder<CommandSourceStack> energyScope(String name, TuningTarget target, boolean crafting) {
        var scope = literal(name)
                .then(literal("idle")
                        .then(energySetting("flat", target, Setting.IDLE_FLAT))
                        .then(energySetting("percent", target, Setting.IDLE_PERCENT)));
        if (crafting) {
            scope.then(literal("craft")
                    .then(energySetting("flat", target, Setting.CRAFT_FLAT))
                    .then(energySetting("percent", target, Setting.CRAFT_PERCENT)));
        }
        return scope;
    }

    /**
     * Creates the set and reset commands for an energy property.
     *
     * @param name    command literal for the property
     * @param target  handler bound to the selected scope
     * @param setting property to change
     * @return the signed value and reset options
     */
    private static LiteralArgumentBuilder<CommandSourceStack> energySetting(String name, TuningTarget target, Setting setting) {
        double limit = DeviceTuningProfile.MAX_ENERGY_MODIFIER;
        return literal(name)
                .then(argument("amount", DoubleArgumentType.doubleArg(-limit, limit))
                        .executes(context -> target.apply(context.getSource(), setting,
                                DoubleArgumentType.getDouble(context, "amount"))))
                .then(literal("reset").executes(context -> target.apply(context.getSource(), setting, Double.NaN)));
    }

    /**
     * Creates the virtual card options for a scope.
     *
     * @param name   command literal for the scope
     * @param target handler bound to the selected scope
     * @return the scope branch with a card count and reset
     */
    private static LiteralArgumentBuilder<CommandSourceStack> speedScope(String name, TuningTarget target) {
        var setting = Setting.CRAFT_SPEED_PERCENT;
        return integerScope(name, target, Setting.VIRTUAL_SPEED_CARDS, 0, DeviceTuningMath.MAX_SPEED_CARDS)
                .then(literal("percent")
                        .then(argument("amount", DoubleArgumentType.doubleArg(-100, DeviceTuningProfile.MAX_ENERGY_MODIFIER))
                                .executes(context -> target.apply(context.getSource(), setting,
                                        DoubleArgumentType.getDouble(context, "amount"))))
                        .then(literal("reset").executes(context -> target.apply(context.getSource(), setting, Double.NaN))));
    }

    /**
     * Creates the write and reset commands for an integer tuning property.
     *
     * @param name    command literal for the scope
     * @param target  handler bound to the selected scope
     * @param setting property updated by this branch
     * @param minimum lowest supported value
     * @param maximum highest supported value
     * @return scope branch with a value and reset
     */
    private static LiteralArgumentBuilder<CommandSourceStack> integerScope(String name, TuningTarget target,
                                                                           Setting setting, int minimum, int maximum) {
        return literal(name)
                .then(argument("amount", IntegerArgumentType.integer(minimum, maximum))
                        .executes(context -> target.apply(context.getSource(), setting,
                                IntegerArgumentType.getInteger(context, "amount"))))
                .then(literal("reset").executes(context -> target.apply(context.getSource(), setting, Double.NaN)));
    }

    /**
     * Creates a handler that chooses the device family from the block or panel in view.
     *
     * @param global true to configure the family globally, false for a single device
     * @return handler for the targeted device scope
     */
    private static TuningTarget deviceTarget(boolean global) {
        return (source, setting, value) -> apply(source, global, setting, value);
    }

    /**
     * Creates a handler that chooses the network from the controller in view.
     *
     * @param type family whose devices inherit the setting inside that grid
     * @return handler for the network and device family
     */
    private static TuningTarget networkTarget(DeviceChannelType type) {
        return (source, setting, value) -> {
            var controller = ChannelCommands.requireController(source);
            controller.getGrid().getService(NetworkDeviceSettingsService.class).setTuning(controller, type, setting, value);
            report(source, "Network", type, setting, value);
            return 1;
        };
    }

    /**
     * Saves a modifier for the crosshair target or its device family.
     *
     * @param source  operator issuing the command
     * @param global  true to change the device family globally
     * @param setting property being changed
     * @param value   signed modifier, card count, or NaN to reset the property
     * @return one after processing the command
     * @throws CommandSyntaxException if the selected device cannot use the property
     */
    private static int apply(CommandSourceStack source, boolean global, Setting setting, double value)
            throws CommandSyntaxException {
        var target = DeviceTarget.lookup(source);
        boolean idle = setting == Setting.IDLE_FLAT || setting == Setting.IDLE_PERCENT;
        if (setting == Setting.PROVIDER_THROUGHPUT) {
            if (target.type() != DeviceChannelType.PATTERN_PROVIDER) {
                throw PROVIDER_REQUIRED.create();
            }
        } else if (!idle && target.type() != DeviceChannelType.MOLECULAR_ASSEMBLER) {
            throw ASSEMBLER_REQUIRED.create();
        }

        if (global) {
            if (GlobalDeviceTuning.get(source.getServer()).set(target.type(), setting, value) && idle) {
                target.forEachMatchingGrid(grid -> grid.getService(DeviceTuningService.class).refreshIdlePower());
            }
        } else {
            var node = target.requireNode();
            var tuning = node.getGrid().getService(DeviceTuningService.class);
            if (tuning.setLocal(node, setting, value)) {
                target.blockEntity().setChanged();
                if (idle) {
                    tuning.refreshIdlePower();
                }
            }
        }

        report(source, global ? "Global" : "Local", target.type(), setting, value);
        return 1;
    }

    /**
     * Reports the saved tuning override or reset operation.
     *
     * @param source  recipient of the command feedback
     * @param scope   label of the changed scope
     * @param type    device family
     * @param setting property changed by the command
     * @param value   saved value, or NaN for a reset
     */
    private static void report(CommandSourceStack source, String scope, DeviceChannelType type, Setting setting,
                               double value) {
        var property = switch (setting) {
            case IDLE_FLAT -> "idle energy (AE/t)";
            case IDLE_PERCENT -> "idle energy";
            case CRAFT_FLAT -> "crafting energy (AE/t)";
            case CRAFT_PERCENT -> "crafting energy";
            case CRAFT_SPEED_PERCENT -> "crafting speed";
            case BATCH_SIZE -> "recipes per batch";
            case PROVIDER_THROUGHPUT -> "recipe operations per tick";
            case VIRTUAL_SPEED_CARDS -> "virtual speed cards";
        };
        boolean percentage = switch (setting) {
            case IDLE_PERCENT, CRAFT_PERCENT, CRAFT_SPEED_PERCENT -> true;
            default -> false;
        };
        var result = Double.isNaN(value) ? "reset" : "set to " + value + (percentage ? "%" : "");
        source.sendSuccess(() -> Component.literal(scope + " " + property + " for " + type.displayName() + " " + result + "."), false);
    }
}
