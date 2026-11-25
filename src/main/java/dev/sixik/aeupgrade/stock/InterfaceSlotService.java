package dev.sixik.aeupgrade.stock;

import java.util.Comparator;
import java.util.HashSet;
import java.util.NavigableMap;
import java.util.Set;
import java.util.TreeMap;

import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridServiceProvider;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.helpers.InterfaceLogicHost;
import appeng.me.GridNode;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Kind;
import dev.sixik.aeupgrade.tree.UpgradeTreeService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Keeps the interface capacity of a grid with its configured ME controller.
 */
public final class InterfaceSlotService implements IGridService, IGridServiceProvider {
    /**
     * Per-controller save key for the absolute number of enabled slot pairs.
     */
    private static final String CAPACITY_TAG = "aeupgrade:interface_slot_capacity";

    /**
     * Priority order for saved settings when two controller groups join.
     */
    private static final Comparator<IGridNode> CONTROLLER_ORDER = Comparator
            .<IGridNode, ResourceLocation>comparing(node -> node.getLevel().dimension().location())
            .thenComparing(node -> ((ControllerBlockEntity) node.getOwner()).getBlockPos());

    /**
     * Explicit capacities keyed by live controller nodes.
     */
    private final NavigableMap<IGridNode, Integer> controllers = new TreeMap<>(CONTROLLER_ORDER);

    /**
     * Stock interfaces attached to this grid.
     */
    private final Set<InterfaceSlots> interfaces = new HashSet<>();
    /**
     * Grid supplying purchased slot extensions.
     */
    private final IGrid grid;

    /**
     * Current grid capacity in slot pairs.
     */
    private int capacity = InterfaceSlotRange.DEFAULT_CAPACITY;

    /**
     * True after any grid membership change requiring a refresh of stocking work.
     */
    private boolean dirty;

    /**
     * Creates the service for an AE2 grid.
     *
     * @param grid newly constructed grid
     */
    public InterfaceSlotService(IGrid grid) {
        this.grid = grid;
    }

    /**
     * Resolves the setting supplied by a device's current grid.
     *
     * @param node device node, or null before it connects
     * @return configured slot pairs, or the native capacity
     */
    public static int capacityFor(@Nullable IGridNode node) {
        if (node instanceof GridNode gridNode && gridNode.getInternalGrid() != null) {
            return gridNode.getInternalGrid().getService(InterfaceSlotService.class).effectiveCapacity();
        }
        return InterfaceSlotRange.DEFAULT_CAPACITY;
    }

    /**
     * @return current capacity in slot pairs
     */
    public int capacity() {
        return capacity;
    }

    /**
     * @return active number of slot pairs with tree adjustments
     */
    public int effectiveCapacity() {
        return InterfaceSlotRange.adjust(capacity, (int) grid.getService(UpgradeTreeService.class).effects().amount(Kind.INTERFACE_SLOTS));
    }

    /**
     * Refreshes interface work after an effect changes the active number of slot pairs.
     */
    public void refreshCapacity() {
        dirty = true;
    }

    /**
     * Changes the grid's interface capacity and refreshes connected devices.
     *
     * @param target controller aimed at by the operator
     * @param slots  new number of unlocked pairs
     * @return effective number of unlocked pairs
     */
    public int setCapacity(IGridNode target, int slots) {
        int next = InterfaceSlotRange.adjust(0, slots);
        if (capacity != next) {
            var controller = controllers.isEmpty() ? target : controllers.firstKey();
            controllers.put(controller, next);
            capacity = next;
            ((ControllerBlockEntity) controller.getOwner()).setChanged();
            dirty = true;
            onServerStartTick();
        }
        return capacity;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onServerStartTick() {
        if (dirty) {
            dirty = false;
            for (var device : interfaces) {
                device.aeupgrade$refreshSlotCapacity();
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void addNode(IGridNode node, @Nullable CompoundTag savedData) {
        var owner = node.getOwner();
        if (owner instanceof ControllerBlockEntity && savedData != null && savedData.contains(CAPACITY_TAG, Tag.TAG_INT)) {
            controllers.put(node, InterfaceSlotRange.adjust(0, savedData.getInt(CAPACITY_TAG)));
            capacity = controllers.firstEntry().getValue();
            dirty = true;
        } else if (InterfaceSlots.supports(owner)) {
            interfaces.add((InterfaceSlots) ((InterfaceLogicHost) owner).getInterfaceLogic());
            dirty = true;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void removeNode(IGridNode node) {
        var owner = node.getOwner();
        if (owner instanceof ControllerBlockEntity && controllers.remove(node) != null) {
            capacity = controllers.isEmpty() ? InterfaceSlotRange.DEFAULT_CAPACITY : controllers.firstEntry().getValue();
            dirty = true;
        } else if (InterfaceSlots.supports(owner)) {
            interfaces.remove((InterfaceSlots) ((InterfaceLogicHost) owner).getInterfaceLogic());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void saveNodeData(IGridNode node, CompoundTag savedData) {
        if (node.getOwner() instanceof ControllerBlockEntity) {
            var saved = controllers.get(node);
            if (saved != null) {
                savedData.putInt(CAPACITY_TAG, saved);
            }
        }
    }
}
