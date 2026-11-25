package dev.sixik.aeupgrade.mixin;

import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import appeng.api.networking.IGrid;
import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.helpers.InventoryAction;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.implementations.PatternAccessTermMenu;
import appeng.parts.crafting.PatternProviderPart;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sixik.aeupgrade.pattern.PatternTerminalSection;
import dev.sixik.aeupgrade.pattern.ProviderPatternSlots;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Shows sections of expanded stock providers through AE2's own terminal menus and packets.
 */
@Mixin(value = PatternAccessTermMenu.class, remap = false)
public abstract class PatternTerminalMenuMixin extends AEBaseMenu {
    /**
     * Native map from terminal container IDs to their server inventories.
     */
    @Shadow
    @Final
    private Long2ObjectOpenHashMap<?> byId;

    /**
     * Native tracker map used to discard obsolete pinned section identities.
     */
    @Shadow
    @Final
    private Map<PatternContainer, ?> diList;

    /**
     * Containers kept visible after becoming full.
     */
    @Shadow
    @Final
    private Set<PatternContainer> pinnedHosts;

    /**
     * @return the grid the open terminal currently has access to, or null when offline
     */
    @Shadow
    private IGrid getGrid() {
        throw new AssertionError();
    }

    /**
     * Declares the base constructor needed by the mixin.
     *
     * @param type            AE2 menu type
     * @param id              current menu id
     * @param playerInventory opening player's inventory
     * @param host            AE2 terminal host
     */
    protected PatternTerminalMenuMixin(MenuType<?> type, int id, Inventory playerInventory, Object host) {
        super(type, id, playerInventory, host);
    }

    /**
     * Uses the same section identities during AE2's inventory discovery and synchronization.
     *
     * @param grid         terminal's live grid
     * @param machineClass class of providers being visited
     * @param original     original AE2 query
     * @return native hosts or size-stable sections for stock Pattern Providers
     */
    @WrapOperation(method = {"visitPatternProviderHosts", "sendFullUpdate"}, at = @At(value = "INVOKE",
            target = "Lappeng/api/networking/IGrid;getActiveMachines(Ljava/lang/Class;)Ljava/util/Set;"))
    private Set<?> aeupgrade$terminalSections(IGrid grid, Class<?> machineClass, Operation<Set<?>> original) {
        var active = original.call(grid, machineClass);
        if (active.isEmpty() || machineClass != PatternProviderBlockEntity.class && machineClass != PatternProviderPart.class) {
            return active;
        }
        var expanded = new LinkedHashSet<PatternContainer>();
        for (var machine : active) {
            var logic = ((PatternProviderLogicHost) machine).getLogic();
            expanded.addAll(((ProviderPatternSlots) logic).aeupgrade$terminalContainers());
        }
        return expanded;
    }

    /**
     * Keeps locked patterns reachable when filtering providers to available space.
     *
     * @param container entry in the terminal
     * @param callback  replaces the fullness check for an upgraded section
     */
    @Inject(method = "isFull", at = @At("HEAD"), cancellable = true)
    private void aeupgrade$sectionIsFull(PatternContainer container, CallbackInfoReturnable<Boolean> callback) {
        if (container instanceof PatternTerminalSection section) {
            callback.setReturnValue(section.isFull());
        }
    }

    /**
     * Drops stale sections from the filter's pinned set after a resize or release of stored slots.
     *
     * @param grid     current terminal grid
     * @param callback Mixin callback
     */
    @Inject(method = "sendFullUpdate", at = @At("TAIL"))
    private void aeupgrade$forgetObsoleteSections(IGrid grid, CallbackInfo callback) {
        pinnedHosts.removeIf(container -> container instanceof PatternTerminalSection && !diList.containsKey(container));
    }

    /**
     * Skips targets that moved off this terminal's network before it could refresh its view.
     *
     * @param inventories tracked inventories
     * @param id          requested terminal container ID
     * @param original    AE2's lookup
     * @return the tracked entry, or null for a stale extended inventory
     */
    @WrapOperation(method = "quickMovePattern", at = @At(value = "INVOKE",
            target = "Lit/unimi/dsi/fastutil/longs/Long2ObjectOpenHashMap;get(J)Ljava/lang/Object;"))
    private Object aeupgrade$validateQuickMoveTarget(Long2ObjectOpenHashMap<?> inventories, long id,
                                                     Operation<Object> original) {
        var entry = original.call(inventories, id);
        if (entry instanceof PatternTerminalTrackerAccessor tracker
                && tracker.aeupgrade$getContainer() instanceof PatternTerminalSection section) {
            var grid = getGrid();
            if (grid == null || grid != section.getGrid()) {
                return null;
            }
        }
        return entry;
    }

    /**
     * Rejects insertion and swaps into locked slots before the native handler clears the target stack.
     *
     * @param player   player using the terminal
     * @param action   inventory action requested by the terminal
     * @param slot     section-relative slot index
     * @param id       tracked container ID
     * @param callback cancels incompatible or fully handled actions
     */
    @Inject(method = "doAction", at = @At("HEAD"), cancellable = true)
    private void aeupgrade$guardLockedSlots(ServerPlayer player, InventoryAction action, int slot, long id,
                                            CallbackInfo callback) {
        if (!(byId.get(id) instanceof PatternTerminalTrackerAccessor tracker)
                || !(tracker.aeupgrade$getContainer() instanceof PatternTerminalSection section)) {
            return;
        }
        var grid = getGrid();
        var inventory = section.inventory();
        if (grid == null || section.getGrid() != grid || slot < 0 || slot >= inventory.size()
                || !inventory.isUnlocked(slot) && !getCarried().isEmpty()
                && (action == InventoryAction.PICKUP_OR_SET_DOWN || action == InventoryAction.SPLIT_OR_PLACE_SINGLE)) {
            callback.cancel();
            return;
        }
        if (action == InventoryAction.MOVE_REGION) {
            // Each pattern must be cleared from its own slot, including when a section starts beyond slot nine.
            for (int i = 0; i < inventory.size(); i++) {
                var stack = inventory.getStackInSlot(i).copy();
                if (!stack.isEmpty()) {
                    player.getInventory().add(stack);
                    inventory.setItemDirect(i, stack);
                }
            }
            callback.cancel();
        }
    }
}
