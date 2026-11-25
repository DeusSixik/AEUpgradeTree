package dev.sixik.aeupgrade.client.tree.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sixik.unigui.api.render.DrawPoint;
import org.junit.jupiter.api.Test;

class NodeCircleClipTest {
    @Test
    void outline_matchesCircleInsideFullImageCoordinates() {
        var outline = NodeCircleClip.outline();
        int quarter = outline.size() / 4;
        assertEquals(new DrawPoint(1, 0.5f), outline.getFirst());
        assertEquals(new DrawPoint(0.5f, 1), outline.get(quarter));
        assertEquals(new DrawPoint(0, 0.5f), outline.get(2 * quarter));
        assertEquals(new DrawPoint(0.5f, 0), outline.get(3 * quarter));
        for (int index = 0; index < outline.size(); index++) {
            var point = outline.get(index);
            var next = outline.get((index + 1) % outline.size());
            // Positions are rounded to float before calculating the radius in the assertion.
            assertEquals(0.5, Math.hypot(point.x() - 0.5, point.y() - 0.5), 2 * Math.ulp(0.5f));
            assertTrue((point.x() - 0.5) * (next.y() - 0.5) - (point.y() - 0.5) * (next.x() - 0.5) > 0);
        }
    }
}
