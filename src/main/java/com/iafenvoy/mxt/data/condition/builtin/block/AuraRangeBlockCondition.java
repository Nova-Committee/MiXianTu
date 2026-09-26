package com.iafenvoy.mxt.data.condition.builtin.block;

import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraRequirement;
import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.data.context.condition.BlockConditionContext;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import org.jspecify.annotations.NonNull;

import java.util.Map;

public record AuraRangeBlockCondition(Map<Holder<Aura>, AuraRequirement> aura) implements BlockCondition {
    public static final MapCodec<AuraRangeBlockCondition> CODEC = CollectionCodecs.map(Aura.CODEC, AuraRequirement.CODEC)
            .fieldOf("aura").xmap(AuraRangeBlockCondition::new, AuraRangeBlockCondition::aura);

    @Override
    public boolean test(@NonNull BlockConditionContext ctx) {
        AuraResult resolved = AuraService.getPositionAura(ctx.level(), ctx.pos());
        return this.aura.entrySet().stream().allMatch(entry -> entry.getValue().test(resolved.pool(entry.getKey()).amount(), ctx.formula()));
    }

    @Override
    public @NonNull MapCodec<AuraRangeBlockCondition> codec() {
        return CODEC;
    }
}
