package com.iafenvoy.mxt.data.condition.builtin.block;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.runtime.world.AuraResult;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.data.aura.AuraRequirement;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.world.level.Level;

import java.util.Map;

public record AuraRangeBlockCondition(Map<Holder<Element>, AuraRequirement> aura) implements BlockCondition {
    public static final MapCodec<AuraRangeBlockCondition> CODEC = CollectionCodecs.map(Element.CODEC, AuraRequirement.CODEC)
            .fieldOf("aura").xmap(AuraRangeBlockCondition::new, AuraRangeBlockCondition::aura);

    @Override
    public boolean test(Level level, BlockPos pos, FormulaContext context) {
        AuraResult resolved = AuraService.getPositionAura(level, pos);
        return this.aura.entrySet().stream().allMatch(entry -> entry.getValue().test(resolved.pool(entry.getKey()).amount(), context));
    }

    @Override
    public MapCodec<AuraRangeBlockCondition> codec() {
        return CODEC;
    }
}
