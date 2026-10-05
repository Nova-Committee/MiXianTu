package com.iafenvoy.mxt.data.quality;

import com.mojang.datafixers.util.Either;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

/**
 * One row of the {@code mxt:default_quality} table: the tier an item starts at, and how strongly this pack claims
 * it. The bare tier id is what a pack almost always writes, so it stays the whole value; the object form with a
 * {@code priority} exists only for the case where two packs name the same item and the order must not decide.
 */
public record DefaultQuality(Holder<ItemQuality> quality, int priority) {
    private static final Codec<DefaultQuality> FIELDS = RecordCodecBuilder.create(i -> i.group(
            ItemQuality.CODEC.fieldOf("quality").forGetter(DefaultQuality::quality),
            Codec.INT.optionalFieldOf("priority", 0).forGetter(DefaultQuality::priority)
    ).apply(i, DefaultQuality::new));
    public static final Codec<DefaultQuality> CODEC = Codec.either(FIELDS, ItemQuality.CODEC)
            .xmap(either -> either.map(value -> value, quality -> new DefaultQuality(quality, 0)), Either::left);

    public static DefaultQuality of(Holder<ItemQuality> quality) {
        return new DefaultQuality(quality, 0);
    }
}
