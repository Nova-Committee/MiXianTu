package com.iafenvoy.mxt.data.rift;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

public record RiftTemplate(String key, RiftPlacement placement, boolean relativeToEntry, RiftShape shape,
                           RiftDestination destination, RiftAppearance appearance, long durationTicks,
                           boolean enabled) {
    public static final Codec<RiftTemplate> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.string(1, 64).fieldOf("key").forGetter(RiftTemplate::key),
            RiftPlacement.CODEC.fieldOf("placement").forGetter(RiftTemplate::placement),
            Codec.BOOL.optionalFieldOf("relative_to_entry", false).forGetter(RiftTemplate::relativeToEntry),
            RiftShape.CODEC.fieldOf("shape").forGetter(RiftTemplate::shape),
            RiftDestination.CODEC.fieldOf("destination").forGetter(RiftTemplate::destination),
            RiftAppearance.CODEC.optionalFieldOf("appearance", RiftAppearance.DEFAULT).forGetter(RiftTemplate::appearance),
            ExtraCodecs.NON_NEGATIVE_LONG.optionalFieldOf("duration_ticks", 0L).forGetter(RiftTemplate::durationTicks),
            Codec.BOOL.optionalFieldOf("enabled", true).forGetter(RiftTemplate::enabled)
    ).apply(i, RiftTemplate::new));
}
