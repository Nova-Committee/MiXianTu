package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.alchemy.Pill;
import com.iafenvoy.mxt.data.Weapon;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

/**
 * A reusable effect attached to one or more {@link DatapackItem}s.
 */
public interface ItemEffect {
    Codec<ItemEffect> DIRECT_CODEC = Codec.STRING.dispatch("type", ItemEffect::type,
            type -> switch (type) {
                case "weapon" -> Weapon.CODEC;
                case "pill" -> Pill.CODEC;
                case "spirit_root" -> SpiritRootItemEffect.CODEC;
                default -> throw new IllegalArgumentException("Unknown item effect type " + type);
            });
    Codec<Holder<ItemEffect>> CODEC = RegistryFixedCodec.create(MxtRegistryKeys.ITEM_EFFECT);

    String type();

    MapCodec<? extends ItemEffect> codec();
}
