package com.iafenvoy.mxt.data.resource;

import com.iafenvoy.mxt.data.resourcebar.ResourceBarContext;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderData;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarVisibility;
import com.iafenvoy.mxt.data.resourcebar.builtin.context.SelfHudContext;
import com.iafenvoy.mxt.data.resourcebar.builtin.visibility.AlwaysVisibility;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;
import java.util.Optional;

/**
 * A client-visible resource bar declaration. Renderer and visibility IDs select built-in typed implementations.
 */
public record ResourceBar(ResourceBarContext context, Anchor anchor, int order,
                          ResourceBarVisibility visibility, ResourceBarRenderData renderer, ValueDisplay valueDisplay,
                          Optional<Double> maximum) {
    /**
     * Inline codec used by the owning {@link Resource} definition.
     */
    public static final Codec<ResourceBar> CODEC = RecordCodecBuilder.create(i -> i.group(
            ResourceBarContext.CODEC.optionalFieldOf("context", SelfHudContext.INSTANCE).forGetter(ResourceBar::context),
            Anchor.CODEC.fieldOf("anchor").forGetter(ResourceBar::anchor),
            Codec.INT.optionalFieldOf("order", 0).forGetter(ResourceBar::order),
            ResourceBarVisibility.CODEC.optionalFieldOf("visibility", AlwaysVisibility.INSTANCE).forGetter(ResourceBar::visibility),
            ResourceBarRenderData.CODEC.fieldOf("renderer").forGetter(ResourceBar::renderer),
            ValueDisplay.CODEC.optionalFieldOf("value_display", ValueDisplay.NONE).forGetter(ResourceBar::valueDisplay),
            Codec.DOUBLE.optionalFieldOf("maximum").forGetter(ResourceBar::maximum)
    ).apply(i, ResourceBar::new));

    public ResourceBar {
        maximum = maximum.filter(value -> Double.isFinite(value) && value > 0.0D);
    }

    public enum Anchor implements StringRepresentable {
        LEFT, RIGHT;
        public static final Codec<Anchor> CODEC = StringRepresentable.fromValues(Anchor::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Numeric label drawn over graphical renderers. Omit the field to render only the bar itself.
     */
    public enum ValueDisplay implements StringRepresentable {
        NONE, CURRENT, CURRENT_AND_MAXIMUM, PERCENTAGE;
        public static final Codec<ValueDisplay> CODEC = StringRepresentable.fromValues(ValueDisplay::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
