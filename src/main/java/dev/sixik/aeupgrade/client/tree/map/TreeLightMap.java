package dev.sixik.aeupgrade.client.tree.map;

import java.util.List;

import dev.sixik.unigui.api.math.ColorView;

/**
 * A map-space raster of the light cast by unlocked nodes. RGB is premultiplied by the combined light strength
 * so bilinear filtering fades the light at its edges without darkening its tint.
 *
 * @param x      world coordinate of the left texture edge
 * @param y      world coordinate of the top texture edge
 * @param width  width in texels
 * @param height height in texels
 * @param pixels premultiplied colors in NativeImage's ABGR format, in row-major order
 */
record TreeLightMap(int x, int y, int width, int height, int[] pixels) {
    /**
     * World units represented by one texel.
     */
    static final int STEP = 8;
    /**
     * Fraction of each radius that receives the full light strength.
     */
    private static final float INNER_RADIUS = 0.16f;

    /**
     * @param x      center in world units
     * @param y      center in world units
     * @param radius outer reach in world units
     * @param color  tint of the node
     */
    record Light(float x, float y, float radius, ColorView color) {
    }

    /**
     * Composites the lights in draw order, including a transparent border for filtering.
     *
     * @param lights ordered emitters, including the core
     * @return light map covering the emitters
     */
    static TreeLightMap bake(List<Light> lights) {
        float left = Float.POSITIVE_INFINITY;
        float top = Float.POSITIVE_INFINITY;
        float right = Float.NEGATIVE_INFINITY;
        float bottom = Float.NEGATIVE_INFINITY;
        for (var light : lights) {
            left = Math.min(left, light.x() - light.radius());
            top = Math.min(top, light.y() - light.radius());
            right = Math.max(right, light.x() + light.radius());
            bottom = Math.max(bottom, light.y() + light.radius());
        }
        int x = (lowerCell(left) - 1) * STEP;
        int y = (lowerCell(top) - 1) * STEP;
        int width = upperCell(right) + 1 - x / STEP;
        int height = upperCell(bottom) + 1 - y / STEP;
        var map = new TreeLightMap(x, y, width, height, new int[width * height]);
        for (var light : lights) {
            map.composite(light);
        }
        return map;
    }

    /**
     * Samples the radial falloff at each affected texel center and composites it over existing light.
     * The normalized radius ramps from 0 to 1 between the inner and outer radius. The polynomial
     * t²(3 − 2t) gives a smooth fade with zero slope at both endpoints.
     *
     * @param light emitter to composite
     */
    private void composite(Light light) {
        int left = lowerCell(light.x() - light.radius()) - x / STEP;
        int top = lowerCell(light.y() - light.radius()) - y / STEP;
        int right = upperCell(light.x() + light.radius()) - x / STEP;
        int bottom = upperCell(light.y() + light.radius()) - y / STEP;
        float red = light.color().r() * 255;
        float green = light.color().g() * 255;
        float blue = light.color().b() * 255;
        float inner = light.radius() * INNER_RADIUS;
        float inverseFadeWidth = 1 / (light.radius() - inner);
        float radiusSquared = light.radius() * light.radius();
        for (int row = top; row < bottom; row++) {
            float dy = y + (row + 0.5f) * STEP - light.y();
            int index = row * width + left;
            for (int col = left; col < right; col++, index++) {
                float dx = x + (col + 0.5f) * STEP - light.x();
                float distanceSquared = dx * dx + dy * dy;
                if (distanceSquared >= radiusSquared) {
                    continue;
                }
                float t = Math.max(0, ((float) Math.sqrt(distanceSquared) - inner) * inverseFadeWidth);
                float fade = t * t * (3 - 2 * t);
                float strength = 1 - fade;
                int old = pixels[index];
                pixels[index] = compositeChannel(strength * 255, fade, old, 24)
                        | compositeChannel(strength * blue, fade, old, 16)
                        | compositeChannel(strength * green, fade, old, 8)
                        | compositeChannel(strength * red, fade, old, 0);
            }
        }
    }

    /**
     * @param source   premultiplied contribution from the current light, in byte units
     * @param fade     fraction of the existing light still visible
     * @param previous previously packed texel
     * @param shift    bit offset of the channel in the ABGR pixel
     * @return blended channel shifted into its position in the pixel
     */
    private static int compositeChannel(float source, float fade, int previous, int shift) {
        return Math.round(source + fade * ((previous >>> shift) & 0xFF)) << shift;
    }

    /**
     * @param coordinate world coordinate
     * @return first cell that may contain a sample at or above the coordinate
     */
    private static int lowerCell(float coordinate) {
        return (int) Math.floor(coordinate / STEP);
    }

    /**
     * @param coordinate world coordinate
     * @return exclusive upper cell bound
     */
    private static int upperCell(float coordinate) {
        return (int) Math.ceil(coordinate / STEP);
    }
}
