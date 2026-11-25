package dev.sixik.aeupgrade.tree.research;

import java.util.HashSet;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import dev.sixik.aeupgrade.tree.UpgradeCatalog;
import org.jetbrains.annotations.Nullable;

/**
 * Defines a one-time research task for every ME network.
 *
 * @param id          stable namespaced ID used to persist progress
 * @param enabled     whether this task is offered when the feature is enabled
 * @param type        event or resource required for completion
 * @param item        item ID used as an optional craft filter or required item
 * @param amount      required jobs or items, from 1 to 1,000,000
 * @param points      exact research point reward
 * @param title       literal name or a translation key starting with {@code @}
 * @param description optional literal description or translation key starting with {@code @}
 * @param tier        number of the tier offering this task
 * @param repeatable  whether this task can be offered again after a reroll
 * @param itemRewards items deposited into the network on completion
 */
public record ResearchTask(String id, Boolean enabled, Type type, @Nullable String item, int amount, int points,
                           String title, @Nullable String description, int tier, boolean repeatable,
                           List<ResearchItemReward> itemRewards) {
    /**
     * Maximum goal in jobs or items.
     */
    public static final int MAX_AMOUNT = 1_000_000;
    /**
     * Maximum distinct reward entries for one task.
     */
    public static final int MAX_ITEM_REWARDS = 8;
    /**
     * Maximum title length in Java characters.
     */
    public static final int MAX_TITLE = 80;
    /**
     * Maximum description length in Java characters.
     */
    public static final int MAX_DESCRIPTION = 240;
    /**
     * Maximum length of a configured resource ID in Java characters.
     */
    public static final int MAX_ID = 100;
    /**
     * Syntax for an explicit Minecraft namespace and path.
     */
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    /**
     * Defaults older configurations to points-only rewards.
     */
    public ResearchTask {
        itemRewards = itemRewards == null ? List.of() : List.copyOf(itemRewards);
    }

    /**
     * What counts toward a research goal.
     */
    public enum Type {
        /**
         * Count completed CPU jobs, optionally matching their final item.
         */
        CRAFT_JOBS,
        /**
         * Count the final output items of completed CPU jobs for a specific item.
         */
        CRAFT_ITEMS,
        /**
         * Take a specific item directly from AE2 network storage.
         */
        DONATE_ITEMS
    }

    /**
     * Creates a one-time fallback-tier task in the original format.
     *
     * @param id          stable task ID
     * @param enabled     whether the task is offered
     * @param type        completion rule
     * @param item        optional item ID
     * @param amount      required work
     * @param points      reward points
     * @param title       visible name
     * @param description optional visible description
     */
    public ResearchTask(String id, Boolean enabled, Type type, @Nullable String item, int amount, int points,
                        String title, @Nullable String description) {
        this(id, enabled, type, item, amount, points, title, description, 0, false, List.of());
    }

    /**
     * Creates a tiered task with a points-only reward.
     *
     * @param id          stable task ID
     * @param enabled     whether the task is offered
     * @param type        completion rule
     * @param item        optional goal item ID
     * @param amount      required work
     * @param points      reward points
     * @param title       visible name
     * @param description optional visible description
     * @param tier        configured tier
     * @param repeatable  whether the task may return after a reroll
     */
    public ResearchTask(String id, Boolean enabled, Type type, @Nullable String item, int amount, int points,
                        String title, @Nullable String description, int tier, boolean repeatable) {
        this(id, enabled, type, item, amount, points, title, description, tier, repeatable, List.of());
    }

    /**
     * Validates configuration fields and, for an active task, resolves its item against the registry.
     *
     * @param active     whether the server is offering this task
     * @param itemExists lookup for registered items usable in this server
     * @throws IllegalArgumentException for invalid fields or an unavailable required item
     */
    void validate(boolean active, Predicate<String> itemExists) {
        if (!validId(id) || enabled == null || type == null || amount < 1 || amount > MAX_AMOUNT
                || points < 0 || points > UpgradeCatalog.MAX_POINTS || points == 0 && itemRewards.isEmpty()
                || title == null || title.isBlank()
                || title.length() > MAX_TITLE || description != null && description.length() > MAX_DESCRIPTION) {
            throw new IllegalArgumentException("Invalid research task " + id + ".");
        }
        if ((item != null || type != Type.CRAFT_JOBS) && (!validId(item) || active && !itemExists.test(item))) {
            throw new IllegalArgumentException("Invalid item " + item + " for research task " + id + ".");
        }
        if (itemRewards.size() > MAX_ITEM_REWARDS) {
            throw new IllegalArgumentException("Too many item rewards on research task " + id + ".");
        }
        var items = new HashSet<String>();
        for (var reward : itemRewards) {
            if (!validId(reward.item()) || reward.amount() < 1 || reward.amount() > MAX_AMOUNT
                    || !items.add(reward.item()) || active && !itemExists.test(reward.item())) {
                throw new IllegalArgumentException("Invalid item reward " + reward.item() + " for research task " + id + ".");
            }
        }
    }

    /**
     * Computes the contribution of a completed CPU job to this task.
     *
     * @param outputItem   final requested AE2 item ID, or null for a non-item job
     * @param outputAmount requested quantity of AE2 job output
     * @return number of jobs or items to count, capped to this task's goal
     */
    int craftCredit(@Nullable String outputItem, long outputAmount) {
        if (type == Type.DONATE_ITEMS || item != null && !item.equals(outputItem)) {
            return 0;
        }
        return type == Type.CRAFT_JOBS ? 1 : (int) Math.clamp(outputAmount, 0, amount);
    }

    /**
     * Adds work without exceeding the goal or overflowing the result.
     *
     * @param current work previously counted, in jobs or items
     * @param credit  newly completed work, in the same units
     * @param goal    positive maximum for this task
     * @return progress after counting up to the outstanding amount
     */
    static int advance(int current, long credit, int goal) {
        int existing = Math.clamp(current, 0, goal);
        return existing + (int) Math.clamp(credit, 0, goal - existing);
    }

    /**
     * Checks syntax and length of a namespaced ID.
     *
     * @param value configured namespaced ID
     * @return whether the ID exists and fits the wire format
     */
    private static boolean validId(@Nullable String value) {
        return value != null && value.length() <= MAX_ID && RESOURCE_ID.matcher(value).matches();
    }
}
