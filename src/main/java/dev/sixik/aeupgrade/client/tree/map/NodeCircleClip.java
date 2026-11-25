package dev.sixik.aeupgrade.client.tree.map;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import dev.sixik.unigui.api.math.ColorView;
import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.math.Transform;
import dev.sixik.unigui.api.render.DrawMesh;
import dev.sixik.unigui.api.render.DrawPoint;
import dev.sixik.unigui.api.render.DrawVertex;
import dev.sixik.unigui.api.render.RenderContext;
import dev.sixik.unigui.api.render.TextureHandle;
import org.jetbrains.annotations.Nullable;

/**
 * Shared circle geometry for a textured node's frame, fill and background layers.
 */
final class NodeCircleClip {
    private static final int SEGMENTS = 48;
    private static final List<DrawPoint> OUTLINE = createOutline();
    /**
     * Meshes for replaced node colors can be collected with their owning nodes.
     */
    private static final Map<ColorView, DrawMesh> MESHES = new WeakHashMap<>();

    private NodeCircleClip() {
    }

    /**
     * The boundary is centered at (0.5, 0.5) and has radius 0.5 in both position and texture coordinates.
     */
    static List<DrawPoint> outline() {
        return OUTLINE;
    }

    /**
     * Places the same unit mesh in the requested circle and applies the current UI opacity.
     */
    static void draw(RenderContext context, @Nullable TextureHandle texture, ColorView tint, Transform placement,
                     float x, float y, float radius) {
        placement.position().set(x - radius, y - radius);
        placement.scale().set(2 * radius, 2 * radius);
        float opacity = context.opacityMultiplier();
        var mesh = opacity == 1 ? MESHES.computeIfAbsent(tint, NodeCircleClip::mesh)
                : mesh(new MutableColor(tint.r(), tint.g(), tint.b(), tint.a() * opacity));
        context.addMesh(mesh, texture, placement);
    }

    private static List<DrawPoint> createOutline() {
        var points = new ArrayList<DrawPoint>(SEGMENTS);
        for (int segment = 0; segment < SEGMENTS; segment++) {
            double angle = 2 * Math.PI * segment / SEGMENTS;
            points.add(new DrawPoint((float) (0.5 + 0.5 * Math.cos(angle)), (float) (0.5 + 0.5 * Math.sin(angle))));
        }
        return List.copyOf(points);
    }

    private static DrawMesh mesh(ColorView tint) {
        var outline = outline();
        var rim = outline.stream().map(point -> new DrawVertex(point.x(), point.y(), point.x(), point.y(), tint)).toList();
        var center = new DrawVertex(0.5f, 0.5f, 0.5f, 0.5f, tint);
        var triangles = new ArrayList<DrawVertex>(3 * rim.size());
        for (int index = 0; index < rim.size(); index++) {
            triangles.add(center);
            triangles.add(rim.get(index));
            triangles.add(rim.get((index + 1) % rim.size()));
        }
        return DrawMesh.triangles(triangles);
    }
}
