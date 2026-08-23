package com.iafenvoy.mxt.data.ability.target;

import com.iafenvoy.mxt.data.ability.TargetSelector;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;

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
        double value = this.radius.evaluate(context);
        if (!Double.isFinite(value) || value < 0.0D) return Stream.empty();
        double radius = Math.min(value, 128.0D);
        Stream<Entity> entities = actor.level().getEntities(actor, actor.getBoundingBox().inflate(radius)).stream();
        if (this.includeActor) return Stream.concat(Stream.of(actor), entities);
        return entities;
    }

    @Override
    public MapCodec<AreaTargetSelector> codec() {
        return CODEC;
    }
}
