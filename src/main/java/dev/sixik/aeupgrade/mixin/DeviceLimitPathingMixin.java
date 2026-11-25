package dev.sixik.aeupgrade.mixin;

import appeng.api.networking.IGridNode;
import appeng.me.Grid;
import appeng.me.service.PathingService;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.sixik.aeupgrade.limit.DeviceLimitService;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies quotas before allocating channels in controller and ad-hoc grids.
 */
@Mixin(value = PathingService.class, remap = false)
public abstract class DeviceLimitPathingMixin {
    /**
     * Grid for which AE2 is calculating channels.
     */
    @Shadow
    @Final
    private Grid grid;

    /**
     * Takes the latest device quota snapshot before AE2 checks for a pending channel rebalance.
     *
     * @param callback pathing service tick callback
     */
    @Inject(method = "onServerEndTick", at = @At("HEAD"))
    private void aeupgrade$prepareDeviceLimits(CallbackInfo callback) {
        grid.getService(DeviceLimitService.class).beforePathing();
    }

    /**
     * Skips disabled devices when AE2 counts channel demand without a controller.
     *
     * @param ignored whether AE2 is already skipping this multiblock member
     * @param node    device considered for a channel
     * @return true if this device should not count towards demand
     */
    @ModifyExpressionValue(method = "calculateAdHocChannels",
            at = @At(value = "INVOKE", target = "Ljava/util/HashSet;contains(Ljava/lang/Object;)Z"))
    private boolean aeupgrade$skipOverLimitDemand(boolean ignored, @Local IGridNode node) {
        return ignored || DeviceLimitService.isBlocked(node);
    }
}
