package com.iafenvoy.mxt.data.resourcebar.builtin.renderdata;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderData;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Optional;

public record OriginsRenderData(Identifier texture, int barIndex, Optional<Integer> iconIndex, boolean inverted)
        implements ResourceBarRenderData {
    public static final Identifier DEFAULT_TEXTURE = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "textures/gui/resource_bar.png");
    public static final MapCodec<OriginsRenderData> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Identifier.CODEC.optionalFieldOf("sprite_location", DEFAULT_TEXTURE).forGetter(OriginsRenderData::texture),
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
