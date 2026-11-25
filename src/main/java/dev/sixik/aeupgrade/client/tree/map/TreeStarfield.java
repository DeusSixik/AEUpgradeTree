package dev.sixik.aeupgrade.client.tree.map;

import java.util.List;

import com.mojang.blaze3d.platform.NativeImage;
import dev.sixik.unigui.api.math.ColorView;
import dev.sixik.unigui.api.math.MutableRect;
import dev.sixik.unigui.api.render.DrawCommand;
import dev.sixik.unigui.api.render.DrawScope;
import dev.sixik.unigui.api.render.TextureHandle;
import dev.sixik.unigui.api.render.shaders.ShaderDrawOptions;
import dev.sixik.unigui.api.render.shaders.ShaderHandle;
import dev.sixik.unigui.api.render.shaders.ShaderUniforms;
import dev.sixik.unigui.widgets.world.WorldCanvas;
import net.minecraft.client.renderer.texture.DynamicTexture;
import org.jetbrains.annotations.Nullable;

/**
 * Reveals the same map-anchored sky wherever an unlocked node casts its light.
 */
final class TreeStarfield implements AutoCloseable {
    /**
     * Shader that animates the visible stars over the baked light map.
     */
    private static final ShaderHandle SHADER = ShaderHandle.resource("aeupgrade:tree_starfield");
    /**
     * Blend the sky over the board before drawing the links and nodes.
     */
    private static final ShaderDrawOptions OPTIONS = ShaderDrawOptions.defaults().blend(true).squareVertexOffset(0);
    /**
     * Maximum reach of the light beyond the node's outline in map units.
     */
    private static final float LIGHT_REACH = 64;

    /**
     * Texture owned by this screen, created on its first render.
     */
    private @Nullable LightTexture texture;
    /**
     * World-space origin of the texture.
     */
    private int mapX;
    private int mapY;

    /**
     * @param x        node's horizontal position in world units
     * @param y        node's vertical position in world units
     * @param diameter node's diameter at the canvas reference zoom
     * @param color    node accent
     * @return emitter whose glow extends beyond the node's outline
     */
    static TreeLightMap.Light light(float x, float y, float diameter, ColorView color) {
        return new TreeLightMap.Light(x, y, LIGHT_REACH + diameter / (2 * TreeCanvas.REFERENCE_ZOOM),
                color);
    }

    /**
     * Rebuilds the texture when the confirmed purchase set changes. Must run on the render thread.
     *
     * @param lights core and unlocked nodes in draw order
     */
    void rebuild(List<TreeLightMap.Light> lights) {
        var map = TreeLightMap.bake(lights);
        var image = new NativeImage(map.width(), map.height(), false);
        for (int row = 0; row < map.height(); row++) {
            for (int col = 0; col < map.width(); col++) {
                image.setPixelRGBA(col, row, map.pixels()[row * map.width() + col]);
            }
        }
        var next = new DynamicTexture(image);
        next.setFilter(true, false);
        close();
        texture = new LightTexture(next, next.getId(), map.width(), map.height());
        mapX = map.x();
        mapY = map.y();
    }

    /**
     * Draws the visible portion of the light map with a single animated shader quad.
     *
     * @param world viewport transform and visible layout bounds
     * @param draw  active canvas drawing scope
     */
    void draw(WorldCanvas world, DrawScope draw) {
        if (texture == null) {
            return;
        }
        var bounds = world.layoutBounds();
        float width = texture.width() * TreeLightMap.STEP;
        float height = texture.height() * TreeLightMap.STEP;
        float left = Math.max(bounds.x() + 1, world.worldToRootX(mapX));
        float top = Math.max(bounds.y() + 1, world.worldToRootY(mapY));
        float right = Math.min(bounds.right() - 1, world.worldToRootX(mapX + width));
        float bottom = Math.min(bounds.bottom() - 1, world.worldToRootY(mapY + height));
        if (left >= right || top >= bottom) {
            return;
        }
        var uniforms = ShaderUniforms.create()
                .setVec4("WorldRect", world.rootToWorldX(left), world.rootToWorldY(top),
                        world.rootToWorldX(right), world.rootToWorldY(bottom))
                .setVec4("Map", mapX, mapY, 1 / width, 1 / height)
                .setVec2("DrawParams", world.viewport().zoom(), draw.context().opacityMultiplier());
        var command = DrawCommand.shader(SHADER, new MutableRect(left, top, right - left, bottom - top), uniforms)
                .shaderOptions(OPTIONS).texture(texture);
        draw.context().submitOwned(command);
    }

    /**
     * Releases both the GPU texture and its native image when the tree screen is removed.
     */
    @Override
    public void close() {
        if (texture != null) {
            texture.image().close();
            texture = null;
        }
    }

    /**
     * Passes the GL texture directly because UniGUI's shader path only binds integer handles to the sampler.
     *
     * @param image  dynamic texture owning the OpenGL object and the native pixels
     * @param glId   cached handle consumed by the Minecraft shader backend
     * @param width  texture width in texels
     * @param height texture height in texels
     */
    private record LightTexture(DynamicTexture image, Integer glId, int width, int height) implements TextureHandle {
        @Override
        public String id() {
            return "aeupgrade:dynamic/tree_light_map";
        }

        @Override
        public Integer nativeHandle() {
            return glId;
        }
    }
}
