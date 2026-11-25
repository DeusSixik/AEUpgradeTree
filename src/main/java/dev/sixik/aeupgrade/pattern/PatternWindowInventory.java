package dev.sixik.aeupgrade.pattern;

import appeng.api.inventories.BaseInternalInventory;
import appeng.api.inventories.InternalInventory;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.util.inv.AppEngInternalInventory;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * A constant-size view over the logical pattern slots, with an isolated client mirror for vanilla slot packets.
 */
public final class PatternWindowInventory extends BaseInternalInventory {
    /**
     * Native inventory on the server, or the two client rows populated by menu packets.
     */
    private final InternalInventory row;

    /**
     * Provider whose extra slots the server viewport addresses, or null for the client mirror.
     */
    private final @Nullable PatternProviderLogic provider;

    /**
     * Number of slots exposed by this view.
     */
    private final int size;

    /**
     * Zero-based logical index of the first visible slot.
     */
    private int offset;

    /**
     * Exclusive limit for insertion on the client, updated from the authoritative menu.
     */
    private int capacity = PatternSlotRange.ROW_SIZE;

    /**
     * Creates a server view or a client mirror.
     *
     * @param nativeInventory ordinary pattern inventory on the host
     * @param provider        server logic, or null when building a client mirror
     */
    public PatternWindowInventory(InternalInventory nativeInventory, @Nullable PatternProviderLogic provider) {
        this(nativeInventory, provider, 0, PatternSlotRange.WINDOW_SIZE);
    }

    /**
     * Creates a fixed section of the server inventory for Pattern Access Terminal.
     *
     * @param nativeInventory provider's native slots
     * @param provider        provider logic
     * @param offset          zero-based first logical slot in this view
     * @param size            number of visible slots
     */
    PatternWindowInventory(InternalInventory nativeInventory, @Nullable PatternProviderLogic provider, int offset, int size) {
        this.row = provider == null ? new AppEngInternalInventory(size) : nativeInventory;
        this.provider = provider;
        this.offset = offset;
        this.size = size;
    }

    /**
     * Updates the logical indices and insertion range for the visible window.
     *
     * @param offset   zero-based index of the first visible slot
     * @param capacity number of unlocked slots
     */
    public void select(int offset, int capacity) {
        this.offset = offset;
        this.capacity = capacity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int size() {
        return size;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int getSlotLimit(int slot) {
        return 1;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ItemStack getStackInSlot(int slot) {
        if (provider == null) {
            return row.getStackInSlot(slot);
        }
        if (slot >= Integer.MAX_VALUE - offset) {
            return ItemStack.EMPTY;
        }
        int index = offset + slot;
        return index < row.size() ? row.getStackInSlot(index)
                : ((ProviderPatternSlots) provider).aeupgrade$extraPatterns().get(index);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isItemValid(int slot, ItemStack stack) {
        if (!isUnlocked(slot)) {
            return false;
        }
        int index = offset + slot;
        return provider == null || index >= row.size() || row.isItemValid(index, stack);
    }

    /**
     * Checks whether the slot currently accepts patterns.
     *
     * @param slot index within this view
     * @return true if the logical slot is below the live capacity
     */
    public boolean isUnlocked(int slot) {
        int unlocked = provider == null ? capacity : ((ProviderPatternSlots) provider).aeupgrade$patternCapacity();
        return slot < unlocked - offset;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void setItemDirect(int slot, ItemStack stack) {
        if (provider == null) {
            row.setItemDirect(slot, stack);
            return;
        }
        var previous = getStackInSlot(slot);
        if (ItemStack.matches(previous, stack)) {
            return;
        }
        if (!stack.isEmpty() && !isUnlocked(slot)
                && !(stack.getCount() < previous.getCount() && ItemStack.isSameItemSameComponents(stack, previous))) {
            return;
        }
        int index = offset + slot;
        if (index < row.size()) {
            row.setItemDirect(index, stack);
            return;
        }
        ((ProviderPatternSlots) provider).aeupgrade$extraPatterns().set(index, stack);
        provider.saveChanges();
        provider.updatePatterns();
    }
}
