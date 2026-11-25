package dev.sixik.aeupgrade.client.tree.editor;

import static dev.sixik.aeupgrade.client.tree.UpgradeTreeText.tr;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.label;
import static dev.sixik.aeupgrade.client.tree.editor.InspectorWidgets.parameterLabel;

import java.util.List;
import java.util.function.Supplier;

import dev.sixik.aeupgrade.client.tree.editor.InspectorGroup.Parameter;
import dev.sixik.aeupgrade.client.tree.map.TreeBoard;
import dev.sixik.aeupgrade.client.widget.AeToggleButton;
import dev.sixik.unigui.api.event.EventPhase;
import dev.sixik.unigui.api.event.PointerEnteredEvent;
import dev.sixik.unigui.api.event.PointerExitedEvent;
import dev.sixik.unigui.api.layout.Align;
import dev.sixik.unigui.api.math.ColorView;
import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.text.RichText;
import dev.sixik.unigui.api.widget.Widget;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftFonts;
import dev.sixik.unigui.impl.widget.WidgetBase;
import dev.sixik.unigui.widgets.containers.HBox;
import dev.sixik.unigui.widgets.feedback.OverlayLayer;
import dev.sixik.unigui.widgets.feedback.Tooltip;
import dev.sixik.unigui.widgets.interaction.ComboBox;
import dev.sixik.unigui.widgets.interaction.TextField;
import org.jetbrains.annotations.Nullable;

/**
 * Shows the hovered parameter's description above scrollable forms.
 */
final class InspectorHelp {
    /**
     * AE2 selection color for shortcut section headings.
     */
    private static final ColorView SHORTCUT_HEADING = MutableColor.fromHex("#ACE9FF");

    private final Tooltip tooltip = TreeBoard.styleTooltip(new Tooltip() {
        @Override
        public boolean showing() {
            if (!super.showing()) {
                return false;
            }
            // Removing a row can detach its hovered field before the next pointer event.
            for (Widget widget = anchor(); widget != null; widget = widget.parent()) {
                if (widget == parent()) {
                    return true;
                }
            }
            return false;
        }
    });
    /**
     * Content provider for the current hover target, or null when there is none.
     */
    private @Nullable Supplier<RichText> hoveredText;

    InspectorHelp(OverlayLayer overlay) {
        overlay.addOverlay(tooltip);
    }

    void attach(Widget widget, String key) {
        attach(widget, () -> description(key));
    }

    void attach(Widget widget, Supplier<String> text) {
        attachRichText(widget, () -> RichText.of(text.get(), MinecraftFonts.defaultFace(), 8));
    }

    /**
     * Shows the grouped map shortcuts when the help icon is hovered.
     *
     * @param widget help icon in the editor toolbar
     */
    void attachShortcuts(Widget widget) {
        attachRichText(widget, InspectorHelp::shortcutsText);
    }

    /**
     * Binds formatted tooltip content to a hover target.
     *
     * @param widget widget that anchors the tooltip
     * @param text provider called on hover or when the tooltip is refreshed
     */
    private void attachRichText(Widget widget, Supplier<RichText> text) {
        widget.on(PointerEnteredEvent.TYPE, event -> {
            if (event.phase() == EventPhase.TARGET) {
                hoveredText = text;
                tooltip.anchor(widget);
                refresh();
            }
        });
        widget.on(PointerExitedEvent.TYPE, event -> {
            if (event.phase() == EventPhase.TARGET && tooltip.anchor() == widget) {
                tooltip.anchor(null);
                hoveredText = null;
            }
        });
    }

    void refresh() {
        if (hoveredText != null) {
            tooltip.richText(hoveredText.get());
        }
    }

    /**
     * @return localized shortcut sections with accented headings and space between groups
     */
    private static RichText shortcutsText() {
        var font = MinecraftFonts.defaultFace();
        var text = RichText.builder();
        var sections = List.of("map", "objects", "clipboard", "editing");
        for (int i = 0; i < sections.size(); i++) {
            if (i > 0) {
                text.append(RichText.of("\n\n", font, 8));
            }
            String key = "shortcuts." + sections.get(i);
            text.append(RichText.of(description(key + ".title") + "\n", font, 9, SHORTCUT_HEADING));
            text.append(RichText.of(description(key), font, 8));
        }
        return text.build();
    }

    HBox row(String key, WidgetBase input) {
        return row(key, key, input);
    }

    HBox row(String captionKey, String helpKey, WidgetBase input) {
        var row = new HBox();
        row.spacing(4);
        row.layout(style -> style.height(18).flexNone().alignItems(Align.CENTER));
        var caption = label(tr("editor." + captionKey), 8).clipOverflow();
        caption.layout(style -> style.widthPercent(40).minWidth(54).flexNone());
        attach(caption, helpKey);
        if (input instanceof ComboBox combo) {
            attach(combo.headerButton(), helpKey);
        } else if (input instanceof TextField) {
            attach(input, helpKey);
        }
        input.layout(style -> style.expand().minWidth(0).height(18));
        row.addChild(caption);
        row.addChild(input);
        return row;
    }

    Parameter parameter(String key, WidgetBase input) {
        return new Parameter(row(key, input), () -> parameterLabel(key));
    }

    Parameter toggle(String key, AeToggleButton toggle) {
        attach(toggle, key);
        return new Parameter(toggle, () -> parameterLabel(key));
    }

    static String description(String key) {
        return tr("editor.help." + key);
    }
}
