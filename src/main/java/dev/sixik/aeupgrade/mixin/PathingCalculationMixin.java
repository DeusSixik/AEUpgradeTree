package dev.sixik.aeupgrade.mixin;

import appeng.me.GridNode;
import appeng.me.pathfinding.IPathItem;
import appeng.me.pathfinding.PathingCalculation;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.sixik.aeupgrade.channel.ChannelCostRules;
import dev.sixik.aeupgrade.channel.ChannelCostService;
import dev.sixik.aeupgrade.channel.DeviceChannelAllocation;
import dev.sixik.aeupgrade.limit.DeviceLimitService;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Routes configurable channel costs through AE2's controller path calculation.
 */
@Mixin(value = PathingCalculation.class, remap = false)
public abstract class PathingCalculationMixin {
    /**
     * AE2's total number of allocated channels for this routing pass.
     */
    @Shadow
    private int channelsInUse;

    /**
     * Cost in channels of the node considered by the next call to AE2's tryUseChannel.
     */
    @Unique
    private int aeupgrade$currentCost = 1;

    /**
     * Resolves whether the candidate requests channels and records its cost for the allocation attempt.
     *
     * @param requiresChannel AE2's REQUIRE_CHANNEL flag on this candidate
     * @param candidate       current path item discovered by the search
     * @return true when the candidate needs an allocation
     */
    @ModifyExpressionValue(method = "processQueue", at = @At(value = "INVOKE",
            target = "Lappeng/me/pathfinding/IPathItem;hasFlag(Lappeng/api/networking/GridFlags;)Z", ordinal = 0))
    private boolean aeupgrade$requiresChannels(boolean requiresChannel, @Local(ordinal = 1) IPathItem candidate) {
        int cost = ChannelCostRules.INHERIT;
        if (candidate instanceof GridNode node) {
            if (DeviceLimitService.isBlocked(node)) {
                return false;
            }
            cost = ChannelCostService.configuredCost(node);
        }
        aeupgrade$currentCost = cost == ChannelCostRules.INHERIT ? 1 : cost;
        return cost == ChannelCostRules.INHERIT ? requiresChannel : cost > 0;
    }

    /**
     * Makes AE2 reject an allocation when any node on the route lacks room for the full cost.
     *
     * @param capacity original route capacity in channels
     * @return the first used-channel count that cannot fit the allocation
     */
    @ModifyExpressionValue(method = "tryUseChannel", at = @At(value = "INVOKE",
            target = "Lappeng/me/GridNode;getMaxChannels()I"))
    private int aeupgrade$capacityThreshold(int capacity) {
        return ChannelCostRules.rejectionThreshold(capacity, aeupgrade$currentCost);
    }

    /**
     * Reserves the full cost instead of one channel after AE2 validates the entire route.
     *
     * @param vanillaCost AE2's original reservation size of one channel
     * @return number of channels reserved at each bottleneck
     */
    @ModifyExpressionValue(method = "tryUseChannel", at = @At(value = "CONSTANT", args = "intValue=1", ordinal = 0))
    private int aeupgrade$reservationSize(int vanillaCost) {
        return aeupgrade$currentCost;
    }

    /**
     * Passes each node's awarded channels to its propagation step and updates the grid total.
     *
     * @param granted whether AE2 awarded channels to this node
     * @param node    node whose own allocation is being propagated
     * @return the original grant flag for the rest of AE2's calculation
     */
    @ModifyExpressionValue(method = "propagateAssignments", at = @At(value = "INVOKE",
            target = "Ljava/util/Set;contains(Ljava/lang/Object;)Z", ordinal = 0))
    private boolean aeupgrade$stageAllocation(boolean granted, @Local GridNode node) {
        int channels = granted ? Math.max(1, ChannelCostService.configuredCost(node)) : 0;
        ((DeviceChannelAllocation) node).aeupgrade$stageAllocation(channels);
        if (granted) {
            // AE2 adds the first channel after propagating this node.
            channelsInUse += channels - 1;
        }
        return granted;
    }
}
