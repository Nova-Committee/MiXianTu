package com.iafenvoy.mxt.runtime.tribulation;

import com.iafenvoy.mxt.attachment.TribulationData;
import com.iafenvoy.mxt.data.Tribulation;
import com.iafenvoy.mxt.data.Tribulation.Phase;
import com.iafenvoy.mxt.event.TribulationEvent.Complete;
import com.iafenvoy.mxt.event.TribulationEvent.PhasePost;
import com.iafenvoy.mxt.event.TribulationEvent.PhasePre;
import com.iafenvoy.mxt.event.TribulationEvent.StartPost;
import com.iafenvoy.mxt.event.TribulationEvent.StartPre;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

/**
 * Drives a persisted, multi-phase tribulation without embedding effect callbacks in attachment data.
 */
public final class TribulationService {
    private TribulationService() {
    }

    public static StartResult start(LivingEntity entity, TribulationData data, Identifier id, Tribulation definition, long gameTime, FormulaContext context) {
        if (data.tribulation().isPresent()) return StartResult.rejected(Failure.ALREADY_ACTIVE);
        if (definition.phases().isEmpty()) return StartResult.rejected(Failure.INVALID_FORMULA);
        if (!definition.triggerCondition().test(entity, context)) return StartResult.rejected(Failure.CONDITIONS);
        long duration = duration(definition.phases().getFirst(), definition, context);
        if (duration < 0L) return StartResult.rejected(Failure.INVALID_FORMULA);
        if (NeoForge.EVENT_BUS.post(new StartPre(data, id, definition)).isCanceled())
            return StartResult.rejected(Failure.CANCELLED);
        data.start(id, 0, Math.addExact(gameTime, duration));
        definition.phases().getFirst().startAction().execute(entity, context);
        NeoForge.EVENT_BUS.post(new StartPost(data, id, definition));
        return StartResult.started(0);
    }

    public static TickResult tick(LivingEntity entity, TribulationData data, Tribulation definition, long gameTime, FormulaContext context) {
        if (data.tribulation().isEmpty() || data.paused()) return TickResult.idle();
        if (gameTime < data.phaseEndsAt()) return TickResult.running(data.phase());
        int next = data.phase() + 1;
        Identifier id = data.tribulation().orElseThrow();
        if (next >= definition.phases().size()) {
            int previous = data.phase();
            definition.phases().get(previous).endAction().execute(entity, context);
            data.clear();
            definition.successAction().execute(entity, context);
            NeoForge.EVENT_BUS.post(new Complete(data, id, definition, previous));
            return TickResult.completed();
        }
        long duration = duration(definition.phases().get(next), definition, context);
        if (duration < 0L) {
            data.setPaused(true);
            definition.failAction().execute(entity, context);
            return TickResult.paused(Failure.INVALID_FORMULA);
        }
        if (NeoForge.EVENT_BUS.post(new PhasePre(data, id, definition, next)).isCanceled())
            return TickResult.running(data.phase());
        data.start(id, next, Math.addExact(gameTime, duration));
        definition.phases().get(next - 1).endAction().execute(entity, context);
        definition.phases().get(next).startAction().execute(entity, context);
        NeoForge.EVENT_BUS.post(new PhasePost(data, id, definition, next));
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
