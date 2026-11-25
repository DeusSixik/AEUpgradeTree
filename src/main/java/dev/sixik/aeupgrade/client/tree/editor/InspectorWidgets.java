package dev.sixik.aeupgrade.client.tree.editor;

import static dev.sixik.aeupgrade.client.tree.UpgradeTreeText.tr;
import static dev.sixik.aeupgrade.client.widget.AeStyle.setLabel;

import java.util.Locale;

import dev.sixik.aeupgrade.client.widget.AeButton;
import dev.sixik.aeupgrade.client.widget.AeStyle;
import dev.sixik.aeupgrade.client.widget.AeTextField;
import dev.sixik.unigui.api.layout.Align;
import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.impl.widget.WidgetBase;
import dev.sixik.unigui.widgets.containers.HBox;
import dev.sixik.unigui.widgets.containers.VBox;
import dev.sixik.unigui.widgets.display.Text;
import dev.sixik.unigui.widgets.display.TextWidget;
import dev.sixik.unigui.widgets.feedback.Popup;
import dev.sixik.unigui.widgets.interaction.Button;
import dev.sixik.unigui.widgets.interaction.ColorPicker;
import dev.sixik.unigui.widgets.interaction.TextField;

/**
 * AE2-styled fields, rows and color controls shared by editor inspectors.
 */
final class InspectorWidgets {
    /**
     * Shades the AE2 panel behind UniGUI's light picker labels.
     */
    private static final MutableColor PICKER_PANEL_TINT = MutableColor.fromHex("#7B85A0");

    private InspectorWidgets() {
    }

    /**
     * @param input numeric text field
     * @return parsed signed integer
     */
    static int integer(TextField input) {
        return Integer.parseInt(input.text().strip());
    }

    static float decimal(TextField input) {
        return Float.parseFloat(input.text().strip().replace(',', '.'));
    }

    /**
     * @param max maximum text length
     * @return compact single-line input
     */
    static TextField field(int max) {
        return new AeTextField(max);
    }

    /**
     * @param value localized label
     * @param size  font size in GUI units
     * @return light-panel label
     */
    static TextWidget label(String value, int size) {
        var label = new Text().color(AeStyle.TEXT_COLOR);
        setLabel(label, value, size);
        return label;
    }

    static HBox coordinates(TextField x, TextField y) {
        var coordinates = new HBox();
        coordinates.spacing(4);
        coordinates.layout(style -> style.alignItems(Align.CENTER));
        x.layout(style -> style.expand().minWidth(0).height(18));
        y.layout(style -> style.expand().minWidth(0).height(18));
        coordinates.addChild(label("X", 8));
        coordinates.addChild(x);
        coordinates.addChild(label("Y", 8));
        coordinates.addChild(y);
        return coordinates;
    }

    /**
     * @param picker color control to match the compact AE2 inspector palette
     * @return styled picker popup
     */
    static Popup styleColorPicker(ColorPicker picker) {
        picker.type(ColorPicker.Type.HSV);
        picker.layout(style -> style.size(18, 18).flexNone());
        var swatch = picker.preview();
        swatch.layout(style -> style.size(18, 18).flexNone());
        swatch.interactionTransitions(false).radius(0);
        var popup = picker.popup().placement(Popup.Placement.ABOVE).offset(0, 4);
        AeStyle.stylePopupPanel(popup);
        popup.backgroundTextureTint(PICKER_PANEL_TINT);
        compactColorPicker(popup);
        return popup;
    }

    static String rgbHex(int argb) {
        return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
    }

    /**
     * Resizes the UniGUI HSV controls while keeping their native layout and pointer handling.
     */
    private static void compactColorPicker(Popup popup) {
        if (!(popup.content() instanceof VBox panel)) {
            return;
        }
        panel.applyQueuedMutations();
        // The picker panel contains the RGB row, HEX row, HSV controls and alternate ARGB panel.
        if (panel.children().size() != 4 || !(panel.children().get(2) instanceof VBox hsv)) {
            return;
        }
        hsv.applyQueuedMutations();
        if (hsv.children().isEmpty() || !(hsv.children().getFirst() instanceof HBox colors)) {
            return;
        }
        colors.applyQueuedMutations();
        if (colors.children().size() != 3 || !(colors.children().get(0) instanceof WidgetBase plane)
                || !(colors.children().get(1) instanceof WidgetBase hue)
                || !(colors.children().get(2) instanceof VBox samples)) {
            return;
        }
        int planeSize = 112;
        int sampleWidth = 56;
        panel.layout(style -> style.width(192));
        plane.layout(style -> style.size(planeSize, planeSize));
        hue.layout(style -> style.size(12, planeSize));
        samples.layout(style -> style.size(sampleWidth, planeSize));
        samples.applyQueuedMutations();
        for (var sample : samples.children()) {
            if (sample instanceof Button swatch) {
                swatch.layout(style -> style.size(sampleWidth, 32));
            } else if (sample instanceof TextWidget caption) {
                caption.layout(style -> style.width(sampleWidth));
            }
        }
    }

    static String parameterLabel(String key) {
        return tr("editor." + key) + " " + key.replace('_', ' ');
    }

    /**
     * @param key    button's localization suffix
     * @param width  control width in GUI units
     * @param action callback for a click
     * @return inventory-style button
     */
    static AeButton control(String key, int width, Runnable action) {
        var button = new AeButton(tr("editor." + key));
        button.layout(style -> style.size(width, 18).flexNone());
        button.onClick(event -> action.run());
        return button;
    }

    static String formatDecimal(float value) {
        return value == (int) value ? Integer.toString((int) value) : Float.toString(value);
    }
}
