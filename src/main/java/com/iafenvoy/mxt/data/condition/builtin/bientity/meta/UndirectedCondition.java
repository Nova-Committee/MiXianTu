package com.iafenvoy.mxt.data.condition.builtin.bientity.meta;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;

/**
 * Makes a directed bi-entity condition pass in either direction.
 */
public record UndirectedCondition(BiEntityCondition condition) implements BiEntityCondition {
    public static final MapCodec<UndirectedCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiEntityCondition.CODEC.fieldOf("condition").forGetter(UndirectedCondition::condition)
    ).apply(instance, UndirectedCondition::new));

    @Override
    public boolean test(Entity actor, Entity target, FormulaContext context) {
        return this.condition.test(actor, target, context) || this.condition.test(target, actor, context);
    }

    @Override
    public MapCodec<UndirectedCondition> codec() {
        return CODEC;
    }
}
