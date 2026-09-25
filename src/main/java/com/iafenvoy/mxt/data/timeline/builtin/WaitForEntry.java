package com.iafenvoy.mxt.data.timeline.builtin;

import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.storage.runtime.WaitCountdown;
import com.iafenvoy.mxt.data.timeline.TimelineContext;
import com.iafenvoy.mxt.data.timeline.TimelineEntry;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;
import java.util.Optional;

/**
 * Waits until a condition holds, re-read every tick, or until the declared timeout runs out. A condition that
 * never holds parks the run on this entry for good, which is the point: the timeline waits for the world instead
 * of guessing a duration. A timeout is for the other case - a deadline a run must not outlast - and says whether
 * running out finishes the beat or fails the run.
 */
public record WaitForEntry(EntityCondition condition, Optional<NumberProvider> timeout,
                           TimeoutOutcome onTimeout) implements TimelineEntry {
    public static final MapCodec<WaitForEntry> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            EntityCondition.CODEC.fieldOf("condition").forGetter(WaitForEntry::condition),
            NumberProvider.CODEC.optionalFieldOf("timeout").forGetter(WaitForEntry::timeout),
            TimeoutOutcome.CODEC.optionalFieldOf("on_timeout", TimeoutOutcome.FAIL).forGetter(WaitForEntry::onTimeout)
    ).apply(i, WaitForEntry::new));

    @Override
    public MapCodec<WaitForEntry> codec() {
        return CODEC;
    }

    @Override
    public void begin(TimelineContext context) {
        // Not scaled by the difficulty: how long a player is given to meet a condition is a deadline, not a phase
        // whose length the tribulation's own difficulty is allowed to stretch.
        this.timeout.ifPresent(provider -> context.state().set(new WaitCountdown(context.unscaledTicks(provider))));
    }

    @Override
    public Outcome consume(TimelineContext context) {
        if (this.condition.test(context.entity(), context.formula())) return Outcome.FINISHED;
        if (this.timeout.isEmpty()) return Outcome.RUNNING;
        WaitCountdown countdown = context.state().get(WaitCountdown.class).orElse(null);
        // A timeout that could not be resolved when the beat began is the failure validate() cannot report on its
        // own: it answers with a non-positive length for a beat that only fails on a later tick.
        if (countdown == null || countdown.remaining() <= 0L) return Outcome.FAILED;
        if (countdown.remaining() == 1L) return this.onTimeout.outcome();
        context.state().set(new WaitCountdown(countdown.remaining() - 1L));
        return Outcome.RUNNING;
    }

    @Override
    public boolean validate(TimelineContext context) {
        return this.timeout.isEmpty() || context.unscaledTicks(this.timeout.get()) >= 0L;
    }

    /**
     * What running out of time means: {@code fail} aborts the run and runs its fail_action, {@code finish} moves
     * on to the next beat as if the condition had held.
     */
    public enum TimeoutOutcome implements StringRepresentable {
        FAIL,
        FINISH;

        public static final Codec<TimeoutOutcome> CODEC = StringRepresentable.fromEnum(TimeoutOutcome::values);

        public Outcome outcome() {
            return this == FINISH ? Outcome.FINISHED : Outcome.FAILED;
        }

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
