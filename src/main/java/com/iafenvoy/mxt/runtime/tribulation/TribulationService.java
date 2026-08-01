package com.iafenvoy.mxt.runtime.tribulation;

import com.iafenvoy.mxt.attachment.TribulationData;
import com.iafenvoy.mxt.data.tribulation.TribulationDefinition;
import com.iafenvoy.mxt.data.tribulation.TribulationDefinition.Phase;
import com.iafenvoy.mxt.event.TribulationEvent;
import com.iafenvoy.mxt.event.TribulationEvent.Complete;
import com.iafenvoy.mxt.event.TribulationEvent.PhasePost;
import com.iafenvoy.mxt.event.TribulationEvent.PhasePre;
import com.iafenvoy.mxt.event.TribulationEvent.StartPost;
import com.iafenvoy.mxt.event.TribulationEvent.StartPre;
import com.iafenvoy.mxt.registry.MxtTypeRegistries;
import com.iafenvoy.mxt.runtime.behavior.BehaviorContext;
import com.iafenvoy.mxt.runtime.behavior.BehaviorContext.Kind;
import com.iafenvoy.mxt.runtime.behavior.DomainBehaviorService;
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

    public static StartResult start(TribulationData data, Identifier id, TribulationDefinition definition, long gameTime, FormulaContext context) {
        if (data.tribulation().isPresent()) return StartResult.rejected(Failure.ALREADY_ACTIVE);
        if (definition.phases().isEmpty()) return StartResult.rejected(Failure.INVALID_FORMULA);
        long duration = duration(definition.phases().getFirst(), definition, context);
        if (duration < 0L) return StartResult.rejected(Failure.INVALID_FORMULA);
        if (NeoForge.EVENT_BUS.post(new StartPre(data, id, definition)).isCanceled())
            return StartResult.rejected(Failure.CANCELLED);
        data.start(id, 0, Math.addExact(gameTime, duration));
        DomainBehaviorService.execute(MxtTypeRegistries.TRIBULATION_STAGE_BEHAVIOR, definition.phases().getFirst().startBehavior(), BehaviorContext.of(
                Kind.TRIBULATION_PHASE_START, id, null, context, true));
        NeoForge.EVENT_BUS.post(new StartPost(data, id, definition));
        return StartResult.started(0);
    }

    /**
     * Entity-aware entry point for data-defined trigger conditions.
     */
    public static StartResult start(LivingEntity entity, TribulationData data, Identifier id, TribulationDefinition definition, long gameTime, FormulaContext context) {
        boolean allowed = definition.triggerConditions().stream().allMatch(condition -> MxtTypeRegistries.CULTIVATION_CONDITION.get(condition)
                .map(reference -> reference.value().test(entity, context)).orElse(false));
        return allowed ? start(data, id, definition, gameTime, context) : StartResult.rejected(Failure.CONDITIONS);
    }

    public static TickResult tick(TribulationData data, TribulationDefinition definition, long gameTime, FormulaContext context) {
        if (data.tribulation().isEmpty() || data.paused()) return TickResult.idle();
        if (gameTime < data.phaseEndsAt()) return TickResult.running(data.phase());
        int next = data.phase() + 1;
        Identifier id = data.tribulation().orElseThrow();
        if (next >= definition.phases().size()) {
            int previous = data.phase();
            DomainBehaviorService.execute(MxtTypeRegistries.TRIBULATION_STAGE_BEHAVIOR, definition.phases().get(previous).endBehavior(), BehaviorContext.of(
                    Kind.TRIBULATION_PHASE_END, id, null, context, true));
            data.clear();
            DomainBehaviorService.execute(MxtTypeRegistries.TRIBULATION_STAGE_BEHAVIOR, definition.successBehavior(), BehaviorContext.of(
                    Kind.TRIBULATION_SUCCESS, id, null, context, true));
            NeoForge.EVENT_BUS.post(new Complete(data, id, definition, previous));
            return TickResult.completed();
        }
        long duration = duration(definition.phases().get(next), definition, context);
        if (duration < 0L) {
            data.setPaused(true);
            DomainBehaviorService.execute(MxtTypeRegistries.TRIBULATION_STAGE_BEHAVIOR, definition.failureBehavior(), BehaviorContext.of(
                    Kind.TRIBULATION_FAILURE, id, null, context, false));
            return TickResult.paused(Failure.INVALID_FORMULA);
        }
        if (NeoForge.EVENT_BUS.post(new PhasePre(data, id, definition, next)).isCanceled())
            return TickResult.running(data.phase());
        data.start(id, next, Math.addExact(gameTime, duration));
        DomainBehaviorService.execute(MxtTypeRegistries.TRIBULATION_STAGE_BEHAVIOR, definition.phases().get(next - 1).endBehavior(), BehaviorContext.of(
                Kind.TRIBULATION_PHASE_END, id, null, context, true));
        DomainBehaviorService.execute(MxtTypeRegistries.TRIBULATION_STAGE_BEHAVIOR, definition.phases().get(next).startBehavior(), BehaviorContext.of(
                Kind.TRIBULATION_PHASE_START, id, null, context, true));
        NeoForge.EVENT_BUS.post(new PhasePost(data, id, definition, next));
        return TickResult.advanced(next);
    }

    private static long duration(Phase phase, TribulationDefinition definition, FormulaContext context) {
        double scale = definition.difficultyScale().evaluate(context);
        double value = phase.duration().evaluate(context) * scale;
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
