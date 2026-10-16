package com.iafenvoy.mxt.particle;

import com.iafenvoy.mxt.data.formation.FormationDrawIn;
import com.iafenvoy.mxt.data.formation.FormationShape;
import com.iafenvoy.mxt.registry.MxtParticleTypes;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import org.jspecify.annotations.NonNull;

/**
 * One order to draw a formation's figure, sent as a command rather than as a picture: the carrier lays no geometry
 * itself, and the drawing is this side's own until the orders stop. The figure travels as the id of its definition and
 * the client reads the strokes out of its own copy, so a dense figure costs no more traffic than a sparse one, and the
 * colour and the dust size of every point come from the stroke it belongs to.
 */
public record FormationParticleOptions(Holder<FormationShape> shape, double radius, FormationDrawIn drawIn)
        implements ParticleOptions {
    public static final MapCodec<FormationParticleOptions> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            FormationShape.CODEC.fieldOf("shape").forGetter(FormationParticleOptions::shape),
            Codec.doubleRange(0.0D, 4096.0D).fieldOf("radius").forGetter(FormationParticleOptions::radius),
            FormationDrawIn.CODEC.fieldOf("draw_in").forGetter(FormationParticleOptions::drawIn)
    ).apply(i, FormationParticleOptions::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, FormationParticleOptions> STREAM_CODEC = StreamCodec.composite(
            FormationShape.STREAM_CODEC, FormationParticleOptions::shape,
            ByteBufCodecs.DOUBLE, FormationParticleOptions::radius,
            FormationDrawIn.STREAM_CODEC, FormationParticleOptions::drawIn,
            FormationParticleOptions::new
    );

    @Override
    public @NonNull ParticleType<?> getType() {
        return MxtParticleTypes.FORMATION.get();
    }
}
