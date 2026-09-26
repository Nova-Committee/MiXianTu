package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.AbilityContext;
import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.data.ability.ActionCarrier;
import com.iafenvoy.mxt.data.ability.TargetSelector;
import com.iafenvoy.mxt.data.ability.target.SelfTargetSelector;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.storage.DataStorageCollector;
import com.iafenvoy.mxt.data.storage.builtin.ResourceDataStorage;
import com.iafenvoy.mxt.data.storage.builtin.TargetLockDataStorage;
import com.iafenvoy.mxt.data.storage.builtin.TimerDataStorage;
import com.iafenvoy.mxt.data.storage.builtin.ToggleDataStorage;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A passive that acts on its own cadence: every {@code interval} ticks, for as long as its condition holds, it runs
 * the caster's action and then reaches whatever its own {@code target_selector} picks.
 */
public record IntervalAbilityType(NumberProvider interval, EntityAction entityAction, TargetSelector targetSelector,
                                  BiEntityCondition targetCondition, BiEntityAction biEntityAction)
        implements AbilityType, ActionCarrier {
    public static final double DEFAULT_INTERVAL = 20.0D;
    public static final MapCodec<IntervalAbilityType> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.optionalFieldOf("interval", new Constant(DEFAULT_INTERVAL)).forGetter(IntervalAbilityType::interval),
            EntityAction.optionalCodec("entity_action").forGetter(IntervalAbilityType::entityAction),
            TargetSelector.CODEC.optionalFieldOf("target_selector", SelfTargetSelector.INSTANCE).forGetter(IntervalAbilityType::targetSelector),
            BiEntityCondition.optionalCodec("target_condition").forGetter(IntervalAbilityType::targetCondition),
            BiEntityAction.optionalCodec("bi_entity_action").forGetter(IntervalAbilityType::biEntityAction)
    ).apply(i, IntervalAbilityType::new));

    @Override
    public MapCodec<IntervalAbilityType> codec() {
        return CODEC;
    }

    // Nothing here is pressed and nothing here pays: the cadence runs the action fields by itself, so the only kinds
    // it keeps are the four a pack writes from inside them.
    @Override
    public void createComponents(Ability ability, DataStorageCollector collector) {
        AbilityType.super.createComponents(ability, collector);
        collector.add(ToggleDataStorage.INSTANCE);
        collector.add(TimerDataStorage.INSTANCE);
        collector.add(ResourceDataStorage.INSTANCE);
        collector.add(TargetLockDataStorage.INSTANCE);
    }

    @Override
    public int tickInterval(AbilityContext context) {
        return AbilityType.cadence(this.interval, context.formula());
    }

    @Override
    public void activeTick(AbilityContext context) {
        this.execute(context.holder(), context.formula(), null);
    }
}
