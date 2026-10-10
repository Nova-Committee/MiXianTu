package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.api.NamedDefinition;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraGain;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.cost.Costs;
import com.iafenvoy.mxt.data.cost.builtin.AuraCost;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.DefinitionText;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.iafenvoy.mxt.util.codec.ContextNameCodec;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.iafenvoy.mxt.util.codec.TolerantListCodec;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.List;
import java.util.Optional;

/**
 * A named cultivation method: what a body does while sitting, and what the session asks of it. The two condition and
 * action pairs divide the work: {@code tick_condition} carries the session on and {@code tick_action} runs on every
 * tick, while {@code cultivate_condition} decides whether a tick yields anything and {@code cultivate_action} is what
 * such a yielding tick runs. The environment is part of the condition context, so a pack asks for the place it wants
 * instead of naming a kind of aura; {@code abort_reason} names the upkeep abort alone.
 */
public record Cultivation(Component name, Component description, int priority,
                          EntityCondition startCondition,
                          EntityCondition cultivateCondition, EntityAction cultivateAction,
                          EntityCondition tickCondition, EntityAction tickAction,
                          int tickInterval,
                          List<Cost> costs, NumberProvider absorbAmount,
                          List<Cost> auraCosts, List<AuraGain> auraGains,
                          int cooldownTicks,
                          Optional<Component> abortReason) implements NamedDefinition {
    private static final String CATEGORY = DefinitionText.category(MxtResourceKeys.CULTIVATION.identifier());
    // The pre-Cost aura map is still read as a list of mxt:aura entries; writing always emits the list form.
    private static final Codec<List<Cost>> AURA_COSTS = Codec.either(
            CollectionCodecs.map(Aura.CODEC, NumberProvider.CODEC), Cost.LIST_CODEC).xmap(
            either -> either.map(values -> values.entrySet().stream()
                    .map(entry -> (Cost) new AuraCost(entry.getKey(), entry.getValue())).toList(), value -> value),
            Either::right).validate(Costs::validateAuras);
    public static final Codec<Holder<Cultivation>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.CULTIVATION);
    public static final Codec<Cultivation> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            ContextNameCodec.name(CATEGORY).forGetter(Cultivation::name),
            ContextNameCodec.description(CATEGORY).forGetter(Cultivation::description),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(Cultivation::priority),
            EntityCondition.optionalCodec("start_condition").forGetter(Cultivation::startCondition),
            EntityCondition.optionalCodec("cultivate_condition").forGetter(Cultivation::cultivateCondition),
            EntityAction.optionalCodec("cultivate_action").forGetter(Cultivation::cultivateAction),
            EntityCondition.optionalCodec("tick_condition").forGetter(Cultivation::tickCondition),
            EntityAction.optionalCodec("tick_action").forGetter(Cultivation::tickAction),
            Codec.intRange(1, 72_000).optionalFieldOf("tick_interval", 20).forGetter(Cultivation::tickInterval),
            Cost.LIST_CODEC.optionalFieldOf("costs", List.of()).forGetter(Cultivation::costs),
            NumberProvider.CODEC.optionalFieldOf("absorb_amount", new Constant(1.0D)).forGetter(Cultivation::absorbAmount),
            AURA_COSTS.optionalFieldOf("aura_costs", List.of()).forGetter(Cultivation::auraCosts),
            TolerantListCodec.create(AuraGain.CODEC).optionalFieldOf("aura_gains", List.of()).forGetter(Cultivation::auraGains),
            Codec.intRange(0, 72_000).optionalFieldOf("cooldown", 0).forGetter(Cultivation::cooldownTicks),
            MiscCodecs.TRANSLATABLE_COMPONENT.optionalFieldOf("abort_reason").forGetter(Cultivation::abortReason)
    ).apply(i, Cultivation::new));
}
