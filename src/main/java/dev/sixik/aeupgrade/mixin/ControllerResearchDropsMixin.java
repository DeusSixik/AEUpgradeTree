package dev.sixik.aeupgrade.mixin;

import appeng.block.AEBaseEntityBlock;
import appeng.blockentity.networking.ControllerBlockEntity;
import dev.sixik.aeupgrade.tree.ResearchCertificateItem;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Transfers or recovers research before AE2 removes the controller's node.
 */
@Mixin(AEBaseEntityBlock.class)
abstract class ControllerResearchDropsMixin {
    @Inject(method = "onRemove", at = @At("HEAD"))
    private void aeupgrade$dropResearch(BlockState state, Level level, BlockPos pos, BlockState replacement,
                                        boolean moving, CallbackInfo callback) {
        if (!moving && !replacement.is(state.getBlock()) && level instanceof ServerLevel server
                && server.getBlockEntity(pos) instanceof ControllerBlockEntity controller) {
            ResearchCertificateItem.dropFromController(server, controller);
        }
    }
}
