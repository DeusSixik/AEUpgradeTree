package dev.sixik.aeupgrade.mixin;

import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Positions native slots while the interface viewport scrolls.
 */
@Mixin(Slot.class)
public interface InterfaceSlotPositionAccessor {
    /**
     * @param x horizontal screen-relative slot position in GUI pixels
     */
    @Mutable
    @Accessor("x")
    void aeupgrade$setSlotX(int x);

    /**
     * @param y vertical screen-relative slot position in GUI pixels
     */
    @Mutable
    @Accessor("y")
    void aeupgrade$setSlotY(int y);
}
