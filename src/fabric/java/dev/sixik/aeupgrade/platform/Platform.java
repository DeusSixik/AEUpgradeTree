package dev.sixik.aeupgrade.platform;

import java.nio.file.Path;

import appeng.api.networking.IGrid;
import appeng.blockentity.networking.ControllerBlockEntity;
import dev.sixik.aeupgrade.AEUpgradeTree;
import dev.sixik.aeupgrade.api.UpgradeEffectsChangedEvent;
import dev.sixik.aeupgrade.tree.ResearchCertificateItem;
import dev.sixik.aeupgrade.tree.UpgradeEffects;
import dev.sixik.aeupgrade.tree.UpgradeTreeService;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import org.jetbrains.annotations.Nullable;

/** Binds common research operations to Fabric. */
public final class Platform {
    /** Persistent controller profile used when a successor has no live AE2 node yet. */
    private static final AttachmentType<CompoundTag> PENDING_PROFILE = AttachmentRegistry.createPersistent(
            ResourceLocation.parse(UpgradeTreeService.PENDING_PROFILE_TAG), CompoundTag.CODEC);
    /** Registry entry for the craftable research certificate. */
    private static final ResourceLocation CERTIFICATE_ID = ResourceLocation.fromNamespaceAndPath(AEUpgradeTree.MODID,
            "research_certificate");

    private Platform() {
    }

    /** Registers the certificate and lets it intercept the click before the controller menu opens. */
    static void registerCertificate() {
        var certificate = Registry.register(BuiltInRegistries.ITEM, CERTIFICATE_ID,
                new ResearchCertificateItem(new Item.Properties()));
        UseBlockCallback.EVENT.register((player, level, hand, hit) ->
                !player.isSpectator() && player.getItemInHand(hand).is(certificate)
                        ? certificate.redeem(new UseOnContext(player, hand, hit)) : InteractionResult.PASS);
    }

    /** @return the registered research certificate */
    public static ResearchCertificateItem researchCertificate() {
        return (ResearchCertificateItem) BuiltInRegistries.ITEM.get(CERTIFICATE_ID);
    }

    /** @return the loader's configuration directory */
    public static Path configDirectory() {
        return FabricLoader.getInstance().getConfigDir().toAbsolutePath().normalize();
    }

    /**
     * @param controller controller waiting for its AE2 node
     * @return a transferred profile or null
     */
    public static @Nullable CompoundTag pendingProfile(ControllerBlockEntity controller) {
        return controller.getAttached(PENDING_PROFILE);
    }

    /**
     * @param controller controller hosting the transfer
     * @param profile transferred profile, or null to clear it
     */
    public static void setPendingProfile(ControllerBlockEntity controller, @Nullable CompoundTag profile) {
        controller.setAttached(PENDING_PROFILE, profile);
    }

    /**
     * @param grid affected AE2 network
     * @param previous last published effects
     * @param current new effects
     */
    public static void effectsChanged(IGrid grid, UpgradeEffects previous, UpgradeEffects current) {
        UpgradeEffectsChangedEvent.EVENT.invoker().onChanged(new UpgradeEffectsChangedEvent(grid, previous, current));
    }
}
