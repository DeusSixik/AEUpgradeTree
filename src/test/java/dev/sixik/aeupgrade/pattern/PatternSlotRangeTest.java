package dev.sixik.aeupgrade.pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Checks the slot arithmetic without allocating storage for the reported capacity. */
class PatternSlotRangeTest {
    /**
     * Checks when the second visual row is needed for unlocked or stored slots.
     *
     * @param browsable total slots available to browse
     * @param expected whether the second row is visible
     */
    @ParameterizedTest
    @CsvSource({"0,false", "9,false", "10,true", "18,true", "19,true"})
    void secondRow_visibilityStartsAfterNativeCapacity(int browsable, boolean expected) {
        assertEquals(expected, PatternSlotRange.needsSecondRow(browsable));
    }

    /**
     * Checks slot adjustments at ordinary and maximum capacities.
     *
     * @param bonus starting extra slot count
     * @param delta signed extra slot adjustment
     * @param expected final extra slot count
     */
    @ParameterizedTest
    @CsvSource({"0,1,1", "1,-3,-2", "0,-9,-9", "-9,-1,-9", "-9,1,-8",
            "0,2147483647,2147483638", "2147483638,1,2147483638"})
    void adjust_clampsAtValidCapacity(int bonus, int delta, int expected) {
        assertEquals(expected, PatternSlotRange.adjust(bonus, delta));
    }

    /**
     * Checks that scrolling preserves columns and can reach an incomplete final row.
     *
     * @param requested requested logical offset
     * @param browsable total navigable slots
     * @param expected clamped offset of the first visible slot
     */
    @ParameterizedTest
    @CsvSource({"1,0,0", "5,9,0", "2,10,0", "9,18,0", "9,19,9", "18,24,9", "27,27,9", "18,28,18",
            "17,100,9", "-3,100,0", "2147483648,2147483647,2147483637"})
    void clampOffset_alignsToAvailableRows(long requested, int browsable, int expected) {
        assertEquals(expected, PatternSlotRange.clampOffset(requested, browsable));
    }
}
