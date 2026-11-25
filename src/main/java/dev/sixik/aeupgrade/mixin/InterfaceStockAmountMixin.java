package dev.sixik.aeupgrade.mixin;

import appeng.api.stacks.GenericStack;
import appeng.helpers.InterfaceLogicHost;
import appeng.helpers.externalstorage.GenericStackInv;
import appeng.menu.implementations.SetStockAmountMenu;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sixik.aeupgrade.stock.InterfaceSlots;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Handles an interface slot being locked while its stock amount editor is open.
 */
@Mixin(value = SetStockAmountMenu.class, remap = false)
public abstract class InterfaceStockAmountMixin {
    /**
     * Host of the edited stocking slot.
     */
    @Shadow
    @Final
    private InterfaceLogicHost host;

    /**
     * Permits clearing a stored configuration and restricts changes to active slots.
     *
     * @param config   stocking inventory
     * @param slot     index of the edited configuration
     * @param value    new configuration, or null to clear it
     * @param original AE2 configuration update
     */
    @WrapOperation(method = "confirm", at = @At(value = "INVOKE",
            target = "Lappeng/helpers/externalstorage/GenericStackInv;setStack(ILappeng/api/stacks/GenericStack;)V"))
    private void aeupgrade$editUnlockedAmount(GenericStackInv config, int slot, @Nullable GenericStack value,
                                              Operation<Void> original) {
        if (value == null || !InterfaceSlots.supports(host)
                || slot < ((InterfaceSlots) host.getInterfaceLogic()).aeupgrade$slotCapacity()) {
            original.call(config, slot, value);
        }
    }
}
