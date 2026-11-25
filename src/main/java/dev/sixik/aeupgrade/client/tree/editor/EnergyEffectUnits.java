package dev.sixik.aeupgrade.client.tree.editor;

import dev.sixik.aeupgrade.tree.UpgradeEffect.Kind;
import org.jetbrains.annotations.Nullable;

/**
 * Pairs the existing per-tick and percentage effects shown as one property in the editor.
 *
 * @param perTick effect kind for a fixed amount in AE/t
 * @param percent effect kind for a percentage adjustment
 */
record EnergyEffectUnits(Kind perTick, Kind percent) {
    private static final EnergyEffectUnits IDLE = new EnergyEffectUnits(Kind.IDLE_FLAT, Kind.IDLE_PERCENT);
    private static final EnergyEffectUnits CRAFT = new EnergyEffectUnits(Kind.CRAFT_FLAT, Kind.CRAFT_PERCENT);

    /**
     * @param kind stored effect kind
     * @return supported units, or null for a property with a single unit
     */
    static @Nullable EnergyEffectUnits of(Kind kind) {
        return switch (kind) {
            case IDLE_FLAT, IDLE_PERCENT -> IDLE;
            case CRAFT_FLAT, CRAFT_PERCENT -> CRAFT;
            default -> null;
        };
    }

    /**
     * @param kind stored effect kind
     * @return representative kind for the property dropdown
     */
    static Kind property(Kind kind) {
        var units = of(kind);
        return units == null ? kind : units.perTick();
    }

    /**
     * @param property   representative kind selected in the property dropdown
     * @param percentage whether the amount is in percent
     * @return effect kind to store in the draft
     */
    static Kind resolve(Kind property, boolean percentage) {
        var units = of(property);
        return units == null ? property : percentage ? units.percent() : units.perTick();
    }
}
