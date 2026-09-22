package com.iafenvoy.mxt.data.timeline;

import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * One evaluation of a timeline entry: the entity the run belongs to, the formula context of the current tick, the
 * tick itself, the definition's difficulty scale, and the slot the current entry keeps its own numbers in. Every
 * wait resolves its length through {@link #ticks(NumberProvider)}, so one rule scales all of them and the result
 * is settled once, when the entry begins, rather than re-read every tick.
 */
public record TimelineContext(@NotNull LivingEntity entity, @NotNull FormulaContext formula, long gameTime,
                              double scale, @NotNull TimelineState state) {
    // -1 when the provider cannot produce a usable duration.
    public long ticks(NumberProvider duration) {
        double value = duration.evaluate(this.formula) * this.scale
                * Math.max(0.0D, 1.0D + this.formula.value("aura_tribulation_modifier"));
        if (!Double.isFinite(value) || value <= 0.0D || value > Long.MAX_VALUE) return -1L;
        return Math.max(1L, Math.round(value));
    }
}
