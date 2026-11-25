package dev.sixik.aeupgrade.pattern;

import appeng.api.implementations.blockentities.PatternContainerGroup;
import appeng.api.networking.IGrid;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import org.jetbrains.annotations.Nullable;

/**
 * A size-stable section of one provider, transmitted as a native terminal container.
 *
 * @param provider  original provider block or panel
 * @param inventory bounded view of the logical slots in this section
 */
public record PatternTerminalSection(PatternProviderLogicHost provider, PatternWindowInventory inventory)
        implements PatternContainer {
    /**
     * {@inheritDoc}
     */
    @Override
    public @Nullable IGrid getGrid() {
        return provider.getGrid();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isVisibleInTerminal() {
        return provider.isVisibleInTerminal();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PatternWindowInventory getTerminalPatternInventory() {
        return inventory;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public long getTerminalSortOrder() {
        return provider.getTerminalSortOrder();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public PatternContainerGroup getTerminalGroup() {
        return provider.getTerminalGroup();
    }

    /**
     * Keeps the section visible under the available-space filter whenever a locked pattern can be retrieved.
     *
     * @return true if every unlocked slot is occupied and no locked slot contains a pattern
     */
    public boolean isFull() {
        for (int i = 0; i < inventory.size(); i++) {
            boolean empty = inventory.getStackInSlot(i).isEmpty();
            if (inventory.isUnlocked(i) ? empty : !empty) {
                return false;
            }
        }
        return true;
    }
}
