package com.iafenvoy.mxt.data.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/** A stable reference to one entry of a data-driven item registry. */
public record ItemDefinitionReference(ItemDefinitionRegistry registry, Identifier id) {
    public static final Codec<ItemDefinitionReference> OBJECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            ItemDefinitionRegistry.CODEC.fieldOf("registry").forGetter(ItemDefinitionReference::registry),
            Identifier.CODEC.fieldOf("id").forGetter(ItemDefinitionReference::id)
    ).apply(instance, ItemDefinitionReference::new));

    public static final Codec<ItemDefinitionReference> CODEC = OBJECT_CODEC;

    public static final StreamCodec<RegistryFriendlyByteBuf, ItemDefinitionReference> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ItemDefinitionReference decode(RegistryFriendlyByteBuf buffer) {
            Identifier registry = Identifier.STREAM_CODEC.decode(buffer);
            Identifier id = Identifier.STREAM_CODEC.decode(buffer);
            return new ItemDefinitionReference(ItemDefinitionRegistry.fromId(registry)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown data-driven item registry: " + registry)), id);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, ItemDefinitionReference value) {
            Identifier.STREAM_CODEC.encode(buffer, value.registry.id());
            Identifier.STREAM_CODEC.encode(buffer, value.id);
        }
    };

    public static ItemDefinitionReference other(Identifier id) {
        return new ItemDefinitionReference(ItemDefinitionRegistry.OTHER, id);
    }
}
