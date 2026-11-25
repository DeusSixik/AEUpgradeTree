package dev.sixik.aeupgrade.mixin;

import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.Icon;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.style.Blitter;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.PatternProviderMenu;
import com.mojang.blaze3d.platform.InputConstants;
import dev.sixik.aeupgrade.client.slot.PatternSlotScrollbar;
import dev.sixik.aeupgrade.pattern.PatternSlotMenu;
import dev.sixik.aeupgrade.pattern.PatternSlotRange;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Adds scrolling controls to the native Pattern Provider screen.
 */
@Mixin(value = PatternProviderScreen.class, remap = false)
public abstract class PatternSlotsScreenMixin extends AEBaseScreen<PatternProviderMenu> {
    /**
     * Distance between rows in AE2's slot grid, in GUI pixels.
     */
    @Unique
    private static final int AEUPGRADE_SLOT_PITCH = 18;

    /**
     * Copies the provider's native slot frames for the second row.
     */
    @Unique
    private static final Blitter AEUPGRADE_PATTERN_ROW = Blitter.texture("guis/pattern_provider.png")
            .src(7, 44, PatternSlotRange.ROW_SIZE * AEUPGRADE_SLOT_PITCH, AEUPGRADE_SLOT_PITCH);

    /**
     * Thumb for jumping through the pattern inventory.
     */
    @Unique
    private PatternSlotScrollbar aeupgrade$scrollbar;

    /**
     * Button for moving up one row.
     */
    @Unique
    private IconButton aeupgrade$previous;

    /**
     * Button for moving down one row.
     */
    @Unique
    private IconButton aeupgrade$next;

    /**
     * Declares the AE2 base screen constructor used by inherited members.
     *
     * @param menu            provider container
     * @param playerInventory opening player's inventory
     * @param title           screen title
     * @param style           native screen layout
     */
    protected PatternSlotsScreenMixin(PatternProviderMenu menu, Inventory playerInventory, Component title,
                                      ScreenStyle style) {
        super(menu, playerInventory, title, style);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void init() {
        super.init();
        var state = (PatternSlotMenu) menu;
        if (!state.aeupgrade$hasPatternWindow()) {
            return;
        }
        int y = getGuiTop();
        int x = getGuiLeft();
        aeupgrade$previous = addRenderableWidget(PatternSlotScrollbar.arrow(Icon.ARROW_UP, x + 177, y + 32,
                "aeupgrade.patternslots.previous", () -> aeupgrade$scrollByRows(-1)));
        int textColor = getStyle().getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        aeupgrade$scrollbar = addRenderableWidget(new PatternSlotScrollbar(x + 178, y + 52, state, textColor,
                "aeupgrade.patternslots", this::aeupgrade$scroll));
        int bottom = aeupgrade$scrollbar.getY() + aeupgrade$scrollbar.getHeight() + PatternSlotScrollbar.PANEL_BOTTOM_EXTENSION;
        aeupgrade$next = addRenderableWidget(PatternSlotScrollbar.arrow(Icon.ARROW_DOWN, x + 177, bottom,
                "aeupgrade.patternslots.next", () -> aeupgrade$scrollByRows(1)));
        aeupgrade$refreshControls();
    }

    /**
     * Keeps the controls in sync with capacity changes while the screen is open.
     *
     * @param callback Mixin callback
     */
    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void aeupgrade$refreshAfterUpdate(CallbackInfo callback) {
        aeupgrade$refreshControls();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void containerTick() {
        super.containerTick();
        var state = (PatternSlotMenu) menu;
        int maximum = PatternSlotRange.maxOffset(state.aeupgrade$browsable());
        if (!state.aeupgrade$waitingForScroll() && state.aeupgrade$offset() > maximum) {
            aeupgrade$scroll(maximum);
        }
    }

    /**
     * Updates the widgets from the acknowledged menu state.
     */
    @Unique
    private void aeupgrade$refreshControls() {
        if (aeupgrade$scrollbar == null) {
            return;
        }
        var state = (PatternSlotMenu) menu;
        aeupgrade$scrollbar.refresh();
        aeupgrade$previous.visible = aeupgrade$next.visible = aeupgrade$scrollbar.visible;
        aeupgrade$previous.active = aeupgrade$scrollbar.active && state.aeupgrade$scrollTarget() > 0;
        aeupgrade$next.active = aeupgrade$scrollbar.active
                && state.aeupgrade$scrollTarget() < PatternSlotRange.maxOffset(state.aeupgrade$browsable());
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean mouseScrolled(double x, double y, double deltaX, double deltaY) {
        if (aeupgrade$scrollbar != null && aeupgrade$scrollbar.visible && deltaY != 0
                && (aeupgrade$overScrollStrip(x, y) || x >= getGuiLeft() + 8 && x < getGuiLeft() + 169
                && y >= getGuiTop() + 44 && y < getGuiTop() + 44 + 2 * AEUPGRADE_SLOT_PITCH)) {
            aeupgrade$scrollByRows(deltaY < 0 ? 1 : -1);
            return true;
        }
        return super.mouseScrolled(x, y, deltaX, deltaY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean mouseClicked(double x, double y, int button) {
        if (button == InputConstants.MOUSE_BUTTON_RIGHT && aeupgrade$scrollbar != null && aeupgrade$scrollbar.isMouseOver(x, y)) {
            return true;
        }
        return super.mouseClicked(x, y, button);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean mouseDragged(double x, double y, int button, double deltaX, double deltaY) {
        // The container screen handles item drags itself, so it never forwards drags to this widget.
        if (aeupgrade$scrollbar != null && aeupgrade$scrollbar.isDraggingThumb() && button == InputConstants.MOUSE_BUTTON_LEFT) {
            return aeupgrade$scrollbar.mouseDragged(x, y, button, deltaX, deltaY);
        }
        return super.mouseDragged(x, y, button, deltaX, deltaY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean mouseReleased(double x, double y, int button) {
        if (aeupgrade$scrollbar != null && aeupgrade$scrollbar.isDraggingThumb() && button == InputConstants.MOUSE_BUTTON_LEFT) {
            setDragging(false);
            return aeupgrade$scrollbar.mouseReleased(x, y, button);
        }
        return super.mouseReleased(x, y, button);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected boolean hasClickedOutside(double x, double y, int left, int top, int button) {
        return !aeupgrade$overScrollStrip(x, y) && super.hasClickedOutside(x, y, left, top, button);
    }

    /**
     * Includes the attached scrolling controls in the menu's input area.
     *
     * @param x mouse x coordinate
     * @param y mouse y coordinate
     * @return true while the pointer is inside the visible scroll strip
     */
    @Unique
    private boolean aeupgrade$overScrollStrip(double x, double y) {
        return aeupgrade$scrollbar != null && aeupgrade$scrollbar.visible
                && x >= getGuiLeft() + 176 && x < getGuiLeft() + 194
                && y >= aeupgrade$previous.getY() && y < aeupgrade$next.getY() + 21;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void drawBG(GuiGraphics graphics, int left, int top, int mouseX, int mouseY, float partialTick) {
        super.drawBG(graphics, left, top, mouseX, mouseY, partialTick);
        if (aeupgrade$scrollbar != null
                && PatternSlotRange.needsSecondRow(((PatternSlotMenu) menu).aeupgrade$browsable())) {
            var firstSlot = menu.getSlots(SlotSemantics.ENCODED_PATTERN).get(PatternSlotRange.ROW_SIZE);
            AEUPGRADE_PATTERN_ROW.dest(left + firstSlot.x - 1, top + firstSlot.y - 1).blit(graphics);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void drawFG(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        super.drawFG(graphics, left, top, mouseX, mouseY);
        if (aeupgrade$scrollbar != null) {
            aeupgrade$scrollbar.drawRange(graphics, 168, 34);
        }
    }

    /**
     * Advances the selected row from the latest scroll target.
     *
     * @param rows signed number of rows to move
     */
    @Unique
    private void aeupgrade$scrollByRows(int rows) {
        var state = (PatternSlotMenu) menu;
        aeupgrade$scroll(state.aeupgrade$scrollTarget() + (long) rows * PatternSlotRange.ROW_SIZE);
    }

    /**
     * Requests a new row unless the player is in the middle of spreading a stack across slots.
     *
     * @param offset zero-based logical index of the first slot
     */
    @Unique
    private void aeupgrade$scroll(long offset) {
        if (!isQuickCrafting) {
            ((PatternSlotMenu) menu).aeupgrade$requestScroll(offset);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void slotClicked(@Nullable Slot slot, int index, int mouseButton, ClickType type) {
        var state = (PatternSlotMenu) menu;
        boolean secondRowHidden = slot != null
                && menu.getSlotSemantic(slot) == SlotSemantics.ENCODED_PATTERN
                && slot.getContainerSlot() >= PatternSlotRange.ROW_SIZE
                && !PatternSlotRange.needsSecondRow(state.aeupgrade$browsable());
        if (!secondRowHidden && !state.aeupgrade$waitingForScroll()
                && (aeupgrade$scrollbar == null || !aeupgrade$scrollbar.isDraggingThumb())) {
            super.slotClicked(slot, index, mouseButton, type);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void renderSlot(GuiGraphics graphics, Slot slot) {
        var state = (PatternSlotMenu) menu;
        if (menu.getSlotSemantic(slot) == SlotSemantics.ENCODED_PATTERN
                && slot.getContainerSlot() >= PatternSlotRange.ROW_SIZE
                && !PatternSlotRange.needsSecondRow(state.aeupgrade$browsable())) {
            return;
        }
        super.renderSlot(graphics, slot);
        if (aeupgrade$scrollbar == null || menu.getSlotSemantic(slot) != SlotSemantics.ENCODED_PATTERN) {
            return;
        }
        if (slot.getContainerSlot() >= state.aeupgrade$capacity() - state.aeupgrade$offset()) {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x60A03030);
        }
    }
}
