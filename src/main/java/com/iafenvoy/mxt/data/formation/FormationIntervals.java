package com.iafenvoy.mxt.data.formation;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;

/**
 * The two clocks a formation runs on, and the throttle contract of its modules. The module beat is what an
 * {@code interval_ticks} is measured in and the finest one a module may ask for; the upkeep period is what a bill, a
 * {@code tick}-beat module and the enter/exit sweep are counted in. They are separate so that a display can refresh
 * faster than the array pays.
 */
public final class FormationIntervals {
    /**
     * The module beat, in ticks.
     */
    public static final int TICK = 10;
    /**
     * The upkeep period, in ticks. It has to be a whole number of beats or a bill would land between two of them.
     */
    public static final int PERIOD = 20;
    /**
     * The upper bound is a courtesy, not a rule: what a slow interval really costs is the module not running, and a
     * definition should be able to make that as sparse as it likes without becoming unreadable.
     */
    public static final int MAX_TICKS = 24000;
    public static final Codec<Integer> CODEC = Codec.intRange(TICK, MAX_TICKS).validate(FormationIntervals::onBeat);

    // Tested against the world clock rather than a per-module counter, so two instances of one definition agree on the
    // phase and a module is stateless: there is nothing to save, and a formation reloaded mid-cycle carries on where
    // the clock says it should.
    public static boolean due(int intervalTicks, long gameTime) {
        return gameTime % intervalTicks == 0L;
    }

    public static boolean beatDue(long gameTime) {
        return gameTime % TICK == 0L;
    }

    public static boolean periodDue(long gameTime) {
        return gameTime % PERIOD == 0L;
    }

    // A value the beat grid cannot honour is refused rather than silently rounded onto it: a module asking for 15
    // would otherwise be reached every 30 ticks, which is not what its author wrote.
    private static DataResult<Integer> onBeat(int ticks) {
        if (ticks % TICK != 0)
            return DataResult.error(() -> "A formation module interval must be a multiple of " + TICK + " ticks: " + ticks);
        return DataResult.success(ticks);
    }

    private FormationIntervals() {
    }
}
