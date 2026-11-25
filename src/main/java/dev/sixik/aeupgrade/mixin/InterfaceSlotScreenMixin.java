package dev.sixik.aeupgrade.mixin;

import java.util.ArrayList;
import java.util.List;

import appeng.client.gui.Icon;
import appeng.client.gui.WidgetContainer;
import appeng.client.gui.implementations.InterfaceScreen;
import appeng.client.gui.implementations.UpgradeableScreen;
import appeng.client.gui.style.PaletteColor;
import appeng.client.gui.style.ScreenStyle;
import appeng.client.gui.widgets.IconButton;
import appeng.core.localization.GuiText;
import appeng.menu.SlotSemantics;
import appeng.menu.implementations.InterfaceMenu;
import appeng.menu.slot.AppEngSlot;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mojang.blaze3d.platform.InputConstants;
import dev.sixik.aeupgrade.client.slot.PatternSlotScrollbar;
import dev.sixik.aeupgrade.pattern.PatternSlotRange;
import dev.sixik.aeupgrade.pattern.SlotScrollState;
import dev.sixik.aeupgrade.stock.InterfaceSlotMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Shows up to two rows of configuration and buffer pairs in the native interface screen.
 */
@Mixin(value = InterfaceScreen.class, remap = false)
public abstract class InterfaceSlotScreenMixin extends UpgradeableScreen<InterfaceMenu> implements SlotScrollState {
    /**
     * Width and height of a native slot frame, in GUI pixels.
     */
    @Unique
    private static final int AEUPGRADE_SLOT_PITCH = 18;

    /**
     * Space occupied by one line each of amount buttons, configuration and buffer slots.
     */
    @Unique
    private static final int AEUPGRADE_ROW_HEIGHT = 3 * AEUPGRADE_SLOT_PITCH;

    /**
     * Header space removed in compact mode between the title and amount buttons.
     */
    @Unique
    private static final int AEUPGRADE_COMPACT_HEADER = 20;

    /**
     * Footer space removed in compact mode between the storage and inventory labels.
     */
    @Unique
    private static final int AEUPGRADE_COMPACT_FOOTER = 12;

    /**
     * Minimum navigation strip position below AE2's upgrade card panel, in GUI pixels.
     */
    @Unique
    private static final int AEUPGRADE_SCROLL_TOP = 30;

    /**
     * Position used for slots outside the current viewport.
     */
    @Unique
    private static final int AEUPGRADE_HIDDEN = -9999;

    /**
     * Native buttons opening the amount editor at a logical configuration index.
     */
    @Shadow
    @Final
    private List<Button> amountButtons;

    /**
     * Visible slots by matching logical index, including rows outside the viewport.
     */
    @Unique
    private List<Slot> aeupgrade$config;

    /**
     * Buffer slots paired one-to-one with the configuration.
     */
    @Unique
    private List<Slot> aeupgrade$storage;

    /**
     * Zero-based index of the first visible configuration and buffer pair.
     */
    @Unique
    private int aeupgrade$first;

    /**
     * Height of the unmodified screen, in GUI pixels.
     */
    @Unique
    private int aeupgrade$baseHeight;

    /**
     * Whether the last layout has two rows of stock slot pairs.
     */
    @Unique
    private boolean aeupgrade$expanded;

    /**
     * Whether the expanded panel uses compact spacing to fit the GUI height.
     */
    @Unique
    private boolean aeupgrade$compact;

    /**
     * Left edge of the configuration and buffer slots relative to the screen.
     */
    @Unique
    private int aeupgrade$slotLeft;

    /**
     * Top edge of the first configuration slot relative to the screen.
     */
    @Unique
    private int aeupgrade$configTop;

    /**
     * Top edge of the first buffer slot relative to the screen.
     */
    @Unique
    private int aeupgrade$storageTop;

    /**
     * Top edge of the first amount button relative to the screen.
     */
    @Unique
    private int aeupgrade$buttonTop;

    /**
     * Scroll controls placed to the right of the interface.
     */
    @Unique
    private PatternSlotScrollbar aeupgrade$scrollbar;

    /**
     * Previous-row navigation control.
     */
    @Unique
    private IconButton aeupgrade$previous;

    /**
     * Next-row navigation control.
     */
    @Unique
    private IconButton aeupgrade$next;

    /**
     * Declares the native base screen constructor.
     *
     * @param menu      backing interface menu
     * @param inventory opening player's inventory
     * @param title     localized screen title
     * @param style     native screen layout and texture
     */
    protected InterfaceSlotScreenMixin(InterfaceMenu menu, Inventory inventory, Component title, ScreenStyle style) {
        super(menu, inventory, title, style);
    }

    /**
     * Leaves the stock amount buttons beyond the first row available for dynamic positioning.
     *
     * @param widgets  native AE2 widget container
     * @param id       style key for the amount button
     * @param button   amount editor trigger
     * @param original native widget registration
     */
    @WrapOperation(method = "<init>", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/WidgetContainer;add(Ljava/lang/String;Lnet/minecraft/client/gui/components/AbstractWidget;)V"))
    private void aeupgrade$registerBaseAmountButtons(WidgetContainer widgets, String id, AbstractWidget button,
                                                     Operation<Void> original) {
        if (!((InterfaceSlotMenu) menu).aeupgrade$hasStockSlots() || amountButtons.size() < PatternSlotRange.ROW_SIZE) {
            original.call(widgets, id, button);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void init() {
        var state = (InterfaceSlotMenu) menu;
        if (!state.aeupgrade$hasStockSlots()) {
            super.init();
            return;
        }
        if (aeupgrade$baseHeight == 0) {
            aeupgrade$baseHeight = imageHeight;
        }
        aeupgrade$expanded = PatternSlotRange.needsSecondRow(aeupgrade$browsable());
        int expandedHeight = aeupgrade$baseHeight + AEUPGRADE_ROW_HEIGHT;
        aeupgrade$compact = aeupgrade$expanded && height < expandedHeight + 10;
        imageHeight = aeupgrade$expanded ? expandedHeight : aeupgrade$baseHeight;
        if (aeupgrade$compact) {
            imageHeight -= AEUPGRADE_COMPACT_HEADER + AEUPGRADE_COMPACT_FOOTER;
        }
        super.init();

        aeupgrade$config = menu.getSlots(SlotSemantics.CONFIG);
        aeupgrade$storage = menu.getSlots(SlotSemantics.STORAGE);
        aeupgrade$slotLeft = aeupgrade$config.getFirst().x;
        aeupgrade$configTop = aeupgrade$config.getFirst().y;
        aeupgrade$storageTop = aeupgrade$storage.getFirst().y;
        aeupgrade$buttonTop = amountButtons.getFirst().getY() - getGuiTop();
        setTextHidden("interface_stored_items", aeupgrade$expanded);
        setTextHidden("interface_config", aeupgrade$compact);
        for (int slot = PatternSlotRange.ROW_SIZE; slot < amountButtons.size(); slot++) {
            var button = amountButtons.get(slot);
            button.setWidth(16);
            button.setHeight(16);
            addRenderableWidget(button);
        }

        int x = getGuiLeft() + imageWidth;
        int y = getGuiTop() + Math.max(AEUPGRADE_SCROLL_TOP, aeupgrade$buttonTop - aeupgrade$headerInset());
        int textColor = getStyle().getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB();
        aeupgrade$previous = addRenderableWidget(PatternSlotScrollbar.arrow(Icon.ARROW_UP, x + 1, y,
                "aeupgrade.interfaceslots.previous", () -> aeupgrade$scroll(aeupgrade$first - PatternSlotRange.ROW_SIZE)));
        aeupgrade$scrollbar = addRenderableWidget(new PatternSlotScrollbar(x + 2, y + 20, this, textColor,
                "aeupgrade.interfaceslots", this::aeupgrade$scroll));
        int end = aeupgrade$scrollbar.getY() + aeupgrade$scrollbar.getHeight() + PatternSlotScrollbar.PANEL_BOTTOM_EXTENSION;
        aeupgrade$next = addRenderableWidget(PatternSlotScrollbar.arrow(Icon.ARROW_DOWN, x + 1, end,
                "aeupgrade.interfaceslots.next", () -> aeupgrade$scroll(aeupgrade$first + PatternSlotRange.ROW_SIZE)));

        aeupgrade$first = PatternSlotRange.clampOffset(aeupgrade$first, aeupgrade$browsable());
        aeupgrade$positionStockSlots();
        aeupgrade$refreshWidgets();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void containerTick() {
        super.containerTick();
        if (aeupgrade$scrollbar == null || isQuickCrafting || aeupgrade$scrollbar.isDraggingThumb()) {
            return;
        }
        if (aeupgrade$expanded != PatternSlotRange.needsSecondRow(aeupgrade$browsable())) {
            rebuildWidgets();
        } else {
            aeupgrade$scroll(aeupgrade$first);
        }
    }

    /**
     * Preserves AE2's per-slot button visibility and hides buttons outside the unlocked viewport.
     *
     * @param callback Mixin callback
     */
    @Inject(method = "updateBeforeRender", at = @At("TAIL"))
    private void aeupgrade$refreshAfterNativeButtons(CallbackInfo callback) {
        if (aeupgrade$scrollbar != null) {
            aeupgrade$refreshWidgets();
        }
    }

    /**
     * Positions the two visible rows at matching logical indices in both inventories.
     */
    @Unique
    private void aeupgrade$positionStockSlots() {
        int shown = aeupgrade$expanded ? PatternSlotRange.WINDOW_SIZE : PatternSlotRange.ROW_SIZE;
        int inset = aeupgrade$headerInset();
        for (int slot = 0; slot < aeupgrade$config.size(); slot++) {
            int relative = slot - aeupgrade$first;
            boolean visible = relative >= 0 && relative < shown;
            int x = aeupgrade$slotLeft + relative % PatternSlotRange.ROW_SIZE * AEUPGRADE_SLOT_PITCH;
            int rowY = relative / PatternSlotRange.ROW_SIZE * AEUPGRADE_ROW_HEIGHT - inset;
            aeupgrade$position((AppEngSlot) aeupgrade$config.get(slot), x, aeupgrade$configTop + rowY, visible);
            aeupgrade$position((AppEngSlot) aeupgrade$storage.get(slot), x, aeupgrade$storageTop + rowY, visible);
            if (visible) {
                amountButtons.get(slot).setPosition(getGuiLeft() + x, getGuiTop() + aeupgrade$buttonTop + rowY);
            }
        }
    }

    /**
     * @return amount removed from the header in GUI pixels
     */
    @Unique
    private int aeupgrade$headerInset() {
        return aeupgrade$compact ? AEUPGRADE_COMPACT_HEADER : 0;
    }

    /**
     * Positions a native slot and excludes it from rendering and hover detection when hidden.
     *
     * @param slot    configuration or storage slot
     * @param x       screen-relative x coordinate
     * @param y       screen-relative y coordinate
     * @param visible whether the slot falls in the viewport
     */
    @Unique
    private void aeupgrade$position(AppEngSlot slot, int x, int y, boolean visible) {
        slot.setActive(visible);
        var position = (InterfaceSlotPositionAccessor) slot;
        position.aeupgrade$setSlotX(visible ? x : AEUPGRADE_HIDDEN);
        position.aeupgrade$setSlotY(visible ? y : AEUPGRADE_HIDDEN);
    }

    /**
     * Enables the controls for the rows and amount editors currently on-screen.
     */
    @Unique
    private void aeupgrade$refreshWidgets() {
        aeupgrade$scrollbar.refresh();
        aeupgrade$previous.visible = aeupgrade$next.visible = aeupgrade$scrollbar.visible;
        aeupgrade$previous.active = aeupgrade$first > 0;
        aeupgrade$next.active = aeupgrade$first < PatternSlotRange.maxOffset(aeupgrade$browsable());
        for (int slot = 0; slot < amountButtons.size(); slot++) {
            var config = aeupgrade$config.get(slot);
            amountButtons.get(slot).visible = slot < aeupgrade$capacity() && config.isActive() && config.hasItem();
        }
    }

    /**
     * Shows the selected row locally. AE2 synchronizes each logical slot with a fixed menu index.
     *
     * @param offset requested first logical slot in the viewport
     */
    @Unique
    private void aeupgrade$scroll(int offset) {
        int first = PatternSlotRange.clampOffset(offset, aeupgrade$browsable());
        if (first != aeupgrade$first && !isQuickCrafting) {
            aeupgrade$first = first;
            aeupgrade$positionStockSlots();
            aeupgrade$refreshWidgets();
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean mouseScrolled(double x, double y, double deltaX, double deltaY) {
        if (aeupgrade$scrollbar != null && aeupgrade$scrollbar.visible && deltaY != 0
                && (aeupgrade$overScrollStrip(x, y) || x >= getGuiLeft() + aeupgrade$slotLeft
                && x < getGuiLeft() + imageWidth && y >= getGuiTop() + aeupgrade$buttonTop - aeupgrade$headerInset()
                && y < getGuiTop() + aeupgrade$storageTop + AEUPGRADE_ROW_HEIGHT + AEUPGRADE_SLOT_PITCH
                - aeupgrade$headerInset())) {
            aeupgrade$scroll(aeupgrade$first + (deltaY < 0 ? PatternSlotRange.ROW_SIZE : -PatternSlotRange.ROW_SIZE));
            return true;
        }
        return super.mouseScrolled(x, y, deltaX, deltaY);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return (button == InputConstants.MOUSE_BUTTON_RIGHT && aeupgrade$overScrollStrip(x, y))
                || super.mouseClicked(x, y, button);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean mouseDragged(double x, double y, int button, double deltaX, double deltaY) {
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
     * Checks whether the pointer is inside the attached controls.
     *
     * @param x pointer screen x coordinate
     * @param y pointer screen y coordinate
     * @return true inside a visible scroll strip, including the arrow buttons
     */
    @Unique
    private boolean aeupgrade$overScrollStrip(double x, double y) {
        return aeupgrade$scrollbar != null && aeupgrade$scrollbar.visible && x >= getGuiLeft() + imageWidth
                && x < getGuiLeft() + imageWidth + 18 && y >= aeupgrade$previous.getY() && y < aeupgrade$next.getY() + 21;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<Rect2i> getExclusionZones() {
        var zones = super.getExclusionZones();
        if (aeupgrade$scrollbar == null || !aeupgrade$scrollbar.visible) {
            return zones;
        }
        var extended = new ArrayList<>(zones);
        extended.add(new Rect2i(getGuiLeft() + imageWidth, aeupgrade$previous.getY(), 18,
                aeupgrade$next.getY() + 21 - aeupgrade$previous.getY()));
        return extended;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void drawBG(GuiGraphics graphics, int left, int top, int mouseX, int mouseY, float partialTick) {
        var background = getStyle().getBackground();
        if (!aeupgrade$expanded || background == null) {
            super.drawBG(graphics, left, top, mouseX, mouseY, partialTick);
            return;
        }
        int srcX = background.getSrcX();
        int srcY = background.getSrcY();
        int width = background.getSrcWidth();
        int split = aeupgrade$storageTop + AEUPGRADE_SLOT_PITCH;
        int headerInset = aeupgrade$headerInset();
        int footerY = split + AEUPGRADE_ROW_HEIGHT - headerInset;
        if (aeupgrade$compact) {
            int buttons = aeupgrade$buttonTop - 1;
            background.src(srcX, srcY, width, buttons - headerInset).dest(left, top).blit(graphics);
            background.src(srcX, srcY + buttons, width, split - buttons)
                    .dest(left, top + buttons - headerInset).blit(graphics);
        } else {
            background.src(srcX, srcY, width, split).dest(left, top).blit(graphics);
        }
        background.src(srcX, srcY + split - AEUPGRADE_ROW_HEIGHT, width, AEUPGRADE_ROW_HEIGHT)
                .dest(left, top + split - headerInset).blit(graphics);
        if (aeupgrade$compact) {
            // The label strip ends where the native inventory heading would be drawn.
            int keptBeforeInventory = aeupgrade$baseHeight - 95 - split - AEUPGRADE_COMPACT_FOOTER;
            background.src(srcX, srcY + split, width, keptBeforeInventory).dest(left, top + footerY).blit(graphics);
            footerY += keptBeforeInventory;
            split += keptBeforeInventory + AEUPGRADE_COMPACT_FOOTER;
        }
        background.src(srcX, srcY + split, width, aeupgrade$baseHeight - split).dest(left, top + footerY).blit(graphics);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void drawFG(GuiGraphics graphics, int left, int top, int mouseX, int mouseY) {
        super.drawFG(graphics, left, top, mouseX, mouseY);
        int storedItemsTop = aeupgrade$storageTop + 20 + AEUPGRADE_ROW_HEIGHT - aeupgrade$headerInset();
        if (aeupgrade$scrollbar != null) {
            aeupgrade$scrollbar.drawRange(graphics, imageWidth - 8, aeupgrade$compact ? storedItemsTop : 24);
        }
        if (aeupgrade$expanded) {
            graphics.drawString(font, GuiText.StoredItems.text(), aeupgrade$slotLeft, storedItemsTop,
                    getStyle().getColor(PaletteColor.DEFAULT_TEXT_COLOR).toARGB(), false);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void renderSlot(GuiGraphics graphics, Slot slot) {
        super.renderSlot(graphics, slot);
        var semantic = menu.getSlotSemantic(slot);
        if (aeupgrade$scrollbar != null && slot.isActive() && slot.getContainerSlot() >= aeupgrade$capacity()
                && (semantic == SlotSemantics.CONFIG || semantic == SlotSemantics.STORAGE)) {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x60A03030);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int aeupgrade$capacity() {
        return ((InterfaceSlotMenu) menu).aeupgrade$stockCapacity();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int aeupgrade$browsable() {
        return ((InterfaceSlotMenu) menu).aeupgrade$stockRange();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public int aeupgrade$offset() {
        return aeupgrade$first;
    }
}
