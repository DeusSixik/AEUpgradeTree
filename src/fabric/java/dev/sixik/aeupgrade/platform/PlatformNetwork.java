package dev.sixik.aeupgrade.platform;

import appeng.platform.network.PacketDistributor;
import dev.sixik.aeupgrade.command.UpgradeTreeCommands;
import dev.sixik.aeupgrade.network.ControllerOverview;
import dev.sixik.aeupgrade.tree.UpgradeTreePackets.Action;
import dev.sixik.aeupgrade.tree.UpgradeTreePackets.Save;
import dev.sixik.aeupgrade.tree.UpgradeTreePackets.Snapshot;
import dev.sixik.aeupgrade.tree.research.ResearchTaskPackets;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

/** Sends AEUpgradeTree and AE2 play payloads on Fabric. */
public final class PlatformNetwork {
    private PlatformNetwork() {
    }

    /** @param payload registered serverbound payload */
    public static void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }

    /**
     * @param player recipient with an active client connection
     * @param payload registered clientbound payload
     */
    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        ServerPlayNetworking.send(player, payload);
    }

    /**
     * @param level dimension containing the animation
     * @param excluded optional recipient to exclude
     * @param x animation X coordinate
     * @param y animation Y coordinate
     * @param z animation Z coordinate
     * @param radius visibility radius in blocks
     * @param payload registered clientbound payload
     */
    public static void sendToPlayersNear(ServerLevel level, @Nullable ServerPlayer excluded, double x, double y, double z,
                                         double radius, CustomPacketPayload payload) {
        PacketDistributor.sendToPlayersNear(level, excluded, x, y, z, radius, payload);
    }

    /** Registers payload codecs on both sides and dispatches server actions on the server thread. */
    static void register() {
        var clientbound = PayloadTypeRegistry.playS2C();
        clientbound.register(Snapshot.TYPE, Snapshot.CODEC);
        clientbound.register(ResearchTaskPackets.Board.TYPE, ResearchTaskPackets.Board.CODEC);
        clientbound.register(ResearchTaskPackets.EditResponse.TYPE, ResearchTaskPackets.EditResponse.CODEC);
        clientbound.register(ControllerOverview.TYPE, ControllerOverview.CODEC);

        var serverbound = PayloadTypeRegistry.playC2S();
        serverbound.register(Action.TYPE, Action.CODEC);
        serverbound.register(Save.TYPE, Save.CODEC);
        serverbound.register(ResearchTaskPackets.Request.TYPE, ResearchTaskPackets.Request.CODEC);
        serverbound.register(ResearchTaskPackets.EditRequest.TYPE, ResearchTaskPackets.EditRequest.CODEC);
        ServerPlayNetworking.registerGlobalReceiver(Action.TYPE,
                (payload, context) -> UpgradeTreeCommands.handle(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(Save.TYPE,
                (payload, context) -> UpgradeTreeCommands.save(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ResearchTaskPackets.Request.TYPE,
                (payload, context) -> UpgradeTreeCommands.handleTask(context.player(), payload));
        ServerPlayNetworking.registerGlobalReceiver(ResearchTaskPackets.EditRequest.TYPE,
                (payload, context) -> UpgradeTreeCommands.editTasks(context.player(), payload));
    }
}
