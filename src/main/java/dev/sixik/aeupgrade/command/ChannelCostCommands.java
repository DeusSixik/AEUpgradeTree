package dev.sixik.aeupgrade.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import appeng.api.networking.IGrid;
import appeng.api.networking.pathing.ChannelMode;
import appeng.api.networking.pathing.ControllerState;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.sixik.aeupgrade.channel.ChannelCostRules;
import dev.sixik.aeupgrade.channel.ChannelCostService;
import dev.sixik.aeupgrade.channel.DeviceChannelType;
import dev.sixik.aeupgrade.channel.GlobalChannelCosts;
import dev.sixik.aeupgrade.network.NetworkDeviceSettingsService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * Changes the channel cost of a device, one grid's device family, or a world-wide device family.
 */
final class ChannelCostCommands {
    /**
     * Keeps this command registration class non-instantiable.
     */
    private ChannelCostCommands() {
    }

    /**
     * Defines the channelcost subcommand under the existing operator-only root.
     *
     * @return the subcommand with local, network and global branches
     */
    static LiteralArgumentBuilder<CommandSourceStack> command() {
        var network = literal("network");
        for (var type : DeviceChannelType.values()) {
            network.then(literal(type.id())
                    .then(argument("cost", IntegerArgumentType.integer(0))
                            .executes(context -> applyNetwork(context.getSource(), type,
                                    IntegerArgumentType.getInteger(context, "cost"))))
                    .then(literal("reset").executes(context -> applyNetwork(context.getSource(), type,
                            ChannelCostRules.INHERIT))));
        }
        return literal("channelcost").then(scope("global", true)).then(scope("local", false)).then(network);
    }

    /**
     * Creates the write and reset arguments for a scope.
     *
     * @param name   subcommand name
     * @param global true for world-wide type costs, false for a single device
     * @return command branch for this scope
     */
    private static LiteralArgumentBuilder<CommandSourceStack> scope(String name, boolean global) {
        return literal(name)
                .then(argument("cost", IntegerArgumentType.integer(0))
                        .executes(context -> apply(context.getSource(), global, IntegerArgumentType.getInteger(context, "cost"))))
                .then(literal("reset").executes(context -> apply(context.getSource(), global, ChannelCostRules.INHERIT)));
    }

    /**
     * Updates the targeted device or device family and schedules relevant grids for repathing.
     *
     * @param source operator running the command
     * @param global true for world-wide type costs, false for a single device
     * @param cost   non-negative number of channels, or {@link ChannelCostRules#INHERIT} to reset
     * @return one after applying the setting
     * @throws CommandSyntaxException if the target is unsupported or the local node is unavailable
     */
    private static int apply(CommandSourceStack source, boolean global, int cost) throws CommandSyntaxException {
        var target = DeviceTarget.lookup(source);
        var type = target.type();

        if (global) {
            if (GlobalChannelCosts.get(source.getServer()).setCost(type, cost)) {
                target.forEachMatchingGrid(grid -> {
                    if (usesConfiguredCosts(grid)) {
                        grid.getPathingService().repath();
                    }
                });
            }
        } else {
            var node = target.requireNode();
            var grid = node.getGrid();
            if (grid.getService(ChannelCostService.class).setLocalCost(node, cost)) {
                target.blockEntity().setChanged();
                if (usesConfiguredCosts(grid)) {
                    grid.getPathingService().repath();
                }
            }
        }

        report(source, global ? "Global" : "Local", type, cost);
        return 1;
    }

    /**
     * Changes the family cost in the network of the selected controller.
     *
     * @param source operator looking at a controller
     * @param type   device family
     * @param cost   non-negative channel count, or {@link ChannelCostRules#INHERIT} to reset
     * @return one after applying the setting
     * @throws CommandSyntaxException if the operator is not targeting a valid controller
     */
    private static int applyNetwork(CommandSourceStack source, DeviceChannelType type, int cost)
            throws CommandSyntaxException {
        var controller = ChannelCommands.requireController(source);
        controller.getGrid().getService(NetworkDeviceSettingsService.class).setChannelCost(controller, type, cost);
        report(source, "Network", type, cost);
        return 1;
    }

    /**
     * Reports the saved channel cost or reset operation.
     *
     * @param source recipient of the command feedback
     * @param scope  label of the changed scope
     * @param type   device family
     * @param cost   channel count or the inheritance sentinel
     */
    private static void report(CommandSourceStack source, String scope, DeviceChannelType type, int cost) {
        var result = cost == ChannelCostRules.INHERIT ? "reset" : "set to " + cost;
        source.sendSuccess(() -> Component.literal(scope + " channel cost for " + type.displayName() + " " + result + "."), false);
    }

    /**
     * Checks whether repathing this grid can reflect a changed device cost immediately.
     *
     * @param grid live AE2 grid
     * @return true for a finite-channel grid with a valid controller
     */
    private static boolean usesConfiguredCosts(IGrid grid) {
        var pathing = grid.getPathingService();
        return pathing.getControllerState() == ControllerState.CONTROLLER_ONLINE
                && pathing.getChannelMode() != ChannelMode.INFINITE;
    }
}
