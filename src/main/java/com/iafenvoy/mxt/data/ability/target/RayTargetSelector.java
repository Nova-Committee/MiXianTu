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
 * Selects entities along the actor's line of sight: a cylinder of {@code radius} reaching {@code length} blocks,
 * truncated where a block gets in the way, so a cast never reaches through a wall. The actor is excluded by default.
 */
public record RayTargetSelector(NumberProvider length, NumberProvider radius, boolean includeActor, int limit,
                                TargetOrder order) implements TargetSelector {
    // Far past any real cast, and the same bound the area selector puts on its reach: a longer line makes the level
    // walk every entity it holds.
    private static final double MAX_LENGTH = 128.0D;
    private static final double MAX_RADIUS = 64.0D;

    public static final MapCodec<RayTargetSelector> CODEC = RecordCodecBuilder.<RayTargetSelector>mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("length").forGetter(RayTargetSelector::length),
            NumberProvider.CODEC.optionalFieldOf("radius", new Constant(0.5D)).forGetter(RayTargetSelector::radius),
            Codec.BOOL.optionalFieldOf("include_actor", false).forGetter(RayTargetSelector::includeActor),
            Codec.INT.optionalFieldOf("limit", 0).forGetter(RayTargetSelector::limit),
            TargetOrder.CODEC.optionalFieldOf("order", TargetOrder.NEAREST).forGetter(RayTargetSelector::order)
    ).apply(i, RayTargetSelector::new)).validate(RayTargetSelector::validate);

    private static DataResult<RayTargetSelector> validate(RayTargetSelector selector) {
        if (selector.limit < 0)
            return DataResult.error(() -> "A target limit must not be negative: " + selector.limit);
        if (selector.length instanceof Constant(double value) && (!Double.isFinite(value) || value <= 0.0D))
            return DataResult.error(() -> "A ray length must be finite and positive: " + value);
        if (selector.radius instanceof Constant(double value) && (!Double.isFinite(value) || value < 0.0D))
            return DataResult.error(() -> "A ray radius must be finite and non-negative: " + value);
        return DataResult.success(selector);
    }

    @Override
    public Stream<Entity> select(Entity actor, FormulaContext context) {
        return this.select(actor, context, null);
    }

    @Override
    public Stream<Entity> select(Entity actor, FormulaContext context, @Nullable Vec3 origin) {
        double reach = this.length.evaluate(context);
        double width = this.radius.evaluate(context);
        if (!Double.isFinite(reach) || !Double.isFinite(width) || reach <= 0.0D || width < 0.0D) return Stream.empty();
        Vec3 start = origin == null ? actor.getEyePosition() : origin;
        Vec3 stop = TargetGeometry.clip(actor, start, start.add(actor.getLookAngle().scale(Math.min(reach, MAX_LENGTH))));
        double radius = Math.min(width, MAX_RADIUS);
        Stream<Entity> found = actor.level().getEntities(actor, new AABB(start, stop).inflate(radius)).stream()
                .filter(entity -> TargetGeometry.crosses(entity, start, stop, radius));
        if (this.includeActor) found = Stream.concat(Stream.of(actor), found);
        return TargetSelector.limited(found, actor, origin, this.limit, this.order);
    }

    @Override
    public MapCodec<RayTargetSelector> codec() {
        return CODEC;
    }
}
