package dev.sixik.aeupgrade.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.electronwill.nightconfig.core.io.WritingException;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import com.mojang.logging.LogUtils;
import dev.sixik.aeupgrade.channel.DeviceChannelType;
import dev.sixik.aeupgrade.limit.DeviceLimitConfig;
import dev.sixik.aeupgrade.limit.DeviceLimitService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import org.slf4j.Logger;

/**
 * Reads and changes server defaults and device limits on individual grids.
 */
final class DeviceLimitCommands {
    /**
     * Logger for failures while saving the server configuration.
     */
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Error shown when the server configuration is not loaded.
     */
    private static final SimpleCommandExceptionType CONFIG_UNAVAILABLE = new SimpleCommandExceptionType(
            Component.literal("The AEUpgradeTree server configuration is not loaded."));

    /**
     * Error shown when the server configuration cannot be written to disk.
     */
    private static final SimpleCommandExceptionType SAVE_FAILED = new SimpleCommandExceptionType(
            Component.literal("Could not save the AEUpgradeTree server configuration. Check the server log."));

    /**
     * Error shown when an operator adds or removes slots from an explicitly unlimited grid.
     */
    private static final SimpleCommandExceptionType UNLIMITED_OVERRIDE = new SimpleCommandExceptionType(
            Component.literal("Use devicelimit network set to give this ME network a finite limit before adjusting it."));

    /**
     * Keeps this command registration class non-instantiable.
     */
    private DeviceLimitCommands() {
    }

    /**
     * Builds the operator-only branches for reading and writing device limits.
     *
     * @return command tree with a literal for each supported device family
     */
    static LiteralArgumentBuilder<CommandSourceStack> command() {
        var get = literal("get");
        var set = literal("set");
        var networkGet = literal("get");
        var networkSet = literal("set");
        var networkAdd = literal("add");
        var networkRemove = literal("remove");
        var networkReset = literal("reset");
        for (var type : DeviceChannelType.values()) {
            var id = type.id();
            get.then(literal(id).executes(context -> {
                int limit = DeviceLimitConfig.getLimit(type);
                report(context.getSource(), type, limit);
                return limit;
            }));
            set.then(literal(id)
                    .then(argument("limit", IntegerArgumentType.integer(DeviceLimitConfig.UNLIMITED))
                            .executes(context -> setLimit(context.getSource(), type,
                                    IntegerArgumentType.getInteger(context, "limit")))));
            networkGet.then(literal(id).executes(context -> networkLimit(context.getSource(), type, NetworkAction.GET, 0)));
            networkSet.then(literal(id)
                    .then(argument("limit", IntegerArgumentType.integer(DeviceLimitConfig.UNLIMITED))
                            .executes(context -> networkLimit(context.getSource(), type, NetworkAction.SET,
                                    IntegerArgumentType.getInteger(context, "limit")))));
            networkAdd.then(literal(id).then(argument("amount", IntegerArgumentType.integer(1))
                    .executes(context -> networkLimit(context.getSource(), type, NetworkAction.ADJUST,
                            IntegerArgumentType.getInteger(context, "amount")))));
            networkRemove.then(literal(id).then(argument("amount", IntegerArgumentType.integer(1))
                    .executes(context -> networkLimit(context.getSource(), type, NetworkAction.ADJUST,
                            -IntegerArgumentType.getInteger(context, "amount")))));
            networkReset.then(literal(id).executes(context -> networkLimit(context.getSource(), type, NetworkAction.RESET, 0)));
        }
        return literal("devicelimit").then(get).then(set)
                .then(literal("network")
                        .then(networkGet)
                        .then(networkSet)
                        .then(networkAdd)
                        .then(networkRemove)
                        .then(networkReset));
    }

    /**
     * Reads or changes the device limit for the grid of the targeted controller.
     *
     * @param source command source for the operator looking at the controller
     * @param type   supported device family
     * @param action operation to perform on the network setting
     * @param amount fixed device limit (including -1) for a set, signed slot delta for an adjustment, or zero otherwise
     * @return effective limit, or -1 for unlimited, on read, and one after a change
     * @throws CommandSyntaxException if no valid controller is targeted or an unlimited override is adjusted
     */
    private static int networkLimit(CommandSourceStack source, DeviceChannelType type, NetworkAction action, int amount)
            throws CommandSyntaxException {
        var controller = ChannelCommands.requireController(source);
        var service = controller.getGrid().getService(DeviceLimitService.class);
        switch (action) {
            case GET -> {
            }
            case SET -> service.setLimit(controller, type, amount);
            case RESET -> service.resetLimit(controller, type);
            case ADJUST -> {
                var current = service.getSetting(type);
                if (current.overridden() && current.value() == DeviceLimitConfig.UNLIMITED) {
                    throw UNLIMITED_OVERRIDE.create();
                }
                service.adjustLimit(controller, type, amount);
            }
        }

        var setting = service.getSetting(type);
        int limit = service.effectiveLimit(type);
        var value = limit == DeviceLimitConfig.UNLIMITED ? "unlimited" : Integer.toString(limit);
        var mode = setting.overridden() ? "network override" : "server default, bonus +" + setting.value();
        source.sendSuccess(() -> Component.literal(
                type.displayName() + " limit for this ME network: " + value + " (" + mode + ")."), false);
        return action == NetworkAction.GET ? limit : 1;
    }

    /**
     * Saves a limit and confirms the change to the operator.
     *
     * @param source command source for the operator
     * @param type   supported device family
     * @param limit  maximum operating devices per grid, or -1 for unlimited
     * @return one after saving the limit
     * @throws CommandSyntaxException if the server configuration is unavailable or cannot be saved
     */
    private static int setLimit(CommandSourceStack source, DeviceChannelType type, int limit)
            throws CommandSyntaxException {
        try {
            DeviceLimitConfig.setLimit(type, limit);
        } catch (IllegalStateException failure) {
            throw CONFIG_UNAVAILABLE.create();
        } catch (WritingException failure) {
            LOGGER.error("Could not save the AEUpgradeTree device limit for {}", type.id(), failure);
            throw SAVE_FAILED.create();
        }
        report(source, type, limit);
        return 1;
    }

    /**
     * Displays a device limit in the command response.
     *
     * @param source command source receiving the result
     * @param type   supported device family
     * @param limit  maximum operating devices per grid, or -1 for unlimited
     */
    private static void report(CommandSourceStack source, DeviceChannelType type, int limit) {
        var value = limit == DeviceLimitConfig.UNLIMITED ? "unlimited" : Integer.toString(limit);
        source.sendSuccess(() -> Component.literal(type.displayName() + " limit per ME network: " + value + "."), false);
    }

    /**
     * Operations shared by the network command branches.
     */
    private enum NetworkAction {
        GET, SET, ADJUST, RESET
    }
}
