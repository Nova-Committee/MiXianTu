package com.iafenvoy.mxt.runtime.wheel;

import com.iafenvoy.mxt.runtime.ability.AbilitySources;
import com.iafenvoy.mxt.util.codec.MiscStreamCodecs;
import com.mojang.serialization.Codec;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.NonNull;

import java.util.List;
import java.util.Locale;

/**
 * Where one wheel's contents come from, and therefore which wheel it is: the first page is the player's own twelve
 * saved slots, every page after it is derived from what the player carries right now.
 *
 * <p>A page is identified by this enum rather than a number, and every request carries it, so the server can re-read
 * the page the request names. Order is the order the switch keys walk, and index 0 is the page the wheel opens on.
 */
public enum WheelSource implements StringRepresentable {
    CONFIGURED,
    MAIN_HAND,
    OFF_HAND,
    CURIOS;

    public static final Codec<WheelSource> CODEC = StringRepresentable.fromEnum(WheelSource::values);
    public static final StreamCodec<ByteBuf, WheelSource> STREAM_CODEC = MiscStreamCodecs.enumCodec(WheelSource.class);
    // Every page, in the order the switch keys walk them and the order the page numbers count.
    public static final List<WheelSource> PAGES = List.of(values());

    @Override
    public @NonNull String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    // Whether this page is the player's saved layout rather than a reading of what they carry.
    public boolean configured() {
        return this == CONFIGURED;
    }

    // Wrapping at both ends, so the switch keys never dead-end.
    public WheelSource step(int delta) {
        return PAGES.get(Math.floorMod(this.ordinal() + delta, PAGES.size()));
    }

    // 1-based, which is what the player sees and never the index.
    public int page() {
        return this.ordinal() + 1;
    }

    // Empty for the configured page. Naming the equipment rather than the entries is what makes a page follow the
    // item: the grants were recorded under the same ids when the item was equipped.
    public List<Identifier> grantSources(LivingEntity entity) {
        return switch (this) {
            case CONFIGURED -> List.of();
            case MAIN_HAND -> List.of(AbilitySources.equipment(EquipmentSlot.MAINHAND, entity.getMainHandItem()));
            case OFF_HAND -> List.of(AbilitySources.equipment(EquipmentSlot.OFFHAND, entity.getOffhandItem()));
            case CURIOS -> List.of(AbilitySources.CURIOS);
        };
    }

    public Component displayName() {
        return Component.translatable("wheel.mxt.source." + this.getSerializedName());
    }
}
