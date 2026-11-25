package dev.sixik.aeupgrade.client.tree.editor;

import static dev.sixik.aeupgrade.client.tree.UpgradeTreeText.tr;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.control;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.field;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.label;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import dev.sixik.aeupgrade.client.widget.AeButton;
import dev.sixik.aeupgrade.client.widget.AeScrollbar;
import dev.sixik.aeupgrade.client.widget.AeStyle;
import dev.sixik.aeupgrade.tree.research.ResearchTaskConfig;
import dev.sixik.aeupgrade.tree.research.ResearchTier;
import dev.sixik.unigui.api.layout.Align;
import dev.sixik.unigui.api.layout.Overflow;
import dev.sixik.unigui.widgets.containers.HBox;
import dev.sixik.unigui.widgets.containers.ScrollView;
import dev.sixik.unigui.widgets.containers.VBox;
import dev.sixik.unigui.widgets.interaction.TextField;

/**
 * Form for the fallback and numbered research-task tiers.
 */
final class ResearchTierEditor {
    /**
     * Tier panel shown in place of the task list and detail form.
     */
    final VBox panel = new VBox();
    /**
     * Tier cards in config order.
     */
    private final VBox rows = new VBox();
    /**
     * Current form for each tier.
     */
    private final List<TierRow> tiers = new ArrayList<>();
    /**
     * Explicit ID for a new tier.
     */
    private final TextField newId = field(5);
    /**
     * Appends a numbered tier after validating its identifier.
     */
    private final AeButton add = control("tasks.tiers.add", 106, this::add);
    private final InspectorHelp help;
    /**
     * Reports invalid ID input in the parent window.
     */
    private final Consumer<String> feedback;
    /**
     * Reassigns task definitions when a tier is removed.
     */
    private final Consumer<Integer> removed;
    /**
     * True when the parent editor can accept changes.
     */
    private boolean editing;

    /**
     * Builds the searchable-width, scrollable form using the parent editor's style.
     *
     * @param help     tooltips hosted in the task editor
     * @param feedback callback for localized validation messages
     * @param removed  callback for reassigning tasks from a deleted tier
     */
    ResearchTierEditor(InspectorHelp help, Consumer<String> feedback, Consumer<Integer> removed) {
        this.help = help;
        this.feedback = feedback;
        this.removed = removed;
        panel.spacing(4);
        panel.layout(style -> style.expand().minHeight(0).alignItems(Align.STRETCH));
        var controls = new HBox().spacing(4);
        controls.layout(style -> style.height(18).flexNone().alignItems(Align.CENTER));
        newId.layout(style -> style.size(55, 18).flexNone());
        help.attach(newId, "tasks.tiers.id");
        help.attach(add, "tasks.tiers.id");
        controls.addChild(add);
        controls.addChild(newId);
        panel.addChild(controls);
        panel.addChild(label(tr("editor.tasks.tiers.range_hint"), 8).wrapText());
        rows.spacing(5);
        rows.layout(style -> style.widthPercent(100).padding(2, 4).alignItems(Align.STRETCH));
        var scroll = new ScrollView(rows);
        scroll.layout(style -> style.expand().minHeight(0).overflowX(Overflow.HIDDEN).overflowY(Overflow.AUTO));
        AeScrollbar.install(scroll);
        panel.addChild(scroll);
    }

    /**
     * Replaces the draft from the confirmed server document.
     *
     * @param definitions tiers including the fallback
     */
    void load(List<ResearchTier> definitions) {
        rows.clearChildren();
        tiers.clear();
        definitions.forEach(this::append);
        suggestId();
    }

    /**
     * @return available tier IDs in display order
     */
    List<Integer> ids() {
        return tiers.stream().map(tier -> tier.id).toList();
    }

    /**
     * @return current tier settings for saving
     * @throws IllegalArgumentException for invalid numeric input
     */
    List<ResearchTier> draft() {
        return tiers.stream().map(TierRow::value).toList();
    }

    /**
     * Enables or disables the entire tier form during a network request.
     *
     * @param editing whether fields can be changed
     */
    void editing(boolean editing) {
        this.editing = editing;
        add.enabled(editing && tiers.size() < ResearchTaskConfig.MAX_TIERS);
        newId.enabled(editing);
        tiers.forEach(tier -> tier.editing(editing));
    }

    /**
     * Appends a tier with the entered ID and initial defaults.
     */
    private void add() {
        if (!editing || tiers.size() >= ResearchTaskConfig.MAX_TIERS) {
            return;
        }
        try {
            int id = InspectorWidgets.integer(newId);
            if (id <= 0 || id > ResearchTier.MAX_ID || ids().contains(id)) {
                throw new NumberFormatException();
            }
            append(new ResearchTier(id, 10, null, 3, 1));
            suggestId();
            editing(editing);
        } catch (NumberFormatException invalid) {
            feedback.accept(tr("editor.tasks.tiers.id_error", ResearchTier.MAX_ID));
        }
    }

    /**
     * Adds one tier card to the form.
     *
     * @param definition tier whose settings are initially shown
     */
    private void append(ResearchTier definition) {
        var tier = new TierRow(definition);
        tiers.add(tier);
        rows.addChild(tier.card);
    }

    /**
     * Suggests the next unused positive tier ID.
     */
    private void suggestId() {
        var used = ids();
        int id = 1;
        while (used.contains(id)) {
            id++;
        }
        newId.text(Integer.toString(id));
    }

    /**
     * Editable rules for one tier with an immutable ID.
     */
    private final class TierRow {
        /**
         * Tier ID used by task definitions.
         */
        private final int id;
        /**
         * Card containing this tier's inputs.
         */
        private final VBox card = new VBox();
        /**
         * Inclusive lower bound in total research points.
         */
        private final TextField minimum = field(5);
        /**
         * Exclusive upper point bound, empty for no cap.
         */
        private final TextField maximum = field(5);
        /**
         * Maximum number of simultaneous tasks.
         */
        private final TextField visible = field(2);
        /**
         * Number of paid tasks needed before a reroll.
         */
        private final TextField reroll = field(2);
        /**
         * Removes this tier and reassigns its tasks to the fallback pool.
         */
        private final AeButton delete = control("tasks.tiers.remove", 100, this::delete);

        /**
         * @param definition values loaded into this card
         */
        private TierRow(ResearchTier definition) {
            id = definition.id();
            card.spacing(3);
            card.backgroundTexture(AeStyle.PANEL).backgroundVisible(true).sliceBorders(3);
            card.layout(style -> style.padding(5).alignItems(Align.STRETCH).flexNone());
            var heading = new HBox().spacing(4);
            heading.layout(style -> style.height(18).flexNone().alignItems(Align.CENTER));
            heading.addChild(label(id == 0 ? tr("editor.tasks.tiers.fallback") : tr("tasks.tier", id), 10)
                    .layout(style -> style.expand().minWidth(0)));
            if (id != 0) {
                heading.addChild(delete);
                help.attach(delete, "tasks.tiers.remove");
            }
            card.addChild(heading);
            minimum.text(Integer.toString(definition.minPoints()));
            maximum.text(definition.maxPoints() == null ? "" : Integer.toString(definition.maxPoints()));
            visible.text(Integer.toString(definition.visible()));
            reroll.text(Integer.toString(definition.reroll()));
            maximum.placeholder(tr("editor.tasks.tiers.unlimited"));
            if (id != 0) {
                card.addChild(help.row("tasks.tiers.minimum", minimum));
                card.addChild(help.row("tasks.tiers.maximum", maximum));
            }
            card.addChild(help.row("tasks.tiers.visible", visible));
            card.addChild(help.row("tasks.tiers.reroll", reroll));
        }

        /**
         * @return tier rule read from this card's inputs
         * @throws IllegalArgumentException for invalid numeric text
         */
        private ResearchTier value() {
            try {
                return new ResearchTier(id, InspectorWidgets.integer(minimum),
                        maximum.text().isBlank() ? null : InspectorWidgets.integer(maximum),
                        InspectorWidgets.integer(visible), InspectorWidgets.integer(reroll));
            } catch (NumberFormatException invalid) {
                throw new IllegalArgumentException(tr("editor.tasks.tiers.number_error", id));
            }
        }

        /**
         * Removes the card and moves assigned tasks to tier zero.
         */
        private void delete() {
            if (editing && id != 0) {
                tiers.remove(this);
                rows.removeChild(card);
                removed.accept(id);
                ResearchTierEditor.this.editing(editing);
            }
        }

        /**
         * @param enabled whether this card may be edited
         */
        private void editing(boolean enabled) {
            minimum.enabled(enabled);
            maximum.enabled(enabled);
            visible.enabled(enabled);
            reroll.enabled(enabled);
            delete.enabled(enabled);
        }
    }
}
