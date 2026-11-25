package dev.sixik.aeupgrade.tuning;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Verifies energy calculation, progress normalization, and virtual speed limits. */
class DeviceTuningMathTest {
    /**
     * Checks that the percentage is applied first, then the signed AE/t offset.
     *
     * @param base original idle or crafting AE per tick
     * @param percent signed percentage of the original draw
     * @param flat signed AE per tick offset
     * @param expected adjusted non-negative draw in AE per tick
     */
    @ParameterizedTest
    @CsvSource({"10,5,5,15.5", "0,5,5,5", "10,-50,-2,3", "10,-100,0,0", "10,0,-20,0",
            "250,-50,5,130", "250,0,0,250"})
    void adjustedDraw_signedModifiers_appliesPercentageBeforeFlat(double base, double percent, double flat, double expected) {
        assertEquals(expected, DeviceTuningMath.adjustedDraw(base, percent, flat), 1e-9);
    }

    /**
     * Checks that progress depends on the fraction of the new energy bill paid.
     *
     * @param base equivalent AE for a full unmodified step
     * @param requested adjusted AE for a full step
     * @param paid delivered AE
     * @param expected equivalent AE passed back to AE2's progress formula
     */
    @ParameterizedTest
    @CsvSource({"250,130,130,250", "250,130,65,125", "250,130,0,0", "250,300,300,250", "250,130,131,250"})
    void equivalentEnergy_partialPower_preservesWorkFraction(double base, double requested, double paid, double expected) {
        assertEquals(expected, DeviceTuningMath.equivalentEnergy(base, requested, paid), 1e-9);
    }

    /**
     * Checks that signed virtual cards combine with installed cards within AE2's range.
     *
     * @param installed physical card count
     * @param virtual signed virtual card adjustment
     * @param expected effective card count
     */
    @ParameterizedTest
    @CsvSource({"0,5,5", "2,2,4", "4,2,5", "5,0,5", "0,0,0", "3,-2,1", "1,-5,0"})
    void effectiveSpeedCards_signedAdjustment_clampsToCardRange(int installed, int virtual, int expected) {
        assertEquals(expected, DeviceTuningMath.effectiveSpeedCards(installed, virtual));
    }

    /**
     * Checks speed above the card maximum, slowdown, and sub-unit rates.
     *
     * @param base card progress per tick before adjustment
     * @param percent signed speed adjustment
     * @param expected adjusted progress per tick
     */
    @ParameterizedTest
    @CsvSource({"50,-50,25", "50,50,75", "50,100,100", "50,200,100", "10,-95,0.5", "10,-100,0"})
    void speed_cardProgress_scalesUpAndDown(double base, double percent, double expected) {
        assertEquals(expected, DeviceTuningMath.speed(base, percent), 1e-9);
    }

    /**
     * Checks that full batches start immediately while partial batches get a chance to fill.
     *
     * @param queued number of accepted jobs
     * @param limit configured size of the batch
     * @param elapsed ticks since the last accepted input
     * @param expected expected readiness
     */
    @ParameterizedTest
    @CsvSource({"4,4,0,true", "3,4,3,false", "1,4,4,true", "0,4,5,false", "4,2,0,true"})
    void batchReady_queueAndTimeout_startsOnlyWhenReady(int queued, int limit, long elapsed, boolean expected) {
        assertEquals(expected, DeviceTuningMath.batchReady(queued, limit, elapsed));
    }
}
