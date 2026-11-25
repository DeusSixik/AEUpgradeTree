package dev.sixik.aeupgrade.tree.research;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.ToIntFunction;

/**
 * Remembers the outstanding part of a payout when ME storage accepts only part of it.
 *
 * @param points    original point reward, granted after the items arrive
 * @param remaining original rewards minus items already deposited
 */
public record ResearchRewardDelivery(int points, List<ResearchItemReward> remaining) {
    /**
     * Copies the reward quantities so checkpoints cannot be mutated by later inserts.
     */
    public ResearchRewardDelivery {
        remaining = List.copyOf(remaining);
    }

    /**
     * Simulates each outstanding item, then records the actual quantities accepted.
     * Storage types may share capacity that independent simulations cannot see.
     *
     * @param simulate   checks what storage would accept without modifying it
     * @param insert     commits one entry and reports the inserted amount
     * @param checkpoint persists each actual insertion before starting the next one
     * @return outstanding items after this attempt
     */
    ResearchRewardDelivery deposit(ToIntFunction<ResearchItemReward> simulate, ToIntFunction<ResearchItemReward> insert,
                                   Consumer<ResearchRewardDelivery> checkpoint) {
        for (var reward : remaining) {
            if (simulate.applyAsInt(reward) < reward.amount()) {
                return this;
            }
        }
        var outstanding = new ArrayList<>(remaining);
        var delivery = this;
        while (!outstanding.isEmpty()) {
            var reward = outstanding.getFirst();
            int accepted = Math.clamp(insert.applyAsInt(reward), 0, reward.amount());
            if (accepted == 0) {
                break;
            }
            if (accepted == reward.amount()) {
                outstanding.removeFirst();
            } else {
                outstanding.set(0, new ResearchItemReward(reward.item(), reward.amount() - accepted));
            }
            delivery = new ResearchRewardDelivery(points, outstanding);
            checkpoint.accept(delivery);
            if (accepted < reward.amount()) {
                break;
            }
        }
        return delivery;
    }
}
