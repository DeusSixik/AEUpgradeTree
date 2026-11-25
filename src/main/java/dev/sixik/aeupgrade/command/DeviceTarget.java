package dev.sixik.aeupgrade.command;

import java.util.function.Consumer;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.parts.IPart;
import appeng.api.parts.IPartHost;
import appeng.hooks.ticking.TickHandler;
import appeng.me.helpers.IGridConnectedBlockEntity;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import dev.sixik.aeupgrade.channel.DeviceChannelType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.HitResult;

/**
 * A supported device selected by an operator.
 *
 * @param blockEntity block entity to dirty when local data changes
 * @param owner       block entity or panel owning the AE2 node
 * @param type        device family for global settings
 */
record DeviceTarget(BlockEntity blockEntity, Object owner, DeviceChannelType type) {
    /**
     * Error shown when the target is not a supported device.
     */
    private static final SimpleCommandExceptionType UNSUPPORTED_TARGET = new SimpleCommandExceptionType(
            Component.literal("Look at a Molecular Assembler, ME Pattern Provider, or ME Interface."));

    /**
     * Error shown when the target has not initialized its node.
     */
    private static final SimpleCommandExceptionType NO_NODE = new SimpleCommandExceptionType(
            Component.literal("The targeted ME device does not have a live network node."));

    /**
     * Selects the supported block or panel under the operator's crosshair.
     *
     * @param source command source issuing the targeting command
     * @return targeted device and its node owner
     * @throws CommandSyntaxException if the source or target cannot be used
     */
    static DeviceTarget lookup(CommandSourceStack source) throws CommandSyntaxException {
        var player = source.getPlayerOrException();
        var hit = ChannelCommands.lookAtBlock(player);
        if (hit.getType() != HitResult.Type.BLOCK) {
            throw UNSUPPORTED_TARGET.create();
        }
        var blockEntity = player.serverLevel().getBlockEntity(hit.getBlockPos());
        Object owner = blockEntity;
        if (blockEntity instanceof IPartHost host) {
            var selection = host.selectPartWorld(hit.getLocation());
            owner = selection == null ? null : selection.part;
        }
        var type = owner == null ? null : DeviceChannelType.of(owner);
        if (type == null) {
            throw UNSUPPORTED_TARGET.create();
        }
        return new DeviceTarget(blockEntity, owner, type);
    }

    /**
     * Retrieves the selected device's live node for local changes.
     *
     * @return the live AE2 node
     * @throws CommandSyntaxException if the selected device has not initialized
     */
    IGridNode requireNode() throws CommandSyntaxException {
        var node = owner instanceof IPart part ? part.getGridNode() : ((IGridConnectedBlockEntity) owner).getMainNode().getNode();
        if (node == null) {
            throw NO_NODE.create();
        }
        return node;
    }

    /**
     * Applies an action once to each live grid containing this type of device.
     *
     * @param action action for a matching grid
     */
    void forEachMatchingGrid(Consumer<IGrid> action) {
        for (var grid : TickHandler.instance().getGridList()) {
            for (var node : grid.getNodes()) {
                if (DeviceChannelType.of(node.getOwner()) == type) {
                    action.accept(grid);
                    break;
                }
            }
        }
    }
}
