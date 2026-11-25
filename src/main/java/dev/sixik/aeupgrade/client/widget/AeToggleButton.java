package dev.sixik.aeupgrade.client.widget;

import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.render.DrawScope;
import dev.sixik.unigui.api.render.Paint;
import dev.sixik.unigui.api.render.RenderContext;
import dev.sixik.unigui.api.render.TextureHandle;
import dev.sixik.unigui.api.render.TexturePlacement;
import dev.sixik.unigui.api.text.RichText;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftFonts;
import dev.sixik.unigui.widgets.interaction.ToggleButton;

/**
 * UniGUI toggle drawn with the switch sprites from AE2's terminal settings.
 */
public final class AeToggleButton extends ToggleButton {
    private static final int ATLAS_SIZE = 64;
    private static final int SWITCH_WIDTH = 22;
    private static final int SWITCH_HEIGHT = 12;
    private static final int LABEL_OFFSET = SWITCH_WIDTH + 4;
    private static final int FONT_SIZE = 9;
    private static final Paint WHITE = Paint.fill(MutableColor.fromHex("#FFFFFF"));
    private static final Paint MUTED = Paint.fill(MutableColor.rgba255(255, 255, 255, 128));
    private static final MutableColor INK = MutableColor.fromHex("#404040");
    private static final MutableColor MUTED_INK = MutableColor.fromHex("#888888");
    private static final TextureHandle TEXTURE = AeStyle.aeTexture("guis/checkbox.png", ATLAS_SIZE, ATLAS_SIZE);

    public AeToggleButton(String label) {
        super(RichText.of(label, MinecraftFonts.defaultFace(), FONT_SIZE));
        interactionTransitions(false).focusRingVisible(false);
        layout(style -> style.height(18).flexNone());
    }

    @Override
    protected void renderSurface(RenderContext context) {
        var bounds = layoutBounds();
        var draw = new DrawScope(context, transform(), bounds);
        boolean focused = uiContext() != null && uiContext().focusManager().isFocused(this);
        int spriteX = enabled() && (hovered() || focused) ? SWITCH_WIDTH : 0;
        int spriteY = checked() ? 40 : 28;
        float y = Math.round(bounds.y() + (bounds.height() - SWITCH_HEIGHT) / 2);
        var placement = new TexturePlacement(Math.round(bounds.x()), y, SWITCH_WIDTH, SWITCH_HEIGHT,
                spriteX / (float) ATLAS_SIZE, spriteY / (float) ATLAS_SIZE,
                SWITCH_WIDTH / (float) ATLAS_SIZE, SWITCH_HEIGHT / (float) ATLAS_SIZE);
        draw.texture(TEXTURE, placement, 1, enabled() ? WHITE : MUTED);
    }

    @Override
    protected void renderButtonText(DrawScope draw) {
        var bounds = layoutBounds();
        float x = bounds.x() + LABEL_OFFSET;
        float width = bounds.width() - LABEL_OFFSET;
        float y = Math.round(bounds.y() + (bounds.height() - FONT_SIZE) / 2);
        renderLabel(draw, richText(), x, bounds.y(), width, bounds.height(), x, y, width, FONT_SIZE,
                enabled() ? INK : MUTED_INK);
    }
}
