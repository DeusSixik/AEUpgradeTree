package dev.sixik.aeupgrade.tuning;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** Checks the per-provider limit and the reservation lifecycle. */
class ProviderTickBudgetTest {
    /** Confirmed dispatches share the same budget and the following tick starts fresh. */
    @Test
    void reserve_sharedProviderLimit_resetsOnNextTick() {
        var budget = new ProviderTickBudget();
        for (int i = 0; i < ProviderDispatch.DEFAULT_THROUGHPUT; i++) {
            assertTrue(budget.reserve(100, ProviderDispatch.DEFAULT_THROUGHPUT));
        }
        assertFalse(budget.reserve(100, ProviderDispatch.DEFAULT_THROUGHPUT));
        assertEquals(0, budget.remaining(100, ProviderDispatch.DEFAULT_THROUGHPUT));
        assertEquals(32, budget.remaining(101, ProviderDispatch.DEFAULT_THROUGHPUT));
        assertTrue(budget.reserve(101, ProviderDispatch.DEFAULT_THROUGHPUT));
        assertEquals(31, budget.remaining(101, ProviderDispatch.DEFAULT_THROUGHPUT));
    }

    /** A refused transfer frees the slot, and a live limit change still accounts for earlier sends. */
    @Test
    void release_refusedDispatch_restoresCapacityWithoutResettingTick() {
        var budget = new ProviderTickBudget();
        assertTrue(budget.reserve(20, 1));
        budget.release();
        assertTrue(budget.reserve(20, 1));
        assertEquals(0, budget.remaining(20, 1));
        assertEquals(1, budget.remaining(20, 2));
    }
}
