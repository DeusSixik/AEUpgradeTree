package dev.sixik.aeupgrade.client.slot;

import java.util.function.IntConsumer;

import appeng.client.gui.Icon;
import appeng.client.gui.style.BackgroundGenerator;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.widgets.IconButton;
import appeng.client.gui.widgets.Scrollbar;
import dev.sixik.aeupgrade.pattern.PatternSlotRange;
import dev.sixik.aeupgrade.pattern.SlotScrollState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Vertical scrollbar using the track and thumb from AE2's Network Status screen.
 */
public final class PatternSlotScrollbar extends AbstractSliderButton {
    /**
     * Extra space below the track in its backing panel, in GUI pixels.
     */
    public static final int PANEL_BOTTOM_EXTENSION = 4;

    /**
     * Length of the track in GUI pixels.
     */
    private static final int TRACK_LENGTH = 66;

    /**
     * Thickness of the network status track in GUI pixels.
     */
    private static final int TRACK_THICKNESS = 6;

    /**
     * Pixel width of the pointer target, including the thumb.
     */
    private static final int CONTROL_WIDTH = 14;

    /**
     * Maximum width of the slot count next to the AE2 Patterns label.
     */
    private static final int LABEL_MAX_WIDTH = 100;

    /**
     * Native thumb sprites used by Network Status and the ME Terminal.
     */
    private static final Scrollbar.Style THUMB = Scrollbar.BIG;

    /**
     * Top cap and inner shadow of the vertical track in the native background texture.
     */
    private static final Blitter TRACK_START = Blitter.texture("guis/networkstatus.png").src(178, 38, TRACK_THICKNESS, 4);

    /**
     * Repeatable single pixel row through the middle of the native track.
     */
    private static final Blitter TRACK_MIDDLE = Blitter.texture("guis/networkstatus.png").src(178, 42, TRACK_THICKNESS, 1);

    /**
     * Bottom cap of the native track.
     */
    private static final Blitter TRACK_END = Blitter.texture("guis/networkstatus.png").src(178, 117, TRACK_THICKNESS, 1);

    /**
     * Authoritative range and acknowledged position.
     */
    private final SlotScrollState menu;

    /**
     * Translation prefix for this slot family's scroll and locked-storage hints.
     */
    private final String hintPrefix;

    /**
     * Called with the selected offset after a click, drag or keyboard change.
     */
    private final IntConsumer select;

    /**
     * Text color chosen by the Pattern Provider screen's AE2 theme.
     */
    private final int textColor;

    /**
     * True while the mouse is changing the thumb position.
     */
    private boolean dragging;

    /**
     * Vertical distance from the thumb's upper edge to the grab point, in GUI pixels.
     */
    private double grabOffset;

    /**
     * Offset in the last rendered label, or -1 before the first update.
     */
    private int labeledOffset = -1;

    /**
     * Capacity in the last rendered label.
     */
    private int labeledCapacity;

    /**
     * Last visible logical slot number shown in the label, or zero for a capacity-only label.
     */
    private int labeledEnd;

    /**
     * Whether the current tooltip describes locked slots.
     */
    private boolean lockedHint;

    /**
     * Creates the scrollbar beside rows of paired or individual slots.
     *
     * @param x          left screen coordinate
     * @param y          top screen coordinate
     * @param menu       scroll state for the open provider
     * @param textColor  ARGB color of the slot count
     * @param hintPrefix translation key prefix for the scroll hints
     * @param select     callback that requests the selected offset
     */
    public PatternSlotScrollbar(int x, int y, SlotScrollState menu, int textColor, String hintPrefix, IntConsumer select) {
        super(x, y, CONTROL_WIDTH, TRACK_LENGTH, Component.empty(), 0);
        this.menu = menu;
        this.select = select;
        this.textColor = textColor;
        this.hintPrefix = hintPrefix;
//        setTooltip(Tooltip.create(Component.translatable(hintPrefix + ".scroll_hint")));
    }

    /**
     * Creates a navigation button using AE2's toolbar art and directional icons.
     *
     * @param icon        native arrow sprite
     * @param x           left screen coordinate
     * @param y           top screen coordinate
     * @param translation tooltip and narration translation key
     * @param navigate    called when the button is pressed
     * @return positioned AE2 icon button
     */
    public static IconButton arrow(Icon icon, int x, int y, String translation, Runnable navigate) {
        var button = new IconButton(pressed -> navigate.run()) {
            /** {@inheritDoc} */
            @Override
            protected Icon getIcon() {
                return icon;
            }
        };
        var label = Component.translatable(translation);
        button.setPosition(x, y);
        button.setMessage(label);
//        button.setTooltip(Tooltip.create(label));
        return button;
    }

    /**
     * Updates the thumb from the requested position and the label from the displayed inventory.
     */
    public void refresh() {
        int maximum = PatternSlotRange.maxOffset(menu.aeupgrade$browsable());
        visible = maximum > 0 || menu.aeupgrade$offset() > 0;
        active = visible;
        boolean hasLocked = menu.aeupgrade$browsable() > menu.aeupgrade$capacity();
        if (lockedHint != hasLocked) {
            lockedHint = hasLocked;
//            setTooltip(Tooltip.create(Component.translatable(hintPrefix + (lockedHint ? ".locked_hint" : ".scroll_hint"))));
        }
        if (!dragging) {
            value = maximum == 0 ? 0 : Math.min(1, (double) menu.aeupgrade$scrollTarget() / maximum);
        }
        showRange(menu.aeupgrade$offset());
    }

    /**
     * @return whether this control currently owns the left mouse drag
     */
    public boolean isDraggingThumb() {
        return dragging;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onClick(double mouseX, double mouseY) {
        dragging = true;
        double pointerOffset = mouseY - getY();
        double thumbOffset = value * thumbTravel();
        double insideThumb = pointerOffset - Math.round(thumbOffset);
        if (insideThumb >= 0 && insideThumb < THUMB.handleHeight()) {
            grabOffset = pointerOffset - thumbOffset;
        } else {
            grabOffset = THUMB.handleHeight() / 2.0;
            moveThumb(mouseY);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void onDrag(double mouseX, double mouseY, double dragX, double dragY) {
        if (dragging) {
            moveThumb(mouseY);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void onRelease(double mouseX, double mouseY) {
        if (dragging) {
            moveThumb(mouseY);
            dragging = false;
        }
        super.onRelease(mouseX, mouseY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void applyValue() {
        if (!dragging && active) {
            select.accept(selectedOffset());
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void updateMessage() {
        showRange(menu.aeupgrade$offset());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void renderWidget(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        int centerX = getX() + getWidth() / 2;
        int top = getY();
        int trackLeft = centerX - TRACK_THICKNESS / 2;
        BackgroundGenerator.draw(getWidth() + 4, getHeight() + 2 + PANEL_BOTTOM_EXTENSION,
                graphics, getX() - 2, top - 1);
        TRACK_START.dest(trackLeft, top).blit(graphics);
        TRACK_MIDDLE.dest(trackLeft, top + 4, TRACK_THICKNESS, getHeight() - 5).blit(graphics);
        TRACK_END.dest(trackLeft, top + getHeight() - 1).blit(graphics);

        int thumbWidth = THUMB.handleWidth();
        int thumbLength = THUMB.handleHeight();
        int thumbY = top + (int) Math.round(value * thumbTravel());
        var sprite = active && PatternSlotRange.maxOffset(menu.aeupgrade$browsable()) > 0
                ? THUMB.enabledSprite() : THUMB.disabledSprite();
        graphics.blitSprite(sprite, centerX - thumbWidth / 2, thumbY, thumbWidth, thumbLength);
    }

    /**
     * Draws the range in the provider's pattern heading even when no scroll travel is available.
     *
     * @param graphics screen drawing context
     * @param right    right edge of the text, relative to the menu
     * @param top      top of the text, relative to the menu
     */
    public void drawRange(GuiGraphics graphics, int right, int top) {
        if (!visible && menu.aeupgrade$capacity() == PatternSlotRange.ROW_SIZE) {
            return;
        }
        var font = Minecraft.getInstance().font;
        int labelWidth = font.width(getMessage());
        float scale = labelWidth <= LABEL_MAX_WIDTH ? 1 : (float) LABEL_MAX_WIDTH / labelWidth;
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(right, top, 0);
        pose.scale(scale, scale, 1);
        graphics.drawString(font, getMessage(), -labelWidth, 0, textColor, false);
        pose.popPose();
    }

    /**
     * Moves the thumb with its grab point and requests the corresponding row while held.
     *
     * @param mouseY absolute vertical GUI pointer coordinate in pixels
     */
    private void moveThumb(double mouseY) {
        value = Mth.clamp((mouseY - getY() - grabOffset) / thumbTravel(), 0.0, 1.0);
        select.accept(selectedOffset());
    }

    /**
     * @return usable distance in GUI pixels between the thumb's first and last positions
     */
    private int thumbTravel() {
        return getHeight() - THUMB.handleHeight();
    }

    /**
     * Maps the normalized thumb position to the nearest scrollable row.
     *
     * @return selected logical index of the first slot
     */
    private int selectedOffset() {
        int lastRow = PatternSlotRange.maxOffset(menu.aeupgrade$browsable()) / PatternSlotRange.ROW_SIZE;
        return (int) Math.round(value * lastRow) * PatternSlotRange.ROW_SIZE;
    }

    /**
     * Displays the visible slot numbers and the total number of unlocked slots.
     *
     * @param offset zero-based index of the first display slot
     */
    private void showRange(int offset) {
        int capacity = menu.aeupgrade$capacity();
        int browsable = menu.aeupgrade$browsable();
        int end = visible && offset < browsable ? offset + Math.min(PatternSlotRange.WINDOW_SIZE, browsable - offset) : 0;
        if (labeledOffset != offset || labeledCapacity != capacity || labeledEnd != end) {
            labeledOffset = offset;
            labeledCapacity = capacity;
            labeledEnd = end;
            setMessage(end > 0 ? Component.translatable("aeupgrade.patternslots.range", offset + 1, end, capacity)
                    : Component.translatable("aeupgrade.patternslots.capacity", capacity));
        }
    }
}
