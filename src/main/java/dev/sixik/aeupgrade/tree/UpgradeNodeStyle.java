package dev.sixik.aeupgrade.tree;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

import org.jetbrains.annotations.Nullable;

/**
 * Visual settings shared by the editor preview and the playable map.
 */
public record UpgradeNodeStyle(float size, Shape shape, @Nullable String iconTexture, List<String> backgrounds,
                               @Nullable String borderColor, @Nullable Float borderWidth,
                               @Nullable Float hoverBorderWidth) {
    public static final int MIN_SIZE = 8;
    public static final int MAX_SIZE = 128;
    public static final int MAX_BACKGROUNDS = 8;
    public static final int MAX_BORDER_WIDTH = MAX_SIZE / 2;
    private static final Pattern TEXTURE = Pattern.compile("[a-z0-9_.-]+:textures/[a-z0-9_/.-]+\\.png");
    private static final Pattern RGB = Pattern.compile("#?[0-9a-fA-F]{6}");

    public UpgradeNodeStyle(float size, Shape shape, @Nullable String iconTexture, List<String> backgrounds) {
        this(size, shape, iconTexture, backgrounds, null, null, null);
    }

    public UpgradeNodeStyle(float size, Shape shape, @Nullable String iconTexture, List<String> backgrounds,
                            @Nullable String borderColor, @Nullable Float borderWidth) {
        this(size, shape, iconTexture, backgrounds, borderColor, borderWidth, null);
    }

    /**
     * FRAMELESS leaves the silhouette to the transparent background textures.
     */
    public enum Shape {
        CIRCLE, SQUARE, DIAMOND, FRAMELESS
    }

    public static UpgradeNodeStyle defaults(UpgradeNode node) {
        int size = node.id() == UpgradeNode.ROOT ? 39 : switch (node.kind()) {
            case STARTER -> 31;
            case CHOICE -> 32;
            case PASSIVE -> 22;
            case CAPSTONE -> 36;
        };
        return new UpgradeNodeStyle(size, Shape.CIRCLE, null, List.of());
    }

    public UpgradeNodeStyle validated() {
        if (!Float.isFinite(size) || size < MIN_SIZE || size > MAX_SIZE || shape == null
                || iconTexture != null && !validTexture(iconTexture) || backgrounds == null
                || backgrounds.size() > MAX_BACKGROUNDS || backgrounds.stream().anyMatch(value -> !validTexture(value))
                || !validBorderWidth(borderWidth) || !validBorderWidth(hoverBorderWidth)) {
            throw new IllegalArgumentException("Invalid node appearance.");
        }
        return new UpgradeNodeStyle(size, shape, iconTexture, List.copyOf(backgrounds), normalizeBorderColor(borderColor),
                borderWidth, hoverBorderWidth);
    }

    /**
     * Applies the ordered layers while keeping the rest of the node's appearance.
     */
    public UpgradeNodeStyle withBackgrounds(List<String> layers) {
        return new UpgradeNodeStyle(size, shape, iconTexture, layers, borderColor, borderWidth, hoverBorderWidth).validated();
    }

    /**
     * Resolves the unscaled frame width for the current pointer and selection state.
     */
    public float resolveBorderWidth(boolean highlighted, boolean hovered) {
        if (hovered && hoverBorderWidth != null) {
            return hoverBorderWidth;
        }
        if (borderWidth == null) {
            return highlighted || hovered ? 2 : 1;
        }
        return hovered && borderWidth > 0 ? Math.min(MAX_BORDER_WIDTH, borderWidth + 1) : borderWidth;
    }

    private static boolean validBorderWidth(@Nullable Float value) {
        return value == null || Float.isFinite(value) && value >= 0 && value <= MAX_BORDER_WIDTH;
    }

    private static @Nullable String normalizeBorderColor(@Nullable String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String hex = value.strip();
        if (!RGB.matcher(hex).matches()) {
            throw new IllegalArgumentException("Expected a #RRGGBB border color.");
        }
        return (hex.startsWith("#") ? hex : "#" + hex).toUpperCase(Locale.ROOT);
    }

    /**
     * Checks the syntax of a resource-pack PNG ID.
     */
    public static boolean validTexture(@Nullable String value) {
        return value != null && value.length() <= 160 && TEXTURE.matcher(value).matches()
                && !value.contains("..") && !value.contains("//");
    }
}
