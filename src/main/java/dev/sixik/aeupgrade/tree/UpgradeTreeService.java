package dev.sixik.aeupgrade.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridServiceProvider;
import appeng.api.networking.pathing.ControllerState;
import appeng.blockentity.networking.ControllerBlockEntity;
import dev.sixik.aeupgrade.platform.Platform;
import dev.sixik.aeupgrade.channel.ChannelCapacityService;
import dev.sixik.aeupgrade.limit.DeviceLimitService;
import dev.sixik.aeupgrade.pattern.PatternSlotService;
import dev.sixik.aeupgrade.stock.InterfaceSlotService;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Kind;
import dev.sixik.aeupgrade.tree.UpgradePlan.Availability;
import dev.sixik.aeupgrade.tree.research.ResearchTask;
import dev.sixik.aeupgrade.tree.research.ResearchItemReward;
import dev.sixik.aeupgrade.tree.research.ResearchRewardDelivery;
import dev.sixik.aeupgrade.tree.research.ResearchTaskBoard;
import dev.sixik.aeupgrade.tree.research.ResearchTaskConfig;
import dev.sixik.aeupgrade.tree.research.ResearchTier;
import dev.sixik.aeupgrade.tuning.DeviceTuningService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Stores the prototype purchases on the controller and exposes the active grid bonuses.
 */
public final class UpgradeTreeService implements IGridService, IGridServiceProvider {
    /**
     * AE2 node data key for a controller's array of purchased IDs.
     */
    private static final String PURCHASES_TAG = "aeupgrade:dev_tree_nodes";
    /**
     * Previous AE2 node data key for the original seven purchase bits.
     */
    private static final String LEGACY_PURCHASES_TAG = "aeupgrade:dev_tree";
    /**
     * AE2 node data flag enabling all tree effects on this saved controller profile.
     */
    private static final String ALL_UNLOCKED_TAG = "aeupgrade:research_all_unlocked";
    private static final String POINTS_TAG = "aeupgrade:research_points";
    private static final String STARTING_ALLOWANCE_TAG = "aeupgrade:research_starting_allowance";
    private static final String CLAIMS_TAG = "aeupgrade:research_claims";
    /**
     * AE2 node data mapping saved task IDs to the number of jobs or items credited.
     */
    private static final String TASKS_TAG = "aeupgrade:research_task_progress";
    /**
     * AE2 node data containing each tier's current offers and held completion states.
     */
    private static final String TASK_BOARDS_TAG = "aeupgrade:research_task_boards";
    /**
     * AE2 node data containing outstanding payouts from partial storage inserts.
     */
    private static final String TASK_DELIVERIES_TAG = "aeupgrade:research_task_deliveries";
    /**
     * Earned points deferred to the final block when two existing profiles share a successor.
     */
    private static final String CARRIED_POINTS_TAG = "aeupgrade:research_carried_points";
    /**
     * Transferred profile held on a block entity until AE2 creates its node.
     */
    public static final String PENDING_PROFILE_TAG = "aeupgrade:pending_research_profile";
    /**
     * AE2's main node tag for powered block entities.
     */
    private static final String MAIN_NODE_TAG = "proxy";

    /**
     * Stable ordering when multiple controllers with trees become part of one grid.
     */
    private static final Comparator<IGridNode> CONTROLLER_ORDER = Comparator
            .<IGridNode, ResourceLocation>comparing(node -> node.getLevel().dimension().location())
            .thenComparing(node -> ((ControllerBlockEntity) node.getOwner()).getBlockPos());

    /**
     * Grid to which the active tree grants upgrades.
     */
    private final IGrid grid;

    /**
     * Saved allocations by controller, including an empty allocation after an explicit respec.
     */
    private final NavigableMap<IGridNode, UpgradePlan> controllers = new TreeMap<>(CONTROLLER_ORDER);
    private final NavigableMap<IGridNode, Set<ResourceLocation>> claimedRewards = new TreeMap<>(CONTROLLER_ORDER);
    /**
     * Saved task counts per controller profile, including partially completed tasks.
     */
    private final Map<IGridNode, Map<ResourceLocation, Integer>> taskProgress = new HashMap<>();
    /**
     * Persistent boards belonging to each controller profile and tier.
     */
    private final Map<IGridNode, Map<Integer, ResearchTaskBoard>> taskBoards = new HashMap<>();
    /**
     * Pending item payouts by controller profile and stable task ID.
     */
    private final Map<IGridNode, Map<ResourceLocation, ResearchRewardDelivery>> taskDeliveries = new HashMap<>();
    private final Map<IGridNode, Integer> carriedPoints = new HashMap<>();
    /**
     * Includes controllers that have never saved an allocation.
     */
    private int controllerCount;

    /**
     * Current immutable allocation effective for this grid.
     */
    private UpgradePlan purchases = UpgradePlan.EMPTY;

    /**
     * Last allocation used to invalidate the AE2 caches.
     */
    private UpgradeEffects published = UpgradeEffects.EMPTY;
    /**
     * Definition last applied to the controller allocations in this grid.
     */
    private UpgradeCatalog catalog = UpgradeCatalog.DEFAULT;

    /**
     * Creates tree state for the live grid.
     *
     * @param grid owning AE2 grid
     */
    public UpgradeTreeService(IGrid grid) {
        this.grid = grid;
    }

    /**
     * @return allocation effective for the grid
     */
    public UpgradePlan purchases() {
        synchronizeCatalog();
        return purchases;
    }

    /**
     * @return aggregated active modifiers for this grid
     */
    public UpgradeEffects effects() {
        var allocation = purchases();
        return controllerCount == 0 ? UpgradeEffects.EMPTY : allocation.effects();
    }

    /**
     * @param player prospective researcher
     * @param target controller used to open the tree
     * @return whether the operator or controller owner can change this grid's research
     */
    public boolean canResearch(ServerPlayer player, IGridNode target) {
        return researchDenial(player, target) == null;
    }

    /**
     * Reports why a player cannot manage the active profile on this network.
     *
     * @param player prospective researcher
     * @param target controller used to access research
     * @return feedback suffix for a denial, or null if the player has access
     */
    public @Nullable String researchDenial(ServerPlayer player, IGridNode target) {
        if (player.hasPermissions(2)) {
            return null;
        }
        synchronizeCatalog();
        if (catalog.definition().operatorOnly()) {
            return "research_operators_only";
        }
        return player.getUUID().equals(researchController(target).getOwningPlayerProfileId()) ? null : "research_access";
    }

    /**
     * Grants points on the active controller, optionally as a one-time reward.
     *
     * @param amount positive number of research points
     * @param reward stable reward ID for an exact one-time grant, or null for a repeatable grant
     * @return accepted points or zero if there is no eligible controller, room, or unclaimed reward
     */
    public int awardPoints(int amount, @Nullable ResourceLocation reward) {
        if (amount <= 0 || controllerCount == 0
                || grid.getPathingService().getControllerState() != ControllerState.CONTROLLER_ONLINE) {
            return 0;
        }
        var pivot = grid.getPivot();
        if (pivot == null || !pivot.getLevel().getServer().isSameThread()) {
            return 0;
        }
        synchronizeCatalog();
        var controller = controllers.isEmpty() ? firstController() : controllers.firstKey();
        if (controller == null) {
            return 0;
        }
        var granted = purchases.grant(amount);
        int accepted = granted.total() - purchases.total();
        if (accepted == 0 || reward != null && (accepted != amount
                || !claimedRewards.computeIfAbsent(controller, ignored -> new HashSet<>()).add(reward))) {
            return 0;
        }
        savePurchases(controller, granted);
        return accepted;
    }

    /**
     * Checks whether the selected research profile has received a one-time reward.
     *
     * @param target controller in the selected grid
     * @param reward stable reward ID
     * @return whether the reward was claimed
     */
    public boolean hasClaimedReward(IGridNode target, ResourceLocation reward) {
        return claimedRewards.getOrDefault(researchController(target), Set.of()).contains(reward);
    }

    /**
     * Records a finished task reward, including one-time rewards worth zero points.
     *
     * @param target validated controller anchoring the claim
     * @param amount exact point payout
     * @param reward one-time reward ID, or null for a repeatable task
     * @return whether the payout was recorded and all points were credited
     */
    public boolean claimTaskPoints(IGridNode target, int amount, @Nullable ResourceLocation reward) {
        var current = purchases();
        if (amount > current.maximum() - current.total()) {
            return false;
        }
        if (amount > 0) {
            return awardPoints(amount, reward) == amount;
        }
        if (reward != null) {
            var controller = researchController(target);
            if (!claimedRewards.computeIfAbsent(controller, ignored -> new HashSet<>()).add(reward)) {
                return false;
            }
            savePurchases(controller, current);
        }
        return true;
    }

    /**
     * @param target selected network controller
     * @param task   stable task ID
     * @return partial payout, or null when no delivery has been started
     */
    public @Nullable ResearchRewardDelivery taskDelivery(IGridNode target, ResourceLocation task) {
        return taskDeliveries.getOrDefault(researchController(target), Map.of()).get(task);
    }

    /**
     * Updates the controller's saved payout after an insertion.
     *
     * @param target   selected network controller
     * @param task     stable task ID
     * @param delivery outstanding reward, or null to clear it after the task pays out
     */
    public void setTaskDelivery(IGridNode target, ResourceLocation task, @Nullable ResearchRewardDelivery delivery) {
        var controller = researchController(target);
        if (delivery != null) {
            taskDeliveries.computeIfAbsent(controller, ignored -> new HashMap<>()).put(task, delivery);
        } else {
            var pending = taskDeliveries.get(controller);
            if (pending == null || pending.remove(task) == null) {
                return;
            }
            if (pending.isEmpty()) {
                taskDeliveries.remove(controller);
            }
        }
        savePurchases(controller, purchases());
    }

    /**
     * Gets the saved work for a research task.
     *
     * @param target controller in the selected grid
     * @param task   task ID
     * @return credited jobs or items, zero when no work has been saved
     */
    public int taskProgress(IGridNode target, ResourceLocation task) {
        return taskProgress.getOrDefault(researchController(target), Map.of()).getOrDefault(task, 0);
    }

    /**
     * Saves the credited work against the active research profile.
     *
     * @param target   controller in the selected grid
     * @param task     task ID
     * @param progress new credited job or item count, or zero to clear it
     */
    public void setTaskProgress(IGridNode target, ResourceLocation task, int progress) {
        var controller = researchController(target);
        if (progress == 0) {
            var progressByTask = taskProgress.get(controller);
            if (progressByTask == null || progressByTask.remove(task) == null) {
                return;
            }
            if (progressByTask.isEmpty()) {
                taskProgress.remove(controller);
            }
        } else {
            taskProgress.computeIfAbsent(controller, ignored -> new HashMap<>()).put(task, progress);
        }
        savePurchases(controller, purchases());
    }

    /**
     * Reads the saved offers for a tier from the active controller profile.
     *
     * @param target controller in the selected grid
     * @param tier   configured tier ID
     * @return saved board or the empty initial state
     */
    public ResearchTaskBoard taskBoard(IGridNode target, int tier) {
        return taskBoards.getOrDefault(researchController(target), Map.of()).getOrDefault(tier, ResearchTaskBoard.EMPTY);
    }

    /**
     * Persists the current offers with the same profile as research points and progress.
     *
     * @param target controller in the selected grid
     * @param tier   configured tier ID
     * @param board  new board and request token
     */
    public void setTaskBoard(IGridNode target, int tier, ResearchTaskBoard board) {
        var controller = researchController(target);
        var boards = taskBoards.computeIfAbsent(controller, ignored -> new HashMap<>());
        boards.keySet().removeIf(id -> ResearchTaskConfig.state().document().tiers().stream().noneMatch(rule -> rule.id() == id));
        boards.put(tier, board);
        savePurchases(controller, purchases());
    }

    /**
     * Sets the free point balance of the selected grid's active research profile.
     *
     * @param target controller selected by the operator
     * @param amount requested free research points, clamped to the remaining capacity
     * @return free research points after the change
     */
    public int setRemainingPoints(IGridNode target, int amount) {
        var current = purchases();
        var updated = current.withRemaining(amount);
        if (updated != current) {
            savePurchases(researchController(target), updated);
        }
        return updated.remaining();
    }

    /**
     * Enables the entire configured tree for the selected grid's active research profile.
     *
     * @param target controller selected by the operator
     * @return number of newly activated upgrades
     */
    public int unlockAll(IGridNode target) {
        var current = purchases();
        var unlocked = current.unlockAll();
        if (unlocked == current) {
            return 0;
        }
        savePurchases(researchController(target), unlocked);
        return catalog.ordered().size() - current.ids().size();
    }

    /**
     * Validates and buys one node on the server.
     *
     * @param target controller selected by the operator
     * @param node   requested upgrade
     * @return availability immediately before the purchase
     */
    public Availability unlock(IGridNode target, UpgradeNode node) {
        synchronizeCatalog();
        var configured = catalog.node(node.id());
        if (configured == null) {
            return Availability.UNKNOWN;
        }
        var availability = purchases.availability(configured);
        if (availability == Availability.AVAILABLE) {
            savePurchases(researchController(target), purchases.purchase(configured));
        }
        return availability;
    }

    public boolean refund(IGridNode target, int nodeId, List<Integer> removedIds, int returned) {
        synchronizeCatalog();
        var refund = purchases.refund(nodeId);
        if (refund.removedIds().isEmpty() || !refund.removedIds().equals(removedIds) || refund.returned() != returned) {
            return false;
        }
        savePurchases(researchController(target), refund.plan());
        return true;
    }

    /**
     * Resets the whole tree from trusted server code, applying the configured refund.
     *
     * @param target controller selected by the operator
     */
    public void reset(IGridNode target) {
        resetAll(target);
    }

    /**
     * Resets the whole tree and reports the configured refund on paid research.
     *
     * @param target controller selected by the operator
     * @return reset allocation and actual refund
     */
    public UpgradePlan.Refund resetAll(IGridNode target) {
        var refund = purchases().refund(UpgradeNode.ROOT);
        if (!refund.removedIds().isEmpty()) {
            savePurchases(researchController(target), refund.plan());
        }
        return refund;
    }

    /**
     * Transfers the removed block's profile, or calculates the drop if it was the last block.
     *
     * @param controller block entity being removed
     * @param successor  surviving controller block in the same structure, or null for the last block
     * @return certificate count to drop at the removed block
     */
    static int removeControllerProfile(ControllerBlockEntity controller, @Nullable ControllerBlockEntity successor) {
        var catalog = UpgradeTreeConfig.current().catalog();
        var source = readControllerData(controller);
        var plan = loadPlan(source, catalog);
        if (plan == null) {
            return 0;
        }
        var node = controller.getMainNode().getNode();
        if (node != null) {
            node.getGrid().getService(UpgradeTreeService.class).clearProfile(node);
        }
        int carried = Math.max(0, source.getInt(CARRIED_POINTS_TAG));
        if (successor == null) {
            return plan.recoveredCertificates(carried);
        }

        var destination = readControllerData(successor);
        var existing = loadPlan(destination, catalog);
        if (existing != null) {
            // Keep the earlier profile under the normal controller ordering, and defer the other profile's points.
            boolean keepSource = controller.getBlockPos().compareTo(successor.getBlockPos()) < 0;
            var displaced = keepSource ? existing : plan;
            carried = (int) Math.min((long) carried + Math.max(0, destination.getInt(CARRIED_POINTS_TAG))
                    + displaced.earnedPoints(), Integer.MAX_VALUE);
            if (!keepSource) {
                source = destination;
            }
        }
        source.putInt(CARRIED_POINTS_TAG, carried);
        var successorNode = successor.getMainNode().getNode();
        if (successorNode == null) {
            Platform.setPendingProfile(successor, source);
        } else {
            var service = successorNode.getGrid().getService(UpgradeTreeService.class);
            service.clearProfile(successorNode);
            service.loadProfile(successorNode, source);
            service.updateActivePurchases();
        }
        successor.setChanged();
        return 0;
    }

    /**
     * @return current node data or the saved data of a controller that has yet to attach to AE2
     */
    private static CompoundTag readControllerData(ControllerBlockEntity controller) {
        var node = controller.getMainNode().getNode();
        var tag = new CompoundTag();
        if (node != null) {
            node.getGrid().getService(UpgradeTreeService.class).saveNodeData(node, tag);
            return tag;
        }
        var pending = Platform.pendingProfile(controller);
        if (pending != null) {
            return pending.copy();
        }
        controller.getMainNode().saveToNBT(tag);
        return tag.getCompound(MAIN_NODE_TAG).copy();
    }

    /**
     * Updates and dirties the controller that governs the tree.
     *
     * @param controller controller that holds this network's research profile
     * @param plan       validated new allocation
     */
    private void savePurchases(IGridNode controller, UpgradePlan plan) {
        controllers.put(controller, plan);
        purchases = plan;
        ((ControllerBlockEntity) controller.getOwner()).setChanged();
    }

    private IGridNode researchController(IGridNode fallback) {
        if (!controllers.isEmpty()) {
            return controllers.firstKey();
        }
        var first = firstController();
        return first == null ? fallback : first;
    }

    private @Nullable IGridNode firstController() {
        IGridNode first = null;
        for (var node : grid.getMachineNodes(ControllerBlockEntity.class)) {
            if (first == null || CONTROLLER_ORDER.compare(node, first) < 0) {
                first = node;
            }
        }
        return first;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addNode(IGridNode node, @Nullable CompoundTag savedData) {
        synchronizeCatalog();
        if (!(node.getOwner() instanceof ControllerBlockEntity controller)) {
            return;
        }
        controllerCount++;
        var pending = Platform.pendingProfile(controller);
        if (pending != null) {
            savedData = pending;
            Platform.setPendingProfile(controller, null);
            controller.setChanged();
        }
        if (savedData != null) {
            loadProfile(node, savedData);
            updateActivePurchases();
        }
    }

    /**
     * Loads the purchase and task data for a controller that does not have a profile in this grid yet.
     *
     * @param node      controller's live AE2 node
     * @param savedData research fields from node storage or a transferred profile
     */
    private void loadProfile(IGridNode node, CompoundTag savedData) {
        var plan = loadPlan(savedData, catalog);
        if (plan == null) {
            return;
        }
        controllers.put(node, plan);
        int carried = savedData.getInt(CARRIED_POINTS_TAG);
        if (carried > 0) {
            carriedPoints.put(node, carried);
        }
        var rewards = new HashSet<ResourceLocation>();
        var savedRewards = savedData.getList(CLAIMS_TAG, Tag.TAG_STRING);
        for (int i = 0; i < savedRewards.size(); i++) {
            var reward = ResourceLocation.tryParse(savedRewards.getString(i));
            if (reward != null) {
                rewards.add(reward);
            }
        }
        if (!rewards.isEmpty()) {
            claimedRewards.put(node, rewards);
        }
        var savedTasks = savedData.getCompound(TASKS_TAG);
        if (!savedTasks.isEmpty()) {
            var progressByTask = new HashMap<ResourceLocation, Integer>();
            for (var id : savedTasks.getAllKeys()) {
                var task = ResourceLocation.tryParse(id);
                int progress = savedTasks.getInt(id);
                if (task != null && progress > 0) {
                    progressByTask.put(task, Math.min(progress, ResearchTask.MAX_AMOUNT));
                }
            }
            if (!progressByTask.isEmpty()) {
                taskProgress.put(node, progressByTask);
            }
        }
        var boards = readTaskBoards(savedData.getList(TASK_BOARDS_TAG, Tag.TAG_COMPOUND));
        if (!boards.isEmpty()) {
            taskBoards.put(node, boards);
        }
        var deliveries = readTaskDeliveries(savedData.getCompound(TASK_DELIVERIES_TAG));
        if (!deliveries.isEmpty()) {
            taskDeliveries.put(node, deliveries);
        }
    }

    private static @Nullable UpgradePlan loadPlan(CompoundTag data, UpgradeCatalog catalog) {
        if (data.contains(POINTS_TAG, Tag.TAG_INT)) {
            int startingAllowance = data.contains(STARTING_ALLOWANCE_TAG, Tag.TAG_INT)
                    ? data.getInt(STARTING_ALLOWANCE_TAG) : catalog.definition().initialPoints();
            var plan = UpgradePlan.restore(data.getIntArray(PURCHASES_TAG), catalog, data.getInt(POINTS_TAG), startingAllowance);
            return data.getBoolean(ALL_UNLOCKED_TAG) ? plan.unlockAll() : plan;
        }
        if (data.contains(PURCHASES_TAG, Tag.TAG_INT_ARRAY)) {
            return UpgradePlan.migrate(UpgradePlan.restore(data.getIntArray(PURCHASES_TAG), catalog));
        }
        if (data.contains(LEGACY_PURCHASES_TAG, Tag.TAG_INT)) {
            return UpgradePlan.migrate(UpgradePlan.fromLegacyMask(data.getInt(LEGACY_PURCHASES_TAG), catalog));
        }
        return null;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeNode(IGridNode node) {
        if (node.getOwner() instanceof ControllerBlockEntity) {
            controllerCount--;
            clearProfile(node);
        }
    }

    /**
     * @param node controller whose purchase, task and carried point state is being removed
     */
    private void clearProfile(IGridNode node) {
        claimedRewards.remove(node);
        taskProgress.remove(node);
        taskBoards.remove(node);
        taskDeliveries.remove(node);
        carriedPoints.remove(node);
        if (controllers.remove(node) != null) {
            updateActivePurchases();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveNodeData(IGridNode node, CompoundTag savedData) {
        synchronizeCatalog();
        if (node.getOwner() instanceof ControllerBlockEntity) {
            savedData.remove(TASKS_TAG);
            savedData.remove(TASK_BOARDS_TAG);
            savedData.remove(TASK_DELIVERIES_TAG);
            savedData.remove(CARRIED_POINTS_TAG);
            var plan = controllers.get(node);
            if (plan != null) {
                var carried = carriedPoints.get(node);
                if (carried != null) {
                    savedData.putInt(CARRIED_POINTS_TAG, carried);
                }
                savedData.putIntArray(PURCHASES_TAG, plan.savedIds());
                savedData.putInt(POINTS_TAG, plan.total());
                savedData.putInt(STARTING_ALLOWANCE_TAG, plan.startingAllowance());
                if (plan.allUnlocked()) {
                    savedData.putBoolean(ALL_UNLOCKED_TAG, true);
                } else {
                    savedData.remove(ALL_UNLOCKED_TAG);
                }
                var rewards = claimedRewards.get(node);
                if (rewards != null && !rewards.isEmpty()) {
                    var savedRewards = new ListTag();
                    rewards.stream().sorted().forEach(id -> savedRewards.add(StringTag.valueOf(id.toString())));
                    savedData.put(CLAIMS_TAG, savedRewards);
                }
                var progressByTask = taskProgress.get(node);
                if (progressByTask != null) {
                    var savedTasks = new CompoundTag();
                    progressByTask.forEach((task, progress) -> savedTasks.putInt(task.toString(), progress));
                    savedData.put(TASKS_TAG, savedTasks);
                }
                var boards = taskBoards.get(node);
                if (boards != null && !boards.isEmpty()) {
                    savedData.put(TASK_BOARDS_TAG, writeTaskBoards(boards));
                }
                var deliveries = taskDeliveries.get(node);
                if (deliveries != null && !deliveries.isEmpty()) {
                    savedData.put(TASK_DELIVERIES_TAG, writeTaskDeliveries(deliveries));
                }
            } else {
                savedData.remove(PURCHASES_TAG);
                savedData.remove(LEGACY_PURCHASES_TAG);
                savedData.remove(POINTS_TAG);
                savedData.remove(STARTING_ALLOWANCE_TAG);
                savedData.remove(CLAIMS_TAG);
                savedData.remove(ALL_UNLOCKED_TAG);
            }
        }
    }

    /**
     * Decodes unfinished payouts from the controller save.
     *
     * @param saved stored deliveries by task ID
     * @return mutable per-controller map of unfinished payouts
     */
    private static Map<ResourceLocation, ResearchRewardDelivery> readTaskDeliveries(CompoundTag saved) {
        var deliveries = new HashMap<ResourceLocation, ResearchRewardDelivery>();
        for (var id : saved.getAllKeys()) {
            var task = id.length() <= ResearchTask.MAX_ID ? ResourceLocation.tryParse(id) : null;
            if (task == null) {
                continue;
            }
            var entry = saved.getCompound(id);
            int points = Math.clamp(entry.getInt("points"), 0, UpgradeCatalog.MAX_POINTS);
            var items = entry.getList("remaining", Tag.TAG_COMPOUND);
            var remaining = new ArrayList<ResearchItemReward>();
            for (int i = 0; i < Math.min(items.size(), ResearchTask.MAX_ITEM_REWARDS); i++) {
                var stack = items.getCompound(i);
                String item = stack.getString("item");
                int amount = stack.getInt("amount");
                if (item.length() <= ResearchTask.MAX_ID && ResourceLocation.tryParse(item) != null && amount > 0) {
                    remaining.add(new ResearchItemReward(item, Math.min(amount, ResearchTask.MAX_AMOUNT)));
                }
            }
            deliveries.put(task, new ResearchRewardDelivery(points, remaining));
        }
        return deliveries;
    }

    /**
     * Encodes outstanding quantities so a retry cannot reissue already stored items.
     *
     * @param deliveries payouts by stable task ID
     * @return NBT for the controller profile
     */
    private static CompoundTag writeTaskDeliveries(Map<ResourceLocation, ResearchRewardDelivery> deliveries) {
        var saved = new CompoundTag();
        deliveries.forEach((task, delivery) -> {
            var entry = new CompoundTag();
            entry.putInt("points", delivery.points());
            var items = new ListTag();
            for (var reward : delivery.remaining()) {
                var stack = new CompoundTag();
                stack.putString("item", reward.item());
                stack.putInt("amount", reward.amount());
                items.add(stack);
            }
            entry.put("remaining", items);
            saved.put(task.toString(), entry);
        });
        return saved;
    }

    /**
     * Restores valid, distinct offer IDs and their completed flags from controller data.
     *
     * @param saved serialized boards, one compound per tier
     * @return mutable board map for the controller profile
     */
    private static Map<Integer, ResearchTaskBoard> readTaskBoards(ListTag saved) {
        var boards = new HashMap<Integer, ResearchTaskBoard>();
        for (int i = 0; i < Math.min(saved.size(), ResearchTaskConfig.MAX_TIERS); i++) {
            var entry = saved.getCompound(i);
            int tier = entry.getInt("tier");
            if (tier < 0 || tier > ResearchTier.MAX_ID || boards.containsKey(tier)) {
                continue;
            }
            var slots = entry.getList("offers", Tag.TAG_COMPOUND);
            var offers = new LinkedHashSet<String>();
            var completed = new HashSet<String>();
            for (int j = 0; j < Math.min(slots.size(), ResearchTaskConfig.MAX_TASKS); j++) {
                var slot = slots.getCompound(j);
                var id = slot.getString("id");
                if (id.length() <= ResearchTask.MAX_ID && id.indexOf(':') > 0 && ResourceLocation.tryParse(id) != null
                        && offers.add(id) && slot.getBoolean("completed")) {
                    completed.add(id);
                }
            }
            boards.put(tier, new ResearchTaskBoard(entry.getLong("token"), List.copyOf(offers), completed));
        }
        return boards;
    }

    /**
     * Serializes saved offers in display order and keeps claims that are waiting for a reroll.
     *
     * @param boards saved board state by tier ID
     * @return NBT accepted by AE2's controller node save
     */
    private static ListTag writeTaskBoards(Map<Integer, ResearchTaskBoard> boards) {
        var saved = new ListTag();
        boards.forEach((tier, board) -> {
            var entry = new CompoundTag();
            entry.putInt("tier", tier);
            entry.putLong("token", board.token());
            var offers = new ListTag();
            for (var id : board.offers()) {
                var slot = new CompoundTag();
                slot.putString("id", id);
                if (board.completed().contains(id)) {
                    slot.putBoolean("completed", true);
                }
                offers.add(slot);
            }
            entry.put("offers", offers);
            saved.add(entry);
        });
        return saved;
    }

    /**
     * Refreshes only AE2 caches affected by a different tree after purchases or topology changes.
     */
    @Override
    public void onServerStartTick() {
        var oldEffects = published;
        var newEffects = effects();
        if (oldEffects == newEffects) {
            return;
        }
        published = newEffects;
        if (newEffects.changed(oldEffects, Kind.CHANNELS)) {
            grid.getService(ChannelCapacityService.class).refreshCableCapacity();
        }
        if (newEffects.changed(oldEffects, Kind.CHANNEL_COST)) {
            grid.getPathingService().repath();
        }
        if (newEffects.changed(oldEffects, Kind.PATTERN_SLOTS)) {
            grid.getService(PatternSlotService.class).refreshPatterns();
        }
        if (newEffects.changed(oldEffects, Kind.INTERFACE_SLOTS)) {
            grid.getService(InterfaceSlotService.class).refreshCapacity();
        }
        if (newEffects.changed(oldEffects, Kind.IDLE_PERCENT) || newEffects.changed(oldEffects, Kind.IDLE_FLAT)) {
            grid.getService(DeviceTuningService.class).refreshIdlePower();
        }
        if (newEffects.changed(oldEffects, Kind.DEVICE_LIMIT) || newEffects.changed(oldEffects, Kind.DEVICE_CAP)) {
            grid.getService(DeviceLimitService.class).refreshLimits();
        }
        if (newEffects.customChanged(oldEffects)) {
            Platform.effectsChanged(grid, oldEffects, newEffects);
        }
    }

    /**
     * Revalidates saved purchases against a newly published server definition.
     */
    private void synchronizeCatalog() {
        var current = UpgradeTreeConfig.current().catalog();
        if (catalog == current) {
            return;
        }
        catalog = current;
        controllers.replaceAll((node, plan) -> {
            var updated = plan.update(current);
            if (updated.total() != plan.total() || !updated.sameUnlocks(plan)) {
                ((ControllerBlockEntity) node.getOwner()).setChanged();
            }
            return updated;
        });
        updateActivePurchases();
    }

    private void updateActivePurchases() {
        purchases = controllers.isEmpty() ? UpgradePlan.empty(catalog) : controllers.firstEntry().getValue();
    }
}
