package dev.sixik.aeupgrade.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/** Verifies bounded terminal sections for small inventories, very large capacities and locked contents. */
class PatternTerminalLayoutTest {
    /** A small upgrade is fully visible even with an empty provider. */
    @Test
    void sections_smallEmptyCapacity_exposesEverySlot() {
        assertEquals(Map.of(0, 10), PatternTerminalLayout.sections(10, IntStream.empty()));
    }

    /** A zero-slot provider exposes only the space needed to recover its stored patterns. */
    @Test
    void sections_zeroCapacity_keepsStoredPatternsAccessible() {
        assertEquals(Map.of(), PatternTerminalLayout.sections(0, IntStream.empty()));
        assertEquals(Map.of(0, 2, 216, 12), PatternTerminalLayout.sections(0, IntStream.of(1, 227)));
    }

    /** A full section unlocks the next part of the terminal without resizing the previous section. */
    @Test
    void sections_firstSectionFull_addsSpaceInNextSection() {
        int page = PatternTerminalLayout.SECTION_SIZE;
        assertEquals(Map.of(0, page, page, 1), PatternTerminalLayout.sections(page + 1, IntStream.range(0, page)));
    }

    /** A pattern at the final legal index requires just its section and the first available section. */
    @Test
    void sections_maximumCapacity_isSparse() {
        int slot = Integer.MAX_VALUE - 1;
        int page = PatternTerminalLayout.SECTION_SIZE;
        int start = slot - slot % page;
        assertEquals(Map.of(0, page, start, Integer.MAX_VALUE - start),
                PatternTerminalLayout.sections(Integer.MAX_VALUE, IntStream.of(slot)));
    }
}
