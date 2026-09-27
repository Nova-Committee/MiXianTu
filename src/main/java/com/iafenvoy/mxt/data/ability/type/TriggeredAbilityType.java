package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.data.ability.ActionCarrier;
import com.iafenvoy.mxt.data.ability.CooldownSource;
import com.iafenvoy.mxt.data.ability.TargetSelector;
import com.iafenvoy.mxt.data.ability.TriggerSource;
import com.iafenvoy.mxt.data.ability.target.SelfTargetSelector;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.condition.DamageCondition;
import com.iafenvoy.mxt.data.storage.DataStorageCollector;
import com.iafenvoy.mxt.data.storage.builtin.CooldownDataStorage;
import com.iafenvoy.mxt.data.storage.builtin.ResourceDataStorage;
import com.iafenvoy.mxt.data.storage.builtin.TargetLockDataStorage;
import com.iafenvoy.mxt.data.storage.builtin.TimerDataStorage;
import com.iafenvoy.mxt.data.storage.builtin.ToggleDataStorage;
import com.iafenvoy.mxt.data.trigger.Trigger;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.LivingEntity;

import java.util.List;

/** A skill a signal wakes up: which signals, how often they get through, and the action fields that then run. */
public record TriggeredAbilityType(List<Trigger> triggers, NumberProvider chance, DamageCondition damageCondition,
                                   EntityAction entityAction, TargetSelector targetSelector,
                                   BiEntityCondition targetCondition, BiEntityAction biEntityAction,
                                   NumberProvider cooldown)
        implements AbilityType, ActionCarrier, TriggerSource, CooldownSource {
    public static final MapCodec<TriggeredAbilityType> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Trigger.CODEC.listOf().optionalFieldOf("triggers", List.of()).forGetter(TriggeredAbilityType::triggers),
            NumberProvider.CODEC.optionalFieldOf("chance", new Constant(1.0D)).forGetter(TriggeredAbilityType::chance),
            DamageCondition.optionalCodec("damage_condition").forGetter(TriggeredAbilityType::damageCondition),
            EntityAction.optionalCodec("entity_action").forGetter(TriggeredAbilityType::entityAction),
            TargetSelector.CODEC.optionalFieldOf("target_selector", SelfTargetSelector.INSTANCE).forGetter(TriggeredAbilityType::targetSelector),
            BiEntityCondition.optionalCodec("target_condition").forGetter(TriggeredAbilityType::targetCondition),
            BiEntityAction.optionalCodec("bi_entity_action").forGetter(TriggeredAbilityType::biEntityAction),
            NumberProvider.CODEC.optionalFieldOf("cooldown", new Constant(0.0D)).forGetter(TriggeredAbilityType::cooldown)
    ).apply(i, TriggeredAbilityType::new));

    @Override
    public MapCodec<TriggeredAbilityType> codec() {
        return CODEC;
    }

    // A signal pays through the same gate a press does and then runs the same fields, so the kinds are the same as
    // mxt:active's.
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

    // A chance the formula cannot answer is a chance that does not get through.
    @Override
    public boolean rolls(LivingEntity entity, FormulaContext context) {
        final double chance;
        try {
            chance = this.chance.evaluate(context);
        } catch (RuntimeException exception) {
            return false;
        }
        if (!Double.isFinite(chance) || chance <= 0.0D) return false;
        return chance >= 1.0D || entity.getRandom().nextDouble() < chance;
    }
}
