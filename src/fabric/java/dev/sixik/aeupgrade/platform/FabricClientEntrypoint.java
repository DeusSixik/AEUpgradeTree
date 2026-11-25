package dev.sixik.aeupgrade.platform;

import dev.sixik.aeupgrade.client.tree.UpgradeTreeScreen;
import dev.sixik.aeupgrade.client.tree.editor.UpgradeTreeEditor;
import dev.sixik.aeupgrade.network.ControllerOverview;
import dev.sixik.aeupgrade.tree.ControllerUpgradeMenu;
import dev.sixik.aeupgrade.tree.UpgradeTreePackets.Snapshot;
import dev.sixik.aeupgrade.tree.research.ResearchTaskPackets;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

/** Connects incoming Fabric packets and client ticks to the research screens. */
public final class FabricClientEntrypoint implements ClientModInitializer {
    /** Installs play-phase handlers after their common codecs have been registered. */
    @Override
    public void onInitializeClient() {
        ClientPlayNetworking.registerGlobalReceiver(Snapshot.TYPE, (payload, context) -> UpgradeTreeScreen.receive(payload));
        ClientPlayNetworking.registerGlobalReceiver(ResearchTaskPackets.Board.TYPE,
                (payload, context) -> UpgradeTreeScreen.receiveTasks(payload));
        ClientPlayNetworking.registerGlobalReceiver(ResearchTaskPackets.EditResponse.TYPE,
                (payload, context) -> UpgradeTreeEditor.receiveTasks(payload));
        ClientPlayNetworking.registerGlobalReceiver(ControllerOverview.TYPE, (payload, context) -> {
            if (context.player().containerMenu instanceof ControllerUpgradeMenu controller) {
                controller.aeupgrade$acceptOverview(payload);
            }
        });
        ClientTickEvents.END_CLIENT_TICK.register(client -> UpgradeTreeScreen.tick());
    }
}
