package dev.sixik.aeupgrade.tree.research;

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
 * Requests and responses specific to the research task tab.
 */
public final class ResearchTaskPackets {
    /**
     * Bounded wire format of the optional item registry ID.
     */
    private static final StreamCodec<io.netty.buffer.ByteBuf, Optional<String>> ITEM =
            ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(ResearchTask.MAX_ID));
    /**
     * Bounded wire format of an optional task description.
     */
    private static final StreamCodec<io.netty.buffer.ByteBuf, Optional<String>> DESCRIPTION =
            ByteBufCodecs.optional(ByteBufCodecs.stringUtf8(ResearchTask.MAX_DESCRIPTION));
    /**
     * Bounded list of items owed as part of a task reward.
     */
    private static final StreamCodec<io.netty.buffer.ByteBuf, List<ResearchItemReward>> REWARDS =
            StreamCodec.composite(ByteBufCodecs.stringUtf8(ResearchTask.MAX_ID), ResearchItemReward::item,
                            ByteBufCodecs.VAR_INT, ResearchItemReward::amount, ResearchItemReward::new)
                    .apply(ByteBufCodecs.list(ResearchTask.MAX_ITEM_REWARDS));
    /**
     * Bounded wire format for the tasks offered to a single network.
     */
    private static final StreamCodec<RegistryFriendlyByteBuf, List<ResearchTaskStatus>> STATUSES =
            StreamCodec.<RegistryFriendlyByteBuf, ResearchTaskStatus>of((buf, status) -> {
                        buf.writeUtf(status.id(), ResearchTask.MAX_ID);
                        buf.writeEnum(status.type());
                        ITEM.encode(buf, Optional.ofNullable(status.item()));
                        buf.writeUtf(status.title(), ResearchTask.MAX_TITLE);
                        DESCRIPTION.encode(buf, Optional.ofNullable(status.description()));
                        buf.writeVarInt(status.amount());
                        buf.writeVarInt(status.points());
                        buf.writeVarInt(status.progress());
                        buf.writeBoolean(status.claimed());
                        buf.writeBoolean(status.rewardFits());
                        REWARDS.encode(buf, status.itemRewards());
                    }, buf -> new ResearchTaskStatus(buf.readUtf(ResearchTask.MAX_ID), buf.readEnum(ResearchTask.Type.class),
                            ITEM.decode(buf).orElse(null), buf.readUtf(ResearchTask.MAX_TITLE), DESCRIPTION.decode(buf).orElse(null),
                            buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readBoolean(), buf.readBoolean(), REWARDS.decode(buf)))
                    .apply(ByteBufCodecs.list(ResearchTaskConfig.MAX_TASKS));

    /**
     * Keeps the payload definitions static.
     */
    private ResearchTaskPackets() {
    }

    /**
     * Action explicitly requested in the tab.
     */
    public enum Operation {
        VIEW, DONATE, CLAIM
    }

    /**
     * @param controller selected controller and dimension
     * @param operation  action requested by the player
     * @param task       selected task ID, ignored for a view request
     * @param token      expected board token, ignored for a view request
     */
    public record Request(GlobalPos controller, Operation operation, ResourceLocation task,
                          long token) implements CustomPacketPayload {
        /**
         * Serverbound research task request type.
         */
        public static final Type<Request> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AEUpgradeTree.MODID, "research_task"));
        /**
         * Encodes the selected controller, operation and task.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, Request> CODEC = StreamCodec.of(
                (buf, request) -> {
                    GlobalPos.STREAM_CODEC.encode(buf, request.controller());
                    buf.writeEnum(request.operation());
                    ResourceLocation.STREAM_CODEC.encode(buf, request.task());
                    buf.writeLong(request.token());
                },
                buf -> new Request(GlobalPos.STREAM_CODEC.decode(buf), buf.readEnum(Operation.class),
                        ResourceLocation.STREAM_CODEC.decode(buf), buf.readLong()));

        /**
         * {@inheritDoc}
         */
        @Override
        public Type<Request> type() {
            return TYPE;
        }
    }

    /**
     * @param controller location this response applies to
     * @param tasks      states of all enabled tasks for the controller
     * @param feedback   translation key for an action or rejection, or empty on quiet refresh
     * @param action     whether this acknowledges a donate or claim request
     * @param tier       currently displayed tier ID
     * @param token      request token for these offers
     * @param completed  paid offers awaiting renewal
     * @param required   paid offers needed for a reroll, or zero for an empty board
     */
    public record Board(GlobalPos controller, List<ResearchTaskStatus> tasks, String feedback, boolean action,
                        int tier, long token, int completed, int required)
            implements CustomPacketPayload {
        /**
         * Clientbound research task list and feedback type.
         */
        public static final Type<Board> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AEUpgradeTree.MODID, "research_board"));
        /**
         * Encodes the statuses with bounded sizes and a feedback key.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, Board> CODEC = StreamCodec.of((buf, board) -> {
            GlobalPos.STREAM_CODEC.encode(buf, board.controller());
            STATUSES.encode(buf, board.tasks());
            buf.writeUtf(board.feedback(), 120);
            buf.writeBoolean(board.action());
            buf.writeVarInt(board.tier());
            buf.writeLong(board.token());
            buf.writeVarInt(board.completed());
            buf.writeVarInt(board.required());
        }, buf -> new Board(GlobalPos.STREAM_CODEC.decode(buf), STATUSES.decode(buf), buf.readUtf(120), buf.readBoolean(),
                buf.readVarInt(), buf.readLong(), buf.readVarInt(), buf.readVarInt()));

        /**
         * {@inheritDoc}
         */
        @Override
        public Type<Board> type() {
            return TYPE;
        }
    }

    /**
     * @param controller live controller anchoring the operator's editor
     * @param revision   last loaded task revision, ignored when reading
     * @param document   proposed task JSON, or empty to read the current config
     */
    public record EditRequest(GlobalPos controller, long revision, String document) implements CustomPacketPayload {
        /**
         * Serverbound task config editor request.
         */
        public static final Type<EditRequest> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AEUpgradeTree.MODID, "research_edit"));
        /**
         * Bounded editor request format.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, EditRequest> CODEC = StreamCodec.composite(
                GlobalPos.STREAM_CODEC, EditRequest::controller,
                ByteBufCodecs.VAR_LONG, EditRequest::revision,
                ByteBufCodecs.stringUtf8(ResearchTaskConfig.MAX_BYTES), EditRequest::document,
                EditRequest::new);

        /**
         * {@inheritDoc}
         */
        @Override
        public Type<EditRequest> type() {
            return TYPE;
        }
    }

    /**
     * @param controller editor anchor this response addresses
     * @param revision   confirmed task config revision
     * @param document   full confirmed config on a successful read or save, otherwise empty
     * @param message    localized feedback key with optional diagnostic details
     */
    public record EditResponse(GlobalPos controller, long revision, String document,
                               String message) implements CustomPacketPayload {
        /**
         * Clientbound result of a task config editor request.
         */
        public static final Type<EditResponse> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath(AEUpgradeTree.MODID, "research_edit_result"));
        /**
         * Bounded editor response format.
         */
        public static final StreamCodec<RegistryFriendlyByteBuf, EditResponse> CODEC = StreamCodec.composite(
                GlobalPos.STREAM_CODEC, EditResponse::controller,
                ByteBufCodecs.VAR_LONG, EditResponse::revision,
                ByteBufCodecs.stringUtf8(ResearchTaskConfig.MAX_BYTES), EditResponse::document,
                ByteBufCodecs.stringUtf8(640), EditResponse::message,
                EditResponse::new);

        /**
         * {@inheritDoc}
         */
        @Override
        public Type<EditResponse> type() {
            return TYPE;
        }
    }
}
