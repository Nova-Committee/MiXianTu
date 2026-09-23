package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.command.ChainReport;
import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.data.quality.ItemQuality;
import com.iafenvoy.mxt.data.quality.QualityChain;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.item.ItemQualityService;
import com.iafenvoy.mxt.runtime.item.QualityChainService;
import com.iafenvoy.mxt.runtime.item.QualityUpgradeService;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.HolderHelper;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder;
import net.minecraft.core.Holder.Reference;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /quality} command; also reachable as {@code /mxt quality}. It works on the item a target is holding,
 * which is where a quality actually lives: {@code set} writes the override component, {@code clear} takes that
 * override off so the item falls back to whatever its definition declares, {@code upgrade} climbs the ladder
 * its binding declares, paying that step's own costs through the shared transaction, and {@code chain} prints the
 * ladders a named tier is a rung of.
 */
public final class QualityCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return literal("quality")
                .executes(ctx -> get(ctx.getSource(), ctx.getSource().getPlayer()))
                .then(literal("get")
                        .executes(ctx -> get(ctx.getSource(), ctx.getSource().getPlayer()))
                        .then(argument("target", EntityArgument.player()).requires(ServerCommandManager::mayChange)
                                .executes(ctx -> get(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))))
                .then(literal("set").requires(ServerCommandManager::mayChange)
                        .then(argument("targets", EntityArgument.entities())
                                .then(argument("quality", ResourceArgument.resource(context, MxtResourceKeys.ITEM_QUALITY))
                                        .executes(QualityCommand::set))))
                .then(literal("clear").requires(ServerCommandManager::mayChange)
                        .then(argument("targets", EntityArgument.entities())
                                .executes(QualityCommand::clear)))
                .then(literal("upgrade").requires(ServerCommandManager::mayChange)
                        .then(argument("targets", EntityArgument.entities())
                                .executes(QualityCommand::upgrade)))
                .then(literal("chain")
                        .then(argument("quality", ResourceArgument.resource(context, MxtResourceKeys.ITEM_QUALITY))
                                .executes(QualityCommand::chain)));
    }

    private static int get(CommandSourceStack source, @Nullable LivingEntity target) {
        if (target == null) {
            source.sendFailure(Component.translatable("command.mxt.requires_player"));
            return 0;
        }
        ItemStack stack = target.getMainHandItem();
        if (stack.isEmpty()) {
            source.sendFailure(Component.translatable("command.mxt.quality.no_item"));
            return 0;
        }
        Optional<Holder<ItemQuality>> quality = ItemQualityService.find(target.level().registryAccess(), stack);
        if (quality.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.mxt.quality.none", stack.getHoverName()), false);
            return 1;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.quality.get", stack.getHoverName(),
                ItemQualityService.coloredName(quality.orElseThrow(), DefinitionText.name(quality.orElseThrow()))), false);
        return 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        // The raw entry, not an enabled-only lookup: a disabled tier may still be written, and the gate that
        // refuses to *use* such an item reads the flag itself.
        Reference<ItemQuality> quality = ResourceArgument.getResource(ctx, "quality", MxtResourceKeys.ITEM_QUALITY);
        Collection<? extends Entity> targets = EntityArgument.getEntities(ctx, "targets");
        int changed = 0;
        for (Entity target : targets) {
            if (!(target instanceof LivingEntity living)) {
                source.sendFailure(Component.translatable("command.mxt.quality.not_living", target.getDisplayName()));
                continue;
            }
            ItemStack stack = living.getMainHandItem();
            if (stack.isEmpty()) {
                source.sendFailure(Component.translatable("command.mxt.quality.no_item"));
                continue;
            }
            ItemQualityService.set(stack, quality);
            changed++;
            source.sendSuccess(() -> Component.translatable("command.mxt.quality.set", stack.getHoverName(),
                    ItemQualityService.coloredName(quality, DefinitionText.name(quality))), true);
        }
        return changed;
    }

    private static int clear(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        int cleared = 0;
        for (Entity target : EntityArgument.getEntities(ctx, "targets")) {
            if (!(target instanceof LivingEntity living)) continue;
            ItemStack stack = living.getMainHandItem();
            if (stack.isEmpty()) {
                source.sendFailure(Component.translatable("command.mxt.quality.no_item"));
                continue;
            }
            if (!ItemQualityService.hasOverride(stack)) {
                source.sendFailure(Component.translatable("command.mxt.quality.clear_none", stack.getHoverName()));
                continue;
            }
            ItemQualityService.clear(stack);
            cleared++;
            source.sendSuccess(() -> Component.translatable("command.mxt.quality.cleared", stack.getHoverName()), true);
        }
        return cleared;
    }

    private static int upgrade(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        int upgraded = 0;
        for (Entity target : EntityArgument.getEntities(ctx, "targets")) {
            if (!(target instanceof LivingEntity living)) continue;
            ItemStack stack = living.getMainHandItem();
            if (stack.isEmpty()) {
                source.sendFailure(Component.translatable("command.mxt.quality.no_item"));
                continue;
            }
            QualityUpgradeService.Result result = QualityUpgradeService.upgrade(living, stack);
            if (result.changed()) {
                upgraded++;
                source.sendSuccess(() -> Component.translatable("command.mxt.quality.upgraded", stack.getHoverName(),
                        ItemQualityService.coloredName(result.to(), DefinitionText.name(result.to()))), true);
            } else {
                source.sendFailure(Component.translatable("command.mxt.quality.upgrade_failed", stack.getHoverName(),
                        Component.translatable("command.mxt.quality.failure." + result.failure().name().toLowerCase(Locale.ROOT))));
            }
        }
        return upgraded;
    }

    private static int chain(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        CommandSourceStack source = ctx.getSource();
        Reference<ItemQuality> quality = ResourceArgument.getResource(ctx, "quality", MxtResourceKeys.ITEM_QUALITY);
        // A disabled tier is no more usable than a missing one, which is the rule every other registry argument
        // follows; the chains themselves are the enabled ones, exactly as the upgrade service reads them.
        if (MxtDatapackRegistries.isDisabled(MxtResourceKeys.ITEM_QUALITY, quality)) {
            source.sendFailure(Component.translatable("command.mxt.quality.chain.disabled", HolderHelper.id(quality).toString()));
            return 0;
        }
        List<Holder<QualityChain>> chains = QualityChainService.chainsOf(source.getServer().registryAccess(), quality);
        if (chains.isEmpty()) {
            source.sendFailure(Component.translatable("command.mxt.quality.chain.none", HolderHelper.id(quality).toString()));
            return 0;
        }
        // One tier may sit on several ladders, so every one of them gets a line instead of the command picking one.
        for (Holder<QualityChain> chain : chains) {
            Component line = ChainReport.line(chain.value().tiers().stream().map(DefinitionText::name).toList(),
                    chain.value().indexOf(quality));
            source.sendSuccess(() -> Component.translatable("command.mxt.quality.chain",
                    HolderHelper.id(chain).toString(), line), false);
        }
        return chains.size();
    }
}
