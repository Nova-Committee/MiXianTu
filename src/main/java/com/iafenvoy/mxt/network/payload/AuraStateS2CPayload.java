package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.aura.Aura;
import com.iafenvoy.mxt.data.aura.AuraZone;
import com.iafenvoy.mxt.runtime.world.AuraPool;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.NonNull;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Server-authoritative aura state for the local player. The two maps intentionally differ: {@code actual} holds every
 * active source, while {@code environment} holds only the selected zone's environmental template. Both are keyed by
 * the aura they hold, and the ids decode against the synced aura registry. An empty {@code source} means no zone
 * covers the player's position, which the client has to be told rather than left showing the previous one.
 */
public record AuraStateS2CPayload(Optional<Holder<AuraZone>> source, Map<Holder<Aura>, AuraPool> actual,
                                  Map<Holder<Aura>, AuraPool> environment) implements CustomPacketPayload {
    public static final Type<AuraStateS2CPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "aura_state_s2c"));
    public static final Codec<AuraStateS2CPayload> CODEC = RecordCodecBuilder.create(i -> i.group(
            AuraZone.CODEC.optionalFieldOf("source").forGetter(AuraStateS2CPayload::source),
            Codec.unboundedMap(Aura.CODEC, AuraPool.CODEC).fieldOf("actual").forGetter(AuraStateS2CPayload::actual),
            Codec.unboundedMap(Aura.CODEC, AuraPool.CODEC).fieldOf("environment").forGetter(AuraStateS2CPayload::environment)
    ).apply(i, AuraStateS2CPayload::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, AuraStateS2CPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.optional(AuraZone.STREAM_CODEC), AuraStateS2CPayload::source,
            ByteBufCodecs.map(LinkedHashMap::new, Aura.STREAM_CODEC, AuraPool.STREAM_CODEC), AuraStateS2CPayload::actual,
            ByteBufCodecs.map(LinkedHashMap::new, Aura.STREAM_CODEC, AuraPool.STREAM_CODEC), AuraStateS2CPayload::environment,
            AuraStateS2CPayload::new);

    @Override
    public @NonNull Type<AuraStateS2CPayload> type() {
        return TYPE;
    }
}
