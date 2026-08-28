package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.data.aura.AuraRequirement;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.core.Holder;

import java.util.Map;

/**
 * Tests the server-resolved aura concentration at an entity's current position.
 */
public record AuraRangeEntityCondition(Map<Holder<Resource>, AuraRequirement> aura) implements EntityCondition {
    public static final MapCodec<AuraRangeEntityCondition> CODEC = CollectionCodecs.map(Resource.CODEC, AuraRequirement.CODEC)
            .fieldOf("aura").xmap(AuraRangeEntityCondition::new, AuraRangeEntityCondition::aura);

    @Override
    public boolean test(EntityConditionContext ctx) {
        Entity entity = ctx.entity();
        FormulaContext context = ctx.formula();
        AuraResult resolved = AuraService.getPositionAura(entity.level(), entity.blockPosition());
        return this.aura.entrySet().stream().allMatch(entry -> entry.getValue().test(resolved.pool(entry.getKey()).amount(), context));
    }

    @Override
    public MapCodec<AuraRangeEntityCondition> codec() {
        return CODEC;
    }
}
