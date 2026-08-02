package com.iafenvoy.mxt.data.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.Identifier;

/**
 * A stable reference to one entry of a data-driven item registry.
 */
public record DatapackItemReference(DatapackItemRegistry registry, Identifier id) {
    public static final Codec<DatapackItemReference> OBJECT_CODEC = RecordCodecBuilder.create(instance -> instance.group(
            DatapackItemRegistry.CODEC.fieldOf("registry").forGetter(DatapackItemReference::registry),
            Identifier.CODEC.fieldOf("id").forGetter(DatapackItemReference::id)
    ).apply(instance, DatapackItemReference::new));

    public static final Codec<DatapackItemReference> CODEC = OBJECT_CODEC;

    public static final StreamCodec<RegistryFriendlyByteBuf, DatapackItemReference> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public DatapackItemReference decode(RegistryFriendlyByteBuf buffer) {
            Identifier registry = Identifier.STREAM_CODEC.decode(buffer);
            Identifier id = Identifier.STREAM_CODEC.decode(buffer);
            return new DatapackItemReference(DatapackItemRegistry.fromId(registry)
                    .orElseThrow(() -> new IllegalArgumentException("Unknown data-driven item registry: " + registry)), id);
        }

        @Override
        public void encode(RegistryFriendlyByteBuf buffer, DatapackItemReference value) {
            Identifier.STREAM_CODEC.encode(buffer, value.registry.id());
            Identifier.STREAM_CODEC.encode(buffer, value.id);
        }
    };

    public static DatapackItemReference other(Identifier id) {
        return new DatapackItemReference(DatapackItemRegistry.OTHER, id);
    }
}
