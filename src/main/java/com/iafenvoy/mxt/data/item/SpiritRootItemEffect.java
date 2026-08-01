package com.iafenvoy.mxt.data.item;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.resources.Identifier;

/** Grants one domain-defined spirit root when the containing item is consumed. */
public record SpiritRootItemEffect(Identifier spiritRoot) implements ItemEffectDefinition {
    public static final MapCodec<SpiritRootItemEffect> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Identifier.CODEC.fieldOf("spirit_root").forGetter(SpiritRootItemEffect::spiritRoot)
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
