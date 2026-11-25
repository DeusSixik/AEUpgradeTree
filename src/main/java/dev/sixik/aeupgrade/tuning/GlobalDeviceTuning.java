package dev.sixik.aeupgrade.tuning;

import java.util.EnumMap;
import java.util.Map;

import dev.sixik.aeupgrade.channel.DeviceChannelType;
import dev.sixik.aeupgrade.tuning.DeviceTuningProfile.Setting;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * World data for energy and speed overrides applied to device families across all dimensions.
 */
public final class GlobalDeviceTuning extends SavedData {
    /**
     * Stable filename and factory for world-wide device overrides.
     */
    private static final Factory<GlobalDeviceTuning> FACTORY =
            new Factory<>(GlobalDeviceTuning::new, GlobalDeviceTuning::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    /**
     * Families with at least one explicit property override.
     */
    private final Map<DeviceChannelType, DeviceTuningProfile> profiles = new EnumMap<>(DeviceChannelType.class);

    /**
     * Returns the tuning data from the server's overworld save.
     *
     * @param server owning Minecraft server
     * @return the live global tuning data
     */
    public static GlobalDeviceTuning get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, "aeupgrade_device_tuning");
    }

    /**
     * Reads a global value for a family.
     *
     * @param type    device family
     * @param setting property to read
     * @return configured value, or zero for AE2's default behavior
     */
    public double get(DeviceChannelType type, Setting setting) {
        var profile = profiles.get(type);
        double value = profile == null ? Double.NaN : profile.get(setting);
        return Double.isNaN(value) ? 0 : value;
    }

    /**
     * Sets a global property and marks the world data dirty if it changed.
     *
     * @param type    device family
     * @param setting property to change
     * @param value   configured value, or NaN to restore the AE2 default
     * @return true if a global setting changed
     */
    public boolean set(DeviceChannelType type, Setting setting, double value) {
        var profile = profiles.computeIfAbsent(type, ignored -> new DeviceTuningProfile());
        boolean changed = profile.set(setting, value);
        if (profile.isEmpty()) {
            profiles.remove(type);
        }
        if (changed) {
            setDirty();
        }
        return changed;
    }

    /**
     * Serializes global overrides keyed by the stable AE2 device family IDs.
     *
     * @param tag        writable world data compound
     * @param registries registry lookup supplied by Minecraft
     * @return the populated compound
     */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        profiles.forEach((type, profile) -> tag.put(type.id(), profile.save()));
        return tag;
    }

    /**
     * Loads persisted profiles from the world save.
     *
     * @param tag        stored global tuning data
     * @param registries registry lookup supplied by Minecraft
     * @return restored world data
     */
    private static GlobalDeviceTuning load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new GlobalDeviceTuning();
        for (var type : DeviceChannelType.values()) {
            if (tag.contains(type.id(), Tag.TAG_COMPOUND)) {
                var profile = DeviceTuningProfile.load(tag.getCompound(type.id()));
                if (!profile.isEmpty()) {
                    data.profiles.put(type, profile);
                }
            }
        }
        return data;
    }
}
