package dev.sixik.aeupgrade.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import com.google.gson.Gson;
import dev.sixik.aeupgrade.api.UpgradeEffectRegistry;
import dev.sixik.aeupgrade.api.UpgradeEffectType;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Core;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Definition;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Entry;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Kind;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Target;
import org.junit.jupiter.api.Test;

class UpgradeCoreTest {
    private static final UpgradeEffectType PRIORITY = UpgradeEffectRegistry.register(
            new UpgradeEffectType("aeupgrade_test:core_priority", "effect.test.core_priority", UpgradeEffectType.Unit.INTEGER,
                    UpgradeEffectType.Polarity.NEUTRAL, UpgradeEffectType.Stacking.HIGHEST_NODE_ID, false));

    @Test
    void allocation_coreEffectsApplyForFreeAndSurviveReset() {
        var core = new Core("Starting network", "Defaults", "ae2:drive",
                List.of(new UpgradeEffect(Kind.CHANNELS, Target.ALL, -2),
                        new UpgradeEffect(Kind.DEVICE_CAP, Target.MOLECULAR_ASSEMBLER, 5),
                        new UpgradeEffect(Kind.SPEED_BASE, Target.ALL, 30),
                        UpgradeEffect.custom(PRIORITY, Target.ALL, 7)), 32, 44, null);
        var childEffects = List.of(new UpgradeEffect(Kind.CHANNELS, Target.ALL, 4),
                new UpgradeEffect(Kind.SPEED_BASE, Target.ALL, 50), UpgradeEffect.custom(PRIORITY, Target.ALL, 13));
        var catalog = catalog(core, childEffects);
        var initial = UpgradePlan.empty(catalog);
        assertTrue(initial.unlocked(core.asEntry().node()));
        assertSame(initial, initial.purchase(core.asEntry().node()));
        assertEquals(12, initial.remaining());
        assertEquals(List.of(), initial.ids());
        assertEquals(-2.0, initial.effects().amount(Kind.CHANNELS), 0);
        assertEquals(5, initial.assemblerLimit(-1));
        assertEquals(30.0, initial.speedBonus(), 0);
        assertEquals(7.0, initial.effects().amount(PRIORITY, Target.ALL, -1), 0);

        var bought = UpgradePlan.restore(List.of(0), catalog);
        assertEquals(List.of(0), bought.ids());
        assertEquals(2.0, bought.effects().amount(Kind.CHANNELS), 0);
        assertEquals(50.0, bought.speedBonus(), 0);
        assertEquals(13.0, bought.effects().amount(PRIORITY, Target.ALL, -1), 0);
        assertSame(initial.effects(), UpgradePlan.empty(catalog).effects());

        var updated = bought.update(catalog(Core.DEFAULT, childEffects));
        assertEquals(List.of(0), updated.ids());
        assertEquals(4.0, updated.effects().amount(Kind.CHANNELS), 0);
        assertEquals(-1, updated.assemblerLimit(-1));
    }

    @Test
    void config_legacyAndCustomizedCore_keepTheirSettings() {
        var gson = new Gson();
        var legacy = new UpgradeCatalog(gson.fromJson("{\"budget\":12,\"nextId\":7,\"nodes\":[]}", Definition.class));
        assertEquals(Core.DEFAULT, legacy.definition().core());
        assertSame(UpgradeEffects.EMPTY, UpgradePlan.empty(legacy).effects());

        var core = new Core("Custom core", "Starting bonuses", "ae2:drive",
                List.of(new UpgradeEffect(Kind.PATTERN_SLOTS, Target.ALL, 3)), 64, -32,
                new UpgradeNodeStyle(48, UpgradeNodeStyle.Shape.DIAMOND, "ae2:textures/item/wireless_terminal.png",
                        List.of(), "#6699FF", 3f, 4f));
        var reloaded = new UpgradeCatalog(gson.fromJson(gson.toJson(catalog(core, List.of()).definition()), Definition.class));
        var saved = reloaded.definition().core();
        assertEquals(core, saved);
        assertEquals(saved, Core.fromEntry(saved.asEntry()));
        assertThrows(IllegalArgumentException.class, () -> catalog(new Core("", "", "", List.of(), Float.NaN, 0, null), List.of()));
        assertThrows(IllegalArgumentException.class, () -> catalog(new Core("", "", "",
                List.of(new UpgradeEffect(Kind.DEVICE_CAP, Target.ALL, -1)), 0, 0, null), List.of()));
    }

    private static UpgradeCatalog catalog(Core core, List<UpgradeEffect> childEffects) {
        var base = new Definition(12, 7, null, List.of(new Entry(UpgradeNode.CHANNELS, "", "", "", childEffects)));
        return new UpgradeCatalog(new Definition(base.budget(), base.nextId(), null, base.nodes(), base.paths(),
                base.experimental(), base.nodeSettings(), core));
    }
}
