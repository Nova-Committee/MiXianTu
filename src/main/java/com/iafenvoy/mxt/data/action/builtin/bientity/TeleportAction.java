package com.iafenvoy.mxt.data.action.builtin.bientity;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.context.action.BiEntityActionContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

import java.util.Set;

/**
 * Swaps either endpoint of a bi-entity action to the other endpoint's position.
 */
public record TeleportAction(boolean teleportActor, boolean teleportTarget, boolean rotate) implements BiEntityAction {
    public static final MapCodec<TeleportAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.BOOL.optionalFieldOf("teleport_actor", false).forGetter(TeleportAction::teleportActor),
            Codec.BOOL.optionalFieldOf("teleport_target", true).forGetter(TeleportAction::teleportTarget),
            Codec.BOOL.optionalFieldOf("rotate", false).forGetter(TeleportAction::rotate)
    ).apply(i, TeleportAction::new));

    @Override
    public void execute(@NonNull BiEntityActionContext ctx) {
        Entity actor = ctx.actor();
        Entity target = ctx.target();
        if (actor.level().isClientSide() || (!this.teleportActor && !this.teleportTarget) || !(actor.level() instanceof ServerLevel actorLevel) || !(target.level() instanceof ServerLevel targetLevel))
            return;
        // "The actor's position" is where the activation happened: an item cast from a display stand pulls a
        // target to the stand rather than to whoever filled it.
        Position actorPosition = Position.at(actorLevel, actor, ctx.position());
        Position targetPosition = Position.of(targetLevel, target);
        if (this.teleportActor) targetPosition.teleport(actor, this.rotate);
        if (this.teleportTarget) actorPosition.teleport(target, this.rotate);
    }

    @Override
    public @NonNull MapCodec<TeleportAction> codec() {
        return CODEC;
    }

    private record Position(ServerLevel level, double x, double y, double z, float yRot, float xRot) {
        private static Position of(ServerLevel level, Entity entity) {
            return at(level, entity, entity.position());
        }

        private static Position at(ServerLevel level, Entity entity, Vec3 position) {
            return new Position(level, position.x, position.y, position.z, entity.getYRot(), entity.getXRot());
        }

        private void teleport(Entity entity, boolean rotate) {
            entity.teleportTo(this.level, this.x, this.y, this.z, Set.of(), rotate ? this.yRot : entity.getYRot(), rotate ? this.xRot : entity.getXRot(), false);
        }
    }
}
