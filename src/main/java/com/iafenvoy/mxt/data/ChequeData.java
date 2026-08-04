package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Persistent value recorded on a cheque. The value is independent of a particular currency item.
 */
public record ChequeData(long value, String issuer) {
    public static final ChequeData EMPTY = new ChequeData(0L, "");
    public static final Codec<ChequeData> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            MiscCodecs.longRange(0, Long.MAX_VALUE).fieldOf("value").forGetter(ChequeData::value),
            Codec.STRING.optionalFieldOf("issuer", "").forGetter(ChequeData::issuer)
    ).apply(instance, ChequeData::new));
}
