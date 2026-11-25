package dev.sixik.aeupgrade.client.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.sixik.aeupgrade.tree.UpgradeEffect;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Kind;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Target;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class UpgradeEffectTextTest {
    @ParameterizedTest
    @CsvSource({
            "SPEED_PERCENT, 15, 150, +150%",
            "SPEED_BASE, '1.5%', 15, +15%",
            "IDLE_PERCENT, '-1,5', -15, −15%",
            "CRAFT_PERCENT, 0.12345, 1.2345, +1.2345%",
            "IDLE_FLAT, 15, 15, +15",
            "CHANNEL_COST, -2, -2, −2",
            "DEVICE_CAP, 16, 16, 16"
    })
    void parseAndFormatEditorAmount_usesScaleOnlyForPercentageKinds(
            Kind kind, String input, double expected, String formatted) {
        double value = UpgradeEffectText.parseEditorAmount(kind.percentage(), input);
        assertEquals(expected, value, 0);
        assertEquals(formatted, UpgradeEffectText.amount(kind, value));
    }

    @ParameterizedTest
    @CsvSource({
            "SPEED_PERCENT, 50, 5",
            "SPEED_BASE, 100, 10",
            "IDLE_PERCENT, -15, -1.5",
            "CRAFT_PERCENT, 0, 0",
            "CRAFT_PERCENT, 1.2345678901234567, 0.12345678901234567",
            "IDLE_FLAT, 15, 15"
    })
    void editorAmount_existingValuesRetainTheirRuntimeAmount(Kind kind, double value, String expected) {
        var effect = new UpgradeEffect(kind, Target.ALL, value);
        String editorAmount = UpgradeEffectText.editorAmount(effect);
        assertEquals(expected, editorAmount);
        assertEquals(value, UpgradeEffectText.parseEditorAmount(kind.percentage(), editorAmount), 0);
    }

    @ParameterizedTest
    @CsvSource({"NaN", "Infinity", "1e309", "'1%%'", "'%'"})
    void parseEditorAmount_incompleteOrNonfinitePercentageIsRejected(String input) {
        assertThrows(NumberFormatException.class, () -> UpgradeEffectText.parseEditorAmount(true, input));
    }
}
