package dev.sixik.aeupgrade.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.sixik.aeupgrade.pattern.PatternSlotRange;
import dev.sixik.aeupgrade.pattern.PatternSlotService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * Test controls for the Pattern Provider slot upgrade stored on an ME controller.
 */
final class PatternSlotCommands {
    /**
     * Keeps registration static.
     */
    private PatternSlotCommands() {
    }

    /**
     * @return operator command branch for reading and changing the Pattern Provider slot bonus
     */
    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return literal("patternslots")
                .then(literal("get").executes(context -> run(context.getSource(), 0)))
                .then(literal("set").then(argument("slots", IntegerArgumentType.integer(0))
                        .executes(context -> set(context.getSource(), IntegerArgumentType.getInteger(context, "slots")))))
                .then(literal("add").then(argument("amount", IntegerArgumentType.integer(1))
                        .executes(context -> run(context.getSource(), IntegerArgumentType.getInteger(context, "amount")))))
                .then(literal("remove").then(argument("amount", IntegerArgumentType.integer(1))
                        .executes(context -> run(context.getSource(), -IntegerArgumentType.getInteger(context, "amount")))));
    }

    /**
     * Reads or changes the number of slots on the targeted controller's grid.
     *
     * @param source operator looking at an ME controller
     * @param delta  signed change in slots per provider, or zero to only read the setting
     * @return total slots per provider on a read, or one on a successful write
     * @throws CommandSyntaxException if the source is not targeting a valid controller
     */
    private static int run(CommandSourceStack source, int delta) throws CommandSyntaxException {
        var controller = ChannelCommands.requireController(source);
        var service = controller.getGrid().getService(PatternSlotService.class);
        if (delta != 0) {
            service.adjust(controller, delta);
        }
        int capacity = report(source, service.effectiveBonus());
        return delta == 0 ? capacity : 1;
    }

    /**
     * Sets the operator's base capacity for the selected network.
     *
     * @param source   operator looking at an ME controller
     * @param capacity unlocked slots before applying the tree bonus
     * @return one after applying the new capacity
     * @throws CommandSyntaxException if the source is not targeting a valid controller
     */
    private static int set(CommandSourceStack source, int capacity) throws CommandSyntaxException {
        var controller = ChannelCommands.requireController(source);
        var service = controller.getGrid().getService(PatternSlotService.class);
        service.setCapacity(controller, capacity);
        report(source, service.effectiveBonus());
        return 1;
    }

    /**
     * Reports the effective capacity and its signed adjustment.
     *
     * @param source recipient of the command feedback
     * @param bonus  adjustment from the base slot count
     * @return total unlocked slots
     */
    private static int report(CommandSourceStack source, int bonus) {
        int capacity = PatternSlotRange.ROW_SIZE + bonus;
        var change = bonus >= 0 ? "+" + bonus : Integer.toString(bonus);
        source.sendSuccess(() -> Component.translatable("aeupgrade.patternslots.result", capacity, change), false);
        return capacity;
    }
}
