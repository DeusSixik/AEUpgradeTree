package dev.sixik.aeupgrade.limit;

import java.nio.file.Path;

import com.electronwill.nightconfig.core.file.CommentedFileConfig;
import com.electronwill.nightconfig.core.io.WritingException;
import com.electronwill.nightconfig.core.io.WritingMode;
import com.electronwill.nightconfig.toml.TomlFormat;
import com.mojang.logging.LogUtils;
import dev.sixik.aeupgrade.channel.DeviceChannelType;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

/** Loads and saves the existing device limit TOML schema on Fabric. */
public final class DeviceLimitStore {
    /** Reports malformed and unreadable configs. */
    private static final Logger LOGGER = LogUtils.getLogger();
    /** Path of the assembler quota in the TOML document. */
    private static final String ASSEMBLERS = "deviceLimits.molecularAssemblers";
    /** Path of the Pattern Provider quota in the TOML document. */
    private static final String PATTERN_PROVIDERS = "deviceLimits.patternProviders";
    /** Path of the ME Interface quota in the TOML document. */
    private static final String INTERFACES = "deviceLimits.interfaces";
    /** Open config for the active server, or null when it is unavailable. */
    private static @Nullable CommentedFileConfig active;

    private DeviceLimitStore() {
    }

    /**
     * Loads server limits, creating the defaults for a new installation.
     *
     * @param directory existing loader config directory
     */
    public static void load(Path directory) {
        unload();
        var path = directory.resolve(DeviceLimitConfig.FILE_NAME);
        var config = CommentedFileConfig.builder(path, TomlFormat.instance())
                .sync().writingMode(WritingMode.REPLACE_ATOMIC).build();
        try {
            config.load();
            boolean hasDefaults = config.add(ASSEMBLERS, DeviceLimitConfig.UNLIMITED);
            hasDefaults |= config.add(PATTERN_PROVIDERS, DeviceLimitConfig.UNLIMITED);
            hasDefaults |= config.add(INTERFACES, DeviceLimitConfig.UNLIMITED);
            if (hasDefaults) {
                config.save();
            }
            publish(config);
            active = config;
        } catch (RuntimeException failure) {
            config.close();
            LOGGER.error("Could not load {}. Using unlimited device counts.", path, failure);
        }
    }

    /** Closes the active config and returns to the default policy. */
    public static void unload() {
        if (active != null) {
            active.close();
            active = null;
        }
        DeviceLimitConfig.unload();
    }

    /**
     * @param type supported device family
     * @param limit maximum operating devices per grid, or -1 for unlimited
     * @throws IllegalStateException if the server configuration is unavailable
     * @throws WritingException if the update cannot be persisted
     */
    static void setLimit(DeviceChannelType type, int limit) {
        var config = active;
        if (config == null) {
            throw new IllegalStateException("The AEUpgradeTree server configuration is not loaded.");
        }
        String key = switch (type) {
            case MOLECULAR_ASSEMBLER -> ASSEMBLERS;
            case PATTERN_PROVIDER -> PATTERN_PROVIDERS;
            case INTERFACE -> INTERFACES;
        };
        Object previous = config.set(key, limit);
        try {
            config.save();
        } catch (WritingException failure) {
            config.set(key, previous);
            throw failure;
        }
        publish(config);
    }

    /** @param config parsed device limit document */
    private static void publish(CommentedFileConfig config) {
        DeviceLimitConfig.update(limit(config, ASSEMBLERS), limit(config, PATTERN_PROVIDERS), limit(config, INTERFACES));
    }

    /**
     * @param config parsed device limit document
     * @param key TOML path of the quota
     * @return a valid quota or -1 when the value is invalid
     */
    private static int limit(CommentedFileConfig config, String key) {
        Object value = config.get(key);
        if (value instanceof Integer integer && integer >= DeviceLimitConfig.UNLIMITED) {
            return integer;
        }
        if (value instanceof Long number && number >= DeviceLimitConfig.UNLIMITED && number <= Integer.MAX_VALUE) {
            return number.intValue();
        }
        return DeviceLimitConfig.UNLIMITED;
    }
}
