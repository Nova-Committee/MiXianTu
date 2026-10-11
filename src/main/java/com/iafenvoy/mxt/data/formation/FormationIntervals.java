package com.iafenvoy.mxt.data.formation;

import com.mojang.serialization.Codec;

/**
 * How often a formation module runs, counted in dispatch periods rather than ticks: the ticker only reaches a
 * module on a period boundary, so a tick count would silently be rounded to that grid. One shared codec keeps the
 * bound and the default identical across every module that throttles.
 */
public final class FormationIntervals {
    /**
     * The upper bound is a courtesy, not a rule: what a slow interval really costs is the module not running, and a
     * definition should be able to make that as sparse as it likes without becoming unreadable.
     */
    public static final Codec<Integer> CODEC = Codec.intRange(1, 1200);

    // Tested against the level clock rather than a per-module counter, so two instances of one definition agree on
    // the phase and a module is stateless: there is nothing to save, and a formation reloaded mid-cycle carries on
    // where the clock says it should.
    public static boolean due(int intervalPeriods, long gameTime, long period) {
        return Math.floorDiv(gameTime, period) % intervalPeriods == 0L;
    }

    private FormationIntervals() {
    }
}
