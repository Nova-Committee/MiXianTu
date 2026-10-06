package com.iafenvoy.mxt.data.quality;

import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

import java.util.List;

/**
 * One entry of the {@code mxt:default_quality} table: the tier an item starts at when the stack carries no tier and
 * the definition it carries declares none. It claims items by item or item tag like every other item table, and it
 * is the last source {@code QualityService.find} asks.
 */
public record DefaultQuality(List<Entry> entries, Holder<ItemQuality> quality, int priority) implements ItemMatcher {
    public static final Codec<DefaultQuality> DIRECT_CODEC = RecordCodecBuilder.create(i -> i.group(
            ENTRIES_CODEC.fieldOf("items").forGetter(DefaultQuality::entries),
            ItemQuality.CODEC.fieldOf("quality").forGetter(DefaultQuality::quality),
            Codec.INT.optionalFieldOf("priority", DEFAULT_PRIORITY).forGetter(DefaultQuality::priority)
    ).apply(i, DefaultQuality::new));
}
