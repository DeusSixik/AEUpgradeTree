package dev.sixik.aeupgrade.mixin;

import appeng.api.networking.IGrid;
import appeng.blockentity.networking.ControllerBlockEntity;
import appeng.menu.AEBaseMenu;
import appeng.menu.me.networktool.NetworkStatusMenu;
import dev.sixik.aeupgrade.command.UpgradeTreeCommands;
import dev.sixik.aeupgrade.network.ControllerOverview;
import dev.sixik.aeupgrade.platform.PlatformNetwork;
import dev.sixik.aeupgrade.tree.ControllerUpgradeMenu;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Connects the tree and extra diagnostics to the controller's native menu.
 */
@Mixin(value = NetworkStatusMenu.class, remap = false)
public abstract class ControllerUpgradeMenuMixin extends AEBaseMenu implements ControllerUpgradeMenu {
    /**
     * Menu action identifier shared by the client and server.
     */
    @Unique
    private static final String AEUPGRADE_OPEN_TREE = "aeupgrade_open_tree";

    /**
     * Grid supplying the native network status update.
     */
    @Shadow
    private IGrid grid;

    /**
     * Last sent or received diagnostics for this menu.
     */
    @Unique
    private ControllerOverview aeupgrade$overview = ControllerOverview.EMPTY;

    /**
     * Mirrors AEBaseMenu's constructor for the inherited action methods.
     *
     * @param id        container ID
     * @param inventory inventory of the player with the open menu
     * @param host      controller hosting this menu
     */
    protected ControllerUpgradeMenuMixin(int id, Inventory inventory, ControllerBlockEntity host) {
        super(NetworkStatusMenu.CONTROLLER_TYPE, id, inventory, host);
    }

    /**
     * Adds the action to the controller constructor only. The network tool has a separate constructor.
     *
     * @param id        container ID
     * @param inventory inventory of the player with the open menu
     * @param host      controller hosting this menu
     * @param callback  constructor injection context
     */
    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lappeng/blockentity/networking/ControllerBlockEntity;)V", at = @At("RETURN"))
    private void aeupgrade$registerTree(int id, Inventory inventory, ControllerBlockEntity host, CallbackInfo callback) {
        registerClientAction(AEUPGRADE_OPEN_TREE, this::aeupgrade$openTree);
    }

    /**
     * Syncs changed diagnostics on the same update interval as AE2's machine groups.
     *
     * @param callback menu broadcast injection context
     */
    @Inject(method = "broadcastChanges", at = @At(value = "INVOKE",
            target = "Lappeng/menu/me/networktool/NetworkStatus;fromGrid(Lappeng/api/networking/IGrid;)Lappeng/menu/me/networktool/NetworkStatus;"))
    private void aeupgrade$broadcastOverview(CallbackInfo callback) {
        if (getType() == NetworkStatusMenu.CONTROLLER_TYPE && getPlayer() instanceof ServerPlayer player) {
            var overview = ControllerOverview.collect(containerId, grid);
            if (!overview.equals(aeupgrade$overview)) {
                aeupgrade$overview = overview;
                PlatformNetwork.sendToPlayer(player, overview);
            }
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public ControllerOverview aeupgrade$overview() {
        return aeupgrade$overview;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void aeupgrade$acceptOverview(ControllerOverview overview) {
        if (getType() == NetworkStatusMenu.CONTROLLER_TYPE && overview.containerId() == containerId) {
            aeupgrade$overview = overview;
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void aeupgrade$openTree() {
        if (getType() != NetworkStatusMenu.CONTROLLER_TYPE) {
            return;
        }
        if (isClientSide()) {
            sendClientAction(AEUPGRADE_OPEN_TREE);
        } else if (getPlayer() instanceof ServerPlayer player && player.containerMenu == this && stillValid(player)
                && getBlockEntity() instanceof ControllerBlockEntity controller) {
            UpgradeTreeCommands.openFromMenu(player, controller);
        }
    }
}
