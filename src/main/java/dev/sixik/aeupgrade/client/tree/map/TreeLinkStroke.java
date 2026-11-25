package dev.sixik.aeupgrade.client.tree.map;

import java.util.List;

import dev.sixik.unigui.api.math.ColorView;
import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.render.DrawScope;
import dev.sixik.unigui.api.render.DrawVertex;

/**
 * Draws graph edges in the destination node's color.
 */
public final class TreeLinkStroke {
    private static final ColorView POWERED_BREAKER = MutableColor.rgba(1, 0.55f, 0.22f, 0.74f);
    private static final ColorView UNPOWERED_BREAKER = MutableColor.rgba(1, 0.55f, 0.22f, 0.50f);

    private TreeLinkStroke() {
    }

    public static void draw(DrawScope draw, float x1, float y1, float x2, float y2, ColorView source, ColorView target,
                            float width, float opacity) {
        if (x1 == x2 && y1 == y2) {
            return;
        }
        // TODO Re-enable the gradient once UniGUI supports SDF rendering for it.
        /*
        if (!sameColor(source, target)) {
            float alpha = opacity * draw.context().opacityMultiplier();
            draw.addMesh(dev.sixik.unigui.api.render.DrawMesh.triangles(
                    vertices(x1, y1, x2, y2, faded(source, alpha), faded(target, alpha), width)), null);
            return;
        }
        */
        draw.addLine(x1, y1, x2, y2, faded(target, opacity), width);
    }

    /**
     * Marks the closed branch with a static stroke perpendicular to the link.
     *
     * @param draw    active canvas drawing scope
     * @param x       horizontal wall position in GUI units
     * @param y       vertical wall position in GUI units
     * @param dx      horizontal displacement along the nonzero link
     * @param dy      vertical displacement along the nonzero link
     * @param scale   node scale at the current zoom
     * @param powered whether the source node is open
     */
    static void drawBreaker(DrawScope draw, float x, float y, float dx, float dy, float scale, boolean powered) {
        float size = Math.clamp(scale, 0.52f, 2.1f);
        float halfHeightPerUnit = 6 * size / (float) Math.hypot(dx, dy);
        float offsetX = -dy * halfHeightPerUnit;
        float offsetY = dx * halfHeightPerUnit;
        draw.addLine(x - offsetX, y - offsetY, x + offsetX, y + offsetY,
                powered ? POWERED_BREAKER : UNPOWERED_BREAKER, size);
    }

    /**
     * Offsets the endpoints by half the stroke width along the perpendicular to the edge.
     */
    static List<DrawVertex> vertices(float x1, float y1, float x2, float y2, ColorView source, ColorView target, float width) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float length = (float) Math.hypot(dx, dy);
        if (length == 0) {
            return List.of();
        }
        float scale = width / (2 * length);
        float offsetX = -dy * scale;
        float offsetY = dx * scale;
        var a = new DrawVertex(x1 + offsetX, y1 + offsetY, source);
        var b = new DrawVertex(x2 + offsetX, y2 + offsetY, target);
        var c = new DrawVertex(x2 - offsetX, y2 - offsetY, target);
        var d = new DrawVertex(x1 - offsetX, y1 - offsetY, source);
        return List.of(a, b, c, a, c, d);
    }

    private static ColorView faded(ColorView color, float opacity) {
        return opacity == 1 ? color : new MutableColor(color.r(), color.g(), color.b(), color.a() * opacity);
    }

    /*
    private static boolean sameColor(ColorView a, ColorView b) {
        return a.r() == b.r() && a.g() == b.g() && a.b() == b.b() && a.a() == b.a();
    }

    private static ColorView interpolate(ColorView a, ColorView b, float t) {
        return new MutableColor(a.r() + (b.r() - a.r()) * t, a.g() + (b.g() - a.g()) * t,
                a.b() + (b.b() - a.b()) * t, a.a() + (b.a() - a.a()) * t);
    }
    */
}
