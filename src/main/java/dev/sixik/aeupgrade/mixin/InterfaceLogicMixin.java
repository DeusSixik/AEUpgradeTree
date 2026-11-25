package dev.sixik.aeupgrade.mixin;

import appeng.api.config.Actionable;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.AEKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.MEStorage;
import appeng.helpers.InterfaceLogic;
import appeng.helpers.InterfaceLogicHost;
import appeng.util.ConfigInventory;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sixik.aeupgrade.limit.DeviceLimitService;
import dev.sixik.aeupgrade.stock.InterfaceSlotRange;
import dev.sixik.aeupgrade.stock.InterfaceSlotService;
import dev.sixik.aeupgrade.stock.InterfaceSlots;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the grid's device and stocking slot limits to the native interface logic.
 */
@Mixin(value = InterfaceLogic.class, remap = false)
public abstract class InterfaceLogicMixin implements InterfaceSlots {
    /**
     * Interface's server-side grid node.
     */
    @Shadow
    @Final
    protected IManagedGridNode mainNode;

    /**
     * Owner of the stocking logic.
     */
    @Shadow
    @Final
    protected InterfaceLogicHost host;

    /**
     * Work calculated by AE2 for each slot pair.
     */
    @Shadow
    @Final
    private GenericStack[] plannedWork;

    /**
     * True for stock blocks and panels with extended backing inventories.
     */
    @Unique
    private boolean aeupgrade$expanded;

    /**
     * Cached number of unlocked pairs on the server.
     */
    @Unique
    private int aeupgrade$capacity = InterfaceSlotRange.DEFAULT_CAPACITY;

    /**
     * Slot accepting delivery from an already scheduled crafting job, or -1 outside delivery.
     */
    @Unique
    private int aeupgrade$deliverySlot = -1;

    /**
     * Updates AE2's stocking plan, config mode and neighbor capabilities.
     */
    @Shadow
    private void readConfig() {
        throw new AssertionError();
    }

    /**
     * Applies the slot limit to filter edits and stocking amount increases.
     *
     * @param builder native interface configuration inventory builder
     * @return the existing builder with a server-side slot filter for stock interfaces
     */
    @ModifyExpressionValue(method = "<init>(Lappeng/api/networking/IManagedGridNode;Lappeng/helpers/InterfaceLogicHost;Lnet/minecraft/world/item/Item;I)V",
            at = @At(value = "INVOKE", target = "Lappeng/util/ConfigInventory;configStacks(I)Lappeng/util/ConfigInventory$Builder;"))
    private ConfigInventory.Builder aeupgrade$filterConfig(ConfigInventory.Builder builder) {
        aeupgrade$expanded = InterfaceSlots.supports(host);
        return aeupgrade$expanded ? builder.slotFilter((slot, key) -> aeupgrade$allowsConfig(slot)) : builder;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int aeupgrade$slotCapacity() {
        return aeupgrade$capacity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void aeupgrade$syncClientSlotCapacity(int capacity) {
        aeupgrade$capacity = capacity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void aeupgrade$refreshSlotCapacity() {
        if (aeupgrade$expanded) {
            int capacity = InterfaceSlotService.capacityFor(mainNode.getNode());
            if (aeupgrade$capacity != capacity) {
                aeupgrade$capacity = capacity;
                readConfig();
            }
        }
    }

    /**
     * Rejects new deposits in locked buffer slots, while permitting delivery of previously scheduled crafts.
     *
     * @param slot     zero-based buffer slot
     * @param what     key being deposited
     * @param callback rejects the deposit when the slot is locked
     */
    @Inject(method = "isAllowedInStorageSlot", at = @At("HEAD"), cancellable = true)
    private void aeupgrade$limitBufferInsertion(int slot, AEKey what, CallbackInfoReturnable<Boolean> callback) {
        if (aeupgrade$expanded && (slot < 0 || slot >= aeupgrade$capacity && slot != aeupgrade$deliverySlot)) {
            callback.setReturnValue(false);
        }
    }

    /**
     * Checks whether this side can set configuration in the specified slot.
     *
     * @param slot zero-based inventory slot
     * @return true for unlocked slots and client mirrors receiving sync
     */
    @Unique
    private boolean aeupgrade$allowsConfig(int slot) {
        if (slot < aeupgrade$capacity) {
            return true;
        }
        var level = host.getBlockEntity().getLevel();
        return level != null && level.isClientSide();
    }

    /**
     * Removes stocking work for locked slots without changing their configuration or contents.
     *
     * @param slot     zero-based configuration slot
     * @param callback skips planning work for this slot when locked
     */
    @Inject(method = "updatePlan(I)V", at = @At("HEAD"), cancellable = true)
    private void aeupgrade$pauseLockedStocking(int slot, CallbackInfo callback) {
        if (aeupgrade$expanded && slot >= aeupgrade$capacity) {
            plannedWork[slot] = null;
            callback.cancel();
        }
    }

    /**
     * Preserves native network passthrough when none of the unlocked slots has a stocking filter.
     *
     * @param config   native configuration inventory
     * @param original native emptiness check
     * @return true when no active filter requires local storage mode
     */
    @WrapOperation(method = "readConfig", at = @At(value = "INVOKE",
            target = "Lappeng/util/ConfigInventory;isEmpty()Z"))
    private boolean aeupgrade$isActiveConfigEmpty(ConfigInventory config, Operation<Boolean> original) {
        if (!aeupgrade$expanded || aeupgrade$capacity == config.size()) {
            return original.call(config);
        }
        for (int slot = 0; slot < aeupgrade$capacity; slot++) {
            if (config.getKey(slot) != null) {
                return false;
            }
        }
        return true;
    }

    /**
     * Allows a submitted craft to finish in its original buffer even if the slot is later locked.
     *
     * @param storage  native interface buffer
     * @param slot     zero-based buffer slot assigned to the link
     * @param what     delivered item or fluid key
     * @param amount   quantity available for delivery
     * @param mode     whether to simulate or commit insertion
     * @param original native buffer insertion
     * @return quantity accepted by the buffer
     */
    @WrapOperation(method = "insertCraftedItems", at = @At(value = "INVOKE",
            target = "Lappeng/util/ConfigInventory;insert(ILappeng/api/stacks/AEKey;JLappeng/api/config/Actionable;)J"))
    private long aeupgrade$finishStockingCraft(ConfigInventory storage, int slot, AEKey what, long amount,
                                               Actionable mode, Operation<Long> original) {
        if (aeupgrade$expanded && slot >= aeupgrade$capacity) {
            int previous = aeupgrade$deliverySlot;
            aeupgrade$deliverySlot = slot;
            try {
                return original.call(storage, slot, what, amount, mode);
            } finally {
                aeupgrade$deliverySlot = previous;
            }
        }
        return original.call(storage, slot, what, amount, mode);
    }

    /**
     * Withholds the exposed inventory while this interface is over its grid limit.
     *
     * @param inventory AE2's normally exposed storage
     * @return available storage, or null for a device over its limit
     */
    @ModifyReturnValue(method = "getInventory", at = @At("RETURN"))
    private MEStorage aeupgrade$limitStorageAccess(MEStorage inventory) {
        return DeviceLimitService.isBlocked(mainNode.getNode()) ? null : inventory;
    }
}
