package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.data.secretrealm.SecretRealm;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.runtime.rift.RiftLanding;
import com.iafenvoy.mxt.runtime.world.SecretRealmEntryLocator;
import com.iafenvoy.mxt.runtime.world.SecretRealmRecord;
import com.iafenvoy.mxt.runtime.world.SecretRealmRegistry;
import com.iafenvoy.mxt.runtime.world.SecretRealmService;
import com.iafenvoy.mxt.util.DefinitionText;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.IdentifierArgument;
import net.minecraft.commands.arguments.ResourceArgument;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

public final class SecretRealmCommand {
    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext context) {
        return literal("secret_realm")
                .requires(ServerCommandManager::mayChange)
                .then(literal("definitions")
                        .executes(ctx -> definitions(ctx.getSource())))
                .then(literal("join")
                        .then(dimension().executes(ctx -> join(ctx.getSource(), IdentifierArgument.getId(ctx, "dimension")))))
                .then(literal("check")
                        .then(dimension().executes(ctx -> check(ctx.getSource(), IdentifierArgument.getId(ctx, "dimension")))))
                .then(literal("anchor")
                        .then(dimension().then(argument("pos", Vec3Argument.vec3())
                                .executes(ctx -> anchor(ctx.getSource(), IdentifierArgument.getId(ctx, "dimension"), Vec3Argument.getVec3(ctx, "pos"))))))
                .then(literal("list")
                        .executes(ctx -> listSecretRealms(ctx.getSource())))
                .then(literal("info")
                        .then(dimension()
                                .executes(ctx -> secretRealmInfo(ctx.getSource(), IdentifierArgument.getId(ctx, "dimension")))))
                .then(literal("enter")
                        .then(argument("definition", ResourceArgument.resource(context, MxtResourceKeys.SECRET_REALM))
                                .executes(ctx -> enterSecretRealm(ctx.getSource(), ResourceArgument.getResource(ctx, "definition", MxtResourceKeys.SECRET_REALM)))))
                .then(literal("exit")
                        .executes(ctx -> exitSecretRealm(ctx.getSource())))
                .then(literal("destroy")
                        .then(dimension()
                                .executes(ctx -> destroySecretRealm(ctx.getSource(), IdentifierArgument.getId(ctx, "dimension")))));
    }

    private static RequiredArgumentBuilder<CommandSourceStack, Identifier> dimension() {
        return argument("dimension", IdentifierArgument.id()).suggests((_, builder) -> SharedSuggestionProvider.suggestResource(
                SecretRealmRegistry.all().stream().map(record -> record.dimension().identifier()), builder));
    }

    private static SecretRealmRecord record(CommandSourceStack source, Identifier dimension) {
        SecretRealmRecord record = SecretRealmRegistry.at(ResourceKey.create(Registries.DIMENSION, dimension)).orElse(null);
        if (record == null)
            source.sendFailure(Component.translatable("command.mxt.secret_realm.unknown", dimension.toString()));
        return record;
    }

    private static int definitions(CommandSourceStack source) {
        Registry<SecretRealm> definitions = source.getServer().registryAccess().lookupOrThrow(MxtResourceKeys.SECRET_REALM);
        List<Identifier> ids = definitions.keySet().stream().sorted().toList();
        source.sendSuccess(() -> Component.translatable("command.mxt.secret_realm.definitions", ids.size()), false);
        for (Identifier id : ids) source.sendSuccess(() -> Component.literal(id.toString()), false);
        return ids.size();
    }

    private static int join(CommandSourceStack source, Identifier dimension) throws CommandSyntaxException {
        SecretRealmRecord record = record(source, dimension);
        if (record == null) return 0;
        SecretRealmService.TravelResult result = SecretRealmService.enterInstance(source.getPlayerOrException(), record, null, null);
        if (result.isFailure()) {
            source.sendFailure(result.message().orElseGet(() -> result.failure().message()));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.secret_realm.entered", DefinitionText.nameOrId(record.definition())), true);
        return 1;
    }

    private static SecretRealmRecord prepare(CommandSourceStack source, Identifier dimension) {
        SecretRealmRecord record = record(source, dimension);
        if (record == null) return null;
        SecretRealmRecord prepared = SecretRealmService.open(source.getServer(), record).orElse(null);
        if (prepared == null)
            source.sendFailure(Component.translatable("command.mxt.secret_realm.prepare_failed", dimension.toString()));
        return prepared;
    }

    private static int check(CommandSourceStack source, Identifier dimension) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SecretRealmRecord record = prepare(source, dimension);
        if (record == null) return 0;
        ServerLevel level = source.getServer().getLevel(record.dimension());
        int slot = record.holds(player.getUUID()) ? record.members().indexOf(player.getUUID()) : record.members().size();
        SecretRealmEntryLocator.Arrival arrival = SecretRealmEntryLocator.arrival(level, record, slot, player.getYRot(), player.getXRot());
        RiftLanding.Inspection primary = RiftLanding.inspect(level, player, arrival.position());
        RiftLanding.Inspection landing = SecretRealmService.checkArrival(level, player, arrival.position(), true);
        source.sendSuccess(() -> Component.translatable("command.mxt.secret_realm.landing", dimension.toString(), coordinates(arrival.position()),
                coordinates(landing.position()), landing.isSuccess() ? Component.translatable("command.mxt.secret_realm.safe") : landing.failure().message(),
                primary.isSuccess() ? Component.translatable("command.mxt.secret_realm.safe") : primary.failure().message()), false);
        return landing.isSuccess() ? 1 : 0;
    }

    private static int anchor(CommandSourceStack source, Identifier dimension, Vec3 position) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SecretRealmRecord record = prepare(source, dimension);
        if (record == null) return 0;
        ServerLevel level = source.getServer().getLevel(record.dimension());
        RiftLanding.Inspection landing = SecretRealmService.checkArrival(level, player, position, false);
        if (landing.isFailure()) {
            source.sendFailure(landing.failure().message());
            return 0;
        }
        SecretRealmRegistry.replace(record.withAnchor(position));
        source.sendSuccess(() -> Component.translatable("command.mxt.secret_realm.anchor", dimension.toString(), coordinates(position)), true);
        return 1;
    }

    private static String coordinates(Vec3 pos) {
        return String.format(Locale.ROOT, "%.3f %.3f %.3f", pos.x, pos.y, pos.z);
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
        source.sendSuccess(() -> Component.translatable("command.mxt.secret_realm.anchor_info", record.anchor().map(SecretRealmCommand::coordinates).orElse("-")), false);
        return 1;
    }

    private static int enterSecretRealm(CommandSourceStack source, Reference<SecretRealm> definition) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SecretRealmService.TravelResult result = SecretRealmService.enter(player, source.getServer(), definition);
        if (!result.isSuccess()) {
            source.sendFailure(result.message().orElseGet(() -> Component.translatable("command.mxt.secret_realm.enter_failed",
                    result.failure().message())));
            return 0;
        }
        source.sendSuccess(() -> Component.translatable("command.mxt.secret_realm.entered", DefinitionText.nameOrId(definition)), true);
        return 1;
    }

    private static int exitSecretRealm(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        SecretRealmService.TravelResult result = SecretRealmService.exit(player);
        if (!result.isSuccess()) {
            source.sendFailure(result.message().orElseGet(() -> Component.translatable("command.mxt.secret_realm.exit_failed",
                    result.failure().message())));
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
                record.definition().unwrapKey().orElseThrow().identifier(),
                record.members().size(), record.instance().maxMembers(),
                record.owner().map(UUID::toString).orElse("-"), record.prepared(),
                server.getLevel(record.dimension()) != null);
    }
}
