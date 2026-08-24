package com.iafenvoy.mxt.data.resourcebar.builtin.visibility;

import com.iafenvoy.mxt.data.resourcebar.ResourceBarView;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarVisibility;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;

public record RecentlyChangedVisibility(long holdTicks) implements ResourceBarVisibility {
    public static final MapCodec<RecentlyChangedVisibility> CODEC = Codec.LONG.optionalFieldOf("hold_ticks", 60L)
            .xmap(RecentlyChangedVisibility::new, RecentlyChangedVisibility::holdTicks);

    public RecentlyChangedVisibility {
        if (holdTicks < 0L) throw new IllegalArgumentException("holdTicks must be non-negative");
    }

    @Override
    public boolean visible(ResourceBarView view) {
        return view.ticksSinceChanged() <= this.holdTicks;
    }

    @Override
    public MapCodec<RecentlyChangedVisibility> codec() {
        return CODEC;
    }
}
