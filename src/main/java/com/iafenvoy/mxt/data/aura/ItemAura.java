package com.iafenvoy.mxt.data.aura;

import com.iafenvoy.mxt.data.action.EntityAction;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.Optional;

/**
 * How one item supplies temporary aura fuel while an entity cultivates. The item is the data map's key, so this
 * record only describes the fuel. {@code type} names the {@link Aura} the item carries rather than the value it is
 * counted in, and the value - with its bounds - is read from {@code Aura#resource()} wherever the pool is written.
 */
public record ItemAura(Holder<Aura> type, NumberProvider aura, NumberProvider consumeSpeed,
                       NumberProvider releaseSpeed, Optional<ItemStackTemplate> resultStack,
                       EntityAction exhaustedAction, int priority) {
    public static final Codec<ItemAura> CODEC = RecordCodecBuilder.create(i -> i.group(
            Aura.CODEC.fieldOf("type").forGetter(ItemAura::type),
            NumberProvider.CODEC.fieldOf("aura").forGetter(ItemAura::aura),
            NumberProvider.CODEC.fieldOf("consume_speed").forGetter(ItemAura::consumeSpeed),
            NumberProvider.CODEC.fieldOf("release_speed").forGetter(ItemAura::releaseSpeed),
            ItemStackTemplate.CODEC.optionalFieldOf("result_stack").forGetter(ItemAura::resultStack),
            EntityAction.optionalCodec("exhausted_action").forGetter(ItemAura::exhaustedAction),
            Codec.INT.optionalFieldOf("priority", ItemMatcher.DEFAULT_PRIORITY).forGetter(ItemAura::priority)
    ).apply(i, ItemAura::new));
}
