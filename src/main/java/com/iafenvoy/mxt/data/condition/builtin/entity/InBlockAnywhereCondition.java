package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.BlockCondition;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.math.Comparison;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;

public record InBlockAnywhereCondition(BlockCondition blockCondition,
                                       Comparison comparison) implements EntityCondition {
    public static final MapCodec<InBlockAnywhereCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            BlockCondition.CODEC.fieldOf("block_condition").forGetter(InBlockAnywhereCondition::blockCondition),
            Comparison.CODEC.fieldOf("comparison").forGetter(InBlockAnywhereCondition::comparison)
    ).apply(i, InBlockAnywhereCondition::new));

    @Override
    public boolean test(Entity entity, FormulaContext context) {
        AABB bounds = entity.getBoundingBox();
        BlockPos min = BlockPos.containing(bounds.minX + 0.001D, bounds.minY + 0.001D, bounds.minZ + 0.001D);
        BlockPos max = BlockPos.containing(bounds.maxX - 0.001D, bounds.maxY - 0.001D, bounds.maxZ - 0.001D);
        int matches = 0;
        for (BlockPos pos : BlockPos.betweenClosed(min, max))
            if (this.blockCondition.test(entity.level(), pos, context)) matches++;
        return this.comparison.compare(matches);
    }

    @Override
    public MapCodec<InBlockAnywhereCondition> codec() {
        return CODEC;
    }
}
