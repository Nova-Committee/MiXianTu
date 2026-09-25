package com.iafenvoy.mxt.data.ability.target;

import com.iafenvoy.mxt.data.ability.TargetSelector;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

/**
 * Selects entities inside a cone along the actor's line of sight: {@code angle} is the half-angle in degrees, so
 * {@code 30} means thirty degrees to either side of the look. The cone's own reach is truncated where a block gets
 * in the way along its centre line, which is what keeps a shout from carrying through a wall; a being shielded
 * sideways inside the cone is not told apart from one in the open. The actor is excluded by default.
 */
public record ConeTargetSelector(NumberProvider length, NumberProvider angle, boolean includeActor, int limit,
                                 TargetOrder order) implements TargetSelector {
    private static final double MAX_LENGTH = 128.0D;

    public static final MapCodec<ConeTargetSelector> CODEC = RecordCodecBuilder.<ConeTargetSelector>mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("length").forGetter(ConeTargetSelector::length),
            NumberProvider.CODEC.fieldOf("angle").forGetter(ConeTargetSelector::angle),
            Codec.BOOL.optionalFieldOf("include_actor", false).forGetter(ConeTargetSelector::includeActor),
            Codec.INT.optionalFieldOf("limit", 0).forGetter(ConeTargetSelector::limit),
            TargetOrder.CODEC.optionalFieldOf("order", TargetOrder.NEAREST).forGetter(ConeTargetSelector::order)
    ).apply(i, ConeTargetSelector::new)).validate(ConeTargetSelector::validate);

    private static DataResult<ConeTargetSelector> validate(ConeTargetSelector selector) {
        if (selector.limit < 0)
            return DataResult.error(() -> "A target limit must not be negative: " + selector.limit);
        if (selector.length instanceof Constant(double value) && (!Double.isFinite(value) || value <= 0.0D))
            return DataResult.error(() -> "A cone length must be finite and positive: " + value);
        if (selector.angle instanceof Constant(double value) && (!Double.isFinite(value) || value < 0.0D || value > 180.0D))
            return DataResult.error(() -> "A cone half-angle must be between 0 and 180 degrees: " + value);
        return DataResult.success(selector);
    }

    @Override
    public Stream<Entity> select(Entity actor, FormulaContext context) {
        return this.select(actor, context, null);
    }

    @Override
    public Stream<Entity> select(Entity actor, FormulaContext context, @Nullable Vec3 origin) {
        double reach = this.length.evaluate(context);
        double half = this.angle.evaluate(context);
        if (!Double.isFinite(reach) || !Double.isFinite(half) || reach <= 0.0D || half < 0.0D) return Stream.empty();
        Vec3 start = origin == null ? actor.getEyePosition() : origin;
        Vec3 direction = actor.getLookAngle();
        Vec3 stop = TargetGeometry.clip(actor, start, start.add(direction.scale(Math.min(reach, MAX_LENGTH))));
        // Beyond the block the cone stops, so its reach is measured to where the beam ended rather than to its
        // written length.
        double span = stop.distanceTo(start);
        double cosine = Math.cos(Math.toRadians(Math.min(half, 180.0D)));
        Stream<Entity> found = actor.level().getEntities(actor, new AABB(start, stop).inflate(span)).stream()
                .filter(entity -> inside(entity, start, direction, span, cosine));
        if (this.includeActor) found = Stream.concat(Stream.of(actor), found);
        return TargetSelector.limited(found, actor, origin, this.limit, this.order);
    }

    private static boolean inside(Entity entity, Vec3 start, Vec3 direction, double span, double cosine) {
        Vec3 to = TargetGeometry.centre(entity).subtract(start);
        double distance = to.length();
        if (distance > span) return false;
        // A being on the caster's own spot has no direction to compare, and is inside by definition.
        if (distance < 1.0E-6D) return true;
        return to.scale(1.0D / distance).dot(direction) >= cosine;
    }

    @Override
    public MapCodec<ConeTargetSelector> codec() {
        return CODEC;
    }
}
