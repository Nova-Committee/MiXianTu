package com.iafenvoy.mxt.command.server;

import com.iafenvoy.mxt.command.ServerCommandManager;
import com.iafenvoy.mxt.data.rift.*;
import com.iafenvoy.mxt.runtime.rift.*;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.mojang.brigadier.arguments.*;
import com.mojang.brigadier.builder.*;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.*;
import net.minecraft.commands.arguments.*;
import net.minecraft.commands.arguments.coordinates.Vec3Argument;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;
import java.util.*;
import static net.minecraft.commands.Commands.*;

public final class RiftCommand {
    private RiftCommand() { }

    public static LiteralArgumentBuilder<CommandSourceStack> build(CommandBuildContext build) {
        LiteralArgumentBuilder<CommandSourceStack> root = literal("rift").requires(ServerCommandManager::mayChange);
        RequiredArgumentBuilder<CommandSourceStack, String> spawn = argument("facing", StringArgumentType.word()).suggests((_, b) -> SharedSuggestionProvider.suggest(List.of("north", "south", "east", "west", "up", "down"), b));
        targets(spawn, build, true);
        root.then(literal("spawn").then(argument("preset", ResourceArgument.resource(build, MxtResourceKeys.RIFT))
                .then(argument("origin", Vec3Argument.vec3()).then(spawn))));
        RequiredArgumentBuilder<CommandSourceStack, UUID> target = id(); targets(target, build, false); root.then(literal("target").then(target));
        root.then(literal("edit").executes(c -> edit(c, false)).then(id().executes(c -> edit(c, true))));
        root.then(literal("info").executes(c -> {
            for (RiftRecord r : RiftService.records(c.getSource().getLevel())) info(c.getSource(), r);
            return 1;
        }).then(id().executes(c -> {
            RiftRecord r = record(c); if (r == null) return 0; info(c.getSource(), r); return 1; })));
        root.then(literal("remove").then(id().executes(c -> record(c) == null ? 0 : RiftService.remove(c.getSource().getLevel(), UuidArgument.getUuid(c, "id")) ? 1 : 0)));
        root.then(literal("duration").then(id().then(argument("ticks", LongArgumentType.longArg(0)).executes(c -> {
            RiftRecord r = record(c); if (r == null) return 0;
            RiftTemplate d = r.data();
            return publish(c, r, new RiftTemplate(d.key(), d.placement(), false, d.shape(), d.destination(), d.appearance(), LongArgumentType.getLong(c, "ticks"), d.enabled()), true);
        }))));
        root.then(literal("enabled").then(id().then(argument("enabled", BoolArgumentType.bool()).executes(c -> {
            RiftRecord r = record(c); if (r == null) return 0;
            RiftTemplate d = r.data();
            return publish(c, r, new RiftTemplate(d.key(), d.placement(), false, d.shape(), d.destination(), d.appearance(), d.durationTicks(), BoolArgumentType.getBool(c, "enabled")), false);
        }))));
        root.then(literal("copy").then(id().then(argument("origin", Vec3Argument.vec3()).executes(c -> {
            RiftRecord r = record(c); if (r == null) return 0;
            RiftPlacement p = r.data().placement();
            return publish(c, null, RiftEdits.placement(r.data(), new RiftPlacement(Vec3Argument.getVec3(c, "origin"), p.normal(), p.tangent())), true);
        }))));
        RequiredArgumentBuilder<CommandSourceStack, UUID> transforms = id();
        transforms.then(literal("move").then(argument("offset", Vec3Argument.vec3(false)).executes(c -> {
            RiftRecord r = record(c); if (r == null) return 0;
            RiftPlacement p = r.data().placement();
            return publish(c, r, RiftEdits.placement(r.data(), new RiftPlacement(p.origin().add(Vec3Argument.getVec3(c, "offset")), p.normal(), p.tangent())), false);
        })));
        for (String operation : List.of("rotate", "scale", "mirror")) {
            if (operation.equals("mirror")) transforms.then(literal(operation).executes(c -> transform(c, operation, -1)));
            else transforms.then(literal(operation).then(argument("value", DoubleArgumentType.doubleArg(operation.equals("scale") ? 0.001 : -360, operation.equals("scale") ? 100 : 360))
                    .executes(c -> transform(c, operation, DoubleArgumentType.getDouble(c, "value")))));
        }
        root.then(literal("transform").then(transforms));
        root.then(literal("export").then(id().then(argument("format", StringArgumentType.word()).suggests((_, b) -> SharedSuggestionProvider.suggest(List.of("preset", "layout"), b)).executes(c -> {
            RiftRecord r = record(c); if (r == null) return 0; String format = StringArgumentType.getString(c, "format");
            if (!format.equals("preset") && !format.equals("layout")) return 0;
            String json = format.equals("preset") ? RiftExport.preset(r.data()) : RiftExport.layout(List.of(r.data()), Optional.empty(), Optional.empty());
            c.getSource().sendSuccess(() -> Component.literal(json), false); return 1;
        }))));
        return root;
    }

    private static int transform(CommandContext<CommandSourceStack> c, String operation, double value) {
        RiftRecord r = record(c); if (r == null) return 0;
        return publish(c, r, RiftEdits.shape(r.data(), RiftEdits.transform(r.data().shape(), Set.of(), new RiftVertex(0, 0), 0, 0,
                operation.equals("rotate") ? value : 0, operation.equals("rotate") ? 1 : value, operation.equals("scale") ? value : 1, 0)), false);
    }

    private static void targets(ArgumentBuilder<CommandSourceStack, ?> parent, CommandBuildContext build, boolean spawn) {
        parent.then(literal("position").then(argument("dimension", DimensionArgument.dimension()).then(argument("destination", Vec3Argument.vec3()).executes(c -> target(c, spawn,
                new RiftDestination.Position(Optional.of(DimensionArgument.getDimension(c, "dimension").dimension().identifier()), Vec3Argument.getVec3(c, "destination"), Optional.empty(), Optional.empty(), false))))));
        parent.then(literal("secret_realm").then(argument("definition", ResourceArgument.resource(build, MxtResourceKeys.SECRET_REALM)).executes(c -> target(c, spawn,
                new RiftDestination.Realm(ResourceArgument.getResource(c, "definition", MxtResourceKeys.SECRET_REALM).key().identifier())))));
        parent.then(literal("return").executes(c -> target(c, spawn, new RiftDestination.Return())));
    }

    private static int target(CommandContext<CommandSourceStack> c, boolean spawn, RiftDestination destination) throws CommandSyntaxException {
        RiftTemplate data; RiftRecord r = null;
        if (spawn) {
            RiftPreset preset = ResourceArgument.getResource(c, "preset", MxtResourceKeys.RIFT).value();
            Direction direction = Direction.byName(StringArgumentType.getString(c, "facing"));
            if (direction == null) return 0;
            Vec3 normal = new Vec3(direction.getStepX(), direction.getStepY(), direction.getStepZ());
            Vec3 tangent = direction.getAxis().isVertical() ? new Vec3(1, 0, 0) : new Vec3(normal.z, 0, -normal.x);
            data = new RiftTemplate("portal", new RiftPlacement(Vec3Argument.getVec3(c, "origin"), normal, tangent), false,
                    preset.shape(), destination, preset.appearance(), 0, true);
        } else {
            r = record(c); if (r == null) return 0;
            RiftTemplate d = r.data();
            data = new RiftTemplate(d.key(), d.placement(), false, d.shape(), destination, d.appearance(), d.durationTicks(), d.enabled());
        }
        return publish(c, r, data, spawn);
    }

    private static RequiredArgumentBuilder<CommandSourceStack, UUID> id() {
        return argument("id", UuidArgument.uuid()).suggests((c, b) -> SharedSuggestionProvider.suggest(
                RiftService.world(c.getSource().getLevel()).locations().stream().filter(r -> !r.expired(RiftService.now(c.getSource().getLevel())))
                        .map(r -> r.id().toString()), b));
    }
    private static RiftRecord record(CommandContext<CommandSourceStack> c) {
        RiftRecord r = RiftService.get(c.getSource().getLevel(), UuidArgument.getUuid(c, "id"));
        if (r == null) c.getSource().sendFailure(Component.translatable("rift.mxt.failure.missing"));
        return r;
    }
    private static int edit(CommandContext<CommandSourceStack> c, boolean selected) throws CommandSyntaxException {
        RiftEditorService.open(c.getSource().getPlayerOrException(), selected ? UuidArgument.getUuid(c, "id") : null, null); return 1;
    }
    private static int publish(CommandContext<CommandSourceStack> c, RiftRecord old, RiftTemplate data, boolean reset) {
        RiftResult result = RiftService.publish(c.getSource().getLevel(), old == null ? null : old.id(), old == null ? 0 : old.revision(), data,
                c.getSource().getEntity() == null ? null : c.getSource().getEntity().getUUID(), reset);
        if (result.isFailure()) { c.getSource().sendFailure(Component.translatable("rift.mxt.failure.edit", result.detail())); return 0; }
        c.getSource().sendSuccess(() -> Component.translatable("command.mxt.rift.saved", result.record().id().toString()), true); return 1;
    }
    private static void info(CommandSourceStack source, RiftRecord r) {
        source.sendSuccess(() -> Component.translatable("command.mxt.rift.record", r.id().toString(), r.revision(), r.data().shape().vertices().size(),
                r.data().destination().type(), r.data().durationTicks(), r.data().enabled()), false);
    }
}
