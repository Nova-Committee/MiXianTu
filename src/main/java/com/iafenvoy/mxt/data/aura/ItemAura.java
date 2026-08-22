package com.iafenvoy.mxt.data.aura;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.data.action.builtin.entity.meta.NoOpAction;
import com.iafenvoy.mxt.registry.MxtResourceKeys;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.List;
import java.util.Optional;

/**
 * Defines how an item supplies temporary aura fuel while an entity cultivates.
 * The definition is matched by item or item tag, like the currency registry.
 */
public record ItemAura(List<Entry> items, NumberProvider totalAura, NumberProvider consumeSpeed, NumberProvider releaseSpeed,
                       Optional<ItemStackTemplate> resultStack, EntityAction exhaustedAction) implements ItemMatcher {
    public static final Codec<Holder<ItemAura>> CODEC = RegistryFixedCodec.create(MxtResourceKeys.ITEM_AURA);
    public static final Codec<ItemAura> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            ENTRIES_CODEC.fieldOf("items").forGetter(ItemAura::items),
            NumberProvider.CODEC.fieldOf("total_aura").forGetter(ItemAura::totalAura),
            NumberProvider.CODEC.fieldOf("consume_speed").forGetter(ItemAura::consumeSpeed),
            NumberProvider.CODEC.fieldOf("release_speed").forGetter(ItemAura::releaseSpeed),
            ItemStackTemplate.CODEC.optionalFieldOf("result_stack").forGetter(ItemAura::resultStack),
            EntityAction.CODEC.optionalFieldOf("exhausted_action", NoOpAction.INSTANCE).forGetter(ItemAura::exhaustedAction)
    ).apply(i, ItemAura::new));

    @Override
    public List<Entry> entries() {
        return this.items;
    }
}
