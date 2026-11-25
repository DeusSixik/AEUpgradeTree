package dev.sixik.aeupgrade.channel;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Tests the scope precedence and whole-cost availability check for devices. */
class ChannelCostRulesTest {
    /**
     * Verifies that the most specific configured value wins and that zero is explicit.
     *
     * @param local local override or -1 for inheritance
     * @param network network override or -1 for inheritance
     * @param global global override or -1 for inheritance
     * @param expected resolved value in channels or -1
     */
    @ParameterizedTest
    @CsvSource({"-1,-1,-1,-1", "-1,-1,2,2", "-1,3,2,3", "4,3,2,4", "0,3,2,0",
            "-1,0,2,0", "-1,-1,0,0", "2,-1,-1,2", "-1,2,-1,2"})
    void resolve_localNetworkAndGlobalCost_preservesPrecedence(int local, int network, int global, int expected) {
        assertEquals(expected, ChannelCostRules.resolve(local, network, global));
    }

    /**
     * Verifies that AE2's comparison rejects the entire request when the full cost does not fit.
     *
     * @param capacity total capacity in channels
     * @param used channels already reserved
     * @param cost requested channels
     * @param rejects whether to reject the request
     */
    @ParameterizedTest
    @CsvSource({"8,6,2,false", "8,7,2,true", "8,8,1,true", "0,0,1,true", "8,0,9,true",
            "2147483647,2147483646,2,true", "2147483647,0,2147483647,false"})
    void rejectionThreshold_routeCapacity_acceptsOnlyFullAllocation(int capacity, int used, int cost, boolean rejects) {
        assertEquals(rejects, used >= ChannelCostRules.rejectionThreshold(capacity, cost));
    }
}
