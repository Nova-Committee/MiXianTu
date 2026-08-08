package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.Sect;
import com.iafenvoy.mxt.registry.MxtDatapackRegistries;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.Optional;

/**
 * Per-chunk sect ownership. Permissions remain defined by the owner's datapack rank policy.
 */
public final class SectTerritoryData {
    public static final MapCodec<SectTerritoryData> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            RegistryFixedCodec.create(MxtDatapackRegistries.SECT).optionalFieldOf("owner").forGetter(SectTerritoryData::owner)
    ).apply(instance, SectTerritoryData::new));
    private Holder<Sect> owner;

    public SectTerritoryData() {
    }

    private SectTerritoryData(Optional<Holder<Sect>> owner) {
        this.owner = owner.orElse(null);
    }

    public Optional<Holder<Sect>> owner() {
        return Optional.ofNullable(this.owner);
    }

    public boolean claimed() {
        return this.owner != null;
    }

    public void claim(Holder<Sect> sect) {
        this.owner = sect;
    }

    public void clear() {
        this.owner = null;
    }
}
