package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.data.Sect;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.Optional;

/**
 * Per-chunk sect ownership. Permissions remain defined by the owner's datapack rank policy.
 */
public final class SectTerritoryAttachment {
    public static final MapCodec<SectTerritoryAttachment> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryFixedCodec.create(MxtResourceKeys.SECT).optionalFieldOf("owner").forGetter(SectTerritoryAttachment::owner)
    ).apply(i, SectTerritoryAttachment::new));
    private Holder<Sect> owner;

    public SectTerritoryAttachment() {
    }

    private SectTerritoryAttachment(Optional<Holder<Sect>> owner) {
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
