package dev.sixik.aeupgrade.mixin;

import appeng.helpers.InterfaceLogicHost;
import appeng.menu.guisync.GuiSync;
import appeng.menu.implementations.InterfaceMenu;
import appeng.menu.implementations.UpgradeableMenu;
import dev.sixik.aeupgrade.stock.InterfaceSlotMenu;
import dev.sixik.aeupgrade.stock.InterfaceSlotRange;
import dev.sixik.aeupgrade.stock.InterfaceSlots;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Publishes the active and browsable ranges of stock interface slots.
 */
@Mixin(value = InterfaceMenu.class, remap = false)
public abstract class InterfaceSlotMenuMixin extends UpgradeableMenu<InterfaceLogicHost> implements InterfaceSlotMenu {
    /**
     * Unlocked configuration and storage pairs, synchronized from the server.
     */
    @GuiSync(40)
    public int aeupgrade$unlocked = InterfaceSlotRange.DEFAULT_CAPACITY;

    /**
     * Number of browsable pairs including locked configuration and storage.
     */
    @GuiSync(41)
    public int aeupgrade$total = InterfaceSlotRange.DEFAULT_CAPACITY;

    /**
     * Declares the native upgradeable menu constructor.
     *
     * @param type      active menu type
     * @param id        container ID
     * @param inventory opening player's inventory
     * @param host      interface block or panel
     */
    protected InterfaceSlotMenuMixin(MenuType<?> type, int id, Inventory inventory, InterfaceLogicHost host) {
        super(type, id, inventory, host);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void broadcastChanges() {
        if (isServerSide() && aeupgrade$hasStockSlots()) {
            var host = getHost();
            aeupgrade$unlocked = ((InterfaceSlots) host.getInterfaceLogic()).aeupgrade$slotCapacity();
            aeupgrade$total = Math.max(InterfaceSlotRange.DEFAULT_CAPACITY, aeupgrade$unlocked);
            var config = host.getConfig();
            var storage = host.getStorage();
            for (int slot = config.size() - 1; slot >= aeupgrade$total; slot--) {
                if (config.getStack(slot) != null || storage.getStack(slot) != null) {
                    aeupgrade$total = slot + 1;
                    break;
                }
            }
        }
        super.broadcastChanges();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onServerDataSync(ShortSet updatedFields) {
        super.onServerDataSync(updatedFields);
        if (aeupgrade$hasStockSlots()) {
            ((InterfaceSlots) getHost().getInterfaceLogic()).aeupgrade$syncClientSlotCapacity(aeupgrade$unlocked);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean aeupgrade$hasStockSlots() {
        return InterfaceSlots.supports(getHost());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int aeupgrade$stockCapacity() {
        return aeupgrade$unlocked;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int aeupgrade$stockRange() {
        return aeupgrade$total;
    }

    /**
     * Opens the native amount editor only for an unlocked configuration index.
     *
     * @param slot     requested logical configuration slot
     * @param callback cancels unavailable slots
     */
    @Inject(method = "openSetAmountMenu", at = @At("HEAD"), cancellable = true)
    private void aeupgrade$guardAmountMenu(int slot, CallbackInfo callback) {
        if (aeupgrade$hasStockSlots() && isServerSide()
                && (slot < 0 || slot >= ((InterfaceSlots) getHost().getInterfaceLogic()).aeupgrade$slotCapacity())) {
            callback.cancel();
        }
    }
}
