package com.iafenvoy.mxt.data.resourcebar.builtin.renderdata;

import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderData;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

public record TexturedRenderData(Identifier backgroundSprite, Identifier fillSprite, int width, int height,
                                 int fillColor,
                                 boolean showValue) implements ResourceBarRenderData {
    public static final MapCodec<TexturedRenderData> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.fieldOf("background_sprite").forGetter(TexturedRenderData::backgroundSprite),
            Identifier.CODEC.fieldOf("fill_sprite").forGetter(TexturedRenderData::fillSprite),
            Codec.intRange(1, 1024).fieldOf("width").forGetter(TexturedRenderData::width),
            Codec.intRange(1, 1024).fieldOf("height").forGetter(TexturedRenderData::height),
            MiscCodecs.COLOR_NO_ALPHA.optionalFieldOf("fill_color", 0xFFFFFF).forGetter(TexturedRenderData::fillColor),
            Codec.BOOL.optionalFieldOf("show_value", false).forGetter(TexturedRenderData::showValue)
    ).apply(i, TexturedRenderData::new));

    @Override
    public MapCodec<TexturedRenderData> codec() {
        return CODEC;
    }

}
