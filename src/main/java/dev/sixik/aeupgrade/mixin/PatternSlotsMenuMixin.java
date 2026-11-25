package dev.sixik.aeupgrade.mixin;

import appeng.api.inventories.InternalInventory;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.menu.AEBaseMenu;
import appeng.menu.implementations.PatternProviderMenu;
import appeng.menu.guisync.GuiSync;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import dev.sixik.aeupgrade.pattern.PatternSlotMenu;
import dev.sixik.aeupgrade.pattern.PatternSlotRange;
import dev.sixik.aeupgrade.pattern.PatternWindowInventory;
import dev.sixik.aeupgrade.pattern.ProviderPatternSlots;
import it.unimi.dsi.fastutil.shorts.ShortSet;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Maps the provider's two rows of pattern slots to a server-owned scroll position.
 */
@Mixin(value = PatternProviderMenu.class, remap = false)
public abstract class PatternSlotsMenuMixin extends AEBaseMenu implements PatternSlotMenu {
    /**
     * AE2 menu action used to request a logical scroll offset.
     */
    @Unique
    private static final String AEUPGRADE_SCROLL_ACTION = "aeupgrade.pattern_scroll";

    /**
     * Provider represented by this menu.
     */
    @Shadow
    @Final
    protected PatternProviderLogic logic;

    /**
     * Current view of the logical pattern inventory, or null for other providers.
     */
    @Unique
    private PatternWindowInventory aeupgrade$window;

    /**
     * Unlocked slots on the server, synchronized to this client menu.
     */
    @GuiSync(40)
    public int aeupgrade$unlocked = PatternSlotRange.ROW_SIZE;

    /**
     * Visible range including stored items in locked slots.
     */
    @GuiSync(41)
    public int aeupgrade$total = PatternSlotRange.ROW_SIZE;

    /**
     * Zero-based index of the first displayed pattern.
     */
    @GuiSync(42)
    public int aeupgrade$first;

    /**
     * Scroll requests acknowledged by the server after sending the new item stacks.
     */
    @GuiSync(43)
    public int aeupgrade$scrollRevision;

    /**
     * Client revision expected when a pending scroll has been processed.
     */
    @Unique
    private int aeupgrade$expectedRevision;

    /**
     * Latest offset selected by the client, including while a request is in flight.
     */
    @Unique
    private int aeupgrade$scrollTarget;

    /**
     * Declares the AE2 base constructor for inherited menu methods.
     *
     * @param type            menu type
     * @param id              container ID
     * @param playerInventory opening player's inventory
     * @param host            AE2 menu host
     */
    protected PatternSlotsMenuMixin(MenuType<?> type, int id, Inventory playerInventory, Object host) {
        super(type, id, playerInventory, host);
    }

    /**
     * Gives AE2's existing pattern slots a positionable backing view.
     *
     * @param inventory       ordinary inventory supplied by AE2
     * @param type            menu type
     * @param id              container ID
     * @param playerInventory opening player's inventory
     * @param host            provider block or panel
     * @return original inventory or two-row view for a standard provider
     */
    @ModifyExpressionValue(method = "<init>", at = @At(value = "INVOKE",
            target = "Lappeng/helpers/patternprovider/PatternProviderLogic;getPatternInv()Lappeng/api/inventories/InternalInventory;"))
    private InternalInventory aeupgrade$wrapInventory(InternalInventory inventory,
                                                      MenuType<?> type, int id, Inventory playerInventory, PatternProviderLogicHost host) {
        if (inventory.size() != PatternSlotRange.ROW_SIZE || !ProviderPatternSlots.supports(host)) {
            return inventory;
        }
        aeupgrade$window = new PatternWindowInventory(inventory, isServerSide() ? logic : null);
        return aeupgrade$window;
    }

    /**
     * Registers the scroll action after all native menu slots have been created.
     *
     * @param callback Mixin callback
     */
    @Inject(method = "<init>", at = @At("TAIL"))
    private void aeupgrade$registerScroll(CallbackInfo callback) {
        if (aeupgrade$window != null) {
            registerClientAction(AEUPGRADE_SCROLL_ACTION, Integer.class, this::aeupgrade$selectOffset);
            if (isServerSide()) {
                aeupgrade$refreshBounds();
            }
        }
    }

    /**
     * Publishes capacity changes before the menu synchronizes to a viewer.
     *
     * @param callback Mixin callback
     */
    @Inject(method = "broadcastChanges", at = @At("HEAD"))
    private void aeupgrade$updateBounds(CallbackInfo callback) {
        if (aeupgrade$window != null && isServerSide()) {
            aeupgrade$refreshBounds();
        }
    }

    /**
     * Reads the unlocked and browsable ranges from the live provider.
     */
    @Unique
    private void aeupgrade$refreshBounds() {
        var provider = (ProviderPatternSlots) logic;
        aeupgrade$unlocked = provider.aeupgrade$patternCapacity();
        aeupgrade$total = Math.max(aeupgrade$unlocked, provider.aeupgrade$extraPatterns().storedRange());
    }

    /**
     * Switches the server viewport and sends its items before acknowledging the new index.
     *
     * @param requested requested zero-based offset
     */
    @Unique
    private void aeupgrade$selectOffset(Integer requested) {
        if (requested == null || !isServerSide() || !isValidMenu()) {
            return;
        }
        aeupgrade$refreshBounds();
        aeupgrade$first = PatternSlotRange.clampOffset(requested, aeupgrade$total);
        aeupgrade$window.select(aeupgrade$first, aeupgrade$unlocked);
        aeupgrade$scrollRevision++;
        sendAllDataToRemote();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onServerDataSync(ShortSet updatedFields) {
        super.onServerDataSync(updatedFields);
        if (aeupgrade$window != null) {
            aeupgrade$window.select(aeupgrade$first, aeupgrade$unlocked);
            aeupgrade$sendScrollTarget();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean aeupgrade$hasPatternWindow() {
        return aeupgrade$window != null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int aeupgrade$capacity() {
        return aeupgrade$unlocked;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int aeupgrade$browsable() {
        return aeupgrade$total;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int aeupgrade$offset() {
        return aeupgrade$first;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int aeupgrade$scrollTarget() {
        return aeupgrade$scrollTarget;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean aeupgrade$waitingForScroll() {
        return aeupgrade$expectedRevision != aeupgrade$scrollRevision;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void aeupgrade$requestScroll(long offset) {
        if (aeupgrade$window != null && isClientSide()) {
            aeupgrade$scrollTarget = PatternSlotRange.clampOffset(offset, aeupgrade$total);
            aeupgrade$sendScrollTarget();
        }
    }

    /**
     * Sends the latest selection once the previous viewport and its items have arrived.
     */
    @Unique
    private void aeupgrade$sendScrollTarget() {
        if (aeupgrade$waitingForScroll() || aeupgrade$scrollTarget == aeupgrade$first) {
            return;
        }
        aeupgrade$scrollTarget = PatternSlotRange.clampOffset(aeupgrade$scrollTarget, aeupgrade$total);
        if (aeupgrade$scrollTarget != aeupgrade$first) {
            aeupgrade$expectedRevision = aeupgrade$scrollRevision + 1;
            sendClientAction(AEUPGRADE_SCROLL_ACTION, aeupgrade$scrollTarget);
        }
    }
}
