package com.iafenvoy.mxt.data.resourcebar.builtin.renderdata;

import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderData;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record SegmentedRenderData(int segments, int gap, int fullColor,
                                  int emptyColor) implements ResourceBarRenderData {
    public static final MapCodec<SegmentedRenderData> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1, 256).fieldOf("segments").forGetter(SegmentedRenderData::segments),
            Codec.intRange(0, 32).optionalFieldOf("gap", 1).forGetter(SegmentedRenderData::gap),
            MiscCodecs.COLOR_NO_ALPHA.optionalFieldOf("full_color", 0xFFFFFF).forGetter(SegmentedRenderData::fullColor),
            MiscCodecs.COLOR_NO_ALPHA.optionalFieldOf("empty_color", 0x555555).forGetter(SegmentedRenderData::emptyColor)
    ).apply(i, SegmentedRenderData::new));

    @Override
    public MapCodec<SegmentedRenderData> codec() {
        return CODEC;
    }

    @Override
    public int width() {
        return this.segments * 8 + (this.segments - 1) * this.gap;
    }

}
