package dev.sixik.aeupgrade.mixin;

import appeng.client.Point;
import appeng.client.gui.AEBaseScreen;
import appeng.client.gui.implementations.PatternProviderScreen;
import appeng.client.gui.layout.SlotGridLayout;
import appeng.client.gui.style.SlotPosition;
import appeng.menu.SlotSemantics;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sixik.aeupgrade.pattern.PatternSlotMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Uses AE2's nine-column grid for scrolling Pattern Provider menus.
 */
@Mixin(value = AEBaseScreen.class, remap = false)
public abstract class PatternSlotLayoutMixin {
    /**
     * Wraps the provider's pattern grid at nine slots using its original style anchor.
     *
     * @param layout   original layout from AE2's screen style
     * @param x        resolved grid origin x coordinate
     * @param y        resolved grid origin y coordinate
     * @param index    zero-based slot index in the grid
     * @param original native layout operation
     * @param position style entry for the slots being placed
     * @return the slot position in this screen
     */
    @WrapOperation(method = "getSlotPosition", at = @At(value = "INVOKE",
            target = "Lappeng/client/gui/layout/SlotGridLayout;getPosition(III)Lappeng/client/Point;"))
    private Point aeupgrade$twoPatternRows(SlotGridLayout layout, int x, int y, int index, Operation<Point> original,
                                           SlotPosition position) {
        if ((Object) this instanceof PatternProviderScreen<?> screen
                && ((PatternSlotMenu) screen.getMenu()).aeupgrade$hasPatternWindow()
                && position == screen.getStyle().getSlots().get(SlotSemantics.ENCODED_PATTERN.id())) {
            return original.call(SlotGridLayout.BREAK_AFTER_9COLS, x, y, index);
        }
        return original.call(layout, x, y, index);
    }
}
