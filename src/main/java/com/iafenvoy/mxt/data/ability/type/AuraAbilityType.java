package com.iafenvoy.mxt.data.ability.type;

import com.iafenvoy.mxt.data.action.BiEntityAction;
import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.ability.Ability;
import com.iafenvoy.mxt.data.ability.AbilityContext;
import com.iafenvoy.mxt.data.ability.AbilityType;
import com.iafenvoy.mxt.data.ability.ActionCarrier;
import com.iafenvoy.mxt.data.ability.CooldownSource;
import com.iafenvoy.mxt.data.ability.TargetSelector;
import com.iafenvoy.mxt.data.ability.target.SelfTargetSelector;
import com.iafenvoy.mxt.data.condition.BiEntityCondition;
import com.iafenvoy.mxt.data.storage.DataStorageCollector;
import com.iafenvoy.mxt.data.storage.builtin.CooldownDataStorage;
import com.iafenvoy.mxt.data.storage.builtin.ResourceDataStorage;
import com.iafenvoy.mxt.data.storage.builtin.TargetLockDataStorage;
import com.iafenvoy.mxt.data.storage.builtin.TimerDataStorage;
import com.iafenvoy.mxt.data.storage.builtin.ToggleDataStorage;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;

/**
 * A skill whose own part is the reach: every {@code interval} it hands the bi-entity half of its action fields to
 * every entity inside {@code radius}. The selector does not take part in a pulse - the radius is what picks targets.
 */
public record AuraAbilityType(NumberProvider interval, NumberProvider radius, EntityAction entityAction,
                              TargetSelector targetSelector, BiEntityCondition targetCondition,
                              BiEntityAction biEntityAction,
                              NumberProvider cooldown) implements AbilityType, ActionCarrier, CooldownSource {
    public static final MapCodec<AuraAbilityType> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            NumberProvider.CODEC.optionalFieldOf("interval", new Constant(20.0D)).forGetter(AuraAbilityType::interval),
            NumberProvider.CODEC.optionalFieldOf("radius", new Constant(4.0D)).forGetter(AuraAbilityType::radius),
            EntityAction.optionalCodec("entity_action").forGetter(AuraAbilityType::entityAction),
            TargetSelector.CODEC.optionalFieldOf("target_selector", SelfTargetSelector.INSTANCE).forGetter(AuraAbilityType::targetSelector),
            BiEntityCondition.optionalCodec("target_condition").forGetter(AuraAbilityType::targetCondition),
            BiEntityAction.optionalCodec("bi_entity_action").forGetter(AuraAbilityType::biEntityAction),
            NumberProvider.CODEC.optionalFieldOf("cooldown", new Constant(0.0D)).forGetter(AuraAbilityType::cooldown)
    ).apply(i, AuraAbilityType::new));

    @Override
    public MapCodec<AuraAbilityType> codec() {
        return CODEC;
    }

    // Firing it pays through the shared gate, and every pulse runs the bi-entity half of the action fields against
    // each target, so a pack can write the same four kinds from inside them.
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
    public int tickInterval(AbilityContext context) {
        return AbilityType.cadence(this.interval, context.formula());
    }

    @Override
    public void activeTick(AbilityContext context) {
        LivingEntity actor = context.holder();
        FormulaContext formula = context.formula();
        double reach = this.radius.evaluate(formula);
        if (!Double.isFinite(reach) || reach < 0.0D) return;
        double reachSquared = reach * reach;
        for (Entity target : actor.level().getEntities(actor, actor.getBoundingBox().inflate(reach))) {
            double distanceSquared = actor.distanceToSqr(target);
            if (distanceSquared > reachSquared) continue;
            FormulaContext targetContext = FormulaContext.of(actor, Map.of("aura_radius", reach, "distance", Math.sqrt(distanceSquared)));
            this.executeOn(actor, target, targetContext, null);
        }
    }
}
