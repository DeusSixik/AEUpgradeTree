package dev.sixik.aeupgrade.client.tree;

import static dev.sixik.aeupgrade.client.tree.UpgradeTreeText.tr;

import java.math.BigDecimal;

import dev.sixik.aeupgrade.tree.UpgradeEffect;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Kind;
import dev.sixik.aeupgrade.tree.UpgradeEffect.Target;
import dev.sixik.unigui.api.math.ColorView;
import dev.sixik.unigui.api.math.MutableColor;
import net.minecraft.network.chat.Component;

/**
 * Localized labels shared by the tree tooltip and editor effect form.
 */
public final class UpgradeEffectText {
    /**
     * An editor unit represents ten percentage points. JSON and runtime values already use percentage points.
     */
    private static final int PERCENT_INPUT_DECIMAL_SHIFT = 1;
    private static final ColorView BENEFIT = MutableColor.fromHex("#85E4BA");
    private static final ColorView PENALTY = MutableColor.fromHex("#FF9F9F");
    private static final ColorView NEUTRAL = MutableColor.fromHex("#C4C9D9");

    /**
     * Keeps formatting stateless.
     */
    private UpgradeEffectText() {
    }

    /**
     * @param kind property of the effect
     * @return localized property name with units
     */
    private static String kind(Kind kind) {
        return tr("effect." + kind.key());
    }

    /**
     * @return display name of a built-in, registered or missing effect
     */
    public static String kind(UpgradeEffect effect) {
        if (effect.kind() != null) {
            return kind(effect.kind());
        }
        var registered = effect.registeredType();
        return registered == null ? tr("effect.missing", effect.type())
                : Component.translatable(registered.translationKey()).getString();
    }

    /**
     * @param target device family affected
     * @return localized family name
     */
    public static String target(Target target) {
        return tr("effect_target." + target.key());
    }

    /**
     * @param effect configured modifier
     * @return its override or the default color for its benefit, penalty or neutral value
     */
    public static ColorView color(UpgradeEffect effect) {
        if (effect.tooltipColor() != null) {
            return MutableColor.fromHex(effect.tooltipColor());
        }
        return switch (effect.impact()) {
            case BENEFIT -> BENEFIT;
            case PENALTY -> PENALTY;
            case NEUTRAL -> NEUTRAL;
        };
    }

    /**
     * @param value number stored by the graph
     * @return decimal representation suitable for a numeric field
     */
    private static String number(double value) {
        return Math.abs(value) >= 1e12 || value != 0 && Math.abs(value) < 1e-6
                ? Double.toString(value) : BigDecimal.valueOf(value).stripTrailingZeros().toPlainString();
    }

    /**
     * @param effect configured modifier with its value in runtime units
     * @return the amount to load into the editor's numeric field
     */
    public static String editorAmount(UpgradeEffect effect) {
        if (!effect.percentage()) {
            return number(effect.value());
        }
        var scaled = BigDecimal.valueOf(effect.value()).scaleByPowerOfTen(-PERCENT_INPUT_DECIMAL_SHIFT).stripTrailingZeros();
        int exponent = scaled.precision() - scaled.scale() - 1;
        return exponent >= 12 || exponent < -6 ? scaled.toString() : scaled.toPlainString();
    }

    /**
     * @param percentage whether the selected property is stored in percentage points
     * @param input      typed amount using a dot or comma as the decimal separator
     * @return amount in runtime units, with editor percentages multiplied by ten
     */
    public static double parseEditorAmount(boolean percentage, String input) {
        String amount = input.strip().replace(',', '.');
        if (!percentage) {
            return Double.parseDouble(amount);
        }
        if (amount.endsWith("%")) {
            amount = amount.substring(0, amount.length() - 1).stripTrailing();
        }
        try {
            double value = new BigDecimal(amount).scaleByPowerOfTen(PERCENT_INPUT_DECIMAL_SHIFT).doubleValue();
            if (Double.isFinite(value)) {
                return value;
            }
        } catch (ArithmeticException outOfRange) {
            throw new NumberFormatException("Percentage exponent is out of range.");
        }
        throw new NumberFormatException("Percentage amount is out of range.");
    }

    /**
     * @param kind  selected effect property
     * @param value amount in runtime units
     * @return signed amount with its percent suffix, or the unsigned absolute device cap
     */
    static String amount(Kind kind, double value) {
        if (kind == Kind.DEVICE_CAP) {
            return number(value);
        }
        return amount(kind.percentage(), value);
    }

    public static String amount(boolean percentage, double value) {
        return signed(value) + (percentage ? "%" : "");
    }

    /**
     * @param effect configured modifier
     * @return localized tooltip line
     */
    static String describe(UpgradeEffect effect) {
        if (effect.kind() == Kind.CHANNELS) {
            return tr("stat.channels") + "  " + signed(effect.value()) + "\n"
                    + tr("stat.dense_channels") + "  " + signed(effect.value() * 4);
        }
        String label = kind(effect);
        if (effect.target() != Target.ALL) {
            label += " · " + target(effect.target());
        }
        return label + " " + (effect.kind() == null ? amount(effect.percentage(), effect.value())
                : amount(effect.kind(), effect.value()));
    }

    /**
     * @param value signed modifier
     * @return sign and readable decimal amount
     */
    private static String signed(double value) {
        return (value < 0 ? "−" : "+") + number(Math.abs(value));
    }
}
