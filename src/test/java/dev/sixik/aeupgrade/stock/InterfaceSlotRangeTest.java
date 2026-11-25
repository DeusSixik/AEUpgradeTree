package dev.sixik.aeupgrade.stock;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Checks relative operator adjustments at ordinary and extreme values. */
class InterfaceSlotRangeTest {
    /**
     * Checks that the resulting capacity stays within the native backing inventory.
     *
     * @param capacity starting number of configuration and buffer pairs
     * @param amount signed adjustment requested by the operator
     * @param expected capacity after applying the adjustment
     */
    @ParameterizedTest
    @CsvSource({"9,1,10", "18,1,19", "10,-1,9", "9,-9,0", "0,-1,0", "53,1,54", "54,1,54",
            "9,2147483647,54", "9,-2147483647,0"})
    void adjust_clampsBetweenZeroAndBackingCapacity(int capacity, int amount, int expected) {
        assertEquals(expected, InterfaceSlotRange.adjust(capacity, amount));
    }
}
