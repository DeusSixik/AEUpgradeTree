package dev.sixik.aeupgrade.client.tree.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.sixik.unigui.api.math.MutableRect;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** Verifies grid alignment and free placement within the saved map bounds. */
class UpgradeEditorCanvasTest {
    /** @param input cursor coordinate in world units
     *  @param snap whether grid alignment is enabled
     *  @param expected configured map coordinate */
    @ParameterizedTest
    @CsvSource({
            "0, true, 0", "10.9, true, 0", "11.1, true, 22", "-11.1, true, -22",
            "4000, true, 3982", "-4001, true, -3982", "24.25, false, 24.25", "-4001, false, -4000"
    })
    void place_requestedCoordinate_obeysGridAndMapBounds(float input, boolean snap, float expected) {
        assertEquals(expected, UpgradeEditorCanvas.place(input, snap), Math.ulp(expected));
    }

    @Test
    void selectionArea_reverseDragOutsideCanvas_clipsAndIncludesBoundary() {
        var viewport = new MutableRect(20, 30, 140, 100);
        var area = UpgradeEditorCanvas.SelectionArea.clipped(82, 92, -40, 150, viewport);
        assertEquals(new UpgradeEditorCanvas.SelectionArea(20, 92, 82, 130), area);
        assertTrue(area.contains(20, 130));
        assertTrue(area.contains(64, 108));
        assertFalse(area.contains(19, 108));
        assertFalse(area.contains(82, 91));
    }
}
