package com.iafenvoy.mxt.data.condition.builtin.bientity;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public record CanSeeCondition(Block shapeType, Fluid fluidHandling) implements BiEntityCondition {
    private static final Codec<Block> BLOCK_CODEC = enumCodec(Block.class);
    private static final Codec<Fluid> FLUID_CODEC = enumCodec(Fluid.class);
    public static final MapCodec<CanSeeCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BLOCK_CODEC.optionalFieldOf("shape_type", Block.VISUAL).forGetter(CanSeeCondition::shapeType),
            FLUID_CODEC.optionalFieldOf("fluid_handling", Fluid.NONE).forGetter(CanSeeCondition::fluidHandling)
    ).apply(instance, CanSeeCondition::new));

    @Override
    public boolean test(Entity actor, Entity target, FormulaContext context) {
        if (actor.level() != target.level()) return false;
        Vec3 from = actor.getEyePosition(), to = target.getEyePosition();
        return from.distanceTo(to) <= 128.0D && actor.level().clip(new ClipContext(from, to, this.shapeType, this.fluidHandling, actor)).getType() == Type.MISS;
    }

    @Override
    public MapCodec<CanSeeCondition> codec() {
        return CODEC;
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.comapFlatMap(value -> {
            try {
                return DataResult.success(Enum.valueOf(type, value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                return DataResult.error(() -> "Unknown " + type.getSimpleName() + " " + value);
            }
        }, value -> value.name().toLowerCase(Locale.ROOT));
    }
}
