package com.iafenvoy.mxt.data.artifact.ability;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.world.phys.Vec3;

import java.util.stream.Stream;

/**
 * How the mount's model is drawn, in the shape a vanilla model's {@code display} uses: {@code rotation} in degrees,
 * composed as {@code rotationXYZ} like the vanilla item transform, {@code scale} as a multiplier of the authored size,
 * and {@code translation} authored in sixteenths of a block. Only the translation is kept in blocks here, so the rest
 * of the mod never has to know about the sixteenth.
 */
public record FlightDisplay(Vec3 translation, Vec3 rotation, Vec3 scale) {
    // The pose the mount was drawn in before any of this was data: laid flat, its sprite's diagonal turned into the
    // direction of travel, and twice the authored size. The rotation is the equivalent of flattening about X by a
    // quarter turn and then turning the blade an eighth of a turn counter-clockwise.
    public static final FlightDisplay DEFAULT = new FlightDisplay(Vec3.ZERO, new Vec3(90.0D, 0.0D, -45.0D), new Vec3(2.0D, 2.0D, 2.0D));
    private static final MapCodec<FlightDisplay> RAW_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Vec3.CODEC.optionalFieldOf("translation", Vec3.ZERO).forGetter(FlightDisplay::translation),
            Vec3.CODEC.optionalFieldOf("rotation", DEFAULT.rotation()).forGetter(FlightDisplay::rotation),
            Vec3.CODEC.optionalFieldOf("scale", DEFAULT.scale()).forGetter(FlightDisplay::scale)
    ).apply(i, FlightDisplay::new));
    public static final MapCodec<FlightDisplay> MAP_CODEC = RAW_CODEC.validate(FlightDisplay::validate);
    // What a field of this shape asks for: an object under one key, not a type-dispatched entry.
    public static final Codec<FlightDisplay> CODEC = MAP_CODEC.codec();

    public FlightDisplay {
        translation = translation.scale(1.0D / 16.0D);
    }

    private static DataResult<FlightDisplay> validate(FlightDisplay display) {
        // Mirroring is legal (a negative scale is what a vanilla transform allows too), so only finiteness is checked.
        return Stream.of(display.translation(), display.rotation(), display.scale()).allMatch(FlightDisplay::isFinite)
                ? DataResult.success(display)
                : DataResult.error(() -> "Flight display vectors must be finite");
    }

    private static boolean isFinite(Vec3 vector) {
        return Double.isFinite(vector.x) && Double.isFinite(vector.y) && Double.isFinite(vector.z);
    }
}
