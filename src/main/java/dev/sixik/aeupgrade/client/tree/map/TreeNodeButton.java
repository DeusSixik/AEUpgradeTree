package dev.sixik.aeupgrade.client.tree.map;

import static dev.sixik.aeupgrade.client.tree.UpgradeTreeText.entryName;
import static dev.sixik.aeupgrade.client.tree.map.TreeBoard.INSET_LIGHT;
import static dev.sixik.aeupgrade.client.tree.map.TreeNodeAssets.MAP_PENALTY;
import static dev.sixik.aeupgrade.client.tree.map.TreeNodeAssets.SELECTION;
import static dev.sixik.aeupgrade.client.tree.map.TreeNodeAssets.entryIcon;
import static dev.sixik.aeupgrade.client.tree.map.TreeNodeAssets.nodeTexture;
import static dev.sixik.aeupgrade.client.tree.map.TreeNodeAssets.resolveBorderColor;

import java.util.List;
import java.util.Objects;

import dev.sixik.aeupgrade.tree.UpgradeCatalog;
import dev.sixik.aeupgrade.tree.UpgradeNodeStyle;
import dev.sixik.aeupgrade.tree.UpgradeNodeStyle.Shape;
import dev.sixik.aeupgrade.tree.UpgradePlan.Availability;
import dev.sixik.unigui.api.core.InvalidationFlags;
import dev.sixik.unigui.api.event.Event;
import dev.sixik.unigui.api.event.PointerReleasedEvent;
import dev.sixik.unigui.api.input.PointerButton;
import dev.sixik.unigui.api.math.ColorView;
import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.math.Transform;
import dev.sixik.unigui.api.render.DrawPoint;
import dev.sixik.unigui.api.render.DrawScope;
import dev.sixik.unigui.api.render.RenderContext;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftGuiRenderBackend;
import dev.sixik.unigui.backend.minecraft_impl.MinecraftTextureHandle;
import dev.sixik.unigui.widgets.interaction.Button;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * An item and state ring whose size follows its canvas anchor.
 */
public final class TreeNodeButton extends Button {
    /**
     * Fill of a node available for purchase.
     */
    private static final ColorView MAP_SLOT = MutableColor.fromHex("#2B3449");
    /**
     * Fill of an unavailable node.
     */
    private static final ColorView MAP_INACTIVE = MutableColor.fromHex("#202635");
    private static final ColorView MAP_ACTIVE = MutableColor.fromHex("#3C5770");
    private static final ColorView MAP_AVAILABLE_TINT = MutableColor.fromHex("#ABB1BE");
    private static final ColorView MAP_INACTIVE_TINT = MutableColor.fromHex("#535B70");

    /**
     * AE2 item model drawn in the node.
     */
    private final ItemStack icon;
    /**
     * Effective color for the ring and its links.
     */
    private final ColorView accent;
    private final ColorView availableAccent;
    private final ColorView inactiveAccent;
    private final UpgradeNodeStyle appearance;
    private final @Nullable MinecraftTextureHandle iconTexture;
    private final List<MinecraftTextureHandle> backgrounds;
    /**
     * Current purchase status of the node.
     */
    private Availability status = Availability.AVAILABLE;
    /**
     * True for the node selected in the footer.
     */
    private boolean selected;
    private boolean editorPreview;
    /**
     * True during a button click emitted as part of a primary pointer release.
     */
    private boolean pointerClick;

    /**
     * Creates a named item node.
     *
     * @param name     localized node name
     * @param icon     AE2 item drawn inside the ring
     * @param accent   branch color
     * @param diameter anchor diameter at the reference zoom in UI pixels
     */
    public TreeNodeButton(String name, ItemStack icon, ColorView accent, float diameter) {
        this(name, icon, accent, new UpgradeNodeStyle(diameter, Shape.CIRCLE, null, List.of()));
    }

    public TreeNodeButton(UpgradeCatalog.Entry entry) {
        this(entry, entry.appearance());
    }

    private TreeNodeButton(UpgradeCatalog.Entry entry, UpgradeNodeStyle appearance) {
        this(entryName(entry), entryIcon(entry), resolveBorderColor(entry.node(), appearance), appearance);
    }

    private TreeNodeButton(String name, ItemStack icon, ColorView accent, UpgradeNodeStyle appearance) {
        super(name);
        this.icon = icon;
        this.accent = accent;
        this.availableAccent = shade(accent, 0.69f);
        this.inactiveAccent = shade(accent, 0.43f);
        this.appearance = appearance;
        this.iconTexture = nodeTexture(appearance.iconTexture());
        this.backgrounds = appearance.backgrounds().stream().map(TreeNodeAssets::nodeTexture)
                .filter(Objects::nonNull).toList();
        interactionTransitions(false).focusRingVisible(false);
    }

    @Override
    public void handle(Event event) {
        if (event instanceof PointerReleasedEvent release && release.button() == PointerButton.PRIMARY) {
            pointerClick = true;
            try {
                super.handle(event);
            } finally {
                pointerClick = false;
            }
        } else {
            super.handle(event);
        }
    }

    /**
     * Updates colors while allowing locked upgrades to be inspected.
     *
     * @param status   purchase status
     * @param selected true if this node is selected
     */
    public void appearance(Availability status, boolean selected) {
        this.status = status;
        this.selected = selected;
        invalidate(InvalidationFlags.VISUAL);
    }

    public void previewAppearance(boolean selected) {
        editorPreview = true;
        appearance(Availability.UNLOCKED, selected);
    }

    public ColorView linkColor() {
        return accent;
    }

    public boolean pointerClick() {
        return pointerClick;
    }

    public float diameter() {
        return appearance.size();
    }

    Availability status() {
        return status;
    }

    private static ColorView shade(ColorView color, float brightness) {
        return new MutableColor(color.r() * brightness, color.g() * brightness, color.b() * brightness, color.a());
    }

    private ColorView textureTint() {
        return switch (status) {
            case UNLOCKED -> INSET_LIGHT;
            case AVAILABLE -> MAP_AVAILABLE_TINT;
            default -> MAP_INACTIVE_TINT;
        };
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void renderSurface(RenderContext context) {
        var bounds = layoutBounds();
        var draw = new DrawScope(context, null);
        float x = bounds.x() + bounds.width() / 2;
        float y = bounds.y() + bounds.height() / 2;
        float radius = bounds.width() / 2;
        float scale = bounds.width() / appearance.size();
        boolean unlocked = status == Availability.UNLOCKED;
        boolean available = status == Availability.AVAILABLE;
        boolean focused = uiContext() != null && uiContext().focusManager().isFocused(this);
        boolean highlighted = selected || focused || unlocked;
        boolean hovered = hovered();
        var interior = unlocked ? MAP_ACTIVE : available ? MAP_SLOT : MAP_INACTIVE;
        var ring = unlocked ? accent : available ? availableAccent : inactiveAccent;
        var tint = textureTint();
        var shape = appearance.shape();
        float width = appearance.resolveBorderWidth(highlighted, hovered);
        float inset = shape == Shape.FRAMELESS ? 0 : Math.min(radius, width * scale);
        float innerRadius = radius - inset;
        if (shape == Shape.CIRCLE && !backgrounds.isEmpty()) {
            var placement = new Transform();
            if (inset > 0) {
                NodeCircleClip.draw(context, null, ring, placement, x, y, radius);
            }
            if (innerRadius > 0) {
                NodeCircleClip.draw(context, null, interior, placement, x, y, innerRadius);
                for (var texture : backgrounds) {
                    NodeCircleClip.draw(context, texture, tint, placement, x, y, innerRadius);
                }
            }
        } else {
            if (inset > 0) {
                fill(draw, x, y, radius, ring);
            }
            if (innerRadius > 0) {
                fill(draw, x, y, innerRadius, interior);
                drawBackgrounds(draw, x, y, innerRadius, tint);
            }
        }
        if (selected && (editorPreview || appearance.borderWidth() != null)) {
            draw.rectStroke(bounds.x() - 2, bounds.y() - 2, bounds.width() + 4, bounds.height() + 4,
                    SELECTION, Math.max(1, scale));
        } else if (!editorPreview && shape == Shape.FRAMELESS && (highlighted || hovered)) {
            draw.rectStroke(bounds.x(), bounds.y(), bounds.width(), bounds.height(), ring, Math.max(1, scale));
        }
    }

    private void drawBackgrounds(DrawScope draw, float x, float y, float radius, ColorView tint) {
        for (var texture : backgrounds) {
            if (appearance.shape() == Shape.DIAMOND) {
                // Each tip samples the midpoint of the matching edge of the original texture rectangle.
                draw.addTexturedQuad(texture, x, y - radius, x + radius, y, x, y + radius, x - radius, y,
                        0.5f, 0, 1, 0.5f, 0.5f, 1, 0, 0.5f, tint);
            } else {
                draw.textureTinted(texture, x - radius, y - radius, 2 * radius, 2 * radius, tint);
            }
        }
    }

    private void fill(DrawScope draw, float x, float y, float radius, ColorView color) {
        switch (appearance.shape()) {
            case CIRCLE -> draw.addCircleFilled(x, y, radius, color, 12);
            case SQUARE -> draw.rectFilled(x - radius, y - radius, 2 * radius, 2 * radius, color);
            case DIAMOND -> draw.addQuadFilled(new DrawPoint(x, y - radius), new DrawPoint(x + radius, y),
                    new DrawPoint(x, y + radius), new DrawPoint(x - radius, y), color);
            case FRAMELESS -> {
            }
        }
    }

    /**
     * Draws the item centered in the anchor, at 65 percent of its current diameter.
     *
     * @param draw active UniGUI draw scope
     */
    @Override
    protected void renderButtonText(DrawScope draw) {
        if (draw.context().backend() instanceof MinecraftGuiRenderBackend backend) {
            var bounds = layoutBounds();
            float size = bounds.width() * 0.65f;
            float x = bounds.x() + (bounds.width() - size) / 2;
            float y = bounds.y() + (bounds.height() - size) / 2;
            // Even a small GUI-space icon can cover several framebuffer pixels.
            if (size > 0) {
                var texture = iconTexture == null ? backend.cachedItemPreviewTexture(icon, 24) : iconTexture;
                if (texture != null) {
                    float width = size;
                    float height = size;
                    if (iconTexture != null) {
                        float ratio = (float) iconTexture.width() / iconTexture.height();
                        width *= Math.min(1, ratio);
                        height *= Math.min(1, 1 / ratio);
                    }
                    draw.textureTinted(texture, x + (size - width) / 2, y + (size - height) / 2,
                            width, height, textureTint());
                } else {
                    draw.addCallback(renderer -> backend.renderItemPreview(icon, x, y, size, size, false));
                }
            }
            if (status == Availability.EXCLUSIVE) {
                float inset = size * 0.24f;
                float stroke = Math.max(0.75f, bounds.width() * 0.06f);
                draw.addLine(x + inset, y + inset, x + size - inset, y + size - inset, MAP_PENALTY, stroke);
                draw.addLine(x + size - inset, y + inset, x + inset, y + size - inset, MAP_PENALTY, stroke);
            }
        }
    }
}
