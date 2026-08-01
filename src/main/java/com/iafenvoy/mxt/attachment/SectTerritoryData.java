package com.iafenvoy.mxt.attachment;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * Per-chunk sect ownership. Permissions remain defined by the owner's datapack rank policy.
 */
public final class SectTerritoryData {
    public static final MapCodec<SectTerritoryData> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.optionalFieldOf("owner").forGetter(SectTerritoryData::owner)
    ).apply(instance, SectTerritoryData::decode));
    public static final Codec<SectTerritoryData> CODEC = MAP_CODEC.codec();

    private Identifier owner;

    public SectTerritoryData() {
    }

    private SectTerritoryData(Optional<Identifier> owner) {
        this.owner = owner.orElse(null);
    }

    private static SectTerritoryData decode(Optional<Identifier> owner) {
        return new SectTerritoryData(owner);
    }

    public Optional<Identifier> owner() {
        return Optional.ofNullable(this.owner);
    }

    public boolean claimed() {
        return this.owner != null;
    }

    public void claim(Identifier sect) {
        this.owner = sect;
    }

    public void clear() {
        this.owner = null;
    }
}
