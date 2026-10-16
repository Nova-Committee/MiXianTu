package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.DescribedEntry;
import com.iafenvoy.mxt.data.condition.EntityCondition;
import com.iafenvoy.mxt.data.cultivation.Technique;
import com.iafenvoy.mxt.util.matcher.ItemMatcher;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

import java.util.List;
import java.util.Optional;

/**
 * Which stacks a technique is read from: the items the declaration claims itself, the carrier the mod generates for
 * the picker, and the conditions that gate an attempt. How long a read lasts and how it looks belongs to the
 * technique's own {@code learn_time} / {@code hold_animation} / {@code hold_sound}. Which technique a stack teaches
 * is its own {@code mxt:technique} component, so a technique with no declaration of its own still reads.
 */
public record TechniqueBinding(Holder<Technique> technique, List<Entry> entries, int priority,
                               Optional<Item> carrierItem, List<DescribedEntry<EntityCondition>> conditions)
        implements ItemMatcher {
    public static final Codec<TechniqueBinding> CODEC = RecordCodecBuilder.create(i -> i.group(
            Technique.CODEC.fieldOf("technique").forGetter(TechniqueBinding::technique),
            ENTRIES_CODEC.optionalFieldOf("items", List.of()).forGetter(TechniqueBinding::entries),
            Codec.INT.optionalFieldOf("priority", DEFAULT_PRIORITY).forGetter(TechniqueBinding::priority),
            BuiltInRegistries.ITEM.byNameCodec().optionalFieldOf("carrier_item").forGetter(TechniqueBinding::carrierItem),
            DescribedEntry.codec(EntityCondition.CODEC, "condition").listOf().optionalFieldOf("conditions", List.of()).forGetter(TechniqueBinding::conditions)
    ).apply(i, TechniqueBinding::new));

    public static TechniqueBinding defaults(Holder<Technique> technique) {
        return new TechniqueBinding(technique, List.of(), DEFAULT_PRIORITY, Optional.empty(), List.of());
    }
}
