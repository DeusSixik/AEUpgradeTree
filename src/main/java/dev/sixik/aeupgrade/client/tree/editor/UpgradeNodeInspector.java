package dev.sixik.aeupgrade.client.tree.editor;

import static dev.sixik.aeupgrade.client.tree.UpgradeTreeText.tr;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.control;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.coordinates;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.decimal;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.field;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.formatDecimal;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.integer;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.label;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.parameterLabel;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.rgbHex;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.styleColorPicker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import dev.sixik.aeupgrade.client.tree.UpgradeTreeText;
import dev.sixik.aeupgrade.client.tree.editor.InspectorGroup.Parameter;
import dev.sixik.aeupgrade.client.tree.editor.UpgradeNodeBatchEdit.Field;
import dev.sixik.aeupgrade.client.tree.map.TreeNodeAssets;
import dev.sixik.aeupgrade.client.widget.AeButton;
import dev.sixik.aeupgrade.client.widget.AeStyle;
import dev.sixik.aeupgrade.client.widget.AeToggleButton;
import dev.sixik.aeupgrade.tree.UpgradeCatalog;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Entry;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Reveal;
import dev.sixik.aeupgrade.tree.UpgradeEffect;
import dev.sixik.aeupgrade.tree.UpgradeNode;
import dev.sixik.aeupgrade.tree.UpgradeNodeStyle;
import dev.sixik.aeupgrade.tree.UpgradeNodeStyle.Shape;
import dev.sixik.unigui.api.event.FocusLostEvent;
import dev.sixik.unigui.api.layout.Align;
import dev.sixik.unigui.api.widget.Visibility;
import dev.sixik.unigui.widgets.containers.HBox;
import dev.sixik.unigui.widgets.containers.VBox;
import dev.sixik.unigui.widgets.display.TextWidget;
import dev.sixik.unigui.widgets.feedback.OverlayLayer;
import dev.sixik.unigui.widgets.feedback.Popup;
import dev.sixik.unigui.widgets.feedback.WindowWidget;
import dev.sixik.unigui.widgets.interaction.ColorPicker;
import dev.sixik.unigui.widgets.interaction.ComboBox;
import dev.sixik.unigui.widgets.interaction.TextField;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * The node fields and appearance editor. The screen owns the draft and its history.
 */
final class UpgradeNodeInspector {
    /**
     * Custom display title.
     */
    private final TextField title = field(UpgradeCatalog.MAX_TITLE_LENGTH);
    /**
     * Custom tooltip content.
     */
    private final TextField description = field(240);
    /**
     * Item ID or PNG path for the node icon.
     */
    private final TextField icon = field(UpgradeIconInput.MAX_LENGTH);
    /**
     * Cost in points.
     */
    private final TextField cost = field(5);
    private final TextField positionX = field(16);
    private final TextField positionY = field(16);
    /**
     * Incoming prerequisite IDs, or -1 for a direct connection to the core.
     */
    private final TextField prerequisites = field(UpgradeCatalog.MAX_NODES * 7);
    /**
     * Optional threshold among incoming nodes.
     */
    private final TextField requiredCount = field(3);
    private final AeToggleButton lockRemaining = new AeToggleButton(tr("editor.lock_remaining"));
    private final AeToggleButton hideDescendants = new AeToggleButton(tr("editor.hide_descendants"));
    private final AeToggleButton hideUntilPrerequisites = new AeToggleButton(tr("editor.hide_until_prerequisites"));
    private final TextField branches = field(UpgradeCatalog.MAX_NODES * 7);
    private final TextField size = field(12);
    private final TextField borderColor = field(7);
    private final TextField borderWidth = field(16);
    private final TextField hoverBorderWidth = field(16);
    private final ColorPicker borderPicker = new ColorPicker();
    private final ComboBox shape = new ComboBox();
    private final UpgradeBackgroundList backgrounds;
    private final EnumSet<Field> editedFields = EnumSet.noneOf(Field.class);
    private final List<BatchInput> batchInputs = List.of(
            new BatchInput(Field.TITLE, title, Entry::title, ""),
            new BatchInput(Field.DESCRIPTION, description, Entry::description, "@example.upgrade.description"),
            new BatchInput(Field.COST, cost, entry -> Integer.toString(entry.node().cost()), ""),
            new BatchInput(Field.X, positionX, entry -> formatDecimal(entry.node().x()), ""),
            new BatchInput(Field.Y, positionY, entry -> formatDecimal(entry.node().y()), ""),
            new BatchInput(Field.INPUTS, prerequisites, entry -> formatIds(UpgradeNodeBatchEdit.inputs(entry.node())), "-1 / 12, 27, 52"),
            new BatchInput(Field.THRESHOLD, requiredCount, entry -> entry.node().requiredCount() > 1
                    ? Integer.toString(entry.node().requiredCount()) : "", tr("editor.prerequisite_auto")),
            new BatchInput(Field.BRANCHES, branches, entry -> formatIds(entry.branches()), "12, 27, 52"),
            new BatchInput(Field.ICON, icon, entry -> UpgradeIconInput.display(entry.icon(), entry.appearance().iconTexture()), ""),
            new BatchInput(Field.SIZE, size, entry -> formatDecimal(entry.appearance().size()), ""),
            new BatchInput(Field.BORDER_COLOR, borderColor, entry -> Objects.toString(entry.appearance().borderColor(), ""), "#RRGGBB"),
            new BatchInput(Field.BORDER_WIDTH, borderWidth, entry -> formatWidth(entry.appearance().borderWidth()), tr("editor.border_auto")),
            new BatchInput(Field.HOVER_BORDER_WIDTH, hoverBorderWidth, entry -> formatWidth(entry.appearance().hoverBorderWidth()), tr("editor.border_auto")));
    private final TextWidget mixedBackgrounds = label(tr("editor.mixed_lists"), 8).clipOverflow();
    private final OverlayLayer overlay;
    private final InspectorHelp help;
    private final BooleanSupplier editable;
    private final BooleanSupplier commit;
    private final List<InspectorGroup> groups = new ArrayList<>();
    private final InspectorGroup connections;
    private final InspectorGroup visibilityRules;
    private @Nullable WindowWidget assetPicker;
    private @Nullable Entry inspected;
    private List<String> layers = List.of();
    private boolean multiple;
    private boolean loading;

    UpgradeNodeInspector(OverlayLayer overlay, InspectorHelp help, VBox properties, BooleanSupplier editable,
                         BooleanSupplier commit, Runnable previewAppearance, Consumer<String> titleChanged,
                         Runnable pickPrerequisite, Consumer<List<String>> applyBackgrounds) {
        this.overlay = overlay;
        this.help = help;
        this.editable = editable;
        this.commit = commit;
        backgrounds = new UpgradeBackgroundList(overlay, help, () -> browseBackground(-1, applyBackgrounds),
                index -> browseBackground(index, applyBackgrounds), applyBackgrounds);
        shape.useOverlay(overlay).items(shapeOptions(false));
        AeStyle.styleComboBox(shape);
        shape.onSelectionChanged(event -> {
            if (!loading && shape.selectedIndex() < Shape.values().length) {
                markEdited(Field.SHAPE);
                previewAppearance.run();
                commit.getAsBoolean();
            }
        });
        title.onTextChanged(event -> {
            if (inspected != null) {
                String name = title.text().isBlank()
                        ? UpgradeTreeText.defaultNodeName(inspected.node()) : UpgradeTreeText.localizedText(title.text().strip());
                titleChanged.accept(name);
            }
        });
        var costRow = help.parameter("cost", cost);
        group(properties, "general", true, help.parameter("name", title), help.parameter("description", description),
                new Parameter(costRow.widget(), costRow.searchText(), () -> !inspectingCore()), positionRow());
        connections = group(properties, "connections", false, prerequisiteRow(pickPrerequisite),
                help.parameter("prerequisite_count", requiredCount),
                toggle(lockRemaining, Field.LOCK_REMAINING, "lock_remaining"), help.parameter("fork", branches));
        visibilityRules = group(properties, "visibility", false, toggle(hideDescendants, Field.HIDE_DESCENDANTS, "hide_descendants"),
                toggle(hideUntilPrerequisites, Field.HIDE_UNTIL_PREREQUISITES, "hide_until_prerequisites"));
        var backgroundControls = new VBox();
        backgroundControls.addChild(mixedBackgrounds);
        backgroundControls.addChild(backgrounds);
        group(properties, "appearance", false, help.parameter("diameter", size), help.parameter("shape", shape), borderColorRow(),
                help.parameter("border_width", borderWidth), help.parameter("border_hover_width", hoverBorderWidth), iconRow(),
                new Parameter(backgroundControls,
                        () -> parameterLabel("backgrounds") + " " + tr("editor.asset_textures") + " PNG "
                                + String.join(" ", layers)));
        for (var field : batchInputs) {
            field.widget().on(FocusLostEvent.TYPE, event -> commit.getAsBoolean());
            field.widget().onTextChanged(event -> {
                markEdited(field.field());
                if (UpgradeNodeBatchEdit.APPEARANCE.contains(field.field())) {
                    previewAppearance.run();
                }
            });
        }
    }

    private Parameter toggle(AeToggleButton button, Field field, String key) {
        button.onCheckedChanged(event -> {
            if (!loading) {
                markEdited(field);
                if (!commit.getAsBoolean()) {
                    editedFields.remove(field);
                    button.silentChecked(event.oldValue());
                }
            }
        });
        return help.toggle(key, button);
    }

    void show(@Nullable Entry entry, List<Entry> chosen) {
        borderPicker.popup().close();
        clearChanges();
        if (entry == null) {
            inspected = null;
            multiple = false;
            showLayers(List.of());
        } else {
            load(entry, chosen);
            showLayers(entry.appearance().backgrounds());
        }
    }

    void showLayers(List<String> layers) {
        this.layers = List.copyOf(layers);
        backgrounds.showLayers(this.layers);
    }

    boolean filter(Predicate<String> matches, boolean searching, boolean visible) {
        boolean found = false;
        for (var group : groups) {
            if (visible && (!inspectingCore() || group != connections && group != visibilityRules)) {
                found |= group.filter(matches, searching, "");
            } else {
                group.widget().visibility(Visibility.COLLAPSED);
            }
        }
        return found;
    }

    private boolean inspectingCore() {
        return inspected != null && inspected.node().id() == UpgradeNode.ROOT;
    }

    boolean hasChanges() {
        return !editedFields.isEmpty();
    }

    EnumSet<Field> changedFields() {
        return editedFields.clone();
    }

    void clearChanges() {
        editedFields.clear();
    }

    void markEdited(Field field) {
        if (!loading && multiple && editable.getAsBoolean()) {
            editedFields.add(field);
        }
    }

    boolean isCoordinate(@Nullable Object widget) {
        return widget == positionX || widget == positionY;
    }

    boolean dropdownOpen() {
        return shape.opened();
    }

    void closeDropdown() {
        shape.close();
    }

    @Nullable Popup openColorPopup() {
        return borderPicker.popup().opened() ? borderPicker.popup() : null;
    }

    @Nullable WindowWidget assetPicker() {
        return assetPicker;
    }

    void loadPosition(UpgradeNode node) {
        positionX.text(formatDecimal(node.x()));
        positionY.text(formatDecimal(node.y()));
    }

    /**
     * Loads common values and marks the fields that differ across the selection.
     */
    void load(Entry entry, List<Entry> chosen) {
        inspected = entry;
        multiple = chosen.size() > 1;
        loading = true;
        try {
            var defaults = new Entry(entry.node(), "", "", "");
            for (var field : batchInputs) {
                boolean mixed = mixed(chosen, field.value());
                field.widget().text(mixed ? "" : field.value().apply(entry));
                String placeholder = switch (field.field()) {
                    case TITLE -> UpgradeTreeText.entryName(defaults);
                    case ICON -> BuiltInRegistries.ITEM.getKey(TreeNodeAssets.entryIcon(defaults).getItem()).toString();
                    default -> field.placeholder();
                };
                field.widget().placeholder(mixed ? tr("editor.mixed_values") : placeholder);
            }
            var appearance = entry.appearance();
            boolean mixedShape = mixed(chosen, value -> value.appearance().shape());
            var options = shapeOptions(mixedShape);
            if (!options.equals(shape.items())) {
                shape.items(options);
                AeStyle.styleComboBoxOptions(shape);
            }
            if (mixedShape) {
                shape.optionButton(Shape.values().length).enabled(false);
            }
            shape.silentSelectedIndex(mixedShape ? Shape.values().length : appearance.shape().ordinal());
            lockRemaining.silentChecked(entry.node().locksRemaining());
            lockRemaining.text(tr(mixed(chosen, value -> value.node().locksRemaining())
                    ? "editor.lock_mixed" : "editor.lock_remaining"));
            showToggle(hideDescendants, "hide_descendants", entry, chosen, e -> e.revealRules().hideDescendantsUntilUnlocked());
            showToggle(hideUntilPrerequisites, "hide_until_prerequisites", entry, chosen,
                    e -> e.revealRules().hideUntilPrerequisites());
            borderPicker.color(TreeNodeAssets.resolveBorderColor(entry.node(), appearance));
            mixedBackgrounds.visibility(mixed(chosen, value -> value.appearance().backgrounds())
                    ? Visibility.VISIBLE : Visibility.COLLAPSED);
        } finally {
            loading = false;
        }
    }

    private static void showToggle(AeToggleButton button, String key, Entry entry, List<Entry> chosen,
                                   Function<Entry, Boolean> value) {
        boolean different = mixed(chosen, value);
        button.silentChecked(!different && value.apply(entry));
        String name = tr("editor." + key);
        button.text(different ? tr("editor.mixed_toggle", name) : name);
    }

    static boolean mixed(List<Entry> entries, Function<Entry, ?> value) {
        var first = value.apply(entries.getFirst());
        return entries.stream().skip(1).anyMatch(entry -> !Objects.equals(first, value.apply(entry)));
    }

    private static List<String> shapeOptions(boolean mixed) {
        var options = new ArrayList<>(Arrays.stream(Shape.values())
                .map(value -> tr("editor.shape." + value.name().toLowerCase(Locale.ROOT))).toList());
        if (mixed) {
            options.add(tr("editor.mixed_values"));
        }
        return options;
    }

    private static String formatIds(List<Integer> ids) {
        return ids.stream().map(String::valueOf).collect(Collectors.joining(", "));
    }

    private static String formatWidth(@Nullable Float width) {
        return width == null ? "" : formatDecimal(width);
    }

    private record BatchInput(Field field, TextField widget, Function<Entry, String> value, String placeholder) {
    }

    @Nullable Entry preview(Entry entry) {
        try {
            var input = UpgradeIconInput.parse(icon.text(), entry.icon());
            var appearance = readAppearance(input, entry.appearance().backgrounds());
            previewBorder(entry.node(), appearance);
            if (input.texture() == null && !input.item().isEmpty()
                    && !BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(input.item()))) {
                return null;
            }
            return new Entry(entry.node(), entry.title(), entry.description(), input.item(), entry.effects(), entry.path(),
                    appearance.equals(entry.appearance()) ? entry.style() : appearance, entry.branches(), entry.reveal());
        } catch (IllegalArgumentException incomplete) {
            // While typing a number or resource path, keep the last valid preview.
            return null;
        }
    }

    void previewBorder(UpgradeNode node, UpgradeNodeStyle appearance) {
        if (!borderPicker.popup().opened()) {
            borderPicker.color(TreeNodeAssets.resolveBorderColor(node, appearance));
        }
    }

    UpgradeNodeBatchEdit readBatch(Entry entry, Set<Field> fields, Supplier<List<UpgradeEffect>> effects) {
        var node = entry.node();
        var updated = node.withCostAndPosition(fields.contains(Field.COST) ? integer(cost) : node.cost(),
                fields.contains(Field.X) ? decimal(positionX) : node.x(), fields.contains(Field.Y) ? decimal(positionY) : node.y());
        if (!UpgradeCatalog.validCoordinate(updated.x()) || !UpgradeCatalog.validCoordinate(updated.y())) {
            throw new IllegalArgumentException(tr("editor.position_error", UpgradeCatalog.MAX_COORDINATE));
        }
        if (fields.contains(Field.INPUTS)) {
            var ids = tokens(prerequisites).stream().map(Integer::valueOf).toList();
            updated = updated.withPrerequisites(ids, UpgradeNodeBatchEdit.defaultThreshold(ids), false);
        }
        var appearance = entry.appearance();
        var input = fields.contains(Field.ICON) ? UpgradeIconInput.parse(icon.text(), entry.icon())
                : new UpgradeIconInput(entry.icon(), appearance.iconTexture());
        var style = new UpgradeNodeStyle(fields.contains(Field.SIZE) ? decimal(size) : appearance.size(),
                fields.contains(Field.SHAPE) ? Shape.values()[shape.selectedIndex()] : appearance.shape(),
                input.texture(), appearance.backgrounds(),
                fields.contains(Field.BORDER_COLOR) ? borderColor.text() : appearance.borderColor(),
                fields.contains(Field.BORDER_WIDTH) ? optionalWidth(borderWidth) : appearance.borderWidth(),
                fields.contains(Field.HOVER_BORDER_WIDTH) ? optionalWidth(hoverBorderWidth) : appearance.hoverBorderWidth()).validated();
        var values = new Entry(updated, title.text().strip(), description.text().strip(), input.item(),
                fields.contains(Field.EFFECTS) ? effects.get() : entry.effects(),
                entry.path(), style, fields.contains(Field.BRANCHES) ? tokens(branches).stream().map(Integer::valueOf).toList()
                : entry.branches(), readReveal());
        Integer threshold = fields.contains(Field.THRESHOLD) && !requiredCount.text().isBlank() ? integer(requiredCount) : null;
        return new UpgradeNodeBatchEdit(fields, values, threshold, lockRemaining.checked());
    }

    UpgradeNode readPrerequisites(UpgradeNode node) {
        var ids = tokens(prerequisites).stream().map(Integer::valueOf).toList();
        int count = requiredCount.text().isBlank()
                ? (ids.isEmpty() || ids.equals(List.of(UpgradeNode.ROOT)) ? 0 : 1) : integer(requiredCount);
        return node.withPrerequisites(ids, count, lockRemaining.checked());
    }

    /**
     * Reads the inspector without changing the draft, so the tooltip can preview unsaved fields.
     */
    Entry read(Entry entry, UpgradeNode linked, Supplier<List<UpgradeEffect>> effects) {
        var update = linked.withCostAndPosition(linked.id() == UpgradeNode.ROOT ? 0 : integer(cost),
                decimal(positionX), decimal(positionY));
        var modifiers = effects.get();
        var input = UpgradeIconInput.parse(icon.text(), entry.icon());
        var appearance = readAppearance(input, entry.appearance().backgrounds());
        var forks = tokens(branches).stream().map(Integer::valueOf).toList();
        return new Entry(update, title.text().strip(), description.text().strip(), input.item(), modifiers,
                entry.path(), appearance.equals(entry.appearance()) ? entry.style() : appearance, forks, readReveal());
    }

    private Reveal readReveal() {
        return new Reveal(hideDescendants.checked(), hideUntilPrerequisites.checked());
    }

    private UpgradeNodeStyle readAppearance(UpgradeIconInput input, List<String> layers) {
        return new UpgradeNodeStyle(decimal(size),
                Shape.values()[shape.selectedIndex()], input.texture(), layers, borderColor.text(),
                optionalWidth(borderWidth), optionalWidth(hoverBorderWidth)).validated();
    }

    private static @Nullable Float optionalWidth(TextField input) {
        String width = input.text().strip().replace(',', '.');
        return width.isEmpty() ? null : Float.valueOf(width);
    }

    private static List<String> tokens(TextField field) {
        return Arrays.stream(field.text().strip().split("[,;\\s]+")).filter(part -> !part.isEmpty()).toList();
    }

    /**
     * @return prerequisite IDs field with a button to add an incoming link from the map
     */
    private Parameter prerequisiteRow(Runnable pick) {
        var row = help.row("prerequisite_ids", prerequisites);
        var picker = control("pick", 40, pick);
        help.attach(picker, "pick");
        row.addChild(picker);
        return new Parameter(row, () -> parameterLabel("prerequisite_ids"));
    }

    private Parameter positionRow() {
        help.attach(positionX, "x");
        help.attach(positionY, "y");
        return new Parameter(help.row("position", coordinates(positionX, positionY)),
                () -> parameterLabel("position") + " X Y");
    }

    private Parameter borderColorRow() {
        var controls = new HBox().spacing(3);
        controls.layout(style -> style.alignItems(Align.CENTER));
        borderPicker.overlayLayer(overlay);
        var popup = styleColorPicker(borderPicker);
        borderPicker.onColorChanged(event -> {
            if (loading || !popup.opened()) {
                return;
            }
            borderColor.text(rgbHex(event.newArgb()));
            if (event.newColor().a() != 1f) {
                borderPicker.argb(0xFF000000 | event.newArgb());
            }
        });
        popup.onOpenChanged(() -> {
            if (!popup.opened()) {
                commit.getAsBoolean();
            }
        });
        borderColor.layout(style -> style.expand().minWidth(0).height(18));
        controls.addChild(borderPicker);
        controls.addChild(borderColor);
        help.attach(borderColor, "border_color");
        help.attach(borderPicker.preview(), "border_color");
        return new Parameter(help.row("border_color", controls), () -> parameterLabel("border_color") + " HEX RGB");
    }

    private Parameter iconRow() {
        var controls = new HBox().spacing(3);
        controls.layout(style -> style.alignItems(Align.CENTER));
        var browse = new AeButton("…");
        browse.layout(style -> style.size(19, 18).flexNone());
        browse.onClick(event -> {
            var iconId = ResourceLocation.tryParse(icon.text().strip());
            boolean textureSelected = iconId != null && UpgradeIconInput.isTexturePath(iconId.getPath());
            browseAssets("editor.asset_icon_title", false, textureSelected, id -> {
                icon.text(id);
                markEdited(Field.ICON);
                commit.getAsBoolean();
            });
        });
        icon.layout(style -> style.expand().minWidth(0).height(18));
        controls.addChild(browse);
        controls.addChild(icon);
        help.attach(icon, "icon");
        help.attach(browse, "icon");
        return new Parameter(help.row("icon", controls),
                () -> parameterLabel("icon") + " " + tr("editor.asset_textures") + " " + tr("editor.asset_items") + " PNG");
    }

    private void browseBackground(int index, Consumer<List<String>> applyBackgrounds) {
        var currentLayers = layers;
        boolean append = index < 0;
        if (append && currentLayers.size() >= UpgradeNodeStyle.MAX_BACKGROUNDS) {
            return;
        }
        browseAssets(append ? "editor.asset_background_title" : "editor.background_replace", true, true, id -> {
            var updated = new ArrayList<>(currentLayers);
            if (append) {
                updated.add(id);
            } else {
                updated.set(index, id);
            }
            applyBackgrounds.accept(updated);
        });
    }

    private void browseAssets(String titleKey, boolean texturesOnly, boolean startWithTextures, Consumer<String> picked) {
        if (!editable.getAsBoolean() || assetPicker != null) {
            return;
        }
        assetPicker = UpgradeAssetPicker.open(overlay, tr(titleKey), texturesOnly, startWithTextures, picked);
        assetPicker.onClosed(event -> assetPicker = null);
    }

    private InspectorGroup group(VBox properties, String key, boolean expanded, Parameter... parameters) {
        var group = new InspectorGroup(key, expanded, null);
        for (var parameter : parameters) {
            group.add(parameter);
        }
        groups.add(group);
        properties.addChild(group.widget());
        return group;
    }

}
