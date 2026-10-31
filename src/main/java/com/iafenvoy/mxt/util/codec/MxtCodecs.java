package com.iafenvoy.mxt.util.codec;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import net.minecraft.world.phys.Vec2;

import java.util.List;

/**
 * The codecs only this mod's definitions use. What is generic enough for the library lives in Saturn's
 * {@code MiscCodecs}; a field shape a content definition is written in stays here.
 */
public final class MxtCodecs {
    public static final Codec<Double> NON_NEGATIVE = Codec.doubleRange(0.0D, Double.MAX_VALUE);

    // Two element [x, z]: a secret realm border's center and a formation stroke's points are both written this way.
    public static final Codec<Vec2> HORIZONTAL_PAIR = Codec.DOUBLE.listOf().comapFlatMap(
            values -> values.size() == 2
                    ? DataResult.success(new Vec2(values.getFirst().floatValue(), values.get(1).floatValue()))
                    : DataResult.error(() -> "Expected [x, z] with exactly two numbers"),
            value -> List.of((double) value.x, (double) value.y));

    private MxtCodecs() {
    }
}
