package dev.sixik.aeupgrade.pattern;

import java.util.Collection;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.stream.IntStream;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;

/**
 * Stores occupied extra slots by logical index. Empty or currently unavailable slots need no backing entries.
 */
public final class ExtraPatternInventory {
    /**
     * Provider save tag containing the occupied extra slots.
     */
    private static final String PATTERNS_TAG = "aeupgrade:extra_patterns";

    /**
     * Logical slot index within one saved pattern entry.
     */
    private static final String SLOT_TAG = "Slot";

    /**
     * Occupied slots, ordered for stable pattern registration and access to the highest index.
     */
    private final TreeMap<Integer, ItemStack> occupied = new TreeMap<>();

    /**
     * Checks whether a saved provider has extra inventory data.
     *
     * @param tag provider save data
     * @return true when the indexed extra-slot list is present
     */
    public static boolean hasSavedSlots(CompoundTag tag) {
        return tag.contains(PATTERNS_TAG, Tag.TAG_LIST);
    }

    /**
     * Reads an extra slot.
     *
     * @param index logical extra slot index starting at nine
     * @return stack at this index or an empty stack
     */
    public ItemStack get(int index) {
        return occupied.getOrDefault(index, ItemStack.EMPTY);
    }

    /**
     * Sets an extra slot, dropping its entry when emptied.
     *
     * @param index logical extra slot index starting at nine
     * @param stack new content of the slot
     */
    public void set(int index, ItemStack stack) {
        if (stack.isEmpty()) {
            occupied.remove(index);
        } else {
            occupied.put(index, stack);
        }
    }

    /**
     * Finds the minimum scrollable range needed to reach all stored items.
     *
     * @return exclusive upper bound, or the native row size when empty
     */
    public int storedRange() {
        return occupied.isEmpty() ? PatternSlotRange.ROW_SIZE : occupied.lastKey() + 1;
    }

    /**
     * @return occupied extra indices in ascending logical slot order
     */
    public IntStream occupiedIndices() {
        return occupied.navigableKeySet().stream().mapToInt(Integer::intValue);
    }

    /**
     * Checks whether a capacity change adds or removes any occupied slot from autocrafting.
     *
     * @param before previous exclusive capacity
     * @param after  new exclusive capacity
     * @return true when there is an occupied slot between the two capacities
     */
    public boolean affectsPatterns(int before, int after) {
        if (before == after || occupied.isEmpty()) {
            return false;
        }
        var first = occupied.ceilingKey(Math.min(before, after));
        return first != null && first < Math.max(before, after);
    }

    /**
     * Iterates occupied slots that are available for crafting.
     *
     * @param capacity exclusive end of unlocked slots
     * @return available pattern stacks ordered by logical index
     */
    public Iterator<ItemStack> unlocked(int capacity) {
        return occupied.headMap(capacity, false).values().iterator();
    }

    /**
     * Reads all stored stacks, including patterns temporarily unavailable for crafting.
     *
     * @return occupied stacks
     */
    public Collection<ItemStack> contents() {
        return occupied.values();
    }

    /**
     * Empties the extra slots when AE2 clears the provider during removal.
     */
    public void clear() {
        occupied.clear();
    }

    /**
     * Writes occupied slots as indexed item stacks.
     *
     * @param tag        provider save data
     * @param registries registries for encoding item stacks
     */
    public void save(CompoundTag tag, HolderLookup.Provider registries) {
        if (occupied.isEmpty()) {
            tag.remove(PATTERNS_TAG);
            return;
        }
        var items = new ListTag();
        occupied.forEach((index, stack) -> {
            var item = new CompoundTag();
            item.putInt(SLOT_TAG, index);
            items.add(stack.save(registries, item));
        });
        tag.put(PATTERNS_TAG, items);
    }

    /**
     * Restores occupied slots independently of the grid's current upgrade.
     *
     * @param tag        provider save data
     * @param registries registries for decoding item stacks
     */
    public void load(CompoundTag tag, HolderLookup.Provider registries) {
        occupied.clear();
        var items = tag.getList(PATTERNS_TAG, Tag.TAG_COMPOUND);
        for (int i = 0; i < items.size(); i++) {
            var item = items.getCompound(i);
            int slot = item.getInt(SLOT_TAG);
            if (slot >= PatternSlotRange.ROW_SIZE && slot < Integer.MAX_VALUE) {
                set(slot, ItemStack.parseOptional(registries, item));
            }
        }
    }
}
