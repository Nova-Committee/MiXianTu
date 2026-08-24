package com.iafenvoy.mxt.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStackTemplate;

import java.util.Optional;

/**
 * Optional visual for an ability or resource hotbar entry. A texture is a 16x16 GUI texture;
 * an item template is materialised only by the client renderer.
 */
public record HotbarIcon(Optional<Identifier> texture, Optional<ItemStackTemplate> item) {
    public static final Codec<HotbarIcon> CODEC = RecordCodecBuilder.create(i -> i.group(
            Identifier.CODEC.optionalFieldOf("texture").forGetter(HotbarIcon::texture),
            ItemStackTemplate.CODEC.optionalFieldOf("item").forGetter(HotbarIcon::item)
    ).apply(i, HotbarIcon::new));

    public HotbarIcon {
        if (texture.isPresent() == item.isPresent())
            throw new IllegalArgumentException("A hotbar icon must define exactly one of texture or item");
    }
}
