package dev.sixik.aeupgrade.mixin;

import appeng.me.GridNode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Exposes AE2's idle energy baseline for the controller's diagnostics.
 */
@Mixin(value = GridNode.class, remap = false)
public interface GridNodePowerAccessor {
    /**
     * @return unmodified idle draw in AE/t
     */
    @Accessor("idlePowerUsage")
    double aeupgrade$baseIdlePower();
}
