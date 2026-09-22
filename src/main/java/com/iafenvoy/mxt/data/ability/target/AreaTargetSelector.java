package com.iafenvoy.mxt.data.ability.target;

import com.iafenvoy.mxt.data.ability.TargetSelector;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.stream.Stream;

/**
 * Selects entities in an actor-centred area. The actor is excluded by default.
 */
public record AreaTargetSelector(NumberProvider radius, boolean includeActor) implements TargetSelector {
    public static final MapCodec<AreaTargetSelector> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.fieldOf("radius").forGetter(AreaTargetSelector::radius),
            Codec.BOOL.optionalFieldOf("include_actor", false).forGetter(AreaTargetSelector::includeActor)
    ).apply(i, AreaTargetSelector::new));

    public AreaTargetSelector(double radius, boolean includeActor) {
        this(new Constant(radius), includeActor);
    }

    @Override
    public Stream<Entity> select(Entity actor, FormulaContext context) {
        return this.select(actor, context, null);
    }

    @Override
    public Stream<Entity> select(Entity actor, FormulaContext context, @Nullable Vec3 origin) {
        double value = this.radius.evaluate(context);
        if (!Double.isFinite(value) || value < 0.0D) return Stream.empty();
        // Clamped at 128 blocks: a larger box makes getEntities walk the whole level.
        double radius = Math.min(value, 128.0D);
        // An area around the place the activation happens at: an item cast from a display stand covers the
        // stand, not whoever wound it there. With no such place this is exactly the actor's own box.
        AABB area = origin == null ? actor.getBoundingBox().inflate(radius)
                : AABB.ofSize(origin, radius * 2.0D, radius * 2.0D, radius * 2.0D);
        Stream<Entity> entities = actor.level().getEntities(actor, area).stream();
        if (this.includeActor) return Stream.concat(Stream.of(actor), entities);
        return entities;
    }

    @Override
    public MapCodec<AreaTargetSelector> codec() {
        return CODEC;
    }
}
