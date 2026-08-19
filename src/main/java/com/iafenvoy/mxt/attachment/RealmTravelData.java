package com.iafenvoy.mxt.attachment;
import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.data.RealmInstance;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.Optional;

/**
 * Return location retained while a player is inside a temporary realm instance.
 */
public final class RealmTravelData {
    public static final MapCodec<RealmTravelData> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryFixedCodec.create(MxtResourceKeys.REALM_INSTANCE).optionalFieldOf("realm").forGetter(RealmTravelData::realm),
            Identifier.CODEC.optionalFieldOf("origin_dimension").forGetter(RealmTravelData::originDimension),
            Codec.DOUBLE.optionalFieldOf("origin_x", 0.0D).forGetter(RealmTravelData::originX),
            Codec.DOUBLE.optionalFieldOf("origin_y", 0.0D).forGetter(RealmTravelData::originY),
            Codec.DOUBLE.optionalFieldOf("origin_z", 0.0D).forGetter(RealmTravelData::originZ),
            Codec.FLOAT.optionalFieldOf("origin_yaw", 0.0F).forGetter(RealmTravelData::originYaw),
            Codec.FLOAT.optionalFieldOf("origin_pitch", 0.0F).forGetter(RealmTravelData::originPitch)
    ).apply(i, RealmTravelData::new));
    private Optional<Holder<RealmInstance>> realm;
    private Optional<Identifier> originDimension;
    private double originX;
    private double originY;
    private double originZ;
    private float originYaw;
    private float originPitch;

    public RealmTravelData() {
    }

    public RealmTravelData(Optional<Holder<RealmInstance>> realm, Optional<Identifier> originDimension, double originX, double originY, double originZ, float originYaw, float originPitch) {
        this.realm = realm;
        this.originDimension = originDimension;
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
        this.originYaw = originYaw;
        this.originPitch = originPitch;
    }

    public boolean active() {
        return this.realm.isPresent() && this.originDimension.isPresent();
    }

    public Optional<Holder<RealmInstance>> realm() {
        return this.realm;
    }

    public Optional<Identifier> originDimension() {
        return this.originDimension;
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

    public void begin(Holder<RealmInstance> realm, Identifier originDimension, double x, double y, double z, float yaw, float pitch) {
        this.realm = Optional.ofNullable(realm);
        this.originDimension = Optional.ofNullable(originDimension);
        this.originX = x;
        this.originY = y;
        this.originZ = z;
        this.originYaw = yaw;
        this.originPitch = pitch;
    }

    public void clear() {
        this.realm = Optional.empty();
        this.originDimension = Optional.empty();
        this.originX = 0.0D;
        this.originY = 0.0D;
        this.originZ = 0.0D;
        this.originYaw = 0.0F;
        this.originPitch = 0.0F;
    }
}
