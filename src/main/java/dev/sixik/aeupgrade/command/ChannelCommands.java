package dev.sixik.aeupgrade.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ChannelMode;
import appeng.api.networking.pathing.ControllerState;
import appeng.blockentity.networking.ControllerBlockEntity;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.sixik.aeupgrade.AEUpgradeTree;
import dev.sixik.aeupgrade.channel.CableChannelCapacity;
import dev.sixik.aeupgrade.channel.ChannelCapacityService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Registers operator commands for testing cable limits on an ME controller's grid.
 */
public final class ChannelCommands {
    /**
     * Error shown when the player is not aiming at a reachable ME Controller block.
     */
    private static final SimpleCommandExceptionType NO_CONTROLLER = new SimpleCommandExceptionType(
            Component.literal("Look at an ME Controller within block interaction range."));

    /**
     * Error shown when the targeted controller has no valid controller grid.
     */
    private static final SimpleCommandExceptionType INVALID_GRID = new SimpleCommandExceptionType(
            Component.literal("The targeted ME Controller must belong to a valid ME network."));

    /**
     * Error shown when the grid's channel capacity is unlimited.
     */
    private static final SimpleCommandExceptionType INFINITE_CHANNELS = new SimpleCommandExceptionType(
            Component.literal("This ME network uses infinite channels."));

    /**
     * Keeps this command registration class non-instantiable.
     */
    private ChannelCommands() {
    }

    /**
     * Registers the add and remove subcommands with Minecraft's server command dispatcher.
     *
     * @param dispatcher command dispatcher for the current server
     */
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal(AEUpgradeTree.MODID)
                .requires(source -> source.hasPermission(2))
                .then(ChannelCostCommands.command())
                .then(DeviceLimitCommands.command())
                .then(PatternSlotCommands.command())
                .then(InterfaceSlotCommands.command())
                .then(UpgradeTreeCommands.command())
                .then(DeviceTuningCommands.energy())
                .then(DeviceTuningCommands.speed())
                .then(DeviceTuningCommands.batch())
                .then(DeviceTuningCommands.throughput())
                .then(literal("channels")
                        .then(literal("add").then(argument("amount", IntegerArgumentType.integer(1))
                                .executes(context -> adjust(context.getSource(), IntegerArgumentType.getInteger(context, "amount")))))
                        .then(literal("remove").then(argument("amount", IntegerArgumentType.integer(1))
                                .executes(context -> adjust(context.getSource(), -IntegerArgumentType.getInteger(context, "amount")))))));
    }

    /**
     * Resolves the controller the operator is looking at and changes that grid's cable limits.
     *
     * @param source command source for the player issuing the command
     * @param delta  signed change to the default-mode normal cable capacity, in channels
     * @return one on successful command execution
     * @throws CommandSyntaxException if the source is not a player, the controller target is invalid,
     *                                or the grid runs with infinite channels
     */
    private static int adjust(CommandSourceStack source, int delta) throws CommandSyntaxException {
        var node = requireController(source);
        var grid = node.getGrid();
        var pathing = grid.getPathingService();
        if (pathing.getChannelMode() == ChannelMode.INFINITE) {
            throw INFINITE_CHANNELS.create();
        }

        var service = grid.getService(ChannelCapacityService.class);
        service.adjust(node, delta);
        int normalLimit = service.getNormalLimit();
        int modeFactor = pathing.getChannelMode().getCableCapacityFactor();
        int normal = CableChannelCapacity.scale(CableChannelCapacity.DEFAULT_NORMAL_LIMIT * modeFactor, normalLimit);
        int dense = CableChannelCapacity.scale(CableChannelCapacity.DEFAULT_DENSE_LIMIT * modeFactor, normalLimit);
        source.sendSuccess(() -> Component.literal("ME cable channels: " + normal + " normal, " + dense + " dense."), false);
        return 1;
    }

    /**
     * Gets the controller node under the player's crosshair in a valid controller grid.
     *
     * @param source command source for the player issuing the command
     * @return live AE2 controller node
     * @throws CommandSyntaxException if the source is not a player or the controller target is invalid
     */
    static IGridNode requireController(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        var level = player.serverLevel();
        var hit = lookAtBlock(player);
        if (hit.getType() != HitResult.Type.BLOCK
                || !(level.getBlockEntity(hit.getBlockPos()) instanceof ControllerBlockEntity controller)) {
            throw NO_CONTROLLER.create();
        }

        var node = controller.getMainNode().getNode();
        if (node == null || node.getGrid().getPathingService().getControllerState() != ControllerState.CONTROLLER_ONLINE) {
            throw INVALID_GRID.create();
        }
        return node;
    }

    /**
     * Raycasts from the server player's eyes along their current view within block interaction range.
     *
     * @param player player issuing a targeting command
     * @return the selected block or a miss
     */
    static BlockHitResult lookAtBlock(ServerPlayer player) {
        var eyes = player.getEyePosition();
        var target = eyes.add(player.getLookAngle().scale(player.blockInteractionRange()));
        return player.serverLevel().clip(new ClipContext(eyes, target, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
    }
}
