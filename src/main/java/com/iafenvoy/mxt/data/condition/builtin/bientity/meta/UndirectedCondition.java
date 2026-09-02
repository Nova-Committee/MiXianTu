package com.iafenvoy.mxt.data.condition.builtin.bientity.meta;

import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.context.condition.BiEntityConditionContext;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.NonNull;

/**
 * Makes a directed bi-entity condition pass in either direction.
 */
public record UndirectedCondition(BiEntityCondition condition) implements BiEntityCondition {
    public static final MapCodec<UndirectedCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BiEntityCondition.CODEC.fieldOf("condition").forGetter(UndirectedCondition::condition)
    ).apply(i, UndirectedCondition::new));

    @Override
    public boolean test(@NonNull BiEntityConditionContext ctx) {
        Entity actor = ctx.actor();
        Entity target = ctx.target();
        FormulaContext context = ctx.formula();
        return this.condition.test(actor, target, ctx) || this.condition.test(target, actor, ctx);
    }

    @Override
    public @NonNull MapCodec<UndirectedCondition> codec() {
        return CODEC;
    }
}
