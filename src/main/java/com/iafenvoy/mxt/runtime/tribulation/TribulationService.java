package com.iafenvoy.mxt.runtime.tribulation;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.attachment.TribulationAttachment;
import com.iafenvoy.mxt.data.Tribulation;
import com.iafenvoy.mxt.data.storage.runtime.EntryBegan;
import com.iafenvoy.mxt.data.timeline.TimelineContext;
import com.iafenvoy.mxt.data.timeline.TimelineEntry;
import com.iafenvoy.mxt.data.timeline.TimelineEntry.Outcome;
import com.iafenvoy.mxt.data.timeline.TimelineJump;
import com.iafenvoy.mxt.data.timeline.TimelineState;
import com.iafenvoy.mxt.event.TribulationEvent.*;
import com.iafenvoy.mxt.runtime.world.AuraService;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.neoforge.common.NeoForge;

import java.util.List;

/**
 * Consumer for a persisted tribulation timeline: a run is installed by copying the definition's timeline into the
 * attachment, its wind-up (when the definition has one) runs down first, and then the beats at the head of that
 * queue are consumed until it is empty or a beat fails.
 * <p>
 * Nothing here is remembered between ticks: the attachment owns the queue and the state of the head beat, which
 * the beat works on as a draft and commits once it has answered.
 */
public final class TribulationService {
    // How many beats one tick may consume. A branch that jumps backwards can describe a cycle, and without a
    // budget a mis-authored timeline would never give the server thread back; a real run waits somewhere long
    // before this, and a cycling one is reported and picked up again next tick.
    public static final int MAX_BEATS_PER_TICK = 1024;

    private TribulationService() {
    }

    // Adds the local aura influence, so every wait is scaled by the environment the run stands in.
    private static FormulaContext tribulationContext(LivingEntity entity, FormulaContext context) {
        return context.with("aura_tribulation_modifier",
                AuraService.getPositionAura(entity.level(), entity.blockPosition()).rules().tribulationModify());
    }

    public static StartResult start(LivingEntity entity, TribulationAttachment data, Holder<Tribulation> tribulation, long gameTime, FormulaContext context) {
        Tribulation definition = tribulation.value();
        if (data.tribulation().isPresent()) return StartResult.rejected(Failure.ALREADY_ACTIVE);
        List<TimelineEntry> timeline = definition.timeline();
        if (timeline.isEmpty()) return StartResult.rejected(Failure.EMPTY_TIMELINE);
        if (!definition.condition().test(entity, context)) return StartResult.rejected(Failure.CONDITIONS);
        // Every entry is asked whether it can run before the run begins: this is the only moment a content error
        // can be reported as a refusal instead of a failure halfway through a committed timeline.
        FormulaContext runContext = tribulationContext(entity, context);
        TimelineContext probe = new TimelineContext(entity, runContext, gameTime,
                definition.difficultyScale().evaluate(runContext), timeline.size(), new TimelineState(), new TimelineJump());
        for (TimelineEntry entry : timeline) {
            if (!entry.validate(probe)) return StartResult.rejected(Failure.INVALID_ENTRY);
        }
        if (NeoForge.EVENT_BUS.post(new StartPre(data, tribulation)).isCanceled())
            return StartResult.rejected(Failure.CANCELLED);
        // The wind-up is resolved here, through the same rule every wait uses, and stored with the run: the
        // countdown a player watches is the number of ticks that will really pass, and a restart resumes it.
        long windup = Math.max(0L, probe.ticks(definition.windup()));
        // Nothing is consumed here: the timeline is copied into the attachment, its wind-up runs down first, and
        // its first entry begins on the tick after that.
        data.start(tribulation, timeline, windup);
        NeoForge.EVENT_BUS.post(new StartPost(data, tribulation));
        return StartResult.accepted();
    }

    public static TickResult tick(LivingEntity entity, TribulationAttachment data, Holder<Tribulation> tribulation, long gameTime, FormulaContext context) {
        // A run with nothing left to consume cannot progress - a save whose timeline the codec reduced to nothing,
        // or a cursor that outlived its beats. Dropping it keeps a dead run from blocking the next breakthrough.
        if (data.peek() == null) {
            if (data.tribulation().isPresent()) data.clear();
            return TickResult.idle();
        }
        // The wind-up is the run counting itself in: the timeline has not started, so nothing is consumed, no
        // beat begins and no state is written while it lasts.
        if (data.windup() > 0L) {
            data.tickWindup();
            return TickResult.running();
        }
        Tribulation definition = tribulation.value();
        FormulaContext runContext = tribulationContext(entity, context);
        double scale = definition.difficultyScale().evaluate(runContext);
        // The head of the cursor is the beat being consumed. A beat that finishes moves on by one; a branch beat
        // leaves a target behind, which is the only thing that can send the run backwards.
        int consumed = 0;
        while (true) {
            TimelineEntry entry = data.peek();
            if (entry == null) {
                data.clear();
                definition.successAction().execute(entity, runContext);
                NeoForge.EVENT_BUS.post(new Complete(data, tribulation));
                return TickResult.completed();
            }
            if (++consumed > MAX_BEATS_PER_TICK) {
                MiXianTu.LOGGER.warn("Tribulation {} consumed {} beats in one tick without waiting: a branch is "
                        + "probably cycling", HolderHelper.id(tribulation), consumed - 1);
                return TickResult.running();
            }
            // The beat reads and writes a draft of the run's state and commits it once it has answered: a beat
            // that changes nothing leaves the stored value untouched, so nothing has to be saved or synced.
            TimelineState state = new TimelineState(data.state().orElse(null));
            TimelineJump jump = new TimelineJump();
            TimelineContext entryContext = new TimelineContext(entity, runContext, gameTime, scale, data.length(), state, jump);
            if (!state.isPresent()) {
                if (NeoForge.EVENT_BUS.post(new EntryPre(data, tribulation, data.consumed(), entry)).isCanceled()) {
                    data.advance(TimelineJump.NEXT);
                    continue;
                }
                // "This beat began" is stored before the beat itself runs, so a restart cannot consume the start
                // of the same beat twice, and a beat that keeps no numbers of its own is still begun.
                state.set(EntryBegan.INSTANCE);
                entry.begin(entryContext);
            }
            Outcome outcome = entry.consume(entryContext);
            data.setState(state.get());
            if (outcome == Outcome.RUNNING) return TickResult.running();
            if (outcome == Outcome.FAILED) {
                data.clear();
                definition.failAction().execute(entity, runContext);
                return TickResult.failed(Failure.INVALID_ENTRY);
            }
            NeoForge.EVENT_BUS.post(new EntryPost(data, tribulation, data.consumed(), entry));
            data.advance(jump.target());
        }
    }

    public enum Failure {ALREADY_ACTIVE, EMPTY_TIMELINE, CONDITIONS, INVALID_ENTRY, CANCELLED}

    public record StartResult(boolean started, Failure failure) {
        static StartResult accepted() {
            return new StartResult(true, null);
        }

        static StartResult rejected(Failure failure) {
            return new StartResult(false, failure);
        }
    }

    public record TickResult(State state, Failure failure) {
        static TickResult idle() {
            return new TickResult(State.IDLE, null);
        }

        static TickResult running() {
            return new TickResult(State.RUNNING, null);
        }

        static TickResult completed() {
            return new TickResult(State.COMPLETED, null);
        }

        static TickResult failed(Failure failure) {
            return new TickResult(State.FAILED, failure);
        }
    }

    public enum State {IDLE, RUNNING, COMPLETED, FAILED}
}
