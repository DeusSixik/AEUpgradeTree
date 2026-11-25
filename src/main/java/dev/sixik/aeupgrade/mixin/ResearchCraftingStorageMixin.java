package dev.sixik.aeupgrade.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEKey;
import dev.sixik.aeupgrade.tree.research.ResearchStorageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Passes research rewards through AE2's virtual crafting mount to the regular storage mounts.
 */
@Mixin(targets = "appeng.me.service.helpers.CraftingServiceStorage$1", remap = false)
abstract class ResearchCraftingStorageMixin {
    /**
     * Declines a research payout so AE2 continues with storage-backed inventories.
     *
     * @param key    item key being routed
     * @param amount requested quantity
     * @param mode   simulated or committed insertion
     * @param source original source of the insertion
     * @param result accepted amount from this virtual mount
     */
    @Inject(method = "insert", at = @At("HEAD"), cancellable = true)
    private void aeupgrade$skipResearchRewards(AEKey key, long amount, Actionable mode, IActionSource source,
                                               CallbackInfoReturnable<Long> result) {
        if (source instanceof ResearchStorageSource) {
            result.setReturnValue(0L);
        }
    }
}
