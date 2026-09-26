package com.iafenvoy.mxt.data.resourcebar.builtin.renderdata;

import com.iafenvoy.mxt.data.SpriteIcon;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderData;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * A background and a fill pair drawn into the bar's own box. Each is a GUI atlas sprite, a texture path, or a
 * declared region of a texture, so a bar can reuse an atlas entry instead of shipping a whole texture file.
 *
 * <p>Only the background's own size is drawn: the fill is cut to the width the bar's progress works out, so a
 * {@code width} or {@code height} written on it changes nothing.
 */
public record TexturedRenderData(SpriteIcon backgroundSprite, SpriteIcon fillSprite, int width, int height,
                                 int fillColor,
                                 boolean showValue) implements ResourceBarRenderData {
    public static final MapCodec<TexturedRenderData> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SpriteIcon.SPRITE_CODEC.fieldOf("background_sprite").forGetter(TexturedRenderData::backgroundSprite),
            SpriteIcon.SPRITE_CODEC.fieldOf("fill_sprite").forGetter(TexturedRenderData::fillSprite),
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
