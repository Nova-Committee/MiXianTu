package com.iafenvoy.mxt.testmod;

import com.iafenvoy.mxt.registry.MxtEntityTypes;
import com.iafenvoy.mxt.runtime.sword.SwordAuraEntity;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;

import static net.minecraft.commands.Commands.argument;
import static net.minecraft.commands.Commands.literal;

final class SwordAuraProbes {
    private static final String TAG = "mxt_test_sword_aura";

    private SwordAuraProbes() {
    }

    static LiteralArgumentBuilder<CommandSourceStack> commands() {
        return literal("sword_aura")
                .executes(context -> spawn(context.getSource(), 1, 4))
                .then(literal("clear").executes(context -> clear(context.getSource())))
                .then(argument("count", IntegerArgumentType.integer(1, 1024))
                        .executes(context -> spawn(context.getSource(), IntegerArgumentType.getInteger(context, "count"), 10))
                        .then(argument("distance", DoubleArgumentType.doubleArg(2, 120))
                                .executes(context -> spawn(context.getSource(), IntegerArgumentType.getInteger(context, "count"),
                                        DoubleArgumentType.getDouble(context, "distance")))));
    }

    private static int spawn(CommandSourceStack source, int count, double distance) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        clear(source);
        Vec3 forward = player.getLookAngle().normalize();
        Vec3 right = forward.cross(new Vec3(0, 1, 0));
        if (right.lengthSqr() < 1.0E-6) right = new Vec3(1, 0, 0);
        else right = right.normalize();
        Vec3 up = right.cross(forward).normalize();
        Vec3 center = player.getEyePosition().add(forward.scale(distance));
        int side = (int) Math.ceil(Math.sqrt(count));
        int spawned = 0;
        for (int index = 0; index < count; index++) {
            Vec3 position = center.add(right.scale((index % side - (side - 1) * 0.5) * 1.25))
                    .add(up.scale((index / side - (side - 1) * 0.5) * 2.4));
            SwordAuraEntity sword = MxtEntityTypes.SWORD_AURA.get().create(source.getLevel(), EntitySpawnReason.COMMAND);
            if (sword == null) continue;
            sword.setPos(position);
            sword.setLifetime(12000);
            sword.addTag(TAG);
            if (source.getLevel().addFreshEntity(sword)) spawned++;
        }
        int result = spawned;
        source.sendSuccess(() -> Component.translatable("command.mxt_test.sword_aura.spawned", result), false);
        return result;
    }

    private static int clear(CommandSourceStack source) {
        ArrayList<Entity> removed = new ArrayList<Entity>();
        for (Entity entity : source.getLevel().getAllEntities())
            if (entity instanceof SwordAuraEntity && entity.entityTags().contains(TAG)) removed.add(entity);
        removed.forEach(Entity::discard);
        if (!removed.isEmpty()) source.sendSuccess(() -> Component.translatable("command.mxt_test.sword_aura.cleared", removed.size()), false);
        return removed.size();
    }
}
