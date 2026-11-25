package dev.sixik.aeupgrade.client.tree.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import dev.sixik.aeupgrade.tree.UpgradeLinkRoute;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute.Point;
import dev.sixik.aeupgrade.tree.UpgradeLinkRoute.Style;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class TreeLinkPathTest {
    @Test
    void build_orthogonalWithAControlPoint_usesAxisAlignedLegsInOrder() {
        var route = new UpgradeLinkRoute(1, Style.ORTHOGONAL, List.of(new Point(30, 20)));
        var path = TreeLinkPath.build(new Point(0, 0), new Point(80, 55), route);
        assertEquals(List.of(new Point(30, 0), new Point(30, 20), new Point(80, 20), new Point(80, 55)),
                path.segments().stream().map(TreeLinkPath.Segment::to).toList());
        assertEquals(List.of(0, 0, 1, 1), path.segments().stream().map(TreeLinkPath.Segment::slot).toList());
        assertEquals(135f, path.length(), 0f);
        assertEquals(0, path.hit(29, 10, 4).slot());
        assertEquals(1, path.hit(60, 22, 4).slot());
        assertNull(path.hit(55, 55, 4));
    }

    @Test
    void build_roundedRightTurn_joinsTheEndpointsAndCutsTheCorner() {
        var from = new Point(0, 0);
        var to = new Point(80, 60);
        var shape = new UpgradeLinkRoute(1, Style.ROUNDED_ORTHOGONAL, List.of());
        var path = TreeLinkPath.build(from, to, shape);
        assertEquals(from, path.segments().getFirst().from());
        assertEquals(to, path.segments().getLast().to());
        assertTrue(path.length() < 140);
        assertTrue(path.length() > Math.hypot(80, 60));
        assertNull(path.hit(80, 0, 4));
        assertNotNull(path.hit(75.5f, 4.5f, 1));
        for (int i = 0; i <= 100; i++) {
            float t = i / 100f;
            float x = 62 + 36 * t - 18 * t * t;
            float y = 18 * t * t;
            // The chord error limit is 0.10 world units. Allow 0.001 for float arithmetic.
            assertNotNull(path.hit(x, y, 0.101f));
        }
        for (int i = 1; i < path.segments().size(); i++) {
            var before = path.segments().get(i - 1);
            var after = path.segments().get(i);
            assertEquals(before.to(), after.from());
            assertEquals(before.end(), after.start(), 0f);
            assertTrue(Math.abs(after.startJoin()) < 1);
            assertEquals(-before.endJoin(), after.startJoin(), 0f);
        }
    }

    @Test
    void build_shortRoundedTurn_usesFewerSegmentsThanAFullRadiusTurn() {
        var from = new Point(0, 0);
        var shape = new UpgradeLinkRoute(1, Style.ROUNDED_ORTHOGONAL, List.of());
        var shortTurn = TreeLinkPath.build(from, new Point(6, 6), shape);
        var longTurn = TreeLinkPath.build(from, new Point(60, 60), shape);
        assertTrue(shortTurn.segments().size() < longTurn.segments().size());
    }

    @ParameterizedTest
    @ValueSource(ints = {-1, 1})
    void build_rightAngle_givesAdjacentSegmentsTheSameWorldSpaceCut(int turn) {
        var shape = new UpgradeLinkRoute(1, Style.ORTHOGONAL, List.of());
        var path = TreeLinkPath.build(new Point(0, 0), new Point(60, 80 * turn), shape);
        var horizontal = path.segments().getFirst();
        var vertical = path.segments().getLast();
        assertEquals(-turn, horizontal.endJoin(), 0f);
        assertEquals(turn, vertical.startJoin(), 0f);
        assertEquals(TreeLinkPath.END_CAP, horizontal.startJoin(), 0f);
        assertEquals(TreeLinkPath.END_CAP, vertical.endJoin(), 0f);
    }

    @Test
    void build_duplicatePointsAndReversal_producesOnlyNonzeroSegments() {
        var start = new Point(0, 0);
        var end = new Point(30, 0);
        var shape = new UpgradeLinkRoute(1, Style.ROUNDED, List.of(start, end, end, start));
        var path = TreeLinkPath.build(start, end, shape);
        assertEquals(3, path.segments().size());
        assertEquals(90f, path.length(), 0f);
        for (var segment : path.segments()) {
            assertEquals(TreeLinkPath.END_CAP, segment.startJoin(), 0f);
            assertEquals(TreeLinkPath.END_CAP, segment.endJoin(), 0f);
        }
        assertTrue(TreeLinkPath.build(start, start, null).segments().isEmpty());
    }
}
