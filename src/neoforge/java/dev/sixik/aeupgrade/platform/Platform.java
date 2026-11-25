package dev.sixik.aeupgrade.platform;

import java.nio.file.Path;

import appeng.api.networking.IGrid;
import appeng.blockentity.networking.ControllerBlockEntity;
import dev.sixik.aeupgrade.AEUpgradeTree;
import dev.sixik.aeupgrade.api.UpgradeEffectsChangedEvent;
import dev.sixik.aeupgrade.tree.ResearchCertificateItem;
import dev.sixik.aeupgrade.tree.UpgradeEffects;
import dev.sixik.aeupgrade.tree.UpgradeTreeService;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;
import org.jetbrains.annotations.Nullable;

/** Binds common research operations to NeoForge. */
public final class Platform {
    /** Deferred item registry for this add-on. */
    private static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(AEUpgradeTree.MODID);
    /** Registered item used for point redemption and controller drops. */
    private static final DeferredItem<Certificate> CERTIFICATE = ITEMS.registerItem("research_certificate", Certificate::new);

    private Platform() {
    }

    /** @param bus the mod bus for item registration */
    static void registerCertificate(IEventBus bus) {
        ITEMS.register(bus);
    }

    /** @return the registered research certificate */
    public static ResearchCertificateItem researchCertificate() {
        return CERTIFICATE.get();
    }

    /** @return the loader's configuration directory */
    public static Path configDirectory() {
        return FMLPaths.CONFIGDIR.get();
    }

    /**
     * @param controller controller waiting for its AE2 node
     * @return a transferred profile or null
     */
    public static @Nullable CompoundTag pendingProfile(ControllerBlockEntity controller) {
        return controller.getPersistentData().get(UpgradeTreeService.PENDING_PROFILE_TAG) instanceof CompoundTag pending
                ? pending : null;
    }

    /**
     * @param controller controller hosting the transfer
     * @param profile transferred profile, or null to clear it
     */
    public static void setPendingProfile(ControllerBlockEntity controller, @Nullable CompoundTag profile) {
        var data = controller.getPersistentData();
        if (profile == null) {
            data.remove(UpgradeTreeService.PENDING_PROFILE_TAG);
        } else {
            data.put(UpgradeTreeService.PENDING_PROFILE_TAG, profile);
        }
    }

    /**
     * @param grid affected AE2 network
     * @param previous last published effects
     * @param current new effects
     */
    public static void effectsChanged(IGrid grid, UpgradeEffects previous, UpgradeEffects current) {
        NeoForge.EVENT_BUS.post(new UpgradeEffectsChangedEvent(grid, previous, current));
    }

    /** Lets NeoForge intercept the click before the controller menu opens. */
    private static final class Certificate extends ResearchCertificateItem {
        /** @param properties default item properties */
        private Certificate(Properties properties) {
            super(properties);
        }

        /** {@inheritDoc} */
        @Override
        public InteractionResult onItemUseFirst(ItemStack stack, UseOnContext context) {
            return redeem(context);
        }
    }
}
