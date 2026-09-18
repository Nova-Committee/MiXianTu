package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import org.jspecify.annotations.NonNull;

import java.util.ArrayList;
import java.util.List;

/**
 * Tells the client to open the picker over some categories. It carries no items: both sides read the same
 * synced registries through {@code ItemPickerManager}, so the grid is built where it is drawn and the only
 * thing that has to travel is which categories to show.
 */
public record ItemPickerS2CPayload(Component title, List<Identifier> categories) implements CustomPacketPayload {
    /**
     * A category list longer than this is not a request this mod makes; the cap keeps a malformed packet from
     * making the client iterate without bound.
     */
    private static final int MAX_CATEGORIES = 64;

    public static final Type<ItemPickerS2CPayload> TYPE = new Type<>(
            Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "item_picker_s2c"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ItemPickerS2CPayload> STREAM_CODEC =
            StreamCodec.of(ItemPickerS2CPayload::write, ItemPickerS2CPayload::read);

    private static void write(RegistryFriendlyByteBuf buf, ItemPickerS2CPayload payload) {
        ComponentSerialization.STREAM_CODEC.encode(buf, payload.title());
        buf.writeVarInt(payload.categories().size());
        for (Identifier category : payload.categories()) Identifier.STREAM_CODEC.encode(buf, category);
    }

    private static ItemPickerS2CPayload read(RegistryFriendlyByteBuf buf) {
        Component title = ComponentSerialization.STREAM_CODEC.decode(buf);
        int size = Math.min(buf.readVarInt(), MAX_CATEGORIES);
        List<Identifier> categories = new ArrayList<>(size);
        for (int i = 0; i < size; i++) categories.add(Identifier.STREAM_CODEC.decode(buf));
        return new ItemPickerS2CPayload(title, List.copyOf(categories));
    }

    @Override
    public @NonNull Type<ItemPickerS2CPayload> type() {
        return TYPE;
    }
}
