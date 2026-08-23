package com.iafenvoy.mxt.data.ability.target;

import com.iafenvoy.mxt.data.ability.TargetSelector;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

import java.util.stream.Stream;

/**
 * Selects only the ability actor.
 */
public enum SelfTargetSelector implements TargetSelector {
    INSTANCE;

    public static final MapCodec<SelfTargetSelector> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public Stream<Entity> select(Entity actor, FormulaContext context) {
        return Stream.of(actor);
    }

    @Override
    public MapCodec<SelfTargetSelector> codec() {
        return CODEC;
    }
}
