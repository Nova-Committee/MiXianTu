package com.iafenvoy.mxt.data.trigger;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.formula.number.Constant;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

/**
 * A datapack event rule: when a published signal is matched by {@code trigger}, the condition is evaluated
 * against the actor with the event's formula context, and the action runs while it holds. A rule owns
 * nothing, so a content pack can turn any published signal into an effect.
 *
 * <p>{@code chance} and {@code cooldown} keep one signal from firing the same rule every tick: the chance is
 * rolled per matching signal, and a cooldown is counted from the tick the action ran, per actor.
 */
public record TriggerRule(Trigger trigger, EntityCondition condition, EntityAction action,
                          NumberProvider chance, NumberProvider cooldown) {
    public static final Codec<Holder<TriggerRule>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.TRIGGER);
    public static final Codec<TriggerRule> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Trigger.CODEC.fieldOf("trigger").forGetter(TriggerRule::trigger),
            EntityCondition.optionalCodec("condition").forGetter(TriggerRule::condition),
            EntityAction.optionalCodec("action").forGetter(TriggerRule::action),
            NumberProvider.CODEC.optionalFieldOf("chance", new Constant(1.0D)).forGetter(TriggerRule::chance),
            NumberProvider.CODEC.optionalFieldOf("cooldown", new Constant(0.0D)).forGetter(TriggerRule::cooldown)
    ).apply(i, TriggerRule::new));
}
