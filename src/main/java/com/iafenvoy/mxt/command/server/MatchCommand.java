package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.api.AlchemyHeatSource;
import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.data.alchemy.HeatSource;
import com.iafenvoy.mxt.data.artifact.Artifact;
import com.iafenvoy.mxt.registry.cache.RegistryCaches;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.runtime.creature.CreatureProfileService;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.saturn.util.HolderHelper;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Block;

import java.util.Locale;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class MatchCommand {
    private MatchCommand() {
    }

    public static LiteralArgumentBuilder<CommandSourceStack> build() {
        return literal("match").requires(ServerCommandManager::mayChange)
                .then(literal("creature").then(argument("target", EntityArgument.entity())
                        .executes(ctx -> creature(ctx.getSource(), EntityArgument.getEntity(ctx, "target")))))
                .then(literal("artifact").executes(ctx -> artifact(ctx.getSource())))
                .then(literal("heat_source").then(argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> heat(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos")))))
                .then(literal("aura_zone").then(argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> aura(ctx.getSource(), BlockPosArgument.getLoadedBlockPos(ctx, "pos")))));
    }

    private static int creature(CommandSourceStack source, Entity target) {
        if (!(target instanceof Mob mob) || !(mob.level() instanceof ServerLevel level)) {
            source.sendFailure(Component.translatable("command.mxt.match.creature.mob_required"));
            return 0;
        }
        CreatureProfileService.Inspection result = CreatureProfileService.inspect(level, mob);
        for (CreatureProfileService.Rejection rejected : result.rejected())
            source.sendSuccess(() -> Component.translatable("command.mxt.match.creature.rejected",
                    HolderHelper.id(rejected.profile()).toString(), rejected.profile().value().priority(),
                    Component.translatable("command.mxt.match.creature." + rejected.reason().name().toLowerCase(Locale.ROOT))), false);
        return result.winner() == null ? missing(source) : winner(source, result.winner(), result.winner().value().priority());
    }

    private static int artifact(CommandSourceStack source) throws CommandSyntaxException {
        Holder<Artifact> match = ArtifactService.definition(source.registryAccess(), source.getPlayerOrException().getMainHandItem()).orElse(null);
        return match == null ? missing(source) : winner(source, match, match.value().priority());
    }

    private static int heat(CommandSourceStack source, BlockPos pos) {
        Block block = source.getLevel().getBlockState(pos).getBlock();
        if (block instanceof AlchemyHeatSource) {
            source.sendSuccess(() -> Component.translatable("command.mxt.match.heat_source.dynamic"), false);
            return 1;
        }
        Holder<HeatSource> match = RegistryCaches.HEAT_SOURCE.definition(block);
        return match == null ? missing(source) : winner(source, match, match.value().priority());
    }

    private static int aura(CommandSourceStack source, BlockPos pos) {
        AuraResult aura = AuraService.getPositionAura(source.getLevel(), pos);
        if (aura.source() == null) return missing(source);
        source.sendSuccess(() -> Component.translatable("command.mxt.match.aura_zone.selected",
                HolderHelper.id(aura.source()).toString(), aura.source().value().priority(),
                Component.translatable("command.mxt.aura.source_kind." + aura.sourceKind().name().toLowerCase(Locale.ROOT))), false);
        return 1;
    }

    private static int winner(CommandSourceStack source, Holder<?> match, int priority) {
        source.sendSuccess(() -> Component.translatable("command.mxt.match.selected", HolderHelper.id(match).toString(), priority), false);
        return 1;
    }

    private static int missing(CommandSourceStack source) {
        source.sendSuccess(() -> Component.translatable("command.mxt.match.missing"), false);
        return 0;
    }
}
