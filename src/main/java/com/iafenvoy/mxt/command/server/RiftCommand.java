package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.command.RegistryReferences;
import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.item.RiftComponent;
import com.iafenvoy.mxt.item.RiftAnchorItem;
import com.iafenvoy.mxt.item.block.entity.RiftBlockEntity;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtBlocks;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.cultivation.CultivationService;
import com.iafenvoy.mxt.runtime.curse.CurseService;
import com.iafenvoy.mxt.runtime.rift.RiftColors;
import com.iafenvoy.mxt.runtime.rift.RiftConnections;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.coordinates.BlockPosArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder.Reference;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /curse} command; also reachable as {@code /mxt curse}. Reading the executing player's own curses needs
 * no permission, while changing anybody's - applying, removing or cleansing - keeps its operator requirement in
 * both positions. It is an administrator's mirror of the transactions content uses, going through
 * {@link CurseService}, so a curse applied here obeys its own condition and stacking.
 */
public final class RiftCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return literal("rift")
                .requires(ServerCommandManager::mayChange)
                .then(literal("info").then(argument("pos", BlockPosArgument.blockPos())
                        .executes(ctx -> riftInfo(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos")))))
                .then(literal("target").then(argument("pos", BlockPosArgument.blockPos())
                        .then(argument("dimension", DimensionArgument.dimension())
                                .executes(ctx -> setRiftTarget(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos"), RegistryReferences.dimension(ctx, "dimension"))))))
                .then(literal("color").then(argument("pos", BlockPosArgument.blockPos())
                        .then(argument("color", StringArgumentType.word())
                                .executes(ctx -> setRiftColor(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos"), StringArgumentType.getString(ctx, "color"))))))
                .then(literal("place").then(argument("pos", BlockPosArgument.blockPos())
                        .then(argument("dimension", DimensionArgument.dimension())
                                .executes(ctx -> placeRift(ctx.getSource(), BlockPosArgument.getBlockPos(ctx, "pos"), RegistryReferences.dimension(ctx, "dimension"))))))
                .then(literal("bind").then(argument("dimension", DimensionArgument.dimension())
                        .executes(ctx -> bindRiftAnchor(ctx.getSource(), RegistryReferences.dimension(ctx, "dimension"), null))
                        .then(argument("color", StringArgumentType.word())
                                .executes(ctx -> bindRiftAnchor(ctx.getSource(), RegistryReferences.dimension(ctx, "dimension"), StringArgumentType.getString(ctx, "color"))))));
    }

    @Nullable
    private static RiftBlockEntity riftAt(CommandSourceStack source, BlockPos pos) {
        if (source.getLevel().getBlockEntity(pos) instanceof RiftBlockEntity rift) return rift;
        source.sendFailure(Component.translatable("command.mxt.rift.missing", formatPos(pos)));
        return null;
    }

    private static int riftInfo(CommandSourceStack source, BlockPos pos) {
        RiftBlockEntity rift = riftAt(source, pos);
        if (rift == null) return 0;
        ServerLevel level = source.getLevel();
        List<BlockPos> links = RiftConnections.connected(level, pos);
        int triangles = RiftConnections.loops(pos, links).size();
        source.sendSuccess(() -> Component.translatable("command.mxt.rift.info", formatPos(pos)), false);
        source.sendSuccess(() -> Component.translatable("command.mxt.rift.info.target", rift.target().toString()), false);
        source.sendSuccess(() -> Component.translatable("command.mxt.rift.info.color", RiftColors.format(RiftColors.resolve(rift)),
                Component.translatable(rift.color() == RiftColors.AUTO
                        ? "command.mxt.rift.color.auto" : "command.mxt.rift.color.override")), false);
        source.sendSuccess(() -> Component.translatable("command.mxt.rift.info.shape", links.size(), triangles,
                links.isEmpty() ? 1 : RiftConnections.componentSize(level, pos)), false);
        source.sendSuccess(() -> Component.translatable("command.mxt.rift.info.isolated",
                Component.translatable(RiftConnections.isolated(level, pos)
                        ? "command.mxt.rift.info.isolated.yes" : "command.mxt.rift.info.isolated.no")), false);
        return 1;
    }

    private static int setRiftTarget(CommandSourceStack source, BlockPos pos, Identifier dimension) {
        RiftBlockEntity rift = riftAt(source, pos);
        if (rift == null) return 0;
        rift.setTarget(dimension);
        source.sendSuccess(() -> Component.translatable("block.mxt.rift.retargeted", dimension.toString()), true);
        return 1;
    }

    private static int setRiftColor(CommandSourceStack source, BlockPos pos, String color) {
        RiftBlockEntity rift = riftAt(source, pos);
        if (rift == null) return 0;
        if (color.equalsIgnoreCase("auto")) {
            rift.setColor(RiftColors.AUTO);
            source.sendSuccess(() -> Component.translatable("command.mxt.rift.color.reset"), true);
            return 1;
        }
        int parsed = RiftColors.parse(color);
        if (parsed == RiftColors.AUTO) {
            source.sendFailure(Component.translatable("command.mxt.rift.color.invalid", color));
            return 0;
        }
        rift.setColor(parsed);
        source.sendSuccess(() -> Component.translatable("block.mxt.rift.recolored", RiftColors.format(parsed)), true);
        return 1;
    }

    private static int placeRift(CommandSourceStack source, BlockPos pos, Identifier dimension) {
        ServerLevel level = source.getLevel();
        if (level.isOutsideBuildHeight(pos) || !level.getBlockState(pos).canBeReplaced()) {
            source.sendFailure(Component.translatable("command.mxt.rift.place.blocked", formatPos(pos)));
            return 0;
        }
        level.setBlock(pos, MxtBlocks.RIFT.get().defaultBlockState(), Block.UPDATE_ALL);
        if (level.getBlockEntity(pos) instanceof RiftBlockEntity rift)
            rift.configure(dimension, RiftColors.AUTO);
        source.sendSuccess(() -> Component.translatable("command.mxt.rift.place.done", formatPos(pos), dimension.toString()), true);
        return 1;
    }

    private static int bindRiftAnchor(CommandSourceStack source, Identifier dimension, @Nullable String color) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof RiftAnchorItem)) {
            source.sendFailure(Component.translatable("command.mxt.rift.bind.missing"));
            return 0;
        }
        int parsed = RiftColors.AUTO;
        if (color != null && !color.equalsIgnoreCase("auto")) {
            parsed = RiftColors.parse(color);
            if (parsed == RiftColors.AUTO) {
                source.sendFailure(Component.translatable("command.mxt.rift.color.invalid", color));
                return 0;
            }
        }
        stack.set(MxtDataComponents.RIFT, new RiftComponent(dimension, parsed));
        source.sendSuccess(() -> Component.translatable("command.mxt.rift.bind.done", dimension.toString()), true);
        return 1;
    }

    private static String formatPos(BlockPos pos) {
        return "%d %d %d".formatted(pos.getX(), pos.getY(), pos.getZ());
    }
}
