package com.iafenvoy.mxt.data.resourcebar.builtin.renderdata;

import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;

public record TextOnlyRenderData(String format, int color, boolean showMaximum) implements ResourceBarRenderData {
    public static final MapCodec<TextOnlyRenderData> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.STRING.optionalFieldOf("format", "%current%").forGetter(TextOnlyRenderData::format),
            ExtraCodecs.STRING_RGB_COLOR.optionalFieldOf("color", 0xFFFFFF).forGetter(TextOnlyRenderData::color),
            Codec.BOOL.optionalFieldOf("show_maximum", false).forGetter(TextOnlyRenderData::showMaximum)
    ).apply(i, TextOnlyRenderData::new));

    @Override
    public MapCodec<TextOnlyRenderData> codec() {
        return CODEC;
    }

}
