package dev.sixik.aeupgrade;

import appeng.api.networking.GridHelper;
import appeng.api.networking.GridServices;
import appeng.api.networking.events.GridControllerChange;
import dev.sixik.aeupgrade.channel.ChannelCapacityService;
import dev.sixik.aeupgrade.channel.ChannelCostService;
import dev.sixik.aeupgrade.limit.DeviceLimitService;
import dev.sixik.aeupgrade.network.NetworkDeviceSettingsService;
import dev.sixik.aeupgrade.pattern.PatternSlotService;
import dev.sixik.aeupgrade.stock.InterfaceSlotService;
import dev.sixik.aeupgrade.tree.UpgradeTreeService;
import dev.sixik.aeupgrade.tuning.DeviceTuningService;

/**
 * Common initialization for the NeoForge and Fabric entry points.
 */
public final class AEUpgradeTree {
    /**
     * Stable mod and resource ID used by existing worlds and server configs.
     */
    public static final String MODID = "aeupgrade";

    /**
     * Keeps shared initialization static.
     */
    private AEUpgradeTree() {
    }

    /**
     * Registers the add-on's grid services before AE2 creates grids.
     */
    public static void registerGridServices() {
        GridServices.register(DeviceLimitService.class, DeviceLimitService.class);
        GridServices.register(PatternSlotService.class, PatternSlotService.class);
        GridServices.register(InterfaceSlotService.class, InterfaceSlotService.class);
        GridServices.register(ChannelCapacityService.class, ChannelCapacityService.class);
        GridServices.register(NetworkDeviceSettingsService.class, NetworkDeviceSettingsService.class);
        GridServices.register(UpgradeTreeService.class, UpgradeTreeService.class);
        GridServices.register(ChannelCostService.class, ChannelCostService.class);
        GridServices.register(DeviceTuningService.class, DeviceTuningService.class);
        GridHelper.addGridServiceEventHandler(GridControllerChange.class, DeviceTuningService.class,
                (service, event) -> service.refreshIdlePower());
    }
}
