package com.iafenvoy.mxt.data.condition.builtin.block;

import com.iafenvoy.mxt.data.context.condition.BlockConditionContext;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.data.aura.AuraRequirement;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;
import org.jspecify.annotations.NonNull;

import java.util.Map;

public record AuraRangeBlockCondition(Map<Holder<Resource>, AuraRequirement> aura) implements BlockCondition {
    public static final MapCodec<AuraRangeBlockCondition> CODEC = CollectionCodecs.map(Resource.CODEC, AuraRequirement.CODEC)
            .fieldOf("aura").xmap(AuraRangeBlockCondition::new, AuraRangeBlockCondition::aura);

    @Override
    public boolean test(@NonNull BlockConditionContext ctx) {
        Level level = ctx.level();
        BlockPos pos = ctx.pos();
        FormulaContext context = ctx.formula();
        AuraResult resolved = AuraService.getPositionAura(level, pos);
        return this.aura.entrySet().stream().allMatch(entry -> entry.getValue().test(resolved.pool(entry.getKey()).amount(), context));
    }

    @Override
    public @NonNull MapCodec<AuraRangeBlockCondition> codec() {
        return CODEC;
    }
}
