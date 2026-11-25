package dev.sixik.aeupgrade.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sixik.aeupgrade.tree.UpgradeEffect.Impact;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Kind;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UpgradeEffectTest {
    @ParameterizedTest
    @CsvSource({
            "SPEED_PERCENT, 50, BENEFIT",
            "SPEED_CARDS, -1, PENALTY",
            "DEVICE_LIMIT, 4, BENEFIT",
            "DEVICE_CAP, 16, PENALTY",
            "DEVICE_CAP, 0, PENALTY",
            "IDLE_FLAT, 10, PENALTY",
            "IDLE_PERCENT, -20, BENEFIT",
            "CRAFT_FLAT, -12.5, BENEFIT",
            "CRAFT_PERCENT, 25, PENALTY",
            "CHANNEL_COST, 1, PENALTY",
            "CHANNEL_COST, -1, BENEFIT",
            "CHANNELS, -0.0, NEUTRAL"
    })
    void impact_usesTheMeaningOfEachModifier(Kind kind, double amount, Impact impact) {
        assertEquals(impact, new UpgradeEffect(kind, Target.ALL, amount).impact());
    }

    @Test
    void tooltipColor_isOptionalValidatedAndIndependentOfImpact() {
        var legacy = new UpgradeEffect(Kind.IDLE_FLAT, Target.ALL, 10);
        var custom = new UpgradeEffect(Kind.IDLE_FLAT, Target.ALL, 10, "  aB7890  ");
        assertNull(legacy.tooltipColor());
        assertEquals("#AB7890", custom.tooltipColor());
        assertTrue(custom.valid());
        assertEquals(legacy.impact(), custom.impact());
        assertFalse(new UpgradeEffect(Kind.IDLE_FLAT, Target.ALL, 10, "#12XX34").valid());
    }
}
