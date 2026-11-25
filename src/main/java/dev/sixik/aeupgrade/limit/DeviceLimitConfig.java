package dev.sixik.aeupgrade.limit;

import com.electronwill.nightconfig.core.io.WritingException;
import dev.sixik.aeupgrade.channel.DeviceChannelType;

/**
 * Server configuration for maximum online device counts per ME grid.
 */
public final class DeviceLimitConfig {
    /**
     * Configuration filename for the server policy.
     */
    public static final String FILE_NAME = "aeupgrade-server.toml";

    /**
     * Value representing an unlimited number of devices.
     */
    public static final int UNLIMITED = -1;

    /**
     * Initial and unloaded policy with no device limits.
     */
    private static final Limits DEFAULTS = new Limits(UNLIMITED, UNLIMITED, UNLIMITED);

    /**
     * Immutable policy published after the loader reads the config.
     */
    private static volatile Limits configuredLimits = DEFAULTS;

    /**
     * Keeps configuration registration static.
     */
    private DeviceLimitConfig() {
    }

    /**
     * Publishes the loaded server values as one policy.
     *
     * @param assemblers molecular assembler limit per grid
     * @param patternProviders Pattern Provider limit per grid
     * @param interfaces ME Interface limit per grid
     */
    static void update(int assemblers, int patternProviders, int interfaces) {
        configuredLimits = new Limits(assemblers, patternProviders, interfaces);
    }

    /**
     * Restores defaults when the server config is unloaded.
     */
    static void unload() {
        configuredLimits = DEFAULTS;
    }

    /**
     * Reads the current server limit for one device family.
     *
     * @param type supported device family
     * @return maximum operating devices per grid, or -1 for unlimited
     */
    public static int getLimit(DeviceChannelType type) {
        return current().forType(type);
    }

    /**
     * Saves a device limit from the server thread.
     *
     * @param type  supported device family
     * @param limit maximum operating devices per grid, or -1 for unlimited
     * @throws IllegalArgumentException if the limit is below -1
     * @throws IllegalStateException    if the server configuration is not loaded
     * @throws WritingException         if the server configuration cannot be saved
     */
    public static void setLimit(DeviceChannelType type, int limit) {
        if (limit < UNLIMITED) {
            throw new IllegalArgumentException("Device limit must be -1 or greater.");
        }
        DeviceLimitStore.setLimit(type, limit);
    }

    /**
     * Takes the current immutable policy for a server tick.
     *
     * @return current configured device limits
     */
    static Limits current() {
        return configuredLimits;
    }

    /**
     * Server limits grouped by device family.
     *
     * @param assemblers       molecular assembler count per grid, or -1
     * @param patternProviders Pattern Provider count per grid, or -1
     * @param interfaces       ME Interface count per grid, or -1
     */
    record Limits(int assemblers, int patternProviders, int interfaces) {
        /**
         * Finds the quota for a supported device family.
         *
         * @param type supported device family
         * @return configured number of operating devices, or -1 for unlimited
         */
        int forType(DeviceChannelType type) {
            return switch (type) {
                case MOLECULAR_ASSEMBLER -> assemblers;
                case PATTERN_PROVIDER -> patternProviders;
                case INTERFACE -> interfaces;
            };
        }
    }
}
