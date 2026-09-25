package com.iafenvoy.mxt.data.ability.target;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * The two pieces an area, a ray and a cone all need: where a beam stops against blocks, and how a straight line is
 * measured against an entity's own box.
 */
final class TargetGeometry {
    private TargetGeometry() {
    }

    // Where a beam from one place to another stops against blocks. A miss means the whole line is clear, so the end
    // point is the answer - which is what keeps the geometry free of a nullable result.
    static Vec3 clip(Entity actor, Vec3 start, Vec3 end) {
        BlockHitResult hit = actor.level().clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, actor));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    // Whether the segment passes through the entity's own box widened by the radius: the test is vanilla's, so a
    // being is caught by any part of itself rather than by its centre alone.
    static boolean crosses(Entity entity, Vec3 start, Vec3 end, double radius) {
        return entity.getBoundingBox().inflate(radius).clip(start, end).isPresent();
    }

    // The middle of an entity, which is what a cone's angle is measured against.
    static Vec3 centre(Entity entity) {
        return entity.position().add(0.0D, entity.getBbHeight() * 0.5D, 0.0D);
    }
}
