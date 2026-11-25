package dev.sixik.aeupgrade.tree;

import java.util.List;

import appeng.api.networking.pathing.ControllerState;
import appeng.blockentity.networking.ControllerBlockEntity;
import dev.sixik.aeupgrade.api.UpgradeResearchPoints;
import dev.sixik.aeupgrade.platform.Platform;
import net.minecraft.ChatFormatting;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * A craftable research point redeemed on a network's ME controller.
 */
public class ResearchCertificateItem extends Item {
    /**
     * @param properties properties of the registered research certificate
     */
    public ResearchCertificateItem(Properties properties) {
        super(properties);
    }

    /**
     * Moves research to a neighboring controller block or drops it from the last block.
     *
     * @param level      server world in which the controller was removed
     * @param controller removed controller block entity, still available before AE2 detaches its node
     */
    public static void dropFromController(ServerLevel level, ControllerBlockEntity controller) {
        int count = UpgradeTreeService.removeControllerProfile(controller, adjacentController(level, controller));
        var certificate = Platform.researchCertificate();
        while (count > 0) {
            int size = Math.min(count, certificate.getDefaultMaxStackSize());
            Block.popResource(level, controller.getBlockPos(), new ItemStack(certificate, size));
            count -= size;
        }
    }

    /**
     * Finds a surviving face-connected block of the same controller structure.
     *
     * @param level      server world containing the structure
     * @param controller removed block at which to look for adjacent blocks
     * @return neighboring block with the lowest position, or null if the structure has no remaining blocks
     */
    private static @Nullable ControllerBlockEntity adjacentController(ServerLevel level, ControllerBlockEntity controller) {
        ControllerBlockEntity next = null;
        for (var side : Direction.values()) {
            var pos = controller.getBlockPos().relative(side);
            if ((next == null || pos.compareTo(next.getBlockPos()) < 0)
                    && level.getBlockState(pos).is(controller.getBlockState().getBlock())
                    && level.getBlockEntity(pos) instanceof ControllerBlockEntity neighbor && !neighbor.isRemoved()) {
                next = neighbor;
            }
        }
        return next;
    }

    /**
     * Redeems the held certificate before the controller handles the click.
     *
     * @param context block interaction and the hand holding the certificate
     * @return pass for other blocks, otherwise the result of handling the controller click
     */
    public InteractionResult redeem(UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof ControllerBlockEntity controller)) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide()) {
            return InteractionResult.SUCCESS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) {
            return InteractionResult.PASS;
        }
        var node = controller.getMainNode().getNode();
        if (node == null || node.getGrid().getPathingService().getControllerState() != ControllerState.CONTROLLER_ONLINE) {
            return feedback(player, "target", 0);
        }
        var grid = node.getGrid();
        var denial = grid.getService(UpgradeTreeService.class).researchDenial(player, node);
        if (denial != null) {
            return feedback(player, denial, 0);
        }
        int credited = UpgradeResearchPoints.award(grid, 1);
        if (credited > 0) {
            context.getItemInHand().consume(credited, player);
        }
        return feedback(player, credited == 0 ? "points_full" : "points_awarded", credited);
    }

    private static InteractionResult feedback(ServerPlayer player, String key, int amount) {
        player.displayClientMessage(Component.translatable("aeupgrade.tree.feedback." + key, amount), true);
        return InteractionResult.CONSUME;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> lines, TooltipFlag flags) {
        lines.add(Component.translatable("item.aeupgrade.research_certificate.tooltip").withStyle(ChatFormatting.GRAY));
    }
}
