package dev.sixik.aeupgrade.api;

import java.util.Objects;

import appeng.api.networking.IGrid;
import dev.sixik.aeupgrade.tree.UpgradeTreeService;
import net.minecraft.resources.ResourceLocation;

/**
 * Server-side entry points for rewarding research on an AE2 network with an online controller.
 */
public final class UpgradeResearchPoints {
    private UpgradeResearchPoints() {
    }

    /**
     * Credits a repeatable reward, accepting as many points as fit under the configured ceiling.
     *
     * @param grid   grid receiving the reward, resolved on the logical server thread
     * @param amount positive number of points to award
     * @return points credited, or zero when there is no online controller or the ceiling has been reached
     */
    public static int award(IGrid grid, int amount) {
        return grid.getService(UpgradeTreeService.class).awardPoints(amount, null);
    }

    /**
     * Credits an exact one-time reward to a controller's research profile. Use a stable namespaced key,
     * such as a quest or advancement ID. A reward rejected for insufficient room can be retried.
     *
     * @param grid     grid receiving the reward, resolved on the logical server thread
     * @param rewardId unique reward ID within the controller profile
     * @param amount   positive number of points to award
     * @return points credited, or zero when the full amount cannot be granted or the ID was already claimed
     */
    public static int awardOnce(IGrid grid, ResourceLocation rewardId, int amount) {
        return grid.getService(UpgradeTreeService.class).awardPoints(amount, Objects.requireNonNull(rewardId));
    }
}
