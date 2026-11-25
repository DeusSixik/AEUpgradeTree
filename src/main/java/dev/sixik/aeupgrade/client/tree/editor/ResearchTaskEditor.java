package dev.sixik.aeupgrade.client.tree.editor;

import static dev.sixik.aeupgrade.client.tree.UpgradeTreeText.localizedText;
import static dev.sixik.aeupgrade.client.tree.UpgradeTreeText.tr;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.control;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.field;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.label;
import static dev.sixik.aeupgrade.client.widget.AeStyle.setLabel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Consumer;

import dev.sixik.aeupgrade.client.tree.UpgradeTreeText;
import dev.sixik.aeupgrade.client.widget.AeButton;
import dev.sixik.aeupgrade.client.widget.AeItemSlot;
import dev.sixik.aeupgrade.client.widget.AeScrollbar;
import dev.sixik.aeupgrade.client.widget.AeStyle;
import dev.sixik.aeupgrade.client.widget.AeToggleButton;
import dev.sixik.aeupgrade.client.widget.ModalWindow;
import dev.sixik.aeupgrade.platform.PlatformNetwork;
import dev.sixik.aeupgrade.tree.research.ResearchTask;
import dev.sixik.aeupgrade.tree.research.ResearchItemReward;
import dev.sixik.aeupgrade.tree.research.ResearchTask.Type;
import dev.sixik.aeupgrade.tree.research.ResearchTaskConfig;
import dev.sixik.aeupgrade.tree.research.ResearchTaskConfig.Document;
import dev.sixik.aeupgrade.tree.research.ResearchTaskPackets.EditRequest;
import dev.sixik.aeupgrade.tree.research.ResearchTaskPackets.EditResponse;
import dev.sixik.unigui.api.layout.Align;
import dev.sixik.unigui.api.layout.Justify;
import dev.sixik.unigui.api.layout.Overflow;
import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.render.NineSlice;
import dev.sixik.unigui.api.render.Paint;
import dev.sixik.unigui.api.widget.Visibility;
import dev.sixik.unigui.impl.widget.WidgetBase;
import dev.sixik.unigui.widgets.containers.HBox;
import dev.sixik.unigui.widgets.containers.ScrollView;
import dev.sixik.unigui.widgets.containers.VBox;
import dev.sixik.unigui.widgets.display.TextWidget;
import dev.sixik.unigui.widgets.feedback.OverlayLayer;
import dev.sixik.unigui.widgets.feedback.WindowWidget;
import dev.sixik.unigui.widgets.interaction.ComboBox;
import dev.sixik.unigui.widgets.interaction.TextField;
import dev.sixik.unigui.widgets.minecraft.MinecraftItemTooltip;
import net.minecraft.core.GlobalPos;
import org.jetbrains.annotations.Nullable;

/**
 * AE-styled modal for the server's shared research task config.
 */
final class ResearchTaskEditor {
    /**
     * Native AE panel texture with a 3-pixel border.
     */
    private static final NineSlice PANEL = NineSlice.of(AeStyle.PANEL, 3);
    /**
     * Opaque tint that preserves the AE panel texture.
     */
    private static final Paint UNTINTED = Paint.fill(MutableColor.fromHex("#FFFFFF"));
    /**
     * Task types in dropdown and enum order.
     */
    private static final Type[] TYPES = Type.values();
    /**
     * Localized feedback indicating the server has moved past the open revision.
     */
    private static final String STALE_MESSAGE = "aeupgrade.tree.editor.tasks.stale";

    /**
     * Controller location used by the server to validate access.
     */
    private final GlobalPos controller;
    /**
     * Updates the tree editor's return snapshot after server responses.
     */
    private final Consumer<Boolean> availabilityChanged;
    /**
     * Modal widget occupying the selected portion of the editor viewport.
     */
    private final WindowWidget window;
    /**
     * Overlay where the item picker and dropdowns can draw above the form.
     */
    private final OverlayLayer content;
    private final InspectorHelp help;
    /**
     * Item selection modal currently above the task form.
     */
    private @Nullable WindowWidget itemPicker;
    /**
     * Editable rows in config order, including suspended tasks.
     */
    private final List<Draft> tasks = new ArrayList<>();
    /**
     * Task selectors within the left scroll area.
     */
    private final VBox list = new VBox();
    /**
     * Form fields for the selected task.
     */
    private final VBox form = new VBox();
    /**
     * Scroll pane for task selectors.
     */
    private final ScrollView taskScroll = scroll(list);
    /**
     * Scroll pane for the selected task form.
     */
    private final ScrollView formScroll = scroll(form);
    /**
     * Enables or disables the task system globally.
     */
    private final AeToggleButton enabled = new AeToggleButton(tr("editor.tasks.enabled"));
    /**
     * Offers or suspends the selected task.
     */
    private final AeToggleButton taskEnabled = new AeToggleButton(tr("editor.tasks.task_enabled"));
    /**
     * Allows the same task to return after a reroll.
     */
    private final AeToggleButton repeatable = new AeToggleButton(tr("editor.tasks.repeatable"));
    /**
     * Tier assignment for the selected task.
     */
    private final ComboBox taskTier = new ComboBox();
    /**
     * Tier IDs matching the current task tier selector's option order.
     */
    private List<Integer> tierChoices = List.of(0);
    /**
     * Namespaced ID used to associate progress with the task.
     */
    private final TextField id = field(ResearchTask.MAX_ID);
    /**
     * Literal label or translation key.
     */
    private final TextField title = field(ResearchTask.MAX_TITLE);
    /**
     * Optional literal description or translation key.
     */
    private final TextField description = field(ResearchTask.MAX_DESCRIPTION);
    /**
     * Optional required item or crafting output filter.
     */
    private final TextField item = field(ResearchTask.MAX_ID);
    /**
     * Selected item preview and entry point to the shared picker.
     */
    private final AeItemSlot itemPreview = new AeItemSlot(true);
    /**
     * Positive number of jobs or items.
     */
    private final TextField amount = field(7);
    /**
     * Positive number of reward points.
     */
    private final TextField points = field(5);
    /**
     * Editable item reward rows for the selected task.
     */
    private final VBox rewards = new VBox();
    /**
     * Editable records for the currently displayed reward rows.
     */
    private final List<RewardRow> rewardRows = new ArrayList<>();
    /**
     * Adds an item reward to the selected task.
     */
    private final AeButton addReward = control("tasks.rewards.add", 120, this::addReward);
    /**
     * Goal type dropdown for the selected task.
     */
    private final ComboBox type = new ComboBox();
    /**
     * Inline validation or server feedback.
     */
    private final TextWidget status = label(tr("tasks.loading"), 9).wrapText();
    /**
     * Edits the points ranges and offer quota for each tier.
     */
    private final ResearchTierEditor tierEditor;
    /**
     * Task list and selected task form, hidden while editing tiers.
     */
    private final HBox body = new HBox();
    /**
     * Switches between tasks and tier settings.
     */
    private final AeButton tierTab = control("tasks.tiers.tab", 105, this::toggleTiers);
    /**
     * Button that adds an unconfigured task to the draft.
     */
    private final AeButton add = control("tasks.add", 98, this::addTask);
    /**
     * Button that deletes the currently selected task.
     */
    private final AeButton remove = control("tasks.remove", 95, this::removeTask);
    /**
     * Action to persist the local draft to the server.
     */
    private final AeButton save = control("tasks.save", 114, this::save);
    /**
     * Discards the local draft and requests the latest confirmed config.
     */
    private final AeButton reload = control("tasks.reload", 98, this::load);
    /**
     * Selected list index, or -1 when no task is selected.
     */
    private int selected = -1;
    /**
     * Latest confirmed revision, or zero until a successful load.
     */
    private long revision;
    /**
     * Whether a read or save request is in flight.
     */
    private boolean pending;
    /**
     * Whether a concurrent change requires reloading before the next save.
     */
    private boolean stale;

    /**
     * Assembles the task form inside an AE-styled overlay.
     *
     * @param host                tree editor overlay hosting the modal
     * @param controller          controller anchoring server requests
     * @param availabilityChanged reports the saved task tab availability
     * @param closed              clears the tree editor's modal reference
     */
    ResearchTaskEditor(OverlayLayer host, GlobalPos controller, Consumer<Boolean> availabilityChanged, Runnable closed) {
        this.controller = controller;
        this.availabilityChanged = availabilityChanged;
        list.spacing(3);
        form.spacing(4);
        var panel = new VBox().spacing(5);
        panel.layout(style -> style.fill().padding(9).alignItems(Align.STRETCH));
        var heading = label(tr("editor.tasks.title"), 11);
        heading.layout(style -> style.expand().minWidth(0));
        var header = new HBox().spacing(5);
        header.layout(style -> style.height(18).flexNone().alignItems(Align.CENTER));
        header.addChild(heading);
        header.addChild(tierTab);
        panel.addChild(header);
        panel.addChild(enabled);

        content = new OverlayLayer(panel);
        help = new InspectorHelp(content);
        tierEditor = new ResearchTierEditor(help, message -> setLabel(status, message, 9), this::moveTasksToFallback);
        help.attach(enabled, "tasks.enabled");
        help.attach(taskEnabled, "tasks.task_enabled");
        help.attach(repeatable, "tasks.repeatable");
        type.useOverlay(content).items(Arrays.stream(TYPES)
                .map(value -> tr("editor.tasks.type." + value.name().toLowerCase(Locale.ROOT))).toList()).maxVisibleOptions(3);
        AeStyle.styleComboBox(type);
        type.onSelectionChanged(event -> refreshItemHint());
        taskTier.useOverlay(content).maxVisibleOptions(6);
        AeStyle.styleComboBox(taskTier);
        form.addChild(taskEnabled);
        form.addChild(repeatable);
        form.addChild(help.row("tasks.name", title));
        form.addChild(help.row("tasks.description", description));
        form.addChild(help.row("tasks.id", id));
        form.addChild(help.row("tasks.type", type));
        form.addChild(help.row("tasks.tier", taskTier));
        form.addChild(itemRow());
        form.addChild(help.row("tasks.amount", amount));
        form.addChild(help.row("tasks.points", points));
        rewards.spacing(4);
        rewards.layout(style -> style.alignItems(Align.STRETCH));
        form.addChild(label(tr("editor.tasks.rewards.title"), 9));
        form.addChild(rewards);
        form.addChild(addReward);
        help.attach(addReward, "tasks.rewards.add");
        form.addChild(remove);

        body.spacing(7);
        body.layout(style -> style.expand().minHeight(0).alignItems(Align.STRETCH));
        var left = new VBox().spacing(4);
        left.layout(style -> style.widthPercent(34).minWidth(96).maxWidth(160).flexNone().alignItems(Align.STRETCH));
        left.addChild(add);
        left.addChild(taskScroll);
        body.addChild(left);
        body.addChild(formScroll);
        panel.addChild(body);
        tierEditor.panel.visibility(Visibility.COLLAPSED);
        panel.addChild(tierEditor.panel);
        status.layout(style -> style.minHeight(12).maxHeight(38).flexNone());
        panel.addChild(status);
        var footer = new HBox().spacing(4);
        footer.layout(style -> style.height(18).flexNone().justifyContent(Justify.END));
        footer.addChild(reload);
        footer.addChild(control("asset_close", 64, this::close));
        footer.addChild(save);
        panel.addChild(footer);

        window = new WindowWidget(tr("editor.tasks.title"), content) {
            @Override
            public boolean captionless() {
                return true;
            }
        };
        var bounds = host.layoutBounds();
        float width = Math.min(514, bounds.width() - 16);
        float height = Math.min(350, bounds.height() - 16);
        window.position((bounds.width() - width) / 2, (bounds.height() - height) / 2);
        window.layout(style -> style.size(width, height));
        window.fixedModal(true).dockingEnabled(false).closeOnOutsideClick(false);
        window.focusScope(true);
        window.renderer((draw, widget) -> {
            var frame = widget.layoutBounds();
            draw.nineSlice(PANEL, frame.x(), frame.y(), frame.width(), frame.height(), UNTINTED);
        });
        window.onClosed(event -> {
            if (event.target() == window) {
                host.removeOverlay(window);
                closed.run();
            }
        });
        ModalWindow.open(host, window);
        load();
    }

    /**
     * Applies a read or save reply while this modal is still open.
     *
     * @param response server response for the editor's controller
     */
    void receive(EditResponse response) {
        pending = false;
        stale |= response.message().equals(STALE_MESSAGE);
        if (!response.document().isEmpty()) {
            var document = ResearchTaskConfig.parse(response.document());
            int previous = selected;
            revision = response.revision();
            stale = false;
            enabled.silentChecked(document.enabled());
            tierEditor.load(document.tiers());
            tasks.clear();
            document.tasks().stream().map(Draft::new).forEach(tasks::add);
            selected = -1;
            select(tasks.isEmpty() ? -1 : Math.clamp(previous, 0, tasks.size() - 1));
            availabilityChanged.accept(enabled.checked() && document.tasks().stream().anyMatch(ResearchTask::enabled));
        }
        setLabel(status, UpgradeTreeText.feedback(response.message()), 9);
        refreshControls();
    }

    /**
     * Closes the dropdown first, then closes the modal when no request is in flight.
     */
    void close() {
        if (itemPicker != null) {
            itemPicker.close();
        } else if (taskTier.opened()) {
            taskTier.opened(false);
        } else if (type.opened()) {
            type.opened(false);
        } else if (!pending) {
            window.close();
        }
    }

    /**
     * Requests the current task config without writing any changes.
     */
    private void load() {
        send("");
    }

    /**
     * Sends the task draft after checking numeric fields.
     */
    private void save() {
        if (pending || stale || revision == 0) {
            return;
        }
        if (body.visibility() == Visibility.VISIBLE) {
            storeSelection();
        }
        try {
            var document = new Document(enabled.checked(), tasks.stream().map(Draft::toTask).toList(), tierEditor.draft());
            send(ResearchTaskConfig.encode(document));
        } catch (NumberFormatException failure) {
            setLabel(status, tr("editor.tasks.number_error", failure.getMessage()), 9);
        } catch (IllegalArgumentException failure) {
            setLabel(status, tr("editor.tasks.invalid") + " " + failure.getMessage(), 9);
        }
    }

    /**
     * Sends an editor request and locks its draft until the server responds.
     *
     * @param document proposed JSON, or empty to load
     */
    private void send(String document) {
        pending = true;
        type.opened(false);
        taskTier.opened(false);
        setLabel(status, tr(document.isEmpty() ? "tasks.loading" : "pending"), 9);
        refreshControls();
        PlatformNetwork.sendToServer(new EditRequest(controller, revision, document));
    }

    /**
     * Appends a starter draft with a fresh persistent ID.
     */
    private void addTask() {
        if (pending || revision == 0 || tasks.size() >= ResearchTaskConfig.MAX_TASKS) {
            return;
        }
        tasks.add(new Draft(new ResearchTask("aeupgrade:task_" + UUID.randomUUID(), true, Type.CRAFT_JOBS,
                null, 1, 1, tr("editor.tasks.new"), null)));
        select(tasks.size() - 1);
    }

    /**
     * Removes the selected task from the local draft.
     */
    private void removeTask() {
        if (!pending && selected >= 0) {
            int index = selected;
            tasks.remove(index);
            selected = -1;
            select(Math.min(index, tasks.size() - 1));
        }
    }

    /**
     * Captures the previously selected task and shows another draft in the form.
     *
     * @param index index of the task to edit, or -1 for a blank form
     */
    private void select(int index) {
        storeSelection();
        selected = index;
        if (index >= 0) {
            var task = tasks.get(index);
            taskEnabled.silentChecked(task.enabled());
            repeatable.silentChecked(task.repeatable());
            id.text(task.id());
            title.text(task.title());
            description.text(task.description());
            item.text(task.item());
            itemPreview.item(task.item());
            amount.text(task.amount());
            points.text(task.points());
            loadRewards(task.rewards());
            type.silentSelectedIndex(task.type().ordinal());
            tierChoices = tierEditor.ids();
            int tierIndex = tierChoices.indexOf(task.tier());
            taskTier.items(tierChoices.stream().map(tier -> tr("tasks.tier", tier)).toList())
                    .silentSelectedIndex(tierIndex < 0 ? tierChoices.indexOf(0) : tierIndex);
            AeStyle.styleComboBoxOptions(taskTier);
            refreshItemHint();
        }
        formScroll.scrollTo(0, 0);
        rebuildList();
        refreshControls();
    }

    /**
     * Preserves temporary input, including invalid numbers, when switching between tasks.
     */
    private void storeSelection() {
        if (selected >= 0) {
            tasks.set(selected, new Draft(id.text().strip(), taskEnabled.checked(), TYPES[type.selectedIndex()],
                    item.text().strip(), amount.text().strip(), points.text().strip(), title.text().strip(),
                    description.text().strip(), tierChoices.get(taskTier.selectedIndex()), repeatable.checked(),
                    rewardRows.stream().map(RewardRow::draft).toList()));
        }
    }

    /**
     * Rebuilds task selectors with a marker for the current selection.
     */
    private void rebuildList() {
        list.clearChildren();
        for (int i = 0; i < tasks.size(); i++) {
            int index = i;
            var task = tasks.get(i);
            String caption = localizedText(task.title().isEmpty() ? task.id() : task.title());
            var button = new AeButton((index == selected ? "> " : "") + "[" + task.tier() + "] " + caption);
            button.layout(style -> style.height(18).flexNone());
            button.onClick(event -> {
                if (!pending && index != selected) {
                    select(index);
                }
            });
            list.addChild(button);
        }
    }

    /**
     * Updates the disabled state of all draft and save controls.
     */
    private void refreshControls() {
        boolean editing = !pending && revision != 0;
        enabled.enabled(editing);
        add.enabled(editing && tasks.size() < ResearchTaskConfig.MAX_TASKS);
        save.enabled(editing && !stale);
        reload.enabled(!pending);
        tierTab.enabled(editing);
        tierEditor.editing(editing);
        taskScroll.enabled(!pending);
        formScroll.enabled(editing);
        formScroll.visibility(selected < 0 ? Visibility.COLLAPSED : Visibility.VISIBLE);
        for (var input : List.<WidgetBase>of(taskEnabled, repeatable, id, title, description, type, taskTier, item,
                itemPreview, amount, points, remove)) {
            input.enabled(editing);
        }
        addReward.enabled(editing && rewardRows.size() < ResearchTask.MAX_ITEM_REWARDS);
        rewardRows.forEach(reward -> reward.editing(editing));
    }

    /**
     * @return ID field accompanied by its live, clickable inventory preview
     */
    private HBox itemRow() {
        var controls = new HBox().spacing(3);
        controls.layout(style -> style.alignItems(Align.CENTER));
        bindItemPicker(item, itemPreview);
        item.layout(style -> style.expand().minWidth(0).height(18));
        controls.addChild(itemPreview);
        controls.addChild(item);
        help.attach(item, "tasks.item");
        return help.row("tasks.item", controls);
    }

    /**
     * Connects an item field and preview to the existing search and hover UI.
     *
     * @param field   item registry ID input
     * @param preview paired item icon
     * @return tooltip to remove if this form row is discarded
     */
    private MinecraftItemTooltip bindItemPicker(TextField field, AeItemSlot preview) {
        preview.onClick(event -> {
            if (!pending && itemPicker == null) {
                itemPicker = UpgradeAssetPicker.openItems(content, tr("editor.tasks.item_picker"), selectedId -> {
                    field.text(selectedId);
                    preview.item(selectedId);
                });
                itemPicker.onClosed(closed -> itemPicker = null);
            }
        });
        field.onTextChanged(event -> preview.item(field.text()));
        var tooltip = new MinecraftItemTooltip(preview, preview::stack);
        content.addOverlay(tooltip);
        return tooltip;
    }

    /**
     * Replaces the displayed item reward rows when the selection changes.
     *
     * @param values raw reward fields for this task
     */
    private void loadRewards(List<RewardDraft> values) {
        for (var row : rewardRows) {
            content.removeOverlay(row.tooltip);
        }
        rewards.clearChildren();
        rewardRows.clear();
        values.forEach(this::appendReward);
    }

    /**
     * Adds a draft row for the operator to select an item and quantity.
     */
    private void addReward() {
        if (!pending && selected >= 0 && rewardRows.size() < ResearchTask.MAX_ITEM_REWARDS) {
            appendReward(new RewardDraft("", "1"));
            refreshControls();
        }
    }

    /**
     * Adds one saved or newly created item reward row.
     *
     * @param value item ID and quantity as raw field text
     */
    private void appendReward(RewardDraft value) {
        var row = new RewardRow(value);
        rewardRows.add(row);
        rewards.addChild(row.controls);
    }

    /**
     * Shows tier options or returns to the preserved task draft.
     */
    private void toggleTiers() {
        boolean show = body.visibility() == Visibility.VISIBLE;
        if (show) {
            storeSelection();
        } else {
            int previous = selected;
            selected = -1;
            select(previous);
        }
        tierTab.label(tr(show ? "editor.tasks.tiers.back" : "editor.tasks.tiers.tab"));
        body.visibility(show ? Visibility.COLLAPSED : Visibility.VISIBLE);
        tierEditor.panel.visibility(show ? Visibility.VISIBLE : Visibility.COLLAPSED);
    }

    /**
     * Moves drafts from a deleted tier into tier zero so they stay editable.
     *
     * @param removed deleted tier ID
     */
    private void moveTasksToFallback(int removed) {
        tasks.replaceAll(task -> task.tier() == removed
                ? new Draft(task.id(), task.enabled(), task.type(), task.item(), task.amount(), task.points(), task.title(),
                task.description(), 0, task.repeatable(), task.rewards()) : task);
    }

    /**
     * Marks the item field as optional only for job-count objectives.
     */
    private void refreshItemHint() {
        item.placeholder(tr(type.selectedIndex() == Type.CRAFT_JOBS.ordinal() ? "editor.tasks.optional_item" : "editor.tasks.required_item"));
    }

    /**
     * Wraps a column in a vertical AE-style scroll area.
     *
     * @param content scrollable column
     * @return scroll view with content width constrained to its viewport
     */
    private static ScrollView scroll(VBox content) {
        content.layout(style -> style.widthPercent(100).padding(2, 3).alignItems(Align.STRETCH));
        var scroll = new ScrollView(content);
        scroll.layout(style -> style.expand().minWidth(0).minHeight(0).overflowX(Overflow.HIDDEN).overflowY(Overflow.AUTO));
        AeScrollbar.install(scroll);
        return scroll;
    }

    /**
     * Text form values captured independently for each task in the draft.
     *
     * @param id          stable namespaced ID
     * @param enabled     whether the selected task is offered
     * @param type        completion rule
     * @param item        optional item ID as raw text
     * @param amount      requested target as raw text
     * @param points      requested reward as raw text
     * @param title       literal title or localization key
     * @param description optional literal description or key
     * @param tier        configured tier ID
     * @param repeatable  whether the task can be offered again
     * @param rewards     raw item reward fields
     */
    private record Draft(String id, boolean enabled, Type type, String item, String amount, String points,
                         String title, String description, int tier, boolean repeatable, List<RewardDraft> rewards) {
        /**
         * @param task server definition or starter defaults
         */
        Draft(ResearchTask task) {
            this(task.id(), task.enabled(), task.type(), Objects.toString(task.item(), ""), Integer.toString(task.amount()),
                    Integer.toString(task.points()), task.title(), Objects.toString(task.description(), ""), task.tier(), task.repeatable(),
                    task.itemRewards().stream().map(reward -> new RewardDraft(reward.item(), Integer.toString(reward.amount()))).toList());
        }

        /**
         * @return task definition with numeric fields parsed and blank optional values omitted
         * @throws NumberFormatException if either amount cannot be parsed as an integer
         */
        ResearchTask toTask() {
            try {
                return new ResearchTask(id, enabled, type, item.isEmpty() ? null : item, Integer.parseInt(amount),
                        Integer.parseInt(points), title, description.isEmpty() ? null : description, tier, repeatable,
                        rewards.stream().map(RewardDraft::toReward).toList());
            } catch (NumberFormatException failure) {
                throw new NumberFormatException(id);
            }
        }
    }

    /**
     * @param item   typed registry ID
     * @param amount typed item count
     */
    private record RewardDraft(String item, String amount) {
        /**
         * @return reward with an integer count
         * @throws NumberFormatException if the entered count is not an integer
         */
        ResearchItemReward toReward() {
            return new ResearchItemReward(item, Integer.parseInt(amount));
        }
    }

    /**
     * An item preview, registry ID, quantity and remove button in the task form.
     */
    private final class RewardRow {
        /**
         * Row container.
         */
        private final HBox controls = new HBox();
        /**
         * Item ID input.
         */
        private final TextField id = field(ResearchTask.MAX_ID);
        /**
         * Number of items to deliver.
         */
        private final TextField amount = field(7);
        /**
         * Item preview and picker control.
         */
        private final AeItemSlot icon = new AeItemSlot(true);
        /**
         * Removes this item from the draft.
         */
        private final AeButton remove = control("tasks.rewards.remove", 20, this::remove);
        /**
         * Native item tooltip attached above the scroll area.
         */
        private final MinecraftItemTooltip tooltip;

        /**
         * @param value initial editable item and quantity
         */
        private RewardRow(RewardDraft value) {
            controls.spacing(3);
            controls.layout(style -> style.height(18).flexNone().alignItems(Align.CENTER));
            id.layout(style -> style.expand().minWidth(0).height(18));
            amount.layout(style -> style.size(50, 18).flexNone());
            id.placeholder(tr("editor.tasks.item"));
            amount.placeholder(tr("editor.tasks.amount"));
            help.attach(id, "tasks.rewards.item");
            help.attach(amount, "tasks.rewards.amount");
            help.attach(remove, "tasks.rewards.remove");
            id.text(value.item());
            amount.text(value.amount());
            icon.item(value.item());
            tooltip = bindItemPicker(id, icon);
            controls.addChild(icon);
            controls.addChild(id);
            controls.addChild(amount);
            controls.addChild(remove);
        }

        /**
         * @return text entered for this reward
         */
        private RewardDraft draft() {
            return new RewardDraft(id.text().strip(), amount.text().strip());
        }

        /**
         * @param enabled whether this row accepts input
         */
        private void editing(boolean enabled) {
            icon.enabled(enabled);
            id.enabled(enabled);
            amount.enabled(enabled);
            remove.enabled(enabled);
        }

        /**
         * Removes this row and its overlay tooltip.
         */
        private void remove() {
            if (!pending) {
                rewardRows.remove(this);
                rewards.removeChild(controls);
                content.removeOverlay(tooltip);
                refreshControls();
            }
        }
    }
}
