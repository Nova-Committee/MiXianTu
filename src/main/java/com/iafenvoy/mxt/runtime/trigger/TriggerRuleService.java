package com.iafenvoy.mxt.runtime.trigger;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.trigger.TriggerRule;
import com.iafenvoy.mxt.data.trigger.TriggerSignal;
import com.iafenvoy.mxt.runtime.ServerCache;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder.Reference;
import net.minecraft.world.entity.Entity;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Runs the datapack event rules of a published signal. Rules are indexed by the signal their trigger names and
 * validated while the server cache is built, so publishing costs one map lookup; a rule needs an actor, so an
 * actor-less signal reaches subscriptions only.
 */
public final class TriggerRuleService {
    /**
     * Guards one rule against re-entering itself through a signal its own action publishes.
     */
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
        for (Reference<TriggerRule> holder : rules) {
            TriggerRule rule = holder.value();
            if (!rule.trigger().matches(signal)) continue;
            String key = HolderHelper.id(holder) + ":" + signal.type();
            if (!DISPATCHING.get().add(key)) continue;
            try {
                // The event context is the parent, so a condition or action can read the payload the
                // caller put into it and the formula values it derived from the event.
                if (!rule.condition().test(actor, signal.context())) continue;
                rule.action().execute(actor, signal.context());
            } catch (RuntimeException exception) {
                MiXianTu.LOGGER.error("Trigger rule {} failed for signal {}", HolderHelper.id(holder), signal.type(), exception);
            } finally {
                DISPATCHING.get().remove(key);
            }
        }
        if (DISPATCHING.get().isEmpty()) DISPATCHING.remove();
    }
}
