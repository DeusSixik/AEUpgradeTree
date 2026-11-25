package dev.sixik.aeupgrade.client.tree.map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import dev.sixik.unigui.api.math.MutableColor;
import org.junit.jupiter.api.Test;

class TreeLightMapTest {
    @Test
    void samplesLightAtWorldAlignedTexelCenters() {
        var light = new TreeLightMap.Light(-20, 12, 64, MutableColor.fromHex("#36E9FF"));
        var map = TreeLightMap.bake(List.of(light));

        assertEquals(0xFFFFE936, sample(map, -20, 12));
        assertEquals(sample(map, -52, 12), sample(map, 12, 12));
        assertEquals(sample(map, -20, -20), sample(map, 12, 12));
        assertTrue(alpha(sample(map, 12, 12)) > alpha(sample(map, 28, 12)));
        assertEquals(0, sample(map, 44, 12));
        assertEquals(0, map.pixels()[0]);
        assertEquals(0, map.pixels()[map.pixels().length - 1]);
    }

    @Test
    void keepsBothTintsWhereTheLightsOverlap() {
        var red = new TreeLightMap.Light(-20, 4, 64, MutableColor.fromHex("#FF0000"));
        var blue = new TreeLightMap.Light(20, 4, 64, MutableColor.fromHex("#0000FF"));
        var map = TreeLightMap.bake(List.of(red, blue));
        var single = TreeLightMap.bake(List.of(blue));
        int mixed = sample(map, 4, 44);

        int redComponent = mixed & 0xFF;
        int blueComponent = (mixed >>> 16) & 0xFF;
        assertTrue(alpha(mixed) > alpha(sample(single, 4, 44)));
        assertTrue(alpha(mixed) < 255);
        assertTrue(redComponent > 0);
        assertTrue(blueComponent > 0);
        assertEquals(0, (mixed >>> 8) & 0xFF);
        assertTrue(Math.abs(alpha(mixed) - redComponent - blueComponent) <= 1);
        assertEquals(0xFFFF0000, sample(map, 20, 4));
    }

    private static int sample(TreeLightMap map, int worldX, int worldY) {
        int col = (worldX - map.x()) / TreeLightMap.STEP;
        int row = (worldY - map.y()) / TreeLightMap.STEP;
        return map.pixels()[row * map.width() + col];
    }

    private static int alpha(int pixel) {
        return pixel >>> 24;
    }
}
