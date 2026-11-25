package dev.sixik.aeupgrade.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Locale;

import com.google.gson.Gson;
import com.google.gson.JsonParser;
import dev.sixik.aeupgrade.api.UpgradeEffectRegistry;
import dev.sixik.aeupgrade.api.UpgradeEffectType;
import dev.sixik.aeupgrade.api.UpgradeEffectType.Polarity;
import dev.sixik.aeupgrade.api.UpgradeEffectType.Stacking;
import dev.sixik.aeupgrade.api.UpgradeEffectType.Unit;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Definition;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Entry;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Impact;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Kind;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Target;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class CustomUpgradeEffectsTest {
    private static final UpgradeEffectType SPEED = register("speed", Unit.PERCENT, Stacking.SUM, true);
    private static final UpgradeEffectType COUNT = register("count", Unit.INTEGER, Stacking.SUM, false);
    private static final String MISSING = "missing_addon:upgrade";

    @Test
    void registry_rejectsDuplicateIds() {
        assertSame(SPEED, UpgradeEffectRegistry.get(SPEED.id()));
        assertThrows(IllegalArgumentException.class, () -> UpgradeEffectRegistry.register(
                new UpgradeEffectType(SPEED.id(), "different", Unit.INTEGER, Polarity.NEUTRAL, Stacking.MAXIMUM, false)));
        assertSame(SPEED, UpgradeEffectRegistry.get(SPEED.id()));
    }

    @Test
    void config_roundTrip_keepsBuiltInRegisteredAndUnavailableEffects() {
        var registered = UpgradeEffect.custom(SPEED, Target.MOLECULAR_ASSEMBLER, 25);
        var missing = new UpgradeEffect(null, Target.INTERFACE, 12.25, "#11AABB", MISSING);
        var builtin = new UpgradeEffect(Kind.CHANNELS, Target.ALL, 3);
        var definition = new Definition(10, 8, null, List.of(entry(7, List.of(registered, missing, builtin))));

        var gson = new Gson();
        String json = gson.toJson(definition);
        var decoded = new UpgradeCatalog(gson.fromJson(json, Definition.class));
        assertEquals(definition.nodes().getFirst().effects(), decoded.entry(7).effects());
        var serialized = JsonParser.parseString(json).getAsJsonObject().getAsJsonArray("nodes").get(0)
                .getAsJsonObject().getAsJsonArray("effects");
        assertEquals(MISSING, serialized.get(1).getAsJsonObject().get("type").getAsString());
        assertEquals("CHANNELS", serialized.get(2).getAsJsonObject().get("kind").getAsString());

        var effects = UpgradePlan.restore(List.of(7), decoded).effects();
        assertEquals(25.0, effects.amount(SPEED, Target.MOLECULAR_ASSEMBLER, 0), 0);
        assertEquals(3.0, effects.amount(Kind.CHANNELS), 0);
        var missingType = new UpgradeEffectType(MISSING, "unused",
                Unit.DECIMAL, Polarity.NEUTRAL, Stacking.SUM, true);
        assertEquals(-1.0, effects.amount(missingType, Target.INTERFACE, -1), 0);
    }

    @Test
    void valid_registeredType_validatesUnitScopeAndAmount() {
        assertTrue(UpgradeEffect.custom(COUNT, Target.ALL, -2).valid());
        assertFalse(UpgradeEffect.custom(COUNT, Target.ALL, 1.5).valid());
        assertFalse(UpgradeEffect.custom(COUNT, Target.INTERFACE, 1).valid());
        assertFalse(UpgradeEffect.custom(COUNT, Target.ALL, 3e9).valid());
        assertFalse(UpgradeEffect.custom(SPEED, Target.ALL, Double.NaN).valid());
        assertFalse(UpgradeEffect.custom(SPEED, Target.ALL, Double.POSITIVE_INFINITY).valid());
        assertFalse(new UpgradeEffect(null, Target.ALL, 1, null, "unqualified").valid());
        assertFalse(new UpgradeEffect(Kind.BATCH, Target.ALL, 1, null, SPEED.id()).valid());
        assertEquals(Impact.PENALTY, UpgradeEffect.custom(SPEED, Target.ALL, 10).impact());
        assertEquals(Impact.BENEFIT, UpgradeEffect.custom(SPEED, Target.ALL, -10).impact());
        assertTrue(UpgradeEffect.custom(SPEED, Target.ALL, 10).percentage());
    }

    @ParameterizedTest
    @CsvSource({"SUM, 19, 9", "MINIMUM, 2, 2", "MAXIMUM, 7, 7", "HIGHEST_NODE_ID, 6, 2"})
    void aggregate_appliesStackingAcrossScopesAndResolvesNodePriority(
            Stacking rule, double expectedAssembler, double expectedGlobal) {
        var type = register(rule.name().toLowerCase(Locale.ROOT), Unit.DECIMAL, rule, true);
        var first = UpgradeEffects.EMPTY.add(19, List.of(
                UpgradeEffect.custom(type, Target.ALL, 2),
                UpgradeEffect.custom(type, Target.MOLECULAR_ASSEMBLER, 4),
                UpgradeEffect.custom(type, Target.MOLECULAR_ASSEMBLER, 6)));
        var combined = first.add(7, List.of(UpgradeEffect.custom(type, Target.ALL, 7)));

        assertEquals(expectedAssembler, combined.amount(type, Target.MOLECULAR_ASSEMBLER, -1), 0);
        assertEquals(expectedGlobal, combined.amount(type, Target.ALL, -1), 0);
        assertEquals(expectedGlobal, combined.amount(type, Target.INTERFACE, -1), 0);
        assertEquals(2.0, first.amount(type, Target.ALL, -1), 0);
        assertEquals(-1.0, UpgradeEffects.EMPTY.amount(type, Target.ALL, -1), 0);
    }

    @Test
    void allocation_changesPublishEffectiveValuesAndKeepZeroDistinctFromAbsence() {
        var zeroCatalog = new UpgradeCatalog(new Definition(10, 8, null,
                List.of(entry(7, List.of(UpgradeEffect.custom(COUNT, Target.ALL, 0))))));
        var newCatalog = new UpgradeCatalog(new Definition(10, 8, null,
                List.of(entry(7, List.of(UpgradeEffect.custom(COUNT, Target.ALL, 4))))));
        var unpurchased = UpgradePlan.empty(zeroCatalog);
        var zero = unpurchased.purchase(zeroCatalog.node(7));
        var updated = zero.update(newCatalog);
        var reset = UpgradePlan.empty(newCatalog);

        assertEquals(0.0, zero.effects().amount(COUNT, Target.ALL, -1), 0);
        assertEquals(4.0, updated.effects().amount(COUNT, Target.ALL, -1), 0);
        assertTrue(zero.effects().changed(unpurchased.effects(), COUNT));
        assertTrue(updated.effects().customChanged(zero.effects()));
        assertTrue(reset.effects().customChanged(updated.effects()));
        assertFalse(updated.effects().customChanged(UpgradePlan.restore(List.of(7), newCatalog).effects()));
    }

    private static UpgradeEffectType register(String name, Unit unit, Stacking stacking, boolean targeted) {
        return UpgradeEffectRegistry.register(new UpgradeEffectType(
                "aeupgrade_test:" + name, "effect.test." + name,
                unit, Polarity.LOWER_IS_BETTER, stacking, targeted));
    }

    private static Entry entry(int id, List<UpgradeEffect> effects) {
        var node = new UpgradeNode(id, "test_" + id, 1, 0, UpgradeNode.Sector.NETWORK, null,
                UpgradeNode.ROOT, UpgradeNode.ROOT, id * 20, 0, UpgradeNode.Kind.PASSIVE);
        return new Entry(node, "", "", "", effects, null, null, List.of());
    }
}
