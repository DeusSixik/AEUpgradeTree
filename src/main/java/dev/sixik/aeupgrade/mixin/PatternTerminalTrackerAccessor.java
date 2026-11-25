package dev.sixik.aeupgrade.mixin;

import appeng.helpers.patternprovider.PatternContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * Accesses the target of an AE2 terminal interaction.
 */
@Mixin(targets = "appeng.menu.implementations.PatternAccessTermMenu$ContainerTracker", remap = false)
public interface PatternTerminalTrackerAccessor {
    /**
     * @return container tracked under the terminal's server slot IDs
     */
    @Accessor("container")
    PatternContainer aeupgrade$getContainer();
}
