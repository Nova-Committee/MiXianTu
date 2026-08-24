package com.iafenvoy.mxt.data.resourcebar.builtin.visibility;

import com.iafenvoy.mxt.data.resourcebar.ResourceBarView;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarVisibility;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

public record RangeVisibility(double min, double max) implements ResourceBarVisibility {
    public static final MapCodec<RangeVisibility> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Codec.DOUBLE.fieldOf("min").forGetter(RangeVisibility::min),
            Codec.DOUBLE.fieldOf("max").forGetter(RangeVisibility::max)
    ).apply(i, RangeVisibility::new));

    public RangeVisibility {
        if (!Double.isFinite(min) || !Double.isFinite(max) || min > max)
            throw new IllegalArgumentException("Invalid resource range");
    }

    @Override
    public boolean visible(ResourceBarView view) {
        return view.current() >= this.min && view.current() <= this.max;
    }

    @Override
    public MapCodec<RangeVisibility> codec() {
        return CODEC;
    }
}
