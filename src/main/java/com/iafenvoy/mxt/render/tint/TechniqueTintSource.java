package com.iafenvoy.mxt.render.tint;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.registry.MxtDataComponents;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import net.minecraft.client.color.item.ItemTintSource;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterColorHandlersEvent;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

import java.util.Locale;

/**
 * Paints one texture layer of the built-in technique book with the colour of the technique the stack resolves. The layer
 * is named in the tint entry because a tint source is never told which index of the model's {@code tints} it is.
 */
@EventBusSubscriber(Dist.CLIENT)
public record TechniqueTintSource(Layer layer) implements ItemTintSource {
    // The type id items/technique_book.json names. This is a tint source type, not a datapack registry.
    public static final Identifier ID = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "technique");
    public static final MapCodec<TechniqueTintSource> MAP_CODEC = Layer.CODEC.optionalFieldOf("layer", Layer.PRIMARY).xmap(TechniqueTintSource::new, TechniqueTintSource::layer);

    @SubscribeEvent
    public static void register(RegisterColorHandlersEvent.ItemTintSources event) {
        event.register(ID, MAP_CODEC);
    }

    @Override
    public int calculate(@NonNull ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity owner) {
        // A book whose technique is gone keeps the colours its texture was authored with, as an unresolved pill does.
        Holder<Technique> holder = stack.get(MxtDataComponents.TECHNIQUE);
        return ARGB.opaque(this.layer.color(holder == null || !holder.isBound() ? null : holder.value()));
    }

    @Override
    public @NonNull MapCodec<? extends ItemTintSource> type() {
        return MAP_CODEC;
    }

    // Which of the two colours a book carries; the tint index on the model is what pairs them with the textures.
    public enum Layer implements StringRepresentable {
        PRIMARY, SECONDARY;
        public static final Codec<Layer> CODEC = StringRepresentable.fromEnum(Layer::values);

        private int color(@Nullable Technique technique) {
            if (this == PRIMARY)
                return technique == null ? Technique.DEFAULT_PRIMARY_COLOR : technique.primaryColor();
            return technique == null ? Technique.DEFAULT_SECONDARY_COLOR : technique.secondaryColor();
        }

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
