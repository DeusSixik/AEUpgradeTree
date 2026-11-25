package dev.sixik.aeupgrade.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tests command adjustments and cable capacity scaling. */
class CableChannelCapacityTest {
    /**
     * Verifies that signed adjustments are applied and clamped without integer overflow.
     *
     * @param current current normal cable limit in channels
     * @param delta signed adjustment in channels
     * @param expected expected new limit in channels
     */
    @ParameterizedTest
    @CsvSource({"8, 4, 12", "12, -4, 8", "8, -2, 6", "8, -9, 0", "0, 1, 1", "8, 2147483647, 4194303"})
    void adjust_signedDelta_returnsBoundedLimit(int current, int delta, int expected) {
        assertEquals(expected, CableChannelCapacity.adjust(current, delta));
    }

    /**
     * Verifies that scaling preserves the difference between cable types and channel modes.
     *
     * @param ae2Limit raw AE2 cable capacity in channels
     * @param normalLimit configured normal default-mode cable limit in channels
     * @param expected expected effective cable limit in channels
     */
    @ParameterizedTest
    @CsvSource({"8, 12, 12", "32, 12, 48", "16, 12, 24", "128, 12, 192", "32, 6, 24", "32, 0, 0"})
    void scale_cableAndChannelMode_preservesTheirMultipliers(int ae2Limit, int normalLimit, int expected) {
        assertEquals(expected, CableChannelCapacity.scale(ae2Limit, normalLimit));
    }
}
