package com.iafenvoy.mxt.data.economy;

import com.iafenvoy.mxt.util.ItemMatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.world.item.Item;

import java.util.List;

/**
 * Defines one item as a currency denomination. Definitions are datapack-owned,
 * so modded items can participate without a hardcoded coin item class.
 */
public record CurrencyValueDefinition(Item item, long value, List<Exchange> exchanges) implements ItemMatcher {
    public static final Codec<CurrencyValueDefinition> CODEC = RecordCodecBuilder.<CurrencyValueDefinition>create(instance -> instance.group(
            BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(CurrencyValueDefinition::item),
            Codec.LONG.fieldOf("value").forGetter(CurrencyValueDefinition::value),
            Exchange.CODEC.listOf().fieldOf("exchanges").forGetter(CurrencyValueDefinition::exchanges)
    ).apply(instance, CurrencyValueDefinition::new)).validate(CurrencyValueDefinition::validate);

    public CurrencyValueDefinition {
        exchanges = List.copyOf(exchanges);
    }

    @Override
    public List<ItemMatcher.Entry> entries() {
        return List.of(ItemMatcher.Entry.item(this.item));
    }

    private static DataResult<CurrencyValueDefinition> validate(CurrencyValueDefinition definition) {
        if (definition.value <= 0L) return DataResult.error(() -> "Currency value must be positive");
        return DataResult.success(definition);
    }

    /** One selected exchange: consume currency items and create the configured result stack. */
    public record Exchange(int cost, ItemStackDefinition result) {
        public static final Codec<Exchange> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                ExtraCodecs.intRange(1, 99).fieldOf("cost").forGetter(Exchange::cost),
                ItemStackDefinition.CODEC.fieldOf("result").forGetter(Exchange::result)
        ).apply(instance, Exchange::new));
    }
}
