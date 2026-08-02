package com.iafenvoy.mxt.data.alchemy;

import com.iafenvoy.mxt.registry.BehaviorReferences;
import com.iafenvoy.mxt.registry.BehaviorReferences.Reference;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * Server-side alchemy state machines consume this immutable recipe configuration.
 */
public record AlchemyRecipeDefinition(List<Identifier> inputs,
                                      NumberProvider targetTemperature,
                                      NumberProvider temperatureTolerance, int minimumFurnaceTier,
                                      NumberProvider duration,
                                      List<Identifier> environmentTags, NumberProvider minimumAura,
                                      List<Identifier> successOutputs, List<Identifier> failureOutputs,
                                      Optional<Identifier> successBehavior, Optional<Identifier> failureBehavior) {
    public static final Codec<AlchemyRecipeDefinition> CODEC = RecordCodecBuilder.<AlchemyRecipeDefinition>create(instance -> instance.group(
            Identifier.CODEC.listOf().fieldOf("inputs").forGetter(AlchemyRecipeDefinition::inputs), NumberProvider.CODEC.fieldOf("target_temperature").forGetter(AlchemyRecipeDefinition::targetTemperature),
            NumberProvider.CODEC.optionalFieldOf("temperature_tolerance", new Constant(0.0D)).forGetter(AlchemyRecipeDefinition::temperatureTolerance), Codec.INT.optionalFieldOf("minimum_furnace_tier", 0).forGetter(AlchemyRecipeDefinition::minimumFurnaceTier),
            NumberProvider.CODEC.fieldOf("duration").forGetter(AlchemyRecipeDefinition::duration), Identifier.CODEC.listOf().optionalFieldOf("environment_tags", List.of()).forGetter(AlchemyRecipeDefinition::environmentTags), NumberProvider.CODEC.optionalFieldOf("minimum_aura", new Constant(0.0D)).forGetter(AlchemyRecipeDefinition::minimumAura), Identifier.CODEC.listOf().fieldOf("success_outputs").forGetter(AlchemyRecipeDefinition::successOutputs), Identifier.CODEC.listOf().optionalFieldOf("failure_outputs", List.of()).forGetter(AlchemyRecipeDefinition::failureOutputs),
            Identifier.CODEC.optionalFieldOf("success_behavior").forGetter(AlchemyRecipeDefinition::successBehavior), Identifier.CODEC.optionalFieldOf("failure_behavior").forGetter(AlchemyRecipeDefinition::failureBehavior)
    ).apply(instance, AlchemyRecipeDefinition::new)).validate(AlchemyRecipeDefinition::validate);

    private static DataResult<AlchemyRecipeDefinition> validate(AlchemyRecipeDefinition value) {
        if (value.inputs.isEmpty() || value.successOutputs.isEmpty())
            return DataResult.error(() -> "Alchemy recipes need inputs and success_outputs");
        return BehaviorReferences.validate(value, MxtTypeRegistries.ALCHEMY_OUTCOME_BEHAVIOR,
                new Reference("success_behavior", value.successBehavior),
                new Reference("failure_behavior", value.failureBehavior));
    }
}
