package dev.sixik.aeupgrade.tree.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Covers item payout validation and recovery from a partial storage insertion. */
class ResearchRewardDeliveryTest {
    /** Confirms that a known capacity failure leaves storage and the outstanding reward intact. */
    @Test
    void deposit_insufficientCapacityDoesNotInsertAnyItems() {
        var delivery = new ResearchRewardDelivery(2,
                List.of(new ResearchItemReward("minecraft:iron_ingot", 5), new ResearchItemReward("minecraft:gold_ingot", 3)));
        var result = delivery.deposit(reward -> reward.item().endsWith("gold_ingot") ? 2 : reward.amount(),
                reward -> { throw new AssertionError("No item should be inserted"); },
                checkpoint -> { throw new AssertionError("No partial delivery should be recorded"); });
        assertSame(delivery, result);
    }

    /** Confirms that independent simulations can oversubscribe shared capacity and that retries use only the remainder. */
    @Test
    void deposit_partialInsertionCheckpointsAndRetriesOnlyOutstandingItems() {
        var iron = new ResearchItemReward("minecraft:iron_ingot", 5);
        var gold = new ResearchItemReward("minecraft:gold_ingot", 3);
        var delivery = new ResearchRewardDelivery(2, List.of(iron, gold));
        var checkpoints = new ArrayList<ResearchRewardDelivery>();
        var partial = delivery.deposit(ResearchItemReward::amount,
                reward -> reward.equals(iron) ? 5 : 1, checkpoints::add);
        var rest = new ResearchItemReward(gold.item(), 2);
        assertEquals(List.of(new ResearchRewardDelivery(2, List.of(gold)), new ResearchRewardDelivery(2, List.of(rest))), checkpoints);
        var retried = new ArrayList<ResearchItemReward>();
        var complete = partial.deposit(ResearchItemReward::amount, reward -> {
            retried.add(reward);
            return reward.amount();
        }, checkpoints::add);
        assertEquals(List.of(rest), retried);
        assertEquals(new ResearchRewardDelivery(2, List.of()), complete);
    }

    /** Confirms that the legacy format and item-only or mixed rewards load with registry validation. */
    @Test
    void config_legacyAndItemRewardsLoadAndInvalidPayoutsFail() {
        var legacy = """
                {"enabled":true,"tasks":[{"id":"pack:first","enabled":true,"type":"CRAFT_JOBS","amount":1,
                 "points":1,"title":"First"}]}
                """;
        var itemsOnly = legacy.replace("\"points\":1", "\"points\":0,\"itemRewards\":[{\"item\":\"minecraft:iron_ingot\",\"amount\":5}]");
        var rewards = List.of(new ResearchItemReward("minecraft:iron_ingot", 5));
        assertEquals(List.of(), ResearchTaskConfig.parse(legacy).tasks().getFirst().itemRewards());
        var parsed = ResearchTaskConfig.parse(itemsOnly);
        assertEquals(rewards, ResearchTaskConfig.compile(parsed, "minecraft:iron_ingot"::equals).tasks().getFirst().itemRewards());
        assertEquals(parsed, ResearchTaskConfig.parse(ResearchTaskConfig.encode(parsed)));
        assertThrows(IllegalArgumentException.class, () -> ResearchTaskConfig.compile(parsed, id -> false));
        assertThrows(IllegalArgumentException.class, () -> ResearchTaskConfig.compile(
                ResearchTaskConfig.parse(itemsOnly.replace("\"amount\":5", "\"amount\":0")), id -> true));
        assertThrows(IllegalArgumentException.class, () -> ResearchTaskConfig.compile(
                ResearchTaskConfig.parse(legacy.replace("\"points\":1", "\"points\":0")), id -> true));
    }
}
