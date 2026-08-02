package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.util.codec.AutoIgnoreListCodec;
import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.core.Holder;

import java.util.List;
import java.util.Optional;

/**
 * A logical, data-created item. It owns its reusable effect references but
 * never knows the physical Item registered in Minecraft's item registry.
 */
public record DatapackItem(List<Holder<ItemEffect>> effects, Optional<Identifier> model) {
    public static final Codec<DatapackItem> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            AutoIgnoreListCodec.create(ItemEffect.CODEC).optionalFieldOf("effects", List.of()).forGetter(DatapackItem::effects),
            Identifier.CODEC.optionalFieldOf("model").forGetter(DatapackItem::model)
    ).apply(instance, DatapackItem::new));

    /**
     * Default resource-pack item model: assets/<namespace>/items/mxt/<path>.json.
     */
    public static Identifier conventionalModel(Identifier itemDefinition) {
        return Identifier.fromNamespaceAndPath(itemDefinition.getNamespace(), "mxt/" + itemDefinition.getPath());
    }

    public Identifier modelFor(Identifier itemDefinition) {
        return this.model.orElseGet(() -> conventionalModel(itemDefinition));
    }
}
