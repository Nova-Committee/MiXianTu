package com.iafenvoy.mxt.data.resource;

import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarVisibilities.Always;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderer;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarVisibility;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;

/**
 * A client-visible resource bar declaration. Renderer and visibility IDs select built-in typed implementations.
 */
public record ResourceBar(Holder<Resource> resource, Context context, Anchor anchor, int order,
                          ResourceBarVisibility visibility, ResourceBarRenderer renderer, ValueDisplay valueDisplay,
                          boolean replaceDefault) {
    public static final Codec<Holder<ResourceBar>> CODEC = RegistryFixedCodec.create(MxtRegistryKeys.RESOURCE_BAR);
    public static final Codec<ResourceBar> DIRECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Resource.CODEC.fieldOf("resource").forGetter(ResourceBar::resource),
            Context.CODEC.optionalFieldOf("context", Context.SELF_HUD).forGetter(ResourceBar::context),
            Anchor.CODEC.fieldOf("anchor").forGetter(ResourceBar::anchor),
            Codec.INT.optionalFieldOf("order", 0).forGetter(ResourceBar::order),
            ResourceBarVisibility.CODEC.optionalFieldOf("visibility", Always.INSTANCE).forGetter(ResourceBar::visibility),
            ResourceBarRenderer.CODEC.fieldOf("renderer").forGetter(ResourceBar::renderer),
            ValueDisplay.CODEC.optionalFieldOf("value_display", ValueDisplay.NONE).forGetter(ResourceBar::valueDisplay),
            Codec.BOOL.optionalFieldOf("replace_default", false).forGetter(ResourceBar::replaceDefault)
    ).apply(instance, ResourceBar::new));

    public enum Context implements StringRepresentable {
        SELF_HUD, TARGET_OVERLAY, BOSS_OVERLAY;
        public static final Codec<Context> CODEC = StringRepresentable.fromValues(Context::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    public enum Anchor implements StringRepresentable {
        BELOW_HEALTH, ABOVE_HOTBAR, TOP_LEFT_STACK, TOP_RIGHT_STACK;
        public static final Codec<Anchor> CODEC = StringRepresentable.fromValues(Anchor::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }

    /** Numeric label drawn over graphical renderers. Omit the field to render only the bar itself. */
    public enum ValueDisplay implements StringRepresentable {
        NONE, CURRENT, CURRENT_AND_MAXIMUM, PERCENTAGE;
        public static final Codec<ValueDisplay> CODEC = StringRepresentable.fromValues(ValueDisplay::values);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
