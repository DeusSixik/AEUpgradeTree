package dev.sixik.aeupgrade.mixin;

import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.stacks.GenericStack;
import appeng.crafting.execution.CraftingCpuLogic;
import appeng.me.cluster.implementations.CraftingCPUCluster;
import appeng.me.service.CraftingService;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.sixik.aeupgrade.tree.research.ResearchTasks;
import dev.sixik.aeupgrade.tuning.DeviceTuningService;
import dev.sixik.aeupgrade.tuning.ProviderDispatch;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Dispatches extra operations through AE2's original per-operation crafting bookkeeping.
 */
@Mixin(value = CraftingCpuLogic.class, remap = false)
public abstract class CraftingCpuLogicMixin {
    /**
     * AE2 crafting CPU that owns the active job, its inventory, and its grid.
     */
    @Shadow
    @Final
    private CraftingCPUCluster cluster;

    /**
     * True during the pass that can only use providers with extra dispatch capacity.
     */
    @Unique
    private boolean aeupgrade$providerPass;

    /**
     * Runs AE2's operation dispatch with the supplied budget.
     *
     * @param maxPatterns maximum dispatches in this pass
     * @param crafting    grid crafting service
     * @param energy      grid energy service
     * @param level       level used to resolve recipe inputs
     * @return actual recipe operations dispatched
     */
    @Shadow
    public abstract int executeCrafting(int maxPatterns, CraftingService crafting, IEnergyService energy, Level level);

    /**
     * @return requested output and quantity of the CPU's current job
     */
    @Shadow
    public abstract GenericStack getFinalJobOutput();

    /**
     * Records completed jobs while AE2 still exposes their original final output.
     *
     * @param success  whether AE2 completed the job
     * @param callback injection context for the job completion method
     */
    @Inject(method = "finishJob", at = @At("HEAD"))
    private void aeupgrade$recordResearchCraft(boolean success, CallbackInfo callback) {
        if (success) {
            ResearchTasks.recordCraft(cluster.getGrid(), getFinalJobOutput());
        }
    }

    /**
     * Offers work to Pattern Providers with remaining capacity after AE2's normal CPU scheduling.
     *
     * @param energy   grid energy service
     * @param crafting grid crafting service
     * @param callback AE2 tick callback
     */
    @Inject(method = "tickCraftingLogic", at = @At("TAIL"))
    private void aeupgrade$dispatchProviderOperations(IEnergyService energy, CraftingService crafting,
                                                      CallbackInfo callback) {
        var grid = cluster.getGrid();
        if (grid == null) {
            return;
        }
        var level = cluster.getLevel();
        int available = grid.getService(DeviceTuningService.class).availableProviderOperations();
        aeupgrade$providerPass = true;
        try {
            while (available > 0) {
                int dispatched = executeCrafting(available, crafting, energy, level);
                if (dispatched == 0) {
                    break;
                }
                available -= dispatched;
            }
        } finally {
            aeupgrade$providerPass = false;
        }
    }

    /**
     * Skips providers without remaining enhanced capacity during the additional pass.
     *
     * @param busy     AE2's original provider availability
     * @param provider provider considered for this operation
     * @return true when the provider should be skipped
     */
    @ModifyExpressionValue(method = "executeCrafting", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/crafting/ICraftingProvider;isBusy()Z"))
    private boolean aeupgrade$checkProviderCapacity(boolean busy, @Local ICraftingProvider provider) {
        return busy || aeupgrade$providerPass
                && (!(provider instanceof ProviderDispatch dispatch) || dispatch.aeupgrade$remainingOperations() == 0);
    }
}
