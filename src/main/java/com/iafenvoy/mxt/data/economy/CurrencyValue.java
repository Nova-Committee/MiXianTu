package com.iafenvoy.mxt.data.economy;

import com.iafenvoy.mxt.data.DescribedEntry;
import com.iafenvoy.mxt.data.condition.ItemCondition;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;

/**
 * Defines one item as a currency denomination. Definitions are datapack-owned,
 * so modded items can participate without a hardcoded coin item class.
 */
public record CurrencyValue(List<Entry> item, long value, List<DescribedEntry<ItemCondition>> unavailableWhen,
                            List<Exchange> exchanges, int priority) implements ItemMatcher {
    public static final Codec<CurrencyValue> CODEC = RecordCodecBuilder.<CurrencyValue>create(i -> i.group(
            ENTRIES_CODEC.optionalFieldOf("item", List.of()).forGetter(CurrencyValue::item),
            Codec.LONG.fieldOf("value").forGetter(CurrencyValue::value),
            DescribedEntry.codec(ItemCondition.CODEC, "condition").listOf().optionalFieldOf("unavailable_when", List.of()).forGetter(CurrencyValue::unavailableWhen),
            Exchange.CODEC.listOf().fieldOf("exchanges").forGetter(CurrencyValue::exchanges),
            Codec.INT.optionalFieldOf("priority", DEFAULT_PRIORITY).forGetter(CurrencyValue::priority)
    ).apply(i, CurrencyValue::new)).validate(CurrencyValue::validate);

    @Override
    public List<Entry> entries() {
        return this.item;
    }

    private static DataResult<CurrencyValue> validate(CurrencyValue definition) {
        if (definition.item.isEmpty() || definition.value <= 0L)
            return DataResult.error(() -> "Currency items must not be empty and value must be positive");
        return DataResult.success(definition);
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
