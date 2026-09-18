package com.iafenvoy.mxt.network.payload;

import com.iafenvoy.mxt.MiXianTu;
import com.iafenvoy.mxt.data.aura.Aura;
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

import java.util.Map;

/**
 * Server-authoritative aura source, fully resolved pools, and environmental pools for the local player. The
 * two maps intentionally have different meanings: {@code actual} contains every active source, while
 * {@code environment} contains only the selected zone's environmental template. Both are keyed by the aura
 * they hold; the ids travel as aura ids and are decoded against the synced aura registry.
 */
public record AuraStateS2CPayload(Identifier source, Map<Holder<Aura>, AuraPool> actual,
                                  Map<Holder<Aura>, AuraPool> environment) implements CustomPacketPayload {
    public static final Type<AuraStateS2CPayload> TYPE = new Type<>(Identifier.fromNamespaceAndPath(MiXianTu.MOD_ID, "aura_state_s2c"));
    public static final Codec<AuraStateS2CPayload> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.fieldOf("source").forGetter(AuraStateS2CPayload::source),
            Codec.unboundedMap(Aura.CODEC, AuraPool.CODEC).fieldOf("actual").forGetter(AuraStateS2CPayload::actual),
            Codec.unboundedMap(Aura.CODEC, AuraPool.CODEC).fieldOf("environment").forGetter(AuraStateS2CPayload::environment)
    ).apply(i, AuraStateS2CPayload::new));
    public static final StreamCodec<RegistryFriendlyByteBuf, AuraStateS2CPayload> STREAM_CODEC =
            ByteBufCodecs.fromCodecWithRegistries(CODEC);

    @Override
    public @NonNull Type<AuraStateS2CPayload> type() {
        return TYPE;
    }
}
