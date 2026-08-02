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
public record AlchemyRecipe(List<Identifier> inputs, NumberProvider targetTemperature,
                            NumberProvider temperatureTolerance, int minimumFurnaceTier, NumberProvider duration,
                            List<Identifier> environmentTags, NumberProvider minimumAura,
                            List<Identifier> successOutputs, List<Identifier> failureOutputs,
                            Optional<Identifier> successBehavior, Optional<Identifier> failureBehavior) {
    public static final Codec<AlchemyRecipe> CODEC = RecordCodecBuilder.<AlchemyRecipe>create(instance -> instance.group(
            Identifier.CODEC.listOf(1, Integer.MAX_VALUE).fieldOf("inputs").forGetter(AlchemyRecipe::inputs),
            NumberProvider.CODEC.fieldOf("target_temperature").forGetter(AlchemyRecipe::targetTemperature),
            NumberProvider.CODEC.optionalFieldOf("temperature_tolerance", new Constant(0.0D)).forGetter(AlchemyRecipe::temperatureTolerance),
            Codec.INT.optionalFieldOf("minimum_furnace_tier", 0).forGetter(AlchemyRecipe::minimumFurnaceTier),
            NumberProvider.CODEC.fieldOf("duration").forGetter(AlchemyRecipe::duration),
            Identifier.CODEC.listOf().optionalFieldOf("environment_tags", List.of()).forGetter(AlchemyRecipe::environmentTags),
            NumberProvider.CODEC.optionalFieldOf("minimum_aura", new Constant(0.0D)).forGetter(AlchemyRecipe::minimumAura),
            Identifier.CODEC.listOf(1, Integer.MAX_VALUE).fieldOf("success_outputs").forGetter(AlchemyRecipe::successOutputs),
            Identifier.CODEC.listOf().optionalFieldOf("failure_outputs", List.of()).forGetter(AlchemyRecipe::failureOutputs),
            Identifier.CODEC.optionalFieldOf("success_behavior").forGetter(AlchemyRecipe::successBehavior),
            Identifier.CODEC.optionalFieldOf("failure_behavior").forGetter(AlchemyRecipe::failureBehavior)
    ).apply(instance, AlchemyRecipe::new)).validate(AlchemyRecipe::validate);

    private static DataResult<AlchemyRecipe> validate(AlchemyRecipe value) {
        return BehaviorReferences.validate(value, MxtTypeRegistries.ALCHEMY_OUTCOME_BEHAVIOR,
                new Reference("success_behavior", value.successBehavior),
                new Reference("failure_behavior", value.failureBehavior));
    }
}
