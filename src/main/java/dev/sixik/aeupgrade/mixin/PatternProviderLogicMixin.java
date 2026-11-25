package dev.sixik.aeupgrade.mixin;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Set;

import appeng.api.crafting.IPatternDetails;
import appeng.api.implementations.blockentities.ICraftingMachine;
import appeng.api.networking.IManagedGridNode;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.helpers.patternprovider.PatternProviderLogic;
import appeng.helpers.patternprovider.PatternProviderLogicHost;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.sixik.aeupgrade.tuning.AssemblerDispatchLoad;
import dev.sixik.aeupgrade.tuning.DeviceTuningService;
import dev.sixik.aeupgrade.tuning.ProviderDispatch;
import dev.sixik.aeupgrade.tuning.ProviderTickBudget;
import net.minecraft.core.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Limits recipe pushes and balances assembly jobs among the provider's adjacent assemblers.
 */
@Mixin(value = PatternProviderLogic.class, remap = false)
public abstract class PatternProviderLogicMixin implements ProviderDispatch {
    /**
     * Total possible adjacent sides of a provider block.
     */
    @Unique
    private static final int AEUPGRADE_SIDE_COUNT = 6;

    /**
     * AE2 node for this provider block or panel.
     */
    @Final
    @Shadow
    private IManagedGridNode mainNode;

    /**
     * AE2 host used to look up adjacent assembler blocks.
     */
    @Final
    @Shadow
    private PatternProviderLogicHost host;

    /**
     * Reservations for all pushes to this provider in the current tick.
     */
    @Unique
    private final ProviderTickBudget aeupgrade$budget = new ProviderTickBudget();

    /**
     * Ordinal of the side that most recently accepted an assembly job, or -1 before the first success.
     */
    @Unique
    private int aeupgrade$lastAssemblerSide = -1;

    /**
     * Reads AE2's outgoing buffer state.
     *
     * @return true when pending ingredients prevent more dispatches
     */
    @Shadow
    public abstract boolean isBusy();

    /**
     * {@inheritDoc}
     */
    @Override
    public int aeupgrade$remainingOperations() {
        var node = mainNode.getNode();
        int limit = DeviceTuningService.providerThroughput(node);
        if (limit == 0 || isBusy() || !mainNode.isActive()) {
            return 0;
        }
        return aeupgrade$budget.remaining(node.getLevel().getServer().getTickCount(), limit);
    }

    /**
     * Counts successful recipe dispatches once per provider, even when multiple CPUs send work.
     *
     * @param pattern     full recipe to dispatch
     * @param ingredients inputs for one recipe operation
     * @param push        AE2's original dispatch operation
     * @return true if the destination took ownership of the ingredients
     */
    @WrapMethod(method = "pushPattern")
    private boolean aeupgrade$limitThroughput(IPatternDetails pattern, KeyCounter[] ingredients, Operation<Boolean> push) {
        var node = mainNode.getNode();
        int limit = DeviceTuningService.providerThroughput(node);
        if (limit == 0) {
            return push.call(pattern, ingredients);
        }
        if (!aeupgrade$budget.reserve(node.getLevel().getServer().getTickCount(), limit)) {
            return false;
        }

        boolean accepted = false;
        try {
            accepted = push.call(pattern, ingredients);
            return accepted;
        } finally {
            if (!accepted) {
                aeupgrade$budget.release();
            }
        }
    }

    /**
     * Orders assembler sides by queue length, preserving the positions of other sides in AE2's iteration.
     *
     * @param sides   sides AE2 allows this provider to send to
     * @param iterate AE2's original side iterator
     * @param pattern current recipe
     * @return ordered sides with the least loaded assembler tried first
     */
    @WrapOperation(method = "pushPattern", at = @At(value = "INVOKE", target = "Ljava/util/Set;iterator()Ljava/util/Iterator;"))
    private Iterator<Direction> aeupgrade$orderAssemblers(Set<Direction> sides, Operation<Iterator<Direction>> iterate,
                                                          IPatternDetails pattern) {
        if (sides.size() < 2 || !(pattern instanceof IMolecularAssemblerSupportedPattern)
                || DeviceTuningService.providerThroughput(mainNode.getNode()) == 0) {
            return iterate.call(sides);
        }

        var block = host.getBlockEntity();
        var level = block.getLevel();
        var pos = block.getBlockPos();
        var ordered = new ArrayList<>(sides);
        int[] slots = new int[AEUPGRADE_SIDE_COUNT];
        int[] loads = new int[AEUPGRADE_SIDE_COUNT];
        int assemblers = 0;
        for (int index = 0; index < ordered.size(); index++) {
            var side = ordered.get(index);
            if (!(level.getBlockEntity(pos.relative(side)) instanceof AssemblerDispatchLoad machine)) {
                continue;
            }
            slots[assemblers] = index;
            int load = machine.aeupgrade$queuedRecipes();
            int slot = assemblers;
            while (slot > 0) {
                var previous = ordered.get(slots[slot - 1]);
                int previousLoad = loads[slot - 1];
                if (load > previousLoad || load == previousLoad && aeupgrade$distance(side) >= aeupgrade$distance(previous)) {
                    break;
                }
                ordered.set(slots[slot], previous);
                loads[slot] = previousLoad;
                slot--;
            }
            ordered.set(slots[slot], side);
            loads[slot] = load;
            assemblers++;
        }
        return ordered.iterator();
    }

    /**
     * Measures how far a side lies from the next side in the rotation.
     *
     * @param side provider output side
     * @return zero-based distance from the next side following the last successful send
     */
    @Unique
    private int aeupgrade$distance(Direction side) {
        return Math.floorMod(side.ordinal() - aeupgrade$lastAssemblerSide - 1, AEUPGRADE_SIDE_COUNT);
    }

    /**
     * Remembers successful assembler sends so equal queues get round-robin service.
     *
     * @param machine     adjacent crafting machine
     * @param pattern     recipe offered by the provider
     * @param ingredients input set owned by the caller until accepted
     * @param returnSide  assembler side facing the provider
     * @param send        original AE2 operation
     * @return true if the machine accepted the inputs
     */
    @WrapOperation(method = "pushPattern", at = @At(value = "INVOKE",
            target = "Lappeng/api/implementations/blockentities/ICraftingMachine;pushPattern(Lappeng/api/crafting/IPatternDetails;[Lappeng/api/stacks/KeyCounter;Lnet/minecraft/core/Direction;)Z"))
    private boolean aeupgrade$rememberAssemblerSide(ICraftingMachine machine, IPatternDetails pattern,
                                                    KeyCounter[] ingredients, Direction returnSide, Operation<Boolean> send) {
        boolean accepted = send.call(machine, pattern, ingredients, returnSide);
        if (accepted && machine instanceof AssemblerDispatchLoad) {
            aeupgrade$lastAssemblerSide = returnSide.getOpposite().ordinal();
        }
        return accepted;
    }
}
