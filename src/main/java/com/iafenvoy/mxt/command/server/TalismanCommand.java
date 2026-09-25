package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.api.ItemAuraAccess;
import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.data.Talisman;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.item.TalismanComponent;
import com.iafenvoy.mxt.data.item.TalismanComponent.TriggerMode;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtItems;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.talisman.TalismanService;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map.Entry;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /talisman} subtree: hands the caller a carrier, which is the one thing a data pack cannot write into
 * a stack by itself. {@code give} inscribes one definition on a fresh carrier; {@code charged} additionally pours
 * the bill in, since a carrier is only loaded by the aura its own definitions bill. Everything here asks for the
 * gamemaster permission.
 */
public final class TalismanCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        // "charged" is built once per branch: brigadier reads a child builder at most once, so sharing one
        // instance between two parents loses whichever parent is reached second.
        LiteralArgumentBuilder<CommandSourceStack> count = literal("count")
                .then(argument("count", IntegerArgumentType.integer(1, 64))
                        .executes(ctx -> give(ctx, false, TriggerMode.FIRE))
                        .then(charged()));
        // stored has nothing charged to start from: a stored carrier is poured by hand, which is the point of the mode.
        LiteralArgumentBuilder<CommandSourceStack> stored = literal("stored")
                .executes(ctx -> give(ctx, false, TriggerMode.STORE))
                .then(literal("count")
                        .then(argument("count", IntegerArgumentType.integer(1, 64))
                                .executes(ctx -> give(ctx, false, TriggerMode.STORE))));
        LiteralArgumentBuilder<CommandSourceStack> give = literal("give")
                .then(argument("talisman", ResourceArgument.resource(context, MxtResourceKeys.TALISMAN))
                        .executes(ctx -> give(ctx, false, TriggerMode.FIRE))
                        .then(count)
                        .then(stored));
        return literal("talisman")
                .requires(ServerCommandManager::mayChange)
                .executes(TalismanCommand::blank)
                .then(literal("blank")
                        .executes(TalismanCommand::blank)
                        .then(literal("count")
                                .then(argument("count", IntegerArgumentType.integer(1, 64))
                                        .executes(TalismanCommand::blank))))
                .then(give);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> charged() {
        return literal("charged").executes(ctx -> give(ctx, true, TriggerMode.FIRE));
    }

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

    private static int give(CommandContext<CommandSourceStack> ctx, boolean charged, TriggerMode mode) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        ServerPlayer player = source.getPlayer();
        if (player == null) {
            source.sendFailure(Component.translatable("command.mxt.requires_player"));
            return 0;
        }
        Reference<Talisman> inscribed = ResourceArgument.getResource(ctx, "talisman", MxtResourceKeys.TALISMAN);
        if (MxtDatapackRegistries.isDisabled(MxtResourceKeys.TALISMAN, inscribed)) {
            source.sendFailure(Component.translatable("command.mxt.talisman.unknown", HolderHelper.id(inscribed).toString()));
            return 0;
        }
        int count = count(ctx);
        ItemStack template = new ItemStack(MxtItems.TALISMAN.get());
        template.set(MxtDataComponents.TALISMAN, new TalismanComponent(List.of(inscribed), mode));
        // The declared wear lands on the stack here, because a carrier handed out by the command is the only one
        // the framework itself makes: the bar is on the item before it is ever used.
        TalismanService.applyDurability(template);
        if (template.has(DataComponents.MAX_DAMAGE)) {
            // A carrier with wear is not stackable - vanilla refuses a stack that is both damageable and
            // stackable - so a count of them is that many single carriers instead of one stack of them.
            for (int i = 0; i < count; i++) hand(player, template.copy(), charged);
        } else {
            template.setCount(count);
            hand(player, template, charged);
        }
        source.sendSuccess(() -> Component.translatable(
                "command.mxt.talisman.given",
                count, template.getDisplayName(), DefinitionText.name(inscribed, "talisman"),
                Component.translatable("tooltip.mxt.talisman.mode." + mode.key()),
                Component.translatable(charged ? "command.mxt.talisman.charged"
                        : "command.mxt.talisman.not_charged")), true);
        return count;
    }

    private static void hand(ServerPlayer player, ItemStack stack, boolean charged) {
        if (charged) charge(player, stack);
        player.getInventory().placeItemBackInInventory(stack);
    }

    // The capacity comes from the inscriptions themselves, so the carrier is filled to exactly what its
    // invocation costs and nothing has to be configured twice.
    private static void charge(ServerPlayer player, ItemStack stack) {
        if (!(stack.getItem() instanceof ItemAuraAccess access)) return;
        for (Entry<Holder<Aura>, Integer> entry : TalismanService.bill(stack).entrySet()) {
            int units = entry.getValue();
            if (units > 0) access.insert(player, stack, entry.getKey(), units, false);
        }
    }

    // The "count" argument is optional and CommandContext offers no "was it given" question, so the missing
    // argument is caught rather than checked for: IntegerArgumentType.getInteger has no defaulted overload.
    private static int count(CommandContext<CommandSourceStack> ctx) {
        try {
            return IntegerArgumentType.getInteger(ctx, "count");
        } catch (IllegalArgumentException missing) {
            return 1;
        }
    }
}
