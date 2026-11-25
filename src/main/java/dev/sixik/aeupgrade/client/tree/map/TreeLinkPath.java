package dev.sixik.aeupgrade.client.tree.map;

import java.util.ArrayList;
import java.util.List;

import dev.sixik.aeupgrade.tree.UpgradeLinkRoute;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute.Point;
import org.jetbrains.annotations.Nullable;

/**
 * World-space path shared by drawing, hit testing, and the current animation.
 */
public record TreeLinkPath(List<Segment> segments, float length) {
    /**
     * Sentinel passed to the pulse shader for a capped endpoint.
     */
    static final float END_CAP = 4;
    private static final float MAX_JOIN_SLOPE = 2.5f;
    private static final float CORNER_TRIM = 18;
    /**
     * One quarter of a GUI unit at the maximum zoom of 2.5.
     */
    private static final float MAX_CORNER_ERROR = 0.10f;
    private static final float EPSILON = 0.001f;

    /**
     * @param start center of the source node in world units
     * @param end   center of the destination node in world units
     * @param route shape and waypoints, or null for a single straight segment
     * @return nonzero segments ordered from source to destination with cumulative distances
     */
    public static TreeLinkPath build(Point start, Point end, @Nullable UpgradeLinkRoute route) {
        if (route == null) {
            float length = distance(start, end);
            return length <= EPSILON ? new TreeLinkPath(List.of(), 0)
                    : new TreeLinkPath(List.of(new Segment(start, end, 0, length, 0)), length);
        }

        var corners = new ArrayList<Vertex>();
        corners.add(new Vertex(start, 0));
        for (int i = 0; i <= route.points().size(); i++) {
            var next = i == route.points().size() ? end : route.points().get(i);
            var last = corners.getLast().point();
            if (route.style().orthogonal() && last.x() != next.x() && last.y() != next.y()) {
                corners.add(new Vertex(new Point(next.x(), last.y()), i));
            }
            if (!corners.getLast().point().equals(next)) {
                corners.add(new Vertex(next, i));
            }
        }

        var segments = new ArrayList<Segment>();
        var last = start;
        for (int i = 1; i < corners.size(); i++) {
            var corner = corners.get(i);
            if (route.style().rounded() && i + 1 < corners.size()) {
                var next = corners.get(i + 1);
                var curve = rounded(corners.get(i - 1).point(), corner.point(), next.point());
                if (!curve.isEmpty()) {
                    append(segments, last, curve.getFirst(), corner.slot());
                    for (int j = 1; j < curve.size(); j++) {
                        int slot = j <= (curve.size() - 1) / 2 ? corner.slot() : next.slot();
                        append(segments, curve.get(j - 1), curve.get(j), slot);
                    }
                    last = curve.getLast();
                    continue;
                }
            }
            append(segments, last, corner.point(), corner.slot());
            last = corner.point();
        }
        joinSegments(segments);
        return new TreeLinkPath(List.copyOf(segments), segments.isEmpty() ? 0 : segments.getLast().end());
    }

    /**
     * At each corner, the sum of the two unit tangents is normal to the shared cut.
     * In either segment's local coordinates the cut has the form {@code x = endpoint + slope * y}.
     * The slopes have opposite signs and magnitude {@code sin(turn) / (1 + cos(turn))}.
     * A near reversal keeps round caps to avoid a long, unstable miter.
     */
    private static void joinSegments(List<Segment> segments) {
        for (int i = 1; i < segments.size(); i++) {
            var before = segments.get(i - 1);
            var after = segments.get(i);
            if (!before.to().equals(after.from())) {
                continue;
            }
            double ax = before.to().x() - before.from().x();
            double ay = before.to().y() - before.from().y();
            double bx = after.to().x() - after.from().x();
            double by = after.to().y() - after.from().y();
            double cross = ax * by - ay * bx;
            double denominator = Math.hypot(ax, ay) * Math.hypot(bx, by) + ax * bx + ay * by;
            if (denominator <= 0 || Math.abs(cross) > MAX_JOIN_SLOPE * denominator) {
                continue;
            }
            float slope = (float) (cross / denominator);
            segments.set(i - 1, new Segment(before.from(), before.to(), before.start(), before.end(),
                    before.slot(), before.startJoin(), -slope));
            segments.set(i, new Segment(after.from(), after.to(), after.start(), after.end(),
                    after.slot(), slope, after.endJoin()));
        }
    }

    /**
     * Samples a quadratic Bézier from the trimmed incoming line to the trimmed outgoing line,
     * with the original corner as its control point. Each trim uses at most half its adjacent
     * segment, so two consecutive rounded corners can meet without overlapping.
     */
    private static List<Point> rounded(Point previous, Point corner, Point next) {
        float before = distance(previous, corner);
        float after = distance(corner, next);
        float cross = (previous.x() - corner.x()) * (next.y() - corner.y())
                - (previous.y() - corner.y()) * (next.x() - corner.x());
        if (before <= EPSILON || after <= EPSILON || Math.abs(cross) <= before * after * EPSILON) {
            return List.of();
        }
        float trim = Math.min(CORNER_TRIM, Math.min(before, after) / 2);
        var from = interpolate(corner, previous, trim / before);
        var to = interpolate(corner, next, trim / after);
        // A quadratic Bézier chord over an interval of width 1/n deviates by at most
        // |from - 2*corner + to| / (4*n²). Short corners need fewer segments.
        double curvature = Math.hypot((from.x() - corner.x()) + (to.x() - corner.x()),
                (from.y() - corner.y()) + (to.y() - corner.y()));
        int segments = Math.max(2, (int) Math.ceil(Math.sqrt(curvature / (4 * MAX_CORNER_ERROR))));
        var curve = new ArrayList<Point>(segments + 1);
        for (int i = 0; i <= segments; i++) {
            float t = (float) i / segments;
            curve.add(interpolate(interpolate(from, corner, t), interpolate(corner, to, t), t));
        }
        return curve;
    }

    private static void append(List<Segment> segments, Point from, Point to, int slot) {
        float length = distance(from, to);
        float start = segments.isEmpty() ? 0 : segments.getLast().end();
        if (length > EPSILON && start + length > start) {
            segments.add(new Segment(from, to, start, start + length, slot));
        }
    }

    /**
     * @return the nearest projected point within the world-space hit radius, or null
     */
    public @Nullable Hit hit(float x, float y, float radius) {
        Hit best = null;
        float limit = radius * radius;
        for (var segment : segments) {
            var from = segment.from();
            var to = segment.to();
            float dx = to.x() - from.x();
            float dy = to.y() - from.y();
            float projection = ((x - from.x()) * dx + (y - from.y()) * dy) / (dx * dx + dy * dy);
            var point = interpolate(from, to, Math.clamp(projection, 0, 1));
            float offsetX = x - point.x();
            float offsetY = y - point.y();
            float distanceSquared = offsetX * offsetX + offsetY * offsetY;
            if (distanceSquared < limit) {
                best = new Hit(point, segment.slot(), distanceSquared);
                limit = distanceSquared;
            }
        }
        return best;
    }

    private static Point interpolate(Point a, Point b, float t) {
        return new Point(a.x() + (b.x() - a.x()) * t, a.y() + (b.y() - a.y()) * t);
    }

    private static float distance(Point a, Point b) {
        return (float) Math.hypot(b.x() - a.x(), b.y() - a.y());
    }

    /**
     * @param start     cumulative distance at the start of the segment in world units
     * @param end       cumulative distance at the end in world units
     * @param slot      insertion index among the saved waypoints
     * @param startJoin shared cut slope at the entrance, or {@link #END_CAP}
     * @param endJoin   shared cut slope at the exit, or {@link #END_CAP}
     */
    public record Segment(Point from, Point to, float start, float end, int slot, float startJoin, float endJoin) {
        Segment(Point from, Point to, float start, float end, int slot) {
            this(from, to, start, end, slot, END_CAP, END_CAP);
        }
    }

    public record Hit(Point point, int slot, float distanceSquared) {
    }

    private record Vertex(Point point, int slot) {
    }
}
