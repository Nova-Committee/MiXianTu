package com.iafenvoy.mxt.data.resourcebar.builtin.renderdata;

import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderData;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record RadialRenderData(int radius, int thickness, double startAngle, double endAngle, int fillColor)
        implements ResourceBarRenderData {
    public static final MapCodec<RadialRenderData> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.intRange(1, 512).fieldOf("radius").forGetter(RadialRenderData::radius),
            Codec.intRange(1, 128).fieldOf("thickness").forGetter(RadialRenderData::thickness),
            Codec.DOUBLE.optionalFieldOf("start_angle", 0.0D).forGetter(RadialRenderData::startAngle),
            Codec.DOUBLE.optionalFieldOf("end_angle", 360.0D).forGetter(RadialRenderData::endAngle),
            MiscCodecs.COLOR_NO_ALPHA.optionalFieldOf("fill_color", 0xFFFFFF).forGetter(RadialRenderData::fillColor)
    ).apply(i, RadialRenderData::new));

    @Override
    public MapCodec<RadialRenderData> codec() {
        return CODEC;
    }

    @Override
    public int width() {
        return this.radius * 2 + this.thickness;
    }

    @Override
    public int height() {
        return this.radius * 2 + this.thickness;
    }

}
