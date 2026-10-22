package com.iafenvoy.mxt.data.rift;

import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

public record RiftPreset(RiftShape shape, RiftAppearance appearance) {
    public static final Codec<Holder<RiftPreset>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.RIFT);
    public static final Codec<RiftPreset> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            RiftShape.CODEC.fieldOf("shape").forGetter(RiftPreset::shape),
            RiftAppearance.CODEC.optionalFieldOf("appearance", RiftAppearance.DEFAULT).forGetter(RiftPreset::appearance)
    ).apply(i, RiftPreset::new));
}
