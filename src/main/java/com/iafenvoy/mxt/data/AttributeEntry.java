package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.util.formula.FormulaContext;
import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.Optional;

/**
 * An attribute target paired with the vanilla modifier. The optional value
 * provider replaces the modifier amount at runtime and is evaluated each tick.
 */
public record AttributeEntry(Holder<Attribute> attribute, AttributeModifier modifier,
                            Optional<NumberProvider> value) {
    public static final MapCodec<AttributeEntry> MAP_CODEC = RecordCodecBuilder.mapCodec(i -> i.group(
            Attribute.CODEC.fieldOf("attribute").forGetter(AttributeEntry::attribute),
            AttributeModifier.MAP_CODEC.forGetter(AttributeEntry::modifier),
            NumberProvider.CODEC.optionalFieldOf("value").forGetter(AttributeEntry::value)
    ).apply(i, AttributeEntry::new));
    public static final Codec<AttributeEntry> CODEC = MAP_CODEC.codec();

    public double amount(FormulaContext context) {
        return this.value.map(provider -> provider.evaluate(context)).orElse(this.modifier.amount());
    }
}
