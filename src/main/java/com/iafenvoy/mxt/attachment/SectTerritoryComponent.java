package com.iafenvoy.mxt.attachment;

import com.iafenvoy.mxt.registry.MxtResourceKeys;

import com.iafenvoy.mxt.data.Sect;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

import java.util.Optional;

/**
 * Per-chunk sect ownership. Permissions remain defined by the owner's datapack rank policy.
 */
public final class SectTerritoryComponent {
    public static final MapCodec<SectTerritoryComponent> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            RegistryFixedCodec.create(MxtResourceKeys.SECT).optionalFieldOf("owner").forGetter(SectTerritoryComponent::owner)
    ).apply(i, SectTerritoryComponent::new));
    private Holder<Sect> owner;

    public SectTerritoryComponent() {
    }

    private SectTerritoryComponent(Optional<Holder<Sect>> owner) {
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
