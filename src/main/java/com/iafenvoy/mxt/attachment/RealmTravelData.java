package com.iafenvoy.mxt.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Return location retained while a player is inside a temporary realm instance.
 */
public final class RealmTravelData {
    public static final MapCodec<RealmTravelData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("realm").forGetter(RealmTravelData::realm),
            Identifier.CODEC.optionalFieldOf("origin_dimension").forGetter(RealmTravelData::originDimension),
            Codec.DOUBLE.optionalFieldOf("origin_x", 0.0D).forGetter(RealmTravelData::originX),
            Codec.DOUBLE.optionalFieldOf("origin_y", 0.0D).forGetter(RealmTravelData::originY),
            Codec.DOUBLE.optionalFieldOf("origin_z", 0.0D).forGetter(RealmTravelData::originZ),
            Codec.FLOAT.optionalFieldOf("origin_yaw", 0.0F).forGetter(RealmTravelData::originYaw),
            Codec.FLOAT.optionalFieldOf("origin_pitch", 0.0F).forGetter(RealmTravelData::originPitch)
    ).apply(instance, RealmTravelData::decode));
    public static final Codec<RealmTravelData> CODEC = MAP_CODEC.codec();

    private Identifier realm;
    private Identifier originDimension;
    private double originX;
    private double originY;
    private double originZ;
    private float originYaw;
    private float originPitch;

    public RealmTravelData() {
    }

    private static RealmTravelData decode(Optional<Identifier> realm, Optional<Identifier> originDimension,
                                          double originX, double originY, double originZ, float originYaw, float originPitch) {
        RealmTravelData value = new RealmTravelData();
        value.realm = realm.orElse(null);
        value.originDimension = originDimension.orElse(null);
        value.originX = originX;
        value.originY = originY;
        value.originZ = originZ;
        value.originYaw = originYaw;
        value.originPitch = originPitch;
        return value;
    }

    public boolean active() {
        return this.realm != null && this.originDimension != null;
    }

    public Optional<Identifier> realm() {
        return Optional.ofNullable(this.realm);
    }

    public Optional<Identifier> originDimension() {
        return Optional.ofNullable(this.originDimension);
    }

    public double originX() {
        return this.originX;
    }

    public double originY() {
        return this.originY;
    }

    public double originZ() {
        return this.originZ;
    }

    public float originYaw() {
        return this.originYaw;
    }

    public float originPitch() {
        return this.originPitch;
    }

    public void begin(Identifier realm, Identifier originDimension, double x, double y, double z, float yaw, float pitch) {
        this.realm = realm;
        this.originDimension = originDimension;
        this.originX = x;
        this.originY = y;
        this.originZ = z;
        this.originYaw = yaw;
        this.originPitch = pitch;
    }

    public void clear() {
        this.realm = null;
        this.originDimension = null;
        this.originX = 0.0D;
        this.originY = 0.0D;
        this.originZ = 0.0D;
        this.originYaw = 0.0F;
        this.originPitch = 0.0F;
    }
}
