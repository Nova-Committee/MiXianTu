package com.iafenvoy.mxt.data.economy;

import com.iafenvoy.mxt.util.ItemMatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Optional;

/**
 * Defines one item as a currency denomination. Definitions are datapack-owned,
 * so modded items can participate without a hardcoded coin item class.
 */
public record CurrencyValue(List<Entry> items, long value, List<Exchange> exchanges) implements ItemMatcher {
    public static final Codec<CurrencyValue> CODEC = RecordCodecBuilder.<CurrencyValue>create(instance -> instance.group(
            ENTRIES_CODEC.optionalFieldOf("items", List.of()).forGetter(CurrencyValue::items),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("item").forGetter(definition -> definition.items().size() == 1 && definition.items().getFirst() instanceof ItemEntry(
                    Item item
            ) ? Optional.of(item) : Optional.empty()),
            Codec.LONG.fieldOf("value").forGetter(CurrencyValue::value),
            Exchange.CODEC.listOf().fieldOf("exchanges").forGetter(CurrencyValue::exchanges)
    ).apply(instance, (items, legacyItem, value, exchanges) -> new CurrencyValue(
            items.isEmpty() ? legacyItem.map(item -> List.of(Entry.item(item))).orElse(List.of()) : items, value, exchanges
    ))).validate(CurrencyValue::validate);

    public CurrencyValue {
        items = List.copyOf(items);
        exchanges = List.copyOf(exchanges);
    }

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
    public record Exchange(int cost, ItemStack result) {
        public static final Codec<Exchange> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.intRange(1, 99).fieldOf("cost").forGetter(Exchange::cost),
                ItemStack.CODEC.fieldOf("result").forGetter(Exchange::result)
        ).apply(instance, Exchange::new));
    }
}
