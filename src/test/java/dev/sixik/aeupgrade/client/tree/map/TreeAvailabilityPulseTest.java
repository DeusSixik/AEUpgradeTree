package dev.sixik.aeupgrade.client.tree.map;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.sixik.aeupgrade.tree.UpgradePlan.Availability;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class TreeAvailabilityPulseTest {
    @ParameterizedTest
    @CsvSource({
            "0, 0.38, 0, 0",
            "180, 0.69, 0, 0",
            "360, 1, 0, 0",
            "550, 1, 0, 0",
            "800, 0.69, 0, 0",
            "1050, 0.38, 0, 0",
            "1142, 0.38, 0.1, 0",
            "1240, 0.38, 0.20652174, 0",
            "1700, 0.38, 0.70652174, 0.5",
            "2160, 0.38, 1, 1",
            "2399, 0.38, 1, 1"
    })
    void pulseTimeline_linkFadesBeforeEitherRingExpands(int phase, float linkOpacity, float first, float second) {
        // Two ULPs cover the rounding of the smoothstep and normalized progress calculations.
        float precision = 2 * Math.ulp(1f);
        assertEquals(linkOpacity, TreeAvailabilityPulse.linkOpacity(phase), precision);
        assertEquals(first, TreeAvailabilityPulse.rippleProgress(phase, 0), precision);
        assertEquals(second, TreeAvailabilityPulse.rippleProgress(phase, TreeAvailabilityPulse.RIPPLE_SPACING), precision);
    }

    @ParameterizedTest
    @CsvSource({"0, 0", "0.1, 0.648", "0.5, 0.2", "1, 0"})
    void rippleOpacity_lifetime_risesThenFades(float progress, float opacity) {
        assertEquals(opacity, TreeAvailabilityPulse.rippleOpacity(progress), 2 * Math.ulp(1f));
    }

    @ParameterizedTest
    @CsvSource({
            "UNLOCKED, AVAILABLE, true", "AVAILABLE, AVAILABLE, false",
            "UNLOCKED, UNLOCKED, false", "UNLOCKED, PREREQUISITE, false",
            "UNLOCKED, POINTS, false", "UNLOCKED, EXCLUSIVE, false"
    })
    void pulsesLink_requiresUnlockedSourceAndPurchasableTarget(Availability source, Availability target, boolean pulses) {
        assertEquals(pulses, TreeAvailabilityPulse.pulsesLink(source, target));
    }
}
