package com.iafenvoy.mxt.data.forging;

import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.meta.AlwaysTrueEntityCondition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.List;
import java.util.Optional;

/**
 * A single datapack-defined forging operation.
 */
public record ForgingMethod(int valueDelta, List<ResourceCost> costs, EntityCondition condition,
                            Optional<Identifier> displayIcon) {
    public static final Codec<Holder<ForgingMethod>> CODEC = RegistryFixedCodec.create(MxtRegistryKeys.FORGING_METHOD);
    public static final Codec<ForgingMethod> DIRECT_CODEC = RecordCodecBuilder.<ForgingMethod>create(instance -> instance.group(
            Codec.INT.fieldOf("value_delta").forGetter(ForgingMethod::valueDelta),
            ResourceCost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(ForgingMethod::costs),
            EntityCondition.CODEC.optionalFieldOf("condition", AlwaysTrueEntityCondition.INSTANCE).forGetter(ForgingMethod::condition),
            Identifier.CODEC.optionalFieldOf("display_icon").forGetter(ForgingMethod::displayIcon)
    ).apply(instance, ForgingMethod::new)).validate(value -> value.valueDelta == 0
            ? DataResult.error(() -> "value_delta must not be zero")
            : DataResult.success(value));

}
