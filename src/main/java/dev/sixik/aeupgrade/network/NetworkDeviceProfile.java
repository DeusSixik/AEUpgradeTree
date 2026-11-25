package dev.sixik.aeupgrade.network;

import java.util.EnumMap;

import dev.sixik.aeupgrade.channel.ChannelCostRules;
import dev.sixik.aeupgrade.channel.DeviceChannelType;
import dev.sixik.aeupgrade.tuning.DeviceTuningProfile;
import dev.sixik.aeupgrade.tuning.DeviceTuningProfile.Setting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Operator overrides for the device families on a single controller.
 */
final class NetworkDeviceProfile {
    /**
     * NBT key for the optional number of channels used by a device family.
     */
    private static final String CHANNEL_COST = "channel_cost";

    /**
     * NBT key for the optional energy, speed and throughput properties of a family.
     */
    private static final String TUNING = "tuning";

    /**
     * Explicit channel costs by family, measured in channels per device.
     */
    private final EnumMap<DeviceChannelType, Integer> channelCosts = new EnumMap<>(DeviceChannelType.class);

    /**
     * Explicit tuning properties by family.
     */
    private final EnumMap<DeviceChannelType, DeviceTuningProfile> tuning = new EnumMap<>(DeviceChannelType.class);

    /**
     * Reads the network's configured channel cost.
     *
     * @param type device family
     * @return number of channels, or {@link ChannelCostRules#INHERIT}
     */
    int channelCost(DeviceChannelType type) {
        return channelCosts.getOrDefault(type, ChannelCostRules.INHERIT);
    }

    /**
     * Stores or clears the network's configured channel cost.
     *
     * @param type device family
     * @param cost non-negative channel count, or {@link ChannelCostRules#INHERIT} to reset
     */
    void setChannelCost(DeviceChannelType type, int cost) {
        if (cost == ChannelCostRules.INHERIT) {
            channelCosts.remove(type);
        } else {
            channelCosts.put(type, cost);
        }
    }

    /**
     * Reads the network override for an individual property.
     *
     * @param type    device family
     * @param setting tuning property
     * @return configured value or NaN when inherited
     */
    double tuning(DeviceChannelType type, Setting setting) {
        var profile = tuning.get(type);
        return profile == null ? Double.NaN : profile.get(setting);
    }

    /**
     * Stores or clears the network override for an individual property.
     *
     * @param type    device family
     * @param setting tuning property
     * @param value   configured value or NaN to reset
     */
    void setTuning(DeviceChannelType type, Setting setting, double value) {
        var profile = tuning.computeIfAbsent(type, ignored -> new DeviceTuningProfile());
        profile.set(setting, value);
        if (profile.isEmpty()) {
            tuning.remove(type);
        }
    }

    /**
     * @return persisted overrides grouped by device family
     */
    CompoundTag save() {
        var data = new CompoundTag();
        for (var type : DeviceChannelType.values()) {
            var cost = channelCosts.get(type);
            var profile = tuning.get(type);
            if (cost == null && profile == null) {
                continue;
            }
            var family = new CompoundTag();
            if (cost != null) {
                family.putInt(CHANNEL_COST, cost);
            }
            if (profile != null) {
                family.put(TUNING, profile.save());
            }
            data.put(type.id(), family);
        }
        return data;
    }

    /**
     * Restores supported overrides from controller node data.
     *
     * @param data persisted overrides grouped by device family
     * @return restored controller profile
     */
    static NetworkDeviceProfile load(CompoundTag data) {
        var restored = new NetworkDeviceProfile();
        for (var type : DeviceChannelType.values()) {
            var family = data.getCompound(type.id());
            if (family.contains(CHANNEL_COST, Tag.TAG_INT) && family.getInt(CHANNEL_COST) >= 0) {
                restored.channelCosts.put(type, family.getInt(CHANNEL_COST));
            }
            var profile = DeviceTuningProfile.load(family.getCompound(TUNING));
            if (!profile.isEmpty()) {
                restored.tuning.put(type, profile);
            }
        }
        return restored;
    }
}
