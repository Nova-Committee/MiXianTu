package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.NonNull;

import java.util.List;

/**
 * Tells the client to open the picker over some categories. It carries no items: both sides read the same synced
 * registries through {@code ItemPickerManager}, so the grid is built where it is drawn and only the category list
 * has to travel.
 */
public record ItemPickerS2CPayload(Component title, List<Identifier> categories) implements CustomPacketPayload {
    public static final Type<ItemPickerS2CPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "item_picker_s2c"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemPickerS2CPayload> STREAM_CODEC = StreamCodec.composite(
            ComponentSerialization.STREAM_CODEC, ItemPickerS2CPayload::title,
            Identifier.STREAM_CODEC.apply(ByteBufCodecs.list(64)), ItemPickerS2CPayload::categories,
            ItemPickerS2CPayload::new);

    @Override
    public @NonNull Type<ItemPickerS2CPayload> type() {
        return TYPE;
    }
}
