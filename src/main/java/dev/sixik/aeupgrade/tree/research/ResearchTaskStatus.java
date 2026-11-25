package dev.sixik.aeupgrade.tree.research;

import java.util.List;

import org.jetbrains.annotations.Nullable;

/**
 * Carries a task definition and the selected network's current state to the client.
 *
 * @param id          stable namespaced task ID
 * @param type        configured goal type
 * @param item        optional item registry ID
 * @param title       literal or localizable title
 * @param description optional literal or localizable description
 * @param amount      goal in jobs or items
 * @param points      full reward in research points
 * @param progress    credited work in the same units as the goal
 * @param claimed     whether the reward was paid to this profile
 * @param rewardFits  whether the balance has room for the full reward
 * @param itemRewards item rewards displayed on the task, or the quantities still owed on a retry
 */
public record ResearchTaskStatus(String id, ResearchTask.Type type, @Nullable String item, String title,
                                 @Nullable String description, int amount, int points, int progress, boolean claimed,
                                 boolean rewardFits,
                                 List<ResearchItemReward> itemRewards) {
}
