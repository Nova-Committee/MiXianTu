package com.iafenvoy.mxt.data.forging;

import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * A single datapack-defined forging operation.
 */
public record ForgingMethodDefinition(int valueDelta, List<ResourceCost> costs,
                                      List<Identifier> conditions, Optional<Identifier> displayIcon) {
    public static final Codec<ForgingMethodDefinition> CODEC = RecordCodecBuilder.<ForgingMethodDefinition>create(instance -> instance.group(
            Codec.INT.fieldOf("value_delta").forGetter(ForgingMethodDefinition::valueDelta),
            ResourceCost.CODEC.listOf().optionalFieldOf("costs", List.of()).forGetter(ForgingMethodDefinition::costs),
            Identifier.CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(ForgingMethodDefinition::conditions),
            Identifier.CODEC.optionalFieldOf("display_icon").forGetter(ForgingMethodDefinition::displayIcon)
    ).apply(instance, ForgingMethodDefinition::new)).validate(value -> value.valueDelta == 0
            ? DataResult.error(() -> "value_delta must not be zero")
            : DataResult.success(value));
}
