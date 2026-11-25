package dev.sixik.aeupgrade.client.tree.editor;

import static dev.sixik.aeupgrade.client.tree.UpgradeTreeText.tr;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.control;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.field;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.label;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.parameterLabel;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.rgbHex;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.styleColorPicker;
import static dev.sixik.aeupgrade.client.widget.AeStyle.setLabel;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;

import dev.sixik.aeupgrade.api.UpgradeEffectRegistry;
import dev.sixik.aeupgrade.client.tree.UpgradeEffectText;
import dev.sixik.aeupgrade.client.tree.editor.InspectorGroup.Parameter;
import dev.sixik.aeupgrade.client.widget.AeButton;
import dev.sixik.aeupgrade.client.widget.AeStyle;
import dev.sixik.aeupgrade.tree.UpgradeEffect;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Kind;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Target;
import dev.sixik.unigui.api.event.FocusLostEvent;
import dev.sixik.unigui.api.layout.Align;
import dev.sixik.unigui.api.layout.SizeValue;
import dev.sixik.unigui.api.widget.Visibility;
import dev.sixik.unigui.widgets.containers.HBox;
import dev.sixik.unigui.widgets.containers.VBox;
import dev.sixik.unigui.widgets.display.TextWidget;
import dev.sixik.unigui.widgets.feedback.OverlayLayer;
import dev.sixik.unigui.widgets.feedback.Popup;
import dev.sixik.unigui.widgets.interaction.ColorPicker;
import dev.sixik.unigui.widgets.interaction.ComboBox;
import dev.sixik.unigui.widgets.interaction.TextField;
import org.jetbrains.annotations.Nullable;

/**
 * Effect cards, grouping and tooltips for the node form.
 */
final class UpgradeEffectInspector {
    /**
     * The dropdown lists one property for each pair of energy units.
     */
    private final List<UpgradeEffect> effectProperties = effectProperties();
    private final OverlayLayer overlay;
    private final InspectorHelp help;
    private final BooleanSupplier editingEnabled;
    private final BooleanSupplier commitFields;
    private final Runnable markEdited;
    private final Runnable refreshTooltips;
    private final Runnable filterParameters;
    private final TextField parameterSearch;
    private final TextWidget mixedEffects = label(tr("editor.mixed_lists"), 8).clipOverflow();
    private final AeButton addEffect = new AeButton(tr("editor.add_effect"));
    private final InspectorGroup effectsGroup;
    private final EnumMap<EffectCategory, InspectorGroup> effectGroups = new EnumMap<>(EffectCategory.class);
    /**
     * Current effect controls in configuration order.
     */
    private final ArrayList<EffectRow> effectRows = new ArrayList<>();

    UpgradeEffectInspector(OverlayLayer overlay, InspectorHelp help, TextField parameterSearch, BooleanSupplier editingEnabled,
                           BooleanSupplier commitFields, Runnable markEdited, Runnable refreshTooltips, Runnable filterParameters) {
        this.overlay = overlay;
        this.help = help;
        this.parameterSearch = parameterSearch;
        this.editingEnabled = editingEnabled;
        this.commitFields = commitFields;
        this.markEdited = markEdited;
        this.refreshTooltips = refreshTooltips;
        this.filterParameters = filterParameters;
        var effectControls = new VBox();
        effectControls.addChild(mixedEffects);
        effectControls.addChild(addEffect);
        effectsGroup = new InspectorGroup("effects", false, effectControls);
        for (var category : EffectCategory.values()) {
            var subgroup = new InspectorGroup("effect_group_" + category.name().toLowerCase(Locale.ROOT), true, null);
            effectGroups.put(category, subgroup);
            effectsGroup.addGroup(subgroup);
        }
        addEffect.layout(style -> style.widthPercent(100).height(18).flexNone());
        addEffect.onClick(event -> addEffect());
        help.attach(addEffect, "add_effect");
    }

    VBox widget() {
        return effectsGroup.widget();
    }

    void show(List<UpgradeEffect> effects, boolean hasSelection) {
        addEffect.enabled(hasSelection);
        closePopups();
        effectRows.clear();
        effectGroups.values().forEach(InspectorGroup::clear);
        effects.forEach(this::appendEffect);
    }

    void showMixed(boolean mixed) {
        mixedEffects.visibility(mixed ? Visibility.VISIBLE : Visibility.COLLAPSED);
    }

    List<UpgradeEffect> read() {
        return effectRows.stream().map(EffectRow::read).toList();
    }

    boolean filter(Predicate<String> matches, boolean searching, boolean visible) {
        if (visible) {
            return effectsGroup.filter(matches, searching, "");
        }
        widget().visibility(Visibility.COLLAPSED);
        return false;
    }

    boolean dropdownOpen() {
        return effectRows.stream().anyMatch(EffectRow::dropdownOpen);
    }

    @Nullable Popup openColorPopup() {
        for (var row : effectRows) {
            if (row.colorPicker.popup().opened()) {
                return row.colorPicker.popup();
            }
        }
        return null;
    }

    void closePopups() {
        effectRows.forEach(EffectRow::closePopups);
    }

    /**
     * Adds an editable effect row to the selected node.
     */
    private void addEffect() {
        if (editingEnabled.getAsBoolean() && commitFields.getAsBoolean()) {
            var row = appendEffect(new UpgradeEffect(Kind.IDLE_FLAT, Target.ALL, 0));
            markEdited.run();
            commitFields.getAsBoolean();
            effectsGroup.expand();
            row.group.expand();
            filterParameters.run();
            if (row.widget.visibility() == Visibility.COLLAPSED) {
                parameterSearch.text("");
            }
        }
    }

    /**
     * @param effect initial property, target and amount of the new row
     * @return card added to the effect's category
     */
    private EffectRow appendEffect(UpgradeEffect effect) {
        var row = new EffectRow(overlay, effect);
        effectRows.add(row);
        row.group.add(row.parameter);
        return row;
    }

    /**
     * @return effect choices available when this editor was opened
     */
    private static List<UpgradeEffect> effectProperties() {
        var properties = new ArrayList<UpgradeEffect>();
        for (var kind : Kind.values()) {
            if (EnergyEffectUnits.property(kind) == kind) {
                properties.add(new UpgradeEffect(kind, Target.ALL, 0));
            }
        }
        for (var type : UpgradeEffectRegistry.types()) {
            properties.add(UpgradeEffect.custom(type, Target.ALL, 0));
        }
        return List.copyOf(properties);
    }

    /**
     * Inspector headings for related effect properties.
     */
    private enum EffectCategory {
        CHANNELS, SLOTS, CRAFTING, ENERGY, DEVICES, ADDONS;

        private static EffectCategory of(@Nullable Kind kind) {
            if (kind == null) {
                return ADDONS;
            }
            return switch (kind) {
                case CHANNELS, CHANNEL_COST -> CHANNELS;
                case PATTERN_SLOTS, INTERFACE_SLOTS -> SLOTS;
                case BATCH, SPEED_PERCENT, SPEED_BASE, SPEED_CARDS, THROUGHPUT -> CRAFTING;
                case IDLE_FLAT, IDLE_PERCENT, CRAFT_FLAT, CRAFT_PERCENT -> ENERGY;
                case DEVICE_LIMIT, DEVICE_CAP -> DEVICES;
            };
        }
    }

    /**
     * One framed effect with a property selector, optional scope, and numeric input.
     */
    private final class EffectRow {
        /**
         * Content displayed in the effect list.
         */
        private final VBox widget = new VBox();
        /**
         * Type of modifier.
         */
        private final ComboBox type = new ComboBox();
        /**
         * Device family when the property supports scopes.
         */
        private final ComboBox target = new ComboBox();
        /**
         * Per-tick or percentage amount for energy properties.
         */
        private final ComboBox unit = new ComboBox();
        /**
         * Signed bonus, assigned base, or cap.
         */
        private final TextField value = field(40);
        /**
         * Actual percentage used by the network, updated as the operator types.
         */
        private final TextWidget percentPreview = label("", 8).clipOverflow();
        /**
         * Optional per-effect tooltip color, with a live preview of automatic coloring.
         */
        private final TextField tooltipColor = field(7);
        private final ColorPicker colorPicker = new ColorPicker();
        private final Parameter parameter = new Parameter(widget, this::searchText);
        /**
         * Category currently hosting this card.
         */
        private InspectorGroup group;
        /**
         * Includes the saved type when the current client does not have its add-on.
         */
        private final List<UpgradeEffect> choices;

        /**
         * @param overlay popup host outside the form clip
         * @param effect  initial data for this row
         */
        private EffectRow(OverlayLayer overlay, UpgradeEffect effect) {
            group = effectGroups.get(EffectCategory.of(effect.kind()));
            widget.backgroundTexture(AeStyle.PANEL).backgroundVisible(true).sliceBorders(3).spacing(3);
            widget.layout(style -> style.alignItems(Align.STRETCH).padding(4));
            var initialKind = effect.kind() == null ? null : EnergyEffectUnits.property(effect.kind());
            var initialProperty = new UpgradeEffect(initialKind, Target.ALL, 0, null, effect.type());
            if (effectProperties.contains(initialProperty)) {
                choices = effectProperties;
            } else {
                var withMissing = new ArrayList<>(effectProperties);
                withMissing.add(initialProperty);
                choices = List.copyOf(withMissing);
            }
            var header = new HBox().spacing(2);
            header.layout(style -> style.height(18).flexNone().alignItems(Align.CENTER));
            type.useOverlay(overlay).items(choices.stream().map(EffectRow::propertyLabel).toList())
                    .maxVisibleOptions(7).silentSelectedIndex(choices.indexOf(initialProperty));
            AeStyle.styleComboBox(type);
            type.layout(style -> style.expand().minWidth(0).height(18));
            help.attach(type.headerButton(), this::propertyHelp);
            header.addChild(type);
            percentPreview.layout(style -> style.maxWidth(68).flexNone());
            help.attach(percentPreview, "effect_percent_preview");
            header.addChild(percentPreview);
            var remove = control("remove_effect", 20, () -> {
                if (editingEnabled.getAsBoolean()) {
                    closePopups();
                    effectRows.remove(this);
                    group.remove(parameter);
                    markEdited.run();
                    commitFields.getAsBoolean();
                    filterParameters.run();
                }
            });
            help.attach(remove, "remove_effect");
            header.addChild(remove);
            widget.addChild(header);
            var inputs = new HBox().spacing(2);
            inputs.layout(style -> style.height(18).flexNone());
            target.useOverlay(overlay).items(Arrays.stream(Target.values()).map(UpgradeEffectText::target).toList())
                    .silentSelectedIndex(effect.target().ordinal());
            AeStyle.styleComboBox(target);
            help.attach(target.headerButton(), "effect_target");
            target.layout(style -> style.expand().minWidth(42).height(18));
            inputs.addChild(target);
            value.text(UpgradeEffectText.editorAmount(effect));
            value.placeholder(tr("editor.effect_value"));
            help.attach(value, this::propertyHelp);
            value.on(FocusLostEvent.TYPE, event -> commitDetails());
            inputs.addChild(value);
            unit.useOverlay(overlay).items(List.of(tr("editor.effect_unit_tick"), "%"))
                    .silentSelectedIndex(initialKind == effect.kind() ? 0 : 1);
            AeStyle.styleComboBox(unit);
            help.attach(unit.headerButton(), "effect_unit");
            unit.headerButton().textPadding(2, 2);
            unit.layout(style -> style.size(50, 18).flexNone());
            inputs.addChild(unit);
            widget.addChild(inputs);
            widget.addChild(colorRow(effect));
            type.onSelectionChanged(event -> {
                updateControls();
                var destination = effectGroups.get(EffectCategory.of(property().kind()));
                if (destination != group) {
                    int index = 0;
                    for (var row : effectRows) {
                        if (row == this) {
                            break;
                        }
                        if (row.group == destination) {
                            index++;
                        }
                    }
                    group.moveTo(parameter, destination, index);
                    group = destination;
                    group.expand();
                }
                refreshEffectPreview();
                help.refresh();
                markEdited.run();
                commitFields.getAsBoolean();
                filterParameters.run();
            });
            target.onSelectionChanged(event -> changeDetails());
            unit.onSelectionChanged(event -> {
                refreshEffectPreview();
                help.refresh();
                changeDetails();
            });
            updateControls();
        }

        private HBox colorRow(UpgradeEffect effect) {
            var controls = new HBox().spacing(3);
            var popup = styleColorPicker(colorPicker);
            tooltipColor.text(effect.tooltipColor() == null ? "" : effect.tooltipColor());
            tooltipColor.placeholder(tr("editor.border_auto"));
            tooltipColor.layout(style -> style.expand().minWidth(0).height(18));
            colorPicker.onColorChanged(event -> {
                if (!editingEnabled.getAsBoolean() || !popup.opened()) {
                    return;
                }
                tooltipColor.text(rgbHex(event.newArgb()));
                if (event.newColor().a() != 1f) {
                    colorPicker.argb(0xFF000000 | event.newArgb());
                }
            });
            popup.onOpenChanged(() -> {
                if (!popup.opened()) {
                    commitDetails();
                }
            });
            tooltipColor.onTextChanged(event -> editDetails());
            tooltipColor.on(FocusLostEvent.TYPE, event -> commitDetails());
            value.onTextChanged(event -> editDetails());
            refreshEffectPreview();
            controls.addChild(colorPicker);
            controls.addChild(tooltipColor);
            help.attach(colorPicker.preview(), "effect_tooltip_color");
            help.attach(tooltipColor, "effect_tooltip_color");
            return help.row("effect_tooltip_color", controls);
        }

        /**
         * Updates the visible percentage, color swatch and map tooltip for this card.
         */
        private void refreshEffectPreview() {
            boolean percentage = percentage();
            percentPreview.visibility(percentage ? Visibility.VISIBLE : Visibility.COLLAPSED);
            if (percentage) {
                setLabel(percentPreview, percentageText(), 8);
            }
            try {
                if (!colorPicker.popup().opened()) {
                    var color = UpgradeEffect.normalizeTooltipColor(tooltipColor.text());
                    double amount = value.text().isBlank() ? 0 : UpgradeEffectText.parseEditorAmount(percentage, value.text());
                    colorPicker.color(UpgradeEffectText.color(effect(amount, color)));
                }
            } catch (IllegalArgumentException incomplete) {
                return;
            }
            if (editingEnabled.getAsBoolean()) {
                refreshTooltips.run();
            }
        }

        private String percentageText() {
            if (percentage()) {
                try {
                    return UpgradeEffectText.amount(true, UpgradeEffectText.parseEditorAmount(true, value.text()));
                } catch (NumberFormatException incomplete) {
                    return "";
                }
            }
            return "";
        }

        private static String propertyLabel(UpgradeEffect property) {
            return switch (property.kind()) {
                case IDLE_FLAT -> tr("editor.effect_idle_draw");
                case CRAFT_FLAT -> tr("editor.effect_craft_draw");
                case null, default -> UpgradeEffectText.kind(property);
            };
        }

        /**
         * Shows the supported device and unit selectors for the current property.
         */
        private void updateControls() {
            var property = property();
            boolean hasUnits = property.kind() != null && EnergyEffectUnits.of(property.kind()) != null;
            boolean targeted = targeted(property);
            boolean expandValue = hasUnits && !targeted;
            target.visibility(targeted ? Visibility.VISIBLE : Visibility.COLLAPSED);
            unit.visibility(hasUnits ? Visibility.VISIBLE : Visibility.COLLAPSED);
            value.layout(style -> style.width(expandValue ? SizeValue.AUTO : SizeValue.px(54)).height(18).minWidth(30)
                    .flex(expandValue ? 1 : 0, 1, SizeValue.AUTO));
        }

        private void commitDetails() {
            commitFields.getAsBoolean();
            if (!parameterSearch.text().isBlank()) {
                filterParameters.run();
            }
        }

        private void editDetails() {
            markEdited.run();
            refreshEffectPreview();
        }

        private void changeDetails() {
            markEdited.run();
            commitDetails();
        }

        private boolean dropdownOpen() {
            return type.opened() || target.opened() || unit.opened();
        }

        private void closePopups() {
            type.close();
            target.close();
            unit.close();
            colorPicker.popup().close();
        }

        private UpgradeEffect property() {
            return choices.get(type.selectedIndex());
        }

        private String propertyHelp() {
            var selected = effect(0, null);
            var kind = selected.kind();
            String text = InspectorHelp.description(kind == null ? "effect_type" : "effect." + kind.key());
            return selected.percentage() ? text + "\n" + InspectorHelp.description("effect_percent_preview") : text;
        }

        private boolean percentage() {
            var property = property();
            return property.kind() == null ? property.percentage()
                    : EnergyEffectUnits.resolve(property.kind(), unit.selectedIndex() == 1).percentage();
        }

        private static boolean targeted(UpgradeEffect property) {
            if (property.kind() != null) {
                return property.kind().targeted();
            }
            var registered = property.registeredType();
            return registered == null || registered.targeted();
        }

        private Target scope() {
            return Target.values()[target.selectedIndex()];
        }

        private String searchText() {
            var property = this.property();
            var selectedEffect = effect(0, null);
            var key = selectedEffect.kind() == null ? selectedEffect.type() : selectedEffect.kind().key();
            String text = UpgradeEffectText.kind(selectedEffect) + " " + key.replace('_', ' ') + " "
                    + parameterLabel("effect_value") + " " + value.text() + " " + percentageText()
                    + " " + parameterLabel("effect_tooltip_color") + " " + tooltipColor.text();
            if (!targeted(property)) {
                return text;
            }
            var device = scope();
            return text + " " + UpgradeEffectText.target(device) + " " + device.key().replace('_', ' ');
        }

        /**
         * @return effect as entered in the form
         */
        private UpgradeEffect read() {
            return effect(UpgradeEffectText.parseEditorAmount(percentage(), value.text()),
                    UpgradeEffect.normalizeTooltipColor(tooltipColor.text()));
        }

        private UpgradeEffect effect(double amount, @Nullable String color) {
            var property = property();
            var kind = property.kind() == null ? null : EnergyEffectUnits.resolve(property.kind(), unit.selectedIndex() == 1);
            return new UpgradeEffect(kind, targeted(property) ? scope() : Target.ALL, amount, color, property.type());
        }
    }
}
