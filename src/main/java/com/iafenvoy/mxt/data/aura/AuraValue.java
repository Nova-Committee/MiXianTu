package com.iafenvoy.mxt.data.aura;

import com.iafenvoy.mxt.data.aura.builtin.FixedAuraMaximum;
import com.iafenvoy.mxt.data.aura.builtin.InitialMultiplierAuraMaximum;
import com.iafenvoy.mxt.util.codec.CollectionCodecs;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.util.ExtraCodecs;

import java.util.Map;

/**
 * One aura entry in a datapack definition. {@code amount} is the zone's initial stock or a block's capacity
 * contribution, depending on its owner, and the color is used by environment rendering only.
 */
public record AuraValue(double amount, AuraMaximum max, double regenPerTick, int color) {
    public static final AuraValue ZERO = new AuraValue(0.0D, InitialMultiplierAuraMaximum.ONE, 0.0D, 0xFFFFFF);
    public static final Codec<AuraValue> CODEC = RecordCodecBuilder.<AuraValue>create(i -> i.group(
            Codec.DOUBLE.optionalFieldOf("amount", 0.0D).forGetter(AuraValue::amount),
            AuraMaximum.CODEC.optionalFieldOf("max", InitialMultiplierAuraMaximum.ONE).forGetter(AuraValue::max),
            Codec.DOUBLE.optionalFieldOf("regen_per_tick", 0.0D).forGetter(AuraValue::regenPerTick),
            ExtraCodecs.STRING_RGB_COLOR.optionalFieldOf("color", 0xFFFFFF).forGetter(AuraValue::color)
    ).apply(i, AuraValue::new)).validate(AuraValue::validate);
    public static final Codec<Map<Holder<Aura>, AuraValue>> MAP_CODEC = CollectionCodecs.map(Aura.CODEC, CODEC);

    private static DataResult<AuraValue> validate(AuraValue value) {
        return Double.isFinite(value.amount) && value.amount >= 0.0D && Double.isFinite(value.regenPerTick)
                ? DataResult.success(value)
                : DataResult.error(() -> "Aura amount must be non-negative and all aura numbers must be finite");
    }

    public AuraValue scale(int count) {
        return new AuraValue(this.amount() * count, new FixedAuraMaximum(this.max().resolve(this.amount()) * count), this.regenPerTick() * count, this.color());
    }

    public AuraValue merge(AuraValue another) {
        double maximum = this.max().resolve(this.amount()) + another.max().resolve(another.amount());
        double firstWeight = Math.max(0.0D, this.amount());
        double secondWeight = Math.max(0.0D, another.amount());
        double totalWeight = firstWeight + secondWeight;
        return new AuraValue(this.amount() + another.amount(), new FixedAuraMaximum(maximum), this.regenPerTick() + another.regenPerTick(), totalWeight <= 0.0D ? this.color() : weightedColor(this.color(), firstWeight, another.color(), secondWeight, totalWeight));
    }

    private static int weightedColor(int first, double firstWeight, int second, double secondWeight, double totalWeight) {
        int red = (int) Math.round((((first >>> 16) & 0xFF) * firstWeight + ((second >>> 16) & 0xFF) * secondWeight) / totalWeight);
        int green = (int) Math.round((((first >>> 8) & 0xFF) * firstWeight + ((second >>> 8) & 0xFF) * secondWeight) / totalWeight);
        int blue = (int) Math.round(((first & 0xFF) * firstWeight + (second & 0xFF) * secondWeight) / totalWeight);
        return (red << 16) | (green << 8) | blue;
    }
}
