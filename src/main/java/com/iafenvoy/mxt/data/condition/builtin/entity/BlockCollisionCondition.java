package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public record BlockCollisionCondition(float offsetX, float offsetY, float offsetZ) implements EntityCondition {
    public static final MapCodec<BlockCollisionCondition> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.FLOAT.optionalFieldOf("offset_x", 0.0F).forGetter(BlockCollisionCondition::offsetX),
            Codec.FLOAT.optionalFieldOf("offset_y", 0.0F).forGetter(BlockCollisionCondition::offsetY),
            Codec.FLOAT.optionalFieldOf("offset_z", 0.0F).forGetter(BlockCollisionCondition::offsetZ)
    ).apply(i, BlockCollisionCondition::new));

    @Override
    public boolean test(Entity entity, FormulaContext context) {
        AABB bounds = entity.getBoundingBox().move(this.offsetX, this.offsetY, this.offsetZ).deflate(0.001D);
        for (BlockPos pos : BlockPos.betweenClosed(BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ), BlockPos.containing(bounds.maxX, bounds.maxY, bounds.maxZ))) {
            BlockState state = entity.level().getBlockState(pos);
            if (!state.getCollisionShape(entity.level(), pos).isEmpty()) return true;
        }
        return false;
    }

    @Override
    public MapCodec<BlockCollisionCondition> codec() {
        return CODEC;
    }
}
