package com.iafenvoy.mxt.data.economy;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/** Persistent value recorded on a cheque. The value is independent of a particular currency item. */
public record ChequeData(long value, String issuer) {
    public static final ChequeData EMPTY = new ChequeData(0L, "");
    public static final Codec<ChequeData> CODEC = RecordCodecBuilder.<ChequeData>create(instance -> instance.group(
            Codec.LONG.fieldOf("value").forGetter(ChequeData::value),
            Codec.STRING.optionalFieldOf("issuer", "").forGetter(ChequeData::issuer)
    ).apply(instance, ChequeData::new)).validate(ChequeData::validate);

    private static DataResult<ChequeData> validate(ChequeData data) {
        return data.value >= 0L ? DataResult.success(data)
                : DataResult.error(() -> "Cheque value cannot be negative");
    }
}
