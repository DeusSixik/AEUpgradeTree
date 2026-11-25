package dev.sixik.aeupgrade.tree.research;

/**
 * An item reward delivered as the registry item's default stack variant.
 *
 * @param item   namespaced item ID
 * @param amount number of items delivered to ME storage
 */
public record ResearchItemReward(String item, int amount) {
}
