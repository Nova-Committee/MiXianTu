package com.iafenvoy.mxt.data.resource;

import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarVisibilities;
import com.iafenvoy.mxt.data.resourcebar.BuiltinResourceBarVisibilities.Always;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarRenderer;
import com.iafenvoy.mxt.data.resourcebar.ResourceBarVisibility;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.Locale;

/**
 * A client-visible resource bar declaration. Renderer and visibility IDs select built-in typed implementations.
 */
public record ResourceBarDefinition(Identifier resource, Context context,
                                    Anchor anchor,
                                    int order, ResourceBarVisibility visibility, ResourceBarRenderer renderer,
                                    boolean replaceDefault) {
    public static final Codec<ResourceBarDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.fieldOf("resource").forGetter(ResourceBarDefinition::resource),
            Context.CODEC.optionalFieldOf("context", Context.SELF_HUD).forGetter(ResourceBarDefinition::context),
            Anchor.CODEC.fieldOf("anchor").forGetter(ResourceBarDefinition::anchor),
            Codec.INT.optionalFieldOf("order", 0).forGetter(ResourceBarDefinition::order),
            ResourceBarVisibility.CODEC.optionalFieldOf("visibility", Always.INSTANCE).forGetter(ResourceBarDefinition::visibility),
            ResourceBarRenderer.CODEC.fieldOf("renderer").forGetter(ResourceBarDefinition::renderer),
            Codec.BOOL.optionalFieldOf("replace_default", false).forGetter(ResourceBarDefinition::replaceDefault)
    ).apply(instance, ResourceBarDefinition::new));

    public enum Context {
        SELF_HUD, TARGET_OVERLAY, BOSS_OVERLAY;
        public static final Codec<Context> CODEC = enumCodec(Context.class);
    }

    public enum Anchor {
        BELOW_HEALTH, ABOVE_HOTBAR, TOP_LEFT_STACK, TOP_RIGHT_STACK;
        public static final Codec<Anchor> CODEC = enumCodec(Anchor.class);
    }

    private static <E extends Enum<E>> Codec<E> enumCodec(Class<E> type) {
        return Codec.STRING.comapFlatMap(value -> {
            try {
                return DataResult.success(Enum.valueOf(type, value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                return DataResult.error(() -> "Unknown " + type.getSimpleName() + " " + value);
            }
        }, value -> value.name().toLowerCase(Locale.ROOT));
    }
}
