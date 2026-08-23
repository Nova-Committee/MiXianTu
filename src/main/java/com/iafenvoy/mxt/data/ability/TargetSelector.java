package com.iafenvoy.mxt.data.ability;

import com.iafenvoy.mxt.registry.MxtRegistries;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;

import java.util.function.Function;
import java.util.stream.Stream;

/**
 * Selects the entities that receive an ability's bi-entity action.
 */
public interface TargetSelector {
    Codec<TargetSelector> CODEC = MxtRegistries.ABILITY_TARGET_SELECTOR_TYPE
            .byNameCodec().dispatch("type", TargetSelector::codec, Function.identity());

    Stream<Entity> select(Entity actor, FormulaContext context);

    MapCodec<? extends TargetSelector> codec();
}
