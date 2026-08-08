package com.iafenvoy.mxt.data.resourcebar;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.List;

/**
 * Built-in pure resource-bar visibility predicates.
 */
public final class BuiltinResourceBarVisibilities {
    private BuiltinResourceBarVisibilities() {
    }

    public enum Always implements ResourceBarVisibility {
        INSTANCE;
        public static final MapCodec<Always> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public boolean visible(ResourceBarView view) {
            return true;
        }

        @Override
        public MapCodec<Always> codec() {
            return CODEC;
        }
    }

    public enum NonFull implements ResourceBarVisibility {
        INSTANCE;
        public static final MapCodec<NonFull> CODEC = MapCodec.unit(INSTANCE);

        @Override
        public boolean visible(ResourceBarView view) {
            return view.current() < view.maximum();
        }

        @Override
        public MapCodec<NonFull> codec() {
            return CODEC;
        }
    }

    public record RecentlyChanged(long holdTicks) implements ResourceBarVisibility {
        public static final MapCodec<RecentlyChanged> CODEC = Codec.LONG.optionalFieldOf("hold_ticks", 60L).xmap(RecentlyChanged::new, RecentlyChanged::holdTicks);

        public RecentlyChanged {
            if (holdTicks < 0L) throw new IllegalArgumentException("holdTicks must be non-negative");
        }

        @Override
        public boolean visible(ResourceBarView view) {
            return view.ticksSinceChanged() <= this.holdTicks;
        }

        @Override
        public MapCodec<RecentlyChanged> codec() {
            return CODEC;
        }
    }

    public record Range(double min, double max) implements ResourceBarVisibility {
        public static final MapCodec<Range> CODEC = RecordCodecBuilder.mapCodec(i -> i.group(Codec.DOUBLE.fieldOf("min").forGetter(Range::min), Codec.DOUBLE.fieldOf("max").forGetter(Range::max)).apply(i, Range::new));

        public Range {
            if (!Double.isFinite(min) || !Double.isFinite(max) || min > max)
                throw new IllegalArgumentException("Invalid resource range");
        }

        @Override
        public boolean visible(ResourceBarView view) {
            return view.current() >= this.min && view.current() <= this.max;
        }

        @Override
        public MapCodec<Range> codec() {
            return CODEC;
        }
    }

    public record And(List<ResourceBarVisibility> values) implements ResourceBarVisibility {
        public static final MapCodec<And> CODEC = ResourceBarVisibility.CODEC.listOf().fieldOf("values").xmap(And::new, And::values);

        @Override
        public boolean visible(ResourceBarView view) {
            return this.values.stream().allMatch(value -> value.visible(view));
        }

        @Override
        public MapCodec<And> codec() {
            return CODEC;
        }
    }

    public record Or(List<ResourceBarVisibility> values) implements ResourceBarVisibility {
        public static final MapCodec<Or> CODEC = ResourceBarVisibility.CODEC.listOf().fieldOf("values").xmap(Or::new, Or::values);

        @Override
        public boolean visible(ResourceBarView view) {
            return this.values.stream().anyMatch(value -> value.visible(view));
        }

        @Override
        public MapCodec<Or> codec() {
            return CODEC;
        }
    }

    public record Not(ResourceBarVisibility value) implements ResourceBarVisibility {
        public static final MapCodec<Not> CODEC = ResourceBarVisibility.CODEC.fieldOf("value").xmap(Not::new, Not::value);

        @Override
        public boolean visible(ResourceBarView view) {
            return !this.value.visible(view);
        }

        @Override
        public MapCodec<Not> codec() {
            return CODEC;
        }
    }
}
