package dev.sixik.aeupgrade.tree.research;

import dev.sixik.aeupgrade.tree.UpgradeCatalog;
import org.jetbrains.annotations.Nullable;

/**
 * Defines when a network uses a task pool and when its finished offers rotate.
 *
 * @param id        tier number, with zero reserved for the fallback
 * @param minPoints inclusive lower bound for a numbered tier
 * @param maxPoints exclusive upper bound, or null for an open-ended tier
 * @param visible   maximum simultaneous offers
 * @param reroll    number of claimed offers needed to replace them
 */
public record ResearchTier(int id, int minPoints, @Nullable Integer maxPoints, int visible, int reroll) {
    /**
     * Default settings for the required fallback tier.
     */
    public static final ResearchTier DEFAULT = new ResearchTier(0, 0, null, 3, 1);
    /**
     * Maximum allowed numeric tier ID.
     */
    public static final int MAX_ID = 10_000;

    /**
     * @throws IllegalArgumentException when the bounds or board size cannot be used
     */
    void validate() {
        if (id < 0 || id > MAX_ID || minPoints < 0 || minPoints > UpgradeCatalog.MAX_POINTS
                || maxPoints != null && (maxPoints <= minPoints || maxPoints > UpgradeCatalog.MAX_POINTS + 1)
                || id == 0 && (minPoints != 0 || maxPoints != null)
                || visible < 1 || visible > ResearchTaskConfig.MAX_TASKS || reroll < 1 || reroll > visible) {
            throw new IllegalArgumentException("Invalid research tier " + id + ".");
        }
    }

    /**
     * @param points total research points, including spent points
     * @return whether the points are in the tier's half-open range
     */
    boolean matches(int points) {
        return points >= minPoints && (maxPoints == null || points < maxPoints);
    }
}
