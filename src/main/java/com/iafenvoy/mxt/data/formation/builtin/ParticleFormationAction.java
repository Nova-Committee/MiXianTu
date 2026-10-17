package com.iafenvoy.mxt.data.formation.builtin;

import com.iafenvoy.mxt.data.formation.FormationActionType;
import com.iafenvoy.mxt.data.formation.FormationDrawIn;
import com.iafenvoy.mxt.data.formation.FormationIntervals;
import com.iafenvoy.mxt.data.formation.FormationShape;
import com.iafenvoy.mxt.particle.FormationParticleOptions;
import com.iafenvoy.mxt.runtime.formation.FormationActionContext;
import com.iafenvoy.mxt.runtime.formation.FormationShapeDisplay;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

/**
 * The display module: which figure the array draws; it never affects an entity. The figure is a
 * {@code mxt:formation_shape} entry rather than a built-in star, so a pack draws whatever it can say with circles and
 * polylines, and everything about a stroke — its colour, how big and how far apart its dusts sit, the window it is
 * drawn over — is written on the stroke itself. The figure grows out of the controller from the moment the array was
 * raised, and the growth is played out on the client, so a long enough {@code duration_ticks} writes the array onto the
 * ground smoothly rather than in one jump per beat. Its default interval is one beat rather than one upkeep period,
 * because a display is what the finer grid exists for.
 */
public record ParticleFormationAction(Holder<FormationShape> shape, int durationTicks, int intervalTicks)
        implements FormationActionType {
    public static final MapCodec<ParticleFormationAction> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            FormationShape.CODEC.fieldOf("shape").forGetter(ParticleFormationAction::shape),
            // Zero is the plain outline: a formation that never animates draws itself complete on its first beat.
            Codec.intRange(0, 24000).optionalFieldOf("duration_ticks", 0).forGetter(ParticleFormationAction::durationTicks),
            FormationIntervals.CODEC.optionalFieldOf("interval_ticks", FormationIntervals.TICK).forGetter(ParticleFormationAction::intervalTicks)
    ).apply(i, ParticleFormationAction::new));

    @Override
    public MapCodec<ParticleFormationAction> codec() {
        return CODEC;
    }

    // The array's own beat: one entity has no boundary to draw.
    @Override
    public void interval(FormationActionContext context) {
        if (context.perEntity()) return;
        FormationShapeDisplay.draw(context.level(), context.controller(), new FormationParticleOptions(this.shape,
                context.radius(), this.drawIn(context)));
    }

    // Timed from the moment the array was raised rather than from a counter, so the animation survives a reload and
    // every player standing there sees the same frame. An instance from before this was recorded reads as finished
    // instead of replaying: it has no start time to measure from.
    private FormationDrawIn drawIn(FormationActionContext context) {
        return FormationDrawIn.at(context.carrier().activatedAt(), context.level().getGameTime(),
                this.durationTicks, this.intervalTicks);
    }
}
