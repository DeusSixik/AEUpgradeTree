package dev.sixik.aeupgrade.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.List;

import com.google.gson.Gson;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute.Point;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute.Style;
import org.junit.jupiter.api.Test;

class UpgradeLinkRouteTest {
    @Test
    void config_routedPrerequisite_roundTripsAndDropsItsRouteWhenRewired() {
        var route = new UpgradeLinkRoute(2, Style.ROUNDED_ORTHOGONAL, List.of(new Point(14, 75), new Point(32, -47)));
        var node = UpgradeNode.OVERDRIVE.withLinkRoute(2, route);
        var gson = new Gson();
        var decoded = gson.fromJson(gson.toJson(node), UpgradeNode.class);
        assertEquals(node, decoded);
        assertEquals(route, decoded.linkRoute(2));
        assertEquals(route, decoded.withCostAndPosition(3, 8, 19).linkRoute(2));
        assertNull(decoded.withPrerequisites(List.of(4), 1).linkRoute(2));
        assertNull(decoded.withLinkRoute(2, null).linkRoute(2));
    }

    @Test
    void config_legacyNodeWithoutRoutes_readsAsStraight() {
        var gson = new Gson();
        var decoded = gson.fromJson(gson.toJson(UpgradeNode.OVERDRIVE), UpgradeNode.class);
        assertEquals(UpgradeNode.OVERDRIVE, decoded);
        assertNull(decoded.linkRoute(2));
    }

    @Test
    void catalog_routeForUnconnectedSource_rejectsDocument() {
        var gson = new Gson();
        var node = gson.toJsonTree(UpgradeNode.OVERDRIVE).getAsJsonObject();
        node.add("linkRoutes", gson.toJsonTree(List.of(new UpgradeLinkRoute(4, Style.ORTHOGONAL, List.of()))));
        var speed = new UpgradeCatalog.Entry(UpgradeNode.SPEED, "", "", "");
        var overdrive = new UpgradeCatalog.Entry(gson.fromJson(node, UpgradeNode.class), "", "", "");
        assertThrows(IllegalArgumentException.class,
                () -> new UpgradeCatalog(new UpgradeCatalog.Definition(10, 7, null, List.of(speed, overdrive))));
    }

    @Test
    void route_invalidCoordinatesOrTooManyPoints_areRejected() {
        assertThrows(IllegalArgumentException.class,
                () -> new UpgradeLinkRoute(-1, Style.POLYLINE, List.of(new Point(0, Float.NaN))));
        assertThrows(IllegalArgumentException.class,
                () -> new UpgradeLinkRoute(-1, Style.ROUNDED, List.of(new Point(4001, 0))));
        assertThrows(IllegalArgumentException.class, () -> new UpgradeLinkRoute(-1, Style.ROUNDED,
                Collections.nCopies(UpgradeLinkRoute.MAX_POINTS + 1, new Point(0, 0))));
    }
}
