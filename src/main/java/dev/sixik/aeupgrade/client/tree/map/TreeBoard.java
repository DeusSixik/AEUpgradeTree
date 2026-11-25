package dev.sixik.aeupgrade.client.tree.map;

import dev.sixik.unigui.api.math.ColorView;
import dev.sixik.unigui.api.math.MutableColor;
import dev.sixik.unigui.api.math.RectView;
import dev.sixik.unigui.api.render.DrawScope;
import dev.sixik.unigui.api.widget.Widget;
import dev.sixik.unigui.widgets.feedback.Tooltip;
import dev.sixik.unigui.widgets.world.WorldCanvas;

/**
 * Frame, grid and hover cards for the tree maps.
 */
public final class TreeBoard {
    /**
     * Shadow on the top and left sides of AE2's recessed panels.
     */
    private static final ColorView INSET_SHADOW = MutableColor.fromHex("#626982");
    /**
     * Light on the bottom and right sides of AE2's recessed panels.
     */
    static final ColorView INSET_LIGHT = MutableColor.fromHex("#F5F5F8");
    /**
     * Recessed background of the icon map.
     */
    private static final ColorView MAP_BACKGROUND = MutableColor.fromHex("#1C2131");
    /**
     * Subtle dots on the map's circuit board.
     */
    private static final ColorView MAP_DOT = MutableColor.fromHex("#323A54");
    /**
     * Color of inactive traces and node rings.
     */
    private static final ColorView MAP_TRACE = MutableColor.fromHex("#515C7C");

    private TreeBoard() {
    }

    /**
     * @param world canvas transform and visible bounds
     * @param draw  drawing scope for the AE2 circuit-board background
     */
    public static void drawBoard(WorldCanvas world, DrawScope draw) {
        var bounds = world.layoutBounds();
        drawBoardFrame(bounds, draw);
        float scale = world.viewport().zoom() / TreeCanvas.REFERENCE_ZOOM;
        int pitch = TreeCanvas.GRID_STEP * Math.max(1, (int) Math.ceil(9f / (TreeCanvas.GRID_STEP * world.viewport().zoom())));
        int firstX = (int) Math.ceil(world.rootToWorldX(bounds.x()) / pitch) * pitch;
        int firstY = (int) Math.ceil(world.rootToWorldY(bounds.y()) / pitch) * pitch;
        float lastX = world.rootToWorldX(bounds.x() + bounds.width());
        float lastY = world.rootToWorldY(bounds.y() + bounds.height());
        for (int x = firstX; x <= lastX; x += pitch) {
            float px = world.worldToRootX(x);
            for (int y = firstY; y <= lastY; y += pitch) {
                draw.rectFilled(px, world.worldToRootY(y), Math.max(0.5f, scale), Math.max(0.5f, scale), MAP_DOT);
            }
        }
    }

    static void drawBoardFrame(RectView bounds, DrawScope draw) {
        draw.rectFilled(bounds.x(), bounds.y(), bounds.width(), bounds.height(), INSET_LIGHT);
        draw.rectFilled(bounds.x(), bounds.y(), bounds.width() - 1, bounds.height() - 1, INSET_SHADOW);
        draw.rectFilled(bounds.x() + 1, bounds.y() + 1, bounds.width() - 2, bounds.height() - 2, MAP_BACKGROUND);
    }

    public static boolean visibleLink(RectView bounds, float x1, float y1, float x2, float y2) {
        return Math.max(x1, x2) >= bounds.x() && Math.min(x1, x2) <= bounds.right()
                && Math.max(y1, y2) >= bounds.y() && Math.min(y1, y2) <= bounds.bottom();
    }

    /**
     * Applies the same tooltip appearance on both maps.
     */
    public static Tooltip mapTooltip(Widget node) {
        return styleTooltip(new Tooltip(node, ""));
    }

    /**
     * @param tooltip tooltip used by the tree map or its editor
     * @return tooltip with the tree's palette and placement
     */
    public static Tooltip styleTooltip(Tooltip tooltip) {
        tooltip.followPointer(true).offset(10, 12).maxWidth(220);
        tooltip.background(MAP_BACKGROUND).border(MAP_TRACE).borderWidth(1).radius(2);
        return tooltip;
    }
}
