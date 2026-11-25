package dev.sixik.aeupgrade.tree.research;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

/** Checks tier boundaries and the persistence-friendly task rotation rules. */
class ResearchTaskBoardTest {
    /** Ensures half-open ranges can meet and tier zero covers all remaining balances. */
    @Test
    void tier_usesTotalPointRangesAndFallsBackAtGaps() {
        var document = ResearchTaskConfig.parse("""
                {"enabled":true,"tasks":[],"tiers":[
                {"id":1,"minPoints":10,"maxPoints":20,"visible":5,"reroll":2},
                {"id":3,"minPoints":20,"visible":6,"reroll":3}]}
                """);
        var config = ResearchTaskConfig.compile(document, id -> false);
        assertEquals(ResearchTier.DEFAULT, config.tier(9));
        assertEquals(1, config.tier(10).id());
        assertEquals(1, config.tier(19).id());
        assertEquals(3, config.tier(20).id());
        assertEquals(3, config.tier(10_000).id());
    }

    /** Ensures ambiguous ranges, impossible quotas and missing tiers are rejected. */
    @Test
    void compile_rejectsInvalidTierSettings() {
        var one = new ResearchTier(1, 10, 20, 5, 2);
        var overlapping = new ResearchTier(2, 19, null, 5, 2);
        var badQuota = new ResearchTier(2, 20, null, 3, 4);
        var unassigned = new ResearchTask("test:task", true, ResearchTask.Type.CRAFT_JOBS, null, 1, 1, "Task", null, 4, false);
        assertThrows(IllegalArgumentException.class, () -> ResearchTaskConfig.compile(
                new ResearchTaskConfig.Document(true, List.of(), List.of(one, overlapping)), id -> false));
        assertThrows(IllegalArgumentException.class, () -> ResearchTaskConfig.compile(
                new ResearchTaskConfig.Document(true, List.of(), List.of(badQuota)), id -> false));
        assertThrows(IllegalArgumentException.class, () -> ResearchTaskConfig.compile(
                new ResearchTaskConfig.Document(true, List.of(unassigned), List.of(one)), id -> false));
    }

    /** Confirms a completed offer stays visible until the configured quota is met. */
    @Test
    void refresh_twoClaimsReplaceOnlyTheirOffers() {
        var tier = new ResearchTier(1, 10, 20, 5, 2);
        var pool = pool(7, false);
        var claimed = new HashSet<String>();
        var random = new Random(17);
        var first = ResearchTaskBoard.EMPTY.refresh(tier, pool, claimed::contains, random);
        assertEquals(5, first.offers().size());
        assertSame(first, first.refresh(tier, pool, claimed::contains, random));
        String firstTask = first.offers().get(0);
        String secondTask = first.offers().get(1);
        claimed.add(firstTask);
        var held = first.complete(firstTask, random);
        assertSame(held, held.refresh(tier, pool, claimed::contains, random));
        assertEquals(Set.of(firstTask), held.completed());
        assertEquals(first.offers(), held.offers());
        claimed.add(secondTask);
        var next = held.complete(secondTask, random).refresh(tier, pool, claimed::contains, random);
        assertEquals(first.offers().subList(2, 5), next.offers().subList(0, 3));
        assertEquals(5, next.offers().size());
        assertTrue(next.completed().isEmpty());
        assertFalse(next.offers().contains(firstTask));
        assertFalse(next.offers().contains(secondTask));
        assertNotEquals(held.token(), next.token());
    }

    /** Keeps a small pool usable and favors other tasks before repeating the freshly paid offer. */
    @Test
    void refresh_smallRepeatablePoolRotatesAndOneTimePoolExhausts() {
        var tier = new ResearchTier(0, 0, null, 3, 2);
        var random = new Random(23);
        var repeatable = pool(1, true);
        var initial = ResearchTaskBoard.EMPTY.refresh(tier, repeatable, id -> false, random);
        assertEquals(1, ResearchTaskBoard.threshold(tier, initial.offers().size()));
        String id = initial.offers().getFirst();
        var repeated = initial.complete(id, random).refresh(tier, repeatable, task -> false, random);
        assertEquals(initial.offers(), repeated.offers());
        assertTrue(repeated.completed().isEmpty());
        assertNotEquals(initial.token(), repeated.token());
        var exhausted = initial.complete(id, random).refresh(tier, pool(1, false), id::equals, random);
        assertTrue(exhausted.offers().isEmpty());
    }

    /**
     * Creates distinct tasks for drawing boards.
     *
     * @param count number of offers in the pool
     * @param repeatable whether those offers can return
     * @return tasks with unique test IDs
     */
    private static List<ResearchTask> pool(int count, boolean repeatable) {
        return IntStream.range(0, count).mapToObj(id -> new ResearchTask("test:task_" + id, true,
                ResearchTask.Type.CRAFT_JOBS, null, 1, 1, "Task " + id, null, 0, repeatable)).toList();
    }
}
