package com.iafenvoy.mxt.data.condition.builtin.bientity;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;
import com.iafenvoy.mxt.util.codec.ExtraEnumCodecs;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;

public record CanSeeCondition(Block shapeType, Fluid fluidHandling) implements BiEntityCondition {
    public static final MapCodec<CanSeeCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            ExtraEnumCodecs.CLIP_CONTEXT_BLOCK.optionalFieldOf("shape_type", Block.VISUAL).forGetter(CanSeeCondition::shapeType),
            ExtraEnumCodecs.CLIP_CONTEXT_FLUID.optionalFieldOf("fluid_handling", Fluid.NONE).forGetter(CanSeeCondition::fluidHandling)
    ).apply(i, CanSeeCondition::new));

    @Override
    public boolean test(@NonNull BiEntityConditionContext ctx) {
        Entity actor = ctx.actor();
        Entity target = ctx.target();
        if (actor.level() != target.level()) return false;
        Vec3 from = actor.getEyePosition(), to = target.getEyePosition();
        // Past 128 blocks this answers false without raycasting, so a long-range check stays cheap.
        return from.distanceTo(to) <= 128.0D && actor.level().clip(new ClipContext(from, to, this.shapeType, this.fluidHandling, actor)).getType() == Type.MISS;
    }

    @Override
    public @NonNull MapCodec<CanSeeCondition> codec() {
        return CODEC;
    }
}
