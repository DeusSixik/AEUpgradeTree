package dev.sixik.aeupgrade.command;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import dev.sixik.aeupgrade.stock.InterfaceSlotRange;
import dev.sixik.aeupgrade.stock.InterfaceSlotService;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;

/**
 * Operator controls for the number of ME Interface stocking pairs in the selected grid.
 */
final class InterfaceSlotCommands {
    /**
     * Keeps command registration static.
     */
    private InterfaceSlotCommands() {
    }

    /**
     * @return command branch for reading, setting, adding and removing interface slot pairs
     */
    static LiteralArgumentBuilder<CommandSourceStack> command() {
        return literal("interfaceslots")
                .then(literal("get").executes(context -> run(context.getSource(), 0, false)))
                .then(literal("set").then(argument("slots", IntegerArgumentType.integer(0, InterfaceSlotRange.MAX_CAPACITY))
                        .executes(context -> run(context.getSource(), IntegerArgumentType.getInteger(context, "slots"), true))))
                .then(literal("add").then(argument("amount", IntegerArgumentType.integer(1))
                        .executes(context -> run(context.getSource(), IntegerArgumentType.getInteger(context, "amount"), false))))
                .then(literal("remove").then(argument("amount", IntegerArgumentType.integer(1))
                        .executes(context -> run(context.getSource(), -IntegerArgumentType.getInteger(context, "amount"), false))));
    }

    /**
     * Applies an absolute or relative change and reports the selected grid's effective capacity.
     *
     * @param source   operator aiming at an ME controller
     * @param amount   absolute slot count, signed delta, or zero for a read
     * @param absolute true when setting an absolute capacity
     * @return effective capacity on reads and one on writes
     * @throws CommandSyntaxException if there is no valid controller under the crosshair
     */
    private static int run(CommandSourceStack source, int amount, boolean absolute) throws CommandSyntaxException {
        var controller = ChannelCommands.requireController(source);
        var service = controller.getGrid().getService(InterfaceSlotService.class);
        boolean read = !absolute && amount == 0;
        int capacity = read ? service.capacity() : service.setCapacity(controller,
                absolute ? amount : InterfaceSlotRange.adjust(service.capacity(), amount));
        source.sendSuccess(() -> Component.translatable("aeupgrade.interfaceslots.result", capacity), false);
        return read ? capacity : 1;
    }
}
