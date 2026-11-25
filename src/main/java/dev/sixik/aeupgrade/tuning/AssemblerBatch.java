package dev.sixik.aeupgrade.tuning;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import appeng.api.crafting.PatternDetailsHelper;
import appeng.api.inventories.InternalInventory;
import appeng.api.networking.IGridNode;
import appeng.api.networking.ticking.TickRateModulation;
import appeng.api.stacks.AEItemKey;
import appeng.api.stacks.KeyCounter;
import appeng.blockentity.crafting.IMolecularAssemblerSupportedPattern;
import appeng.blockentity.crafting.MolecularAssemblerBlockEntity;
import appeng.core.definitions.AEItems;
import appeng.core.network.clientbound.AssemblerAnimationPacket;
import appeng.crafting.CraftingEvent;
import dev.sixik.aeupgrade.platform.PlatformNetwork;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Holds the accepted inputs and undelivered results of one automatic assembly cycle.
 */
public final class AssemblerBatch {
    /**
     * Vanilla work rate in progress per tick for each of AE2's six card levels.
     */
    private static final int[] CARD_PROGRESS = {10, 13, 17, 20, 25, 50};

    /**
     * Number of slots in the crafting matrix.
     */
    private static final int INPUT_SLOTS = 9;

    /**
     * Vanilla assembler slot for results that could not be ejected.
     */
    private static final int OUTPUT_SLOT = 9;

    /**
     * Full ingredient matrix for each accepted pattern push.
     */
    private final Deque<NonNullList<ItemStack>> inputs = new ArrayDeque<>();

    /**
     * Crafted items and remainders that have not yet been emitted or moved to the output slot.
     */
    private final Deque<ItemStack> outputs = new ArrayDeque<>();

    /**
     * Encoded pattern used to restore the batch after loading.
     */
    private ItemStack pattern = ItemStack.EMPTY;

    /**
     * Decoded pattern shared by every job in this batch.
     */
    private @Nullable IMolecularAssemblerSupportedPattern plan;

    /**
     * Direction back toward the pattern provider until every result has been delivered.
     */
    private @Nullable Direction side;

    /**
     * Server game time of the last accepted pattern push, in ticks.
     */
    private long lastInputTick;

    /**
     * True once paid work has started and new inputs can no longer join the batch.
     */
    private boolean started;

    /**
     * Assembly progress for the whole batch, from zero to {@link DeviceTuningMath#PROGRESS_PER_CYCLE}.
     */
    private double progress;

    /**
     * Reports whether this batch still owns ingredients, results, or the vanilla output slot.
     *
     * @return true while the batch is collecting, working, or delivering results
     */
    public boolean active() {
        return side != null;
    }

    /**
     * Returns work progress for the assembler menu.
     *
     * @return completed progress units, rounded down
     */
    public int progress() {
        return (int) progress;
    }

    /**
     * Reports how many recipes are in this batch while it still accepts new work.
     *
     * @return collected input sets, or maximum integer when busy crafting or delivering outputs
     */
    public int queuedRecipes() {
        return started || inputs.isEmpty() ? Integer.MAX_VALUE : inputs.size();
    }

    /**
     * Accepts one complete ingredient set for the same pattern and provider as the rest of the batch.
     *
     * @param incoming  decoded AE2 pattern being offered
     * @param table     ingredients owned by the caller until accepted
     * @param direction side of the provider that receives outputs
     * @param limit     maximum recipe count for a new or collecting batch
     * @param gameTime  world game time in ticks
     * @return true if the batch took ownership of all ingredients
     */
    public boolean enqueue(IMolecularAssemblerSupportedPattern incoming, KeyCounter[] table, Direction direction,
                           int limit, long gameTime) {
        if (active() && (started || inputs.isEmpty() || inputs.size() >= limit || side != direction
                || !incoming.getDefinition().equals(AEItemKey.of(pattern)))) {
            return false;
        }

        var copied = new KeyCounter[table.length];
        for (int index = 0; index < table.length; index++) {
            copied[index] = new KeyCounter();
            copied[index].addAll(table[index]);
        }
        var matrix = NonNullList.withSize(INPUT_SLOTS, ItemStack.EMPTY);
        incoming.fillCraftingGrid(copied, matrix::set);
        for (var counter : copied) {
            counter.removeZeros();
            if (!counter.isEmpty()) {
                return false;
            }
        }

        if (!active()) {
            side = direction;
            pattern = incoming.getDefinition().toStack();
            plan = incoming;
        }
        for (var counter : table) {
            counter.clear();
        }
        inputs.addLast(matrix);
        lastInputTick = gameTime;
        return true;
    }

    /**
     * Collects, advances, or delivers the current cycle using the assembler's real AE2 grid.
     *
     * @param assembler owning block entity
     * @param node      live assembler node
     * @param ticks     elapsed work ticks supplied by AE2
     * @param crafting  reusable vanilla crafting matrix
     * @return how AE2 should schedule the next tick
     */
    public TickRateModulation tick(MolecularAssemblerBlockEntity assembler, IGridNode node, int ticks,
                                   CraftingContainer crafting) {
        if (inputs.isEmpty()) {
            flush(assembler);
            return TickRateModulation.IDLE;
        }

        boolean beginning = !started;
        if (beginning && !DeviceTuningMath.batchReady(inputs.size(), DeviceTuningService.batchSize(node),
                node.getLevel().getGameTime() - lastInputTick)) {
            return TickRateModulation.IDLE;
        }

        if (plan == null && PatternDetailsHelper.decodePattern(pattern, node.getLevel()) instanceof IMolecularAssemblerSupportedPattern loaded) {
            plan = loaded;
        }
        if (plan == null) {
            while (!inputs.isEmpty()) {
                inputs.removeFirst().forEach(this::queueOutput);
            }
            assembler.saveChanges();
            flush(assembler);
            return TickRateModulation.IDLE;
        }

        int installed = assembler.getUpgrades().getInstalledUpgrades(AEItems.SPEED_CARD);
        int base = CARD_PROGRESS[DeviceTuningService.speedCards(node, installed)];
        double rate = DeviceTuningMath.speed(base, DeviceTuningService.speedPercent(node));
        double remaining = DeviceTuningMath.PROGRESS_PER_CYCLE - progress;
        double step = DeviceTuningService.advance(node, rate, base / 10.0, remaining,
                beginning ? 1 : ticks, inputs.size());
        started = true;
        progress += step;
        if (step > 0) {
            assembler.saveChanges();
        }
        if (step < remaining) {
            return TickRateModulation.FASTER;
        }

        craftAll(assembler, node, crafting, (int) Math.ceil(rate));
        flush(assembler);
        return TickRateModulation.IDLE;
    }

    /**
     * Executes each funded recipe using the same pattern, remainder, and crafting event APIs as AE2.
     *
     * @param assembler      owning block entity
     * @param node           live assembler node
     * @param crafting       reusable vanilla crafting matrix
     * @param animationSpeed progress per tick for the client animation
     */
    private void craftAll(MolecularAssemblerBlockEntity assembler, IGridNode node,
                          CraftingContainer crafting, int animationSpeed) {
        var level = node.getLevel();
        AEItemKey representative = null;
        while (!inputs.isEmpty()) {
            var matrix = inputs.peekFirst();
            for (int slot = 0; slot < INPUT_SLOTS; slot++) {
                crafting.setItem(slot, matrix.get(slot));
            }
            var recipeInput = crafting.asCraftInput();
            var result = plan.assemble(recipeInput, level);
            if (result.isEmpty()) {
                matrix.forEach(this::queueOutput);
            } else {
                result.onCraftedBySystem(level);
                CraftingEvent.fireAutoCraftingEvent(level, plan, result, crafting);
                var remainders = plan.getRemainingItems(recipeInput);
                queueOutput(result);
                remainders.forEach(this::queueOutput);
                if (representative == null) {
                    representative = AEItemKey.of(result);
                }
            }
            inputs.removeFirst();
        }
        if (representative != null) {
            var pos = assembler.getBlockPos();
            PlatformNetwork.sendToPlayersNear(level, null, pos.getX(), pos.getY(), pos.getZ(), 32,
                    new AssemblerAnimationPacket(pos, (byte) animationSpeed, representative));
        }
        progress = 0;
        assembler.saveChanges();
    }

    /**
     * Adds a result or remainder, combining adjacent identical stacks where they fit.
     *
     * @param stack crafted item, remainder, or recovered ingredient
     */
    private void queueOutput(ItemStack stack) {
        if (stack.isEmpty()) {
            return;
        }
        var tail = outputs.peekLast();
        if (tail != null && canMerge(tail, stack)) {
            tail.grow(stack.getCount());
        } else {
            outputs.addLast(stack.copy());
        }
    }

    /**
     * Delivers outputs to the provider or the assembler's normal externally extractable output slot.
     *
     * @param assembler owning block entity
     */
    private void flush(MolecularAssemblerBlockEntity assembler) {
        var inventory = assembler.getInternalInventory();
        var target = InternalInventory.wrapExternal(assembler.getLevel(), assembler.getBlockPos().relative(side),
                side.getOpposite());
        var buffered = inventory.getStackInSlot(OUTPUT_SLOT);
        if (!buffered.isEmpty() && target != null) {
            int count = buffered.getCount();
            buffered = target.addItems(buffered);
            if (buffered.getCount() != count) {
                inventory.setItemDirect(OUTPUT_SLOT, buffered);
                assembler.saveChanges();
            }
        }
        while (!outputs.isEmpty()) {
            var next = outputs.removeFirst();
            int count = next.getCount();
            var remainder = target == null ? next : target.addItems(next);
            if (remainder.isEmpty()) {
                assembler.saveChanges();
                continue;
            }

            if (buffered.isEmpty()) {
                buffered = remainder;
            } else if (canMerge(buffered, remainder)) {
                buffered = buffered.copyWithCount(buffered.getCount() + remainder.getCount());
            } else {
                if (remainder.getCount() != count) {
                    assembler.saveChanges();
                }
                outputs.addFirst(remainder);
                break;
            }
            inventory.setItemDirect(OUTPUT_SLOT, buffered);
            assembler.saveChanges();
        }
        if (buffered.isEmpty() && outputs.isEmpty()) {
            side = null;
            assembler.saveChanges();
        }
    }

    /**
     * Checks whether two stacks can share one inventory slot.
     *
     * @param existing current stack in the slot or at the end of the output queue
     * @param extra    stack to add
     * @return true for identical items whose combined count fits
     */
    private static boolean canMerge(ItemStack existing, ItemStack extra) {
        return ItemStack.isSameItemSameComponents(existing, extra)
                && extra.getCount() <= existing.getMaxStackSize() - existing.getCount();
    }

    /**
     * Adds batch-owned ingredients and pending results to the assembler's block drops.
     *
     * @param drops mutable item drop list
     */
    public void addDrops(List<ItemStack> drops) {
        for (var matrix : inputs) {
            for (var stack : matrix) {
                if (!stack.isEmpty()) {
                    drops.add(stack.copy());
                }
            }
        }
        for (var stack : outputs) {
            drops.add(stack.copy());
        }
    }

    /**
     * Serializes accepted jobs, paid progress, and results that have yet to be delivered.
     *
     * @param registries lookup used when encoding item stacks
     * @return saved batch data
     */
    public CompoundTag save(HolderLookup.Provider registries) {
        var tag = new CompoundTag();
        tag.putString("side", side.getName());
        tag.put("pattern", pattern.saveOptional(registries));
        tag.putLong("last_input", lastInputTick);
        tag.putBoolean("started", started);
        tag.putDouble("progress", progress);
        var savedInputs = new ListTag();
        for (var matrix : inputs) {
            savedInputs.add(ContainerHelper.saveAllItems(new CompoundTag(), matrix, registries));
        }
        tag.put("inputs", savedInputs);
        var savedOutputs = new ListTag();
        for (var stack : outputs) {
            savedOutputs.add(stack.save(registries));
        }
        tag.put("outputs", savedOutputs);
        return tag;
    }

    /**
     * Restores an active batch from the assembler's saved data.
     *
     * @param tag        saved batch compound
     * @param registries lookup used when decoding items
     * @return restored batch
     */
    public static AssemblerBatch load(CompoundTag tag, HolderLookup.Provider registries) {
        var batch = new AssemblerBatch();
        var direction = Direction.byName(tag.getString("side"));
        batch.side = direction == null ? Direction.DOWN : direction;
        batch.pattern = ItemStack.parseOptional(registries, tag.getCompound("pattern"));
        batch.lastInputTick = tag.getLong("last_input");
        batch.started = tag.getBoolean("started");
        double progress = tag.getDouble("progress");
        batch.progress = Double.isFinite(progress) ? Math.clamp(progress, 0, DeviceTuningMath.PROGRESS_PER_CYCLE) : 0;
        for (var entry : tag.getList("inputs", Tag.TAG_COMPOUND)) {
            var matrix = NonNullList.withSize(INPUT_SLOTS, ItemStack.EMPTY);
            ContainerHelper.loadAllItems((CompoundTag) entry, matrix, registries);
            batch.inputs.addLast(matrix);
        }
        for (var entry : tag.getList("outputs", Tag.TAG_COMPOUND)) {
            batch.queueOutput(ItemStack.parseOptional(registries, (CompoundTag) entry));
        }
        return batch;
    }
}
