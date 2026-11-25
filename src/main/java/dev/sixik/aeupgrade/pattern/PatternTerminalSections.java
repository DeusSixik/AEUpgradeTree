package dev.sixik.aeupgrade.pattern;

import java.util.Collection;
import java.util.TreeMap;
import java.util.stream.IntStream;

import appeng.helpers.patternprovider.PatternContainer;
import appeng.helpers.patternprovider.PatternProviderLogicHost;

/**
 * Caches the section identities used by AE2's open Pattern Access Terminals.
 */
public final class PatternTerminalSections {
    /**
     * Original host used for access, grouping and links.
     */
    private final PatternProviderLogicHost host;

    /**
     * Current section instances keyed by their first slot.
     */
    private final TreeMap<Integer, PatternTerminalSection> sections = new TreeMap<>();

    /**
     * Capacity used to build the last layout.
     */
    private int lastCapacity = -1;

    /**
     * True when an inventory change may have changed which sections are needed.
     */
    private boolean dirty = true;

    /**
     * Creates a cached layout for a standard provider.
     *
     * @param host provider block or panel
     */
    public PatternTerminalSections(PatternProviderLogicHost host) {
        this.host = host;
    }

    /**
     * Marks the layout for rebuilding the next time a terminal visits the provider.
     */
    public void invalidate() {
        dirty = true;
    }

    /**
     * Reads the current ordered sections, retaining the same IDs when their layout is unchanged.
     *
     * @param capacity number of unlocked slots for this provider
     * @param extra    sparse extra-slot storage
     * @return containers to synchronize to the terminal
     */
    public Collection<? extends PatternContainer> get(int capacity, ExtraPatternInventory extra) {
        if (dirty || lastCapacity != capacity) {
            var logic = host.getLogic();
            var base = logic.getPatternInv();
            var occupied = IntStream.concat(
                    IntStream.range(0, base.size()).filter(slot -> !base.getStackInSlot(slot).isEmpty()),
                    extra.occupiedIndices());
            var layout = PatternTerminalLayout.sections(capacity, occupied);
            sections.keySet().retainAll(layout.keySet());
            layout.forEach((start, size) -> {
                var previous = sections.get(start);
                if (previous == null || previous.inventory().size() != size) {
                    sections.put(start, new PatternTerminalSection(host, new PatternWindowInventory(base, logic, start, size)));
                }
            });
            lastCapacity = capacity;
            dirty = false;
        }
        return sections.values();
    }
}
