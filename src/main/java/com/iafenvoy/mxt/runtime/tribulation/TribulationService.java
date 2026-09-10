package com.iafenvoy.mxt.runtime.tribulation;

import com.iafenvoy.mxt.attachment.TribulationAttachment;
import com.iafenvoy.mxt.data.Tribulation;
import com.iafenvoy.mxt.data.Tribulation.Phase;
import com.iafenvoy.mxt.event.TribulationEvent.*;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Drives a persisted, multi-phase tribulation without embedding effect callbacks in attachment data.
 */
public final class TribulationService {
    private TribulationService() {
    }

    /**
     * Adds the local aura influence so every phase is scaled by the environment, not only the
     * phase that started at breakthrough.
     */
    private static FormulaContext tribulationContext(LivingEntity entity, FormulaContext context) {
        return context.with("aura_tribulation_modifier",
                AuraService.getPositionAura(entity.level(), entity.blockPosition()).rules().tribulationModify());
    }

    public static StartResult start(LivingEntity entity, TribulationAttachment data, Holder<Tribulation> tribulation, long gameTime, FormulaContext context) {
        Tribulation definition = tribulation.value();
        if (data.tribulation().isPresent()) return StartResult.rejected(Failure.ALREADY_ACTIVE);
        if (definition.phases().isEmpty()) return StartResult.rejected(Failure.INVALID_FORMULA);
        if (!definition.triggerCondition().test(entity, context)) return StartResult.rejected(Failure.CONDITIONS);
        // Every phase duration is validated before the tribulation begins. A phase duration is a
        // pure function of the definition and the context, so a phase that cannot resolve would
        // otherwise become an unresolvable failure later.
        FormulaContext phaseContext = tribulationContext(entity, context);
        for (Phase phase : definition.phases()) {
            if (duration(phase, definition, phaseContext) < 0L) return StartResult.rejected(Failure.INVALID_FORMULA);
        }
        long duration = duration(definition.phases().getFirst(), definition, phaseContext);
        if (NeoForge.EVENT_BUS.post(new StartPre(data, tribulation)).isCanceled())
            return StartResult.rejected(Failure.CANCELLED);
        data.start(tribulation, 0, Math.addExact(gameTime, duration));
        definition.phases().getFirst().startAction().execute(entity, phaseContext);
        NeoForge.EVENT_BUS.post(new StartPost(data, tribulation));
        return StartResult.started(0);
    }

    public static TickResult tick(LivingEntity entity, TribulationAttachment data, Tribulation definition, long gameTime, FormulaContext context) {
        // A paused attachment can only come from a legacy save; no runtime path pauses it now.
        if (data.tribulation().isEmpty() || data.paused()) return TickResult.idle();
        if (gameTime < data.phaseEndsAt()) return TickResult.running(data.phase());
        Holder<Tribulation> tribulation = data.tribulation().orElseThrow();
        // The aura influence is resampled once per phase transition, so later phases are scaled
        // by the environment the entity is standing in rather than the breakthrough location.
        FormulaContext phaseContext = tribulationContext(entity, context);
        int next = data.phase() + 1;
        if (next >= definition.phases().size()) {
            int previous = data.phase();
            definition.phases().get(previous).endAction().execute(entity, phaseContext);
            data.clear();
            definition.successAction().execute(entity, phaseContext);
            NeoForge.EVENT_BUS.post(new Complete(data, tribulation, previous));
            return TickResult.completed();
        }
        long duration = duration(definition.phases().get(next), definition, phaseContext);
        if (duration < 0L) {
            // Defensive only: start() already rejects definitions whose phases cannot resolve.
            data.clear();
            definition.failAction().execute(entity, phaseContext);
            return TickResult.paused(Failure.INVALID_FORMULA);
        }
        if (NeoForge.EVENT_BUS.post(new PhasePre(data, tribulation, next)).isCanceled())
            return TickResult.running(data.phase());
        data.start(tribulation, next, Math.addExact(gameTime, duration));
        definition.phases().get(next - 1).endAction().execute(entity, phaseContext);
        definition.phases().get(next).startAction().execute(entity, phaseContext);
        NeoForge.EVENT_BUS.post(new PhasePost(data, tribulation, next));
        return TickResult.advanced(next);
    }

    private static long duration(Phase phase, Tribulation definition, FormulaContext context) {
        double scale = definition.difficultyScale().evaluate(context);
        double value = phase.duration().evaluate(context) * scale * Math.max(0.0D, 1.0D + context.value("aura_tribulation_modifier"));
        return !Double.isFinite(value) || value <= 0.0D || value > Long.MAX_VALUE ? -1L : Math.max(1L, Math.round(value));
    }

    public enum Failure {ALREADY_ACTIVE, DISABLED, CONDITIONS, INVALID_FORMULA, CANCELLED}

    public record StartResult(boolean started, int phase, Failure failure) {
        static StartResult started(int phase) {
            return new StartResult(true, phase, null);
        }

        static StartResult rejected(Failure failure) {
            return new StartResult(false, -1, failure);
        }
    }

    public record TickResult(State state, int phase, Failure failure) {
        static TickResult idle() {
            return new TickResult(State.IDLE, -1, null);
        }

        static TickResult running(int phase) {
            return new TickResult(State.RUNNING, phase, null);
        }

        static TickResult advanced(int phase) {
            return new TickResult(State.ADVANCED, phase, null);
        }

        static TickResult completed() {
            return new TickResult(State.COMPLETED, -1, null);
        }

        static TickResult paused(Failure failure) {
            return new TickResult(State.PAUSED, -1, failure);
        }
    }

    public enum State {IDLE, RUNNING, ADVANCED, COMPLETED, PAUSED}
}
