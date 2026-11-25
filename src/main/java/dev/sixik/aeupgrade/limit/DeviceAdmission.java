package dev.sixik.aeupgrade.limit;

/**
 * Determines whether a device fits in the slots for its family.
 */
final class DeviceAdmission {
    /**
     * Keeps the admission rule static.
     */
    private DeviceAdmission() {
    }

    /**
     * Finds the zero-based position where devices stop fitting in their family quota.
     *
     * @param count connected devices of the same family
     * @param limit number of allowed devices, or -1 for unlimited
     * @return first blocked position, or count if all devices fit
     */
    static int firstBlockedIndex(int count, int limit) {
        return limit < 0 ? count : Math.min(limit, count);
    }

    /**
     * Adds the network bonus to a finite server limit, capped at {@link Integer#MAX_VALUE}.
     *
     * @param base  configured number of operating devices, or -1 for unlimited
     * @param bonus non-negative number of extra device slots on the grid
     * @return effective device limit, or -1 for unlimited
     */
    static int limitWithBonus(int base, int bonus) {
        return base == DeviceLimitConfig.UNLIMITED ? base : adjustSlots(base, bonus);
    }

    /**
     * Adds a signed slot adjustment and clamps the sum to the range [0, Integer.MAX_VALUE].
     *
     * @param slots current non-negative number of device slots
     * @param delta signed change in device slots
     * @return number of slots after the adjustment
     */
    static int adjustSlots(int slots, int delta) {
        return Math.clamp((long) slots + delta, 0, Integer.MAX_VALUE);
    }
}
