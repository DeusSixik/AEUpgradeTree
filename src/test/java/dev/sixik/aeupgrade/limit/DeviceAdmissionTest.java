package dev.sixik.aeupgrade.limit;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Verifies the slot boundaries used for per-grid device limits. */
class DeviceAdmissionTest {
    /**
     * Checks the first position outside the configured set of operating devices.
     *
     * @param count connected devices of the same family
     * @param limit allowed devices, or -1 for unlimited
     * @param expected first blocked position, or count when all devices fit
     */
    @ParameterizedTest
    @CsvSource({"33,32,32", "32,32,32", "31,32,31", "1,0,0", "0,0,0", "99,-1,99"})
    void firstBlockedIndex_familyQuota_keepsOnlyAllowedSlots(int count, int limit, int expected) {
        assertEquals(expected, DeviceAdmission.firstBlockedIndex(count, limit));
    }

    /**
     * Checks the effective quota with a network bonus.
     *
     * @param base server limit, or -1 for unlimited
     * @param bonus extra network slots
     * @param expected effective number of slots, or -1 for unlimited
     */
    @ParameterizedTest
    @CsvSource({"32,16,48", "32,0,32", "0,16,16", "-1,16,-1", "2147483647,16,2147483647"})
    void limitWithBonus_serverLimit_resolvesEffectiveQuota(int base, int bonus, int expected) {
        assertEquals(expected, DeviceAdmission.limitWithBonus(base, bonus));
    }

    /**
     * Checks adding and removing network slots within the supported numeric range.
     *
     * @param bonus extra network slots before the change
     * @param delta signed change in slots
     * @param expected remaining extra slots
     */
    @ParameterizedTest
    @CsvSource({"0,16,16", "16,-4,12", "4,-16,0", "2147483647,1,2147483647"})
    void adjustSlots_signedChange_clampsToAllowedRange(int bonus, int delta, int expected) {
        assertEquals(expected, DeviceAdmission.adjustSlots(bonus, delta));
    }
}
