package dev.sixik.aeupgrade.client.widget;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class AeScrollbarTest {
    @ParameterizedTest
    @CsvSource({"-10, 4, 80, 0", "4, 4, 80, 0", "44, 4, 80, 0.5",
            "51, 11, 80, 0.5", "84, 4, 80, 1", "100, 4, 80, 1", "20, 4, 0, 0"})
    void scrollbar_fixedThumbUsesItsGrabPointAndFullTrack(float pointer, float grab, float travel, float expected) {
        assertEquals(expected, AeScrollbar.dragFraction(pointer, grab, travel), Math.ulp(expected));
    }
}
