package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraGain;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.resource.ResourceCost;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.AutoIgnoreListCodec;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.List;
import java.util.Map;

/**
 * A named cultivation activity with entity conditions and an interval action. Where it may be practised is said
 * with {@code start_condition} and {@code condition} like every other requirement: the environment is part of the
 * condition context, so a pack asks for the place it wants instead of naming a kind of aura.
 */
// TODO: may be removed. What it holds is "how an entity cultivates right now", which the rest of the system could
// keep in the state attachment rather than a datapack registry; CultivationModeService, CultivationActionService,
// AuraDistributionService and the registry key below would go with it. Marked, not scheduled.
public record CultivateAction(Component name, Component description, boolean defaultAction,
                              EntityCondition startCondition, EntityCondition condition,
                              int tickInterval,
                              List<ResourceCost> costs, NumberProvider absorbAmount,
                              Map<Holder<Aura>, NumberProvider> auraCosts, List<AuraGain> auraGains,
                              int cooldownTicks,
                              EntityAction tickAction) implements NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.CULTIVATE_ACTION.identifier());
    public static final Codec<Holder<CultivateAction>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.CULTIVATE_ACTION);
    public static final Codec<CultivateAction> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(CultivateAction::name),
            ContextNameCodec.description(CATEGORY).forGetter(CultivateAction::description),
            Codec.BOOL.optionalFieldOf("default", false).forGetter(CultivateAction::defaultAction),
            EntityCondition.optionalCodec("start_condition").forGetter(CultivateAction::startCondition),
            EntityCondition.optionalCodec("condition").forGetter(CultivateAction::condition),
            Codec.intRange(1, 72_000).optionalFieldOf("tick_interval", 20).forGetter(CultivateAction::tickInterval),
            ResourceCost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(CultivateAction::costs),
            NumberProvider.CODEC.optionalFieldOf("absorb_amount", new Constant(1.0D)).forGetter(CultivateAction::absorbAmount),
            CollectionCodecs.map(Aura.CODEC, NumberProvider.CODEC).optionalFieldOf("aura_costs", Map.of()).forGetter(CultivateAction::auraCosts),
            AutoIgnoreListCodec.create(AuraGain.CODEC).optionalFieldOf("aura_gains", List.of()).forGetter(CultivateAction::auraGains),
            Codec.intRange(0, 72_000).optionalFieldOf("cooldown", 0).forGetter(CultivateAction::cooldownTicks),
            EntityAction.optionalCodec("tick_action").forGetter(CultivateAction::tickAction)
    ).apply(i, CultivateAction::new));
}
