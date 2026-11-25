package dev.sixik.aeupgrade.client.widget;

import appeng.client.gui.widgets.Scrollbar;
import dev.sixik.unigui.api.core.InvalidationFlags;
import dev.sixik.unigui.api.event.EventPhase;
import dev.sixik.unigui.api.event.PointerMovedEvent;
import dev.sixik.unigui.api.event.PointerPressedEvent;
import dev.sixik.unigui.api.event.PointerReleasedEvent;
import dev.sixik.unigui.api.input.PointerButton;
import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.render.DrawScope;
import dev.sixik.unigui.api.render.Paint;
import dev.sixik.unigui.api.render.TextureHandle;
import dev.sixik.unigui.api.render.TexturePlacement;
import dev.sixik.unigui.widgets.containers.ScrollView;
import dev.sixik.unigui.widgets.interaction.ScrollBar;

/**
 * Uses AE2's fixed-size Network Status thumb with UniGUI's scrolling.
 */
public final class AeScrollbar {
    private static final float TRACK_WIDTH = 6;
    private static final float ATLAS_SIZE = 256;
    private static final Paint WHITE = Paint.fill(MutableColor.fromHex("#FFFFFF"));
    private static final Paint ACTIVE = Paint.fill(MutableColor.fromHex("#CDEFFF"));
    private final ScrollBar bar;
    private final TextureHandle thumb;
    private int capturedPointer = -1;
    private float grabOffset;

    /**
     * Applies the AE2 track and fixed-size thumb to a view's vertical scrollbar.
     */
    public static void install(ScrollView scroll) {
        scroll.overlayScrollBars(false).scrollbarSize(Math.max(TRACK_WIDTH, Sprites.BIG.width())).scrollbarGap(4);
        new AeScrollbar(scroll.verticalScrollBar(), Sprites.BIG);
    }

    /**
     * Uses the compact AE2 thumb in a virtual list's reserved scrollbar lane.
     */
    public static void installCompact(ScrollBar bar) {
        new AeScrollbar(bar, Sprites.SMALL);
    }

    private AeScrollbar(ScrollBar bar, TextureHandle thumb) {
        this.bar = bar;
        this.thumb = thumb;
        bar.renderer((draw, widget) -> renderBar(draw));
        bar.on(PointerPressedEvent.TYPE, this::press);
        bar.on(PointerMovedEvent.TYPE, this::drag);
        bar.on(PointerReleasedEvent.TYPE, this::release);
    }

    private void renderBar(DrawScope draw) {
        var bounds = bar.layoutBounds();
        float height = bounds.height();
        if (height <= 5 || bar.max() <= bar.min()) {
            return;
        }
        float trackX = bounds.x() + (bounds.width() - TRACK_WIDTH) / 2;
        drawTrack(draw, trackX, bounds.y(), 4, 38, 4);
        drawTrack(draw, trackX, bounds.y() + 4, height - 5, 42, 1);
        drawTrack(draw, trackX, bounds.bottom() - 1, 1, 117, 1);
        float length = Math.min(height, thumb.height());
        float offset = thumbOffset(height - length);
        float thumbWidth = Math.min(bounds.width(), thumb.width());
        draw.texture(thumb, bounds.x() + (bounds.width() - thumbWidth) / 2, bounds.y() + offset, thumbWidth, length,
                capturedPointer >= 0 ? ACTIVE : WHITE);
    }

    private void drawTrack(DrawScope draw, float x, float y, float height, int sourceY, int sourceHeight) {
        var placement = new TexturePlacement(x, y, TRACK_WIDTH, height,
                178 / ATLAS_SIZE, sourceY / ATLAS_SIZE, TRACK_WIDTH / ATLAS_SIZE, sourceHeight / ATLAS_SIZE);
        draw.texture(Sprites.TRACK, placement, 1, WHITE);
    }

    private void press(PointerPressedEvent event) {
        if (event.phase() != EventPhase.TARGET || event.button() != PointerButton.PRIMARY || bar.max() <= bar.min()) {
            return;
        }
        var bounds = bar.layoutBounds();
        int thumbHeight = thumb.height();
        float travel = bounds.height() - thumbHeight;
        if (travel > 0) {
            float y = event.rootY() - bounds.y();
            float inside = y - thumbOffset(travel);
            boolean grabbed = inside >= 0 && inside <= thumbHeight;
            grabOffset = grabbed ? inside : thumbHeight / 2f;
            capturedPointer = event.pointerId();
            bar.uiContext().capturePointer(capturedPointer, bar);
            if (!grabbed) {
                moveThumb(event.rootY());
            }
            bar.invalidate(InvalidationFlags.VISUAL);
        }
        event.cancel();
    }

    private void drag(PointerMovedEvent event) {
        if (capturedPointer >= 0 && event.pointerId() == capturedPointer) {
            moveThumb(event.rootY());
            event.cancel();
        }
    }

    private void release(PointerReleasedEvent event) {
        if (capturedPointer >= 0 && event.pointerId() == capturedPointer && event.button() == PointerButton.PRIMARY) {
            moveThumb(event.rootY());
            bar.uiContext().releasePointer(capturedPointer, bar);
            capturedPointer = -1;
            bar.invalidate(InvalidationFlags.VISUAL);
            event.cancel();
        }
    }

    private float thumbOffset(float travel) {
        return travel * ((bar.value() - bar.min()) / (bar.max() - bar.min()));
    }

    private void moveThumb(float rootY) {
        var bounds = bar.layoutBounds();
        float travel = bounds.height() - thumb.height();
        if (travel > 0) {
            float fraction = dragFraction(rootY - bounds.y(), grabOffset, travel);
            bar.value(bar.min() + fraction * (bar.max() - bar.min()));
        }
    }

    /**
     * Maps the grabbed point on the fixed thumb to the full scroll range.
     */
    static float dragFraction(float pointerY, float grabOffset, float travel) {
        return travel > 0 ? Math.clamp((pointerY - grabOffset) / travel, 0, 1) : 0;
    }

    /**
     * Loads client resources when the scrollbar is installed.
     */
    private static final class Sprites {
        private static final TextureHandle TRACK = AeStyle.aeTexture("guis/networkstatus.png", 256, 256);
        private static final TextureHandle BIG = thumb(Scrollbar.BIG);
        private static final TextureHandle SMALL = thumb(Scrollbar.SMALL);

        private static TextureHandle thumb(Scrollbar.Style style) {
            return AeStyle.aeTexture("gui/sprites/" + style.enabledSprite().getPath() + ".png",
                    style.handleWidth(), style.handleHeight());
        }
    }
}
