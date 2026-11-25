package dev.sixik.aeupgrade.pattern;

import java.util.NavigableMap;
import java.util.TreeMap;
import java.util.stream.IntStream;

/**
 * Lays out occupied patterns and the next available space within AE2's bounded terminal packets.
 */
final class PatternTerminalLayout {
    /**
     * Twelve complete terminal rows, below the native protocol limit of 128 entries per container.
     */
    static final int SECTION_SIZE = 12 * PatternSlotRange.ROW_SIZE;

    /**
     * Keeps the layout calculation static.
     */
    private PatternTerminalLayout() {
    }

    /**
     * Encloses every stored pattern and makes free space available without allocating large empty ranges.
     *
     * @param capacity number of unlocked logical slots
     * @param occupied distinct occupied indices in ascending order
     * @return visible section lengths keyed by the first logical slot in each section
     */
    static NavigableMap<Integer, Integer> sections(int capacity, IntStream occupied) {
        var sections = new TreeMap<Integer, Integer>();
        int firstEmpty = 0;
        var slots = occupied.iterator();
        while (slots.hasNext()) {
            int slot = slots.nextInt();
            if (slot == firstEmpty) {
                firstEmpty++;
            }
            int start = sectionStart(slot);
            sections.put(start, slot - start + 1);
        }
        if (firstEmpty < capacity) {
            sections.putIfAbsent(sectionStart(firstEmpty), 0);
        }
        sections.replaceAll((start, occupiedLength) ->
                Math.max(occupiedLength, Math.min(SECTION_SIZE, capacity - start)));
        return sections;
    }

    /**
     * Finds the boundary of the section containing a logical slot.
     *
     * @param slot zero-based logical slot
     * @return first index of that slot's section
     */
    private static int sectionStart(int slot) {
        return slot - slot % SECTION_SIZE;
    }
}
