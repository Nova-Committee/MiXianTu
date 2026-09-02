package com.iafenvoy.mxt.data.cultivation;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.context.condition.EntityConditionContext;
import com.iafenvoy.mxt.data.trigger.Trigger;
import com.iafenvoy.mxt.data.trigger.TriggerSignal;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Optional;

/**
 * Data-driven rules shared by cultivation start and breakthrough transitions.
 * Conditions are evaluated synchronously; trigger and action are optional
 * extension points for event-driven and active transition entry points.
 */
public record CultivateConditions(List<EntityCondition> conditions,
                                  List<Trigger> triggers,
                                  Optional<EntityAction> action) {
    public static final Codec<CultivateConditions> CODEC = RecordCodecBuilder.create(i -> i.group(
            EntityCondition.CODEC.listOf().optionalFieldOf("conditions", List.of()).forGetter(CultivateConditions::conditions),
            Trigger.CODEC.listOf().optionalFieldOf("triggers", List.of()).forGetter(CultivateConditions::triggers),
            EntityAction.CODEC.optionalFieldOf("action").forGetter(CultivateConditions::action)
    ).apply(i, CultivateConditions::new));

    public static final CultivateConditions EMPTY = new CultivateConditions(List.of(), List.of(), Optional.empty());

    public boolean test(Entity entity, FormulaContext formula) {
        EntityConditionContext context = new EntityConditionContext(entity, formula);
        return this.conditions.stream().allMatch(condition -> condition.test(context));
    }

    public boolean matchesTrigger(TriggerSignal signal) {
        return this.triggers.stream().anyMatch(trigger -> trigger.matches(signal));
    }
}
