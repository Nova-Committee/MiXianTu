package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.Map;

/**
 * Server-authoritative aura source, shared chunk inventory, and sensed environmental pools for
 * the local player.
 */
public record AuraStateS2CPayload(Identifier source, Map<Identifier, AuraPool> stored,
                                  Map<Identifier, AuraPool> sensed) implements CustomPacketPayload {
    public static final Type<AuraStateS2CPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "aura_state_s2c"));
    public static final Codec<AuraStateS2CPayload> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("source").forGetter(AuraStateS2CPayload::source),
            Codec.unboundedMap(Identifier.CODEC, AuraPool.CODEC).fieldOf("stored").forGetter(AuraStateS2CPayload::stored),
            Codec.unboundedMap(Identifier.CODEC, AuraPool.CODEC).fieldOf("sensed").forGetter(AuraStateS2CPayload::sensed)
    ).apply(i, AuraStateS2CPayload::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, AuraStateS2CPayload> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public @NonNull Type<AuraStateS2CPayload> type() {
        return TYPE;
    }
}
