package dev.sixik.aeupgrade.pattern;

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
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import appeng.me.GridNode;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Kind;
import dev.sixik.aeupgrade.tree.UpgradeTreeService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Maintains the extra slot count for standard Pattern Providers attached to one controller grid.
 */
public final class PatternSlotService implements IGridService, IGridServiceProvider {
    /**
     * Per-node save key containing extra slots per provider.
     */
    private static final String BONUS_TAG = "aeupgrade:pattern_slot_bonus";

    /**
     * Grid providing the runtime tree bonus.
     */
    private final IGrid grid;

    /**
     * Priority of configured controllers when two controller groups join.
     */
    private static final Comparator<IGridNode> CONTROLLER_ORDER = Comparator
            .<IGridNode, ResourceLocation>comparing(node -> node.getLevel().dimension().location())
            .thenComparing(node -> ((ControllerBlockEntity) node.getOwner()).getBlockPos());

    /**
     * Saved bonuses in controller priority order, including zero after explicitly clearing one.
     */
    private final NavigableMap<IGridNode, Integer> controllerBonuses = new TreeMap<>(CONTROLLER_ORDER);

    /**
     * Standard providers with a live node on this grid.
     */
    private final Set<ProviderPatternSlots> providers = new HashSet<>();

    /**
     * Bonus published after the last controller update.
     */
    private int bonus;

    /**
     * True when node membership changed and recipes may need refreshing.
     */
    private boolean dirty;

    /**
     * Creates a new Pattern Provider upgrade service for an AE2 grid.
     *
     * @param grid grid for which AE2 is constructing services
     */
    public PatternSlotService(IGrid grid) {
        this.grid = grid;
    }

    /**
     * Reads the extra slots available to a live provider.
     *
     * @param node live provider node, or null while its host has no grid
     * @return extra slots supplied by the connected controller
     */
    public static int bonusFor(@Nullable IGridNode node) {
        if (node instanceof GridNode gridNode && gridNode.getInternalGrid() != null) {
            return gridNode.getInternalGrid().getService(PatternSlotService.class).effectiveBonus();
        }
        return 0;
    }

    /**
     * @return extra slots for each standard provider on this grid
     */
    public int getBonus() {
        return bonus;
    }

    /**
     * @return total adjustment per provider after combining the operator setting and tree bonus
     */
    public int effectiveBonus() {
        return PatternSlotRange.adjust(bonus, (int) grid.getService(UpgradeTreeService.class).effects().amount(Kind.PATTERN_SLOTS));
    }

    /**
     * Schedules recipe refreshes after the effective slot capacity changes.
     */
    public void refreshPatterns() {
        dirty = true;
    }

    /**
     * Changes the extra slots provided by the grid's configured controller.
     *
     * @param target live controller selected by the operator
     * @param delta  signed number of slots to add or remove per provider
     * @return new extra slots per provider
     */
    public int adjust(IGridNode target, int delta) {
        return setBonus(target, PatternSlotRange.adjust(bonus, delta));
    }

    /**
     * Sets the capacity per provider in the selected grid.
     *
     * @param target   live controller selected by the operator
     * @param capacity total slots per provider, between zero and Integer.MAX_VALUE
     * @return signed adjustment from the native capacity
     * @throws IllegalArgumentException if the capacity is negative
     */
    public int setCapacity(IGridNode target, int capacity) {
        if (capacity < 0) {
            throw new IllegalArgumentException("Negative pattern slot capacity: " + capacity);
        }
        return setBonus(target, capacity - PatternSlotRange.ROW_SIZE);
    }

    /**
     * Saves the signed slot adjustment and refreshes recipes.
     *
     * @param target   live controller selected by the operator
     * @param newBonus adjustment to the native capacity
     * @return current adjustment
     */
    private int setBonus(IGridNode target, int newBonus) {
        if (newBonus != bonus) {
            var controller = controllerBonuses.isEmpty() ? target : controllerBonuses.firstKey();
            controllerBonuses.put(controller, newBonus);
            bonus = newBonus;
            dirty = true;
            ((ControllerBlockEntity) controller.getOwner()).setChanged();
            onServerStartTick();
        }
        return bonus;
    }

    /**
     * Refreshes providers after AE2 finishes adding or transferring their nodes.
     */
    @Override
    public void onServerStartTick() {
        if (dirty) {
            dirty = false;
            for (var provider : providers) {
                provider.aeupgrade$refreshPatternCapacity();
            }
        }
    }

    /**
     * Restores controller bonuses or tracks a joining provider.
     *
     * @param node      joining AE2 node
     * @param savedData provider and controller data transferred by AE2
     */
    @Override
    public void addNode(IGridNode node, @Nullable CompoundTag savedData) {
        var owner = node.getOwner();
        if (owner instanceof ControllerBlockEntity && savedData != null && savedData.contains(BONUS_TAG, Tag.TAG_INT)) {
            controllerBonuses.put(node, PatternSlotRange.adjust(0, savedData.getInt(BONUS_TAG)));
            bonus = controllerBonuses.firstEntry().getValue();
            dirty = true;
        } else if (ProviderPatternSlots.supports(owner)) {
            providers.add((ProviderPatternSlots) ((PatternProviderLogicHost) owner).getLogic());
            dirty = true;
        }
    }

    /**
     * Forgets departing providers and controllers.
     *
     * @param node departing AE2 node
     */
    @Override
    public void removeNode(IGridNode node) {
        var owner = node.getOwner();
        if (owner instanceof ControllerBlockEntity && controllerBonuses.remove(node) != null) {
            bonus = controllerBonuses.isEmpty() ? 0 : controllerBonuses.firstEntry().getValue();
            dirty = true;
        } else if (ProviderPatternSlots.supports(owner)) {
            providers.remove((ProviderPatternSlots) ((PatternProviderLogicHost) owner).getLogic());
        }
    }

    /**
     * Persists the configured controller bonus across saves and grid transfers.
     *
     * @param node      saving AE2 node
     * @param savedData writable per-node save data
     */
    @Override
    public void saveNodeData(IGridNode node, CompoundTag savedData) {
        if (node.getOwner() instanceof ControllerBlockEntity) {
            var saved = controllerBonuses.get(node);
            if (saved != null) {
                savedData.putInt(BONUS_TAG, saved);
            }
        }
    }
}
