package dev.sixik.aeupgrade.client.widget;

import static dev.sixik.aeupgrade.client.widget.AeStyle.BUTTON;
import static dev.sixik.aeupgrade.client.widget.AeStyle.BUTTON_DISABLED;
import static dev.sixik.aeupgrade.client.widget.AeStyle.BUTTON_HIGHLIGHTED;
import static dev.sixik.aeupgrade.client.widget.AeStyle.DISABLED_TEXT;
import static dev.sixik.aeupgrade.client.widget.AeStyle.TEXT_COLOR;
import static dev.sixik.aeupgrade.client.widget.AeStyle.UNTINTED;

import dev.sixik.unigui.api.math.ColorView;
import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.render.DrawScope;
import dev.sixik.unigui.api.render.RenderContext;
import dev.sixik.unigui.api.text.RichText;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftFonts;
import dev.sixik.unigui.widgets.interaction.Button;
import net.minecraft.client.Minecraft;

/**
 * Draws the toolbar using AE2's native button sprites.
 */
public class AeButton extends Button {
    private static final ColorView FOCUS_COLOR = MutableColor.fromHex("#ACE9FF");

    /**
     * Creates a fixed-size AE2 toolbar button.
     *
     * @param name initial localized label
     */
    public AeButton(String name) {
        interactionTransitions(false).focusRingVisible(false).textPadding(4, 2).textColor(TEXT_COLOR);
        label(name);
    }

    /**
     * Updates the label using the Minecraft pixel font.
     *
     * @param name localized label
     */
    public void label(String name) {
        richText(RichText.of(name, MinecraftFonts.defaultFace(), 9));
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AeButton enabled(boolean enabled) {
        super.enabled(enabled);
        textColor(enabled ? TEXT_COLOR : DISABLED_TEXT);
        return this;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void renderSurface(RenderContext context) {
        var bounds = layoutBounds();
        var draw = new DrawScope(context, null);
        var sprite = !enabled() ? BUTTON_DISABLED : hovered() || pressed() ? BUTTON_HIGHLIGHTED : BUTTON;
        draw.nineSlice(sprite, bounds.x(), bounds.y(), bounds.width(), bounds.height(), UNTINTED);
        if (uiContext() != null && uiContext().focusManager().isFocused(this)) {
            draw.rectStroke(bounds.x(), bounds.y(), bounds.width(), bounds.height(), FOCUS_COLOR, 1);
        }
    }
}
