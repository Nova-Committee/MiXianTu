package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.alchemy.PillDefinition;
import com.iafenvoy.mxt.data.weapon.WeaponDefinition;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.iafenvoy.mxt.registry.MxtRegistryKeys;
import net.minecraft.core.Holder;
import net.minecraft.resources.RegistryFixedCodec;

/**
 * A reusable effect attached to one or more {@link ItemDefinition}s.
 */
public interface ItemEffectDefinition {
    Codec<ItemEffectDefinition> CODEC = Codec.STRING.dispatch("type", ItemEffectDefinition::type,
            type -> switch (type) {
                case "weapon" -> WeaponDefinition.CODEC;
                case "pill" -> PillDefinition.CODEC;
                case "spirit_root" -> SpiritRootItemEffect.CODEC;
                default -> throw new IllegalArgumentException("Unknown item effect type " + type);
            });

    Codec<Holder<ItemEffectDefinition>> HOLDER_CODEC = RegistryFixedCodec.create(MxtRegistryKeys.ITEM_EFFECT);

    String type();

    MapCodec<? extends ItemEffectDefinition> codec();
}
