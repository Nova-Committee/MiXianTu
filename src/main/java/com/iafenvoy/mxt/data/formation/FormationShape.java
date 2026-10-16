package com.iafenvoy.mxt.data.formation;

import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.List;

/**
 * What an array draws, as a figure rather than as one of three built-in stars: a ring and a star are strokes of this
 * list, and so is anything else that can be said with circles and polylines. The module points at one of these, and the
 * client reads the strokes straight out of the definition, so a figure costs one registry id on the wire however dense
 * it is.
 *
 * <p>The built-in {@code five}, {@code six} and {@code eight} are shipped as entries of this registry by the mod's own
 * pack, so nothing about them is special in code.
 */
public record FormationShape(List<FormationStroke> strokes) {
    public static final Codec<Holder<FormationShape>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.FORMATION_SHAPE);
    public static final StreamCodec<RegistryFriendlyByteBuf, Holder<FormationShape>> STREAM_CODEC = ByteBufCodecs.holderRegistry(MxtResourceKeys.FORMATION_SHAPE);
    /**
     * The strokes one figure may hold. A figure is a drawing, and a drawing with hundreds of strokes is one nobody can
     * read; the cap is a courtesy, not a rule the renderer depends on.
     */
    public static final int MAX_STROKES = 256;
    public static final Codec<FormationShape> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            FormationStroke.CODEC.listOf(1, MAX_STROKES).fieldOf("strokes").forGetter(FormationShape::strokes)
    ).apply(i, FormationShape::new));

    public FormationShape {
        strokes = List.copyOf(strokes);
    }
}
