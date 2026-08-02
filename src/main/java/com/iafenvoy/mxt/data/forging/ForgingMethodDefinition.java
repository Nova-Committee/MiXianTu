package com.iafenvoy.mxt.data.forging;

import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import com.iafenvoy.mxt.runtime.cultivation.CultivationCondition;
import com.iafenvoy.mxt.util.codec.AutoIgnoreListCodec;
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
public record ForgingMethodDefinition(int valueDelta, List<ResourceCost> costs,
                                      List<CultivationCondition> conditions, Optional<Identifier> displayIcon) {
    public static final Codec<Holder<ForgingMethodDefinition>> HOLDER_CODEC = RegistryFixedCodec.create(MxtRegistryKeys.FORGING_METHOD);
    public static final Codec<ForgingMethodDefinition> CODEC = RecordCodecBuilder.<ForgingMethodDefinition>create(instance -> instance.group(
            Codec.INT.fieldOf("value_delta").forGetter(ForgingMethodDefinition::valueDelta),
            ResourceCost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(ForgingMethodDefinition::costs),
            AutoIgnoreListCodec.create(MxtTypeRegistries.CULTIVATION_CONDITION.byNameCodec()).optionalFieldOf("conditions", List.of()).forGetter(ForgingMethodDefinition::conditions),
            Identifier.CODEC.optionalFieldOf("display_icon").forGetter(ForgingMethodDefinition::displayIcon)
    ).apply(instance, ForgingMethodDefinition::new)).validate(value -> value.valueDelta == 0
            ? DataResult.error(() -> "value_delta must not be zero")
            : DataResult.success(value));
}
