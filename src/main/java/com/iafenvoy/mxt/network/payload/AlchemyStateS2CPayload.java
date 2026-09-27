package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.screen.menu.AlchemyFurnaceView;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

public record AlchemyStateS2CPayload(int containerId, AlchemyFurnaceView view) implements CustomPacketPayload {
    public static final Type<AlchemyStateS2CPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "alchemy_state_s2c"));
    public static final StreamCodec<RegistryFriendlyByteBuf, AlchemyStateS2CPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, AlchemyStateS2CPayload::containerId,
            ByteBufCodecs.fromCodecWithRegistries(AlchemyFurnaceView.CODEC), AlchemyStateS2CPayload::view,
            AlchemyStateS2CPayload::new);

    @Override
    public @NonNull Type<AlchemyStateS2CPayload> type() {
        return TYPE;
    }
}
