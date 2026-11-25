package dev.sixik.aeupgrade.client.tree.map;

import java.util.List;
import java.util.Map;

import dev.sixik.aeupgrade.tree.UpgradeNode;
import dev.sixik.aeupgrade.tree.UpgradePlan.Availability;
import dev.sixik.unigui.api.math.ColorView;
import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.render.DrawScope;
import dev.sixik.unigui.widgets.world.WorldCanvas;

/**
 * Coordinates the fading links and expanding rings of nodes that can be purchased.
 */
final class TreeAvailabilityPulse {
    /**
     * Duration of a full cycle in milliseconds, including the pause after the rings fade.
     */
    private static final int PERIOD = 2400;
    /**
     * Milliseconds spent brightening an incoming link.
     */
    private static final int RISE = 360;
    /**
     * Milliseconds spent dimming an incoming link.
     */
    private static final int FALL = 500;
    /**
     * Phase in milliseconds when the link finishes dimming and the first ring starts.
     */
    private static final int RIPPLE_START = 1050;
    /**
     * Delay of the second ring in milliseconds.
     */
    static final int RIPPLE_SPACING = 190;
    /**
     * Lifetime of either ring in milliseconds.
     */
    private static final int RIPPLE_DURATION = 920;
    /**
     * The color snapshot is copied into each UniGUI draw command.
     */
    private final MutableColor tint = new MutableColor();
    /**
     * Monotonic clock origin in nanoseconds for this map view.
     */
    private final long startNanos = System.nanoTime();

    /**
     * @return the current cycle phase in milliseconds
     */
    int phase() {
        return (int) ((System.nanoTime() - startNanos) / 1_000_000 % PERIOD);
    }

    /**
     * Selects powered incoming links for the available-node animation.
     *
     * @param source source status, with the core treated as unlocked
     * @param target destination status
     * @return whether the link should fade in sync with the destination's rings
     */
    static boolean pulsesLink(Availability source, Availability target) {
        return source == Availability.UNLOCKED && target == Availability.AVAILABLE;
    }

    /**
     * Fades from the dim trace to the node's full color and back.
     * The ramp is {@code t = clamp(min(phase / RISE, (RIPPLE_START - phase) / FALL), 0, 1)}.
     * Easing with {@code t² * (3 - 2t)} gives both ends of the ramp zero slope.
     *
     * @param phase elapsed milliseconds in the current cycle
     * @return the opacity of the accent over the link backing, between 0.38 and 1
     */
    static float linkOpacity(int phase) {
        float t = Math.clamp(Math.min((float) phase / RISE, (float) (RIPPLE_START - phase) / FALL), 0, 1);
        return 0.38f + 0.62f * t * t * (3 - 2 * t);
    }

    /**
     * Computes linear expansion from the end of the line's fade, offset for each ring.
     *
     * @param phase elapsed milliseconds in the current cycle
     * @param delay milliseconds of delay after the first ring starts
     * @return fraction of the ring lifetime in [0, 1], clamped at either end
     */
    static float rippleProgress(int phase, int delay) {
        return Math.clamp((float) (phase - RIPPLE_START - delay) / RIPPLE_DURATION, 0, 1);
    }

    /**
     * Eases a ring in over the first tenth of its lifetime and fades it as it expands.
     * For normalized lifetime {@code t}, the envelope is {@code 0.8 * min(10t, 1) * (1 - t)²}.
     *
     * @param progress normalized ring lifetime in [0, 1]
     * @return opacity of the ring before applying the node color's alpha
     */
    static float rippleOpacity(float progress) {
        float remaining = 1 - progress;
        return 0.8f * Math.min(progress * 10, 1) * remaining * remaining;
    }

    /**
     * Composites the accent onto the opaque link backing. Each channel is
     * {@code backing + (accent - backing) * opacity * accent.a}, so joins stay uniform.
     *
     * @param backing opaque background of the link
     * @param accent  destination node color
     * @param opacity animated opacity in [0, 1]
     * @return temporary color to copy into the draw commands before reusing the tint
     */
    ColorView linkColor(ColorView backing, ColorView accent, float opacity) {
        float alpha = accent.a() * opacity;
        return tint.set(
                backing.r() + (accent.r() - backing.r()) * alpha,
                backing.g() + (accent.g() - backing.g()) * alpha,
                backing.b() + (accent.b() - backing.b()) * alpha, 1);
    }

    /**
     * Draws both rings behind the available nodes using the same phase as the links.
     *
     * @param world     tree canvas with its current camera transform
     * @param draw      active draw scope
     * @param available visible nodes that can be purchased
     * @param buttons   map buttons providing each node's size and color
     * @param scale     zoom relative to the reference zoom
     * @param phase     elapsed milliseconds in the current cycle
     */
    void drawRings(WorldCanvas world, DrawScope draw, List<UpgradeNode> available, Map<UpgradeNode, TreeNodeButton> buttons,
                   float scale, int phase) {
        float first = rippleProgress(phase, 0);
        float second = rippleProgress(phase, RIPPLE_SPACING);
        float firstOpacity = rippleOpacity(first);
        float secondOpacity = rippleOpacity(second) * 0.75f;
        if (scale < 0.12f || firstOpacity == 0 && secondOpacity == 0) {
            return;
        }
        float stroke = Math.max(0.65f, 1.2f * scale);
        var bounds = world.layoutBounds();
        for (int i = 0; i < available.size(); i++) {
            var node = available.get(i);
            var button = buttons.get(node);
            float x = world.worldToRootX(node.x());
            float y = world.worldToRootY(node.y());
            float baseRadius = button.diameter() * scale / 2;
            // The wave travels 22 reference pixels, starting two pixels beyond the node.
            float outerRadius = baseRadius + 24 * scale + stroke;
            if (x + outerRadius < bounds.x() || x - outerRadius > bounds.right()
                    || y + outerRadius < bounds.y() || y - outerRadius > bounds.bottom()) {
                continue;
            }
            var color = button.linkColor();
            if (firstOpacity > 0) {
                drawRing(draw, x, y, baseRadius + (2 + 22 * first) * scale, stroke, color, firstOpacity);
            }
            if (secondOpacity > 0) {
                drawRing(draw, x, y, baseRadius + (2 + 22 * second) * scale, stroke, color, secondOpacity);
            }
        }
    }

    /**
     * Draws one circular wave in the node's accent color.
     *
     * @param draw    active draw scope
     * @param x       center X in root UI pixels
     * @param y       center Y in root UI pixels
     * @param radius  ring radius in UI pixels
     * @param stroke  outline thickness in UI pixels
     * @param color   node accent
     * @param opacity ring opacity in [0, 1]
     */
    private void drawRing(DrawScope draw, float x, float y, float radius, float stroke, ColorView color, float opacity) {
        tint.set(color.r(), color.g(), color.b(), color.a() * opacity);
        draw.circleStroke(x - radius, y - radius, 2 * radius, 2 * radius, tint, stroke);
    }
}
