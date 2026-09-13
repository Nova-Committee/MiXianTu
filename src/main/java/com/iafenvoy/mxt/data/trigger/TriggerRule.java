package com.iafenvoy.mxt.data.trigger;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

/**
 * A datapack event rule: when a published signal is matched by {@code trigger}, the condition is
 * evaluated against the actor with the event's formula context, and the action runs while it holds.
 *
 * <p>This is the data-driven side of the trigger system. The intrinsic {@code mxt:trigger_type}
 * registry decides how a signal is matched - a built-in signal matches by its type, and a scripted
 * matcher may inspect the whole signal - while abilities use the same matchers for triggers they own.
 * A rule owns nothing: it is a standalone reaction, so a content pack can turn any published signal
 * into an effect such as adding a resource.</p>
 */
public record TriggerRule(Trigger trigger, EntityCondition condition, EntityAction action) {
    public static final Codec<Holder<TriggerRule>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.TRIGGER);
    public static final Codec<TriggerRule> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            Trigger.CODEC.fieldOf("trigger").forGetter(TriggerRule::trigger),
            EntityCondition.optionalCodec("condition").forGetter(TriggerRule::condition),
            EntityAction.optionalCodec("action").forGetter(TriggerRule::action)
    ).apply(i, TriggerRule::new));
}
