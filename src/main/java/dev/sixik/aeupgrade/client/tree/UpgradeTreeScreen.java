package dev.sixik.aeupgrade.client.tree;

import static dev.sixik.aeupgrade.client.tree.UpgradeTreeText.branchChoices;
import static dev.sixik.aeupgrade.client.tree.UpgradeTreeText.entryName;
import static dev.sixik.aeupgrade.client.tree.UpgradeTreeText.feedback;
import static dev.sixik.aeupgrade.client.tree.UpgradeTreeText.nodeTooltip;
import static dev.sixik.aeupgrade.client.tree.UpgradeTreeText.pathName;
import static dev.sixik.aeupgrade.client.tree.UpgradeTreeText.prerequisiteText;
import static dev.sixik.aeupgrade.client.tree.UpgradeTreeText.tr;
import static dev.sixik.aeupgrade.client.tree.map.TreeBoard.mapTooltip;
import static dev.sixik.aeupgrade.client.widget.AeStyle.PANEL;
import static dev.sixik.aeupgrade.client.widget.AeStyle.TEXT_COLOR;
import static dev.sixik.aeupgrade.client.widget.AeStyle.setLabel;
import static dev.sixik.aeupgrade.client.widget.AeStyle.text;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

import dev.sixik.aeupgrade.client.tree.editor.UpgradeTreeEditor;
import dev.sixik.aeupgrade.AEUpgradeTree;
import dev.sixik.aeupgrade.client.tree.map.TreeCanvas;
import dev.sixik.aeupgrade.client.tree.map.TreeNodeButton;
import dev.sixik.aeupgrade.client.tree.map.UpgradeTreeGraph;
import dev.sixik.aeupgrade.client.widget.AeButton;
import dev.sixik.aeupgrade.client.widget.ModalWindow;
import dev.sixik.aeupgrade.platform.PlatformNetwork;
import dev.sixik.aeupgrade.tree.UpgradeCatalog;
import dev.sixik.aeupgrade.tree.UpgradeNode;
import dev.sixik.aeupgrade.tree.UpgradePlan;
import dev.sixik.aeupgrade.tree.UpgradePlan.Availability;
import dev.sixik.aeupgrade.tree.UpgradeTreeConfig;
import dev.sixik.aeupgrade.tree.UpgradeTreePackets;
import dev.sixik.aeupgrade.tree.UpgradeTreePackets.Action;
import dev.sixik.aeupgrade.tree.UpgradeTreePackets.RefundQuote;
import dev.sixik.aeupgrade.tree.UpgradeTreePackets.Snapshot;
import dev.sixik.aeupgrade.tree.research.ResearchTaskPackets;
import dev.sixik.unigui.api.event.EventPhase;
import dev.sixik.unigui.api.event.PointerPressedEvent;
import dev.sixik.unigui.api.event.PointerReleasedEvent;
import dev.sixik.unigui.api.input.KeyCodes;
import dev.sixik.unigui.api.input.PointerButton;
import dev.sixik.unigui.api.layout.Align;
import dev.sixik.unigui.api.layout.Justify;
import dev.sixik.unigui.api.math.ColorView;
import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.screen.UniScreen;
import dev.sixik.unigui.api.widget.Visibility;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftFonts;
import dev.sixik.unigui.widgets.containers.HBox;
import dev.sixik.unigui.widgets.containers.VBox;
import dev.sixik.unigui.widgets.display.Text;
import dev.sixik.unigui.widgets.display.TextWidget;
import dev.sixik.unigui.widgets.feedback.ContextMenu;
import dev.sixik.unigui.widgets.feedback.OverlayLayer;
import dev.sixik.unigui.widgets.feedback.Tooltip;
import dev.sixik.unigui.widgets.feedback.WindowWidget;
import net.minecraft.client.Minecraft;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * Interactive UniGUI upgrade map for one ME controller.
 */
public final class UpgradeTreeScreen {
    /**
     * Maximum interval between two accepted primary clicks on the same upgrade.
     */
    private static final long DOUBLE_CLICK_NANOS = 300_000_000L;
    /**
     * Dims the world behind the inventory-style frame.
     */
    private static final ColorView BACKDROP = MutableColor.rgba255(15, 13, 24, 175);
    /**
     * Secondary text on the light inventory background.
     */
    private static final ColorView SECONDARY_TEXT = MutableColor.fromHex("#585D73");
    /**
     * Displayed tree, cleared when its UniScreen is dismissed.
     */
    private static @Nullable UpgradeTreeScreen active;

    /**
     * Server-selected controller position and dimension.
     */
    private final GlobalPos controller;
    /**
     * Server definition backing this view and the editor.
     */
    private final UpgradeCatalog catalog;
    private final UpgradeTreeGraph graph;
    /**
     * Revision of the graph used by every action from this view.
     */
    private final long revision;
    /**
     * Pan and zoom viewport for the graph.
     */
    private final TreeCanvas canvas = new TreeCanvas();
    /**
     * Graph node buttons by their corresponding upgrade.
     */
    private final Map<UpgradeNode, TreeNodeButton> nodes = new LinkedHashMap<>();
    /**
     * Hover cards for the nodes, above the canvas scissor.
     */
    private final Map<UpgradeNode, Tooltip> tooltips = new LinkedHashMap<>();
    /**
     * Remaining point balance in the header.
     */
    private final TextWidget points = new Text().color(TEXT_COLOR);
    /**
     * Purchase control for the selected node.
     */
    private final AeButton buy = new AeButton("");
    /**
     * Respec control for the controller's current purchases.
     */
    private final AeButton reset = new AeButton(tr("map.reset"));
    /**
     * Opens the editor while no tree action is pending.
     */
    private final AeButton edit = new AeButton(tr("editor.open"));
    /**
     * Selected node or action feedback in the footer.
     */
    private final TextWidget statusLine = new Text().color(SECONDARY_TEXT).clipOverflow();
    /**
     * UniGUI screen hosting the prototype widgets.
     */
    private final UniScreen screen;
    /**
     * Research task content, visible only when the server offers tasks and the player opens the tab.
     */
    private final ResearchTaskPanel taskPanel;
    /**
     * Switches between the tree and the task tab whenever tasks are available.
     */
    private final AeButton tabs = new AeButton(tr("tasks.tab"));
    /**
     * Tree action buttons, hidden while browsing research tasks.
     */
    private final HBox footer = new HBox();
    /**
     * View-request placeholder ignored by the server for the VIEW operation.
     */
    private static final ResourceLocation TASK_VIEW_ID = ResourceLocation.fromNamespaceAndPath(AEUpgradeTree.MODID, "view");
    private final OverlayLayer overlay;
    private @Nullable WindowWidget confirmation;
    private @Nullable ContextMenu nodeMenu;
    private @Nullable NodeContextPress nodeContextPress;

    /**
     * Currently inspected node, which can be locked or already purchased.
     */
    private @Nullable UpgradeNode selected;
    /**
     * Node and time of the last click eligible to start a purchase gesture.
     */
    private @Nullable UpgradeNode lastClickedNode;
    private long lastNodeClick;
    /**
     * Latest server-confirmed allocation.
     */
    private UpgradePlan purchases;
    private UpgradeTreeVisibility visibility;
    /**
     * True while awaiting the result of the current purchase or respec request.
     */
    private boolean pending;
    private int refreshTicks;
    /**
     * Localized server feedback, or empty while inspecting a node.
     */
    private String feedback = "";

    /**
     * Builds the graph, hover cards and controls from the initial server state.
     *
     * @param snapshot initial tree state supplied by the server
     */
    private UpgradeTreeScreen(Snapshot snapshot) {
        controller = snapshot.controller();
        revision = snapshot.revision();
        catalog = UpgradeTreeConfig.decode(snapshot.catalog());
        feedback = feedback(snapshot.message());
        var entries = catalog.definition().nodes();

        var root = new VBox();
        root.background(BACKDROP);
        root.layout(style -> style.fill().padding(10).alignItems(Align.CENTER).justifyContent(Justify.CENTER));

        var frame = new VBox();
        frame.backgroundTexture(PANEL).backgroundVisible(true).sliceBorders(3).spacing(5);
        frame.layout(style -> style.fill().maxWidth(758).maxHeight(480).padding(8).alignItems(Align.STRETCH));
        root.addChild(frame);
        overlay = new OverlayLayer(root);
        taskPanel = new ResearchTaskPanel(this::requestTask, overlay);
        overlay.on(PointerPressedEvent.TYPE, true, event -> {
            if (event.phase() != EventPhase.BUBBLE
                    && (event.button() != PointerButton.PRIMARY || event.target() != nodes.get(lastClickedNode))) {
                lastClickedNode = null;
            }
            if (event.phase() == EventPhase.CAPTURE && event.button() == PointerButton.SECONDARY) {
                nodeContextPress = null;
            }
        });

        var header = new HBox();
        header.spacing(8);
        header.layout(style -> style.height(27).flexNone().alignItems(Align.CENTER)
                .justifyContent(Justify.SPACE_BETWEEN));
        var caption = new VBox();
        caption.spacing(1);
        caption.layout(style -> style.expand().minWidth(0));
        caption.addChild(text(tr("title"), 12, TEXT_COLOR));
        var pos = controller.pos();
        caption.addChild(text(tr("controller", pos.getX(), pos.getY(), pos.getZ()), 9, SECONDARY_TEXT));
        header.addChild(caption);
        header.addChild(points);
        var player = Minecraft.getInstance().player;
        if (player != null && player.hasPermissions(2)) {
            edit.layout(style -> style.size(70, 18).flexNone());
            edit.onClick(event -> UpgradeTreeEditor.open(new Snapshot(controller, Optional.of(purchases.ids()), true, "",
                            revision, snapshot.catalog(), purchases.total(), false, purchases.allUnlocked(),
                            tabs.visibility() == Visibility.VISIBLE), catalog,
                    selected == null ? -1 : selected.id(), UpgradeTreeScreen::show));
            header.addChild(edit);
        }
        var close = new AeButton(tr("close"));
        close.layout(style -> style.size(53, 18).flexNone());
        close.onClick(event -> close());
        frame.addChild(header);

        canvas.layout(style -> style.expand().minHeight(0));
        var rootEntry = catalog.definition().core().asEntry();
        var core = new TreeNodeButton(rootEntry);
        core.appearance(Availability.UNLOCKED, false);
        core.onClick(event -> canvas.center());
        addNode("core", rootEntry.node().x(), rootEntry.node().y(), core);
        var coreTooltip = mapTooltip(core);
        coreTooltip.richText(nodeTooltip(rootEntry, Availability.UNLOCKED, tr("core_active"), List.of(), id -> ""));
        overlay.addOverlay(coreTooltip);
        for (var entry : entries) {
            var node = entry.node();
            var nodeButton = new TreeNodeButton(entry);
            nodeButton.onClick(event -> select(node, nodeButton.pointerClick()));
            nodeButton.on(PointerPressedEvent.TYPE, event -> {
                if (event.phase() == EventPhase.TARGET && event.button() == PointerButton.SECONDARY
                        && !pending && confirmation == null && purchases.canRefund(node.id())) {
                    nodeContextPress = new NodeContextPress(node, event.rootX(), event.rootY());
                    event.cancel();
                }
            });
            nodeButton.on(PointerReleasedEvent.TYPE, event -> openNodeMenu(node, event));
            nodes.put(node, nodeButton);
            addNode(node.key(), node.x(), node.y(), nodeButton);
            var tooltip = mapTooltip(nodeButton);
            overlay.addOverlay(tooltip);
            tooltips.put(node, tooltip);
        }
        graph = new UpgradeTreeGraph(canvas, catalog, rootEntry, core, nodes);
        updatePurchases(snapshot);
        frame.addChild(canvas);

        footer.spacing(4);
        footer.layout(style -> style.height(18).alignItems(Align.CENTER).flexNone().justifyContent(Justify.SPACE_BETWEEN));
        statusLine.layout(style -> style.expand().minWidth(0));
        footer.addChild(statusLine);
        buy.layout(style -> style.size(79, 18).flexNone());
        buy.onClick(event -> {
            unlockSelected();
        });
        footer.addChild(buy);
        reset.layout(style -> style.size(48, 18).flexNone());
        reset.onClick(event -> requestRefund(UpgradeTreePackets.RESET));
        footer.addChild(reset);
        var center = new AeButton(tr("center"));
        center.layout(style -> style.size(61, 18).flexNone());
        center.onClick(event -> canvas.center());
        footer.addChild(center);
        var overview = new AeButton(tr("map.overview"));
        overview.layout(style -> style.size(58, 18).flexNone());
        overview.onClick(event -> canvas.overview());
        footer.addChild(overview);
        frame.addChild(footer);

        taskPanel.body().visibility(Visibility.COLLAPSED);
        frame.addChild(taskPanel.body());
        tabs.layout(style -> style.size(76, 18).flexNone());
        tabs.onClick(event -> showTaskTab(taskPanel.body().visibility() != Visibility.VISIBLE));
        updateTaskAvailability(snapshot.tasksEnabled());
        header.addChild(tabs);
        header.addChild(close);

        screen = new UniScreen(tr("title"), overlay).scaleWithGameGui(true).pauseGame(false)
                .keyboardInputHandler((key, scanCode, modifiers) -> key == KeyCodes.ESCAPE && (dismissConfirmation() || closeNodeMenu()))
                .defaultFont(MinecraftFonts.defaultFace()).onClose(() -> {
                    graph.close();
                    if (active == this) {
                        active = null;
                    }
                });
        refresh();
    }

    /**
     * Applies a task response if it addresses the currently open controller.
     *
     * @param board validated task state and action result
     */
    public static void receiveTasks(ResearchTaskPackets.Board board) {
        if (active != null && active.controller.equals(board.controller())) {
            active.taskPanel.apply(board);
        }
    }

    /**
     * Updates task tab availability on startup or after an operator changes the config.
     *
     * @param available whether the server offers any research tasks
     */
    private void updateTaskAvailability(boolean available) {
        tabs.visibility(available ? Visibility.VISIBLE : Visibility.COLLAPSED);
        if (!available && taskPanel.body().visibility() == Visibility.VISIBLE) {
            showTaskTab(false);
        }
    }

    /**
     * Switches the content while retaining the tree selection and camera.
     *
     * @param showTasks whether to display the task panel
     */
    private void showTaskTab(boolean showTasks) {
        canvas.visibility(showTasks ? Visibility.COLLAPSED : Visibility.VISIBLE);
        footer.visibility(showTasks ? Visibility.COLLAPSED : Visibility.VISIBLE);
        taskPanel.body().visibility(showTasks ? Visibility.VISIBLE : Visibility.COLLAPSED);
        tabs.label(tr(showTasks ? "tasks.tree" : "tasks.tab"));
        if (showTasks) {
            requestTask(TASK_VIEW_ID, ResearchTaskPackets.Operation.VIEW);
        }
    }

    /**
     * Requests the latest task board or asks the server to perform a task action.
     *
     * @param id        selected task ID, ignored for VIEW
     * @param operation requested user action
     */
    private void requestTask(ResourceLocation id, ResearchTaskPackets.Operation operation) {
        PlatformNetwork.sendToServer(new ResearchTaskPackets.Request(controller, operation, id, taskPanel.token()));
    }

    /**
     * Anchors an item button to a map location and scales its bounds with the camera.
     *
     * @param id   stable anchor ID
     * @param x    horizontal position in world canvas units
     * @param y    vertical position in world canvas units
     * @param icon item button to anchor
     */
    private void addNode(String id, float x, float y, TreeNodeButton icon) {
        canvas.anchorLayer().add(id, x, y, icon).screenSize(icon.diameter(), icon.diameter())
                .pivot(0.5f, 0.5f).zoomScaling(true, TreeCanvas.REFERENCE_ZOOM).cullOutsideViewport(true);
    }

    /**
     * Opens the requested tree or merges a server response into the matching active screen.
     *
     * @param snapshot server-confirmed purchases and the original controller location
     */
    public static void receive(Snapshot snapshot) {
        if (snapshot.refresh()) {
            if (active != null && !active.pending && active.controller.equals(snapshot.controller())) {
                active.refreshProgress(snapshot);
            }
            return;
        }
        if (UpgradeTreeEditor.receive(snapshot)) {
            return;
        }
        if (snapshot.open() || active != null && active.controller.equals(snapshot.controller()) && !snapshot.catalog().isEmpty()) {
            show(snapshot);
        } else if (active != null && active.controller.equals(snapshot.controller())) {
            active.pending = false;
            if (snapshot.purchases().isPresent()) {
                active.updateTaskAvailability(snapshot.tasksEnabled());
                active.updatePurchases(snapshot);
            }
            active.feedback = feedback(snapshot.message());
            active.refresh();
        }
    }

    private void refreshProgress(Snapshot snapshot) {
        if (!snapshot.catalog().isEmpty()) {
            show(snapshot);
            return;
        }
        if (snapshot.purchases().isPresent()) {
            updateTaskAvailability(snapshot.tasksEnabled());
            var ids = snapshot.purchases().orElseThrow();
            if (snapshot.totalPoints() != purchases.total() || snapshot.allUnlocked() != purchases.allUnlocked()
                    || !ids.equals(purchases.ids())) {
                updatePurchases(snapshot);
                refresh();
            }
        } else if (!snapshot.message().isEmpty()) {
            var player = Minecraft.getInstance().player;
            if (player != null) {
                player.displayClientMessage(Component.literal(feedback(snapshot.message())), true);
            }
            close();
        }
    }

    /**
     * Updates the open tree when points are granted outside its screen.
     */
    public static void tick() {
        var tree = active;
        if (tree != null && Minecraft.getInstance().player != null && !tree.pending && ++tree.refreshTicks >= 20) {
            tree.refreshTicks = 0;
            PlatformNetwork.sendToServer(new Action(tree.controller, UpgradeTreePackets.REFRESH, tree.revision, List.of()));
            if (tree.taskPanel.body().visibility() == Visibility.VISIBLE) {
                tree.requestTask(TASK_VIEW_ID, ResearchTaskPackets.Operation.VIEW);
            }
        }
    }

    /**
     * @param snapshot full confirmed server definition and allocation to display
     */
    private static void show(Snapshot snapshot) {
        var next = new UpgradeTreeScreen(snapshot);
        active = next;
        next.screen.openGui();
    }

    /**
     * Updates the visible tree from the server's allocation.
     *
     * @param snapshot confirmed purchases, points and whole-tree access
     */
    private void updatePurchases(Snapshot snapshot) {
        var confirmed = UpgradePlan.restore(snapshot.purchases().orElse(List.of()), catalog, snapshot.totalPoints(),
                snapshot.allUnlocked());
        if (purchases != null && !confirmed.sameUnlocks(purchases)) {
            dismissConfirmation();
            closeNodeMenu();
        }
        purchases = confirmed;
        visibility = new UpgradeTreeVisibility(catalog, confirmed);
        graph.updatePurchases(confirmed, visibility);
        canvas.limitToGraph(catalog.definition(), visibility);
        for (var node : nodes.keySet()) {
            boolean visible = visibility.visible(node.id());
            canvas.anchorLayer().anchor(node.key()).visible(visible);
            tooltips.get(node).visibility(visible ? Visibility.VISIBLE : Visibility.COLLAPSED);
        }
        if (selected == null || !visibility.visible(selected.id())) {
            lastClickedNode = null;
            selected = nodes.keySet().stream().filter(node -> visibility.visible(node.id())).findFirst().orElse(null);
        }
    }

    /**
     * Repaints node and control states using the last acknowledged purchases.
     */
    private void refresh() {
        setLabel(points, tr("points", purchases.remaining(), purchases.total(), catalog.budget()), 10);
        for (var entry : nodes.entrySet()) {
            var node = entry.getKey();
            if (!visibility.visible(node.id())) {
                continue;
            }
            var status = purchases.availability(node);
            entry.getValue().appearance(status, node == selected);
            var upgrade = catalog.entry(node.id());
            tooltips.get(node).richText(nodeTooltip(upgrade, status, nodeCondition(node, status),
                    branchChoices(upgrade, status, catalog, purchases), id -> nodeName(catalog.node(id))));
        }

        var status = selected == null ? Availability.PREREQUISITE : purchases.availability(selected);
        buy.label(pending ? tr("pending") : status == Availability.UNLOCKED ? tr("feedback.unlocked") : tr("buy"));
        buy.enabled(!pending && status == Availability.AVAILABLE);
        reset.enabled(!pending && !purchases.isEmpty());
        edit.enabled(!pending);
        var selection = selected == null ? tr("editor.empty") : status == Availability.EXCLUSIVE
                ? tr("map.blocked", nodeName(selected)) : tr("map.selected", nodeName(selected));
        setLabel(statusLine, feedback.isEmpty() ? selection : feedback, 9);
    }

    /**
     * Formats a node's cost, prerequisite or current lock state.
     *
     * @param node   upgrade being described
     * @param status current availability for the controller
     * @return localized condition
     */
    private String nodeCondition(UpgradeNode node, Availability status) {
        boolean showRequirements = catalog.definition().nodeSettings().showRequirements();
        String condition = switch (status) {
            case AVAILABLE -> tr("cost", node.cost());
            case PREREQUISITE -> showRequirements ? prerequisiteText(node, id -> nodeName(catalog.node(id)), true)
                    : tr("map.locked");
            case EXCLUSIVE -> {
                var choice = purchases.blockingChoice(node);
                if (choice != null) {
                    yield tr("choice_closed", nodeName(choice));
                }
                var blocker = purchases.blockingNode(node);
                var path = catalog.pathOf(blocker.id());
                var own = catalog.pathOf(node.id());
                yield own != null && path != null && !own.equals(path)
                        && own.exclusiveGroup() != null && own.exclusiveGroup().equals(path.exclusiveGroup())
                        ? tr("blocked_by", pathName(path)) : tr("blocked_by_node", nodeName(blocker));
            }
            default -> tr("feedback." + status.name().toLowerCase(Locale.ROOT));
        };
        if (status == Availability.AVAILABLE || status == Availability.POINTS || status == Availability.UNLOCKED) {
            for (var gate : catalog.choiceGatesFor(node.id())) {
                if (visibility.visible(gate.id())) {
                    int chosen = purchases.unlockedPrerequisites(gate);
                    if (chosen < gate.requiredCount()) {
                        condition = tr("choice_status", nodeName(gate), chosen, gate.requiredCount()) + "\n" + condition;
                    }
                }
            }
        }
        if (!showRequirements || node.requirements() == null) {
            return condition;
        }
        int required = node.requiredCount();
        String progress = tr("requires_progress", Math.min(purchases.unlockedPrerequisites(node), required), required);
        return status == Availability.PREREQUISITE ? condition + "\n" + progress
                : prerequisiteText(node, id -> nodeName(catalog.node(id)), false) + "\n" + progress + "\n" + condition;
    }

    /**
     * @param node upgrade whose title is needed
     * @return localized name of the upgrade
     */
    private String nodeName(UpgradeNode node) {
        return visibility.visible(node.id()) ? entryName(catalog.entry(node.id())) : tr("map.hidden_node");
    }

    /**
     * Selects a node and buys it on a second primary click within the time limit.
     *
     * @param node         clicked node
     * @param pointerClick whether the button activation came from a pointer release
     */
    private void select(UpgradeNode node, boolean pointerClick) {
        if (!visibility.visible(node.id())) {
            return;
        }
        long now = System.nanoTime();
        boolean canUnlock = !pending && purchases.availability(node) == Availability.AVAILABLE;
        boolean doubleClick = canUnlock && pointerClick && lastClickedNode == node
                && now - lastNodeClick <= DOUBLE_CLICK_NANOS;
        lastClickedNode = canUnlock && pointerClick && !doubleClick ? node : null;
        lastNodeClick = now;
        selected = node;
        feedback = "";
        if (doubleClick) {
            unlockSelected();
        } else {
            refresh();
        }
    }

    /**
     * Submits the same guarded purchase from the footer or the map.
     */
    private void unlockSelected() {
        var node = selected;
        if (node == null || pending || confirmation != null || purchases.availability(node) != Availability.AVAILABLE) {
            return;
        }
        var branches = purchases.newlyClosedBranches(node);
        if (branches.isEmpty()) {
            send(node.id(), List.of());
            return;
        }
        openConfirmation(UpgradeTreeConfirmation.purchase(overlay, nodeName(node), node.cost(),
                branches.stream().map(this::nodeName).toList(),
                () -> send(node.id(), branches.stream().map(UpgradeNode::id).toList())));
    }

    private void requestRefund(int nodeId) {
        if (pending || confirmation != null) {
            return;
        }
        var quote = purchases.refund(nodeId);
        if (quote.removedIds().isEmpty()) {
            return;
        }
        closeNodeMenu();
        var action = new Action(controller, nodeId, revision, List.of(),
                Optional.of(new RefundQuote(quote.removedIds(), quote.returned())));
        var player = Minecraft.getInstance().player;
        if (player != null && player.isCreative()) {
            send(action);
            return;
        }
        boolean all = nodeId == UpgradeTreePackets.RESET;
        String title = tr(all ? "confirm.reset_title" : "confirm.refund_title");
        String operation = all ? tr("confirm.reset_operation") : tr("confirm.refund_operation", nodeName(catalog.node(nodeId)));
        openConfirmation(UpgradeTreeConfirmation.refund(overlay, title, operation, quote.removedIds().size(),
                quote.returned(), quote.lost(), () -> send(action)));
    }

    private void openNodeMenu(UpgradeNode node, PointerReleasedEvent event) {
        if (event.phase() != EventPhase.TARGET || event.button() != PointerButton.SECONDARY) {
            return;
        }
        var pressed = nodeContextPress;
        nodeContextPress = null;
        if (pressed == null || pressed.node() != node || pending || confirmation != null || !purchases.canRefund(node.id())
                || Math.abs(event.rootX() - pressed.x()) > 3 || Math.abs(event.rootY() - pressed.y()) > 3) {
            return;
        }
        closeNodeMenu();
        var menu = new ContextMenu();
        menu.backgroundTexture(PANEL).backgroundVisible(true).sliceBorders(3);
        var refund = new AeButton(tr("map.refund_node"));
        refund.layout(style -> style.size(136, 18).flexNone());
        refund.onClick(click -> requestRefund(node.id()));
        menu.item(refund);
        nodeMenu = menu;
        overlay.addOverlay(menu.openAt(event.rootX(), event.rootY()));
        event.cancel();
    }

    private boolean closeNodeMenu() {
        if (nodeMenu == null) {
            return false;
        }
        boolean open = nodeMenu.opened();
        overlay.removeOverlay(nodeMenu.close());
        nodeMenu = null;
        return open;
    }

    private void openConfirmation(WindowWidget window) {
        lastClickedNode = null;
        window.onClosed(event -> {
            confirmation = null;
            overlay.removeOverlay(window);
        });
        confirmation = window;
        ModalWindow.open(overlay, window);
    }

    /**
     * @return whether there was an open confirmation to dismiss
     */
    private boolean dismissConfirmation() {
        if (confirmation == null) {
            return false;
        }
        confirmation.close();
        return true;
    }

    /**
     * Sends a purchase for validation without predicting the result on the client.
     *
     * @param id                purchased node ID
     * @param confirmedBranches closed branch IDs acknowledged in the dialog
     */
    private void send(int id, List<Integer> confirmedBranches) {
        send(new Action(controller, id, revision, confirmedBranches));
    }

    private void send(Action action) {
        if (!pending && confirmation == null) {
            closeNodeMenu();
            lastClickedNode = null;
            pending = true;
            feedback = "";
            refresh();
            PlatformNetwork.sendToServer(action);
        }
    }

    /**
     * Closes the current UniGUI screen.
     */
    private void close() {
        screen.closeGui();
    }

    private record NodeContextPress(UpgradeNode node, float x, float y) {
    }
}
