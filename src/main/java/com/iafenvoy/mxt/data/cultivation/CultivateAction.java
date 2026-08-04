package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.data.resource.ResourceGain;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.builtin.entity.meta.NoOpAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.meta.AlwaysTrueEntityCondition;
import com.iafenvoy.mxt.data.condition.builtin.entity.meta.NeverEntityCondition;
import com.iafenvoy.mxt.util.codec.AutoIgnoreListCodec;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.NumberProvider.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * A named cultivation activity with entity conditions and an interval action.
 */
public record CultivateAction(EntityCondition startCondition, EntityCondition stopCondition,
                              int tickInterval, List<Identifier> environmentTags, List<ResourceCost> costs,
                              NumberProvider progressGain, NumberProvider auraCost, List<ResourceGain> auraGains,
                              int cooldownTicks, EntityAction tickAction) {
    public static final Codec<CultivateAction> CODEC = RecordCodecBuilder.<CultivateAction>create(instance -> instance.group(
            EntityCondition.CODEC.optionalFieldOf("start_condition", AlwaysTrueEntityCondition.INSTANCE).forGetter(CultivateAction::startCondition),
            EntityCondition.CODEC.optionalFieldOf("stop_condition", NeverEntityCondition.INSTANCE).forGetter(CultivateAction::stopCondition),
            Codec.intRange(1, 72_000).optionalFieldOf("tick_interval", 20).forGetter(CultivateAction::tickInterval),
            Identifier.CODEC.listOf().optionalFieldOf("environment_tags", List.of()).forGetter(CultivateAction::environmentTags),
            ResourceCost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(CultivateAction::costs),
            NumberProvider.CODEC.optionalFieldOf("progress_gain", new Constant(0.0D)).forGetter(CultivateAction::progressGain),
            NumberProvider.CODEC.optionalFieldOf("aura_cost", new Constant(0.0D)).forGetter(CultivateAction::auraCost),
            AutoIgnoreListCodec.create(ResourceGain.CODEC).optionalFieldOf("aura_gains", List.of()).forGetter(CultivateAction::auraGains),
            Codec.intRange(0, 72_000).optionalFieldOf("cooldown", 0).forGetter(CultivateAction::cooldownTicks),
            EntityAction.CODEC.optionalFieldOf("tick_action", NoOpAction.INSTANCE).forGetter(CultivateAction::tickAction)
    ).apply(instance, CultivateAction::new));
}
