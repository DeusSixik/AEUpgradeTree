package dev.sixik.aeupgrade.platform;

import dev.sixik.aeupgrade.AEUpgradeTree;
import dev.sixik.aeupgrade.client.tree.UpgradeTreeScreen;
import dev.sixik.aeupgrade.command.ChannelCommands;
import dev.sixik.aeupgrade.limit.DeviceLimitConfig;
import dev.sixik.aeupgrade.limit.DeviceLimitStore;
import dev.sixik.aeupgrade.tree.UpgradeTreeConfig;
import dev.sixik.aeupgrade.tree.research.ResearchTasks;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.server.ServerAboutToStartEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

/** Registers AEUpgradeTree hooks on NeoForge. */
@Mod(AEUpgradeTree.MODID)
public final class NeoForgeEntrypoint {
    /**
     * @param modBus mod lifecycle bus
     * @param container this mod's config owner
     */
    public NeoForgeEntrypoint(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, DeviceLimitStore.SPEC, DeviceLimitConfig.FILE_NAME);
        modBus.addListener(ModConfigEvent.Loading.class, DeviceLimitStore::update);
        modBus.addListener(ModConfigEvent.Reloading.class, DeviceLimitStore::update);
        modBus.addListener(DeviceLimitStore::unload);
        modBus.addListener(PlatformNetwork::register);
        Platform.registerCertificate(modBus);
        AEUpgradeTree.registerGridServices();
        NeoForge.EVENT_BUS.addListener((RegisterCommandsEvent event) -> ChannelCommands.register(event.getDispatcher()));
        NeoForge.EVENT_BUS.addListener((ServerAboutToStartEvent event) -> {
            UpgradeTreeConfig.load();
            ResearchTasks.load();
        });
        NeoForge.EVENT_BUS.addListener((ServerStoppedEvent event) -> {
            UpgradeTreeConfig.unload();
            ResearchTasks.unload();
        });
    }

    /** Registers the research screen refresh on physical clients. */
    @EventBusSubscriber(modid = AEUpgradeTree.MODID, value = Dist.CLIENT)
    public static final class Client {
        /** @param event end of the client tick */
        @SubscribeEvent
        public static void tick(ClientTickEvent.Post event) {
            UpgradeTreeScreen.tick();
        }
    }
}
