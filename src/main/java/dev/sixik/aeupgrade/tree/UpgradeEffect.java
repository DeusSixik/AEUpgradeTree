package dev.sixik.aeupgrade.tree;

import java.util.Locale;
import java.util.regex.Pattern;

import dev.sixik.aeupgrade.api.UpgradeEffectRegistry;
import dev.sixik.aeupgrade.api.UpgradeEffectType;
import org.jetbrains.annotations.Nullable;

/**
 * @param kind         built-in property, or null for a registered effect
 * @param target       device family, or all applicable devices
 * @param value        signed adjustment in the property's units, or an absolute value for a cap or base
 * @param tooltipColor optional RGB override, or null to use the color for its impact
 * @param type         namespaced ID of a registered effect, or null for a built-in property
 */
public record UpgradeEffect(@Nullable Kind kind, Target target, double value, @Nullable String tooltipColor,
                            @Nullable String type) {
    /**
     * Maximum magnitude for continuous effects, in AE/t or percentage points.
     */
    private static final double MAX_VALUE = Float.MAX_VALUE;
    private static final Pattern RGB = Pattern.compile("#[0-9A-F]{6}");

    public UpgradeEffect {
        tooltipColor = canonicalTooltipColor(tooltipColor);
        if (type != null && target == null) {
            target = Target.ALL;
        }
    }

    public UpgradeEffect(Kind kind, Target target, double value, @Nullable String tooltipColor) {
        this(kind, target, value, tooltipColor, null);
    }

    public UpgradeEffect(Kind kind, Target target, double value) {
        this(kind, target, value, null);
    }

    /**
     * @return an effect entry that can be written to a tree definition
     */
    public static UpgradeEffect custom(UpgradeEffectType type, Target target, double value) {
        return new UpgradeEffect(null, target, value, null, type.id());
    }

    /**
     * @return the registered definition for an add-on effect when available
     */
    public @Nullable UpgradeEffectType registeredType() {
        return type == null ? null : UpgradeEffectRegistry.get(type);
    }

    /**
     * @return whether the amount is stored in percentage points
     */
    public boolean percentage() {
        if (kind != null) {
            return kind.percentage();
        }
        var registered = registeredType();
        return registered != null && registered.unit() == UpgradeEffectType.Unit.PERCENT;
    }

    /**
     * @param color typed or serialized tooltip color
     * @return canonical RGB color, or null for automatic coloring
     */
    public static @Nullable String normalizeTooltipColor(@Nullable String color) {
        String hex = canonicalTooltipColor(color);
        if (hex != null && !RGB.matcher(hex).matches()) {
            throw new IllegalArgumentException("Expected a #RRGGBB effect tooltip color.");
        }
        return hex;
    }

    private static @Nullable String canonicalTooltipColor(@Nullable String color) {
        if (color == null || color.isBlank()) {
            return null;
        }
        String hex = color.strip();
        return (hex.startsWith("#") ? hex : "#" + hex).toUpperCase(Locale.ROOT);
    }

    /**
     * How the modifier affects the player for tooltip presentation.
     */
    public enum Impact {
        BENEFIT, PENALTY, NEUTRAL
    }

    /**
     * @return impact of this effect independently of a custom tooltip color
     */
    public Impact impact() {
        if (kind == null) {
            var registered = registeredType();
            return registered == null ? Impact.NEUTRAL : registered.polarity().impact(value);
        }
        if (kind == Kind.DEVICE_CAP) {
            return Impact.PENALTY;
        }
        if (value == 0) {
            return Impact.NEUTRAL;
        }
        boolean higherIsWorse = switch (kind) {
            case IDLE_FLAT, IDLE_PERCENT, CRAFT_FLAT, CRAFT_PERCENT, CHANNEL_COST -> true;
            default -> false;
        };
        return (value > 0) == higherIsWorse ? Impact.PENALTY : Impact.BENEFIT;
    }

    /**
     * Whether this effect is valid for its property and target.
     */
    public boolean valid() {
        if (target == null || !Double.isFinite(value) || Math.abs(value) > MAX_VALUE
                || (tooltipColor != null && !RGB.matcher(tooltipColor).matches())) {
            return false;
        }
        if (kind == null) {
            if (!UpgradeEffectRegistry.validId(type)) {
                return false;
            }
            var registered = registeredType();
            // Keep the saved entry if its add-on is unavailable.
            return registered == null || registered.accepts(target, value);
        }
        return type == null && (kind.targeted() || target == Target.ALL)
                && (!kind.whole || value == Math.rint(value) && Math.abs(value) <= Integer.MAX_VALUE)
                && (kind != Kind.DEVICE_CAP || value >= 0);
    }

    /**
     * Supported effects backed by live AEUpgradeTree mechanics.
     */
    public enum Kind {
        CHANNELS(true, false),
        PATTERN_SLOTS(true, false),
        INTERFACE_SLOTS(true, false),
        BATCH(true, false),
        SPEED_PERCENT(false, false),
        SPEED_BASE(false, false),
        SPEED_CARDS(true, false),
        THROUGHPUT(true, false),
        IDLE_FLAT(false, true),
        IDLE_PERCENT(false, true),
        CRAFT_FLAT(false, false),
        CRAFT_PERCENT(false, false),
        CHANNEL_COST(true, true),
        DEVICE_LIMIT(true, true),
        DEVICE_CAP(true, true);

        /**
         * True for properties measured in whole counts.
         */
        private final boolean whole;
        /**
         * True for effects that can select a device family.
         */
        private final boolean targeted;

        /**
         * @param whole    whether only whole amounts are valid
         * @param targeted whether the effect can select a device family
         */
        Kind(boolean whole, boolean targeted) {
            this.whole = whole;
            this.targeted = targeted;
        }

        /**
         * @return whether the UI should offer a device selector
         */
        public boolean targeted() {
            return targeted;
        }

        /**
         * @return whether the runtime amount is measured in percentage points
         */
        public boolean percentage() {
            return switch (this) {
                case SPEED_PERCENT, SPEED_BASE, IDLE_PERCENT, CRAFT_PERCENT -> true;
                default -> false;
            };
        }

        /**
         * @return localization suffix
         */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Supported device families for targeted effects.
     */
    public enum Target {
        ALL, MOLECULAR_ASSEMBLER, PATTERN_PROVIDER, INTERFACE;

        /**
         * @return localization suffix
         */
        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }
}
