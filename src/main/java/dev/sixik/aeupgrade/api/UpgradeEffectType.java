package dev.sixik.aeupgrade.api;

import java.util.Objects;

import appeng.api.networking.IGrid;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Impact;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Target;
import dev.sixik.aeupgrade.tree.UpgradeTreeService;

/**
 * A numeric upgrade that an add-on can expose in the tree editor.
 *
 * @param id             stable identifier under the add-on's namespace
 * @param translationKey localized name for the editor and tooltip
 * @param unit           numeric input format (percentages are stored in percentage points)
 * @param polarity       determines the default tooltip color for a signed adjustment
 * @param stacking       how unlocked contributions to the same scope combine
 * @param targeted       whether the editor offers the built-in AE2 device families
 */
public record UpgradeEffectType(String id, String translationKey, Unit unit, Polarity polarity,
                                Stacking stacking, boolean targeted) {
    public UpgradeEffectType {
        if (!UpgradeEffectRegistry.validId(id)) {
            throw new IllegalArgumentException("Expected a namespaced upgrade effect ID: " + id);
        }
        if (Objects.requireNonNull(translationKey, "translationKey").isBlank()) {
            throw new IllegalArgumentException("An effect translation key is required.");
        }
        Objects.requireNonNull(unit, "unit");
        Objects.requireNonNull(polarity, "polarity");
        Objects.requireNonNull(stacking, "stacking");
    }

    public enum Unit {
        INTEGER, DECIMAL, PERCENT
    }

    public enum Polarity {
        HIGHER_IS_BETTER, LOWER_IS_BETTER, NEUTRAL;

        /**
         * @return the default tooltip impact of this amount
         */
        public Impact impact(double amount) {
            if (this == NEUTRAL || amount == 0) {
                return Impact.NEUTRAL;
            }
            return (amount > 0) == (this == HIGHER_IS_BETTER) ? Impact.BENEFIT : Impact.PENALTY;
        }
    }

    public enum Stacking {
        SUM, MINIMUM, MAXIMUM,
        /**
         * Takes the value from the highest unlocked node ID. The last effect on that node wins ties.
         */
        HIGHEST_NODE_ID
    }

    /**
     * @return whether the amount is valid for this type and device scope
     */
    public boolean accepts(Target target, double amount) {
        return target != null && (targeted || target == Target.ALL) && Double.isFinite(amount)
                && Math.abs(amount) <= Float.MAX_VALUE
                && (unit != Unit.INTEGER || amount == Math.rint(amount) && Math.abs(amount) <= Integer.MAX_VALUE);
    }

    /**
     * @return this modifier's global value on the grid, or zero when absent
     */
    public double amount(IGrid grid) {
        return amount(grid, Target.ALL, 0);
    }

    /**
     * Reads the latest modifiers for the grid. A device receives contributions from its family and the global scope.
     *
     * @param grid     AE2 grid that owns the purchases
     * @param target   scope to resolve
     * @param fallback value when no applicable modifier is unlocked
     * @return the combined amount, or the fallback when absent
     */
    public double amount(IGrid grid, Target target, double fallback) {
        return grid.getService(UpgradeTreeService.class).effects().amount(this, target, fallback);
    }
}
