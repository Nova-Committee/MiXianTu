package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;

public record AttributeCondition(Holder<Attribute> attribute, Comparison comparison) implements EntityCondition {
    public static final MapCodec<AttributeCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Attribute.CODEC.fieldOf("attribute").forGetter(AttributeCondition::attribute),
            Comparison.CODEC.forGetter(AttributeCondition::comparison)
    ).apply(i, AttributeCondition::new));

    @Override
    public boolean test(EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        return entity instanceof LivingEntity living && this.comparison.compare(living.getAttributeValue(this.attribute));
    }

    @Override
    public MapCodec<AttributeCondition> codec() {
        return CODEC;
    }
}
