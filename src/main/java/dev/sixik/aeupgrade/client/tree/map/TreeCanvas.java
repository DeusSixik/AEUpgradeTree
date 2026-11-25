package dev.sixik.aeupgrade.client.tree.map;

import java.util.List;

import dev.sixik.aeupgrade.client.tree.UpgradeTreeVisibility;
import dev.sixik.aeupgrade.tree.UpgradeCatalog;
import dev.sixik.aeupgrade.tree.UpgradeNode;
import dev.sixik.unigui.api.event.ScrollEvent;
import dev.sixik.unigui.api.input.KeyModifiers;
import dev.sixik.unigui.api.input.PointerButton;
import dev.sixik.unigui.api.math.RectView;
import dev.sixik.unigui.api.viewport.Viewport2D;
import dev.sixik.unigui.api.viewport.ViewportBounds;
import dev.sixik.unigui.widgets.world.WorldCanvas;

/**
 * Fits the initial tree to the laid-out canvas and adds zoom on an unmodified wheel scroll.
 */
public class TreeCanvas extends WorldCanvas {
    /**
     * Reference zoom at which anchors use their declared diameters.
     */
    public static final float REFERENCE_ZOOM = 0.88f;
    /**
     * Distance between the canvas grid points in world units.
     */
    public static final int GRID_STEP = 22;
    private static final int VIEW_MARGIN = 2 * GRID_STEP;
    private float coreX;
    private float coreY;
    /**
     * Horizontal half extent around the core, including icons, in world units.
     */
    private float extentX;
    /**
     * Vertical half extent around the core, including icons, in world units.
     */
    private float extentY;

    /**
     * Temporary viewport for computing cursor-centered zoom before publishing the change.
     */
    private final Viewport2D wheelViewport = new Viewport2D();

    /**
     * Configures the graph camera and its mouse input.
     */
    public TreeCanvas() {
        zoomRange(0.01f, 2.5f).panButton(PointerButton.SECONDARY).wheelPanningEnabled(false);
        on(ScrollEvent.TYPE, this::zoomWithWheel);
    }

    /**
     * Fits the first layout and preserves the viewed center across later canvas size changes.
     *
     * @param bounds new bounds in root UI pixels
     */
    @Override
    public void arrange(RectView bounds) {
        float oldWidth = layoutBounds().width();
        float oldHeight = layoutBounds().height();
        float oldX = viewport().x();
        float oldY = viewport().y();
        super.arrange(bounds);
        if (oldWidth <= 0 || oldHeight <= 0) {
            overview();
        } else if (bounds.width() != oldWidth || bounds.height() != oldHeight) {
            viewport(oldX + (bounds.width() - oldWidth) / 2, oldY + (bounds.height() - oldHeight) / 2);
        }
    }

    /**
     * Centers the core with the starter nodes and route choices at a readable scale.
     */
    public void center() {
        var bounds = layoutBounds();
        float zoom = Math.min(REFERENCE_ZOOM,
                Math.min(bounds.width() / (350 * UpgradeNode.HORIZONTAL_STRETCH), bounds.height() / 275));
        viewport(bounds.width() / 2 - coreX * zoom, bounds.height() / 2 - coreY * zoom, zoom);
    }

    /**
     * Fits the whole viewing area or the editor's core-centered draft.
     */
    public void overview() {
        var bounds = layoutBounds();
        var graph = viewport().worldBounds();
        if (graph != null) {
            float zoom = Math.clamp(Math.min(bounds.width() / graph.width(), bounds.height() / graph.height()),
                    viewport().minZoom(), REFERENCE_ZOOM);
            viewport((bounds.width() - graph.width() * zoom) / 2 - graph.x() * zoom,
                    (bounds.height() - graph.height() * zoom) / 2 - graph.y() * zoom, zoom);
            return;
        }
        float zoom = Math.min(REFERENCE_ZOOM, Math.min(bounds.width() / (2 * extentX), bounds.height() / (2 * extentY)));
        viewport(bounds.width() / 2 - coreX * zoom, bounds.height() / 2 - coreY * zoom, zoom);
    }

    /**
     * Applies the viewer's bounds during layout and immediately after any camera input.
     */
    public void limitToGraph(UpgradeCatalog.Definition definition, UpgradeTreeVisibility visibility) {
        // UniGUI 1.6.4 applies world bounds during layout, so refresh the clamp on input as well.
        if (viewport().worldBounds() == null) {
            onViewportChanged(event -> clampToWorldBounds(true));
        }
        coreX = definition.core().x();
        coreY = definition.core().y();
        var graph = viewingBounds(definition, visibility);
        worldBounds(graph.x(), graph.y(), graph.width(), graph.height()).clampToWorldBounds(true);
    }

    static ViewportBounds viewingBounds(UpgradeCatalog.Definition definition, UpgradeTreeVisibility visibility) {
        var core = definition.core().asEntry();
        float coreRadius = core.appearance().size() / (2 * REFERENCE_ZOOM);
        float left = core.node().x() - coreRadius;
        float top = core.node().y() - coreRadius;
        float right = core.node().x() + coreRadius;
        float bottom = core.node().y() + coreRadius;
        for (var entry : definition.nodes()) {
            var node = entry.node();
            if (!visibility.visible(node.id())) {
                continue;
            }
            float radius = entry.appearance().size() / (2 * REFERENCE_ZOOM);
            left = Math.min(left, node.x() - radius);
            top = Math.min(top, node.y() - radius);
            right = Math.max(right, node.x() + radius);
            bottom = Math.max(bottom, node.y() + radius);
            for (var route : node.routes()) {
                if (!visibility.linkVisible(route.source(), node.id())) {
                    continue;
                }
                for (var point : route.points()) {
                    left = Math.min(left, point.x());
                    top = Math.min(top, point.y());
                    right = Math.max(right, point.x());
                    bottom = Math.max(bottom, point.y());
                }
            }
        }
        return new ViewportBounds(left - VIEW_MARGIN, top - VIEW_MARGIN,
                right - left + 2 * VIEW_MARGIN, bottom - top + 2 * VIEW_MARGIN);
    }

    /**
     * @param entries editable nodes, starting with the core
     */
    protected void measureGraph(List<UpgradeCatalog.Entry> entries) {
        var core = entries.getFirst().node();
        coreX = core.x();
        coreY = core.y();
        extentX = 54;
        extentY = 54;
        for (var entry : entries) {
            float margin = Math.max(36, entry.appearance().size() / REFERENCE_ZOOM / 2 + 6);
            extentX = Math.max(extentX, Math.abs(entry.node().x() - coreX) + margin);
            extentY = Math.max(extentY, Math.abs(entry.node().y() - coreY) + margin);
            for (var route : entry.node().routes()) {
                for (var point : route.points()) {
                    extentX = Math.max(extentX, Math.abs(point.x() - coreX) + 36);
                    extentY = Math.max(extentY, Math.abs(point.y() - coreY) + 36);
                }
            }
        }
    }

    /**
     * Zooms around the pointer by {@code zoomStep() ^ deltaY}, where {@code deltaY} is the wheel delta.
     * UniGUI already implements the same behavior for Ctrl-scroll.
     *
     * @param event scroll delta and pointer position in root UI pixels
     */
    private void zoomWithWheel(ScrollEvent event) {
        if (event.deltaY() == 0 || KeyModifiers.has(event.modifiers(), KeyModifiers.CONTROL)) {
            return;
        }
        var current = viewport();
        var bounds = layoutBounds();
        wheelViewport.zoomRange(current.minZoom(), current.maxZoom());
        wheelViewport.set(current.x(), current.y(), current.zoom());
        if (wheelViewport.zoomAt(event.rootX() - bounds.x(), event.rootY() - bounds.y(),
                (float) Math.pow(zoomStep(), event.deltaY()))) {
            viewport(wheelViewport.x(), wheelViewport.y(), wheelViewport.zoom());
        }
        event.cancel();
    }
}
