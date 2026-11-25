package dev.sixik.aeupgrade.tree.research;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import java.util.random.RandomGenerator;

/**
 * Stores a single network's offers for one tier.
 *
 * @param token     changes when the board changes, so obsolete clicks cannot affect a fresh offer
 * @param offers    task IDs in screen order
 * @param completed claimed offers awaiting replacement
 */
public record ResearchTaskBoard(long token, List<String> offers, Set<String> completed) {
    /**
     * Board before this controller's first draw.
     */
    public static final ResearchTaskBoard EMPTY = new ResearchTaskBoard(0, List.of(), Set.of());

    /**
     * Copies persisted and newly generated collections into immutable state.
     */
    public ResearchTaskBoard {
        offers = List.copyOf(offers);
        completed = Set.copyOf(completed);
    }

    /**
     * Reconciles the saved offers with the configured pool and replaces finished offers when enough have paid out.
     *
     * @param tier    current settings for this board
     * @param pool    enabled task definitions in the tier
     * @param claimed tests whether a one-time reward has already been paid on this profile
     * @param random  source for choosing unseen offers
     * @return current board or replacement board, preserving unfinished offers
     */
    ResearchTaskBoard refresh(ResearchTier tier, List<ResearchTask> pool, Predicate<String> claimed, RandomGenerator random) {
        var eligible = new ArrayList<String>();
        var existing = new HashSet<String>();
        for (var task : pool) {
            String id = task.id();
            if (task.repeatable() || !claimed.test(id)) {
                eligible.add(id);
                existing.add(id);
            } else if (completed.contains(id)) {
                existing.add(id);
            }
        }
        var visible = new ArrayList<>(offers.stream().filter(existing::contains).limit(tier.visible()).toList());
        var finished = new HashSet<>(completed);
        finished.retainAll(visible);
        fill(visible, eligible, Set.of(), tier.visible(), random);
        if (!finished.isEmpty() && finished.size() >= threshold(tier, visible.size())) {
            visible.removeIf(finished::contains);
            fill(visible, eligible, finished, tier.visible(), random);
            finished.clear();
        }
        return offers.equals(visible) && completed.equals(finished) ? this
                : new ResearchTaskBoard(random.nextLong(), visible, finished);
    }

    /**
     * Marks a paid task as finished while it waits for the rest of the reroll quota.
     *
     * @param id     validated offer receiving a reward
     * @param random source for a fresh request token
     * @return board with this offer marked completed
     */
    ResearchTaskBoard complete(String id, RandomGenerator random) {
        var finished = new HashSet<>(completed);
        finished.add(id);
        return new ResearchTaskBoard(random.nextLong(), offers, finished);
    }

    /**
     * Uses the configured quota unless the pool has fewer offers than the quota.
     *
     * @param tier       settings for the displayed tier
     * @param offerCount offers currently shown, including claimed ones
     * @return number of claims needed to trigger a rotation, or zero for an empty board
     */
    static int threshold(ResearchTier tier, int offerCount) {
        return Math.min(tier.reroll(), offerCount);
    }

    /**
     * Fills open positions from eligible tasks, preferring alternatives to newly rotated offers.
     *
     * @param offers   current ordered board slots
     * @param eligible task IDs that can be selected
     * @param rotated  freshly completed tasks to rank after alternatives
     * @param limit    maximum concurrent offers
     * @param random   random selection source
     */
    private static void fill(List<String> offers, List<String> eligible, Set<String> rotated, int limit, RandomGenerator random) {
        if (offers.size() >= limit) {
            return;
        }
        var available = new ArrayList<>(eligible);
        available.removeAll(offers);
        Collections.shuffle(available, random);
        if (!rotated.isEmpty()) {
            available.sort(Comparator.comparing(rotated::contains));
        }
        offers.addAll(available.subList(0, Math.min(limit - offers.size(), available.size())));
    }
}
