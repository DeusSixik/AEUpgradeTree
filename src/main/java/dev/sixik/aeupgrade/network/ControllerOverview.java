package dev.sixik.aeupgrade.network;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import appeng.api.networking.GridFlags;
import appeng.api.networking.IGrid;
import appeng.api.networking.IGridNode;
import appeng.api.stacks.AEItemKey;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import dev.sixik.aeupgrade.AEUpgradeTree;
import dev.sixik.aeupgrade.channel.ChannelCostRules;
import dev.sixik.aeupgrade.channel.ChannelCostService;
import dev.sixik.aeupgrade.channel.DeviceChannelType;
import dev.sixik.aeupgrade.limit.DeviceLimitService;
import dev.sixik.aeupgrade.mixin.GridNodePowerAccessor;
import dev.sixik.aeupgrade.pattern.PatternSlotRange;
import dev.sixik.aeupgrade.pattern.ProviderPatternSlots;
import dev.sixik.aeupgrade.stock.InterfaceSlotRange;
import dev.sixik.aeupgrade.stock.InterfaceSlotService;
import dev.sixik.aeupgrade.stock.InterfaceSlots;
import dev.sixik.aeupgrade.tuning.DeviceTuningService;
import dev.sixik.aeupgrade.tuning.ProviderDispatch;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Extra statistics synced to the native ME Controller menu.
 */
public record ControllerOverview(int containerId, List<Limit> limits,
                                 List<MachineDetails> machines) implements CustomPacketPayload {
    /**
     * Data used until a status packet reaches the open menu.
     */
    public static final ControllerOverview EMPTY = new ControllerOverview(-1, List.of(), List.of());
    /**
     * Payload ID for a controller overview update.
     */
    public static final Type<ControllerOverview> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AEUpgradeTree.MODID, "controller_overview"));
    /**
     * Wire format for an immutable menu snapshot.
     */
    public static final StreamCodec<RegistryFriendlyByteBuf, ControllerOverview> CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, ControllerOverview::containerId,
            Limit.CODEC.apply(ByteBufCodecs.list()), ControllerOverview::limits,
            MachineDetails.CODEC.apply(ByteBufCodecs.list()), ControllerOverview::machines,
            ControllerOverview::new);

    /**
     * Collects finite limits and effective device tuning, using AE2's item and channel-state grouping.
     *
     * @param containerId active ME Controller menu ID
     * @param grid        same grid used by AE2's network status update
     * @return immutable extra statistics for this update
     */
    public static ControllerOverview collect(int containerId, IGrid grid) {
        var service = grid.getService(DeviceLimitService.class);
        var tuning = grid.getService(DeviceTuningService.class);
        var limits = new ArrayList<Limit>();
        for (var family : DeviceChannelType.values()) {
            int maximum = service.effectiveLimit(family);
            if (maximum >= 0) {
                limits.add(new Limit(family.id(), service.installed(family), maximum));
            }
        }

        var byGroup = new HashMap<MachineKey, MachineStats>();
        service.forEachDevice(node -> {
            var icon = node.getVisualRepresentation();
            if (icon == null || !(node instanceof GridNodePowerAccessor original)) {
                return;
            }
            var group = byGroup.computeIfAbsent(new MachineKey(icon, !node.meetsChannelRequirements()),
                    ignored -> new MachineStats());
            collectTuning(node, group);
            double idleDelta = node.getIdlePowerUsage() - original.aeupgrade$baseIdlePower();
            var craft = Optional.<CraftRange>empty();
            if (DeviceChannelType.of(node.getOwner()) == DeviceChannelType.MOLECULAR_ASSEMBLER) {
                var modifiers = tuning.craftEnergy(node);
                craft = Optional.of(new CraftRange(modifiers.percent(), modifiers.percent(), modifiers.flat(), modifiers.flat()));
            }
            var change = new EnergyChange(Math.max(0, idleDelta), Math.max(0, -idleDelta), craft);
            group.energy = group.energy.merge(change);
        });
        boolean hasCraftChanges = byGroup.values().stream()
                .anyMatch(group -> group.energy.crafting().filter(CraftRange::changed).isPresent());
        var machines = byGroup.entrySet().stream()
                .map(entry -> new MachineDetails(entry.getKey().item(), entry.getKey().missingChannel(),
                        entry.getValue().energy, entry.getValue().visibleStats()))
                .filter(entry -> !entry.stats().isEmpty() || entry.change().changed()
                        || hasCraftChanges && entry.change().crafting().isPresent())
                .toList();
        return new ControllerOverview(containerId, List.copyOf(limits), machines);
    }

    /**
     * Records the settings used by the same runtime hooks that operate this device.
     *
     * @param node  live supported device on this grid
     * @param group aggregate for this item's channel-state group
     */
    private static void collectTuning(IGridNode node, MachineStats group) {
        int channelCost = ChannelCostService.configuredCost(node);
        int defaultCost = node.hasFlag(GridFlags.REQUIRE_CHANNEL) ? 1 : 0;
        group.add(Stat.CHANNEL_COST, channelCost == ChannelCostRules.INHERIT ? defaultCost : channelCost, defaultCost);

        var owner = node.getOwner();
        if (owner instanceof MolecularAssemblerBlockEntity assembler) {
            int installed = assembler.getInstalledUpgrades(AEItems.SPEED_CARD);
            group.add(Stat.BATCH, DeviceTuningService.batchSize(node), 1);
            group.add(Stat.SPEED, DeviceTuningService.speedPercent(node), 0);
            group.add(Stat.EXTRA_SPEED_CARDS, DeviceTuningService.speedCards(node, installed) - installed, 0);
        } else if (owner instanceof PatternProviderLogicHost provider) {
            int cap = DeviceTuningService.providerThroughput(node);
            group.add(Stat.THROUGHPUT, cap, cap == 0 ? 0 : ProviderDispatch.DEFAULT_THROUGHPUT);
            if (ProviderPatternSlots.supports(owner) && provider.getLogic() instanceof ProviderPatternSlots slots) {
                group.add(Stat.PATTERN_SLOTS, slots.aeupgrade$patternCapacity(), PatternSlotRange.ROW_SIZE);
            }
        } else if (InterfaceSlots.supports(owner)) {
            group.add(Stat.INTERFACE_SLOTS, InterfaceSlotService.capacityFor(node), InterfaceSlotRange.DEFAULT_CAPACITY);
        }
    }

    /**
     * @return aggregate energy changes, preserving increases and savings separately
     */
    public EnergyChange energy() {
        var total = EnergyChange.NONE;
        for (var machine : machines) {
            total = total.merge(machine.change());
        }
        return total;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Type<ControllerOverview> type() {
        return TYPE;
    }

    /**
     * Finite quota for one family, including devices installed beyond the limit.
     */
    public record Limit(String family, int installed, int maximum) {
        /**
         * Wire format for the stable device family ID and the two counts.
         */
        static final StreamCodec<ByteBuf, Limit> CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, Limit::family, ByteBufCodecs.VAR_INT, Limit::installed,
                ByteBufCodecs.VAR_INT, Limit::maximum, Limit::new);
    }

    /**
     * Matches AE2's status groups by displayed item and availability of their channel.
     */
    private record MachineKey(AEItemKey item, boolean missingChannel) {
    }

    /**
     * Energy and device settings for one visible machine group.
     */
    public record MachineDetails(AEItemKey item, boolean missingChannel, EnergyChange change, List<StatChange> stats) {
        /**
         * Wire format of an AE2 item key in a registry-aware packet.
         */
        private static final StreamCodec<RegistryFriendlyByteBuf, AEItemKey> ITEM_CODEC = StreamCodec.of(
                (buf, item) -> item.writeToPacket(buf), AEItemKey::fromPacket);
        /**
         * Wire format for a changed setting and its endpoints.
         */
        private static final StreamCodec<RegistryFriendlyByteBuf, StatChange> STAT_CODEC = StreamCodec.composite(
                StreamCodec.of((buf, stat) -> buf.writeEnum(stat), buf -> buf.readEnum(Stat.class)), StatChange::stat,
                ByteBufCodecs.DOUBLE, StatChange::min, ByteBufCodecs.DOUBLE, StatChange::max, StatChange::new);
        /**
         * Wire format of the group identity, energy and changed settings.
         */
        static final StreamCodec<RegistryFriendlyByteBuf, MachineDetails> CODEC = StreamCodec.composite(
                ITEM_CODEC, MachineDetails::item, ByteBufCodecs.BOOL, MachineDetails::missingChannel,
                EnergyCodecs.CHANGE, MachineDetails::change,
                STAT_CODEC.apply(ByteBufCodecs.list()), MachineDetails::stats, MachineDetails::new);
    }

    /**
     * Display order and translation suffixes for the device settings.
     */
    public enum Stat {
        /**
         * Recipe operations in an assembler cycle.
         */
        BATCH("batch"),
        /**
         * Signed speed modifier applied after the card level, in percent.
         */
        SPEED("speed"),
        /**
         * Additional acceleration cards currently provided by virtual tuning.
         */
        EXTRA_SPEED_CARDS("speed_cards"),
        /**
         * Recipe operations dispatched by a provider per tick.
         */
        THROUGHPUT("throughput"),
        /**
         * Enabled pattern slots per provider.
         */
        PATTERN_SLOTS("pattern_slots"),
        /**
         * Enabled configuration and storage pairs per interface.
         */
        INTERFACE_SLOTS("interface_slots"),
        /**
         * Required network channels per device.
         */
        CHANNEL_COST("channel_cost");

        /**
         * Suffix of the localized stat label.
         */
        private final String key;

        /**
         * @param key suffix of the localized stat label
         */
        Stat(String key) {
            this.key = key;
        }

        /**
         * @return suffix of the localized stat label
         */
        public String key() {
            return key;
        }
    }

    /**
     * Range of effective values for a setting changed on at least one device.
     */
    public record StatChange(Stat stat, double min, double max) {
    }

    /**
     * Aggregates devices that share one icon in the AE2 machine table.
     */
    static final class MachineStats {
        /**
         * Energy changes for all devices in the group.
         */
        private EnergyChange energy = EnergyChange.NONE;
        /**
         * Observed values for the settings that apply to this device family.
         */
        private final EnumMap<Stat, StatRange> stats = new EnumMap<>(Stat.class);

        /**
         * Includes one device in the stat range even if the device uses the default.
         *
         * @param stat         device setting and its unit
         * @param value        effective value for this device
         * @param defaultValue value without AEUpgradeTree tuning for this device
         */
        void add(Stat stat, double value, double defaultValue) {
            var range = stats.get(stat);
            if (range == null) {
                stats.put(stat, new StatRange(value, value, value != defaultValue));
            } else {
                stats.put(stat, new StatRange(Math.min(range.min(), value), Math.max(range.max(), value),
                        range.changed() || value != defaultValue));
            }
        }

        /**
         * @return only changed settings, with the full observed range in display order
         */
        List<StatChange> visibleStats() {
            var changes = new ArrayList<StatChange>();
            stats.forEach((stat, range) -> {
                if (range.changed()) {
                    changes.add(new StatChange(stat, range.min(), range.max()));
                }
            });
            return List.copyOf(changes);
        }
    }

    /**
     * Group endpoints and whether any value differs from its device's untuned setting.
     */
    record StatRange(double min, double max, boolean changed) {
    }

    /**
     * Wire formats for the energy records used in menu snapshots.
     */
    private static final class EnergyCodecs {
        /**
         * Wire format for both signed modifier ranges.
         */
        private static final StreamCodec<ByteBuf, CraftRange> CRAFT_RANGE = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, CraftRange::minPercent, ByteBufCodecs.DOUBLE, CraftRange::maxPercent,
                ByteBufCodecs.DOUBLE, CraftRange::minFlat, ByteBufCodecs.DOUBLE, CraftRange::maxFlat, CraftRange::new);
        /**
         * Wire format of idle deltas and optional crafting range.
         */
        private static final StreamCodec<ByteBuf, EnergyChange> CHANGE = StreamCodec.composite(
                ByteBufCodecs.DOUBLE, EnergyChange::added, ByteBufCodecs.DOUBLE, EnergyChange::saved,
                CRAFT_RANGE.apply(ByteBufCodecs::optional), EnergyChange::crafting, EnergyChange::new);
    }

    /**
     * Idle deltas in AE/t and the optional range of crafting modifiers within a group.
     */
    public record EnergyChange(double added, double saved, Optional<CraftRange> crafting) {
        /**
         * Empty energy change used to start the network total.
         */
        static final EnergyChange NONE = new EnergyChange(0, 0, Optional.empty());

        /**
         * @return true when the group has a nonzero idle delta or crafting modifier
         */
        public boolean changed() {
            return added > 0 || saved > 0 || crafting.filter(CraftRange::changed).isPresent();
        }

        /**
         * Combines groups, avoiding cancellation between added and saved idle draw.
         *
         * @param other separate group of devices
         * @return combined totals and crafting range
         */
        EnergyChange merge(EnergyChange other) {
            var range = crafting.isEmpty() ? other.crafting
                    : other.crafting.isEmpty() ? crafting : Optional.of(crafting.get().merge(other.crafting.get()));
            return new EnergyChange(added + other.added, saved + other.saved, range);
        }
    }

    /**
     * Minimum and maximum crafting percentages and flat AE/t across the applicable assemblers.
     */
    public record CraftRange(double minPercent, double maxPercent, double minFlat, double maxFlat) {
        /**
         * @return true if at least one modifier differs from its base value
         */
        public boolean changed() {
            return minPercent != 0 || maxPercent != 0 || minFlat != 0 || maxFlat != 0;
        }

        /**
         * Expands the range to cover a second group of assemblers.
         *
         * @param other crafting modifier range for the second group
         * @return minimum and maximum for each modifier across both groups
         */
        CraftRange merge(CraftRange other) {
            return new CraftRange(Math.min(minPercent, other.minPercent), Math.max(maxPercent, other.maxPercent),
                    Math.min(minFlat, other.minFlat), Math.max(maxFlat, other.maxFlat));
        }
    }
}
