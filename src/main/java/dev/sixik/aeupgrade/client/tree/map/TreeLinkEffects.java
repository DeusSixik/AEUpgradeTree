package dev.sixik.aeupgrade.client.tree.map;

import java.util.HashMap;
import java.util.Map;

import dev.sixik.aeupgrade.tree.UpgradeCatalog;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute.Point;
import dev.sixik.aeupgrade.tree.UpgradeNode;
import dev.sixik.aeupgrade.tree.UpgradeNodeStyle.Shape;
import dev.sixik.aeupgrade.tree.UpgradePlan;
import dev.sixik.aeupgrade.tree.UpgradePlan.Availability;

/**
 * Moving pulses with a shared clock and an impact at excluded branch entrances.
 */
final class TreeLinkEffects {
    static final float PULSE_SPACING = 144;
    private static final long PULSE_PERIOD_NS = 1_750_000_000L;
    private static final long START_TIME = System.nanoTime();

    enum Effect {
        NONE, CURRENT, BREAKER
    }

    /**
     * Values match the wall kind passed to tree_current.fsh.
     */
    enum Wall {
        NONE, POWERED, UNPOWERED
    }

    private TreeLinkEffects() {
    }

    static Effect effectFor(Availability source, Availability target) {
        if (target == Availability.EXCLUSIVE && source != Availability.EXCLUSIVE) {
            return Effect.BREAKER;
        }
        return source == Availability.UNLOCKED && target == Availability.UNLOCKED ? Effect.CURRENT : Effect.NONE;
    }

    /**
     * Distance the current has traveled within the repeating pulse cycle, sampled once per frame.
     */
    static float pulsePosition() {
        return (float) ((System.nanoTime() - START_TIME) % PULSE_PERIOD_NS / (double) PULSE_PERIOD_NS * PULSE_SPACING);
    }

    /**
     * Gives each purchased node a common arrival phase for all of its powered inputs.
     */
    static Map<Integer, Float> flowDistances(UpgradeCatalog catalog, UpgradePlan plan) {
        var distances = new HashMap<Integer, Float>();
        distances.put(UpgradeNode.ROOT, 0f);
        for (var node : catalog.ordered()) {
            if (!plan.unlocked(node)) {
                continue;
            }
            float distance = node.connectsToCore() ? arrivalDistance(catalog, distances, node, UpgradeNode.ROOT) : 0;
            for (int i = 0; i < node.prerequisiteCount(); i++) {
                distance = Math.max(distance, arrivalDistance(catalog, distances, node, node.prerequisiteId(i)));
            }
            distances.put(node.id(), distance);
        }
        return Map.copyOf(distances);
    }

    private static float arrivalDistance(UpgradeCatalog catalog, Map<Integer, Float> distances, UpgradeNode node, int previous) {
        var start = distances.get(previous);
        if (start == null) {
            return 0;
        }
        var parent = catalog.node(previous);
        float x = parent == null ? catalog.definition().core().x() : parent.x();
        float y = parent == null ? catalog.definition().core().y() : parent.y();
        var route = node.linkRoute(previous);
        return start + (route == null ? (float) Math.hypot(node.x() - x, node.y() - y)
                : TreeLinkPath.build(new Point(x, y), new Point(node.x(), node.y()), route).length());
    }

    /**
     * @return the distance to the breaker along the route, or zero when no gap is visible
     */
    static float breakerDistance(TreeLinkPath path, Shape source, float sourceRadius, Shape target, float targetRadius) {
        if (path.segments().isEmpty()) {
            return 0;
        }
        float start = inset(path.segments().getFirst(), source, sourceRadius);
        float end = path.length() - inset(path.segments().getLast(), target, targetRadius);
        return breakerDistance(start, end);
    }

    private static float inset(TreeLinkPath.Segment segment, Shape shape, float radius) {
        float dx = segment.to().x() - segment.from().x();
        float dy = segment.to().y() - segment.from().y();
        float length = (float) Math.hypot(dx, dy);
        return outlineDistance(shape, radius, dx / length, dy / length);
    }

    private static float breakerDistance(float start, float end) {
        float gap = end - start;
        return gap < 2 ? 0 : start + Math.min(gap * 0.46f, 44);
    }

    private static float outlineDistance(Shape shape, float radius, float dx, float dy) {
        return switch (shape) {
            case CIRCLE -> radius;
            case DIAMOND -> radius / (Math.abs(dx) + Math.abs(dy));
            case SQUARE, FRAMELESS -> radius / Math.max(Math.abs(dx), Math.abs(dy));
        };
    }

}
