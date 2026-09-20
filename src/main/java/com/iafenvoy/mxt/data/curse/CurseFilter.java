package com.iafenvoy.mxt.data.curse;

import com.iafenvoy.mxt.attachment.CurseHolderAttachment.State;
import com.iafenvoy.mxt.registry.MxtAttachments;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.HolderHelper;
import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberRange;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;

import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;

/**
 * What a curse query asks of one held instance: which definition, which tags, how many stacks, how long is left.
 * <p>
 * Every filter that is present must hold for the <em>same</em> instance, and the tags are all required, so asking
 * for two tags means "a curse that carries both". A query that names no filter at all only asks whether the
 * entity holds any curse. Combine several queries with {@code mxt:or} when any-of is what is meant.
 */
public record CurseFilter(Optional<Holder<Curse>> curse, List<TagKey<Curse>> tags,
                          Optional<NumberRange> stacks, Optional<NumberRange> remainingTicks) {
    public static final MapCodec<CurseFilter> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Curse.CODEC.optionalFieldOf("curse").forGetter(CurseFilter::curse),
            TagKey.hashedCodec(MxtResourceKeys.CURSE).listOf().optionalFieldOf("tags", List.of()).forGetter(CurseFilter::tags),
            NumberRange.CODEC.optionalFieldOf("stacks").forGetter(CurseFilter::stacks),
            NumberRange.CODEC.optionalFieldOf("remaining_ticks").forGetter(CurseFilter::remainingTicks)
    ).apply(i, CurseFilter::new));
    public static final Codec<CurseFilter> CODEC = MAP_CODEC.codec();

    public boolean test(Entity entity, FormulaContext context) {
        long gameTime = entity.level().getGameTime();
        for (Entry<Holder<Curse>, State> entry : entity.getData(MxtAttachments.CURSE_HOLDER).instances().entrySet()) {
            if (this.matches(entry.getKey(), entry.getValue(), gameTime, context)) return true;
        }
        return false;
    }

    /**
     * Whether one held instance satisfies the whole query. Every filter that is present has to hold for the same
     * instance, which is what makes {@link #test} an any-instance check rather than a per-filter one.
     */
    private boolean matches(Holder<Curse> held, State state, long gameTime, FormulaContext context) {
        if (this.curse.isPresent() && !HolderHelper.id(held).equals(HolderHelper.id(this.curse.get()))) return false;
        for (TagKey<Curse> tag : this.tags) if (!held.is(tag)) return false;
        if (this.stacks.isPresent() && !this.stacks.get().test(state.stacks(), context)) return false;
        if (this.remainingTicks.isPresent()) {
            // A permanent instance never runs out: it answers a lower bound but never an upper one.
            double remaining = state.expiresAt() < 0L ? Double.POSITIVE_INFINITY : state.expiresAt() - gameTime;
            return this.remainingTicks.get().test(remaining, context);
        }
        return true;
    }
}
