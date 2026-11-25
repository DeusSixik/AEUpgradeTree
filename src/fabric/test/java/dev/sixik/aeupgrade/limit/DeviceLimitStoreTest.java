package dev.sixik.aeupgrade.limit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import dev.sixik.aeupgrade.channel.DeviceChannelType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** Verifies Fabric reads and persists the existing server limit settings. */
class DeviceLimitStoreTest {
    /** Temporary directory for the test server's config. */
    @TempDir
    Path directory;

    /** Discards the test server's loaded limits. */
    @AfterEach
    void closeConfig() {
        DeviceLimitStore.unload();
    }

    /**
     * Preserves the other limits when a single family is updated and the server restarts.
     *
     * @throws IOException if the test fixture cannot be written
     */
    @Test
    void setLimit_existingConfig_preservesOtherLimitsAfterReload() throws IOException {
        Files.writeString(directory.resolve(DeviceLimitConfig.FILE_NAME), """
                [deviceLimits]
                molecularAssemblers = 32
                patternProviders = 12
                interfaces = 0
                """);
        DeviceLimitStore.load(directory);
        assertEquals(32, DeviceLimitConfig.getLimit(DeviceChannelType.MOLECULAR_ASSEMBLER));

        DeviceLimitConfig.setLimit(DeviceChannelType.MOLECULAR_ASSEMBLER, 10);
        assertEquals(10, DeviceLimitConfig.getLimit(DeviceChannelType.MOLECULAR_ASSEMBLER));
        DeviceLimitStore.unload();
        assertEquals(-1, DeviceLimitConfig.getLimit(DeviceChannelType.MOLECULAR_ASSEMBLER));

        DeviceLimitStore.load(directory);
        assertEquals(10, DeviceLimitConfig.getLimit(DeviceChannelType.MOLECULAR_ASSEMBLER));
        assertEquals(12, DeviceLimitConfig.getLimit(DeviceChannelType.PATTERN_PROVIDER));
        assertEquals(0, DeviceLimitConfig.getLimit(DeviceChannelType.INTERFACE));
    }

    /** Rejects an invalid quota and preserves the unlimited defaults on disk. */
    @Test
    void setLimit_belowUnlimited_rejectsUpdate() {
        DeviceLimitStore.load(directory);
        assertThrows(IllegalArgumentException.class,
                () -> DeviceLimitConfig.setLimit(DeviceChannelType.INTERFACE, -2));

        DeviceLimitStore.load(directory);
        assertEquals(-1, DeviceLimitConfig.getLimit(DeviceChannelType.INTERFACE));
    }
}
