package com.iafenvoy.mxt.data.resourcebar;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/**
 * Built-in client renderer schemas. No renderer performs drawing from the common/server code.
 */
public final class BuiltinResourceBarRenderers {
    private BuiltinResourceBarRenderers() {
    }

    public record Textured(Identifier backgroundSprite, Identifier fillSprite, int width, int height, int fillColor,
                           boolean showValue) implements ResourceBarRenderer {
        public static final MapCodec<Textured> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(Identifier.CODEC.fieldOf("background_sprite").forGetter(Textured::backgroundSprite), Identifier.CODEC.fieldOf("fill_sprite").forGetter(Textured::fillSprite), Codec.intRange(1, 1024).fieldOf("width").forGetter(Textured::width), Codec.intRange(1, 1024).fieldOf("height").forGetter(Textured::height), MiscCodecs.COLOR_NO_ALPHA.optionalFieldOf("fill_color", 0xFFFFFF).forGetter(Textured::fillColor), Codec.BOOL.optionalFieldOf("show_value", false).forGetter(Textured::showValue)).apply(i, Textured::new));

        @Override
        public MapCodec<Textured> codec() {
            return CODEC;
        }
    }

    /**
     * Origins-compatible texture sheet: indexed fill row plus indexed 8x8 icon.
     */
    public record Origins(Identifier texture, int barIndex, int iconIndex,
                          boolean inverted) implements ResourceBarRenderer {
        public static final Identifier DEFAULT_TEXTURE = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/resource_bar.png");
        public static final MapCodec<Origins> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
                Identifier.CODEC.optionalFieldOf("sprite_location", DEFAULT_TEXTURE).forGetter(Origins::texture),
                Codec.intRange(0, 24).optionalFieldOf("bar_index", 0).forGetter(Origins::barIndex),
                Codec.intRange(0, 24).optionalFieldOf("icon_index", 0).forGetter(Origins::iconIndex),
                Codec.BOOL.optionalFieldOf("inverted", false).forGetter(Origins::inverted)
        ).apply(i, Origins::new));

        @Override
        public MapCodec<Origins> codec() {
            return CODEC;
        }
    }

    public record Segmented(int segments, int gap, int fullColor, int emptyColor) implements ResourceBarRenderer {
        public static final MapCodec<Segmented> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(Codec.intRange(1, 256).fieldOf("segments").forGetter(Segmented::segments), Codec.intRange(0, 32).optionalFieldOf("gap", 1).forGetter(Segmented::gap), MiscCodecs.COLOR_NO_ALPHA.optionalFieldOf("full_color", 0xFFFFFF).forGetter(Segmented::fullColor), MiscCodecs.COLOR_NO_ALPHA.optionalFieldOf("empty_color", 0x555555).forGetter(Segmented::emptyColor)).apply(i, Segmented::new));

        @Override
        public MapCodec<Segmented> codec() {
            return CODEC;
        }
    }

    public record Radial(int radius, int thickness, double startAngle, double endAngle,
                         int fillColor) implements ResourceBarRenderer {
        public static final MapCodec<Radial> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(Codec.intRange(1, 512).fieldOf("radius").forGetter(Radial::radius), Codec.intRange(1, 128).fieldOf("thickness").forGetter(Radial::thickness), Codec.DOUBLE.optionalFieldOf("start_angle", 0.0D).forGetter(Radial::startAngle), Codec.DOUBLE.optionalFieldOf("end_angle", 360.0D).forGetter(Radial::endAngle), MiscCodecs.COLOR_NO_ALPHA.optionalFieldOf("fill_color", 0xFFFFFF).forGetter(Radial::fillColor)).apply(i, Radial::new));

        @Override
        public MapCodec<Radial> codec() {
            return CODEC;
        }
    }

    public record TextOnly(String format, int color, boolean showMaximum) implements ResourceBarRenderer {
        public static final MapCodec<TextOnly> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(Codec.STRING.optionalFieldOf("format", "%current%").forGetter(TextOnly::format), MiscCodecs.COLOR_NO_ALPHA.optionalFieldOf("color", 0xFFFFFF).forGetter(TextOnly::color), Codec.BOOL.optionalFieldOf("show_maximum", false).forGetter(TextOnly::showMaximum)).apply(i, TextOnly::new));

        @Override
        public MapCodec<TextOnly> codec() {
            return CODEC;
        }
    }

    public enum Missing implements ResourceBarRenderer {
        INSTANCE;
        public static final MapCodec<Missing> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public MapCodec<Missing> codec() {
            return CODEC;
        }
    }
}
