package dev.sixik.aeupgrade.client.widget;

import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.render.DrawScope;
import dev.sixik.unigui.api.render.Paint;
import dev.sixik.unigui.api.render.RenderContext;
import dev.sixik.unigui.api.render.TextureHandle;
import dev.sixik.unigui.api.render.TexturePlacement;
import dev.sixik.unigui.api.style.StyleKeys;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftFonts;
import dev.sixik.unigui.widgets.interaction.TextField;

/**
 * Renders the ME Terminal field texture and uses UniGUI for text editing.
 */
public final class AeTextField extends TextField {
    private static final int ATLAS_SIZE = 128;
    private static final int SPRITE_HEIGHT = 12;
    private static final int FIELD_HEIGHT = SPRITE_HEIGHT + 4;
    private static final Paint WHITE = Paint.fill(MutableColor.fromHex("#FFFFFF"));
    private static final TextureHandle TEXTURE = AeStyle.aeTexture("guis/text_field.png", ATLAS_SIZE, ATLAS_SIZE);

    public AeTextField(int maxLength) {
        maxLength(maxLength);
        textPaddingX(3);
        font(MinecraftFonts.defaultFace(), 9);
        selectionColor().set(MutableColor.fromHex("#000080"));
        style(style -> style.put(StyleKeys.BACKGROUND_VISIBLE, false)
                .put(StyleKeys.BORDER_VISIBLE, false)
                .put(StyleKeys.FOCUS_RING_COLOR, MutableColor.rgba255(0, 0, 0, 0))
                .put(StyleKeys.TEXT_COLOR, MutableColor.fromHex("#FFFFFF"))
                .put(StyleKeys.PLACEHOLDER_COLOR, MutableColor.fromHex("#DEDFE3"))
                .put(StyleKeys.CARET_COLOR, MutableColor.fromHex("#FFFFFF")));
    }

    @Override
    protected void renderTextInput(RenderContext context) {
        var bounds = layoutBounds();
        if (bounds.width() > 2 && bounds.height() > 0) {
            int row = !enabled() ? 1 : focused() ? 2 : 0;
            float height = Math.min(bounds.height(), FIELD_HEIGHT);
            float y = Math.round(bounds.y() + (bounds.height() - height) / 2);
            var draw = new DrawScope(context, transform(), bounds);
            drawSlice(draw, bounds.x(), y, 1, height, 0, 1, row);
            drawSlice(draw, bounds.x() + 1, y, bounds.width() - 2, height, 1, ATLAS_SIZE - 2, row);
            drawSlice(draw, bounds.right() - 1, y, 1, height, ATLAS_SIZE - 1, 1, row);
        }
        super.renderTextInput(context);
    }

    private static void drawSlice(DrawScope draw, float x, float y, float width, float height, int sourceX, int sourceWidth,
                                  int row) {
        var region = new TexturePlacement(x, y, width, height,
                (float) sourceX / ATLAS_SIZE, (float) (row * SPRITE_HEIGHT) / ATLAS_SIZE,
                (float) sourceWidth / ATLAS_SIZE, (float) SPRITE_HEIGHT / ATLAS_SIZE);
        draw.texture(TEXTURE, region, 1, WHITE);
    }
}
