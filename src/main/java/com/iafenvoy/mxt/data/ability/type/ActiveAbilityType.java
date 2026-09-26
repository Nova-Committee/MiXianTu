package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.data.ability.ActionCarrier;
import com.iafenvoy.mxt.data.ability.CooldownSource;
import com.iafenvoy.mxt.data.ability.TargetSelector;
import com.iafenvoy.mxt.data.ability.Togglable;
import com.iafenvoy.mxt.data.ability.ToggleContext;
import com.iafenvoy.mxt.data.ability.target.SelfTargetSelector;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
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

/**
 * A skill a player presses for. It is also the first {@link Togglable}: the press runs the whole cast pipeline,
 * which pays for itself and then runs the action fields, so the shared press gate is skipped rather than applied
 * twice. Which cell it sits in is the player's own wheel layout, never a property of the skill, so the removed
 * {@code slot} field is ignored like any other field this type does not read.
 */
public record ActiveAbilityType(EntityAction entityAction, TargetSelector targetSelector, BiEntityCondition targetCondition,
                                BiEntityAction biEntityAction,
                                NumberProvider cooldown) implements AbilityType, ActionCarrier, Togglable, CooldownSource {
    public static final MapCodec<ActiveAbilityType> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EntityAction.optionalCodec("entity_action").forGetter(ActiveAbilityType::entityAction),
            TargetSelector.CODEC.optionalFieldOf("target_selector", SelfTargetSelector.INSTANCE).forGetter(ActiveAbilityType::targetSelector),
            BiEntityCondition.optionalCodec("target_condition").forGetter(ActiveAbilityType::targetCondition),
            BiEntityAction.optionalCodec("bi_entity_action").forGetter(ActiveAbilityType::biEntityAction),
            NumberProvider.CODEC.optionalFieldOf("cooldown", new Constant(0.0D)).forGetter(ActiveAbilityType::cooldown)
    ).apply(i, ActiveAbilityType::new));

    @Override
    public MapCodec<ActiveAbilityType> codec() {
        return CODEC;
    }

    // The press pays through the shared gate and then runs its own action fields, so it keeps the payment state and
    // the four kinds a pack writes from inside those actions.
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

    // A cast pays inside its own transaction, which also owns its cooldown and its charges.
    @Override
    public boolean gated(ToggleContext context) {
        return false;
    }

    @Override
    public Result activate(ToggleContext context) {
        return AbilityActivationService.cast(context);
    }
}
