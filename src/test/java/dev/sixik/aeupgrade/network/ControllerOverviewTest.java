package dev.sixik.aeupgrade.network;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Optional;

import dev.sixik.aeupgrade.network.ControllerOverview.CraftRange;
import dev.sixik.aeupgrade.network.ControllerOverview.EnergyChange;
import dev.sixik.aeupgrade.network.ControllerOverview.MachineStats;
import dev.sixik.aeupgrade.network.ControllerOverview.Stat;
import dev.sixik.aeupgrade.network.ControllerOverview.StatChange;
import org.junit.jupiter.api.Test;

/** Checks how controller diagnostics aggregate mixed device tuning. */
class ControllerOverviewTest {
    /** Verifies the UI can report savings and added idle draw even when they cancel out. */
    @Test
    void merge_oppositeIdleDeltas_preservesBothDirections() {
        var result = new EnergyChange(5, 0, Optional.empty()).merge(new EnergyChange(0, 5, Optional.empty()));
        assertTrue(result.changed());
        assertEquals(5, result.added(), 0.000001);
        assertEquals(5, result.saved(), 0.000001);
    }

    /** Verifies that absent crafting data does not add a zero to the assembler range. */
    @Test
    void merge_craftSettings_reportsOnlyApplicableAssemblers() {
        var a = new EnergyChange(0, 0, Optional.of(new CraftRange(-25, -25, 3, 3)));
        var b = new EnergyChange(0, 0, Optional.of(new CraftRange(-10, -10, 5, 5)));
        var result = EnergyChange.NONE.merge(a).merge(b);
        assertEquals(Optional.of(new CraftRange(-25, -10, 3, 5)), result.crafting());
        assertFalse(EnergyChange.NONE.changed());
        assertFalse(new EnergyChange(0, 0, Optional.of(new CraftRange(0, 0, 0, 0))).changed());
    }

    /** Default-only settings do not add lines to a machine's tooltip. */
    @Test
    void visibleStats_defaultValues_omitsUnchangedSettings() {
        var group = new MachineStats();
        group.add(Stat.BATCH, 1, 1);
        group.add(Stat.SPEED, 0, 0);
        group.add(Stat.CHANNEL_COST, 1, 1);
        assertTrue(group.visibleStats().isEmpty());
    }

    /** A mixed group includes both default and customized endpoints in display order. */
    @Test
    void visibleStats_mixedTuning_keepsDefaultEndpoints() {
        var group = new MachineStats();
        group.add(Stat.SPEED, -50, 0);
        group.add(Stat.SPEED, 0, 0);
        group.add(Stat.SPEED, 20, 0);
        group.add(Stat.BATCH, 1, 1);
        group.add(Stat.BATCH, 4, 1);
        assertEquals(List.of(new StatChange(Stat.BATCH, 1, 4), new StatChange(Stat.SPEED, -50, 20)),
                group.visibleStats());
    }

    /** A zero-valued override stays visible when the device would normally require a channel. */
    @Test
    void visibleStats_zeroChannelCost_reportsOverride() {
        var group = new MachineStats();
        group.add(Stat.CHANNEL_COST, 0, 1);
        group.add(Stat.CHANNEL_COST, 0, 0);
        assertEquals(List.of(new StatChange(Stat.CHANNEL_COST, 0, 0)), group.visibleStats());
    }
}
