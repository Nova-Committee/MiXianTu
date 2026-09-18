package com.iafenvoy.mxt.command;

import com.iafenvoy.mxt.data.Talisman;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.item.TalismanComponent;
import com.iafenvoy.mxt.data.item.TalismanComponent.TriggerMode;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.spirit.ItemAuraAccess;
import com.iafenvoy.mxt.runtime.talisman.TalismanService;
import com.iafenvoy.mxt.util.DefinitionText;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /talisman} subtree: hands the caller a carrier, which is the one thing a data pack cannot write
 * into a stack by itself. What is inscribed on a carrier is a component, so an operator wanting one to test
 * with would otherwise have to write component syntax; here they name the definitions instead.
 * <p>
 * {@code blank} is the empty carrier and {@code give} inscribes one or more definitions on a fresh carrier -
 * comma separated, because a carrier holds a list and an operator usually wants the multi-inscription case.
 * {@code charged} additionally pours the bill in, which is what makes the result fire on the next click: a
 * carrier is only loaded by the aura its own definitions bill, and an operator testing an invocation should not
 * have to go and find the matching aura first. Everything here asks for the gamemaster permission.
 */
public final class TalismanCommand {
    public static final LiteralArgumentBuilder<CommandSourceStack> ROOT = build();

    private TalismanCommand() {
    }

    private static LiteralArgumentBuilder<CommandSourceStack> build() {
        // Every optional node is one readable level, and each builder is attached to exactly one parent - which
        // is why "charged" is built once per branch: brigadier reads a child builder at most once, so sharing one
        // instance between two parents loses whichever parent is reached second.
        LiteralArgumentBuilder<CommandSourceStack> count = literal("count")
                .then(argument("count", IntegerArgumentType.integer(1, 64))
                        .executes(ctx -> give(ctx, false, TriggerMode.FIRE))
                        .then(charged()));
        // The storing mode has nothing charged to start from: a stored carrier is poured by hand, which is the
        // whole point of the mode, so the pairing is not offered.
        LiteralArgumentBuilder<CommandSourceStack> stored = literal("stored")
                .executes(ctx -> give(ctx, false, TriggerMode.STORE))
                .then(literal("count")
                        .then(argument("count", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> give(ctx, false, TriggerMode.STORE))));
        LiteralArgumentBuilder<CommandSourceStack> give = literal("give")
                .then(argument("talismans", StringArgumentType.greedyString())
                        .suggests(TalismanCommand::suggestTalismans)
                        .executes(ctx -> give(ctx, false, TriggerMode.FIRE))
                        .then(count)
                        .then(stored));
        return literal("talisman")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .executes(TalismanCommand::blank)
                .then(literal("blank")
                        .executes(TalismanCommand::blank)
                        .then(literal("count")
                                .then(argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(TalismanCommand::blank))))
                .then(give);
    }

    /**
     * The {@code charged} node, built fresh for each branch that offers it.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> charged() {
        return literal("charged").executes(ctx -> give(ctx, true, TriggerMode.FIRE));
    }

    /**
     * Suggests every loaded talisman definition, so {@code give} offers what the server actually has rather
     * than a hand-written list. The argument is greedy because it is a comma-separated list, so the whole id
     * list is offered as one completion.
     */
    private static CompletableFuture<Suggestions> suggestTalismans(CommandContext<CommandSourceStack> ctx,
                                                                   SuggestionsBuilder builder) {
        return SharedSuggestionProvider.suggest(
                MxtDatapackRegistries.holders(ctx.getSource().getServer().registryAccess(), MxtResourceKeys.TALISMAN)
                        .map(holder -> holder.key().identifier().toString()).sorted().toList(), builder);
    }

    /**
     * An empty carrier: nothing is written on it, so nothing is billed and no aura is needed.
     */
    private static int blank(CommandContext<CommandSourceStack> ctx) {
        ServerPlayer player = ctx.getSource().getPlayer();
        if (player == null) {
            ctx.getSource().sendFailure(Component.translatable("command.mxt.requires_player"));
            return 0;
        }
        ItemStack stack = new ItemStack(MxtItems.TALISMAN.get(), count(ctx));
        player.getInventory().placeItemBackInInventory(stack);
        ctx.getSource().sendSuccess(() -> Component.translatable("command.mxt.talisman.blank", stack.getCount()), true);
        return stack.getCount();
    }

    private static int give(CommandContext<CommandSourceStack> ctx, boolean charged, TriggerMode mode) {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.mxt.requires_player"));
            return 0;
        }
        List<Holder<Talisman>> inscribed = new ArrayList<>();
        List<String> unknown = new ArrayList<>();
        for (String raw : StringArgumentType.getString(ctx, "talismans").split(",")) {
            String trimmed = raw.trim();
            if (trimmed.isEmpty()) continue;
            Identifier id = Identifier.tryParse(trimmed);
            Reference<Talisman> holder = id == null ? null
                    : MxtDatapackRegistries.holder(MxtResourceKeys.TALISMAN, id).orElse(null);
            if (holder == null) unknown.add(trimmed);
            else inscribed.add(holder);
        }
        if (!unknown.isEmpty()) {
            source.sendFailure(Component.translatable("command.mxt.talisman.unknown", String.join(", ", unknown)));
            return 0;
        }
        if (inscribed.isEmpty()) {
            source.sendFailure(Component.translatable("command.mxt.talisman.empty_inscription"));
            return 0;
        }
        ItemStack stack = new ItemStack(MxtItems.TALISMAN.get(), count(ctx));
        stack.set(MxtDataComponents.TALISMAN, new TalismanComponent(List.copyOf(inscribed), mode));
        if (charged) charge(player, stack);
        player.getInventory().placeItemBackInInventory(stack);
        source.sendSuccess(() -> Component.translatable(
                "command.mxt.talisman.given",
                stack.getCount(), stack.getDisplayName(), inscriptions(inscribed),
                Component.translatable("tooltip.mxt.talisman.mode." + mode.key()),
                Component.translatable(charged ? "command.mxt.talisman.charged"
                        : "command.mxt.talisman.not_charged")), true);
        return stack.getCount();
    }

    /**
     * The inscriptions as a readable list, shown with the confirmation: which definitions were written is the
     * thing an operator is about to check anyway.
     */
    private static Component inscriptions(List<Holder<Talisman>> inscribed) {
        Component line = Component.empty();
        for (int index = 0; index < inscribed.size(); index++) {
            if (index > 0) line = line.copy().append(Component.literal(", "));
            line = line.copy().append(DefinitionText.name(inscribed.get(index), "talisman"));
        }
        return line;
    }

    /**
     * Pours the whole bill in, one aura at a time. The capacity comes from the inscriptions themselves, so a
     * carrier is filled to exactly what its invocation costs and nothing has to be configured twice.
     */
    private static void charge(ServerPlayer player, ItemStack stack) {
        if (!(stack.getItem() instanceof ItemAuraAccess access)) return;
        for (Entry<Holder<Aura>, Integer> entry : TalismanService.bill(stack).entrySet()) {
            int units = entry.getValue();
            if (units > 0) access.insert(player, stack, entry.getKey(), units, false);
        }
    }

    /**
     * How many carriers this node hands out. The argument is optional, so a node reached without it is not an
     * error - which is the one way to tell, since {@code CommandContext} offers no "was it given" question and
     * {@code IntegerArgumentType.getInteger} has no defaulted overload.
     */
    private static int count(CommandContext<CommandSourceStack> ctx) {
        try {
            return IntegerArgumentType.getInteger(ctx, "count");
        } catch (IllegalArgumentException missing) {
            return 1;
        }
    }
}
