package dev.sixik.aeupgrade.limit;

import static dev.sixik.aeupgrade.limit.DeviceLimitConfig.UNLIMITED;

import com.electronwill.nightconfig.core.io.WritingException;
import dev.sixik.aeupgrade.channel.DeviceChannelType;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/** Persists device limits through NeoForge's server config. */
public final class DeviceLimitStore {
    /**
     * Number of molecular assemblers permitted per grid.
     */
    private static final ModConfigSpec.IntValue ASSEMBLERS;

    /**
     * Number of Pattern Provider blocks and panels permitted per grid.
     */
    private static final ModConfigSpec.IntValue PATTERN_PROVIDERS;

    /**
     * Number of ME Interface blocks and panels permitted per grid.
     */
    private static final ModConfigSpec.IntValue INTERFACES;

    /**
     * Specification registered as a NeoForge server config.
     */
    public static final ModConfigSpec SPEC;

    static {
        var builder = new ModConfigSpec.Builder();
        builder.comment(
                        "Base device limits per connected ME grid, including grids without a controller.",
                        "Devices above the limit stay placed and resume when a slot becomes available.",
                        "Use -1 for unlimited or 0 for no base slots. Controllers can set a fixed limit or add slots.",
                        "Blocks and panels share their family limit.")
                .push("deviceLimits");
        ASSEMBLERS = builder.defineInRange("molecularAssemblers", UNLIMITED, UNLIMITED, Integer.MAX_VALUE);
        PATTERN_PROVIDERS = builder.defineInRange("patternProviders", UNLIMITED, UNLIMITED, Integer.MAX_VALUE);
        INTERFACES = builder.defineInRange("interfaces", UNLIMITED, UNLIMITED, Integer.MAX_VALUE);
        builder.pop();
        SPEC = builder.build();
    }

    /**
     * Publishes the loaded server values as one policy.
     *
     * @param event NeoForge's load or reload event
     */
    public static void update(ModConfigEvent event) {
        if (event.getConfig().getSpec() == SPEC) {
            DeviceLimitConfig.update(ASSEMBLERS.get(), PATTERN_PROVIDERS.get(), INTERFACES.get());
        }
    }

    /**
     * Restores defaults when the server config is unloaded.
     *
     * @param event NeoForge's config unload event
     */
    public static void unload(ModConfigEvent.Unloading event) {
        if (event.getConfig().getSpec() == SPEC) {
            DeviceLimitConfig.unload();
        }
    }

    /**
     * Saves a device limit from the server thread. NeoForge dispatches a reload event after saving.
     *
     * @param type  supported device family
     * @param limit maximum operating devices per grid, or -1 for unlimited
     * @throws IllegalStateException    if the server configuration is not loaded
     * @throws WritingException         if NeoForge cannot save the server configuration
     */
    static void setLimit(DeviceChannelType type, int limit) {
        if (!SPEC.isLoaded()) {
            throw new IllegalStateException("The AEUpgradeTree server configuration is not loaded.");
        }

        var configValue = switch (type) {
            case MOLECULAR_ASSEMBLER -> ASSEMBLERS;
            case PATTERN_PROVIDER -> PATTERN_PROVIDERS;
            case INTERFACE -> INTERFACES;
        };
        int previous = configValue.getRaw();
        configValue.set(limit);
        try {
            SPEC.save();
        } catch (WritingException failure) {
            configValue.set(previous);
            throw failure;
        }
    }

    private DeviceLimitStore() {
    }
}
