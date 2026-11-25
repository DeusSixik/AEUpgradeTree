package dev.sixik.aeupgrade.client.widget;

import appeng.client.gui.Icon;
import dev.sixik.unigui.api.render.DrawScope;
import dev.sixik.unigui.api.render.RenderContext;
import dev.sixik.unigui.api.render.TextureOptions;
import dev.sixik.unigui.api.render.TextureRegion;
import dev.sixik.unigui.api.text.RichText;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftFonts;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftTextureHandle;
import dev.sixik.unigui.widgets.interaction.Button;

/**
 * Clickable preview surface drawn with AE2's native inventory slot.
 */
public abstract class AeSlot extends Button {
    /**
     * Width and height of the native slot in GUI pixels.
     */
    public static final int SIZE = Icon.SLOT_BACKGROUND.width;

    /**
     * Source region in the AE2 state atlas.
     */
    private static final TextureRegion SLOT = TextureRegion.ofPixels(
            new MinecraftTextureHandle(Icon.TEXTURE, Icon.TEXTURE_WIDTH, Icon.TEXTURE_HEIGHT, TextureOptions.nearest()),
            Icon.SLOT_BACKGROUND.x, Icon.SLOT_BACKGROUND.y, SIZE, SIZE);

    /**
     * @param label backing button label for the preview
     */
    protected AeSlot(String label) {
        super(RichText.of(label, MinecraftFonts.defaultFace(), 9));
        interactionTransitions(false).textPadding(0, 0).textColor(AeStyle.TEXT_COLOR);
        layout(style -> style.size(SIZE, SIZE).flexNone());
    }

    /**
     * @param context render context for the AE2 slot texture
     */
    @Override
    protected void renderSurface(RenderContext context) {
        var bounds = layoutBounds();
        var draw = new DrawScope(context, transform(), bounds);
        draw.texture(SLOT, bounds.x(), bounds.y(), bounds.width(), bounds.height(), AeStyle.UNTINTED);
    }
}
