package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.util.matcher.builtin.ItemEntry;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStackTemplate;
import net.minecraft.util.ExtraCodecs;

import java.util.List;
import java.util.Optional;

/**
 * Defines one item as a currency denomination. Definitions are datapack-owned,
 * so modded items can participate without a hardcoded coin item class.
 */
public record CurrencyValue(List<Entry> items, long value, List<Exchange> exchanges) implements ItemMatcher {
    public static final Codec<CurrencyValue> CODEC = RecordCodecBuilder.<CurrencyValue>create(i -> i.group(
            ENTRIES_CODEC.optionalFieldOf("items", List.of()).forGetter(CurrencyValue::items),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("item").forGetter(definition -> definition.items().size() == 1 && definition.items().getFirst() instanceof ItemEntry(
                    Item item
            ) ? Optional.of(item) : Optional.empty()),
            Codec.LONG.fieldOf("value").forGetter(CurrencyValue::value),
            Exchange.CODEC.listOf().fieldOf("exchanges").forGetter(CurrencyValue::exchanges)
    ).apply(i, (items, legacyItem, value, exchanges) -> new CurrencyValue(
            items.isEmpty() ? legacyItem.map(item -> List.of((Entry) new ItemEntry(item))).orElse(List.of()) : items, value, exchanges
    ))).validate(CurrencyValue::validate);

    @Override
    public List<Entry> entries() {
        return this.items;
    }

    private static DataResult<CurrencyValue> validate(CurrencyValue definition) {
        if (definition.items.isEmpty() || definition.value <= 0L)
            return DataResult.error(() -> "Currency items must not be empty and value must be positive");
        return DataResult.success(definition);
    }

    /**
     * One selected exchange: consume currency items and create the configured result stack.
     */
    public record Exchange(int cost, ItemStackTemplate result) {
        public static final Codec<Exchange> CODEC = RecordCodecBuilder.create(i -> i.group(
                ExtraCodecs.intRange(1, 99).fieldOf("cost").forGetter(Exchange::cost),
                ItemStackTemplate.CODEC.fieldOf("result").forGetter(Exchange::result)
        ).apply(i, Exchange::new));
    }
}
