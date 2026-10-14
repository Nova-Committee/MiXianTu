package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.command.RegistryReferences;
import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.data.secretrealm.SecretRealm;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.curse.CurseService;
import com.iafenvoy.mxt.runtime.world.SecretRealmRecord;
import com.iafenvoy.mxt.runtime.world.SecretRealmRegistry;
import com.iafenvoy.mxt.runtime.world.SecretRealmService;
import com.iafenvoy.mxt.util.DefinitionText;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.arguments.DimensionArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import java.util.List;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

/**
 * The {@code /curse} command; also reachable as {@code /mxt curse}. Reading the executing player's own curses needs
 * no permission, while changing anybody's - applying, removing or cleansing - keeps its operator requirement in
 * both positions. It is an administrator's mirror of the transactions content uses, going through
 * {@link CurseService}, so a curse applied here obeys its own condition and stacking.
 */
public final class SecretRealmCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return literal("secret_realm")
                .requires(ServerCommandManager::mayChange)
                .then(literal("list")
                        .executes(ctx -> listSecretRealms(ctx.getSource())))
                .then(literal("info")
                        .then(argument("dimension", DimensionArgument.dimension())
                                .executes(ctx -> secretRealmInfo(ctx.getSource(), RegistryReferences.dimension(ctx, "dimension")))))
                .then(literal("enter")
                        .then(argument("definition", ResourceArgument.resource(context, MxtResourceKeys.SECRET_REALM))
                                .executes(ctx -> enterSecretRealm(ctx.getSource(), ResourceArgument.getResource(ctx, "definition", MxtResourceKeys.SECRET_REALM)))))
                .then(literal("exit")
                        .executes(ctx -> exitSecretRealm(ctx.getSource())))
                .then(literal("destroy")
                        .then(argument("dimension", DimensionArgument.dimension())
                                .executes(ctx -> destroySecretRealm(ctx.getSource(), RegistryReferences.dimension(ctx, "dimension")))));
    }

    private static int listSecretRealms(CommandSourceStack source) {
        List<SecretRealmRecord> records = SecretRealmRegistry.all();
        if (records.isEmpty()) {
            source.sendSuccess(() -> Component.translatable("command.mxt.secret_realm.list.empty"), false);
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.secret_realm.list", records.size()), false);
        for (SecretRealmRecord record : records)
            source.sendSuccess(() -> Component.literal(describe(record, source.getServer())), false);
        return records.size();
    }

    private static int secretRealmInfo(CommandSourceStack source, Identifier dimension) {
        SecretRealmRecord record = SecretRealmRegistry.at(ResourceKey.create(Registries.DIMENSION, dimension)).orElse(null);
        if (record == null) {
            source.sendFailure(Component.translatable("command.mxt.secret_realm.unknown", dimension.toString()));
            return 0;
        }
        source.sendSuccess(() -> Component.literal(describe(record, source.getServer())), false);
        return 1;
    }

    private static int enterSecretRealm(CommandSourceStack source, Reference<SecretRealm> definition) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SecretRealmService.Result result = SecretRealmService.enter(player, source.getServer(), definition);
        if (!result.changed()) {
            source.sendFailure(result.message().orElseGet(() -> Component.translatable("command.mxt.secret_realm.enter_failed",
                    result.failure().name())));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.secret_realm.entered", DefinitionText.text(definition)), true);
        return 1;
    }

    private static int exitSecretRealm(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SecretRealmService.Result result = SecretRealmService.exit(player);
        if (!result.changed()) {
            source.sendFailure(result.message().orElseGet(() -> Component.translatable("command.mxt.secret_realm.exit_failed",
                    result.failure() == null ? "unknown" : result.failure().name())));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.secret_realm.exited"), true);
        return 1;
    }

    private static int destroySecretRealm(CommandSourceStack source, Identifier dimension) {
        SecretRealmRecord record = SecretRealmRegistry.at(ResourceKey.create(Registries.DIMENSION, dimension)).orElse(null);
        if (record == null) {
            source.sendFailure(Component.translatable("command.mxt.secret_realm.unknown", dimension.toString()));
            return 0;
        }
        if (!SecretRealmService.destroy(source.getServer(), record)) {
            source.sendFailure(Component.translatable("command.mxt.secret_realm.destroy_failed", dimension.toString()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.secret_realm.destroyed", dimension.toString()), true);
        return 1;
    }

    private static String describe(SecretRealmRecord record, MinecraftServer server) {
        return "%s #%d %s members=%d limit=%s owner=%s prepared=%s loaded=%s".formatted(
                record.dimension().identifier(), record.index(),
                record.definition().unwrapKey().map(key -> key.identifier().toString()).orElse("?"),
                record.members().size(), record.instance().maxMembers(),
                record.owner().map(UUID::toString).orElse("-"), record.prepared(),
                server.getLevel(record.dimension()) != null);
    }
}
