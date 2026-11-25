package dev.sixik.aeupgrade.mixin;

import java.util.List;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.crafting.IPatternDetails;
import appeng.api.networking.IGridNode;
import appeng.api.networking.energy.IEnergyService;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sixik.aeupgrade.channel.ChannelCostService;
import dev.sixik.aeupgrade.limit.DeviceLimitService;
import dev.sixik.aeupgrade.tuning.AssemblerBatch;
import dev.sixik.aeupgrade.tuning.AssemblerDispatchLoad;
import dev.sixik.aeupgrade.tuning.DeviceTuningMath;
import dev.sixik.aeupgrade.tuning.DeviceTuningService;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the configured channel, speed, and working energy rules to an assembler.
 */
@Mixin(value = MolecularAssemblerBlockEntity.class, remap = false)
public abstract class MolecularAssemblerMixin implements AssemblerDispatchLoad {
    /**
     * NBT key for jobs and results of a multi-recipe assembly cycle.
     */
    @Unique
    private static final String AEUPGRADE_BATCH_TAG = "aeupgrade:assembler_batch";

    /**
     * Progress applied by AE2 to the current single recipe, in units out of 100.
     */
    @Shadow
    private double progress;

    /**
     * Reusable AE2 crafting matrix.
     */
    @Shadow
    @Final
    private CraftingContainer craftingInv;

    /**
     * In-flight batch, or null when AE2 is scheduling ordinary work.
     */
    @Unique
    private AssemblerBatch aeupgrade$batch;

    /**
     * Fractional work that was paid for but did not make up a full unit of progress.
     */
    @Unique
    private double aeupgrade$fractionalProgress;

    /**
     * {@inheritDoc}
     */
    @Override
    public int aeupgrade$queuedRecipes() {
        if (aeupgrade$batch != null) {
            return aeupgrade$batch.queuedRecipes();
        }
        var assembler = (MolecularAssemblerBlockEntity) (Object) this;
        return assembler.getInternalInventory().isEmpty() ? 0 : Integer.MAX_VALUE;
    }

    /**
     * Updates AE2's awake state after all batch work has completed.
     */
    @Shadow
    private void updateSleepiness() {
        throw new AssertionError();
    }

    /**
     * Computes funded work at the adjusted speed without losing fractional progress across ticks.
     *
     * @param ticks             elapsed game ticks to process
     * @param baseSpeed         AE2 progress per tick after speed cards
     * @param energyPerProgress AE2 energy cost per unit of progress
     * @param callback          returns full units of progress to AE2 where the speed is overridden
     */
    @Inject(method = "userPower", at = @At("HEAD"), cancellable = true)
    private void aeupgrade$applySpeed(int ticks, int baseSpeed, double energyPerProgress,
                                      CallbackInfoReturnable<Integer> callback) {
        var assembler = (MolecularAssemblerBlockEntity) (Object) this;
        var node = assembler.getMainNode().getNode();
        double percent = DeviceTuningService.speedPercent(node);
        if (percent == 0) {
            aeupgrade$fractionalProgress = 0;
            return;
        }
        double remaining = DeviceTuningMath.PROGRESS_PER_CYCLE - progress - aeupgrade$fractionalProgress;
        double step = DeviceTuningService.advance(node, DeviceTuningMath.speed(baseSpeed, percent), energyPerProgress,
                remaining, ticks, 1);
        if (step >= remaining) {
            aeupgrade$fractionalProgress = 0;
            callback.setReturnValue((int) Math.ceil(DeviceTuningMath.PROGRESS_PER_CYCLE - progress));
            return;
        }
        double accumulated = aeupgrade$fractionalProgress + step;
        int fullProgress = (int) accumulated;
        aeupgrade$fractionalProgress = accumulated - fullProgress;
        callback.setReturnValue(fullProgress);
    }

    /**
     * Clears fractional work when AE2 changes its current plan.
     *
     * @param callback planning callback
     */
    @Inject(method = "recalculatePlan", at = @At("HEAD"))
    private void aeupgrade$resetPartialProgress(CallbackInfo callback) {
        aeupgrade$fractionalProgress = 0;
    }

    /**
     * Lets AE2's crafting switch use virtual acceleration alongside physical cards.
     *
     * @param installed number of physical speed cards AE2 counted
     * @return effective speed card count, up to AE2's maximum
     */
    @ModifyExpressionValue(method = "tickingRequest", at = @At(value = "INVOKE",
            target = "Lappeng/api/upgrades/IUpgradeInventory;getInstalledUpgrades(Lnet/minecraft/world/level/ItemLike;)I"))
    private int aeupgrade$virtualSpeed(int installed) {
        var assembler = (MolecularAssemblerBlockEntity) (Object) this;
        return DeviceTuningService.speedCards(assembler.getMainNode().getNode(), installed);
    }

    /**
     * Preserves the configured energy cost for crafts using AE2's original speed path.
     *
     * @param energy     grid energy service
     * @param base       original AE requested by AE2 for the entire step
     * @param action     energy extraction mode
     * @param multiplier AE2's energy conversion
     * @param draw       original energy extraction operation
     * @param ticks      elapsed game ticks in the step
     * @param speed      AE2 progress per tick
     * @param tax        energy per unit of progress
     * @return base energy equivalent of the steps actually funded
     */
    @WrapOperation(method = "userPower", at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/energy/IEnergyService;extractAEPower(DLappeng/api/config/Actionable;Lappeng/api/config/PowerMultiplier;)D"))
    private double aeupgrade$chargeCrafting(IEnergyService energy, double base, Actionable action,
                                            PowerMultiplier multiplier, Operation<Double> draw, int ticks, int speed, double tax) {
        if (ticks <= 0) {
            return draw.call(energy, base, action, multiplier);
        }
        var assembler = (MolecularAssemblerBlockEntity) (Object) this;
        double basePerTick = speed * tax;
        double perTick = DeviceTuningService.energy(assembler.getMainNode().getNode(), basePerTick, true);
        if (perTick == basePerTick) {
            return draw.call(energy, base, action, multiplier);
        }
        if (perTick == 0) {
            return energy.isNetworkPowered() ? base : 0;
        }
        double requested = perTick * ticks;
        return DeviceTuningMath.equivalentEnergy(base, requested, draw.call(energy, requested, action, multiplier));
    }

    /**
     * Checks the allocation and optionally accepts one pattern push into a multi-recipe batch.
     *
     * @param pattern   pattern offered by AE2's provider
     * @param table     ingredients for one recipe operation
     * @param direction side that should receive the results
     * @param callback  tracks whether the assembler took ownership of the ingredients
     */
    @Inject(method = "pushPattern", at = @At("HEAD"), cancellable = true)
    private void aeupgrade$checkNewCraft(IPatternDetails pattern, KeyCounter[] table, Direction direction,
                                         CallbackInfoReturnable<Boolean> callback) {
        var assembler = (MolecularAssemblerBlockEntity) (Object) this;
        var node = assembler.getMainNode().getNode();
        if (aeupgrade$waitingForActivation(node)) {
            callback.setReturnValue(false);
            return;
        }
        int limit = DeviceTuningService.batchSize(node);
        if (aeupgrade$batch == null && (limit == 1 || !assembler.getInternalInventory().isEmpty())) {
            return;
        }
        if (!(pattern instanceof IMolecularAssemblerSupportedPattern supported) || direction == null || node == null) {
            callback.setReturnValue(false);
            return;
        }

        var batch = aeupgrade$batch == null ? new AssemblerBatch() : aeupgrade$batch;
        boolean accepted = batch.enqueue(supported, table, direction, limit, node.getLevel().getGameTime());
        if (accepted) {
            aeupgrade$batch = batch;
            node.getGrid().getTickManager().wakeDevice(node);
            assembler.saveChanges();
        }
        callback.setReturnValue(accepted);
    }

    /**
     * Pauses work while channels are missing and advances active batches in the assembler's tick.
     *
     * @param node               assembler's live node
     * @param ticksSinceLastCall ticks since AE2 last ran the device
     * @param callback           scheduler result of the active batch or the allocation pause
     */
    @Inject(method = "tickingRequest", at = @At("HEAD"), cancellable = true)
    private void aeupgrade$pauseCraft(IGridNode node, int ticksSinceLastCall,
                                      CallbackInfoReturnable<TickRateModulation> callback) {
        if (aeupgrade$waitingForActivation(node)) {
            callback.setReturnValue(TickRateModulation.IDLE);
        } else if (aeupgrade$batch != null) {
            var assembler = (MolecularAssemblerBlockEntity) (Object) this;
            var result = aeupgrade$batch.tick(assembler, node, ticksSinceLastCall, craftingInv);
            if (!aeupgrade$batch.active()) {
                aeupgrade$batch = null;
                updateSleepiness();
            }
            callback.setReturnValue(result);
        }
    }

    /**
     * Keeps accepted batch work awake when AE2 recomputes the assembler's tick request.
     *
     * @param vanilla whether the normal output slot is occupied
     * @return true while the assembler has output or accepted batch work
     */
    @ModifyReturnValue(method = "canPush", at = @At("RETURN"))
    private boolean aeupgrade$wakeForBatch(boolean vanilla) {
        return vanilla || aeupgrade$batch != null && aeupgrade$batch.active();
    }

    /**
     * Shows the current cycle's progress through the assembler's normal menu.
     *
     * @param vanilla progress in AE2's single recipe path
     * @return current work progress in units out of 100
     */
    @ModifyReturnValue(method = "getCraftingProgress", at = @At("RETURN"))
    private int aeupgrade$displayBatchProgress(int vanilla) {
        return aeupgrade$batch == null ? vanilla : aeupgrade$batch.progress();
    }

    /**
     * Saves accepted recipe inputs and queued outputs with the block entity.
     *
     * @param tag        writable assembler data
     * @param registries item registry lookup
     * @param callback   AE2's save callback
     */
    @Inject(method = "saveAdditional", at = @At("TAIL"))
    private void aeupgrade$saveBatch(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo callback) {
        if (aeupgrade$batch != null && aeupgrade$batch.active()) {
            tag.put(AEUPGRADE_BATCH_TAG, aeupgrade$batch.save(registries));
        }
    }

    /**
     * Restores batch data before AE2 recalculates its awake state.
     *
     * @param tag        saved assembler data
     * @param registries item registry lookup
     * @param callback   AE2's load callback
     */
    @Inject(method = "loadTag", at = @At("HEAD"))
    private void aeupgrade$loadBatch(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo callback) {
        aeupgrade$batch = tag.contains(AEUPGRADE_BATCH_TAG, Tag.TAG_COMPOUND)
                ? AssemblerBatch.load(tag.getCompound(AEUPGRADE_BATCH_TAG), registries)
                : null;
    }

    /**
     * Returns batch-owned ingredients and pending results when the assembler breaks.
     *
     * @param level    world where the assembler is being broken
     * @param pos      assembler position
     * @param drops    mutable drop list
     * @param callback AE2's drop callback
     */
    @Inject(method = "addAdditionalDrops", at = @At("TAIL"))
    private void aeupgrade$dropBatch(Level level, BlockPos pos, List<ItemStack> drops, CallbackInfo callback) {
        if (aeupgrade$batch != null) {
            aeupgrade$batch.addDrops(drops);
        }
    }

    /**
     * Releases batch state along with AE2's normal inventory.
     *
     * @param callback AE2's clear-content callback
     */
    @Inject(method = "clearContent", at = @At("HEAD"))
    private void aeupgrade$clearBatch(CallbackInfo callback) {
        aeupgrade$batch = null;
        aeupgrade$fractionalProgress = 0;
    }

    /**
     * Includes the channel requirement when reporting server-side assembler activity.
     *
     * @param active AE2's original status
     * @return true if originally active and any configured requirement has been met
     */
    @ModifyReturnValue(method = "isActive", at = @At("RETURN"))
    private boolean aeupgrade$reflectAllocation(boolean active) {
        var assembler = (MolecularAssemblerBlockEntity) (Object) this;
        return active && !aeupgrade$waitingForActivation(assembler.getMainNode().getNode());
    }

    /**
     * Determines whether the assembler must pause for a slot or a complete channel allocation.
     *
     * @param node live device node, or null on the client or before initialization
     * @return true if the assembler is over the device limit or waiting for required channels
     */
    @Unique
    private static boolean aeupgrade$waitingForActivation(IGridNode node) {
        return DeviceLimitService.isBlocked(node) || ChannelCostService.configuredCost(node) > 0 && !node.isActive();
    }
}
