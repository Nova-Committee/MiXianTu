package com.iafenvoy.mxt.data.resourcebar.builtin.renderdata;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.SpriteIcon;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

/**
 * The Origins-style layout: one sheet holding a background row, one fill row per bar index and a column of 8x8
 * icons. The icon names the sheet and where its usable area starts, which is what a packed atlas needs; the cell
 * arithmetic itself belongs to the renderer that knows this layout.
 */
public record OriginsRenderData(SpriteIcon sheet, int barIndex, Optional<Integer> iconIndex, boolean inverted)
        implements ResourceBarRenderData {
    public static final Identifier DEFAULT_TEXTURE = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/resource_bar.png");
    public static final MapCodec<OriginsRenderData> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            SpriteIcon.TEXTURE_CODEC.optionalFieldOf("sprite_location", SpriteIcon.of(DEFAULT_TEXTURE, false))
                    .forGetter(OriginsRenderData::sheet),
            Codec.intRange(0, 24).optionalFieldOf("bar_index", 0).forGetter(OriginsRenderData::barIndex),
            Codec.intRange(0, 24).optionalFieldOf("icon_index").forGetter(OriginsRenderData::iconIndex),
            Codec.BOOL.optionalFieldOf("inverted", false).forGetter(OriginsRenderData::inverted)
    ).apply(i, OriginsRenderData::new));

    @Override
    public MapCodec<OriginsRenderData> codec() {
        return CODEC;
    }

    public int resolvedIconIndex() {
        return this.iconIndex.orElse(this.barIndex);
    }

}
