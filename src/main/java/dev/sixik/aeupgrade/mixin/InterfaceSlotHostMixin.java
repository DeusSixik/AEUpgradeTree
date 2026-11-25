package dev.sixik.aeupgrade.mixin;

import appeng.api.networking.IManagedGridNode;
import appeng.blockentity.misc.InterfaceBlockEntity;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import appeng.parts.misc.InterfacePart;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sixik.aeupgrade.stock.InterfaceSlotRange;
import dev.sixik.aeupgrade.stock.InterfaceSlots;
import net.minecraft.world.item.Item;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Gives the stock block and panel full backing storage for variable slot limits.
 */
@Mixin(value = {InterfaceBlockEntity.class, InterfacePart.class}, remap = false)
public abstract class InterfaceSlotHostMixin {
    /**
     * Builds the stock interface with AE2's configurable-size constructor.
     *
     * @param node     interface grid node
     * @param host     owner of the stocking logic
     * @param item     interface item that determines compatible upgrades
     * @param original original logic constructor
     * @return interface logic with matching config, storage and crafting slot counts
     */
    @WrapOperation(method = "createLogic", at = @At(value = "NEW",
            target = "(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/InterfaceLogicHost;Lnet/minecraft/world/item/Item;)Lappeng/helpers/InterfaceLogic;"))
    private InterfaceLogic aeupgrade$createExpandedLogic(IManagedGridNode node, InterfaceLogicHost host,
                                                         Item item, Operation<InterfaceLogic> original) {
        return InterfaceSlots.supports(host)
                ? new InterfaceLogic(node, host, item, InterfaceSlotRange.MAX_CAPACITY)
                : original.call(node, host, item);
    }
}
