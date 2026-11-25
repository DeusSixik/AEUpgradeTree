package dev.sixik.aeupgrade.client.widget;

import dev.sixik.unigui.api.layout.EdgeInsets;
import dev.sixik.unigui.api.math.ColorView;
import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.render.ImageFit;
import dev.sixik.unigui.api.render.NineSlice;
import dev.sixik.unigui.api.render.Paint;
import dev.sixik.unigui.api.render.TextureOptions;
import dev.sixik.unigui.api.style.StyleKeys;
import dev.sixik.unigui.api.style.WidgetState;
import dev.sixik.unigui.api.text.RichText;
import dev.sixik.unigui.api.widget.visual.BackgroundKind;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftFonts;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftTextureHandle;
import dev.sixik.unigui.widgets.display.Text;
import dev.sixik.unigui.widgets.display.TextWidget;
import dev.sixik.unigui.widgets.feedback.Popup;
import dev.sixik.unigui.widgets.interaction.Button;
import dev.sixik.unigui.widgets.interaction.ComboBox;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;

/**
 * AE2 textures, palette and styling for UniGUI controls.
 */
public final class AeStyle {
    private AeStyle() {
    }

    /**
     * AE2's default text color from its screen palette.
     */
    public static final ColorView TEXT_COLOR = MutableColor.fromHex("#413F54");
    /**
     * Color of readable labels on AE2's dark disabled sprite.
     */
    static final ColorView DISABLED_TEXT = MutableColor.fromHex("#F0F1F8");
    /**
     * AE2 inventory panel with three-pixel frame borders.
     */
    public static final MinecraftTextureHandle PANEL = aeTexture("guis/background.png", 256, 256);
    /**
     * Native AE2 button sprite at rest.
     */
    static final NineSlice BUTTON = aeButtonSprite("button");
    /**
     * Native AE2 button sprite for hover.
     */
    static final NineSlice BUTTON_HIGHLIGHTED = aeButtonSprite("button_highlighted");
    /**
     * Native AE2 button sprite for disabled actions.
     */
    static final NineSlice BUTTON_DISABLED = aeButtonSprite("button_disabled");
    /**
     * Untinted draw paint for the button textures.
     */
    static final Paint UNTINTED = Paint.fill(MutableColor.rgba255(255, 255, 255, 255));

    /**
     * Wraps a texture from the installed AE2 resource pack with pixel sampling.
     *
     * @param path   path relative to {@code assets/ae2/textures}
     * @param width  source width in pixels
     * @param height source height in pixels
     * @return handle into the current resource pack
     */
    static MinecraftTextureHandle aeTexture(String path, int width, int height) {
        return new MinecraftTextureHandle(ResourceLocation.fromNamespaceAndPath("ae2", "textures/" + path),
                width, height, TextureOptions.nearest());
    }

    /**
     * Applies AE2's three-pixel nine-slice border to a button sprite.
     *
     * @param name sprite filename without the extension
     * @return resizable native AE2 button art
     */
    private static NineSlice aeButtonSprite(String name) {
        return NineSlice.of(aeTexture("gui/sprites/" + name + ".png", 200, 20), 3);
    }

    /**
     * Creates a single-line label using the active Minecraft font.
     *
     * @param value displayed label
     * @param size  font size in UI pixels
     * @param color label color
     * @return styled label
     */
    public static TextWidget text(String value, float size, ColorView color) {
        return new Text(value).font(MinecraftFonts.defaultFace(), size).color(color);
    }

    /**
     * Updates a dynamic label with the same font metadata for measurement and rendering.
     *
     * @param label label whose contents change
     * @param value text to display
     * @param size  font size in UI pixels
     */
    public static void setLabel(TextWidget label, String value, float size) {
        label.richText(RichText.of(value, MinecraftFonts.defaultFace(), size));
    }

    /**
     * Skins the selector and its popup without changing UniGUI's selection and scrolling behavior.
     */
    public static void styleComboBox(ComboBox combo) {
        combo.optionRowHeight(19);
        var header = combo.headerButton();
        styleComboButton(header);
        header.layout(style -> style.height(18).minHeight(0).flexNone());

        var panel = combo.dropDownPopup();
        panel.padding(new EdgeInsets(0, 0, 0, 4));
        stylePopupPanel(panel);
        combo.optionsHost().style(style -> style.put(StyleKeys.BACKGROUND_VISIBLE, false)
                .put(StyleKeys.BORDER_VISIBLE, false));
        AeScrollbar.install(combo.optionsScroll());
        styleComboBoxOptions(combo);
    }

    /**
     * Restyles the option buttons after the inspector replaces their labels.
     */
    public static void styleComboBoxOptions(ComboBox combo) {
        for (int index = 0; index < combo.itemCount(); index++) {
            styleComboButton(combo.optionButton(index));
        }
        var font = Minecraft.getInstance().font;
        int labelWidth = combo.items().stream().mapToInt(font::width).max().orElse(0);
        boolean scrollable = combo.maxVisibleOptions() > 0 && combo.itemCount() > combo.maxVisibleOptions();
        var scroll = combo.optionsScroll();
        float scrollbarWidth = scrollable ? scroll.scrollbarSize() + scroll.scrollbarGap() : 0;
        // Reserve space for the panel frame, list inset and button text padding on both sides.
        combo.dropDownWidth(labelWidth + 24 + scrollbarWidth);
    }

    public static void stylePopupPanel(Popup panel) {
        panel.backgroundTexture(PANEL).backgroundTextureFit(ImageFit.NINE_SLICE)
                .backgroundVisible(true).sliceBorders(3).radius(0);
        panel.style(style -> style.put(StyleKeys.BACKGROUND_VISIBLE, true)
                .put(StyleKeys.BACKGROUND_COLOR, MutableColor.rgba255(0, 0, 0, 0))
                .put(StyleKeys.BORDER_VISIBLE, false));
    }

    public static void styleComboButton(Button button) {
        var transparent = MutableColor.rgba255(0, 0, 0, 0);
        button.interactionTransitions(false).textPadding(5, 2).textColor(TEXT_COLOR)
                .hoverBackground(transparent).pressedBackground(transparent);
        button.backgroundVisible(true).radius(0).borderVisible(false).sliceBorders(3);
        button.style(style -> style.put(StyleKeys.BACKGROUND_KIND, BackgroundKind.TEXTURE)
                .put(StyleKeys.BACKGROUND_COLOR, transparent)
                .put(StyleKeys.BACKGROUND_TEXTURE, BUTTON.texture())
                .put(StyleKeys.BACKGROUND_TEXTURE, WidgetState.HOVERED, BUTTON_HIGHLIGHTED.texture())
                .put(StyleKeys.BACKGROUND_TEXTURE, WidgetState.PRESSED, BUTTON_HIGHLIGHTED.texture())
                .put(StyleKeys.BACKGROUND_TEXTURE, WidgetState.CHECKED, BUTTON_HIGHLIGHTED.texture())
                .put(StyleKeys.BACKGROUND_TEXTURE, WidgetState.DISABLED, BUTTON_DISABLED.texture())
                .put(StyleKeys.TEXT_SIZE, 9f));
    }
}
