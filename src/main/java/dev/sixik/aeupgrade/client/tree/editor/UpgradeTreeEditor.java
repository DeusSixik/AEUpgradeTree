package dev.sixik.aeupgrade.client.tree.editor;

import static dev.sixik.aeupgrade.client.tree.UpgradeTreeText.tr;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.control;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.field;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.integer;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.label;
import static dev.sixik.aeupgrade.client.tree.editor.UpgradeLinkInspector.linkStyleKey;
import static dev.sixik.aeupgrade.client.widget.AeStyle.setLabel;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import dev.sixik.aeupgrade.client.tree.UpgradeTreeText;
import dev.sixik.aeupgrade.client.tree.editor.UpgradeEditorCanvas.AnchorPosition;
import dev.sixik.aeupgrade.client.tree.editor.UpgradeEditorCanvas.ContextPoint;
import dev.sixik.aeupgrade.client.tree.editor.UpgradeEditorCanvas.LinkChange;
import dev.sixik.aeupgrade.client.tree.editor.UpgradeEditorCanvas.LinkId;
import dev.sixik.aeupgrade.client.tree.editor.UpgradeEditorCanvas.LinkPick;
import dev.sixik.aeupgrade.client.tree.editor.UpgradeEditorCanvas.LinkSelection;
import dev.sixik.aeupgrade.client.tree.editor.UpgradeEditorHistory.Draft;
import dev.sixik.aeupgrade.client.tree.editor.UpgradeNodeBatchEdit.Field;
import dev.sixik.aeupgrade.client.widget.AeButton;
import dev.sixik.aeupgrade.client.widget.AeScrollbar;
import dev.sixik.aeupgrade.client.widget.AeStyle;
import dev.sixik.aeupgrade.client.widget.AeToggleButton;
import dev.sixik.aeupgrade.platform.PlatformNetwork;
import dev.sixik.aeupgrade.tree.UpgradeCatalog;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Core;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Definition;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Entry;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.ExperimentalSettings;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.NodeSettings;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Path;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Kind;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute.Point;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute.Style;
import dev.sixik.aeupgrade.tree.UpgradeNode;
import dev.sixik.aeupgrade.tree.UpgradeNode.Sector;
import dev.sixik.aeupgrade.tree.UpgradePlan.Availability;
import dev.sixik.aeupgrade.tree.UpgradeTreeConfig;
import dev.sixik.aeupgrade.tree.UpgradeTreeExamples;
import dev.sixik.aeupgrade.tree.UpgradeTreePackets.Save;
import dev.sixik.aeupgrade.tree.UpgradeTreePackets.Snapshot;
import dev.sixik.aeupgrade.tree.research.ResearchTaskPackets.EditResponse;
import dev.sixik.unigui.api.event.FocusLostEvent;
import dev.sixik.unigui.api.event.PointerMovedEvent;
import dev.sixik.unigui.api.input.KeyCodes;
import dev.sixik.unigui.api.input.KeyModifiers;
import dev.sixik.unigui.api.layout.Align;
import dev.sixik.unigui.api.layout.Justify;
import dev.sixik.unigui.api.layout.Overflow;
import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.screen.UniScreen;
import dev.sixik.unigui.api.text.RichText;
import dev.sixik.unigui.api.widget.Visibility;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftFonts;
import dev.sixik.unigui.widgets.containers.HBox;
import dev.sixik.unigui.widgets.containers.ScrollView;
import dev.sixik.unigui.widgets.containers.VBox;
import dev.sixik.unigui.widgets.display.Text;
import dev.sixik.unigui.widgets.display.TextWidget;
import dev.sixik.unigui.widgets.feedback.ContextMenu;
import dev.sixik.unigui.widgets.feedback.OverlayLayer;
import dev.sixik.unigui.widgets.feedback.Popup;
import dev.sixik.unigui.widgets.interaction.TextField;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Operator-only UniGUI form for editing a draft of the server's upgrade tree.
 */
public final class UpgradeTreeEditor {
    /**
     * Editor currently waiting for input or a save result.
     */
    private static @Nullable UpgradeTreeEditor active;
    /**
     * Background behind the editor panel.
     */
    private static final MutableColor SCRIM = MutableColor.rgba255(15, 13, 24, 175);
    /**
     * Draft entries in stable display order, starting with the core.
     */
    private final ArrayList<Entry> entries;
    private final UpgradeEditorHistory history = new UpgradeEditorHistory();
    private String committedBudget;
    private String committedStartingPoints;
    private String committedRefundPercent;
    private boolean operatorOnly;
    private boolean dropResearchOnBreak;
    /**
     * Custom path metadata carried forward from earlier config versions.
     */
    private final List<Path> legacyPaths;
    /**
     * Shared rendering options for the saved tree.
     */
    private ExperimentalSettings experimental;
    private NodeSettings nodeSettings;
    /**
     * Group selected on the map and the in-editor clipboard.
     */
    private final LinkedHashSet<Integer> selection = new LinkedHashSet<>();
    private List<Entry> clipboard = List.of();
    /**
     * Source snapshot for effect-only pastes, independent of copied map nodes.
     */
    private @Nullable Entry effectClipboard;
    /**
     * Server graph and allocation to return to when the draft is discarded.
     */
    private Snapshot baseline;
    private final Consumer<Snapshot> returnToTree;
    /**
     * First ID that has yet to be issued.
     */
    private int nextId;
    /**
     * Selected row index in the full draft, or -1 with no nodes.
     */
    private int selected = -1;
    /**
     * True while the server processes the submitted document.
     */
    private boolean pending;
    /**
     * Suppresses edit events while restoring inspector values.
     */
    private boolean loadingForm;
    /**
     * A differently revised document must be reopened before it can be saved.
     */
    private boolean stale;

    /**
     * Budget field in points per network.
     */
    private final TextField budget = field(5);
    private final TextField startingPoints = field(5);
    private final TextField refundPercent = field(3);
    private final AeToggleButton operatorOnlyToggle = new AeToggleButton(tr("editor.operator_only"));
    private final AeToggleButton dropResearchOnBreakToggle = new AeToggleButton(tr("editor.drop_research_on_break"));
    private final TextField search = field(100);
    private final TextField parameterSearch = field(100);
    /**
     * Editable graph in the left pane.
     */
    private final UpgradeEditorCanvas canvas;
    /**
     * Hosts context menus and drop-downs above the map and inspector.
     */
    private final OverlayLayer overlay;
    private final InspectorHelp help;
    /**
     * Most recent context menu, removed before opening the next.
     */
    private @Nullable ContextMenu contextMenu;
    /**
     * Current outgoing connection source, or DISCONNECTED while inactive.
     */
    private int connectionSource = UpgradeNode.DISCONNECTED;
    /**
     * Whether the prerequisite picker is waiting for a map target.
     */
    private boolean pickingPrerequisite;
    /**
     * The right pane alternates between selection properties and tree settings.
     */
    private final VBox nodeInspector = new VBox();
    /**
     * Scrollable tree settings shown in place of the node inspector.
     */
    private final ScrollView settingsScroll;
    private final AeButton settings = control("settings", 82, this::toggleSettings);
    /**
     * Task settings modal while the operator is editing the server's shared task config.
     */
    private @Nullable ResearchTaskEditor taskEditor;
    private final AeToggleButton starfieldToggle = new AeToggleButton(tr("editor.starfield"));
    private final AeToggleButton animatedLinksToggle = new AeToggleButton(tr("editor.animated_links"));
    private final AeToggleButton availableNodePulsesToggle = new AeToggleButton(tr("editor.available_node_pulses"));
    private final AeToggleButton showRequirementsToggle = new AeToggleButton(tr("editor.show_requirements"));
    /**
     * Fields for editing the currently selected node or route.
     */
    private final VBox properties = new VBox();
    private final UpgradeNodeInspector nodeFields;
    private final UpgradeLinkInspector linkInspector;
    private final UpgradeEffectInspector effects;
    private final ScrollView inspectorScroll;
    private final TextWidget noParameters = label(tr("editor.no_parameters"), 8).clipOverflow();
    /**
     * Node ID heading.
     */
    private final TextWidget heading = new Text().color(AeStyle.TEXT_COLOR);
    /**
     * Validation and save status text.
     */
    private final TextWidget status = new Text().color(AeStyle.TEXT_COLOR).clipOverflow();
    /**
     * Saves the current draft to the server.
     */
    private final AeButton save = control("save", 80, this::save);
    /**
     * Removes the selected draft node.
     */
    private final AeButton delete = control("delete", 60, this::delete);
    /**
     * UniGUI screen instance.
     */
    private final UniScreen screen;

    /**
     * Builds the form from the confirmed document and current map selection.
     *
     * @param baseline     authoritative snapshot to edit
     * @param graph        parsed version of that snapshot
     * @param selectedId   node selected on the map, possibly absent
     * @param returnToTree opens the viewer with the confirmed snapshot
     */
    private UpgradeTreeEditor(Snapshot baseline, UpgradeCatalog graph, int selectedId, Consumer<Snapshot> returnToTree) {
        this.baseline = baseline;
        this.returnToTree = returnToTree;
        entries = new ArrayList<>();
        entries.add(graph.definition().core().asEntry());
        entries.addAll(graph.definition().nodes());
        legacyPaths = graph.definition().paths();
        experimental = graph.definition().experimental();
        nodeSettings = graph.definition().nodeSettings();
        nextId = graph.definition().nextId();
        committedBudget = Integer.toString(graph.budget());
        budget.text(committedBudget);
        committedStartingPoints = Integer.toString(graph.definition().initialPoints());
        startingPoints.text(committedStartingPoints);
        committedRefundPercent = Integer.toString(graph.definition().refundPercent());
        refundPercent.text(committedRefundPercent);
        operatorOnly = graph.definition().operatorOnly();
        dropResearchOnBreak = graph.definition().dropResearchOnBreak();

        var root = new VBox();
        root.background(SCRIM);
        root.layout(style -> style.fill().padding(8).alignItems(Align.CENTER).justifyContent(Justify.CENTER));
        var frame = new VBox().spacing(5);
        frame.backgroundTexture(AeStyle.PANEL).backgroundVisible(true).sliceBorders(3);
        frame.layout(style -> style.fill().maxWidth(714).maxHeight(475).padding(8).alignItems(Align.STRETCH));
        root.addChild(frame);
        overlay = new OverlayLayer(root);
        help = new InspectorHelp(overlay);
        canvas = new UpgradeEditorCanvas(entries, this::selectOnMap, this::selectBox, this::moveNodes, this::openContextMenu,
                this::pickLink, this::commitLinkChange, overlay, this::hoverText);
        overlay.on(PointerMovedEvent.TYPE, event -> canvas.pointer(event.rootX(), event.rootY()));

        search.placeholder(tr("editor.search_hint"));
        search.layout(style -> style.height(19).flexNone());
        search.onTextChanged(event -> applySearch());
        help.attach(search, "search");
        frame.addChild(search);

        var header = new HBox().spacing(5);
        header.layout(style -> style.height(20).flexNone().alignItems(Align.CENTER));
        var caption = label(tr("editor.title"), 12);
        caption.layout(style -> style.expand().minWidth(0));
        header.addChild(caption);
        var shortcuts = new AeButton("?");
        shortcuts.focusable(false);
        shortcuts.layout(style -> style.size(18, 18).flexNone());
        help.attachShortcuts(shortcuts);
        header.addChild(shortcuts);
        var grid = new AeButton(tr("editor.grid_on"));
        grid.layout(style -> style.size(70, 18).flexNone());
        grid.onClick(event -> grid.label(tr(canvas.toggleSnap() ? "editor.grid_on" : "editor.grid_off")));
        help.attach(grid, "grid");
        header.addChild(grid);
        header.addChild(control("tasks.button", 73, this::openTasks));
        header.addChild(settings);
        frame.addChild(header);

        var body = new HBox().spacing(7);
        body.layout(style -> style.expand().minHeight(0).alignItems(Align.STRETCH));
        canvas.layout(style -> style.expand().minWidth(0).minHeight(0));
        body.addChild(canvas);
        var inspector = new VBox();
        inspector.layout(style -> style.widthPercent(43).minWidth(150).maxWidth(286).flexNone().alignItems(Align.STRETCH));
        nodeInspector.spacing(4).layout(style -> style.expand().minHeight(0).alignItems(Align.STRETCH));
        inspector.addChild(nodeInspector);
        settingsScroll = createInspectorScroll(buildSettingsInspector());
        settingsScroll.visibility(Visibility.COLLAPSED);
        inspector.addChild(settingsScroll);
        parameterSearch.placeholder(tr("editor.parameter_search"));
        parameterSearch.layout(style -> style.height(18).flexNone());
        help.attach(parameterSearch, "parameter_search");
        nodeInspector.addChild(parameterSearch);
        heading.clipOverflow();
        nodeInspector.addChild(heading);
        properties.spacing(3);
        properties.layout(style -> style.alignItems(Align.STRETCH));
        nodeFields = new UpgradeNodeInspector(overlay, help, properties, () -> selected >= 0 && !loadingForm && !pending,
                this::commitFields, this::refreshAppearance, this::updateHeading,
                this::togglePrerequisitePicker, this::applyBackgrounds);
        effects = new UpgradeEffectInspector(overlay, help, parameterSearch,
                () -> selected >= 0 && !pending && !loadingForm, this::commitFields,
                () -> nodeFields.markEdited(Field.EFFECTS), () -> selection.forEach(canvas::refreshTooltip),
                this::filterParameters);
        properties.addChild(effects.widget());
        linkInspector = new UpgradeLinkInspector(overlay, help, properties, canvas, () -> pending, this::commitFields,
                this::mapPoint, this::applyLinkChange, this::removeLinkPoint, message -> setLabel(status, message, 8));
        noParameters.visibility(Visibility.COLLAPSED);
        properties.addChild(noParameters);
        inspectorScroll = createInspectorScroll(properties);
        nodeInspector.addChild(inspectorScroll);
        budget.on(FocusLostEvent.TYPE, event -> commitFields());
        startingPoints.on(FocusLostEvent.TYPE, event -> commitFields());
        refundPercent.on(FocusLostEvent.TYPE, event -> commitFields());
        parameterSearch.onTextChanged(event -> {
            filterParameters();
            inspectorScroll.scrollTo(0, 0);
        });
        body.addChild(inspector);
        frame.addChild(body);

        status.layout(style -> style.height(10).flexNone());
        frame.addChild(status);
        var controls = new HBox().spacing(4);
        controls.layout(style -> style.height(19).flexNone().justifyContent(Justify.SPACE_BETWEEN));
        controls.addChild(control("add", 66, this::add));
        controls.addChild(delete);
        controls.addChild(control("fit", 56, canvas::fit));
        controls.addChild(control("cancel", 80, this::back));
        controls.addChild(save);
        frame.addChild(controls);

        screen = new UniScreen(tr("editor.title"), overlay).scaleWithGameGui(true).pauseGame(false)
                .keyboardInputHandler(this::keyPressed)
                .defaultFont(MinecraftFonts.defaultFace()).onClose(() -> {
                    if (active == this) {
                        active = null;
                    }
                });
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).node().id() == selectedId) {
                selected = i;
                break;
            }
        }
        if (selected < 0 && !entries.isEmpty()) {
            selected = 0;
        }
        if (selected >= 0) {
            selection.add(entries.get(selected).node().id());
        }
        loadForm();
    }

    /**
     * Creates the viewport shared by the editor's inspector panes.
     *
     * @param content rows and groups to lay out inside the viewport
     * @return a vertically scrollable view with a reserved AE2 scrollbar lane
     */
    private static ScrollView createInspectorScroll(VBox content) {
        var scroll = new ScrollView(content);
        AeScrollbar.install(scroll);
        scroll.scrollStep(36).tickCullingEnabled(true);
        scroll.layout(style -> style.expand().minWidth(0).minHeight(0)
                .overflowX(Overflow.HIDDEN).overflowY(Overflow.AUTO));
        return scroll;
    }

    /**
     * Builds the collapsible groups for tree settings.
     *
     * @return the settings content at its natural height
     */
    private VBox buildSettingsInspector() {
        var content = new VBox();
        content.spacing(4).layout(style -> style.alignItems(Align.STRETCH));
        var group = new InspectorGroup("experimental", false, null);
        group.add(help.toggle("starfield", starfieldToggle));
        group.add(help.toggle("animated_links", animatedLinksToggle));
        group.add(help.toggle("available_node_pulses", availableNodePulsesToggle));
        content.addChild(group.widget());
        starfieldToggle.onCheckedChanged(event -> setExperimental(
                new ExperimentalSettings(event.newValue(), experimental.animatedLinks(), experimental.availableNodePulses())));
        animatedLinksToggle.onCheckedChanged(event -> setExperimental(
                new ExperimentalSettings(experimental.starfield(), event.newValue(), experimental.availableNodePulses())));
        availableNodePulsesToggle.onCheckedChanged(event -> setExperimental(
                new ExperimentalSettings(experimental.starfield(), experimental.animatedLinks(), event.newValue())));

        var nodes = new InspectorGroup("nodes", false, null);
        nodes.add(help.toggle("show_requirements", showRequirementsToggle));
        content.addChild(nodes.widget());
        showRequirementsToggle.onCheckedChanged(event -> setShowRequirements(event.newValue()));

        var research = new InspectorGroup("research_points", false, null);
        research.add(help.parameter("starting_points", startingPoints));
        research.add(help.parameter("budget", budget));
        research.add(help.parameter("refund_percent", refundPercent));
        research.add(help.toggle("drop_research_on_break", dropResearchOnBreakToggle));
        research.add(help.toggle("operator_only", operatorOnlyToggle));
        content.addChild(research.widget());
        dropResearchOnBreakToggle.onCheckedChanged(event -> {
            if (commitFields()) {
                var before = draft();
                dropResearchOnBreak = event.newValue();
                history.record(before, draft());
            }
            refreshSettingsControls();
        });
        operatorOnlyToggle.onCheckedChanged(event -> {
            if (commitFields()) {
                var before = draft();
                operatorOnly = event.newValue();
                history.record(before, draft());
            }
            refreshSettingsControls();
        });
        refreshSettingsControls();
        return content;
    }

    private void toggleSettings() {
        if (!commitFields()) {
            return;
        }
        showSettings(settingsScroll.visibility() == Visibility.COLLAPSED);
    }

    /**
     * Opens the task settings while retaining the current tree draft.
     */
    private void openTasks() {
        if (!pending && taskEditor == null) {
            taskEditor = new ResearchTaskEditor(overlay, baseline.controller(), this::updateTaskAvailability,
                    () -> taskEditor = null);
        }
    }

    /**
     * Receives task config replies only for the currently shown operator editor.
     *
     * @param response confirmed config or save error from the server
     */
    public static void receiveTasks(EditResponse response) {
        var editor = active;
        if (editor != null && editor.taskEditor != null && editor.baseline.controller().equals(response.controller())) {
            editor.taskEditor.receive(response);
        }
    }

    /**
     * Keeps the return snapshot in sync with the independently saved task configuration.
     *
     * @param available whether the server is offering at least one task
     */
    private void updateTaskAvailability(boolean available) {
        baseline = new Snapshot(baseline.controller(), baseline.purchases(), baseline.open(), baseline.message(), baseline.revision(),
                baseline.catalog(), baseline.totalPoints(), baseline.refresh(), baseline.allUnlocked(), available);
    }

    private void showSettings(boolean open) {
        nodeInspector.visibility(open ? Visibility.COLLAPSED : Visibility.VISIBLE);
        settingsScroll.visibility(open ? Visibility.VISIBLE : Visibility.COLLAPSED);
        settings.label(tr(open ? "editor.node_parameters" : "editor.settings"));
    }

    private void setExperimental(ExperimentalSettings settings) {
        if (commitFields()) {
            var before = draft();
            experimental = settings;
            history.record(before, draft());
        }
        refreshSettingsControls();
    }

    private void setShowRequirements(boolean show) {
        if (commitFields()) {
            var before = draft();
            nodeSettings = new NodeSettings(show);
            history.record(before, draft());
        }
        refreshSettingsControls();
    }

    private void refreshSettingsControls() {
        operatorOnlyToggle.silentChecked(operatorOnly);
        dropResearchOnBreakToggle.silentChecked(dropResearchOnBreak);
        starfieldToggle.silentChecked(experimental.starfield());
        animatedLinksToggle.silentChecked(experimental.animatedLinks());
        availableNodePulsesToggle.silentChecked(experimental.availableNodePulses());
        showRequirementsToggle.silentChecked(nodeSettings.showRequirements());
    }

    /**
     * @param baseline     current server document
     * @param graph        parsed server catalog
     * @param selectedId   map selection to inspect first
     * @param returnToTree opens the viewer with the confirmed snapshot
     */
    public static void open(Snapshot baseline, UpgradeCatalog graph, int selectedId, Consumer<Snapshot> returnToTree) {
        var editor = new UpgradeTreeEditor(baseline, graph, selectedId, returnToTree);
        active = editor;
        editor.screen.openGui();
    }

    /**
     * @param snapshot server response
     * @return whether the active editor consumed the response
     */
    public static boolean receive(Snapshot snapshot) {
        var editor = active;
        if (editor == null || !editor.pending || snapshot.refresh() || snapshot.open()
                || !editor.baseline.controller().equals(snapshot.controller())) {
            return false;
        }
        editor.pending = false;
        if (snapshot.message().equals("aeupgrade.tree.feedback.editor_saved")) {
            active = null;
            editor.returnToTree.accept(snapshot);
        } else {
            if (!snapshot.catalog().isEmpty()) {
                editor.baseline = snapshot;
                editor.stale = true;
            }
            editor.save.enabled(!editor.stale);
            setLabel(editor.status, UpgradeTreeText.feedback(snapshot.message()), 8);
        }
        return true;
    }

    /**
     * @param id       clicked node or the core
     * @param additive whether to toggle the node within the current selection
     * @return whether the press can start dragging the node
     */
    private boolean selectOnMap(int id, boolean additive) {
        if (pending || (canvas.selectedLink() != null || nodeFields.hasChanges()) && !commitFields()) {
            return false;
        }
        clearLinkSelection();
        if (pickingPrerequisite && id != UpgradeNode.DISCONNECTED) {
            if (!selection.contains(id)) {
                pickingPrerequisite = false;
                connectSelection(id);
            }
            return false;
        }
        if (id == UpgradeNode.ROOT) {
            if (!commitFields()) {
                return false;
            }
            showSettings(false);
            if (!selection.contains(id)) {
                setSelection(id);
            }
            return true;
        }
        if (id >= 0 && selection.contains(id) && !additive) {
            return true;
        }
        if (!commitFields()) {
            return false;
        }
        if (!additive) {
            selection.clear();
        } else if (id >= 0) {
            selection.remove(UpgradeNode.ROOT);
        }
        boolean added = id >= 0 && selection.add(id);
        if (id >= 0 && additive && !added) {
            selection.remove(id);
        }
        selected = indexOf(added ? id : selection.isEmpty() ? UpgradeNode.DISCONNECTED : selection.getLast());
        loadForm();
        return added && !additive;
    }

    /**
     * Applies the visible nodes covered by the canvas selection rectangle.
     */
    private void selectBox(List<Integer> ids, boolean additive) {
        if (pending || additive && ids.isEmpty() || !commitFields()) {
            return;
        }
        clearLinkSelection();
        if (!additive) {
            selection.clear();
        }
        selection.remove(UpgradeNode.ROOT);
        selection.addAll(ids);
        selected = selection.isEmpty() ? -1 : indexOf(selection.getLast());
        loadForm();
    }

    /**
     * @param point clicked node, link, or empty map position
     */
    private void openContextMenu(ContextPoint point) {
        if (pending) {
            return;
        }
        if (point.link() != null && !pickingPrerequisite) {
            if (commitFields()) {
                openLinkMenu(point, point.link());
            }
            return;
        }
        if (canvas.selectedLink() != null && !commitFields()) {
            return;
        }
        clearLinkSelection();
        if (point.nodeId() != UpgradeNode.DISCONNECTED && !selection.contains(point.nodeId())) {
            if (!commitFields()) {
                return;
            }
            setSelection(point.nodeId());
        }
        if (contextMenu != null) {
            overlay.removeOverlay(contextMenu.close());
        }
        var menu = new ContextMenu();
        menu.backgroundTexture(AeStyle.PANEL).backgroundVisible(true).sliceBorders(3);
        if (point.nodeId() == UpgradeNode.DISCONNECTED) {
            menu.item(control("create_here", 132, () -> addDetached(point)));
            menu.item(control("add_examples", 132, () -> addExamples(point)));
        } else {
            if (connectionSource != UpgradeNode.DISCONNECTED && point.nodeId() >= 0 && point.nodeId() != connectionSource) {
                menu.item(control("link_to", 132, () -> finishConnection(point.nodeId())));
            }
            menu.item(control("link_from", 132, () -> startConnection(point.nodeId())));
            var source = entries.get(indexOf(point.nodeId()));
            menu.item(control("copy_effects", 132, () -> copyEffects(source.node().id()))
                    .enabled(!source.effects().isEmpty()));
            menu.item(control("paste_effects", 132, this::pasteEffects).enabled(effectClipboard != null));
        }
        menu.item(control("copy", 132, this::copySelection).enabled(canCopyOrDelete()));
        menu.item(control("paste", 132, () -> paste(point)).enabled(!clipboard.isEmpty()));
        menu.item(control("delete", 132, this::delete).enabled(canCopyOrDelete()));
        if (connectionSource != UpgradeNode.DISCONNECTED) {
            menu.item(control("cancel_link", 132, this::cancelConnection));
        }
        contextMenu = menu;
        overlay.addOverlay(menu.openAt(point.rootX(), point.rootY()));
    }

    private void openLinkMenu(ContextPoint point, LinkPick hit) {
        var route = routeOf(hit.link());
        if (route == null) {
            return;
        }
        focusLink(hit.selection());
        if (contextMenu != null) {
            overlay.removeOverlay(contextMenu.close());
        }
        var menu = new ContextMenu();
        menu.backgroundTexture(AeStyle.PANEL).backgroundVisible(true).sliceBorders(3);
        if (hit.pointIndex() >= 0) {
            menu.item(control("link_remove_point", 164, () -> removeLinkPoint(hit.selection())));
        } else {
            menu.item(control("link_add_point", 164, () -> addLinkPoint(hit))
                    .enabled(route.points().size() < UpgradeLinkRoute.MAX_POINTS));
        }
        for (var style : UpgradeLinkRoute.Style.values()) {
            menu.item(control(linkStyleKey(style), 164,
                    () -> commitLinkChange(new LinkChange(hit.selection(),
                            new UpgradeLinkRoute(route.source(), style, route.points())))).enabled(route.style() != style));
        }
        menu.item(control("link_reset", 164,
                () -> commitLinkChange(new LinkChange(new LinkSelection(hit.link(), -1), null)))
                .enabled(route.style() != UpgradeLinkRoute.Style.POLYLINE || !route.points().isEmpty()));
        contextMenu = menu;
        overlay.addOverlay(menu.openAt(point.rootX(), point.rootY()));
    }

    private void pickLink(LinkPick hit) {
        if (pickingPrerequisite || !commitFields() || routeOf(hit.link()) == null) {
            return;
        }
        var selectedLink = canvas.selectedLink();
        if (hit.pointIndex() < 0 && selectedLink != null && selectedLink.link().equals(hit.link())) {
            addLinkPoint(hit);
        } else {
            focusLink(hit.selection());
        }
    }

    private void focusLink(LinkSelection link) {
        var route = routeOf(link.link());
        if (route == null) {
            return;
        }
        boolean newLink = !link.link().equals(linkInspector.link());
        if (selected >= 0 || !selection.isEmpty()) {
            selection.clear();
            selected = -1;
            loadForm();
        }
        canvas.highlightLink(link);
        properties.visibility(Visibility.VISIBLE);
        String start = draftName(link.link().source());
        setLabel(heading, tr("editor.link_selected", start, draftName(link.link().target())), 10);
        linkInspector.show(link.link(), route);
        if (newLink) {
            showSettings(false);
            inspectorScroll.scrollTo(0, 0);
        }
        filterParameters();
        setLabel(status, "", 8);
    }

    private @Nullable UpgradeLinkRoute routeOf(LinkId link) {
        int index = indexOf(link.target());
        if (index < 0 || !entries.get(index).node().connectedFrom(link.source())) {
            return null;
        }
        var route = entries.get(index).node().linkRoute(link.source());
        return route == null ? new UpgradeLinkRoute(link.source(), UpgradeLinkRoute.Style.POLYLINE, List.of()) : route;
    }

    private void addLinkPoint(LinkPick hit) {
        var route = routeOf(hit.link());
        if (route == null || hit.insertAt() > route.points().size()) {
            return;
        }
        if (route.points().size() >= UpgradeLinkRoute.MAX_POINTS) {
            setLabel(status, tr("editor.link_point_limit", UpgradeLinkRoute.MAX_POINTS), 8);
            return;
        }
        var points = new ArrayList<>(route.points());
        var point = new Point(canvas.place(hit.point().x()), canvas.place(hit.point().y()));
        if (points.contains(point)) {
            return;
        }
        points.add(hit.insertAt(), point);
        commitLinkChange(new LinkChange(new LinkSelection(hit.link(), hit.insertAt()),
                new UpgradeLinkRoute(route.source(), route.style(), points)));
    }

    private void removeLinkPoint(LinkSelection selectedLink) {
        if (!commitFields()) {
            return;
        }
        var route = routeOf(selectedLink.link());
        if (route == null || selectedLink.pointIndex() < 0 || selectedLink.pointIndex() >= route.points().size()) {
            return;
        }
        var points = new ArrayList<>(route.points());
        points.remove(selectedLink.pointIndex());
        applyLinkChange(new LinkChange(new LinkSelection(selectedLink.link(), -1),
                new UpgradeLinkRoute(route.source(), route.style(), points)));
    }

    private void commitLinkChange(LinkChange change) {
        if (commitFields()) {
            applyLinkChange(change);
        }
    }

    private void applyLinkChange(LinkChange change) {
        var link = change.selection().link();
        if (routeOf(link) == null) {
            clearLinkSelection();
            return;
        }
        int index = indexOf(link.target());
        var before = draft();
        var entry = entries.get(index);
        var updated = entry.withNode(entry.node().withLinkRoute(link.source(), change.route()));
        entries.set(index, updated);
        canvas.put(updated);
        focusLink(change.selection());
        history.record(before, draft());
    }

    private void clearLinkSelection() {
        if (canvas.selectedLink() != null) {
            canvas.highlightLink(null);
            linkInspector.clear();
            if (selected < 0) {
                setLabel(heading, tr("editor.empty"), 10);
                properties.visibility(Visibility.COLLAPSED);
            } else {
                filterParameters();
            }
            setLabel(status, "", 8);
        }
    }

    /**
     * @param source node or ME core where the prerequisite begins
     */
    private void startConnection(int source) {
        if (!commitFields()) {
            return;
        }
        pickingPrerequisite = false;
        connectionSource = source;
        canvas.linkFrom(source);
        setLabel(status, tr("editor.connect_hint"), 8);
    }

    /**
     * @param target dependent node clicked in the context menu
     */
    private void finishConnection(int target) {
        connectNodes(connectionSource, target);
    }

    private void connectNodes(int source, int target) {
        if (!commitFields()) {
            return;
        }
        try {
            var connected = UpgradeCatalog.connect(entries, source, target);
            var before = draft();
            entries.set(indexOf(target), connected);
            setSelection(target);
            history.record(before, draft());
            canvas.refreshTooltip(target);
            cancelConnection();
            setLabel(status, tr("editor.connected", target), 8);
        } catch (IllegalArgumentException failure) {
            setLabel(status, tr("editor.connection_error", failure.getMessage()), 8);
        }
    }

    /**
     * Adds a prerequisite to the inspected selection as one graph edit.
     */
    private void connectSelection(int source) {
        int target = entries.get(selected).node().id();
        if (selection.size() == 1) {
            connectNodes(source, target);
            return;
        }
        if (!commitFields()) {
            return;
        }
        try {
            var updated = new ArrayList<>(entries);
            for (int id : selection) {
                int index = indexOf(id);
                if (!entries.get(index).node().connectedFrom(source)) {
                    updated.set(index, UpgradeCatalog.connect(updated, source, id));
                }
            }
            applyBatch(updated);
            setLabel(status, tr("editor.connected_selection", selection.size()), 8);
        } catch (IllegalArgumentException failure) {
            setLabel(status, tr("editor.connection_error", failure.getMessage()), 8);
        }
    }

    /**
     * Clears the connection preview and its status message.
     */
    private void cancelConnection() {
        connectionSource = UpgradeNode.DISCONNECTED;
        canvas.linkFrom(UpgradeNode.DISCONNECTED);
        setLabel(status, "", 8);
    }

    /**
     * Writes the final positions of a drag or nudge as one edit.
     */
    private void moveNodes(List<AnchorPosition> positions) {
        var before = draft();
        for (var position : positions) {
            int index = indexOf(position.id());
            var entry = entries.get(index);
            var node = entry.node();
            var moved = node.withCostAndPosition(node.cost(), position.anchor().worldX(), position.anchor().worldY());
            entries.set(index, entry.withNode(moved));
            if (index == selected && selection.size() == 1) {
                nodeFields.loadPosition(moved);
            }
        }
        if (selection.size() > 1) {
            syncNodeFields();
        }
        history.record(before, draft());
    }

    /**
     * Copies the selected draft entry into the input widgets.
     */
    private void loadForm() {
        loadingForm = true;
        try {
            populateForm();
        } finally {
            loadingForm = false;
        }
    }

    private void populateForm() {
        linkInspector.clear();
        canvas.highlightLink(null);
        boolean present = selected >= 0;
        properties.visibility(present ? Visibility.VISIBLE : Visibility.COLLAPSED);
        delete.enabled(canCopyOrDelete());
        pickingPrerequisite = false;
        var entry = present ? entries.get(selected) : null;
        canvas.highlight(selection);
        nodeFields.show(entry, present ? selectedEntries() : List.of());
        effects.show(present ? entry.effects() : List.of(), present);
        if (!present) {
            setLabel(heading, tr("editor.empty"), 10);
            return;
        }
        showHeading(entry.node().id(), UpgradeTreeText.entryName(entry));
        effects.showMixed(UpgradeNodeInspector.mixed(selectedEntries(), Entry::effects));
        filterParameters();
    }

    private List<Entry> selectedEntries() {
        return selection.stream().map(id -> entries.get(indexOf(id))).toList();
    }

    /**
     * Updates the selected node's draft appearance during editing.
     */
    private void refreshAppearance() {
        if (loadingForm || pending || selected < 0) {
            return;
        }
        if (selection.size() > 1) {
            previewBatchAppearance();
            return;
        }
        var preview = nodeFields.preview(entries.get(selected));
        if (preview != null) {
            canvas.put(preview);
            canvas.refreshTooltip(preview.node().id());
        }
    }

    /**
     * @return whether the selected node or route fields were accepted into the draft
     */
    private boolean commitFields() {
        if (loadingForm || pending) {
            return false;
        }
        if (!committedBudget.equals(budget.text()) || !committedStartingPoints.equals(startingPoints.text())
                || !committedRefundPercent.equals(refundPercent.text())) {
            var before = draft();
            committedBudget = budget.text();
            committedStartingPoints = startingPoints.text();
            committedRefundPercent = refundPercent.text();
            history.record(before, draft());
        }
        if (selected < 0) {
            return linkInspector.commitCoordinates();
        }
        if (selection.size() > 1) {
            return commitBatchFields();
        }
        var entry = entries.get(selected);
        UpgradeNode linked;
        try {
            linked = entry.node().id() == UpgradeNode.ROOT ? entry.node() : nodeFields.readPrerequisites(entry.node());
        } catch (IllegalArgumentException error) {
            setLabel(status, tr("editor.prerequisite_error"), 8);
            return false;
        }
        try {
            var changed = nodeFields.read(entry, linked, effects::read);
            if (!UpgradeCatalog.validCoordinate(changed.node().x()) || !UpgradeCatalog.validCoordinate(changed.node().y())) {
                setLabel(status, tr("editor.position_error", UpgradeCatalog.MAX_COORDINATE), 8);
                return false;
            }
            applyFields(changed);
            return true;
        } catch (NumberFormatException error) {
            setLabel(status, tr("editor.number_error"), 8);
            return false;
        } catch (IllegalArgumentException error) {
            setLabel(status, tr("editor.appearance_error"), 8);
            return false;
        }
    }

    private void applyFields(Entry changed) {
        if (!changed.equals(entries.get(selected))) {
            var before = draft();
            entries.set(selected, changed);
            canvas.put(changed);
            history.record(before, draft());
        }
        canvas.refreshTooltip(changed.node().id());
    }

    private void previewBatchAppearance() {
        var fields = nodeFields.changedFields();
        fields.retainAll(UpgradeNodeBatchEdit.APPEARANCE);
        if (fields.isEmpty()) {
            return;
        }
        try {
            var edit = nodeFields.readBatch(entries.get(selected), fields, effects::read);
            var iconInput = edit.values();
            if (fields.contains(Field.ICON) && iconInput.appearance().iconTexture() == null && !iconInput.icon().isEmpty()
                    && !BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse(iconInput.icon()))) {
                return;
            }
            var active = edit.apply(entries.get(selected));
            nodeFields.previewBorder(active.node(), active.appearance());
            for (var entry : selectedEntries()) {
                canvas.put(edit.apply(entry));
                canvas.refreshTooltip(entry.node().id());
            }
        } catch (IllegalArgumentException incomplete) {
            // Keep the last valid preview while the operator types.
        }
    }

    private boolean commitBatchFields() {
        if (!nodeFields.hasChanges()) {
            return true;
        }
        try {
            var edit = nodeFields.readBatch(entries.get(selected), nodeFields.changedFields(), effects::read);
            var updated = edit.applyToSelection(entries, selection);
            new UpgradeCatalog(definition(updated));
            applyBatch(updated);
            return true;
        } catch (NumberFormatException invalid) {
            setLabel(status, tr("editor.number_error"), 8);
            return false;
        } catch (IllegalArgumentException invalid) {
            setLabel(status, tr("editor.batch_error", invalid.getMessage()), 8);
            return false;
        }
    }

    private void applyBatch(List<Entry> updated) {
        var before = draft();
        entries.clear();
        entries.addAll(updated);
        nodeFields.clearChanges();
        syncNodeFields();
        for (var entry : selectedEntries()) {
            canvas.put(entry);
            canvas.refreshTooltip(entry.node().id());
        }
        history.record(before, draft());
    }

    private int indexOf(int id) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).node().id() == id) {
                return i;
            }
        }
        return -1;
    }

    private void setSelection(int id) {
        selection.clear();
        selection.add(id);
        selected = indexOf(id);
        loadForm();
    }

    private void showHeading(int id, String name) {
        var headingText = id == UpgradeNode.ROOT ? name : (selection.size() > 1
                ? tr("editor.selected_count", selection.size()) + "  " : "") + "#" + id + "  " + name;
        setLabel(heading, headingText, 10);
    }

    private void applySearch() {
        if (commitFields()) {
            filterNodes();
        }
    }

    private void filterNodes() {
        String query = search.text().strip().toLowerCase(Locale.ROOT);
        canvas.filter(entry -> matches(entry, query));
        if (!query.isEmpty()) {
            if (selection.removeIf(id -> !matches(entries.get(indexOf(id)), query))) {
                if (!selection.contains(entries.get(selected).node().id())) {
                    selected = selection.isEmpty() ? -1 : indexOf(selection.getLast());
                }
                loadForm();
            }
        }
    }

    private static boolean matches(Entry entry, String query) {
        if (entry.node().id() == UpgradeNode.ROOT || query.isEmpty()) {
            return true;
        }
        if (query.startsWith("#")) {
            return ("#" + entry.node().id()).equals(query);
        }
        return (UpgradeTreeText.entryName(entry) + " " + entry.title() + " " + entry.node().key()
                + " " + entry.node().id() + " " + UpgradeTreeText.localizedText(entry.description()))
                .toLowerCase(Locale.ROOT).contains(query);
    }

    private RichText hoverText(int id) {
        int index = indexOf(id);
        var entry = entries.get(index);
        if (!loadingForm && !pending && !canvas.moving() && selection.contains(id)) {
            try {
                if (selection.size() == 1) {
                    var node = entry.node();
                    entry = nodeFields.read(entry, id == UpgradeNode.ROOT ? node : nodeFields.readPrerequisites(node),
                            effects::read);
                } else if (nodeFields.hasChanges()) {
                    entry = nodeFields.readBatch(entries.get(selected), nodeFields.changedFields(), effects::read).apply(entry);
                }
            } catch (IllegalArgumentException incomplete) {
                // Keep the last complete values while the operator edits a field.
            }
        }
        var node = entry.node();
        if (node.id() == UpgradeNode.ROOT) {
            return UpgradeTreeText.nodeTooltip(entry, Availability.UNLOCKED, tr("core_active"), List.of(), this::draftName);
        }
        String condition = tr("cost", node.cost());
        if (node.disconnected()) {
            condition += "\n" + tr("feedback.disconnected");
        } else if (nodeSettings.showRequirements() && node.prerequisiteCount() > 0) {
            condition += "\n" + UpgradeTreeText.prerequisiteText(node, this::draftName, true);
        }
        return UpgradeTreeText.nodeTooltip(entry, Availability.UNLOCKED, condition, entry.branches(), this::draftName);
    }

    private String draftName(int id) {
        int index = indexOf(id);
        return index < 0 ? "#" + id : UpgradeTreeText.entryName(entries.get(index));
    }

    private boolean keyPressed(int key, int scanCode, int modifiers) {
        if (taskEditor != null) {
            if (key == KeyCodes.ESCAPE) {
                taskEditor.close();
                return true;
            }
            return false;
        }
        var colorPopup = openColorPopup();
        if (colorPopup != null) {
            if (key == KeyCodes.ESCAPE) {
                colorPopup.close();
                return true;
            }
            return false;
        }
        var assetPicker = nodeFields.assetPicker();
        if (assetPicker != null) {
            if (key == KeyCodes.ESCAPE) {
                assetPicker.close();
                return true;
            }
            return false;
        }
        if (pending || canvas.moving()) {
            return false;
        }
        boolean control = KeyModifiers.has(modifiers, KeyModifiers.CONTROL);
        if (control && (key == KeyCodes.Z || key == KeyCodes.Y)) {
            replay(key == KeyCodes.Y || KeyModifiers.has(modifiers, KeyModifiers.SHIFT));
            return true;
        }
        var focused = overlay.uiContext().focusManager().focusedWidget();
        if ((key == KeyCodes.ENTER || key == KeyCodes.KEYPAD_ENTER)
                && (nodeFields.isCoordinate(focused) || linkInspector.isCoordinate(focused))) {
            commitFields();
            return true;
        }
        if (focused instanceof TextField) {
            return false;
        }
        var selectedLink = canvas.selectedLink();
        if (selectedLink != null && !linkInspector.dropdownOpen() && (contextMenu == null || !contextMenu.opened())) {
            if (key == KeyCodes.DELETE) {
                removeLinkPoint(selectedLink);
                return true;
            }
            if (key == KeyCodes.ESCAPE) {
                if (commitFields()) {
                    clearLinkSelection();
                }
                return true;
            }
        }
        if (control) {
            if (KeyModifiers.has(modifiers, KeyModifiers.SHIFT) && (key == KeyCodes.C || key == KeyCodes.V)) {
                if (selected < 0 || contextMenu != null && contextMenu.opened() || nodeFields.dropdownOpen()
                        || effects.dropdownOpen()) {
                    return false;
                }
                if (key == KeyCodes.C && selection.size() == 1) {
                    copyEffects(entries.get(selected).node().id());
                    return true;
                }
                if (key == KeyCodes.V && effectClipboard != null) {
                    pasteEffects();
                    return true;
                }
                return false;
            }
            if (key == KeyCodes.C && canCopyOrDelete()) {
                copySelection();
                return true;
            }
            var point = canvas.cursorPoint();
            if (key == KeyCodes.V && point != null && !clipboard.isEmpty()) {
                paste(point);
                return true;
            }
            return false;
        }
        if (selection.isEmpty() || contextMenu != null && contextMenu.opened() || nodeFields.dropdownOpen()
                || effects.dropdownOpen()) {
            return false;
        }
        return switch (key) {
            case KeyCodes.DELETE -> {
                delete();
                yield true;
            }
            case KeyCodes.LEFT, KeyCodes.RIGHT, KeyCodes.UP, KeyCodes.DOWN -> {
                if (!nodeFields.hasChanges() || commitFields()) {
                    canvas.nudge(key == KeyCodes.LEFT ? -1 : key == KeyCodes.RIGHT ? 1 : 0,
                            key == KeyCodes.UP ? -1 : key == KeyCodes.DOWN ? 1 : 0);
                }
                yield true;
            }
            default -> false;
        };
    }

    /**
     * @return open color palette, if any
     */
    private @Nullable Popup openColorPopup() {
        var colorPopup = nodeFields.openColorPopup();
        return colorPopup != null ? colorPopup : effects.openColorPopup();
    }

    private void copySelection() {
        if (!pending && canCopyOrDelete() && commitFields()) {
            clipboard = selection.stream().map(id -> entries.get(indexOf(id))).toList();
            setLabel(status, tr("editor.copied", clipboard.size()), 8);
        }
    }

    /**
     * @param id source node whose committed effects are copied
     */
    private void copyEffects(int id) {
        if (!commitFields()) {
            return;
        }
        var source = entries.get(indexOf(id));
        if (!source.effects().isEmpty()) {
            effectClipboard = source;
            setLabel(status, tr("editor.effects_copied", source.effects().size()), 8);
        }
    }

    /**
     * Replaces the selected nodes' effects in a single history entry.
     */
    private void pasteEffects() {
        var copied = effectClipboard;
        if (copied == null || selected < 0 || !commitFields()) {
            return;
        }
        var edit = new UpgradeNodeBatchEdit(Set.of(Field.EFFECTS), copied, null, false);
        applyBatch(edit.applyToSelection(entries, selection));
        setLabel(status, tr("editor.effects_pasted", selection.size()), 8);
    }

    private void paste(ContextPoint point) {
        if (clipboard.isEmpty() || !canAdd(clipboard.size())) {
            return;
        }
        var before = draft();
        var copies = UpgradeTreeClipboard.paste(clipboard, nextId, canvas.place(point.worldX()), canvas.place(point.worldY()));
        nextId += copies.size();
        entries.addAll(copies);
        selection.clear();
        for (var entry : copies) {
            selection.add(entry.node().id());
            canvas.put(entry);
        }
        selected = entries.size() - copies.size();
        loadForm();
        history.record(before, draft());
        setLabel(status, tr("editor.pasted", copies.size()), 8);
    }

    /**
     * Adds a new prototype with a fresh ID next to the selected prerequisite on the map.
     */
    private void add() {
        if (!canAdd(1)) {
            return;
        }
        var parentEntry = selected < 0 ? null : entries.get(selected);
        var parent = parentEntry == null ? null : parentEntry.node();
        insertNode(parentEntry, parent == null ? UpgradeNode.ROOT : parent.id(),
                parent == null ? 66 : parent.x() + 38, parent == null ? 38 : parent.y() + (parent.y() > 0 ? -28 : 28));
        canvas.fit();
    }

    /**
     * @param point clicked location in empty map space
     */
    private void addDetached(ContextPoint point) {
        if (canAdd(1)) {
            insertNode(null, UpgradeNode.DISCONNECTED, point.worldX(), point.worldY());
        }
    }

    private void addExamples(ContextPoint point) {
        if (!canAdd(UpgradeTreeExamples.NODE_COUNT)) {
            return;
        }
        var before = draft();
        var examples = UpgradeTreeExamples.at(nextId, canvas.place(point.worldX()), canvas.place(point.worldY()));
        nextId += examples.size();
        entries.addAll(examples);
        examples.forEach(canvas::put);
        setSelection(examples.getFirst().node().id());
        filterNodes();
        history.record(before, draft());
    }

    /**
     * @return whether the current draft can accept a new node
     */
    private boolean canAdd(int count) {
        if (pending || !commitFields()) {
            return false;
        }
        if (entries.size() - 1 + count > UpgradeCatalog.MAX_NODES || nextId + count > UpgradeCatalog.ID_LIMIT) {
            setLabel(status, tr("editor.node_limit"), 8);
            return false;
        }
        return true;
    }

    /**
     * @param parentEntry entry whose path and sector should be inherited, or null
     * @param previousId  parent node, ME core, or DISCONNECTED
     * @param x           horizontal world coordinate
     * @param y           vertical world coordinate
     */
    private void insertNode(@Nullable Entry parentEntry, int previousId, float x, float y) {
        var before = draft();
        var parent = parentEntry == null ? null : parentEntry.node();
        int id = nextId++;
        var node = new UpgradeNode(id, "custom." + id, 1, 0, parent == null ? Sector.NETWORK : parent.sector(),
                parent == null ? null : parent.route(), previousId, UpgradeNode.ROOT, canvas.place(x), canvas.place(y),
                UpgradeNode.Kind.PASSIVE);
        var entry = new Entry(node, "@aeupgrade.tree.editor.new_node", "", "", List.of(), null, null, List.of());
        entries.add(entry);
        canvas.put(entry);
        setSelection(id);
        history.record(before, draft());
    }

    /**
     * Removes the entire selection as one undoable edit.
     */
    private void delete() {
        if (pending || canvas.moving() || !canCopyOrDelete()) {
            return;
        }
        commitFields();
        var before = draft();
        var after = before.deleteSelection();
        history.record(before, after);
        restore(after);
        setLabel(status, tr("editor.deleted", before.selection().size()), 8);
    }

    private boolean canCopyOrDelete() {
        return !selection.isEmpty() && !selection.contains(UpgradeNode.ROOT);
    }

    private Draft draft() {
        int inspected = selected < 0 ? UpgradeNode.DISCONNECTED : entries.get(selected).node().id();
        return new Draft(committedBudget, entries, List.copyOf(selection), inspected, experimental, nodeSettings,
                canvas.selectedLink(), committedStartingPoints, operatorOnly, committedRefundPercent, dropResearchOnBreak);
    }

    private void replay(boolean redo) {
        if (!commitFields()) {
            restore(draft());
            return;
        }
        var restored = redo ? history.redo() : history.undo();
        if (restored != null) {
            restore(restored);
            setLabel(status, tr(redo ? "editor.redone" : "editor.undone"), 8);
        }
    }

    /**
     * Synchronizes the draft, map and inspector while preserving the current camera and search.
     */
    private void restore(Draft restored) {
        if (contextMenu != null) {
            overlay.removeOverlay(contextMenu.close());
            contextMenu = null;
        }
        nodeFields.closeDropdown();
        effects.closePopups();
        clearLinkSelection();
        cancelConnection();
        var restoredIds = restored.entries().stream().map(entry -> entry.node().id()).collect(Collectors.toSet());
        for (var entry : entries) {
            if (!restoredIds.contains(entry.node().id())) {
                canvas.remove(entry.node().id());
            }
        }
        entries.clear();
        entries.addAll(restored.entries());
        entries.forEach(canvas::put);
        committedBudget = restored.budget();
        budget.text(committedBudget);
        committedStartingPoints = restored.startingPoints();
        startingPoints.text(committedStartingPoints);
        committedRefundPercent = restored.refundPercent();
        refundPercent.text(committedRefundPercent);
        operatorOnly = restored.operatorOnly();
        dropResearchOnBreak = restored.dropResearchOnBreak();
        experimental = restored.experimental();
        nodeSettings = restored.nodeSettings();
        refreshSettingsControls();
        selection.clear();
        selection.addAll(restored.selection());
        selected = indexOf(restored.inspectedId());
        loadForm();
        filterNodes();
        if (restored.inspectedLink() != null) {
            focusLink(restored.inspectedLink());
        }
        if (selected >= 0) {
            canvas.refreshTooltip(entries.get(selected).node().id());
        }
    }

    /**
     * Validates the local draft and requests an operator-authorized server commit.
     */
    private void save() {
        if (pending || stale || !commitFields()) {
            return;
        }
        try {
            var graph = new UpgradeCatalog(definition(entries));
            var document = UpgradeTreeConfig.encode(graph.definition());
            pending = true;
            save.enabled(false);
            setLabel(status, tr("pending"), 8);
            PlatformNetwork.sendToServer(new Save(baseline.controller(), baseline.revision(), document));
        } catch (IllegalArgumentException failure) {
            setLabel(status, tr("feedback.editor_invalid") + " " + failure.getMessage(), 8);
        }
    }

    private Definition definition(List<Entry> draftEntries) {
        int maximum = integer(budget);
        int initial = integer(startingPoints);
        if (initial < 0 || maximum < initial || maximum > UpgradeCatalog.MAX_POINTS) {
            throw new IllegalArgumentException(tr("editor.points_error", UpgradeCatalog.MAX_POINTS));
        }
        int refund = integer(refundPercent);
        if (refund < 0 || refund > 100) {
            throw new IllegalArgumentException(tr("editor.refund_error"));
        }
        return new Definition(maximum, nextId, null, draftEntries.subList(1, draftEntries.size()), legacyPaths,
                experimental, nodeSettings, Core.fromEntry(draftEntries.getFirst()), initial, operatorOnly, refund, dropResearchOnBreak);
    }

    /**
     * Discards the local draft and returns to the last confirmed tree.
     */
    private void back() {
        if (!pending) {
            active = null;
            returnToTree.accept(baseline);
        }
    }

    private void applyBackgrounds(List<String> layers) {
        if (pending || selected < 0 || !commitFields()) {
            return;
        }
        var entry = entries.get(selected);
        var appearance = entry.appearance().withBackgrounds(layers);
        var changed = new Entry(entry.node(), entry.title(), entry.description(), entry.icon(), entry.effects(),
                entry.path(), appearance, entry.branches(), entry.reveal());
        if (selection.size() > 1) {
            var edit = new UpgradeNodeBatchEdit(Set.of(Field.BACKGROUNDS), changed, null, false);
            applyBatch(edit.applyToSelection(entries, selection));
        } else if (!appearance.equals(entry.appearance())) {
            applyFields(changed);
        }
        nodeFields.showLayers(appearance.backgrounds());
        filterParameters();
    }

    private void filterParameters() {
        String query = parameterSearch.text().strip();
        var matches = InspectorParameterSearch.compile(query);
        boolean inspectingNode = linkInspector.link() == null;
        boolean searching = !query.isEmpty();
        boolean found = nodeFields.filter(matches, searching, inspectingNode);
        found |= effects.filter(matches, searching, inspectingNode);
        found |= linkInspector.filter(matches, searching);
        noParameters.visibility(found ? Visibility.COLLAPSED : Visibility.VISIBLE);
    }


    private Point mapPoint(int id) {
        var node = entries.get(indexOf(id)).node();
        return new Point(node.x(), node.y());
    }

    private void syncNodeFields() {
        var chosen = selectedEntries();
        boolean alreadyLoading = loadingForm;
        loadingForm = true;
        try {
            nodeFields.load(entries.get(selected), chosen);
            effects.showMixed(UpgradeNodeInspector.mixed(chosen, Entry::effects));
        } finally {
            loadingForm = alreadyLoading;
        }
    }

    private void updateHeading(String name) {
        if (selected >= 0) {
            showHeading(entries.get(selected).node().id(), name);
        }
    }

    private void togglePrerequisitePicker() {
        if (!pending && canCopyOrDelete()) {
            if (connectionSource != UpgradeNode.DISCONNECTED) {
                cancelConnection();
            }
            pickingPrerequisite = !pickingPrerequisite;
            setLabel(status, pickingPrerequisite ? tr("editor.pick_hint") : "", 8);
        }
    }
}
