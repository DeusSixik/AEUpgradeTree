package dev.sixik.aeupgrade.mixin;

import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.pathing.ControllerState;
import appeng.me.GridNode;
import appeng.parts.networking.CablePart;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.sixik.aeupgrade.channel.CableChannelCapacity;
import dev.sixik.aeupgrade.channel.ChannelCapacityService;
import dev.sixik.aeupgrade.channel.ChannelCostRules;
import dev.sixik.aeupgrade.channel.ChannelCostService;
import dev.sixik.aeupgrade.channel.DeviceChannelAllocation;
import dev.sixik.aeupgrade.channel.DeviceChannelType;
import dev.sixik.aeupgrade.limit.DeviceLimitService;
import dev.sixik.aeupgrade.tuning.DeviceTuningService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Constant;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyConstant;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies configured cable capacity and tracks each device's own allocation separately from transit channels.
 */
@Mixin(value = GridNode.class, remap = false)
public abstract class GridNodeMixin implements DeviceChannelAllocation {
    /**
     * Total channels through this node in the current AE2 calculation.
     */
    @Shadow
    int usedChannels;

    /**
     * Total channels through this node in the last completed AE2 calculation.
     */
    @Shadow
    private int lastUsedChannels;

    /**
     * Channels granted to this device itself in the calculation being finalized.
     */
    @Unique
    private int aeupgrade$pendingAllocation;

    /**
     * Channels granted to this device itself in the last completed calculation.
     */
    @Unique
    private int aeupgrade$allocation;

    /**
     * {@inheritDoc}
     */
    @Override
    public void aeupgrade$stageAllocation(int channels) {
        aeupgrade$pendingAllocation = channels;
    }

    /**
     * Adds the full local allocation to the transit count.
     *
     * @param vanillaChannels default count of one granted channel
     * @return channels granted to this device when AE2 runs its local increment
     */
    @ModifyConstant(method = "propagateChannelsUpwards", constant = @Constant(intValue = 1))
    private int aeupgrade$propagateLocalCost(int vanillaChannels) {
        return Math.max(vanillaChannels, aeupgrade$pendingAllocation);
    }

    /**
     * Clears the previous controller allocation when AE2 calculates an ad-hoc or conflicted grid.
     *
     * @param channels AE2's new ad-hoc transit count
     * @param callback callback for the assignment
     */
    @Inject(method = "setAdHocChannels", at = @At("HEAD"))
    private void aeupgrade$clearControllerAllocation(int channels, CallbackInfo callback) {
        aeupgrade$pendingAllocation = 0;
    }

    /**
     * Commits the local grant before notifying listeners, including when only the local grant changed.
     *
     * @param callback callback for AE2's finalization pass
     */
    @Inject(method = "finalizeChannels", at = @At("HEAD"))
    private void aeupgrade$finalizeAllocation(CallbackInfo callback) {
        int previous = aeupgrade$allocation;
        aeupgrade$allocation = aeupgrade$pendingAllocation;
        var node = (GridNode) (Object) this;
        if (previous != aeupgrade$allocation && usedChannels == lastUsedChannels
                && node.getInternalGrid() != null && DeviceChannelType.of(node.getOwner()) != null) {
            node.notifyStatusChange(IGridNodeListener.State.CHANNEL);
        }
    }

    /**
     * Evaluates configured devices using their own grant rather than channels passing through them.
     *
     * @param vanillaResult AE2's result for the node's original flag
     * @return true if the device is allowed to run and meets its channel requirement
     */
    @ModifyReturnValue(method = "meetsChannelRequirements", at = @At("RETURN"))
    private boolean aeupgrade$meetsDeviceCost(boolean vanillaResult) {
        var node = (IGridNode) this;
        if (DeviceLimitService.isBlocked(node)) {
            return false;
        }
        int cost = ChannelCostService.configuredCost(node);
        return cost == ChannelCostRules.INHERIT ? vanillaResult : aeupgrade$allocation >= cost;
    }

    /**
     * Makes AE2's energy cache use the adjusted idle draw for the device.
     *
     * @param baseDraw AE2's original idle draw in AE per tick
     * @return idle draw after applying the device's modifiers
     */
    @ModifyReturnValue(method = "getIdlePowerUsage", at = @At("RETURN"))
    private double aeupgrade$idleDraw(double baseDraw) {
        return DeviceTuningService.energy((IGridNode) this, baseDraw, false);
    }

    /**
     * Applies the grid limit to finite-capacity cable nodes in networks with a valid controller.
     *
     * @param limit capacity computed by AE2, in channels
     * @return adjusted capacity for this cable, or the original capacity when no override applies
     */
    @ModifyReturnValue(method = "getMaxChannels", at = @At("RETURN"))
    private int aeupgrade$adjustCableCapacity(int limit) {
        var node = (IGridNode) this;
        if (limit == 0 || limit == Integer.MAX_VALUE || !(node.getOwner() instanceof CablePart)) {
            return limit;
        }
        var grid = node.getGrid();
        if (grid.getPathingService().getControllerState() != ControllerState.CONTROLLER_ONLINE) {
            return limit;
        }

        int normalLimit = grid.getService(ChannelCapacityService.class).getNormalLimit();
        return normalLimit == CableChannelCapacity.DEFAULT_NORMAL_LIMIT
                ? limit
                : CableChannelCapacity.scale(limit, normalLimit);
    }
}
