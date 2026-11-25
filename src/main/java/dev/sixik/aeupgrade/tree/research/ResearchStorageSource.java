package dev.sixik.aeupgrade.tree.research;

import java.util.Optional;

import appeng.api.networking.security.IActionHost;
import appeng.api.networking.security.IActionSource;
import net.minecraft.world.entity.player.Player;

/**
 * Identifies reward inserts that belong in storage rather than in a waiting crafting CPU.
 */
public record ResearchStorageSource(Player owner) implements IActionSource {
    /**
     * @return authenticated player collecting the reward
     */
    @Override
    public Optional<Player> player() {
        return Optional.of(owner);
    }

    /**
     * @return empty because the research player initiates the transfer
     */
    @Override
    public Optional<IActionHost> machine() {
        return Optional.empty();
    }

    /**
     * @param key requested AE2 context type
     * @param <T> requested context type
     * @return empty because this source only carries the player
     */
    @Override
    public <T> Optional<T> context(Class<T> key) {
        return Optional.empty();
    }
}
