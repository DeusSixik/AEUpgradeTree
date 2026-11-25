package dev.sixik.aeupgrade.mixin;

import java.util.Collection;
import java.util.Iterator;
import java.util.List;

import appeng.api.networking.IManagedGridNode;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.util.inv.AppEngInternalInventory;
import com.google.common.collect.Iterators;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sixik.aeupgrade.pattern.ExtraPatternInventory;
import dev.sixik.aeupgrade.pattern.PatternSlotRange;
import dev.sixik.aeupgrade.pattern.PatternSlotService;
import dev.sixik.aeupgrade.pattern.PatternTerminalSections;
import dev.sixik.aeupgrade.pattern.ProviderPatternSlots;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Feeds extra slots into AE2's normal crafting, persistence and drop paths.
 */
@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class PatternSlotsLogicMixin implements ProviderPatternSlots {
    /**
     * Provider host backing the logic on this side.
     */
    @Shadow
    @Final
    private PatternProviderLogicHost host;

    /**
     * Managed node used to find the current grid's capacity.
     */
    @Shadow
    @Final
    private IManagedGridNode mainNode;

    /**
     * Extra inventory, allocated only for a standard provider that opens the menu or has saved slots.
     */
    @Unique
    private ExtraPatternInventory aeupgrade$extra;

    /**
     * Logical terminal sections, created when a terminal first encounters this provider.
     */
    @Unique
    private PatternTerminalSections aeupgrade$terminalSections;

    /**
     * Capacity that was used to publish the current set of patterns.
     */
    @Unique
    private int aeupgrade$registeredCapacity = PatternSlotRange.ROW_SIZE;

    /**
     * Rebuilds both the available recipes and the set of ingredients checked by blocking mode.
     */
    @Shadow
    public abstract void updatePatterns();

    /**
     * {@inheritDoc}
     */
    @Override
    public ExtraPatternInventory aeupgrade$extraPatterns() {
        if (aeupgrade$extra == null) {
            aeupgrade$extra = new ExtraPatternInventory();
        }
        return aeupgrade$extra;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int aeupgrade$patternCapacity() {
        return PatternSlotRange.ROW_SIZE + PatternSlotService.bonusFor(mainNode.getNode());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Collection<? extends PatternContainer> aeupgrade$terminalContainers() {
        if (aeupgrade$terminalSections == null) {
            aeupgrade$terminalSections = new PatternTerminalSections(host);
        }
        return aeupgrade$terminalSections.get(aeupgrade$patternCapacity(), aeupgrade$extraPatterns());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void aeupgrade$refreshPatternCapacity() {
        int current = aeupgrade$patternCapacity();
        if (Math.min(PatternSlotRange.ROW_SIZE, current) != Math.min(PatternSlotRange.ROW_SIZE, aeupgrade$registeredCapacity)
                || aeupgrade$extra != null && aeupgrade$extra.affectsPatterns(aeupgrade$registeredCapacity, current)) {
            updatePatterns();
        }
        aeupgrade$registeredCapacity = current;
    }

    /**
     * Supplies unlocked native and extra patterns to AE2's standard pattern registration.
     *
     * @param inventory native AE2 provider inventory
     * @param original  original iterator factory
     * @return occupied and unlocked patterns in slot order
     */
    @WrapOperation(method = "updatePatterns", at = @At(value = "INVOKE",
            target = "Lappeng/util/inv/AppEngInternalInventory;iterator()Ljava/util/Iterator;"))
    private Iterator<ItemStack> aeupgrade$includeExtraSlots(AppEngInternalInventory inventory,
                                                            Operation<Iterator<ItemStack>> original) {
        if (!ProviderPatternSlots.supports(host)) {
            return original.call(inventory);
        }
        aeupgrade$registeredCapacity = aeupgrade$patternCapacity();
        if (aeupgrade$terminalSections != null) {
            aeupgrade$terminalSections.invalidate();
        }
        if (aeupgrade$registeredCapacity < inventory.size()) {
            return inventory.getSubInventory(0, aeupgrade$registeredCapacity).iterator();
        }
        var iterator = original.call(inventory);
        return aeupgrade$extra == null ? iterator
                : Iterators.concat(iterator, aeupgrade$extra.unlocked(aeupgrade$registeredCapacity));
    }

    /**
     * Restricts memory-card restoration to the unlocked native slots.
     *
     * @param inventory AE2's native inventory
     * @param stack     pattern being restored
     * @param original  native insertion
     * @return overflow if there is no enabled native slot that accepts the pattern
     */
    @WrapOperation(method = "importSettings", at = @At(value = "INVOKE",
            target = "Lappeng/util/inv/AppEngInternalInventory;addItems(Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/item/ItemStack;"))
    private ItemStack aeupgrade$restoreIntoUnlocked(AppEngInternalInventory inventory, ItemStack stack,
                                                    Operation<ItemStack> original) {
        if (ProviderPatternSlots.supports(host)) {
            int capacity = aeupgrade$patternCapacity();
            if (capacity < inventory.size()) {
                return inventory.getSubInventory(0, capacity).addItems(stack);
            }
        }
        return original.call(inventory, stack);
    }

    /**
     * Saves extra patterns alongside the provider's native inventory.
     *
     * @param tag        provider save data
     * @param registries registry access for item serialization
     * @param callback   Mixin callback
     */
    @Inject(method = "writeToNBT", at = @At("TAIL"))
    private void aeupgrade$saveExtra(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo callback) {
        if (aeupgrade$extra != null) {
            aeupgrade$extra.save(tag, registries);
        }
    }

    /**
     * Loads extra patterns before AE2 readies the node and publishes its recipes.
     *
     * @param tag        provider save data
     * @param registries registry access for item deserialization
     * @param callback   Mixin callback
     */
    @Inject(method = "readFromNBT", at = @At("TAIL"))
    private void aeupgrade$loadExtra(CompoundTag tag, HolderLookup.Provider registries, CallbackInfo callback) {
        if (ProviderPatternSlots.supports(host) && (aeupgrade$extra != null || ExtraPatternInventory.hasSavedSlots(tag))) {
            aeupgrade$extraPatterns().load(tag, registries);
        }
    }

    /**
     * Adds all extra patterns to the drops, including any currently locked slots.
     *
     * @param drops    AE2's drop list
     * @param callback Mixin callback
     */
    @Inject(method = "addDrops", at = @At("TAIL"))
    private void aeupgrade$dropExtra(List<ItemStack> drops, CallbackInfo callback) {
        if (aeupgrade$extra != null) {
            drops.addAll(aeupgrade$extra.contents());
        }
    }

    /**
     * Clears the extra inventory when AE2 empties a removed provider.
     *
     * @param callback Mixin callback
     */
    @Inject(method = "clearContent", at = @At("TAIL"))
    private void aeupgrade$clearExtra(CallbackInfo callback) {
        if (aeupgrade$extra != null) {
            aeupgrade$extra.clear();
        }
    }
}
