package dev.sixik.aeupgrade.tuning;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

import appeng.api.config.Actionable;
import appeng.api.config.PowerMultiplier;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.networking.IGridService;
import appeng.api.networking.IGridServiceProvider;
import appeng.api.networking.crafting.ICraftingProvider;
import appeng.api.networking.events.GridPowerIdleChange;
import appeng.api.networking.pathing.ControllerState;
import appeng.me.GridNode;
import dev.sixik.aeupgrade.channel.DeviceChannelType;
import dev.sixik.aeupgrade.network.NetworkDeviceSettingsService;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Kind;
import dev.sixik.aeupgrade.tree.UpgradeTreeService;
import dev.sixik.aeupgrade.tuning.DeviceTuningProfile.Setting;
import it.unimi.dsi.fastutil.objects.Reference2ObjectOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * Resolves local, network and global tuning and keeps AE2's cached idle draw up to date.
 */
public final class DeviceTuningService implements IGridService, IGridServiceProvider {
    /**
     * Key in AE2's per-node data for a device's local properties.
     */
    private static final String LOCAL_TAG = "aeupgrade:device_tuning";

    /**
     * Grid that owns these device settings.
     */
    private final IGrid grid;

    /**
     * Profiles for live nodes with local overrides.
     */
    private final Map<IGridNode, DeviceTuningProfile> localProfiles = new Reference2ObjectOpenHashMap<>();

    /**
     * Vanilla Pattern Provider dispatchers attached to this grid.
     */
    private final Set<ProviderDispatch> providers = Collections.newSetFromMap(new Reference2ObjectOpenHashMap<>());

    /**
     * Shared world data, captured when a supported device joins this grid.
     */
    private @Nullable GlobalDeviceTuning globalTuning;

    /**
     * True when cached idle power must be refreshed after node or controller changes.
     */
    private boolean idleRefreshPending;

    /**
     * Creates a tuning service for one ME grid.
     *
     * @param grid owning grid
     */
    public DeviceTuningService(IGrid grid) {
        this.grid = grid;
    }

    /**
     * Applies the matching energy modifiers to a device's AE2 draw.
     *
     * @param node     device AE2 node, or null if unavailable
     * @param base     original draw in AE per tick
     * @param crafting true for active assembly and false for idle draw
     * @return the adjusted draw in AE per tick
     */
    public static double energy(@Nullable IGridNode node, double base, boolean crafting) {
        if (!(node instanceof GridNode gridNode)) {
            return base;
        }
        var type = DeviceChannelType.of(node.getOwner());
        var service = type == null ? null : activeService(gridNode);
        if (service == null) {
            return base;
        }

        double percent = service.energyPercent(node, type, crafting);
        double flat = service.energyFlat(node, type, crafting);
        return DeviceTuningMath.adjustedDraw(base, percent, flat);
    }

    /**
     * Reads the energy tuning that applies per recipe operation while an assembler is working.
     *
     * @param node live assembler node
     * @return effective crafting modifiers before applying them to the operation's draw
     */
    public CraftEnergy craftEnergy(IGridNode node) {
        if (grid.getPathingService().getControllerState() != ControllerState.CONTROLLER_ONLINE) {
            return new CraftEnergy(0, 0);
        }
        var type = DeviceChannelType.MOLECULAR_ASSEMBLER;
        return new CraftEnergy(energyPercent(node, type, true), energyFlat(node, type, true));
    }

    /**
     * Resolves the adjusted energy percent including the tree bonus.
     *
     * @param node     live device node
     * @param type     device family
     * @param crafting true for active assembly, false for idle draw
     * @return signed percentage points relative to base draw
     */
    private double energyPercent(IGridNode node, DeviceChannelType type, boolean crafting) {
        double percent = resolve(node, type, crafting ? Setting.CRAFT_PERCENT : Setting.IDLE_PERCENT);
        return percent + grid.getService(UpgradeTreeService.class).effects()
                .amount(crafting ? Kind.CRAFT_PERCENT : Kind.IDLE_PERCENT, type.effectTarget());
    }

    /**
     * @param node     live device
     * @param type     device family
     * @param crafting whether to use the active crafting adjustment
     * @return signed sum of operator and tree adjustments, in AE/t
     */
    private double energyFlat(IGridNode node, DeviceChannelType type, boolean crafting) {
        double flat = resolve(node, type, crafting ? Setting.CRAFT_FLAT : Setting.IDLE_FLAT);
        return flat + grid.getService(UpgradeTreeService.class).effects()
                .amount(crafting ? Kind.CRAFT_FLAT : Kind.IDLE_FLAT, type.effectTarget());
    }

    /**
     * Effective modifiers for a crafting operation.
     *
     * @param percent signed percentage of its base draw
     * @param flat    signed AE/t applied after the percentage
     */
    public record CraftEnergy(double percent, double flat) {
    }

    /**
     * Applies virtual acceleration to the assembler's installed card count.
     *
     * @param node      assembler node, or null if unavailable
     * @param installed number of physical speed cards
     * @return number of speed cards used by AE2's crafting loop
     */
    public static int speedCards(@Nullable IGridNode node, int installed) {
        if (!(node instanceof GridNode gridNode)) {
            return installed;
        }
        var service = activeService(gridNode);
        if (service == null) {
            return installed;
        }
        double virtual = service.resolve(node, DeviceChannelType.MOLECULAR_ASSEMBLER, Setting.VIRTUAL_SPEED_CARDS)
                + service.grid.getService(UpgradeTreeService.class).effects().amount(Kind.SPEED_CARDS);
        int cards = Math.clamp((int) virtual, -DeviceTuningMath.MAX_SPEED_CARDS, DeviceTuningMath.MAX_SPEED_CARDS);
        return DeviceTuningMath.effectiveSpeedCards(installed, cards);
    }

    /**
     * Returns the percentage applied after the assembler's card level.
     *
     * @param node assembler node, or null if unavailable
     * @return configured signed percentage, or zero for AE2 speed
     */
    public static double speedPercent(@Nullable IGridNode node) {
        return Math.max(-100, assemblerSetting(node, Setting.CRAFT_SPEED_PERCENT));
    }

    /**
     * Returns the maximum jobs accepted into a new assembly batch.
     *
     * @param node assembler node, or null if unavailable
     * @return batch limit, with one for ordinary AE2 processing
     */
    public static int batchSize(@Nullable IGridNode node) {
        return Math.clamp((int) assemblerSetting(node, Setting.BATCH_SIZE),
                1, DeviceTuningMath.MAX_BATCH_SIZE);
    }

    /**
     * Resolves the provider's per-tick cap in controller networks.
     *
     * @param node live Pattern Provider node, or null before initialization
     * @return configured recipe operations per tick, or zero for native AE2 dispatch
     */
    public static int providerThroughput(@Nullable IGridNode node) {
        if (!(node instanceof GridNode gridNode)
                || DeviceChannelType.of(node.getOwner()) != DeviceChannelType.PATTERN_PROVIDER) {
            return 0;
        }
        var service = activeService(gridNode);
        if (service == null) {
            return 0;
        }
        int configured = (int) service.resolve(node, DeviceChannelType.PATTERN_PROVIDER, Setting.PROVIDER_THROUGHPUT);
        int base = configured == 0 ? ProviderDispatch.DEFAULT_THROUGHPUT : configured;
        double delta = service.grid.getService(UpgradeTreeService.class).effects().amount(Kind.THROUGHPUT);
        return Math.clamp((int) (base + delta), 1, ProviderDispatch.MAX_THROUGHPUT);
    }

    /**
     * Computes the most additional recipe operations a CPU could dispatch to this grid this tick.
     *
     * @return sum of the providers' remaining capacity, limited to the integer range
     */
    public int availableProviderOperations() {
        long available = 0;
        for (var provider : providers) {
            available += provider.aeupgrade$remainingOperations();
        }
        return (int) Math.min(available, Integer.MAX_VALUE);
    }

    /**
     * Charges each recipe for the requested share of a work tick and returns the progress funded.
     * With rate {@code r}, granted step {@code s}, and batch size {@code n}, the bill is
     * {@code configuredDraw(r * accelerationCost) * n * s / r}.
     *
     * @param node             live assembler node
     * @param rate             adjusted progress per tick
     * @param accelerationCost AE2's AE per unit of progress
     * @param remaining        progress needed to complete the current cycle
     * @param ticks            game ticks to process
     * @param recipes          number of accepted recipe operations in the cycle
     * @return progress funded by the grid, capped by the remaining work
     */
    public static double advance(IGridNode node, double rate, double accelerationCost, double remaining, int ticks, int recipes) {
        if (rate <= 0 || ticks <= 0 || remaining <= 0) {
            return 0;
        }
        double step = Math.min(remaining, rate * ticks);
        var energy = node.getGrid().getEnergyService();
        double requested = energy(node, rate * accelerationCost, true) * recipes * (step / rate);
        if (requested == 0) {
            return energy.isNetworkPowered() ? step : 0;
        }
        double paid = energy.extractAEPower(requested, Actionable.MODULATE, PowerMultiplier.CONFIG);
        return DeviceTuningMath.equivalentEnergy(step, requested, paid);
    }

    /**
     * Resolves a tuning property that belongs to a molecular assembler.
     *
     * @param node    assembler node, or null if unavailable
     * @param setting property to resolve
     * @return configured value, or zero for an inherited default
     */
    private static double assemblerSetting(@Nullable IGridNode node, Setting setting) {
        if (!(node instanceof GridNode gridNode)) {
            return 0;
        }
        var service = activeService(gridNode);
        if (service == null) {
            return 0;
        }
        double base = service.resolve(node, DeviceChannelType.MOLECULAR_ASSEMBLER, setting);
        var tree = service.grid.getService(UpgradeTreeService.class);
        return setting == Setting.BATCH_SIZE ? Math.max(1, base) + tree.effects().amount(Kind.BATCH)
                : base + tree.purchases().speedBonus();
    }

    /**
     * Resolves tuning only for nodes attached to a valid controller grid.
     *
     * @param node internal AE2 node
     * @return the active tuning service, or null outside controller grids
     */
    private static @Nullable DeviceTuningService activeService(GridNode node) {
        var grid = node.getInternalGrid();
        if (grid == null || grid.getPathingService().getControllerState() != ControllerState.CONTROLLER_ONLINE) {
            return null;
        }
        return grid.getService(DeviceTuningService.class);
    }

    /**
     * Resolves local and network properties before falling back to the device family's global property.
     *
     * @param node    device node
     * @param type    device family
     * @param setting property to resolve
     * @return effective modifier, with zero for AE2 defaults
     */
    private double resolve(IGridNode node, DeviceChannelType type, Setting setting) {
        var profile = localProfiles.get(node);
        double local = profile == null ? Double.NaN : profile.get(setting);
        if (!Double.isNaN(local)) {
            return local;
        }
        double network = grid.getService(NetworkDeviceSettingsService.class).tuning(type, setting);
        if (!Double.isNaN(network)) {
            return network;
        }
        return globalTuning == null ? 0 : globalTuning.get(type, setting);
    }

    /**
     * Changes a local property, dropping the profile when it becomes empty.
     *
     * @param node    device whose profile is changed
     * @param setting property to update
     * @param value   configured value, or NaN to inherit
     * @return true when the device's saved configuration changed
     */
    public boolean setLocal(IGridNode node, Setting setting, double value) {
        var profile = localProfiles.computeIfAbsent(node, ignored -> new DeviceTuningProfile());
        boolean changed = profile.set(setting, value);
        if (profile.isEmpty()) {
            localProfiles.remove(node);
        }
        return changed;
    }

    /**
     * Schedules an energy cache refresh once AE2 has processed topology and configuration changes.
     */
    public void refreshIdlePower() {
        idleRefreshPending = true;
    }

    /**
     * Restores node properties before scheduling an idle power update.
     *
     * @param node      node joining the grid
     * @param savedData saved AE2 node data, or null for new nodes
     */
    @Override
    public void addNode(IGridNode node, @Nullable CompoundTag savedData) {
        var type = DeviceChannelType.of(node.getOwner());
        if (type == null) {
            return;
        }
        if (globalTuning == null) {
            globalTuning = GlobalDeviceTuning.get(node.getLevel().getServer());
        }
        if (savedData != null && savedData.contains(LOCAL_TAG, Tag.TAG_COMPOUND)) {
            var profile = DeviceTuningProfile.load(savedData.getCompound(LOCAL_TAG));
            if (!profile.isEmpty()) {
                localProfiles.put(node, profile);
            }
        }
        if (type == DeviceChannelType.PATTERN_PROVIDER
                && node.getService(ICraftingProvider.class) instanceof ProviderDispatch provider) {
            providers.add(provider);
        }
        refreshIdlePower();
    }

    /**
     * Drops node metadata after AE2 saves or transfers the node.
     *
     * @param node departing node
     */
    @Override
    public void removeNode(IGridNode node) {
        localProfiles.remove(node);
        if (node.getService(ICraftingProvider.class) instanceof ProviderDispatch provider) {
            providers.remove(provider);
        }
    }

    /**
     * Writes local properties alongside the node's other AE2 saved data.
     *
     * @param node      device being saved
     * @param savedData writable per-node compound
     */
    @Override
    public void saveNodeData(IGridNode node, CompoundTag savedData) {
        var profile = localProfiles.get(node);
        if (profile != null) {
            savedData.put(LOCAL_TAG, profile.save());
        }
    }

    /**
     * Tells AE2 to recompute cached power draw for supported devices after the grid settles.
     */
    @Override
    public void onServerEndTick() {
        if (!idleRefreshPending) {
            return;
        }
        idleRefreshPending = false;
        for (var node : grid.getNodes()) {
            if (DeviceChannelType.of(node.getOwner()) != null) {
                grid.postEvent(new GridPowerIdleChange(node));
            }
        }
    }
}
