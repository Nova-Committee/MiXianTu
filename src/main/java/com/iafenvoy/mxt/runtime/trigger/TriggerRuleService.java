package com.iafenvoy.mxt.runtime.trigger;

import com.iafenvoy.mxt.attachment.TriggerCooldownAttachment;
import com.iafenvoy.mxt.data.trigger.TriggerRule;
import com.iafenvoy.mxt.data.trigger.TriggerSignal;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.cache.RegistryCaches;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.MiscUtil;
import com.iafenvoy.mxt.util.profiling.LoggerDispatcher;
import com.iafenvoy.mxt.util.profiling.LoggingLevel;
import com.mojang.logging.LogUtils;
import net.minecraft.core.Holder;
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
    private static final LoggerDispatcher LOGGER = LoggerDispatcher.of(LogUtils.getLogger(), LoggingLevel.ERROR, LoggingLevel.ERROR);
    // Guards one rule against re-entering itself through a signal its own action publishes.
    private static final ThreadLocal<Set<String>> DISPATCHING = ThreadLocal.withInitial(HashSet::new);

    private TriggerRuleService() {
    }

    public static void dispatch(TriggerSignal signal) {
        Entity actor = signal.context().actor();
        if (actor == null) return;
        List<Holder<TriggerRule>> rules = RegistryCaches.TRIGGER_RULES_BY_SIGNAL.get(signal.type());
        if (rules.isEmpty()) return;
        long gameTime = actor.level().getGameTime();
        TriggerCooldownAttachment cooldowns = actor.getExistingData(MxtAttachments.TRIGGER_COOLDOWNS).orElse(null);
        // A read-only query never creates the attachment, so the expired rows are only swept once it exists.
        if (cooldowns != null) cooldowns.clearExpired(gameTime);
        for (Holder<TriggerRule> holder : rules) {
            TriggerRule rule = holder.value();
            if (!rule.trigger().matches(signal)) continue;
            Identifier ruleId = HolderHelper.id(holder);
            if (cooldowns != null && cooldowns.isOnCooldown(holder, gameTime)) continue;
            if (!rolls(rule, signal)) continue;
            String key = ruleId + ":" + signal.type();
            if (!DISPATCHING.get().add(key)) continue;
            try {
                // The event context is the parent, so a condition or action can read the payload the
                // caller put into it and the formula values it derived from the event.
                if (!rule.condition().test(actor, signal.context())) continue;
                rule.action().execute(actor, signal.context());
            } catch (RuntimeException exception) {
                LOGGER.log("Trigger rule '" + ruleId + "' failed for signal " + signal.type(), exception);
            } finally {
                DISPATCHING.get().remove(key);
            }
            double cooldown = MiscUtil.positive(rule.cooldown().evaluate(signal.context().formula()));
            if (cooldown > 0.0D) {
                if (cooldowns == null) cooldowns = actor.getData(MxtAttachments.TRIGGER_COOLDOWNS);
                cooldowns.setCooldownUntil(holder, gameTime + Math.max(1L, Math.round(cooldown)));
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
