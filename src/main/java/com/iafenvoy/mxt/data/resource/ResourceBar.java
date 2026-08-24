package com.iafenvoy.mxt.data.resource;

import com.iafenvoy.mxt.data.resourcebar.builtin.visibility.AlwaysVisibility;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderData;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarVisibility;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/**
 * A client-visible resource bar declaration. Renderer and visibility IDs select built-in typed implementations.
 */
public record ResourceBar(Context context, Anchor anchor, int order,
                          ResourceBarVisibility visibility, ResourceBarRenderData renderer, ValueDisplay valueDisplay,
                          boolean replaceDefault) {
    /**
     * Inline codec used by the owning {@link Resource} definition.
     */
    public static final Codec<ResourceBar> CODEC = RecordCodecBuilder.create(i -> i.group(
            Context.CODEC.optionalFieldOf("context", Context.SELF_HUD).forGetter(ResourceBar::context),
            Anchor.CODEC.fieldOf("anchor").forGetter(ResourceBar::anchor),
            Codec.INT.optionalFieldOf("order", 0).forGetter(ResourceBar::order),
            ResourceBarVisibility.CODEC.optionalFieldOf("visibility", AlwaysVisibility.INSTANCE).forGetter(ResourceBar::visibility),
            ResourceBarRenderData.CODEC.fieldOf("renderer").forGetter(ResourceBar::renderer),
            ValueDisplay.CODEC.optionalFieldOf("value_display", ValueDisplay.NONE).forGetter(ResourceBar::valueDisplay),
            Codec.BOOL.optionalFieldOf("replace_default", false).forGetter(ResourceBar::replaceDefault)
    ).apply(i, ResourceBar::new));

    public enum Context implements StringRepresentable {
        SELF_HUD, TARGET_OVERLAY, BOSS_OVERLAY;
        public static final Codec<Context> CODEC = StringRepresentable.fromValues(Context::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
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
