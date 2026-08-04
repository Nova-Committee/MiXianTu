package com.iafenvoy.mxt.data.condition.builtin.item;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.runtime.artifact.ArtifactService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.MapCodec;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;

public enum OwnedByItemCondition implements ItemCondition {
    INSTANCE;
    public static final MapCodec<OwnedByItemCondition> CODEC = MapCodec.unit(INSTANCE);

    @Override
    public boolean test(Entity holder, ItemStack stack, FormulaContext context) {
        return ArtifactService.state(stack).ownerUuid().filter(value -> value.equals(holder.getUUID().toString())).isPresent();
    }

    @Override
    public MapCodec<OwnedByItemCondition> codec() {
        return CODEC;
    }
}
