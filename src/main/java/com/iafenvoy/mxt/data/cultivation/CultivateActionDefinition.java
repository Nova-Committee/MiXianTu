package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.registry.BehaviorReferences;
import com.iafenvoy.mxt.registry.BehaviorReferences.Reference;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * A named cultivation activity. Behaviour identifiers are resolved by the cultivation action registry.
 */
public record CultivateActionDefinition(List<Identifier> startConditions,
                                        List<Identifier> stopConditions, int tickInterval,
                                        List<Identifier> environmentTags,
                                        List<ResourceCost> costs, NumberProvider progressGain, NumberProvider auraCost,
                                        int cooldownTicks, Optional<Identifier> gainBehavior) {
    public static final Codec<CultivateActionDefinition> CODEC = RecordCodecBuilder.<CultivateActionDefinition>create(instance -> instance.group(
            Identifier.CODEC.listOf().optionalFieldOf("start_conditions", List.of()).forGetter(CultivateActionDefinition::startConditions), Identifier.CODEC.listOf().optionalFieldOf("stop_conditions", List.of()).forGetter(CultivateActionDefinition::stopConditions),
            Codec.intRange(1, 72_000).optionalFieldOf("tick_interval", 20).forGetter(CultivateActionDefinition::tickInterval), Identifier.CODEC.listOf().optionalFieldOf("environment_tags", List.of()).forGetter(CultivateActionDefinition::environmentTags),
            ResourceCost.CODEC.listOf().optionalFieldOf("costs", List.of()).forGetter(CultivateActionDefinition::costs),
            NumberProvider.CODEC.optionalFieldOf("progress_gain", new Constant(0.0D)).forGetter(CultivateActionDefinition::progressGain),
            NumberProvider.CODEC.optionalFieldOf("aura_cost", new Constant(0.0D)).forGetter(CultivateActionDefinition::auraCost),
            Codec.intRange(0, 72_000).optionalFieldOf("cooldown", 0).forGetter(CultivateActionDefinition::cooldownTicks),
            Identifier.CODEC.optionalFieldOf("gain_behavior").forGetter(CultivateActionDefinition::gainBehavior)
    ).apply(instance, CultivateActionDefinition::new)).validate(value -> BehaviorReferences.validate(value, MxtTypeRegistries.CULTIVATION_OUTCOME_BEHAVIOR,
            new Reference("gain_behavior", value.gainBehavior)));
}
