package dev.sixik.aeupgrade.client.tree.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import dev.sixik.aeupgrade.tree.UpgradeEffect.Kind;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;

class EnergyEffectUnitsTest {
    @ParameterizedTest
    @CsvSource({
            "IDLE_FLAT, IDLE_FLAT, IDLE_PERCENT",
            "IDLE_PERCENT, IDLE_FLAT, IDLE_PERCENT",
            "CRAFT_FLAT, CRAFT_FLAT, CRAFT_PERCENT",
            "CRAFT_PERCENT, CRAFT_FLAT, CRAFT_PERCENT"
    })
    void storedEnergyEffectsMapBackToBothUnits(Kind stored, Kind perTick, Kind percent) {
        var property = EnergyEffectUnits.property(stored);
        assertEquals(perTick, property);
        assertEquals(perTick, EnergyEffectUnits.resolve(property, false));
        assertEquals(percent, EnergyEffectUnits.resolve(property, true));
        assertEquals(stored, EnergyEffectUnits.resolve(property, stored != property));
    }

    @ParameterizedTest
    @EnumSource(value = Kind.class, mode = EnumSource.Mode.EXCLUDE,
            names = { "IDLE_FLAT", "IDLE_PERCENT", "CRAFT_FLAT", "CRAFT_PERCENT" })
    void otherPropertiesKeepTheirOwnKind(Kind kind) {
        assertNull(EnergyEffectUnits.of(kind));
        assertEquals(kind, EnergyEffectUnits.property(kind));
        assertEquals(kind, EnergyEffectUnits.resolve(kind, true));
    }
}
