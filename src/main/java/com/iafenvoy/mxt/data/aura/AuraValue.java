package com.iafenvoy.mxt.data.aura;

import com.iafenvoy.mxt.data.aura.AuraMaximum.InitialMultiplier;
import com.iafenvoy.mxt.data.cultivation.Element;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.Map;

/**
 * One elemental aura entry in a datapack definition. {@code amount} is the
 * zone's initial stock or a block's capacity contribution, depending on its owner.
 */
public record AuraValue(double amount, AuraMaximum max, double regenPerTick) {
    public static final AuraValue ZERO = new AuraValue(0.0D, InitialMultiplier.ONE, 0.0D);
    public static final Codec<AuraValue> CODEC = RecordCodecBuilder.<AuraValue>create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("amount", 0.0D).forGetter(AuraValue::amount),
            AuraMaximum.CODEC.optionalFieldOf("max", InitialMultiplier.ONE).forGetter(AuraValue::max),
            Codec.DOUBLE.optionalFieldOf("regen_per_tick", 0.0D).forGetter(AuraValue::regenPerTick)
    ).apply(i, AuraValue::new)).validate(AuraValue::validate);
    public static final Codec<Map<Holder<Element>, AuraValue>> MAP_CODEC = CollectionCodecs.map(Element.CODEC, CODEC);

    private static DataResult<AuraValue> validate(AuraValue value) {
        return Double.isFinite(value.amount) && value.amount >= 0.0D && Double.isFinite(value.regenPerTick)
                ? DataResult.success(value)
                : DataResult.error(() -> "Aura amount must be non-negative and all aura numbers must be finite");
    }
}
