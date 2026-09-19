package com.iafenvoy.mxt.runtime.curse;

import com.iafenvoy.mxt.attachment.CurseHolderAttachment;
import com.iafenvoy.mxt.attachment.CurseHolderAttachment.State;
import com.iafenvoy.mxt.data.curse.Curse;
import com.iafenvoy.mxt.data.curse.CurseType;
import com.iafenvoy.mxt.data.trigger.Trigger;
import com.iafenvoy.mxt.data.trigger.TriggerSignal;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.runtime.trigger.TriggerDispatcher;
import com.iafenvoy.mxt.runtime.trigger.TriggerRehydrator;
import com.iafenvoy.mxt.runtime.trigger.TriggerRehydrators;
import com.iafenvoy.mxt.runtime.trigger.TriggerSubscription;
import com.iafenvoy.mxt.util.HolderHelper;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.util.Map;

/**
 * The trigger side of a curse: an {@code mxt:triggered} definition runs its periodic behaviour when the trigger
 * system publishes a signal one of its triggers matches, instead of on a tick interval.
 * <p>
 * Subscriptions are rebuilt from the persisted attachment and are runtime-only, exactly like every other
 * module's. Rebuilding is idempotent, so applying, removing, expiring and rehydrating can all ask for it.
 */
public final class CurseTriggerSubscriptions {
    private static final String MODULE = "curse";

    static {
        TriggerRehydrators.register(new TriggerRehydrator() {
            @Override
            public String module() {
                return MODULE;
            }

            @Override
            public void rehydrate(LivingEntity entity) {
                // A definition that came back from being disabled has to be scheduled again as well as subscribed.
                CurseScheduler.reschedule(entity);
                rebuild(entity);
            }
        });
    }

    private CurseTriggerSubscriptions() {
    }

    /**
     * Forces class initialization so the rehydrator is registered before the first server lifecycle event.
     */
    public static void initialize() {
    }

    /**
     * Rebuilds this entity's triggered-curse subscriptions from the curses it holds.
     */
    public static void rebuild(Entity entity) {
        if (entity.level().isClientSide()) return;
        TriggerDispatcher.clearModule(entity.getUUID(), MODULE);
        CurseHolderAttachment holder = entity.getData(MxtAttachments.CURSE_HOLDER);
        for (Map.Entry<Holder<Curse>, State> entry : holder.instances().entrySet()) {
            if (!(entry.getKey().value().typedType() instanceof CurseType.Triggered triggered)) continue;
            Identifier curseId = HolderHelper.id(entry.getKey());
            int index = 0;
            for (Trigger trigger : triggered.triggers()) {
                TriggerDispatcher.register(new TriggerSubscription(entity.getUUID(), MODULE, curseId + "/" + index++,
                        trigger, signal -> true, signal -> run(entity, entry.getKey(), signal), false));
            }
        }
    }

    /**
     * Runs the effect of one triggered curse. The held instance is re-checked when the signal arrives: a
     * subscription lives until the next rebuild, and by then the curse may be gone, frozen, or a different type.
     */
    private static void run(Entity entity, Holder<Curse> curse, TriggerSignal signal) {
        if (!entity.getData(MxtAttachments.CURSE_HOLDER).instances().containsKey(curse)) return;
        Curse definition = curse.value();
        if (!(definition.typedType() instanceof CurseType.Triggered)) return;
        if (CurseService.definitionState(curse) != CurseService.DefinitionState.ACTIVE) return;
        definition.onTick().execute(entity, signal.context().formula());
    }
}
