package dev.sixik.aeupgrade.client.tree.map;

import java.io.IOException;
import java.nio.FloatBuffer;
import java.nio.charset.StandardCharsets;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import dev.sixik.aeupgrade.AEUpgradeTree;
import dev.sixik.unigui.api.math.ColorView;
import dev.sixik.unigui.api.render.DrawScope;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftGuiRenderBackend;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL32;
import org.slf4j.Logger;

/**
 * Draws the visible link effects in one batch while the UniGUI canvas clip is active.
 */
final class TreeLinkBatch implements AutoCloseable {
    private static final Logger LOGGER = LogUtils.getLogger();
    /**
     * Endpoints, RGBA tints, path distances, wall state, join slopes and tint fractions.
     */
    private static final int FLOATS_PER_LINK = 19;
    private static final int STRIDE = FLOATS_PER_LINK * Float.BYTES;

    private final FloatBuffer links;
    private final Matrix4f projection = new Matrix4f();
    private final float[] matrix = new float[16];
    private int program;
    private int vertexArray;
    private int buffer;
    private int projectionUniform;
    private int styleUniform;
    private int pulseUniform;
    private boolean failed;

    /**
     * @param segmentCount number of line segments in this screen's graph
     */
    TreeLinkBatch(int segmentCount) {
        links = BufferUtils.createFloatBuffer(segmentCount * FLOATS_PER_LINK);
    }

    /**
     * @return whether this renderer can draw effects or still needs to load its shader
     */
    boolean available() {
        return !failed;
    }

    void begin() {
        links.clear();
    }

    /**
     * Adds the approach to a breaker or a fully unlocked link. The geometry shader expands it into a
     * narrow quad along the link, so diagonal links shade only the area around the actual stroke.
     *
     * @param x1            source X in GUI units
     * @param y1            source Y in GUI units
     * @param x2            destination X in GUI units
     * @param y2            destination Y in GUI units
     * @param source        source node tint
     * @param target        destination node tint
     * @param startDistance phase position at the start, in map units
     * @param endDistance   phase position at the end, in map units
     * @param wall          branch blocker at the end, if present
     * @param startJoin     shared cut at the start, or a round end cap
     * @param endJoin       shared cut at the end, or a round end cap
     * @param startTint     progress of the color transition at the start
     * @param endTint       progress of the color transition at the end
     */
    void add(float x1, float y1, float x2, float y2, ColorView source, ColorView target,
             float startDistance, float endDistance, TreeLinkEffects.Wall wall,
             float startJoin, float endJoin, float startTint, float endTint) {
        if (x1 == x2 && y1 == y2) {
            return;
        }
        links.put(x1).put(y1).put(x2).put(y2);
        putColor(source);
        putColor(target);
        links.put(startDistance).put(endDistance).put(wall.ordinal());
        links.put(startJoin).put(endJoin).put(startTint).put(endTint);
    }

    private void putColor(ColorView color) {
        links.put(color.r()).put(color.g()).put(color.b()).put(color.a());
    }

    /**
     * @param draw  drawing scope inside the canvas clip
     * @param scale graph scale, relative to reference zoom
     */
    void submit(DrawScope draw, float scale) {
        if (links.position() == 0) {
            return;
        }
        links.flip();
        float opacity = draw.context().opacityMultiplier();
        draw.addCallback(backend -> {
            if (backend instanceof MinecraftGuiRenderBackend minecraft) {
                render(minecraft, scale, opacity);
            }
        });
    }

    private void render(MinecraftGuiRenderBackend backend, float scale, float opacity) {
        int previousProgram = GL32.glGetInteger(GL32.GL_CURRENT_PROGRAM);
        int previousVertexArray = GL32.glGetInteger(GL32.GL_VERTEX_ARRAY_BINDING);
        int previousBuffer = GL32.glGetInteger(GL32.GL_ARRAY_BUFFER_BINDING);
        try {
            if (program == 0) {
                create();
            }
            // UniGUI restores blend, depth and cull state after the callback.
            RenderSystem.enableBlend();
            RenderSystem.blendFuncSeparate(GL32.GL_SRC_ALPHA, GL32.GL_ONE_MINUS_SRC_ALPHA,
                    GL32.GL_ONE, GL32.GL_ONE_MINUS_SRC_ALPHA);
            RenderSystem.disableDepthTest();
            RenderSystem.depthMask(false);
            RenderSystem.disableCull();

            projection.set(RenderSystem.getProjectionMatrix()).mul(RenderSystem.getModelViewMatrix())
                    .mul(backend.graphics().pose().last().pose());
            GL32.glUseProgram(program);
            GL32.glUniformMatrix4fv(projectionUniform, false, projection.get(matrix));
            GL32.glUniform3f(styleUniform, Math.clamp(scale, 0.52f, 2.1f), opacity, Math.max(0.75f, 1.5f * scale) / 2);
            GL32.glUniform2f(pulseUniform, TreeLinkEffects.pulsePosition(), TreeLinkEffects.PULSE_SPACING);
            GL32.glBindVertexArray(vertexArray);
            GL32.glBindBuffer(GL32.GL_ARRAY_BUFFER, buffer);
            GL32.glBufferData(GL32.GL_ARRAY_BUFFER, links, GL32.GL_STREAM_DRAW);
            GL32.glDrawArrays(GL32.GL_POINTS, 0, links.remaining() / FLOATS_PER_LINK);
        } catch (IOException error) {
            failed = true;
            close();
            LOGGER.error("Could not load the ME upgrade link shader", error);
        } finally {
            GL32.glUseProgram(previousProgram);
            GL32.glBindVertexArray(previousVertexArray);
            GL32.glBindBuffer(GL32.GL_ARRAY_BUFFER, previousBuffer);
        }
    }

    private void create() throws IOException {
        program = GL32.glCreateProgram();
        attachShader(GL32.GL_VERTEX_SHADER, "vsh");
        attachShader(GL32.GL_GEOMETRY_SHADER, "gsh");
        attachShader(GL32.GL_FRAGMENT_SHADER, "fsh");
        GL32.glBindAttribLocation(program, 0, "Edge");
        GL32.glBindAttribLocation(program, 1, "FromColor");
        GL32.glBindAttribLocation(program, 2, "ToColor");
        GL32.glBindAttribLocation(program, 3, "LinkFlow");
        GL32.glBindAttribLocation(program, 4, "JoinTint");
        GL32.glLinkProgram(program);
        if (GL32.glGetProgrami(program, GL32.GL_LINK_STATUS) == GL32.GL_FALSE) {
            throw new IOException(GL32.glGetProgramInfoLog(program));
        }
        projectionUniform = GL32.glGetUniformLocation(program, "Projection");
        styleUniform = GL32.glGetUniformLocation(program, "Style");
        pulseUniform = GL32.glGetUniformLocation(program, "Pulse");
        vertexArray = GL32.glGenVertexArrays();
        buffer = GL32.glGenBuffers();
        GL32.glBindVertexArray(vertexArray);
        GL32.glBindBuffer(GL32.GL_ARRAY_BUFFER, buffer);
        attribute(0, 4, 0);
        attribute(1, 4, 4);
        attribute(2, 4, 8);
        attribute(3, 3, 12);
        attribute(4, 4, 15);
    }

    private void attachShader(int type, String extension) throws IOException {
        var id = ResourceLocation.fromNamespaceAndPath(AEUpgradeTree.MODID, "shaders/tree_current." + extension);
        try (var stream = Minecraft.getInstance().getResourceManager().open(id)) {
            int shader = GL32.glCreateShader(type);
            try {
                GL32.glShaderSource(shader, new String(stream.readAllBytes(), StandardCharsets.UTF_8));
                GL32.glCompileShader(shader);
                if (GL32.glGetShaderi(shader, GL32.GL_COMPILE_STATUS) == GL32.GL_FALSE) {
                    throw new IOException(id + ": " + GL32.glGetShaderInfoLog(shader));
                }
                GL32.glAttachShader(program, shader);
            } finally {
                GL32.glDeleteShader(shader);
            }
        }
    }

    private static void attribute(int index, int size, int offset) {
        GL32.glEnableVertexAttribArray(index);
        GL32.glVertexAttribPointer(index, size, GL32.GL_FLOAT, false, STRIDE, (long) offset * Float.BYTES);
    }

    @Override
    public void close() {
        GL32.glDeleteProgram(program);
        GL32.glDeleteVertexArrays(vertexArray);
        GL32.glDeleteBuffers(buffer);
        program = vertexArray = buffer = 0;
    }
}
