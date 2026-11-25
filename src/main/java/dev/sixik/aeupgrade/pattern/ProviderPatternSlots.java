package dev.sixik.aeupgrade.pattern;

import java.util.Collection;

import appeng.blockentity.crafting.PatternProviderBlockEntity;
import appeng.helpers.patternprovider.PatternContainer;
import appeng.parts.crafting.PatternProviderPart;

/**
 * Extra pattern slots owned by AE2's standard provider logic.
 */
public interface ProviderPatternSlots {
    /**
     * Checks whether an AE2 host is a standard provider block or panel.
     *
     * @param host owner of the provider logic or node
     * @return true for the two stock providers with a nine-slot layout
     */
    static boolean supports(Object host) {
        return host.getClass() == PatternProviderBlockEntity.class || host.getClass() == PatternProviderPart.class;
    }

    /**
     * @return stored patterns in extra slots, including any temporarily locked slots
     */
    ExtraPatternInventory aeupgrade$extraPatterns();

    /**
     * @return total number of unlocked pattern slots
     */
    int aeupgrade$patternCapacity();

    /**
     * @return stable terminal views of the native and extended pattern slots
     */
    Collection<? extends PatternContainer> aeupgrade$terminalContainers();

    /**
     * Refreshes AE2's advertised recipes when a grid change affects occupied extra slots.
     */
    void aeupgrade$refreshPatternCapacity();
}
