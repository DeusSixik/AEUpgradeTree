package dev.sixik.aeupgrade.platform;

import dev.sixik.aeupgrade.AEUpgradeTree;
import dev.sixik.aeupgrade.command.ChannelCommands;
import dev.sixik.aeupgrade.limit.DeviceLimitStore;
import dev.sixik.aeupgrade.tree.UpgradeTreeConfig;
import dev.sixik.aeupgrade.tree.research.ResearchTasks;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;

/** Registers AEUpgradeTree hooks on Fabric. */
public final class FabricEntrypoint implements ModInitializer {
    /** Registers common services, packets and server lifecycle callbacks. */
    @Override
    public void onInitialize() {
        Platform.registerCertificate();
        AEUpgradeTree.registerGridServices();
        PlatformNetwork.register();
        CommandRegistrationCallback.EVENT.register((dispatcher, registry, environment) -> ChannelCommands.register(dispatcher));
        ServerLifecycleEvents.SERVER_STARTING.register(server -> {
            DeviceLimitStore.load(Platform.configDirectory());
            UpgradeTreeConfig.load();
            ResearchTasks.load();
        });
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> {
            ResearchTasks.unload();
            UpgradeTreeConfig.unload();
            DeviceLimitStore.unload();
        });
    }
}
