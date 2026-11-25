package dev.sixik.aeupgrade.client.tree;

import static dev.sixik.aeupgrade.client.tree.UpgradeTreeText.localizedText;
import static dev.sixik.aeupgrade.client.tree.UpgradeTreeText.tr;
import static dev.sixik.aeupgrade.client.widget.AeStyle.TEXT_COLOR;
import static dev.sixik.aeupgrade.client.widget.AeStyle.text;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;

import dev.sixik.aeupgrade.client.widget.AeButton;
import dev.sixik.aeupgrade.client.widget.AeItemSlot;
import dev.sixik.aeupgrade.client.widget.AeScrollbar;
import dev.sixik.aeupgrade.client.widget.AeStyle;
import dev.sixik.aeupgrade.tree.research.ResearchTask.Type;
import dev.sixik.aeupgrade.tree.research.ResearchTaskPackets.Board;
import dev.sixik.aeupgrade.tree.research.ResearchTaskPackets.Operation;
import dev.sixik.aeupgrade.tree.research.ResearchTaskStatus;
import dev.sixik.unigui.api.layout.Align;
import dev.sixik.unigui.api.layout.Overflow;
import dev.sixik.unigui.api.widget.Widget;
import dev.sixik.unigui.widgets.containers.HBox;
import dev.sixik.unigui.widgets.containers.ScrollView;
import dev.sixik.unigui.widgets.containers.VBox;
import dev.sixik.unigui.widgets.display.TextWidget;
import dev.sixik.unigui.widgets.feedback.OverlayLayer;
import dev.sixik.unigui.widgets.minecraft.MinecraftItemTooltip;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * Renders the selected network's research tasks and their available actions.
 */
final class ResearchTaskPanel {
    /**
     * Body inserted in the shared research screen.
     */
    private final VBox body = new VBox();
    /**
     * Scrollable task cards in configuration order.
     */
    private final VBox rows = new VBox();
    /**
     * Native item tooltips attached to the root overlay instead of the clipped task list.
     */
    private final List<MinecraftItemTooltip> tooltips = new ArrayList<>();
    /**
     * Hosts tooltips above the scroll area.
     */
    private final OverlayLayer overlay;
    /**
     * Tier and completion quota above the cards.
     */
    private final TextWidget tier = text("", 9, TEXT_COLOR);
    /**
     * Token sent with the next action so only currently shown offers can be changed.
     */
    private long token;
    /**
     * Current operation feedback below the scroll area.
     */
    private final TextWidget feedback = text(tr("tasks.loading"), 9, TEXT_COLOR).wrapText();
    /**
     * Callback to request a storage donation or reward from the server.
     */
    private final BiConsumer<ResourceLocation, Operation> send;
    /**
     * Last task states shown. Equal responses leave the layout and controls intact.
     */
    private List<ResearchTaskStatus> tasks = List.of();
    /**
     * True while the server validates a donation or claim.
     */
    private boolean pending;

    /**
     * Builds a scrollable tab with AE-styled controls.
     *
     * @param send    action callback for the selected task ID
     * @param overlay root overlay hosting the vanilla item tooltips
     */
    ResearchTaskPanel(BiConsumer<ResourceLocation, Operation> send, OverlayLayer overlay) {
        this.send = send;
        this.overlay = overlay;
        body.spacing(5);
        body.layout(style -> style.expand().minHeight(0).alignItems(Align.STRETCH));
        rows.spacing(5);
        rows.layout(style -> style.widthPercent(100).padding(2, 4).alignItems(Align.STRETCH));
        var scroll = new ScrollView(rows);
        scroll.layout(style -> style.expand().minHeight(0).overflowX(Overflow.HIDDEN).overflowY(Overflow.AUTO));
        AeScrollbar.install(scroll);
        tier.layout(style -> style.height(13).flexNone());
        body.addChild(tier);
        body.addChild(scroll);
        feedback.layout(style -> style.minHeight(16).flexNone());
        body.addChild(feedback);
    }

    /**
     * @return tab content rooted in the research window
     */
    VBox body() {
        return body;
    }

    /**
     * @return token of the most recently received task board
     */
    long token() {
        return token;
    }

    /**
     * Updates the visible task cards and any server feedback.
     *
     * @param board current network statuses and optional action response
     */
    void apply(Board board) {
        token = board.token();
        String summary = board.required() > 0 ? tr("tasks.tier_progress", board.tier(), board.completed(), board.required())
                : tr("tasks.tier", board.tier());
        if (!tier.text().equals(summary)) {
            AeStyle.setLabel(tier, summary, 9);
        }
        boolean acknowledged = pending && board.action();
        if (acknowledged) {
            pending = false;
        }
        if (acknowledged || !tasks.equals(board.tasks())) {
            tasks = board.tasks();
            rows.clearChildren();
            tooltips.forEach(overlay::removeOverlay);
            tooltips.clear();
            for (var task : tasks) {
                rows.addChild(card(task));
            }
        }
        if (board.action() || !board.feedback().isEmpty() || tasks.isEmpty()
                || feedback.text().equals(tr("tasks.loading")) || feedback.text().equals(tr("tasks.empty"))) {
            AeStyle.setLabel(feedback, board.feedback().isEmpty() ? (tasks.isEmpty() ? tr("tasks.empty") : "")
                    : Component.translatable(board.feedback()).getString(), 9);
        }
    }

    /**
     * Creates a task with its goal, progress, reward and next available action.
     *
     * @param task latest server-confirmed task status
     * @return task card
     */
    private Widget card(ResearchTaskStatus task) {
        var card = new VBox();
        card.backgroundTexture(AeStyle.PANEL).backgroundVisible(true).sliceBorders(3).spacing(4);
        card.layout(style -> style.padding(7).alignItems(Align.STRETCH).flexNone());
        card.addChild(text(localizedText(task.title()), 11, TEXT_COLOR).wrapText());
        if (task.description() != null && !task.description().isBlank()) {
            card.addChild(text(localizedText(task.description()), 9, TEXT_COLOR).wrapText());
        }
        if (task.item() != null) {
            var objective = new HBox().spacing(5);
            objective.layout(style -> style.alignItems(Align.CENTER).flexNone());
            objective.addChild(itemPreview(task.item()));
            objective.addChild(text(goal(task), 9, TEXT_COLOR).wrapText().layout(style -> style.expand().minWidth(0)));
            card.addChild(objective);
        } else {
            card.addChild(text(goal(task), 9, TEXT_COLOR).wrapText());
        }

        var actions = new HBox().spacing(5);
        actions.layout(style -> style.height(19).flexNone().alignItems(Align.CENTER));
        var reward = text(task.points() > 0 ? tr("tasks.reward", task.points()) : "", 9, TEXT_COLOR);
        reward.layout(style -> style.expand().minWidth(0));
        actions.addChild(reward);
        if (task.claimed()) {
            actions.addChild(text(tr("tasks.claimed"), 9, TEXT_COLOR));
        } else if (task.progress() >= task.amount() || task.type() == Type.DONATE_ITEMS) {
            boolean claim = task.progress() >= task.amount();
            if (claim && !task.rewardFits()) {
                actions.addChild(text(tr("tasks.no_room"), 9, TEXT_COLOR));
            }
            var action = new AeButton(tr(claim ? "tasks.claim" : "tasks.donate"));
            action.layout(style -> style.size(110, 18).flexNone());
            action.enabled(!pending && (!claim || task.rewardFits()));
            action.onClick(event -> {
                if (!pending) {
                    pending = true;
                    action.enabled(false);
                    AeStyle.setLabel(feedback, tr("pending"), 9);
                    send.accept(ResourceLocation.parse(task.id()), claim ? Operation.CLAIM : Operation.DONATE);
                }
            });
            actions.addChild(action);
        }
        card.addChild(actions);
        for (var item : task.itemRewards()) {
            var line = new HBox().spacing(5);
            line.layout(style -> style.alignItems(Align.CENTER).flexNone());
            line.addChild(itemPreview(item.item()));
            line.addChild(text(tr("tasks.item_reward", item.amount(), itemName(item.item())), 9, TEXT_COLOR)
                    .wrapText().layout(style -> style.expand().minWidth(0)));
            card.addChild(line);
        }
        return card;
    }

    /**
     * Creates an item preview under the normal Minecraft item tooltip.
     *
     * @param item registry ID of the goal or reward
     * @return preview slot that can receive hover events
     */
    private AeItemSlot itemPreview(String item) {
        var preview = new AeItemSlot(false);
        preview.item(item);
        var tooltip = new MinecraftItemTooltip(preview, preview::stack);
        tooltips.add(tooltip);
        overlay.addOverlay(tooltip);
        return preview;
    }

    /**
     * Formats the configured goal and progress with the local item's display name.
     *
     * @param task current task state
     * @return localized objective
     */
    private static String goal(ResearchTaskStatus task) {
        String item = task.item() == null ? "" : itemName(task.item());
        return switch (task.type()) {
            case CRAFT_JOBS -> task.item() == null ? tr("tasks.craft_jobs", task.progress(), task.amount())
                    : tr("tasks.craft_filtered", task.progress(), task.amount(), item);
            case CRAFT_ITEMS -> tr("tasks.craft_items", task.progress(), task.amount(), item);
            case DONATE_ITEMS -> tr("tasks.donate_items", task.progress(), task.amount(), item);
        };
    }

    /**
     * @param id configured item registry ID
     * @return localized default stack name or the ID when unavailable
     */
    private static String itemName(String id) {
        return BuiltInRegistries.ITEM.getOptional(ResourceLocation.parse(id))
                .map(value -> value.getDefaultInstance().getHoverName().getString()).orElse(id);
    }
}
