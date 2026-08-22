package com.iafenvoy.mxt.data.alchemy;

import com.iafenvoy.mxt.data.action.BlockAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.builtin.block.meta.NoOpBlockAction;
import com.iafenvoy.mxt.data.action.builtin.entity.meta.NoOpAction;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;

import java.util.List;
import java.util.Map;

/**
 * Server-side alchemy state machines consume this immutable recipe configuration.
 */
public record AlchemyRecipe(List<Identifier> inputs, NumberProvider targetTemperature,
                            NumberProvider temperatureTolerance, int minimumFurnaceTier, NumberProvider duration,
                            List<Identifier> auraKinds, Map<Holder<Element>, NumberProvider> minimumAura, List<Identifier> successOutputs,
                            List<Identifier> failureOutputs, EntityAction successAction, EntityAction failureAction,
                            BlockAction successBlockAction, BlockAction failureBlockAction) {
    public static final Codec<AlchemyRecipe> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.listOf(1, Integer.MAX_VALUE).fieldOf("inputs").forGetter(AlchemyRecipe::inputs),
            NumberProvider.CODEC.fieldOf("target_temperature").forGetter(AlchemyRecipe::targetTemperature),
            NumberProvider.CODEC.optionalFieldOf("temperature_tolerance", new Constant(0.0D)).forGetter(AlchemyRecipe::temperatureTolerance),
            Codec.INT.optionalFieldOf("minimum_furnace_tier", 0).forGetter(AlchemyRecipe::minimumFurnaceTier),
            NumberProvider.CODEC.fieldOf("duration").forGetter(AlchemyRecipe::duration),
            Identifier.CODEC.listOf().optionalFieldOf("aura_kinds", List.of()).forGetter(AlchemyRecipe::auraKinds),
            CollectionCodecs.map(Element.CODEC, NumberProvider.CODEC).optionalFieldOf("minimum_aura", Map.of()).forGetter(AlchemyRecipe::minimumAura),
            Identifier.CODEC.listOf(1, Integer.MAX_VALUE).fieldOf("success_outputs").forGetter(AlchemyRecipe::successOutputs),
            Identifier.CODEC.listOf().optionalFieldOf("failure_outputs", List.of()).forGetter(AlchemyRecipe::failureOutputs),
            EntityAction.CODEC.optionalFieldOf("success_action", NoOpAction.INSTANCE).forGetter(AlchemyRecipe::successAction),
            EntityAction.CODEC.optionalFieldOf("failure_action", NoOpAction.INSTANCE).forGetter(AlchemyRecipe::failureAction),
            BlockAction.CODEC.optionalFieldOf("success_block_action", NoOpBlockAction.INSTANCE).forGetter(AlchemyRecipe::successBlockAction),
            BlockAction.CODEC.optionalFieldOf("failure_block_action", NoOpBlockAction.INSTANCE).forGetter(AlchemyRecipe::failureBlockAction)
    ).apply(i, AlchemyRecipe::new));
}
