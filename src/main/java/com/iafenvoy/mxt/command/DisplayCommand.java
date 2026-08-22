package com.iafenvoy.mxt.command;

import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent.ShowItem;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.EnumSet;
import java.util.List;
import java.util.stream.Stream;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/** Displays one of the executing player's equipped item stacks in chat. */
public final class DisplayCommand {
    private static final EnumSet<EquipmentSlot> DISPLAY_SLOTS = EnumSet.of(
            EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND, EquipmentSlot.HEAD,
            EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET);
    private static final List<String> SLOT_NAMES = DISPLAY_SLOTS.stream().map(EquipmentSlot::getName).sorted().toList();

    private DisplayCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(literal("display")
                .requires(source -> source.getPlayer() != null)
                .executes(ctx -> display(ctx, EquipmentSlot.MAINHAND))
                .then(argument("target_or_slot", StringArgumentType.word())
                        .suggests((ctx, builder) -> SharedSuggestionProvider.suggest(Stream.concat(
                                SLOT_NAMES.stream(), ctx.getSource().getServer().getPlayerList().getPlayers().stream()
                                        .map(player -> player.getGameProfile().name())).sorted().toList(), builder))
                        .executes(DisplayCommand::displayTargetOrSlot)
                        .then(argument("slot", StringArgumentType.word())
                                .suggests((_, builder) -> SharedSuggestionProvider.suggest(SLOT_NAMES, builder))
                                .executes(DisplayCommand::displayTargetSlot))));
    }

    private static int displayTargetOrSlot(CommandContext<CommandSourceStack> ctx) {
        String value = StringArgumentType.getString(ctx, "target_or_slot");
        EquipmentSlot slot = parseSlot(value);
        if (slot != null) return display(ctx, slot);
        ServerPlayer target = target(ctx, value);
        return target == null ? 0 : display(ctx, target, EquipmentSlot.MAINHAND);
    }

    private static int displayTargetSlot(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer target = target(ctx, StringArgumentType.getString(ctx, "target_or_slot"));
        if (target == null) return 0;
        String name = StringArgumentType.getString(ctx, "slot");
        EquipmentSlot slot = parseSlot(name);
        if (slot == null) {
            ctx.getSource().sendFailure(Component.translatable("command.mxt.display.unknown_slot", name, String.join(", ", SLOT_NAMES)));
            return 0;
        }
        return display(ctx, target, slot);
    }

    private static EquipmentSlot parseSlot(String name) {
        try {
            EquipmentSlot slot = EquipmentSlot.byName(name);
            return DISPLAY_SLOTS.contains(slot) ? slot : null;
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    private static ServerPlayer target(CommandContext<CommandSourceStack> ctx, String name) {
        ServerPlayer target = ctx.getSource().getServer().getPlayerList().getPlayerByName(name);
        if (target != null && target.getGameProfile().name().equals(name)) return target;
        ctx.getSource().sendFailure(Component.translatable("command.mxt.display.unknown_player", name));
        return null;
    }

    private static int display(CommandContext<CommandSourceStack> ctx, EquipmentSlot slot) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.translatable("command.mxt.requires_player"));
            return 0;
        }
        ItemStack item = player.getItemBySlot(slot);
        Component slotName = Component.translatable("command.mxt.display.slot." + slot.getName());
        if (item.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.mxt.display.empty", slotName));
            return 0;
        }
        Component displayItem = displayItem(item);
        ctx.getSource().getServer().getPlayerList().broadcastSystemMessage(
                Component.translatable("command.mxt.display.success", player.getDisplayName(), displayItem, slotName), false);
        return Command.SINGLE_SUCCESS;
    }

    private static int display(CommandContext<CommandSourceStack> ctx, ServerPlayer target, EquipmentSlot slot) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.translatable("command.mxt.requires_player"));
            return 0;
        }
        ItemStack item = player.getItemBySlot(slot);
        Component slotName = Component.translatable("command.mxt.display.slot." + slot.getName());
        if (item.isEmpty()) {
            ctx.getSource().sendFailure(Component.translatable("command.mxt.display.empty", slotName));
            return 0;
        }
        Component displayItem = displayItem(item);
        if (target == player) {
            player.sendSystemMessage(Component.translatable("command.mxt.display.private_self", displayItem, slotName));
        } else {
            target.sendSystemMessage(Component.translatable("command.mxt.display.private_received", player.getDisplayName(), displayItem, slotName));
            player.sendSystemMessage(Component.translatable("command.mxt.display.private_sent", target.getDisplayName(), displayItem, slotName));
        }
        return Command.SINGLE_SUCCESS;
    }

    private static Component displayItem(ItemStack item) {
        return item.getDisplayName().copy().withStyle(style -> style.withHoverEvent(new ShowItem(ItemStackTemplate.fromNonEmptyStack(item))));
    }
}
