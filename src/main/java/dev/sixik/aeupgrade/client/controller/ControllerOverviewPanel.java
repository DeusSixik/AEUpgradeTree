package dev.sixik.aeupgrade.client.controller;

import java.util.ArrayList;
import java.util.List;

import appeng.client.Point;
import appeng.client.gui.widgets.Scrollbar;
import com.mojang.blaze3d.platform.InputConstants;
import dev.sixik.aeupgrade.channel.DeviceChannelType;
import dev.sixik.aeupgrade.network.ControllerOverview.Limit;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Scrollable device quotas for the ME Controller screen.
 */
public final class ControllerOverviewPanel {
    /**
     * Translation prefix for controller information.
     */
    private static final String TEXT = "aeupgrade.controller.";
    /**
     * Maximum card width, in GUI pixels.
     */
    public static final int WIDTH = 102;
    /**
     * Space between the card and controller window, in GUI pixels.
     */
    public static final int GAP = 4;
    /**
     * Minimum width allowing an item, count and scrollbar, in GUI pixels.
     */
    private static final int MIN_WIDTH = 45;
    /**
     * Header height in GUI pixels.
     */
    private static final int HEADER_HEIGHT = 18;
    /**
     * Row height and distance between item icons, in GUI pixels.
     */
    private static final int ROW_HEIGHT = 20;
    /**
     * Maximum number of rows drawn before scrolling.
     */
    private static final int VISIBLE_ROWS = 5;
    /**
     * Size of the item slot with its one-pixel border, in GUI pixels.
     */
    private static final int SLOT_SIZE = 18;
    /**
     * AE2 text and over-limit colors.
     */
    private static final int INK = 0xff413f54;
    private static final int EXTRA = 0xff913748;

    /**
     * Native AE2 scrollbar with row indices as its scroll range.
     */
    private final Scrollbar scrollbar = new Scrollbar(Scrollbar.BIG);
    /**
     * Last list received from the menu.
     */
    private List<Limit> limits = List.of();
    /**
     * Display items and counts resolved from the last list.
     */
    private List<Row> rows = List.of();
    /**
     * Absolute card bounds in GUI pixels, or null when hidden.
     */
    private @Nullable Rect2i bounds;
    /**
     * Whether the scrollbar owns the current left mouse press.
     */
    private boolean scrollbarPressed;

    /**
     * Rebuilds the list when quotas change and lays out the native scrollbar beside it.
     *
     * @param limits finite quotas from the current menu snapshot
     * @param x      absolute left edge in GUI pixels
     * @param y      absolute top edge in GUI pixels
     * @param width  available card width in GUI pixels
     */
    public void update(List<Limit> limits, int x, int y, int width) {
        if (this.limits != limits) {
            this.limits = limits;
            var newRows = new ArrayList<Row>(limits.size());
            for (var limit : limits) {
                var type = DeviceChannelType.byId(limit.family());
                if (type != null) {
                    newRows.add(new Row(type.displayItem(), Component.literal(limit.installed() + "/" + limit.maximum()),
                            limit.installed() > limit.maximum() ? EXTRA : INK));
                }
            }
            rows = newRows;
            scrollbar.setRange(0, Math.max(0, rows.size() - VISIBLE_ROWS), 1);
        }
        if (rows.isEmpty() || width < MIN_WIDTH) {
            bounds = null;
            releaseScrollbar();
        } else {
            int height = HEADER_HEIGHT + Math.min(rows.size(), VISIBLE_ROWS) * ROW_HEIGHT + GAP;
            if (bounds == null || bounds.getX() != x || bounds.getY() != y
                    || bounds.getWidth() != width || bounds.getHeight() != height) {
                bounds = new Rect2i(x, y, width, height);
                scrollbar.setPosition(new Point(x + width - GAP - Scrollbar.BIG.handleWidth(), y + HEADER_HEIGHT + 1));
                scrollbar.setHeight(height - HEADER_HEIGHT - GAP - 2);
            }
            if (!hasOverflow()) {
                releaseScrollbar();
            }
        }
    }

    /**
     * @return current card bounds in absolute GUI pixels, or null when hidden
     */
    public @Nullable Rect2i bounds() {
        return bounds;
    }

    /**
     * Draws the visible slice of the quotas and the ordinary tooltip for a hovered item.
     *
     * @param graphics native screen draw context with absolute coordinates
     * @param font     Minecraft font
     * @param mouseX   horizontal pointer location in GUI pixels
     * @param mouseY   vertical pointer location in GUI pixels
     */
    public void draw(GuiGraphics graphics, Font font, int mouseX, int mouseY) {
        if (bounds == null) {
            return;
        }
        int x = bounds.getX();
        int y = bounds.getY();
        int right = x + bounds.getWidth();
        int bottom = y + bounds.getHeight();
        graphics.fill(x, y, right, bottom, 0xff626982);
        graphics.fill(x + 1, y + 1, right - 1, bottom - 2, 0xffc8c9d2);
        graphics.hLine(x + 1, right - 2, y + 1, 0xfff5f5f8);
        graphics.vLine(x + 1, y + 1, bottom - 2, 0xfff5f5f8);
        drawText(graphics, font, tr("limits"), x + 5, y + 5, bounds.getWidth() - 10, INK, false);

        var track = hasOverflow() ? scrollbar.getBounds() : null;
        int rowRight = track == null ? right - 3 : track.getX() - 3;
        int first = scrollbar.getCurrentScroll();
        int end = Math.min(first + VISIBLE_ROWS, rows.size());
        ItemStack hovered = ItemStack.EMPTY;
        for (int i = first; i < end; i++) {
            var row = rows.get(i);
            int rowY = y + HEADER_HEIGHT + (i - first) * ROW_HEIGHT;
            int slotX = x + 4;
            int slotY = rowY + 1;
            graphics.fill(x + 3, rowY, rowRight, rowY + ROW_HEIGHT - 1,
                    i % 2 == 0 ? 0xffb4b8ca : 0xffbdc0cd);
            graphics.fill(slotX, slotY, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0xff74788b);
            graphics.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE, slotY + SLOT_SIZE, 0xffe8e8ef);
            graphics.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, 0xff9a9fb4);
            graphics.renderItem(row.item, slotX + 1, slotY + 1);
            if (mouseX >= slotX && mouseX < slotX + SLOT_SIZE && mouseY >= slotY && mouseY < slotY + SLOT_SIZE) {
                graphics.fill(slotX + 1, slotY + 1, slotX + SLOT_SIZE - 1, slotY + SLOT_SIZE - 1, 0x50ffffff);
                hovered = row.item;
            }
            int valueX = slotX + SLOT_SIZE + 3;
            drawText(graphics, font, row.count, valueX, rowY + 6, rowRight - valueX - 3, row.color, true);
        }
        if (track != null) {
            int trackBottom = track.getY() + track.getHeight();
            graphics.fill(track.getX() - 1, track.getY() - 1, track.getX() + track.getWidth() + 1,
                    trackBottom + 1, 0xfff5f5f8);
            graphics.fill(track.getX(), track.getY(), track.getX() + track.getWidth(), trackBottom, 0xff8290a9);
            scrollbar.drawForegroundLayer(graphics, bounds, new Point(mouseX, mouseY));
        }
        if (!hovered.isEmpty()) {
            graphics.renderTooltip(font, hovered, mouseX, mouseY);
        }
    }

    /**
     * Scrolls the side list independently of the native machine table.
     *
     * @param x     horizontal pointer location in GUI pixels
     * @param y     vertical pointer location in GUI pixels
     * @param delta vertical wheel movement, positive toward earlier rows
     * @return true if the side panel handled the wheel
     */
    public boolean scroll(double x, double y, double delta) {
        if (bounds == null || !bounds.contains((int) x, (int) y) || delta == 0) {
            return false;
        }
        scrollbar.onMouseWheel(new Point((int) x, (int) y), Math.signum(delta));
        return true;
    }

    /**
     * Begins a native scrollbar interaction or absorbs a click on the visible card.
     *
     * @param x      horizontal pointer location in GUI pixels
     * @param y      vertical pointer location in GUI pixels
     * @param button mouse button index
     * @return true if the click was inside the card
     */
    public boolean mouseClicked(double x, double y, int button) {
        if (bounds == null || !bounds.contains((int) x, (int) y)) {
            return false;
        }
        if (button == InputConstants.MOUSE_BUTTON_LEFT && hasOverflow()
                && scrollbar.getBounds().contains((int) x, (int) y)) {
            scrollbarPressed = scrollbar.onMouseDown(new Point((int) x, (int) y), button);
        }
        return true;
    }

    /**
     * Forwards an active drag even after the pointer leaves the scrollbar.
     *
     * @param x      horizontal pointer location in GUI pixels
     * @param y      vertical pointer location in GUI pixels
     * @param button mouse button index
     * @return true if the side scrollbar owns the drag
     */
    public boolean mouseDragged(double x, double y, int button) {
        if (!scrollbarPressed || button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        scrollbar.onMouseDrag(new Point((int) x, (int) y), button);
        return true;
    }

    /**
     * Ends scrollbar capture when the button is released.
     *
     * @param button mouse button index
     * @return true if the side scrollbar owned the press
     */
    public boolean mouseReleased(int button) {
        return button == InputConstants.MOUSE_BUTTON_LEFT && releaseScrollbar();
    }

    /**
     * Ticks AE2's auto-repeat when the track is held.
     */
    public void tick() {
        if (scrollbarPressed) {
            scrollbar.tick();
        }
    }

    /**
     * @return whether the number of limits exceeds the visible rows
     */
    private boolean hasOverflow() {
        return rows.size() > VISIBLE_ROWS;
    }

    /**
     * @return whether a scrollbar press was ended
     */
    private boolean releaseScrollbar() {
        if (!scrollbarPressed) {
            return false;
        }
        scrollbarPressed = false;
        scrollbar.onMouseUp(new Point(0, 0), InputConstants.MOUSE_BUTTON_LEFT);
        return true;
    }

    /**
     * Fits a single row cell to its allotted width, keeping the values right-aligned.
     *
     * @param graphics current draw context
     * @param font     Minecraft font
     * @param text     cell content
     * @param x        cell left in GUI pixels
     * @param y        cell top in GUI pixels
     * @param width    maximum drawn width in GUI pixels
     * @param color    text color in ARGB
     * @param right    true for trailing alignment
     */
    private static void drawText(GuiGraphics graphics, Font font, Component text, int x, int y, int width, int color, boolean right) {
        int natural = font.width(text);
        if (natural == 0 || width <= 0) {
            return;
        }
        float scale = Math.min(1, (float) width / natural);
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(right ? x + width - natural * scale : x, y + (font.lineHeight * (1 - scale)) / 2, 0);
        pose.scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, color, false);
        pose.popPose();
    }

    /**
     * @param key  panel translation suffix
     * @param args values formatted into the message
     * @return translated UI text
     */
    private static MutableComponent tr(String key, Object... args) {
        return Component.translatable(TEXT + key, args);
    }

    /**
     * Device icon, formatted installed and maximum counts, and ARGB count color.
     */
    private record Row(ItemStack item, Component count, int color) {
    }
}
