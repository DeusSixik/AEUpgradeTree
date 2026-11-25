package dev.sixik.aeupgrade.tuning;

import java.util.EnumMap;
import java.util.Map;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/**
 * Optional per-property overrides for one device or one device family.
 */
public final class DeviceTuningProfile {
    /**
     * Individual tuning properties with stable keys in NBT.
     */
    public enum Setting {
        /**
         * Idle percentage change relative to AE2.
         */
        IDLE_PERCENT("idle_percent"),
        /**
         * Additional idle AE per tick.
         */
        IDLE_FLAT("idle_flat"),
        /**
         * Assembly percentage change relative to AE2's crafting draw.
         */
        CRAFT_PERCENT("craft_percent"),
        /**
         * Additional AE per crafting tick.
         */
        CRAFT_FLAT("craft_flat"),
        /**
         * Signed percentage change to progress after speed cards.
         */
        CRAFT_SPEED_PERCENT("craft_speed_percent"),
        /**
         * Maximum recipe operations gathered in one assembly cycle.
         */
        BATCH_SIZE("batch_size"),
        /**
         * Recipe operations a Pattern Provider can accept per server tick.
         */
        PROVIDER_THROUGHPUT("provider_throughput"),
        /**
         * Number of virtual speed cards.
         */
        VIRTUAL_SPEED_CARDS("virtual_speed_cards");

        /**
         * Stable NBT property name.
         */
        private final String key;

        /**
         * Binds a property to its world data key.
         *
         * @param key stable NBT name
         */
        Setting(String key) {
            this.key = key;
        }
    }

    /**
     * Maximum magnitude that can be configured for flat and percentage energy modifiers.
     */
    public static final double MAX_ENERGY_MODIFIER = Float.MAX_VALUE;

    /**
     * Overrides explicitly present in this profile. A set value of zero remains an override.
     */
    private final Map<Setting, Double> overrides = new EnumMap<>(Setting.class);

    /**
     * Reads one override without changing the profile.
     *
     * @param setting property to look up
     * @return configured value, or NaN when inherited
     */
    public double get(Setting setting) {
        var value = overrides.get(setting);
        return value == null ? Double.NaN : value;
    }

    /**
     * Sets a property, or clears it so it can inherit an outer value.
     *
     * @param setting property to change
     * @param value   configured value, or NaN to reset
     * @return true if the override changed
     */
    public boolean set(Setting setting, double value) {
        if (Double.isNaN(value)) {
            return overrides.remove(setting) != null;
        }
        if (Double.compare(get(setting), value) == 0) {
            return false;
        }
        overrides.put(setting, value);
        return true;
    }

    /**
     * Reports whether the record still contains settings to persist.
     *
     * @return true if every property is inherited
     */
    public boolean isEmpty() {
        return overrides.isEmpty();
    }

    /**
     * Writes all explicitly configured properties to a new compound.
     *
     * @return persisted overrides
     */
    public CompoundTag save() {
        var tag = new CompoundTag();
        overrides.forEach((setting, value) -> tag.putDouble(setting.key, value));
        return tag;
    }

    /**
     * Restores supported properties from a persisted profile.
     *
     * @param tag compound containing optional overrides
     * @return profile containing the valid values
     */
    public static DeviceTuningProfile load(CompoundTag tag) {
        var profile = new DeviceTuningProfile();
        for (var setting : Setting.values()) {
            if (tag.contains(setting.key, Tag.TAG_ANY_NUMERIC)) {
                double value = tag.getDouble(setting.key);
                boolean valid = switch (setting) {
                    case VIRTUAL_SPEED_CARDS ->
                            value >= 0 && value <= DeviceTuningMath.MAX_SPEED_CARDS && value == (int) value;
                    case BATCH_SIZE -> value >= 1 && value <= DeviceTuningMath.MAX_BATCH_SIZE && value == (int) value;
                    case PROVIDER_THROUGHPUT ->
                            value >= 1 && value <= ProviderDispatch.MAX_THROUGHPUT && value == (int) value;
                    case CRAFT_SPEED_PERCENT -> value >= -100 && value <= MAX_ENERGY_MODIFIER;
                    default -> Math.abs(value) <= MAX_ENERGY_MODIFIER;
                };
                if (valid) {
                    profile.overrides.put(setting, value);
                }
            }
        }
        return profile;
    }

}
