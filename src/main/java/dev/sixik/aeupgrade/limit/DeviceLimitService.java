package dev.sixik.aeupgrade.limit;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Consumer;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridNodeListener;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridServiceProvider;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.me.GridNode;
import appeng.parts.AEBasePart;
import dev.sixik.aeupgrade.channel.DeviceChannelType;
import dev.sixik.aeupgrade.tree.UpgradeTreeService;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps the list of devices allowed to operate in one connected AE2 grid.
 */
public final class DeviceLimitService implements IGridService, IGridServiceProvider {
    /**
     * Per-node NBT compound containing extra device slots for a controller.
     */
    private static final String BONUSES_TAG = "aeupgrade:device_limit_bonuses";

    /**
     * Per-node NBT compound containing fixed device limits for a controller.
     */
    private static final String OVERRIDES_TAG = "aeupgrade:device_limit_overrides";

    /**
     * Order of devices within a family, independent of chunk load or network iteration order.
     */
    private static final Comparator<Member> DEVICE_ORDER = Comparator.comparing(Member::dimension)
            .thenComparing(Member::position)
            .thenComparingInt(Member::face);

    /**
     * Stable priority for configured controllers when grids join.
     */
    private static final Comparator<IGridNode> CONTROLLER_ORDER = Comparator
            .<IGridNode, ResourceLocation>comparing(node -> node.getLevel().dimension().location())
            .thenComparing(node -> ((ControllerBlockEntity) node.getOwner()).getBlockPos());

    /**
     * Grid whose devices and channel paths are managed by this service.
     */
    private final IGrid grid;

    /**
     * Members of each supported family, kept current as AE2 builds, merges, or splits the grid.
     */
    private final Map<DeviceChannelType, Set<Member>> members = new EnumMap<>(DeviceChannelType.class);

    /**
     * Cached locations for live eligible nodes.
     */
    private final Map<IGridNode, Member> byNode = new HashMap<>();

    /**
     * Devices above their family's limit in the current grid.
     */
    private final Set<IGridNode> blocked = new HashSet<>();

    /**
     * Over-limit state communicated to AE2's node listeners on the previous tick.
     */
    private final Set<IGridNode> notifiedBlocked = new HashSet<>();

    /**
     * Devices that joined under a finite quota and require a state update after the grid settles.
     */
    private final Set<IGridNode> joined = new HashSet<>();

    /**
     * Configured controllers and their limits by device family. The first controller governs the grid.
     */
    private final NavigableMap<IGridNode, EnumMap<DeviceChannelType, NetworkDeviceLimit>> controllerLimits =
            new TreeMap<>(CONTROLLER_ORDER);

    /**
     * A policy snapshot held stable during each allocation pass.
     */
    private DeviceLimitConfig.Limits limits = DeviceLimitConfig.current();

    /**
     * True if the grid's blocked devices need recalculation.
     */
    private boolean selectionDirty;

    /**
     * Creates the service for one newly created ME grid.
     *
     * @param grid owning AE2 grid
     */
    public DeviceLimitService(IGrid grid) {
        this.grid = grid;
    }

    /**
     * Reads the current device limit setting for this grid.
     *
     * @param type supported device family
     * @return fixed limit or server-default adjustment for the family
     */
    public NetworkDeviceLimit getSetting(DeviceChannelType type) {
        var controller = controllerLimits.firstEntry();
        return controller == null ? NetworkDeviceLimit.INHERITED
                : controller.getValue().getOrDefault(type, NetworkDeviceLimit.INHERITED);
    }

    /**
     * Returns the maximum operating devices after applying controller, server and tree policy.
     *
     * @param type device family
     * @return maximum admitted devices, or -1 for unlimited
     */
    public int effectiveLimit(DeviceChannelType type) {
        int limit = getSetting(type).resolve(limits.forType(type));
        return grid.getService(UpgradeTreeService.class).effects().deviceLimit(type.effectTarget(), limit);
    }

    /**
     * Counts installed devices, including those that exceed the limit.
     *
     * @param type supported device family
     * @return installed count of blocks and parts
     */
    public int installed(DeviceChannelType type) {
        var family = members.get(type);
        return family == null ? 0 : family.size();
    }

    /**
     * Visits the live nodes tracked by the limit service.
     *
     * @param visitor callback for each supported device, which must leave grid membership intact
     */
    public void forEachDevice(Consumer<IGridNode> visitor) {
        byNode.keySet().forEach(visitor);
    }

    /**
     * Marks the selection dirty for the upcoming pathing tick.
     */
    public void refreshLimits() {
        selectionDirty = true;
    }

    /**
     * Sets an absolute limit on the grid's controller.
     *
     * @param targetedController live controller node in this grid
     * @param type               supported device family
     * @param limit              maximum number of working devices, or -1 for unlimited
     */
    public void setLimit(IGridNode targetedController, DeviceChannelType type, int limit) {
        changeSetting(targetedController, type, new NetworkDeviceLimit(true, limit));
    }

    /**
     * Resets the family's grid setting to the server default.
     *
     * @param targetedController live controller node in this grid
     * @param type               supported device family
     */
    public void resetLimit(IGridNode targetedController, DeviceChannelType type) {
        changeSetting(targetedController, type, NetworkDeviceLimit.INHERITED);
    }

    /**
     * Adjusts a fixed limit or the bonus to the server default.
     *
     * @param targetedController live controller node in this grid
     * @param type               supported device family
     * @param delta              signed change in device slots
     */
    public void adjustLimit(IGridNode targetedController, DeviceChannelType type, int delta) {
        changeSetting(targetedController, type, getSetting(type).adjust(delta));
    }

    /**
     * Saves the grid's setting on its configured controller, or anchors it to the targeted controller.
     *
     * @param targetedController live controller node in this grid
     * @param type               supported device family
     * @param setting            new fixed limit or server-default adjustment
     */
    private void changeSetting(IGridNode targetedController, DeviceChannelType type, NetworkDeviceLimit setting) {
        if (setting.equals(getSetting(type))) {
            return;
        }

        var controller = controllerLimits.isEmpty() ? targetedController : controllerLimits.firstKey();
        var settings = controllerLimits.computeIfAbsent(controller, ignored -> new EnumMap<>(DeviceChannelType.class));
        if (setting.equals(NetworkDeviceLimit.INHERITED)) {
            // Keep the controller's priority so clearing its last setting cannot activate another profile.
            settings.remove(type);
        } else {
            settings.put(type, setting);
        }
        ((ControllerBlockEntity) controller.getOwner()).setChanged();
        selectionDirty = true;
    }

    /**
     * Checks whether a device is currently above its type limit.
     *
     * @param node live device node, or null when there is no server node
     * @return true if the device is present but has no operating slot
     */
    public static boolean isBlocked(@Nullable IGridNode node) {
        if (!(node instanceof GridNode gridNode) || DeviceChannelType.of(node.getOwner()) == null) {
            return false;
        }
        var grid = gridNode.getInternalGrid();
        if (grid == null) {
            return false;
        }

        var service = grid.getService(DeviceLimitService.class);
        service.updateSelection();
        return service.blocked.contains(node);
    }

    /**
     * Applies changed limits before AE2 counts channels so disabled devices do not reserve any.
     */
    public void beforePathing() {
        var current = DeviceLimitConfig.current();
        if (!limits.equals(current)) {
            limits = current;
            selectionDirty = true;
        }
        updateSelection();
        if (!blocked.equals(notifiedBlocked)) {
            grid.getPathingService().repath();
        }
    }

    /**
     * Rebuilds admission whenever grid membership or the applied configuration has changed.
     */
    private void updateSelection() {
        if (!selectionDirty) {
            return;
        }
        selectionDirty = false;
        blocked.clear();
        for (var entry : members.entrySet()) {
            var type = entry.getKey();
            var family = entry.getValue();
            int limit = effectiveLimit(type);
            int firstBlocked = DeviceAdmission.firstBlockedIndex(family.size(), limit);
            if (firstBlocked == family.size()) {
                continue;
            }
            if (firstBlocked == 0) {
                for (var member : family) {
                    blocked.add(member.node());
                }
                continue;
            }

            var sorted = new ArrayList<>(family);
            sorted.sort(DEVICE_ORDER);
            for (int index = firstBlocked; index < sorted.size(); index++) {
                blocked.add(sorted.get(index).node());
            }
        }
    }

    /**
     * Restores a joining controller's settings or tracks a joining device for quota selection.
     *
     * @param node      joining AE2 node
     * @param savedData data transferred by AE2, or null for a new node
     */
    @Override
    public void addNode(IGridNode node, @Nullable CompoundTag savedData) {
        var owner = node.getOwner();
        if (owner instanceof ControllerBlockEntity) {
            if (savedData != null && (savedData.contains(BONUSES_TAG, Tag.TAG_COMPOUND)
                    || savedData.contains(OVERRIDES_TAG, Tag.TAG_COMPOUND))) {
                restoreController(node, savedData);
            }
            return;
        }
        var type = DeviceChannelType.of(owner);
        if (type == null) {
            return;
        }
        var member = Member.of(node, owner, type);
        byNode.put(node, member);
        members.computeIfAbsent(type, ignored -> new HashSet<>()).add(member);
        selectionDirty = true;
        if (effectiveLimit(type) >= 0) {
            joined.add(node);
        }
    }

    /**
     * Restores a controller's fixed limits and bonuses from AE2's node data.
     *
     * @param node      controller node joining this grid
     * @param savedData AE2's saved node data
     */
    private void restoreController(IGridNode node, CompoundTag savedData) {
        var overrides = savedData.getCompound(OVERRIDES_TAG);
        var bonuses = savedData.getCompound(BONUSES_TAG);
        var settings = new EnumMap<DeviceChannelType, NetworkDeviceLimit>(DeviceChannelType.class);
        for (var type : DeviceChannelType.values()) {
            var id = type.id();
            int override = overrides.getInt(id);
            int bonus = bonuses.getInt(id);
            if (overrides.contains(id, Tag.TAG_INT) && override >= DeviceLimitConfig.UNLIMITED) {
                settings.put(type, new NetworkDeviceLimit(true, override));
            } else if (bonus > 0) {
                settings.put(type, new NetworkDeviceLimit(false, bonus));
            }
        }
        controllerLimits.put(node, settings);
        selectionDirty = true;
    }

    /**
     * Releases a device slot or removes a controller's settings from the grid.
     *
     * @param node departing AE2 node
     */
    @Override
    public void removeNode(IGridNode node) {
        if (node.getOwner() instanceof ControllerBlockEntity && controllerLimits.remove(node) != null) {
            selectionDirty = true;
            return;
        }
        var member = byNode.remove(node);
        if (member == null) {
            return;
        }
        members.get(member.type()).remove(member);
        blocked.remove(node);
        notifiedBlocked.remove(node);
        joined.remove(node);
        selectionDirty = true;
    }

    /**
     * Saves controller settings when the controller is written to disk or moves to a different grid.
     *
     * @param node      AE2 node being saved
     * @param savedData writable tag for node service data
     */
    @Override
    public void saveNodeData(IGridNode node, CompoundTag savedData) {
        if (!(node.getOwner() instanceof ControllerBlockEntity)) {
            return;
        }
        var settings = controllerLimits.get(node);
        if (settings != null) {
            var bonuses = new CompoundTag();
            var overrides = new CompoundTag();
            settings.forEach((type, setting) -> (setting.overridden() ? overrides : bonuses)
                    .putInt(type.id(), setting.value()));
            savedData.put(BONUSES_TAG, bonuses);
            if (!overrides.isEmpty()) {
                savedData.put(OVERRIDES_TAG, overrides);
            }
        }
    }

    /**
     * Updates AE2's device status and lets newly admitted nodes resume their work.
     */
    @Override
    public void onServerEndTick() {
        if (blocked.equals(notifiedBlocked) && joined.isEmpty()) {
            return;
        }
        var changed = new HashSet<>(joined);
        joined.clear();
        for (var node : blocked) {
            if (!notifiedBlocked.contains(node)) {
                changed.add(node);
            }
        }
        for (var node : notifiedBlocked) {
            if (!blocked.contains(node)) {
                changed.add(node);
            }
        }
        notifiedBlocked.clear();
        notifiedBlocked.addAll(blocked);

        for (var node : changed) {
            if (node instanceof GridNode gridNode && gridNode.getInternalGrid() == grid) {
                gridNode.notifyStatusChange(IGridNodeListener.State.CHANNEL);
            }
        }
    }

    /**
     * Stable device position used to decide which devices get slots.
     *
     * @param node      AE2 node for the device
     * @param type      device family
     * @param dimension world dimension ID
     * @param position  location of the device block or its panel host
     * @param face      panel direction ordinal, or -1 for a full block
     */
    private record Member(IGridNode node, DeviceChannelType type, ResourceLocation dimension, BlockPos position,
                          int face) {
        /**
         * Captures the device position from AE2's block or panel owner.
         *
         * @param node  live node being added to the grid
         * @param owner supported block entity or panel
         * @param type  family of the device
         * @return member with a stable sort key
         */
        static Member of(IGridNode node, Object owner, DeviceChannelType type) {
            var dimension = node.getLevel().dimension().location();
            if (owner instanceof AEBasePart panel) {
                return new Member(node, type, dimension, panel.getBlockEntity().getBlockPos(), panel.getSide().ordinal());
            }
            var block = (BlockEntity) owner;
            return new Member(node, type, dimension, block.getBlockPos(), -1);
        }
    }
}
