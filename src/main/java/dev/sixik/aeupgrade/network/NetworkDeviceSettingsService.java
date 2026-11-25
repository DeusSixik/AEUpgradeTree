package dev.sixik.aeupgrade.network;

import java.util.Comparator;
import java.util.NavigableMap;
import java.util.TreeMap;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridServiceProvider;
import appeng.api.networking.pathing.ChannelMode;
import appeng.blockentity.networking.ControllerBlockEntity;
import dev.sixik.aeupgrade.channel.ChannelCostRules;
import dev.sixik.aeupgrade.channel.DeviceChannelType;
import dev.sixik.aeupgrade.tuning.DeviceTuningProfile.Setting;
import dev.sixik.aeupgrade.tuning.DeviceTuningService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Owns the network-scoped device settings persisted on AE2 controller nodes.
 */
public final class NetworkDeviceSettingsService implements IGridService, IGridServiceProvider {
    /**
     * Per-controller node data key for device family overrides.
     */
    private static final String PROFILE_TAG = "aeupgrade:network_device_settings";

    /**
     * Deterministic preference when separately configured networks join.
     */
    private static final Comparator<IGridNode> CONTROLLER_ORDER = Comparator
            .<IGridNode, ResourceLocation>comparing(node -> node.getLevel().dimension().location())
            .thenComparing(node -> ((ControllerBlockEntity) node.getOwner()).getBlockPos());

    /**
     * Grid whose devices use the active profile.
     */
    private final IGrid grid;

    /**
     * Profiles of all configured controllers currently attached to this grid.
     */
    private final NavigableMap<IGridNode, NetworkDeviceProfile> controllers = new TreeMap<>(CONTROLLER_ORDER);

    /**
     * Selected profile, or null if this grid has never been configured.
     */
    private @Nullable NetworkDeviceProfile active;

    /**
     * Whether a cost or topology change requires a fresh pathing pass.
     */
    private boolean repathPending;

    /**
     * Whether a tuning or topology change invalidated AE2's cached idle energy.
     */
    private boolean idleRefreshPending;

    /**
     * Creates the device settings service for an AE2 grid.
     *
     * @param grid owning AE2 grid
     */
    public NetworkDeviceSettingsService(IGrid grid) {
        this.grid = grid;
    }

    /**
     * Reads the active network channel cost.
     *
     * @param type device family
     * @return configured number of channels, or {@link ChannelCostRules#INHERIT}
     */
    public int channelCost(DeviceChannelType type) {
        return active == null ? ChannelCostRules.INHERIT : active.channelCost(type);
    }

    /**
     * Reads the active network tuning value.
     *
     * @param type    device family
     * @param setting tuning property
     * @return configured value or NaN when inherited
     */
    public double tuning(DeviceChannelType type, Setting setting) {
        return active == null ? Double.NaN : active.tuning(type, setting);
    }

    /**
     * Sets or clears a device family's network channel cost.
     *
     * @param target selected controller node on this grid
     * @param type   device family
     * @param cost   non-negative channel count, or {@link ChannelCostRules#INHERIT} to reset
     */
    public void setChannelCost(IGridNode target, DeviceChannelType type, int cost) {
        if (cost != channelCost(type)) {
            profileForWrite(target).setChannelCost(type, cost);
            repathPending = true;
            markControllerChanged();
        }
    }

    /**
     * Sets or clears one network tuning property.
     *
     * @param target  selected controller node on this grid
     * @param type    device family
     * @param setting tuning property
     * @param value   configured value or NaN to reset
     */
    public void setTuning(IGridNode target, DeviceChannelType type, Setting setting, double value) {
        if (Double.compare(value, tuning(type, setting)) != 0) {
            profileForWrite(target).setTuning(type, setting, value);
            if (setting == Setting.IDLE_FLAT || setting == Setting.IDLE_PERCENT) {
                idleRefreshPending = true;
            }
            markControllerChanged();
        }
    }

    /**
     * Anchors the first override to the selected controller or reuses the existing governing controller.
     *
     * @param target selected controller node on this grid
     * @return writable active profile
     */
    private NetworkDeviceProfile profileForWrite(IGridNode target) {
        var controller = controllers.isEmpty() ? target : controllers.firstKey();
        active = controllers.computeIfAbsent(controller, ignored -> new NetworkDeviceProfile());
        return active;
    }

    /**
     * Persists changes together with the active controller.
     */
    private void markControllerChanged() {
        ((ControllerBlockEntity) controllers.firstKey().getOwner()).setChanged();
    }

    /**
     * Updates the active profile and queues cache invalidation after a topology change.
     */
    private void selectController() {
        var first = controllers.firstEntry();
        var next = first == null ? null : first.getValue();
        if (active != next) {
            active = next;
            repathPending = true;
            idleRefreshPending = true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addNode(IGridNode node, @Nullable CompoundTag savedData) {
        if (node.getOwner() instanceof ControllerBlockEntity && savedData != null
                && savedData.contains(PROFILE_TAG, Tag.TAG_COMPOUND)) {
            controllers.put(node, NetworkDeviceProfile.load(savedData.getCompound(PROFILE_TAG)));
            selectController();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeNode(IGridNode node) {
        if (node.getOwner() instanceof ControllerBlockEntity && controllers.remove(node) != null) {
            selectController();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveNodeData(IGridNode node, CompoundTag savedData) {
        if (node.getOwner() instanceof ControllerBlockEntity) {
            var profile = controllers.get(node);
            if (profile != null) {
                // An empty profile keeps its priority after the operator clears the last override.
                savedData.put(PROFILE_TAG, profile.save());
            }
        }
    }

    /**
     * Applies changes after AE2 has processed the current batch of topology updates.
     */
    @Override
    public void onServerStartTick() {
        if (repathPending) {
            repathPending = false;
            var pathing = grid.getPathingService();
            if (pathing.getChannelMode() != ChannelMode.INFINITE) {
                pathing.repath();
            }
        }
        if (idleRefreshPending) {
            idleRefreshPending = false;
            grid.getService(DeviceTuningService.class).refreshIdlePower();
        }
    }
}
