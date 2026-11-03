package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.saturn.profiling.ProfilerFrame;
import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.UUID;

public record ProfilerSnapshotS2CPayload(UUID token, ProfilerFrame frame) implements CustomPacketPayload {
    public static final Type<ProfilerSnapshotS2CPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "profiler_snapshot"));
    public static final StreamCodec<RegistryFriendlyByteBuf, ProfilerSnapshotS2CPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, ProfilerSnapshotS2CPayload::token,
            ProfilerFrame.STREAM_CODEC, ProfilerSnapshotS2CPayload::frame,
            ProfilerSnapshotS2CPayload::new);

    @Override
    public @NonNull Type<ProfilerSnapshotS2CPayload> type() {
        return TYPE;
    }
}
