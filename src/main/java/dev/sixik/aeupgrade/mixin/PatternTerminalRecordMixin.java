package dev.sixik.aeupgrade.mixin;

import appeng.client.gui.me.patternaccess.PatternContainerRecord;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Keeps equally sorted provider sections in the order their logical slots were assigned IDs.
 */
@Mixin(value = PatternContainerRecord.class, remap = false)
public abstract class PatternTerminalRecordMixin {
    /**
     * @return AE2's increasing server-side inventory ID
     */
    @Shadow
    public abstract long getServerId();

    /**
     * Uses the send order when sections share AE2's provider sort key.
     *
     * @param other    terminal section being compared
     * @param callback original provider ordering
     */
    @Inject(method = "compareTo(Lappeng/client/gui/me/patternaccess/PatternContainerRecord;)I",
            at = @At("RETURN"), cancellable = true)
    private void aeupgrade$orderedSections(PatternContainerRecord other, CallbackInfoReturnable<Integer> callback) {
        if (callback.getReturnValueI() == 0) {
            callback.setReturnValue(Long.compare(getServerId(), other.getServerId()));
        }
    }
}
