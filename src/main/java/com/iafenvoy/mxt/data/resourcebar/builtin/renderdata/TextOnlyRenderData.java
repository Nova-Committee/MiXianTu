package com.iafenvoy.mxt.data.resourcebar.builtin.renderdata;

import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderData;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record TextOnlyRenderData(String format, int color, boolean showMaximum) implements ResourceBarRenderData {
    public static final MapCodec<TextOnlyRenderData> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("format", "%current%").forGetter(TextOnlyRenderData::format),
            MiscCodecs.COLOR_NO_ALPHA.optionalFieldOf("color", 0xFFFFFF).forGetter(TextOnlyRenderData::color),
            Codec.BOOL.optionalFieldOf("show_maximum", false).forGetter(TextOnlyRenderData::showMaximum)
    ).apply(i, TextOnlyRenderData::new));

    @Override
    public MapCodec<TextOnlyRenderData> codec() {
        return CODEC;
    }

}
