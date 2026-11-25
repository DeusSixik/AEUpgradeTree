package dev.sixik.aeupgrade.tree.research;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

import appeng.api.config.Actionable;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ControllerState;
import appeng.api.networking.security.IActionSource;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.GenericStack;
import appeng.api.storage.IStorageProvider;
import appeng.blockentity.networking.ControllerBlockEntity;
import dev.sixik.aeupgrade.AEUpgradeTree;
import dev.sixik.aeupgrade.platform.Platform;
import dev.sixik.aeupgrade.tree.UpgradeTreeService;
import dev.sixik.aeupgrade.tree.research.ResearchTaskPackets.Board;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Records task work and pays completed tasks on the server.
 */
public final class ResearchTasks {
    /**
     * Result of pressing a task's server-validated action button.
     */
    public enum Result {
        /**
         * Items were accepted and counted towards the goal.
         */
        DONATED,
        /**
         * The research reward was credited.
         */
        CLAIMED,
        /**
         * No eligible item could be taken from storage.
         */
        NO_ITEMS,
        /**
         * The goal is complete but the exact reward does not fit under the point cap.
         */
        POINTS_FULL,
        /**
         * No online storage can accept items because the grid is unpowered or still booting.
         */
        STORAGE_OFFLINE,
        /**
         * Every detected storage provider lacks an allocated channel.
         */
        STORAGE_NO_CHANNEL,
        /**
         * No storage with room accepts the outstanding item reward.
         */
        STORAGE_FULL,
        /**
         * Some items arrived, with the remainder waiting for storage space.
         */
        STORAGE_PARTIAL,
        /**
         * An outstanding item reward is absent from the current item registry.
         */
        STORAGE_ITEM_MISSING,
        /**
         * The task has not yet met the goal.
         */
        INCOMPLETE,
        /**
         * The task is stale, disabled or has already paid out.
         */
        UNAVAILABLE
    }

    /**
     * Keeps task operations static.
     */
    private ResearchTasks() {
    }

    /**
     * Loads the server's task definitions after the item registry is available.
     */
    public static void load() {
        ResearchTaskConfig.load(Platform.configDirectory(), id -> itemKey(id) != null);
    }

    /**
     * Clears the current definitions when the server closes.
     */
    public static void unload() {
        ResearchTaskConfig.unload();
    }

    /**
     * Saves a task config using the server's item registry to validate every active item filter.
     *
     * @param json     submitted task config
     * @param revision revision captured by the operator
     * @return committed config, or null if the editor is stale
     * @throws IllegalArgumentException for invalid settings
     * @throws IOException              if the config write fails
     */
    public static @Nullable ResearchTaskConfig.State save(String json, long revision) throws IOException {
        return ResearchTaskConfig.save(Platform.configDirectory(), json, revision, id -> itemKey(id) != null);
    }

    /**
     * Credits a successfully finished job to matching tasks on the CPU's live AE2 network.
     *
     * @param grid   grid containing the CPU, possibly absent during teardown
     * @param output original requested job output, possibly absent
     */
    public static void recordCraft(@Nullable IGrid grid, @Nullable GenericStack output) {
        var config = ResearchTaskConfig.current();
        if (config.crafting().isEmpty() || grid == null || output == null
                || grid.getPathingService().getControllerState() != ControllerState.CONTROLLER_ONLINE) {
            return;
        }
        var target = grid.getMachineNodes(ControllerBlockEntity.class).iterator();
        if (!target.hasNext()) {
            return;
        }
        var controller = target.next();
        if (controller.getLevel().getServer() == null || !controller.getLevel().getServer().isSameThread()) {
            return;
        }
        var research = grid.getService(UpgradeTreeService.class);
        var tier = config.tier(research.purchases().total());
        var board = currentBoard(controller, research, config, tier);
        String outputItem = output.what() instanceof AEItemKey key ? key.getId().toString() : null;
        for (String id : board.offers()) {
            if (board.completed().contains(id)) {
                continue;
            }
            var task = config.byId().get(id);
            int credit = task.craftCredit(outputItem, output.amount());
            if (credit == 0) {
                continue;
            }
            var key = ResourceLocation.parse(id);
            int current = research.taskProgress(controller, key);
            int next = ResearchTask.advance(current, credit, task.amount());
            if (next > current) {
                research.setTaskProgress(controller, key, next);
            }
        }
    }

    /**
     * Takes a batch of donation items, or claims an already completed task.
     *
     * @param player player authorized to do research for the selected network
     * @param target validated online controller the player can reach
     * @param id     stable ID of the requested task
     * @param claim  whether the player pressed the claim button instead of donating
     * @param token  request token for the displayed task board
     * @return result to display in the tree UI
     */
    public static Result act(ServerPlayer player, IGridNode target, ResourceLocation id, boolean claim, long token) {
        var config = ResearchTaskConfig.current();
        String key = id.toString();
        var task = config.byId().get(key);
        if (task == null) {
            return Result.UNAVAILABLE;
        }
        var grid = target.getGrid();
        var research = grid.getService(UpgradeTreeService.class);
        var profile = research.purchases();
        var tier = config.tier(profile.total());
        var board = currentBoard(target, research, config, tier);
        if (token != requestToken(board) || !board.offers().contains(key) || board.completed().contains(key)) {
            return Result.UNAVAILABLE;
        }
        int current = research.taskProgress(target, id);
        var pending = research.taskDelivery(target, id);
        if (claim) {
            if (pending == null && current < task.amount()) {
                return Result.INCOMPLETE;
            }
            var result = claim(player, target, research, task, id, pending);
            if (result != Result.CLAIMED) {
                return result;
            }
            research.setTaskProgress(target, id, 0);
            research.setTaskDelivery(target, id, null);
            var completed = board.complete(key, ThreadLocalRandom.current());
            research.setTaskBoard(target, tier.id(), refreshBoard(target, research, config, tier, completed));
            return Result.CLAIMED;
        }
        if (task.type() != ResearchTask.Type.DONATE_ITEMS || pending != null || current >= task.amount()) {
            return Result.UNAVAILABLE;
        }
        var item = itemKey(task.item());
        if (item == null) {
            return Result.UNAVAILABLE;
        }
        long taken = grid.getStorageService().getInventory().extract(item, task.amount() - current,
                Actionable.MODULATE, IActionSource.ofPlayer(player));
        if (taken <= 0) {
            return Result.NO_ITEMS;
        }
        research.setTaskProgress(target, id, ResearchTask.advance(current, taken, task.amount()));
        return Result.DONATED;
    }

    /**
     * Delivers configured items through actual storage mounts and then grants points.
     *
     * @param player   authenticated claimer
     * @param target   controller supplying the destination network
     * @param research active network research service
     * @param task     completed task definition
     * @param id       stable task ID
     * @param saved    previous partial delivery, or null for the initial claim
     * @return delivery result, retaining any outstanding items for a retry
     */
    private static Result claim(ServerPlayer player, IGridNode target, UpgradeTreeService research, ResearchTask task,
                                ResourceLocation id, @Nullable ResearchRewardDelivery saved) {
        var delivery = saved == null ? new ResearchRewardDelivery(task.points(), task.itemRewards()) : saved;
        var profile = research.purchases();
        if (profile.maximum() - profile.total() < delivery.points()) {
            return Result.POINTS_FULL;
        }
        if (!delivery.remaining().isEmpty()) {
            var grid = target.getGrid();
            if (!grid.getEnergyService().isNetworkPowered() || !target.hasGridBooted()) {
                return Result.STORAGE_OFFLINE;
            }
            var items = new HashMap<String, AEItemKey>();
            for (var reward : delivery.remaining()) {
                var key = itemKey(reward.item());
                if (key == null) {
                    return Result.STORAGE_ITEM_MISSING;
                }
                items.put(reward.item(), key);
            }
            var storage = grid.getStorageService().getInventory();
            var source = new ResearchStorageSource(player);
            var delivered = delivery.deposit(
                    reward -> (int) storage.insert(items.get(reward.item()), reward.amount(), Actionable.SIMULATE, source),
                    reward -> (int) storage.insert(items.get(reward.item()), reward.amount(), Actionable.MODULATE, source),
                    remaining -> research.setTaskDelivery(target, id, remaining));
            if (delivered != delivery && !delivered.remaining().isEmpty()) {
                return Result.STORAGE_PARTIAL;
            }
            if (!delivered.remaining().isEmpty()) {
                return storageUnavailable(grid);
            }
        }
        var reward = task.repeatable() ? null : rewardKey(id);
        return research.claimTaskPoints(target, delivery.points(), reward) ? Result.CLAIMED : Result.UNAVAILABLE;
    }

    /**
     * Diagnoses an insertion rejected by ME storage using the state of the grid's providers.
     *
     * @param grid receiving AE2 network
     * @return missing channels when all detected providers lack them, otherwise a storage-capacity message
     */
    private static Result storageUnavailable(IGrid grid) {
        boolean noChannel = false;
        for (var node : grid.getNodes()) {
            if (node.getService(IStorageProvider.class) == null) {
                continue;
            }
            if (node.isOnline()) {
                return Result.STORAGE_FULL;
            }
            noChannel |= !node.meetsChannelRequirements();
        }
        return noChannel ? Result.STORAGE_NO_CHANNEL : Result.STORAGE_FULL;
    }

    /**
     * Builds the current task list for an authorized client.
     *
     * @param target   selected online controller
     * @param location selected controller's location on the wire
     * @param message  localized feedback from this action, or empty
     * @param action   whether this acknowledges a donation or claim
     * @return current offers, reroll state and feedback
     */
    public static Board status(IGridNode target, GlobalPos location, String message, boolean action) {
        var config = ResearchTaskConfig.current();
        var service = target.getGrid().getService(UpgradeTreeService.class);
        var profile = service.purchases();
        var tier = config.tier(profile.total());
        if (config.tasks().isEmpty()) {
            return new Board(location, List.of(), message, action, tier.id(), 0, 0, 0);
        }
        var board = currentBoard(target, service, config, tier);
        var statuses = board.offers().stream().map(key -> {
            var task = config.byId().get(key);
            var id = ResourceLocation.parse(key);
            boolean claimed = board.completed().contains(key);
            var pending = service.taskDelivery(target, id);
            int points = pending == null ? task.points() : pending.points();
            int progress = claimed || pending != null ? task.amount() : Math.min(task.amount(), service.taskProgress(target, id));
            return new ResearchTaskStatus(task.id(), task.type(), task.item(), task.title(), task.description(),
                    task.amount(), points, progress, claimed, profile.total() + points <= profile.maximum(),
                    pending == null ? task.itemRewards() : pending.remaining());
        }).toList();
        return new Board(location, statuses, message, action, tier.id(), requestToken(board), board.completed().size(),
                ResearchTaskBoard.threshold(tier, statuses.size()));
    }

    /**
     * Invalidates a click when the board changes or an operator edits the underlying task settings.
     *
     * @param board active saved offers for the tier
     * @return request token for the current board and config revision
     */
    private static long requestToken(ResearchTaskBoard board) {
        return board.token() ^ ResearchTaskConfig.state().revision();
    }

    /**
     * Reads or draws the network's tier board and persists changes before accepting clicks.
     *
     * @param target  controller addressing the network
     * @param service grid research service
     * @param config  enabled task definitions
     * @param tier    current tier rule
     * @return existing or refreshed offers
     */
    private static ResearchTaskBoard currentBoard(IGridNode target, UpgradeTreeService service,
                                                  ResearchTaskConfig.Active config, ResearchTier tier) {
        var existing = service.taskBoard(target, tier.id());
        var board = refreshBoard(target, service, config, tier, existing);
        if (board != existing) {
            service.setTaskBoard(target, tier.id(), board);
        }
        return board;
    }

    /**
     * Reconciles a saved board with the active tier pool and one-time reward history.
     *
     * @param target  controller addressing the network
     * @param service grid research service
     * @param config  enabled task definitions
     * @param tier    current tier rule
     * @param board   existing board to reconcile
     * @return current offers after any eligible rotation
     */
    private static ResearchTaskBoard refreshBoard(IGridNode target, UpgradeTreeService service,
                                                  ResearchTaskConfig.Active config, ResearchTier tier, ResearchTaskBoard board) {
        return board.refresh(tier, config.pools().getOrDefault(tier.id(), List.of()),
                id -> service.hasClaimedReward(target, rewardKey(ResourceLocation.parse(id))), ThreadLocalRandom.current());
    }

    /**
     * Resolves the default component variant of an item for storage donation.
     *
     * @param id validated registry ID or null for a task without an item
     * @return usable default stack key, or null
     */
    private static @Nullable AEItemKey itemKey(@Nullable String id) {
        return id == null ? null : BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id)).map(AEItemKey::of).orElse(null);
    }

    /**
     * Maps built-in research tasks into a reserved part of the one-time reward namespace.
     *
     * @param id stable namespaced task identifier
     * @return one-time reward key stored with the controller
     */
    private static ResourceLocation rewardKey(ResourceLocation id) {
        return ResourceLocation.fromNamespaceAndPath(AEUpgradeTree.MODID, "research_task/" + id.getNamespace() + "/" + id.getPath());
    }
}
