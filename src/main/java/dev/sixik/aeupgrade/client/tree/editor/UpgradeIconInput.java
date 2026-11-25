package dev.sixik.aeupgrade.client.tree.editor;

import java.util.regex.Pattern;

import dev.sixik.aeupgrade.tree.UpgradeNodeStyle;
import org.jetbrains.annotations.Nullable;

/**
 * Translates a single editor field into the existing item and texture settings.
 */
record UpgradeIconInput(String item, @Nullable String texture) {
    static final int MAX_LENGTH = 160;
    private static final Pattern RESOURCE_ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_/.-]+");

    static String display(String item, @Nullable String texture) {
        return texture == null ? item : texture;
    }

    static boolean isTexturePath(String path) {
        return path.startsWith("textures/") && path.endsWith(".png");
    }

    static UpgradeIconInput parse(String input, String fallbackItem) {
        String id = input.strip();
        if (id.isEmpty()) {
            return new UpgradeIconInput("", null);
        }
        if (!id.contains(":")) {
            id = "minecraft:" + id;
        }
        if (!RESOURCE_ID.matcher(id).matches()) {
            throw new IllegalArgumentException("Invalid icon resource ID.");
        }
        String path = id.substring(id.indexOf(':') + 1);
        if (path.startsWith("textures/")) {
            if (!UpgradeNodeStyle.validTexture(id)) {
                throw new IllegalArgumentException("Invalid PNG resource ID.");
            }
            return new UpgradeIconInput(fallbackItem, id);
        }
        if (id.length() > 100) {
            throw new IllegalArgumentException("Item ID is too long.");
        }
        return new UpgradeIconInput(id, null);
    }
}
