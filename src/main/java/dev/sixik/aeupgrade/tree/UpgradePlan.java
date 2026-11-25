package dev.sixik.aeupgrade.tree;

import java.util.ArrayList;
import java.util.BitSet;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import dev.sixik.aeupgrade.tree.UpgradeCatalog.Blocker;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Path;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Kind;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Target;
import org.jetbrains.annotations.Nullable;

/**
 * Immutable point allocation shared by the server and the upgrade screen.
 */
public final class UpgradePlan {
    /**
     * Development points available per ME network.
     */
    public static final int BUDGET = 36;
    /**
     * Maximum operating assemblers in a network with Overdrive.
     */
    public static final int OVERDRIVE_LIMIT = 16;
    /**
     * Allocation with all points unspent and all paths open.
     */
    public static final UpgradePlan EMPTY = empty(UpgradeCatalog.DEFAULT);

    /**
     * Purchased stable node IDs. This bit set belongs exclusively to the allocation.
     */
    private final BitSet nodes;
    /**
     * Remaining points from the development budget.
     */
    private final int points;
    private final int total;
    /**
     * Portion of the total originating from the free initial grant.
     */
    private final int startingAllowance;
    /**
     * Definition under which these purchases were validated.
     */
    private final UpgradeCatalog catalog;
    /**
     * Aggregated effects from the core and all active nodes.
     */
    private final UpgradeEffects effects;
    /**
     * Reason for each closed branch in this allocation.
     */
    private final Map<Integer, Blocker> blockers;
    /**
     * Whether the operator has opened every node in the current tree for free.
     */
    private final boolean allUnlocked;

    /**
     * A node's status in the current allocation.
     */
    public enum Availability {
        /**
         * The node can be purchased.
         */
        AVAILABLE,
        /**
         * The node is already unlocked.
         */
        UNLOCKED,
        /**
         * More of the connected prerequisites must be unlocked first.
         */
        PREREQUISITE,
        /**
         * Another path in the same group has been chosen.
         */
        EXCLUSIVE,
        /**
         * The node needs a connection to the tree.
         */
        DISCONNECTED,
        /**
         * The network has too few unspent points.
         */
        POINTS,
        /**
         * The requested node has been removed from the server tree.
         */
        UNKNOWN
    }

    /**
     * Stores the validated allocation.
     *
     * @param nodes             owned bit set with no other mutable references
     * @param points            unspent points
     * @param total             all points granted to this allocation, including spent points
     * @param startingAllowance remaining free initial points, excluded from controller drops
     * @param catalog           definition of the current tree
     * @param effects           aggregated modifiers
     * @param blockers          reason for each closed branch
     * @param allUnlocked       whether all configured nodes and their effects are active
     */
    private UpgradePlan(BitSet nodes, int points, int total, int startingAllowance, UpgradeCatalog catalog, UpgradeEffects effects,
                        Map<Integer, Blocker> blockers, boolean allUnlocked) {
        this.nodes = nodes;
        this.points = points;
        this.total = total;
        this.startingAllowance = startingAllowance;
        this.catalog = catalog;
        this.effects = effects;
        this.blockers = blockers;
        this.allUnlocked = allUnlocked;
    }

    /**
     * @param catalog definition providing the budget
     * @return empty allocation for that tree
     */
    static UpgradePlan empty(UpgradeCatalog catalog) {
        return empty(catalog, catalog.definition().initialPoints());
    }

    private static UpgradePlan empty(UpgradeCatalog catalog, int total) {
        return empty(catalog, total, Math.min(total, catalog.definition().initialPoints()));
    }

    private static UpgradePlan empty(UpgradeCatalog catalog, int total, int startingAllowance) {
        return new UpgradePlan(new BitSet(), total, total, startingAllowance, catalog, catalog.coreEffects(), Map.of(), false);
    }

    /**
     * @return granted points, including points currently allocated to nodes
     */
    public int total() {
        return total;
    }

    /**
     * @return configured ceiling covering free and spent points
     */
    public int maximum() {
        return catalog.budget();
    }

    /**
     * @return points from the initial grant still contained in this allocation
     */
    int startingAllowance() {
        return startingAllowance;
    }

    /**
     * @return recoverable earned points, including points spent on upgrades
     */
    int earnedPoints() {
        return total - startingAllowance;
    }

    /**
     * Applies the current refund rate when the last controller block is destroyed.
     *
     * @param carriedPoints earned points carried over from another profile in the structure
     * @return certificate count, rounded down, or zero when drops are disabled
     */
    int recoveredCertificates(int carriedPoints) {
        if (!catalog.definition().dropResearchOnBreak()) {
            return 0;
        }
        return refundAmount(Math.min((long) earnedPoints() + carriedPoints, Integer.MAX_VALUE));
    }

    /**
     * Checks whether a refund can remove research in this allocation.
     *
     * @param nodeId purchased node ID, or the root sentinel for a full reset
     * @return whether the request can remove at least one node
     */
    public boolean canRefund(int nodeId) {
        return nodeId == UpgradeNode.ROOT ? !isEmpty() : !allUnlocked && nodeId >= 0 && nodes.get(nodeId);
    }

    /**
     * Calculates a refund for all purchases or for a specific unlocked node.
     *
     * @param nodeId purchased node ID, or the root sentinel to refund all purchases
     * @return changed allocation and exact preview, including descendants that become invalid
     */
    public Refund refund(int nodeId) {
        if (!canRefund(nodeId)) {
            return new Refund(this, List.of(), 0, 0);
        }
        var retained = nodeId == UpgradeNode.ROOT ? empty(catalog, total, startingAllowance)
                : restore(nodes.stream().filter(id -> id != nodeId), catalog, total, startingAllowance);
        int spent = retained.points - points;
        int returned = refundAmount(spent);
        int lost = spent - returned;
        int newTotal = total - lost;
        var plan = new UpgradePlan(retained.nodes, points + returned, newTotal, Math.min(startingAllowance, newTotal),
                catalog, retained.effects, retained.blockers, false);
        var removed = allUnlocked ? catalog.ordered().stream().map(UpgradeNode::id).sorted().toList()
                : nodes.stream().filter(id -> !retained.nodes.get(id)).boxed().toList();
        return new Refund(plan, removed, returned, lost);
    }

    private int refundAmount(long amount) {
        return (int) (amount * catalog.definition().refundPercent() / 100);
    }

    /**
     * @param plan       resulting allocation
     * @param removedIds removed purchased nodes in ascending ID order
     * @param returned   points refunded to the network
     * @param lost       points deducted from the network's total
     */
    public record Refund(UpgradePlan plan, List<Integer> removedIds, int returned, int lost) {
    }

    /**
     * @param amount requested points
     * @return allocation with the accepted amount added up to the network ceiling
     */
    UpgradePlan grant(int amount) {
        int accepted = Math.min(Math.max(0, amount), catalog.budget() - total);
        return accepted == 0 ? this : new UpgradePlan(nodes, points + accepted, total + accepted, startingAllowance,
                catalog, effects, blockers, allUnlocked);
    }

    /**
     * Sets the unspent balance within the ceiling left after accounting for paid purchases.
     *
     * @param amount requested unspent points, clamped between zero and the ceiling minus spent points
     * @return updated allocation, or the existing allocation if the balance stays the same
     */
    UpgradePlan withRemaining(int amount) {
        int spent = total - points;
        int remaining = Math.clamp(amount, 0, catalog.budget() - spent);
        int newTotal = spent + remaining;
        return remaining == points ? this : new UpgradePlan(nodes, remaining, newTotal,
                Math.min(startingAllowance, newTotal), catalog, effects, blockers, allUnlocked);
    }

    /**
     * Activates every configured upgrade, preserving the original purchases for a future reset.
     *
     * @return allocation with all effects active, or this allocation if already active or the tree has no nodes
     */
    UpgradePlan unlockAll() {
        if (allUnlocked || catalog.ordered().isEmpty()) {
            return this;
        }
        var activeEffects = catalog.coreEffects();
        for (var node : catalog.ordered()) {
            activeEffects = activeEffects.add(node.id(), catalog.entry(node.id()).effects());
        }
        return new UpgradePlan(nodes, points, total, startingAllowance, catalog, activeEffects, Map.of(), true);
    }

    /**
     * @return whether operator-granted access to the whole tree is active
     */
    public boolean allUnlocked() {
        return allUnlocked;
    }

    /**
     * Compares the research state used for rendering and refunds.
     *
     * @param other allocation whose paid node IDs and access flag are compared
     * @return whether both allocations have the same purchases and whole-tree access
     */
    public boolean sameUnlocks(UpgradePlan other) {
        return allUnlocked == other.allUnlocked && nodes.equals(other.nodes);
    }

    /**
     * Gives old saves the new starting amount or enough points to keep their valid purchases.
     */
    static UpgradePlan migrate(UpgradePlan legacy) {
        int spent = legacy.total - legacy.points;
        int total = Math.max(legacy.catalog.definition().initialPoints(), spent);
        return new UpgradePlan(legacy.nodes, total - spent, total, total, legacy.catalog,
                legacy.effects, legacy.blockers, false);
    }

    /**
     * @return remaining points from the development budget
     */
    public int remaining() {
        return points;
    }

    /**
     * @return true if no upgrades besides the core are active
     */
    public boolean isEmpty() {
        return !allUnlocked && nodes.isEmpty();
    }

    /**
     * Checks a node's allocated state.
     *
     * @param node upgrade to check
     * @return whether the node is active through a purchase or whole-tree access
     */
    public boolean unlocked(UpgradeNode node) {
        return node.id() == UpgradeNode.ROOT || nodes.get(node.id()) || allUnlocked && catalog.node(node.id()) != null;
    }

    /**
     * @return purchased node IDs in ascending order for NBT
     */
    int[] savedIds() {
        return nodes.stream().toArray();
    }

    /**
     * @return paid node IDs in ascending order for the menu snapshot
     */
    public List<Integer> ids() {
        return nodes.stream().boxed().toList();
    }

    /**
     * @return effective tree crafting speed contribution in percentage points
     */
    public double speedBonus() {
        return effects.amount(Kind.SPEED_BASE) + effects.amount(Kind.SPEED_PERCENT);
    }

    /**
     * @return cached modifiers from the core and all active nodes
     */
    public UpgradeEffects effects() {
        return effects;
    }

    /**
     * Resolves the assembler limit including the tree's base caps and slot adjustments.
     *
     * @param limit configured limit in devices, or -1 for unlimited
     * @return effective assembler limit in devices, or -1 for unlimited
     */
    public int assemblerLimit(int limit) {
        return effects.deviceLimit(Target.MOLECULAR_ASSEMBLER, limit);
    }

    /**
     * Checks route exclusivity, connected prerequisites and points.
     *
     * @param node catalog node to evaluate
     * @return current availability, with an exclusive path reported before prerequisites
     */
    public Availability availability(UpgradeNode node) {
        if (unlocked(node)) {
            return Availability.UNLOCKED;
        }
        if (blockers.containsKey(node.id())) {
            return Availability.EXCLUSIVE;
        }
        if (node.disconnected()) {
            return Availability.DISCONNECTED;
        }
        if (unlockedPrerequisites(node) < node.requiredCount()) {
            return Availability.PREREQUISITE;
        }
        return points < node.cost() ? Availability.POINTS : Availability.AVAILABLE;
    }

    /**
     * @param node upgrade that depends on other nodes
     * @return number of its incoming prerequisites already unlocked
     */
    public int unlockedPrerequisites(UpgradeNode node) {
        if (allUnlocked) {
            return node.prerequisiteCount();
        }
        int unlocked = 0;
        for (int i = 0; i < node.prerequisiteCount(); i++) {
            if (nodes.get(node.prerequisiteId(i))) {
                unlocked++;
            }
        }
        return unlocked;
    }

    /**
     * @param node catalog node to evaluate
     * @return selected path blocking the node, or null when its path is available
     */
    public @Nullable Path blockingPath(UpgradeNode node) {
        var blocker = blockingNode(node);
        return blocker == null ? null : catalog.pathOf(blocker.id());
    }

    /**
     * @param node catalog node to evaluate
     * @return purchased upgrade that conflicts with the given node, or null
     */
    public @Nullable UpgradeNode blockingNode(UpgradeNode node) {
        var blocker = blockers.get(node.id());
        return blocker == null || blocker.choiceLimit() ? null : blocker.source();
    }

    /**
     * @param node catalog node to evaluate
     * @return continuation whose branch quota closed the node, or null
     */
    public @Nullable UpgradeNode blockingChoice(UpgradeNode node) {
        var blocker = blockers.get(node.id());
        return blocker == null || !blocker.choiceLimit() ? null : blocker.source();
    }

    /**
     * Previews the branch entrances that a purchase would close under the current allocation.
     *
     * @param node catalog node to purchase
     * @return newly closed entrances in dependency order, omitting descendants that lose access through them
     */
    public List<UpgradeNode> newlyClosedBranches(UpgradeNode node) {
        if (!catalog.hasExclusions(node.id()) || availability(node) != Availability.AVAILABLE) {
            return List.of();
        }
        var next = (BitSet) nodes.clone();
        next.set(node.id());
        var closed = catalog.blockingNodes(next);
        var entrances = new ArrayList<UpgradeNode>();
        for (var candidate : catalog.ordered()) {
            int id = candidate.id();
            if (next.get(id) || blockers.containsKey(id) || !closed.containsKey(id)) {
                continue;
            }
            long reachableInputs = candidate.prerequisiteIds().stream()
                    .filter(input -> next.get(input) || !closed.containsKey(input))
                    .count();
            if (reachableInputs >= candidate.requiredCount()) {
                entrances.add(candidate);
            }
        }
        return List.copyOf(entrances);
    }

    /**
     * Allocates one upgrade without mutating the previous state.
     *
     * @param node catalog node to purchase
     * @return new plan for an available upgrade, or the existing plan
     */
    UpgradePlan purchase(UpgradeNode node) {
        if (availability(node) != Availability.AVAILABLE) {
            return this;
        }
        var next = (BitSet) nodes.clone();
        next.set(node.id());
        return new UpgradePlan(next, points - node.cost(), total, startingAllowance, catalog,
                effects.add(node.id(), catalog.entry(node.id()).effects()),
                catalog.hasExclusions(node.id()) ? catalog.blockingNodes(next) : blockers, false);
    }

    /**
     * Migrates the original seven-bit controller state.
     *
     * @param mask legacy NBT integer containing purchased node bits
     * @return valid plan retaining the purchased functional upgrades
     */
    static UpgradePlan fromLegacyMask(int mask) {
        return fromLegacyMask(mask, UpgradeCatalog.DEFAULT);
    }

    /**
     * @param mask    legacy purchase bits
     * @param catalog server definition
     * @return migrated allocation
     */
    static UpgradePlan fromLegacyMask(int mask, UpgradeCatalog catalog) {
        return restore(IntStream.range(0, UpgradeNode.LEGACY_COUNT).filter(id -> (mask & (1 << id)) != 0),
                catalog, catalog.budget());
    }

    /**
     * Replays saved choices in catalog order, dropping corrupt IDs and incompatible routes.
     *
     * @param ids persisted stable node IDs
     * @return validated allocation with its points and route locks reconstructed
     */
    static UpgradePlan restore(int[] ids) {
        return restore(ids, UpgradeCatalog.DEFAULT);
    }

    /**
     * Reconstructs the allocation received from a menu snapshot.
     *
     * @param ids purchased node IDs
     * @return validated allocation
     */
    static UpgradePlan restore(List<Integer> ids) {
        return restore(ids, UpgradeCatalog.DEFAULT);
    }

    /**
     * @param ids     persisted IDs from NBT
     * @param catalog current server definition
     * @return validated allocation
     */
    static UpgradePlan restore(int[] ids, UpgradeCatalog catalog) {
        return restore(ids, catalog, catalog.budget());
    }

    static UpgradePlan restore(int[] ids, UpgradeCatalog catalog, int total) {
        return restore(IntStream.of(ids), catalog, total);
    }

    static UpgradePlan restore(int[] ids, UpgradeCatalog catalog, int total, int startingAllowance) {
        return restore(IntStream.of(ids), catalog, total, startingAllowance);
    }

    /**
     * @param ids     purchased IDs from the server
     * @param catalog server definition
     * @return validated allocation for the current graph
     */
    public static UpgradePlan restore(List<Integer> ids, UpgradeCatalog catalog) {
        return restore(ids, catalog, catalog.definition().initialPoints());
    }

    /**
     * @param ids     purchases received with the point total
     * @param catalog server definition
     * @param total   total points received from the server
     * @return reconstructed allocation
     */
    public static UpgradePlan restore(List<Integer> ids, UpgradeCatalog catalog, int total) {
        return restore(ids.stream().mapToInt(Integer::intValue), catalog, total);
    }

    /**
     * Reconstructs the effective allocation from the server snapshot.
     *
     * @param ids         paid purchases, as stable node IDs
     * @param catalog     current server definition
     * @param total       total research points, including paid purchases
     * @param allUnlocked whether the operator has opened the entire tree
     * @return reconstructed allocation
     */
    public static UpgradePlan restore(List<Integer> ids, UpgradeCatalog catalog, int total, boolean allUnlocked) {
        var plan = restore(ids, catalog, total);
        return allUnlocked ? plan.unlockAll() : plan;
    }

    /**
     * @param catalog replacement graph
     * @return validated purchases for the replacement graph
     */
    UpgradePlan update(UpgradeCatalog catalog) {
        if (this.catalog == catalog) {
            return this;
        }
        var updated = restore(nodes.stream(), catalog, total, startingAllowance);
        return allUnlocked ? updated.unlockAll() : updated;
    }

    /**
     * Replays allowed IDs in prerequisite order so save and packet ordering cannot affect valid plans.
     *
     * @param ids     stream of requested unlocked node IDs
     * @param catalog definition ordering the prerequisites
     * @param total   granted points before deducting purchases
     * @return consistent allocation within the point budget
     */
    private static UpgradePlan restore(IntStream ids, UpgradeCatalog catalog, int total) {
        return restore(ids, catalog, total, catalog.definition().initialPoints());
    }

    private static UpgradePlan restore(IntStream ids, UpgradeCatalog catalog, int total, int startingAllowance) {
        var saved = new BitSet();
        ids.filter(id -> id >= 0 && id < UpgradeCatalog.ID_LIMIT).forEach(saved::set);
        int boundedTotal = Math.clamp(total, 0, catalog.budget());
        var plan = empty(catalog, boundedTotal, Math.clamp(startingAllowance, 0, boundedTotal));
        for (var node : catalog.ordered()) {
            if (saved.get(node.id())) {
                plan = plan.purchase(node);
            }
        }
        return plan;
    }
}
