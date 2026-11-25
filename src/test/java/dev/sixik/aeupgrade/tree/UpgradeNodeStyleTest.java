package dev.sixik.aeupgrade.tree;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import dev.sixik.aeupgrade.tree.UpgradeNodeStyle.Shape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class UpgradeNodeStyleTest {
    private static final String FRONT = "pack:textures/tree/front.png";
    private static final String BACK = "pack:textures/tree/back.png";
    private static final String ICON = "pack:textures/tree/icon.png";

    @Test
    void withBackgrounds_reorderedLayers_preservesOrderAndOtherAppearance() {
        var original = new UpgradeNodeStyle(24, Shape.DIAMOND, ICON, List.of(BACK, FRONT), "#123ABC", 3f, 5f);
        var reordered = new ArrayList<>(List.of(FRONT, BACK));
        var updated = original.withBackgrounds(reordered);
        reordered.clear();

        assertEquals(new UpgradeNodeStyle(24, Shape.DIAMOND, ICON, List.of(FRONT, BACK), "#123ABC", 3f, 5f), updated);
        assertEquals(List.of(BACK, FRONT), original.backgrounds());
        assertEquals(List.of(), updated.withBackgrounds(List.of()).backgrounds());
    }

    @Test
    void withBackgrounds_overLimitOrInvalidPath_rejectsTheEdit() {
        var style = new UpgradeNodeStyle(24, Shape.CIRCLE, null, List.of());
        assertThrows(IllegalArgumentException.class,
                () -> style.withBackgrounds(Collections.nCopies(UpgradeNodeStyle.MAX_BACKGROUNDS + 1, BACK)));
        assertThrows(IllegalArgumentException.class, () -> style.withBackgrounds(List.of("pack:textures/tree/front.jpg")));
    }

    @Test
    void validated_optionalBorder_normalizesRgbAndAcceptsZeroWidth() {
        var style = new UpgradeNodeStyle(24, Shape.CIRCLE, null, List.of(), " 12aB3c ", 0f).validated();
        assertEquals("#12AB3C", style.borderColor());
        assertEquals(0f, style.borderWidth(), 0f);
        assertNull(new UpgradeNodeStyle(24, Shape.CIRCLE, null, List.of(), " ", null).validated().borderColor());
    }

    @ParameterizedTest
    @ValueSource(strings = {"#123", "#12345678", "ocean", "#Q7D010"})
    void validated_invalidBorderColor_rejectsTheEdit(String color) {
        assertThrows(IllegalArgumentException.class,
                () -> new UpgradeNodeStyle(24, Shape.CIRCLE, null, List.of(), color, null).validated());
    }

    @ParameterizedTest
    @ValueSource(floats = {-1f, 64.01f, Float.NaN, Float.POSITIVE_INFINITY})
    void validated_invalidBorderWidth_rejectsTheEdit(float width) {
        assertThrows(IllegalArgumentException.class,
                () -> new UpgradeNodeStyle(24, Shape.CIRCLE, null, List.of(), null, width).validated());
        assertThrows(IllegalArgumentException.class,
                () -> new UpgradeNodeStyle(24, Shape.CIRCLE, null, List.of(), null, null, width).validated());
    }

    @Test
    void validated_maxBorderWidth_acceptsBoundary() {
        var style = new UpgradeNodeStyle(24, Shape.SQUARE, null, List.of(), "#AABBCC", 64f).validated();
        assertEquals(64f, style.borderWidth(), 0f);
    }

    @ParameterizedTest
    @CsvSource(value = {
            "null, null, false, false, 1",
            "null, null, true, false, 2",
            "null, null, false, true, 2",
            "3.5, null, true, false, 3.5",
            "3.5, null, true, true, 4.5",
            "64, null, true, true, 64",
            "0, null, false, true, 0",
            "3.5, 0, true, true, 0",
            "0, 5, false, true, 5",
            "null, 9.5, true, true, 9.5",
            "3.5, 9.5, true, false, 3.5",
            "3.5, 9.5, true, true, 9.5"
    }, nullValues = "null")
    void resolveBorderWidth_hoverAndHighlight_useTheExpectedWidth(Float normal, Float hover, boolean highlighted,
            boolean hovered, float expected) {
        var style = new UpgradeNodeStyle(24, Shape.CIRCLE, null, List.of(), null, normal, hover);
        assertEquals(expected, style.resolveBorderWidth(highlighted, hovered), 0f);
    }
}
