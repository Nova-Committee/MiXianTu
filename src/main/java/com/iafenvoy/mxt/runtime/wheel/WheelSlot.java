package com.iafenvoy.mxt.runtime.wheel;

import com.iafenvoy.mxt.MiXianTu;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * One wheel sector's content: the kind says which registry {@link #id()} belongs to, so a twelve-cell layout
 * can mix abilities and auras.
 */
public record WheelSlot(WheelEntryKind kind, Identifier id) {
    // Never looked up, and the server forces it onto empty slots.
    public static final Identifier EMPTY_ID = Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "empty");
    public static final WheelSlot EMPTY = new WheelSlot(WheelEntryKind.EMPTY, EMPTY_ID);
    public static final Codec<WheelSlot> CODEC = RecordCodecBuilder.create(i -> i.group(
            WheelEntryKind.CODEC.fieldOf("kind").forGetter(WheelSlot::kind),
            Identifier.CODEC.fieldOf("id").forGetter(WheelSlot::id)
    ).apply(i, WheelSlot::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, WheelSlot> STREAM_CODEC = StreamCodec.composite(
            WheelEntryKind.STREAM_CODEC, WheelSlot::kind,
            Identifier.STREAM_CODEC, WheelSlot::id,
            WheelSlot::new);
    public static final Codec<List<WheelSlot>> LIST_CODEC = CODEC.listOf();
    public static final StreamCodec<RegistryFriendlyByteBuf, List<WheelSlot>> LIST_STREAM_CODEC =
            STREAM_CODEC.apply(ByteBufCodecs.list());

    public static WheelSlot of(WheelEntryKind kind, Identifier id) {
        return kind.holdsEntry() && id != null ? new WheelSlot(kind, id) : EMPTY;
    }

    public boolean isEmpty() {
        return !this.kind.holdsEntry();
    }

    @Override
    public @NonNull String toString() {
        return "WheelSlot[" + this.kind.getSerializedName() + " " + this.id + "]";
    }
}
