package dev.sixik.aeupgrade.limit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Verifies how local limits interact with the server default. */
class NetworkDeviceLimitTest {
    /**
     * Checks fixed, inherited, and bonus limits against the current server policy.
     *
     * @param overridden whether this network has a fixed limit
     * @param value fixed limit or inherited bonus
     * @param base server default device limit
     * @param expected effective network device limit
     */
    @ParameterizedTest
    @CsvSource({"true,5,-1,5", "true,5,32,5", "true,0,-1,0", "true,-1,32,-1",
            "false,0,-1,-1", "false,4,-1,-1", "false,4,32,36"})
    void resolve_networkSetting_selectsEffectiveLimit(boolean overridden, int value, int base, int expected) {
        assertEquals(expected, new NetworkDeviceLimit(overridden, value).resolve(base));
    }

    /**
     * Checks that adjustment preserves the setting mode, including an absolute zero limit.
     *
     * @param overridden whether this network has a fixed limit
     * @param value fixed limit or inherited bonus
     * @param delta signed slot change
     * @param expected adjusted value within the same mode
     */
    @ParameterizedTest
    @CsvSource({"true,5,2,7", "true,5,-20,0", "true,-1,2,-1", "false,5,-20,0"})
    void adjust_slotChange_preservesSettingMode(boolean overridden, int value, int delta, int expected) {
        assertEquals(new NetworkDeviceLimit(overridden, expected),
                new NetworkDeviceLimit(overridden, value).adjust(delta));
    }
}
