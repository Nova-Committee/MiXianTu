package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.runtime.rift.RiftRecord;
import com.iafenvoy.saturn.util.codec.MiscStreamCodecs;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import net.minecraft.util.StringRepresentable;
import org.jspecify.annotations.NonNull;

import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public record RiftEditorS2CPayload(UUID token, Identifier dimension, EditorAction action, Optional<RiftRecord> record,
                                   String body) implements CustomPacketPayload {
    public static final Type<RiftEditorS2CPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "rift_editor"));
    public static final StreamCodec<RegistryFriendlyByteBuf, RiftEditorS2CPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, RiftEditorS2CPayload::token,
            Identifier.STREAM_CODEC, RiftEditorS2CPayload::dimension,
            EditorAction.STREAM_CODEC, RiftEditorS2CPayload::action,
            ByteBufCodecs.optional(RiftRecord.STREAM_CODEC), RiftEditorS2CPayload::record,
            ByteBufCodecs.STRING_UTF8, RiftEditorS2CPayload::body,
            RiftEditorS2CPayload::new);

    @Override
    public @NonNull Type<RiftEditorS2CPayload> type() {
        return TYPE;
    }

    /**
     * What the server tells the editor to do. The ids are what the wire carries, so renaming a constant does not
     * change the protocol.
     */
    public enum EditorAction implements StringRepresentable {
        OPEN, OPEN_ITEM, CLOSE, ERROR, ACCEPTED, ITEM_ACCEPTED, EXPORT, BLUEPRINT;
        public static final StreamCodec<ByteBuf, EditorAction> STREAM_CODEC = MiscStreamCodecs.enumCodec(EditorAction.class);

        @Override
        public @NonNull String getSerializedName() {
            return this.name().toLowerCase(Locale.ROOT);
        }
    }
}
