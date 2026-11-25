package dev.sixik.aeupgrade.channel;

/**
 * Bridges AE2's route calculation and the node that receives a channel allocation.
 */
public interface DeviceChannelAllocation {
    /**
     * Stages the number of channels granted to the device itself for the current calculation.
     *
     * @param channels granted local channels, or zero if the device was not granted any
     */
    void aeupgrade$stageAllocation(int channels);
}
