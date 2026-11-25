package dev.sixik.aeupgrade.limit;

/**
 * One device family's fixed network limit or its adjustment to the server default.
 *
 * @param overridden whether the value is a fixed limit
 * @param value      fixed limit of -1 or more, or non-negative extra slots when inheriting
 */
public record NetworkDeviceLimit(boolean overridden, int value) {
    /**
     * Network setting that inherits the server default with zero extra slots.
     */
    static final NetworkDeviceLimit INHERITED = new NetworkDeviceLimit(false, 0);

    /**
     * Validates the setting's slot range for its mode.
     *
     * @throws IllegalArgumentException if the value is below the minimum for its mode
     */
    public NetworkDeviceLimit {
        if (value < (overridden ? DeviceLimitConfig.UNLIMITED : 0)) {
            throw new IllegalArgumentException("Invalid network device limit: " + value);
        }
    }

    /**
     * Resolves the maximum device count for this family.
     *
     * @param base server default in devices, or -1 for unlimited
     * @return fixed network limit when overridden, otherwise the server default with any bonus
     */
    public int resolve(int base) {
        return overridden ? value : DeviceAdmission.limitWithBonus(base, value);
    }

    /**
     * Adds a signed slot adjustment to the current fixed limit or bonus.
     *
     * @param delta slots to add or remove
     * @return updated setting, or this setting for an unlimited override or an unchanged value
     */
    NetworkDeviceLimit adjust(int delta) {
        if (value == DeviceLimitConfig.UNLIMITED) {
            return this;
        }
        int adjusted = DeviceAdmission.adjustSlots(value, delta);
        return adjusted == value ? this : new NetworkDeviceLimit(overridden, adjusted);
    }
}
