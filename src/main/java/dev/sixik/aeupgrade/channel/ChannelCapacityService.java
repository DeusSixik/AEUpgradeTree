package dev.sixik.aeupgrade.channel;

import java.util.LinkedHashMap;
import java.util.Map;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridServiceProvider;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.parts.networking.CablePart;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Kind;
import dev.sixik.aeupgrade.tree.UpgradeTreeService;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * Tracks the test cable capacity for one grid and saves overrides with their controller nodes.
 */
public final class ChannelCapacityService implements IGridService, IGridServiceProvider {
    /**
     * NBT key in AE2's per-node service data for the default-mode normal cable limit.
     */
    private static final String NORMAL_LIMIT_TAG = "aeupgrade:normal_cable_limit";

    /**
     * Grid whose cable nodes use this service.
     */
    private final IGrid grid;

    /**
     * Configured controller nodes and their limits in channels. The first entry remains authoritative
     * while it belongs to this grid, including when a second configured controller joins the grid.
     */
    private final Map<IGridNode, Integer> controllerLimits = new LinkedHashMap<>();

    /**
     * Cached default-mode normal cable limit for use during channel routing.
     */
    private int normalLimit = CableChannelCapacity.DEFAULT_NORMAL_LIMIT;

    /**
     * Creates the capacity service for a newly created grid.
     *
     * @param grid AE2 grid that owns this service
     */
    public ChannelCapacityService(IGrid grid) {
        this.grid = grid;
    }

    /**
     * Reads the cable routing limit including tree upgrades.
     *
     * @return normal cable capacity in the default channel mode, in channels
     */
    public int getNormalLimit() {
        return CableChannelCapacity.adjust(normalLimit, (int) grid.getService(UpgradeTreeService.class).effects().amount(Kind.CHANNELS));
    }

    /**
     * Adjusts the current override and schedules channel routing again when it changes.
     *
     * @param targetedController controller node selected by the operator within this grid
     * @param delta              signed change to the default-mode normal cable limit, in channels
     * @return new default-mode normal cable limit, in channels
     */
    public int adjust(IGridNode targetedController, int delta) {
        int adjustedLimit = CableChannelCapacity.adjust(normalLimit, delta);
        if (adjustedLimit == normalLimit) {
            return normalLimit;
        }

        var controller = controllerLimits.isEmpty() ? targetedController : controllerLimits.keySet().iterator().next();
        controllerLimits.put(controller, adjustedLimit);
        normalLimit = adjustedLimit;
        ((ControllerBlockEntity) controller.getOwner()).setChanged();
        refreshCableCapacity();
        return adjustedLimit;
    }

    /**
     * Recomputes routing and notifies smart cables after their effective capacity changes.
     */
    public void refreshCableCapacity() {
        grid.getPathingService().repath();

        // The smart cable's display can change even when its used channel count stays the same.
        for (var node : grid.getNodes()) {
            if (node.getOwner() instanceof CablePart cable) {
                cable.markForUpdate();
            }
        }
    }

    /**
     * Restores a controller's override when the controller loads or moves to this grid.
     *
     * @param gridNode  node joining this grid
     * @param savedData node data supplied by AE2, or null for a node without saved data
     */
    @Override
    public void addNode(IGridNode gridNode, @Nullable CompoundTag savedData) {
        if (gridNode.getOwner() instanceof ControllerBlockEntity && savedData != null
                && savedData.contains(NORMAL_LIMIT_TAG)) {
            int restoredLimit = CableChannelCapacity.adjust(0, savedData.getInt(NORMAL_LIMIT_TAG));
            if (controllerLimits.isEmpty()) {
                normalLimit = restoredLimit;
            }
            controllerLimits.put(gridNode, restoredLimit);
        }
    }

    /**
     * Removes a departing controller's override from this grid.
     *
     * @param gridNode node leaving this grid
     */
    @Override
    public void removeNode(IGridNode gridNode) {
        if (controllerLimits.remove(gridNode) != null) {
            normalLimit = controllerLimits.isEmpty()
                    ? CableChannelCapacity.DEFAULT_NORMAL_LIMIT
                    : controllerLimits.values().iterator().next();
        }
    }

    /**
     * Writes a controller's override into AE2's per-node data before saving or moving the node.
     *
     * @param gridNode  node being saved
     * @param savedData writable compound for this node's service data
     */
    @Override
    public void saveNodeData(IGridNode gridNode, CompoundTag savedData) {
        var limit = controllerLimits.get(gridNode);
        if (limit != null) {
            savedData.putInt(NORMAL_LIMIT_TAG, limit);
        }
    }
}
