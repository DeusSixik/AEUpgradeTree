package dev.sixik.aeupgrade.api;

import appeng.api.networking.IGrid;
import dev.sixik.aeupgrade.tree.UpgradeEffects;
import net.fabricmc.fabric.api.event.Event;
import net.fabricmc.fabric.api.event.EventFactory;

/**
 * Notifies Fabric add-ons when a grid publishes different custom modifiers on the server thread.
 *
 * @param grid affected AE2 network
 * @param previous last published effects
 * @param current new effects
 */
public record UpgradeEffectsChangedEvent(IGrid grid, UpgradeEffects previous, UpgradeEffects current) {
    /** Fabric callback for invalidating caches of custom effects. */
    public static final Event<Listener> EVENT = EventFactory.createArrayBacked(Listener.class,
            listeners -> event -> {
                for (var listener : listeners) {
                    listener.onChanged(event);
                }
            });

    /** Receives an effect update for one AE2 network. */
    @FunctionalInterface
    public interface Listener {
        /** @param event the affected network and its last two effects snapshots */
        void onChanged(UpgradeEffectsChangedEvent event);
    }
}
