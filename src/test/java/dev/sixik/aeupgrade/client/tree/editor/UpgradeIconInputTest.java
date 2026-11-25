package dev.sixik.aeupgrade.client.tree.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UpgradeIconInputTest {
    @Test
    void parse_itemId_replacesTheFallbackAndClearsTheTexture() {
        assertEquals(new UpgradeIconInput("ae2:controller", null), UpgradeIconInput.parse(" ae2:controller ", "minecraft:stick"));
        assertEquals(new UpgradeIconInput("minecraft:diamond", null), UpgradeIconInput.parse("diamond", "minecraft:stick"));
    }

    @Test
    void parse_savedTextureAndItem_retainsTheItemFallback() {
        String item = "minecraft:stick";
        String texture = "pack:textures/upgrade/icon.png";
        assertEquals(texture, UpgradeIconInput.display(item, texture));
        assertEquals(new UpgradeIconInput(item, texture), UpgradeIconInput.parse(UpgradeIconInput.display(item, texture), item));
        assertEquals(item, UpgradeIconInput.display(item, null));
    }

    @Test
    void parse_emptyField_restoresDefaultIcon() {
        assertEquals(new UpgradeIconInput("", null), UpgradeIconInput.parse("  ", "minecraft:stick"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"pack:", "pack:textures/icon", "pack:textures/../icon.png", "pack:textures//icon.png",
            "pack:textures/icon.jpg", "pack:textures/Bad.png", "two words"})
    void parse_invalidResource_rejectsIncompleteAndMalformedIds(String input) {
        assertThrows(IllegalArgumentException.class, () -> UpgradeIconInput.parse(input, ""));
    }
}
