package com.iafenvoy.mxt.data;

import com.iafenvoy.mxt.util.formula.NumberProvider;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.Holder;
import net.minecraft.world.entity.ai.attributes.Attribute;

import java.util.Locale;

/**
 * Datapack-safe description of an attribute modifier; application belongs to runtime state.
 */
public record AttributeModifier(Holder<Attribute> attribute, Operation operation, NumberProvider value) {
    public static final Codec<AttributeModifier> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            Attribute.CODEC.fieldOf("attribute").forGetter(AttributeModifier::attribute),
            Operation.CODEC.optionalFieldOf("operation", Operation.ADD_VALUE).forGetter(AttributeModifier::operation),
            NumberProvider.CODEC.fieldOf("value").forGetter(AttributeModifier::value)
    ).apply(instance, AttributeModifier::new));

    public enum Operation {
        ADD_VALUE,
        ADD_MULTIPLIED_BASE,
        ADD_MULTIPLIED_TOTAL;

        public static final Codec<Operation> CODEC = Codec.STRING.comapFlatMap(value -> {
            try {
                return DataResult.success(valueOf(value.toUpperCase(Locale.ROOT)));
            } catch (IllegalArgumentException exception) {
                return DataResult.error(() -> "Unknown attribute modifier operation " + value);
            }
        }, value -> value.name().toLowerCase(Locale.ROOT));
    }
}
