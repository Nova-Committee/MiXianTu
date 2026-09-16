package com.iafenvoy.mxt.command;

import com.iafenvoy.mxt.registry.MxtEntityTypes;
import com.iafenvoy.mxt.runtime.lightning.ColoredLightningBolt;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.DynamicCommandExceptionType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /mxt lightning} subtree: strike a coloured bolt right now. Also a top-level {@code /lightning}
 * when the server option allows it. The look is an ordered chain of optional nodes, so tab completion walks
 * the caller through whatever is left rather than demanding every field up front.
 */
public final class LightningCommand {
    private static final DynamicCommandExceptionType INVALID_COLOR = new DynamicCommandExceptionType(
            value -> Component.translatable("command.mxt.lightning.invalid_color", value));
    /** The colours offered for completion; any six hexadecimal digits are accepted. */
    private static final List<String> COLORS = List.of("737380", "FFFFFF", "000000", "66CCFF", "7A5CFF", "FF4444", "44FF88", "FFCC00");
    private static final double DEFAULT_DAMAGE = 5.0D;

    public static final LiteralArgumentBuilder<CommandSourceStack> ROOT = build();

    /**
     * Built bottom-up so every optional node is one readable level instead of a closing-parenthesis cliff. Each
     * builder is attached to exactly one parent; {@code CommandManager} building the tree twice for the two
     * roots is what makes the alias a separate set of nodes rather than a shared one.
     */
    private static LiteralArgumentBuilder<CommandSourceStack> build() {
        LiteralArgumentBuilder<CommandSourceStack> visualOnly = literal("visual_only")
                .executes(ctx -> strike(ctx, position(ctx), color(ctx), alpha(ctx), thickness(ctx), damage(ctx), true));
        LiteralArgumentBuilder<CommandSourceStack> damage = literal("damage")
                .then(argument("damage", DoubleArgumentType.doubleArg(0.0D))
                        .executes(ctx -> strike(ctx, position(ctx), color(ctx), alpha(ctx), thickness(ctx), damage(ctx), false))
                        .then(visualOnly));
        LiteralArgumentBuilder<CommandSourceStack> thickness = literal("thickness")
                .then(argument("thickness", DoubleArgumentType.doubleArg(
                                ColoredLightningBolt.MIN_THICKNESS, ColoredLightningBolt.MAX_THICKNESS))
                        .executes(ctx -> strike(ctx, position(ctx), color(ctx), alpha(ctx), thickness(ctx), DEFAULT_DAMAGE, false))
                        .then(damage));
        LiteralArgumentBuilder<CommandSourceStack> alpha = literal("alpha")
                .then(argument("alpha", DoubleArgumentType.doubleArg(0.0D, 1.0D))
                        .executes(ctx -> strike(ctx, position(ctx), color(ctx), alpha(ctx),
                                ColoredLightningBolt.DEFAULT_THICKNESS, DEFAULT_DAMAGE, false))
                        .then(thickness));
        LiteralArgumentBuilder<CommandSourceStack> color = literal("color")
                .then(colorArgument()
                        .executes(ctx -> strike(ctx, position(ctx), color(ctx), ColoredLightningBolt.DEFAULT_ALPHA,
                                ColoredLightningBolt.DEFAULT_THICKNESS, DEFAULT_DAMAGE, false))
                        .then(alpha));
        return literal("lightning")
                .requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                .executes(ctx -> strike(ctx, ctx.getSource().getPosition(), ColoredLightningBolt.DEFAULT_COLOR,
                        ColoredLightningBolt.DEFAULT_ALPHA, ColoredLightningBolt.DEFAULT_THICKNESS, DEFAULT_DAMAGE, false))
                .then(argument("pos", Vec3Argument.vec3())
                        .executes(ctx -> strike(ctx, position(ctx), ColoredLightningBolt.DEFAULT_COLOR,
                                ColoredLightningBolt.DEFAULT_ALPHA, ColoredLightningBolt.DEFAULT_THICKNESS, DEFAULT_DAMAGE, false))
                        .then(color));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, String> colorArgument() {
        return argument("color", StringArgumentType.word())
                .suggests((_, builder) -> SharedSuggestionProvider.suggest(COLORS, builder));
    }

    /**
     * Strikes the bolt with everything already set, because the client's copy is built when it enters the level.
     */
    private static int strike(CommandContext<CommandSourceStack> ctx, Vec3 position, int color, float alpha,
                              float thickness, double damage, boolean visualOnly) {
        CommandSourceStack source = ctx.getSource();
        ServerLevel level = source.getLevel();
        ColoredLightningBolt bolt = MxtEntityTypes.COLORED_LIGHTNING.get().create(level, EntitySpawnReason.COMMAND);
        if (bolt == null) return 0;
        bolt.setColor(color);
        bolt.setAlpha(alpha);
        bolt.setThickness(thickness);
        bolt.setVisualOnly(visualOnly);
        bolt.setDamage((float) damage);
        ServerPlayer player = source.getPlayer();
        if (player != null) bolt.setCause(player);
        bolt.setPos(position.x(), position.y(), position.z());
        level.addFreshEntity(bolt);
        String where = String.format(Locale.ROOT, "%.1f %.1f %.1f", position.x(), position.y(), position.z());
        String hex = String.format(Locale.ROOT, "%06X", color);
        Component message = visualOnly
                ? Component.translatable("command.mxt.lightning.struck_visual_only", where, hex, format(alpha), format(thickness))
                : Component.translatable("command.mxt.lightning.struck", where, hex, format(alpha), format(thickness), format(damage));
        source.sendSuccess(() -> message, true);
        return 1;
    }

    private static Vec3 position(CommandContext<CommandSourceStack> ctx) {
        return Vec3Argument.getVec3(ctx, "pos");
    }

    /**
     * The colour is six hexadecimal digits without a leading {@code #}, which is the one spelling
     * {@code word()} accepts from both the chat box and a command block.
     */
    private static int color(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        String value = StringArgumentType.getString(ctx, "color");
        if (value.length() != 6) throw INVALID_COLOR.create(value);
        try {
            return Integer.parseInt(value, 16);
        } catch (NumberFormatException exception) {
            throw INVALID_COLOR.create(value);
        }
    }

    private static float alpha(CommandContext<CommandSourceStack> ctx) {
        return (float) DoubleArgumentType.getDouble(ctx, "alpha");
    }

    private static float thickness(CommandContext<CommandSourceStack> ctx) {
        return (float) DoubleArgumentType.getDouble(ctx, "thickness");
    }

    private static double damage(CommandContext<CommandSourceStack> ctx) {
        return DoubleArgumentType.getDouble(ctx, "damage");
    }

    private static String format(double value) {
        return String.format(Locale.ROOT, "%.2f", value);
    }
}
