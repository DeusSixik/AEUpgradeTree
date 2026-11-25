package dev.sixik.aeupgrade.channel;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.datafix.DataFixTypes;
import net.minecraft.world.level.saveddata.SavedData;

/**
 * Stores global device costs in the overworld's saved data for one server world.
 */
public final class GlobalChannelCosts extends SavedData {
    /**
     * File key for the saved type overrides.
     */
    private static final String DATA_NAME = "aeupgrade_channel_costs";

    /**
     * Constructs or reads the overrides for a world.
     */
    private static final Factory<GlobalChannelCosts> FACTORY =
            new Factory<>(GlobalChannelCosts::new, GlobalChannelCosts::load, DataFixTypes.SAVED_DATA_COMMAND_STORAGE);

    /**
     * Configured non-negative channel costs indexed by device family.
     */
    private final Map<DeviceChannelType, Integer> costs = new EnumMap<>(DeviceChannelType.class);

    /**
     * Creates an empty set of overrides for a new world.
     */
    private GlobalChannelCosts() {
    }

    /**
     * Returns the single set of global overrides shared by the server's dimensions.
     *
     * @param server server whose world stores the overrides
     * @return the live world data
     */
    public static GlobalChannelCosts get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FACTORY, DATA_NAME);
    }

    /**
     * Reads a type's configured channel cost.
     *
     * @param type device family
     * @return non-negative channel cost, or {@link ChannelCostRules#INHERIT}
     */
    public int cost(DeviceChannelType type) {
        return costs.getOrDefault(type, ChannelCostRules.INHERIT);
    }

    /**
     * Changes the override for one device family.
     *
     * @param type device family
     * @param cost non-negative channel cost, or {@link ChannelCostRules#INHERIT} to clear the override
     * @return true if the saved setting changed
     */
    public boolean setCost(DeviceChannelType type, int cost) {
        if (cost(type) == cost) {
            return false;
        }

        if (cost == ChannelCostRules.INHERIT) {
            costs.remove(type);
        } else {
            costs.put(type, cost);
        }
        setDirty();
        return true;
    }

    /**
     * Writes each configured cost to the world's save.
     *
     * @param tag        writable data compound
     * @param registries registry lookup supplied by Minecraft
     * @return the populated compound
     */
    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        costs.forEach((type, cost) -> tag.putInt(type.id(), cost));
        return tag;
    }

    /**
     * Reads the configured costs from the world's save.
     *
     * @param tag        stored type costs
     * @param registries registry lookup supplied by Minecraft
     * @return the restored data with any absent types left inherited
     */
    private static GlobalChannelCosts load(CompoundTag tag, HolderLookup.Provider registries) {
        var data = new GlobalChannelCosts();
        for (var type : DeviceChannelType.values()) {
            if (tag.contains(type.id(), Tag.TAG_INT) && tag.getInt(type.id()) >= 0) {
                data.costs.put(type, tag.getInt(type.id()));
            }
        }
        return data;
    }
}
