package com.iafenvoy.mxt.data.condition.builtin.entity;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.resource.Resource;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.Entity;

public record ResourceCompareEntityCondition(Holder<Resource> resource,
                                             NumberProvider min) implements EntityCondition {
    public static final MapCodec<ResourceCompareEntityCondition> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Resource.CODEC.fieldOf("resource").forGetter(ResourceCompareEntityCondition::resource), NumberProvider.CODEC.fieldOf("min").forGetter(ResourceCompareEntityCondition::min)
    ).apply(instance, ResourceCompareEntityCondition::new));

    @Override
    public boolean test(Entity entity, FormulaContext context) {
        return entity.getData(MxtAttachments.RESOURCE_HOLDER).get(HolderHelper.id(this.resource)) >= this.min.evaluate(context);
    }

    @Override
    public MapCodec<ResourceCompareEntityCondition> codec() {
        return CODEC;
    }
}
