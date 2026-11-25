package dev.sixik.aeupgrade.client.tree.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sixik.unigui.api.math.ColorView;
import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.render.DrawVertex;
import org.junit.jupiter.api.Test;

class TreeLinkStrokeTest {
    private static final MutableColor START = MutableColor.fromHex("#FF0000");
    private static final MutableColor END = MutableColor.fromHex("#0000FF");
    // Allows float rounding through the perpendicular normalization and endpoint offsets.
    private static final float EPSILON = 0.00001f;

    @Test
    void vertices_diagonalLink_assignsColorsAndKeepsConstantWidth() {
        var vertices = TreeLinkStroke.vertices(0, 0, 3, 4, START, END, 2);
        assertEquals(6, vertices.size());
        assertVertex(vertices.get(0), -0.8f, 0.6f, START);
        assertVertex(vertices.get(1), 2.2f, 4.6f, END);
        assertVertex(vertices.get(2), 3.8f, 3.4f, END);
        assertVertex(vertices.get(3), -0.8f, 0.6f, START);
        assertVertex(vertices.get(4), 3.8f, 3.4f, END);
        assertVertex(vertices.get(5), 0.8f, -0.6f, START);
    }

    @Test
    void vertices_overlappingNodes_returnsEmptyMesh() {
        assertTrue(TreeLinkStroke.vertices(2, 3, 2, 3, START, END, 2).isEmpty());
    }

    private static void assertVertex(DrawVertex vertex, float x, float y, ColorView color) {
        assertEquals(x, vertex.x(), EPSILON);
        assertEquals(y, vertex.y(), EPSILON);
        assertEquals(color.r(), vertex.color().r(), 0f);
        assertEquals(color.g(), vertex.color().g(), 0f);
        assertEquals(color.b(), vertex.color().b(), 0f);
        assertEquals(color.a(), vertex.color().a(), 0f);
    }
}
