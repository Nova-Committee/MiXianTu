package com.iafenvoy.mxt.runtime.trigger;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.TriggerCooldownAttachment;
import com.iafenvoy.mxt.data.trigger.TriggerRule;
import com.iafenvoy.mxt.data.trigger.TriggerSignal;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder.Reference;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs the datapack event rules of a published signal. Rules are indexed by the signal their trigger names and
 * validated while the server cache is built, so publishing costs one map lookup; a rule needs an actor, so an
 * actor-less signal reaches subscriptions only.
 *
 * <p>A rule's own {@code chance} and {@code cooldown} are read here rather than through a condition, because a
 * cooldown has to be written whether or not the pack thought to declare a storage component for it.
 */
public final class TriggerRuleService {
    // Guards one rule against re-entering itself through a signal its own action publishes.
    private static final ThreadLocal<Set<String>> DISPATCHING = ThreadLocal.withInitial(HashSet::new);

    private TriggerRuleService() {
    }

    public static void dispatch(TriggerSignal signal) {
        ServerCache cache = ServerCache.get().orElse(null);
        if (cache == null) return;
        Entity actor = signal.context().actor();
        if (actor == null) return;
        List<Reference<TriggerRule>> rules = cache.triggerRules(signal.type());
        if (rules.isEmpty()) return;
        long gameTime = actor.level().getGameTime();
        TriggerCooldownAttachment cooldowns = actor.getExistingData(MxtAttachments.TRIGGER_COOLDOWNS).orElse(null);
        // A read-only query never creates the attachment, so the expired rows are only swept once it exists.
        if (cooldowns != null) cooldowns.clearExpired(gameTime);
        for (Reference<TriggerRule> holder : rules) {
            TriggerRule rule = holder.value();
            if (!rule.trigger().matches(signal)) continue;
            Identifier ruleId = HolderHelper.id(holder);
            if (cooldowns != null && cooldowns.isOnCooldown(ruleId, gameTime)) continue;
            if (!rolls(rule, signal)) continue;
            String key = ruleId + ":" + signal.type();
            if (!DISPATCHING.get().add(key)) continue;
            try {
                // The event context is the parent, so a condition or action can read the payload the
                // caller put into it and the formula values it derived from the event.
                if (!rule.condition().test(actor, signal.context())) continue;
                rule.action().execute(actor, signal.context());
            } catch (RuntimeException exception) {
                MiXianTu.LOGGER.error("Trigger rule {} failed for signal {}", ruleId, signal.type(), exception);
            } finally {
                DISPATCHING.get().remove(key);
            }
            double cooldown = rule.cooldown().evaluate(signal.context().formula());
            if (Double.isFinite(cooldown) && cooldown > 0.0D) {
                if (cooldowns == null) cooldowns = actor.getData(MxtAttachments.TRIGGER_COOLDOWNS);
                cooldowns.setCooldownUntil(ruleId, gameTime + Math.max(1L, Math.round(cooldown)));
            }
        }
        if (DISPATCHING.get().isEmpty()) DISPATCHING.remove();
    }

    // A chance of 1 or more always fires and 0 or less never does; a value that is not a number leaves the rule
    // firing exactly as it did before the field existed, rather than silently switching it off.
    private static boolean rolls(TriggerRule rule, TriggerSignal signal) {
        double chance = rule.chance().evaluate(signal.context().formula());
        if (!Double.isFinite(chance)) return true;
        if (chance >= 1.0D) return true;
        if (chance <= 0.0D) return false;
        Entity actor = signal.context().actor();
        return actor != null && actor.getRandom().nextDouble() < chance;
    }
}
