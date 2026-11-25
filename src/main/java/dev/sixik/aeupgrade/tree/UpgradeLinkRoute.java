package dev.sixik.aeupgrade.tree;

import java.util.List;

/**
 * @param source prerequisite ID or the ME core
 * @param style  how the line joins its waypoints
 * @param points interior waypoints in map coordinates, ordered from source to destination
 */
public record UpgradeLinkRoute(int source, Style style, List<Point> points) {
    public static final int MAX_POINTS = 24;

    public UpgradeLinkRoute {
        if (source < UpgradeNode.ROOT || style == null || points == null || points.size() > MAX_POINTS
                || points.stream().anyMatch(point -> point == null || !UpgradeCatalog.validCoordinate(point.x())
                || !UpgradeCatalog.validCoordinate(point.y()))) {
            throw new IllegalArgumentException("Invalid link route for source " + source + ".");
        }
        points = List.copyOf(points);
    }

    /**
     * @param x horizontal world coordinate
     * @param y vertical world coordinate
     */
    public record Point(float x, float y) {
    }

    public enum Style {
        POLYLINE, ORTHOGONAL, ROUNDED, ROUNDED_ORTHOGONAL;

        public boolean orthogonal() {
            return this == ORTHOGONAL || this == ROUNDED_ORTHOGONAL;
        }

        public boolean rounded() {
            return this == ROUNDED || this == ROUNDED_ORTHOGONAL;
        }
    }
}
