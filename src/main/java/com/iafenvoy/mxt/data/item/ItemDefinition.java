package com.iafenvoy.mxt.data.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/**
 * A logical, data-created item. It owns its reusable effect references but
 * never knows the physical Item registered in Minecraft's item registry.
 */
public record ItemDefinition(List<Identifier> effects, Optional<Identifier> model) {
    public static final Codec<ItemDefinition> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Identifier.CODEC.listOf().optionalFieldOf("effects", List.of()).forGetter(ItemDefinition::effects),
            Identifier.CODEC.optionalFieldOf("model").forGetter(ItemDefinition::model)
    ).apply(instance, ItemDefinition::new));

    public ItemDefinition {
        effects = List.copyOf(effects);
    }

    /** Default resource-pack item model: assets/<namespace>/items/mxt/<path>.json. */
    public static Identifier conventionalModel(Identifier itemDefinition) {
        return Identifier.fromNamespaceAndPath(itemDefinition.getNamespace(), "mxt/" + itemDefinition.getPath());
    }

    public Identifier modelFor(Identifier itemDefinition) {
        return this.model.orElseGet(() -> conventionalModel(itemDefinition));
    }
}
