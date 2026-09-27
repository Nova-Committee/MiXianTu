package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.data.ability.ActionCarrier;
import com.iafenvoy.mxt.data.ability.ChannelSource;
import com.iafenvoy.mxt.data.ability.CooldownSource;
import com.iafenvoy.mxt.data.ability.TargetSelector;
import com.iafenvoy.mxt.data.ability.Togglable;
import com.iafenvoy.mxt.data.ability.ToggleContext;
import com.iafenvoy.mxt.data.ability.target.SelfTargetSelector;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.cost.Cost;
import com.iafenvoy.mxt.data.storage.DataStorageCollector;
import com.iafenvoy.mxt.data.storage.builtin.CooldownDataStorage;
import com.iafenvoy.mxt.data.storage.builtin.ResourceDataStorage;
import com.iafenvoy.mxt.data.storage.builtin.TargetLockDataStorage;
import com.iafenvoy.mxt.data.storage.builtin.TimerDataStorage;
import com.iafenvoy.mxt.data.storage.builtin.ToggleDataStorage;
import com.iafenvoy.mxt.runtime.ability.AbilityActivationService;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * A skill that stays on while its upkeep is paid: its own part is the pulse, and every pulse - activation included -
 * runs the action fields again.
 */
public record ChannelledAbilityType(NumberProvider tickInterval, List<Cost> upkeepCosts, EntityAction entityAction,
                                    TargetSelector targetSelector, BiEntityCondition targetCondition,
                                    BiEntityAction biEntityAction, NumberProvider cooldown)
        implements AbilityType, ActionCarrier, ChannelSource, Togglable, CooldownSource {
    public static final MapCodec<ChannelledAbilityType> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.optionalFieldOf("tick_interval", new Constant(1.0D)).forGetter(ChannelledAbilityType::tickInterval),
            Cost.LIST_CODEC.optionalFieldOf("upkeep_costs", List.of()).forGetter(ChannelledAbilityType::upkeepCosts),
            EntityAction.optionalCodec("entity_action").forGetter(ChannelledAbilityType::entityAction),
            TargetSelector.CODEC.optionalFieldOf("target_selector", SelfTargetSelector.INSTANCE).forGetter(ChannelledAbilityType::targetSelector),
            BiEntityCondition.optionalCodec("target_condition").forGetter(ChannelledAbilityType::targetCondition),
            BiEntityAction.optionalCodec("bi_entity_action").forGetter(ChannelledAbilityType::biEntityAction),
            NumberProvider.CODEC.optionalFieldOf("cooldown", new Constant(0.0D)).forGetter(ChannelledAbilityType::cooldown)
    ).apply(i, ChannelledAbilityType::new));

    @Override
    public MapCodec<ChannelledAbilityType> codec() {
        return CODEC;
    }

    // The cast pays once and every upkeep pulse pays again, and each of those moments runs the action fields. Its
    // pulse cursor is not declared here: the runtime owns that value, so content has no business writing it.
    @Override
    public void createComponents(Ability ability, DataStorageCollector collector) {
        AbilityType.super.createComponents(ability, collector);
        collector.add(CooldownDataStorage.INSTANCE);
        ability.charges().ifPresent(charges -> collector.add(charges.declared()));
        collector.add(ToggleDataStorage.INSTANCE);
        collector.add(TimerDataStorage.INSTANCE);
        collector.add(ResourceDataStorage.INSTANCE);
        collector.add(TargetLockDataStorage.INSTANCE);
    }

    @Override
    public NumberProvider channelInterval() {
        return this.tickInterval;
    }

    @Override
    public List<Cost> upkeepCosts() {
        return this.upkeepCosts;
    }

    // Starting a channel is a cast; the upkeep pulses are paid where they are taken.
    @Override
    public boolean gated(ToggleContext context) {
        return false;
    }

    @Override
    public Result activate(ToggleContext context) {
        return AbilityActivationService.cast(context);
    }
}
