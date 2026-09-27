package com.iafenvoy.mxt.screen.hud;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * What the layout file holds for one element: the window anchor it is bound to, how far its own anchor point sits
 * from that window point in pixels, and whether it is shown at all. Both distances are exact pixels, so a drag never
 * rounds a position away, and a missing file entry instead of a stored one means the element follows its defaults.
 */
public record HudPlacement(HudAnchor anchor, int offsetX, int offsetY, boolean visible) {
    public static final Codec<HudPlacement> CODEC = RecordCodecBuilder.create(i -> i.group(
            HudAnchor.CODEC.fieldOf("anchor").forGetter(HudPlacement::anchor),
            Codec.INT.optionalFieldOf("offset_x", 0).forGetter(HudPlacement::offsetX),
            Codec.INT.optionalFieldOf("offset_y", 0).forGetter(HudPlacement::offsetY),
            Codec.BOOL.optionalFieldOf("visible", true).forGetter(HudPlacement::visible)
    ).apply(i, HudPlacement::new));
}
