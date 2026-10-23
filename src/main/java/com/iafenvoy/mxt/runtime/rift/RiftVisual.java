package com.iafenvoy.mxt.runtime.rift;

import com.iafenvoy.mxt.data.rift.*;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

public record RiftVisual(RiftPlacement placement, RiftShape shape, RiftAppearance appearance, int color,
                         boolean enabled) {
    public static final Codec<RiftVisual> CODEC = RecordCodecBuilder.create(i -> i.group(
            RiftPlacement.CODEC.fieldOf("placement").forGetter(RiftVisual::placement),
            RiftShape.CODEC.fieldOf("shape").forGetter(RiftVisual::shape),
            RiftAppearance.CODEC.fieldOf("appearance").forGetter(RiftVisual::appearance),
            Codec.INT.fieldOf("color").forGetter(RiftVisual::color),
            Codec.BOOL.fieldOf("enabled").forGetter(RiftVisual::enabled)
    ).apply(i, RiftVisual::new));

    public static RiftVisual of(RiftRecord record) {
        RiftTemplate data = record.data();
        int color = data.appearance().color();
        if (color == -1) {
            String identity = switch (data.destination()) {
                case RiftDestination.Realm realm -> realm.realm().toString();
                case RiftDestination.Position position -> position.dimension().orElseThrow().toString();
                case RiftDestination.Return _ -> "mxt:return";
            };
            color = RiftColors.forDimension(Identifier.parse(identity));
        }
        return new RiftVisual(data.placement(), data.shape(), data.appearance(), color, data.enabled());
    }
}
