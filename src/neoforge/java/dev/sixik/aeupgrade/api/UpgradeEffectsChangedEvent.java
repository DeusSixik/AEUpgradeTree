package dev.sixik.aeupgrade.api;

import appeng.api.networking.IGrid;
import dev.sixik.aeupgrade.tree.UpgradeEffects;
import net.neoforged.bus.api.Event;

/**
 * Posted on the NeoForge game bus on the server thread when a grid publishes different add-on modifiers.
 * Add-ons may use it to invalidate caches after purchases, resets, tree edits and topology changes.
 */
public final class UpgradeEffectsChangedEvent extends Event {
    private final IGrid grid;
    private final UpgradeEffects previous;
    private final UpgradeEffects current;

    public UpgradeEffectsChangedEvent(IGrid grid, UpgradeEffects previous, UpgradeEffects current) {
        this.grid = grid;
        this.previous = previous;
        this.current = current;
    }

    public IGrid grid() {
        return grid;
    }

    public UpgradeEffects previous() {
        return previous;
    }

    public UpgradeEffects current() {
        return current;
    }
}
