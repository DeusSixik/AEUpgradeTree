package dev.sixik.aeupgrade.client.tree.map;

import static dev.sixik.aeupgrade.client.tree.map.TreeBoard.visibleLink;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import dev.sixik.aeupgrade.client.tree.UpgradeTreeVisibility;
import dev.sixik.aeupgrade.tree.UpgradeCatalog;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute.Point;
import dev.sixik.aeupgrade.tree.UpgradeNode;
import dev.sixik.aeupgrade.tree.UpgradePlan;
import dev.sixik.aeupgrade.tree.UpgradePlan.Availability;
import dev.sixik.unigui.api.math.ColorView;
import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.math.RectView;
import dev.sixik.unigui.api.render.DrawScope;
import dev.sixik.unigui.widgets.world.WorldCanvas;
import org.jetbrains.annotations.Nullable;

/**
 * Draws the viewing map's static traces, animated links and starfield.
 */
public final class UpgradeTreeGraph implements AutoCloseable {
    /**
     * Shadow under the traces.
     */
    private static final ColorView MAP_LOCKED = MutableColor.fromHex("#242A39");

    private final UpgradeCatalog catalog;
    private final UpgradeCatalog.Entry core;
    private final TreeNodeButton coreButton;
    private final Map<UpgradeNode, TreeNodeButton> nodes;
    private final List<GraphLink> links;
    private List<GraphLink> visibleLinks = List.of();
    private List<GraphLink> pulsingLinks = List.of();
    private final TreeStarfield starfield = new TreeStarfield();
    private final @Nullable TreeLinkBatch linkBatch;
    private final @Nullable TreeAvailabilityPulse availabilityPulse;
    private List<UpgradeNode> availableNodes = List.of();
    private UpgradePlan purchases;
    private boolean starfieldDirty = true;
    private Map<Integer, Float> flowDistances = Map.of();

    public UpgradeTreeGraph(TreeCanvas canvas, UpgradeCatalog catalog, UpgradeCatalog.Entry core,
                            TreeNodeButton coreButton, Map<UpgradeNode, TreeNodeButton> nodes) {
        this.catalog = catalog;
        this.core = core;
        this.coreButton = coreButton;
        this.nodes = nodes;
        var experimental = catalog.definition().experimental();
        availabilityPulse = experimental.availableNodePulses() ? new TreeAvailabilityPulse() : null;
        var entries = catalog.definition().nodes();
        var edges = new ArrayList<GraphLink>();
        for (var entry : entries) {
            var node = entry.node();
            if (node.connectsToCore()) {
                edges.add(graphLink(node, UpgradeNode.ROOT));
            }
            for (int i = 0; i < node.prerequisiteCount(); i++) {
                edges.add(graphLink(node, node.prerequisiteId(i)));
            }
        }
        links = List.copyOf(edges);
        int segments = links.stream().mapToInt(edge -> edge.path().segments().size()).sum();
        linkBatch = experimental.animatedLinks() ? new TreeLinkBatch(segments) : null;
        canvas.addWorldLayer((world, draw) -> TreeBoard.drawBoardFrame(world.layoutBounds(), draw));
        if (experimental.starfield()) {
            canvas.addWorldLayer(this::drawStarfield);
        }
        canvas.addWorldLayer(this::drawGraph);
    }

    /**
     * Refreshes the displayed paths and visual effects for the confirmed research state.
     *
     * @param confirmed  latest server allocation
     * @param visibility revealed nodes and links for that allocation
     */
    public void updatePurchases(UpgradePlan confirmed, UpgradeTreeVisibility visibility) {
        if (purchases == null || !purchases.sameUnlocks(confirmed)) {
            starfieldDirty = true;
        }
        purchases = confirmed;
        var ordinaryLinks = new ArrayList<GraphLink>();
        var availableLinks = new ArrayList<GraphLink>();
        for (var link : links) {
            if (!visibility.linkVisible(link.sourceId(), link.target().id())) {
                continue;
            }
            var source = catalog.node(link.sourceId());
            if (availabilityPulse != null && TreeAvailabilityPulse.pulsesLink(
                    source == null ? Availability.UNLOCKED : confirmed.availability(source),
                    confirmed.availability(link.target()))) {
                availableLinks.add(link);
            } else {
                ordinaryLinks.add(link);
            }
        }
        visibleLinks = ordinaryLinks;
        pulsingLinks = availableLinks;
        if (availabilityPulse != null) {
            availableNodes = nodes.keySet().stream()
                    .filter(node -> visibility.visible(node.id()) && confirmed.availability(node) == Availability.AVAILABLE)
                    .toList();
        }
        if (linkBatch != null) {
            flowDistances = TreeLinkEffects.flowDistances(catalog, purchases);
        }
    }

    @Override
    public void close() {
        starfield.close();
        if (linkBatch != null) {
            linkBatch.close();
        }
    }

    /**
     * Draws the stars below the graph, rebuilding the light map on the first frame after an allocation change.
     *
     * @param world canvas with the current pan and zoom transform
     * @param draw  active canvas drawing scope
     */
    private void drawStarfield(WorldCanvas world, DrawScope draw) {
        if (starfieldDirty) {
            var lights = new ArrayList<TreeLightMap.Light>();
            lights.add(TreeStarfield.light(core.node().x(), core.node().y(), coreButton.diameter(), coreButton.linkColor()));
            for (var entry : nodes.entrySet()) {
                var node = entry.getKey();
                if (purchases.unlocked(node)) {
                    var icon = entry.getValue();
                    lights.add(TreeStarfield.light(node.x(), node.y(), icon.diameter(), icon.linkColor()));
                }
            }
            starfield.rebuild(lights);
            starfieldDirty = false;
        }
        starfield.draw(world, draw);
    }

    /**
     * Draws graph edges behind the anchored node buttons.
     *
     * @param world UniGUI canvas with the current pan and zoom transform
     * @param draw  active UI drawing scope
     */
    private void drawGraph(WorldCanvas world, DrawScope draw) {
        float scale = world.viewport().zoom() / TreeCanvas.REFERENCE_ZOOM;
        boolean pulse = availabilityPulse != null && !availableNodes.isEmpty();
        int phase = pulse ? availabilityPulse.phase() : 0;
        float pulseOpacity = pulse ? TreeAvailabilityPulse.linkOpacity(phase) : 1;
        var batch = linkBatch != null && linkBatch.available() ? linkBatch : null;
        if (batch != null) {
            batch.begin();
        }
        for (int i = 0; i < visibleLinks.size(); i++) {
            drawLink(world, draw, visibleLinks.get(i), scale, batch, 1);
        }
        // Crossings and shared routes must leave a powered pulse visible over ordinary traces.
        for (int i = 0; i < pulsingLinks.size(); i++) {
            drawLink(world, draw, pulsingLinks.get(i), scale, batch, pulseOpacity);
        }
        if (batch != null) {
            batch.submit(draw, scale);
        }
        if (pulse) {
            availabilityPulse.drawRings(world, draw, availableNodes, nodes, scale, phase);
        }
    }

    private GraphLink graphLink(UpgradeNode node, int source) {
        var parent = source == UpgradeNode.ROOT ? core : catalog.entry(source);
        var from = new Point(parent.node().x(), parent.node().y());
        var path = TreeLinkPath.build(from, new Point(node.x(), node.y()), node.linkRoute(source));
        var targetStyle = catalog.entry(node.id()).appearance();
        var sourceStyle = parent.appearance();
        float scale = 2 * TreeCanvas.REFERENCE_ZOOM;
        float breaker = TreeLinkEffects.breakerDistance(path, sourceStyle.shape(),
                sourceStyle.size() / scale, targetStyle.shape(), targetStyle.size() / scale);
        var button = nodes.get(node);
        var color = button.linkColor();
        // Composite dim traces onto their opaque underlay once so overlapping joins retain the same brightness.
        float alpha = color.a() * 0.55f;
        var muted = path.segments().size() <= 1 ? color : new MutableColor(
                MAP_LOCKED.r() + (color.r() - MAP_LOCKED.r()) * alpha,
                MAP_LOCKED.g() + (color.g() - MAP_LOCKED.g()) * alpha,
                MAP_LOCKED.b() + (color.b() - MAP_LOCKED.b()) * alpha, 1);
        return new GraphLink(source, source == UpgradeNode.ROOT ? coreButton : nodes.get(parent.node()),
                node, button, path, breaker, muted);
    }

    private void drawLink(WorldCanvas world, DrawScope draw, GraphLink link, float scale, @Nullable TreeLinkBatch batch,
                          float pulseOpacity) {
        var parentButton = link.parentButton();
        var button = link.targetButton();
        var parentStatus = parentButton.status();
        var status = button.status();
        var effect = TreeLinkEffects.effectFor(parentStatus, status);
        var source = parentButton.linkColor();
        var target = button.linkColor();
        boolean closed = status == Availability.EXCLUSIVE
                || link.target().locksRemaining() && parentStatus == Availability.EXCLUSIVE;
        boolean powered = parentStatus == Availability.UNLOCKED;
        boolean current = effect == TreeLinkEffects.Effect.CURRENT;
        boolean bright = current && batch == null || powered && (status == Availability.AVAILABLE
                || status == Availability.PREREQUISITE && link.target().requiredCount() > 1);
        float breaker = effect == TreeLinkEffects.Effect.BREAKER ? link.breaker() : 0;
        float width = Math.max(0.75f, 1.5f * scale);
        float length = link.path().length();
        float startPhase = batch != null && powered && (current || breaker > 0) ? flowDistances.get(link.sourceId()) : 0;
        float phaseSpan = batch != null && current ? flowDistances.get(link.target().id()) - startPhase : 0;
        var bounds = world.layoutBounds();
        var segments = link.path().segments();
        boolean joined = segments.size() > 1;
        var trace = !bright && joined ? link.mutedTrace() : target;
        float traceOpacity = bright || joined ? 1 : 0.55f;
        if (availabilityPulse != null && pulseOpacity < 1) {
            trace = availabilityPulse.linkColor(MAP_LOCKED, target, pulseOpacity);
        }
        if (joined && !closed) {
            drawLinkBacking(world, draw, bounds, segments, 4 * scale);
        }
        for (int i = 0; i < segments.size(); i++) {
            var segment = segments.get(i);
            float x1 = world.worldToRootX(segment.from().x());
            float y1 = world.worldToRootY(segment.from().y());
            float x2 = world.worldToRootX(segment.to().x());
            float y2 = world.worldToRootY(segment.to().y());
            if (x1 == x2 && y1 == y2 || !visibleLink(bounds, x1, y1, x2, y2)) {
                continue;
            }
            if (closed) {
                drawClosedSegment(draw, x1, y1, x2, y2, segment, breaker, length, width, scale, powered,
                        startPhase, source, target, batch);
            } else {
                if (!joined) {
                    draw.addLine(x1, y1, x2, y2, MAP_LOCKED, 4 * scale);
                }
                TreeLinkStroke.draw(draw, x1, y1, x2, y2, source, trace, width, traceOpacity);
                if (batch != null && current) {
                    float from = segment.start() / length;
                    float to = segment.end() / length;
                    batch.add(x1, y1, x2, y2, source, target, startPhase + phaseSpan * from, startPhase + phaseSpan * to,
                            TreeLinkEffects.Wall.NONE, segment.startJoin(), segment.endJoin(), from, to);
                }
            }
        }
    }

    /**
     * Draws the dark layer first so its caps cannot cover the trace on a neighboring segment.
     */
    private static void drawLinkBacking(WorldCanvas world, DrawScope draw, RectView bounds,
                                        List<TreeLinkPath.Segment> segments, float width) {
        for (int i = 0; i < segments.size(); i++) {
            var segment = segments.get(i);
            float x1 = world.worldToRootX(segment.from().x());
            float y1 = world.worldToRootY(segment.from().y());
            float x2 = world.worldToRootX(segment.to().x());
            float y2 = world.worldToRootY(segment.to().y());
            if (visibleLink(bounds, x1, y1, x2, y2)) {
                draw.addLine(x1, y1, x2, y2, MAP_LOCKED, width);
            }
        }
    }

    private static void drawClosedSegment(DrawScope draw, float x1, float y1, float x2, float y2,
                                          TreeLinkPath.Segment segment, float breaker, float length, float width, float scale, boolean powered,
                                          float startPhase, ColorView source, ColorView target, @Nullable TreeLinkBatch batch) {
        float span = segment.end() - segment.start();
        float dx = x2 - x1;
        float dy = y2 - y1;
        if (segment.start() < breaker) {
            float stop = Math.min(segment.end(), breaker);
            float t = (stop - segment.start()) / span;
            float endX = x1 + dx * t;
            float endY = y1 + dy * t;
            TreeLinkStroke.draw(draw, x1, y1, endX, endY, source, target, width, powered ? 0.55f : 0.25f);
            boolean wallHere = breaker <= segment.end();
            if (batch != null && (powered || wallHere)) {
                var wall = !wallHere ? TreeLinkEffects.Wall.NONE
                        : powered ? TreeLinkEffects.Wall.POWERED : TreeLinkEffects.Wall.UNPOWERED;
                batch.add(x1, y1, endX, endY, source, target, startPhase + segment.start(), startPhase + stop, wall,
                        segment.startJoin(), wallHere ? TreeLinkPath.END_CAP : segment.endJoin(),
                        segment.start() / breaker, stop / breaker);
            } else if (wallHere) {
                TreeLinkStroke.drawBreaker(draw, endX, endY, dx, dy, scale, powered);
            }
        }
        float tail = length - breaker;
        for (int dash = 0; dash < 4; dash++) {
            float dashStart = breaker + tail * (dash * 0.28f);
            float dashEnd = breaker + tail * (dash * 0.28f + 0.16f);
            float start = Math.max(segment.start(), dashStart);
            float end = Math.min(segment.end(), dashEnd);
            if (start < end) {
                float a = (start - segment.start()) / span;
                float b = (end - segment.start()) / span;
                TreeLinkStroke.draw(draw, x1 + dx * a, y1 + dy * a, x1 + dx * b, y1 + dy * b,
                        source, target, Math.max(0.8f, 2 * scale), 0.38f);
            }
        }
    }

    /**
     * Prepared geometry and the branch break in world units.
     */
    private record GraphLink(int sourceId, TreeNodeButton parentButton, UpgradeNode target,
                             TreeNodeButton targetButton,
                             TreeLinkPath path, float breaker, ColorView mutedTrace) {
    }
}
