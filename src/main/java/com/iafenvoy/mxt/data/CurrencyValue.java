package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.util.codec.MiscCodecs;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.chat.Component;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;

/**
 * One item as a currency denomination: what it is worth, when it cannot be spent, and what it can be exchanged
 * for. The item is the data map's key, so a definition no longer names the items it covers.
 */
public record CurrencyValue(long value, List<UnavailableWhen> unavailableWhen,
                            List<Exchange> exchanges, int priority) {
    public static final Codec<CurrencyValue> CODEC = RecordCodecBuilder.<CurrencyValue>create(i -> i.group(
            Codec.LONG.fieldOf("value").forGetter(CurrencyValue::value),
            UnavailableWhen.CODEC.listOf().optionalFieldOf("unavailable_when", List.of()).forGetter(CurrencyValue::unavailableWhen),
            Exchange.CODEC.listOf().fieldOf("exchanges").forGetter(CurrencyValue::exchanges),
            Codec.INT.optionalFieldOf("priority", ItemMatcher.DEFAULT_PRIORITY).forGetter(CurrencyValue::priority)
    ).apply(i, CurrencyValue::new)).validate(CurrencyValue::validate);

    private static DataResult<CurrencyValue> validate(CurrencyValue definition) {
        return definition.value > 0L
                ? DataResult.success(definition)
                : DataResult.error(() -> "Currency value must be positive");
    }

    /**
     * A stack condition that makes this denomination unavailable, with a datapack-defined reason.
     */
    public record UnavailableWhen(ItemCondition condition, Component reason) {
        public static final Codec<UnavailableWhen> CODEC = RecordCodecBuilder.create(i -> i.group(
                ItemCondition.CODEC.fieldOf("condition").forGetter(UnavailableWhen::condition),
                MiscCodecs.TRANSLATABLE_COMPONENT.fieldOf("reason").forGetter(UnavailableWhen::reason)
        ).apply(i, UnavailableWhen::new));
    }

    /**
     * One exchange: consume this many currency items, create the configured result stack.
     */
    public record Exchange(int cost, ItemStackTemplate result) {
        public static final Codec<Exchange> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.intRange(1, 99).fieldOf("cost").forGetter(Exchange::cost),
                ItemStackTemplate.CODEC.fieldOf("result").forGetter(Exchange::result)
        ).apply(i, Exchange::new));
    }
}
