package dev.sixik.aeupgrade.tree;

import java.util.List;
import java.util.Optional;

import dev.sixik.aeupgrade.AEUpgradeTree;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Synchronizes the prototype tree and its server-validated actions.
 */
public final class UpgradeTreePackets {
    /**
     * Action ID used to remove all purchased nodes with the configured refund.
     */
    public static final int RESET = -1;
    public static final int REFRESH = -2;

    /**
     * Keeps registration and payload definitions static.
     */
    private UpgradeTreePackets() {
    }

    /**
     * Published state of the targeted controller tree.
     *
     * @param controller   original controller location, including dimension
     * @param purchases    allocated node IDs, or empty for a rejected target
     * @param open         true when the operator explicitly opened the screen
     * @param message      localized feedback key, followed by optional plain details on a new line
     * @param revision     server graph revision
     * @param catalog      complete document on open or graph changes, otherwise empty
     * @param totalPoints  granted points including allocated points
     * @param refresh      true for periodic progress synchronization
     * @param allUnlocked  whether every configured node has been opened by an operator
     * @param tasksEnabled whether any research tasks are offered on this server
     */
    public record Snapshot(GlobalPos controller, Optional<List<Integer>> purchases, boolean open, String message,
                           long revision, String catalog, int totalPoints, boolean refresh, boolean allUnlocked,
                           boolean tasksEnabled)
            implements CustomPacketPayload {
        /**
         * Clientbound tree update ID.
         */
        public static final Type<Snapshot> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AEUpgradeTree.MODID, "tree_snapshot"));

        /**
         * Wire format with the target, server purchase state and action feedback.
         */
        private static final StreamCodec<io.netty.buffer.ByteBuf, Optional<List<Integer>>> PURCHASES =
                ByteBufCodecs.optional(ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(UpgradeCatalog.MAX_NODES)));
        public static final StreamCodec<RegistryFriendlyByteBuf, Snapshot> CODEC = StreamCodec.of(
                (buf, snapshot) -> {
                    GlobalPos.STREAM_CODEC.encode(buf, snapshot.controller());
                    PURCHASES.encode(buf, snapshot.purchases());
                    buf.writeBoolean(snapshot.open());
                    buf.writeUtf(snapshot.message());
                    buf.writeVarLong(snapshot.revision());
                    buf.writeUtf(snapshot.catalog(), UpgradeTreeConfig.MAX_BYTES);
                    buf.writeVarInt(snapshot.totalPoints());
                    buf.writeBoolean(snapshot.refresh());
                    buf.writeBoolean(snapshot.allUnlocked());
                    buf.writeBoolean(snapshot.tasksEnabled());
                },
                buf -> new Snapshot(GlobalPos.STREAM_CODEC.decode(buf), PURCHASES.decode(buf), buf.readBoolean(),
                        buf.readUtf(), buf.readVarLong(), buf.readUtf(UpgradeTreeConfig.MAX_BYTES), buf.readVarInt(),
                        buf.readBoolean(), buf.readBoolean(), buf.readBoolean()));

        /**
         * {@inheritDoc}
         */
        @Override
        public Type<Snapshot> type() {
            return TYPE;
        }
    }

    /**
     * Requests a purchase or respec for the controller shown in the tree screen.
     *
     * @param controller        controller location shown in the open screen
     * @param nodeId            zero-based node ID, or {@link #RESET}
     * @param revision          graph revision shown when the action was requested
     * @param confirmedBranches newly closed branch IDs acknowledged by the player, in dependency order
     * @param refund            acknowledged node removals and returned points, when requesting a respec
     */
    public record Action(GlobalPos controller, int nodeId, long revision, List<Integer> confirmedBranches,
                         Optional<RefundQuote> refund)
            implements CustomPacketPayload {
        /**
         * Serverbound tree action ID.
         */
        public static final Type<Action> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AEUpgradeTree.MODID, "tree_action"));

        /**
         * Wire format containing the expected controller and the requested action.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, Action> CODEC = StreamCodec.composite(
                GlobalPos.STREAM_CODEC, Action::controller,
                ByteBufCodecs.VAR_INT, Action::nodeId,
                ByteBufCodecs.VAR_LONG, Action::revision,
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(UpgradeCatalog.MAX_NODES)), Action::confirmedBranches,
                ByteBufCodecs.optional(RefundQuote.CODEC), Action::refund,
                Action::new);

        public Action(GlobalPos controller, int nodeId, long revision, List<Integer> confirmedBranches) {
            this(controller, nodeId, revision, confirmedBranches, Optional.empty());
        }

        /**
         * {@inheritDoc}
         */
        @Override
        public Type<Action> type() {
            return TYPE;
        }
    }

    /**
     * Refund preview accepted by the player. The server checks it against the current allocation.
     */
    public record RefundQuote(List<Integer> removedIds, int returned) {
        private static final StreamCodec<io.netty.buffer.ByteBuf, RefundQuote> CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT.apply(ByteBufCodecs.list(UpgradeCatalog.MAX_NODES)), RefundQuote::removedIds,
                ByteBufCodecs.VAR_INT, RefundQuote::returned,
                RefundQuote::new);
    }

    /**
     * Submits a full editor draft for server-side validation.
     *
     * @param controller controller that opened the editor
     * @param revision   graph revision from which the draft was made
     * @param document   proposed JSON definition
     */
    public record Save(GlobalPos controller, long revision, String document) implements CustomPacketPayload {
        /**
         * Serverbound editor action ID.
         */
        public static final Type<Save> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AEUpgradeTree.MODID, "tree_save"));
        /**
         * Bounded wire format for the editor's proposed document.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, Save> CODEC = StreamCodec.composite(
                GlobalPos.STREAM_CODEC, Save::controller,
                ByteBufCodecs.VAR_LONG, Save::revision,
                ByteBufCodecs.stringUtf8(UpgradeTreeConfig.MAX_BYTES), Save::document,
                Save::new);

        /**
         * {@inheritDoc}
         */
        @Override
        public Type<Save> type() {
            return TYPE;
        }
    }
}
