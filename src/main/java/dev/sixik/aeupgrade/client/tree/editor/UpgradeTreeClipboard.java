package dev.sixik.aeupgrade.client.tree.editor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import dev.sixik.aeupgrade.tree.UpgradeCatalog;
import dev.sixik.aeupgrade.tree.UpgradeCatalog.Entry;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute.Point;
import dev.sixik.aeupgrade.tree.UpgradeNode;

/**
 * Pastes a subgraph with fresh IDs and a common offset from its first node.
 */
final class UpgradeTreeClipboard {
    private UpgradeTreeClipboard() {
    }

    static List<Entry> paste(List<Entry> copied, int nextId, float x, float y) {
        var first = copied.getFirst().node();
        float dx = x - first.x();
        float dy = y - first.y();
        float left = first.x(), right = first.x(), top = first.y(), bottom = first.y();
        var ids = new HashMap<Integer, Integer>();
        for (var entry : copied) {
            var node = entry.node();
            ids.put(node.id(), nextId + ids.size());
            left = Math.min(left, node.x());
            right = Math.max(right, node.x());
            top = Math.min(top, node.y());
            bottom = Math.max(bottom, node.y());
        }
        for (var entry : copied) {
            for (var route : entry.node().routes()) {
                if (route.source() != UpgradeNode.ROOT && !ids.containsKey(route.source())) {
                    continue;
                }
                for (var point : route.points()) {
                    left = Math.min(left, point.x());
                    right = Math.max(right, point.x());
                    top = Math.min(top, point.y());
                    bottom = Math.max(bottom, point.y());
                }
            }
        }
        dx = boundedOffset(dx, left, right);
        dy = boundedOffset(dy, top, bottom);
        var pasted = new ArrayList<Entry>(copied.size());
        for (var entry : copied) {
            var node = entry.node();
            int id = ids.get(node.id());
            var copy = new UpgradeNode(id, "custom." + id, node.cost(), node.amount(), node.sector(), node.route(),
                    node.connectsToCore() ? UpgradeNode.ROOT : UpgradeNode.DISCONNECTED, UpgradeNode.ROOT,
                    node.x() + dx, node.y() + dy, node.kind());
            if (node.prerequisiteCount() > 0) {
                var parents = node.prerequisiteIds().stream().filter(ids::containsKey).map(ids::get).toList();
                copy = copy.withPrerequisites(parents, Math.min(node.requiredCount(), parents.size()),
                        node.locksRemaining() && !parents.isEmpty());
            }
            for (var route : node.routes()) {
                int source = route.source() == UpgradeNode.ROOT ? UpgradeNode.ROOT
                        : ids.getOrDefault(route.source(), UpgradeNode.DISCONNECTED);
                if (source != UpgradeNode.DISCONNECTED) {
                    var points = new ArrayList<Point>();
                    for (var point : route.points()) {
                        points.add(new Point(point.x() + dx, point.y() + dy));
                    }
                    copy = copy.withLinkRoute(source, new UpgradeLinkRoute(source, route.style(), points));
                }
            }
            var branches = entry.branches().stream().filter(ids::containsKey).map(ids::get).toList();
            String title = entry.title().isBlank() ? "@aeupgrade.tree.node." + node.key() : entry.title();
            if (title.length() > UpgradeCatalog.MAX_TITLE_LENGTH) {
                title = "";
            }
            pasted.add(new Entry(copy, title, entry.description(), entry.icon(), entry.effects(), entry.path(), entry.style(),
                    branches, entry.reveal()));
        }
        return pasted;
    }

    /**
     * Limits a group translation by its outermost node centers without changing relative positions.
     */
    static float boundedOffset(float offset, float min, float max) {
        return Math.clamp(offset, -UpgradeCatalog.MAX_COORDINATE - min, UpgradeCatalog.MAX_COORDINATE - max);
    }
}
