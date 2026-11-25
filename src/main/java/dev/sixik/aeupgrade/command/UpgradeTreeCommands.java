package dev.sixik.aeupgrade.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import appeng.api.networking.IGridNode;
import appeng.api.networking.pathing.ControllerState;
import appeng.blockentity.networking.ControllerBlockEntity;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.sixik.aeupgrade.platform.PlatformNetwork;
import dev.sixik.aeupgrade.tree.UpgradeNode;
import dev.sixik.aeupgrade.api.UpgradeResearchPoints;
import dev.sixik.aeupgrade.tree.UpgradeTreeConfig;
import dev.sixik.aeupgrade.tree.UpgradePlan.Availability;
import dev.sixik.aeupgrade.tree.UpgradeTreePackets;
import dev.sixik.aeupgrade.tree.UpgradeTreePackets.Action;
import dev.sixik.aeupgrade.tree.UpgradeTreePackets.Save;
import dev.sixik.aeupgrade.tree.UpgradeTreePackets.Snapshot;
import dev.sixik.aeupgrade.tree.UpgradeTreeService;
import dev.sixik.aeupgrade.tree.research.ResearchTaskConfig;
import dev.sixik.aeupgrade.tree.research.ResearchTaskPackets;
import dev.sixik.aeupgrade.tree.research.ResearchTasks;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/**
 * Opens and validates the development tree for the selected ME controller.
 */
public final class UpgradeTreeCommands {
    /**
     * Shared translation prefix for feedback from server actions.
     */
    private static final String FEEDBACK = "aeupgrade.tree.feedback.";

    /**
     * Keeps command registration and packet handling static.
     */
    private UpgradeTreeCommands() {
    }

    /**
     * @return operator commands for opening and managing the selected grid's tree
     */
    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return literal("tree").executes(context -> open(context.getSource()))
                .then(literal("points")
                        .then(literal("set").then(argument("amount", IntegerArgumentType.integer(0))
                                .executes(context -> changePoints(context.getSource(), IntegerArgumentType.getInteger(context, "amount"), false))))
                        .then(literal("add").then(argument("amount", IntegerArgumentType.integer(1))
                                .executes(context -> award(context.getSource(), IntegerArgumentType.getInteger(context, "amount")))))
                        .then(literal("remove").then(argument("amount", IntegerArgumentType.integer(1))
                                .executes(context -> changePoints(context.getSource(), IntegerArgumentType.getInteger(context, "amount"), true)))))
                .then(literal("unlockAll").executes(context -> unlockAll(context.getSource())))
                .then(literal("resetAll").executes(context -> resetAll(context.getSource())));
    }

    /**
     * Grants points up to the targeted network's ceiling.
     *
     * @param source operator looking at an online ME controller
     * @param amount requested positive number of points
     * @return number of points granted
     * @throws CommandSyntaxException if the controller target is invalid
     */
    private static int award(CommandSourceStack source, int amount) throws CommandSyntaxException {
        var grid = ChannelCommands.requireController(source).getGrid();
        int credited = UpgradeResearchPoints.award(grid, amount);
        source.sendSuccess(() -> Component.translatable(FEEDBACK + (credited == 0 ? "points_full" : "points_awarded"), credited),
                false);
        return credited;
    }

    /**
     * Sets or subtracts free points on the targeted network and reports its resulting balance.
     *
     * @param source operator looking at an online ME controller
     * @param amount requested free balance, or a positive number of points to remove
     * @param remove whether to subtract the amount from the current balance
     * @return points removed for a subtraction, or the final free balance for a set
     * @throws CommandSyntaxException if the controller target is invalid
     */
    private static int changePoints(CommandSourceStack source, int amount, boolean remove) throws CommandSyntaxException {
        var controller = ChannelCommands.requireController(source);
        var tree = controller.getGrid().getService(UpgradeTreeService.class);
        int previous = tree.purchases().remaining();
        int remaining = tree.setRemainingPoints(controller, remove ? previous - amount : amount);
        if (remove) {
            int removed = previous - remaining;
            source.sendSuccess(() -> Component.translatable(FEEDBACK + "points_removed", removed, remaining), false);
            return removed;
        }
        source.sendSuccess(() -> Component.translatable(FEEDBACK + "points_set", remaining), false);
        return remaining;
    }

    /**
     * Activates every node on the targeted network.
     *
     * @param source operator looking at an online ME controller
     * @return number of newly activated nodes
     * @throws CommandSyntaxException if the controller target is invalid
     */
    private static int unlockAll(CommandSourceStack source) throws CommandSyntaxException {
        var controller = ChannelCommands.requireController(source);
        int unlocked = controller.getGrid().getService(UpgradeTreeService.class).unlockAll(controller);
        source.sendSuccess(() -> Component.translatable(FEEDBACK + "unlocked_all", unlocked), false);
        return unlocked;
    }

    /**
     * Resets the targeted network's tree and applies the configured refund.
     *
     * @param source operator looking at an online ME controller
     * @return number of removed upgrades
     * @throws CommandSyntaxException if the controller target is invalid
     */
    private static int resetAll(CommandSourceStack source) throws CommandSyntaxException {
        var controller = ChannelCommands.requireController(source);
        var refund = controller.getGrid().getService(UpgradeTreeService.class).resetAll(controller);
        int removed = refund.removedIds().size();
        source.sendSuccess(() -> Component.translatable(FEEDBACK + "reset_all", removed, refund.returned(),
                refund.plan().remaining()), false);
        return removed;
    }

    /**
     * Publishes the selected controller's tree to the client.
     *
     * @param source operator looking at an online ME controller
     * @return one after opening the screen
     * @throws CommandSyntaxException if the operator is not targeting a valid controller
     */
    private static int open(CommandSourceStack source) throws CommandSyntaxException {
        sendOpen(source.getPlayerOrException(), ChannelCommands.requireController(source));
        return 1;
    }

    /**
     * Opens the tree for the controller supplied by the player's active AE2 menu.
     *
     * @param player     operator who clicked the menu button
     * @param controller live block entity backing the active menu
     */
    public static void openFromMenu(ServerPlayer player, ControllerBlockEntity controller) {
        var node = reachableController(player, GlobalPos.of(player.serverLevel().dimension(), controller.getBlockPos()));
        if (node == null || node.getOwner() != controller) {
            player.displayClientMessage(Component.translatable(FEEDBACK + "target"), true);
            return;
        }
        var denial = node.getGrid().getService(UpgradeTreeService.class).researchDenial(player, node);
        if (denial != null) {
            player.displayClientMessage(Component.translatable(FEEDBACK + denial), true);
            return;
        }
        player.closeContainer();
        sendOpen(player, node);
    }

    /**
     * Publishes the state for a controller already validated by the server.
     *
     * @param player     recipient of the open packet
     * @param controller online AE2 controller node
     */
    private static void sendOpen(ServerPlayer player, IGridNode controller) {
        var tree = controller.getGrid().getService(UpgradeTreeService.class);
        var pos = ((ControllerBlockEntity) controller.getOwner()).getBlockPos();
        var location = GlobalPos.of(controller.getLevel().dimension(), pos);
        publish(player, location, tree, true, true, "");
    }

    /**
     * Applies an operator action while the controller shown in the tree is still within reach.
     *
     * @param player sender supplied by the server's network context
     * @param action requested node or respec with the original controller location
     */
    public static void handle(ServerPlayer player, Action action) {
        boolean refresh = action.nodeId() == UpgradeTreePackets.REFRESH && action.refund().isEmpty();
        var controller = reachableController(player, action.controller());
        if (controller == null) {
            if (!refresh) {
                reject(player, action.controller(), "target", "");
            }
            return;
        }

        var tree = controller.getGrid().getService(UpgradeTreeService.class);
        var denial = tree.researchDenial(player, controller);
        if (denial != null) {
            reject(player, action.controller(), denial, "", refresh);
            return;
        }
        var state = UpgradeTreeConfig.current();
        if (refresh) {
            publish(player, action.controller(), tree, false, action.revision() != state.revision(), "", true);
            return;
        }
        if (action.revision() != state.revision()) {
            publish(player, action.controller(), tree, false, true, FEEDBACK + "editor_stale");
            return;
        }
        String message;
        if (action.refund().isPresent()) {
            var refund = action.refund().orElseThrow();
            message = FEEDBACK + (tree.refund(controller, action.nodeId(), refund.removedIds(), refund.returned())
                    ? "refunded\n" + refund.returned() : "refund_changed");
        } else {
            var node = state.catalog().node(action.nodeId());
            if (node == null) {
                message = FEEDBACK + "unknown";
            } else {
                var purchases = tree.purchases();
                if (purchases.availability(node) == Availability.AVAILABLE
                        && !purchases.newlyClosedBranches(node).stream().map(UpgradeNode::id).toList()
                        .equals(action.confirmedBranches())) {
                    publish(player, action.controller(), tree, false, false, FEEDBACK + "branches_changed");
                    return;
                }
                var availability = tree.unlock(controller, node);
                message = FEEDBACK + (availability == Availability.AVAILABLE
                        ? state.catalog().entry(node.id()).effects().isEmpty() ? "prototype" : "purchased"
                        : availability.name().toLowerCase(Locale.ROOT));
            }
        }
        publish(player, action.controller(), tree, false, false, message);
    }

    /**
     * Resolves task actions against the authenticated player, live controller and server task definition.
     *
     * @param player  authenticated player issuing the request
     * @param request selected controller, task and operation
     */
    public static void handleTask(ServerPlayer player, ResearchTaskPackets.Request request) {
        var controller = reachableController(player, request.controller());
        boolean view = request.operation() == ResearchTaskPackets.Operation.VIEW;
        var denial = controller == null ? "target"
                : controller.getGrid().getService(UpgradeTreeService.class).researchDenial(player, controller);
        if (denial != null) {
            PlatformNetwork.sendToPlayer(player, new ResearchTaskPackets.Board(request.controller(), List.of(),
                    FEEDBACK + denial, !view, 0, 0, 0, 0));
            return;
        }
        String message = "";
        if (!view) {
            var result = ResearchTasks.act(player, controller, request.task(), request.operation() == ResearchTaskPackets.Operation.CLAIM,
                    request.token());
            message = "aeupgrade.tree.tasks.feedback." + result.name().toLowerCase(Locale.ROOT);
        }
        PlatformNetwork.sendToPlayer(player, ResearchTasks.status(controller, request.controller(), message, !view));
    }

    /**
     * Reads or saves the shared task config for an operator targeting a reachable controller.
     *
     * @param player  authenticated player submitting the request
     * @param request requested anchor and optional replacement data
     */
    public static void editTasks(ServerPlayer player, ResearchTaskPackets.EditRequest request) {
        ResearchTaskConfig.State result = null;
        String message = "";
        if (!player.hasPermissions(2)) {
            message = FEEDBACK + "operator";
        } else if (reachableController(player, request.controller()) == null) {
            message = FEEDBACK + "target";
        } else if (request.document().isEmpty()) {
            result = ResearchTaskConfig.state();
        } else {
            try {
                result = ResearchTasks.save(request.document(), request.revision());
                message = "aeupgrade.tree.editor.tasks." + (result == null ? "stale" : "saved");
            } catch (IllegalArgumentException failure) {
                var reason = failure.getMessage();
                message = "aeupgrade.tree.editor.tasks.invalid"
                        + (reason == null ? "" : "\n" + reason.substring(0, Math.min(480, reason.length())));
            } catch (IOException failure) {
                com.mojang.logging.LogUtils.getLogger().error("Could not save the AEUpgradeTree research tasks", failure);
                message = "aeupgrade.tree.editor.tasks.io";
            }
        }
        PlatformNetwork.sendToPlayer(player, new ResearchTaskPackets.EditResponse(request.controller(),
                result == null ? 0 : result.revision(), result == null ? "" : ResearchTaskConfig.encode(result.document()), message));
    }

    /**
     * Accepts a definition only from an operator near the original controller.
     *
     * @param player  authenticated player sending the editor draft
     * @param request revision and proposed definition
     */
    public static void save(ServerPlayer player, Save request) {
        if (!player.hasPermissions(2)) {
            reject(player, request.controller(), "operator", "");
            return;
        }
        var controller = reachableController(player, request.controller());
        if (controller == null) {
            reject(player, request.controller(), "target", "");
            return;
        }
        var tree = controller.getGrid().getService(UpgradeTreeService.class);
        try {
            var saved = UpgradeTreeConfig.save(request.document(), request.revision());
            publish(player, request.controller(), tree, false, true,
                    FEEDBACK + (saved == null ? "editor_stale" : "editor_saved"));
        } catch (IllegalArgumentException failure) {
            reject(player, request.controller(), "editor_invalid", failure.getMessage());
        } catch (IOException failure) {
            com.mojang.logging.LogUtils.getLogger().error("Could not save the AEUpgradeTree tree", failure);
            reject(player, request.controller(), "editor_io", "");
        }
    }

    /**
     * Sends current purchases and optionally the full server graph.
     *
     * @param player         recipient
     * @param controller     selected controller location
     * @param tree           live grid tree service
     * @param open           whether to open a new screen
     * @param includeCatalog whether to send the full graph
     * @param message        localized feedback key
     */
    private static void publish(ServerPlayer player, GlobalPos controller, UpgradeTreeService tree, boolean open,
                                boolean includeCatalog, String message) {
        publish(player, controller, tree, open, includeCatalog, message, false);
    }

    private static void publish(ServerPlayer player, GlobalPos controller, UpgradeTreeService tree, boolean open,
                                boolean includeCatalog, String message, boolean refresh) {
        var state = UpgradeTreeConfig.current();
        var purchases = tree.purchases();
        PlatformNetwork.sendToPlayer(player, new Snapshot(controller, Optional.of(purchases.ids()), open, message,
                state.revision(), includeCatalog ? state.json() : "", purchases.total(), refresh, purchases.allUnlocked(),
                !ResearchTaskConfig.current().tasks().isEmpty()));
    }

    /**
     * Resolves an online controller in an already loaded chunk inside the player's interaction range.
     *
     * @param player   player interacting with a controller
     * @param location selected controller's dimension and block position
     * @return valid controller node, or null if the target cannot be used
     */
    private static @Nullable IGridNode reachableController(ServerPlayer player, GlobalPos location) {
        var level = player.serverLevel();
        var pos = location.pos();
        if (!level.dimension().equals(location.dimension()) || !player.canInteractWithBlock(pos, 0)
                || !level.isLoaded(pos) || !(level.getBlockEntity(pos) instanceof ControllerBlockEntity controller)) {
            return null;
        }
        var node = controller.getMainNode().getNode();
        return node != null && node.getGrid().getPathingService().getControllerState() == ControllerState.CONTROLLER_ONLINE
                ? node : null;
    }

    /**
     * Reports a stale or invalid target without applying an action.
     *
     * @param player     original sender
     * @param controller location from the rejected request
     * @param reason     suffix of the localized feedback key
     * @param details    extra diagnostic text, possibly empty
     */
    private static void reject(ServerPlayer player, GlobalPos controller, String reason, String details) {
        reject(player, controller, reason, details, false);
    }

    private static void reject(ServerPlayer player, GlobalPos controller, String reason, String details, boolean refresh) {
        String diagnostic = details == null ? "" : details.substring(0, Math.min(details.length(), 512));
        PlatformNetwork.sendToPlayer(player, new Snapshot(controller, Optional.empty(), false,
                FEEDBACK + reason + (diagnostic.isEmpty() ? "" : "\n" + diagnostic),
                UpgradeTreeConfig.current().revision(), "", 0, refresh, false, false));
    }
}
