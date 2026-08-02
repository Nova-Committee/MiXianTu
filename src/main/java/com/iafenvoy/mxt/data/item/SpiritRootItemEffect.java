package com.iafenvoy.mxt.data.item;

import com.iafenvoy.mxt.data.cultivation.SpiritRootDefinition;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;

/**
 * Grants one domain-defined spirit root when the containing item is consumed.
 */
public record SpiritRootItemEffect(Holder<SpiritRootDefinition> spiritRoot) implements ItemEffectDefinition {
    public static final MapCodec<SpiritRootItemEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            SpiritRootDefinition.HOLDER_CODEC.fieldOf("spirit_root").forGetter(SpiritRootItemEffect::spiritRoot)
    ).apply(instance, SpiritRootItemEffect::new));

    @Override
    public String type() {
        return "spirit_root";
    }

    @Override
    public MapCodec<SpiritRootItemEffect> codec() {
        return CODEC;
    }
}
