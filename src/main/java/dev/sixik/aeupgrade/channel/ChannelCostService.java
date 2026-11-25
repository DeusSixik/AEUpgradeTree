package dev.sixik.aeupgrade.channel;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridServiceProvider;
import appeng.api.networking.pathing.ChannelMode;
import appeng.api.networking.pathing.ControllerState;
import appeng.me.GridNode;
import dev.sixik.aeupgrade.network.NetworkDeviceSettingsService;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Kind;
import dev.sixik.aeupgrade.tree.UpgradeTreeService;
import it.unimi.dsi.fastutil.objects.Reference2IntOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Stores local costs with AE2 nodes and resolves them against network and world settings.
 */
public final class ChannelCostService implements IGridService, IGridServiceProvider {
    /**
     * Key in AE2's saved per-node data for the configured local channel cost.
     */
    private static final String LOCAL_COST_TAG = "aeupgrade:device_channel_cost";

    /**
     * Non-negative local costs keyed by the identity of each configured node in this grid.
     */
    private final Reference2IntOpenHashMap<IGridNode> localCosts = new Reference2IntOpenHashMap<>();

    /**
     * Shared server data, captured when the first configurable device joins this grid.
     */
    private @Nullable GlobalChannelCosts globalCosts;

    /**
     * Creates a service with inherited local costs. AE2 requires a public constructor.
     */
    public ChannelCostService() {
        localCosts.defaultReturnValue(ChannelCostRules.INHERIT);
    }

    /**
     * Resolves the override applied to a live device on a finite-channel controller grid.
     *
     * @param node device node, or null if the device has no live node
     * @return effective configured cost in channels, or {@link ChannelCostRules#INHERIT} for AE2 behavior
     */
    public static int configuredCost(@Nullable IGridNode node) {
        if (!(node instanceof GridNode gridNode)) {
            return ChannelCostRules.INHERIT;
        }

        var type = DeviceChannelType.of(node.getOwner());
        var grid = gridNode.getInternalGrid();
        if (type == null || grid == null) {
            return ChannelCostRules.INHERIT;
        }
        var pathing = grid.getPathingService();
        if (pathing.getControllerState() != ControllerState.CONTROLLER_ONLINE
                || pathing.getChannelMode() == ChannelMode.INFINITE) {
            return ChannelCostRules.INHERIT;
        }

        var service = grid.getService(ChannelCostService.class);
        int local = service.localCosts.getInt(node);
        int network = grid.getService(NetworkDeviceSettingsService.class).channelCost(type);
        int configured = ChannelCostRules.resolve(local, network,
                service.globalCosts == null ? ChannelCostRules.INHERIT : service.globalCosts.cost(type));
        double delta = grid.getService(UpgradeTreeService.class).effects().amount(Kind.CHANNEL_COST, type.effectTarget());
        if (delta == 0) {
            return configured;
        }
        int base = configured == ChannelCostRules.INHERIT ? (node.hasFlag(GridFlags.REQUIRE_CHANNEL) ? 1 : 0) : configured;
        return Math.max(0, (int) (base + delta));
    }

    /**
     * Changes the override on one live node.
     *
     * @param node supported device node in this grid
     * @param cost non-negative cost in channels, or {@link ChannelCostRules#INHERIT} to clear it
     * @return true if the stored setting changed
     */
    public boolean setLocalCost(IGridNode node, int cost) {
        if (cost == ChannelCostRules.INHERIT) {
            return localCosts.removeInt(node) != ChannelCostRules.INHERIT;
        }
        return localCosts.put(node, cost) != cost;
    }

    /**
     * Restores a local override when a device loads or moves to this grid.
     *
     * @param node      joining node
     * @param savedData AE2 node data, or null for a new device
     */
    @Override
    public void addNode(IGridNode node, @Nullable CompoundTag savedData) {
        if (DeviceChannelType.of(node.getOwner()) == null) {
            return;
        }
        if (globalCosts == null) {
            globalCosts = GlobalChannelCosts.get(node.getLevel().getServer());
        }
        if (savedData != null && savedData.contains(LOCAL_COST_TAG, Tag.TAG_INT) && savedData.getInt(LOCAL_COST_TAG) >= 0) {
            localCosts.put(node, savedData.getInt(LOCAL_COST_TAG));
        }
    }

    /**
     * Drops the in-memory index entry for a departing node.
     *
     * @param node node leaving this grid
     */
    @Override
    public void removeNode(IGridNode node) {
        localCosts.removeInt(node);
    }

    /**
     * Writes a local override when AE2 saves the device or transfers its node.
     *
     * @param node      node being saved
     * @param savedData writable per-node compound
     */
    @Override
    public void saveNodeData(IGridNode node, CompoundTag savedData) {
        int cost = localCosts.getInt(node);
        if (cost != ChannelCostRules.INHERIT) {
            savedData.putInt(LOCAL_COST_TAG, cost);
        }
    }
}
