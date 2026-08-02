package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.data.resource.ResourceGain;
import com.iafenvoy.mxt.registry.BehaviorReferences;
import com.iafenvoy.mxt.registry.BehaviorReferences.Reference;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.runtime.cultivation.CultivationCondition;
import com.iafenvoy.mxt.util.codec.AutoIgnoreListCodec;
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
public record CultivateAction(List<CultivationCondition> startConditions, List<CultivationCondition> stopConditions,
                              int tickInterval, List<Identifier> environmentTags, List<ResourceCost> costs,
                              NumberProvider progressGain, NumberProvider auraCost, List<ResourceGain> auraGains,
                              int cooldownTicks, Optional<Identifier> gainBehavior) {
    public static final Codec<CultivateAction> CODEC = RecordCodecBuilder.<CultivateAction>create(instance -> instance.group(
            AutoIgnoreListCodec.create(MxtTypeRegistries.CULTIVATION_CONDITION.byNameCodec()).optionalFieldOf("start_conditions", List.of()).forGetter(CultivateAction::startConditions),
            AutoIgnoreListCodec.create(MxtTypeRegistries.CULTIVATION_CONDITION.byNameCodec()).optionalFieldOf("stop_conditions", List.of()).forGetter(CultivateAction::stopConditions),
            Codec.intRange(1, 72_000).optionalFieldOf("tick_interval", 20).forGetter(CultivateAction::tickInterval),
            Identifier.CODEC.listOf().optionalFieldOf("environment_tags", List.of()).forGetter(CultivateAction::environmentTags),
            ResourceCost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(CultivateAction::costs),
            NumberProvider.CODEC.optionalFieldOf("progress_gain", new Constant(0.0D)).forGetter(CultivateAction::progressGain),
            NumberProvider.CODEC.optionalFieldOf("aura_cost", new Constant(0.0D)).forGetter(CultivateAction::auraCost),
            AutoIgnoreListCodec.create(ResourceGain.CODEC).optionalFieldOf("aura_gains", List.of()).forGetter(CultivateAction::auraGains),
            Codec.intRange(0, 72_000).optionalFieldOf("cooldown", 0).forGetter(CultivateAction::cooldownTicks),
            Identifier.CODEC.optionalFieldOf("gain_behavior").forGetter(CultivateAction::gainBehavior)
    ).apply(instance, CultivateAction::new)).validate(value -> BehaviorReferences.validate(value, MxtTypeRegistries.CULTIVATION_OUTCOME_BEHAVIOR,
            new Reference("gain_behavior", value.gainBehavior)));
}
