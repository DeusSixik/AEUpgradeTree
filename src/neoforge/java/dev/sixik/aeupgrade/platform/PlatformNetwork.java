package dev.sixik.aeupgrade.platform;

import dev.sixik.aeupgrade.client.tree.UpgradeTreeScreen;
import dev.sixik.aeupgrade.client.tree.editor.UpgradeTreeEditor;
import dev.sixik.aeupgrade.command.UpgradeTreeCommands;
import dev.sixik.aeupgrade.network.ControllerOverview;
import dev.sixik.aeupgrade.tree.ControllerUpgradeMenu;
import dev.sixik.aeupgrade.tree.UpgradeTreePackets.Action;
import dev.sixik.aeupgrade.tree.UpgradeTreePackets.Save;
import dev.sixik.aeupgrade.tree.UpgradeTreePackets.Snapshot;
import dev.sixik.aeupgrade.tree.research.ResearchTaskPackets;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import org.jetbrains.annotations.Nullable;

/** Sends AEUpgradeTree and AE2 play payloads on NeoForge. */
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
        PacketDistributor.sendToPlayer(player, payload);
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

    /**
     * Registers the play-phase messages on their owning threads.
     *
     * @param event NeoForge payload registration event
     */
    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        var tree = registrar.versioned("15");
        tree.playToClient(Snapshot.TYPE, Snapshot.CODEC, (payload, context) -> UpgradeTreeScreen.receive(payload));
        tree.playToClient(ResearchTaskPackets.Board.TYPE, ResearchTaskPackets.Board.CODEC,
                (payload, context) -> UpgradeTreeScreen.receiveTasks(payload));
        tree.playToServer(ResearchTaskPackets.Request.TYPE, ResearchTaskPackets.Request.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                UpgradeTreeCommands.handleTask(player, payload);
            }
        });
        tree.playToClient(ResearchTaskPackets.EditResponse.TYPE, ResearchTaskPackets.EditResponse.CODEC,
                (payload, context) -> UpgradeTreeEditor.receiveTasks(payload));
        tree.playToServer(ResearchTaskPackets.EditRequest.TYPE, ResearchTaskPackets.EditRequest.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                UpgradeTreeCommands.editTasks(player, payload);
            }
        });
        registrar.versioned("2").playToClient(ControllerOverview.TYPE, ControllerOverview.CODEC, (payload, context) -> {
            if (context.player().containerMenu instanceof ControllerUpgradeMenu controller) {
                controller.aeupgrade$acceptOverview(payload);
            }
        });
        tree.playToServer(Action.TYPE, Action.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                UpgradeTreeCommands.handle(player, payload);
            }
        });
        tree.playToServer(Save.TYPE, Save.CODEC, (payload, context) -> {
            if (context.player() instanceof ServerPlayer player) {
                UpgradeTreeCommands.save(player, payload);
            }
        });
    }
}
