package dev.sixik.aeupgrade.client.tree.editor;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.IntFunction;
import java.util.function.Predicate;

import dev.sixik.aeupgrade.client.tree.UpgradeTreeText;
import dev.sixik.aeupgrade.client.tree.map.TreeBoard;
import dev.sixik.aeupgrade.client.tree.map.TreeCanvas;
import dev.sixik.aeupgrade.client.tree.map.TreeLinkPath;
import dev.sixik.aeupgrade.client.tree.map.TreeLinkStroke;
import dev.sixik.aeupgrade.client.tree.map.TreeNodeButton;
import dev.sixik.aeupgrade.tree.UpgradeCatalog;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Entry;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute.Point;
import dev.sixik.aeupgrade.tree.UpgradeNode;
import dev.sixik.unigui.api.core.InvalidationFlags;
import dev.sixik.unigui.api.event.Event;
import dev.sixik.unigui.api.event.EventPhase;
import dev.sixik.unigui.api.event.PointerEnteredEvent;
import dev.sixik.unigui.api.event.PointerMovedEvent;
import dev.sixik.unigui.api.event.PointerPressedEvent;
import dev.sixik.unigui.api.event.PointerReleasedEvent;
import dev.sixik.unigui.api.input.KeyCodes;
import dev.sixik.unigui.api.input.PointerButton;
import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.math.RectView;
import dev.sixik.unigui.api.render.DrawScope;
import dev.sixik.unigui.api.text.RichText;
import dev.sixik.unigui.api.widget.Visibility;
import dev.sixik.unigui.widgets.feedback.OverlayLayer;
import dev.sixik.unigui.widgets.feedback.Tooltip;
import dev.sixik.unigui.widgets.world.AnchorWidget;
import org.jetbrains.annotations.Nullable;

/**
 * Draft map with zoomable item anchors and direct manipulation of node positions.
 */
final class UpgradeEditorCanvas extends TreeCanvas {
    /**
     * Core ring and selection box.
     */
    private static final MutableColor HIGHLIGHT = MutableColor.fromHex("#ACE9FF");
    private static final MutableColor SELECTION_FILL = MutableColor.rgba255(172, 233, 255, 43);
    /**
     * Motion in root pixels required to distinguish a drag from a click.
     */
    private static final float DRAG_THRESHOLD = 3;
    private static final float LINK_HIT_RADIUS = 5;
    private static final float HANDLE_SIZE = 7;
    /**
     * Live draft entries, also backing the prerequisite lines.
     */
    private final List<Entry> entries;
    /**
     * Anchors indexed by stable node ID.
     */
    private final Map<Integer, AnchorWidget> anchors = new LinkedHashMap<>();
    /**
     * Selects a node and permits a drag, or completes a connection.
     */
    private final NodeSelection select;
    /**
     * Applies the nodes inside a completed selection rectangle.
     */
    private final BiConsumer<List<Integer>, Boolean> selectBox;
    /**
     * Writes the final positions of a moved selection back to the draft.
     */
    private final Consumer<List<AnchorPosition>> moved;
    /**
     * Opens the editor context menu after a right click.
     */
    private final Consumer<ContextPoint> contextMenu;
    private final Consumer<LinkPick> pickedLink;
    private final Consumer<LinkChange> changedLink;
    private final Map<UpgradeLinkRoute, CachedPath> paths = new IdentityHashMap<>();
    private @Nullable LinkSelection selectedLink;
    private @Nullable PointDrag pointDrag;
    private @Nullable UpgradeLinkRoute previewRoute;
    private @Nullable UpgradeLinkRoute parameterPreview;
    private final OverlayLayer overlay;
    private final IntFunction<RichText> tooltipText;
    private final Map<Integer, Tooltip> tooltips = new LinkedHashMap<>();
    private final Map<Integer, Entry> displayed = new LinkedHashMap<>();
    private Set<Integer> selected = Set.of();
    private Predicate<Entry> filter = entry -> true;
    /**
     * Node where the connection preview starts, or DISCONNECTED when inactive.
     */
    private int source = UpgradeNode.DISCONNECTED;
    /**
     * Preview endpoint in root coordinates.
     */
    private float cursorX = Float.NaN, cursorY = Float.NaN;
    /**
     * Whether edited positions snap to the underlying world grid.
     */
    private boolean snap = true;
    /**
     * Current left-button gesture, including the preserved grab point.
     */
    private @Nullable Drag drag;
    /**
     * Nodes and starting positions once a press has become a valid drag.
     */
    private @Nullable List<AnchorPosition> dragGroup;
    private boolean dragThresholdPassed;
    /**
     * Left-button capture when a selection starts in empty space.
     */
    private @Nullable SelectionGesture selectionGesture;
    /**
     * Recorded right-press position and original camera offset.
     */
    private @Nullable ContextGesture contextGesture;
    /**
     * Whether the active right-button gesture is panning.
     */
    private boolean panning;

    /**
     * @param entries     mutable draft list
     * @param select      callback on a node press
     * @param selectBox   callback on release after a background click or drag
     * @param moved       callback after moving a selection
     * @param contextMenu callback for a right click on a node, link or the map
     * @param pickedLink  callback for a click on a link or waypoint
     * @param changedLink commits a waypoint drag as one edit
     */
    UpgradeEditorCanvas(List<Entry> entries, NodeSelection select, BiConsumer<List<Integer>, Boolean> selectBox,
                        Consumer<List<AnchorPosition>> moved,
                        Consumer<ContextPoint> contextMenu, Consumer<LinkPick> pickedLink, Consumer<LinkChange> changedLink,
                        OverlayLayer overlay, IntFunction<RichText> tooltipText) {
        this.entries = entries;
        this.select = select;
        this.selectBox = selectBox;
        this.moved = moved;
        this.contextMenu = contextMenu;
        this.pickedLink = pickedLink;
        this.changedLink = changedLink;
        this.overlay = overlay;
        this.tooltipText = tooltipText;
        panningEnabled(false);
        addWorldLayer((world, draw) -> {
            TreeBoard.drawBoard(world, draw);
            drawLinks(draw);
            drawSelection(draw);
        });
        entries.forEach(this::put);
        measureGraph(entries);
    }

    /**
     * @param entry new or updated draft node
     */
    void put(Entry entry) {
        paths.clear();
        var node = entry.node();
        var previous = displayed.get(node.id());
        if (previous != null && previous.icon().equals(entry.icon())
                && previous.appearance().equals(entry.appearance())) {
            displayed.put(node.id(), entry);
            var anchor = anchors.get(node.id());
            anchor.worldPosition(node.x(), node.y());
            if (!previous.title().equals(entry.title())) {
                ((TreeNodeButton) anchor.widget()).text(UpgradeTreeText.entryName(entry));
            }
            display(entry);
            return;
        }
        remove(node.id());
        displayed.put(node.id(), entry);
        float size = entry.appearance().size();
        var item = new TreeNodeButton(entry);
        var anchor = addAnchor(node.id(), node.x(), node.y(), item, size);
        var tooltip = TreeBoard.mapTooltip(item);
        tooltips.put(node.id(), tooltip);
        overlay.addOverlay(tooltip);
        display(entry);
        item.on(PointerEnteredEvent.TYPE, event -> {
            if (event.phase() == EventPhase.TARGET) {
                refreshTooltip(node.id());
            }
        });
        updateAppearance(node.id());
        item.on(PointerPressedEvent.TYPE, event -> pressNode(event, node.id(), anchor, item));
        item.on(PointerMovedEvent.TYPE, this::move);
        item.on(PointerReleasedEvent.TYPE, this::release);
    }

    private void pressNode(PointerPressedEvent event, int id, AnchorWidget anchor, TreeNodeButton item) {
        if (event.button() != PointerButton.PRIMARY) {
            return;
        }
        if (!moving()) {
            boolean shift = shiftDown();
            // Shift toggles selection on a click and keeps its existing free-move behavior on a drag.
            if (shift || select.pick(id, controlDown())) {
                dragThresholdPassed = false;
                dragGroup = null;
                drag = new Drag(id, anchor.worldX(), anchor.worldY(), item, event.pointerId(), event.rootX(), event.rootY(),
                        anchor.worldX() - rootToWorldX(event.rootX()), anchor.worldY() - rootToWorldY(event.rootY()), shift);
                uiContext().capturePointer(event.pointerId(), item);
            }
        }
        event.cancel();
    }

    void refreshTooltip(int id) {
        var tooltip = tooltips.get(id);
        if (tooltip != null) {
            tooltip.richText(tooltipText.apply(id));
        }
    }

    /**
     * @param id stable ID to remove from the canvas
     */
    void remove(int id) {
        paths.clear();
        displayed.remove(id);
        var anchor = anchors.remove(id);
        if (anchor != null) {
            anchorLayer().remove(anchor);
        }
        var tooltip = tooltips.remove(id);
        if (tooltip != null) {
            overlay.removeOverlay(tooltip);
        }
    }

    void highlight(Set<Integer> ids) {
        var previous = selected;
        selected = Set.copyOf(ids);
        previous.forEach(this::updateAppearance);
        selected.forEach(this::updateAppearance);
    }

    /**
     * @param selection edge and optional waypoint to highlight, or null to clear
     */
    void highlightLink(@Nullable LinkSelection selection) {
        if (selection == null || selectedLink == null || !selectedLink.link().equals(selection.link())) {
            previewLinkParameters(null);
        }
        selectedLink = selection;
        invalidate(InvalidationFlags.VISUAL);
    }

    /**
     * @param route temporary geometry from the parameter fields, or null to show the saved draft
     */
    void previewLinkParameters(@Nullable UpgradeLinkRoute route) {
        if (parameterPreview != null) {
            paths.remove(parameterPreview);
        }
        parameterPreview = route;
        invalidate(InvalidationFlags.VISUAL);
    }

    @Nullable LinkSelection selectedLink() {
        return selectedLink;
    }

    void filter(Predicate<Entry> filter) {
        this.filter = filter;
        entries.forEach(this::display);
    }

    private void display(Entry entry) {
        int id = entry.node().id();
        anchors.get(id).visible(filter.test(entry));
        updateTooltipVisibility(id, tooltips.get(id));
    }

    private void updateTooltipVisibility(int id, Tooltip tooltip) {
        boolean visible = selectionGesture == null && anchors.get(id).visible();
        tooltip.visibility(visible ? Visibility.VISIBLE : Visibility.COLLAPSED);
    }

    /**
     * @param id stable source ID, the core, or DISCONNECTED to clear the preview
     */
    void linkFrom(int id) {
        int old = source;
        source = id;
        updateAppearance(old);
        updateAppearance(id);
    }

    /**
     * @param id node or core whose ring needs to reflect the editor state
     */
    private void updateAppearance(int id) {
        var anchor = anchors.get(id);
        if (anchor != null) {
            ((TreeNodeButton) anchor.widget()).previewAppearance(id == source || selected.contains(id));
        }
    }

    /**
     * @return whether snapping is now active
     */
    boolean toggleSnap() {
        snap = !snap;
        return snap;
    }

    /**
     * @param position desired coordinate in world units
     * @return bounded coordinate with the current snapping setting applied
     */
    float place(float position) {
        return place(position, snap && !shiftDown());
    }

    private boolean shiftDown() {
        return uiContext().keyboard().isAnyDown(KeyCodes.LEFT_SHIFT, KeyCodes.RIGHT_SHIFT);
    }

    private boolean controlDown() {
        return uiContext().keyboard().isAnyDown(KeyCodes.LEFT_CONTROL, KeyCodes.RIGHT_CONTROL);
    }

    /**
     * Applies the world-grid step to one coordinate. With snapping enabled, the result is
     * step × round(position / step), bounded by the largest grid point within the map limit.
     *
     * @param position desired coordinate in world units
     * @param snap     whether to align to the grid
     * @return bounded coordinate in world units
     */
    static float place(float position, boolean snap) {
        if (snap) {
            int last = UpgradeCatalog.MAX_COORDINATE / GRID_STEP;
            return Math.clamp(Math.round(position / GRID_STEP), -last, last) * GRID_STEP;
        }
        return Math.clamp(position, -UpgradeCatalog.MAX_COORDINATE, UpgradeCatalog.MAX_COORDINATE);
    }

    /**
     * Fits the current draft including positions changed since opening.
     */
    void fit() {
        measureGraph(entries);
        overview();
    }

    void pointer(float x, float y) {
        cursorX = x;
        cursorY = y;
    }

    @Nullable ContextPoint cursorPoint() {
        return layoutBounds().contains(cursorX, cursorY) ? new ContextPoint(UpgradeNode.DISCONNECTED, cursorX, cursorY,
                rootToWorldX(cursorX), rootToWorldY(cursorY), null) : null;
    }

    boolean moving() {
        return drag != null || contextGesture != null || selectionGesture != null || pointDrag != null;
    }

    /**
     * Moves the selected nodes by an exact number of map units.
     */
    void nudge(float dx, float dy) {
        var group = anchorPositions();
        translate(group, dx, dy);
        moved.accept(group);
    }

    private List<AnchorPosition> anchorPositions() {
        return selected.stream().map(id -> {
            var anchor = anchors.get(id);
            return new AnchorPosition(id, anchor, anchor.worldX(), anchor.worldY());
        }).toList();
    }

    private static void translate(List<AnchorPosition> group, float dx, float dy) {
        if (group.isEmpty()) {
            return;
        }
        var first = group.getFirst();
        float left = first.x(), right = first.x(), top = first.y(), bottom = first.y();
        for (var position : group) {
            left = Math.min(left, position.x());
            right = Math.max(right, position.x());
            top = Math.min(top, position.y());
            bottom = Math.max(bottom, position.y());
        }
        dx = UpgradeTreeClipboard.boundedOffset(dx, left, right);
        dy = UpgradeTreeClipboard.boundedOffset(dy, top, bottom);
        for (var position : group) {
            position.anchor().worldPosition(position.x() + dx, position.y() + dy);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handle(Event event) {
        if (event instanceof PointerMovedEvent motion && motion.phase() != EventPhase.BUBBLE) {
            pointer(motion.rootX(), motion.rootY());
            if (pointDrag != null && motion.pointerId() == pointDrag.pointerId()) {
                movePoint();
                motion.cancel();
                return;
            }
            if (contextGesture != null && motion.pointerId() == contextGesture.pointerId()) {
                panTo(cursorX, cursorY);
                motion.cancel();
                return;
            }
            if (selectionGesture != null && motion.pointerId() == selectionGesture.pointerId()) {
                invalidate(InvalidationFlags.VISUAL);
                motion.cancel();
                return;
            }
        }
        if (event instanceof PointerPressedEvent press && press.phase() == EventPhase.TARGET && press.target() == this
                && press.button() == PointerButton.PRIMARY && !moving()) {
            pointer(press.rootX(), press.rootY());
            var handle = hitHandle(rootToWorldX(cursorX), rootToWorldY(cursorY));
            if (handle != null) {
                var selection = new LinkSelection(handle.link(), handle.pointIndex());
                var route = selectedRoute();
                var point = route.points().get(selection.pointIndex());
                highlightLink(selection);
                pointDrag = new PointDrag(press.pointerId(), cursorX, cursorY,
                        point.x() - rootToWorldX(cursorX), point.y() - rootToWorldY(cursorY), route);
                uiContext().capturePointer(press.pointerId(), this);
                press.cancel();
                return;
            }
            selectionGesture = new SelectionGesture(press.pointerId(), cursorX, cursorY, shiftDown() || controlDown());
            tooltips.forEach(this::updateTooltipVisibility);
            uiContext().capturePointer(press.pointerId(), this);
            press.cancel();
            return;
        }
        if (event instanceof PointerReleasedEvent release && release.button() == PointerButton.PRIMARY
                && pointDrag != null && release.pointerId() == pointDrag.pointerId()) {
            pointer(release.rootX(), release.rootY());
            movePoint();
            var updated = previewRoute;
            pointDrag = null;
            if (updated != null) {
                paths.remove(updated);
            }
            previewRoute = null;
            uiContext().releasePointer(release.pointerId(), this);
            if (updated != null && selectedLink != null) {
                changedLink.accept(new LinkChange(selectedLink, updated));
            }
            invalidate(InvalidationFlags.VISUAL);
            release.cancel();
            return;
        }
        if (event instanceof PointerReleasedEvent release && release.button() == PointerButton.PRIMARY
                && selectionGesture != null && release.pointerId() == selectionGesture.pointerId()) {
            pointer(release.rootX(), release.rootY());
            var gesture = selectionGesture;
            selectionGesture = null;
            uiContext().releasePointer(release.pointerId(), this);
            tooltips.forEach(this::updateTooltipVisibility);
            finishSelection(gesture);
            invalidate(InvalidationFlags.VISUAL);
            release.cancel();
            return;
        }
        if (event instanceof PointerPressedEvent press && press.phase() != EventPhase.BUBBLE
                && press.button() == PointerButton.SECONDARY && !moving()) {
            int nodeId = UpgradeNode.DISCONNECTED;
            for (var entry : anchors.entrySet()) {
                if (entry.getValue().widget() == press.target()) {
                    nodeId = entry.getKey();
                    break;
                }
            }
            cursorX = press.rootX();
            cursorY = press.rootY();
            panning = false;
            contextGesture = new ContextGesture(nodeId, press.pointerId(), cursorX, cursorY, viewport().x(), viewport().y());
            uiContext().capturePointer(press.pointerId(), this);
            press.cancel();
            return;
        }
        if (event instanceof PointerReleasedEvent release && release.button() == PointerButton.SECONDARY
                && contextGesture != null && contextGesture.pointerId() == release.pointerId()) {
            cursorX = release.rootX();
            cursorY = release.rootY();
            panTo(cursorX, cursorY);
            var finished = contextGesture;
            contextGesture = null;
            uiContext().releasePointer(release.pointerId(), this);
            if (!panning && layoutBounds().contains(cursorX, cursorY)) {
                float x = rootToWorldX(cursorX);
                float y = rootToWorldY(cursorY);
                var link = finished.nodeId() == UpgradeNode.DISCONNECTED ? hitLink(x, y) : null;
                contextMenu.accept(new ContextPoint(finished.nodeId(), cursorX, cursorY,
                        x, y, link));
            }
            release.cancel();
            return;
        }
        super.handle(event);
    }

    private void finishSelection(SelectionGesture gesture) {
        var ids = new ArrayList<Integer>();
        if (pastThreshold(cursorX - gesture.rootX(), cursorY - gesture.rootY())) {
            var area = selectionArea(gesture);
            for (var entry : anchors.entrySet()) {
                var anchor = entry.getValue();
                if (entry.getKey() >= 0 && anchor.visible()
                        && area.contains(worldToRootX(anchor.worldX()), worldToRootY(anchor.worldY()))) {
                    ids.add(entry.getKey());
                }
            }
        } else if (!gesture.additive() || selectedLink != null && shiftDown() && !controlDown()) {
            var hit = hitLink(rootToWorldX(cursorX), rootToWorldY(cursorY));
            if (hit != null && (!gesture.additive() || selectedLink.link().equals(hit.link()))) {
                pickedLink.accept(hit);
                return;
            }
        }
        selectBox.accept(ids, gesture.additive());
    }

    private void movePoint() {
        if (!pastThreshold(cursorX - pointDrag.rootX(), cursorY - pointDrag.rootY()) && previewRoute == null) {
            return;
        }
        var route = pointDrag.route();
        var points = new ArrayList<>(route.points());
        points.set(selectedLink.pointIndex(), new Point(place(rootToWorldX(cursorX) + pointDrag.offsetX()),
                place(rootToWorldY(cursorY) + pointDrag.offsetY())));
        if (previewRoute != null) {
            paths.remove(previewRoute);
        }
        previewRoute = new UpgradeLinkRoute(route.source(), route.style(), points);
        invalidate(InvalidationFlags.VISUAL);
    }

    private void drawSelection(DrawScope draw) {
        var gesture = selectionGesture;
        if (gesture == null || !pastThreshold(cursorX - gesture.rootX(), cursorY - gesture.rootY())) {
            return;
        }
        var area = selectionArea(gesture);
        float width = area.right() - area.left();
        float height = area.bottom() - area.top();
        draw.rectFilled(area.left(), area.top(), width, height, SELECTION_FILL);
        draw.rectStroke(area.left(), area.top(), width, height, HIGHLIGHT, 1);
    }

    private SelectionArea selectionArea(SelectionGesture gesture) {
        return SelectionArea.clipped(gesture.rootX(), gesture.rootY(), cursorX, cursorY, layoutBounds());
    }

    /**
     * @param x horizontal root coordinate of the right-button gesture
     * @param y vertical root coordinate of the right-button gesture
     */
    private void panTo(float x, float y) {
        float dx = x - contextGesture.rootX();
        float dy = y - contextGesture.rootY();
        panning |= pastThreshold(dx, dy);
        if (panning) {
            viewport(contextGesture.viewportX() + dx, contextGesture.viewportY() + dy);
        }
    }

    /**
     * @param event pointer motion routed to the captured item
     */
    private void move(PointerMovedEvent event) {
        if (drag == null || event.pointerId() != drag.pointerId()) {
            return;
        }
        moveTo(event.rootX(), event.rootY());
        event.cancel();
    }

    /**
     * @param event release of the captured pointer
     */
    private void release(PointerReleasedEvent event) {
        if (drag == null || event.button() != PointerButton.PRIMARY || event.pointerId() != drag.pointerId()) {
            return;
        }
        moveTo(event.rootX(), event.rootY());
        var finished = drag;
        drag = null;
        uiContext().releasePointer(event.pointerId(), finished.item());
        if (dragGroup != null) {
            moved.accept(dragGroup);
            dragGroup = null;
        } else if (!dragThresholdPassed && finished.shiftClick()) {
            select.pick(finished.id(), true);
        }
        event.cancel();
    }

    /**
     * @param x horizontal pointer coordinate in root pixels
     * @param y vertical pointer coordinate in root pixels
     */
    private void moveTo(float x, float y) {
        if (!dragThresholdPassed && pastThreshold(x - drag.rootX(), y - drag.rootY())) {
            dragThresholdPassed = true;
            if (!drag.shiftClick() || select.pick(drag.id(), false)) {
                dragGroup = anchorPositions();
            }
        }
        if (dragGroup != null) {
            translate(dragGroup, place(rootToWorldX(x) + drag.offsetX()) - drag.originX(),
                    place(rootToWorldY(y) + drag.offsetY()) - drag.originY());
        }
    }

    /**
     * @param dx horizontal motion in root pixels
     * @param dy vertical motion in root pixels
     * @return whether the pointer moved at least the drag threshold
     */
    private static boolean pastThreshold(float dx, float dy) {
        return dx * dx + dy * dy >= DRAG_THRESHOLD * DRAG_THRESHOLD;
    }

    /**
     * @param id   stable node ID
     * @param x    horizontal position in world units
     * @param y    vertical position in world units
     * @param item rendered node widget
     * @param size node diameter at the reference zoom
     * @return live canvas anchor
     */
    private AnchorWidget addAnchor(int id, float x, float y, TreeNodeButton item, float size) {
        var anchor = anchorLayer().add(Integer.toString(id), x, y, item).screenSize(size, size)
                .pivot(0.5f, 0.5f).zoomScaling(true, REFERENCE_ZOOM).cullOutsideViewport(true);
        anchors.put(id, anchor);
        return anchor;
    }

    /**
     * @param draw editor world layer
     */
    private void drawLinks(DrawScope draw) {
        for (var entry : entries) {
            var node = entry.node();
            if (node.connectsToCore()) {
                drawLink(draw, UpgradeNode.ROOT, node);
            }
            for (int i = 0; i < node.prerequisiteCount(); i++) {
                drawLink(draw, node.prerequisiteId(i), node);
            }
        }
        drawHandles(draw);
        var from = anchors.get(source);
        if (from != null && from.visible() && Float.isFinite(cursorX) && Float.isFinite(cursorY)) {
            draw.addLine(worldToRootX(from.worldX()), worldToRootY(from.worldY()), cursorX, cursorY,
                    ((TreeNodeButton) from.widget()).linkColor(), 1.6f);
        }
    }

    private void drawLink(DrawScope draw, int parent, UpgradeNode child) {
        var from = anchors.get(parent);
        var to = anchors.get(child.id());
        if (from == null || to == null || !from.visible() || !to.visible()) {
            return;
        }
        boolean editing = selectedLink != null && selectedLink.link().matches(parent, child.id());
        var route = editing ? routePreview(child, parent) : child.linkRoute(parent);
        float width = editing ? 2.5f : selected.contains(child.id()) ? 2 : 1.25f;
        var fromColor = ((TreeNodeButton) from.widget()).linkColor();
        var toColor = editing ? HIGHLIGHT : ((TreeNodeButton) to.widget()).linkColor();
        if (route == null) {
            TreeLinkStroke.draw(draw, worldToRootX(from.worldX()), worldToRootY(from.worldY()),
                    worldToRootX(to.worldX()), worldToRootY(to.worldY()), fromColor, toColor, width, 1);
            return;
        }
        var bounds = layoutBounds();
        var segments = path(from, to, route).segments();
        for (int i = 0; i < segments.size(); i++) {
            var segment = segments.get(i);
            float x1 = worldToRootX(segment.from().x());
            float y1 = worldToRootY(segment.from().y());
            float x2 = worldToRootX(segment.to().x());
            float y2 = worldToRootY(segment.to().y());
            if (TreeBoard.visibleLink(bounds, x1, y1, x2, y2)) {
                TreeLinkStroke.draw(draw, x1, y1, x2, y2, fromColor, toColor, width, 1);
            }
        }
    }

    private TreeLinkPath path(AnchorWidget from, AnchorWidget to, @Nullable UpgradeLinkRoute route) {
        var cached = route == null ? null : paths.get(route);
        if (cached != null && cached.from().x() == from.worldX() && cached.from().y() == from.worldY()
                && cached.to().x() == to.worldX() && cached.to().y() == to.worldY()) {
            return cached.path();
        }
        var start = new Point(from.worldX(), from.worldY());
        var end = new Point(to.worldX(), to.worldY());
        var path = TreeLinkPath.build(start, end, route);
        if (route != null) {
            paths.put(route, new CachedPath(start, end, path));
        }
        return path;
    }

    private @Nullable UpgradeLinkRoute selectedRoute() {
        if (selectedLink == null || source != UpgradeNode.DISCONNECTED) {
            return null;
        }
        var link = selectedLink.link();
        var from = anchors.get(link.source());
        var to = anchors.get(link.target());
        if (from == null || to == null || !from.visible() || !to.visible()) {
            return null;
        }
        return routePreview(displayed.get(link.target()).node(), link.source());
    }

    private @Nullable UpgradeLinkRoute routePreview(UpgradeNode target, int source) {
        return previewRoute != null ? previewRoute
                : parameterPreview != null ? parameterPreview : target.linkRoute(source);
    }

    private void drawHandles(DrawScope draw) {
        var route = selectedRoute();
        if (route == null) {
            return;
        }
        for (int i = 0; i < route.points().size(); i++) {
            var point = route.points().get(i);
            float x = worldToRootX(point.x()) - HANDLE_SIZE / 2;
            float y = worldToRootY(point.y()) - HANDLE_SIZE / 2;
            draw.rectFilled(x, y, HANDLE_SIZE, HANDLE_SIZE, selectedLink.pointIndex() == i ? HIGHLIGHT : SELECTION_FILL);
            draw.rectStroke(x, y, HANDLE_SIZE, HANDLE_SIZE, HIGHLIGHT, 1);
        }
    }

    private @Nullable LinkPick hitHandle(float x, float y) {
        var route = selectedRoute();
        if (route == null) {
            return null;
        }
        float radius = LINK_HIT_RADIUS / viewport().zoom();
        float closest = radius * radius;
        LinkPick hit = null;
        for (int i = 0; i < route.points().size(); i++) {
            var point = route.points().get(i);
            float dx = x - point.x();
            float dy = y - point.y();
            float distance = dx * dx + dy * dy;
            if (distance < closest) {
                closest = distance;
                hit = new LinkPick(selectedLink.link(), point, i, i);
            }
        }
        return hit;
    }

    private @Nullable LinkPick hitLink(float x, float y) {
        if (source != UpgradeNode.DISCONNECTED) {
            return null;
        }
        var handle = hitHandle(x, y);
        if (handle != null) {
            return handle;
        }
        float radius = LINK_HIT_RADIUS / viewport().zoom();
        LinkPick closest = null;
        for (var entry : entries) {
            var node = entry.node();
            if (node.connectsToCore()) {
                closest = nearerLink(closest, UpgradeNode.ROOT, node, x, y, radius);
            }
            for (int i = 0; i < node.prerequisiteCount(); i++) {
                closest = nearerLink(closest, node.prerequisiteId(i), node, x, y, radius);
            }
        }
        return closest;
    }

    private @Nullable LinkPick nearerLink(@Nullable LinkPick closest, int parent, UpgradeNode node,
                                          float x, float y, float radius) {
        var from = anchors.get(parent);
        var to = anchors.get(node.id());
        if (from == null || to == null || !from.visible() || !to.visible()) {
            return closest;
        }
        var hit = path(from, to, node.linkRoute(parent)).hit(x, y, radius);
        if (hit == null) {
            return closest;
        }
        if (closest != null) {
            float dx = x - closest.point().x();
            float dy = y - closest.point().y();
            if (dx * dx + dy * dy < hit.distanceSquared() || dx * dx + dy * dy == hit.distanceSquared()
                    && (selectedLink == null || !selectedLink.link().matches(parent, node.id()))) {
                return closest;
            }
        }
        return new LinkPick(new LinkId(parent, node.id()), hit.point(), hit.slot(), -1);
    }

    private record CachedPath(Point from, Point to, TreeLinkPath path) {
    }

    /**
     * Captured node and cursor offset in world coordinates for a zoom-independent drag.
     */
    private record Drag(int id, float originX, float originY, TreeNodeButton item, int pointerId, float rootX,
                        float rootY,
                        float offsetX, float offsetY, boolean shiftClick) {
    }

    private record SelectionGesture(int pointerId, float rootX, float rootY, boolean additive) {
    }

    private record PointDrag(int pointerId, float rootX, float rootY, float offsetX, float offsetY,
                             UpgradeLinkRoute route) {
    }

    /**
     * A selection rectangle in root units, restricted to the visible canvas.
     */
    record SelectionArea(float left, float top, float right, float bottom) {
        static SelectionArea clipped(float startX, float startY, float endX, float endY, RectView viewport) {
            float x = Math.clamp(endX, viewport.x(), viewport.right());
            float y = Math.clamp(endY, viewport.y(), viewport.bottom());
            return new SelectionArea(Math.min(startX, x), Math.min(startY, y), Math.max(startX, x), Math.max(startY, y));
        }

        boolean contains(float x, float y) {
            return x >= left && x <= right && y >= top && y <= bottom;
        }
    }

    record AnchorPosition(int id, AnchorWidget anchor, float x, float y) {
    }

    @FunctionalInterface
    interface NodeSelection {
        boolean pick(int id, boolean additive);
    }

    /**
     * Right press and camera positions in root pixels.
     */
    private record ContextGesture(int nodeId, int pointerId, float rootX, float rootY, float viewportX,
                                  float viewportY) {
    }

    /**
     * Clicked node ID or DISCONNECTED for empty map space, plus its root and world coordinates.
     */
    record ContextPoint(int nodeId, float rootX, float rootY, float worldX, float worldY, @Nullable LinkPick link) {
    }

    record LinkId(int source, int target) {
        boolean matches(int source, int target) {
            return this.source == source && this.target == target;
        }
    }

    /**
     * Waypoint index is -1 when the selection covers only the line.
     */
    record LinkSelection(LinkId link, int pointIndex) {
    }

    record LinkPick(LinkId link, Point point, int insertAt, int pointIndex) {
        LinkSelection selection() {
            return new LinkSelection(link, pointIndex);
        }
    }

    record LinkChange(LinkSelection selection, @Nullable UpgradeLinkRoute route) {
    }
}
