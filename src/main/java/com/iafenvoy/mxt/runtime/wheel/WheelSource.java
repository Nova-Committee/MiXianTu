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
 * Where one wheel's contents come from, and therefore which wheel it is. The first page is the player's own
 * twelve saved slots; every page after it is derived from what the player carries right now, so those pages are
 * read rather than edited and follow the equipment on their own.
 *
 * <p>A page is identified by this enum rather than by a number, and every request carries it: the server re-reads
 * the page the request names, so an entry is only honoured while the page it claims to come from still holds it.
 * Order is the order the switch keys walk, and index 0 is the page the wheel always opens on.</p>
 */
public enum WheelSource implements StringRepresentable {
    /** The player's own twelve slots, stored in the wheel layout attachment and edited by the configuration screen. */
    CONFIGURED,
    /** Whatever the item in the main hand grants right now. */
    MAIN_HAND,
    /** Whatever the item in the off hand grants right now. */
    OFF_HAND,
    /** Whatever the equipped Curios artifacts grant right now. */
    CURIOS;

    public static final Codec<WheelSource> CODEC = StringRepresentable.fromEnum(WheelSource::values);
    public static final StreamCodec<ByteBuf, WheelSource> STREAM_CODEC = MiscStreamCodecs.enumCodec(WheelSource.class);
    /** Every page, in the order the switch keys walk them and the order the page numbers count. */
    public static final List<WheelSource> PAGES = List.of(values());

    @Override
    public @NonNull String getSerializedName() {
        return this.name().toLowerCase(Locale.ROOT);
    }

    /** Whether this page is the player's saved layout rather than a reading of what they carry. */
    public boolean configured() {
        return this == CONFIGURED;
    }

    /** This page {@code delta} steps along, wrapping at both ends so the switch keys never dead-end. */
    public WheelSource step(int delta) {
        return PAGES.get(Math.floorMod(this.ordinal() + delta, PAGES.size()));
    }

    /** {@code 1}-based page number, which is what the player sees and never the index. */
    public int page() {
        return this.ordinal() + 1;
    }

    /**
     * The ability-grant sources this page reads right now, empty for the configured page. Naming the equipment
     * rather than the entries is what makes a page follow the item: the grants were recorded under the same ids
     * when the item was equipped.
     */
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
